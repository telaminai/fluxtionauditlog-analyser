# Recorded baselines — the numbers a later run must reproduce

Every row is a **measured** figure with the configuration that produced it. A new measurement that
disagrees is either a regression or a changed input; the point of the table is that it cannot be
neither. `validate-controls.sh` enforces the bands in `control-bands.tsv` against this table.

**Method for every row:** interleaved arms, **minimum of ≥5 reps**, idle machine, harness invariants
asserted (records published non-zero, checksum matched, `-D` placement verified). Native: per-arm PGO
with the profile SHA recorded, and every build input verified in the build log rather than assumed.

| | |
|---|---|
| Machine | Apple M4 |
| JIT | OpenJDK 25.0.2 (build 25.0.2+10-69) |
| Native | Oracle GraalVM 25.0.4+7.1 · `native-image 25.0.4` |
| Harness | **h5** — stamps a runtime digest as well as its own version; h4 — adds a no-op record arm; h3 fixed the escaping processor |

## After the id-path fix (round 63 §20) — harness h4

`EventLogger` resolves each node and key name to an id **once per node**, not once per event.

| arm, 30-node converging, every node logs | JIT ns | native ns |
|---|---:|---:|
| fair baseline, no auditor | 19.42 | 17.64 |
| binary record, String path (before) | 74.35 | 167.82 |
| **binary record, id path (now)** | **54.6–61.1** | **82.2–115.8** |
| text record | 403.0 | 698.6 |

The native binary range spans the value-store choice: **81.3 with `VarHandle`, 115.8 with the byte
loop** that core must ship for Java 8. JIT is the other way round — 54.6 byte loop, 63.2 `VarHandle`.

> **Bands are only valid for the harness version they were recorded under.** h1/h2 numbers are not
> comparable to h3: h2 let the processor escape its loop method, which costs **4.3× on native** and
> nothing on JIT, with nothing in the build log to show for it.

## The 30-node, 5-event-type, converging-tail graph — harness h3

| Variant | Nodes | Audit | Record | Profile | JIT ns | JIT M/s | native ns | native M/s |
|---|---|---|---|---|---:|---:|---:|---:|
| **`c-dispatch`** no audit | light | none | — | `LOWEST_LATENCY` | **12.34** | 81.0 | **2.17** | **461.0** |
| `c-audit-sparse` one node logs | light | on | binary | `LOW_LATENCY_AUDIT` | 47.6 † | 21.0 | 70.9 † | 14.1 |
| `c-audit-sparse` one node logs | light | on | text | `LOW_LATENCY_AUDIT` | 147.2 † | 6.8 | — | — |
| **`c-audit-dense`** every node logs | light | on | binary | `LOW_LATENCY_AUDIT` | **81.51** | 12.3 | **119.36** | 8.4 |
| `c-audit-text` every node logs | light | on | text | `LOW_LATENCY_AUDIT` | 403.0 † | 2.5 | 698.6 † | 1.4 |
| `c-heavy` every node logs | **heavy** | on | binary | `LOW_LATENCY_AUDIT` | 485.8 † | 2.1 | 517.7 † | 1.9 |

† measured under harness h2 (processor escaping). **Re-measure under h3 before calibrating anything
against these rows.** The two bold rows are h3 and are what `control-bands.tsv` gates on.

## Derived, from the h3 rows — with the auditor as the ONLY variable

The audit cost is only meaningful against a baseline that differs by the auditor alone. A
`LOWEST_LATENCY` baseline has **172 fewer `isDirty_` references** than an audited processor, so its
delta is audit *plus* conditional propagation. The fair baseline uses the same profile and installs no
auditor; both generated sources are diffed feature-by-feature before the numbers are taken.

| arm | JIT ns | native ns |
|---|---:|---:|
| `LOWEST_LATENCY`, no auditors | 11.84 | **2.13** |
| `LOW_LATENCY_AUDIT`, **no auditor** (the fair baseline) | 29.01 | 25.47 |
| `LOW_LATENCY_AUDIT` + auditor + binary record | 82.02 | 119.34 |
| **profile cost — guards, not audit** | **17.18** | **23.33** |
| **true audit cost** | **53.00** | **93.88** |
| per audit entry (11.75 per event) | 4.51 | 7.99 |

**Anything that stops the processor dissolving costs native far more than JIT.** Three instances of one
mechanism, measured:

| | native | JIT |
|---|---:|---:|
| processor escapes its loop method | 4.3× | 1.0× |
| dirty filtering on | **11.9×** | 2.5× |
| audit record built per event | +93.9 ns | +53.0 ns |

