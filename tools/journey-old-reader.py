#!/usr/bin/env python3
"""OA-5 (spec §6.1, §8): what an analyser that PREDATES conversation journeys does with a journey bundle.

    python3 tools/journey-old-reader.py <released-analyser.jar>        # e.g. the 1.27.0 release asset

Under an isolated home, with DEMO data only: verify and unpack the committed journey bundle with the OLD jar, open it
the way a recipient does, play every step of the walk, then save the project and look at what the old version wrote.
The claim under test is the catalogue page's: an older analyser opens the evidence and plays the walk WITHOUT its
dialogue (degraded viewing), and does not fail. Prints one line per check and exits non-zero on any failure.
"""
import importlib.util
import json
import pathlib
import shutil
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent
spec = importlib.util.spec_from_file_location("capture_docs", HERE / "capture-docs.py")
cd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cd)

BUNDLE = REPO / "docs/site/assets/journeys/find-the-first-recorded-breach.fexp"
WALK = "DEMO first breach"
ROOT = pathlib.Path("/tmp/analyser-journey-old-reader")
FAILED = []


def check(ok, what, detail=""):
    print(("PASS " if ok else "FAIL ") + what + (f" — {detail}" if detail else ""))
    if not ok:
        FAILED.append(what)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    old = pathlib.Path(sys.argv[1]).resolve()
    shutil.rmtree(ROOT, ignore_errors=True)
    home = ROOT / "home"
    (home / ".fluxtion-analyser").mkdir(parents=True)
    print("old reader: " + old.name)

    v = subprocess.run(["java", f"-Duser.home={home}", "-jar", str(old), "--verify", str(BUNDLE)], capture_output=True, text=True)
    check(v.returncode == 0, "the old reader verifies the journey bundle", v.stdout.strip().splitlines()[0] if v.stdout else v.stderr)
    work = ROOT / "received"
    u = subprocess.run(["java", f"-Duser.home={home}", "-jar", str(old), "--unpack", str(BUNDLE), "--into", str(work)],
                       capture_output=True, text=True)
    check(u.returncode == 0, "the old reader unpacks it")
    copy = pathlib.Path(next(l for l in u.stdout.splitlines() if l.startswith("working copy: "))
                        .split("working copy: ", 1)[1].split("  (")[0])
    profile = copy / "profile" / "project.fluxtion-settings"
    before = profile.read_text()

    (home / ".fluxtion-analyser" / "config").write_text("assistant.rest=true\nwindowX=60\nwindowY=60\nwindowW=1680\nwindowH=1050\n")
    endpoint = home / ".fluxtion-analyser" / "rest-endpoint"
    proc = subprocess.Popen(["java", f"-Duser.home={home}", "-jar", str(old)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(40):
            time.sleep(1)
            if endpoint.exists():
                time.sleep(2)
                break
        ep = json.loads(endpoint.read_text())
        check(cd.act(ep, "open", {"project": str(profile)}).get("ok"), "the old reader opens the bundled project")
        log = next((copy / "log").iterdir())
        graph = next((copy / "graph").iterdir())
        check(cd.act(ep, "open", {"log": str(log), "graphml": str(graph)}).get("ok"), "and its log and graph")
        time.sleep(4)
        ctx = (cd.act(ep, "context").get("context") or {})
        saved = [w for w in ((ctx.get("walks") or {}).get("saved") or []) if w.get("name") == WALK]
        check(len(saved) == 1, "the old reader lists the journey as an ordinary walk", json.dumps(saved)[:200])
        steps = saved[0].get("steps", 0) if saved else 0
        phases = []
        for n in range(1, steps + 1):
            r = cd.act(ep, "walk", {"name": WALK, "play": True, "step": n})
            showing = {}
            for _ in range(40):
                showing = ((cd.act(ep, "context").get("context") or {}).get("walks") or {}).get("showing") or {}
                if showing.get("step") == n and showing.get("phase") not in (None, "PREPARING"):
                    break
                time.sleep(0.25)
            phases.append(showing.get("phase"))
            check(bool(r.get("ok")), f"step {n} plays in the old reader", showing.get("phase", "?"))
        cd.act(ep, "walk", {"end": True})
        check(all(p in ("SHOWN", "PARTLY_SHOWN") for p in phases) and len(phases) == steps,
              "every step's evidence is shown by the old reader (the walk plays; its dialogue is not shown)", str(phases))
        # a save by the old version: does the dialogue survive a round trip through a reader that does not know it?
        cd.act(ep, "walk", {"name": WALK, "rename": WALK + " (seen by 1.27)"})
        cd.act(ep, "walk", {"name": WALK + " (seen by 1.27)", "rename": WALK})
        time.sleep(2)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
    after = profile.read_text()
    check(after != before, "the old reader DID rewrite the profile (a rename there and back), so the next check means something")
    kept = [l for l in before.splitlines() if ".conv." in l or ".through=" in l or l.split("=")[0].endswith(".id")]
    check(all(l in after.splitlines() for l in kept),
          "after the old reader's own saves, every dialogue and binding key it did not understand is still in the profile",
          f"{len(kept)} keys kept")
    shutil.rmtree(ROOT, ignore_errors=True)
    print("RESULT: " + ("all checks passed" if not FAILED else f"{len(FAILED)} FAILED: {FAILED}"))
    sys.exit(1 if FAILED else 0)


if __name__ == "__main__":
    main()
