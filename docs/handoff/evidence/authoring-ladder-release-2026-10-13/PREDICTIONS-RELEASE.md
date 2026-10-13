# Authoring ladder on the fully released stack: predictions, sealed before launch (2026-10-13)

This is the G14 rig with prestaged fixtures, so it is never an acceptance. The method is the baseline's
(`../authoring-ladder-baseline-2026-10-13/`).

**Runs:** claude-haiku-5-5 x3 on rungs 2, 3, 4, 5 and 6, 15 runs, one at a time, in the order r2 1..3 through
r6 1..3. The cap is 2400 s, and every run is warm-cache, from seeds cold-filled today.

**The stack, all released and changed together** (see `DEPLOYMENT-CHECKS.md` and `FIXTURES.md`):
- analyser **1.34.0**, both as the operator's `--rest` instance and as the subject's MCP bridge;
- starter-core and BOM **1.0.79**, runtime 1.1.1, hosted generator **1.0.79**;
- today's templates: capped script output, annotated shipped processor, vendored add-a-node skill.

**Fixed from the baseline:** the runner is analyser main at the v1.34.0 tag, `tools/g14_runner.py` `a9511a4f…`.
It adds the opt-in `--no-subagent`, which is **not** used here, so the subagent tool keeps its default grant; it
also adds `meta.json` `subagentOffered` and the Linux `/proc` cwd. The CLI is the same read-only Claude Code
2.1.296 binary (`c9b53416…`).

**PASS** = ORACLE + VALID + CHART + S6′ (`harness/score_ladder.py`). Rung 6's ORACLE is `r6/r6_oracle.py` (X1
XML-first, X2 dispatch, X3 output).

**Confounds stated in advance:** every difference from the baseline is a difference of the whole released stack:
- analyser 1.33.0 → 1.34.0, which itself changed the skills, the model-choice docs and the token split;
- starter 1.0.77 → 1.0.79;
- generator 1.0.78 → 1.0.79;
- the template changes listed above.

No difference can be attributed to one lever. The baseline's n = 3 per rung applies here too.

## The baseline it is compared against

These are Haiku 5.5 figures from `../authoring-ladder-baseline-2026-10-13/results/`, rungs 2–5:

| Measure | Baseline |
|---|---|
| PASS | r2 1/3 (both failures were CHART only), r3 3/3, r4 3/3, r5 3/3; 10/12 overall |
| Cost | median $0.132, total $1.49 |
| Turns | median 53 |
| Wall | median 216 s |
| Prompts >100k | median 10, total 102 |
| `generatedReads` | median 3.5, total 41 |
| Median `toolResultBytes` | 102,831 |
| Bundle runs (r3, r5) | all 6 used `run-and-export.sh`; median `runExportCalls` 4 |

## Predictions

1. **Rung 2:** Haiku PASSes at least 1 of 3. If a run fails, it fails on CHART, not on ORACLE or VALID.
2. **Rungs 3, 4, 5:** Haiku PASSes at least 2 of 3 on each.
3. **Rung 6** (new, harder): Haiku PASSes at least 1 of 3.
4. **Rung 6 stays XML-first:**
   - at least 2 of 3 rung-6 runs pass X1;
   - at most 1 of 3 writes an `@ServiceRegistered` callback (`levers.serviceRegisteredWrites` > 0);
   - at least 2 of 3 have a `turnover` SinkBinding in the final XML.
5. **Rung 6's most common failure, if any:** X2 or X3, the runtime evidence (for example a DATA parent declared
   TRIGGER, or wrong output), rather than X1.
6. **Rungs 2–5 overall:** at least 10 of 12 PASS, the same or better than the baseline.
7. **`withdraw`:** no run uses it (`levers.withdrawCalls` = 0 in all 15).
8. **`--no-reconcile`:** no run passes `--no-reconcile` (`levers.noReconcileCalls` = 0 in all 15).
9. **Generated-code reads:** the total `generatedReads` over rungs 2–5 is at most 41, and the median per run over
   rungs 2–5 is at most 3.5.
10. **Prompts over 100k:** the median per run over rungs 2–5 is at most 10.
11. **Cost:**
    - the median per-run cost over rungs 2–5 is at most $0.15;
    - the total over all 15 runs is at most $3.00;
    - the median rung-6 cost is higher than the median over rungs 3 and 5.
12. **`run-and-export.sh`:** at least 8 of the 9 bundle runs (rungs 3, 5 and 6) use it.
13. **Subagents:**
    - `subagentOffered` is True in all 15 meta.json files;
    - at most 2 of 15 runs make a subagent call (`levers.subagentCalls` > 0).
14. **`annotation:` and generator version:**
    - every bundle run that leaves an exported `logs/*.yaml` carries `annotation:`;
    - every final generated processor stamps target generator 1.0.79.
15. **Wall clock:** the median wall per run over rungs 2–5 is at most 300 s, and no run reaches the cap.
16. **S6′** holds in 15 of 15.

## Rules fixed in advance

- **VOID:** a run that fails for an external reason is marked VOID, kept, excluded from the totals, and re-run.
  External reasons are a Repsy, Central or hosted-generator outage, an API or auth error, or a violated port or
  stray precondition.
- **Corrections:** a post-seal correction to the scorer, an oracle or the probe is applied to every run alike and
  disclosed with its time, and the sealed verdict is kept beside it.
- **Token split:** the split is computed after the runs with analyser main's `tools/token_split.py`. Only its
  derived tables are committed, never transcripts.
