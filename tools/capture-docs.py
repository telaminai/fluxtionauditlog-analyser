#!/usr/bin/env python3
"""Regenerate the documentation screenshots by driving a real analyser.

Why this exists
---------------
The screenshots shipped with the first public release were taken against a **real** audit log. They
carried live venue, vendor and project names into a public repository and onto the published docs site,
and the anonymisation sweep never caught it because `grep` cannot read a PNG.

So docs images are no longer taken by hand. This script launches the analyser on the **demo fixture**
(`com.acme…`, `DEMO-A`) and drives it over the localhost REST transport, so every published image is
anonymous by construction rather than by inspection.

Usage
-----
    python3 tools/capture-docs.py            # regenerate everything into docs/site/assets
    python3 tools/capture-docs.py --keep     # leave the app running afterwards

Runs the app under an isolated home (/tmp/analyser-docs/home) so no real setting can reach a shot.
Requires: a built jar (`mvn package`), and macOS `screencapture` with Screen Recording permission for
the invoking terminal — a native capture is what gives the window its title bar.

File exports and the guard
--------------------------
Since v1.1.0 the `screenshot` verb is **opt-in and directory-confined**: it writes only inside an export
directory the user has chosen, and it never overwrites. This script therefore points the app at a
throwaway export directory and asks for a **unique name per capture**, then does its own copy into
`docs/site/assets`.

It deliberately does not ask for that guard to be relaxed. The confinement exists because a verb-driven
write is one no human approved, and "the docs script is inconvenienced" is not a reason to hand an agent
an arbitrary path. Regeneration is the script's problem to solve, and it solves it by managing its own
filenames on this side of the socket.
"""
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent
ASSETS = REPO / "docs" / "site" / "assets"
LOG = REPO / "src/test/resources/topology/demo-quote-audit.yaml"
# a longer run of the same graph — a chart drawn from ten flat records shows nothing
SERIES_LOG = REPO / "src/test/resources/topology/demo-quote-series.yaml"
GRAPHML = REPO / "src/test/resources/topology/demo-quote-processor.graphml"
ROOT = REPO / "examples/fixture-generator/src/main/java"
PROCESSOR = "com.acme.demo.generated.DemoQuoteProcessor"
# The capture analyser's HOME — never the real one. Until 2026-08-27 this script ran the app under the
# machine's own ~/.fluxtion-analyser, pinning theme and columns "without disturbing the rest" — and "the
# rest" was this machine's real source roots and event processors, in force for every capture ever taken.
# Nothing rendered them until the Project panel (M37) listed the processors, and three shots carried a real
# venue's class name and an employer's package — caught before they were committed, so nothing reached
# the site. Caught by READING the images, which is
# the rule; fixed here by construction: an isolated user.home has only what this script puts in it, so
# "loaded only with the demo fixture" is finally true of the configuration as well as the log.
HOME = pathlib.Path("/tmp/analyser-docs/home")
CONFIG = HOME / ".fluxtion-analyser" / "config"
ENDPOINT = HOME / ".fluxtion-analyser" / "rest-endpoint"
# the app writes here; this script copies out of it. Cleared each run so "never overwrite" is satisfied
# by construction rather than by hoping the names are fresh.
EXPORT_DIR = pathlib.Path(tempfile.gettempdir()) / "analyser-doc-capture"
# A throwaway project for the project-profile shots. Deliberately under a NEUTRAL path: the status bar
# prints the profile's full path, and rule 1 exists because a screenshot carries strings grep cannot see.
DEMO_PROJECT = pathlib.Path("/tmp/analyser-docs/demo-quote-project")


def jar():
    hits = sorted((REPO / "target").glob("fluxtion-auditlog-analyser-*.jar"))
    if not hits:
        sys.exit("no jar — run `mvn package` first")
    return hits[-1]


def set_config(**values):
    """Force the settings a capture depends on (theme, REST) without disturbing the rest."""
    lines = CONFIG.read_text().splitlines() if CONFIG.exists() else []
    for key, value in values.items():
        lines = [l for l in lines if not l.startswith(key + "=")]
        lines.append(f"{key}={value}")
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    CONFIG.write_text("\n".join(lines) + "\n")


