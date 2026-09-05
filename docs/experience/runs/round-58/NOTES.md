# Round 58 — the same processor on nine runtimes

**Date:** 2026-09-03. **Machine:** Apple silicon, macOS, single core, closed loop.
**Question:** the published benchmark ([round-54](../round-54/BLOG-NUMBERS.md)) measured one JDK.
Does *"derived orchestration costs ~1.3 ns/event"* survive a change of compiler?

**Short answer: no — it is a range, and the compiler is what moves it.**

> Derived orchestration costs **+0.61 to +6.10 ns per event** depending on how the code is compiled:
> ~7% under profile-guided AOT, 11–22% on a JIT, and **72–122% under AOT with no profiles**.

---

## Method

Identical bytecode everywhere: compiled **once** with `javac 21.0.9`, then run on every runtime, so
the variable is the runtime and never the compiler front end. Every arm asserts on its own output
(`breaches`, `updates`, `buffer`) and **all nine runtimes produced identical values** — the
correctness gate is checked before any timing is compared.

Each cell is the **median of 5 runs of 200M events**, warmup 5M, under `-XX:+UseEpsilonGC -Xmx256m`
so any steady-state allocation is fatal rather than collected.

| arm | what it is |
|---|---|
| `plainInline` | hand-written, all arithmetic in one method, no guards — the physical floor |
| `plainGuarded` | hand-written, **replicating the generated shape**: per-node objects, dirty flags, guard methods |
| `fluxtionStreamClock` | the generated processor, clock strategy injected (`setClockStrategy`) |
| `fluxtionDefault` | the generated processor, default wall clock |

`plainGuarded` is the comparator that matters. `plainInline` does not buy the same semantics.

### Runtimes

| label | build |
|---|---|
| `corretto21-c2` | Amazon Corretto 21.0.9, C2 — **the published baseline** |
| `openjdk25-c2` | OpenJDK 25.0.2, C2 |
| `graal25.3-c2` | GraalVM CE 25.3.4.1, `-XX:-UseJVMCICompiler` — same build, C2 |
| `graal25.3-graaljit` | GraalVM CE 25.3.4.1 (jdk 25.0.4.1, `jvmci-25.3-b22`), Graal JIT + libgraal |
| `oracle25-graaljit` | Oracle GraalVM 25.0.4 LTS, Graal JIT |
| `graal25.3-native` | CE 25.3 native-image |
| `graal25.3-native-march` | CE 25.3 native-image, `-march=native` |
| `oracle25-native` | Oracle 25.0.4 native-image |
| `oracle25-native-pgo` | Oracle 25.0.4 native-image, **PGO** from merged 4-arm profiles |

The `graal25.3-c2` row exists to separate *distribution* from *compiler*. Without it, any Graal gain
could be the JDK build rather than the JIT. It landed within 0.3% of stock OpenJDK 25 — so the gain
is the compiler.

---

## Result 1 — the ladder (`results/all-results.csv`, 180 rows)

Median ns/event:

| arm | Corretto 21 C2 | JDK 25 C2 | G25.3 C2 | **G25.3 Graal JIT** | Oracle JIT | G25.3 native | Oracle native | **Oracle +PGO** |
|---|---|---|---|---|---|---|---|---|
| plainInline | 2.87 | 3.28 | 3.21 | **2.12** | 2.09 | 3.03 | 3.03 | **1.52** |
| plainGuarded | 7.04 | 7.47 | 7.24 | **6.74** | 6.83 | 7.86 | 7.88 | 8.96 |
| **generated** | **8.58** | 8.77 | 8.80 | **7.71** | 8.03 | 13.54 | 13.98 | 9.58 |
| generated, wall clock | 18.45 | 19.71 | 19.97 | 19.01 | 19.01 | 21.44 | 20.87 | 19.94 |

### Cost of derived orchestration (generated vs `plainGuarded`)

| runtime | generated | hand-written | delta | throughput |
|---|---|---|---|---|
| **G25.3 CE, Graal JIT** | **7.71** | 6.74 | +0.97 ns · **+14.4%** | **129.7M/s** |
| Oracle 25, Graal JIT | 8.03 | 6.83 | +1.20 ns · +17.6% | 124.6M/s |
| Corretto 21 C2 *(published)* | 8.58 | 7.04 | +1.54 ns · +21.9% | 116.5M/s |
| OpenJDK 25 C2 | 8.77 | 7.47 | +1.30 ns · +17.4% | 114.1M/s |
| G25.3 CE, C2 | 8.80 | 7.24 | +1.56 ns · +21.5% | 113.6M/s |
| **Oracle native + PGO** | 9.58 | 8.96 | +0.61 ns · **+6.8%** | 104.4M/s |
| G25.3 CE native | 13.54 | 7.86 | +5.68 ns · +72.3% | 73.8M/s |
| Oracle native, no PGO | 13.98 | 7.88 | +6.10 ns · +77.3% | 71.5M/s |

**Fastest absolute throughput is GraalVM CE 25.3 with the Graal JIT** — not Oracle, not native, not
PGO. CE beat Oracle's JIT by 4%, but those are different release trains (CE innovation vs Oracle LTS),
so treat that as suggestive, not clean.

### Observations

- **JDK 25 C2 is slower than JDK 21 C2 on every arm** (+2% to +14%). Observed, not explained. It means
  "upgrade the JDK" and "switch to Graal" pull in opposite directions, and the published baseline
  being JDK 21 flatters JDK 21.
- **`-march=native` changed nothing** (13.53 vs 13.54). The AOT penalty is not instruction selection.
- Allocation held at 4e-6 B/event on every JVM arm — 800 bytes total across 200M events, a fixed
  setup cost. **Native arms report 0.0 because JMX is absent, not because allocation differs**;
  Epsilon not exhausting a 256 MB heap is the actual evidence there.

---

## Result 2 — PGO is not optional for native images

Oracle GraalVM 25.0.4, same distribution throughout, so this comparison is clean:

| arm | JIT | native | **native + PGO** | PGO vs native |
|---|---|---|---|---|
| plainInline | 2.09 | 3.03 | **1.52** | **−49.6%** |
| plainGuarded | 6.83 | 7.88 | 8.96 | +13.7% |
| **generated** | 8.03 | 13.98 | **9.58** | **−31.5%** |
| generated, wall clock | 19.01 | 20.87 | 19.94 | −4.5% |

**Skipping PGO costs 31.5% of throughput on the generated arm.** It is the single highest-leverage
flag in the entire matrix.

The mechanism is consistent with speculation: closed-world AOT has no profile, so it cannot
devirtualise or hoist the way a profiling JIT does. Handing it the profile the JIT would have
collected anyway recovers most of the loss. **That is a hypothesis consistent with the data, not a
measurement of the mechanism.**

**Read the +6.8% carefully.** PGO does not make the abstraction nearly free. It made the generated arm
31.5% *faster* and the hand-written arm 13.7% *slower* — the gap closes from both ends. The ranking of
the two implementations depends on the compiler.

PGO also produced a **smaller** image (11.0 MB vs 13.9 MB) in **less build time** (17.8s vs 29.7s),
because the profile tells the compiler what is cold.

### Profile collection

