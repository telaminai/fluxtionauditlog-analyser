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
CONFIG = pathlib.Path.home() / ".fluxtion-analyser" / "config"
ENDPOINT = pathlib.Path.home() / ".fluxtion-analyser" / "rest-endpoint"
# the app writes here; this script copies out of it. Cleared each run so "never overwrite" is satisfied
# by construction rather than by hoping the names are fresh.
EXPORT_DIR = pathlib.Path(tempfile.gettempdir()) / "analyser-doc-capture"


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


def launch(theme):
    subprocess.run(["pkill", "-f", "fluxtion-auditlog-analyser"], check=False)
    time.sleep(1)
    ENDPOINT.unlink(missing_ok=True)
    # a fresh view every run: a remembered zoom or a stale topology would make the images irreproducible
    # a fresh export directory per launch: the guard refuses to overwrite, and regenerating the same
    # nine filenames is the entire job
    if EXPORT_DIR.exists():
        shutil.rmtree(EXPORT_DIR)
    EXPORT_DIR.mkdir(parents=True)
    set_config(**{"assistant.rest": "true", "theme": theme, "topologyZoom": "0",
                  "topologySpacing": "100", "topologyTextSize": "11",
                  "topologyOrientation": "TOP_DOWN", "topologySyncSource": "true",
                  "eventFilterCollapsed": "false",
                  "assistant.exports": "true", "assistant.exportDir": str(EXPORT_DIR)})
    subprocess.Popen(["java", "-jar", str(jar()), str(LOG)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        time.sleep(1)
        if ENDPOINT.exists():
            time.sleep(2)
            return json.loads(ENDPOINT.read_text())
    sys.exit("the app did not publish a REST endpoint — is the transport enabled?")


def act(ep, verb, params=None):
    body = json.dumps({"v": 1, "action": verb, "params": params or {}}).encode()
    req = urllib.request.Request(ep["url"] + "/action", data=body, method="POST",
                                 headers={"Content-Type": "application/json",
                                          "X-Analyser-Token": ep["token"]})
    with urllib.request.urlopen(req, timeout=30) as r:
        out = json.loads(r.read())
    if not out.get("ok"):
        print(f"  ! {verb} failed: {out.get('error')}")
    return out


def capture(ep, name):
    """Native window capture — the painted fallback cannot draw the title bar."""
    # a relative name lands inside the export directory; unique per call because the guard never
    # overwrites, and this script regenerates the same asset names every run
    scratch_name = f"{len(_captured):02d}-{name}"
    res = act(ep, "screenshot", {"path": scratch_name})
    if not res.get("ok"):
        print(f"  ! {name}: {res.get('error')}")
        return False
    _captured.append(scratch_name)
    painted = EXPORT_DIR / scratch_name

    b = res["wrote"]["windowBounds"]
    target = ASSETS / name
    subprocess.run(["screencapture", "-x", "-R",
                    f"{b['x']},{b['y']},{b['width']},{b['height']}", str(target)], check=False)
    if target.exists():
        print(f"  ✓ {name}  ({target.stat().st_size // 1024} KB)")
        return True
    # no Screen Recording permission: fall back to the app painting itself, minus the title bar
    shutil.copy(painted, target)
    print(f"  ~ {name}  (painted fallback — no native capture)")
    return True


_captured = []


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

    # the front page: the whole tool at work — records, the logical detail, and the graph of the cycle
    act(ep, "goto", {"recordIndex": 5, "reveal": True})
    act(ep, "topology", {"select": "quotePublisher", "scope": "neighbours"})
    capture(ep, "screenshot-light.png")

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

    if "--keep" not in sys.argv:
        subprocess.run(["pkill", "-f", "fluxtion-auditlog-analyser"], check=False)
        shutil.rmtree(EXPORT_DIR, ignore_errors=True)
    print(f"done — {len(_captured)} captures")


if __name__ == "__main__":
    main()
