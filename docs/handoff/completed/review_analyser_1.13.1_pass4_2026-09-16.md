# Analyser 1.13.1 — independent review, pass 4

## Verdict: READY WITH FOLLOW-UPS for 1.13.1

**R3-B1 is CLOSED.** My exact project-first reproduction now finishes without a dialog. I also
found and executed a stronger real-menu sequence which isolates the close verb: it passes now,
fails when only that verb's audience declaration is removed, and retains a human-warning control.
There is no remaining blocker from this four-pass candidate review.

R3-F2's escaped text-block example is fixed and its new colour test is red against the preceding
implementation. R3-F3 is resolved by the concrete M44.3a tracker entry. The deferred refresh/arrival
defect itself remains open; this verdict does not claim the entire session lifecycle is correct.

Reviewed: code `1c3c817a`, documentation `ee54ad3a070a452104b7a7a8ec91f0601557f2c4`, over
`4363f439`. Worktree: `/private/tmp/analyser-1.13.1-pass4-ee54ad3a`. Reviewer: Codex.
The third-pass review and ledger content at `4363f439` compare unchanged with my `2eef950e`.
No production source, tests or skills were edited. Only this review and its ledger annotation
are committed; no push, compiler work or release workflow.

## Non-blocking findings

### R4-F1 · P3 · CONFIRMED — the close-only regression is testable through Recent GraphML

Locations: `PairingDuringLoadFrameTest.java:142` and ledger lines 32–35. The ledger correctly says
the current test stays green if only the close declaration is removed. I reproduced that result.
Its further claim is wrong:

> the human graph-open entrance is a file chooser and cannot be driven from a test, so no sequence reachable here has a human operation immediately before the socket close

`MainFrame.rebuildRecentMenu` wires `recentGraphmlMenu` to the human `openGraphml(String)` helper
(`MainFrame.java:3867`, `:3886`). A real menu item's `doClick()` runs that entrance without a
chooser, without assigning the audience field in the probe, and without invoking a fabricated effect.

My stronger sequence:

1. Socket-open project P, then human `openFile(A)`; A logs `nodeA`. Wait for the load.
2. Socket-open graph B, which declares only `nodeB`. It is kept with a mismatch verdict and is
   automatically added to Recent GraphML.
3. Click that same B item in **Recent GraphML**. This is a human operation; B remains open and
   the audience becomes true.
4. Socket action `open {close: "graph"}`.

Results:

| Code / action at step 4 | Observed result |
|---|---|
| Current candidate, socket close | Graph closed, **0 dialogs**, audience false |
| Remove only `AppControlAdapter.close`'s assignment | Graph closed, **1 Project modal**; the probe fails its no-dialog assertion |
| Current candidate, human File-menu Close graph instead | Graph closed, **1 Project warning**, audience true — positive control |

The warning being triggered by a refresh remains part of deferred M44.3a. For this control, the
point is that the *same* warning is non-modal for the socket and still rendered for the person.

**Follow-up:** commit this Recent GraphML sequence and its close-only negative control to the
display suite, and replace the ledger's impossibility claim. This is a missing durable regression
pin, not a remaining implementation failure: I have independently exercised the guard itself.

### R4-F2 · P3 · CONFIRMED — the universal audience wording exceeds the actual routing

`CHANGELOG.md:52` says:

> every socket verb declares it on entry and every File-menu action declares it in its listener

That is broader than the implementation and the handoff's own exceptions. Socket `close`,
`openGraphml` and `selectProcessor` now declare false. Log arrivals and project transitions carry
their existing audience. `openLogs` and `discoverGraphs` have no new entrance declaration.

On the human side, the changed `MainFrame.openGraphml(String)` helper is reached by **Recent
GraphML**. File → Open GraphML calls `topologyPanel.chooseFile()` directly (`MainFrame.java:1556`);
file drops call `topologyPanel.load()` directly (`:1517`). Those routes do not pass through the
helper whose comment calls it the File-menu entrance. This routing discrepancy is source-confirmed;
I am not claiming a new modal regression on those human paths.

**Follow-up:** narrow the changelog/javadoc to the entrances actually covered, and describe the
helper accurately. The implementation is still a mutable field with declarations at selected
entrances, not a type-enforced operation context. Keep that distinction visible when extending it.
Do not add declarations indiscriminately to shared effect handlers.

## Response dispositions

| Item | Disposition | Independent verification |
|---|---|---|
| R3-B1 mixed-audience modal | **CLOSED** | Exact project-first sequence from pass 3, then the stronger Recent GraphML sequence above; both no-dialog on current code. Close-only mutant fails the latter. |
| R3-F2 escaped text-block delimiter | **CLOSED** | Reused my JDK-compilable escaped-delimiter example: `alpha`, `beta` and `gamma` now all have literal colour. The new committed colour test fails against the pass-3 jar at `beta` and passes in the current suite. |
| R3-F3 missing tracker ownership | **CLOSED** | `docs/specs/tracker.md:487` now contains M44.3a with the failing-load/two-graph sequence, the stale-observation explanation, and graph-identity/arrival remedy. |
| ui-frame gate | **ACCEPTED** | Local display run 4/4, and exact-tip CI job 4/4 with its separate no-skip XML check. |
| R2-F3 refresh/arrival defect | **OPEN, accepted deferred scope** | Now concretely tracked as M44.3a; no fix or wider lifecycle approval inferred. |

### The author's mutant caveat, independently repeated