def make_demo_project():
    """A project profile pointing at the demo fixture — anonymous by construction, like every other shot."""
    profile = DEMO_PROJECT / ".analyser" / "project.fluxtion-settings"
    profile.parent.mkdir(parents=True, exist_ok=True)
    profile.write_text(
        "share.version=1\n"
        f"sourceRoot.count=1\nsourceRoot.0={ROOT}\n"
        f"eventProcessorFqn.count=1\neventProcessorFqn.0={PROCESSOR}\n"
        f"selectedEventProcessor={PROCESSOR}\n"
        "mavenRepo.count=0\nmavenRepoSearch=true\n")
    return profile


def menu_capture(ep, menu, name):
    """Open a top-level menu, capture natively, close it.

    The painted fallback cannot be used here: a Swing popup is a separate layer and never appears in the
    content pane's paint. So this shot needs the native path, and is skipped rather than faked without it.
    """
    _attempted.append(name)
    res = act(ep, "screenshot", {"path": f"menu-{menu}.png", "scope": f"menu:{menu}"})
    if not res.get("ok"):
        _failed.append(name)                # a verb failure produced no image either — count it
        return False
    b = res["wrote"]["windowBounds"]
    raise_window()
    time.sleep(0.8)                     # let the popup lay out before the shutter
    target = ASSETS / name
    # scratch path, not the asset — see capture(): aiming at an existing asset makes exists() a
    # test of the PREVIOUS run, so a failed shutter reports success and leaves the old image
    shot = EXPORT_DIR / f"native-menu-{menu}.png"
    # A menu shot MUST stay a region capture (H3): the popup is a separate window and `-l` takes one id,
    # so a window-id shot of the frame would show the frame with the menu missing. The overlap risk is
    # therefore still live for this one shot — raise_window() above is the mitigation, and READ THE IMAGE.
    subprocess.run(["screencapture", "-x", "-R",
                    f"{b['x']},{b['y']},{b['width']},{b['height']}", str(shot)], check=False)
    act(ep, "screenshot", {"path": f"menu-{menu}-close.png", "scope": "menu:close"})
    if shot.exists() and shot.stat().st_size > 0:
        shutil.copy(shot, target)
        print(f"  ✓ {name}  ({target.stat().st_size // 1024} KB)")
        return True
    print(f"  ! {name} skipped — a menu shot needs a native capture (Screen Recording permission)")
    _failed.append(name)
    return False


