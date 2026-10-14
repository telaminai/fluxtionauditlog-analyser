"""M72 L4 (iii) acceptance: the spec's decision rule, applied literally (docs/specs/spec-authoring-token-lowering.md,
"L4 (iii) — as designed", Acceptance), from committed files only.  Usage: decide.py <results dir>   prints markdown + JSON.

Per run (results/<run>/): score.json (PASS, ORACLE, CHART, billed cost), phases.json (L0 phases; `diagnosis` holds every
request in L0's diagnosis phase in every context, the subagent's included; `delegation` when the run delegated),
buckets.json (cost recomputed vs billed). Run ids: ab-r<rung>-<c|t>-<n>.

Metrics (spec): diagnosis-phase cost; diagnosis-phase requests over 100k; total run cost (billed, which counts every
subagent). Reported, not judged: delegations, briefs, per-context first prompts.
A run with a `delegation.warnings` entry is excluded from the COST metrics only (still counts for PASS/ORACLE/CHART).

Per rung, PASS when ALL hold:
  (a) median treatment diagnosis cost < median control diagnosis cost by MORE than the control's min-max spread
      (of diagnosis cost) on that rung;
  (b) median treatment diagnosis over-100k <= half the control's median;
  (c) median treatment total cost <= median control total cost;
  (d) PASS, ORACLE and CHART counts of the treatment are not below the control's.
Overall:
  1. any rung where (d) fails -> REJECT;
  2. else KEEP if the cost criteria (a)(b)(c) pass on BOTH bundle rungs (3, 5) and on at least 3 of 4 rungs, and no
     rung's median treatment total cost exceeds the control's by more than the control's (total-cost) spread;
  3. else NO EFFECT SHOWN (the section is deleted);
  4. UP-PG-08's starter half proceeds only if rungs 2 and 4 both pass.
Out-of-sample check (L0's): every run's recomputed cost within 2% of billed.
"""
import json, statistics as st, sys
from pathlib import Path

R = Path(sys.argv[1])
runs = {}
for d in sorted(R.iterdir()):
    if 'VOID' in d.name or not (d / 'score.json').exists():
        continue
    s = json.loads((d / 'score.json').read_text())
    if 'error' in s:
        continue
    ph = json.loads((d / 'phases.json').read_text()); bk = json.loads((d / 'buckets.json').read_text())
    _, rung, arm, n = d.name.split('-')
    dg = ph.get('delegation') or {}
    runs[d.name] = {'rung': int(rung[1:]), 'arm': arm, 'PASS': s['PASS'], 'ORACLE': s['ORACLE']['ok'], 'CHART': s['CHART']['ok'],
                    'total': s['usage']['costUsd'], 'diagCost': ph['phases']['diagnosis']['costUsd'],
                    'diagOver100k': ph['phases']['diagnosis']['over100k'], 'warnings': dg.get('warnings', []),
                    'delegations': dg.get('delegations', 0), 'ratio': bk['totals']['costRecomputedRatio'],
                    'subagentCost': dg.get('subagentCostUsd'), 'backgrounded': dg.get('backgrounded', 0),
                    'briefChars': dg.get('delegateInputChars'), 'replyBytes': dg.get('delegateResultBytes'),
                    'mainWhileSub': dg.get('mainRequestsWhileSubagentRan', 0),
                    'subFirstPrompts': [c['firstPrompt'] for c in bk.get('contexts', []) if c['context'] != 'main']}
