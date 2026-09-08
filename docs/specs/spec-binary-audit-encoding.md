# Spec — binary audit encoding and the LOW_LATENCY_AUDIT profile

**Status:** PROPOSED · **Owner:** analyser (measurements) + Fluxtion core (implementation)
**Evidence:** `docs/experience/runs/round-63/NOTES.md` §6–§8 — every number below is measured, and the
predictions that were wrong are scored there too.

## 1. Why

An audited Fluxtion processor runs at **6.5M events/sec (JIT)** and **4.5M (native AOT)** on a
representative 30-node, 5-event-type graph with a converging tail. The graph itself dispatches in
**10.0 ns (JIT) / 3.4 ns (native)**. Everything else — 94% of JIT cost, 98% of native — is building an
audit record.

**That record is text.** `LogRecord.addRecord(String, String, double)` calls `sb.append(value)`, a Ryu
double-to-text conversion, executed by the node doing the logging, inside the event cycle. All seven
overloads end the same way.

Downstream cannot undo it. Mongoose's `ChronicleAuditCaptureService` is a `LogRecordListener`, so it
receives the record only after `terminateRecord()`, when all 221 characters already exist. Measured,
`asCharSequence()` costs **3.6 ns** — there is nothing left for a sink to do. A sink cannot choose the
bytes; only the encoder can.

Replacing the text encoder with one that writes bits reaches **20.3M/s (JIT)** and **12.8M/s (native)**,
zero allocation, at 54 bytes per record against 193.

## 2. Goal

**≥10M events/sec fully audited on both JIT and native AOT**, excluding the disk or network write, with
zero allocation on the event path.

Met by the prototype: 20.3M and 12.8M. This spec is about making that a supported configuration rather
than a subclass in a benchmark kit.

## 3. What is already true (do not re-litigate)

Three things were verified by reading the source and are load-bearing here:

1. **A plugin record is already installable.** `EventLogControlEvent` has a public `LogRecord`
   constructor; `EventLogManager.calculationLogConfig` swaps `this.logRecord` and re-points every
   node's `EventLogger` through `updateLogRecord()`. The prototype used exactly this path.
2. **Types survive to the encoder.** `EventLogger.log(String, double, LogLevel)` calls
   `logrecord.addRecord(sourceId, key, double)`. No boxing, no stringification. A binary encoder
   receives the primitive.
3. **The generated processor calls `clock.eventReceived(...)` before `eventLogger.eventReceived(...)`**
   — but that follows auditor registration order and **is not enforced**. §6.2 of this spec makes it
   normative because §5 depends on it.

## 4. Measurements this spec rests on

30 nodes, 5 event types, one shared tail; minimal audit profile; no sink write. JIT is OpenJDK 25.0.2;
native is Oracle GraalVM 25.0.4+7.1 with per-arm PGO.

| Graph | Record | JIT ns | JIT Mmsg/s | native ns | native Mmsg/s | bytes/rec |
|---|---|---:|---:|---:|---:|---:|
| tail — 1 node logs | text | 147.2 | 6.8 | — | — | 193 |
| tail — 1 node logs | **binary** | **47.6** | **21.0** | 70.9 | 14.1 | 54 |
| **converging — every node logs** | text | 403.0 | 2.5 | 698.6 | 1.4 | 548 |
| **converging — every node logs** | **binary** | **79.4** | **12.6** | 149.5 | 6.7 | 181 |

All arms `LOW_LATENCY_AUDIT`, `logTime` from `getProcessTime()`, no-op sink, zero allocation,
`recPerEvent` verified at 1.000 and graph checksums verified equal. Native: per-arm PGO, `--gc=epsilon`,
the generated inlining directive, `armv8.1-a`, each **verified in the build log**. Interleaved, minimum
of 6 reps.

**Earlier drafts of this spec quoted native as faster than JIT. That was measured against a profile
that had silently disabled the audit log** (round 63 §12.1) — withdrawn.

Two results worth carrying forward because they are counter-intuitive:

- **Native AOT is 1.5–1.9× slower than JIT on the audited path**, and the gap grows with audit density
  (1.49× at 2 entries per record, 1.88× at 11.75). Native's spread is much tighter — ~1.5 ns against
  ~7 ns — so it is the better choice where the tail latency matters more than the median, but it is
  **not** the faster option here. Receiver provability does not explain the gap: a monomorphic image
  measures 151.5 ns against 149.5 polymorphic.
- **The 10M msg/sec target is met on JIT (12.6M) and missed on native (6.7M)** for a graph where every
  node logs. The lighter shape clears it on both (21.0M / 14.1M), so audit density is what decides.
