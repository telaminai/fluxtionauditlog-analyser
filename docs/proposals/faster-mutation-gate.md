# Proposal: make the mutation gate fast enough to stay mandatory

**Status:** implemented in #18 (merged as `7254c29d`): `--engine fast`, `--mode compare`, `--changed-since`,
`--mode selftest` and a `mutation-gate` CI job running the full set on every PR and push to `main`. The text
below is the proposal as reviewed, with one factual correction marked in place. Raised 2026-09-24 after #13
landed, at the owner's request:
*"have both faster mutation gate on merge to main and only subsets of gates on a branch"*. Written by the
session whose work the gate keeps catching.

## Follow-up: four isolated CI shards (2026-09-26)

Implementation on `gate/parallel-mutation-shards`; review and merge remain separate from implementation.
The sequential CI job grew with the registry: run 36271215353 took 16m53s, including 14m35s for controls
and 1m39s for the engine self-test. Run 36270986642 recorded 174 controls, all using the fast path.

The complete registry is partitioned across four jobs. Each job has its own checkout, compiled trees and
Xvfb display; controls inside a job still run sequentially with fresh JVMs. Each shard establishes its
baseline, requires every mutation to fail its named assertion, restores source and compiled bytes, and
reruns the named test green. No assertion, restoration check or control is dropped for speed.

`tools/mutation_timings.json` records durations and provenance from the earlier CI artifact. Longest-first
allocation distributes expensive controls across workers. Timing data is only a scheduling hint: the live
`CASES` registry supplies the required set, and a new control without timing data receives the default
weight. Old timing entries never create controls. Refresh timings from a complete CI artifact when the
balance drifts; include the run URL and revision. Each worker prints its assigned controls' estimated time.

The design-status-capped control runs first in its assigned shard, once. This preserves the early Linux
regression check without a separate compile, baseline and duplicate execution. The engine self-test runs
in its own parallel job. Matrix fail-fast is disabled so a failed control does not cancel other workers'
evidence. Partial results upload even when a worker fails.

The final job retains the name **mutation-gate**, preserving the existing required-check name. It requires
both the self-test and all workers to succeed, then independently recomputes the partition and verifies:
all four results are present, all refer to this checkout revision, every current control occurs exactly
once in its assigned shard, the source digests match, baselines are green, failures are at the named
assertions, both byte restores succeeded, and named restored tests pass without skips. Missing, duplicate,
stale, skipped or incomplete evidence fails the job. CI uses the complete set, never `--changed-since`.

For local diagnosis, run one shard **at a time on a shared display**:

```sh
python3 tools/verify_project_chart_review.py --mode mutations --engine fast \
  --shard-index 0 --shard-count 4 --output target/mutation-shard-0.json
```

The index is zero-based. Sharding refuses `--case`, `--changed-since`, other engines and other modes.
The existing unsharded invocation is unchanged. After obtaining all four results from the same revision:

```sh
python3 tools/mutation_shards.py --artifacts target --count 4 \
  --revision "$(git rev-parse HEAD)" --output target/mutation-gate.json
```

Elapsed time is expected to fall to roughly 5–7 minutes if runners start promptly; that is an estimate,
not acceptance evidence. Four setups and baselines may increase total runner usage. Actual timings,
checks, prediction misses and CI results belong in the implementation report.

## The cost, measured

