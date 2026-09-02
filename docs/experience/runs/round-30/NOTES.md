# Round 30 — the order-dependent spec, and vanilla wins it

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `ccf77c9`. **2 of 5.**

Built after the round-27 tie, on the owner's point that the previous spec could not distinguish the
approaches: no rule read another rule's output, four of five EDGE rules had a single trigger, and
recompute-everything was correct and cheap. This spec has a five-deep chain, a diamond, once-per-node
evaluation, a halt gate, and after-event commits — and requires the engine to publish what actually ran.

| | decisions | O1 once per node | O2 no stale reads | O4 reverse commits | tests | `mvn` |
|---|---|---|---|---|---|---|
| **Fluxtion** | **8/8** | ✓ | ✓ | **✗ never emitted** | 1 | 3 |
| **vanilla** | **8/8*** | ✓ | ✓ | **✓** | 7 | 4 |

\* vanilla numbers events from 0 where the spec says 1-based — one defect, uniform across all probes.

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| W1 | Fluxtion beats vanilla on the order checks | **vanilla 3/3, Fluxtion 2/3** | ✗ |
| W2 | vanilla passes O1 and O2 anyway | it did, by hard-coding the five-step order | ✓ |
| W3 | vanilla fails O4 | **vanilla passed it; Fluxtion is the one that failed** | ✗ |
| W4 | decision scores within two probes | 8/8 and 8/8 | ✓ |
| W5 | the glitch probes separate them | both passed g1 and g5 | ✗ |

## The framework did deliver what it claims — and it was not enough to win

Fluxtion's own §7, unprompted, is the clearest statement of the division in the whole project:

> **O1 (one evaluation per node per event):** *"Fluxtion provides this free."*
> **O2 (no stale reads):** *"Fluxtion provides this free. Topological evaluation order ensures parents
> complete before children run."*
> **O3 (halting):** *"I implemented this. Fluxtion doesn't provide halt semantics."*
> **O5 (unchanged reference data):** *"I implemented this."*

So the two properties this round was built to test **are** free on the framework, and vanilla had to
construct them: its effort split is **20% rules, 70% ordering and glitch avoidance**, of which *40% is
evaluation-order enforcement alone*. Against round 27's spec the same arm reported 25/75 with ordering
barely mentioned. **The spec change worked — ordering became the dominant cost.**

It just did not become an *error*. At five levels with a fixed shape, a hand-ordered pass is achievable,
and vanilla achieved it. Its own §7 says what it expects to break: at ten levels *"performance would
degrade… a proper DAG-based topological sort would be more efficient, but less reliable without full
dependency tracking."* That is a prediction about scale this project has still not measured.

**And Fluxtion lost the round on the one property it did not get free.** It skipped `@AfterTrigger`
entirely and emitted no commits — declaring it in §8 as a gap. The framework has the mechanism; the
author did not reach for it.

## Two more instrument defects, both found before reporting

- **Every expected file omitted `CONCENTRATION`.** It fires whenever one instrument exceeds 60% of a
  book, which for a single-instrument book is always. Recomputed by hand.
- **The order checker demanded globally non-decreasing depth and globally descending commits.** Wrong
  when an event touches independent subgraphs — a `PRICE` affecting two books may evaluate one book's
  chain then the other's. It scored a correct engine as failing. Now checks depth per subject, and that
  commits are exactly the reverse of the evaluation sequence.
- **And O4 passed vacuously** for an engine that emitted no commits at all. Absence is now a failure.

Sixth, seventh and eighth. Every one was found by checking a surprising number rather than reporting it,
and every one would have produced a false headline — two against vanilla, one for Fluxtion.

## Standing, after three specs

**Three specs, three ties or losses on correctness.** Round 21 (3 detectors), round 27 (12 rules), round
30 (order-dependent graph). The falsifier stated before this round said a third tie would be a result
rather than a miss, and this is worse than a tie: vanilla scored higher.

What is established: **the framework genuinely provides O1 and O2, and vanilla genuinely pays for
them — 40% of its effort.** What is not established is that paying that cost produces worse outcomes at
any size this project has reached. The cost is real; the consequence is not yet visible.
