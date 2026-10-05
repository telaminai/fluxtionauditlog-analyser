# Mutation gate iteration: predictions before implementation

Base: spec r2 at `744379aa`. These are predictions, not results.

1. Named-method baselines exclude unrelated methods while retaining injected parameters, inherited tests and lifecycle failures. Existing launcher support should suffice without Java changes.
2. Draft selection needs registry-aware comparison: the current file heuristic can miss a newly registered control whose site did not change. Unknown base/registry/shared infrastructure must select full.
3. Eight workers reduce the longest control allocation; named baselines reduce duplicated setup work. The eight-minute target is unverified until paired complete CI measurements.
4. Collector checks need explicit scope/schema and a digest of registry/source/allocation; partial feedback must fail even if its runs all claim caught.
5. Cancellation must use a PR-specific key, with unique keys for other runs. Actual GitHub event/check conclusions require CI trials, not just YAML tests.
6. Existing source/class restoration and compile fallback behaviour can be preserved unchanged. No grouping or persistent witness JVM is necessary.
7. The full 628-control set remains; no controls are retired. Timing refresh must use several complete runs and disclose any missing evidence.
8. Manual merge/release enforcement stays in force. No repository protection setting is changed.

Validation plan: targeted Python policy/collector tests and executable launcher self-tests, full headless suite, selected real controls under the display lock, preflight, strict docs and public-data sweep. Full parity and paired timing evidence should use isolated CI runners; retain any failure and mark unrun acceptance honestly. No full local mutation sweep during ordinary iteration.

## External review and acceptance completion (P3, before changes/trials)

- P3.1: a detached descendant retaining stdout reproduces the unbounded drain; a bounded drain retains partial output and returns 124 without waiting for that descendant's lifetime.
- P3.2: the reference Maven runner can use the same owned-process helper without changing successful suite parsing; missing reports after timeout must not become green evidence.
- P3.3: full-registry class and named baselines at one implementation revision catch the same 628 controls. Intentional surviving probes remain survivors in both variants.
- P3.4: three alternating four-worker/class versus eight-worker/method CI pairs reduce wall time, with cache state and queue delay reported rather than assumed equal. A timing miss does not truncate checks.
- P3.5: a temporary narrow-change PR based on an isolated acceptance branch exercises partial draft failure, unchanged-head ready transitions and same-PR supersession; unrelated PR/diagnostic runs remain independent. The temporary workflow only adds the acceptance base to its PR branch filter; it cannot be merged into main.

These are predictions, not results. All failed or cancelled trial attempts will be retained.
