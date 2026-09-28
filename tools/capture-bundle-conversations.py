#!/usr/bin/env python3
"""Generate docs/site/evidence-bundles/with-an-assistant.md from REAL runs: evidence bundles and replay, as a person
does them, by asking an AI assistant that drives the analyser.

The same discipline as capture-conversations.py, whose recorder this reuses (rule 1): the asks and the agent's answers
are authored; every tool call, every echo and every COMMAND's output between them is recorded from a live run on the
DEMO set under the isolated home the screenshots come from, and an answer that cites a figure fails the run if the
recording no longer contains it. The recipient's half runs commands, not verbs (--verify, the replay runner,
--replay-compare), so this adds a shell step that runs each one for real and records what it printed.

Two analysers, one after the other, each under a fresh isolated home: the SENDER's, which captures, and the
RECIPIENT's, which receives only the .fexp files. The recipient's "build" is the committed DEMO sources, compiled here
twice: as they are, and with the risk limit changed, which is the build that should diverge.

Usage:  mvn package && python3 tools/capture-bundle-conversations.py   (macOS screencapture for the shots; jbang)
"""
import importlib.util
import pathlib
import re
import shutil
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent


def _load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


cd = _load("capture_docs", "capture-docs.py")
cc = _load("capture_conversations", "capture-conversations.py")

REPO = cd.REPO
PAGE = REPO / "docs/site/evidence-bundles/with-an-assistant.md"
WORK = cd.CAPTURE_ROOT / "bundle-conversations"
SENT = WORK / "sent"                       # what the sender's analyser wrote, copied out before the next launch
BUILDS = WORK / "builds"
REPLAY_DIR = REPO / "src/test/resources/replay"
RECORDED_LOG = REPLAY_DIR / "demo-quote-recorded-audit.yaml"
RECORDED_GRAPH = REPLAY_DIR / "demo-quote-recorded-processor.graphml"
RECORDED_REPLAY = REPLAY_DIR / "demo-quote-recorded.replay.yaml"
PROCESSOR = "com.acme.demo.generated.DemoQuoteRecordedProcessor"
DEMO_SRC = REPO / "examples/fixture-generator/src/main/java"
DEMO_GRAPHML = REPO / "examples/fixture-generator/src/main/resources/com/acme/demo/generated/DemoQuoteRecordedProcessor.graphml"
RUNTIME = pathlib.Path.home() / ".m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.16/fluxtion-runtime-1.0.16.jar"


# ---- the recorder, plus the recipient's commands ---------------------------------------------------------------------

def neutral(text):
    """Paths and identities made neutral for a public page, as shorten() does for echoes."""
    for prefix, label in ((str(SENT), "~/Downloads"), (str(WORK), "~/work"), (str(cd.EXPORT_DIR), "<exchange-dir>"),
                          (str(REPO), "…/analyser")):
        text = text.replace(prefix, label)
    return re.sub(r"sha256:([0-9a-f]{12})[0-9a-f]{52}", r"sha256:\1…", text)


class Transcript(cc.Transcript):

    def shot(self, name, caption):
        """As the recorder's, with the image path one level up: this page lives in evidence-bundles/."""
        before = len(self.lines)
        super().shot(name, caption)
        self.lines[before:] = [l.replace("](assets/", "](../assets/") for l in self.lines[before:]]

    def block(self, tool, request, echo):
        """Every recorded call and echo made neutral, as a command's output is: no working path reaches the page."""
        super().block(tool, neutral(request), neutral(echo))

    def attempt(self, verb, params, expect):
        """A call that is EXPECTED to be refused: recorded as the refusal it is, and the run fails if it is not one."""
        res = cd.act(self.ep, verb, params)
        if res.get("ok") or expect not in str(res.get("error")):
            sys.exit(f"{verb} was expected to be refused ({expect!r}), and answered {res}")
        self.raw.append(str(res.get("error")))
        self.block(f"analyser_{verb}", cc.json.dumps(cc.shorten(params), ensure_ascii=False),
                   cc.dump({"ok": False, "error": res.get("error")}))
        return res

    def shell(self, shown, argv, expect_code=0):
        """Run one command for real and record what it printed. `shown` is the command as a person types it."""
        r = subprocess.run([str(a) for a in argv], capture_output=True, text=True, cwd=REPO)
        printed = "\n".join(l for l in (r.stdout + r.stderr).splitlines()
                            if not l.startswith("[jbang]") and not l.startswith("updating event log config"))
        if r.returncode != expect_code:
            sys.exit(f"`{shown}` exited {r.returncode}, not {expect_code}:\n{printed}")
        self.raw.append(printed)
        self.lines.append("")
        self.lines.append(f'??? example "→ shell: `{shown.split(" ")[0]} {shown.split(" ")[1]}`"')
        self.lines.append("    ```console")
        self.lines.append("    $ " + neutral(shown))
        for ln in neutral(printed).splitlines():
            self.lines.append("    " + ln)
        self.lines.append(f"    (exit {r.returncode})")
        self.lines.append("    ```")
        self.lines.append("")
        return printed