Profiles were collected from **all four arms and merged**, not just the measured one. Profiling only
the arm under test would overfit the benchmark to itself.

---

## Result 3 — one big `onEvent` method (`results/flat-results.csv`, 75 rows)

**Origin: the generator used to emit a single large dispatch method; that was stopped over a concern
it would not JIT-compile.** The concern names a real cliff — HotSpot's `HugeMethodLimit` is 8000
bytecode bytes, and a method above it is **never JIT-compiled at all**, running interpreted forever.

`scripts/flatten.py` rewrites the generated processor into that shape: all nine `guardCheck_*()`
bodies inlined as boolean expressions, plus `auditEvent()` and `afterEvent()`, into one
`handleEvent(MarketTick)`.

### The sizing answer

| | `handleEvent` bytecode | % of the 8000 limit |
|---|---|---|
| current (many small methods) | 180 bytes | 2.2% |
| flattened (one method) | **276 bytes** | **3.5%** |

Flattening costs ~27 bytes per node. **The cliff arrives at roughly 290 nodes triggered by a single
event type** — and the limit is per method, so a large graph spread across several event types may
never approach it. The original concern was sound in mechanism and wrong by ~30× in scale. The right
guard is a bytecode-size check at generation time, not a blanket architectural choice.

### Does flattening help?

Within-run comparison, arms interleaved under identical conditions:

| runtime | original | flat | change | verdict |
|---|---|---|---|---|
| Corretto 21 C2 | 9.19 | 9.18 | −0.1% | no effect (ranges overlap) |
| G25.3 CE Graal JIT | 8.03 | 7.99 | −0.4% | no effect (ranges overlap) |
| Oracle 25 Graal JIT | 7.70 | 7.69 | −0.1% | no effect (ranges overlap) |
| Oracle native, no PGO | 14.12 | 14.24 | **+0.9%** | slightly **worse**, ranges disjoint |
| **Oracle native + PGO** | 9.55 | **9.12** | **−4.5%** | **real**, ranges disjoint |

**Prediction registered before the run was wrong in both native cases.** It predicted flattening would
help native-without-PGO (it hurt, slightly) and be redundant under PGO (it is the one place it helps).
The JIT half of the prediction — no effect, because a JIT inlines nine tiny methods anyway — held.

Flattened + PGO gives the lowest abstraction cost measured anywhere: **+2.5% over hand-written**.

So the answer to *"is there any point?"* is: **not for JIT deployment, yes for native+PGO**, worth
about 4.5%, and the size concern that stopped it does not bite until ~290 nodes per event type.

---

## Result 4 — measurement drift (`results/drift-check.txt`)

Re-running the **identical** binary 25 minutes later, after three native-image builds had loaded the
machine:

| | run 1 | 25 min later | drift |
|---|---|---|---|
| plainGuarded | 7.04 | 7.22 | +2.4% |
| fluxtionStreamClock | 8.58 | 8.79 | +2.4% |

**Rule for this round: compare only within a single CSV.** `all-results.csv` and `flat-results.csv`
were collected in different machine states; their absolute numbers are not comparable. Every
comparison drawn above is within-file.

This also bears on the published figure. Round-54 reports **+1.32 ns / 19%**; re-running that exact
configuration here gave **+1.54 ns / 21.9%**. Each number reproduces within ~2%, but the headline is a
*difference of two ~8 ns quantities*, so ±2% on each becomes **±17% on the delta**. The single figure
is more fragile than two decimal places imply — which is the second reason to publish the range.

---

## Native-image portability

The generated processor built with `--no-fallback` and **no configuration at all**, in 20 seconds.
Fluxtion resolves its wiring at *its* build step, so by the time closed-world analysis runs there is
nothing dynamic left — no reflective construction, no proxies, no classpath scanning.

One thing does need registering. Reflection appears in exactly three places in the generated source —
`getNodeById`'s fallback, `getAuditorById`, and `newInstance` — and **none is on the dispatch path**:

```
onEvent        → zero reflection, fully native
getAuditorById → this.getClass().getField(id).get(this)
```

The first native run failed with `NoSuchFieldException: clock` from the benchmark's *assertion*
harness, which reads the clock auditor to prove the injected stream clock is in force. Fixed with
`scripts/reflect-config.json`. **The reflection registration a user needs is proportional to how much
they introspect the graph, not to how much work it does.**

A harness defect found the same way: `ladder-native.sh` discarded stderr, so the first pass silently
produced a table with two arms missing rather than an error. A benchmark script that swallows stderr
will report a partial ladder as a complete one.

---

## What this does not support

- **One fixture, ten nodes, one machine, one core, closed loop.** Dispatch is straight-line so linear
  scaling is expected, but that is a prediction.
- **The devirtualisation explanation for the AOT penalty is a hypothesis.** What is measured is the
  asymmetry: native costs the generated arm ~4× what it costs the hand-written one, and PGO removes
  most of it.
- **CE vs Oracle JIT is not a clean comparison** — different release trains (CE 25.3 innovation,
  Oracle 25.0.4 LTS).
- **Native allocation is unmeasured, not measured-zero.** JMX is absent under SubstrateVM.
- **Startup was measured best-of-5 wall clock**, not a rigorous startup benchmark: JVM 76 ms vs native
  21 ms for 100k events, 10.4 MB binary vs a 636 MB JDK. That is what native buys, and for a
  long-running processor at 100M+ events/sec it is the wrong trade.

---

## Reproducing

```bash
cd docs/experience/runs/round-58
# the flattened processor is DERIVED from round-54's generated source and inherits its
# vendor-domain copyright header, so it is gitignored (rule 1). Regenerate it:
python3 scripts/flatten.py \
  ../round-54/generated/com/bench/gen/BenchProcessor.java \
  flat/com/bench/gen/BenchProcessorFlat.java

# compile ONCE with JDK 21, run everywhere
javac -d classes -cp $FLUXTION_RUNTIME <round-54 src + plain + generated> src/GraalBench.java
scripts/ladder.sh $JAVA_HOME <label> [flags...]        # JIT ladder
scripts/build-native.sh <name> [native-image flags]    # native image
scripts/build-pgo.sh                                   # 3-stage PGO
```

`scripts/flatten.py` was verified to reproduce the exact file that was measured, byte for byte.

---

# Addendum — is there a better *shape* for the generated code?

**Question:** PGO recovers 31–36% under AOT. Is that compensating for something wrong with the
generated code's shape, and is there an annotation that would fix it at the source?

**Answer: the shape is essentially fine. Four hypotheses were tested and four failed. The only two
levers that matter are PGO and not emitting auditor calls.**

## Hypotheses tested and killed

| hypothesis | test | result |
|---|---|---|
| node calls aren't devirtualised | read the generated field declarations | **dead** — every node field is a concrete type (`public final transient Mid mid`), so `mid.calc()` is already a direct call. Nothing to devirtualise. |
| guard methods aren't inlined | hand-inline all nine (`flatten.py`) | **dead** — −1.8% on native. Inlining them by hand is what an inliner would do. |
| pre-analysis inlining limits are too tight | `-H:InlineBeforeAnalysisAllowedNodes=200 AllowedInvokes=40 AllowedDepth=40` (defaults are 1/1/20) | **dead** — −3.8%. |
| `DataFlow` interface dispatch can't be devirtualised under closed world | `TypedBench`: same processor, one implementation, reference held as interface vs concrete vs typed-arg | **dead** — native 14.06 (interface) vs 14.03 (typed). **0.2%.** |

