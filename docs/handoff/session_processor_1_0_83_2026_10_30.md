# Session processor regeneration: 1.0.79 → 1.0.83

This change is independent of built-in Claude onboarding. Both PRs target `main`.
Only the checked-in processor and its resource copy are regenerated; the graph,
nodes and dependency versions are unchanged. Revert this PR to restore the old
processor. No persisted data format changes.

## Predictions recorded before the initial capture

Baseline: `6d51aa1671c4dbc9c3d6236c1b1e684781b7d13e` (compiler 1.0.79 output).
Candidate: that baseline, regenerated with compiler 1.0.83 via `mvn -q -Pregen process-classes`.
Both use runtime 1.1.0 and the identical `SessionProcessorCompatibilityTest` fixture.

1. The healthy project → log → graph → project-switch fixture will produce the
   same ordered business records. Framework invocation order will also match;
   timestamps and process-specific object identities are not semantic changes.
2. A one-shot audit-listener failure at cycle close will propagate the original
   exception on both versions. Node changes made before it are not rolled back.
   The old object-event path will remain in processing state and silently queue
   the following profile observation. The new path will close the failed record,
   clear processing/dirty state, and process the following observation.
3. Consequently the fault captures should **differ**: the new capture contains
   the failed cycle's completed record and the next event; the old one lacks
   those records. This is expected recovery evidence, not business equivalence.
4. The same recovery check covers typed events, batch end and host event cycles.
   The old host-cycle path already clears processing in `finally`; its recovery
   assertion may pass while the old object, typed and batch-end paths fail.
5. Runtime 1.1.0 does **not** discard queued callbacks after a failure. That
   stronger guarantee requires runtime 1.1.3 or later and is outside this PR.
   Neither this fixture nor a matching business comparison proves all possible
   event sequences equivalent, or rollback of node-owned state.

The fixture exports real `SessionAuditSink` records; it does not synthesize or
edit audit YAML. Its injected listener throws once before forwarding to the sink,
which permits the generated cleanup to retry closure without another exception.
The normal application sink counts write failures internally; this injection
exercises the processor boundary directly.

## Initial runtime 1.1.0 results

