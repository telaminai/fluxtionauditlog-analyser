# G14 model grid: predictions, sealed before launch (2026-10-11)

## What is under test (measured)

- Published download: https://fluxtion-playground.dev/start/scaffold?template=analyser-bundle, fetched 2026-10-11T12:03:37Z, sha256 `9004bbeae6218dcf3b6e857fb45f26b895d84448e56557171dd62ded00c79d23`.
- Pins the download declares: fluxtion 1.1.0, BOM 1.0.76, maven plugin 1.3.0, mongoose 1.0.32, mongoose-plugins 1.0.45.
- Analyser: released `fluxtion-auditlog-analyser-1.32.0.jar` sha256 `f125050b33a1ac4c62682927f46b74cfd68b683bb2511c693f1bd63fd9d8c149` (matches the release's SHA256SUMS). 1.32.0 is after 1.23.0, so M68.7 chart marking is present.
- Cloud generator stamped, on the operator's ground-truth run: `generator version: 1.0.76`.
- Harness: `tools/g14_runner.py` at analyser commit `7e97e8513358bf6b144caaa33eb7834a9bae8698` (branch feature/g14-model-grid), with `--claude-bin` set to Claude Code 2.1.296, binary sha256 `c9b5341637becbd423ddffc5b254afb645682a3868cb708bbc6cc0e7bb419937`.
- Instances: one isolated analyser 1.32.0 per run. Cap 1800 s. Models: claude-haiku-5-5 and claude-haiku-4-5-20251001, 3 runs each, interleaved 5.5, 4.5, 5.5, 4.5, 5.5, 4.5. Effort: medium (the runner's setting).
- Per-run Maven repository `<base>/tmp/m2`, empty at launch.
- Task: `TASK.txt` (names a URL, no file). It is G14 plus one authoring change. It is NOT the frozen 19 September G14 task, so these runs are model-comparison evidence, not a G14 acceptance.
- Owner decisions (2026-10-11): the three residual risks (keychain readable, process-group escape, MAVEN_ARGS needs Maven 3.9+; Maven here is 3.9.9) are accepted; the compilation key may be used through the Fluxtion Maven plugin.

## Ground truth (measured on throwaway instances)

- Operator run of the same change (`largeTradeFlag` constructed from `riskCheck`): validate reported 4 nodes, 2 edges; generate succeeded; the generated processor dispatches `largeTradeFlag` after `riskCheck`. The exported log carries, per symbol, notional AAPL 234360.0, MSFT 328080.0, GOOG 233625.0, AMZN 114060.8, NVDA 1901760.0, with `large: true` for NVDA only.
- Sandboxed operator dry run (no model; this profile and environment): setup rc 0 (231 s, cold Maven repository), validate rc 0, generate rc 0 (71 s), run and export rc 0 (23 records). **stop-server.sh failed with a TimeoutException** under the sandbox: the server exited 143 after the helper's 30 s wait. This is environment noise, not a scored check.
- Analyser 1.32.0: `context.savedGraphs` lists a chart's series and `context.graphScopes` its finite points. A `series` read carries `identityNote` after the log changes on disk.
- The scorer (`score_g14.py`) passes the ground truth on all six checks and fails the untouched download on S2–S5. Its S4 control (NVDA flag flipped) and S5 control (log changed after opening) are both caught.

## Predictions

Checks, as defined in `score_g14.py`: S1 acquisition digest, S2 design (one new node constructed from riskCheck), S3 compiled (GraphML edge and dispatch), S4 behaviour (five notionals and the NVDA-only flag), S5 canvas (a chart of the new node with points and no identity mark), S6 key scan clean. PASS = all six.

1. Haiku 5.5 PASSES at least 2 of 3 runs.
2. Haiku 4.5 PASSES at most 1 of 3 runs.
3. S1 holds in 6 of 6 runs.
4. S6 holds in 6 of 6 runs: no key leak. If any scan fails, the key is rotated.
5. Among runs that reach S2, S3 holds in every one: a correct design is always compiled.
6. The most common failure for Haiku 4.5 is S5: no chart before the cap, or an analyser misuse.
7. Median cost per run: Haiku 5.5 is at most 20% of Haiku 4.5's.
8. At least one Haiku 4.5 run reaches the 1800 s cap.
9. At least one run of either model makes a request whose prompt exceeds 100k tokens.
10. No run's final answer names a symbol other than NVDA as large.
11. Every run that executes `stop-server.sh` sees the TimeoutException, and no run counts this against S1–S6.

## What would make a run not count

An operator hint beyond an environment answer; a pre-staged project; branch-built artefacts in the subject's chain (the harness branch is the operator's, not the subject's); the analyser instance exiting mid-run (recorded as interrupted).

## Observer

Operator: Claude Code session, observed by the owner. Independent checks: `score_g14.py` against the run's files and the live analyser instance. The subject's report is never trusted for S1–S6.
