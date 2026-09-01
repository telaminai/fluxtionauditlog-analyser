
## Scale — additional detectors and enrichment

In addition to D1–D6 above, this engine covers **D7–D18**, and the enrichment each needs.

- **D7 momentum ignition** — one trader's executions move the mid ≥3% within 10000ms, followed by that
  trader executing the opposite side within 20000ms.
- **D8 cross-desk collusion** — two traders on *different* desks execute opposite sides of the same
  instrument within 2000ms at a price outside the prevailing bid/ask.
- **D9 order-to-trade ratio** — a trader whose orders exceed 20× their executions on one instrument
  within a session.
- **D10 quote fade** — a trader cancels ≥5 orders on one instrument within 500ms of a quote moving
  against them.
- **D11 excessive spread capture** — an execution at a price better than the prevailing mid by more than
  half the spread.
- **D12 odd-lot evasion** — ≥10 orders from one trader on one instrument below `lotSize` within 5000ms.
- **D13 closing-auction concentration** — a trader accounting for ≥40% of executed quantity on one
  instrument within 60000ms before SessionClose.
- **D14 sector embargo drift** — an order in an embargoed sector by a trader **not** flagged restricted,
  where that trader has traded that sector before the embargo.
- **D15 self-cross** — a trader's own BUY and SELL orders both live on the same instrument at crossing
  prices.
- **D16 stale-quote trading** — an execution against a quote older than 5000ms.
- **D17 ramping** — ≥5 consecutive executions by one trader on one instrument each at a price above the
  previous.
- **D18 cancel clustering** — ≥8 cancels from one trader across ≥3 instruments within 1000ms.

**Scoring and escalation.** Each tripped detector carries a severity score (D1–D6 = 3, D7–D12 = 2,
D13–D18 = 1). A record whose surviving alerts total **≥5** must additionally record
`escalation: "TIER2"`; ≥8 must record `"TIER1"`; otherwise `"NONE"`. Escalation is computed **after**
the materiality gate — suppressed alerts contribute nothing.

Every rule S1–S10 applies unchanged, over all eighteen detectors. A design that satisfies every rule
tends to land around **50 nodes**; that is an observation, not a requirement, and node count is not
scored.
