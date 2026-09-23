# Review — Mongoose audit production, phase 1, round 4 (final) (reviewer: claude)

**Heads:**
- mongoose-plugins `b208257`;
- core `2c4192e`;
- analyser `e8a1cfe7` (follow-ups in `5d4bf116`).

Report: `report_mongoose_audit_production_phase1_2026_09_23.md`. Spec: `spec-mongoose-audit-production.md`
at `fda01845`.

**Verdict:**
- **plugins and core: MERGE.**
- **analyser: CHANGES REQUIRED, one small fix, then MERGE as a partial.**

You asked whether a sixth copy of the BOM rule is latent. **It is, and it changes a verdict**: `RecordParser`
(F1). By your own test, that changes the recommendation. The fix is small, and it is the argument for
consolidating the rule now rather than later.

Nothing was fixed, merged or released. The review ran in disposable worktrees.

---

## F1 · Medium · The sixth BOM site: `RecordParser` drops record 1's header behind a BOM (RAN + read)

`parse/RecordParser.java:69` does `line.strip()` and `:74` tests `t.charAt(0) == '#'`. `String.strip()`
keeps U+FEFF, so a BOM'd first line `﻿#<time> [<thread>] <LEVEL> <logger>` is not seen as a header. On a
BOM'd file whose records open with the documented header comment, record 1 loses its thread, level,
logger and headerTime.

The same happens on **every mid-file BOM record** in a concatenated file. `cat run1.yaml run2.yaml` of two
BOM'd files is the shape a new-file-per-start writer produces when someone joins its outputs.

`parse/RecordParser.java:93` (`t.equals("eventLogRecord:")`) and `:173` (`isIdentifier`) have the same gap:
a record that is only a BOM'd opener becomes a PARSE_ERROR (read). `HeaderParser.java:22-23` repeats the
`strip()`/`startsWith("#")` pair.

**It changes a verdict (RAN).**
- Two records, with DEBUG then INFO headers:
  - without a BOM, `auditLevelFinest` = DEBUG, and the note says nothing finer than DEBUG appears;
  - with a BOM, `auditLevelFinest` = INFO, and the note says debug calls may be missing.
- `CoverageService.java:56` feeds `record.level()` into `AuditLevel`.
- The thread text filter (`FilterState.java:119`) and the aggregate thread grouping
  (`AggregateService.java:132`) also see null for that record.
- `ByteOrderMarkFramingTest` uses no header comments, so nothing covers this.

**Every load path agrees**: heap, mapped, rolled at thresholds 0 and 1000, registry, SPI, follow. This is
not a V2 disagreement. It is a gap between a BOM'd file and the same file without one.

**Required:**
- one BOM-aware strip, used by `RecordParser` (header, key, scalar lines) and `HeaderParser`;
- a test with header comments through `HeapLogStore` and `MappedLogStore`, covering a BOM at the file head
  and a BOM mid-file;
- a witness for each.

**Answer to your structural question.** Five copies was already six. **Consolidate before merging**: one
`BomAware.strip`, or `StreamEndMarker.strip` promoted to a shared utility, used by every site in the
inventory below. Then have a test enumerate the sites, so that a seventh caller either uses it or fails.
For a partial merge that is still the right call: it is a mechanical change, and the defect class has now
recurred six times.