The interface hypothesis was the most plausible and is worth recording as wrong: an earlier reading
blamed a harness with three `DataFlow` implementations loaded for a penalty that grew from +77% to
+138%. That shift was **machine drift, not polymorphism** — `TypedBench` isolates the type situation
and finds nothing.

One number reframes the whole question: **hand-written is 6.25 ns native vs 6.47 ns JIT.** Native
compiles the actual arithmetic *faster*. The penalty is entirely in the machinery around the
computation — and it is not the machinery's shape.

## The ladder of levers (native, generated arm)

| lever | ns | vs stock |
|---|---|---|
| stock native | 14.06 | — |
| flatten to one method | 14.62¹ | −1.8% |
| raise inlining limits | 14.32¹ | −3.8% |
| remove 4 bookkeeping auditor calls | 13.89¹ | −6.7% |
| **remove ALL 6 auditor calls** | **11.21** | **−20%** |
| **PGO** | **9.34** | **−36%** |
| **no auditors + PGO** | **8.12** | **−42%** |

¹ measured in the `aud-results.csv` batch against a 14.89 stock baseline; percentages are within-file.

**Best native configuration reaches +2.8% of hand-written** (8.12 vs 7.90 ns, both native, both
measured back to back). On the JIT, removing all six auditor calls is worth 9% (7.83 → 7.12).

## What that means for the generator

1. **PGO is the deployment lever, not a code-shape problem.** Nothing generated differently comes
   close to it. Document it for native users; it is worth 36% and costs one extra build stage.
2. **Auditor emission is the one real codegen lever** — 20% on native, 9% on JIT. The generated
   dispatch currently calls three auditors' `eventReceived` and three `processingComplete` on
   **every event**, hard-coded into `handleEvent`/`afterEvent`. Note this is emitted structure, so
   clearing `getAuditorMap()` at runtime cannot remove these call sites; only generating without them
   can. A build-time switch that omits the calls when nothing audits would capture this.
3. **Do not reach for `@AlwaysInline`.** It exists in `com.oracle.svm.core.annotate`, but raising the
   inlining thresholds globally — a strictly stronger intervention than annotating individual
   methods — bought 3.8%. It would also make the runtime jar depend on GraalVM internals, which is a
   poor trade for a framework that must run on stock HotSpot.

## Still unexplained

With **all six auditor calls removed**, native is still 11.21 ns against 7.90 ns hand-written —
**+42% with no auditors at all**. No shape hypothesis tested here accounts for that residual, and PGO
closes most of it without any shape change. Identifying it would need the compiled machine code for
both arms compared directly (`-H:+PrintAssembly` or `perf`), which was not done. **The residual is
unexplained, not explained-and-small.**

## Files

`results/aud-results.csv` (auditor + inlining ablations), `results/typed-results.csv` (interface vs
concrete), `src/AudBench.java`, `src/TypedBench.java`, `src/BareBench.java`. The `NoAud` and `Bare`
processors are hand-edited experimental variants derived from the generated source — **not shipped
shapes** — and are gitignored with the rest of `flat/` for the same rule-1 reason.

---

# Addendum 2 — the AOT penalty found: it is the generic entry wrapper

**Question:** with all auditors removed, native was still 11.21 ns against 7.90 ns hand-written.
Addendum 1 left that unexplained. This resolves it, from the compiled machine code.

## The dispatch method is not the problem

Both binaries rebuilt with `-H:-DeleteLocalSymbols`, dispatch methods extracted with `objdump`
(`asm/asm-gen.txt`, `asm/asm-hand.txt`):

| | instructions | real calls | fp arith | loads | stores | branches |
|---|---|---|---|---|---|---|
| generated `handleEvent` | **276** | 0 (23 `bl` are cold NPE stubs) | 24 | 46 | 34 | 31 |
| hand-written `onTick` | 288 | 0 (23 cold NPE stubs) | 24 | 49 | 44 | 32 |

**The generated dispatch compiles to fewer instructions than the hand-written equivalent**, with
identical floating-point work and no un-inlined calls. Nothing is wrong with the emitted dispatch.

## The cost is the wrapper around it

Hand-written hot loop: `main → PlainGuarded_onTick`. One call to a leaf.

Generated hot loop: `main → processEvent`, which makes four un-inlined calls **per event**:

```
processEvent
  ├─ bl CallbackDispatcherImpl_queueReentrantEvent
  ├─ bl BenchProcessorBare_triggerCalculation
  ├─ bl onEventInternal ──→ bl handleEvent   (plus blr x30, an indirect call)
  └─ bl CallbackDispatcherImpl_dispatchQueuedCallbacks
```

This is re-entrancy queueing and callback draining, executed on every event whether or not the graph
has any re-entrant callbacks.

## Measured (`EntryBench`: same processor, wrapper vs direct `handleEvent`)

| runtime | via generic entry | direct to dispatch | wrapper costs |
|---|---|---|---|
| JIT | 7.29 | 6.89 | **0.40 ns** (−5.5%) |
| **native** | **11.22** | **7.84** | **3.38 ns (−30.1%)** |
| native + PGO | 8.16 | 7.39 | 0.77 ns (−9.4%) |

Hand-written native is **7.90 ns**. Calling the generated dispatch directly gives **7.84 ns** — the
generated processor is **faster than the hand-written equivalent** once the wrapper is bypassed.

**The entry wrapper is 3.38 ns of the 3.31 ns gap.** It accounts for the whole thing. A JIT inlines it
and folds the always-empty queue check to a predictable branch; closed-world AOT without profiles
leaves four real calls. PGO recovers most but not all of it.

This is the same effect round-54 saw on a JIT and correctly called small — *"the typed entry point
buys nothing today, because it still routes through the generic path, paying the type dispatch and
re-entrancy machinery anyway"*, worth ~0.59 ns. **Under AOT the same machinery is worth 3.38 ns.**

## Auditors: the earlier reading was wrong

`DefaultProbe` isolates the six auditor calls with unremovable work in the loop:

| | work only | +6 inherited defaults | +6 explicit overrides |
|---|---|---|---|
| JIT | 1.3152 | 1.3065 | 1.2883 |
| native | 1.4752 | 1.4526 | 1.4475 |
| native + PGO | 1.3455 | 1.2549 | 1.3157 |

**Empty default-method calls are free on every runtime, native included**, and inherited defaults are
indistinguishable from explicit overrides. Generating no-op auditor calls was a sound decision.
Addendum 1's *"remove all six auditors → −20% on native"* conflated the five free ones with
`clock.eventReceived`, which does real work (an interface call into `ClockStrategy`). Consolidating
auditor call sites would not help: there is nothing there to save.

A first attempt at this probe measured **0.0000 ns on every arm** — both compilers deleted the entire
loop, because six calls that do nothing have no observable effect. That null result is retained as the
reason the probe carries a `work()` accumulator.

## The better shape, and it is statically decidable

