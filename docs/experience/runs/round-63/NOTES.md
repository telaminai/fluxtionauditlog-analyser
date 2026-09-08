# Round 63 — what does the audit trail cost, and can it be zero-allocation?

**Runtime** Oracle GraalVM 25.0.4+7.1, macOS/aarch64 · **Graph** 10 nodes, **3 of which log** (`tickIn`,
`exposure`, `buffer`) — boundaries and decision points, not every arithmetic step.
**Sink** no-op; this measures the cost of BUILDING a record, not writing it. Allocation measured
directly with `ThreadMXBean.getThreadAllocatedBytes`, not inferred.

**Owner's ask:** *"We should have a low latency audit profile. Audit on, no tracing, no event to string
and anything else not needed. We should be zero gc and the messages should be short id names and not on
every node."*

---

> **⚠ SECTIONS 1–3 BELOW WERE MEASURED ON THE WRONG JDK AND ARE SUPERSEDED BY §5.**
> The benchmark ran on whatever `java` was on `PATH` — **Corretto 21** — while every other measurement
> today used GraalVM 25. `StringBuilder.append(double)` allocates **96 bytes on JDK 21 and 0 on JDK 25**.
> The owner said *"the audit log is zerogc, you must be allocating somewhere"* and was right: the
> allocation was the JDK's, not Fluxtion's. Kept below unedited because the wrong conclusion and how it
> was reached are the useful part.

## 1. The measurement

| mode | ns/event | **bytes/event** |
|---|---:|---:|
| `none` — `LOWEST_LATENCY`, no audit | **5.40** | **0.000** |
| `minimal` — audit, tracing OFF, `printEventToString(false)`, `printThreadName(false)` | 265.3 | **432.003** |
| `traced` — audit + full node-invocation tracing | 418.9 | 432.003 |

**Tracing is not the expensive part.** It costs 154 ns and **zero extra bytes**. Turning it off is worth
having and is not what stands between the current state and a low-latency audit profile.

## 2. The blocker, named: rendering a `double` as decimal text

Same graph, same three logging nodes, same five values per record — logged as `long` instead of
`double`:

| values logged as | ns/event | bytes/event |
|---|---:|---:|
| `double` | 265.3 | **432.003** |
| `long` | **153.9** | **0.003** |

**Double-to-text formatting is the entire allocation and 42% of the time.** `StringBuilder.append(long)`
writes digits in place; `append(double)` runs a decimal-conversion that allocates. Nothing else on the
path allocates — `LogRecord` reuses one `StringBuilder`, `clear()` is `setLength(0)`, `init()` sets
`clearAfterPublish = true`, and the sink is handed the record object rather than a String.

**So the owner's zero-GC requirement is achievable today, and the axis is not the one the ask
assumed.** Short id names would shrink the record; they would not remove the allocation. *Values
rendered as decimal text* are what allocate.

## 3. Where that leaves the profile

**Achievable now, unchanged code:** audit on, tracing off, no event `toString`, no thread name, few
nodes logging, **values logged as integers** → **0.003 bytes/event and ~154 ns/event**.

**Still 28× the no-audit path** (5.40 → 153.9), so it is an "on for a venue that can afford 150 ns"
mode rather than free. Against Mongoose's 250 ns p50 it is +62%; against a 100 ns budget at 10M msgs/s
it does not fit.

**The design that would change the answer** — and it fits the architecture rather than fighting it:
carry values as **raw bits or fixed-point integers** in the record and let the **analyser** render them.
The analyser already owns interpretation; the processor does not need to format decimals on the event
path to produce a log the analyser can read. That is a real piece of work, aimed at a measured cost.

## 4. Method notes

- **The no-op sink was checked for elimination.** A genuinely empty `processLogRecord` can let the
  compiler delete the record construction feeding it, which would read as "free". A counting sink was
  run alongside and confirmed one record per event (100,002 and 100,000 over 100,000 events).
- **The first run was invalid and looked catastrophic.** The sink is **not** carried into generated
  source — the generator emits `new EventLogManager()` and the sink is set at runtime via
  `getAuditorById(EventLogManager.NODE_NAME).setLogSink(...)`. Until that was found, records were being
  written to stdout and the audited modes were measuring console I/O.
- Allocation was measured, not inferred from an Epsilon-GC OOM.


---

## 5. CORRECTED — measured on GraalVM 25.0.4, the JDK everything else today used

**Isolation probe, no Fluxtion involved — just a `StringBuilder`:**

| | JDK 21 (Corretto) | JDK 25 (GraalVM) |
|---|---:|---:|
| `append(double)` | **96.000 bytes/call** | **0.000** |
| `append(long)` | 0.000 | 0.000 |
| `append(int)` | 0.000 | 0.000 |

Four doubles per record × 96 B ≈ the 432 B/event §1 reported. **The allocation was the JDK's.**

**The audit path re-measured on JDK 25:**

| mode | ns/event | bytes/event |
|---|---:|---:|
| `none` — no audit | **5.4** | 0.000 |
| `minimal` — audit, tracing off | **262** | **0.003 — zero** |
| `traced` — audit + node tracing | 446 | **0.003 — zero** |

**The audit log is zero-GC, with `double` values, unmodified, today.** The owner's requirement was
already met and this round briefly claimed otherwise.

### 5.1 What survives, and what does not

**Does not survive:** *"double-to-text formatting is the blocker"*. There is no allocation blocker. §2's
432 B and §3's design proposal — carry raw bits, let the analyser render — were aimed at a JDK artifact.

**Survives, and is the real finding:** **audit costs ~256 ns/event on top of a 5.4 ns graph** — 48×.
That is not allocation-driven; it is unchanged between JDK 21 (265) and JDK 25 (262), where allocation
went from 432 B to zero. It is the cost of building a text record.

**Also survives, weakened:** integer values are still cheaper in *time* — 222 ns against 275 on JDK 25,
about 20%. Worth having, no longer worth redesigning for.

**Tracing costs 184 ns and no allocation** (262 → 446), confirmed on both JDKs.

### 5.2 The profile, as measurable today

Audit on, tracing off, no event `toString`, no thread name, few nodes logging:
**~262 ns/event, zero allocation, ~3.8M events/sec.** With integer values, ~222 ns.

### 5.3 Method failure

**The benchmark did not pin its JDK.** Every native measurement today explicitly invoked GraalVM 25;
the JIT audit runs used bare `java` and silently got Corretto 21. One line of output —
`java -version` — would have caught it, and it is now the first thing the audit bench prints.

That is the second time today a measurement was invalidated by an unstated input (the first was
`--attempts 1` on the node-count sweep). Both were caught by someone questioning the result rather than
by the harness.

## 6. The real sink: Mongoose's Chronicle capture service

Every audit figure above uses a **no-op sink** — the cost of *building* a record and dropping it. The
owner's question was the obvious next one: Mongoose already ships an encoder
(`ChronicleAuditCaptureService`), and the deployed cost is build **plus** encode **plus** write.

Wired through the service's own public API — `attach(dataFlow, name)` / `start(name)`, production
`AgronaCountersService`, a real memory-mapped queue on disk — on the **same** 30-node / 5-event /
shared-tail graph as §6's tail arms, changing **only** the sink.

### 6.1 Predictions, recorded before the decomposition ran

The two end points were already measured when these were written (no-op 156.7, Chronicle 736.6, so the
sink adds **~580 ns**). What was *not* known is where the 580 goes. `onRecord` does five things:
`asCharSequence()`, `writingDocument()/text()/close()`, `AtomicLong.incrementAndGet()`,
`Instant.now()`, `MongooseCounter.increment()`.

| # | Prediction | Basis |
|---|---|---|
| V1 | `asCharSequence()` alone adds **< 30 ns** | the text is already built by `auditLog.info(k,v)` during the cycle; this only terminates the record |
| V2 | the bookkeeping trio (AtomicLong + `Instant.now()` + counter) adds **50–90 ns** | `Instant.now()` is a clock read plus a 24 B allocation; the rest are single increments |
| V3 | the Chronicle document write is **the dominant term, > 400 ns** | it is the only part that touches a memory-mapped file and UTF-8-encodes 221 bytes |
| V4 | allocation is **24 B/event and all of it is `Instant.now()`** | measured 24.003 B/event; `Instant` is exactly 24 B and is the only allocation on the path |
| V5 | removing `Instant.now()` alone recovers **> 40 ns and all the allocation** | it is a liveness display field, not part of the record |

V4 and V5 are the ones with a fix attached: if they hold, the encoder is one line away from zero-GC.

### 6.2 Results — the write is everything, the rest is noise

OpenJDK 25.0.2, JIT, 2 reps × 2M events after 500k warm, 30-node shared-tail graph, minimal audit
profile. Each arm is a `LogRecordListener` doing a strict prefix of `onRecord`'s work.

| Arm | ns/event | Δ vs previous | alloc B/event | what it does |
|---|---:|---:|---:|---|
| `noop` | 151.7 | — | 0 | discard the record |
| `cs` | 155.3 | **+3.6** | 0 | `asCharSequence()` |
| `book` | 184.0 | **+28.7** | **24** | + AtomicLong + `Instant.now()` + Agrona counter |
| `queue` | 726.6 | **+571.3** | 0 | `cs` + the Chronicle document write |
| `noinstant` | 714.0 | — | 0 | everything except `Instant.now()` |
| `full` | 734.1 | +20.1 over `noinstant` | **24** | a faithful re-implementation of `onRecord` |

The service wired through its own public API (`attach`/`start`, production `AgronaCountersService`,
real queue on disk) measured **736.6 ns** — `full` reproduces it to within 0.4%, so the decomposition
is measuring the real thing.

### 6.3 Scoring

| # | Predicted | Measured | |
|---|---|---|---|
| V1 | `asCharSequence()` < 30 ns | **+3.6 ns** | ✅ |
| V2 | bookkeeping trio 50–90 ns | **+28.7 ns** | ❌ low by ~2× |
| V3 | the document write dominates, > 400 ns | **+571.3 ns — 98% of the sink** | ✅ |
| V4 | allocation is 24 B and all of it is `Instant.now()` | `book` allocates 24 B with no queue; `queue` allocates 0; `full` 24 | ✅ |
| V5 | dropping `Instant.now()` recovers > 40 ns **and** all allocation | **all the allocation, but only 20 ns** | ➗ half |

**V1 is the one worth stating plainly.** `asCharSequence()` costs 3.6 ns because the text was already
built — `auditLog.info(k, v)` appends into the record's own `StringBuilder` during the cycle, and the
sink only terminates it. The 151.7 ns baseline is *already* a fully-rendered 221-byte text record. The
no-op arm was never "audit without the text cost"; it was "text built, nothing done with it".

**The fix V4/V5 points at is still worth making** and is one line: `Instant.now()` per record buys a
liveness timestamp for a monitoring display and costs the encoder its zero-GC property. A
`System.currentTimeMillis()` long, or a stamp taken on the reader side, gives the same display for
0 bytes. It is 3% of the time and 100% of the allocation.

### 6.4 The number that actually matters, and the next question

**571 ns to append 221 bytes.** Chronicle Queue is not a 571 ns/append product — it is sold in the
low hundreds of nanoseconds and below. So either the cost is *per byte* (the record is large and the
run writes **553 MB in ~1.5 s**, faulting in freshly-mapped pages), or it is *per append* (queue
header, index, roll checks) and the record size is incidental.

That distinction decides whether the binary record discussed earlier is worth building, so it gets its
own predictions before anything is measured:

| # | Prediction | Basis |
|---|---|---|
| W1 | a standalone 221-char append reproduces the in-graph cost, **500–650 ns** | if it does not, the cost is not in the appender and §6.2's attribution is wrong |
| W2 | a 32-char append is **< 200 ns** | if cost tracks bytes, a 7× smaller record should be several times cheaper |
| W3 | an 8-byte binary `long` append is **< 120 ns** | the floor of the appender itself |
| W4 | the dominant term is **page-faulting newly mapped file pages**, not encoding | 553 MB touched in 1.5 s is ~360 MB/s of first-touch |

W2 and W3 are the binary-record case. If they land, a compact binary record is worth roughly the ratio
between them and 571 ns; if they come out flat near 571, record size is irrelevant and the answer is to
write fewer records, not smaller ones.

### 6.5 Results — it is the text encoder, and only the text encoder

No Fluxtion in this probe at all: a bare Chronicle appender, same JVM, 2 reps × 2M appends.

| Shape | ns/append | bytes on disk | vs `long8` |
|---|---:|---:|---:|
| `long8` — one `int64` | **70.5** | 33.6 | 1.0× |
| `bytes32` — a 32-byte blob | **79.7** | 60.5 | 1.1× |
| `bytes221` — a **221-byte blob** | **96.8** | **248.4** | 1.4× |
| `text32` — a 32-char wire string | **148.7** | 60.5 | 2.1× |
| `text221` — a **221-char wire string** | **665.0** | **248.4** | **9.4×** |

Read the last two rows together. `bytes221` and `text221` put **the same number of bytes on the same
disk** — 248.4 each. One costs 96.8 ns, the other 665.0. Nothing about I/O, page faults, file growth
or append overhead can explain a 6.9× gap between two runs that write identical volume.

| # | Predicted | Measured | |
|---|---|---|---|
| W1 | standalone 221-char append reproduces the in-graph cost, 500–650 ns | **665** — attribution reproduced, band 2% low | ➗ |
| W2 | 32-char append < 200 ns | **148.7** | ✅ |
| W3 | 8-byte binary append < 120 ns | **70.5** | ✅ |
| W4 | the dominant term is page-faulting newly mapped pages | **falsified** — same bytes, same pages, 6.9× apart | ❌ |

**The cost is `ValueOut.text(CharSequence)` — character-by-character wire-string encoding, ~2.6 ns per
character.** Not the disk, not the queue, not Fluxtion. W4 was the confident one and it was wrong; it
was wrong in the useful direction, because "it is the disk" would have closed the question and "it is
the encoder" opens a fix.

This is the same shape as the `TextProbe` result from earlier in the day — text formatting is the
expensive thing on every path measured, and it is expensive again here, in someone else's library.

### 6.6 What a binary record is worth — predictions

Two independent text costs sit on the audited path, and each has a separate fix:

1. **Sink side, ~571 ns**: the record is handed to Chronicle as a *wire string*. Handing the same
   bytes over as an opaque blob is a Mongoose-side change of one call — `text(cs)` → `bytes(...)` —
   and the queue file contains the same content either way.
2. **Graph side, ~142 ns**: the 151.7 ns baseline is *not* "audit without text". `auditLog.info(k, v)`
   renders into the record's `StringBuilder` as the cycle runs, so by the time the sink sees it the
   221 characters already exist. The no-audit arm on this graph is 10.0 ns (JIT). The other ~142 ns is
   building the text.

Only (1) is measurable without a core change, so only (1) gets measured now.

| # | Prediction | Basis |
|---|---|---|
| X1 | a `binbytes` arm — identical to `full`, but `bytes(...)` instead of `text(cs)` — lands at **230–280 ns/event** | 151.7 baseline + ~97 ns `bytes221` append + ~29 ns bookkeeping |
| X2 | that is **≥ 2.6× faster than `full`** end-to-end | 734.1 ÷ ~275 |
| X3 | it stays **zero-allocation** if `Instant.now()` goes with it | §6.3 V4 |
| X4 | the queue file is **the same size**, ±2% | the same characters, carried differently |

### 6.7 Results — the one-call change is worth 2.2×, and the copy loop is why it is not 2.6×

`binbytes` is `full` with `text(cs)` replaced by a copy into a reused direct `Bytes` buffer and
`bytes(buf)`. Same content, same queue, same everything else.

| Arm | ns/event | alloc B/event | queue B/event |
|---|---:|---:|---:|
| `full` (same session) | 748.4 | 24.003 | 221.5 |
| **`binbytes`** | **340.7** | **0.004** | **221.5** |

| # | Predicted | Measured | |
|---|---|---|---|
| X1 | 230–280 ns/event | **340.7** — 24% high | ❌ |
| X2 | ≥ 2.6× faster than `full` | **2.20×** | ❌ |
| X3 | zero-allocation | **0.004 B/event** | ✅ |
| X4 | same file size ±2% | **221.5 vs 221.5, exact** | ✅ |

X1 and X2 missed for one reason, and it was measurable: a `copyonly` arm — copy the characters into
the buffer and write nothing — costs **212.5 ns against `cs`'s 158.0**, so the char→byte loop is
**54.5 ns** (0.25 ns/char). X1's arithmetic budgeted zero for it.

With that term the model closes:

```
151.7  graph + record build
  3.6  asCharSequence()
 54.5  char -> byte copy      <- the term X1 forgot
 29.0  bookkeeping (AtomicLong + Instant + counter)
 96.8  bytes221 append
------
335.6  predicted   vs   340.7 measured   (1.5%)
```

### 6.8 Standing conclusions

**On the deployed audit path, ~79% of the cost is text.** 571 ns of wire-string encoding in the sink,
plus ~142 ns building the characters during the cycle, on a graph that runs in 10.0 ns.

Three changes, in ascending order of how much they cost to make:

| Change | Where | Worth | Measured? |
|---|---|---|---|
| `Instant.now()` → a `long` millis, or stamp on the reader | Mongoose, 1 line | 20 ns and **all** the allocation | yes — §6.3 |
| `text(cs)` → `bytes(buf)` | Mongoose, ~6 lines | **2.20×** end-to-end, 748 → 341 | yes — §6.7 |
| a record carrying values, not characters | Fluxtion core + a reader | the remaining ~142 ns build **and** the 54.5 ns copy | **no** — needs a core change |

Only the first two are claims. The third is arithmetic on measured parts and is written here as a
hypothesis, not a result.

### 6.9 A correction to the record

The prompt for this section assumed the analyser reads Chronicle audit logs. **It does not.**
`ReaderRegistry` registers exactly one reader — `new YamlAuditReader()` — and
`docs/proposals/upstream-asks.md` still lists **UP-RDR-01 (the Chronicle reader)** among the asks that
have not been filed. So the encoder measured here writes a queue that nothing in this repo can open
yet; the decode half of the question could not be tested because the decoder does not exist.

That makes §6.7 more interesting rather than less: the format on the wire is still unfixed, so
choosing `bytes` over `text` costs nothing in compatibility today and is 2.2× on the write side.

### 6.10 Method note — the JDK moved under the benchmark, again

GraalVM 25.0.4 is **no longer installed on this machine**, so every figure in §6 is JIT-only, on
OpenJDK 25.0.2. The native+PGO arms of §5 could not be re-run and nothing here should be compared to
them without rebuilding that toolchain. §5.3 recorded that an unstated JDK invalidated a day of
measurements; this time the JDK is stated in the section, and the missing one is stated too.

Chronicle also needs a long list of `--add-opens` / `--add-exports` on JDK 25 (`java.base/jdk.internal.misc`
for Agrona, `java.lang.reflect` for Chronicle core). That is worth knowing before anyone proposes it as
a native-image target.

## 7. The encoder, not the sink — and the seam already exists

### 7.1 What the source says (read, not inferred)

Three questions were asked. The audit sources answer all three.

**"Is it the thing that encodes that makes the difference?"** Yes, and the source shows why the sink
cannot help. `LogRecord.addRecord(String, String, double)` is:

```java
public void addRecord(String sourceId, String propertyKey, double value) {
    addSourceId(sourceId, propertyKey);   // appends "\n        - node: { key: "
    sb.append(value);                     // Ryu double -> text, into a StringBuilder
}
```

The characters are written **during the cycle**, by the node doing the logging. There are seven such
overloads (`double`, `long`, `int`, `char`, `CharSequence`, `Object`, `boolean`) and every one ends in
`sb.append`.

**"Is the Chronicle processor too late to affect the bytes we write?"** **Yes — categorically.** It is a
`LogRecordListener`, and `EventLogManager.processingComplete()` calls
`sink.processLogRecord(logRecord)` only after `terminateRecord()`. By then all 221 characters exist.
That is exactly why §6.2 measured `asCharSequence()` at 3.6 ns: nothing is left to do. (It is also in
mongoose **core**, `ChronicleAuditCaptureService`, not the web admin plugin.)

**"I thought the auditor allowed a plugin encoder."** **It does, and the memory is right.**
`EventLogControlEvent` has a public constructor taking a `LogRecord`, and `EventLogManager` swaps it in:

```java
LogRecord newLogRecord = newConfig.getLogRecord();
if (newLogRecord != null) {
    newLogRecord.updateLogLevel(logRecord.getLogLevel());
    newLogRecord.replaceBuffer(logRecord.sb);
    this.logRecord = newLogRecord;
    this.logRecord.setClock(clock);
    updateLogRecord();            // re-points every node's EventLogger at the new record
}
```

So a record subclass that overrides the seven primitives and writes **bits instead of characters** can
be installed today, as an ordinary event, with **no core change**. Whether that is the right long-term
shape is a separate question (§7.4) — but it is testable now, which means it gets measured before it
gets designed.

### 7.2 The target, and predictions recorded before building

Goal, as set: **10M+ msgs/sec fully audited**, excluding the actual disk or network write — i.e.
**≤ 100 ns/event** for graph + record construction + encode into a buffer.

Where that stands today on this graph (JIT, OpenJDK 25.0.2): graph alone **10.0 ns**; graph + text
record **151.7 ns** = **6.6M/s**. The text record build is ~142 ns and is the entire gap.

| # | Prediction | Basis |
|---|---|---|
| Y1 | a binary record build costs **35–60 ns/event** | `sb.append(double)` is a Ryu conversion (tens of ns); a raw 8-byte store is ~1 ns. The keys and node names become small ids instead of appended strings |
| Y2 | graph + binary record totals **< 100 ns — the 10M/s goal is met on JIT** | 10.0 + Y1 |
| Y3 | it is **zero-allocation** | nothing on the path allocates once the buffer is reused |
| Y4 | the encoded record is **< 80 bytes** against 221 characters | 2-byte ids for names, 8 bytes for a double instead of ~17 characters |
| Y5 | the **`==` identity cache on `sourceId` hits ~100%** | generated code passes interned String constants, and calls cluster by node |

Y2 is the one that matters. If it holds, the 10M/s target is not a stretch goal — it is what the
existing seam delivers once the encoder stops making text.

### 7.3 Results — the goal is met, at 16.6M/s

`BinaryLogRecord extends LogRecord` overrides all seven `addRecord` overloads plus `addTrace`,
`triggerEvent`, `triggerObject`, `terminateRecord` and `clear`, and never touches the inherited
`StringBuilder`. Node names and property keys intern to `short` ids; values are a type tag plus raw
bits. It is installed with `p.onEvent(new EventLogControlEvent(binaryRecord))` — **the seam that
already exists, no core change**. No-op sink in both arms: nothing is written to disk or network,
which is what the target excludes.

| Record | ns/event | msg/sec | alloc B/event | bytes/record |
|---|---:|---:|---:|---:|
| stock `LogRecord` (text) | 155.7 | **6.4M** | 0 | 193.2 |
| **`BinaryLogRecord`** | **60.4** | **16.6M** | **0** | **54.0** |

**2.58× on time, 3.6× on size, still zero-allocation.**

| # | Predicted | Measured | |
|---|---|---|---|
| Y1 | binary record build 35–60 ns | **50.4 ns** (60.4 total − 10.0 graph) | ✅ |
| Y2 | total < 100 ns, 10M/s met | **60.4 ns → 16.6M/s** | ✅ |
| Y3 | zero-allocation | **0.000 B/event** | ✅ |
| Y4 | record < 80 bytes | **54.0** vs 193.2 | ✅ |
| Y5 | the `==` identity cache hits ~100% | **0%**, then **50%** after a fix — and neither mattered | ❌ |

**Y5 is the instructive failure.** The first cache hit 0% because `head()` calls `idOf(sourceId)` then
`idOf(propertyKey)`, alternating, so a one-slot cache is defeated on every call — a design defect, not
a measurement. Split into two slots it reaches 50%: the key repeats, the node alternates between the
two nodes that log. **And the fix changed the time by nothing measurable** (60.4 → 61.5, inside noise).
So interning was never on the critical path, and the tempting optimisation of a perfect id cache is
worth zero. That is only knowable because the prediction was wrong out loud.

### 7.4 Where the remaining 50 ns goes — predictions

The record holds two property writes (the minimal profile logs on two nodes), a header and a
terminator: 54 bytes, ~20 field writes. That should not cost 50 ns. Reading `Clock` says why it might:

```java
public long getWallClockTime() { return wallClock.getWallClockTime(); }   // -> System::currentTimeMillis
```

`LogRecord` calls it **twice per record** — `logTime` in the header, `endTime` in the terminator —
through a `ClockStrategy` interface, live, every time. And `Clock.eventReceived` **already read the
wall clock once** for this event and cached it in `processTime`. So one of the two reads is redundant
with work the framework has already done.

| # | Prediction | Basis |
|---|---|---|
| Z1 | `System.currentTimeMillis()` costs **15–30 ns** here | macOS arm64, through an interface call |
| Z2 | `logTime` from `getProcessTime()` instead of a live read drops the binary arm to **40–48 ns → 21–25M/s** | removes one of two live reads |
| Z3 | removing both live reads gives **28–35 ns → ~30M/s** | removes the other |
| Z4 | the **text arm improves by the same absolute amount**, ~155.7 → ~125 | if it is the clock, it is the clock in both encoders |

Z4 is the control. If the text arm does not move by the same number of nanoseconds, the clock is not
what Z1–Z3 say it is.

### 7.5 Results — the clock, and the correctness gate

`ClockProbe`, 50M reads, nothing else on the path:

```
viaClockStrategy = 12.293 ns      directCurrentTimeMillis = 12.344 ns
```

The `ClockStrategy` interface indirection costs **nothing** — it inlines away completely. The 12.3 ns
is `System.currentTimeMillis()` itself.

| Arm | ns/event | msg/sec | bytes/record |
|---|---:|---:|---:|
| text, stock `LogRecord` | 153.5 | 6.5M | 193.2 |
| text, `logTime`/`endTime` from `getProcessTime()` | **139.8** | **7.2M** | 193.2 |
| binary, live clock | 59.6 | 16.8M | 54.0 |
| **binary + `getProcessTime()`** | **49.2** | **20.3M** | **54.0** |

| # | Predicted | Measured | |
|---|---|---|---|
| Z1 | a wall-clock read costs 15–30 ns | **12.3 ns**, and the interface indirection is free | ❌ low |
| Z2 | binary drops to 40–48 ns / 21–25M | **49.2 ns / 20.3M** — just outside both bands | ❌ marginal |
| Z3 | removing the second read gives 28–35 ns | **48.1 ns** — no further gain | ❌ **mis-specified** |
| Z4 | the text arm improves by the same absolute amount | binary saved **10.4 ns**, text saved **13.7 ns** | ✅ control holds |

**Z3 failed because the arm was wrong, not because the hypothesis was.** `now()` serves *both*
`logTime` and `endTime`, so `clock=process` had already removed both live reads; `clock=none` only
removed a field read, and measured accordingly. Z2 and Z3 were never separable as written.

One real number falls out of the gap between Z1 and Z2: two reads at 12.3 ns each in isolation cost
**10.4 ns together** inside the record build. The marginal cost of a clock read surrounded by real work
is about half its isolated cost — the core has other things to be getting on with while it waits.

**Correctness gate.** `CorrectnessBinary` drives the same 40 events through both encoders, decodes the
binary form through its id dictionary, and asserts every node/key/value appears in the text record with
the same rendered value:

```
PASS  records=40  entries verified=80

eventLogRecord:                                  same record, decoded from 54 bytes:
    eventTime: 1788853484794                     event=com.bench.E0
    logTime: 1788853484794                       [Entry[node=t5, key=v, value=7.917840894551858],
    groupingId: null                              Entry[node=t5, key=n, value=1]]
    event: E0
    nodeLogs:
        - t5: { v: 7.917840894551858, n: 1}
    endTime: 1788853484794
```

The double round-trips to the last digit — raw bits are exact by construction, where the text path is
exact only because `Double.toString` happens to be round-trip safe.

### 7.6 What this says for core

**The goal is met and then some: 20.3M msg/sec fully audited, zero allocation, 54 bytes/record,**
excluding disk and network — against a 10M target. Two changes get there, and they are very different
in size.

**One is a core one-liner and is arguably a correctness fix, not an optimisation.** `logTime` means
"when processing began". `Clock.eventReceived` **already read the wall clock for this event** and cached
it as `processTime`. `LogRecord` ignores that and takes a *second, later* reading — so today's `logTime`
is not the time processing began, it is some moments after. Using `getProcessTime()` is both more
correct and **13.7 ns cheaper on the existing text path, for every user, with no API change**.
`endTime` is different: it genuinely needs a live read, because `endTime − logTime` is the processing
duration. Keep it live, or make it optional under the latency profile.

**The other is the encoder, and the seam is real but not shaped for it.** `BinaryLogRecord` needed no
core change to *run*, but three things say core should own the abstraction rather than leaving it to
subclasses:

| Friction | Why it bites |
|---|---|
| `protected final StringBuilder sb` | every binary record carries a `StringBuilder` it will never write to |
| `EventLogManager` calls `newLogRecord.replaceBuffer(logRecord.sb)` on swap | the swap path pushes *text* into a record that has no text |
| `asCharSequence()` is the only expression channel | a binary record must throw from it, and every sink must downcast to get at the bytes |

So the core change to specify is: **let a record express itself as bytes, not only as characters.**
Either `LogRecord` becomes abstract with the YAML behaviour in a `TextLogRecord` subclass, or the
encoder becomes an interface behind it. Either way `LogRecordListener` needs a byte-facing path so a
sink can write a record without downcasting to a vendor class.

That is worth doing *because* the seam already works: the 20.3M/s number is not a design sketch, it is
measured through the shipped `EventLogControlEvent` path with a correctness check attached.

**And it composes with §6.** A 54-byte binary record handed to Chronicle as `bytes` rather than a
221-char wire string removes both text costs at once — the ~104 ns of building characters *and* the
571 ns of encoding them. §6 and §7 are the same finding from two ends.

**Scope note:** all of §7 is JIT, OpenJDK 25.0.2. GraalVM is not installed on this machine, so the AOT
half of the target is **not yet measured**. Given §6.5's `TextProbe` result — AOT is *slower* than JIT
at text formatting and faster at dispatch — removing the text is expected to help AOT more than JIT,
which makes it the more interesting arm and the one still outstanding.

## 8. Native AOT with PGO — and the text penalty lands where it was predicted

GraalVM was never deleted; the path was lost. Oracle GraalVM **25.0.4+7.1** (the PGO-capable build)
is at `scratchpad/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home`, now pinned in
`scratchpad/GRAALVM_HOME.txt` so it cannot be mislaid again.

Method: one shared `--pgo-instrument` image; each arm collects **its own** profile; each final image is
built with `--pgo=<that arm's profile>` only, because round 60 established that the profile decides the
AOT mode. Profile SHA verified unchanged across every build. 3 reps × 3M events, `-Dwarm=500000`.

| Arm | JIT ns | JIT msg/s | **Native ns** | **Native msg/s** | native ÷ JIT |
|---|---:|---:|---:|---:|---:|
| text, live clock | 153.5 | 6.5M | **221.7** | 4.51M | **1.44× slower** |
| text, `getProcessTime()` | 139.8 | 7.2M | **221.5** | 4.51M | 1.58× slower |
| binary, live clock | 59.6 | 16.8M | **90.6** | 11.04M | 1.52× slower |
| **binary + `getProcessTime()`** | **49.2** | **20.3M** | **78.1** | **12.8M** | 1.59× slower |

### 8.1 The 10M target

**Met on both.** 20.3M/s on JIT and **12.8M/s native**, fully audited, zero allocation, 54 bytes per
record, excluding disk and network. The text encoder reaches neither: 6.5M and 4.5M.

### 8.2 AOT is slower at building records — exactly as the isolated probe said

`TextProbe` (§6, no Fluxtion in it) measured AOT **1.44× slower than JIT at text formatting**. The
text record arm here comes in at **1.44×**. That is the same number from an independent experiment, and
it is worth stating plainly because the intuition runs the other way: on this graph AOT is *2.9× faster
at dispatch* (3.4 ns vs 10.0) and *simultaneously* 1.44× slower at making the record.

The penalty is not confined to text. The binary arm is 1.59× slower on AOT too — raw byte stores, no
formatting. So **AOT's disadvantage here is record construction generally**, not string conversion
specifically; text is simply where there is most of it.

Scoring the one prediction §7.6 recorded about this:

| Predicted | Measured | |
|---|---|---|
| removing the text helps **AOT more than JIT** | absolute: AOT saves **143.6 ns**, JIT **104.3 ns** ✅ · ratio: AOT **2.84×**, JIT **3.12×** ❌ | ➗ |

Right in nanoseconds, wrong in multiples. The absolute saving is what a latency budget is spent in, so
the useful half held — but the claim as written was ambiguous between the two and should not have been.

### 8.3 An unexplained asymmetry, recorded rather than explained away

The `getProcessTime()` change is worth **13.7 ns on JIT text** and **~0 ns on native text**
(221.985 / 221.215 — inside noise), while on the binary arm it is worth **10.4 ns on JIT** and
**12.5 ns on native**. So the same edit pays on three arms out of four and vanishes on exactly one.

No measurement here explains that. The plausible story is that the native text path is long enough for
two clock reads to hide inside it entirely, which is consistent with §7.5's finding that a 12.3 ns
read costs only ~5 ns marginally when surrounded by work — but that is a hypothesis, and this note
records it as one. It does not change the recommendation: the change is a correctness fix that happens
to pay on three arms and costs nothing on the fourth.

## 9. §8 was measured wrong — two method errors, one of which contradicts round 62

The owner asked why native is slower than JIT, whether the PGO is valid, and whether indirection
explains it. Checking the build logs: **the PGO is valid** — `Graal compiler: optimization level: 3,
target machine: armv8.1-a, PGO: user-provided`, with ~4 MB profiles per arm and SHAs verified unchanged.
That is not the problem. Two other things are.

