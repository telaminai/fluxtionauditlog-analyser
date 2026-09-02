# Round 54 — the runtime measurement, and the claim it does not support

Every earlier round in this series measured **token cost**. None measured runtime. "High performance,
zero GC" was on the owner's list of things Fluxtion brings and I had flagged it, in the session, as
**completely untested** — the generated dispatch is *consistent* with it, and consistency is not evidence.

This round measures it. One claim is confirmed outright, one is **refuted**, and the interesting result
is neither.

## What was built

Ten nodes, all state `double`/`long`, nothing allocated in any `calc()`. **One `MarketTick` object,
mutated and re-fired** for the whole run — the event stream itself contributes no garbage. A detector
(`Limit`) arrests roughly half of all cycles, so the conditional tail of the graph is real and not
straight-line.

The processor is generated **ahead of time** (`Gen.java`) and the benchmark runs against the
generated class with **only `fluxtion-runtime` on the classpath** — no builder, no compiler. That is
both the honest measurement (in-memory compilation allocates heavily and would have been measured
instead) and the real deployment shape.

## 1. Zero GC in the dispatch path — CONFIRMED

| iters | bytes allocated | per event |
|---|---|---|
| 10,000,000 | 35,008 | 0.0035 |
| 40,000,000 | 35,008 | 0.0009 |
| 160,000,000 | 35,008 | 0.0002 |

**The byte count does not move.** It is a fixed cost inside the measured window, not a per-event one.
The AOT harness shows the same shape at a different constant: 278,832 bytes, **identical to the byte**
across 500M events on both dispatch paths.

The decisive run: **500,000,000 events under `-XX:+UseEpsilonGC -Xmx64m`** — a collector that never
frees anything. One byte per event would have needed 500MB. **0 collections, completed.**

## 2. Epsilon is not faster — and that is the finding

The owner's guess on seeing Epsilon was *"we might go even faster with that"*. It does not, and the
reason is better than the speedup would have been:

| collector | throughput | p50 | p99 | GCs in 50M events |
|---|---|---|---|---|
| Epsilon | 56,663,294 /s | 17 ns | 23 ns | **0** |
| G1 | 55,797,380 /s | 17 ns | 23 ns | **0** |
| Serial (512MB heap) | 55,101,279 /s | 17 ns | 24 ns | **0** |
| ZGC | 46,476,342 /s | 20 ns | 28 ns | **0** |

**No collector ever ran.** Epsilon cannot beat G1 at collecting nothing, so the collector choice is
unobservable — which is a stronger statement about the design than any speedup would have been.

**ZGC is the exception and it is a warning, not a win:** ~17% slower at p50 and in throughput. Its
read barrier is paid on every reference load whether or not there is garbage. **Choosing a low-pause
collector costs you throughput in a design that never needed one.**

## 3. The measurement apparatus was dominating — quantified, not asserted

Owner's question, and it was right. `System.nanoTime()` costs ~16.3 ns for the pair, against a ~17 ns
dispatch, and it also *serialises* — destroying the instruction-level overlap between consecutive events.

| batch | throughput | p50 |
|---|---|---|
| 1 | 25,406,243 /s | 41 ns |
| 5 | 43,686,078 /s | 16 ns |
| 20 | 53,879,717 /s | 16 ns |
| 100 | 56,435,991 /s | 17 ns |
| 1000 | 56,930,193 /s | 16 ns |

**At batch=1 the apparatus was eating 55% of throughput.** Converged by batch≈50.

**Neither view gives an unbiased per-event tail, and the writeup says so:**
- **batch=1** keeps per-event resolution but adds a ~24 ns constant to every sample. Its *shape* is
  real — p50 41 → p99 42 is a genuinely flat distribution, and the p99.99 = 792 ns outliers are real
  events (with GCs = 0, they are OS scheduling and JIT, not collection).
- **batch≥50** gives true throughput, but its histogram is of 100-event *means*. One 5 µs event hiding
  among 99 fast ones would never appear.

## 4. The audit log is not zero-GC BY DEFAULT — and the default is a one-argument fix

Asked directly whether the audit log allocates, I had asserted "by design" without measuring, and the
`LogRecord` API argues the other way: it has `clear()`, a reusable buffer, and **primitive overloads**
(`addRecord(String, String, double)`) — no boxing. So it was worth measuring. A **counting sink**
proves the records are really produced and not optimised away.

Same graph, three nodes logging one figure each:

| level | throughput | ns/event | bytes/event | records at sink | GCs |
|---|---|---|---|---|---|
| NONE | 27,024,086 /s | 37.0 | **0.0** | 2 | 0 |
| ERROR | 6,954,547 /s | 143.8 | **184.0** | 2 | 12 |
| INFO | 2,189,122 /s | 456.8 | **460.0** | 22,000,003 | 29 |
| DEBUG | 2,243,153 /s | 445.8 | 460.0 | 22,000,003 | 29 |

