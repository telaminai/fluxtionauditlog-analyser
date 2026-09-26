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

The prediction above was committed as `a70edec6` before the test or correction was written.

**N1 fixed:** the current-ticket failure callback calls the existing `revealPaneFor(nodePane)`
after finishing the check, before setting its label. Processor-only mode therefore becomes
Split. The existing stale-ticket guard remains before every UI effect. No new message service,
source read, history entry or model installation is introduced. The guide and Unreleased entry
now describe that visible mode change explicitly.

**Regression:** `SourceFreshnessFrameTest.aFailedTypeClickInProcessorModeShowsItsReason` uses
MainFrame under an isolated home. It loads constructed processor Java, selects the Source tab
and Processor-only mode, locates the `C()` token and dispatches a Ctrl-modified mouse-pressed
event to the real text component. Its production mouse listener resolves the type and starts
the injected failing lookup. This is a synthetic Swing mouse event in a real window, not a
socket verb, a direct call to `openTypeIfPresent`, or a physical/Robot click.

The test first asserts the Node label is hidden and processor text is showing. It waits for
the actual failure decision, then checks the reason, `isShowing()`, nonempty visible bounds,
the still-visible unchanged processor and completed check state.

**Observed before correction:** against product source identical to `bf9c47b7`, the new test
reported **1 total / 1 failure / 0 errors / 0 skips**, at its intended assertion:

> a failed type click must show its reason in Processor-only mode ==> expected: <true> but was: <false>

After adding the reveal call, the frame class passes. No baseline failure was an error or skip.
This is a reproduced before/after regression; no mutation or byte-restore claim is made.

**Optional N2 taken:** the stale-failure fake now remains held across cancellation interrupts
until explicitly released. The newer navigation runs on the EDT; the test establishes A is
shown and no old decision has arrived before releasing C. It still requires the actual
`discarded` decision and unchanged A feedback. A `finally` releases the fake on assertion
failure. This removes the misleading ordering claim identified by the review.

## Commands and verification

All runs used Corretto JDK 21 on macOS, in the isolated fix worktree. Display classes ran
sequentially. Counts are total / failures / errors / skips from their Surefire XML.

| Command | Result |
|---|---|
| `mvn -o -q test -Dtest=SourceFreshnessFrameTest#aFailedTypeClickInProcessorModeShowsItsReason -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false` before fix | **1 / 1 / 0 / 0**, intended assertion above |
| `mvn -o -q test -Dtest=SourcePanelFreshnessTest,SourceServiceTest,SourcePanelRootChangeTest` after fix | **23 + 5 + 6 = 34 / 0 / 0 / 0** |
| `mvn -o -q test -Dtest=SourceFreshnessFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false` after fix | **2 / 0 / 0 / 0** |
| `mvn -o -q test -Dtest=JavaSourceSpotlightFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false` after fix | **12 / 0 / 0 / 0** |
| `mkdocs build --strict` | Passed |
| `git diff --check` | Passed |
| CLAUDE.md rule-one terms over tracked files and additions | Clean |

All four frozen predictions held. The final scoped totals are **34 headless and 14 display**,
with no failures, errors or skips.

**READ:** `SourceFreshnessFrameTest` is already included in both CI display lists; no list edit
or new suite was required. The current-ticket guard remains ahead of the reveal, so the
obsolete-failure test still protects newer feedback. No generic screenshot depicts this
conditional error state; the normal menu/source layout did not change.

**Not run:** full suite, mutation gate/manual mutations, provider legs, LLM sessions or public
release acceptance. The earlier unrun P2 remains unverified; this work does not supply its
missing baseline. No participant files or keys were accessed. No owner policy was changed.
No merge or release is performed; the author retains acceptance and merge responsibility.
