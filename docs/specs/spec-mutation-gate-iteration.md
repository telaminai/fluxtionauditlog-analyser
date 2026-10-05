# Mutation gate: fast iteration, complete merge evidence

**Status: PROPOSED r1 — 2026-10-05.** Documentation only. No workflow, runner, test or
branch-protection change is implemented or accepted by this document.
**Source baseline:** main `53c0386e` (application released as 1.31.0).
**Tracker:** MG-1. **Delivery:** one implementation PR, independently reviewed as a package.

## 1. Outcome and scope

Routine development should not wait for hundreds of unrelated mutation trials. The final
merge candidate must still prove that every registered control is caught by its own witness.
Reduce repeated work and superseded runs before weakening evidence or deleting controls.

OWNER: request a single specification combining minimal local mutation checks, cancellation
of superseded PR runs, additional CI workers and opportunities to combine controls. The
specific choices below are proposals for review, not further owner decisions.

The ordinary Maven suite and the registered display gate remain. This proposal changes
when mutation checks run and how their work is scheduled; it changes no analyser behaviour.
It supersedes the execution policy, not the historical evidence, in the
[earlier gate proposal](../proposals/completed/faster-mutation-gate.md).

## 2. Measured starting point

RAN means commands or artifact calculations executed while preparing this proposal. READ
means source inspected; these are not measurements of a proposed implementation.

| Evidence | Observation |
|---|---|
| RAN: GitHub jobs API and downloaded `mutation-shard-*` artifacts, [main run 36864001774](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36864001774), revision `787f2e1a` | 628 controls caught; four workers; workflow start to collector completion about 14m48s. Build 1m52s; display job 2m39s. |
| RAN: sum of artifact `runs[].seconds` | Per-shard control work: 699.5, 691.3, 642.6, 694.6 seconds; setup plus baseline: 121.8, 134.7, 125.6, 119.3 seconds. These are combined categories, not a profile of JVM/compiler cost. |
| RAN: registry grouping by target and exact `(site, old, new)` | 628 controls; 502 distinct target methods; 155 target classes; 113 source sites. Twelve groups of two have identical mutation edits but different witnesses. |
| RAN: artifact baselines | 805 + 879 + 866 + 817 = 3,367 test executions, for 150 + 151 + 150 + 152 distinct witness methods assigned across the shards. Some methods belong to several shards. |
| RAN: timing-file coverage | 227 current controls have recorded weights; 401 use the 4.2-second default. |
| RAN: replay allocation using that run's actual per-control durations | Eight workers: 341.2 seconds maximum control work, before setup/baseline/queueing. This is a scheduling estimate, not an eight-worker trial. |
| READ: `run_gate`, `FastEngine.control`, `GateLauncher`, `mutation_shards.collect` | Whole-class baseline; single-file compile with API-change fallback; fresh JVM per mutated/restored witness; exact class-tree restoration; independent collector. |
| READ: `select_subset`, `.github/workflows/ci.yml` | Direct-dependency heuristic exists and explicitly cannot guarantee transitive coverage. Full runs currently use four shards; no supersession concurrency group. |

No mutation or product trial was run to write this specification. These figures describe
one full CI run, not a claimed long-term percentile. Baselines are already shared within a
shard; the proposal reduces their scope, not a nonexistent per-control baseline.

## 3. Test policy (MG-D1)

| Stage | Ordinary checks | Mutation work | Meaning |
|---|---|---|---|
| Local edit/experiment | Relevant logic tests; relevant frame tests sequentially under the display lock | None by default | Development feedback; no mutation claim |
| Local defect correction / new protection | Regression reproduces the wrong result; relevant tests green | New/changed controls and specifically implicated existing ones, once the fix stabilises | Rule 8 evidence for that correction |
| Draft PR | Existing build, static/preflight, display and loop checks retain their existing policy | Changed-control/affected subset, unless MG-D2 requires full | Explicitly partial feedback |
| Ready PR, including each subsequent code push | Existing ordinary CI checks | Complete registry, eight shards | Merge-candidate evidence |
| Push to main | Existing ordinary CI checks | Complete registry, eight shards | Integrated-tree evidence |
| Release | Existing release tests and built-jar acceptance | Use successful full main CI on the candidate code; no ritual local full rerun | Release receipt cites exact revision and scope |
| Proven docs-only change | Existing classifier and build policy | None, as today | Explicit docs-only exemption, not “all controls caught” |

