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

## The measurement, and the argument I built on it — WITHDRAWN after review

**The measurement is right. The conclusion I drew from it was wrong, and review overturned it.** This
section is kept rather than deleted, because the reasoning error is the useful part.

**What I measured.** A marker declaring zero records, fed to the released analyser, reads
`{state: complete, recordsRead: 0, declaredRecords: 0}`. Review reproduced this on **both** published
jars (1.18.0 and 1.19.0), by digest. It is accurate.

**What I claimed.** That `complete` was "a confident false one", and therefore MA-1 must precede MA-2 as
a *requirement*.

**Why that was wrong, on two counts:**

1. **`complete` is TRUE, not false.** Under §1a it is a *container* claim — the marker's count matches
   what was read, and a marker is last. For an empty log that declared zero, it is literally true:
   nothing was written and nothing was lost. The brief's own phrase, "true-but-useless", was the accurate
   one; the spec said something stronger and unsupportable.
2. **MA-1 does not remove the case it was supposed to gate.** `EventLogManager.processingComplete()`
   publishes only when some node logged **at the current level**. Verified by running: the same handler,
   calling `auditLog.info` only, produced **5 records at DEBUG and 1 at WARN** — and that 1 was the
   level-change event itself. So a *correctly audited* processor, on any path including AOT, writes an
   effectively empty log whenever its level is quieter than its calls. The hole is not the wrapper path;
   it is any quiet level. **Ordering MA-1 before MA-2 does not close it.**

**Where the harm actually lives: the reader.** Review's five-case probe found that `NO_NODE_LOGS` fires
only when records **exist** and are empty. Nothing fires on an empty log at all, marked or unmarked,
because `ProducerDiagnostics` returns before any check when the index is empty. So today's empty export
is *already* silent; a marker only changes the label from `unknown` to `complete`. **What makes it look
healthy is the missing warning, not the verdict.**

### MA-0 · The analyser reports an empty log as its own finding — and it is in THIS repository

**New, and it replaces the ordering constraint.** When `recordsRead == 0` the analyser says so as a
finding, marked or unmarked, and qualifies the verdict on an empty file — "complete, and empty: the
writer declared and wrote nothing". That fixes today's silent empty export as well, which is a live
defect independent of everything else here.

**With MA-0 shipped, MA-2 is safe in either order**, and MA-1 stands on its own merit — the handler's log
lines — rather than as a gate.

### Ordering, corrected

```
MA-0  (analyser: an empty log is a finding)     ← THIS repo; unblocks the ordering
MA-1  (the auditor on the wrapper path)         ← independent; OD-1 DECIDED opt-in, OD-2 OPEN
MA-2  (the marker writer)                       ← needs MA-0, not MA-1
MA-4  (defaults, docs, the developer journey)   ← GATED on MA-2: OD-4 makes text the developer default
MA-5  (capture must fan out, and restore on stop) ← independent; a live silent-discard path
MA-3  → moved to mongoose-plugins#38
AFMT-3 (the per-node NONE corruption)           ← DEPENDENCY, see below
```

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

**(d) Build through the Fluxtion builder AT RUNTIME. REJECTED — constraint, not preference.**
`fluxtion-builder-api` `1.0.16` does declare `addEventAudit()`, `addEventAudit(LogLevel)` and
`addAuditedEventLog(LogLevel)` — the exact call `ProducerDiagnostics.NO_NODE_LOGS` names — and building
the graph at runtime would register the user's real node set and lift D-MA1b's cap. **It is ruled out
anyway:** the compiler is a **build-time** tool and **Mongoose must not depend on it at runtime**
(owner, 2026-09-23). Recorded because it is the option that would lift the cap, so anyone who revisits
this will think of it; the answer is no, and the reason is the dependency, not the mechanism.

### D-MA1 verdict · (c), generate the shape and commit it

**OD-3 — DECIDED 2026-09-23 by the owner: (c).** (d) is closed on the runtime-compiler constraint;
(a) and (b) are not taken. (b) remains the fallback if (c)'s open questions below answer badly — it is
spiked and working, so falling back costs little.

`DefaultEventProcessor` began as generator output — its javadoc still carries unsubstituted placeholders
— so the shape Mongoose needs is generatable: a processor that declares `eventLogger` and wires it in
`initialiseAuditor`, `auditEvent` and `afterEvent` **natively**, exactly as a generated AOT processor
does. Generate it once **at build time**, commit the source, ship it. Mongoose gains the auditor with
**no runtime compiler dependency**, no framework release, and none of (b)'s hand-written overrides.

