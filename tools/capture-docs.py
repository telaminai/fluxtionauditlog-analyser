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
    python3 tools/capture-docs.py --mcp      # regenerate only the MCP setup/dialog shots
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
# M19 tutorial shots are of a REAL generated bundle, not the demo fixture — the page teaches that
# bundle, so a mocked-up screenshot would teach something that does not exist. The bundle must be
# staged under a NEUTRAL path: its own paths are rendered in the title bar, the Project panel and the
# status bar, and a scratch directory carrying an account name would put it in a PNG the sweep cannot
# read. Stage it with tools/stage-tutorial-bundle.sh, which refuses a path containing a home directory.
TUTORIAL_PROJECT = pathlib.Path("/tmp/fluxtion-tutorial/audit-analyser-bundle")
TUTORIAL_LOG = TUTORIAL_PROJECT / "logs" / "audit-audit-analyser-bundle.yaml"
TUTORIAL_GRAPHML = TUTORIAL_PROJECT / "src/main/resources/com/example/myapp/generated/MarketProcessor.graphml"
# The capture analyser's HOME — never the real one. Until 2026-08-27 this script ran the app under the
# machine's own ~/.fluxtion-analyser, pinning theme and columns "without disturbing the rest" — and "the
# rest" was this machine's real source roots and event processors, in force for every capture ever taken.
# Nothing rendered them until the Project panel (M37) listed the processors, and three shots carried a real
# venue's class name and an employer's package — caught before they were committed, so nothing reached
# the site. Caught by READING the images, which is
# the rule; fixed here by construction: an isolated user.home has only what this script puts in it, so
# "loaded only with the demo fixture" is finally true of the configuration as well as the log.
CAPTURE_ROOT = pathlib.Path("/tmp/analyser-docs")
HOME = CAPTURE_ROOT / "home"
CONFIG = HOME / ".fluxtion-analyser" / "config"
ENDPOINT = HOME / ".fluxtion-analyser" / "rest-endpoint"
CAPTURE_BIN = CAPTURE_ROOT / "bin"
CAPTURE_JAR = CAPTURE_ROOT / "fluxtion-auditlog-analyser.jar"
# the app writes here; this script copies out of it. Cleared each run so "never overwrite" is satisfied
# by construction rather than by hoping the names are fresh.
EXPORT_DIR = pathlib.Path(tempfile.gettempdir()) / "analyser-doc-capture"
# A throwaway project for the project-profile shots. Deliberately under a NEUTRAL path: the status bar
# prints the profile's full path, and rule 1 exists because a screenshot carries strings grep cannot see.
DEMO_PROJECT = CAPTURE_ROOT / "demo-quote-project"
_capture_process = None


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
    # M38: the profile is portable context — a skill-shaped runbook, a glossary and a saved analysis, all demo
    ops = DEMO_PROJECT / "ops"
    ops.mkdir(parents=True, exist_ok=True)
    (ops / "restart-quote-service.md").write_text(
        "---\nname: restart-quote-service\n"
        "description: Restart the DEMO quote service after a config change; what to check first, how to verify.\n---\n"
        "1. Confirm no live orders: `quotePublisher.liveOrders` must read 0 in the latest cycle.\n"
        "2. Restart the service through the deployment tool.\n"
        "3. Verify: the next audit log opens with a MarketDataEvent within 5s and `spread` is back near 0.01.\n")
    docs = DEMO_PROJECT / "docs"
    docs.mkdir(parents=True, exist_ok=True)
    (docs / "glossary.md").write_text(
        "# Glossary\n\n- **live**: an order the venue has acknowledged and not yet filled or cancelled\n"
        "- **spread**: quotePublisher ask minus bid, in price units; 0.01 is normal here\n"
        "- **breach**: liveOrders above the risk limit; the RiskBreachEvent that follows is routine, not an outage\n")
    uat = DEMO_PROJECT / "logs" / "uat"
    uat.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(REPO / "src/test/resources/topology/demo-quote-audit-traced.yaml", uat / "quote-service-uat.yaml")
    profile.write_text(
        "share.version=1\n"
        "environment.count=1\nenvironment.0.name=uat\nenvironment.0.provenance=DEMO quote service · uat\n"
        "environment.0.logDir=logs/uat\n"
        f"sourceRoot.count=1\nsourceRoot.0={ROOT}\n"
        f"eventProcessorFqn.count=1\neventProcessorFqn.0={PROCESSOR}\n"
        f"selectedEventProcessor={PROCESSOR}\n"
        "mavenRepo.count=0\nmavenRepoSearch=true\n"
        "runbook.count=1\nrunbook.0.name=restart\nrunbook.0.path=ops/restart-quote-service.md\n"
        "vocabulary=docs/glossary.md\n"
        "analysis.count=1\nanalysis.0.name=spread breach\n"
        "analysis.0.rationale=every breach incident starts the same way: the spread before it\n"
        "analysis.0.param.count=1\nanalysis.0.param.0.name=log\n"
        "analysis.0.step.count=2\n"
        "analysis.0.step.0.action=open\nanalysis.0.step.0.params={\"log\": \"{log}\"}\n"
        "analysis.0.step.1.action=graph\n"
        "analysis.0.step.1.params={\"name\": \"Spread before the breach\", \"series\": [\"quotePublisher.spread\"]}\n")
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
    raise_window(ep.get("pid"))
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


