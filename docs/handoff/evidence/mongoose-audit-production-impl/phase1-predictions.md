# Phase 1 predictions, recorded BEFORE the trials

Written before any code was changed or any test run, so a wrong prediction stays on the record rather
than being quietly absorbed. Spec `fda01845`.

## P1 · mongoose-plugins#39 — the export escape

**The defect, predicted shape.** `YamlContainerWriter.document(String yaml)` writes the record text
unchanged. Any line inside that text which trims to `---` therefore terminates the document early.

**Predictions:**

1. **P1.1** — Before the fix, an export containing a record whose `eventToString` carries a separator
   line plus marker lines will read, in the published analyser, as **more records than were written**,
   with the injected marker recognised. *(Already observed once at spec-writing time: 3 written, 4 read,
   two runs, `missing_records`. I expect the same shape here, through the real exporter rather than a
   hand-framed file.)*
2. **P1.2** — The **indented** (`  ---`), **tab** (`\t---`) and **CR** (`---\r`) variants will each
   separate too, because both framers trim before comparing. I have read that predicate but not run it.
3. **P1.3** — A **node value** carrying the payload will behave identically to `eventToString`, since
   the framing break happens before any field is parsed.
4. **P1.4** — After escaping, the record count and the stream-end verdict will be **identical to the
   same export with a benign payload** (V1).
5. **P1.5** — The escape will be visible in the exported text: the logged value changes. That is V1's
   stated price, and I expect to have to assert the escaped form explicitly rather than assert the
   original round-trips.

**What I am unsure of, recorded now:**

- **U1.1** — whether escaping inside the YAML *value* keeps the document parseable by the analyser at
  all: the value is emitted by the runtime, already formatted, and I do not know whether it is a plain
  scalar or a block literal in every case. If it is a block literal, changing the line may need care to
  keep indentation valid.
- **U1.2** — whether `handleAuditExport`'s JSON-lines branch needs the same treatment. My expectation is
  **no** — a newline in a JSON string is escaped by the mapper — but I have not checked.

## P2 · MA-5 — capture fans out and restores

1. **P2.1** — Capturing `MongooseServer.logRecordListener` at `attach` will be enough for fan-out,
   because `attach` runs during registration, before `start` replaces the listener.
2. **P2.2** — MA-5.4's re-registration acceptance will need the **configuration path**, not
   `addEventProcessor`, because the latter does not call `init()` on a running server (round 5, F8).
3. **P2.3** — Two servers in one JVM will each restore their own listener **only** if the capture is
   taken per processor at attach; re-reading the static at stop will restore the wrong one. I expect a
   test that boots two servers to fail against a static read.

**Unsure:**

- **U2.1** — whether `stopRecording` has access to the per-processor captured listener at the point it
  needs it, or whether the sink has to hold it.

## P3 · Analyser MA-0, MA-6 reader half, MA-8

1. **P3.1** — MA-0 keyed on `index.size() == 0` before the early return will make all six empty shapes
   raise the finding, with the stream-end state unchanged.
2. **P3.2** — `isWarning()` will pass for the wrong reason on the marked rolled set, which is why the
   acceptance names `firstWarning()`. I expect to be able to demonstrate that difference in a test.
3. **P3.3** — MA-8's annotation will need the control record even when a filter excludes it, so the
   coverage service will need the unfiltered record stream for level changes.
4. **P3.4** — Routing producer findings to the report (D-MA0c) touches `ReportRenderer`, which carries
   none today; I expect this to be the largest single diff in phase 1.

**Unsure:**

- **U3.1** — whether `SOURCE_DAMAGE` ordering (MA-0.6, damage first) is already enforced somewhere, or
  whether ordering has to be introduced.
- **U3.2** — whether MA-8 can distinguish a genuine `EventLogControlEvent` record from a content record
  that merely looks like one, before MA-7's writer half ships. The spec says annotate-never-excuse
  partly for this reason; I may not be able to close it fully in phase 1.

## P4 · Final-review round — recorded before these trials

