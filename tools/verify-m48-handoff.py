#!/usr/bin/env python3
"""
M48.7 on the BUILT JAR - the `handoff` verb and `context.handoff`, over the action socket, isolated home.

What only a real run can show: that the section is served on a FRESH START (it has to sit above
context()'s early return - twice in this project a correctly-put fact was invisible to agents because it
sat below it), that a write from the socket is attributed to the socket, that a malformed record is
refused WHOLE - the posture sent in the same call is not applied - and that a project switch, being a
session boundary, clears what was placed.

Reuses the harness in verify-m46-agent-api.py: a hard per-call timeout, so a hang is a FAIL.

  python3 tools/verify-m48-handoff.py        # expects target/*.jar (mvn package). Opens a window.
"""
import glob
import importlib.util
import json
import os
import shutil
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location("verify_m46", os.path.join(REPO, "tools", "verify-m46-agent-api.py"))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)

# the selector's own example record (spec-authoring-mode-selector: The handoff contract)
RECORD = {"branch": "catalogue", "modes": ["0+", "2/3"], "skills": [None, "fluxtion-node-authoring"],
          "resolved_figures": ["adjusted", "alert"], "authoring_required": ["netPosition"],
          "selection_candidates": {}}


def main():
    jars = sorted(j for j in glob.glob(os.path.join(REPO, "target", "fluxtion-auditlog-analyser-*.jar"))
                  if "original" not in os.path.basename(j) and not j.endswith("-sources.jar"))
    if not jars:
        raise SystemExit("no jar - run `mvn package` first")
    work = tempfile.mkdtemp(prefix="verify-m48-")
    home = os.path.join(work, "home")
    os.makedirs(home)
    project = os.path.join(work, "proj")
    os.makedirs(os.path.join(project, ".analyser"))
    with open(os.path.join(project, ".analyser", "project.fluxtion-settings"), "w") as f:
        f.write("share.version=1\nsourceRoot.count=0\n")

    check = v.Checks()
    posture = lambda ctx: (ctx.get("handoff") or {}).get("posture") or {}
    try:
        with v.Analyser(jars[-1], home, "handoff") as a:
            p = posture(a.context())
            check("fresh start: context.handoff is served, and the posture is DERIVED and says it is a guess",
                  p.get("source") == "derived" and "guess" in str(p.get("note")), p)
            check("with no project open the guess is research/support", p.get("value") == "research/support", p)

            reply = a.act("handoff", posture="authoring", record=RECORD)
            check("handoff {posture, record} is accepted", reply.get("ok") is True, reply)
            ctx = a.context()
            p, rec = posture(ctx), (ctx.get("handoff") or {}).get("record") or {}
            check("context reads back the SET posture, attributed to the action socket",
                  p.get("value") == "authoring/deploy" and p.get("source") == "set"
                  and p.get("setBy") == "action socket", p)
            check("and says what the derivation would have been, since they disagree",
                  p.get("derivedWouldBe") == "research/support", p)
            check("the record is there - null skill, authoring gap and attribution intact",
                  rec.get("modes") == ["0+", "2/3"] and rec.get("skills") == [None, "fluxtion-node-authoring"]
                  and rec.get("authoringRequired") == ["netPosition"] and rec.get("setBy") == "action socket", rec)

            bad = a.act("handoff", posture="research", record={"branch": "c", "modes": "0+"})
            check("a malformed record is REFUSED, with the reason",
                  bad.get("ok") is False and "must be a list" in json.dumps(bad), bad)
            check("and the posture sent in the same call was NOT applied - refused whole",
                  posture(a.context()).get("value") == "authoring/deploy", posture(a.context()))

            ignored = a.act("handoff", posture="authoring", colour="blue")
            check("an undeclared param is named as ignored (M26.4)", "colour" in json.dumps(ignored), ignored)

            a.act("open", project=project)
            h = a.settled_context().get("handoff") or {}
            check("a project switch is a session boundary: the record is gone", "record" not in h, h)
            check("and the posture is derived again - authoring now, because a project is open",
                  (h.get("posture") or {}).get("source") == "derived"
                  and (h.get("posture") or {}).get("value") == "authoring/deploy", h)

            a.act("handoff", posture="research")
            a.act("handoff", clear="all")
            check("clear undoes it", posture(a.context()).get("source") == "derived", posture(a.context()))
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    if check.failed:
        print("FAILED: %d check(s)" % len(check.failed))
        for f in check.failed:
            print("  - " + f)
        sys.exit(1)
    print("all M48.7 handoff checks pass")


if __name__ == "__main__":
    main()
