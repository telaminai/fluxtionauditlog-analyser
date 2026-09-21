#!/usr/bin/env python3
"""Validate a desk audit log against the reference model and the spec invariants.

usage: validate_audit.py <audit.yaml> [feed.csv] [sink-dir] [report.md]

Part 1 aligns the log's business records with the feed rows (one feed, so same order) and compares what each
node logged with what reference_model.py expects. Part 2 checks invariants I1-I6 from the log alone.
Part 3 (when a sink directory is given) compares what the desk actually PUBLISHED - the files Mongoose's
file sinks wrote - with the model: the log says what a node decided, the sink says what left the process.
Part 4 compares the end-of-day report markdown the reports sink wrote with the markdown the model
renders. For a reporting node the rendered file IS the deliverable, so its text is part of the contract.
It defaults to report.md beside the audit log.
Exit status is 0 only when every part is clean.
"""
import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
EVENT_OF = {"PRODUCT": "ProductDefinition", "EXCHANGE": "ExchangeStatus", "MARKET": "MarketPrice",
            "ORDER": "ClientOrder", "FILL": "HedgeFill", "SIGNAL": "Signal", "EOD": "EndOfDay"}
DESK_NODES = ["productCatalog", "marketSession", "priceBook", "hedgeFills", "orderGateway",
              "positionKeeper", "hedger", "pnlCalculator", "eodReporter"]
TRACE_KEYS = {"thread", "method"}
TOL = 1e-6


def parse_log(path):
    records = []
    for index, chunk in enumerate(c for c in Path(path).read_text().split("---") if "eventLogRecord" in c):
        event = re.search(r"^\s*event: (.*)$", chunk, re.M).group(1).strip()
        nodes = defaultdict(dict)
        order = []
        for name, body in re.findall(r"^\s*- (\w+): \{(.*)\}\s*$", chunk, re.M):
            order.append(name)
            for pair in re.split(r", (?=[\w.]+: )", body.strip()):
                if ": " in pair:
                    k, v = pair.split(": ", 1)
                    nodes[name][k.strip()] = v.strip()
        records.append({"index": index, "event": event, "nodes": nodes, "order": order})
    return records


def same(expected, actual):
    if actual is None:
        return False
    if isinstance(expected, bool):
        return actual == str(expected).lower()
    if isinstance(expected, (int, float)):
        try:
            return abs(float(actual) - float(expected)) <= TOL * max(1.0, abs(float(expected)))
        except ValueError:
            return False
    return actual == str(expected)


def compare(records, expected):
    problems = []
    business = [r for r in records if r["event"] in EVENT_OF.values()]
    if len(business) != len(expected):
        problems.append(f"record count: log has {len(business)} business records, feed has {len(expected)} rows")
    checked = 0
    for rec, exp in zip(business, expected):
        where = f"line {exp['line']} [{exp['row']}] record {rec['index']}"
        if rec["event"] != EVENT_OF[exp["kind"]]:
            problems.append(f"{where}: event is {rec['event']}, expected {EVENT_OF[exp['kind']]}")
            continue
        for node in DESK_NODES:
            actual = {k: v for k, v in rec["nodes"].get(node, {}).items() if k not in TRACE_KEYS}
            want = exp["expect"].get(node)
            if want is None:
                if actual:
                    problems.append(f"{where}: {node} should not have logged, but logged {actual}")
                continue
            for key, value in want.items():
                checked += 1
                if not same(value, actual.get(key)):
                    problems.append(f"{where}: {node}.{key} expected {value!r}, log has {actual.get(key)!r}")
            if node == "hedger":
                extra = {k for k in actual if k.startswith("hedge_")} - {k for k in want if k.startswith("hedge_")}
                for key in sorted(extra):
                    problems.append(f"{where}: hedger sent an order the model did not: {key}={actual[key]}")
    return problems, checked, len(business)


