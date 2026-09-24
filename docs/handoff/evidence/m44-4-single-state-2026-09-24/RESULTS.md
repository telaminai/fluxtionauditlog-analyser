# M44.4 single-state model — results

Against [PREDICTIONS.md](PREDICTIONS.md). Wrong predictions are recorded as wrong.

## Set 1 — M44.4a

| # | Prediction | Result |
|---|---|---|
| P1 | headless `mvn test` green | **Held.** 1,941 run, 0 failures, 0 errors, 65 skipped. That is 1,937 before plus the 4 new `SessionFactsTest` cases. The named risk (a GraphML-pinning test) did not fire. |
| P2 | frame tests 66 / 0 / 0 / 1 skip, at 60% confidence | **Held.** 66 / 0 / 0 / 1, the known local focus skip. |
| P3 | a reader-supplied graph reaches the session; an inferred one refuses coverage | **Held.** `aGraphWithNoFileIsStillOpen`. Witness W3, restoring path-based `isOpen()`, turned it red. |
| P4 | a fact posted mid-operation runs after it, and is recorded as a no-op | **Held.** `aFactPostedMidOperationIsQueued`. Witness W4, making `post` drop while dispatching, turned it red. |
| P5 | a stale fact is refused as `staleFact` | **Held.** `aStaleFactIsRefused`. Witness W5, removing the generation comparison, turned it red. |
| P6 | `verify-m68-1-coverage.py` 65 / 0 on the rebuilt jar | **Held.** 65 pass, 0 fail. |
| P7 | `MainFrame` net lines fall by at least 25 | **Wrong**, and withdrawn as a prediction because it was already measurable: +40 / −41. |

Each witness was applied, run and then restored. The file's SHA-256 was checked identical after every restore, and
the class was green again afterwards (0 failures).

**A process slip, recorded.** The predictions commit `196eb776` was made without running the gates first, the same
mistake as sets 4 and 6 of the M68.1 evidence. The file was docs-only, and the gate run above covers it. It is still
a breach of the rule, and it is written down here rather than folded away.

**A bootstrap step that should not be repeated.** Regeneration could not compile against a generated processor
that still referenced the deleted events. The stale generated file was hand-stripped to bootstrap, and the
regeneration then overwrote it whole: `grep Observed` on the result finds 0 matches, and the two emitted copies are
byte-identical. The owner's procedure (strip `@OnEventHandler`, regenerate, delete the methods) is now in
`SessionProcessorBuilder`'s javadoc, so no hand-edited state has to be trusted next time.

**Found, not predicted.** `OpenGraph` equated "no file path" with "no graph", so a reader-supplied graph was invisible
to the processor. The CHANGELOG line and P3 cover it.

## Set 2 — M44.4b

| # | Prediction | Result |
|---|---|---|
| P8 | `SessionSnapshotTest` 6 cases green on first run, at 80% confidence | **Held.** 6 / 0 / 0 / 0. |
| P9 | witnesses W6–W10 each red at the named test | **Held, all five.** W6 also turned `listenersHearChangesOnly` red, because an unpublished snapshot is never heard. Each restore was byte-identical, and the class was green again afterwards. |
| P10 | headless 1,947 / 0 / 0 / 65 | **Held.** |
| P11 | frame 66 / 0 / 0 / 1, at 65% confidence | **Wrong on first run: 2 errors**, in `aSampledPairingAgreesAcrossFrameDiscoveryAndSession` and `committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession`: `NoSuchMethodException: MainFrame.pairingAgainst`. The cause was not the one I named. Both parity tests called the deleted scorer by reflection. The named risk, qualifications surviving a same-graph reopen, did not fire. **Fix:** the frame leg now compares the verdict the frame PUBLISHES (`lastPairing`, what `context` and the panel render) and the session leg compares the snapshot. The `equals` strength is unchanged. After the fix, 66 / 0 / 0 / 1, including the reworded O-i test. |
| P12 | `verify-m68-1-coverage.py` 65 / 0, at 70% confidence | **Held.** 65 pass, 0 fail, on the rebuilt jar. |

**What the P11 miss says.** I predicted a behavioural risk and missed a structural one: tests that reach into the
frame by reflection are bound to its private method names, so deleting a duplicate breaks them even when behaviour
is right. The rewrite also leaves them stronger. The old frame leg checked a recomputation that nothing displayed.