### 9.1 Error 1 — one image, three `LogRecord` subclasses

`BenchBinary` references stock `LogRecord`, `ProcessTimeLogRecord` **and** `BinaryLogRecord`, and §8
built **one image containing all four arms**. So every `logrecord.addRecord(...)` call site in
`EventLogger` has three reachable receivers, closed-world, with no speculation available.

The JIT has the opposite situation: it profiles the call site, finds one receiver in practice, and
inlines behind a guard.

**Round 62 §7 established that static provability of the receiver is the dominant AOT variable** — and
§8 then handed AOT an unprovable call site and reported the result as a property of AOT. It is not; it
is a property of the harness.

### 9.2 Error 2 — Serial GC, not epsilon

`Garbage collector: Serial GC`. Every previous native measurement in this work used `--gc=epsilon`.
Serial GC emits card-marking write barriers on reference stores; epsilon emits none. On a zero-allocation
path that should be small, but it is an uncontrolled difference from every earlier figure.

### 9.3 What §8's numbers therefore mean

`text`, `binary`, and the ratios between them stand as an *internal* comparison — all four arms carried
both errors equally, so relative ordering within §8 is intact. **The native-vs-JIT comparison does not
stand** and §8.2's claim that "AOT is slower at building records generally" is withdrawn pending §9.5.
*(Written before the rebuild. **§9.6 reinstates it** — the two errors were real but worth ~14 ns against a
~57 ns gap. This withdrawal was premature, and is itself withdrawn.)*

The one thing that survives untouched is the `TextProbe` result from §6: that probe contains **no
Fluxtion and no `LogRecord` subclass at all**, so it had nothing to be polymorphic about, and its
1.44× AOT text penalty is independent of both errors.

### 9.4 Predictions, recorded before the rebuild

Two variables, isolated separately. `poly` = all record classes reachable (as §8); `mono` = only the
one arm's record class reachable; GC as marked.

| # | Prediction | Basis |
|---|---|---|
| N1 | epsilon vs serial, holding polymorphism: worth **< 10 ns** | write barriers on a zero-alloc path are real but small |
| N2 | mono vs poly, holding GC: worth **> 40 ns on text**, the larger effect by far | round 62 §7 — provability is the dominant AOT variable |
| N3 | native text, mono + epsilon, lands **≤ 175 ns** (from 221.7) | N1 + N2 |
| N4 | native text still does **not** beat JIT text (153.5) | the `TextProbe` 1.44× penalty is independent of both errors and does not go away |
| N5 | the same call site cannot be made provable for the **binary** arm at all | `EventLogManager.init()` hardcodes `new LogRecord(clock)`, so stock `LogRecord` is instantiated before the swap — two receivers are inherent to the runtime-swap seam |

**N5 is the one with a design consequence.** If it holds, the `EventLogControlEvent` swap seam is
structurally AOT-hostile, and the encoder spec's §6 change should let the encoder be chosen at **build**
time, not swapped at runtime — which is a materially stronger reason for that change than "the
`StringBuilder` is unused".

### 9.5 Results — the errors were real, and they are not the answer

`--gc=epsilon` and a trimmed classpath, rebuilt with per-arm PGO (`PGO: user-provided`, SHA verified),
3 reps × 3M events. `poly` = all record subclasses reachable; `mono` = only the arm's own.

| Arm | config | native ns | reachable types |
|---|---|---:|---:|
| text | poly + serial (**§8**) | 221.7 | 3,438 |
| text | poly + epsilon | 207.9 | 3,386 |
| text | mono + serial | 207.5 | 3,399 |
| text | **mono + epsilon** | **205.8** | 3,341 |
| binary | poly + serial (**§8**) | 90.6 | 3,438 |
| binary | **mono + epsilon** | **90.8** | 3,345 |

| # | Predicted | Measured | |
|---|---|---|---|
| N1 | epsilon vs serial worth < 10 ns | 13.8 (text/poly), 1.7 (text/mono), **0** (binary) | ❌ not separable, and confounded by a heap-size change |
| N2 | mono vs poly worth **> 40 ns** on text | **2.1 ns** (epsilon), 14.2 (serial), **0** on binary | ❌ **badly falsified** |
| N3 | native text mono+epsilon **≤ 175 ns** | **205.8** | ❌ |
| N4 | native text still does not beat JIT | 205.8 vs 148.9 | ✅ |
| N5 | two receivers are inherent to the swap seam | true as a fact — but see below | ➗ |

**The three clean configurations all land at ~206 ns and §8's 221.7 stands alone.** Read honestly, that
means the two errors together are worth ~14 ns *on one arm*, nothing on the other, and part of even that
is the build lottery round 60 documented (I also changed max heap from 512m to 1g, which is a confound I
should not have introduced).

### 9.6 §8.2 is reinstated — AOT really is ~1.4× slower at building records

With both errors removed:

| | JIT | native | native ÷ JIT |
|---|---:|---:|---:|
| text record | 148.9 | 205.8 | **1.38×** |
| binary record | 63.0 | 90.8 | **1.44×** |
| `TextProbe` (no Fluxtion at all) | 58.8 | 84.7 | **1.44×** |

Three measurements, two of them with completely different code, all at ~1.4×. **It is not indirection,
it is not the GC, it is not a missing or invalid PGO, and it is not receiver provability.** The AOT
compiler produces slower code for this arithmetic-and-conversion work, and this round cannot say why.
What it can say is which explanations are now excluded, which is worth more than a guess.

### 9.7 Why round 62's provability finding did not transfer

N2 deserves more than a ❌, because it was not a careless prediction — it was round 62 §7's headline
finding applied to a new case, and it failed.

Round 62 measured provability on **dispatch-dominated** graphs running at ~1.6 ns/event, where a single
unprovable call site is most of the work. This workload is **conversion-dominated** at 150–220 ns/event.
Two extra virtual calls per event are ~2 ns — real, and irrelevant at this scale.

**A finding is scoped to the regime it was measured in.** Round 62's conclusion is not wrong; carrying
it into a different cost regime was.

**And it kills a spec change I was about to make.** N5 was going to argue that the runtime-swap seam is
structurally AOT-hostile, and therefore that the encoder should be selected at build time. The fact
underlying N5 is true — `EventLogManager.init()` creates a stock `LogRecord` before any swap, so two
receivers are always reachable — but it is worth **~2 ns**, so it is not an argument for anything. That
paragraph did not go into the spec, because the measurement came first.

### 9.8 The other auditor — asked, found, fixed, and not worth what it looked like

The question "is there another auditor with indirection?" has a real answer. Four auditors are generated
into this processor: `clock`, `eventLogger`, `nodeNameLookup`, `serviceRegistry`.

- `nodeNameLookup` is **correctly absent** from the event path — that is W15 working.
- **`ServiceRegistryNode` was not.** It implements `Auditor`, does all its work in `registerService` /
  `deRegisterService` / `nodeRegistered`, and overrides **none** of the three per-event callbacks — so it
  inherited `auditEventReceipt() == true` and every generated processor carried
  `serviceRegistry.eventReceived(event)` **and** `serviceRegistry.processingComplete()` on every event,
  both inherited no-ops. Exactly the case W15 fixed for `NodeNameAuditor`; the second auditor in the same
  position was missed.

Fixed in core, and the processor regenerated — the event path is now `clock` and `eventLogger` only.

**Measured, it is worth nothing this harness can resolve:** text 160.2 → 157.5, binary 66.6 → 65.7, with
run-to-run spreads of 12 ns and 5 ns respectively. So **≤ ~3 ns and inside noise.**

The change stays, for the reasons `LOWEST_LATENCY`'s own documentation already gives for its two
zero-measured settings: the generated code is smaller and the declaration is truthful. It is **not** a
performance claim and must not be quoted as one.

*(Regenerating also caught a stale toolchain: the first attempt used the `~/.m2` builder jar and emitted
`nodeNameLookup.eventReceived` — pre-W15 output. A measurement against that would have shown a bigger
"win" from a compiler that was simply older.)*

## 10. The AOT gap was mostly harness — three errors, and the owner named the biggest one

The owner pushed back on §9: AOT audit *should* beat JIT, the binary figure moved 78 → 90 between
sections, and ~30 ns unexplained is too much to accept. All three concerns were justified. Three
separate faults, in ascending order of size.

### 10.1 Fault 1 — 78 vs 90 was my reporting, not instability

§8's 78.1 ns is the **`clock=process`** arm. §9's `mono_bin` runs passed no `-Dclock`, so
`BinaryLogRecord.clockMode` defaulted to `"live"`, and 90.8 is the **live** arm. I compared them as
though they were the same measurement. On one binary, both arms:

```
clock=live     82.4  82.6
clock=process  68.7  70.1
```

There was no regression to explain.

### 10.2 Fault 2 — measurements were not interleaved, on a machine with P and E cores

The same native binary read **90.8** in §9 and **82.4** minutes later. Nothing changed but machine
state. This is an Apple M4: core placement and thermal state move a benchmark ~10%, and §8/§9 ran each
arm in a block, one toolchain after the other — so every JIT-vs-native ratio in them compares two
*different machine states* as well as two compilers.

Interleaved, alternating JIT and native, idle machine, `clock=process`, 6 reps each:

```
jit     62.232  61.529  60.313  63.377  63.948  64.739     min 60.3
native  68.182  69.752  69.331  69.186  71.104  69.917     min 68.2
```

**1.13×, not the 1.44× §9.6 reported.** §9.6's ratio is withdrawn: it measured thermal drift as well as
compilers. **Minimum, not mean, is the honest statistic here**, and interleaving is mandatory from now on.

### 10.3 Fault 3 — the generated inlining directive was never on the build classpath

The generator emits `META-INF/native-image/<processor>/native-image.properties`:

```
# Forces the event-dispatch chain to inline into the caller's event loop. Without it the
# processor is passed to an un-inlined callee, escapes, and its node objects can no longer
# be scalar-replaced: 5.55 ns/event against 1.57 with it, and the loss is silent.
Args = -H:+UnlockExperimentalVMOptions -H:PriorityForceInline=<processor>.*
```

Every native build in §8 and §9 used a classpath of `tclasses:tvendor:runtime` or
`binclasses:runtime`. **None of those contains `META-INF/native-image`** — the generated resource
directory was never on it. So every AOT number in this round was built **without** the one directive
whose own generated comment says it is worth 3.5× on dispatch and that losing it is silent.

The comment was right about the silence. Nothing in the build log says the directive is absent, because
an absent directive is indistinguishable from one that was never generated.

### 10.4 Predictions, before the rebuild

| # | Prediction | Basis |
|---|---|---|
| Q1 | with the directive, native binary `process` drops from **68.2 to 55–63 ns** | the directive restores scalar replacement; the dispatch portion is ~3.4 ns of the total so the win should be smaller than the 3.5× seen on a dispatch-only graph, but escape analysis affects the record path too |
| Q2 | native **beats JIT** (60.3) on at least one arm | the owner's expectation, and the reason for looking |
| Q3 | the directive is worth **more on the text arm in absolute ns** than on the binary arm | more code on the path to keep un-escaped |
| Q4 | run-to-run spread stays ~1 ns within a binary, so 10.2's drift really was machine state | if the spread is now large, something else is loose |

Q2 is the one that decides whether AOT audit is worth recommending at all.

### 10.5 Results — the gap closes, and it was never mostly AOT

All arms `clock=process`, binary record, interleaved, 8 reps, **minimum** reported (the honest statistic
once §10.2 showed the mean carries machine drift).

| Step | JIT min | native min | native spread |
|---|---:|---:|---:|
| §9 as reported (not interleaved) | 63.0 | 90.8 | — |
| interleaved, `AUDITED` profile, no directive | 61.7 | 70.0 | 2.4 |
| + inlining directive | 61.7 | **71.3** — no change | 2.4 |
| **+ `LOW_LATENCY_AUDIT` profile** | **57.3** | **58.0** | **0.99** |

Full final runs:

```
native  58.949 58.028 58.453 58.261 58.375 58.251 59.014 58.552   min 58.03  median 58.41  spread 0.99
jit     60.012 67.168 59.612 57.255 59.864 59.633 58.999 61.756   min 57.26  median 59.82  spread 9.91
```

**Native and JIT are the same speed on this workload — 17.2M vs 17.5M msg/sec — and native's spread is
ten times tighter.** On median native is *faster* (58.41 vs 59.82); on worst case it is much faster
(59.01 vs 67.17). The originally reported "1.44× slower" was three harness faults and a missing profile.

| # | Predicted | Measured | |
|---|---|---|---|
| Q1 | the directive drops native to 55–63 ns | **71.3 — no change at all** | ❌ |
| Q2 | native beats JIT on at least one arm | median ✅ and worst case ✅; minimum ❌ (58.03 vs 57.26) | ➗ |
| Q3 | the directive is worth more on the text arm | **not run** — recorded as unmeasured, not assumed | — |
| Q4 | within-binary spread ~1 ns, so §10.2's drift was machine state | **0.99 ns native**, 9.91 JIT | ✅ |

### 10.6 Why the inlining directive does nothing here

Q1 was confidently wrong and the reason is worth keeping. The directive exists to stop the processor
escaping into an un-inlined callee so its **node objects can be scalar-replaced** — worth 5.55 → 1.57 ns
on a dispatch-only graph. On the audit path the dominant object is the `LogRecord`, which is a **field on
`EventLogManager` and lives for the whole run**. Escape analysis has nothing to win on a long-lived
object, so the directive has nothing to give.

It should still always be on the classpath — it costs nothing and the dispatch half of any real graph
still needs it. But it is not an audit-path lever, and §10.3's framing of it as "the missing
optimisation" was too strong: it was *a* missing input, worth ~0 here.

### 10.7 Scoring the owner's five hypotheses

| Hypothesis | Verdict |
|---|---|
| Bad PGO | ❌ `PGO: user-provided`, 4 MB profiles, SHAs verified across every build |
| The low-latency profile is inaccurate | ✅ **the largest single factor** — the bench built `AUDITED`, worth 5.4 ns JIT and ~12 ns native |
| Earlier optimisation work is on another branch | ❌ the merge base *is* `w2-w3-w8`'s tip, so `w4-baseline-config` already contains it |
| Inlining cache breached | ➗ the directive really was absent from every build, and is worth ~0 on this path (§10.6) |
| An untracked indirection | ➗ real — `ServiceRegistryNode` (§9.8) — but ≤3 ns, inside noise |

Four of the five were worth checking and two were right. The one that mattered most was the profile —
which is to say the number was wrong because the thing being measured was not the thing being specified.

### 10.8 What this changes in the spec

- `LOW_LATENCY_AUDIT` is **implemented** (M52.2), not just specified, and is now a measured 5.4 ns on
  JIT and ~12 ns on native over `AUDITED` on this graph.
- The spec's §7 claim that native AOT is 1.4× slower at record building is **withdrawn**. Corrected:
  **parity on throughput, ~10× tighter spread**, which is a materially better story for the
  latency-sensitive deployment the profile is aimed at.
- Every future native measurement in this work MUST interleave arms and report minimum. §8, §9 and the
  first half of §10 all failed this and all three produced a wrong headline.

## 11. Can AOT overtake JIT? Two levers, one of which the build log asked for

The owner asked whether AOT can actually get ahead, and whether code shape or concrete classes would do
it. Two things on the table that §10 did not test, found by re-reading the build log I had already been
quoting:

**Lever 1 — the target machine.** Every native build in this round reports
`target machine: armv8.1-a`. The machine is an **Apple M4**. The build's own recommendation block says
`CPU: Enable more CPU features with '-march=native' for improved performance` and I ignored it four
builds running. **A JIT always compiles for the CPU it is running on**; AOT was compiling for a generic
2016 baseline. That is not an AOT limitation, it is a flag I did not pass.

**Lever 2 — a benchmark artifact on the hot path.** `BinaryLogRecord.now()` does
`switch (clockMode)` on a **String**, twice per record (header and terminator). That is a hash plus an
equals on every call, in both arms, and it exists only because the class carries three experiment modes.
A real encoder resolves this at construction.

### 11.1 Predictions

| # | Prediction | Basis |
|---|---|---|
| R1 | `-march=native` is worth **> 2 ns** on native, enough to put its minimum below JIT's 57.26 | the JIT has always had this advantage; the gap to close is 0.77 ns |
| R2 | replacing the String switch with a `final boolean` is worth **> 3 ns on both arms** | two String switches per record |
| R3 | with both, native beats JIT on **minimum, median and worst case** | R1 + native's already-tighter spread |
| R4 | making the record receiver provable is still worth **< 3 ns** | §9 measured exactly this at ~2 ns; the owner's "concrete classes" idea is real but small |

R4 is the direct answer to "would concrete classes help": measured already, and the answer is *a little*.
R1 is the one that should decide it.

### 11.2 Results — AOT overtakes JIT, and it was the code shape

Interleaved, 8 reps, binary record, `clock=process`, `LOW_LATENCY_AUDIT`, epsilon, PGO, directive.

| Arm | min | max | spread | msg/sec |
|---|---:|---:|---:|---:|
| **native, `armv8.1-a`** | **49.86** | 51.76 | 1.90 | **20.1M** |
| native, `-march=native` | 50.62 | 53.04 | 2.42 | 19.8M |
| JIT | 57.09 | 58.59 | 1.50 | 17.5M |

