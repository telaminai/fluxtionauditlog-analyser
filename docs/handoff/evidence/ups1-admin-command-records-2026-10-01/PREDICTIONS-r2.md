# UPS-1 — the review of 9474c687 — predictions (written before any code change)

The independent review of `473cd674..9474c687` ([PR #92 comment 5927917194](https://github.com/telaminai/fluxtionauditlog-analyser/pull/92#issuecomment-5927917194))
requested changes: four required findings and one regression gap. This round starts from `e1ad93c0`, which is
`9474c687` with `main` (`258da371`, PR #87) merged in. Merge only: CHANGELOG kept both sides, and the mutation harness
merged textually, keeping every line either side added. DEMO data only; JDK 21.

## What was read before deciding (rule 6)

- **The producer's writer**, fluxtion-runtime 1.1.0 `LogRecord`: fields at four spaces, in the order `eventTime`,
  `logTime`, `groupingId`, `event`, `eventToString`, `thread`, `eventFilter`, `nodeLogs`, `endTime`; node items at
  eight spaces. `nodeLogs:` is written for every record. `eventToString` is `event.toString()`, unquoted.
- **The exported-service description** is a compile-time constant. `ExportFunctionAuditEvent.toString()` returns the
  description passed to `beforeServiceCall`. The generator (`JavaSourceGenerator.generateExportMethodDispatcher`)
  passes either `Method.toGenericString()`, which is one line, or the builder's `exportMethodSignature`. That one is
  documented in `ExportFunctionDataDto` as `"@Override\npublic boolean myMethod(int arg0)"`, and it is a string
  literal in the generated processor (`DemoQuoteProcessor.java:398`). So the only line break a producer writes into
  that value comes from the generator, never from runtime data, and its shape is exact: `@Override`, a line break,
  and one `public …(…)` signature at column 0.

## The design, decided before code

1. **One structural rule, shared by the detector and the parser** (findings 1 and 2).
   - `RecordBreak` classifies every line of a record, and `RecordParser` reads fields and node-log lines only where
     that classification says so. It no longer applies its own rule.
   - A field is a key line at the fields' indentation, outside a quoted scalar and outside the node-log block.
   - The node-log block holds the lines more indented than the fields, plus the `- ` items at their indentation. A
     line that begins inside a closed quoted scalar is that value's continuation wherever it is. It is never a field
     and never ends the block.
   - New break shapes, outside the node-log block:
     - a line **more** indented than the fields that does not continue a block scalar (`|`, `>`), or a nested
       mapping under an unknown, empty-valued key;
     - a non-blank, non-key line at the fields' indentation.
   - Breaks are collected over the whole record. The record is read up to the **earliest** line any break withholds
     from, so a repeated field found after another break still withholds from its first copy.
   - A header comment counts only before the first field.
2. **Withheld is not absent** (finding 3).
   - The index marks a record whose node logs were not read.
   - The coverage echo, its ledger, the pairing verdict, the coverage policy's sentence, the CSV export and the
     records table all say *withheld*, never *never wrote audit output*, *no node output was recorded* or `0`.
   - The count reaches the pairing as part of the arrival fact (`LogOpened` / `LogAppended`), computed in the same
     sample loop as the logged ids (rule 9).
3. **The exported-service shape is allowed exactly, and nothing else is** (finding 4).
   - A line less indented than the fields is not a break only if the record's first `event:` is
     `ExportFunctionAuditEvent`, the line directly follows an `eventToString` whose value is exactly `@Override`, and
     the line is one column-0 Java `public` method signature with no key.
   - The value stays `@Override`, as it read before UPS-1.
   - The first `event:` line is written before `eventToString`, so a value's text cannot select this rule.
4. **Regression gaps** (finding 5): a mid-record byte-order-mark witness, and a typed-grammar (`QUOTED_SCALARS`)
   broken-record witness on both the parser and the SPI conformance path.

## Predictions

- **R1.** The review's deeper-indented payload (forged node, forged `event`, `eventTime`, `groupingId`; LF, CRLF and
  tabs) reads no forged value. The record is broken at the first deeper line, `forged` is in no node list, and the
  event stays `AdminCommandEvent`. This holds on the built-in path, the legacy and typed SPI paths, and a real frame.
- **R2.** The review's quoted node-log continuation (`event: Forged` inside a closed `"…"`, also `'…'` with `''`) leaves
  the event `AdminCommandEvent`. The record is whole, and the node keeps its whole message.
- **R3.** An all-broken log whose withheld blocks hold genuine output: coverage reports the node as **withheld**, not
  *never wrote*; membership and pairing say *read* or *withheld*, not *recorded*; the CSV `nodeLogs` cell says
  `withheld`; the table cell is not `0`.
- **R4.** Against `473cd674`'s parser, field by field over every audit text in the repository, on both reader paths:
  - **every exported-service record in C21 and both topology fixtures is identical**, records 9 and 10 included;
  - the only changed records are broken ones — C31, the hostile export and stream evidence, and the two collapsed
    framing fixtures the review named.
- **R5.** The review's two surviving mutations are caught by named tests: a mid-record BOM treated as the file's, and
  `RecordBreak` bypassed for `QUOTED_SCALARS`. So are new controls removing each new rule (the deeper-line break, the
  continuation that never ends the block, the earliest-withhold rule, the exported-service condition, and the
  withheld marks on coverage, pairing, CSV and table).
- **R6.** The full headless suite and all 41 frame suites stay green. Counts rise only by this round's tests.

## Not claimed

A field forged at the fields' own indentation, under a key the producer did not write for that record (`eventType`,
which a text producer never writes), followed by a tail shaped as an unknown field, leaves no structural trace. Only
the producer quoting its values (UP-FLX-55) closes that. It is stated here and in the format specification, and not
fixed.
