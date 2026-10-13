# Authoring-ladder baseline on the released stack: results (2026-10-13)

**Runs:** rungs 2–5, claude-haiku-5-5 × 3 each, 12 runs. Each ran once, one at a time, from 2026-10-13T12:16:18Z
to 13:03:57Z. None was VOID and none was re-run.

**Stack:**
- the analyser: 1.33.0, both as the operator's `--rest` instance and as the subject's MCP bridge. The bridge is
  the same jar (`config.json` `mcp.args`). In the first ladder both were 1.32.0 (jar `f125050b…`), and 1.33.0
  itself changed skill guidance and how the `annotation` trace key is read (CHANGELOG 1.33.0);
- starter-core and BOM 1.0.77, runtime 1.1.1;
- hosted generator 1.0.78;
- Claude Code 2.1.296: the same read-only binary as the first ladder, `claudeBinUnchanged` in every meta.json.

**What the comparison can and cannot attribute:** the analyser, starter, BOM, runtime, hosted generator and
template guidance all changed together between the two ladders. Every delta below is therefore a delta of the
**whole released stack**, and none can be attributed to the guidance alone, or to any single lever.
`POST-SEAL-NOTES.md` records this, and the other corrections made after the review of PR #131, against the sealed
files, which are left byte-identical.

**Sealing:** the predictions and manifest were sealed at 2026-10-13T12:16:09Z (commit `b2234e52`, pushed) before
the first run. See `FIXTURES.md` for what was downloaded and how each fixture and seed was built.

**Sources:** every number below is computed from `results/<run>/*.json` by `harness/collect.py`, and the
comparison by `harness/compare.py`. The comparison also reads `../authoring-ladder-2026-10-12/results/` and
`first-ladder-probe/`. Transcripts are not committed, and the home directory is redacted to `~`.

