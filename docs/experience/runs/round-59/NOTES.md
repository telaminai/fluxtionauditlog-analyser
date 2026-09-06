# Round 59 — verifying two M50 claims that had never been executed

**Date** 2026-09-06 · **Machine** macOS aarch64 (Darwin 25.6.0)
**Runtime** Oracle GraalVM 25.0.4+7.1 LTS (`graal-25.3.4.1` build), `native-image` 25.0.4
**Purpose** close the two gaps the M50 review briefs admitted: W10 had never run against a real
Fluxtion processor, and W12's native-image claim was inferred rather than demonstrated.

Round 58 measured ~700 runs but its *conclusions* rested in two places on things nobody had executed.
This round executes them. It also found a defect in the harness itself, and a conclusion round 58's
own data supports but its write-up never drew.

---

## 0. The workspace had to be rebuilt, and that is a finding

Round 58's `scripts/` expects `classes/` and `gvm/` beside it. **Neither survives** — the same defect
already tracked as "round 57 lacks its jar workspace" (tracker M48.15). Worse, `src/base/BaseProcessor.java`
is a **hand-written stand-in** whose own javadoc says "what the generator emits", and `MarketTick`
does not exist in the repo at all.

So round 58 never benchmarked a generated processor in the arms that used `BaseProcessor` — it
benchmarked a faithful *model* of one. That is legitimate and was labelled, but it means "run the bench
against a real processor" required generating one, which this round does.

## 1. Three traps hit while getting a real processor generated

Recorded because each would silently produce a wrong answer, and two produced one here.

**1.1 A stale installed jar shadows the branch.** `mvn test` does not `install`. The
`fluxtion-generator-core-1.0.67-SNAPSHOT.jar` in `~/.m2` was built 2026-09-02 — before the W12 change —
and carries the OLD template. Generation used it. A developer verifying W12 this way generates
pre-W12 code and concludes the change does not work.

**1.2 Generation silently falls back to a REMOTE service.** With Velocity absent from the classpath the
`ServiceLoader` finds no local `SourceGenerator` and logs, at DEBUG only:

```
no local SourceGenerator found on classpath, falling back to remote
using remote /generate-source endpoint
Sending DTO+config to remote source endpoint at https://fluxtion-source-gen…/generate-source
```

**The build succeeds and emits a processor built from the PUBLISHED template, not the local one.** Any
local compiler change is silently inert. Nothing at INFO or WARN says so. This is the single most
dangerous behaviour found in this round: a compiler developer can change a template, watch a green
build, and measure the old output.

*It also has a use*: it gives a free control arm — the published generator's output — used in §3.

**1.3 Classpath order.** Module `target/classes` appended AFTER dependency jars is shadowed by them.

## 2. W10 — the bench, run against a real generated processor, and a defect in the bench

`tools/bench/dispatch-bench.py` had **never** been run against anything but a fake program. The first
real run failed on its own gate:

```
[FAIL] every arm produced a RESULT line — arm 'hand' produced no RESULT line
```

**A genuine defect in the harness.** It built `java -cp CP Bench -Darm=hand …`, putting the `-D` flags
AFTER the main class, where the JVM passes them to `main(String[])` as program arguments.
`System.getProperty("arm")` returned the default, so **every arm would have measured the same code**.

That is precisely the silent wrong-answer this harness exists to prevent, and it was in the harness.
It failed loudly rather than reporting "no measurable difference between the arms" — which is the
error round 58 published in draft — because the fake program in the tests happens to read `sys.argv`
while a JVM does not. Fixed; two regression tests added (`test_jvm_property_flags_precede_the_main_class`,
`test_native_mode_appends_flags_after_the_binary`). Suite 13/13.

### The measured result

Real W12-branch-generated processor, 10 nodes, void triggers, `setSupportDirtyFiltering(false)`,
auditors present (clock, nodeNameLookup, serviceRegistry). One binary, arms by `-Darm=`, 9 interleaved
rounds, warm 5M, 200M iterations, EpsilonGC. **All gates passed. All three arms emit identical checks**
(`buf=11551.2267 upd=205000000 brch=102500000`).

| arm | median ns | min | max | sd | events/sec |
|---|---|---|---|---|---|
| `generated` — default clock | **29.08** | 28.94 | 31.78 | 0.86 | 34M |
| `generatedStreamClock` — supplied `ClockStrategy` | **5.03** | 4.90 | 5.20 | 0.10 | 199M |
| `hand` — hand-rolled flat | **2.19** | 2.12 | 2.24 | 0.03 | 458M |

Paired, same round: stream clock **−24.02 ns (−82.6%)**, faster in 9/9, ranges disjoint.

**Cross-validation:** `generatedStreamClock` at 5.03 ns sits beside round 58's recorded JIT base case of
4.80 ns. Different machine state, same shape, same order. The harness reproduces the historical figure.

## 3. W12 — the native-image claim, demonstrated

W12's justification was that a hand-written `reflect-config.json` stops being necessary. That had never
been run. Three images, same graph, `--no-fallback`, `-O1`:

| image | reflect-config | `getNodeById("mid")` | `getAuditorById("clock")` | `getLastAuditLogRecord()` | exit |
|---|---|---|---|---|---|
| published generator (3 reflective sites) | **none** | PASS | **FAIL** `NoSuchFieldException: clock` | PASS | 3 |
| published generator | **supplied** | PASS | PASS | PASS | 0 |
| **W12 branch (0 reflective sites)** | **none** | PASS | **PASS** | PASS | 0 |