**Native is 12.7% faster than JIT on minimum, 13% on median, and 12% on worst case.** The answer to
"can AOT overtake JIT" is yes, and on this workload it now does on every statistic.

| # | Predicted | Measured | |
|---|---|---|---|
| R1 | `-march=native` worth > 2 ns | **−0.8 ns — consistently *slower***, all 8 reps | ❌ |
| R2 | removing the String switch worth > 3 ns **on both arms** | **native 8.2 ns, JIT 0.17 ns** | ❌ as stated — and the miss is the finding |
| R3 | native beats JIT on min, median and worst case | **all three** | ✅ |
| R4 | concrete classes worth < 3 ns | §9 measured ~2 ns; not re-run | ✅ standing |

### 11.3 The finding: AOT cannot speculate on a runtime constant, and JIT can

R2 predicted the String switch would cost both arms about the same. It cost **native 8.2 ns and JIT
0.17 ns** — a 48× asymmetry, and it is the whole reason native was behind.

```java
public static String clockMode = System.getProperty("clock", "live");   // read once, never changes
private long now() { switch (clockMode) { case "process": ... } }        // twice per record
```

HotSpot profiles that switch, observes one value for millions of events, and **constant-folds it away**.
Native-image sees a mutable static `String` whose value is only known at run time, cannot speculate, and
pays a hash and an equals twice per record — forever, with no deoptimisation guard to buy it back.

Resolving it once into a `final boolean` at construction costs JIT nothing and hands AOT 8.2 ns.

**This is the direct answer to "would the shape of the code help?" — yes, decisively, and it is the
single largest lever found in this round.** The general rule:

> **Anything that is constant for the life of a run must be a `final` field resolved at construction,
> not a mutable static read on the hot path.** A JIT will discover the constant for you. An AOT compiler
> cannot, and the loss is silent.

That rule is *more* important for generated code than hand-written code, because a generator knows at
build time which values are fixed and can emit them as `final` — which is exactly the advantage a
hand-rolled application cannot systematically get.

### 11.4 `-march=native` is not a lever here — it is a small regression

R1 was the confident one and it was wrong in the wrong direction: `-march=native` was **slower in all 8
reps** (min 50.62 against 49.86) and had a wider spread. The build log recommends it generically; on this
workload, for this compiler version, it does not pay. It is recorded here so nobody adds it on the
strength of the build's own advice without measuring — which is what I nearly did.

### 11.5 Where this leaves the numbers

**Binary record, `LOW_LATENCY_AUDIT`, fully audited, zero allocation, 54 bytes/record, no disk:**

| | ns/event | msg/sec |
|---|---:|---:|
| **native AOT** | **49.86** | **20.1M** |
| JIT | 57.09 | 17.5M |
| text record, JIT (where this round started) | 153.5 | 6.5M |

**3.1× from where the round started, and the 10M target is met twice over on both toolchains.**

## 12. The profile disabled the audit log — and §10/§11 measured a graph with no audit

### 12.1 The bug

`LOW_LATENCY_AUDIT` set `setSupportNodeNameLookup(false)`, reading it as "drop a lookup map". It is not
that. **It stops node registration**, and `EventLogManager.nodeRegistered(node, name)` is what
constructs each node's `EventLogger` and calls `setLogger` on it. With registration off, every node's
`auditLog` is the null logger: the processor runs, the sink is installed, and **nothing is ever
published**.

Measured directly on the generated source:

| profile | `auditor.nodeRegistered` calls | audit records published |
|---|---:|---:|
| `AUDITED` | 33 | 1.000 per event |
| `LOW_LATENCY_AUDIT` (as shipped in §10) | **0** | **0** |

**So the "5.4 ns/event that `LOW_LATENCY_AUDIT` saves over `AUDITED`" was the cost of the audit log,
removed.** And every §10/§11 figure built on that profile — including "native beats JIT by 13%" — timed
a graph that was not auditing. Those numbers are withdrawn.

### 12.2 The second fault found at the same time: `-D` after the main class

`java -cp … app.BenchMonoBin -Dclock=process` puts `-Dclock=process` in `argv`. **It is not a system
property.** Proved directly:

```
after the main class :  clock property = <unset>   argv=[-Dclock=process]
before the main class:  clock property = process   argv=[]
```

A GraalVM native image *does* parse a trailing `-D` as a property. So in every §10/§11 comparison
**native ran `clock=process` and JIT ran `clock=live`** — the JIT arm carried an extra live wall-clock
read per record that the native arm did not. The comparison was never like-for-like in either direction.

### 12.3 Why the tests did not catch it

`LowLatencyAuditProfileTest` asserted the profile's *flags*. It did not assert its *behaviour*. Five
green tests, and the profile disabled the thing it exists to preserve.

Fixed in three places, each mutation-verified to fail without the fix:

- the profile no longer touches node registration, with the reasoning in the code;
- `LowLatencyAuditProfileTest.mustNotDisableNodeRegistrationBecauseThatSilentlyKillsTheAuditLog`;
- `AuditNeedsNodeRegistrationTest` in the runtime pins the premise both ways — a registered node logs,
  an unregistered one publishes nothing and reports nothing.

### 12.4 The harness now refuses to report what it cannot prove

Four silent harness faults in one round is a pattern, not bad luck. `BenchAudited` throws rather than
prints unless:

- `-Drecord` and `-Dclock` are actually set as properties — a `-D` in the wrong place is now fatal,
  not silently ignored;
- the resolved clock mode equals the requested one;
- **the sink saw a non-zero number of records**, and they were non-empty;
- the graph checksum matches across arms.

The zero-records check found the profile bug on its first run.

### 12.5 Predictions for the real graph, recorded before measuring

Two graphs, both 30 nodes / 5 event types / one shared tail, both `LOW_LATENCY_AUDIT` (fixed):

- **tail** — one node logs, 2 entries per record. Verified reference: `AUDITED` + binary + `clock=process`
  on JIT is **42.2 ns, 23.7M/s, 54 bytes/record, 1.000 records/event**.
- **converging** — **every node on the path logs into the same record**, ~8 entries. The realistic shape.

| # | Prediction | Basis |
|---|---|---|
| S1 | converging binary record is **100–160 bytes** | 54 bytes at 2 entries; an entry is 13 bytes; ~8 entries |
| S2 | converging binary on JIT lands **70–95 ns → still above 10M/s** | ~6 more entries at ~4–6 ns each on top of 42 |
| S3 | converging **text** on JIT lands **250–400 ns → below 10M/s** | text pays a node name, a key and a formatted double per entry |
| S4 | the binary:text ratio is **larger on the converging graph than the 3.1× seen at 2 entries** — predict **> 3.5×** | per-entry text cost multiplies while the header amortises |
| S5 | native lands **within ±10% of JIT** on the converging binary arm | §11's 13% claim is withdrawn; with the `-D` fault corrected I have no basis for a directional call |
| S6 | **the converging graph, binary, exceeds 10M msg/sec on both toolchains** | the owner's target, and the one that matters |

S6 is the target. S3 is the one that decides whether the binary encoder is optional or necessary.

**Build inputs to be verified in the log for every native arm, not assumed:** `PGO: user-provided`,
`Garbage collector: Epsilon GC`, `-H:PriorityForceInline` origin line, and `target machine: armv8.1-a`
(not `-march=native`, measured a regression in §11.4).

### 12.6 Results — with the audit log actually on

Every arm verified by the harness: `recPerEvent=1.000`, zero allocation, matching graph checksum
(`v=117.4865`) across all six. Every native build verified in its log for PGO, epsilon, the inlining
directive and target machine. Interleaved, 6 reps, minimum reported.

| Graph | Record | JIT ns | JIT Mmsg/s | native ns | native Mmsg/s | bytes/record |
|---|---|---:|---:|---:|---:|---:|
| tail (2 entries) | binary | **47.6** | **21.0** | 70.9 | 14.1 | 54 |
| tail (2 entries) | text | 147.2 | 6.8 | — | — | 193 |
| **converging (~11.75 entries)** | **binary** | **79.4** | **12.6** | 149.5 | 6.7 | 181 |
| **converging (~11.75 entries)** | **text** | 403.0 | 2.5 | 698.6 | 1.4 | 548 |

| # | Predicted | Measured | |
|---|---|---|---|
| S1 | converging binary record 100–160 bytes | **180.8** — the path is ~11.75 entries, not the 8 I assumed | ❌ |
| S2 | converging binary on JIT 70–95 ns, above 10M/s | **79.4 ns, 12.6M/s** | ✅ |
| S3 | converging text on JIT 250–400 ns, below 10M/s | **403.0 ns, 2.5M/s** — 0.75% over the band | ➗ |
| S4 | binary:text ratio > 3.5× on the converging graph | **5.08×**, against 3.20× on the tail graph | ✅ |
| S5 | native within ±10% of JIT | **1.88× slower** | ❌ |
| S6 | converging binary above 10M/s **on both toolchains** | **JIT 12.6M ✅ · native 6.7M ❌** | ➗ |

### 12.7 The target, answered honestly

**On a realistically audited 30-node graph — every node on the path logging into one record — the
binary encoder reaches 12.6M msg/sec on JIT and 6.7M on native.** The owner's 10M target is **met on
JIT and missed on native**.

The lighter tail shape clears it on both (21.0M / 14.1M), so the target is not out of reach for AOT; it
is the density of audit entries that decides.

**And the text encoder cannot reach it on any toolchain**: 2.5M/s on JIT, 1.4M native. On a graph where
every node logs, **the binary encoder is not an optimisation, it is the difference between 2.5M and
12.6M** — a 5.08× gap that widens with audit density (3.20× at 2 entries, 5.08× at 11.75).

### 12.8 Native AOT is slower on the audited path — confirmed, with a harness that checks itself

§8.2 claimed this, §9.3 withdrew it, §9.6 reinstated it, §10.5 withdrew it again after finding the
interleaving fault, and §11 claimed the opposite. **It is now confirmed with every input verified**:
native is 1.49× slower on the tail graph, 1.73× on converging text, 1.88× on converging binary.

The §10/§11 result that said otherwise was measuring a graph whose audit log had been silently disabled
by the profile. Once the audit is genuinely on, AOT is behind on every shape measured, and the gap grows
with the amount of record-building work.

**Concrete receivers do not explain it.** A monomorphic image — `ConvProcessor` typed concretely, the
other processor physically absent from the class tree — measures **151.5 ns against the polymorphic
image's 149.5**. That is the third time provability has been proposed as the explanation here and the
third time it has measured ~nothing. Round 62's finding does not reach this workload.

### 12.9 On the record data structure

The owner asked whether `LogRecord`'s structure is optimal. The marginal cost per logged value, from
the two graph shapes (+9.75 entries between them):

| Record | marginal ns per audit entry |
|---|---:|
| text `LogRecord` | **26.2** |
| `BinaryLogRecord` | **3.4** |

**The `StringBuilder` itself is not the problem.** It reuses capacity — `clear()` calls `setLength(0)`,
so after warm-up there is no reallocation, and allocation measures zero on every arm. The container is
fine.

**The format is the problem, and so is the timing.** Per entry the text record writes a node name, a
key, punctuation and a `Ryu`-formatted double — 26 ns of character generation — and it does it
**inside the event cycle**, on the thread that is trying to process events.

That points at a structural option the binary record only half takes:

> Collect `(nodeId, keyId, tagged value)` into a primitive buffer during the cycle, and **format only at
> publish** — or never, if the record is filtered out downstream.

`BinaryLogRecord` already does the first half, which is where its 3.4 ns comes from. The second half is
the larger prize and it is not measured here: a filtered-out record currently pays full formatting cost
before anything gets to reject it. On a system that audits everything and reads back a fraction, that is
most of the cost of auditing spent on records nobody will ever open.

Recorded as a design observation, not a result — nothing here measures a filtering sink.

## 13. Are the PGO and the compiler args actually optimal? — sweeping the audit regime

The owner's challenge: *"You have an accurate pgo and optimal compiler args."* Read as a question, the
honest answer is **the PGO is verified accurate; the args are verified applied, not verified optimal.**

The knob sweep in round 60 (`§ The knobs that do not work`) was run against the **dispatch** regime at
~1.6 ns/event. This is the **audit** regime at 80–150 ns/event, and §9.7 and §12.8 have already shown
twice that a finding does not survive the move between them. So the sweep has to be redone here.

Three candidates that were never tested and are plausible for an object-heavy record path:

| | Candidate | Why it could matter here and not in round 60 |
|---|---|---|
| A | `-H:-SpawnIsolates` | isolates put a **heap-base offset on every object reference**. The dispatch benchmark scalar-replaces its objects away; the audit path constantly touches `LogRecord`, `EventLogger` and the name/key strings, so it cannot |
| B | `--initialize-at-build-time` for the audit and bench classes | native-image emits a **class-initialisation check before static access** for runtime-initialised classes. `BinaryLogRecord.clockMode`, the `LogLevel` enums and the record statics are all on the hot path |
| C | `-H:PriorityForceInline` widened to `EventLogManager`/`EventLogger`/`BinaryLogRecord` | the shipped directive names **only the processor class**. On JIT the audit call chain inlines by profile; nothing forces it under AOT. Round 60 measured widening as "no effect" — on a path where the audit classes were not being called at all |

### 13.1 Predictions

| # | Prediction | Basis |
|---|---|---|
| U1 | `-H:-SpawnIsolates` worth **> 5 ns** | one base-register add per object access, on a path doing many |
| U2 | build-time init of the audit classes worth **> 3 ns** | a load-and-branch per static access, several per record |
| U3 | widening the inline directive to the audit classes worth **> 10 ns** — the largest of the three | the audit chain is 90% of the work and nothing forces it to inline |
| U4 | **none of them alone closes the 70 ns gap to JIT** (149.5 → 79.4) | if one did, the gap was never about AOT code quality |

U3 is the one with a design consequence: if it lands, the generator should emit the audit classes into
the directive it already writes, and every audited native build in the world is currently missing it.

### 13.2 Results — the args were NOT optimal; isolates cost 16.7%

30-node converging graph (every node logs, ~11.75 entries/record), binary record, `clock=process`,
per-arm PGO verified, epsilon, inlining directive, `armv8.1-a`. Interleaved, 5 reps, minimum.

| Arm | min ns | Mmsg/s | vs base |
|---|---:|---:|---:|
| JIT | **75.9** | **13.2** | — |
| native base | 145.8 | 6.9 | — |
| **native `-H:-SpawnIsolates`** | **121.4** | **8.2** | **−24.4 ns, 16.7% faster** |
| native `--initialize-at-build-time` | 155.4 | 6.4 | +9.6 ns **worse** |
| native widened inline directive | 150.2 | 6.7 | +4.4 ns **worse** |

| # | Predicted | Measured | |
|---|---|---|---|
| U1 | `-H:-SpawnIsolates` worth > 5 ns | **24.4 ns** — the largest AOT lever found in this work | ✅ |
| U2 | build-time init worth > 3 ns | **9.6 ns worse** | ❌ |
| U3 | widened inline directive worth > 10 ns, the largest | **4.4 ns worse**, and the smallest | ❌ |
| U4 | none alone closes the 70 ns gap | closes 24.4 of ~70 | ✅ |

**The answer to "you have optimal compiler args" is: I did not.** `-H:-SpawnIsolates` removes the
heap-base offset that isolate-mode adds to every object reference. The round-60 sweep never found it
because the dispatch benchmark scalar-replaces its objects away and has almost no references left to
offset; the audit path touches `LogRecord`, `EventLogger` and the interned strings on every entry and
cannot.

**That is the third finding in this round that did not survive the move between the dispatch regime and
the audit regime** — after provability (§9.7) and the inlining directive (§10.6). The pattern is now
strong enough to state as a rule: *a native-image flag sweep is only valid for the workload shape it was
run against.*

U3 is worth noting for the opposite reason: widening `PriorityForceInline` to the audit classes made
things **worse**, which is consistent with round 62's inlining-budget finding — forcing more code in
pushes something else out.

Native is still 1.60× behind JIT (121.4 against 75.9), so isolates were not the whole story either.

### 13.3 Clarification — "lighter shape" meant fewer audit entries, not lighter nodes

Both graphs in §12.6 use **identical nodes**: `v = p.v * 1.05 + 0.5`, one multiply and one add. The
"tail" and "converging" shapes differ **only** in how many nodes call `auditLog.info` — one against all
of them. So every figure in this round is for a **light-node** graph, and the variable being swept is
**audit density**, not computation weight.

That leaves node weight untested in the audit regime, and round 62 found node weight to be regime-
defining on the dispatch path (≤25 light nodes → 27–36× against a library; ~50 → 2.5×; heavy nodes
invert the result entirely). It is the obvious next axis and is **not** measured here.

## 14. Node weight — the axis the audit work had never varied

§13.3 admitted every figure so far used **light nodes** (one multiply, one add) and varied only audit
density. `DagNodesHeavy` keeps the graph, the shape and the converging logs identical and replaces the
node body with a 24-iteration Horner loop — real arithmetic, no allocation, no data-dependent branches.

