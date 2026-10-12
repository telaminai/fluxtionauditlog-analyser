# A/B v2 on the G14 rig: predictions, sealed before launch (r2, 2026-10-12)

r2 changes only the orchestrator's leftover-process pattern. After ab-ctl2-r1 (kept), the precondition refused ab-trt2-r1: its pattern matched the operator's own monitor, whose command line names `fx-ab-ctl2-r*`. It now matches run folders only (`-r<n>/`). The predictions are unchanged.

Prestaged comparison, never a G14 acceptance. Harness: analyser commit `6522367c4f5d5defbfac3edc2a8e9b0c24902afb`. Model claude-haiku-5-5,
Claude Code 2.1.296, analyser 1.32.0, cap 1800 s, 3 runs per arm, interleaved ctl2, trt2, ctl2, trt2, ctl2, trt2.

- **Task `TASK-AB2.txt`:** v1's task with one change, "add a step" became "add a new node". In v1 one treatment
  run (trt-r3c) put the step inside RiskCheck, which the sealed S2 rejects.
- **ctl2:** the published bundle as fetched.
- **trt2:** v1's treatment (PR #127 skills, audit-compare.py, RUNBOOK step 5 from fluxtion-web #8), plus the
  bundle CLAUDE.md section "What to read when you change the graph", copied verbatim from fluxtion-web #8 at
  `5313262` and placed before "## Operate".

v1 found the reads happened while orienting, before RUNBOOK or a skill was read. CLAUDE.md is loaded first.

## Scoring, declared before the runs

- **PASS** = S2, S3, S4′, S5 and S6′.
  - **S4′** is S4 over both audit-log shapes (`score_s4_formats.py`).
  - **S6′** means 0 occurrences of any compilation-key value or of the trial token. Key-path pattern hits
    are reported but do not fail S6′: v1 showed they come from a discarded presence check.
  - The sealed S6 is reported beside S6′.
- **Reads:** `measure_reads.py`.
- **Cost:** CLI `total_cost_usd`.

v1 reference, same harness: ctl medians were 4 generated-code reads, 104 KB of tool output and $0.051.
trt v1 medians were 4 reads, 90 KB and $0.037.

## Predictions

1. trt2 median generatedReads is at most 2, and ctl2 median is at least 3.
2. trt2 median toolResultBytes is at least 20% below ctl2.
3. trt2 median cost is below ctl2.
4. Correctness is kept: trt2 PASSes 3 of 3, and ctl2 PASSes at least 2 of 3.
5. No trt2 request exceeds 100k prompt tokens.
6. S6′ holds in 6 of 6.

The effect is shown if 1, 2 and 4 hold.
