# Audit stream end — saying whether a text audit file is whole (Design Spec)

_Status: **PROPOSED 2026-09-21; analyser half IMPLEMENTED on `feat/audit-format-end-marker`, REVIEWED, and revised in response.** The review rejected the branch and was right to: the reader reported every real Mongoose export as damaged, the three stores disagreed about the same file, follow mode leaked the marker, and a record could be deleted by its own contents. One state is withdrawn as unsound, two are added, recognition is inverted, and the corrections are recorded inline rather than only in the tracker. Step 1 of release 1 in the
[Mongoose audit format proposal](../proposals/mongoose-audit-format/README.md): the contract both the
writer and the reader implement, so it comes before either. Owner decision 1, taken 2026-09-21: the
analyser learns to report incompleteness. Amends the published
[format specification](../site/format-spec.md) additively. Related: D-T8 and D-T9 in
[trust structure](spec-trust-structure.md), [source adapters](spec-source-adapters.md) D-A6._

## The problem, and why the obvious answer fails

A Mongoose server writing a text audit file can stop in the middle. The proposal required it to "write an
explicit end marker" so that a file without one reads as incomplete. **Measured: as specified that is
false.** Three naive designs were tried against the shipped analyser:

| Marker written as | What the analyser does |
|---|---|
| its own document between `---` lines | becomes a **phantom extra record** |
| a line inside the last record | **silently absorbed** by the lenient parser |
| absent, file killed at a record boundary | **indistinguishable** from either of the above |

So "write a marker" is a requirement with no mechanism behind it, and the acceptance check that a
killed run is reported incomplete could not have passed. This spec supplies the mechanism.

## The decomposition, and the half of it that was wrong

This spec first said the problem splits in two, and that only one half needed a format change.

**A · Killed mid-record. WITHDRAWN — this was unsound, and it was the branch's worst defect.** The claim
was that `RecordFramer` already knows, because it does not emit a trailing record with no closing `---`.
That reads `---` as a *terminator*. **§1 makes it a separator**, and explicitly skips blank text after the
last one, so a whole file may legitimately end with its last record and nothing after it. Mongoose's own
audit export does exactly that: it writes `\n---\n` **between** records and nothing at the end. Measured on
the branch — a byte-exact export of 25 real records reported *"a writer that stopped mid-record, or a
damaged tail"*. **Every real export, called damaged.** All fifteen conformance fixtures happened to end
with a separator, so the suite could not see it; `c19-export-layout.yaml` exists so that it can.

The correction is not a better heuristic. **A text container cannot detect a mid-record stop at all.** A
truncated record usually still parses — `RecordParser` returns `PARSE_ERROR` only when it recognises *no*
field, so a record cut after `logTime:` is an ordinary record — and the missing separator carries no
information. A run killed at any point has no marker, so it is UNKNOWN. That is the honest answer, and it
is D-T8 applied to the analyser itself rather than to a producer.

**B · Killed exactly at a record boundary.** The file is well-formed and short. Nothing distinguishes it
from a complete one, because completeness is not currently stated anywhere. **This needs the format
change**, and it is now the *whole* of what the format change buys.

**Parity note, corrected.** The binary reader genuinely detects a cut tail, because its framing is
structural: a length-prefixed record that runs off the end of the file is unreadable, full stop. The text
container has no such structure. The two are not at parity and cannot be made so, which is a real argument
for the binary format rather than a gap to paper over.

## D-E1 · The end marker is a record carrying a reserved field

Format 1 §1 is explicit that a text container has **no position for non-record text**: any non-blank text
between separators is a record, and a file that opens with a banner earns a `PARSE_ERROR` record for it.
So a marker at the container level — a sentinel line, a YAML `...` terminator, a comment — cannot be added
without changing what a container is, and an old reader would turn any of them into a `PARSE_ERROR`.

A marker that **is** a record avoids that, and Format 1 already tolerates the mechanism: §2's
`c02-unknown-fields` fixture pins that unknown fields are accepted and ignored. An additive reserved field
therefore degrades gracefully in every existing reader.

```yaml
---
eventLogRecord:
  streamEnd: normal        # normal | stopping
  streamEndRecords: 25     # records since the previous marker, or since the start
---
```

**A marker is recognised by everything the record holds, not by a key it mentions — corrected in
review.** The first implementation asked whether any line of the record trimmed to something starting
with `streamEnd:`. `RecordParser` is indentation-insensitive, so a line inside a multiline
`eventToString` is indistinguishable from a top-level key by shape. Measured: a record whose event's
`toString` contained the line `streamEnd: normal` was **dropped from the index on all three paths**, and
the file then reported records missing. Silent, content-controlled data loss, reachable by an ordinary
producer with no malice at all — a direct D-T8 violation committed by the mechanism built to serve D-T8.
Recognition is now an allow-list: a marker may carry `streamEnd`, `streamEndRecords` and its own
`logTime`, and nothing else. **Anything more makes it a record, and it is indexed and counted.** The
asymmetry is deliberate: mistaking a marker for a record shows an extra row, and mistaking a record for a
marker destroys evidence.