**The guards skip nothing on this graph** — the auditor shows guards-on and guards-off invoking
identical nodes (13/10/11), because each event reaches its chain by topology. So the 17/23 ns is the
pure cost of guards with zero benefit, and a guard breaks even only when
`P(skip) × cost(node) > ~1.4 ns` (JIT). Light nodes: never. Heavy nodes (~34 ns): at ~4% skip rate.

## Control bands — harness h5, verified green

`validate-controls.sh` gates on these. All five pass as recorded.

| control | toolchain | band | measured |
|---|---|---|---:|
| `c-dispatch` no audit | jit | 9.0–13.0 | 11.4 |
| `c-dispatch` no audit | native | 1.9–2.3 | **2.05** |
| `c-profile-only` profile, no auditor | native | 15.0–18.4 | 16.9 |
| `c-audit-dense` every node logs | jit | 40.6–58.4 | 54.7 |
| `c-audit-dense` every node logs | native | 56.7–69.3 | **64.1** |

**JIT bands are ±18%, native ±10%** — from each toolchain's *measured* batch variance, not a flat
number. A band tighter than the noise fails on a healthy machine, which trains people to ignore it.

## Current shippable configuration — core, Java 8, no generation

`LOW_LATENCY_AUDIT` (guards off) + `BinaryEventLogger` + `BinaryLogRecord` with `long[]` slots.
30 nodes, 5 event types, every node on the path logging, 11.75 entries/record, harness h4.

| | JIT | native |
|---|---:|---:|
| baseline, no auditor | 19.72 · 50.7 M/s | 17.77 · 56.3 M/s |
| **audited** | **53.42 · 18.7 M/s** | **66.12 · 15.1 M/s** |
| audit cost | 33.70 | 48.34 |

Verified per run: `recPerEvent=1.000`, 188 bytes/record (11.75 × 16), matching graph checksum, and the
harness version and runtime digest stamped on the result line.

**Measured repeatability**, three batches of six, minimum per batch:

| | spread of minima | CV |
|---|---:|---:|
| native audited | **0.053 ns** | **0.05%** |
| native baseline | 0.106 ns | 0.31% |
| JIT audited | 6.673 ns | 5.49% |

So native figures are quoted to three decimals and **JIT figures are quoted as approximate** — at 5.49%
they are not repeatable to the precision this table would otherwise imply. `measure.sh` enforces 2%
native / 6% JIT and refuses anything looser.

## Known inputs that change the answer — isolate one at a time

Hold every other column in `binaries.tsv` equal; the difference is then attributable.

| Input | Effect | § in round-63 notes |
|---|---|---|
| **harness: processor escapes the loop method** | **4.3× on native, 0 on JIT** | 16 |
| `-H:-SpawnIsolates` | −24 ns audited; ~−0.7 ns baseline | 13.2 |
| record type binary vs text | 3.2× sparse, **5.1× dense** | 12.6 |
| audit density | the variable behind most of the spread | 12.6 |
| node weight | dilutes the record-format gap 4.83× → 1.56× | 14.1 |
| GC epsilon vs serial | 0–14 ns | 9.5 |
| PGO profile SHA | decides which regime a build lands in | round 60 |
| `-march=native` | small regression — do not add it | 11.4 |
| `--initialize-at-build-time` | 9.6 ns worse | 13.2 |
| widened `PriorityForceInline` | 4.4 ns worse | 13.2 |
| build lottery, same config rebuilt | **±8 ns audited · ±0.002 ns dispatch** | 15.2, 16 |

> **The lottery is wider than most single-flag effects on the audited graph.** Any audited difference
> below ~8 ns needs several builds per configuration before it means anything. One build per arm is how
> "method-level inlining works" was nearly published — the control that killed it was a second build of
> the *same* configuration reading 119.1 against 126.9.

> **`LOW_LATENCY_AUDIT` once disabled the audit log entirely.** Before trusting any audited number,
> confirm `recPerEvent > 0` and count `auditor.nodeRegistered` in the generated source.

## The code-model ceiling (round 63 §33) — harness h5

!!! danger "Superseded by §34 below — these ran against a record with three hot-path faults"
    The comparison method here stands. The conclusion does not: after the record was profiled and fixed,
    the ordinal arm became **slower** than the arm it was built to beat. Kept because a superseded
    baseline that is labelled is evidence, and a deleted one is a gap.

