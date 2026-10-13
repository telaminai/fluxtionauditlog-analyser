# Authoring ladder on the fully released stack: results (2026-10-13)

**Runs:** claude-haiku-5-5, 3 runs each on rungs 2, 3, 4, 5 and 6, 15 runs in all, one at a time, between
2026-10-13T20:54:12Z and 22:07:18Z. **No run was VOID and none was re-run.**

**Stack:**
- the analyser is 1.34.0, both as the operator's `--rest` instance and as the subject's MCP bridge;
- starter-core and BOM 1.0.79, runtime 1.1.1, hosted generator 1.0.79;
- Claude Code 2.1.296 is the same read-only binary as before (`claudeBinUnchanged` in all 15 meta.json).

**Sealed before the first run:** the predictions and manifest, sealed at 2026-10-13T20:53:59Z (commit `a445666d`,
pushed). Supporting files: `DEPLOYMENT-CHECKS.md` (12 checks), `FIXTURES.md` (downloads, fixtures, rung 6, ground
truth, controls, seeds).

**Sources:**
- every number below comes from the committed `results/<run>/*.json`, computed by `harness/collect_rel.py`;
- the baseline comparison reads `../authoring-ladder-baseline-2026-10-13/results/`;
- `TOKEN-SPLIT.md` holds the L0 split and the "what fills the context past 100k" analysis;
- transcripts are not committed, and home paths are redacted to `~`.

**PASS** = ORACLE + VALID + CHART + S6′ (`harness/score_ladder.py`). Rung 6's ORACLE is `r6/r6_oracle.py`, which is
X1 (XML-first) + X2 (dispatch) + X3 (output).

**Confounds:** the analyser (1.33.0 → 1.34.0, its skills and docs), the starter (1.0.77 → 1.0.79), the generator
(1.0.78 → 1.0.79) and the templates (capped scripts, annotated shipped processor, re-vendored add-a-node) all
changed together since the baseline. **Every delta is a delta of the whole released stack, and none is attributable
to one lever.** With n = 3 per rung, a one-run difference on a rung is noise.

## Per run

Two columns report generated-code reads:
- **generatedReads (pattern)** is `measure_reads.py`'s count, kept for comparability with earlier ladders.
- **content reads** is `harness/content_reads.py`, added after sealing at the owner's request; it is reporting
  only and does not change the score. See *Generated-code reads* below.

| run | PASS | ORACLE | VALID | CHART | S6′ | cost $ | turns | wall s | max prompt | >100k | generatedReads (pattern) | content reads | toolResultBytes | runExport | run-and-export | --no-reconcile | withdraw | subagent calls / offered | gen version | annotation in export |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| rel-r2-h55-1 | True | True | True | True | True | 0.126 | 53 | 199 | 110991 | 10 | 2 | 0 | 92007 | 3 | 0 | 0 | 0 | 0 / True | 1.0.79 | None |
| rel-r2-h55-2 | True | True | True | True | True | 0.150 | 55 | 238 | 110781 | 12 | 3 | 1 | 104887 | 3 | 0 | 0 | 0 | 0 / True | 1.0.79 | None |
| rel-r2-h55-3 | True | True | True | True | True | 0.254 | 68 | 245 | 139198 | 20 | 2 | 1 | 131694 | 3 | 0 | 0 | 0 | 0 / True | 1.0.79 | None |
| rel-r3-h55-1 | True | True | True | True | True | 0.093 | 47 | 170 | 106039 | 6 | 0 | 0 | 99120 | 3 | 2 | 0 | 0 | 0 / True | 1.0.79 | True |
| rel-r3-h55-2 | True | True | True | True | True | 0.186 | 54 | 243 | 132786 | 14 | 3 | 1 | 159877 | 4 | 2 | 0 | 0 | 0 / True | 1.0.79 | True |
| rel-r3-h55-3 | True | True | True | True | True | 0.167 | 66 | 477 | 123364 | 11 | 5 | 1 | 122139 | 5 | 2 | 0 | 0 | 0 / True | 1.0.79 | True |
| rel-r4-h55-1 | True | True | True | True | True | 0.199 | 59 | 256 | 121635 | 17 | 5 | 1 | 118420 | 4 | 0 | 0 | 0 | 0 / True | 1.0.79 | None |
| rel-r4-h55-2 | True | True | True | True | True | 0.367 | 72 | 312 | 141647 | 35 | 4 | 1 | 129837 | 5 | 0 | 0 | 2 | 0 / True | 1.0.79 | None |
| rel-r4-h55-3 | True | True | True | True | True | 0.260 | 77 | 262 | 134571 | 22 | 3 | 1 | 122203 | 5 | 0 | 0 | 1 | 0 / True | 1.0.79 | None |
| rel-r5-h55-1 | False | True | True | False | True | 0.196 | 57 | 276 | 119077 | 18 | 3 | 1 | 115116 | 4 | 4 | 1 | 3 | 0 / True | 1.0.79 | True |
| rel-r5-h55-2 | True | True | True | True | True | 0.045 | 40 | 138 | 96556 | 0 | 2 | 1 | 100203 | 2 | 2 | 0 | 0 | 0 / True | 1.0.79 | True |
| rel-r5-h55-3 | True | True | True | True | True | 0.077 | 48 | 239 | 103654 | 3 | 1 | 0 | 92378 | 5 | 5 | 0 | 1 | 0 / True | 1.0.79 | True |
| rel-r6-h55-1 | True | True | True | True | True | 0.042 | 39 | 169 | 90892 | 0 | 4 | 1 | 85923 | 4 | 2 | 0 | 0 | 0 / True | 1.0.79 | True |
| rel-r6-h55-2 | True | True | True | True | True | 0.038 | 32 | 122 | 87930 | 0 | 2 | 1 | 70990 | 2 | 2 | 0 | 0 | 0 / True | 1.0.79 | True |
| rel-r6-h55-3 | True | True | True | True | True | 0.101 | 43 | 222 | 105104 | 7 | 1 | 1 | 105682 | 3 | 2 | 0 | 0 | 0 / True | 1.0.79 | True |

