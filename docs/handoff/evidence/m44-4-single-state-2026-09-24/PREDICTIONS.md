# M44.4 single-state model — predictions

Written before the trials they predict. RESULTS.md records the outcome of each, including the wrong ones.
Spec: [`spec-session-processor.md`](../../../specs/spec-session-processor.md) §13. Branch `feat/m44-single-state-session`.

## Set 1 — M44.4a (honest facts replace the observation funnel)

**Already run, so not a prediction:** the 99 tests in the `session` package, after being converted from
`LogObserved`/`GraphObserved` to `SessionFixtures` (request plus `LogOpened`, `GraphOpened`, `GraphCleared`,
`LogAppended`): 99 / 0 / 0 / 0. The tests were converted by me, in the same change, so this shows the conversion
compiles and agrees with itself. It does not show the application behaves the same.

Predictions for trials not yet run:

- **P1 — headless `mvn test`: green, 0 failures.** Risk named: `SessionGraphShapeTest`, or a GraphML-pinning test,
  reads node or handler names from the regenerated GraphML and fails on the renamed handlers. If it fails, the fix is
  to re-pin, but only after reading that the new edges are the intended ones.
- **P2 — frame tests: 66 run, 0 failures, 0 errors, 1 skip** (the known local focus skip). Most likely to fail:
  a `PairingDuringLoadFrameTest` case that reads the session audit ring (`aFollowAppendWritesNoSessionAuditRecordUntilCoverageReadsIt`),
  because every graph change now writes a fact record and `closeLog` posts `LogCleared`. That test counts records
  after appends only, so it should hold. **Confidence 60%.**
- **P3 — a reader-supplied graph now reaches the session.** Before this change, `openGraph.isOpen()` was false for it.
  A new test, a session replay of `GraphOpened(null path, READER_INFERRED)` followed by a log, will show
  `coverageClaim` REFUSED with "inferred from what ran". The witness: restore path-based `isOpen()`, and the test must fail.
- **P4 — a close made by an effect is recorded as a no-op fact, not dropped.** After `EXPLICIT_SWITCH` closes the log,
  the audit ring contains a `LogCleared` record with `noOp`. The witness: make `post` drop the fact while dispatching,
  and the test must fail.
- **P5 — a stale fact is refused.** `LogAppended` naming generation *n* after generation *n+1* is open changes
  nothing and logs `staleFact`. The witness: remove the generation check, and the test must fail.
- **P6 — `tools/verify-m68-1-coverage.py` against the rebuilt jar: 65 / 0**, unchanged, because this slice moves
  reporting and not verdicts.
- ~~**P7 — `MainFrame` net lines fall** in this slice, by at least 25 lines.~~ **Withdrawn as a prediction: it was
  already measurable when written.** `git diff --numstat` shows +40 / −41, net −1. So "at least 25" was **wrong**, and
  it is left on the record. The funnel's deleted lines were replaced by the fact reporters and their comments. The
  spec's −150 for the whole milestone rests on M44.4b/c, which delete the verdict copies.