**INFO costs 26× the throughput and 460 bytes per event.** The RCA artefact this whole project is
built on is not free, and no earlier document in this repo said so.

### The allocation is the framework's, not the harness's — challenged and checked

Asked whether the *harness* was causing the allocation, which would have invalidated the whole table.
Two suspects, both mine:

- `auditLog.info("mid", value)` on a `double` field — **cleared by the API**: `EventLogger` has a
  primitive `info(String, double)` overload, so no boxing.
- my sink calling `r.asCharSequence()` 22,000,000 times — **cleared by measurement**, below.

Three sinks, same runs — a **no-op** sink, a **count-only** sink, and the original **chars** sink:

| level | sink | bytes/event |
|---|---|---|
| ERROR | noop | 184.0 |
| ERROR | count | 184.0 |
| ERROR | chars | 184.0 |
| INFO | noop | 460.0 |
| INFO | count | 460.0 |
| INFO | chars | 460.0 |

**Identical to the byte, including with a sink whose body is empty.** `asCharSequence()` returns the
reusable buffer rather than a new String. The allocation is inside the framework's audit path, not the
measurement apparatus. The challenge strengthened the result instead of overturning it — and it was the
right challenge, because §3 had already shown the apparatus dominating once.

The decomposition that follows: at ERROR the three nodes' own `info()` calls are suppressed by level,
so **184 B/event is framework tracing that happens regardless of what the nodes log**. INFO adds a
further **276 B/event** for the three node log statements and publication.

### Traced, and the conclusion I was about to file was wrong

Asked to trace it. JFR allocation profiling (`jdk.ObjectAllocationSample`) names one site for 97.7%
of the ERROR-level allocation:

```
java.lang.StringBuilder.toString()
com.telamin.fluxtion.runtime.audit.LogRecord.triggerObject(Object)  line 210
com.telamin.fluxtion.runtime.audit.EventLogManager.eventReceived(Object)
com.bench2.gen.AuditProcessor.auditEvent(Object)          <- once per dispatch
com.bench2.gen.AuditProcessor.handleEvent(MarketTick)
```

**The triggering event is stringified once per dispatch, before any level check.** That is the whole
184 B/event.

**It is a default, not a defect — and that correction only came from testing it.** `addEventAudit` has
overloads taking booleans, and the second one controls exactly this. Same graph, same nodes, only the
builder call differs:

| builder call | level | throughput | ns/event | bytes/event | records |
|---|---|---|---|---|---|
| `addEventAudit(INFO)` | ERROR | 7,008,515 /s | 142.7 | **184.0** | 0 |
| `addEventAudit(INFO, false)` | ERROR | 8,277,522 /s | 120.8 | **0.0** | 0 |
| `addEventAudit(INFO, false, false)` | ERROR | 9,061,427 /s | 110.4 | **0.0** | 0 |
| `addEventAudit(INFO)` | INFO | 2,265,304 /s | 441.4 | 460.0 | 10,000,000 |
| `addEventAudit(INFO, false)` | INFO | 2,359,832 /s | 423.8 | **276.0** | 10,000,000 |
| `addEventAudit(INFO, false, false)` | INFO | 2,798,140 /s | 357.4 | 276.0 | 10,000,000 |

**So the audit machinery itself CAN be zero-GC.** With event-stringification off, an audit-enabled
graph at a suppressing level allocates **0.0 bytes per event** — the §1 result survives with auditing
compiled in and armed.

The remaining **276 B/event at INFO is the cost of producing text**, which is what was actually asked
for at that level. `DataFlow.setAuditLogRecordEncoder(LogRecord)` exists, so that is pluggable and a
binary encoder is the obvious route; not measured here.

The third boolean buys a further ~10 ns/event at no allocation change. What it controls is not
established here and is deliberately not guessed.

**What still deserves to go upstream, narrowed:** the default pays 184 B/event **at levels where
nothing is published** — the sink saw 0 records in 10,000,000 events and the cost was charged anyway.
The knob exists; the default is the wrong way round, and nothing in the docs points an author at it.

**Retracted:** the earlier framing of this as "the audit log is not zero-GC, claim refuted" was too
strong. Correct statement: *the audit log is not zero-GC by default, is zero-GC when configured, and
costs 276 B/event when it is actually producing records.*

Also worth stating: **audit compiled-in but set to NONE costs 37.0 ns/event against 17.9 ns for the
build with no audit at all** — roughly 2×, at zero allocation. Availability is not free either.

## What did not change

The generated dispatch with audit enabled has the **same guards in the same order** as without it;
`auditInvocation` calls are interleaved between the existing calls. Enabling tracing does not
restructure the graph.

## Honest limits

- Single machine, JDK 21 Corretto, macOS, one core, closed loop. **No coordinated-omission
  correction** — this is a throughput harness, so tails under a paced arrival rate would differ.
