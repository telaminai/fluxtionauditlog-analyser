import json, pathlib, urllib.request, urllib.error
ep=json.loads((pathlib.Path.home()/".fluxtion-analyser"/"rest-endpoint").read_text())
def act(v,p=None):
    b=json.dumps({"v":1,"action":v,"params":p or {}}).encode()
    r=urllib.request.Request(ep["url"]+"/action",data=b,method="POST",
      headers={"Content-Type":"application/json","X-Analyser-Token":ep["token"]})
    try:
        with urllib.request.urlopen(r,timeout=60) as f: return json.loads(f.read())
    except urllib.error.HTTPError as e: return {"ok":False,"error":f"HTTP {e.code}: {e.read().decode()[:300]}"}
print("B. loggedButNotInTopology / warning:")
cov=act("coverage",{}).get("coverage",{})
print("   loggedButNotInTopology:",json.dumps(cov.get("loggedButNotInTopology"))[:300])
print("   warning:",json.dumps(cov.get("warning"))[:300])
print("\nC. the topology verb's declared 'step' values:")
m=urllib.request.Request(ep["url"]+"/manifest",headers={"X-Analyser-Token":ep["token"]})
with urllib.request.urlopen(m,timeout=30) as f: man=json.loads(f.read())
props=man.get("verbs",man).get("topology",{})
print("   ", json.dumps(props)[:900])
