# Mongoose produces an audit log the analyser can trust (Design Spec)

_Status: **PROPOSED 2026-09-23, not started. REWRITTEN at round 3** — three reviews, all CHANGES
REQUIRED, all answered. Rewritten rather than patched: each earlier rescope added text and left the text
it replaced in place, four times, and round 3 found the appendix holding live decisions while telling
readers to skip them. The superseded analysis is **deleted**, not archived; its conclusions are inline
and its history is one section at the end._

_**One owner decision blocks: OD-5**, whether the Chronicle backend gets a marker. Without it MA-2
changes nothing for deployed configurations. **OD-2** (refuse) and **OD-4** (text for developers,
Chronicle deployed) are taken; **OD-1 and OD-3 are moot** — see *Decisions*._

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
2. **Nothing writes a stream-end marker**, so every export reads `unknown` and the 1.18.0 and 1.0.44
   work delivers nothing a user sees.
3. **An empty log raises no finding at all** — the analyser's own gap, and the cheapest to close.

## What is NOT the problem — corrected twice, at cost

**Not `customHandler`.** An earlier draft treated the wrapper path as central. It is an edge case, used
only to run Mongoose without a Fluxtion event processor. Verified by downloading a public
`analyser-bundle` (`/start/scaffold?template=analyser-bundle`, 200, sha256
`1eb6062b00a2e95a5bafc4d3cdd3d4b6e778cf804495e96e6fc332da86c1c8ee`): it contains **no** `customHandler`;
its `marketProcessor` is an AOT `MarketProcessor` declaring `eventLogger`, wiring its clock, calling
`initialiseAuditor(eventLogger)` and registering the user's own `riskCheck` and `rootNode`. **Pin
evidence by digest, not size** — review's download of the same template was 66,269 bytes against this
one's 65,364.

**Not "the wrapper path" either.** Review corrected the correction: the population that gets silence is
**any processor without an `EventLogManager`**, which includes **AOT processors built without audit** —
the low-latency profile, a supported configuration rather than an edge case. MA-1 keys on the
capability, not the construction route.

**`complete` is not a false verdict.** It is a true container claim: the marker's count matches what was
read. An earlier draft called it "a confident false one" and built an ordering requirement on it. Both
withdrawn. The harm is the **missing empty-log finding**, which is MA-0.

## Ordering

```
MA-0  (analyser: an empty log is a finding)     ← THIS repo; smallest; closes a live gap
MA-1  (a processor that cannot audit says so)   ← independent
MA-5  (capture must fan out, and restore)       ← independent; a live silent-discard path
AFMT-3 (per-node NONE corrupts the record)      ← GATES MA-2
  └── MA-2  (the text backend + the marker)     ← needs AFMT-3 resolved and OD-5 decided
        └── MA-4  (defaults, docs, the journey) ← GATED on MA-2
              └── AF-6 (the coupled documents)  ← in the tracker, not here
MA-3  → moved to mongoose-plugins#38
```

MA-0, MA-1 and MA-5 can start now, in any order. MA-2 cannot.

---

## MA-0 · The analyser reports an empty log as a finding

**In this repository.** Today every empty shape returns from `ProducerDiagnostics` before any check, so
an empty log raises nothing — marked or not. A marker only changes the label from `unknown` to
`complete`; what makes it look healthy is the missing warning.

### D-MA0a · A finding, never a seventh state

The six states are a published contract (§1a, D-E3) that three store paths and `context` agree on. The
empty-log signal is a **`ProducerDiagnostics` finding beside an unchanged state**. An earlier draft said
"qualifies the verdict"; that risked a seventh state and is withdrawn.

### D-MA0b · Key it on zero records only

The quiet-level case — a processor whose level is above its calls — is **already** caught by
`ONLY_CONTROL_EVENTS`, because the one record present is the control event. MA-0 needs only
`index.size() == 0`, placed before the early return. Stated so nobody widens it.

### Acceptance MA-0

Adopted from review, which ran the case table on the published 1.19.0 jar:

1. Each of these raises the empty-log finding as a **warning** (not a `COMPLETENESS_NOTE`) on the status
   bar, in the tooltip, in `context` and in the report, with the stream-end state **unchanged**: a marker
   declaring 0 and nothing else; today's empty export; zero bytes; whitespace only; two empty marked
   segments; a rolled set whose members are all empty.
2. Five records without node entries are unchanged — `NO_NODE_LOGS` still fires alone — and a healthy
   marked file stays clean.
3. In Follow, an empty file opened before its first record shows the finding, and it **clears on every
   surface** when a record arrives. Diagnostics recompute on `added > 0` but the status text is set at
   open; assert both.
