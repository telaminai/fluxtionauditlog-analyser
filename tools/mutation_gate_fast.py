"""The fast mutation engine and branch-subset selection for tools/verify_project_chart_review.py.

Proposal: docs/proposals/faster-mutation-gate.md (PR #16). The Maven engine pays a whole Maven lifecycle
twice per control, and each one recompiles all of src/main/java because one file changed. This engine
keeps what makes the gate trustworthy and drops that cost:

- ONE `mvn test-compile` up front, then per control:
- `javac` of only the mutated file into target/classes;
- the named test method in a FRESH JVM, through the JUnit Platform launcher (tools/gate/GateLauncher.java);
- the source restored byte-identical, the class files restored byte-identical from a snapshot, and every
  file under target/classes checked against the snapshot taken after the initial compile;
- the named test run again, in another fresh JVM, and required green.

Compiling one file is only sound when nothing else needs recompiling. javac INLINES static final
constants into the classes that use them, and a changed method signature leaves callers linked to a
method that no longer exists. So each control compares `javap -p -constants` of the mutated class before
and after: if its members or constant values changed, the control falls back to a full `mvn test-compile`
(and another one after the restore). `--mode selftest` exercises that detection on generated classes.

Parity with Surefire, which the Maven engine uses: the working directory is the repository root; the
classpath order is test-classes, classes, then the test-scope dependencies; `-Djava.awt.headless=false` is
passed exactly as the Maven engine passes it; a throwable that is an AssertionError is a failure, any
other is an error, and an aborted or disabled test is a skip.
"""
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

GATE = Path('target/gate')
# One fresh JVM runs one test method, or the baseline's handful of classes; minutes, never an hour.
RUN_TIMEOUT_SECONDS = 600
TEST_SOURCES = Path('src/test/java')

# Anything here changes how EVERY control runs or what it tests, so a diff touching it selects the full set.
FULL_SET_TRIGGERS = (
    'tools/verify_project_chart_review.py',
    'tools/mutation_gate_fast.py',
    'tools/gate/',
    'pom.xml',
    '.github/workflows/',
)


