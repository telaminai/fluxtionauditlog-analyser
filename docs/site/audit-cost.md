# What does auditing cost?

An audit log you can analyse is only worth having if the system can afford to write it. This page
answers that with a measurement rather than a reassurance: a realistic market-making engine, built
twice from the same source, differing in one setting.

The short answer, on a six-node quoting graph with **sparse** logging:

| | per event | with auditing | cost of auditing |
|---|---:|---:|---:|
| Java (JIT) | 8.62 ns | 21.27 ns | **+12.9 ns** |
| C++ (`clang -O3`) | 3.73 ns | 13.32 ns | **+9.3 ns** |

One binary audit record per event, in both languages. At 21 ns an event, a single core can audit
roughly 47 million events a second — so for most systems the honest answer is that auditing is
affordable and the interesting question is what you choose to log, not whether you can.

## The graph being measured

Two-node microbenchmarks tell you what an operator costs and nothing about what a system costs, so
this is a graph shaped like the thing it stands in for: six nodes, two event types, integer prices in
ticks so nothing on the measured path allocates.

```
MarketTick ──▶ BookState ──▶ VolatilityWindow ─┐
                   │                            ├──▶ QuoteCalculator ──▶ RiskGate ──▶ QuotePublisher
Fill ──────▶ InventoryBook ─────────────────────┘
```

Mid price from the touch, a volatility proxy from the range of a sliding window of mids, a
book-imbalance term, per-symbol inventory accumulated from fills, a skew that leans quotes against the
position, and a risk gate that refuses to quote past a limit. 64 symbols; one fill every 32 events, so
the inventory path is a cold branch rather than half the work. There is no edge in the pricing — the
shape and its cost are the subject.

## What "sparse" means, and why it is the number that matters

Auditing is not one setting with one price. This measurement uses **four keys in total**: the fill node
records the symbol and resulting position, and the publisher records the bid and ask. That is enough to
reconstruct afterwards what the engine decided and why.

The alternative — full node tracing, where the framework records every node's participation in every
cycle — costs several times more. Both are supported, and choosing between them is a real decision:
sparse logging answers "what did it decide", full tracing answers "which nodes ran". Most
post-mortems ask the first question.

## Does a native image help?

Not on this workload, and the measurement is worth having because the assumption usually runs the other
way:

| toolchain | per event | with auditing |
|---|---:|---:|
| OpenJDK 25.0.2 (C2 JIT) | **8.60 ns** | 21.99 ns |
| GraalVM 25.0.4 (Graal JIT) | 12.03 ns | 21.31 ns |
| Native image, AOT + PGO | 12.66 ns | 22.30 ns |
| C++ (`clang -O3`) | 3.80 ns | 13.83 ns |

The native image is **slower** than the standard JIT on the unaudited path and level with it once
auditing — and this was a properly profiled build, verified from its own build log rather than assumed.
It is not tighter in the tail either: measured over 64 million events it is worse than the JIT at p50,
p90, p99 and p99.99.
Once you are auditing, all three Java toolchains land within 5% of each other: the audit path is the
same code in each and dominates the event. A native image is worth building here for startup time, not
for steady-state throughput.

## Is the audited path allocation-free?

Yes, and it is checked three ways rather than asserted, because "allocation-free" is easy to claim from
reading code and wrong the moment one autobox is on the path:

- the JVM's own per-thread accounting reads **0 bytes** over 5 million events, audited or not;
- both Java arms run **25 million events under a non-collecting garbage collector** on a 32 MB heap —
  which is impossible if the path allocates anything — while publishing 26 million binary audit
  records, at the same speed as the collecting run;
- the C++ arms count global `operator new` on the measured path and report **zero calls**.

This is what makes auditing usable on a latency-sensitive path: it is not merely cheap, it produces no
garbage, so it cannot hand you a collection pause later.

## Why latency is reported per burst

The review question behind this page asked for **latency distributions**, not just throughput, and the
distinction is a fair one: throughput is a steady-state rate with successive events overlapping in the
pipeline, and it can look excellent while a tail is misbehaving.

The obstacle is the clock. On the machine these numbers came from, both `System.nanoTime` and
`mach_absolute_time` resolve to **41.67 nanoseconds** — 82% of consecutive reads return the same
value — while these events cost between 3.7 and 21 ns. Timing events individually measures the counter
and not the graph: 74% of unaudited events did not move the clock at all. The harness therefore
**refuses** to print percentiles in that case rather than reporting a p99 that is one tick of a
counter.

