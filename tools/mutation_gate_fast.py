"""The fast mutation engine and branch-subset selection for tools/verify_project_chart_review.py.

Proposal: docs/proposals/faster-mutation-gate.md (PR #16). The Maven engine pays a whole Maven lifecycle
twice per control, and each one recompiles all of src/main/java because one file changed. This engine
keeps what makes the gate trustworthy and drops that cost:

- ONE `mvn test-compile` up front, and a byte snapshot of target/classes and target/test-classes;
- per control, the mutated file is made live in the cheapest SOUND way (see `Site`);
- the named test runs in a FRESH JVM, through the JUnit Platform launcher (tools/gate/GateLauncher.java);
- the source is restored byte-identical and both class trees are written back to the snapshot exactly —
  changed files rewritten, files the mutation added deleted — then compared against it;
- the named test runs again, in another fresh JVM, and must be green.

Compiling one file is only sound when nothing else must be recompiled with it. javac INLINES constants into
the classes that use them, a changed signature leaves callers linked to a method that no longer exists, and
an annotation type's definition (its retention, target or defaults) is read at compile time by every class
that carries it. So a Java site is compiled alone only when `javap` shows its visible API unchanged AND it
declares no annotation type; otherwise the control falls back to a full `mvn test-compile`.
`--mode selftest` exercises each of these on generated classes, through the same functions.

Parity with Surefire, which the Maven engine uses: the working directory is the repository root; the
classpath order is test-classes, classes, then the test-scope dependencies; `-Djava.awt.headless=false` is
passed exactly as the Maven engine passes it, and `basedir` is set as Surefire sets it; a throwable that is
an AssertionError is a failure, any other is an error, and an aborted or disabled test is a skip. A failure
in a class-level callback (@BeforeAll, @AfterAll) or in discovery is reported, as Surefire reports it, and
fails the run even when every test method passed.
"""
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

GATE = Path('target/gate')
TEST_SOURCES = Path('src/test/java')
CLASS_TREES = (Path('target/classes'), Path('target/test-classes'))
# One fresh JVM runs one test method, or the baseline's handful of classes: minutes, never an hour.
RUN_TIMEOUT_SECONDS = 600

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


def run_with_timeout(command, timeout, cwd=None):
    """Run a command; a hang becomes exit 124 with whatever it printed, never an exception or a stall."""
    try:
        proc = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)
        return proc.returncode, proc.stdout.decode('utf-8', 'replace')
    except subprocess.TimeoutExpired as hung:
        partial = hung.stdout or b''
        if isinstance(partial, bytes):          # TimeoutExpired carries BYTES even when the run asked for text
            partial = partial.decode('utf-8', 'replace')
        return 124, partial + '\nTIMED OUT after %d s' % timeout