4. A file whose zero records come from `SOURCE_DAMAGE` says both, **damage first**, matching the existing
   ordering.
5. Each case is a conformance fixture with a mutation witness proving it fails without the change.

---

## MA-1 · A processor that cannot audit says so

**Not "a `customHandler` processor".** The discriminator is the capability.

### D-MA1a · Detect by capability, with `getAuditorById("eventLogger")`

It resolves on a generated processor built with audit (public field) and throws on one built without, and
on `DefaultEventProcessor`. One check covers both populations. **Known limit**, from `AuditReadiness` N1:
a processor with a custom auditor would be refused wrongly — the safe direction to be wrong in.

### D-MA1b · Refuse at start, not at boot

A hard refusal at boot would stop a server with one unaudited processor in `autoStart`, which is exactly
the mixed deployment that build-time opt-in creates. So: **the server boots**; `start(name)` for that
processor **refuses**, naming the reason and the remedy; the sink is **never listed as recording**.

### Acceptance MA-1

1. Each of the three per-processor triggers — `autoStart`, the `audit.start` admin command, and
   `MongooseAuditCaptureService.start(name)` — refuses for a processor with no `EventLogManager`, naming
   the reason and the remedy (build with audit enabled), **and the server still boots**.
2. That processor never appears as recording in `liveSinks` or the admin file list.
3. `POST /api/processors/{group}/{name}/audit/level` — in **`svc-admin-web` (mongoose-plugins), not
   core** — returns **409 or 422** with a body naming the reason. "Not 200" alone would be satisfied by a
   500.
4. A regression check for each; all three failures are currently silent.

---

## MA-5 · Capture must fan out, and restore on stop

`ChronicleAuditCaptureService` carries a comment promising behaviour its code does not implement: it says
the capture listener is composed in front of the existing one and restored on stop. The code sets
`previousListener = null` and calls `setAuditLogProcessor(captureListener)`.

- **MA-5a — capture REPLACES the console listener.** Turning persistence on silently stops audit reaching
  the console.
- **MA-5b — `stopRecording()` installs a no-op**, so after stopping, audit reaches neither console nor
  file until restart.

MA-5b is this spec's defect class exactly: auditing apparently on, output silently discarded.

### D-MA5 · The mechanism, because there is no getter

`DataFlow` exposes `setAuditLogProcessor` and **no getter** — the code's own comment says so. So "keep the
previous listener" needs a named mechanism. **Mongoose already owns the listener it installs**
(`MongooseServer.logRecordListener`, set on registration and in `ServerConfigurator`), so it passes that
one into `attach`/`start`. No upstream ask required. *The listener MA-5 restores is Mongoose's* — a
processor author's own listener is already replaced before capture starts.

### Acceptance MA-5

1. With capture on, a record reaches **both** the Chronicle queue and Mongoose's listener.
2. After `stopRecording()`, records reach that listener again — not a no-op, without a restart.
3. **start → stop → start**: no double wrapping; each record reaches each destination exactly once.
4. **Re-registration while recording**: `stopProcessor` does not stop capture; re-adding the name swaps
   the `DataFlow` in `attach`, and `start` then returns early because the sink `isRecording()`. Assert the
   new instance's records reach the capture file. *(Review's read, not run — a plausible fourth silent
   path.)*
5. **Isolation**: a listener that throws must not stop the other destination receiving the record.
6. Fan-out and restore are a contract of `MongooseAuditCaptureService`, tested for **every** backend —
   otherwise MA-2's text backend reintroduces MA-5a.

---

## AFMT-3 · Per-node `NONE` corrupts the record — GATES MA-2

**Reproduced by review on today's download**, compiled against the bundle's `fluxtion-runtime-1.0.16`:
setting `riskCheck` to `NONE` turns the next record into
`mainonPriceEventPriceEvent{…}195.31200` — no header, no keys, values run together. `rootNode` `NONE`
gives `mainonRiskCheck`. Global `NONE`, per-node `ERROR`/`WARN`/`DEBUG`, and unknown-node `NONE` are all
fine. **The defect is per-node `NONE` on a registered node, and only that.**

**Why it gates MA-2**, on the published 1.19.0 jar: a file of good record, AFMT-3 record, good record,
with a marker declaring 3, reads `{complete, recordsRead: 3, declaredRecords: 3}` **with no finding**. The
corrupt document carries no `eventLogRecord:` key, is counted as a record, and nothing flags it. **With
MA-2 shipped, the marker turns that silence into a verified completeness claim.** A marker must not vouch
for a record the runtime corrupted.

**The tracker's repro is stale and must be fixed first**: `LevelTest3.java` targets `volumeTotal`, which
is not a node in today's bundle, so re-running it shows every case clean and AFMT-3 looks fixed. Retarget
it at `riskCheck`/`rootNode`.

