# M81 C2 — an interrupted request is not lost

This extends C1 commit `d4f03d4ce1b9fccc42f952df53f4e4c101be4e6b` in the same C delivery, based on main
`5e770067863d8f150dd25340723037831382dc55`. It does not integrate PR #171. A, B and D remain unimplemented,
and no owner answer to OQ-1 through OQ-8 is inferred.

The node retains the interrupted ticket's own send reference. If the folder/mode no longer permits resuming,
it discloses the reason, starts a new conversation and requests an unsent recovery draft. An explicitly unknown
resumed id falls back once; generic launch/auth failures do not. Successful resume consumes the old recovery
reference, so a later voluntary New chat cannot resurrect it. The adapter composes the person's words verbatim,
a blank line, and the fixed interrupted-part-way reminder through the normal draft-composition path. Nothing
is sent until Send. The visible word is "mode", not the draft's "posture", to preserve the existing user-language
contract.

The node also requests the startup clock and publishes its SPAWNING/HANDSHAKE/BINDING step and elapsed data.
Ticks carry generation/ticket and never masquerade as stream events. The adapter stops scheduling when the
published scope is no longer current. Feature A will render this same activity line and extend it to live turns;
no live-tool or stall-cue acceptance is claimed here.

## Named red / green witnesses

`LocalAgentRecoveryDraftTest` drives the generated processor. Before implementation, the changed-folder,
unknown-resume, Stop-deadline draft and startup-clock methods failed; all six methods pass after implementation.
Its startup-clock case covers each starting phase and rejects a replaced generation's tick. Generic failures do
not auto-retry. An added final-readback assertion, **successful resume consumes the old interrupted request**,
failed before clearing the reference on successful binding and passed after the correction.

The starred C5 witness is `theStartDeadlineRendersItsOwnTimeoutBanner`: replacing the timeout sentence with
the generic exited sentence failed **TIMEOUT has its own banner, not EXITED or ALREADY_RUNNING**. Restoring
the distinct 35-second sentence restored green; the test also renders the actual panel and finds that banner.
The starred C1/C2 witnesses remain recorded in [C1 evidence](m81-c1-restart-evidence.md).

`ResumeErrorObservationTest` has three passing adapter checks: narrow vendor-prefix recognition; a virtual
clock that ticks during an uncompleted handshake and stops with its scope; and a fixture process that exits a
resumed handshake with the unknown-id error, producing exactly one fresh conversation and recovery effect.

The existing `ClaudeCodeTabFrameTest` gains `aRecoveryDraftKeepsThePersonsWordsAndIsNeverSent`. It runs under
an isolated home with only fixture/stub executables. It asserts the original DEMO request plus fixed reminder is
in the composer with zero process/user sends, then explicitly presses Send and observes exactly one user send.
Changing the composition to keep only the reminder made **the original request is placed verbatim and unsent**
fail; restoration passed. The actual display suite ran **2 tests, zero failures/errors/skips**, with the display
lock held and both CI headless flags overridden. An earlier skipped invocation and a sandboxed JVM abort are
not counted as display acceptance. This is an existing registered FrameTest, so no CI suite-list addition is needed.

## Protocol evidence and limits

The public [`@anthropic-ai/claude-code` 2.1.282 distribution](https://registry.npmjs.org/@anthropic-ai/claude-code/2.1.282)
wrapper explicitly maps a child signal to `128 + signum`. The matching public
[Darwin-arm64 package](https://registry.npmjs.org/@anthropic-ai/claude-code-darwin-arm64/2.1.282)
contains the exact `No conversation found with session ID: ` error
prefix. The adapter recognises only that leading prefix, only for a resumed process; similar prose and generic
failures do not qualify. This is public distribution-source evidence, not a live CLI probe: the real CLI's stderr
routing for this error was not exercised. The fixture exercises the adapter's stderr path. No live model,
paid provider, participant project, real assistant configuration or credentials were used.

The official streaming documentation mentions a first non-ping event, but that does not establish a whole-session
heartbeat during Bash. OQ-4 therefore remains evidence work for A, not a claim that CLI pings do not exist.

## Verification

`mvn -Pregen process-classes` succeeded after the final node correction. Generated Java/resource copies and
GraphML were generated, not edited. Final full `mvn test`: **602 suites, 4,270 tests, zero failures/errors,
349 headless skips**. The display rerun after that final node correction also passed **2 tests, zero skips**.
Preflight: **73 frame suites, 688 anchors**. The repository tracked-plus-addition sweep, changed-file private-path
sweep and `git diff --check` printed nothing. No site files changed, so strict MkDocs is not applicable.

The owner has since answered the policy choices for later deliveries: no timed stall repetition after Keep waiting;
one whole-set unticked script checkbox with each script digest-bound; POSIX-first B without Windows pre-allow;
four-second output polling; and explicitly accepted local output offers available with exchange off. Those decisions
and the draft's contradictory stall sentence are to be recorded in the next feature branch. They do not widen C.
