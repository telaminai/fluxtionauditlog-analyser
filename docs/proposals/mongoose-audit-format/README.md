# Reading Mongoose audit output without an export step

_Status: **PROPOSAL, 2026-09-21. Not implemented, not reviewed, not owner-approved.** One required slice —
Mongoose writes a directly readable **text** audit file — plus a dependent socket fix and an optional
renderer move. Filed in the holding pen because the work lands in Mongoose, the playground,
mongoose-plugins and this repository. Companion:
[trust structure](../../specs/spec-trust-structure.md) (D-T8, D-T9),
[tool agreement](../../specs/spec-tool-agreement.md),
[source adapters](../../specs/spec-source-adapters.md)._

## Revision history — this document has been wrong three times

Kept in full, because the corrections are the most useful thing in it and two of them were mine.

| Rev | What changed | Found by |
|---|---|---|
| 1 | `backend` is never read. The first draft called it "a field that exists with one legal value"; it has no effect at all. | independent session, by running a server |
| 2 | Ordering reversed to binary first, on the leniency of the text parser. | owner |
| 3 | The renderer move and the socket folded in as slices. | owner |
| 4 | Claimed the format is a deployment property, settable at runtime. **Wrong in the half that mattered.** | author, from a bad search |
| **5** | **Ordering reversed back to text first. The runtime swap does not yield a working binary processor. The repository list was wrong. The socket hypothesis is refuted and replaced by an observed root cause.** | independent session, by running a live server |

**Revision 4's error, stated plainly.** It said the record format is a deployment property because
`EventLogControlEvent` carries a `LogRecord` and `EventLogManager` swaps it live. The event does carry it
and the swap does occur. **But the swapped processor is not equivalent to one that started binary**, and
revision 4 asserted equivalence from reading alone. That is the same failure it had just corrected in
revision 1: taking a mechanism's existence for its behaviour.

## The friction, stated precisely

A Mongoose user who wants to look at what their system did must: run the server, stop it or reach an admin
endpoint, run an export to convert the capture into analyser-readable YAML, then open that YAML. The export
exists solely because the analyser cannot read what the server wrote. Every hosted journey pays it, and it
is a step where a first-time user can simply stop.

## What is already true

Facts marked **read** come from source. Facts marked **OBSERVED** were produced by running something.
Evidence is machine-local and git-ignored under `.local-evidence/coldstart-v2-2026-09-20/`:
`text-backend-2026-09-21/` for revision 1, `audit-format-review-2026-09-21/` for revision 5.

| Fact | Basis |
|---|---|
| Chronicle capture writes **the same YAML text** the analyser already parses, one record per excerpt | read — `ChronicleAuditCaptureService.java:257-261` |
| The audit **sink** is a boot parameter and is swappable at runtime | read — `MongooseServer.java:269`, `ServerConfigurator.java:39,126`, `EventLogControlEvent(LogRecordListener)` |
| `BinaryLogWriter implements LogRecordListener`, and **throws on a plain `LogRecord`** | read — `BinaryLogWriter.java` |
| The analyser **already reads the binary FLXA format in core** | read — `spi/binary/BinaryAuditReader.java` |
| **`backend` is never read.** Chronicle is built whenever `auditCapture.enabled`; `backend: text` was accepted silently and a `.cq4` written. | **OBSERVED**, and `getBackend()` has zero call sites |
| **The export's last record is unterminated.** | **OBSERVED** |
| **A processor swapped to binary at runtime is not equivalent to one that started binary.** Same five events, tracing on: started binary gives 7 records, `riskCheck` 15 entries, 0 unresolved ids. The only ordering that runs at all gives `riskCheck` **0 entries and 1 unresolved id**, with no error reported. The other two orderings throw. | **OBSERVED** (rev 5) |
| **`/ws/audit-tail/{processor}` exists and its producer throws on every tick.** `ThreadingIllegalStateException: StoreTailer … not thread safe`, about 480 times in 12 s, each swallowed at DEBUG. | **OBSERVED** (rev 5), `WebAdminService.java:1022`, svc-admin-web 1.0.43 |
| An unknown processor name is **not** silent: `{"err":"no live capture for processor …"}` then close 1000. | **OBSERVED** (rev 5) |
| The `/api/audit/files` listing was **not** stale: `recordCount` 25 matched 25 exported records. | **OBSERVED** — staleness **unreproduced** |

## The format is a deployment property for the sink, and not for the record

Revision 4 claimed both. Only the first half survives.

**What holds.** The sink is freely swappable at runtime. That is how Chronicle capture already operates,
and **a text file writer needs nothing more than this.**

**What fails, and it is the half binary depends on.** Two causes, both read in source and re-verified:

1. **Stale loggers.** `updateLogRecord()` (`EventLogManager.java:181-191`) iterates `name2LogSourceMap`
   and rebuilds `node2Logger` **only for nodes that log for themselves**. A node that is merely *traced*
   keeps its old logger, bound to the discarded record, and `nodeInvoked` (`:199-200`) reads from
   `node2Logger`. That is the measured zero-entries-and-one-unresolved-id result.
