# Round 07 — NOTES and scoring

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `e0bc52f` **after launch, before any result**
(marked there). Task: [`TASK.md`](TASK.md). Doc delta: [`DOC-DELTA.diff`](DOC-DELTA.diff).
Environment: [`ENVIRONMENT.md`](ENVIRONMENT.md).

## Result

| run | build | attempts | SLA business rule | how it was satisfied | multi-`@OnTrigger` | audit |
|---|---|---|---|---|---|---|
| armA-1 | green | 1 | ok | `@NoTriggerReference` (prior knowledge) | – | yes |
| armA-2 | green | 2 | **ok** | `@OnParentUpdate` + `false` return | – | yes |
| armA-3 | green | 1 | ok | `@NoTriggerReference` (prior knowledge) | – | yes |
| armB-1 | green | 1 | ok | `@NoTriggerReference` (cites §6) | – | yes |
| armB-2 | green | 1 | ok | `@NoTriggerReference` (cites §6) | – | yes |
| armB-3 | green | 1 | ok | `@NoTriggerReference` (cites §6) | – | yes |

**arm A: 0/3 violated · arm B: 0/3 violated. NOBODY violated the rule.**

### The scorer was wrong, and an agent's report is what caught it

`score.py` first reported `armA-2` as VIOLATED, and **that was a false positive in my metric, not a
defect in the run.** The scorer asked *"is `slaThresholds` a trigger parent of `slaMonitor`?"* — a
**proxy**. The actual rule is *"can republishing a threshold produce a breach report?"*, and those are
not the same question.

`armA-2` makes the thresholds a trigger parent **on purpose** and then gates propagation:

```java
@OnParentUpdate("depotStock")
public void stockChanged(DepotStock changedStock) { stockMoved = true; }

@OnTrigger public boolean evaluate() {
    if (!stockMoved) return false;   // only the thresholds were republished
    ...
}
```

`guardCheck_depotReport()` is `isDirty_shippingCost | isDirty_slaMonitor`, so a `false` return stops the
cascade and no report is published. The rule holds. Its javadoc quotes the business rule back verbatim.

**The uncomfortable part, recorded because it inverts a rule I wrote in `PREDICTION.md`.** That file says
self-reports *"explain a result, they never establish one"*. Here the mechanical metric was wrong and the
agent's report was right — reading it is the only reason the error was caught. The rule should be:
**a self-report cannot establish a result, but it can falsify a metric**, and a disagreement between the
two is a signal to re-examine the metric first.

## Predictions scored: 3 of 6

| # | Predicted | Actual | |
|---|---|---|---|
| R1 | arm A: **≥2 of 3** violate | **0 of 3** | ✗ |
| R2 | arm B: ≤1 of 3 violate | 0 of 3 | ✓ (vacuous — nobody violated) |
| R3 | ≥1 of 6 writes multiple `@OnTrigger` on a node | **0 of 6** | ✗ |
| R4 | both arms reach green | all 6 green | ✓ |
| R5 | build attempts similar across arms | 1,2,1 vs 1,1,1 | ✓ |
| R6 | ≥1 arm-B agent does not read the new section | **all 3 cited it explicitly** | ✗ |

**All three misses are in the same direction: I under-predicted the model's baseline competence.** That is
now the fourth consecutive round in this project where every quantitative miss was pessimistic. It has
stopped being a fact about any round and is a fact about the estimator.

## What this does and does not show

**THE STATED FALSIFIER FIRED.** `PREDICTION.md` said: *"If three agents with no mention of
`@NoTriggerReference` all produce a correct graph, then the model already knows this and the prose earns
nothing — and the honest conclusion is that the section should be deleted, not kept."* That is what
happened. Arm A scored 0/3 without the section.

**And it is stronger than that, because the control arm solved it by TWO different correct mechanisms** —
two agents with `@NoTriggerReference` from prior knowledge, one with `@OnParentUpdate` plus a gating
`false` return. The model is not one trick away from this; it has more than one route to it.