The `withdraw` column is the probe's count of calls naming the starter's `withdraw` command. That includes
`withdraw --help`, so see *withdraw* below for real use.

### Rung 6 (new): the oracle's three checks

| r6 run | X1 XML-first | X1 checks failing | X2 dispatch | X3 output | @ServiceRegistered writes |
|---|---|---|---|---|---|
| rel-r6-h55-1 | True | - | True | True | 0 |
| rel-r6-h55-2 | True | - | True | True | 0 |
| rel-r6-h55-3 | True | - | True | True | 0 |


## Summary (Haiku 5.5)

- **PASS:** rung 2 **3/3**, rung 3 **3/3**, rung 4 **3/3**, rung 5 **2/3**, rung 6 **3/3**. That is **11/12** on
  rungs 2–5 and **14/15** overall.
- **The one failure,** rel-r5-h55-1, fails CHART alone; its ORACLE (I1, I2, I3), VALID and S6′ pass. The saved
  analyser state holds no graph (`graphs: []`), and the run's own answer says it "could not get a chart of EURUSD
  alone onto the canvas".
- **Cost:** total **$2.30** over the 15 runs.
  - Rungs 2–5: total $2.12, median **$0.176**.
  - Rung 6: total $0.18, median $0.042.
- **Turns:** median 56 on rungs 2–5, and 39 on rung 6.
- **Wall:** median 244 s on rungs 2–5, and 169 s on rung 6. The longest run took 477 s, and none reached the
  2400 s cap.
- **Prompts over 100k:** median **13** per run on rungs 2–5 (168 in total), and 0 on rung 6 (7 in total, all in
  one run).
- **Generated-code reads:**
  - pattern: total 33 and median 3 on rungs 2–5;
  - content: **0 reads of the generated processor or its GraphML in any of the 15 runs**. Builder or supplier
    source was read in 12 of 15 runs, 1 call each.
- **Bundle runs (rungs 3, 5, 6):** `run-and-export.sh` was used in **9 of 9**. Median `runExportCalls` was 4, and
  every exported log carries `annotation:` (9 of 9).
- **Generator:** every final generated processor stamps target generator **1.0.79** (15 of 15).
- **`withdraw`:** used for real in **4 runs**, 3 of them after a SPRING_RECONCILE_CONFLICT and 1 (rel-r5-h55-1) after the starter wrote supplier-class shells; see below.
- **`--no-reconcile`:** used in 1 run (rel-r5-h55-1).
- **Subagents:** the subagent tool was offered in 15 of 15 runs (`subagentOffered`) and used in 0.
- **S6′:** holds in 15 of 15, and every key scan is clean (0 secret values, 0 token occurrences, 0 key-path or
  token-path reads).

## Against the baseline (rungs 2–5, Haiku 5.5)

All figures are the median per run, except PASS.

