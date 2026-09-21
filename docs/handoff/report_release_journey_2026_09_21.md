# Project-starter journey release — 2026-09-21

Status: RELEASED — analyser **1.16.0**, compiler/starter **1.0.72**, public v2 playground.

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

## Publication and witnessed checks

- Compiler/starter **1.0.72 published**: release workflow 35544048421, tag `v1.0.72`
  (`f7b23476`). Full reactor CI: 4,092 tests, zero failures/errors, nine skips. Public BOM and
  starter resolution and comment-resource parity pass without local substitution.
- Playground **d1f7dcc deployed**: 546 tests pass with the published starter; production build and
  deployment pass. Browser-builder mirror is `7a01945`; public reference docs are `3de39f5`.
- [Public unattended preflight](https://github.com/telaminai/fluxtion-web/actions/runs/35544485872)
  passes against the customer scaffold route with an empty Maven cache and no generation key:
  five independently checked sample rows, clean stop, all 43 originals unchanged. Its 31.561 seconds
  is CI preflight duration, not a client acquisition measurement.
  [Machine result](evidence/release-journey-2026-09-21/public-preflight.json).
- Owner confirms Railway deployed. An independent released-client remote generation succeeds;
  generated source and descriptor both identify **1.0.72**, with the expected diagnostic sidecar.
  This proves one deployed generation, not a new customer's key-provisioning journey.
- **Public browser preview witnessed by the owner:** “Both nodes and arrow visible, no error.”
  [Screenshot](evidence/release-journey-2026-09-21/public-spring-preview.png) shows `rootNode`, `child`
  and the dependency arrow from child to root. Visible strings inspected: generic demo names only.
  This verifies initial rendering, not every browser interaction or responsive width.
- [Process restart result](evidence/release-journey-2026-09-21/process-restart.json), three JVMs.
  Analyser main CI 35543897332, docs 35543897301 and static checks 35543897443 pass.

## Release completion

No required release action remains. The acquisition limitations below remain explicit; no battery
or T3–T6 was started. Broader provenance and routing improvements are separate work.

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

## Sealed public acquisition result

**INCONCLUSIVE — environment failure; gate not passed.** The requested Sonnet client resolved to
`claude-sonnet-5`. It stopped on its first terminal provider error at 436.650 seconds:
“Failed to authenticate. API Error: 401 OAuth access token has been revoked.” The first 401 retry
was observed at 434.480 seconds. The runner stops on the terminal API error, not transient retry
notices; this distinction is recorded rather than calling the later timestamp the first 401.
No provider reasoning-policy refusal occurred. No operator intervention or second attempt occurred.

The subject did not acquire a ZIP or begin a build. It probed guessed catalogue routes, scraped
public JavaScript and searched for a browser. That discovery difficulty is observable before the
authentication failure, but this interrupted trial cannot establish an eventual product failure.
The untouched public-bundle preflight proves the artifact works through its exact URL; it does not
prove that this client can discover that URL from the website root. Keep those claims separate.

[Scoresheet](evidence/release-journey-2026-09-21/public-acquisition.json). Private raw transcript,
visible tool events, exact prompt, pristine download and subject project are preserved under the
ignored local-evidence public-acquisition directory; hashes bind the public scoresheet to them.
Raw provider records are deliberately not published. Clock drift was 0.0063 seconds with negligible
suspend time. Actual Maven cache was empty; no warm-cache acquisition time is claimed.

| Frozen prediction | Result |
| --- | --- |
| Completes uncoached, without provider refusal | Completion missed: revoked credential stopped the client; zero coaching, no reasoning refusal. |
| Shipped launcher/stop, no handwritten main | Not reached; no application acquired. Absence of a handwritten main is not a workflow pass. |
| Five independently matching sample events | Not reached by client. Automated preflight passed separately. |
| All original project files unchanged | Not assessable: no project downloaded. |
| 240 seconds to completion, cap 600 | Did not complete. Terminal error at 436.650 s, 196.650 s beyond predicted completion; not a completion-time measurement. |
| At least 75% attributable tool batches | Missed: 64 assistant tool-use messages, one `from: task`, 63 missing pointers. Zero shipped-artifact pointers. |

Batch accounting treats each assistant tool-use message as an observable batch, taking the last
explicit source pointer since the preceding batch. Missing pointers stay in the denominator.
The lone task pointer names the prompt, not a shipped document. The client omitted the requested
channel; this does not justify reconstructing its motives from file reads or claiming a routing ratio
improvement. No v1 numeric comparison is available.

**Next boundary:** restore client authentication before any separately authorised future attempt.
The current run stays sealed. A fresh public acquisition result or explicit owner disposition is
needed to remove the held release gate; the screenshot and automated preflight do not replace it.

## Owner-authorised retry

The owner requested “retry analyser we want to release”. A new isolated empty project/cache and
fresh Sonnet process reused the exact prompt and frozen predictions. The original run was untouched.
The client failed at 0.723 seconds, before any tool call, with “OAuth session expired and could not
be refreshed”. Zero builds, zero interventions, no acquisition result and no model tokens consumed.
[Retry record](evidence/release-journey-2026-09-21/public-acquisition-retry1.json).
The predictions are untested in this attempt; login failure is not a product verdict. No further
retry is useful until credentials recover. Existing analyser main CI at `b9064dc3` is green.

**Owner disposition:** wait for login restoration. Release remains held; no gate is waived.

Owner subsequently confirmed login restored; a separately archived fresh attempt is authorised.

## Restored-login attempt — acquisition gate met with limits

[Independent result](evidence/release-journey-2026-09-21/public-acquisition-retry2.json).
A fresh Sonnet process acquired the public bundle, built it, ran the shipped launcher, exported
its audit with the shipped helper and stopped with the shipped stop helper. No source checkout,
compilation key, coaching or dependency substitution. All **43 original files match byte for byte**
the pristine public download. An independent input-to-audit check verifies **all five business
rows** and downstream audit; the registry is empty and port 8181 closed afterwards.
The configured sink is empty because the shipped nodes do not publish sink messages; audit output
is the verified result. A sink-output scenario is not claimed.

The acquisition took the longer public route: catalogue JSON, the site's distributed encoder and
`/start/scaffold?s=...`, rather than the documented `template=analyser-bundle` shortcut. The resulting
project matches the pristine exact-URL preflight artifact. Discovery friction remains a follow-up;
completion does not mean routing is efficient. The wrapper's bootstrap hit the operator sandbox's
macOS temporary-directory restriction. The client preserved the error and used installed Maven 3.9.9,
with unchanged POM and isolated empty cache. It used `-DskipTests`; the separate unattended public
preflight and analyser CI are the test evidence. This is not a claim that the wrapper worked in the
client sandbox. No starter code was patched.

Frozen predictions: uncoached completion, shipped launch/stop, five matching events and unchanged
files all hold. Duration **388.311 s**, prediction 240 s, signed error **+148.311 s**. The attribution
prediction fails: **87 tool batches**, two `task`, one `readme`, **84 unattributed**. Missing pointers
remain in the denominator. The transcript shows actual reads but cannot supply the missing declared
reasons. No comparative routing claim, no v1 improvement and no generation-task acceptance follows.
Clock drift is 0.0056 s. Prior authentication failures remain failures, not retrospectively passes.

The bounded release claim is public acquisition to independently checked sample output, with the
above environmental tooling substitution disclosed. Existing static and unattended published-bundle
gates remain regression protection. The analyser release can now proceed under the owner's instruction.

## Published analyser verification

[Release 1.16.0](https://github.com/telaminai/fluxtionauditlog-analyser/releases/tag/v1.16.0),
commit `02dac9d3b36820e6c41c224c1aafbbdd9a3365e1`.
[Release workflow](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35545989495)
passed verification, atomic changelog/tag push, jar build, assets and docs dispatch.
Docs deployment 35546034206 passed; the public release-notes page contains 1.16.0 and session recovery.
The workflow stamps UTC date 2026-09-20; this operator record uses local date 2026-09-21.

Both independently downloaded jars are 3,855,184 bytes and match the shipped checksum file:
`0d0821434f977f1a3d0e1ecd7b5a5f92745e6db859e47a3454b9ec254a485e54`.
The manifest identifies 1.16.0. [Asset result](evidence/release-journey-2026-09-21/published-analyser.json).
The **downloaded release jar**, not the local build, also passes the three-JVM normal-quit,
offer-only startup, explicit restore and CLI-isolation smoke:
[published-jar result](evidence/release-journey-2026-09-21/published-restart.json).

The accepted claim is an available onboarding workflow with independently checked sample output.
Dependency certification, dependency-jar integrity, vendor shadowing and other open producer/canvas
findings are not closed by this release. No claim of improved routing versus v1 is made.
