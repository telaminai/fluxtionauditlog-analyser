# Audit stream end — saying whether a text audit file is whole (Design Spec)

_Status: **PROPOSED 2026-09-21; analyser half IMPLEMENTED on `feat/audit-format-end-marker`, not yet reviewed.** Step 1 of release 1 in the
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

## The two problems are separate, and only one needs a format change

This is the decomposition the proposal missed.

**A · Killed mid-record.** The framer already knows: `RecordFramer` splits on `---` lines and **does not
emit a record that has no closing `---`**, which is how follow mode avoids showing a half-written record.
The information exists today; nothing surfaces it. **No format change is needed** — this is analyser
reporting only.

**B · Killed exactly at a record boundary.** The file is well-formed and short. Nothing distinguishes it
from a complete one, because completeness is not currently stated anywhere. **This needs the format
change.**

**Parity note.** The binary reader already reports A: an unreadable tail is a source diagnostic naming *a
process that stopped mid-write, or a damaged tail*. The text reader reporting the same thing is parity,
not a new concept.

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
  streamEndRecords: 25     # records written before this marker
---
```

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

## D-E3 · Four states, and the analyser reports all four

The reader declares which of these it found. **Three are reportable facts; the fourth is an honest
unknown, and it must not be rendered as "complete".**

| File | Marker | Count | Analyser reports |
|---|---|---|---|
| ends after a `---`, marker present, count matches emitted records | yes | matches | **complete**, and verified |
| marker present, count does not match | yes | mismatch | **records are missing**, with both numbers |
| trailing text with no closing `---` | no | — | **stopped mid-write**, naming the unread bytes |
| ends cleanly after a `---`, no marker | no | — | **unknown whether complete** |

The last row is the D-T8 obligation. A file written by a producer that does not emit markers — every
producer today, including every existing export — falls there, and must keep opening exactly as it does
now. **Silence about completeness is the status quo and stays legal.** What changes is that silence is
*reported as silence* rather than read as success.

## D-E4 · The marker is not a record in any view that counts records

It is a container fact wearing a record's clothes, so:

- it MUST NOT appear in the records table, in `read`, in a report, in coverage, in a series or as a
  chart point;
- it MUST NOT be included in the record count the analyser shows or in `context`;
- its `logTime` MUST NOT extend the timeline used by the time filter or the graph x-axis.

A reader that cannot suppress it is still conformant — it sees a tolerated unknown field — but the
analyser, as the reference implementation, suppresses it.

## Acceptance

1. **The four states of D-E3**, each with a fixture, each reported distinctly, and the fourth reported as
   unknown rather than complete.
2. **A file killed mid-record** reports the stop and the unread byte count, and **still shows every
   complete record before it**. Losing good records to report a bad tail is a worse failure than the one
   being fixed.
3. **A file killed exactly at a record boundary, with no marker**, reports unknown. It must not report
   complete, and it must not report a stop it cannot see.
4. **A count mismatch** is reported with both numbers, from a fixture whose marker claims more records
   than the file holds.
5. **Existing fixtures are unchanged in behaviour.** All fifteen conformance fixtures, which carry no
   marker, produce exactly today's results through both the built-in path and the SPI path.
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
   re-reading it.

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
   the encoding.
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

**Not read, and relevant:** whether any shipped reader plugin would mis-handle a record whose only field
is unrecognised. Nothing here has been implemented or run.