How much of audit cost is reachable by specialising the call sites, measured rather than argued. Four
arms differing **only** in how a node reaches the record; records byte-identical (188 B, one per event),
checksum equal, one runtime digest, and — for native — **three independent PGO builds per arm**, because
the audited build lottery (±8 ns) is larger than the effect.

| arm | JIT ns | native, 3 builds | native mean |
|---|---:|---|---:|
| `c-audit-noaudit` audit not generated | 12.879 | 2.104 | — |
| `c-audit-ceiling` record bound to the node | 45.943 | 46.4 · 44.5 · 37.9 | **42.95** |
| `c-audit-ordinal` `declareKeys` + `info(0, v)` | 49.688 | 46.8 · 46.9 · 43.9 | **45.88** |
| `c-audit-string` `info("v", v)` — ships today | 56.264 | 50.0 · 47.0 · 48.8 | **48.61** |

| comparison | pairings won | native effect | separated? |
|---|:---:|---:|---|
| ordinal vs String | **9 / 9** | 2.73 ns | yes, p ≈ 0.05 |
| ceiling vs String | **9 / 9** | 5.66 ns | yes, p ≈ 0.05 |
| ceiling vs ordinal | 6 / 9 | 2.93 ns | **no** — ranges overlap |

**Effect estimates are means across builds, not minima.** `measure.sh` minimises *within* a build, which
removes measurement noise; minimising *across* builds samples the lucky tail of the lottery and would
report 3.11 and 9.16 ns — figures no deployment would see.

**The ceiling arm has the widest build spread: 8.56 ns** against 2.95 and 2.97. Removing the logger
removed code that was constraining the compiler, and the lottery widened with it.

!!! warning "`c-audit-dense` and `c-audit-string` are the same shape and disagree by ~17 ns"
    Both are "every node logs, binary record, `LOW_LATENCY_AUDIT`". `c-audit-dense` native reads 64.1 and
    `c-audit-string` native reads 46.9. Neither is wrong and the difference is not a regression — they
    are different **binaries**: `c-audit-dense` is the `nimg28` image, built before `-H:-SpawnIsolates`
    (worth ~24 ns audited) and against an older runtime digest.

    The older control is kept deliberately. Deleting it would erase the evidence that a flag found late
    in the round was worth more than every source-level change measured after it, and a control whose
    band still passes is doing its job even when a better configuration exists. **Compare like with
    like: the current audited arm is `c-audit-string`.**


## After profiling the record (round 63 §34) — harness h5

A JFR profile of the audited JIT path put `EventLogger.keyRef` at **37%** of samples and
`IdentityHashMap.get` at **19%** — 56% resolving names that never change. Three fixes followed: the event
type resolved through the identity table that already existed instead of the fallback map; the first two
key ids held as fields on the logger instead of two per-logger arrays; the node id resolved in the
logger's constructor instead of re-checked per entry. A redundant per-entry boolean store went with them.

| arm | JIT before | JIT after | native before | native after |
|---|---:|---:|---:|---:|
| no audit | 12.879 | 13.410 | 2.104 | 2.104 |
| **String keys — ships today** | 56.264 | **42.605** | 48.61 | **42.73** |
| ordinal keys | 49.688 | 44.063 | 45.88 | 45.99 |
| ceiling | 45.943 | 43.972 | 42.95 | 39.09 |

Native columns are the mean of three independent PGO builds. Post-fix native detail —
str: 43.737 · 41.341 · 43.119 · ord: 46.999 · 46.452 · 44.504 · ceil: 38.564 · 36.424 · 42.291.

| | JIT | native |
|---|---:|---:|
| shipped arm | −13.66 ns (**−24%**) | −5.88 ns (−12%) |
| **audit cost over the same-graph no-audit arm** | 43.39 → **29.20** (**−33%**) | 46.51 → 40.63 (−13%) |
| throughput | **23.5 M/s** | **23.4 M/s** (best build 24.19) |

**AOT and JIT are now level on the audited path** — 42.7 against 42.6. The 1.5–1.9× native deficit
recorded through this whole round was never a property of the toolchain: it was the JIT speculating its
way through pointer-chasing that closed-world compilation had to execute.

**The ordinal arm is now a pessimisation**: 3.26 ns slower than the shipped arm on native (9 of 9 build
pairings) and 1.46 slower on JIT. `keyRef` is two reference compares against fields; `ordinalRef` is an
array load with a bounds check and a resolved-test. The optimisation was worth something only while the
thing it replaced was broken.

