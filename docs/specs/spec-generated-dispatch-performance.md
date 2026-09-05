# Spec — generated dispatch performance: the optimisation set

**Status:** PROPOSED. **Evidence:** [`round-58`](../experience/runs/round-58/NOTES.md) — 9 runtimes,
~500 measured runs, all arms output-verified before timing.
**Owner:** Fluxtion compiler (upstream). This repo holds the evidence, not the implementation.

---

## The claim this spec exists to support

> With unneeded support compiled out and profile-guided AOT compilation, a generated Fluxtion event
> processor runs at **1.55 ns/event (646M events/sec)** against a hand-written single-method Java
> implementation of the same semantics at **1.54 ns** — an abstraction penalty of **0.6%, with
> overlapping ranges**.

Ten addressable, observable, independently testable node objects cost **nothing** versus one hand-rolled
method. That is the result. Everything below is how to get there and what it costs when you don't.

**Scope limit, stated up front:** one fixture, ten nodes, one machine, single core, closed loop. The
comparator is hand-written **Java**. Parity with hand-written **C++ is not established** — see §6.

---

## 1. Where the time actually goes

Measured on the generated arm, ns/event. Each row is a real configuration, not a model.

| configuration | JIT | native | native+PGO |
|---|---|---|---|
| stock (auditors, guards, re-entrancy wrapper) | 7.86 | 12.89 | 9.41 |
| + guarded drain | 7.74 | 10.59 | 8.39 |
| + entry wrapper removed | 7.31 | 9.52 | 6.87 |
| + auditors removed | ~7.1 | 11.21¹ | — |
| **void triggers + dirty filtering off** | 4.75 | 6.39 | **1.55** |
| hand-rolled Java floor, same semantics | 2.32 | 3.18 | 1.54 |

¹ measured in a different batch; compare within-row only.

**The single most important line:** the same code is **9.41 ns** stock-with-PGO and **1.55 ns** at the
base case with PGO. A 6× range, entirely from configuration.

---

## 2. The four changes

### 2.1 Guarded callback drain — do this first

**No flag, no semantic change, no user decision.** `processEvent` currently calls
`dispatchQueuedCallbacks()` on **every** event. Its empty fast path is not free:

```
getfield  myStack                 ; declared java.util.Deque — an INTERFACE
invokeinterface Deque.isEmpty()   ; cannot be folded without profiles
putfield  dispatching = false     ; unconditional store even when nothing was queued
```

Replace with a field read:

```java
processing = true;
onEventInternal(event);
if (callbackPending) {              // plain boolean on the processor
    callbackDispatcher.dispatchQueuedCallbacks();
    callbackPending = false;
}
processing = false;
```

**Measured: −17.8% native, −10.8% PGO, −1.6% JIT.** Recovers 68% of the full wrapper cost while
keeping every semantic.

Two further cheap wins, **not yet measured**, likely additive: declare `myStack` as `ArrayDeque` so the
call is direct, and skip the `dispatching = false` store on the empty path.

### 2.2 `noReentrancy` compiler flag

Omit the entry wrapper entirely when no node can raise a re-entrant event or register a callback.

**Measured: −26% native, −7% JIT.**

- **Detect at build time** from the graph the generator already holds — nodes injecting
  `EventProcessorContext`, `Callback`, `DirtyStateMonitor`, or calling the re-entrant dispatch API.
- **Fail the build**, naming the offending node, when the flag is set and such a node exists. The cost
  is removed *on a proof*; violating the proof must not be silent.
- **Keep a runtime guard that throws.** Detection cannot be complete — a node may reach the dispatcher
  through a service or reflectively. A field check on an already-loaded field is near-free (§2.1
  demonstrates this).
- **Default off.** It trades a capability for throughput.

### 2.3 Hoist auditor calls — for size, not speed

**Performance: none.** Empty default-method calls cost nothing on any runtime; the original generated
shape already *is* the hoisted form.

**Code size: 180 bytes/handler hoisted vs 276 inlined — 35%**, scaling with event-type count.
Keep it, and do not attach a performance claim to it.

### 2.4 Interface-typed fields on the event path — a systematic hazard

Three instances found:

