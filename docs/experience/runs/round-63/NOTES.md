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