**(b) is the fallback and the only PROVEN option** — spiked and running — if generating the artefact is
unattractive. **(a)** remains right only if the fix should benefit every `DefaultEventProcessor` consumer
rather than Mongoose alone.

#### What (c) still has to answer — it is chosen, not proven

Unlike (b), nothing here has been run. These are the implementation's first questions, in order:

1. **Can the generator actually emit this shape?** The target is a processor hosting a *runtime-supplied*
   `ObjectEventHandlerNode` — the wrapper shape — with an `eventLogger` wired in. `DefaultEventProcessor`
   is evidence the shape exists, but not that today's generator will emit it on request. **Spike this
   first**; if it cannot, fall back to (b) rather than hand-writing the file, which is (b) with extra
   steps.
2. **The drift cost, which is (c)'s real price.** The artefact is a committed copy of a shape
   `fluxtion-runtime` also maintains. When `DefaultEventProcessor` changes, Mongoose's copy does not —
   and this is not hypothetical: that class's own `getLastAuditLogRecord()` is **already broken** in the
   framework, silently returning `""` because it reflects on a field its hand-written version never
   declares. A copy inherits that and adds a second place to fix it. **The spec's position: regenerate
   rather than hand-edit, and record the generator version and source in the artefact's header** so the
   next person can tell a regeneration from a patch.
3. **Where does it live and who regenerates it?** `mongoose` core, since that is where
   `ConfigAwareEventProcessor` lives and what chooses the processor class. Needs a documented,
   repeatable generation step — a committed artefact nobody can reproduce is worse than a hand-written
   one, because it *looks* generated.
4. **The `EventLogControlEvent` trap, found by the (b) spike.** Routing it to `eventReceived` instead of
   `calculationLogConfig` yields **zero records, silently** — the same failure shape as the defect this
   spec exists to fix. A generated processor should get this right natively; **assert it**, because it
   fails quietly.

### OD-1 · DECIDED — the auditor is OPT-IN

**Restored and decided 2026-09-23.** This block was silently deleted four commits ago by a splice that
rewrote the section around it (`745cd995`, rejecting option (d)) — nobody noticed, including me, and I
went on to write commit messages about a decision whose text no longer existed. It is the same
add-a-correction-without-minding-what-it-replaces failure as the duplicate ordering block, in its worst
form: not stale text left behind, but live content removed.

**The decision: OPT-IN.** The auditor is installed only when asked for. In the owner's words, the
no-auditor configuration **is** the low-latency profile, and that profile has to stay reachable.

The options as they were framed, for the record:

| | Always on | **Opt-in (CHOSEN)** |
| --- | --- | --- |
| **What** | the processor always installs an `EventLogManager` | a constructor/flag installs it |
| **For** | `auditLog` in a node means the same thing everywhere; no silent no-op | no cost for users who never audit |
| **Against** | `UP-FLX-51` measured **~120 ns/event** for a manager recording nothing | the silent no-op survives for anyone who does not know the flag |

**This settles review's F5.** F5 objected that choosing always-on rested on an unmeasured post-`UP-FLX-51`
cost. Under opt-in nobody pays that cost who did not ask for it, so the measurement is no longer a gate
on this decision. It still matters for `UP-FLX-51` itself, and the `NONE`-default interaction with
**AFMT-3** still stands: a `NONE` default must not ship onto an undiagnosed corruption path.

**What opt-in makes worse, and OD-2 must answer.** Opt-in plus today's wrapper-path behaviour is the
worst combination available: a user who *explicitly opts in* gets silence — an empty log and a 200 from
the level endpoint. Opting in and receiving nothing is a stronger failure than never having been offered
it.

### D-MA1b · What MA-1 can and cannot deliver — it is NOT per-node coverage

`initialiseAuditor` (line 291) registers a **fixed list of four nodes**: `callbackDispatcher`,
`subscriptionManager`, `context` and `allEventHandler`. An `EventLogManager` installed here would
therefore audit those four and **nothing else**. Any node the handler constructs or references
internally is invisible to the runtime on this path, because the list is hard-coded rather than
discovered.