**Emit a typed entry that calls the dispatch directly, and omit the re-entrancy wrapper when the graph
provably has no re-entrant callbacks.** Whether any node queues a re-entrant event or registers a
callback is known at generation time — if none does, `queueReentrantEvent` and
`dispatchQueuedCallbacks` are dead code on every event, and the queue is provably always empty.

That is the same partial-evaluation move the generator already makes for dispatch order: decide it
once, at build time, where the information is. Worth **30% on native**, ~5% on a JIT, and it makes the
generated processor faster than hand-written code of the same semantics.

Ranked, for the generated arm on native:

| lever | worth |
|---|---|
| **omit the entry wrapper when no re-entrant callbacks exist** | **−30%** |
| PGO | −36% (and −9% more on top of the above) |
| omit empty auditor calls | ~0% — they are already free |
| flatten to one method | −1.8% |
| raise inlining limits | −3.8% |
| `@AlwaysInline` | not needed, and not worth the GraalVM-internals dependency |

## Files

`src/EntryBench.java`, `src/DefaultProbe.java`, `src/LeanBench.java`, `asm/asm-gen.txt`,
`asm/asm-hand.txt`. Symbol-preserving builds used `-H:-DeleteLocalSymbols`; JIT assembly was **not**
obtained (no `hsdis` present), so the machine-code comparison is native-to-native.

---

# Addendum 3 — can re-entrancy be made cheap, and should auditors be hoisted?

## Why the wrapper costs what it does

`dispatchQueuedCallbacks()` is called on **every** event. Its empty fast path is not free:

```
getfield  eventProcessor          ; null check
getfield  myStack                 ; declared as java.util.Deque -- an INTERFACE
invokeinterface Deque.isEmpty()   ; <-- interface call, cannot be folded without profiles
putfield  dispatching = false     ; unconditional store even when nothing was queued
```

A JIT profiles `myStack` to one concrete type and folds the whole thing to a load and a branch.
Closed-world AOT without profiles leaves the interface call standing.

## Measured: three wrapper shapes, one binary, identical auditors and dispatch

Medians of 5 × 200M events. Arms differ **only** in the entry wrapper.

| runtime | stock | guarded drain | wrapper removed |
|---|---|---|---|
| JIT | 7.86 | 7.74 (−1.6%) | 7.31 (−7.1%) |
| **native** | **12.89** | **10.59 (−17.8%)** | **9.52 (−26.1%)** |
| native + PGO | 9.41 | 8.39 (−10.8%) | 6.87 (−27.0%) |

`guarded drain` keeps full re-entrancy semantics; it only replaces the always-taken empty check with
a field read:

```java
processing = true;
onEventInternal(event);
if (callbackPending) {                 // plain boolean field on the processor
    callbackDispatcher.dispatchQueuedCallbacks();
    callbackPending = false;
}
processing = false;
```

**The guard recovers 68% of the wrapper cost on native (2.29 of 3.37 ns) with no loss of
functionality**, and 40% under PGO. Removing the wrapper entirely is better still, but requires
knowing no re-entrant callback can occur.

Two cheaper variants of the same idea, not measured here, likely additive:
declare `myStack` as `ArrayDeque` rather than `Deque` so the call is direct; and skip the
`dispatching = false` store on the empty path.

## Q1 — hoisting auditors to one expanded call

**Performance: no.** `DefaultProbe` (Addendum 2) shows empty default-method calls cost nothing on any
runtime. There is nothing to recover. Confirmed independently here: the *original* generated shape
already **is** the hoisted form — one `auditEvent(typedEvent)` call that internally calls three
auditors — and flattening it into three inline calls measured −1.8% on native, i.e. nothing.

**Code size: yes, modestly.** Hoisted vs inlined `handleEvent` bytecode:

| | bytes |
|---|---|
| hoisted (one `auditEvent` + one `afterEvent` call) | **180** |
| inlined at the call site | 276 |

**96 bytes per event handler, ~35%.** The generator emits one handler per event type, so the saving
scales with event-type count, not node count. Worth keeping for size; it buys no speed.

## Q2 — a "no re-entrancy" compiler flag

Warranted by the measurement: worth up to **−26% on native**, −7% on a JIT. Design:

1. **Compile-time detection.** Whether any node can raise a re-entrant event or register a callback is
   visible in the graph the generator already holds — nodes injecting `EventProcessorContext`,
   `Callback`, or `DirtyStateMonitor`, or invoking the re-entrant dispatch API. If none does, the
   queue is provably always empty and the wrapper is dead code.
2. **Fail at build time, not runtime.** With the flag set and a re-entrant use detected, the generator
   should refuse to compile and name the offending node. That is the whole point: the cost is being
   removed on a proof, so violating the proof must be a build failure.
3. **Runtime backstop.** Detection cannot be complete — a node could reach the dispatcher
   reflectively or through a service. The generated processor should retain a guard that throws
   rather than silently dropping a queued event. A field check on an already-loaded field is close to
   free, as the `guarded` arm demonstrates.
4. **Default off.** This trades a capability for throughput; the safe default is the current
   behaviour.

This is the same partial-evaluation move the generator already makes for dispatch order — decide once
at build time, where the information is — applied to a capability rather than an ordering.

## Standing result

With the wrapper bypassed and auditors removed, the generated processor runs **7.84 ns** against a
hand-written implementation of the same semantics at **7.90 ns** (Addendum 2, native). **The generated
dispatch is not slower than hand-written code; the generic entry path is.**

---

# Addendum 4 — scan for other latency, and was monomorphic dispatch the right call?

## Was generating concrete-typed, monomorphic dispatch a good choice? Yes, and the assembly proves it

This is the decision the whole result rests on. Every node is a `public final transient <ConcreteType>`
field, so every `node.calc()` is a direct call, and the generated `handleEvent` compiles to **276
instructions with zero un-inlined calls** — *fewer* than the hand-written equivalent's 288, with
identical floating-point work (Addendum 2).

Had nodes been held behind an interface, they would have hit exactly the failure this round
documents three times over: interface-typed fields on a hot path are folded by a profiling JIT and
left as real dispatch by closed-world AOT. The generated dispatch is immune to that by construction.
**The generated code was never the problem; everything found in this round is in the runtime
scaffolding around it.**

## The recurring pattern: interface-typed fields called every event

| site | field | per-event call | status |
|---|---|---|---|
| `CallbackDispatcherImpl.myStack` | `java.util.Deque` | `Deque.isEmpty()` | **found, measured** (Addendum 3) |
| `Clock.wallClock` | `ClockStrategy` | `getWallClockTime()` | **found here, not yet measured** |
| `ServiceRegistryNode` maps | `Map`/`List` | none — not touched per event | not an issue |

`Clock.wallClock` is the third instance and it is on the audit path of every event. With
`setClockStrategy(() -> …)` the receiver is a lambda, and several `ClockStrategy` implementations are
reachable. This is very likely why `clock.eventReceived` was the one auditor call that cost anything
(~0.7 ns on JIT) while the other five were free.

**This pattern is worth grepping the whole runtime for**: an interface-typed field, read on the
event path, is invisible on a JIT and real under AOT.

## Boxing on the re-entrant path

