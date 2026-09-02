#!/usr/bin/env python3
"""Score the idiomatic arm on BUSINESS OUTCOMES, not on which records appear.

The two architectures legitimately emit different traces - Fluxtion arrests a path and records
nothing, an idiomatic component recomputes and records. Comparing stage sets would score
architecture, not correctness. So this compares, after each event: the current value of every
published figure, the alerts published, and the stateful counters.
"""
import re, sys

FIGURES = ["marketdata.mid","marketdata.depth","marketdata.vol","marketdata.ewma",
           "pricing.adjusted","pricing.spread","liquidity.book","liquidity.score",
           "risk.notional","risk.exposure","risk.var",
           "capital.charge","capital.buffer","capital.fee"]

def fluxtion_state(path):
    """Walk the framework audit log, carrying the last value of every figure forward."""
    out, cur = [], {}
    for b in re.split(r'(?=eventLogRecord:)', open(path).read()):
        m = re.search(r'^\s*event: (\S+)', b, re.M)
        if not m: continue
        ev = m.group(1).split('$')[-1].lower()
        if ev not in {"config","tick","rate","trade"}: continue
        for s, v in re.findall(r'stage: ([\w.]+),?\s*\n?\s*value: ([-\d.eE+]+)', b):
            cur[s] = float(v)
        out.append((ev, dict(cur)))
    return out

def plain_state(path):
    out, cur = [], {}
    for line in open(path):
        line = line.strip()
        if not line: continue
        p = line.split(',')
        if p[0].strip().upper() == 'EVENT':
            if out or cur: pass
            out.append([p[1].strip().lower(), None])
            continue
        if len(p) >= 2 and out:
            try: cur[p[0].strip()] = float(p[1])
            except ValueError: continue
            out[-1][1] = dict(cur)
    return [(e, s) for e, s in out if s]

exp = fluxtion_state(sys.argv[1])
got = plain_state(sys.argv[2])
ea  = [l.strip() for l in open(sys.argv[3]) if l.strip()]
ga  = [l.strip() for l in open(sys.argv[4]) if l.strip()]

score = total = 0
notes = []
total += 1
if len(exp) == len(got): score += 1
else: notes.append(f"event count: expected {len(exp)}, got {len(got)}")
for i, (e, g) in enumerate(zip(exp, got)):
    total += 1
    bad = [k for k in FIGURES if k in e[1] and abs(e[1][k] - g[1].get(k, 1e9)) > 1e-3]
    if not bad: score += 1
    else: notes.append(f"[{i} {e[0]}] wrong: {bad[:4]}")
total += 1
if ea == ga: score += 1
else: notes.append(f"alerts: expected {len(ea)}, got {len(ga)}")
for k in ("capital.breachCount","capital.alertCount","risk.streak"):
    total += 1
    ev = [round(s.get(k, -1), 4) for _, s in exp]
    gv = [round(s.get(k, -1), 4) for _, s in got]
    if ev == gv: score += 1
    else: notes.append(f"{k}: expected {ev[-3:]}, got {gv[-3:]}")
print(f"SCORE {score}/{total}")
for n in notes[:8]: print("  " + n)