### 14.1 JIT, measured

| Nodes | Record | ns/event | Mmsg/s | bytes/rec | text ÷ binary |
|---|---|---:|---:|---:|---:|
| light | binary | **83.2** | 12.0 | 181 | — |
| light | text | 401.5 | 2.5 | 548 | **4.83×** |
| heavy | binary | **487.9** | 2.1 | 181 | — |
| heavy | text | 760.3 | 1.3 | 609 | **1.56×** |

**The record format matters most when the nodes are light.** Node work adds ~405 ns, and it is the same
~405 ns whichever record is in use — so it dilutes the format difference from 4.83× down to 1.56×.

That is a useful thing for a reader to know before quoting either number: *the binary encoder's value is
inversely proportional to how much work your nodes do.* A graph of thin transforms gets 4.8×; a graph
doing real computation per node gets 1.6×.

It also reframes the 10M target. **10M events/sec with full audit is reachable only for light-node
graphs** — heavy nodes cost 405 ns of arithmetic before any audit at all, which is a 2M/s ceiling
regardless of encoder. That is not an audit cost and no record format can recover it.

### 14.2 Predictions for the native arm

Round 62 found node weight to be regime-defining on the dispatch path, and AOT to be strong on
arithmetic and weak on record building. Heavy nodes shift the mix decisively toward arithmetic.

| # | Prediction | Basis |
|---|---|---|
| V1 | the native ÷ JIT ratio **improves markedly** with heavy nodes — better than the 1.60× measured on light nodes | the added 405 ns is arithmetic, which §12.8 never showed AOT to be bad at |
| V2 | native **beats JIT** on the heavy-node binary arm | if AOT's deficit is confined to record building, a path that is 83% arithmetic should invert |
| V3 | `-H:-SpawnIsolates` is worth **less in absolute ns** here than the 24.4 ns on light nodes | the added work is register arithmetic, not object references, so there are no extra heap-base offsets to remove |

V2 is the interesting one: it would mean AOT-versus-JIT on an audited Fluxtion graph is decided by the
**ratio of computation to record-building**, not by auditing as such.

## 15. The baseline, and where the audit cost actually lands

The owner's methodological catch: the audit numbers had **no denominator on this shape**. Without a
no-audit baseline built the same way, "audit costs X" is not a measurement.

`DagNodesPlain` is the same 30-node converging graph with the same node bodies, but the nodes do not
extend `EventLogNode` and make no `auditLog` call at all, built with `LOWEST_LATENCY`. The generated
processor has empty `auditEvent` methods and zero `eventLogger` references. **Checksum verified equal to
the audited arm at the same iteration count** (`v=111.4572`), so it computes identical work.

| | baseline (no audit) | audited (binary, converging) | **audit cost** |
|---|---:|---:|---:|
| JIT | 12.42 ns · 80.5M/s | 75.31 ns · 13.3M/s | **62.9 ns** |
| native, `-H:-SpawnIsolates` | **8.86 ns · 112.8M/s** | 121.65 ns · 8.2M/s | **112.8 ns** |
| native, base | 9.51 ns · 105.1M/s | 145.77 ns · 6.9M/s | 136.3 ns |

**This is the decomposition that was missing, and it inverts the story:**

- **Native is 1.40× FASTER than JIT at the graph** — 8.86 ns against 12.42. AOT dispatch is not the
  problem and never was.
- **Native is 1.79× SLOWER than JIT at the audit** — 112.8 ns against 62.9.

Every "AOT is slower" figure in this round was those two effects netted together, at a mix that happened
to favour JIT. The owner's ~12 ns target for a light 30-node graph is met on both: **12.42 JIT, 8.86
native.**

### 15.1 The owner's hypothesis, and the heavy-node result that motivates it

The heavy-node arm (§14) came out at native 1.07× JIT, against 1.60× on light nodes — i.e. adding
arithmetic *helped* AOT relatively. The owner's reading is that this is backwards if AOT were simply
worse at code, and that **the audit is interfering with inlining**, which shows up most when the graph
itself is small.

The decomposition above is consistent with that: the audit penalty is roughly constant in absolute terms,
so it dominates a light graph and is diluted by a heavy one.

`-H:PriorityForceInline=<Processor>.*` forces **every method of the processor class** to inline into its
caller. In an audited processor those methods contain the audit dispatch, so the forced-inline body is
substantially larger — and round 62 established that the inlining budget governs which regime a build
lands in. Forcing a bigger body could push something else out.

### 15.2 Predictions

| # | Prediction | Basis |
|---|---|---|
| W1 | removing the inline directive from the **audited** build changes it by **less than 5 ns** | §10.6 measured the directive at ~0 on an audited path |
| W2 | if the owner's hypothesis holds instead, removing it **helps by more than 5 ns** | a smaller forced body leaves budget for the audit chain |
| W3 | removing the directive from the **baseline** build **hurts badly, > 3×** | this is the dispatch regime, where the directive is worth 3.5× |
| W4 | a **method-level** `PriorityForceInline` pattern is still rejected or ignored on 25.0.4 | the performance page records that naming individual methods does not work; the owner wants this to change |

W1 and W2 are mutually exclusive by design — this is the test that decides between them.

## 16. The baseline drift — the harness was the variable

The owner asked whether the new baselines matched what was recorded for this shape earlier. They did
not: **9.7 ns today against 3.41 ns recorded**. Four independent profile-and-build cycles all landed at
9.64–9.79, so it was not the build lottery — every build agreed.

The difference was **the harness**. `BenchPlain` constructs the processor in `main` and passes it into
the loop method; the older `BenchTail_*` constructed it **inside** the method running the loop. That is
the runtime-shape rule already on the performance-page checklist — *"processor constructed inside the
method that runs the event loop, and never escapes it"* — and I broke it while writing a cleaner harness.

| 30-node converging graph, no audit | native ns | Mmsg/s |
|---|---:|---:|
| processor **local** to the loop method (build 1) | **2.263** | 442 |
| processor **local** to the loop method (build 2) | **2.265** | 442 |
| processor **escapes** into the loop method | 9.790 | 102 |
| JIT, either shape | 12.418 | 81 |

**A 4.3× penalty, from where a variable is declared.** And two independent builds of the local shape
agree to 0.002 ns, so it is not a lottery effect either.

Two consequences:

1. **Native AOT is 5.5× faster than JIT on this graph** — 2.26 ns against 12.42 — once the shape is
   right. Every native-vs-JIT figure in §§8–15 was measured with the processor escaping, which cost the
   native arm 7.5 ns and the JIT arm nothing.
2. **The audited arms are measured with the same fault** and are being re-run.

### 16.1 Why this one was invisible

Every recorded input matched: same profile, same PGO, same GC, same flags, same graph, same machine.
The build log cannot show it and the binary index as first written could not either — it indexes how a
binary was **built**, not what was **built**. The harness is an input, and it was the only unversioned
one.

`HarnessVersion` now stamps a version onto every RESULT line (`harness=h3`), with a changelog naming
what each version altered and why it could move a number:

```
h3 - processor constructed inside the loop method and never escaping; 4.3x on native, 0 on JIT
h2 - refuses to report unless it can prove what it measured
h1 - original; -D after the main class silently ignored, arms not interleaved
```

Five silent harness faults in one round — a swallowed system property, a mismatched arm, un-interleaved
runs, a missing build-classpath entry, and now an escaping local — all produced plausible numbers and
wrong conclusions. **The failure mode of this work is not a wrong measurement, it is a right measurement
of the wrong thing.**

## 17. Validating the baseline actually does work — and finding the real confound

Two owner challenges: *"the 12 ns and 2 ns seem fast but possible — check that"*, and *"inspect the code
to make sure the auditor is the only difference between the two generated processors, then use the audit
log"*. Both were right, and the second one invalidated the audit-cost figure.

### 17.1 Does the graph do work? — ask the auditor, do not infer it

The first attempt estimated nodes-per-event by dividing the audit record size by the entry width. That
is inference. **Turning tracing on makes the processor state it as fact**, which is what the auditor is
for:

```
---- event E0 invoked 13 nodes ----
        - r0:   { method: on,   v: 3.0}
        - c0_0: { method: calc, v: 3.6500000000000004}
        - c0_1: { method: calc, v: 4.3325000000000005}
        - c0_2: { method: calc, v: 5.049125000000001}
        ... c0_3 c0_4 c0_5 t0 t1 t2 t3 t4 ...
        - t5:   { method: calc, v: 11.175630148106741, n: 1}

---- event E1 invoked 10 nodes ----
        - r1: … c1_0 c1_1 c1_2 t0 t1 t2 t3 t4 t5
```

Every node is named, in dispatch order, with the value it computed — and the values chain correctly:
`3.0 × 1.05 + 0.5 = 3.65`, `3.65 × 1.05 + 0.5 = 4.3325`. **10–13 nodes fire per event and each performs
its arithmetic.** Nothing is eliminated.

A depth-scaling control agrees: chains of 3/6/12/24 measure 12.13 / 16.07 / 25.47 / 48.05 ns on JIT —
linear in depth — and the tail checksum differs at every depth (110.3 / 137.3 / 204.8 / 416.4), which it
could not if intermediate nodes were dead.

### 17.2 The confound — the auditor was NOT the only difference

The "audit cost" figures compared a `LOW_LATENCY_AUDIT` processor against a `LOWEST_LATENCY` baseline.
Diffing the generated sources:

| | fair baseline | audited | |
|---|---:|---:|---|
| `isDirty_` | 0 → **172** | 172 | `LOWEST_LATENCY` turns dirty filtering **off** |
| `eventLogger` | 0 | 19 | the intended difference |

**172 against zero.** The delta being called "audit" was audit **plus conditional propagation**. A fair
baseline uses the *same* profile and simply installs no auditor; validated by regenerating both and
confirming every other feature count matches exactly.

### 17.3 Results — the decomposition, with the auditor as the only variable

30-node converging graph, every node logs, harness h3, min of 6 interleaved, idle machine.

| arm | JIT ns | native ns |
|---|---:|---:|
| `LOWEST_LATENCY`, no auditors | 11.84 | **2.13** |
| `LOW_LATENCY_AUDIT`, **no auditor installed** | 29.01 | 25.47 |
| `LOW_LATENCY_AUDIT` + auditor + binary record | 82.02 | 119.34 |
| **profile cost — dirty filtering et al** | **17.18** | **23.33** |
| **true audit cost** | **53.00** | **93.88** |
| per audit entry (11.75/event) | 4.51 | 7.99 |

**Previously reported as audit cost: 70.2 JIT / 117.2 native. It is 53.0 / 93.9.** The rest was dirty
filtering, and calling it audit overstated the audit by 32% on JIT and 25% on native.

### 17.4 The finding that matters more than the correction

**Dirty filtering takes native from 2.13 ns to 25.47 — an 11.9× regression. JIT goes 11.84 → 29.01, only
2.5×.**

So conditional propagation destroys AOT's dispatch advantage in exactly the way the audit does, and for
the same reason: both put state on the processor that has to survive the call, which defeats the escape
analysis that lets the node objects be scalar-replaced. Native's 5.5× lead over JIT survives neither.

That reframes the toolchain choice. **AOT is not "worse at auditing" — it is worse at anything that
stops the processor dissolving.** Dirty filtering, an audit record, and a processor that escapes its
loop method are three instances of one mechanism, and each costs native far more than JIT:

| what stops the processor dissolving | native | JIT |
|---|---:|---:|
| processor escapes the loop method (§16) | 4.3× | 1.0× |
| dirty filtering on | **11.9×** | 2.5× |
| audit record built per event | +93.9 ns | +53.0 ns |

**For a native deployment `setSupportDirtyFiltering(false)` is worth more than every compiler flag in
this round combined** — if the graph does not need conditional propagation. `LOW_LATENCY_AUDIT` will not
set it, deliberately: it changes what the graph computes, and that is the author's call. But the cost is
now measured and recorded in the profile's own javadoc so the choice can be made knowingly.

## 18. What the guards actually buy on this graph — nothing

The owner's reading of §17: 30 guards cost real time, probably in branch behaviour, and the design
choice is therefore *guards to skip occasional heavy work* versus *no guards, run the whole graph, gate
at a terminal operation*. That is the right frame, and the auditor settles which side this graph is on.

**Same trace, guards on and guards off:**

| event | guards ON (`isDirty_` × 172) | guards OFF (`isDirty_` × 0) |
|---|---:|---:|
| E0 | 13 nodes | **13 nodes** |
| E1 | 10 nodes | **10 nodes** |
| E2 | 11 nodes | **11 nodes** |

**Identical.** Dirty filtering skips nothing here, because each event reaches its chain by *topology* —
the generated `onEvent(E0)` only calls the `r0` chain in the first place. Guards only decide anything at
a **join**, where a node has several parents and only some are dirty; this graph's joins (`j1`, `j2`,
the shared tail) are always reached.

So the 17.2 ns (JIT) / 23.3 ns (native) measured in §17.3 is the **pure cost of guards that never save
anything** — the worst case for conditional propagation, and a fair measure of what a guard costs:

```
~1.4 ns per guarded node on JIT      ~1.9 ns per guarded node on native
```

### 18.1 The trade, stated so an author can decide

A guard pays when **P(skip) × cost(node) > cost(guard)**. With the numbers from this round:

| node kind | node cost | break-even skip rate, JIT | verdict |
|---|---:|---:|---|
| light (one FMA) | ~0.2–1.5 ns | **> 100%** | a guard can never pay |
| heavy (24-iteration Horner) | ~34 ns | **~4%** | a guard pays if it skips even occasionally |

So the owner's two designs are both right, for different graphs:

- **Light nodes → no guards, run the whole wave, gate at a terminal operation.**
  `setSupportDirtyFiltering(false)` and decide once at the end. On this graph that is worth
  **2.5× on JIT and 11.9× on native**.
- **Heavy nodes behind a join → keep the guards.** Skipping 34 ns of work for 1.4 ns of branch is a good
  trade at any realistic skip rate.

The asymmetry between toolchains is the part worth remembering: **guards cost native 11.9× and JIT
2.5×**, because the guard state lives on the processor and stops it dissolving (§17.4). An AOT
deployment should reach for the terminal-gate design much sooner than a JIT one.

### 18.2 What this does not show

Nothing here measures a graph where guards **do** skip work — every arm invoked identical nodes. The
break-even table above is arithmetic on separately-measured costs, not a measurement of a skipping
graph. **A graph with a heavy node behind a sometimes-cold join is the experiment that would confirm
it**, and it has not been run.

## 19. Isolating where the native audit cost actually is

With guards off in both arms and the harness held at h3, the audit cost on the 30-node converging graph
is **68.8 ns on JIT** (89.10 − 20.33) and **124.8 ns on native** (142.83 − 18.02). Native pays **1.8×**
for the same audit trail. §17–18 established it is not guards and not the graph. This isolates it.

### 19.1 The decomposition, and the arm that was missing

Per event the audit path does four things:

1. `clock.eventReceived` — one wall-clock read
2. `eventLogger.eventReceived` → `logRecord.triggerObject` — the record header
3. **× 11.75**: `auditLog.info(k,v)` → `EventLogger.log` → level check → `logRecord.addRecord(...)`
4. `processingComplete` → `terminateRecord` → `sink.processLogRecord`

Steps 1, 2 and 4 are **auditor dispatch**. Step 3 splits into a **call chain** (the `EventLogger`
indirection and level check, 11.75 times) and **record building** (what `addRecord` writes).

Nothing so far separates the call chain from the record. A **no-op `LogRecord`** does exactly that: the
auditor runs, every node calls `auditLog.info`, every call reaches `addRecord` — and `addRecord` writes
nothing. Its cost is the audit machinery with the record removed.

```
A  fair baseline      no auditor at all
B  record=noop        auditor + full call chain + a record that does nothing   <- the new arm
C  record=binary      auditor + call chain + bits written
D  record=text        auditor + call chain + characters written
```

`B − A` is dispatch and call chain. `C − B` is record building. **If native's penalty is in `C − B` it
is an implementation problem in the encoder; if it is in `B − A` it is the shape of the generated
dispatch, and no encoder change will help.**

### 19.2 Predictions

| # | Prediction | Basis |
|---|---|---|
| X1 | the no-op record arm costs **< 20 ns over baseline on JIT** | ~12 level checks and virtual calls, a few ns each |
| X2 | `B − A` is **larger on native than JIT** | the `LogRecord` receiver is not statically provable, and native cannot speculate |
| X3 | **record building (`C − B`) is the majority** of the audit cost on both toolchains | §12 measured 3.4 ns/entry of pure encoding against a 4.5 ns/entry total |
| X4 | native's 1.8× penalty is concentrated in **`C − B`, not `B − A`** | the encoder writes to a byte array through a long field-store chain, which is where §11.3 already found AOT weak |

**X4 is the one that decides the next move.** If it holds, the encoder is fixable. If instead the
penalty is in `B − A`, the cost is in the generated dispatch shape and the fix belongs in the compiler.

**Harness constant:** all four arms are served by **one binary** with the record selected by a runtime
property, so the image, the profile, the PGO and the flags are identical across B/C/D and only the
record class varies. A is necessarily a different processor — that is the declared variable, and
`compare-arms.sh` is told so.