def stop_capture_app():
    """Stop only the analyser process this harness launched; never kill a person's open window."""
    global _capture_process
    if _capture_process is not None and _capture_process.poll() is None:
        _capture_process.terminate()
        try:
            _capture_process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            _capture_process.kill()
            _capture_process.wait(timeout=5)
    _capture_process = None


def stage_mcp_setup_launchers():
    """Make every visible setup path neutral before a public screenshot is taken."""
    CAPTURE_JAR.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jar(), CAPTURE_JAR)
    jbang_launcher = HOME / ".jbang" / "bin" / "analyser"
    jbang_launcher.parent.mkdir(parents=True, exist_ok=True)
    # McpLaunchCommand deliberately discovers the documented JBang launcher by file identity. It is
    # shown but never executed in these shots, so a neutral placeholder proves the layout without
    # enrolling a real client or exposing a developer's launcher path.
    jbang_launcher.write_text("# documentation capture launcher — never executed\n")
    jbang_launcher.chmod(0o700)
    CAPTURE_BIN.mkdir(parents=True, exist_ok=True)
    claude = CAPTURE_BIN / "claude"
    claude.write_text("#!/bin/sh\nexit 0\n")
    claude.chmod(0o700)
    return CAPTURE_JAR


def launch(theme, project=None, mcp_setup=False):
    global _capture_process
    stop_capture_app()
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
    launch_jar = stage_mcp_setup_launchers() if mcp_setup else jar()
    env = None
    if mcp_setup:
        env = os.environ.copy()
        env["PATH"] = str(CAPTURE_BIN) + os.pathsep + env.get("PATH", "")
    _capture_process = subprocess.Popen(["java", f"-Duser.home={HOME}", "-jar", str(launch_jar), str(LOG)],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
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


def window_id(pid, title=None, fallback_rank=0):
    """The CGWindowID of this analyser's main window or one of its dialogs, or None.

    Polish H3: `screencapture -R` photographs a REGION OF THE SCREEN, so anything overlapping the window
    lands in a public docs image — a browser with personal bookmarks nearly shipped that way (B-M20-2).
    `screencapture -l <id>` photographs ONE WINDOW, whatever is in front of it. The id comes from
    CGWindowListCopyWindowInfo via JXA, which ships with macOS (pyobjc does not).

    Matched on the OWNER PID — the pid the app itself publishes in its rest-endpoint file — plus layer 0
    (a normal window, not a popup); the largest such window wins. For a dialog we prefer its exact title
    and use its stable area rank only when macOS withholds window names. Not on owner name: a `java -jar`
    window's owner is the main class ("Main"), not a product name. The pid is the one identity the two
    processes agree on.
    """
    expected_title = json.dumps(title) if title else "null"
    js = ('ObjC.import("CoreGraphics");'
          'var l=$.CGWindowListCopyWindowInfo($.kCGWindowListOptionOnScreenOnly|$.kCGWindowListExcludeDesktopElements,0);'
          'var a=ObjC.deepUnwrap(ObjC.castRefToObject(l));var candidates=[];var wanted=%s;'
          'for (var w of a){'
          ' if(w.kCGWindowOwnerPID!==%d)continue;'
          ' if(w.kCGWindowLayer!==0)continue;'
          ' var area=w.kCGWindowBounds.Width*w.kCGWindowBounds.Height;'
          ' candidates.push({id:w.kCGWindowNumber,area:area,name:String(w.kCGWindowName||"")});}'
          'var exact=wanted===null?[]:candidates.filter(function(w){return w.name===wanted;});'
          'var selected=exact.length?exact[0]:(candidates.sort(function(a,b){return b.area-a.area;})[%d]);'
          'selected?String(selected.id):""') % (expected_title, int(pid), int(fallback_rank))
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


def raise_window(pid):
    """Bring the analyser to the front before a native capture.

    `screencapture -R` photographs a REGION OF THE SCREEN, not a window, so anything overlapping the
    analyser is captured with it. A browser window once landed in a documentation shot complete with its
    URL bar and personal bookmarks — the exact leak CLAUDE.md rule 1 exists to prevent, and one a text
    sweep can never catch. The app raises itself too; this is the belt to that pair of braces.
    """
    subprocess.run(["osascript", "-e",
                    'tell application "System Events" to set frontmost of '
                    f'the first process whose unix id is {int(pid)} to true'],
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
    raise_window(ep.get("pid"))
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


def capture_mcp_setup():
    """M42 UI disclosures, with fake local launchers so a public image contains no personal path."""
    print("MCP setup dialogs")
    stop_capture_app()
    shutil.rmtree(CAPTURE_ROOT, ignore_errors=True)
    HOME.mkdir(parents=True)
    if EXPORT_DIR.exists():
        shutil.rmtree(EXPORT_DIR)
    EXPORT_DIR.mkdir(parents=True)
    stage_mcp_setup_launchers()
    classes = CAPTURE_ROOT / "classes"
    source = REPO / "tools" / "McpSetupDocCapture.java"
    compile_result = subprocess.run(["javac", "-cp", str(CAPTURE_JAR), "-d", str(classes), str(source)],
                                    capture_output=True, text=True)
    names = ["mcp-generic-setup.png", "mcp-claude-code-confirm.png"]
    _attempted.extend(names)
    if compile_result.returncode != 0:
        print(f"  ! could not compile MCP dialog capture: {compile_result.stderr.strip()}")
        _failed.extend(names)
        return
    env = os.environ.copy()
    env["PATH"] = str(CAPTURE_BIN) + os.pathsep + env.get("PATH", "")
    capture_result = subprocess.run(["java", f"-Duser.home={HOME}", "-cp",
                                     str(classes) + os.pathsep + str(CAPTURE_JAR), "McpSetupDocCapture", str(ASSETS)],
                                    capture_output=True, text=True, env=env)
    if capture_result.returncode != 0:
        print(f"  ! MCP dialog capture failed: {capture_result.stderr.strip()}")
    for name in names:
        asset = ASSETS / name
        if capture_result.returncode == 0 and asset.exists() and asset.stat().st_size > 0:
            print(f"  ✓ {name}  ({asset.stat().st_size // 1024} KB, dialog capture)")
        else:
            _failed.append(name)


def capture_template_picker():
    """M19.5's real catalogue chooser, generated under the same isolated home as every docs image."""
    print("template picker")
    stop_capture_app()
    shutil.rmtree(CAPTURE_ROOT, ignore_errors=True)
    HOME.mkdir(parents=True)
    stage_mcp_setup_launchers()
    classes = CAPTURE_ROOT / "classes"
    source = REPO / "tools" / "TemplatePickerDocCapture.java"
    result = subprocess.run(["javac", "-cp", str(CAPTURE_JAR), "-d", str(classes), str(source)],
                            capture_output=True, text=True)
    name = "template-picker.png"
    _attempted.append(name)
    if result.returncode != 0:
        print(f"  ! could not compile template-picker capture: {result.stderr.strip()}")
        _failed.append(name)
        return
    result = subprocess.run(["java", f"-Duser.home={HOME}", "-cp",
                             str(classes) + os.pathsep + str(CAPTURE_JAR),
                             "telamin.fluxtion.audit.analyser.analyser.ui.TemplatePickerDocCapture", str(ASSETS)],
                            capture_output=True, text=True)
    asset = ASSETS / name
    if result.returncode == 0 and asset.exists() and asset.stat().st_size > 0:
        print(f"  ✓ {name}  ({asset.stat().st_size // 1024} KB, dialog capture)")
    else:
        print(f"  ! template-picker capture failed: {result.stderr.strip()}")
        _failed.append(name)


def finish_capture():
    if "--keep" not in sys.argv:
        stop_capture_app()
        shutil.rmtree(EXPORT_DIR, ignore_errors=True)
        shutil.rmtree(CAPTURE_ROOT, ignore_errors=True)
    if _failed:
        print(f"done — {len(_attempted) - len(_failed)} of {len(_attempted)} regenerated; "
              f"{len(_failed)} NOT captured: {', '.join(_failed)}")
        print("  grant Screen Recording and Accessibility permission to this terminal and re-run before committing docs")
        sys.exit(1)
    print(f"done — {len(_attempted)} captures, all native")


def seed(ep):
    """Every capture starts from the same loaded state."""
    act(ep, "source_root", {"add": [str(ROOT)]})
    act(ep, "open", {"processor": PROCESSOR})
    act(ep, "open", {"graphml": str(GRAPHML)})


def capture_tutorial():
    """The M19 tutorial shots — a real downloaded bundle, opened the way the page tells you to.

    Everything here mirrors the page's own instructions rather than a convenient shortcut: the
    PROJECT is opened first and the log second, because that is what the page tells the reader to do
    and what the analyser actually requires (a project switch is a session boundary and ignores a log
    passed with it). Shooting it any other way would photograph a path the tutorial does not teach.
    """
    if not TUTORIAL_LOG.exists():
        sys.exit(f"stage the bundle first: {TUTORIAL_LOG} is missing "
                 f"(see tools/stage-tutorial-bundle.sh)")
    home = str(pathlib.Path.home())
    if str(TUTORIAL_PROJECT).startswith(home):
        sys.exit(f"refusing: {TUTORIAL_PROJECT} is inside {home}, so its path would render an "
                 f"account name into the images — stage it somewhere neutral")

    print("tutorial shots (light)")
    ep = launch("Light")

    # 1. the project as the bundle ships it: profile adopted, nothing typed in
    act(ep, "open", {"project": str(TUTORIAL_PROJECT / ".analyser" / "project.fluxtion-settings")})
    time.sleep(1)
    capture(ep, "tutorial-project-open.png")

    # 2. the exported log + its graph — the pairing verdict is the point of the shot
    act(ep, "open", {"log": str(TUTORIAL_LOG), "graphml": str(TUTORIAL_GRAPHML),
                     "provenance": "audit-analyser-bundle"})
    for _ in range(20):
        time.sleep(0.5)
        if (act(ep, "context").get("context", {}).get("log") or {}).get("records"):
            break
    else:
        sys.exit("the tutorial log did not load")
    act(ep, "goto", {"recordIndex": 0, "reveal": True})
    capture(ep, "tutorial-log-open.png")

    # 3. a PriceEvent cycle read out: which nodes ran, in dispatch order, and what each logged.
    # The index is found by scanning the log itself rather than by asking the app: `read` needs an
    # anchor, and defaulting to record 0 silently shot the EventLogControlEvent — a cycle in which
    # nothing logged, which is the opposite of what this figure is for.
    price = 0
    seen = -1
    for line in TUTORIAL_LOG.read_text().splitlines():
        if line.startswith("eventLogRecord:"):
            seen += 1
        elif "event: PriceEvent" in line:
            price = seen
            break
    act(ep, "goto", {"recordIndex": price, "reveal": True})
    capture(ep, "tutorial-cycle.png")

    # 4. click-to-source: the node line reaching the bundled source, which the profile made possible
    act(ep, "topology", {"select": "rootNode", "source": True})
    capture(ep, "tutorial-source.png")

    finish_capture()


def capture_projects_menu():
    """The project actions after M19.5 — including the live-catalogue template picker entry point."""
    print("project menu (light)")
    ep = launch("Light")
    menu_capture(ep, "File", "projects-file-menu.png")
    finish_capture()


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)

    if "--tutorial" in sys.argv:
        capture_tutorial()
        return

    if "--projects-menu" in sys.argv:
        capture_projects_menu()
        return

    if "--template-picker" in sys.argv:
        capture_template_picker()
        finish_capture()
        return

    if "--mcp" in sys.argv:
        capture_mcp_setup()
        finish_capture()
        return

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
    # M43.6: the AI menu, with a project open so Runbooks…/Domain glossary… are ENABLED — the shot has to
    # show the working state, not the greyed one a reader would take for the feature being unavailable.
    # (Found in review: this call originally sat BEFORE the project relaunch, so the shot showed exactly
    # the greyed state the comment promised to avoid.)
    menu_capture(ep, "AI", "ai-menu.png")
    act(ep, "goto", {"recordIndex": 3, "reveal": True})
    capture(ep, "projects-active.png")
    # Working with AI ▸ runbooks: the Project panel's Project section with the runbook pointer, the glossary and
    # the saved analysis's offer — the human-facing half of context.runbooks / vocabulary / analyses
    capture(ep, "ai-runbooks-panel.png")

    finish_capture()


if __name__ == "__main__":
    main()
