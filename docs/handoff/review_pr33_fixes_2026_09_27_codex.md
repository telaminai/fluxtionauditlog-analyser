# PR 33: independent fix review and authorised corrections

**Verdict at the reviewed head `0eda3575`: not mergeable.** Four concrete problems remained. The owner subsequently asked this session to fix them on the PR branch, with tests, for the original author to review. Those corrections are implemented below. This is a handoff for that review, not an independent approval of my own fixes. Nothing was merged or released.

The initial review was independent of the session that wrote the previous review and fixes. Scope was the merge and the D-2/D-4 corrections; D-1 and D-3 were not re-audited. RAN means executed here on macOS with JDK 21; READ means source/history inspection. Author-reported earlier trials are not counted as mine.

## Required corrections — implemented

1. **Nested links still escaped the exchange directory (RAN).** At `0eda3575`, `config/ExchangeDir.java:91–104` checked the configured directory, but `llm/ExportGuard.java:88–102` checked an output only lexically. A real `project/exchange` containing `nested -> outside`, followed by `screenshot {path:"nested/out.png"}`, returned success and wrote the PNG outside the project. The read guard admitted the same route. This needs no concurrent attacker: the link can already be present in a checkout.

   **Fix:** `ExportGuard.containedTarget` checks the actual target, resolving its existing ancestors even when output subdirectories do not yet exist. Both reads and writes use it. It returns the canonical location; `ExchangeDir` likewise returns its checked location, so swapping an already-traversed exchange alias does not redirect the returned path. The explicit chooser-file grant is unchanged. Tests: `ExportGuardTest#aNestedLinkCannotWidenReadsOrWrites`, `#aFileLinkCannotWidenReads`, `#anInternalLinkAllowsNewDescendantsAndReturnsTheCheckedLocation`, `ProjectSuppliedExchangeDirTest#anAcceptedAliasReturnsItsCheckedLocation`, and the real screenshot path in `ReportRecoverableDeleteFrameTest#aProjectExchangeCannotExportThroughANestedOutsideLink`. Controls: `p33-nested-link-containment`, `p33-canonical-exchange-location`, `p33-canonical-export-target`.

2. **The report name `true` could not be restored by the verb (RAN).** `MainFrame.java:1331` treated both boolean true and the string `"true"` as a listing request. Creating and deleting a report named `true`, then following the delete reply's `report {restore:"true"}` instruction, returned `ok:true` with a list and left the report deleted. `VerbSchemas.java:168` compounded the ambiguity by publishing only a string parameter while the documented listing form was boolean.

   **Fix:** boolean true lists; every string names a report. The schema admits strings and boolean true. Tests: `ReportRecoverableDeleteFrameTest#aReportNamedTrueIsRestoredRatherThanListed` and `VerbSchemasTest#reportRestorePublishesBothNamesAndTheBooleanListRequest`. Controls: `p33-restore-string-true`, `p33-restore-schema`.

3. **A mixed restore request silently performed only part of the request (RAN).** `MainFrame.java:1328–1345` returned from restore before considering sections or export. `report {restore:"combined", sections:[...]}` restored the old definition and returned success without applying the requested sections or disclosing that omission. The reviewed `ActionExecutor.java:224–239` also permitted a path alongside restore.

   **Fix:** `ActionExecutor` refuses a restore combined with any other parameter before path processing or any mutation. False, blank and non-string/non-true values are refused too. The docs describe the exclusivity. `ReportRecoverableDeleteFrameTest#restoreRefusesMixedOperationsWithoutChangingAnything` checks sections, path, name, delete, rename and an unknown key, including unchanged live reports and bin. Control: `p33-restore-mixed-operations`.

4. **Delete and Restore were clipped at the default window size (RAN).** `ReportsPanel.java:138–159` used FlowLayout in BorderLayout.NORTH. At 1200×800 the sidebar's toolbar had room for one row, but Delete and Restore wrapped below that row. They were attached and `isShowing()` was true; their visible rectangles were not usable. The first native mouse attempt did not open Delete. Programmatic `doClick()` could invoke the hidden widget, which exposed why an action-only test would miss the defect.

   **Fix:** the toolbar gives each action a row. `ReportRecoverableDeleteFrameTest#theVisibleButtonsDeleteRestoreAndExplainAnEmptyBin` requires all four buttons' full visible rectangles at 1200×800, then clicks the actual Swing buttons, answers the real modal dialogs, and asserts the restored report and the visible empty-bin message. It restores the look and feel in `finally`. The FlowLayout mutation fails the named visibility assertion. Control: `p33-visible-report-actions`.

## Other requested checks

