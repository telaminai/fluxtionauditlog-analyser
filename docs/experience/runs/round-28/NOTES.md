# Round 28 — the ceiling: 8/18 at double the spec

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `83c6c70` before launch. **4 of 5.**

28 event types, 24 rules, 13 decision kinds, 18 probes — roughly double the spec the harness scores
12/12 on. Same harness, same model (Haiku 4.5).

| | probes passed |
|---|---|
| `PASS` | 01 gold-release · 08 po-late · 09 unapproved-supplier · 11 hazard-bin · 13 refund-excess · 14 return-unknown · 15 sla-boundary · 18 unchanged-refdata |
| `FAIL` | 02 quarantine · 03 embargo · 04 re-release · 05 two-in-one-cycle · 06 stockout · 07 oversold · 10 bin-overflow · 12 quality-hold · 16 sla-breach · 17 dispatch-both |

**8 of 18**, with 3 tests written and the engine **self-reported as unfinished**:

> *"Due to token constraints, let me pivot… **Verified to emit decisions: None yet (all are shells)**."*

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| Y1 | does not score above 15/18 | 8/18 | ✓ |
| Y2 | at least 10/18 — degrades rather than collapses | **8/18** | ✗ |
| Y3 | losses cluster in two-quantity rules, not flat lookups | **exactly this** — see below | ✓ |
| Y4 | probe 05 fails despite the spec stating the rule in words | failed | ✓ |
| Y5 | cycles rise to 12–20 and more simplifications declared | **1 build**, many gaps declared | ✗ / ✓ |

## Y3 is the finding

Every passing probe is a **single-event lookup**: is this supplier approved, is this bin hazardous, does
this refund exceed, is this return unknown. Every failure needs **state carried across events**:
quarantine and embargo as gates, re-release after a transition, stock crossing zero, available versus
on-hand, a release timestamp recalled at PICKDONE.

The engine wired all 27 nodes and proved the orchestration — step 3 worked — and then ran out of room
before implementing the transition tracking. **The graph was not the limit; the per-rule state was.**

## What ran out was room, not capability

One `mvn` run, 3 tests, and an explicit stop. Its own §8 names the missing piece precisely:

> *"Tracking state transitions for EDGE rules — knowing 'it just became true' versus 'it's been true'
> requires storing previous state. The framework doesn't provide this automatically; I need custom
> tracking in each node."*

That is the same 20% of effort vanilla independently reported for the same job at half the size
(round 27). At 14 EDGE rules it is the dominant cost, and neither the framework nor the harness reduces
it.

## Where the ceiling is

| spec | rules | EDGE rules | score |
|---|---|---|---|
| round 21 | 3 detectors | 3 | 5/5 |
| rounds 22–26 | 12 | 5 | **12/12** |
| **round 28** | **24** | **14** | **8/18** |

So the harness holds to about **12 rules with 5 EDGE rules** and degrades between there and 24/14. The
degradation is not random: flat rules survive, stateful ones do not, and the engine stops rather than
shipping something wrong — which is a better failure mode than rounds 13–14, where cells shipped
confident broken engines.

**The obvious next experiment**, and it follows directly from Y3: give the harness a worked
edge-detector shape — a small reusable "became true this cycle" helper demonstrated once in the
template — and re-run this spec. If the ceiling moves, the limit was a repeated per-rule cost rather
than the model.
