# Onboard assistant, persistent chat window and conversation journeys

**Status (2026-10-01):** implemented and released in 1.29.0 (PR #77, including review R1–R5 and the R1c idle-filter correction); acceptance remains open — the authorised live-provider run, native input and IME, the journey's native screenshots and a cross-machine recipient (tracker ▸ *Onboard assistant and conversation journeys*).

**Status: PROPOSED r1 — 2026-09-29.** Documentation only; no implementation or acceptance is claimed.
**Source baseline:** main `ba8b601b`. The Start-page integration must also incorporate PR #71's final merged labels and navigation; that PR was not part of this baseline.

## 1. Outcome and owner direction

A person can ask the onboard assistant a question, keep chatting while it changes the analyser's views,
inspect the evidence behind its answer, and save an explanation as a spotlight walk. Someone else can
open a journey-specific `.fexp` from the website and watch a clearly labelled conversation alongside
those same analyser views. They can then ask their own question with the onboard assistant or connect
a CLI assistant.

The owner requires **both a fully working live onboard assistant and an optional popout window**.
A simulated chat player alone does not fulfil this feature. The owner also wants simulated conversation
and analyser effects captured together in walks, with journey-specific bundles available from the site.
The `.fexp` extension remains unchanged. The browser is a distribution surface; no browser player is required.

This is a proposed implementation contract, open to independent review. OA-D identifiers below are
proposals, not additional owner approvals. Existing evidence, identity, root-grant and read-refusal
contracts remain binding.

### First complete delivery

1. A useful live assistant through the existing configured provider routes: send a question, run the
   bounded analyser action loop, see results and refusals, follow up, cancel, and start a new conversation.
2. One conversation model with docked and popout presentation. Navigation must not hide a popout or reset a conversation.
3. One DEMO investigation with synchronised conversation and actual spotlight-walk views, including Back,
   Next, resume, refusal, and a handoff to asking a fresh question.
4. A capture/editor path attaching selected conversation turns to saved steps, plus a verified `.fexp`
   and its download page. A hand-authored mockup or local JSON edit is not the completed authoring path.

Delivery can use separate dependent PRs, but none may describe the complete feature as shipped before
all four exist. Model streaming, a catalogue of many journeys and a browser viewer are not prerequisites.

## 2. What exists (READ, not newly exercised)

| Source / symbol | Observed implementation | Consequence |
|---|---|---|
| `ui/LlmPanel` (`onSend`, `runRound`, `onReset`) | Docked transcript/input, Send, Cancel, Copy prompt, Reset; the panel owns `Conversation`, cancellation and manifest state, chooses rounds and dispatches parsed actions. Context is attached to the first turn; actions require a loaded store. | This is an existing assistant to complete/refactor, not a blank feature. Moving the component alone leaves lifecycle and stale-result problems unaddressed. |
| `llm/LlmClient`, `AnthropicClient`, `OpenAiClient` | Two configured provider adapters; blocking text completion off the EDT; connect/request timeouts; no streaming interface. | Reuse adapters initially. Do not promise native streaming, CLI-account login or newer API capabilities without implementation and tests. |
| `llm/Conversation`, `PromptBuilder`, `ActionParser`, `ActionDispatcher` | Ordered messages, bounded prompt construction, fenced action parsing, shared verb dispatch and read-identity refusal. | Keep one action contract for onboard and external clients; do not create a chat-specific graph or record reader. |
| `MainFrame` (`llmPanel`, `bind`, `explainSelection`) | One panel installed in the side tabs; Explain selects it and primes the question. | Detachment needs an assistant-host abstraction so Explain reveals the right host without blindly selecting a removed component. |
| `walk/WalkSpec`, `WalkSteps`, `ui/WalkPresenter`, `session/node/WalkPlayback` | Saved steps bind views/targets to evidence; the node owns playback; the presenter applies transient views and reports readiness. No dialogue field today. | Extend the walk; do not put another player and step counter in the chat window. |
| `bundle/BundleProfile` | Walks already travel with a restricted profile. Paths are refused or redacted by field meaning; external chart data is excluded. | New conversation fields need explicit persistence, export and privacy handling; an arbitrary transcript in `extras` is not an adequate contract. |
| `bundle/EvidenceBundle` | Exact manifest-byte identity; integrity is not sender authentication; formats 1 and 2 distinguish investigation and replay. | Dialogue is not replay data. Its presence must never imply replay was run or an incident reproduced. |

Framework basis: READ the current [Fluxtion reference](https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt)
and the generated `session/generated/SessionProcessor` walk handlers. Follow CLAUDE rule 9: decisions
live in nodes, effects perform work, facts report outcomes. No claim here depends on guessed re-entrancy.
Implementation must reread the relevant reference and provider documentation if those contracts change.

## 3. Presentation: one assistant, two hosts (OA-D1)

The assistant header always states its mode: **Live assistant**, **Simulated conversation**, or
**Recorded conversation**. Mode is text and an accessible name, not colour alone. In live mode it also
shows the configured provider/model and a short current project/log identity. Names are configuration,
not a promise that a particular model is available.

The docked tab has **Pop out**; the modeless popout has **Dock** and **Show analyser**. Reparent one
view, or render a single immutable conversation projection in alternative hosts: either way there is
one composer, one draft, one conversation and one active request. Opening another host must never send
again or create a second provider conversation. A docked placeholder may say **Assistant is in a
separate window — Show / Dock**; it must not be another live input.

- Closing the popout docks it. It does not reset chat, cancel a request or exit the analyser.
- Popout/dock preserves draft, transcript, selection and scroll position; new content scrolls only
  when the person was already following the bottom. Switching the analyser tab does not raise, hide,
  minimise or refocus the popout. App exit cancels pending work and disposes both hosts.
- The popout is resizable, not always-on-top and not modal. Restore its bounds only within current
  usable screen bounds; a removed monitor must not strand it. Persist host preference/bounds in the
  machine tier, never the project or a bundle. Chat text remains memory-only in this delivery.
- Explain reveals whichever host is active and primes the draft without sending. It must not overwrite
  an existing draft. The host's Show analyser action brings forward the evidence window explicitly.
- At 1200×800 and narrow layouts, the composer and Send/Cancel remain reachable. Transcript and tool
  details scroll separately from the composer. Support both themes, keyboard-only operation and
  accessible names for mode, status, turn author, tool outcome and host actions.
- Enter sends, Shift+Enter inserts a newline; allow IME composition to finish before interpreting Enter.
  Left/Right in the composer edit text, never advance a walk. Explicit Next/Back in the chat header and
  overlay share the existing walk navigation facts. Global arrow grabs must not take the chat's typing.

A popout click must be classified as an assistant interaction, not as a random outside click that ends
the walk. This is a **narrow proposed extension** of M69's outside-press rule, not a blanket exemption
for every application window. Selecting/copying demo text and scrolling it preserve playback; a live
request or another evidence-changing action ends it through the session. Test with actual window routing.

## 4. Live assistant contract (OA-D2)

### 4.1 Useful end to end

The onboard assistant uses the same validated analyser verbs as the external bridge. At minimum the
acceptance journey queries context and counts, reads a cited record, draws/selects a chart, spotlights
its evidence, and answers a follow-up. Every tool reply shown is the actual structured result, including
refusals, identity qualifications, partial results and scope. Model prose is labelled as an answer,
not as a verified verdict. Success means the requested UI effect really occurred, not just that a
provider returned words saying it occurred.

A project/topology with no log still supports applicable `context`, topology/source navigation and
setup assistance. Record-dependent tools give their ordinary no-log refusal; lack of a store must
not disable the entire action manifest. Keep one schema source with external/onboard manifest parity.
Reuse the existing `analyser-action` protocol for the first delivery; provider-native tool calls are
not required and must not create a second dispatcher if added later.

With no usable provider configuration, show **Configure provider**, **Copy prompt**, and **Connect a
CLI assistant**. Do not pretend a request was sent. Copy prompt remains an explicitly local clipboard
action; it is not a conversation turn from a model and must not become bundle content by default.
Onboard provider credentials are distinct from the Fluxtion compilation key and from a CLI subscription.
No automatic credential import or sharing between them is proposed.

### 4.2 Turns, actions and cancellation

- Capture one immutable request input: draft, accepted history, configured route and limits, selected
  record references, and current session basis. UI observations are captured on the EDT; expensive
  record/source assembly and network work run off it and return correlated facts. Observe the existing
  grant and identity guards. Do not carry a mutable `AppConfig` or live conversation into a worker.
- One active live turn at a time. Disable Send while pending, keep the next draft editable and do not
  silently queue it. A turn may contain multiple bounded rounds and actions. Validate configured caps
  and enforce both per-reply and per-turn budgets; the final reply states an exhausted limit.
- Show **Requesting**, **Running action n**, **Waiting for result**, **Complete**, **Cancelled**,
  **Superseded**, **Limit reached**, or **Failed**, as applicable. Non-streaming completion is acceptable;
  a progress state is required. Simulated typing is not evidence that a live provider is streaming.
- Execute actions in order. Before EACH action, the session checks its turn ticket and basis. Feed
  every action result back into the loop when another round is allowed, including successful render
  results and their warnings. Never promote a requested-but-refused effect into a success caption.
- Cancel invalidates the turn immediately and requests transport cancellation. A late provider reply,
  error or tool result cannot append as current, start another action, clear a newer busy state or
  navigate. Cancellation does not roll back already completed edits; show which actions completed.
  Do not auto-retry a mutating action after a timeout or uncertain completion.
- A new chat invalidates any pending turn before clearing its view. A cancelled/failed turn has a
  visible terminal record, and a later Send can succeed. Provider errors are safely summarised;
  neither credential headers nor unbounded raw response bodies belong in transcript/errors.

### 4.3 Basis and authority

Bind the live turn to conversation ID, turn ID, project identity, log generation, and relevant graph /
view revisions, with the same qualifications the session already knows. This list is an implementation
input, not permission to invent a second identity rule in `LlmPanel`.

A project/log change or contradictory external view change supersedes outstanding work. Freeze the old
thread for reading, label its basis, and offer a new thread for the new workspace. Old history is not
silently sent as current evidence. Navigation that merely reveals a different tab or relocates chat
keeps the conversation. The node classifies these cases. An authorised tool's own expected change is
correlated to its ticket so it does not cancel itself; after that result, refresh context from the
accepted state before the next round. Follow and file-identity changes use existing session facts;
never substitute pathname equality for current evidence identity.

R1 implementation: the shared dispatcher carries the assistant ticket and action identity on an
open request, through its reader, and into its asynchronous completion. A different client's open
request supersedes the turn immediately; the late assistant result cannot resume it. The assistant's
own open continues only after the frame reports that the accepted log, source graph and reset view
have all been applied. The session basis includes the investigation filter key (range, dimensions,
text and grouping). An external filter change supersedes a pending reply or action; a filter change
made by that action keeps its turn. An external filter change while idle refreshes the basis without
freezing a completed conversation; the next Send prepares context for the current filter. Project,
log and graph changes still freeze idle conversations. Tab navigation remains a reveal, not a basis change.

The live assistant receives no extra file grants, export paths or server powers. Existing tool guards
and user decisions apply equally onboard. Destructive actions, external writes and root changes are
not unlocked by simulated messages. Network transmission starts with the person's Send, not opening
chat, opening a bundle, playing a journey or reading an imported transcript. The UI states the selected
provider and that request context is sent there. Raw logs, sources and imported text are data, not new
policy instructions. No shell, arbitrary URL fetch or producer execution is added by this feature.

## 5. Ownership and event flow (OA-D3)

Add assistant lifecycle node(s) to the generated session processor, alongside the existing walk node.
Refactor decision logic out of `LlmPanel.runRound`; a detached copy of that loop is explicitly excluded.
`Conversation` can remain a data type, but mutation of live state has one owner. Large transcript bodies
may be an immutable append-only store referenced by snapshot IDs; turn order, accepted revisions,
status and visibility remain session-owned. Do not copy a growing transcript into every unrelated snapshot.

Proposed vocabulary, to refine against existing facts during implementation:

| Request/fact | Node decision | Effect/result |
|---|---|---|
| AssistantSendRequested | Validate route, idle state, budgets and current basis; allocate ticket | PrepareAssistantContext → ContextPrepared/Failed |
| ContextPrepared | Accept only the active ticket/basis | RequestCompletion → CompletionReceived/Failed |
| CompletionReceived | Accept, parse/validate action proposals, decide next allowed action or finish | DispatchAssistantAction → AssistantActionFinished |
| AssistantActionFinished | Accept actual outcome; decide next action/round/end and refresh basis if needed | Next effect, immutable transcript projection |
| AssistantCancelRequested / NewChatRequested | Invalidate ticket before further effects | CancelProviderRequest; terminal state |
| Existing project/log/identity/view facts | Decide continue, supersede or qualify | Cancel pending work / render changed basis |
| AssistantHostRequested | Change docking presentation only | Move/show view → HostChanged/Failed |
| Walk play/navigation/preparation/end facts | Existing WalkPlayback decides step and validity; dialogue projection follows its frozen definition and accepted ticket | Render conversation prefix and walk status |

Adapters own transport handles and Swing components, not continuation decisions. A worker may stop
obsolete I/O as an optimisation; node rejection is still the correctness guard. An effect must not race
between checking a ticket and performing a UI mutation: validate/apply through the same serial session/
EDT boundary used by the action. Long-running reads report a basis-bearing result and revalidate on
publication. No busy-wait on `loadInFlight` and no blocking provider call on the EDT.

Regenerate the session processor through the existing supported build route. Inspect generated dispatch
against the nodes. Add OneDispatchModel checks for the new UI/presenter paths; handwritten calls between
node handlers and duplicate status composition are not allowed.

## 6. Conversation journeys are walks, not recorded commands (OA-D4)

A journey combines a saved walk with optional dialogue. Existing walks without dialogue work unchanged.
Normal playback still writes no project/settings/chart/focus definitions and retains M69's whole-view
validation, identity checks, settled-layout checks and refusal behaviour.

Two distinct things are shown side by side:

- **Saved narrative:** scripted or captured user/assistant words, explicitly attributed and never claimed
  to have just been produced by a live model.
- **Current presentation result:** what this analyser actually showed, with current/historical/unresolved
  target states, availability and reasons from the existing walk snapshot.

Playing does not interpret message text or tool-shaped text as actions. Its real analyser effects are
M69's allow-listed transient view changes and spotlight resolution. A chart created during authoring
travels as a saved definition/result in the bundle; the walk selects it under the existing open/closed
rules. A saved tool result may be displayed as **Recorded result — not re-executed**. It is not a fresh
query. A missing/closed/undrawn chart is disclosed; no silent create/open/edit is introduced to make a
simulated claim look true. Producer replay remains M70's separately initiated, separately qualified work.

### 6.1 Typed format and step binding

Propose an optional, versioned `conversation` object on a walk and stable IDs on dialogue-bearing steps.
Each such step names the last visible turn; multiple steps may reveal the same prefix. Example shape
(not a runnable current verb):

```json
{
  "name": "DEMO first breach",
  "conversation": {
    "version": 1,
    "kind": "scripted",
    "turns": [
      {"id": "t1", "role": "user", "text": "Where did the application first log a breach?"},
      {"id": "t2", "role": "assistant", "text": "Inspect the recorded breach flag and this selected record."}
    ]
  },
  "steps": [
    {"id": "s1", "conversationThrough": "t2", "caption": "Inspect the first recorded breach", "view": {}, "targets": []}
  ]
}
```

View, target and digest validation is still the existing walk contract; the empty placeholders above
are not a complete accepted step. Valid kinds: `scripted`, `recorded`, `edited-recording`. Only actual
captured turns qualify as recorded; editing words changes the kind, disclosed as **Edited recorded
conversation**. Sender/author/model names are declared metadata, never authenticated attribution.

Reject duplicate IDs, missing turn references, out-of-order reveal boundaries, unsupported conversation
versions, invalid roles, excessive sizes and unknown fields inside this version of the conversation schema at authoring/import.
Proposed bounds: existing 50-step maximum, at most 200 turns, 16 KiB UTF-8 text per turn, 512 KiB total
conversation JSON. Validate before partial installation; bounds are explicit configurable product
limits, not claims of secure redaction. Store text as inert plain text or restricted Markdown, with no
active HTML, remote images, automatic links or embedded tool execution.

Bind the conversation to the frozen walk definition/digest, not just its name and numeric step. Editing,
reordering, replacing, renaming or deleting the playing walk follows a node-owned definition-change
transition; stale preparation must not display a prefix from another revision. Rename preserves IDs;
copy/replace has an explicit validated identity. Keep text and step bindings through walk export/import,
project snapshots, global-tier saves, rename/delete/bin restore and bundle extraction.

Use typed keys under the existing walk serialization and REPORTS share category; do not hide the schema
in opaque `extras`. Extend `ConfigStore`, `KnownKeys`, `SettingsShare` export/preview/apply,
`ProjectProfile.Snapshot`, `WalkBin` and `BundleProfile` as required. Keep absent-conversation round trips
unchanged. Readers predating this feature may ignore optional dialogue while opening evidence and an ordinary
walk; that is degraded viewing, not this journey's acceptance. Do not promise that an old binary will
recognise a new minimum-version field. This implementation rejects an unsupported conversation version
on dialogue Play with an upgrade explanation, while offering evidence-only viewing. If safe degradation
cannot be demonstrated on the supported old reader, use an explicitly incompatible envelope rather
than silently misrepresenting a journey. The precise minimum reader version is set at release.

### 6.2 Next, Back, interruption and live handoff

- Play reveals dialogue up to the requested step and shows **Preparing evidence…** until its ticket
  settles. Only then show the actual SHOWN/PARTIAL/NOT SHOWN result; authored claims remain authored.
- Next/Back and direct Play-from-N restore that step's view and prefix, not replay prior chat commands.
  Back neither appends duplicate turns nor executes a provider request. Rapid Next/Back discards stale
  dialogue/geometry results with the same ticket as the walk. Off-screen targets stay unavailable.
- A failed/refused view leaves the prior accepted view as M69 specifies. Label the requested step as
  refused; do not label the prior view as its evidence. The conversation shows the last accepted prefix
  plus the current refusal, rather than a success answer for the failed request.
- Entering demonstration mode suspends live input and leaves any existing live draft/history intact.
  If a live turn is pending, require its completion or explicit cancellation before Play; there is no
  hidden switch. Demo mode has no Send-to-provider action.
- **Ask about this evidence** ends the walk through the session and opens a new live thread with current
  trusted context. It does not inherit fake user turns or a simulated system prompt. An explicit copy
  of a selected passage is possible but remains quoted, untrusted content. **Connect your CLI assistant**
  uses existing connection guidance and grants no new capability.
- Conversation viewing is optional: Show conversation / Hide conversation affects presentation only.
  Walk controls remain usable without it; the strip still identifies the journey and its mode.

## 7. Capture and authoring (OA-D5)

Extend the existing spotlight save workflow: **Include conversation…** offers selected completed turns
from the current live thread or an explicit **Write simulated dialogue** editor. It is never preselected
for a private conversation. The editor maps turn boundaries to stable steps and previews the result.
Do not capture the entire chat when saving one spotlight. Live editing a draft does not rewrite evidence.

Capture step basis, conversation revision and accepted action/result IDs coherently. If the log, walk
or conversation changes before the capture finishes, reject or retry explicitly; never combine a caption
from one turn with another log's record. Incomplete/cancelled turns cannot be recorded as successful.
Tool details are optional, bounded, sanitised display data; recorded commands remain inert. Authoring
may use live tools to create a chart/report, but subsequent presentation must not repeat those writes.

Before export, show exactly which dialogue leaves the machine. Provider keys, bridge tokens, system
prompts, hidden request context, machine paths and provider transport diagnostics are excluded. Existing
BundleProfile path policy must cover every new text/structured field and revalidate references after
redaction. Redaction is disclosed; edited recordings retain that label. No regex can prove arbitrary
prose contains no secret: the preview and explicit selection are required, public examples use DEMO,
and publication still requires the public-data sweep plus visual reading.

A journey may be saved and used without a bundle. Bundling must preserve the walk/conversation relationship
and all existing digest qualifications. Extracting an excerpt that loses a referenced record/chart must
transform or refuse the affected step under the existing excerpt rules and name any resulting dialogue
gap. It must not keep an answer claiming a dropped reference is current.

## 8. Website delivery and first journey (OA-D6)

The first catalogue entry is **Find the first recorded breach**. A short, complete journey:

1. Ask what the DEMO log contains; show records and the actual scope.
2. Ask where the application first logged a breach; show a saved, evidence-backed chart and record.
   Do not substitute the first numerical limit crossing for the application's logged breach.
3. Ask what supports that answer; show the record detail/topology with its pairing/coverage qualifications.
4. Explain the conclusion and offer **Ask about this evidence** / **Connect your CLI assistant**.

Counts, record indices and prose are authored against verified DEMO fixture results; the publishing
check recomputes them. The website transcript is captured from the real journey renderer, not painted
screenshots or hand-written output claimed to be measured. Synthetic dialogue is fine when labelled.

Every catalogue card/page states title, task taught, step count, approximate duration (measured or clearly
estimated), minimum analyser version, download size/hash, bundled evidence, missing source dependencies,
and **No provider needed for this demonstration**. State separately whether replay inputs exist and
that opening/playing never runs them. Link setup for a real onboard or CLI assistant.

Download is an ordinary `.fexp`. Opening verifies and presents it through the existing disposable-copy
flow; it does not auto-run the walk, a model or a producer. Offer **Play journey** after opening. Do not
add a custom extension or silently fetch missing members. Current format 1/2 compatibility must be
proved; add a new envelope version only if actual reader semantics require it, not just for dialogue.

This repo owns renderer, schema, bundle checks and the analyser docs page. If the public catalogue lives
on another site, its publication is a separate delivery dependency with a stable download URL and exact
bundle hash. A working local fixture does not count as published or cross-machine acceptance.

## 9. Acceptance and wrong-result witnesses

All items start **UNVERIFIED**. Each requires a named check; runtime boundaries also require a mutation
that produces the wrong result and fails a named assertion, not an error/skip. Proposed test names are
not claims that these classes exist.

| ID | Observable acceptance | Regression / wrong-result witness |
|---|---|---|
| OA-A1 | Detach, switch Summary→Graph→Topology, continue chatting, dock: same draft/history/request; one Send produces one request. Close docks; app exit cancels. | `AssistantHostFrameTest`; duplicate model on detach yields two sends or lost text. |
| OA-A2 | Actual provider-backed multi-turn question invokes query, chart and spotlight and uses their returned results. | Local fake HTTP server through the real provider adapter + `AssistantLoopTest`; omit render-result feedback, final result assertion fails. Authorised live-provider smoke per advertised route reported separately. |
| OA-A3 | Context/topology tools work with no log; record tools refuse honestly; no-key mode never sends. | `AssistantCapabilitiesTest`; old store-only enable gate fails the no-log context assertion. |
| OA-A4 | Cancel/new chat, then release a delayed success/error; no action, stale text or busy-state overwrite. A fresh Send works. | Latch-controlled `AssistantCancellationTest`; drop ticket guard and assert late action count must remain zero. |
| OA-A5 | Switch project/log/graph/filter while a reply/action is held; old work cannot operate on the new basis. Tool's own accepted change can continue. | `AssistantBasisTest` plus real-frame witness; remove per-action revalidation, wrong chart/log assertion fails. |
| OA-A6 | Detach/theme/resize/monitor-bound restore; keyboard typing and IME do not trigger walk arrows. | Real-frame `AssistantHostFrameTest` and native keyboard probe. No direct-dispatch shortcut for cross-window claims. |
| OA-A7 | Scripted/recorded/edited labels persist in both hosts, imported walkthrough and screenshots. Zero network calls on demo open/play/back/resume. | `ConversationJourneyTest`; drop provenance label or execute text as an action, visible label / zero-call assertions fail. |
| OA-A8 | Steps 1→2→1 and direct resume reveal the exact bound prefix; rapid navigation and replaced definitions cannot mix revisions. | `ConversationWalkPlaybackTest`; remove definition/ticket match, prefix/target pairing fails. |
| OA-A9 | Missing, historical, clipped, closed or undrawn target never becomes SHOWN evidence merely because dialogue claims success. | Existing walk frame witnesses extended for chat; force success before settled geometry, visible status fails. |
| OA-A10 | Normal journey playback leaves project/settings/chart/focus definition bytes unchanged after pending saves settle. | `ConversationWalkPersistenceTest`; invoke ordinary persisting chart open during playback, byte comparison fails. |
| OA-A11 | Native popout copy/scroll and strip Next preserve walk; unrelated outside navigation ends it. Composer arrows edit text. | Real frame/Robot event-route test with explicit focus preconditions; remove assistant-window classification, premature-end assertion fails. |
| OA-A12 | Save selected live turns or scripted turns, edit, rename, delete/restore, share/import, reopen project: IDs/text/provenance remain correct. | `ConversationWalkStorageTest` exercises globalTier and walk-only share paths; omit new keys from a serializer, round-trip assertion fails. |
| OA-A13 | Privacy preview is exact; credentials/hidden context never exported; new fields follow path/redaction rules; limits and unsupported schema fail before partial write. | `ConversationBundleProfileTest` with synthetic secrets/path fixtures; remove field exclusion/redaction/limit guard and assert exported bytes/refusal. |
| OA-A14 | A stranger opens the published DEMO bundle, steps all stops, sees correct dialogue and evidence, without provider or sender paths; own settings unchanged. | Cross-machine recipient run plus existing bundle integrity tests. Recompute published hash; locally isolated homes alone are not this acceptance. |
| OA-A15 | Final handoff starts a real fresh conversation or shows connection setup; no simulated history enters a provider call. | `AssistantJourneyHandoffTest`; reuse demo history, captured request assertion fails. |
| OA-A16 | Budgets, timeout, malformed reply, rate limit/auth error and unknown action terminate visibly; no duplicate write on retry. | Fake-provider deterministic `AssistantLoopTest`; remove action/round cap, bounded-count assertion fails. |
| OA-A17 | Onboard and external tools retain the same schema, scope and identity refusal; assistant state has one human/context projection. | Extend manifest, ContextSections and OneDispatchModel tests; UI-owned continuation or divergent refusal fails. |
| OA-A18 | Catalogue describes the actual downloadable bytes/version and no automatic replay; all screenshots are real DEMO captures read by eye. | Published-artifact download/open check plus docs checks. Cannot close with a branch-only link. |

Tests should use injected provider transports and local fake servers for deterministic CI; do not bypass
actual parsing, adapter or dispatcher boundaries. Never put a real key into a fixture. Live-provider
acceptance requires explicit authorised credentials/cost and records provider/model/build and results;
not running it must be reported as UNVERIFIED, not replaced with a simulated conversation.

Implementation gates: JDK 21 full headless suite (totals/failures/errors/skips and orphan reports),
sequential registered display suites, preflight and targeted new controls, CI's complete mutation gate,
strict MkDocs, link checks, public sweep and native DEMO image review. New FrameTests enter both CI lists.
Record predictions before implementation and retain failed runs. A focus skip is not a visual pass.

## 10. Context, contracts and documentation

Propose an optional `context.assistant` status projection: mode, docked/popped-out host, conversation/turn
IDs, phase, current basis and reason. It exposes neither transcript nor credentials. The assistant header
is its human surface; the assistant user guide documents it. Keep it above the no-log early return.
If added, update ContextSections, schema/manifest tests and size budgets together. Do not create a new
verb merely to detach a window. Dialogue creation/import extends `walk`, with strict operation-field
validation and matching REST/onboard manifests, MCP tests and assistant docs.

Update the assistant guide, walks guide, bundle opening/capture guide, Start guidance and release notes
in the implementing PR. Distinguish live chat from simulated/recorded narrative on every surface. Show
how to connect a CLI assistant without implying it is required to use onboard chat or to play a demo.
Skills updates follow their existing hash-pinned publication process; do not silently change a pinned skill.

## 11. Delivery slices and open review points

| Slice | Scope | Depends on / completion boundary |
|---|---|---|
| OA-1 | Session-owned live loop, coherent context, cancellations, same dispatcher, fake-provider tests | Existing assistant contracts; required before claiming a working new assistant |
| OA-2 | Single chat host, popout/dock, focus/geometry lifecycle | OA-1's single model; real display acceptance |
| OA-3 | Typed dialogue, step IDs/binding, authoring UI, storage/share/bin/privacy | Existing walk schema; can be developed alongside OA-1/2 |
| OA-4 | Synchronized demo rendering, ticketed readiness, Back/resume, live handoff | OA-1–3; preserve M69 no-write rule |
| OA-5 | One DEMO journey, bundle/export, site page and published download | OA-4; native captures and independent recipient |
| OA-6 | Live-provider and release acceptance, docs and CI closure | All slices; no provider run occurs merely to write this spec |

Questions for the independent spec reviewer (not missing owner direction):

- Is extending the existing assistant loop sufficient without changing provider wire protocols? Inspect
  the provider routes against current official docs during implementation; no model-name promise is made here.
- Does reparenting one Swing view meet focus/accessibility needs, or should two hosts render one projection?
  Either must pass the same no-duplication/cancellation tests.
- Are turn-size limits and memory-only live history appropriate? Persistence/restart recovery is a separate
  proposal; this delivery must not quietly write private chats into project profiles.
- Can the optional walk schema stay compatible in the current `.fexp` envelope? Demonstrate old-reader
  behaviour, not just unknown-key preservation. Minimum-version disclosure alone does not prove it.
- Where should the first catalogue page be published? The renderer and content can be reviewed here,
  but external site deployment is not implied by approving this spec.

## 12. Revision record

| Revision | Date | Change |
|---|---|---|
| r1 | 2026-09-29 | Proposed from the owner's live-assistant + optional popout + conversation-walk + website-bundle request; checked against main `ba8b601b`. Adds explicit live/demo separation, single session ownership, capture/share limits and acceptance. |
| r1 implemented | 2026-09-29 | Implemented on `feat/onboard-assistant-journeys` (OA-1–OA-5) with six deviations, each listed with its reason in [RESULTS](../handoff/evidence/onboard-assistant-2026-09-29/RESULTS.md): basis = project/log/graph; an independent per-turn budget; an unowned popout; the docked-host exception on the overlay; recorded tool details not captured; no streaming. Status stays PROPOSED until review. |

### Checks performed while preparing r1

Only this specification and the new tracker section changed. Source observations above are READ;
no new feature, provider call, generated processor or bundle was implemented or exercised.
`mvn -o -q test` on JDK 21: **2836 total / 0 failures / 0 errors / 170 skips**, 378 source-mapped
reports and no orphans. Included `SpecLinksResolveTest`: **3/0/0/0**. The first sandboxed run had
29 local socket-permission errors (2836/0/29/170); the unchanged run with loopback permission passed.
`mkdocs build --strict`, `git diff --check` and the public-data sweeps were clean. These are document
and existing-baseline checks, not completion of OA-A1–OA-A18. No display or mutation gate was run.
