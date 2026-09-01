# PREDICTION — round 09: behaviours only, full capability stack

**Committed before launch.** Task: [`TASK.md`](TASK.md). n=2 per arm, 4 agents.

## What changed, and why this round is not comparable to 07/08

Rounds 07 and 08 **named the nodes**. That is transcription, and it tests the half the model is already
good at — which is why both returned null. This task specifies **behaviour only**: no node list, no
dependency graph, no count. The decomposition is the agent's.

**Variable:** arm A = the repaired doc set (`AGENTS.md` now a pointer, runtime FQNs and `@OnEventHandler`
added, `regenerate` skill fixed). Arm B = **no docs at all** — task, pom, empty source tree.

**All four capability layers are available to both arms**, which is the point of the round:

| layer | catches | present |
|---|---|---|
| compiler diagnostics | syntax, structural rejection | both |
| static docs | idiom — the construct that fits the shape | **arm A only** |
| generated source | logical inspection: what will actually dispatch | both |
| audit log | behavioural facts: what actually ran, in what order | both |
| the analyser | coverage / topology / context over the run | both, path given |

Four of the six requirements (M3–M6) **cannot be shown from source** — they are claims about a run.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| T1 | Both arms reach a green build and produce a runnable `Main`. | high |
| T2 | **All 4 produce the audit log** — the task demands quoted evidence, so this is compelled rather than chosen. | high |
| **T3** | **But the log will be used to CONFIRM, not to DISCOVER: in ≥3 of 4 runs no defect is first found via the audit log.** Agents have consistently front-loaded verification (`javap`, reading generated source) and arrive believing they are correct. | **medium — this is the one I most want falsified** |
| T4 | **The analyser is used by ≤1 of 4.** It is an extra process for a question the agent thinks it can answer with `grep`. | medium-high |
| T5 | **M5 (report is last) is the most likely silent violation** — it is a dispatch-order property, invisible without the log, and it depends on getting every dependency right rather than on any annotation. | medium |
| T6 | **M6 (no mixed generations) passes in all 4 with no deliberate effort** — glitch-freedom is structural in Fluxtion, so the framework earns this one, not the author. | medium-high |
| T7 | Arm A and arm B both reach behaviourally correct outcomes; the difference shows in **idiom, not correctness** — arm A uses `@NoTriggerReference` for M3, arm B invents a boolean/flag gate that also works. | medium |
| T8 | Arm B needs **more `mvn` attempts** than arm A (no package names to copy). | medium |

## The measurement that matters most

Not pass/fail — **which layer caught which defect**. Every agent is asked to report it, and the answer is
checkable against their transcript. If the audit log catches nothing anywhere, the stack has one layer
that is ceremony rather than instrument, and that is worth knowing.

## Falsifiers

- **If arm B matches arm A on idiom as well as correctness**, the docs earn nothing on a *design* task
  either, and the deletion case gets much stronger than two null rounds already made it.
- **If T3 is wrong and the log finds real defects**, that is the strongest possible result for the
  audit-log-as-instrument claim, and it would justify pushing it much harder in the authoring loop.

## Standing caveat

n=2 per arm measures a **trajectory**, not a rate. And my quantitative misses have been pessimistic five
rounds running, so discount T5 and T8 accordingly.
