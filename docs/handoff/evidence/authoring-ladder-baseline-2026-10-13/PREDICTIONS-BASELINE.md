# Authoring-ladder baseline on the released stack: predictions, sealed before launch (2026-10-13)

This is the G14 rig with prestaged fixtures, so it is never an acceptance. It is the same method as the first
ladder (`../authoring-ladder-2026-10-12/`), with the stack moved to what a user downloads today.

**What stays the same:**
- the runner, `tools/g14_runner.py` `b28ca7de…`, sandboxed, with an isolated home;
- the Claude Code CLI: the same read-only 2.1.296 binary, `c9b53416…`;
- the tasks and oracles, which are unchanged (see `FIXTURES.md` for two path-only edits);
- PASS, defined below.

**What changes:**
- the operator's `--rest` analyser is released **1.33.0**;
- the fixtures are rebuilt from today's public downloads: starter-core 1.0.77, BOM 1.0.77, runtime 1.1.1, and
  hosted generator 1.0.78;
- the warm caches are freshly cold-filled and frozen.

**Runs:** claude-haiku-5-5 x3 on each of rungs 2, 3, 4 and 5, one at a time, in the order r2 1..3, r3 1..3,
r4 1..3, r5 1..3. The cap is 2400 s. Every run is warm-cache, the protocol default.

**PASS** (`score_ladder.py`) = ORACLE and VALID and CHART and S6′, exactly as in the first ladder.

## The first ladder's Haiku 5.5 numbers that these are measured against

These are computed from `../authoring-ladder-2026-10-12/results/lad-r*-h55-*/score.json`, excluding the VOID runs.
Corrected scoring gives 11/12 PASS: 3/3, 3/3, 3/3 and 2/3. The other figures are:

| Measure | Median per run | Other |
|---|---|---|
| Cost | $0.190 | total $2.34 |
| Turns | 55.5 | — |
| Wall | 331 s | — |
| Prompts >100k | 15.5 | — |
| `generatedReads` | 7 | total 78 |
| `toolResultBytes` | 109,586 | — |

The first ladder's lever measures come from its preserved transcripts, through the same `probe_levers.py`
(`first-ladder-probe/`), computed after it ran:
- R3/R5 `runExportCalls`: median 5.5 (6, 8, 5, 7, 5, 5);
- `run-and-export.sh` calls: 0, because it did not exist;
- `annotation:` in exported logs: 0 of 6;
- R5: 2 of 3 runs used `--no-reconcile`, and all 3 hit SPRING_RECONCILE_CONFLICT.

## What each shipped lever should change

| Lever (shipped since the first ladder) | Expected effect | Measured by |
|---|---|---|
| Read-less guidance (Spring RUNBOOK step 5; bundle CLAUDE.md "What to read when you change the graph") | fewer reads of generated code and builder, fewer tool-result bytes, fewer prompts over 100k, lower cost | `reads.generatedReads`, `reads.toolResultBytes`, `usage.over100k`, `usage.costUsd` |
| `run-and-export.sh` (one command: start, settle, export, stop) | fewer run and export calls on the bundle rungs | `levers.runExportCalls`, `levers.runAndExportCalls` (rungs 3 and 5) |
| Starter 1.0.77 reconciles supplier classes (compiler #100) | R5 passable with plain `./generate.sh`, so no `--no-reconcile` | `levers.noReconcileCalls`, `levers.reconcileConflicts`, R5 PASS |
| Hosted generator 1.0.78 (`annotation:` in traces) | every regenerated processor stamps 1.0.78, and exported logs carry `annotation:` | `levers.generatorVersion`, `levers.annotationInExport` |
| Analyser 1.33.0 as the operator instance | no change to PASS expected; CHART holds | `CHART` |

## Predictions

1. **Rung 2:** Haiku 5.5 PASSes at least 2 of 3.
2. **Rung 3:** Haiku 5.5 PASSes at least 2 of 3.
3. **Rung 4:** Haiku 5.5 PASSes at least 2 of 3.
4. **Rung 5:** Haiku 5.5 PASSes at least 2 of 3. No R5 run calls `--no-reconcile`, and at least 2 of 3 R5 runs see
   no SPRING_RECONCILE_CONFLICT in any tool result.
5. **Overall:** Haiku 5.5 PASSes at least 10 of 12. The first ladder, under corrected scoring, had 11 of 12.
6. **Generated-code reads:** the total `generatedReads` over the 12 runs is at most 39, half the first ladder's 78.
   The median per run is at most 3.
7. **Tool-result bytes:** the median `toolResultBytes` per run is below the first ladder's 109,586.
8. **Prompts over 100k:** the median per run is at most 12 (first ladder: 15.5).
9. **Cost:** the median per-run cost is below $0.190, and the total for the 12 runs is at most $2.50.
10. **Turns:** the median per run is at most 55.5.
11. **Run and export (rungs 3 and 5):**
    - at least 4 of the 6 bundle runs call `run-and-export.sh`;
    - the median `runExportCalls` on those 6 runs is at most 4 (first ladder: 5.5).
12. **`annotation:`:**
    - every run's final generated processor stamps target generator 1.0.78;
    - every rung-3 and rung-5 run that leaves an exported `logs/*.yaml` carries at least one `annotation:` key in
      it (`annotationInExport` is True).
13. **Wall clock:** the median wall per run is at most 400 s, and no run reaches the 2400 s cap.
14. **S6′** holds in 12 of 12.

## Rules fixed in advance

- **VOID:** a run that fails for an external reason is marked VOID, kept as evidence, excluded from the totals, and
  re-run. External reasons are a Repsy, Central or hosted-generator outage, an API or auth error, or a port or stray
  precondition violated mid-run. A run is never silently dropped.
- **Corrections:** a scorer correction made after sealing is applied to every run alike and disclosed with its time,
  and the sealed verdict is kept beside it.
- **Small samples:** n = 3 per rung. A one-run difference is noise, and no per-rung change of 1/3 is read as an
  effect.
