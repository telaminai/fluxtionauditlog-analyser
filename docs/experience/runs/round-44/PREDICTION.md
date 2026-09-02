# PREDICTION — round 44, the business brief

**Written and committed BEFORE either arm is launched.** No result seen.

## What changed, and why

Rounds 39–41 had a confound the owner identified and I confirmed: **my task text specified Fluxtion's
execution model** — recompute *"when, and only when, something it depends on has changed"*, run
*"exactly once"*, `false` *"stops that path"*. The plain-Java arm's dirty flags were it implementing my
specification. Worse, for that fixture none of it was needed for correct values, so those criteria
scored adherence to a model rather than correctness of an answer.

**This round states business requirements only and prescribes no design.** The brief ends: *"How you
wire the libraries together is entirely your decision."* The requirements are made load-bearing by the
fixture instead of by instruction:

| requirement | why ignoring it now gives a WRONG ANSWER |
|---|---|
| the breach count must be exactly right | `BreachCount` **accumulates**. Recompute-everything runs it on events where exposure did not change, and the regulator's number comes out too high. No ordering fixes it. |
| the operator changes the fee mid-run | needs behaviour indirection; a hard-wired calculation cannot satisfy it at all |
| an unowned config key must cost nothing | observable in the trace as work that should not be there |
| figures correct after every event | still needs dependency order |

`STRATEGY,premium` moves the fee from 1% to 5% — measured on the reference, `4129.81` → `23230.17`.
A `RATE` event does not change exposure, so **no breach may be counted for it**; that single row is
where a recompute-everything engine gives itself away.

## Design

| | |
|---|---|
| **Variable** | the composition mechanism. FX = five bean declarations + generator. VAN = plain Java, five jars. |
| **Held constant** | the five libraries' behaviour, published API, jars-only delivery, the brief, the scenario format, evidence-in-native-format, the model |
| **n** | 1 per arm |
| **Scored on** | a held-out 12-event scenario neither arm sees, including two stray config keys and two strategy swaps |

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| S1 | **Fluxtion scores full marks.** Five declarations; the only real work is the strategy service and the runner. | high |
| S2 | **The breach count is where the plain-Java arm loses marks if it loses any.** It is the one requirement that punishes running a stage when nothing changed, and nothing in the brief warns about it. | medium |
| S3 | **Both arms handle the strategy swap.** It is stated explicitly as a business requirement, so neither can miss it; only the plumbing differs. | high |
| S4 | **Both arms honour the unowned-key requirement**, because the libraries' adapters already return `false` and both arms will notice. | medium-high |
| S5 | Plain-Java consumer code ≥3× Fluxtion's. | medium |
| S6 | Plain Java needs ≥2× the `mvn` runs. | medium |

## The estimator warning, applied to myself

**I have under-predicted the plain-Java arm in three consecutive rounds** (39 R4, 40 Q3/Q4, 41 R4), and
across the project every quantitative miss but one has been pessimistic about the model's baseline
competence. The owner expects plain Java to struggle here. **I am deliberately holding S2 at medium
rather than high** for that reason: the arm has twice now built machinery I predicted it would skip.

## Falsifiers

- **If the plain-Java arm scores full marks**, the fixture still does not force the model to be
  load-bearing, and I will say so — that would be the third design defect found in this sub-series and
  it belongs on my ledger, not the arm's.
- **If Fluxtion drops marks**, the bean file or the strategy wiring is at fault, and that is the more
  useful finding.

## Not measured here

n=1. And **cost is not the headline**: round 41 showed the losing arm was *cheaper*, because being
wrong is cheap. Tokens are recorded, conditioned on correctness, and not led with. A performance
comparison is a separate run and is deliberately not mixed into this one.
