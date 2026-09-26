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

## Set 7 — M68.5, acceptance 8's diagnostic (a pointer that fails names the root tried)

Written after the code compiled and before any of its tests ran. Gated on a tree without the code.

**Found while reading, before any trial:** `Runbooks.resolve` returned null both with no project root and for a path
that leaves the root. `context` then put neither `resolved` nor `exists`, so the Project panel showed such a pointer
with **no warning at all**. That is more than the named gap, a vague "not found under the project root". Both are in
scope. **Scope:** the runbook and vocabulary pointers, the two the panel diagnoses. Environment log directories and
report destinations resolve through other paths and are not covered by this set.

- **P37 — `RunbooksResolutionTest`, 4 cases, green on first run.** Confidence 80%. Risk: a `@TempDir` path on macOS
  under `/var` → `/private/var` symlinks, if `toAbsolutePath().normalize()` and the test's expectation disagree on the
  prefix. Neither side resolves symlinks, so I expect agreement.
- **P38 — `ProjectModelTest.aFailingPointerNamesTheRootItTried` green, and the existing runbook and vocabulary row
  tests unchanged and green**, because a context without `problem` keeps the old sentence.
- **P39 — `ProjectPanelIsRevealOnlyTest` stays green** with `runbooks.problem` and `vocabulary.problem` added to
  `KEYS_READ`, because `MainFrame` puts both keys literally. Confidence 70%; I have not read how that test matches a
  key.
- **P40 — witnesses:**
  - W29: `ProjectModel` ignoring `problem` turns `aFailingPointerNamesTheRootItTried` red;
  - W30: `resolution` naming no root in the not-found problem turns `notFoundNamesTheRoot` red.
- **P41 — headless 1,999** (1,994 + 5), 0 failures. **Frame 66 / 0 / 0 / 1.** **Verifier 69 / 0.**

## Set 8 — M68.2, rendered evidence is checked, not assumed (D-E8)

Written after the code compiled and before any of its tests ran. Gated on a tree without the code.

**What the packet's PDF shows, read before any code** (`spring-g14-recovery-2026-09-24/subject-report-operator-export.pdf`):

- The "Trend" picture is the whole chart TAB, with its style dropdown, "Edit series" and legend, painted about
  190 px wide. Inside it the plot is a sliver saying "No data under the current fi…".
- The PDF has no topology section and no statement about one.

Three defects follow:

1. Chart sections were captured with `paintOf(panel)` at the tab's live size. `ChartPanel.toImage()` also painted at
   the component's own size, into a larger image.
2. A plot starved of room printed the FILTER sentence ("No data under the current filter").
3. The renderer's CHART/TOPOLOGY case printed only a picture. So the topology section's "recorded gap" text,
   which `renderReportPdf` does build, has never reached a page, and a chart with no picture vanished too.

**Not built:** an offscreen render of a named FOCUS. The section now states its gap on the page; it does not draw
the focus. **Not reproduced:** the packet's original log. It is not in the public packet; its condition, one
collapsed record and therefore one point, is constructed.

- **P42 — `ChartExportRenderTest`, 4 cases, green on first run.** Confidence 55%. Risks: `setSize`/`doLayout` on a
  never-displayed `ChartPanel` may not give the layout `paint` expects (the legend strip, `rightMargin()`), so the
  plot at 1200×600 could still be judged too small; or `realEmptinessIsStillNoData` finds that an empty `Series`
  gives a range rather than NaN.
- **P43 — the two new `ReportRendererTest` cases green on first run.** Confidence 70%. Risk: the em dash in "NOT
  RENDERED — chart" encoding oddly in the PDF stream. The tests only look for "NOT RENDERED".
- **P44 — witnesses:**
  - W31: `toImage(w,h)` without `setSize` turns `aNarrowChartExportsItsPlot` red;
  - W32: `emptyPlotMessage` returning the filter sentence for a starved plot turns `noRoomIsNotNoData` red;
  - W33: the renderer's CHART/TOPOLOGY case without the NOT RENDERED callout turns both new renderer cases red.
- **P45 — headless 2,005** (1,999 + 6), 0 failures, 65 skipped. **Frame 66 / 0 / 0 / 1.** Named risk: a frame test
  exporting a chart PNG and asserting its size, which `toImage()` still keeps at least 640×360.
- **P46 — end to end.** The new scenario 13 fails on a jar built from `df0b24a5` (no "NOT RENDERED" in the PDF) and
  passes on the fix. The verifier otherwise stays at 69 / 0.