The final review returned four required items. Two of them are measurements I had not made, and the
review states its own results for both. **I am recording what I expect BEFORE re-running them**, so
that agreeing with the reviewer is a result rather than an assumption. Written against branch head
`7138dc6f`.

1. **P4.1 — the `RecordParser` narrowing.** A record indented with U+3000 (ideographic space) or
   U+2003 (em space) will, after the F1 consolidation, lose `event` and its node logs, because
   `AuditText.strip` leaves the mark in place and `splitScalar`'s key then fails `isIdentifier` at
   character 0. I expect `kind` to stay **OK, not PARSE_ERROR**, because `eventLogRecord:` sits at
   column 0 and so `sawFields` is still set — which makes the loss *quiet*, which is the part worth
   pinning. I expect `ProducerDiagnostics` to raise **NO_NODE_LOGS**, and its message to blame
   `addEventAudit()`, which is the wrong cause.
2. **P4.2 — the same file before the narrowing.** Under `String.strip()` it parsed fully. I have not
   run this; it follows from `strip()` removing every Unicode space, and I expect the test to have to
   assert the *current* behaviour rather than a diff, since the old code is gone.
3. **P4.3 — the guard's remaining spellings.** The review planted six that get past the text match. I
   expect all six to reproduce, and I expect that moving the guard onto **integer literal values**
   closes five of them — lowercase hex, the digit separator, lowercase byte constants, signed bytes
   and octal are all literals — and does **not** close a constant expression (`0xFE00 + 0xFF`), which
   is only decidable by evaluation, not by reading a token.
4. **P4.4 — the rebase.** I expect exactly one test to fail on the rebased tree,
   `everyByteSensitiveFixtureStillCarriesItsTrailingBytes`, and to pass once the spike file's bytes
   are restored. I expect **no** conflict, since no file is touched on both sides.

**Unsure:**

- **U4.1** — whether a literal-value guard can be written without a parser. I intend to scan tokens
  and evaluate each integer literal, which is a lexer, not a parser; I do not yet know whether that
  is enough to avoid false positives on ordinary code (a `0xBF` in unrelated byte handling would now
  be caught by *value*, where before it was caught by *text* — same answer, different route).
- **U4.2** — whether restoring the spike file is the whole of F1, or whether my branch rewrote bytes
  in any other pre-existing evidence file. The review says it is the only one; I have not verified
  that independently.

## P5 · Independent review — recorded before these fixes

Review `2d7ac12f` (branch `review/mongoose-analyser-independent-2026-09-24`) returned three High and
three Medium findings against `0b7076fd`. **All six reproduced on my checkout before any change**, with
the reviewer's own probe, against the published `svc-admin-web-1.0.45.jar` (SHA-256 `68398176…3d`,
matching the review) and runtime 1.0.16. The probe output is kept as evidence
(`probe-0b7076fd.txt`, beside this file).

**A finding of my own, from reading the runtime for F4 (rule 6), before any fix.** `EventLogManager.
calculationLogConfig` (1.0.16) applies a change only when the processor's `logRecord.groupingId` is null
or equals the change's `groupId`; then `sourceId == null` sets **every** node. So `groupId` is **not**
node membership — it gates the whole change by the processor's grouping, which every runtime record
writes as `groupingId:`. MA-8 as shipped read `groupId` as "a group of nodes whose members the log cannot
name". That was an inference, not a reading, and it is exactly the failure rule 6 names.

1. **P5.1 — F1.** Restricting a BOM-before-separator to **file offset 0**, in both framers, makes the
   real-runtime payload read as **1 record, UNKNOWN** with both `recordEndTime` settings, on heap and
   mapped, because a payload can never sit at the file's first byte — the container writes that. I expect
   the pre-existing `UNSEPARATED` misreading of the payload to remain; it is on base too and is not mine
   to fix in this change.
2. **P5.2 — F1's cost.** `cat a.yaml b.yaml` with a BOM'd `b` stops separating at the join and raises
   `UNSEPARATED`. That is base behaviour and is loud rather than wrong; I expect to have to change at
   least one round-3 test that asserted the concatenation separates.
