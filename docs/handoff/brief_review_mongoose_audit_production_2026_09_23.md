# Review brief — `spec-mongoose-audit-production.md`, 2026-09-23

**Review the spec, not an implementation.** Nothing is built. The point of reviewing now is that one of
its claims decides the order of the work, and getting that order wrong would ship a regression that is
worse than the defect it replaces.

**What to read:** [`docs/specs/spec-mongoose-audit-production.md`](../specs/spec-mongoose-audit-production.md).
Background if needed: [`spec-audit-stream-end.md`](../specs/spec-audit-stream-end.md) §1a is the contract
both halves obey; `UP-MON-01` and `UP-MON-02` in
[`docs/proposals/upstream-asks.md`](../proposals/upstream-asks.md); tracker AF-4 and AF-7.

**Why it exists.** The producer-side work was recorded in three places that did not reference each other
— tracker AF-4, `UP-MON-02` in the holding pen, and an issue in another repository. This is one spec
covering all of it. The reader half shipped in analyser 1.18.0 and the exporter half in `mongoose-plugins`
1.0.44; what is left is MA-1, MA-2 and MA-3.

## The claim the whole spec rests on

A marker writer shipped **before** the emission fix would write a marker declaring zero records, and the
released analyser reads that as **`complete`** — a confident statement that an empty log is whole, where
today it honestly says `unknown`.

I measured it against the published 1.18.0 jar and the spec quotes the output. **Reproduce it**; if it is
wrong the ordering collapses and MA-2 can go first. It is four lines of YAML:

```yaml
eventLogRecord:
  streamEnd: normal
  streamEndRecords: 0

---
```

## What to attack, in order — round 4

The spec was **rewritten**, not patched, after round 3. It is 343 lines from 600; the appendix is
deleted. Round 3's findings are all answered and its review is committed in `docs/handoff/`.

1. **The rewrite itself.** Three rescopes of patching produced four stale-text defects. Read the body
   against itself: one `## Ordering` block at top level, one definition of MA-1, no surviving mention of
   `customHandler` as central, `complete` described as true everywhere. Tell me what contradicts.
2. **MA-2 is where the risk now sits** and it is the least-reviewed item. D-MA2a (separators split by
   backend), D-MA2b (`backend` is read by nothing today), D-MA2c (five lifecycle answers), and whether
   MA-2.4's byte-identity baseline is achievable at all.
3. **OD-5**, the one blocking decision: does Chronicle get a marker? As specified MA-2 changes nothing
   for deployments. Is the framing fair, or does it hide a third option?
4. **MA-0's acceptance is adopted from round 3 verbatim.** Check I have not weakened it, and that the
   SOURCE_DAMAGE ordering and Follow-clearing cases are right.
5. **MA-1's capability check.** `getAuditorById(eventLogger)` refuses a processor with a custom
   auditor — the stated safe direction. Is 409/422 right, and are the three triggers complete?
6. **MA-5's mechanism** — passing Mongoose's own listener into `attach`/`start`. Does that hold for
   every backend, including MA-2's text writer?

## What I verified versus what I only read

Both are listed at the end of the spec, deliberately. In short: the empty-but-claimed measurement, the
zero-record emission on a booted server, the `getAuditorById` failure and the c21 counts were **run**.
`DefaultEventProcessor`'s field list was read via `javap`; the Mongoose wrapper source was read on
`develop` `17a03b4`. The OD-1 trade-off is **neither** — it is the decision I am asking for.

## Process

I am the implementer. Write the review as
`docs/handoff/review_mongoose_audit_production_<round>_2026_09_23_<reviewer>.md` with a verdict, numbered
findings, `file:line` references, required corrections, and an explicit statement of what you ran versus
what you only read. Do not edit the spec, implement, merge or release.

**COMMIT AND PUSH THE REVIEW** — to a review branch, or ask the author to collect it. **An earlier
version of this brief said to leave it uncommitted. That was wrong and it cost a whole round:** the first
review sat unread in the reviewer's worktree while the author revised the spec around findings they had
never seen. "Uncommitted" is the convention for reviewing someone's *proposal* untouched; a handoff
review is correspondence and has to reach the author.

**A caution earned in this session, twice.** Two defects here were found only by driving the running
thing, and both had survived careful reading — including mine. A claim in this spec that has not been run
is a claim to attack, not to accept because the reasoning looks sound. I asserted a window was
"microseconds"; it was a median of 8 ms, and review caught it.
