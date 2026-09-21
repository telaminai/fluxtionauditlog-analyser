# Forward-compatibility check — stream-end marker vs released 1.16.0

Acceptance 6 of [`spec-audit-stream-end.md`](../../../specs/spec-audit-stream-end.md): a file carrying a
marker must be readable by an analyser that predates the feature. **Run, not reasoned about** — and it
found something the spec had understated.

## What was run

The reader used is the released **1.16.0** jar from the JBang cache, confirmed to contain **zero**
`StreamEnd` classes, so it is a genuine pre-feature reader rather than a branch build with the feature
switched off. `Compat.java` opens each file through `HeapLogStore` and prints the record count, the parse
errors and the time range.

```
unmarked.yaml          records=2 parseErrors=0  minLogTime=1000 maxLogTime=1001  kinds=[OK OK]
marked.yaml            records=3 parseErrors=0  minLogTime=1000 maxLogTime=1002  kinds=[OK OK OK]
marked-same-time.yaml  records=3 parseErrors=0  minLogTime=1000 maxLogTime=1001  kinds=[OK OK OK]
marked-untimed.yaml    records=3 parseErrors=0  minLogTime=1000 maxLogTime=1001  kinds=[OK OK OK]
```

**Still current after the review revision (2026-09-21).** The §1a example marker was changed to the
untimed shape, which is the row measured best here: `marked-untimed.yaml` loads in the old reader with
three records and a time range identical to the unmarked file. Nothing about this check needed re-running,
because the shape the spec now recommends is the shape it already measured. The review's other changes
are reader-side and cannot affect a reader that predates the feature.

## What it shows

**The claim holds.** A marked file loads in the old reader with no parse error; every record is `OK`. The
marker is tolerated as one ordinary record carrying unknown fields, exactly as §2 already required.

**And the spec had understated the cost.** `marked.yaml` moved the old reader's `maxLogTime` from 1001 to
1002, because the marker carried a `logTime` later than the last record. That widens the time range and
every axis drawn from it — small, but a real behaviour change in a reader that cannot know better.

**The fix is free.** With the marker untimed, or reusing the last record's time, the range is identical
and the two files differ only by that extra record. §2 already defines an untimed record as kept but off
the timeline, so no new rule was needed. Format spec §1a now says a writer SHOULD omit `logTime`.

The same files through the branch build report 2 records and `COMPLETE`, timed or untimed.
