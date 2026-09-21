# Reading Mongoose audit output without an export step

_Status: **PROPOSAL, 2026-09-21. Not implemented, not reviewed, not owner-approved.** **Two releases.** Release 1 is text —
Mongoose writes a directly readable audit file, plus the dependent socket fix — and **touches no fluxtion
code**: Mongoose, the playground, mongoose-plugins and this repository. Release 2 batches the binary
stale-logger fix and the renderer move into one `fluxtion-runtime` release, once text is stable. Filed in
the holding pen because most of the work lands outside this repository. Companion:
[trust structure](../../specs/spec-trust-structure.md) (D-T8, D-T9),
[tool agreement](../../specs/spec-tool-agreement.md),
[source adapters](../../specs/spec-source-adapters.md)._

## Revision history — six revisions, three of them corrections of error

Kept in full, because the corrections are the most useful thing in it and two of them were mine.

| Rev | What changed | Found by |
|---|---|---|
| 1 | `backend` is never read. The first draft called it "a field that exists with one legal value"; it has no effect at all. | independent session, by running a server |
| 2 | Ordering reversed to binary first, on the leniency of the text parser. | owner |
| 3 | The renderer move and the socket folded in as slices. | owner |
| 4 | Claimed the format is a deployment property, settable at runtime. **Wrong in the half that mattered.** | author, from a bad search |
| **5** | **Ordering reversed back to text first. The runtime swap does not yield a working binary processor. The repository list was wrong. The socket hypothesis is **half** refuted — the producer exists, but no client opens the socket — and an observed root cause is added.** | independent session, by running a live server |
| **7** | **Owner decisions taken; the framing requirement was dangerous.** A writer built to revision 5's spec emits no `---` separators, which the analyser silently reads as ONE record. End-marker support is funded in the analyser. Twelve items lost in the churn are restored. | independent session, by running the analyser |
| **6** | **Sequenced into two releases (owner).** Text ships now touching no fluxtion code; the runtime fix and the renderer move batch into one later release. The renderer's demotion is partly reversed: it is a poor standalone release and a cheap passenger on one already happening. | owner |

**Revision 4's error, stated plainly.** It said the record format is a deployment property because
`EventLogControlEvent` carries a `LogRecord` and `EventLogManager` swaps it live. The event does carry it
and the swap does occur. **But the swapped processor is not equivalent to one that started binary**, and
revision 4 asserted equivalence from reading alone. That is the same failure it had just corrected in
revision 1: taking a mechanism's existence for its behaviour.

## Owner decisions — taken 2026-09-21

| # | Decision | Consequence |
|---|---|---|
| 1 | **The analyser learns to report incompleteness.** | Format-spec amendment plus analyser code join release 1. Without it an end marker is invisible; see *Framing*. |
| 2 | **The per-node `NONE` corruption waits.** | Not patched now. See the note below: "wait" resolves to release 2, not to never. |
| 3 | **Fix the audit-tail thread bug now**, for Chronicle users. | Pulled out of slice 2 as immediate work, independent of the format change. |
| 4 | **Public documentation is in scope.** | The tutorial and format spec change, so release 1 includes a site deploy. |
| 5 | **Text is the default** in generated projects, with an easy switch to Chronicle. | Templates default `backend: file`, `format: text`. |
| 6 | **Fund binary, and make record selection pluggable in the runtime** rather than only fixing the bug. | Release 2 is larger than a patch, and the closed-compiler route becomes a fallback rather than a rival. |
| 7 | **Web-admin audit views should keep working under a file backend, if possible.** | The socket is fed from the live listener rather than by tailing storage. |

**Decisions 2 and 6 interact, and the reading matters.** Decision 2 says wait; decision 6 funds a runtime
release. So **"wait" resolves to "ride release 2"**, not to "never" — the corruption is a live defect that
should not outlive the next runtime release. Flagged because taken separately the two answers look like a
contradiction. If the intent was never, say so and it moves to a standing known-defect record.

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
| **The export's last record has no closing `---`.** Revision 1 recorded this as "unterminated"; the sharper consequence is that **follow mode never shows it**. | **OBSERVED**, re-read rev 7 |
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

**Wider than a format swap (revision 7).** OBSERVED: a **text-to-text** record swap also drops a node's
entries, 5 to 0. **Any** record swap is affected, not only a change of format.

