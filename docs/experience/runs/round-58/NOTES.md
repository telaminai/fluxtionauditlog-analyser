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
