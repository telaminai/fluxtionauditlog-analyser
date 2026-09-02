# Build: an order-fulfilment engine

Behaviour only. The number of nodes, their names and their dependencies are your design.

**Every rule below is marked EDGE or CONDITION.** A CONDITION is a fact that holds while it is true. An
EDGE fires exactly once, on the event that first makes something true, and does not fire again until it
has become false and true again. Where a rule is an EDGE, re-emitting on later events is wrong; where it
is a CONDITION, it is simply a value other rules read.

## Events (15)

**Reference data**
- `PRODUCT,sku,unitPrice,hazardous` — hazardous is `true`/`false`
- `CUSTOMER,customerId,tier,creditLimit` — tier is `STANDARD`/`GOLD`
- `CARRIER,carrierId,maxWeightKg,handlesHazardous`

**Stock**
- `RECEIPT,sku,quantity,timestampMs`
- `ADJUST,sku,delta,timestampMs` — delta may be negative
- `COUNT,sku,countedQuantity,timestampMs` — sets on-hand absolutely

**Orders**
- `ORDER,orderId,customerId,sku,quantity,timestampMs`
- `AMEND,orderId,newQuantity,timestampMs`
- `CANCEL,orderId,timestampMs`

**Payment**
- `PAID,orderId,amount,timestampMs`
- `PAYFAIL,orderId,timestampMs`

**Fulfilment**
- `PICKSTART,orderId,timestampMs`
- `PICKDONE,orderId,timestampMs`
- `DISPATCH,orderId,carrierId,weightKg,timestampMs`
- `DELIVERED,orderId,timestampMs`

## Rules

**R1 — on-hand stock (CONDITION).** Per sku: `RECEIPT` adds, `ADJUST` adds its delta (which may be
negative), `COUNT` replaces the value outright. An unknown sku starts at 0.

**R2 — order state (CONDITION).** An order is OPEN from `ORDER`. `AMEND` replaces its quantity.
`CANCEL` makes it CANCELLED, which is terminal — later events for that order change nothing and emit
nothing.

**R3 — paid amount (CONDITION).** Per order, the sum of `PAID` amounts. `PAYFAIL` adds nothing.

**R4 — allocatable (CONDITION).** An OPEN order is allocatable when on-hand for its sku is greater than
or equal to its quantity.

**R5 — credit ok (CONDITION).** An OPEN order is credit-ok when **either** paid amount ≥
`quantity × unitPrice`, **or** the customer's tier is `GOLD` and `quantity × unitPrice` ≤ the customer's
credit limit.

**R6 — RELEASE (EDGE).** Emit `RELEASE` for an order on the event that first makes it both allocatable
and credit-ok. Emit at most once per order unless the order stops being releasable and becomes
releasable again, in which case emit again.

**R7 — HAZARD_BLOCK (EDGE).** On a `DISPATCH` whose product is hazardous and whose carrier does not
handle hazardous.

**R8 — OVERWEIGHT (EDGE).** On a `DISPATCH` whose `weightKg` is strictly greater than the carrier's
`maxWeightKg`.

**R9 — SLA_BREACH (EDGE).** On the `PICKDONE` event for an order, when that event's timestamp is
strictly more than 3600000ms after the timestamp of the event on which that order was RELEASED. If the
order was never released, emit nothing.

**R10 — STOCKOUT (EDGE).** On the event that takes a sku's on-hand from zero-or-above to below zero.

**R11 — reference data is not activity (CONDITION).** A `PRODUCT`, `CUSTOMER` or `CARRIER` event whose
values are unchanged must not cause any rule to be re-evaluated or any decision to be emitted.

**R12 — arrest propagation.** A node that has decided nothing must not cause downstream decision nodes
to run.

## What it must produce

A decisions file, one line per decision, in the order decided:

```
<eventNumber>,<DECISION>,<key>
```

`eventNumber` is the 1-based position of the triggering event in the scenario file (comments and blank
lines are not events and are not counted). `DECISION` is one of `RELEASE`, `HAZARD_BLOCK`, `OVERWEIGHT`,
`SLA_BREACH`, `STOCKOUT`. `key` is the `orderId` for the first four and the `sku` for `STOCKOUT`.

## Running it

```
java -cp <classpath> <your.Main> <scenario-file> <decisions-file>
```
One event per line, comma-separated, `#` starts a comment. **Do not hardcode a scenario** — your engine
will be run against scenarios written by someone else.

## Deliverables

1. The graph, built with a `FluxtionGraphBuilder`. Report your main class's fully-qualified name.
2. `Main` as above, writing the decisions file, and also writing the framework's audit log if given a
   third argument.
3. **JUnit tests**; `mvn clean test` green. Tests that would fail if a rule stopped firing.
