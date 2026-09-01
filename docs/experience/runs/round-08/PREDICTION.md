# PREDICTION — round 08, a harder task

**Written and committed BEFORE the agents are launched.** Round 07's prediction was written after launch
and said so; this one is not, which is the process fix.

## Is this a better experiment than round 07? Partly — and where it is not, no task design fixes it

**Better, for three reasons:**

1. **Round 07's hazard was stated as an explicit business rule** — *"re-publishing a threshold must never,
   by itself, produce a breach report"* — which is a hint. It tested *can you satisfy a stated
   constraint*, not *do you notice an unstated hazard*. Round 08 states requirements in domain terms only.
2. **§4 is repaired for both arms.** It was the dominant recurring finding and confounded everything;
   removing it from the comparison is a real improvement.
3. **The new primary hazard is one where prior knowledge AND the diagnostic both point the wrong way.**
   That is the key design change and it is explained below.

**Not better, and these are structural:**

- **n=3 per arm is still underpowered.** If the true failure rate were 30%, n=3 detects it about half the
  time. Round 07's 0/3 vs 0/3 is exactly what an underpowered test looks like. Detecting a real difference
  wants n≥10 per arm; nothing about task design changes that, and this run cannot be called significant
  whatever it shows.
- **Six samples of one model is not six independent authors.**
- **I write the task and the docs**, so a task chosen to be answerable by my prose is teaching to the
  test. The protocol's answer is a held-out task that never drives an edit; this round does not have one
  yet, and that is a gap I am naming rather than hiding.

**The design change that matters.** Round 07 failed to discriminate because the model's prior was
**correct** — it already knew `@NoTriggerReference`. A doc can only earn its place where the author would
otherwise be wrong. So round 08's primary hazard is chosen where **the compiler's own diagnostic pushes
toward the wrong repair**:

> A node holds a builder-supplied `final int` config (`new DispatchPolicy(demand, prices, 20)`). FLX-1009
> fires: *"the fields [minChargePct] look like node-local state"*. The suggested repair — `@FluxtionIgnore`
> — **compiles green and silently discards the 20**. The policy then behaves as if the minimum charge were
> zero. Nothing fails.

Prior knowledge says "annotate it, that's the fix". The diagnostic says the same. Only the distinction
between *derived local state* and *builder-supplied configuration* gets it right, and that distinction is
exactly what arm B's section carries and arm A's does not.

## Design

| | |
|---|---|
| **Variable** | arm A = repaired `current/` doc set. Arm B = identical **plus** the silent-failure section (config-vs-state, plain-reference-is-a-trigger, one-`@OnTrigger`, same-typed-parent disambiguation). |
| **Held constant** | task, scaffold (seeded with `seed-project.sh` this time), builder 1.0.66, model, instructions |
| **n** | 3 per arm, fresh context, parallel |
| **Task** | grid battery dispatch, ~8 nodes. Hazards are **not** stated as rules. |

### Hazards planted, and how each is scored — all mechanical, from generated source

| | hazard | fails how | metric |
|---|---|---|---|
| **H1** | builder-supplied `final int` config | **silent** — `@FluxtionIgnore` discards the value | does the generated source construct with the literal `20`? |
| **H2** | two constructor params of the **same type** | **loud** (FLX-1001) then **silent** — `@OnParentUpdate` without `value()` binds non-deterministically | `@AssignToField` present, and `@OnParentUpdate` carries a `value()` |
| **H3** | a lookup that must not trigger dispatch | **silent** | can a tariff republish reach the dispatch node's output? *(measured by propagation, not by guard membership — round 07's metric was a proxy and produced a false positive)* |
| **H4** | one report record per cycle | **silent** | report method invocations per `handleEvent` |

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| S1 | **H1 is the discriminator. Arm A: ≥2 of 3 lose the config value. Arm B: ≤1 of 3.** | medium |
| S2 | **H2's loud half shows no arm difference** — both arms hit FLX-1001 and repair it. Diagnostics are arm-independent. | high |
| S3 | H2's **silent** half (missing `@OnParentUpdate` `value()`) appears in ≥1 arm-A run | medium |
| S4 | H3 repeats round 07: **0 violations in both arms.** The model knows this one. | medium-high |
| S5 | Median attempts-to-green rises above round 07's 1 — the task is genuinely harder | medium |
| S6 | **≥1 arm-A agent applies `@FluxtionIgnore` to the config field and says the diagnostic told it to.** That is the trap closing, in the agent's own words. | medium |

## The falsifier, again stated up front

**If arm A scores 3/3 on H1, the section earns nothing a second time and should be deleted rather than
kept** — this time with two rounds behind the conclusion instead of one. The protocol rewards deletion and
I will apply it.

Equally: **if arm B also loses the config value**, the prose does not work and needs rewriting, not
promoting. Either outcome is more useful than the section quietly surviving on assumption.
