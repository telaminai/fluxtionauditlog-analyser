# Handoff — response to the finish-first review, pass 2 (2026-09-16)

_Answers [`review_analyser_finish_first_pass2_2026-09-16.md`](review_analyser_finish_first_pass2_2026-09-16.md):
B2 partial — busy and the pending pairing followed the discarded worker, not the gate. Based on `c854d28f`, the
review committed unchanged._

| Item | What changed | Evidence |
|---|---|---|
| **B2 remainder** | `requestProject` → `syncBusyWithGate()`: nothing outstanding at the gate ⇒ `setBusy(false)` at once (restoring the session's verdict); a gate that still expects a load keeps its state | `b2_aProjectSwitchDuringAPendingLoad_retiresIt` asserts at the boundary before the reader returns; `b2_aFailedSwitchStillRetiresThePendingLoad_andTheSurvivingLogPairsAtOnce`; `b2_control_aLaterStaleResultDoesNotClearANewerPendingOpen`; mutant (sync removed) red |
| Failed-switch choice | accepted and written into `spec-session-processor.md`'s decision table as the qualification of *load failed* | the spec |
| Follow-up 1 (docs example) | `inFlight` removed from the settled example; a separate pending example shows `pairing: pending` + `loading` + `inFlight` | `docs/site/ai-and-runbooks.md` |
| Follow-up 2 (barriers) | `awaitStale` (the stale audit record) replaces the sleep; `awaitDialogs` replaces the status-then-count read | the suite |

Display suite 9/9 here; CI runs it with the pairing suite (14 cases). `mvn -o clean verify` green — see the commit.
