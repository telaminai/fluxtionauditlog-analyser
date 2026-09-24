#!/usr/bin/env python3
"""M68.1 end-to-end: the coverage, pairing and scope verdicts, driven through the BUILT jar's action socket.

The unit suite proves the logic; this proves the public path a held-out client actually used — `open`,
`coverage`, `context` — against the committed recovery-packet graph that client was misled about.

Every log here is CONSTRUCTED and says so in its records. None is a replay of the 2026-09-24 session.

    mvn -q package -DskipTests && python3 tools/verify-m68-1-coverage.py

Runs under an isolated user.home in a temp directory; touches no real configuration. Opens a window.
Exits non-zero on the first failed check.

WRONG-RESULT WITNESS: the last scenario plants a foreign id and requires the warning to APPEAR. A check that
only ever asserts "no warning" would pass against an analyser that had stopped warning at all.
"""
import glob
import os
import shutil
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from importlib import import_module

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRAPH = os.path.join(REPO, "docs", "handoff", "evidence", "spring-g14-recovery-2026-09-24", "MyProcessor.graphml")
FAILED = []


def check(name, ok, detail=""):
    print(("  PASS " if ok else "  FAIL ") + name + ("" if ok else "\n       " + str(detail)[:600]))
    if not ok:
        FAILED.append(name)


def constructed_log(path, records):
    t = 1000
    with open(path, "w") as out:
        for ids in records:
            out.write("---\neventLogRecord: \n")
            out.write(f"    eventTime: {t}\n    logTime: {t + 1}\n    groupingId: null\n")
            out.write("    event: PriceUpdate\n")
            out.write("    eventToString: CONSTRUCTED for M68.1, not a replay of any session\n")
            out.write("    thread: constructed\n    nodeLogs: \n")
            for node_id in ids:
                out.write(f"        - {node_id}: {{ v: 1}}\n")
            out.write(f"    endTime: {t + 2}\n")
            t += 10
        out.write("---\n")


def settle(a):
    for _ in range(30):
        c = a.context()
        pairing = c.get("graphPairing") or {}
        if not pairing.get("loading") and c.get("log"):
            return c
        time.sleep(0.5)
    return a.context()


def append_record(path, ids, t=900000):
    """Append one constructed record to a followed log, terminated so the reader indexes it."""
    with open(path, "a") as out:
        out.write("eventLogRecord: \n")
        out.write(f"    eventTime: {t}\n    logTime: {t + 1}\n    groupingId: null\n")
        out.write("    event: PriceUpdate\n")
        out.write("    eventToString: CONSTRUCTED for M68.1, appended under Follow\n")
        out.write("    thread: constructed\n    nodeLogs: \n")
        for node_id in ids:
            out.write(f"        - {node_id}: {{ v: 1}}\n")
        out.write(f"    endTime: {t + 2}\n---\n")


def wait_records(a, n, seconds=12):
    """Poll context until the followed store holds n records."""
    deadline = time.time() + seconds
    ctx = a.context()
    while time.time() < deadline:
        ctx = a.context()
        if (ctx.get("log") or {}).get("records") == n:
            return ctx
        time.sleep(0.5)
    return ctx


def open_in_order(a, log, order):
    """Open the log and the graph in one of the three orders an agent or a person can use (review R2).
    The script used to open everything combined, which is why it could not see that two orders published an
    unscoped sample."""
    a.act("open", close="all")
    if order == "combined":
        a.act("open", log=log, graphml=GRAPH)
    elif order == "graph first":
        a.act("open", graphml=GRAPH)
        time.sleep(1.5)
        a.act("open", log=log)
    else:
        a.act("open", log=log)
        settle(a)
        a.act("open", graphml=GRAPH)
    ctx = settle(a)
    time.sleep(1.5)            # review O6: the combined-open graph drop lands after the reply; wait past it
    return a.context()


def open_pair(a, log):
    a.act("open", close="all")
    reply = a.act("open", log=log, graphml=GRAPH)
    if not reply.get("ok"):
        print("  open failed:", reply)
    return settle(a)


