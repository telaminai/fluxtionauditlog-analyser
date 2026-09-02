# Round 50 — a shared stateful instance, and a subsystem with internal depth

Round 49 asks whether idiomatic plain-Java components can meet the same requirements. This round asks
the question that round 42 identified and the series has never tested:

> **For plain Java, instance sharing and encapsulation are in direct conflict.** Move to components
> and the consumer can no longer share an internal node, because it never sees one — each component
> builds its own, and two copies of a stateful thing diverge.

## The addition: `Positions`, a shared stateful instance

A new figure, `netPosition`, accumulated per symbol across trades. **It is stateful and
order-sensitive**, and — this is the point — **two components need the same one**:

| component | why it needs `netPosition` |
|---|---|
| `Risk` | exposure is now `netPosition × mid × (1 + score/1000)` |
| `Capital` | the buffer is scaled by the absolute net position |

`netPosition` must advance **exactly once per TRADE**. Advance it twice and every downstream figure
is wrong; advance it zero times and they are stale.

### Why this is the decisive fixture

| route | what happens |
|---|---|
| **Fluxtion** | `Positions` is one node. `Risk` and `Capital` both take it as a constructor parameter; the generator resolves both references to **one instance** (verified in round 42: 25 nodes, each constructed exactly once) and dispatches it **once per event** by construction. |
| **idiomatic components, vendor anticipated sharing** | `Positions` is a sixth component the consumer constructs and passes to both. Works — *if the consumer calls it exactly once, first*. Nothing enforces either. |
| **idiomatic components, vendor did NOT anticipate sharing** | `Risk` and `Capital` each construct their own `Positions`. Both compile. Both run. **They diverge silently**, and every figure below them is wrong. |

The third row is the realistic one, and it is the failure round 42 predicted without ever measuring.
**The fixture ships it that way** — each component constructs its own `Positions` internally — and
also publishes a constructor that accepts one, so a consumer who *notices* can share it. Noticing is
the test.

## The addition: internal depth in `pricing`

`Pricing` grows from two figures to five, with a diamond inside it: `curve` and `skew` both read
`adjusted`, and `quote` reads both. Internal to the component, so an idiomatic author orders it by
hand in one method; under Fluxtion it is four nodes and the order is derived.

This tests nothing about the boundary — it tests whether **within-component ordering** is genuinely
the easy case, as round 49's design argued. If the idiomatic arm gets the diamond wrong, that claim
was wrong.

## What is measured

The same architecture-neutral scoring as round 49: every published figure's value after every event,
the alerts, and the stateful counters — now including `netPosition`.

Plus one new question to both arms: **how many instances of `Positions` does your engine contain?**
The answer is checkable from the audit trail, because a doubled instance produces a doubled figure.

## Predictions, to be committed before either arm runs

Written when the round is built, not now. But the falsifier is stated in advance: **if the idiomatic
arm shares the instance correctly and gets `netPosition` exact, then encapsulation and sharing are
not in conflict after all**, round 42's finding was wrong, and the framework's remaining case is the
audit trail alone.
