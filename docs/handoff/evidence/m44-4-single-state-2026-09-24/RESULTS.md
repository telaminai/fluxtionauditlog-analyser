# M44.4 single-state model — results

Against [PREDICTIONS.md](PREDICTIONS.md). Wrong predictions are recorded as wrong.

## Set 1 — M44.4a

| # | Prediction | Result |
|---|---|---|
| P1 | headless `mvn test` green | **Held.** 1,941 run, 0 failures, 0 errors, 65 skipped. That is 1,937 before plus the 4 new `SessionFactsTest` cases. The named risk (a GraphML-pinning test) did not fire. |
| P2 | frame tests 66 / 0 / 0 / 1 skip, at 60% confidence | **Held.** 66 / 0 / 0 / 1, the known local focus skip. |
| P3 | a reader-supplied graph reaches the session; an inferred one refuses coverage | **Held.** `aGraphWithNoFileIsStillOpen`. Witness W3, restoring path-based `isOpen()`, turned it red. |
| P4 | a fact posted mid-operation runs after it, and is recorded as a no-op | **Held.** `aFactPostedMidOperationIsQueued`. Witness W4, making `post` drop while dispatching, turned it red. |
| P5 | a stale fact is refused as `staleFact` | **Held.** `aStaleFactIsRefused`. Witness W5, removing the generation comparison, turned it red. |
| P6 | `verify-m68-1-coverage.py` 65 / 0 on the rebuilt jar | **Held.** 65 pass, 0 fail. |
| P7 | `MainFrame` net lines fall by at least 25 | **Wrong**, and withdrawn as a prediction because it was already measurable: +40 / −41. |

Each witness was applied, run and then restored. The file's SHA-256 was checked identical after every restore, and
the class was green again afterwards (0 failures).

**A process slip, recorded.** The predictions commit `196eb776` was made without running the gates first, the same
mistake as sets 4 and 6 of the M68.1 evidence. The file was docs-only, and the gate run above covers it. It is still
a breach of the rule, and it is written down here rather than folded away.

**A bootstrap step that should not be repeated.** Regeneration could not compile against a generated processor
that still referenced the deleted events. The stale generated file was hand-stripped to bootstrap, and the
regeneration then overwrote it whole: `grep Observed` on the result finds 0 matches, and the two emitted copies are
byte-identical. The owner's procedure (strip `@OnEventHandler`, regenerate, delete the methods) is now in
`SessionProcessorBuilder`'s javadoc, so no hand-edited state has to be trusted next time.

**Found, not predicted.** `OpenGraph` equated "no file path" with "no graph", so a reader-supplied graph was invisible
to the processor. The CHANGELOG line and P3 cover it.
