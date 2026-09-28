#!/usr/bin/env python3
"""Evidence bundles — a driver that runs the whole demo against REAL analysers, and checks it (spec §4, §5, §7).

Capture is one operation on the running analyser, report {bundle}; there is no capture skill any more
(convergence, 2026-09-28). This drives it as an agent would, then opens the result as a recipient would
(--unpack, then the three opens), under two isolated homes on two paths, so "the recipient" really has none of the
sender's settings, roots or files:

  sender     /tmp/fluxtion-evidence-demo/sender     a DEMO project; opens the DEMO log and graph, saves a chart,
                                                    a chart with an external CSV (to be LEFT OUT), a report and
                                                    a three-step walk; then captures: the whole log with notes,
                                                    an excerpt that holds the breach, and one that misses it
  recipient  /tmp/fluxtion-evidence-demo/recipient  a cold home with its own project and no source roots; receives
                                                    only the two good .fexp files, opens each, plays the walk

The replay leg (M70.R5): the sender then opens a RECORDED run and captures it with its replay records, after two
refusals by name (replay records from another log; a window with them). The recipient verifies it, replays it with
tools/replay/ReplayBundle.java into two builds compiled here from the committed DEMO sources, and compares each with
--replay-compare: our build AGREES, a build with a different risk limit DIVERGES at record 6, and a build carrying
another processor's graph is refused before anything runs. Then it opens the bundle and plays its walk.

Both homes are isolated (rule 1): nothing on screen comes from this machine's own settings. Every check prints PASS
or FAIL and the run exits non-zero on any FAIL; timings for the recipient's steps are written to the output JSON.

Usage:
  python3 tools/evidence-bundle-demo.py                 # the whole demo; both analysers are stopped at the end
  python3 tools/evidence-bundle-demo.py --keep          # leave the recipient's analyser open, to look at
  python3 tools/evidence-bundle-demo.py --open X.fexp   # recipient only: open a whole-log bundle someone sent you
  python3 tools/evidence-bundle-demo.py --out results.json

Needs a built jar (`mvn package`) and a display. The analyser is run as `java -jar <jar>`; an installed one is
`analyser …` with the same flags.
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
FIXTURES = REPO / "src/test/resources/topology"
LOG = FIXTURES / "demo-quote-audit.yaml"
GRAPHML = FIXTURES / "demo-quote-processor.graphml"
# Neutral paths (rule 1): they are printed, shown in the title bar, and written into the results.
ROOT = pathlib.Path("/tmp/fluxtion-evidence-demo")
SENDER = ROOT / "sender"
RECIPIENT = ROOT / "recipient"

# The replay leg (M70.R5): a recorded run, its replay records, and two builds of the processor for the recipient.
REPLAY_DIR = REPO / "src/test/resources/replay"
RECORDED_LOG = REPLAY_DIR / "demo-quote-recorded-audit.yaml"
RECORDED_GRAPH = REPLAY_DIR / "demo-quote-recorded-processor.graphml"
RECORDED_REPLAY = REPLAY_DIR / "demo-quote-recorded.replay.yaml"
RECORDED_PROCESSOR = "com.acme.demo.generated.DemoQuoteRecordedProcessor"
DEMO_SRC = REPO / "examples/fixture-generator/src/main/java"
DEMO_RES = REPO / "examples/fixture-generator/src/main/resources"
RUNNER = REPO / "tools/replay/ReplayBundle.java"
M2 = pathlib.Path.home() / ".m2/repository"
REPLAY_WALK = "the-recorded-breach"
DIVERGENCE = ("replay: DIVERGES at record 6 (OrderUpdateEvent): eventLogRecord.nodeLogs.riskMonitor: the bundled log "
              "has '{ liveOrders: 2, limit: 2, redispatch: true}', and the replay has no such line")

WALK = "why-the-spread-moved"
CHART = "Spread before the breach"
EXTERNAL_CHART = "Venue feed latency (external CSV)"
REPORT = "breach-0900"

RESULTS = {"checks": [], "timings": {}}
# A person's window, not a fresh home's default: pinned so a run is reproducible. --default-window leaves it out,
# which is how the cold-recipient chart finding was made (spec r3 §12).
WINDOW = {"windowX": "60", "windowY": "60", "windowW": "1440", "windowH": "900"}


def check(ok, what, detail=""):
    RESULTS["checks"].append({"ok": bool(ok), "check": what, "detail": detail})
    print(("  PASS  " if ok else "  FAIL  ") + what + (f"  ({detail})" if detail and not ok else ""))
    return ok


# ---- the analyser, as a process and a socket --------------------------------------------------------------------

def jar():
    hits = sorted(p for p in (REPO / "target").glob("fluxtion-auditlog-analyser-*.jar") if not p.name.startswith("original-"))
    if not hits:
        sys.exit("no jar — run `mvn package` first")
    return hits[-1]


def cli(home, *args):
    """The analyser's headless flags. Exit code, stdout and stderr, exactly as a recipient sees them."""
    r = subprocess.run(["java", f"-Duser.home={home}", "-jar", str(jar()), *map(str, args)],
                       capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


class Analyser:
    def __init__(self, home, config):
        self.home = home
        cfg = home / ".fluxtion-analyser" / "config"
        cfg.parent.mkdir(parents=True, exist_ok=True)
        cfg.write_text("".join(f"{k}={v}\n" for k, v in config.items()))
        endpoint = home / ".fluxtion-analyser" / "rest-endpoint"
        endpoint.unlink(missing_ok=True)
        self.proc = subprocess.Popen(["java", f"-Duser.home={home}", "-jar", str(jar())],
                                     stdout=subprocess.DEVNULL, stderr=open(home / "analyser.err", "w"))
        for _ in range(60):
            time.sleep(0.5)
            if endpoint.exists():
                time.sleep(1.5)
                self.ep = json.loads(endpoint.read_text())
                return
        sys.exit(f"the analyser under {home} did not publish a REST endpoint")

    def act(self, verb, params=None):
        body = json.dumps({"v": 1, "action": verb, "params": params or {}}).encode()
        for attempt in range(8):
            req = urllib.request.Request(self.ep["url"] + "/action", data=body, method="POST",
                                         headers={"Content-Type": "application/json", "X-Analyser-Token": self.ep["token"]})
            try:
                with urllib.request.urlopen(req, timeout=30) as r:
                    return json.loads(r.read())
            except urllib.error.HTTPError as e:
                if e.code == 429:
                    time.sleep(0.4 * (attempt + 1))
                    continue
                try:
                    return json.loads(e.read())
                except Exception:
                    return {"ok": False, "error": f"HTTP {e.code}"}
        return {"ok": False, "error": "rate limited"}

    def must(self, verb, params=None):
        res = self.act(verb, params)
        if not res.get("ok"):
            sys.exit(f"{verb} {json.dumps(params)} failed: {res.get('error')}")
        return res

    def context(self):
        return self.must("context").get("context", {})

    def settle(self, what="the log", timeout=20):
        """Wait until nothing is in flight and a log is loaded — the open echo never carries the verdict."""
        for _ in range(int(timeout / 0.25)):
            c = self.context()
            if not c.get("inFlight") and (c.get("log") or {}).get("records"):
                return c
            time.sleep(0.25)
        sys.exit(f"{what} did not finish loading")

    def idle(self):
        """Until no project edit is waiting for its debounced write."""
        for _ in range(40):
            if not (self.context().get("project") or {}).get("unsavedEdits"):
                time.sleep(0.5)
                return
            time.sleep(0.25)

    def stop(self):
        self.proc.terminate()
        try:
            self.proc.wait(10)
        except subprocess.TimeoutExpired:
            self.proc.kill()


# ---- capture: one operation on the running analyser ----------------------------------------------------------------

def capture(an, name, notes=None, frm=None, to=None, replay=None):
    """The capture OPERATION on the running analyser: report {bundle}. The session decides every refusal and whether
    the result stands; this only asks and waits. Returns (ok, lines, path): lines are the refusal, or what the
    analyser said was left out, redacted or excerpted, then the identity."""
    bundle = {"path": name}
    if notes is not None:
        bundle["notes"] = notes
    if frm is not None:
        bundle["from"] = frm
    if to is not None:
        bundle["to"] = to
    if replay is not None:
        bundle["replay"] = str(replay)
    res = an.act("report", {"bundle": bundle})
    if not res.get("ok"):
        return False, [res.get("error", "")], None
    path = res["bundle"]["path"]
    for _ in range(80):
        c = an.context().get("capture") or {}
        if c.get("path") == path and c.get("phase") != "WRITING":
            break
        time.sleep(0.25)
    else:
        return False, ["the capture did not finish"], path
    if c.get("phase") != "WRITTEN":
        return False, [c.get("reason", "")], path
    return True, list(c.get("lines") or []) + ["identity: " + c["identity"]], path


# ---- opening a received bundle: --unpack, then the three opens ---------------------------------------------------------------------------

def open_bundle(an, bundle, work):
    """--unpack, then the three opens (spec §5). Returns the working copy, or exits on a refusal: nothing is opened
    from a bad bundle."""
    t0 = time.monotonic()
    code, so, se = cli(an.home, "--unpack", bundle, "--into", work)              # step 1: verify, extract
    RESULTS["timings"]["unpack_s"] = round(time.monotonic() - t0, 2)
    print("  " + "\n  ".join(so.splitlines()))
    if code != 0:
        sys.exit(f"the bundle was refused: {se.strip()}")
    copy = pathlib.Path(next(l for l in so.splitlines() if l.startswith("working copy: "))
                        .split("working copy: ", 1)[1].split("  (")[0])
    t0 = time.monotonic()
    an.must("open", {"project": str(copy / "profile" / "project.fluxtion-settings")})   # step 2: project ALONE
    log = next((copy / "log").iterdir())
    graphs = list((copy / "graph").iterdir()) if (copy / "graph").exists() else []
    params = {"log": str(log), "provenance": "evidence bundle " + bundle.name}
    if graphs:
        params["graphml"] = str(graphs[0])
    an.must("open", params)                                                      # step 3: log + graph together
    an.settle("the bundle's log")
    RESULTS["timings"]["open_s"] = round(time.monotonic() - t0, 2)
    return copy


def play(an, name, label="walk"):
    """Play the walk, one step at a time; each step's showing as the analyser states it."""
    shown = []
    t0 = time.monotonic()
    an.must("walk", {"name": name, "play": True, "step": 1})
    total = None
    step = 1
    while True:
        for _ in range(40):
            showing = ((an.context().get("walks") or {}).get("showing") or {})
            if showing.get("phase") == "SHOWN" and showing.get("step") == step:
                break
            time.sleep(0.25)
        shown.append(showing)
        if showing.get("phase") == "SHOWN":            # EP-A11 is by eye: the recipient's screen, painted by the app
            an.act("screenshot", {"path": f"{label}-step-{step}.png"})
        total = showing.get("of") or total
        if not total or step >= total:
            break
        step += 1
        an.must("walk", {"name": name, "play": True, "step": step})
    RESULTS["timings"]["walk_s"] = round(time.monotonic() - t0, 2)
    return shown


# ---- the demo ---------------------------------------------------------------------------------------------------

def sha(p):
    return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()


def props(path):
    """A properties file as key -> value (escapes left as written: only equality matters here)."""
    out = {}
    for line in pathlib.Path(path).read_text().splitlines() if pathlib.Path(path).exists() else []:
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v
    return out


def config_diff(before, after):
    """The keys a step added, removed or changed — machine-tier writes are LISTED, which is what EP-A7 asks."""
    return sorted(k for k in set(before) | set(after) if before.get(k) != after.get(k))


def sender_session():
    home = SENDER / "home"
    project = SENDER / "demo-quote-project"
    exchange = SENDER / "exchange"
    for d in (home, project / ".analyser", exchange):
        d.mkdir(parents=True, exist_ok=True)
    profile = project / ".analyser" / "project.fluxtion-settings"
    profile.write_text("share.version=1\n")
    (exchange / "venue-latency.csv").write_text("t,latencyMs\n1767258000100,3\n1767258000200,9\n1767258000300,4\n")
    an = Analyser(home, {"assistant.rest": "true", "activeProjectPath": profile,
                         "assistant.exports": "true", "assistant.exportDir": exchange})
    ok, lines, _ = capture(an, "never.fexp")     # EP-A1, live: a real session with no log refuses, and writes nothing
    check(not ok and lines[0].startswith("capture refused: no log is open") and not (exchange / "never.fexp").exists(),
          "EP-A1 a live capture with no log open refuses by name and writes nothing", "; ".join(lines))
    an.must("open", {"log": str(LOG), "graphml": str(GRAPHML), "provenance": "DEMO quote service"})
    an.settle()
    an.must("graph", {"name": CHART, "series": ["quotePublisher.spread"],
                      "rationale": "the spread moved before the breach"})
    an.must("graph", {"name": EXTERNAL_CHART, "newTab": True, "series": ["quotePublisher.spread"],
                      "external": [{"path": str(exchange / "venue-latency.csv"), "label": "venue latency",
                                    "time": "t", "timeFormat": "epochMillis", "value": "latencyMs"}]})
    an.must("report", {"name": REPORT, "title": "The 09:00 breach", "sections": [
        {"kind": "narrative", "text": "The spread widened two cycles before the risk limit was reached."},
        {"kind": "record", "recordIndex": 7},
        {"kind": "chart", "graph": CHART}]})
    an.must("walk", {"name": WALK, "title": "Why the spread moved", "steps": [
        {"caption": "where every price enters", "view": {"tab": "topology"},
         "targets": [{"target": "topology:node:priceListener", "caption": "every price arrives here"}]},
        {"caption": "the record where the limit is reached", "view": {"tab": "summary", "record": 7},
         "targets": [{"target": "records:row:7", "caption": "the breach record"}]},
        {"caption": "the spread before it", "view": {"tab": "graph", "graph": CHART},
         "targets": [{"target": f"graph:{CHART}:series:quotePublisher.spread", "caption": "it moved first"}]}]})
    return an


NOTES = "# The 09:00 breach (DEMO)\n\nThe spread widened two cycles before the risk limit was reached.\n"
# the excerpt window: records 4..8 of the DEMO log, which hold the breach record (7) and the cycles before it
EXCERPT = (1767258000140, 1767258000210)


def recipient(an, bundle, label, records, row, work):
    """Open one bundle on the cold recipient and play its walk; every check names the bundle it is about."""
    received = sha(bundle)
    code, so, se = cli(an.home, "--verify", bundle)
    check(code == 0, f"EP-A2/A4 [{label}] the received bundle verifies", se.strip())
    check("limit: unsigned" in so and "limit: no replay" in so, f"EP-A10 [{label}] verify states both limits")
    check("authenticated" not in (so + se).replace("does not authenticate", "").lower(),
          f"EP-A10 [{label}] nothing says the sender is authenticated")
    if records != 10:
        check(f"excerpt: the log is records {EXCERPT_RECORDS} of 10, not the whole log" in so,
              f"[{label}] verify says the log is an excerpt, and which", so)
    t_flow = time.monotonic()
    copy = open_bundle(an, bundle, work)
    ctx = an.context()
    check((ctx.get("project") or {}).get("settings", "").startswith(str(copy)),
          f"EP-A6 [{label}] the project is the bundle's working copy", str((ctx.get("project") or {}).get("settings")))
    check((ctx.get("log") or {}).get("records") == records, f"EP-A6 [{label}] the log loaded: {records} records",
          str((ctx.get("log") or {}).get("records")))
    check((ctx.get("graphPairing") or {}).get("applies") is True, f"EP-A6 [{label}] the graph loaded and applies")
    names = [w.get("name") for w in ((ctx.get("walks") or {}).get("saved") or [])]
    check(WALK in names, f"EP-A6 [{label}] the walk arrived", str(ctx.get("walks")))
    # review F4: assert the key exists and holds what it should, so a renamed key fails rather than passing for free
    saved = ctx.get("savedGraphs")
    check(isinstance(saved, list) and any(g.get("name") == CHART for g in saved)
          and not any(g.get("name") == EXTERNAL_CHART for g in saved),
          f"EP-A9 [{label}] the chart arrived and the external-series chart did not", json.dumps(saved)[:300])
    source = ctx.get("source")
    check(isinstance(source, dict) and source.get("roots") == [], f"EP-A12 [{label}] the recipient has no source roots",
          json.dumps(source)[:200])
    shown = play(an, WALK, label)
    RESULTS["timings"][f"{label}_received_to_walk_end_s"] = round(time.monotonic() - t_flow, 2)
    RESULTS["walks"][label] = shown
    check(len(shown) == 3 and all(s.get("phase") == "SHOWN" for s in shown), f"EP-A8 [{label}] all three steps shown",
          json.dumps(shown)[:400])
    caveats = [i for i, s in enumerate(shown, 1) if "not been re-checked" in (s.get("reason") or "")]
    check(len(caveats) == 1, f"M69.F3 [{label}] the not-re-checked caveat is stated once, stepping by the verb",
          f"stated on steps {caveats}")
    for i, s in enumerate(shown, 1):
        targets = s.get("targets") or []
        check(targets and all(t.get("state") == "CURRENT" and t.get("available") for t in targets),
              f"EP-A8 [{label}] step {i}: every target current and lit", json.dumps(targets)[:300])
    record_targets = [t.get("target") for t in (shown[1].get("targets") or [])] if len(shown) > 1 else []
    check(record_targets == [f"records:row:{row}"], f"[{label}] the breach record is row {row} here", str(record_targets))
    an.must("walk", {"end": True})
    check(sha(bundle) == received, f"EP-A5 [{label}] the received bundle is byte-identical after unpack, open and the walk")
    code2, _, _ = cli(an.home, "--unpack", bundle, "--into", work)
    check(code2 == 0 and sha(bundle) == received, f"EP-A5 [{label}] a second unpack: a fresh copy, the bundle unchanged")
    return copy


EXCERPT_RECORDS = "4..8"


# ---- the replay leg (M70.R5): a recorded run, sent with its replay records, checked against two builds -------------

def replay_sender(an, inbox):
    """With the DEMO log open, then the recorded run: the analyser refuses replay records that are not this log's, and
    a window with them; it writes the whole recorded run with them. Returns the bundle in the recipient's inbox."""
    exchange = SENDER / "exchange"
    # A genuinely different run: the longer series log. NOT the short DEMO log, whose first seven inputs are the recorded
    # run's, at the same instants (the same input script on the same clock), so by content they ARE its inputs and pair.
    an.must("open", {"log": str(FIXTURES / "demo-quote-series.yaml"), "provenance": "DEMO quote service"})
    an.settle("the series log")
    ok, lines, _ = capture(an, "wrong-log.fexp", replay=RECORDED_REPLAY)
    check(not ok and "the replay does not belong to this log" in lines[0] and not (exchange / "wrong-log.fexp").exists(),
          "R2 replay records from another run are refused by name, and nothing is written", "; ".join(lines))
    an.must("open", {"log": str(RECORDED_LOG), "graphml": str(RECORDED_GRAPH), "provenance": "DEMO quote service"})
    an.settle("the recorded run")
    an.must("walk", {"name": REPLAY_WALK, "title": "The breach, in the recorded run", "steps": [
        {"caption": "the graph raises the breach itself", "view": {"tab": "summary", "record": 7},
         "targets": [{"target": "records:row:7", "caption": "raised by riskMonitor, not sent in"}]}]})
    ok, lines, _ = capture(an, "recorded-window.fexp", frm=1767258000100, to=1767258000180, replay=RECORDED_REPLAY)
    check(not ok and "a replay needs the whole run" in lines[0], "R2 a window with replay records is refused by name",
          "; ".join(lines))
    ok, lines, path = capture(an, "recorded-run.fexp", notes="# Recorded run (DEMO)\n", replay=RECORDED_REPLAY)
    print("  recorded run, with its replay records:\n    " + "\n    ".join(lines))
    check(ok and any("the run's 7 recorded inputs, paired with the log in order" in l for l in lines),
          "R2 the whole recorded run is written with its 7 replay records, paired in order", "; ".join(lines))
    with zipfile.ZipFile(path) as z:
        check("replay/demo-quote-recorded.replay.yaml" in z.namelist(), "R2 the replay records travel as the replay/ member")
        check(json.loads(z.read("manifest.json")).get("format") == 2, "R2 a bundle with replay records is format 2")
    shutil.copyfile(path, inbox / "recorded-run.fexp")
    return inbox / "recorded-run.fexp"


def build(work, name, risk_limit=None, graphml=None):
    """A recipient's build of the processor from the committed DEMO sources (not the builder, which needs the
    compiler), with the generator's GraphML beside the class. `risk_limit` changes the generated processor's limit;
    `graphml` puts another processor's graph beside it, making a build that is not the bundle's processor."""
    src, classes = work / f"{name}-src", work / name
    files = []
    for p in DEMO_SRC.rglob("*.java"):
        rel = p.relative_to(DEMO_SRC).as_posix()
        if "/builder/" in rel or rel.endswith("GenerateFixtures.java"):
            continue
        text = p.read_text()
        if risk_limit and rel.endswith("DemoQuoteRecordedProcessor.java"):
            was = "new com.acme.demo.node.Nodes.RiskMonitor(orderTracker, 2)"
            if was not in text:
                sys.exit("the changed build could not find the generated risk limit")
            text = text.replace(was, f"new com.acme.demo.node.Nodes.RiskMonitor(orderTracker, {risk_limit})")
        (src / rel).parent.mkdir(parents=True, exist_ok=True)
        (src / rel).write_text(text)
        files.append(str(src / rel))
    classes.mkdir(parents=True)
    subprocess.run(["javac", "-proc:none", "-nowarn", "-d", str(classes), "-cp", runtime_cp(), *files], check=True)
    graph = classes / "com/acme/demo/generated/DemoQuoteRecordedProcessor.graphml"
    graph.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(graphml or DEMO_RES / "com/acme/demo/generated/DemoQuoteRecordedProcessor.graphml", graph)
    return classes


def runtime_cp():
    """The Fluxtion runtime and its one dependency, from the local repository `mvn package` filled."""
    runtime = M2 / "com/telamin/fluxtion/fluxtion-runtime/1.0.16/fluxtion-runtime-1.0.16.jar"
    agrona = next((p for p in (M2 / "org/agrona/agrona").rglob("agrona-*.jar") if "sources" not in p.name), None)
    if not runtime.exists() or agrona is None:
        sys.exit("the Fluxtion runtime is not in ~/.m2 — run `mvn package` first")
    return f"{runtime}{':'}{agrona}"


def runner(bundle, build_dir, out):
    """The recipient's runner, run as `java` runs a single source file: no JBang and no network needed here."""
    r = subprocess.run(["java", "-cp", runtime_cp(), str(RUNNER), "--bundle", str(bundle), "--processor",
                        RECORDED_PROCESSOR, "--cp", str(build_dir), "--out", str(out)], capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def replay_recipient(an, bundle, work):
    """Verify, replay into our build (AGREES), into a build that behaves differently (DIVERGES, named), refuse a build
    that is not the bundle's processor; then open the bundle and play its walk."""
    label = "replay"
    received = sha(bundle)
    code, so, se = cli(an.home, "--verify", bundle)
    check(code == 0 and "replay: replay/demo-quote-recorded.replay.yaml, the run's 7 recorded inputs" in so,
          f"R2 [{label}] verify says the bundle carries the run's 7 recorded inputs", so + se)
    check("limit: replay: the recorded inputs reproduce this log only on a build whose graph matches" in so
          and "limit: no replay" not in so, f"R2 [{label}] verify states the replay limit, never 'no replay'", so)
    builds = work / "builds"
    builds.mkdir(parents=True)
    ours = build(builds, "our-build")
    changed = build(builds, "risk-change", risk_limit=3)
    foreign = build(builds, "foreign", graphml=DEMO_RES / "com/acme/demo/generated/DemoQuoteProcessor.graphml")

    t0 = time.monotonic()
    code, so, se = runner(bundle, ours, work / "replayed.yaml")
    RESULTS["timings"]["replay_runner_s"] = round(time.monotonic() - t0, 2)
    check(code == 0 and "graph: your build's nodes and edges are the bundle's" in so and "(8 audit records)" in so,
          f"R4 [{label}] the runner replays 7 inputs into our build, which raises the breach again: 8 records", so + se)
    t0 = time.monotonic()
    code, so, se = cli(an.home, "--replay-compare", bundle, work / "replayed.yaml")
    RESULTS["timings"]["replay_compare_s"] = round(time.monotonic() - t0, 2)
    check(code == 0 and "replay: AGREES, 8 of 8 records" in so, f"R3 [{label}] our build gives the same audit log: AGREES",
          so + se)

    code, so, se = runner(bundle, changed, work / "replayed-risk-change.yaml")
    check(code == 0, f"R4 [{label}] a build with the same graph and a different risk limit replays", se)
    code, so, se = cli(an.home, "--replay-compare", bundle, work / "replayed-risk-change.yaml")
    check(code == 1 and DIVERGENCE in so,
          f"R3 [{label}] it DIVERGES at record 6, naming the risk monitor entry it never writes", so + se)

    code, so, se = runner(bundle, foreign, work / "replayed-foreign.yaml")
    check(code == 1 and "your build's graph is not the bundle's: node(s) [replayCapture] missing" in se
          and not (work / "replayed-foreign.yaml").exists(),
          f"R4 [{label}] a build that is not the bundle's processor is refused by name, nothing written", so + se)

    open_bundle(an, bundle, work / "copies")
    shown = play(an, REPLAY_WALK, label)
    check(len(shown) == 1 and shown[0].get("phase") == "SHOWN"
          and all(t.get("state") == "CURRENT" for t in shown[0].get("targets") or []),
          f"EP-A8 [{label}] the recorded run's walk plays, its target current", json.dumps(shown)[:300])
    an.must("walk", {"end": True})
    check(sha(bundle) == received, f"EP-A5 [{label}] the received bundle is byte-identical after replay, compare and the walk")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--keep", action="store_true", help="leave the recipient's analyser open on the excerpt's walk")
    ap.add_argument("--open", type=pathlib.Path, help="recipient only: open this bundle (a whole-log bundle)")
    ap.add_argument("--out", type=pathlib.Path, default=ROOT / "results.json")
    ap.add_argument("--default-window", action="store_true", help="do not pin the window size (a fresh home's default)")
    a = ap.parse_args()
    if a.default_window:
        WINDOW.clear()
    if a.open:
        a.open = a.open.resolve()
    shutil.rmtree(ROOT, ignore_errors=True)
    ROOT.mkdir(parents=True)
    RESULTS["walks"] = {}
    inbox = RECIPIENT / "inbox"
    inbox.mkdir(parents=True)
    bundles = []
    replay_bundle = None
    if a.open:
        bundles.append((a.open, "received", 10, 7))
    else:
        print("sender: a DEMO investigation, then three captures through report {bundle}")
        an = sender_session()
        exchange = SENDER / "exchange"
        try:
            ok, lines, path = capture(an, "breach-0900.fexp", notes=NOTES)
            print("  whole log:\n    " + "\n    ".join(lines))
            check(ok and path and pathlib.Path(path).exists(), "capture wrote a whole-log bundle", "; ".join(lines))
            check(any(l.startswith("left out: chart '" + EXTERNAL_CHART) for l in lines),
                  "EP-A9 the chart with an external series is left out and named")
            with zipfile.ZipFile(path) as z:
                check(z.read("notes/NOTES.md").decode() == NOTES, "the author's notes travel as notes/NOTES.md")
            RESULTS["identity"] = next((l.split(": ", 1)[1] for l in lines if l.startswith("identity: ")), None)
            shutil.copyfile(path, inbox / "breach-0900.fexp")
            bundles.append((inbox / "breach-0900.fexp", "whole", 10, 7))

            ok, lines, path = capture(an, "breach-0900-excerpt.fexp", frm=EXCERPT[0], to=EXCERPT[1])
            print("  excerpt:\n    " + "\n    ".join(lines))
            check(ok and any(l.startswith(f"excerpt: records {EXCERPT_RECORDS} of 10") for l in lines),
                  "capture wrote an excerpt of records 4..8, and says so", "; ".join(lines))
            check(not any(l.startswith("left out: walk") or l.startswith("left out: report") for l in lines),
                  "the excerpt holds the breach, so the walk and the report are re-based, not left out", "; ".join(lines))
            shutil.copyfile(path, inbox / "breach-0900-excerpt.fexp")
            bundles.append((inbox / "breach-0900-excerpt.fexp", "excerpt", 5, 3))

            ok, lines, path = capture(an, "before-the-breach.fexp", to=1767258000170)
            check(ok and any(l.startswith(f"left out: walk '{WALK}'") for l in lines)
                  and any(l.startswith(f"left out: report '{REPORT}'") for l in lines),
                  "an excerpt that misses the breach leaves the walk and the report out, and names them", "; ".join(lines))

            print("sender: the recorded run, with its replay records")
            replay_bundle = replay_sender(an, inbox)
        finally:
            an.stop()

    print("recipient: a cold home on another path — --unpack, then open")
    home = RECIPIENT / "home"
    home.mkdir(parents=True, exist_ok=True)
    own_profile = RECIPIENT / "own-project" / ".analyser" / "project.fluxtion-settings"   # the recipient's OWN work
    own_profile.parent.mkdir(parents=True)
    own_profile.write_text("share.version=1\nreport.count=0\n")
    shots = RECIPIENT / "shots"
    shots.mkdir(parents=True, exist_ok=True)
    t_start = time.monotonic()
    an = Analyser(home, {"assistant.rest": "true", "activeProjectPath": own_profile, **WINDOW,
                         "assistant.exports": "true", "assistant.exportDir": shots})
    RESULTS["timings"]["recipient_start_s"] = round(time.monotonic() - t_start, 2)
    try:
        # The baseline is the recipient's own work AS THE ANALYSER WRITES IT. A hand-written profile is normalised
        # (and given a nonce) on the analyser's first write of it, and the machine config is written lazily, so a
        # baseline taken before either would blame the bundle for the analyser's own first save.
        scratch_root = own_profile.parent.parent / "src"
        scratch_root.mkdir(exist_ok=True)
        an.must("source_root", {"add": [str(scratch_root)]})
        an.idle()
        an.must("source_root", {"remove": [str(scratch_root)]})
        an.idle()
        own_profile_before = sha(own_profile)
        config_before = props(home / ".fluxtion-analyser" / "config")
        for bundle, label, records, row in bundles:
            recipient(an, bundle, label, records, row, RECIPIENT / "work")
        if not a.open:
            print("recipient: the recorded run — verify, replay into two builds, compare, open")
            replay_recipient(an, replay_bundle, RECIPIENT / "replay")
        RESULTS["screenshots"] = sorted(str(p) for p in shots.glob("*.png"))
        if not a.keep:
            an.must("open", {"project": str(own_profile)})                     # back to the recipient's own work
            an.must("open", {"close": "project"})
            check(sha(own_profile) == own_profile_before, "EP-A7 the recipient's own project profile is byte-identical")
            keys = config_diff(config_before, props(home / ".fluxtion-analyser" / "config"))
            RESULTS["recipientConfigKeysChanged"] = keys
            print("  recipient machine-tier settings changed (EP-A7, listed, not judged): " + (", ".join(keys) or "none"))
    finally:
        if a.keep:
            print(f"\nthe recipient's analyser is still open (pid {an.proc.pid})")
        else:
            an.stop()
    return finish(a)


def finish(a):
    a.out.parent.mkdir(parents=True, exist_ok=True)
    a.out.write_text(json.dumps(RESULTS, indent=2) + "\n")
    failed = [c for c in RESULTS["checks"] if not c["ok"]]
    print(f"\n{len(RESULTS['checks']) - len(failed)} passed, {len(failed)} failed · timings {RESULTS['timings']} · {a.out}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
