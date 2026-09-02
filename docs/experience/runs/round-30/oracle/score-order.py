#!/usr/bin/env python3
"""Score the EVALUATION file.

CORRECTED. The first version required evaluation depth to be non-decreasing across a whole line and
commit depth to be globally descending. Both are wrong when an event touches independent subgraphs: a
PRICE affecting two books may evaluate one book's whole chain then the other's, and either interleaving
is correct because the books do not depend on each other. It scored a correct engine as failing.

The properties that actually matter:
  O1 no node evaluates twice in one event
  O2 within one subject (a book, or a book:instrument), depth never goes backwards
  O4 the commit sequence is exactly the reverse of the evaluation sequence
"""
import sys, re

DEPTH = [("position", 0), ("mark", 1), ("base", 2), ("exposure", 3), ("util", 4)]

def depth_of(name):
    n = name.lower()
    for key, d in DEPTH:
        if key in n:
            return d
    return None

def subject(name):
    m = re.search(r'[(\[]([^)\]]+)[)\]]', name)
    if m:
        return m.group(1).split(":")[0]      # book, so a book's own chain is one subject
    return ""

def parse(path):
    rows = {}
    for line in open(path):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        num, _, rest = line.partition(",")
        if not num.strip().isdigit():
            continue
        rows[int(num)] = [p for p in rest.split("|") if p]
    return rows

def main(path):
    rows = parse(path)
    R = []

    dup = [n for n, v in rows.items()
           if len([x for x in v if not x.startswith("commit:")]) !=
              len({x for x in v if not x.startswith("commit:")})]
    R.append(("O1 no node evaluates twice in one event", not dup, f"events {dup[:3]}" if dup else ""))

    bad = []
    for n, v in rows.items():
        evals = [x for x in v if not x.startswith("commit:")]
        per = {}
        for x in evals:
            d = depth_of(x)
            if d is None:
                continue
            per.setdefault(subject(x), []).append(d)
        if any(seq != sorted(seq) for seq in per.values()):
            bad.append(n)
    R.append(("O2 within a subject, depth never goes back", not bad, f"events {bad[:3]}" if bad else ""))

    cbad = []
    for n, v in rows.items():
        evals = [x for x in v if not x.startswith("commit:")]
        commits = [x[len("commit:"):] for x in v if x.startswith("commit:")]
        if commits and commits != list(reversed(evals)):
            cbad.append(n)
    R.append(("O4 commits are the reverse of evaluation", not cbad, f"events {cbad[:3]}" if cbad else ""))

    ok = sum(1 for _, p, _ in R if p)
    for a, p, d in R:
        print(f"  [{'PASS' if p else 'FAIL'}] {a:<42} {d}")
    print(f"  ---- {ok}/{len(R)} ----")

if __name__ == "__main__":
    main(sys.argv[1])
