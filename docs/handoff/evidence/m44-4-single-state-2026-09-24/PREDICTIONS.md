# M44.4 single-state model — predictions

Written before the trials they predict. RESULTS.md records the outcome of each, including the wrong ones.
Spec: [`spec-session-processor.md`](../../../specs/spec-session-processor.md) §13. Branch `feat/m44-single-state-session`.

## Set 1 — M44.4a (honest facts replace the observation funnel)

**Already run, so not a prediction:** the 99 tests in the `session` package, after being converted from
`LogObserved`/`GraphObserved` to `SessionFixtures` (request plus `LogOpened`, `GraphOpened`, `GraphCleared`,
`LogAppended`): 99 / 0 / 0 / 0. The tests were converted by me, in the same change, so this shows the conversion
compiles and agrees with itself. It does not show the application behaves the same.

Predictions for trials not yet run:

- **P1 — headless `mvn test`: green, 0 failures.** Risk named: `SessionGraphShapeTest`, or a GraphML-pinning test,
  reads node or handler names from the regenerated GraphML and fails on the renamed handlers. If it fails, the fix is
  to re-pin, but only after reading that the new edges are the intended ones.
- **P2 — frame tests: 66 run, 0 failures, 0 errors, 1 skip** (the known local focus skip). Most likely to fail:
  a `PairingDuringLoadFrameTest` case that reads the session audit ring (`aFollowAppendWritesNoSessionAuditRecordUntilCoverageReadsIt`),
  because every graph change now writes a fact record and `closeLog` posts `LogCleared`. That test counts records
  after appends only, so it should hold. **Confidence 60%.**
- **P3 — a reader-supplied graph now reaches the session.** Before this change, `openGraph.isOpen()` was false for it.
  A new test, a session replay of `GraphOpened(null path, READER_INFERRED)` followed by a log, will show
  `coverageClaim` REFUSED with "inferred from what ran". The witness: restore path-based `isOpen()`, and the test must fail.
- **P4 — a close made by an effect is recorded as a no-op fact, not dropped.** After `EXPLICIT_SWITCH` closes the log,
  the audit ring contains a `LogCleared` record with `noOp`. The witness: make `post` drop the fact while dispatching,
  and the test must fail.
- **P5 — a stale fact is refused.** `LogAppended` naming generation *n* after generation *n+1* is open changes
  nothing and logs `staleFact`. The witness: remove the generation check, and the test must fail.
- **P6 — `tools/verify-m68-1-coverage.py` against the rebuilt jar: 65 / 0**, unchanged, because this slice moves
  reporting and not verdicts.
- ~~**P7 — `MainFrame` net lines fall** in this slice, by at least 25 lines.~~ **Withdrawn as a prediction: it was
  already measurable when written.** `git diff --numstat` shows +40 / −41, net −1. So "at least 25" was **wrong**, and
  it is left on the record. The funnel's deleted lines were replaced by the fact reporters and their comments. The
  spec's −150 for the whole milestone rests on M44.4b/c, which delete the verdict copies.

## Set 2 — M44.4b (the snapshot; appends reported as they land; retention by kind)

Written after the code compiled and before any test in it ran. **Already measured, so not predicted:** `MainFrame` is
+52 / −58, net −6. The frame's duplicate scorer `pairingAgainst`, `republishPairingAfterAppend`,
`refreshSessionIfLogGrew`, `sessionNotedTotal` and the `invokeAndWait` are all gone. No UI class calls
`GraphPairing.of`, `.withScope(` or `.rescoped(` any more (grep: 0).

**Scope moved from the spec's table, stated before the trials:** M44.4d's retention by kind lands here. Reporting
every append to the session would otherwise break O-i's property, so the two cannot ship apart. Two things are
**not built**:

- off-EDT `post` marshalling, because nothing posts off the EDT now that coverage reads the snapshot;
- the DEBUG-level half of D-S13.5, because with tracing on it removes keys and not records, and retention is what
  O-i needed.

