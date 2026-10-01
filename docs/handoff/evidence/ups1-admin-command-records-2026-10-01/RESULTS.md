# UPS-1 — admin command records — results

Predictions: [PREDICTIONS.md](PREDICTIONS.md), committed before any code (`ab630614`). Branch
`fix/ups1-admin-command-records`, from `main` at `473cd674`. DEMO data only; JDK 21.

## What was checked, and what was right

Mongoose 1.0.32 admin commands, on a processor generated for fluxtion runtime 1.1.0 by builder 1.0.76, through a real
`MongooseServer` and its `AdminCommandProcessor`. The analyser was run as an isolated instance and driven through its
own verbs. The GraphML was generated through Maven from Mongoose's `design-doc/admin-gen` project against released
mongoose 1.0.32; below the generator header it is byte-identical to the fixture that wrote the logs.

- **Framing.** The command is its own `AdminCommandEvent` record; an event it raises follows as its own record; nothing
  is spliced (the defect mongoose#45 was raised from).
- **Coverage and pairing.** 2 of 2 declared nodes; "the graph declares all 2 node(s) this log writes".
- **Table and detail.** One row per command; the detail shows the command and its node's line
  ([`after-cycle-table.png`](after-cycle-table.png)).
- **Step-through.** On a command record the node that logged is lit; the other is *may have run*. That is hedged, not
  false: the GraphML records no annotations, so an `@AfterEvent` node cannot be excluded.
- **Signal-routed commands.** `eventTime: -1` reads as absent (Format 1 §2); there is no time-order finding.

## What was wrong (P1, P2 — confirmed)

An operator-typed argument holding a line break reached `eventToString` unquoted and broke its record:

- an argument holding a line break, `nodeLogs:` and `- forged: …` made the analyser report a node **`forged`**, and say
  "the graph declares 2 of the 3 node(s) this log writes; 1 written id(s) are not in the graph"
  ([`before-forged-node.png`](before-forged-node.png));
- one holding a line break, `---`, `eventLogRecord:` and `event: Forged` showed the event type **`Forged]]`**
  ([`before-forged-event.png`](before-forged-event.png)).

## The fix

- **`RecordBreak`** finds where a record's own structure breaks:
  - a line less indented than its fields;
  - a second `eventLogRecord:` line;
  - a top-level field written twice. The record is then kept only up to that field's first occurrence, because the
    forged copy can come first.

  It shares `FramingScan`'s quote tracker, and trusts quotes only when they close within the record. A separator line
  the framer did not split on is framing's subject, so the record key after it is the break.
- **`RecordParser`** reads a broken record only up to its break and reads **none** of its node logs. It never reads a
  line that begins inside a closed quoted scalar as a field. The record carries `brokenAtLine`.
- **`ProducerDiagnostics.Kind.BROKEN_VALUE`** names each broken record and its line. UNSEPARATED no longer fires for a
  record key that follows a broken value: one cause, one name.
- **Every surface says *not read*, never *none logged*.** The record detail says "node logs not read: this record's
  structure breaks at its line 7", and the step cursor says the same. The status bar names the finding: "a value broke
  its record".
- **The status bar keeps the log's producer and time-order warnings when a graph opens.** This was an older bug
  (2026-08): the pairing sentence replaced the whole line, so an agent's `open {log, graphml}` hid every producer
  finding from the bar.

After ([`after-forged-node.png`](after-forged-node.png), [`after-forged-event.png`](after-forged-event.png)):

- 6 records, all of them `AdminCommandEvent` or `Reading`;
- "the graph declares all 2 node(s) this log writes";
- one `BROKEN_VALUE` finding naming records 3 and 4 at line 7.

## Predictions scored

| Prediction | Result |
|---|---|
| P1 broken records keep their fields before the break and read no node logs; no forged node or event | **Held** (unit, conformance C31 on both reader paths, frame test, screenshots) |
| P2 a new finding names each broken record and line, on the bar, in `context` and on both paths | **Held** |
| P3 every whole record parses as before | **Held**, one refinement. The first full run failed `ExporterFramingAgreementTest`: a mid-file BOM'd separator was called a broken value, which silenced UNSEPARATED. A separator line is now framing's subject. The full suite is otherwise unchanged. |
| P4 the raw stream's split record (7, not 6) is not fixed here | **Held** — MA-7, and upstream UP-FLX-55 |
| P5 each break rule's removal is caught by a named test | **Held**, and widened to 14 controls |

**Not predicted, found while checking the fix:**
- The first "after" screenshot said "(no node logged in this cycle)" on a broken record. That is a false claim the fix
  itself introduced, now replaced by "not read".
- A correctly quoted multi-line value was read as fields. This is older than the fix, and now also fixed.

## Gates (local, this branch)

| Gate | Result |
|---|---|
| Full headless suite | **3041 / 0 / 0 / 219** in 408 reports. `main`: 3026 / 0 / 0 / 218. The only failure on the last pre-commit run was `SpecLinksResolveTest`, whose link was to this file, not yet written. |
| All 41 registered frame suites, sequentially under the display lock | **217 / 0 / 0 / 2** ([`frame-suites.txt`](frame-suites.txt)). The 2 skips are `AssistantNativeFrameTest` (native input not delivered that time); rerun alone, 2 / 0 / 0 / 0. The previous full run on this branch was 217 / 0 / 0 / 0. |
| Mutation controls, fast engine | **14 requested, 14 caught**, each at its named assertion; byte-identical restore ([`controls-ups1.txt`](controls-ups1.txt)) |
| Preflight | 41 frame suites; anchors resolve (`test_*chart_review*` OK) |
| `mkdocs build --strict` | clean |

## Not done here

- **Upstream.** The writer should quote line breaks in values (UP-FLX-55, not filed), so the record says what it meant.
- **mongoose#45.** Close it once this merges.
- **AF-8.** An argument like `note: by ops # DEMO` raises the owner's open question about a trailing `#`. It is kept out
  of fixture C31 so this change decides nothing about it.
