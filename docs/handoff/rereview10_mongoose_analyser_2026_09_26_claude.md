# Targeted tenth re-review — ninth-round fixes

**Verdict: R9-1, R9-2, R9-3 and O9-1 are closed. No required correction remains in this review's scope.**

Reviewed `8a35a988..d8bb6e3c` on `feat/mongoose-audit-production-rebased`, in an isolated worktree.
This is a targeted acceptance of those four fixes, not a fresh audit of the branch or its earlier rounds.
The previous review is `752015b7`. I used its named counterexamples again; agreement with the author's
report was not the check. No implementation, tracker or preserved evidence was changed.

`RUN` means reproduced here; `READ` means inspected code or documents; `REPORT` identifies an author's
historical observation that I did not reproduce. Java paths below are relative to
`src/{main,test}/java/telamin/fluxtion/audit/analyser/analyser/`.

## Dispositions

1. **R9-1 — closed (RUN, READ).** `topology/PerNodeLevelChanges.java:380,415` counts marker
   occurrences rather than distinct boundary positions. My packaged-jar probe confirms boundaries
   `[1, 1]` and `[1, 1, 1]`: the following record receives no annotation. The empty intervening run
   also receives none after its second marker; the one-marker positive control still annotates.
   `ControlAddressAndScopeTest.java:530`, `adjacentMarkersAreCountedByOccurrence`, asserts exactly these
   refusals and positive controls. Restoring the distinct-position lookup fails its `R9-1 2 adjacent
   markers` assertion. The matrix stays green, as reported: adjacent markers are covered by the dedicated test.

2. **R9-2 — closed (RUN, READ).** `PerNodeLevelChanges.java:209–234` accepts other header fields
   until either payload field. My null-record probe gives byte-identical annotation text for the standard
   header and `eventTime` before `logTime`, bounded before record 3. The node-value spoof is not a closer.
   `ControlAddressAndScopeTest.java:554`, `theRawHeaderAcceptsEveryPermittedFieldAndStopsAtPayload`, also
   exercises `thread`, an unknown field, and `event:` after `eventToString`; all run green.
   Making `eventTime` a payload boundary fails the named `R9-2 eventTime` assertion, not compilation or
   an unrelated assertion. The matrix remains green because it has no null-record rows.

3. **R9-3 — closed (RUN, READ).** `PerNodeLevelChanges.java:529` now says “Every record in view
   that this annotation concerns is in a LATER run”. My mixed view containing records 1 and 2,
   and the three-separated-marker case, both give that qualified lead and retain the endpoint before
   the marker preceding record 3. `ControlAddressAndScopeTest.java:592`,
   `theLaterRunLeadSpeaksOnlyForTheRecordsItConcerns`, additionally covers a view crossing the second
   marker and mixed groupings. Restoring the universal lead fails the named `R9-3 [0, 1]` assertion.
   The matrix fails through **reach**, accurately recorded in the report; the dedicated assertion is
   the direct guard for this wording defect.

4. **O9-1 — closed (RUN, READ).** `ControlAddressAndScopeTest.java:776–831` derives expected
   endpoints from the constructed layout, its leading-record shift, and the closing control's fixture
   parameters. It extracts the actual endpoint from the note and compares it with that independent
   expectation. The older `closedClause` check is still present, but does not supply the new expected
   endpoint. Repeating the review's `m2 + 1` → `m2 + 2` mutation now fails the matrix through
   **offenders**. This is an endpoint check, not merely a branch-reach failure.

## Required corrections, optional improvements and owner decisions

- **Required:** none in the requested delta.
- **Optional:** none added in this targeted review.
- **Owner decisions:** unchanged. The second marker is counted by occurrence. The one-stream premise
  remains documented rather than inserted into each note. Neither was reopened.

## Checks actually run

All Java commands used JDK 21. Counts are **total / failures / errors / skipped**.

