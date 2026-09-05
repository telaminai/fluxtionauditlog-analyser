# Spec — generated dispatch performance: every lever, and the one that matters

**Status:** PROPOSED. **Evidence:** [`round-58`](../experience/runs/round-58/NOTES.md) — ~700 measured
runs across 9 runtimes, every arm output-verified before timing.
**Owner:** Fluxtion compiler (upstream). This repo holds the evidence, not the implementation.

---

## The thesis

> **Give the integrator every lever that preserves vendor-library integration. For performance
> equivalent to hand-rolled Java, deploy native-image with PGO.**

Two closed-world systems compose, and neither is sufficient alone:

- **Fluxtion** closes the world at the **graph** level — dispatch order derived at build time and
  emitted as straight-line code. It cannot dissolve node objects whose source it does not have.
- **GraalVM native-image** closes it at the **program** level — whole-program points-to analysis and
  scalar replacement. It cannot derive dispatch order; that is a domain fact the graph declares.

Fluxtion alone, on a JIT, with vendor components: **4.74 ns/event**.
Both together: **1.57 ns/event — within 1% of hand-written Java — with components arriving as opaque
pre-compiled jars.**

**The integration thesis and the performance thesis are only simultaneously true under ahead-of-time
compilation.**

---

## 1. The measured floor

Base case: void triggers (`failBuildIfMissingBooleanReturn = false`), `setSupportDirtyFiltering(false)`,
no auditors, no re-entrancy wrapper. Processor non-escaping. Median of 5 × 200M events, output verified.

| runtime | Fluxtion | hand-rolled Java | ratio | throughput |
|---|---|---|---|---|
| Corretto 21.0.9, C2 | 5.33 | 3.13 | 1.71× | 187M/s |
| OpenJDK 25.0.2, C2 | 5.33 | 3.12 | 1.71× | 188M/s |
| **GraalVM CE 25.3.4.1, Graal JIT** | **4.80** | 2.34 | 2.05× | **208M/s** |
| Oracle GraalVM 25.0.4, Graal JIT | 4.93 | 2.33 | 2.12× | 203M/s |
| Oracle native-image, **no PGO** | 6.34 | 3.22 | 1.97× | 158M/s |
| **Oracle native-image + PGO** | **1.57–1.70** | 1.56 | **1.01–1.08×** | **590–635M/s** |

**JIT floor: 4.80 ns (208M/s). Native+PGO floor: 1.57 ns (635M/s), within 1% of hand-written Java.**

Every 1.5x figure in this document is **native-image + PGO**. No JIT configuration reaches it.

**Without PGO, native-image is the *worst* runtime measured** (6.34 ns). PGO is not a tuning detail;
it is the entire difference.

## 2. Vendor jars cost nothing under AOT

Node classes packaged as a pre-compiled jar — no source, the real integration case:

| | native + PGO | Graal JIT |
|---|---|---|
| **nodes from a vendor jar** | **1.57** | 4.74 |
| same nodes, source on the classpath | 1.58 | 4.82 |
| hand-rolled single method | 1.56 | 2.34 |

Substrate's points-to analysis is whole-program: it proves the vendor node instance non-escaping and
dissolves it regardless of where it was compiled. **No source access, no codegen change, no loss.**

---

## 3. Levers that preserve vendor integration

Ranked by measured value. All keep semantics unless stated.

| # | lever | JIT | native | notes |
|---|---|---|---|---|
| 1 | **PGO at deploy** | — | **−36%** | no code change at all; the single biggest item |
| 2 | **`noReentrancy` build flag** | −7% | **−26%** | only when no node can raise a re-entrant event |
| 3 | **guarded callback drain** | −2% | **−18%** | **unconditional — no flag, no semantic change** |
| 4 | concrete `ClockStrategy` field | 0% | −10–15% of the clock read | third-order |
| 5 | hoist auditor calls | 0% | 0% | **35% smaller bytecode/handler**; size only |
| 6 | inlining flags / `@AlwaysInline` | ~0% | −3.8% | not worth a GraalVM-internals dependency |

**Implementation order: 3, then 1 (documentation), then 2.** The guarded drain needs no user decision
and is pure gain; PGO is documentation rather than code; `noReentrancy` needs build-time proof plus a
runtime guard.

### 3.1 Guarded callback drain — do this first

`processEvent` calls `dispatchQueuedCallbacks()` on **every** event. Its empty fast path is not free:

```
getfield  myStack                 ; declared java.util.Deque — an INTERFACE
invokeinterface Deque.isEmpty()   ; cannot be folded without profiles
putfield  dispatching = false     ; unconditional store even when nothing was queued
```

Gate it on a field the callback path sets:

```java
processing = true;
onEventInternal(event);
if (callbackPending) { callbackDispatcher.dispatchQueuedCallbacks(); callbackPending = false; }
processing = false;
```