So MA-1 buys **the handler's own log lines**, not the per-node topology an AOT-built processor gives.

**There is no "denominator of four", and review corrected this too.** The analyser takes coverage's
denominator from the **paired graph** (`CoverageScope`), not from the log. The wrapper path produces no
graph, so `LogArrival` records `noGraph`/`nothingToJudge` and the analyser makes **no coverage claim at
all** — the denominator is absent, not small. (The four-node registration itself is confirmed; the spec's
auditor list also omitted a third, `serviceRegistry`.)

**This is worth stating plainly because it limits the claim.** MA-1 turns "this processor can never log"
into "this processor logs at handler granularity". It does **not** make "absence is evidence" work at
node level on the wrapper path — for that, the processor has to be a real generated graph. An earlier
draft of this spec implied otherwise.

**The cap stands under every option that remains.** (a), (b) and (c) all keep the wrap-an-object shape
and register the four hard-coded nodes. Only (d) would have lifted it, by building a real graph at
runtime, and (d) is closed — Mongoose must not depend on the compiler at runtime. So MA-1 delivers
handler-granularity logging on this path, and **full per-node coverage remains the AOT path**, which
already works: `c21-real-export.yaml` is a real export from one, 7 of its 25 records carrying node
entries. That is a coherent position rather than a gap — the wrapper path is for code that is not a
Fluxtion graph, and it can only report what it knows.

This sharpens OD-2 rather than answering it: given that the cap is permanent under the remaining
options, is MA-1 worth doing at all, or is the honest product answer to make the wrapper path SAY that
per-node audit requires an AOT-built processor?

**OD-2 — OPEN, and reframed by OD-1.** The owner is undecided. The narrowest form of the question:

**It is no longer "is MA-1 worth building".** Under opt-in it costs nothing to anyone who does not ask.
What remains is **truthfulness**, and D-T8 answers half of it: a user who opts into auditing must not be
told that nothing is wrong. So the choice is between two honest outcomes —

- **(i) Make it log.** MA-1 as specified: handler-granularity records, no coverage claim.
- **(ii) Make it refuse.** The wrapper path says at configuration time that per-node audit needs an
  AOT-built processor, and the level endpoint stops returning 200 for a processor that cannot honour it.

**Only "neither" is off the table**, because that is today's behaviour and it is the defect class this
spec exists to remove.

**The check is DONE — CONFIRMED 2026-09-23, not inferred.** A fresh public bundle was downloaded from
the documented endpoint (`/start/scaffold?template=analyser-bundle`, 200, 65,364 bytes) and read:

| Question | Answer |
| --- | --- |
| Is `marketProcessor` a `customHandler`? | **No.** `MongooseProgrammaticMain` uses `.handlerBuilder(new MarketProcessorSupplier())` |
| Does `customHandler` appear anywhere in the bundle? | **No occurrences** |
| What supplies the processor? | `generated/MarketProcessor.java` — **AOT-generated** |
| Does it declare the auditor? | **Yes** — `public final transient EventLogManager eventLogger`, clock wired, `initialiseAuditor(eventLogger)` called |
| What does it register? | **The user's own domain nodes** — `riskCheck`, `rootNode` — plus `callbackDispatcher`, `subscriptionManager`, `context` |

**So the developer download is entirely unaffected by MA-1.** It has the auditor, per-node registration
of real domain nodes, and therefore a real coverage denominator — the opposite of the wrapper path in
every respect. The onboarding journey never touches the broken path.

**What that means for OD-2.** MA-1's value is confined to users who write their own `customHandler`
processors. For them, option (i) delivers **handler-granularity logging only** — no per-node coverage,
no graph. But the bundle demonstrates that the AOT path gives those same users **real per-node audit**,
which is strictly better than anything MA-1 can offer them.

**Recommendation: (ii), and it is now the cheaper AND the more useful answer.** Tell a `customHandler`
user at configuration time that per-node audit requires an AOT-built processor, and stop the level
endpoint returning 200 for a processor that cannot honour it. That is honest, small, and points them at
the path that actually serves them — rather than building MA-1 to hand them a degraded version of
something they can already have properly.

**(i) is still defensible** if handler-granularity logging is wanted for its own sake on a path that
deliberately hosts non-Fluxtion code. That is the owner's call; the evidence no longer supports doing it
for the onboarding journey's benefit, because there is no such benefit.