2. **Every control event is itself published.** `processingComplete()` (`:295-298`) publishes `logRecord`
   to `sink` for every event including the control event, and **no constructor sets both the record and
   the sink**. Two separate events therefore necessarily publish a record of one format to a sink of the
   other, which is what the two throwing orderings are.

**This is itself a D-T8-shaped defect:** the surviving ordering produces a processor that silently logs
less, carrying an unresolved id, with no error anywhere.

**Consequence for binary.** Reaching binary on a running traced processor needs either a **runtime fix**,
whose release cadence the owner has ruled out, or a **build-time choice**, which brings back the
closed-compiler obstacle, because `FluxtionSpringConfig` carries `logLevel` and no format. Revision 4
deleted that obstacle on a false premise; it is reinstated.

**And Chronicle plus binary must be refused.** `asCharSequence()` throws `UnsupportedOperationException`
on a `BinaryLogRecord` by design (`EventLogManager.java:84-87`), so the existing capture path crashes on
one. That combination rule was not named before and is named now.

## Text first — the ordering reversed back

Revision 2 chose binary first because the analyser's text parser is lenient and the conformance suite
gates readers rather than writers. **Both premises are true** — `RecordParser.java:114` ignores unknown
top-level scalars, and the suite asserts that two readers agree. **The conclusion does not follow.**

The text a writer would emit is `asCharSequence()`: the same text Chronicle stores today, the same text
every export carries, the same text the analyser reads every day. The content is not new. **What is new is
only the framing** — terminate each record as written, write an explicit end marker — and a byte
comparison against a known-good export gates exactly that, without relying on the lenient parser.

Binary, meanwhile, is the untested path in hosted Mongoose and the one measured broken above.

**So: text first, and binary only if a runtime fix or a build-time choice is funded.**

## What this costs, per repository — corrected

Revision 4 claimed two repositories and nothing elsewhere. That was wrong in three places.

| Repository | Work |
|---|---|
| **mongoose** | Write the text file: swap the sink, own the stream lifecycle, terminate each record, write an end marker. Validate `backend`/`format` and refuse unknown values by name, including chronicle-plus-binary |
| **fluxtion-web** | Surface the choice in templates, runbooks and generated documentation |
| **mongoose-plugins** | The web admin's audit tail, files listing and export are **all tied to Chronicle** — the tail opens a `ChronicleQueue` at `currentSink(processor).path()`. Under a file backend those features lose their source |
| **fluxtionauditlog-analyser** | `docs/skills/mongoose/run-mongoose-server/SKILL.md:40-42` states *"Mongoose does not write analyser-readable YAML directly"*, which this change makes false. The skill, its pin and the playground re-vendor all move. `TemplateArchive.java:35,203` also installs `export-audit` as a project command |
| **fluxtion** *(public runtime)* | None for text. **For binary**, the stale-logger fix above |
| **fluxtion-compiler** *(closed)* | None for text. **For binary via a build-time choice**, a format on `FluxtionSpringConfig` |

## The slices

### Slice 1 — Mongoose writes a text audit file *(required)*

Sink swap only, which is the half that works. Requirements:

1. **Validate the field and refuse unknown values at boot**, naming the legal values, and refuse
   chronicle-plus-binary explicitly. A silently ignored setting is a tool-agreement defect in its own
   right; proposed as a D-row in [spec-tool-agreement.md](../../specs/spec-tool-agreement.md): *a
   configuration key the server accepts must either take effect or be refused by name.*
2. **Terminate every record as it is written, never on close**, and **write an explicit end marker** on a
   normal end. A file without one reads as incomplete — stated-unknown under D-T8 and D-T9 — never as a
   short but complete run.
3. **One new file per boot.** Accumulating exports confused two readers and a subject in earlier trials.
4. **The writer must be the listener that actually receives records.** See the risk below.

**Acceptance.** Byte-comparable to a known-good export for the same input; record count equals events
processed; a clean end and a kill mid-run each produce a file the analyser reads correctly, the second
reporting incompleteness.

### Slice 2 — fix `/ws/audit-tail/{processor}` *(dependent on slice 1, not independent)*

Revision 3 hypothesised a missing producer, from a checkout predating the route. **Refuted.** In the tested
build, svc-admin-web 1.0.43, the route exists with producer `WebAdminService$AuditTailState`: its own queue
and tailer, `toEnd()`, polled every 25 ms. The connection is a real upgrade, HTTP 101.

**Observed root cause.** The tailer is created and `toEnd()` called on the connect thread, then read on the
executor thread, so every tick throws `ThreadingIllegalStateException` at `WebAdminService.java:1022` and
is swallowed at DEBUG.