med = lambda v: st.median(v) if v else None
spread = lambda v: (max(v) - min(v)) if v else None
res = {}
for rung in (2, 3, 4, 5):
    C = [r for r in runs.values() if r['rung'] == rung and r['arm'] == 'c']
    T = [r for r in runs.values() if r['rung'] == rung and r['arm'] == 't']
    Cc = [r for r in C if not r['warnings']]; Tc = [r for r in T if not r['warnings']]
    cd, td = [r['diagCost'] for r in Cc], [r['diagCost'] for r in Tc]
    co, to = [r['diagOver100k'] for r in Cc], [r['diagOver100k'] for r in Tc]
    ct, tt = [r['total'] for r in Cc], [r['total'] for r in Tc]
    q = {k: (sum(r[k] for r in T), sum(r[k] for r in C)) for k in ('PASS', 'ORACLE', 'CHART')}
    x = {'n': (len(T), len(C)), 'nCost': (len(Tc), len(Cc)),
         'diagCost': {'t': med(td), 'c': med(cd), 'cSpread': spread(cd)}, 'diagOver100k': {'t': med(to), 'c': med(co)},
         'total': {'t': med(tt), 'c': med(ct), 'cSpread': spread(ct)}, 'quality': q}
    full = len(Tc) and len(Cc)
    x['a'] = bool(full) and (x['diagCost']['c'] - x['diagCost']['t']) > x['diagCost']['cSpread']
    x['b'] = bool(full) and x['diagOver100k']['t'] <= x['diagOver100k']['c'] / 2
    x['c'] = bool(full) and x['total']['t'] <= x['total']['c']
    x['d'] = all(t >= c for t, c in q.values())
    x['cost'] = x['a'] and x['b'] and x['c']
    x['pass'] = x['cost'] and x['d']
    x['totalAboveBySpread'] = bool(full) and (x['total']['t'] - x['total']['c']) > x['total']['cSpread']
    res[rung] = x
if any(not x['d'] for x in res.values()):
    decision = 'REJECT'
elif res[3]['cost'] and res[5]['cost'] and sum(x['cost'] for x in res.values()) >= 3 and not any(x['totalAboveBySpread'] for x in res.values()):
    decision = 'KEEP'
else:
    decision = 'NO EFFECT SHOWN'
starter = res[2]['pass'] and res[4]['pass']
oos = all(abs(r['ratio'] - 1) <= 0.02 for r in runs.values())
print('| rung | n T/C | diag cost $ median T / C (C spread) | (a) | diag >100k median T / C | (b) | total $ median T / C (C spread) | (c) | PASS T/C | ORACLE T/C | CHART T/C | (d) | rung passes |')
print('|---|---|---|---|---|---|---|---|---|---|---|---|---|')
f = lambda v: '-' if v is None else f'{v:.4f}'
for rung, x in res.items():
    q = x['quality']
    print(f"| {rung} | {x['n'][0]}/{x['n'][1]} | {f(x['diagCost']['t'])} / {f(x['diagCost']['c'])} ({f(x['diagCost']['cSpread'])}) | {x['a']} | "
          f"{x['diagOver100k']['t']} / {x['diagOver100k']['c']} | {x['b']} | {f(x['total']['t'])} / {f(x['total']['c'])} ({f(x['total']['cSpread'])}) | {x['c']} | "
          f"{q['PASS'][0]}/{q['PASS'][1]} | {q['ORACLE'][0]}/{q['ORACLE'][1]} | {q['CHART'][0]}/{q['CHART'][1]} | {x['d']} | {x['pass']} |")
print(f'\n**Decision: {decision}.** UP-PG-08 starter half proceeds: {starter}. Out-of-sample check (recomputed within 2% of billed in every run): {oos}.')
print('\n| run | arm | PASS | ORACLE | CHART | total $ | diag $ | diag >100k | delegations | backgrounded | main requests while subagent ran | subagent $ | subagent first prompt | brief chars | reply bytes | warnings | recomputed/billed |')
print('|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|')
for k, r in runs.items():
    print(f"| {k} | {r['arm']} | {r['PASS']} | {r['ORACLE']} | {r['CHART']} | {r['total']:.4f} | {r['diagCost']:.4f} | {r['diagOver100k']} | {r['delegations']} | "
          f"{r['backgrounded']} | {r['mainWhileSub']} | {r['subagentCost'] if r['subagentCost'] is not None else '-'} | {','.join(map(str, r['subFirstPrompts'])) or '-'} | "
          f"{r['briefChars'] if r['briefChars'] is not None else '-'} | {r['replyBytes'] if r['replyBytes'] is not None else '-'} | {'; '.join(r['warnings']) or '-'} | {r['ratio']} |")
print('\n' + json.dumps({'decision': decision, 'starterHalf': starter, 'outOfSample': oos, 'rungs': res}, default=str))
