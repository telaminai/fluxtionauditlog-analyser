# Fluxtion performance — the numbers, and how they were taken

A citable results sheet. Everything here is reproducible from `docs/experience/runs/round-54/`.

## Method, first, because it decides whether the numbers mean anything

- **10-node graph**: an event handler, eight compute nodes, and a detector that **arrests** roughly half
  of all cycles — so the conditional tail of the graph is exercised, not just a straight line.
- **One event object**, mutated and re-fired. The event stream contributes no garbage, so what is
  measured is dispatch, not allocation of inputs.
- **Node state is `double`/`long` only.** Nothing in any compute method allocates.
- **The processor is generated ahead of time** and run with **only `fluxtion-runtime` on the
  classpath** — no builder, no compiler. In-memory compilation allocates heavily and would otherwise
  have been measured instead.
- **Every arm is asserted to produce identical EXTERNALLY OBSERVABLE output** — the detector's
  `breaches` count and the final `buffer` value. These are implementations of the same function.
  **Internal invocation counts deliberately differ**: with dirty filtering disabled, downstream
  `updates` goes from 102,500,000 to 205,000,001 by design, because every node fires every cycle. Only
  the computed results are asserted equal, never the work done to reach them.
- **200,000,000 events per run, 5 JVM runs per arm**, median reported with min/max.
- **`-XX:+UseEpsilonGC`** — a collector that never frees a byte, so allocation accumulates instead of
  disappearing. **What this proves is bounded: a sustained byte per event would have exhausted the
  heap.** Sufficiently rare or fractional amortised allocation could still pass, so the claim is "no
  *measured* per-event allocation", not "provably none".

Machine: JDK 21.0.9 Corretto, macOS, single core, closed loop. **No coordinated-omission correction** —
this is a throughput harness; tails under a paced arrival rate would differ.

### Method details needed to reproduce or attack this

| | |
|---|---|
| **warm-up** | 5,000,000 events per arm before timing (20,000,000 for the latency harness), through the same code path as the measured loop |
| **heap** | `-Xmx256m -Xms256m` throughput ladder · `-Xmx64m -Xms64m` the 500M Epsilon run · `-Xmx512m` collector comparison · `-Xmx1g` audit-cost runs |
| **allocation measured by** | `com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes()`, read immediately before and after the timed loop on the same thread |
| **GC counted by** | `GarbageCollectorMXBean.getCollectionCount()` summed across collectors, same window |
| **repeats** | 5 JVM runs for the headline table · 3 for the floor ladder · **1** for the audit-cost and collector tables |
| **min/max** | reported only where 5 runs exist; single-run rows carry no spread |
| **allocation profiling** | JFR `jdk.ObjectAllocationSample`, `settings=profile` |

**Raw output:** [`blog/raw-results.txt`](blog/raw-results.txt) holds the per-run `RESULT` lines behind
the 5-run table. **The collector, audit-cost and floor tables were transcribed from stdout and their
per-run output is NOT committed** — a real gap, listed at the end.

## Throughput

| arm | median ns/event | min | max | events/sec | bytes/event |
|---|---|---|---|---|---|
| hand-written Java, one inline method | 3.15 | 2.67 | 3.25 | 317,682,191 | **0.0** |
| hand-written Java, dirty-flags + guards | 7.12 | 7.05 | 7.27 | 140,465,221 | **0.0** |
| **Fluxtion, stream-driven clock** | **8.44** | 8.38 | 8.48 | **118,491,836** | **0.0** |
| **Fluxtion, stream clock + dirty filtering off** | **5.98** | — | — | **167,322,011** | **0.0** |
| Fluxtion, default wall clock | 17.60 | 17.45 | 17.70 | 56,820,765 | **0.0** |

- **Fluxtion with a stream-driven clock is within 19% of hand-written Java carrying the same guard
  semantics** (8.44 vs 7.12 ns).
- Injecting the clock is **2.09× faster** than the default, and it is one lambda (below).
- The hand-inlined arm is **2.68× faster (8.44 ÷ 3.15)**, and that gap is the price of *not*
  hand-ordering ten computations correctly — which is the thing this project separately measured
  people and models getting wrong.

> **Two inline figures appear in this sheet, and the difference is the harness, not the code.**
> `BlogBench` (5 JVM runs, median) reports **3.15 ns**; `FloorBench` (3 runs, median) reports
> **2.92 ns** for the same arm — about 7% apart. **Every ratio below names its numerator and its
> comparator explicitly**, because mixing the two tables silently produces different multiples for
> the same claim. Where a ratio spans tables it is not quoted at all.
- **Run-to-run repeatability in this harness is tightest on the Fluxtion arm**: 8.38–8.48 ns across
  five JVM runs, a 1.2% spread, against 2.67–3.25 (18%) for the inline arm. **That is benchmark
  repeatability, not latency predictability in service** — five JVM starts on one machine say nothing
  about behaviour under a paced arrival rate.

