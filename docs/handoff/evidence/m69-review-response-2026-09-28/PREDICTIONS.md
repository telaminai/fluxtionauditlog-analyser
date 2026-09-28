# M69 review response — predictions, before any fix (2026-09-28)

Responds to [`review_m69_spotlight_walks_2026_09_28_codex.md`](../../review_m69_spotlight_walks_2026_09_28_codex.md)
(PR57 R1–R9, implementation findings). The reviewer's report and evidence are untouched. The response is recorded in
`RESPONSE.md` beside this file.

**Reproduced first, at `ad61a37c`.** Both reviewer probes were compiled against a fresh test classpath and run:
`ReviewProbe` headless, `ReviewFrameProbe` on a real frame. Every line matches the captured outputs: R1, R2, R3, R5,
R6, R7, R8 and R9 are live, and R4 reads `UNRESOLVED` (closed by `1a6ddf63`).

## Design, fixed before code (rule 9)

| finding | where the decision goes | shape |
|---|---|---|
| **R1** | the node | The node decides whether record identity can be trusted (the session's verdict is not `UNVERIFIED` or `REPLACEMENT`) and carries it on `ApplyWalkViewEffect` and `ResolveWalkTargetsEffect`. Untrusted, the presenter reads **no** record text and supplies no run basis, so record targets are `UNRESOLVED` and not available, and charts `UNRESOLVED`. The chart run basis becomes the opening file digests **plus the session's current record count**, so a Follow append moves it. `walk {play}` joins the dispatcher's record-reading verbs; list and bin operations stay outside. A capture refuses to bind record targets while the published identity is degraded. |
| **R2** | authoring | `append` and `replace` refuse when a kept step has a chart target and the walk's run basis differs from the current one. Record targets carry their own digests, and structural targets none, so neither is affected. |
| **R3** | the adapter carries the node's numbers | The light effect's targets keep their `n`. The walk's light path gives the overlay each target's number, and the overlay stops renumbering by position for it. |
| **R5** | presenter validation plus propagation | An out-of-range record refuses the whole view **before anything changes**. The previous step's spotlight goes out only after validation, moving the clear from `MainFrame` into `applyView`. A record the step's filter hides is a failed prerequisite: targets that depend on the selected record (`detail`, `detail:node:*`) are not available, with that reason. |
| **R6** | the node | `WalkPlayRequested` carries the definition. The node holds it frozen, and every effect carries the step it names, so the presenter never reads mutable config for an active walk. A new fact, `WalkDefinitionChanged(name, definition-or-null, renamedTo)`, is posted by the adapter on **every** mutation (save, replace, delete, rename, restore), and the adapter decides nothing. The node's policy: a rename keeps the frozen version under the new name; a replace with a different definition, or a delete, ends it and says why; an identical save changes nothing. The verb's own "post End on delete or rename" goes. |
| **R7** | the node | `WalkPlayRequested` carries a request id. The node publishes the answer to that id (accepted, or refused and why) in `WalkPlaybackState`. The verb reports that answer, not whether a walk of that name is showing. |
| **R8** | presenter | A step with no filter applies `Filter.ALL`: every stored filter field defaults, never the person's current selection. Applied at playback, so old and imported definitions are covered. The contract is unchanged. |
| **R9** | parsers | `view.record`, `view.filter.from` / `to` and the play `step` must be finite, integral and in range **before** narrowing. `4294967296` is refused whole, naming the field. |

## Predictions

1. **Regeneration is needed** for R1, R6 and R7 (new effect fields, a new fact, the play fact's shape and new state
   fields). The first `-Pregen` hits the stale-generated-constructor problem and needs staging, as round 3 of the
   spike did.
2. **Each regression is red before its fix, with a named assertion:**
   - R1: `available` is `true` where `false` is expected;
   - R2: `HISTORICAL` → `CURRENT`, or the append is accepted where it should be refused;
   - R3: the overlay's `Lit.n` is `1` where `2` is expected;
   - R5: the phase is `SHOWN` and the `detail` target available, where it should not be;
   - R6: the published count is 2 after the one-step replacement;
   - R7: `ok=true` for step 99;
   - R8: `RAW_EVENT` / `dirty` survive;
   - R9: the parse is accepted, as record 0.
3. **Existing tests changed, not only added:**
   - `WalkPlaybackTest`, because the play fact's shape changes;
   - `FakeSessionAdapter`, for the new effect fields;
   - `WalkVerbTest`'s rig, because delete and rename no longer post End themselves;
   - `WalkPresenterTest`'s rig, for the effect shapes.

   Each is a mechanical change, with no assertion weakened.
4. **Counts:**
   - headless 2,664 → about 2,680 (+16 regressions);
   - one new frame suite, `WalkReviewFrameTest`, for the real-overlay, real-selection and real-session checks,
     registered in both CI lists (the preflight's suites 28 → 29);
   - about 12 new mutation controls;
   - `m69-review-record-representation` and the changed-record and run-basis controls are still caught.
5. **At least one retained M69 control is orphaned** by the rewrites (WalkPlayback, WalkPresenter and WalkVerb each
   change controlled lines), and the preflight names it. One of them may also be made vacuous, as in the spike's
   round 3, and only a mutation run finds that.
6. **The `MainFrame.onLoaded` NullPointerException the reviewer logged is not caused by this work.** It will be
   looked for in the display run and reported either way.

## What would show a design wrong

- A fix that needs the adapter to decide *when* an active walk is stale. That would be a rule-9 breach, and the
  design would be wrong.
- R1 needing the presenter to read the identity verdict itself instead of being told.
- R6's frozen definition diverging from what `context.walks` publishes about the showing walk.
