# The quoting core — a bounded decision-core benchmark

Built to a reviewer's specification after they judged the previous engine too thin to stand for a real
application. [`GenQuoteEngine`](QUOTE-ENGINE-RESULTS.md) is retained as the **control**; this
supersedes it in realism and the two are reported side by side.

**Scope, stated so it cannot drift.** Normalised market and order events enter; per-symbol state is
updated; fair value and desired quotes are computed; risk and freshness are applied; desired orders are
reconciled against live and pending orders; zero or more executable order intents emerge. **No packet
parsing, kernel, NIC, FIX/OUCH/SBE, exchange simulation or hedging** — those are separate benchmarks
and mixing them in would obscure this one.

## The graph — 10 nodes, 4 event types

```
MarketTick  → MarketState → SignalState → FairValue → QuoteBuilder → Risk → OrderDiff → Intent
Fill        → InventoryState →            FairValue → QuoteBuilder → Risk → OrderDiff → Intent
OrderUpdate → WorkingOrders →                                              OrderDiff → Intent
TimerTick   → FreshnessState →                                     Risk → OrderDiff → Intent
```

These are the **generated** dispatch paths, read out of the emitted processor, not a diagram of intent.
A `Fill` recomputes fair value from stored market state for its symbol; an `OrderUpdate` reconciles
against the stored desired quote without recomputing it.

What each node does, in brief: `MarketState` validates the book and derives mid, spread and imbalance;
`SignalState` maintains fixed-point EWMA volatility and momentum per instrument; `InventoryState`
tracks signed position per symbol from explicit buy/sell fills; `WorkingOrders` is the order state
machine (live price and quantity, remaining quantity, pending flag, generation, driven by
ACK_NEW / ACK_REPLACE / ACK_CANCEL / PARTIAL_FILL / REJECT); `FreshnessState` runs a rolling staleness
sweep off the timer; `FairValue` fuses a microprice tilt with momentum; `QuoteBuilder` produces price
**and size** and rounds to venue ticks; `Risk` suppresses **bid and ask independently**; `OrderDiff`
reconciles desired against working state into NONE / NEW / REPLACE / CANCEL; `IntentPublisher` writes a
fixed-layout intent into a preallocated ring.

**Prices are fixed-point in 1/16 tick**, quantised to the venue tick only when the executable quote is
built — calculated price → venue rounding → executable price, with the bid rounding down and the ask
rounding up so a quote never crosses itself.

**Every piece of state is per symbol.** That is the substantive correction over the control, where book
and quote state were scalar so consecutive ticks for different symbols blended.

**All callbacks return `void`**, so the generated dispatch carries no dirty flags and no guards.

## The workload

A **pre-generated event buffer**, replayed — nothing is computed from the loop index inside the timed
region. A fixed-seed xorshift64 PRNG with **independent sub-streams** for type, symbol, price, size and
anomalies, so no two can lock in phase. Per-symbol random walks, occasional jumps, occasional
one-sided books, occasional invalid books.

Two profiles, and the measured branch mix rather than an assertion of realism:

| | events | NONE | NEW | REPLACE | CANCEL | NONE breakdown |
|---|---|---:|---:|---:|---:|---|
| **active** | market 70.1% · order 22.0% · fill 6.0% · timer 2.0% | 89.4% | 2.1% | 8.5% | 0.02% | pending 78.7%, notAllowed 16.4%, unchanged 4.8% |
| **selective** | same | 92.3% | 1.5% | 6.2% | 0.04% | pending 50.1%, notAllowed 14.6%, **unchanged 35.3%** |

CANCEL is rare but exercised (464 and 662 occurrences) — raw counts are reported alongside percentages
because "CANCEL = 0.0%" and "CANCEL never happens" are different claims.

## Results — Java, 64 symbols, active profile

All `measure.sh` REPEATABLE, 3 batches x 5 reps, gated on CV and on machine load.

| | unaudited | audited (sparse, binary) |
|---|---:|---:|
| throughput | 23.659 ns | 32.410 ns |
| read-control | 24.326 ns | 33.042 ns |
| **causal latency (serial)** | **23.783 ns** | **33.103 ns** |

**Throughput, control and latency agree within 3%** — the graph is entirely serial-bound, so the
throughput figure IS the latency figure. That differs from the control engine, where unaudited
throughput and latency diverged by 34% (8.83 vs 11.82 ns), and it is what a ten-node dependency chain
should look like.

**Throughput and latency are the same number here even unaudited** — unlike the control, where they
diverged by 34%. A ten-node dependency chain leaves almost no cross-event overlap to lose, so the
richer graph is already serial-bound. That makes the throughput figures directly quotable as latencies.

Auditing costs **9.3 ns/event** on this graph, less than the control's 12.9, because sparse logging
here produces a record only on cycles that did something — a fill, or a non-NONE decision — rather than
on every event.

## The audit log on this graph

| | control engine | quoting core |
|---|---:|---:|
| records per event | 1.00 | **0.27** |
| bytes per event | 62.0 | **20.4** |
| at audited throughput | 2.79 GB/s | **~630 MB/s** |

73% of events produce no record at all, which is what makes this sparse in a way the control was not.

**The record mix is INVERTED relative to the event mix** — across 400 sampled records, OrderUpdate is
22% of events and 66% of records, while MarketTick is 70% of events and 12% of records. Market data
dominates the input and barely appears in the log, because most ticks correctly decide NONE. The log
records DECISIONS, not traffic. Whether that is the right thing to audit is an open question.

