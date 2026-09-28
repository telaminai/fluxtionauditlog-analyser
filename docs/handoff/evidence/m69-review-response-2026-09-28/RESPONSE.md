# M69 review response — PR57 R1–R9 (2026-09-28)

Responds to [`review_m69_spotlight_walks_2026_09_28_codex.md`](../../review_m69_spotlight_walks_2026_09_28_codex.md).
The reviewer's report, probes and captures are unchanged. Predictions were committed before any fix, in
[`PREDICTIONS.md`](PREDICTIONS.md) (`711678c3`).

**Range for review:** `ad61a37c..` the commit that adds this file, on `feat/m69-spotlight-walks`. R4 (`1a6ddf63`) and
its control are kept as they were.

**Reproduced first.** Both probes were compiled against a fresh test classpath at `ad61a37c` and run. Every captured
line matched: R1, R2, R3, R5, R6, R7, R8 and R9 were live, and R4 read `UNRESOLVED`.

## Findings

| # | cause | fix | regression, red before | controls (fast engine) | commit |
|---|---|---|---|---|---|
| **R1** | The identity verdict travelled only as text. The presenter re-read record text whatever the verdict. `walk {play}` was outside the read policy. The chart run basis was the opening digests only. | The **node** decides trust (`WalkIdentity.recordsTrusted` on the session's verdict) and carries it on both walk effects. Untrusted, the presenter reads no record text and claims no run basis. The node always states what is lit, including nothing. Play joins the record-reading verbs, while the bin, list, rename, delete and end stay outside. A capture reads no record text, and a save of record steps is refused, while the published verdict is degraded. The run basis is the opening digests plus the session's record count (`runBasisOf`). | `WalkReviewFrameTest#aDegradedIdentityConstrainsTheTargets`: red at "expected UNRESOLVED but was CURRENT". After the first fix it was red again at **"and not lit"**, exposing a second defect: a re-resolution that left nothing available kept the previous light. `WalkAuthoringTest#aDegradedIdentityRefusesRecordBinding`. `WalkIdentityTest#aFollowAppendMovesTheRunBasis` tests new API, so it cannot be red before; its control removes the count. | 7 of 7: `m69-r1-node-decides-trust`, `-trust-rule`, `-presenter-reads-no-untrusted-text`, `-light-states-nothing-too`, `-play-reads-records`, `-capture-refused-while-degraded`, `-run-basis-counts-records` | `c19e4357` |
| **R2** | `save` rewrites the walk's single run basis, so `append` and `replace` rebound every kept chart step. | Refuse, with nothing saved, when a **kept** step has a chart target and the walk's run is not the current one. Record targets carry their own digests and structural targets none, so neither is affected, and replacing the chart step itself is allowed. | `WalkAuthoringTest#anEditOnAnotherRunDoesNotRebindKeptChartSteps` (the append was accepted); `#anEditOnTheSameRunOrWithoutChartsWorks` guards against over-refusing. | 3 of 3: `m69-r2-append-refuses-mixed-run`, `-replace-refuses-mixed-run`, `-only-kept-charts-count` | `038249a3` |
| **R3** | The presenter dropped `n`, and `applySpotlight` renumbered the reduced list by position. | The light request carries each target's `n`, and the walk's path hands the overlay those numbers (`SpotlightOverlay.renumber`). | `WalkReviewFrameTest#theOverlayKeepsTheSessionsNumbers`, on the real overlay's `Lit.n`: "expected <2> but was <1>". | 2 of 2: `m69-r3-overlay-takes-the-numbers`, `-presenter-carries-n` | `378ee835` |
| **R5** | A failed selection was only a note, and a visible detail pane counted as proof. `MainFrame` cleared the previous light before `applyView` could refuse. | A `detail` target is available only if the **real** selection is the requested record. A record outside the log refuses the whole view before anything changes. The previous light goes out only after validation. | `WalkReviewFrameTest#aHiddenRecordIsNotClaimedAsShown` (SHOWN) and `#aRefusedViewLeavesThePreviousStep` (SHOWN; expected NOT_SHOWN, with the filter unchanged and the old light kept). | 3 of 3: `m69-r5-record-refused-whole`, `-clear-only-after-validation` (the reviewer's ordering, put back), `-selection-prerequisite` | `8cbc12c9` |
| **R6** | The node held a name and a count, and each effect re-read config. The verb itself decided to End on delete and rename, and save had no equivalent. | `WalkPlayRequested` carries the definition. The node holds it frozen, publishes it (`WalkPlaybackState.definition`), and both effects carry it. A new fact, `WalkDefinitionChanged`, is posted on **every** save, replace, delete and rename, and the adapter decides nothing. The node's policy: a rename keeps the frozen version; a save that changes the steps, or a delete, ends the showing with a reason; an unchanged save changes nothing. | `WalkReviewFrameTest#replacingTheShowingWalkIsTheSessionsDecision` (timed out: no decision); `#renameKeepsItAndDeleteEndsIt` (the rename ended the walk). Node level: `WalkPlaybackTest#aDefinitionChangeIsTheNodesDecision` and `#navigationUsesTheFrozenDefinition`. | 6 of 6: `m69-r6-replace-ends-the-showing`, `-delete-ends-the-showing`, `-rename-keeps-the-frozen-version`, `-only-the-showing-walk`, `-authoring-reports-every-save`, `-verb-reports-rename` | `157b50c2` |
| **R7** | The verb took "a walk of that name is showing" as acceptance. | The play fact carries a request id. The node publishes its `Answer` to that id, and the verb reports it. The range decision stays in the node. | `WalkReviewFrameTest#aRefusedPlayIsReportedAsRefused`, on the real session: ok=true for step 99. | 2 of 2: `m69-r7-verb-reports-the-answer`, `-node-answers-a-refusal` | `a397aac5` |
| **R8** | The presenter applied a filter only when the step had one. | An absent filter applies `Filter.ALL`, at playback, so old and imported definitions are covered. The contract is unchanged. | `WalkPresenterTest#anOmittedFilterAppliesTheDefaults`, from parse to application, plus a stored null filter: "grouping defaults ... RAW_EVENT". | 1 of 1: `m69-r8-omitted-filter-defaults` | `846f2413` |
| **R9** | `Number.intValue()` narrowed before the check. Infinity passed too, because `floor(inf) == inf`. | `WalkSteps.integral` checks the value exactly (finite, integral, in range, including a `BigInteger` beyond `long`) before any conversion. It covers `view.record`, `view.filter.from` / `to` and the play step, and a failure refuses the whole request, naming the field. | `WalkStepsTest#numbersAreRangeCheckedBeforeNarrowing`; the 2^32+2 case in `WalkVerbTest#playReportsAndReadsBack`. | 3 of 3: `m69-r9-record-range-before-narrowing`, `-integral-is-exact`, `-play-step-range` | `15ba38a5`, corrected in `a397aac5` |

Every control failed with a **named assertion**, never an error or a skip. Source and compiled classes were restored
byte-identically, and each restored run was green.

## Rule 9

No adapter decides when an active walk is stale:
- trust is the node's decision;
- the definition is frozen in the node;
- definition changes are facts, and the node decides what they mean;
- the reply to a play request is the node's answer.

The adapters only perform effects and report facts. `WalkIdentity.recordsTrusted` is one pure rule, and it has two
readers of the session's published verdict: the node for playback, and authoring for capture. It is not a second
composition of that rule.

## Tests whose assertions changed, not only their shapes

Two tests asserted the design the review found wrong, and both were changed to assert the corrected behaviour:
- `WalkVerbTest#deletingTheShowingWalkEndsIt` asserted that **the verb** posts End, which is the scattered decision R6
  removes. It now asserts that the delete and the rename are reported, and nothing else.
- `WalkVerbTest#readIdentityPolicyPerOperation` asserted "playing reads no records", which is R1's policy mismatch. It
  now asserts that playing reads records, and that the bin, the list and end do not.

`WalkPlaybackTest#nothingAvailableLightsNothing` changed from "no light effect" to "one light effect naming no
target". The property, that nothing is lit, is kept, and it is stated so that a previous light is taken down (R1).
Every other change was mechanical, for the new fact and effect shapes.

## Retained controls

Three were orphaned by the rewrites. Each is re-anchored where its property now lives, with the same witness, and
each is caught:
- `m69-s4-delete-ends-showing`, now on the verb's reporting line;
- `m69-s4-play-reads-the-session`, now on the verb taking the node's answer;
- `m69-s4-read-identity-per-operation`, now on the read-policy line.

## Verification (JDK 21, macOS)

| check | result |
|---|---|
| `mvn -o clean test`, fresh Surefire XML | **2679 / 0 / 0 / 144** (total / failures / errors / skips), **358 reports, 0 orphans** |
| registered display gate, sequential, both headless flags false | **144 / 0 / 0 / 2**, 29 suites. The skips are `WalkArrowKeysFrameTest#arrowKeysMove` and one `PersonAtTheScreenFrameTest` case; both need a keyboard focus owner that this Mac does not give posted events. **Locally the gate is therefore not green.** CI's `ui-frame` job rejects skips and is the gate for these. |
| every `m69-*` control: 62 original, R4's, and 27 new | **90 of 90 caught**, run as 4 parallel shards in separate worktrees (22 or 23 each, about 110 s wall-clock). They include `m69-review-record-representation` and the changed-record and run-basis controls. |
| `--mode preflight` | 29 frame suites, **339 anchors**, no orphan or ambiguous anchor |
| reviewer's frame probe, re-run | `numbering: session=[1:false, 2:true], overlay=[2:second]` and `identity: session=UNVERIFIED, target=UNRESOLVED, available=false, lit=false`. The probe then stops **by design**: it reuses the session it has just made `UNVERIFIED`, so R1 now refuses its next record-bound save. Its later scenarios are covered, each in a fresh frame, by `WalkReviewFrameTest`. |
| reviewer's headless probe | **No longer compiles.** `WalkAuthoring.Frame` gained `post` (R6) and `sessionIdentity` (R1). The evidence is left as it was; R2, R8 and R9's scenarios are regressions above. |
| `mkdocs build --strict`, `git diff --check`, rule-1 sweep | green / clean / clean (no file outside the two exempt ones) |

**The asynchronous `MainFrame.onLoaded` NullPointerException** (`summaryPanel` is null) **reproduces**. It fires
during `LogFindingsOnEverySurfaceFrameTest`, and Surefire counts no error for it. It is **not caused by this PR**:
the same exception fires in the same test on `origin/main` at `80decad7`. It is disclosed, not fixed here.

## Predictions, scored

| # | prediction | result |
|---|---|---|
| 1 | Regeneration for R1, R6 and R7, needing staging | **Half.** Regenerated three times, offline, with both copies byte-identical each time. **No staging was needed**, because the node's constructor never changed. |
| 2 | Each regression red before, with a named assertion | **Hit for every finding.** R7's first red run was *my own* R9 overreach: I had bounded the verb's step to `MAX_STEPS`, which is a range decision in the verb. It was corrected in `a397aac5`, and then R7 went red as the reviewer reported. R1's run basis tests new API and is witnessed by its control. |
| 3 | Changed tests: WalkPlaybackTest, FakeSessionAdapter, the WalkVerbTest and WalkPresenterTest rigs | **Mostly.** `FakeSessionAdapter` did **not** change. `WalkAuthoringTest`'s rig did. Two tests had asserted the flawed design (above). |
| 4 | About 2,680 headless; frame suites 28 → 29; about 12 new controls | **2,679** (+15); **29** (hit); **27 new controls** (more than predicted: several findings needed more than one call site pinned). |
| 5 | At least one retained control orphaned; possibly one made vacuous | **3 orphaned** and re-anchored. **None vacuous**: all 90 M69 controls were caught. |
| 6 | The `onLoaded` NullPointerException is not this PR's | **Hit.** It reproduces on `main` at `80decad7`. |

The design falsifiers did not occur:
- no adapter decides staleness;
- the presenter is told trust by the node and never reads the verdict;
- `context.walks.showing` renders the node's state, which is built from the frozen definition.

## Remaining limitations

- **Not merged with `main`.** `origin/main` moved to `80decad7` ("docs: revise M69 because playback and identity
  reuse need explicit safeguards") after this branch was cut. Integrating it is the owner's call.
- **The presenter's use of the frozen definition has no control.** Under the node's policy a showing walk's config
  never differs from its frozen copy (a change ends it or renames it), so reading config instead would be an
  equivalent mutant. The node-side controls cover the policy.
- **Not attempted:**
  - W-A4's byte comparison of both settings files after a dirty start;
  - a native Robot or keyboard trial;
  - a native screenshot capture (W-A11);
  - the hash-pinned skills publication.
- **Out of scope:** the optional items (strip reasons beyond three lines, a lexical-guard note) are not addressed.
