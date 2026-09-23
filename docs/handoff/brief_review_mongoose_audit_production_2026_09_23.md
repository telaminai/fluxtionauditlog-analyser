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

## What to attack, in order

1. **The ordering claim above.** Reproduce it. Then ask the harder question: is `complete` actually the
   wrong verdict for a file whose marker honestly says "I wrote nothing"? I argue the writer's claim is
   true-but-useless and the harm is that it *looks* like a healthy log. If you think `complete` is
   defensible, the spec's central argument is wrong and I want to know.
2. **D-MA1b — how much MA-1 actually buys.** `initialiseAuditor` registers a hard-coded list of four
   nodes, so an `EventLogManager` here audits the handler plus three infrastructure nodes and nothing
   else. MA-1 does NOT make "absence is evidence" work at node level on the wrapper path. **OD-2** asks
   whether MA-1 is then worth doing at all, versus making the wrapper path say out loud that auditing
   needs an AOT-built processor. This was added after the owner asked what `DefaultEventProcessor`
   actually is; an earlier draft implied MA-1 delivered per-node coverage, which was wrong.
3. **D-MA1 — that the fix belongs in `fluxtion-runtime`, not Mongoose.** I said the opposite to the owner
   earlier today and corrected it after reading `DefaultEventProcessor`'s declared fields. Check I have
   that right: is there a supported way for Mongoose to install an `EventLogManager` on a graph it did
   not build, which would keep the change out of the framework?
4. **OD-1, the owner decision I framed** — always-on versus opt-in for the auditor. Check the framing is
   honest, particularly that I have not stacked it. `UP-FLX-51` measures ~120 ns/event for a manager
   recording nothing and proposes a fix that would make always-on cheap; I claim the two should be
   decided together. Is that right, or is it a way of avoiding the cost question?
5. **D-MA2, per-node entry parity rather than a record count.** The argument is that a record count passes
   while every record is empty, which is exactly the MA-1 failure. Is parity checkable at the writer, or
   does it need the reader?
6. **Acceptance MA-1.3** — that the level endpoint must be shown to change the bytes, not to return 200.
   And MA-2.3, that a killed run must never read `complete`. Are these the right acceptances, and are any
   of them unfalsifiable as written?
7. **Scope.** MA-3 is much smaller than the other two and arguably belongs in its own issue rather than
   this spec. And the per-node `NONE` corruption is excluded because I could not find its description —
   only a reference at `tracker.md:131`. If you know where it is recorded, that changes AF-7's gate and
   possibly this spec's scope.

## What I verified versus what I only read

Both are listed at the end of the spec, deliberately. In short: the empty-but-claimed measurement, the
zero-record emission on a booted server, the `getAuditorById` failure and the c21 counts were **run**.
`DefaultEventProcessor`'s field list was read via `javap`; the Mongoose wrapper source was read on
`develop` `17a03b4`. The OD-1 trade-off is **neither** — it is the decision I am asking for.

## Process

I am the implementer. Leave the review uncommitted as
`docs/handoff/review_mongoose_audit_production_2026_09_23_<reviewer>.md` with a verdict, numbered
findings, `file:line` references, required corrections, and an explicit statement of what you ran versus
what you only read. Do not edit the spec, implement, merge, push or release.

**A caution earned in this session, twice.** Two defects here were found only by driving the running
thing, and both had survived careful reading — including mine. A claim in this spec that has not been run
is a claim to attack, not to accept because the reasoning looks sound. I asserted a window was
"microseconds"; it was a median of 8 ms, and review caught it.
