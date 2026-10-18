# Integration review F4 — a sample alias widened the hosted allow-root

Owner: `fix/owner-test-small` (FIX-6); introducing commit `62e1971a` within that branch.
Reviewed baseline: `ddccd5c5`. Before-fix regressions executed on `c526fcc1` production code, JDK 21/macOS.

Sequence: under a temporary DEMO home, make the analyser's sample directory a symbolic link to that home, then
make an ordinary project below the home active. `LocalAgentFolders.sampleRoot` resolved the alias to the home and
mistook every project below it for the sample. In a Full session working in scratch, `McpTools.hostedCallKind`
therefore returned PRE_ALLOWED for a different DEMO log outside the project. Neither a permission prompt nor a
single-call grant was required by that classifier. This requires a pre-existing filesystem alias; this review does
not claim that an incoming log or a remote client can create it. No credential or participant file was accessed.

Maintained checks in `InProjectOpenTest`:

- `aSymlinkedSampleDoesNotWidenTheApprovalBoundary`: before fix, **expected CHANGES, got PRE_ALLOWED** at
  `an ordinary project must not inherit the symlinked sample's home-wide pre-allow`.
- `theSampleAnchorCannotMoveButTheHomeMayBeAnAlias`: before fix, expected no sample root but got the directory
  reached through an aliased analyser parent. The positive control preserves ordinary canonicalisation of home.

Focused before-fix Maven result: **2 / 2 / 0 / 0**. A separate small classifier probe reproduced PRE_ALLOWED too.
After fix, full `mvn -o test`: **4189 / 0 / 0 / 327**. Existing genuine-sample, Safe, outside-file, linked-file and
project-switch controls also pass. Counts are total / failures / errors / skips. The affected frame results are
recorded in the final review.

Fix: resolve the home first, then require the sample's canonical directory to equal its fixed expected anchor.
Missing/unresolvable/moved anchors do not widen the allow-root. This does not remove the genuine-sample exception,
change external client API semantics, or claim to eliminate filesystem time-of-check/time-of-use races.
No node wiring or generated source changed.
