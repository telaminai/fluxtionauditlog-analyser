# Post-seal notes (added 2026-10-14, from the review of PR #141 at `908e29fc`)

The sealed files (`PREDICTIONS-AB.md`, `FIXTURES.md`, `MANIFEST-AB.sha256`, `.sealed-at` and the sealed harness,
commit `3ca0bed4`) are left byte-identical, and so is every scored file under `results/`. These notes correct or
qualify the reporting. **None of them changes the verdict (NO EFFECT SHOWN) or any number the decision rule used.**

1. **The bundle half had no power, so KEEP was impossible on this control data.**
   - Criterion (a) requires `control median − treatment median > control spread` of diagnosis cost. On both bundle
     rungs the control's spread exceeds its median:
     - rung 3: spread $0.0191, median $0.0147;
     - rung 5: spread $0.0237, median $0.0194.
   - A treatment diagnosis cost of $0 would therefore still fail (a), and KEEP needs (a) on both bundle rungs.
   - RESULTS.md now says this next to "the section is to be deleted" and no longer calls rung 3 "equal within
     noise".
   - **This is a property of the sealed rule at n = 3, not a finding about the section.**
2. **Side observation, with confounds.** The control's bundle-rung costs are far below the release ladder's on the
   same seeds, CLI, jar, tasks, oracles and scorer. That is consistent with fluxtion-web #17 (and the other bundle
   script and CLAUDE.md changes) having removed much of the headroom the lever targeted. It is not a claim. The
   confounds are in RESULTS.md.
3. **Main-context analyser calls around the delegations** are now given per run, counted as distinct calls in event
   order.
   - ab-r2-t-2 made **4** analyser calls after its first reply, not 7: per-delegation sums double-counted it, because
     it delegated twice.
   - ab-r2-t-2 made **12** analyser calls before its first delegation, counted distinct by call id. The review counted
     11; the difference is not resolved here, and both figures say the run diagnosed in the main context before
     delegating.
4. **Delegation and cost.** The comparison is now worded as **no evidence** that delegation lowered cost, confounded
   by **self-selection**: the two most expensive delegators edited and regenerated between their two delegations
   (fix-and-rerun loops). The rung mix of the delegating and non-delegating treatment runs is balanced, 3 starter and
   3 bundle runs each.
5. **VOID re-run order.** ab-r5-c-3 is the last cell in the sealed order, so its re-run took its own slot, and the
   interleaving is intact. The earlier "the only cell out of interleaved order" was wrong.
6. **Space freed for the re-run.** It came from 50 `cli/` directories, not 51. The VOID cell's own partial copy
   (44 MB, truncated by the full disk) failed the digest check and was left in place.
7. **Who agreed the patch-not-replace deviation.** FIXTURES.md (sealed) says "The coordinator agreed before sealing
   (2026-10-14)". To be precise: it was agreed by the **coordinating session** that commissioned this A/B, by message,
   before sealing. **It was not an owner decision.** The deviation itself is unchanged: the vendored skill gains only
   the canonical routing paragraph and the fresh-context section, byte-identical.
