#!/usr/bin/env python3
"""
M46's analyser-owned defects A1-A5, asserted against the BUILT JAR over the action socket.

The M46 spec (docs/specs/spec-authoring-toolchain-repair.md, Acceptance) asks for two regressions that
no unit test can be, because both are about the running application and a caller with nobody at the
screen:

  A1  "a pairing verdict is never emitted before the log it describes has loaded; the same call twice
      returns the same answer. Regression test drives open {log, graphml} on a VIRGIN instance."
  A2  "no modal is reachable from any REST-driven path - asserted by a test that opens a log INSIDE A
      PROJECT DIRECTORY over the socket, which is the case verify-session-transitions.py never covers."

A1 and A2 were found fixed when this was written (2026-09-17: M44.3 made the open a session-processor
decision, and the project offer has been data on the socket path since M35.7). This script is what
keeps them fixed. A3-A5 were still live and are fixed alongside it; each check names the defect.

Every call has a hard timeout, so a hang is a FAIL, not a stuck script - A2 was a hang.

  python3 tools/verify-m46-agent-api.py                # expects target/*.jar (mvn package)
  python3 tools/verify-m46-agent-api.py --jar path.jar

Runs under an isolated user.home in a temp directory; touches no real configuration. Opens a window.
"""

import argparse
import glob
import json
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEMO = os.path.join(REPO, "src", "main", "resources", "demo")
RATE_LIMIT_PAUSE = 0.45          # the socket rate-limits at 10/s; stay under it
STARTUP_TIMEOUT = 40
CALL_TIMEOUT = 15                # A2 presented as "every later verb returns nothing"
MIXED_ROUNDS = 12                # A2's second route followed MIXED coverage/topology calls


class Analyser:
    def __init__(self, jar, home, tag):
        self.jar, self.home = jar, home
        self.out = os.path.join(home, f"analyser-stdout-{tag}.log")
        self.proc = self.url = self.token = None

    def __enter__(self):
        with open(self.out, "w") as out:
            self.proc = subprocess.Popen(
                ["java", "-Duser.home=" + self.home, "-jar", self.jar, "--rest"],
                stdout=out, stderr=subprocess.STDOUT)
        deadline = time.time() + STARTUP_TIMEOUT
        while time.time() < deadline:
            text = open(self.out).read()
            for line in text.splitlines():
                if "X-Analyser-Token:" in line:
                    self.url = "http://" + line.split("http://")[1].split()[0]
                    self.token = line.split("X-Analyser-Token:")[1].strip()
                    return self
            if self.proc.poll() is not None:
                raise SystemExit("the analyser exited during startup:\n" + text)
            time.sleep(0.4)
        raise SystemExit("no REST endpoint within %ss:\n%s" % (STARTUP_TIMEOUT, open(self.out).read()))

    def __exit__(self, *exc):
        if self.proc and self.proc.poll() is None:
            self.proc.send_signal(signal.SIGTERM)
            try:
                self.proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.proc.kill()

    def act(self, action, **params):
        time.sleep(RATE_LIMIT_PAUSE)
        request = urllib.request.Request(
            self.url + "/action", method="POST",
            data=json.dumps({"action": action, "params": params}).encode(),
            headers={"X-Analyser-Token": self.token, "Content-Type": "application/json"})
        started = time.time()
        try:
            with urllib.request.urlopen(request, timeout=CALL_TIMEOUT) as reply:
                return json.load(reply)
        except urllib.error.HTTPError as e:
            return json.load(e)
        except Exception as e:                       # a timeout IS the finding
            return {"HUNG": "%s after %.1fs" % (type(e).__name__, time.time() - started)}

    def context(self):
        return self.act("context").get("context") or {}

    def settled_context(self):
        """`context` once no load is in flight - A1's whole point is what is said BEFORE this."""
        for _ in range(20):
            c = self.context()
            if not c.get("inFlight"):
                return c
        return c


class Checks:
    def __init__(self):
        self.failed = []

    def __call__(self, description, condition, detail=""):
        print(("  PASS  " if condition else "  FAIL  ") + description
              + (("   -> " + str(detail)[:150]) if detail and not condition else ""))
        if not condition:
            self.failed.append(description)


