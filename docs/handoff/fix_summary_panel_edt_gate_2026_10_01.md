# SummaryPanel fault accounting in the display gate

Base: `258da371`, the PR #87 merge on main. JDK 21; isolated worktree; DEMO fixtures only.
The application and generated session processor are unchanged. No provider calls or regeneration.

## Cause and scope

The SummaryPanel exception in [main's successful ui-frame job](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36835367607/job/110281444228)
comes from `LogFindingsOnEverySurfaceFrameTest#aLoadThatThrowsPartWayDoesNotStopTheNextLogsEvidence`.
That test deliberately sets the frame's final `summaryPanel` field to null, lets a load fail after
`LogOpened`, restores the panel, then proves that the next log is scanned. It protects the PR #43
F1 correction: an unfinished scan must not swallow the next generation's scan.

This is deliberate fault injection, not evidence that ordinary frame construction leaves the panel
null. Previously the test neither asserted nor accounted for the exception: the EDT printed it while
JUnit reported success. An unrelated asynchronous exception could likewise escape the suite's checks.

`EdtExceptionWatch` now captures asynchronous EDT exceptions during each log-findings frame test.
The injected-fault test must observe one NullPointerException from `MainFrame.applyLoaded` naming
`summaryPanel`. Any additional exception fails teardown. The original handler and reflected panel
are restored even on assertion failure. The watcher drains work queued before its teardown barrier;
it does not promise to catch callbacks submitted after the test has finished, or exceptions in other suites.

## Reproduction and verification

All rows below were **RAN** locally. Counts are total / failures / errors / skips.
Display runs used `lockf -k /tmp/fluxtion-analyser-display.lock` and both
`-Djava.awt.headless=false -DargLine=-Djava.awt.headless=false`. Source and class mutations were
restored from byte copies, without `git checkout`.

| Check | Command or selection | Result | Evidence |
|---|---|---|---|
| Original false-green witness | `mvn -o -q test -Dtest=LogFindingsOnEverySurfaceFrameTest#aLoadThatThrowsPartWayDoesNotStopTheNextLogsEvidence`, with the original base test temporarily restored | **1 / 0 / 0 / 0**, exit 0, uncaught SummaryPanel NPE printed; source/classes restored byte-identically | [log](evidence/summary-panel-edt-gate-2026-10-01/original-baseline.log), [counts](evidence/summary-panel-edt-gate-2026-10-01/original-baseline-report.txt), [restoration](evidence/summary-panel-edt-gate-2026-10-01/original-baseline.json) |
| Guard without expected-fault accounting | Same single-test selector after installing the watcher, before adding `expect` | **1 / 1 / 0 / 0**, named assertion `uncaughtEdtFailure` | [failed attempt](evidence/summary-panel-edt-gate-2026-10-01/unaccounted-injected-fault.txt) |
| Corrected log-findings display suite | `mvn -o -q test -Dtest=LogFindingsOnEverySurfaceFrameTest,AsyncOpenInterleavingFrameTest,EdtExceptionWatchTest` | **14 / 0 / 0 / 0** | [report](evidence/summary-panel-edt-gate-2026-10-01/display-log-findings.txt) |
| Neighbouring asynchronous-open display suite | Same command | **12 / 0 / 0 / 0** | [report](evidence/summary-panel-edt-gate-2026-10-01/display-async-open.txt) |
| Watcher regressions | Same command; also run independently with `-Dtest=EdtExceptionWatchTest` | **4 / 0 / 0 / 0** | [report](evidence/summary-panel-edt-gate-2026-10-01/edt-watch-unit.txt) |
| Full headless suite | `mvn -o -q test` | **3088 / 0 / 0 / 218**, 407 source-mapped reports, no orphans | [per-suite counts](evidence/summary-panel-edt-gate-2026-10-01/headless-counts.json), [output](evidence/summary-panel-edt-gate-2026-10-01/headless.log) |
| Registry and anchor preflight | `python3 tools/verify_project_chart_review.py --mode preflight --output <output>` | 40 frame suites, 562 anchors | [result](evidence/summary-panel-edt-gate-2026-10-01/preflight.json) |
| Harness regressions | `python3 tools/test_project_chart_review.py` | **5 / 0 / 0 / 0** | Passed locally |
| Documentation | `mkdocs build --strict` | exit 0 | [output](evidence/summary-panel-edt-gate-2026-10-01/mkdocs.log) |

### Targeted mutation controls

Command: `python3 tools/verify_project_chart_review.py --mode mutations --engine fast --output <output>`
with one `--case` for each name below. Requested **5**, ran **5**, caught **5**. The shared baseline was
**30 / 0 / 0 / 0**. All five restored runs were green; both source and class trees were byte-identical.
No full mutation gate was run locally.

| Control | Wrong result and named failing assertion |
|---|---|
| `edt-unexpected-failure-fails-the-test` | Disable rejection of unaccounted exceptions: `EdtExceptionWatchTest#unexpectedFailureMakesTheWatchFailAndRestoresTheHandler` fails at `anUnexpectedEdtExceptionMustFailTheTest`. |
| `summary-injected-failure-must-be-observed` | Keep the panel present, removing the injected failure: the F1 frame test fails at `theInjectedEdtFailureWasObserved`. |
| `summary-injected-failure-is-accounted` | Fabricate an expected exception without consuming the captured one: the F1 frame test fails at `uncaughtEdtFailure`. |
| `m44-5-f1-new-generation-resets-outstanding` | Remove the generation reset: `LogEvidenceTest#aDroppedScanDoesNotSwallowTheNextGenerationsScan` fails at `the new generation asks for a scan of its own`. |
| `m44-5-f1-frame-next-log-is-scanned` | Same production mutation: the F1 frame test fails at `F1: the next log's evidence is scanned and rendered`, including pending scan, status and context assertions. |

The watcher tests also prove that a rejected exception type is not consumed, that accounting for one
expected fault does not hide a second unrelated fault, and that the original handler is restored after
both a clean run and an assertion failure. The cheap static check is the registry/anchor preflight;
the registered controls provide executable wrong-result witnesses in future CI runs.

Raw mutation assertions, counts and restoration flags: [JSON](evidence/summary-panel-edt-gate-2026-10-01/mutations.json),
[output](evidence/summary-panel-edt-gate-2026-10-01/mutations.log). Private machine paths in evidence are replaced
with `<worktree>`, `<home>` or `<temp>`.

Exact-head CI is recorded on the pull request after pushing. The earlier main run is diagnostic
context, not verification of this change. This work does not close the separate issue #84 review findings.

PR #105's initial CI stopped on a skipped native mouse mutation witness, while its build and display
jobs passed. The failed attempt and the acquisition correction are documented in
[native press acquisition](evidence/summary-panel-edt-gate-2026-10-01/native-acquisition/README.md).

The branch was subsequently integrated with main. Verification of that combined code, including
all 13 targeted controls and the larger headless suite, is recorded in
[integration evidence](evidence/summary-panel-edt-gate-2026-10-01/integration-c66f744d/README.md).
The subsequent main release documentation was also integrated; the
[final-tree headless and documentation checks](evidence/summary-panel-edt-gate-2026-10-01/integration-53c0386e/README.md)
passed with the two unshipped test corrections retained under Unreleased.
