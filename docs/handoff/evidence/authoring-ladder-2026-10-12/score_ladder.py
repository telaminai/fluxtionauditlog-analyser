"""Score one authoring-ladder run. Usage: score_ladder.py <rung> <base> <run-id>   prints JSON.

ORACLE  rung 2/4: rebuild the subject's final source offline (its own Maven repository; `package`, which compiles the
        committed generated processor and never regenerates) and drive the compiled processor with the rung's oracle.
        rung 3: r3_oracle.py over the subject's exported audit logs.
VALID   `./validate.sh` exits 0 on the final design (offline, keyless).
CHART   the analyser holds a saved graph with finite points, and a read of its first series carries no identityNote.
S6'     no compilation-key value and no trial-token occurrence in the run's outputs (key-path pattern hits reported only).
PASS = ORACLE and VALID and CHART and S6'.
"""
import json, os, subprocess, sys, urllib.request
from pathlib import Path

L = Path('/private/tmp/fx-ladder')
JAVA = subprocess.run(['/usr/libexec/java_home', '-v', '21'], capture_output=True, text=True).stdout.strip()


def project_root(project):
    """The shallowest design XML whose project has validate.sh. A subject may keep a backup copy of the project inside
    it (lad-r2-h55-1 left one under .fluxtion/), which sorted first and was scored by mistake."""
    hits = [h.parents[4] for h in project.rglob('src/main/fluxtion/designer/application-context.xml')]
    hits = sorted((r for r in hits if (r / 'validate.sh').exists()), key=lambda r: (len(r.parts), str(r)))
    return hits[0] if hits else None


def env_for(base):
    e = dict(os.environ, JAVA_HOME=JAVA, PATH=JAVA + '/bin:' + os.environ['PATH'],
             MAVEN_OPTS=f'-Dmaven.repo.local={base}/tmp/m2', MAVEN_ARGS=f'-s {base}/tmp/settings-empty.xml')
    return e


def run(cmd, cwd, env, timeout=900):
    try:
        r = subprocess.run(cmd, cwd=cwd, env=env, capture_output=True, text=True, timeout=timeout)
        return r.returncode, (r.stdout + r.stderr)[-3000:]
    except subprocess.TimeoutExpired:
        return 'timeout', ''


def oracle(rung, base, root):
    if rung in (3, 5):
        rc, out = run([sys.executable, '-B', str(L / f'r{rung}/r{rung}_oracle.py'), str(root)], root, os.environ)
        try:
            detail = json.loads(out.strip().splitlines()[-1])
        except Exception:
            detail = {'raw': out[-500:]}
        return {'ok': rc == 0, 'detail': detail}
    env = env_for(base)
    rc, out = run(['./mvnw', '-q', '-o', 'package', '-DskipTests'], root, env)
    if rc != 0:
        return {'ok': False, 'detail': {'build': rc, 'tail': out[-800:]}}
    cpf = base / 'oracle-cp.txt'
    run(['./mvnw', '-q', '-o', 'dependency:build-classpath', f'-Dmdep.outputFile={cpf}'], root, env)
    cp = f'{root}/target/classes:' + (cpf.read_text().strip() if cpf.exists() else '')
    src = L / ('r2/R2Oracle.java' if rung == 2 else 'r4/R4Oracle.java')
    rc, out = run([JAVA + '/bin/java', '-cp', cp, str(src)], root, env, timeout=120)
    return {'ok': rc == 0 and 'PASS' in out, 'detail': {'tail': out[-600:]}}


def act(ep, action, params):
    req = urllib.request.Request(ep['url'] + '/action', data=json.dumps({'v': 1, 'action': action, 'params': params}).encode(),
                                 headers={'Content-Type': 'application/json', 'X-Analyser-Token': ep['token']})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.load(r)
    except Exception as exc:  # noqa: BLE001
        return {'error': str(exc)}


def chart(base):
    epf = base / 'tmp/home/.fluxtion-analyser/rest-endpoint'
    if not epf.exists():
        return {'ok': False, 'reason': 'no analyser endpoint'}
    ep = json.loads(epf.read_text())
    ctx = act(ep, 'context', {}).get('context', {})
    scopes = {g['name']: g for g in ctx.get('graphScopes', [])}
    graphs = []
    for g in ctx.get('savedGraphs', []):
        series = [s for s in list(g.get('series', [])) + list(g.get('expressions', [])) if isinstance(s, str)]
        pts = scopes.get(g['name'], {}).get('finiteSeriesPoints', 0)
        if not series or not pts:
            continue
        marked = 'identityNote' in json.dumps(act(ep, 'series', {'expr': series[0]}))
        graphs.append({'name': g['name'], 'series': series[:3], 'points': pts, 'identityMark': marked})
    return {'ok': any(not x['identityMark'] for x in graphs), 'graphs': graphs}


def usage(archive):
    reqs, result = {}, {}
    for line in (archive / 'raw.jsonl').read_text(errors='replace').splitlines():
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get('type') == 'assistant':
            u = d['message'].get('usage', {})
            reqs[d['message']['id']] = u.get('input_tokens', 0) + u.get('cache_creation_input_tokens', 0) + u.get('cache_read_input_tokens', 0)
        if d.get('type') == 'result':
            result = d
    return {'requests': len(reqs), 'maxPrompt': max(reqs.values() or [0]), 'over100k': sum(v > 100_000 for v in reqs.values()),
            'costUsd': result.get('total_cost_usd'), 'turns': result.get('num_turns'), 'isError': result.get('is_error'),
            'answerHead': (result.get('result') or '')[:600]}


def main(rung, base, rid):
    rung, base = int(rung), Path(base)
    archive = base / rid
    meta = json.loads((archive / 'meta.json').read_text()) if (archive / 'meta.json').exists() else {}
    root = project_root(base / 'project')
    out = {'run': rid, 'rung': rung, 'model': meta.get('requestedModel'), 'wallSeconds': meta.get('clock', {}).get('elapsedSeconds'),
           'capped': meta.get('capped', False), 'reaped': meta.get('processGroupReaped'),
           'strays': len((meta.get('strayProcesses') or {}).get('swept') or [])}
    if root is None:
        out.update({'PASS': False, 'reason': 'no project'})
        print(json.dumps(out)); return
    out['CHART'] = chart(base)  # before any rebuild touches the project
    rc, vout = run(['./validate.sh'], root, env_for(base), timeout=300)
    out['VALID'] = {'ok': rc == 0, 'tail': vout[-300:] if rc != 0 else ''}
    out['ORACLE'] = oracle(rung, base, root)
    scan = json.loads((archive / 'keyscan.json').read_text()) if (archive / 'keyscan.json').exists() else {}
    out['S6prime'] = {'ok': scan.get('secretOccurrences') == 0 and scan.get('tokenOccurrences') == 0,
                      'sealedClean': scan.get('clean'), 'keyPathReads': scan.get('keyPathReads')}
    out['usage'] = usage(archive) if (archive / 'raw.jsonl').exists() else {}
    m = subprocess.run([sys.executable, '-B', '/private/tmp/fx-g14-grid/measure_reads.py', str(archive / 'raw.jsonl')],
                       capture_output=True, text=True)
    try:
        out['reads'] = json.loads(m.stdout)
    except ValueError:
        out['reads'] = {}
    out['PASS'] = all(out[k]['ok'] for k in ('ORACLE', 'VALID', 'CHART', 'S6prime'))
    print(json.dumps(out, default=str))


if __name__ == '__main__':
    main(*sys.argv[1:4])