def mvn(*args):
    """Run Maven quietly; return (exit, output)."""
    proc = subprocess.run(['mvn', '-q', *args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    return proc.returncode, proc.stdout


class FastEngine:
    """Prepared once per run; `run(selectors)` launches one fresh JVM; `control(case)` runs one mutation."""

    def __init__(self):
        self.cp = None
        self.launcher_dir = GATE / 'launcher'
        self.snapshot = None
        self.fallbacks = 0

    # ---- set-up -------------------------------------------------------------------------------------------

    def prepare(self):
        code, out = mvn('test-compile')
        assert code == 0, 'test-compile failed:\n' + out
        GATE.mkdir(parents=True, exist_ok=True)
        cp_file = GATE / 'test-classpath.txt'
        code, out = mvn('dependency:build-classpath', '-Dmdep.includeScope=test', '-Dmdep.outputFile=' + str(cp_file))
        assert code == 0, 'build-classpath failed:\n' + out
        deps = cp_file.read_text().strip()
        engine = re.search(r'junit-platform-engine-([0-9][^/:]*)\.jar', deps)
        assert engine, 'junit-platform-engine is not on the test classpath'
        version = engine.group(1)
        lib = GATE / 'lib'
        code, out = mvn('dependency:copy', '-Dartifact=org.junit.platform:junit-platform-launcher:' + version,
                        '-DoutputDirectory=' + str(lib))
        assert code == 0, 'could not fetch junit-platform-launcher ' + version + ':\n' + out
        launcher_jar = lib / ('junit-platform-launcher-' + version + '.jar')
        assert launcher_jar.exists(), launcher_jar
        # Surefire's order: test-classes, classes, then dependencies. The launcher comes last and matches the
        # platform version already on the classpath, so it cannot shadow anything the tests load.
        self.cp = ':'.join(['target/test-classes', 'target/classes', deps, str(launcher_jar)])
        self.launcher_dir.mkdir(parents=True, exist_ok=True)
        proc = subprocess.run(['javac', '-nowarn', '-d', str(self.launcher_dir), '-cp', self.cp,
                               'tools/gate/GateLauncher.java'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True)
        assert proc.returncode == 0, 'GateLauncher did not compile:\n' + proc.stdout
        self.snapshot = classes_snapshot()

    # ---- one JVM ------------------------------------------------------------------------------------------

    def run(self, names):
        """Run 'Class#method' names in one fresh JVM; return the Maven engine's result shape."""
        selectors = [qualify(n) for n in names]
        out = GATE / 'last-run.jsonl'
        if out.exists():
            out.unlink()
        command = ['java', '-Djava.awt.headless=false', '-cp', str(self.launcher_dir) + ':' + self.cp,
                   'GateLauncher', str(out), *selectors]
        try:
            proc = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                                  timeout=RUN_TIMEOUT_SECONDS)
            exit_code, output = proc.returncode, proc.stdout
        except subprocess.TimeoutExpired as hung:
            # a hang is a failed run with a reason, never a gate that waits forever
            exit_code = 124
            output = (hung.stdout or '') + '\nTIMED OUT after %d s' % RUN_TIMEOUT_SECONDS
        rows = [json.loads(line) for line in out.read_text().splitlines()] if out.exists() else []
        suites = []
        for cls in dict.fromkeys(n.split('#')[0] for n in names):
            mine = [r for r in rows if r['class'].split('.')[-1] == cls]
            suites.append({'name': cls, 'tests': len(mine),
                           'failures': sum(r['kind'] == 'failure' for r in mine),
                           'errors': sum(r['kind'] == 'error' for r in mine),
                           'skipped': sum(r['kind'] == 'skipped' for r in mine),
                           'testNames': [r['method'] for r in mine],
                           'assertions': [{'test': r['method'], 'kind': r['kind'], 'message': r['message']}
                                          for r in mine if r['kind'] in ('failure', 'error')]})
        failed = any(r['kind'] in ('failure', 'error') for r in rows)
        return {'command': command, 'exit': 1 if (exit_code != 0 or failed or not rows) else 0,
                'suites': suites, 'output': output}

    # ---- one control --------------------------------------------------------------------------------------

    def control(self, case, entry):
        """Mutate, compile, run, restore. Fills `entry` in place; returns (mutated_result, restored_result)."""
        name, site, old, new, target = case
        path = Path(site)
        original = path.read_bytes()
        owned = class_files_for(site)
        saved_classes = {p: p.read_bytes() for p in owned}
        api_before = javap_api(owned)
        mutated = None
        full_compile = False
        try:
            path.write_text(original.decode().replace(old, new))
            proc = subprocess.run(['javac', '-nowarn', '--release', compiler_release(), '-d', 'target/classes',
                                   '-cp', self.cp, site],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            if proc.returncode != 0:
                mutated = {'command': ['javac', site], 'exit': proc.returncode, 'suites': [],
                           'output': 'mutated source does not compile:\n' + proc.stdout}
            else:
                if javap_api(class_files_for(site)) != api_before:
                    # a constant or a member changed: other classes may have inlined or linked against it
                    full_compile = True
                    self.fallbacks += 1
                    code, out = mvn('test-compile')
                    assert code == 0, 'fallback test-compile failed:\n' + out
                mutated = self.run([target])
        finally:
            path.write_bytes(original)
            entry['restoredByteIdentical'] = path.read_bytes() == original
            if full_compile:
                code, out = mvn('test-compile')
                assert code == 0, 'restore test-compile failed:\n' + out
            else:
                for p in class_files_for(site):
                    if p not in saved_classes:
                        p.unlink()                       # a class the mutation created (a new lambda or inner)
                for p, data in saved_classes.items():
                    p.write_bytes(data)
            drift = snapshot_drift(self.snapshot)
            entry['classesRestoredByteIdentical'] = not drift
            if drift:
                entry['classDrift'] = drift[:20]
        entry['engine'] = 'fast'
        entry['fullCompileFallback'] = full_compile
        restored = self.run([target])
        return mutated, restored


# ---- helpers ------------------------------------------------------------------------------------------------

def qualify(name):
    """'Class#method' -> 'pkg.Class#method' (or 'Class' -> 'pkg.Class'), resolved from src/test/java."""
    cls, _, method = name.partition('#')
    found = list(TEST_SOURCES.rglob(cls + '.java'))
    assert len(found) == 1, ('test class', cls, found)
    fqcn = '.'.join(found[0].relative_to(TEST_SOURCES).with_suffix('').parts)
    return fqcn + ('#' + method if method else '')


def class_files_for(site):
    """The top-level class compiled from `site` and every nested or anonymous class javac emits with it."""
    rel = Path(site).relative_to('src/main/java').with_suffix('')
    folder = Path('target/classes') / rel.parent
    stem = rel.name
    return sorted([p for p in folder.glob(stem + '.class')] + [p for p in folder.glob(stem + '$*.class')])


ANONYMOUS = re.compile(r'\$\d+(\$|\.class$)')


def javap_api(files):
    """Non-private members and constant values of these classes: what OTHER classes can inline or link to.

    javap's default visibility (package, protected, public) is exactly that set. Private members, lambdas'
    synthetic methods and anonymous classes are reachable only from inside the one file being recompiled,
    and they renumber whenever a mutation adds or removes one, so they are left out.
    """
    files = [f for f in files if not ANONYMOUS.search(Path(f).name)]
    if not files:
        return ''
    proc = subprocess.run(['javap', '-constants', *map(str, files)],
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    return '\n'.join(line for line in proc.stdout.splitlines() if not line.startswith('Compiled from'))


def compiler_release():
    """The --release Maven compiles with, so a single-file compile produces the same class-file level."""
    found = re.search(r'<maven\.compiler\.release>(\d+)</maven\.compiler\.release>', Path('pom.xml').read_text())
    assert found, 'pom.xml declares no maven.compiler.release'
    return found.group(1)


def classes_snapshot():
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in Path('target/classes').rglob('*') if p.is_file()}


def snapshot_drift(snapshot):
    now = classes_snapshot()
    return sorted(set(snapshot) ^ set(now)) + sorted(k for k in snapshot if k in now and snapshot[k] != now[k])


# ---- branch subset ------------------------------------------------------------------------------------------

def changed_files(ref):
    proc = subprocess.run(['git', 'diff', '--name-only', ref + '...HEAD'], stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True)
    assert proc.returncode == 0, 'git diff failed: ' + proc.stdout
    uncommitted = subprocess.run(['git', 'diff', '--name-only', 'HEAD'], stdout=subprocess.PIPE, text=True).stdout
    return sorted(set(proc.stdout.split()) | set(uncommitted.split()))


def select_subset(cases, changed):
    """Pick the controls a diff can affect. Returns (selected, skipped) with a reason for every control.

    A control is selected when the diff touches its site file, its target test class, or a test source its
    target class references by simple name (shared fixtures such as ChartLifecycleReviewFrameTest's helpers).
    A diff to the harness, the build or CI selects everything. This is a heuristic for a branch: it never
    replaces the full set, and every skip is reported.
    """
    triggers = [f for f in changed if f.startswith(FULL_SET_TRIGGERS)]
    if triggers:
        return [(c, 'full set: the diff touches ' + triggers[0]) for c in cases], []
    changed_tests = {Path(f).stem: f for f in changed if f.startswith('src/test/java/') and f.endswith('.java')}
    selected, skipped = [], []
    source_cache = {}
    for case in cases:
        name, site, old, new, target = case
        cls = target.split('#')[0]
        target_file = next(iter(TEST_SOURCES.rglob(cls + '.java')), None)
        if site in changed:
            selected.append((case, 'site changed: ' + site))
            continue
        if target_file is not None and str(target_file) in changed:
            selected.append((case, 'target test changed: ' + str(target_file)))
            continue
        if target_file is not None and changed_tests:
            text = source_cache.setdefault(target_file, target_file.read_text())
            refs = [stem for stem in changed_tests if stem != cls and re.search(r'\b' + re.escape(stem) + r'\b', text)]
            if refs:
                selected.append((case, 'target test references changed ' + changed_tests[refs[0]]))
                continue
        skipped.append((case, 'no changed file reaches ' + site + ' or ' + cls))
    return selected, skipped


# ---- self-test of the compile-fallback detection ------------------------------------------------------------

def selftest():
    """Show that javap_api tells a body change (safe to compile alone) from a constant or signature change."""
    results = {}
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / 'src' / 'p'
        out = Path(tmp) / 'out'
        src.mkdir(parents=True)
        out.mkdir()
        a = src / 'A.java'
        (src / 'B.java').write_text('package p; public class B { public static int k() { return A.K; } }\n')

        def compile_all():
            subprocess.run(['javac', '-d', str(out), *map(str, src.glob('*.java'))], check=True)

        def api():
            return javap_api(sorted(out.glob('p/A*.class')))

        a.write_text('package p; public class A { public static final int K = 1;'
                     ' public static int f() { return 1; } }\n')
        compile_all()
        base = api()
        cases = {
            'body change (compile alone is sound)': ('return 1;', 'return 2;', False),
            'static final constant (inlined into B)': ('K = 1;', 'K = 2;', True),
            'signature change (B could link to a missing method)': ('int f()', 'int f(int x)', True),
        }
        original = a.read_text()
        for label, (old, new, expect_changed) in cases.items():
            a.write_text(original.replace(old, new))
            compile_all()
            changed = api() != base
            results[label] = {'apiChanged': changed, 'expected': expect_changed, 'ok': changed == expect_changed}
            a.write_text(original)
            compile_all()
        # and the reason it matters: B really does keep the old constant if A alone is recompiled
        a.write_text(original.replace('K = 1;', 'K = 2;'))
        subprocess.run(['javac', '-d', str(out), '-cp', str(out), str(a)], check=True)
        probe = Path(tmp) / 'Probe.java'
        probe.write_text('public class Probe { public static void main(String[] a) {'
                         ' System.out.println(p.B.k()); } }\n')
        stale = subprocess.run(['java', '-cp', str(out), str(probe)], stdout=subprocess.PIPE, text=True).stdout.strip()
        results['B after recompiling A alone'] = {'reads': stale, 'expected': '1 (stale inlined constant)',
                                                  'ok': stale == '1'}
    return results
