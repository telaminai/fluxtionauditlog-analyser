# Spec — generated dispatch performance: every lever, and the one that matters

**Status:** PROPOSED. **Evidence:** [`round-58`](../experience/runs/round-58/NOTES.md) — ~700 measured
runs across 9 runtimes, every arm output-verified before timing.
**Owner:** Fluxtion compiler (upstream). This repo holds the evidence, not the implementation.

---

## The thesis

> **Give the integrator every lever that preserves vendor-library integration. For maximum
> performance, compile ahead of time and keep the processor non-escaping on the hot path.**

Two closed-world systems compose, and neither is sufficient alone:

- **Fluxtion** closes the world at the **graph** level — dispatch order derived at build time and
  emitted as straight-line code. It cannot dissolve node objects whose source it does not have.
- **GraalVM native-image** closes it at the **program** level — whole-program points-to analysis and
  scalar replacement. It cannot derive dispatch order; that is a domain fact the graph declares.

Fluxtion alone, on a JIT, with vendor components: **4.80 ns/event (208M/s)**.
Both together, non-escaping shape, no PGO: **1.42 ns/event (703M/s)** — **8.9% faster than the best
hand-written C++ implementation of the same arithmetic measured here** — with components arriving as
separately compiled jars.

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
| Oracle native-image, no PGO, loop in a multi-arm `main` | 6.34 | 3.22 | 1.97× | 158M/s |
| Oracle native-image + PGO, same harness | 1.70 | 1.56 | 1.08× | 590M/s |
| **Oracle native-image, no PGO, dedicated method, non-escaping** | **1.42** | 1.49 | **0.95×** | **703M/s** |

Note rows 5–7: the same source in three harnesses. **Every figure from this round must name its
compilation shape**; quoting one without it is how three successive drafts of the article got the
headline wrong.

**JIT floor: 4.80 ns (208M/s). AOT floor: 1.42 ns (703M/s).**

### The lever is compilation shape, not PGO

Same static methods, same classes, only image kind and PGO differ:

| arm | exe, no PGO | exe, PGO | shared lib, no PGO | shared lib, PGO |
|---|---|---|---|---|
| **processor created in the call, never escapes** | 1.53 | 1.64 | **1.42** | 6.28 |
| processor in a `static final` field | 3.10 | 2.51 | 3.10 | 6.25 |
| hand-written Java, same holding | 2.44 | 1.49 | 2.31 | 3.14 |

- **A non-escaping processor reaches ~1.4–1.5 ns in both image kinds with no profile.**
- **PGO helps only shapes that block the optimisation** and *hurts* the one that does not — mildly in
  an executable, catastrophically in a shared library (1.42 → 6.28).
- **Never carry a profile across image kinds.** An executable's profile applied to a shared library
  cost 4×.

**Do not assume PGO helps. Measure it.**

## 2. Separately compiled vendor jars had no measurable cost

Node classes packaged as a pre-compiled jar — the real integration case:

| | native + PGO | Graal JIT |
|---|---|---|
| **nodes from a vendor jar** | **1.57** | 4.74 |
| same nodes, source on the classpath | 1.58 | 4.82 |
| hand-rolled single method | 1.56 | 2.34 |

Java compilers consume **bytecode**, so removing `.java` files proves nothing on its own. The finding
is that whole-program analysis sees through **separately compiled package and jar boundaries** well
enough to remove the measured component-object overhead. Components are opaque to the integrator and
available to the optimiser as bytecode.

## 2a. Versus hand-written C++

Native shared library, non-escaping batch entry, no PGO, 20 rounds in one process, output asserted
identical every round. C++ given every lever short of changing the algorithm.

| | median ns | events/sec |
|---|---|---|
| **Fluxtion generated** | **1.4224** | **703M** |
| C++ hand-scalarised to locals — *best C++* | 1.5619 | 640M |
| C++ struct, `-O3 -march=native -funroll-loops` | 1.5725 | 636M |
| C++ with clang PGO trained on this workload | 1.5673 | 638M |

**8.9% faster than the best C++ measured**, ranges non-overlapping. Hand-scalarising bought C++ 0.7%
(clang already register-allocated the struct); clang PGO bought nothing. `-ffast-math` was not used —
it reassociates floating point and the outputs would stop matching.