So it times a **burst of 64 events**, which is a real quantity — a market-data batch arriving in one
pass of an agent loop is exactly this — and lands well above the counter's floor:

| arm | burst p50 | p99 | p99.9 | worst burst |
|---|---:|---:|---:|---:|
| Java, no audit | 542 ns | 709 ns | 1,416 ns | 8.6 µs |
| Java, audited | 1,375 ns | 1,875 ns | 2,375 ns | 26.9 µs |
| C++, no audit | 250 ns | 333 ns | 375 ns | 8.2 µs |
| C++, audited | 834 ns | 1,042 ns | 1,209 ns | 9.1 µs |

Measured further out, over 64 million events per arm, the picture sharpens — and the important column
is the last one:

| arm | p50 | p99 | p99.9 | p99.99 |
|---|---:|---:|---:|---:|
| Java, no audit | 583 ns | 708 ns | 917–1,292 ns | 3,875–4,958 ns |
| Java, audited | 1,416 ns | ~1,730 ns | ~2,350 ns | 6,542–6,833 ns |
| C++, no audit | **250 ns** | **~310 ns** | **375 ns** | **417–458 ns** |
| C++, audited | 917 ns | 1,042 ns | 1,209 ns | 5,250–5,667 ns |

**Auditing is cheap on average and not cheap in the tail.** It costs about 10 ns per event at the
median and roughly eight times that at p99.99 — around +81 ns/event for C++, +34 ns/event for Java.
The unaudited C++ arm is remarkably flat, spreading only 1.8x from its median out to one-in-ten-
thousand; auditing costs it that flatness, widening the spread to 6.2x.

If your budget is a median, auditing costs ~10 ns. If your budget is p99.99 — which is the budget a
quoting engine actually has — it costs the better part of 100 ns on the otherwise-tightest arm. Still
affordable at these rates, but it is a different claim, and quoting the median alone would mislead.

What this cannot tell you is the latency of **one** event. Below roughly 83 ns per event, that is not
measurable this way on this hardware — a property of the instrument, not of the graph.

## For an audited system, throughput *is* the latency

The figures above are reciprocal throughput — a rate measured on a machine that overlaps work from
successive events — and that is genuinely a different thing from how long one event takes. So we
measured latency directly, by a different experiment: make each event's input depend on the previous
event's output, so the hardware cannot begin one event before the last has finished. Elapsed time
divided by event count is then a causal latency.

| | throughput | latency | difference |
|---|---:|---:|---:|
| Java, audited | 21.84 ns | **22.02 ns** | +0.8% |
| C++, audited | 14.03 ns | **14.25 ns** | +1.6% |
| Java, no audit | 8.83 ns | 11.82 ns | +34% |
| C++, no audit | 3.86 ns | 9.10 ns | +136% |

**Once auditing, the two converge**: each event must finish writing its audit record before the next
begins, which serialises the pipeline by itself, so there is no overlap left to lose. An audited event
enters and its result emerges about 22 ns later in Java and 14 ns in C++.

On the unaudited path the two diverge sharply, and the C++ figure is the one that moves most — it was
overlapping events heavily. Measured as latency rather than throughput, the gap between the languages
narrows from 2.3x to 1.3x.

## Reading these numbers honestly

- **The headline figures are reciprocal throughput.** `t0 = now; loop N events; (now - t0)/N` measures
  a steady-state rate on a machine that overlaps work from successive events. On the *unaudited* path
  that is not a latency — 3.86 ns per event does not mean an event's result emerges 3.86 ns later; the
  measured latency is 9.10 ns. On the *audited* path the two agree within a couple of percent, for the
  reason given above.
- **Auditing costs roughly the same in both languages** — a clock read and a record append that neither
  avoids — which is why C++'s advantage narrows from 2.3x to 1.6x once both are auditing.
- **A graph's cost is its own.** Six nodes doing integer arithmetic is representative of a quoting
  engine and not of a system that parses, allocates or calls out. Measure yours.
- **Sparse logging is the assumption.** Change what you log and this number changes.

The engine, both harnesses and the full method are in `tools/bench/latency-kit/dsl/` in the analyser
repository, along with the record of what went wrong while measuring it — including three bugs in the
benchmark itself that each produced plausible output.