## Dirty filtering off — the fastest mode, and exactly what it changes

`EventProcessorConfig.setSupportDirtyFiltering(false)` emits a flat, unconditional call sequence:
**zero `guardCheck_` methods and zero `isDirty_` fields**, every node fires every cycle.

| arm | ns/event | events/sec | breaches | bufferUpdates | final buffer |
|---|---|---|---|---|---|
| stream clock, **dirty on** | 8.45 | 118,276,009 | 102,500,000 | 102,500,000 | 11551.2267 |
| stream clock, **dirty off** | **5.98** | **167,322,011** | 102,500,000 | **205,000,001** | 11551.2267 |
| default clock, dirty on | 17.45 | 57,296,740 | 102,500,000 | 102,500,000 | 11551.2267 |
| default clock, dirty off | 16.90 | 59,177,200 | 102,500,000 | 205,000,000 | 11551.2267 |

**The semantic change is predictable and narrow.** Every computed value is identical — `breaches` and
the final `buffer` are unchanged. What moves is *how often downstream ran*: 102.5M → 205M invocations.
So **anything idempotent downstream is unaffected; counters and accumulators are not.** That is a rule
an author can hold in their head, which is what makes the switch usable rather than dangerous.

**An interaction worth knowing:** dirty filtering costs **2.47 ns/event** with the clock injected, but
only **0.55 ns** with the default wall clock — the syscall masks it. Turning dirty support off while
leaving the default clock in place looks nearly free and is not; the cost is hidden, not absent. Take
the clock first.

**Like-for-like, the two Fluxtion modes bracket the two hand-written arms:**

| comparison | Fluxtion | hand-written | ratio |
|---|---|---|---|
| guarded semantics (arrest honoured) | 8.45 | **7.12** — hand-written *guarded* | **1.19×** |
| unconditional (every node every cycle) | 5.98 | **3.50** — hand-written *components* | **1.70×** |

Both rows compare like with like: the guarded row against the arm that implements the same dirty-guard
semantics, the unconditional row against the arm that does the same unconditional work. **Neither is
against the 2.92 ns inline loop**, which implements different semantics — measured against that, the
unconditional figure would be 2.05× and the guarded 2.89×, and those comparisons are not like for
like.

## The floor — everything off

`setSupportDirtyFiltering(false)` + `getAuditorMap().clear()`, then two hand-edits to the generated
source to price an optimisation that **does not exist yet**: route the typed entry point straight to
`handleEvent`, skipping `processEvent` (buffering check, re-entrancy flag, `callbackDispatcher`) and
`onEventInternal` (the `instanceof` chain).

| arm | ns/event | events/sec | updates |
|---|---|---|---|
| hand-written inline | 2.92 | 342,407,122 | 102,500,000 |
| hand-written components | 3.51 | 285,078,967 | 102,500,000 |
| Fluxtion floor, `onEvent(Object)` | 5.49 | 182,225,705 | 205,000,000 |
| Fluxtion floor, `onEvent(MarketTick)` | 5.51 | 181,573,882 | 205,000,000 |
| + typed → `handleEvent` direct *(hand-edited)* | 4.92 | 203,334,689 | 205,000,000 |
| **+ last auditor removed** *(hand-edited)* | **4.88** | **204,737,629** | 205,000,000 |

**The typed entry point currently buys nothing** — 5.51 vs 5.49. `onEvent(MarketTick)` still routes
through `processEvent`, so the `instanceof` chain and re-entrancy machinery are paid regardless. The
monomorphic win exists only if the typed method dispatches directly.

**That optimisation is worth ~0.59 ns/event, about 11%.** It is real and it is not free: it removes
re-entrancy and buffering support, so it is a mode rather than a default — and the caller must commit
to a concrete event type to claim it.

**Removing the final auditor buys 0.04 ns.** `nodeNameLookup` is effectively free.

**Floor: 4.88 ns/event, 204,737,629 events/sec, zero allocation — 1.39× hand-written code doing the
same unconditional work.**

## Latency distribution — percentiles of 50-EVENT BATCH MEANS, not per-event

| arm | p50 | p90 | p99 | p99.9 | p99.99 | max |
|---|---|---|---|---|---|---|
| hand-written, guarded | 7 | 8 | 10 | 10 | 25 | 430 |
| **Fluxtion, stream clock** | **8** | **9** | **11** | **20** | **30** | 1,814 |
| Fluxtion, default wall clock | 17 | 19 | 24 | 39 | 111 | 2,316 |

