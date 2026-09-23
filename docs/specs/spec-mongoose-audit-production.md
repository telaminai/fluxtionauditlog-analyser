# Mongoose produces an audit log the analyser can trust (Design Spec)

_Status: **PROPOSED 2026-09-23, not started.** One spec for the whole producer side, replacing the
scattered record of it: tracker AF-4, `UP-MON-02` in [upstream asks](../proposals/upstream-asks.md), and
[mongoose-plugins#38](https://github.com/telaminai/mongoose-plugins/issues/38). The reader half shipped in
analyser **1.18.0** and the exporter half in `mongoose-plugins` **1.0.44**; this is what remains.
Implements the producer side of [audit stream end](spec-audit-stream-end.md), whose §1a contract both
halves obey. Owner decisions are marked **OD**. Related: D-T8 in [trust structure](spec-trust-structure.md)
— never conceal a gap._

## The problem

The analyser's central claim is that **absence is evidence**: a node that never logged is a finding, and
a log that cannot say whether it is whole is reported `unknown` rather than guessed at. Both depend on the
producer. Today Mongoose cannot hold up either end:

1. A processor built the way Mongoose builds one from a `customHandler` **emits no audit records at all**,
   so absence means nothing — nothing could ever have logged.
2. Nothing writes a stream-end marker, so every export reads `unknown` and the 1.18.0 reader work and the
   1.0.44 exporter work deliver nothing a user sees.

## The measurement that orders this work

The obvious plan — write the marker first, it is the smaller change — is wrong, and one measurement shows
why. A marker writer running on a processor that emits nothing writes a marker declaring **zero** records.
Fed to the released analyser 1.18.0 jar:

```yaml
eventLogRecord:
  streamEnd: normal
  streamEndRecords: 0

---
```
```
records   = 0
streamEnd = {"state": "complete", "recordsRead": 0, "declaredRecords": 0}
```

**`complete`.** The analyser states that this log is whole and contains nothing. Today the same processor
produces `unknown` — "I cannot tell". Shipping the marker writer first therefore **replaces an honest
non-answer with a confident false one**, on exactly the processors that are already broken. That is D-T8
inverted, and it would be this project's worst regression.

**So MA-1 precedes MA-2, and that ordering is a requirement, not a preference.**

## MA-1 · A `DefaultEventProcessor` graph has no `EventLogManager`, so no node can log

**What this class is.** `DefaultEventProcessor` is **hand-written in the shape of a generated processor**
— its javadoc header still carries the generator's template fields (`generation time : Not available`) —
and its job is to host a single `ObjectEventHandlerNode`, the `allEventHandler`, so that handler code
which is not a Fluxtion-generated graph can run inside the runtime. It is the wrapper path, not the
compiled-graph path. That matters for how much MA-1 can deliver; see D-MA1b.

**Where, read not inferred.** `fluxtion-runtime` `1.0.15` sources,
`com.telamin.fluxtion.runtime.DefaultEventProcessor`. Its declared auditors are
`NodeNameAuditor nodeNameLookup` and `Clock clock` (lines 65, 97-100). There is **no** `EventLogManager`
field, so `getAuditorById("eventLogger")` throws `NoSuchFieldException` and a node's `auditLog` has
nothing to publish through.

**The class expects one to exist.** `getLastAuditLogRecord()` (line 399) does
`this.getClass().getField(EventLogManager.NODE_NAME).get(this)` and catches `Throwable`, returning `""`.
It is written for a generated subclass that declares an `eventLogger` field, and in this hand-written one
that field never exists — so the method silently returns empty for every caller. **That is evidence this
is an omission in a hand-maintained file rather than a considered decision that auditing is meaningless
here**, and it is the strongest single argument for MA-1.

Mongoose reaches that class through `EventProcessorConfig.getEventHandler()`, which wraps a
`customHandler` in `ConfigAwareEventProcessor extends DefaultEventProcessor`
(`mongoose/src/main/java/com/telamin/mongoose/internal/ConfigAwareEventProcessor.java`).

**Measured consequences**, on a booted `MongooseServer` with `auditCapture` enabled:

| Observation | Result |
| --- | --- |
| Handler calls `auditLog.info/warn/error/debug` per event at level DEBUG | **0 records** |
| `POST /api/processors/{group}/{name}/audit/level` with `{"level":"INFO"}` | **200 OK**, changes nothing |
| Sink directory after 40 events | only `metadata.cq4t`; **no data file ever written** |
| `mongoose-1.0.29`, all 170 classes | **no reference** to `EventLogManager`, `addAuditor` or `EventLogControlEvent` |

**Scope — this is not all processors.** An AOT-built processor does log. The conformance fixture
`src/test/resources/conformance/c21-real-export.yaml`, a preserved real export, holds **25 records of
which 7 carry node entries and 18 are empty** — counted. The defect is the `DefaultEventProcessor`
construction path.

### D-MA1 · Three ways to fix it, and only one needs a framework release

`DefaultEventProcessor` owns its auditor set, so the obvious reading is that the fix must be in
`fluxtion-runtime`. **The owner challenged that twice, and both challenges were right.** The options:

**(a) Change `fluxtion-runtime`.** Add the auditor to `DefaultEventProcessor`. Clean, and it fixes the
path for every consumer — but it needs a framework release, and it puts the always-on/opt-in cost
question (OD-1) in the framework where it affects everyone.

**(b) A Mongoose-side subclass. PROVEN — spiked and running, 2026-09-23.** No framework release. The
spike source and output are in
[`evidence/mongoose-audit-production-2026-09-23/`](../handoff/evidence/mongoose-audit-production-2026-09-23/):
**5 of 5 events produced records carrying `nodeLogs: - allEventHandler: { tick: e1}`**. It works, and
what it takes was found by running rather than reading:

| Requirement | Found how |
| --- | --- |
| `public final transient EventLogManager eventLogger` | `getAuditorById` does `getClass().getField(id)`, so it must be **public** — then it resolves |
| `eventLogger.clock = this.clock` | omitted → `NullPointerException` in `LogRecord.triggerEvent` |
| `eventLogger.init()` + `nodeRegistered(handler, "allEventHandler")` | this is what injects the logger into the handler's `auditLog` |
| `EventLogControlEvent` → `calculationLogConfig(c)`, **not** `eventReceived` | routed to `eventReceived` first: **0 records**, silently |
| otherwise `eventReceived` → `super.onEvent` → `processingComplete()` | `processingComplete` is what publishes |

**Two wrinkles the spike also exposed**, both against (b): the `init()` record bled into the first
event's record, so lifecycle ordering needs care; and `getLastAuditLogRecord()` still returned empty.
Neither is fatal, both are the cost of re-implementing by hand what the framework does natively.

**(c) Mongoose generates the processor shape it wants. RECOMMENDED.** `DefaultEventProcessor` is not a
hand-designed class — its javadoc still carries **unsubstituted** generator placeholders
(`${generator_version_information}`), so it began as generator output and has been hand-maintained since.
Mongoose can therefore generate its own equivalent that declares `eventLogger` and wires it in
`initialiseAuditor`, `auditEvent` and `afterEvent` **natively**, exactly as a generated AOT processor
does — no overrides, none of (b)'s wrinkles, and no framework release. Like `DefaultEventProcessor`
itself, it is generated once and committed, so no permanent build-time dependency on the compiler is
needed (neither Mongoose repo has one today).

**OD-3 — owner decision: (a), (b) or (c).** The spec recommends **(c)**: it is the only one that is both
release-free and structurally the same as what the framework does for generated graphs. (b) is proven and
is the fallback if generating is unattractive. (a) remains the right answer if the fix should benefit
every `DefaultEventProcessor` consumer rather than only Mongoose.

**OD-1 — owner decision.** Two shapes, and the owner picks:

| | Always on | Opt-in |
| --- | --- | --- |
| **What** | `DefaultEventProcessor` always installs an `EventLogManager` | a constructor/flag installs it |
| **For** | `auditLog` in a node means the same thing everywhere; no silent no-op | no cost for users who never audit |
| **Against** | `UP-FLX-51` measured **~120 ns/event** for a manager recording nothing | the silent no-op survives for anyone who does not know the flag |

`UP-FLX-51` proposes defaulting the record to `NONE`, which would remove most of that cost and make
"always on" cheap. **These two asks should be decided together.**

### D-MA1b · What MA-1 can and cannot deliver — it is NOT per-node coverage

`initialiseAuditor` (line 291) registers a **fixed list of four nodes**: `callbackDispatcher`,
`subscriptionManager`, `context` and `allEventHandler`. An `EventLogManager` installed here would
therefore audit those four and **nothing else**. Any node the handler constructs or references
internally is invisible to the runtime on this path, because the list is hard-coded rather than
discovered.

So MA-1 buys **the handler's own log lines**, not the per-node topology an AOT-built processor gives. The
analyser's coverage denominator becomes four, not the user's real node count.

**This is worth stating plainly because it limits the claim.** MA-1 turns "this processor can never log"
into "this processor logs at handler granularity". It does **not** make "absence is evidence" work at
node level on the wrapper path — for that, the processor has to be a real generated graph. An earlier
draft of this spec implied otherwise.

**Does option (c) lift the cap?** Partly, and the honest answer is: not by itself. A generated processor
registers the nodes known **at generation time**, and on the wrapper path the handler is supplied at
**runtime**, so its internal nodes are still invisible. (c) buys the same coverage as (b), cleanly. The
cap is lifted only by generating from the user's actual node set, which is the AOT path and is out of
scope here. **This is worth a reviewer's attention: if (c) can be made to register a runtime-supplied
node set, MA-1 becomes far more valuable than this spec claims.**

**OD-2 — owner decision.** Given that cap, is MA-1 worth doing at all, or is the right answer to tell users
that auditing requires an AOT-built processor and make the wrapper path **say so** rather than silently
emit nothing? A third option: keep MA-1 *and* have the analyser report the wrapper path explicitly, so a
four-node denominator is not mistaken for a complete topology.

### Acceptance MA-1

1. A processor added via `EventProcessorConfig.builder().customHandler(...)`, audit level INFO, writes
   records **carrying `nodeLogs` entries** into its Chronicle sink.
2. `getAuditorById("eventLogger")` resolves on that processor.
3. `POST …/audit/level` demonstrably changes what is written — a level below the call suppresses it, a
   level at or above emits it. The endpoint returning 200 is not acceptance; the bytes are.
4. The analyser loads that export with a **non-empty coverage denominator** and `NO_NODE_LOGS` does not
   fire — understanding that the denominator is the four registered nodes, not the user's topology
   (D-MA1b). An acceptance that reads a four-node denominator as full coverage would be wrong.
5. A regression check that fails if the auditor stops being installed — per CLAUDE.md rule 8, the finding
   is not closed until the check that would catch it next time exists.

## MA-2 · The marker writer — blocked on MA-1

Mongoose writes the text audit file directly, ending with a stream-end marker, so a reader can tell a
whole log from a truncated one. This is tracker AF-4 item 1.

### D-MA2 · Per-node entry parity, not a record count

The marker declares what was written. §1a's count is over **records**, but the thing worth checking is
that each record's node entries survived — a record count alone passes while every record is empty, which
is precisely the MA-1 failure. The writer states the record count as §1a requires **and** the acceptance
compares per-node entries.

### D-MA3 · Config validation refuses unknown values by name

A `streamEnd` value the writer does not recognise is refused at configuration time, naming the value and
the accepted set, rather than written and left for a reader to interpret.

### D-MA4 · The separator is not this writer's job

Settled and shipped: it belongs to the export formatter, done in `1.0.44`. A marker writer that tries to
terminate its own document is writing someone else's byte.

### Acceptance MA-2

1. An export carrying a marker reads **`complete`** in the released analyser, with `declaredRecords`
   equal to `recordsRead`.
2. An export **without** one reads exactly as it does today.
3. A run killed mid-write reads `unterminated_marker` or `unknown` — **never** `complete`. This is the
   assertion that MA-1 exists to make meaningful.
4. **Byte-identical to a known-good export modulo the marker and the final separator.** The original
   acceptance said "modulo the marker" alone; §1a rule 1 makes that impossible, because a known-good
   export has no trailing separator. Recorded because the withdrawn version is still quoted in places.
5. Verified against the **published** analyser jar, by digest, not a local build.

## MA-3 · The `MAX_PENDING` ceiling has no live-server test

[mongoose-plugins#38](https://github.com/telaminai/mongoose-plugins/issues/38). Independent of MA-1 and
MA-2 and much smaller; included so the producer side has one list.

The ceiling is guarded at unit level by `AuditTailTickTest`, where a **failing send** is the condition. A
live test was written and failed on its own premise: a JDK websocket client that stops calling `request()`
applies flow control in its listener, not on the wire, so the server's sends kept succeeding and the batch
never grew.

### Acceptance MA-3

A client that completes the upgrade and then stops reading **at TCP level** drives the pending batch to
`MAX_PENDING`; the server logs `client fell behind`, sends the `err` frame and closes the session.

## Out of scope, explicitly

- **The per-node `NONE` corruption** gating tracker AF-7. Adjacent, and **its description could not be
  found** in the live tracker — only a reference at `tracker.md:131`. Recovering what it is comes first,
  and is not this spec.
- **AF-6, the coupled analyser documents.** Blocked on MA-2 shipping, by design: until a marker is
  written, the statement that Mongoose does not write analyser-readable text directly is still true.
- **Binary audit encoding**, `spec-binary-audit-encoding.md`.

## Ordering

```
MA-1  (fluxtion-runtime: install the auditor)   ← OD-1 blocks the start
  └── MA-2  (the marker writer)                 ← blocked on MA-1, measured above
        └── AF-6  (the coupled documents)       ← blocked on MA-2, not in this spec
MA-3  (the ceiling's live test)                 ← independent, any time
```

## What is verified, and what is only read

**Verified by running, 2026-09-23:** the empty-but-claimed-complete measurement against the published
1.18.0 jar (sha256 `5a8c2a4f070ad06a7804894391b5660d3fe160c14d6f382ddf2ddff3f79a2f02`); the zero-record
emission on a booted `MongooseServer`; the `getAuditorById` failure; the c21 counts of 25/7/18.

**Read, not run:** `DefaultEventProcessor`'s declared fields (via `javap` on
`fluxtion-runtime-1.0.15.jar`); `EventProcessorConfig.getEventHandler()` and `ConfigAwareEventProcessor`
source on `develop` `17a03b4`.

**Not established:** whether "always on" or "opt-in" is right for OD-1; the per-event cost of an
`EventLogManager` after `UP-FLX-51`; whether any AOT path in Mongoose is also affected.
