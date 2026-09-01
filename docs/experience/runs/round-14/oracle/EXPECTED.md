# Hold-out expectations — written before any round-14 engine exists

Scored against `holdout.csv`. Records are matched by **content** (event ids), never by index, so extra
lifecycle records do not shift the scoring.

| # | check | rule |
|---|---|---|
| H1 | one record per scenario event: **24** business records | S6 |
| H2 | the **last** path element is the same node name in every record | S7 |
| H3 | the two identical republishes evaluate **zero** detectors and raise **zero** alerts | S5 |
| H4 | the record for order **S10** has `D5` in `detectorsTripped` | D5 |
| H5 | the record for the execution of **W2** has `D3` in `detectorsTripped` | D3 |
| H6 | the record for **R1** has `D6` tripped and its alert in `alerts` (notional 20000) | D6 + S4 |
| H7 | the record for **R2** has its D6 alert in `suppressedAlerts`, not `alerts` (notional 5000) | S4 |
| H8 | the final `QUOTE` record's `pathLength` is **strictly less** than the W2 execution record's | S10 |

No agent sees this file or `holdout.csv`.
