# PREDICTION — round 30: a spec where dispatch order is a correctness property

**Committed before launch.** Fluxtion and vanilla, same spec, same probes, Haiku 4.5 both.

## Why this spec exists

Round 27 ended in a 12/12 tie, and inspecting the vanilla engine showed why the tie proves little.
Three properties of that spec let a hand-written engine win by brute force: **no rule read another
rule's output**, four of five EDGE rules were triggered by exactly one event type, and recomputing every
order on every event was both correct and cheap. Ordered incremental dispatch is what a compiled graph
buys, and that task could not measure it.

This one is built so it can:

- **A five-deep derived chain.** position → mark → base → exposure → utilisation. Each reads the one
  before. Evaluating out of order yields a stale intermediate.
- **A diamond.** `RISK` reports a **mark** and a **utilisation** in the same line. The mark is two hops
  from a `PRICE`, the utilisation is five. An engine that evaluates the decision when the near path
  updates reports a fresh mark beside a stale utilisation — a glitch, visible as a wrong number.
- **Once per node per event (O1).** An engine that re-evaluates a node once per changed parent is
  detected directly.
- **Halting (O3).** A halted book must run nothing below the gate: no evaluation, no decision.
- **After-event commit (O4)**, in reverse dependency order.

**And the engine must publish what actually ran** — an evaluation file, one line per event, listing the
nodes that evaluated in order, each once, with commits appended. That is the audit log made a
deliverable, scored mechanically by `oracle/score-order.py`, which is validated against a good log and
a glitchy one before use.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **W1** | **Fluxtion scores higher than vanilla on the ORDER checks (O1/O2/O4).** These are what the framework derives and the hand-written engine must maintain. This is the whole point of the round. | medium |
| **W2** | **Vanilla passes O1 and O2 anyway**, because recompute-everything-in-a-fixed-order is glitch-free by construction, and the author will hard-code the five-step order. The chain is short enough to hold in the head. | medium |
| **W3** | **Vanilla fails O4** — commits in reverse dependency order is the one property with no natural expression in an if/else engine, and nothing forces it. | medium |
| **W4** | **The decision scores are close, within two probes.** Both engines will get the arithmetic right; the difference, if any, is in ordering. | medium |
| **W5** | **The glitch probes (g1, g5) separate them if anything does.** g1 crosses the diamond with one `PRICE`; g5 moves an `FX` rate five hops from the decision. | medium-low |

## Falsifier, and it is the important one

**If vanilla ties again on both scores, then at this size a compiled graph is not measurably better than
a careful `if/else` chain**, and the case for the framework rests on change cost and on scale beyond
what this project can measure — not on correctness. I have now written two specs that failed to
separate them; a third tie would be a result rather than a miss.