def main():
    # an explicit jar may be given, so the same checks can be run against an OLDER build to prove they
    # catch the defect this slice fixes (the seen-red run)
    jars = [sys.argv[1]] if len(sys.argv) > 1 else [
        j for j in glob.glob(os.path.join(REPO, "target", "fluxtion-auditlog-analyser-*.jar"))
        if not j.endswith("-original.jar") and "sources" not in j]
    if not jars:
        raise SystemExit("build the jar first: mvn -q package -DskipTests")
    Analyser = import_module("verify-m46-agent-api").Analyser
    home = tempfile.mkdtemp(prefix="m68-1-home-")
    work = tempfile.mkdtemp(prefix="m68-1-logs-")
    exchange = tempfile.mkdtemp(prefix="m68-1-exchange-")
    # the report export needs assistant file exchange; enabled only inside this isolated home
    os.makedirs(os.path.join(home, ".fluxtion-analyser"), exist_ok=True)
    with open(os.path.join(home, ".fluxtion-analyser", "config"), "w") as cfg:
        cfg.write(f"assistant.exports=true\nassistant.exportDir={exchange}\n")
    try:
        with Analyser(jars[0], home, "m68-1") as a:
            print("1. the packet graph with its three declared authored nodes")
            log = os.path.join(work, "three-authored.yaml")
            constructed_log(log, [["checked", "child"], ["rootNode"]])
            ctx = open_pair(a, log)
            cov = a.act("coverage").get("coverage") or {}
            check("declared 3", cov.get("declared") == 3, cov)
            check("covered 3", cov.get("covered") == 3, cov)
            check("ratio 1.0", cov.get("ratio") == 1.0, cov)
            check("authorship declared by the graph", cov.get("authorshipBasis") == "declared", cov)
            check("no out-of-topology warning", "warning" not in cov, cov.get("warning"))
            said = str(cov).lower() + str(ctx.get("graphPairing")).lower()
            check("no build-provenance claim on coverage or context", "different build" not in said, said)
            pairing = ctx.get("graphPairing") or {}
            check("context: membership established", pairing.get("membershipEstablished") is True, pairing)
            check("context: every observed id declared", pairing.get("everyObservedIdDeclared") is True, pairing)
            check("context: applies is labelled a retention policy",
                  "retention policy" in str(pairing.get("appliesMeans")), pairing)

            print("2. a declared framework node that writes")
            log = os.path.join(work, "framework-writes.yaml")
            constructed_log(log, [["child", "serviceRegistry"]])
            open_pair(a, log)
            cov = a.act("coverage").get("coverage") or {}
            check("no warning for a declared framework node", "warning" not in cov, cov.get("warning"))
            check("population unchanged at 3", cov.get("declared") == 3, cov)
            fw = cov.get("frameworkNodesNotScored") or {}
            check("framework node disclosed outside the denominator", "serviceRegistry" in (fw.get("ids") or []), cov)

            print("3. no node output at all")
            log = os.path.join(work, "no-output.yaml")
            constructed_log(log, [[]])
            ctx = open_pair(a, log)
            pairing = ctx.get("graphPairing") or {}
            check("context: membership NOT established", pairing.get("membershipEstablished") is False, pairing)
            reply = a.act("coverage")
            membership = ((reply.get("coverage") or {}).get("membership") or {})
            check("coverage: membership NOT established",
                  membership.get("established") is False or not reply.get("ok"), reply)

            print("4. WRONG-RESULT WITNESS — a foreign id must still warn")
            # Three of four ids declared, so the graph is retained and coverage runs. A half-foreign log is
            # NOT used here: opened together with this graph, the combined open reports success and the
            # graph is then no longer loaded (observed 2026-09-24), which is M68.4's whole-or-refused defect,
            # not this slice's — and a witness that silently stops exercising the warning is worthless.
            log = os.path.join(work, "foreign.yaml")
            constructed_log(log, [["child", "rootNode", "checked", "notInTheGraph"]])
            ctx = open_pair(a, log)
            pairing = ctx.get("graphPairing") or {}
            check("the graph is retained on a partial match", pairing.get("applies") is True, pairing)
            check("…and context says not every observed id is declared",
                  pairing.get("everyObservedIdDeclared") is False, pairing)
            cov = a.act("coverage").get("coverage") or {}
            check("the warning appears", "warning" in cov, cov)
            check("it names the foreign id", "notInTheGraph" in str(cov.get("loggedButNotInTopology")), cov)
            check("it states the fact without a build conclusion",
                  "build" not in str(cov.get("warning")).lower().replace("does not establish a build", ""), cov)

            print("5. acceptance 3 in every open order: a 600-record log, the only foreign id in record 600")
            log = os.path.join(work, "a3-600.yaml")
            ids = ["checked", "child", "rootNode"]
            constructed_log(log, [[ids[i % 3]] for i in range(599)] + [["foreignAfter500"]])
            for order in ("combined", "graph first", "log first"):
                ctx = open_in_order(a, log, order)
                gp = ctx.get("graphPairing") or {}
                check(f"{order}: the graph is still open", gp.get("graph") is not None, gp)
                check(f"{order}: the pairing says it is a sample", gp.get("pairingSampled") is True, gp)
                check(f"{order}: and names its scope", gp.get("pairingScope") == "first 500 of 600 records", gp)
                check(f"{order}: the verdict sentence carries the scope",
                      "judged on the first 500 of 600 records" in str(gp.get("verdict")), gp.get("verdict"))
                reply = a.act("coverage")
                cov = reply.get("coverage") or {}
                check(f"{order}: whole-log coverage finds the id the sample could not see",
                      "foreignAfter500" in str(cov.get("loggedButNotInTopology")), cov)
                check(f"{order}: and its reply says it qualified the published pairing",
                      "supersedes the sampled pairing" in str(cov.get("qualifiedPublishedPairing")), cov)
                q = (a.context().get("graphPairing") or {}).get("qualifiedBy") or {}
                check(f"{order}: context now carries the qualification", q.get("supersedesSample") is True, q)
                check(f"{order}: naming the foreign id", "foreignAfter500" in (q.get("notDeclared") or []), q)

            print("7. N1 — a Follow append must not leave a whole-log verdict describing the old log")
            log = os.path.join(work, "n1-follow.yaml")
            constructed_log(log, [[ids[i % 3]] for i in range(600)])
            open_in_order(a, log, "combined")
            a.act("coverage")
            q = (a.context().get("graphPairing") or {}).get("qualifiedBy") or {}
            check("before the append, coverage confirms the whole log", "confirms" in str(q.get("note")), q)
            a.act("open", follow=True)
            append_record(log, ["lateForeign"])
            ctx = wait_records(a, 601)
            gp = ctx.get("graphPairing") or {}
            q = gp.get("qualifiedBy") or {}
            check("the followed store now holds 601 records", (ctx.get("log") or {}).get("records") == 601, ctx.get("log"))
            check("the qualification no longer claims to confirm the whole log",
                  not q or ("confirms the sampled pairing for the whole log" not in str(q.get("note"))), q)
            check("and says the log has grown since coverage compared it", not q or q.get("stale") is True, q)
            check("the published pairing's scope counts the appended record",
                  gp.get("pairingScope") == "first 500 of 601 records", gp.get("pairingScope"))
            reply = a.act("coverage")
            q2 = (a.context().get("graphPairing") or {}).get("qualifiedBy") or {}
            check("a fresh coverage finds the appended foreign id",
                  "lateForeign" in (q2.get("notDeclared") or []), q2)
            check("and is not stale", q2.get("stale") is not True, q2)
            note = str((reply.get("coverage") or {}).get("claimNote"))
            check("the coverage claim's own scope counts the appended record too (the session's copy)",
                  "of 600 records" not in note and "of 601 records" in note, note)
            a.act("open", follow=False)

            print("8. N2 — a narrower comparison must not erase a wider one, in either order")
            log = os.path.join(work, "n2-a3.yaml")
            constructed_log(log, [[ids[i % 3]] for i in range(599)] + [["foreignAfter500"]])
            open_in_order(a, log, "combined")
            a.act("coverage")
            a.act("filter", **{"from": 1001, "to": 1011})
            reply = a.act("coverage", filtered=True)
            gp = a.context().get("graphPairing") or {}
            q = gp.get("qualifiedBy") or {}
            check("whole then filtered: the whole-log finding still stands in context",
                  q.get("scope") == "whole log" and "foreignAfter500" in (q.get("notDeclared") or []), q)
            check("…and the filtered comparison is stated beside it",
                  "current filter" in str(q.get("narrower")), q)
            check("…and the filtered coverage reply says the whole-log finding still stands",
                  "whole log" in str((reply.get("coverage") or {}).get("qualifiedPublishedPairing")), reply.get("coverage"))
            a.act("filter", **{"from": None, "to": None})   # null clears, missing keeps
            open_in_order(a, log, "combined")
            a.act("filter", **{"from": 1001, "to": 1011})
            a.act("coverage", filtered=True)
            a.act("filter", **{"from": None, "to": None})   # null clears, missing keeps
            a.act("coverage")
            q = (a.context().get("graphPairing") or {}).get("qualifiedBy") or {}
            check("filtered then whole: the whole-log finding leads", q.get("scope") == "whole log"
                  and q.get("supersedesSample") is True and "foreignAfter500" in (q.get("notDeclared") or []), q)
            check("…and the earlier, dominated filtered comparison is not carried", not q.get("narrower"), q)

            print("6. the exported PDF says what the screen says (D-E2)")
            pdf = os.path.join(exchange, "m68-1-partial.pdf")
            reply = a.act("report", name="m68-1-partial", title="M68.1 verification",
                          sections=[{"kind": "table", "call": {"verb": "coverage"}}], path=pdf)
            check("the report exported", reply.get("ok") is True and os.path.exists(pdf), reply)
            text = open(pdf, "rb").read().decode("latin-1") if os.path.exists(pdf) else ""
            check("the page carries the membership warning", "not declared anywhere in the graph" in text,
                  "the warning reached the agent reply but not the page")
            check("the page carries the honest figures", "declared 3" in text and "covered 3" in text, text[:200])
    finally:
        shutil.rmtree(work, ignore_errors=True)
        shutil.rmtree(home, ignore_errors=True)
        shutil.rmtree(exchange, ignore_errors=True)

    print()
    if FAILED:
        print(f"{len(FAILED)} check(s) FAILED: " + "; ".join(FAILED))
        sys.exit(1)
    print("all M68.1 end-to-end checks passed")


if __name__ == "__main__":
    main()
