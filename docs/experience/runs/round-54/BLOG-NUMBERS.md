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
- **Every arm is asserted to produce identical output** — `breaches=102,500,000`,
  `updates=102,500,000`, `buffer=11551.2267`. These are implementations of the same function, not
  different functions.
- **200,000,000 events per run, 5 JVM runs per arm**, median reported with min/max.
- **`-XX:+UseEpsilonGC`** — a collector that never frees a byte. Any per-event allocation is fatal
  rather than invisible.

Machine: JDK 21.0.9 Corretto, macOS, single core, closed loop. **No coordinated-omission correction** —
this is a throughput harness; tails under a paced arrival rate would differ.

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
- The hand-inlined arm is 2.68× faster, and that gap is the price of *not* hand-ordering ten
  computations correctly — which is the thing this project separately measured people and models
  getting wrong.
- **Fluxtion's variance is the tightest of any arm**: 8.38–8.48 ns, a 1.2% spread, against 2.67–3.25
  (18%) for the inline version.

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
| guarded semantics (arrest honoured) | 8.45 | 7.12 | **1.19×** |
| unconditional (every node every cycle) | 5.98 | 3.50 | **1.71×** |

## Latency distribution (ns/event, batch=50, 200M events)

| arm | p50 | p90 | p99 | p99.9 | p99.99 | max |
|---|---|---|---|---|---|---|
| hand-written, guarded | 7 | 8 | 10 | 10 | 25 | 430 |
| **Fluxtion, stream clock** | **8** | **9** | **11** | **20** | **30** | 1,814 |
| Fluxtion, default wall clock | 17 | 19 | 24 | 39 | 111 | 2,316 |

Each sample is the mean of 50 events — `System.nanoTime()` costs ~16 ns for a pair against an ~8 ns
operation, so per-event timing would measure the timer. This shows steady-state shape; it does not
resolve a single-event tail.

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

**No collector ever ran**, so the choice is unobservable — a stronger statement than any speedup.
**ZGC is the exception and it is a warning:** ~17% slower, because its read barrier is paid on every
reference load whether or not there is garbage. Reaching for a low-pause collector *costs* throughput
in a design that never needed one.

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
injected is `System.currentTimeMillis()`, which measures 12.32 ns/call standalone on this machine.

## What the audit log costs

The audit log is a **runtime level**, so it can be off in the hot path and on when something is being
investigated.

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