**Flat scalars, corrected during implementation.** This spec first drew the payload as a nested mapping.
`RecordParser` switches on top-level SCALAR keys, so a nested payload would have needed new parser
machinery for a two-field value, and `c02-unknown-fields` pins that scalar and mapping unknowns are alike
tolerated — so the flat pair buys identical compatibility for materially less code.

**No `logTime`, also corrected during implementation.** Running the compatibility check against released
1.16.0 showed a timed marker widening that reader's time range. §1a now says a writer SHOULD omit it.

**Compatibility, stated rather than hoped.** An older analyser, or any third-party reader, sees one extra
record with an unrecognised field and no `event` or `nodeLogs`. That is already-tolerated behaviour, not a
parse failure. It is visible, which is the cost; it is benign, which is the point.

## D-E2 · The marker carries a count, so the claim is checkable rather than declarative

`records` is the number of records written before the marker. This is the denominator idea applied to the
file itself: the file states what it should contain, so **absence becomes measurable** instead of being
inferred from the file's shape.

Without the count, a marker only says "I meant to stop here". With it, a reader can also detect records
lost *in the middle* — a write that failed and was swallowed, a truncated copy, a partial transfer —
which no amount of tail inspection can find.

## D-E3 · Five states, and the analyser reports all five

The reader declares which of these it found. **Four are reportable facts; the last is an honest unknown,
and it must not be rendered as "complete".**

| File | Analyser reports |
|---|---|
| every marker's count matches its segment, and a marker is the last thing in the file | **complete**, and verified |
| a marker claims more records than its segment holds | **records are missing**, with both numbers |
| a marker claims fewer records than precede it | **more records than declared** — the marker is wrong, or it is not an end |
| a marker carries no readable count | **unverified** — an end is claimed and nothing backs it |
| no marker at all, or records after the last one | **unknown whether complete** |

The last row is the D-T8 obligation. A file written by a producer that does not emit markers — every
producer today, including every existing export — falls there, and must keep opening exactly as it does
now. **Silence about completeness is the status quo and stays legal.** What changes is that silence is
*reported as silence* rather than read as success.

**Three of these five came out of review, and each replaced a wrong answer rather than a missing one:**

- *stopped mid-write* is **gone**. It was produced by the absence of a trailing separator, which is not
  evidence of anything. See the decomposition above.
- *unverified* was previously folded into "records are missing", because a marker with no readable count
  yielded `-1` and `-1 != emitted`. Measured output: *"holds -1 records and 26 were read. -26 are
  missing"*. §1a already had the right word — an unbacked claim is unverified, not a loss.
- *more than declared* was also "records are missing", printing *"-5 are missing"*. A negative gap is not
  a gap, and the two situations call for different work: one is lost data, the other is a wrong marker.

**A marker counts its segment, not the file.** Review asked what a second marker means once two runs are
appended, which is the shape Mongoose's cumulative export already produces across restarts. Counting the
whole file would read the second marker as claiming 25 records in a 50-record file and report 25 missing
from a file that has lost nothing. Segments give the right answer — two whole runs are complete — and
cost one counter. **Records after the last marker are UNKNOWN**: a declared end that is not the end of the
file says nothing about what followed it, which is exactly a cumulative export whose current run is still
going.

## D-E4 · The marker is not a record in any view that counts records

It is a container fact wearing a record's clothes, so:

- it MUST NOT appear in the records table, in `read`, in a report, in coverage, in a series or as a
  chart point;
- it MUST NOT be included in the record count the analyser shows or in `context`;
- its `logTime` MUST NOT extend the timeline used by the time filter or the graph x-axis.

A reader that cannot suppress it is still conformant — it sees a tolerated unknown field — but the
analyser, as the reference implementation, suppresses it.

## Acceptance

1. **The five states of D-E3**, each reported distinctly, and the last reported as unknown rather than
   complete.
2. ~~**A file killed mid-record** reports the stop and the unread byte count.~~ **WITHDRAWN in review as
   unachievable, and it was achieved falsely.** There is no signal for it: see the decomposition. What
   replaces it is the negative acceptance below, because the attempt did active harm.
2a. **A file that ends without a trailing separator loads as an ordinary, whole file** — every record
   read, no diagnostic, state unknown. Fixture `c19-export-layout.yaml`, in the exact layout Mongoose's
   export writes.
2b. **A record is never removed because of its own contents.** A record whose `eventToString` or node-log
   value contains a marker-shaped line is indexed, counted and shown, on all three store paths. Fixture
   `c20-marker-lookalike.yaml`.
3. **A file killed exactly at a record boundary, with no marker**, reports unknown. It must not report
   complete, and it must not report a stop it cannot see.
4. **A count mismatch** is reported with both numbers and a positive gap, from a fixture whose marker
   claims more records than the file holds.
4a. **Two whole runs appended into one file are complete**, and a run followed by an unfinished one is
   unknown.
5. **Existing fixtures are unchanged in behaviour.** All the conformance fixtures that carry no marker
   produce exactly today's results through both the built-in path and the SPI path.
