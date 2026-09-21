#!/usr/bin/env python3
"""Independent check of the vendor's VaR from the feed: EWMA(0.94) variance of simple returns,
VaR = sum |qty * multiplier * price| * vol * 2.326. Compares with what the vendor nodes logged."""
import math, re, sys
sys.path.insert(0, "desk")
from validate_audit import parse_log
LIMIT, LAM, Z = 20000.0, 0.94, 2.326
price, var, pos, expected, last = {}, {}, {}, [], False
for line in open("data/risk-day.csv"):
    f = [c.strip() for c in line.strip().split(",")]
    if not line.strip() or line.startswith("#"): continue
    if f[0] == "RQUOTE":
        s, p = f[1], float(f[2])
        if s in price and price[s]:
            r = p / price[s] - 1
            var[s] = LAM * var.get(s, 0.0) + (1 - LAM) * r * r
        price[s] = p
    else:
        pos[f[1]] = (float(f[2]), float(f[3]))
    total = sum(abs(q * m * price.get(s, 0.0)) * math.sqrt(var.get(s, 0.0)) * Z for s, (q, m) in pos.items())
    breached = total > LIMIT
    expected.append((",".join(f), total, breached, breached != last)); last = breached
recs = [r for r in parse_log(sys.argv[1]) if r["event"] in ("RiskQuote", "RiskPosition")]
bad = 0
print(f"{'row':<24}{'model VaR':>12}{'vendor VaR':>12}  breached  alert")
for (row, total, breached, alert), r in zip(expected, recs):
    n = r["nodes"]; eng = n.get("acmeRiskEngine", {}); g = n.get("riskLimitGuard", {})
    ok = (abs(float(eng.get("totalVar", "nan")) - total) < 1e-6 and eng.get("breached") == str(breached).lower()
          and g.get("alert") == str(alert).lower())
    bad += not ok
    print(f"{row:<24}{total:>12.2f}{float(eng.get('totalVar','nan')):>12.2f}  {eng.get('breached'):<8}  {g.get('alert'):<6}{'' if ok else '  <-- MISMATCH'}")
print(f"rows {len(expected)} records {len(recs)} mismatches {bad}")
print("node order in a quote record:", recs[1]["order"])
sys.exit(1 if bad or len(expected) != len(recs) else 0)
