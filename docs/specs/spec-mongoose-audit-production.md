# Mongoose produces an audit log the analyser can trust (Design Spec)

_Status: **PROPOSED 2026-09-23, not started.** Five reviews, all CHANGES REQUIRED, all answered; review
files and evidence are in [`docs/handoff/`](../handoff/). Rewritten at rounds 3 and 4 rather than patched
— this document shipped **six** add-without-remove defects, so each revision replaces the text it
supersedes in the same edit, and there is deliberately **no appendix**._

_**Open owner decision: OD-5**, whether the Chronicle backend gets a marker. It blocks **only MA-2's
Chronicle half**; the text writer the developer journey depends on proceeds without it. **Decided:**
OD-2 (refuse), OD-4 (text for developers via the configured listener, Chronicle deployed), the flush
policy (a user-configurable property, with `auditText` as its home), MA-2's framing home — **which must
carry the READER's trimmed separator predicate, not an exact match**. **Moot:** OD-1, OD-3._

_Replaces the scattered record: tracker AF-4, `UP-MON-02` in [upstream asks](../proposals/upstream-asks.md),
[mongoose-plugins#38](https://github.com/telaminai/mongoose-plugins/issues/38). The reader half shipped in
analyser **1.18.0**, the exporter half in `mongoose-plugins` **1.0.44**. Implements the producer side of
[audit stream end](spec-audit-stream-end.md) §1a. Related: D-T8 in
[trust structure](spec-trust-structure.md) — never conceal a gap._

## The problem

The analyser's central claim is that **absence is evidence**: a node that never logged is a finding, and
a log that cannot say whether it is whole reads `unknown` rather than being guessed at. Both depend on
the producer, and Mongoose holds up neither end.

1. **A processor with no `EventLogManager` emits nothing, and everything reports success.** The capture
   sink is created and says it is recording; `POST …/audit/level` returns 200; the file stays empty.
   **Measured on a booted server** with an unaudited AOT processor and capture on: listed with
   `recordCount: 0` holding only `metadata.cq4t`; `POST /api/audit/{p}/start` answers
   `{"recording": true}` **200**; `POST …/audit/level` answers **200**.
2. **Nothing writes a stream-end marker**, so every export reads `unknown` and the 1.18.0 and 1.0.44
   work delivers nothing a user sees.
3. **An empty log raises no finding at all** — the analyser's own gap, and the cheapest to close.

## What is NOT the problem — corrected twice, at cost

**Not `customHandler`.** An earlier draft treated the wrapper path as central. It is an edge case, used
only to run Mongoose without a Fluxtion event processor. Verified by downloading a public
`analyser-bundle` (`/start/scaffold?template=analyser-bundle`): it contains **no** `customHandler`; its
`marketProcessor` is an AOT `MarketProcessor` declaring `eventLogger`, wiring its clock, calling
`initialiseAuditor(eventLogger)` and registering the user's own `riskCheck` and `rootNode`.

**Pinning that evidence — a digest alone is not enough, measured.** Two effects, both real:

- **The zip digest moves without the content moving.** Two downloads with identical parameters, hours
  apart, produced byte-identical extracted trees and different zip digests at the same size — the
  container carries fresh entry timestamps. Back-to-back downloads do match, so it is timestamp
  granularity.
- **The content moves with the request parameters.** `group`/`artifact`/`basePackage` are substituted
  into every generated file, so they change both size and content digest. `com.example/demo` gives
  64,609 bytes where `com.acme/audit-check` gives 65,364. This is why round 4's digests differ from
  this one's — different parameters, not a changed template.

**So pin the digest WITH its parameters and date.** For
`group=com.acme&artifact=audit-check&basePackage=com.acme.auditcheck`, 2026-09-23:
`generated/MarketProcessor.java` = `6745794596512197828c87bc6a0fdb42e61b9646de58a36ec194f46cec27ed04`;
tree hash (`find . -type f | LC_ALL=C sort | xargs shasum -a 256 | shasum -a 256`) =
`3629ef6c6a84531cfe0098e51b9f793b77a5c37483c0f1cc25c2fe4a65f507a0`.

**Not "the wrapper path" either.** The population that gets silence is **any processor without an
`EventLogManager`**, which includes **AOT processors built without audit** — the low-latency profile, a
supported configuration rather than an edge case. MA-1 keys on the capability, not the construction
route.

**`complete` is not a false verdict.** It is a true container claim: the marker's count matches what was
read. An earlier draft called it false and built an ordering requirement on it. Both withdrawn. The harm
is the **missing empty-log finding**, which is MA-0.

## Ordering

```
MA-0  (analyser: an empty log is a finding)      ← THIS repo; smallest; closes a live gap
MA-1  (a processor that cannot audit says so)    ← independent
MA-5  (capture must fan out, and restore)        ← startable now, but ON THE TEXT WRITER'S CRITICAL PATH
MA-6  (a document without eventLogRecord: )      ← independent; unblocks MA-2 without AFMT-3
MA-7  (framing injection — a payload forges a marker) ← GATES MA-2; V1
MA-8  (coverage qualifies a per-node level)      ← THIS repo; MA-0 sibling
  └── MA-2 TEXT writer                           ← needs MA-5, MA-6 and MA-7; NOT blocked by AFMT-3/OD-5
        └── MA-4  (defaults, docs, the journey)  ← GATED on the text writer
              └── AF-6 (the coupled documents)   ← in the tracker, not here
  └── MA-2 CHRONICLE marker                      ← needs OD-5 decided
AFMT-3 (per-node NONE corrupts the record)       ← a live runtime defect; MA-6 defends against it
MA-3  → moved to mongoose-plugins#38
```

MA-0, MA-1, MA-5, MA-6, MA-7 and MA-8 can start now, in any order.

---

## Validity invariants

Four checks every MA item's acceptance must pass. They were proposed as the test any signal here should
face: **what would have to be true for "this node never ran" to be wrong, and does the analyser say so?**
Stated once, here; items point at them rather than restating them.

**V1 · A payload cannot change the verdict.** Nothing an event or node value carries may alter the
record count, the framing, the stream-end state, **or any field value, node entry or coverage result**.
The parser is indentation-insensitive and content lines have been read as fields before, so a payload
reaching field values would move coverage — "this node never ran", in the other direction. **Live
violation: MA-7.** *(That a payload can reach field values is a stated gap: untested by either side.)*

**V2 · The stream-end STATE and every finding agree at every cut**, including the bare-marker-header
cut; **the record count may differ only by the one pending last document.** Counts legitimately disagree
there by the reader's own contract — a cold open reads the marker's lone `eventLogRecord:` line as a
record, while Follow holds an unterminated last document pending — so an earlier wording demanding the
counts agree contradicted §1. Asserted where the cut is, in MA-2.3, with Follow driven through the real
store.

**V3 · Absence never becomes a claim.** Not knowing must read as `unknown` or as a finding, never as
`complete`. This is D-T8 applied to this spec, and it is why OD-5 (c) is rejected.

**V4 · Every verdict is stated in its own scope.** MA-4's acceptance means **one run's file**; a
directory of files is `unknown` by design (D-E5). An empty file is a fact about **the file**, not the
run. `complete` means "every record this processor produced is here", never "no event was dropped".

---

## MA-0 · The analyser reports an empty log as a finding

**In this repository.** Today every empty shape returns from `ProducerDiagnostics` before any check, so
an empty log raises nothing — marked or not. A marker only changes the label from `unknown` to
`complete`; what makes it look healthy is the missing warning.

### D-MA0a · A finding, never a seventh state

The six states are a published contract (§1a, D-E3) that three store paths and `context` agree on. The
empty-log signal is a **`ProducerDiagnostics` finding beside an unchanged state**.

### D-MA0b · Key it on zero records only

The quiet-level case — a processor whose level is above its calls — is **already** caught by
`ONLY_CONTROL_EVENTS`, because the one record present is the control event. MA-0 needs only
`index.size() == 0`, placed before the early return. **A document missing `eventLogRecord:` is MA-6, not
MA-0**, so this stays narrow.

### D-MA0c · The report is a NEW surface, and that is MA-0's cost

`ReportRenderer` carries no producer findings today. Routing the empty-log finding there means routing
**all** producer findings there. That is the decision taken — findings that matter on the status bar
matter in the report — and it is part of MA-0's cost, not a free surface.

### D-MA0d · In Follow, say "no records in the file yet"

**V4 applied.** The spike measured a buffered writer holding **0 bytes** while ~24 records sat in memory,
so the wording must be about the file. MA-0's acceptance also carries **V2**: the finding must read the
same in Follow as on a cold open.

### Acceptance MA-0

1. Each of these raises the empty-log finding as a **warning**, with the stream-end state **unchanged**:
   a marker declaring 0 and nothing else; today's empty export; zero bytes; whitespace only; two empty
   marked segments; a rolled set whose members are all empty.
2. It appears on the status bar, in the tooltip, in `context`, and in the report (per D-MA0c).
3. **Two separate cases, not one:** five records without node entries **marked** (C), and the same
   **unmarked** (D). `NO_NODE_LOGS` still fires alone on both; a healthy marked file stays clean.
4. **Tests assert via `firstWarning()`, not `isWarning()`.** `isWarning()` reads only `get(0)`, which is
   a `COMPLETENESS_NOTE` for a marked rolled set, so the assertion would pass for the wrong reason.
5. In Follow, an empty file opened before its first record shows the finding, and it clears on every
   surface when a record arrives. Assert both surfaces.
6. A file whose zero records come from `SOURCE_DAMAGE` says both, **damage first**.
7. Each case is a conformance fixture with a mutation witness.

---

## MA-1 · A processor that cannot audit says so

The discriminator is the **capability**, not the construction route.

### D-MA1a · Detect with `getAuditorById("eventLogger")` — VERIFIED BY RUNNING

It resolves on the bundle's `MarketProcessor` and throws `NoSuchFieldException` on a current-generator
AOT processor built without audit and on `DefaultEventProcessor`. The unaudited population is **common**:
130 of 158 local generated processors declare no `eventLogger` (a wider local count gave 162 of 207;
same conclusion).

### D-MA1b · Refuse at start; `autoStart` logs and continues

`start()` runs inside the processor-registration lambda, so **a throw there fails registration**. Refusing
`autoStart` by throwing would take down a server for an unaudited processor. So:

- **`autoStart`** — logs loudly and continues; the sink is not started.
- **The explicit triggers** — refuse.

### D-MA1c · An escape hatch, because "safe direction" holds only for a warning

A processor with a **custom** auditor is refused wrongly (`AuditReadiness` N1). For a warning that is
tolerable; for a refusal it removes persistence from a processor that works. **Required:** a
per-processor allow-list that forces capture on, or the ability to downgrade the refusal to a loud
warning. Name it in the config.

### Acceptance MA-1

1. Four triggers behave as D-MA1b says for a processor with no `EventLogManager`:
   `autoStart` (log and continue), the `audit.start` admin command,
   `MongooseAuditCaptureService.start(name)`, and **`POST /api/audit/{processor}/start`**.
2. That endpoint maps `IllegalArgumentException` to **404**, so a naive refusal would report an existing
   processor as "not found". The refusal must surface as **409** — naming the exception type it throws
   and the REST mapping that carries it — for **both** that endpoint and `POST …/audit/level`.
3. The processor is **never listed as recording** in `liveSinks`. It may still appear in the admin file
   list, which reads the directory and can hold files from an earlier audited build; that is expected
   and must not be asserted away.
4. The escape hatch of D-MA1c restores capture without a rebuild.
5. A regression check for each; all four failures are currently silent.

---

## MA-5 · Capture must fan out, and restore on stop

`ChronicleAuditCaptureService`'s comment says the capture listener is composed in front of the existing
one and restored on stop. The code sets `previousListener = null` and, on stop, installs a no-op.

- **MA-5a — capture REPLACES the configured listener.** Persistence silently stops audit reaching the
  console.
- **MA-5b — `stopRecording()` installs a no-op**, so after stopping, audit reaches neither console nor
  file until restart.

### D-MA5a · Capture at `attach`, per processor

`DataFlow` has no getter for the current listener. The one available mechanism is **the server's
configured listener** — `MongooseServer.logRecordListener`. It is `private static` and **overwritten by
each `bootServer` call**, so two servers in one JVM share it. **Capture it at `attach`, per processor,
and never re-read the static at stop.**

### D-MA5b · Isolation must report, not swallow

A destination that fails must be **counted as received and logged**. Isolation that swallows write
errors reproduces MA-5b on the text backend, and leaves the marker claiming records the file never got —
the count must make that read `missing_records`.

### Acceptance MA-5

1. With capture on, a record reaches **both** the Chronicle queue and the server's configured listener.
2. After `stopRecording()`, records reach that listener again — not a no-op, without a restart.
3. **start → stop → start**: no double wrapping; each record reaches each destination exactly once.
4. **Re-registration while recording — MEASURED, and the silent path is real.** After `stopProcessor`
   and a re-add, the new instance's events reached the configured listener but the capture's
   `recordCount` stayed at 23, the export held none of them, and `start` still answered
   `"recording": true`. Assert the new instance's records reach the capture file. **Name the re-add
   path**: `addEventProcessor` on a *running* server does **not** call `init()` — the first re-add threw
   `init() must be called before start()` and the processor never ran, while the configuration path does
   call it. Whether that is itself a Mongoose defect is an owner call, recorded here because MA-5.4's
   acceptance depends on which path it uses.
5. **Isolation**: a listener that throws does not stop the other destination — and the failure is
   counted and logged, per D-MA5b.
6. Two servers in one JVM each restore their own listener.
7. Fan-out and restore are a contract of `MongooseAuditCaptureService`, tested for every backend.

---

## MA-6 · A document without `eventLogRecord:` is named

**Split out of the AFMT-3 gate, and this resolves the contradiction round 4 found.** D-MA0b keeps MA-0
at zero records; the check for a document missing the recognising key lives here.

**Why it is its own item.** It defends against AFMT-3's output without waiting on AFMT-3's undiagnosed
cause, and it is not specific to AFMT-3 — any producer emitting a document without the key gets named.

**Two halves:**

- **Reader half (this repo):** a document carrying no `eventLogRecord:` key raises a finding.
- **Writer half (MA-2):** the writer **writes the record, counts it, and withholds the marker.** Round 5
  showed the earlier "refuses to count or mark" had three readings and two break an invariant: writing
  but not counting reads `more_than_declared` — damage claimed; not writing and not counting reads
  `complete` with a produced record missing — breaking V4. Only write-count-withhold is honest, and it
  yields `unknown` plus the reader finding.

### Acceptance MA-6

1. A file of good record, AFMT-3 record, good record, with a marker declaring 3, raises the finding.
2. With the writer half, such a run reads **`unknown`** — the record is present and counted, and no
   marker is written.
3. A conformance fixture with a mutation witness.

---

## MA-7 · Framing injection — a payload forges a marker. GATES MA-2

**V1's live violation, and the most serious finding in this spec.** The runtime writes `eventToString`
**unescaped**. An event whose `toString()` contains a line that **trims to** `---` (see the predicate below), followed by marker
lines, **breaks the framing before recognition runs**, so §1a's allow-list cannot defend against it —
the allow-list protects a record that *mentions* a marker key, not one that *terminates the document*.

**Reproduced independently, twice.** Review framed 3 real records as the 1.0.44 exporter does, plus a
marker declaring 3, with one hostile payload: the analyser read **5 records**, recognised the injected
marker, and reported damage on a file that lost nothing. My own payload gave **4 records** from 3, split
into **two runs**, `missing_records`, with a `more_than_declared` run whose `declaredRecords` was the
injected **0**. Different shapes, same three effects: **a record split, a marker forged, damage claimed
where nothing was lost.**

**Reach.** Every text sink today — including the **shipped 1.0.44 exporter**, whose `YamlContainerWriter`
writes the record text as-is — and MA-2's writer, wherever it lives. Payloads come from event
`toString()`, which is routinely user-controlled: symbols, messages, parsed input.

**Not recorded anywhere before this**: no tracker item, no spec, nothing on escaping, injection or
framing.

### The predicate is the READER's, not "a line that is exactly `---`"

Both framers split on any line that, **after trimming space, tab and CR**, is `---`. So `  ---`,
`\t---` and `---\r` all separate, and a writer checking for an exact match lets all three through.
**Since D-MA2b moves framing to core, core carries that one predicate and cites §1 for it.**

### ESCAPE, never refuse

**Refusing cannot satisfy V1**, which requires the count *and* the verdict unchanged: count the refused
record and the file reads `missing_records` — the payload changed the verdict; don't count it and the
record vanishes under a `complete` marker — breaking V4. **Only escaping** — altering the line so it no
longer trims to `---`, and keeping the record — meets V1. **The escape changes the logged value, and
that is the stated price of V1.** An earlier "refuses or escapes" is withdrawn.

### Both fixes, not one — decided without waiting on the open question

- **The writer escapes.** MA-2's day-one defence.
- **A producer-side escape is MANDATORY for any path through the shipped 1.0.44 exporter** — runtime, or
  a patched exporter release. A writer fix cannot reach bytes already shipped, the reader cannot tell a
  real marker from a forged one, and **neither side can prove no forged-`complete` payload exists**. On
  V3, not knowing whether an attack exists is a finding, never a clearance — so this is decided **as if
  the answer were yes**. **FILED** for the existing exporter as
  [mongoose-plugins#39](https://github.com/telaminai/mongoose-plugins/issues/39), outside this spec.

**Recorded as open:** no payload making a log read `complete` falsely has been constructed; neither side
tried to prove none exists. The decision above stands in spite of it, not because of it.

### Acceptance MA-7

1. **Hostile payload:** a record whose `eventToString` carries a separator line plus marker lines leaves
   **the record count and the verdict unchanged** (V1). Fixtures include the **indented (`  ---`), tab
   and CR (`---\r`) variants**, not only the bare line.
2. The same for a node value, and **coverage is unchanged too**, not only the count (V1's extension).
3. A conformance fixture with a mutation witness.

---

## MA-8 · Coverage qualifies a node whose level was changed per node

**An MA-0 sibling, analyser side.** A node set to `WARN` **runs**, but reads as never logged — and the
log carries the evidence.

**Measured:** with `riskCheck` at `WARN`, the file reads `complete`, 6 of 6, no findings, and **zero**
`riskCheck` entries, while `riskCheck` ran three times. **The control record naming
`sourceId=riskCheck, level=WARN` is in the log.**

Coverage reads only record levels and node entries; `AuditLevel` names a **global** level as a cause of a
coverage gap and does not look at a **per-node** change the log states outright. So coverage lists
`riskCheck` as uncovered with no qualification — which is precisely "this node never ran" being wrong,
with the answer sitting in the file.

### Acceptance MA-8

1. When a control record names a `sourceId`, coverage **annotates that node** — its level was set to
   `WARN` at *t*, and lines below that level are not in this log.
2. **Annotate, never excuse.** The node **stays in `uncovered` and in the ratio**, with the note.
   Excusing it would hide a node that never ran if the qualifying record is wrong — and MA-7's injection
   means a control-*looking* record can be content until every writer escapes.
3. **Filter scope:** level changes are configuration state, consulted **regardless of the current
   filter**, up to the scope's end. A time or type filter that excludes the control record must not drop
   the annotation.
4. **Intervals:** a node set to `WARN` and later restored is annotated **only between the two changes**;
   silence outside that window is plain uncovered.
5. **What is parsed:** key on the record's `event` being `EventLogControlEvent` and parse
   `sourceId`/`level` — **the runtime's `toString` format is not a contract**, so the fixture is pinned
   to the runtime version. **`groupId` IS in scope**, treated exactly as `sourceId`: it targets nodes and
   produces the same silence. Where the log alone does not let a `groupId` be mapped to its nodes, the
   annotation is made **at the group level**, naming the limitation rather than dropping it.
6. A conformance fixture. *(The admin endpoint is global-only, so this is reachable from Java alone —
   which does not make it rare in hand-tuned deployments.)*

---

## AFMT-3 · Per-node `NONE` corrupts the record — a live runtime defect

**Reproduced on today's download**, against the bundle's `fluxtion-runtime-1.0.16`: setting `riskCheck`
to `NONE` turns the next record into `mainonPriceEventPriceEvent{…}195.31200` — no header, no keys,
values run together. `rootNode` `NONE` gives `mainonRiskCheck`. Global `NONE`, per-node
`ERROR`/`WARN`/`DEBUG` and unknown-node `NONE` are all fine. **The defect is per-node `NONE` on a
registered node, and only that.**

**Why it matters here**, on the published 1.19.0 jar: a file of good record, AFMT-3 record, good record,
with a marker declaring 3, reads `{complete, recordsRead: 3, declaredRecords: 3}` **with no finding**.

**The corrupt text is what the sink receives, so every listener gets it** — JUL, Chronicle and a text
writer alike. *(The cause is NOT established; an earlier draft asserted it was in the record encoder,
which the same document also called undiagnosed.)*

**The tracker's repro is stale**: `LevelTest3.java` targets `volumeTotal`, absent from today's bundle, so
re-running it shows every case clean and AFMT-3 looks fixed. Retarget at `riskCheck`/`rootNode`.

**Reach:** the admin endpoint sets levels globally only, so a REST user cannot trigger it; Java code can,
via `DataFlow.setAuditLogLevel(level, sourceId)`.

**MA-2 is no longer gated on AFMT-3** — MA-6's writer half is the defence. AFMT-3 remains a live runtime
defect worth fixing, and `UP-FLX-51`'s `NONE` default must not ship onto this path before it is.

---

## MA-2 · The text writer and the marker

### D-MA2a · The separator rule splits by backend

- **Chronicle** — the 1.0.44 exporter writes the separators. Unchanged.
- **Text** — the writer owns **every** `---` between records **and** the one after the marker (§1a rule
  1), because OD-4 removes the exporter from that path. `ProducerDiagnostics`' `UNSEPARATED` message says
  the sink must `append("---\n")`.

### D-MA2b · Where the framing lives — DECIDED: move it to core

`YamlContainerWriter` is package-private in `svc-admin-web`, and **core cannot depend on a plugin**.
Move the framing rule to core and have the plugin use it. The alternative — two copies held together by
MA-2.4's parity test — is rejected: this document has shipped six duplicate-text defects, and a
duplicated framing rule is the same failure in code.

### D-MA2c · The marker's lifecycle — ANSWERED, from the spike

| Question | **Answer** |
| --- | --- |
| **When** | In a shutdown hook, **after `server.stop()` returns**, and on `stopRecording`. The marker **depends on a completed stop**: measured at 4–6 ms normally but **over 30 s** behind a large backlog, and a supervisor's `SIGKILL` at a grace deadline cuts it off — the file then reads `unknown`, which is honest |
| **Thread** | A lock is **necessary but not sufficient**; the **order** decides it. Measured: with the marker written *before* the processors stopped, 39 and then 20 records arrived afterwards and the file still read **`complete`** — self-consistent, so the analyser cannot detect it. **Required:** write the marker only after the processors stop, **and** count and log any record arriving after it as an error |
| **Restart** | **A new file per start.** Verified: each file reads `complete`; concatenated, `complete` as two segments |
| **Roll** | **None for the text backend in the developer profile** |
| **Retention** | `retainHours` removes **whole files only, never the open one** |
| **Counted when** | **Records RECEIVED, before the write.** Chronicle's `onRecord` counts after a successful write, which hides a swallowed write failure — the loss the count exists to catch |
| **Flush** | **A user-configurable property (owner decision).** Default **flush-per-record in the developer bundle**, because Follow is part of that journey; buffering available for deployments |

**What buffering costs, measured:** a buffered (8 KB) file held **0 bytes** one second after boot while
~24 records sat in memory. So Follow shows nothing until the buffer fills; once MA-0 ships, a buffered
live file opened early raises the empty-log finding while records exist in memory (hence D-MA0d); and
`kill -9` loses the buffered tail — still `unknown`, so honest.

**One thing no marker can see (V4).** Events Mongoose drops upstream — `dropping publish to
slow/contended queue` — never reach a processor, so no marker can account for them.

### D-MA2d · Per-node parity is an acceptance-time comparison

§1a admits only `streamEnd`, `streamEndRecords` and `logTime`; any other key makes the document an
ordinary record. A per-node count cannot travel in the marker without a format change.

### D-MA2e · `backend` must stop being a silent no-op

`getBackend()` has no caller in core, so `backend: text` is accepted and ignored today, and so is a typo.
**Under OD-4 text is not a capture backend at all**, so `backend` accepts only `chronicle`, and
`backend: text` is **refused with a message pointing at the text writer's own configuration**.

**That configuration needs a home, and had none.** The bundle boots from YAML (`bootServer(reader)`)
while `bootServer(config, listener)` is programmatic, so nothing named how YAML turns the text writer
on, where its directory goes, or where the owner-decided flush property lives — **a user-configurable
property with no configuration key is not configurable**.

**Decided: the block is `auditText: {enabled, directory, flush}`**, a sibling of `auditCapture`.
`flush` defaults to per-record in the developer bundle (D-MA2c). **Its interaction with `auditCapture`
is the MA-5 dependency**: with both enabled and capture still replacing the listener, the text writer
sees only pre-registration records, so Mongoose warns loudly until MA-5 lands.

### Acceptance MA-2

1. A text file carrying a marker reads **`complete`** in the released analyser, `declaredRecords` equal
   to `recordsRead`, **`recordsRead > 0`**, records carrying node entries — **asserted with the
   developer bundle's ACTUAL configuration, capture included**. *(The spike's `complete`, 29 of 29 held
   only with capture **disabled**; that condition was unstated, and with the shipped configuration the
   claim was false. See OD-4.)*
2. A text file whose writer never reached stop reads `unknown`; MA-0 fires if the file is empty.
3. **Kill points**, corrected by measurement: before any record → `unknown`; between records → `unknown`;
   mid-record → `unknown`; **just after the marker's `eventLogRecord:` line → `unknown`, with a phantom
   extra record — NOT `unterminated_marker`**; mid-marker after its recognising key →
   `unterminated_marker`; after the marker's separator is flushed → `complete`, correctly.
   **"Stop under load" asserts against a producer-side emitted count, not the file** — the file is
   self-consistent in the failure case.
   **V2 is asserted here**, at the bare-marker-header cut, with Follow driven through the real store:
   the stream-end **state and every finding** agree between a live tail and a cold open, and the record
   count differs only by the one pending last document.
4. **Parity, in ONE process.** One record sequence goes to both writers; the text file must equal the
   export **plus the marker plus `---\n`**. Measured: Chronicle round-trips each record exactly and the
   framing reproduces byte for byte. *("The same run" is impossible — a run has one backend, and a
   second run differs in timestamps.)*
5. The writer **writes and counts a record lacking `eventLogRecord:` and withholds the marker**, so the
   file reads `unknown` (MA-6's writer half), and **escapes — never refuses — a record carrying a line
   that trims to `---`** (MA-7's writer half).
6. An unknown `backend` is refused by name (D-MA2e).
7. Verified against the **published** analyser jar.
8. **With `auditText` and a still-replacing `auditCapture` both enabled, Mongoose warns loudly** —
   asserted, not assumed. Until MA-5 lands this is the only thing standing between a developer and a
   `complete` file holding none of their events (F1).

### OD-5 — OPEN · Does the Chronicle backend get a marker?

Blocks **only MA-2's Chronicle half**. The text writer proceeds without it.

- **(a) Capture appends a marker excerpt at stop**, which the 1.0.44 exporter terminates. Two
  consequences if chosen: counts must be of **records received**, not written; and `retainHours` pruning
  a run's early files will read **`missing_records`**, correctly but surprisingly.
- **(b) No marker for Chronicle.** Deployed exports keep reading `unknown`.
- **(c) An export-time marker counting what the exporter read — REJECTED BY NAME, on V3.** It would
  always read `complete`, because the exporter always reaches its own end: absence becoming a claim. The
  `run-mongoose-server` skill already forbids manufactured markers.
- **(d) Deployments also run the text writer**, through MA-5's fan-out. Needs **no Chronicle change**.

---

## MA-4 · The developer journey — gated on the text writer

**Mongoose audit-logs by default in core**, where persistence is off: `MongooseServer` installs a JUL
`LogRecordListener` at boot and sets it on every processor, and core's own code uses `auditLog`. **In the
developer download persistence is ON**, so MA-5a applies and those records no longer reach the console.

| | State today |
| --- | --- |
| Records reaching a listener | on in **core examples**; replaced by capture in the developer download (MA-5a) |
| `AuditCaptureConfig.enabled` | **`false`** in core; **`true`** in the playground's `analyser-bundle` |
| What persistence writes | a **Chronicle binary queue**; text needs `svc-admin-web`'s export |
| Console records | not analyser-openable — JUL prefixes break Format 1 framing |
| Core documentation | absent from `docs/`; appears once in `design-doc/backpressure-and-slow-consumer-handling.md` |

### OD-4 · DECIDED — text for developers, Chronicle deployed, **via the configured listener**

The spike settles the mechanism. For the developer profile the text backend is **the server's configured
listener** (`bootServer(config, listener)`), not the capture service.

**The mechanism DEPENDS ON MA-5, and round 5 found that the hard way.** The developer bundle ships
`auditCapture.enabled: true` with `autoStart: [marketProcessor]`, and MA-5a says capture **replaces**
the configured listener. OD-4 makes the text writer *be* that listener. Run together — which nobody had
done — the writer received **4 startup records, none of the 8 business events**, and the file read
**`{complete, recordsRead: 4, declaredRecords: 4}`**. `complete` true of the file, false of the run:
**a V3 and V4 violation in MA-2's own shipped configuration.** Confirmed in source: `ServerConfigurator`
sets the configured listener, then registration calls `attach` and, for `autoStart`, `start` — which
replaces it.

**So: the text writer requires MA-5**, so capture fans out instead of replacing. Chosen over "turn
capture off in the bundle", which would leave the composition broken for anyone configuring both and
would also remove the admin UI's file list and export. **As a guard, Mongoose warns loudly when a text
writer and a replacing capture are configured together**, so the interim is not silent.

**Why the listener route, still.** It delivers MA-4's acceptance with **no `backend` switch and no
`svc-admin-web` change**. The capture-service route drags in a blast radius no item had costed:
`DirAuditIntrospectionService` is built over the Chronicle service, and the plugin's file listing, export
and websocket tail all read Chronicle, so each would have to become backend-aware or refuse by name.

**What it costs, stated rather than discovered.** A listener-based writer is **invisible to
`audit.start`/`stop`, `liveSinks` and the admin file list**. For the developer profile that is
acceptable: the journey is *open the file in the analyser*, not *manage capture from the admin UI*.
Deployments keep Chronicle and may add text through OD-5 (d).

**Still open under OD-4:** whether core's own examples match the playground's setting, and where
`auditCapture` is documented.

### Acceptance MA-4

A developer who downloads Mongoose and follows getting-started **ends up with an audit log they can open
in the analyser**, without knowing `auditCapture` exists. Run as the virgin-LLM test, **against the
bundle's actual shipped configuration** — not a variant with capture disabled (F1).

---

## MA-3 · Moved to `mongoose-plugins#38`

The `MAX_PENDING` ceiling's live-server test. It shares no code and no ordering with this spec.

---

## Decisions

| | Question | State |
| --- | --- | --- |
| **OD-1** | auditor always-on vs opt-in in `DefaultEventProcessor` | **MOOT.** Its flag existed to install an `EventLogManager` on the wrapper path, which MA-1 withdrew. The live opt-in is the AOT build-time `addEventAudit()` / designer `logLevel`. The owner's preference — opt-in, the no-auditor build being the low-latency profile — informs D-MA1b |
| **OD-2** | install an auditor, or refuse | **DECIDED: refuse** |
| **OD-3** | how to install it | **MOOT**, superseded by OD-2 |
| **OD-4** | developer default | **DECIDED: text for developers via the configured listener; Chronicle deployed** |
| **Flush** | per record, or buffered | **DECIDED: a user-configurable property**; default flush-per-record in the developer bundle |
| **Framing home** | duplicate, or move to core | **DECIDED: move to core** (D-MA2b) |
| **OD-5** | does Chronicle get a marker? | **OPEN — blocks MA-2's Chronicle half only.** (c) rejected by name |

## What is verified, and what is only read

**Verified by running:** F1's composition — the OD-4 text writer with the bundle's capture on, reading
`complete` with none of the run's business events; problem 1's three clauses on an unaudited AOT
processor; MA-5.4's re-registration discard; MA-7's framing injection, reproduced independently — 3 records read as 4, split
into two runs, `missing_records` with a forged `declaredRecords: 0`; MA-8's per-node `WARN` case;
the spike's six results — clean SIGTERM `complete` 29 of 29 with node entries;
marker-after-stop `complete` 31,586 twice with zero records after the marker; marker-before-stop
`complete` with 39 and 20 records lost; `kill -9` `unknown`; buffered 0 bytes after boot; two starts
giving two `complete` files and a `complete` two-segment concatenation. The MA-2.4 parity result. The
kill-point cuts. `getAuditorById("eventLogger")` on three processor kinds. Both rolled-set cases. The
empty-log case table on the published 1.19.0 jar. AFMT-3's reproduction and the marked-file case. The
zip-digest and request-parameter effects on the bundle.

**Read, not run:** `getBackend()` having no caller (a grep) and `DataFlow` having no listener getter (a
`javap`); `ChronicleAuditCaptureService`'s replace-and-no-op behaviour; `ServerConfigurator`;
`AuditCaptureConfig`; `DefaultEventProcessor` and `EventLogManager` sources; the framers' separator
predicates; `CoverageService.assess`. **MA-1's refusal paths remain unbuilt**, though three of the four
triggers' *current* behaviour is now measured — `autoStart` silent, REST start claiming recording, the
level endpoint 200; the `audit.start` admin command was not run.

**Not established:** AFMT-3's **cause**; the per-event cost of an `EventLogManager` after `UP-FLX-51`;
**whether a payload exists that makes a log read `complete` falsely** (MA-7) — none was constructed, and
that none exists is unproved.

## How this spec got here

Five reviews, all CHANGES REQUIRED, all answered. Two lessons are worth more than the findings.

1. **An edge case was treated as central.** `customHandler` drove three reframings and a proposed
   generated artefact before anyone asked how often it is used. The check that settled it — downloading
   the bundle — took one command and should have been first, not last.
2. **Every revision added text and left what it replaced.** Six such defects shipped: a duplicate
   ordering block, a retracted phrase, a silently deleted section, an appendix holding live decisions, a
   stale status code, and a self-contradicting gate. The discipline that works is not care while
   editing — it is grepping for the superseded phrase after the edit, which is now how every revision
   of this document ends.
