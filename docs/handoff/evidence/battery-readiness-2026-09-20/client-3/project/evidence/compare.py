#!/usr/bin/env python3
"""Compare exported audit YAML business rows against expectations.

Usage: evidence/compare.py [expectations.json] [scenario-id ...]

For each scenario it reads evidence/scenarios/<ID>/audit-<ID>.yaml, extracts every
PriceEvent event-cycle record in file order, and compares symbol, price, volume and
order (1-based dispatch position) with the expected rows. The sink file is reported
separately: an empty sink is recorded as UNVERIFIED, never as a pass.

Exit code 0 only if every compared row matches and no scenario is missing evidence.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
EVENT_RE = re.compile(
    r"^\s*eventToString:\s*PriceEvent\{symbol=(?P<symbol>.*?), price=(?P<price>[^,]*), volume=(?P<volume>[^}]*)\}\s*$"
)
NODELOG_RE = re.compile(r"price:\s*(?P<price>[-\d.eE]+), volume:\s*(?P<volume>-?\d+)")


def actual_rows(yaml_path):
    """Business rows as observed: one per PriceEvent cycle, in file order."""
    rows = []
    lines = yaml_path.read_text().splitlines()
    for i, line in enumerate(lines):
        m = EVENT_RE.match(line)
        if not m:
            continue
        row = {
            "order": len(rows) + 1,
            "symbol": m.group("symbol"),
            "price": float(m.group("price")),
            "volume": int(m.group("volume")),
        }
        # cross-check the dedicated rootNode audit entries in the same cycle
        for follow in lines[i:i + 6]:
            n = NODELOG_RE.search(follow)
            if n and "rootNode" in follow:
                row["audited_price"] = float(n.group("price"))
                row["audited_volume"] = int(n.group("volume"))
                break
        rows.append(row)
    return rows


def compare(expect_file, scenarios):
    spec = json.loads(pathlib.Path(expect_file).read_text())
    failures = []
    print(f"expectations: {expect_file}\n")
    for sid in scenarios:
        sc = spec["scenarios"][sid]
        exp = sc["expected_audit_rows"]
        sdir = ROOT / "evidence" / "scenarios" / sid
        ypath = sdir / f"audit-{sid}.yaml"
        print(f"== scenario {sid}")
        if not ypath.is_file():
            failures.append(f"{sid}: no exported audit YAML at {ypath}")
            print("  FAIL no exported audit YAML")
            continue
        act = actual_rows(ypath)
        if len(act) != len(exp):
            failures.append(f"{sid}: expected {len(exp)} business rows, observed {len(act)}")
            print(f"  FAIL row count expected={len(exp)} observed={len(act)}")
        for e, a in zip(exp, act):
            diffs = []
            for field in ("order", "symbol"):
                if e[field] != a[field]:
                    diffs.append(f"{field}: expected {e[field]!r} observed {a[field]!r}")
            if float(e["price"]) != a["price"]:
                diffs.append(f"price: expected {e['price']} observed {a['price']}")
            if int(e["volume"]) != a["volume"]:
                diffs.append(f"volume: expected {e['volume']} observed {a['volume']}")
            if a.get("audited_price") is not None and a["audited_price"] != a["price"]:
                diffs.append(f"internal: rootNode price {a['audited_price']} != event {a['price']}")
            if a.get("audited_volume") is not None and a["audited_volume"] != a["volume"]:
                diffs.append(f"internal: rootNode volume {a['audited_volume']} != event {a['volume']}")
            status = "OK  " if not diffs else "FAIL"
            print(f"  {status} row {a['order']}: {a['symbol']},{a['price']},{a['volume']}")
            for d in diffs:
                print(f"       {d}")
                failures.append(f"{sid} row {e['order']}: {d}")
        # sink: reported, never counted as a pass
        out = sdir / f"output-{sid}.txt"
        size = out.stat().st_size if out.is_file() else None
        if size is None:
            print("  SINK UNVERIFIED: no sink file produced")
        elif size == 0:
            print("  SINK UNVERIFIED: data/output.txt exists but is empty (0 bytes) "
                  "- no graph node publishes to the sink")
        else:
            print(f"  SINK has content ({size} bytes) - inspect {out}")
    print()
    if failures:
        print(f"RESULT: FAIL ({len(failures)} mismatch(es))")
        for f in failures:
            print(f"  - {f}")
        return 1
    print(f"RESULT: PASS (all business rows matched across {len(scenarios)} scenario(s))")
    return 0


if __name__ == "__main__":
    args = sys.argv[1:]
    expect = args[0] if args else str(ROOT / "EXPECTED.json")
    ids = args[1:] or ["A", "B", "C", "D", "E", "F"]
    sys.exit(compare(expect, ids))