# ---- set-up ----------------------------------------------------------------------------------------------------------

def await_capture(ep, path_suffix):
    for _ in range(80):
        c = (cd.act(ep, "context").get("context") or {}).get("capture") or {}
        if str(c.get("path", "")).endswith(path_suffix) and c.get("phase") != "WRITING":
            return c
        time.sleep(0.25)
    sys.exit(f"the capture of {path_suffix} did not finish")


def await_log(ep, records):
    for _ in range(40):
        log = (cd.act(ep, "context").get("context") or {}).get("log") or {}
        if log.get("records") == records:
            return
        time.sleep(0.25)
    sys.exit(f"the log did not load with {records} records")


def await_step(ep, step):
    for _ in range(40):
        showing = ((cd.act(ep, "context").get("context") or {}).get("walks") or {}).get("showing") or {}
        if showing.get("phase") == "SHOWN" and showing.get("step") == step:
            return showing
        time.sleep(0.25)
    sys.exit(f"walk step {step} was not shown")


def build(name, risk_limit=None):
    """A recipient's build of the processor: the committed DEMO sources (not the builder: it needs the compiler), with
    the generator's GraphML beside the class. With `risk_limit`, the generated processor's limit is changed."""
    src, classes = BUILDS / f"{name}-src", BUILDS / name
    shutil.rmtree(src, ignore_errors=True)
    shutil.rmtree(classes, ignore_errors=True)
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
        to = src / rel
        to.parent.mkdir(parents=True, exist_ok=True)
        to.write_text(text)
        files.append(str(to))
    classes.mkdir(parents=True)
    agrona = next(p for p in (pathlib.Path.home() / ".m2/repository/org/agrona/agrona").rglob("agrona-*.jar")
                  if "sources" not in p.name)
    subprocess.run(["javac", "-proc:none", "-nowarn", "-d", str(classes), "-cp", f"{RUNTIME}:{agrona}", *files],
                   check=True)
    graph = classes / "com/acme/demo/generated/DemoQuoteRecordedProcessor.graphml"
    graph.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(DEMO_GRAPHML, graph)
    return classes


# ---- the conversations -----------------------------------------------------------------------------------------------

NOTES = "# The 09:00 breach (DEMO)\n\nThe spread narrowed three cycles before the risk limit was reached.\n"


