# Project-panel and chart-lifecycle review and fixes

## Verdict

**The pinned `00b39ce8` tree is not ready unchanged.** This branch fixes reproduced chart-loss paths and adds real-display and mutation checks. The corrected branch passes the gates recorded below. Nothing is merged or released.

Subject: `00b39ce8`, with the code and documentation commits listed in the requesting brief. Worktree: an isolated checkout of that commit; branch `review-fix/project-chart-lifecycle-2026-09-24`. The separate `fix/chart-delete-cancel-and-revealer` branch is not included. Its integration still needs review on the combined tree.

This is not a fresh reviewer with no history. I authored the earlier `90746e83` review and had an unpublished integration prototype. Predictions acknowledge that prior knowledge. I re-ran the probes on the pinned baseline and did not use the prototype's results as fresh evidence. Fixes in this report are author-verified, not independently reviewed.

## Findings fixed on this branch

The findings below distinguish failures reproduced on the pinned tree from narrower source-inspection conclusions.

Locations below refer to `00b39ce8` unless explicitly marked “fixed tree”. Paths under `src/main/java/telamin/fluxtion/audit/analyser/analyser/` are abbreviated.

### F1 — High: explicit names bypassed reservation

**`ui/GraphTabs.java:151`, `:251`; `config/SavedGraphMerge.java:37`. Reproduced.** With an open `One` and a closed, annotated `Graph 2`, `graphForAction("Graph 2", true)` created a blank tab with the closed definition's identity. Saving chose the blank live tab. Requesting a second `One` also created duplicate tabs. Thus the earlier R4 closure covered rename, but not creation.

Fixed: explicit creation refuses any existing open or saved identity. Generated names still reserve closed definitions. The action response explains the refusal and the alternative of reopening without `newTab`. `closedNamesAreReservedForCreationAndRename` exercises the production frame; the `explicit-name` mutation removes the guard and fails it. `reserved-names` separately breaks the existing generated-name guard and fails `aGeneratedNameSkipsOneAClosedChartStillHolds`.

### F2 — High: targeting a closed chart discarded its content

**`ui/GraphTabs.java:251–256`. Reproduced.** An ordinary named graph action on saved-but-closed `Two`, without `newTab`, created a new empty chart. The next save replaced its explanation, notes, style and pin. This differs from F1: refusing explicit duplicate creation alone would merely make the existing edit request stop working.

Fixed: `MainFrame` supplies the actual saved definitions. A named action reopens the matching definition before editing it. `graphActionOnClosedChartPreservesItsDefinition` fails when the `closed-definition` mutation disables that lookup. The supplemental witness re-runs this after the null-safe name comparison was added. Project-row Open already used `openSaved`; it is now covered through the real adapter as well.

### F3 — High: duplicate saved definitions were silently collapsed

**`config/SavedGraphMerge.java:37`, `:44`; `config/SettingsShare.java:319`. Reproduced.** Two profile entries named `Same` with different explanations loaded successfully; the merge discarded one. Duplicate open tabs were collapsed by the map in the opposite direction. The original `SavedGraphMergeTest` deliberately asserted one-entry collapse, so that green test endorsed loss.

Fixed: reject duplicate named definitions before import application, before clearing/rebuilding tabs, and before merging either list. A refused profile leaves its bytes and the target configuration unchanged. No winner is selected and no automatic rename is invented. Legacy unnamed entries remain allowed by this check; this pass did not add a migration for arbitrary hand-edited naming conventions.

`GraphProfileMetadataTest.duplicateNamesRefuseWithoutChangingProfileOrTarget`, `mergeRefusesAmbiguousOpenTabsWithoutChoosingAWinner`, and `SavedGraphMergeTest.aDuplicateNameInTheProfileIsRefusedInsteadOfDiscarded` have separate `duplicate-profile`, `duplicate-tabs` and `duplicate-saved` mutation witnesses.

### F4 — High: import refresh overwrote the incoming definition

**`ui/MainFrame.java:4724–4727`; related ordering at `:5267`. Reproduced; carried from the ledger.** Import Points/closed over a live Line/open chart. `onConfigChanged()` saved the old tabs into the newly imported configuration before `restore` read it. Result: Line/open remained, although the imported definition said otherwise.

