#!/usr/bin/env python3
"""Compare exported audit-preview-smoke.yaml PriceEvent rows against EXPECTED.json.

Usage: compare.py <expected.json> <scenario_letter> <audit_yaml_path>

Parses only the lines this bundle actually emits:
    event: PriceEvent
    eventToString: PriceEvent{symbol=X, price=Y, volume=Z}
in file order, and checks symbol/price/volume/order (row position) against
EXPECTED.json's scenarios[<letter>].rows. Exits non-zero and prints every
mismatch found; exits zero only if every row of the scenario matches exactly.
"""
import json
import re
import sys

ROW_RE = re.compile(r"PriceEvent\{symbol=([^,]*), price=([^,]*), volume=([^}]*)\}")


def parse_audit_rows(path):
    rows = []
    with open(path) as f:
        lines = f.readlines()
    for i, line in enumerate(lines):
        if line.strip() == "event: PriceEvent":
            next_line = lines[i + 1]
            m = ROW_RE.search(next_line)
            if not m:
                continue
            symbol, price, volume = m.groups()
            rows.append({
                "symbol": symbol,
                "price": float(price),
                "volume": int(volume),
            })
    return rows


def main():
    if len(sys.argv) != 4:
        print(f"usage: {sys.argv[0]} <expected.json> <scenario_letter> <audit_yaml_path>", file=sys.stderr)
        return 2

    expected_path, scenario, audit_path = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(expected_path) as f:
        expected = json.load(f)

    expected_rows = expected["scenarios"][scenario]["rows"]
    actual_rows = parse_audit_rows(audit_path)

    failures = []

    if len(actual_rows) != len(expected_rows):
        failures.append(
            f"row count mismatch: expected {len(expected_rows)}, got {len(actual_rows)}"
        )

    for idx, exp in enumerate(expected_rows):
        exp_order = exp["order"]
        if idx >= len(actual_rows):
            failures.append(f"order {exp_order}: missing from audit log (expected {exp})")
            continue
        act = actual_rows[idx]
        act_order = idx + 1
        if act_order != exp_order:
            failures.append(f"order mismatch at position {idx}: expected order {exp_order}, actual position {act_order}")
        if act["symbol"] != exp["symbol"]:
            failures.append(f"order {exp_order}: symbol expected {exp['symbol']!r} got {act['symbol']!r}")
        if act["price"] != exp["price"]:
            failures.append(f"order {exp_order}: price expected {exp['price']!r} got {act['price']!r}")
        if act["volume"] != exp["volume"]:
            failures.append(f"order {exp_order}: volume expected {exp['volume']!r} got {act['volume']!r}")

    print(f"scenario {scenario}: {len(actual_rows)} audit rows parsed, {len(expected_rows)} expected rows")
    if failures:
        print(f"scenario {scenario}: FAIL")
        for f_ in failures:
            print(f"  - {f_}")
        return 1
    print(f"scenario {scenario}: PASS — all rows match (symbol, price, volume, order)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