| Check | Own result |
|---|---|
| `git rev-list --count HEAD..origin/main`, before review additions | `0` |
| `mvn -q clean package` | **2110 / 0 / 0 / 98**, 278 Surefire XML reports mapped to `src/test/java`; no orphans; worktree clean after package |
| `R10Targeted` against the packaged branch jar | **13 assertions passed**; constructed inputs, not a client replay |
| `python3 docs/handoff/evidence/mongoose-audit-production-impl/rereview9-fixes/witness14.py` | Four controls: green baseline → named XML `<failure>` → byte-identical source restore → green |
| `python3 docs/handoff/evidence/mongoose-audit-production-impl/rereview8-fixes/witness13.py` | Four controls: same protocol; matrix **offenders** for all four |
| Focused suites used by both scripts | `CoveragePerNodeLevelTest,ControlAddressAndScopeTest,PerNodeLevelChangesTest`; **55 / 0 / 0 / 0** on the final restored run |
| `mkdocs build --strict` | Pass |
| `git diff --check 8a35a988 HEAD` | Pass |
| `git diff --stat 8a35a988 HEAD` | No UI class; display suite not run |
| CLAUDE rule-one sweep, tracked files and review additions | Clean |

Each script deletes the reports before each baseline, mutation and restored run. Each named red was a
`<failure>`, not an `<error>`. All eight controls reported SHA-256 restoration, clean `git status -- src`,
and a green rerun. I inspected the result records, not just the scripts' exit codes.

| Control | Named failure | Matrix assertion |
|---|---|---|
| R9-1 | `adjacentMarkersAreCountedByOccurrence`, `R9-1 2 adjacent markers` | Green |
| R9-2 | `theRawHeaderAcceptsEveryPermittedFieldAndStopsAtPayload`, `R9-2 eventTime` | Green |
| R9-3 | `theLaterRunLeadSpeaksOnlyForTheRecordsItConcerns`, `R9-3 [0, 1]` | Reach |
| O9-1 | `noBranchOfTheSentencePresumesAProcessor` | Offenders |
| R8-1 | `theLaterRunIsBoundedAndTheAnnotationStopsAtTheSecondMarker`, `R8-1 M` | Offenders |
| R8-2 | `theLaterRunIsBoundedAndTheAnnotationStopsAtTheSecondMarker`, `R8-2 P` | Offenders |
| P1 | `noBranchOfTheSentencePresumesAProcessor` | Offenders |
| P2 | `noBranchOfTheSentencePresumesAProcessor` | Offenders |

The [probe and outputs](evidence/mongoose-audit-production-impl/rereview10-probe/) preserve this run.
`R8Review.java` contains the old fixture helpers with its broad `main` removed; only `R10Targeted` was
executed. Compile both against the packaged jar, then run `R10Targeted` with that jar and the compiled
probe directory on the classpath. The complete old probe was deliberately not rerun.

## Ninth-round report accuracy

**READ and RUN:** the four-row table at `report_mongoose_audit_production_phase1_2026_09_23.md:649`
matches the fixes and my targeted runs. Its matrix dispositions reproduce exactly. The implemented /
verified / still-open split at line 660 is appropriate: these tests establish the supported null-record
reader path, not that a deployed plugin actually returns null records. The matrix's stated omissions
remain covered here by the dedicated tests, not by a claim of exhaustiveness.

**READ:** the “corrected in round 9” marks on the round-8 marker decision and R8-4 row accurately
describe the old code and this delta. The changed CHANGELOG lines match the named behaviours observed
here: adjacent occurrences count separately, the later-run lead is qualified, and permitted header
fields do not themselves terminate the scan before payload. This is not a new audit of every possible header.

**READ / REPORT:** `7b9defde` contains the P14 predictions and precedes fix commit `66772fab`.
P14.1 remains correctly **unverified**: the author says the four wording references were changed before
running the tests, so the predicted initial failures were never observed. I did not reconstruct that
uncommitted intermediate state or the reported temporary matrix NPE, and do not count either as my evidence.

## Limits

No full mutation gate, other earlier witnesses, display session, client scenario, key use, merge or
release. No renewed audit of the remainder of `sentence()`, `closing()` or `unreadableClosing()`.
The deployed-plugin premise and producer grouping premise remain as recorded, not independently
established by this review. No additional out-of-scope finding was investigated.
