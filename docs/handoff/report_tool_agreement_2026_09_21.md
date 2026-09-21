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
