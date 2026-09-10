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

The tails separate the two languages more than the medians do: C++ holds its p99.9 at 1.5x its median
where Java runs 2.6x, and Java's worst burst is three times C++'s. That is the shape you would expect
from a managed runtime, and it is the reason to look at a distribution rather than an average.

What this cannot tell you is the latency of **one** event. Below roughly 83 ns per event, that is not
measurable this way on this hardware — a property of the instrument, not of the graph.

## Reading these numbers honestly

- **They are reciprocal throughput, not single-event latency.** `t0 = now; loop N events; (now - t0)/N`
  measures a steady-state rate on a machine that overlaps work from successive events. A figure of
  3.73 ns per event does not mean an event enters and its result emerges 3.73 ns later.
- **Auditing costs roughly the same in both languages** — a clock read and a record append that neither
  avoids — which is why C++'s advantage narrows from 2.3x to 1.6x once both are auditing.
- **A graph's cost is its own.** Six nodes doing integer arithmetic is representative of a quoting
  engine and not of a system that parses, allocates or calls out. Measure yours.
- **Sparse logging is the assumption.** Change what you log and this number changes.

The engine, both harnesses and the full method are in `tools/bench/latency-kit/dsl/` in the analyser
repository, along with the record of what went wrong while measuring it — including three bugs in the
benchmark itself that each produced plausible output.