def body(reply, *keys):
    for key in keys:
        if isinstance(reply.get(key), dict):
            return reply[key]
    return reply


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jar")
    args = ap.parse_args()
    jars = [args.jar] if args.jar else sorted(glob.glob(os.path.join(REPO, "target", "fluxtion-auditlog-analyser-*.jar")))
    jars = [j for j in jars if j and not j.endswith("-sources.jar") and "original" not in os.path.basename(j)]
    if not jars:
        raise SystemExit("no jar - run `mvn package` first, or pass --jar")
    jar = jars[-1]

    work = tempfile.mkdtemp(prefix="verify-m46-")
    home = os.path.join(work, "home")
    os.makedirs(home)
    log = shutil.copy(os.path.join(DEMO, "demo-quote-audit.yaml"), work)
    graph = shutil.copy(os.path.join(DEMO, "demo-quote-processor.graphml"), work)
    project = os.path.join(work, "proj")
    os.makedirs(os.path.join(project, ".analyser"))
    os.makedirs(os.path.join(project, "logs"))
    with open(os.path.join(project, ".analyser", "project.fluxtion-settings"), "w") as f:
        f.write("share.version=1\nsourceRoot.count=0\n")
    project_log = shutil.copy(os.path.join(DEMO, "demo-quote-audit.yaml"),
                              os.path.join(project, "logs", "in-project.yaml"))

    check = Checks()
    try:
        with Analyser(jar, home, "first") as a:
            print("A1 - the first verdict after open {log, graphml} on a VIRGIN instance")
            opened = body(a.act("open", log=log, graphml=graph), "opened")
            g = opened.get("graphml") or {}
            early_verdict = any(k in g for k in ("verdict", "appliesToOpenLog", "declaredByGraph", "loggedNodes"))
            check("while the log is loading the echo says PENDING, or the load had already landed",
                  ("pending" in str(g.get("pairing", "")).lower() and not early_verdict) or not opened.get("logLoading"),
                  g)
            settled = a.settled_context()
            first, second = settled.get("graphPairing"), a.context().get("graphPairing")
            check("the same question twice returns the same pairing", first == second, (first, second))
            check("the settled pairing says the graph applies - never 'no log is open', never '0 of 5'",
                  (first or {}).get("applies") is True, first)
            c1, c2 = body(a.act("coverage"), "coverage"), body(a.act("coverage"), "coverage")
            figures = lambda c: {k: c.get(k) for k in ("declared", "covered", "uncovered", "ratio", "recordsScanned")}
            check("coverage twice returns the same figures", figures(c1) == figures(c2), (figures(c1), figures(c2)))

            print("A5 - topology's position while records are open and none is selected")
            t = body(a.act("topology"), "topology")
            check("an unbound cursor does not claim there are no records",
                  t.get("position") != "no records" and t.get("recordsOpen") == 10, t.get("position"))
            a.act("goto", recordIndex=3)
            t = body(a.act("topology"), "topology")
            check("after goto it reports a real position", "entry" in str(t.get("position")) or "row" in str(t.get("position")),
                  t.get("position"))

            print("A3 - what the open {graphml} echo calls the graph's size")
            g = body(a.act("open", graphml=graph), "opened").get("graphml") or {}
            check("`graphNodes` is the graph's size and `authoredNodes` the authored count - two keys",
                  g.get("graphNodes") == 20 and g.get("authoredNodes") == 10, g)
            check("no bare `nodes` key is left to be read as either", "nodes" not in g, list(g))

            print("A2 - route 2: mixed coverage/topology calls")
            hung = [v for _ in range(MIXED_ROUNDS) for v in ("coverage", "topology") if "HUNG" in a.act(v)]
            check("%d interleaved calls, none hangs" % (MIXED_ROUNDS * 2), not hung, hung)

            print("A2 - route 1: a log INSIDE a project directory, over the socket")
            reply = a.act("open", log=project_log)
            check("the open returns rather than waiting behind a project-offer modal", "HUNG" not in reply, reply)
            after = a.settled_context()
            check("the socket still answers afterwards", bool(after), after)
            check("the project offer arrived as DATA in context", bool(after.get("projectOffer")), list(after))
            check("context says an agent opened it", (after.get("log") or {}).get("openedBy") == "action socket",
                  (after.get("log") or {}).get("openedBy"))

            print("open {analysis} - the recall runs off the EDT; its decision must still reach the driver ON it")
            # Found 2026-09-17 re-running capture-conversations.py: released in 1.13.x, every recall ran
            # its steps and then failed with a SessionDriver thread-confinement violation. An analysis
            # that does not exist takes the same path, so this needs no saved analysis to prove it.
            reply = a.act("open", analysis="no-such-analysis")
            check("a recall answers about the ANALYSIS, never with a thread-confinement violation",
                  "confined to the thread" not in json.dumps(reply) and "HUNG" not in reply, reply)

        print("A4 - a FRESH instance never implicitly restores the global last log")
        with Analyser(jar, home, "second") as b:
            fresh = b.settled_context()
            # Project-starter journey supersedes automatic startup restore. This run never
            # accepted the project offer; a global remembered path grants no restore permission.
            # verify-session-restart.py separately checks normal quit + explicit project restore.
            check("the global remembered log stays unopened", not fresh.get("log"), fresh.get("log"))
            check("an unopened log has no invented attribution", not (fresh.get("log") or {}).get("openedBy"))
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    if check.failed:
        print("FAILED: %d check(s)" % len(check.failed))
        for f in check.failed:
            print("  - " + f)
        sys.exit(1)
    print("all M46 agent-API checks pass")


if __name__ == "__main__":
    main()
