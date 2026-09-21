# Tool agreement implementation — 2026-09-21

Branch: `feat/tool-agreement`, based on `08954d43`. Work in progress; no release claimed.
The owner kept the existing evidence-correctness work at P1 for this pass.

## TA-1 — completed

Frozen prediction (tracker, committed before implementation): the committed 23-node graph paired with
constructed logged ids `rootNode`, `riskCheck`, `output` will match 3/3 through UI, discovery and session;
authored-only pairing will fail the equality regression; the foreign-graph control will remain rejected.
**Held.** Shared declared ids now enter discovery, frame pairing and the session's graph observation.
Discovery's authored-node count is retained as a view count, independent of pairing.

Fixtures: committed `MarketProcessor.src-round3.graphml`; the three-logger audit record is a
**constructed regression case**, not a recovered session log.

Tests: `GraphPairingTest.frameworkLoggerIsDeclaredInTheCommittedGraph` and
`PairingDuringLoadFrameTest.committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession`.
The latter drives the real frame and compares its verdict, discovery and the session processor.
Existing `GraphPairingTest` / `GraphmlDiscoveryTest` foreign-graph controls remain green (reject mismatch).

Mutation: returning `Scaffolding.authoredNodes(topology)` from the common helper produced two failures:
`expected: <23> but was: <8>` and `session includes the framework logger ==> expected: <3> but was: <2>`.
[Saved witness](evidence/tool-agreement-2026-09-21/ta1-mutation.json). The collector initially expected a
bare test name; Surefire includes `(Path)`. The same saved results were checked after normalizing that
suffix; the mutation was not rerun to obtain a different result.

Validation: restored `mvn -q test` passed; restored display test passed with 1 test, 0 failures/errors/skips:

```sh
mvn -q '-Dtest=PairingDuringLoadFrameTest#committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession' -Djava.awt.headless=false '-DargLine=-Djava.awt.headless=false' test
```

Counts: analyser **13 → 11 open** (D1/D2 closed); upstream **8 → 8 open**.
No runtime behavior was changed or newly asserted. No client sessions ran.

## TA-2 — completed

Frozen before implementation in the tracker: the committed copies, renamed to their original common
basename in separate directories, will disagree without a log; a round-3 logger will rank the 23-node
copy first and name `eodReportPublisher` missing from the 20-node copy; identical copies agree; opening
either copy announces the disagreement without refusing. Disabling the comparison must fail.
**Held.** Counts: analyser **11 → 10 open** (D3b closed), upstream **8 → 8 open** (D3a still open).

Tests in `GraphmlDiscoveryTest`:
- `committedCopiesDisagreeWithoutALogAndRankByLoggedEvidence`: committed fixtures, exact fingerprints,
  counts, modification times, missing logger and ranking; no recovered log is claimed.
- `identicalCommittedCopiesAgreeAndMissingMetadataIsUnknown`: identical-copy negative control;
  additionally a labelled constructed metadata-absence variant stays unknown.
- `declaredProcessorGroupsRenamedCopiesAndNodeSetsAreCompared`: constructed metadata variants of the
  committed graph (the packet contains no processor-class key); equal counts/fingerprints cannot mask
  a changed node id. Only declared metadata is used; no class execution or hierarchy inference.

`PairingDuringLoadFrameTest.openingCommittedCopiesAnnouncesDisagreementWithoutRefusing` opens each
committed copy with no log, awaits the background comparison, checks context and the visible status,
and verifies close clears it. Every entry point uses the topology-load callback. A result is discarded
if a different topology has since loaded. The immediate open echo says pending; it never claims a
comparison already finished. Scan scope is configured roots plus the opened directory; it is bounded,
reports incomplete scans and does not choose a correct copy. Reopen/discover refreshes file observations.

Mutation: disable the disagreement verdict. These three tests failed with
`expected: <disagree> but was: <agree>`: the ranked fixture test, node-set/processor test and real-frame
open test. [Saved witness](evidence/tool-agreement-2026-09-21/ta2-mutation.json).
The first sandboxed attempt aborted in macOS windowing and produced no valid witness; it was repeated
outside the sandbox. A test-author error using `close` instead of `open {close}` was corrected before
acceptance. Neither error is presented as a product failure or mutation witness.

Restored validation: `mvn -q test`; all `PairingDuringLoadFrameTest` cases with display enabled;
`mkdocs build --strict`. All pass. No new LLM sessions, no producer fix, no release or main push.
