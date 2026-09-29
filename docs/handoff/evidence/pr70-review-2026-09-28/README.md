# Portable PR #70 review evidence

Subject tested: `faf0fd2c`; full feature since `v1.27.0`.
See [the review](../../review_m70_evidence_bundle_replay_2026_09_28_codex.md).
These are diagnostic probes, not fixes or acceptance tests. The required findings remain open.

## Reproduce on another machine

Use JDK 21 and Python 3, in an isolated checkout containing this evidence. Build the analyser:

```sh
mvn -o -q package -DskipTests
python3 docs/handoff/evidence/pr70-review-2026-09-28/reproduce.py --out /path/to/new/DEMO-review-output
```

`--out` must not exist; the script never removes previous evidence. It resolves Java through `JAVA_HOME`, or PATH
if that variable is absent. `--runtime` and `--jar` override the default Maven-cache runtime and built analyser.
A cold dependency cache needs an ordinary dependency-resolving build first. No compiler profile, compiler key,
provider, display, participant project or personal analyser settings are used. All fixture data are DEMO data.

The script compiles committed generated DEMO code, the production runner, and two probes. It creates synthetic
bundles and invokes the real CLI/runner. The memory probe runs in a separate **64 MiB child JVM**: it intentionally
exercises rejection of many compressed members. Do not remove that heap limit to try the same attack against a
large host process. The only mutation is compiled from a **scratch copy** of ReplayCapture; repository source and
compiled classes are untouched. Generated archives/classes and raw working files stay in the new output folder.

`results.json` captures each subprocess's exit/stdout/stderr, replacing local paths with placeholders. It records
observations rather than treating the old defects as the expected behaviour of a fixed build. Script exit zero
alone is not a passing gate. Review every result; compilation errors, unexpected exceptions and timeouts are not
regression witnesses. After fixing a finding, the corresponding old wrong result should disappear.

## Recorded observations and required corrected behaviour

| Probe | Reviewed implementation | Corrected expectation |
|---|---|---|
| normal-runner / normal-compare | 7 inputs, 8 audit records; AGREES 8/8 | unchanged positive control |
| nested-comment-compare | exit 0, AGREES 8/8 despite a changed nested thread value | DIVERGES, naming the nested value |
| pretty-verify / pretty-runner | verifier accepts; runner says graph member is unlisted | both accept the JSON representation |
| payload-runner | 7 audit records from 7 inputs | 8 audit records; business text must not suppress one |
| payload-direct-capture | all eight external inputs recorded; nine audit records | unchanged positive control; this probe adds one external breach to the seven-input script, so its log has one extra record |
| missing-separator | exit 0, 6 inputs and 6 audit records | named refusal before executing the processor or writing output |
| duplicate-fields-and-pairing | duplicate event/time block reads as one event; changed bid still pairs | duplicate block refused; keep pairing's limits explicit if its type/time policy is retained |
| aggregate-memory | OutOfMemoryError before multiple-replay refusal | bounded, named refusal, no output file |
| live-recorder | 8 external inputs captured, one breach, 9 audit records; receipt times match | unchanged; promote this into a regression |
| live-recorder-identity-mutant | 9 inputs captured, two breaches; AssertionError naming external-only recording | the promoted regression must catch this mutation |

`LiveCapture.java` drives the committed generated processor via `onEvent`, not a manually invoked auditor.
Its clock ticks per read. It sends the seven input fixture events and one externally supplied RiskBreachEvent,
while the graph itself also raises a RiskBreachEvent. `Probe.java` drives the production reader/pairing helpers.

Separately, on `2f71c970`, removing the production recorder's identity guard left all **47 / 0 / 0 / 0**
`Replay*Test` tests green. The original source was restored from a byte copy and SHA-256 checked; the restored
run was also **47 / 0 / 0 / 0**. This distinguishes the new live probe from the ineffective existing protection.
No fixture regeneration was performed. The recorder and affected production runner/comparator source blobs are
unchanged between that commit and `faf0fd2c`.

`reviewer-results.json` records the review's final-head gate counts. `portable-probe-results.json` is the output
from rerunning this published script, not reconstructed expected output. Original machine-specific logs remain
local; the published files deliberately omit them and their paths.
