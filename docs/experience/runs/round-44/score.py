#!/usr/bin/env python3
"""Score one arm against the held-out scenario, reading its native trace.

Only DATA events are scored (config/tick/rate/trade). A STRATEGY line is an operator action, not a
business event, and the two arms record it differently by design; its effect is scored where it is
actually observable - in capital.fee on the events that follow.
"""
import re, sys
from collections import Counter
from decimal import Decimal, ROUND_HALF_UP

DATA = {"config", "tick", "rate", "trade"}

def parse_fluxtion(text):
    rows = []
    for b in re.split(r'(?=eventLogRecord:)', text):
        m = re.search(r'^\s*event: (\S+)', b, re.M)
        if not m:
            continue
        ev = m.group(1).split('$')[-1].lower()
        if ev not in DATA:
            continue
        st = [(s, float(v)) for s, v in
              re.findall(r'stage: ([\w.]+),?\s*\n?\s*value: ([-\d.eE+]+)', b)]
        if st:
            rows.append((ev, st))
    return rows

def parse_plain(text):
    rows, cur = [], None
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        p = line.split(',')
        head = p[0].strip().upper()
        if head == 'EVENT':
            ev = p[1].strip().lower()
            cur = (ev, []) if ev in DATA else None
            if cur:
                rows.append(cur)
        elif cur is not None and len(p) >= 2:
            try:
                cur[1].append((p[0].strip(), float(p[1])))
            except ValueError:
                pass                      # a NOTE line, not a measurement
    return [(e, s) for e, s in rows if s]

def hu(v, dp):
    return float(Decimal(repr(v)).quantize(Decimal(1).scaleb(-dp), rounding=ROUND_HALF_UP))

def score(exp, got, dp, deps):
    s = t = 0
    notes = []
    t += 1
    if [e for e, _ in exp] == [e for e, _ in got]:
        s += 1
    else:
        notes.append(f"R0 events with activity: expected {len(exp)} {[e for e,_ in exp]}, "
                     f"got {len(got)} {[e for e,_ in got]}")
    for i, (e, g) in enumerate(zip(exp, got)):
        ev = e[0]
        es, gs = {k for k, _ in e[1]}, {k for k, _ in g[1]}
        t += 1
        if es == gs:
            s += 1
        else:
            extra = sorted(gs - es)
            notes.append(f"R1 [{i} {ev}] never ran {sorted(es-gs)}" +
                         (f" | ran but should NOT have: {extra}" if extra else ""))
        gd = dict(g[1])
        t += 1
        bad = [(k, hu(v, dp), gd.get(k)) for k, v in e[1]
               if k not in gd or abs(gd[k] - hu(v, dp)) > 10 ** -dp]
        if not bad:
            s += 1
        else:
            notes.append(f"R2 [{i} {ev}] {len(bad)} wrong, first {bad[0]}")
        t += 1
        pos = {k: j for j, (k, _) in enumerate(g[1])}
        bad_order = [f"{k} before parent {p}" for k in pos for p in deps.get(k, [])
                     if p in pos and pos[p] > pos[k]]
        if not bad_order:
            s += 1
        else:
            notes.append(f"R3 [{i} {ev}] {bad_order[0]}")
        t += 1
        d = [k for k, n in Counter(k for k, _ in g[1]).items() if n > 1]
        if not d:
            s += 1
        else:
            notes.append(f"R4 [{i} {ev}] ran more than once: {d}")
    return s, t, notes
