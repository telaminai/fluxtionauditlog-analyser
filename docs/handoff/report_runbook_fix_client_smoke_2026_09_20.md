# PPF-1 correction and one client smoke

**PPF-1 is fixed and verified on a fresh download. One fresh client completed the provisioned-project
smoke in 92.421 seconds, without a provider refusal or operator intervention. No battery was run.**

Playground fix: `feat/project-starter-journey@a4b4dde`, pushed. The analyser implementation is unchanged.
This answers [the supported-project preflight](report_supported_v2_preflight_2026_09_20.md) and the
owner's request to correct the runbooks before testing one client.

## What changed and how it was checked

- Task runbooks choose an actually emitted `run-server.sh` or `run.sh`.
- If neither is present, they list the emitted files and explicitly prescribe no run command.
  There is no inferred `java -jar` fallback. The script-disabled Mongoose README also stops naming an
  absent script and points to its supplied IDE run configuration or regeneration with scripts enabled.
- Standalone POM comments direct readers to `run.sh`, which supplies the selected host's JVM flags.
  This also closes the contradictory POM comment identified in the participant's follow-up.

Final tests cover all hosted catalogue entries, all standalone POM-launch comments, explicit script
opt-out and an unrecognised emitted file set. Replacing only project-support.ts with its old version
made eight of the final 36 focused tests fail; restoring it passed all 36. The POM comment cases were
also seen red separately. Full playground gate: **520/520**, no skips; production build passes.
Type check still reports the four existing SplitPane errors and six warnings, with no new error.

A new HTTP scaffold download, SHA-256
`5a354b62c68e1ed6e45e15299bded5df7db21c596bfc56c334338774e020bd20`, supplied both a pristine baseline
and a separate operator working copy. The commands in its corrected runbook built and ran the bundle,
exported five matching input rows, and stopped it with registry removal. All 43 original files remained
byte-identical. This verification completed before the client started. Dependencies were reused from
the earlier empty-cache public-resolution preflight; no compilation key or private provider was supplied.

## Smoke configuration, frozen predictions and outcome

The fresh client received another untouched extraction, with no build output or previous audit. Its
task was explicitly three steps: inspect and write expected values; build/run/export/compare; stop and
report. It was instructed to retain the shipped coordinates and files. This is deliberately a
**provisioned-project smoke**, not the cold-start battery's empty-directory acquisition task.

The exact [prompt](evidence/runbook-fix-client-smoke-2026-09-20/smoke-input.txt),
[predictions](evidence/runbook-fix-client-smoke-2026-09-20/smoke-predictions.json),
[observed events](evidence/runbook-fix-client-smoke-2026-09-20/smoke-events.jsonl) and
[independent checks](evidence/runbook-fix-client-smoke-2026-09-20/client-verification.json) are retained.

| Frozen prediction | Observed outcome |
|---|---|
| Client completes normally | Exit 0; provider result is_error=false; observed model claude-sonnet-5 |
| Under 300 seconds | 92.421 seconds, measured by the operator |
| Zero substantive interventions | Zero; no messages were sent after the initial task |
| Build succeeds | `run-server.sh` built the pristine project through its wrapper, then started it |
| Five input rows match export | Client's written comparison and independent operator numeric comparison both match |
| Clean stop | Supplied helper reports exit/removal; registry absent; operator confirms no listener remains |
| No original files changed | All 43 match the pristine baseline, including POM and configuration |
| Three pointers precede the steps | Observed at 2.540 s, 22.400 s and 73.545 s |

No frozen prediction missed. This small success does not estimate a provider failure rate or establish
why the earlier instrument failed. It establishes that this client/configuration can complete this task.

EXPECTED.json was written at 19.236 s, before the first launch command at 22.976 s. The client read
the shipped input and node audit fields before writing it. Its comparison is a written table, not a
new executable regression test; the operator independently parsed the exported PriceEvent rows and
checked symbol, price, volume and order against the CSV. The configured sink remains empty because
the unchanged nodes publish no sink output; neither subject nor operator claimed a sink scenario passed.

## Friction and reporting accuracy

The client recovered without help from three environment mistakes:

1. Redirecting startup output to `/tmp` was denied; it changed to the supplied `$TMPDIR`.
2. Looking under shell `~/.mongoose/servers` found nothing; the registry was under the declared override.
3. `ps` was denied after a successful stop; it verified the removed registry instead. The operator
   separately checked that the application port was no longer listening.

The final sentence, **“No failures encountered”**, overstates the transcript. The application workflow
succeeded, but those failed commands occurred and remain in the evidence. Do not use the final prose
as a substitute for command outcomes. Low-disk warnings also appeared; no write failure was observed.

This smoke uses a scoped recording protocol: short, visible source pointers, without a journal or
subject-authored timestamps. Timing comes from tool-event receipt and process duration. Pointers were
`task`, `PROJECT.md`, `task`; they are declared attribution, not proof of causal influence or an adoption
ratio. The operator observed the reads and checked their sequence. HARNESS.md and the battery prompts
were not rewritten, and the old instrument's results were not rescored as this protocol.

OS probes denied reads of analyser, playground, compiler and staged-answer files while allowing the
project, Java/Maven and scratch writes. Java home, Maven/wrapper cache and registry were isolated.
The CLI had no persisted session or MCP tools, used only project/local settings, and received no prior
conversation. Its existing authenticated client subscription was used; no generation service key was used.

## What remains before a battery

The keyless ordinary bundle route and this one client smoke are now verified. Graph changes still
require a supported generation route. This run did not exercise Spring reconciliation, browser/UI/MCP,
public acquisition, another model level or the held-out tasks. Publication gates remain open.

The participant's scaffold-time capability gate is recorded as a proposal, not silently implemented:
the website cannot inspect a local key or installed provider. A future explicit capability declaration
could refuse an incompatible choice and offer a keyless template, without uploading a secret or blocking
legitimate downloads intended for another environment. Correct backend selection must also remain valid
for local providers and custom endpoints. That contract needs specifying before changing the endpoint.

For the next battery, freeze the action-recording protocol, require the intended starter/coordinates as
an explicit target, and establish the generation prerequisite for the actual tasks. Older dependencies
are not to be sabotaged: switching to them can solve another task, but does not validate the selected v2
starter. Trial 5's documented key constraint remains a reasonable reason for its migration, not proof
that it ignored guidance. No wider routing change is adopted from this one task.

## Evidence preservation and gates

The [manifest](evidence/runbook-fix-client-smoke-2026-09-20/manifest.json) pins the ZIP and all exported
artifacts. The event projection includes visible text/tool results with operator receive times and raw
line references; private thinking/signature blocks are omitted. Exported text applies rule-1 redactions
where needed and trims trailing whitespace. Unedited transcript, project, caches, pristine ZIP and
operator runners are durable and git-ignored under
`.local-evidence/coldstart-v2-2026-09-20/runbook-fix-smoke/`.

Both sample servers and the preview are stopped. Analyser gate: `mvn -q test`, 1,718 tests, no failures
or errors, 40 display skips. The first restricted run failed to bind test sockets; it passed when rerun
with the required permission. No analyser execution logic, client settings or user IDE edits changed.
