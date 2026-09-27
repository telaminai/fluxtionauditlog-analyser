# PR #33: canonical exchange paths — implementation response

This is the implementer's report, not an independent approval. It answers review
[5851378076](https://github.com/telaminai/fluxtionauditlog-analyser/pull/33#issuecomment-5851378076)
on `02327844`. The owner authorised merging after the correction and gates pass.

## Changes

- **R1:** `ExportGuard.java:63–101` now makes containment decisions only after resolving both
  locations. Absolute requests through a directory alias, including a linked project root,
  are accepted when they resolve inside the exchange directory. Machine-tier aliases work too.
  The chooser grant is unchanged; canonical targets, existing-file refusal and outside-link
  refusal remain. This is a resolution-time guard, not a race-free sandbox.
- `ExportGuard.java:116–122` explains when a symbolic link does not resolve. Both a dangling
  file link and a dangling directory link with missing descendants are refused.
- `ReportRecoverableDeleteFrameTest` requires positive button width, checks that invalid
  restore values leave reports and the bin unchanged, and successfully writes an inside
  screenshot before attempting the nested-link escape in the same fixture.
- The assistant guide explicitly says symbolic link and retains the concurrency limitation.
  The existing Unreleased exchange-path entry now states that equivalent alias paths work
  and unresolved symbolic links produce an explanation. Bin policy and restore semantics
  are unchanged.

## Checks actually run (macOS, JDK 21)

Counts below are **total / failures / errors / skips**.

Before changing production code, the expanded `ExportGuardTest` on `02327844` reported
**18 / 4 / 0 / 0**. The three alias tests failed on acceptance/canonical-target assertions;
`danglingFileAndDirectoryLinksAreRefused` failed on the explanation, not the refusal.
After the fix, all 18 passed in the package gate. The portable directory-alias, linked-project
and machine-tier cases each exercise reads and writes. The directory-alias test additionally
exercised the system temporary-directory alias on this macOS machine; that subcase runs only
where that alias exists. Its pre-fix test stopped at the first portable alias assertion.

| Check | Result |
| --- | --- |
| `mvn -q clean package` | **2506 / 0 / 0 / 124**, 334 source-mapped reports, no orphans |
| `ReportRecoverableDeleteFrameTest`, direct headless=false and argLine=false | **5 / 0 / 0 / 0**, real display |
| Verifier preflight | 24 frame suites, 207 anchors |
| `python3 tools/test_project_chart_review.py` | 5 tests, green |
| `mkdocs build --strict` | Green |
| `git diff --check` | Clean |
| Rule-one tracked-file and added-line sweeps | Clean |

The initial package attempt inside the sandbox was **2506 / 0 / 29 / 124**: local-socket
creation was denied. Its output and XML were retained locally, then the unrestricted package
run above passed. The display test passed on its first attempt.

Six targeted fast-engine controls ran in **30.5 seconds**. Each had a green baseline,
**1 / 1 / 0 / 0** at its named assertion under mutation, byte-identical source and class
restoration, and a green re-run:

| Control | Named assertion's behaviour |
| --- | --- |
| `p33-absolute-alias-paths` (new) | An absolute alias read/write must be accepted |
| `p33-nested-link-containment` | The screenshot must refuse the outside link |
| `p33-canonical-exchange-location` | The directory returned is the checked location |
| `p33-canonical-export-target` | The output returned is the checked target |
| `p33-visible-report-actions` | Every report action is fully visible |
| `p33-restore-mixed-operations` | Restore refuses the additional operation |

The new control reinstates the lexical refusal ahead of canonical containment. An independent
SHA-256 comparison of all tracked source and tool files also confirmed restoration. No full
mutation gate was run locally; CI runs the complete set. No key, provider, client session,
participant project or recovery store was used.

## Merge conditions and owner note

The new head's CI results will be read directly and linked in the PR comment before merging,
including build, zero-skip ui-frame and the complete mutation collector count. This report does
not claim a pending CI run has passed. PR #24 merge `29a9ece4` carries an unwanted author address;
history is unchanged. This fix and the authorised local `--no-ff` merge use the personal identity.
