# MG-1 implementation evidence

Implementation branch: `feat/mutation-gate-iteration`, based on spec r2 `744379aa`.
Predictions were committed first as `cea820b0`. This is the implementer's evidence, not independent approval.

## RAN locally

- JDK 21, `mvn -o -q test`: 3179 / 0 / 0 / 248 (total / failures / errors / skips), 419 reports, no orphans.
- Initial five regression witnesses on the old implementation: 47 / 5 / 0 / 0. That run included the existing
  sharding class twice through unittest discovery; the final module removes that duplication. Named failures:
  `test_baseline_runs_only_deduplicated_witnesses`, `test_shared_test_resource_change_forces_full`,
  `test_partial_scope_cannot_masquerade_as_full`, `test_unknown_schema_cannot_be_accepted`,
  `test_changed_plan_cannot_be_accepted`. These were assertions about wrong results, not import/compile errors.
- `python3 tools/test_mutation_iteration.py`: 38 / 0 / 0 / 0 (includes 21 inherited collector tests).
- `python3 tools/test_mutation_shards.py`: 21 / 0 / 0 / 0; `tools/test_project_chart_review.py`: 5 / 0 / 0 / 0;
  `tools/test_ci_docs_only.py`: 5 / 0 / 0 / 0. Counts overlap; do not add them as distinct coverage.
- `--mode selftest`: 27 executable checks, no failed checks. Includes genuine JUnit launcher probes for
  injected parameters, inherited methods, missing and disabled witnesses, class lifecycle errors and an
  unrelated failing method excluded by named selection. Both real compile-fallback controls were caught/restored.
- `--mode compare-baselines --case dialog-unanswered-row`: both variants caught the real control and left
  both planted survivors uncaught, including the API-change fallback. Whole-class variant 19.8s, named-method
  variant 13.3s. One warm local comparison is not evidence for a general speed-up.
- Under the shared display lock, `--mode mutations --engine fast --case design-status-capped
  --case dialog-unanswered-row`: 2 requested, 2 caught at named assertions; both source and class restore flags
  true, restored runs green without skips. Total engine elapsed 9.8s. No full local mutation sweep.
- Preflight: 43 registered frame suites, 628 anchors. No controls removed or grouped.
- Strict MkDocs, whitespace check and public-data sweep: clean before commit.

## Timings refreshed, not performance proved

Three complete published collector artifacts trained the medians: runs 36861284238, 36859073552,
36854482047. Run 36864001774 was held out. 559 controls have at least three observations; remaining
controls use the conservative 8.0s fallback. The held-out run contains all 628 controls. The current eight-worker plan has 78/78/79/78/79/79/79/78 controls
and 72/74/76/78/76/75/72/78 baseline selectors (601 total; this is an allocation count). The JSON records
run/revision provenance and the allocation model. These are historical measurements with the old baselines,
not eight-worker implementation trials or cached evidence of current correctness.

## Misses and failed attempts retained

- The first test command replaced PATH without Maven's installation directory; exit 127, no tests executed.
  Retrying with the normal Maven path produced the headless counts above.
- Adding phase data exposed an incomplete fake engine in the Python tests (MagicMock not JSON serializable,
  then a missing prepare method). The fixture now supplies real serializable phase fields and a no-op prepare.
- The initial safe historical-registry reader missed append/extend forms: the exact-registry comparison failed
  with 15 missing controls. It now handles those literal operations without executing historical Python.
- The test-only selection probe initially missed a named class when its test fixture was not on disk. Matching
  the changed test filename fixes that and preserves explicit test-only coverage.

## Acceptance not yet verified

Real GitHub draft-to-ready/ready-to-draft transitions, supersession cancellation and unchanged-head transitions;
full-registry old/new parity; three paired complete timing trials; median/p95
performance targets. Source/configuration tests do not establish platform behaviour. MG-A7/A8 remain deferred
with grouping. Manual enforcement is the owner's decision, not a claim of GitHub protection.

The implementation adds `workflow_dispatch` for full diagnostic CI on the feature branch. It does not make
such runs interchangeable with PR merge-candidate evidence. No merge or release is authorised by these results.

## Full diagnostic CI at the implementation commit

RAN: [run 37298189215](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37298189215),
`workflow_dispatch` at `c1cd9aa7beb6893a8c4700cdb9e09f10790d5401`: success. Build 3179 / 0 / 0 / 248;
registered display 246 / 0 / 0 / 0 across 43 suites. Loop benchmark and engine self-test succeeded.
All eight workers succeeded. The collector reports 628 controls caught exactly once; downloaded
worker artifacts were independently recollected locally against the exact registry/source/plan,
again 628 complete. The feedback job was intentionally skipped because this was a full run.