def sender(t):
    ep = t.ep
    t.heading("1 · \"Package this for the dev team\": an investigation as one file")
    t.you("I've worked out why the quote service misbehaved at 09:00. Package the investigation for the dev team, "
          "with a short walk through what I found.")
    t.context(["log", "capture"])
    t.prose("The agent saves the finding as a **walk** first, because flags do not travel and a walk does. Each "
            "step says where to look and why.")
    t.call("walk", {"name": "the-0900-breach", "title": "The 09:00 breach", "steps": [
        {"caption": "every price enters here", "view": {"tab": "topology"},
         "targets": [{"target": "topology:node:priceListener", "caption": "every price arrives here"}]},
        {"caption": "the record where the risk limit is reached", "view": {"tab": "summary", "record": 7},
         "targets": [{"target": "records:row:7", "caption": "the breach: two live orders"}]}]},
           show=["walk", "name", "steps", "saved"])
    t.call("report", {"bundle": {"path": "breach-0900.fexp", "notes": NOTES}}, show=["phase", "path", "note"])
    await_capture(ep, "breach-0900.fexp")
    ctx = t.context(["capture"])
    identity = ctx["capture"]["identity"]
    t.agent("Done: `breach-0900.fexp` is in your exchange directory, with the log, its graph, the walk *The 09:00 "
            "breach* and your notes. It also lists what it **left out**, because it describes your machine, not the "
            "investigation. Send the file however you like, and send its identity line (`" + neutral(identity) +
            "`) by another route, such as a chat message: the bundle is unsigned, and matching that line is how "
            "the recipient knows the file is the one you packed.",
            cites=['"phase": "WRITTEN"', identity])
    cd.act(ep, "walk", {"name": "the-0900-breach", "play": True, "step": 2})
    await_step(ep, 2)
    time.sleep(0.6)
    t.shot("bundle-conv-walk.png", "The walk the bundle carries, on step 2: the breach record lit with its caption. "
           "The recipient sees exactly this")
    cd.act(ep, "walk", {"end": True})

    t.heading("2 · \"Include the replay records so they can check it\"")
    cd.act(ep, "open", {"log": str(RECORDED_LOG), "graphml": str(RECORDED_GRAPH), "provenance": "DEMO quote service"})
    await_log(ep, 8)
    t.you("This run was recorded with a replay writer. Send the dev team the minute around the breach, with its replay "
          "records, so they can check it on their own build.")
    shutil.copy(RECORDED_REPLAY, cd.EXPORT_DIR / RECORDED_REPLAY.name)   # a read the analyser confines to it
    t.prose("The processor that wrote this log had a replay writer compiled in: it recorded each input the service "
            "received, at the instant its cycle ran, next to the audit log. That file is in the exchange directory, "
            "the one place the assistant may point the analyser at a file, and the agent names it.")
    t.attempt("report", {"bundle": {"path": "recorded-run.fexp", "replay": RECORDED_REPLAY.name,
                                    "from": 1767258000100, "to": 1767258000180}},
              expect="a replay needs the whole run")
    t.agent("The analyser refused a time window with the replay records, and it is right to: the processor's state "
            "at the start of a window depends on every input before it, so a replay has to start at the beginning. "
            "I'll send the whole run. It is eight records, so the size is no concern.",
            cites=["a replay needs the whole run"])
    t.call("walk", {"name": "the-breach", "title": "The breach, in the recorded run", "steps": [
        {"caption": "the graph raises the breach itself", "view": {"tab": "summary", "record": 7},
         "targets": [{"target": "records:row:7", "caption": "raised by riskMonitor, not sent in"}]}]},
           show=["walk", "name", "saved"])
    t.call("report", {"bundle": {"path": "recorded-run.fexp", "replay": RECORDED_REPLAY.name,
                                 "notes": "# Recorded run (DEMO)\n\nReplay it into your build and compare.\n"}},
           show=["phase", "path"])
    await_capture(ep, "recorded-run.fexp")
    t.context(["capture"])
    t.agent("Written as `recorded-run.fexp`, and it carries **the run's 7 recorded inputs, paired with the log in "
            "order**: the analyser checked that each one is one of the log's records at the instant its cycle ran, "
            "so they cannot be from another run. The eighth record, the breach, is not among them because the graph "
            "raised it itself; a replay raises it again. The log holds no exported-service calls, so nothing the "
            "replay records cannot carry.",
            cites=["the run's 7 recorded inputs, paired with the log in order"])
    t.shot("bundle-conv-recorded-run.png", "The recorded run the second bundle carries: eight records, the last "
           "the breach the graph raised itself")

    SENT.mkdir(parents=True, exist_ok=True)
    for name in ("breach-0900.fexp", "recorded-run.fexp"):
        shutil.copy(cd.EXPORT_DIR / name, SENT / name)


