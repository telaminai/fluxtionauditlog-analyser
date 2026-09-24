# Project, Sources and Audit log menus

Status: implemented and locally verified; ready for independent PR review. Branch `feat/project-sources-audit-menus`,
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

## Verification

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
