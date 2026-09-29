# PR #77 — independent review and bounded corrections

**Verdict: merge after R1 is corrected and the final head's CI completes successfully.**

Reviewed `d6468eb0...5c79e35733ff5701fd541eed0d332733aebe936c`. The owner subsequently authorised fixes on the PR branch. Correction commit **`9a8ffbde`** contains seven new regressions and seven controls; its independent-review delta is `5c79e357..9a8ffbde`. This report is on the separate review branch. Nothing was merged or released.

RAN means executed here; READ means inspected source or CI logs; REPORTED means author evidence not independently reproduced. Counts below are **total / failures / errors / skips**, not passes.

## Required findings, ranked

### R1 — an unrelated open can inherit the assistant's authority; view scope is not bound (still open)

**RAN**, `session/node/AssistantLoop.java:269`, `:251`, `:438`.

Concrete sequence on the real generated processor: start an assistant turn; accept a reply with `open`, then `flag`; hold the open effect; submit and complete a separate person's normal `OpenLogRequested`/`LogOpened`; complete the held assistant action. Actual result: the turn remains `RUNNING_ACTION`, adopts `DEMO-persons-log.yaml`, stays unfrozen, and requests the second action. Expected: the unrelated change supersedes the old turn before it can affect that log. The node identifies ownership by the *presence* of a running `open`, not by a causal ticket on the workspace change.

The [probe and raw output](evidence/pr77-review-2026-09-29/workspace-probe.txt) reproduce this using `SessionFixtures.openLog`, not direct node mutation. Its adapter holds effects; it is not a second real-frame open experiment.

The same probe posts `ViewFilterChanged` during a provider request: it remains `REQUESTING`. **READ:** `basisKeyNow()` omits the view/filter revision. Deviation 1 is therefore not equivalent to the spec's basis rule. A question about the current filtered investigation can be followed by a chart action after the person changes that scope; a chart with no explicit range follows the new filter. Absolute record addresses do not make all render verbs scope-independent. That concrete chart race is source-derived, not separately driven through a provider/frame here.

**Correction:** carry causal assistant ticket/action identity through the actual open request and its completion, and distinguish it from person/other-client requests. Bind relevant view scope, or explicitly freeze it in the action contract and supersede incompatible external changes. Keep that decision in the session graph. Add a held-open/person-open/next-action refusal witness, plus a held-reply/filter-change/render witness. Do not solve it with a broader “open in progress” exception. This requires protocol/wiring work; I did not regenerate the processor or introduce an adapter-owned substitute.

### R2 — private words entered snapshot and audit metadata (fixed in `9a8ffbde`)

**RAN**, `assistant/AssistantAdapter.java:187`, `:250`, `:297`.

A loopback HTTP 400 whose body echoes `DEMO-question-private-phrase` put those words in the published failure reason. Separately, a model action with an unknown action name put that arbitrary name in the snapshot and session audit. Scrubbing the configured key did not protect question/answer text carried by those fields.

The correction sends safe error categories/HTTP status only, and permits only the existing verb-schema vocabulary in action-name facts. Full dispatcher refusal results remain in the private transcript/provider history. No new session events or generated edits were required.

Regressions: `AssistantAdapterTest#providerFailureWordsNeverBecomeSessionFacts` and `#anUnknownActionNameStaysOutOfSessionFacts`, through the real client, loopback HTTP, dispatcher and generated processor. Both failed at named snapshot assertions before correction; afterwards both also assert that the audit contains no private words. Two new controls discriminate those protections. The existing key-control was re-anchored to reintroduce unsafe error forwarding and still fails at its key assertion.

### R3 — cancellation did not guard the final asynchronous source apply (fixed in `9a8ffbde`)

**RAN**, `ui/ActionExecutor.java:1069`; completion path `ui/MainFrame.java:2904`.

Start a Java spotlight, block its real source-archive discovery, invalidate the assistant guard, then let discovery finish without relying on worker interruption. The old code returned success and lit the source. Its guard protected preparation entry, not the later reveal/apply callback.