def recipient(t):
    ep = t.ep
    jar = cd.jar()
    bundle = SENT / "recorded-run.fexp"
    ours, changed = build("our-build"), build("risk-change", risk_limit=3)

    t.heading("3 · \"We've been sent this: does our build give the same run?\"")
    t.you("The dev team sent `recorded-run.fexp`. Check it's intact, then check our build of the quote processor "
          "gives the same audit log on that run.")
    t.prose("The recipient's agent works with **commands**, not the running analyser: checking a file and replaying "
            "it into a build need nothing open. Each command below ran for real; `analyser` is `java -jar "
            "fluxtion-auditlog-analyser-<version>.jar`, and the runner is `tools/replay/ReplayBundle.java` in the "
            "analyser's repository, run with JBang.")
    t.shell("analyser --verify ~/Downloads/recorded-run.fexp", ["java", "-jar", jar, "--verify", bundle])
    t.agent("Intact: every member matches the manifest. Its identity line is the one to compare with what the sender "
            "sent you separately. It carries the run's 7 recorded inputs.",
            cites=["verified: 5 members", "the run's 7 recorded inputs"])
    out = WORK / "replayed.yaml"
    out.unlink(missing_ok=True)
    t.shell(f"jbang tools/replay/ReplayBundle.java --bundle ~/Downloads/recorded-run.fexp --processor {PROCESSOR} "
            f"--cp ~/work/builds/our-build --out ~/work/replayed.yaml",
            ["jbang", REPO / "tools/replay/ReplayBundle.java", "--bundle", bundle, "--processor", PROCESSOR,
             "--cp", ours, "--out", out])
    t.shell("analyser --replay-compare ~/Downloads/recorded-run.fexp ~/work/replayed.yaml",
            ["java", "-jar", jar, "--replay-compare", bundle, out])
    t.agent("Our build gives the same audit log. The runner first checked that our build **is** the bundle's processor: the "
            "same nodes and edges as the graph the bundle carries. It then replayed the 7 inputs at their recorded "
            "instants, and the graph raised the breach again by itself: 8 audit records. The analyser compared "
            "them with the bundled log and they agree, 8 of 8. The only lines allowed to differ are when and where "
            "each cycle ran (`endTime`, `thread`), which a replay cannot know.",
            cites=["graph: your build's nodes and edges are the bundle's", "replay: AGREES, 8 of 8 records"])

    printed = t.shell("analyser --unpack ~/Downloads/recorded-run.fexp",
                      ["java", "-jar", jar, "--unpack", bundle, "--into", WORK / "copies"])
    copy = pathlib.Path(next(l for l in printed.splitlines() if l.startswith("working copy: "))
                        .split("working copy: ", 1)[1].split("  (")[0])
    t.call("open", {"project": str(copy / "profile" / "project.fluxtion-settings")}, show=["project", "opened"])
    t.call("open", {"log": str(next((copy / "log").iterdir())), "graphml": str(next((copy / "graph").iterdir())),
                    "provenance": "evidence bundle recorded-run.fexp"}, show=["opened", "log"], wait=1.5)
    await_log(ep, 8)
    t.call("walk", {"name": "the-breach", "play": True, "step": 1}, show=["walk", "showing"])
    await_step(ep, 1)
    time.sleep(0.6)
    t.agent("And the sender's walk plays here from a fresh working copy, without touching our project or the file we "
            "were sent: the breach record lit, captioned *raised by riskMonitor, not sent in*.")
    t.shot("bundle-conv-received.png", "On the recipient's machine: the bundle opened from its working copy, the "
           "sender's walk playing on the breach record")
    cd.act(ep, "walk", {"end": True})

    t.heading("4 · \"Does the branch with the risk change still behave the same?\"")
    t.you("And our branch where the risk limit is 3: does it still behave the same on this run?")
    out2 = WORK / "replayed-risk-change.yaml"
    out2.unlink(missing_ok=True)
    t.shell(f"jbang tools/replay/ReplayBundle.java --bundle ~/Downloads/recorded-run.fexp --processor {PROCESSOR} "
            f"--cp ~/work/builds/risk-change --out ~/work/replayed-risk-change.yaml",
            ["jbang", REPO / "tools/replay/ReplayBundle.java", "--bundle", bundle, "--processor", PROCESSOR,
             "--cp", changed, "--out", out2])
    printed = t.shell("analyser --replay-compare ~/Downloads/recorded-run.fexp ~/work/replayed-risk-change.yaml",
                      ["java", "-jar", jar, "--replay-compare", bundle, out2], expect_code=1)
    m = re.search(r"eventLogRecord\.nodeLogs\.riskMonitor: the bundled log has '([^']*)', and the replay has no such line",
                  printed)
    if not m:
        sys.exit("the changed build did not diverge at the risk monitor's entry: " + printed)
    t.agent("No. The graph is the same, so it replays, but it **diverges at record 6**, the last order update. In the "
            "recorded run the risk monitor logged `" + m.group(1) + "` on that cycle; on our branch it logs nothing "
            "there at all. With the limit at 3, two live orders are no longer a breach, so the monitor stays quiet "
            "and the breach the recorded run ends with never comes: the replay has 7 records, not 8. That is the "
            "change behaving as intended, and it is still a behaviour change on this run. I've lit the record in "
            "the analyser.",
            cites=["replay: DIVERGES at record 6 (OrderUpdateEvent)", m.group(1), "and the replay has no such line"])
    t.call("goto", {"recordIndex": 6, "reveal": True}, show=["recordIndex", "event"])
    t.call("spotlight", {"target": "records:row:6",
                         "caption": "diverges here: on the branch the risk monitor logs nothing, and no breach follows"},
           show=["lit"])
    time.sleep(0.6)
    t.shot("bundle-conv-divergence.png", "The bundled log with the divergence lit: record 6, whose risk monitor entry "
           "(in the detail pane) the branch never writes")
    cd.act(ep, "spotlight", {"clear": True})


