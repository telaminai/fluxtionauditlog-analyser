# Un-reviewed changes — REVIEWED entries archived 2026-09 (from `../unreviewed-changes.md`)

_Moved verbatim on 2026-09-16. Relative links resolve from this directory: the referenced responses and reviews
live beside this file._

## ☑ 2026-09-15 · round-11 response, based on `06d7cb28` · INDEPENDENTLY REVIEWED 2026-09-15, accepted with one correction

Response: [handoff_analyser_round11_response_2026-09-15.txt](handoff_analyser_round11_response_2026-09-15.txt).
Independent review: `review_analyser_round11_response_2026-09-15.txt` (core-baseline `design/handoff/`, off-repo):
R11-1, R11-2 and R11-3 accepted as closed CLASSES (118 key-character clicks per arm across six arms, span
integrity over every offset, mutation controls for each new test). One correction required and folded into the
commit: **F1** — the shared formatter quoted legacy text values (`C:\temp\x` rendered `"C:\\temp\\x"` in the
logical view); values are now quoted only when the parsed entry says the reader quoted them, pinned by a legacy
red-then-green test. F2 (three vacuous assertions) reworded. The broader review's B1 (guided-start fallback said
"never ran"; it now says "never logged" and why) and B3 (the CHANGELOG folded to one Added/Changed/Fixed, stale
fixture count and builder version corrected) are in the same commit. B2 (`verify-m43.py` tautology) is deferred
to after the release. Not checked by either review: a rendered PDF, a native popup, the fresh-model guided tour.

The tokenizer now emits complete key positions with its entries; the detail panel consumes those
positions rather than inferring identity from a displayed word. Logical view, step status and both
report-evidence paths share the same name/value formatter. The six-node sparse M61 workload label
and format-spec wording are corrected. The package-generated reduced POM now matches released pins.

Author checks: three regressions failed before production edits; clean verify **1,385/1,385**;
docs strict build; tool smoke **24/24**; packaged session acceptance **22/22**; packaged regression
probes **4/4**; live template download/open **6/6**. Parser semantic parity held over **18 fixtures,
1,584 record/grammar pairs**, comparing the pre-fix parser with the packaged candidate.

Reviewer must attack the new source-position mapping (CRLF, skipped comments, continuations,
repeated nodes/records, escaped keys), the conservative multiline fallback, and the shared formatter
through actual UI/report consumers. The broad review also leaves the fresh-model guided tour and
modal-dialog/theme checks unverified; a green script is not their sign-off.


---

---

---

## ☑ reviewed 2026-09-03 · `180a1e7`, `4c1c9d8` · review response + M48.11

**Review:** [`review_response_180a1e7_4c1c9d8.txt`](review_response_180a1e7_4c1c9d8.txt) — verified by
execution, not by reading: the reviewer's original probe binaries were replayed unchanged against the
fixed scorer and flipped to the correct verdicts; round-48 XML held byte-identical through all four
resolver fixes; M48.11 reproduced independently (PASS 12/12, exit 0).

**Verdict: accepted, with residue.** Follow-up review `review_analyser_response_4c1c9d8_followup.txt`
then found six further defects, all since fixed (G9/G10, the `--json` cycle path, bean-id collisions,
and the derived fixture whose provenance had become record metadata).

## ☑ reviewed 2026-09-03 · `6f45fe4^..4224196` + spec response · resolver, gates and builder spec

**Review:** [`review_spec_builder_component_resolution.txt`](review_spec_builder_component_resolution.txt)
checked the production spec against the target builder module and accepted its architecture with two
Java-8/CheerpJ blockers and three contract corrections. **Response:**
[`review_response_spec_builder_component_resolution.txt`](review_response_spec_builder_component_resolution.txt)
made Java 8 mandatory, separated the desktop Java parser from the browser surface, pinned SpEL and
Stitch byte semantics, and additionally corrected remote transport to the frozen-DTO/side-band rule.

**What & why.** Closing the residue from four review rounds. Three parts:

- **Resolver restructured, not patched.** A review found a cyclic 2-component candidate beating a
  valid 3-component one on minimality — the valid answer was then discarded downstream as
  UNSATISFIABLE. **Constructibility is now a validity constraint inside `solve()`**, and `solve()`
  returns a typed `Resolution` carrying selection, emission order and id allocation. Renderers consume
  it; none re-validates. The same review found `com.alpha.Node` and `com.beta.Node` in one jar both
  emitting `bundleNode` — ids are now allocated once from full class identity and escalate only as far
  as needed, so single-entry-point catalogues keep their committed output.
