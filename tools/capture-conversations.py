#!/usr/bin/env python3
"""Generate docs/site/sample-conversations.md from REAL runs — the transcript counterpart of capture-docs.py.

The asks and the agent's answers are authored below; every tool call and every echo between them is
recorded from the live action socket against the demo set, under the same isolated home capture-docs uses.
A hand-typed transcript is stale the release after it is written and nothing fails; a recorded one is
regenerated with the screenshots (rule 1: generated, not taken — and read before committing).

Usage:  python3 tools/capture-conversations.py      (needs a built jar; macOS screencapture for the shots)
"""
import importlib.util
import json
import pathlib
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("capture_docs", HERE / "capture-docs.py")
cd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cd)

REPO = cd.REPO
PAGE = REPO / "docs/site/sample-conversations.md"
NOAUDIT_GRAPHML = REPO / "src/test/resources/topology/demo-quote-processor-noaudit.graphml"

# ---- rendering ---------------------------------------------------------------------------------

def shorten(v, depth=0):
    """Trim an echo for the page: paths made neutral, long lists cut, long strings cut, depth bounded."""
    if isinstance(v, str):
        for prefix, label in ((str(cd.DEMO_PROJECT), "…/demo-quote-project"), (str(cd.EXPORT_DIR), "<exchange-dir>"),
                              (str(REPO), "…/analyser")):
            v = v.replace(prefix, label)
        return v if len(v) <= 110 else v[:107] + "…"
    if isinstance(v, bool) or v is None or isinstance(v, (int, float)):
        return v
    if isinstance(v, list):
        if depth >= 5:
            return f"[… {len(v)} item(s)]"
        out = [shorten(x, depth + 1) for x in v[:3]]
        if len(v) > 3:
            out.append(f"… (+{len(v) - 3} more)")
        return out
    if isinstance(v, dict):
        if depth >= 5:
            return "{…}"
        return {k: shorten(x, depth + 1) for k, x in v.items()}
    return str(v)


def pick(echo, keys):
    """Keep only the named top-level keys (dotted for one level down) — the ones the prose talks about."""
    if not keys:
        return echo
    out = {}
    for k in keys:
        if "." in k:
            a, b = k.split(".", 1)
            if isinstance(echo.get(a), dict) and b in echo[a]:
                out.setdefault(a, {})[b] = echo[a][b]
        elif k in echo:
            out[k] = echo[k]
    return out


def dump(obj):
    return json.dumps(shorten(obj), ensure_ascii=False, indent=2)


