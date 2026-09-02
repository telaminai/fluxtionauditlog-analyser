#!/usr/bin/env python3
"""Score the EVALUATION file: the checks that only a correctly ordered graph can pass.

Decision correctness is scored separately. These are the properties that distinguish an engine which
evaluates in dependency order, once per node, from one that propagates eagerly or recomputes blindly.
Node names are matched by substring so an engine may name its nodes what it likes.
"""
import sys, re

DEPTH = [("position", 0), ("mark", 1), ("base", 2), ("exposure", 3), ("util", 4)]

def depth_of(name):
    n = name.lower()
    for key, d in DEPTH:
        if key in n:
            return d
    return None

def parse(path):
    rows = {}
    for line in open(path):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        num, _, rest = line.partition(",")
        if not num.isdigit():
            continue
        rows[int(num)] = [p for p in rest.split("|") if p]
    return rows

def main(path):
    rows = parse(path)
    R = []

    dup = [(n, v) for n, v in rows.items()
           if len([x for x in v if not x.startswith("commit:")]) !=
              len({x for x in v if not x.startswith("commit:")})]
    R.append(("O1 each node evaluates at most once per event", not dup,
              f"repeats in events {[n for n,_ in dup][:3]}" if dup else ""))

    bad = []
    for n, v in rows.items():
        seen = [d for d in (depth_of(x) for x in v if not x.startswith("commit:")) if d is not None]
        if seen != sorted(seen):
            bad.append(n)
    R.append(("O2 evaluation follows dependency depth", not bad,
              f"out of order in events {bad[:3]}" if bad else ""))

    cbad = []
    for n, v in rows.items():
        c = [depth_of(x[len("commit:"):]) for x in v if x.startswith("commit:")]
        c = [d for d in c if d is not None]
        if c and c != sorted(c, reverse=True):
            cbad.append(n)
    R.append(("O4 commits run in reverse depth order", not cbad,
              f"forward-ordered in events {cbad[:3]}" if cbad else ""))

    ok = sum(1 for _, p, _ in R if p)
    for a, p, d in R:
        print(f"  [{'PASS' if p else 'FAIL'}] {a:<44} {d}")
    print(f"  ---- {ok}/{len(R)} ----")

if __name__ == "__main__":
    main(sys.argv[1])
