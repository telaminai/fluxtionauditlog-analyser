#!/usr/bin/env python3
"""Score one arm against the held-out scenario, reading its NATIVE trace format.

usage: score.py <arm-dir> <fx|van> <fqn> <classpath> <expected.txt> <scenario.txt>

Neither arm is asked to invent an output format. The Fluxtion arm writes the framework's
audit log exactly as it comes; the plain-Java arm writes the records its subsystems emit,
as they come, plus an EVENT line per incoming event. Both are reduced here to the same
comparable structure: [(event, [(stage, value), ...]), ...].

Guards learned the hard way in earlier rounds:
  - the output file is deleted BEFORE the run, so a crashed engine cannot be scored
    against a previous run's file (round 30 scored a stale file for three probes);
  - no output at all is a hard failure, never a vacuous pass.
"""
import subprocess, sys, os, re


def parse_fluxtion(text):
    """The framework's audit log: eventLogRecord blocks, each naming its event and its nodeLogs."""
    rows = []
    for block in re.split(r'\n(?=eventLogRecord)', text):
        m = re.search(r'^\s*event: (\S+)', block, re.M)
        if not m:
            continue
        ev = m.group(1).split('$')[-1].lower()
        if ev in ('eventlogcontrolevent', 'clockstrategyevent'):
            continue
        stages = [(s, float(v)) for s, v in
                  re.findall(r'stage: ([\w.]+),?\s*\n?\s*value: ([-\d.eE+]+)', block)]
        if stages:
            rows.append((ev, stages))
    return rows


def parse_plain(text):
    """EVENT,<type> lines delimiting <stage>,<value> records."""
    rows, cur = [], None
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split(',')
        if parts[0].strip().upper() == 'EVENT':
            cur = (parts[1].strip().lower(), [])
            rows.append(cur)
        elif cur is not None and len(parts) >= 2:
            try:
                cur[1].append((parts[0].strip(), float(parts[1])))
            except ValueError:
                pass
    return [(e, s) for e, s in rows if s]      # an event with no activity contributes no row


arm, fmt, fqn, cp, expected_f, scen = sys.argv[1:7]
out = os.path.join(arm, "score-out.txt")
if os.path.exists(out):
    os.remove(out)

r = subprocess.run(["java", "-cp", cp, fqn, os.path.abspath(scen), out],
                   cwd=arm, capture_output=True, text=True, timeout=300)
if not os.path.exists(out):
    print(f"FAIL: engine produced no output file\nstdout:\n{r.stdout[-2000:]}\nstderr:\n{r.stderr[-2000:]}")
    sys.exit(1)

parser = parse_fluxtion if fmt == 'fx' else parse_plain
got = parser(open(out).read())
exp = parse_fluxtion(open(expected_f).read())
if not got:
    print(f"FAIL: no scoreable records in output\nstdout:\n{r.stdout[-2000:]}\nstderr:\n{r.stderr[-2000:]}")
    sys.exit(1)

score, total, notes = 0, 0, []

total += 1                                     # C1: the right events produced activity, silent ones stayed silent
if [e for e, _ in exp] == [e for e, _ in got]:
    score += 1
else:
    notes.append(f"C1 event sequence: expected {[e for e,_ in exp]}, got {[e for e,_ in got]}")

for i, (e, g) in enumerate(zip(exp, got)):
    ev = e[0]
    total += 1                                 # C2: which stages ran
    es, gs = {k for k, _ in e[1]}, {k for k, _ in g[1]}
    if es == gs:
        score += 1
    else:
        notes.append(f"C2 [{i} {ev}] missing {sorted(es-gs)} extra {sorted(gs-es)}")

    total += 1                                 # C3: values
    gd = dict(g[1])
    bad = [(k, v, gd.get(k)) for k, v in e[1] if k not in gd or abs(gd[k]-v) > 1e-4]
    if not bad:
        score += 1
    else:
        notes.append(f"C3 [{i} {ev}] {len(bad)} wrong, first: {bad[0]}")

    total += 1                                 # C4: run order, over the stages both ran
    eo, go = [k for k, _ in e[1]], [k for k, _ in g[1]]
    if [k for k in go if k in eo] == [k for k in eo if k in go]:
        score += 1
    else:
        notes.append(f"C4 [{i} {ev}] order differs\n     ref {eo}\n     got {go}")

    total += 1                                 # C5: exactly once per event - no double-run
    from collections import Counter
    dupes = [k for k, n in Counter(go).items() if n > 1]
    if not dupes:
        score += 1
    else:
        notes.append(f"C5 [{i} {ev}] ran more than once: {dupes}")

print(f"SCORE {score}/{total}")
for n in notes:
    print("  " + n)
