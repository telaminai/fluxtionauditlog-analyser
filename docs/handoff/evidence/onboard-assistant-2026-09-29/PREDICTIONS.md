# Onboard assistant and conversation journeys — premises checked, decisions, predictions

Recorded **before any implementation**, against `bce667f5` (main, PR #71 merged) plus the proposal `8522d275`
(cherry-picked unchanged as the branch's first commit). Spec: `docs/specs/spec-onboard-assistant-journeys.md` r1.
These are predictions and design choices, not evidence. Misses are recorded in RESULTS, never by editing this file.

## Spec premises, checked against the source (READ)

| Spec claim | Finding | Consequence |
|---|---|---|
| §2: `LlmPanel` owns the loop, cancellation and manifest state | **Confirmed.** `onSend`/`runRound` decide rounds; `cancelled` is a volatile flag; a reply that returns after Cancel still appends and still dispatches its actions (no ticket). | OA-1 moves every decision into a node; the panel renders. |
| §4.1: lack of a store disables the whole manifest | **Confirmed.** `actionsOn = assistantActionsInProcess && store != null`. The REST bridge instead refuses record verbs lazily ("no log loaded") and keeps context/topology/source. | Onboard adopts the bridge's construction: one dispatcher shape, record verbs refuse without a log. |
| §4.2: render successes are not fed back | **Confirmed.** `feedback` is true only for errors and `result` payloads. | Every action result is fed back when another round is allowed. |
| §3: a popout click must be classified or it ends a walk | **Half true.** `SpotlightOverlay` covers the MAIN frame and dismisses on any press inside it — including a press in the docked assistant tab. A separate window's presses never reach it. | The narrow extension applies to the DOCKED host (the overlay passes presses inside the assistant host through, by `contains`, so routing stays native). The popout needs no exemption; a native Robot test proves both. |
| §5: effects are performed by the adapter | **Confirmed, with a constraint the spec does not state:** `SessionDriver` is single-in-flight and EDT-confined, and render verbs (`open`, `walk`, …) submit to the driver themselves. An action run INSIDE an effect would violate single-in-flight. | Actions run as the REST bridge runs them: from a worker, with the node deciding before each action and a guard evaluated inside every `ActionExecutor` EDT hop (below). |

## Decisions (routine; documented, reviewable)

- **D1 One node, `assistantLoop`.** Owns conversation id, turn ticket, phase, round, per-turn action count, the
  ordered entry list (ids, kinds, statuses — never text), provider history (entry ids), basis, host (docked or
  popped out), whether the thread is frozen, and the refusal reason. Text lives in an append-only
  `AssistantTranscript` keyed by entry id, outside the graph, so no transcript or credential ever enters the
  session audit record or a snapshot.
- **D2 Effects.** `PrepareAssistantContext` (selection captured on the EDT, record/source assembly off it),
  `RequestAssistantCompletion` (a worker through the real `LlmClient`), `RunAssistantAction` (one action, on a
  single serial worker), `CancelAssistantTransport`, `ShowAssistantHost`. Each is answered at once by
  `AssistantEffectStarted`; the outcome is posted later on the EDT and carries the ticket.
- **D3 The race guard.** The node emits one action at a time, only while its ticket is current. The worker runs it
  with a thread-bound guard that `ActionExecutor.onEdt` evaluates INSIDE each EDT task before the body: a
  superseded ticket throws there, so no Swing mutation can follow a Cancel. The node still rejects every stale
  result (the correctness guard); the EDT check is what stops the effect, as §5 requires.
- **D4 Budgets.** Per-reply cap `maxActionsPerReply`; rounds `maxActionRounds`; per-turn cap derived as
  their product (no new setting). Hitting any states which limit in the terminal entry.
- **D5 Basis.** Project profile path, log open + generation, graph open + revision. While an `open` action of the
  current turn is running, a basis change is attributed to it and the basis is re-read after its result; any
  other change supersedes an active turn and freezes an idle thread ("start a new chat"). View/filter changes do
  NOT supersede: actions address absolute records and every result carries its scope. This narrows the spec's
  "contradictory view change" and is listed as a deviation.
- **D6 Credentials.** Events carry a route (provider, model, whether a key exists, caps) — never the key. The
  adapter reads the key when it performs the request. Provider error bodies are bounded and never include headers.
- **D7 Hosts.** One `AssistantPanel` component is reparented between the side tab and an UNOWNED `JFrame`
  (an owned window would always float above the analyser, contradicting "not always-on-top"). The docked slot
  shows a placeholder with Show / Dock while popped out. Close docks. Bounds and preference are machine-tier.
- **D8 Dialogue schema.** `WalkSpec.conversation` (version, kind, turns) and `Step.id` / `Step.conversationThrough`,
  serialised as typed `walk.N.conv.*` and `walk.N.s.J.id|through` keys. An older reader keeps them as extras and
  plays the walk without dialogue (degraded viewing); to be DEMONSTRATED with the released 1.27.0 jar.
- **D9 Playback.** The dialogue prefix is rendered from `walkPlayback`'s frozen definition and step; the node gains
  the last ACCEPTED step so a refused step shows the accepted prefix plus the refusal. A dialogue walk cannot start
  while a live turn is pending; demo mode has no Send; "Ask about this evidence" ends the walk and opens a fresh
  thread through the session.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| P1 | The node, effects and facts regenerate through `-Pregen` with no hand edit; `GeneratedSourceIsPublishableTest` stays green. | 80% |
| P2 | A late provider reply after Cancel, New chat or a workspace change appends nothing, runs no action, and leaves the terminal state: the fake-provider test shows 0 late actions, and dropping the ticket guard makes it ≥ 1. | 85% |
| P3 | With no log, `context` and `topology` succeed onboard and `aggregate` refuses by name; the old store gate makes the context assertion fail. | 85% |
| P4 | One Send across pop out → switch three tabs → dock produces exactly one provider request, and the draft and transcript are byte-identical after the round trip. | 75% |
| P5 | A native Robot click in the popout, and a scroll/select in the docked host, leave a showing walk running; a press elsewhere in the frame still ends it. | 60% |
| P6 | Walks with dialogue round-trip through the project profile, the global tier, share export/import, the bin and a bundle; a walk without dialogue serialises byte-identically to today. | 80% |
| P7 | Playing, stepping back and resuming a dialogue walk makes zero provider requests and leaves the profile, settings and chart definition bytes unchanged. | 80% |
| P8 | The released 1.27.0 jar opens a journey bundle and plays its walk without the dialogue, and does not fail. | 65% |
| P9 | The full headless suite stays green (total rises; failures 0; skips move only by new frame suites). | 80% |
| P10 | A paid live-provider acceptance run is NOT performed (not authorised); OA-A2's live half and OA-A14/OA-A18's published and cross-machine halves remain UNVERIFIED. | 95% |
