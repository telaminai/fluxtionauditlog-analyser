#!/usr/bin/env python3
"""Structural gate for round 14.

FIX (applied before any cell was scored): the first version checked node-name literals in EVERY file
outside node/, which includes the graph builder. Registering a node under a name is legitimate wiring in
both arms — Fluxtion's addNode(instance,"orderBook") and any hand-rolled registry both do it. Checking
there would have failed every Fluxtion cell and passed a vanilla monolith that registers nothing.
Requirement 2 says the code that EMITS the audit log must not name nodes. So only emitters are checked.
"""
import sys,os,re,glob
root=sys.argv[1]
main=os.path.join(root,"src/main/java/com/acme/surveillance")
nodes=sorted(glob.glob(os.path.join(main,"node","*.java")))
names={os.path.basename(p)[:-5] for p in nodes}
lower={n[0].lower()+n[1:] for n in names}
allj=[f for f in glob.glob(os.path.join(main,"**","*.java"),recursive=True)]
def txt(f): return open(f,errors="ignore").read()
# an emitter is code that writes the record/log shape
emitters=[f for f in allj if re.search(r'nodeLogs|pathLength|detectorsTripped|surveillance-audit',txt(f))
          and "/node/" not in f]
viol=[]
for f in emitters:
    for lit in re.findall(r'"([A-Za-z][A-Za-z0-9_]{2,})"',txt(f)):
        if lit in names or lit in lower: viol.append((os.path.basename(f),lit))
typed=sum(len(re.findall(r'path\.add\("|path\.append\("',txt(f))) for f in allj)
print(f"  node classes in …/node/        : {len(nodes)}")
print(f"  emitter files                  : {len(emitters)}")
print(f"  node-name literals in emitters : {len(viol)}" + (f"  e.g. {viol[:3]}" if viol else ""))
print(f"  hand-typed path.add(\"…\")       : {typed}")
ok = len(nodes)>=10 and not viol and typed==0
print(f"  ---- GATE {'PASS' if ok else 'FAIL'} ----")
