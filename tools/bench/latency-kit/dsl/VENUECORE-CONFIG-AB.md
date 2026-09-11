# Configuration as node state, measured

Eight tuning values moved from Java compile-time constants to node fields configured at build time.
Everything else about the graph is unchanged, deliberately, so the audit oracle has to produce the
same log — which is what makes this a measurement of the *mechanism* rather than of a tuning change.

Rebuild both arms with `AB_OUT=<dir> ./build-venuecore-ab.sh`.

## What moved, and what did not

**Moved — 8.** `volShift` `imbalanceShift` `inventoryShift` `momentumShift` `baseQty` `maxQuoteQty`
`positionLimit` `qtyThreshold`. All are tuning a real venue configures per symbol class or risk
appetite. All had zero harness references except `positionLimit`, whose single reference argued *for*
the move: the harness drove the graph into its risk path using `GenVenueCore.POSITION_LIMIT * 4`, a
second copy of the value that happened to agree. It now reads `risk.positionLimit` off the node, so the
test follows the configuration instead of restating it.

**Stayed — 10 tags.** `BID` `ASK`, the four ack types, the four intent kinds. These are an *encoding*
shared with everything that talks to the graph — the harness reads `NONE`/`NEW`/`REPLACE`/`CANCEL`
off the graph's output 10–12 times each. A per-instance `BID` is meaningless.

**Stayed — 3 by judgement.** `SUB_TICK_SHIFT` and `BASE_HALF_SPREAD` are the price *representation*,
not tuning. `STALE_NANOS` is a contract between the graph's staleness rule and the event generator's
timer cadence, enforced by a refusing invariant (`freshnessDeadlineNs <= STALE_NANOS`); two nodes with
different staleness bounds would break that single invariant, so moving it is a design decision and
not a mechanical one.

## What the change actually buys

Before, the two arms agreed because someone kept 21 constants in sync **by hand across two languages** —
`public static final int` in `GenVenueCore.java`, `constexpr int` in `main-venuecore.cpp`. They *were*
in sync; that is not the point. The generated C++ now carries the values the graph was built with:

```cpp
struct FairValue   { int32_t symbolCount = 64; int32_t momentumShift = 2; ... };
struct QuoteBuilder{ int32_t volShift = 1; int32_t imbalanceShift = 5; int32_t inventoryShift = 2;
                     int32_t baseQty = 10; int32_t maxQuoteQty = 25; ... };
struct RiskLimits  { int32_t positionLimit = 2000; ... };
struct OrderDiff   { int32_t qtyThreshold = 3; ... };
```

and the eight `constexpr` in the C++ arm are **gone**, along with `-DSYMBOLS`. One declaration feeds
both languages. Drift is now impossible rather than merely absent — and a type the target cannot carry
is refused by name (`FLX-1031`) instead of being silently absent, which a hand-kept `constexpr` block
can never be.

## The oracle

300,000 events, data-driven clock, binary audit logs decoded record-for-record:

```
records=67247  entries=533328
sha256 control (statics)   : 097cbeee8c1633ccd7b528d7fcc79834…
sha256 cfg     (node state): 097cbeee8c1633ccd7b528d7fcc79834…
```

**Identical, timestamps included** — 600,575 dump lines, every node id, key, value and timestamp. And
it is the same digest the builder-API and Spring-composed arms produced, so three build routes and two
state representations now agree on one log.

The `.flxa` files differ by 12 bytes: the dictionary interns the event-type names and
`GenVenueCoreCfg$MarketTick` is longer than `GenVenueCore$MarketTick`. That is why the comparison is on
the normalised dump, which records the simple name.

All four arms agree on the runtime checksum too: `284567111902293727`.

## The cost

| arm | control (folded constants) | cfg (node state) | delta |
|---|---|---|---|
| Java, causal latency | 23.737 ns | 23.986 ns | +0.249 ns, +1.05% |
| C++, causal latency | 10.872 ns | 10.989 ns | +0.117 ns, +1.08% |

Both `measure.sh` REPEATABLE. **Neither is a difference.** The Java gate holds that a JIT delta under
~5% is noise, measured; the C++ arms' own batch-to-batch CV was 1.26% and 0.40%, so a 1.08% gap sits at
the same order as the spread within a single binary.

Read conservatively: moving tuning out of folded constants into captured node state costs **at most a
tenth of a nanosecond per event in C++, and nothing measurable in Java**. The tags staying `static
final` is part of why — the hot comparison paths still fold.
