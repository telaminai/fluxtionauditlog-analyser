#!/usr/bin/env python3
"""Score a Fluxtion native audit log for round 18 (fleet telemetry)."""
import sys,re
def biz(t): return [r for r in t.split("eventLogRecord")[1:] if not re.search(r'LifecycleEvent|EventLogConfig',r)]
def nodes(r): return re.findall(r'^\s*- (\w+): \{',r,re.M)
def trips(r): return set(re.findall(r'detector: (E\d)[^}]*?tripped: true',r))
def main(p):
    recs=biz(open(p).read()); R=[]; n=len(recs)
    R.append(("P1 21 business records", n==21, f"got {n}"))
    if n!=21:
        for a,b,c in R: print(f"  [{'PASS' if b else 'FAIL'}] {a:<40} {c}")
        print("  ---- 0/8 (cannot score further) ----"); return
    fired={i:trips(r) for i,r in enumerate(recs,1) if trips(r)}
    at=lambda d:[i for i,s in fired.items() if d in s]
    R.append(("P2 E1 trips at 9 and 14 only", at('E1')==[9,14], str(at('E1'))))
    R.append(("P3 E2 trips at 19 only",        at('E2')==[19],   str(at('E2'))))
    R.append(("P4 E3 trips at 15 and 16 only", at('E3')==[15,16],str(at('E3'))))
    quiet=lambda i: not re.search(r'detector: E\d',recs[i-1])
    R.append(("P5 repeated ROSTER/LIMIT quiet", quiet(4) and quiet(21), f"c4={quiet(4)} c21={quiet(21)}"))
    def gate(i):
        m=re.search(r'raised: (\d+), suppressed: (\d+)',recs[i-1]); return (int(m.group(1)),int(m.group(2))) if m else None
    raised_ok  = all((gate(i) or (0,0))[0]>=1 for i in (9,15,19))
    suppress_ok= all((gate(i) or (0,0))[1]>=1 for i in (14,16))
    R.append(("P6 raises 9/15/19, suppresses 14/16", raised_ok and suppress_ok,
              f"{[gate(i) for i in (9,14,15,16,19)]}"))
    R.append(("P7 no other cycle trips", set(fired)=={9,14,15,16,19}, str(sorted(fired))))
    R.append(("P8 reference cycle shorter than telemetry", len(nodes(recs[2]))<len(nodes(recs[8])),
              f"limit={len(nodes(recs[2]))} telem={len(nodes(recs[8]))}"))
    ok=sum(1 for _,b,_ in R if b)
    for a,b,c in R: print(f"  [{'PASS' if b else 'FAIL'}] {a:<40} {c}")
    print(f"  ---- {ok}/{len(R)} ----")
if __name__=="__main__": main(sys.argv[1])
