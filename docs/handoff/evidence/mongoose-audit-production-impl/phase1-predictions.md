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
