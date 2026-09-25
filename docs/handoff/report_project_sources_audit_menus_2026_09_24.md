# Project, Sources and Audit log menus

Status: independently re-reviewed, merged as PR #15 (`fda14f70`), and shipped in 1.20.0.
[Release receipt](release_analyser_1_20_0_2026_09_24.md). The sections below preserve the earlier
implementation and verification record. Branch `feat/project-sources-audit-menus`,
based on released 1.19.2 (`cfe4c925`). The unfinished menu patch was copied from its older worktree;
that worktree was preserved. No chart-lifecycle fixes were overwritten during the transfer.

## Scope

The owner's requested separate top-level menus replace File. Project contains profile operations,
saved analyses, settings and Exit. Sources contains the three source configuration shortcuts and
opening/discovering/closing topology, design and producer diagnostics. Audit log contains local/S3
acquisition, recents, Close log, Follow, external CSV and record export. Records, Theme, AI and Help
remain separate. Keeping Records top-level preserves its existing menu spotlight addressability;
the current spotlight implementation deliberately does not support nested menu items.

Existing listeners continue to call the existing actions. No verb or persisted profile schema is
added. File-based spotlight/screenshot requests must use the actual new menu name; there is no
invisible alias. Published navigation, offline help, assistant examples and screenshot tools change
with the menu. Historical review evidence and released changelog entries are not rewritten.

## Predictions, before targeted mutations

- Renaming the Project menu back to File fails `projectSourcesAndAuditHaveTheirOwnActions`.
- Dropping the named Settings-page argument fails `sourceShortcutsOpenTheNamedSettingsPage` at
  the actual selected page assertion, rather than merely checking a listener exists.
- Removing the menu's `closeLog()` call fails `closeLogFromItsMenuPreservesTheProjectAndSavedChart`
  because the actual store remains loaded. Its green case also checks the profile and chart survive,
  then clicks Project's Close project and checks its enabled state.

All use the existing verifier's shared baseline, named assertion (not exception) failure,
byte-for-byte source restoration and restored-green run. The new suite is in both CI display lists;
the preflight compares them with source discovery.

## Original verification at 06884f1b

- Focused real-display suite: 3 / 0 / 0 / 0. Actual Swing menu items use `doClick`; the Settings
  shortcuts open actual modal dialogs, whose selected tabs are observed before disposal. Log/project
  close uses menu items, not socket substitutes. The fixture's setup uses the existing action surface.
- Full `mvn -q clean package`: 1941 / 0 / 0 / 81, meaning 1860 executed and 81 skipped. The skipped
  display cases are not counted as passes. Java 21, no POM edits.
- Display gate: 82 / 0 / 0 / 0 across all 16 registered suites. Maven receives
  `-Djava.awt.headless=false` directly; no POM edit or hidden display skip.
  [Final display results](evidence/project-sources-audit-menus-2026-09-24/display.json).
- Built-jar spotlight integration: 94 checks pass, including the renamed Audit log menu target.
- Strict MkDocs build and diff check pass; the rule-one sweep is clean.
- Python verifier tests: 5 pass. Frame-list/anchor preflight passes (16 suites, 25 anchors).
- All three menu controls met their predictions: green baseline, one named assertion failure,
  zero errors/skips, byte-identical restoration, then green.
  [Executable results](evidence/project-sources-audit-menus-2026-09-24/mutations.json).
- An initial test compile error used a nonexistent MainFrame accessor. The test now reads the existing
  project configuration; that compile error is not a behavioral mutation witness.

The first mutation attempt stopped before applying any mutation: the verifier requires an exact
Surefire test name and parameter-injected methods were reported with `(Path)`. The new tests now
use the same field-injected `@TempDir` convention as the existing guarded tests. No failed build or
missing test is counted as a mutation witness.

## Commands and inspection

Java 21 was selected through `JAVA_HOME`. Commands:

```sh
mvn -q clean package
python3 tools/verify_project_chart_review.py --mode display --output /private/tmp/menu-display.json
python3 tools/verify_project_chart_review.py --mode mutations --case menu-layout --case menu-source-page --case menu-close-log --output /private/tmp/menu-mutations.json
python3 tools/test_project_chart_review.py
python3 tools/verify-m64-spotlight.py
mkdocs build --strict
git diff --check
```

Menu tests drive the real Swing `JMenuItem.doClick()` callback on the EDT. They do not synthesise
OS mouse input or substitute a socket verb for a menu click. The full display gate also exercises
the existing chart lifecycle and Project-panel button suites. No new manual mouse-only claim is made.

The 26-image demo capture suite was regenerated from the branch jar, under an isolated home,
and every image inspected. The first Audit log capture omitted its popup and was rejected. Moving
window activation before opening the popup and recapturing produced the required visible menu.
Screenshots use native capture; their setup uses socket verbs and is not treated as menu-action
verification. The capture helper's file-success result still requires visual inspection.

Current site navigation, offline help, assistant menu examples and the changelog follow the new layout.
The [asset inventory](../site/assets/README.md#capture-audit--menu-layout-2026-09-24) records the capture
scope. Specialized earlier walkthrough images remain explicitly labelled as recorded examples; their
producer/client experiments were not rerun. Existing narrow topology layout is not claimed fixed.

## Review focus and boundaries

Review the relocation of existing listeners in `MainFrame.buildMenu`, the three named Settings
shortcuts, the preserved top-level Records menu, and the actual menu close test. Check both CI display
lists and rerun the three controls above. Saved `menu:File` spotlight requests require the new visible
name; there is no alias. Project profiles, assistant action verbs and chart lifecycle logic are unchanged.

This branch does not merge or release the menu work. The shared primary checkout and the original
unfinished menu worktree were left untouched. Release 1.19.2 continues to contain only the separately
reviewed chart fixes.

## Review response

Review read in full at `421dda11` on `review/pr15-project-sources-audit-menus-2026-09-24`.
Predictions were committed first as `7449b71c`; the unchanged-branch baseline was
1941 total / 0 failures / 0 errors / 81 skips. This response remains on the original feature branch.

**What I got wrong:** I presented the layout test as protection for the relocation, but its
`containsAll` assertions covered only subsets. It could not see a lost action, and the Close project
assertion checked a disabled item rather than the completed transition. The three original controls
proved their narrow behaviours, not the completeness of the move. The review's five-change mutation
exposed that gap; agreement with the intended layout was not regression protection.

### Required corrections

| Finding | Cause and fix | Regression and required witness |
|---|---|---|
| R1 | Subset assertions missed lost/misplaced actions. `MenuInventory` now supplies exact ordered labels and separators to both the live-menu and documentation checks. The live test also compares the three-menu union with the independently preserved 26-item base File inventory plus three shortcuts, once each, and checks Records. | `projectSourcesAndAuditHaveTheirOwnActions`; remove Exit, Close graph, Close log and topology, or the recent-log submenu in four separate controls. All three close tests force `sessionInteractive=false` before the real click and require true afterwards; remove the reset declaration in a fifth control. |
| R2 | Close outcomes were incompletely asserted. New fixtures load both a log and a one-node topology. Closing both must remove both while retaining the project path and chart definitions. Close project must leave the actual path empty. | `closeBothFromItsMenuKeepsProjectAndCharts`, `closeGraphFromItsMenuKeepsLogProjectAndCharts`, and the strengthened existing Close log test. Replace reset's `resetAll()` with `closeLog()`; the graph-closed assertion must fail. |
| R3 | Offline help used an obsolete S3 label, and paths had no static guard. Correct it and check paths in README, every site Markdown file, and help.html against `MenuInventory`. The parser normalizes whitespace and optional ellipses and handles the published HTML entities/inline formatting and wrapped paths; it does not accept a valid label as a prefix of an invalid one. | `MenuDocumentationTest.documentedPathsNameExistingItems` and `extractsHtmlMarkdownAndReportsLocations`. Restore the incorrect S3 label; the assertion must name help.html, line and invalid path. |

The doc guard also required replacing combined Export/Import and CSV/YAML pseudo-labels with actual
items, expanding the Flag action's abbreviation, marking the plain template path explicitly, and
linking *Rolled log sets* as a documentation section instead of presenting it as a Records menu item.
These are wording corrections; no additional menu actions were created.

### Optional improvements

O1–O5 are taken: declare the renamed item once; separate Close project from Close log and topology;
place recents beside their open actions; align reset status, changelog and getting-started wording;
remove the image qualification from the image-free Spring overview; use the full Open log label in
the empty-state guidance. Capture only the affected menu images and the start page, whose reset
status also changed. All five affected assets were recaptured at 3360×2100 under the isolated home and opened for
inspection. Each menu popup is visible; the recents and separator match the inventory. The start
page shows the renamed status and full Open log guidance. The other images were not recaptured.

### Owner decisions carried, not resolved

- **D1:** CSV remains under Audit log. Whether it belongs elsewhere remains the owner's decision.
- **D2:** the existing no-alias behaviour for `menu:File` is unchanged. The compatibility consequence
  is now explicit in the changelog; this response does not settle the policy.
- **D3:** inspection of git metadata confirms merge commits `870f2833` (#7), `aa49a8f6` (#9) and
  `cdbcd342` (#10) use the fourth sweep term's domain as author email. The repository-local pin does
  not govern GitHub web merges. The shared config reads `review@local`, and pushed commit `262fc020`
  uses it. No restricted domain is reproduced here, no shared config was changed, and no history
  was rewritten. This response's commits explicitly use the verified personal address.

### Verification record

The two new headless tests and all five menu frame tests pass in a focused run (7/0/0/0 with a display).
An initial compile attempt exposed checked reflection exceptions inside Runnable callbacks; the test
helper now handles reflection failures explicitly. That compile error is not a mutation witness.
All ten menu controls passed: shared green baseline (7/0/0/0), a named `<failure>` and no `<error>`
for each plant, byte-identical restoration, then restored green. The seven new predictions all held.
[Response mutation results](evidence/project-sources-audit-menus-2026-09-24/review-response/mutations.json)
are separate from the original three-control evidence above.

`mvn -q clean package` passed with **1945 / 0 / 0 / 83** (1862 executed), summed only from the
259 XML reports mapped to `src/test/java`; **no orphan reports**. This matches the frozen count.
[Mapped counts](evidence/project-sources-audit-menus-2026-09-24/review-response/headless-counts.json).
The full display gate passed **84 / 0 / 0 / 0**, across all 16 frame suites, matching the prediction.
[Display result](evidence/project-sources-audit-menus-2026-09-24/review-response/display.json).
It passes `-Djava.awt.headless=false` directly; no POM edit was made. An initial sandboxed attempt
stopped with a missing `PairingDuringLoadFrameTest` report, before publishing a result. It is not
counted as a pass; the complete run outside the sandbox is the result above.
The five Python verifier tests pass. The rebuilt-jar spotlight gate passes **94/94**;
[recorded output](evidence/project-sources-audit-menus-2026-09-24/review-response/spotlight.txt).
Strict MkDocs, `git diff --check`, the exact rule-one sweep and the added-lines sweep pass.
Screenshots were captured only after the package build and
before display tests, so test windows could not cover the capture window.

The other commands were the package, Python, spotlight, MkDocs and diff commands listed above,
plus `python3 tools/verify_project_chart_review.py --mode display --output
/private/tmp/pr15-response-display.json`. Capture commands were `python3 tools/capture-docs.py
--projects-menu` and `python3 tools/capture-docs.py --start-page`, with JDK 21 first on `PATH`.
[Menu capture log](evidence/project-sources-audit-menus-2026-09-24/review-response/capture-menus.txt)
and [start-page capture log](evidence/project-sources-audit-menus-2026-09-24/review-response/capture-start-page.txt)
record the five captures; the appearance claims above come from opening the images, not their logs.

The mutation command for this response (JDK 21 selected through `JAVA_HOME`) was:

```sh
python3 tools/verify_project_chart_review.py --mode mutations \
  --case menu-layout --case menu-source-page --case menu-close-log \
  --case menu-exit --case menu-close-graph-item --case menu-reset-item \
  --case menu-recent-log --case menu-reset-human --case menu-reset-topology \
  --case menu-help-s3 --output /private/tmp/pr15-response-mutations.json
```

The chooser/network actions and Exit were inspected as retained listeners, not clicked. Pending-load
close interleavings are not newly claimed by these tests. The local display is macOS; Linux/xvfb
results belong to CI and will be stated separately from local runs.

## Main integration — 2026-09-24

Integrated fetched main `4d787d1b` into the feature branch after review-response commit `e1eec2e4`,
using a merge as authorised, so the pushed PR history does not need a force-push. The two textual
conflicts were CHANGELOG and CI: retain both sets of unreleased entries, and both sets of frame
suites in both lists. `MainFrame` and the verifier merged automatically; inspection against main
confirms the menu relocation and close listeners remain, alongside the newly merged chart fixes.

Per owner instruction, **the mutation gate was not rerun** on this combined tree. Earlier mutation
results above apply to their recorded pre-integration tree; no new mutation claim is made. Preflight
checks all 46 anchors and both CI lists (19 suites), without running a mutation.

JDK 21 `mvn -q clean package`: **1985 total / 0 failures / 0 errors / 97 skips**,
265 source-mapped XML reports, no orphans (1888 executed).
[Counts](evidence/project-sources-audit-menus-2026-09-24/main-integration/headless-counts.json).
`python3 tools/test_project_chart_review.py`: five tests pass. Strict MkDocs passed.
Display: **98 / 0 / 0 / 0**, all 19 suites, using
`python3 tools/verify_project_chart_review.py --mode display --output /private/tmp/pr15-main-display.json`.
[Result](evidence/project-sources-audit-menus-2026-09-24/main-integration/display.json).
The command passes `-Djava.awt.headless=false` directly. `python3 tools/verify-m64-spotlight.py`
passes **94/94** against the rebuilt jar under an isolated home;
[output](evidence/project-sources-audit-menus-2026-09-24/main-integration/spotlight.txt).
Strict docs, diff checks and both public-repository sweeps are clean. No screenshot recapture: main's additions do not alter the captured menu layout.

### Refresh to main's 1.19.3 stamp

Fetched main again at the owner's request: `cb56c96d` adds only the 1.19.3 changelog heading.
Merge resolution retains the menu entry under Unreleased and puts main's shipped entries under
1.19.3. No source or test implementation changed. The non-mutation gates are rerun below; the
mutation gate remains deliberately omitted.

Fresh JDK 21 `mvn -q clean package`: **1985 / 0 / 0 / 97**, 265 mapped suites, no orphans;
[XML totals](evidence/project-sources-audit-menus-2026-09-24/main-1193-integration/headless-counts.json).
Display command: `python3 tools/verify_project_chart_review.py --mode display --output
/private/tmp/pr15-1193-display.json`, using the direct headless=false property: **98 / 0 / 0 / 0**
across all 19 suites; [result](evidence/project-sources-audit-menus-2026-09-24/main-1193-integration/display.json).
Five Python verifier tests passed; preflight found all 46 anchors; strict MkDocs passed.

`python3 tools/verify-m64-spotlight.py` against the freshly packaged jar: **94/94**;
[output](evidence/project-sources-audit-menus-2026-09-24/main-1193-integration/spotlight.txt).
Diff check, exact rule-one and added-lines sweeps are clean. No mutation rerun, screenshot
recapture, merge to main, release or shared identity-config change was performed.