def invariants(records):
    problems = []
    products, is_open, position = {}, defaultdict(bool), {}
    halted, enabled = False, True
    for rec in records:
        n = rec["nodes"]
        where = f"record {rec['index']} ({rec['event']})"
        if "symbol" in n.get("productCatalog", {}):
            c = n["productCatalog"]
            products[c["symbol"]] = {"exchange": c["exchange"], "lot": float(c["lotSize"]), "threshold": float(c["hedgeThreshold"])}
        if "exchange" in n.get("marketSession", {}):
            is_open[n["marketSession"]["exchange"]] = n["marketSession"]["open"] == "true"
        g = n.get("orderGateway", {})
        if g.get("decision") == "ACCEPT":                                          # I1
            if halted:
                problems.append(f"I1 {where}: order {g.get('orderId')} accepted while halted")
            p = products.get(g.get("symbol"))
            if p and not is_open[p["exchange"]]:
                problems.append(f"I1 {where}: order {g.get('orderId')} accepted with {p['exchange']} closed")
        if "halted" in g:
            halted = g["halted"] == "true"
        k = n.get("positionKeeper", {})
        if "position" in k:                                                        # I4
            position[(k["book"], k["symbol"])] = float(k["position"])
            total = sum(q for (b, s), q in position.items() if s == k["symbol"])
            if abs(total - float(k["net"])) > TOL:
                problems.append(f"I4 {where}: net {k['net']} != sum of book positions {total} for {k['symbol']}")
        h = n.get("hedger", {})
        if "enabled" in h:
            enabled = h["enabled"] == "true"
        for key, value in h.items():
            if key.startswith("hedge_"):                                          # I2, I6
                symbol, p = key[6:], products.get(key[6:])
                if not enabled:
                    problems.append(f"I2 {where}: hedge order for {symbol} while hedging is off")
                if p and not is_open[p["exchange"]]:
                    problems.append(f"I2 {where}: hedge order for {symbol} with {p['exchange']} closed")
                if p and abs(float(value) / p["lot"] - round(float(value) / p["lot"])) > TOL:
                    problems.append(f"I6 {where}: hedge quantity {value} is not a multiple of lot {p['lot']}")
        if "ordersSent" in h and enabled:                                          # I3
            for key, value in h.items():
                if key.startswith("exposure_"):
                    p = products.get(key[9:])
                    if p and is_open[p["exchange"]] and abs(float(value)) >= p["threshold"] and abs(float(value)) >= p["lot"]:
                        problems.append(f"I3 {where}: {key[9:]} exposure {value} left unhedged (threshold {p['threshold']})")
        c = n.get("pnlCalculator", {})
        if "totalPnl" in c:                                                        # I5
            books = sum(float(v) for key, v in c.items() if key.startswith("pnl_"))
            if abs(books - float(c["totalPnl"])) > 1e-6 * max(1.0, abs(books)):
                problems.append(f"I5 {where}: totalPnl {c['totalPnl']} != sum of books {books}")
    return problems


def sinks(sink_dir, expected):
    want = {"hedgeOrders": [], "executions": [], "rejects": []}
    for row in expected:
        g, h = row["expect"].get("orderGateway", {}), row["expect"].get("hedger", {})
        f = row["row"].split(",")
        if g.get("decision") == "ACCEPT":
            want["executions"].append(f"Execution[orderId={g['orderId']}, book={f[3]}, symbol={f[4]}, deskQty={g['deskQty']}, price={g['execPrice']}]")
        if g.get("decision") == "REJECT":
            want["rejects"].append(f"OrderReject[orderId={g['orderId']}, reason={g['reason']}]")
        for key in h:
            if key.startswith("hedge_"):
                sym = key[6:]
                want["hedgeOrders"].append(f"HedgeOrder[id={h['hedgeId_' + sym]}, symbol={sym}, quantity={h[key]}, limit={h['hedgeLimit_' + sym]}]")
    problems = []
    for name, lines in want.items():
        path = Path(sink_dir) / f"{name}.txt"
        got = path.read_text().splitlines() if path.exists() else []
        if got != lines:
            problems.append(f"sink {name}: published {len(got)} messages, model expects {len(lines)}")
            for i, (a, b) in enumerate(zip(got, lines)):
                if a != b:
                    problems.append(f"sink {name} message {i + 1}: published {a!r}, expected {b!r}")
                    break
    return problems, sum(len(v) for v in want.values())


def report_markdown(path, expected):
    """Compare the published report against the model, line by line."""
    want = "".join(r["markdown"] for r in expected if "markdown" in r)
    if not want:
        return [], None
    if not Path(path).exists():
        return [f"report: the model renders a report but {path} does not exist"], 0
    got = Path(path).read_text()
    if got.strip() == want.strip():
        return [], len(want.splitlines())
    problems, a, b = [], got.strip().splitlines(), want.strip().splitlines()
    if len(a) != len(b):
        problems.append(f"report: published {len(a)} lines, model renders {len(b)}")
    for i, (x, y) in enumerate(zip(a, b), 1):
        if x != y:
            problems.append(f"report line {i}: published {x!r}")
            problems.append(f"report line {i}: model     {y!r}")
            break
    return problems, len(b)


def main():
    audit = sys.argv[1]
    feed = sys.argv[2] if len(sys.argv) > 2 else str(HERE.parent / "data" / "desk-day.csv")
    expected = json.loads(subprocess.run([sys.executable, str(HERE / "reference_model.py"), feed],
                                         check=True, capture_output=True, text=True).stdout)
    records = parse_log(audit)
    model_problems, checked, business = compare(records, expected)
    invariant_problems = invariants(records)
    sink_problems, sink_count = sinks(sys.argv[3], expected) if len(sys.argv) > 3 and sys.argv[3] != "-" else ([], None)
    report_path = sys.argv[4] if len(sys.argv) > 4 else str(Path(audit).parent / "report.md")
    report_problems, report_lines = report_markdown(report_path, expected)
    print(f"log: {len(records)} records, {business} business | feed: {len(expected)} rows | values compared: {checked}")
    print(f"model mismatches: {len(model_problems)} | invariant violations: {len(invariant_problems)}"
          + (f" | sink messages checked: {sink_count}, mismatches: {len(sink_problems)}" if sink_count is not None else " | sinks: not checked")
          + (f" | report lines checked: {report_lines}, mismatches: {len(report_problems)}" if report_lines is not None else " | report: none rendered"))
    for p in model_problems + invariant_problems + sink_problems + report_problems:
        print("  -", p)
    ok = not (model_problems or invariant_problems or sink_problems or report_problems)
    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