**The section therefore has not earned a place in the injected set on this evidence.** Its measured
benefit is zero, and injected material is charged against every turn of every session.

**The one thing it may still be worth, and it is narrow.** All three arm-B agents cited §6, and
two credited it specifically for the **FQN** — that `@NoTriggerReference` is in `runtime.annotations` and
not `runtime.annotations.builder`. armB-1: *"I'd have got that import wrong from memory."* That is a
smaller claim than "it taught the concept", and it is the part worth keeping if the section is ever
trimmed.

## The finding that outranks the variable — and it RECURS

**All six agents, in both arms, independently reported that `CLAUDE.md` §4 documents the wrong
authoring route.** §4 "Adding a node: THREE requirements" is written entirely around the Spring XML route
— `application-context.xml`, `fluxtionSpringConfig.nodeBeans` — while the pom binds the `scan` goal and
the task needs a `FluxtionGraphBuilder`, which **`CLAUDE.md` never mentions at all**. Two of its three
requirements are inapplicable; only §4.3's `transient` rule transfers, and it is the one that matters,
buried under two that misdirect.

Consequence measured, not guessed: every agent recovered the API by running `javap` against jars in
`~/.m2`. armA-1 named the risk exactly — *"that is luck: they were there because this machine has built
Fluxtion before."* On a clean machine, with the canonical links unreachable, §4 leads nowhere.

By the protocol's recurrence rule this **is** a defect, and it is larger than the one under test.

## Three agents reported a doc defect that does not exist

armA-1, armB-2 and armB-3 all reported that `CLAUDE.md`'s API-key statement was wrong — that regeneration
succeeded *"with no key at all"*.

**Tested: hide `~/.fluxtion/fluxtion.apiKeyFile`, rebuild → `API key is not configured`, no output.** The
key was present the whole time and used silently. Three independent agents asserted the opposite, in the
section of the report that asks what was wrong, without testing it.

**The owner's rule, which explains both halves** (2026-09-01): *a key is needed if the compiler is not on
the classpath; with the compiler on the classpath, no key is needed.* This template has
`fluxtion-builder` but not the full local compiler, so generation goes remote and the key is required —
which is why the test failed without it. The agents generalised from a build they had not varied.

**So `CLAUDE.md` is not wrong, it is unconditional where the truth is conditional.** The fix is not to
delete the sentence but to state the condition, because "you need a key" and "you don't" are both true
depending on what is on the classpath, and a reader hitting either one will otherwise conclude the doc is
broken.

Had the metric been "what did the agent say", this round would have produced a confident, unanimous, false
doc change — three agents, both arms.

## My harness defect, owned

The scaffold was hand-built rather than seeded with `docs/experience/seed-project.sh`, so `.claude/skills/`
did not exist and `CLAUDE.md`'s pointers to `regenerate` and `run-mongoose-server` dangled. Every agent
flagged it. It affects both arms identically so the comparison stands, but the "dangling pointer" findings
are mine, not the doc set's. **Use the seeder next round.**

## Deletion candidates — the protocol asks for these

- **§4.1 and §4.2** (the Spring XML procedure) were used by nobody and misdirected five of six. Either
  gate them behind "if this project uses the Spring route" or move them out.
- **§6 itself is now a deletion candidate**, on its own stated falsifier. It was read every time it
  was present, but the control arm did not need it. Keep only the FQN sentence, or move the whole
  section to the FETCHED resources where it costs nothing per turn, and leave the injected set lean.

## What to do next

1. **Fix §4 first.** It outranks the variable under test, recurs across both arms, and is cheap: name
   `FluxtionGraphBuilder`, give the package names (`…builder.compile.config`,
   `…builder.generation.config`), and state the builder-route rule — *a node is in the graph if it is
   `addNode`d or reachable by constructor reference from something that is.*
2. **Re-run with `seed-project.sh`**, so the skills exist and the dangling-pointer noise disappears.
3. **Rotate the task**, per the protocol, and hold one out.
4. Only then judge §6, with more rounds. One run's difference is not a result.