Two notes on fairness: the loop-carried `ewma` dependency blocks SIMD for **both** sides, and the Java
arm mutates an event object every iteration where the C++ arm passes two doubles — **Java does more
work per event and is still faster.** What would still win: a different algorithm or data layout,
which is no longer the same comparison.

---

## 3. Levers that preserve vendor integration

Ranked by measured value. All keep semantics unless stated.

| # | lever | JIT | native | notes |
|---|---|---|---|---|
| 1 | **non-escaping hot path** | ~0% | **−54%** (3.10 → 1.42) | deployment shape, not a flag; worth more than everything below combined |
| 2 | **`noReentrancy` build flag** | −7% | **−26%** | only when no node can raise a re-entrant event |
| 3 | **guarded callback drain** | −2% | **−18%** | **unconditional — no flag, no semantic change** |
| 3a | PGO at deploy | — | −32% on blocking shapes, **+340% on non-blocking** | measure it; never carry across image kinds |
| 4 | concrete `ClockStrategy` field | 0% | −10–15% of the clock read | third-order |
| 5 | hoist auditor calls | 0% | 0% | **35% smaller bytecode/handler**; size only |
| 6 | inlining flags / `@AlwaysInline` | ~0% | −3.8% | not worth a GraalVM-internals dependency |

**Implementation order: 3 (code), then 1 and 3a (documentation), then 2.** The guarded drain needs no user decision
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
| `Clock.wallClock` | `ClockStrategy` | **measured in situ**: +0.25 ns on JIT and on native+PGO, +1.33 ns on native without PGO |
| `ServiceRegistryNode` maps | `Map`/`List` | not touched per event |

Also: `myStack` is `Deque<Supplier<Boolean>>`, so **every callback boxes a boolean**. Off the fast
path, but it means the re-entrant path allocates. `BooleanSupplier` removes it.

**Priority note on the clock.** The in-situ cost is +0.25 ns in both configurations anyone should
deploy, and +1.33 ns only in native-without-PGO — which §1 already rules out. **This is third-order;
do it if the field is being touched anyway, not as its own work item.**

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

**Now established** (§2a): the generated processor was **not slower** than the best hand-written C++
implementation of the same arithmetic measured here — 1.42 vs 1.56 ns. Scope: one fixture, ten nodes,
one machine, non-escaping shape, and a competent but not expert-tuned C++ comparator.

**Also known:** the C ABI boundary costs ~3.9 ns per call, more than the processor. **Cross the ABI per
batch, not per event.**

**A measurement caution carried from the round:** these numbers vary up to 3× with whole-program
compilation shape. Every comparison must be within a single binary with both arms present. Two probes
in round 58 returned clean zeroes because the compiler deleted what they measured — a suspiciously
clean result is a broken probe, not a finding.

---

# Part II — Implementation plan

Everything above is measured. This part turns it into branch work for a Fluxtion release. Items are
scoped by module because they land in different artifacts with different compatibility profiles.

## 8. What is guaranteed and should be documented, not built

Two findings need no code. They need to be **stated**, because without them someone will optimise in
the wrong direction.

### 8.1 Interface separation between components is free

Rebuilding the pricing chain with every node behind an interface — ten `invokeinterface` per event
instead of ten `invokevirtual`, verified in bytecode — costs nothing:

| shape | Graal JIT | native, no PGO | native + PGO |
|---|---|---|---|
| concrete node fields | 4.7843 | 6.5358 | 1.5752 |
| **interface fields, 1 implementor** | 4.6777 | **6.3188** | 1.7070 |
| interface fields, 3 implementors reachable | 4.7431 | 6.8017 | 1.5677 |

Proven from machine code, not inferred: with **one implementor** the compiled `handleEvent` contains
**zero indirect branches** and 27 floating-point operations — closed-world analysis resolved all ten
interface calls and inlined the bodies, **with no profile**. With three implementors it contains
**ten indirect branches and zero floating-point operations**, at **0.048 ns per unresolved site**.

