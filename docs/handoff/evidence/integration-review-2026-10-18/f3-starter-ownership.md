# Integration F3 — starter ownership across project changes

Owner: `feature/propose-starter` (#167). Review base `ddccd5c5`; before-fix production `f15e9d4b`.

The owner chose: cancel later opens and drafts; retain downloaded files. A failed switch or no-op re-open does not change the project. A successful close also cancels continuation. Already placed drafts are not erased.

## Executed before-fix witnesses

- `StarterProjectTest#aProjectSwitchCancelsEveryPendingStarterStage`: **1 / 1 / 0 / 0**. Named failure: `project switch must cancel starter continuation at stage 0`, expected CANCELLED, actual DOWNLOADING.
- `StarterProposalFrameTest#aProjectSwitchDuringTheGraphOfferCannotOpenTheOldGraph`: **1 / 1 / 0 / 0**, real frame, isolated home, stub starter/agent. Complete the actual proposal/install/open chain, deliver the watcher's fact, switch to DEMO-B inside the graph-offer answer, answer Yes. `a superseded starter offer must not open the old graph`: expected false, actual true. The active-project assertion passed first.
- The earlier standalone `StarterOfferOwnershipProbe.java` also failed with the old graph in the new project. It injected an adapter effect, not a complete starter chain; the maintained frame test supersedes that narrower probe. The original probe is preserved unchanged.
- An initial test compilation failed because it accessed a private fake-adapter field. Corrected to the existing `withProfile` fixture API before counting either witness. That unsuccessful attempt remains in local ignored output.

## Correction

`StarterProject` cancels on accepted project application/restoration, except the specific operation admitted as its own open. `SessionBoundary` rejects a cancelled queued open by starter request, not the ambient OPENING phase; an unrelated person's switch receives the ordinary running-agent confirmation. The node consumes a graph-offer answer and emits an open effect only while that offer still belongs to it. Cancellation stops the watch through an effect and settles the proposal through a fact. Design reads consult the node's revocable permission before and after the read. No files are deleted.

The processor and GraphML were regenerated together with `mvn -Pregen process-classes`; credentials were neither inspected nor printed. `GeneratedSourceIsPublishableTest`, `OneDispatchModelTest`, and `CustomerApiKeysTest` passed. The maintained replay covers five pending phases, failed-switch controls, ignored late outcomes, and a queued stale open. A current graph Yes opens exactly once; the existing real-frame successful chain remains the positive control.

After-fix focused run: **45 / 0 / 0 / 0**. First display run: **9 / 0 / 0 / 0** across StarterProposalFrameTest (4), TemplateCatalogueFrameTest (1), DesignSpotlightFrameTest (4). Final full and display results are recorded in the review report. Counts are total / failures / errors / skips.

The expanded neighbouring display run caught an implementation mistake: rechecking every design guard after the read broke six recovery cases (**20 / 6 / 0 / 0**), because recovery's original guard compares the generation before its own ReadRequested. The new revocation recheck is now specific to StarterDesignGuard; recovery retains its existing captured-generation checks. This failed attempt is retained in local ignored output, not presented as a product finding.

## Final boundary refinement

Source inspection of my correction found that rejecting a stale OpenProjectRequested in SessionBoundary was too late for other request observers (AssistantLoop, OpenBundle, ProjectReopenOffer). A real-frame adapter-boundary probe, now maintained as `StarterProposalFrameTest#aCancelledQueuedStarterOpenNeverReachesTheProjectBoundary`, failed **1 / 1 / 0 / 0** on production `7e826028` at `a cancelled starter callback must not submit a project request`. It injects an already-issued stale effect, not a second complete download chain; the existing successful full chain remains its positive control.

The queued adapter now enforces StarterProject's revocable permission before entering that shared funnel and reports a not-opened fact instead. Ownership and the rule for revoking it remain in the node. The boundary's correlated-operation guard remains defensive. No handler/wiring changed in this refinement, so no regeneration was needed. It is a separate corrective commit: prior review commits were not rewritten.