3. **P5.3 — F2.** Comparing **byte** lengths rather than decoded lengths, and carrying held-back bytes as
   a pending tail, makes the `C3` case read `pending=1, UNKNOWN` with identity cleared. I expect an
   invalid lead byte (`C0`, `F5`) and an invalid second byte after `E0`/`ED`/`F0`/`F4` to have to throw
   rather than wait, and I do not yet know whether any existing test depends on the old quiet wait.
4. **P5.4 — F3.** Skipping leading **plain** `---` lines (ASCII whitespace only, no BOM) before the first
   content line clears the healthy binary record and still names a genuinely headerless one.
5. **P5.5 — F4/F5 together.** Moving MA-8's intervals from **time** to **record order**, and applying the
   runtime's real rule (grouping gate; `sourceId == null` means every node), fixes F4 (a global restore
   closes the interval), both F5 cases (the exact boundary, and an empty selection) and the untimed-future
   case in one change, because an empty selection has no rows and a control after the selected rows
   cannot precede any of them. I expect **several** existing `CoveragePerNodeLevelTest` assertions to
   change, since they pin time-based wording and the group-membership sentence.
6. **P5.6 — F6.** The MA-6 message can be made conditional without knowing the container state.
7. **P5.7 — O1.** `0_177377` goes to the decimal path because the lexer checks for an octal DIGIT after
   the `0`, not an underscore.

**Unsure:**

- **U5.1** — whether a run boundary (a stream-end marker) inside a quiet interval can be surfaced without
  changing `StreamEnd`. I intend to expose marker positions through `LogStore` with an empty default.
- **U5.2** — how the `recordEndTime=true` case splits into two records today. I expect it is the same BOM
  separator, and that the runtime's closing brace is what differs; not verified.

## P6 · Re-review — recorded before these fixes

Re-review `cd063e89` (branch `review/mongoose-independent-rereview-2026-09-24`) against `6998fcc8`: one
High, three Medium. **All four reproduced** with the re-review's own probe before any change; its output is
kept beside this file (`rereview-probe-6998fcc8.txt`).

1. **P6.1 — RR-1.** Retiring the identity and the completeness claim BEFORE decoding, and forcing UNKNOWN
   when the decode throws, makes `C0` after a marker read `UNKNOWN, identities=0, pending=0` and still
   throw. I expect no existing test to depend on the old post-throw state, because none asserted it.
2. **P6.2 — RR-2.** Parsing the rendering by its FIXED separators — `EventLogConfig{level=`,
   `, logRecordProcessor=`, `, sourceId=`, `, groupId=`, `}` — each exactly once and in order, keeps a
   value's commas, braces and spaces, so `riskMonitor, DEMO`, `riskMonitor}DEMO` and ` riskMonitor ` stop
   addressing riskMonitor, and `alpha, DEMO` stops matching grouping `alpha`. An EMPTY source is a node
   named "", which is what the runtime's exact map lookup does with it. **The literal `null` cannot be
   separated from Java null by any reader of this text**; I expect to keep reading it as "no node" and to
   say so in the sentence, not to claim a fix.
3. **P6.3 — RR-3.** Giving every record a context from its OWN `groupingId:` line — a value, declared
   null, or ABSENT — and letting a change explain, or be closed by, only records of the same declared
   context closes both mixed-processor cases and the rolled one. An absent grouping cannot establish
   applicability, so it must be qualified rather than read as ungrouped. **This does not establish
   processor identity**: two ungrouped processors share a context and will still be read as one stream.
   I expect to disclose that in the annotation note, not to solve it.
4. **P6.4 — RR-4.** Splitting the sentence into what holds within the run and what is conditional after a
   boundary; a scope wholly after the boundary gets no definite suppression claim.

**Unsure:**

- **U6.1** — whether the analyser's `LogRecord` keeps "field absent" apart from "field says null" for
  `groupingId`. I expect not (`nullLiteral`), and to have to read the raw text.