**The prediction-commit conflict, stated.** `345cf57d` (predictions) was again committed ungated. This time it was
deliberate, because the gate would have run the very tests those predictions are about, which were already in the
working tree. From set 3 on, the code is stashed, the predictions commit is gated on the tree without it, and the
code is then restored, so both rules hold.

## Set 3 — M44.4c

| # | Prediction | Result |
|---|---|---|
| P13 | `PairingQualifierTest` 6 green on first run, at 70% confidence | **Held.** 6 / 0 / 0 / 0. The assumed `toMap` keys were right. |
| P14 | W11–W14 each red at the named test | **Held, all four.** Byte-identical restores, and green afterwards. |
| P15 | headless 1,953 / 0 / 0 / 65 | **Held**, both before and after the P16 fix. |
| P16 | frame 66 / 0 / 0 / 1, at 55% confidence | **Wrong on first run: 1 failure**, `aFollowAppendEvictsNoTransitionRecordAndTheClaimIsCurrent`. The cause was not the lag I named. `pollFollow` calls `onFilterChanged()` after each append, which posted an **unchanged** `ViewFilterChanged` twice per append; with tracing on, that wrote ten transition records over five appends. Diagnosed by counting event types in the two record lists (`Counter({'ViewFilterChanged': 10})`), not by reading code. **Fix:** post only when the key differs from the session's own `filterKey`. A non-change is not a fact. After the fix, 66 / 0 / 0 / 1. |
| P17 | `verify-m68-1-coverage.py` 65 / 0, at 60% confidence | **Held.** |

**Added after the predictions, with its own witness.** Spec §13 asks `SessionGraphShapeTest` to pin the new node.
`theQualifierFollowsThePairAndActsOnNothing` pins `openLog`/`openGraph`/`pairing → pairingQualifier` and forbids
`pairingQualifier → effectQueue`. Witness W15, deleting the `openLog → pairingQualifier` edge from the committed
GraphML, turned it red. The restore was byte-identical.

**The class P16 belongs to.** It is the second defect of one kind in this milestone: the adapter reporting something
that did not change. The first was M44.4a's re-scopes, which M44.4b met by retention. With tracing on, a non-change
still costs a record, so the adapter must report only changes. The O-i frame test is what caught both. Rule 8's cheap
check for the class is that test. A headless version, asserting transitions unchanged across N no-op facts from each
adapter entrance, would catch it without a window, and is not yet written.

**A process error, caught before it counted.** The first gate for the set 3 predictions commit ran with `git stash --
src/`. That left the three new untracked files in place, so the tree did not compile and the run was void. It was
redone with `stash -u`, and the commit (`296467ad`) was gated on 1,947 / 0 / 0 / 65 headless and 66 / 0 / 0 / 1 frame.

**A small upstream finding.** The refused regeneration (`FLX-1009`) left an empty `SessionProcessor.java.failed` in
`src/main/java`, which `git status` showed as untracked. It was moved out rather than committed. The generator could
write it under `target/`, or not at all.

**The limit stated in the predictions, confirmed.** The two parity frame tests' frame-vs-session comparison is now
equal by construction. They still check session against discovery, and the panel line.

## Set 4 — M44.4 journeys, and M68.4's combined open

The journeys (5 / 0 / 0 / 0, W16–W19 each red) were run before predictions, as `PREDICTIONS.md` set 4 records, and
are not scored.