The correction checks the captured guard again in that callback, before any reveal or source-view mutation. `JavaSourceSpotlightFrameTest#anEndedAssistantCannotApplyACompletedSourceLookup` failed **1 / 1 / 0 / 0** before correction; afterwards the complete class ran **13 / 0 / 0 / 0**. The test uses a real frame and resolver with a held discovery lock; ticket invalidation is represented by the bound guard becoming false. Its mutation removes only the final check and is caught by the named refusal assertion.

### R4 — cancelling the first unanswered request lost the action manifest (fixed in `9a8ffbde`)

**RAN**, `session/node/AssistantLoop.java:368`.

Send the first question, prepare its manifest-bearing prompt, cancel before an answer, then send again. `end()` removed the unanswered user message but retained `manifestSent=true`; the new provider history contained no manifest and the replacement prompt omitted it. The correction clears that marker when no retained history remains. `AssistantLoopTest#cancellingTheFirstRequestDoesNotLoseTheManifest` and its control protect both retry instructions and the single unanswered-user-message rule.

### R5 — dialogue bounds had three holes (fixed in `9a8ffbde`)

**RAN**, `walk/WalkConversation.java:54`, `:100`; `config/ConfigStore.java:608`.

- A 512 KiB author plus a short turn was accepted because the total ignored the author and JSON escaping. The limit now measures serialized UTF-8 JSON. The regression also checks quote-heavy text.
- Version `4294967297` narrowed to integer `1` and was accepted. It now refuses without narrowing and names the supplied version.
- A profile declaring `conv.t.count=100000` allocated all those turn objects before validation. The reader now materialises at most 201, preserving an invalid overflow sentinel so the profile is refused rather than silently accepted as truncated. No huge-memory or OOM trial was needed.

The three new regressions each failed on the reviewed head. Each has a named wrong-result control; all are caught after correction.

## Slice decisions and other checks

| Slice | Decision | Evidence |
|---|---|---|
| OA-1 live loop | **Request changes: R1**; R2–R4 corrected | RAN generated-loop, adapter and frame tests; READ generated dispatch, node dependencies and effect ordering. Ticket checks reject cancelled-turn replies even when a newer turn has the same phase. ThreadLocal clearing is in `finally`. |
| OA-2 hosts | Accept with nits | RAN host/live/native suites, including disposal and docked pass-through. READ one reparented panel, bounds/theme restoration and application shutdown. Unowned host is an acceptable documented choice. |
| OA-3 storage/editor | Accept after R5 correction | RAN storage, draft, schema, editor and bundle tests plus controls. READ every authoring/share entrance, unknown-version extras, bin/rename and excerpt disclosure. Plain walks write no conversation keys. |
| OA-4 journey playback | Accept | RAN real journey, walk playback/review suites and controls. READ accepted-step projection, pending-live refusal, fresh handoff and dialogue-edit termination. Refused/preparing steps retain the accepted prefix; no provider is invoked by narrative playback. |
| OA-5 DEMO journey | Accept with disclosed acceptance gaps | RAN independent bundle-byte/log inspection and released 1.28.0 reader; inspected all eight native images by eye. Publication and genuinely separate-machine acceptance remain unverified. |
| OA-6 acceptance evidence | Accept with corrections disclosed | RAN counts and requested controls below. Earlier counts predate the final extra host test; they are not this head's totals. The four reported former-survivor repairs are meaningful on reading and their final controls are caught here. |

**Optional:** `tools/journey-old-reader.py:83` accepts `PARTLY_SHOWN` as well as `SHOWN`, and its preservation check should explicitly require a nonempty key list. My actual run had four `SHOWN` steps and 36 retained keys, so neither permissive condition concealed a failure in this run. Tighten these before using this script as a general full-journey acceptance gate.

