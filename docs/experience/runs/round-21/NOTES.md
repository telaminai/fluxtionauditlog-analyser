# Round 21 — vanilla Java, nothing forcing its shape

The fleet-telemetry problem with every framework-shaped requirement removed: no audit-log format, no
logging conventions, no structural rules, no required classes. JDK only, *"write it the way you would
naturally write it."* Scored on the same five domain decisions the Fluxtion cells were scored on, plus
no-spurious-alerts.

| cell | `mvn` runs | tests | **domain decisions** | output tok | weighted |
|---|---|---|---|---|---|
| T4 — Fluxtion, template, no table | **5** (2 failed) | 26 | **5/5, none spurious** | 13,668 | 5.81 |
| VAN — vanilla, JDK only | 7 (1 failed) | 29 | **5/5, none spurious** | 15,965 | **4.65** |

**Correctness is a tie. Vanilla is 20% cheaper on weighted cost; Fluxtion needs 29% fewer build cycles.**

Vanilla produced the hold-out file exactly right on the first attempt:

```
9,E1,raised     14,E1,suppressed     15,E3,raised     16,E3,suppressed     19,E2,raised
```

## The measurement this round existed to take

Asked how its effort split between the detection logic and the surrounding machinery — dispatch,
ordering, state plumbing, observing what happened — vanilla answered **60% detection, 40% machinery**,
and itemised it: `FleetState` 110 lines, `Engine` 80, the event hierarchy and visitor 60, output and
`Main` 25.

**At three detectors the machinery is roughly 320 lines, and that is not enough for a framework to pay
for itself.** The argument that a framework absorbs the plumbing is true here and too small to matter.

## What vanilla itself says breaks at scale

Its §7, unprompted:

> *"Detector composition — what if E1 and E2 both fire on the same event? — requires ordering
> guarantees. **The order is hard-coded to E1, E2, E3**; at scale would need a priority/causality model."*
>
> *"State mutations during event processing can create subtle race conditions if async invocation is
> added later."*

That is precisely the property Fluxtion derives rather than hand-maintains, named by the arm that does
not have it. **It is a prediction about scale, not a measurement**, and this project has no measurement
of it: both attempts at ~50 nodes failed in both arms and measured the task instead.

## Honest reading

At this size — 3 detectors, ~8 classes, ~400 lines — **the framework does not pay.** Vanilla matches it
on correctness, beats it on cost, and its author reports the machinery as a minority of the effort. The
onboarding work in rounds 16–20 lowered Fluxtion's cost substantially (60% → 25% self-reported framework
difficulty, 8 runs → 5), and it still does not win here.

What has survived every round and is not contradicted by this one:

- **The log is an instrument the author did not build.** Vanilla's own §4 lists "code inspection" among
  the mechanisms it used to convince itself; the Fluxtion cells read a log the framework wrote. Round 14
  showed what the difference is worth — a hand-written trace was perfect in 28 of 28 cycles because the
  narrator was the thing being narrated.
- **Ordering is derived rather than typed.** Vanilla hard-coded E1, E2, E3 and said so.

Both matter more as node count and event-path count grow, and **neither is demonstrated to matter at
this scale.** The experiment that would settle it is a graph large enough that hand-maintained dispatch
order becomes a real cost — which is the measurement this project has repeatedly failed to obtain,
because both arms drown before they get there.