def main():
    cd.ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.rmtree(WORK, ignore_errors=True)
    WORK.mkdir(parents=True)
    ep = cd.launch("Light", project=cd.make_demo_project())
    cd.seed(ep)
    t = Transcript(ep)
    t.lines.append("<!-- GENERATED by tools/capture-bundle-conversations.py — edit the script, not this file. -->")
    t.lines.append("# Evidence bundles with your assistant")
    t.prose("You rarely type a bundle operation yourself. You ask the AI assistant connected to the analyser "
            "([Connecting an LLM](../connect-an-llm.md)), and it drives the analyser for you. Here are four short "
            "conversations on the DEMO set: two by the person **sending** an investigation, two by the person who "
            "**receives** it.")
    t.prose("The **asks** and the **agent's answers** are written by hand. **Every tool call, every echo and every "
            "command's output was recorded from a real run** by `tools/capture-bundle-conversations.py`, under an "
            "isolated home with only the DEMO data. Open a collapsed block to see what the agent actually sent and "
            "got back. The exact operations are in [Commands and file format](reference.md).")
    sender(t)

    # the recipient: a fresh analyser, a fresh home, and only the files that were sent
    t.ep = cd.launch("Light")
    recipient(t)

    t.prose("---")
    t.prose("*Regenerate with `mvn package && python3 tools/capture-bundle-conversations.py`. Every call and command "
            "above is the run's own; if one fails, or an answer cites something the run no longer shows, the script "
            "stops rather than write a page that pretends it worked.*")
    PAGE.write_text("\n".join(t.lines).rstrip() + "\n")
    print(f"wrote {PAGE.relative_to(REPO)} ({len(t.lines)} lines); shots: {len(cd._captured)}")
    if cd._failed_actions:
        sys.exit(f"screenshot verb failed for {cd._failed_actions}")
    if cd._failed:
        print(f"WARNING: {len(cd._failed)} image(s) NOT regenerated (no native capture): {cd._failed}", file=sys.stderr)
    if "--keep" not in sys.argv:
        cd.stop_capture_app()


if __name__ == "__main__":
    main()