**Guarantee to publish:** components may be separated behind interfaces for testability, substitution
or vendor abstraction at no measurable dispatch cost. **Assertion a build can make:** the dispatch
method contains no indirect branch (`blr == 0` on AArch64) — see §11.3.

### 8.2 Event-type dispatch is not a scaling risk

The `instanceof` chain grows **0.26 ns from 2 to 16 event types** on a JIT, and `switch`-on-type-id is
*worse* there (4.57 vs 1.49 at 16 types) because the interface call to obtain the id costs more than
the scan it replaces. `DISPATCH_STRATEGY` already offers `CLASS_NAME`, `INSTANCE_OF` and
`PATTERN_MATCH`.

**Recommendation: leave the default alone.** Record the measurement so the knob is not turned on
intuition.

---

## 9. Service registration as a static binding problem

`EventProcessorConfig` already has two paths, and they bind at different times:

| path | binds | machinery |
|---|---|---|
| `registerInjectable(name, Class<T>, S)` | **build time** | could be a direct field reference |
| `registerService(Service<?>)` | **runtime** | 4 callback maps, 3 volatile caches, registration/deregistration lists |

This is the same shape as `noReentrancy`: **the dynamic machinery exists to serve a case the graph can
often prove does not arise.** If every service a graph consumes is registered at build time, the
registry lookup, the callback maps and the deregistration lists are dead structure.

### 9.1 Scope, and why the measurement matters first

**`ServiceRegistryNode` is not on the per-event dispatch path.** Its `eventReceived` is the empty
`Auditor` default and was measured free (§3.3). The cost, if any, is in:

- `init()` — building maps that a statically-bound graph would not need
- **exported service method calls**, which the generator wraps in `beforeServiceCall`/`afterServiceCall`
- native-image heap and startup — the maps and callback lists are image-heap structure
- **`Map`/`List` interface-typed fields**, the same AOT hazard as §3.3, on the service-call path rather than the event path

**None of this has been measured.** Every other item in this document has been. Rolling in unmeasured
work would break the property that makes the spec trustworthy.

### 9.2 The determinism argument, which is stronger than the performance one

Service **registration order** is a realised non-deterministic choice. If a node's behaviour depends
on which services were registered, or in what order, and that order can vary between runs, it is
**output-reaching non-determinism** and must either be captured in the reproduction record or
eliminated by static binding.

Static binding eliminates it. That is a better reason to do this work than any nanosecond count, and
it is a precondition for the replay-planning work in §11.4.

### 9.3 Recommendation: roll in, gated on measurement

**Roll in** — the static-binding half. It is the same kind of change as `noReentrancy`, lands in the
same modules, and shares the verification gates.

**Keep separate** — any redesign of the *dynamic* service lifecycle: registration/deregistration
callbacks, cache invalidation, the `volatile` fields and their thread-safety. That touches lifecycle
semantics rather than dispatch, carries a different risk profile, and would destabilise an otherwise
low-risk release.

**Gate:** measure exported service-call cost and native-image heap contribution **before**
implementing. If the numbers are third-order, ship §9 as documentation plus the determinism guarantee
and defer the codegen.

---

## 10. Work items by module

| # | item | module | kind | measured gain | depends on |
|---|---|---|---|---|---|
| **W1** | guarded callback drain | `fluxtion-runtime` | additive | **−18% native**, −2% JIT | — |
| **W2** | `myStack` → `ArrayDeque`; drop the `dispatching=false` store on the empty path | `fluxtion-runtime` | internal | unmeasured, likely additive to W1 | W1 |
| **W3** | `Deque<Supplier<Boolean>>` → `BooleanSupplier` | `fluxtion-runtime` | internal | removes per-callback boxing | — |
| **W4** | `noReentrancy` build flag + build failure + runtime guard | generator + `builder-api` | **additive, default off** | **−26% native**, −7% JIT | W1 |
| **W5** | ambient-read scan **+ service boundary check** → determinism precondition | generator | **new gate, opt-in** | correctness, not speed | — |
| **W6** | compiler-derived replay capture set + determinism report | generator + `builder-api` | new artifact | correctness | W5 |
| **W7** | static service binding when all services are build-registered | generator | additive | **unmeasured — gate first** | W5 |
| **W8** | concrete `ClockStrategy` field or `long` fast path | `fluxtion-runtime` | internal | +0.25 ns; third-order | — |
| **W9** | document the performance configuration | docs | — | 9.41 → 1.42 ns | — |
| **W10** | conformance benchmark harness | `tools/bench` | new | reproducibility | — |