| rung | measure (median per run unless PASS) | baseline | release |
|---|---|---|---|
| 2 | PASS | 1/3 | 3/3 |
| 2 | cost $ | 0.159 | 0.150 |
| 2 | turns | 59 | 55 |
| 2 | wall s | 244.665 | 237.836 |
| 2 | prompts >100k | 12 | 12 |
| 2 | max prompt | 116518 | 110991 |
| 2 | generatedReads | 2 | 2 |
| 2 | toolResultBytes | 100423 | 104887 |
| 3 | PASS | 3/3 | 3/3 |
| 3 | cost $ | 0.073 | 0.167 |
| 3 | turns | 38 | 54 |
| 3 | wall s | 191.881 | 242.925 |
| 3 | prompts >100k | 3 | 11 |
| 3 | max prompt | 103196 | 123364 |
| 3 | generatedReads | 4 | 3 |
| 3 | toolResultBytes | 103635 | 122139 |
| 4 | PASS | 3/3 | 3/3 |
| 4 | cost $ | 0.126 | 0.260 |
| 4 | turns | 53 | 72 |
| 4 | wall s | 213.298 | 261.535 |
| 4 | prompts >100k | 9 | 22 |
| 4 | max prompt | 107860 | 134571 |
| 4 | generatedReads | 3 | 4 |
| 4 | toolResultBytes | 91100 | 122203 |
| 5 | PASS | 3/3 | 2/3 |
| 5 | cost $ | 0.160 | 0.077 |
| 5 | turns | 49 | 48 |
| 5 | wall s | 267.22 | 239.447 |
| 5 | prompts >100k | 11 | 3 |
| 5 | max prompt | 117441 | 103654 |
| 5 | generatedReads | 3 | 2 |
| 5 | toolResultBytes | 114664 | 100203 |
| 2-5 | PASS | 10/12 | 11/12 |
| 2-5 | cost $ | 0.132 | 0.176 |
| 2-5 | turns | 53 | 56 |
| 2-5 | wall s | 216.101 | 243.777 |
| 2-5 | prompts >100k | 10 | 13 |
| 2-5 | max prompt | 108652 | 120356 |
| 2-5 | generatedReads | 3.500 | 3.000 |
| 2-5 | toolResultBytes | 102831 | 116768 |
| 6 | PASS | - | 3/3 |
| 6 | cost $ | - | 0.042 |
| 6 | turns | - | 39 |
| 6 | wall s | - | 168.855 |
| 6 | prompts >100k | - | 0 |
| 6 | max prompt | - | 90892 |
| 6 | generatedReads | - | 2 |
| 6 | toolResultBytes | - | 85923 |

**Totals on rungs 2–5:**

| | Baseline | Release |
|---|---|---|
| Cost | $1.49 | $2.12 |
| Generated reads (pattern) | 41 | 33 |
| Generated reads (content) | 11 | 9 |
| Prompts over 100k | 102 | 168 |
| Tool-result bytes | 1,200,467 | 1,387,881 |

**What changed, by rung:**
- **Rung 2:** up from 1/3 to 3/3. All three of the baseline's CHART failures are gone, and cost and over-100k
  counts are flat.
- **Rung 3:** still 3/3, but its median cost more than doubled ($0.073 → $0.167). Two of its three runs hit an
  un-injected service (TradeLimits) in the author's own change: the server ran, every event failed in the new node,
  and the authors diagnosed it from `logs/server.log` (TOKEN-SPLIT.md).
- **Rung 4:** still 3/3, but its median cost doubled ($0.126 → $0.260), with 22 over-100k prompts against 9. Two of
  its three runs entered a reconcile loop: SPRING_RECONCILE_CONFLICT, then `withdraw`, then regenerate. One of them
  logged 9 conflict results and 35 over-100k requests.
- **Rung 5:** fell from 3/3 to 2/3, the one CHART failure; its median cost fell ($0.160 → $0.077).

None of these rung-level changes can be attributed to one lever; the confounds are stated above.

## Sealed predictions against outcomes (PREDICTIONS-RELEASE.md)