- **Sink-side encoding is a separate 571 ns.** Handing Chronicle a 221-char wire string costs 665 ns/append;
  handing it a 221-byte blob writing the identical bytes to the identical file costs 96.8. That is
  `ValueOut.text(CharSequence)` at ~2.6 ns/char, and it is Mongoose's call to make, not core's (§8).

## 5. Change 1 — `logTime` (correctness fix; **implemented**)

`logTime` is documented as "when the event processing began". `Clock.eventReceived` **already takes
that reading** and caches it as `processTime`. `LogRecord` ignored it and took a second, later reading,
so `logTime` reported a time *after* processing began — and paid `System.currentTimeMillis()` per record
to be less accurate.

**Normative:** `logTime` MUST come from `Clock.getProcessTime()`. `endTime` MUST remain a live reading,
because `endTime - logTime` is the processing duration and a cached value reports it as zero.

Worth **13.7 ns/event** on the JIT text path for every existing user, ~0 on native text, 10.4 (JIT) /
12.5 (native) on a binary record. Implemented as `LogRecord.logTime()`, with
`LogRecordLogTimeTest` gating both halves; the test was verified to fail without the change.

## 6. Change 2 — let a record express itself as bytes

The prototype ran with no core change, but three frictions say core should own the abstraction:

| Friction | Consequence |
|---|---|
| `protected final StringBuilder sb` | every binary record allocates and carries a `StringBuilder` it never writes to |
| `EventLogManager` calls `newLogRecord.replaceBuffer(logRecord.sb)` on swap | the swap path pushes *text* into a record that has no text |
| `asCharSequence()` is the only expression channel | a binary record must throw from it, and every sink must downcast to a vendor class to reach the bytes |

### 6.1 Normative

1. The record abstraction MUST NOT mandate a `StringBuilder`. Either `LogRecord` becomes abstract with
   today's YAML behaviour moved to a `TextLogRecord`, or an encoder interface is introduced behind it.
   Existing `LogRecord` subclasses and `EventLogControlEvent(LogRecord)` MUST keep working.
2. `LogRecordListener` MUST gain a byte-facing path so a sink can write a record's encoded form
   **without downcasting**. A text record satisfies it by exposing its characters as bytes.
3. The record swap path MUST NOT assume the incoming record holds characters — `replaceBuffer` must
   become optional, or move onto the text record.
4. `asCharSequence()` MUST remain available for text records and MUST NOT be the only channel.

### 6.2 Normative — auditor ordering

The generator MUST emit `Clock.eventReceived(...)` **before any other auditor's** `eventReceived(...)`.
§5 is correct only under that order; today it holds by registration accident. A generator test MUST
assert it, because the failure is silent: `logTime` would quietly become the *previous* event's
timestamp.

### 6.3 Wire format (informative — the prototype's shape)

Names are not written. Node names and property keys intern to a `short` id; only the id goes on the
wire, and the dictionary is published separately (it is fixed after warm-up, since generated code
passes String constants).

```
record := header, entry*, terminator
header := 0x01, eventTime:i64, logTime:i64, eventTypeId:u16
entry  := nodeId:u16, keyId:u16, tag:u8, bits
term   := 0x00, endTime:i64
```

54 bytes against 193 characters for the same content. **A reader is required before this format is
usable** — see §9.

The `Object` overload is the one case that cannot avoid text; it is encoded as a length-prefixed
string. A deployment targeting this profile should not be logging `Object`.

### 6.4 What the prototype learned that a re-implementation should not have to

- An id cache keyed on the last name **hits 0%**, because the write path alternates node then key.
  Split into two slots it reaches 50%. **Neither version changed the measured time** — interning is not
  on the critical path, so do not build an elaborate id cache.
- Correctness must gate the encoder. `CorrectnessBinary` drives the same events through both encoders,
  decodes the binary form through its dictionary and asserts all entries match the text record
  value-for-value, doubles to the last digit. Any implementation MUST ship an equivalent.

## 7. Change 3 — the `LOW_LATENCY_AUDIT` profile

`EventProcessorConfig.PerformanceProfile` today offers `DEFAULT`, `AUDITED` and `LOWEST_LATENCY`.
`LOWEST_LATENCY` **gives up the audit log**, so there is no named configuration for "I want the audit
log and I want it cheap" — which is the deployed case.

**Status: IMPLEMENTED**, and corrected after shipping broken. `PerformanceProfile.LOW_LATENCY_AUDIT`
plus `addLowLatencyEventLog(level)`.

!!! danger "The first version disabled the audit log"
    It set `setSupportNodeNameLookup(false)`, which does not merely drop a lookup map — it stops **node
    registration**, and `EventLogManager.nodeRegistered` is what gives every node its `EventLogger`. The
    generated processor emitted **zero** `nodeRegistered` calls against 33 for `AUDITED`, so no node
    ever logged and nothing was ever published. The benchmark measuring "the cost of auditing" was
    measuring a graph with no audit, and reported the missing work as a 5.4 ns speed-up.

    Five tests passed over it, because they asserted the profile's **flags** and not its **behaviour**.
    Now pinned in three places, each mutation-verified. **§7.1's "MUST NOT" list is normative for this
    reason**: the failure is completely silent.

