# Achieving optimal performance with a Fluxtion processor

**Status** DRAFT for the Fluxtion docs site · **Item** M50/W9 · **Updated** 2026-09-06
**Evidence** [`round-58`](../experience/runs/round-58/NOTES.md) (~700 runs, 19 addenda) and
[`round-59`](../experience/runs/round-59/NOTES.md) (verification on generated code).
**Harness** `tools/bench/dispatch-bench.py` — every figure here was produced by it or by round 58.

---

## Read this first: every figure names its shape

The same processor source has measured anywhere from **1.4 to 29 ns per event** depending on
configuration and deployment shape. While round 58 was written up, **four successive drafts carried a
wrong headline figure** — each a true measurement of a different shape, quoted without naming it.

So: **a figure without its shape is not a result.** Every number below carries one.

---

## The configuration, in one place

Six switches. The first three are the ones that matter most, and each **changes behaviour** — that is
the point of them, not a side effect.

```java
// 1 — NO DIRTY FLAGS: every node fires on every event, unconditionally
@OnTrigger(failBuildIfMissingBooleanReturn = false)          // void trigger, no dirty flag, no guard
@OnEventHandler(failBuildIfMissingBooleanReturn = false)
config.setSupportDirtyFiltering(false);                      // and no dirty-flag machinery at all

// 2 — NO AUDITORS DOING WORK: supply a clock instead of reading the system clock per event
processor.onEvent(ClockStrategy.registerClockEvent(() -> myStreamTime));

// 3 — OPTIMISED RE-ENTRANCY: no wrapper on the event path, guard retained
config.setSupportReentrancy(false);

// 4 — no event buffering
config.setSupportBufferAndTrigger(false);

// 5 — no event-feed subscription (also removes an escape, see §Deployment shape)
config.setSupportSubscriptions(false);

// 6 — deployment shape: construct the processor inside the method that runs the loop (§below)
```

### 1 · No dirty flags

`setSupportDirtyFiltering(false)` plus void triggers. **Removes the guards.** A void trigger returns
no boolean, so there is no dirty flag to store, no guard to test, and no dirty-flag maps on the
processor.

**What you give up: conditional propagation.** Every node fires on every event. If your graph relies on
a node declining to propagate, this changes results, not just speed. Take it when the graph is a
pipeline that recomputes everything anyway.

### 2 · No auditors doing real work

Every generated processor carries the `Clock`, `NodeNameLookup` and `ServiceRegistry` auditors, and
calls them on every event. **Five of those six calls are free** — round 58 measured inherited no-op
default methods as indistinguishable from no call at all on every runtime, native included, so **do
not try to hand-optimise them away.**

The sixth is not free. `Clock.init()` sets `wallClock = System::currentTimeMillis`, and
`Clock.eventReceived` calls it **on every event**:

| | ns/event | events/sec |
|---|---|---|
| default clock (reads the system clock per event) | 29.08 | 34M |
| supplied `ClockStrategy` | **5.03** | 199M |

**The clock is 83% of the default cost on a JIT** — measured on a real generated processor
(round 59), and round 58 measured the same on all seven runtimes it tested (19.01 → 7.71 on Graal JIT).
**This is the lever you lose by doing nothing**, and it was undocumented until now.

Supply a stream time whenever your events carry their own. If they do not, you are performing an
ambient clock read inside the event path, which is also what makes a run non-reproducible — the
performance argument and the determinism argument coincide.

### 3 · Optimised re-entrancy

`setSupportReentrancy(false)`. Worth **−26% on native**, −7% on a JIT.

`processEvent` runs on every event and, with support on, tests a flag, may queue a re-entrant event,
and drains a callback queue. When no node in the graph can raise a re-entrant event that queue is
provably always empty and all of it is dead code. With the flag off the generated processor also
**dispatches the typed entry directly** — `onEvent(MarketTick)` calls `handleEvent(event)` instead of
widening to `Object` and recovering the type with an `instanceof` chain.

**A guard is retained and it throws.** Build-time detection cannot be complete — a node can reach the
dispatcher through a service or reflectively — so a re-entrant event fails loudly rather than
vanishing.

### 4 · No buffering · 5 · No subscriptions

`setSupportBufferAndTrigger(false)` removes the buffering branch.
`setSupportSubscriptions(false)` stops the constructor publishing the processor to the subscription
manager — which matters for more than one reason, see below.

---

## Deployment shape — worth more than every flag combined

**Construct the processor inside the method that drives the event loop.**

| processor reached via | native |
|---|---|
| a local that never escapes the method driving the loop | **1.41–1.53** |
| a `static` / `static final` field | 3.10 |
| an instance field of a statically-held object | 4.82 |

If the processor escapes, the compiler cannot dissolve the node objects. It escapes by being stored in
a static, a registry, a factory, a thread pool, or a getter that anything calls — **and the escape
happens at construction, so reading the field into a local before the loop does not recover it.**

On a JIT every shape measures about the same; this is an AOT consideration.

---

## Which runtime you deploy on decides which advice applies

**These are two different optimisation problems.** Measured on identical classes, same machine:

| | Graal JIT | native-image, no PGO | native-image + accurate PGO |
|---|---|---|---|
| processor carrying no framework fields | 4.64 | **1.86** | **1.56** |
| processor carrying all seven | 4.74 | **6.20** | **1.58** |
| hand-rolled flat equivalent | 2.09 | 2.45 | 1.55 |

**On a JIT, none of the structural tuning below matters.** Every configuration lands at ~4.6 ns,
because the JIT never dissolves the processor's object structure — so nothing you remove was being
optimised away in the first place. Its floor for a ten-node graph is ~4.6 and no flag reaches past it.

