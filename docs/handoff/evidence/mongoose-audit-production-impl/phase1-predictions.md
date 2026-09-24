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