**Current gate:** the source fix is in [runtime PR #53](https://github.com/telaminai/fluxtion/pull/53)
and passes the strengthened analyser fixture. This branch still pins runtime 1.1.0,
which still fails that assertion. Keep this PR draft until the fixed runtime is released,
adopted and the assertion becomes part of the normal suite. The issue is not accepted
for release. Onboarding can proceed independently on the old processor.

| Check | Observed result |
|---|---|
| Fresh regeneration | Byte-identical to the 1.0.83 output removed from onboarding; Java and resource copies identical. |
| Healthy fixture | 28 records each, 17 with business entries; business and framework views match. Two process-specific identities masked by the existing comparator. |
| Failed close / next event | Original exception preserved. Baseline stays `FULL`; candidate processes the next event and reaches `SAFE`. |
| Recovery regression | Candidate: all four entry cases pass. Baseline: object, typed and batch-end cases fail the next-event assertion; host-cycle passes, as predicted. |
| Failed-close audit comparison | **No verdict** (exit 2). Candidate document 3 contains `endTime` twice. Prediction 3's expectation of a comparable completed record was wrong. |

The candidate retries `EventLogManager.processingComplete()` after the injected
listener throws. On runtime 1.1.0 that appends the end marker again. The raw record
is preserved unchanged, and the existing comparator refuses it. Its generic
diagnostic mentions a line break in a value, but this fixture's actual cause is
the repeated close. There is no claim that the failed capture is equivalent or
fully readable. The following event's record is separate and processing recovers.
The application's `SessionAuditSink` normally catches/counts its own write errors;
the injected throwing listener deliberately exercises the lower-level boundary.

Evidence: [healthy comparison](evidence/session-processor-1.0.83/healthy-comparison.txt),
[failed-close comparison](evidence/session-processor-1.0.83/failed-close-comparison.txt),
[before outcome](evidence/session-processor-1.0.83/before/outcome.txt),
[after outcome](evidence/session-processor-1.0.83/after/outcome.txt),
[before healthy](evidence/session-processor-1.0.83/before/healthy.yaml),
[after healthy](evidence/session-processor-1.0.83/after/healthy.yaml),
[before failure](evidence/session-processor-1.0.83/before/failed-close.yaml),
[after failure](evidence/session-processor-1.0.83/after/failed-close.yaml).

## Reproduction and scope

Use Java 21. The baseline worktree is clean main at the revision above, with only
the identical new test source copied in; no production source or dependencies
were changed. Fixture SHA-256:
`830b69c1091da3b86bdbb6ee28f87834b3283dbf7e94f6b399d80ed19cd27c75`.
The first fixture attempt armed the throwing listener during its own installation
event; this plumbing error was corrected before the failed-cycle captures.

In each worktree, choose a separate absolute output directory:

```sh
mvn -q -Dtest=SessionProcessorCompatibilityTest \
  -Dsession.compatibility.capture=/tmp/session-candidate test
```

The baseline exits nonzero because the three recovery assertions are deliberate
wrong-result witnesses. The capture test itself passes in both worktrees. Compare
the exported files with the repository's existing tool (no comparator changes):

```sh
python3 docs/skills/spring/add-a-node/audit-compare.py \
  /tmp/session-baseline/healthy.yaml /tmp/session-candidate/healthy.yaml
python3 docs/skills/spring/add-a-node/audit-compare.py \
  /tmp/session-baseline/failed-close.yaml /tmp/session-candidate/failed-close.yaml
```

These are bounded session fixtures, not a whole-runtime compatibility proof.
Normal-release gates and adoption of the audit-close fix below remain open.
Compiler cleanup does not provide transactions, node-state rollback or runtime
1.1.3 queue-discard behavior on runtime 1.1.0. No dependency upgrade is bundled here.

Java 21 `mvn -q test`: 4,479 total, 4,098 executed passes, 381 display-dependent skips,
zero failures/errors. The five new compatibility cases execute headlessly. Public-data
and whitespace checks pass. Desktop onboarding acceptance belongs to its separate PR.


## Audit-close source fix: predictions before the candidate run

The follow-up fixes termination in the public runtime rather than hand-editing the
processor. A completed text/binary record retains its close result and first end time;
failed delivery remains retryable, and the manager suppresses repeated completion after
successful publication. The analyser production dependency is still runtime 1.1.0.

The [integration assertion patch](evidence/session-processor-1.0.83/runtime-fix/integration-assertion.patch)
adds exactly-one-`endTime` checks to the existing capture and all four recovery entry
cases, without changing their input sequence. It also requires all four capture records,
including FULL and SAFE, and two additional records in each recovery case, so dropping
the failed record cannot make the test pass. It is applied in an isolated validation
worktree. Against the production runtime pin, all five cases fail with two end markers.
The assertion belongs in the normal test suite when the fixed runtime is released and
adopted; this evidence patch does not make the current production pin pass.

Predictions before the fixed-runtime capture:

1. All five strengthened cases pass with exactly one end marker per captured record.
2. The healthy capture still matches the original healthy capture's business entries
   and framework trace (28 records, 17 with business entries).
3. The failed-close capture is readable by the unchanged comparison tool. Compared
   with the original 1.0.79 baseline, it has the expected additional FULL and SAFE
   observations and yields a business-difference verdict (exit 1), not an unreadable
   verdict (exit 2). The original malformed evidence is retained unchanged.
4. Using the newer runtime without regenerating the old processor does not add its
   missing queue-discard calls. That adoption is separately tracked in
   [issue #215](https://github.com/telaminai/fluxtionauditlog-analyser/issues/215).


## Fixed-runtime results and adoption gate

[Runtime PR #53](https://github.com/telaminai/fluxtion/pull/53), commit
`38134088aaaee481891bc840870c44566a1d709b`, implements idempotent record closure and
completion after successful publication. An independent internal review found no
blocking defect; its requested test for explicit publication after ordinary completion
was added. A follow-up evidence review prompted the record-count/presence assertions
above; the final strengthened fixture was rerun against the candidate. Runtime verification passed 309 normal tests; the one manifest test deferred
on the classes path then passed against the packaged jar.

The exact analyser fixture plus the assertion patch was run twice: all five cases fail
against the pinned runtime with two end markers; all five pass against the locally built
runtime from that commit. The input events and generated processor are unchanged.
The full analyser headless suite also passed with that candidate: 4,479 total, 4,098
executed passes, 381 display-dependent skips, zero failures/errors. This is local
candidate evidence, not evidence that the branch's old dependency has been fixed.

| Predicted check | Observed result |
|---|---|
| Exactly one end marker | All five strengthened cases pass; the object capture contains four readable records, each with one `endTime`. |
| Healthy comparison | 28 records each, 17 with business entries; business and framework views match (exit 0). |
| Failure comparison | Original baseline has two records; candidate has four, adding only the expected FULL and SAFE business observations (exit 1). The unchanged comparator can read it. |
| Queue policy | Generated source remains unchanged and lacks discard calls. No queue-discard claim is made; issue #215 remains open. |

Evidence: [healthy comparison](evidence/session-processor-1.0.83/runtime-fix/healthy-comparison.txt),
[failed-close comparison](evidence/session-processor-1.0.83/runtime-fix/failed-close-comparison.txt),
[healthy capture](evidence/session-processor-1.0.83/runtime-fix/after/healthy.yaml),
[failed-close capture](evidence/session-processor-1.0.83/runtime-fix/after/failed-close.yaml),
[outcome](evidence/session-processor-1.0.83/runtime-fix/after/outcome.txt).
The original malformed capture and exit-2 result above remain unchanged.

To reproduce in a throwaway worktree of this branch, build and install the runtime
candidate from the linked commit locally, apply the assertion patch, then set
`AUDIT_RUNTIME_VERSION` to that local build's version and run:

```sh
git apply docs/handoff/evidence/session-processor-1.0.83/runtime-fix/integration-assertion.patch
mvn -q -Dfluxtion.version="$AUDIT_RUNTIME_VERSION" \
  -Dtest=SessionProcessorCompatibilityTest \
  -Dsession.compatibility.capture=/tmp/session-fixed-runtime test
```

Without the runtime override, the assertion deliberately fails against the current pin.
For adoption, publish the reviewed runtime fix through its normal release process,
update the analyser to that published version, incorporate the assertion directly into
`SessionProcessorCompatibilityTest`, and repeat the normal build/acceptance gates.
Changing the runtime alone does not implement the separately tracked queue-discard
policy: that requires regeneration and the tests in
[issue #215](https://github.com/telaminai/fluxtionauditlog-analyser/issues/215).

The unchanged production-pin suite was also rerun after adding this evidence: 4,098
executed passes, 381 display-dependent skips, zero failures/errors. The stronger close
assertion is supplied as the patch above and is not silently counted as passing on that
pin. Strict MkDocs, public-data and whitespace checks pass.


## Review follow-up (2026-10-31)

[Review of this PR](https://github.com/telaminai/fluxtionauditlog-analyser/pull/214#issuecomment-6540751674),
finding G1: the recovery cases counted two new records with one `endTime` each, which an
end-marker-only record or a record with another cycle's entries prefixed to it would also satisfy.
The [assertion patch](evidence/session-processor-1.0.83/runtime-fix/integration-assertion.patch)
now also requires the first new record to be the failed cycle's own (by its `eventToString`:
the FULL observation, `BatchEnd` or the host-cycle name), the second to be the SAFE event's, and
every captured record to start with its own `eventLogRecord:` header. A sixth test is a negative
witness: the check rejects an end-marker-only record, a merged record and a doubled `endTime`.

The runtime PR's review finding D1 (entries written after a mid-cycle `publishLastRecord()`
were carried into the next record) is fixed on the runtime branch in
`3128bf7950e17ed29063605378706e3824c5d8c7`; the candidate below is that commit.

| Run (Java 21) | Result |
|---|---|
| Strengthened patch, production pin 1.1.0 | 5 of 6 fail, each on the doubled `endTime`; the negative witness passes |
| Strengthened patch, runtime candidate `3128bf79` (local 1.1.4-SNAPSHOT) | 6 of 6 pass |
| Fresh candidate captures vs the committed `runtime-fix/after` captures | healthy and failed-close both MATCH (exit 0), so the committed evidence still stands |
| Fresh candidate failed-close vs the original 1.0.79 baseline | exit 1, adding only the FULL and SAFE observations, as before |

Still open for adoption: a case where a queued callback throws after the cycle's record was
already published (the empty end-marker path the runtime PR describes), which this fixture does
not reach.