**Ordering.** W1 → W2 → W4 is the performance spine. W5 → W6 → W7 is the determinism spine and is the
more valuable of the two. W3, W8, W9, W10 are independent.

**Compatibility.** W1, W2, W3, W8 are internal to the runtime and invisible to consumers. W4 and W7
are opt-in flags defaulting to current behaviour. W5 is an opt-in gate. W6 emits a new artifact. **No
item is breaking.** The release is additive.

---

## 11. Verification gates

Applies to every item. §6's gates stand; these are the additions the new work needs.

**11.1 Output equivalence.** Byte-identical for W1, W2, W3, W8. W4 and W7 change semantics only when
their flag is set, and then only by removing machinery the build has proven unreachable — verified
against the same expectation, not a new one.

**11.2 The audit log record stream is unchanged** at every level: same records, same order, same node
names. This is the product contract and every item here touches dispatch.

**11.3 New assertion — `blr == 0`.** For a graph with a single implementor per component interface,
the compiled dispatch method must contain **no indirect branch**. This makes §8.1's guarantee a
build-checkable property rather than a claim. Regression here means the optimiser stopped
devirtualising, which is exactly the change nobody would otherwise notice.

**11.4 Replay fidelity is preserved and, for W6, derived.** Existing replay must reproduce recorded
processing time exactly — demonstrated on the shipped runtime. W6 additionally requires the ablation
pair: **remove one output-reaching capture → divergence; remove one non-reaching capture → no
divergence** under the declared observable contract.

**11.5 `fluxtion.sourceFingerprint` unmoved** where the graph is unchanged. These are dispatch
optimisations, not graph changes.

**11.6 Zero steady-state allocation retained** at every configuration under EpsilonGC.

**11.7 M34.3 record-format conformance still passing.**

**11.8 The vendor-jar case measured, not assumed** — nodes from a pre-compiled jar must still reach the
floor. This is the load-bearing claim of the whole architecture.

---

## 12. W10 — the harness, and why it is not optional

Every number in this document is shape-dependent. The same source measured **1.42, 2.57, 4.82 and
6.34 ns** across compilation shapes, and four successive drafts of the public write-up carried wrong
headline figures because a shape was quoted without naming it.

**Without a harness that fixes the shape, these numbers will not reproduce — including for us.**

The harness must:

- build both arms into **one** binary, interleave them, and report paired differences
- pin the compilation shape explicitly: non-escaping processor, single-purpose image
- refuse to compare across image kinds
- assert output equivalence before reporting any timing
- fail on a suspiciously clean zero — twice in round 58 a probe measured 0.0000 ns because the
  compiler deleted what it was timing

`tools/bench/` already exists (M19.6) and is the natural home.

---

## 13. What is still unmeasured

Stated so the branch does not silently acquire unmeasured claims:

- exported service-call cost, and native-image heap contribution of the service registry (§9)
- `ArrayDeque` and the dropped store, separately from the guarded drain (W2)
- whether a `noReentrancy` graph and a guarded-drain graph compose, or overlap
- whether any of this holds beyond ten nodes
- whether the replay capture set derived in W6 matches what the recorder captures today

---

# Part III — the service boundary

## 14. Services breach the invariant in two directions

The property everything rests on is:

> **There is no state change without events.**

Combined with a single point of consumption per graph, that makes the recorded event sequence the
complete determinant of the state trajectory. Services are the two places it can be false.

### 14.1 Direction one — consumed services (graph calls out)

A node holds an injected service and calls it during dispatch:

```java
double rate = fxRates.lookup("EURUSD");     // arrived without being an event
```

The **return value is external input that did not enter through the event path.** On replay the
service may be absent, may return something else, or may not be callable at all. Recording *that the
call happened* is insufficient — the graph's state depends on **what came back**.

### 14.2 Direction two — exported services (outside calls in)