A real record, and `act` is the reconciler's decision (1 = NEW, 2 = REPLACE, 3 = CANCEL; NONE never
reaches the log because nothing is published):

```yaml
eventLogRecord:
    eventTime: 1789040605190
    logTime: 1789040605190
    event: MarketTick
    nodeLogs:
        - intent: { sym: 7, act: 2, px: 10109}
```

## Working set — and why the first sweep was worthless

The reviewer asked for 64 / 256 / 1024 / 4096 symbols. The first attempt reported a flat curve, and it
was **an artefact**: the processor bakes its symbol count in at build time, so every run was against a
64-symbol graph, and the skewed symbol distribution kept indices below 64 so nothing ever ran off the
end. Uniform selection exposed it with an `ArrayIndexOutOfBoundsException`. The graph must be
**rebuilt per symbol count**, and these numbers are.

Causal latency, uniform symbol selection, min of 3 reps:

| symbols | per-symbol state | unaudited | audited |
|---:|---:|---:|---:|
| 64 | 8 KB | 26.74 | 37.72 |
| 256 | 32 KB | 38.50 | 38.38 |
| 1024 | 128 KB | 31.75 | 39.52 |
| 4096 | 512 KB | **40.19** | **52.84** |

Unaudited cost rises **50%** from 64 to 4096 symbols, audited **40%** — the working set leaving cache,
which is the useful information the sweep was for.

**The curve is not monotonic and the 256 point is reproducible**, not noise: 38.5 unaudited against
31.8 at 1024. The likely cause is cache set conflicts at that array size — several per-symbol arrays
of 1 KB mapping to the same sets — but that is a hypothesis, not a measurement, and it is left as one.

With the **skewed** distribution the same sweep is nearly flat — 24.2 → 25.5 unaudited from 64 to 4096
— because the hot set stays in L1 however many symbols are allocated. Both are true and they answer
different questions: skew is what a real book distribution looks like; uniform is what tells you where
the cache cliff is.

## Bugs this found

Four, and all four produced a benchmark that ran:

1. **`code too large`.** Fluxtion serialises hand-written node state into the generated constructor,
   including array **contents** — passing per-symbol arrays in emitted every element as a literal and
   the constructor exceeded the 64 KB method limit at 64 symbols, never mind 4096. Fixed with
   `@FluxtionIgnore` on the arrays and `@Initialise` allocation, which keeps the generated source O(1)
   in the symbol count.
2. **`PARTIAL_FILL` never cleared `pending`.** Any order slot that received a partial while a request
   was in flight stayed pending forever, and the engine stopped quoting that symbol and side —
   100% NONE and 80 intents from a million events.
3. **`break` inside an arrow-switch case** silently dropped order events rather than skipping a lookup;
   the decision count fell by a fifth with nothing to show why.
4. **Ack starvation.** The first design drew ack targets from the same random stream as everything
   else, so orders went pending and were never acked: 90.3% of decisions were NONE and both profiles
   were identical. Acks now follow real orders via a FIFO cursor trailing the intent ring, which is
   what a venue does.

Three of the four were only visible because the harness reports the **branch mix**. A benchmark that
prints only nanoseconds would have reported every one of them as a fast result.

## C++

Both arms build clean under `-Wall -Wextra`. **Java and C++ produce byte-identical decisions** — the
same deterministic stream replayed in both languages agrees at every one of 1,981,480 decision points:
NONE 1,771,067 / NEW 41,801 / REPLACE 168,148 / CANCEL 464, same intents emitted, same risk and stale
suppressions. That is the correctness gate for the comparison, and it is stronger than a checksum.

| 64 symbols, active | Java | C++ | ratio |
|---|---:|---:|---:|
| throughput, unaudited | 23.659 | **9.893** | 2.39x |
| throughput, audited | 32.410 | **15.575** | 2.08x |
| read-control, unaudited | 24.326 | 10.072 | |
| read-control, audited | 33.042 | 16.228 | |
| **causal latency, unaudited** | 23.783 | **9.970** | 2.39x |
| **causal latency, audited** | 33.103 | **15.863** | 2.09x |

**C++ shows the same serial-bound signature**: throughput, control and latency agree within 4%, so the
throughput figure is the latency figure in both languages. Auditing costs **5.9 ns/event in C++**
against Java's 9.3.

### Working set, C++

Causal latency, uniform symbols, rebuilt per count, min of 3:

| symbols | state | C++ unaudited | C++ audited | Java unaudited |
|---:|---:|---:|---:|---:|
| 64 | 8 KB | 11.90 | 17.23 | 26.74 |
| 256 | 32 KB | 12.30 | 17.25 | 38.50 |
| 1024 | 128 KB | 12.82 | 18.02 | 31.75 |
| 4096 | 512 KB | **20.60** | **25.76** | 40.19 |

**The C++ curve is clean and monotonic with a clear cliff at 4096** — flat from 64 to 1024, then +61%
as the working set leaves cache. Java's curve over the same builds is noisier and non-monotonic. We
would not read much into the difference in shape beyond noting that the C++ arm makes the cache effect
easy to see and the Java arm does not.

## Status

Both arms complete. **The `@Initialise` gap was found here** — the harness needs porting, and the
per-symbol state that lives in node fields in Java has to live at file scope in C++, as it does for the
control. The reviewer's prediction was C++ 20–40 ns and Java 30–60 ns causal latency for the audited
64-symbol core; Java measures **32.4 ns**, inside their band.
