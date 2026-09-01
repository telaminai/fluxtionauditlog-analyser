# PREDICTION — round 13: does the advantage scale?

**Written and committed BEFORE any agent is launched.**

Round 12 measured an 8-node graph and found a 5.9× cost advantage (Fluxtion+Haiku vs vanilla+Opus) and a
change cost of 0.79× the build. It could say **nothing** about the owner's central claim — that vanilla's
cost grows non-linearly with graph size — because every run was the same size. This round varies size.

It also fixes the loop defect round 12 exposed: agents read generated source to learn a dispatch order
their own audit log already stated, because `CLAUDE.md` told them to and because **no task ever asked for
tests**. This round requires tests and ranks the evidence channels explicitly (`LOOP.md`).

## Design

| | |
|---|---|
| **Variables** | framework (Fluxtion / vanilla) × graph size (~20 / ~50 nodes) |
| **Held constant** | the behaviour spec — **byte-identical across arms and sizes** (verified by hash); model (**Haiku, all four cells**); scaffold; JUnit available; the `LOOP.md` evidence ranking |
| **Differs by arm** | only the deliverable's build mechanism. The vanilla task **never names the framework** — round 12's confound, fixed structurally by composing every task from a shared core |
| **n** | **1 per cell**, 4 agents, fresh context |
| **Task** | market-abuse surveillance. 7 event types, pathways of differing length, 6 detectors (20) / 18 detectors + scoring + escalation (50) |

**Why this task.** Three properties were chosen to be cheap in a derived-dispatch engine and hand-built
without one: **S8** (a record must separate `detectorsTripped` / `evaluatedNotTripped` / `notEvaluated`),
**S10** (`path` and `pathLength` per cycle), and **S3** (detectors arrest propagation). Fluxtion's audit
log states all three. Vanilla must build the machinery that knows them.

## The owner's framing, adopted as the null

> *"The test cases will force the vanilla Java to correctness."*

Taken as the null hypothesis: **with tests mandatory, correctness converges and only cost separates the
arms.** Predictions below are written against it — P1 concedes correctness parity at 20 nodes, and the
claim is relocated onto cost, iterations, and whether the cheap model holds at all at 50.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **P1** | **At 20 nodes both arms reach green `mvn test`.** Tests do force vanilla to correctness. Correctness is not the discriminator at this size. | high |
| **P2** | **At 50 nodes, vanilla+Haiku does not.** It either fails to reach green, or reaches it having dropped rules — fewer than 10 of S1–S10 evidenced, or detectors stubbed. Fluxtion+Haiku reaches green with all ten. **This is the headline and the boldest claim here.** | medium |
| **P3** | **The scaling is the result.** 20→50 is 2.5× the nodes. Fluxtion costs **under 2×** its 20-node tokens; vanilla costs **over 2.5×**. Sub-linear vs super-linear. | medium |
| **P4** | **The mechanism is the test-fix loop.** Vanilla needs more `mvn test` runs to green than Fluxtion at both sizes, and the *gap widens* with size. If P3 holds and P4 does not, my explanation is wrong even if the number is right. | medium |
| **P5** | **The crossover lands between the sizes.** At 8 nodes vanilla was *cheaper* to build (44k vs 86k). I predict **vanilla still cheaper at 20, Fluxtion cheaper at 50.** | medium-low |
| **P6** | **The loop fix works: the Fluxtion arms read no generated source at all**, citing tests and the audit log instead — and any arm that does read it states why. | medium |
| **P7** | **S5 (paperwork) is violated by nobody.** Sixth consecutive round of this prediction; never once violated. | high |
| **P8** | **S8 and S10 are where vanilla bleeds.** ≥1 vanilla arm gets `detectorsNotEvaluated` or `pathLength` wrong, or hand-maintains a list that drifts from what ran. Fluxtion gets both from the log. | medium |

## Falsifiers, stated up front

- **If vanilla+Haiku ships 50 nodes green with all ten rules evidenced, P2 is dead** and the honest
  conclusion is that the framework's advantage is cost-only, not capability — a much weaker claim than
  the one round 12 tempted me toward.
- **If Fluxtion's tokens grow super-linearly too**, P3 is dead and the "scales non-linearly" claim is
  the owner's intuition rather than a measured property. I have no data for it today and said so.
- **If both arms sail through 50 nodes**, the task is not hard enough and the round measures nothing.

## Honest limits

**n=1 per cell.** This cannot establish significance and I will not claim it does. What n=1 *can* do is
show a size **trend within an arm** — two points per arm — and detect a capability cliff if one exists.
A cliff at 50 nodes in one arm and not the other is worth more than a token ratio, and is the thing to
watch. If the direction looks real the follow-up is n≥3 at 50 nodes only, where the signal is.

**I write both the task and the docs**, so a task whose hazards my prose happens to cover is teaching to
the test. S8/S10 were chosen because they favour derived dispatch — that is the hypothesis, stated
openly, not a neutral task.