`CallbackDispatcherImpl.myStack` is `Deque<Supplier<Boolean>>` — draining it does
`Supplier.get()` → `Boolean.booleanValue()`, so **every callback boxes a boolean**. Off the fast path,
but it means the re-entrant path allocates, which matters for the zero-allocation claim if anything
uses callbacks. A `BooleanSupplier` (primitive-specialised) removes it.

## Event-type dispatch: not a scaling risk

`onEventInternal` is a linear `instanceof` chain, so the concern is cost growing with event-type
count. Measured with a genuinely unknown receiver type and N distinct types live (`ChainProbe`):

| | 2 types | 5 | 9 | 16 | switch on type id |
|---|---|---|---|---|---|
| JIT | 1.3976 | 1.3667 | 1.3920 | 1.6555 | 3.4826 |
| native | 3.6242 | 4.3602 | 3.5111 | 2.8721 | 2.4707 |

**JIT grows 0.26 ns from 2 to 16 types** — real but small, and the switch-on-id alternative is
*worse* there. The native row is non-monotonic, so it is dominated by branch prediction over the
cycled types rather than by chain length. **No action recommended**, and no claim that a switch is
better.

## Methodological note — three probes folded away

Two probes in this round returned exactly 0.0000 ns or unusable numbers because the compiler
deleted what they were trying to measure:

- `DefaultProbe` v1: six empty calls with no observable effect — whole loop eliminated.
- `ChainProbe` v1: receiver declared as its concrete type, so every `instanceof` folded to a constant.

Both are kept in `src/` in fixed form. **Microbenchmarking things that are nearly free is
adversarial**: the compiler's job is to delete exactly what you are trying to time, and a suspiciously
clean zero is a broken probe, not a result.

## Ranked work, all measured on the generated arm

| change | native | JIT | note |
|---|---|---|---|
| omit entry wrapper (no-re-entrancy flag) | **−26%** | −7% | needs build-time proof + runtime guard |
| PGO at deploy | **−36%** | n/a | no code change at all |
| guarded drain (keeps re-entrancy) | **−18%** | −2% | no flag, no semantic change — do this first |
| `ClockStrategy` field devirtualisation | untested | untested | third instance of the pattern |
| hoist auditors | ~0% | ~0% | **35% smaller bytecode per handler** — size, not speed |
| flatten to one method | −1.8% | ~0% | not worth it |
| `@AlwaysInline` | — | — | not needed |

---

# Addendum 5 — floor vs floor: strip everything and compare to the lowest hand-rolled code

**Setup.** Generated processor with auditors removed, entry wrapper bypassed, and the dirty-flag
machinery removed — reduced to the single branch that can actually arrest. Compared against both
hand-written arms. **All three produce identical output** (1,050,000 breaches / 1,050,000 updates /
buffer 11551.2267), verified before timing. 7 reps, median [min-max].

| runtime | GENERATED minimum | hand-written, guarded shape | hand-written, fully inlined |
|---|---|---|---|
| JIT | **4.12** [4.00-4.19] · 243M/s | 7.14 [6.69-7.29] · 140M/s | **2.07** [2.06-2.10] · 484M/s |
| native | **5.71** [5.66-5.76] · 175M/s | 8.00 [7.98-8.06] · 125M/s | **3.03** [2.91-3.07] · 331M/s |
| native + PGO | **3.95** [3.92-4.00] · 253M/s | 8.64 [8.61-8.68] · 116M/s | **1.49** [1.47-1.50] · 671M/s |

## Two findings, and the second is the bigger one

**1. The minimum generated processor beats hand-written code of the same shape, on every runtime** —
by 42% on JIT, 29% on native, 54% under PGO. That is the strongest form of the result in this round:
strip the scaffolding and the generator wins outright against a human writing the same structure.

**2. The dirty-flag machinery is the largest single cost in the system.** Both `generatedMin` and
`handGuarded` use the same ten node objects and the same arithmetic. The only difference is that
`handGuarded` maintains nine `isDirty_*` flags, evaluates nine guard expressions and resets all nine
every event, while `generatedMin` keeps the one branch that can be false. That difference is
**3.0 ns on JIT and 4.7 ns under PGO** — larger than the entry wrapper (3.38 ns native, 0.40 ns JIT)
and far larger than auditors (~0).

## Why that matters for the generator

In this graph, **eight of the nine guards can never be false.** `TickIn.onTick` always returns `true`,
so every node up to `limit` fires on every event; only `charge`/`buffer` are ever arrested.
`PlainInline` reproduces the entire semantics with a single `if (exposure <= limit) return`.

So the machinery is paying, per event, for nine flags to express what one branch expresses. **A guard
whose predicate is statically always true is dead code**, and eliding it is the same partial-evaluation
move as everything else here.

Whether that is decidable depends on the framework's contract, and **this needs checking against the
Fluxtion reference rather than inferred** (rule 6): if a trigger method returning `void` means
*always propagate*, then its dirty flag is statically `true` and both the flag and every guard term
referencing it can be elided at generation time. Nodes returning `boolean` remain undecidable in
general — the generator cannot prove user code always returns `true`.

**This was not measurable before this round** because the entry wrapper and auditor costs were
sitting on top of it.

## The remaining gap to the absolute floor

`generatedMin` 4.12 vs `handInline` 2.07 on JIT — a 2× gap that is **not dispatch and not
orchestration**. Both run identical arithmetic; the difference is that the fully-inlined version keeps
everything in locals in one method, while the generated version loads and stores fields across ten
separate node objects. That is the price of nodes being addressable, observable, independently
testable units — which is the thing the framework exists to provide.

It is a real cost and worth stating plainly: **the graph structure itself costs about 2 ns per event
in this fixture, independent of every mechanism examined in this round.**

## Revised ladder (JIT, generated arm)

| configuration | ns | |
|---|---|---|
| stock generated | 7.86 | |
| guarded drain | 7.74 | −2% |
| entry wrapper removed | 7.31 | −7% |
| **+ auditors removed** | ~7.1 | −10% |
| **+ dirty machinery elided where statically true** | **4.12** | **−48%** |
| hand-written fully inlined (absolute floor) | 2.07 | the graph structure costs the rest |

---

# Addendum 6 — the TRUE base case: void triggers + dirty filtering off

**Correction to Addendum 5.** That addendum kept one branch to preserve semantics. That was the wrong
baseline. Removing dirty support is **the application developer's choice** — the semantics change, and
that is the point of the switch, not an accident to be worked around. The framework supports it
directly, and the API was read rather than inferred (rule 6):

```
@OnTrigger(failBuildIfMissingBooleanReturn = false)        // void trigger permitted
@OnEventHandler(failBuildIfMissingBooleanReturn = false)   // same on the entry handler
EventProcessorConfig.setSupportDirtyFiltering(false)
```

A void trigger returns no boolean, so there is no dirty flag and no guard: **every node fires every
event, unconditionally.** That is the true base case.

## Setup

Ten nodes with void `@OnTrigger` methods; the processor calls them in dependency order with no flags,
no guards, no auditors and no re-entrancy wrapper. Compared against the lowest hand-rolled equivalent
**at the same semantics** — one method, everything unconditional, no objects. Output verified
identical on both arms (1,050,000 breaches / 2,100,000 updates / buffer 11551.2267) before timing.
7 reps.

