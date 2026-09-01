# PREDICTION — round 15: how fast does the best documentation reach CORRECTNESS?

**Written before any result.** The learner cell was launched first and has seen no expectations; the
metric below and the hold-out were both fixed before it could report.

## The question changed, and so did the metric

Rounds 7–14 asked whether a framework wins. This round asks a narrower, more useful thing:
**what documentation gets an author to correct behaviour in the fewest build cycles and tokens?**
Fluxtion only. No comparison arm.

**Correctness is scored on input the author never sees, and nothing else counts.** Round 14 settled that
`mvn test` green is a bad proxy: `van50f-2` passed **29 of its own tests** and got **0 of 5** behavioural
checks right, because its tests validated dispatch and asserted nothing about the domain. So:

| | metric |
|---|---|
| **primary** | hold-out score — 8 checks, 21 events, written before any engine existed, invisible to every agent |
| **secondary** | `mvn` runs to green; measured output tokens |
| **the number that matters** | **hold-out points per 1,000 output tokens** — correctness reached per unit of cost |

A cell that compiles, passes its own tests and detects nothing scores **zero** on the primary metric.
That is deliberate.

## Design

The owner's proposal, adopted because it removes the confound I have flagged since round 07 — *I write
the docs, so a task my prose happens to cover is teaching to the test.*

1. **L1 (learner)** builds the small task with the **existing** doc set, keeping a running note of every
   blocker, wrong guess and wasted build cycle. It then writes **`LEARNED.md`** — the guidance it wishes
   it had had, optimised for getting the next person to *correct behaviour* fast.
2. **V1 (validator)**, fresh context, builds the **same** task with the existing docs **plus**
   `LEARNED.md`. Same scaffold, same model, same hold-out.
3. The doc's value is the delta between them on the primary metric.

**Task**: requirements only. 4 event types, 3 detectors, 7 rules. **No node count, no structural
requirement** — rounds 13 and 14 both broke on my structural demands and are not repeated.
**Baseline**: round 13's `fx20` — same model, same doc set, comparable scale: 7 `mvn` runs, 2 failures,
13,495 output tokens, and an **empty `logs/` directory** while citing audit evidence for eight rules.

## Ground truth, established independently before the learner reports

Verified against the 1.0.14 runtime jar and by building and running a two-node graph — not inferred:

| fact | how it fails if you get it wrong |
|---|---|
| `cfg.addEventAudit(LogLevel.INFO)` at **build** time is mandatory | **silent**: `setAuditLogProcessor` then receives nothing, no error, empty log |
| `flow.setAuditLogProcessor(rec -> …)` before `init()` | no log |
| `onEvent(Object)` — one method, not `onOrder()` | compile failure |
| lifecycle is `init/start/stop/tearDown`; there is no `shutdown()` | compile failure |
| running needs `mvn dependency:build-classpath` | `NoClassDefFoundError` |
| builder FQNs are `builder.compile.config.FluxtionGraphBuilder`, `builder.compile.config.FluxtionCompilerConfig`, `builder.generation.config.EventProcessorConfig` | compile failure |

The first row is very likely why `fx20` shipped an empty log. `LEARNED.md` will be scored against this
list rather than believed.

**Recorded against myself:** I got those builder FQNs wrong by inference while `CLAUDE.md` had them
right at lines 124–126. The doc was not the problem; not reading it was. That is the same failure this
whole series keeps measuring in agents.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **R1** | **`LEARNED.md`'s top item is the audit wiring** — `addEventAudit` and/or `setAuditLogProcessor`. It has blocked three agents across two rounds and it fails silently. | medium-high |
| **R2** | **`LEARNED.md` misses at least one of the six ground-truth facts**, because a learner only documents what it personally tripped over. The gaps are as informative as the content. | medium-high |
| **R3** | **L1 scores 5–6 of 8** on the hold-out. Three detectors with explicit numeric trip conditions is far easier than round 14's eighteen; the likely losses are G7 (genuine suppression vs merely reporting it) and one detector boundary. | medium |
| **R4** | **V1 beats L1 on points-per-1,000-output-tokens by ≥25%**, and needs **strictly fewer** `mvn` runs. This is the whole hypothesis. | medium |
| **R5** | **V1 does not beat L1 on raw hold-out score by more than one point.** Documentation buys speed to correctness, not a higher ceiling — the ceiling is set by the model. | medium |
| **R6** | **L1 writes a real audit log**, unlike `fx20`, because the task names the log as the feedback channel and requires `Main` to write it to a given path. | medium-high |

## Falsifiers

- **If V1 is no cheaper than L1**, agent-authored documentation does not transfer, and the honest
  conclusion is that this loop does not work — which would be worth knowing before building more of it.
- **If L1 scores ≤3 of 8**, the task is still too hard for the model and the round measures the task
  rather than the docs. Start smaller again.
- **If both score 8 of 8**, the hold-out is too easy to discriminate and needs harder cases.

## Honest limits

n=1 per cell. A single learner's blockers are not the population of blockers, and `LEARNED.md` will be
shaped by one run's accidents. The loop is worth iterating only if R4 survives; if it does, the next
step is a second learner on a different task feeding the same document.