**Inventory of every head-of-file, record or line decision (READ, with the agent's probe matrix RAN):**

| Site | file:line | BOM-safe |
|---|---|---|
| String framer, separator and blank | `RecordFramer.java:121,129` | yes (any line head) |
| Byte framer, separator and blank | `ByteRecordFramer.java:99,107,112` | yes, equivalent |
| Marker recognition | `StreamEndMarker.java:59-60,108` | yes |
| MA-6 record key | `ProducerDiagnostics.java:221` | yes (shared strip) |
| Unseparated check | `ProducerDiagnostics.java:~268` | yes (`indexOf`) |
| YAML sniff | `YamlAuditReader.java:55,58` | yes, but looser: strips BOMs anywhere; affects only extension fallback |
| Binary sniff, roll-set refusal | `BinaryAuditReader.java:75-90`, `RollSetResolver.java:47-56` | n/a (magic bytes) |
| Roll ordering | `RollSetResolver.java:206-212` | safe in practice |
| **Record header, key, scalars** | **`RecordParser.java:69,74,93,173`** | **no (F1)** |
| **Header parser** | **`HeaderParser.java:22-23`** | **no (F1)** |
| External CSV header | `ExternalCsvLoader.java:271-279` | no; fails loudly, not silently (Info) |

**Your specific doubt about the byte framer (RAN): it holds.**
- EF BB BF is checked at the head of *any* line, not only the file head, which matches the string framer.
- The framer assembles each line across buffer reads, so a buffer boundary cannot split a line.
- A mid-file BOM placed at bytes 65533, 65534, 65535, 65536 and 131071 (buffer 1<<16): heap and mapped
  agree in every case.

## F2 · Low · Follow throws on a half-written UTF-8 character (RAN; not BOM-specific)

`HeapLogStore.appendFrom` (`:158`, `Files.readString`) throws `MalformedInputException` when a poll lands
mid-way through a multi-byte character. A BOM split across writes (EF, then BB BF) reproduces it.
`MainFrame.pollFollow` shows "Follow read failed" for that tick and recovers on the next, and the final
state matches a fresh read.

**Required (small):** decode only up to the last complete character, or treat a malformed tail as pending.
This is the pending-tail rule applied at byte level.

## F3 · Low · A double leading BOM raises a false `NO_RECORD_KEY` (RAN)

`cat bomonly.yaml run.yaml` gives `﻿﻿eventLogRecord:`. One BOM is stripped, and the second fails the
framing check. The result is consistent on every path, and completeness stays COMPLETE. Folding it into
the consolidated strip (strip all leading U+FEFF) closes it.

## F4 · Low · Two stale statements in code and report that this branch authored (read)

1. **`PerNodeLevelChanges.all()` javadoc** (`:211`): "for the `context` echo and the report". `all()` has no
   production caller; the only user of the class is `CoverageService` via `of()`. This is the fourth
   comment-promises-what-the-code-doesn't in this file family, and the first authored on this branch. Fix
   the comment, or wire `all()` with D-MA0c.
2. **Control-event matching is inconsistent again.**
   - Round 2 made MA-8 match "consistently with `onlyControlEvents`".
   - Round 3 changed MA-8 to an exact simple-name match (`PerNodeLevelChanges.java:111`).
   - `ProducerDiagnostics.onlyControlEvents` still uses `contains` (`:307`).
   - So `FakeEventLogControlEventX` counts as a control event for `ONLY_CONTROL_EVENTS` but not for MA-8.
   - Use one predicate; `PerNodeLevelChanges.isControlEvent` is the right one.

   The report still says the round-2 thing (line 154-155: "event-name matched with `contains`, consistently
   with `onlyControlEvents`"), contradicting its own round-3 text (176-179). That is add-without-remove,
   in the handoff.

## F5 · Low · Wording overclaim for two untimed changes (RAN)

An untimed WARN followed by an untimed INFO reads "WARN for this whole log (both changes are untimed)",
although record order shows a restore part-way through. Say what is known: "set to WARN, then restored;
neither change carries a time, so the window cannot be placed".

---

## Your question 3 · Is the analyser mergeable as a partial?

**Yes, after F1.** None of the merged behaviour depends on an open clause to be *correct*. Each open clause
leaves a surface exactly as it was before this branch; none leaves it wrong.

- **MA-0.2, three of four surfaces.** A finding on the status bar but not in the report is **not worse than
  no finding.** The report says nothing about an empty log, which is what it said before this branch. The
  app now says something true.
  - The risk is **inconsistency between surfaces**: someone reading only the report sees no warning.
  - That is a V4 scope issue to state in the release notes ("producer findings are not yet in the report"),
    not a merge blocker.
- **MA-8 annotation not in the report.** Same reasoning: the report lists the node as uncovered, exactly as
  before, and the interactive answer is now better.
- **MA-0.5 (Follow).** The empty-log wording is already correct for a live file (`ProducerDiagnostics.java:
  170-172`: "No records in this file … says the file is empty, not that the run produced nothing"), which
  meets D-MA0d and V4. Diagnostics recompute on append and the status text is refreshed per tick. Only the
  *test* is missing; the behaviour is not wrong.
- **MA-0.7/MA-6.3 fixtures and MA-5.7:** completeness, not correctness.

## Your question 4 · What the three merges do to each other

**No build or binary ordering exists between them (READ).**
- **Plugins:** `svc-admin-web` implements neither `MongooseAuditCaptureService` nor
  `MongooseAuditIntrospectionService`, and calls none of `attach`, `invalidate` or `liveSinks` (grep over
  `src/main/java`). The plugins build against mongoose **1.0.29**, so plugins `b208257` merges and releases
  independently.
- **Analyser:** it depends on neither.
- **Core `2c4192e` changes:**
  - `MongooseAuditCaptureService` gains a **default** `attach(dataFlow, name, configuredListener)` that
    delegates to the two-argument form. That is binary- and source-compatible.
  - `DirAuditIntrospectionService` (internal) now overlays live handles, and invalidates on sink mutation.

**Two delivery facts to state, because nobody has:**
1. **Merging core is not delivering it.** The developer bundle pins `mongoose 1.0.29`, and MA-5's fan-out,
   5.4 and the listing fix reach users only when a core release is cut **and** the bundle pin is bumped.
   Round 5's F1 composition (the text writer plus capture on) depends on exactly that.
2. **The default `attach` overload silently drops fan-out for any external capture-service implementation** that
   doesn't override it. Only `ChronicleAuditCaptureService` fans out. That is MA-5.7's open clause. Say it
   in the javadoc of the default method, so an implementor sees it.

**The exported bytes change (plugins).** An escaped line gains a leading `\`. The analyser's static
conformance fixtures (c19, c21) are unaffected. Records still open with `eventLogRecord:`, so neither MA-6
nor `UNSEPARATED` misfires on escaped exports (read). #39's own acceptance ran against the published 1.19.0
jar.

## Your question 5 · The "what I got wrong" record

**The report is more accurate than the brief.** The brief says three defects were "missed by my tests and
caught by the existing suites": MA-0 on a null index, nullable `logTime`, and the BOM regression.
- The report attributes only the **first two** to the suites (lines 160-165, 215-221). That is correct.
- **The BOM regression was caught by review** (round 2's N1), not by a suite. The report lists it neutrally
  as "mine from round 2" (line 171).
- So the brief's framing, not the report, overclaims the suites.

**Unrecorded:**
1. **F1 above.** A sixth BOM site on a branch that fixed the other five.
2. **The report's own stale round-2 text** (F4.2).
3. The `PerNodeLevelChanges.all()` comment (F4.1): your own instance of the "comment promises" family you
   named in round 3.

None of these was caught by a suite, so the report's two-item suite list stays accurate. **Its "what I got
wrong" section should add F1 and F4.**

---

## Held up (RAN unless marked)

- Full analyser suite **1920/0/62**, matching the claim.
- Round-3 follow-ups:
  - untimed windows: no path prints or compares a raw `MIN_VALUE` in any branch;
  - the multi-group test: reinstating the `break` fails `aLaterGroupsWindowIsFoundWhenTheFirstDoesNotApply:380`;
  - dropping `!untimedClose` fails 2 tests;
  - each of the four framer BOM skips removed fails its test.
- BOM probe matrix over heap, mapped, rolled (0/1000), registry (0/1000), SPI and follow, for:
  - BOM + records + marker;
  - BOM + `---`;
  - BOM-only, BOM + LF, BOM + CRLF;
  - BOM + marker of 0;
  - two concatenated BOM'd runs (with and without a missing separator);
  - a split BOM in Follow;
  - a mid-file BOM at five buffer boundaries.

  **No load path disagrees with another on count, state or findings.** Rolled reads `unknown` where the
  others read `complete`; that is D-E5, and it does so equally on the no-BOM controls.
- Plugins and core: unchanged since round 3, and verified then, including live: #39 witnesses; MA-5.4
  export 23 → 46; listing 23 → 24 → 46 matching the export.

## Required corrections

1. **F1:** one BOM-aware strip used by `RecordParser` and `HeaderParser`, and consolidate the other five
   onto it. Header-comment tests through heap and mapped, at the file head and mid-file, each with a
   witness. Then merge the analyser.
2. F2 and F3 in the same pass if cheap: a malformed tail is pending; strip every leading U+FEFF.
3. F4: fix the `all()` javadoc; use one control-event predicate; replace report lines 154-155; add F1 and F4
   to "what I got wrong".
4. Release notes: producer findings and MA-8 notes are not yet in the report (MA-0.2, D-MA0c); merging core
   is not delivering it until the bundle pin moves.

## What I ran versus what I only read

**Ran:**
- the BOM probe matrix across seven load paths and the Follow path;
- the verdict-changing header case;
- the buffer-boundary cases;
- the round-3 follow-up mutations, restored by sha;
- the full analyser suite.

The probes are in the reviewer's local scratch directory, not committed.

**Read:**
- `RecordParser`, `HeaderParser`, the inventory sites;
- the core diff for `MongooseAuditCaptureService`/`DirAuditIntrospectionService`;
- the plugins' coupling (grep over `src/main/java`);
- `ProducerDiagnostics` wording;
- the report's ledger.

**Not done:**
- `MainFrame` itself (Swing), so the app-level claims about MA-0 remain registry-level, as the report
  already says;
- no boot this round: core and plugins are unchanged since the live round-3 checks.
