# Response — M65 implementation review pass 4 ([review](review_m65_implementation_2026-09-17.md) at 64e83562)

**Date:** 2026-09-17 · **Author:** the implementing session · **Branch:** `feat/m65-follow-refreshes-graphs`
**Verdict received:** NOT READY — B1 blocker; F1, F3 required-ish; F2, F4, F5 noted. **All accepted; all landed
in one commit** (this one). Full suite after: **1443 run, 0 failures, 0 errors.** Sweep clean.

| item | verdict | what landed | where |
|---|---|---|---|
| **B1** extend contracted a zoomed-out view | **accepted — a real defect in my rule 1** | `landWindow` restated in the review's four-line form: hold if `newMax <= v1` (already visible), else extend if the view covered the whole old range, else slide if the right edge was at the old max, else hold. The reviewer's probe is now the test `aChartZoomedOutPastBothEndsHolds_thenExtendsWhenTheLiveEdgePassesIt` — `[500, 3500]` over `[1000, 2000]`: a point at 3000 holds, a point at 4000 **extends** to `[500, 4000]`. Spec D-F7 restated with the guard as rule 1 and the one-sentence principle; the 1c3d7737 clause on rule 2 is gone because the guard subsumes it | `ui/GraphPanel.java`, `ui/FollowRefreshesGraphTest.java`, spec D-F7 |
| **F1** filter reads the live index arrays with no happens-before on a grown reference | **accepted — the argument had a third case** | Took the `volatile` option: the sixteen row arrays in `LogIndex` are `volatile`, with a comment naming the case. A reference published by a volatile store carries its contents; a load-acquire per element on AArch64, nothing on x86. The `Snapshot`-with-`length` shape is the cleaner long-term form and is not taken here — it would move the filter onto a different index surface, which is a bigger change than this branch should carry. Spec D-F0 part 2 records the choice | `index/LogIndex.java`, spec D-F0 |
| **F2** `appendFrom` mid-frame failure: strictly better, write down the recovery | accepted | Comment in `appendFrom`: readers cannot throw; the unindexed tail is picked up when the file next GROWS (a same-length re-read returns 0), where before the retry was immediate but readers could throw | `parse/HeapLogStore.java` |
| **F3** a synchronous throw from the runner leaves `extracting` set forever | **accepted** | `extractionRunner.run(...)` wrapped: on a `RuntimeException` clear `extracting` and rethrow. Not unit-tested — the only producer is `RejectedExecutionException` at pool shutdown, and a test would need a runner that throws, which the seam allows; left as a two-line guard with a comment | `ui/GraphPanel.java` |
| **F4** `refreshed` type and request-counting semantics | accepted as is | no change | — |
| **F5** D-F5 measurement not taken | accepted | Tracker **M65.5 ☐** carries it with the trigger the spec names | `docs/specs/tracker.md` |
| §5 *observed, not attributed*: `graph` said *no log loaded* after a command-line open | noted | Tracker **M65.6 ☐**: check on `main`; M65 did not touch that path | `docs/specs/tracker.md` |
| §5 the live extend tick not independently reproduced | noted | Stands as the implementer's proof (extend and slide, screenshots in the exchange dir). The reviewer's analyser is still up with a graph open; one click on *Follow* and one `./export-audit.sh` closes it — the owner can do that in a minute | — |

## On the two owner questions

- **Q1 (D-F0 soundness):** the reviewer's third case is correct and I had not seen it: I argued about the arrays
  `rowSpans()` captured, and the filter does not read those — it reads the live fields through `view.index()`.
  `volatile` closes it by the JMM; the comment in `LogIndex` states the case so the next reader does not re-derive it.
- **Q2 (rule-2 change after review):** the reviewer agrees it should stay, and B1 is the same principle applied to
  rule 1. The restated rule is one sentence — *the view moves only to reveal a point that would otherwise be
  hidden* — with the guard in front, which is shorter than either earlier form.

## What changed in the spec and tracker

- D-F7 restated (rules 0–4 with the visibility guard first; the principle stated once).
- D-F0 part 2: the `volatile` row arrays and why.
- Review record: three new rows (B1, F1, F3).
- Tracker M65: decisions sentence updated; M65.5 (measurement) and M65.6 (the *no log loaded* observation) added
  as open items. M65.0–.3 stay ☑.

## Not done

- No test for F3's guard (see the row). If the reviewer wants one, the seam makes it a ten-line test: a runner
  that throws, assert `isExtracting()` is false afterwards and the next request extracts.
- The live extend tick, independently (see the row).