A graph exports a service interface and external code invokes it:

```java
graph.pricingControl().setSpreadFloor(0.02);   // mutates graph state, not an event
```

This is a state change with no event. Worse, it has a **position**: if it lands between event 5 and
event 6, replay must place it there. Recording the invocation without its ordering relative to the
event stream does not reproduce the run.

## 15. W5 — the service boundary check

The build must detect both directions and **fail**, unless the service is fully auditable and
replayable.

### 15.1 What "fully auditable and replayable" requires

| direction | must be captured |
|---|---|
| **consumed service** | the invocation, its arguments, and **the return value** |
| **exported service** | the invocation, its arguments, **and its position in the event sequence** |
| **registration lifecycle** | `registerService` / `deRegisterService` calls, in order, as recorded events |

All three are serialisations of method invocations against the graph. That is the unifying
requirement: **every interaction with the graph that is not already an event must become a recorded,
ordered invocation, and consumed-service calls must additionally record what they returned.**

### 15.2 The build-check disposition

For each service reachable from the graph, classify and act:

| situation | non-determinism introduced | disposition |
|---|---|---|
| build-registered, pure function of its arguments | none | **pass** — no capture required |
| build-registered, reads external state | return value | **pass only if returns are captured** |
| runtime-registered | registration order and timing, plus returns | **pass only if lifecycle and returns are captured** |
| exported, callable from outside | invocation, arguments, ordering | **pass only if invocations are serialised into the event sequence** |
| any of the above without declared capture | unknown | **generate the capture (§17), or FAIL naming the service and method** |

Purity cannot be assumed; it must be declared and, where possible, checked by the same ambient-read
scan applied to the service implementation when its bytecode is available.

### 15.3 The design consequence worth taking

The cleanest resolution is not to build a second capture channel.

> **Make exported service invocations events.**

If every interaction enters through the same single point of consumption, the invariant is restored by
construction, the existing recorder captures them with correct ordering for free, and replay needs no
new mechanism. A separate invocation log would have to be merged with the event log on replay, and
merging two orderings is exactly the second-representation problem this architecture exists to avoid.

Consumed-service returns cannot be handled this way — they are inputs arriving mid-dispatch. They need
genuine return-value capture, which is the harder half and should be scoped accordingly.

### 15.4 Consequence for W7

Static service binding no longer stands on a performance argument it has not earned. It stands on
this:

- it removes **registration-order** non-determinism entirely, rather than requiring it to be captured;
- it leaves **return-value** non-determinism untouched, so a statically bound service that reads
  external state still requires capture.

**Static binding is necessary but not sufficient for a replayable graph.** W7 should say so, and the
build check must not treat build-registration as evidence of determinism.

### 15.5 Open questions, unmeasured

- return-value capture for **mutable** returns: capturing a reference is not capturing a value, and
  collections need ordering as well as contents
- cost of serialising exported invocations into the event stream
- whether purity can be established for vendor services from bytecode alone, or must be declared
- whether an exported-invocation-as-event changes the graph's declared topology, and therefore
  `fluxtion.sourceFingerprint`

---

## 16. W11 — generate the service registration dispatch

**This is the highest-value service item, and unlike W7 it needs no measurement to justify.**

### 16.1 What the runtime does today

Verified from the runtime bytecode (1.0.14):

```java
class ServiceRegistryNode$Callback {
    java.lang.reflect.Method method;      // ← reflective dispatch target
    Object node;
    String nodeName;
    boolean namedService;
    void invoke(Object, String);          // ← Method.invoke
}
```

Consumers are discovered from `@ServiceRegistered` / `@ServiceDeregistered` on node methods, held in
`Map<RegistrationKey, List<Callback>>`, and dispatched by map lookup followed by **reflective
invocation**.

**Every input to that decision is available at compile time.** The generator holds every node, every
annotated method, and the service class and name. Which node receives which registration is a
determination that currently binds at runtime, by reflection, on every registration — and it could
bind once, in the generator.

### 16.2 What generation produces instead

