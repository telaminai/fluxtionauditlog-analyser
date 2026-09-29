# PR #77 R1c correction — 2026-09-29

Starting PR head: `a0bf460fcc875342659f2243376f224becef2363`.
Isolated worktree: `/private/tmp/pr77-r1`; JDK 21. Only DEMO data and the local
`FakeProvider` were used. The preceding independent-review and R1 evidence remain intact.

## Wrong-result witness, before the fix

`AssistantLoopTest#idleFilterChangeRefreshesTheBasisWithoutFreezingTheConversation`
failed: after a completed turn and an external `ViewFilterChanged`, `frozen` was true
instead of false. `AssistantScopeRaceFrameTest#idleSearchFieldEditKeepsSendAvailableAfterTheDebounce`
also failed: typing one character through the real search editor reached the debounced
filter, then froze the completed conversation and disabled Send. Both failures were run
on `a0bf460f` before changing the node. The assistant's own `filter` followed by `graph`
frame case passed before the fix, establishing that the new test exercises a working
path that an origin-loss regression could break.

## Correction and checks

`AssistantLoop.onViewFilterChanged` still supersedes an externally changed filter while
PREPARING, REQUESTING or RUNNING_ACTION. When idle and not already frozen, it re-captures
the current basis without freezing the conversation. If a project, log or graph already
froze the conversation, a later filter edit cannot unfreeze it. An action's own filter
fact retains its ticket/action identity.

The headless test asserts that a completed conversation stays unfrozen and accepts a
follow-up Send under the new filter. A second headless test asserts that a different
graph still freezes an idle conversation. Real-frame regressions assert the search
editor's debounced path keeps Send enabled, and that the assistant's own `filter` then
`graph` actions finish with the chart present and two provider requests.

Preflight found **39 frame suites and 521 anchors**. The two new controls mutate the
idle guard back to the freeze behavior and remove the assistant origin from the frame's
filter fact. The targeted fast gate requested **18 controls and caught all 18** at their
named assertions, with byte-identical source/class restoration and restored-green runs.
The request included all `oa-r1-*` and R2–R5 review controls, plus the older ownership,
ticket, EDT and error-boundary guards. Full local headless Maven: **2954 tests / 0 failures /
0 errors / 199 display skips**, **399 source-mapped reports, no orphans**. Seven relevant
display classes: **40 / 0 / 0 / 2**; the two native Robot cases skipped because this
desktop did not deliver their required native press (`focusOwner=null`). All five
`AssistantScopeRaceFrameTest` cases ran and passed. CI's separate zero-skip display gate
remains required for final-head acceptance.

No handler signature changed, so the committed generated processor and GraphML did not
need regeneration. Strict MkDocs, whitespace, project-chart tooling and the public-data
sweep are recorded with the commit. Exact-head PR and merged-main CI are checked separately.
