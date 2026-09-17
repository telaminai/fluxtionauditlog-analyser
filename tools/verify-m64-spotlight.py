#!/usr/bin/env python3
"""
M64 on the BUILT JAR - the `spotlight` verb, over the action socket, isolated home.

The unit tests prove the vocabulary parses and that resolution is pure; the display test proves the
screenshot's pixels. What only this shows is the thing a tutor actually does, end to end, on the artefact a
user runs: every target FAMILY lights on a real window (or refuses with a reason a tutor can act on), a
target that is not on screen is REVEALED first, `context` reports what is lit and nothing after it goes
out, and each view-changing verb puts it out.

Reuses the harness in verify-m46-agent-api.py: a hard per-call timeout, so a hang is a FAIL.

  python3 tools/verify-m64-spotlight.py        # expects target/*.jar (mvn package). Opens a window.
"""
import glob
import importlib.util
import json
import os
import shutil
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEMO = os.path.join(REPO, "src", "main", "resources", "demo")
spec = importlib.util.spec_from_file_location("verify_m46", os.path.join(REPO, "tools", "verify-m46-agent-api.py"))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)


def items(reply):
    """What a spotlight reply (or context) says is lit: [{n, target, caption?, bounds?}]."""
    return ((reply or {}).get("spotlight") or {}).get("lit") or []


def lit(reply):
    return reply.get("ok") is True and len(items(reply)) > 0


def area(reply):
    """The SMALLEST cut-out in the reply - so a set passes only if every member has an area."""
    boxes = [(i.get("bounds") or {}) for i in items(reply)]
    return min((b.get("width", 0) * b.get("height", 0) for b in boxes), default=0)


def targets(context):
    return [i.get("target") for i in items(context)]


