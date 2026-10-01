# UPS-1 — the review of 9474c687 — results

This round answers the review's request for changes at `9474c687`
([PR #92 comment 5927917194](https://github.com/telaminai/fluxtionauditlog-analyser/pull/92#issuecomment-5927917194)).
Predictions are in [PREDICTIONS-r2.md](PREDICTIONS-r2.md), committed before any code change (`932a3711`). The first
round's results are in [RESULTS.md](RESULTS.md); three of its claims were false, and are corrected below. DEMO data only;
JDK 21.

## Commits

| commit | what |
|---|---|
| `e1ad93c0` | merge of `main` `258da371` (PR #87). The CHANGELOG kept both sides; the mutation harness merged textually, and every line either side added is present |
| `932a3711` | predictions |
| `ca4d89ae` | one structural rule for the detector and the parser (findings 1, 2, 4, 5) |
| `c7de5d12` | withheld node logs are never reported as absent (finding 3) |
| `798c3978` | conformance C32, the typed-grammar witness, withheld in the real frame, the format specification |
| `e70b97b1` | a broken record is never read past its first text-carrying field |
| `8b01505e`, `b0ac3bf8`, `89d500a3`, `ccf771b4`, `ef0dbd35` | the controls registered; four branches removed because no mutation could kill them; one witness strengthened |

## Findings

**1 — a payload indented deeper than the fields forged nodes and fields. Fixed.**
- `RecordBreak.analyse` now gives every line a role, and `RecordParser` reads only by it: a field only at the fields'
  indentation, outside quotes and outside the node-log block. Before, the parser read a field at any indentation.
- Outside the node-log block, a deeper line is a break. The exceptions are a block scalar's text and the nesting of an
  unknown key with no value, which the format ignores.
- The review's record, its CRLF and tab variants, and deeper `event:`, `eventTime:` and `groupingId:` lines read nothing
  forged. This holds on the built-in path, both SPI grammars, and in the published conformance fixture C32.

**2 — a closed quoted node-log value replaced the event. Fixed.**
- A line that begins inside a closed quoted value continues it. It is never a field, and never the end of the node-log
  block.
- The node-log tokenizer now tracks quotes the same way. The review's input shape has a third weakness: a continuation
  line that begins `- forged: …` started a new node.
- Both quote styles and doubled-single-quote escapes are tested, with continuations shaped like `event:`, `thread:`,
  `logTime:`, `endTime:` and an item.

**3 — withheld logs became "never logged". Fixed.**
- The index marks a broken record's node logs as withheld.
- Each surface now says *withheld*, *not read*, or nothing, never *absent*:
  - the coverage verb and the report's coverage ledger;
  - the pairing, carried as part of the arrival fact through the generated session processor (rule 9);
  - the coverage policy's sentence;
  - the CSV `nodeLogs` cell (`withheld`);
  - the records table, whose cell is now empty, never 0.
- No build setting is offered as making absence conclusive.
- Uncovered nodes stay in the ratio (annotate, never excuse), and the echo says covered is a lower bound.

**4 — exported-service records lost their data; P3 was false. Fixed by an evidenced shape, not a whitelist of text.**
- The description is a compile-time constant:
  - `ExportFunctionAuditEvent.toString()` returns what the generated processor passes to `beforeServiceCall`;
  - the generator (`JavaSourceGenerator.generateExportMethodDispatcher`) passes either `Method.toGenericString()`
    (one line) or the builder's signature;
  - `ExportFunctionDataDto` documents that signature as `"@Override\npublic boolean myMethod(int arg0)"`;
  - it is a string literal in the generated processor.
- Exactly that shape is whole: the record's first `event` is `ExportFunctionAuditEvent`, `eventToString` is exactly
  `@Override`, and the next line is one column-0 `public …(…)` signature. The value still reads `@Override`, as before
  UPS-1.
- The first `event:` precedes `eventToString`, and a second one is a repeated field, so a value's text cannot select
  this rule.

**5 — two mutations survived. Both are now caught at named assertions.**
- A mid-record BOM treated as the file's: `ups1-r2-mid-record-bom`.
- `RecordBreak` bypassed for `QUOTED_SCALARS`: `ups1-r2-typed-grammar-not-exempt`, plus C31 and C32 on a typed reader.

**Found while fixing, beyond the review** (each failed on `9474c687`):
- a field forged at the fields' indentation before the visible break was read (`endTime: 999`, a never-repeated
  `eventType`);
- a node nested under an unknown key was read;
- a header comment after the fields could forge a logger and a level;
- an unknown field after the node logs was read into the last node's value (C02, below).

## Predictions scored

| | Prediction | Result |
|---|---|---|
| R1 | the deeper payload forges nothing, on every path and in the real frame | **Held** |
| R2 | the quoted continuation leaves the event, and the record is whole | **Held**, and widened: the tokenizer split a quoted `- ` line, which was not predicted |
| R3 | coverage, pairing, CSV and table say withheld | **Held**. NO_NODE_LOGS was already safe, because BROKEN_VALUE explains the log first; the edit I made there was dead code and was reverted |
| R4 | against `473cd674`, only broken records change; every exported-service record is identical | **Held, with one unpredicted change**: C02's unknown field after the node logs. It was read into the node's value as `17.1} anotherFutureField: { nested: true`, and is now `17.1`. A correction, pinned by `anUnknownFieldAfterTheNodeLogsEndsTheBlock` |
| R5 | the two survivors and every new rule have a named witness | **Held, after four corrections.** Four controls first survived. Two guarded code made redundant by the first-text-field cap (removed), one guarded a redundant flag (removed), and one had a witness that never reached its rule (strengthened). A fifth, the parser's clearing of node logs after a break, was dead for the same reason; its control now removes the cap |
| R6 | the full suites stay green | **Held**, with skips reported separately |

## Compatibility, field by field ([r2-field-comparison.txt](r2-field-comparison.txt))

Every audit text in the repository (44 inputs) was read on three paths: built-in, SPI legacy, and SPI typed. 19 fields
were compared per record, including node logs. A dumper was compiled against each tree.

- **Tool check:** `473cd674 → 9474c687` reproduces the review's figure exactly, 84 changed fields per path.
- **`473cd674 → this head`:** 41 fields per path, in broken records only — the hostile export (10) and stream (4), C31
  (10), and the two collapsed-framing fixtures (7 and 9) — plus the one C02 correction.
- **Identical to before UPS-1:** C21, and records 9 and 10 of both topology fixtures.
- **The collapsed-framing records** now also withhold the first record's thread and endTime. They are already reported
  as run together.

## Gates (local, this head)

| Gate | Result |
|---|---|
| Regressions on `9474c687`'s code ([r2-regressions-on-9474c687.txt](r2-regressions-on-9474c687.txt)) | `RecordStructureReviewTest` 14 / 12 / 0 / 0: each failure is a named assertion with the review's wrong result. The two that pass are a guard on the new allowance and the BOM mutation witness, both by design. Finding 3's surfaces were probed with that tree's own APIs ([r2-withheld-probe-on-9474c687.txt](r2-withheld-probe-on-9474c687.txt)), because the tests use new APIs and a compile error is not a witness |
| `mvn -o -q clean test` | **3125 / 0 / 0 / 220, 411 reports, no orphans.** From the reviewed head's 3041 / 0 / 0 / 219 (408): +58 is main's PR #87 (`BundleProfileTest` 10 → 68, measured on both trees); +26 is this round's tests; +3 reports are the new classes; +1 skip is the new frame method, headless |
| 41 frame suites, one run each, real display ([r2-frame-suites.txt](r2-frame-suites.txt)) | **218 / 0 / 0 / 8.** The 8 skips are native-input tests (keys, a native drag, a file drop), in 5 suites. They skip the same way alone, and on `9474c687`'s tree the same day: native input is not delivered in this desktop session. CI runs them under Xvfb and rejects skips. `BrokenValueFrameTest` 2 / 0 / 0 / 0 |
| Mutation controls, fast engine, one run on the final code ([controls-ups1-r2.txt](controls-ups1-r2.txt)) | **40 requested, 40 caught**, each at its named test as an assertion failure. Every baseline green; source and classes restored byte-identical. 39 are `ups1-*` (14 → 39) and one is `m44-journey-scope`, re-anchored on `Pairing`'s new call |
| Preflight | 41 frame suites, 598 anchors |

## Not done, and stated

- **Undetectable by structure.** A field forged at the fields' own indentation, under a key the producer did not write
  for that record, followed by a tail shaped as an unknown field, leaves no break. Only the producer quoting its values
  closes it (UP-FLX-55). This is in the format specification.
- **Graph discovery's ranking** compares the ids that were read, and does not state a withheld count. It makes no
  absence claim: a candidate with no ids read has no pairing.
- **The Follow append** carries its withheld count as the arrival does: `refreshLoggedNodeSample` uses the same sample.
  No control removes the frame's append-side count; the session's handling of it is controlled.
- **AF-9** (reading the exported-service signature) is unchanged.
