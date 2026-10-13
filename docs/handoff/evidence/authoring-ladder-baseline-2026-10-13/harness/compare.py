"""Per-rung comparison, Haiku 5.5, first ladder (2026-10-12, corrected scoring) vs baseline (2026-10-13), from committed files only.
Usage: compare.py <evidence root docs/handoff/evidence>"""
import json, glob, statistics as st, sys
E = sys.argv[1]
def load(pat, probe):
    out = []
    for f in sorted(glob.glob(pat)):
        if 'VOID' in f: continue
        s = json.load(open(f)); rid = s['run']
        try: l = json.load(open(probe.format(rid=rid)))
        except FileNotFoundError: l = {}
        out.append((s, l))
    return out
A = load(f'{E}/authoring-ladder-2026-10-12/results/lad-r*-h55-*/score.json', E + '/authoring-ladder-baseline-2026-10-13/first-ladder-probe/{rid}.json')
B = load(f'{E}/authoring-ladder-baseline-2026-10-13/results/base-r*-h55-*/score.json', E + '/authoring-ladder-baseline-2026-10-13/results/{rid}/levers.json')
M = [('PASS', lambda s, l: s['PASS'], 'sum'), ('cost $', lambda s, l: s['usage']['costUsd'], 'median'),
     ('turns', lambda s, l: s['usage']['turns'], 'median'), ('wall s', lambda s, l: s['wallSeconds'], 'median'),
     ('prompts >100k', lambda s, l: s['usage']['over100k'], 'median'), ('max prompt', lambda s, l: s['usage']['maxPrompt'], 'median'),
     ('generatedReads', lambda s, l: s['reads']['generatedReads'], 'median'), ('toolResultBytes', lambda s, l: s['reads']['toolResultBytes'], 'median'),
     ('runExportCalls', lambda s, l: l.get('runExportCalls'), 'median')]
def cell(rows, f, how):
    v = [f(s, l) for s, l in rows]
    if how == 'sum': return f'{sum(v)}/{len(v)}'
    m = st.median(v); return f'{m:.3f}' if isinstance(m, float) and m < 10 else f'{m:g}' if isinstance(m, float) else str(m)
print('| rung | measure (median per run unless PASS) | first ladder | baseline |'); print('|---|---|---|---|')
for rung in (2, 3, 4, 5, 'all'):
    a = [x for x in A if rung == 'all' or x[0]['rung'] == rung]; b = [x for x in B if rung == 'all' or x[0]['rung'] == rung]
    for name, f, how in M:
        if name == 'runExportCalls' and rung in (2, 4, 'all'): continue  # defined for the bundle rungs only
        print(f'| {rung} | {name} | {cell(a, f, how)} | {cell(b, f, how)} |')
for lab, rows in (('first', A), ('baseline', B)):
    print(lab, 'totals: cost %.2f generatedReads %d over100k %d toolResultBytes %d' % (sum(s['usage']['costUsd'] for s, _ in rows),
          sum(s['reads']['generatedReads'] for s, _ in rows), sum(s['usage']['over100k'] for s, _ in rows), sum(s['reads']['toolResultBytes'] for s, _ in rows)))
