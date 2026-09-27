# M44.5: results, scored against PREDICTIONS.md (2026-09-27)

Branch `feat/m44-5-derived-state-in-snapshot`. Predictions and design committed first (2388a327, a2473141); the
code followed in three stages (25c1b905 session layer; ed5d1cfa surfaces; 7af1e6e8 witnesses and the static check),
then the controls (e4046c0a) and a merge of `main`.

## What shipped
- **`logEvidence`** (new node) owns the open log's producer findings, time-order report, content signature, the last
  Follow read failure, and whether a scan is outstanding. It decides WHEN the evidence is stale: on a new
  generation, on `LogAppended`, and when a Follow poll's `LogContentObserved` signature moves. It refuses a result
  for another generation, and coalesces a request while a scan is outstanding (that scan reads the log when it runs).
- **One effect**, `ScanLogEvidenceEffect(opId, generation)`. The adapter answers `ScanScheduled`, runs the scan on
  the next EDT turn, and posts `ProducerFindingsObserved` and `TimeOrderObserved`.
- **`OpenLog`** carries provenance and its source (resolved before `LogOpened`), and Follow (`FollowToggled`;
  continued through a reload only into a log that can be followed, `LogOpened.followable`).
- **The snapshot** publishes all of it. **MainFrame** renders from it on the one listener: one composer for the
  status line, the tooltip, the Reports tab, the time-order dialog (a person's load only), the Follow toggles and
  the poll timer. `context`, the `report` reply and the PDF read it. The frame's copies, its refresh gate and its
  four status assemblies are deleted.
- **`OneDispatchModelTest`** (static) and **CLAUDE.md rule 9**.
- **The audit sink**: evidence re-derivations are re-scopes; per-poll content observations have a ring of their
  own; a batch end is kept with the kind of the cycle that raised it.

## Predictions
| # | Prediction | Result |
|---|---|---|
| 1 | Regeneration succeeds; generated source passes `GeneratedSourceIsPublishableTest` | **Held**, twice (stage 1, and again for `onLogAppended`). Both generated copies byte-identical. |
| 2 | `SessionGraphShapeTest` passes after one expectation is added | **Miss (harmless).** It passed unchanged — its existing rule (effects descend from a decision) already covered the new node. |
| 3 | Processor tests show the listed behaviours | **Held, with one implementation miss.** The design said `LogAppended` requests a scan; stage 1 did not implement it. A frame test found the consequence (below), and it was added then. `LogEvidenceTest` now has 12 tests. |
| 4 | W1 fails on `main`, passes here | **Held.** Run on `origin/main` 29a9ece4: `context.timeOrder` null and the line silent after the out-of-order append. |
| 5 | W2 fails on `main`, passes here | **Held.** On `main` the Follow line lost the provenance and the order warning. |
| 6 | Existing surface tests stay green, with only exact status text updated | **Miss.** More changed than text: the scan now reports on the next EDT turn, so frame tests that polled and asserted in one EDT turn were split in two, and their reads moved from the retired frame field to the snapshot. `StatusLineTest`'s refresh-predicate tests now test the signature's stream-end key. No assertion was weakened; three were strengthened. |
| 7 | Headless and display suites green; CI green with the mutation gate | Local: see *Gates*. CI: on the PR. |

## Risks named in advance
- **Status text.** The Follow line now reads `Following <provenance (file)> · N records · range …` from its first
  render; the start-only wording "— watching for new records…" is gone. A failed Follow read is appended to the
  log line (`⚠ Follow read failed: …`) instead of replacing it.
- **The say-hold (R12-2).** The 12-second hold timer is deleted. The behaviour is kept by construction — an idle
  tick changes no snapshot, and a render sets the line only when its text changed — and the re-anchored
  `follow-hold` control is caught.
- **Scan cost. Not measured.** Time-order validation moved from the loader's background job to the EDT scan: one
  linear pass over the index per content change, beside the producer findings pass the frame already ran on the
  EDT. No large-log measurement was made; per the standing rule, no number is claimed.

## Found while building (none predicted)
1. **A line that mixed two revisions.** Between a Follow poll and the scan it asks for, the line counted the new rows
   beside the previous revision's findings (`1 records … ⚠ empty log`). Two causes, both hand-written ordering:
   the poll's `LogAppended` published the count a cycle before the evidence was marked outstanding, and the
   poll's identity report published a snapshot after the store grew but before the session heard of the append.
   Fixed in the processor (`onLogAppended` marks the scan outstanding in the same cycle) and in the render (the
   session's count, and only when the store is at that revision). Witness:
   `theLineNeverCountsNewRowsBesideTheOldFindings`.
2. **Follow would have churned the audit record.** Every poll now reports its content, idle ones too; at one a second
   those records would have evicted the re-scopes in minutes, and a scan's batch end was filed as a transition.
   Witness: `SessionSnapshotTest#idlePollsEvictNothingButTheirOwnKind`.
3. **A witness too weak for its control.** `m44-5-line-waits-for-the-session` survived its first run: the witness
   checked the count, and the count had become the session's. It now asserts the whole previous line holds.

4. **CI found what the local run did not (PR #43, first run).** Two failures, one cause: the scan was queued at
   `LogOpened` and ran on the next EDT turn, so
   - a reader could see a finished load still saying "Loading b.yaml …" for one turn
     (`AsyncOpenInterleavingFrameTest#b3_…`, a race that passed locally), and
   - worse, the existing `global-preservation` control SURVIVED: its mutation makes `onLoaded` throw part-way,
     and the queued scan then rendered a complete-looking line over a load that had crashed.

   Fix: the adapter performs a requested scan only against the store of the generation it names
   (`scanWhenInstalled`). At load that is the END of `onLoaded`, in the same task, so a load that throws before
   then never gets a line. Under Follow the store is installed and nothing changed. `global-preservation` is caught
   again, and it is the deterministic check for this; b3 is the racy one.

## Controls
13 controls anchored on deleted code were re-anchored to the one site each behaviour now has, at their original
witnesses — except `integration-repeat-failure-skip`, which is observable only as a scan count now (equal findings
are kept by identity) and names `LogEvidenceTest#theContentSignatureDecidesARescan`. 12 new controls. All 25 run
through the gate (`--engine fast`, on a real display): named assertion red, byte-identical restore, green.

## The owner's question (prediction, for later scoring)
Not scorable yet — it is to be scored against later review records. One early data point, recorded so it is not
remembered selectively: every defect found in this work (items 1–3 above) was hand-written code around the
processor — adapter ordering, a render gate, a witness — and none was inside the generated dispatch. Prediction 2
said the remaining defects would move to the adapter boundary; item 1 is exactly that, and it was found at the
processor level in minutes because the ordering was readable in the generated code and the audit record.

## Gates
Recorded at the end of the work: see the PR description. After finding 4 the local gates were re-run: headless
2460/0 failures, all 23 frame suites 123/0 failures (1 skip, local keyboard focus), and 26 controls
(`global-preservation` plus this work's 25) caught.

## Found in review (PR #43, independent review of 66c16959)
Appended 2026-09-27; the entries above are not rewritten. Fixed in 69dd2b7e, then `main` (PR #33) was merged
(704b096a).

5. **F1 (required): a scan that never reports swallowed every later generation's scan.** On a new generation
   `logEvidence` cleared its evidence but not `scanPending`, so the new request coalesced into the old one. When the
   adapter legitimately dropped that old scan (its load threw after `LogOpened`), no scan was ever asked for again
   and the evidence stayed pending for every later open and append. Coalescing is sound only within a generation.
   - **Fix (in the node):** `scanPending = false` on a new generation, before `requestScan`. No regeneration: the
     handler set is unchanged.
   - `LogEvidenceTest#aDroppedScanDoesNotSwallowTheNextGenerationsScan` failed on the unfixed node (scans `[1]`)
     and passes fixed.
   - `LogFindingsOnEverySurfaceFrameTest#aLoadThatThrowsPartWayDoesNotStopTheNextLogsEvidence` makes a later load
     step fail after `LogOpened`. Under the mutation it reproduces the review's frame repro: pending true, the line
     stuck at "Loading …/good.yml …", `context.timeOrder` absent. Fixed, the next log reaches its composed line.
     This also confirms by test that gen 2's scan is performed at the end of gen 2's `onLoaded`.
   - **Scored against the owner's-question data point above, which this corrects:** F1 is a wrong rule INSIDE a
     node — my modelling error, not hand-written code around the processor. Prediction 2 named "node decisions, a
     wrong rule in a node" as one of the two places defects would move to, and said they would be cheaper to find.
     This one was found by an independent reviewer and reproduced headless in one test.
6. **The second trigger (maybeOfferProject's modal), checked by a throwaway real-frame probe, not committed.**
   - A person's load of a log inside an unopened project shows the modal inside `onLoaded`. A socket `open` issued
     meanwhile runs in the modal's nested event loop (`ActionExecutor.onEdt` → `invokeAndWait`), and its
     `LogOpened` DOES land there: the other log loads completely, as gen 2.
   - On this branch its evidence is not swallowed: pending false, the right line, `context.timeOrder` present.
   - **A separate defect, pre-existing on `main` (29a9ece4), was found.** When the modal returns, the rest of gen 1's
     `onLoaded` installs gen 1's table (3 rows) over gen 2's store (2 records). The table then shows one log while
     the session, the store, the line and `context` name the other.
   - On `main` the status line is ALSO wrong there: "3 records · … in.yaml · ⚠ time-order violations (1)" pairs the
     ordered log with the other log's violation.
   - Not fixed here — deciding what a superseded load's tail may still do is a load-path design call, beyond F1.
     Reported to the owner.
7. **F2: the rolled set's cross-file order report no longer passes through the frame.**
   - It is `LogStore.crossFileOrder()`, a final field that `RolledLogStore.open` sets on the load thread, and
     `TimeOrderValidator.validate(LogStore)` merges it. The unsynchronized `WeakHashMap` is gone.
   - No earlier test proved the merge reached a surface; `RollSetResolverTest` only proves the report is computed.
     New: `TimeOrderValidatorTest#aRolledSetsCrossFileOverlapIsPartOfItsTimeOrder` (headless) and
     `LogFindingsOnEverySurfaceFrameTest#aRolledSetsFileOverlapReachesContextAndTheLine`.
8. **F3: OneDispatchModelTest's gaps.** It now also catches:
   - evidence types in generic and array field types;
   - method references (`::of`, `::validate`);
   - `followTimer.stop` outside `renderFollow` / `finishExit`;
   - in the log's lifecycle methods, a status write that is not one of their named explanations;
   - a `"Following "` line assembled outside `statusLine`.

   The widened copy rule immediately flagged `ReportsPanel`'s `Supplier<ProducerDiagnostics>`. A supplier is a read
   on demand, not a copy, so it is exempt, with the reason in the test.
9. **Correction.** "Predictions" row 3 above says `LogEvidenceTest` "now has 12 tests". At 66c16959 it had 11. It has
   12 with the F1 test.

### Controls (review round)
Eight new, one per fix and rule: `m44-5-f1-new-generation-resets-outstanding`, `m44-5-f1-frame-next-log-is-scanned`,
`m44-5-f2-set-carries-its-cross-file-order`, `m44-5-f2-validation-merges-the-cross-file-part`,
`m44-5-f3-generic-copy`, `m44-5-f3-follow-line-in-poll`, `m44-5-f3-method-reference`,
`m44-5-f3-timer-stopped-outside-render`.

Run on the merged tree with `global-preservation`, `m44-5-append-is-outstanding-at-once` and
`m44-5-w1-follow-validates-time-order` (its anchor moved with F2). All 11 met the same standard:
- green;
- the named test fails with kind `failure`, and 0 errors and 0 skips;
- source and compiled classes restored byte-identical;
- green again.

### Gates (merged tree 704b096a, JDK 21.0.9)
| Gate | Result |
|---|---|
| `mvn -o clean test` | 2530 run, 0 failures, 0 errors, 130 skipped (display-only suites); 336 reports, no orphans |
| The CI frame-suite list, now 24 suites (PR #33 added one), on a real display | 130 run, 0 failures, 0 errors, 1 skipped — `PersonAtTheScreenFrameTest#escapeWithTheSearchHistoryPopupFocused…`, which this display cannot give keyboard focus; it runs under CI's Xvfb |
| Preflight | 24 suites, 227 anchors |
| `tools/test_mutation_shards.py` | 21 tests, OK |
