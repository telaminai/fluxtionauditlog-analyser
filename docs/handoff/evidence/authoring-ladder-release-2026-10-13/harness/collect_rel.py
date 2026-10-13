"""Collect each release-ladder run's files into the evidence folder (home dir redacted to ~), run the L0 token split
(analyser main's tools/token_split.py) into results/<run>/, then print tables computed ONLY from the collected files,
with a comparison against the baseline's committed results. Usage: collect_rel.py <evidence dir> <analyser worktree>"""
import json, glob, os, statistics as st, subprocess, sys
from pathlib import Path

E = Path(sys.argv[1]); W = Path(sys.argv[2]); R = E / 'results'; R.mkdir(exist_ok=True)
HOME = str(Path.home())
for base in sorted(glob.glob('/private/tmp/fx-rel-r*')):
    rid = os.path.basename(base)[3:]
    arch = rid.split('-VOID')[0]
    out = R / rid; out.mkdir(exist_ok=True)
    srcs = {'score.json': f'{base}/score.json', 'levers.json': f'{base}/levers.json',
            'warm-cache.json': f'{base}/warm-cache.json', 'precondition.json': f'{base}/precondition.json'}
    for n in ('meta.json', 'keyscan.json', 'config.json'):
        srcs[n] = f'{base}/{arch}/{n}'
    for n, s in srcs.items():
        if os.path.exists(s):
            (out / n).write_text(Path(s).read_text().replace(HOME, '~'))
    try:
        d = json.loads((out / 'score.json').read_text())
        (out / 'reads.json').write_text(json.dumps(d.get('reads', {})) + '\n')
    except (OSError, ValueError):
        pass
    raw = Path(f'{base}/{arch}/raw.jsonl')
    if raw.exists() and not (out / 'buckets.json').exists():
        subprocess.run([sys.executable, '-B', str(W / 'tools/token_split.py'), 'split', str(raw), '--out', str(out),
                        '--run', rid], check=False, capture_output=True)
    if raw.exists():
        cr = subprocess.run([sys.executable, '-B', str(Path(__file__).resolve().parent / 'content_reads.py'), str(raw), base],
                            capture_output=True, text=True).stdout
        (out / 'content-reads.json').write_text(cr.replace(HOME, '~'))
    for n in ('buckets.json', 'categories.json', 'phases.json'):
        p = out / n
        if p.exists():
            p.write_text(p.read_text().replace(HOME, '~'))


BCR = E / 'baseline-content-reads'; BCR.mkdir(exist_ok=True)
for base in sorted(glob.glob('/private/tmp/fx-base-r?-h55-?')):
    rid = os.path.basename(base)[3:]; raw = Path(f'{base}/{rid}/raw.jsonl')
    if raw.exists():
        cr = subprocess.run([sys.executable, '-B', str(Path(__file__).resolve().parent / 'content_reads.py'), str(raw), base],
                            capture_output=True, text=True).stdout
        (BCR / f'{rid}.json').write_text(cr.replace(HOME, '~'))


def load(d, n):
    p = d / n
    try:
        return json.loads(p.read_text()) if p.exists() else {}
    except ValueError:
        return {}


rows = []
for d in sorted(R.iterdir()):
    rows.append((d.name, load(d, 'score.json'), load(d, 'levers.json'), load(d, 'meta.json')))
CR = {d.name: load(d, 'content-reads.json') for d in R.iterdir()}
print('| run | PASS | ORACLE | VALID | CHART | S6′ | cost $ | turns | wall s | max prompt | >100k | generatedReads (pattern) | content reads | toolResultBytes | runExport | run-and-export | --no-reconcile | withdraw | subagent calls / offered | gen version | annotation in export |')
print('|' + '---|' * 21)
valid = []
for rid, sc, lv, mt in rows:
    if 'VOID' in rid or not sc or 'error' in sc:
        print(f'| {rid} | VOID / no score |' + ' |' * 19); continue
    valid.append((rid, sc, lv, mt)); u = sc['usage']; r = sc.get('reads', {})
    gv = ','.join(sorted(set(lv.get('generatorVersion', {}).values()))) or '-'
    print(f"| {rid} | {sc['PASS']} | {sc['ORACLE']['ok']} | {sc['VALID']['ok']} | {sc['CHART']['ok']} | {sc['S6prime']['ok']} | "
          f"{u.get('costUsd') or 0:.3f} | {u.get('turns')} | {sc.get('wallSeconds') or 0:.0f} | {u.get('maxPrompt')} | {u.get('over100k')} | "
          f"{r.get('generatedReads')} | {CR.get(rid, {}).get('contentReads')} | {r.get('toolResultBytes')} | {lv.get('runExportCalls')} | {lv.get('runAndExportCalls')} | "
          f"{lv.get('noReconcileCalls')} | {lv.get('withdrawCalls')} | {lv.get('subagentCalls')} / {mt.get('subagentOffered')} | {gv} | {lv.get('annotationInExport')} |")
print()
print('| r6 run | X1 XML-first | X1 checks failing | X2 dispatch | X3 output | @ServiceRegistered writes |')
print('|---|---|---|---|---|---|')
for rid, sc, lv, mt in valid:
    if sc['rung'] != 6: continue
    det = sc['ORACLE'].get('detail', {})
    x1 = det.get('X1', {}); fails = [k for k, v in (x1.get('checks') or {}).items() if not v] or ([x1.get('reason')] if x1.get('reason') else [])
    print(f"| {rid} | {x1.get('ok')} | {', '.join(map(str, fails)) or '-'} | {det.get('X2', {}).get('ok')} | {det.get('X3', {}).get('ok')} | {lv.get('serviceRegisteredWrites')} |")
