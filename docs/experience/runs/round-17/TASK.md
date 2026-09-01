# Build: a small trade-surveillance engine

This describes **behaviour only**. The number of nodes, their names
and their dependencies are entirely your design.

## What arrives

- **Order** — `orderId, trader, instrument, side (BUY/SELL), quantity, price, timestampMs`
- **Execution** — `orderId, quantity, price, timestampMs`
- **Quote** — `instrument, bid, ask, timestampMs`
- **TraderRoster** — `trader, restricted` (reference data)

## What must be true

**S1 — the book.** An order is live from its Order event until fully executed. Partial executions
reduce the live quantity. An execution against an unknown order changes nothing.

**S2 — the market.** From Quote, `mid = (bid+ask)/2`; the most recent quote for an instrument is the
current one.

**S3 — detectors arrest propagation.** A detector must not propagate when its condition does not hold.
In a cycle where no detector trips, the alert-handling nodes must not run at all.

**S4 — the materiality gate.** An alert whose notional (`quantity × price`) is below **10,000** is
suppressed rather than raised.

**S5 — reference data is not market activity.** A `TraderRoster` event whose values are unchanged must
not cause any detector to evaluate or any alert to be raised.

## Detectors

- **D1 wash trade** — the same trader has an executed BUY and an executed SELL of the same instrument
  within 5000ms of each other. It trips on the cycle whose execution completes such a pair, and not on
  cycles where nothing was newly executed.
- **D2 quote stuffing** — 10 or more orders from one trader on one instrument within 1000ms.
- **D3 restricted breach** — an order from a trader whose roster entry has `restricted = true`.

## Evidence: use the framework's own audit log

**Do not build a log format of your own.** Enable the framework's audit log and write it out as it
comes. It already records one entry per event and, within each, the nodes that ran in dispatch order —
so "which nodes ran", "in what order" and "how long was this path" are answered without you assembling
anything. A record you write by hand is worth less than one the framework wrote, because yours cannot
contradict your own code.

Two one-line conventions, so the log can be read mechanically:

- every detector logs its verdict: `auditLog.info("detector","D1").info("tripped", tripped);`
- the alert gate logs the outcome: `auditLog.info("raised", n).info("suppressed", m);`

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
run against a scenario written by someone else, which you will never see, and its audit log checked
against expected results you will never see. Correct behaviour on unseen input is the only thing
scored — a green build whose detectors never fire counts as a failure.

## Deliverables

1. The graph, built with a `FluxtionGraphBuilder`. Keep the package and generated class name you
   start with, or change them — your choice. Report the fully-qualified name of your main class.
2. `Main` as above, writing the framework's audit log to the given path.
3. **JUnit tests**; `mvn clean test` green. Write tests that would fail if a detector stopped firing.
4. **`SPEC-FRICTION.md`** — where this specification fought the framework. Every place you had to
   contort a design to satisfy a requirement, every requirement that felt unnatural to express, and
   what the natural shape would have been instead. Be blunt; this file is used to fix the spec.