**A second defect behind it**, read from bytecode: each tick reads into a local list and discards it
without sending when it does not flush — fewer than 32 records, or within 50 ms of the last flush. Once the
thread bug is fixed this will silently drop records. Another D-T8 gap, and it cannot be observed live until
the first is fixed.

**Why it is not independent of slice 1:** the tail reads the capture's Chronicle directory, not the live
listener. A file backend removes its source, so the fix and the backend change must be designed together.

**Acceptance.** A client connected before the run receives records appended during it; one connected
mid-run receives subsequent records; **the count delivered equals the count exported for the same window.**

### Slice 3 — move the record renderer into the runtime *(optional, and the case is stronger than revision 4 allowed)*

Revision 4 demoted this by arguing a live view can simply choose text. That misses three cases:

- "Choose text" only gives a live view **while Chronicle remains the store**. A file-backed text
  deployment needs a tailer for text files; a binary one needs the renderer.
- **A binary production deployment having an incident is exactly when a live view is wanted.**
- Chronicle plus binary crashes, so that combination is not an alternative.

Still optional, and still weighed against the public runtime's deliberately slow release cadence. If taken,
the shape is settled: move the analyser's private `RecordTextRenderer` beside `BinaryLogReader` rather than
writing a second one.

## Implementation risk

**The capture service detaches its listener by installing a no-op.** `ChronicleAuditCaptureService.java:242`
does this in **`stopRecording()`** — revision 3 mis-cited it as happening at boot. The risk it points at is
still real: in an earlier session a first run wrote only one record until the listener was passed through
`bootServer`. A writer constructed but not installed produces a file that exists, opens, and is nearly empty.

**Acceptance must be stronger than a record count.** The broken binary swap above had the *right* record
count with entries missing. So: **per-node entry counts matching a text run on the same input, and
`unresolvedIds == 0`.**

## Options considered

**Option A — a Chronicle reader plugin for the analyser.** Reads an existing queue in place, including from
a server nobody can rebuild. Costs Chronicle and its transitive dependencies, memory-mapped access and JVM
access flags, so it must be a plugin. Worth building later for the forensic case; not the answer to
local-development friction.

**Option B — Mongoose writes a directly readable file.** Recommended, text first, per above.

**Option C — make the export cheaper.** Keeps a conversion between a system and the tool built to observe
it. A socket-based version is doubly unattractive while slice 2 is broken.

## Open questions — proposed answers, owner decides

1. **Rotation.** *Proposed: none; one new file per boot*, bounded by the run. Avoids colliding with the
   analyser's rolled-set ordering rules.
2. **Truncation.** *Per slice 1 requirement 2.* For binary the reader already reports an unreadable tail as
   a stop mid-write, and unresolved names as unknown rather than empty.
3. **Hot-path cost.** *Open.* Nothing measured.
4. **The existing export.** *Keep it, for Chronicle only* — noting that binary deployments lose the web
   admin's audit views entirely.
5. **Backends or a format field.** *A format field*, plus an explicit table of allowed backend-and-format
   combinations, since chronicle-plus-binary must be refused.

## Owner decisions this proposal does not settle

1. The default format in the playground templates.
2. Which backend and format combinations are allowed.
3. **Whether to fund a runtime release for the stale-logger fix, or take a build-time binary choice in the
   closed compiler, or neither.** This decides whether binary happens at all.
4. Whether templates keep tracing on.
5. Whether web admin audit features must keep working under file backends.
6. Whether to adopt the proposed tool-agreement D-row.

## Recorded separately

**Port 8181 is hard-coded in seven places** in the demo bundle's scripts and documentation. Hosted test runs
only worked by editing `listenPort` in `config/server-config.yml`.

## Supporting context, not evidence

A virgin-session run adding two new nodes completed end to end in 126 seconds and one build; the remaining
friction was the export step. **n=1**, a hypothesis rather than a finding under this project's own adoption
threshold.

## What was read, what was run, and what was not

**Run by the revision 5 reviewer:** the five-way swap comparison on the shipped bundle's own processor; the
socket's live behaviour and its root cause; offline projection, 25 of 25 excerpts; that appended rows
reached the capture. **Run by the revision 1 reviewer:** the text-backend server test.

**Read in source and re-verified here for revision 5:** `updateLogRecord` and its logger rebuild;
`processingComplete`'s publish; `asCharSequence()` throwing on a binary record; the no-op listener's real
location in `stopRecording()`; the analyser skill's now-false sentence and `TemplateArchive`'s
`export-audit` command.

**Not verified by anyone:** the closed compiler and `FluxtionSpringConfig`; the playground repository;
mongoose-plugins source at 1.0.43, read as bytecode only; whether the deployed Mongoose matches the local
checkout; hot-path cost; the flush-drop defect live, because the thread bug masks it.

**A labelling correction.** Revision 4's swap claim rested on reading alone yet was asserted as settled.
Revisions 2 to 4 also deleted superseded material rather than marking it; this revision keeps the history
table above instead.
