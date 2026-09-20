# Project-starter journey release — 2026-09-21

Status: IN PROGRESS. Owner requested execution; no release completion is claimed here.

Target: analyser 1.16.0 and deployed v2 starter journey. Public local-tool publication precedes
playground deployment. The beta claim is limited to onboarding and the evidence actually checked;
dependency certification and broader provenance work remain open. No v1 comparative claim.

## Verified so far

- Reviewed analyser feature integrated with main on a separate branch; no conflicts or force push.
  User IDE edits stay in the original checkout.
- Full Java suite: 1,718 tests, zero failures/errors, 40 display skips. Strict docs pass. The new
  process-restart witness is included in the display-enabled CI loop job.
- 41 display cases pass. Built-jar M46, M48 and M64 probes pass. M46's obsolete automatic-startup
  expectation was replaced by the accepted explicit-restore rule, not by a product workaround.
- Three actual JVMs prove normal quit/persistence, offer-only startup, explicit recovery of log,
  topology, design, diagnostics, selection and focus; CLI input isolation and dismiss also pass.
  `tools/verify-session-restart.py` is the repeatable check. Its initial selection assertion expected
  integers while the API returns record summaries; the probe now compares the captured summaries
  and independently asserts topology recordIndex. No application source changed for this check.
- All five capture-conversations scenarios complete. Transcript unchanged; regenerated images restored.
- Personal author address verified; accepted historical employer-address counts remain unchanged.

## Remaining gates

Public artifact parity; playground publication and unattended public-download preflight; one public
acquisition-only fresh client; witnessed browser preview; final analyser main CI/release/assets/docs.
No browser is connected at this checkpoint. No battery or T3–T6 will be started.

## Frozen public acquisition spot-check

One fresh client, empty directory, public website only, supported catalogue id `analyser-bundle`.
No compilation key, preview proxy, source checkout, prior session or operator hints. Use the existing
observer-clock/event recorder and first-refusal stop. No journal. Missing requested source pointers
remain unattributed in the denominator. Cap: ten minutes; one attempt; no retry to tidy results.

Prompt: acquire the Audit analyser bundle from `https://fluxtion-playground.dev`, retain its shipped
coordinates, build and run it, show the processing output and stop it cleanly using what the project
provides. Environment supplies Java 21, Python, curl, unzip and isolated homes/cache/registry. Before
each tool batch request a short source pointer, never reasoning or timestamps. Exact delivered prompt
and model selection will be archived before launch. Timing is a warm-cache trial if preflight has
provisioned the isolated public dependencies; it is not an acquisition benchmark.

Predictions, frozen before launch: completes without coaching or provider refusal; follows shipped
launch/stop scripts, no handwritten main; five sample input events have matching audit values;
all original source/configuration files remain unchanged. Expected observed duration 240 seconds,
cap 600. At least 75% of tool batches have an attributable source pointer; omission is not success.
Wrong predictions will be reported, not repaired. Independent acceptance compares exported rows with
the downloaded input fixture and checks stopped process/registry and project-file integrity.

This establishes an absolute public-route result only, never improvement over the unavailable v1
numeric routing baseline. The frozen preview trials are not reclassified as public acquisition.
