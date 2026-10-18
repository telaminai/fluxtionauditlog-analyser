# Review — M77 r1, the Set up AI wizard

**Reviewer:** independent agent (did not write the spec). **Short id:** 7f3c. **Date:** 2026-10-18.
**Subject:** `docs/specs/spec-ai-setup-wizard.md` DRAFT r1 at `d5d199c8` (branch `docs/m77-ai-setup-wizard`, based on
`feature/claude-code-p3`).
**Method:** read-only. The reviewer checked the spec against the code on that branch, the M74 spec, the start-page
spec (D-S1), onboarding R6, CLAUDE.md rules 1, 6, 8 and 9, and the robot journey report with its screenshots. It ran no
`claude` command and edited nothing.
**Focus:** usability, rule 9, and safety. Safety here means three things: nothing is sent before the person acts,
consent stays explicit, and external access is confirmed.

Source paths below drop the prefix `src/main/java/telamin/fluxtion/audit/analyser/analyser/`.

---

## Verdict (reviewer)

**The spec is not ready to implement yet. It needs an r2, and the direction is right.** It covers almost every item in
the owner's brief. Its screen copy is mostly plain, its safety invariants are well chosen, and its CLI claims are
clearly sourced. Three problems stop it:

1. **The proposed graph order cannot support the decisions the spec gives `aiSetup` (rule 9).** `aiSetup` sits
   upstream of `assistantProfile`. In the same cycle it therefore cannot see the profile in force or an Apply refusal,
   and it cannot learn that a Full confirmation was cancelled.
2. **The pending question is placed too early in the "Claude inside" path.** Placing it at `AiSetupFinished` puts it in
   front of `AssistantProfileApplied`, the observations that follow it, and the mode change. That rebuilds robot #1,
   the exact dead end the spec exists to close.
3. **Adding `AssistantMode.NONE` as specified breaks existing in-app users.** Start-up only posts a mode for
   `LOCAL_AGENT`, so anyone on the in-app assistant would start in NONE and every Send would be refused.

There is also a cluster of factual errors about today's code: a `UiText.PRODUCT` constant that does not exist, a
Settings confirmation for external access that does not exist, a capture harness that is not a first run,
`ClaudeMcpClient.detect` using the raw PATH, and launcher copy that names a "packaged app" when there isn't one.

## Findings (reviewer)

