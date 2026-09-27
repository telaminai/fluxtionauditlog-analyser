# M69 spotlight walks — results (2026-09-27)

Scored against [`PREDICTIONS.md`](PREDICTIONS.md), which was committed before any code (`7a68b262`, with P-10 withdrawn
and P-10b recorded in `fd3c3d99`, both before S0). A miss is recorded as a miss.

Branch `feat/m69-spotlight-walks`, slices S0 `8f6fd72b`, S1 `dd2f24bb`, S2 `49d911fc`, S3 `8aba74dc`, and S4 in the
commit that adds this file.

## Gates at S4

| gate | result |
|---|---|
| `mvn clean test` (headless) | 2,663 run, 0 failed, 137 skipped. Every skip is a display-bound frame test, run below. |
| registered frame suites, real display, sequential | 137 run, 0 failed, 2 skipped: `WalkArrowKeysFrameTest` and one `PersonAtTheScreenFrameTest` case. Both need a keyboard focus owner, which this Mac does not give posted key events. They run in CI under Xvfb, and CI rejects a skip. |
| verification preflight | 28 frame suites, 311 anchors |
| mutation controls, fast engine | the complete set: **311 of 311 caught** by a named assertion, each restored byte-identical (785 s). The S4 set, 13, is included. |
| `mkdocs build --strict` | passes |
| rule-1 sweep | clean: no file outside the two exempt ones, with the new files included |

## Predictions

| id | prediction | result |
|---|---|---|
| P-1 | S0 touches `TopologyPanel` alone; the session-recovery tests pass unchanged | **Hit.** One source file, plus its test and one control. No session-recovery test changed. The `loadFromSource` half of the fix is defensive: no transition reaches it with a digest set, so it has no mutation control, and the code says so. |
| P-2 | Storage follows the report pattern; no existing report test changes; three `SettingsShare` paths plus the label | **Hit.** Export, preview and apply, plus the label. No report test was modified. |
| P-3 | The record digest is identical across two opens, heap and mapped | **Hit.** `WalkIdentityTest` asserts it for every row of a file, in both stores. |
| P-4 | `FilterState.setAll` turns up to four change events into one | **Hit.** `WalkPresenterTest#aStepFilterIsOneChange` counts exactly one. |
| P-5 | The paint outcome needs `ChartPanel` only, plus one data-stamp call in `GraphPanel` | **Hit, and smaller than predicted.** `ChartPanel` only: the data revision it already kept was enough, so `GraphPanel` did not change. |
| P-6 | A demo step is ready in under 500 ms; only the bound's own test reaches 5 s | **Not measured.** No test reached the 5 s bound, and the frame tests' waits (polled every 50 ms, 8 s deadline) all passed. But no test times readiness, so the 500 ms half of this is unscored. |
| P-7 | Classifying input before dismissal changes only `SpotlightOverlay`'s mouse handling; every M64 frame test passes unchanged | **Hit.** No M64 frame test was modified, and all pass. |
| P-8 | The key check passes under Xvfb; here it may be skipped, and a skip is reported as one | **Hit.** It skips here, as reported above, and passes in CI's `ui-frame` job on PR #57, which rejects a skip. S3 also found that a skipped test inside a mutation *witness* class fails the gate's baseline, so the key test had to move into its own class (`WalkArrowKeysFrameTest`). That was not predicted. |
| P-9 | Apart from the spec-named contract tests, only `CloseVerbTest` and `ProjectVerbTest` assert the verb count | **Hit on counts.** `McpToolsTest` also counts, and it is a W-A10 contract test. **Unpredicted:** a verb also has to be named in the FAQ's security answer, because `walk` is destructive to MCP clients (`FaqSecurityContractTest`), and in the assistant guide (`ManifestVerbContractTest`). The Project panel's reveal-only test had to read `WalkVerb`, where `context.walks` is assembled. And a copied schema line made an existing control's anchor ambiguous (`p33-restore-schema`), caught by the preflight. |
| P-10b | `walkPlayback` needs only `openLog` and the operation gate as parents; its facts and effects fit the existing shapes; `SessionGraphShapeTest` needs one new expectation | **Two misses.** Its parents are `openLog` and the effect queue. It never needed the operation gate, because a walk's own ticket does the gate's job for stale answers. `SessionGraphShapeTest` needed **no** change: it does not enumerate nodes. The facts and effects did fit the existing shapes, and no other node changed. |
| P-11 | 2,500–4,000 lines, including tests | **Miss, over.** About 4,800 added, excluding the regenerated processor: roughly 3,000 main, 1,600 tests, 460 docs. The regenerated processor adds a further ~780 across its two copies and graph. |
| P-12 | At least 25 new mutation controls | **Hit.** 62 registered: S0 1, S1 17, S2 21, S3 10, S4 13. One further S2 control was written, found unreachable, and removed (below). |
| P-13 | More than "two days", measured in working sessions | **Miss, under.** One working session on 2026-09-27, carried across one context compaction. |

## Design falsifiers (stated in advance)

- **A step cannot be applied without writing the profile or settings.** *Not observed.* `WalkPresenterTest` shows
  selection, filter and focus applied with no call reaching the save funnel. A closed chart is named and never
  opened.
- **The paint outcome cannot tell a stale paint from a current one without a second size calculation.** *Not
  observed.* The outcome records the width, height and data revision it painted. `ChartDrawnFactFrameTest` changes
  the data under the same timestamps, and the fact goes unsettled until the next paint.
- **Input cannot be classified before dismissal without breaking an M64 behaviour.** *Not observed* (P-7).

## Misses in the work itself (not predictions)

- **Weak witnesses**, found by the mutation gate, which counts an error as a survivor:
  - `WalkStepsTest`: an NPE on a null error;
  - `WalkIdentityTest`: an NPE from `List.copyOf` with a null;
  - `WalkPresenterTest`: an index error on an empty notes list;
  - `ChartDrawnFactFrameTest`: a moving window masked the stale-data case, and the unpainted case threw an NPE.

  Each now asserts before it dereferences.
- **An unreachable control.** `m69-s2-node-refuses-stale-generation` could not be caught. The generated dispatch
  runs `onLogChanged` first, and it ends the walk before a stale step can arrive. The control was removed, and the
  check is documented in the node as defensive.
- **Two D-SP4 guard failures.** Comments in the config and session packages spelled the word the guard forbids. The
  guard now allows only the exact phrase "spotlight walk".

## Not done in S4, and why

- **The capture (W-A11, capture half).** `tools/capture-docs.py --walk` drives the scene on the demo fixture under
  the isolated home. It saves a three-step walk through the verb and plays it from step 2. The painted fallback was
  read and shows the right scene. But this terminal has no Screen Recording permission, so no native image exists.
  The fallback was not committed, and the docs do not reference a walk image. Owner action: grant the permission and
  run `python3 tools/capture-docs.py --walk`.
- **The skills (`docs/skills/`).** The canonical skills are hash-pinned to a published revision
  (`CanonicalSkillsTest`), so an edit is a publication step, not a docs edit. The verb reaches every client through
  its schema and both manifests, which are derived. Mentioning walks in `point-at-the-fault` is left for the next
  skills publication.