| # | Prediction | Result |
|---|---|---|
| P18 | seen red: the main case fails at its first assertion; both controls pass | **Held.** `expected: <0> but was: <1>` CloseGraphEffect, at `aGraphOpenedForTheLoggingLogIsKept`; 1 of 3 failed. |
| P19 | a one-decision fix in `LogArrival`, using the graph revision | **Held in outcome, wrong in mechanism.** The revision-based version passed all three cases. Before the end-to-end run, reasoning about the verifier's scenario order exposed a hole: re-opening the graph ALREADY on screen does not move the revision, because revision counts DIFFERENT graphs for the qualifications, so that open read as residue. Intent is "somebody opened a graph", so `OpenGraph` gained an `openings` count, and `LogArrival` compares that. A fourth case, `reOpeningTheSameGraphIsIntent`, was added. That reasoning turned out not to be what the verifier exercises, since `open_in_order` closes everything first. The case is real regardless. |
| P20 | W20 and W21 each red at their control | **Held**, re-run against the final condition, plus W22 (openings counting only moved graphs, turning `reOpeningTheSameGraphIsIntent` red). |
| P21 | nothing else changes; I knew of no frame test opening a mismatching graph mid-load | **Wrong.** `AsyncOpenInterleavingFrameTest.b1_aHumanRecentGraphmlDuringAPendingSocketLoad…` does exactly that, and relied on the arrival closing the graph. Headless held at 1,963 / 0 / 0 / 65 (predicted 1,962; the fourth case was added after the prediction). **Decision taken, for review:** the test's premise changed, and its point did not. The graph is now kept; the arrival still warns, and that warning is still not a dialog for a socket arrival. Frame suite afterwards: 66 / 0 / 0 / 1. |
| P22 | the new verifier check fails on `8893cb08` and passes on the fix | **Held after one correction.** The first version PASSED on the old jar. A one-record log can finish loading before the graph opens, and then the graph arrives as ordinary intent and is kept on any build. It also broke an unrelated check (the PDF figures), because I inserted it before the PDF scenario and changed the log that scenario exports. **Fix:** a 40,000-record log, so it is still loading when the graph opens, placed last. Then the old jar gave 65 pass, 2 fail (`'graph': None` after the combined open settled), and the fixed jar 67 / 0. `verify-session-transitions.py`: ALL PASS on the fixed jar. |

**What the P22 miss teaches.** The first end-to-end check passed on the defect because it never produced the
interleaving the defect needs. It passed for the wrong reason. Running it against the old jar is what caught that,
which is why seen-red runs against a pre-fix build are part of the protocol.

## Set 5 — M68.5, identity under Follow

| # | Prediction | Result |
|---|---|---|
| P23 | `FollowIdentityTest` 9 green, at 85% confidence | **Held.** 9 / 0 / 0 / 0. |
| P24 | `HeapLogStoreFollowIdentityTest` 6 green, at 60% confidence | **Held.** 6 / 0 / 0 / 0. Neither named risk fired: the touch did not read as a change during the read, and the atomic move did give a new inode. |
| P25 | `LogIdentityTest` 4 green, at 75% confidence | **Wrong: 1 failure, a real bug.** `onlyTheSamePathIsAReopen` found a log opened at a DIFFERENT path marked `REOPENED`. The same-path comparison sat after the line that overwrote `logPath`, so it compared the new path with itself. Fixed by reading the previous path first. 4 / 0 afterwards. |
| P26 | W23–W25 each red | **Held**, after correcting a false alarm. W23 first read as "NOT CAUGHT" in the store test. The raw report showed 2 failures, including `sameLengthRewrite: expected -1 but was 0`, the old silent behaviour. The harness regex `name="(\w+)"` never matched test names that carry a parameter (`sameLengthRewrite(Path)` for `@TempDir`). So it was a harness defect, not a missing witness. W24 and W25 were red at their named tests. |
| P27 | headless 1,982 / 0 / 0 / 65 | **Held.** |
| P28 | frame 66 / 0 / 0 / 1, at 60% confidence | **Held.** No Follow frame test hit a moving-read pause. |
| P29 | scenario 12 fails on `f2e25e80`, passes on the fix | **Held.** Old jar 67 pass, 2 fail (`identity: {}`: the same-length rewrite ignored); fixed jar 69 / 0. |

**The harness defect is worth keeping.** All six witness scripts this session share the pattern. Earlier sets used
test methods without parameters, so they were unaffected. Any future witness against a parameterised test would
have reported NOT CAUGHT for a real catch, the safe direction, but a false alarm all the same. Match on the name up
to `(` or `"`.

## Set 6 — M68.5, identity at the next request

| # | Prediction | Result |
|---|---|---|
| P30 | `ReadThroughIdentityTest` 6 green, at 85% confidence | **Held.** |
| P31 | `MappedLogStoreReadIdentityTest` 3 green, at 60% confidence | **Held.** Both named risks were unfounded on APFS. `Files.writeString` rewrote in place (the channel read `zzz`), and after an atomic replace the old channel still read the opened file (`aaa`). That is now measured, not assumed. |
| P32 | `ActionDispatcherReadIdentityTest` 3 green, at 75% confidence | **Held.** |
| P33 | W26–W28 each red | **Held**, with the corrected name pattern. |
| P34 | headless 1,994 / 0 / 0 / 65 | **Held.** `atomicReplace` did not assume out. |
| P35 | frame 66 / 0 / 0 / 1, at 55% confidence | **Held.** The window-focus listener disturbed no frame test. |
| P36 | verifier 69 / 0 | **Held.** |