class Transcript:
    def __init__(self, ep):
        self.ep = ep
        self.lines = []
        self.raw = []          # every FULL echo from the run, untrimmed — what `cites` is checked against

    def call(self, verb, params=None, show=None, wait=0.0):
        """Run the verb for real; render the request and the (trimmed) echo. A failure aborts the run —
        a page that showed a failed call as if it had worked would be the lie this tool exists to prevent."""
        res = cd.act(self.ep, verb, params)
        if not res.get("ok"):
            sys.exit(f"{verb} failed during capture: {res.get('error')}")
        if wait:
            time.sleep(wait)
        payload = {k: v for k, v in res.items() if k not in ("ok", "action")}
        # the socket wraps a verb's payload under its own key ({"coverage": {...}}); show the inside
        if len(payload) == 1 and isinstance(next(iter(payload.values())), dict):
            payload = next(iter(payload.values()))
        self.raw.append(json.dumps(payload, ensure_ascii=False, indent=2))
        self.lines.append("```json")
        self.lines.append(f"→ analyser_{verb} {json.dumps(shorten(params or {}), ensure_ascii=False)}")
        self.lines.append(f"← {dump(pick(payload, show))}")
        self.lines.append("```")
        return res

    def context(self, show):
        """`context` is the verb every conversation starts with; render only the keys the prose needs."""
        res = cd.act(self.ep, "context")
        if not res.get("ok"):
            sys.exit("context failed during capture")
        ctx = res.get("context", {})
        self.raw.append(json.dumps(ctx, ensure_ascii=False, indent=2))
        self.lines.append("```json")
        self.lines.append("→ analyser_context {}")
        self.lines.append(f"← {dump(pick(ctx, show))}")
        self.lines.append("```")
        return ctx

    def you(self, text):
        self.lines.append("")
        self.lines.append("> **You:** " + text)
        self.lines.append("")

    def agent(self, text, cites=()):
        """The agent's answer is AUTHORED — it is the point of the page, since it shows how an agent reasons
        over what it got. But that makes it the half the harness cannot regenerate, and therefore the half
        that goes stale silently: recording the echoes from a real run keeps the JSON honest while the prose
        beside it drifts, contradicting the very echo it is reading.

        That is not hypothetical. This page shipped saying "five of the six nodes that can log did" and
        quoting ratio 0.833; M40.2b landed a day later, proved `spreadCalculator` cannot log at all, and the
        echo became 1.0 — the numbers regenerated, the sentence did not.

        So a claim can CITE the run: pass the figures the sentence depends on and the capture fails if the
        live echo no longer contains them. A generated echo makes the JSON true; this makes the prose true.
        """
        for claim in cites:
            if claim not in self.transcript_json():
                sys.exit(f"authored prose cites {claim!r}, which no recorded echo on this page contains "
                         f"any more — the run changed under the sentence. Rewrite the prose, do not "
                         f"weaken this check.")
        self.lines.append("")
        self.lines.append("> **Agent:** " + text)
        self.lines.append("")

    def transcript_json(self):
        """Every FULL echo recorded from the live socket so far — never the authored prose.

        Checked against the untrimmed echoes on purpose. The page's rendering is deliberately trimmed
        (long strings cut, lists capped), so testing the claim against what is VISIBLE would fail for
        sentences that are perfectly true of the run — and a check that cries wolf gets deleted."""
        return "\n".join(self.raw)

    def prose(self, text):
        self.lines.append("")
        self.lines.append(text)
        self.lines.append("")

    def shot(self, name, caption):
        cd.capture(self.ep, name)
        self.lines.append("")
        self.lines.append(f"![{caption}](assets/{name})")
        self.lines.append("")

    def heading(self, text):
        self.lines.append("")
        self.lines.append("## " + text)
        self.lines.append("")


# ---- the conversations --------------------------------------------------------------------------