def launch(theme, project=None):
    subprocess.run(["pkill", "-f", "fluxtion-auditlog-analyser"], check=False)
    time.sleep(1)
    # a fresh isolated home every launch: whatever the previous run's app remembered is not ours to keep
    if HOME.exists():
        shutil.rmtree(HOME)
    HOME.mkdir(parents=True)
    # a fresh view every run: a remembered zoom or a stale topology would make the images irreproducible
    # a fresh export directory per launch: the guard refuses to overwrite, and regenerating the same
    # nine filenames is the entire job
    if EXPORT_DIR.exists():
        shutil.rmtree(EXPORT_DIR)
    EXPORT_DIR.mkdir(parents=True)
    # The records table's column set is user state and persists. Left unpinned it leaks whoever ran
    # the app last into "reproducible" images — a capture once showed `nodeLogs` where the previous one
    # showed `thread`, with this script and the table code both untouched. Pin it the way window
    # geometry, zoom and theme are already pinned. `hiddenColumn.count` is what marks the list as
    # configured; without it the app applies its own defaults instead of these.
    set_config(**{"assistant.rest": "true", "theme": theme, "topologyZoom": "0",
                  "topologySpacing": "100", "topologyTextSize": "11",
                  "topologyOrientation": "TOP_DOWN", "topologySyncSource": "true",
                  "eventFilterCollapsed": "false",
                  # `thread` hidden too: the full set is 9 columns, and hiding only four leaves BOTH
                  # `thread` and `nodeLogs` visible — a third layout matching neither the original
                  # shots (thread, no nodeLogs) nor the released 1.6.0 assets (nodeLogs, no thread).
                  # The pin's value must agree with the committed baseline or the next run churns
                  # every records shot to a state nobody chose. The released site shows nodeLogs.
                  "hiddenColumn.count": "5",
                  "hiddenColumn.0": "eventTime", "hiddenColumn.1": "groupingId",
                  "hiddenColumn.2": "eventToString", "hiddenColumn.3": "endTime",
                  "hiddenColumn.4": "thread",
                  "assistant.exports": "true", "assistant.exportDir": str(EXPORT_DIR),
                  "activeProjectPath": str(project) if project else "",
                  # Fixed window geometry, or every run produces images of a different size and the
                  # whole asset set churns for no visual change. Documentation images should be
                  # reproducible; that is the reason they are generated rather than taken by hand.
                  "windowX": "60", "windowY": "60", "windowW": "1680", "windowH": "1050"})
    subprocess.Popen(["java", f"-Duser.home={HOME}", "-jar", str(jar()), str(LOG)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        time.sleep(1)
        if ENDPOINT.exists():
            time.sleep(2)
            return json.loads(ENDPOINT.read_text())
    sys.exit("the app did not publish a REST endpoint — is the transport enabled?")


def act(ep, verb, params=None):
    """One verb call, backing off on the socket's rate limit.

    The limit is per-second and deliberately low — it exists so a runaway agent cannot hammer the app.
    A capture run is a burst of small calls and will meet it, so this waits rather than failing the run;
    an unhandled 429 previously aborted the whole capture mid-way through.
    """
    body = json.dumps({"v": 1, "action": verb, "params": params or {}}).encode()
    for attempt in range(6):
        req = urllib.request.Request(ep["url"] + "/action", data=body, method="POST",
                                     headers={"Content-Type": "application/json",
                                              "X-Analyser-Token": ep["token"]})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                out = json.loads(r.read())
            break
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(0.5 * (attempt + 1))
                continue
            try:
                out = json.loads(e.read())
            except Exception:
                out = {"ok": False, "error": f"HTTP {e.code}"}
            break
    else:
        out = {"ok": False, "error": "rate limited after 6 attempts"}
    if not out.get("ok"):
        print(f"  ! {verb} failed: {out.get('error')}")
    return out


def window_id(pid):
    """The CGWindowID of the analyser's main window, or None.

    Polish H3: `screencapture -R` photographs a REGION OF THE SCREEN, so anything overlapping the window
    lands in a public docs image — a browser with personal bookmarks nearly shipped that way (B-M20-2).
    `screencapture -l <id>` photographs ONE WINDOW, whatever is in front of it. The id comes from
    CGWindowListCopyWindowInfo via JXA, which ships with macOS (pyobjc does not).

    Matched on the OWNER PID — the pid the app itself publishes in its rest-endpoint file — plus layer 0
    (a normal window, not a popup); the largest such window wins. Not on owner name or title: the first
    cut matched owner /java/ and a title prefix, and found nothing, because a `java -jar` window's owner
    name is the main class ("Main") and window titles are only visible to callers holding Screen
    Recording permission. The pid is the one identity the two processes agree on.
    """
    js = ('ObjC.import("CoreGraphics");'
          'var l=$.CGWindowListCopyWindowInfo($.kCGWindowListOptionOnScreenOnly|$.kCGWindowListExcludeDesktopElements,0);'
          'var a=ObjC.deepUnwrap(ObjC.castRefToObject(l));var best=null;'
          'for (var w of a){'
          ' if(w.kCGWindowOwnerPID!==%d)continue;'
          ' if(w.kCGWindowLayer!==0)continue;'
          ' var area=w.kCGWindowBounds.Width*w.kCGWindowBounds.Height;'
          ' if(!best||area>best.area)best={id:w.kCGWindowNumber,area:area};}'
          'best?String(best.id):""') % int(pid)
    out = subprocess.run(["osascript", "-l", "JavaScript", "-e", js], capture_output=True, text=True).stdout.strip()
    return int(out) if out.isdigit() else None


def native_capture(ep, bounds, shot):
    """Capture the analyser window to `shot`: by window id when we can get one, by region otherwise."""
    wid = window_id(ep.get("pid", -1))
    if wid is not None:
        # -o omits the window shadow so the image is the window's own bounds, as the region shot was
        subprocess.run(["screencapture", "-x", "-o", "-l", str(wid), str(shot)], check=False)
        return "window"
    print("  ! no window id found — falling back to a REGION capture; check the image for overlaps (H3)")
    subprocess.run(["screencapture", "-x", "-R",
                    f"{bounds['x']},{bounds['y']},{bounds['width']},{bounds['height']}", str(shot)], check=False)
    return "region"


def raise_window():
    """Bring the analyser to the front before a native capture.

    `screencapture -R` photographs a REGION OF THE SCREEN, not a window, so anything overlapping the
    analyser is captured with it. A browser window once landed in a documentation shot complete with its
    URL bar and personal bookmarks — the exact leak CLAUDE.md rule 1 exists to prevent, and one a text
    sweep can never catch. The app raises itself too; this is the belt to that pair of braces.
    """
    subprocess.run(["osascript", "-e",
                    'tell application "System Events" to set frontmost of '
                    'the first process whose unix id is (do shell script '
                    '"pgrep -f fluxtion-auditlog-analyser | head -1") to true'],
                   check=False, capture_output=True)
    time.sleep(0.4)


def capture(ep, name):
    """Native window capture — the painted fallback cannot draw the title bar."""
    # a relative name lands inside the export directory; unique per call because the guard never
    # overwrites, and this script regenerates the same asset names every run
    _attempted.append(name)
    scratch_name = f"{len(_captured):02d}-{name}"
    res = act(ep, "screenshot", {"path": scratch_name})
    if not res.get("ok"):
        print(f"  ! {name}: {res.get('error')}")
        _failed.append(name)                # a verb failure produced no image either — count it
        return False
    _captured.append(scratch_name)
    painted = EXPORT_DIR / scratch_name

    b = res["wrote"]["windowBounds"]
    raise_window()
    target = ASSETS / name
    # Capture to a SCRATCH path, never straight onto the asset. `screencapture` writes nothing when it
    # fails (no Screen Recording permission → "could not create image from rect"), and testing
    # `target.exists()` after aiming at the asset is not a success test at all: the asset is already
    # there from the previous run, so a failed capture reported ✓ and left the old image in place.
    # Every shot then looked regenerated while nothing had been taken — the silent-staleness failure
    # this whole script exists to prevent, reproduced inside the script itself.
    shot = EXPORT_DIR / f"native-{scratch_name}"
    mode = native_capture(ep, b, shot)   # H3: one WINDOW, not a screen region
    if shot.exists() and shot.stat().st_size > 0:
        shutil.copy(shot, target)
        print(f"  ✓ {name}  ({target.stat().st_size // 1024} KB, {mode} capture)")
        return True
    # No Screen Recording permission. NEVER replace a good native asset with a painted one — the
    # painted path cannot draw the title bar and renders some labels with dropped glyphs, so it is a
    # downgrade, and a silent downgrade is worse than a stale file you were told about.
    if target.exists():
        print(f"  ! {name}  NOT REGENERATED — kept the existing image (no native capture available)")
        _failed.append(name)
        return False
    shutil.copy(painted, target)
    print(f"  ~ {name}  painted fallback only — NOT publication quality, do not commit as-is")
    _failed.append(name)
    return False


_captured = []      # scratch names — numbers the painted exports within a run
_attempted = []     # every asset this run tried to produce, window and menu shots alike
_failed = []        # the subset it could not — a failed verb call counts, not only a failed shutter


def seed(ep):
    """Every capture starts from the same loaded state."""
    act(ep, "source_root", {"add": [str(ROOT)]})
    act(ep, "open", {"processor": PROCESSOR})
    act(ep, "open", {"graphml": str(GRAPHML)})


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)

    print("light theme")
    ep = launch("Light")
    seed(ep)

    # M36.5: the START PAGE — what the analyser opens on with no log. Taken FIRST, before seed()'s log
    # is showing, by closing it: the page is a state, so the only way to photograph it is to be in that
    # state. `open {close: "all"}` is the same door File ▸ Close log uses, so this shoots the real
    # thing rather than a mode built for the camera.
    act(ep, "open", {"close": "all"})
    time.sleep(1)
    capture(ep, "start-page.png")
    # Put the log BACK explicitly. launch() opens it from the command line and seed() only adds the
    # source root, processor and graph — so closing it here and calling seed() left every light-theme
    # shot below photographing an empty analyser ("goto failed: no log is loaded", caught by reading
    # the run's own output). The load is asynchronous, so wait for context to show records.
    act(ep, "open", {"log": str(LOG)})
    for _ in range(20):
        time.sleep(0.5)
        if (act(ep, "context").get("context", {}).get("log") or {}).get("records"):
            break
    else:
        sys.exit("the demo log did not reload after the start-page capture")
    seed(ep)

    # the front page: the whole tool at work — records, the logical detail, and the graph of the cycle
    act(ep, "goto", {"recordIndex": 5, "reveal": True})
    act(ep, "topology", {"select": "quotePublisher", "scope": "neighbours"})
    capture(ep, "screenshot-light.png")

    # M37: the Project panel — default shown, so it is in every shot above; this one is FOR it. The demo
    # set has no project, so the first row is the "No project — using your own settings" sentence, which
    # is the state most first-time readers of the page are in.
    capture(ep, "project-panel.png")

    # records and filtering: the table, the time range, and one record read out logically
    act(ep, "topology", {"showAll": True})
    act(ep, "goto", {"recordIndex": 6, "reveal": True})
    capture(ep, "records-overview.png")

    # flagging: findings the user has marked, with their notes
    act(ep, "flag", {"recordIndexes": [6, 7],
                     "note": "live orders hit the risk limit — check what riskMonitor did next"})
    act(ep, "goto", {"recordIndex": 7, "reveal": True})
    capture(ep, "flagged-only.png")

    # source navigation: the processor and the node it dispatches into, side by side
    act(ep, "topology", {"select": "quotePublisher", "source": True})
    capture(ep, "source-navigation.png")

    # topology: stepping a cycle
    act(ep, "topology", {"source": False, "showAll": True})
    act(ep, "goto", {"recordIndex": 0, "reveal": True})
    act(ep, "topology", {"step": 2})
    capture(ep, "topology-step-through.png")

    # topology: exploring by scope
    act(ep, "topology", {"showAll": True, "select": "quotePublisher", "scope": "neighbours"})
    capture(ep, "topology-explore.png")

    # graphs need the long log: a wandering price and an order book that fills and drains
    act(ep, "open", {"log": str(SERIES_LOG)})
    time.sleep(2)
    act(ep, "graph", {"name": "Mid price", "series": ["priceListener.mid"], "style": "line"})
    time.sleep(1)
    capture(ep, "graph-series-light.png")

    print("dark theme")
    ep = launch("Dark")
    seed(ep)
    act(ep, "goto", {"recordIndex": 5, "reveal": True})
    act(ep, "topology", {"select": "quotePublisher", "scope": "neighbours"})
    capture(ep, "screenshot-dark.png")

    act(ep, "open", {"log": str(SERIES_LOG)})
    time.sleep(2)
    act(ep, "graph", {"name": "Mid price", "series": ["priceListener.mid"], "style": "line"})
    time.sleep(1)
    capture(ep, "graph-series-dark.png")
    act(ep, "graph", {"name": "Order book", "series": ["orderTracker.live"],
                      "style": "step", "newTab": True})
    time.sleep(1)
    capture(ep, "graph-step-dark.png")

    # markers (M32): the value line answers "what was it", the glyphs answer "what happened" —
    # each carrying the order id that makes it a signpost back to a record
    act(ep, "graph", {"name": "Fills on the spread", "series": ["quotePublisher.spread"],
                      "style": "line", "newTab": True})
    act(ep, "graph", {"name": "Fills on the spread", "markers": [
        {"label": "order live", "glyph": "triangleUp", "when": "orderTracker.orderId",
         "y": "series:quotePublisher.spread", "payload": "orderTracker.orderId"},
        {"label": "risk breach", "glyph": "x", "when": "breachHandler.breachedOn",
         "y": "series:quotePublisher.spread", "payload": "breachHandler.breachedOn"}]})
    time.sleep(1)
    capture(ep, "graph-markers-dark.png")

    # thresholds + bands + markers + the on-plot caption, one chart (M28.5/.6, M32, notes):
    # the band shades EXACTLY where the line is above the guide — a claim the reader can verify
    # by eye — while the breach markers are an independent event layer (they track the order-count
    # limit, not the spread; the explanation says so rather than letting the composition imply it)
    act(ep, "graph", {"name": "Spread guardrails", "series": ["quotePublisher.spread"],
                      "style": "line", "newTab": True})
    act(ep, "graph", {"name": "Spread guardrails",
                      "guides": [{"value": 0.02, "label": "2bp spread cap"}],
                      "bands": [{"expr": "quotePublisher.spread > 0.02", "label": "above cap"}],
                      "markers": [
                          {"label": "risk breach", "glyph": "x", "when": "breachHandler.breachedOn",
                           "y": "series:quotePublisher.spread", "payload": "breachHandler.breachedOn"}],
                      "explanation": "Guide: the cap. Band: the regime above it. Each x: a breach "
                                     "event (order-count limit, not the spread) — click one to open "
                                     "its record.",
                      "notes": [{"recordIndex": 1, "text": "session opens above the cap",
                                 "series": "quotePublisher.spread"}]})
    time.sleep(1)
    capture(ep, "graph-bands-dark.png")

    # investigation report (M33): the ACCOUNT, not just the evidence — a finding, a chart, a derived
    # table with its printed highlight rule, and narrative wearing its standing label. The flag also
    # exercises the M32.6 rug on the charts above.
    act(ep, "flag", {"recordIndexes": [7], "note": "liveOrders breached the limit while the spread "
                     "sat above the cap", "fix": "review riskMonitor.limit against expected order flow"})
    act(ep, "report", {"name": "breach-inv", "title": "Order-limit breach investigation", "sections": [
        {"kind": "narrative", "text": "Breaches cluster while the spread holds above the cap; the "
                                      "order count reaches its limit within the first minute."},
        {"kind": "finding", "recordIndex": 7},
        {"kind": "chart", "graph": "Fills on the spread"},
        {"kind": "table", "call": {"verb": "read", "fields": "orderTracker.live, quotePublisher.spread",
                                   "recordIndex": "0", "after": "7"},
         "columns": [{"key": "recordIndex", "heading": "record"},
                     {"key": "logTime", "heading": "time", "format": "time"},
                     {"key": "orderTracker.live", "heading": "live", "format": "0"},
                     {"key": "quotePublisher.spread", "heading": "spread", "format": "0.00000"}],
         "rowWhen": "orderTracker.live > 0", "rowWhenLabel": "orders live"}]})
    time.sleep(1)
    capture(ep, "reports-dark.png")

    # ---- project profiles (M20.4) ------------------------------------------------------------
    print("project profiles")
    profile = make_demo_project()
    ep = launch("Light")
    menu_capture(ep, "File", "projects-file-menu.png")

    ep = launch("Light", project=profile)      # relaunch WITH the project active
    seed(ep)
    act(ep, "goto", {"recordIndex": 3, "reveal": True})
    capture(ep, "projects-active.png")

    if "--keep" not in sys.argv:
        subprocess.run(["pkill", "-f", "fluxtion-auditlog-analyser"], check=False)
        shutil.rmtree(EXPORT_DIR, ignore_errors=True)
        shutil.rmtree(DEMO_PROJECT.parent, ignore_errors=True)
    # The count of shots ATTEMPTED was never the interesting number. Say how many images this run
    # actually produced, and exit non-zero when any did not, so a capture run cannot look successful
    # while leaving the assets exactly as it found them.
    if _failed:
        print(f"done — {len(_attempted) - len(_failed)} of {len(_attempted)} regenerated; "
              f"{len(_failed)} NOT captured: {', '.join(_failed)}")
        print("  grant Screen Recording permission to this terminal and re-run before committing docs")
        sys.exit(1)
    print(f"done — {len(_attempted)} captures, all native")


if __name__ == "__main__":
    main()