The first set this session in which every prediction held. The sets before it are why the confidence figures were
where they were.

## Set 7 — M68.5, acceptance 8's diagnostic

| # | Prediction | Result |
|---|---|---|
| P37 | `RunbooksResolutionTest` 4 green, at 80% confidence | **Held.** The `/var` → `/private/var` risk did not fire: neither side resolves symlinks. |
| P38 | the new model test green, the old row tests unchanged and green | **Held.** `ProjectModelTest` 24 / 0. |
| P39 | `ProjectPanelIsRevealOnlyTest` green with the two new keys, at 70% confidence | **Held.** 2 / 0. |
| P40 | W29 and W30 each red | **Held.** |
| P41 | headless 1,999 / 0; frame 66 / 0 / 0 / 1; verifier 69 / 0 | **Held.** |

**A process correction.** Set 6's gate-and-commit script committed the predictions whatever the gate result was. The
gates were green, so nothing wrong was committed, but the script could not have stopped it. Set 7's commits only
when both gates exit 0.

**The branch in total.** Seven sets across M44.4 and M68: 41 predictions. **5 were wrong outright** (P7, P11,
P16, P21, P25), and **2 more held only after a correction** (P19's mechanism, P22's first check). Two of those found real bugs: P25's path comparison, and P21's reviewed
test whose premise changed. 30 mutation witnesses, W1–W30, each red at its named test. W23's "not caught" was a
harness regex, not a missing witness.

## Set 8 — M68.2, rendered evidence

| # | Prediction | Result |
|---|---|---|
| P42 | `ChartExportRenderTest` 4 green, at 55% confidence | **Held.** Neither named risk fired: `setSize`/`doLayout` on a never-displayed panel laid out as `paint` needs, and an empty `Series` gives NaN. |
| P43 | the two renderer cases green, at 70% confidence | **Wrong: 1 failure, which found a real flaw.** `aChartWithNoPictureSaysItWasNotRendered` failed on `contains("spread")`. Callout LABELS print upper-cased, so the chart's name reached the page as `SPREAD`, and chart names are case-sensitive. The name now goes in the callout body, exactly. 13 / 0 afterwards. |
| P44 | W31–W33 each red | **Held.** W31 also turned `realEmptinessIsStillNoData` red: without the off-screen layout even a truly empty chart was judged at the component's zero size, so the old export mislabelled that case too. |
| P45 | headless 2,005 / 0 / 0 / 65; frame 66 / 0 / 0 / 1 | **Held.** |
| P46 | scenario 13 fails on `df0b24a5`, passes on the fix | **Held after two corrections to MY check, not the product.** (1) I wrote the section as `{"kind": "topology", "ref": …}`, and the verb takes `focus`. It skipped the section with a warning and replied `ok` with 0 sections. (2) `saveFocusAs` needs a focus APPLIED first (`focus: true`), and a selection alone is not one. Each was diagnosed by making the check report what it found. Final: old jar 71 pass, 1 fail, where the resolved topology section left nothing on the page and the focus is not even mentioned; fixed jar 72 / 0. |

**For M68.4's D-E3 audit, observed here and not changed.** `report` accepted a request containing a malformed
section, skipped it and replied `ok` with `sections: 0`, naming the skip in `warnings`. That is not silent, but it is
a request honoured in part, which is D-E3's question.

## Set 9 — M68.6, the naming grammar

| # | Prediction | Result |
|---|---|---|
| P47 | `ChartNamingTest` 4 green, at 60% confidence | **Held.** Neither named risk fired: `x:note:2` quoted round-trips, and `series: ["n.v"]` is the verb's shape. |
| P48 | `SpotlightTargetTest` unchanged and green | **Held.** 86 / 0. |
| P49 | W34 and W35 red | **Held.** |
| P50 | headless 2,009 / 0; frame 66 / 0 / 0 / 1; verifier 72 / 0 | **Held.** |

## Set 10 — M68.4, whole-or-refused and the record parameter

