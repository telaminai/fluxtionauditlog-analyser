# PR 15 menu guard follow-ups

Status: independently reviewed; F1 corrected before merge on `fix/menu-guard-followups`, based on main `fda14f70`.
Read the complete re-review at `88afe42b` before changes. PR #15 was merged locally as `fda14f70`,
with the personal author and committer address, after confirming all head checks were green.
GitHub reports it MERGED. The shared repository email is restored to the personal address as the
owner requested; no history was rewritten. No release is performed in this work.

Predictions: `f98eb66a`, committed before trials. No new test methods were added.

## Disposition

- **O-a implemented:** AI, Theme and Help have explicit ordered inventories checked against live
  menus. Documentation scans README, site, skills, help and the assistant system prompt. Java sources
  are parsed with the JDK compiler (parse only, no application execution); string literals, text blocks
  and constant string concatenations are checked, comments are excluded. Settings paths are dialog
  tabs, and Working with AI is a documentation navigation section, explicitly outside menu scope.
  The check covers the first item; dynamic descendants and dialog controls are not menu inventory.
  Computed strings and paths assembled across method calls are not evaluated.
- **O-b implemented:** two named controls remove the Close log and Close graph interactive assignments.
- **O-c implemented:** README names both Export settings and Import settings, and the changelog records it.
- **O-d focus retry withdrawn after review:** the retry could dismiss the already-open popup if
  activation succeeded. Restore the original focus assumption; the full display gate still rejects
  any skip. The unsuccessful retry measurements below remain historical evidence.
- **O-d second-menu flake not claimed fixed:** inspection shows the deferred old-menu callback removes
  only targets whose menu name matches its own, and refuses to remove while that popup is showing.
  Ten before runs did not reproduce the failure. That does not identify its cause or establish that
  it cannot occur under full-suite conditions. The spotlight test and its existing waits are left
  byte-identical, as requested when the cause cannot be established. No speculative wait was added.

## Measurements and boundaries

The two complete display classes were run together ten times before changing either one. All ten
passed: PersonAtTheScreenFrameTest **30 / 0 / 0 / 0**, NamedGraphAndMenuSpotlightFrameTest
**60 / 0 / 0 / 0**. This matches the frozen prediction; it is a small isolation sample, not proof of
full-suite stability. [Before results](evidence/menu-guard-followups-2026-09-24/display-before.json).

The repetition driver is [preserved](evidence/menu-guard-followups-2026-09-24/repeat-display.py).
It runs `mvn -q test -Djava.awt.headless=false -Dtest=PersonAtTheScreenFrameTest,NamedGraphAndMenuSpotlightFrameTest`,
removes each named suite's stale XML first, preserves failures/skips and records fresh XML for every run.
Java 21 is selected via `JAVA_HOME`. These are real macOS display runs, not a Linux/Xvfb CI claim.

The ten after runs produced PersonAtTheScreen **30 / 0 / 0 / 2** (28 executed), and spotlight
**60 / 0 / 0 / 0**. Runs 8 and 9 skipped the focus-dependent case: their first request and single retry both
failed to establish focus. All other first requests succeeded; no retry succeeded in this sample.
[After results](evidence/menu-guard-followups-2026-09-24/display-after.json). **The all-green after
prediction was wrong.** The trial retry recorded what happened, but these data do not establish
improved stability. A refused native focus request remains an environmental limitation, and is not
counted as a pass. The full display gate must still run with zero skips.

The first targeted-control attempt stopped at its baseline, before planting anything: the new
text-block unit example exposed a path boundary error (`Help ▸ About` followed on the next line by
an AI path was read as `About AI`). The extractor now stops before a new menu path.
[Failed baseline](evidence/menu-guard-followups-2026-09-24/baseline-block-boundary-failure.json)
is preserved; it is not a successful mutation witness.

## Targeted witnesses (RUN)

All five controls passed from a shared **7 / 0 / 0 / 0** baseline, each a named `<failure>`
(not `<error>`), byte-identical restoration and restored green. [Evidence](evidence/menu-guard-followups-2026-09-24/mutations.json).

| Control | Named failure |
|---|---|
| menu-doc-ai | documentedPathsNameExistingItems: faq.md:30, AI → Nonexistent item |
| menu-java-path | documentedPathsNameExistingItems: ProjectModel.java:195, Audit log → Open |
| menu-close-log-human | closeLogFromItsMenuPreservesTheProjectAndSavedChart: Close log declares human intent |
| menu-close-graph-human | closeGraphFromItsMenuKeepsLogProjectAndCharts: Close graph declares human intent |
| menu-help-s3 (existing control) | documentedPathsNameExistingItems: help.html:263, Audit log → Open from S3 |

Command (JDK 21): `python3 tools/verify_project_chart_review.py --mode mutations --case menu-doc-ai
--case menu-java-path --case menu-close-log-human --case menu-close-graph-human --case menu-help-s3
--output /private/tmp/menu-followups-mutations.json`. No other mutation cases ran.

## Final gates

`JAVA_HOME=<Corretto 21> mvn -q clean package`: **1985 / 0 / 0 / 97** (1888 executed),
265 source-mapped suites, no orphans; [counts](evidence/menu-guard-followups-2026-09-24/headless-counts.json).
This matches the prediction. Python verifier tests: **5 pass**. Preflight: **19 frame suites,
50 anchors**; both CI lists match discovery, with no new FrameTest class. Strict MkDocs passed.
The final full display gate passed on its first run: **98 / 0 / 0 / 0**, all 19 suites,
using `python3 tools/verify_project_chart_review.py --mode display --output
/private/tmp/menu-followups-display.json`. The verifier passes `-Djava.awt.headless=false` directly.
[Result](evidence/menu-guard-followups-2026-09-24/display.json). This does not erase the two skips
in the deliberately repeated measurement above. `python3 tools/verify-m64-spotlight.py` passed **94/94** against the clean package's jar with an
isolated home; [output](evidence/menu-guard-followups-2026-09-24/spotlight.txt). `mkdocs build --strict`,
`git diff --check`, the exact rule-one sweep and the added-lines sweep passed. No screenshots were
recaptured because no production UI or menu layout changed.

Everything labelled RUN above was executed here; the popup-listener diagnosis is source inspection
only. No client session, key, runtime or deployment experiment was used. Linux/Xvfb and the native
focus retry succeeding on its second attempt were not verified. O-d's unexplained spotlight flake
remains a follow-up; the existing test was left byte-identical. No owner policy was changed.

## PR #17 review response

Read the full independent review at `cd2814d0`,
`docs/handoff/review_pr17_menu_guard_followups_2026_09_24_claude.md`. F1 is resolved by removing
the retry and its attempt logging: `PersonAtTheScreenFrameTest` is byte-identical to main
`fda14f70`. The existing popup assertion and focus assumption remain. This removes the unverified
success path instead of claiming to repair focus acquisition. No product code changed.

The earlier evidence and counts above describe `952ad0a7`; they have not been rewritten.
No mutation gate is repeated for this removal. The optional scanner refinements remain follow-ups.

Post-removal checks (RUN): `JAVA_HOME=<Corretto 21> mvn -q test` passed
**1985 / 0 / 0 / 97**, 265 source-mapped reports, no orphans. The first sandboxed attempt
failed because loopback sockets were denied; the unrestricted rerun passed. Strict MkDocs and
`git diff --check` passed. No local display run was started alongside the separate PR #18
comparison; the new PR-head CI display gate must pass before merging.
