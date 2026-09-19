#!/usr/bin/env python3
"""Exercise M66 through the packaged app's REST API, with an isolated home and synthetic files.
Run after mvn package with Java 21 on PATH. No generation, network service or real user config is used.
Screenshots and the JSON transcript remain in the printed temporary directory for review.
"""
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent
ROOT = pathlib.Path(tempfile.mkdtemp(prefix="analyser-design-", dir="/tmp"))
HOME = ROOT / "home"
PROJECT = ROOT / "project"
EXPORT = ROOT / "captures"
TRANSCRIPT = []
for directory in (HOME / ".fluxtion-analyser", PROJECT / ".analyser", PROJECT / "target", EXPORT):
    directory.mkdir(parents=True, exist_ok=True)
(HOME / ".fluxtion-analyser/config").write_text(
    f"assistant.rest=true\nassistant.exports=true\nassistant.exportDir={EXPORT}\n"
    f"theme={'Dark' if '--dark' in sys.argv else 'Light'}\nwindowW=1680\nwindowH=1050\nwindowX=50\nwindowY=50\n")
PROFILE = PROJECT / ".analyser/project.fluxtion-settings"
PROFILE.write_text(f"share.version=1\nsourceRoot.count=1\nsourceRoot.0={PROJECT}\n")
XML = "<beans>\n  <bean id='quotePublisher' class='Node'/>\n  <bean id='config' class='FluxtionSpringConfig'>\n    <property name='serviceRegistrations'><list>\n      <bean><property name='nodeBeans'><list><value>missing</value></list></property></bean>\n    </list></property>\n  </bean>\n</beans>\n"
A = PROJECT / "design.xml"
B = PROJECT / "other.xml"
A.write_text(XML)
B.write_text("<beans>\n\n\n<bean id='quotePublisher'/>\n</beans>")
(PROJECT / "Node.java").write_text("class Node { int count; }\n")

def act(verb, params=None, ok=True):
    payload = {"v": 1, "action": verb, "params": params or {}}
    for attempt in range(12):
        request = urllib.request.Request(endpoint["url"] + "/action", data=json.dumps(payload).encode(),
            headers={"Content-Type": "application/json", "X-Analyser-Token": endpoint["token"]})
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                result = json.load(response)
            break
        except urllib.error.HTTPError as error:
            if error.code == 429:
                time.sleep(0.3 * (attempt + 1))
                continue
            result = json.loads(error.read())
            break
    else:
        raise AssertionError("rate limit did not settle")
    TRANSCRIPT.append({"request": payload, "response": result})
    assert result.get("ok") == ok, (payload, result)
    return result

def context():
    return act("context")["context"]

def until(predicate):
    last = None
    for _ in range(30):
        time.sleep(0.2)
        last = context()
        if predicate(last):
            return last
    raise AssertionError(last)

def report(kind, element):
    return {"diagnosticsVersion":"1.0", "sourceRoot":"", "diagnostics":[{
        "code":kind, "severity":"ERROR", "message":"Synthetic declaration needs attention",
        "why":"The referenced declaration is unavailable", "suggestedFix":"Correct the working design",
        "element":element}]}

def write_result(name, value):
    path = PROJECT / "target" / name
    path.write_text(json.dumps(value))
    return path