```java
public void registerService(Service<?> svc) {
    Class<?> c = svc.serviceClass();
    String n = svc.serviceName();
    Object i = svc.instance();
    if (c == FxRates.class && "primary".equals(n)) {
        pricingNode.fxRatesRegistered((FxRates) i);      // direct call, declared order
        hedgeNode.fxRatesRegistered((FxRates) i);
    } else if (c == CreditLimits.class) {
        creditNode.limitsRegistered((CreditLimits) i);
    }
}
```

No `Method`, no `Map`, no `List`, no reflection.

### 16.3 Three independent justifications

**1. It removes a native-image configuration burden.** Reflective dispatch requires reflection
metadata, so users must supply JSON config for service registration or hit runtime failures. This is
the same class of problem round 58 hit directly: `getAuditorById` uses `Class.getField`, the native
build failed with `NoSuchFieldException: clock`, and a `reflect-config.json` was required. **Removing
reflection removes the configuration, and configuration a user can get wrong is an adoption tax.**

**2. It removes non-determinism the framework itself introduces.** Discovery walks declared methods,
and the JDK explicitly does **not** guarantee the order `getDeclaredMethods()` returns. When two nodes
register for the same service, the order they are notified is therefore not guaranteed stable across
JVMs or versions. If any node's registration handler has effects another node observes, that is
**output-reaching non-determinism produced by the framework, arriving through no event, and captured
nowhere.**

That is a Corollary 2 violation in the current runtime. Generated dispatch fixes it by construction:
the order becomes declared, stable, and visible in the generated source.

*Scope note: the mechanism is real and follows from the JDK contract. Whether any production graph
today depends on that order is unmeasured.*

**3. It removes the maps, the callback lists and the reflective machinery** from the image heap and
from startup. Registration is not on the per-event path (§3.3), so this is a startup and footprint
argument, not a throughput one — and it should not be sold as throughput.

### 16.4 Relationship to W7

| | W7 static binding | **W11 generated dispatch** |
|---|---|---|
| applies when services are **build**-registered | yes | yes |
| applies when services are **runtime**-registered | no | **yes** |
| removes registration-order non-determinism | yes | **yes** |
| removes reflection and native-image config | partially | **yes** |
| needs a measurement to justify | **yes** | **no** |

**W11 is strictly broader and lower risk than W7, and should be done first.** W7 then becomes an
optimisation on top of a graph that is already reflection-free, rather than the vehicle for removing
reflection.

### 16.5 The pattern this belongs to

Round 58 found reflection in three places in the generated processor — `getNodeById`'s fallback,
`getAuditorById`, and `newInstance` — **none on the dispatch path, all on introspection and lifecycle
paths**, and all requiring native-image configuration from the user.

Service registration is the fourth. That is a pattern, not four coincidences:

> **W12 — audit every reflection site reachable from `init`, registration or lifecycle, and generate
> the dispatch instead.** Each site removed is one less line of native-image configuration a user can
> get wrong, and one less place where JDK-unspecified ordering can leak into behaviour.

The dispatch path is already reflection-free and fast. **The remaining reflection is all in the paths
that decide *what is connected to what* — which is exactly the class of determination the generator
should be binding.**

### 16.6 Revised work item table entries

| # | item | module | kind | justification | depends on |
|---|---|---|---|---|---|
| **W11** | generate service registration/deregistration dispatch | generator | additive | native-image config removed; framework-introduced ordering non-determinism removed | — |
| **W12** | audit and generate remaining reflective lifecycle dispatch (`getNodeById`, `getAuditorById`, `newInstance`) | generator + runtime | additive | same | W11 |

**W11 moves ahead of W7 in the determinism spine: W5 → W11 → W6 → W7.**

---

## 17. W13 — generated service auditors: replay covers invocations, not just events

§15 framed the service boundary as something the build must **detect and reject**. It can do better
than that: in most cases it can **generate the capture**, and the machinery on both sides already
exists.

### 17.1 The two assets that already exist

**Mongoose already converts events into method invocations on a graph** via its dispatch strategies.
The mapping between an invocation and a serialisable record is therefore already modelled on the way
in — this work makes the reverse direction a generation target rather than new infrastructure.

