# Review, round 4 — `spec-mongoose-audit-production.md` at `6be31f58` (reviewer: claude)

**Verdict: CHANGES REQUIRED, and narrower than any earlier round.** The rewrite works:
- there is one `## Ordering`, at top level (`:51`);
- there is one definition of MA-1;
- `customHandler` is nowhere treated as central;
- `complete` is described as true everywhere it appears.

**MA-0 and MA-5 can start once a few lines are fixed. MA-1 needs one missing trigger and an escape
hatch. MA-2 is still not buildable**, but it is blocked anyway, and the fixes are now specific.

**The sixth add-without-reconcile defect is present, and it is partly mine.** D-MA0b says "key it on zero
records only … stated so nobody widens it" (`:80-84`). AFMT-3's gate says it can be cleared by "MA-0
grows a check for a document carrying no `eventLogRecord:` key" (`:192-193`). I proposed both in round 3.
They contradict each other, and the second route cannot satisfy MA-2.5 either (F1).

**Three of my own round-3 claims, adopted by the spec, are wrong. I correct them here:**
- the bundle-digest pin (F2);
- the Follow status-text rationale (F4);
- "the report" as an existing surface for producer findings (F4).

Reviewed in my own worktree at `6be31f58` on branch `review/mongoose-audit-production-r4`. Nothing in the
spec was edited, implemented, merged or released. Only this file is committed.

---

## F1 · High · The AFMT-3 gate contradicts D-MA0b, and one of its two routes cannot pass MA-2.5 (read)

- `:80-84` D-MA0b: MA-0 needs **only** `index.size() == 0`, "stated so nobody widens it".
- `:192-193` AFMT-3: to clear the gate, "**or** MA-0 grows a check for a document carrying no
  `eventLogRecord:` key".
- `:266` MA-2.5: "A per-node-`NONE` run produces **no marked file that vouches** for a corrupt record."

Under the second route the marked file still reads `complete`, now with a warning beside it. A warning
does not stop a file vouching. So that route fails MA-2.5 as written, and it also breaks D-MA0b.
**Only the runtime fix clears the gate as the acceptance is worded.**

**Required.** Choose one:
1. **Runtime fix only.** Delete the "or MA-0 grows" route. D-MA0b stands.
2. **Keep both routes.** Then:
   - move the no-`eventLogRecord:` check out of MA-0 into its own item (say MA-0b), so D-MA0b stays
     true of MA-0;
   - reword MA-2.5 to "… or the analyser names the corrupt document beside the verdict";
   - add a third option alongside: **the writer refuses to count or mark** a record whose text lacks
     `eventLogRecord:`.

Option 2's last point is cheap and puts the check where the marker is made.

## F2 · High · Pinning by zip digest is unreproducible: my round-3 advice was wrong (RUN)

I downloaded the bundle twice in a row: both 66,269 bytes, sha256 `f7a40fbc…` and `35a81eab…`. The
extracted trees are **identical** (`diff -rq` clean). The zip is generated per request with fresh entry
timestamps (`09-23-2026 18:24`), so its digest changes on **every** download. The spec's
`1eb6062b…` (`:36`) therefore cannot be re-verified by anyone, which is the opposite of what pinning is
for. I recommended it in round 3, and it was wrong.

