# Reading Mongoose audit output without an export step

_Status: **PROPOSAL, 2026-09-21. Not implemented, not reviewed, not owner-approved.** One required slice —
Mongoose writes a directly readable audit file — plus two independent ones: the broken audit-tail socket,
and an optional renderer move that is recorded but not recommended. Filed in the holding pen because the
work lands in Mongoose and the playground, not here. Raised by the owner
after repeated friction reading Mongoose audit logs. Companion:
[trust structure](../../specs/spec-trust-structure.md) (D-T8, D-T9),
[tool agreement](../../specs/spec-tool-agreement.md),
[source adapters](../../specs/spec-source-adapters.md)._

_**Revision 1, 2026-09-21.** An independent session tested the one claim in the first draft that could be
tested, and three premises were wrong or incomplete. Their corrections are folded in below and marked
**OBSERVED**. One stated reason was false and has been removed._

_**Revision 4, 2026-09-21 — the format is a deployment property, and one obstacle was my error.** The
owner challenged two claims and was right on both. Selecting binary is *not* build-time only: the control
event carries a `LogRecord` and a `LogRecordListener`, and both swap live, so no closed-compiler change is
needed. And the renderer move is demoted from required to optional rather than tying a specification to
the public runtime's release cadence. **Two repositories carry the friction fix.** See **The format is a
deployment property**._

_**Revision 3, 2026-09-21.** At the owner's request the renderer move and the broken `/ws/audit-tail`
socket are raised here rather than filed separately, because they form one dependency chain with the
format change. A likely cause for the socket is offered from source, with the questions that would
confirm or refute it left for whoever can reach the running server. See **Three slices, one dependency
chain**._

_**Revision 2, 2026-09-21 — the ordering is reversed: binary first, then text.** The owner asked where a
shared module would live and pointed out that the analyser's text parser is lenient. Both questions have
answers that change the plan. There is no new module: `fluxtion-runtime` already holds the writer, the
reader and the round-trip tests, and both consumers already depend on it. And leniency makes text the
riskier format to introduce first, not the safer one. See **Where the code lives** and **Why binary goes
first**._

## The friction, stated precisely

A Mongoose user who wants to look at what their system did must: run the server, stop it or reach an admin
endpoint, run an export to convert the capture into analyser-readable YAML, then open that YAML. The export
exists solely because the analyser cannot read what the server wrote. Every hosted journey pays it, and it
is a step where a first-time user can simply stop.

## What is already true

Facts marked **read** come from source. Facts marked **OBSERVED** were produced by running a server: a copy
of the Spring demo bundle on port 18283 with `performanceMonitoring.auditCapture.backend: text`, against
sealed predictions X1–X3. Evidence is machine-local and git-ignored at
`.local-evidence/coldstart-v2-2026-09-20/text-backend-2026-09-21/`.

| Fact | Basis |
|---|---|
| Chronicle capture writes **the same YAML text** the analyser already parses, one record per excerpt: `CharSequence yaml = record.asCharSequence()` then `ctx.wire().getValueOut().text(yaml)` | read — mongoose `ChronicleAuditCaptureService.java:259-261` |
| The audit listener is **already a boot parameter**: `bootServer(Reader, LogRecordListener)` and `bootFromConfig(MongooseServerConfig, LogRecordListener)`, applied via `eventProcessor.setAuditLogProcessor(logRecordListener)` | read — mongoose `MongooseServer.java:269`, `ServerConfigurator.java:39,126` |
| **`BinaryLogWriter implements LogRecordListener`** and takes an `OutputStream` | read — fluxtion-runtime `audit/BinaryLogWriter.java` |
| Mongoose already depends on **fluxtion-runtime**, so that writer needs no new dependency | read — mongoose POM |
| The analyser **already reads the binary FLXA format in core**, not as a plugin, importing only `java.*` plus three fluxtion-runtime classes | read — analyser `spi/binary/BinaryAuditReader.java:1-7` |
| A conformance suite already exists that both shipped readers pass | read — analyser `FormatConformanceTest`, `FlxaConformanceTest` |
| **`backend` is never read.** `MongooseServer` constructs `ChronicleAuditCaptureService` whenever `auditCapture.enabled`, whatever `backend` says. `backend: text` was accepted silently and a Chronicle `.cq4` was written. The web admin plugin has no audit-format setting either. | **OBSERVED**, and confirmed in source: `MongooseServer.java:210-218` selects unconditionally, and `getBackend()` has **zero call sites** outside its own declaration |
| **The `/ws/audit-tail/{processor}` socket delivers nothing.** Zero messages, including for two rows appended *after* the client connected; the export confirms both were processed. Not merely tail-from-end. | **OBSERVED** — `ws-tail.log` records `socket open` then `messages received: 0` |
| **The export's last record is unterminated.** The file ends mid-record with no trailing newline. | **OBSERVED** — `export.yaml` ends at byte `…3580` with no terminator |
| The `/api/audit/files` listing was **not** stale in this run: `recordCount` 25 matched 25 exported records. | **OBSERVED** — any claim of listing staleness is **unreproduced as of 2026-09-21** |

