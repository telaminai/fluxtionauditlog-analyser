# Evidence-set key — the analyser opens a log with the graph and source that produced it

**Status:** revision 3 (2026-11-03). Revision 2 was reviewed independently (findings referenced as R-x.y) and every owner
decision in §8 is decided; revision 3 records where the implementation departed from it (§9). Issue
[#211](https://github.com/telaminai/fluxtionauditlog-analyser/issues/211). Tracker: *Evidence and bundles ▸ Evidence-set
key*. Spans the Fluxtion runtime, the compiler repository (one fix, §2.2), Mongoose and this analyser.

## 1. Why this first

The analyser's main flow is: open a log, see the graph, walk what happened. That flow is only trustworthy if the graph
shown is the graph that ran, and if one run is not mixed with another. Before this work the analyser guessed both. A
correct main flow makes every later view, including failures, easier to understand.

## 2. Before (verified)

| Artifact | Build key |
|---|---|
| graphml | `fluxtion.sourceFingerprint`, written by the generator |
| generated source | `ProcessorDescriptor.sourceFingerprint()` |
| interpreted processor | **null**: its descriptor is built without one (R-1.2) |
| remote generation | client-computed, copied by the server, never rechecked (R-1.2) |
| audit log | none |
| Mongoose capture sidecar | none; group, name and generation, for routed captures only (R-2.6) |

The analyser's `GraphmlDiscovery` read the graphml fingerprint but paired a log with a graph by comparing logged node
names with declared ones.

### 2.1 What a fingerprint does and does not prove (R-1.5)

The canonical form covers node names, classes, callbacks and edges. It does not cover configured values or node method
bodies. Two builds with the same shape and different code or parameters share a fingerprint (the Mongoose test fixtures
`AlarmProcessor` and `CycleAlarmProcessor` are an example). So a match means **"same graph shape"**, never "same build",
and the analyser words it that way (D4).

### 2.2 Known instability (R-1.3)

A filter id held in a field whose type is neither Number nor String was hashed through `toString()`, so a type without
its own `toString()` changed the fingerprint on every build. Fixed in the compiler repository: such a key is now a build
error, FLX-1010 (§9.5). A processor that still reaches the analyser with no fingerprint shows "graph not verified".

### 2.3 Generation is not a run (R-2.5)

Mongoose's generation is an in-memory counter per agent. A static deployment restarts as `group/name#1` every time.
Generation separates replacements inside one process, not restarts.

## 3. The key

```
graph key  = sourceFingerprint                        (joins log to graphml and source; may be null)
run key    = serverId + runId + group/name#generation (separates runs; Mongoose fills it)
```

- `toolchainVersion` and `runtimeVersion` are information only, not key parts: the graphml and the descriptor derive the
  version differently and may disagree (R-1.4).
- `runId` is a per-process UUID created at server start, with `startedAt`. `serverId` is the configured server name,
  else the host name.

## 4. Design

### 4.1 Generator: hand the descriptor to the audit manager

*Withdrawn in revision 3 (§9.1): no generator change was needed.*

### 4.2 Runtime, text format: a lazy header document

- Written on the first record published after any sink change: the constructor's default sink, `setLogSink`, and a
  format swap through `EventLogControlEvent`. Never at `init`, and never for a stream that publishes nothing.
- Once per stream, never per event; written for every processor (D1).
- Shape:

```yaml
auditStreamHeader:
    schema: 1
    processor: <generated class name>
    sourceFingerprint: <value or null>
    toolchainVersion: <info>
    runtimeVersion: <info>
    startedAt: <ISO-8601>
    host: {serverId, runId, group, name, generation, mongooseVersion}   # from the host; {} outside one
```

- Host fields come from `EventLogManager.setHeaderContext(Map)`, applied to the next header. The normative text form is
  the analyser's [format specification §1b](../site/format-spec.md#1b-the-stream-header-format-12-additive).

### 4.3 Runtime, binary format

*Revised in revision 3 (§9.2):* FLXA version 2 adds one frame, HEADER (`0x03, len:u32, utf8[len]`), carrying the same
text. A version-1 reader refuses version 2 by its number.

### 4.4 Mongoose: name the run

- At server start: create `runId`; resolve `serverId`.
- On capture attach: set the header context `{serverId, runId, group, name, generation, mongooseVersion}`.
- The capture sidecar (routed captures) gains `sourceFingerprint`, `serverId` and `runId`. Keys are additive; `schema`
  stays 1.
- Rotation (R-2.4): the capture rolls daily and core never prunes. Each roll file must be attributable on its own, so
  the capture writes the header again at the start of each roll cycle, and the analyser carries one run across a rolled
  set when the run key is equal.

### 4.5 Analyser: pair by key, warn, segment

- **Tolerance first (R-2.1, verified by running 1.40.0):** a header document was counted as a record and raised
  `NO_RECORD_KEY`, and a header-only file was refused as unrecognised. Now `auditStreamHeader` is recognised as stream
  metadata, not a record, and a header-only file opens as an empty log of that processor. *(Shipped, slice 1.)*
- **Pair** by `sourceFingerprint` first; name matching only when the fingerprint is null or absent.
- **Status line** on the open log, one of:
  - "graph shape matches" (fingerprints equal)
  - "graph shape does not match" (fingerprints differ): a persistent warning, never a refusal (D2), including a forced or
    manual pairing. It makes the coverage claim QUALIFIED, with a reason saying the shape differs (owner decision O3,
    2026-11-03); "graph not verified" does not, because most logs carry no header yet.
  - "graph not verified" (no header, a malformed header, a null fingerprint on either side, or records no header
    covers: before the first header, after a stream-end marker, or at the start of a rolled file, with no header
    immediately before them). Missing evidence is never a mismatch; a proven difference outranks missing evidence.
    When the fingerprints differ and the log's and the graph's builder versions are both known and differ, the
    reason says the difference may be in how the shape was digested rather than in the shape.
  - One shared judgement (`EvidenceVerdict`) decides this for the session, graph discovery and `--verify`.
- **MCP:** results over a pair carry `evidenceSet: MATCH | MISMATCH | UNVERIFIED` with both fingerprints, worded as graph
  shape — exactly these: `context.graphPairing`, the `open {graphml}` echo, `coverage`, and each candidate of
  `open {discover: "graphml"}`. `topology` and `read` do not carry it; an agent reads it from `context`. The MCP
  instructions tell the assistant to state the status before explaining anything.
- **Segments:** a new segment starts only when the **run key changes**, never on every header: a re-sent header with an
  equal key (a sink or context change, a daily roll) does not split the run. An empty host map continues the run in
  force and never starts or names one; a log whose headers are all empty is one segment until a header names a key. A
  stream break (a stream-end marker, the start of a rolled file) ends the stream: records after it with no header
  immediately before them are a segment of their own, and a header after it with the same run key continues the run.
  A header the analyser cannot read starts a segment labelled "run identity unreadable". Segments are labelled
  `serverId · runId(first 8) · group/name#generation`.
- **Bundles** record the graph key and run key of every file.

## 5. Compatibility and ship order

1. Analyser: text tolerance (header recognised, header-only file opens) and the binary reader for the new version.
2. Mongoose admin web: its capture export pages by document count; exclude the header from `from`/`limit` counting and
   render it as metadata.
3. Fluxtion runtime: text header, FLXA HEADER frame, host context.
4. Mongoose: run identity, header context, sidecar keys, header per roll cycle.
5. Analyser: pairing, status line, MCP field, segments, bundles.

Logs without a header behave exactly as before, labelled "graph not verified". The runtime that reads FLXA version 2
must reach the analyser before any producer writes version 2: from runtime 1.1.4 `BinaryLogWriter` writes version 2
unconditionally.

## 6. Out of scope

- Reading Mongoose 1.1 control-plane evidence.
- Proving "same build" (code bodies, parameters): that would need a second digest over the generated source.

## 7. Acceptance

1. A generated processor's text stream begins with a header whose fingerprint equals its graphml's; an interpreted
   processor writes `sourceFingerprint: null`.
2. A binary stream carries the same fields; an old reader refuses it by version.
3. A folder with two graphml builds of different shape: the log pairs with the matching one.
4. Forced pairing with a different shape: a UI warning; MCP results carry `evidenceSet: MISMATCH`.
5. A log with no header opens exactly as before, labelled "graph not verified".
6. A static Mongoose deployment restarted twice: three segments, distinguished by `runId`, each `#1`.
7. A capture across a daily roll: one run, one segment, across both files.
8. A header-only file (an idle processor) opens as an empty log of that processor.
9. A processor whose filter id has an unstable `toString()`: now a build error (FLX-1010); a log that reaches the
   analyser with a null fingerprint is shown "graph not verified".

Items 3–9 are the analyser's; where each is tested is listed in the slice reports under `docs/handoff/`.

## 8. Owner decisions

- D1 **Decided:** the header is written for every processor, once per stream.
- D2 **Decided:** warn on mismatch; never refuse.
- D3 **Decided:** the analyser tracker order stays as is; this runs in parallel.
- D4 **Decided 2026-11-03:** a match is worded "graph shape matches", never "same build" (R-1.5).
- D5 **Decided 2026-11-03:** `runId` and `serverId` are in the key, so restarts of a static deployment are
  distinguishable (R-2.5).
- O3 **Decided 2026-11-03:** a graph-shape MISMATCH makes the coverage claim QUALIFIED; UNVERIFIED does not.

## 9. Revision 3 — where the implementation departs from revision 2, and why

1. **No generator change (§4.1 withdrawn).** Generated code already registers its `DataFlowContext` with every auditor;
   `EventLogManager` reads the processor's descriptor through it at the first header. Every processor names itself
   whichever builder generated it, including processors already compiled into an application jar, and generated source
   keeps compiling against older runtimes.
2. **The binary key is a HEADER frame, not the 8-byte file header (§4.3).** The file header is written when a
   `BinaryLogWriter` is constructed, before any descriptor is known. FLXA's versioning already required a version bump
   for a new frame type, so version 2 is one new frame (`0x03`); a version-1 reader refuses version 2 by number.
3. **Header delivery: `LogRecordListener.processStreamHeader` (a new default method).** A text stream's header is
   delivered to a sink as an ordinary record; a binary stream's is not delivered to a sink that does not override it,
   because a binary sink casting every record to `BinaryLogRecord` would otherwise throw. Consequence measured in the
   analyser: any text sink that counts records counts the header unless it overrides the method.
4. **The run context is stated while the processor is being built.** Mongoose installs the listener and the log level
   while building the processor; both are audited cycles, so applying the context afterwards produced a first header
   with no run. An equal context does not re-head the stream.
5. **Filter keys (§2.2):** a non-stable key also broke dispatch, not only the fingerprint; it is now a build error
   (FLX-1010) on source-generating builds. Enum and Class keys keep dispatching on `toString()`; only the fingerprint
   input is canonicalised.

## 10. Analyser notes (slice 5)

- The verdict and the runs are the session processor's (`evidenceSet` node, rule 9): the adapter's evidence scan reports
  the log's headers as a fact (`StreamHeadersObserved`), the graph's fingerprint arrives on `GraphOpened`, and a Follow
  append that moves the header count is content, so it asks for a scan.
- The analyser does not read Mongoose capture sidecars (`.mongoose-capture.properties`) anywhere, so their new keys
  are not cross-checked; nothing was added to read them.