**Every figure below is a percentile of the MEAN of 50 consecutive events, not of individual events.**
`System.nanoTime()` costs ~16 ns for a pair against an ~8 ns operation, so per-event timing would
measure the timer rather than the work. Averaging over 50 hides individual outliers by construction:
one 5 µs event among 49 fast ones would not appear anywhere in this table. **Do not read these as
per-event latency.**

## Zero GC — the strongest form of the claim

**500,000,000 events under `-XX:+UseEpsilonGC -Xmx64m`.** One byte per event would have required
500MB. **Zero collections, run completed.**

Allocation is *constant*, not per-event:

| events | bytes allocated |
|---|---|
| 10,000,000 | 35,008 |
| 40,000,000 | 35,008 |
| 160,000,000 | 35,008 |

## The collector becomes irrelevant

50,000,000 events, same graph:

| collector | throughput | p50 | collections |
|---|---|---|---|
| Epsilon | 56,663,294 /s | 17 ns | **0** |
| G1 | 55,797,380 /s | 17 ns | **0** |
| Serial (512MB heap) | 55,101,279 /s | 17 ns | **0** |
| ZGC | 46,476,342 /s | 20 ns | **0** |

**No collector ever ran**, so **collection activity and pause time stop mattering** — a stronger
statement than any speedup. **The collector itself does not stop mattering:** ZGC was ~17% slower in
throughput and p50 while also collecting nothing.

**Why ZGC is slower is observed, not explained.** Attributing it to read barriers specifically would
need profiling or generated-code evidence this benchmark does not have; the result is *consistent
with* barrier overhead on reference loads and is stated no more strongly. The practical point holds
either way: reaching for a low-pause collector cost throughput in a design that never needed one.

## The stream-driven clock — the whole change

```java
DataFlow flow = new MyProcessor();
flow.init();
flow.setClockStrategy(() -> streamTime);   // a long provider; ClockStrategy is a functional interface
```

Every node then sees that value through `clock.getProcessTime()`, so a replay produces identical
timestamps to the original run. **Verified in the harness, not assumed:** the injected value is read
back through the node-visible clock before any measurement is taken.

Cost of the deterministic clock itself: **0.28 ns/event** — the difference between injecting a strategy
(8.50) and removing the clock auditor from the build entirely (8.22). The 9 ns separating default from
injected is `System.currentTimeMillis()`, which measures 12.32 ns/call standalone on this machine —
the same order as the 9.43 ns the auditor adds. **This isolates timestamp acquisition; it does not
establish the mechanism**, so no claim is made about system calls, vDSO reads or anything else.

## What the audit log costs

The audit *level* is a runtime setting, so **records** can be suppressed without a rebuild.
**Instrumentation is not removed by doing so:** audit compiled in at `NONE` still costs ~2× against a
graph with no audit at all (37.0 vs 17.9 ns), because the `auditInvocation` sites are emitted
unconditionally and the level is checked inside each. "Off in the hot path" is true of record
*production* and false of instrumentation.

| configuration | ns/event | bytes/event |
|---|---|---|
| no audit compiled in | 17.9 | 0.0 |
| audit compiled in, level NONE | 37.0 | 0.0 |
| `addEventAudit(INFO, false)`, level ERROR | 120.8 | **0.0** |
| default, level ERROR | 142.7 | 184.0 |
| default, level INFO (10M records) | 441.4 | 460.0 |

Two things worth knowing: **an audit-enabled graph sitting at a suppressing level can be entirely
allocation-free** — `addEventAudit(level, false)` turns off per-event event-stringification, which is on
by default and costs 184 B/event even when nothing is published. And **merely compiling audit in costs
~2×** at zero allocation, because the `auditInvocation` call sites are emitted unconditionally.

## Claims this evidence does NOT support

- **"The integrator pays nothing."** Against hand-inlined Java it is 2.68×. The defensible sentence is
  *within 19% of hand-written code carrying equivalent correctness guarantees, at zero allocation.*
- **Larger graphs.** Ten nodes. Dispatch is straight-line so linear scaling in node count is expected,
  but that is a prediction, not a result.
- **Multi-threaded or paced-arrival behaviour.** Not measured.
- **This is not yet a one-command reproduction package.** [`run.sh`](run.sh) rebuilds and re-runs the
  throughput ladder from committed sources, and `blog/raw-results.txt` holds that table's raw output.
  **The collector, audit-cost, floor and latency tables have no committed raw output and no pinned
  dependency provenance** — they were transcribed from stdout during the session. Reproducing them
  means re-running the harnesses in `variants/` and `blog/` by hand.
