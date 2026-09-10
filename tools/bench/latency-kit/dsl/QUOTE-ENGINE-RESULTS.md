# A representative market-making engine, measured

Asked in review for an experiment on a realistic graph rather than a two-node shape, and specifically
for **throughput and event-level latency distributions with auditing on and off**. This is that
experiment. It is a hand-written quote engine, not a DSL flow, because that is the low-latency path to
trading: a hand-written node is a class with fields and a callback, and the framework contributes
dispatch and nothing else.

## The graph

Six nodes, two event types, all arithmetic in integer ticks so nothing on the measured path allocates
in either language.

```
MarketTick ──▶ BookState ──▶ VolatilityWindow ─┐
                   │                            ├──▶ QuoteCalculator ──▶ RiskGate ──▶ QuotePublisher
Fill ──────▶ InventoryBook ─────────────────────┘
```

Mid from the touch, a volatility proxy from the high-low range of a sliding window of mids, a
book-imbalance term, per-symbol inventory from fills, a skew leaning quotes against the position, and
a risk gate. 64 symbols, one fill every 32 events. No edge lives here — the shape and its cost are the
subject, not a strategy.

Two arms, differing in one setting:

| arm | configuration |
|---|---|
| unaudited | `LOWEST_LATENCY`, and not one setting beside it |
| audited | `LOW_LATENCY_AUDIT` + `BINARY`, **sparse** logging — four keys total, on the two nodes whose output a post-mortem would ask about |

## Results

Apple Silicon, `clang++ -O3`, Java on JIT. Throughput is min-of-5-batches of 5M events, three
interleaved reps. Latency is burst latency at 64 events per burst, 200,000 bursts.

| arm | throughput | burst p50 | p99 | p99.9 | max | per-event p50 | per-event p99 |
|---|---|---|---|---|---|---|---|
| Java, no audit | **8.62 ns** | 542 ns | 709 | 1416 | 8584 | 8.47 ns | 11.08 ns |
| Java, audited sparse | **21.27 ns** | 1375 ns | 1875 | 2375 | 26875 | 21.48 ns | 29.30 ns |
| C++, no audit | **3.73 ns** | 250 ns | 333 | 375 | 8167 | 3.91 ns | 5.20 ns |
| C++, audited sparse | **13.32 ns** | 834 ns | 1042 | 1209 | 9125 | 13.03 ns | 16.28 ns |

Throughput and burst-p50-per-event agree in all four arms — 8.47/8.62, 21.48/21.27, 3.91/3.73,
13.03/13.32 — which is the cross-check that matters, because they are separately derived.

**What it says.** Auditing costs **12.9 ns/event in Java and 9.3 ns in C++** on a six-node graph with
sparse logging, and it produces one binary record per event either way. C++ is 2.3x on the unaudited
path and 1.6x once both are auditing, because the audit cost is largely a clock read and a record
append that neither language avoids. The tails differ more than the medians: C++ holds p99.9 at 1.5x
its median where Java runs 2.6x, and Java's worst burst is 26.9 us against C++'s 9.1 us.

## Why latency is reported per BURST

**Per-event latency is not measurable here, and the harness refuses to pretend otherwise.**
`System.nanoTime` and `mach_absolute_time` both resolve to **41.67 ns** on this machine — 82% of
consecutive reads return the same value — while these events cost 3.7 to 21 ns. Timing them
individually measures the counter: 74% of unaudited Java events did not move the clock at all, and the
harness prints REFUSED rather than a p99 of 41 ns.

So it times a burst of 64 events, which is a real quantity — a market-data batch arriving in one
agent-loop pass is exactly this — and puts the sample well above the counter's floor. It keeps the
tail, which is what a quoting engine is judged on: a stall inside a burst still lands in that burst's
sample. It gives up any claim about one event's latency, and the key names say so (`perEventP50` is an
average within a burst, not a latency). The unaudited C++ arm was refused at burst=16 and had to be
re-run at 64 — 16 x 3.73 ns still lands on one tick.

**Anything under ~83 ns per event cannot be timed per-event on Apple Silicon.** That is the
instrument, not the graph.

## Four bugs this found, three of them in the benchmark

A benchmark that runs is not a benchmark that measures.

1. **The risk gate ate the experiment.** Publish rate was 6%: inventory drifted past the position
   limit, so 94% of events took the gate's early return and the measured path was the branch that does
   nothing. Two distinct causes, and the first fix did not move the number:
   - fills happen when `i % 32 == 0`, so every fill index is a multiple of 32 and `(i & 7)` is
     **always 0** — every fill carried an identical quantity. The quantity must be indexed by the
     **fill** number, not the event number.
   - with that fixed, `symbols = 64` and an 8-long quantity cycle still **alias**: for a fixed symbol
     the fill number is congruent mod 64, so its quantity is constant again. The cycle length must be
     **coprime** with the symbol count. It is 7 now, and the publish rate is 99.99995%.
2. **`GroupBy.lastValue()` had no C++ spelling.** The generated store exposed `valueFor(key)` and
   `groupCount()`, and a downstream stub is handed the store and not the key — so the only readable
   key was one fixed when the graph was written. "The inventory of the symbol this fill was for", the
   ordinary shape of a keyed graph, was expressible in Java and not in C++. Now closed, with
   `CppGroupByLastValueTest` pinning both insertion paths and the before-first-event guard.
3. **The bench was not measuring the branch.** Every figure in this kit had been resolving fluxtion
   classes from `~/.m2` snapshot jars rather than the worktrees under test. Those jars happened to be
   current, which is precisely the problem — nothing looked wrong. `branch-classpath.sh` now builds a
   branch-first classpath, drops every fluxtion jar outright, and verifies by loading each key class
   and asking where it came from.

## A note on guards, which is a design point and not a defect

The engine was first written with `boolean` callbacks, and its `LOWEST_LATENCY` build carried a
`guardCheck_` before every node. That is correct behaviour: `setSupportDirtyFiltering(false)` drops the
dirty flags that **decide nothing**, and a `boolean` return is not an optimisation hint — it IS the
node's propagation decision. Discarding it would not make the graph faster, it would make it wrong.

So on the lowest-latency path the callbacks return `void` — which requires
`failBuildIfMissingBooleanReturn = false`, since the build lint rejects a void callback by default —
and the propagation decisions move out of return values and into node state: `BookState` publishes
`valid`, `RiskGate` publishes `quotable`, and the nodes below read them. Identical behaviour, zero
dirty flags, zero guards, straight-line dispatch in both languages. The branch is in your code where
you can see it rather than in the framework's where you cannot.

## A real asymmetry between the targets

A hand-written **Java** node IS your class: fields and callbacks together, one object per node. The
C++ emitter generates a **stub** carrying parent pointers and (when audited) an `auditLog`, and nothing
else — it cannot know your fields. So C++ node state lives at file scope in the harness. Both are
contiguous and neither allocates on the measured path, so the comparison stands, but they are not the
same memory layout and a nanosecond on this path could be that rather than the language.

## Reproducing

```bash
export JAVA_HOME=$(/usr/libexec/java_home)
eval "$(tools/bench/latency-kit/branch-classpath.sh)"     # refuses if a fluxtion jar is on the path
tools/bench/latency-kit/branch-classpath.sh --verify      # prints where each key class resolved
```
Then generate and build both arms from `GenQuoteEngine` (`-Daudit=true|false`, `-Dtarget=java|cpp`) and
run `BenchQuoteEngine` / `qebench`. `-Dlatency=true -Dburst=64` selects burst latency; the audited arms
assert their sink actually saw records, because an audited build that publishes nothing reads as a
speed-up.