def main():
    cd.ASSETS.mkdir(parents=True, exist_ok=True)
    ep = cd.launch("Light", project=cd.make_demo_project())
    cd.seed(ep)
    t = Transcript(ep)

    t.lines.append("<!-- GENERATED by tools/capture-conversations.py — edit the script, not this file. -->")
    t.lines.append("# Sample conversations — an LLM, MCP, and the analyser")
    t.prose("Six short conversations on the demo set that ships in the jar. The **asks** and the **agent's answers** "
            "are written by hand; **every tool call and every echo between them was recorded from a real run** by "
            "`tools/capture-conversations.py`, under the same isolated home the screenshots come from — so when a "
            "verb's echo changes, this page changes with it rather than quietly going stale. Each conversation ends "
            "with what the person sees, because the answer is meant to land in the analyser, not in the chat "
            "(*The shared research canvas*).")
    t.prose("The client discovers one tool per verb — `analyser_context`, `analyser_aggregate`, `analyser_read`, "
            "`analyser_flag`, `analyser_coverage`, `analyser_topology`, `analyser_goto`, `analyser_graph`, "
            "`analyser_report`, `analyser_open` … — so nothing below is pasted into a prompt; the agent reads the "
            "schemas and decides. *Connecting an LLM to the analyser* covers the setup.")

    # 1 ---------------------------------------------------------------------------------------------
    t.heading("1 · \"What happened around 09:00?\" — the answer lands as flags, not prose")
    t.you("The quote service looked wrong at 09:00 today. Use the fluxtion-analyser tools: what happened, and mark "
          "the records I should look at.")
    t.context(["log", "graphPairing.graph", "graphPairing.applies", "showing"])
    t.prose("Ten records, and the graph applies to this log — so a node's absence would be evidence, not silence. The "
            "agent starts wide, then narrows.")
    t.call("aggregate", {"groupBy": "dimension"})
    t.call("read", {"recordIndex": 7}, show=["records", "record", "event", "logTime", "nodeLogs"])
    t.call("flag", {"recordIndexes": [6, 7], "note": "live orders reached the risk limit — the breach that follows is routine here, but the spread moved first"})
    t.agent("At 09:00:00.300 a RiskBreachEvent fired: `breachHandler` recorded `breachedOn: ord-2` with two live "
            "orders. The cycle before it (09:00:00.270) is the one that took liveOrders to the limit — I've flagged "
            "both with a note; they're the yellow rows in your table and the *Flagged only* toggle shows just them. "
            "Nothing else in the ten records is unusual.",
            cites=["breachedOn", "ord-2", "riskMonitor"])
    cd.act(ep, "goto", {"recordIndex": 7, "reveal": True})
    t.shot("conv-what-happened.png", "The two flagged records, the note in the detail pane — the agent's answer as things you can click")

    # 2 ---------------------------------------------------------------------------------------------
    t.heading("2 · \"Which nodes never ran?\" — coverage that counts only what could log")
    t.you("Which nodes in this processor never logged anything in this run?")
    t.call("coverage", show=["declared", "covered", "uncovered", "ratio", "uncoveredIds", "uncoveredNodes",
                             "excludedFromDenominator", "excludedNote"])
    t.agent("Every node that *can* log did — five of five. Five other things in the graph were left out of the "
            "denominator on purpose and the echo names each one with its reason: three event classes and the "
            "`QuoteControl` service interface can never write audit output, and `spreadCalculator` cannot either — "
            "its class declares no supertype, so there is no `auditLog` for it to write with. Counting all five "
            "would have reported 50% for a run that logged everything it could.\n>\n"
            "> Read that last exclusion carefully, though, because the echo is careful about it too: "
            "`spreadCalculator` being unable to log is **not** the same as it being fine. It is not observable in "
            "*any* audit log, so this 100% says nothing about whether it ran — proof-of-absence simply does not "
            "reach it. If you need to know that it ran, it needs an audit logger before the next capture.",
            cites=['"declared": 5', '"covered": 5', '"ratio": 1.0', "spreadCalculator", "QuoteControl"])

    # 3 ---------------------------------------------------------------------------------------------
    t.heading("3 · \"Why is this number what it is?\" — one value followed back through the graph")
    t.you("The spread in the 09:00:00.270 cycle is 0.011. Show me where that comes from.")
    t.call("topology", {"select": "quotePublisher", "scope": "routes"}, show=["selected", "scope", "visibleNodes", "totalNodes", "routeBound", "focus"])
    t.call("goto", {"recordIndex": 6, "reveal": True}, show=["recordIndex", "event", "logTime"])
    t.call("topology", {"step": 2}, show=["position", "currentNode", "rowCount", "atEntry", "orderMeaningful"])
    t.agent("`quotePublisher.spread` is computed from `spreadCalculator`, which listens to `priceListener` — the "
            "Topology tab is now focused on exactly those routes, and I've stepped the 09:00:00.270 cycle to the "
            "second node it dispatched — `quotePublisher`, after `orderTracker`. `spreadCalculator` cannot write audit output at "
            "all (conversation 2), so the "
            "0.011 you see is `quotePublisher` reporting a value it was handed, and no audit log will ever let us "
            "trace it further — that is a property of the build, not of this run. Position within the cycle *is* dispatch order here (`orderMeaningful: true`), so the sequence "
            "you're looking at is causal.",
            cites=["quotePublisher", "orderMeaningful"])
    t.shot("conv-why-this-number.png", "The routes into quotePublisher, the cycle stepped to its second node")

    # 5 (before 4, which closes the log) --------------------------------------------------------------
    t.heading("4 · \"Chart it and write it up\" — a graph with its reason, and a report that leaves the session")
    cd.act(ep, "open", {"log": str(cd.SERIES_LOG)})
    for _ in range(20):
        time.sleep(0.5)
        if (cd.act(ep, "context").get("context", {}).get("log") or {}).get("records"):
            break
    t.you("Open the longer run and chart the spread against live orders; then write it up as a report I can send.")
    t.call("graph", {"name": "Spread vs live orders", "series": ["quotePublisher.spread"], "rightAxis": ["orderTracker.live"],
                     "style": "line", "rationale": "the spread widens when orders are live — checking the two move together"},
           show=["graph", "name", "series", "rationale", "points", "rows"])
    t.call("report", {"name": "spread-vs-live", "title": "Spread widens while orders are live",
                      "sections": [{"kind": "narrative", "text": "Every widening of the spread in this run coincides with live orders; the chart shows the two series moving together."},
                                   {"kind": "record", "recordIndex": 12}],
                      "path": "spread-vs-live.pdf"},
           show=["wrote", "report", "path", "sections", "saved", "name"])
    t.agent("The chart is on your Graph tab — spread on the left axis, live orders on the right, captioned with why I "
            "drew it. The report is saved in the project as *spread-vs-live* and rendered to the exchange directory "
            "as `spread-vs-live.pdf`; its header names the log it was written against, so whoever receives it can "
            "check it against the same file.")
    t.shot("conv-chart-and-report.png", "The report on the Reports tab, written against the 726-record series log — the narrative labelled as the author's account, not evidence; the chart it refers to is on the Graph tab")

    # 6 ---------------------------------------------------------------------------------------------
    t.heading("5 · \"Use what the project knows\" — runbooks, glossary, a saved analysis")
    t.you("Is there a standard analysis for this kind of incident, and what does the team do next?")
    t.context(["runbooks", "vocabulary.path", "vocabulary.exists", "analyses", "provenance"])
    t.call("open", {"analysis": "spread breach", "bind": {"log": str(cd.LOG)}}, show=["analysis", "completed", "steps"], wait=2.0)
    t.agent("Yes — the project saves a *spread breach* analysis (\"every breach incident starts the same way: the "
            "spread before it\"). I ran it: it opened the incident log and drew `quotePublisher.spread`. The glossary "
            "says a *breach* here is routine, not an outage. For what happens next the project points at "
            "`ops/restart-quote-service.md`; I read it — confirm liveOrders is 0, restart through the deployment tool, "
            "verify the next log starts within 5 s. I have not run anything. (*Runbooks, glossary and saved analyses "
            "with an AI* has this conversation in full.)")

    # 4 ---------------------------------------------------------------------------------------------
    t.heading("6 · \"Will this processor log at all?\" — an answer with no log to examine")
    t.you("Support say the audit log from the new build is empty. Here's the build's graph — is that expected?")
    cd.act(ep, "open", {"close": "all"})
    time.sleep(1)
    t.call("open", {"graphml": str(NOAUDIT_GRAPHML)}, wait=2.0)
    t.context(["graphPairing.auditLogging", "graphPairing.auditLoggingNote", "graphPairing.graph", "log"])
    t.agent("Expected, and not a quiet system: this build was compiled **without** audit logging — `EventLogManager` is "
            "not on the graph, so the processor writes no audit log at all, however carefully its nodes call "
            "`auditLog.info(…)`. The fix is `addEventAudit()` on the graph builder and a rebuild. I could tell you "
            "that from the graph alone, before any run — there was never going to be a log to read.",
            cites=["not_enabled", "addEventAudit()"])
    t.shot("conv-will-it-log.png", "No log open, the no-audit build's graph loaded — 18 nodes and no EventLogManager; the verdict rides context.graphPairing and the Project panel's Graph section")

    t.prose("---")
    t.prose("*Regenerate with `python3 tools/capture-conversations.py` after `mvn package`. The calls above are the "
            "run's own; if one fails, the script stops rather than writing a page that pretends it worked.*")

    PAGE.write_text("\n".join(t.lines).rstrip() + "\n")
    print(f"wrote {PAGE.relative_to(REPO)} ({len(t.lines)} lines); shots: {len(cd._captured)}")
    if cd._failed:
        sys.exit(f"captures failed: {cd._failed}")


if __name__ == "__main__":
    main()
