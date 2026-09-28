#!/usr/bin/env python3
"""Evidence bundle v1 — a driver that runs the whole demo against REAL analysers, and checks it (spec r3 §4, §5, §7).

It is the executable reference for the two skills, docs/skills/common/capture-evidence-bundle and
open-evidence-bundle: each step below is a numbered step there. It runs two analysers under two isolated homes on
two different paths, so "the recipient" really has none of the sender's settings, roots or files:

  sender     /tmp/fluxtion-evidence-demo/sender     a DEMO project; opens the DEMO log and graph, saves a chart,
                                                    a chart with an external CSV (to be LEFT OUT), a report and
                                                    a three-step walk; then CAPTURES a bundle (the skill)
  recipient  /tmp/fluxtion-evidence-demo/recipient  a cold home with no source roots; receives only the .fexp,
                                                    unpacks it, opens it and plays the walk (the other skill)

Both homes are isolated (rule 1): nothing on screen comes from this machine's own settings. Every check prints PASS
or FAIL and the run exits non-zero on any FAIL; timings for the recipient's steps are written to the output JSON.

Usage:
  python3 tools/evidence-bundle-demo.py                 # the whole demo; both analysers are stopped at the end
  python3 tools/evidence-bundle-demo.py --keep          # leave the recipient's analyser open on the walk, to look at
  python3 tools/evidence-bundle-demo.py --open X.fexp   # recipient only: open a bundle someone sent you
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

REPO = pathlib.Path(__file__).resolve().parent.parent
FIXTURES = REPO / "src/test/resources/topology"
LOG = FIXTURES / "demo-quote-audit.yaml"
GRAPHML = FIXTURES / "demo-quote-processor.graphml"
# Neutral paths (rule 1): they are printed, shown in the title bar, and written into the results.
ROOT = pathlib.Path("/tmp/fluxtion-evidence-demo")
SENDER = ROOT / "sender"
RECIPIENT = ROOT / "recipient"

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
    """The analyser's headless flags. Exit code, stdout and stderr, exactly as a skill sees them."""
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


# ---- capture-evidence-bundle, step by step ------------------------------------------------------------------------

def capture_refusal(ctx):
    """Skill step 2: the reason this session cannot be captured coherently, or None. Pure: tested headless.

    Each reason is one of spec §4.1's, named so a person reading it knows what to do. An ABSENT log.identity is not a
    refusal: it means no identity check has run yet, and the bundle's own sha256 of the copied bytes is then the only
    statement of what was read; the skill says so.
    """
    log = ctx.get("log") or {}
    if not log.get("path"):
        return "no log is open: open the log you are investigating first"
    if ctx.get("inFlight"):
        return f"a load is pending ({ctx['inFlight']}): wait for it to land, then capture"
    ident = (log.get("identity") or {}).get("state")
    if ident in ("replacement", "unverified"):
        return f"the log file is not established to be the one that was read (identity: {ident}); reopen it first"
    fresh = log.get("freshness") or {}
    if fresh.get("state") == "changed-on-disk":
        return "the log file changed on disk since it was read; reopen it first"
    members = fresh.get("members") or []
    if len(members) != 1 or (members[0].get("loaded") or {}).get("directory"):
        return "the log is not one plain file (a rolled set, a directory or a remote store): v1 bundles one file"
    if not pathlib.Path(log["path"]).is_file():
        return "the log's store is not a plain file on this machine"
    return None


