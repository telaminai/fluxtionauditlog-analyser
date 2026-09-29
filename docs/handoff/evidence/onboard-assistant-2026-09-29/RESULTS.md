# Onboard assistant and conversation journeys — results

Against the predictions committed first ([PREDICTIONS.md](PREDICTIONS.md)). Branch `feat/onboard-assistant-journeys`,
built from main `bce667f5` and rebased (unpublished) onto `d6468eb0` after 1.28.0 was released; the proposal `8522d275` is
its first commit, unchanged. Commit ids below are the rebased ones.
RAN = executed here; READ = source inspection; UNVERIFIED = not run, and not replaced by anything else.

Environment: macOS arm64, Corretto JDK 21.0.11, Retina display. Native (Robot) input was refused for the first run of the
native suite (2/2 skipped) and delivered in the final run of every frame suite (2/0/0/0); the display slept at times (a
black full-screen capture), which stopped one screenshot. Both are stated where they matter.

## Commits

| Slice | Commit | What |
|---|---|---|
| — | `4b82d3a3` | the proposal, unchanged |
| — | `ca23c81b` | premises checked, decisions D1–D9, predictions P1–P10 |
| OA-1 | `d450f5ad` | `assistantLoop` node, `AssistantAdapter`, `AssistantPanel`, `AssistantTranscript`, guard in `ActionExecutor`; `LlmPanel` removed |
| OA-2 | `61c228a8` | one panel, two hosts (unowned window, close docks, placement, docked pass-through) |
| OA-3 | `d8f1eca1` | typed dialogue on walks, bindings, editor, storage, sharing, export privacy |
| OA-4 | `490c6f55` | journeys play in lock-step with `walkPlayback`; handoff |
| OA-5 | `51ac8290` | the DEMO journey, its bundle and page; old-reader check |
| OA-6 | (this commit) | `context.assistant`, results, docs |

## Predictions

| # | Prediction | Result |
|---|---|---|
| P1 | regenerates through `-Pregen` with no hand edit; publishable | **Met.** Two regenerations (OA-1, OA-4); `GeneratedSourceIsPublishableTest` green; the attribution line stripped by the build. OA-4 needed a one-cycle bootstrap constructor, removed after the regeneration. |
| P2 | a late reply after Cancel/New chat/workspace change does nothing; dropping the guard lets it act | **Met.** `AssistantLoopTest` (real processor) and `AssistantAdapterTest` (real client, loopback provider): 0 late actions; `oa-ticket-guard` caught. |
| P3 | with no log, context/topology work, aggregate refuses; the old store gate fails it | **Met.** `AssistantLiveFrameTest` in the real frame; `oa-no-store-gate` restores `store != null` and is caught. |
| P4 | one Send across pop out → three tabs → dock is one request; draft and transcript intact | **Met.** `AssistantHostFrameTest#oneConversationAcrossHosts`: 1 provider request, same conversation id, draft moved. |
| P5 | native clicks/typing in the window keep a walk; outside press ends it | **Met on this desktop.** `AssistantNativeFrameTest` 2/0/0/0 with native Robot input in the final frame run (first attempt: 2/2 SKIPPED, no native input — kept on record). IME composition is not tested. |
| P6 | dialogue round-trips everywhere; a walk without it is byte-identical | **Met.** `ConversationWalkStorageTest` 9/0/0/0 (store, project, global tier, profile, share, refusal, bin, rename); no new keys for a plain walk. |
| P7 | play/Back/resume make zero requests and write nothing | **Met.** `ConversationJourneyFrameTest#aJourneyPlays`: 0 provider requests; the isolated home's bytes unchanged. |
| P8 | 1.27.0 opens a journey and plays it without dialogue | **Met, and more.** `tools/journey-old-reader.py` with the release jars (checksums matched), 1.27.0 and — released from main while this was built — 1.28.0: 4/4 steps SHOWN; after each rewrote the profile, all 36 dialogue keys survived ([1.27.0](old-reader-1.27.0.txt), [1.28.0](old-reader-1.28.0.txt)). |
| P9 | full headless suite green | **Met** at every slice; final counts below. |
| P10 | no paid live-provider run | **Met — and so UNVERIFIED:** OA-A2's live half, OA-A14 and OA-A18 remain open. |