| # | Prediction | Result |
|---|---|---|
| P51 | `WholeOrRefusedTest` 9 green, at 45% confidence | **Held.** The proxy stand-in application worked first time. |
| P52 | `TopologyWholeOrRefusedTest` 3 green, at 55% confidence | **Held.** |
| P53 | `SpotlightEndsWhenTheViewChangesTest` green | **Wrong: 1 failure, a real product bug.** `aRefusedCallLeavesItLit` expected `goto {recordIndex: 3}` on an EMPTY log to be refused. It "succeeded": `clampRow` gives `max(0, min(3, -1)) = 0`, so goto replied ok for record 0 of a log with no records. Fixed: goto refuses when there are no records. 10 / 0 afterwards. |
| P54 | at least one existing test breaks on a changed behaviour | **Wrong: none did.** None of the changed behaviours was pinned by any test: a rename with other fields, flag's clamp, the formatted-log echo. That is itself a finding about coverage. |
| P55 | W36–W40 each red | **Held.** W39's mutation also broke `theQueryAndCanvasVerbsLeaveItLit` (the mutation was cruder than the original). |
| P56 | headless 2,022 / 0; frame 66 / 0 / 0 / 1 | **Held.** |
| P57 | scenarios 14 and 15 fail on `00fd7773`, pass on the fix | **Held.** Old jar 73 pass, 5 fail. Scenario 14's reply was `recordIndex: 0`, "no record selected", and an out-of-range index was accepted: DX-04 exactly. Scenario 15 had `graph: None` after the rolled-set open: DX-03. Fixed jar 78 / 0. |

**Where the defects were.** Both real bugs this set found, goto on an empty log and flag's clamp, are in the verb
layer. Across sets 1–10 the real defects split. **Two were in session nodes:** `OpenGraph` treating a file-less
graph as no graph (set 1, pre-existing, found BY the migration), and `OpenLog`'s path comparison (set 5, introduced
by me and caught by its test). The rest were at the adapter or verb boundary: the non-change posts (sets 1–3), the
empty-log goto and the flag clamp (set 10), the upper-cased section name and the export capture (set 8). A first
draft of this paragraph said all of them were outside the processor, and was wrong.

## Set 11 — M68.3, framing on constructed logs

| # | Prediction | Result |
|---|---|---|
| P58 | `FramingScanTest` 5 green, at 75% confidence | **Held.** |
| P59 | `ProducerFramingTest` 9 green, at 50% confidence | **Held.** None of the three named risks fired. The collapsed live tail stays pending under `forFollow()`; heap and mapped give identical messages; the starter sample's headers are at column 0. |
| P60 | `ProducerDiagnosticsTest` unchanged and green | **Held.** 9 / 0. |
| P61 | W41–W43 red | **Held.** W41 turned both its named tests red. |
| P62 | headless 2,036 / 0; frame 66 / 0 / 0 / 1 | **Held.** |
| P63 | scenario 16 fails on `ac0efaa0`, passes on the fix | **Held.** Old jar 78 pass, 3 fail. The legal file got "record 1 alone contains 2 records run together", the round-3 false verdict word for word, and the collapse was stated as fact with no span. Fixed jar 81 / 0. |

**A log was not needed.** Every acceptance-10 case was constructed, because the diagnostic reads structure. The client's
file would have shown only one case: the collapse. The cases that mattered most, the legal file that was accused and the
pending live collapse, could only be constructed.

## After the sets: the witnesses are now regression protection

Every witness in sets 1–11 ran from a one-off script in the session's scratchpad. That shows a mutation was caught
once, and protects nothing after. On 2026-09-25 they were registered as 45 controls in `tools/mutation_controls_session.py`,
which `tools/verify_project_chart_review.py` appends to `CASES`. The CI `mutation-gate` job (main's PRs #16 and #18)
therefore runs them on every PR to main and every push to it. W3–W44 map one to one, with W23 split across its
two named tests. **W45 is new:** removing goto's empty-log refusal (set 10, P53) must turn
`SpotlightEndsWhenTheViewChangesTest#aRefusedCallLeavesItLit` red, and it did. W1/W2 belong to the M68.1 harness
and stay there. Run: the 45 alone, all caught in 62.4 s; the full gate, 95 controls caught in 255.0 s; preflight,
95 anchors. The GraphML control anchors on the edge's source and target rather than its id, because the id changed
across this branch's regenerations (78, then 80), and an id-based anchor would have moved with the next one.