A regression test and its control are still required for a runtime correction under rule 8.
Minimal local checking means no repeated full gate and no automatic mutation run after every
edit. It does not mean accepting a control never demonstrated to fail at the intended assertion.
CI may provide that demonstration; do not claim it ran locally if it did not.

Local commands remain explicit: `--case NAME` for a known correction, or
`--changed-since origin/main` for a broader, clearly labelled heuristic. Never run two mutation
processes in one worktree. A shared desktop still permits only one display test process at a time.

Update CLAUDE guidance, onboarding, release procedure and reviewer/implementer instructions in
the implementation PR so old prompts do not re-impose full local gates. Preserve the existing
headless-suite and display requirements; this spec does not silently reduce ordinary coverage.

## 4. PR lifecycle and complete evidence (MG-D2)

The workflow listens to `pull_request` opened, synchronize, reopened, ready_for_review and
converted_to_draft events, plus pushes to main. A transition to ready MUST run the full gate
even when no commit changed. A new commit on a ready PR also runs full; switching it back to
draft intentionally restores iteration mode. There is no “full once per PR” exemption.

Draft selection is the union of:

- the existing affected-file heuristic;
- new or changed registry entries, identified by semantic tuple including witness, not line number;
- entries whose named witness changed (including test-only changes).

Removed controls and changed mappings are listed explicitly for review. An unparseable registry,
unknown diff base, selection failure, or changes to the harness, collector, build, CI or shared
runtime fixtures force the full set, including on a draft. A large genuinely affected subset
may still be expensive; it is never truncated to make a timing target. Empty selection says
“0 selected; N not run”, not “N passed”. All unrun names and selection reasons are retained.

Use separate result names and schemas:

- `mutation-feedback`: partial draft checks; publishes selected/caught/not-run names and revision.
- `mutation-gate`: the full collector, or the existing proven docs-only exemption.

For a code-bearing draft, `mutation-gate` must NOT be a skipped-success job. It runs a cheap
explicit non-success result saying “full gate deferred while draft; mark ready to run”. This
makes a red full-gate check on a draft intentional; fast feedback remains separately readable.
On a ready PR it succeeds only from complete evidence for that run's checked-out revision.
Partial artifacts cannot be accepted by the full collector. A cancelled, errored, skipped or
missing worker cannot yield success. The docs-only exemption remains visibly separate.