| ID | Sev | Area | Finding (evidence) | Proposed fix |
|---|---|---|---|---|
| R1-1 | BLOCKER | rule 9 | §8.1's order `… → aiSetup → assistantProfile → startPagePrompt` cannot carry the decisions given to `aiSetup`. (a) The decisions "nothing set up", `firstRun`, `inForce` and the AI line need the profile in force. That is `assistantProfile`'s state (`session/node/AssistantProfileChoice.java`: `onProfileObserved`, `onAvailability`, `status()`), and a node cannot read its child within a cycle. Observing those facts again would recompute the same value in a second place. (b) A mid-turn Apply is refused downstream (`onProfileChosen`, :146) with no fact, so `aiSetup` cannot keep the step or carry the refusal in its `line`. (c) Re-selecting Safe after Full is cancelled needs `AssistantFullProfileAnswered` as `assistantProfile` interprets it. | Make the setup node the one owner of the profile choice (extend `AssistantProfileChoice` or fold it in). Otherwise put `aiSetup` downstream, with an `origin` per request and outcome facts keyed by request. Add a ★ test: an Apply refused mid-turn from the wizard keeps the step and shows exactly one line. |
| R1-2 | BLOCKER | correctness / rule 9 | The pending question is placed before the profile and mode are in force. For Claude-inside-only, Finish is the Apply. What follows is asynchronous: an effect; `AssistantProfileApplied`; consent, availability and `AssistantProfileObserved` as separate operations (`StartPagePrompt.java:303–345`); then `ChooseAssistantModeEffect` and `AssistantModeChosen` (`ui/MainFrame.java:11564`). Placed at Finish, the draft is composed under IN_APP/NONE, `onDraftComposed` logs a `noOp` (`LocalAgentSession.java:962`), and `staleDraft` refuses it. §12 also names two deciders. | Place only when Finish, Applied(ok), the following Observed and the matching mode all hold. One node decides. Give `AssistantProfileApplied` an origin. Add a ★ witness that the one draft is bound in `localAgentSession.bindings`; the mutation to kill is "place on `AiSetupFinished`". |
| R1-3 | MAJOR | correctness | Making `AssistantMode.NONE` the initial mode breaks existing users. `postStartupFacts` posts `AssistantModeChosen` only for `claudeCodeLastMode=LOCAL_AGENT` (`ui/MainFrame.java:11584–11590`). About 28 mode-check sites are not listed. | Derive the initial mode from `AssistantProfiles.inForce` for every profile. Add a ★ test that an upgrader with a key and `lastMode=IN_APP` can Send. List the sites. |
| R1-4 | MAJOR | completeness / rule 9 | The Ask entry has no trigger. An Ask composes a draft and sends nothing, so none of the four first-use handlers that move fires. | Name the fact: the draft-composed facts, handled while nothing is set up, or a new `AskRequested`. Add it to T2. |
| R1-5 | MAJOR | correctness | §3, §5.1 and §14.1 disagree on "access on + profile NONE". `--rest` persists `assistant.rest=true` (`ui/MainFrame.java:415–420`). The capture harness writes `assistant.rest=true` and launches with a log (`tools/capture-docs.py:261`), so it is not a first run. | External access alone does not count as set up. Derive "Your own client" from a recorded choice. `firstRun` ignores `assistant.rest`. Fix §5.1 and name the harness run that produces the first-run shots. |
| R1-6 | MAJOR | safety | The probes' working directory is unspecified. `auth status` may read project settings (`apiKeyHelper`) in that directory, which would be a trust bypass ahead of folder trust. The same applies to `mcp get`. | Verify against the CLI. Pin the cwd to an empty scratch directory. Add S10 and a ★ test. |
| R1-7 | MAJOR | rule 9 | External access keeps hand-placed writers: the AI menu (`ui/MainFrame.java:2872`), Settings (`ui/ConfigPanel.java:1144`) and `McpSetupDialog.confirmEnableTransport` (`ui/McpSetupDialog.java:699–707`). OD-77.4 would leave *when to confirm* to a view. | One fact, `ExternalAccessRequested(request, on, origin)`, decided by `actionListener`, which requests the confirmation and then the write. All doors post it. Shrink T16's whitelist. |
| R1-8 | MAJOR | correctness / safety | §14.2 says Settings' external-access control "keeps its confirmation", but it has none. *More options…* keeps `McpSetupDialog`'s stock OK-default dialogs reachable (`McpSetupDialog.java:700`, `:686`, `:666`). | Convert those dialogs to SafeChoice and route the enable through R1-7, or drop *More options…*. Correct §14.2. |
| R1-9 | MAJOR | correctness | The "found" badge and registration disagree on a Finder launch. The badge uses `ClaudeResolver`; registration uses `ClaudeMcpClient.detect()`, which searches the raw PATH only (`mcp/ClaudeMcpClient.java:68`), as does Codex's. | Build both clients from one resolver. Add a ★ test with a GUI-shaped PATH. |
| R1-10 | MAJOR | process | T19 "fails until P-3 lands" while it ships in M77-2, and CI rejects failures. | Make P-3 a hard prerequisite of M77-2, or use a large-window variant. |
| R1-11 | MAJOR | usability | M77-2 ships four choices with no Screen 2b, so two choices lead nowhere. | Route them to today's `McpSetupDialog` with a line, or hide them. |
| R1-12 | MINOR | correctness | `UiText.PRODUCT` does not exist. The pin as written is impossible: "the analyser" appears in literals across 17 `ui/` files and in `CLAUDE_CODE_SENDS`. | Say the constant is new. Limit the pin to the wizard's strings and the "Canvas" spelling. |
| R1-13 | MINOR | correctness | "the packaged app": there is none. `McpLaunchCommand` supports JBang or `java -jar` (`:47`, `:66`). | Reword. |
| R1-14 | MINOR | correctness | `AssistantProfileChosen` has no `origin` field (`SessionEvents.java:796`). | List the new field, plus Applied's. |
| R1-15 | MINOR | correctness | The probes' environment depends on the posture: the PATH repair applies to Full only (`LocalAgentAdapter.java:297`). | Probe with the selected posture's environment and re-run on change. |
| R1-16 | MINOR | rule 9 | Missing facts and effects: (a) reading and writing the dismissal; (b) the `--rest` launch; (c) "an owned modal has focus"; (d) a duplicate resolver read; (e) the readiness source (the 5 s poll, `ui/MainFrame.java:746`). | Add a start-up fact and a persist effect; drop (c) (SafeChoice is application-modal); reuse `StartPageAvailability`; name the poll. |
| R1-17 | MINOR | rule 9 | Two owners of the panel's "nothing set up" line, plus stale card wording (`AssistantProfileChoice.java:150`). | Name one owner and suppress the others. Add a panel sibling to T15. |
| R1-18 | MINOR | safety / correctness | There is no state for access "closed for this session" (B2), so the watch would wait for ever. The Full-session caveat must cover any session since arming. The watch is lost when the server is rebuilt. | Add `CLOSED_FOR_SESSION`; base the caveat on "since arming"; keep the watch in the lifecycle adapter. |
| R1-19 | MINOR | usability | Cancel leaves external access on. | Offer *Turn it off* (or make it an OD). |
| R1-20 | MINOR | usability | Choosing *Your own client* while Claude inside is in force turns it off silently. | Add a warning line on that card. |
| R1-21 | MINOR | usability | Two Claudes are still unexplained. | One sentence on each card. |
| R1-22 | MINOR | usability | 2b keeps robot #9's jargon. | Plain line first; add the strings to the jargon test. |
| R1-23 | MINOR | usability | The Ask relabel loses which question each item asks; whether Finish is enabled while sign-in is still ⏳ is unstated. | Keep the question in the label; enable Finish once Installed passes. |
| R1-24 | MINOR | usability | The quick switch's "never checked" test resets on restart and needs a guard. | Use the resolver's file check; the node decides. |
| R1-25 | MINOR | completeness | 6.5 reuses a component from the overlay that §14.3 deletes; refusing an empty key ignores a key already stored. | Extract the component; refuse only when no key is stored and none entered. |
| R1-26 | MINOR | rule 8 | T13, T17 and T20 have no witness. | Witnesses as proposed. |
| R1-27 | MINOR | safety (rule 1) | T20's registration shot would show a jar path under a personal home. | Capture from a jar copied under the isolated temporary root. |
| R1-28 | NIT | correctness | That initialize and `tools/list` do not reach `/action` is already shown in `mcp/McpBridge.java:190–192`, `:292`. | State it as verified. |
| R1-29 | NIT | a11y | Alt+B is not a macOS convention; VoiceOver announcements are unreliable; Enter on 2b could trigger Finish. | Platform keys; focusable results; Enter fires the focused button. |
| R1-30 | NIT | completeness | `RecordSelectionObserved` on every selection change is a high-rate fact. | Post it once, at Finish. |

