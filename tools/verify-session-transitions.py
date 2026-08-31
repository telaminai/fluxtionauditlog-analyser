#!/usr/bin/env python3
"""
Session-transition acceptance, run against the BUILT JAR — M44 §11.

Rule 4 does not unit-test Swing, so the session-boundary rules M35 spent eleven slices establishing
could only ever be checked by a person clicking through menus. This closes most of that gap without
one: the assistant socket's `open {project}` and `open {close: "project"}` go through the *same*
SessionDriver and the *same* SessionBoundary node as the menu items — the only difference is whether a
failure is rendered as a dialog or returned as text.

So what this proves is the decision, end to end, on the artefact a user actually runs:

  A  an explicit switch with a log open closes it, and the echo REPORTS what closed
  B  re-opening the already-active project is a no-op that does not cost you the log
  C  switching to a different project closes again and names the log
  D  a failed switch closes nothing and changes nothing
  E  leaving the project restores your own settings and ends the session

It also asserts the thing only a real run can show: **nothing reaches stdout**. Fluxtion's no-arg
EventLogManager defaults its sink to System.out::println, so a mis-ordered sink attachment prints every
audit record to the console. That failure is invisible to every unit test and obvious here.

  python3 tools/verify-session-transitions.py                 # builds nothing; expects target/*.jar
  python3 tools/verify-session-transitions.py --jar path.jar

What it does NOT cover, and this is the honest boundary: the five entrances that only exist behind a
dialog — ADOPT_FOR_OPEN_LOG (the project offered because a log was just opened), CREATE, FORK,
STARTUP_ACTIVATION, and the import dialog's "open as project". Those reach the same decision node with a
different TransitionKind, so the RULE is covered here; whether each call site passes the right kind is
verified by reading, not by running. ADOPT_FOR_OPEN_LOG is the one worth a manual check, because it is
the exception a reader is most likely to think is a bug.

Runs under an isolated user.home, so it never touches the real configuration or recent-project list.
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

RATE_LIMIT_PAUSE = 0.45          # the socket rate-limits at 10/s; stay under it
STARTUP_TIMEOUT = 40


class Analyser:
    """The built jar, running under an isolated home, talked to over its assistant socket."""

    def __init__(self, jar, home):
        self.jar, self.home = jar, home
        self.proc = self.url = self.token = None
        self.log_path = os.path.join(home, "analyser-stdout.log")

    def __enter__(self):
        with open(self.log_path, "w") as out:
            self.proc = subprocess.Popen(
                ["java", "-Duser.home=" + self.home, "-jar", self.jar, "--rest"],
                stdout=out, stderr=subprocess.STDOUT)
        deadline = time.time() + STARTUP_TIMEOUT
        while time.time() < deadline:
            text = open(self.log_path).read()
            if "X-Analyser-Token:" in text:
                for line in text.splitlines():
                    if "X-Analyser-Token:" in line:
                        self.url = line.split("http://")[1].split()[0]
                        self.url = "http://" + self.url
                        self.token = line.split("X-Analyser-Token:")[1].strip()
                return self
            if self.proc.poll() is not None:
                raise SystemExit("the analyser exited during startup:\n" + text)
            time.sleep(0.5)
        raise SystemExit("the analyser did not publish a REST endpoint within "
                         f"{STARTUP_TIMEOUT}s:\n" + open(self.log_path).read())

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
        try:
            with urllib.request.urlopen(request) as reply:
                return json.load(reply)
        except urllib.error.HTTPError as e:
            return json.load(e)

    def echo(self, reply):
        """The detail map is nested under the STATUS key — `opened` for a switch, `applied` for a close."""
        for key in ("opened", "applied"):
            if isinstance(reply.get(key), dict):
                return reply[key]
        return reply

    def state(self):
        """(open log path or None, whether a project is active, its name)."""
        context = self.act("context")["context"]
        log = context.get("log") or {}
        project = context.get("project") or {}
        return log.get("path"), project.get("active"), project.get("name")

    def stdout(self):
        return open(self.log_path).read()


class Checks:
    def __init__(self):
        self.failed = []

    def __call__(self, description, condition, detail=""):
        print(("  PASS  " if condition else "  FAIL  ") + description
              + (("   -> " + str(detail)[:110]) if detail else ""))
        if not condition:
            self.failed.append(description)


def make_project(root, name):
    """A minimal but genuine project profile — the canonical path the app looks for."""
    directory = os.path.join(root, name, ".analyser")
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, "project.fluxtion-settings"), "w") as f:
        f.write("sourceRoot.0=%s\n" % os.path.join(root, name, "src"))
    return os.path.join(root, name)


def main():
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", default=None, help="default: the newest target/*.jar")
    parser.add_argument("--log", default=os.path.join(here, "src/test/resources/sample.yml"))
    args = parser.parse_args()

    jar = args.jar
    if jar is None:
        candidates = [j for j in glob.glob(os.path.join(here, "target", "*.jar"))
                      if "original-" not in os.path.basename(j)]
        if not candidates:
            raise SystemExit("no jar in target/ — run `mvn package` first")
        jar = max(candidates, key=os.path.getmtime)
    if not os.path.isfile(args.log):
        raise SystemExit("no audit log at " + args.log)

    workspace = tempfile.mkdtemp(prefix="verify-session-")
    home = os.path.join(workspace, "home")
    os.makedirs(home)
    A = make_project(workspace, "projA")
    B = make_project(workspace, "projB")
    missing = os.path.join(workspace, "does-not-exist")
    check = Checks()

    print("jar:  " + jar)
    print("log:  " + args.log)
    print("home: " + home + "  (isolated — the real configuration is never touched)\n")

    try:
        with Analyser(jar, home) as app:
            app.act("open", close="project")
            app.act("open", close="all")

            print("[A] a log is open, then an EXPLICIT switch — the profile is the session boundary (M35.5)")
            app.act("open", log=args.log)
            before, _, _ = app.state()
            reply = app.act("open", project=A)
            after, active, name = app.state()
            closed = (app.echo(reply).get("closed") or {}).get("log")
            check("precondition: a log was open", before is not None, before)
            check("the switch succeeded", reply.get("ok") is True, reply.get("error"))
            check("THE LOG ACTUALLY CLOSED", after is None, "log now %s" % after)
            check("the new project is in force", active is True and name == "projA", name)
            check("the echo REPORTS what closed, rather than predicting it", closed == before, closed)

            print("\n[B] re-opening the ALREADY ACTIVE project — a no-op must not cost you the log")
            app.act("open", log=args.log)
            before, _, _ = app.state()
            reply = app.act("open", project=A)
            after, _, _ = app.state()
            check("precondition: a log is open again", before is not None, before)
            check("reported as already active", app.echo(reply).get("alreadyActive") is True,
                  app.echo(reply).get("note"))
            check("THE LOG SURVIVED", after == before, "before=%s after=%s" % (before, after))
            check("nothing was reported closed", not (app.echo(reply).get("closed") or {}))

            print("\n[C] switching to a DIFFERENT project closes again, and names the log")
            reply = app.act("open", project=B)
            after, _, name = app.state()
            check("the new project is in force", name == "projB", name)
            check("the log closed", after is None, after)
            check("the echo named it", (app.echo(reply).get("closed") or {}).get("log") == before)

            print("\n[D] a FAILED switch must close nothing and change nothing")
            app.act("open", log=args.log)
            before, _, was = app.state()
            reply = app.act("open", project=missing)
            after, _, name = app.state()
            check("precondition: a log is open under project %s" % was, before is not None)
            check("the switch was refused", reply.get("ok") is False, reply.get("error"))
            check("THE LOG SURVIVED A BAD PATH", after == before,
                  "before=%s after=%s" % (before, after))
            check("the active project is unchanged", name == was, "%s -> %s" % (was, name))

            print("\n[E] leaving the project restores your own settings and ends the session")
            before, _, _ = app.state()
            reply = app.act("open", close="project")
            after, active, _ = app.state()
            check("precondition: a log is open", before is not None, before)
            check("the close succeeded", reply.get("ok") is True, reply.get("error"))
            check("no project is active", active is not True, active)
            check("the log closed with it", after is None, after)
            check("the echo named it", (app.echo(reply).get("closed") or {}).get("log") == before)

            print("\n[F] the processor's audit log never reaches the console")
            noise = [line for line in app.stdout().splitlines()
                     if any(marker in line for marker in
                            ("eventLogRecord", "nodeLogs", "updating event log config"))]
            check("no Fluxtion audit record on stdout, across every transition above",
                  not noise, "\n".join(noise[:3]))
    finally:
        shutil.rmtree(workspace, ignore_errors=True)

    print()
    if check.failed:
        print("%d FAILURE(S):\n  - %s" % (len(check.failed), "\n  - ".join(check.failed)))
        return 1
    print("ALL PASS — the session-boundary rules hold on the built jar.")
    print("Still unverified here: ADOPT_FOR_OPEN_LOG, CREATE, FORK, STARTUP_ACTIVATION and the import")
    print("dialog. They reach the same decision with a different kind; the wiring is read, not run.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