**Reach:** the admin endpoint sets levels globally only, so a REST user cannot trigger it; Java code can,
via `DataFlow.setAuditLogLevel(level, sourceId)`. The corruption is in the runtime's record encoder, so it
reaches JUL, Chronicle and a text writer alike.

**To clear the gate**, either the runtime fix lands first, **or** MA-0 grows a check for a document
carrying no `eventLogRecord:` key (cheap, same class) and MA-2's acceptance includes a per-node-`NONE`
run. `UP-FLX-51`'s `NONE` default is a second, independent reason not to ship a `NONE` default onto this
path.

---

## MA-2 · The text backend and the marker — blocked

### D-MA2a · The separator rule splits by backend

The earlier "the separator is not this writer's job" was right when the marker travelled through the
export. **OD-4 makes the developer default Mongoose writing the text file directly, with no export in the
path**, so for that backend the writer owns **every** `---` between records **and** the one after the
marker (§1a rule 1). `ProducerDiagnostics`' own `UNSEPARATED` message says the sink must `append("---\n")`.

- **Chronicle backend** — the 1.0.44 exporter writes the separators. Unchanged.
- **Text backend** — the writer writes its own. New.

### D-MA2b · `backend` is the value that must be refused by name

The earlier rule aimed at `streamEnd`, which has no referent: `normal`/`stopping` is chosen by the writer
when it stops, not configured. **`backend` is read by nothing today** — `getBackend()` has no caller in
`src/main`, and `MongooseServer` constructs `ChronicleAuditCaptureService` whenever `enabled` is true. So
`backend: text` is accepted and silently ignored, and so is a typo. **That is this spec's defect class, in
the switch OD-4 depends on.** MA-2 makes `backend` load-bearing and refuses an unknown value by name.

### D-MA2c · The marker's lifecycle — all five answers required

| Question | Why it must be decided |
| --- | --- |
| **When** — `stopRecording`, shutdown hook, roll? | missing any leaves a clean stop reading `unknown` |
| **Which thread** | records append on the processor thread, `stop` comes from the admin thread; a marker written from the admin thread under load counts a record not yet appended, or misses one being appended |
| **Restart — new file or append?** | appending after a crash puts the crashed run's unmarked records into the next segment, and the next marker counts only its own run → `more_than_declared` on a file that lost nothing (D-E3, per-segment counting). A new file per start avoids this and the cumulative-export trap |
| **Roll — daily/size, as Chronicle does?** | every rolled set reads `unknown` by design (D-E5). If text rolls, each file carries its own marker, or "the developer opens the file and sees `complete`" is false after midnight |
| **Retention (`retainHours`)** | say whether the janitor applies to text |

### D-MA2d · Per-node parity is an acceptance-time comparison

§1a admits only `streamEnd`, `streamEndRecords` and `logTime`; **any other key makes the document an
ordinary record**. A per-node count cannot travel in the marker without a format change. Parity is
compared at acceptance — the handler's ground truth against the reader's parse — not declared by the
writer.

### OD-5 — BLOCKING · Does the Chronicle backend get a marker?

MA-2 as scoped describes the text writer only. **The deployed configuration keeps Chronicle** (OD-4), and
its exports will read `unknown` for ever unless capture appends a marker excerpt at stop, which the 1.0.44
exporter would then terminate. As specified, MA-2 changes nothing for any deployed export.

Decide explicitly, even if the answer is "not in this spec". Until then the problem statement's "every
export reads `unknown`" remains true for deployments.

### The text backend's blast radius — in scope, and previously in no item

`DirAuditIntrospectionService` is constructed over the Chronicle service, and `svc-admin-web`'s file
listing, export and websocket tail all read Chronicle. Under `backend: text` each must become
backend-aware **or refuse by name**, or the admin UI shows no files and the tail connects and delivers
nothing — the original defect's shape again. The bundle's `export-audit.sh`, the `run-mongoose-server`
skill ("Mongoose does not write analyser-readable YAML directly") and AF-6 all change with it.

### Acceptance MA-2

1. A text file carrying a marker reads **`complete`** in the released analyser, with `declaredRecords`
   equal to `recordsRead`, **`recordsRead > 0`**, and those records carrying node entries.
2. A text file whose writer never reached stop reads `unknown`; **MA-0 fires if it is empty**. (The
   earlier "an export without one reads as today" has no meaning for a direct writer.)
3. Kill points, scoped: before any record → `unknown`; **between records** (after a separator) →
   `unknown`; mid-record → `unknown`; mid-marker → `unterminated_marker`; **after the marker's separator
   is flushed → `complete`, which is correct**. Plus **stop under load** → `complete` with
   `declaredRecords` equal to the records actually appended — D-MA2c's thread question as a test.
