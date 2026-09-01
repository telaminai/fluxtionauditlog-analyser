# PREDICTION — round 19: does showing the annotation fix the bug it prevents?

**Committed before the agent launches.** The narrowest, most falsifiable test in the series.

## What changed

Round 18 had two cells build the fleet-telemetry engine and **both scored 6/8, failing P4 and P7 for
exactly one reason**: E3 fired on cycles where no telemetry arrived (a `Service` event, a `FleetRoster`
event), because a detector holding a state-node parent re-evaluates whenever that parent moves.

Since then the template demonstrates the fix in code (`ec0332f`):

- `ThresholdAlert` holds two parents — `sensorState` (trigger) and `@NoTriggerReference limitStore`
  (data only) — and the template's own audit log shows a *changed* `Limit` running `limitStore` and
  **not** `thresholdAlert`;
- a mutation-checked test, `aLimitEventDoesNotRunTheAlert`, fails if the annotation is deleted;
- the README carries a six-row table of every annotation that affects what runs, including
  `@TriggerEventOverride`;
- the pom is the two-pass build, so the bootstrap trap cannot occur.

Nothing else changed: same task, same hold-out, same oracle, same model, cold context.

## Baselines

| cell | context | `mvn` runs | hold-out | why it lost points |
|---|---|---|---|---|
| COLD (r18) | template, cold | 8 (4 failed) | 6/8 | E3 fired on cycle 18 |
| WARM (r18) | warm from problem 1 | 6 (3 failed) | 6/8 | E3 fired on cycles 17 and 18 |

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **W1** | **8/8.** The template shows the precise fix for the only bug both cells had, so P4 and P7 both pass. | medium |
| **W2** | **Fewer `mvn` runs than COLD's 8** — the two-pass pom removes one guaranteed failure class and the annotation removes a debugging cycle. I expect 5 or 6. | medium |
| **W3** | **It beats the WARM cell's 6/8 while being cold.** If a template can carry what a warm session carried, the artefact substitutes for session context — which is the whole question behind seeding. | medium-low |
| **W4** | **The agent cites the annotation explicitly**, rather than arriving at the behaviour by its own guard logic. That distinguishes "the template taught it" from "it would have got there anyway". | medium |

## Falsifiers, stated first

- **If E3 still over-fires, W1 is dead and so is the approach.** Demonstrating an annotation in working
  code, with a passing mutation-checked test and a table naming it, would then be shown NOT to transfer
  to a new problem — and the answer to seeding has to be a compiler diagnostic rather than any artefact
  the author reads.
- **If it scores 8/8 but never mentions the annotation**, the template did not teach it; something else
  did, and W4 catches that.
- **If build cycles do not drop**, the pom fix is worth less than measured elsewhere and W2 is wrong.

## The honest limit

n=1. A single cell cannot separate "the template taught it" from "this run happened to get it right".
If W1 holds, the follow-up is a second cell on a third problem, not a claim.
