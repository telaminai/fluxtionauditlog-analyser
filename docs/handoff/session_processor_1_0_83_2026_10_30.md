# Session processor regeneration: 1.0.79 → 1.0.83

This change is independent of built-in Claude onboarding. Both PRs target `main`.
Only the checked-in processor and its resource copy are regenerated; the graph,
nodes and dependency versions are unchanged. Revert this PR to restore the old
processor. No persisted data format changes.

## Predictions recorded before capture

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

## Results

**Draft; do not promote until the failed-audit-close issue below is resolved or
explicitly accepted.** Onboarding can proceed independently on the old processor.

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
Normal-release gates and a decision on the failed-close record remain open.
Compiler cleanup does not provide transactions, node-state rollback or runtime
1.1.3 queue-discard behavior on runtime 1.1.0. No dependency upgrade is bundled here.

Java 21 `mvn -q test`: 4,479 total, 4,098 executed passes, 381 display-dependent skips,
zero failures/errors. The five new compatibility cases execute headlessly. Public-data
and whitespace checks pass. Desktop onboarding acceptance belongs to its separate PR.