- **U6.2** — whether any fixture in the corpus writes `groupingId:` on some records and not others, which
  RR-3's rule would now treat as two contexts.

## P7 · Second re-review — recorded before these fixes

Second re-review `68660535` (branch `review/mongoose-second-rereview-2026-09-24`) against `3d41c3a7`: two
Medium, three Low, five optional. Recorded before any change.

1. **P7.1 — S1.** Returning `-1` from `appendFrom` whenever a decode SUCCEEDS after `liveReadFailed` was set
   makes the longer-rotation case reload instead of reading as an append, so the store never shows COMPLETE
   beside the fault. I expect no existing test to depend on a successful read after a failure, because none
   performs one.
2. **P7.2 — S2.** Composing *applied* and *survived* into one condition, and saying "records sharing this
   grouping" wherever applicability is NOT_ESTABLISHED, changes wording that two existing tests pin: the
   run-boundary pair uses `control()`, which declares `groupingId: null`, so those stay on the YES branch and
   should NOT change. I expect only `ControlAddressAndScopeTest.anAbsentGroupingIsNotADeclaredNull` to need a
   wording update, if any.
3. **P7.3 — S3.** Leading with the ambiguity means the `"null"` sentence no longer contains the words
   "every node's audit level" as its subject. `positiveControls_aRealPerNodeAndARealGlobalChange` asserts
   `contains("every node")` for a real global change — which is the SAME rendering as `"null"`, since the log
   cannot tell them apart. So I expect that test's assertion to have to accept the conditional form too; if I
   find myself weakening it, that is the limit showing, not a regression.
4. **P7.4 — S4.** `E2 82` then `C0`: the first poll holds 2 pending bytes; the second throws in
   `decodeCompletePrefix` (C0 cannot begin a character) and must leave `trailingRecordsPending() == 0`.
5. **P7.5 — S5.1.** A complete rendering on a `Quote` record makes the event type the only thing refusing it,
   so `isControlEvent → true` turns the test red.
6. **P7.6 — O1.** Moving the live-read fault to `sourceDiagnostics()` changes the RR-1 test's assertions on
   `completenessIsNote()` and `completenessDiagnostics()`; the state stays UNKNOWN.

**Correction recorded now, before the report is written:** my round-3 report said "three negative tests —
the lookalike and fully-qualified event-name tests — pass or fail on grouping". Wrong twice: the
fully-qualified test is a POSITIVE test (its failure is how the dependency was found), and the reviewer's
witness run could reproduce the grouping dependency for only one negative test, the lookalike.
`aRecordThatIsNotAControlEvent…` never depended on grouping; it was refused by `parse()` (S5.1).

**Unsure:**

- **U7.1** — whether `pollFollow`'s catch (O2) can refresh producer diagnostics without re-entering the load
  path; it is Swing, so it will be READ plus a source-text check, not a unit test.

## P8 · Third re-review — recorded before these fixes

Third re-review `8514f91b` (branch `review/mongoose-third-rereview-2026-09-24`) against `fc9b1f9c`: two Low
required, six optional. All six optional items are planned, and any I end up skipping will be said so. Recorded
before any change.

1. **P8.1 — R1.** Describing a closing change rendered `sourceId=null` as "records a change to INFO that names no
   node — or a node literally called "null"" changes no existing assertion, because no test pins the " for every
   node" suffix. The reviewer's four-record log will read the disclosure in the closing clause. The window's end
   stays where it is: closing it on the ambiguous change is the conservative direction, since it only withholds
   annotations after that record, and the sentence will say that only the no-node reading would end it.
2. **P8.2 — R2.** Adding `assertFalse(note.contains("this processor"))` to
   `positiveControls_aRealPerNodeAndARealGlobalChange` makes the declared-branch mutant go red there; nothing else
   changes.
3. **P8.3 — O-A.** For a node literally named "null", dropping the *named no node* premise makes both readings
   conclude the same way, with no "otherwise" clause. No existing test uses a node named "null".
