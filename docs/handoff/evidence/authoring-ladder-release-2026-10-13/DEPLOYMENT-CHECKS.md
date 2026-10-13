# Deployment checks: fully released stack (2026-10-13, before sealing)

Each check was run by the operator on 2026-10-13 between 20:26Z and 20:50Z. The evidence sits in this folder; where
a check is not a file, the command and its result are given here.

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Analyser 1.34.0 jar matches the release's checksum | **PASS** | `gh release download v1.34.0`. The jar's sha256 is `3f1ce0ced839…8988f`, the same as the `fluxtion-auditlog-analyser-1.34.0.jar` line in `SHA256SUMS.sha256`. Published 2026-10-13T20:13:32Z. |
| 2 | `python3 tools/bench/template-bench.py --live` passes | **PASS** | Run at the v1.34.0 tag (`0a1e4f7b`, which is analyser main) against the deployed catalogue, after 1.0.79 went live: exit 0, 34 PASS, 0 FAIL (`checks/template-bench-live.txt`). Each of the 14 catalogue templates' archive marks its root scripts executable, and each is installed executable by `TemplateArchive`. The bench builds from the tag's source, not from the released jar, so #3 repeats the install with the jar itself. |
| 3 | The downloaded fixture zips install through the **released 1.34.0 jar's** TemplateArchive with every root script executable | **PASS** | `harness/InstallCheck.java`, run as `java -cp fluxtion-auditlog-analyser-1.34.0.jar InstallCheck.java`, gives `checks/install-check.txt`: every root `*.sh` and `mvnw` is executable in all three. The bundle's set includes `run-and-export.sh`. The Spring starter has `run.sh`, `setup.sh`, `validate.sh`, `generate.sh` and `check-fluxtion-key.sh`. |
| 4 | Downloads carry starter-core/BOM **1.0.79** and runtime **1.1.1** | **PASS** | `fluxtion-authoring.json` `starterVersion` is 1.0.79, and the pom has `fluxtion.bom.version` 1.0.79 and `fluxtion.version` 1.1.1, in all three (FIXTURES.md). Public Repsy `maven-metadata.xml` gives `release` 1.0.79 for starter-core, bom and builder, and `fluxtion-starter-core-1.0.79-all.jar` returns HTTP 200 (4,308,149 bytes). |
| 5 | The new add-a-node skill ("Regeneration writes the class shell", placeholder ids) | **PASS (bundle) / not shipped (Spring starter)** | **Bundle:** `.claude/skills/add-a-node/SKILL.md` contains the sentence once. Its example uses the placeholder ids `myNode`, `parentA`, `lookup` and `out`. It is canonical `docs/skills/spring/add-a-node/SKILL.md` at `e78e50a6`, which is unchanged at `a6570f4e`, with the bundle substitutions filled in (regenerate command, script path). `audit-compare.py` is byte-identical to canonical. **Spring starter:** the scaffold ships no `.claude/skills` at all, as at baseline. The check as worded ("bundle + Spring starter") does not hold for the Spring starter; this is not a regression. |
| 6 | load-audit-log does NOT yet carry "Diagnose in a fresh context" (deliberate) | **as expected** | The bundle's `load-audit-log/SKILL.md` has 0 occurrences of it, while canonical `a6570f4e` has 2. |
| 7 | Capped scripts | **PASS** | In both templates, `setup.sh`, `validate.sh`, `generate.sh` and the bundle's run and export scripts send their full output to `logs/<step>.log` and print a summary. For example, `setup: ok; full output (1 lines): …/logs/setup.log` and `validate: ok; full output (2 lines): …/logs/validate.log`. |
| 8 | `run-and-export.sh` executable | **PASS** | The zip entry is `-rwxr-xr-x` and it installs executable (#3). |
| 9 | `java -jar .fluxtion/fluxtion-starter-core.jar withdraw --help` works after `./setup.sh` | **PASS** | Exit 0 on the as-shipped 1.0.79 bundle (`checks/withdraw-help.txt`): `Usage: withdraw [--project DIR] [--xml FILE] --class FQCN [--member KEY] [--still-declared]`. |
| 10 | A no-regenerate run exports a log carrying `annotation:` | **PASS** | On the as-shipped 1.0.79 bundle, `./setup.sh` then `./run-and-export.sh`, with no generate, produced `logs/audit-marketProcessor.yaml` with 10 `annotation:` keys, for example `rootNode: { …, method: onPriceEvent, annotation: OnEventHandler, … }`. That export is `r5/baseline/logs/audit-marketProcessor.yaml`. The shipped processor is stamped analyser 1.0.79 / target generator 1.0.79. |
| 11 | A keyed `./generate.sh` stamps hosted generator **1.0.79** | **PASS** | All 5 ground-truth regenerations on the 1.0.79 fixtures (r2 to r6) stamped `target generator version : 1.0.79`. An earlier keyed generation on the 1.0.78 bundle (the r6 design draft, 20:3xZ) stamped 1.0.79 as well. The key was used only through the shipped `generate.sh`, and its value was never read or printed. |
| 12 | The live playground serves starter-core 1.0.79 | **PASS** | `checks/live-poll.log`: not found at 20:35:24Z, then FOUND at 20:40:32Z, with the served bundle's `starterVersion` 1.0.79. |

**Not checked here:**
- the fluxtion-web PRs (#11 to #14) individually;
- the 1.0.79 pin PR, beyond the starter versions the live downloads now carry.

Their observable effects are the capped scripts (#7), the annotated shipped processor (#10), the vendored
add-a-node skill (#5) and the pins (#4).
