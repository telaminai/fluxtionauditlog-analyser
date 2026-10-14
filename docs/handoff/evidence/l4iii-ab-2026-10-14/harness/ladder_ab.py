"""M72 L4 (iii) acceptance A/B (2026-10-14). G14 rig, prestaged (never an acceptance of the analyser).

Haiku 5.5, rungs 2-5, n = 3 per arm per rung, 24 runs, arms INTERLEAVED run by run in one window, the first arm
alternating by rung (r2: C first, r3: T first, r4: C first, r5: T first). Control = today's released fixtures
(r<n>/fixture); treatment = the same plus the fresh-context section (r<n>/fixture-t). Both arms use the default
subagent grant (no --no-subagent); meta.json records subagentOffered. Spend cap: the driver stops before a cell
when the billed total of the cells already scored is at or above $8.00.

    python3 -B ladder_ab.py            # runs every cell not yet done, in ORDER
"""
import json, os, signal, subprocess, sys, time, shutil
from pathlib import Path

L = Path(__file__).resolve().parent
RUNNER = Path.home() / 'IdeaProjects/telamin/worktrees/analyser-l4iii-ab/tools/g14_runner.py'
# The read-only Claude Code 2.1.296 binary the first ladder used, passed by path and checked by digest.
CLI = os.environ.get('LADDER_CLI', '')
CLI_SHA256 = 'c9b5341637becbd423ddffc5b254afb645682a3868cb708bbc6cc0e7bb419937'
JAR = L / 'jar/fluxtion-auditlog-analyser-1.34.0.jar'
MODEL = 'claude-haiku-5-5'
ORDER = [(rung, arm, n) for rung in (2, 3, 4, 5) for n in (1, 2, 3)
         for arm in (('c', 't') if rung in (2, 4) else ('t', 'c'))]
SPEND_CAP = 8.00
CAP = 2400
STRAY = (r'/private/tmp/fx-(g14-(55|45)|ab-(ctl|trt)2?|lad-r[0-9]-[a-z0-9]+|base-r[0-9]-h55|rel-r[0-9]-h55|ab-r[0-9]-[ct])-r?[0-9]+[a-z]?/'
         r'|audit-analyser-bundle-1\.0\.0-SNAPSHOT\.jar|/private/tmp/fx-(base13|rel13|ab14)/(gt|ctl|suff|dev|m2-baseline)')


def java():
    return subprocess.run(['/usr/libexec/java_home', '-v', '21'], capture_output=True, text=True).stdout.strip()


def precondition(rid):
    busy = subprocess.run(['/usr/sbin/lsof', '-nP', '-iTCP:8181', '-sTCP:LISTEN'], capture_output=True, text=True).stdout
    strays = subprocess.run(['/usr/bin/pgrep', '-f', STRAY], capture_output=True, text=True).stdout.split()
    if busy.strip() or strays:
        raise SystemExit(f'refusing {rid}: port 8181 held={bool(busy.strip())}, leftover processes={strays}')
    return {'port8181Free': True, 'strays': [], 'checkedUtc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}


def spent():
    import glob
    tot = 0.0
    for f in glob.glob('/private/tmp/fx-ab-r*/score.json'):
        try:
            tot += json.loads(Path(f).read_text())['usage']['costUsd'] or 0
        except (ValueError, KeyError, TypeError):
            pass
    return tot


def run_cell(rung, arm, n):
    rid = f'ab-r{rung}-{arm}-{n}'
    base = Path(f'/private/tmp/fx-{rid}')
    if (base / rid / 'meta.json').exists():
        return rid, 'done-before'
    if base.exists():
        raise SystemExit(f'refusing {rid}: {base} exists without a sealed meta.json (interrupted? mark it VOID first)')
    if spent() >= SPEND_CAP:
        raise SystemExit(f'spend cap: ${spent():.2f} billed >= ${SPEND_CAP:.2f}; not starting {rid}')
    pre = precondition(rid)
    home = base / 'tmp/home'
    (base / 'tmp/jar').mkdir(parents=True, exist_ok=True)
    home.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(JAR, base / 'tmp/jar/analyser.jar')
    # WARM CACHE (protocol default): the run's empty Maven repository is seeded from a frozen, provenance-checked
    # copy of a cache filled cold today from Central + public Repsy by the operator's ground-truth builds.
    seed = L / ('seed-spring' if rung in (2, 4) else 'seed-bundle')  # rungs 3, 5 and 6 are bundle rungs
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
                            str(d / ('fixture' if arm == 'c' else 'fixture-t')), rid, MODEL, str(base), str(d / f'TASK-R{rung}.txt')],
                           cwd=RUNNER.parent.parent)
        s = subprocess.run([sys.executable, '-B', str(L / 'score_ladder.py'), str(rung), str(base), rid],
                           capture_output=True, text=True, timeout=1800)
        (base / 'score.json').write_text(s.stdout or ('{"error": %r}' % s.stderr[-2000:]))
        p = subprocess.run([sys.executable, '-B', str(L / 'probe_levers.py'), str(base), rid], capture_output=True, text=True)
        (base / 'levers.json').write_text(p.stdout or ('{"error": %r}' % p.stderr[-2000:]))
        (base / 'split').mkdir(exist_ok=True)
        subprocess.run([sys.executable, '-B', str(RUNNER.parent / 'token_split.py'), 'split', str(base / rid / 'raw.jsonl'),
                        '--out', str(base / 'split'), '--run', rid], capture_output=True, text=True)
        c = subprocess.run([sys.executable, '-B', str(L / 'content_reads.py'), str(base / rid / 'raw.jsonl'), str(base)],
                           capture_output=True, text=True)
        (base / 'content-reads.json').write_text(c.stdout)
        print(rid, f'spent so far ${spent():.2f}', flush=True)
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
    for rung, arm, n in ORDER:
        if only and only != f'r{rung}-{arm}-{n}':
            continue
        print(run_cell(rung, arm, n), flush=True)