I created isolated bytecode overrides of `MainFrame$AppControlAdapter` in `/private/tmp`, leaving
repository source/tests untouched. Each run printed the loaded adapter's actual code-source path.
The current compiled frame tests were invoked with their temporary-directory arguments under a
real display, using the scratch runner from pass 3.

- Remove all three socket-entrance declarations: the original three frame tests pass; exactly
  `humanArrivalThenSocketGraphOpenAndClose_noDialogForTheSocketOperations` fails on the dialog count.
- Remove only the close declaration: **all four committed frame tests pass**. The author's
  measurement is correct; the preceding socket graph open masks the missing close declaration.
- Run my Recent GraphML probe against that same close-only override: **fails**, one dialog.
  Current candidate: **passes**, zero dialogs. Human-close control: **passes**, one warning.

### The two undeclared socket entrances

`discoverGraphs()` assembles candidate data; I found no session-warning effect on that synchronous
path. `openLogs()` passes `OpenRequest.socket(provenance)` to `openRolledSet`; the eventual successful
arrival declares its audience in `onLoaded`. I found no need for an extra field assignment solely
to fix those successful paths. This is source inspection, not a new rolled-set/failure-path run.

## Commands, environment and results

macOS, Corretto 21.0.9; isolated user homes for frame probes. Maven ran offline. Local HTTP tests
and display processes ran outside the sandbox with approval.

```sh
git worktree add --detach /private/tmp/analyser-1.13.1-pass4-ee54ad3a ee54ad3a
env JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.9/Contents/Home mvn -q -o clean verify
env JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.9/Contents/Home mvn -q -o \
  -Dtest=PairingDuringLoadFrameTest -Djava.awt.headless=false \
  '-DargLine=-Djava.awt.headless=false' test
gh run view 35143844342 --repo telaminai/fluxtionauditlog-analyser --json jobs,headSha,conclusion,url
gh run view 35143844342 --repo telaminai/fluxtionauditlog-analyser --job 104954786876 --log
```

- Clean verify: **185 suites, 1,408 tests, 0 failures, 0 errors, 4 skipped**. Summed from XML before
  the focused display run overwrote that suite's report. The four skips are the frame tests.
- Local display run: **4 tests, 0 failures, 0 errors, 0 skipped**, suite time 1.725 s.
- [CI run 35143844342](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35143844342)
  is on `ee54ad3a070a452104b7a7a8ec91f0601557f2c4`. Build, loop-bench and ui-frame all completed
  successfully. The [display job](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35143844342/job/104954786876)
  reports **4 / 0 / 0 / 0**, 3.822 s, and the next step prints
  `tests="4" errors="0" skipped="0" failures="0"`.
- Maven's reduced-POM line-ending-only rewrite was inspected and restored; it is not part of this review.
- Final `mvn -q -o test` gate repeats **1,408 / 0 / 0 / 4**. Staged diff check and public-content
  sweep pass, with no non-exempt matches.

Reviewer evidence retained on this host:

- `/private/tmp/Analyser1131AudienceProbe.java`, mode `socket-close-after-human`: exact prior sequence.
- `/private/tmp/Analyser1131Pass4CloseProbe.java`: stronger sequence; modes `recent-socket` and
  `recent-human`, using the real Recent GraphML and Close graph menu listeners.
- `/private/tmp/Analyser1131Pass4Mutant.java`: isolated adapter overrides, variants `close` and `all`.
- `/private/tmp/Analyser1131TextBlockProbe.java`: same compilable source and colour probe as pass 3.
- Logs: `/private/tmp/analyser1131-pass4-original-sequence.log`,
  `analyser1131-pass4-recent-socket.log`, `analyser1131-pass4-recent-human.log`,
  `analyser1131-pass4-recent-socket-mutant.log`, `analyser1131-pass4-suite-mutant-close.log`,
  `analyser1131-pass4-suite-mutant-all.log`, `analyser1131-pass4-colour-negative.log`,
  `analyser1131-pass4-full.log`, `analyser1131-pass4-frame-test.log`, `analyser1131-pass4-ci-frame.log`.

## WHAT I DID NOT CHECK

- No fresh whole-product audit: this is the pass-3 response and the specifically requested fourth-pass
  checks. Earlier closed findings retain their earlier evidence; the current full/display suites ran.
- No concurrent/re-entrant operation stress test or asynchronous audience-ownership proof. A field
  that each entrance writes is not proof against all interleavings.
- No new S3, rolled-set, follow-rotation or failure-dialog tests. No network-transport timeout test:
  the probes execute the real action executor on the EDT, as in the preceding reviews.
- I did not drive the native File → Open GraphML chooser or a drag/drop. Their bypass of the changed
  helper is **CONFIRMED by source**; any resulting audience-related user-visible defect on those
  paths is **PLAUSIBLE, not demonstrated** here.
- No re-run of M44.3a's failed-load/two-graph reproduction or review of its eventual implementation.
- No exhaustive Java lexer conformance, theme/platform matrix, performance benchmark, regeneration
  profile, compiler changes or release workflow. CR-only line-comment colouring remains an accepted
  cosmetic limitation.
- No new strict docs-site build: only non-site handoff documents are changed by this review. I
  inspected the CI conclusions for build/loop-bench, and the actual test/count log for ui-frame.

## Next handoff

The candidate may proceed on this review. Preserve the explicit deferred M44.3a limitation.
Follow up by committing the stronger close-only regression test and correcting the route/testability
wording; those are not reasons to reopen the now-verified R3-B1 implementation fix.