Two further cheap wins, **unmeasured but likely additive**: declare `myStack` as `ArrayDeque` so the
call is direct, and skip the `dispatching = false` store on the empty path.

### 3.2 `noReentrancy` flag

Omit the wrapper when no node can raise a re-entrant event or register a callback.

- **Detect at build time** from the graph the generator already holds.
- **Fail the build**, naming the offending node. The cost is removed *on a proof*; violating it must
  not be silent.
- **Keep a runtime guard that throws** — detection cannot be complete.
- **Default off.**

### 3.3 Interface-typed fields on the event path — a systematic AOT hazard

Three instances found; a JIT folds them, closed-world AOT leaves real dispatch:

| field | type | status |
|---|---|---|
| `CallbackDispatcherImpl.myStack` | `Deque` | fixed by §3.1 |
| `Clock.wallClock` | `ClockStrategy` | **open** — worth ~0.1 ns/event |
| `ServiceRegistryNode` maps | `Map`/`List` | not touched per event |

Also: `myStack` is `Deque<Supplier<Boolean>>`, so **every callback boxes a boolean**. Off the fast
path, but it means the re-entrant path allocates. `BooleanSupplier` removes it.

---

## 4. Levers the application developer already controls

No compiler change needed; these were the largest single effect measured:

```java
@OnTrigger(failBuildIfMissingBooleanReturn = false)        // void trigger: no dirty flag, no guard
@OnEventHandler(failBuildIfMissingBooleanReturn = false)
config.setSupportDirtyFiltering(false);
```

Semantics change — every node fires every event — and **that is the developer's choice**, not a defect.
**Document this as the performance configuration.** It is currently undocumented as a coherent choice.

### Deployment shape matters under AOT

| processor reached via | native+PGO |
|---|---|
| **non-escaping local, or a private field of an object that never escapes** | **1.57** |
| `static` / `static final` field | 2.57 |
| instance field of a statically-held object | 4.82 |

A `private final` field is fine **provided the holding object never escapes** — an event-loop worker
that builds its own engine and never publishes the reference. Broken by storing it in a static or
registry, an opaque factory, a thread pool, or a getter that is actually called. **Reading the field
into a local before the loop does not help.** On a JIT, all shapes are ~4.8 — this is an AOT-only
consideration.

---

## 5. Flat-state codegen — the JIT lever, and why it is deprioritised

Hoisting node state into the processor and re-emitting bodies as private methods takes the **JIT** from
4.82 to **2.50 ns** (−48%), within 6.8% of hand-written. It is the largest JIT lever found.

**It is nonetheless not recommended**, for two reasons:

1. **It cannot be applied to vendor components.** It requires the node's method body and private field
   layout — *"I read your declarations"* becomes *"I read your implementation"*. There is no
   legitimate way to hoist private fields out of a class you do not own. **This contradicts the
   integration thesis directly.**
2. **It buys nothing where it matters.** Native+PGO already achieves the same result via scalar
   replacement — 1.57 with or without it. Flat codegen does statically what PGO does dynamically.

It remains the only lever for **JIT deployment with nodes you own**. For **JIT with vendor jars there
is no lever at all** (4.74 ns), and that is the single configuration where the two theses conflict.

---

## 6. Required verification before merge

1. **Byte-identical output** to the stock build for every guard-preserving change (§3.1–3.3). The
   base case (§4) changes semantics deliberately and must be verified against its own expectation.
2. **The audit log record stream must be unchanged** at every level — same records, same order, same
   node names. Every change here touches dispatch, and the log is this project's product contract.
3. **`fluxtion.sourceFingerprint` unmoved** where the graph is unchanged.
4. **Zero steady-state allocation retained** at every configuration under EpsilonGC.
5. **M34.3 record-format conformance** still passing.
6. **Vendor-jar case measured**, not assumed: nodes from a pre-compiled jar must still reach the
   floor under native+PGO.

---

## 7. What this replaces, and what is not established

The published benchmark states derived orchestration costs **+1.32 ns / 19%** on one JDK. That is one
cell. The range is **+1% (native+PGO) to +105% (JIT)**, governed by compiler and configuration.

**Not established:** parity with hand-optimised **C++**. The shared-library build reached 4.19 ns
against C++ at 1.66, but PGO could not be applied to it — `-R:ProfilesDumpFile` did not fire on
`graal_tear_down_isolate`. That is the experiment that would settle it.

**Also known:** the C ABI boundary costs ~3.9 ns per call, more than the processor. **Cross the ABI per
batch, not per event.**

**A measurement caution carried from the round:** these numbers vary up to 3× with whole-program
compilation shape. Every comparison must be within a single binary with both arms present. Two probes
in round 58 returned clean zeroes because the compiler deleted what they measured — a suspiciously
clean result is a broken probe, not a finding.
