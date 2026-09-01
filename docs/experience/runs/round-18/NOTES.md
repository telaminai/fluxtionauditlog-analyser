# Round 18 — warm context buys speed, not correctness

Tests the owner's thesis: *an LLM arrives with deep Java priors and near-zero Fluxtion priors, so what
docs and templates really do is close a context gap.* One agent built problem 1 (surveillance), then
problem 2 (fleet telemetry) in the same session. A fresh agent built problem 2 cold from the same
template. Every number re-measured independently; scored on a 21-event hold-out written before any
engine existed.

| cell | problem | context | pom | `mvn` runs | hold-out |
|---|---|---|---|---|---|
| T1 | surveillance | template, cold | old | **11** (3 failed) | **8/8** |
| **WARM** (same agent) | telemetry | **warm — had just built T1** | old | **6** (3 failed) | **6/8** |
| **COLD** (fresh agent) | telemetry | template only | **new two-pass** | **8** (4 failed) | **6/8** |

## The result

**Build cycles: warm 6, cold 8 — a 25% reduction, and the cold cell had the strictly better build.**
The two-pass pom that removes the bootstrap trap shipped after the warm agent started, so the confound
runs *against* the hypothesis and the real gap is wider than 25%.

**Correctness: identical, 6/8 each.** Warm context bought nothing here.

**Self-reported framework share of effort, same problem, same template:**

| | framework | ordinary Java |
|---|---|---|
| T1, problem 1 (first contact) | 40% | 60% |
| WARM, problem 2 | 50% | 50% |
| COLD, problem 2 | **60%** | 40% |

The cold agent found the framework harder than the warm agent on the identical problem. That is the
thesis in one line: **the gap being bridged is context, and context transfers within a session.**

> What carried: `@OnEventHandler` vs `@OnTrigger`, parents-are-fields, propagation-arrest, the graph
> builder calls, the audit wiring, lifecycle order. The warm agent said it would not have known
> parents-are-fields, the builder calls or the lifecycle ordering cold.
>
> What did not: every detector's domain logic. A consecutive-streak with reset and a staleness
> comparison cost full price.

## Transfer carried a bug across its validity boundary

E3 (inactive-vehicle reporting) is the one detector the warm agent called a **"1:1 transfer"** from
problem 1's D3. **It is the one that broke** — in both cells:

```
cycle 17: Service[vehicleId=V3]            <- no telemetry in this cycle
     e3InactiveReporting: { detector: E3, tripped: true }
```

A detector triggered by a state node re-evaluates on **every** propagation and must guard on whether
its own input moved this cycle. In problem 1 the pattern was safe by accident: orders always arrived
with the trigger. In problem 2 `Service` and `FleetRoster` also trigger it.

**Third and fourth independent occurrences** — the session author's reference implementation had the
identical defect in D1, fixed with a sequence guard. By the recurrence rule this is a real finding, and
it is precisely what T1 asked the template to demonstrate: a node that refuses to propagate on a
business condition. The template's `ThresholdAlert` always returns true, so the single idea the
framework turns on is described in prose and never shown in code.

The warm agent reused a pattern **without re-examining it**, because it felt already solved. That is
the cost of warm context, and it is worth stating alongside the benefit.

## My own instrument nearly produced a false result

The first scoring run gave COLD 5/8 against WARM's 6/8 — which would have read as "warm context
improves correctness". The difference was entirely my regex: it matched `raised: n, suppressed: m` in
that order, and COLD emits `suppressed` first when suppressing. Order-independent now, and both cells
score 6/8. Fourth proxy-metric defect caught in this series, all four by checking before reporting.

## What follows

1. **Put an arresting node in the template**, with the stale-input guard. Three independent authors hit
   this; it is the highest-value addition available.
2. **The build fix is worth more than the lesson it replaces.** The warm agent's single most valuable
   carry-over was "write `Main` after `process-classes`", worth 2 runs — which the two-pass pom now
   makes unnecessary. A warm agent on the current build should need roughly 4 runs.
3. **Session context is a real asset that currently evaporates.** The measured advantage is framework
   mechanics, which is exactly what a template or a well-aimed document can carry to a cold start —
   so the question worth asking next is how much of the warm agent's advantage a written artefact can
   recover.