def capture(an, out):
    """The capture skill: one coherent transaction (spec §4.1). Returns (ok, lines) and leaves no bundle on refusal."""
    lines = []
    ctx = an.context()
    resume_follow = bool((ctx.get("log") or {}).get("following"))
    if resume_follow:                                                            # step 1: pause Follow
        an.must("open", {"follow": False})
        ctx = an.context()
    try:
        why = capture_refusal(ctx)                                               # step 2: refuse, by name
        if why:
            return False, [f"REFUSED: {why}"]
        for _ in range(40):                    # a project's edits reach its FILE after a debounce: wait, never guess
            if not (ctx.get("project") or {}).get("unsavedEdits"):
                break
            time.sleep(0.25)
            ctx = an.context()
        else:
            return False, ["REFUSED: the project has edits that are not yet written to its file (a failed write?): "
                           "see the status bar, then capture again"]
        generation = ctx["log"]["generation"]                                    # step 3: record the generation
        folder = out.with_suffix(".folder")
        shutil.rmtree(folder, ignore_errors=True)
        (folder / "log").mkdir(parents=True)
        log = pathlib.Path(ctx["log"]["path"])
        shutil.copyfile(log, folder / "log" / log.name)
        graph = (ctx.get("graphPairing") or {}).get("graphPath")
        if graph:
            (folder / "graph").mkdir()
            shutil.copyfile(graph, folder / "graph" / pathlib.Path(graph).name)
        settings = (ctx.get("project") or {}).get("settings") or str(an.home / ".fluxtion-analyser" / "config")
        (folder / "profile").mkdir()
        code, so, se = cli(an.home, "--bundle-profile", settings, folder / "profile" / "project.fluxtion-settings")
        lines += so.splitlines()
        if code != 0:
            return False, [f"REFUSED: the profile cannot travel: {se.strip()}"]
        code, so, se = cli(an.home, "--pack", folder, out)
        lines += so.splitlines()
        if code != 0:
            return False, [f"REFUSED: {se.strip()}"]
        after = an.context()                                                     # step 4: re-read; moved -> delete
        if (after.get("log") or {}).get("generation") != generation:
            out.unlink(missing_ok=True)
            return False, ["REFUSED: another log was opened while the bundle was being written; it was deleted"]
        if not (ctx["log"].get("identity") or {}).get("state"):
            lines.append("note: no identity check had run on the open log; the bundle's sha256 is the statement of "
                         "the bytes it carries")
        return True, lines
    finally:
        shutil.rmtree(out.with_suffix(".folder"), ignore_errors=True)
        if resume_follow:
            an.act("open", {"follow": True})


# ---- open-evidence-bundle, step by step ---------------------------------------------------------------------------

def open_bundle(an, bundle, work):
    """The open skill (spec §5). Returns the working copy, or exits on a refusal: nothing is opened from a bad bundle."""
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