print()
for rung in (2, 3, 4, 5, 6):
    v = [x for x in valid if x[1]['rung'] == rung]
    print(f'rung {rung}: {sum(x[1]["PASS"] for x in v)}/{len(v)}')
def stats(rows, label):
    f = lambda k: [k(x[1]) for x in rows]
    print(f'{label}: PASS {sum(x[1]["PASS"] for x in rows)}/{len(rows)}; cost total %.4f median %.4f' % (sum(f(lambda s: s['usage']['costUsd'])), st.median(f(lambda s: s['usage']['costUsd']))))
    for name, k in [('turns', lambda s: s['usage']['turns']), ('wall', lambda s: s['wallSeconds']), ('over100k', lambda s: s['usage']['over100k']),
                    ('maxPrompt', lambda s: s['usage']['maxPrompt']), ('generatedReads', lambda s: s['reads']['generatedReads']),
                    ('toolResultBytes', lambda s: s['reads']['toolResultBytes'])]:
        v = f(k); print(f'  {name} median {st.median(v)} total {sum(v)} min {min(v)} max {max(v)}')
stats([x for x in valid if x[1]['rung'] in (2, 3, 4, 5)], 'rungs 2-5')
for lab, sel in (('rungs 2-5', (2, 3, 4, 5)), ('rung 6', (6,)), ('all 15', (2, 3, 4, 5, 6))):
    v = [CR.get(x[0], {}).get('contentReads', 0) for x in valid if x[1]['rung'] in sel]
    print(f'  content reads {lab}: total {sum(v)} median {st.median(v) if v else "-"} runs with any {sum(1 for c in v if c)}/{len(v)}')
bv = {f.stem: json.loads(f.read_text()) for f in BCR.glob('*.json')}
v = [x['contentReads'] for x in bv.values()]
print(f'  baseline content reads (rungs 2-5, 12 runs): total {sum(v)} median {st.median(v)} runs with any {sum(1 for c in v if c)}/{len(v)}')
print('content-read calls (shape, markers):')
for rid in sorted(CR):
    for c in CR[rid].get('calls', []):
        print(f'  {rid}: {c["markers"]} {c["resultBytes"]} B — {c["shape"]}')
for rid in sorted(bv):
    for c in bv[rid].get('calls', []):
        print(f'  {rid} (baseline): {c["markers"]} {c["resultBytes"]} B — {c["shape"]}')
stats([x for x in valid if x[1]['rung'] == 6], 'rung 6')
stats(valid, 'all 15')
b = [x for x in valid if x[1]['rung'] in (3, 5, 6)]
print('bundle runs: run-and-export users', sum(x[2].get('runAndExportCalls', 0) > 0 for x in b), '/', len(b),
      'runExport median', st.median([x[2]['runExportCalls'] for x in b]), 'annotationInExport', [x[2].get('annotationInExport') for x in b])
print('withdraw users', sum(x[2].get('withdrawCalls', 0) > 0 for x in valid), 'noReconcile users', sum(x[2].get('noReconcileCalls', 0) > 0 for x in valid),
      'subagent users', sum(x[2].get('subagentCalls', 0) > 0 for x in valid), 'subagentOffered', [x[3].get('subagentOffered') for x in valid])
print('gen versions', sorted({v for x in valid for v in x[2].get('generatorVersion', {}).values()}), 'capped', sum(bool(x[1].get('capped')) for x in valid),
      'S6prime', sum(x[1]['S6prime']['ok'] for x in valid), 'keyscanClean', sum(bool(x[1]['S6prime'].get('sealedClean')) for x in valid))
# baseline comparison, rungs 2-5, from the baseline's committed files
BL = E.parent / 'authoring-ladder-baseline-2026-10-13/results'
base = [json.loads((d / 'score.json').read_text()) for d in sorted(BL.iterdir()) if (d / 'score.json').exists() and 'VOID' not in d.name]
print()
print('| rung | measure (median per run unless PASS) | baseline | release |'); print('|---|---|---|---|')
M = [('PASS', lambda s: s['PASS'], 'sum'), ('cost $', lambda s: s['usage']['costUsd'], 'med'), ('turns', lambda s: s['usage']['turns'], 'med'),
     ('wall s', lambda s: s['wallSeconds'], 'med'), ('prompts >100k', lambda s: s['usage']['over100k'], 'med'),
     ('max prompt', lambda s: s['usage']['maxPrompt'], 'med'), ('generatedReads', lambda s: s['reads']['generatedReads'], 'med'),
     ('toolResultBytes', lambda s: s['reads']['toolResultBytes'], 'med')]
def cell(v, how):
    if not v: return '-'
    if how == 'sum': return f'{sum(v)}/{len(v)}'
    m = st.median(v); return f'{m:.3f}' if isinstance(m, float) and m < 10 else f'{m:g}'
rel = [x[1] for x in valid]
for rung in (2, 3, 4, 5, '2-5', 6):
    a = [s for s in base if rung == '2-5' or s['rung'] == rung]; c = [s for s in rel if (rung == '2-5' and s['rung'] in (2, 3, 4, 5)) or s['rung'] == rung]
    for name, k, how in M:
        print(f'| {rung} | {name} | {cell([k(s) for s in a], how) if a else "-"} | {cell([k(s) for s in c], how)} |')
for lab, rows_ in (('baseline 2-5', base), ('release 2-5', [s for s in rel if s['rung'] in (2, 3, 4, 5)])):
    print(lab, 'totals: cost %.2f generatedReads %d over100k %d toolResultBytes %d' % (sum(s['usage']['costUsd'] for s in rows_),
          sum(s['reads']['generatedReads'] for s in rows_), sum(s['usage']['over100k'] for s in rows_), sum(s['reads']['toolResultBytes'] for s in rows_)))
