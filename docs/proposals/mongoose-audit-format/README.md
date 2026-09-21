# Reading Mongoose audit output without an export step

_Status: **PROPOSAL, 2026-09-21. Not implemented, not reviewed, not owner-approved.** Filed in the holding
pen because the preferred option's work lands in Mongoose and the runtime, not here. Raised by the owner
after repeated friction reading Mongoose audit logs. Companion:
[trust structure](../../specs/spec-trust-structure.md) (D-T8, D-T9),
[tool agreement](../../specs/spec-tool-agreement.md),
[source adapters](../../specs/spec-source-adapters.md)._

_**Revision, 2026-09-21.** An independent session tested the one claim in the first draft that could be
tested, and three premises were wrong or incomplete. Their corrections are folded in below and marked
**OBSERVED**. The recommendation is unchanged; one of its stated reasons was false and has been removed._

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
- `backend: file`, `format: text` — the YAML the analyser has always read. Maximally debuggable,
  greppable, diffable, and the format most people already have fixtures in.
- `backend: file`, `format: binary` — a `BinaryLogWriter` onto a file. The analyser reads it in core
  today. Compact, with a normative specification and a conformance suite already in place.

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

**Option B, text first, binary immediately after.** It is the smallest change that removes the friction, it
needs no new dependency on either side, and both halves already exist. Ship text first because it needs no
decoder at all and is the easiest thing to verify by eye.

**Option A is worth building later, as a plugin, and for a different reason than convenience.** Reading an
existing queue in place is a capability option B cannot provide: a captured queue from a server that has
already shut down, or one produced by a deployment nobody can change. That is the forensic case, and it
justifies a plugin's dependency cost. It should not be the answer to local development friction.

**Option C only if B is refused**, and then as a request-response endpoint, recorded as a decision rather
than reached as a default.

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
2. **Truncation.** *Proposed: per requirements 2 and 3 above* — terminate per record, explicit end marker,
   and the analyser carries a fixture for each case: a clean end, and a process killed mid-run.
3. **Hot-path cost.** *Open.* Nothing has been measured. It should be measured rather than assumed, and the
   configuration documentation should say which backend is for which purpose.
4. **The existing export.** *Proposed: keep it, for Chronicle only.* It should not become a second route to
   the same file for the file backends.
5. **Backends or a format field.** *Proposed: a format field.* Text and binary produce the same kind of
   file and differ only in encoding, so `backend` stays the storage mechanism and `format` the encoding.

## Recorded separately, not this proposal's scope

- **The `/ws/audit-tail/{processor}` socket is a Mongoose defect** whatever option is chosen. It reports a
  healthy connection and delivers nothing.
- **Port 8181 is hard-coded in seven places** in the demo bundle's scripts and documentation. Hosted test
  runs only worked by editing `listenPort` in `config/server-config.yml`. The scripts should read the port
  from that config or from the server registry.

## Supporting context, not evidence for this proposal

A virgin-session run adding two new nodes on port 18282 completed end to end in 126 seconds and one build.
The remaining friction on that path was the export step itself, which is what this proposal removes. **n=1**,
and under this project's own adoption threshold a single observation is a hypothesis rather than a finding.

## What was read, what was run, and what was not

**Read in source:** the Mongoose capture service's write path and its no-op listener, the boot and
configurator listener wiring, the audit capture config and its `backend` field with its call sites; the
runtime's binary writer, reader, record and decoder class surfaces; the analyser's reader SPI, its two
shipped readers and their imports, and the conformance tests.

**Run:** the text-backend test described above, by an independent session, with sealed predictions.
Its three confirmations were re-verified here against source and against the raw evidence files rather
than accepted from the report: `getBackend()` has no call sites, `ws-tail.log` records zero messages, and
`export.yaml` terminates mid-record with no trailing byte.

**Not run:** nothing was benchmarked. Chronicle's own API was not read in detail, and no file-backend
implementation exists to measure. The hot-path cost in Q3 is unmeasured and is marked as such.
