#!/usr/bin/env python3
"""OA-5 — the DEMO conversation journey "Find the first recorded breach", built and captured from a real run.

    mvn -q package -DskipTests && python3 tools/capture-journey.py [--bundle] [--images] [--page]

(no flag runs all three; --bundle needs no screen, --images needs Screen Recording and an awake display)

What it does, all against the REAL analyser jar under an isolated home holding only the DEMO fixtures:

  1. opens the DEMO topology and the DEMO quote-series log;
  2. ASKS the analyser for every fact the dialogue states — the record counts, the first record where the application
     logged a RiskBreachEvent, the record that preceded it, the pairing verdict — and writes the scripted dialogue from
     those answers, so no number in it is typed by hand;
  3. draws and saves the chart the journey selects, saves the walk with its scripted dialogue, and PLAYS it, stepping
     through every stop and recording what the analyser actually showed (each target's state);
  4. captures the journey as an evidence bundle (.fexp) — an ordinary one, with no replay records;
  5. photographs the analyser and the popped-out assistant natively (window capture, never a screen region), and the
     docked assistant with no provider configured;
  6. writes docs/site/journeys/find-the-first-recorded-breach.md from the run: the catalogue card, the measured
     facts, the bundle's size and sha256 recomputed from its bytes, and the transcript of what played.

The dialogue is SCRIPTED and labelled so everywhere. Nothing here calls a model: the journey needs no provider.
Every fact is re-checked against the bundled log by JourneyCatalogueTest, headless, on every build.
"""
import hashlib
import importlib.util
import json
import pathlib
import shutil
import subprocess
import sys
import time
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("capture_docs", HERE / "capture-docs.py")
cd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cd)

REPO = cd.REPO
ROOT = pathlib.Path("/tmp/analyser-journey")            # its own isolated root: never another capture's
cd.CAPTURE_ROOT = ROOT
cd.HOME = ROOT / "home"
cd.CONFIG = cd.HOME / ".fluxtion-analyser" / "config"
cd.ENDPOINT = cd.HOME / ".fluxtion-analyser" / "rest-endpoint"
cd.CAPTURE_JAR = ROOT / "fluxtion-auditlog-analyser.jar"
cd.EXPORT_DIR = ROOT / "exchange"
cd.CAPTURE_BIN = ROOT / "bin"

PAGE = REPO / "docs/site/journeys/find-the-first-recorded-breach.md"
ASSETS = REPO / "docs/site/assets/journeys"
BUNDLE = "find-the-first-recorded-breach.fexp"
WALK = "DEMO first breach"
CHART = "DEMO first breach"
TITLE = "Find the first recorded breach"
# the analyser at the size every docs image uses (charts need its side column); the assistant's window beside it,
# overlapping on a smaller screen — each image is ONE window, by window id, so an overlap never reaches either image
MAIN_W, MAIN_H, SIDE_W = 1680, 1050, 560