Deviations 2–6 are reasonable with their stated limits: independent action cap; unowned popout; narrowly bounded docked pass-through; selected visible question/answer words rather than tool bodies; and status updates rather than token streaming. Deviation 1 needs R1's scope resolution. Large transcripts stay outside the processor; lifecycle decisions in the inspected paths stay in nodes. Metadata projections in the frame are not themselves a second lifecycle.

## What ran

Environment: macOS, JDK 21; local fake provider only. Native display runs acquired the shared display lock, one class at a time. Test fixtures and the old-reader harness used isolated settings/homes.

| Command / group | Result |
|---|---|
| `mvn -o -q test`, unmodified `5c79e357` | **2935 / 0 / 0 / 193**, 398 reports, no orphans |
| Eight requested display classes | **39 / 0 / 0 / 0**; per-class raw counts in evidence |
| Original requested fast-engine cases | **39/39 caught**, 145.7 s; requested and returned names compared, named failures and both byte-restore flags checked |
| New regression trial before corrections | Four headless classes **40 / 6 / 0 / 0**; source race **1 / 1 / 0 / 0** |
| `mvn -o -q test`, corrected tree | **2942 / 0 / 0 / 194**, 398 reports, no orphans |
| Corrected source frame class | **13 / 0 / 0 / 0** |
| Seven new controls + retained key and EDT controls | **9/9 caught**, 42.4 s; named failures, source/classes restored byte-identically, restored green |
| `python3 tools/journey-old-reader.py <released-1.28.0-jar>` | Four `SHOWN` steps, actual profile rewrite, 36 preserved dialogue/binding keys |
| `--mode preflight` | 38 frame suites, **515 anchors** after the seven additions |
| `python3 tools/test_project_chart_review.py` | **5 / 0 / 0 / 0** |
| Strict MkDocs; both diff whitespace checks; rule-one sweep | Successful; tracked/additions/bundle-member scans clean |

The DEMO bundle is **20,242 bytes**, SHA-256 `cbd8f28dbc50263d45678cc926f089e6ad5cffab1a990f37e8ec87b20fa21743`, identity `sha256:4662ddcecd4d632d393ae05d4a00595cdb8351d7dbee56b0a9c93d718ebb53cb`. Independently counted: MarketDataEvent 400, OrderUpdateEvent 166, RiskBreachEvent 160; 726 total, first/last breach indices 16/722. No replay member or absolute machine-root match. All eight images contain DEMO evidence and their title bars; a live-provider/docked no-provider catalogue image is still missing as disclosed.

Predictions were explicitly written **after baseline/source reading, before adversarial probes**. P1–P4 and P6 confirmed; P5 refuted by R5; P7's clean baseline confirmed and its native-skip risk did not materialise here. These are not blind predictions. The author's prediction commit precedes implementation in git history.

## CI and limits

**READ:** reviewed head `5c79e357`, CI [36600464414](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36600464414): all jobs successful, display **191 / 0 / 0 / 0**, collector **508 controls exactly once across four shards**. Correction head `9a8ffbde` started [36605585352](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36605585352); build, ui-frame, loop-bench and mutation-selftest were successful at the final check; the four mutation shards/collector were still running. Earlier green CI does not certify my corrections.

The logged `MainFrame.onLoaded` null `summaryPanel` exception is real despite green CI. I found the identical exception on main `bce667f5`, run **36585070827**, before this PR; the release-base run **36587621958** did not contain it. It is a disclosed pre-existing asynchronous-load/fixture issue, not a newly attributed PR77 blocker.

Not run: paid/live provider sessions, IME composition, a separate-machine journey run, site publication, the released 1.27.0 reader, full local mutation gate, or processor regeneration. Generated source/resource copies match and dispatch wiring was read against the framework reference; I cannot independently certify the generation operation itself without running the forbidden regeneration. The supplied 1.27.0 results and generation provenance remain REPORTED. R1's source-level chart-scope scenario needs a real dispatcher/frame regression before closure.
