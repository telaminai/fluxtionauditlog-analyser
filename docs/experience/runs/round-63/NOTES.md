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
