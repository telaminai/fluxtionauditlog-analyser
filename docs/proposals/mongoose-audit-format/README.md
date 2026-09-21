# Reading Mongoose audit output without an export step

_Status: **PROPOSAL, 2026-09-21. Not implemented, not reviewed, not owner-approved.** Filed in the holding
pen because the preferred option's work lands in Mongoose and the runtime, not here. Raised by the owner
after repeated friction reading Mongoose audit logs. Companion:
[trust structure](../../specs/spec-trust-structure.md) (D-T8, D-T9),
[source adapters](../../specs/spec-source-adapters.md)._

## The friction, stated precisely

A Mongoose user who wants to look at what their system did must: run the server, stop it or reach an admin
endpoint, run an export to convert the capture into analyser-readable YAML, then open that YAML. The export
exists solely because the analyser cannot read what the server wrote. Every hosted journey pays it, and it
is a step where a first-time user can simply stop.

## What is already true — read in source on 2026-09-21

This changes the shape of the answer, so it comes first. None of it is inferred.

| Fact | Where |
|---|---|
| Chronicle capture writes **the same YAML text** the analyser already parses, one record per excerpt: `CharSequence yaml = record.asCharSequence()` then `ctx.wire().getValueOut().text(yaml)` | mongoose `ChronicleAuditCaptureService.java:259-261` |
| The audit listener is **already a boot parameter**: `bootServer(Reader, LogRecordListener)` and `bootFromConfig(MongooseServerConfig, LogRecordListener)`, applied via `eventProcessor.setAuditLogProcessor(logRecordListener)` | mongoose `MongooseServer.java:269`, `ServerConfigurator.java:39,126` |
| **`BinaryLogWriter implements LogRecordListener`** and takes an `OutputStream` | fluxtion-runtime `audit/BinaryLogWriter.java` |
| Mongoose already depends on **fluxtion-runtime**, so that writer needs no new dependency | mongoose POM |
| The analyser **already reads the binary FLXA format in core**, not as a plugin, importing only `java.*` plus three fluxtion-runtime classes | analyser `spi/binary/BinaryAuditReader.java:1-7` |
| A conformance suite already exists that both shipped readers pass | analyser `FormatConformanceTest`, `FlxaConformanceTest` |
| `auditCapture.backend` exists as a config field, documented as *"currently only chronicle"* | mongoose `AuditCaptureConfig.java:52-58` |

Two consequences follow, and together they decide this proposal.

**Chronicle is a container, not a format.** It is holding YAML the analyser can already parse. So the
question was never "can the analyser understand Mongoose's format". It is "must that text arrive inside a
Chronicle queue".

**Both ends of the cheap option already exist.** A writer that satisfies Mongoose's listener interface and
produces a format the analyser reads in core, with no new dependency on either side, is already written.
What is missing is one wire between them.

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

Honour the existing `backend` field with two additional values, selecting the `LogRecordListener` that is
already a boot parameter:

- **`binary`** — a `BinaryLogWriter` onto a file. The analyser reads it in core today. Compact, and the
  format already has a normative specification and a conformance suite.
- **`text`** — the YAML the analyser has always read, written straight to a file. Maximally debuggable,
  greppable, diffable, and the format most people already have fixtures in.
- **`chronicle`** — unchanged default, unchanged behaviour.

**For.** It deletes the export step rather than working around it. No new dependency anywhere: the writer
is in a library Mongoose already has, the reader is already in the analyser. It reuses a config field that
exists and currently has one legal value. The path a first-time hosted user takes becomes run, then open.
And it makes the audit file an ordinary artefact: attachable to a bug report, committable as a fixture,
diffable between runs, which the determinism argument depends on.

**Against, and these are real.** Chronicle's roll, retention and low-latency write path are not free
properties to give up; a plain file writer needs its own answers for rotation and for what happens when the
disk fills. It is a change in another repository, on a server whose release cadence you do not control from
here. And a new backend needs its own D-T8 story: what a truncated or partially written file reports, and
how an abnormal termination presents. It must degrade to stated-unknown, never to a silently short file
that reads as a complete run.

### Option C — make the export cheaper

Leave both formats alone and reduce the friction around the existing step: a single command, or an admin
endpoint the analyser can fetch from directly.

**Against.** It keeps a conversion between a system and the tool built to observe it. Every future reader,
fixture and runbook keeps paying it. It is the option that looks cheapest today and stays forever.

## Recommendation

**Option B, with `text` first and `binary` immediately after.** It is the smallest change that removes the
friction, it needs no new dependency on either side, and both halves already exist. Ship `text` first
because it needs no decoder at all and is the easiest thing to verify by eye, then `binary` for size.

**Option A is worth building later, as a plugin, and for a different reason than convenience.** Reading an
existing queue in place is a capability option B cannot provide: a captured queue from a server that has
already shut down, or one produced by a deployment nobody can change. That is the forensic case, and it is
the one where a plugin's dependency cost is clearly worth paying. It should not be the answer to local
development friction.

**Option C only if B is refused**, and it should be recorded as a decision rather than a default.

## Open questions, which need answers before implementation

1. **Rotation and retention for a file backend.** Chronicle supplies roll cycles and retention; a plain
   file does not. Does the local-dev backend rotate at all, or is it bounded by the run? The analyser
   already has a rolled-set concept with ordering validation, so if rotation happens the two models must
   line up or a rolled file will read as an ordering fault.
2. **What a truncated file reports.** D-T8 and D-T9 both bear here. A server killed mid-write must produce
   a file that says it is incomplete, not one that looks like a short but complete run.
3. **Whether the per-record write cost is acceptable on the hot path** for the intended use. This is a
   local-development backend, so the bar is lower than production, but it should be measured rather than
   assumed, and the config documentation should say which backend is for which purpose.
4. **Whether the admin endpoint's export stays** once a direct format exists. Keeping it is probably right
   for the Chronicle path; it should not become a second way to do the same thing for the file backends.
5. **Whether `binary` and `text` are backends or one backend with a format field.** A format field is
   probably cleaner, since both are "a file the analyser reads" and they differ only in encoding.

## What I read, and what I did not

Read in source: the Mongoose capture service's write path, its boot and configurator listener wiring, the
audit capture config and its documented backend values; the runtime's binary writer, reader, record and
decoder class surfaces; the analyser's reader SPI, its two shipped readers and their imports, and the
conformance tests.

Not read: Chronicle Queue's own API in any detail, the admin export endpoint implementation, and the
runtime's binary format specification beyond the reader's references to it. Nothing here was built, run or
benchmarked, and no measurement in this document is claimed as observed.