- **Merge (READ, compilation/tests RAN):** the combined MainFrame constructor keeps delete/rename hooks and main's `setLogFindings`. The old ReportsPanel constructor call in `ReportsPanelLogFindingsTest` is adapted. Searching the constructors and `ConfigStore.readReports`/`writeReports`, `ExchangeDir.of` and report-routing callers found no additional incompatible merge resolution. D-1/D-3's settings/title changes were retained; the main-side report findings remain rendered. The conflict is the one shown by `--remerge-diff`, not evidence that every auto-merged line is correct.
- **Other link cases (RAN):** direct outside links and a chain are refused; internal links, a linked project root and a case-insensitive spelling on this filesystem work. A later request rechecks a changed exchange link. Directory hard links were not created; they are not a portable supported directory operation. The old returned lexical path could be redirected after checking; that was reproduced separately from the static nested-link defect.
- **Bin sharing and persistence (RAN/READ):** an actual profile save after deletion excludes the bin. An all-category `SettingsShare.export` excludes it, and import ignores injected `deletedReport` keys. READ: `ExportSettingsDialog` passes its selected categories to that exporter; I did not manually tick every export checkbox. Both `ConfigStore.save(c)` and `save(c, globalTier)` preserve the bin. Restoring to empty and saving again does not resurrect stale keys. The last two behaviours and all-category sharing now have permanent `ReportBinTest` regressions. The CONFIG-only KnownKeys registration owns the stale machine keys, not a profile category.
- **Scope and naming (RAN/READ):** the empty path key is the no-project bin; two profiles in one root have separate bins. The key is a profile path, so moving or renaming the profile does not migrate its entries. The docs now say this. READ: renaming a live report onto a deleted name does not change the bin; restoring while that live name exists refuses without replacing it. The original taken-name and newest-first/global-20 tests pass.
- **UI (RAN):** actual MainFrame, selected Reports tab, real delete/restore/empty-bin dialogs, under an isolated home. The successful driver was the visible buttons' `doClick()`, not a socket verb masquerading as a button press. The initial native Robot attempt is not claimed as successful. A screenshot was inspected; the docs' Reports screenshot was refreshed separately from the built jar using synthetic demo data.
- **Evidence honesty (READ):** `9286c0d8` precedes `0eda3575`. The earlier RESULTS records the 23-versus-24 frame-suite miss, the restore routing mistake, missing KnownKeys registration, and the original persistence test bypassing save. Those statements agree with the final code/test changes. I cannot independently establish uncommitted trial chronology from a final file, and did not rerun the author's historical trials. My own predictions/probes were committed in `d19d234b` before implementation.

## Verification

Counts are total / failures / errors / skips; skips are not passes.

| Run | Result |
|---|---|
| Original `mvn -q clean test` | 2487 / 0 / 1 / 120; 334 reports; no orphans |
| Original source-navigation class retry | 23 / 0 / 0 / 0 |
| Original full retry | 2487 / 0 / 0 / 120; 334 reports; no orphans |
| Original registered display gate | 120 / 0 / 0 / 0; 24 suites |
| Original five p33 controls | 5 caught in 16.5 s; named failures, exact source/class restore, restored green |
| Corrected `mvn -q clean package` | 2498 / 0 / 0 / 124; 334 reports; no orphans |
| Corrected report frame class | 5 / 0 / 0 / 0 |
| Corrected registered display retry | 124 / 0 / 0 / 0; 24 suites |
| Corrected retained + new controls | 12 caught in 40.9 s; named failures, exact source/class restore, restored green |
| Corrected preflight | 24 frame suites; 206 anchors |
| Python harness tests | 5 passed |
| Strict MkDocs and diff whitespace | Passed |

The corrected full display retry passed all 124 tests with zero skips. The first corrected display attempt was **124 / 1 / 0 / 0**: `NamedGraphAndMenuSpotlightFrameTest#lightingASecondMenu_keepsWhatTheEchoSaid_byReplaceAndByAdd` expected the AI menu spotlight but observed none. That test and its implementation are unchanged by these fixes. This attempt is not omitted in favour of its retry.

The original full run's error was `SourcePanelFreshnessTest#aNewerNavigationWinsOverAnEarlierTypeClickCheck`: its polling loop called `contains` on a null pane text. The isolated retry and full retry passed. During implementation, the first targeted run also caught my immutable schema-map mistake, and canonical-path assertions needed updating. The first corrected full run was 2498 / 2 / 0 / 124: two existing external-series tests compared `/var` spelling with its canonical alias. Their expectations now compare the actual canonical file; later-file selection, legend order and row-count checks remain intact. These were development failures, not failures hidden from the final counts.

The CI logs for **36278791716 at 0eda3575** were read directly: build **2487 / 0 / 0 / 120**, ui-frame **120 / 0 / 0 / 0**, collector **199 controls caught exactly once across four shards**, and every job green. This is evidence for the reviewed head, not the newly authored fixes. New-head CI must be assessed separately by the author before merging.

See [probe and control evidence](evidence/pr33-fixes-review-2026-09-27/PREDICTIONS.md) and [results](evidence/pr33-fixes-review-2026-09-27/RESULTS.md). The historical probe deliberately asserts the old counterexamples; the permanent tests assert their corrected behaviour.

## Optional items and limits for the author

- The delete warning used to say the sections and notes were “lost” while promising recovery. It now consistently says they move to the recently-deleted list.
- Path checks do not lock directories against concurrent local filesystem mutation. Canonical paths remove the checked-alias swap, but this is not a race-free filesystem sandbox. That limit is explicit in the helper and user guide; no broad sandbox claim is made.
- The existing report-verb entrance still requires a loaded log, including listing/restoring a definition; the Reports-tab operation itself does not need that verb. Removing that precondition is a separate usability improvement, not silently included here.
- No policy change was made to the global 20-entry cap, path-based profile scope or no-project key. A stronger identity/migration scheme remains an owner choice; the current limits are now stated.

**Not verified:** Windows filesystem behaviour, directory hard links, adversarial concurrent directory replacement, arbitrary modal/project-switch interleavings, or the full mutation gate on the new code. Only the 12 relevant controls ran locally. No key, provider, client session, participant project, merge or release was used. The original author should review the new fixes and their CI before deciding to merge.