## Set 9 — M68.6, names and addresses share one grammar (D-E5; Q2 answered: refuse at creation)

**Owner decision, 2026-09-24: Q2 = refuse at creation**, saved names reachable through an explicit compatible
address. Written after the code compiled and before any of its tests ran. Gated on a tree without the code.

**The rule, read from `SpotlightTarget.parse`, not invented.** A chart name cannot be addressed when:

- it contains `:`, the part separator;
- it is exactly `note` or `series` in any case, because its parts would be read as the bare forms;
- it contains `"`, which now quotes the compatible address.

Leading and trailing spaces are already trimmed by `addGraph`. The compatible address is quoted,
`graph:"a:b":note:2`. `context.graphAddresses` publishes each chart's address. It is enforced at the naming
entrances: the `graph` verb's create and rename, and the UI rename. NOT at `addGraph`, which is also the restore
path for saved charts.

- **P47 — `ChartNamingTest`, 4 cases, green on first run.** Confidence 60%. Risks: `graphAddress("x:note:2")`
  quoted then parsed. The quoted parser reads to the FIRST closing quote and then sees nothing, which should be
  right. The verb test's `graph {name, series: ["n.v"]}` may need a `key` shape rather than `series`, and could fail
  for that reason rather than for the rule.
- **P48 — `SpotlightTargetTest` unchanged and green.** The unquoted colon form keeps its refusal.
- **P49 — witnesses:**
  - W34: the quoted branch removed from `parse` turns `everyNameHasAnAddress` red;
  - W35: `doGraph`'s create check removed turns `theVerbRefusesAtCreation` red.
- **P50 — headless 2,009** (2,005 + 4), 0 failures. Frame 66 / 0 / 0 / 1. Verifier 72 / 0.

## Set 10 — M68.4, whole-or-refused requests (D-E3) and the record parameter (D-E4)

Written after the code compiled and before any of its tests ran. Gated on a tree without the code.

**The order, stated honestly.** The audit (a read-only agent, whose file:line claims were spot-checked before any
fix was built on them: the `open` early returns, the spotlight ordering, the topology record path) came first, and
the behaviour changes followed. The spec's disposition table is WRITTEN after the changes; the spec asked for it
first. **Decisions taken that reverse or change a tested or recorded behaviour, for review:**

- **The spotlight** goes out only when a view-changing verb SUCCEEDS. This reverses M64's recorded rationale, and
  `SpotlightEndsWhenTheViewChangesTest` was rewritten to the new policy, keeping its one-case-per-verb property.
- **`flag`** refuses an out-of-log index or offset instead of clamping it, following `SpotlightSetTest`'s precedent.
  `goto` keeps its clamp and names it.
- **A `graph` rename** carrying other fields is refused.

**Predictions:**

- **P51 — `WholeOrRefusedTest`, 9 cases, green on first run.** Confidence 45%. The proxy-based stand-in
  application is new: `invokeDefault` on a default interface method, and matching `openLog`'s three overloads by
  name. It is the likeliest thing to fail, for harness reasons rather than product ones. `gotoNamesItsClampAndItsLosers`
  assumes `targetRow` clamps 99 to 1 and prefers `recordIndex` over `at`.
- **P52 — `TopologyWholeOrRefusedTest`, 3 cases, green.** Confidence 55%. Risk: `cursorState().get("selected")` may
  not be a List, or the fixture's node id may not be `rootNode`.
- **P53 — `SpotlightEndsWhenTheViewChangesTest` green**, now 10 cases.
- **P54 — existing tests that break because they pinned a behaviour this set changes.** I expect **at least one**,
  from tests that send `graph {name, rename, …}` with other keys, clamp a `flag`, or read `open {log, format}`'s echo
  shape. Each one found is a disposition to check against the table, not a test to relax.
- **P55 — witnesses:**
  - W36: the rolled-set early return restored turns `aRolledSetAndAGraphAreBothOpened` red;
  - W37: `flag` back to clamping turns `flagRefusesAnIndexTheLogDoesNotHave` red;
  - W38: topology without its pre-check turns `aRefusalIsWhole` red;
  - W39: render clearing before `renderVerb` turns `aRefusedCallLeavesItLit` red;
  - W40: `withIgnoredParams` returning early on a refusal turns `aRefusalNamesUnknownKeys` red.
