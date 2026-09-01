#!/usr/bin/env python3
"""Score a Fluxtion NATIVE audit log against the hold-out. No derived log shape is required.

Round 15 required agents to emit a hand-built record carrying path/pathLength/detectorsTripped.
That is a verbatim restatement of what nodeLogs already contains, and hand-writing it recreates the
exact narration problem the framework's own log avoids. It is gone. Two one-line conventions remain:
a detector logs `detector: Dn` and `tripped: true|false`; the alert gate logs `raised`/`suppressed`.
"""
import sys,re
def biz(t):
    return [r for r in t.split("eventLogRecord")[1:]
            if not re.search(r'LifecycleEvent|EventLogConfig',r)]
def nodes(r): return re.findall(r'^\s*- (\w+): \{',r,re.M)
def trips(r): return set(re.findall(r'detector: (D\d)[^}]*?tripped: true',r))
def main(p):
    recs=biz(open(p).read()); R=[]
    n=len(recs)
    R.append(("N1 21 business records", n==21, f"got {n}"))
    if n!=21:
        for x,y,z in R: print(f"  [{'PASS' if y else 'FAIL'}] {x:<38} {z}")
        print("  ---- 0/8 (cannot score further) ----"); return
    fired={i:trips(r) for i,r in enumerate(recs,1) if trips(r)}
    R.append(("N2 D2 trips only at cycle 14",[i for i,d in fired.items() if 'D2' in d]==[14],str([i for i,d in fired.items() if 'D2' in d])))
    R.append(("N3 D1 trips only at cycle 18",[i for i,d in fired.items() if 'D1' in d]==[18],str([i for i,d in fired.items() if 'D1' in d])))
    R.append(("N4 D3 trips only at 19 and 20",[i for i,d in fired.items() if 'D3' in d]==[19,20],str([i for i,d in fired.items() if 'D3' in d])))
    R.append(("N5 repeat ROSTER runs no detector", not any(d.lower().startswith('d') and re.match(r'd\d',d.lower()) for d in nodes(recs[3])), str(nodes(recs[3]))))
    q=[len(nodes(recs[i-1])) for i in (3,21)]; o=[len(nodes(recs[i-1])) for i in (14,19)]
    R.append(("N6 quote path shorter than order", max(q)<min(o), f"quote={q} order={o}"))
    r19,r20=recs[18],recs[19]
    R.append(("N7 R1 raised, R2 suppressed",
              bool(re.search(r'raised: (1|true)',r19)) and bool(re.search(r'suppressed: (1|true)',r20)),
              ""))
    R.append(("N8 no other cycle trips", set(fired)=={14,18,19,20}, str(sorted(fired))))
    ok=sum(1 for _,p_,_ in R if p_)
    for x,y,z in R: print(f"  [{'PASS' if y else 'FAIL'}] {x:<38} {z}")
    print(f"  ---- {ok}/{len(R)} ----")
if __name__=="__main__": main(sys.argv[1])