**And level changes are a different, live defect.** They do **not** go through `updateLogRecord`, which
runs only when a record is supplied, so revision 6's instruction to test them there rested on a false
premise. Global `INFO`, `DEBUG` and `NONE` behave correctly, as does per-node `DEBUG` and an unknown node
name. But a **per-node `NONE` corrupts the published text record**: header, keys and newlines all vanish.
Chronicle stores exactly those bytes, so this is **live today**, unrelated to this proposal, and its cause
is undiagnosed. Owner decision 2 holds it for release 2.

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

## Framing — the requirement that was dangerous, corrected

Revision 5 required the writer to "terminate every record as it is written" and "write an explicit end
marker". **A writer built to that specification produces a file the analyser silently misreads.**

**OBSERVED** (revision 7 reviewer), the same 25 records read three ways:

| Layout written | Analyser reads |
|---|---|
| the export's own layout | **25 records** |
| newline-terminated, no `---` — what revision 5 literally said | **1 record, silently** |
| a `---` line after each record | **25 records** |

**The rule already exists and revision 5 simply missed it.** `docs/site/format-spec.md:18-20` defines a
text container as records separated by lines consisting of `---`, and `RecordFramer` splits on exactly
that. Verified here: its own header states that a record with no closing `---` is **not emitted**, which
is how follow mode avoids showing a half-written record.

**Corrected requirement: the writer emits `asCharSequence()` followed by a `\n---\n` separator.** That
is not an addition to the format; it is the format. Confirmed at the byte level by the reviewer: the
export is exactly the Chronicle excerpts joined by `\n---\n`, so a conforming writer reproduces the
export rather than approximating it.

**This also reinterprets revision 1's finding.** The export's last record is not missing a newline; it is
missing its closing `---`. The consequence is sharper than "unterminated": **follow mode never shows the
export's last record at all.**

**And it vindicates revision 2.** The leniency concern was right, and revisions 5 and 6 dismissed it for
the wrong reason. The risk was never in the content, which is byte-identical to what ships today. It is in
the framing, where a lenient parser turns a missing separator into one enormous record with no error.

### The end marker needs analyser support — decision 1

**OBSERVED:** an end marker written as its own document becomes a phantom extra record; written inside the
last record it is silently absorbed; and a file killed at a record boundary is indistinguishable from
either. So revision 5's claim that "a file without one reads as incomplete" was **false for the analyser
as shipped**, and its acceptance line about a kill mid-run could not have passed.

Under decision 1 this is funded rather than dropped: **a format-spec amendment defining the marker, plus
analyser code that reports a file without one as incomplete.** That is a D-T8 product semantic — the file
states whether it is whole — and it is the property this proposal exists to sell. It moves the analyser
from a skill edit to a code change, and with decision 4 it moves the public format spec too.

## What this costs, per repository — corrected

Revision 4 claimed two repositories and nothing elsewhere. That was wrong in three places.

| Repository | Work |
|---|---|
| **mongoose** | Write the text file: swap the sink, own the stream lifecycle, emit `asCharSequence()` + `\n---\n` per record, write the end marker. Validate `backend`/`format` and refuse unknown values by name, including chronicle-plus-binary |
| **fluxtion-web** | Surface the choice in templates, runbooks and generated documentation |
| **mongoose-plugins** | The web admin's audit tail, files listing and export are **all tied to Chronicle** — the tail opens a `ChronicleQueue` at `currentSink(processor).path()`. Under a file backend those features lose their source |
| **fluxtionauditlog-analyser** | **Code**, per decision 1: end-marker support and incompleteness reporting. **Public docs**, per decision 4: `format-spec.md` amendment and `tutorial-playground.md` §3, so a site deploy. **Skills and specs**: `docs/skills/mongoose/run-mongoose-server/SKILL.md:40-42` states *"Mongoose does not write analyser-readable YAML directly"*, now false, with its pin and the playground re-vendor; `spec-tool-agreement.md` D12 (the socket diagnosis the thread exception refutes) and D13 (listing staleness, unreproduced); `spec-onboarding-example.md:62-72`; `spec-guided-start.md:66`; `spec-follow-refreshes-graphs.md:37`; `docs/experience/current/skills/read-audit-log`; `TemplateArchive.java:35,203` installing `export-audit` |
| **fluxtion** *(public runtime)* | None for text. **For binary (decision 6)**, pluggable record selection rather than only the stale-logger fix, plus the renderer and — per the decision 2 note — the per-node `NONE` corruption |
| **fluxtion-compiler** *(closed)* | None for text. A format on `FluxtionSpringConfig` becomes a **fallback** under decision 6, not a rival route |

