# G14 model grid and guidance A/B — results (2026-10-11/12)

The per-run evidence is under `results/<run>/`: `score.json`, `meta.json`, `keyscan.json`, `config.json`, and
`reads.json` for the A/B runs. The post-seal content checks are `S1prime.json` (acquisition by content) and
`S4prime.json` (the audit-log format check). Transcripts are not committed. Every number is computed from these
files. The collected files contain no secret key value (the key file's API-key fields) and no trial token, which
was scanned before committing. Two public endpoint paths that also appear in the key file are named in this text
(see S6 below).

### G14 grid (published bundle)

| run | model | S1 sealed | S1′ content | S2 | S3 | S4 sealed | S4′ | S5 chart | S6 sealed | key-file values that occur | PASS′ (S1′ as written) | cost $ | turns | max prompt | >100k |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| g14-45-r1b | claude-haiku-4-5-20251001 | False | True | True | True | True | True | False | True | 0 | False | 0.45725039999999995 | 23 | 76927 | 0 |
| g14-45-r2b | claude-haiku-4-5-20251001 | False | True | True | True | False | True | False | True | 0 | False | 0.8804962000000003 | 93 | 108791 | 13 |
| g14-45-r3b | claude-haiku-4-5-20251001 | False | True | True | True | True | True | False | True | 0 | False | 0.45899680000000015 | 59 | 73672 | 0 |
| g14-55-r1 | claude-haiku-5-5 | False | — | False | False | False | — | False | True | 0 | False | 0.00335286 | 5 | 20289 | 0 |
| g14-55-r1b | claude-haiku-5-5 | False | True | True | True | True | True | True | True | 0 | True | 0.051437540000000004 | 35 | 97448 | 0 |
| g14-55-r2b | claude-haiku-5-5 | False | True | True | True | True | True | True | True | 0 | True | 0.12550474 | 51 | 109923 | 7 |
| g14-55-r3b | claude-haiku-5-5 | False | False | True | True | True | True | True | False | 24 | False | 0.10941887 | 49 | 112155 | 6 |

**Result.**
- **Under the corrected checks (PASS′ = S1′ + S2 + S3 + S4′ + S5 + S6′):** Haiku 5.5 is 2/3 with S1′ as written,
  or 3/3 counting the transcript as evidence of acquisition. That 3/3 also relies on S6′ in place of the sealed S6
  (see below). Haiku 4.5 is 0/3: every run fails S5, the chart.
- **Under the sealed checks:** 0/6. The sealed S1 (digest equality) cannot hold for any run, because the scaffold
  is generated per request.
- `g14-55-r1` is the void first launch, where `dontAsk` denied every tool; it is excluded.

**Sealed predictions against outcomes** (PREDICTIONS.md):

| # | Prediction | Outcome |
|---|---|---|
| 1 | Haiku 5.5 passes at least 2 of 3 | holds under PASS′ (2/3 as written), fails under sealed PASS (0/3) |
| 2 | Haiku 4.5 passes at most 1 of 3 | holds (0/3) |
| 3 | S1 holds in 6 of 6 | fails (0/6: digest per request) |
| 4 | S6 holds in 6 of 6; rotate on any failure | fails as sealed (r3b). The hits are public values (below), so the key was **not rotated**: no secret left the box |
| 5 | Every run reaching S2 passes S3 | holds (6/6) |
| 6 | Haiku 4.5's most common failure is S5 | holds (3/3) |
| 7 | Haiku 5.5 median cost ≤ 20% of Haiku 4.5's | fails ($0.109 vs $0.459 = 24%) |
| 8 | A Haiku 4.5 run reaches the 1800 s cap | fails (longest 663 s) |
| 9 | Some run sends a prompt over 100k | holds |
| 10–11 | Final-answer symbol and stop-server TimeoutException | not checked |

### A/B (prestaged; correctness = S2–S5 and no token, since S1 acquisition does not apply)

| run | arm | correct (S2–S5, no token) | generated-code reads | tool-result bytes | cost $ | turns |
|---|---|---|---|---|---|---|
| ab-ctl-r1 | — | VOID: stray servers from earlier runs held port 8181 | | | | |
| ab-ctl-r1b | — | VOID: completed (37 turns), but the new sweep killed the operator's REST analyser, so the canvas could not be scored; its score.json is empty (0 bytes) | | | | |
| ab-ctl-r1c | v1 control | True | 4 | 103911 | 0.0509 | 37 |
| ab-ctl-r2c | v1 control | True | 5 | 79281 | 0.0533 | 50 |
| ab-ctl-r3c | v1 control | True | 4 | 104071 | 0.0457 | 40 |
| ab-ctl2-r1 | v2 control | True | 5 | 85379 | 0.0378 | 33 |
| ab-ctl2-r2 | v2 control | True | 3 | 75545 | 0.0368 | 34 |
| ab-ctl2-r3 | v2 control | True | 4 | 91981 | 0.0385 | 31 |
| ab-trt-r1 | — | VOID: started at 18:00:55Z after the strays were stopped (8181 free), then halted by the operator about 16 s later while the runner was fixed; incomplete. The sealed PREDICTIONS-AB r2 text gives a different reason; this row corrects it | | | | |
| ab-trt-r1c | v1 treatment | True | 4 | 89701 | 0.0462 | 40 |
| ab-trt-r2c | v1 treatment | True | 6 | 89678 | 0.0367 | 38 |
| ab-trt-r3c | v1 treatment | False | 2 | 92668 | 0.0319 | 26 |
| ab-trt2-r1 | v2 treatment | True | 1 | 48396 | 0.0291 | 33 |
| ab-trt2-r2 | v2 treatment | True | 2 | 76750 | 0.0297 | 28 |
| ab-trt2-r3 | v2 treatment | True | 1 | 68113 | 0.0328 | 35 |

- **v1 control** (n=3): correct 3/3; median generated-code reads 4; median tool-result bytes 103911; median cost $0.0509
- **v1 treatment** (n=3): correct 2/3; median generated-code reads 4; median tool-result bytes 89701; median cost $0.0367
- **v2 control** (n=3): correct 3/3; median generated-code reads 4; median tool-result bytes 85379; median cost $0.0378
- **v2 treatment** (n=3): correct 3/3; median generated-code reads 1; median tool-result bytes 68113; median cost $0.0297

v1 shows no effect on generated-code reads. **v2 shows one:** median reads 4 → 1, tool-result bytes −20%, cost
−21%, correctness 3/3 in both arms. The v2 treatment is the guidance shipped in fluxtion-web #8 (merged
2026-10-13) and analyser 1.33.0 (CHANGELOG, `add-a-node`).

