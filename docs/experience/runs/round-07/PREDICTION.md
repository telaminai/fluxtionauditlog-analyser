# PREDICTION — round 07, the two-arm authoring-accuracy run

**Written AFTER the six agents were launched and BEFORE any result arrived.** That is a process miss and
it is marked rather than hidden, the same way round 03 marked its own contamination. It is not
outcome-contaminated — I have seen nothing — but it was not committed before the run started, which is
what the protocol asks. Recorded so the next reader can discount it appropriately.

## Design

Run under `spec-authoring-experience.md`'s rules for further rounds: **one variable, n≥3 in parallel, a
preflight first.**

| | |
|---|---|
| **Variable** | the injected doc set. Arm A = `docs/experience/current/CLAUDE.md` verbatim (157 lines). Arm B = identical **plus one 33-line section**: "Failures that compile, run, and are still wrong" (`@NoTriggerReference`, one-`@OnTrigger`-per-node). |
| **Held constant** | the task, the scaffold, the builder (1.0.66), the model, the instructions |
| **n** | 3 per arm, fresh context each, run in parallel |
| **Task** | a 6-node parcel-depot graph. **No errors are planted** — unlike the earlier loops, the agent writes its own code and we measure whether the defects OCCUR. |

**The key design choice: the trigger requirement is stated as a business rule, never as an annotation
hint.** The task says *"Re-publishing an SLA threshold must never, by itself, produce a breach report.
Only a change in what is actually waiting can do that."* An agent can satisfy that only by knowing that a
plain field reference is a trigger. Nothing in the task names `@NoTriggerReference`.

## Metric — mechanical, from the generated dispatch, not self-report

1. **Primary:** does `guardCheck_slaMonitor()` contain `isDirty_slaThresholds`? If yes, the stated
   business rule is violated in the emitted graph.
2. **Secondary:** does any node have ≥2 `@OnTrigger` methods invoked within one dispatch?
3. Did the build reach green, and in how many `mvn` attempts?
4. Was the audit log actually enabled?

Self-reports are collected too, but only to explain a result — never to establish one.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| R1 | **Arm A: ≥2 of 3 violate the primary metric** — `slaThresholds` ends up a trigger parent of `slaMonitor`. The doc set does not mention `@NoTriggerReference` at all, and the default is the surprising one. | high |
| R2 | **Arm B: ≤1 of 3 violate it.** This is the whole hypothesis. | medium |
| R3 | ≥1 of the 6 writes multiple `@OnTrigger` methods on one node. More likely in arm A. | medium |
| R4 | **Both arms reach green.** The loud failures are diagnostic-covered and arm-independent. | high |
| R5 | **Build-attempt counts are similar across arms** — the docs change authoring, not compiler behaviour. A big divergence means something other than the variable moved. | medium-high |
| R6 | At least one arm-B agent will **not read** the new section and will hit the defect anyway. Reaching a fix requires opening a section nothing prompts you to open. | medium |

## The honest limit, stated before the numbers exist

**n=3 per arm cannot establish significance.** A 3–0 versus 0–3 split is suggestive; anything narrower is
noise, and I will say so rather than dress it up. Rounds are non-deterministic by design — this repo's own
protocol says a finding counts only when it **recurs**.

So the useful output of this round is a **direction plus a defect list**, not a result. If the direction
looks real, the follow-up is more rounds with rotated tasks, which is what the protocol was built for.

## What would falsify the whole hypothesis

Arm A passing cleanly. If three agents with no mention of `@NoTriggerReference` all produce a correct
graph, then the model already knows this and the prose earns nothing — and the honest conclusion is that
the section should be **deleted**, not kept. The protocol explicitly rewards deletion.