### 19.3 Results — the decomposition, both toolchains

harness h4, one binary per toolchain serving all three record modes, native min over **two builds** ×
5 reps (the lottery is ±8 ns here).

| arm | JIT ns | native ns | native ÷ JIT |
|---|---:|---:|---:|
| **A** fair baseline, no auditor | 20.07 | **18.08** | 0.90 — *native faster* |
| **B** auditor + no-op record | 30.99 | 45.67 | 1.47 |
| **C** auditor + binary record | 88.93 | 144.58 | 1.63 |
| **D** auditor + text record | 406.51 | 794.72 | 1.96 |
| | | | |
| **B − A** dispatch + call chain | 10.93 (0.93/entry) | **27.58 (2.35/entry)** | **2.52×** |
| **C − B** binary record building | 57.93 (4.93/entry) | **98.92 (8.42/entry)** | **1.71×** |
| **D − B** text record building | 375.52 (31.96/entry) | 749.05 (63.75/entry) | 1.99× |
| total audit (binary) | 68.86 | 126.50 | 1.84× |

| # | Predicted | Measured | |
|---|---|---|---|
| X1 | no-op record < 20 ns over baseline on JIT | **10.93** | ✅ |
| X2 | `B − A` larger on native | **27.58 vs 10.93 — 2.52×**, the worst ratio in the table | ✅ |
| X3 | record building is the majority on both | **84% JIT, 78% native** | ✅ |
| X4 | native's penalty concentrated in `C − B`, not `B − A` | **half right** | ➗ |

**X4 is the interesting miss.** In absolute nanoseconds it holds — of native's 57.6 ns extra, **41 ns is
record building and 17 ns is dispatch**. But *proportionally* the dispatch is the worse of the two
(2.52× against 1.71×). So the answer to "shape or implementation" is **both, and they need different
fixes**:

- **Shape** — the `EventLogger` → `LogRecord.addRecord` chain costs native 2.35 ns per entry against
  JIT's 0.93. That is a virtual call to a receiver native cannot prove and cannot speculate on; JIT
  profiles it and inlines. Fixing it means changing what the generator emits, not the encoder.
- **Implementation** — the encoder costs 8.42 ns per entry on native against 4.93 on JIT, for writing
  13 bytes. That is fixable in the encoder, and §19.4 tries.

**And the no-op arm proves the machinery is not free even with nothing to write:** 27.58 ns/event on
native — 1.5× the entire no-audit graph — before a single byte is recorded.

### 19.4 Trying to remove it — the encoder writes a long as eight bounds-checked stores

`BinaryLogRecord.i64` writes a `long` one byte at a time, each with an implicit array bounds check:

```java
buf[pos++] = (byte) (v >>> 56);  buf[pos++] = (byte) (v >>> 48);  ... eight of these
```

A double entry is `u16 u16 u8 i64` = **13 bytes and 13 bounds checks**. JIT unrolls and folds most of
that; native evidently does not, which is exactly the shape of a 1.71× gap on pure byte-writing.

`VarHandle byteArrayViewVarHandle(long[].class, BIG_ENDIAN)` writes the same eight bytes as **one
unaligned store with one bounds check**.

| # | Prediction | Basis |
|---|---|---|
| Y1 | the VarHandle store cuts native record building by **> 20%** | 13 bounds checks → 4 per entry |
| Y2 | it helps **native more than JIT in relative terms** | JIT already folds the byte loop; native does not |
| Y3 | it does **not** close the whole 41 ns — the id lookups and the call chain remain | only the value store changes |

### 19.5 Results — one fix landed, one blocked by the API

**Fix 1 — the value stores. Landed, 25.9% off native.**

`i64` wrote a `long` as eight bounds-checked byte stores. `VarHandle byteArrayViewVarHandle` writes it
as one. Same binary, store path selected by a runtime property, so exactly one variable moves:

| toolchain | bytewise | varhandle | delta |
|---|---:|---:|---:|
| JIT | 77.08 | 78.44 | +1.8% |
| **native** | 165.24 | **122.39** | **−25.9%** |

| # | Predicted | Measured | |
|---|---|---|---|
| Y1 | > 20% off native record building | **25.9%** | ✅ |
| Y2 | helps native more than JIT | native −42.9 ns, JIT +1.4 ns | ✅ |
| Y3 | does not close the whole gap | native/JIT went 2.14× → 1.56× | ✅ |

**JIT was already folding the byte loop; native was not.** That is a pure implementation win and it is
the single largest improvement found in this round.

**Fix 2 — the name lookups. Attempted, and the attempt failed usefully.**

An `intern=none` ceiling arm — write a constant id, never resolve a name — showed interning costs
**26.2 ns/event on JIT and 27.5 on native**, about a third of the whole audit record.

*(That contradicts §7.3's Y5, which found interning "never on the critical path". Y5 was measured on the
tail graph at **2 entries per event**; this is 11.75. The finding was right for its shape and wrong as a
general claim — the third time in this round a result failed to survive a change of regime.)*

So an open-addressed identity table replaced the one-slot cache. It recovered **1.89 of 26.17 ns — 7%**:

| intern strategy | JIT ns |
|---|---:|
| `map` — one-slot cache + `IdentityHashMap` fallback | 76.01 |
| `table` — open-addressed, identity-probed | 74.12 |
| `none` — no lookup at all (the ceiling) | **49.84** |

**The cost is not the map. It is resolving a name at all** — ~1.1 ns per lookup × 23.5 lookups per
event (a node name and a key name for each of 11.75 entries). Any lookup pays it; a better lookup does
not help.

### 19.6 Where the remaining cost is, and who can remove it

| term | JIT ns | native ns | fixable by |
|---|---:|---:|---|
| graph | 20.07 | 18.08 | — |
| audit dispatch + `EventLogger` call chain | 10.93 | **27.58** | the **generator** — a virtual call native cannot prove |
| name resolution, 23.5 lookups/event | 26.17 | 27.54 | the **API** — see below |
| value encoding, after the VarHandle fix | ~31.8 | ~48.9 | the encoder — partly done |

**Name resolution cannot be fixed in the encoder, because the API hands it a `String`.**

```java
EventLogger.log(String key, double value) → LogRecord.addRecord(String sourceId, String key, double)
```

Both names are **compile-time constants in generated source**. The generator knows them, and knows every
one of them, at build time. A binary-first record has to turn each into a small integer, and today it
must do so at runtime, 23.5 times per event, forever — to recover information the generator already had
and discarded.

**The fix is to assign ids at generation time and pass the id.** That is a change to the audit API and
the generated code, not to any encoder, and it is worth the full **26–28 ns/event on both toolchains** —
more than every compiler flag in this round put together. It also removes the dictionary problem from
[`spec-binary-audit-encoding.md`](../../../specs/spec-binary-audit-encoding.md) §6.3: the id table
becomes a build artifact emitted next to the processor rather than something discovered at runtime and
published on the wire.

**Recorded as the next change to specify, not as a result** — nothing here implements it.

### 19.7 Where the numbers stand

| | before this section | after the VarHandle fix | if ids were passed (projected) |
|---|---:|---:|---:|
| JIT | 88.93 | ~78.4 | ~50 |
| **native** | **165.24** | **122.39** | **~96** |

The projected column is the measured `intern=none` ceiling, not a guess about a fix that has not been
built. It is what the record would cost if name resolution were free.

## 20. Solved — the fix is in `EventLogger`, and node code does not change

§19.5 concluded name resolution "cannot be fixed in the encoder, because the API hands it a `String`".
That was right about the encoder and wrong about the difficulty. The owner asked the follow-up that
matters: *how does the implementation change, and is client code the same?*

**Client code is identical.** `auditLog.info("v", v)` is untouched, in every node, unchanged.

### 20.1 Why my prototype's cache missed — the structural error

`EventLogger` is constructed **per node** and holds `private final String logSourceId`. The node name is
therefore a constant for that logger's whole life.

My cache lived on the **shared `LogRecord`**, so twelve nodes contended for one slot. That is the
measured 50% hit rate from §7.3: the key always hit (every node logs `"v"`), the node name always missed
(twelve names rotating through one slot). Making the cache cleverer could not fix a cache in the wrong
place — which is why the open-addressed table recovered only 7%.

### 20.2 The change

**`LogRecord`** gains an optional hook and id-carrying overloads:

```java
public int internName(String name) { return NO_ID; }        // default: "I encode names directly"
public void addRecord(int sourceRef, int keyRef, double value) { … }
```

**`EventLogger`** resolves once and reuses:

```java
private int sourceRef = LogRecord.NO_ID;   // the node name — resolved once, it is final
private final String[] keyNames = new String[4];   // a node logs a small fixed set of keys
private final int[] keyRefs = new int[4];          // identity compare, no hashing, no map

public EventLogger log(String key, double value, LogLevel level) {
    if (this.logLevel.level >= level.level) {
        if (useIds()) { logrecord.addRecord(sourceRef, keyRef(key), value); }
        else          { logrecord.addRecord(logSourceId, key, value); }   // unchanged path
    }
    return this;
}
```

**Backward compatible by construction.** A record that does not override `internName` returns `NO_ID`,
`useIds()` is false, and it takes exactly the path it takes today. The text `LogRecord` is untouched.

**No generator change was needed.** §19.6 proposed build-time ids emitted by the compiler; that turns
out to be unnecessary for the bulk of the win, because the information is already sitting in a final
field on a per-node object. Build-time ids remain interesting for the *reader* (a dictionary known
before the process starts) but they are no longer on the performance path.

### 20.3 Results

Same binary, path selected by a runtime property, so exactly one variable moves. Node source identical
throughout; record bytes (180.8) and graph checksum verified equal on every arm.

| | JIT | native |
|---|---:|---:|
| fair baseline, no auditor | 19.42 | 17.64 |
| **String path — as shipped today** | 74.35 | **167.82** |
| **id path — `EventLogger` resolves once** | **61.06** | **82.21** |
| no interning at all (the ceiling) | 59.82 | 82.90 |
| | | |
| interning cost recovered | **91%** | **101%** (at the ceiling) |
| audit cost | 54.93 → **41.65** (−24%) | 150.18 → **64.56** (**−57%**) |

### 20.4 The whole arc, native, one graph

| | ns | Mmsg/s |
|---|---:|---:|
| where §19 started — bytewise stores, String path | 165.24 | 6.1 |
| + `VarHandle` value stores (§19.4) | 122.39 | 8.2 |
| **+ id path (§20)** | **82.21** | **12.2** |

**2.0× on native, and the 10M msg/sec target is now met on both toolchains for the fully-audited
converging graph** — 16.4M JIT, 12.2M native, zero allocation, 181 bytes per record, every node on the
path logging.

Native's remaining audit cost is 64.56 ns against JIT's 41.65 — **1.55×**, down from 1.84×. What is
left is the dispatch shape measured in §19.3 (2.52× on the `EventLogger` → `LogRecord` virtual call),
which is a generator concern, not an encoder one.

### 20.5 Tests

`EventLoggerIdPathTest` pins four things, and the first is the point of the change:

- a name is resolved **once per logger, not once per event** — 100 events produce 100 entries and
  exactly 2 `internName` calls;
- the id path records the same information as the String path;
- more distinct keys than the logger has cache slots still resolve correctly;
- a record that declines ids keeps the String path untouched.

## 21. Wiring it up — what was actually shipped, and one thing that could not be

The owner asked whether the binary recorder is stored in the profile. It was not: `BinaryLogRecord`
lived only in the analyser's bench kit, and `addLowLatencyEventLog` installed a text record regardless —
so **none of the measured binary numbers were reachable through a supported API**.

### 21.1 Now shipped

- `BinaryLogRecord` is in **core**, `com.telamin.fluxtion.runtime.audit`.
- The format is a **build input**:
  `addLowLatencyEventLog(LogLevel.INFO, AuditRecordFormat.BINARY)`.
- `EventLogManager` builds it at `init()`. The runtime `EventLogControlEvent` swap still works and is
  still how you change format on a *running* processor.
- **`TEXT` stays the default**, and not for performance reasons: nothing can read the binary form yet.
  A test pins the default so it cannot drift.

### 21.2 Repeatability

Independent re-measure an hour after the recorded figures, same binaries:

| | recorded | re-measured | delta |
|---|---:|---:|---:|
| JIT id path | 61.06 | 64.96 | +3.90 |
| native id path | 82.21 | 79.45 | −2.77 |

Both inside run-to-run variance. The result reproduces.

### 21.3 What could not ship — and it is not a straightforward loss

`fluxtion-runtime` targets **Java 8**, enforced mechanically by animal-sniffer (deliberately: the
browser bundle and agrona compatibility depend on it). So the `VarHandle` byte-array views worth 25.9%
on native **cannot go into core**, and it writes the byte loop instead.

Measured with the id path on in both arms and only the store path varying:

| | bytewise | VarHandle | |
|---|---:|---:|---|
| JIT | **54.59** | 63.19 | the byte loop is **8.6 ns faster** |
| native | 115.80 | **81.34** | VarHandle is **34.5 ns faster** |

**HotSpot already folds the byte loop and then pays for the `VarHandle` indirection; native-image does
not fold it.** So core ships the better choice for a JIT deployment and the worse one for native, and
the 34 ns is available only through a multi-release jar or a separate module. Recorded in the javadoc on
`i64()` as a known gap.

That also revises §19.4: "VarHandle stores are worth 25.9% on native" is true, and incomplete — they
cost JIT 8.6 ns, which the first measurement (+1.8%, inside noise) did not resolve because the id path
was not yet in place to shrink everything else.

### 21.4 Where the profile now stands, measured

| record, every node on the path logging | JIT | native | bytes |
|---|---:|---:|---:|
| `TEXT` (default) | 403.0 ns · 2.5M/s | 698.6 ns · 1.4M/s | 548 |
| **`BINARY`** | **54.6 ns · 18.3M/s** | **115.8 ns · 8.6M/s** | **181** |

**Node code is identical either way.**

## 22. The two ways past the Java 8 wall — and why generating the writer wins

`VarHandle` is used in exactly three places: `u16`, `i32`, `i64`, the value-store primitives. A double
entry calls four of them (`headById` is two `u16`, then `u8`, then `i64`) to write 13 bytes.

The owner's two options:

**Option 1 — a multi-release jar or a separate module.** Lets core keep Java 8 and still offer the
`VarHandle` path. It recovers the 34.5 ns on native, and nothing else.

**Option 2 — generate the writer into the event processor.** The generated processor is compiled by the
*user's* toolchain, not by `fluxtion-runtime`'s Java 8 build. So it can use `VarHandle` freely — and
that is the smallest of three things it fixes.

### 22.1 What each option can reach, against the measured decomposition

Native audit cost is 64.56 ns (id path, `VarHandle`), made of three terms this round measured
separately:

| term | native ns | option 1 fixes it? | option 2 fixes it? |
|---|---:|:---:|:---:|
| dispatch — `EventLogger` → `LogRecord` virtual call (§19.3) | 27.58 | ✗ | **✓** inlined at the call site |
| name resolution (§20) | ~0 | already done | **✓** ids become literals |
| value encoding — 13 bytes through 4 primitive calls | ~37 | **✓** | **✓** and the offsets fold |

**Option 1 addresses one of three terms. Option 2 addresses all three**, because a generator knows the
node name, the key and the record layout at build time. `auditLog.info("v", v)` can become a handful of
stores at a constant offset with no call, no lookup and no virtual dispatch.

### 22.2 Predictions, before measuring the ceiling

A hand-written stand-in for what the generator would emit — nodes writing bits directly, ids as
constants — bounds what option 2 could reach. **It is a ceiling, not an implementation**: it has no
level check, no record swap, no sink contract.

| # | Prediction | Basis |
|---|---|---|
| G1 | the inline ceiling is **below 30 ns/event of audit cost on native** | 64.56 minus most of dispatch and most of encoding |
| G2 | it beats option 1's best (native 81.3 total) by **more than 20 ns** | option 1 leaves the 27.58 ns dispatch term untouched |
| G3 | the JIT gap narrows much less | JIT's dispatch term is 10.93, not 27.58, so there is less to remove |
| G4 | **native's remaining 1.55× penalty over JIT largely closes** | if what is left is straight-line stores, it is the arithmetic AOT is already good at (§14) |

G4 is the one that matters for the toolchain recommendation.

### 22.3 Results — generating the writer is worth 4.6× on native audit cost

`DagNodesInline` stands in for what a generator could emit: each node writes its entry as bits at a
constant offset with its node id and key id as **literals** — no `EventLogger` call, no name lookup, no
virtual dispatch. Invariant asserted: the arm refuses to report unless bytes were actually written.

| arm | JIT ns | native ns |
|---|---:|---:|
| baseline, no audit | 20.76 | 17.77 |
| binary record through the API (today) | 54.37 | 83.47 |
| **inline ceiling — generated writer** | **30.04** | **32.02** |
| | | |
| **audit cost today** | 33.62 | **65.70** (native 1.95×) |
| **audit cost, inline** | **9.29** | **14.24** (native 1.53×) |