def play(an, name):
    """Skill step 4: play the walk, one step at a time; each step's showing as the analyser states it."""
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
            an.act("screenshot", {"path": f"walk-step-{step}.png"})
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
    never = ROOT / "never.fexp"                 # EP-A1, live: a real session with no log refuses, and writes nothing
    ok, lines = capture(an, never)
    check(not ok and lines[0].startswith("REFUSED: no log is open") and not never.exists(),
          "EP-A1 a live capture with no log open refuses by name and leaves no bundle", "; ".join(lines))
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--keep", action="store_true", help="leave the recipient's analyser open on the walk")
    ap.add_argument("--open", type=pathlib.Path, help="recipient only: open this bundle")
    ap.add_argument("--out", type=pathlib.Path, default=ROOT / "results.json")
    ap.add_argument("--default-window", action="store_true", help="do not pin the window size (a fresh home's default)")
    a = ap.parse_args()
    if a.default_window:
        WINDOW.clear()
    if a.open:
        a.open = a.open.resolve()
    shutil.rmtree(ROOT, ignore_errors=True)
    ROOT.mkdir(parents=True)

    bundle = a.open
    if bundle is None:
        print("sender: a DEMO investigation, then capture-evidence-bundle")
        an = sender_session()
        try:
            bundle = RECIPIENT / "inbox" / "breach-0900.fexp"
            bundle.parent.mkdir(parents=True)
            ok, lines = capture(an, bundle)
            print("  " + "\n  ".join(lines))
            check(ok and bundle.exists(), "capture wrote a bundle", "; ".join(lines))
            check(any(l.startswith("left out: chart '" + EXTERNAL_CHART) for l in lines),
                  "EP-A9 the chart with an external series is left out and named")
            RESULTS["identity"] = next((l.split(": ", 1)[1] for l in lines if l.startswith("identity: ")), None)
        finally:
            an.stop()
        if not bundle.exists():
            return finish(a)

    print("recipient: a cold home on another path — open-evidence-bundle")
    home = RECIPIENT / "home"
    home.mkdir(parents=True, exist_ok=True)
    received = sha(bundle)
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
        shutil.copyfile(own_profile, ROOT / "own-profile.baseline")
        shutil.copyfile(home / ".fluxtion-analyser" / "config", ROOT / "recipient-config.baseline")
        config_before = props(home / ".fluxtion-analyser" / "config")
        code, so, se = cli(home, "--verify", bundle)
        check(code == 0, "EP-A2/A4 the received bundle verifies", se.strip())
        check(all(("limit: " + l) in so for l in ("unsigned:", "no replay:")) or ("limit: unsigned" in so and "limit: no replay" in so),
              "EP-A10 verify states both limits")
        check("authenticated" not in (so + se).replace("does not authenticate", "").lower(),
              "EP-A10 nothing says the sender is authenticated")
        t_flow = time.monotonic()
        copy = open_bundle(an, bundle, RECIPIENT / "work")
        ctx = an.context()
        check((ctx.get("project") or {}).get("settings", "").startswith(str(copy)),
              "EP-A6 the project is the bundle's working copy", str((ctx.get("project") or {}).get("settings")))
        check((ctx.get("log") or {}).get("records") == 10, "EP-A6 the log loaded: 10 records")
        check((ctx.get("graphPairing") or {}).get("applies") is True, "EP-A6 the graph loaded and applies")
        names = [w.get("name") for w in ((ctx.get("walks") or {}).get("saved") or [])]
        check(WALK in names, "EP-A6 the walk arrived", str(ctx.get("walks")))
        check(not [g for g in ctx.get("savedGraphs") or [] if (g.get("name") if isinstance(g, dict) else g) == EXTERNAL_CHART],
              "EP-A9 the external-series chart is not in the recipient's session")
        check(not ctx.get("source", {}).get("roots"), "EP-A12 the recipient has no source roots")
        shown = play(an, WALK)
        RESULTS["timings"]["received_to_walk_end_s"] = round(time.monotonic() - t_flow, 2)
        RESULTS["walk"] = shown
        RESULTS["screenshots"] = sorted(str(p) for p in shots.glob("*.png"))
        check(len(shown) == 3 and all(s.get("phase") == "SHOWN" for s in shown), "EP-A8 all three steps shown",
              json.dumps(shown)[:400])
        caveats = [i for i, s in enumerate(shown, 1) if "not been re-checked" in (s.get("reason") or "")]
        check(len(caveats) == 1, "M69.F3 the not-re-checked caveat is stated once in the walk, stepping by the verb",
              f"stated on steps {caveats}")
        for i, s in enumerate(shown, 1):
            targets = s.get("targets") or []
            check(targets and all(t.get("state") == "CURRENT" and t.get("available") for t in targets),
                  f"EP-A8 step {i}: every target current and lit", json.dumps(targets)[:300])
        if not a.keep:
            an.must("walk", {"end": True})
        check(sha(bundle) == received, "EP-A5 the received bundle is byte-identical after unpack, open and the walk")
        code2, so2, _ = cli(home, "--unpack", bundle, "--into", RECIPIENT / "work")
        check(code2 == 0 and sha(bundle) == received, "EP-A5 a second unpack: a fresh copy, the bundle unchanged")
        an.must("open", {"project": str(own_profile)})                         # back to the recipient's own work
        an.must("open", {"close": "project"})
        check(sha(own_profile) == own_profile_before, "EP-A7 the recipient's own project profile is byte-identical")
        keys = config_diff(config_before, props(home / ".fluxtion-analyser" / "config"))
        RESULTS["recipientConfigKeysChanged"] = keys
        print("  recipient machine-tier settings changed (EP-A7, listed, not judged): " + (", ".join(keys) or "none"))
    finally:
        if a.keep:
            print(f"\nthe recipient's analyser is still open (pid {an.proc.pid}), on the walk '{WALK}'")
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
