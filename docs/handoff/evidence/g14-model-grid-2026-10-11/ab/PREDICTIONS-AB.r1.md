# A/B on the G14 rig: predictions, sealed before launch (2026-10-12)

Prestaged comparison, never a G14 acceptance (runner `--prestaged`, analyser commit `dac3f8ac86ce981b105c6dca4e1c03a1f7ba444d`).
Model claude-haiku-5-5, Claude Code 2.1.296, analyser 1.32.0, cap 1800 s, 3 runs per arm, interleaved
ctl, trt, ctl, trt, ctl, trt. Task: `TASK-AB.txt`, the G14 grid task without its download paragraph.

- **Control (ctl):** the published download as fetched (pristine copy, sha256 `9004bbea…`). Its skills are
  canonical@94e49ea7, and RUNBOOK step 5 says "Read the generated processor…".
- **Treatment (trt):** the same tree with exactly five files changed:
  - add-a-node, load-audit-log and run-mongoose-server SKILL.md from analyser PR #127 at `ed3d5ffc`,
    carrying the bundle's own TODO(bundle) substitutions;
  - audit-compare.py beside add-a-node;
  - RUNBOOK step 5 as in fluxtion-web PR #8 at `dc4a27a`.

  The author reads the Spring XML and compiler messages, never generated code. Digests are in MANIFEST-AB.sha256.

Measures: `score_g14.py` S2 to S6 (S1 does not apply when prestaged); `measure_reads.py` generatedReads,
generatedBytes and toolResultBytes; and from the transcript, cost, turns, max prompt and requests over 100k.

Baseline seen before sealing (G14 grid, which included acquisition): Haiku 5.5 r1/r2 made 4 and 5
generated-code reads, about 41 to 43 kB of 107 to 109 kB of tool output.

## Predictions

1. Treatment median generatedReads is at most 1, and control median is at least 3.
2. Treatment median toolResultBytes is at least 25% below control median.
3. Treatment median cost is below control median.
4. Correctness is maintained: treatment passes S2 to S6 in 3 of 3; control passes S2 to S6 in at least 2 of 3.
5. No treatment request exceeds 100k prompt tokens.
6. Treatment median turns is below control median.
7. Key and token scans are clean in 6 of 6.

The effect is shown if 1, 2 and 4 hold. Prediction 3 alone is not enough, since n is 3.
