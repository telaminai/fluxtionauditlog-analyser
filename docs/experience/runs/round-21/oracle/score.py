#!/usr/bin/env python3
"""Score a plain results file: eventNumber,detector,raised|suppressed.

Behaviour only. The framework-observability checks used for the Fluxtion cells (record count, quiet
cycles, path length) have no natural equivalent in a plain Java engine and are deliberately not scored
here — this measures the same DOMAIN decisions the Fluxtion cells were measured on, nothing more.
"""
import sys,re
EXPECT = {(9,"E1","raised"),(14,"E1","suppressed"),(15,"E3","raised"),
          (16,"E3","suppressed"),(19,"E2","raised")}
def main(p):
    got=set()
    for line in open(p):
        line=line.strip()
        if not line or line.startswith("#"): continue
        f=[x.strip() for x in line.split(",")]
        if len(f)>=3 and f[0].isdigit():
            got.add((int(f[0]), f[1].upper(), f[2].lower()))
    R=[]
    for e in sorted(EXPECT):
        R.append((f"V{sorted(EXPECT).index(e)+1} event {e[0]}: {e[1]} {e[2]}", e in got, ""))
    extra = got - EXPECT
    R.append(("V6 no spurious alerts", not extra, f"extra={sorted(extra)}" if extra else ""))
    ok=sum(1 for _,b,_ in R if b)
    for a,b,c in R: print(f"  [{'PASS' if b else 'FAIL'}] {a:<34} {c}")
    print(f"  ---- {ok}/{len(R)} ----")
if __name__=="__main__": main(sys.argv[1])