**On native-image the structure is everything**, because AOT *does* dissolve it — reaching 1.86 ns,
**faster than hand-rolled flat code at 2.45.** That is also what makes it fragile: see the cliff below.

Note the JIT beats AOT on the flat hand-rolled arm (2.09 vs 2.45). This is not "AOT is faster". It is
specifically that only AOT removes the graph's object structure.

## The escape-analysis cliff — native without PGO only

Without a profile, a native image's escape analysis has a **finite budget**, and past it the processor
stops being dissolved. It is a cliff, not a gradient — nothing lands between 1.9 and 6.2 ns:

| processor carries | ns |
|---|---|
| nothing extra | 1.87 |
| any ONE framework field (each tested separately) | 1.86–1.88 |
| `callbackDispatcher` + `clock` + `nodeNameLookup` | 1.87 |
| **`callbackDispatcher` + `nodeNameLookup` + `subscriptionManager`** | **6.22** |
| all seven | 6.20 |
| all seven **minus** `subscriptionManager` | 6.16 |

Two things follow that are easy to get wrong:

- **No single field is expensive.** Every one of the seven is free on its own. What costs is the total
  size of the allocation graph — `SubscriptionManagerNode` alone brings an `ArrayList` and three
  `HashMap`s, where `Clock` brings none.
- **Removing the expensive one is not enough.** Dropping `subscriptionManager` from the full set
  changes nothing (6.16 vs 6.20). Once past the cliff you must get back under it, not shave it.

**This is why elision has to be measured rather than counted.**

## PGO — an accurate profile removes the cliff; a bad one is worse than none

**For an AOT Fluxtion processor a bad profile is worse than no profile.** This is not a caution, it is
a measurement:

| arm | exe, no PGO | exe, PGO | shared lib, no PGO | shared lib, PGO |
|---|---|---|---|---|
| non-escaping processor | 1.53 | 1.64 | **1.41** | **6.28** |
| processor in a `static final` field | 3.10 | 2.51 | 3.10 | 6.25 |

- **An accurate profile makes the cliff disappear.** With profiles collected from every arm and
  merged, the processor carrying **all seven** framework fields runs at **1.58 ns** — the same as one
  carrying none (1.56), and the same as hand-rolled (1.55). The structural sensitivity above is a
  property of *unprofiled* AOT, not of AOT.
- **A non-escaping processor reaches ~1.4–1.9 ns with no profile at all**, so PGO is not *required* —
  but it is what makes the result robust to structure rather than dependent on it.
- **A mismatched profile is worse than no profile.** An executable's profile applied to a shared
  library took 1.41 → 6.28, the worst configuration measured anywhere in this work. **Never carry a
  profile across image kinds**, and collect it from a run that exercises what you actually deploy.
- Round 58 saw PGO make its fastest shape slightly *worse* (1.53 → 1.64) with a narrower profile.
  Both observations hold: the profile's accuracy is the variable, not PGO itself.

---

## What you do NOT need to do

All proven, so effort does not go the wrong way.

- **Interface separation between components is free** — ~0.03 ns per call site, and **zero** indirect
  branches with a single implementor, verified by counting `blr` in the disassembly. Structure your
  components for clarity. (With three implementations reachable in one image it costs +115%: AOT
  devirtualises what is provably monomorphic and nothing more.)
- **Event-type dispatch is not a scaling risk** — 2 to 16 event types cost +0.26 ns total.
- **Empty auditor calls are already free.** Do not hand-optimise them.
- **Flattening the graph into one method is not needed** — worth −1.8%, and it cannot be applied to
  components you do not own.

---

## Honest numbers, and one correction

Measured on macOS/aarch64, Oracle GraalVM 25.0.4, output verified identical on every arm.

| shape | JIT | native (no PGO) |
|---|---|---|
| generated, default clock, all support on | 29.08 | 16.07 |
| generated + `ClockStrategy` | 5.03 | 8.92 |
| **generated, full baseline config above** | **5.16** | **7.03** |
| hand-rolled flat equivalent | 2.05 | 2.46 |

**The correction.** Round 58's headline figures of **1.41–1.55 ns / 646–707M events per second** were
measured on `BaseProcessor` — a **hand-written stand-in** whose own javadoc says *"what the generator
emits"*. They are not measurements of generated code. Round 58's actual generated base case was
**6.39 ns** native, and round 59 measures **7.03** on the same shape with a hand-rolled control that
matches round 58 exactly (2.46 vs 2.44).

**Generated code has not yet reached 1.4 ns.** The remaining gap is not dispatch — the generated
`handleEvent` compiles smaller than the hand-written equivalent — it is that the generated processor
still carries seven framework fields (`callbackDispatcher`, `clock`, `nodeNameLookup`,
`subscriptionManager`, `context`, `serviceRegistry`, `functionAudit`) where the model has ten node
fields and nothing else. Removing those from the baseline configuration is open work, tracked as M50.

Quote the shape, not the best number in the table.

---

## Reproducing this

Nothing here should be taken on trust. `tools/bench/dispatch-bench.py` refuses to report unless both
arms ran in one binary, the runtime kind is single and recorded, every arm emits identical check
values before any timing is believed, and no arm falls below an elimination floor — a probe measuring
0.0000 ns is a deleted loop, not a result, and that happened twice in round 58.

**Example project: TO BE NAMED.** This page should point at a runnable repository containing the graph,
the two arms and the build scripts, so a reader reproduces rather than believes. That repository does
not exist yet and its home is an owner decision — round 58's own workspace did not survive, which is
precisely the argument for creating it.
