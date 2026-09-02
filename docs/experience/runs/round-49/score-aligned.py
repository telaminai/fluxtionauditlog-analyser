#!/usr/bin/env python3
"""Score on business outcomes, ALIGNED BY SCENARIO POSITION.

Fixes a real defect in score-idiomatic.py: it dropped record-groups that carried no values, so an
event which legitimately publishes nothing shifted every later comparison by one and reported the
whole tail as wrong. Both architectures may publish on different events; the spine must be the
scenario, not the log. STRATEGY lines are excluded because the reference publishes nothing for them.
"""
import re, sys

FIGURES = ["marketdata.mid","marketdata.depth","marketdata.vol","marketdata.ewma",
           "pricing.adjusted","pricing.spread","liquidity.book","liquidity.score",
           "risk.notional","risk.exposure","risk.var",
           "capital.charge","capital.buffer","capital.fee"]
SCORED = {"config","tick","rate","trade"}

def fluxtion_state(path):
    out, cur = [], {}
    for b in re.split(r'(?=eventLogRecord:)', open(path).read()):
        m = re.search(r'^\s*event: (\S+)', b, re.M)
        if not m: continue
        ev = m.group(1).split('$')[-1].lower()
        if ev not in SCORED: continue
        for s, v in re.findall(r'stage: ([\w.]+),?\s*\n?\s*value: ([-\d.eE+]+)', b):
            cur[s] = float(v)
        out.append((ev, dict(cur)))
    return out

def plain_state(path):
    """Keep EVERY scored event group, even one that published no values."""
    out, cur = [], {}
    for line in open(path):
        p = [x.strip() for x in line.strip().split(',')]
        if not p or not p[0]: continue
        if p[0].upper() == 'EVENT':
            ev = p[1].lower()
            if ev in SCORED: out.append([ev, None])
            else: out.append(None)          # placeholder, dropped below
            continue
        if len(p) >= 2 and out and out[-1] is not None:
            try: cur[p[0]] = float(p[1])
            except ValueError: continue
        if out and out[-1] is not None: out[-1][1] = dict(cur)
    # An event that published nothing carries the state it had AT THAT MOMENT.
    # Using the file-final `cur` here was a defect: it back-dated the end state onto
    # every silent event and reported the whole tail as wrong.
    running, fixed = {}, []
    for e in out:
        if e is None: continue
        if e[1]: running = e[1]
        fixed.append((e[0], dict(running)))
    return fixed

exp, got = fluxtion_state(sys.argv[1]), plain_state(sys.argv[2])
ea = [l.strip() for l in open(sys.argv[3]) if l.strip()]
ga = [l.strip() for l in open(sys.argv[4]) if l.strip()]
score = total = 0; notes = []
total += 1
if len(exp) == len(got): score += 1
else: notes.append(f"event count: expected {len(exp)}, got {len(got)}")
for i, (e, g) in enumerate(zip(exp, got)):
    total += 1
    bad = [k for k in FIGURES if k in e[1] and abs(e[1][k] - g[1].get(k, 1e9)) > 1e-3]
    if not bad: score += 1
    else: notes.append(f"[{i} {e[0]}] wrong: {bad[:4]}"
                       + (f"  e.g. {bad[0]} expected {e[1][bad[0]]:.4f} got {g[1].get(bad[0], float('nan')):.4f}"))
total += 1
if ea == ga: score += 1
else: notes.append(f"alerts: expected {len(ea)} {ea}, got {len(ga)} {ga}")
for k in ("capital.breachCount","capital.alertCount","risk.streak"):
    total += 1
    ev = [round(s.get(k, -1), 4) for _, s in exp]; gv = [round(s.get(k, -1), 4) for _, s in got]
    if ev == gv: score += 1
    else: notes.append(f"{k}: expected {ev[-3:]}, got {gv[-3:]}")
print(f"SCORE {score}/{total}")
for n in notes[:10]: print("  " + n)
