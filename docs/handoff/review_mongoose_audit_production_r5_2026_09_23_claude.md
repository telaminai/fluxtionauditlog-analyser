# Review, round 5 — `spec-mongoose-audit-production.md` at `20039c58` (reviewer: claude)

**Verdict: CHANGES REQUIRED.** The invariants section is the right idea, and MA-6, MA-7 and MA-8 are
the right items. But one composition was never run, and it defeats the developer journey: **OD-4's
chosen mechanism, run with the developer bundle's shipped configuration, produces a file that reads
`complete` and holds none of the run's business events** (F1).

MA-7's decided fix contradicts its own acceptance and misstates the separator it must defend against
(F2). The fix choice should not wait on whether a forged-`complete` payload exists (F3).

Two items the spec lists as "read, not run" are now **verified by running** (F8), and MA-5.4's silent
path is real.

Reviewed in my own worktree at `20039c58` on branch `review/mongoose-audit-production-r5`. Nothing in
the spec was edited, implemented, merged or released.

---

## F1 · High · OD-4's configured-listener writer gets only startup records when capture is on — and reads `complete` (RAN)

The developer download ships `auditCapture.enabled: true` with `autoStart: [marketProcessor]`. MA-5a
says capture **replaces** the configured listener. OD-4 (`:450-463`) makes the text writer **be** the
configured listener. Nobody ran the two together.

Run on the booted bundle server:
- configuration: the bundle's own server config with capture on (`evidence/…-r5-live-checks/live-config.diff.txt`);
- the text writer installed as the configured listener, exactly as OD-4 specifies;
- the 5 input lines, plus 3 more appended.

Results:
- the writer received **4 records**: 3 `EventLogControlEvent` and 1 `LifecycleEvent`;
- **0 of the 8 `PriceEvent`s**, and none of the appended events;
- the file reads **`{complete, recordsRead: 4, declaredRecords: 4}`**;
- Chronicle captured the business events.

That breaks the invariants at their core. `complete` is true of the file and false of the run:
- **V4** says `complete` means "every record this processor produced is here";
- **V3** requires that not knowing never reads as `complete`.

The spike's success (MA-2.1's parenthetical, `:403-404`: "Met end to end by the spike") ran with
capture **disabled**. That condition is not stated, and with the shipped configuration the claim is
false.

