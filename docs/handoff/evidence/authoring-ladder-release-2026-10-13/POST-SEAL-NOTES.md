# Post-seal notes (added 2026-10-14, from the review of PR #140 at `6b2ddc2f`)

The sealed files (`PREDICTIONS-RELEASE.md`, `FIXTURES.md`, `DEPLOYMENT-CHECKS.md`, `MANIFEST-RELEASE.sha256` and
`.sealed-at`, commit `a445666d`) are left **byte-identical**, so the manifest still verifies. The scored files
(`results/<run>/score.json`, `levers.json`, `meta.json`, `keyscan.json`, `config.json`) are unchanged.

Every correction below is to **reporting** (RESULTS.md, TOKEN-SPLIT.md, the post-seal content-read measure), or a
limitation of the sealed design stated now. None changes a PASS, an oracle verdict or a sealed number.

1. **Generated-code content reads were undercounted.**
   - **What changed:** `harness/content_reads.py` now also counts a *path-named read*: a read verb whose argument is
     a `generated/…java` or `.graphml` file, with a non-empty, non-error result, excluding count-only greps. Its
     docstring no longer lists `dispatchMap` or `handleEvent(`, which the regex never matched.
   - **Re-run** over all 15 release and all 12 baseline runs:
     - **release:** 2 partial reads of the processor (rel-r4-h55-3, `head -c 3000` of the shipped TradeProcessor;
       rel-r6-h55-1, a 5-line `grep`) and 0 dispatch-code or GraphML reads;
     - **baseline:** 1 partial read (base-r4-h55-3, `cat … | head -80` of the shipped TradeProcessor) and 0
       dispatch-code or GraphML reads.
   - **Correction:** the earlier statement "0 reads of the generated processor or its GraphML in any of the 15 runs"
     was false.
2. **The rung-3 and rung-4 cost rise is now quantified and marked INFERRED** in RESULTS.md and TOKEN-SPLIT.md. The
   un-injected-service windows ($0.055) and the reconcile loops explain part of each rise, not all of it.
   - **Rung 3:** the rest sits mostly in a diagnosis phase entered at 112–121k tokens.
   - **Rung 4:** the rest is in design, and includes rel-r4-h55-1, which had no conflict.
   - For the phase comparison, the baseline transcripts were split with the same `token_split.py`
     (`baseline-token-split/`, derived tables only).
3. **Reconcile conflict counts.**
   - **What `levers.reconcileConflicts` counts:** every tool result that mentions SPRING_RECONCILE_CONFLICT,
     including the bundle docs' own mention.
   - **Reconciler results** (the starter's own conflict output, or its reconciliation report) are fewer:
     - rel-r4-h55-2: 8 (calls 24, 26, 27, 30, 33, 37, 39, 40, 1-based);
     - rel-r4-h55-3: 2 (calls 21 and 34);
     - rel-r5-h55-1: 0;
     - rel-r5-h55-3: 1.
4. **Scopes of the server.log figures.** The whole-run figures (5 calls / 26.1 kB and 3 calls / 7.5 kB, any read verb
   naming the file, including compound calls) and the failure-window figures (4 reads / 22.5 kB and 2 reads /
   6.8 kB, a read verb taking the file) are now labelled. In the window table, "tool bytes" is labelled as **all**
   tool bytes in the window.
5. **"Most over-100k requests in diagnosis"** is a strict majority in 8 of the 12 runs that cross. Diagnosis is a
   plurality, not a majority, in 2 runs (rel-r2-h55-3, 9 of 20; rel-r4-h55-1, 7 of 17), and design dominates in the
   other 2.
6. **Call indices** in RESULTS.md, TOKEN-SPLIT.md and `part-a/*.jsonl` are 0-based unless marked 1-based.
7. **The rung-5 setup-before-pom cause is INFERRED**, from the order of calls and the starter's "members 3 added".
   RESULTS.md already said so, and the PR body now does too.
8. **Rung-6 limitations of the sealed design**, stated now.
   - **(a) A mild hint in the fixture.** The fixture's `CsvRejections.onCsvRejection` return line, edited for rung 6
     (FIXTURES.md (b)), carries the comment "a rejection is a change: a node that references CsvRejections may
     observe it". The agent can see it, and it mildly hints that a node may reference CsvRejections. The class's
     unedited Javadoc still says "A leaf — nothing depends on it", which the rung-6 change makes inaccurate.
   - **(b) X1 is narrower than the task.** It accepts the node's parents only as `<constructor-arg ref="…"/>`. A
     design that injected a parent by `<property ref="…"/>` would fail X1 even if it were otherwise correct.
   - **No run was affected:** all three rung-6 designs declare the three parents as constructor args.
   - A later rung-6 fixture should drop the comment, and X1 should accept property references.