def launch(popped_out):
    """cd.launch, with the analyser narrower and the assistant's window placed beside it (a machine-tier preference)."""
    cd.stop_capture_app()
    time.sleep(1)
    if cd.HOME.exists():
        shutil.rmtree(cd.HOME)
    cd.HOME.mkdir(parents=True)
    if cd.EXPORT_DIR.exists():
        shutil.rmtree(cd.EXPORT_DIR)
    cd.EXPORT_DIR.mkdir(parents=True)
    cd.set_config(**{"assistant.rest": "true", "theme": "Light", "topologyZoom": "0", "topologySpacing": "100",
                     "topologyTextSize": "11", "topologyOrientation": "TOP_DOWN", "eventFilterCollapsed": "false",
                     "hiddenColumn.count": "5", "hiddenColumn.0": "eventTime", "hiddenColumn.1": "groupingId",
                     "hiddenColumn.2": "eventToString", "hiddenColumn.3": "endTime", "hiddenColumn.4": "thread",
                     "assistant.exports": "true", "assistant.exportDir": str(cd.EXPORT_DIR),
                     "windowX": "60", "windowY": "60", "windowW": str(MAIN_W), "windowH": str(MAIN_H),
                     "assistant.window.poppedOut": "true" if popped_out else "false",
                     "assistant.window.x": str(max(60, 1900 - SIDE_W)), "assistant.window.y": "60",
                     "assistant.window.w": str(SIDE_W), "assistant.window.h": str(MAIN_H)})
    proc = subprocess.Popen(["java", f"-Duser.home={cd.HOME}", "-jar", str(cd.jar())],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    cd._capture_process = proc
    for _ in range(40):
        time.sleep(1)
        if cd.ENDPOINT.exists():
            time.sleep(2)
            return json.loads(cd.ENDPOINT.read_text())
    sys.exit("the analyser did not publish a REST endpoint")


def must(res, what):
    if not res.get("ok"):
        sys.exit(f"{what} failed: {res.get('error')}")
    return res


def context(ep):
    return must(cd.act(ep, "context"), "context").get("context") or {}


def await_step(ep, step):
    for _ in range(80):
        showing = (context(ep).get("walks") or {}).get("showing") or {}
        if showing.get("step") == step and showing.get("phase") not in (None, "PREPARING"):
            return showing
        time.sleep(0.25)
    sys.exit(f"step {step} did not settle")


def shoot_window(ep, name, title):
    """One NATIVE window capture by window id — the main frame (title None) or the assistant window."""
    wid = cd.window_id(ep.get("pid", -1), title)
    if wid is None:
        sys.exit(f"no window found for {name} ({title})")
    scratch = cd.EXPORT_DIR / f"native-{name}"
    for attempt in range(3):                       # the window server occasionally refuses one shot mid-repaint
        subprocess.run(["screencapture", "-x", "-o", "-l", str(wid), str(scratch)], check=False, capture_output=True)
        if scratch.exists() and scratch.stat().st_size > 0:
            break
        time.sleep(0.8)
    if not scratch.exists() or scratch.stat().st_size == 0:
        sys.exit(f"could not create image for {name}: grant Screen Recording to the app running this script")
    ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy(scratch, ASSETS / name)
    print(f"  ✓ {name}  ({(ASSETS / name).stat().st_size // 1024} KB, window capture)")


def facts(ep):
    """Every number the dialogue states, ASKED of the analyser — never typed."""
    whole = must(cd.act(ep, "aggregate", {"metric": "count", "groupBy": "dimension"}), "aggregate")["result"]
    breach = must(cd.act(ep, "aggregate", {"metric": "count", "filter": {"dimensions": ["RiskBreachEvent"]}}),
                  "aggregate RiskBreachEvent")["result"]
    first = breach["firstRecordIndex"]
    record = must(cd.act(ep, "read", {"recordIndex": first}), "read first breach")["result"]
    before = must(cd.act(ep, "read", {"recordIndex": first - 1}), "read the record before")["result"]
    pairing = context(ep).get("graphPairing") or {}
    counts = {b["key"]: b["count"] for b in whole["buckets"]}
    return {"total": whole["total"], "counts": counts, "breaches": breach["total"], "first": first,
            "last": breach["lastRecordIndex"], "record": record, "before": before, "pairing": pairing}


def dialogue(f):
    counts = ", ".join(f"{k} {v}" for k, v in sorted(f["counts"].items(), key=lambda kv: -kv[1]))
    return [
        ("t1", "user", "What does this DEMO log contain?"),
        ("t2", "assistant", f"{f['total']} records from the DEMO quote processor: {counts}. That is the whole log — "
                            "no filter is applied, so every count here is over all of it."),
        ("t3", "user", "Where did the application first log a breach?"),
        ("t4", "assistant", f"At record {f['first']}: the first RiskBreachEvent the application itself logged, of "
                            f"{f['breaches']} (the last is record {f['last']}). The chart marks each one with an x on "
                            "the spread. That is the logged breach — not the first time a value crossed a limit, "
                            "which is a different question."),
        ("t5", "user", "What supports that answer?"),
        ("t6", "assistant", f"Record {f['first']}'s own detail: its breachHandler entry is what makes it a breach. "
                            "The topology's verdict says whether the graph declares the nodes this log writes, so "
                            "the record's nodes can be read against the processor's design."),
        ("t7", "user", "So what is the conclusion?"),
        ("t8", "assistant", f"The application first logged a breach at record {f['first']}. Everything above is what "
                            "this bundled log shows; it is not a live answer. Ask your own question about this evidence "
                            "with the assistant, or connect your CLI assistant."),
    ]


def steps():
    return [
        {"caption": "The whole DEMO log: every record, no filter", "view": {"tab": "summary"},
         "targets": [{"target": "records", "caption": "every record of the log"}], "conversationThrough": "t2"},
        {"caption": "The first record where the application logged a breach",
         "view": {"graph": CHART, "record": None},
         "targets": [{"target": "graph:" + CHART, "caption": "each x is a logged RiskBreachEvent"}],
         "conversationThrough": "t4"},
        {"caption": "What supports it: the record's own detail and the pairing verdict", "view": {"tab": "topology"},
         "targets": [{"target": "detail", "caption": "the breach record's detail"},
                     {"target": "topology:verdict", "caption": "does the graph declare this log's nodes?"}],
         "conversationThrough": "t6"},
        {"caption": "The conclusion, and where to go next", "view": {"tab": "summary"},
         "targets": [{"target": "records", "caption": "the evidence this journey rests on"}], "conversationThrough": "t8"},
    ]


def open_received(ep, bundle):
    """Open a bundle as a recipient does (the evidence-bundle guide): --unpack, then the project, then log and graph."""
    work = ROOT / "received"
    work.mkdir(parents=True, exist_ok=True)
    r = subprocess.run(["java", f"-Duser.home={cd.HOME}", "-jar", str(cd.jar()), "--unpack", str(bundle), "--into", str(work)],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("the journey bundle was refused on unpack:\n" + r.stdout + r.stderr)
    copy = pathlib.Path(next(l for l in r.stdout.splitlines() if l.startswith("working copy: "))
                        .split("working copy: ", 1)[1].split("  (")[0])
    must(cd.act(ep, "open", {"project": str(copy / "profile" / "project.fluxtion-settings")}), "open the bundled project")
    log = next((copy / "log").iterdir())
    graph = next((copy / "graph").iterdir())
    must(cd.act(ep, "open", {"log": str(log), "graphml": str(graph), "provenance": "evidence bundle " + bundle.name}),
         "open the bundled log and graph")
    time.sleep(4)
    return copy


def build_bundle():
    """Phase 1: ask the analyser for the facts, save the chart and the journey, play every stop, capture the bundle."""
    print("the DEMO journey: " + TITLE)
    ep = launch(popped_out=False)
    must(cd.act(ep, "open", {"graphml": str(cd.GRAPHML)}), "open graph")
    must(cd.act(ep, "open", {"log": str(cd.SERIES_LOG)}), "open log")
    time.sleep(4)
    f = facts(ep)
    assert f["counts"].get("RiskBreachEvent") == f["breaches"], f
    print(f"  facts: {f['total']} records {f['counts']}; first RiskBreachEvent at record {f['first']} of {f['breaches']}")
    must(cd.act(ep, "graph", {"name": CHART, "series": ["quotePublisher.spread"], "style": "line", "newTab": True}), "chart")
    must(cd.act(ep, "graph", {"name": CHART, "markers": [
        {"label": "logged breach", "glyph": "x", "when": "breachHandler.breachedOn",
         "y": "series:quotePublisher.spread", "payload": "breachHandler.breachedOn"}],
        "notes": [{"recordIndex": f["first"], "text": f"first logged breach: record {f['first']}",
                   "series": "quotePublisher.spread"}],
        "explanation": "Each x is a RiskBreachEvent the application logged. The note marks the first."}), "chart markers")
    time.sleep(1)
    s = steps()
    s[1]["view"]["record"] = f["first"]
    s[2]["view"]["record"] = f["first"]
    s[1]["targets"].insert(0, {"target": f"records:row:{f['first']}", "caption": f"record {f['first']}: the first logged breach"})
    turns = [{"id": i, "role": r, "text": t} for i, r, t in dialogue(f)]
    for st in s:
        st["view"] = {k: v for k, v in st["view"].items() if v is not None}
    must(cd.act(ep, "walk", {"name": WALK, "title": TITLE, "steps": s,
                             "conversation": {"version": 1, "kind": "scripted", "author": "the analyser's DEMO journey",
                                              "turns": turns}}), "save the journey")
    shown = play_all(ep, len(s), shoot=False)
    must(cd.act(ep, "report", {"bundle": {"path": BUNDLE, "notes":
        f"# {TITLE} (DEMO)\n\nA conversation journey over the DEMO quote-series log. Play it from the Reports tab: "
        f"its dialogue is scripted and labelled, and nothing in it runs or calls a provider."}}), "capture the bundle")
    for _ in range(80):
        cap = context(ep).get("capture") or {}
        if cap.get("phase") in ("WRITTEN", "REFUSED"):
            break
        time.sleep(0.25)
    if cap.get("phase") != "WRITTEN":
        sys.exit(f"the bundle was not written: {cap}")
    ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy(cd.EXPORT_DIR / BUNDLE, ASSETS / BUNDLE)
    cd.stop_capture_app()
    (ASSETS / "find-the-first-recorded-breach.facts.json").write_text(json.dumps(
        {"total": f["total"], "counts": f["counts"], "breaches": f["breaches"], "first": f["first"], "last": f["last"],
         "turns": turns, "steps": s, "shown": shown}, indent=1) + "\n")
    print(f"  bundle: {BUNDLE}; every stop played: " + ", ".join(x.get("phase", "?") for x in shown))


def play_all(ep, count, shoot):
    shown = []
    for n in range(1, count + 1):
        must(cd.act(ep, "walk", {"name": WALK, "play": True, "step": n}), f"play step {n}")
        st = await_step(ep, n)
        if st.get("phase") not in ("SHOWN", "PARTLY_SHOWN"):
            sys.exit(f"step {n} was not shown: {st}")
        shown.append(st)
        time.sleep(1.2)
        if shoot:
            shoot_window(ep, f"journey-step-{n}.png", None)
            shoot_window(ep, f"journey-assistant-step-{n}.png", "Analyser assistant")
    must(cd.act(ep, "walk", {"end": True}), "end")
    return shown


def capture_images():
    """Phase 2: open the COMMITTED bundle as a recipient does, play it with the assistant popped out, photograph it."""
    bundle = ASSETS / BUNDLE
    if not bundle.exists():
        sys.exit("run the bundle phase first: python3 tools/capture-journey.py --bundle")
    probe = subprocess.run(["screencapture", "-x", str(ROOT / "probe.png")], capture_output=True)
    ep = launch(popped_out=True)
    open_received(ep, bundle)
    facts_file = json.loads((ASSETS / "find-the-first-recorded-breach.facts.json").read_text())
    shown = play_all(ep, len(facts_file["steps"]), shoot=True)
    cd.stop_capture_app()
    docked_shot()
    return shown


def docked_shot():
    """The docked assistant with no provider configured: the honest live state. A fresh launch opens on the Start page
    (PR #71), where the assistant tab is not on screen, so the DEMO workspace is opened first."""
    ep = launch(popped_out=False)
    must(cd.act(ep, "open", {"graphml": str(cd.GRAPHML)}), "open graph")
    must(cd.act(ep, "open", {"log": str(cd.SERIES_LOG)}), "open log")
    time.sleep(4)
    must(cd.act(ep, "spotlight", {"target": "tab:assistant", "caption": "the live assistant, docked"}), "show the tab")
    must(cd.act(ep, "spotlight", {"clear": True}), "clear")
    time.sleep(1)
    shoot_window(ep, "assistant-docked-no-provider.png", None)
    cd.stop_capture_app()


def main():
    phases = [a for a in sys.argv[1:] if a in ("--bundle", "--images", "--docked", "--page")] or ["--bundle", "--images", "--page"]
    if "--bundle" in phases:
        build_bundle()
    shown = None
    if "--images" in phases:
        shown = capture_images()
    elif "--docked" in phases:
        docked_shot()
    if "--page" in phases:
        bundle = ASSETS / BUNDLE
        facts_file = json.loads((ASSETS / "find-the-first-recorded-breach.facts.json").read_text())
        verify = subprocess.run(["java", "-jar", str(cd.jar()), "--verify", str(bundle)], capture_output=True, text=True)
        if verify.returncode != 0:
            sys.exit("the bundle does not verify:\n" + verify.stdout + verify.stderr)
        data = bundle.read_bytes()
        with zipfile.ZipFile(bundle) as z:
            members = sorted(n for n in z.namelist() if n != "manifest.json")
        write_page(facts_file, facts_file["turns"], facts_file["steps"], shown or facts_file["shown"], verify.stdout,
                   len(data), hashlib.sha256(data).hexdigest(), members, images=all(
                (ASSETS / f"journey-step-{n}.png").exists() for n in range(1, len(facts_file["steps"]) + 1)),
                   docked=(ASSETS / "assistant-docked-no-provider.png").exists())
        print(f"wrote {PAGE.relative_to(REPO)}; {BUNDLE} {len(data)} bytes")
    if "--keep" not in sys.argv:
        shutil.rmtree(ROOT, ignore_errors=True)


def write_page(f, turns, s, shown, verify, size, sha, members, images=True, docked=True):
    identity = next((l.split(": ", 1)[1] for l in verify.splitlines() if l.startswith("identity: ")), "?")
    lines = [
        "<!-- GENERATED by tools/capture-journey.py from a real run — edit the script, not this file. -->",
        f"# {TITLE}",
        "",
        "A **conversation journey**: a spotlight walk over the DEMO quote-series log, with a scripted conversation shown "
        "beside the analyser's own views as you step through it. Download the bundle, open it, and press **Play**.",
        "",
        "| | |",
        "|---|---|",
        f"| **What it teaches** | Finding where an application *logged* a breach — its own RiskBreachEvent — and what supports that answer, rather than the first time a value crossed a limit |",
        f"| **Steps** | {len(s)} |",
        "| **Duration** | about 3 minutes (an estimate: four stops, read at your own pace) |",
        "| **Needs** | an analyser newer than 1.28.0 — the first release with conversation journeys. Older ones (1.27.0 and 1.28.0 checked) open the bundle and play the walk without its dialogue |",
        f"| **Download** | [`{BUNDLE}`](../assets/journeys/{BUNDLE}) — {size:,} bytes, sha256 `{sha}` |",
        f"| **Identity** | `{identity}` — compare it with what `analyser --verify` prints |",
        f"| **Bundled evidence** | {', '.join('`' + m + '`' for m in members)} |",
        "| **Not bundled** | the DEMO processor's Java sources: source navigation is unavailable when you play it |",
        "| **Replay records** | none: this bundle carries no replay inputs, and opening or playing it never runs anything |",
        "| **Provider** | **No provider needed for this demonstration.** Nothing is sent anywhere while it plays |",
        "",
        "## Play it",
        "",
        f"1. Download [`{BUNDLE}`](../assets/journeys/{BUNDLE}) and check it: `analyser --verify {BUNDLE}`.",
        "2. Open it (drop it on the Start page, or choose **Open evidence bundle** there). It opens in a disposable working copy; "
        "your own settings are not changed.",
        f"3. In **Reports ▸ Spotlight walks**, select **{TITLE}** and press **Play**. Pop the assistant out "
        "(**Pop out** in its tab) to keep the conversation beside the evidence while the walk changes views.",
        "4. Step with ◀ ▶ on the strip. At the end, **Ask about this evidence** starts a fresh, live conversation with "
        "your own assistant — none of the journey's words go with it — or **Connect your CLI assistant**.",
        "",
        "The conversation is **scripted**, and every surface says so. Its facts were asked of the analyser when the "
        "journey was built, and a test re-checks them against the bundled log on every build. It is not a live model's "
        "output, and nothing in it runs.",
        "",
        "## The journey, as it played",
        "",
    ]
    by_step = {}
    for i, st in enumerate(s):
        by_step[i] = st["conversationThrough"]
    done = 0
    turn_ids = [t["id"] for t in turns]
    for i, st in enumerate(s):
        upto = turn_ids.index(st["conversationThrough"]) + 1
        lines.append(f"### Step {i + 1} — {st['caption']}")
        lines.append("")
        if images:
            lines.append(f"![Step {i + 1}: the analyser](../assets/journeys/journey-step-{i + 1}.png)")
            lines.append("")
        for t in turns[done:upto]:
            who = "**Question (scripted):**" if t["role"] == "user" else "**Answer (scripted — not a live model):**"
            lines.append(f"> {who} {t['text']}")
            lines.append(">")
        if lines[-1] == ">":
            lines.pop()
        done = upto
        lines.append("")
        state = shown[i]
        lines.append(f"*What the analyser showed:* **{state['phase'].replace('_', ' ').lower()}** — " + "; ".join(
            f"{t['target']}: {t['state'].lower() if t['available'] else 'not available (' + t.get('reason', '') + ')'}"
            for t in state.get("targets", [])))
        lines.append("")
        if images:
            lines.append(f"![Step {i + 1}: the assistant, popped out beside it](../assets/journeys/journey-assistant-step-{i + 1}.png)")
            lines.append("")
    lines += [
        "## Your own questions",
        "",
        "The live assistant needs a provider of your own (Settings ▸ Assistant), or none at all if you connect a CLI "
        "assistant instead — see [Connecting an LLM](../connect-an-llm.md). With no provider configured it sends "
        "nothing, and says so:",
        "",
        "![The docked assistant with no provider configured](../assets/journeys/assistant-docked-no-provider.png)"
        if docked else "*(This screenshot is captured natively with `--docked`; it is not in this build.)*",
        "",
        "*Regenerate with `mvn package && python3 tools/capture-journey.py`. The dialogue's numbers come from the run; "
        "the images are native window captures under an isolated home with only the DEMO data.*",
        "",
    ]
    PAGE.parent.mkdir(parents=True, exist_ok=True)
    PAGE.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