Three consequences follow, and together they decide this proposal.

**Chronicle is a container, not a format.** It is holding YAML the analyser can already parse. So the
question was never "can the analyser understand Mongoose's format". It is "must that text arrive inside a
Chronicle queue".

**Both ends of the cheap option already exist.** A writer that satisfies Mongoose's listener interface and
produces a format the analyser reads in core, with no new dependency on either side, is already written.
What is missing is one wire between them.

**The first draft was wrong about the config field, in the direction that flattered the proposal.** It said
option B "reuses a config field that exists and currently has one legal value". The field exists and has
**no effect at all**. That correction came from running the server, not from reading it, and the error came
from treating a javadoc as a statement about behaviour. It makes option B slightly larger and adds a
requirement it did not have.

## Where the code lives — no new module is needed

Both repositories already depend on **`fluxtion-runtime`**, and everything required is already inside it.
Verified in both POMs: the analyser declares it (it arrived with the session processor, bringing agrona
transitively) and Mongoose declares it.

| Piece | Where it already is |
|---|---|
| The text form | `LogRecord.asCharSequence()` |
| The binary writer, already a `LogRecordListener` | `audit/BinaryLogWriter` |
| The binary reader and decoder | `audit/BinaryLogReader`, `audit/BinaryRecordDecoder` |
| Round-trip and text-versus-binary tests | runtime `BinaryLogFileRoundTripTest`, `BinaryRecordRoundTripTest`, `BinaryVersusTextRecordTest` |

So writer and reader are a matched pair in one module, round-trip tested there, with both consumers
downstream. No new artifact, no new coordinate, no version coupling to negotiate. The analyser already
made the corresponding call deliberately: it first shipped a second decoder written from the
specification and the owner reversed it, on the reasoning that *"two implementations only prove a format
when something forces them to agree"* — nothing did, because both were written from the same reading of
the same document. **One tested implementation, in the shared module, is the standing decision.** Any new
code here follows it.

## Why binary goes first — the leniency argument

The first draft recommended text first, because it needs no decoder and is easy to check by eye. That
reason survives and is now outweighed.

**The analyser's text parser is deliberately lenient.** Its record parser ignores an unknown top-level
scalar and keeps it in `rawText`. That is correct for a reader meeting other people's files. It is
dangerous for accepting a writer we control, because a text writer emitting something subtly
non-conformant produces a file that opens, parses and displays, with fields quietly missing and no error
anywhere. That is the exact failure shape D-T8 and D-T9 exist to prevent.

**The conformance suite does not close that gap.** Read its own header: each fixture runs twice, through
the built-in text path and through the SPI path, and the two must agree record for record. It is a
**reader** conformance suite. Nothing in it asserts that a given writer emits conformant text, so a new
text writer would have no gate, and the lenient reader would hide its mistakes.