Available under either: have the analyser report the wrapper path explicitly, so the **absence of a
coverage claim is not read as a clean one**. (Not "a four-node denominator" — there is none on this
path; see D-MA1b.)

### Acceptance MA-1

1. A processor added via `EventProcessorConfig.builder().customHandler(...)`, audit level INFO, writes
   records **carrying `nodeLogs` entries** into its Chronicle sink.
2. `getAuditorById("eventLogger")` resolves on that processor.
3. `POST …/audit/level` demonstrably changes what is written — a level below the call suppresses it, a
   level at or above emits it. The endpoint returning 200 is not acceptance; the bytes are.
4. Records carry the handler's node entries, **and the analyser reports no coverage claim rather than a
   partial one**. The earlier wording — "a non-empty coverage denominator" — was unmeetable: coverage
   comes from the paired graph and there is no graph on this path. Its `NO_NODE_LOGS` clause also passed
   vacuously on an empty log.
5. A regression check that fails if the auditor stops being installed — per CLAUDE.md rule 8, the finding
   is not closed until the check that would catch it next time exists.

## MA-2 · The marker writer — blocked on MA-0, not on MA-1

Mongoose writes the text audit file directly, ending with a stream-end marker, so a reader can tell a
whole log from a truncated one. This is tracker AF-4 item 1. **Blocked on MA-0** — an empty log must be
reported as such before a marker starts labelling empty logs `complete`.

### D-MA2 · Per-node entry parity, not a record count

The marker declares what was written. §1a's count is over **records**, but the thing worth checking is
that each record's node entries survived — a record count alone passes while every record is empty.

**Parity CANNOT travel in the marker, and review corrected this.** §1a's recognition rule admits only
`streamEnd`, `streamEndRecords` and `logTime`; **any other key makes the document an ordinary record**.
A per-node count would therefore need a format change — the stream-end spec's own open question 2 already
records that a second count is one. So parity is an **acceptance-time comparison** — the handler's ground
truth against the reader's parse — not something the writer declares or a runtime reader can check.
Stated explicitly because an implementer would otherwise try to put it in the marker.

### D-MA3 · Config validation refuses unknown values by name

A `streamEnd` value the writer does not recognise is refused at configuration time, naming the value and
the accepted set, rather than written and left for a reader to interpret.

### D-MA4 · The separator is not this writer's job

Settled and shipped: it belongs to the export formatter, done in `1.0.44`. A marker writer that tries to
terminate its own document is writing someone else's byte.

### Acceptance MA-2

1. An export carrying a marker reads **`complete`** in the released analyser, with `declaredRecords`
   equal to `recordsRead` **and `recordsRead > 0`, those records carrying node entries**. The bare
   version of this acceptance passes on an empty log, which is the case MA-0 exists for.
2. An export **without** one reads exactly as it does today.
3. A run killed **before the marker's terminating separator is flushed** reads `unterminated_marker` or
   `unknown` — never `complete`. Scoped deliberately: killed before any record → `unknown`; mid-record →
   `unknown`; mid-marker → `unterminated_marker`; **after the separator is flushed → `complete`, which is
   correct**. An unscoped "never `complete`" is false for the one case where `complete` is right.
4. **Byte-identical to a known-good export modulo the marker and the final separator.** The original
   acceptance said "modulo the marker" alone; §1a rule 1 makes that impossible, because a known-good
   export has no trailing separator. Recorded because the withdrawn version is still quoted in places.
5. Verified against the **published** analyser jar, by digest, not a local build.

## MA-4 · Default-off and undocumented — added 2026-09-23, and the spec was incomplete without it

Asked by the owner: *does Mongoose log with text in the developer download by default?*

**First, a correction to an earlier answer in this spec, because the owner was right to push back.**
Mongoose **does** audit-log by default. `MongooseServer` installs its own `LogRecordListener` at boot —
a static lambda routing records to its `java.util.logging` logger — and passes it to
`DataFlow.setAuditLogProcessor` for every processor. Core's own code uses `auditLog` (for example
`BatchDtoHandler`). So a developer running an example **does** see audit output, and examples can rely
on it. An earlier draft of this section said capture was "off entirely", which conflated **logging**
with **persistence**. They are different, and only the second is off.

**What is actually missing is a file the analyser can open.**