**PASS** = ORACLE + VALID + CHART + S6′ (`harness/score_ladder.py`, the first ladder's scorer). The lever columns
come from `harness/probe_levers.py`, which does not affect PASS:
- `runExport` and `run-and-export`, defined for rungs 3 and 5;
- `--no-reconcile` calls;
- tool results with SPRING_RECONCILE_CONFLICT;
- generator version;
- `annotation:` in `logs/*.yaml`.

## Per run

| run | PASS | ORACLE | VALID | CHART | S6′ | cost $ | turns | wall s | max prompt | >100k | generatedReads | toolResultBytes | runExport | run-and-export | --no-reconcile | reconcile conflicts | gen version | annotation in export |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| base-r2-h55-1 | False | True | True | False | True | 0.170 | 59 | 245 | 116518 | 13 | 2 | 107189 | 3 | 0 | 0 | 0 | 1.0.78 | None |
| base-r2-h55-2 | True | True | True | True | True | 0.139 | 55 | 335 | 109444 | 11 | 5 | 83468 | 2 | 0 | 0 | 0 | 1.0.78 | None |
| base-r2-h55-3 | False | True | True | False | True | 0.159 | 60 | 219 | 116542 | 12 | 1 | 100423 | 3 | 0 | 0 | 0 | 1.0.78 | None |
| base-r3-h55-1 | True | True | True | True | True | 0.087 | 50 | 192 | 103196 | 5 | 4 | 103635 | 4 | 2 | 0 | 0 | 1.0.78 | True |
| base-r3-h55-2 | True | True | True | True | True | 0.041 | 34 | 201 | 83685 | 0 | 4 | 70837 | 4 | 4 | 0 | 0 | 1.0.78 | True |
| base-r3-h55-3 | True | True | True | True | True | 0.073 | 38 | 157 | 105657 | 3 | 5 | 109227 | 3 | 2 | 0 | 0 | 1.0.78 | True |
| base-r4-h55-1 | True | True | True | True | True | 0.097 | 53 | 213 | 105824 | 4 | 3 | 88522 | 2 | 0 | 0 | 1 | 1.0.78 | None |
| base-r4-h55-2 | True | True | True | True | True | 0.126 | 53 | 203 | 107860 | 9 | 3 | 91100 | 5 | 0 | 0 | 0 | 1.0.78 | None |
| base-r4-h55-3 | True | True | True | True | True | 0.180 | 55 | 235 | 118417 | 14 | 5 | 102027 | 3 | 0 | 0 | 0 | 1.0.78 | None |
| base-r5-h55-1 | True | True | True | True | True | 0.160 | 49 | 320 | 122876 | 11 | 3 | 121266 | 3 | 3 | 0 | 0 | 1.0.78 | True |
| base-r5-h55-2 | True | True | True | True | True | 0.100 | 45 | 171 | 104526 | 7 | 2 | 108109 | 4 | 4 | 0 | 0 | 1.0.78 | True |
| base-r5-h55-3 | True | True | True | True | True | 0.162 | 53 | 267 | 117441 | 13 | 4 | 114664 | 6 | 6 | 0 | 0 | 1.0.78 | True |


## Summary (Haiku 5.5)

- **PASS:** rung 2 **1/3**, rung 3 **3/3**, rung 4 **3/3**, rung 5 **3/3**, overall **10/12**.
- **Cost:** total **$1.49**, median **$0.132** per run.
- **Wall:** median **216 s**; the longest was 335 s, and no run reached the 2400 s cap.
- **Prompts over 100k:** median **10** per run, 102 in total, between 0 and 14 per run.
- **Generated-code reads** (`measure_reads.py`): median **3.5** per run, **41** in total.
- **Tool-result bytes:** median 102,831 per run.
- **Turns:** median 53.
- **Rungs 3 and 5:** all 6 runs used `run-and-export.sh`, and `runExportCalls` had a median of 4. All 6 exported
  logs carry `annotation:` keys.
- **Rung 5:** 0 of 3 runs used `--no-reconcile`, and 0 of 3 saw SPRING_RECONCILE_CONFLICT.
- **Generator:** every generated processor stamps target generator 1.0.78.
- **S6′:** holds in 12 of 12.

## Against the first ladder (Haiku 5.5, corrected scoring, VOID runs excluded)

| rung | measure (median per run unless PASS) | first ladder | baseline |
|---|---|---|---|
| 2 | PASS | 3/3 | 1/3 |
| 2 | cost $ | 0.206 | 0.159 |
| 2 | turns | 66 | 59 |
| 2 | wall s | 461.765 | 244.665 |
| 2 | prompts >100k | 17 | 12 |
| 2 | max prompt | 122738 | 116518 |
| 2 | generatedReads | 8 | 2 |
| 2 | toolResultBytes | 109555 | 100423 |
| 3 | PASS | 3/3 | 3/3 |
| 3 | cost $ | 0.213 | 0.073 |
| 3 | turns | 53 | 38 |
| 3 | wall s | 293.131 | 191.881 |
| 3 | prompts >100k | 18 | 3 |
| 3 | max prompt | 135526 | 103196 |
| 3 | generatedReads | 7 | 4 |
| 3 | toolResultBytes | 165008 | 103635 |
| 3 | runExportCalls | 6 | 4 |
| 4 | PASS | 3/3 | 3/3 |
| 4 | cost $ | 0.146 | 0.126 |
| 4 | turns | 52 | 53 |
| 4 | wall s | 276.982 | 213.298 |
| 4 | prompts >100k | 12 | 9 |
| 4 | max prompt | 116997 | 107860 |
| 4 | generatedReads | 3 | 3 |
| 4 | toolResultBytes | 94570 | 91100 |
| 5 | PASS | 2/3 | 3/3 |
| 5 | cost $ | 0.111 | 0.160 |
| 5 | turns | 49 | 49 |
| 5 | wall s | 353.615 | 267.22 |
| 5 | prompts >100k | 7 | 11 |
| 5 | max prompt | 109498 | 117441 |
| 5 | generatedReads | 7 | 3 |
| 5 | toolResultBytes | 109826 | 114664 |
| 5 | runExportCalls | 5 | 4 |
| all | PASS | 11/12 | 10/12 |
| all | cost $ | 0.190 | 0.132 |
| all | turns | 55.5 | 53 |
| all | wall s | 331.066 | 216.101 |
| all | prompts >100k | 15.5 | 10 |
| all | max prompt | 124802 | 108652 |
| all | generatedReads | 7.000 | 3.500 |
| all | toolResultBytes | 109586 | 102831 |

**Totals:**

| | First ladder | Baseline |
|---|---|---|
| Cost | $2.34 | $1.49 |
| Generated-code reads | 78 | 41 |
| Prompts over 100k | 177 | 102 |
| Tool-result bytes | 1,448,215 | 1,200,467 |

The first ladder's run/export figures come from its preserved transcripts, run through the same probe after the
fact (`first-ladder-probe/`).

## Sealed predictions against outcomes (PREDICTIONS-BASELINE.md)

| # | Prediction | Outcome |
|---|---|---|
| 1 | R2 ≥ 2/3 | **wrong**: 1/3 (both failures CHART-only; ORACLE and VALID pass in 3/3) |
| 2 | R3 ≥ 2/3 | holds: 3/3 |
| 3 | R4 ≥ 2/3 | holds: 3/3 |
| 4 | R5 ≥ 2/3; no `--no-reconcile`; ≥ 2/3 R5 runs see no reconcile conflict | holds: 3/3; 0/3 used it; 3/3 saw none |
| 5 | Overall ≥ 10/12 | holds: 10/12 (first ladder 11/12) |
| 6 | generatedReads total ≤ 39 and median ≤ 3 | **wrong**: total 41, median 3.5. It fell from 78 and 7, but by less than half. |
| 7 | Median toolResultBytes < 109,586 | holds: 102,831 |
| 8 | Median prompts >100k ≤ 12 | holds: 10 |
| 9 | Median cost < $0.190; total ≤ $2.50 | holds: $0.132; $1.49 |
| 10 | Median turns ≤ 55.5 | holds: 53 |
| 11 | ≥ 4/6 bundle runs call run-and-export.sh; median runExportCalls ≤ 4 | holds: 6/6; 4 |
| 12 | All generated processors 1.0.78; every R3/R5 exported log carries `annotation:` | holds: 12/12; 6/6 |
| 13 | Median wall ≤ 400 s; no cap | holds: 216 s; none |
| 14 | S6′ 12/12 | holds |

## The two rung-2 failures (both CHART)

- **base-r2-h55-1** saved no graph. It opened its own audit log, read series and records, spotlighted, and stored a
  *report* with a series section, but it never called `graph`. The scorer saw `graphs: []`.
- **base-r2-h55-3** saved a graph, "Scenario counts", with 4 points, but the scorer's read of its first series
  carried an `identityNote`. The likely cause, **INFERRED**, is that **the charted file was rewritten after
  charting**. From the transcript's tool calls, in order:
  - the subject opened and charted `target/scenario-run1.audit.log` (calls 46 and 52);
  - it changed the design to `propagate=false` and regenerated (calls 55 and 56), then reverted it (call 57);
  - in call 58 it regenerated and re-ran its Scenario driver with output to that same file.
  1.33.0 attaches `identityNote` when a read finds the file changed under the loaded store (`ReadThroughIdentity`,
  through `ActionDispatcher.withIdentityNote`). The note's text was not captured, because the operator instance is
  stopped after scoring. Opening the same file fresh on a 1.33.0 or a 1.32.0 instance gives no identityNote.
- **The scorer itself:** a post-run check of `score_ladder.py`'s CHART code against a fresh 1.33.0 instance, with a
  saved graph over the r3 ground-truth log, scored `ok: True` (5 points, no identity mark). The 1.33.0 context
  shape is therefore read correctly. This check changed nothing in the scoring.

In both runs ORACLE and VALID pass: the authoring was right, and the miss is the analyser-evidence step. The first
ladder's rung-2 runs all passed CHART.

## Disclosures

- **Small samples.** There are 3 runs per rung, so a 1/3 change on a rung is noise. Rung 2's 3/3 → 1/3 is two
  runs, both on CHART alone, and should not be read as a regression in authoring.
- **No VOID runs, no re-runs, no post-seal changes** to fixtures, tasks, oracles, scorer, probe or driver. The
  `compare.py` script was written after the runs to compute the table above. It reads only committed files, and its
  run/export row is restricted to the bundle rungs, where the probe defines it.
- **Pre-seal path edits** (disclosed in FIXTURES.md, made before sealing):
  - `score_ladder.py`: `L` is now the harness root;
  - `r5_oracle.py`: `FIXTURE` and `BASELINE` point at the rebuilt fixture and at the new bundle's as-shipped run,
    because the export was renamed to `audit-marketProcessor.yaml`. Its business keys equal the old baseline's.
- **Key scans:** two runs read "not clean", **base-r3-h55-3** and **base-r5-h55-1**. Each has **0** key-value
  occurrences, **0** trial-token occurrences and **4** key-*path* pattern hits: 2 in `raw.jsonl` and 2 in its
  mirror `events.jsonl`. The commands, checked against the transcripts (the shipped `check-fluxtion-key.sh` does not
  contain the string `apiKey`):
  - **base-r3-h55-3:** `… tail -5 logs/setup.log; ls -la .fluxtion …; test -f ~/.fluxtion/fluxtion.apiKeyFile
    && echo …`. This is an existence test, and its result was "key file absent".
  - **base-r5-h55-1:** the pattern hits are `… head -3; test -e ~/.fluxtion/fluxtion.apiKeyFile`, an existence
    test whose result was "key path absent". Separately, the subject ran
    `grep -s '^apiKey=' ~/.fluxtion/fluxtion.apiKeyFile >/dev/null && echo "key file present" || echo "NO key
    file"`. That is an attempt to **open and read** the key file, with the output discarded, and the scan's pattern
    does not count it.
  - **Why nothing was read:** in both runs `~` was the run's isolated home (`HOME` is set to `<base>/tmp/home`), and
    no `.fluxtion` directory existed there. The grep printed "NO key file".
  - **Verdict:** no key value and no token appear in any captured output (0 and 0 across raw.jsonl, events.jsonl,
    stderr.log and the project and tmp trees), so S6′ holds in both. The grep is still a hygiene finding: the
    subject tried to read a file the task said not to print, copy or modify. The runner's "rotate the key" message
    is over-cautious here, because no key bytes were reachable at the path the subject used.
- **No contamination:**
  - every run's precondition (`results/<run>/precondition.json`) found port 8181 free and no stray process;
  - every meta.json reports the process group reaped. Eleven runs swept no strays. base-r2-h55-1 swept one
    process, and it was the **operator's own** status-wait shell, not a subject process. Its command line named
    the run base (`… cat /private/tmp/fx-base-r2-h55-1/score.json …`), so the runner's end-of-run sweep matched it
    and terminated it (SIGTERM) after the subject had exited. It only read files. The committed meta.json truncates
    the command at 160 characters, before the quoted text; the full command, from the operator's session record, is
    in `results/base-r2-h55-1/operator-swept-shell.txt` (path-redacted). Later waits named no run base;
  - after the last run, no server held 8181 and no run process remained.
- **Warm against cold:**
  - every baseline run was warm, seeded from caches cold-filled today and provenance-checked (see FIXTURES.md);
  - in the first ladder, 4 of the 12 Haiku runs were cold: rung 2 × 3 and lad-r3-h55-1;
  - the wall-clock comparison, rung 2's especially, therefore includes download time on the first side;
  - the cost, turns, reads and prompt comparisons are not expected to be materially affected. A check after the
    review (`harness/setup_bytes.py` → `first-ladder-probe/setup-bytes.jsonl`) found that the tool results of
    calls naming `setup.sh` total 12,825–15,132 bytes in the 4 cold runs, within the 8 warm runs' range of
    4,175–26,459. This is a coarse measure, because a call that also reads docs counts whole.
- **Rung 4, first ladder:** under its sealed oracle (r1) the first ladder had 10/12. The comparison uses its
  corrected 11/12; this baseline used the corrected oracle from the start.
- **Rung 5:** the first ladder's rung 5 passed only by `--no-reconcile` or failed at the conflict. Here, plain
  generation works on the released 1.0.77 starter, and all three runs pass without the option.
- **Runner:** the first ladder's sealed runner is `39393be5…` (analyser `2eb87ca9`); this one is `b28ca7de…`
  (`df8dab57`). Their only difference is the stray-sweep path match: `in_base()` replaces a substring test, so a
  sibling base sharing a prefix is no longer swept. `coldstart_clock.py` is unchanged. Sealed
  PREDICTIONS-BASELINE.md says "the runner, `b28ca7de…`" under "What stays the same", which is wrong about the
  first ladder's runner; see POST-SEAL-NOTES.md.
- **Prestaged G14 rig, never an acceptance.** `acceptanceEligible` is false in every meta.json.