**The ceiling still leads on native** — 39.09 against 42.73, 8 of 9 pairings, 3.64 ns — by removing the
logger object from the entry path entirely. That is the honest remaining headroom, and it is smaller than
what profiling found twice.


## Final state of round 63 (§35–§36) — harness h5

The ordinal and ceiling arms are gone; binary tracing works; the per-entry level gate reads a cached int
instead of dereferencing the level enum.

| arm | JIT | native, 3 builds | native mean |
|---|---:|---|---:|
| audit not generated | 12.4 | 2.078 | — |
| audit calls present, nothing listening | 12.2 | — | — |
| audit calls **removed from node source** | 10.8 | — | — |
| **audited, binary record — what ships** | **~41.0** | 40.285 · 40.915 · 42.073 | **41.09** |

**The no-op audit call costs 1.40 ns across 11.75 call sites** — 0.12 ns each (12.218 against 10.822).
This was measured because a JFR profile showed `EventLogger.info` as the leaf frame in **71% of samples**
on the no-audit control, which would have been a large and entirely fictitious hotspot. HotSpot inlines
the node's arithmetic into `info` and the sampler attributes the inlined frame. **Recorded here as the
clearest example in this kit of why a profile sizes nothing.**

**The cached level gate is worth 1.64 ns on native** (42.73 → 41.09, mean of three builds, 8 of 9
pairings) and **0.87 ns on JIT, which is inside the 6% JIT noise floor and is therefore not a
performance claim.** It is kept because it removes a dependent load, unified eight duplicated level
checks, and made the unset-level case safe without a null check — not because JIT got faster.

**`c-audit-dense` (jit) has been retired.** It shared a classpath with `c-audit-string`, so it was never
an independent control, and after §34 its band described code that no longer exists. The native row is
kept: it is a genuinely different binary, built before `-H:-SpawnIsolates`, and it is the evidence that
one build flag was worth more than every source change measured after it.


## The published benchmark, re-measured (2026-09-09) — harness h5

The core documentation site reported **50 M/s and ~20 ns/event** from a JMH run against **Fluxtion 9.7.5,
January 2025**, under the old `com.fluxtion` group id, on an unrecorded machine, JIT only, at a single
configuration. That benchmark — a four-node market-data price ladder doing real array work — was ported
to the current runtime and re-measured across every profile, on both toolchains, against a hand-written
Java control **and a hand-written C++ control**. All three produce byte-identical checksums at every
iteration count; without that they are different programs.

| configuration | audit | JIT ns | native ns |
|---|:---:|---:|---:|
| C++ `-O3 -march=native` | no | — | **1.158** |
| hand-written Java | no | 6.390 | 4.297 |
| `LOWEST_LATENCY` | no | 9.407 | **4.474** |
| no configuration at all | no | 15.070 | 14.708 |
| `LOW_LATENCY_AUDIT` + `BINARY` | yes | 20.436 | **18.161** |
| `LOW_LATENCY_AUDIT` + `TEXT` | yes | 42.298 | 50.607 |
| `AUDITED` + tracing | yes | 112.327 | 200.806 |

Native no-audit is the mean of three independent PGO builds — hand 4.299 / 4.292 / 4.301, generated
4.511 / 4.507 / 4.404. **Framework cost = 0.18 ns, 4.1%**, and the build lottery that dominates an
audited path is essentially absent here (0.009 ns spread on the hand-written arm).

**Native wins the lean and binary-audit paths; the JIT wins text-heavy ones by up to 1.8×.** The
toolchain follows the profile.

### The C++ gap is the platform's, not the framework's

C++ is 3.7× ahead of hand-written Java on this workload — and hand-written Java uses no framework at all,
so none of that gap is attributable to Fluxtion. **Two explanations were tested and both refuted:**

- *auto-vectorisation of the five-element loops* — `-fno-vectorize -fno-slp-vectorize` made C++ **faster**
  (1.056 ns), not slower
- *data layout* — a Java variant using one flat `int[]` for all 10,000 ladders, matching the C++ struct,
  was **slower** (5.908 ns), because the object form's fixed-length-5 arrays let the compiler remove
  bounds checks a computed base index defeats

Array bounds checking is the leading remaining candidate. **It has not been measured and is not claimed.**

!!! danger "Every previously published binary-record figure was taken under a clock mode users cannot get"
    `-Dclock=process` made both `logTime` and `endTime` reuse a cached reading. With the record fixed to
    take `logTime` from process time and `endTime` live, the dense-audit graph moved **41.0 → 55.4 ns**.
    The subsequent clock and `endTime` changes then took it back down. The lesson is not the numbers: it
    is that a benchmark switch in production code let the benchmark measure a configuration that did not
    ship, for the entire life of this work.