4. **Byte-identical to a named baseline modulo the marker**: the 1.0.44 Chronicle export of the same run.
   (The old reason — "a known-good export has no trailing separator" — has been stale since 1.0.44
   terminates the last document.)
5. A per-node-`NONE` run produces no marked file that vouches for a corrupt record (AFMT-3).
6. `backend: text` selects the text writer; an unknown `backend` is refused by name.
7. Verified against the **published** analyser jar, by digest.

---

## MA-4 · The developer journey — gated on MA-2

**Mongoose audit-logs by default; what is missing is a file the analyser can open.** `MongooseServer`
installs a JUL `LogRecordListener` at boot and sets it on every processor, and core's own code uses
`auditLog`. So a developer running an example does see audit output. But:

| | State today |
| --- | --- |
| Records reaching a listener | **on** — until persistence replaces it (MA-5a) |
| `AuditCaptureConfig.enabled` | **`false`** in core; **`true`** in the playground's `analyser-bundle`, which IS the developer download |
| What persistence writes | a **Chronicle binary queue**; text needs `svc-admin-web`'s export |
| Console records | not analyser-openable — JUL prefixes break Format 1 framing |
| Core documentation | absent from `docs/`; appears once in `design-doc/backpressure-and-slow-consumer-handling.md` |

**OD-4, decided: text as the developer default, Chronicle when deployed.** That makes MA-2 load-bearing
for the journey — text as the default *is* MA-2 — so MA-4 is **gated on** MA-2, not merely improved by it.

**Still open under OD-4:** whether core's own examples match the playground's setting, and where
`auditCapture` is documented.

### Acceptance MA-4

A developer who downloads Mongoose and follows getting-started **ends up with an audit log they can open
in the analyser**, without knowing `auditCapture` exists. Run as the virgin-LLM test: a fresh session
either opens a file or it does not.

---

## MA-3 · Moved to `mongoose-plugins#38`

The `MAX_PENDING` ceiling's live-server test. It shares no code and no ordering with the rest of this
spec. Reaching the ceiling needs a client that stops reading at TCP level; a JDK client that stops calling
`request()` applies flow control in its listener, not on the wire.

---

## Decisions

| | Question | State |
| --- | --- | --- |
| **OD-1** | auditor always-on vs opt-in in `DefaultEventProcessor` | **MOOT.** Its flag existed to install an `EventLogManager` on the wrapper path, which MA-1 withdrew. The live opt-in is the existing AOT build-time `addEventAudit()` / designer `logLevel`. The owner's *preference* — opt-in, because the no-auditor build is the low-latency profile — stands and informs D-MA1b; the mechanism is gone |
| **OD-2** | install an auditor, or refuse | **DECIDED: refuse.** A user wanting audit is better served by the AOT path, which gives real per-node coverage |
| **OD-3** | how to install it — framework release / subclass / generated artefact | **MOOT**, superseded by OD-2 |
| **OD-4** | developer default | **DECIDED: text for developers, Chronicle deployed** |
| **OD-5** | does Chronicle get a marker? | **OPEN — BLOCKS MA-2** |

## What is verified, and what is only read

**Verified by running:** the empty-log case table on the published 1.19.0 jar (all six shapes silent
today); the bundle's contents and digest; `getBackend()` having no caller; `DataFlow` having no listener
getter; AFMT-3's reproduction on today's bundle and the marked-file-vouches-for-corruption case; the
`volumeTotal` repro target being absent; that a quiet level yields only the control-event record.

**Read, not run:** `ChronicleAuditCaptureService`'s replace-and-no-op behaviour; `ServerConfigurator`;
`AuditCaptureConfig`; `DefaultEventProcessor` and `EventLogManager` sources; the re-registration path in
MA-5.4.

**Not established:** AFMT-3's **cause**; the per-event cost of an `EventLogManager` after `UP-FLX-51`;
whether a text backend can meet MA-2.4's byte-identity baseline.

## How this spec got here

Three reviews, all CHANGES REQUIRED, all answered; the review files are in `docs/handoff/`. Two
corrections are worth carrying forward, because they were the expensive ones.

1. **An edge case was treated as central.** `customHandler` drove three reframings and a proposed
   generated artefact before anyone asked how often it is used. The check that settled it — downloading
   the bundle — took one command and should have been the first thing done, not the last.
2. **Every rescope added text and left the text it replaced.** Four such defects shipped in one day: a
   duplicate ordering block, a retracted phrase, a silently deleted `OD-1` section, and an appendix
   holding live decisions while telling readers to skip them. This revision is a **rewrite**, and the
   appendix is deleted rather than archived — archiving is what produced the fourth.