| field | type | per-event call | status |
|---|---|---|---|
| `CallbackDispatcherImpl.myStack` | `Deque` | `isEmpty()` | fixed by §2.1 |
| `Clock.wallClock` | `ClockStrategy` | `getWallClockTime()` | **open, unmeasured** |
| `ServiceRegistryNode` maps | `Map`/`List` | none | not an issue |

A JIT profiles these to one concrete type and folds them away; closed-world AOT leaves real dispatch.
**Grep the whole runtime for the pattern** — it is invisible until you compile ahead of time.

Related: `myStack` is `Deque<Supplier<Boolean>>`, so **every callback boxes a boolean**. Off the fast
path, but it means the re-entrant path allocates, which undercuts the zero-allocation property for
anyone using callbacks. `BooleanSupplier` removes it.

---

## 3. What the application developer already controls

These need no compiler change — they exist today and were the largest lever measured:

```java
@OnTrigger(failBuildIfMissingBooleanReturn = false)        // void trigger: no dirty flag, no guard
@OnEventHandler(failBuildIfMissingBooleanReturn = false)
config.setSupportDirtyFiltering(false);
```

Semantics change — every node fires every event — and **that is the developer's choice**, not a defect.
It took the generated arm from 9.41 to 1.55 ns under PGO.

**Document this as the performance configuration.** It is currently the difference between 646M and
106M events/sec and is not presented anywhere as a coherent choice.

---

## 4. PGO is not optional, and it is not a native-image detail

| | native | native+PGO |
|---|---|---|
| stock generated | 12.89 | 9.41 |
| base case | 6.39 | **1.55** |

**PGO is what makes the graph free.** Without it, ten node objects cost ~2.4 ns (JIT) to ~3.2 ns
(native); with it, zero. The JIT never closes that gap — only profile-guided AOT does, which inverts
the usual assumption that live runtime profiles beat ahead-of-time compilation.

Two traps, both measured:

- **A mismatched profile is worse than no profile.** Reusing an executable's profile for a shared
  library took 2.85 → 4.19 ns: the unprofiled entry point was treated as cold and deoptimised.
- **PGO also shrank the image** (11.0 vs 13.9 MB) and halved build time. It is not purely a throughput
  lever.

---

## 5. Required verification before any of this merges

1. **Regression suite across the matrix.** Every configuration in §1 must produce **byte-identical
   output** to the stock build for guard-preserving changes (§2.1, §2.2, §2.3). The base case (§3)
   changes semantics deliberately and must be verified against its own expectation, not the stock one.
2. **The audit log must be unaffected.** Every optimisation here touches dispatch, and the audit log
   is this project's product contract. Required: with auditing enabled at each level, the emitted
   `eventLogRecord` stream must be identical before and after — same records, same order, same
   node names. §2.3 in particular reorders nothing but must be proven not to.
3. **`fluxtion.sourceFingerprint` unchanged** where the graph is unchanged. These are dispatch
   optimisations, not graph changes; the GraphML fact must not move.
4. **Zero steady-state allocation retained** at every configuration, under EpsilonGC.
5. **Conformance:** the generated processor must still pass the record-format conformance suite
   (M34.3).

---

## 6. Open, and what would settle it

**C++ parity is not established.** The processor built as a `native-image --shared` library, embedded
in C++, runs at 3.10 ns/event batch against hand-optimised C++ at 1.66 ns — **1.87×**. But that is the
**un-profiled** library, because PGO could not be applied: `-R:ProfilesDumpFile` did not fire on
`graal_tear_down_isolate`.

The Java PGO figure (1.55 ns) sits in the same range as this harness's C++ (1.66 ns), which is
suggestive only — different harnesses, different clocks, never run head to head. **Do not quote parity
with C++.** The experiment that settles it is a PGO-enabled shared library.

Also open: the C ABI boundary costs **~3.9 ns per call**, more than the processor itself. A batched
`@CEntryPoint` taking an array of ticks would remove it. Guidance today: **cross the ABI per batch,
not per event.**

---

## 7. What this replaces

The published benchmark states derived orchestration costs **+1.32 ns / 19%**. Round 58 shows that is
one cell of a range governed by compiler and configuration:

> **+0.6% to +122%**, depending on how it is compiled and what is compiled in.

Both the article and `round-54/BLOG-NUMBERS.md` should carry the range, not the single figure.