class Site:
    """Where a control's mutation lives, and therefore how it is made live and undone.

    - `java`: a source under src/main/java or src/test/java — compiled alone into its class tree, or by a full
      test-compile when compiling alone would be unsound;
    - `resource`: a file under src/main/resources — its runtime copy in target/classes is refreshed as well,
      for a test that reads it from the classpath;
    - `document`: anything else (docs, README) — tests read it from the source tree, so only the source changes.
    """

    def __init__(self, site):
        self.path = Path(site)
        s = site.replace('\\', '/')
        if s.endswith('.java') and s.startswith('src/main/java/'):
            self.kind, self.root, self.tree = 'java', Path('src/main/java'), Path('target/classes')
        elif s.endswith('.java') and s.startswith('src/test/java/'):
            self.kind, self.root, self.tree = 'java', Path('src/test/java'), Path('target/test-classes')
        elif s.startswith('src/main/resources/'):
            self.kind, self.root, self.tree = 'resource', Path('src/main/resources'), Path('target/classes')
        else:
            self.kind, self.root, self.tree = 'document', None, None

    def class_files(self):
        """The top-level class compiled from this source and every nested or anonymous class javac emits with it."""
        rel = self.path.relative_to(self.root).with_suffix('')
        folder = self.tree / rel.parent
        return sorted(list(folder.glob(rel.name + '.class')) + list(folder.glob(rel.name + '$*.class')))

    def runtime_copy(self):
        return self.tree / self.path.relative_to(self.root)


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
        compile_launcher(self.cp, self.launcher_dir)
        self.snapshot = tree_snapshot()

    # ---- one JVM ------------------------------------------------------------------------------------------

    def run(self, names):
        """Run 'Class#method' names (or whole 'Class'es) in one fresh JVM; return the Maven engine's shape."""
        return launch(self.cp, self.launcher_dir, [qualify(n) for n in names], names, RUN_TIMEOUT_SECONDS)

    # ---- one control --------------------------------------------------------------------------------------

    def control(self, case, entry):
        """Mutate, make live, run, restore. Fills `entry` in place; returns (mutated_result, restored_result)."""
        name, site, old, new, target = case
        where = Site(site)
        path = where.path
        original = path.read_bytes()
        mutated = None
        full_compile = False
        try:
            path.write_bytes(original.decode().replace(old, new).encode())
            if where.kind == 'java':
                before = api_fingerprint(where.class_files())
                code, out = run_with_timeout(['javac', '-nowarn', '--release', compiler_release(),
                                              '-d', str(where.tree), '-cp', self.cp, site], RUN_TIMEOUT_SECONDS)
                if code != 0:
                    mutated = {'command': ['javac', site], 'exit': code, 'suites': [],
                               'output': 'mutated source does not compile:\n' + out}
                elif needs_full_compile(before, where.class_files()):
                    full_compile = True
                    self.fallbacks += 1
                    force_full_compile()
            elif where.kind == 'resource':
                where.runtime_copy().parent.mkdir(parents=True, exist_ok=True)
                where.runtime_copy().write_bytes(path.read_bytes())
            if mutated is None:
                mutated = self.run([target])
        finally:
            path.write_bytes(original)
            entry['restoredByteIdentical'] = path.read_bytes() == original
            # Every path — single file, fallback, resource — ends by writing BOTH class trees back to the snapshot
            # taken after the initial compile: changed files rewritten, added files deleted. The snapshot is the
            # compiled form of the original sources, so this is a restore, not a recompile.
            corrected = restore_trees(self.snapshot)
            drift = tree_drift(self.snapshot)
            entry['classesRestoredByteIdentical'] = not drift
            entry['restoreRewrote'] = corrected[:20]
            if drift:
                entry['classDrift'] = drift[:20]
        entry['engine'] = 'fast'
        entry['siteKind'] = where.kind
        entry['fullCompileFallback'] = full_compile
        restored = self.run([target])
        return mutated, restored


# ---- launching one JVM --------------------------------------------------------------------------------------

def compile_launcher(cp, out_dir):
    Path(out_dir).mkdir(parents=True, exist_ok=True)
    code, out = run_with_timeout(['javac', '-nowarn', '-d', str(out_dir), '-cp', cp, 'tools/gate/GateLauncher.java'],
                                 RUN_TIMEOUT_SECONDS)
    assert code == 0, 'GateLauncher did not compile:\n' + out