**The claim holds.** The middle row closes the causal chain: the failure is caused by the reflective
lookup and is cured either by config the user must write, or by W12 removing the reflection.

**But it is one site of three, and the brief should say so.**

- `getNodeById` passes either way — `"mid"` is in `nodeNameLookup`, so the reflective fallback is never
  reached. It would only differ for an id that is an auditor.
- `getLastAuditLogRecord` passes either way for a worse reason: it catches `Throwable` and returns `""`.
  Under the published generator it silently returns empty instead of the record. W12 does not change
  that — `EventLogManager.NODE_NAME` is `"eventLogger"`, which is not a field here, so both throw
  internally and both swallow it.
- **`getAuditorById` is the one that observably breaks**, and it is the documented way to reach an
  auditor.

Also worth stating: **dispatch itself never needed the config.** Both images ran events correctly. The
config was only ever needed for the introspection API.

## 4. The conclusion round 58's own data supports and its write-up never drew

`generated` 29.08 vs `generatedStreamClock` 5.03 is a 24 ns difference from one cause:

```java
// Clock.java
@Initialise public void init() { wallClock = System::currentTimeMillis; }
@Override public void eventReceived(Object event) {
    processTime = getWallClockTime();     // System.currentTimeMillis() on EVERY event
    eventTime   = processTime;
}
```

**This is not new data.** `results/all-results.csv` from round 58 has it on every runtime measured:

| runtime | `fluxtionDefault` | `fluxtionStreamClock` | delta |
|---|---|---|---|
| graal25.3-graaljit | 19.01 | 7.71 | −11.3 |
| oracle25-graaljit | 19.01 | 8.03 | −11.0 |
| corretto21-c2 | 18.45 | 8.58 | −9.9 |
| oracle25-native | 20.87 | 13.98 | −6.9 |
| oracle25-native-pgo | 19.94 | 9.58 | −10.4 |

**The measurement was made and the conclusion was never carried forward.** `spec-generated-dispatch-performance.md`
§3.3 discusses the `ClockStrategy` *field typing* at +0.25 ns and calls the clock **"third-order; do it
if the field is being touched anyway"** — a verdict about the field indirection that reads as though it
covers the clock generally. The same round's raw data shows the clock **read** is the largest single
line item in the whole CSV.

Both statements are true about different things. The spec never separates them, and W8 was deferred on
the "third-order" wording.

**Consequence for W9.** The performance-configuration page listed five decisions and did not include
*supply a `ClockStrategy`*, which on this evidence is the largest application-level lever in the default
configuration on every runtime measured. Corrected in that document.

**Caveat.** `System.currentTimeMillis()` cost is platform-dependent; 24 ns here is macOS/aarch64. Round
58's ~11 ns on the same class of machine is the more conservative figure. The direction and the
dominance are consistent across seven runtimes; the magnitude is not a portable constant.

## 5. What is still not done

- **No native-image throughput arm.** §2 is JIT only. The native floor (1.42 ns) is NOT re-measured here
  and nothing in this round should be quoted against it.
- **The clock finding is not re-measured under native-image** in this round; §4's native rows are round
  58's.
- **W2/W3 are not measured at all.** Still true: no number should be attached to that branch.

---

## 6. The native arm — and what it says about the 1.42 ns floor

Same three arms, one native binary, Oracle GraalVM 25.0.4 `native-image`, **no PGO**, epsilon GC,
`--no-fallback`, no reflection config. Processor generated by the **local** compiler
(`-Dfluxtion.sourceGeneratorId=local`, `Selected SourceGenerator by id 'local'`, 0 reflective sites).
**All gates passed; all three arms emit identical checks.**

| arm | median ns | min | max | sd | events/sec |
|---|---|---|---|---|---|
| `generated` — default clock | 16.07 | 15.91 | 16.10 | 0.07 | 62M |
| `generatedStreamClock` | **8.92** | 8.88 | 9.01 | 0.04 | 112M |
| `hand` | 3.14 | 3.06 | 3.18 | 0.04 | 318M |

Paired: stream clock **−7.09 ns (−44.1%)**, 9/9, disjoint. The clock finding holds on native too, at
roughly half the JIT proportion.

**Epsilon GC survived 200M events on every arm** — the zero-steady-state-allocation property holds for
a real generated processor, which is a result in its own right and was previously only shown for the
hand-written model.

### 6.1 A prediction of mine that was wrong

I expected the 3-arm binary to be the reason these are far from 1.42 ns, since round 58 measured
identical source at 4.86 vs 1.58 ns in multi-arm vs single-purpose binaries. So I built single-purpose
binaries. **The opposite happened for the generated arm** (5 reps each, medians):

| arm | 3-arm binary | single-purpose binary |
|---|---|---|
| `generatedStreamClock` | 8.92 | **11.07** (slower) |
| `hand` | 3.14 | **2.41** (faster) |

The multi-arm penalty round 58 found applies to the hand-rolled arm and **reverses** for the generated
one. Recorded rather than explained: I do not know why, and it is not required for anything below.
*(Different binaries — T1 forbids the harness from reporting this as a comparison, and it is not
presented as one.)*

### 6.2 What the 1.42 ns floor actually measured

The floor arm is `V1_bare.java`:

```java
BaseProcessor p = new BaseProcessor();
for (long i = 0; i < it; i++) p.handleEvent(set(e, i));
```