**How strong this is.** v1 control and v2 control are the same pristine arm, run in different sessions, yet they
differ by 26% in cost and 18% in bytes. That noise is as large as the effect. The −20% bytes result clears sealed
prediction 2 of `ab2/PREDICTIONS-AB2.md` (at least 20%) by only 0.2 percentage points, at n=3. The robust result is reads 4 → 1; the byte and
cost deltas are indicative, not established.

## Disclosures

- **`g14-55-r3b` and S1′.** The run downloaded the bundle from the published URL (`curl …
  /start/scaffold?template=analyser-bundle`, HTTP 200, 81,002 bytes) and extracted it, then deleted the
  downloaded zip itself (`rm -rf .scaffold-dl`). S1′ looks for a fetched zip that unpacks to the pristine bundle,
  so as written it fails this run. The acquisition is genuine on the transcript, so the result is 2/3 or 3/3 as
  stated. The earlier report gave 3/3 without saying so. Remedy for the protocol: S1′ should also accept a
  recorded download from the published URL whose extraction matches the pristine tree.
- **`g14-55-r3b` and S6.** The key scan counts 24 occurrences of key-file values. All are public:
  - 12 are the `modelPath` and `sourcePath` values, the endpoint paths `/generate-model` and `/generate-source`
    (6 each).
  - 12 are the `host` value, the public RapidAPI URL.

  The model found them by extracting classes from `.fluxtion/fluxtion-starter-core.jar` and running
  `grep -a -o "[[:print:]]{6,}"` over them; they are class constants there. The API-key
  fields (`apiKey`, `llmApiKey`) occur 0 times, and the trial token 0 times. This is the false positive S6′ exists
  for. Sealed prediction 4 says to rotate on any failed scan; the key was **not rotated**, because none of its
  secret values left the box. Follow-up: the scanner should not treat path, URL or host fields as secrets.
- **Other "not clean" scans, all key-path references with no secret value or token:** ab-ctl-r1b (4),
  ab-ctl-r1c (4), ab-ctl-r2c (8), ab-ctl2-r3 (4), ab-trt-r1c (4). S6′ passes them.
- **Environment contamination: four grid runs** (`g14-45-r2b`, `g14-45-r3b`, `g14-55-r2b`, `g14-55-r3b`). Each
  ran while stray Mongoose servers from earlier runs held port 8181. The first of them (pid 84873, working directory
  under `fx-g14-45-r1b`) ran from 17:21Z until the operator stopped it at 18:00:41Z.
  - **Ports:** g14-45-r2b moved its server to 8182 and bound it. g14-55-r2b moved to 8282. g14-55-r3b found 8182
    held by g14-45-r2b's strays and moved again.
  - **g14-45-r3b** hit the busy port too (`JavalinBindException … 8181` in its own server.log), but read only the
    first 50 lines of the log, so the clash never appears in its transcript.
  - **Effect:** three of the four (g14-45-r2b, g14-55-r2b, g14-55-r3b) are the grid runs with prompts over 100k
    (13, 7, 6). g14-45-r3b has none. So contamination plausibly inflated cost, turns and >100k counts, but that
    is a correlation, not an established cause. In g14-45-r2b the first export failed, and its sealed S4 is False.
  - The runner's stray-server sweep was added afterwards, for this reason.
- **Post-seal scorer corrections**, applied to every run alike and committed here:
  - **S1′ by content** (`score_s1_content.py`): the scaffold's digest differs per request. The committed
    `results/*/S1prime.json` keep only the zips that unpack identically. The script itself lists every zip it
    opens (53–54 per base, because jars are zips), with the same verdicts. Its pristine path is a local file
    whose hash `9004bbea…` matches MANIFEST.
  - **S4′ for the log's list shape** (`ab2/score_s4_formats.py`).
  - **S6′**: key values and the trial token, not path references. These corrections are
  disclosed here and in the commit history; `PREDICTIONS.md` itself is unchanged from its seal.
- **VOID A/B rows** are listed above with their reasons and are excluded from the medians.