4. **P8.4 — O-D.** Making the YES parenthetical its own sentence ("It was addressed to processor grouping 'alpha',
   which is…") keeps `aChangeAddressedToAnotherGrouping…`'s `contains` assertion passing, because the quoted words
   are unchanged.
5. **P8.5 — O-C.** "within that run" becomes "within the run it was made in", which breaks the two tests that pin
   "so within that run" / "Within that run, if": `aScopeSpanningARunBoundaryIsDefiniteOnlyBeforeIt` and
   `notEstablishedAndSpanningABoundaryConditionsBothHalves`.
6. **P8.6 — O-B.** Refreshing on a failed tick only when the source diagnostics differ from those the cached
   findings were built from keeps the fault reaching `context` on a growing file (the O2 probe stays green) and
   stops the per-tick rebuild. This is Swing; its evidence is the jar probe plus the frame suite, not a unit test.

**Verified before writing this, by me:** O4's premise. At mongoose `2c4192e`, `MongooseServer.java:116` declares
one `private static LogRecordListener`, and `addEventProcessor` installs it with `setAuditLogProcessor` at `:758`
for every processor it adds (READ, source).

## P9 · Fourth re-review — recorded before these fixes

Fourth re-review `1c706216` (branch `review/mongoose-fourth-rereview-2026-09-24`) against `74d5a009`: three Low
required, three optional. All six are planned. Recorded before any change.

**R-B's premise, checked by derivation rather than taken from the review.** The runtime applies a change when the
processor's grouping is null or equals its `groupId`. With a DECLARED context, `applies()` is determinate and the
closing change shares `c`'s context (`annotationFor` requires it), so a closing change that affects the node is YES:
it applied. With an ABSENT context: if `c.groupId` is null, `c` applied only under a null grouping, which accepts
every change; if the two `groupId`s are equal, the condition is the same. **Only when the context is absent,
`c.groupId` is non-null and differs from `next.groupId` — including a null `next.groupId` — can `c` have applied and
`next` not.** That is exactly the review's condition.

1. **P9.1 — R-A.** Extending `aNodeNamedNullIsSetUnderBothReadings` with a closing `"null"` change makes the
   `if (false)` mutant at the node-named-"null" closing branch go red there; nothing else changes.
2. **P9.2 — R-B.** Disclosing the closing change's addressed grouping, and that its applying is not established,
   changes no existing assertion, because no test builds an undeclared context with two different `groupId`s.
3. **P9.3 — R-C.** A parameterised matrix test — {per-node, "null"} source × {declared null, absent, alpha=alpha,
   absent with gid alpha} × {none, spanning, wholly after} × {none, per-node, "null"} closing, plus a node named
   "null" — asserting no "this processor" on every non-null note will be green on first run (the reviewer's 110-case
   probe found none), and red for a plant in any `closing()` literal and in the post-marker text. Some combinations
   will legitimately yield no annotation (a closing change can close the window before a later in-view record); the
   test must count its non-null notes and fail if too few, so it cannot pass by annotating nothing.
4. **P9.4 — O-1.** "Before the marker" in place of "Within the run it was made in" breaks the two tests and the O-C
   witness that pin the old phrase, and nothing else.
5. **P9.5 — O-2 and O-3** are a comment and probe-file headers; no test changes.

## P10 · Fifth re-review — recorded before these fixes

Fifth re-review `79a51d27` (branch `review/mongoose-fifth-rereview-2026-09-24`) against `98148175`: two Low
required (R5-1, R5-2), three optional (O5-1–O5-3). All five are planned. Recorded before any change.

**R5-2's class, re-read clause by clause before fixing.** One more instance is predicted, in the opening for a
node literally named "null": "— either way it sets this node" is appended whatever `applies()` says, so with an
absent grouping it asserts the setting the next sentence says is not established. It is fixed with R5-2 ("either
way it addresses this node") and held to the same regression.

1. **P10.1 — R5-1.** Adding `G("null", "alpha")` and a closing-`groupId` dimension {same, "beta", none} grows the
   matrix to 3 openings × 5 groupings × 3 boundaries × 7 closings = **315 logs, all 315 annotated** (the closing
   change sits after the in-view records, so it never removes the annotation). With the guard
   `processor(?! grouping)` it is green on the fixed code, and red at `noBranchOfTheSentencePresumesAProcessor`
   for a plant at the declared-null YES note, in each of the three `closeOpen` strings, and for "for its processor"
   in the per-node closing.
2. **P10.2 — R5-2.** "this log records a change setting riskMonitor's audit level to WARN" for NOT_ESTABLISHED
   breaks no existing assertion: no test pins "this log sets" for an absent grouping. Restoring the old opening is
   red at the matrix's new absent-grouping assertion and at `aClosingChangeWhoseApplyingIsOpenSaysSo`.
3. **P10.3 — O5-1 and O5-2.** "It holds at least until" and the de-duplicated disclosure change no assertion except
   none: R-B's pinned phrase ("addressed to processor grouping 'beta'; whether that applied here is not established
   either") is the per-node branch, whose wording is kept apart from "at least".
4. **P10.4 — O5-3.** Naming the marker where it is first used breaks the two tests that pin "before the marker":
   `CoveragePerNodeLevelTest.aScopeSpanningARunBoundaryIsDefiniteOnlyBeforeIt` and
   `ControlAddressAndScopeTest.notEstablishedAndSpanningABoundaryConditionsBothHalves`; nothing else.
5. **P10.5.** No `MainFrame` change; the headless suite grows by zero tests (R5-1 and R5-2 extend existing tests)
   and stays at 1,980 run.

## P11 · Sixth re-review — recorded before these fixes

Sixth re-review `1c3173ae` (branch `review/mongoose-sixth-rereview-2026-09-24`) against `4bb68d08`, confirmed
still reproducible on the main merge `e82808e7` by the integration review (`99f9ec47`): three required (R6-1, R6-2,
R6-3), four optional (O6-1–O6-4), plus the integration review's optional status-prose item. All planned. **The owner
has asked for no mutation witnesses this round**: regressions are ordinary tests, and every claim below is checked
by running them, not by planting.

1. **P11.1 — R6-1.** Recording unreadable control records (an `EventLogControlEvent` whose `eventToString` is
   missing or does not parse) with their context, and closing a window at the first one in the same context,
   makes the reviewer's four-record log (WARN, Quote, unreadable control, Quote) annotate record 2 with "It holds
   at least until record 3 …, a control record this reader could not read" and leave record 4 unannotated. It
   breaks no existing assertion: no current test contains an unreadable control record.
2. **P11.2 — R6-2.** When a stream-end marker lies between the change and the record that closes it, the closing
   clause becomes "The next change to it in these records is at record N …, which …" and never says "holds". The
   reviewer's six-record log loses "It holds until record 4". No existing assertion breaks: the only tests that
   pin "It holds" have no marker between the change and its closer.
3. **P11.3 — R6-3.** Tightening the two loose reach phrasings to the reviewer's
   (`"If the change at record 1 (logTime 1) applied here, riskMonitor's"` and `"the log renders both identically. "`)
   keeps the matrix green, because each is produced by exactly one branch; the loose `"; otherwise this change
   explains nothing here"` entry is replaced by the no-boundary-specific `"applied here, riskMonitor's lines below
   WARN are not in this log; otherwise"`.
4. **P11.4 — the matrix.** Adding an "unreadable" closing (one variant) grows it from 315 to **360** logs, all 360
   annotated. New assertions: a note whose closer lies after a marker never says "It holds"; a note closed by an
   unreadable record says so and never says "Nothing later". Both green on the fixed code.
5. **P11.5 — optional.** O6-1 (`(?i)` on the processor guard), O6-2 (`bareIt` widened with `(?! here:)`, applied to
   every note that has a closing clause), O6-3 ("end the window there") and O6-4 (CHANGELOG) change no existing
   assertion except the two that pin "either way it would end it there" (the matrix reach entry and its prefix in
   no other test).
6. **P11.6 — suite.** Headless grows by exactly the two new tests (R6-1, R6-2) from 2100 to **2102**; skips stay 98.
   `MainFrame` is not touched, so no display run is required.

## P12 · Seventh re-review — recorded before these fixes

Seventh re-review `43e29973` (branch `review/mongoose-seventh-rereview-2026-09-26`) against `d8512121`: four
required (R7-1–R7-4), two optional wording (R7-5, R7-6), three optional (O7-1–O7-3). The reviewer's probes `R7`,
`R7b` and `R7Matrix` were re-run on `d8512121` first and reproduce **byte-identically** (R7b compiled together with
R7, which it calls). **Owner decisions, taken 2026-09-26:** the conclusion is **bounded** by the change, the window's
end and the grouping (option a); the rule that a closer applied whenever the change before it did does **not**
extend across a stream-end marker; **targeted mutation witnesses** for R7-1–R7-4 only.

**The wording, fixed before coding.** Every "X's lines below WARN are not in this log" becomes
"after record C[ and before E], in <scope>, X's lines below WARN are not in this log", where C is the change's
record, E is the first of the closer ("record N") or the first stream-end marker after the change ("the stream-end
marker preceding record K"), and <scope> is the existing "the records sharing its grouping" / "the records that,
like it, state no grouping". With a premise: ". If <premises>, then after record C …; otherwise this change explains
nothing here". Wholly after a marker: ". If <premises and survival>, then after the stream-end marker preceding
record K[ and before E2], in <scope>, …". A closer past a marker in an ungrouped context is **open** (R7-4). While
the change's own applying is open, a closer clause is prefixed "If the change at record C applied here, it holds …"
(R7-5), and the definite ungrouped closer reads "…, whose change to INFO applied wherever this one did" — so no
ungrouped note says " sets it to ". The cross-marker unreadable clause says "A later control record …" (R7-6).

1. **P12.1 — R7-1–R7-3.** Probes A, B, C, D, E, F, H and I all gain a bound starting "after record <the change>"
   and "in <scope>", and each ends at the closer or the marker. Probe D's "Before record 4" and I's ", so
   riskMonitor's lines" disappear.
2. **P12.2 — R7-4.** Probe L's "which sets it to INFO" becomes "which records a change to INFO addressed to
   processor grouping 'alpha'; whether that applied here is not established either".
3. **P12.3 — R7-6.** Probe J's "The next control record" becomes "A later control record".
4. **P12.4 — breakage.** Every existing assertion that pins an unbounded conclusion breaks and is rewritten, not
   deleted: I expect between 8 and 14 assertions across `ControlAddressAndScopeTest` and `CoveragePerNodeLevelTest`
   (the S2, S3, O-A, spanning, wholly-after, R-B positive-control, R6-1 and R6-2 cases). No other test class breaks.
5. **P12.5 — the matrix.** A "lead" dimension {none, the node logs in a record before the change, another
   grouping's record with the node's line after the change} and a fourth boundary {view wholly before a marker,
   closer past it} grow it to 3 × 5 × 4 × 8 × 3 = **1440 logs, all 1440 annotated**. New offender rules — every
   "not in this log" sentence starts its bound at the change and names its scope; with a marker in the log, a
   pre-marker bound ends at it; no ungrouped note says " sets it to " — are green on the fixed code.
6. **P12.6 — witnesses** (strict protocol: green baseline, reports deleted, a `<failure>` not an `<error>` at the
   named test, SHA-256 restore, clean `git status -- src`, green again; the failing ASSERTION recorded, not just the
   test): reverting each of R7-1, R7-2, R7-3 and R7-4's fix goes red at its own dedicated test's own assertion.
7. **P12.7 — suite.** Headless grows by the two new dedicated tests, 2102 → **2104**, skips unchanged at 98.
   `MainFrame` is not touched. O7-3 regenerates `dependency-reduced-pom.xml` with no other diff.
