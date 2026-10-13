# TOKEN-SPLIT — where the authoring runs' tokens, tool bytes and cost go (M72 L0)

**Measured 2026-10-13** by `tools/token_split.py` from each run's stream-json transcript (not committed), backfilled
over the 12 baseline runs (this folder) and the 16 non-VOID first-ladder runs (`../authoring-ladder-2026-10-12/`).
Every number below is computed **from the committed tables only** — `results/<run>/buckets.json`,
`categories.json`, `phases.json` beside each `score.json` — by `python3 tools/token_split.py summary <results-dir>`;
the full output is at the end of this file. The sealed files (PREDICTIONS*, MANIFEST*, FIXTURES.md, score.json) are
unchanged. Tags as in the spec: **[D]** derived from these tables.

## Checks that the tables are the transcripts

- **Bytes**: in every one of the 28 runs, the classified tool-result bytes sum exactly to score.json's
  `reads.toolResultBytes` [D]. One call of 90 bytes (first ladder) and one of 162 (Sonnet 5) are `unclassified`;
  the baseline has none.
- **Cost**: cost recomputed from the per-request buckets is within **0.987–1.006** of the CLI's billed
  `usage.costUsd` on every Haiku 5.5 run and exact on every Sonnet 5 run [D]. This is **in-sample**: the prices were
  fitted to these runs, so it shows the bucket arithmetic is consistent, not that the prices are right; the
  out-of-sample check is L0's interleaved pair.
