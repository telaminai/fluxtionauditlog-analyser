# PREDICTION — round 14: force the structure, score with evidence the agent never sees

**Written and committed BEFORE any agent is launched.** Two owner challenges drive this round, and both
were confirmed against round 13 before it was designed.

**Challenge 1 — "van50 lies."** Correct. It reported 26 nodes and `D1Detector`…`D18Detector`; none
exist. It is one 637-line class with **27 hand-typed `path.add("...")` literals and zero dispatch
mechanism**. `van20` is the same: one 520-line class. Every Haiku vanilla arm at every size has written
a monolith. The only vanilla arms that ever built real node structure are round 12's two Opus runs
(8–9 node classes, rank-derived dispatch) at **8** nodes.

**Challenge 2 — "is the model reading the generated class?"** Also correct, and larger than expected:

| cell | share of tool-result intake that was generated source |
|---|---|
| fx-orch (the round-12 change agent) | **31.5%** |
| fx-local | 22.7% |
| fx50 / fx20 | 18.0% / 14.0% |

So round 12's "refactoring is nearly equal" was measured on a run that spent a third of its intake
reading machine output.

**A third thing fell out of chasing challenge 2, and it invalidates prior cost claims.** The token
figures quoted through rounds 9–13 were an unverified harness composite. Measured tokens:

| cell | output | cache-read in | weighted |
|---|---|---|---|
| fx50 | 19,894 | 9.91M | 10.92 |
| van50 | 13,721 | 7.32M | 8.02 |
| fx20 | 13,495 | 6.21M | 6.89 |
| van20 | 9,961 | 5.39M | 5.89 |

(weights: cache-read 1, fresh input 10, output 50.) **Round 13's claim that Fluxtion's penalty narrows
with scale was wrong — it widens: 1.17× at 20 nodes, 1.36× at 50.** From here cost is reported as
measured output and input, never the composite.

## Design

| | |
|---|---|
| **Cells** | `van50f-1`, `van50f-2` (vanilla, structure forced), `fx50f` (Fluxtion, forced), `fx50nr` (Fluxtion, forced, **forbidden from reading generated source**) |
| **Held constant** | behaviour spec (identical text), ~50-node scale, Haiku, JUnit, the structural gate, the CLI contract |
| **New: structural gate** | one class per node in `…surveillance.node`; **no string literal equal to a node name** in the emitter; `path` assembled by the dispatcher from what it actually invoked; adding a node requires no edit to the emitter. Greppable, and the agents are told it is checked |
| **New: hold-out scoring** | `Main <scenario> <log>`. A 24-event scenario and 9 expectations, written before any engine exists, **seen by no agent**. Self-grading ends |
| **Fixed** | S3 vs S6/S7 — the record is now explicitly exempt from S3 and driven by the cycle. This defect is round 09's M3/M4, reintroduced; it broke `fx50` in 12 of 14 cycles |

The oracle was validated against round 13's logs before use: its H2 check independently reproduced
`fx50`'s seven-distinct-terminals defect, found by hand. It is not vacuous.

## Predictions

**Standing correction to my own estimator.** Round 07's notes recorded that *every* quantitative miss
was pessimistic about the model's baseline; rounds 08–13 repeated it, and round 13's P2 died the same
way. These are adjusted upward for vanilla on purpose.

| # | Prediction | Confidence |
|---|---|---|
| **Q1** | **The crossover appears when structure is forced.** `van50f` weighted cost rises **≥1.4×** over unforced `van50` (8.02 → ≥11.2) and lands **at or above** `fx50f`. This is the round's central claim and the first like-for-like measurement in the series. | medium |
| **Q2** | **At least one vanilla arm fails the structural gate** — literals in the emitter, or nodes that are not classes — despite being told it is checked. | medium |
| **Q3** | **No arm scores 9/9 on the hold-out.** Unseen input is harder than self-authored evidence, and every cell has scored its own homework until now. | medium-high |
| **Q4** | **`fx50nr` costs less than `fx50f`** — a real share of the 18% was waste — **but needs at least one more build failure**, because part of it (the processor's `onEvent` API) was load-bearing. Both halves must hold or the prediction is wrong. | medium |
| **Q5** | **Fluxtion beats vanilla on H2 specifically** (one consistent terminal node in every record). That is the check a derived dispatcher gets free and a hand-built one must maintain. | medium |
| **Q6** | **S5 is violated by nobody.** Seventh consecutive round; never once violated. | high |

## Falsifiers

- **If `van50f` matches its unforced cost**, the structural gate is not binding and Q1 is dead — the
  monolith was never the reason vanilla looked cheap, and the framework's cost premium is real and
  unexplained. This is the outcome that would most damage the wedge argument.
- **If `fx50nr` is both cheaper and no more error-prone**, then the generated-source reading is pure
  waste, `CLAUDE.md:214` should be rewritten, and round 12's change-cost number should be re-measured
  under the ban before it is cited again.
- **If vanilla scores as well as Fluxtion on the hold-out**, then forced structure buys correctness
  parity and the remaining case rests on change economics alone — which is n=1 and now suspect.

## Honest limits

n=2 for vanilla, n=1 for each Fluxtion cell. This cannot establish significance. It can show whether
forcing structure moves vanilla's cost at all, which is a direction, and the hold-out makes the
correctness comparison independent for the first time.
