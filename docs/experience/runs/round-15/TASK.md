# Build: a small trade-surveillance engine

Package `com.acme.surveillance`. This describes **behaviour and nothing else**. How many nodes there
are, what they are called, and how they depend on each other is entirely your design — there is no
required structure and no required node count.

## What arrives

- **Order** — `orderId, trader, instrument, side (BUY/SELL), quantity, price, timestampMs`
- **Execution** — `orderId, quantity, price, timestampMs`
- **Quote** — `instrument, bid, ask, timestampMs`
- **TraderRoster** — `trader, restricted` (reference data)

## What must be true

**S1 — the book.** An order is live from its Order event until fully executed. Partial executions reduce
the live quantity. An execution against an unknown order changes nothing.

**S2 — the market.** From Quote: `mid = (bid+ask)/2`. The most recent quote for an instrument is
the current one.

**S3 — detectors arrest propagation.** A detector must not propagate when its condition does not hold.
In a cycle where no detector trips, the alert-handling nodes must not run at all. *The surveillance
record is exempt: it is driven by the cycle and runs once in every cycle, including quiet ones.*

**S4 — the materiality gate.** An alert whose notional (`quantity × price`) is below **10,000** is
suppressed: it must be reported as suppressed and must not count as a raised alert.

**S5 — reference data is not market activity.** A `TraderRoster` event whose values are unchanged must
not cause any detector to evaluate or any alert to be raised.

**S6 — one record per cycle.** Each incoming event produces exactly one surveillance record.

**S7 — the record is last.** The surveillance record is the final node recorded in every cycle.

## Detectors

- **D1 wash trade** — one trader executes both sides of the same instrument within 5000ms.
- **D2 quote stuffing** — 10 or more orders from one trader on one instrument within 1000ms.
- **D3 restricted breach** — an order from a trader whose roster entry has `restricted = true`.

## The audit log

Every run writes a log, one document per event:

```yaml
- record:
    cycle: 7
    event: "Execution"
    eventDetail: "Execution{orderId=O-4, quantity=100, price=150.0}"
    path: ["orderBook", "washDetector", "materialityGate", "surveillanceRecord"]
    pathLength: 4
    detectorsTripped: ["D1"]
    alerts:        [{ detector: "D1", trader: "TRD-1", instrument: "DEMO1", notional: 15000.0 }]
    suppressedAlerts: [{ detector: "D2", notional: 4000.0 }]
```

## Running it

`Main` takes two arguments and must work with **any** scenario file in this format:

```
java -cp <classpath> com.acme.surveillance.Main <scenario-file> <output-log-path>
```

```
ORDER,orderId,trader,instrument,BUY|SELL,quantity,price,timestampMs
EXEC,orderId,quantity,price,timestampMs
QUOTE,instrument,bid,ask,timestampMs
ROSTER,trader,true|false
```
One event per line, `#` comments ignored. **Do not hardcode a scenario in `Main`.** Your engine will be
run against a scenario written by someone else that you will never see.

## Deliverables

1. The graph, built with a `FluxtionGraphBuilder`; generated class `SurveillanceProcessor`, package
   `com.acme.surveillance.generated`, output `src/main/java`, resources `src/main/resources`.
2. `Main` as specified above, writing the audit log.
3. **JUnit tests** under `src/test/java`; `mvn clean test` green.
4. Evidence for S1–S7: for each, cite a passing test or the audit-log lines, and say which.

## The audit log is your feedback channel

The log states, per cycle, which nodes ran and in what order. S3, S6 and S7 are claims about what
actually ran, and the log answers them directly — prefer it to reading generated source.
