# Integration review evidence

The original review subject is 5776e750. Fix 9283c402 applies there; 764b4522 carries identical product code, tests and gate controls on integration tip 1df151df, preserving its released changelog sections. No other session's branch was edited.

JSON files retain counts, named assertions and restoration results. Maven command output and temporary paths are omitted or redacted. `full-gate.json` is the 155-control run BEFORE the fix; `fix-controls.json` is the six NEW controls on 9283c402. `final-headless.json` and `final-display.json` are the final PR tree, 764b4522. The full 161-control set was not rerun. Predictions were frozen before trials; report corrections and harness mistakes are in the review.

Use JDK 21 (`JAVA_HOME`) and an isolated worktree. Display runs must be sequential. No key, provider or client is needed.

## Reproduce the new controls

```sh
python3 tools/verify_project_chart_review.py --mode mutations --engine fast \
  --case integration-failed-identity \
  --case integration-failed-session \
  --case integration-coverage-bound \
  --case integration-pending-refresh \
  --case integration-failure-refresh \
  --case integration-repeat-failure-skip \
  --output /tmp/integration-controls.json
```

The shared gate requires a green baseline, a named failure, byte-identical source/classes restoration and a green rerun. The two frame methods manually invoke the real EDT Follow poll, with its timer stopped; they do not assert after an arbitrary sleep or use a socket verb as a replacement for the poll.

## Parent controls and independent probes

Run `parent_controls.py <scratch-output-directory>` from a disposable worktree at 5776e750. It substitutes parent methods or the corresponding policy fragment, preserving current signatures where necessary. Each case lists its exact test selection. `SOURCE_REPO` may name another local clone when the disposable tree was created with `git archive`. Byte backups, cmp and SHA-256 restoration are enforced. These are not full parent builds.

`check_changelog.py` independently checks all four parents' Unreleased content against 5776e750. Its two parent-only controls run on temporary copies and must fail the inclusion assertion before byte-identical restoration.

`IntegrationProbe.java` prints constructed-file observations and exercises CoverageService through a sentinel store that rejects reads beyond the captured bound. Compile it against `target/classes` plus the Maven test dependency classpath; run from the checkout root. `parent_probe.py` needs `target/gate/test-classpath.txt` (created by the fast gate), `JAVA_HOME`, and optionally `REVIEW_ROOT` and `PROBE_OUTPUT`. It substitutes the parent HeapLogStore and RecordFramer classes while retaining the remaining integrated classes. Its output demonstrates a pre-existing Unicode pending-text issue, so that observation was not included in the integration fixes.