- **P8 — `SessionSnapshotTest`, 6 cases, all green on first run.** Confidence 80%. The likeliest failure is
  `reScopesNeverEvictTransitions` miscounting `droppedRescopes`, if `openLog` or `graph` in the setup also emits
  something the sink classifies as a re-scope.
- **P9 — each witness turns its named test red:**
  - W6: `publishSnapshot` not called, turns `theSnapshotFollowsEachOperation` red;
  - W7: no `equals` short-circuit, turns `listenersHearChangesOnly` red;
  - W8: `revision` not incremented, turns `aDifferentArtefactIsADifferentPair` red;
  - W9: re-scopes routed to the transition ring, turns `reScopesNeverEvictTransitions` red;
  - W10: `GraphPairing.of(` restored in `MainFrame`, turns `theFrameRendersAndDoesNotCompute` red.
- **P10 — headless `mvn test`: 1,947 run** (1,941 + 6), 0 failures, 0 errors, 65 skipped.
- **P11 — frame tests 66 / 0 / 0 / 1 skip**, including the reworded O-i test
  (`aFollowAppendEvictsNoTransitionRecordAndTheClaimIsCurrent`). Confidence 65%. Named risk: `judgeOpenedGraph` now
  reads the session's verdict, and reopening the *same* graph file no longer produces a new pairing object. Qualifications
  from an earlier coverage comparison therefore survive a same-graph reopen, where before they were dropped. If a frame
  test asserts they drop, it fails, and that is a finding about which behaviour is right, not a test to relax.
- **P12 — `verify-m68-1-coverage.py` on the rebuilt jar: 65 / 0.** Confidence 70%, for the same named risk.

## Set 3 — M44.4c (the qualifications move into the session; the frame keeps no verdict)

Written after the code compiled and before any test in it ran. This time the code was stashed and the predictions
commit was gated on a tree without it (set 2's stated remedy).

**Already measured, so not predicted:**

- `MainFrame` is +55 / −94 in this slice, net −39. Across M44.4 so far (since `63518e82`) it is +112 / −158, net −46.
  The spec predicted −150 or more for the milestone; that is on course to be wrong, and set 4 will score it.
- The frame's `lastPairing`, `qualifications`, `qualifiedPairing`, `currentQualifications()` and `publishedSnapshot`
  are gone, and so is `setBusy`'s verdict logic. "Pending" is now the session gate's `inFlightWhat()`, carried on the
  snapshot.

**A limit stated before the trials:** the two parity frame tests' frame-vs-session comparison is now equal by
construction, because the frame renders the session's verdict. What they still check independently is session
against discovery, and the panel status line, as a rendering witness.

**Fixed as a side effect, and claimed only once a test shows it:** the scan-during-open race. The coverage verb
scanned off the EDT, then qualified whichever pairing was published when the scan finished. The comparison now
carries the pair identity captured before the scan, and the session refuses a mismatch.

- **P13 — `PairingQualifierTest`, 6 cases, green on first run.** Confidence 70%. Likeliest failure: a `toMap` key name
  (`stale`, `filterStale`, `everyObservedIdDeclared`) that I have assumed rather than read for the case exercised.
- **P14 — witnesses:**
  - W11: `onPairChanged` always clears, turns `aReScopeKeepsItStale` red;
  - W12: no generation/revision check, turns `aStaleComparisonIsRefused` red;
  - W13: `pending` always false, turns `aPendingOpenPublishesNoVerdict` red;
  - W14: a `GraphPairing` field restored in `MainFrame`, turns `theFrameRendersAndDoesNotCompute` red.
- **P15 — headless 1,953 run** (1,947 + 6), 0 failures, 0 errors, 65 skipped.
- **P16 — frame 66 / 0 / 0 / 1 skip.** Confidence 55%. Named risk: the note and `context` now read the session's
  `total` rather than `store.size()`, and "pending" from the gate rather than `loadInFlight`. A frame test that reads
  `context` between a store change and its fact would see the session lag by one step.
- **P17 — `verify-m68-1-coverage.py` 65 / 0 on the rebuilt jar.** Confidence 60%, for the same risk.
