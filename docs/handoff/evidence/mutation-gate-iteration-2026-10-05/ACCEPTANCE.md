# MG-1 acceptance completion

Implementation: `5a4d1977`; reference trial: `735a40b7` (only the temporary CI workflow differs).
This is the implementer's receipt. A separate local reviewer independently recomputed the six
phase collections and timing figures. Public artifacts are linked by their run IDs below.

## Corrections and current candidate

External N1 (unbounded timeout drain) and residual N2 (unowned reference Maven execution) are fixed.
The follow-up local review also required rejection of an assertion followed by abnormal process
termination. Explicit normal completion is required by schema 3; missing metadata is rejected.
Wrong-result regressions were run before fixes, then green afterwards. Iteration: 51 / 0 / 0 / 0.
See RESULTS.md for the failed fixture and cleanup attempts, not just the final green result.

RAN/READ: PR run [37314151906](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37314151906)
at `5a4d1977`, merge candidate `33909828`, succeeded: build 3179 / 0 / 0 / 248;
UI 246 / 0 / 0 / 0; self-test 27 checks; all eight workers and collector succeeded.
Collector: schema 3, complete, full, 628 caught exactly once. Job span was 502 seconds (8m22s),
so this complete-CI observation exceeds the eight-minute planning target. No timeout was relaxed.
A documentation-only receipt commit still needs its own PR CI; this receipt cannot substitute for it.

## MG-A11: full registered-control parity

Each of the six phases ran the complete 628-control registry at one implementation revision.
The reference is the current fast engine with four workers and whole-class baselines; the optimized
variant uses eight workers and named-method baselines. It is not a comparison with the Maven engine
or with an old executable. Neither side groups or omits controls.

All six independently collected as complete, with named assertion failures, explicit normal process
completion, original source hashes, byte-identical source/class restores and green restored witnesses.
Every pair has identical name-to-verdict maps. The implementer and independent local reviewer both
re-ran collection from the downloaded worker artifacts. The trial workflow is deliberately not merge evidence.

The separately executed `compare-baselines --case dialog-unanswered-row` at `5a4d1977` additionally
caught the real control and left both intentional probes (`plant-survivor-comment` and
`plant-survivor-api-change`) uncaught in both variants, with green baselines. This focused local run
used the shared display lock. No full local mutation sweep was run.

## MG-A12: three paired measurements

| Run | Order | Reference worker span | Optimized worker span | Reference worker minutes | Optimized worker minutes |
| --- | --- | ---: | ---: | ---: | ---: |
| [37314313577](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37314313577) | Reference first | 896s | 447s | 55.25 | 54.70 |
| [37314316821](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37314316821) | Optimized first | 878s | 452s | 56.60 | 52.97 |
| [37314319900](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37314319900) | Reference first | 840s | 472s | 50.17 | 57.00 |
| Median | | 878s (14m38s) | 452s (7m32s) | 55.25 | 54.70 |

RAN/READ: all 36 workers reported Maven cache hits, on ubuntu-latest with Temurin 21. Recorded
worker queues were 1–3s. Baseline invocations were 3255 versus 648; each phase retained three
full-compile fallbacks. Detailed phase durations remain in the worker artifacts; summarized
counts, queue observations and verdict digests are in acceptance-pairs.json.

These spans include worker checkout/setup and upload, but exclude policy and shared collector
overhead. Ordinary build, UI and engine-selftest jobs were skipped in the trial workflow; their
coverage is established separately by current-candidate CI. These are not complete CI latency
measurements or billing figures. The third pair increased worker compute time by 13.6%; no general
compute saving is claimed. Three pairs establish neither p95 nor performance on cold caches.
The eight-minute full-CI target remains a planning target, not a reason to omit evidence.

## MG-A2/A3: live platform lifecycle

Temporary PR [#111](https://github.com/telaminai/fluxtionauditlog-analyser/pull/111) is based on
an isolated acceptance branch containing `5a4d1977`, with only the workflow's PR base filter extended.
The head changes a DEMO production-file comment. No temporary workflow or fixture is merged into main.
This arrangement is necessary because #107 itself changes shared infrastructure and therefore
correctly chooses full execution even while draft.

- Initial draft, `a8e19b24`, run 37314801069: plan feedback, 118 selected / 510 omitted. The actual
  full gate concluded failure, never neutral/skipped/success. Ready transition at unchanged head
  started 37315096985 in full mode and cancelled the prior draft run.
- New ready revision `09c4373d`, run 37315403508: superseded 37315096985. It later failed the
  BundleProvenance baseline in shard 3; zero controls ran in that shard and the collector rejected it.
- New revision `0b9c5742`, run 37322258141: full ready run; converting to draft cancelled it.
- Draft at unchanged `0b9c5742`, run 37322331591: feedback completed with one caught control,
  627 explicitly not run, scope feedback, while the full gate concluded failure. Converting back
  to ready cancelled the remaining jobs and started a new full run at the same head, 37322972207.
  That run succeeded: all 628 controls collected at schema 3.

The unrelated #107 candidate CI and all three non-PR diagnostic runs completed successfully while
these lifecycle cancellations occurred. Main/release event isolation is READ from the unique
non-PR concurrency key; no actual main push or release was performed for this experiment.
Manual enforcement remains MG-O1: none of this proves GitHub blocks the merge button.

## Retained failure and limits

The recurring BundleProvenance baseline failure is now tracked in
[issue #112](https://github.com/telaminai/fluxtionauditlog-analyser/issues/112), including the earlier
PR #110 attempt. Its cause remains unproven; the assertion was not weakened and no application fix
is claimed. A later green result does not erase these failures or waive future red gates.

MG-D6 grouping and MG-A7/A8 stay deferred. p95, cold-cache timing and actual main/release event
execution remain unmeasured. No branch protection was changed, no key/provider was used, and no
application code or generated session processor was changed by the deliverable.

## Final lifecycle outcome and disposition

RAN: rapid revision `e031b48a` started run 37324424759; revision `eab65ac2` superseded it.
The older run concluded cancelled. Its replacement, 37324541150, was also cancelled after conversion
to draft. Cancellation was not instantaneous: the final draft run waited until the older jobs stopped.
The preceding rapid revision `6cf49a7f` had no observed CI run; no cancellation is claimed for it.

RAN: final draft run [37324652272](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37324652272)
completed with successful feedback and an explicitly failed full mutation gate (the expected refusal).
Its feedback contains one caught control and 627 omitted controls. Build and display succeeded.
This closes the draft/ready/supersession experiment; the temporary PR is not a delivery candidate.

MG-A2/A3 lifecycle, MG-A11 full parity and MG-A12 three paired observations are demonstrated above.
The independent local reviewer accepted the correctness fixes and independently recomputed the six
phase receipts and timing medians. Remaining limits are performance characterization and the explicitly
deferred grouping design, not required code corrections. The implementation is ready for owner merge,
subject to successful CI on the final documentation head. No merge or release was performed here.