- **P56 — headless 2,022** (2,009 + 9 + 3 + 1), 0 failures after any P54 fixes. **Frame 66 / 0 / 0 / 1.**
- **P57 — end to end.** Scenarios 14 and 15 fail on a jar built from `00fd7773` (14: echo `recordIndex` 0, nothing
  selected; 15: graph gone) and pass on the fix. Everything else stays green, at 72 plus the new checks.

## Set 11 — M68.3, the framing diagnostic (D-E9, acceptance 10), on CONSTRUCTED logs

Written after the code compiled and before any of its tests ran. Gated on a tree without the code.

**No original log is needed, and why.** The diagnostic judges structure: where a record header appears and where
separators are. So each acceptance case is constructed exactly (`src/test/resources/framing/`, with a README saying
so), and the one real collapsed log the repository holds (`sg1-release-2026-09-21/sample-run.txt`) is the
cross-check. The client's `run1.log` is not in the public packet. Its SHAPE, taken from its PDF (nine
`eventLogRecord:` blocks, no `---`), is reproduced with `DEMO` names.

**The defect, read from the code.** `unseparated` used `indexOf("eventLogRecord:")` anywhere in a record's text,
quoted values included. **Limit, stated before the trials:** `FramingScan` opens a quote only at a value position. A
plain value containing `: '` whose quote never closes on its line would open a false quote and could hide later lines
(a false negative). No fixture exercises that.

- **P58 — `FramingScanTest`, 5 cases, green on first run.** Confidence 75%. Risk: the single-quoted `''` case, or
  the `- ` list-item handling in `quoteStateAfter`.
- **P59 — `ProducerFramingTest`, 9 cases, green on first run.** Confidence 50%. The risks:
  - `aLiveCollapseIsSuspectedFromThePendingFrame`: whether `forFollow()` plus `appendFrom` leaves the collapsed
    tail as PENDING rather than indexed;
  - `heapAndMappedAgree`: the mapped store's `rawText` may differ at the trailing newline;
  - `theRealStarterSampleIsSuspected`: the sample's own first line might not be column-0.
- **P60 — the existing `ProducerDiagnosticsTest` stays green.** The new message keeps "N records run together",
  `---` and `record.toString()`.
- **P61 — witnesses:**
  - W41: `FramingScan` ignoring quote state turns `aQuotedKeyIsText` red, and `aLegalOneRecordFileIsNotAccused` too;
  - W42: quotes opening anywhere turns `anApostropheIsNotAQuote` red;
  - W43: the pending frame ignored turns `aLiveCollapseIsSuspectedFromThePendingFrame` red.
- **P62 — headless 2,036** (2,022 + 5 + 9), 0 failures. **Frame 66 / 0 / 0 / 1.**
- **P63 — end to end.** Scenario 16's first check fails on a jar built from `ac0efaa0` (the old count accuses the
  legal file), and its suspicion checks fail there too (the old message says "This log is missing", not
  "Suspected"). All pass on the fix.

## Set 12 — the independent review's findings R1–R8, O1, O2 (review `0bb01fa8`, subject `14a72acc`)

**Written before any fix code or new test exists** — this commit touches only this file, so the gate is the tree
itself. What is already OBSERVED is not predicted here: every review probe was re-run on `0bb01fa8` and reproduced
byte for byte (`ReviewProbe`, `CoverageRaceProbe`, `ReportCoverageProbe`; outputs identical to the committed ones).
Those are reproductions, recorded in RESULTS. The predictions below are about trials that have not run: the new
regressions, their state on the unfixed code, the witnesses, the counts, and the probes re-run after the fixes.

**Design, fixed before coding.**
- **R1.** `doCoverage` captures the store, the session snapshot, the graph input, a COPY of the filter and the scan
  bound in ONE task on the EDT, where every log, graph and filter change happens, so no change can fall between
  them. The scan runs off the EDT on the copy and the bound (`CoverageService.assess` gains a bound). The
  qualification carries the captured generation/revision; the qualifier's existing stale check refuses it if the
  pair moved.
- **R2.** The report's own graph-provenance test is removed. `coverageForReport` takes the session snapshot's claim
  in the same EDT call as the store and graph it scores: REFUSED renders the claim's reason, with no ledger and no
  scalar line; QUALIFIED keeps the whole ledger and adds the claim's reason as a note. One helper, used only by the
  report, turns the session's `Assessment` into `CoverageData`; it decides nothing.
- **R3.** `RolledLogStore.readThroughIdentity()` asks every member and returns the most severe verdict, its reason
  prefixed with the member file and its position; any member that suspends reads makes the set suspend reads.
  `LogStore.readThroughAssessed()` (default false) states the SPI boundary: true for the heap and mapped stores with a
  file, true for a rolled set only if every member is.
