# Preview battery R3 — operator checkpoint

**In progress; no full-battery or release verdict.** The target is the corrected playground
`feat/project-starter-journey@3bdcfbd`, selected by name before the trial. This tests preview
acquisition of the Audit analyser bundle, not unguided catalogue discovery or public acquisition.
The [protocol](evidence/battery-r3-2026-09-20/PROTOCOL.md), exact task quotations, preface and
predictions were sealed before the first client started. The target stays unchanged during the matrix.

## Clock concern resolved before starting

The prior R2-3 discrepancy coincides with two host idle sleeps in the retained `pmset` log.
Python's macOS monotonic clock uses `mach_absolute_time`, which excludes sleep; Apple's installed
SDK describes `mach_continuous_time` as the corresponding clock that advances during sleep.
This explains the direction and concentration of the gap; the coarse sleep log is not an exact
nanosecond reconciliation. R2's original evidence is unchanged.

The R3 recorder uses suspend-inclusive continuous elapsed time for caps, retaining UTC and awake
monotonic readings separately. Each client holds an idle-sleep assertion. A preregistered 60-second
child-process probe completed with 13 samples, maximum sample UTC drift 0.258 ms, maximum apparent
suspend difference 0.051 ms and maximum receipt latency 4.748 ms. All are below its frozen 100 ms
limits. Two unit tests cover a simulated long suspend and a backward UTC adjustment. This probe
validates this measurement path, not general host clock accuracy or model compatibility.

The published runtime `1.0.16` bytecode uses `System.currentTimeMillis` through its runtime clock for
audit processing times; it does not use Python's awake clock. Eighteen R2 audit exports have no
backward `logTime` or end-before-start records. That supports the observed ordering, not a latency
benchmark. Wall-clock audit times can include sleep or clock adjustments.

## Instrument decisions

Every assistant tool-call batch stays in the routing denominator. Missing, conflicting or invalid
source pointers are **unattributed**; unseen references are **unverified**. Observed file reads do
not establish why an action was chosen. Assistant message identifiers in the raw transcript group
stream fragments into batches. No fabricated journal bridges the old and new scoring units.

The R2 compatibility runs are repeats of one applicable task. They do not establish recurrence
across different tasks. R3 reports cohort and task separately. A future routing change still needs
the spec's independent-session and different-task evidence.

## Results so far

| Cohort / task | Result | Independently checked |
|---|---|---|
| 1 / T1, Sonnet | Normal completion, 191.149 s; no provider refusal or substantive intervention | Preview ZIP acquired; 43 originals byte-identical to the pristine download; five input rows match five audited business records; successful server stop in transcript |
| 1 / T2 | Running | Fresh context, same project; generation credential provided outside the project |
| Remaining eligible tasks | Not run yet | No result claimed |
| T5, all cohorts | Ineligible | Vendor catalogue entry has not shipped |

T1 initially guessed several download URLs that returned 404, then discovered the website's
agent instructions and scaffold endpoint without an operator hint. This friction stays in the
transcript. The file sink is empty; only the audit processing output is verified. No sink scenario
is counted. The T1 clock had less than 1 ms UTC drift and no material suspend gap.

The first T2 launch was rejected by the operator command sandbox before the client started
(`sandbox_apply: Operation not permitted`). Its empty transcript, stderr and metadata are retained
separately. The unchanged runner was then launched with the required host permission. This is an
environment launch failure, not a subject/provider refusal or a product result.

## Still open

- Full eligible battery, manual attribution, static-trap classification and prediction scoring.
- Playground launch advice should derive from one shared launch decision across all emitted
  surfaces, with cross-template agreement tests. PPF-1/PPF-2 fixes remain verified; this further
  structural work is not claimed complete and must not change the sealed preview mid-trial.
- Starter-core `1.0.72` still returns HTTP 404 at the authoritative Repsy coordinate. Public artifact
  parity and public download/setup acceptance remain blocked. The working customer generation route
  uses different, already published coordinates and does not close this gate.
- Witnessed browser graph preview and analyser OS-process restart remain separate release gates.

Private raw transcripts, pristine acquisitions and task snapshots are under the git-ignored
`.local-evidence/coldstart-v2-2026-09-20/battery-r3/`. The public evidence directory contains the
sealed protocol, predictions, clock measurements and aggregate audit-time checks; no credentials
or private model reasoning are published.

Checkpoint gates: `mvn -q test` — 1,718 tests, zero failures/errors, 40 display skips;
`python3 -m unittest discover -s tools -p test_coldstart_clock.py` — two tests pass;
`git diff --check` and rule-1 sweep over tracked and new public files — clean.