## The C++ target (round 63 §39) — harness h5

Controls for the C++ generation target, held to the same discipline as the Java ones: fixed graph, fixed
input, and a checksum every arm must agree on before any timing is believed.

**These are rebuildable, not merely re-runnable.** The Java native controls point at binaries in a
session scratch directory; if the machine is wiped they are gone. The C++ sources and a build script live
in `cpp/`, so `build-controls.sh` regenerates the processor with the C++ target and recompiles it.

### 30-node converging graph, 5 event types — all arms `v=120.5988`

| arm | ns | M events/sec |
|---|---:|---:|
| **`c-cpp-conv` — generated C++, templated on parents** | **0.738** | 1355 |
| C++ hand-written, direct members | 0.850 | 1176 |
| Java generated, native AOT (`c-audit-noaudit`) | 2.055 | 487 |
| C++ hand-written, parent pointers | 5.625 | 178 |
| Java generated, JIT | 12.336 | 81 |

**The ratio to watch is `c-cpp-conv` against `c-audit-noaudit` native: 2.8×.** That single number is the
C++ target's whole claim, and a regression in either arm moves it.

### 4-node price ladder — all arms `v=2185441000`

| arm | ns |
|---|---:|
| C++ hand-written, flat | 1.166 |
| **`c-cpp-ladder-fat` — generated, one fat node, no re-entrancy** | **1.214** |
| generated C++, one fat node, re-entrancy on | 3.580 |
| generated C++, four nodes *(not gated — see below)* | ~4.50 |
| Java generated, native AOT | 4.200 |

### One arm is deliberately not a control

`c-cpp-ladder` — the four-node ladder — was refused three times running at **2.23%, 2.57% and 2.83%**
batch spread against a 2% limit, while the other two C++ arms sit at **0.3% and 0.25%**. So it is that
arm, not the toolchain.

A control whose reading the harness will not certify cannot detect a regression, because it cannot
produce a number. Widening the limit to admit it would weaken the gate on the two arms that are
comfortably inside it. Its measurement is recorded above; it is simply not gated.

### What the C++ controls are for

They exist because **every C++ figure quoted before they did was from hand-written code modelling what a
generator would emit**, and the real generator was 3.7× off it. A control that measures generated output
is the only thing that keeps that from happening again.

### C++ audit controls (round 63 §40)

The counterparts of the Java audit arms, and — verified before any timing was believed — producing the
**same record shape**, 188 bytes per record on the dense graph.

| arm | C++ ns | Java native ns | C++ advantage |
|---|---:|---:|---:|
| **dense audit** — 30 nodes, every node logs (`c-cpp-audit-dense` / `c-audit-string`) | **30.50** | 39.16 | 1.28× |
| **audit machinery** — 4-node ladder, no node logs (`c-cpp-audit-machinery`) | **11.64** | 16.48 | 1.42× |

Audit cost over the same graph with auditing off:

| | C++ | Java native |
|---|---:|---:|
| dense, 30 nodes | 30.50 − 0.74 = **29.76** | 39.16 − 2.06 = 37.10 |
| machinery, 4 nodes | 11.64 − 4.47 = **7.17** | 16.48 − 4.20 = 12.28 |

**The C++ advantage is smaller on audit than on dispatch** — 1.3–1.4× against 2.8× on the 30-node
dispatch arm. The audit path is dominated by work neither compiler can remove: a clock read, the slot
stores, and the publish decision. Dispatch is where a compiler that can see the whole graph wins.

!!! warning "The record shape was wrong first, and the checksum could not tell"
    The C++ dense arm produced **172** bytes per record against Java's **188** — same graph, same
    checksum, one fewer entry. The Java `Tail` node logs *two* entries,
    `auditLog.info("v", v).info("n", 1L)`, a double and a long; the C++ body logged one.

    The checksum is computed on the graph's value and is blind to what was logged, so it passed. Only
    `avgRecBytes` caught it. **A cross-language audit comparison needs the record shape checked as well
    as the result** — otherwise it compares two different amounts of work and reports the difference as
    a speed-up.


## DSL controls — the generated artefact, both targets (2026-09-09)

