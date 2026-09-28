#!/usr/bin/env python3
"""EB.F3 follow-up — is a capture of the SAME evidence the same on another machine?

Both machines run THIS script, unchanged, and compare its output. Everything that could vary is fixed here:
the fixtures, the window, the chart, the walk, the notes. What is left to differ is the machine.

The bundle's IDENTITY is expected to differ — the manifest carries createdAt — so identity is not the test.
The test is the member digests, and within the profile, the parts that bind evidence: the walk's target
digests and its run basis. Those must be machine-independent or a chart step captured on one machine cannot
be current on another.

Usage:  python3 capture-determinism.py <path-to-analyser.jar> <empty-work-dir>
"""
import hashlib, json, os, re, shutil, subprocess, sys, time, urllib.request, zipfile

JAR, WORK = sys.argv[1], os.path.abspath(sys.argv[2])
REPO = os.path.dirname(os.path.abspath(__file__)).split("/docs/")[0]
LOG = f"{REPO}/src/main/resources/demo/demo-quote-series.yaml"
GRAPH = f"{REPO}/src/test/resources/topology/demo-quote-processor.graphml"
NOTES = ("DEMO bundle for the EB.F3 capture-determinism test. Fixed text, so the notes member is identical "
         "on every machine that runs this script.")
CHART = {"name": "DEMO mid and spread", "series": ["priceListener.mid", "quotePublisher.spread"], "style": "step"}
WALK = {"name": "DEMO cross-machine walk",
        "title": "Three kinds of target, for a recipient who has never seen this log",
        "steps": [
            {"caption": "A record target: the row this walk was written against.",
             "view": {"tab": "summary", "record": 5},
             "targets": [{"target": "records:row:5", "caption": "record 5, bound by its digest"}]},
            {"caption": "A chart target: its run basis is the loaded files plus the record count.",
             "view": {"tab": "graph", "graph": "DEMO mid and spread"},
             "targets": [{"target": "graph:DEMO mid and spread", "caption": "mid and spread"}]},
            {"caption": "A topology target: the graph digest, and the pairing verdict beside it.",
             "view": {"tab": "topology"},
             "targets": [{"target": "topology:verdict", "caption": "all 5 logged nodes declared"}]}]}

def call(url, token, action, params):
    req = urllib.request.Request(url, method="POST",
                                 data=json.dumps({"action": action, "params": params}).encode(),
                                 headers={"X-Analyser-Token": token, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)

def main():
    shutil.rmtree(WORK, ignore_errors=True)
    home, out = f"{WORK}/home", f"{WORK}/out"
    os.makedirs(f"{home}/.fluxtion-analyser"); os.makedirs(out)
    open(f"{home}/.fluxtion-analyser/config", "w").write(
        f"assistant.rest=true\nassistant.exports=true\nassistant.exportDir={out}\n"
        "windowX=60\nwindowY=60\nwindowW=1440\nwindowH=900\n")
    env = dict(os.environ, HOME=home)
    log = open(f"{WORK}/gui.log", "w")
    proc = subprocess.Popen(["java", f"-Duser.home={home}", "-jar", JAR], stdout=log, stderr=log, env=env)
    try:
        url = token = None
        for _ in range(60):
            time.sleep(1)
            text = open(f"{WORK}/gui.log").read()
            m, t = re.search(r"http://127\.0\.0\.1:\d+", text), re.search(r"X-Analyser-Token: (\S+)", text)
            if m and t:
                url, token = m.group(0) + "/action", t.group(1); break
        if not url:
            print("the analyser did not start; see", f"{WORK}/gui.log"); return 1

        call(url, token, "open", {"log": LOG});   time.sleep(8)
        call(url, token, "open", {"graphml": GRAPH}); time.sleep(3)
        call(url, token, "graph", CHART);         time.sleep(4)
        call(url, token, "walk", WALK)
        fexp = f"{out}/determinism.fexp"
        call(url, token, "report", {"bundle": {"path": fexp, "notes": NOTES}})
        for _ in range(40):
            time.sleep(1)
            cap = (call(url, token, "context", {}) or {}).get("context", {}).get("capture") or {}
            if cap.get("phase") in ("WRITTEN", "REFUSED", "FAILED"): break
        if cap.get("phase") != "WRITTEN":
            print("capture did not complete:", json.dumps(cap)); return 1

        with zipfile.ZipFile(fexp) as z:
            manifest = json.loads(z.read("manifest.json"))
            profile = z.read("profile/project.fluxtion-settings").decode()
        print("identity (EXPECTED TO DIFFER — the manifest carries createdAt):")
        print("   ", "sha256:" + hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()[:16], "…")
        print("\nmember digests — THESE MUST MATCH on every machine:")
        for m in sorted(manifest["members"], key=lambda x: x["path"]):
            print(f"    {m['path']:<40} {m['sha256'][:32]}…  {m['bytes']} bytes")
        # the profile carries when the walk was saved; strip what is a clock, keep what binds evidence
        varying = re.compile(r"^(walk\.\d+\.(created|updated)|report\.\d+\.created|profileNonce)=")
        kept = sorted(l for l in profile.splitlines() if l and not l.startswith("#") and not varying.match(l))
        print("\nprofile, with clocks and the nonce removed — MUST MATCH:")
        print("    sha256:" + hashlib.sha256("\n".join(kept).encode()).hexdigest())
        print("\nthe lines that bind evidence — MUST MATCH, line for line:")
        for l in kept:
            if re.search(r"\.(basis|digest|run\.\d+|run\.count|target)=|fp\.(records|first|last)=", l):
                print("   ", l)
        print("\nthis machine:", subprocess.run(["uname", "-sm"], capture_output=True, text=True).stdout.strip(),
              "| java", subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()[0])
        return 0
    finally:
        proc.terminate()
        try: proc.wait(timeout=10)
        except subprocess.TimeoutExpired: proc.kill()

sys.exit(main())