**Required, one of:**
1. The text writer **depends on MA-5**, so capture fans out to the configured listener instead of
   replacing it. Add it to the ordering (`:76`: "needs MA-6 and MA-7" becomes "needs MA-5, MA-6 and
   MA-7").
2. The developer bundle turns Chronicle capture **off** when the text writer is on, **and** Mongoose
   refuses or warns loudly when both are configured.

Either way:
- MA-2.1 names the configuration it was verified under;
- MA-4's virgin-LLM acceptance runs against the bundle's **actual** configuration.

## F2 · High · MA-7's decided fix misstates the separator and contradicts its own acceptance (read + code)

**The separator is not "a line that is exactly `---`".** Both readers split on any line that **trims**
to `---`, trimming space, tab, CR and LF:
- `RecordFramer.java:111-116`;
- `ByteRecordFramer.java:92-97`: "True if the line (with its EOL) trims to exactly `---`".

So `  ---`, `\t---` and `---\r` all separate. A writer implementing D-MA7 (`:283-284`) and MA-2.5
(`:417`) as written, checking for a line that is **exactly** `---`, lets all three through.

**Required:**
- state the writer's predicate as the reader's: *a line that, after removing space, tab and CR, is
  `---`*;
- since D-MA2b moves framing to core, have core carry that one predicate and cite §1 for it;
- MA-7.1's fixture includes the indented and CR variants.

**"Refuses or escapes" (`:284`, `:417`) cannot both satisfy V1.** MA-7.1 requires **the record count
and the verdict unchanged**. A writer that **refuses** a hostile record changes the count:
- if it counts the refused record as received (D-MA2c), the file reads `missing_records`, and the
  payload has changed the verdict;
- if it doesn't count it, the record silently vanishes under a `complete` marker. That breaks V4.

**Only escaping** (altering the line so it no longer trims to `---`, and keeping the record) meets V1.
Strike "refuses" from D-MA7 and MA-2.5, and say what the escape is. The escape changes the logged
value; say so, because that is the price of V1.

## F3 · High · Choose the fix without waiting on the open question (design)

The brief asks whether a payload exists that makes a log falsely read `complete`, and says finding one
would make the runtime fix mandatory. I did not try to construct one, and I don't think the decision
should wait on it. The facts already on the page settle it:
- **The shipped 1.0.44 exporter cannot be fixed by a writer** (`:286-289`, the author's own words).
- **The reader's recognition is line-based and indentation-insensitive.** `StreamEndMarker`'s own
  javadoc records a record being dropped by its own `toString`. So the reader cannot tell a real
  marker from one a payload produced.
- **Neither reviewer can prove no such payload exists.**

On V3, not knowing whether an attack exists must be treated like a finding, never as "complete". Treat
the question as unresolved and **decide as if the answer were yes**:
- make a producer-side escape (runtime, or a patched exporter release) **mandatory** for any path
  through the 1.0.44 exporter;
- keep the writer fix for MA-2's day one.

Record the question as open, with that decision taken in spite of it.

**V1 is also narrower than the harm.** V1 (`:94-95`) protects "the record count, the framing, or the
stream-end state". The parser is indentation-insensitive, so content lines have been read as fields
before. A payload that can reach **field values or node entries** would change coverage, which is the
"this node never ran" question in the other direction. Extend V1 to "field values and node entries",
and make MA-7.2 assert coverage unchanged, not only the count. *(Not tested; stated as a gap.)*

## F4 · Medium · MA-6's writer half has three readings, and two of them break an invariant (read)

"The writer refuses to count or mark a record that lacks `eventLogRecord:`" (`:248`, `:416`), with
MA-6.2 "no marker that counts the corrupt record" (`:255`). Its readings:

| Reading | Result |
|---|---|
| write it, don't count it, write the marker | `more_than_declared`: damage claimed |
| don't write it, don't count it, write the marker | `complete` with a record the processor produced missing: breaks V4 |
| write it, count it, **write no marker** | `unknown` plus MA-6's reader finding: honest |

**Required:** specify the third, and make MA-6.2 assert `unknown`.

## F5 · Medium · V2 as worded contradicts the reader's own contract (read + round-4 run)

V2 (`:97-99`): "Follow and a fresh open agree at every cut, including the bare-marker-header cut". At
that cut, round 4 measured the following on the published jar:
- a cold open reads **10 records** (the marker's lone `eventLogRecord:` line is a record);
- Follow holds an unterminated last document **pending** by design, since §1 makes a missing trailing
  separator legal on a cold open.

So the **counts** disagree by the reader's own contract, while the **state** (`unknown`) agrees.

V2 is also claimed for MA-0 (`:137-138`), but its named cut is tested only in MA-2.3, which doesn't
claim V2.

**Required:**
- word V2 as "the stream-end state and every finding agree at every cut; the record count may differ
  only by the one pending last document";
- assert it where the cut is (MA-2.3), with Follow driven through the real store.

## F6 · Medium · MA-8's acceptance is not implementable as written (read + round-4 run)

`CoverageService.assess` reads each record's `level()` and `nodeLogs()`, **skipping records outside the
current filter** (`CoverageService.java:52-56`). The control record is a record like any other: its
`eventToString` is `EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=riskCheck,
groupId=null}` (round 4). MA-8's three lines leave these unanswered:

1. **Filter scope.** A time or event-type filter that excludes the control record drops the
   qualification. Level changes are configuration state: consult them regardless of filter, up to the
   scope's end.
2. **Intervals.** A node set to WARN and later restored is qualified only between the two changes.
   Silence outside that window is plain uncovered.
3. **Denominator.** Does a qualified node stay in `uncovered` and the ratio, or leave them? It should
   stay counted, with a note. Excusing it would hide a node that never ran if the qualifying record is
   wrong (next point).
4. **What is parsed.** The runtime's `toString` format is not a contract.
   - Key on the record's `event` being `EventLogControlEvent`, and parse `sourceId`/`level` with a
     fixture pinned to the runtime version.
   - `groupId` also targets nodes; say whether it's in scope.
5. **Dependency on MA-7.** MA-7's injection splits records, so until every writer escapes, a
   control-looking record can be content. Another reason for point 3: annotate, never excuse.

## F7 · Medium · D-MA2e's switch is coherent; the text writer's configuration has no home (read)

**D-MA2e holds up**, stated precisely. Under OD-4, text is not a capture `backend` at all. So `backend`
accepts only `chronicle`, and `backend: text` is refused **with a message pointing at the text writer's
configuration**. Say that. As written ("whatever OD-4's mechanism", `:398`) it reads as if `text` might
be accepted.

**The gap is the other way round.** The bundle boots from YAML (`MongooseMain` calls
`bootServer(reader)`), and `bootServer(config, listener)` is programmatic. Nothing names:
- how the YAML turns the text writer on;
- where its directory goes;
- where the owner-decided **flush property** lives.

A user-configurable property with no configuration key isn't configurable. **Required:** name the
config block (for example `auditText: {enabled, directory, flush}`), and its interaction with
`auditCapture` (F1).

## F8 · Medium · Three "read, not run" items are now run: move them (RAN)

Booted the bundle server with capture on and a second processor: an AOT processor generated **without
audit** (`evidence/…-r5-live-checks/`).

**Problem 1 (`:25-28`):**
- the unaudited processor is listed with `recordCount: 0`, and holds only `metadata.cq4t`;
- `POST /api/audit/unaudited/start` answers **`{"recording": true}` HTTP 200**;
- `POST …/unaudited/audit/level` answers **HTTP 200**.

Every clause of problem 1 is now measured. Delete the "Read, not measured" note, and move the item to
"verified by running".

**MA-5.4 (`:227-229`).** I called `stopProcessor` on `marketProcessor`, re-added it, and appended 3
events:
- the new instance processed them (they reached the configured listener);
- the capture's `recordCount` stayed **23**, and the export holds **none** of them;
- `start` still answered **`"recording": true`**.

The silent-discard path is real. Delete "(Read, not run.)".

**A finding the re-registration exposed (Low).** `addEventProcessor` on a **running** server does not
call `init()`: the first re-add threw `init() must be called before start()`, and the processor never
ran. The configuration path calls `init()` (`ServerConfigurator.java:128`). MA-5.4's acceptance must
name its re-add path. Whether that is itself a Mongoose defect is for the owner to decide.

**MA-1's baseline.** The current behaviour of three of the four triggers is now measured: `autoStart` is
silent, REST start claims recording, and the level endpoint returns 200. The fourth, the `audit.start`
admin command, was not run. The refusals are still unbuilt.

## F9 · Low · Where the invariants are claimed, against the acceptances (read)

| Claim | Holds? |
|---|---|
| V1 for MA-7 | not as written: F2 (refusal breaks it) and F3 (fields and node entries not covered) |
| V2 for MA-0 | the Follow-clearing case yes; the named cut is not in MA-0's acceptance (F5) |
| V4 for MA-0 | yes: D-MA0d's "in the file yet" wording |
| V4 for MA-2 | **no**: F1 is a V4 violation in MA-2's own configuration |
| V3 for OD-5 | yes: (c) rejected correctly. Under (a), counting received records is consistent with V3 |

- **MA-4's acceptance** (`:470-471`) doesn't carry the "one run's file" scope V4 assigns it. Add it
  there, not only in V4.

## F10 · Low · Body against itself (read)

- `:27-28` and `:229`: "read, not run" notes now contradicted by F8.
- `:403-404`: MA-2.1's "met end to end by the spike" without its capture-off condition (F1).
- `:284` against `:286`: "Decided: the writer" decides the location but not refuse-versus-escape (F2).
- `:505-509`: the read-not-run list, per F8.
- `:11` "MA-2's framing home" is decided, but the predicate that home must carry is the reader's (F2).

---

## Required corrections, in order

1. **F1:**
   - the text writer depends on MA-5, or the bundle turns capture off and Mongoose refuses the
     combination;
   - MA-2.1 names its configuration;
   - MA-4 runs the shipped configuration.
2. **F2:**
   - the writer's separator predicate is the reader's trimmed one;
   - strike "refuses";
   - specify the escape;
   - add the indented and CR fixtures.
3. **F3:**
   - decide the producer-side escape for 1.0.44 paths as mandatory now, with the open question
     recorded as open;
   - extend V1 to field values and node entries.
4. **F4:** MA-6's writer half writes, counts, and withholds the marker.
5. **F5:** reword V2 around state and findings; test it at MA-2.3's cut through the real store.
6. **F6:** MA-8 answers filter scope, intervals, the denominator, the parse contract and `groupId`;
   annotate, never excuse.
7. **F7:** name the text writer's config block, including flush; `backend: text` points there.
8. **F8–F10:** move the newly run items; fix the leftovers.

## What I ran versus what I only read

**Ran, on the booted bundle server (`mongoose-1.0.29`, `fluxtion-runtime-1.0.16`), files read with the
published analyser 1.19.0:**
- the OD-4 writer with capture on (F1);
- an unaudited AOT processor with capture on: listing, REST start, level endpoint (F8);
- MA-5.4 re-registration, run twice: once exposing the missing `init()`, once with an initialised
  processor (F8).

A first attempt at the second run was discarded, because the previous server still held the port.

**Read:**
- `RecordFramer`/`ByteRecordFramer` separator predicates;
- `StreamEndMarker` recognition;
- `CoverageService.assess`;
- `WebAdminService` handlers, `MongooseServer` and `ServerConfigurator` (as cited);
- the spec against itself.

**Not done:**
- I did not attempt to construct a payload that makes a log read `complete` falsely (F3 explains why
  the decision should not depend on it);
- I did not test whether a payload can reach field values or node entries;
- no MA-1 refusal paths (unbuilt);
- the `audit.start` admin command.
