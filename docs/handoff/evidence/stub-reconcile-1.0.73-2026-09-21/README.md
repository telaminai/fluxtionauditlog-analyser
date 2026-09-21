# Preserved reviewer evidence — starter 1.0.73 reconciliation

Start with [the reviewer report](reviewer-report.md), copied verbatim. This packet preserves the
public pristine download, before/after AlertNode snapshots, setup/regeneration/compiler logs,
and final XML/ownership record. The reviewer ran the experiment; the documentation author read
and preserved these files, but did not independently rerun generation or compilation.

[manifest.json](manifest.json) records SHA-256 and byte counts. All listed source files were
copied byte-for-byte. The public ZIP entries and copied text passed the repository's public-data
sweep. The complete private workspace remains outside this repository; its cached classpath,
local project configuration and other extracted files are not published here. The original
report's `project/` description refers to that full workspace. This copy contains only the
final XML and ownership record beneath `project/`; reconstruct the other files from the ZIP.

## What this supports

- The saved generated AlertNode lacks an EventLogNode superclass.
- The final AlertNode has that superclass, the implemented audit call and the later NewsEvent
  handler. The reviewer reports that successive regeneration accepted these changes.
- The initial and final source visibly contain repeated comment-contract text interleaved
  between modifiers/annotations: proposed tool-agreement D21, owned by the starter.

The logs do not include recorded exit codes or a javap transcript. The empty javac.log alone
cannot establish successful compilation; that outcome is explicitly the reviewer's report.
There is no key-gated processor generation or runtime audit record for this experiment.
Preserve that limit when citing the workaround.

The original snapshots retain whitespace, including trailing spaces, as part of the formatting
evidence. They have not been cleaned up for the documentation PR.