**Required.** Pin a **content** digest:
- `generated/MarketProcessor.java`: `13d1aae32cfb502cef29ec51cc3784445e77b4ff51287620394ff14359c6834c`
  (today's);
- or a tree hash: `find . -type f | LC_ALL=C sort | xargs shasum -a 256 | shasum -a 256` gives
  `d2c2a89d3f6dc1d5c836ddd000bb67c16303b45e9c016057ca5057b0fef9d50d` today.

The author's 65,364 bytes against today's 66,269 means the **content** changed between the two
downloads, not only the zip. Record the date with the digest.

## F3 · High · MA-2: D-MA2c asks five questions and answers none, and misses two more (read + RUN)

`:219-227` is titled "all five answers required", and the table holds only questions. For an item that
"carries the risk now", an implementer still has to make every one of these decisions. Answer them here,
or list each as an OD. Recommended answers, with reasons:

| Question | Recommended answer |
|---|---|
| When | on `stopRecording` **and** in a shutdown hook, which covers the bundle's stop script (SIGTERM). Never on roll, if the text backend doesn't roll (next row) |
| Thread | the marker is appended **under the same lock as records**. Today `onRecord` is unsynchronised and reads `appender` racily (`ChronicleAuditCaptureService.java:254-259`, "raced with stop()"), so the text writer must not copy that shape |
| Restart | **a new file per start.** It avoids `more_than_declared` after a crash, and it removes the cumulative-export trap the skill warns about |
| Roll | **no rolling for the text backend** in the developer profile. One file per start is small, and D-E5 then never applies |
| Retention | the janitor applies with `retainHours`, deleting whole files only, never the open one |

**Two questions the table misses:**

1. **What the count counts: records received, or records written?** The stream-end spec's reason for
   having a count is to "detect records lost *in the middle* — a write that failed and was swallowed"
   (`spec-audit-stream-end.md:102-104`). That only works if the writer counts records **handed to it**,
   before the write. Chronicle's `onRecord` increments **after** a successful write
   (`ChronicleAuditCaptureService.java:260-263`), and a writer copying that shape declares exactly what
   it managed to write. The one loss the count exists to catch then reads `complete`.
   **Required:** the text writer (and a Chronicle marker, if OD-5 says yes) counts received records.
2. **Flush policy.** MA-2.3's kill points are defined by what is on disk, and the developer journey
   includes Follow on a live text file. A writer buffering 8 KB loses its tail on `kill -9`, which is
   honest (it reads `unknown`), but Follow shows nothing until the buffer fills. State the policy, for
   example a flush per record or every N ms.

**MA-2.3 kill points: one is wrong (RUN, published 1.19.0 jar).** I took a real 9-record file (bundle
`MarketProcessor`, the exporter's framing, a marker) and cut it at eight points inside and around the
marker:

| Cut | Result |
|---|---|
| after the last record's separator | `unknown`, 9 |
| **after the marker's `eventLogRecord:` line only** | **`unknown`, 10 records**: a phantom empty record |
| mid `streamEnd` value / after it / mid count / count without separator | `unterminated_marker`, 9 |
| `---` with no final newline | `complete`, 9 |
| full | `complete`, 9 |

"Mid-marker → `unterminated_marker`" (`:260`) is false for the earliest cut. That cut reads `unknown`
and shows **one extra record** with no finding. **Required:** correct MA-2.3, and decide whether the
phantom counts as an MA-0-class finding. It is a document with an `eventLogRecord:` key and nothing
else.

## F4 · Medium · Three MA-0 details came from my round 3, and two are wrong (read)

- **`:97-98` rationale is false.** "The status text is set at open." It isn't. Every Follow tick rewrites
  it with `producerWarning()` (`MainFrame.java:4325-4338`). The acceptance ("clears on every surface;
  assert both") is still right. Drop the rationale, or someone will "fix" code that isn't broken.
- **`:90-91` "and in the report".** `ReportRenderer` carries **no** producer findings today. Only the
  status bar, the tooltip and `context` do (`MainFrame.java:4003-4011`, `:6432-6435`). So MA-0.1 adds a
  new surface. It also makes MA-0.2 inconsistent: "`NO_NODE_LOGS` still fires alone" would be true in
  the report only if `NO_NODE_LOGS` went there too. Either drop "report", or put all producer findings
  there and say so.
- **`:94` merges two cases.** "Five records without node entries" was **C (marked) and D (unmarked)**. The
  marked one is where a marker makes the log look healthy. Name both.
- **Ordering inside `of()`.** `isWarning()` looks only at `findings.get(0)`. For a marked rolled set of
  empty members, today's findings are `[COMPLETENESS_NOTE]` (RUN, 1.19.0). With MA-0 they become
  `[COMPLETENESS_NOTE, EMPTY]`, and `isWarning()` returns **false**. It is unused in `src/main` today (the
  status bar uses `firstWarning()`), but a test asserting "raises a warning" with it will pass for the
  wrong reason or fail wrongly. Assert via `firstWarning()`, or fix `isWarning()`.

**Not weakened:** SOURCE_DAMAGE-first (`:99-100`) and the Follow-clearing case (`:96-98`) keep what I
proposed. All six MA-0 shapes are now verified silent on 1.19.0; the rolled set of empty members was
the one I hadn't run in round 3.

## F5 · Medium · MA-2.4's baseline is achievable, but not as "the same run" (RUN)

The author could not establish this, so I ran it. I fed the bundle's `MarketProcessor` 9 records through
a sink that tees each `LogRecord.asCharSequence()` into:
- a list (the text writer's input);
- a Chronicle queue written exactly as `onRecord` does, then read back exactly as `handleAuditExport`
  does.

| Check | Result |
|---|---|
| Chronicle round-trip of each record | **exact**, 9 of 9 |
| export framing (`YamlContainerWriter`: `\n---\n` between documents and after the last) against the same framing over the list | **byte-identical**, 3,427 bytes |
| do records end with a newline? | no, so `\n---\n` is the right framing and a text writer must match it |
| a second run of the same input | **not identical**: timestamps |

**So "the 1.0.44 Chronicle export of the same run" (`:263`) cannot be met as worded:**
- D-MA2b makes `backend` select **one** writer per run;
- two runs never match byte for byte.

**Required:** restate it as a **parity test**. One record sequence goes to both writers in one process,
and the text file must equal the export plus marker plus `---\n`.

**The framing lives in the wrong repository for the text writer.** `YamlContainerWriter` is a
package-private nested class of `WebAdminService` (`svc-admin-web`), and core cannot depend on a plugin.
Either:
- move the framing into core and have the exporter use it; or
- accept two copies, held together only by the parity test.

Say which.

## F6 · Medium · OD-5 is fair on consequence, but it hides two options (read)

"As specified, MA-2 changes nothing for any deployed export" (`:240`) is true, and saying so plainly is
right. But the framing is binary: a marker excerpt at stop, or not in this spec. Two more options belong
on the table:

- **(c) An export-time marker, from what the exporter read. Rule it out explicitly.** It is the cheapest
  option and would look like success. But a count the exporter makes of what it read always matches what
  it read, so every export would read `complete` whatever the capture lost. It is the "manufactured
  marker" the `run-mongoose-server` skill already forbids agents to write. Name it and reject it, because
  someone will propose it.
- **(d) Deployments run the text writer as well.** Once MA-5 makes fan-out a contract of the capture
  service, a deployment can have both sinks. The cost is a second write per record, but then OD-5 needs
  no Chronicle change at all.

**If (a) is chosen, two consequences belong in OD-5:**
- the count must be of records received (F3);
- `retainHours` pruning a run's early cycle files will make that run read `missing_records`. That is
  honest, but it will alarm people unless documented.

**The blocking scope is too wide.** The Ordering says MA-2 "needs AFMT-3 resolved **and OD-5 decided**"
(`:58`), and the status says OD-5 blocks MA-2 (`:9`). OD-5 decides only the Chronicle half. The text
half, which is the developer journey that gates MA-4, doesn't depend on it. Block the Chronicle half
alone, or state why the text half waits too.

## F7 · Medium · MA-1: the capability check holds; a trigger is missing, and "safe direction" is wrong for a refusal (RUN + read)

**D-MA1a verified by running** `getAuditorById("eventLogger")` against `fluxtion-runtime-1.0.16`:

| Processor | Result |
|---|---|
| the bundle's `MarketProcessor` (AOT, audit on) | resolves to an `EventLogManager` |
| `IntPipelineProcessor` (AOT, current `com.telamin` generator, **built without audit**) | `NoSuchFieldException` |
| `DefaultEventProcessor` | `NoSuchFieldException` |

In the owner's own tree, 130 of 158 generated processors declare no `eventLogger` field. They were built
without audit, so the no-auditor population is real and common.

**A fourth trigger is missing:** `POST /api/audit/{processor}/start` in `svc-admin-web`
(`WebAdminService.java:287`, handler `:770-786`). It catches `IllegalArgumentException` and returns
**404 "not found"**. If `start(name)` refuses by throwing `IllegalArgumentException` (the natural choice,
matching its unknown-processor case), this endpoint tells the user an **existing** processor was not
found. Anything else escapes as a 500. **Required:** add it as a trigger, and name the exception type and
the REST mapping.

**409 or 422: pick 409, and only one.** The request is well-formed; the processor's state conflicts
with it. 422 means the body is semantically invalid. "409 or 422" leaves the acceptance test to guess.
Use the same code for the level endpoint and the start endpoint.

**`autoStart` calls `start(name)` inside the processor-registration lambda** (`MongooseServer.java:770-773`).
A throw there fails registration. MA-1.1's "and the server still boots" will catch this, but D-MA1b
should say `autoStart` logs and continues rather than relying on `start()` not throwing.

**"The safe direction to be wrong in" (`:113`) was right for `AuditReadiness`, which warns. It is not
safe for a refusal.** A processor with a custom auditor that writes Format 1 correctly would lose
persistence entirely, and would be told to rebuild something that works. **Required:** an escape hatch,
such as a per-processor `auditCapture.allowWithoutEventLogManager: [names]`, or downgrading to a loud
warning when the processor carries any auditor Mongoose doesn't recognise. Otherwise the limit becomes
an outage for exactly the users sophisticated enough to have written an auditor.

**Minor:** MA-1.2 says the processor "never appears … in the admin file list" (`:126`). The file list
reads the directory (`DirAuditIntrospectionService`), so files from an earlier, audited build of the
same name will appear. Say "never listed as **recording**".

## F8 · Medium · MA-5's mechanism holds for every backend, with two conditions (read)

Passing Mongoose's listener into `attach`/`start` works for Chronicle and for a text writer alike,
because both are capture services that receive the same `LogRecord`. Two conditions make it hold:

1. **Capture the listener at `attach`, per processor; don't re-read it at stop.** `logRecordListener` is
   a **static** field (`MongooseServer.java:116`), overwritten by every `bootServer(config, listener)`
   (`:347`). With two servers in one JVM (tests), a restore that re-reads the static at stop restores the
   other server's listener. The existing code already shares the static at `:758`; MA-5 should not widen
   that. The listener is also the **embedder's** when one is passed to `bootServer`, so call it "the
   server's configured listener", not "Mongoose's own".
2. **For the text writer, isolation (MA-5.5) must also report.** An `IOException` on the processor thread
   (disk full) must not stop the console, but it must not be swallowed either. It has to be logged at
   WARN or counted, and, per F3, counted as **received**, so that the marker then reads
   `missing_records`. Isolation without reporting is MA-5b's silent-discard shape on the text backend.

The acceptances otherwise catch both original failures (MA-5.1, MA-5.2). MA-5.3 to MA-5.6 are the right
additions.

## F9 · Low · The rewrite against itself: the remaining contradictions (read)

| Where | What |
|---|---|
| `:274-276` against `:280-281` | "A developer running an example **does** see audit output" sits beside "until persistence replaces it". Persistence is **on** in the developer download, so after startup that developer sees none (the A2/A3 consoles showed 5 records, then nothing). Scope the sentence to core examples |
| `:189` against `:329` | "The corruption is in the runtime's record encoder" asserts a cause; `:329` says the cause is not established. My round-3 wording. What was run is narrower: **the corrupt text is what the sink receives**, so every listener gets it. Say that |
| `:320-323` | "Verified by running" includes `getBackend()` having no caller and `DataFlow` having no getter. Those were a grep and a `javap`, which is reading. Move them. Add D-MA1a (F7) and the MA-2.4 parity (F5) as run |
| `:25-26` | Problem 1's "sink says recording, level 200, file empty" was **measured** for the wrapper path only. For AOT-without-audit it is read. List it under "read, not run" |
| `:9`, `:58` | OD-5 blocks all of MA-2 (F6) |

---

## Required corrections, in order

1. **AFMT-3 gate** against D-MA0b and MA-2.5: choose one route, or split the check out of MA-0 (F1).
2. **Evidence pin:** a content digest, not a zip digest (F2).
3. **MA-2:**
   - answer D-MA2c, or list each question as an OD;
   - add count semantics (received) and flush policy;
   - correct MA-2.3's earliest mid-marker cut;
   - restate MA-2.4 as a one-process parity test;
   - decide where the framing lives (F3, F5).
4. **MA-0:** drop the false rationale; resolve "report"; name C and D; assert via `firstWarning()` (F4).
5. **OD-5:** add and reject (c); add (d); scope the block to the Chronicle half (F6).
6. **MA-1:**
   - add the REST start trigger and its status mapping;
   - pick 409;
   - `autoStart` logs and continues;
   - add an escape hatch for custom auditors (F7).
7. **MA-5:** capture the listener at `attach`; isolation must report (F8).
8. The F9 table.

## What I ran versus what I only read

**Ran:**
- two consecutive bundle downloads: digests differ, contents identical; content and tree digests
  computed;
- `getAuditorById("eventLogger")` on three processors, including a current-generator AOT processor built
  without audit (`IntPipelineProcessor`, from another of the owner's local projects), against
  `fluxtion-runtime-1.0.16`;
- the Chronicle parity experiment: bundle `MarketProcessor`, Chronicle Queue from the bundle's own
  resolved classpath (`chronicle-queue-5.27ea0`), `onRecord`'s write, `handleAuditExport`'s read,
  `YamlContainerWriter`'s framing;
- eight kill-point cuts through a real marked file, on the published 1.19.0 jar (`826daf64…`);
- rolled sets of empty members, marked and unmarked, on 1.19.0 (`RolledLogStore`).

**Read, not run:**
- `MainFrame` Follow refresh (`:4316-4338`, `:4369-4377`) and the `context` echo (`:6432-6435`);
- `ReportRenderer` (no producer findings);
- `ProducerDiagnostics.isWarning`;
- Mongoose `develop` `17a03b4`: `ChronicleAuditCaptureService.onRecord`, `MongooseServer` (`:116`,
  `:347`, `:758`, `:770-773`);
- mongoose-plugins `origin/main` `40f01cf`: `WebAdminService` (`:287`, `:770-786`, `:924-975`,
  `:1191-1209`);
- a count of `eventLogger` declarations across 158 generated processors in the owner's local checkouts.

**Not done:**
- no booted server, so MA-1's refusal paths and the `autoStart` throw are read;
- no text writer exists to test, so F3's thread and flush points are design review;
- AFMT-3's cause is still undiagnosed.

Experiment sources (`Parity.java`, `Cap.java`, `Wrap.java`, `RProbe.java`, the K0–K8 cuts) are in my
worktree under `.review-tmp/`, uncommitted.

---

## Addendum: a text writer booted in the real server (RUN)

Asked by the owner after the review was pushed: *why not boot a server with a text writer?* No text
writer exists (`backend` is unread), so I wrote the simplest one as a **review spike, not an
implementation**:
- `TextAuditWriter`, 72 lines, a `LogRecordListener`;
- a 34-line main that boots the bundle's **own** `server-config.yml` through
  `MongooseServer.bootServer(reader, writer)`, with `auditCapture.enabled: false`.

The writer:
- opens one file per start;
- writes each record followed by `\n---\n` (the exporter's framing, F5);
- counts records **received**, before the write;
- holds records and the marker under one lock;
- fans out to a delegate listener, isolated both ways;
- writes a marker in a shutdown hook.

Flushing is a switch. Sources are in `evidence/mongoose-audit-production-r4-text-writer-spike/`.
Every file was read with the published 1.19.0 jar.

| Run | Result |
|---|---|
| **Clean SIGTERM**, the bundle's 5 input lines plus 3 appended | **`complete`, 29 of 29**. Every record carries node entries; all 8 `PriceEvent`s carry `riskCheck` and `rootNode`. **MA-2.1 met end to end** |
| **SIGTERM under continuous load**, server stopped **then** marker written (2 reps) | `complete`, 31,586 of 31,586 both times; **0 records after the marker**; stop took 4 ms |
| **SIGTERM under continuous load**, marker written **then** server stopped (2 reps) | **`complete`**, 31,627 and 31,746, **but 39 and 20 records were produced after the marker and are not in the file** |
| `kill -9` under load, flushed and buffered | `unknown` both times. Honest |
| buffered (8 KB), 1 s after boot | **0 bytes on disk**, with ~24 boot records held in memory. Flushed per record: 8,632 bytes |
| two clean starts into one directory | two files, each `complete` (26 of 26); concatenated, `complete` (52, two segments) |
| SIGTERM with a 195k-line backlog (an earlier, discarded run) | stop took **over 30 s** (`feeds-agent failed to close due to timeout, retrying…`), with `dropping publish to slow/contended queue` warnings before it |

**What this settles in F3:**

1. **Thread: a lock is necessary but not sufficient; the order decides it.** With the marker written
   before the processors stopped, the file and its marker **agree**: it reads `complete`, with records
   missing. The analyser cannot detect this by construction, because the marker counted what it saw.
   Only the writer's own after-close counter showed the loss. **Required in D-MA2c:**
   - the marker is written only after the processors have stopped;
   - a record arriving after the marker is an **error, counted and logged**, never silently dropped.

   **Required in MA-2.3's "stop under load":** assert against a **producer-side** count (records the
   processor emitted), not the file, because the file is self-consistent in the failure case.
2. **When: the shutdown hook works, but it can be slow.** Stop was 4-6 ms in normal runs and over 30 s
   behind a large backlog. A supervisor that sends SIGKILL after a grace period (10 s is common) will
   cut that off. The file then reads `unknown`, which is honest, but D-MA2c should say the marker
   depends on a completed stop.

   Events Mongoose drops upstream (`dropping publish to slow/contended queue`) never reach a processor.
   No marker can see them. That is out of this spec's scope, but it is worth one sentence so nobody
   reads `complete` as "no event was dropped".
3. **Restart: a new file per start works.** Each run reads `complete` on its own, and appended runs read
   as segments.
4. **Flush policy: a user-configurable property, as the owner has decided.** The spec should:
   - **name the property and give its default**;
   - say what buffering costs, as the run shows.

   A buffered file lags the run. After boot it held **0 bytes** while records existed, so:
   - Follow shows nothing until the buffer fills;
   - once MA-0 ships, a buffered live file opened early raises the **empty-log** finding while records
     exist in memory;
   - `kill -9` loses the buffered tail (still `unknown`, so honest).

   Recommended:
   - default to a flush per record in the developer profile, which the bundle's config sets, since
     Follow is part of that journey;
   - buffering is available for deployments;
   - **MA-0's Follow wording** should not assert emptiness as a fact about the run when the writer may
     be buffering. For example: "no records **in the file yet**".

**Also visible from the spike:** for the developer profile, the simplest text backend is **the server's
configured listener** (`bootServer(config, listener)`), not a capture service. It needs no `backend`
switch and no `svc-admin-web` change. But it is then invisible to `audit.start`/`stop`, `liveSinks` and
the admin file list. That is a real alternative for OD-4's developer default. The spec should either
choose it or say why the capture-service route is worth the blast radius it lists.

**Still not done:** MA-1's refusal paths (no implementation to boot); AFMT-3's cause.
