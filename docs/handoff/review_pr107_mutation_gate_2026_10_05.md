# PR #107: independent local review and corrections

Review basis: `744379aa..525a95f5`, followed by a recheck of the correction diff.
A separate local agent performed the review and both rechecks without editing the implementation.
This report records its findings and the implementer's executed regressions separately.

## Local correctness verdict

The independent agent found four required corrections. Its final recheck finds all four resolved,
with no new correctness blocker. This is a local correctness result, not merge approval: current
candidate CI and the declared acceptance experiments below remain separate.

| Finding | Evidence and correction | Regression witness |
| --- | --- | --- |
| R1: comparison modes bypassed SIGTERM restoration | RAN: real SIGTERM left temporary source mutated in `compare-baselines`. The handler now surrounds every mode in `verify_project_chart_review.main`. | `ReviewCorrectionsTest.test_compare_sigterm_restores_the_real_mutated_source` |
| R2: timed-out subprocess descendants could write after restoration | RAN: a descendant changed a temporary class after restoration flags were true. `mutation_gate_fast.run_with_timeout` now owns and stops the process group before returning or propagating interruption. | Timeout, SIGTERM and normal-parent-exit descendant tests in `ReviewCorrectionsTest` |
| R3: unsupported registry transformations were silently ignored | RAN: conditional append omitted a historical control. `mutation_policy.assignments` now rejects unknown module statements and mutable aliases; selection falls back to the full registry. This was a removal-report gap, not a demonstrated full-gate bypass. | `test_unknown_registry_mutations_refuse_instead_of_silently_dropping_controls`; existing full-fallback test |
| R4: run timing omitted earlier attempts | RAN with mocked documented API responses: a failed minute followed by a successful minute was reported as one minute. `mutation_run_report` requests `filter=all` and retains job ID and attempt. | `test_run_report_keeps_failed_jobs_from_earlier_attempts` |

## Executed evidence

- Before the fixes: iteration module 42 tests, 8 assertion failures, 0 errors, 0 skips.
  Five failures were subtests of the registry test; the other three reproduced R1, R2 and R4.
- Two additional permanent descendant regressions against the original helper at `525a95f5`:
  2 tests, 2 assertion failures, 0 errors, 0 skips. Both observed `late mutant` after cleanup.
  The source was restored from a byte copy and its SHA-256 checked. No Java classes were mutated.
- Restored implementation: iteration 44 / 0 / 0 / 0; sharding 21 / 0 / 0 / 0;
  project-chart harness 5 / 0 / 0 / 0; docs classifier 5 / 0 / 0 / 0.
  Iteration includes sharding tests: these counts overlap and must not be summed as distinct tests.
- Independent agent reran those four Python suites. It also executed temporary real-process probes
  for SIGTERM and normal parent exit; neither descendant performed its scheduled late write.
- The agent READ the final signal guard, process cleanup, registry rejection and all-attempt API path.
  No full local mutation sweep was run.

## Retained CI and verification limits

PR #107 run `37299880959` at `525a95f5` succeeded. That predates the corrections and cannot certify them.
The briefly opened duplicate PR #110 was closed in favour of #107. Its run `37299827217` was cancelled,
but shard 0 had already failed its baseline at
`BundleProvenanceFrameTest#aBundleCarriesItsEventProcessor` (`theRecipientCanStillChooseWithinTheSession`).
Zero controls ran in that shard. The failed attempt is not discarded or described as a passing retry.

READ: the fixture waits for provenance publication, then changes configuration directly and asserts
off the EDT. Asynchronous processor inference may still be in flight. This is a plausible fixture race,
not a reproduced root cause or proof that named baseline selection is unrelated. No application or
fixture change is included in this PR to hide the failure.

Not established by this local review: actual draft/ready/supersession lifecycle experiments,
full-registry old/new parity, three paired complete timing trials and median/p95 targets.
MG-A7/A8 grouping remains deferred. The owner retains manual merge/release enforcement.
The correction commit must receive its own current-candidate CI before any merge decision.

## Final local verification of the corrections

RAN: Maven retry with loopback access: 3179 / 0 / 0 / 248, 419 reports, no orphans.
Engine self-test: 27 checks, none failed. Preflight: 43 frame suites, 628 anchors.
Under the shared display lock, comparison of class and method baselines caught the real control
and retained both deliberately surviving controls in both variants. The two focused controls
`dialog-unanswered-row` and `design-status-capped` were both caught, restored byte-identically
(source and classes), and rerun green; engine elapsed 12.9 seconds. No full local sweep.
Strict MkDocs, whitespace and public-data checks were clean. The independent agent's final local
correctness verdict is passes; the declared platform and performance acceptance gaps remain.

## Final acceptance addendum

The earlier acceptance gaps are superseded by the [acceptance receipt](evidence/mutation-gate-iteration-2026-10-05/ACCEPTANCE.md):
six complete parity phases, three paired timing observations, and real draft/ready/supersession trials.
The independent local agent accepted code head `5a4d1977` and independently recollected all six phases.
No required code correction remains. Ready for owner merge once the final documentation head's CI is green.
The disclosed baseline flake is tracked in #112; performance limits and deferred grouping remain explicit.
