# Round 49 — idiomatic plain-Java components against the optimal Fluxtion instructions

Every "plain Java" arm so far has been given **Fluxtion's design with the annotations deleted**:
separate node classes, `boolean calc()` on each, constructor-injected parents, a per-node audit sink.
No Java author would ship that. It made the plain arm re-implement a dataflow runtime against a
library shaped for one — the worst of both worlds, and the owner's standing objection to the series.

This round fixes it. **Same five vendors, same twelve figures, same business requirements — but the
plain-Java libraries are built the way a Java author would build them.**

## What "idiomatic" means here

| | Fluxtion-shaped (rounds 45–48) | idiomatic (this round) |
|---|---|---|
| granularity | one class per figure | **one class per component** |
| dispatch | `boolean calc()` per node, ordered by the consumer | **`onTick(...)` computing the component's figures internally, in order** |
| dependencies | constructor-injected node references | **constructor-injected component interfaces** |
| tracing | per-node `Audit.SINK` | **the component records its own figures** |
| ordering | the consumer derives it | **the author writes it, once, inside the method** |

The consumer's job becomes: construct five components in dependency order and call them. No
topological sort, no trigger sets, no dirty flags.

## What this should cost, and what it should lose

**Cheaper to integrate** — encapsulation does that, and the measurement will say by how much.

**But three of the six business requirements become the consumer's problem again**, because a coarse
component cannot express them across a boundary:

1. **The arrest.** `capital` cannot be triggered by `risk`'s limit detector — the detector is not
   visible. `capital` must *ask* `risk` whether it breached. That is polling, not propagation.
2. **Exactly-once.** `Ewma` and `Streak` are inside components now; whether they advance once per
   event depends on how the consumer sequences the calls, and nothing enforces it.
3. **The audit trail.** "Which stages ran for this event" stops being answerable, because the
   component decides what to record and the consumer cannot see inside.

**That is the real trade**, and it has never been measured in this series. If the idiomatic arm is
correct and cheap, the framework's case rests on the third column alone. If it is cheap and *wrong*,
the case is much stronger than rounds 46–48 suggested.

## Held constant

The scoring scenario, the expected figures, the alert semantics, the supplied test, and the Fluxtion
arm's configuration — **cell O**, the measured optimum: indexed catalogue, 659-word manual, supplied
runner, bean file as the only deliverable.
