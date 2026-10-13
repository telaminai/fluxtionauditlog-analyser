"""Authoring-ladder BASELINE on the released stack (2026-10-13). G14 rig, prestaged (never an acceptance).

Rungs 2-5, claude-haiku-5-5 x3 each, one at a time, in the order r2 1..3, r3 1..3, r4 1..3, r5 1..3. Derived from
the first ladder's ladder.py / ladder5.py; the differences are the analyser jar (released 1.33.0), the harness root,
the rebuilt fixtures and seeds, Haiku 5.5 only, the run-id prefix `base-`, and a wider stray/port precondition.

    python3 -B ladder_base.py            # runs every cell not yet done
    python3 -B ladder_base.py r3-h55-2   # one cell
"""
import json, os, signal, subprocess, sys, time, shutil
from pathlib import Path

L = Path(__file__).resolve().parent
RUNNER = Path.home() / 'IdeaProjects/telamin/worktrees/analyser-ladder-baseline/tools/g14_runner.py'
# The read-only Claude Code 2.1.296 binary the first ladder used, passed by path and checked by digest.
CLI = os.environ.get('LADDER_CLI', '')
CLI_SHA256 = 'c9b5341637becbd423ddffc5b254afb645682a3868cb708bbc6cc0e7bb419937'
JAR = L / 'jar/fluxtion-auditlog-analyser-1.33.0.jar'
MODEL = 'claude-haiku-5-5'
ORDER = [(rung, n) for rung in (2, 3, 4, 5) for n in (1, 2, 3)]
CAP = 2400
STRAY = (r'/private/tmp/fx-(g14-(55|45)|ab-(ctl|trt)2?|lad-r[0-9]-[a-z0-9]+|base-r[0-9]-h55)-r?[0-9]+[a-z]?/'
         r'|audit-analyser-bundle-1\.0\.0-SNAPSHOT\.jar|/private/tmp/fx-base13/(gt|ctl|suff)-')


def java():
    return subprocess.run(['/usr/libexec/java_home', '-v', '21'], capture_output=True, text=True).stdout.strip()


def precondition(rid):
    busy = subprocess.run(['/usr/sbin/lsof', '-nP', '-iTCP:8181', '-sTCP:LISTEN'], capture_output=True, text=True).stdout
    strays = subprocess.run(['/usr/bin/pgrep', '-f', STRAY], capture_output=True, text=True).stdout.split()
    if busy.strip() or strays:
        raise SystemExit(f'refusing {rid}: port 8181 held={bool(busy.strip())}, leftover processes={strays}')
    return {'port8181Free': True, 'strays': [], 'checkedUtc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}


def run_cell(rung, n):
    rid = f'base-r{rung}-h55-{n}'
    base = Path(f'/private/tmp/fx-{rid}')
    if (base / rid / 'meta.json').exists():
        return rid, 'done-before'
    if base.exists():
        raise SystemExit(f'refusing {rid}: {base} exists without a sealed meta.json (interrupted? mark it VOID first)')
    pre = precondition(rid)
    home = base / 'tmp/home'
    (base / 'tmp/jar').mkdir(parents=True, exist_ok=True)
    home.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(JAR, base / 'tmp/jar/analyser.jar')
    # WARM CACHE (protocol default): the run's empty Maven repository is seeded from a frozen, provenance-checked
    # copy of a cache filled cold today from Central + public Repsy by the operator's ground-truth builds.
    seed = L / ('seed-spring' if rung in (2, 4) else 'seed-bundle')
    shutil.copytree(seed, base / 'tmp/m2', symlinks=True)
    subprocess.run(['chmod', '-R', 'u+w', str(base / 'tmp/m2')])
    (base / 'warm-cache.json').write_text(json.dumps({'warmCache': True, 'seed': seed.name,
                                                      'seedTreeSha256': (L / (seed.name + '.tree.sha256')).read_text().strip()}) + '\n')
    (base / 'precondition.json').write_text(json.dumps(pre) + '\n')
    log = open(base / 'analyser-instance.log', 'w')
    inst = subprocess.Popen([java() + '/bin/java', f'-Duser.home={home}', '-jar', str(base / 'tmp/jar/analyser.jar'), '--rest'],
                            stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(90):
        if (home / '.fluxtion-analyser/rest-endpoint').exists():
            break
        time.sleep(1)
    try:
        d = L / f'r{rung}'
        r = subprocess.run([sys.executable, '-B', str(RUNNER), '--claude-bin', CLI, '--cap', str(CAP), '--prestaged',
                            str(d / 'fixture'), rid, MODEL, str(base), str(d / f'TASK-R{rung}.txt')], cwd=RUNNER.parent.parent)
        s = subprocess.run([sys.executable, '-B', str(L / 'score_ladder.py'), str(rung), str(base), rid],
                           capture_output=True, text=True, timeout=1800)
        (base / 'score.json').write_text(s.stdout or ('{"error": %r}' % s.stderr[-2000:]))
        p = subprocess.run([sys.executable, '-B', str(L / 'probe_levers.py'), str(base), rid], capture_output=True, text=True)
        (base / 'levers.json').write_text(p.stdout or ('{"error": %r}' % p.stderr[-2000:]))
        print(rid, 'runner', r.returncode, flush=True)
    finally:
        try:
            os.killpg(inst.pid, signal.SIGTERM)
            inst.wait(timeout=30)
        except (ProcessLookupError, PermissionError):
            pass
        except subprocess.TimeoutExpired:
            os.killpg(inst.pid, signal.SIGKILL)
    return rid, 'ran'


if __name__ == '__main__':
    import hashlib
    if not CLI or hashlib.sha256(Path(CLI).read_bytes()).hexdigest() != CLI_SHA256:
        raise SystemExit('LADDER_CLI must name the Claude Code 2.1.296 binary with the recorded digest')
    only = sys.argv[1] if len(sys.argv) > 1 else None
    for rung, n in ORDER:
        if only and only != f'r{rung}-h55-{n}':
            continue
        print(run_cell(rung, n), flush=True)