| runtime | GENERATED base (10 node objects) | hand-rolled, one method | gap |
|---|---|---|---|
| JIT | 4.75 [4.74-4.77] · 210M/s | 2.32 [2.31-2.34] · 432M/s | +105.2% |
| native | 6.39 [6.35-6.47] · 156M/s | 3.18 [3.11-3.19] · 315M/s | +101.4% |
| **native + PGO** | **1.55** [1.54-1.57] · **646M/s** | **1.54** [1.53-1.57] · **649M/s** | **+0.6%** |

## The result

**Under PGO the graph costs nothing.** 1.55 ns against 1.54 ns, ranges overlapping — ten addressable
node objects and ten calls are indistinguishable from a single hand-written method doing the same
arithmetic in locals. **646M events/sec.**

Addendum 5 attributed a ~2 ns floor to "the graph structure itself — the price of nodes being
addressable, observable, independently testable." **That was wrong, or rather it was a statement about
compilers, not about structure.** The structure costs ~2.4 ns on a JIT and ~3.2 ns on un-profiled AOT,
and **zero** once the compiler has profiles. PGO inlines the ten node methods and the object boundaries
stop existing.

Two things follow:

1. **The strongest configuration is void triggers + dirty filtering off + native-image + PGO**, and at
   that setting there is no measurable abstraction penalty at all. The developer chooses this by
   turning off a feature they do not need; the compiler does the rest.
2. **PGO is not a native-image detail — it is the thing that makes the graph free.** The JIT never
   closes this gap (4.75 vs 2.32); only profile-guided AOT does. That reverses the usual assumption
   that a JIT with runtime profiles beats AOT.

## Where each configuration lands (generated arm, ns/event)

| configuration | JIT | native | native+PGO |
|---|---|---|---|
| stock (auditors, guards, wrapper) | 7.86 | 12.89 | 9.41 |
| guarded drain | 7.74 | 10.59 | 8.39 |
| wrapper removed | 7.31 | 9.52 | 6.87 |
| guards kept, everything else stripped | 4.12 | 5.71 | 3.95 |
| **void triggers, dirty filtering off** | **4.75** | **6.39** | **1.55** |
| hand-rolled floor, same semantics | 2.32 | 3.18 | 1.54 |

Note the base case is *slower* than Addendum 5's arm on JIT and native (4.75 vs 4.12) because it does
strictly more work — `charge` and `buffer` fire on every event rather than only on a breach. It is a
different semantic, chosen deliberately. Under PGO that extra work disappears into the inlining and
the base case wins outright.

---

# Addendum 7 — the processor as a native shared library, embedded in C++

**Setup.** The base-case processor (void triggers, no dirty filtering, no auditors, no wrapper) built
with `native-image --shared` and `@CEntryPoint`, producing a 5.6 MB `.dylib` with C headers. Embedded
in a C++ harness alongside a hand-optimised C++ implementation of the same arithmetic. Buffer value
asserted identical (11551.2267) before timing. `clang++ -O3 -march=native -std=c++17`.

| arm | ns/event | events/sec | vs C++ |
|---|---|---|---|
| **hand-optimised C++** | **1.66** | 602M | — |
| Fluxtion native lib, batch (loop inside the library) | **3.10** | 323M | 1.87× |
| Fluxtion native lib, per-event across the C ABI | 7.02 | 142M | 4.2× |

## Two separate costs

**The C boundary costs ~3.9 ns per call** (7.02 − 3.10). An integrator calling event-by-event from C++
pays more for the boundary than for the processor. Batching across it recovers that entirely — which
is the design guidance: cross the ABI per batch, not per event.

**The processor itself is 1.87× hand C++ — in the un-profiled configuration.**

## The limitation that matters: this is NOT the fast configuration

PGO is what closed the gap in the Java comparison (6.39 → 1.55 ns, Addendum 6). **It could not be
applied to the shared library here**, and the result is therefore the configuration already known to
be ~2× off:

- `-R:ProfilesDumpFile` is accepted at build time, but **the dump did not fire** on
  `graal_tear_down_isolate`, with either an absolute or a relative path. No profile was produced.
- Reusing the profile from the equivalent **executable** made things worse, not better —
  2.85 → 4.19 ns. `fx_run_batch` does not appear in that profile, so it was treated as cold and
  deoptimised. **PGO profiles are entry-point specific; a mismatched profile is worse than none.**
  That is a genuine finding and a trap for anyone applying PGO to a library.
- `-march=native` on the image changed nothing (2.996 → 3.030), as it did not for the executable.

The 1.87× ratio matches the un-profiled Java ratio almost exactly (native 6.39 vs hand-Java 3.18 =
2.01×), which is consistent with the shared library simply being the un-profiled configuration.

## What is and is not established

**Established:** a Fluxtion processor compiles to a self-contained 5.6 MB native shared library with a
C ABI, embeds in C++, produces identical results, and runs at **323M events/sec un-profiled** —
1.87× a hand-written C++ implementation of the same arithmetic.

**Not established: parity with C++.** The Java measurement of **1.55 ns/event under PGO** sits in the
same range as this harness's hand C++ at **1.66 ns**, which is suggestive — but they come from
different harnesses and different clocks (`System.nanoTime` vs `steady_clock`) and were never run head
to head. **Do not quote it as C++ parity.** The experiment that would settle it is a PGO-enabled
shared library, and that requires resolving the profile dump first.

## Follow-up needed

1. Get `--pgo-instrument` to emit a profile from a shared library — via `graal_create_isolate`
   parameters, or by exercising the same entry points from an executable built from the same sources.
2. Re-run this comparison with the PGO library. **That is the experiment that tests the C++ parity
   claim.**
3. Consider a batched `@CEntryPoint` taking an array of ticks, since the per-event boundary cost
   (~3.9 ns) dominates the processor.

---

# Addendum 8 — CORRECTION: the 1.55 ns headline was a benchmark artefact

**Addendum 6 claimed the generated processor reaches 1.55 ns/event (646M/sec), within 0.6% of
hand-written Java. That number does not survive a realistic object graph, and the claim is withdrawn
in the form it was made.**

## What broke it

Putting all arms in one C++ process with one clock produced a Java hand-rolled figure of 3.11 ns —
double the 1.54 ns measured in the Java executable, for the same source. Chasing that found the cause.

`EscapeBench` runs the identical source shapes but adds **static fields holding a second instance** of
each class. Back-to-back on the same machine, native+PGO:

| binary | arm | ns/event |
|---|---|---|
| `BaseBench` (Addendum 6) | generatedBase | **1.50** |
| `BaseBench` | handBase | 1.49 |
| `EscapeBench` | localFluxtion — *same source shape* | **4.56** |
| `EscapeBench` | staticFluxtion | 2.50 |
| `EscapeBench` | localHand | 1.55 |
| `EscapeBench` | staticHand | 1.85 |
| shared library, via C++ | fluxtion generated | 4.20 |

**The hand-written arm barely moves (1.49 → 1.55). The generated arm moves 3×.**

## Why

`HandBase` is one flat object of primitive fields; instance count barely matters. `BaseProcessor` holds
**ten node objects**, and its cost depends entirely on what the compiler can prove about them:

- **`BaseBench`** — exactly one `BaseProcessor` is ever created, inside the measured method, never
  stored. Analysis proves it non-escaping and **scalar-replaces the whole graph**: ten objects become
  registers. → 1.50 ns.