def main():
    jars = sorted(j for j in glob.glob(os.path.join(REPO, "target", "fluxtion-auditlog-analyser-*.jar"))
                  if "original" not in os.path.basename(j) and not j.endswith("-sources.jar"))
    if not jars:
        raise SystemExit("no jar - run `mvn package` first")
    work = tempfile.mkdtemp(prefix="verify-m64-")
    home = os.path.join(work, "home")
    exchange = os.path.join(work, "exchange")
    os.makedirs(os.path.join(home, ".fluxtion-analyser"))
    os.makedirs(exchange)
    with open(os.path.join(home, ".fluxtion-analyser", "config"), "w") as f:   # the screenshot verb is opt-in (B1)
        f.write("assistant.exports=true\nassistant.exportDir=%s\n" % exchange)
    log = shutil.copy(os.path.join(DEMO, "demo-quote-series.yaml"), work)
    graph = shutil.copy(os.path.join(DEMO, "demo-quote-processor.graphml"), work)

    check = v.Checks()
    try:
        with v.Analyser(jars[-1], home, "spotlight") as a:
            print("with NOTHING open - a tutor's first 'look here' happens on a fresh start")
            for target in ("status", "toolbar:open", "tab:topology", "project"):
                r = a.act("spotlight", target=target, caption="look here first")
                check("%s lights with nothing open" % target, lit(r) and area(r) > 0, r)
            ctx = a.context()
            check("context reports the live spotlight, on a fresh start (above the early return)",
                  targets(ctx) == ["project"], ctx.get("spotlight"))
            r = a.act("spotlight", target="topology:node:priceListener")
            check("a node with no topology open is REFUSED with a reason to act on, never lit on nothing",
                  r.get("ok") is False and "open {graphml}" in json.dumps(r), r)
            check("and the refused request left the standing spotlight alone",
                  targets(a.context()) == ["project"], a.context().get("spotlight"))

            print("an unknown target names the vocabulary")
            r = a.act("spotlight", target="topolgy:node:x")
            check("a misspelt target is an error that teaches the vocabulary",
                  r.get("ok") is False and "topology:node:<instanceId>" in json.dumps(r), r)
            r = a.act("spotlight", target="status", caption="line one\nline two")
            check("a two-line caption is refused - the sentence belongs in the chat", r.get("ok") is False, r)

            print("with a log, a graph and a chart open - every family")
            a.act("open", log=log, graphml=graph)
            a.settled_context()
            check("opening put the spotlight out (a view-changing verb)", "spotlight" not in a.context(), a.context().get("spotlight"))
            drawn = a.act("graph", name="Spread", series=["quotePublisher.spread"],
                          notes=[{"recordIndex": 15, "text": "the crossing"}])
            check("(setup) the chart drew its series - a spotlight test that lit an EMPTY chart proves nothing",
                  "quotePublisher.spread" in json.dumps(drawn.get("applied", {}).get("resolved")), drawn)
            a.act("goto", recordIndex=15)
            for target in ("tab:summary", "tab:source", "tab:graph", "tab:reports", "tab:assistant", "tab:topology",
                           "records", "records:row:15", "detail", "detail:node:quotePublisher", "topology",
                           "topology:node:priceListener", "topology:verdict",
                           "graph", "graph:series:quotePublisher.spread", "graph:note:1",
                           "project", "project:log", "project:graph", "project:processors", "project:roots",
                           "toolbar:open", "toolbar:flag", "toolbar:explain", "toolbar:follow", "status"):
                r = a.act("spotlight", target=target)
                check("%s lights" % target, lit(r) and area(r) > 0, r)

            print("a series is named EXACTLY - never bound to the first label that starts with it (review F1)")
            r = a.act("spotlight", target="graph:series:quote")
            check("graph:series:quote is REFUSED - it is a prefix of quotePublisher.spread, not a series",
                  r.get("ok") is False and "not on the selected graph" in json.dumps(r), r)
            a.act("graph", name="Spread", series=["quotePublisher.spread", "quotePublisher.liveOrders"])
            r = a.act("spotlight", target="graph:series:quotePublisher")
            check("with TWO series sharing a prefix, the prefix lights neither", r.get("ok") is False, r)
            r = a.act("spotlight", target="graph:series:quotePublisher.liveOrders")
            check("and each is still lit by its own exact label", lit(r) and targets(r) == ["graph:series:quotePublisher.liveOrders"], r)

            print("a target that is off screen is REVEALED first")
            a.act("spotlight", target="tab:summary")
            r = a.act("spotlight", target="topology:node:quotePublisher", caption="every price ends up here")
            check("a node on a tab that was not showing is revealed and lit", lit(r) and area(r) > 0, r)
            a.act("filter", text="RiskBreachEvent")
            r = a.act("spotlight", target="records:row:3")
            check("a FILTERED-OUT row is revealed through the goto path, then lit", lit(r) and area(r) > 0, r)

            print("the tutor's own check: context, then a screenshot")
            a.act("spotlight", target="topology:node:priceListener", caption="this node sees every price first")
            spot = items(a.context())
            check("context names the target and carries the caption", targets(a.context()) == ["topology:node:priceListener"]
                  and "sees every price" in str(spot[0].get("caption")), spot)
            shot = a.act("screenshot", path=os.path.join(exchange, "lit.png"))
            check("a screenshot can be taken WITHOUT putting the spotlight out",
                  shot.get("ok") is True and targets(a.context()) == ["topology:node:priceListener"], shot)

            print("each view-changing verb puts it out")
            for verb, params in (("filter", {"text": ""}), ("goto", {"recordIndex": 1}),
                                 ("graph", {"name": "Spread", "series": ["quotePublisher.spread"]}),
                                 ("topology", {"select": "priceListener"}), ("open", {"graphml": graph})):
                a.act("spotlight", target="status")
                a.act(verb, **params)
                check("%s puts the spotlight out" % verb, "spotlight" not in a.settled_context(), a.context().get("spotlight"))

            print("SEVERAL at once - a finding is usually a relation (M64.6)")
            a.act("goto", recordIndex=15)
            r = a.act("spotlight", targets=[
                {"target": "topology:node:priceListener", "caption": "every price arrives here"},
                {"target": "topology:node:quotePublisher", "caption": "and leaves here"},
                {"target": "topology:verdict", "caption": "the verdict"},
                "records:row:15"])
            check("four things on screen together light as ONE call, every one with an area",
                  lit(r) and len(items(r)) == 4 and area(r) > 0, r)
            check("they are numbered in the order asked, in the echo and in context",
                  [i.get("n") for i in items(r)] == [1, 2, 3, 4] and [i.get("n") for i in items(a.context())] == [1, 2, 3, 4], r)
            check("a callout is optional per target", "caption" not in items(r)[3] and "caption" in items(r)[0], items(r))
            shot = a.act("screenshot", path=os.path.join(exchange, "four.png"))
            check("a screenshot leaves all four lit", shot.get("ok") is True and len(items(a.context())) == 4, shot)

            r = a.act("spotlight", targets=["status", "topology:node:ghost"])
            check("a set with ONE bad member is refused whole, naming the member",
                  r.get("ok") is False and "'topology:node:ghost': " in json.dumps(r), r)
            check("and the four that were lit are still lit", len(items(a.context())) == 4, a.context().get("spotlight"))

            r = a.act("spotlight", targets=["topology:node:priceListener", "graph:note:1"])
            check("two things on DIFFERENT tabs are refused: they cannot be on screen together",
                  r.get("ok") is False and "cannot be on screen at the same time" in json.dumps(r), r)
            check("and the refusal says which standing spotlights its reveal took off screen",
                  "went out" in json.dumps(r), r)

            a.act("spotlight", target="status", caption="one")
            r = a.act("spotlight", target="toolbar:flag", caption="two", add=True)
            check("{add: true} keeps what is lit and numbers the new one after it",
                  [(i.get("n"), i.get("target")) for i in items(r)] == [(1, "status"), (2, "toolbar:flag")], r)
            r = a.act("spotlight", target="status", caption="one, reworded", add=True)
            check("adding a target ALREADY lit re-lights it in place - same number, never twice",
                  [(i.get("n"), i.get("caption")) for i in items(r)] == [(1, "one, reworded"), (2, "two")], r)
            r = a.act("spotlight", target="status")
            check("without add, a call REPLACES the set", targets(r) == ["status"], r)
            a.act("spotlight", target="toolbar:flag", add=True)
            a.act("spotlight", target="toolbar:open", add=True)
            r = a.act("spotlight", clear=True, target="toolbar:flag")
            check("{clear: true, target} puts out exactly that one, and the others KEEP their numbers",
                  r.get("ok") is True and [(i.get("n"), i.get("target")) for i in items(r)]
                  == [(1, "status"), (3, "toolbar:open")], r)

            print("stable numbers: putting out the HIGHEST does not free its number (review F1)")
            a.act("spotlight", targets=["status", "detail"])
            a.act("spotlight", clear=True, target="detail")
            r = a.act("spotlight", target="records", add=True)
            check("status=1, detail=2, detail out, records added -> records is 3, NOT a reused 2",
                  [(i.get("n"), i.get("target")) for i in items(r)] == [(1, "status"), (3, "records")], r)

            print("a call that is WRONG touches nothing - judged whole before any row is revealed (review F2)")
            a.act("filter", text="nothing-matches-review-probe")
            a.act("spotlight", targets=["status", "toolbar:flag"])
            scope = lambda c: (c.get("filter"), c.get("selection"), c.get("spotlight"))
            before = scope(a.context())
            r = a.act("spotlight", targets=["records:row:15", "not-a-target"])
            check("a misspelt member beside a row is refused", r.get("ok") is False and "not-a-target" in json.dumps(r), r)
            check("and the filter, the selection and the standing spotlights are exactly as they were",
                  scope(a.context()) == before and "nothing-matches-review-probe" in json.dumps(before), (before, scope(a.context())))
            a.act("spotlight", targets=["status", "toolbar:open", "toolbar:flag", "toolbar:explain", "toolbar:follow", "records"])
            before = scope(a.context())
            r = a.act("spotlight", target="records:row:10", add=True)
            check("a SEVENTH that is a row is refused with the bound", r.get("ok") is False and "at most 6" in json.dumps(r), r)
            check("and it did not clear the filter or select record 10 on its way to being refused",
                  scope(a.context()) == before, (before, scope(a.context())))
            r = a.act("spotlight", target="records:row:99999")
            check("a row this log does NOT HAVE is refused, naming the range (goto would have clamped it to the last record)",
                  r.get("ok") is False and "there is no record 99999" in json.dumps(r), r)
            check("and it neither cleared the filter nor selected the last record",
                  scope(a.context()) == before, (before, scope(a.context())))
            r = a.act("spotlight", target="records:row:15")
            check("control: a VALID row is still revealed through goto's path - the filter is relaxed for it",
                  lit(r) and "nothing-matches-review-probe" not in json.dumps(a.context().get("filter")), a.context().get("filter"))
            a.act("filter", text="")

            seven = ["status", "toolbar:open", "toolbar:flag", "toolbar:explain", "toolbar:follow", "records", "detail"]
            r = a.act("spotlight", targets=seven)
            check("a seventh is refused with the bound, not silently dropped",
                  r.get("ok") is False and "at most 6" in json.dumps(r), r)
            a.act("spotlight", targets=seven[:6])
            r = a.act("spotlight", target=seven[6], add=True)
            check("and add cannot take the lit set past the bound either",
                  r.get("ok") is False and "at most 6" in json.dumps(r) and len(items(a.context())) == 6, r)
            r = a.act("spotlight", targets=["status"], caption="whose?")
            check("a top-level caption beside a list is refused - it would belong to none of them", r.get("ok") is False, r)
            r = a.act("spotlight", targets=[{"target": "status", "colour": "red"}])
            check("an unknown field in an entry is refused, never ignored", r.get("ok") is False and "colour" in json.dumps(r), r)

            a.act("spotlight", targets=["status", "records"])
            a.act("goto", recordIndex=2)
            check("a view-changing verb puts ALL of them out", "spotlight" not in a.settled_context(), a.context().get("spotlight"))

            print("the point-at-the-fault skill's WORKED EXAMPLE, call for call - a skill's numbers must not rot (M64.8)")
            a.act("filter", text="")
            r = a.act("series", expr="riskMonitor.liveOrders", crossings={"above": 1})
            res = r.get("result") or {}
            events = (res.get("crossings") or {}).get("aboveEvents") or []
            check("series finds ONE crossing above 1: record 15, value 2, over 160 points",
                  res.get("points") == 160 and [(e.get("recordIndex"), e.get("value")) for e in events] == [(15, 2.0)], r)
            r = a.act("read", recordIndex=15, count=1, fields=["riskMonitor.*"])
            vals = ((r.get("result") or {}).get("records") or [{}])[0].get("values") or {}
            check("and the record bears the claim out: liveOrders 2, limit 2",
                  vals.get("riskMonitor.liveOrders") == "2" and vals.get("riskMonitor.limit") == "2", vals)
            r = a.act("spotlight", targets=[
                {"target": "records:row:15", "caption": "liveOrders 2 = limit 2 - first time it is reached"},
                {"target": "topology:node:riskMonitor", "caption": "the node that reports it"}])
            check("the evidence lights as ONE numbered call: the record and the node that reported it",
                  lit(r) and [(i.get("n"), i.get("target")) for i in items(r)]
                  == [(1, "records:row:15"), (2, "topology:node:riskMonitor")], r)
            r = a.act("flag", recordIndexes=[15], note="live orders reached the risk limit (2 = 2)",
                      fix="riskMonitor.limit - is 2 the intended cap?")
            check("and flagging it (note + fix) leaves the spotlights lit - flag changes no view",
                  r.get("ok") is True and len(items(a.context())) == 2, r)
            r = a.act("series", expr="riskMonitor.liveOrders", crossings={"above": 99})
            res = r.get("result") or {}
            check("the SAME runbook finding nothing: no crossings above 99, max 6 - and then it lights nothing",
                  ((res.get("crossings") or {}).get("aboveEvents")) == [] and (res.get("stats") or {}).get("max") == 6.0, r)
            a.act("spotlight", clear=True)

            print("a label must name exactly ONE series (re-review R4)")
            csv = os.path.join(exchange, "x.csv")
            with open(csv, "w") as f:
                f.write("time,value\n1750000000000,1\n1750000001000,2\n")
            ext = {"path": csv, "label": "x", "time": "time", "timeFormat": "epochMillis", "zone": "UTC", "value": "value"}
            a.act("graph", name="Duplicates", series=["quotePublisher.spread"], external=[ext, ext])
            r = a.act("spotlight", target="graph:series:x")
            check("the SAME external given twice makes two identical legend rows - the label lights NEITHER, and says there are two",
                  r.get("ok") is False and "2 series" in json.dumps(r), r)
            a.act("graph", name="Collision", series=["quotePublisher.spread"],
                  exprs=[{"label": "x  (external)", "expr": "quotePublisher.spread"}], external=[ext])
            r = a.act("spotlight", target="graph:series:x  (external)")
            check("a FORMULA labelled with the legend's own suffix collides with the external series - refused too",
                  r.get("ok") is False and "2 series" in json.dumps(r), r)
            r = a.act("spotlight", target="graph:series:quotePublisher.spread")
            check("and the unambiguous series on that same graph still lights", lit(r), r)
            # LAST on purpose: these draw NEW chart tabs, and re-issuing an existing named graph does not re-select its
            # tab — a spotlight addresses the SELECTED chart only — so anything after this would be looking at "Collision".

            a.act("spotlight", target="status")
            r = a.act("spotlight", clear=True)
            check("{clear: true} puts it out and context reports nothing after", r.get("ok") is True
                  and "spotlight" not in a.context(), r)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    if check.failed:
        print("FAILED: %d check(s)" % len(check.failed))
        for f in check.failed:
            print("  - " + f)
        sys.exit(1)
    print("all M64 spotlight checks pass")


if __name__ == "__main__":
    main()