## Delivery — two releases, in this order *(owner sequencing, revision 6)*

The slices do not ship one at a time. They fall into two groups divided by whether they need a
`fluxtion-runtime` release, and that division is the whole plan.

### Immediate — the audit-tail thread fix *(decision 3)*

Independent of everything else and worth shipping on its own: create the tailer and call `toEnd()` on the
reading thread. It repairs live tailing for **every existing Chronicle deployment** and needs no format
change. mongoose-plugins only.

### Release 1 — text. **No fluxtion code is touched.**

Slices 1 and 2. Four repositories: **mongoose**, **fluxtion-web**, **mongoose-plugins**, and **this one** —
where decisions 1 and 4 now mean analyser *code* and a *public site deploy*, not only a skill edit.

It touches no fluxtion code because it never changes the record type: only the sink is swapped, which is
the half that works, and the text is `asCharSequence()`, which already exists. The stale-logger defect is
never reached, so the runtime needs nothing and the closed compiler needs nothing.

**One scoping detail.** The runtime ships two `LogRecordListener` implementations — `JULLogRecordListener`
and `BinaryLogWriter` — and **no text file writer**. So Mongoose writes a small one: call
`asCharSequence()`, terminate the record, write to the stream. **It belongs in Mongoose, not the runtime**,
which owns the file lifecycle and the configuration anyway. Putting it in the runtime would pull a release
into release 1 for no benefit, which is the thing this sequencing exists to avoid.

### Release 2 — binary, the renderer and the live defects, once text is stable.

Slices 3 and 4 in **one** `fluxtion-runtime` release, and under decision 6 it is **pluggable record
selection**, not merely a bug fix. Two live defects ride it: the wider record-swap staleness, and the
per-node `NONE` corruption held by decision 2.

**They are logically coupled, not merely convenient to batch.** The renderer only matters once binary
exists, and binary only works once the stale-logger fix ships. Releasing them separately means two slow
releases for work that is useless apart.

**This partly reverses revision 4's demotion of the renderer, and the reasoning is worth keeping.** The
objection was that it ties a specification to a deliberately slow release cadence. That is true of the
renderer **as a standalone release** and false of it **as a passenger on a release already happening**.
Once the logger fix requires a runtime release regardless, the renderer costs almost nothing more.

**Two things to settle before release 2, because it is expensive and a second one will not come soon.**

1. **Revision 6's instruction to test level changes through `updateLogRecord` was wrong** and is
   withdrawn: level changes do not go through it. The real defect is the per-node `NONE` corruption, whose
   cause is undiagnosed and which must be diagnosed before the release rather than during it.
2. **Pluggable selection must cover the swap path, not just the start path.** Any record swap drops a
   traced node's entries today, so a pluggable design that still swaps records at runtime inherits the
   defect unless the logger rebuild is fixed with it.
3. **The closed-compiler fallback stays priced.** A build-time format means a processor *starts* binary and
   never swaps, avoiding the path rather than repairing it — useful if pluggable selection slips.

## The slices

### Slice 1 — Mongoose writes a text audit file *(release 1, required)*

**Slice 3 is binary**, described under *The format is a deployment property* above: it needs the
stale-logger fix or a build-time choice, and rides release 2 with slice 4.


Sink swap only, which is the half that works. Requirements:

1. **Validate the field and refuse unknown values at boot**, naming the legal values, and refuse
   chronicle-plus-binary explicitly. A silently ignored setting is a tool-agreement defect in its own
   right; proposed as a D-row in [spec-tool-agreement.md](../../specs/spec-tool-agreement.md): *a
   configuration key the server accepts must either take effect or be refused by name.*
2. **Emit `asCharSequence()` followed by `\n---\n` for every record, as it is written, never on close.**
   The separator is the format, not an embellishment; without it the analyser reads the whole file as one
   record. **Write the end marker** defined by the format-spec amendment funded in decision 1, so a file
   without one is *reported* incomplete rather than merely being so.
3. **One new file per boot.** Accumulating exports confused two readers and a subject in earlier trials.
4. **The writer must be the listener that actually receives records.** See the risk below.

**Acceptance**, tightened after revision 7 found the same weakness revision 5 had fixed in one place only:

- **Byte-identical to a known-good export** for the same input, modulo the end marker alone — the export is
  exactly the excerpts joined by `\n---\n`, so a conforming writer reproduces it. Any other difference is
  a defect, not a normalisation.