| # | Predicted | Measured | |
|---|---|---|---|
| G1 | inline ceiling below 30 ns audit cost on native | **14.24** | ✅ |
| G2 | beats option 1's best by > 20 ns | option 1 tops out at 81.3 total; inline is **32.02** — 49 ns better | ✅ |
| G3 | JIT narrows less than native | JIT 3.6×, native 4.6× | ✅ |
| G4 | native's penalty over JIT **largely closes** | 1.95× → **1.53×** — improved, not closed | ❌ |

**G4 is the honest miss.** Removing the audit machinery does not remove native's relative disadvantage;
it shrinks the term the disadvantage applies to. What is left at 14.24 vs 9.29 ns is 11.75 straight-line
13-byte stores, and native is still 1.5× slower at those. §14 found AOT good at *arithmetic*; this says
it is not equally good at *stores*.

### 22.4 The recommendation

| | option 1 — multi-release jar | option 2 — generate the writer |
|---|---|---|
| terms addressed | 1 of 3 (value encoding) | **3 of 3** |
| native audit cost | 65.70 → ~31 (est.) | **65.70 → 14.24 (measured ceiling)** |
| native total | ~81 | **32.02 — 31.2M msg/sec** |
| JIT | *costs* 8.6 ns (§21.3) | 33.62 → 9.29 |
| build complexity | a multi-release jar in core | generator work; no core Java-version change at all |
| side effects | none | the `VarHandle` problem disappears — generated code is compiled by the **user's** toolchain, not by `fluxtion-runtime`'s Java 8 build |

**Option 2, and the Java 8 wall stops being a constraint rather than being worked around.**

**What the ceiling is not.** `DagNodesInline` has no level check, no record swap, no sink contract, no
header or terminator. Real generated code adds some of that back, so **14.24 ns is a floor, not a
promise**. The comparison against option 1 survives that caveat easily — the gap is 49 ns — but the
absolute number should not be quoted as an achievable result.

**And it does not remove the API.** `EventLogger` stays for hand-written nodes, dynamic keys and any
node the generator did not compile. The generated path is an optimisation of the common case, not a
replacement for the seam.

## 23. Correction — the inline ceiling assumed information the generator does not have

"Can that be easily generated in place in the event processor?" Checking rather than assuming, and the
answer forces a correction to §7B.

### 23.1 Three facts

1. **The property key is a literal inside the node's own method body**: `auditLog.info("v", v)` lives in
   `DagNodesConverging.R0.on(E0)`, compiled separately from the processor.
2. **The generator does not parse method bodies.** Zero ASM usages in `fluxtion-generator-core`. It sees
   a node as a class with annotations and fields; it never reads the bytecode of `on(E0)`.
3. **Node names, by contrast, ARE known** — the generator emits `auditor.nodeRegistered(c1_0, "c1_0")`
   as literals.

**So the generator cannot know that node `t5` logs key `"v"`.** §7B.3's normative "assigning node and
key ids as literals" is not implementable as written for keys, and §22's 14.24 ns ceiling was reached by
editing the node source — which is exactly what a generator cannot do.

**That withdraws the ceiling as an achievable target.** The comparison against the multi-release jar
does not survive unqualified either; what survives is below.

### 23.2 What IS generatable, and it is not nothing

The generator controls **which `EventLogger` each node receives** — `EventLogManager.nodeRegistered`
constructs it, and that is core code a factory hook can open. So a generator can emit, per node:

```java
final class EventLogger_c1_0 extends EventLogger {          // compiled by the USER's toolchain
    @Override public EventLogger log(String key, double v, LogLevel level) {
        if (canLog(level)) {
            // node id is a literal; key resolved once as today; VarHandle is legal HERE
            record.writeDouble(17, keyRef(key), v);
        }
        return this;
    }
}
```

That reaches:

| term | native ns | reachable by a generated `EventLogger`? |
|---|---:|---|
| value encoding (`VarHandle` stores) | ~34.5 | **yes** — generated code is not bound by core's Java 8 |
| `LogRecord.addRecord` virtual call | part of 27.58 | **yes** — the writer can target a concrete record |
| `auditLog.info` virtual call on `EventLogger` | rest of 27.58 | **no** — `EventLogNode.auditLog` is typed `EventLogger`, and N generated subclasses make it polymorphic. §9 measured provability at ~2 ns, so this is small |
| name resolution | ~0 | already fixed in §20; the generator cannot improve on once-per-node |

**So the honest estimate is the `VarHandle` term plus part of the dispatch term — call it 35–45 ns of
the 65.70 ns native audit cost, not the 51 ns the ceiling suggested.** That is still the largest
remaining win, and it still makes the multi-release jar unnecessary, because the writer moves out of
core either way.

### 23.3 What would reach the full ceiling

Only something that sees the node's method body:

- **an annotation processor** running on the user's node sources, which sees `auditLog.info("v", v)`
  and can rewrite or index it;
- **build-time bytecode transformation**, which the project has no precedent for and which would
  undermine the "read the generated source" property the whole toolchain leans on;
- **an API change** that puts the key where the generator can see it — e.g. keys declared as fields or
  in an annotation rather than passed as literals at the call site. That changes node code, which is
  the thing §20 was careful not to do.

None is proposed here. Recorded so the ceiling is not mistaken for a plan.

### 23.4 Answering the question directly

**Easily? No.** A generated `EventLogger` subclass per node is a real, contained change — a factory hook
in `EventLogManager`, and emission in the generator — and it is worth an estimated 35–45 ns of the
65.70. **The remaining ~20 ns needs the generator to see inside node method bodies, which it does not
do and has no machinery for.**

## 24. The template is the right place — and it reaches further than §23 allowed

The owner's point: `SimpleEventProcessorModel` holds the full model — every node, every name — and
`javaTemplate.vsl` can carry an audit-generation section that fires when the `EventLogManager` auditor
is present. That is correct, and §23's estimate was too pessimistic for a reason I had missed.

### 24.1 What the model already has

| needed | available today? |
|---|---|
| every node name | **yes** — emitted as literals in `auditor.nodeRegistered(c1_0, "c1_0")` |
| which nodes can log | **derivable** — the model holds live node instances, so the same `node instanceof EventLogSource` test `EventLogManager.nodeRegistered` does at runtime works at build time |
| whether an auditor is present | **yes** — `model.getNodeRegistrationListenerFields()`, already used to decide `auditEvent` emission |
| **which record class will be built** | **yes, since §21** — `addLowLatencyEventLog(level, BINARY)` makes the format a *build* input |
| the property keys | **no** — literals inside node method bodies, and the generator has no bytecode analysis (§23) |

### 24.2 The thing §23 got wrong

§23 listed the `LogRecord.addRecord` virtual call as only partly reachable. **It is fully reachable**,
because §21 moved the record format from a runtime swap to a build-time choice. The generator therefore
knows the concrete record class and can emit a writer that calls it directly — no virtual dispatch to
the `LogRecord` base at all.

Revised, and marked as arithmetic on measured terms rather than a measurement:

| term | native ns | reachable from the template? |
|---|---:|---|
| value encoding — `VarHandle` stores | ~34.5 | **yes** — generated code is compiled by the user's toolchain, not core's Java 8 build |
| `LogRecord.addRecord` virtual call | most of 27.58 | **yes** — the record class is a build input since §21 |
| `auditLog.info` virtual call on `EventLogger` | remainder | **no** — `EventLogNode.auditLog` is typed `EventLogger`, so N generated subclasses stay polymorphic. §9 measured provability at **~2 ns** |
| name resolution | ~0 | already once-per-node (§20); the generator cannot improve on it |

**Revised estimate: 50–55 ns of the 65.70 ns**, against §23's 35–45. The measured ceiling was 51.5 ns
(65.70 → 14.24), so a template-generated writer gets most of the way there **without needing to see a
single node method body**.

### 24.3 The shape of the change

```java
// nested in the generated processor — compiled by the user's toolchain
private static final class AuditWriter_c1_0 extends EventLogger {
    private static final int NODE_ID = 17;                 // the model knows the name
    private final BinaryLogRecord rec;                     // CONCRETE — format is a build input
    @Override public EventLogger log(String key, double v, LogLevel lvl) {
        if (canLog(lvl)) { rec.writeDouble(NODE_ID, keyRef(key), v); }   // one direct call, VarHandle inside
        return this;
    }
}
```

Three pieces, each contained:

1. **template** — a slot before the closing brace for generated members, and a section that fires when
   the audit auditor is present;
2. **model/generator** — identify `EventLogSource` nodes and emit one writer class per logging node;
3. **core** — a factory hook on `EventLogManager`, which today calls `new EventLogger(logRecord, nodeName)`
   directly.

**Node code does not change.** Keys are still resolved through `internName`, once per logger.

### 24.4 What it still cannot do

The `auditLog.info` call site stays polymorphic, and the property key still cannot become a literal.
Both are small — ~2 ns and ~0 respectively after §20 — but they are the reason the full 14.24 ns ceiling
needs an annotation processor or an API change, and neither is proposed.

**Status: this is a design sketch on measured terms. Nothing in §24 has been built or measured**, and
the 50–55 ns is arithmetic, not a result.

## 25. The model must be complete — and §24's design pushed the wrong way

The owner's architectural constraint: **everything goes into the model, the template generates from it,
and the Java template must be replaceable with another target language.** §24's sketch does not respect
that, and the current codebase does not either.

### 25.1 Where the boundary actually sits today

| | today |
|---|---|
| templates that exist | **one Java template** (`template/base/javaTemplate.vsl`) plus test fixtures — no second target |
| Velocity control flow in it | **two directives**, one of them an `#if` on imports. It is slot-filling, not rendering |
| what the model hands it | **pre-rendered Java statements** — `eventAuditDispatch += String.format("%8s%s.eventReceived(typedEvent);%n", …)`, `nodeMemberAssignmentList.add("initialiseAuditor(" + name + ");")` |

**A C++ template fed this model would receive Java source.** The separation the constraint asks for is a
direction of travel, not the current state — and §24 would have added *more* Java-string-building to
`JavaSourceGenerator`, moving away from it.

### 25.2 The audit writer is a good place to start moving toward it

It is small, self-contained, and **new** — unlike the dispatch code, which is already thousands of lines
of Java strings that would have to be unpicked. So the audit writer can be built model-first without
first paying to migrate anything else.

**Model side — declarative, no target language anywhere in it:**

```
auditPlan:
  recordFormat : TEXT | BINARY
  entryLayout  : [nodeId:u16, keyId:u16, tag:u8, value:<by type>]
  writers:
    - nodeName: "c1_0"   nodeId: 17   valueTypes: [double]
    - nodeName: "t5"     nodeId: 23   valueTypes: [double, long]
```

Everything in that plan is already derivable: node names are emitted as literals today; logging nodes
are identified by `instanceof EventLogSource` against the live instances the model holds; the record
format is a build input since §21.

**Template side — the only place a language appears:**

```velocity
#foreach($w in ${MODEL.auditPlan.writers})
private static final class AuditWriter_${w.nodeName} extends EventLogger { … }
#end
```

A second target renders the same plan its own way and needs no generator change.

### 25.3 What this changes about the estimate

Nothing measured, and nothing about what is reachable — the same terms are addressed. It changes **where
the work lives**: in the model as data plus a template that renders it, rather than in
`JavaSourceGenerator` as more `String.format`. The 50–55 ns estimate from §24 stands, and remains
arithmetic on measured terms.

It does add a requirement §24 lacked: **the plan must carry the value types**, because a template that
renders `writeDouble` versus `writeLong` needs to know which, and the generator cannot read the node's
method body to find out. That is derivable from the `EventLogger.log` overloads a node *could* call —
which is all of them — so a complete plan either lists every type or the writer overrides every
overload. **The second is simpler and is what the ceiling measured.**

### 25.4 Honest scope

This is a **principle applied to one new feature**, not a migration. The existing dispatch, initialise,
teardown and event-handler sections still hand the template Java text, and nothing here changes that or
proposes to. Claiming the template is "replaceable" after this work would be false; claiming the audit
section is model-first would be true.

## 26. Two corrections from the owner — "replaceable" is achievable, and the compile trap is real

### 26.1 I was wrong that the template is not replaceable

§25 argued the Java template is not replaceable because the model hands it pre-rendered Java. That is
true of the **existing dispatch code** and I over-generalised it into a claim about the design.

The owner's point: **a C++ target would not specialise in the generator** — it would take the same
declarative plan and use metaprogramming, e.g. `template<int NodeId> struct AuditWriter`. The
specialisation happens in the *target's* compiler, not in `JavaSourceGenerator`. So a plan carrying
`{nodeName, nodeId, recordFormat, entryLayout}` is genuinely language-neutral, and "replaceable" is a
property of the **plan**, not of how many classes each target chooses to emit.

That also removes the objection §25.3 raised about value types. A target that uses metaprogramming does
not need the plan to enumerate `double` vs `long` per node — it overrides or instantiates for all of
them. **The plan stays smaller than §25 claimed it needed to be.**

### 26.2 The compile trap the owner predicted — confirmed in the code

> *"The one issue I found was compiling in process for multiple classes in a single file."*

`StringCompilation.compileWithProcessors`:

```java
final JavaByteObject byteObject = new JavaByteObject(className);   // ONE object, ONE name
JavaFileManager fileManager = createFileManager(standardFileManager, byteObject);
…
final ClassLoader inMemoryClassLoader = createClassLoader(byteObject);
return inMemoryClassLoader.loadClass(className);
```

**One `JavaByteObject` for one class name.** A nested class compiled from the same source is written to
that same object. The batch entry point is no better in the case that matters:

```java
for (String name : classNames) { outputs.put(name, new JavaByteObject(name)); }   // top-level only
…
JavaByteObject o = outputs.get(className);
return o != null ? o : fallbackOutput;      // nested classes → ONE SHARED fallback
```

`fallbackOutput` is a single object named `__fluxtion_lint_fallback`. Every nested class overwrites the
last, and none is retrievable by name. **So §24's "nested writer class per node" would break the
in-memory compile path**, exactly as predicted.

### 26.3 And the owner's second point is also right — the AOT path does not care

The performance work targets the AOT path, which sets `setWriteSourceToFile(true)`: the generator
**writes source**, the user's build compiles it, and nothing is loaded in process. The trap is confined
to the in-memory `EventProcessorFactory.compile()` path used by tests and interpreted mode.

But "confined to tests" is not "safe to ignore" — the in-memory path is how most of the suite runs.

### 26.4 The design refinement this forces, and it is an improvement

§24 proposed **one writer class per logging node**. That maximises exposure to the trap for no benefit,
because the per-node part — the node id — is already free after §20 (resolved once per logger).

The two terms actually worth generating are the `VarHandle` stores and the concrete record type, and
**neither is per-node**. So:

```java
// ONE generated logger class, not one per node
private static final class GeneratedAuditLogger extends EventLogger {
    private final int nodeId;              // constructor arg — already free since §20
    private final BinaryLogRecord rec;     // CONCRETE — the format is a build input since §21
    @Override public EventLogger log(String key, double v, LogLevel lvl) {
        if (canLog(lvl)) { rec.writeDouble(nodeId, keyRef(key), v); }
        return this;
    }
}
```

**One extra class instead of N**, capturing both large terms. Emitted as a **top-level class alongside
the processor**, the batch compile path already handles it — `classNames` and `sources` are lists.

### 26.5 What still needs fixing either way

`StringCompilation` should handle multiple classes per compilation unit properly: create a
`JavaByteObject` on demand for any class name rather than falling back to one shared object, and serve
all of them to the classloader. That is a latent defect independent of this work — **any** generated
nested class hits it today. Recorded as such.

## 27. How many generated classes, and should it be default?

### 27.1 The count is one per processor

§26 settled the shape: **one generated logger class, not one per node.** The per-node part is the node
id, and that is a constructor argument — free since §20, where `EventLogger` resolves it once. Nothing
else in the writer varies by node.

```java
private static final class GeneratedAuditLogger extends EventLogger {
    private final int nodeId;              // constructor arg — the only per-node thing
    private final BinaryLogRecord rec;     // concrete type — same for every node
}
```

So a 30-node graph with every node logging generates **one** extra class, instantiated 30 times. Code
cache cost is one class body and, in native, one compiled method per `log` overload actually used.

If it had been one class per node it would be 30 classes with identical bodies differing in a single
constant — which is the trade the owner is right to check, and it is not the trade being made.

### 27.2 But the count is the wrong question — most of the win needs no generation at all

Splitting what generation actually buys:

| what it buys | needs generation? | measured |
|---|---|---|
| **concrete record type** — removes the virtual `LogRecord.addRecord` call | **no** — a core `BinaryEventLogger` with a `BinaryLogRecord`-typed field does it | part of the 27.58 ns dispatch term, **not separately isolated** |
| **`VarHandle` value stores** | **yes** — core targets Java 8 | native **−34.5 ns**, JIT **+8.6 ns** |
| node id as a literal | no | already free since §20 |

**Only the `VarHandle` term actually requires generated code**, and it is the one term that *hurts* JIT.

### 27.3 Which makes the recommendation a split, not a yes/no