jar = sorted((REPO / "target").glob("fluxtion-auditlog-analyser-*.jar"))[-1]
log = (ROOT / "app.log").open("w")
process = subprocess.Popen(["java", f"-Duser.home={HOME}", "-jar", str(jar), "--rest"], stdout=log, stderr=log)
try:
    epfile = HOME / ".fluxtion-analyser/rest-endpoint"
    for _ in range(60):
        if epfile.exists():
            break
        if process.poll() is not None:
            raise AssertionError("app exited; inspect " + str(ROOT / "app.log"))
        time.sleep(0.25)
    endpoint = json.loads(epfile.read_text())
    act("open", {"project":str(PROFILE)})
    act("open", {"design":str(A)})
    initial = context()["design"]["revision"]
    assert act("source", {"file":str(B), "bean":"quotePublisher"})["source"]["line"] == 4
    lit = act("spotlight", {"target":"source:design:bean:quotePublisher", "caption":"The declared handler"})
    assert lit["spotlight"]["lit"][0]["file"] == str(A.resolve()), lit
    act("screenshot", {"path":"design-spotlight.png"})
    A.write_text(XML.replace("  <bean id='quotePublisher'", "\n\n  <bean id='quotePublisher'"))
    edited = until(lambda c: c["design"].get("revision") != initial and c.get("spotlight"))
    assert "edited since" in edited["spotlight"]["lit"][0]["caption"], edited
    revision = edited["design"]["revision"]
    A.write_text("<beans><bean")
    malformed = until(lambda c: "error" in c["design"])
    assert malformed["design"]["revision"] == revision
    assert malformed.get("spotlight")
    act("screenshot", {"path":"design-parse-error.png"})
    A.write_text("<beans/>")
    gone = until(lambda c: "source:design:bean:quotePublisher" in c.get("designSpotlights",{}).get("wentOut",[]))
    assert not gone.get("spotlight"), gone
    A.write_text(XML)
    until(lambda c: c["design"].get("revision") == initial)
    act("spotlight", {"target":"source:design:line:2"})
    A.write_text("\n" + XML)
    until(lambda c: "source:design:line:2" in c.get("designSpotlights",{}).get("wentOut",[]))
    A.write_text(XML)
    until(lambda c: c["design"].get("revision") == initial)
    act("source", {"bean":"quotePublisher", "line":2}, ok=False)
    outside = ROOT / "outside.xml"
    outside.write_text("<beans/>")
    act("source", {"file":str(outside)}, ok=False)
    validation = write_result("fluxtion-validation.json", {"contractVersion":"1.0", "valid":False,
        "diagnosticReport":report("SPRING_UNKNOWN_BINDING_NODE", {"kind":"SPRING_SERVICE_BINDING", "beanName":"missing"})})
    loaded = act("open", {"diagnostics":str(validation)})
    assert loaded["opened"]["diagnostics"]["findings"][0]["location"]["line"] == 5, loaded
    act("screenshot", {"path":"producer-findings.png"})
    reconcile = write_result("fluxtion-reconciliation.json", {"schemaVersion":"1.0", "classes":{},
        "diagnosticReport":report("SPRING_RECONCILE_CONFLICT", {"kind":"SOURCE_MEMBER", "className":"Node", "member":"field:count"})})
    loaded = act("open", {"diagnostics":str(reconcile)})
    assert loaded["opened"]["diagnostics"]["findings"][0]["location"]["mode"] == "NODE", loaded
    sidecar = write_result("sidecar.json", report("FLX-1009", {"kind":"NODE", "nodeName":"quotePublisher"}))
    act("open", {"diagnostics":str(sidecar)})
    sidecar.write_text('{"diagnosticsVersion":"99","diagnostics":[]}')
    act("open", {"diagnostics":str(sidecar)}, ok=False)
    assert "diagnosticsFile" not in context()["design"]
    # Keep the XML, edit Java, load an older real demo log, then inspect a failed-build receipt.
    old_source = (PROJECT / "Node.java").read_bytes()
    source_token = "Node.java" + "\0sha256:" + hashlib.sha256(old_source).hexdigest() + "\n"
    old_source_hash = "sha256:" + hashlib.sha256(source_token.encode()).hexdigest()
    (PROJECT / "fluxtion-authoring.json").write_text('{"sourceRoot":"."}')
    (PROJECT / "Node.java").write_text("class Node { int changed; }\n")
    inputs = {"xmlHash":"sha256:" + hashlib.sha256(XML.encode()).hexdigest(), "sourceHash":old_source_hash}
    write_result("fluxtion-run.json", {"schemaVersion":"1.0", "stages":{
        "validate":{"inputs":inputs,"outcome":"ok"}, "build":{"inputs":inputs,"outcome":"failed","compilerRan":False}}})
    runlog = PROJECT / "run.yaml"
    shutil.copyfile(REPO / "src/test/resources/topology/demo-quote-audit.yaml", runlog)
    act("open", {"log":str(runlog)})
    until(lambda c: not c.get("loading",False) and c.get("design",{}).get("file"))
    sidecar.write_text(json.dumps(report("FLX-1009", {"kind":"NODE", "nodeName":"quotePublisher"})))
    stale = act("open", {"diagnostics":str(sidecar)})["opened"]["diagnostics"]
    assert stale["relationship"] == "input-current", stale
    assert stale["inputChecks"]["sourceHash"] == "mismatch", stale
    assert stale["resultStatus"] == "sidecar predates the last attempt", stale
    assert stale["logRelationship"] == "unverified", stale
    act("screenshot", {"path":"stale-inputs.png"})
    selected = act("source", {"bean":"quotePublisher"})["source"]
    assert selected["recordsRelationship"] == "unverified" and selected["source"], selected
    A.write_text(XML.replace("</beans>", "<bean id='quotePublisher'/></beans>"))
    until(lambda c: c["design"].get("revision") != initial)
    act("source", {"bean":"quotePublisher"}, ok=False)
    act("spotlight", {"target":"source:design:bean:quotePublisher"}, ok=False)
    A.write_text(XML)
    until(lambda c: c["design"].get("revision") == initial)
    act("spotlight", {"target":"source:design:bean:quotePublisher"})
    A.write_text(XML.replace("</beans>", "<bean id='quotePublisher'/></beans>"))
    until(lambda c: not c.get("spotlight"))
    outside_log = ROOT / "outside.yaml"
    shutil.copyfile(runlog, outside_log)
    act("open", {"log":str(outside_log)})
    until(lambda c: "file" not in c["design"])
    A.write_text("<beans><bean")
    act("open", {"design":str(B)})
    act("open", {"design":str(A)})
    assert "parseError" in act("source", {"line":1})["source"]
    broken = write_result("broken.json", report("SPRING_XML_NOT_WELL_FORMED", {"kind":"SPRING_DOCUMENT"}))
    loaded = act("open", {"diagnostics":str(broken)})
    assert loaded["opened"]["diagnostics"]["findings"][0]["location"]["line"] == 1
    act("open", {"close":"project"})
    assert "file" not in context()["design"]
    print("PASS: design/glance/spotlight, no-log Follow, malformed recovery, root boundary, three result wrappers, clear-on-refuse and project clear", flush=True)
finally:
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
    log.close()
    (ROOT / "transcript.json").write_text(json.dumps(TRANSCRIPT, indent=2))
    print("Evidence: " + str(ROOT), flush=True)
