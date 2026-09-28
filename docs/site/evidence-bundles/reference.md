# Commands and file format

A bundle is **written by the running analyser** and **read by two headless commands**. Writing needs the live
session, because every refusal is a fact only it holds. Reading needs nothing but the file.

## Writing: one operation on the analyser

```
report {bundle: {path, notes?, from?, to?, replay?}}
```

| field | meaning |
|---|---|
| `path` | the `.fexp`, inside the exchange directory; never overwritten |
| `notes` | your account, packed as `notes/NOTES.md` |
| `from`, `to` | epoch millis: pack only that window of records, as an excerpt ([Sending](sending.md#an-excerpt-only-the-part-that-matters)) |
| `replay` | the path of the run's replay records, written by a replay writer in the same run as the log. Like every file the analyser reads for an assistant, it must be **inside the exchange directory** (a relative name resolves there), or a file you picked this session. Packed as the `replay/` member. They must pair with the log: each record is one of the log's records, at its `eventTime`, in order. Refused with `from`/`to`, while the log is still growing, or when they do not pair. The log's exported-service calls are counted, because replay records do not carry them |

The echo says `phase: WRITING`. **`context.capture`** then says `WRITTEN`, with the `identity` and `lines` (what
was left out, redacted or excerpted), or `REFUSED`, with the `reason`. A refusal the analyser can make at once, such
as no log open or a load pending, is the verb's error.

## Reading: three commands

An installed analyser is `analyser …`; from a jar, `java -jar fluxtion-auditlog-analyser-<version>.jar …`.

| command | what it does | exit code |
|---|---|---|
| `--verify <bundle.fexp>` | checks every member against the manifest without extracting anything. Prints the identity, `verified: N members…`, `excerpt: …` for an excerpt, `replay: …` for a bundle with replay records, and the limits | 0 · 1 refused, naming the member · 2 usage |
| `--unpack <bundle.fexp> [--into <dir>]` | verifies, then extracts into a **new** directory named for the identity. Prints `working copy:` | 0 · 1 refused, nothing extracted · 2 usage |
| `--replay-compare <bundle.fexp> <replayed-audit.yaml>` | verifies, then compares the bundle's log with an audit log written by replaying its `replay/` records into your own build of the processor. Prints `replay: AGREES, N of N records`, or `replay: DIVERGES at …` naming the first difference, and the limits | 0 agrees · 1 diverges, or refused · 2 usage |

Verification refuses, naming the member:

- a **changed** member (sha256 or size);
- a **missing** member;
- an **unlisted** member;
- a **duplicated** entry;
- a path that **escapes**: absolute, `..`, a backslash, an empty segment;
- a manifest that is missing, not the first entry, duplicated, larger than 4 MiB, unreadable, or of another format;
- a member **larger than its declared size**, refused as soon as it exceeds it.

Verification streams each member through a fixed buffer, so it needs the same small amount of memory for a 4 KB
log as for a 150 MB one. A member the manifest does not list is refused without being read. `--unpack` verifies the
whole bundle before it writes anything, then extracts in a second pass, checking every member again as it writes.

`--pack` and `--bundle-profile` existed in the first build and were removed: a bundle assembled by hand would skip
the checks only the running analyser can make. They now exit 2 and say so.

## The manifest (format 1)

```json
{"format":1,"createdAt":"2026-09-28T12:00:00Z","analyser":"1.27.0",
 "log":{"member":"log/demo-quote-audit.yaml"},
 "graph":{"member":"graph/demo-quote-processor.graphml"},
 "members":[{"path":"graph/demo-quote-processor.graphml","sha256":"…","bytes":12653},
            {"path":"log/demo-quote-audit.yaml","sha256":"…","bytes":4053},
            {"path":"notes/NOTES.md","sha256":"…","bytes":…},
            {"path":"profile/project.fluxtion-settings","sha256":"…","bytes":…}],
 "limits":["unsigned: verification detects a changed member; it does not authenticate the sender",
           "no replay: this bundle shows an investigation; it does not reproduce or fix it"]}
```

- The keys come in this fixed order, and the members are sorted by path, so a given folder packed at a given time
  always gives the same bytes.
- **The identity is `sha256:` of the manifest's exact bytes.** It is never stored inside the manifest. A manifest
  re-serialised with the same content is a different bundle.
- The walk and report fingerprints in the profile member already record the log's provenance and record count, so
  the manifest does not repeat them unverified.
- **An excerpt adds `excerpt`** after `graph`: `{"firstRecord":4,"lastRecord":8,"sourceRecords":10,"from":…,"to":…}`.
  A recipient never reads a slice as the whole log. A log captured while still growing adds `"readSoFar":true`, and
  `sourceRecords` is then the number of records read.

## The manifest with replay records (format 2)

A bundle that carries replay records is **format 2**; every other bundle stays format 1, byte for byte. It adds
`"replay"` after `graph` and states its own second limit:

```json
{"format":2, …,
 "replay":{"member":"replay/demo-quote-recorded.replay.yaml","records":7,"serviceCalls":0},
 "members":[…],
 "limits":["unsigned: verification detects a changed member; it does not authenticate the sender",
           "replay: the recorded inputs reproduce this log only on a build whose graph matches, and only as far as the processor reads nothing the records do not carry"]}
```

- A format 2 manifest must name its `replay/` member, and list it; verification refuses one that does not.
- The replay records are the run's inputs only, each stamped with the instant its cycle ran at. The events the graph
  raised itself are in the log and not in the replay records.
- **The analyser does not replay them.** You replay the records into your own build of the processor with the
  runner below; `--replay-compare` then says whether that log matches, which is the claim in the limit and not more.

## Replaying replay records: the runner

```
jbang tools/replay/ReplayBundle.java --bundle breach-0900.fexp \
      --processor com.acme.demo.generated.DemoQuoteRecordedProcessor --cp target/classes --out replayed-audit.yaml
```

It runs your build, which is why it is a separate program and not part of the analyser. It checks the build first,
feeds the replay records in, and writes the audit log `--replay-compare` reads:

- **Is your build the bundle's processor?** It compares the nodes and edges of your build's GraphML
  (`<Class>.graphml`, written by the generator beside the class) with the bundle's `graph/` member. It refuses
  naming the difference. `--skip-graph-check` goes on anyway and prints `graph: NOT checked`.
- **Only your build's event types.** A replay record is read only as one of the event types your processor's
  `handleEvent` methods take. Any other type is refused and never loaded.
- **The recorded instant.** Each record is fed in on a data-driven clock set to its instant, so the processor reads
  the time the run read. The events the graph raised itself are raised again by the graph, not fed in.

Exit 0 when the replay records were fed in and the audit log written; 1 refused, naming why; 2 usage.

## `--replay-compare`: comparing a replayed log

```
analyser --replay-compare breach-0900.fexp replayed-audit.yaml
```

`--replay-compare` checks every record, and every line of it, exactly, **except `endTime` and `thread`**: when and
where a cycle ran, never what it computed. `endTime` is the live clock reading at the end of a cycle, and a replay pins
the clock at the recorded instant. `thread` names the thread the cycle ran on, and your replay runs on its own. Nothing
else is excepted: an input's `eventTime`, the times of an event the graph raised itself, and every node's values must
all agree. The exceptions are by position, so a record whose `endTime` line is missing on one side still differs.

- **Agrees:** `replay: AGREES, 8 of 8 records (endTime and thread excepted, differing on 8: when and where a cycle
  ran, which a replay cannot know)`.
- **A node computed something else:** `replay: DIVERGES at record 1 (OrderUpdateEvent):
  eventLogRecord.nodeLogs.orderTracker: '{ orderId: ord-1, live: 1}' ≠ '{ orderId: ord-1, live: 7}'`.
- **A build that stopped raising an event:** `replay: DIVERGES at record 7: the bundled log has record 7
  (RiskBreachEvent), and the replay does not (8 records bundled, 7 replayed)`.

It is refused, comparing nothing, when the bundle fails verification, carries no replay records, or holds an
excerpt: replay records are of the whole run.

## Context fields

- `context.capture`: the last capture, as the session decided it: `phase`, `path`, `identity`, `reason`, `lines`.
- `context.log.generation`: the session's log generation, the one a capture is decided in.
- `context.project.unsavedEdits`: `true` while a project edit waits for its write to the profile file. A capture
  does not wait for it: it writes the pending edit itself.
