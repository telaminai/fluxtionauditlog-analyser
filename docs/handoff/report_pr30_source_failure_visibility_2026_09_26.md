# PR #30: visible source-check failure

## Frozen prediction — before the correction

Subject: `bf9c47b7`. Review: PR #30 comment `5847169235`, required N1.

A failed Ctrl-click writes its reason into the Node pane. In Processor-only mode that pane
is outside the visible layout. Reveal the existing Node pane through the existing mode helper
before publishing the current request's failure; retain the ticket check before any UI effect.
The Processor stays on screen as the layout becomes Split. No history or model change is needed.

Predictions:

1. A new real-frame regression starts in Processor-only mode with actual processor text,
   dispatches a Ctrl-click mouse event to its text component, and makes the existence lookup
   fail. Before the fix, the failure reason exists but the label's `isShowing()` assertion
   fails. After the fix, the label is showing with nonempty visible bounds, the reason is
   correct, the processor text is retained and the request is finished.
2. The existing queue-rejection, exception and stale-failure regressions remain green.
3. Tighten the optional stale-failure fixture: ignore cancellation interrupts until its explicit
   release, invoke the newer navigation on the EDT, and assert the old check has not decided
   before release. It must still report discarded and retain the newer text and label.
4. Scoped headless counts stay 34 / 0 / 0 / 0. SourceFreshnessFrameTest becomes 2 / 0 / 0 / 0;
   JavaSourceSpotlightFrameTest remains 12 / 0 / 0 / 0. No new frame class or CI-list edit.

Protocol: run the new test against the unfixed source, then apply the correction and rerun it.
This is a before/after regression, not a mutation gate. No full suite, mutation gate or manual
mutation is planned. Record actual results below, including any prediction misses.

## Results

Pending. This prediction record claims no completed correction or verification.