## Misses and surprises, kept

- `oa-turn-budget` **survived** its first run: the per-turn budget was derived as rounds × per-reply, so it could never
  bind first. It is now its own setting (Settings ▸ Assistant), and the control is caught.
- `oa3-turn-size-limit` **survived**: its test dereferenced a null refusal (an error, not a named assertion). The test now
  asserts the refusal exists first.
- `oa4-demo-has-no-composer` **survived**: the assertion used `isShowing()`, which a step's tab switch made vacuous. It
  now checks the composer's own visibility and that Send is off.
- `oa4-handoff-carries-nothing` **survived** as first written: an equivalent mutant (priming after the view had cleared).
  Re-registered to capture the demo's words BEFORE the handoff.
- The editor **crashed** on any walk without dialogue (`List.copyOf` refuses the nulls that mean "nothing yet") — found
  by `ConversationEditorFrameTest`; fixed, with a headless case.
- The verb treated `steps` + `conversation` as two operations — found by `ConversationWalkVerbTest`; fixed.
- Two M69 control anchors moved with the code (`m69-s4-saved-by-the-assistant`, `m69-r6-replace-ends-the-showing`) and
  were re-anchored to the same mutation; both caught.
- The journey's chart step was honestly **NOT SHOWN** at a 1180 px window ("no room at 167×396 px"). The journey is now
  captured at the docs window size, where all four stops are SHOWN.
- A spotlight on the status line was refused in a frame test ("not on screen") until the window was shown — the product
  being right; the test was fixed.