- Ten nodes. Larger graphs are not measured, and dispatch is straight-line so this should scale
  linearly in node count, but that is a prediction, not a result.
- One process per configuration, not repeated. The collector comparison is suggestive at this n.


## 5. Build time or runtime? Both routes exist; only two of three work

Asked which kind of flag `printEventToString` is. The clean generated diff between
`addEventAudit(INFO)` and `addEventAudit(INFO, false)` is **one behavioural line, in the
constructor** — not in `init()`:

```java
public PA(Map<Object, Object> contextMap) {
    ...
    eventLogger.setClearAfterPublish(false);
    eventLogger.trace = true;
    eventLogger.printEventToString = true;     // <- the only difference
    eventLogger.printThreadName = true;        // <- what the THIRD boolean controls
```

That also names the third boolean, which §4 declined to guess: **`printThreadName`**.

`LogRecord` keeps its **own** `protected boolean printEventToString`, and that is what decides the
outcome. Three routes, measured on a processor built with the default, level=ERROR:

| route | when | bytes/event | ns/event | works? |
|---|---|---|---|---|
| `addEventAudit(INFO, false)` | build | **0.0** | 118.6 | **yes** |
| `mgr.printEventToString(false)` before `init()` | runtime | 208.0 | 151.0 | **no** |
| `mgr.printEventToString(false)` after the control event | runtime | 208.0 | 146.9 | **no** |
| `setAuditLogRecordEncoder(new LogRecord(clock))` with flag off | runtime | **0.0** | 113.0 | **yes, with a caveat** |
| *(unchanged default, for reference)* | — | 184.0 | 143.3 | — |

**Setting the manager's field is measurably not equivalent** — the field reads `false` in the printout
and 208 bytes are still allocated, because the `LogRecord` already holds its own copy. Worse than
leaving it alone, consistently, by 24 B/event. Why it rises rather than merely failing to fall is not
established here.

**The encoder route is the working runtime one — and it is a footgun.** Zero allocation and the
fastest of all at a suppressing level, but at INFO, where records actually publish, it grows without
bound and dies:

```
java.lang.OutOfMemoryError: Java heap space
  at LogRecord.addSourceId(LogRecord.java:141)
  at LogRecord.addTrace(LogRecord.java:131)
  at EventLogger.logNodeInvocation(EventLogger.java:240)
```

Calling `r.clear()` in the sink does **not** fix it, and the *default* record survives the identical
sink for 5,000,000 events. So a supplied `LogRecord` is not wired the way the generated one is. **This
is recorded as measured, not diagnosed — the likeliest explanation is that the harness is misusing the
API**, and the generated constructor's `setClearAfterPublish(false)` suggests there is a companion call
that was not made. Either way it is a documentation gap: the API is reachable, plausible, and lethal.

**Answer: treat it as a build-time flag.** `addEventAudit(level, false)` is the route that is both
correct and safe.

## 6. Every configuration, one table — what the audit log actually costs

Same ten nodes, same reused event, G1 unless stated:

| configuration | throughput | ns/event | bytes/event | vs no audit |
|---|---|---|---|---|
| **no audit in the graph at all** | **56,663,294 /s** | **17.9** | **0.0** | 1.0× |
| audit built in, `LogLevel.NONE` | 27,024,086 /s | 37.0 | 0.0 | 2.1× slower |
| audit, `addEventAudit(INFO,false,false)`, level ERROR | 9,061,427 /s | 110.4 | 0.0 | 6.2× |
| audit, `addEventAudit(INFO,false)`, level ERROR | 8,277,522 /s | 120.8 | 0.0 | 6.7× |
| audit default, level ERROR (nothing published) | 7,008,515 /s | 142.7 | 184.0 | 8.0× |
| audit, `addEventAudit(INFO,false,false)`, level INFO | 2,798,140 /s | 357.4 | 276.0 | 20.0× |
| audit default, level INFO (10M records) | 2,265,304 /s | 441.4 | 460.0 | 24.7× |

**Answered directly: with no audit log the processor runs at 17.9 ns/event, 56.7M events/sec — and
that is the same 25× that separates it from a fully-tracing INFO configuration.**

Three things worth saying about that table:

1. **Merely compiling audit in, with the level at NONE, costs 2.1×** (37.0 vs 17.9 ns) at zero
   allocation. The `auditInvocation` call sites are emitted unconditionally — 27 of them in this
   10-node graph — and the level is checked inside each. Availability is not free.
2. **The whole 184 B/event default is avoidable with one boolean**, and the resulting configuration is
   both allocation-free and 16% faster than the default at the same level.
3. **The RCA artefact this project is built on costs ~25× throughput when it is actually recording.**
   That is a real price and no document in this repo had stated it. It is also, in fairness, the price
   of writing 15.5 billion characters of evidence — and it is a *runtime level*, so it can be off in
   the hot path and on when something is being investigated.
