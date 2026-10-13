# G14 model grid and guidance A/B — results (2026-10-11/12)

The per-run evidence is under `results/<run>/`: `score.json`, `meta.json`, `keyscan.json`, `config.json`, and
`reads.json` for the A/B runs. The post-seal content checks are `S1prime.json` (acquisition by content) and
`S4prime.json` (the audit-log format check). Transcripts are not committed. Every number is computed from these
files. The collected files contain no key value and no trial token, which was scanned before committing.

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

**Result.** Haiku 5.5 is 2/3 PASS′ with S1′ as written, or 3/3 counting the transcript as evidence of acquisition
(see below). Haiku 4.5 is 0/3: every run fails S5, the chart. `g14-55-r1` is the void first launch, where
`dontAsk` denied every tool; it is excluded.

### A/B (prestaged; correctness = S2–S5 and no token, since S1 acquisition does not apply)

| run | arm | correct (S2–S5, no token) | generated-code reads | tool-result bytes | cost $ | turns |
|---|---|---|---|---|---|---|
| ab-ctl-r1 | — | VOID (no complete run: dontAsk denial / superseded relaunch) | | | | |
| ab-ctl-r1b | — | VOID (no complete run: dontAsk denial / superseded relaunch) | | | | |
| ab-ctl-r1c | v1 control | True | 4 | 103911 | 0.0509 | 37 |
| ab-ctl-r2c | v1 control | True | 5 | 79281 | 0.0533 | 50 |
| ab-ctl-r3c | v1 control | True | 4 | 104071 | 0.0457 | 40 |
| ab-ctl2-r1 | v2 control | True | 5 | 85379 | 0.0378 | 33 |
| ab-ctl2-r2 | v2 control | True | 3 | 75545 | 0.0368 | 34 |
| ab-ctl2-r3 | v2 control | True | 4 | 91981 | 0.0385 | 31 |
| ab-trt-r1 | — | VOID (no complete run: dontAsk denial / superseded relaunch) | | | | |
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
−21%, correctness 3/3 in both arms. The v2 treatment is the guidance shipped in fluxtion-web #8 and analyser
1.33.0.

## Disclosures

- **`g14-55-r3b` and S1′.** The run downloaded the bundle from the published URL
  (`curl … /start/scaffold?template=analyser-bundle`) and extracted it, then deleted the downloaded zip itself
  (`rm -rf .scaffold-dl`). S1′ looks for a fetched zip that unpacks to the pristine bundle, so as written it fails
  this run. The acquisition is genuine on the transcript, so the result is 2/3 or 3/3 as stated. The earlier
  report gave 3/3 without saying so. Remedy for the protocol: S1′ should also accept a recorded download from
  the published URL whose extraction matches the pristine tree.
- **`g14-55-r3b` and S6.** The key scan counts 24 "secret" occurrences. They are the key file's `modelPath` and
  `sourcePath` values, the public endpoint paths `/generate-model` and `/generate-source`, which the model found
  by running `strings` on the builder jar (they are class constants beside the public RapidAPI URL). No API key
  or token value occurs; the long secret-shaped values occur 0 times. This is the false positive S6′ exists for.
  Follow-up: the scanner should not treat path-only key-file fields as secrets.
- **Prompts over 100k.** Haiku 4.5 r2b sent 13; Haiku 5.5 r2b and r3b sent 7 and 6.
- **Post-seal scorer corrections**, applied to every run alike: S1′ by content (the scaffold's digest differs per
  request) and S4′ (the log's list shape), both disclosed in the PREDICTIONS addenda.
- **VOID A/B rows** are relaunches superseded by the `…c` and `…2` series, or runs with no complete transcript.
  They are excluded from the medians.
