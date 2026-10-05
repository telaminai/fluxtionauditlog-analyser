# PR #105 — targeted review and main integration, 2026-10-05

Verdict: mergeable subject to complete CI at the pushed integration head. No new required code correction found.
Reviewed `be2d337e` and integrated main `2a54894f` in an isolated worktree. The only textual conflict was
CHANGELOG.md; both sides' Unreleased entries are preserved. Application code, generated session code,
workflow, fast runner and collector equal main. This review does not claim an independent author history:
it checks the existing PR against current main, with new executed evidence.

## Findings and dispositions

- READ/RAN — EDT accounting: `EdtExceptionWatch.java:27` consumes one fault only after checking its type
  and stack location; `close()` restores the prior handler before asserting no unaccounted fault remains.
  `LogFindingsOnEverySurfaceFrameTest.java:566` checks the injected SummaryPanel fault and restores the
  panel in finally. The recovery check remains. The three related controls failed at their intended
  accounting assertions, not at an unrelated asynchronous error. Accepted.
- READ/RAN — native acquisition: `TableDragCancellationFrameTest.java:52` observes the actual delivered
  press and retries only acquisition, before cancellation assertions. Exhausted input is an error.
  Held-button cancellation, next-drag autoscroll, column adjustment and non-modal behaviour remain checked.
  All eight mouse controls failed at their intended assertions. Accepted; no skip was scored as a pass.
- READ/RAN — bundle fixture: `BundleProvenanceFrameTest.java:690` observes two model refreshes for the
  newly installed store (loaded render, then inference); adding a source root has its own two-refresh
  boundary. Removing each listener on the EDT waits for the callback to finish. The real processor-selection
  action is used. Manifest, adoption and selection controls failed at their named assertions. Accepted for
  its stated settled-inference scope.
- READ — remaining application limitation: `MainFrame.java:6304` applies an inference result without
  protecting a newer manual choice made while inference was pending. This predates the PR; the PR body
  discloses it and this fixture deliberately does not prove that case. Retain issue #112 until that race is
  independently resolved; this review does not turn a fixture correction into a claim of an application fix.

## RAN on the merged working tree, JDK 21 / macOS

Counts are total / failures / errors / skips. [Machine-readable receipt](evidence/pr105-targeted-2026-10-05/results.json).

| Check | Result |
| --- | --- |
| `mvn -o -q clean test` | 3184 / 0 / 0 / 249; 420 reports, no orphans |
| `TableDragCancellationFrameTest` on display | 4 / 0 / 0 / 0 |
| `LogFindingsOnEverySurfaceFrameTest` on display | 14 / 0 / 0 / 0 |
| `BundleProvenanceFrameTest` on display | 18 / 0 / 0 / 0 |
| Targeted fast controls | 15 requested, 15 caught; 180.0 seconds |
| `tools/test_project_chart_review.py` | 5 / 0 / 0 / 0 |
| `tools/test_mutation_iteration.py` | 51 / 0 / 0 / 0 (includes collector tests) |
| Preflight | 43 frame suites, 634 anchors |
| Strict MkDocs and whitespace | Clean |

Display suites and controls ran sequentially under the shared display lock. Requested/caught names were
compared; every mutation failed at its intended assertion, restored source and classes byte-identically,
and passed its restored witness. The test-source mutations also work through main's new runner. No full
local mutation gate was run. No application or test correction was needed in this round.

Not re-verified: all other display classes locally, a deterministic runtime reproduction/fix of the pending
inference race, or every historical attempt in the author's evidence. Full exact-head CI remains the merge
condition; earlier green PR runs are not substituted. No key, provider or participant project was used.