**The recorder is already an `Auditor`.** `YamlReplayRecordWriter` implements
`com.telamin.fluxtion.runtime.audit.Auditor` and is registered in the same slot as any other auditor.
A *generated* auditor lands in an existing extension point, not a new one.

### 17.2 Exported services — generated auditor, invocations become records

The generator knows the exported interface and every method on it. It emits an auditor that captures
each invocation with its arguments into the same log as events, **in event-stream position**:

```
--- !ReplayRecord   event: !MarketTick {bid: 100.0, ask: 100.5}      wallClockTime: 1788683000975
--- !ReplayRecord   invocation: pricingControl.setSpreadFloor(0.02)   wallClockTime: 1788683001102
--- !ReplayRecord   event: !MarketTick {bid: 100.1, ask: 100.6}      wallClockTime: 1788683001139
```

Replay re-issues the invocation at its recorded position. **Ordering relative to events is preserved
because there is one record stream, not two** — which is what §15.3 required and the reason a separate
invocation log was rejected.

### 17.3 Consumed services — generated recording proxy, calls *and* returns

The same generation trick solves the harder direction. The generator knows the consumed interface, so
it can emit a recording decorator and a replaying stub from the same signature:

```java
// record: delegate, capture the return
final class FxRates$Recorder implements FxRates {
    public double lookup(String k) {
        double r = delegate.lookup(k);
        auditor.recordReturn("fxRates.lookup", k, r);
        return r;
    }
}
// replay: no delegate, return what was recorded
final class FxRates$Replayer implements FxRates {
    public double lookup(String k) { return (double) replaySource.nextReturn("fxRates.lookup", k); }
}
```

On replay the graph never calls out. It consumes recorded returns, in order, and reproduces exactly.
**This is the piece §14.1 identified as missing and §15.1 could only demand rather than provide.**

### 17.4 What this does to the build check

The disposition changes from *reject* to *generate, or reject if generation is impossible*:

| service situation | build action |
|---|---|
| build-registered, declared pure | pass, generate nothing |
| consumed, recordable signature | **generate recorder + replayer** |
| exported, recordable signature | **generate auditor; invocations enter the record stream** |
| runtime-registered | **generate lifecycle capture** (W11 already makes the dispatch static) |
| **signature not recordable** | **FAIL, naming the service and method** |

**"Recordable signature" is the decidable criterion**, and it is a property of types the generator
already inspects. Expected failure cases:

- arguments or returns that cannot be serialised
- returns that are **mutable and later mutated** — capturing a reference is not capturing a value
- callback- or stream-shaped services (`Consumer`, `Observer`, reactive returns) where the interaction
  is not a single call/return pair
- services that return other services

These should fail loudly and name the method. A service whose signature cannot be recorded is a hole
in the replay claim, and the build is the right place to say so.

### 17.5 Why this is the strongest item in Part III

It converts the entire service boundary from *a caveat on the replay claim* into *generated code*, and
it does so without new runtime infrastructure — a generated auditor in an existing slot, and generated
proxies from signatures the generator already reads.

The resulting claim is materially stronger than today's:

> **Replay reproduces the graph's inputs completely: events, exported invocations in stream position,
> service returns, and registration lifecycle — or the build fails and names what it could not
> capture.**

That is the precondition every verification, property-checking and formal-layer ambition rests on, and
it is reachable with the machinery already in place.

### 17.6 Revised items and ordering

| # | item | module | depends on |
|---|---|---|---|
| **W13a** | generated auditor for exported service invocations, recorded in event-stream position | generator | W11 |
| **W13b** | generated recorder/replayer proxies for consumed services, capturing returns | generator | W11, W13a |
| **W13c** | build failure on non-recordable signatures, naming service and method | generator | W13a, W13b |

**Determinism spine, final: W5 → W11 → W13 → W6 → W7.** W6's capture-set derivation becomes much more
useful once W13 exists, because the set it derives is then something the system can actually record.

### 17.7 Unmeasured

- cost of recording exported invocations and service returns at production volumes
- whether recorded returns should be value-copied or reference-captured, and the cost of the former
- whether a graph with generated service auditors changes `fluxtion.sourceFingerprint` — if it does,
  W13 is a graph change and cannot ship under gate 11.5 with the additive items
