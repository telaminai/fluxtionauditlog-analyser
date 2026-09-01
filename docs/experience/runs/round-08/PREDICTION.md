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

- ~~n=3 is underpowered~~ **SET TO n=4 per arm (owner).** 8 agents. That improves *precision* over
  round 07 without pretending to settle it — n=4 still cannot establish significance, and a narrow split
  will be reported as suggestive, not proven.
  **It does not fix sensitivity, and those are different failures.** Round 07 failed on sensitivity — the
  base rate of the defect was zero, so more samples would only have bought a more confident null. Sample
  size separates signal from noise; task complexity is what creates signal in the first place. Both were
  raised here, and the ordering matters: without the harder hazard, more samples would only have produced
  a more confident null.
- **Complexity must be AIMED, not just added.** More nodes and more arithmetic makes agents fail for
  reasons no document addresses — both arms fail equally and the extra difficulty is noise. H1 is targeted
  precisely because the canon's own triage walks an author into it.
- **Eight samples of one model is not eight independent authors.**
- **I write the task and the docs**, so a task chosen to be answerable by my prose is teaching to the
  test. The protocol's answer is a held-out task that never drives an edit; this round does not have one
  yet, and that is a gap I am naming rather than hiding.

**The design change that matters.** Round 07 failed to discriminate because the model's prior was
**correct** — it already knew `@NoTriggerReference`. A doc can only earn its place where the author would
otherwise be wrong. So round 08's primary hazard is chosen where **the compiler's own diagnostic pushes
toward the wrong repair**:

> `DispatchPolicy` needs two **operator-set limits** — minimum charge 20% in NORTH, 35% in SOUTH — that
> come from the build, not from any event. However the author models them (two `int`s, a map, a config
> object), the generator must reproduce them, and the shapes that read most naturally are exactly the ones
> the canon's triage tells you to annotate away: *"A field is a `Map`, `List`, `Set`… annotate
> `@FluxtionIgnore`"*. Do that and **the build goes green and both limits are silently gone** — every
> battery's minimum becomes zero and the "may never DISCHARGE below minimum" rule stops holding.
>
> The canon's advice is correct for node-local state and destructive for builder-supplied configuration,
> and it does not distinguish them. That distinction is arm B's section, and only that.

Prior knowledge says "annotate it, that's the fix". The diagnostic says the same. Only the distinction
between *derived local state* and *builder-supplied configuration* gets it right, and that distinction is
exactly what arm B's section carries and arm A's does not.

## Design

| | |
|---|---|
| **Variable** | arm A = repaired `current/` doc set. Arm B = identical **plus** the silent-failure section (config-vs-state, plain-reference-is-a-trigger, one-`@OnTrigger`, same-typed-parent disambiguation). |
| **Held constant** | task, scaffold (seeded with `seed-project.sh` this time), builder 1.0.66, model, instructions |
| **n** | **4 per arm**, fresh context, parallel — 8 agents |
| **Task** | grid battery dispatch, ~8 nodes. Hazards are **not** stated as rules. |

### Hazards planted, and how each is scored — all mechanical, from generated source

| | hazard | fails how | metric |
|---|---|---|---|
| **H1** | builder-supplied operator limits (20% NORTH / 35% SOUTH) | **silent** — `@FluxtionIgnore` discards them | do the literals `20` and `35` survive into the generated construction? |
| **H2** | two constructor params of the **same type** | **loud** (FLX-1001) then **silent** — `@OnParentUpdate` without `value()` binds non-deterministically | `@AssignToField` present, and `@OnParentUpdate` carries a `value()` |
| **H3** | a lookup that must not trigger dispatch | **silent** | can a tariff republish reach the dispatch node's output? *(measured by propagation, not by guard membership — round 07's metric was a proxy and produced a false positive)* |
| **H4** | one report record per cycle | **silent** | report method invocations per `handleEvent` |

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| S1 | **H1 is the discriminator. Arm A: ≥2 of 4 lose the operator limits. Arm B: ≤1 of 4.** | medium |
| S2 | **H2's loud half shows no arm difference** — both arms hit FLX-1001 and repair it. Diagnostics are arm-independent. | high |
| S3 | H2's **silent** half (missing `@OnParentUpdate` `value()` with two same-typed parents) appears in ≥1 arm-A run | medium |
| S4 | H3 repeats round 07: **≤1 violation per arm.** The model knows this one. | medium-high |
| S5 | Median attempts-to-green rises above round 07's 1 — the task is genuinely harder | medium |
| S6 | **≥1 arm-A agent applies `@FluxtionIgnore` to the operator limits and say the error message led them there.** The trap closing, in the agent's own words. | medium |

## The falsifier, again stated up front

**If arm A scores 4/4 on H1, the section earns nothing a second time and should be deleted rather than
kept** — this time with two rounds behind the conclusion instead of one. The protocol rewards deletion and
I will apply it.

Equally: **if arm B also loses the config value**, the prose does not work and needs rewriting, not
promoting. Either outcome is more useful than the section quietly surviving on assumption.
