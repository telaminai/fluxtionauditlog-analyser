# PR 15 review-response predictions — before changes or trials

Subject: `06884f1b`; review read in full from `421dda11`. No implementation or test changes precede
this prediction commit. Work stays on `feat/project-sources-audit-menus` without a rebase or merge.

## Predicted controls

| Mutation | Named test and expected failing assertion |
|---|---|
| Remove `projectMenu.add(exit)` | `MenuLayoutFrameTest.projectSourcesAndAuditHaveTheirOwnActions`: Project's exact ordered inventory lacks Exit. |
| Remove `sources.add(closeGraphItem)` | Same test: Sources' exact ordered inventory lacks Close graph. |
| Remove `projectMenu.add(resetItem)` | Same test: Project's exact ordered inventory lacks Close log and topology. |
| Remove `audit.add(recentMenu)` | Same test: Audit log's exact ordered inventory lacks Open recent audit log. |
| Remove the reset listener's `sessionInteractive = true` | `MenuLayoutFrameTest.closeBothFromItsMenuKeepsProjectAndCharts`: human intent is false after the click. |
| Replace the reset listener's `resetAll()` with `closeLog()` | Same test: the topology remains open at the graph-closed assertion. |
| Restore `Open from S3…` in offline help | `MenuDocumentationTest.documentedPathsNameExistingItems`: a failure names help.html and the offending line/path. |

Keep the three existing controls. Each new control must establish a green baseline, one named
`failure` rather than an `error`, byte-identical restore from the captured file bytes, and restored
green. No compiler error, absent test, or skipped display case counts as a witness.

## Planned tests and counts

- Strengthen the existing inventory test, sharing one test inventory with the headless documentation
  guard. Assert the ordered labels and separators, all 26 legacy File items (with the one renamed
  label) plus the three shortcuts, exactly once. Also pin Records so the documentation guard is not
  checked against an unverified list.
- Extend the existing Close log test with the human-intent assertion and the actual empty project
  path after Close project. Add two frame tests for Close graph and Close log and topology. Load a
  real fixture topology before each; assert the reset keeps the project and chart definitions.
- Add two headless documentation tests: all published menu paths, plus parser/location cases.
- Expected total: **1945 / 0 / 0 / 83**, up from 1941 / 0 / 0 / 81: four added tests, two display-gated.
  Expected display: **84 / 0 / 0 / 0**, still 16 frame suites. Python verifier tests remain five.
  Existing spotlight checks remain 94. These are predictions, not results.

## Optional items and owner decisions

Take O1–O5: one declaration for the renamed reset item; a separate close-input group; recent menus
beside their open items; matching status/help/changelog wording; remove the image qualification from
an image-free page; exact log opener labels; recapture only changed menu assets plus the reset-status
start-page asset if needed. Prefer the requested 3360×2100 native capture and verify actual geometry.

Do not decide D1 (CSV placement), D2 (compatibility alias policy), or D3 (identity policy/history).
The shared config was read as `review@local`; use the verified personal address via per-command
`-c user.email=…`, never change the shared config. Record the reported merge-identity and pushed
placeholder-identity cases without spelling restricted domains or rewriting history.

## Limits and expected discovery

The old regression claimed protection for the relocation but checked only subsets. That was my
error. The new documentation guard may reveal existing abbreviations or prose that looks like a
menu path without naming a real item; correct the wording rather than silently teaching it aliases.
No chooser/network action or Exit click is newly claimed verified by these close tests.