5a. **The three store paths agree about the same bytes.** Heap, memory-mapped and SPI report the same
   record count, the same state and the same diagnostics for every shape. Review found them disagreeing
   on five fixtures, because the unterminated-tail signal existed only in the heap framer: the same file
   said one thing opened small and another opened large.
6. **Forward compatibility is demonstrated, not assumed. DONE, and it found something.** Verified
   against released **1.16.0**, which contains no stream-end class: a marked file loads with 3 records,
   zero parse errors, every record `OK`, against 2 for the same file unmarked. **But the marker's own
   `logTime` extended that reader's `maxLogTime` from 1001 to 1002** — the compatibility paragraph had
   said only "one extra record", which understated it. An untimed marker, or one reusing the last
   record's time, leaves the range identical. §1a now says a writer SHOULD omit `logTime`. This is why
   the check had to be run rather than reasoned about.
7. **The marker is absent from every counting surface** per D-E4: table, `read`, report, coverage,
   series, record count, `context`, and the time range.
8. **Follow mode** sees the marker arrive and switches the file from unknown to complete without
   re-reading it. Review found both halves of this broken while the acceptance was ticked: `appendFrom`
   framed with no tracker at all, so an appended marker was **indexed as a record** and the state never
   moved off its load-time value.
9. **A rolled set gives one honest answer about itself.** It is complete only when every member is, one
   silent member makes the set unknown, and a member that lost records makes the set say so and names
   which file. Review found the set taking the default and reporting unknown while a member was short.
10. **The claim reaches a human.** `context` carries the state, and the status bar says "complete". The
    branch computed that note into a local variable and never put it in the status text, so the surface
    `context`'s own comment pointed at did not exist.

## What this does not do

- It does not establish **origin**. A marker is written by whoever wrote the file, so it says the writer
  believed it finished. D-T5's limits are unchanged, and nothing here is tamper-evidence.
- It does not make an unmarked file suspect. The overwhelming majority of files will never carry one.
- It does not apply to the binary format, which already reports a truncated tail structurally.

## Open questions for review

1. **`reason` values.** `normal` and `stopping` are a guess at the useful distinction — a run that ended
   versus a server shutting down. Is a third needed, and should an unrecognised value be tolerated like
   any other unknown, or refused?
2. **Should the count be of records, or of bytes, or both?** Records is what a reader can check cheaply.
   Bytes would also catch a truncated-but-boundary-aligned copy, at the cost of coupling the marker to
   the encoding. **Review raised the stakes on this one.** Now that "stopped mid-write" is withdrawn, the
   marker's count is the *only* completeness signal a text container has, and a record count cannot
   detect a file whose last record was cut — the record is still there and still parses. A byte count in
   the marker would. Worth deciding before AF-4 writes the first marker, because adding a second count
   later is a format change and this one is free.
3. **ANSWERED by §1a: the container.** It is described in §1a, beside the framing rules, because that is
   where a reader looks for facts about the file. Left here because the reasoning is worth keeping:
   physically a record, semantically a container fact, and the reader's obligations are container
   obligations.
4. **Does the marker belong in `format-spec.md` §1 (container) or §2 (record)?** (superseded by 3) It is physically a
   record and semantically a container fact. The spec should say which, because that decides where an
   adapter author looks for it.
4. **Is a mismatch an error or a warning?** This spec says report; it does not say refuse. A file with a
   count mismatch is still readable, and refusing it would lose data to report a defect.

## What was read

`docs/site/format-spec.md` §1 and §2, including the no-non-record-text rule and the `PARSE_ERROR`
consequence; `RecordFramer`'s separator handling and its explicit non-emission of an unclosed record;
`src/test/resources/conformance/`'s fifteen fixtures and `c02-unknown-fields` in particular;
`FormatConformanceTest`'s statement that every fixture runs through both the built-in and SPI paths; the
binary reader's existing truncation diagnostics.

**Read after review, and it changed the design.** `WebAdminService.handleAuditExport` in
`telaminai/mongoose-plugins`, which is the producer this whole contract serves: it writes `\n---\n`
between records and nothing after the last one. The original spec reasoned about the framer without ever
reading the writer, and got the one fact that mattered backwards. `RecordParser`'s line loop, which
strips every line and never consults indentation, which is why marker recognition cannot be a key search.

**Still not read, and still relevant:** whether any shipped reader plugin would mis-handle a record whose
only field is unrecognised.

## Revision history

| when | what changed, and what it cost |
|---|---|
| 2026-09-21, first draft | The contract: marker as a record, a count, four states, D-E4 suppression. |
| same day, after implementation | Payload flattened to scalars; `logTime` dropped on the writer's side after the released-jar check found it widening an old reader's time range. |
| same day, after review | The branch was rejected. `STOPPED_MID_WRITE` withdrawn as unsound — it called every real export damaged. `UNVERIFIED` and `MORE_THAN_DECLARED` split out of a `MISSING_RECORDS` that had been printing negative gaps. Counting made per-segment so appended runs work. Marker recognition inverted from a key search to an allow-list, after a record was found being deleted by its own `toString`. Rolled sets and follow mode given the rule they never had. |