Apple M4, OpenJDK 25.0.2, Oracle GraalVM 25.0.4+7.1, clang (Apple 21.0.0). `map -> map -> filter ->
aggregate` emitted for both targets by the generator under `LOWEST_LATENCY`, no audit, 20M events,
min-of-6, 2 reps. **Every arm checksums 488000000** — the filter rejects a little over half the data,
so that equality is a semantic check and not only an arithmetic one.

| arm | rep1 | rep2 | band |
|---|---:|---:|---|
| C++ `-O3` | 2.761 | 2.757 | 2.5 – 3.1 |
| C++ `-O2` | 3.528 | 3.566 | *not a control — see below* |
| Java JIT | 10.751 | 10.421 | 9.4 – 11.9 |
| Java native AOT, `-H:-SpawnIsolates` | 10.536 | 10.398 | 9.4 – 11.6 |
| Java native AOT, isolates on | 11.058 | 11.036 | *not a control* |

**C++ is 3.8x the Java DSL** (2.757 against 10.398). Not the 23x once published from a hand-written
chain the generator does not emit, and not the 2.9x that `-O2` would have suggested.

### Compiler options, measured rather than assumed

| option | effect |
|---|---|
| `-O3` over `-O2` | **-22%**, 3.55 → 2.76 — the only one that pays |
| `-march=native` | nothing (2.770 against 2.761) |
| `-flto` | nothing — the processor is one translation unit already |
| PGO | **+6%, i.e. WORSE** (2.93 against 2.76) |

The prediction was that `-O2` had already inlined the templated chain and PGO would be the largest win.
Both were wrong, and the second was wrong backwards. The profile was collected on the same periodic
input the benchmark replays, so it told the compiler what the branch predictor already knew at run
time, while costing the layout and inlining decisions of an instrumented build. Recorded in
`dsl/PREDICTIONS-AND-RESULTS.md`.

### Native AOT loses to JIT on this shape

The only graph in this kit where that holds. A DSL chain is many small flow-function objects, and the
JVM's advantage is escape analysis on a processor that does not escape its loop — the bargain a
closed-world compiler does not strike the same way. `-H:-SpawnIsolates` recovers it to level. This was
predicted, on the strength of one contrary reading from a hand-rolled bench earlier in the round; it
now reproduces on the generated artefact, which is what makes it a property of the shape rather than of
that harness.


## Re-measured after M53 (2026-09-09) — the C++ arm regressed 21%

Same graph, same harness, same flags; the only change is the emitter, which gained trigger overrides,
windowing, flatMap, groupBy and count in between. Checksum `488000000` on every arm, as before.

| arm | before M53.4 | after M53 |
|---|---:|---:|
| C++ `-O3` | 2.757 / 2.757 | **3.343 / 3.363** |
| Java JIT | 10.421 / 10.751 | 10.639 / 10.509 |

**C++ is 21% slower; Java is unchanged.** The ratio moves from 3.8x to **3.1x**.

### A claim withdrawn

The M53.4 commit said of the trigger-override machinery: *"the two constants fold away at compile time —
the graphs measured earlier emit the same machine code."* That was reasoning, not measurement, and it is
wrong. `overrideUpdateTrigger_` and `overridePublishTrigger_` do fold, but
`fireEventUpdateNotification()` now also clears four mutable flags on every event:

```cpp
overrideTriggered_ = false;
publishTriggered_ = false;
publishOverrideTriggered_ = false;
resetTriggered_ = false;
```

Four stores per node per event, on a five-node chain, at ~3 ns total. That is the 0.6 ns.

**The fix is not to remove them** — they are Java's semantics and a node that gains a trigger needs them.
It is to emit them only for nodes that HAVE a trigger override, which the emitter already knows: the
`Triggers` record is passed to every struct builder and is all-false for most nodes. That is a change
worth making and worth measuring, in that order — this entry exists because the order was reversed once
already.

### Fixed, and it over-recovered

Emitting the flags only for nodes that have an override: **2.653 / 2.663 ns**, against 3.35 with them
everywhere and 2.757 before the trigger machinery existed. The ratio to Java is now **4.0x**. Predicted
2.80-3.00 and 3.6x — the direction was right and the size under-called, which is the better way round.

The falsifier mattered and was refuted: `-O3` had NOT eliminated the stores, despite the flags being
private and every read of them folding to a compile-time constant. "The optimiser will handle it" is a
prediction, not a fact — the second time that assumption has been wrong in this kit.

### Still not measured

Windowed, flatMap and groupBy graphs. Their shapes differ enough from `map -> map -> filter ->
aggregate` that nothing here predicts them, and `TEST-INDEX.md` says so.
