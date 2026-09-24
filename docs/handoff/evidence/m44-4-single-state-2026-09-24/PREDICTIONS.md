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

## Set 4 — M44.4 acceptance journeys, and M68.4's combined open

**Already run, so not predicted:** `SessionLifecycleJourneysTest`, 5 journeys re-expressing the M68.1 frame tests'
verdict assertions headless: 5 / 0 / 0 / 0. They were run as soon as they were written, and predictions were not
written first. They re-express behaviour the frame tests already establish, so there was nothing new to predict.
What makes them count is that each fails when the behaviour it re-expresses is broken:

- W16: `pending` always false, turned `aGraphOpenedDuringTheNextLoadIsJudgedAgainstTheNewLog` red.
- W17: the unscoped verdict (round 2's R2) turned `openOrderDoesNotChangeTheVerdict` red, and two others.
- W18: `sizeStale` always false turned `aFollowAppendMakesTheWholeLogComparisonStale` red.
- W19: the qualifier ignoring a closed log turned `closingTheLogRetiresEverythingAboutIt` red.

Every restore was byte-identical.

**M68.4, the combined open**, written before any run of `CombinedOpenTest` and before the fix:

- **P18 — seen red.** On the current code, `aGraphOpenedForTheLoggingLogIsKept` FAILS at its first assertion: one
  `CloseGraphEffect`, the graph the request itself opened. Both controls PASS, because they describe today's
  behaviour for residue and reader graphs.
- **P19 — the fix is one decision in `LogArrival`.** It records the graph revision when the log is requested, and on
  arrival keeps an `OPENED` graph whose revision moved since then, announcing the mismatch and recording
  `graphOpenedForThisLog`. After regenerating, all three cases pass.
- **P20 — witness W20:** keeping every graph whose revision moved, regardless of source, turns `aReaderGraphIsNotIntent`
  red. **W21:** dropping the revision comparison, so every `OPENED` graph is kept, turns
  `aGraphFromBeforeTheRequestIsStillResidue` red.
- **P21 — nothing else changes.** Headless 1,962 (1,954 + 5 journeys + 3), 0 failures. Frame 66 / 0 / 0 / 1. The
  M68.1 verifier stays at 65 / 0. Named risk: a frame test that opens a mismatching graph DURING a load and expects it
  closed. I know of none, and `freshWindow_socketGraphThenMismatchingSocketLog…` opens the graph first.
- **P22 — end to end.** A new verifier check, a half-foreign log opened combined with the committed graph, shows
  the graph still loaded on the final state (after the arrival rule, past review O6's delay), with `applies` false. It
  fails on a jar built from `8893cb08` and passes on the fixed jar.

## Set 5 — M68.5, identity under Follow (heap store)

Written after the code compiled and before any of its tests ran. Gated on a tree without the code.

**Scope, stated before the trials.** Follow runs only on the heap store, which re-reads the whole file each poll,
so the full-prefix comparison D-E6 requires costs nothing extra there. The mapped store's half of acceptance 7
(identity at the next read) is **not** in this set. **A deliberate departure:** D-E6 calls "same key, same length,
changed modification time, matching prefix" unverified, because a prefix SAMPLE cannot prove identity; the heap store
compares every byte, so identical bytes are UNCHANGED, and the reason says the comparison was complete. **A design
choice:** the session hears APPEND and UNCHANGED as one state, `VERIFIED`, because they alternate on every
append-then-idle pair of polls, and reporting them apart would be the non-change churn M44.4 hit twice.

- **P23 — `FollowIdentityTest`, 9 cases, green on first run.** Confidence 85%. It is pure.
- **P24 — `HeapLogStoreFollowIdentityTest`, 6 cases, green on first run.** Confidence 60%. Likeliest failure: `touched`,
  if setting the modification time lands inside the read's before/after window and reads as "changed during read"
  (UNVERIFIED rather than UNCHANGED); or `differentFileSamePath`, if the atomic move keeps the inode on this
  filesystem.
- **P25 — `LogIdentityTest`, 4 cases, green on first run.** Confidence 75%.
- **P26 — witnesses:**
  - W23: `classify` without the `startsWith` check turns `sameLengthRewrite` (pure) red, and the store's
    `sameLengthRewrite` too;
  - W24: `OpenLog.onLogOpened` without the reopened-after-replacement rule turns `aReopenAfterReplacementSaysWhy` red;
  - W25: the store indexing on UNVERIFIED-moving reads turns `missingOrMoving` red. That one is pure, and it guards
    `mayIndexGrowth`.
- **P27 — headless 1,982 run** (1,963 + 9 + 6 + 4), 0 failures, 0 errors, 65 skipped (or 66 if `differentFileSamePath`
  assumes out).
- **P28 — frame 66 / 0 / 0 / 1.** Confidence 60%. Named risk: an existing Follow frame test that rewrites the file
  in place, or whose Follow poll now reads a "changed during read" window and pauses indexing for a poll, making a
  `wait_records`-style loop time out.
- **P29 — end to end.** The verifier's new scenario 12 fails on a jar built from `f2e25e80` (no `log.identity` in
  context) and passes on the fix. The other 67 checks are unchanged.

## Set 6 — M68.5, the file's identity at the next request (both stores)

Written after the code compiled and before any of its tests ran. Gated on a tree without the code.

**Scope, stated before the trials.** The mapped store does not follow, so acceptance 7's "while following, on both
stores" cannot apply to it as written. What D-E6 does ask of it is the next REQUEST that reads the file. That is
checked at the dispatcher, for record-reading verbs, and at `context`, and for a person on window focus. **Limit,
stated:** an in-place rewrite that restores size, modification time and key is invisible to this metadata check.
Seeing it means re-reading every byte, the cost D-E6 names. The heap store's Follow pays it; a request-time check
does not. **Not covered:** the human table view is announced on the status line, but not suspended.

- **P30 — `ReadThroughIdentityTest`, 6 cases, green on first run.** Confidence 85%; it is pure.
- **P31 — `MappedLogStoreReadIdentityTest`, 3 cases, green on first run.** Confidence 60%. Likeliest failure:
  `inPlaceRewrite`'s precondition, if `Files.writeString` truncates and rewrites through a new inode on this
  filesystem rather than in place. `atomicReplace`'s claim that the old channel still reads the opened file is POSIX
  behaviour, and I have not seen it measured on APFS.
- **P32 — `ActionDispatcherReadIdentityTest`, 3 cases, green on first run.** Confidence 75%. Risk: the stub
  dispatcher's snapshot throwing for `context`, if `context` touches the snapshot.
- **P33 — witnesses:**
  - W26: the dispatcher's `suspendsReads()` refusal removed, turns `suspendedRefuses` red;
  - W27: `inMemory` ignored in `classify` turns `inMemoryIsRetained` red;
  - W28: the mapped store classifying with `inMemory = true` turns `inPlaceRewrite` red.
- **P34 — headless 1,994 run** (1,982 + 12), 0 failures, 0 errors; 65 skipped, or 66 if `atomicReplace` assumes out.
- **P35 — frame 66 / 0 / 0 / 1.** Confidence 55%. Named risk: the window-focus listener firing inside a frame test,
  after a test rewrote a heap-loaded file, and changing the status text that test then asserts.
- **P36 — the verifier stays at 69 / 0.** Its logs are heap-loaded and unchanged while read, apart from scenario 12,
  which is Follow.
