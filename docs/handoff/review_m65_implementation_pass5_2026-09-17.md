# Review — M65 implementation, pass 5 (`feat/m65-follow-refreshes-graphs` at 533c6538)

**Date:** 2026-09-17 · **Reviewer:** Claude (this session) · **Response reviewed:**
[review_response_m65_impl_2026-09-17.md](review_response_m65_impl_2026-09-17.md) · **Pass 4:**
[review_m65_implementation_2026-09-17.md](review_m65_implementation_2026-09-17.md)

## Verdict: READY — merge to main

B1 is fixed the way §3 of pass 4 asked, and the reviewer's probe is now a test on the branch. F1 and F3 are
landed; F2 and F5 are written down where they belong. Nothing in the fix commit widens the change beyond the
review's findings. One thing remains unverified by me and is stated plainly in §3: the extend tick on the live
jar, which needs a click this session cannot deliver. Everything a reviewer can check without that click, I did.

## 1 · Verified against source at 533c6538

`mvn test` on the branch, my run: **1443 run, 0 failures, 0 errors, 14 skipped** (matches the response). Strict
docs build passes. Sweep clean.

| Item | Response claim | Source | Result |
|---|---|---|---|
| B1 | `landWindow` restated with the visibility guard first | `ui/GraphPanel.java` `landWindow`: `if (newMax <= v1) hold; else if (v0 <= oldMin && v1 >= oldMax) extend; else if (v1 >= oldMax) slide; else hold` | **Confirmed.** Rule 0 (no previous points) unchanged in the first branch. Exactly the four-line form. |
| B1 test | the probe as a variant, then extend on the next tick | `FollowRefreshesGraphTest.aChartZoomedOutPastBothEndsHolds_thenExtendsWhenTheLiveEdgePassesIt`: `[500, 3500]` over `[1000, 2000]`; point at 3000 → held; point at 4000 → `[500, 4000]` | **Confirmed**, and the second tick is the right assertion — the view still covered the whole old range, so extend, not slide. |
| F1 | sixteen row arrays `volatile` | `index/LogIndex.java` fields; `add` calls `ensure(size + 1)` (the volatile stores) **before** the element writes, `size` last, all under the lock | **Confirmed and sound.** A reader that observes a grown reference through the volatile load sees the copied prefix; rows below its captured size were written before the lock release it acquired after. Both cases covered. |
| F2 | recovery documented | `parse/HeapLogStore.java` `appendFrom` comment | **Confirmed.** |
| F3 | synchronous runner throw clears `extracting` | `GraphPanel.extract`: `try { extractionRunner.run(...) } catch (RuntimeException rejected) { extracting = false; throw rejected; }` | **Confirmed**, with one nit below. |
| F5 / §5 | tracker M65.5 (measurement) and M65.6 (the *no log loaded* observation) | `docs/specs/tracker.md` lines 395–397 | **Confirmed.** |
| Spec | D-F7 restated rules 0–4 with the principle; D-F0 records `volatile`; review record gains three rows | `docs/specs/spec-follow-refreshes-graphs.md` | **Confirmed**; the spec and the code say the same thing. |

## 2 · One nit, not blocking

**F3's catch is wider than its producer.** `catch (RuntimeException)` around `extractionRunner.run(...)` is
right for the production runner, whose only synchronous throw is `RejectedExecutionException` (its callbacks land
later via `invokeLater`). With a **synchronous** runner — the test seam's `INLINE` — a throw from inside the
landing code propagates out of `run` *after* the `finally` has already called `finishExtraction()`, which may
have started the coalesced follow-up; the catch then sets `extracting = false` under that follow-up's feet. Only a
test can hit it, and only if landing throws. Narrowing to `catch (java.util.concurrent.RejectedExecutionException)`
matches the one real producer and removes the case. One token; do it when next in the file.

## 3 · Not verified by me — stated for the merge decision

The extend tick on the running jar. Follow is a toggle with no verb behind it and synthetic input is not delivered
to the app from this session (pass 4 §5). The unit test drives every step of `pollFollow` after `appendFrom`
except `extendAbsMax` itself, simulated with the pair `publish` would send; the implementer's M65.3 live proof
covers the real path for extend and slide. The analyser I launched is still up with the bundle log and a
`rootNode.price` graph open: one click on *Follow* and one `./export-audit.sh` in the bundle shows the point land
within about a second. Worth the minute before the release that carries this, not before the merge.

## 4 · Did not check

- `RolledLogStore` follow and the off-heap stores (scoped out; the interface default keeps them as they were).
- The screenshots in the exchange directory from the implementer's live proof.
- M65.6 on `main` (open item; not this branch's).

## Review record

| Pass | Commit reviewed | Verdict | Conditions |
|---|---|---|---|
| spec 1 | 5cb9121e | CONDITIONAL | C1 store publication, C2 in-flight back-pressure |
| spec 2 | 88f17de7 | CONDITIONAL | C4 echo path defeats the hold, C5 one tail rule |
| spec 3 | d745006c | READY WITH FOLLOW-UPS | F1 capture order (applied in 812bfe0d) |
| impl 4 | 64e83562 | NOT READY | B1 rule 1 contracts a zoomed-out view; F1 live-index reads; F3 `extracting` on a synchronous throw |
| **impl 5** | **533c6538** | **READY** | none — nit: narrow F3's catch to `RejectedExecutionException` |
