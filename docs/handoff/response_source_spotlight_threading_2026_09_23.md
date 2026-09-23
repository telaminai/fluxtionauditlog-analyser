# Response to threading/model review — N-1 and L-1

The reviewer closed S-1/S-2 against main `809303f7` and requested a preparation deadline (N-1)
and a rebase preserving main's tracker cleanup (L-1).

**N-1 accepted and implemented.** The built-in bridge's timeout is in this repository:
`mcp/McpBridge.java`, `CALL_TIMEOUT = Duration.ofSeconds(60)`. Java spotlight preparation has a
10-second server deadline, checked using `System.nanoTime()` before apply as well as by an EDT timer.
The timer completes the refusal while a read remains blocked; expiry invalidates the request ticket.
Neither a terminal future nor an expired/superseded ticket may publish a view, model or binding later.
Worker interruption is best effort, never the correctness boundary.

`preparationDeadlineRefusesBeforeReadReturnsAndLateCompletionCannotLight` holds the actual archive
resolver's discovery monitor past a shortened test deadline. The refusal arrives before the monitor is
released. After release and worker completion, nothing is lit. Removing the terminal, elapsed-deadline
and expiry-ticket guards makes its late-publication assertion fail; the exact output is in the
[mutation record](evidence/java-source-spotlight-2026-09-23/mutations.json).

Waiting-caller interruption is request-scoped too, including interruption before EDT capture. It cancels
the pending future, preserves the thread's interrupt flag, and does not clear a newer request's spotlight.
`interruptedCallerCancelsOnlyItsOwnPreparation` exercises the blocked-read path through ActionExecutor.
A disconnected third-party client with a timeout below ten seconds is not observable as thread cancellation;
that limitation is stated in the proposal, guide and report. No claim about arbitrary client deadlines is made.

**L-1 completed.** The still-unpublished feature branch was rebased from `401da35b` onto `809303f7`.
There was no conflict. `git diff origin/main -- docs/specs/tracker.md` was inspected immediately after
rebase: only the new spotlight section was added. Archived items and completed statuses were preserved.
Proposal packet commits were replayed, not force-pushed over the published documentation branch.
Implementation uses `/private/tmp/analyser-java-source-spotlight`; the primary checkout was not switched.

See the [implementation report](report_java_source_spotlight_2026_09_23.md) for final gates and the
[review brief](brief_review_java_source_spotlight_2026_09_23.md) for independent attack instructions.