- **Per-node entry parity** against a Chronicle run on the same input, not a record count. Five price
  events produce 23 records once lifecycle and control records are counted, so "record count equals events
  processed" is ill-defined and a count alone hides the missing-entries failure.
- **A kill mid-run is reported incomplete** by the analyser, which is only testable once decision 1 lands.
- **An unknown `backend`/`format` value is refused by name**, and chronicle-plus-binary is refused. Neither
  had an acceptance check before.

### Slice 2 — `/ws/audit-tail/{processor}` *(thread fix immediate; the rest with release 1)*

**Revision 5 declared revision 3's hypothesis refuted. Half of it was true and was lost.** The route does
exist in the tested build with a producer, so "no producer" was wrong. But **nothing in the shipped 1.0.43
client opens it** — `app.js` opens only `/ws/monitor` and `/ws/logs` — so a client is part of the fix.

**Observed root cause.** The tailer is created and `toEnd()` called on the connect thread, then read on
the executor thread, so every tick throws `ThreadingIllegalStateException` at `WebAdminService.java:1022`,
swallowed at DEBUG.

**A second defect behind it**, read from bytecode: each tick reads into a local list and discards it
without sending when it does not flush — fewer than 32 records, or within 50 ms of the last flush. It
cannot be observed until the first is fixed, and a test that does not include a burst of records less than
50 ms apart will not exercise it.

**Decision 3 pulls the thread fix forward**, because it repairs live tailing for every Chronicle
deployment today and needs nothing from the format work.

**Decision 7 changes the design of the rest.** Revision 5 said the socket was dependent on slice 1 because
the tail reads the Chronicle directory. Under decision 7 that dependency is designed out: **feed the
socket from the live listener through a second sink alongside the file writer**, rather than tailing
storage. That works for any backend, and the analyser keeps its own live view through existing follow mode.

**Acceptance.** A client connected before the run receives records appended during it; one connected
mid-run receives subsequent records; **the count delivered equals the count exported for the same window**,
with the test including a sub-50 ms burst so the flush-drop defect is exercised rather than assumed.

### Slice 4 — move the record renderer into the runtime *(rides release 2)*

Revision 4 demoted this by arguing a live view can simply choose text. That misses three cases:

- "Choose text" only gives a live view **while Chronicle remains the store**. A file-backed text
  deployment needs a tailer for text files; a binary one needs the renderer.
- **A binary production deployment having an incident is exactly when a live view is wanted.**
- Chronicle plus binary crashes, so that combination is not an alternative.

**Revision 6 changes its standing.** It remains unnecessary for the friction fix, but the cadence
objection no longer applies once release 2 is happening for the logger fix. It rides that release. The
shape is settled: move the analyser's private `RecordTextRenderer` beside `BinaryLogReader` rather than
writing a second one.

## Restored — true material lost across revisions 2 to 6

Revisions 2 to 4 deleted superseded material rather than marking it, and revisions 5 and 6 did not put it
back. All of the following is still true and was absent from both.

**About the shared runtime.** Mongoose and the analyser **already depend on `fluxtion-runtime`**, verified
in both POMs, so nothing here needs a new artifact or coordinate. The runtime carries the binary
round-trip tests — `BinaryLogFileRoundTripTest`, `BinaryRecordRoundTripTest`, `BinaryVersusTextRecordTest`
— and the analyser's conformance suites are `FormatConformanceTest` and `FlxaConformanceTest`. The
**one-implementation standing decision** applies to anything added: the analyser deleted its own second
decoder because *"two implementations only prove a format when something forces them to agree"*, and
nothing did.

**About the config seam.** Mongoose already imports `EventLogControlEvent` and already carries `logLevel`
per processor group in `EventProcessorGroupConfig`. A format setting belongs beside it. The determinism
argument also survives revision 5's correction: the **sink** is a deployment property even though the
record is not, which is precisely what makes the text slice cheap.

**Costs of option B, stated in the first draft and dropped.** Chronicle's roll, retention and low-latency
write path are real properties being given up for the file backends. The configuration documentation must
**say which backend is for which purpose**, so nobody selects the development one for production by
accident. And Mongoose's **release cadence is not controlled from here**, which is a scheduling risk on
release 1 that no amount of design removes.

**Cost of option A.** A Chronicle reader plugin inherits the opaque-reader identity cost: **two extra full
traversals**, because the reader owns its I/O and cannot use the indexing-time digest.

**Acceptance for the renderer move**, if release 2 takes it: the analyser's existing binary fixtures render
**byte for byte identically** before and after, and no second visitor implementation exists anywhere.

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
