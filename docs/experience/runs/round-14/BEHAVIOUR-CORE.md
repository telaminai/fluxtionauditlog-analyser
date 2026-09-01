# Build: a market-abuse surveillance engine

Package `com.acme.surveillance`. **This specification describes BEHAVIOUR. It does not tell you what the
nodes are, how many there should be, or how they depend on each other — that is your design.**

## What arrives

Seven event types, in any order and any number of times:

- **Order** — `orderId, trader, instrument, side (BUY/SELL), quantity, limitPrice, timestampMs`
- **Execution** — `orderId, quantity, price, timestampMs`
- **Cancel** — `orderId, timestampMs`
- **Quote** — `instrument, bid, ask, timestampMs`
- **InstrumentStatic** — `instrument, sector, lotSize` (reference data)
- **TraderRoster** — `trader, desk, restricted` (reference data)
- **SessionClose** — `timestampMs` (control)

## What must be true

**S1 — the book.** An order is *live* from its Order event until it is fully executed or cancelled.
Partial executions reduce the live quantity. Cancelling an unknown or already-dead order changes nothing.

**S2 — the market.** From Quote: `mid = (bid+ask)/2`, `spread = ask-bid`. The most recent quote for an
instrument is the current one.

**S3 — detectors arrest propagation.** Each detector below is evaluated when its inputs move, and **must
not propagate** when its condition does not hold. In a cycle where no detector trips, the
**alert-handling nodes** — the materiality gate, escalation, and anything else that exists to process
alerts — must not run at all.

*The surveillance record is explicitly exempt from S3.* It is driven by the cycle itself, not by any
detector, and it runs exactly once in every cycle including quiet ones (S6). "Runs every cycle" and
"is triggered by a detector" are different things, and the record is the first and not the second.
Round 13 lost this in twelve of fourteen cycles by conflating them.

**S4 — the materiality gate arrests.** An alert whose notional (`quantity × price`) is **below 10,000**
is suppressed: it must not reach case management and must not appear as a raised alert. The threshold is
fixed at build time and comes from no event.

**S5 — reference data is not market activity.** An `InstrumentStatic` or `TraderRoster` event carrying
values that are unchanged must **not**, by itself, cause any detector to evaluate, any alert to be
raised, or any existing alert to be re-raised.

**S6 — one record per cycle.** Each incoming event produces **exactly one** surveillance record,
however many detectors fired inside that cycle.

**S7 — the record is last.** In every cycle the surveillance record must be the **final** node recorded
in that cycle's `nodeLogs`.

**S8 — the record's length tracks what happened.** A cycle in which no detector trips records **zero**
alerts. A cycle in which three trip records three, in a stable, documented order. Every record must
distinguish three populations, by name:
`detectorsTripped`, `detectorsEvaluatedNotTripped`, and `detectorsNotEvaluated`.

**S9 — no mixed generations.** Every value in one record must describe the same event. A record may not
pair an alert computed from this event with a book or quote state from a previous one.

**S10 — pathways differ in length.** A `Quote` reaches the record through fewer nodes than an
`Execution` does. Each record must state `pathLength` (how many nodes ran in that cycle) and `path`
(their names, in execution order).

## Detectors

Each is a separate concern. Trip conditions:

- **D1 layering** — ≥3 live orders from one trader on one instrument, same side, placed within 1000ms of
  each other, and all cancelled within 2000ms of their own placement.
- **D2 spoofing** — an order of at least 5× that trader's median order size for that instrument,
  cancelled with no execution against it.
- **D3 wash trade** — one trader executes both sides of the same instrument within 5000ms.
- **D4 marking the close** — an execution within 30000ms before a SessionClose that moves the
  instrument's mid by ≥2%.
- **D5 quote stuffing** — ≥10 orders from one trader on one instrument within 1000ms.
- **D6 restricted breach** — an order from a trader whose roster entry has `restricted = true`, in an
  instrument whose sector is embargoed. The embargoed sector list is fixed at build time and contains exactly one sector: `ENERGY`.

## The audit log

Every run writes `logs/surveillance-audit.yaml`. One document per event, in this shape:

```yaml
- record:
    cycle: 7
    event: "Execution"
    eventDetail: "Execution{orderId=O-4, quantity=100, price=150.0}"
    nodeLogs:
      - <nodeName>: { method: <methodName>, <any node-specific fields> }
    pathLength: 5
    path: ["orderBook", "executionLedger", "washDetector", "materialityGate", "surveillanceRecord"]
    detectorsTripped: ["D3"]
    detectorsEvaluatedNotTripped: ["D2"]
    detectorsNotEvaluated: ["D1", "D4", "D5", "D6"]
    alerts:
      - { detector: "D3", trader: "TRD-1", instrument: "DEMO1", notional: 15000.0 }
    suppressedAlerts:
      - { detector: "D2", reason: "below materiality", notional: 4000.0 }
    generation: 7
```

The node-by-node `nodeLogs` list, in execution order, is the primary evidence for S3, S6, S7, S8 and
S10 — those are claims about what actually ran, and cannot be shown by reading source.
