#!/usr/bin/env python3
"""
M48.7 on the BUILT JAR - the shared canvas: `open {posture | record}`, `open {close: "handoff"}` and
`context.handoff`, over the action socket, isolated home. (It was a `handoff` verb for one day; folded into `open`.)

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

            reply = a.act("open", posture="authoring", record=RECORD)
            check("open {posture, record} is accepted - the canvas is written through `open`", reply.get("ok") is True, reply)
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

            gone = a.act("handoff", posture="research")
            check("`handoff` is NOT a verb - it was one for a day and was folded into `open` before it shipped",
                  gone.get("ok") is False and "unknown verb" in json.dumps(gone), gone)

            bad = a.act("open", posture="research", record={"branch": "c", "modes": "0+"})
            check("a malformed record is REFUSED, with the reason",
                  bad.get("ok") is False and "must be a list" in json.dumps(bad), bad)
            check("and the posture sent in the same call was NOT applied - refused whole",
                  posture(a.context()).get("value") == "authoring/deploy", posture(a.context()))

            # review F2: a required SCALAR is typed too - this used to be accepted as branch "{instructions=invented}"
            typed = a.act("open", posture="research", record={"branch": {"instructions": "invented"}, "modes": ["0"]})
            check("a `branch` that is not a string is REFUSED, never stringified into a valid-looking record",
                  typed.get("ok") is False and "'branch' must be a string" in json.dumps(typed), typed)
            h = a.context().get("handoff") or {}
            check("and neither the record nor the posture beside it changed",
                  (h.get("record") or {}).get("branch") == RECORD["branch"]
                  and (h.get("posture") or {}).get("value") == "authoring/deploy", h)

            mixed = a.act("open", posture="research", log=os.path.join(work, "no-such.yaml"))
            check("a canvas write goes ALONE: combined with a log it is refused whole, and names what it carried",
                  mixed.get("ok") is False and "goes ALONE" in json.dumps(mixed) and "log" in json.dumps(mixed), mixed)
            check("so the posture did not change, and nothing started loading",
                  posture(a.context()).get("value") == "authoring/deploy" and "inFlight" not in a.context(), a.context().get("handoff"))

            ignored = a.act("open", posture="authoring", colour="blue")
            check("an undeclared param is never silently honoured: the call is refused and names it",
                  ignored.get("ok") is False and "colour" in json.dumps(ignored), ignored)

            a.act("open", project=project)
            h = a.settled_context().get("handoff") or {}
            check("a project switch is a session boundary: the record is gone", "record" not in h, h)
            check("and the posture is derived again - authoring now, because a project is open",
                  (h.get("posture") or {}).get("source") == "derived"
                  and (h.get("posture") or {}).get("value") == "authoring/deploy", h)

            a.act("open", posture="research", record=RECORD)
            closed = a.act("open", close="handoff")
            h = a.context().get("handoff") or {}
            check("open {close: \"handoff\"} takes both off - the same idiom as open {close: \"project\"}",
                  closed.get("ok") is True and (closed.get("applied") or {}).get("closed") == "handoff"
                  and "record" not in h and (h.get("posture") or {}).get("source") == "derived", closed)
            check("and it closed ONLY the canvas: the project is still open",
                  bool(a.context().get("project")), a.context().get("project"))
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