def launch(cp, launcher_dir, selectors, names, timeout, workdir='.'):
    """One fresh JVM through GateLauncher. `names` are the harness's short 'Class#method' names, for grouping."""
    out = (Path(workdir) / GATE / 'last-run.jsonl').resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    command = ['java', '-Djava.awt.headless=false', '-Dbasedir=' + str(Path(workdir).resolve()),
               '-cp', str(Path(launcher_dir).resolve()) + ':' + cp, 'GateLauncher', str(out), *selectors]
    code, output = run_with_timeout(command, timeout, cwd=None if str(workdir) == '.' else workdir)
    rows = [json.loads(line) for line in out.read_text().splitlines()] if out.exists() else []
    suites = []
    for cls in dict.fromkeys(n.split('#')[0] for n in names):
        mine = [r for r in rows if r['class'].split('.')[-1] == cls]
        tests = [r for r in mine if r['method'] is not None]           # container rows carry no method
        suites.append({'name': cls,
                       'tests': len(tests) + sum(r['method'] is None and r['kind'] in ('failure', 'error') for r in mine),
                       'failures': sum(r['kind'] == 'failure' for r in mine),
                       'errors': sum(r['kind'] == 'error' for r in mine),
                       'skipped': sum(r['kind'] == 'skipped' for r in mine),
                       'testNames': [r['method'] for r in tests],
                       'assertions': [{'test': r['method'] or '[class]', 'kind': r['kind'], 'message': r['message']}
                                      for r in mine if r['kind'] in ('failure', 'error')]})
    # a row for a class nobody asked about (an engine or discovery failure) still fails the run
    failed = any(r['kind'] in ('failure', 'error') for r in rows)
    return {'command': command, 'exit': 1 if (code != 0 or failed or not rows) else 0,
            'suites': suites, 'output': output, 'rows': rows}


def same_test(reported, target):
    """Surefire names a parameter-injected method `name(Path)`; the launcher and the CASES say `name`."""
    return reported == target or ('(' not in target and reported.split('(')[0] == target)


def qualify(name):
    """'Class#method' -> 'pkg.Class#method' (or 'Class' -> 'pkg.Class'), resolved from src/test/java."""
    cls, _, method = name.partition('#')
    found = list(TEST_SOURCES.rglob(cls + '.java'))
    assert len(found) == 1, ('test class', cls, found)
    fqcn = '.'.join(found[0].relative_to(TEST_SOURCES).with_suffix('').parts)
    return fqcn + ('#' + method if method else '')


# ---- the single-file soundness check ------------------------------------------------------------------------

ANONYMOUS = re.compile(r'\$\d+(\$|\.class$)')


