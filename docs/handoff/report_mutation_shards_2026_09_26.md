# Four isolated mutation CI jobs

Implementation on `gate/parallel-mutation-shards`, from main `8cdf9dfe`. No merge or release.

The complete gate had grown to 16m53s in CI run 36271215353: 14m35s in controls, 1m39s in the engine self-test,
18s in the repeated early precheck, and 21s in setup/uploads. The adjacent run 36270986642 records 174 controls,
zero full-compile fallbacks, and 759.9 seconds summed across controls. This is sequential work growing with the
registry, not an unexpected return to the Maven engine.

## What changed

- Four matrix workers, each on its own runner, checkout, class trees and Xvfb display. Each worker runs sequentially
  through the existing fast engine; no per-control baseline, assertion, restoration or green re-run was removed.
- The recorded durations seed a deterministic longest-first split. The four allocations contain 43, 43, 44 and 44
  controls, estimated at 189.6, 189.7, 190.5 and 190.1 seconds of control work before setup/baselines. New controls
  without timing data are included automatically with a positive default weight.
- The engine self-test is a separate required job. The design-status-capped early control runs first in its assigned
  worker, without its previous duplicate run and setup.
- The final job retains the existing `mutation-gate` check name. It requires successful self-test/workers and
  independently checks the live registry, partition, revision, source digests, complete exactly-once results,
  named assertion failures, both byte restores and named restored-green results. Missing, stale, duplicated,
  skipped, failed or incomplete results cannot make the gate green. Partial worker artifacts remain downloadable.
- Local unsharded commands retain their behaviour. Sharding refuses combinations with branch subsets, explicit
  case selection, other modes or the Maven engine. Sharding infrastructure changes select the full branch gate.

## Checks and evidence

Predictions are in `evidence/mutation-shards-2026-09-26/PREDICTIONS.md`, committed as `dc5dcb96` before implementation.

- Initial unchanged Java baseline: 2414 total / 0 failures / 0 errors / 111 skips, 325 reports. The first sandboxed
  attempt had 29 socket-permission errors; the unrestricted rerun passed. Skips are not passes.
- `python3 tools/test_project_chart_review.py`: 5 tests, no failures/errors/skips.
- `python3 tools/test_mutation_shards.py`: 21 tests, no failures/errors/skips. These include incomplete and duplicate
  artifacts, stale revision/source digests, wrong named assertion, errors mistaken for failures, missing byte restores,
  skipped baselines/restores, newly registered controls, CLI selection refusal and the actual worker entry point.
- Fast-engine self-test: 21/21, including the existing real constant/annotation compile-fallback probes.
- Replayed the earlier CI artifact through the new collector: all 174 real entries accepted with their existing
  baseline, mutation and restoration evidence. This is format/collector compatibility, not a new execution of those
  controls; the replay's shard metadata was constructed for this check.
- Two source mutation witnesses: dropping the last allocation candidate fails
  `test_new_controls_are_included_without_timing_entries`; ignoring both byte-restoration flags fails
  `test_each_restore_flag_is_required`. Both start green, fail the named assertions, restore by byte copy with `cmp`,
  and pass again. No `git checkout` restoration.

## Misses and limits

- The first scheduling fixture expected a load spread of at most 2 seconds. The greedy algorithm produced 3 seconds
  on that fixture. The test now requires a spread no greater than the 5-second default control weight; the live
  measured registry's spread is 0.9 seconds. This is a bound on a timing estimate, not a timing guarantee.
- One collector CLI test was accidentally run while a live local mutation had removed another control's source
  anchor. It correctly refused at preflight, before reaching the missing-artifact assertion that the test expected.
  This attempt is not reported as a passing run; final Python checks must run after restoration.
- Four repeated setups and baselines can increase total runner time even when elapsed time falls. The 5–7 minute
  target assumes runner availability and is not a verified result until the CI matrix completes.
- No application code changed. No key, provider, client trial, participant project or recovery store was used.

## Final local results

- Real local shard: `--mode mutations --engine fast --shard-index 1 --shard-count 4`, 43/43 caught in 187.1 seconds.
  Its shared baseline ran 250 tests across 32 suites: zero failures, errors or skips. Every control recorded the
  named failure, source/class byte-identical restoration and restored green. The early design control ran first.
- After the shard restored, both Python suites passed again: 21 and 5 tests, no failures/errors/skips.
- Preflight: 22 registered frame suites, 174 anchors. No source file remains changed by a mutation.
- Final `mvn -q clean test`, JDK 21: 2414 total / 0 failures / 0 errors / 111 skips, 325 reports, no orphans.
- `mkdocs build --strict`, `git diff --check`, the tracked-file rule-one sweep and the added-line sweep pass.

The full four-worker Linux execution and actual elapsed-time comparison are CI acceptance, not a local claim.
At this commit that CI run has not happened yet. Its worker artifacts and the required collector's result are the
reviewable evidence; the local 43-control run does not substitute for the complete 174-control gate. No measured
CI speed-up is claimed in this document. No branch-protection setting was changed.
