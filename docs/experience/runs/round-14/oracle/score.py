#!/usr/bin/env python3
"""Score an engine's audit log against the hold-out expectations. Records matched by content."""
import re,sys

def blocks(t):
    idx=[m.start() for m in re.finditer(r'^\s*-?\s*record:',t,re.M)]
    if not idx: idx=[m.start() for m in re.finditer(r'^\s*cycle:',t,re.M)]
    return [t[a:b] for a,b in zip(idx,idx[1:]+[len(t)])]

def lst(b,key):
    m=re.search(key+r'\s*:\s*\[(.*?)\]',b,re.S)
    return [x.strip().strip('"\'') for x in m.group(1).split(',') if x.strip()] if m else []

def has(b,key,det):   # detector named in an alert list section
    m=re.search(key+r'\s*:\s*(.*?)(?=\n\s{0,6}[a-zA-Z]+\s*:|\Z)',b,re.S)
    return bool(m and re.search(r'"?'+det+r'"?',m.group(1)))

def find(bs,token):
    hits=[b for b in bs if token in b]
    return hits[-1] if hits else None

def main(path):
    t=open(path).read(); bs=blocks(t)
    biz=[b for b in bs if not re.search(r'LifecycleEvent|EventLogControl|EventLogConfig',b)]
    R=[]
    R.append(("H1 24 business records", len(biz)==24, f"got {len(biz)}"))
    lasts=[lst(b,'path')[-1] for b in biz if lst(b,'path')]
    R.append(("H2 same terminal node every record",
              bool(lasts) and len(set(lasts))==1 and len(lasts)==len(biz),
              f"{len(set(lasts))} distinct terminals over {len(lasts)}/{len(biz)} records"))
    rep=[b for b in biz if 'DEMO1' in b and ('TECH' in b or 'InstrumentStatic' in b)]
    quiet=[b for b in biz if not lst(b,'detectorsTripped') and not lst(b,'detectorsEvaluatedNotTripped')]
    R.append(("H3 republishes evaluate no detector", len(quiet)>=2, f"{len(quiet)} fully-quiet cycles"))
    for tag,tok,det in (("H4 S10 trips D5","S10","D5"),("H5 W2 exec trips D3","W2","D3"),
                        ("H6 R1 trips D6","R1","D6")):
        b=find(biz,tok); R.append((tag, bool(b) and det in lst(b,'detectorsTripped'),
                                   "record not found" if not b else str(lst(b,'detectorsTripped'))))
    b=find(biz,'R1'); R.append(("H6b R1 alert is raised", bool(b) and has(b,'alerts','D6'),"" ))
    b=find(biz,'R2'); R.append(("H7 R2 alert suppressed",
        bool(b) and has(b,'suppressedAlerts','D6') and not re.search(r'\n\s*alerts:\s*\n\s*-.*D6',b),""))
    q=find(biz,'QUOTE') or find(biz,'Quote'); w=find(biz,'W2')
    def pl(b):
        m=re.search(r'pathLength\s*:\s*(\d+)',b or ''); return int(m.group(1)) if m else None
    R.append(("H8 quote path shorter than exec", bool(pl(q) and pl(w) and pl(q)<pl(w)), f"quote={pl(q)} exec={pl(w)}"))
    ok=sum(1 for _,p,_ in R if p)
    for n,p,d in R: print(f"  [{'PASS' if p else 'FAIL'}] {n:<38} {d}")
    print(f"  ---- {ok}/{len(R)} ----")
    return ok,len(R)

if __name__=="__main__": main(sys.argv[1])