| # | Prediction | Outcome |
|---|---|---|
| 1 | R2 ≥ 1/3, and a failure, if any, is CHART | holds: 3/3, no failure |
| 2 | R3, R4, R5 ≥ 2/3 each | holds: 3/3, 3/3, 2/3 |
| 3 | R6 ≥ 1/3 | holds: 3/3 |
| 4 | R6 XML-first: X1 ≥ 2/3; @ServiceRegistered ≤ 1/3; SinkBinding ≥ 2/3 | holds: X1 3/3; 0/3; 3/3 |
| 5 | R6's most common failure is X2 or X3 | not applicable (no R6 failure) |
| 6 | Rungs 2–5 ≥ 10/12 | holds: 11/12 |
| 7 | No run uses `withdraw` | **wrong**: 4 runs used it for real (r4-h55-2, r4-h55-3, r5-h55-1, r5-h55-3) |
| 8 | No run passes `--no-reconcile` | **wrong**: 1 run (rel-r5-h55-1, after its withdrawals) |
| 9 | Rungs 2–5 generatedReads ≤ 41 total and ≤ 3.5 median | holds: 33; 3 |
| 10 | Rungs 2–5 median >100k ≤ 10 | **wrong**: 13 |
| 11 | Rungs 2–5 median cost ≤ $0.15; total of 15 ≤ $3.00; R6 median above the R3+R5 median | **wrong** on 2 of 3 parts: $0.176; $2.30 (holds); R6 $0.042 against R3+R5 $0.130 |
| 12 | `run-and-export.sh` in ≥ 8/9 bundle runs | holds: 9/9 |
| 13 | subagentOffered 15/15; subagent calls in ≤ 2/15 | holds: 15/15; 0/15 |
| 14 | `annotation:` in every bundle export; every processor stamps 1.0.79 | holds: 9/9; 15/15 |
| 15 | Rungs 2–5 median wall ≤ 300 s; no cap | holds: 244 s; none |
| 16 | S6′ 15/15 | holds |

## Rung 6: XML-first, and no withdraw

All three rung-6 authors stayed XML-first. Each final design declares:
- the TurnoverTracker bean, with constructor refs to rootNode, riskCheck and csvRejections;
- the node in nodeBeans;
- a `ReferenceBinding` that makes csvRejections `DATA`, with neither trigger parent DATA;
- a `turnover` `SinkBinding`.

None wrote `@ServiceRegistered` (`serviceRegisteredWrites` = 0), and none used `withdraw` or `--no-reconcile`.
In the audit log:
- X2: TurnoverTracker runs after rootNode and riskCheck for every accepted price, and in no CsvRejection record;
- X3: `data/turnover.txt` ends with exactly the five expected records.

Rung 6 was the cheapest rung: a median of $0.042, 39 turns, and 0 over-100k prompts in 2 of the 3 runs.

## `withdraw` (new in 1.0.79)

Four runs invoked the starter's new `withdraw` command. The table gives the calls themselves; reconciler and
starter messages are quoted, never file content. The bundle's own PROJECT.md and runbooks name the conflict and
point to the remedy; those doc mentions are what the probe's `reconcileConflicts` counts in rel-r5-h55-1.

| run | conflict, from the reconciler's own messages | withdraw call(s) | then |
|---|---|---|---|
| rel-r4-h55-2 | `VwapNode#field:sinkVwap`, "Unowned construct contradicts the declaration" (9 conflict results) | `withdraw --help`, then `withdraw --class com.example.myapp.node.VwapNode --still-declared` | regenerate: "3 adopted" |
| rel-r4-h55-3 | 3 conflict results | `withdraw --class …VwapCalculator --member field:vwapPublisher` | regenerate: clean |
| rel-r5-h55-1 | **No conflict in any generate result.** Its first generate after declaring the supplier beans reported "members 3 added": class shells written under `src/main/java/com/acme/fx`, read back at call 22. | `withdraw --help`, then `withdraw --class com.acme.fx.FxQuote` and `--class com.acme.fx.FxRateBook --still-declared`, then the shells removed | `./generate.sh --no-reconcile` |
| rel-r5-h55-3 | `com.acme.fx.FxRateBook#<class>`, "Recorded source src/main/java/com/acme/fx/FxRateBook.java has been removed" | `withdraw --class com.acme.fx.FxRateBook --still-declared`, then `--class com.acme.fx.FxQuote` | regenerate: clean |

**On rung 5, the order of setup and the pom change decided it.**
- In both rung-5 runs that withdrew, `./setup.sh` ran **before** the pom gained the supplier dependency (calls
  6 → 10 in rel-r5-h55-1, and 5/6 → 8 in rel-r5-h55-3), and was not re-run before the first generate. So
  `.fluxtion/classpath` lacked the supplier jar, and the first generate wrote class shells for the supplier
  classes ("members 3 added").
- In rel-r5-h55-3 the author deleted the shells, the ownership record still named them, and the reconciler
  refused with "Recorded source … has been removed". `withdraw` cleared it.
- rel-r5-h55-2 ran setup **after** the pom edit (calls 8–9 → 14). It met none of this and was the cheapest rung-5
  run ($0.045, 0 over-100k).
- The ground truth also patched the pom before setup.
- The skill says to re-run setup after a dependency change. Why the two authors did not is INFERRED, and the
  classpath cause above is inferred from the order of calls and the starter's "3 added". Both runs that withdrew still pass I3 (no hand-written Java
