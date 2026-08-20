import json, pathlib, urllib.request, urllib.error
ep=json.loads((pathlib.Path.home()/".fluxtion-analyser"/"rest-endpoint").read_text())
def act(v,p=None):
    b=json.dumps({"v":1,"action":v,"params":p or {}}).encode()
    r=urllib.request.Request(ep["url"]+"/action",data=b,method="POST",
      headers={"Content-Type":"application/json","X-Analyser-Token":ep["token"]})
    try:
        with urllib.request.urlopen(r,timeout=60) as f: return json.loads(f.read())
    except urllib.error.HTTPError as e: return {"ok":False,"error":f"HTTP {e.code}: {e.read().decode()[:200]}"}
print("STEP-THROUGH over record 74 — the order the tool presents as dispatch order:")
act("topology",{"recordIndex":74})
for i in range(6):
    t=act("topology",{"step":1}).get("topology",{})
    print(f"   step {i+1}: row={t.get('rowIndex')}  current={t.get('currentNode')!r}  {t.get('position')}")