| | State today | Fixed by |
| --- | --- | --- |
| Audit records reaching a listener | **ON by default** — `MongooseServer`'s JUL listener — **but only while persistence is OFF**, see MA-5 | already works |
| A processor built from a `customHandler` | produces **nothing** for that listener to receive | MA-1 |
| `AuditCaptureConfig.enabled` — persistence | **`false`**; without it nothing is retained | nothing in this spec |
| What persistence writes when on | a **Chronicle binary queue** at `./audit`; the record text is the excerpt payload, the container is not text | MA-2 |
| How analyser-readable text is obtained | only `GET /api/audit/file/{id}/export?format=yaml`, which needs `svc-admin-web` — not core | MA-2 |
| Developer examples | **no example in mongoose CORE** enables persistence — but the **playground's `analyser-bundle` template, which IS the developer download, ships `auditCapture: enabled: true`**. Scope corrected after review. | partly decided already |
| Core documentation | `auditCapture` appears in no `docs/*.md`; it does appear once in `design-doc/backpressure-and-slow-consumer-handling.md` | nothing in this spec |

**Persistence REPLACES logging; it does not add to it.** The table above first presented the two as
additive. They are alternatives — see MA-5, which review found and I confirmed in the source.

**So the developer journey breaks at persistence, not at logging.** Records go to the console, where they
cannot be opened, compared, or reasoned about by the analyser — and the moment the process exits they are
gone. That is the gap, and it is narrower and more fixable than the earlier draft implied.

**This is the "ships and does nothing" failure again**, in a new place. UP-MON-01 was worth doing only
because something would eventually write a marker; MA-1 and MA-2 are worth doing only if a developer
can reach the result. A capability that is off by default, undocumented, and requires a separate plugin
to read is one almost nobody will find.

## MA-5 · Capture REPLACES the console listener, and stopping it discards audit entirely

**Found by review, confirmed in the source.** `ChronicleAuditCaptureService` carries a comment promising
behaviour its code does not implement:

> *"Compose the capture listener IN FRONT of whatever listener mongoose already installed … we wrap our
> listener with a delegate that fans to the previous one if present. The previous listener is what we'll
> restore in stopRecording."*

The code immediately below sets `this.previousListener = null` and calls
`setAuditLogProcessor(captureListener)`. So:

- **MA-5a — capture replaces the console listener.** Turning persistence on silently stops audit records
  reaching the console. Observed on a template run: 5 records at startup, then none, while events flowed
  to Chronicle.
- **MA-5b — `stopRecording()` installs a no-op**, not the previous listener, because the previous
  listener was never kept. After capture is stopped — through the admin API, say — audit output goes to
  **neither the console nor a file** until the server restarts.

**MA-5b is this spec's own defect class**: a live processor, auditing apparently on, output silently
discarded. It is the same shape as the audit-tail socket that connected, reported healthy and delivered
nothing.

**The fix is what the comment already describes:** keep the previous listener, fan out to it while
capturing, restore it on stop. Small, and it removes a silent-discard path.

### Acceptance MA-5

1. With capture on, a record reaches **both** the Chronicle queue and the previously installed listener.
2. After `stopRecording()`, records reach the listener that was installed before capture started — not a
   no-op, and without a restart.
3. A regression check for each, since both failures are silent.

### D-MA5 · Making it work is not the same as making it reachable, and the spec must say which it is doing

MA-1 and MA-2 make audit logging **possible and trustworthy**. They do not make it **on**, **discoverable**
or **documented**. Those are a product decision, not a consequence.

**OD-4 — DECIDED 2026-09-23: TEXT as the developer default, CHRONICLE when deployed.** The developer
download writes analyser-readable text directly, so a new user can open their audit log without the
export endpoint, a plugin, or knowing `auditCapture` exists. Deployed configurations keep Chronicle,
which is what the throughput path needs.

**This makes MA-2 load-bearing for the developer journey**, not merely for completeness claims: text as
the default *is* MA-2, Mongoose writing the text file directly. Until MA-2 ships the developer default
cannot change, so **MA-4 is gated on MA-2** rather than just improved by it.

**Still open under OD-4:** whether core's own examples match the playground's persistence setting, and
where `auditCapture` is documented.

The original framing, kept for the record:

- **Default persistence on?** Partly decided already: the developer download ships it **on**, as
  Chronicle, relying on `svc-admin-web`'s export. The live question is **text versus Chronicle** by
  default, and whether core's own examples should match the playground. Recording costs — `UP-FLX-51` measured ~120 ns/event for a manager recording nothing —
  so "on for everyone" is a real choice, not a free one. "On in the developer/example configuration,
  off in production defaults" is the obvious middle and should be considered explicitly.
- **Text or Chronicle by default?** MA-2 makes direct text possible. Text is readable by the analyser
  with no plugin; Chronicle is faster and needs the export endpoint.
- **Documented where?** `auditCapture` is absent from core's docs entirely. At minimum one page, and
  one example config that turns it on.

### Acceptance MA-4

A developer who downloads Mongoose and follows the getting-started path **ends up with an audit log they
can open in the analyser**, without knowing that `auditCapture` exists. If that journey still requires
prior knowledge, MA-1 and MA-2 have not delivered a user-visible capability.

## MA-3 · MOVED to `mongoose-plugins#38`

Review is right that it belongs there: it shares no code and no ordering with the rest of this spec. The
acceptance below stays as the pointer; the work is tracked in the issue.

### The pointer

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

- ~~The per-node `NONE` corruption~~ — **WRONG, and moved in-scope as a dependency.** I wrote that its
  description could not be found. It is **AFMT-3**, in `docs/specs/tracker.md` (under *Audit format*) and
  in `docs/proposals/mongoose-audit-format/README.md` §§102-106 and 267-271, **with a repro**
  (`…/audit-format-review-2026-09-21/rev5/LevelTest3.java`): after
  `EventLogControlEvent(sourceId, null, NONE)` the next record keeps its values but loses its header,
  keys and newlines, for every node. Cause undiagnosed. It is a **dependency** of this spec, not adjacent
  to it: MA-1.3 drives levels through the same control event, and OD-1's `NONE` default touches the same
  path. See the Ordering block.
- **AF-6, the coupled analyser documents.** Blocked on MA-2 shipping, by design: until a marker is
  written, the statement that Mongoose does not write analyser-readable text directly is still true.
- **Binary audit encoding**, `spec-binary-audit-encoding.md`.

## Ordering

**The ordering block lives once, at the top of this spec**, under *"The measurement, and the argument I
built on it — WITHDRAWN after review"*. A second copy stood here and still carried the **withdrawn**
ordering — MA-2 blocked on MA-1, no MA-0, no MA-5, MA-3 in scope — so an implementer skimming to the end
would have got exactly the ordering this spec retracted. Deleted rather than duplicated: two copies of an
ordering is how the stale one survives.

The only downstream item not in that block: **AF-6**, the coupled analyser documents, blocked on MA-2
shipping and tracked in the tracker rather than here.

## What is verified, and what is only read

**Review, 2026-09-23.** Two rounds, both CHANGES REQUIRED, both now answered. The first review
(`review_mongoose_audit_production_2026_09_23_claude.md`) **overturned this spec's central argument** —
`complete` is a true container claim, and MA-1 does not close the empty-log hole. The second
(`..._r2_...`) found MA-5. Both are committed beside this spec; the first sat unread in the reviewer's
worktree for a round because **my brief told them to leave it uncommitted**, which is the wrong
instruction for a handoff review and is corrected in the brief.

**Verified by running, 2026-09-23:** the empty-but-claimed-complete measurement against the published
1.18.0 jar (sha256 `5a8c2a4f070ad06a7804894391b5660d3fe160c14d6f382ddf2ddff3f79a2f02`); the zero-record
emission on a booted `MongooseServer`; the `getAuditorById` failure; the c21 counts of 25/7/18.

**Read, not run:** `DefaultEventProcessor`'s declared fields (via `javap` on
`fluxtion-runtime-1.0.15.jar`); `EventProcessorConfig.getEventHandler()` and `ConfigAwareEventProcessor`
source on `develop` `17a03b4`.

**Verified by running after review:** that a correctly audited processor at a quiet level writes an
effectively empty log — the same handler gave **5 records at DEBUG and 1 at WARN**, which is what
withdrew the ordering constraint. AFMT-3's existence and location. MA-5a/MA-5b in the source.

**Not established:** the per-event cost of an
`EventLogManager` after `UP-FLX-51`; whether any AOT path in Mongoose is also affected; **AFMT-3's
cause**, which is now a dependency rather than out of scope.
