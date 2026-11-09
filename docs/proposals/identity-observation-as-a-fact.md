# Proposal — the observed file identity as a session fact (context's identity branch 1)

**Status:** proposed 2026-09-28, from readable-surfaces step 2. **Not implemented.** The step-2 brief kept branch 1
out of scope, and this is why it should change, and how.

## What branch 1 does today

With Follow off, `context` calls `observeReadIdentity()` (`MainFrame`), which does three separate things:

1. **Reads the file's identity now:** `store.readThroughIdentity()`. That is a real observation, fresher than any
   verdict the session holds (D-E6), and it carries `readsSuspended`, which the session does not hold at all.
2. **Reports it to the session**, but one EDT turn later (`invokeLater` posting `LogIdentityObserved`), so the reply
   `context` returns and the verdict the session publishes disagree for one turn.
3. **Writes the status bar by hand:** `status.setText("⚠ " + displayName(...) + ": " + identity.reason())`, at the moment
   of observation.

The dispatcher performs the same observation before any record-reading verb, and the window does it on focus.

## Which parts belong to the session (rule 9)

- **(1) is adapter work and stays there.** Reading the file is an effect. The adapter performs it and reports the
  result.
- **(2) is a hand-placed dispatch.** Whether a surface or a reply may state the fresh observation is a staleness
  decision, and it is being made by *when the adapter happens to post*.
- **(3) is a second composition site of the status line.** The status line has a view node, and this text bypasses
  it. It can also overwrite the view's text with a sentence the audit never records as rendered.

## Proposal

1. **A request fact and an effect.** `ReadIdentityObservationRequested(reason)` is posted by `context`, the dispatcher
   and window focus. The session asks for `ObserveReadIdentityEffect`. The adapter performs it and answers with
   `LogIdentityObserved(generation, verdict, reason, suspendsReads)`, carrying `suspendsReads` as a fact.
2. **Answered in the same dispatch.** The effect is performed at the batch end of the requesting cycle, as the scan
   is, so `context`'s reply reads the session's state *after* the observation, and there is no one-turn gap.
3. **`readsSuspended` becomes session state** (`OpenLog`), and so available to the banner view. Then `context`'s
   branch 1 is a projection too, and the dispatcher's refusal reads the same value.
4. **The hand-written status text goes.** The status line's view already states the file's identity through its
   `reopenedReason`. A changed-in-place file is a view change, drawn by its backends and recorded as rendered.

## Costs and risks

- **Every `context` call and record-reading verb posts a fact.** That means one more record per call in the session
  audit, a record that mostly says "unchanged". It should be classified with the other observation kinds
  (`SessionAuditSink`'s observations ring), or its absence-when-unchanged kept. That is the signal-to-noise question
  readable-surfaces step 4 asks to measure first.
- **Timing:** the effect must be performed synchronously within the verb's EDT turn, or the reply loses its
  freshness, which is the one thing branch 1 exists for. The scan's batch-end pattern shows it can be.
- **Scope:** it touches the dispatcher's read policy, which M69's review response (PR #57, R1) has just changed.
  Sequence it after M69 lands.

## Acceptance, if adopted

- `context` never composes `log.identity`: all three branches project session state.
- A file changed in place is refused by the dispatcher, stated in `context` and drawn on the status line **in the same
  EDT turn**, from one session value.
- No `status.setText` for identity outside the status line's backend. `OneDispatchModelTest` can assert that.