**Binary has the opposite property, and it is already shipped.** Frames are length-prefixed with an
interned dictionary, so damage is structural rather than plausible. The analyser's binary reader already
reports, as source diagnostics, that a tail did not form a whole record — *"a process that stopped
mid-write, or a damaged tail. Every record before them is here; the one they belong to is not"* — plus
dictionary redefinition and unresolved ids, the latter shown as *"unknown, not empty"*. **Open question 2
is therefore already answered for binary, in shipped code**, and unanswered for text.

**And binary needs less new code.** `BinaryLogWriter` already *is* a `LogRecordListener`. Text would need
a new adapter class to call `asCharSequence()` and write it.

## The format is a deployment property — corrected, and it removes an obstacle

**Revision 4 corrects an error of mine, and the correction makes this materially smaller.**

The previous revision said selecting binary was a build input, so a hosted processor had to be
*generated* to emit binary records, which pushed part of slice 1 into the closed compiler because
`FluxtionSpringConfig` carries `logLevel` and no format. That obstacle does not exist.

I had grepped `EventLogControlEvent` for a field called `format`, found none, and flagged
`EventLogManager`'s claim of a runtime swap as a possibly stale comment. **The field is called
`logRecord`.** The event carries both:

- `private LogRecord logRecord` with constructor `EventLogControlEvent(LogRecord)`
- `private LogRecordListener logRecordProcessor` with constructor `EventLogControlEvent(LogRecordListener)`

and `EventLogManager` swaps both live at `:215-224`, preserving log level and buffer across the
replacement. So a running processor's **record format and its sink are both settable at runtime**. The
comment was right; the discrepancy was my bad search, and the earlier revision's "unverified" flag on it
should be read as withdrawn rather than unresolved.

**Why this is the right shape, not merely a cheaper one.** The format is a property of a deployment, not
of a graph. The same declared graph should run in development writing text and in production writing
binary, with only the observation differing. Baking it into `FluxtionSpringConfig` would mean recompiling
an artefact to change a log format, and would make two deployments of one design produce two different
binaries — which cuts against the determinism argument the product rests on.

**Consequences:**

- **No closed-compiler change.** `FluxtionSpringConfig` needs no audit-format field.
- **No public-runtime change.** `binaryRecord` at build time stays what its own comment says it is: the
  way to *start* in the right format, not the only way to be in it.
- **The config seam already exists.** Mongoose already imports `EventLogControlEvent` and already carries
  `logLevel` per processor group in `EventProcessorGroupConfig`. A format setting belongs beside it.
- **The sink is settable the same way**, which means the capture service's no-op listener is a live
  override rather than a fixed design, and may make the implementation risk below smaller than stated.

## What this actually costs, per repository

| Repository | Slice | Work |
|---|---|---|
| **mongoose** | 1 | Send the control event at boot with the chosen record type and the file writer as sink; validate `backend`/`format` and refuse unknown values by name |
| **fluxtion-web** | 1 | Surface the choice in templates, runbooks and generated documentation |
| **mongoose-plugins** | 3 | The audit-tail producer and a client that opens it |
| fluxtion *(public runtime)* | — | **None.** Writer, reader, decoder, round-trip tests and the runtime swap all exist |
| fluxtion-compiler *(closed)* | — | **None**, once the format is a deployment property |
| fluxtionauditlog-analyser | — | **None.** It already reads the binary format in core |

**Two repositories carry the friction fix.** That is the whole of slice 1.

## Three slices, and only the first is required

Slice 1 stands alone and removes the friction by itself. The other two are worth doing on their own
merits and neither blocks it.

### Slice 2 — move the record renderer into the runtime · *optional, deferred*

An earlier revision made this a required prerequisite, on the reasoning that a binary-backed web admin
would otherwise need its own binary-to-text conversion. **With the format a deployment property, that
case mostly disappears:** a deployment that wants a live web-admin view chooses text, the records are
already text, and `asCharSequence()` is on the record. A deployment that chooses binary is choosing
offline analysis, which the analyser already does with its own renderer.

What remains is the narrow case of live-viewing a deployment configured for binary. That is not the
friction this proposal exists to fix, and the owner's objection stands: the renderer would live in
`fluxtion-runtime`, whose release cadence is deliberately slow, and tying a specification to it for a
narrow case is a poor trade.

