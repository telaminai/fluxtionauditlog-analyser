# Build: an order-fulfilment engine (extended)

Behaviour only. The number of nodes, their names and their dependencies are your design.

**Every rule is marked EDGE or CONDITION.** A CONDITION is a fact that holds while it is true — a value
other rules read. An EDGE fires exactly once, on the event that first makes something true, and does not
fire again until it has become false and true again. Re-emitting an EDGE on later events is wrong.

**More than one decision of the same kind may be emitted in a single event** — if one event makes three
orders releasable, three RELEASE decisions are emitted for that event number.

## Events (28)

**Reference** — `PRODUCT,sku,unitPrice,hazardous` · `CUSTOMER,customerId,tier,creditLimit`
(tier `STANDARD`/`GOLD`) · `CARRIER,carrierId,maxWeightKg,handlesHazardous` ·
`SUPPLIER,supplierId,leadTimeMs,approved` · `EMBARGO,country,active` ·
`BIN,binId,capacity,hazardousAllowed`

**Stock** — `RECEIPT,sku,quantity,timestampMs` · `ADJUST,sku,delta,timestampMs` ·
`COUNT,sku,countedQuantity,timestampMs` · `RESERVE,orderId,sku,quantity,timestampMs` ·
`BINPUT,sku,binId,quantity,timestampMs`

**Purchasing** — `PO,poId,supplierId,sku,quantity,timestampMs` · `POACK,poId,timestampMs` ·
`PODELIVERY,poId,quantity,timestampMs`

**Orders** — `ORDER,orderId,customerId,sku,quantity,destCountry,timestampMs` ·
`AMEND,orderId,newQuantity,timestampMs` · `CANCEL,orderId,timestampMs`

**Payment** — `PAID,orderId,amount,timestampMs` · `PAYFAIL,orderId,timestampMs` ·
`REFUND,orderId,amount,timestampMs`

**Fulfilment** — `PICKSTART,orderId,timestampMs` · `PICKDONE,orderId,timestampMs` ·
`DISPATCH,orderId,carrierId,weightKg,timestampMs` · `DELIVERED,orderId,timestampMs`

**Returns & quality** — `RETURN,orderId,quantity,timestampMs` · `DEFECT,sku,severity,timestampMs` ·
`QUARANTINE,sku,active,timestampMs` · `RELEASEQ,sku,timestampMs`

## Rules

**C1 — on-hand stock (CONDITION).** Per sku: `RECEIPT` and `PODELIVERY` add, `ADJUST` adds its delta,
`COUNT` replaces outright, `RETURN` adds back. Unknown sku starts at 0.

**C2 — reserved stock (CONDITION).** Per sku, the sum of `RESERVE` quantities for orders that are still
OPEN. A cancelled order's reservation is released.

**C3 — available stock (CONDITION).** `on-hand − reserved`.

**C4 — order state (CONDITION).** OPEN from `ORDER`; `AMEND` replaces quantity; `CANCEL` is terminal —
later events for that order change nothing and emit nothing.

**C5 — paid amount (CONDITION).** Sum of `PAID` minus sum of `REFUND`. `PAYFAIL` adds nothing.

**C6 — allocatable (CONDITION).** An OPEN order is allocatable when **available** stock for its sku is
≥ its quantity.

**C7 — credit ok (CONDITION).** Paid ≥ `quantity × unitPrice`, **or** tier is `GOLD` and
`quantity × unitPrice` ≤ credit limit.

**C8 — quarantined (CONDITION).** A sku is quarantined from `QUARANTINE,sku,true` until
`RELEASEQ,sku` or `QUARANTINE,sku,false`.

**C9 — embargoed (CONDITION).** A country is embargoed from `EMBARGO,country,true` until
`EMBARGO,country,false`.

**C10 — supplier approved (CONDITION).** From `SUPPLIER,supplierId,leadTimeMs,approved`.

**E1 — RELEASE (EDGE).** For an order, on the event that first makes it allocatable **and** credit-ok
**and** its sku not quarantined **and** its destination not embargoed. Emit again if it stops being
releasable and becomes releasable again.

**E2 — HAZARD_BLOCK (EDGE).** On a `DISPATCH` whose product is hazardous and whose carrier does not
handle hazardous.

**E3 — OVERWEIGHT (EDGE).** On a `DISPATCH` whose `weightKg` is strictly greater than the carrier's
`maxWeightKg`.

**E4 — SLA_BREACH (EDGE).** On the `PICKDONE` for an order, when its timestamp is strictly more than
3600000ms after the timestamp of the event on which that order was RELEASED. Nothing if never released.

**E5 — STOCKOUT (EDGE).** On the event that takes a sku's **on-hand** from zero-or-above to below zero.

**E6 — OVERSOLD (EDGE).** On the event that takes a sku's **available** stock below zero while on-hand
is still zero-or-above.

**E7 — PO_LATE (EDGE).** On a `PODELIVERY` arriving more than the supplier's `leadTimeMs` after the
`POACK` for that po. Nothing if the po was never acknowledged.

**E8 — UNAPPROVED_SUPPLIER (EDGE).** On a `PO` for a supplier whose `approved` is false.

**E9 — BIN_OVERFLOW (EDGE).** On a `BINPUT` that takes a bin's total quantity above its capacity.

**E10 — HAZARD_BIN (EDGE).** On a `BINPUT` of a hazardous sku into a bin whose `hazardousAllowed` is
false.

**E11 — QUALITY_HOLD (EDGE).** On a `DEFECT` with severity `HIGH` for a sku that is not already
quarantined.

**E12 — REFUND_EXCESS (EDGE).** On a `REFUND` that takes an order's paid amount below zero.

**E13 — RETURN_UNKNOWN (EDGE).** On a `RETURN` for an order that was never placed.

**E14 — reference data is not activity (CONDITION).** A `PRODUCT`, `CUSTOMER`, `CARRIER`, `SUPPLIER` or
`BIN` event whose values are unchanged must not cause any rule to be re-evaluated or any decision
emitted.

## What it must produce

A decisions file, one line per decision, in the order decided:

```
<eventNumber>,<DECISION>,<key>
```

`eventNumber` is the 1-based position of the triggering event in the scenario file (comments and blank
lines are not events and are not counted). `DECISION` is one of `RELEASE`, `HAZARD_BLOCK`, `OVERWEIGHT`,
`SLA_BREACH`, `STOCKOUT`, `OVERSOLD`, `PO_LATE`, `UNAPPROVED_SUPPLIER`, `BIN_OVERFLOW`, `HAZARD_BIN`,
`QUALITY_HOLD`, `REFUND_EXCESS`, `RETURN_UNKNOWN`. `key` is the `orderId` for order-scoped decisions,
the `sku` for stock- and quality-scoped ones, the `poId` for `PO_LATE` and `UNAPPROVED_SUPPLIER`, and
the `binId` for `BIN_OVERFLOW` and `HAZARD_BIN`.

## Running it

```
java -cp <classpath> <your.Main> <scenario-file> <decisions-file> [audit-log]
```
One event per line, comma-separated, `#` starts a comment. **Do not hardcode a scenario** — your engine
will be run against scenarios written by someone else.

## Deliverables

1. The graph, built with a `FluxtionGraphBuilder`. Report your main class's fully-qualified name.
2. `Main` as above, writing the decisions file and the framework's audit log if given a third argument.
3. **JUnit tests**; `mvn clean test` green.