Fixed: install a snapshot of incoming definitions in the view before the callback that may save; use the same ordering for project-settings application. The test goes through real `SettingsShare.preview/apply` and the production post-dialog refresh, then inspects persisted state. It does **not** drive the file chooser/import confirmation dialog. `import-before-view` restores the old ordering and fails `importingOverAnOpenChartKeepsIncomingDefinitionAndOpenState`.

### F5 — Medium: a stale Delete question could delete a replacement

**`ui/GraphTabs.java:495–506`. Reproduced in a real modal dialog.** Ask to delete `One`; while its dialog runs the nested EDT event loop, replace the chart at the selected index with `Replacement`; confirm. The old index deleted `Replacement`, which the question had never named.

Fixed: retain the original pane and name, then locate that pane again after confirmation. If it disappeared or was renamed, the confirmation does nothing. `confirmationCannotDeleteAReplacementTab` uses the real toolbar and dialog, replaces the view while the question is open, then presses OK. `stale-confirmation` restores deletion by the original index and fails that assertion.

### F6 — Medium: an action request could wait for an unrelated modal

**`ui/GraphTabs.java:288–295`; `ui/ActionExecutor.java:316`. Inspected before; exercised after correction.** Rename an open chart onto a closed chart's name through the action API. The shared rename implementation opened a blocking JOptionPane; the API's false branch also misleadingly said no graph existed.

Fixed: the shared operation returns refusal without a dialog; only the toolbar's interactive prompt presents the warning. The action distinguishes a missing target, blank new name and collision. The real-frame name test now checks refusal against a closed name. A separate `rename-modal` mutation restores the blocking dialog. A timer detects and dismisses it, so the test fails at the no-modal assertion rather than hanging. The creation mutation is independent of this policy.

### F7 — Medium: the importer fix lacked a load-bearing regression test

**`config/SettingsShare.java:354`; `StyleDropdownRequestsASaveTest`'s direct wither test. Carried and reproduced.** Changing the importer back to the old shorter `GraphSpec` constructor lost `style` and `open`, while the direct wither test still checked a different call site.

Fixed coverage: `GraphProfileMetadataTest.externalPathsKeepStyleAndClosedState` saves and loads an actual project profile with external series and, separately, an external marker. Points and closed must survive the path rewrite. `external-import` changes the actual importer constructor back and fails the style assertion. The production wither fix in `90746e83` remains intact.

### F8 — Medium: documentation still contradicted the accepted boundary

**`docs/ONBOARDING.md:115`, `docs/specs/completed/spec-ai-menu.md:24`, `docs/specs/spec-project-starter-journey.md:287–298`, `docs/specs/completed/spec-loaded-panel.md:104`. Inspected.** “Nothing changes state” forbade the newly durable open flag, despite the owner accepting reveal of a particular existing definition. A method-set test can constrain the panel's vocabulary, but cannot establish what its adapter does.

Corrected all four governing documents: navigation may persist an existing chart's open-view state; it may not edit or discard its content. I agree with the amendment: allocating a view for a definition that already exists is reveal, provided it does not replace that definition or rebuild an already-open edited pane. The adapter tests below exercise both limits. Inspection of `ProjectModel.java:355` and `:378` confirms that chart rows carry the saved name and report rows carry `name` separately from displayed `title`; the other row targets do not select a report by their label. The display test checks the selected identity and rendered detail title in both directions.

Also corrected: leftover dead-button language in test commentary, the test's “two methods” javadoc, the attribution of the Delete toolbar to the later collision fix rather than `38ecc7f3`, and the restore proposal's claim that three differences exhaust its gap. The selected main right-hand tab is not captured either. The historical investigation now distinguishes its original unverified UI checks from this follow-up and does not claim an immutable commit message was rewritten.

## Required work remaining

No merge or release is performed here. Review the combined tree before integrating the separately reviewed `bd5cfe40` branch: it touches the same adapter and Delete code, and this report does not certify that combination. Do not resolve those conflicts by choosing an entire file from either side.

The code fixes in this packet need independent review. The earlier reject report and preserved evidence are unedited; its author-added header is not a closure certificate. Contrary to the new brief's shorthand, that header actually said R9 was **partly** fixed and its adapter still untested.

## Optional improvements and owner decisions

