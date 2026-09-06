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

## The baseline configuration, in one place

**This is the configuration to start from for best performance.** Five items. Two of them change
behaviour and are your decision; three are free.

```java
// ---- 1. SEMANTIC CHOICES — these change what the graph does ------------------------------

// 1a  NO DIRTY FLAGS: every node fires on every event, unconditionally
@OnTrigger(failBuildIfMissingBooleanReturn = false)          // void trigger, no dirty flag, no guard
@OnEventHandler(failBuildIfMissingBooleanReturn = false)
config.setSupportDirtyFiltering(false);                      // and no dirty-flag machinery at all

// 1b  IF YOU AUDIT: turn off event stringification and thread name.
//     This is BASELINE, not an optimisation to consider later: it is the difference between
//     zero allocation and 208 bytes/event, and it removes about a third of the audit cost.
config.addEventAudit(LogLevel.INFO, false, false);           // printEventToString, printThreadName

// ---- 2. FREE — no capability lost ---------------------------------------------------------

// 2a  SUPPLY A CLOCK: otherwise the Clock auditor reads the system clock on EVERY event
processor.onEvent(ClockStrategy.registerClockEvent(() -> myStreamTime));

// 2b  DEPLOYMENT SHAPE: construct the processor inside the method that runs the event loop,
//     and time from the caller. Nothing between the constructor and the loop.       (§below)

// 2c  BUILD WITH AN ACCURATE PGO PROFILE, collected from what you actually deploy.  (§below)

// ---- Handled for you, nothing to configure ------------------------------------------------
// Node-name lookup is generated as a switch rather than a populated map. It was the single
// largest cost; the generator now emits it as code and no capability is lost.          (§6)

// ---- NOT needed — all measured free. Set only if you don't want the capability -------------
//   config.setSupportReentrancy(false);        // wrapper free, guard free
//   config.setSupportBufferAndTrigger(false);  // free
//   config.setSupportSubscriptions(false);     // free
```

**Measured end state:** ~1.6 ns/event unaudited, ~550 ns fully traced-and-audited, both
zero-allocation, both from generated code.

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

### 6 · Node-name lookup generated as code — the largest single cost, and it costs you nothing

**Measured: 5.55 → 1.57 ns on a real generated processor, with lookup still working.**

`initialiseAuditor` registers **every node** with each auditor, and `NodeNameAuditor` stores them in
two `HashMap`s. Every node object is then published into a live heap structure, so none can be
scalar-replaced and the whole graph materialises as real allocations.

**It is invisible unless you build with PGO.** Without a profile the processor measures ~5.5 ns either
way — that configuration is already slow for other reasons — so a benchmark without PGO will tell you
this is free. It is not.

**You give up nothing.** The generator knows every name and field at build time, so it emits the
mapping as a switch that reads a field on demand and stores no reference. `getNodeById` keeps working.
The switch never runs on the event path, so its cost is irrelevant — what matters is that it holds
nothing.

**The one case still to pay for it:** an auditor that consumes `nodeRegistered` — an audit log that
names its nodes — still receives every node and still publishes them. An audited processor does not
reach 1.57 by this route, and that is a trade worth making.

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

### It is cumulative — no single field is the culprit, in either direction

Tested exhaustively, both ways round, all arms in one binary:

| | ns |
|---|---|
| **adding** any ONE of the seven to a bare processor | 1.86–1.88 — every one free |
| **removing** any ONE of the seven from the full set | 6.12–6.29 — every one useless |
| the full set | 6.20 |
| none of them | **1.87** |

So there is no expensive field and no cheap win. Adding one costs nothing; removing one saves nothing.
What decides the outcome is the **total size of the allocation graph** the compiler must dissolve:

| framework object | objects it allocates |
|---|---|
| `ServiceRegistryNode` | 6 — four `HashMap`s plus a lock |
| `SubscriptionManagerNode` | 5 — an `ArrayList` and three `HashMap`s |
| `NodeNameAuditor` | 3 — two `HashMap`s |
| `CallbackDispatcherImpl` | 2 — an `ArrayDeque` |
| `Clock`, `ExportFunctionAuditEvent` | 1 each |

Three fields can be fine or fatal depending which three: `callbackDispatcher + clock + nodeNameLookup`
stays at 1.87, while `callbackDispatcher + nodeNameLookup + subscriptionManager` falls to 6.22.

**The practical consequence: you cannot shave your way back. You have to get under the budget.**
Elision has to be measured, not counted — and on an unprofiled native image it is close to
all-or-nothing.

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
- **A mismatched or INCOMPLETE profile is worse than no profile.** This is the single biggest trap
  here, and it was measured three separate times: an executable's profile applied to a shared library
  (1.41 → 6.28), and twice a path that was *in the image but not in the profile* (1.83 → 6.19 and
  1.59 → 7.20). In every case the result was **worse than building with no profile at all**, because
  GraalVM reads absent profile data as coldness and compiles that path for size — so the escape
  analysis that reaches 1.57 never runs.

  > **Every code path you deploy must be exercised during profile collection.**

  Collect from a run that exercises what you actually ship, and never carry a profile across image
  kinds. If one entry point is slow and the others are fast, suspect the profile before the code.
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

**A note on provenance.** Round 58's published figures of 1.41–1.55 ns were measured on
`BaseProcessor`, a hand-written stand-in, not on generated code. That gap is now closed: with the
configuration above **the generator itself produces 1.57 ns**, matching that control (1.58) and
hand-rolled flat code (1.55).

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
