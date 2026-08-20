import json, pathlib, urllib.request, urllib.error
ep=json.loads((pathlib.Path.home()/".fluxtion-analyser"/"rest-endpoint").read_text())
def act(v,p=None):
    b=json.dumps({"v":1,"action":v,"params":p or {}}).encode()
    r=urllib.request.Request(ep["url"]+"/action",data=b,method="POST",
      headers={"Content-Type":"application/json","X-Analyser-Token":ep["token"]})
    try:
        with urllib.request.urlopen(r,timeout=60) as f: return json.loads(f.read())
    except urllib.error.HTTPError as e: return {"ok":False,"error":f"HTTP {e.code}: {e.read().decode()[:200]}"}

print("A. Does the translator's synthetic '_concurrent' node resolve as DATA?")
print("  ", json.dumps(act("series",{"expr":"_concurrent.nodes"}))[:200])

print("\nB. Is an OBSERVED-BUT-UNDECLARED node reported anywhere? (_concurrent is in no GraphML)")
c=act("coverage",{}); cov=c.get("coverage",{})
print("   declared:",cov.get("declared"),"covered:",cov.get("covered"),"uncovered:",cov.get("uncovered"))
print("   keys returned:",sorted(cov.keys()))

print("\nC. STEP-THROUGH: what order does the topology walk a record's nodeLogs in?")
act("goto",{"recordIndex":74,"reveal":True})
for i in range(5):
    t=act("topology",{"step":"next","recordIndex":74}).get("topology",{})
    print(f"   step {i}: row={t.get('rowIndex')} current={t.get('currentNode')} pos={t.get('position')}")
