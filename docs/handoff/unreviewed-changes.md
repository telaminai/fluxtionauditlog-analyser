# Un-reviewed changes on `main` — pending review

A running ledger of changes **committed directly to `main` without** the usual brief → report → review
cycle. These are small, ad-hoc fixes made by a session working primarily in **another repo** (a downstream
consumer of the analyser) that hit an analyser-side bug and fixed it in passing, rather than a delegated
work block.

**For the reviewing session:** on your next pull, review each `☐` entry below — read the commit, sanity
the change against the codebase and the repo rules (CLAUDE.md), run `mvn test`, and **verify anything the
entry says was not verified** (Swing UI changes are not unit-tested — build and run the jar). Then tick it
`☑ reviewed <date>` with a one-line verdict, and file any follow-up as a normal review. Fully-reviewed
entries move to `completed/` when this file is next tidied.

Every entry must carry: commit SHA, what & why, files, what was verified, and **what the reviewer must
still check**.

## ☐ 2026-09-16 · Note rules drift when zoomed in (owner report, reproduced on the live 1.13.1 over MCP)

**What & why.** `ChartPanel.paintNotes` passed the view bounds to `ChartNotes.byColumn` cast to `long`; the
series use the exact doubles (`xToPx`). Zoomed to a 17 ms window with fractional-ms bounds the origin moved by
up to 1 ms ≈ 60 px, so every dashed note rule sat ~35 px right of its point (screenshot of the owner's "Price
by symbol" graph, rules 1–5 versus the NVDA steps). `byColumn(double, double, int)` now uses the series'
own formula; the `long` overload delegates.

**Verified.** `ChartNotesTest.aRuleLandsOnItsPointWhenTheWindowHasFractionalBounds` (453 vs the 489 the
truncated origin gave); mutant restoring the truncation: red on that test AND on the existing colliding-
columns test. Not re-captured on the live instance (it runs 1.13.1; the fix is on main).

**Reviewer must still check.** A note exactly at the right bound (`f == 1`, column == width) draws at the frame
edge rather than one pixel inside — same as the series, but worth an eye. Marker SERIES (`MarkerSeries.aggregate`)
and the record marker already used doubles and were not changed.

## ☐ 2026-09-16 · M44.3 the asynchronous session driver + M44.3a, N1 and the clamp fixtures, the skill rewording

**What & why.** Finish-first items after 1.13.1. **M44.3** per `spec-async-session-driver.md` (status block says
what was built and the two deviations): `OpenLogRequested` → new `LogOpening` node → `OpenLogEffect`; the adapter
(`MainFrame.startLoad`) answers `Pending` and later `LogOpened`/`LogOpenFailed` with the same opId; `onLoaded`
submits the result FIRST and discards a store the gate refuses (superseded); `OperationGate` gains
`inFlightWhat()` (→ `context.inFlight`); `SessionDriver` is confined to its creating thread. **M44.3a**:
`LogArrival` handles `LogOpened` only; `CloseGraphEffect(opId, graphPath)`; the adapter closes nothing if the
open graph differs. The processor was REGENERATED (hosted generator, builder 1.0.68; 38 nodes). The three
vocabulary GraphML fixtures were restored after the regen script overwrote them: they are a frozen pair with the
legacy export (2026-08-31 graph) for the exporter-compatibility tests, so the script's refresh is now opt-in and
`DescriptorFingerprintTest` compares the live processor with the live GraphML instead.

**Verified.** `AsyncOpenReplayTest` (6, all on one thread), the rewritten `LogArrivalReplayTest` (request-then-
result), `EffectDrainAtBatchEndTest` cascade; `mvn -o clean package` 1418 tests green; display suite 5/5 with the
positive control re-pointed (a human ARRIVAL closing a mismatching graph warns as a dialog; a human File-menu
close no longer warns, which was the R2-F3 spurious re-judgement); `tools/verify-session-transitions.py` ALL PASS
on the built jar.

**Reviewer must still check.** The generated processor diff (regenerated, not hand-edited — the copyright line
stripped by the build); supersede on a real slow load (only replayed here); whether `openLogs`/`discoverGraphs`
need an audience declaration now that `OpenLogEffect` sets it; the audit record of one operation across
dispatches (D-A5) read in the analyser itself.

## ☑ 2026-09-16 · 1.13.1 READY WITH FOLLOW-UPS — fourth independent pass; follow-ups R4-F1/R4-F2 done in this commit

Review: [review_analyser_1.13.1_pass4_2026-09-16.md](review_analyser_1.13.1_pass4_2026-09-16.md) — the remaining
blocker (R3-B1) closed on the reviewer's project-first sequence; the close-only mutant independently killed through
*Open recent GraphML*. **This commit:** R4-F1 — that sequence and its human positive control are in
`PairingDuringLoadFrameTest` (5 display cases; the close-only mutant is red on it). R4-F2 — the audience wording is
narrowed to the entrances actually covered, and the two human graph entrances the helper did not cover (*File ▸
Open GraphML*, a file drop) now declare at their entrance. Deferred, recorded: **M44.3a**. Release: run the manual
`release` workflow on `main` with version `1.13.1`.

## ☑ reviewed 2026-09-16 · Response to the third-pass review: R3-B1 fixed; R3-F2 fixed; R3-F3 recorded · `1c3c817a` + `ee54ad3a`, based on `4363f439`

**Verdict: READY WITH FOLLOW-UPS for 1.13.1.** Independent
[fourth-pass review](review_analyser_1.13.1_pass4_2026-09-16.md): the exact project-first R3-B1
sequence passes. A stronger real Recent GraphML menu sequence also passes and fails when ONLY
the close declaration is removed, with the human-warning control retained. The author's all-entrance
mutant result is reproduced, but the claim below that no human entrance can be driven without a
chooser is disproved: commit the stronger test as follow-up R4-F1. R3-F2's colour fix and R3-F3's
M44.3a tracker entry are accepted. R4-F2 narrows the universal audience wording to the actual
entrances. Local verify: 1,408 tests, 0 failures/errors, 4 display-only skips; local and exact-tip
CI display suites both execute all four with no skips. M44.3a remains explicitly deferred.

Response: [handoff_analyser_1.13.1_pass3_response_2026-09-16.md](handoff_analyser_1.13.1_pass3_response_2026-09-16.md).
Review: [review_analyser_1.13.1_pass3_2026-09-16.md](review_analyser_1.13.1_pass3_2026-09-16.md).

**What & why.** R3-B1 — the audience flag belongs to the operation whose effects execute, never to the session:
the socket verbs `close`, `openGraphml` and `selectProcessor` set `sessionInteractive = false` on entry; the
File-menu close/reset listeners and the human graph-open entrance set it `true` there (not inside the shared
`closeLog`/`closeGraph`, which session effects and socket verbs also call); a log arrival keeps taking it from its
`OpenRequest`; a project transition from its argument. R3-F2 — the text-block delimiter search skips an escaped
triple quote (an odd run of backslashes before it). R3-F3 — R2-F3 is now recorded as tracker item **M44.3a** with
its reproduction and processor-side remedy, instead of "filed there".

**Verified.** `PairingDuringLoadFrameTest` gains the review's shape without the project step: a HUMAN
`openFile(A)` arrival, then socket `open {graphml: B}` (kept, mismatch announced), then socket `close {graph}` under
the dialog watchdog: 0 dialogs, graph closed. 4/4 on a display. **Mutants.** Removing the `close` verb's declaration alone turned nothing red in the first version of this
entry, and the entry then claimed the human graph-open entrance "cannot be driven from a test" — **wrong** (R4-F1):
*Open recent GraphML* is a real menu item wired to the human helper. The suite now drives it:
`recentGraphmlByAPerson_thenSocketClose_noDialog_andAHumanCloseStillWarns` (human log, socket graph, the person
re-opens it from Recent, socket close → 0 dialogs; then a File-menu close → exactly 1 dialog, the positive control).
With the `close` declaration alone removed that test is red. Removing ALL three socket-entrance
assignments turns exactly the new test red. `JavaHighlighterLongLiteralTest` 6/6 with the escaped-delimiter colour
spans. `mvn -o clean verify`: 1408 tests, 0 failures, 4 skipped (the frame suite, headless by design). Strict docs
and the sweep pass.

**Reviewer must still check.** The reviewer's own project-first sequence, which this test approximates without the
project step. Whether `openLogs` and `discoverGraphs` should also declare the socket audience (they raise no
warning today, so they were left alone). The CR-only line-comment limitation is still present, unchanged.

## ☑ reviewed 2026-09-16 · Response to the second-pass review: R2-B1, R2-B2 fixed; R2-F4, R2-F5, R2-F6 done; R2-F3 open · `b662bc33`, based on `657ab6e4`

**Verdict: NOT READY for 1.13.1.** Independent
[third-pass review](review_analyser_1.13.1_pass3_2026-09-16.md): R2-B1 and R2-B2 close for their
reported sequences, both with independently failing mutants. The exact-commit CI display job
really executed 3 tests with no skips, and the local display run agrees. R3-B1 is a new mixed-audience
regression: socket-open project → human-open log A → socket-open mismatching graph B → socket-close
graph waits on a modal, unlike the previous candidate. Colour-span and viewport fixes hold for
the reported examples; escaped text-block delimiters remain cosmetic follow-up R3-F2. R3-F3 notes
that the deferred refresh finding is linked to M44.3 in the handoff but not concretely filed in
the canonical tracker. Full verify: 1,406 tests, 0 failures/errors, 3 display-only skips.

Response: [handoff_analyser_1.13.1_pass2_response_2026-09-16.md](handoff_analyser_1.13.1_pass2_response_2026-09-16.md).
Review: [review_analyser_1.13.1_pass2_2026-09-16.md](review_analyser_1.13.1_pass2_2026-09-16.md).

**What & why.** R2-B1 — the load-start bookkeeping (`status`, `setBusy(true)`) moved from `openFile` into
`openFileWithReader`, the one asynchronous entrance for a local file, so the explicit-`format` verb path starts the
pending lifecycle too. R2-B2 — `onLoaded` sets `sessionInteractive = !loadFromSocket`, so the effects an arrival
raises are rendered for that request's audience; a non-interactive warning lands in the status bar instead of a
modal. R2-F4 — the scanner ends a literal at LF/CR even after a backslash and colours a text block whole. R2-F5 —
`showSelectedProcessor` navigates only when the processor pane was re-read or its name changed; caret accessors for
tests. R2-F6 — `ci.yml` gains a `ui-frame` job that runs `PairingDuringLoadFrameTest` under xvfb and fails if the
suite skipped. R2-F3 (a refresh observation can close the graph that replaced the one judged; pre-existing) is NOT
fixed here: it needs the session processor to tell a real arrival from a refresh, which is M44.3's shape.

**Verified.** `PairingDuringLoadFrameTest` now has three cases (auto-detect, explicit `format: "yaml"`, fresh-window
socket graph + mismatching socket log with a dialog watchdog): 3/3 green on a display. Mutants: R2-B1 (bookkeeping
back on `openFile` only) → `theSameThroughAnExplicitReaderFormat` red; R2-B2 (audience not taken from the arrival) →
`freshWindow_…WithoutADialog` red (the watchdog saw the dialog). `JavaHighlighterLongLiteralTest` 5/5 with colour-span
assertions for backslash-LF, CR and a text block. `SourcePanelRootChangeTest` 6/6 incl. the caret test.
`mvn -o clean verify`: 1406 tests, 0 failures, 3 skipped (the frame suite, headless by design). Strict docs and the
sweep pass.

**Reviewer must still check.** The `ui-frame` CI job on its first run (it must NOT skip). Whether a human File-menu
close after a socket load should re-arm the dialog (the flag is now set by arrivals and project transitions; a
menu close between them inherits the last one). R2-F3 stays open and is filed against M44.3.

## ☑ reviewed 2026-09-16 · Source panel `StackOverflowError` on a generated processor (JBang 1.13.0) · `6b0a5258`, based on `96880f2b`

**Verdict: overflow fix accepted, colouring follow-ups remain.** Independent
[second-pass review](review_analyser_1.13.1_pass2_2026-09-16.md): all three test methods fail with
`StackOverflowError` against 1.13.0 and pass with the candidate. R2-F4 records escaped-newline,
CR-only and text-block limitations; ordinary literal/comment spans match. This is not approval
of the whole release; the lifecycle blockers in the next entry remain.

**What & why.** `JavaHighlighter.STRING` was `"(\\.|[^"\\])*"|'(\\.|[^'\\])*'` — a group with an alternation
under `*`, which `java.util.regex` matches by recursing per character (the report's stack: `Loop → GroupHead →
Branch → CharProperty → BranchConn → GroupTail`, repeated). The bundle's generated `MarketProcessor.java` holds ONE
apostrophe, in a javadoc, with 4,115 characters after it and no closing quote: the char-literal alternative scanned
all of them and overflowed the EDT stack. Reproduced on the real file through the highlighter (a probe, not a
test). Fix: `applyLiterals` scans string/char literals by hand, stops at a newline (a Java literal cannot cross one),
and colours nothing for an unterminated literal. The other regexes with a group under a repetition
(`SourceNavigation` modifier prefixes, dotted identifiers in `GraphPanel`/`TemplateClient`, `PathForm.ANCHOR`)
iterate per token, not per character, and were left alone.

**Verified.** `JavaHighlighterLongLiteralTest`: a 50,000-character literal, a literal with escaped quotes, and the
trigger shape (an apostrophe in a comment followed by 3,000 lines) — all three threw `StackOverflowError` against the
regex and pass against the scanner; the third also asserts the body after the apostrophe is not painted as a
literal. The real generated file renders (23,310 chars). Full verify, strict docs, sweep before commit.

**Reviewer must still check.** The highlighter's colouring of literals against the previous regex on ordinary
sources (a quote inside a line comment is coloured string then overridden by the comment pass, as before). Whether
the unit test's 3,000-line file is enough to overflow on every JDK's default EDT stack — it did here.

## ☑ reviewed 2026-09-16 · Response to the 1.13.1 review: B1, B2, F3, F4 fixed; F5 corrected · `96880f2b`, based on `26c10d45`

**Verdict: NOT READY for 1.13.1.** Independent
[second-pass review](review_analyser_1.13.1_pass2_2026-09-16.md): B2/F4 and §4's F5 attribution
accepted; the original B1 sequence and fresh final verdict now work. R2-B1 finds explicit-format
opens bypass pending state; R2-B2 finds a new modal warning on a fresh-window socket load.
The failed-load restore was correct in both local probes, including a graph changed during the
load. Full verify: 1,401 tests, 0 failures/errors, 1 skipped; the display override independently
ran the frame test 1/1. The author account below remains evidence to review, not acceptance.

Response: [handoff_analyser_1.13.1_response_2026-09-16.md](handoff_analyser_1.13.1_response_2026-09-16.md).
Review: [review_analyser_1.13.1_2026-09-16.md](review_analyser_1.13.1_2026-09-16.md).

**What & why.** B1 — the verdict retires when a load starts (`setBusy(true)`) and when a graph is opened during
one; `context.graphPairing` says `pending` + `loading` while `loadInFlight`; a failed load restores the session's
still-true verdict (`setBusy(false)`). B2 — `ReadService.traceOnly` decides per instance over every contribution:
`tracedOnly` needs a wire marker and no entry anywhere; `traceLikeOnly` (legacy only) needs a `method` entry and
nothing but `thread`/`method`; schema updated. F3 — `repairLoadedGraph` builds the session driver on the first log
arrival instead of returning a null verdict. F4 — `SourcePanel.rerenderIfChanged` always re-renders a pane showing
a miss, so the placeholder names the roots now searched; `processorPaneText()`/`nodePaneText()` for tests. F5 —
handoff §4 rewritten to the client-side boundary; the compiler spec on branch
`docs/diagnostics-entry-point-rendering` already sits there.

**Verified.** `PairingDuringLoadFrameTest` — the reviewer's real-frame two-call sequence on a fresh window with no
project: echo pending, context pending with no `applies`, final B/B verdict. **Skipped under Maven** (the pom
forces headless for every run) and run with `-Djava.awt.headless=false -DargLine=…` on a machine with a display.
Mutation controls, all red: B1 (context reports the old verdict, no invalidation), F3 (no lazy driver → no verdict
within 20 s), F4 (content-only re-render → node placeholder names the old root). `ReadServiceTest` carries both B2
counterexamples in the legacy grammar and the marker+value case under the declared grammar, with a positive bare-marker
control and a business-`method` control. Full `mvn -o clean verify`, strict docs, rule-1 sweep before commit.

**Reviewer must still check.** The frame test cannot run on CI; it is a developer-machine test and its skip is
silent there. Whether `traceLikeOnly` should require `thread` as well as `method` (it requires `method` only,
matching `AuditTrace`). The skill wordings from the review's other dispositions — "every final field" narrowed to
eligible instance fields; the Mongoose capture roll is a roll, not a one-day retention; fresh capture location over
deletion — are NOT changed here: each is a canonical-skill edit plus an index re-pin, an owner's call.

## ☑ reviewed 2026-09-16 · Session-report items: pending pairing echo, `tracedOnly`, three skills · based on `c443c865`

**Verdict: NOT READY for 1.13.1.** Independent [review](review_analyser_1.13.1_2026-09-16.md): B1 stale
pairing in context and B2 false `tracedOnly` assertions block; F3 records the pre-existing fresh-session
gap and F5 corrects the compiler attribution. The author account below is retained as reviewed history,
not as acceptance of those claims.

**Source.** An agent's session report on the 1.13.0 template bundle (off-repo, the owner's copy). Its analyser
items were triaged: one defect, two gaps, one policy kept (screenshot never overwrites — `ExportGuard` is
deliberate), and the compiler item is already in 1.0.67 (FLX-1009) but the remote path throws the LEGACY
message (`DiagnosticException(diagnostic, legacyMessage)`; the http client rethrows `resp.getError()`), so an
agent never sees it — a compiler/generator-http item, recorded in the report to the owner, not fixed here.

**What & why.** (1) `open {log, graphml}` echoed a pairing judged against the PREVIOUS log or none, because
`openLog` hands the load to `Background.run` and returns; `openGraphml` judged synchronously. Now the log echo
says `loading: true`; `ActionExecutor.doOpen` replaces the verdict keys with `PAIRING_PENDING` when the log
opened in the same call is loading; `MainFrame.openGraphml` echoes pending itself while `loadInFlight`, so a
graph opened in the NEXT call is covered too. `onLoaded → repairLoadedGraph` already re-judges. (2) `read …
fields` adds `tracedOnly` (`ReadService.traceOnlyNodes`: legacy text grammar with only `thread`/`method`
keys, or a bare binary trace marker); the verb schema says so. (3) Skills: `spring/add-a-node` (description
broadened to body-only logging; constructor-mapping rule before regenerate; a heading for the logging
section), `mongoose/run-mongoose-server` (cumulative export; confirm a clean stop; edit input only between
stop and start), `common/load-audit-log` (the echo cannot judge; context is the authority).

**Index and test.** `m19-skills/2/index.json` is re-pinned IN THE FOLLOWING COMMIT (its `revision` must name
the commit containing the bytes, so it cannot be the same commit); the commit with the bytes is red on exactly
`aPUBLISHEDindexMustPinARevisionContainingEverySelectedByte` and green again one commit later — pushed
together, CI sees the tip. `publishedM19IndexPinsTheAcceptedMongooseSubsetAndItsExactBytes` now hashes the
bytes AT v1's revision (`git show`) instead of the worktree: it was only ever true because neither v1 file had
changed since v1, and v1 stays byte-pinned exactly as the README says. Bundles keep `canonical@48b0e0a7` until
the playground vendors the new revision.

**Verified.** `OpenEchoPendingPairingTest` (rewrite when the adapter says loading; control: a synchronous
adapter keeps its verdict; a graph opened alone is never rewritten), `ReadServiceTest.tracedOnlyNames…` (with a
control record where the same node logs a value), `CanonicalSkillsTest` string pins intact. Full
`mvn -o clean verify`, strict docs, rule-1 sweep before each commit.

**Reviewer must still check.** The live two-call race on a real frame (`open {log}` then `open {graphml}`
within the load): the second echo must say pending and the status bar must show the new log's pairing after
it lands. Whether `tracedOnly` should also appear in the raw-text (`fields` omitted) shape — left out because
the raw text already shows `thread`/`method`.

## ☑ reviewed 2026-09-16 · Source panel: stale "No source to show" after a project switch · based on `7960462d` (v1.13.0)

**Verdict: original processor switch and live project hint accepted; node-pane follow-up F4 remains.**
Independent [review](review_analyser_1.13.1_2026-09-16.md) reproduced the missing→missing node placeholder
retaining old roots. Source re-read overhead on large/remote files was not measured.

**What & why.** Reported on the deployed 1.13.0: the processor pane said *No source to show … Source root
searched: &lt;an older checkout's root&gt;* while the nodes pane resolved and `context` already reported the new
project's root and `processors[0].source=found`. Cause: `SourcePanel.navigate` skips an unchanged class name, so
after a project switch that selects the SAME processor the pane was never re-read; and the placeholder listed
roots without saying which project they came from, while the offer to load the log's own project had gone by as
a status-line note (M35.7: socket-driven opens never show the dialog). Fix: `showSelectedProcessor()` re-reads
either pane whose file content changed with the roots; `navigate` retries a miss on every navigation (history is
still pushed only for a new name); the placeholder text is a pure static (`nothingToShowText`) that appends a
frame-supplied hint — the active project and root, and any pending project offer with both remedies.

**Files.** `ui/SourcePanel.java`, `ui/MainFrame.java` (`sourceLookupHint()`), `CHANGELOG.md`,
`src/test/java/…/ui/SourcePanelRootChangeTest.java`.

**Verified.** Four new tests (headless Swing construction, as `DetailPanelExactClickTest` does): same name +
new roots → re-read; new roots without the file → stale source dropped; placeholder ordering roots → origin →
remedy; blank hint adds nothing. Mutation control: with the re-read and the miss-retry removed the two switch
tests fail (2/4 red), so they pin the behaviour. `mvn -o clean verify`, strict docs build and the rule-1 sweep
run before commit (see the commit).

**Reviewer must still check.** The live surface: open a log over the socket while another project is active,
then load the log's project — the processor pane must show the file without a click, and before loading, the
placeholder must name the pending project with `File ▸ Open project…` and `open {project: …}`. Also whether
re-reading the node pane on every `onConfigChanged()` (a file read per pane) is noticeable on large files.

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-09.md`](completed/unreviewed-changes-2026-09.md) and, earlier, [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._
