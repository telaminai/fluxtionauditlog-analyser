"""Collect each baseline run's files into the evidence folder (home dir redacted to ~), then print the summary tables
computed ONLY from the collected files. Usage: collect.py <evidence dir>"""
import json, glob, os, shutil, statistics as st, sys
from pathlib import Path

E = Path(sys.argv[1]); R = E / 'results'; R.mkdir(exist_ok=True)
HOME = str(Path.home())
for base in sorted(glob.glob('/private/tmp/fx-base-r*')):
    rid = os.path.basename(base)[3:]
    out = R / rid; out.mkdir(exist_ok=True)
    srcs = {'score.json': f'{base}/score.json', 'levers.json': f'{base}/levers.json', 'warm-cache.json': f'{base}/warm-cache.json',
            'precondition.json': f'{base}/precondition.json'}
    arch = rid.replace('-VOID', '').split('-VOID')[0]
    for n in ('meta.json', 'keyscan.json', 'config.json'):
        srcs[n] = f'{base}/{arch}/{n}'
    for n, s in srcs.items():
        if os.path.exists(s):
            (out / n).write_text(Path(s).read_text().replace(HOME, '~'))
    s = out / 'score.json'
    if s.exists():
        try:
            d = json.loads(s.read_text())
            (out / 'reads.json').write_text(json.dumps(d.get('reads', {})) + '\n')
        except ValueError:
            pass

def load(rid, n):
    p = R / rid / n
    return json.loads(p.read_text()) if p.exists() else {}

rows = []
for d in sorted(R.iterdir()):
    sc = load(d.name, 'score.json'); lv = load(d.name, 'levers.json')
    rows.append((d.name, sc, lv))
print('| run | PASS | ORACLE | VALID | CHART | S6′ | cost $ | turns | wall s | max prompt | >100k | generatedReads | toolResultBytes | runExport | run-and-export | --no-reconcile | reconcile conflicts | gen version | annotation in export |')
print('|' + '---|' * 19)
valid = []
for rid, sc, lv in rows:
    if 'VOID' in rid or not sc or 'error' in sc:
        print(f'| {rid} | VOID / no score |' + ' |' * 17); continue
    valid.append((rid, sc, lv)); u = sc['usage']; r = sc.get('reads', {})
    gv = ','.join(sorted(set(lv.get('generatorVersion', {}).values()))) or '-'
    print(f"| {rid} | {sc['PASS']} | {sc['ORACLE']['ok']} | {sc['VALID']['ok']} | {sc['CHART']['ok']} | {sc['S6prime']['ok']} | "
          f"{u.get('costUsd') or 0:.3f} | {u.get('turns')} | {sc.get('wallSeconds') or 0:.0f} | {u.get('maxPrompt')} | {u.get('over100k')} | "
          f"{r.get('generatedReads')} | {r.get('toolResultBytes')} | {lv.get('runExportCalls')} | {lv.get('runAndExportCalls')} | "
          f"{lv.get('noReconcileCalls')} | {lv.get('reconcileConflicts')} | {gv} | {lv.get('annotationInExport')} |")
print()
for rung in (2, 3, 4, 5):
    v = [x for x in valid if x[1]['rung'] == rung]
    print(f'rung {rung}: {sum(x[1]["PASS"] for x in v)}/{len(v)}')
f = lambda k: [k(x[1]) for x in valid]
cost = f(lambda s: s['usage']['costUsd']); print(f'overall {sum(x[1]["PASS"] for x in valid)}/{len(valid)}')
print('cost total %.4f median %.4f' % (sum(cost), st.median(cost)))
for name, k in [('turns', lambda s: s['usage']['turns']), ('wall', lambda s: s['wallSeconds']), ('over100k', lambda s: s['usage']['over100k']),
                ('maxPrompt', lambda s: s['usage']['maxPrompt']), ('generatedReads', lambda s: s['reads']['generatedReads']),
                ('toolResultBytes', lambda s: s['reads']['toolResultBytes'])]:
    v = f(k); print(f'{name} median {st.median(v)} total {sum(v)} min {min(v)} max {max(v)}')
print('capped', sum(bool(x[1].get('capped')) for x in valid), 'S6prime', sum(x[1]['S6prime']['ok'] for x in valid),
      'keyscanClean', sum(bool(x[1]['S6prime'].get('sealedClean')) for x in valid))
b = [x[2] for x in valid if x[1]['rung'] in (3, 5)]
if b:
    print('bundle runExport median', st.median([l['runExportCalls'] for l in b]), 'run-and-export users', sum(l['runAndExportCalls'] > 0 for l in b), '/', len(b),
          'annotationInExport', [l.get('annotationInExport') for l in b])
r5 = [x[2] for x in valid if x[1]['rung'] == 5]
print('r5 noReconcile', [l.get('noReconcileCalls') for l in r5], 'conflicts', [l.get('reconcileConflicts') for l in r5])
print('gen versions', sorted({v for x in valid for v in x[2].get('generatorVersion', {}).values()}))