Rationale (READ, [GitHub status-check documentation](https://docs.github.com/en/pull-requests/reference/status-checks)):
skipped jobs can satisfy required checks. Simply skipping a job called `mutation-gate` for
drafts would misrepresent its meaning. The reviewer must validate real draft-to-ready behaviour,
including unchanged head SHA, rather than relying only on a YAML assertion.

Record PR head, base and actual test-merge SHA. Require complete evidence on the current merge
candidate; a changed base invalidates the earlier candidate. If the platform does not schedule
a fresh candidate automatically, update the branch through the normal integration process or
rerun the PR workflow. Never reuse old evidence on the basis of a matching branch name.
A manually dispatched workflow is diagnostic, not assumed to satisfy PR-required checks:
[GitHub documents event restrictions](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).

Branch protection is a separate owner setting. Check and report its actual state. Without a
ruleset requiring the gate/current candidate, these remain explicit merge/release obligations;
this PR must not claim GitHub prevents bypass. No automatic merge is introduced.

## 5. Supersession and workers (MG-D3, MG-D4)

**MG-D3:** cancel obsolete runs of this CI workflow for the SAME PR, including draft/ready
transitions. Use a workflow-specific, PR-number concurrency key. Non-PR runs get unique run
keys; different PRs, main pushes, release and Pages workflows cannot cancel one another.
Do not share one main concurrency group even with `cancel-in-progress: false`: pending work
can still be replaced. Main release evidence must not disappear through PR optimisation.
See [GitHub concurrency semantics](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#concurrency).

Cancellation is not a caught control. Persist results incrementally; upload partial artifacts
and rejection reasons with `always()` where the runner remains alive. Hard cancellation may
prevent upload: absence remains absence, never synthesised success. Isolated ephemeral CI
workspaces contain interrupted mutations; local cancellation must still restore in `finally`.
Matrix fail-fast remains false within one current run so one failing worker does not erase
other workers' diagnostic evidence.

**MG-D4:** eight full-gate workers, each with an independent checkout, class trees and Xvfb.
One authoritative shard-count setting feeds allocation, matrix and collector; no independently
maintained literal fours/eights. Validate it, cap it to useful work, and record it in evidence.
Insufficient runner capacity delays work; it never reduces the selected control set.

Refresh scheduling weights from several complete CI artifacts, with run/revision provenance,
medians and held-out evaluation as the existing timing procedure requires. The registry, not
the timing file, defines membership. Missing timings receive a conservative fallback. Keep
`design-status-capped` first in its assigned shard, once. Keep the engine self-test as a
separate parallel job. Report total runner-minutes as well as elapsed time: eight workers
can cost more through duplicated setup even while reducing the wait.

## 6. Share only work whose meaning stays the same

### 6.1 Named-method baselines (MG-D5)

Replace each shard's whole-class baseline selectors with its deduplicated named witness
methods. Use the existing JUnit launcher so parameter injection, superclass lookup and
lifecycle callbacks retain their semantics. Every requested witness must actually execute,
with zero failures, errors or skips. Missing methods, discovery/container failures and empty
results fail the baseline. Do not replace an executed baseline with a previous run's cache.

The ordinary whole-class/full suite remains elsewhere in CI. The mutation baseline proves
its selected witnesses pass before mutation; it is not another full regression suite.

### 6.2 Identical mutations, multiple witnesses (MG-D6)

A group may share compilation/restoration ONLY when site, original source bytes, exact old
text and exact replacement bytes match. Include site kind and engine/schema identity in a
stable, versioned group key; do not group by file, target method, similar name or intention.
Changing two different lines together is NOT this optimisation.

Preserve every control ID. Twelve duplicate-edit pairs at the measured baseline are candidates,
not a hard-coded group list and not permission to delete twelve controls. If one edit protects
two entrances, both witnesses still have to fail independently.

Execution per group:

1. Verify each anchor and the original digest; establish the green baseline witnesses.
2. Apply the one mutation and compile once, retaining all API/annotation full-compile fallbacks.
3. Execute each distinct named witness in its OWN fresh JVM. Every witness must fail at its
   required assertion, with no error or skip accepted as a kill. Run all group witnesses so a
   first failure cannot hide a second survivor. Check that source/classes remain the prepared
   mutant between witnesses; a test rewriting them fails the group rather than contaminating it.
4. Restore original source and both class trees from byte copies, even after failure/cancellation;
   compare exact bytes/digests. No `git checkout` restoration.
5. Execute each distinct restored witness in its OWN fresh JVM; all must pass without skips.
6. Emit one group record and explicit control-to-witness mappings. Shared results must be
   referenced as shared, not fabricated as separate executions.

This deliberately moves the restore boundary from each duplicate control to the single edit
shared by that group. Each unique mutant remains isolated from the next. Restored-green runs
are NOT deferred across different mutants, and unrelated restored tests are not batched in
one JVM in this delivery. Swing state, static caches and timers are not reset by copying class files.

A shard owns the whole group; it cannot split member controls across workers. Use measured
group costs once available. Until then, sum member weights as a conservative estimate; do not
assume one witness's duration pays for all witnesses. Schedule intact groups longest-first.

### 6.3 Collector and evidence schema (MG-D7)

Introduce an explicit schema version. The collector independently reconstructs groups and
allocation from its own registry and source. It checks exact revision, registry/plan identity,
all expected groups and all control IDs exactly once, source and mutant digests, unique witness
coverage, named assertion failures, byte-identical restoration and restored-green results.
Validate that each mapped witness is the one declared for that control. Two controls may
legitimately reference one identical edit/witness result; a wrong or missing mapping cannot pass.

Reject old/incompatible schemas rather than silently interpreting missing fields as true.
Artifacts come only from the current run and consistent attempt; a failed-job rerun cannot mix
plans, revisions or schemas. Retain a machine-readable rejection result on collector failure
alongside the shard artifacts. Check listings continue to distinguish controls, groups and
actual test executions; e.g. “628 controls across 616 mutation groups” only when recomputed.

### 6.4 Deferred optimisations (MG-D8)

No persistent JVM across different mutants, bytecode switchable mutants, classloader-only
Swing isolation, removal of restore checks, cross-revision result caching, probabilistic
sampling as a merge gate, or simultaneous different faults in one mutant. Redundant controls
can be retired later with a specific reviewed equivalence argument, never solely because they
share a test. Grouping the twelve pairs saves some compiler work; it cannot by itself halve CI.

## 7. Time budgets and instrumentation (MG-D9)

Planning targets, not permission to skip work:

- Local feedback: targeted ordinary tests; mutation only at correction milestones.
- Draft feedback: aim for a few minutes for a small change. Show selection size and predicted
  time before execution; a broad dependency change is allowed to exceed the target honestly.
- Full gate: target median at most eight minutes and p95 at most ten minutes when capacity is
  available. The current eight-worker estimate is roughly seven to nine minutes including
  overhead; this remains unverified until measured.

Record queue delay, checkout/setup, compile/classpath preparation, baseline, mutation compile,
API/fallback inspection, red-test JVM, byte restoration/hash check, restored-green JVM,
artifact upload and collector time. JVM phases include startup plus execution unless further
instrumented; do not label the whole phase “test execution”. Count fallbacks, groups, controls,
selected methods, actual invocations and skipped/unrun controls. Report runner-minutes too.

Budgets produce warnings and investigation, not truncated evidence or a green timeout.
Retain bounded process/job timeouts. A hung witness must terminate and fail the gate. Refresh
weights from several complete runs and display slow groups and imbalance in the job summary.

## 8. Acceptance and wrong-result witnesses

All are required unless explicitly described as a performance target. Counts and timings must
come from the implementation's head, not this document's historical numbers.

| ID | Check that must discriminate the wrong result |
|---|---|
| MG-A1 | Policy table tests: local/default iteration never launches full mutations; a new runtime correction still requires its named red witness before closure. Updated instructions agree. |
| MG-A2 | Draft with a small production change runs partial feedback; full `mutation-gate` is explicitly not satisfied. Ready transition without a new commit executes full. A new ready-PR commit cannot reuse prior evidence. |
| MG-A3 | Real GitHub lifecycle exercise: rapid successive pushes cancel obsolete same-PR runs; different PR/main/release runs are unaffected. Convert ready to draft and back; stale successes cannot satisfy final acceptance. Record check conclusions at each SHA. |
| MG-A4 | Selector tests: new/changed controls, test-only changes, removals, empty subset, unknown base and shared-infrastructure changes. A selector that omits a new control fails a named assertion. Indirect dependency uncertainty stays disclosed. |
| MG-A5 | Eight-worker plan covers the entire live registry exactly once, keeps identical groups intact and the early Linux control first. Missing timing entries cannot omit a control. Capacity delays remain distinguishable from worker runtime. |
| MG-A6 | Named baseline executes precisely the requested methods, including parameter-injected/inherited methods and lifecycle callbacks. Missing/aborted/container-failing witnesses fail. Plant an unrelated failing method: selected baseline excludes it, while ordinary full-suite CI still catches it. |
| MG-A7 | Two controls share exactly one edit, with two different witnesses: both go red; only one compile/restore occurs. A shared witness must not create false counts. Change replacement/source digest and the group splits. |
| MG-A8 | Plant a group where one witness catches the edit and the other survives: collector rejects, naming the survivor. Repeat with error, skip, wrong method, missing mapping, altered mutant bytes, and restored-red witness. |
| MG-A9 | Restore after compile failure, assertion failure, timeout and catchable cancellation; source/classes byte-identical and restored test green where executable. Force the existing API/constant/annotation fallback; grouping cannot evade it. |
| MG-A10 | Collector adversarial tests: duplicate/missing/control in wrong shard, stale revision, wrong source or plan, incompatible schema, forged restore flag and partial feedback masquerading as full. Each rejects for the intended reason; rejection evidence retained. |
| MG-A11 | Old and new engines at the SAME implementation revision and registry agree control by control, including intentional surviving probes. Preserve all attempts. A green ordinary test run alone is not parity evidence. |
| MG-A12 | Three paired complete measurements, alternate old/new order on equivalent runners, record cache state and queue time. Compare correctness first, then elapsed time, per-phase costs, fallback count and runner-minutes. Report median, range and target misses; p95 only after enough ongoing runs. |
| MG-A13 | Full implementation-head CI green: build, display zero skips, self-test, all workers and collector. Exact plan/registry/control counts published. An incomplete or cancelled run is never the accepting evidence. |
| MG-A14 | Docs-only classification remains conservative; unknown files/diffs do not gain an exemption. Main and release identify the actual full code-validation revision and subsequent docs-only deltas. No “all caught” claim on a docs-only run. |

MG-A11/A12 are intentional full trials for changing the harness itself. They do not authorise
normal developers or reviewers to repeat full local gates on unrelated features. Prefer
isolated CI runners for comparisons; local trials must respect the shared display lock.

## 9. One-PR implementation and review plan

Keep this specification, implementation and evidence reviewable as one PR, with separate commits:

1. Seal predictions; add phase metrics and policy/collector self-tests, including intentional survivors.
2. Implement lifecycle/cancellation and eight-worker scheduling; record actual event/check behaviour.
3. Implement named baselines, then identical-edit grouping and the versioned collector contract.
4. Refresh weights; run parity and paired timing trials. Preserve red/aborted attempts and explain misses.
5. Reconcile guidance, tracker and historical proposal pointers; obtain independent review of the package.

The rollout must not enable partial checks under the full check name between commits. Keep a reference
ungrouped path for parity and rollback during this PR. Reverting the optimisation returns to the full
ungrouped gate, not to skipping mutations. No application or generated-session changes are expected.

Suggested files: `.github/workflows/ci.yml`, `tools/verify_project_chart_review.py`,
`tools/mutation_gate_fast.py`, `tools/mutation_shards.py`, `tools/mutation_timings.json`, their tests,
`CLAUDE.md`, `docs/ONBOARDING.md`, and `docs/admin/release-process.md`. Registry content remains unless
an entry change is independently justified. No user-facing application changelog claim for a CI-only change.

## 10. Review decisions and revision record

Review the package particularly on: the intentionally unsatisfied draft full-gate check; eight-worker
runner cost; the grouped restoration boundary; method-only baselines; the completeness of event triggers;
and whether the performance target is met without disguising missing checks. Branch protection remains
an explicit owner setting, not a setting this specification silently changes.

**r1 — 2026-10-05:** initial combined proposal. READ source and GitHub workflow semantics; RAN registry
analysis and calculations against downloaded CI evidence. No gate implementation or performance gain claimed.
