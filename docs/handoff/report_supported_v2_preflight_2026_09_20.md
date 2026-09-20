# One supported v2 preview project — operator preflight

**The unchanged Audit analyser bundle builds, processes its five input rows, exports matching audit
values and stops cleanly without a key. The new runbooks contain a reproduced launch defect.**
This is operator verification, not a fresh-client trial or Spring regeneration acceptance.

The owner requested one supported preview project after the unsuccessful cold-start rehearsal.
No new model sessions were started. Playground `47b9952c103f04b2403e780389543fbadf227e30`
served `/start/scaffold?template=analyser-bundle&group=com.acme&artifact=preview-preflight&basePackage=com.acme.preview`.
The catalogue and README explicitly promise keyless ordinary build/run by shipping a generated processor.
The ZIP SHA-256 is `5e7b11553ad9c1eec5cde26b48879b9a1d3b6b08694d0bcf99e2e088f35a2fe5`.

## What ran

| Check | Observed result |
|---|---|
| Fresh extraction | 43 files, including PROJECT.md, task runbooks, agent guides, profile, generated processor and GraphML |
| `./mvnw -B package` | Exit 0; one supplied graph test passes; 36.53 seconds including wrapper startup |
| `./run-server.sh` | Registers the declared processor and serves the local admin endpoint |
| `./export-audit.sh` | Exit 0; writes the declared YAML audit file |
| Independent comparison with shipped CSV | Exactly five PriceEvent records; symbol, logged price and volume match input order |
| `./stop-server.sh` | Exit 0; process stops and registry entry disappears |
| `java -jar target/preview-preflight-1.0.0-SNAPSHOT.jar` | Exit 1 with IllegalAccessError, as README warns |
| Download integrity after the checks | All 43 original files remain byte-identical to the pristine extraction |

JDK: Corretto 21.0.11. Maven dependency cache, wrapper cache, Java home, temporary files and Mongoose
registry were isolated under the evidence directory. Dependencies resolved from the POM's public
repositories; no private provider, local artifact installation, inherited API-key property or user Maven
cache was supplied. This operator process was not a source-blind LLM sandbox. The build uses the
shipped processor; it does not exercise generation or the unpublished starter coordinate.

Expected runtime rows were written before starting the server, from the input CSV. No sink output was
expected: the shipped nodes do not publish to the configured sink. Its output file is empty. This
checks event processing and audit fidelity, not a meaningful risk algorithm or a sink scenario.
The runtime emitted a low-disk warning; no write failure was observed.

## PPF-1 — new runbooks choose an unsupported launcher

**Required before treating this download's runbooks as ready for an unassisted hosted journey.**
Owner: playground project-support emitter. Source at the recorded head:
`web/src/lib/starter/project-support.ts:96` selects `run.sh` if present, otherwise plain `java -jar`.
It does not select the emitted `run-server.sh`. Both `runbooks/build.md:7` and
`runbooks/hosting.md:7` therefore prescribe a command the README explicitly prohibits.

Reproduction: download the above ZIP, build with Java 21, then run the command in build.md.
It exits 1 because `org.agrona.UnsafeApi` cannot access `jdk.internal.misc.Unsafe`.
Run `./run-server.sh` instead: the same jar boots and processes all five rows. This is a real
instruction defect, not a missing key or dependency. No emitter or downloaded project was fixed here.

Acceptance for the correction: choose the actual emitted launcher for this project; require both
runbooks to agree with it and execute that documented command on a fresh bundle. Include hosted
templates with and without an emitted launcher, so the fallback is not silently assumed safe.

## Preserved-corpus re-score

The independent scorer repair `18b47a7` was already committed on main; it is now pushed to origin/main.
The feature checkout was not switched or merged. All six preserved trials were re-scored with that
exact script, using the original baseline for trial 5 only. The original scores remain unchanged.

| Trial | Parsed entries, old → new | Routing, old → new | Fingerprint change |
|---|---|---|---|
| 1 | 2 → 2 | 1:1 → 1:1 | None |
| 2 | 0 → 0 | 0:0 → 0:0 | None; empty journal still not a pass |
| 3 | 13 → 13 | 4:7 → 4:7 | Literal harness now detected |
| 4 | 14 → 21 | 10:3 → 15:5 | First source remains operator instead of being overwritten |
| 5 | 17 → 17 | 11:5 → 11:5 | Four false collection hits removed; an additional main candidate appears |
| 6 | 16 → 16 | 5:11 → 5:11 | Literal harness detected; seven collection hits retained |

The published corpus now supports a repeatable regression check, without models or private archives:

```sh
python3 tools/check_coldstart_corpus.py --scorer-revision 18b47a7
python3 tools/check_coldstart_corpus.py --scorer-revision 8c488c8
```

New: 11 checks pass. Old: five fail, independently witnessing CS-2, CS-3 and CS-4. The check uses
public source excerpts for the named witnesses; it does not pretend those excerpts replace the full
private projects and pristine baseline for acceptance scoring.

**One precision limitation remains:** the new T-MAIN rule matches any main containing `.onEvent(`.
It now also flags trial 5's `FluxtionMain.java`, which reads `data/market-events.csv` through
`EventFileReader.read(feed)`. That is a file-backed custom driver, not the literal-event harness in
trials 3 and 6. The additional candidate must be classified manually against the intended criterion;
do not count it automatically as another literal-event trap. Its legacy-coordinate drift is a separate,
already verified finding. Trial 5 did not finish its application build; only trial 3 completed normally,
while trial 6 produced output/tests before a provider refusal.

Repairing the parser does not fix retrospective entries, invented timings, provider compatibility or
target identity. A tool transcript shows what was opened and when; it does not by itself establish
which source caused a decision. Keep source pointers as declared attribution, checked against observed
actions, rather than relabelling inference as ground truth. No new adoption ratio is claimed.

## Evidence and next gate

[Evidence manifest](evidence/supported-v2-preflight-2026-09-20/manifest.json) records original and public
hashes. Beside it are the build/runtime/export/stop logs, failed plain launch, predictions, checked rows,
downloaded runbooks and all six corrected scores. Public exports replace rule-1 terms if present,
normalize the checkout path and trim trailing whitespace. Originals, the exact ZIP, pristine extraction,
working project, isolated dependencies and operator runner remain locally under
`.local-evidence/coldstart-v2-2026-09-20/preflight-supported/`; corrected raw scores are under the
sibling `rescore-18b47a7/`. These local archives are git-ignored and survive reboot.

The sample server and preview are stopped. Analyser UI/MCP, browser acquisition, changed-graph
generation and a fresh-client run were not exercised. The successful preflight establishes a supported
keyless **ordinary bundle** path. It does not provision the generation prerequisite for later authoring
tasks. Before another battery: correct PPF-1, validate the observable-action instrument/client pairing,
and record the generation route required by the selected tasks. Keep the target-coordinate check explicit.

Repository gate: `mvn -q test`, 1,718 tests, zero failures/errors, 40 display skips. No product code changed.
