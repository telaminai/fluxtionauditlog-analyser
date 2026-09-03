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