- **Owner decision — last-tab policy.** Close on the last tab remains a no-op. Restoring all-closed definitions produces a blank placeholder, and deleting the last open tab does too. Name reservation protects the closed definitions, but a genuinely zero-tab session would be clearer if that is the desired product policy. This branch does not silently choose it. My first Cancel/Delete assertion incorrectly assumed deleting `Graph 1` could never leave that name present; the fallback legitimately creates a new empty `Graph 1`. I corrected the test to use `Delete me`, preserving the failed attempt as a test-assumption error rather than calling it another loss of the old definition.
- **Optional — safer GraphSpec evolution.** Prefer withers/copy operations to field-by-field rebuilding. Back-compatible constructors are why metadata omission compiled. This branch tests the importer call site; it does not remove public constructors.
- **Optional — recovery UI policy.** Zoom is still transient while pin is persistent. The proposal remains a proposal; no restore/reload UX change is implemented or approved by this review.
- **Optional — naming migration.** Manually edited noncanonical names and unnamed closed entries deserve a dedicated compatibility policy. The refusal here targets duplicate named identities; it is not a claim that every malformed profile is recoverable automatically.

## Earlier R1–R12: checked disposition

| Earlier item | Finding at the pinned baseline / disposition here |
|---|---|
| R1, R2 | Generated-name reservation works. Real Delete/Cancel and delete-then-create preserve the unrelated closed chart. Separate `reserved-names` witness is red. Changing delete ordering alone is not an independent witness while reservation holds; I do not claim otherwise. |
| R3 | Rename moves the stored definition through MainFrame; the real-frame check leaves only the new name plus the unrelated closed definition. |
| R4 | **Not fully fixed at baseline**: explicit creation still duplicates names (F1), and the merge accepts duplicates (F3). Fixed here. |
| R5 | Extraction made the real merge testable; `merge-preservation`, `restore-closed`, `reopen-notifies` all fail named assertions. Display tests now cover the adapter boundary the older tests could not. |
| R6 | `90746e83` listener fix retained; `style-dropdown` red witness reproduced. Real-frame persistence checked. The earlier eight-look-and-feel/physical-mouse probe is prior evidence, not re-run here. |
| R7 | Production wither retained; the new importer round trip closes the call-site gap (F7). F4 fixes the separate refresh ordering loss. |
| R8 | Parent source confirms saved rows rendered **no Open button**. The published correction is right; remaining test prose corrected here. |
| R9 | `openSaved` notification is load-bearing; real row clicks now reach both MainFrame adapters. Existing pane identity and later edits survive. D-L3 wording clarified rather than asserting navigation leaves every byte unchanged. |
| R10 | Journey supersession is valid; residual absolute no-mutation wording, including the AI-menu spec, corrected. |
| R11 | Four input roles plus the applied view map independently read in SessionResumeStore/MainFrame. The corrected proposal is substantially right; its exhaustive-gap claim needed narrowing. |
| R12 | CHANGELOG correctly states the first save writes explicit style keys. No claim that an old profile stays byte-identical across upgrade. |

The `b8197eb9` substitutions were checked against the code commits: navigation `35eeb320`, close/delete `38ecc7f3`, style/wither `90746e83`, collision/merge follow-up `f6e8d7e0`. The Delete-toolbar misattribution in the journey was the correction needed. No new M68 slice number is assigned here; evidence-integrity's M68 is untouched.

## Evidence and checks actually run

All fixtures in this packet are **constructed regression cases**, with placeholder names and an isolated `user.home`. No customer profile, real log, API key or LLM client session was used.

