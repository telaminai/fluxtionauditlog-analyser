# Starter verification — owner decision and implementation

Playground implementation: `feat/project-starter-journey@4b41631`. Analyser policy, tracker,
static-corpus CLI and CI changes accompany this report on its `feat/project-starter-journey` branch.

**The battery is stopped, not passed. No T3–T6, additional cohorts or completed-trial reruns.**
The dedicated battery proxy is stopped; original protocols, predictions, transcripts and projects are
preserved. No new client was used for this work. The next permitted initial-beta session is **one
public acquisition**, only after the public ZIP carries v2 and unattended preflight succeeds.

## Established claim and limits

“a fresh client, with no prior exposure and no coaching, acquired the supported v2 bundle and
extended it with a new event to correct running totals; results independently verified; zero
substantive interventions.”

Operationally, fresh/no prior exposure means fresh context, no supplied history and enforced exclusion
of source checkouts/previous trials; it says nothing about pretraining. Acquisition and extension used
two fresh contexts on the same cohort's project, not two independent complete journeys. They took
191.149 and 212.724 seconds; operator work is additional. All work used preview artefacts and provisioned
dependencies; extension used an externally provisioned generation credential. Ordinary build/run was
keyless. See the [retained R3 report](report_battery_r3_2026_09_20.md).

**No comparison with v1 is available:** its published control contains no numeric unrouted total.
No number of new trials can establish a reduction from that missing baseline. Do not reconstruct it,
repair old journals or reopen the comparison. The preview establishes no public acquisition or broader
evidence-integrity claim. T3–T6 are retired, not waiting for another preparation cycle.

## Delivered tiers

| Tier | Trigger | Executable check / result |
|---|---|---|
| Static | Every commit | Analyser `.github/workflows/starter-static.yml`: pinned six-project corpus, observer-clock regressions. `tools/check_coldstart_corpus.py --project PATH --baseline PRISTINE` fingerprints finished projects without a journal. |
| Static | Every commit | Playground `starter-static.yml`: fourteen-template command/profile matrix and preflight checker regressions. The ZIP generator checks all emitted text surfaces for project commands naming absent files, and refuses unsupported bare JVM launch advice for hosted/connector projects. |
| Preflight | Each successful production publication | Playground `bundle-preflight.yml`: customer-route download, v2-file check, empty Maven/wrapper caches, build, shipped launch, independent sample audit comparison, shipped stop, process exit/registry removal and unchanged originals. No client, key, provider or operator. |
| Spot-check | Routing-surface paths change | Playground `routing-spot-check.yml` requests one short acquisition-only report. Agent entry, catalogue, runbook/README emitters and entry routes are named paths. No automatic model call and no release-tag trigger. A successful request job is not a successful session. |

The publisher actually emits a successful `check_run` from `cloudflare-workers-and-pages`, named
`Workers Builds: fluxtion-web`, with a production URL; the repository currently has no deployment events.
The preflight listens to that observed mechanism, not a hypothetical deployment hook. It runs trusted
default-branch code against the public customer URL and archives the exact tested ZIP/hash and logs.
The workflows are implementation on the feature branch: **activation awaits merge to main**. No live
post-publication CI run is claimed. A local alternate URL verifies the harness only.

The preflight covers the supported keyless analyser bundle. It does not silently treat that bundle
as coverage for key-requiring catalogue entries; another supported bundle needs its own explicit
sample oracle/lifecycle contract. Full instructions are in playground `docs/starter-verification-tiers.md`.

## Regression closure

Standing rule, now in the spec and CLAUDE.md: **a finding is not closed until the static regression
check that would catch it next time exists.** Runtime boundaries also keep their executable assertion
and wrong-result witness. A successful trial is discovery evidence, not continuing protection.

| Finding | Cheap check |
|---|---|
| PPF-1, invented/bare launcher in runbook or POM | `command-contract.test.ts`: corrupt each surface; generator-boundary mutation injects a new guide and must refuse the ZIP. |
| PPF-2, disabled launcher still prescribed | Same matrix removes the emitted launcher and rejects dangling instructions; existing no-launcher tests remain. |
| New option case: analyser bundle with launcher disabled | Validation and generator refuse `analyserBundle` without `addRunScript`; ordinary projects retain explicit decline. The generic matrix found this without a client. |
| CS-2/3/4 scorer regressions | Eleven pinned corpus witnesses, including old erroneous outcomes and the genuine positive control. No new trial or rewritten journal. |
| R2 clock basis | `test_coldstart_clock.py`: suspend and UTC adjustment cases, run in static CI. Historical probe retained. |
| Preflight false stop timeout from unreaped direct child | `test_server_exit_is_reaped_while_caller_is_busy`: child exits and is reaped while parent can be blocked on its stop helper. |
| Empty/wrong/duplicate sample or missing downstream audit | Preflight negative fixtures reject each; the real check derives expected values from the downloaded CSV before running. |

Provider reliability and wider routing/distribution observations are not marked closed by these checks.
No evidence for dependency identity, dependency-class shadowing or null-mapper fixes was produced here.
The website cannot infer local key/provider availability; a capability-declaration design and legacy
coordinate distribution policy remain separate decisions, not asserted finished work.

## Verified and not verified

- Unattended preview preflight: **PASS, 33.035 s**, empty caches, five matched input/audit rows,
  clean stop, all 43 original files unchanged. No sink-output scenario claimed.
- Public preflight: **FAIL before build, 0.261 s**. Download succeeded, but PROJECT.md,
  runbooks/build.md and runbooks/hosting.md are absent. This is why no public client is scheduled.
- Playground: **541 tests pass, five local-artifact tests skipped** because this run did not supply
  the optional starter jar; production build passes. Six preflight regression tests pass.
- Analyser: eleven corpus witnesses pass; the finished R3 project has no trap candidates relative to
  its pristine download. That is a static result, not a new routing or behavioural measurement.
  Full `mvn -q test`: 1,718 tests, zero failures/errors, 40 display skips. After staging, the link and
  whitespace tests pass again. Two observer-clock tests pass; workflow YAML parses; rule-1 sweep and
  diff whitespace checks pass. No new display/UI witness is claimed.

The first local preflight attempts recorded environment DNS/HTTP transport failures. curl reached the
same public route successfully. The first full preview execution then exposed the harness child-reaping
bug: sample values matched but its stop helper timed out waiting for the parent's unreaped child. The
dedicated regression and corrected run above address that harness defect; no subject project was edited.
Raw attempts are retained locally under `.local-evidence/starter-verification-tiers-2026-09-20/`.
Public JSON results are in [the evidence directory](evidence/starter-verification-tiers-2026-09-20/README.md).

**Beta:** publish v2, pass public preflight, then one preregistered public acquisition. **Broader launch
claim:** remains gated by the owning evidence-integrity workstreams. Browser/restart requirements for
the wider analyser release remain separate; this decision removes the battery, not those UI checks.

The first analyser gate also caught trailing padding in the previously committed public sleep-log
excerpt. Its public formatting is normalised; the original raw log remains unchanged in the private
evidence archive. No timestamps, trial outcomes or sealed protocol files changed.