- **Pricing used** (fitted to the billed cost; re-read the provider's page before relying on it): Haiku 5.5
  $0.10 input / $0.01 cache read / **$0.20 one-hour cache write** / $0.50 output per M, ×5 for a request over
  100k; Sonnet 5 $2 / $0.20 / $4 / $10 per M, **no tier** (prompts up to 210k billed flat). The spec's supplied
  $0.125 cache write [S] is the five-minute rate; every write in these runs is a one-hour write.
- **Per-request output is not in stream-json** (only a stream-start value); the billed run total is allocated to
  requests by what each emitted (stated in buckets.json). Input buckets are exact.

## The answers

**1. Tool-result bytes by kind — the audit log is a small share; the kinds cannot be ranked.** Baseline (Haiku
5.5, 1.20 MB of tool results), listed in the classifier's priority order, not by size:

| kind | share by priority | single-kind share (lower bound) | upper bound | tier-weighted carried share |
|---|---|---|---|---|
| **audit log** | **8.8%** (raw text 8.2%) | 3.5% | 8.8% (+ ≤ 1.4% echoed, below) | **7.1%** |
| generated (processor, GraphML) | 1.0% | 0.0% | 1.0% | 1.4% |
| server (run scripts, `java`, server logs) | 1.2% | 0.2% | 3.0% | 1.2% |
| build (generate/validate/mvn/javac, compiler reports) | 14.6% | 3.5% | 15.9% | 15.4% |
| design (Spring XML, authoring JSON) | 22.6% | 1.0% | 25.3% | 24.4% |
| library (javap, jars, classes, Maven repository) | 9.6% | 3.4% | 16.5% | 11.3% |
| docs (Markdown, skills, help, WebFetch) | 18.0% | 6.0% | 33.6% | 19.7% |
| source (hand-written Java, POM, scripts, config, data) | 10.5% | 9.5% | 50.9% | 10.7% |
| analyser replies | 10.7% | 10.7% | 10.7% | 5.9% |
| other | 3.1% | 3.1% | 22.3% | 3.0% |

A call's bytes go to the highest-priority kind it names; the **single-kind share** counts only calls that read one
kind (the lower bound), and the **upper bound** counts every call that names the kind. Most bytes come from
**compound orientation reads** — one `cat … && cat …` of the authoring JSON with config, scripts, skills and
runbooks, or of the Spring XML with several Java sources. For those the split between kinds is not measured: design's
22.6% is a priority artefact (96% of its bytes come from multi-kind calls; single-kind 1.0%), and source (up to
50.9%) and docs (up to 33.6%) could each be larger. So no ranking of design, docs, build and source is claimed.
"Tier-weighted carried" weights each result by the later requests that re-read it, ×5 for those over 100k.

**L1's table view is not worth building yet.** The `auditLog` kind is **8.8%** of baseline tool bytes — its
classifier upper bound, auditCompare output included; raw text (without auditCompare) is 8.2%, with a per-run median
of 7.6% and a per-run maximum of 13.8%. Build and server results that also carry audit-record keys (a run or build
echoing records) add at most **1.4%** more as whole calls (first ladder Haiku 5.5: auditLog 4.6% plus up to 5.8%;
Sonnet 5: 2.6% plus up to 5.8%) [D]. Weighted by what the context carries, the auditLog kind is **7.1%**. L1's own
acceptance (auditLog bytes −75%) would therefore remove ≈ 5% (0.75 × 7.1%) of what tool results cost to carry, or a
little more counting echoed records — far smaller than the 26% between-session cost noise [G], so it could not even be
shown. The spec's §4 condition ("only if raw-log text is a material share") is not met; L1's skill text pointing at
the existing `read {fields}`, `series` and `aggregate` is enough for now.

**Correction to §1.3/L3: generated code is 1.0% of baseline tool bytes, not 26%.** score.json's `generatedBytes`
counts any call whose command names `/builder/` or reads a setup/validate/generate script, and most such calls are
compound reads of hand-written sources. L0's `generated` kind is the generated processor and GraphML only: 1.0%
(4 calls) on the baseline, down from 16.9% on the first ladder [D]. L3's driver is therefore much smaller than the
spec stated; the remaining reads are hand-written sources, builder code and scripts (`source`, `design`, `library`).

**2. Cost share from requests over 100k — 60%, not ≈ 40%.** Baseline Haiku 5.5: requests over 100k cost
**$0.891 of $1.487 recomputed = 60.0%** (input side alone 47.9%); at or under 100k 39.0%; CLI side calls 1.1%
[D]. Per run: median 57.9%, range 0–73%. First ladder Haiku 5.5: **72.8%** [D]. This replaces §1.5's INFERRED ≈ 40%
(input only) and its ≈ 30–35% saving: with the token counts unchanged, moving every such request under the tier
would cut the baseline's cost by 0.8 × 60.0% = **48%** (first ladder 58%) [D] — an upper bound, since keeping a
request under 100k also changes what it holds.

**3. Per-phase cost — diagnosis is the most expensive phase on the baseline.**

| baseline, Haiku 5.5 | cost share | requests | over 100k | tool bytes share |
|---|---|---|---|---|
| design | 29.5% | 259 | 0 | 78.7% |
| run | 14.6% | 62 | 14 | 11.7% |
| diagnosis | **54.8%** | 131 | **88** | 9.6% |

Diagnosis reads 9.6% of the tool bytes but pays 54.8% of the cost and holds 88 of the 102 requests over 100k [D].
That it does so *because it re-reads the design phase's context* is INFERRED: the tables do not attribute a
request's carried context to the phase that produced it, and as the last phase diagnosis necessarily carries the
largest context. The contrasts matter: on the first ladder (Haiku 5.5) diagnosis is 39.3% against design's 40.8%,
and for Sonnet 5 it is 18.3% (design 74.2%) [D]. L4 (iii) (diagnosis in a fresh context seeded with the receipt)
is better supported than before, but its saving is not estimated from these tables.

**Also measured**: the first request's prompt (the base prompt) has median **21.8k** tokens on the baseline (20.4k–23.1k),
confirming §1.3's ≈ 22k [S]. On the first ladder, where both models ran the same harness, the first prompt's
median is 21.4k for Haiku 5.5 and **32.7k** for Sonnet 5, 1.53× [D].

## Phase definition

Calls in transcript order. E = the last run call (a run/export script executed, or `java` run) before the last
analyser `open` of a log; B = the last successful build (generate.sh executed, mvn/mvnw, javac) before E; D = the
first analyser call after E. design = up to B, run = between, diagnosis = from D. A request takes the phase of the
first tool call it emits; one that emits none takes the phase of the nearest earlier request that emitted one. Full text in each phases.json and in the script. One run (lad-r5-h55-1) has no diagnosis
phase: it made no analyser call after its export.

## Generated summary

Run from this folder: `python3 ../../../../tools/token_split.py summary results ../authoring-ladder-2026-10-12/results`

### authoring-ladder-baseline-2026-10-13

| run | toolResultBytes = score.json | cost recomputed / billed | >100k requests | >100k cost share |
|---|---|---|---|---|
| base-r2-h55-1 | 107189 = 107189 yes | 0.9891 | 13/38 | 0.7332 |
| base-r2-h55-2 | 83468 = 83468 yes | 0.9912 | 11/45 | 0.607 |
| base-r2-h55-3 | 100423 = 100423 yes | 0.994 | 12/41 | 0.698 |
| base-r3-h55-1 | 103635 = 103635 yes | 1.0016 | 5/31 | 0.4479 |
| base-r3-h55-2 | 70837 = 70837 yes | 1.0 | 0/26 | 0.0 |
| base-r3-h55-3 | 109227 = 109227 yes | 0.9999 | 3/27 | 0.3967 |
| base-r4-h55-1 | 88522 = 88522 yes | 0.9922 | 4/42 | 0.3608 |
| base-r4-h55-2 | 91100 = 91100 yes | 0.9871 | 9/39 | 0.5505 |
| base-r4-h55-3 | 102027 = 102027 yes | 0.9977 | 14/46 | 0.7127 |
| base-r5-h55-1 | 121266 = 121266 yes | 0.9969 | 11/38 | 0.6741 |
| base-r5-h55-2 | 108109 = 108109 yes | 1.0063 | 7/38 | 0.5241 |
| base-r5-h55-3 | 114664 = 114664 yes | 1.0011 | 13/41 | 0.7109 |

**claude-haiku-5-5, 12 runs — tool-result bytes by kind** (share of 1,200,467 bytes; share = bytes assigned by kind priority; single-kind = calls that read only that kind, the lower bound; upper = every call that names the kind)

| kind | bytes | share | single-kind share (lower) | upper | calls | carried share | tier-weighted carried share |
|---|---|---|---|---|---|---|---|
| auditLog | 105,154 | 8.8% | 3.5% | 8.8% | 59 | 5.3% | 7.1% |
| generated | 11,554 | 1.0% | 0.0% | 1.0% | 4 | 1.4% | 1.4% |
| server | 14,555 | 1.2% | 0.2% | 3.0% | 16 | 0.9% | 1.2% |
| build | 174,808 | 14.6% | 3.5% | 15.9% | 78 | 14.6% | 15.4% |
| design | 271,610 | 22.6% | 1.0% | 25.3% | 33 | 28.6% | 24.4% |
| library | 115,704 | 9.6% | 3.4% | 16.5% | 50 | 11.0% | 11.3% |
| docs | 215,589 | 18.0% | 6.0% | 33.6% | 30 | 21.8% | 19.7% |
| source | 125,941 | 10.5% | 9.5% | 50.9% | 33 | 10.8% | 10.7% |
| analyser | 128,586 | 10.7% | 10.7% | 10.7% | 140 | 3.2% | 5.9% |
| other | 36,966 | 3.1% | 3.1% | 22.3% | 148 | 2.5% | 3.0% |
| unclassified | 0 | 0.0% | 0.0% | 0.0% | 0 | 0.0% | 0.0% |

Raw audit-log text (auditLog without auditCompare): 98,602 bytes, 8.2% of tool bytes; per run median 7.6%, max 13.8%. Build/server results that also carry audit-record keys: 17,117 bytes, 1.4% (whole calls, an upper bound on echoed audit text).

Cost: billed $1.4930, recomputed $1.4866 (0.9957); requests over 100k $0.8914 = **60.0%** of recomputed cost (input side alone $0.7120 = 47.9%); at or under 100k $0.5794 = 39.0%; CLI side calls $0.0158 = 1.1%.

| phase | cost $ | cost share | requests | over 100k | tool calls | analyser calls | tool bytes | bytes share |
|---|---|---|---|---|---|---|---|---|
| design | 0.4393 | 29.5% | 259 | 0 | 360 | 4 | 944,422 | 78.7% |
| run | 0.2170 | 14.6% | 62 | 14 | 80 | 12 | 140,471 | 11.7% |
| diagnosis | 0.8146 | 54.8% | 131 | 88 | 151 | 124 | 115,574 | 9.6% |
| (CLI side calls) | 0.0158 | 1.1% | | | | | | |

### authoring-ladder-2026-10-12

| run | toolResultBytes = score.json | cost recomputed / billed | >100k requests | >100k cost share |
|---|---|---|---|---|
| lad-r2-h55-1 | 109616 = 109616 yes | 0.9947 | 22/59 | 0.7891 |
| lad-r2-h55-2 | 109555 = 109555 yes | 0.997 | 14/43 | 0.7176 |
| lad-r2-h55-3 | 105517 = 105517 yes | 0.9927 | 17/49 | 0.7407 |
| lad-r2-s5-1 | 137557 = 137557 yes | 1.0 | 54/128 | 0.538 |
| lad-r3-h55-1 | 190314 = 190314 yes | 0.9997 | 25/48 | 0.863 |
| lad-r3-h55-2 | 165008 = 165008 yes | 1.0042 | 18/36 | 0.8144 |
| lad-r3-h55-3 | 142013 = 142013 yes | 0.9961 | 12/33 | 0.6987 |
| lad-r3-s5-1 | 187524 = 187524 yes | 1.0 | 74/122 | 0.7121 |
| lad-r4-h55-1 | 84039 = 84039 yes | 0.9916 | 12/43 | 0.613 |
| lad-r4-h55-2 | 100568 = 100568 yes | 0.9986 | 18/45 | 0.7587 |
| lad-r4-h55-3 | 94570 = 94570 yes | 0.9982 | 9/43 | 0.628 |
| lad-r4-s5-1 | 134374 = 134374 yes | 1.0 | 37/82 | 0.5418 |
| lad-r5-h55-1 | 99331 = 99331 yes | 0.9991 | 3/44 | 0.3302 |
| lad-r5-h55-2 | 109826 = 109826 yes | 1.004 | 7/44 | 0.5313 |
| lad-r5-h55-3 | 137858 = 137858 yes | 0.9942 | 20/55 | 0.7441 |
| lad-r5-s5-1 | 199550 = 199550 yes | 1.0 | 54/94 | 0.6796 |

**claude-haiku-5-5, 12 runs — tool-result bytes by kind** (share of 1,448,215 bytes; share = bytes assigned by kind priority; single-kind = calls that read only that kind, the lower bound; upper = every call that names the kind)

| kind | bytes | share | single-kind share (lower) | upper | calls | carried share | tier-weighted carried share |
|---|---|---|---|---|---|---|---|
| auditLog | 66,256 | 4.6% | 1.3% | 4.6% | 58 | 2.2% | 3.1% |
| generated | 245,432 | 16.9% | 3.2% | 17.2% | 53 | 19.6% | 19.9% |
| server | 72,009 | 5.0% | 1.9% | 7.5% | 33 | 3.1% | 4.0% |
| build | 162,000 | 11.2% | 6.0% | 16.3% | 88 | 10.6% | 11.4% |
| design | 210,796 | 14.6% | 0.6% | 17.8% | 23 | 19.4% | 16.7% |
| library | 156,879 | 10.8% | 4.1% | 16.8% | 52 | 10.4% | 11.3% |
| docs | 217,265 | 15.0% | 3.3% | 30.0% | 33 | 19.1% | 17.1% |
| source | 156,224 | 10.8% | 10.6% | 46.9% | 37 | 11.5% | 10.8% |
| analyser | 124,741 | 8.6% | 8.6% | 8.6% | 132 | 2.1% | 3.5% |
| other | 36,523 | 2.5% | 2.5% | 20.2% | 167 | 1.9% | 2.2% |
| unclassified | 90 | 0.0% | 0.0% | 0.0% | 1 | 0.0% | 0.0% |

Raw audit-log text (auditLog without auditCompare): 66,256 bytes, 4.6% of tool bytes; per run median 5.1%, max 13.5%. Build/server results that also carry audit-record keys: 84,166 bytes, 5.8% (whole calls, an upper bound on echoed audit text).

Cost: billed $2.3387, recomputed $2.3326 (0.9974); requests over 100k $1.6972 = **72.8%** of recomputed cost (input side alone $1.3758 = 59.0%); at or under 100k $0.6070 = 26.0%; CLI side calls $0.0285 = 1.2%.

| phase | cost $ | cost share | requests | over 100k | tool calls | analyser calls | tool bytes | bytes share |
|---|---|---|---|---|---|---|---|---|
| design | 0.9511 | 40.8% | 376 | 35 | 480 | 11 | 1,235,639 | 85.3% |
| run | 0.4353 | 18.7% | 67 | 46 | 76 | 18 | 112,001 | 7.7% |
| diagnosis | 0.9177 | 39.3% | 99 | 96 | 121 | 103 | 100,575 | 6.9% |
| (CLI side calls) | 0.0285 | 1.2% | | | | | | |

Runs with no diagnosis phase: lad-r5-h55-1.

**claude-sonnet-5, 4 runs — tool-result bytes by kind** (share of 659,005 bytes; share = bytes assigned by kind priority; single-kind = calls that read only that kind, the lower bound; upper = every call that names the kind)

| kind | bytes | share | single-kind share (lower) | upper | calls | carried share | tier-weighted carried share |
|---|---|---|---|---|---|---|---|
| auditLog | 17,097 | 2.6% | 1.2% | 2.6% | 17 | 2.2% | 2.2% |
| generated | 32,040 | 4.9% | 3.4% | 4.9% | 26 | 4.6% | 4.6% |
| server | 36,138 | 5.5% | 3.3% | 6.3% | 36 | 2.9% | 2.9% |
| build | 65,910 | 10.0% | 8.6% | 10.6% | 49 | 9.2% | 9.2% |
| design | 36,636 | 5.6% | 5.1% | 6.0% | 14 | 6.8% | 6.8% |
| library | 186,163 | 28.2% | 7.5% | 30.6% | 88 | 27.7% | 27.7% |
| docs | 55,246 | 8.4% | 8.4% | 8.6% | 29 | 12.9% | 12.9% |
| source | 143,464 | 21.8% | 18.9% | 23.5% | 61 | 26.9% | 26.9% |
| analyser | 61,575 | 9.3% | 9.3% | 9.3% | 67 | 2.5% | 2.5% |
| other | 24,574 | 3.7% | 3.7% | 32.0% | 97 | 4.4% | 4.4% |
| unclassified | 162 | 0.0% | 0.0% | 0.0% | 1 | 0.0% | 0.0% |

Raw audit-log text (auditLog without auditCompare): 17,097 bytes, 2.6% of tool bytes; per run median 2.4%, max 5.6%. Build/server results that also carry audit-record keys: 38,244 bytes, 5.8% (whole calls, an upper bound on echoed audit text).

Cost: billed $13.6692, recomputed $13.6692 (1.0000); requests over 100k $8.6019 = **62.9%** of recomputed cost (input side alone $7.5735 = 55.4%); at or under 100k $5.0566 = 37.0%; CLI side calls $0.0108 = 0.1%.

| phase | cost $ | cost share | requests | over 100k | tool calls | analyser calls | tool bytes | bytes share |
|---|---|---|---|---|---|---|---|---|
| design | 10.1375 | 74.2% | 340 | 133 | 391 | 15 | 593,135 | 90.0% |
| run | 1.0204 | 7.5% | 26 | 26 | 28 | 0 | 17,356 | 2.6% |
| diagnosis | 2.5006 | 18.3% | 60 | 60 | 66 | 52 | 48,514 | 7.4% |
| (CLI side calls) | 0.0108 | 0.1% | | | | | | |