Wall clock: 521 seconds (8m41s); all-job execution total 57.85 minutes (not billed minutes).
For comparison, the same API report for historical run 36864001774 gives 889 seconds (14m49s)
and 63.55 job-execution minutes. These are different runs/revisions, not controlled paired trials.
Worker engine durations were 415.1, 426.1, 387.1, 297.5, 270.2, 268.5, 389.7, 373.7 seconds.
Baseline invocation counts were 77, 74, 82, 99, 76, 75, 78, 87 (648 total), versus 601 distinct
selectors across the plan: parameterized witnesses can execute more than once. Baseline phases
were 11.02–32.14 seconds. These are one-run observations, not a median or a paired performance trial.

The UI job emitted a SummaryPanel null-pointer annotation despite passing. The same annotation
exists in baseline run 36864001774 (READ logs). No application code changed; this is retained as
an existing caveat, not silently called fixed. The branch diagnostic run does not replace a PR's
current merge-candidate validation. Later receipt changes are documentation only.

## Independent review correction round

The spec and implementation are now together on PR #107 (`spec/mutation-gate-iteration`);
PR #110 was closed as a duplicate. The feature branch is not a second delivery PR.

See [the independent local review](../../review_pr107_mutation_gate_2026_10_05.md).
Four implementation assumptions missed by the original tests were falsified: all mutation modes
were not termination-safe; killing only a parent did not prevent later class writes; unknown
historical registry statements were not rejected; and timing reports omitted earlier attempts.
These were discovered during review, not predictions committed before implementation.

All four now have discriminating regressions. The initial review regressions ran before the fixes:
42 tests / 8 assertion failures / 0 errors / 0 skips (five failures are parser subtests).
Two additional descendant tests ran against the original helper: 2 / 2 / 0 / 0, both named late-write
assertions. Byte-copy/SHA-256 restoration was verified. Final Python results: 44 / 0 / 0 / 0 iteration,
21 / 0 / 0 / 0 sharding (overlap), 5 / 0 / 0 / 0 harness, 5 / 0 / 0 / 0 classifier.
The independent agent's final local correctness recheck passes; it does not close acceptance experiments.

The first Maven attempt in this round recorded 3179 / 0 / 36 / 248: all 36 errors were sandbox
loopback-socket refusals. Its reports and log were retained separately before retrying with socket access.
The duplicate PR's failed baseline and cancellation are retained in the review; the later successful
PR #107 run is evidence of a successful attempt, not proof the earlier failure is fixed.

## Final local verification of the corrections

RAN: Maven retry with loopback access: 3179 / 0 / 0 / 248, 419 reports, no orphans.
Engine self-test: 27 checks, none failed. Preflight: 43 frame suites, 628 anchors.
Under the shared display lock, comparison of class and method baselines caught the real control
and retained both deliberately surviving controls in both variants. The two focused controls
`dialog-unanswered-row` and `design-status-capped` were both caught, restored byte-identically
(source and classes), and rerun green; engine elapsed 12.9 seconds. No full local sweep.
Strict MkDocs, whitespace and public-data checks were clean. The independent agent's final local
correctness verdict is passes; the declared platform and performance acceptance gaps remain.

## External correction N1 and residual N2

RAN before correction: the detached-descendant regression returned after 5.05s, failing the named
3s upper bound. The reference Maven-runner test initially failed at missing-suite rather than its
intended helper assertion; its fixture was corrected and then failed at the named helper-use assertion.
The timeout pipe drain is now bounded to one second, preserving partial output. A descendant that
explicitly creates a new session is outside process-group containment; this helper is not a sandbox.
The reference Maven runner now uses the same bounded owned-process helper. Nonzero runs with no
suite report remain unsuccessful, never invented green evidence.

Miss: the first bounded-drain implementation tried to kill an already-stopped group again in finally,
producing PermissionError on the local platform. Cleanup is now idempotent. Both attempts were kept;
this was not resolved by widening permissions. The final 46-test run was green before adding the
explicit reference-baseline CLI regression. Timing wording now states the latest attempt interval.

An explicit --baseline-policy classes option retains the reference execution path for acceptance
trials. Normal runs still use methods; the evidence records the chosen policy. No production control
or witness has been changed. CHANGELOG now names the development-facing improvement.

The local recheck of N2 exposed an additional wrong-result witness: a named assertion followed by
a process timeout could count as caught. Three regressions failed before correction (predicate,
actual fast JSONL parsing, collector). Fast results now preserve abnormal exits and explicit normal
completion. The full collector requires the completion field to be true; a missing-field control also
failed before correction. Evidence schema is 3, so old schema-2 receipts cannot satisfy new collection.
The launcher regression covers raw exits 1, 124 and -15, not only the timeout code.