- **CI (Xvfb) found a real defect:** the assistant's window is unowned, so disposing the analyser (other than through the
  app's exit sequence) left it showing. It is now disposed with the analyser; `AssistantHostFrameTest#theWindowGoesWithTheAnalyser`
  and `oa-window-disposed-with-analyser` guard it. The same CI runs skipped one native test twice: a stderr diagnostic
  showed the table's centre was UNDER the assistant window on CI's smaller screen, so the press honestly never reached the
  analyser. The test now clicks an uncovered part of the table. (Also in those runs, not from this branch: an EDT
  `NullPointerException` in `onLoaded` from a late load on a disposed frame, seen in 2 of main's last 3 CI runs.)

## Deviations from the proposed spec (r1)

1. **Basis (§4.3).** A turn is superseded by a change of project, log or graph, not by a view or filter change: actions
   address absolute records and every result states its scope. The turn's own `open` is attributed to it.
2. **Per-turn budget.** A new setting, `assistant.maxActionsPerTurn` (default 30), beside the per-reply cap and rounds.
3. **The popout is an unowned `JFrame`** (an owned window always floats above its owner, contradicting §3's "not always
   on top").
4. **The docked-host exception (§3)** is implemented where the conflict actually is: the spotlight overlay lets presses
   inside the DOCKED assistant through by `contains()` — Swing's own routing, nothing re-dispatched. The window's
   presses never reached the overlay, so it needs no rule.
5. **Tool details in recorded dialogue (§7)** are not captured: a recorded turn carries the question and the answer's
   words. Recorded tool results would need their own inert, bounded display type; left for a follow-up.
6. **Streaming** is not implemented (non-goal in §1); status states are shown instead.

## Acceptance (spec §9)

| ID | Status | Evidence |
|---|---|---|
| OA-A1 | **Met** | `AssistantHostFrameTest` 3/0/0/0; `oa-one-panel-two-hosts`, `oa-close-docks` |
| OA-A2 | **Half**: fake-provider met; **live provider UNVERIFIED** | `AssistantAdapterTest`, `AssistantLiveFrameTest`; `oa-every-result-fed-back`. No authorised live run. |
| OA-A3 | **Met** | `AssistantLiveFrameTest`, `AssistantLoopTest#noKeyNeverSends`; `oa-no-store-gate`, `oa-no-key-refused` |
| OA-A4 | **Met** | `AssistantLoopTest` (3 cases), `AssistantAdapterTest#aHeldReplyReleasedAfterCancelDoesNothing`; `oa-ticket-guard`, `oa-edt-guard`, `oa-cancel-stops-transport` |
| OA-A5 | **Met** (node) | `AssistantLoopTest#aWorkspaceChangeSupersedesTheTurn`, `#theTurnsOwnOpenContinues`; `oa-workspace-supersedes`, `oa-own-open-continues`. No separate real-frame basis witness. |
| OA-A6 | **Met, except IME**: theme, resize, 1200×800 reach, screen-bound restore; native typing and ← edit the composer, not the walk | `AssistantHostFrameTest`, `AssistantPlacementTest`, `AssistantNativeFrameTest` 2/0/0/0. **IME composition UNVERIFIED.** |
| OA-A7 | **Met** | `ConversationJourneyFrameTest#aJourneyPlays` (labels, 0 requests); `oa4-demo-has-no-composer` |
| OA-A8 | **Met** | same test (step 1→2→1, resume at 3, no duplicates); `ConversationWalkPlaybackTest`; `oa3-dialogue-edit-ends-walk` |
| OA-A9 | **Met** | closed chart not available, missing record NOT SHOWN without its answer; `oa4-refused-step-keeps-accepted-prefix`, `oa4-accepted-step-tracked` |
| OA-A10 | **Met** | home bytes unchanged across play; `oa4-playback-writes-nothing` (an ordinary persisting chart open) caught |
| OA-A11 | **Met on this desktop** | `AssistantNativeFrameTest` 2/0/0/0 (a native click in the window and in the docked assistant keeps a walk; a table click ends it); `oa-docked-pass-through` caught. CI's Xvfb run is the second platform. |
| OA-A12 | **Met** | `ConversationWalkStorageTest`, `ConversationWalkVerbTest`, `ConversationEditorFrameTest`; `oa3-serializer-keeps-bindings`, `oa3-binding-validated`, `oa3-share-validates` |
| OA-A13 | **Met** | `ConversationBundleProfileTest`, `ConversationDraftTest`, `WalkConversationTest`; `oa3-redaction-covers-dialogue`, `oa3-turn-size-limit`, `oa3-excerpt-discloses-words`, `oa-key-never-in-errors` |
| OA-A14 | **UNVERIFIED** | no cross-machine recipient; the local old-reader and isolated-home runs are not this acceptance |
| OA-A15 | **Met** | `ConversationJourneyFrameTest#theHandoffIsAFreshThread`; `oa4-handoff-ends-demo`, `oa4-handoff-carries-nothing` |
| OA-A16 | **Met** | `AssistantLoopTest` (caps, rounds, budget, failure), `AssistantAdapterTest` (401, unknown verb, malformed JSON) |
| OA-A17 | **Met** | `OneDispatchModelTest#theAssistantHasOneDispatchModel`; one `actionDispatcher()` for bridge and onboard |
| OA-A18 | **Half**: the page describes the actual bytes, and 8 of its 9 images are native captures read by eye (met); **published download UNVERIFIED**; the docked "no provider" image pending an awake display (`capture-journey.py --docked --page`) | `JourneyCatalogueTest`; `oa5-*` controls |

## Final gates (RAN, the final tree)

Counts are total / failures / errors / skips.

| Gate | Result |
|---|---|
| `mvn -o -q test` | **2934 / 0 / 0 / 192**, 398 reports, no orphans ([headless-counts.txt](headless-counts.txt)); baseline main `bce667f5` was 2862 / 0 / 0 / 182 |
| every registered frame suite, one at a time under the display lock | **38 suites, 190 / 0 / 0 / 1** ([frame-suites.txt](frame-suites.txt)); the one skip is `PersonAtTheScreenFrameTest`'s existing keyboard-focus precondition |
| fast-engine controls: every OA control, plus the two re-anchored M69 controls | **38 requested, 38 caught** in one run, each at a named assertion, byte-identical restore ([controls-final.json](controls-final.json)) |
| preflight | 38 frame suites; 507 anchors |
| `mkdocs build --strict` | clean |
| old readers (released 1.27.0 and 1.28.0) | all checks passed ([1.27.0](old-reader-1.27.0.txt), [1.28.0](old-reader-1.28.0.txt)) |
| public-data sweep, tracked files and additions | empty |

Not run: an authorised live-provider trial; IME input; a cross-machine recipient; publication. CI is the second platform.