### 7.1 Normative

`LOW_LATENCY_AUDIT` MUST:

- keep the `EventLogManager` auditor — the audit log is the point;
- set tracing **off** (`tracingOff()`), so no per-node invocation records;
- set `printEventToString(false)` and `printThreadName(false)` — the two defaults that allocate;
- set `setSupportBufferAndTrigger(false)` and `setSupportSubscriptions(false)`, measured at zero cost
  but smaller generated code, exactly as `LOWEST_LATENCY` does;
- select the **binary record** once §6 lands.

It MUST NOT:

- set `setSupportNodeNameLookup(false)` — **it stops node registration, which silently disables the
  audit log**; this is not a tuning choice, it is a correctness requirement;
- set `setSupportDirtyFiltering(false)` — that changes propagation semantics, and an audit profile must
  not alter what the graph computes;
- set `setSupportReentrancy(false)` — the same reasoning `LOWEST_LATENCY` already documents: it is the
  one setting that can break a working graph, and a profile should not spend that capability;
- remove the `Clock` framework auditor — §5 depends on it, and so does every timestamp in the record.

### 7.2 What it gives up, stated plainly

Per-node method tracing, the event's `toString()`, and the thread name. Those are the things that make
a record readable when you do not know what you are looking for — which is the wrong trade for a
production hot path and the right one for a development run. `AUDITED` remains the profile for that.

## 8. Out of scope for core — the Mongoose side

Two changes belong to Mongoose, not here, and are recorded so they are not lost:

- `ChronicleAuditCaptureService.ProcessorSink.onRecord` should hand the record's bytes to
  `ValueOut.bytes(...)` rather than `ValueOut.text(CharSequence)`. Measured **748 → 341 ns/event**,
  2.20×, zero allocation, byte-identical queue file.
- The same method calls `Instant.now()` per record for a liveness display. It is **3% of the time and
  100% of the allocation** on that path. A `long` millis, or a stamp taken on the reader side, gives the
  same display for zero bytes.

## 9. Dependency — there is no reader

**The analyser cannot read Chronicle audit logs today.** `ReaderRegistry` registers exactly one reader,
`YamlAuditReader`, and `docs/proposals/upstream-asks.md` still lists **UP-RDR-01 (the Chronicle reader)**
among asks that have not been filed.

This cuts both ways and both are worth saying:

- **It is why the format is free to choose.** Nothing reads the queue yet, so picking bytes over text
  costs nothing in compatibility.
- **It is a hard dependency.** A binary record with no reader is a log nobody can open. §6 MUST NOT ship
  ahead of a reader, and the analyser's format specification (*The audit log ▸ Format specification*,
  M34.3) MUST be extended to cover the binary encoding with the same conformance suite the YAML reader
  passes.

## 10. Sequencing

| # | Change | Where | Blocked by |
|---|---|---|---|
| 1 | `logTime` from `getProcessTime()` | core — **done** | — |
| 2 | auditor ordering made normative + generator test | compiler | — |
| 3 | `LOW_LATENCY_AUDIT` without the binary record | core builder-api | — |
| 4 | byte-facing record + listener path | core | 2 |
| 5 | binary reader + conformance suite | analyser | 4 · CLI reader spec'd in [spec-binary-audit-reader.md](spec-binary-audit-reader.md) |
| 6 | `LOW_LATENCY_AUDIT` selects the binary record | core | 4, 5 |
| 7 | `bytes` not `text`; drop `Instant.now()` | mongoose | — (independent) |

1, 2, 3 and 7 are independently shippable. 6 is the one that must wait for a reader.

## 11. Open questions

- ~~**Dictionary publication.**~~ **RESOLVED** by
  [`spec-binary-audit-reader.md`](spec-binary-audit-reader.md) §3, which had to settle it: a reader that
  cannot resolve id → name cannot print anything. A `0x02` dictionary-entry record on first intern, the
  full dictionary re-emitted at every roll, and an unknown id rendered `#<id>` rather than treated as an
  error — because the common reason to read an audit log is that something went wrong and the file is
  exactly as complete as the process managed to make it.
- **Is `AUDITED` still the right default?** With §5 landed it gets 13.7 ns for free; with §6 it could
  become "binary by default, text on request". Not proposed here — it would change the on-disk format
  for every existing user.
- **Why does §5 pay nothing on native text?** 13.7 ns on JIT text, ~0 on native text, but 12.5 ns on
  native binary. Recorded in round-63 §8.3 as unexplained rather than explained away.
