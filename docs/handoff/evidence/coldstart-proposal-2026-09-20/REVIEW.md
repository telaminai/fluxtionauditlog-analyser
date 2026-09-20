# Intake review — cold-start instrument

2026-09-20. The four participant files are preserved verbatim, with SHA-256 manifest. They are a
proposal, not a completed acceptance run or an approved scoring oracle. No source files in the live
staged project were changed. This file qualifies the proposal where the original text overclaims.

## Keep

- Pre-action attribution, explicit `prior`, source-file imitation and a pristine-download baseline.
- Transcript as primary evidence, interventions recorded, both new-output correctness and non-regression.
- Manual assessment of oracle independence and surviving mutations. Scenarios must reach the fault and
  assertions must detect it; a survivor can also be an equivalent/ineffective mutation or harness failure.
- Clean acquisition, no owner-source access and no previous solutions in the subject's reachable files.

## Required before treating the scoresheet as acceptance

1. The familiar reporting exercise T4 is regression, not held out. Freeze a different private task and
   its applicable mutations. T1 permits a bare embedded application, so a main method is not intrinsically
   wrong. Judge examples and feed choices against the selected template/task, not one preferred host.
   T3 has an expected mechanism to test, not proof that every other correct design must fail.
2. The operator protocol contradicts itself: separate fresh sessions for tasks versus T2–T4 continuing
   earlier work. Use one cold session per end-to-end journey, distinct fresh journeys for repetition,
   and a separately labelled re-entry/held-out condition. Freeze this before running.
3. `from:` is testimony. Append-only and pre-action are instructions, not enforcement. Collect transcript
   timestamps/tool accesses plus operator-owned journal snapshots and hashes; audit missing/retro/edited
   entries. Normal search and correct prior knowledge are not automatically routing failure. Record raw
   categories, then adjudicate whether an applicable supplied pointer was absent/misleading. Do not reward
   invented runbook attributions or discourage asking for necessary environment information.
4. Static fingerprints are review leads, not defects or PASS/FAIL. A true-returning handler can be correct;
   multiple feeds can be intentional; a dependency-only bean is legal and not itself shadowing. Missing
   `auditLog` text does not prove no audit output, nor does its presence prove any. Inspect changed portions
   as well as unchanged template examples: editing an unrelated line makes the entire file subject-owned
   under the current heuristic, and excluding pristine files hides bad teaching examples.
5. Validate the journal and input paths before scoring: required fields, known kinds/from categories,
   unique increasing entry ids, consistent timezone-aware monotonic timestamps, real prediction answers,
   explicit task boundaries and outcomes. A baseline must be a separately hashed pristine extraction;
   reject missing baselines and a baseline equal to/inside the subject tree for acceptance mode. Unknown
   or missing observations must remain UNKNOWN, never disappear from a percentage denominator.
6. The current JSON loses baseline/isolation warnings and negative fingerprint results. Record instrument
   version, baseline/current manifests, model/version, task, caps, intervention types, applicability and
   status for every fingerprint. Per-task timings/counts are promised but the parser currently has no
   reliable task association. A `CHECK` entry does not establish an independently checked result.
7. The reconstructed v1 session is historical context, not a matched control. Its routed/unrouted counts
   and identical observation conditions are unavailable, so the stated comparative pass cannot yet be
   computed. Use a matched v1/v2 protocol or report a qualified descriptive comparison. Preserve task
   completion/correctness as primary outcomes; shorter HIT lists cannot offset skipped tasks/checks.
8. A directory alone does not deny access to other directories, network or tools. Record an enforced
   filesystem/tool/network allowlist and test forbidden reads before starting. Subjects receive JOURNAL
   and their current prompt only, not the operator prompts, injection table, answers or this repository's
   preserved proposal. Do not expose the operator artifact folder through the working directory.

The proposed two capability levels × three runs is an experiment size proposal, not a substitute for
the spec's recurrence/held-out rule or approval to spend paid keys. No experiment has been run here.

## Independent probes performed

Ran the preserved Python scorer on temporary synthetic inputs, then removed those inputs:

- A class whose block comment contains a line starting `@OnEventHandler` is still reported by T-EVENTLOG.
- Duplicate E1 ids, an unknown `from: made-up` and decreasing timestamps are accepted; elapsed becomes
  negative (`-1 day, 23:00:00`).
- Passing the subject directory itself as baseline suppresses the candidate hit without refusal.

These contradict treating the current scorer as objective defect counts. Keep it as a candidate detector
until validation and scope fixes are implemented with regression probes; do not change the preserved copy.

## Implementation disposition

Adopt attribution/imitation recording and pristine-baseline provenance as inputs to the journey harness.
Use a separate hardened operator scorer when implementing acceptance, retaining raw counts and explicit
manual decisions. The analyser only displays the resulting evidence; it does not execute these tasks.
