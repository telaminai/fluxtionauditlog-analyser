# M81 A — activity is the node's published decision

This branch starts from combined C head `2964d734f0ce932a0a4b9505ca2d5be219ede183` (PR #173), itself based on
main `5e770067863d8f150dd25340723037831382dc55`. It explicitly integrates the authorised #171 feature branch
at `ff3a9888d14d9f1b7bc4a7b0af377f4cc2fd7e2a` in merge `793fcac04fe0b241abc72f640f0ed72ef79d7c96`.
That is a feature-branch integration, not a PR/main merge or independent approval of #171. Both merge identities
are personal; the integration baseline passed **605 suites, 4,278 tests, zero failures/errors, 351 headless skips**.

## Decisions and boundaries

The session node owns current tools, scope, counters, the separate tool-duration and last-event clocks, and the
60-second stall verdict. It accepts a tool start before its result, refuses late generation/ticket facts, and does
not treat a synthetic tick as a stream event. Actual progress, including recognised ping/keep-alive observations,
can advance the last-event clock. A completed tool line alone never means answer text is streaming.

Fixed step wording and the redacted argument reference are node-owned. `ActivityText.step` only substitutes that
reference from the bounded adapter store; panel and context share it. Argument words never enter facts or node
state. Read shows a base name; Bash shows only the first line, at most 40 characters and eight argument words,
with absolute POSIX, drive and UNC paths redacted and controls removed. Existing metadata/schema hashes and
top-level customer keys are not changed; four additive `project.assistant` fields are pinned literally.

The owner answers are recorded in specification r4. Keep waiting never repeats on a timer in the same silence
episode; elapsed time keeps counting. Only a new stream event rearms it, followed by another 60 seconds.
Approval suppresses the stall entirely. Tool/turn totals reset at New chat, not at a process restart.

**Explicit A5 correction, OD-81.11:** the owner preserves application-modal approval. A composer Show control
behind it would be blocked, so none is offered. The status says Needs your approval; the person answers the
existing modal question. The existing show-effect performer is idempotent when internally repeated. No modality,
approval lifetime, queue, deadline, permission, or Stop semantics were changed. This supersedes the draft's Show
affordance, rather than pretending a programmatic click proved human operability.

## Named wrong-result evidence

- `aToolStartIsVisibleBeforeItsResultAndIsCountedOnlyOnce`: before the fix, **tool START publishes its step before
  tool FINISHED** failed. Green checks visibility before the result, one count for a replayed start, then the
  transition to Thinking and genuine answer text.
- `aStallStartsAtSixtyNotFiftyNineAndKeepWaitingNeedsANewStreamEvent`: before the fix the 60-second cue was absent.
  Independently changing the threshold to 61 failed **60 seconds without a stream event raises the cue**.
  Restoring 60 passed; 59 stays quiet, acknowledgement stays dismissed at 240, and a new event rearms at 60.
- `anApprovalSuppressesTheStallAndKeepsItsOwnPendingRequest`: dropping the approval guard failed **no stall cue
  while an approval is pending or queued** (expected false, observed true); restoration passed. The original
  method name mentioned Show before the owner's explicit modal correction.
- `AssistantChromeBudgetFrameTest.theChromeBudget`: removing the eight-word argument cap produced **24 words**
  in the long-script state in both themes and failed **the chrome budget (spec §6)** against the unchanged
  **15-word** running ceiling. Restoring the cap returned that state to **12 words**, without raising the ceiling.
  The extra tenth state changes the observation count from 18 to 20, not any word/control ceiling.

Additional checks cover parallel tools (an actual stream event resets silence, not a running tool's duration),
old-ticket refusal, totals exactly once, redaction, and adapter tool-start/tool-result ordering with a fixture process.
`CustomerApiKeysTest` pins exactly phase/currentStep/secondsSinceLastEvent/approvalPending, including idle values.
`OneDispatchModelTest` rejects step words, stall logic or a new UI clock in the panel classes.

## Verification status

Final focused activity/replay/recovery/dispatch/customer API run: **52 tests, zero failures/errors/skips**.
An earlier sandboxed run could not bind the existing header test's loopback HTTP server; the same run with local
binding permitted passed. This was an environment error, not a wrong-result witness. Final regeneration succeeded;
the removed Show input was bootstrapped through temporary source compatibility, then that source was removed and
the graph regenerated again. No generated Java/GraphML was hand-edited; the generated header is retained.

Final full headless run: **607 suites, 4,287 tests, zero failures/errors, 351 skips**. The first full run caught two
static-contract omissions: the plain-text label setter needed its explicit cross-surface `UiText.plain` wrapper,
and the new vocabulary-only tool-name fact needed its inventory entry. Both were corrected before the final green
run; no permission, word-budget or schema ceiling was relaxed.

Final native-display run, sequential under the shared display lock: `LocalAgentPermissionFrameTest` (4),
`AssistantChromeBudgetFrameTest` (1), and `ClaudeCodeTabFrameTest` (2): **7 tests, zero failures/errors/skips**.
The native composer click left the modal permission request and open turn intact. Both themes measured the
long-script state at 12 words and the approval state at 8, with four controls each. Published exact-head CI is
reported separately in the PR; local results do not stand in for it.
Preflight: **75 frame suites, 688 anchors** (the two additional suites come from #171). Existing FrameTests were
extended; no new suite class needs registration. Strict MkDocs passed using the repository-pinned requirements.
No live CLI/model, paid provider, participant project, real assistant configuration or credentials were used.
The protocol heartbeat evidence limits remain recorded in r4: no guaranteed quiet-Bash heartbeat is claimed.