**Recorded, not recommended.** If it is ever wanted, the shape is settled: move the analyser's private
`RecordTextRenderer` beside `BinaryLogReader` rather than writing a second one, per the one-implementation
rule. Until then the analyser keeps it private and nothing is lost.

### Slice 3 — fix `/ws/audit-tail/{processor}` · *independent*

**Observed:** the socket reports a healthy connection and delivers zero messages, including for two rows
appended after the client connected, both confirmed processed by the export.

It no longer depends on slice 2: with text deployed, the tail carries the records as written.

**A likely cause, read in source and offered as a hypothesis rather than a diagnosis.** In the
mongoose-plugins checkout available here, `3f5fd03` dated 2026-09-14:

- The **consumer half exists and is specified.** `web/replay/eventlog-parser.js:136-139` documents the
  contract — *"Frame from `/ws/audit-tail/{processor}` is a JSON array of one or more record objects"* —
  and `parseFrame` implements it.
- **Nothing opens that socket.** The only two `new WebSocket(...)` calls in `app.js` are `/ws/monitor`
  (line 4295) and `/ws/logs` (line 4373).
- **No producer exists for it.** `/ws/logs` is fed by `LogTail` and `/ws/monitor` by `MonitoringSampler`;
  there is no equivalent class for the audit tail, and `audit-tail` appears nowhere in the Java sources.

That fits the observation exactly: a connection is accepted and nothing ever publishes to it. **Caveat:
this checkout may be older than the server that was tested**, so this is where the hypothesis needs
confirming rather than believing.

**What the fix looks like, if the hypothesis holds.** A producer class in the shape of the two that
already work, publishing frames in the contract the client parser already specifies, and a client that
opens the socket.

**Acceptance.** A client connected before the run receives records appended during it; a client connected
mid-run receives subsequent records; the count delivered equals the count exported for the same window.
The last of those is the one that matters, because it is the assertion that would have failed today.

### Open questions for whoever confirms this against the running server

Recorded here rather than guessed, because they decide the shape of slice 3:

1. **Does `/ws/audit-tail/{processor}` exist server-side in the tested build**, and if so which class
   serves it? If it does exist, this hypothesis is wrong and the cause is elsewhere.
2. **Does the connection complete a WebSocket upgrade**, or does the client see a socket that opened
   against a route that does not exist? The evidence records `socket open` without distinguishing these.
3. **Is the `{processor}` path segment matched against anything**, and what happens for an unknown
   processor name? A silent empty stream for a misspelled name is the same defect class as `backend`.
4. **Is the frame contract in `eventlog-parser.js` the intended one**, or has it moved? The fix should
   satisfy the documented contract or change it deliberately.
5. **Was audit capture enabled in the tested run**, and is the tail sourced from the capture service or
   from the live listener? If from the capture service, slice 1 changes what it reads.

## The options

### Option A — a Chronicle reader plugin for the analyser

Implement the reader SPI against Chronicle Queue: `formatId`, `displayName`, `canOpen`, `timeBase`,
`capabilities`, and a `read` that walks excerpts and hands each text value to the consumer. The payload
needs no parsing work, so the reader itself is small, on the order of days.

**Against.** It pulls Chronicle Queue and its transitive dependencies into whatever ships it, needs
memory-mapped access and JVM access flags — the same flags the Mongoose launcher passes, which is why
`java -jar` fails there — and couples the reader's Chronicle version to the server that wrote the queue.
Shipping it in core would contradict the analyser's one-UI-dependency-otherwise-the-JDK position, so it
would have to be a plugin, which is what the reader SPI exists for. It also inherits the opaque-reader
identity cost: two extra full traversals, because the reader owns its I/O and cannot use the
indexing-time digest.

**For.** It is the only option that reads an existing captured queue in place, including one produced by a
server nobody can rebuild. That is a real capability and it is not obtainable any other way.

### Option B — Mongoose writes a directly readable audit file for local development *(recommended)*

