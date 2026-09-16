# Handoff — response to the finish-first review, 2026-09-16

_Answers [`review_analyser_finish_first_2026-09-16.md`](review_analyser_finish_first_2026-09-16.md) (M44.3 NOT READY:
B1, B2, B3; F4 pre-existing; F5, F6 follow-ups). Based on `5be00dc4`, the review committed unchanged. Author
verification, kept distinct from the independent pass._

| # | Review said | What changed | Evidence |
|---|---|---|---|
| **B1** P1 | arrival effects ran before the operation's audience was restored; a Recent-GraphML click mid-load made a socket arrival modal | `onLoaded`/`onLoadFailed` set the audience from the operation's request BEFORE submitting the result | `b1_aHumanRecentGraphmlDuringAPendingSocketLoad_…` 0 dialogs; control `b1_control_aHumanArrivalStillWarnsAsADialog` 1 dialog; mutant red on both |
| **B2** P1 | a successful project switch left the superseded open "opening …" forever; busy never cleared | the gate retires `inFlightWhat` on `OpenProjectRequested`; an old completion cannot clear a newer pending load | `b2_aProjectSwitchDuringAPendingLoad_retiresIt` (no `inFlight`, no `loading`, status "Discarded …"); controls: same project and bad path leave the load alone; replay `aProjectRequestRetiresThePendingOpen`; mutant red |
| **B3** P2 | a superseded failure overwrote status and showed a dialog | `onLoadFailed` applies the refusal: audit record kept, nothing presented | `b3_aSupersededFailure_isRecordedButNeverShown` (0 dialogs, status unchanged); control `b3_control_anAcceptedHumanFailureStillWarns` 1 dialog; mutant red |
| F4 P2 pre-existing | export reopened as one merged record | `SessionAuditSink.export` frames every record with `---` | `exportRoundTripsThroughTheReader`: store size == records, pending and stale as separate records |
| F5 | the mapping test did not pin the `ChartPanel` call site; mutant claim imprecise | `ChartNotesCallBoundaryTest` paints a real panel at fractional bounds and reads the rule's pixel column against `xToPx`; the exact mutation is recorded in the ledger and the over-claim withdrawn | the test |
| F6 | close does not supersede a pending open — decide the policy | filed as tracker **M44.3b**; not changed here on purpose | tracker |

Also: `context.inFlight` is now on the AI page's `context` example; the Project panel does not render it (stated).

## Verification record

Display suite: `AsyncOpenInterleavingFrameTest` 7/7 and `PairingDuringLoadFrameTest` 5/5 on this machine; the CI
`ui-frame` job runs both. Headless `mvn -o clean verify`: see the commit message. Mutants B1/B2/B3 each run against
only their cases and restored, diff-checked.

## Open, stated

- A FAILED project switch still supersedes the pending load (the request, not the outcome, moves the gate). Stated
  as a choice for the reviewer.
- M44.3b (close/reset during a pending open) awaits a decision.
