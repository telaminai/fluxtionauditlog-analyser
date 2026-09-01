# Round 08 — NOTES and scoring

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `2656e15` **before launch**.
Task: [`TASK.md`](TASK.md) · Doc delta: [`DOC-DELTA.diff`](DOC-DELTA.diff) (57 lines).
n=4 per arm, seeded with `seed-project.sh`, builder 1.0.66.

## Result

**8 of 8 green. 8 of 8 business-correct in the final artefact. No measurable arm difference.**

Behavioural oracle — fires a fixed scenario at each generated processor and reads only the audit records
the task requires, so it is blind to how each agent structured things:

| run | SOUTH discharges below its 35% minimum? | nodes logging in a tariff-republish cycle | attempts |
|---|---|---|---|
| armA-1 | no | `tariffBook` only | 1 |
| armA-2 | no | `tariffBook` only | 1 |
| **armA-3** | no | `tariffBook` only | **5** |
| armA-4 | no | `tariffBook` only | 1 |
| armB-1..4 | no | `tariffBook` only | 1,1,1,1 |

Both business rules — *a battery below its zone minimum may never discharge*, and *a tariff republication
must not re-make a dispatch decision* — hold in every run, in both arms.

## Can the authoring loop zero in on the correct business solution? YES — and armA-3 is the evidence

armA-3 is the only run that went wrong, and it is the most valuable datum in either round because it
**recovered**:

1. Attempt 1 — a real generator diagnostic: *"use @AssignToField to resolve clashing types these fields:
   [shortWindow, longWindow]"*. Fixed in one step; the message named the annotation.
2. **Attempt 2 was GREEN and silently wrong.** The generated processor contained
   `new DispatchPolicy(..., Arrays.asList(new ZoneLimit(), new ZoneLimit()))` — **the operator limits had
   been thrown away.** A node reached only through a `List` constructor argument is regenerated with a
   no-arg constructor. `process-classes` reported SUCCESS.
3. The agent caught it **by reading the generated dispatch**, not from any error, and fixed it by
   `addNode`-ing each `ZoneLimit`; they then serialise as `new ZoneLimit("NORTH", 20.0)`.
4. Attempt 3 failed on the *previous* run's bad output, because `compile` precedes `process-classes` — so
   one bad generation wedges every later build, and the error points at generated code rather than at
   your builder. Fixed by deleting the generated directory.

**The loop's safety net is inspection, not the compiler.** The compiler said SUCCESS on the wrong build.
What caught it was CLAUDE.md §5 — *"read them to confirm your node was wired"* — which that agent called
*"the single most valuable instruction in the file."* That sentence, not any diagnostic and not the
section under test, is what produced a correct business outcome.

**A new silent hazard, worth a diagnostic:** nodes inside a collection constructor argument are
default-constructed unless separately registered. Silent, green, and it destroys builder-supplied values.

## Predictions scored: 3 of 6

| # | Predicted | Actual | |
|---|---|---|---|
| S1 | arm A ≥2/4 lose the limits; arm B ≤1/4 | **0/4 and 0/4** | ✗ |
| S2 | H2's loud half shows no arm difference | armA-3 hit it, fixed in one step | ✓ |
| S3 | `@OnParentUpdate` without `value()` in ≥1 arm-A run | **0** — all used `value()` | ✗ |
| S4 | H3 ≤1 violation per arm | 0 and 0 | ✓ |
| S5 | median attempts above round 07's 1 | median 1; **max 5** | ✓ (range only) |
| S6 | ≥1 arm-A agent `@FluxtionIgnore`s the limits | 0 | ✗ |

Misses pessimistic again — **fifth consecutive round**.

## My H1 premise was falsified, and I inherited the error from the canon

I designed H1 believing a `Map` config field would trip FLX-1009 and tempt `@FluxtionIgnore`. It does not:
**Fluxtion renders `Map<String,Double>` fine**, via `MapBuilder.builder().put("NORTH",20.0)…`. Two arm-A
runs proved it, one as a constructor arg and one setter-wired.

The belief came from the canon's own words — *"A field is a `Map`, `List`, `Set`… Fluxtion can't
reconstruct it"* — which is **too strong**. That is exactly the correction already made on the playground
page (*the rule is finality, not renderability*), and I then failed to apply it to my own task design.
Third time this session that reasoning from the canon rather than from behaviour has cost me.

## Recurring findings — all reported independently by 4+ agents, both arms

1. **`AGENTS.md` is stale and contradicts the repaired `CLAUDE.md`** — still presents Spring XML as the
   only route, and repeats a §1 claim `CLAUDE.md` explicitly flags as a past error. Several noted many
   harnesses read `AGENTS.md` **by default**. **This one is mine**: I repaired only `CLAUDE.md`.
2. **Runtime FQNs are missing** — `EventLogNode`, `EventLogControlEvent`, `@OnTrigger`, `@OnEventHandler`
   have no package anywhere, in the section that promises to remove exactly that risk.
3. **`@OnEventHandler` is never mentioned at all** — nothing documents how an event enters the graph.
4. **`@AssignToField` is undocumented in the injected set**, yet the task's first requirement needs it.
5. **The `regenerate` skill does not work here** — `./mvnw -Pgenerate-fluxtion package`, with no wrapper
   and no such profile.

## Two false hazards asserted by agents, both settled mechanically

- **`writeSourceToFile`** — armA-4 reported that omitting it silently produces no file. **7 of 8 never
  called it and all 8 generated.** False.
- Round 07's API-key claim was the same shape. **Self-reports keep inventing doc defects**, which is why
  the metric is mechanical.

## And two bugs in MY oracle, caught before reporting

The first version substring-matched decisions: `standingChargePerMWh` contains `CHARGE`, so it reported a
tariff re-dispatch on **every** run — including ones whose generated code demonstrably cannot. The second
hardcoded the event package, which armB-2 had legitimately placed under `com.acme.grid.event`.

Both were caught by disbelieving a result that contradicted the source. **Third proxy-metric failure this
session** — the discipline that keeps working is: when a metric disagrees with the artefact, suspect the
metric.

## What to do next

1. **Fix `AGENTS.md`** — make it a pointer to `CLAUDE.md`, or delete it. Highest-value, recurs 8/8, mine.
2. **Add the runtime FQNs and `@OnEventHandler`** to §4.0's snippet — the cheapest remaining gap.
3. **Fix or delete the `regenerate` skill.**
4. **Ask upstream for a diagnostic** on nodes inside collection arguments being default-constructed.
5. **§6 still unproven after two rounds.** Both rounds show zero defect base rate in the control arm. On
   the protocol's own bias toward deletion it should move to the fetched resources rather than stay in the
   injected set, where it is charged against every turn.