| | ships as | who benefits |
|---|---|---|
| **core `BinaryEventLogger`** — concrete record field, byte-loop stores | **default**, no generation, no extra class | **both** toolchains |
| **generated logger** — adds `VarHandle` stores | **compiler option**, off by default | **native only**; costs JIT 8.6 ns |

That matches how the toolchain already treats native-specific work: the generated
`native-image.properties` inlining directive is emitted always and matters only for native, and the same
logic says a JIT deployment should not pay 8.6 ns for a native optimisation.

It also keeps the default path free of generated classes entirely — the code-cache question disappears
for everyone who does not opt in.

### 27.4 What is not measured, and it matters to this decision

**The `addRecord` virtual call has not been isolated.** §19.3's 27.58 ns dispatch term bundles three
things: the `auditLog.info` virtual call from the node, the level check, and the virtual `addRecord`.
§26 established the first cannot be removed (`auditLog` is typed `EventLogger`, and `NullEventLogger`
plus `EventLogger` already occupy it) and §9 measured provability at ~2 ns — but **how much of the
27.58 is `addRecord` specifically is unknown.**

If it is most of it, the core-only option captures most of the win and generation is a small native
extra. If it is little, generation carries more of the value and the option matters more.

**That measurement is one arm — an `EventLogger` holding a concrete record field — and it should be
taken before the default is chosen.** Recorded as the open question, not guessed at.

## 28. `BinaryEventLogger` in the runtime — 40% off native audit, and no generation needed

The owner's question: *if it is one per processor, can we have them in the runtime — do we need to
generate?*

**No generation is needed, and §24–27 were solving a problem that does not exist.**

### 28.1 Why the runtime is the right home

Nothing about the logger varies per processor:

- the **node id** is a constructor argument — per instance, not per class;
- the **record type** is `BinaryLogRecord` for every binary processor;
- **`BinaryLogRecord` is `final`**, so a field of that type devirtualises in the runtime exactly as it
  would in generated code.

And generating it would not have bought what §24 claimed. The value stores live *inside*
`BinaryLogRecord`, which is core and Java 8 — so a generated logger calling `addRecord` still reaches
the byte loop. **`VarHandle` is not reachable by generating the logger**; it needs the stores themselves
to move, which is a different and more invasive design.

### 28.2 Results — the §27.4 measurement, finally taken

`BinaryEventLogger extends EventLogger` holds the record as a concrete `BinaryLogRecord`, so the
per-entry write is a direct call. One binary per toolchain, record chosen by a runtime property, so
exactly one variable moves. Records verified identical (180.8 B, matching checksum).

| | audit cost, generic logger | with `BinaryEventLogger` | saved |
|---|---:|---:|---:|
| JIT | 41.73 | 41.45 | **0.28 ns — 1%** |
| **native** | **148.19** | **88.43** | **59.76 ns — 40%** |

**The asymmetry is the result.** HotSpot already devirtualises `addRecord` by profiling, so it gains
nothing and needs nothing. Native cannot speculate, so it gained 40% — and now does not have to.

That answers §27.4's open question: **the virtual `addRecord` call was most of the dispatch term on
native**, and it is removable in the runtime for free. It also settles §27.3's default-versus-option
question in the simplest possible way — **there is no option to make.** The specialised logger is
selected automatically whenever the record is binary, costs nothing on JIT, and generates nothing.

### 28.3 Where that leaves native

| | JIT | native | ratio |
|---|---:|---:|---:|
| audit cost before | 41.73 | 148.19 | 3.55× |
| **audit cost now** | **41.45** | **88.43** | **2.12×** |

### 28.4 Predictions for the one combination core cannot reach

`BinaryEventLogger` + `VarHandle` stores needs the stores out of core — generated code or a
multi-release jar. Measured separately, `VarHandle` was worth **−34.5 ns native, +8.6 ns JIT**.

| # | Prediction | Basis |
|---|---|---|
| P1 | native audit cost lands **50–58 ns**, closing native/JIT to **~1.3×** | 88.43 − ~34 |
| P2 | the terms do **not** compose fully — the combined saving comes in **under 34.5 ns** | the 34.5 was measured against a *virtual* `addRecord` that is now direct |
| P3 | JIT still **loses** 8.6 ns to `VarHandle`, so it stays a native-only option | §21.3 |

**P2 is the one most likely to be wrong in an interesting direction.** This round has repeatedly found
terms that did not compose the way arithmetic predicted — the escaping processor, the guards, the
inlining directive.

## 29. What the round established

### 29.1 One mechanism explains most of the JIT/native difference

Native AOT is **5.5× faster than JIT at dispatch** (2.13 ns against 11.84 on the same 30-node graph) and
slower at everything that keeps state alive across a call. Three separately-measured results turn out to
be the same mechanism — **anything that stops the processor being scalar-replaced costs native far more
than JIT**:

| | native | JIT |
|---|---:|---:|
| processor escapes its loop method | **4.3×** | 1.0× |
| dirty filtering on | **11.9×** | 2.5× |
| audit record built per event | +94 ns | +53 ns |

A fourth belongs with them: **native cannot speculate on a runtime constant.** A `switch` on a mutable
`static String` that never changed cost native **8.2 ns** and JIT **0.17 ns** — a 48× asymmetry, because
HotSpot profiles the read and folds it while native-image pays forever with no deoptimisation guard.

**This is the practical rule the round produced:** on AOT, keep values that are constant for a run in
`final` fields, and keep the processor from escaping. Both are free on JIT, and both are large on native.

### 29.2 Audit cost is the record, not the machinery

| term | JIT | native |
|---|---:|---:|
| audit dispatch + call chain | 10.93 | 27.58 |
| **record building** | **57.93** | **98.92** |

84% of the audit cost on JIT and 78% on native is *building the record*. Which means the format decides
almost everything:

- **binary vs text is 3.2× when one node logs and 5.1× when every node does** — the value grows with
  audit density;
- **node weight dilutes it** — heavy nodes drop the ratio to 1.56×, because node work is the same either
  way;
- per logged value: **26 ns text against 3.4 ns binary**.

### 29.3 The two fixes that landed, and why they were findable

| fix | JIT | native | why it was there |
|---|---:|---:|---|
| resolve names once per node, not per event | −24% | **−57%** | the cache was on the *shared record*, so 12 nodes fought over one slot |
| hold the record as a concrete type | −1% | **−40%** | HotSpot devirtualises by profiling; native cannot |

Both are **runtime changes with no generation and no node-code change**, and both were found by
decomposing rather than guessing — the no-op-record arm separated dispatch from encoding, and the
`intern=none` arm gave a ceiling to aim at.

### 29.4 The methodological result, which may be the most durable

**Five silent harness faults in one round**, each producing a plausible number and a wrong published
conclusion: a `-D` after the main class that never reached the JVM; two different arms compared as one;
un-interleaved runs on a machine with P and E cores; a build classpath missing the generated inlining
directive; and a processor allowed to escape its loop method. Plus a profile that **silently disabled
the audit log** and was reported as a speed-up, and a baseline that differed from the audited build by
dirty filtering as well as by the auditor.

**The failure mode of this work is not a wrong measurement. It is a right measurement of the wrong
thing.** Every one of those produced a correct number.

And a second pattern, seen four times: **a finding is scoped to the regime it was measured in.**
Round 62's receiver-provability result, round 60's flag sweep, the inlining directive, and my own
"interning is not on the critical path" all held where they were taken and failed when carried across.

The tooling that came out of it is the answer to both: a versioned harness that refuses to report what
it cannot prove, a binary index keyed by configuration, control bands, and a comparison script that
**refuses a comparison where more than one declared input differs**.

### 29.5 Targets

**10M events/sec, fully audited, on a realistic graph — met on both toolchains.** 30 nodes, 5 event
types, every node on the path logging, zero allocation, 181 bytes per record.

### 29.6 What is not established

- **Native is still 2.12× JIT on audit cost** after both fixes. What remains is straight-line stores,
  and no explanation beyond "AOT is slower at those" has been demonstrated.
- **The `VarHandle` combination is unmeasured** — predicted in §28.4, not run.
- **Most rows in `RECORDED-BASELINES.md` are still pre-h3**, measured with the processor escaping. They
  are marked, and they need re-measuring before anything calibrates against them.
- **Nothing has been run on a second machine.** Every coefficient is Apple-M4-specific until shown
  otherwise, which is the assumption most likely to be wrong.

## 30. Is `VarHandle` required? — two Java 8 alternatives

`VarHandle` buys one thing: writing 8 bytes as **one store with one bounds check** instead of eight of
each. It is worth **−34.5 ns on native** and cannot ship in core, which targets Java 8. So: is there a
Java 8 way to get the same effect?

Two candidates, both pure Java 8:

**C — `ByteBuffer.putLong(index, value)`.** A heap `ByteBuffer` wrapping the same array. HotSpot
intrinsifies it; GraalVM native-image has its own intrinsics for it too. One call, one store.

**D — `long[]` slots instead of `byte[]`.** Stop writing bytes at all. Pack
`nodeId(16) | keyId(16) | tag(8)` into one `long` and put the value in the next, so an entry is **two
aligned `long` array stores** — the simplest thing a JIT or an AOT compiler can emit, with no unaligned
handling and no byte assembly. The record becomes 16 bytes per entry instead of 13, and bytes are
produced only at publish.

### 30.1 Predictions

| # | Prediction | Basis |
|---|---|---|
| Q1 | `long[]` slots beat the byte loop on native by **> 25 ns** | it removes the same 8 bounds checks `VarHandle` does, and adds nothing |
| Q2 | `long[]` slots **beat `VarHandle` too** | aligned array stores need no unaligned-access handling, and there is no `VarHandle` indirection for a JIT to pay for |
| Q3 | `ByteBuffer.putLong` lands **between** the byte loop and `long[]` | intrinsified, but through more layers than a bare array store |
| Q4 | on JIT all four are **within ~5 ns** | HotSpot already folds the byte loop — §21.3 measured `VarHandle` as 8.6 ns *worse* there |
| Q5 | the record grows **~23%** (13 → 16 bytes per entry) | packing to whole longs wastes 3 bytes per entry |

**Q2 is the one that would settle it.** If `long[]` beats `VarHandle`, then `VarHandle` is not required,
core can ship the fast path, and the multi-release-jar and generated-writer options both become
unnecessary.

### 30.2 Results — `VarHandle` is not required, and Java 8 beats it

| store | JIT ns | native ns | vs the byte loop core ships |
|---|---:|---:|---:|
| bytewise *(core today, Java 8)* | 61.00 | 123.20 | — |
| `ByteBuffer.putLong` *(Java 8)* | 72.45 | 118.60 | −4.60 |
| `VarHandle` *(not shippable in core)* | 64.14 | 91.52 | −31.67 |
| **`long[]` slots *(Java 8)*** | **60.72** | **71.77** | **−51.42** |

| # | Predicted | Measured | |
|---|---|---|---|
| Q1 | `long[]` beats the byte loop by > 25 ns on native | **51.42** | ✅ |
| Q2 | `long[]` **beats `VarHandle`** | **19.75 ns better** | ✅ |
| Q3 | `ByteBuffer` lands between | −4.60, between byte loop and `VarHandle` | ✅ |
| Q4 | all four within ~5 ns on JIT | `ByteBuffer` is 11.4 off | ❌ |
| Q5 | record grows ~23% | 172 vs 180.8 — **not cleanly measured**, the arm is partial | — |

**So `VarHandle` is not required, and the multi-release jar and the generated writer are both
unnecessary** — a pure Java 8 record beats the option core could not ship.

**Caveat, and it matters:** the `longslot` arm only converts the *entries*. The header and terminator
still go through the byte path, which is why the record reads 172 bytes against 180.8. **71.77 is not a
shippable number** until the whole record moves to slots.

### 30.3 The combination has not been measured

Every store arm above ran with the **generic** `EventLogger`, because `-Drecord=binary` installs the
bench record, not the core type. And `BinaryEventLogger` (§28) was measured only against the **core**
record, which is bytewise. **So `BinaryEventLogger` + `long[]` slots is unmeasured**, and it is the
shippable configuration.

| # | Prediction | Basis |
|---|---|---|
| R1 | they are **largely additive** — native audit cost lands **40–55 ns** | they address different things: the logger removes the virtual `addRecord`, the slots remove byte assembly *inside* it |
| R2 | the combined saving is **slightly less than the sum** (59.76 + 51.42) | the −51.42 was measured through a virtual call that is now direct, so part of it was call overhead |
| R3 | JIT is **unchanged, ~40 ns audit cost** | both changes are worth ~0 on JIT separately |
| R4 | **native clears 10M msg/sec** on the fully-audited converging graph | 40–55 ns audit on a 17 ns baseline is 57–72 ns total |

**R4 is the one that matters** — it is the target this graph shape has been missing on native.

### 30.4 Results — they combine, and native clears the target

`BinaryEventLogger` + `long[]` slots, both in core, both pure Java 8, no generation.

| | JIT | native |
|---|---:|---:|
| baseline, no auditor | 19.72 | 17.77 |
| audited | 53.42 | 66.12 |
| **audit cost** | **33.70** | **48.34** |
| throughput | 18.7 M/s | **15.12 M/s** |

| # | Predicted | Measured | |
|---|---|---|---|
| R1 | native audit cost **40–55 ns** | **48.34** | ✅ |
| R2 | combined saving **less than the sum** (59.76 + 51.42 = 111.2) | **99.85** | ✅ |
| R3 | JIT unchanged at ~40 ns | **33.70** — improved more than predicted | ❌ |
| R4 | **native clears 10M msg/sec** | **15.12 M/s** | ✅ |

**Native audit cost: 148.19 → 48.34 ns, 3.07×.** Native/JIT on audit cost: **3.55× → 1.43×**.

R3 is worth noting: the `long[]` slots helped JIT too (40.09 → 33.70), which the separate measurement
had shown as ~0. Two changes that each looked JIT-neutral were not neutral together.

### 30.5 Are we at the end of the line?

No, but the remaining headroom needs machinery that does not exist.

| | native audit cost |
|---|---:|
| where the round started | 148.19 |
| **now — core, Java 8, no generation** | **48.34** |
| inline ceiling (§22, hand-edited nodes) | 14.24 |

**Everything reachable without generation has been taken.** What is left between 48.34 and 14.24 is the
`auditLog.info` virtual call, the level check, and the header/terminator — and closing it needs
something that sees node method bodies: an annotation processor, bytecode transformation, or an API
change moving the key out of the call site. §23 established the generator cannot do it, and none of the
three is proposed.

**And the two options that were going to be needed are now unnecessary.** The multi-release jar and the
generated writer both existed to reach `VarHandle`; a pure Java 8 record beats `VarHandle` by 19.7 ns.

## 31. Harness rigour — identity, stability, repeatability

Three gates added, each because something got past its absence.

### 31.1 Identity — a result must say what produced it

`HarnessVersion` now stamps **both** the harness version and a **runtime digest** on every RESULT line:

```
RESULT harness=h5 rt:4ca35f6085 store=... graph=conv record=core ...
```

The runtime changed **six times** in this round — the id path, the specialised logger, the slot record —
and nothing in a build log or a flag list shows which one produced a figure. `measure.sh` **refuses**
a result carrying no harness version or runtime digest, and it caught a native binary built at h4 being
compared against h5 numbers within minutes of being written.

### 31.2 Stability — gate on the statistic actually reported

The first version gated on the CV of every rep and refused a JIT run at **11.88%**. That was the wrong
statistic: JIT warm-up variance is inherent, which is *why* the minimum is reported. So the gate is now
**K batches of N reps, minimum per batch, CV across the batch minima** — which is repeatability, the
thing actually being claimed.

### 31.3 Repeatability, measured — and the owner's expectation confirmed quantitatively

| | batch minima | spread | CV |
|---|---|---:|---:|
| **native**, audited | 63.825 / 63.827 / 63.878 | **0.053 ns** | **0.05%** |
| **native**, baseline | 17.047 / 17.153 / 17.110 | 0.106 ns | 0.31% |
| **JIT**, audited | 57.502 / 60.650 / 64.175 | **6.673 ns** | **5.49%** |

**Native is roughly 100× more repeatable than JIT.** Thresholds are therefore set from measurement, not
from a round number: **2% native, 6% JIT.**

Two consequences:

1. **A JIT difference under ~5% is not a difference.** Several comparisons in this round sat in that
   band and were reported as though they were results. The harness now refuses them.
2. **It is a product property, not only a benchmarking nuisance.** A deployment that cares about the
   tail rather than the median gets a far flatter distribution from AOT — consistent with §10.5, where
   native's spread was already 10× tighter before any of this work.

### 31.4 Current numbers, through the gated harness

| | native | JIT |
|---|---:|---:|
| baseline, no auditor | **17.047 ns · 58.7 M/s** (CV 0.31%) | ~19.7 |
| **audited** | **63.825 ns · 15.7 M/s** (CV 0.05%) | ~57.5 (CV 5.49%) |
| audit cost | **46.78 ns** | ~37.8 |

The native figures are stated with their CV because they earned it. **The JIT figures are quoted as
approximate**, because at 5.49% they are not repeatable to the precision this round has been quoting
them at.
