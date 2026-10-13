# Authoring ladder — results (2026-10-12)

The per-run evidence is under `results/<run>/`: `score.json`, `meta.json`, `keyscan.json` and `config.json`. All
of it is produced by `score_ladder.py` and the G14 runner. The transcripts (`raw.jsonl`) are not committed:
they are large and carry machine paths. Every number below is computed from `results/*/score.json`, not from a
summary. Home-directory paths are redacted to `~`. The collected files contain no key value and no trial token,
which was scanned before committing.

PASS = ORACLE (an independent check of behaviour) + VALID (`./validate.sh`) + CHART (a saved analyser graph with
finite points and no identity mark) + S6′ (no key value, no trial token).

| run | rung | model | PASS | ORACLE | VALID | CHART | S6′ | cost $ | turns | wall s | max prompt tokens | prompts >100k |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| lad-r2-h55-1 | 2 | claude-haiku-5-5 | True | True | True | True | True | 0.271 | 77 | 488 | 137141 | 22 |
| lad-r2-h55-2 | 2 | claude-haiku-5-5 | True | True | True | True | True | 0.175 | 59 | 376 | 119062 | 14 |
| lad-r2-h55-3 | 2 | claude-haiku-5-5 | True | True | True | True | True | 0.206 | 66 | 462 | 122738 | 17 |
| lad-r2-s5-1 | 2 | claude-sonnet-5 | True | True | True | True | True | 3.524 | 128 | 1016 | 160120 | 54 |
| lad-r3-h55-1 | 3 | claude-haiku-5-5 | True | True | True | True | True | 0.370 | 60 | 652 | 173218 | 25 |
| lad-r3-h55-2 | 3 | claude-haiku-5-5 | True | True | True | True | True | 0.213 | 53 | 293 | 135526 | 18 |
| lad-r3-h55-2-VOID-repsy502 | - | - | VOID (no score: Repsy 502) |  |  |  |  |  |  |  |  |  |
| lad-r3-h55-3 | 3 | claude-haiku-5-5 | True | True | True | True | True | 0.161 | 51 | 254 | 126867 | 12 |
| lad-r3-s5-1 | 3 | claude-sonnet-5 | False | False | True | True | True | 4.410 | 138 | 1231 | 209625 | 74 |
| lad-r3-s5-1-VOID-repsy502 | 3 | claude-sonnet-5 | False | False | False | False | True | 1.002 | 34 | 559 | 76276 | 0 |
| lad-r4-h55-1 | 4 | claude-haiku-5-5 | True | True | True | True | True | 0.146 | 48 | 286 | 108663 | 12 |
| lad-r4-h55-2 | 4 | claude-haiku-5-5 | True | True | True | True | True | 0.210 | 52 | 277 | 128538 | 18 |
| lad-r4-h55-3 | 4 | claude-haiku-5-5 | True | True | True | True | True | 0.144 | 58 | 253 | 116997 | 9 |
| lad-r4-s5-1 | 4 | claude-sonnet-5 | True | True | True | True | True | 2.409 | 93 | 608 | 151474 | 37 |
| lad-r5-h55-1 | 5 | claude-haiku-5-5 | False | False | True | False | True | 0.099 | 49 | 412 | 105561 | 3 |
| lad-r5-h55-2 | 5 | claude-haiku-5-5 | True | True | True | True | True | 0.111 | 48 | 309 | 109498 | 7 |
| lad-r5-h55-3 | 5 | claude-haiku-5-5 | True | True | True | True | True | 0.233 | 70 | 354 | 130448 | 20 |
| lad-r5-s5-1 | 5 | claude-sonnet-5 | True | True | True | True | True | 3.326 | 135 | 941 | 190887 | 54 |

- **claude-haiku-5-5**: 11/12 PASS; total $2.34; median $0.190/run; median wall 332 s
- **claude-sonnet-5**: 3/4 PASS; total $13.67; median $3.425/run; median wall 978 s

The rung-5 Haiku run 1 failure is the starter refusing supplier node classes. That is fixed in compiler 1.0.77
(fluxtion-compiler #100); a plain `./generate.sh` on the rung-5 project passes on released artefacts (2026-10-13).
The Sonnet 5 rung-3 failure is the counter running on every trade; its final answer claimed success.

## Disclosures

- **Small samples.** Haiku 5.5 has 3 runs per rung; Sonnet 5 has 1.
- **Prompts over 100k tokens are common.** Every scored run sent some: 3–25 per Haiku 5.5 run, with maximum
  prompts of 105k–173k. Haiku 5.5 bills 5× per token above 100k, so part of each run's cost is above the
  threshold. The costs above are the CLI's own billed totals and already include this. The earlier report's
  statement that almost all requests stayed under the threshold was wrong.
- **VOID runs.** `lad-r3-h55-2-VOID-repsy502` and `lad-r3-s5-1-VOID-repsy502` hit the Repsy 502 outage. They
  are kept as evidence and excluded from the totals, and their rungs were re-run.
- **Post-seal scorer corrections**, applied to every run alike: the rung-4 oracle accepts a class with accessors
  as well as a record (see the PREDICTIONS addenda), and S6′ counts key values and the trial token, not
  key-path references.
- **`lad-r5-h55-3` key scan reads "not clean".** It has 4 key-*path* references and 0 key-value or token
  occurrences in 1,757 files. The model ran the bundle's own masked `check-fluxtion-key.sh` and `ls -la` on the
  key file; it never read the file. The runner's "KEY LEAK … rotate the key" message is over-cautious for this
  case. S6′ passes, and no key material left the box.
- **Warm cache.** Runs use a provenance-checked warm Maven cache (WARM-CACHE.md), so setup and network time are
  excluded.