All figures below were measured on this machine, JDK 21, at `main` = `4d787d1b` (post-#13), with a real
display. They are wall clock from `/usr/bin/time -p`, not estimates:

| | |
|---|---|
| Full suite, no clean, headless — 1,978 tests, 263 classes | **14.0 s** |
| One targeted class, nothing recompiled | **1.5 s** |
| One targeted class after touching one main source | **5.7 s** |
| One frame class on a display, nothing recompiled | **7.5 s** |
| One mutation control end to end, headless target (`dialog-unanswered-row`) | **12.7 s** |
| One mutation control end to end, display target (`repair-carry-unsaved`) | **22.0 s** |

The last two include that control's own baseline run, which the full set pays once and shares. The
marginal cost of a control in a full run is its two remaining Maven invocations: roughly **11 s** headless
and **20 s** on a display.

`CASES` currently holds **36 controls — 19 display, 17 headless**, over 11 distinct test classes. At the
measured marginal rates that is **about 9–10 minutes** of controls plus the shared baseline. Runs
observed during this cycle took longer than that, which is consistent with contention from other work on
the same machine rather than with the runner being slower than these numbers imply.

**Almost none of that is test execution.** The whole suite — 1,978 tests — runs in 14 seconds. A single
control runs one test method and costs 11 to 22 seconds, because each of its two invocations pays a whole
Maven lifecycle: JVM start, plugin resolution, and a full recompile of `src/main/java` triggered by the
one-file mutation. Seventy-two JVM starts and seventy-two recompiles for about thirty seconds of
assertions.

## Why the answer is not "make it optional"

This repository has already run a version of that experiment. **62 display tests were skipped by every
local headless run**, because running them needed a flag nobody passed, and every review in this cycle quoted
"62 skips" as a known cost.

*Corrected in review of #16:* an earlier version of this paragraph said they "never ran anywhere". They did:
CI's `ui-frame` job has run them under xvfb since 2026-09-16 (`b662bc33`), and on 2026-09-24 it reported
`Tests run: 63, Failures: 0, Errors: 0, Skipped: 0` on `main`. The accurate lesson is narrower and still
supports the argument: a skip count people quote as a known cost says nothing about whether the tests run
anywhere else, and the mutation controls themselves ran nowhere automatically until #18.

The mutation gate has a stronger claim on staying mandatory than most tests do, because of what it caught
in one day, all of it against the author of this proposal:

- a frame-test suite that was unregistered in CI and therefore never ran;
- that same suite surviving three simultaneous behaviour mutations — it asserted nothing;
- a `@TempDir` **parameter** that made a witness unmatchable by name, so it would have silently not run;
- three claims in commit messages that the artefact did not support.

A gate whose main function is catching the author's own false confidence is exactly the gate not to make
skippable.

## Two changes, both wanted

### 1. A faster engine — the full set, on merge to `main`

Replace the per-control Maven lifecycle while keeping the one property that makes the gate trustworthy: a
**fresh process per mutation**.

- one `mvn -o test-compile` up front;
- per control: `javac` only the mutated file into `target/classes`, then invoke the JUnit Platform Console
  launcher directly on the named `Class#method`;
- restore the bytes and re-run exactly as now.

The measurements say where the time goes, so they also say what this saves: the 5.7 s targeted run drops
towards the 1.5 s one once the recompile is a single file rather than the whole source tree, and the Maven
lifecycle disappears from both invocations. Isolation is unchanged — still a new JVM per mutation, still a
byte-identical restore, still a named-assertion check. **The estimate of what this lands at is not
measured, and the proposal should not be accepted on the strength of the estimate.** Step 1 of the
verification below produces the real number before anything is switched over.

This is what runs on merge to `main`: the whole set, every time, because that is the point at which
nothing should be taken on trust.

### 2. Subset selection on a branch — the controls the change can affect

On a PR branch, run only the controls whose `site` file appears in `git diff --name-only main...HEAD`,
plus any control whose target test class the diff touches. For a typical PR in this cycle that is 3–6
controls: **under a minute even on today's engine**.

**A subset is a heuristic, and the proposal is only honest if it says so.** A control can be broken by a
change to a file it does not name — coupling the diff cannot see. So:

- the subset is a fast signal on a branch, **never** a merge gate;
- the full set runs on merge to `main`, and a red control there blocks the merge;
- the runner **prints which controls it skipped and why**. A green subset must be unable to read as a
  green gate. Silence about what was not run is precisely how "62 skips" became invisible.

## How the change proves itself

A verification tool that changes needs its own verification, and it cannot be its own witness:

1. Run the current engine and the new engine over all 36 controls and compare verdicts control by control.
   Identical verdicts, or the new engine is wrong. This run also produces the real timing figure that
   replaces the estimate above.
2. Plant a control that must fail — a mutation with no covering assertion — and confirm both engines
   report it red. An engine that cannot fail is not faster, it is broken.
3. Keep the old engine behind a flag for one cycle, so a disagreement can be investigated rather than
   argued about.

**This belongs in its own PR.** Changing the harness inside a change that the harness is verifying is the
move that makes the evidence worthless.

## What is not proposed

Parallelising controls across worktrees. The display-gated controls contend for window focus — this cycle
already saw `PersonAtTheScreenFrameTest` skip twice on one branch and pass on another, minutes apart, on
the same machine. Running them concurrently would turn that intermittent into noise nobody can read, and
the gate's value is that a red result means something.