- **R4.** `PairingQualifications` gains a frozen copy whose mutators throw; `SessionSnapshot` freezes whatever it is
  given in its compact constructor. Equality ignores the flag.
- **R5.** `topologyProblem` predicts, from the request's own `select`, `pop` and `focus`, whether a focus will be in
  force when `saveFocusAs` runs, and refuses the whole call if not (and for a blank name).
- **R6.** `FramingScan` skips one U+FEFF at position 0 of the item, for that first line's header test only.
- **R7.** `pendingUnseparated` returns `FRAMING_NOT_ASSESSED`, worded as the record still being written, when the scan
  is truncated with no candidate. The pending frame is scanned unless an UNSEPARATED finding is already present (it
  was skipped whenever ANY indexed finding, including NOT_ASSESSED, had been added).
- **R8.** `SpotlightTarget.graphAddress` returns null for a name the quoted grammar cannot carry (it contains `"`);
  `context.graphAddresses` carries null at that position, and `context.graphAddressUnavailable` names the chart and why.
- **O1.** `publishSnapshot` stops delivering an older snapshot once a listener's posted fact has published a newer one.
- **O2.** Javadoc and spec state what `graphRevision` binds (path, source, declared ids, node types); a test pins that
  an edge-only change does not move it. Content identity stays an open decision, recorded, not claimed.

**Predictions.**
- **P64 — R1 red first.** `CoverageCaptureTest#oldInputsCannotAcquireANewIdentity` (a log switch queued by the store
  supplier itself) fails on the unfixed executor: the new generation acquires a qualification naming the old log's
  id. `#aGraphChangeDuringPreparationIsNotStampedNew` fails the same way. `#theScanUsesTheFilterItCaptured` fails:
  the live filter changed mid-scan is what the scan used. `#theScanRunsOffTheEdt` PASSES on the unfixed code (it
  already scans off the EDT) — a guard, not a witness of the defect. Confidence 70%: the graph case depends on the
  topology panel and `GraphOpened` moving in one EDT task.
- **P65 — R2 red first.** `ReportCoverageTest` has three cases (retained mismatched graph, INFERRED graph, graph with
  no auditor). On the unfixed report path the mismatch and no-auditor cases print a ratio; the INFERRED case is
  refused by the report's own provenance test, so it passes on the unfixed code. After the fix all three render the
  action's exact refusal sentence and no "ratio". The built-jar `ReportCoverageProbe` re-run prints the refusal and no
  "declared … ratio" line.
- **P66 — R3 red first.** `RolledLogStoreReadIdentityTest`: mapped members, second member rewritten in place →
  suspends reads and names `b.yaml`; heap members, second changed → retained, labelled, names it; second deleted →
  REPLACEMENT, retained; unchanged → null; `readThroughAssessed` true for heap/mapped/rolled, false for `SpiLogStore`.
  Red first on every case that expects non-null, and on `readThroughAssessed` (the method does not exist: a compile
  failure, so the red-first trial is run with the method stubbed false).
- **P67 — R4 red first.** `SessionSnapshotTest#aConsumerCannotChangeThePublishedQualifications` fails on the unfixed
  snapshot (`clear()` succeeds and the next read is empty).
- **P68 — R5 red first.** `TopologyWholeOrRefusedTest#aRefusedSaveLeavesTheSelection` fails (selection becomes
  `[rootNode]`); `#focusThenSaveInOneCallStillSaves` passes before and after.
- **P69 — R6/R7 red first.** In `ProducerFramingTest`: `aLeadingBomDoesNotHideACollapse` (through `HeapLogStore` and
  `ProducerDiagnostics`) fails; `aBomInsideAValueIsNotAHeader` passes before and after; `anOversizedPendingFrameIsNotAssessed`
  fails (no finding); `anOversizedPendingFrameWithACandidateIsSuspected` passes before (the prefix candidate is found)
  and after; the pending frame stays unindexed in both (size 0).
- **P70 — R8 red first.** `SpotlightTargetTest#aSavedNameWithAQuoteHasNoAddress` fails (a quoted string comes back).
- **P71 — O1 red first.** `SessionSnapshotTest#aListenerThatPostsDoesNotReorderDelivery` fails with `[second, first]`.
  O2's `graphRevisionBindsTheHeldFactsNotContent` passes before and after (it pins existing behaviour).