`BaseProcessor` is the **hand-written stand-in** from §0 — its own javadoc says "what the generator
emits". It calls `handleEvent` directly and has no auditors, no `processEvent` re-entrancy wrapper and
no callback dispatcher. **The 1.42 ns figure is a measurement of that class, not of generated code.**

The spec is not silent about this: §1 states the base case is "no auditors, no re-entrancy wrapper".
But **no auditors and no re-entrancy wrapper is a configuration the generator cannot currently
produce** — `clock`, `nodeNameLookup` and `serviceRegistry` are emitted into every processor, and
`processEvent` wraps every event. That configuration is the *target* of W1, W2 and W4, which is why
those work items exist.

Lined up, with the caveat that round 58 ran on a different machine state and absolute cross-run
comparison is weak:

| what was measured | native, no PGO |
|---|---|
| round 58 `V1_bare` — **hand-written `BaseProcessor` model** | **1.42** |
| round 58 `fluxtionStreamClock` — **real generated processor**, ladder | 13.98 |
| this round `generatedStreamClock` — real generated, 3-arm binary | 8.92 |
| this round `generatedStreamClock` — real generated, single-purpose | 11.07 |
| this round `hand` — `HandBase`, single-purpose | 2.41 |

Round 58's own ladder measured the real generated processor at **13.98 ns** and its floor table reports
**1.42 ns** in a column headed "Fluxtion". Both numbers are in the same round's data. The difference
between them is not noise, not shape, and not the harness — it is that one measured generated code and
the other measured a model of it.

**What is safe to say from this round:**

- The generated processor, base case with a stream clock, no PGO: **~9–11 ns native, ~5 ns JIT** on
  this machine, with gates passed and output verified.
- Generated dispatch **is** cheap. The gap to the hand-rolled arm is the auditors, the entry wrapper
  and the clock — exactly what W1/W2/W4 target — not the node dispatch the generator emits.
- **1.42 ns is a target, not a current measurement of generated code**, and "0.95× hand-written /
  703M events per second" compares two hand-written classes to each other.

**This is an owner call, not a defect finding.** The modelling is legitimate and labelled inside round
58. The risk is only in how the number travels: the floor table's column heading is "Fluxtion", and
that is the form the figure has been quoted in.

---

## 7. The target is reachable: 1.87 ns from GENERATED node structure, beating hand-rolled

**Correction to §6.2, and to two negative results earlier in this round.** Both were harness artefacts.

### 7.1 The harness shape was masking everything

Round 58's 1.41/1.53 figures come from `cpp/FxLib2.java` `batchLocal`:

```java
public static void batchLocal(long n) {
    BaseProcessor p = new BaseProcessor(); MarketTick e = new MarketTick();
    for (long i = 0; i < n; i++) p.handleEvent(e.set(...));
    outBuf = p.buffer.value; ...
}
```

**Construction and loop are adjacent, and the caller does the timing.** My harness called
`System.nanoTime()` *between* constructing the processor and entering the loop. That is an opaque call
between an allocation and its use, and it stops the compiler proving the processor never escapes.

Same code, only the harness changed:

| | nanoTime inside the method | nanoTime outside (round 58's shape) |
|---|---|---|
| stripped processor, `handleEvent` | 6.27 | **1.87** |

**3.3× from where the clock call sits.** Two conclusions I published earlier in this round — "the
constructor self-publication changed nothing" and "removing the framework fields buys ~1%" — were both
measured through the blocked harness and are **withdrawn**. They were measurements of the harness.

### 7.2 The decomposition, with the harness fixed

Native, no PGO, `batchLocal` shape, output identical on every arm, 3 reps:

| arm | ns | what it isolates |
|---|---|---|
| `batchGenerated` — full W4 baseline generated, via `onEvent` | 5.59 | everything |
| `batchGenDirect` — full generated, `handleEvent` called directly | 6.32 | the guard is not the cost |
| `batchStripGuard` — framework fields removed, via `onEvent` + guard | **1.88** | the guard is FREE |
| `batchDirect` — framework fields removed, `handleEvent` | **1.87** | the floor for this node graph |
| `batchHand` — hand-rolled flat | 2.47 | reference |

**Three results.**

1. **The seven framework fields are the entire remaining gap: 5.59 → 1.87, a 3.7 ns / 66% cost.**
   `callbackDispatcher`, `clock`, `nodeNameLookup`, `subscriptionManager`, `context`,
   `serviceRegistry`, `functionAudit`. None is on the hot path — only two no-op auditor calls are —
   so this is not work being done. It is that a processor holding seven live heap objects cannot be
   dissolved, and once it can be, the ten node objects dissolve with it.

2. **The re-entrancy guard is free**, 1.88 vs 1.87. Round 58 predicted exactly this: "a field check on
   an already-loaded field is close to free". W4 keeps its safety backstop at no measured cost.

3. **The generated node structure at 1.87 ns beats hand-rolled Java at 2.47 by 24%** — on the
   generator's own field layout and its own topological dispatch order, not on a hand-written model.
   Round 58's "generated dispatch is not slower than hand-written" now holds for generated code.

### 7.3 What the generator has to do to get there

The target is **not** blocked on dispatch, codegen shape, PGO, or the guard. It is one change:

> **Do not emit a framework field the graph does not use.**

Each is statically decidable at generation time, which is the same partial-evaluation move the
generator already makes for dispatch order:

| field | emit only when |
|---|---|
| `callbackDispatcher` | re-entrancy support is on |
| `subscriptionManager` | subscription support is on |
| `context` | a node injects `DataFlowContext`, or something above needs it |
| `clock` | a node injects `Clock`, or an auditor reads it |
| `nodeNameLookup` | `getNodeById` by node name is required |
| `serviceRegistry` | the graph consumes or exports a service |
| `functionAudit` | the graph exports a service |

W4 already added the two flags that decide the first two. The rest are graph properties the builder
holds. **This is the remaining work, and it is now measured rather than assumed: 3.7 ns of 5.59.**

### 7.4 Guidance this changes

The performance page must say **where you put the clock call matters**, not only where the processor
lives. Construct the processor and run the loop with nothing between them; time from the caller. A
profiler or a timing call placed inside that method silently costs 3.3×, and it will look like a
framework cost.

---

## 8. The control, run here — and the generated structure is already AT the target

§7 left a residual doubt: the stripped generated processor measured 1.87 while round 58 reported 1.53.
Round 58's control was available, so it was run rather than reasoned about.

### 8.1 Round 58's control reproduces exactly on this machine

`cpp/FxLib2.java` + `cpp/ExeRun.java` verbatim (only the `@CEntryPoint` wrappers removed, which carry
no work), `BaseProcessor`, native, no PGO:

| arm | round 58 | this machine |
|---|---|---|
| `batchLocal` | 1.53 | **1.53** |
| `batchStatic` | 3.10 | **3.11** |
| `batchHand` | 2.44 | **2.46** |

The machine is not the variable. **1.5 ns is reachable here.**

### 8.2 In ONE binary, the generated structure and the control are the same number

The 1.53-vs-1.87 difference was **binary composition** — the control binary has three arms, the
comparison binary six. Round 58 measured the same effect (4.86 vs 1.58 for identical source). Put both
in one binary and the question answers itself:

| arm | ns | |
|---|---|---|
| `batchBase` — **round 58's `BaseProcessor`, the control** | **1.87** | |
| `batchDirect` — generated node structure, framework fields stripped | **1.87** | identical to the control |
| `batchStripGuard` — as above, plus the W4 re-entrancy guard | **1.87** | the guard is free |
| `batchGenerated` — full generated processor as it ships today | 5.52 | |
| `batchHand` — hand-rolled flat | 2.49 | both beat it by 25% |

**Three statements are now measured, not inferred.**

1. **The generator's node structure is already at parity with the hand-written model.** 1.87 vs 1.87,
   same binary, same clock, identical output. There is no residual codegen penalty to remove — not
   dispatch order, not field layout, not the typed entry. §7.2's phrase "the floor for this node graph"
   was too weak: it is *the same floor as the model*.
2. **The W4 guard costs nothing**, confirmed a second way.
3. **The whole remaining gap is the seven framework fields: 5.52 → 1.87.** Nothing else.

### 8.3 What this means for the target

**The target is not a target for the codegen. It is a target for what the generator EMITS ALONGSIDE
the codegen.** Given a graph that uses none of them, a processor carrying no `callbackDispatcher`,
`clock`, `nodeNameLookup`, `subscriptionManager`, `context`, `serviceRegistry` or `functionAudit`
lands on the control's number today, with W4's guard still in place.

That makes §7.3's work item the whole job, and it is now bounded: **emit no framework field the graph
does not use.** W4 supplies the two flags that decide `callbackDispatcher` and `subscriptionManager`;
the other five are graph properties the builder already holds.

### 8.4 A methodological note, because it bit twice in one round

Two figures in this round were wrong for two *different* harness reasons, and both looked like
framework costs:

- `System.nanoTime()` placed between constructing the processor and entering the loop — **3.3×**
  (§7.1), because an opaque call between an allocation and its use blocks the escape proof.
- The number of arms compiled into the binary — **1.53 vs 1.87 for identical source** (§8.2).

Neither is a property of Fluxtion. Both are properties of the measuring program. Round 58's rule —
*every figure must name its shape* — is now demonstrated to extend to the harness itself, and the
conformance bench's insistence on one binary is what makes the arms above comparable at all.

---

## 9. Progressive isolation — it is an escape-analysis CLIFF, not "the framework fields"

§7 and §8 said "the seven framework fields cost 3.7 ns". **That framing is wrong and is corrected
here.** Testing them one at a time, all arms in one binary, native, no PGO, output identical:

| variant — 10 node objects plus… | ns |
|---|---|
| P0 nothing | 1.87 |
| P1 `callbackDispatcher` | 1.88 |
| P2 `clock` | 1.86 |
| P3 `nodeNameLookup` | 1.86 |
| P4 `subscriptionManager` | 1.87 |
| **P5 `context` (+ its 3 constructor args)** | **6.18** |
| P6 `serviceRegistry` | 1.86 |
| P7 `functionAudit` | 1.87 |
| **P8 all seven** | **6.43** |
| `batchBase` — round 58's control | 1.87 |
| `batchHand` | 2.45 |

**Six of the seven are free.** Every one of them, individually, costs nothing.

### 9.1 It is not `context` either

`P9CtxArgsOnly` — context's three constructor args, with **no context field at all** — measures
**6.20**, the same as P5. `P10CtxNulls` — context constructed with four nulls — measures 6.18. So
`MutableDataFlowContext` is not the cause; the objects around it are.

### 9.2 It is a threshold, and it is sharp

| combination | ns |
|---|---|
| `subscriptionManager` alone | 1.87 |
| `subscriptionManager` + `callbackDispatcher` | 1.88 |
| `subscriptionManager` + `nodeNameLookup` | 1.88 |
| **`subscriptionManager` + `callbackDispatcher` + `nodeNameLookup`** | **6.22** |
| `callbackDispatcher` + `clock` + `nodeNameLookup` — also three | **1.87** |

Three extra fields are fine or fatal **depending on which three**. Nothing lands between 1.9 and 6.2;
it is a cliff.

The distinguishing property is the size of the allocation graph, not the field count.
`SubscriptionManagerNode` allocates **five** objects of its own — an `ArrayList` and three `HashMap`s,
plus a `DataFlow` reference — where `Clock` allocates none and `NodeNameAuditor` and
`CallbackDispatcherImpl` one each. Past some total, **GraalVM's escape analysis stops dissolving the
processor**, and the ten node objects that were being scalar-replaced become real allocations again.

**That is why the cost is 3.7 ns and yet nothing extra runs on the event path.** No work was added. The
compiler simply stopped removing work it had been removing.

### 9.3 What is actually on the event path

Confirmed by reading the generated dispatch, for the `MarketTick` path:

- `nodeNameLookup.eventReceived(typedEvent)` — inherited default, **no-op**
- `nodeNameLookup.processingComplete()` — inherited default, **no-op**
- `clock.eventReceived(...)` — **the only real work**, and only when the Clock auditor is registered;
  it calls `System.currentTimeMillis()` (§4)

Nothing else. The re-entrancy guard is a field test and is **free**: `batchStripGuard` 1.87 against
`batchDirect` 1.87, confirming round 58's prediction for a check on an already-loaded field.

**And with no auditors the clock should not be emitted at all.** Today the baseline configuration
removes it from the auditor *map* — so no per-event call — but the *field* is still generated. It is
free on its own (P2), but it is one more object in the allocation graph that decides the cliff.

### 9.4 What this changes about the work

The work item is no longer "elide seven fields". It is narrower and better founded:

1. **Emit no framework field the graph does not use** — still correct, and now the reason is that each
   one consumes escape-analysis budget rather than that each one costs time.
2. **`subscriptionManager` is the expensive one to keep**, because it brings five objects. It is the
   first to elide, and W4's `supportSubscriptions=false` already decides it.
3. **Drop the `clock` field when no auditor and no node needs it**, per §9.3.
4. **The budget is finite and shared.** Removing any three of these may be enough; removing the wrong
   three achieves nothing. **Elision has to be measured, not counted** — which is exactly what the
   progressive harness above is for, and it should be kept.

### 9.5 The cliff is AOT-only — and that inverts the usual assumption

Everything in §9 is **`native-image`, no PGO, epsilon GC**. Graal's *partial escape analysis* is what
dissolves the processor, and the same compiler backs both native-image AOT and Graal JIT — so the
obvious question is whether the cliff exists on the JIT. **It does not.** Same classes, same harness,
same machine:

| arm | Graal JIT | native-image |
|---|---|---|
| P0 nothing | 4.64 | **1.87** |
| P4 `subscriptionManager` | 4.68 | 1.87 |
| Q3Fields — the combination that falls off the cliff | **4.55** | **6.22** |
| P8 all seven | 4.74 | 6.43 |
| `batchBase` — the control | 4.57 | 1.87 |
| `batchHand` — hand-rolled flat | **2.09** | 2.45 |

**On the JIT every arm is flat at ~4.6 ns**, whether the processor carries zero extra fields or all
seven. There is no cliff because **the JIT never dissolves the processor to begin with** — the ten node
objects stay real, so adding more objects costs nothing. There is nothing left to lose.

**AOT dissolves them and the JIT does not.** 1.87 vs 4.57 for identical code, a 2.4× advantage to
native-image — and note the JIT is *faster* than AOT on the flat hand-rolled arm (2.09 vs 2.45), so
this is not "AOT is faster". It is specifically that only AOT removes the graph's object structure.

This restates round 58 addendum 6 — *"the JIT never closes this gap; only profile-guided AOT does,
which reverses the usual assumption that a JIT with runtime profiles beats AOT"* — with two additions:
it happens **without PGO**, and the resulting optimisation is **fragile in a way the JIT's is not**.

**Consequences for the work.**

- **Field elision pays on native-image only.** On a JIT it buys nothing measurable. The work item is
  worth doing, and it is worth saying plainly that it is an AOT optimisation.
- **A JIT deployment cannot reach 1.87 by configuration.** Its floor for this graph is ~4.6, and the
  only lever that ever moved it was flat-state codegen (round 58 addendum 14), which is rejected
  because it cannot be applied to components you do not own.
- **The performance page must separate the two runtimes here**, or a JIT user will spend effort on an
  elision that does nothing for them.

### 9.6 Leave-one-out: no single field helps either

§9.2 showed each field is free when ADDED alone. The reverse was tested too — remove exactly one from
the full seven, all arms in one binary, native, no PGO:

| dropped | ns | | dropped | ns |
|---|---|---|---|---|
| none (all seven) | 6.20 | | `context` | 6.17 |
| `callbackDispatcher` | 6.18 | | `serviceRegistry` | 6.23 |
| `clock` | 6.27 | | `functionAudit` | 6.28 |
| `nodeNameLookup` | 6.14 | | **nothing at all** | **1.87** |

**Every removal is useless and every addition is free.** The effect is purely cumulative: the budget is
consumed by the total allocation graph, and once past it no single removal recovers anything.
Confirmed against the earlier partial results — all-minus-`subscriptionManager` 6.16, and
all-minus-`subscriptionManager`-and-`clock` 6.23.

**On `nodeNameLookup` being the one that is populated at construction:** a fair hypothesis, and these
variants do not model it — they construct `new NodeNameAuditor()` with **empty** maps, because
`nodeRegistered` is never called. In the real generated processor `initialiseAuditor(nodeNameLookup)`
populates them. But population cannot be what triggers the cliff, because **removing the field
entirely does not help** (6.14). Whether population makes the *real* processor worse still is
untested and is a separate question from the cliff.

**Consequence for the work item.** "Emit no framework field the graph does not use" remains right, but
its payoff profile is now known: on an unprofiled native image it is **all-or-nothing**, so partial
elision delivers nothing measurable. Either get under the budget or use PGO (§9.7), which removes the
sensitivity entirely.

### 9.7 An accurate profile removes the cliff completely

Profiles collected per arm and merged, as round 58's `build-pgo.sh` did:

| arm | native, no PGO | native + merged PGO |
|---|---|---|
| no framework fields | 1.86 | **1.56** |
| **all seven framework fields** | **6.20** | **1.58** |
| `batchBase` — round 58's control | 1.84 | 1.58 |
| `batchHand` — hand-rolled flat | 2.45 | 1.55 |
| `batchGenerated` — the real generated processor | 5.52 | 4.84 |

**With an accurate profile the field composition stops mattering** — 1.56 against 1.58, and both match
round 58 addendum 6's 1.55 exactly. The structural sensitivity documented in §9.2–9.6 is a property of
**unprofiled** AOT, not of AOT.

This does not contradict "a bad profile is worse than none": these profiles were collected from every
arm and merged, so they are accurate for what was measured. A profile from the wrong entry point took
1.41 → 6.28 in round 58. **The variable is the profile's accuracy, not PGO.**

**One arm did not converge.** `batchGenerated` — the actual generated processor — stays at 4.84 even
with the profile, while a hand-built class carrying the same seven fields reaches 1.58. The difference
is what the real constructor does beyond declaring fields: `serviceRegistry.setDataFlowContext(context)`
publishes `context` into `serviceRegistry`, and `initialiseAuditor(nodeNameLookup)` runs. Those are
reference-graph edges the P-variants do not have. **That is the next thing to test**, and it is a
better-founded target than field elision.

---

## 10. THE TARGET IS REACHED — 1.57 ns from fully generated code

### 10.1 The cause: every node is published into a HashMap at construction

`initialiseAuditor(nodeNameLookup)` calls `auditor.nodeRegistered(node, "name")` for **every node**,
and `NodeNameAuditor` stores them in two `HashMap`s. Every node object escapes into a live heap
structure, so none can be scalar-replaced and the whole graph materialises.

Isolated — one field, maps empty vs maps filled, nothing else changed:

| | ns |
|---|---|
| `nodeNameLookup` field, maps empty | **1.83** |
| same field, ten nodes registered into it | **6.22** |

### 10.2 It is invisible without a profile

This is why it survived. Remove the registration calls from a real generated processor and measure
**without** PGO: 5.55 against 5.53 — **no change**. That regime is saturated by other effects, so
anyone benchmarking unprofiled concludes registration is free.

Only under an accurate profile does it separate, and then it is the whole story:

| arm | no PGO | + accurate PGO |
|---|---|---|
| real generated processor, registration ON | 5.55 | **5.07** |
| real generated processor, registration OFF | 5.55 | **1.57** |

**PGO cannot rescue it**, because it is a genuine escape rather than a budget problem — unlike the
seven framework fields (§9), which an accurate profile removes entirely.

### 10.3 The result, generated end to end

`supportNodeNameLookup` added to `EventProcessorConfig`, gated in the generator. Generated by the
compiler with the full baseline configuration, **no hand editing**, native image, output identical on
every arm:

| arm | no PGO | + accurate PGO |
|---|---|---|
| **generated, full baseline config** | 5.55 | **1.57** |
| generated, registration still on | 5.55 | 5.06 |
| round 58's `BaseProcessor` control | 1.87 | 1.58 |
| hand-rolled flat equivalent | 2.54 | 1.55 |

**1.57 ns — about 637M events/sec — from generated code**, matching the hand-written control at 1.58,
hand-rolled flat at 1.55, and round 58's own 1.55.

**The target is met, and it is met by the generator rather than by a model of it.**

### 10.4 The complete recipe

1. void triggers (`failBuildIfMissingBooleanReturn = false`)
2. `setSupportDirtyFiltering(false)`
3. `setSupportReentrancy(false)` — guard retained, and free
4. `setSupportSubscriptions(false)`
5. **`setSupportNodeNameLookup(false)`** — the one that matters, and the one nobody could see
6. an **accurate** PGO profile, collected from what you deploy
7. construct the processor inside the method that runs the loop, and time from the caller

Items 5 and 6 are jointly necessary: 5 without 6 measures no better, and 6 without 5 stops at 5.07.

### 10.5 Two costs that look identical and are not

| | seven framework fields (§9) | node registration (§10) |
|---|---|---|
| mechanism | escape-analysis **budget** exhausted | a **genuine escape** into a live map |
| cost without PGO | 1.87 → 6.20 | invisible (5.55 either way) |
| cost with accurate PGO | **none** — 1.58 | **5.07 vs 1.57** |
| fixed by PGO | yes, completely | no |
| fixed by elision | only if you get under the budget | yes, completely |

Reading either as "framework overhead" and applying the other's remedy gets nothing. **This is why the
progressive harness was worth keeping.**

---

## 11. The lookup problem is SOLVED, and the capability trades were unnecessary

§10 reached the floor by turning node-name lookup **off**. That was the wrong fix. The right one keeps
every capability and costs nothing.

### 11.1 Generate the mapping as code, not as data

`NodeNameAuditor` stores name↔node in two `HashMap`s, which publishes every node and stops the graph
being dissolved. But the generator **knows every name and every field at build time**, so it can emit
the mapping as code that reads a field on demand and stores no reference:

```java
public <T> T getNodeByIdGenerated(String id) throws NoSuchFieldException {
    switch (id) {
        case "mid": return (T) mid;
        ...
    }
}
public String lookupInstanceNameGenerated(Object node) {
    if (node == mid) { return "mid"; }
    ...
}
```

**This is the same move W12 made for `getAuditorById`**, and the same one the generator already makes
for dispatch order: decide it at build time, where the information is.

**On "a switch does not scale":** it never runs on the event path — only when someone asks for a node
by name — so its cost is irrelevant. What matters is that it holds nothing. A Java string switch is a
`hashCode` lookupswitch plus `equals` and is fine at thousands of cases; the reverse identity chain is
linear but equally off the hot path.

### 11.2 With the switch in place, every capability can stay on

Four configurations, generated end to end, native + accurate PGO, lookup **verified live** before
timing (`getNodeById("mid")` asserted non-null):

| config | re-entrancy | subscriptions | buffering | node registration | ns |
|---|---|---|---|---|---|
| A | off | off | off | off | 1.57 |
| B | off | **on** | off | off | 1.57 |
| **D** | **on** | **on** | **on** | off | **1.57** |
| C | off | on | off | **on** | 5.55 |
| hand-rolled flat | — | — | — | — | 1.55 |

**Registration is the only variable that matters.** Re-entrancy, subscriptions and buffering are all
free — config D carries the full wrapper, the subscription manager and the buffering branch and still
lands on 1.57.

### 11.3 What this retracts

The recipe in §10.4 asked for four capability trades. **Three were unnecessary:**

- `setSupportReentrancy(false)` — **not needed for performance.** The wrapper is free. (The flag is
  still worth having: it removes a capability some deployments genuinely do not want, and the guard
  it leaves behind is also free.)
- `setSupportSubscriptions(false)` — **not needed.**
- `setSupportNodeNameLookup(false)` — **not needed once lookup is generated.** The flag traded away a
  capability to buy something a codegen change gives for free.

Only the two that were always **semantic** choices remain real: void triggers and
`setSupportDirtyFiltering(false)` change what the graph does, and that is the developer's call.

**The conclusion the owner reached before the measurement did:** it is all escape analysis. Nothing
here was framework overhead in the sense of work being performed. It was one data structure holding
references that the compiler needed to be free of, and generating that structure as code removes it
without removing anything else.

### 11.4 Also tested: `isDirty("test")` in `init()`

A generated `init()` contains `isDirty("test")` — "initialise dirty lookup map". Removing it from a
real generated processor changed nothing (1.57 either way), because with dirty filtering off the maps
are already elided (§9) and the call resolves to a constant. **Not a factor**, though it is dead code
in that configuration and should not be emitted.

### 11.5 Remaining limitation, stated honestly

Registration is skipped for the name lookup because the generator replaces it. **Any OTHER auditor that
consumes `nodeRegistered` — an audit log that names its nodes — still receives the calls, and will
still publish every node.** So a fully audited processor does not reach 1.57 by this route.

That is the correct trade and it is the one the analyser's whole product rests on: the audit log is
worth the nanoseconds. But it should be measured rather than assumed, and it has not been.

### 11.6 Both directions, generated, full capability — 1.57 ns

The generated processor now **implements `NodeNameLookup` itself**, so the interface's two methods are
generated rather than backed by maps:

```java
public class BenchProcessor implements … , NodeNameLookup {
    @Override public <T> T getInstanceById(String id) { switch (id) { case "mid": return (T) mid; … } }
    @Override public String lookupInstanceName(Object node) { if (node == mid) { return "mid"; } … }
}
```

Measured with **both directions asserted live before timing** — `getInstanceById("mid")`,
`getNodeById("exposure")`, `lookupInstanceName(buffer)`, `lookupInstanceName(limit)` — and with
re-entrancy, subscriptions and buffering all **enabled**:

| config | ns |
|---|---|
| **E — both switches live, every capability on** | **1.57** |
| D — id lookup only, every capability on | 1.57 |
| C — registration still populating the maps | 5.49 |
| `BaseProcessor` control | 1.59 |
| hand-rolled flat | 1.55 |

**The answer is yes.** A static switch for node id and an identity chain for instance lookup give the
full `NodeNameLookup` capability at 1.57 ns — the same as having no lookup at all, the same as the
hand-written control, and within 1.5% of hand-rolled flat code.

**It is a config option, not a redesign.** Everything else stays on.

### 11.7 Gates after the change

`fluxtion-generator-core` 21/21 · `fluxtion-builder` 230/230 ·
`fluxtion-integration-tests` **3518/3520** — the two `RuntimeMetaBoundaryGateTest` failures are the
environmental ones, unrelated to this work.

The two `PreSplitGoldenParityTest` goldens moved again, as they must: this changes generated source on
purpose. **`imperative.behaviour.txt`, `dsl.behaviour.txt`, `imperative.dto.txt` and `dsl.dto.txt` are
all BYTE-IDENTICAL**, which is the evidence that behaviour and the model did not move — only the
emitted text.

---

## 12. What audit actually costs — and it is not 4.5 ns

§11.5 left the audited case untested and guessed it was "worth the nanoseconds". **Measured, it is not
nanoseconds.** It was also easy to mistake the earlier 4.5–5.1 ns figures for an audit cost: those came
from node **registration** (§10, an escape-analysis effect), not from audit logging.

Four configurations, generated end to end, native + accurate PGO, **no-op log sink** so this measures
record BUILDING and not IO:

| configuration | ns/event | vs base case |
|---|---|---|
| no audit (base case, §11) | 1.57 | — |
| no audit, in this binary | 5.49 | binary composition, §8.2 |
| `addEventAudit()` — records, no method tracing | **155.7** | ~100× |
| `addEventAudit(INFO)` — tracing on, nodes NOT registered | **174.5** | |
| **`addEventAudit(INFO)` + node registration — a real audit log** | **884.7** | **~560×** |
| hand-rolled flat | 1.54 | |

**Two things to take from the table.**

**1. Tracing without registration is nearly useless AND still costs.** With no `nodeRegistered` calls
the `EventLogManager` resolves every node to `NullEventLogger.INSTANCE`, so the log records almost
nothing — yet the arm still costs 174 ns. **Audit and the node-name switch of §11 are in tension:**
removing registration is what buys 1.57 ns, and an audit log that names its nodes needs exactly that
registration. The switch solves `NodeNameLookup`; it does not solve `EventLogManager`.

**2. Audit allocates per event.** Every audited arm **died with `OutOfMemoryError` under epsilon GC**,
which never collects — the same harness in which every unaudited arm runs 200M events cleanly. The
zero-steady-state-allocation property holds for the base case and **does not hold with audit enabled**.
That is a stronger statement than the timing: audited runs need a collector.

### 12.1 What this changes

- **"The audit log is worth the nanoseconds" was the wrong framing**, and this repo's own performance
  page said it. It is worth hundreds of nanoseconds. That may still be the right trade — for an
  investigation, a replay, a support question, 884 ns/event is irrelevant — but it must be stated at
  the right order of magnitude.
- **The deployment split the page recommends is now quantified**: ~1.6 ns unaudited against ~885 ns
  fully audited is a 560× difference, not a rounding error. Splitting audited and throughput
  deployments is not a compromise, it is the only sane arrangement.
- **A per-node logging cost of ~88 ns** (884 over ten nodes) is the number worth attacking if audit
  throughput ever matters. Nothing in this round examined it.

### 12.2 Caveats, because these are large numbers

- **No-op sink.** Real IO is on top. This is the framework's own record-building cost.
- **Serial GC** (epsilon is impossible here, see above), so allocation and collection are included —
  which is honest for an audited deployment but not separable from the timing.
- **One fixture, ten nodes**, and the tracing cost scales with node count.
- The 5.49 ns unaudited figure in this binary is **not** comparable to §11's 1.57 — different binary,
  more arms, §8.2's composition effect. Compare within the table, not across tables.

### 12.3 CORRECTION — audit does NOT allocate per event. `printEventToString` does.

§12 claimed "audit allocates per event" on the strength of an `OutOfMemoryError` under epsilon GC.
**That was wrong**, and the owner said so before the measurement did. The allocation came from two
**configuration defaults**, not from the auditor.

Bytes per event, JVM, `ThreadMXBean.getThreadAllocatedBytes`, no-op sink:

| configuration | bytes/event |
|---|---|
| no audit | **0.000** |
| `addEventAudit()` — defaults | 208 |
| `addEventAudit(INFO)` — defaults | 208 |
| **`addEventAudit(INFO, printEventToString=false, printThreadName=false)`** | **0.006** |

`LogRecord` was designed for this: one reusable `StringBuilder`, `clear()` does `sb.setLength(0)`,
`clearAfterPublish` defaults true, and `timeFormatter` is `StringBuilder::append` on a `long`. Nothing
in that path allocates. What allocates is `event.toString()` once per event and
`Thread.currentThread().getName()` — both switched on by default.

**Confirmed the other way too:** with those two off, the audited processor runs 200M events under
**epsilon GC** without dying — the same harness that killed the default-configured audit arms.

### 12.4 The corrected audit numbers

| configuration | ns/event | bytes/event | epsilon-safe |
|---|---|---|---|
| no audit | 5.49 | 0.000 | yes |
| `addEventAudit()` — records, defaults | 155.7 | 208 | **no** |
| `addEventAudit(INFO)` + registration — defaults | 884.7 | 208 | **no** |
| **`addEventAudit(INFO, false, false)` + registration** | **~550** | **0.006** | **yes** |

**Turning off event stringification removes all the allocation and about a third of the time**
(885 → 550 ns for full tracing). What remains is genuine work: building the record text for ten nodes
into the reusable buffer.

**So the trade is CPU, not garbage.** An audited processor can be zero-allocation and still cost
hundreds of nanoseconds, and those are different objections with different remedies. §12's framing
conflated them.