- **New gates.** `tools/test_tools.py` (24 checks) **wired into CI**; `TrailingWhitespaceTest`
  hardened — `.txt` scanned, audit logs detected structurally rather than by any mention of
  `eventLogRecord:`, a failure if `git ls-files` returns nothing, and every byte-sensitive fixture
  pinned with a missing file treated as failure rather than a skip.
- **Documentation** reconciled: the fingerprint three-way distinction, orientation §6, volatile line
  counts removed. A new canonical production spec places catalogue generation, typed resolution and a
  dependency-free Spring document parser/writer in the existing `fluxtion-builder` jar. It keeps the
  starter as a downstream editor and makes `#{bean.field}` a typed exposed-field reference rather than
  an opaque scalar.

**Verified.** `mvn -q test` green · `python3 tools/test_tools.py` 24/24 · **round-48 XML byte-identical
after every resolver change** · sweep clean.

**Review residue carried as delivery gates, not present claims.** The prototype id-escalation ladder's
fourth level still produces ugly names and has no direct test; `Fluxtion-Consumes` is still unused in
solving; the Java/JavaScript conformance corpus, dependency-tree proof, CheerpJ smoke and Stitch
byte-parity migration are specified but unbuilt; M49's evidence package remains unbuilt.

_No further entries awaiting review; the entries below are historical._

---

_Earlier reviewed entries: [`unreviewed-changes-2026-08.md`](unreviewed-changes-2026-08.md)._

# Retired 2026-09-17 — the 1.13.1 release cycle and the finish-first round (all ☑ reviewed)

_Moved verbatim from `../unreviewed-changes.md`. Every review and response these entries link to moved with them into this directory._

## ☑ reviewed 2026-09-17 · Response to the finish-first review, pass 2: B2 completed (busy follows the gate) · `d78a0144`, based on `c854d28f`

**Independent pass 3: READY — B2 CLOSED.** Original successful/failed project sequences now clear loading
before the discarded reader returns; progress and existing/current-graph pairing verified independently.
Same-project/missing-path controls preserve the open, and both stale success and failure preserve a newer
pending request after project retirement. The nine interleaving tests pass on the candidate; exactly the two
boundary cases fail against the pre-fix jar. Full verify 1,431/0/0/14; local and exact-tip CI display suites
14/14, no skips. Spec/example/barrier follow-ups closed; M44.3b remains deferred. See
[pass-3 review](review_analyser_finish_first_pass3_2026-09-17.md).

Response: [handoff_analyser_finish_first_response2_2026-09-16.md](handoff_analyser_finish_first_response2_2026-09-16.md).
Review: [review_analyser_finish_first_pass2_2026-09-16.md](review_analyser_finish_first_pass2_2026-09-16.md).