- **`EscapeBench` static** — `SP` is built at image-build time (`--initialize-at-build-time`), so it
  lives in the image heap at **known constant addresses**. → 2.50 ns.
- **`EscapeBench` local** — a second instance exists, escape analysis fails, field access goes through
  real pointers. → 4.56 ns.
- **Shared library** — processor in static state, reachable from C entry points. → 4.20 ns.

**The 646M events/sec figure required the processor to be provably unique and non-escaping.** No
deployed processor is: it is held in a field, it escapes, and its classes may have several instances.

## The honest numbers

For a realistically-held processor, native + PGO, base case (void triggers, dirty filtering off):

| | ns/event | events/sec |
|---|---|---|
| **Fluxtion generated, realistic** | **4.2 – 4.6** | **217 – 238M** |
| Fluxtion generated, image-heap static | 2.50 | 400M |
| hand-rolled Java, realistic | 1.55 – 1.85 | 540 – 645M |
| **hand-optimised C++** | **1.59** | **629M** |

**So the C++ premise was right and my Java figure was wrong.** In one process with one clock, C++ at
1.59 ns beats both hand-rolled Java (3.11 in-library) and the generated processor (4.20). clang PGO
changed nothing (1.59 → 1.58) — that loop was already optimal.

## What still stands

- The generated dispatch compiles to **fewer instructions than hand-written** (276 vs 288, Addendum 2).
- The **entry wrapper**, not the dispatch, is the AOT cost (Addendum 2/3), and the guarded drain
  (−17.8% native) is real.
- **PGO is worth 31–36%** on the generated arm (Addendum 1) and a mismatched profile is worse than none.
- The generated processor **beats hand-written code of the same shape** when both hold per-node objects
  (Addendum 5) — that comparison is unaffected, because both arms carry the same object structure.

## What is withdrawn

- "**The graph costs nothing**" (Addendum 6). It costs whatever the compiler cannot prove away, and in
  a realistic program it cannot prove much. **~2.6 ns of object-field traffic against hand-rolled Java.**
- "**646M events/sec**" as a headline. It is reproducible only under single-instance scalar replacement.
- Any suggestion of **C++ parity**. C++ is ~2.6× the realistic generated figure.

## The lesson for every number in this round

**Microbenchmarks of object-graph code measure the compiler's escape analysis as much as the code.**
A benchmark that creates one instance in one method is the best case that will ever exist, and it is
not the case anyone deploys. Every future measurement here must hold the processor the way a
deployment does — in a field, escaping, with the possibility of siblings.

---

# Addendum 9 — the clock strategy: concrete typing measured, and what `mapClass` can actually reach

**Question:** `Clock.wallClock` is declared as the `ClockStrategy` **interface** and read every event.
`EventProcessorConfig.mapClass(String, String)` exists (with `getClass2replace()`). If the declaration
were mapped to the concrete type, would the wall-clock call become monomorphic and faster?

## Measured

`ClockProbe`: identical work, several `ClockStrategy` implementations reachable, all state static and
escaping (the realistic shape per Addendum 8). Only the **declared type of the strategy field** differs.

| runtime | interface-typed | concrete-typed | gain |
|---|---|---|---|
| JIT | 0.6877 | 0.6872 | **0%** |
| native | 0.8012 | 0.7230 | **−9.7%** |
| native + PGO | 0.7102 | 0.5999 | **−15.5%** |

**Directionally correct and real under AOT — but worth ~0.08–0.11 ns/event.** A JIT devirtualises it
without help, which is why this has never shown up. For scale: the entry wrapper is 3.38 ns and
object-field traffic is ~2.6 ns. This is a third-order item.

## The catch: `mapClass` cannot reach this field

`mapClass` rewrites class names in the **generated source**. `wallClock` is a private field inside the
runtime's own `com.telamin.fluxtion.runtime.time.Clock`, and the virtual call happens *inside* `Clock`.
No builder-side mapping can change a field declaration in runtime source. Three routes that do work:

1. **Reachability.** If only one `ClockStrategy` implementation is reachable in the image, closed-world
   analysis devirtualises it with no API at all. This is the cheapest fix and needs no code change —
   it is a property of what the application links, not of how it is declared.
2. **Upstream declaration change.** Declare `Clock.wallClock` as a concrete final type, or give `Clock`
   a `long` fast-path field written by the strategy.
3. **Generate the clock update inline**, with the processor holding its own concrete-typed strategy
   field. **Then `mapClass` is exactly the right hook**, because that field is in generated source.

Route 1 costs nothing and is worth checking first for anyone building natively.

## Also found while reading the config: dispatch strategy is already configurable

`EventProcessorConfig.setDispatchStrategy(...)` accepts `CLASS_NAME`, `INSTANCE_OF`, `PATTERN_MATCH`,
and `setInstanceOfDispatch(boolean)` exists alongside it. Addendum 4 measured the `instanceof` chain
and found **no scaling problem** (JIT grew 0.26 ns from 2 to 16 event types, and a switch-on-id was
*worse* on JIT). So the alternatives already exist and, on that evidence, there is no reason to change
the default — but the knob is there if a graph with many event types ever shows otherwise.

---

# Addendum 10 — what the 1.55 ns actually depended on, and the dispatch-strategy answer

Addendum 8 withdrew the headline and blamed "a second instance existing". **That diagnosis was wrong**
and is corrected here. The effect is real but the trigger is different, and it is now isolated.

## Hypotheses tested and eliminated

| hypothesis | test | result |
|---|---|---|
| an unused `static` **declaration** of the type defeats it | `StaticProc` / `StaticEvent` / `StaticBoth` | **no** — all 1.57–1.63 |
| a **second instance** existing defeats it | `TwoAlloc`, with and without | **no** — 1.58 either way |
| several competing **hot paths** in one image defeat it | single-entry-point shared library | **no** — still 4.19 |
| the loop being in a **separate method** defeats it | `ShapeA` | **no** — 1.60 |

## What it actually is

| shape | ns/event | events/sec |
|---|---|---|
| processor is a **non-escaping local** | **1.58** | 633M |
| local, loop in a separate method | 1.60 | 625M |
| processor in a **`static final` field** | **2.62** | 382M |
| same, compiled as a **shared library** | **4.19** | 239M |

**Two independent effects, each about 1–1.6 ns:**

1. **Escape analysis (+1.0 ns).** A processor that never escapes lets the compiler scalar-replace the
   node graph — ten objects become registers. Held in a field, it cannot.
2. **Shared-library code model (+1.6 ns).** The same source shape costs 4.19 as a `.dylib` against 2.62
   as an executable — position-independent code and indirect access to statics.

## The honest headline

**The number depends on how the processor is held, by up to 2.6×.** Reference points, native + PGO,
base case (void triggers, no dirty filtering, no auditors, no wrapper), all output-verified:

| | ns/event | events/sec |
|---|---|---|
| hand-rolled C++ | 1.66 | 602M |
| Fluxtion, non-escaping local *(benchmark shape only)* | 1.58 | 633M |
| hand-rolled Java, static field | ~1.85 | 540M |
| **Fluxtion, `static final` field — a realistic Java deployment** | **2.62** | **382M** |
| Fluxtion, embedded in C++ as a shared library | 4.19 | 239M |

