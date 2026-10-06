# Targeted correction results

RAN on macOS, Corretto JDK 21.0.8. Counts are total / failures / errors / skips.
The original four PR commits were rebased onto main `3d7767bd`, becoming
`8a21a8b5..156d2dd7`. Only CHANGELOG required an additive conflict resolution.

## Trials

- Before the import fixes, the strengthened `BundleImportOffTheEdtFrameTest` was **4 / 3 / 0 / 0**.
  `cancelledBorrowMustNotApply`, `newProjectMustNotReceiveTheOldBorrow`, and
  `returningToAProjectMustNotReviveTheBorrow` each observed hidden columns changed to `DEMO-borrowed`.
- After correction, `BundleImportOffTheEdtFrameTest`: **8 / 0 / 0 / 0**.
- `Issue84BundleFrameTest`: **15 / 0 / 0 / 0**.
- `BundleProvenanceFrameTest`: **18 / 0 / 0 / 0**.
- All three display suites ran separately, serially, under the shared display lock:
  `mvn -o -q test -Dtest=CLASS -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false`.
- `mvn -o -q clean test`: **3201 / 0 / 0 / 257**, **422 reports, 0 orphans**.
  Of these, `EvidenceBundleTest` is **15 / 0 / 0 / 0** and `WorkingCopyOwnershipTest` **5 / 0 / 0 / 0**.
  There are **13 added correction tests** (seven frame, six headless).
- `python3 tools/verify_project_chart_review.py --mode mutations --engine fast --case NAME`
  for the 14 names in `controls-summary.json`: **14 requested, 14 caught**, each by a named assertion;
  source and compiled classes restored byte-identically, each restored run green. **61.8 seconds**.
  The receipt records hashes checked against the final source bytes. The full local gate was not run.
- `python3 tools/verify_project_chart_review.py --mode preflight`: **44 frame suites, 647 anchors**.
- `python3 tools/test_project_chart_review.py`: **5 / 0 / 0 / 0**.
- `mkdocs build --strict`: exit 0 (dependency `codecs.open` deprecation notice).
- `git diff --check`: exit 0. Rule-one tracked-file and addition sweep: no matches outside its two exemptions.

## Attempts and prediction scoring

1–4: confirmed by lease, cross-process, per-window, pending-extraction and direct-profile-open tests.
5: confirmed; the old cleanup tests treated unmarked directories as disposable. Their fixtures now
create managed markers, and their home is isolated before cleanup runs.
6: final result confirmed, but the first neighbouring frame run was **15 / 0 / 1 / 0**.
An initial implementation immediately reaped released copies. That removed the failed-open fixture
before the provenance test could replace its profile. Immediate reaping was removed: release and deletion
are separate. Cleanup happens on a later bundle open or on the explicit control, as documented.
The provenance assertion was not weakened. Final neighbouring results are listed above.

Environmental attempts are retained here rather than counted as passing:
- One sandboxed display JVM aborted with exit 134 before a fresh report existed. A stale XML file
  still said six tests; that was not evidence for the aborted run. The display-access retry ran successfully.
- One sandboxed full suite: **3201 / 0 / 36 / 257**, 422 reports, no orphans. Local-server fixtures were
  denied loopback sockets. The unsandboxed retry produced the full green result above.
- The first control-run wrapper used the wrong module constant and exited before running any control.
  The corrected wrapper selected `CONTROLS`, compared requested versus caught names, and ran all 14.

No native screenshots, full registered display run, full local mutation gate, Windows/network-filesystem
trial, compiler regeneration, hosted provider, key or participant project was used. CI is reported on the PR
at the published head, separately from these local results.