remains, and the supplier repository is unchanged), so the `withdraw` path did its job. Its cost shows in rung 4's
over-100k counts.

## Generated-code reads, two ways

`measure_reads.py` counts a call when its command or path **names** generated code (`/generated/`, the processor or
GraphML file, the builder, or a script's source read with cat/sed/head/less). That count therefore includes:
- a directory listing of `generated/`;
- a Write of the author's own file whose path matches;
- a read of the builder.

`harness/content_reads.py` (post-seal, reporting only) counts a call only when its **tool result** carries
generator-written content: the processor's version stamp, generated dispatch identifiers (`isDirty_…`,
`guardCheck_…`), GraphML, or a builder or supplier class body. Each such call is listed by command shape in
`results/<run>/content-reads.json`.

| | Pattern count (measure_reads) | Content reads: processor or GraphML | Content reads: builder or supplier source |
|---|---|---|---|
| Release, rungs 2–5 (12 runs) | 33 (median 3) | **0** | 9 (9 runs) |
| Release, rung 6 (3 runs) | 7 | **0** | 3 (3 runs) |
| Baseline, rungs 2–5 (12 runs) | 41 (median 3.5) | **0** | 11 (10 runs) |

The baseline content reads were computed from the baseline's preserved transcripts and are committed in
`baseline-content-reads/`.

**Which number to judge the guidance by:** the "don't read generated code" guidance should be judged by the
**content reads**. The pattern count stays only so that numbers stay comparable across ladders, because a path
match without content (a listing, or a Write of one's own file) is not a read.

Judged that way:
- no run on this stack, and none on the baseline, read the generated processor or its GraphML;
- the builder and supplier sources the guidance also names are still read once in most runs, typically inside an
  orientation `cat` of several sources.

For contrast, the first ladder's lad-r2-h55-1 read MyProcessor.java dispatch code 4 times.

## Disclosures

- **Small samples.** n = 3 per rung.
- **No VOID runs and no re-runs.**
- **No post-seal change to the scored set.** Fixtures, tasks, oracles, scorer, probe and driver are as sealed.
  Added after the runs, reporting only:
  - `content_reads.py` (owner request);
  - `past100k.py`, `service_failures.py` and `past100k_md.py` (part A, owner request);
  - `collect_rel.py` and `early.py`.
- **The early-report pause marker.**
  - At 20:55:29Z, after the coordinator first asked for an extra sanity run, I created an empty directory
    `fx-rel-r2-h55-2`. It was meant to stop the driver after the first cell.
  - The request was corrected minutes later, and I removed the directory at 20:55:35Z, while rel-r2-h55-1 was still
    running. No cell was affected: the driver created r2-h55-2's base itself after r2-h55-1 had ended.
  - No sanity run was made, and the predictions were sealed before any run.
- **Two precondition refusals.**
  - **First refusal:** after rel-r5-h55-2, at 21:42:52Z, the driver's stray/port precondition refused to start
    rel-r5-h55-3 (leftover pid 74132).
  - **Second refusal:** on relaunch, at 21:43:15Z, it refused again (pids 74293 and 74300).
  - **State each time:** port 8181 was free, the pids were gone on inspection, and no run base had been created.
  - **Likely cause:** another session's bundle measurement (fluxtion-web PR #17), whose bundle-jar processes match the
    stray pattern. This is unconfirmed. The first may instead have been the operator's own shell running `ls` on a
    finished run's base.
  - **Restart:** the driver was restarted at 21:54:21Z, skipping completed cells, and ran the last four.
  - **Effect on runs:** every run's own `precondition.json` records port 8181 free and no strays at its start, and
    every meta.json records the process group reaped with no strays swept.
- **The Skill tool, like the Agent tool, is not gated by the runner's `--allowedTools` list.**
  - 7 of the 15 runs loaded a skill through it (9 calls). Six were the project's own skills: `load-audit-log` in 5,
    `add-a-node`, and `point-at-the-fault`. Two were the CLI-bundled `dataviz`.
  - The baseline had 1 such call, and the first ladder's Haiku runs had 2 (one of them `dataviz`).
  - Reported here, not scored.
- **Warm cache.** Every run was seeded from caches cold-filled and provenance-checked today (FIXTURES.md), as at
  baseline.
- **Rung 6 fixture.** It carries two modifications, disclosed in FIXTURES.md. One makes `CsvRejections` propagate,
  so that "not triggered by the DATA parent" is observable; it was found by control C2 before sealing.
- **Prestaged G14 rig, never an acceptance.** `acceptanceEligible` is false in all 15 meta.json files.