**650M events/sec is real but requires the processor to be a non-escaping local** — true in a
microbenchmark, not in an application that stores it. **382M is the number to quote for a deployed
Java service.** Against hand-rolled Java at 540M that is +41%; against hand C++ at 602M, +58%.

Addendum 6's claim of parity stands only for the non-escaping shape. Addendum 8's *mechanism* was
wrong; its *conclusion* — that the headline needed qualifying — was right.

## Dispatch strategy (`DISPATCH_STRATEGY` already exists: `CLASS_NAME`, `INSTANCE_OF`, `PATTERN_MATCH`)

Four strategies, receiver type genuinely unknown, 256-slot cycled `Object[]`, real work per dispatch:

| runtime | strategy | 4 types | 16 types | 64 types |
|---|---|---|---|---|
| JIT | instanceof chain | 1.485 | **1.498** | 4.594 |
| JIT | **id switch** (interface `getId()`) | 1.438 | **4.569** | 4.863 |
| JIT | **getClass() identity** | 1.474 | **1.490** | **4.534** |
| JIT | pattern switch | 1.479 | 1.503 | 7.289 |
| native+PGO | instanceof chain | 1.815 | 2.425 | 6.413 |
| native+PGO | id switch | 1.726 | 1.833 | **4.240** |
| native+PGO | **getClass() identity** | **1.482** | **1.492** | 4.530 |
| native+PGO | pattern switch | 1.472 | 1.621 | 5.209 |

**`getClass()` identity comparison is the most consistently good** — best or tied at 4 and 16 types on
both runtimes, and competitive at 64. It avoids the interface call entirely, because `getClass()` is a
JVM intrinsic rather than a virtual dispatch, and each `c == E.class` is a single pointer compare.

**The reservation about `getEventId()` was well founded.** The id switch is the *worst* option on a JIT
at 16 types (4.569 vs 1.490) — the interface call costs more than the linear scan it replaces. It only
wins at 64 types under AOT, where O(1) finally beats 64 pointer compares.

Note also that `instanceof` on a **final** class is already a single class-word compare, so the chain is
much better than "linear scan" suggests — which is why it holds up to 16 types.

**You cannot `switch` on a `Class` in Java.** The options are a chain of `c == X.class` compares (what
was measured), a hash lookup on the class identity for large N, or Java 21 pattern switch — which
compiles to `invokedynamic typeSwitch` and measured well at small N but **7.29 ns at 64 types on a
JIT**, the worst cell in the table.

**Recommendation:** `getClass()` identity compares for typical graphs; consider a hash lookup on class
identity beyond ~32 event types. Do not adopt an interface-returned event id — it is slower than what
it replaces at every count except 64-under-AOT.

---

# Addendum 11 — matched holding, and the 1.58 ns figure is not reproducible

## Test environment, stated plainly

| label | VM |
|---|---|
| "JIT" | **GraalVM CE 25.3.4.1** (JDK 25.0.4.1, `jvmci-25.3-b22`), Graal JIT + libgraal |
| "native", "native+PGO" | **Oracle GraalVM 25.0.4 LTS** native-image (Substrate VM) |
| earlier C2 rows | Amazon Corretto 21.0.9, OpenJDK 25.0.2 |

## Does a `private final` instance field preserve the fast path? No — and holding does not matter

Oracle GraalVM native-image + PGO, processor held four ways:

| holding | ns |
|---|---|
| `App` local, `private final` field, no accessor | 4.78 |
| `App` in a static field, `private final` | 4.78 |
| `App` in a static field, **non-final** | 4.77 |
| bare `static final` processor, no wrapper object | **2.57** |

**`final` makes no difference at all.** Indirection through any object field costs the same as a
non-final one. On the CE JIT every shape including `static final` is ~4.7 — the JIT never gets the
benefit that Substrate's build-time image heap gives `static final`.

## The fair comparison: both implementations stateful, both held identically, same binary

The hand-written arm is also stateful and must store its values somewhere, so it is eligible for the
same optimisations. Earlier comparisons put a local C++/Java struct against a static Fluxtion
processor — not like for like. Corrected:

| shape | Fluxtion generated | hand-rolled Java | ratio |
|---|---|---|---|
| **native+PGO**, local | 4.86 | **1.56** | **3.1×** |
| **native+PGO**, `private final` field | 4.74 | **1.57** | **3.0×** |
| CE JIT, local | 4.78 | 2.34 | 2.0× |
| CE JIT, `private final` field | 4.76 | 2.35 | 2.0× |

**At matched holding, hand-rolled Java is 2.0× faster on the JIT and 3.0–3.1× faster on native+PGO.**
Holding shape is irrelevant to both arms; the hand-written implementation is simply faster.

## The 1.58 ns figure does not reproduce, and I cannot control what produces it

`fxLocal` above is the **identical source shape** to `PureLocal`, which measured **1.58 ns**. In this
binary it is **4.86 ns**. Observed range for nominally the same work:

| binary | ns |
|---|---|
| `PureLocal` / `TwoAlloc` / `StaticProc` — minimal, one use of the type | **1.58** |
| `BaseBench` — 2 arms | 1.50 |
| `ShapeB` — `static final`, loop in a method | 2.62 |
| `FieldShape` `staticFinal` — 4 arms | 2.57 |
| `SingletonBench` (3 arms), `EscapeBench` (4), `FairShape` (4) | 4.7–4.9 |
| single-entry-point shared library | 4.19 |

Hypotheses eliminated by experiment: unused static declarations; a second live instance;
several competing hot paths; the loop being in a separate method; `final` vs non-final; local vs
field. **None of them explains it.** The remaining explanation is whole-program compilation and PGO
profile allocation — decisions Substrate makes across the entire image that I cannot isolate with the
tools used here.

**Practical consequence: 1.55–1.58 ns / 650M events/sec must not be quoted.** It appears only in
minimal binaries containing a single use of the processor type, and vanishes in every binary
resembling an application.

## The defensible statement

> In a binary containing more than a trivial single use of the processor, the generated event
> processor in its fastest configuration (void triggers, no dirty filtering, no auditors, no
> re-entrancy wrapper) runs at **~4.8 ns/event (~208M events/sec)**, which is **2–3× a hand-written
> single-method Java implementation** of the same semantics, and roughly **3× hand-optimised C++**
> (1.66 ns). Embedded as a native shared library it is 4.19 ns (239M/sec).

What still stands unchanged, because those comparisons were internally valid:

- The generated **dispatch method** compiles to fewer instructions than hand-written (276 vs 288).
- The **entry wrapper** is the AOT cost; the guarded drain recovers 17.8% of it.
- **PGO is worth 31–36%** on the generated arm, and a mismatched profile is worse than none.
- The generated processor **beats hand-written code carrying the same per-node object structure**
  (Addendum 5) — the gap is the object graph itself, not the generated dispatch.

**The honest summary of this round: the cost of the graph is the ten node objects, and no compiler
setting removes it in a realistic binary.** That is the price of nodes being addressable, observable
and independently testable — which is what Addendum 5 said before Addendum 6 briefly suggested
otherwise on the strength of a benchmark that does not represent a program.
