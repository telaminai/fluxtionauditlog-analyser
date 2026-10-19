# Integration v3 review evidence

Reviewed candidate: `adc1e57929e31594b6214758dbb39cd36dd2549f`; scope `ddccd5c5..adc1e579`.
See [the report](../../review_integration_v3_2026_10_18_codex.md) for findings and verification limits.
All data is DEMO data. Counts below are total / failures / errors / skips.

## Before-fix witnesses

| Witness | Result before correction | Wrong result |
|---|---|---|
| `SourceTextTest.aLargeContextCannotWrapTheWindowIntoAnEmptySuccess` | 1 / 1 / 0 / 0 | Requested window returns `to=0`, expected 200 |
| `IntegrationCReportAdmissionProbeTest` (archived alongside this file) | 3 / 3 / 0 / 0 | Wrong project receives report; PDF follows exchange outside project; retitle is overwritten |
| `SampleProjectSpotlightFrameTest.theSampleJourneyWaitsForItsEarlierPaneRead` without its barrier | 1 / 1 / 0 / 0 | The exact superseded refusal, rather than the requested visible spotlight |

The archived Java probe targets the candidate's old internal `ReportState` API. To reproduce it, use a separate
detached checkout of `adc1e579`, copy that Java file into the matching test UI package, and run
`mvn -o -Dtest=IntegrationCReportAdmissionProbeTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false test`
under the shared display lock. It deliberately fails three assertions there; it is not a passing regression suite.
It represents an already admitted request and supplies prior report ownership in its wrapper. It exercises the real
classifier, dispatcher, frame, project and exporter, but does not claim a complete hosted HTTP/CLI handshake.

The maintained replacement, `ReportOwnershipFrameTest`, uses a real generated session with simulated CLI facts and
actual frame project/report effects. It covers those sequences and valid writes, switching away and back, an appeared
output file, substituted links, and receipt delivery after a human edit. It is registered in both CI frame lists.

Raw before/after logs, original failed attempts, full headless XML reports and the inspected DEMO image are retained
in the clone's ignored review evidence store. They are excluded from the public tree because machine paths appear
in ordinary Maven logs and the screenshot's source label. The sanitised facts are stated here explicitly.

## Targeted controls

Requested names:

- `integration-c-source-window`
- `integration-c-report-project`
- `integration-c-report-output`
- `integration-c-report-full-only`
- `integration-c-report-revision`
- `integration-c-report-receipt`
- `integration-c-sample-reader-barrier`

Run with `tools/verify_project_chart_review.py --mode mutations --engine fast`, one `--case NAME` per entry,
and a unique `--output` path, under the shared display lock. The gate is fail-fast: compare the completed names with
this requested set, require named assertion failures, source/class byte restoration, and restored green runs.
Executed result: **REQUESTED = CAUGHT, all seven names**. Every mutation compiled, failed its named test assertion
with **1 / 1 / 0 / 0**, restored source and class bytes identically, then passed with **1 / 0 / 0 / 0**.
There were no skips, crashes, compiler-error witnesses or incomplete controls. Total targeted gate time: 26.7 s.

| Control | Named assertion witness |
|---|---|
| `integration-c-source-window` | `SourceTextTest.aLargeContextCannotWrapTheWindowIntoAnEmptySuccess` |
| `integration-c-report-project` | `ReportOwnershipFrameTest.anAdmittedReportCannotMoveToAnotherProject` |
| `integration-c-report-output` | `ReportAdmissionGuardTest.theWriteRunsOnlyAsJudged` (changed destination) |
| `integration-c-report-full-only` | `ReportAdmissionGuardTest.theWriteRunsOnlyAsJudged` (Safe mode) |
| `integration-c-report-revision` | `ReportOwnershipFrameTest.anExternalRetitleRevokesAPreviouslyAdmittedOwnUpdate` |
| `integration-c-report-receipt` | `HostedGrantServerTest.theServerRecordsTheReceiptRatherThanAnInterveningEdit` |
| `integration-c-sample-reader-barrier` | `SampleProjectSpotlightFrameTest.theSampleJourneyWaitsForItsEarlierPaneRead` |

Source SHA-256 values before and after each applicable control:

| Source | SHA-256 (identical after restoration) |
|---|---|
| SourceText.java | `94bc44c8cccf5d0a81be59378d8f9b0324d9c2ff0f85ec4228c21be10825cc31` |
| LocalAgentPermission.java | `d88391fdf14d17a71aa6ab93275b7cef864ee832a3a41ac1f6ebab33ee1dc722` |
| ActionServer.java | `ce0b1ad80fbde9549b7e1e67927344a9e9cd6d8ca02c65e8689240b8ac80f55f` |
| SampleProjectSpotlightFrameTest.java | `3ec48f00e0e0a0cfd873430290604fa1d8f9a5ea57a0d4a3e0b40a139feaa03d` |