def api_fingerprint(files):
    """What OTHER classes can inline or link against, plus whether any of these classes is an annotation type.

    javap's default visibility (package, protected, public) with -constants gives the members and constant
    values another class can see. Lambdas' synthetic methods and anonymous classes are reachable only from inside
    the file being recompiled, and they renumber whenever a mutation adds or removes one, so they are left out.
    A private NAMED nested class is still listed: javac gives it package access in its own class file and keeps
    `private` only in the InnerClasses attribute, which this JDK's javap does not print. Adding one therefore
    takes the full-compile fallback — needless but safe (PR #18 review, finding 10, left as a known cost).
    """
    files = [Path(f) for f in files if not ANONYMOUS.search(Path(f).name)]
    if not files:
        return {'api': '', 'annotation': False}
    proc = subprocess.run(['javap', '-constants', *map(str, files)],
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    blocks, current = [], []
    for line in proc.stdout.splitlines():
        if line.startswith('Compiled from'):
            if current:
                blocks.append(current)
            current = []
        else:
            current.append(line)
    if current:
        blocks.append(current)
    return {'api': '\n'.join('\n'.join(b) for b in blocks),
            'annotation': any(b and 'extends java.lang.annotation.Annotation' in b[0] for b in blocks)}


def needs_full_compile(before, after_files):
    """True when compiling this one file alone could leave another class stale."""
    after = api_fingerprint(after_files)
    # An annotation type's retention, targets and defaults are read by javac when it compiles every class that
    # carries the annotation. None of that shows in javap's member listing, so any edit to a file declaring one
    # recompiles everything. Annotation types are rare as sites; being conservative here costs little.
    return before['annotation'] or after['annotation'] or before['api'] != after['api']


def force_full_compile():
    """Recompile EVERY production and test class from source.

    A plain `mvn test-compile` is incremental: the single-file javac above has just written the mutated class,
    newer than its source, so Maven reports "Nothing to compile - all classes are up to date" and the classes
    that inlined a constant or carry an annotation keep their stale bytecode (PR #18 re-review, R1). Deleting
    both class trees and Maven's incremental state leaves it nothing to trust but the sources. Resources are
    copied again by the same lifecycle; the snapshot restore afterwards puts every byte back.
    """
    import shutil
    for tree in (*CLASS_TREES, Path('target/maven-status')):
        if tree.exists():
            shutil.rmtree(tree)
    code, out = mvn('test-compile')
    assert code == 0, 'fallback test-compile failed:\n' + out
    assert any(CLASS_TREES[0].rglob('*.class')), 'fallback test-compile produced no classes'


def compiler_release():
    """The --release Maven compiles with, so a single-file compile produces the same class-file level."""
    found = re.search(r'<maven\.compiler\.release>(\d+)</maven\.compiler\.release>', Path('pom.xml').read_text())
    assert found, 'pom.xml declares no maven.compiler.release'
    return found.group(1)


# ---- class-tree snapshot and restore ------------------------------------------------------------------------

def tree_snapshot(trees=CLASS_TREES):
    """Every file's BYTES under the class trees, taken once after the initial compile."""
    return {str(p): p.read_bytes() for t in trees if Path(t).exists() for p in Path(t).rglob('*') if p.is_file()}


def restore_trees(snapshot, trees=CLASS_TREES):
    """Make the class trees exactly the snapshot again. Returns what had to be rewritten or deleted."""
    touched = []
    for t in trees:
        for p in Path(t).rglob('*') if Path(t).exists() else []:
            if p.is_file() and str(p) not in snapshot:
                p.unlink()
                touched.append('deleted ' + str(p))
    for name, data in snapshot.items():
        p = Path(name)
        if not p.exists() or p.read_bytes() != data:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(data)
            touched.append('rewrote ' + name)
    return touched


def tree_drift(snapshot, trees=CLASS_TREES):
    now = {str(p): p.read_bytes() for t in trees if Path(t).exists() for p in Path(t).rglob('*') if p.is_file()}
    return sorted(set(snapshot) ^ set(now)) + sorted(k for k in snapshot if k in now and snapshot[k] != now[k])


# ---- branch subset ------------------------------------------------------------------------------------------

def changed_files(ref):
    proc = subprocess.run(['git', 'diff', '--name-only', ref + '...HEAD'], stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True)
    assert proc.returncode == 0, 'git diff failed: ' + proc.stdout
    uncommitted = subprocess.run(['git', 'diff', '--name-only', 'HEAD'], stdout=subprocess.PIPE, text=True).stdout
    return sorted(set(proc.stdout.split()) | set(uncommitted.split()))


def select_subset(cases, changed):
    """Pick the controls a diff can affect, by direct heuristic match. Returns (selected, skipped) with a reason.

    Selected when the diff touches: the control's site; its target test class; a test source that target
    references by simple name (shared fixtures); or a production class that the target test or the site
    references by simple name. A change to the harness, the build or CI, or to a resource that is not itself a
    site, selects everything. This is a heuristic for a branch — indirect dependencies can escape it — so it
    never replaces the full set, and every skip is reported as exactly that.
    """
    sites = {c[1] for c in cases}
    triggers = [f for f in changed if f.startswith(FULL_SET_TRIGGERS)
                or (f.startswith('src/main/resources/') and f not in sites)]
    if triggers:
        return [(c, 'full set: the diff touches ' + triggers[0]) for c in cases], []
    java_changed = {Path(f).stem: f for f in changed
                    if f.endswith('.java') and (f.startswith('src/test/java/') or f.startswith('src/main/java/'))}
    selected, skipped = [], []
    cache = {}

    def text(p):
        if p not in cache:
            cache[p] = Path(p).read_text() if Path(p).exists() else ''
        return cache[p]

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
        why = None
        for stem, f in java_changed.items():
            if stem == cls:
                continue
            pattern = r'\b' + re.escape(stem) + r'\b'
            if target_file is not None and re.search(pattern, text(target_file)):
                why = 'target test references changed ' + f
                break
            if site.endswith('.java') and re.search(pattern, text(site)):
                why = 'site references changed ' + f
                break
        if why:
            selected.append((case, why))
        else:
            skipped.append((case, 'no direct heuristic match for ' + site + ' or ' + cls))
    return selected, skipped


# ---- self-tests, through the engine's own functions ---------------------------------------------------------

def selftest():
    """The compile-fallback detection, the class-tree restore and the timeout report, on generated classes."""
    results = {}
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / 'src' / 'p'
        out = Path(tmp) / 'out'
        src.mkdir(parents=True)
        out.mkdir()

        def compile_all():
            subprocess.run(['javac', '-d', str(out), *map(str, src.glob('*.java'))], check=True)

        def a_files():
            return sorted(list(out.glob('p/A.class')) + list(out.glob('p/A$*.class')))

        a = src / 'A.java'
        (src / 'B.java').write_text('package p; public class B { public static int k() { return A.K; } }\n')
        a.write_text('package p; public class A { public static final int K = 1;'
                     ' public static int f() { return 1; } }\n')
        compile_all()
        original = a.read_text()
        cases = {
            'body change: compiling alone is sound': ('return 1;', 'return 2;', False),
            'static final constant (inlined into B): full compile': ('K = 1;', 'K = 2;', True),
            'signature change (B could link to a missing method): full compile': ('int f()', 'int f(int x)', True),
            'private nested class added: full compile (conservative, known cost)': (
                'public static int f()', 'private static class Extra {} public static int f()', True),
        }
        for label, (old, new, expect) in cases.items():
            before = api_fingerprint(a_files())
            a.write_text(original.replace(old, new))
            compile_all()
            got = needs_full_compile(before, a_files())
            results[label] = {'fullCompile': got, 'expected': expect, 'ok': got == expect}
            a.write_text(original)
            for f in out.glob('p/A$*.class'):
                f.unlink()
            compile_all()

        # the reason the fallback exists: B keeps the old constant if A alone is recompiled
        a.write_text(original.replace('K = 1;', 'K = 2;'))
        subprocess.run(['javac', '-d', str(out), '-cp', str(out), str(a)], check=True)
        probe = Path(tmp) / 'Probe.java'
        probe.write_text('public class Probe { public static void main(String[] a) {'
                         ' System.out.println(p.B.k()); } }\n')
        stale = subprocess.run(['java', '-cp', str(out), str(probe)], stdout=subprocess.PIPE, text=True).stdout.strip()
        results['B after recompiling A alone reads the stale constant'] = {'reads': stale, 'ok': stale == '1'}
        a.write_text(original)
        compile_all()

        # PR #18 review 2: an annotation type's retention changes what consumers carry, invisibly to javap
        m = src / 'Mark.java'
        (src / 'Consumer.java').write_text('package p; @Mark public class Consumer {}\n')
        m.write_text('package p; import java.lang.annotation.*; @Retention(RetentionPolicy.CLASS) public @interface Mark {}\n')
        compile_all()
        before = api_fingerprint(sorted(out.glob('p/Mark*.class')))
        m.write_text(m.read_text().replace('CLASS', 'RUNTIME'))
        subprocess.run(['javac', '-d', str(out), '-cp', str(out), str(m)], check=True)
        got = needs_full_compile(before, sorted(out.glob('p/Mark*.class')))
        results['annotation retention change: full compile'] = {'fullCompile': got, 'ok': got is True}

        # PR #18 review 5: a restore writes the tree back exactly — an added class file is gone, and unloadable
        tree = Path(tmp) / 'tree'
        shutil_copytree(out, tree)
        snap = tree_snapshot((tree,))
        (tree / 'p' / 'A$Extra.class').write_bytes(b'\xca\xfe\xba\xbe extra')
        (tree / 'p' / 'B.class').write_bytes(b'mutated')
        touched = restore_trees(snap, (tree,))
        loads = subprocess.run(['java', '-cp', str(tree), str(probe)], stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True).stdout.strip()
        results['restore deletes an added class and rewrites a changed one'] = {
            'touched': touched, 'drift': tree_drift(snap, (tree,)),
            'ok': not tree_drift(snap, (tree,)) and not (tree / 'p' / 'A$Extra.class').exists() and loads == '1'}

        # PR #18 review 6: a hang is reported, with its partial output, not raised
        hang = Path(tmp) / 'Hang.java'
        hang.write_text('public class Hang { public static void main(String[] a) throws Exception {'
                        ' System.out.println("before hang"); System.out.flush();'
                        ' new java.util.concurrent.CompletableFuture<Void>().join(); } }\n')
        code, output = run_with_timeout(['java', str(hang)], 5)
        results['a hang is reported as exit 124 with its output'] = {
            'exit': code, 'ok': code == 124 and 'before hang' in output and 'TIMED OUT' in output}
    return results


def shutil_copytree(src, dst):
    import shutil
    shutil.copytree(src, dst)


def launcher_selftest(cp, launcher_dir):
    """PR #18 review 1, 7 and 9, through `launch` itself: a class-level failure, a parameter-injected method
    selected by name, and the basedir property. Needs the prepared test classpath (JUnit on it)."""
    results = {}
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        src = work / 'src' / 'probe'
        classes = work / 'classes'
        src.mkdir(parents=True)
        (src / 'AfterAllProbe.java').write_text(
            'package probe; import org.junit.jupiter.api.*;\n'
            'class AfterAllProbe { @Test void okay() {}\n'
            '  @AfterAll static void broken() { throw new IllegalStateException("after-all failure"); } }\n')
        (src / 'ParameterProbe.java').write_text(
            'package probe; import org.junit.jupiter.api.*; import org.junit.jupiter.api.io.TempDir;'
            ' import java.nio.file.*;\n'
            'class ParameterProbe { @Test void parameter(@TempDir Path tmp) { Assertions.assertTrue(Files.isDirectory(tmp)); } }\n')
        (src / 'BasedirProbe.java').write_text(
            'package probe; import org.junit.jupiter.api.*; import java.nio.file.*;\n'
            'class BasedirProbe { @Test void basedir() {\n'
            '  Assertions.assertEquals(Path.of("").toAbsolutePath().toString(), System.getProperty("basedir")); } }\n')
        code, out = run_with_timeout(['javac', '-d', str(classes), '-cp', cp, *map(str, src.glob('*.java'))], 300)
        assert code == 0, out
        full_cp = str(classes) + ':' + ':'.join(str(Path(e).resolve()) if not Path(e).is_absolute() else e
                                                for e in cp.split(':'))
        r = launch(full_cp, launcher_dir, ['probe.AfterAllProbe'], ['AfterAllProbe'], 120, workdir=tmp)
        results['@AfterAll failure fails the run although the test passed'] = {
            'exit': r['exit'], 'suite': {k: r['suites'][0][k] for k in ('tests', 'failures', 'errors', 'skipped')},
            'ok': r['exit'] != 0 and r['suites'][0]['errors'] == 1}
        r = launch(full_cp, launcher_dir, ['probe.ParameterProbe#parameter'], ['ParameterProbe#parameter'], 120,
                   workdir=tmp)
        results['a parameter-injected method is selected by name and runs'] = {
            'exit': r['exit'], 'names': r['suites'][0]['testNames'],
            'ok': r['exit'] == 0 and r['suites'][0]['testNames'] == ['parameter']}
        r = launch(full_cp, launcher_dir, ['probe.BasedirProbe#basedir'], ['BasedirProbe#basedir'], 120, workdir=tmp)
        results['basedir is set as Surefire sets it'] = {'exit': r['exit'], 'ok': r['exit'] == 0}
    return results