### Questions for the owner (reviewer)

1. Should the setup node absorb `assistantProfile` (R1-1)?
2. Does `assistant.rest=true` alone count as "Your own client chosen" (R1-5)?
3. Cancel after turning access on: keep it on with a one-click *Turn it off*, or roll back (R1-19)?
4. P-3 as a hard gate for M77-2 (R1-10)?
5. M77-2 without 2b: route to the old dialog, or hide the choices (R1-11)?
6. NONE mode: is a start-up mode derived from the profile acceptable (R1-3)?
7. *More options…*: keep `McpSetupDialog`, with its dialogs converted, or retire it (R1-8)?

---

## Disposition (author, r2)

Every finding was checked against the code before it was accepted. The reviewer's main code claims were confirmed:
`capture-docs.py` pre-sets `assistant.rest`; `--rest` persists it; `postStartupFacts` posts only `LOCAL_AGENT`; and
`LocalAgentAdapter` extends the PATH for Full only. For R1-6, `claude auth status` (2.1.282) was run with a project
`.claude/settings.json` in the working folder, under an isolated `HOME` and `CLAUDE_CONFIG_DIR`. It **read the
folder's project settings**: it reported `authMethod: api_key_helper`. It **did not run** the helper (no marker file
appeared).

| ID | Disposition | Where in r2 | Regression check (rule 8) |
|---|---|---|---|
| R1-1 | **Accepted, one node.** `AssistantProfileChoice` grows into `aiSetup`, with pure helpers for its words, the parser and the derived table. The two-node alternative is OD-77.13. | §8.1, §7.2, §8.2 | ★ T23 (a mid-turn Apply keeps the step and shows one line), ★ T4 |
| R1-2 | **Accepted.** Placement waits for Finish, Applied(ok) for the wizard's request, the following Observed, and the mode. `startPagePrompt` is the one decider. `Applied` gains `origin`. | §12, §8.2 `finished`, §8.3 | ★ T12; witness "place on Finish alone" |
| R1-3 | **Accepted.** The start-up mode is derived from `inForce` for every profile. Mode checks become exhaustive `switch`es and are listed in M77-1. | §8.5 | ★ T21 |
| R1-4 | **Accepted.** The draft-composed facts trigger the wizard while nothing is set up, and the draft is held pending. | §5 entry 5 | ★ T2 (extended) |
| R1-5 | **Accepted.** Access alone is not a choice. New key `assistant.setup.client`; `firstRun` ignores `assistant.rest`; §5.1 corrected; the existing harness shots pin `dismissed=true`, and a dedicated fresh-home run produces the first-run shots. | N6, §5, §5.1, §14.1 | ★ T1 (derived table), T20 |
| R1-6 | **Accepted, and verified** (above). Every probe and registration command runs in a fresh empty scratch directory. `api_key_helper` is mapped. | §8.6, §9, §13 S10 | ★ T22 |
| R1-7 | **Accepted.** `ExternalAccessRequested` from all four doors is decided by `actionListener`, which is now the only requester of the write. | §9.2, §8.3, §8.4 | ★ T9 (four doors), ★ T16 (one writer) |
| R1-8 | **Accepted.** §14.2 is corrected: Settings has no confirmation today. `McpSetupDialog` stays behind *More options…*, with its three dialogs converted to SafeChoice in M77-3. Retiring it is OD-77.12. | §9.3, §14.2 | the SafeChoice frame test, extended |
| R1-9 | **Accepted.** One resolver for Claude Code and Codex; the clients are built from it. | §6.1, §8.4 | ★ T24 |
| R1-10 | **Accepted.** P-3 is a hard prerequisite of M77-2. | §4, §18 | T19 at 1200×800 |
| R1-11 | **Accepted.** Until M77-3, the two client choices open `McpSetupDialog` with a line saying so. | §18 | — (interim; removed by M77-3) |
| R1-12 | **Accepted.** The constant is new; the pin is limited to the wizard's strings and the "Canvas" spelling. | header | ★ T18 |
| R1-13 | **Accepted.** | §6.3 | — |
| R1-14 | **Accepted.** `origin` is added to `Chosen` and `Applied`. | §8.3 | T1 |
| R1-15 | **Accepted.** The probes use the selected posture's environment and re-run when it changes. | §6.2, §8.6 | T6 |
| R1-16 | **Accepted in part.** (a) and (b) become `AiSetupStartupObserved` and the persist effects. (c) is dropped: SafeChoice is application-modal. (d) reuses `StartPageAvailability`. (e) names the poll; its reclaim decision is a follow-up (§19). | §5.1, §8.3, §8.4, §19 | T8 |
| R1-17 | **Accepted.** `aiSetup` is the one owner; the old strings are retired; the assistants' own refusal lines are suppressed while nothing is set up. | §6.6 | ★ T23 |
| R1-18 | **Accepted.** `CLOSED_FOR_SESSION` is added with its copy; the caveat is based on "since arming"; the watch is held by the lifecycle adapter. | §6.3, §8.2, §8.4, §9.4 | T11 |
| R1-19 | **Accepted.** *Turn it off* is offered on Cancel; rolling back instead is OD-77.11. | §6, §7.4 | T5 (nothing sent), T9 |
| R1-20 | **Accepted.** | §6.1 | — |
| R1-21 | **Accepted.** | §6.1 | — |
| R1-22 | **Accepted.** | §6.3 | ★ T18 |
| R1-23 | **Accepted.** | §6.2, §6.6 | T19 |
| R1-24 | **Accepted.** The resolver's file check is used, and `aiSetup` decides. | §14.3 | T2 |
| R1-25 | **Accepted.** | §6.5 | — |
| R1-26 | **Accepted.** T13 and T17 gain witnesses; T20 is a capture rule (its check is reading the shots, CLAUDE.md rule 1). | §16 | — |
| R1-27 | **Accepted.** | §16 T20 | — |
| R1-28 | **Accepted.** | §9.4 | CLI-drift canary |
| R1-29 | **Accepted.** | §6, §15 | T17 |
| R1-30 | **Accepted.** | §8.3 | — |

**Owner questions** map to the spec's decisions as follows. Q1 is OD-77.13 (recommended: one node). Q2 is resolved in
r2: no, access alone is not a choice. Q3 is OD-77.11. Q4 and Q5 are resolved in r2 as the reviewer proposed, and the
owner may overrule them. Q6 is OD-77.6, now with the derived start-up mode. Q7 is OD-77.12.