- Predictions were committed as `2a6a3301` before production changes: [predictions](evidence/project-chart-review-fix-2026-09-24/predictions.md).
- Unchanged baseline: **1,909 total / 0 failures / 0 errors / 62 skips** (1,847 executed): [baseline](evidence/project-chart-review-fix-2026-09-24/baseline.json).
- New display probes on unchanged production: **8 total / 4 failures / 0 errors / 0 skips**, reproducing F1, F2, F4, F5: [before output](evidence/project-chart-review-fix-2026-09-24/before.txt).
- Duplicate-profile/merge probes before their fix: **3 total / 2 failures / 0 errors / 0 skips**: [before output](evidence/project-chart-review-fix-2026-09-24/duplicate-before.txt).
- Fixed tree, `JAVA_HOME=<JDK21> mvn -q test`: **1,923 total / 0 failures / 0 errors / 73 skips**, **1,850 executed**. The 73 skips are the prior 62 display cases plus this branch's 11 new frame cases, not passes: [full-suite counts](evidence/project-chart-review-fix-2026-09-24/full-suite.json).
- Mutation evidence: [initial 14 controls](evidence/project-chart-review-fix-2026-09-24/mutations.json), [supplemental duplicate-saved and null-safe closed lookup controls](evidence/project-chart-review-fix-2026-09-24/mutations-additional.json). **16 distinct mutations**. The initial explicit-name and closed-definition runs were error-only reds (an exception from the downstream duplicate guard and a null dereference); they did **not** meet the named-assertion standard. Those attempts are preserved. The tests and verifier were tightened, and [assertion-corrected witnesses](evidence/project-chart-review-fix-2026-09-24/mutations-assertion-corrections.json) supersede those two results. Count only the corrected witnesses: all 16 have a green baseline, failure at the named assertion, byte-identical source restoration and restored green run. The [no-modal control](evidence/project-chart-review-fix-2026-09-24/mutation-rename.json) separately proves the refusal stays non-interactive. The supplemental closed-lookup run replaces no historical evidence. The runner records source SHA-256 and actual Maven output; it restores file copies in `finally`, never `git checkout`.

Reproduce on a real display with JDK 21:

```sh
python3 tools/verify_project_chart_review.py --mode display --output /tmp/chart-display.json
python3 tools/verify_project_chart_review.py --mode mutations --output /tmp/chart-mutations.json
```

The display mode reads and compares both CI class lists, rejects missing/empty/skipped suites, then runs the exact list. The new frame suite is in both execution and no-skip lists. Mutation mode accepts `--case NAME` for a bounded reproduction. Do not run it concurrently with another Maven run or source editor in the same worktree.

### What “real display” means here

These are real visible Swing frames on the Mac display. Tests press the **actual Project-row, Close, New graph and Delete buttons** with `doClick()`; a Swing timer presses the actual confirmation buttons while the modal event loop runs. No MCP/socket verb substitutes for a Project-row or Delete click. The style test selects the actual JComboBox. This is UI automation, not a claim of fresh manual human testing or a physical mouse for this pass.

Project/log setup and reload use ActionExecutor; the import test calls the production refresh after real SettingsShare application. Several tests explicitly flush pending persistence before reading profile bytes. They establish the stored result, not debounce timing. The existing mutation test separately establishes that the dropdown requests a save. The frame check selects Points then Line in the actual control, closes/reopens the project and log through the action surface, and asserts the reopened control says Line; it does not claim to click the project menu.

The [native Delete-dialog screenshot](../site/assets/chart-delete-confirmation.png) was captured from the isolated placeholder fixture, visually inspected (including every visible string), and added to the guide. An initial capture attempt passed the path inside Maven argLine, which did not propagate it; the successful run supplied `-Dreview.captureDir` directly. Both executions passed the Cancel/Delete assertions; only the latter produced the image.

The new frame cases cover: report title versus identity in both directions; saved-chart reopen; an already-open pane retaining later edits; Close retaining its definition and reload leaving it closed; pin, notes, explanation and style on reopen; Delete/Cancel and stale confirmation; explicit and generated names; import ordering; and project switching without merging outgoing charts into the incoming profile.

### Final gates

- JDK 21 full test phase and package: green, **1,923 / 0 failures / 0 errors / 73 skips**.
- Exact CI display list: **74 total / 0 failures / 0 errors / 0 skips**, across 13 suites: [display record](evidence/project-chart-review-fix-2026-09-24/display.json). This is a separate display run, not 74 additional unique tests to add to the headless total.
- `python3 tools/test_tools.py`: all 26 checks passed.
- `mkdocs build --strict`: passed.
- `git diff --check`: clean after adding two narrow end-of-line-whitespace exemptions for raw Maven failure output. Staging exposed those spaces; the recorded failures were preserved byte-for-byte rather than tidied. The package-generated dependency-reduced POM is excluded from the change.
- Rule-one tracked-file sweep (after staging this packet): clean. Public git author email checked; no customer data is introduced.

No production server, remote key, client trial, merge, release, deployment or changes to the primary shared checkout. Menu reorganisation and its screenshot work remain in their separate worktree and are not silently folded into this scope.