- **P72 — existing tests.** At most three existing assertions change, all wording or signature: the report tests that
  build `CoverageData` directly are unaffected; a `graphAddresses` assertion that expects a quoted form for a name with
  `"` (if one exists) is rewritten to expect null. No other class breaks.
- **P73 — witnesses** (strict: green baseline, a `<failure>` at the named test and assertion, byte-identical restore
  from a copy, green after), registered in `tools/mutation_controls_session.py`: one per finding — R1 capture split
  (store read outside the EDT task), R1 live filter, R2 claim ignored, R3 member identity dropped, R4 no freeze, R5 no
  save precheck, R6 no BOM skip, R7 no pending bound, R8 quoted address restored, O1 no stop. Each goes red at its own
  test. The gate grows from 101 to **111**, all caught.
- **P74 — counts.** Headless grows by the new tests (about 20, final number stated in RESULTS with the list); 0
  failures, 0 errors; skips unchanged at 101. Frame 102 / 0 / 0 / 0 unless a frame test asserts a quoted address.
- **P75 — probes after.** `ReviewProbe` re-run on the fix: `snapshot.after` throws (the probe does not catch it, so it
  stops at that line — recorded as the fix, not a failure of the fix); a copy with that line guarded prints every other
  line reversed. `CoverageRaceProbe` cannot run against the fix: its store supplier blocks INSIDE the capture, which is
  now on the EDT, so its own `invokeAndWait` waits until its 10 s timeout. That is the defect made impossible, and the
  deterministic regression P64 replaces it; the probe is preserved unchanged.

## Set 13 — closing the stated gaps before one review (owner request, 2026-09-26)

**Written before any code for these items exists.** The owner asked for the remaining implementable gaps to be closed
so that one review covers them. Out of scope, because each is an owner decision or another repository: Q4 (partial
delivery), Q5 (a saved name with `"`), O2's content identity, and M68.3's producer half. The general check that a chart
does not contradict its series stays a stated gap, because it has no single surface to hold it.

**Design, fixed before coding.**
- **A — the table (D-E6).** The log table shows a banner rendered FROM THE SESSION SNAPSHOT: when `logIdentity` is
  `UNVERIFIED` or `REPLACEMENT`, the banner states the reason. It is hidden for null, `VERIFIED` and `REOPENED`.
  `LogTablePanel.setIdentityNote(String)`; `MainFrame` calls it where it renders the snapshot, so no second verdict.
- **B — focus sections (D-E8).** `TopologyPanel.renderFocusForReport(name, w, h)` paints the named focus's resolved
  ids off-screen with the existing `paintOffscreen`, or returns null. The PDF draws it; a focus that no longer
  resolves prints NOT RENDERED with why.
- **C — series sections (D-E8).** A series section's `key` or `expr` is extracted synchronously under the current
  filter and drawn on a detached `ChartPanel` at page size. An expression that fails to extract prints NOT RENDERED.
- **D — environment and destination pointers (D-E7).** An environment's `logDir` and a DIRECTORY destination are
  resolved like the runbook pointer: no project root, outside the root, or no directory there, each naming the root
  tried. An `s3://` or `https://` destination is stated as not checked. `context` carries `problem`; the Project panel
  row turns WARN and shows it.
- **E — nested keys (D-E3).** `ActionDispatcher` walks array items whose schema declares `properties` and names an
  unknown nested key by path (`notes[0].txt`) in `ignoredParams`, or in the refusal.

**Predictions.**
- **P76 — red first.** New tests fail on the tree without the code (compile failure for the new methods, so each is run
  red with a stub returning null/false/no-op): A `LogTablePanelIdentityBannerTest` (2 cases) and a static guard that the
  snapshot render calls it; B `TopologyReportFocusTest` (2); C `ReportSeriesPictureTest` (2); D in `RunbooksResolutionTest`
  (3) and `ProjectModelTest` (1); E in `ActionDispatcherNestedKeysTest` (2). Confidence 65%: C depends on synchronous
  extraction off a detached panel working headless.
- **P77 — the end-to-end harness.** Scenario 13 asserts NOT RENDERED for a focus section and will go red once B lands;
  it is rewritten to assert the picture is drawn and NOT RENDERED is absent, which is the stated gap closing, not a
  weakened check. No other scenario changes.
- **P78 — witnesses.** One per item (A banner setter no-op, B render returns null, C series returns null, D the
  directory check removed, E nested walk removed), each red at its own test; the gate grows by 5 over set 12's.
- **P79 — counts.** About 13 new headless tests; 0 failures; skips unchanged at 101. Frame 102 / 0 / 0 / 0.