**What & why.** `MainFrame.requestProject` calls `syncBusyWithGate()` after the request is dispatched: when the
gate reports nothing outstanding and the adapter still says loading, `setBusy(false)` — which also restores the
session's still-true pairing verdict. So at the project boundary, whether the switch then succeeds or fails, the
busy indicator, `context.graphPairing.loading`/`pairing: pending` and a graph opened next no longer wait for the
discarded reader; a load the gate still expects keeps its state. The discarded worker is not cancelled (spec D-A3:
correctness rests on refusing its result). `spec-session-processor.md`'s decision-table row *load failed* is
qualified (a pending open is superseded by the request, not the outcome — the reviewer's accepted choice). The
docs example no longer shows `applies` beside `inFlight`; a separate pending example was added. Barriers in the
frame tests wait for the stale audit record / the dialog observation instead of a sleep or a status set.

**Verified.** `AsyncOpenInterleavingFrameTest` now 9 cases, 9/9 on a display: the B2 case asserts at the boundary
BEFORE releasing the reader (no `inFlight`, no `loading`, no pending pairing) and again after the stale result; a
new failed-switch case (a profile with a malformed escape reaches the gate and fails to load) asserts the same and
that a graph opened next is judged AT ONCE against the surviving log; a newer-pending-open control (two readers)
asserts an older stale result clears nothing. Mutant (the sync call removed): the boundary cases red. Full verify,
strict docs, links, sweep.

**Review disposition.** Exact-tip CI display counts independently verified: five pairing + nine interleaving
cases, none skipped. Sync after socket `close` is not part of this fix: M44.3b must decide whether close/reset
invalidates an outstanding open before that projection changes.

## ☑ reviewed 2026-09-16 · Response to the finish-first review · `323277b5` + `b8a93533`, based on `5be00dc4`

**Independent pass 2: NOT READY to close M44.3; B2 PARTIALLY fixed.** B1/B3 and export F4 close; F5's
caller-only mutant is red; F6 is correctly filed. The gate retires a project-superseded open, but the frame still
reports loading until the discarded reader returns. A genuine failed switch has the same projection defect and
defers pairing a newly opened graph against the surviving log. Full verify 1,429/0/0/12; local and exact-tip CI
display suites 12/12, zero skips. Six expected failures against the original jar prove the new response tests
detect their guarded defects. Findings, failed-switch policy judgement and before-reader-release probe:
[pass-2 review](review_analyser_finish_first_pass2_2026-09-16.md). This tick means reviewed, not approved.

Response: [handoff_analyser_finish_first_response_2026-09-16.md](handoff_analyser_finish_first_response_2026-09-16.md).
Review: [review_analyser_finish_first_2026-09-16.md](review_analyser_finish_first_2026-09-16.md).

**What & why.** B1 — `onLoaded` and `onLoadFailed` set `sessionInteractive` from the operation's own request
BEFORE submitting its result (its effects run inside that submit). B2 — `OperationGate.onOpenProjectRequested`
retires `inFlightWhat`; javadoc rewritten for the asynchronous boundary. B3 — `onLoadFailed` checks the gate after
submitting: a refused failure keeps its audit record and presents nothing. F4 — `SessionAuditSink.export` writes a
`---` line before every record and one after the last; no header is invented. F5 — `ChartNotesCallBoundaryTest`
paints a real `ChartPanel` at fractional bounds and reads the rule's column from the image against the panel's
`xToPx`; the ledger's earlier "mutant kills the collision test too" claim is withdrawn — the exact mutation run was
`f = (t - (long) from) / ((long) to - (long) from)` (truncation with the rounded-width formula → 489), and the
collision test's result under it was not the claim's basis. F6 — filed as tracker M44.3b, a policy decision.

**Verified.** `AsyncOpenInterleavingFrameTest` (7 cases, latch-controlled `test-slow` reader registered through the
frame's own registry, dialog watchdog): B1 + human-arrival control; B2 + same-project and bad-path controls; B3 +
accepted-human-failure control — 7/7 on a display. Mutants: B1 (audience assignment removed) → both b1 cases red;
B2 (retirement removed) → a b2 case red; B3 (refusal check removed) → the b3 case red. `AsyncOpenReplayTest` gains
the gate-retirement replay (old completion cannot clear a newer pending load). `SessionAuditRecordTest` export round
trip: records == store size, pending and stale refusal as separate records. Full verify, strict docs, sweep.

**Reviewer must still check.** The B2 behaviour on a FAILED project switch (the pending load is superseded at the
request, so it is discarded even though the switch failed — consistent with "a project is a session boundary", but
a choice); the ui-frame CI job now runs 12 display cases (~2 min); `context.inFlight` is documented on the AI page
but the Project panel does not render it.

## ☑ reviewed 2026-09-16 · Note rules drift when zoomed in (owner report, reproduced on the live 1.13.1 over MCP)

**Independent verdict: ACCEPTED with test-coverage follow-up F5.** The note and series mappings agree,
including the right bound; the old-mapping mutant kills the new fractional-bounds test, but not the
existing collision test. Add a call-boundary pin for `ChartPanel` too. See the
[finish-first review](review_analyser_finish_first_2026-09-16.md).

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

## ☑ reviewed 2026-09-16 · M44.3 the asynchronous session driver + M44.3a, N1 and the clamp fixtures, the skill rewording

**Independent verdict: NOT READY for M44.3; goldens and skills ACCEPTED.** Delayed-reader probes confirm
B1 (completion inherits another entrance's audience), B2 (successful project switch leaves a discarded
load permanently pending), and B3 (refused failures still show status/modal). Ordinary slow-load
supersession works. The requested session export/read-back also exposes pre-existing F4: 15 dispatches
merge into one analyser record. Full verify and all five display cases pass, locally and in exact-tip
CI; they do not cover those interleavings. Findings, controls and portable probe:
[finish-first review](review_analyser_finish_first_2026-09-16.md). This tick means reviewed, not approved.

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