Select the `LogRecordListener` that is already a boot parameter, and write a file the analyser opens
directly. Per Q5 below, the encoding is a **format** field and `backend` keeps meaning the storage
mechanism:

- `backend: chronicle` — unchanged default, unchanged behaviour.
- `backend: file`, `format: binary` — **ship this first.** `BinaryLogWriter` is already the listener
  interface, the analyser reads it in core today, damage is structural rather than silent, and the
  truncation diagnostics are already written.
- `backend: file`, `format: text` — second, and gated on a writer conformance check rather than on
  reading the output. Debuggable, greppable, diffable, and the format most fixtures are already in.

**Requirements, three of which come from the measured findings.**

1. **Validate the field and refuse unknown values at boot**, with an error naming the legal values. This is
   not polish. A setting that is silently ignored is a tool-agreement defect in its own right, and any
   configuration in the world that already says `backend: text` believes it has something it does not.
   Proposed as a decision row in [spec-tool-agreement.md](../../specs/spec-tool-agreement.md): *a
   configuration key the server accepts must either take effect or be refused by name; silent acceptance
   of an inert setting is a disagreement between the tool and its operator.*
2. **The writer terminates every record as it writes it, never on close.** Finding 3 is a live example of
   what the alternative produces.
3. **A run that ends normally writes an explicit end marker.** A file without one reads as incomplete,
   which is stated-unknown under D-T8 and D-T9, never as a short but complete run.
4. **The file writer must be the listener that actually receives records.** See the implementation risk
   below; this is the failure most likely to look like success.

**For.** It deletes the export step rather than working around it. No new dependency anywhere: the writer
is in a library Mongoose already has, the reader is already in the analyser. The path a first-time hosted
user takes becomes run, then open. And it makes the audit file an ordinary artefact: attachable to a bug
report, committable as a fixture, diffable between runs, which the determinism argument depends on.

**Against, and these are real.** Chronicle's roll, retention and low-latency write path are not free
properties to give up. It is a change in another repository, on a server whose release cadence you do not
control from here. And it is now known to need config validation that does not exist today, which the
first draft assumed away.

### Option C — make the export cheaper

Leave both formats alone and reduce the friction around the existing step: a single command, or an admin
endpoint the analyser can fetch from.

**A socket-based version of this is ruled out on evidence.** `/ws/audit-tail/{processor}` delivers zero
messages even for records appended after connect, so "the analyser subscribes to a live tail" is not
available to build on until that is fixed. A request-response endpoint remains possible.

**Against.** It keeps a conversion between a system and the tool built to observe it. Every future reader,
fixture and runbook keeps paying it. It is the option that looks cheapest today and stays forever.

## Recommendation

**Option B, binary first, text second.** It is the smallest change that removes the friction, it needs no
new dependency on either side, and both halves already exist in the shared runtime.

**The ordering reversed in revision 2, and the reason is worth keeping.** The first draft chose text first
because it needs no decoder and reads by eye. Under a lenient parser that is a liability rather than a
virtue: a non-conformant text file opens and displays with fields quietly missing, and no gate exists that
would catch it. Binary needs less new code, fails structurally instead of silently, and already reports a
mid-write stop honestly. Readability helps one person debugging once; leniency hurts every user silently.

**Text remains worth shipping**, second, with its acceptance a round-trip against the conformance fixtures
rather than an inspection of the output.

**Option A is worth building later, as a plugin, and for a different reason than convenience.** Reading an
existing queue in place is a capability option B cannot provide: a captured queue from a server that has
already shut down, or one produced by a deployment nobody can change. That is the forensic case, and it
justifies a plugin's dependency cost. It should not be the answer to local development friction.

**Option C only if B is refused**, and then as a request-response endpoint, recorded as a decision rather
than reached as a default.

**Only slice 1 is required, and it stands alone.** It needs Mongoose and the playground and nothing else.
The socket fix is independent and can proceed in parallel. The renderer move is recorded as optional and
is not recommended, because the case for it mostly disappears once the format is a deployment choice.

