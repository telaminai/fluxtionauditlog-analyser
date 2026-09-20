#!/usr/bin/env python3
"""Compare audited business rows against EXPECTED.json.

Usage:
  ./compare.py                      # compare every scenario against EXPECTED.json
  ./compare.py --expected FILE      # compare against an alternative expectations file
  ./compare.py --only B             # one scenario

Reads evidence/<ID>/audit-<ID>.yaml (the exported audit log) and pulls one business row per
PriceEvent cycle: symbol, price, volume (from the rootNode nodeLog entry) and order (the
position of the cycle in the log). Also checks the file sink evidence. Exit code 0 = all pass.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
EVENT_RE = re.compile(r"^\s*event:\s*PriceEvent\s*$")
ROOTNODE_RE = re.compile(
    r"-\s*rootNode:\s*\{[^}]*receivedEvent:\s*PriceEvent\{symbol=(?P<symbol>[^,]*),\s*"
    r"price=(?P<ets_price>[^,]*),\s*volume=(?P<ets_volume>[^}]*)\},\s*"
    r"price:\s*(?P<price>[^,]+),\s*volume:\s*(?P<volume>[^}]+)\}"
)


def parse_rows(yaml_path):
    """One row per PriceEvent event cycle, in log order."""
    rows, in_event, order = [], False, 0
    for line in yaml_path.read_text().splitlines():
        if line.strip() == "---":
            in_event = False
            continue
        if EVENT_RE.match(line):
            in_event = True
            continue
        if in_event:
            m = ROOTNODE_RE.search(line)
            if m:
                order += 1
                rows.append({
                    "order": order,
                    "symbol": m.group("symbol").strip(),
                    "price": float(m.group("price")),
                    "volume": int(m.group("volume")),
                    # the same values as rendered inside PriceEvent.toString()
                    "toString_price": float(m.group("ets_price")),
                    "toString_volume": int(m.group("ets_volume")),
                })
                in_event = False
    return rows


def compare(scenario, failures):
    sid = scenario["id"]
    ev = ROOT / "evidence" / sid
    yaml_path = ev / f"audit-{sid}.yaml"
    print(f"\n=== scenario {sid} ({scenario['input_source']}) ===")
    if not yaml_path.is_file():
        failures.append(f"{sid}: missing export {yaml_path}")
        print(f"  FAIL no audit export at {yaml_path}")
        return

    actual = parse_rows(yaml_path)
    expected = scenario["expected_business_rows"]
    if len(actual) != len(expected):
        failures.append(f"{sid}: row count expected {len(expected)} actual {len(actual)}")
        print(f"  FAIL row count: expected {len(expected)}, actual {len(actual)}")

    for i in range(max(len(actual), len(expected))):
        exp = expected[i] if i < len(expected) else None
        act = actual[i] if i < len(actual) else None
        if exp is None:
            failures.append(f"{sid}: unexpected extra row {act}")
            print(f"  FAIL extra audited row: {act}")
            continue
        if act is None:
            failures.append(f"{sid}: missing row {exp}")
            print(f"  FAIL missing row: {exp}")
            continue
        diffs = []
        for field in ("order", "symbol", "price", "volume"):
            if act[field] != exp[field]:
                diffs.append(f"{field}: expected {exp[field]!r} actual {act[field]!r}")
        # the two independent audit renderings of the same event must agree
        if act["toString_price"] != act["price"] or act["toString_volume"] != act["volume"]:
            diffs.append("event toString disagrees with the logged price/volume fields")
        if diffs:
            failures.append(f"{sid} row {i + 1}: " + "; ".join(diffs))
            print(f"  FAIL row {i + 1} ({exp['symbol']}): " + "; ".join(diffs))
        else:
            print(f"  ok   order={act['order']} symbol={act['symbol']} "
                  f"price={act['price']} volume={act['volume']}")

    # sink evidence
    sink = ev / "output.txt"
    sink_rows = [l for l in sink.read_text().splitlines() if l.strip()] if sink.is_file() else []
    exp_sink = scenario["expected_sink_rows"]
    state = "absent" if not sink.is_file() else ("empty" if not sink_rows else f"{len(sink_rows)} rows")
    if len(sink_rows) != exp_sink:
        failures.append(f"{sid}: sink rows expected {exp_sink} actual {len(sink_rows)}")
        print(f"  FAIL sink: expected {exp_sink} rows, actual {len(sink_rows)}")
    else:
        note = " (NOT a verified sink scenario: nothing publishes to the sink)" if exp_sink == 0 else ""
        print(f"  ok   sink {state} as predicted{note}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--expected", default=str(ROOT / "EXPECTED.json"))
    ap.add_argument("--only")
    args = ap.parse_args()

    spec = json.loads(Path(args.expected).read_text())
    print(f"expectations: {args.expected}")
    failures = []
    for scenario in spec["scenarios"]:
        if args.only and scenario["id"] != args.only:
            continue
        compare(scenario, failures)

    print("\n" + "=" * 60)
    if failures:
        print(f"RESULT: FAIL — {len(failures)} mismatch(es)")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("RESULT: PASS — every compared business row matched")
    return 0


if __name__ == "__main__":
    sys.exit(main())