## Implementation risk, from earlier work in this project

**The capture service installs its own do-nothing listener.**
`ChronicleAuditCaptureService$NoOpLogRecordListener` is set via `dataFlow.setAuditLogProcessor(...)` at
`ChronicleAuditCaptureService.java:242`. In an earlier session a first run wrote only **one** record until
the listener was passed through `bootServer`. A file writer that is constructed but not installed as the
live listener will produce a file that exists, opens, and is nearly empty.

**Therefore the acceptance test asserts the record count equals the events processed**, not that the file
exists and not that it parses. A file that opens cleanly is the exact shape this failure takes.

## Open questions — proposed answers, owner decides

1. **Rotation.** *Proposed: none.* The local-development file is bounded by the run. That avoids colliding
   with the analyser's rolled-set ordering rules, which would otherwise read a rolled file as an ordering
   fault. Revisit only if the backend is ever intended for production.
2. **Truncation.** *Answered for binary, already shipped:* the reader reports an unreadable tail as a
   source diagnostic naming a stop mid-write, and shows unresolved names as unknown rather than empty.
   *Proposed for text, per requirements 2 and 3 above* — terminate per record, explicit end marker. Either
   way the analyser carries a fixture for each case: a clean end, and a process killed mid-run.
3. **Hot-path cost.** *Open.* Nothing has been measured. It should be measured rather than assumed, and the
   configuration documentation should say which backend is for which purpose.
4. **The existing export.** *Proposed: keep it, for Chronicle only.* It should not become a second route to
   the same file for the file backends.
5. **Backends or a format field.** *Proposed: a format field.* Text and binary produce the same kind of
   file and differ only in encoding, so `backend` stays the storage mechanism and `format` the encoding.

## Recorded separately, not this proposal's scope

- **Port 8181 is hard-coded in seven places** in the demo bundle's scripts and documentation. Hosted test
  runs only worked by editing `listenPort` in `config/server-config.yml`. The scripts should read the port
  from that config or from the server registry.

## Supporting context, not evidence for this proposal

A virgin-session run adding two new nodes on port 18282 completed end to end in 126 seconds and one build.
The remaining friction on that path was the export step itself, which is what this proposal removes. **n=1**,
and under this project's own adoption threshold a single observation is a hypothesis rather than a finding.

## What was read, what was run, and what was not

**Read in source:** the web admin's `/ws/logs` and `/ws/monitor` producers and their two client call
sites, the documented `/ws/audit-tail` frame contract in `eventlog-parser.js` and the absence of any
server-side handler for it in the checkout available here; the Mongoose capture service's write path and
its no-op listener, the boot and
configurator listener wiring, the audit capture config and its `backend` field with its call sites; the
runtime's binary writer, reader, record and decoder class surfaces, `EventLogManager`'s `binaryRecord`
build input and `EventLogControlEvent`'s fields; both POMs' `fluxtion-runtime` dependencies; the
analyser's reader SPI, its two shipped readers and their imports, its record parser's leniency branch,
its binary reader's source diagnostics and private text renderer, and the conformance suite's own
statement of what it covers.

**Run:** the text-backend test described above, by an independent session, with sealed predictions.
Its three confirmations were re-verified here against source and against the raw evidence files rather
than accepted from the report: `getBackend()` has no call sites, `ws-tail.log` records zero messages, and
`export.yaml` terminates mid-record with no trailing byte.

**Not run:** nothing was benchmarked. Chronicle's own API was not read in detail, and no file-backend
implementation exists to measure. The hot-path cost in Q3 is unmeasured and is marked as such. No binary
audit file was produced from a Mongoose server, because no template emits binary records today; the
binary claims here rest on the runtime's own round-trip tests and the analyser's shipped reader, not on
an end-to-end run. **The question revision 3 left unresolved is now resolved against me:** a running processor's record
format and sink *can* both be switched, through `EventLogControlEvent`'s `logRecord` and
`logRecordProcessor` fields and `EventLogManager:215-224`. Revision 3 searched for a field named `format`,
found none, and wrongly doubted a correct comment.
