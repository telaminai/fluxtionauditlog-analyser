# M77 — Set up AI: the first-run wizard for the shared canvas

**Status:** DRAFT r2, 2026-10-18 — r2 folds in the independent review of r1
([`review_m77_r1_7f3c.md`](../handoff/review_m77_r1_7f3c.md), 3 blockers, 8 major findings, 30 in all; the
disposition table is in that file and summarised in *Revision history*). r1 was written from the owner's direction of
2026-10-18 and the first-time-user robot walk of the same day (built on `review-fix/pr-151` @ `79cb0f43`, restated in
§1), and read against `feature/claude-code-p3` (`d9235afd`).

**Tracker:** M77. **Builds on:** M74 ([`spec-claude-code-provider.md`](spec-claude-code-provider.md): the profiles, the
posture, the consents, the folder trust, the carried question), the MCP bridge and M42's registration
([`spec-assistant-actions-mcp.md`](spec-assistant-actions-mcp.md); `mcp/ClaudeMcpClient`, `mcp/CodexMcpClient`), the
start page ([`completed/spec-start-page.md`](completed/spec-start-page.md), D-S1), and CLAUDE.md rules 1, 6, 8 and 9.

**Owner direction (2026-10-18).** The analyser — the future *Telamin Canvas* — is a shared canvas, so on first start it
offers a wizard to integrate an LLM: **(1) No AI; (2) your own AI CLI connects via MCP; (3) full Claude integration
onboard, i.e. Claude Code inside the canvas, with a Safe analysis / Full Claude Code sub-choice; (4) both 2 and 3.** It
then guides the set-up as required.

**Naming.** r13-4 (M74) keeps new strings neutral until the rename PR. Every string below that names the product is
written `{Product}`. It is a **new** constant, `UiText.PRODUCT` (today *"the analyser"*, after the rename *"Canvas"*),
used by the wizard's strings. `UserVisibleJargonTest` pins only that the wizard's own strings use it and that no
literal anywhere spells *Canvas*; the ~17 `ui/` files that say *"the analyser"* today, and constants the wizard shows
verbatim (`AssistantProfiles.CLAUDE_CODE_SENDS`), are the rename PR's (r13-4), not this one's (review R1-12).

---

## 1. Why — what the robot walk found

A robot drove the real `Main` under an isolated `user.home` through four newcomer paths (screenshots A–E). The ten worst
problems, and which of them this spec owns:

| # | Problem (robot's numbering) | Owned here |
|---|---|---|
| 1 | A question asked from a record before an assistant is chosen can never be sent to Claude Code: drafted for the in-app mode in force, its binding is unknown to `LocalAgentSession`, `staleDraft()` refuses it, and asking again appends a second refused copy. 16 acts, four dead Sends. | **the first-run case** (via the pending question, §12); the general fix (b)–(d) lands on `feature/assistant-ux-followups` and is a **prerequisite** (§4) |
| 2 | The docked assistant breaks at the default 1200×800 window (transcript at −256 px; Start and Send hidden) | no — prerequisite worth doing first (§4) |
| 3 | Contradictory "what do I press" cues (Start then Send / Send / "Start — ask your question") | no |
| 4 | Two "Claude"s and about eight entry points; the in-app Claude Code route is offered on none of the start page's entries | **yes** — one door, *Set up AI…* (§5) |
| 5 | The start page says "No AI assistant can answer here yet" and offers no way to set one up | **yes** — the AI status line (§6.0) |
| 6 | *Configure provider* opens Settings on Source roots; "LLM" / "In-app assistant" / "Fluxtion key" naming | partly — in-app moves behind *Other options* (§6.5) |
| 7 | The Full Claude Code consent text is clipped mid-sentence (`SafeChoice.build` packs a wrapping `JTextArea` with no width) | no — **prerequisite**: the wizard reuses that dialog (§4) |
| 8 | Before a choice the mode is silently IN_APP: chips say "In-app · no API key", Ask items say "Ask the assistant" with none chosen | **yes** — §8.5 and §5 entry 5 |
| 9 | The external-MCP path is jargon-heavy, uses two stock OK-default dialogs, and never proves the client connected | **yes** — §9 |
| 10 | Weak "what now" cues around the first answer (a no-log question gets a generic answer) | partly — the Summary's suggested first action (§6.4) |

The newcomer path the owner cares about — **Sample project → right-click a record → Ask → (wizard) → Send** — took 16
acts with four Sends that did nothing. This spec's target is **6 acts with no dead end** (§16, the robot walkthrough
test).

## 2. Goals and non-goals

**Goals**

- G1. **One door.** *Set up AI…* is the one place a person chooses how AI works with {Product}, reachable from first run,
  Assistant ▾, AI ▸, the start page's AI line, and any Send or Ask pressed with nothing set up.
- G2. **The four owner choices, in plain words,** each leading only to the steps it needs.
- G3. **Detection, not instructions.** The wizard checks what it can (Claude Code installed, signed in; external access
  on; the client registered; a client actually reached this window) and says what failed and what to do, each with
  *Check again*.
- G4. **A proof of success for the external client** — a live check that an external client reached THIS window — which
  the current dialog says it cannot give.
- G5. **The first question survives set-up.** A question asked before set-up is placed for the chosen assistant when the
  wizard finishes, bound so Send works (closing robot #1 for first use).
- G6. **Rule 9.** Every decision is a session node's (`aiSetup`); the wizard renders `AiSetupState`; every write goes
  through the node that owns it today.
- G7. **Consent stays explicit and unchanged in strength.** Nothing is sent to a model by the wizard; Full Claude Code,
  external access and registration each keep (or gain) a SafeChoice confirmation with the safe choice the default.

**Non-goals**

- N1. No new assistant, posture, verb or permission. The profiles and their settings are M74's; the wizard writes them
  through M74's nodes.
- N2. No change to folder trust: it stays at the first Start in a folder that needs it (§11).
- N3. Not the docked-panel layout (#2), the Start/Send cue (#3), *Configure provider*'s page (#6) or the SafeChoice
  clipping (#7) — prerequisites or siblings (§4), not this milestone.
- N4. No sign-in inside {Product}. The wizard never runs `claude auth login`, never reads a credential, a key file or
  `~/.claude.json`; it tells the person the one terminal command and checks again.
- N5. No installation of Claude Code or Codex. A link (through `LinkConfirm`) and *Check again*.
- N6. As little new stored state as possible. The choice in force is DERIVED from the keys M74 and M42 already use
  (§14.1), plus exactly two new machine-tier keys: the first-run dismissal and the external client the person set up
  (`assistant.setup.client`, because external access alone does not mean a client was chosen — review R1-5).
- N7. The rename to Telamin Canvas / Telamin AI (r13-4) — a separate PR; this spec only routes the name through one
  constant.

## 3. Standing decisions this touches

| Decision | What it says | What this spec does |
|---|---|---|
| **M36 D-S1 / onboarding R6** | The start page IS the first run; no first-run dialog — an owner-directed removal (*"no config found modal popped"*) that "must not return". | **Kept by default.** First run shows the wizard's first screen *inline on the start page* (§6.0), as a state, not a window. A window that opens by itself would reverse D-S1: that is owner decision **OD-77.1**, not assumed. |
| **M35.7 / M35.9** | No modal in the load path; nothing fires at an empty screen. | The wizard window opens only on the person's act (§5); it is modeless; under `--rest` (nobody at the screen) nothing is offered (§5.1). |
| **M74 r13-3** | The profile overlay (four cards) opens from the header, and by itself on the first Start or question. | **Amended:** the cards stop being the entry point (§14.3). The first Start or question opens *Set up AI* instead. |
| **M74 r13-4** | New strings neutral; rename later. | `{Product}` constant (header). |
| **M74 OD-74.5 / OD-74.12** | Both postures need the folder confirmation, at Start. | Unchanged (§11). |
| **SafeChoice rule** (review M74 usability #7) | The safe choice is the default, at the right; the risky one alone at the left, never default-capable. | Every confirmation the wizard raises uses it — including the two that are stock OK-default `JOptionPane`s today (external access, registration; robot E/02–E/03). |
| **M42.4** | `ClaudeMcpClient`: user scope only; never parse or edit `~/.claude.json`; exact argument vectors; `mcp get` only on an explicit act (it may health-check a configured server). | Kept; the wizard calls the same class (§9.3). |
| **`FaqSecurityContractTest`** | The "not local-only" sentence (`AssistantProfiles.CLAUDE_CODE_SENDS`, `IN_APP_SENDS`) is pinned on every surface. | The wizard is a new surface: it shows the constant and joins the test (§16). |

## 4. Prerequisites and dependencies

| Item | Where | Why M77 needs it |
|---|---|---|
| **P-1. SafeChoice clipping (robot #7)** | being fixed separately | The wizard's Full confirmation IS `ConfigPanel.fullPostureDialog`; its two new confirmations use `SafeChoice.build` too. Without the fix the wizard inherits the clipped home-folder warning. **M77 PR-1 does not merge before it.** Its regression check (an area's preferred height covers its line count at the packed width, with the current clipping as witness) is the fix's, and the wizard's three dialogs are added to it. |
| **P-2. The pending question (robot #1)** | `feature/assistant-ux-followups` (landing separately) | §12 reuses its mechanism — a question asked before an assistant can answer is held pending and unbound, then placed and bound for the assistant chosen. Today only the start box carries its question (`StartPagePrompt.carried`); the record-Ask path has nothing equivalent. M77 changes **who triggers placement** (the wizard's Finish), not the mechanism. If P-2 has not landed when M77 PR-1 starts, PR-1 stops and asks: it must not grow a second pending slot. |
| **P-3. Docked layout floor (robot #2)** | independent, about 1 day | **A hard prerequisite of M77-2** (review R1-10): the newcomer's Send after Finish is in the docked panel, and at 1200×800 Send is hidden today. The walkthrough test (§16, T19) runs at 1200×800 and ships in M77-2, so M77-2 does not merge before P-3 — CI rejects a failing or skipped test, and a 1680×1050 stand-in would hide the dead end a newcomer meets next. |
| P-4. *Configure provider* opens the LLM page (robot #6) | recommended, one line | *Other options* lands on the in-app key field itself (§6.5), so M77 does not depend on it. |

## 5. Entry points — where it lives and when it opens

**Host.** A modeless dialog owned by the main window, title **"Set up AI"**, about 640×560, centred on the main window,
minimum 520×480 (§15). Not the docked panel: the panel can be ~216 px wide (#2), and the wizard must run from the start
page, where the panel is not shown. One instance: a second request brings the open one to the front, at its current
step (the node's `open` is the truth; the view never opens a second).

| # | Entry | What the person does | What `aiSetup` decides |
|---|---|---|---|
| 1 | **First run** — the start page's AI block in its first-run form (§6.0) | nothing: the block is part of the no-log state; pressing a choice there and **Continue** is the act | the block's form (`firstRun` in the snapshot) when: the profile is observed `NONE`, no external client was set up (`assistant.setup.client` unset), `assistant.setup.dismissed` is false, and the process was not started with `--rest` — all reported at start-up by `AiSetupStartupObserved` (§8.3). **`assistant.rest` is ignored** (review R1-5): `--rest` persists it (`MainFrame`, M19.7) and the capture harness pre-sets it, so it says nothing about what a person chose. Continue opens the window at the next step with the choice already made. |
| 2 | **Assistant ▾ → Set up AI…** | menu item | open at Screen 1, with the choice in force preselected (§14.1) |
| 3 | **AI ▸ Set up AI…** (first item; *Connect an AI client…* becomes *Set up AI…* — §14.4) | menu item | same as 2 |
| 4 | **The start page's AI line** — *Set up AI…* / *Change…* (§6.0) | link button | same as 2 |
| 5 | **Send or Ask with nothing set up** — the start page's Send, the panel's Send or Start, any record/chart *Ask…* item | the press | open at Screen 1 with the question **pending** (§12) — *instead of* the profile cards, which no longer open by themselves (§14.3). Sends and Starts arrive as today's facts (`LocalAgentSendRequested`, `AssistantSendRequested`, `LocalAgentStartRequested`, `StartPagePromptSent`). **An Ask sends nothing**, so it has its own trigger (review R1-4): the draft-composed facts (`LocalAgentDraftComposed` and the in-app assistant's equivalent), handled by `aiSetup` while nothing is set up — the draft is held pending, unbound, and the wizard opens. Once per press: a dismissed wizard is not reopened by the same press; the next press reopens it (no "once per session" latch — the latch is what made the cards' second press silent). |
| 6 | **Settings ▸ Assistant → Set up AI…** (a button at the top of the page) | button | same as 2; Settings stays open behind |

**"Nothing set up"** (entry 5) is the node's verdict, not the button's: the profile observed `NONE` *and* no stored
in-app key that can answer (`StartPageAvailability.inAppCanAnswer`) — the same test `AssistantProfileChoice.status()`
uses today, so a person with a key stored before profiles existed is never interrupted. A profile of `OFF` is a choice,
not "nothing set up": a Send under Off is refused with Off's line and its *Set up AI…* link (§6.0), and the wizard does
not open by itself.

**5.1 When it never opens by itself.** Under `--rest` (`-Danalyser.rest`; an agent drives the process — reported by
`AiSetupStartupObserved.restLaunch`), and during the scripted demo (the assistant header is disabled then,
`AssistantPanel` `inDemo`). The SafeChoice confirmations are application-modal, so no entry can be pressed behind one.
**The capture harness** (`tools/capture-docs.py`) is not a first run as it stands: it writes `assistant.rest=true` into
its isolated config and launches with a log (review R1-5). Its existing shots pin `assistant.setup.dismissed=true` so
they do not change; M77-4 adds one harness run on a fresh isolated home with no log for the first-run block and the
wizard's screens (T20).

## 6. The screens, with their copy

Conventions: **bold** is a button; ● ○ a radio choice; ✓ ✗ ⏳ are glyph AND word (never colour alone, §15). The footer
line of every wizard screen is **"Nothing is sent to an AI model from this window."** The button row is
`Back` (left) · `Cancel` · primary (right). Enter fires the focused button; on a screen whose focus is on no button,
Enter is the primary (review R1-29: on 2b, Enter on **Register for me…** must not Finish). Cancel closes; what an
earlier step already turned on stays on, the closing status line says what is in force, and when this wizard turned
external access on, the line offers **Turn it off** (§7.4, review R1-19).

### 6.0 The start page's AI block (first run) and the AI line (every other time)

**First run** (entry 1). Replaces the *Connect an assistant* group (§14.4) and the "No AI assistant can answer here yet"
fallback line, in the same place:

> **How do you want AI to work with {Product}?**
> ○ No AI · ○ Your own AI client connects · ○ Claude inside {Product} · ○ Both
> *(one line under the selected choice, Screen 1's sentence for it)*
> **Continue** · **Not now**
> *Nothing is sent or started until you choose.*

- *Claude inside {Product}* is preselected when `claude` was found (`StartPageAvailability.claudeCodeFound`, §8.3), otherwise nothing is.
  Preselecting is acceptable because Continue runs only the read-only checks of §8 (G7).
- **Not now** stores `assistant.setup.dismissed=true` (machine tier) and the block becomes the AI line.
- **No AI** + Continue applies `OFF` at once (no window; there is nothing to guide) and the block becomes the AI line
  *"AI: off — Set up AI…"*.
- The other three open the window at Screen 2a or 2b with the choice made.

**Every other time**, ONE line under the start box, composed by `aiSetup` (§8.4) — it replaces the contradictory pair
the robot saw (A/p1-02) and the per-entry fallback rows (A/p3-02):

| In force | Line |
|---|---|
| nothing set up, `claude` found | *"AI: not set up · Claude Code found — **Set up AI…**"* |
| nothing set up | *"AI: not set up — **Set up AI…**"* |
| Off | *"AI: off — **Set up AI…**"* |
| Safe / Full | *"AI: Claude inside {Product} (Safe analysis)"* / *"(Full Claude Code)"* — **Change…** |
| In-app | *"AI: the in-app assistant (your API key)"*, or *"… — no key yet"* — **Change…** |
| + external access on | append *" · your own client can connect"*; or *" · another {Product} window holds the client connection"* when the readiness is `OTHER_INSTANCE` |

### 6.1 Screen 1 — "How do you want AI to work with {Product}?"

Four radio cards in one column; each card is a `JRadioButton` with its sentence as the accessible description:

- ○ **No AI** — *"Everything in {Product} works without AI: records, topology, charts, reports, walks and the tour. You
  can set AI up later from Assistant ▾ ▸ Set up AI…"*
- ○ **Your own AI client connects to {Product}** — *"Claude Code, Codex or another MCP client in your terminal reads
  records, draws charts and points at evidence here. You keep using it in your terminal."* Badge: *"Claude Code found"*
  / *"Codex found"* / none. While Claude inside is in force, a second line: *"This turns off Claude in the panel —
  choose Both to keep it."* (review R1-20)
- ○ **Claude inside {Product}** — *"The same Claude Code, run by {Product} in its assistant panel. Ask from any record,
  chart or the start page."* Badge: *"Claude Code found"* or *"Claude Code not found"*. (The two "Claude" sentences say
  it is one program used two ways — review R1-21, robot #4.)
- ○ **Both** — *"Claude in the panel, and your terminal client can drive {Product} too."*

Below the cards: the link **Other options: the in-app assistant with your own API key…** (→ §6.5).

Badges are a file-existence observation that starts no process, stated in the card's tooltip: *"Found on this machine;
nothing was run."* They come from **one resolver** (review R1-9): `ClaudeResolver` (PATH, then the standard install
places), and a Codex resolution built the same way (PATH, then the npm-global places). The registration clients of §9.3
are constructed **from that resolution**, not from their own `detect()` — which searches only the inherited `PATH`
(`ClaudeMcpClient.detect`, `CodexMcpClient.detect`), so a Finder-launched {Product} would say "found" and then fail to
register.

Buttons: **Cancel** · **Continue** (disabled until a choice is made). Preselection: the choice in force (§14.1); on a
fresh machine *Claude inside {Product}* when `claude` was found, otherwise none.

### 6.2 Screen 2a — "Claude inside {Product}" (choices 3 and 4)

Step indicator for *Both*: *"Step 2 of 3 · Claude inside {Product}"*.

**Checks.** The line above them, shown before they run: *"Checking runs `claude --version` and `claude auth status` on
this machine. No question and no evidence is sent."* They start when the screen opens — the person's Continue on the
choice that needs them is the act (G7) — and each has its own **Check again**.

| Check | ⏳ while running | ✓ pass | ✗ / ? fail states (each with **Check again**) |
|---|---|---|---|
| **Installed** | *"Looking for Claude Code…"* | *"Claude Code 2.1.282 is installed."* + the path, shortened to `~` (rendered by the view from a path reference, never carried in the snapshot — §8.4) | **Not found:** *"Claude Code was not found. Searched:"* + `ClaudeResolver.Resolution.searchedText()` lines, **How to install…** (docs link via `LinkConfirm`). Continue disabled; Back offered. **Found but did not run** (exit ≠ 0, no version line, or 5 s timeout): *"Claude Code was found at ~/… but did not answer `--version`. Try running it once in a terminal."* Continue disabled. |
| **Signed in** (runs only after Installed passed) | *"Checking whether Claude Code is signed in…"* | *"Signed in with your Claude account (Max plan)."* — `authMethod` `claude.ai`, plan from `subscriptionType`; or *"Uses an API key (ANTHROPIC_API_KEY). Claude Code checks the key when it starts."* — `authMethod` `api_key`, with `apiKeySource`; or *"Uses an API key helper from your Claude Code settings. Claude Code runs it when it starts."* — `authMethod` `api_key_helper` | **Not signed in** (`loggedIn:false`): *"Claude Code is not signed in. In a terminal run* `claude auth login` *, then Check again."* **Copy command**. Continue stays enabled with the line *"You can continue; Claude Code will ask you to sign in when it starts."* **Could not tell** (timeout 10 s, unreadable output, or an `authMethod` this spec has not verified): *"Could not tell whether Claude Code is signed in. You can continue; Start checks again."* — Continue enabled: this is an observation, not a gate (the binding check at Start, `LocalAgentInitialized.loggedIn`, remains the authority). |

**The sub-question:** *"How much of your Claude Code set-up should it use?"*

- ● **Safe analysis (recommended)** — `ProfileOverlay.SAFE_CARD_LINE`'s facts in two lines: *"Reads your evidence through
  {Product}; asks you before it changes what is open or writes a file."* / *"No shell, no file edits, no web. Your
  skills, hooks and MCP servers are not loaded — safe with logs you did not write."*
- ○ **Full Claude Code** — *"Your own settings, skills, hooks, MCP servers and tools, as in your terminal."* When
  selected, three warning bullets appear inline (the first three sentences of `ConfigPanel.FULL_POSTURE_WARNING`, by
  reference, not a copy) and the Full mode line (*"Mode: default · change"*, the four modes of
  `LocalAgentPosture.FullMode`). The primary button's label becomes **Continue — confirm Full…**.
- The model, collapsed: *"Model: sonnet · change"* (`ClaudeCodeProvider.validModel`).
- The honesty line, verbatim `AssistantProfiles.CLAUDE_CODE_SENDS`.
- The folder note: *"If you work in your own project folder, Claude Code asks once before it starts there."* (§11)

Buttons: **Back** · **Cancel** · **Continue** (Both) or **Finish** (Claude inside only), **enabled once Installed has
passed**; *Signed in* may still be ⏳, and then shows *"still checking — Start checks again"* (review R1-23). Pressing it
applies the profile (§7.2).

**The probes' environment follows the sub-choice** (review R1-15): `LocalAgentAdapter` extends the child's `PATH`
(`ChildPath.extend`) only for Full, so a `claude` that is a Node script can run under one posture and not the other.
Installed is checked with the selected posture's environment and **re-run when the sub-choice changes**. For Full, the existing confirmation opens first — *"Full Claude Code — read this first"*, **Cancel** the
default; Cancel returns to this screen with **Safe analysis re-selected** and nothing applied.

### 6.3 Screen 2b — "Your own AI client" (choices 2 and 4)

Step indicator for *Both*: *"Step 3 of 3 · your own client"*. Four numbered parts on one scrolling screen, each
enabled when the one above is done:

**1. Which client?** ● Claude Code ○ Codex ○ Another MCP client — badges as Screen 1. *Another MCP client* covers Claude
Desktop with today's sentence (`McpSetupDialog.refreshClaudeDesktopStatus`: no extension is offered; use the copied
configuration).

**2. Let your client reach this window.** A switch, **Let programs on this computer reach this window**, with the
setting's name as its secondary label (*External MCP/REST access*, so Settings and the AI menu can be found from it) and
the line *"Only programs on this computer that have this run's key can connect. Nothing leaves this computer."* (plain
words first — review R1-22; the strings join `UserVisibleJargonTest`). Turning it **on** raises a SafeChoice
confirmation (§9.2); turning it off needs none. When access is already on: *"✓ On."* When it is **closed for the Full
Claude Code session running in the panel** (`ActionListener.closedForSession`, review B2): *"Closed while the Full
Claude Code session in the panel runs — it opens again when that session ends."* and part 4 is not armed (review
R1-18).

**3. Register {Product} with your client.**

- Claude Code / Codex: *"Your client needs to know how to start the {Product} bridge. This is the exact command:"* — the
  command collapsed to its first words (`claude mcp add --scope user fluxtion-analyser …`), **Show the full command**,
  **Copy**; then **Register for me…** (a SafeChoice showing the verbatim command, §9.3) and **I'll run it myself**
  (copies it; the step shows ✓ *"Copied — run it in a terminal"*). **Check registration** (explicit; §9.3): ✓ *"Already
  registered"*, ✗ *"Not registered yet"*, ? *"Could not tell — your client's configuration was not changed."*
- Another MCP client: **Copy configuration** / **Save snippet…** (`GenericMcpConfiguration`, unchanged) and *"Add it
  to your client's MCP settings, then restart the client."*
- **No supported launcher** (`McpLaunchCommand` unavailable): ✗ *"This copy of {Product} cannot be registered: it was
  not started with `java -jar` or the JBang install."* **How to install…** (review R1-13: there is no packaged app). Parts 3–4 disabled; **Finish without a client**
  offered.

**4. Can your client see {Product}?** — the live check (§9.4).

- ⏳ *"Start a **new** session of your client and ask it:* Use analyser_context and tell me what is open. *Waiting…"*
  **Copy the question**.
- ✓ *"A client reached this window through the bridge at 14:02."* (and, OD-77.8, *"It calls itself
  "claude-code"."*). The words are honest: it proves a client with this window's token reached this window, not which
  client.
- After 2 minutes, still waiting, the tips appear (the wait continues): *"Restart your client after registering — in
  Claude Code, `/mcp` should list fluxtion-analyser."* · *"Check that External MCP/REST access is on (part 2)."*
- ✗ **Another window holds the connection** (readiness `OTHER_INSTANCE`): *"Another {Product} window owns the client
  connection, so your client is reading that window's log. Close it, or set up from it."* **Check again**.
- ✗ **Access was turned off** while waiting: *"Access is off, so your client cannot reach this window."* — part 2
  re-enabled.
- ✗ **Closed for a Full session** (above): the watch is disarmed, with part 2's line.
- ✗ **Not registered** (only when **Check registration** said so): *"Your client has no registration named
  fluxtion-analyser."* **Register for me…**.

Buttons: **Back** · **Cancel** · **Finish** (enabled once part 2 is on; the live check is never a gate) and **Finish
without checking** while ⏳.

### 6.4 Screen 3 — Summary, and one suggested first action

Shown at Finish, **except** when a question is waiting (OD-77.2, recommended): then Finish closes the window, the
question is placed (§12), and the Summary's first two lines become the assistant panel's one status line (*"Set up:
Claude inside {Product} · Safe analysis. Your question is below — press Send."*). That is what keeps the newcomer path
at 6 acts (§16).

> **You're set up**
> - *"Claude inside {Product}: **Safe analysis** · Claude Code 2.1.282 · signed in"* (or *"… · sign-in not checked"*)
> - *"Your own client: Claude Code · registered · connected at 14:02"* (or *"… · not checked"*)
> - *"No AI"* / *"In-app assistant · your API key"* as applicable
> - *"Change any of this: Assistant ▾ ▸ Set up AI…. Settings ▸ Assistant keeps every option."*

**One suggested first action**, chosen by `aiSetup` from the facts (§7.3) — one button, the default:

| Situation | Button | What it does (nothing is sent) |
|---|---|---|
| Claude inside (or Both), no log open | **Open the sample and ask what happened** | opens the demo sample (the start page's *Sample project* action), selects the demo manifest's breach record **by its record id, never a row number**, and places *"What happened here?"* bound for Claude Code, focus on Send |
| Claude inside (or Both), a log open, a record selected | **Ask about the selected record** | places the Ask catalogue's *What happened here?* for that record, focus on Send |
| Claude inside, a log open, nothing selected | — | the line *"Right-click any record and choose Ask… to ask about it."* and **Done** |
| Your own client only | **Copy a first question** | copies *"Use analyser_context, then tell me what happened in the open log."*; the line *"Ask it in your terminal."* |
| In-app with a key | as the Claude rows | for the in-app assistant |
| No AI | **Take the guided tour** | starts `DemoTour` |

Plus **Done** (closes). Esc and the window's close are Done.

### 6.5 Other options — "The in-app assistant (your own API key)"

Reached only from Screen 1's link. The in-app card's details (provider, model, key) — **extracted from `ProfileOverlay`
into their own component in M77-2**, before M77-4 deletes the overlay (review R1-25) — **focus on the key field**, the `IN_APP_SENDS` line, and the key note
(*"stored on this machine only (in cleartext), never in a project, a share or a bundle"*). If `claude` was found, one
line above it: *"Claude Code is installed and needs no API key — Back to use it instead."* Buttons: **Back** ·
**Cancel** · **Finish** (applies `IN_APP` with the details through the same performer as the cards). Finish is refused only when
**no key is stored and none was entered** — *"Add a key, or choose another option"*; a blank field with a stored key
keeps the stored key, as `AssistantProfiles.apply` does (review R1-25). That closes robot C/02's "accepted with no key"
for this surface.

### 6.6 The assistant panel when nothing is set up

The panel's empty state (today: the four cards on Send) becomes one line and one button, at any width down to 200 px:
*"No AI set up yet."* **Set up AI…**. The chips read *"No assistant set up"* (not *"In-app assistant · no API
key"*, robot #8). The composer stays visible and editable, so a typed question is not lost; Send opens the wizard with
it pending (§5 entry 5). The Ask items keep their question and add the cue while nothing is set up: *"Ask: What
happened here? (set up AI first)"* (review R1-23). **This line has one owner** (review R1-17): `aiSetup` (§8.1),
which replaces `AssistantProfileChoice`'s `NO_ASSISTANT*` strings (they point at cards that no longer exist) and its
*"choose one of the four cards first"* refusal; while nothing is set up or the wizard is open, the assistants' own
refusal lines (*"no provider is configured…"*) are suppressed in favour of it.

## 7. The decision logic

### 7.1 Screen flow

```
Screen 1 ──No AI──────────────► apply OFF ─► (external on? OD-77.5) ─► Summary
         ──Your own client─────► 2b ─► Finish ─► apply OFF (panel) ─► Summary
         ──Claude inside───────► 2a ─► Finish ─► apply SAFE|FULL ─► Summary | close+place (question waiting)
         ──Both────────────────► 2a ─► Continue (apply SAFE|FULL) ─► 2b ─► Finish ─► Summary | close+place
         ──Other options───────► 6.5 ─► Finish ─► apply IN_APP ─► Summary | close+place
Back: one screen back; it undoes nothing; the screen shows what is in force.
```

### 7.2 When things are applied

| Act | Applied | Through |
|---|---|---|
| 2a **Finish/Continue** (Safe) | the profile at once | `AssistantProfileChosen(request, SAFE, origin=SETUP)` → `aiSetup` decides → `ApplyAssistantProfileEffect` (unchanged performer) |
| 2a **Continue — confirm Full…** | nothing until confirmed | `aiSetup` → `ConfirmFullProfileEffect` → `AssistantFullProfileAnswered`; confirmed → apply |
| 2b part 2 switch on | external access, after its confirmation | `ExternalAccessRequested(request, on, SETUP)` → `actionListener` (§9.2) |
| 2b **Register for me…** | the client's registration, after its confirmation | §9.3 |
| Screen 1 **No AI** (and the first-run block's) | `OFF`, and `assistant.setup.client` cleared | `AssistantProfileChosen(OFF, SETUP)`; `PersistSetupClientEffect(none)` (external access: OD-77.5) |
| 2b **Finish** for choice 2 or 4 | `assistant.setup.client` = the client of part 1; for choice 2 also `OFF` for the panel (Screen 1 warned when Claude inside was in force, §6.1) | `PersistSetupClientEffect`; `AssistantProfileChosen(OFF, SETUP)` |
| 6.5 **Finish** | `IN_APP` with details | as above |

Mid-turn: an Apply while either assistant's turn is open is refused by `aiSetup` with the existing words
(`BUSY_LOCAL_AGENT_TEXT` / `BUSY_IN_APP_TEXT`) as the screen's ONE line (`AiSetupState.line`); the step does not
advance. Because the same node decides the refusal and the step, there is nothing to combine in the view (review
R1-1(b)).

### 7.3 The suggested action (§6.4), as a decision

`aiSetup` picks it at Finish from: a question pending (§12) → close+place; else the effective choice; else
`openLog.isOpen()`; else whether a record is selected. No session fact carries the selection today: M77-2 adds
`RecordSelectionObserved(recordRef)`, posted on change by the records table's adapter (a reference, never the record's
text). The view renders `AiSetupState.suggested` and decides nothing.

### 7.4 Cancel

Cancel (or Esc, or the window's close) on any screen before the Summary: `AiSetupCancelled`. Nothing further is
applied; what was applied stays — and when this wizard turned external access on, the closing line is *"External access
is still on — Turn it off"* with **Turn it off** as one click (`ExternalAccessRequested(off)`, needing no confirmation;
review R1-19; rolling back instead is OD-77.11); a pending question is **returned, not dropped silently**: a start-box question stays
in the box with the note *"Set up was cancelled, so your question was not placed — it is still in the box"*
(`StartPagePrompt.CARRY_DROPPED_NOTE`'s successor); a record-Ask draft is discarded with *"Set up was cancelled — ask
again from the record when you're ready."* The status line names what is in force.

## 8. The `aiSetup` session node (rule 9)

### 8.1 One node: `aiSetup` grows out of `assistantProfile`

r1 put a new `aiSetup` upstream of `assistantProfile`. The review showed that order cannot work (R1-1): the decisions
given to `aiSetup` — "nothing set up", the first-run block, the derived choice, the AI line, a refused mid-turn Apply,
Safe re-selected after a cancelled Full — all need the profile in force and the Apply/confirm outcomes, which are
`assistantProfile`'s state, and a node cannot read its child within a cycle. Observing those facts a second time
upstream would be the same value recomputed in a second place (rule 9). **r2: there is one node.** Since §14.3 retires
the cards, `AssistantProfileChoice` becomes **`AiSetup`** (node id `aiSetup`), the ONE owner of the profile choice and
of the wizard:

- **Kept from `AssistantProfileChoice`:** the observed profile (`onProfileObserved`), what the in-app assistant can do
  (`onAvailability`), the Apply decision with its mid-turn refusals (`onProfileChosen`), Full's confirmation
  (`confirmingFull`, `onFullProfileAnswered`), the applied result (`onProfileApplied`, which requests
  `ChooseAssistantModeEffect`), the first-use handlers, `interceptedStartPage`, and `ProfileState` in the snapshot
  (the header chip and the quick switch render it).
- **Changed:** first use requests `ShowAiSetupEffect` instead of `ShowAssistantProfilesEffect`, and its once-per-session
  `offered` latch goes (§5 entry 5). The Full guard reads *the surface that asked is still open* — the wizard's `open`
  for `origin=SETUP`, nothing for `origin=MENU` (the quick switch, whose confirmation is the whole interaction) —
  instead of the cards' `open`; without this a Full confirmed from the wizard is silently cancelled (★ T4). `status()`
  becomes `AiSetupState.line` (§6.0, §6.6).
- **Added:** the wizard's steps, the checks, the external-client parts, the suggested action — §8.2–§8.4.
- **Pure helpers, not nodes,** keep the class readable: `AiSetupLines` (every sentence the node publishes, so
  `UserVisibleJargonTest` and `FaqSecurityContractTest` read one class), `ClaudeAuthParse` (§8.6),
  `AiSetupDerived` (§14.1's table). The node calls them; they decide nothing about WHEN.

**Place in the graph:** where `assistantProfile` is today — downstream of `localAgentSession` and `assistantLoop`
(it reads whether a turn is open, and whether a Send was accepted, after they handled the fact), upstream of
`startPagePrompt` (a JavaBean parent of it, as now) — plus two new JavaBean parents, `actionListener` (its
`ListenerState`: external, closed for session) and `openLog` (`isOpen()`). Both are already upstream of
`startPagePrompt`; `localAgentSession` pushes into `actionListener` (`need`/`released`) earlier in the same cycle, so
`aiSetup` reads the listener after the push. Regenerate: `mvn -Pregen process-classes` (expected, not avoided — rule
9). M77-1 checks the generated order (`SessionProcessor`'s dispatch for `AssistantProfileApplied` and
`RestSwitchObserved`) before building on it; rule 6 applies to Fluxtion's own propagation semantics too.

### 8.2 `AiSetupState` (in `SessionSnapshot`)

Enums, ids, times and the node's own words only — never a path, a key, raw CLI output, an e-mail or an organisation.

| Field | Type | Meaning |
|---|---|---|
| `open` | boolean | the window is open (one instance) |
| `firstRun` | boolean | the start page shows the first-run block (§6.0) |
| `origin` | `FIRST_RUN · MENU · START_LINE · SEND · ASK · SETTINGS` | why it opened |
| `step` | `CHOICE · INSIDE · CLIENT · OTHER · SUMMARY` | the screen |
| `choice` | `NONE · NO_AI · CLIENT · INSIDE · BOTH · IN_APP` | the person's Screen 1 choice in this wizard |
| `inForce` | same enum | the choice in force, DERIVED (§14.1) — what Screen 1 preselects and the AI line says |
| `posture` | `SAFE · FULL` | the sub-choice |
| `installed` | `Check(verdict, version, pathRef)` | verdict `UNCHECKED · RUNNING · FOUND · NOT_FOUND · DID_NOT_RUN` |
| `signedIn` | `Auth(verdict, method, plan)` | verdict `UNCHECKED · RUNNING · SIGNED_IN · NOT_SIGNED_IN · UNKNOWN`; method `CLAUDE_AI · API_KEY · API_KEY_HELPER · OTHER`; plan a short word from a fixed list or "" |
| `client` | `CLAUDE_CODE · CODEX · GENERIC` | 2b part 1 |
| `externalAccess` | `OFF · CONFIRMING · ON · CLOSED_FOR_SESSION · OTHER_INSTANCE` | from `ListenerState` + readiness (review R1-18) |
| `registration` | `UNCHECKED · RUNNING · PRESENT · ABSENT · UNKNOWN · NO_LAUNCHER · COPIED · REGISTERED · FAILED` | 2b part 3 |
| `live` | `Live(verdict, at, labelRef)` | verdict `IDLE · WAITING · WAITING_LONG · SEEN · OTHER_INSTANCE · ACCESS_OFF` |
| `confirming` | `NONE · FULL · EXTERNAL · REGISTER` | which confirmation is showing (the view disables the wizard behind it) |
| `pending` | `Pending(kind, ref)` or none | the waiting question (§12); kind `START_BOX · ASK_DRAFT` |
| `finished` | `Finished(request)` or none | Finish was pressed for Apply `request` — read by `startPagePrompt` for placement (§12) |
| `suggested` | `Suggested(kind)` | §6.4's row |
| `line` | String | the ONE status line of the screen, the start page's AI line (§6.0) and the panel's empty-state line (§6.6) — the node's words, refusals included |

### 8.3 Facts (`SessionEvents`)

| Fact | Posted by | Notes |
|---|---|---|
| `AiSetupRequested(origin)` | the view (menu, link, button) | the person's act |
| `AiSetupStartupObserved(dismissed, client, restLaunch)` | the frame, once at start-up with the other start-up facts | the two new keys and whether the process was started with `--rest` (review R1-16 a, b) |
| `AssistantProfileChosen(request, profile, origin)` | the wizard, the quick switch, Settings' profile write | **gains `origin`** `SETUP · MENU · SETTINGS` (review R1-14); the record is `(long request, AssistantProfile profile)` today |
| `AssistantProfileApplied(request, ok, profile, reason, origin)` | the Apply performer | **gains `origin`**, echoed from the request, so `startPagePrompt` knows a wizard Apply from any other (§12) |
| `AiSetupChoiceMade(choice)` / `AiSetupPostureChosen(posture, fullMode)` / `AiSetupClientChosen(client)` | the view | |
| `AiSetupBack` / `AiSetupContinue` / `AiSetupCancelled` / `AiSetupFinished` / `AiSetupFirstRunDismissed` | the view | Continue carries the screen it was pressed on; a press on a screen that is no longer current is stale |
| `AiSetupCheckAgain(check)` | the view | `INSTALLED · SIGNED_IN · REGISTRATION · LIVE · READINESS` |
| `StartPageAvailability(…, claudeCodeFound, …)` + `AiClientsObserved(codexFound)` | the frame, on `ObserveAiClientsEffect` | file existence only; Claude's "found" is the field `StartPageAvailability` already carries, not a second resolver read (review R1-16 d) |
| `ClaudeObserved(probe, outcome, version, pathRef)` | probe adapter | outcome `FOUND · NOT_FOUND · DID_NOT_RUN · TIMED_OUT` |
| `ClaudeAuthObserved(probe, outcome, loggedIn, method, plan, keySource)` | probe adapter | parsed at the boundary (§8.6) |
| `ExternalAccessRequested(request, on, origin)` | every door: the wizard, Settings (on save), the AI menu checkbox, `McpSetupDialog` | decided by **`actionListener`**, which owns the listener (review R1-7) |
| `ExternalAccessAnswered(request, confirmed)` | the confirmation's performer | **new name**: not `ExternalAccessChosen`, which is review B2's "close external access while this session runs" keyed by generation, and stays |
| `RestSwitchObserved(on)` | existing | `actionListener` decides the listener, as today |
| `McpReadinessObserved(status)` | the frame's existing 5 s readiness poll (the observation that feeds `McpIndicator`) | posted on change; `READY · STARTING · OTHER_INSTANCE · OFF`. The poll stays an adapter's observation; the reclaim decision it also makes today in the view is out of M77's scope and recorded as a follow-up (§19; review R1-16 e) |
| `RecordSelectionObserved(recordRef)` | the records table's adapter | posted **once, at Finish**, on `RequestSelectionEffect` — not on every selection change (review R1-30) |
| `McpRegistrationObserved(probe, client, status)` | registration adapter | `PRESENT · ABSENT · INDETERMINATE` (the client classes' own enums) |
| `RegisterAnswered(request, confirmed)` | the confirmation's performer | |
| `McpRegistrationChanged(request, client, status)` | registration adapter | the client classes' `Status` |
| `ExternalClientSeen(watch, at, labelRef, fullSessionSinceArm)` | `ActionServer` via its performer (§9.4) | once per armed watch |
| `AiSetupTimerFired(watch)` | the timer adapter | the 2-minute tip; time is a fact, never a Swing timer that decides |
| existing: `AssistantProfileApplied`, `AssistantProfileObserved`, `AssistantFullProfileAnswered`, `StartPageAvailability`, `LocalAgentDraftComposed`, `StartPagePromptSent`, the Send/Start facts | | read, not changed |

Every probe, watch and request carries an id minted by `aiSetup`; a fact for an id that is not current (Check again
pressed again, Back, Cancel, the window closed) is logged `staleFact` and changes nothing — the M74 pattern.

### 8.4 Effects (`SessionEffects`)

| Effect | Performer | Reports |
|---|---|---|
| `ShowAiSetupEffect(step)` / `CloseAiSetupEffect` | the frame (creates or fronts the one window) | — |
| `ObserveAiClientsEffect` | the one resolver of §6.1 for Claude Code and Codex (no process) | `StartPageAvailability`, `AiClientsObserved` |
| `ProbeClaudeEffect(probe)` | resolve, then `claude --version` (5 s, the adapter's child environment — §8.6) | `ClaudeObserved` |
| `ProbeClaudeAuthEffect(probe)` | `claude auth status --json` (10 s) | `ClaudeAuthObserved` |
| `ConfirmExternalAccessEffect(request)` | requested by `actionListener`; SafeChoice (§9.2) | `ExternalAccessAnswered` |
| `SetExternalAccessEffect(request, on)` | requested by **`actionListener`**; writes `assistant.rest`, saves, posts `RestSwitchObserved` — **the only writer** of `assistant.rest` (Settings, the AI menu and `McpSetupDialog` post `ExternalAccessRequested` instead; the `--rest` start-up write stays as M19.7's, before the session exists) | `RestSwitchObserved` |
| `PersistSetupDismissedEffect` / `PersistSetupClientEffect(client)` | write the two new machine-tier keys | — |
| `RequestSelectionEffect` | the records table reports the selected record | `RecordSelectionObserved` |
| `CheckMcpRegistrationEffect(probe, client)` | `ClaudeMcpClient.registration()` / Codex's | `McpRegistrationObserved` |
| `ConfirmRegisterEffect(request, client)` | SafeChoice with the verbatim command (§9.3) | `RegisterAnswered` |
| `RegisterMcpEffect(request, client)` | `ClaudeMcpClient.add` / Codex's `add` | `McpRegistrationChanged` |
| `CopyToClipboardEffect(what)` | the frame | — |
| `WatchExternalClientEffect(watch)` / `UnwatchExternalClientEffect(watch)` | the adapter that owns the action server's lifecycle holds the armed watch and **re-applies it to a rebuilt server** (review R1-18); the server makes the one-shot report | `ExternalClientSeen` |
| `AiSetupTimerEffect(watch, seconds)` | a scheduler | `AiSetupTimerFired` |
| `ApplyAssistantProfileEffect`, `ConfirmFullProfileEffect` | requested by `aiSetup` (the same node, §8.1) | unchanged performers |
| — | placement is P-2's: `startPagePrompt` composes, as it does for the carried question today (§12) | P-2's facts |
| `RunSuggestedActionEffect(kind)` | the frame's existing actions (open sample, compose an Ask draft, start the tour, copy) | the existing facts |

**The status line is the node's** (`AiSetupState.line`), never composed by the view or the performer (rule 9; the
B5 lesson in `ActionListener`).

### 8.5 The mode before a choice

Robot #8: before any profile is chosen `assistantMode` is `IN_APP`, so an Ask draft is composed for the in-app assistant
and `localAgentSession.onDraftComposed` drops its binding (`noOp`). If P-2 has introduced an unbound "no assistant yet"
state, M77 uses it. If not, **`AssistantMode.NONE`** is added (OD-77.6) — and r1's version of it would have broken
existing users (review R1-3): `postStartupFacts` posts `AssistantModeChosen` only when `claudeCodeLastMode` is
`LOCAL_AGENT`, so an in-app user with a stored key would start in NONE and every Send would be refused. r2:

- **The start-up mode is derived from the profile in force,** for every profile: the frame posts
  `AssistantModeChosen(AssistantProfiles.inForce(config).mode())` at start-up (IN_APP included), replacing the
  `claudeCodeLastMode`-only post; `inForce` already maps a key stored before profiles to IN_APP, so upgraders keep
  answering. `AssistantProfile.NONE.mode()` and `OFF.mode()` return NONE. ★ T21: an upgrader with a key and
  `lastMode=IN_APP` can Send on first launch (witness: keep the `LOCAL_AGENT`-only post).
- **Under NONE** neither assistant's node accepts a Send or a draft — each refuses in its own handler, as for the other
  mode (M74 R2-7; today `AssistantLoop` refuses only on `localAgent()`, so it gains the NONE case) — and a draft composed
  under it is held pending (§12).
- **Every mode check is enumerated in M77-1** (about 28 sites across `AssistantPanel`, `AssistantSurface`,
  `LocalAgentState`, `StartPageState`, `MainFrame` and the two assistant nodes), each changed to an exhaustive `switch`
  over `AssistantMode` so the compiler finds the next one; the list is in the PR description and T21 covers the
  nodes' handlers.

### 8.6 The parse boundary and the child environment

- **Same environment as Start, for the selected posture.** The probes run with the environment the local-agent adapter
  builds for the posture chosen on 2a (`provider.environment(posture, path)`, with `ChildPath.extend` only for Full —
  §6.2, review R1-15), so a Finder-launched {Product} sees what Start will see, including whether `ANTHROPIC_API_KEY` is
  set at all.
- **An empty working folder** (review R1-6). Verified 2026-10-18 (§9): `claude auth status` **reads the working
  folder's `.claude/settings.json`** — a project's `apiKeyHelper` changed its answer to `api_key_helper` — though it did
  not run the helper in that run. A folder's settings are exactly what folder trust (§11) is for, so every probe and
  every `mcp get`/`mcp add` runs in a **fresh empty directory under the analyser's private scratch area**, created per
  probe and holding none of the three trust files, never the project root or the process's own working directory
  (S10, ★ T22). The user-level settings still apply, as they do in the person's terminal.
- **`auth status` output carries personal data.** Verified 2026-10-18: the JSON has `email`, `orgId` and `orgName`
  besides the fields used. The parser reads ONLY `loggedIn`, `authMethod`, `subscriptionType`, `apiKeySource` and
  `apiProvider`, by name, and drops the rest at the boundary; raw output is never logged, shown, recorded or put in a
  fact (the `ClaudeMcpClient.Result` rule: "raw Claude Code output is not sent to the UI or logs"). ★ test with a
  fixture holding all three.
- `method` maps `claude.ai` → `CLAUDE_AI`, `api_key` → `API_KEY`, `api_key_helper` → `API_KEY_HELPER`, anything else →
  `OTHER` (rendered as *Could not tell*, §6.2).
- `plan` is mapped from a fixed list (`pro`, `max`, `team`, `enterprise` → *Pro / Max / Team / Enterprise plan*);
  anything else renders as *"your Claude plan"*, never echoed.

## 9. The detection checks, verified against the CLI

**Rule 6: read, never infer.** Verified on 2026-10-18 against Claude Code **2.1.282**, with the CLI's own help and two
read-only runs under an isolated `HOME` and `CLAUDE_CONFIG_DIR` (no prompt is sent by either command; no real key was
used — the API-key case used a made-up key):

| Fact | Evidence | Consequence |
|---|---|---|
| `claude --version` prints `2.1.282 (Claude Code)` | run | parse `^\d+\.\d+\.\d+`; anything else is `DID_NOT_RUN` |
| `claude auth status [--json \| --text]`: *"Show authentication status"*; `--json` is the default | `claude auth status --help` | always pass `--json` explicitly (a default can change) |
| signed in with a subscription: exit 0, `loggedIn:true`, `authMethod:"claude.ai"`, `subscriptionType:"max"`, plus `email`, `orgId`, `orgName` | run (keys only printed) | §8.6 drops the personal fields |
| signed out: **exit 1**, `loggedIn:false`, `authMethod:"none"` | isolated `HOME`/`CLAUDE_CONFIG_DIR` | the verdict reads `loggedIn` from the JSON, not the exit code; exit 1 *with* parseable JSON is `NOT_SIGNED_IN`, without it `UNKNOWN` |
| an API key in the environment: exit 0, `loggedIn:true`, `authMethod:"api_key"`, `apiKeySource:"ANTHROPIC_API_KEY"` — **even for a made-up key** | isolated run with a fake key | `auth status` does not validate a key. The wizard's words say *"uses an API key; Claude Code checks it when it starts"*, never "signed in" for that case |
| a project's `.claude/settings.json` in the **working folder** with an `apiKeyHelper`: exit 0, `loggedIn:true`, `authMethod:"api_key_helper"`, `apiKeySource:"apiKeyHelper"`; the helper (a command that would have created a marker file) **did not run** | isolated `HOME`/`CLAUDE_CONFIG_DIR`, the settings file in the cwd only | `auth status` reads the cwd's project settings, so the probes run in an empty folder (§8.6); "did not run" is one observation on 2.1.282, not a guarantee, which is why the folder is pinned rather than trusted |
| `claude auth login [--claudeai \| --console \| --sso]` signs in | `claude auth login --help` | the one command the fail state copies; the wizard never runs it (N4) |
| `claude mcp get <name>`: *"approved servers are health-checked unless disabled for this project"* | `claude mcp get --help` | run only on **Check registration**, never automatically, and never while the live check is armed (§9.4) |
| `claude mcp add … -s, --scope <local\|user\|project>` (default `local`) | `claude mcp add --help` | the wizard always passes `--scope user` (M42.4) — the existing `ClaudeMcpClient.addCommand` |

A CLI-drift canary for these shapes is the M74 item *CLI-drift canary* (tracker, review B8); M77 adds its four
`auth status` fixtures (subscription, signed out, API key, API key helper — personal fields replaced) to that canary's
corpus.

### 9.1 Timeouts and processes

`--version` 5 s; `auth status` 10 s; `mcp get` 10 s (`ClaudeMcpClient.DEFAULT_TIMEOUT`); `mcp add` 10 s. A probe that
times out is killed with its process tree. At most one probe of each kind runs; Check again while one runs is refused
(*"Already checking"*). Closing the wizard kills nothing that has already written (an `mcp add` that started finishes;
its result fact is stale and only logged — the status line on next open re-checks).

### 9.2 Turning on external access

Today: AI ▸ *External MCP/REST access* is a bare checkbox that writes `assistant.rest` with no confirmation, and
`McpSetupDialog.confirmEnableTransport` is a stock `JOptionPane` with OK as the default (robot E/02). The wizard's
confirmation, under the SafeChoice rule:

> **Turn on External MCP/REST access?**
> *"An MCP client or a REST caller on this machine that has this run's token can then read the open log and change what
> {Product} shows. The endpoint listens on this machine only (127.0.0.1) and the token is new each run. A Full Claude
> Code session in the panel can use it too (it can read the token file in your home folder)."*
> **Turn on access** (left, never default) · **Cancel** (right, default)

The last sentence is review B2's fact (`ActionListener`'s offer to close it while a Full session runs), stated where
the consent is given.

**One owner** (review R1-7, R1-8). Today four doors write `assistant.rest` directly: the AI menu checkbox
(`MainFrame.buildAiMenu`), Settings ▸ Assistant on save (`ConfigPanel`, **with no confirmation** — r1 wrongly said it
had one), `McpSetupDialog.confirmEnableTransport` (a stock OK-default `JOptionPane`), and `--rest` at start-up. From
M77-3 the first three post `ExternalAccessRequested(request, on, origin)`; **`actionListener` decides**: turning on
requests `ConfirmExternalAccessEffect`, and only `ExternalAccessAnswered(confirmed)` for that request leads to
`SetExternalAccessEffect`; turning off goes straight to it. So *when to confirm* is decided in one node for every door,
not in a view. Whether the AI menu's and Settings' *on* get the confirmation is OD-77.4 (recommended: yes — the same
node, the same dialog; off stays one click). `--rest` stays M19.7's announced persistence: an agent launched it and
nobody is at the screen to confirm.

### 9.3 Registration

- **Register for me…** → SafeChoice: title *"Register {Product} with Claude Code"*; body *"This runs, on this machine:"*
  and the **verbatim argument vector** `ClaudeMcpClient.addCommand(bridge)` would execute, in a read-only, selectable,
  monospaced, wrapping area (after P-1's fix), then *"It adds fluxtion-analyser to your user-level Claude Code
  settings. {Product} does not read or edit that file itself."* — **Register** (left) · **Cancel** (right, default).
  Codex: the same with `CodexMcpClient`'s vector.
- A present registration found by **Check registration** offers **Replace…** with today's replace semantics (a confirmed
  remove, then add — both vectors shown).
- **I'll run it myself** copies `ClaudeMcpClient.userCommandForCopy` (plain `claude`), unchanged.
- **`McpSetupDialog` stays** behind 2b's **More options…** for what the wizard does not do (project-scope copy, remove,
  Claude Desktop's note, *Check connection*). Its three stock `JOptionPane` confirmations (registration change, replace
  snippet, enable transport) are converted to SafeChoice in M77-3 and its enable posts `ExternalAccessRequested`
  (review R1-8); the SafeChoice frame test covers them. Retiring the dialog instead is OD-77.12.
- The registered command names this copy's launcher (jar path / JBang). After an upgrade it can point at an old copy;
  the Summary's line *"registered"* is therefore *"registered (for this copy of {Product})"* and the robot's "brittle
  after an upgrade" finding is a follow-up (§19), not solved here.

### 9.4 The live check: `ExternalClientSeen`

- **Armed** by `aiSetup` with `WatchExternalClientEffect(watch)` only when part 4 is current AND `actionListener` says the
  listener is external. Disarmed on leaving part 4, Cancel, Finish, or access turned off.
- **What counts:** the first request to `/action` after arming that (a) carries no hosted credential, (b) passed the
  shared-token check, and (c) passed the rate limit. Not `/manifest`, not a refused (401/403/429) call. A `400` from the
  dispatcher still counts — the client reached this window. The server posts once, then disarms itself.
- **What is excluded:** the analyser's own `McpConnectionProbe` (*Check connection* in `McpSetupDialog`). The probe
  starts the bridge as a child process; it passes an environment flag the bridge turns into a marker header, and a
  marked request never counts. An external caller can set the marker too — that only hides it from the check (a false
  negative, never a false positive).
- **Why the person is asked to call a tool:** verified in the bridge (review R1-28): `initialize`, `server/discover`
  and `tools/list` are answered locally by `McpBridge` (its request switch), and only `tools/call` forwards to `/action`.
  So an MCP client's start-up never reaches this window, and nor does `claude mcp get`'s health-check unless it calls a
  tool (its help says it health-checks; it does not say it calls tools — the watch is never armed while a registration
  check runs, so the question does not arise). A `tools/call` is the earliest honest proof, and `analyser_context` is
  read-only. The CLI-drift canary (§9) re-checks the bridge's local answers when the MCP revision moves.
- **What it does not prove**, said in its words: which client it was. With OD-77.8 the bridge forwards the client's
  self-reported `clientInfo.name` as a display label (ASCII, ≤ 40 characters, sanitised; never trusted for anything
  else). A Full Claude Code session in the panel can also reach the endpoint (B2); if one **existed at any time since
  arming** (`fullSessionSinceArm`, from `actionListener`'s state — review R1-18), the ✓ line adds *"A Full Claude Code
  session ran in the panel while this was checked; this check cannot tell them apart."*
- **`OTHER_INSTANCE`:** another window owns the well-known endpoint file, so this window's server never sees the call.
  `aiSetup` reads `McpReadinessObserved` and shows §6.3's fail state instead of waiting forever.
- **No polling by hand:** the server reports; the 2-minute tip is `AiSetupTimerFired`.

## 10. The Safe / Full sub-choice

The sub-choice is the profile cards' Safe/Full pair, moved (§14.3). Full is confirmed by the **existing**
`ConfigPanel.fullPostureDialog` (title *"Full Claude Code — read this first"*, *Turn on full Claude Code* at the left,
**Cancel** the default at the right), requested by `aiSetup` (grown out of `assistantProfile`, §8.1) exactly as the cards request it today (PR151 F4).
Re-applying Full when Full is already in force asks nothing (unchanged). The Full mode is chosen inline; `bypassPermissions`
and `dontAsk` are not offered (unchanged, M74 §3). A posture change while a session exists still offers New chat (M74
§3: never resumed under another posture) — the Summary adds *"Your next question starts a new conversation."* when the
profile changed under an existing session.

## 11. Folder trust stays at the first Start

The wizard asks nothing about folders. `LocalAgentConsents.of` is unchanged: the analyser's scratch folder counts as
confirmed (for Full only while it holds none of the three trust files — review finding 3); a project folder is asked
about at its first Start, by the existing *"Full Claude Code — confirm this folder"* / Safe folder confirmations. The
newcomer path (the sample, which runs in the scratch folder) therefore meets no folder question, as the robot found
(Journey B). The wizard's folder note (§6.2) says when the question will come.

## 12. The pending question

The question that opened the wizard (entry 5) is **pending**: held, unbound, never sent. M77 reuses P-2's mechanism
(the generalisation of `StartPagePrompt.carried` to every Ask source) and changes only the trigger:

- **Placement waits for every one of** (review R1-2): (a) Finish pressed (`AiSetupState.finished`); (b)
  `AssistantProfileApplied(ok)` for that wizard's Apply request (`origin=SETUP`); (c) the `AssistantProfileObserved`
  that follows it (the performer posts consent, availability and the observation as separate operations — the wait
  `StartPagePrompt.onProfileObserved` already does for the carried question); and (d) the mode in force being the
  profile's mode (`AssistantModeChosen` from the `ChooseAssistantModeEffect` that `onProfileApplied` requests). r1
  placed at Finish alone; for *Claude inside*, Finish IS the Apply, so the draft would have been composed while the mode
  was still IN_APP/NONE, `onDraftComposed` would have dropped its binding, and `staleDraft` would have refused it —
  robot #1 rebuilt. In *Both*, (b)–(d) are met on 2a and placement waits for (a) on 2b, so the question is never
  placed in a panel behind the wizard.
- **One decider:** `startPagePrompt` (P-2's node, which already holds the carried question and parents both `aiSetup`
  and `assistantMode`) places on the first fact after which (a)–(d) all hold; `aiSetup` only publishes (a) and the
  Apply request. ★ T12's witness is r1's rule, "place on Finish".
- **Placement replaces, never appends** (P-2's fix (c)): the placed draft is bound for the assistant now in force, so
  `localAgentSession.staleDraft` sees a known binding (★ witness: the robot's B/02–B/06 sequence).
- **Choices that cannot answer** (No AI, own client only, Claude not found): the question is returned as §7.4 says, with
  the line *"No assistant in the panel can answer this — your question is still in the box."* For own client only:
  *"Ask it in your terminal client — Copy question."*
- **Nothing is sent.** Placing is all. The person's Send is the act (G7).

## 13. Safety invariants, each with its test

| # | Invariant | Test (§16) |
|---|---|---|
| S1 | The wizard sends nothing to a model: no `LocalAgentSendRequested`, `AssistantSendRequested` or stdin write in any wizard state | ★ T5 |
| S2 | No process is started before the person's act on the choice that needs it; the only processes are `claude --version`, `claude auth status --json`, and — on an explicit button — `claude mcp get` / `mcp add` (or Codex's) | ★ T6 |
| S3 | Full is applied only after its confirmation's risky button; Cancel, Esc, close and a late answer apply nothing | ★ T3, T4 |
| S4 | External access is turned on only after its confirmation; Cancel default | ★ T9 |
| S5 | Registration runs only after its confirmation shows the verbatim vector that is then executed (the same `List<String>`, not a re-render) | ★ T10 |
| S6 | Folder trust is not asked or recorded by the wizard | T13 |
| S7 | No personal field of `auth status` reaches a fact, the snapshot, a log, a recording or the screen | ★ T7 |
| S8 | The live check never reports ✓ without an authenticated external `/action` after arming | ★ T11 |
| S9 | Nothing the wizard writes is exported (machine tier, like M73/M74's consents) | ★ T14 |
| S10 | No probe and no registration command runs in the project folder or the process's working directory; each runs in a fresh empty scratch directory (§8.6) | ★ T22 |

## 14. Coexistence

### 14.1 The same keys, and a derived choice

The wizard writes only what exists: `assistant.claudeCode.profile`, `assistant.claudeCode`, `assistant.claudeCode.full`,
`.full.mode`, `.model`, `.lastMode`, the in-app provider/model/key (all via `AssistantProfiles.apply`), and
`assistant.rest` (via `SetExternalAccessEffect`). Two new machine-tier keys: **`assistant.setup.dismissed`** and
**`assistant.setup.client`** (`CLAUDE_CODE · CODEX · GENERIC`, written at 2b's Finish). External access alone is not a
client choice (review R1-5): `--rest` and the capture harness leave it on with nobody having chosen anything. The choice
in force is derived:

| Profile in force | `assistant.setup.client` | Choice shown |
|---|---|---|
| NONE | unset | not set up (whatever `assistant.rest` says) |
| OFF | unset | No AI |
| OFF or NONE | set | Your own client |
| SAFE / FULL | unset | Claude inside (Safe / Full) |
| SAFE / FULL | set | Both |
| IN_APP | unset / set | In-app (Other options) / In-app + your own client |

Whether a client can reach this window *now* is a separate fact (external access on, readiness), shown on the AI line
(§6.0) — not part of the choice.

So a person who changes Settings later sees the wizard reflect it next time, and nothing can disagree with Settings.

### 14.2 Settings ▸ Assistant

Keeps every option. Gains **Set up AI…** at the top of the page (§5 entry 6). Its Full control keeps its confirmation;
its external-access control has **none today** (review R1-8) and, from M77-3, posts `ExternalAccessRequested` on save,
so it gets the one node's decision (§9.2, OD-77.4).

### 14.3 The profile cards — retired as the entry point

Recommended (OD-77.3): **`ProfileOverlay` is removed.** Its content has a home in the wizard (Safe/Full in 2a; In-app in
6.5; Off on Screen 1), so keeping it would keep two surfaces that write the same settings with two sets of words —
robot #4's problem. **Assistant ▾** becomes a small menu, rendered from `ProfileState`:

> ✓ Safe analysis · Full Claude Code… · In-app assistant · Off · ─── · **Set up AI…**

A quick switch posts `AssistantProfileChosen(request, p, origin=MENU)`; Full from the menu raises the same confirmation
(F4). `aiSetup` decides, not the menu (review R1-24): a switch to Safe or Full while `claude` is **not found** (the
resolver's file check, which needs no probe and survives a restart), or to In-app with no stored key, opens the wizard
at that screen instead of applying. `ProfileOverlayFrameTest`, `ProfileChooserLayoutTest` and the cards' parts of
`UnifiedAssistantFrameTest` are replaced by the wizard's tests; their witnesses that still apply (Full needs its
confirmation; Apply mid-turn refused) move to T3/T8.

### 14.4 The other doors

- **AI ▸ Connect an AI client…** → **AI ▸ Set up AI…** (opens at Screen 2b when the choice in force includes a client;
  Screen 1 otherwise). `McpSetupDialog` stays, reachable from 2b's **More options…** link for what the wizard does not
  do (project-scope copy, remove, Claude Desktop's note, *Check connection*).
- **AI ▸ External MCP/REST access** stays as a checkbox (OD-77.4 for its confirmation).
- The start page's **Connect an assistant** group (Codex / Claude / Other MCP client) is removed; the AI block or line
  (§6.0) replaces it. Their tooltips' sentences move into 2b.
- The panel's **Configure provider** and **Connect a CLI assistant** buttons show only in the mode they belong to
  (in-app), which is the robot's #4 fix for the panel and is rendered from the mode in the snapshot.
- The in-app API-key route lives behind **Other options** (§6.5) and Settings; it is no longer a first-class card.

## 15. Accessibility, keyboard, themes, widths

- **Keyboard:** every control reachable by Tab in reading order; Screen 1's radios move with the arrow keys; Enter is
  the primary button (Continue / Finish / Done) **except** inside a SafeChoice, where Enter is the safe choice; Esc is
  Cancel (or Done on the Summary); Back is Cmd+[ on macOS and Alt+Left elsewhere (review R1-29); Enter fires the
  focused button, as §6 says. Focus lands on the first unanswered control of each screen (the selected
  radio; the failing check's **Check again**; the key field on 6.5).
- **Screen readers:** each card's accessible name is its title and its description its sentence; each check row is one
  accessible label (*"Installed: Claude Code 2.1.282 found"*), **focusable** so a screen-reader user can Tab to the
  result, updated in place; the change is also fired as an accessible-name change, but because VoiceOver's
  announcement of that is unreliable under Java (review R1-29), the result never depends on it being heard. The step
  indicator is the window's accessible description.
- **Never colour alone:** ✓ ✗ ⏳ ? are glyphs with words; colours from `UiTheme` tokens; light, dark and high-contrast
  FlatLaf themes; text scales with the font size setting.
- **Widths.** The window: 640 px default, **520 px minimum** (the pop-out panel's narrow width), every screen one
  column, wrapping labels sized to their width (the P-1 lesson: a wrapping area is given its width before `pack()`),
  the command line in 2b scrolls horizontally inside its own box rather than widening the window. **The docked panel at
  ~200 px** shows only §6.6's line and button, which wrap. **The start-page block** reflows its four radios to two rows
  below 560 px.
- No animation; ⏳ is a static glyph plus words.

## 16. Tests (rule 8: ★ marks a test with a wrong-result witness)

Headless (`AiSetupTest`, `ClaudeProbeParseTest`, …) unless marked *frame* (a registered frame suite under Xvfb in CI).

| # | Test | Catches | Witness (the mutation that must turn it red) |
|---|---|---|---|
| ★ T1 | each Screen 1 choice + Finish maps to the right `AssistantProfileChosen` and access facts (§7.2, §14.1 table); the derived choice round-trips for every row of §14.1 | a choice writing the wrong settings | map `CLIENT` to `SAFE` |
| ★ T2 | first use: Send / Start / start-page question / **a record Ask (the draft-composed fact)** with nothing set up → `ShowAiSetupEffect`, not `ShowAssistantProfilesEffect`, and the Ask's draft held pending; an assistant that can answer is never interrupted; a key stored before profiles never triggers it | the cards or a dead end on first use; an Ask that opens nothing (R1-4) | open the cards from first use; or ignore the draft-composed fact |
| ★ T3 | Full from the wizard: confirmed → applied; Cancel/Esc/close → Safe re-selected, nothing applied | Full applied without consent | apply on `AssistantProfileChosen(FULL)` directly |
| ★ T4 | a Full confirmation answered after the wizard closed applies nothing; one answered while the wizard is open applies (the `origin=SETUP` guard); a cancelled one leaves Safe selected and the step unchanged | a late consent applied, or a wizard Full silently dropped | keep the guard reading only the cards' `open` |
| ★ T5 | across every state × every act, no Send fact or stdin write is produced | S1 | post `LocalAgentSendRequested` on Finish |
| ★ T6 | no probe effect before the Continue that needs it; Screen 1's badges request only `ObserveAiClientsEffect`; `mcp get` only on its button | S2 | probe on window open |
| ★ T7 | the auth parser over three fixtures (subscription, signed out exit 1, API key) gives the verdicts of §9; a fixture with `email`/`orgId`/`orgName` leaves none of them in the fact, the snapshot, the audit log or the rendered text | S7, a personal-data leak | keep the whole JSON map |
| ★ T8 | stale facts: a `ClaudeObserved`/`ClaudeAuthObserved`/`McpRegistrationObserved`/`ExternalClientSeen` for an old probe or watch id changes nothing | a late result painting the wrong check | drop the id comparison |
| ★ T9 | external access, from **each of the four doors** (wizard, AI menu, Settings, `McpSetupDialog`): on only after `ExternalAccessAnswered(confirmed)` for that request (as OD-77.4 decides for the menu and Settings); off at once; the dialog's default and Esc are Cancel (*frame*); `actionListener` decides | S4 | write `assistant.rest` on the switch press |
| ★ T10 | registration: the vector shown equals the vector executed — the performer executes the `List<String>` the dialog rendered (`ClaudeMcpClient.add` / `CodexMcpClient.add` gain an overload taking it; today each rebuilds it from `addCommand(bridge)`); Cancel runs nothing; the dialog's default is Cancel (*frame*) | S5, an OK-default dialog | re-render the command for execution |
| ★ T11 | live check: ✓ only after an authenticated external `/action` post-arm; not for `/manifest`, a 401/403/429, a hosted-credential call, a marked probe call, or a call before arming; `OTHER_INSTANCE` shows its fail state | S8, a false proof | count `/manifest` |
| ★ T12 | pending question: Ask before set-up → wizard → Finish(Safe) → exactly one draft in the composer, **bound in `localAgentSession.bindings`**; Send starts a turn (not `STALE_DRAFT`); in *Both*, nothing is placed until 2b's Finish; Cancel returns it (§7.4) | robot #1's dead end, a duplicated draft | place on Finish alone (r1's rule); or append instead of replace |
| ★ T13 | the wizard never posts a folder-trust fact or writes `assistant.claudeCode.trusted*` | S6 | the wizard posts a folder-confirmed fact |
| ★ T14 | `assistant.setup.dismissed` and every key the wizard writes are machine-tier: an exported profile, share or bundle holds none | S9 | put the key in the project tier |
| ★ T15 | the AI line (§6.0) for every row of its table, from the node; the start page shows exactly one AI line (no "No AI assistant can answer here yet" beside it) | robot #5's contradiction | compose the line in `StartPanel` |
| ★ T16 | `OneDispatchModelTest` gains rules: the wizard view posts facts and renders `AiSetupState` only; **no `assistantActionsRest` write anywhere but `SetExternalAccessEffect`'s performer** (and M19.7's `--rest` start-up write); no `ClaudeMcpClient`/`CodexMcpClient`/`claude` process outside the performers; no status line composed outside `AiSetupLines` | hand-placed dispatch | write `assistant.rest` in the AI menu's listener (today's code) |
| ★ T17 *(frame)* | each screen at 640 and 520 px in light and dark: no clipped text (every wrapping area's preferred height covers its lines), primary button visible, focus order as §15 | layout regressions | today's `SafeChoice.build` packing a wrapping `JTextArea` with no width (robot D/02b) |
| ★ T18 | `FaqSecurityContractTest` and `UserVisibleJargonTest` include the wizard's strings; `{Product}` is the only spelling of the product | an unpinned "local-only" claim, jargon | spell "Canvas" in a literal |
| ★ **T19 — the robot walkthrough** *(frame, real `Main`, isolated `user.home`, 1200×800, a stub `claude` on `PATH` answering `--version`, `auth status --json` and a recorded P0 stream)* | with P-3 landed, the newcomer path in **≤ 6 acts**: (1) *Sample project*; (2) right-click the breach record; (3) *Ask: What happened here? (set up AI first)* → the wizard opens at Screen 1 with *Claude inside* preselected; (4) **Continue** → both checks ✓, Safe preselected; (5) **Finish** → the window closes, the question is placed and bound; (6) **Send** → a turn starts, and the stub's answer streams. Asserted at every step: no refusal, no hidden primary button, no second copy of the draft, no folder question | the dead end coming back by any route | revert P-2's binding (the robot's B/02 refusal returns); or show the cards on (3) |
| T20 | the capture harness gains the wizard's screens (`tools/capture-docs.py`, a fresh isolated home with no log and no `assistant.rest`, demo fixture only); **the analyser runs from a jar copied under the isolated temporary root**, so the registration confirmation's verbatim vector (jar path, `-Duser.home`) carries no personal path (review R1-27); every shot is read before commit (CLAUDE.md rule 1) | real names or paths in a screenshot | a shot whose vector names the repo checkout |
| ★ T21 | the start-up mode: an upgrader with a stored key and `lastMode=IN_APP` can Send on first launch; NONE/OFF start in NONE; under NONE both assistant nodes refuse in their own handlers | R1-3, in-app users locked out | keep the `LOCAL_AGENT`-only start-up post |
| ★ T22 | every probe and registration process is started in a fresh empty scratch directory, never the project root or the process's cwd | S10 | start the probe in the project root |
| ★ T23 | the panel's empty-state line and the start page's AI line come from `aiSetup` alone; with nothing set up the panel shows one line (no `NO_ASSISTANT*`, no *"no provider is configured"* beside it); a mid-turn Apply from the wizard keeps the step and shows exactly one line | R1-1(b), R1-17 | compose the refusal in the wizard view |
| ★ T24 | a GUI-shaped `PATH` (`/usr/bin:/bin:/usr/sbin:/sbin`) with `claude` in `~/.local/bin`: Screen 1 says found AND 2b builds a register command | R1-9 | construct the client with `ClaudeMcpClient.detect()` |

## 17. Effort (one developer)

| Part | Days |
|---|---|
| `aiSetup` grown out of `assistantProfile` (one node, §8.1), state, facts, effects; the `origin` fields; NONE mode and the derived start-up mode with its ~28 mode checks, if P-2 lacks them; regenerate | 4–5 |
| Probe adapters (`--version`, `auth status --json`) with the child environment, timeouts, parse boundary, fixtures | 1–1.5 |
| External path: `ExternalAccessRequested` through `actionListener` from four doors, registration through the existing clients built from one resolver, `McpSetupDialog`'s three dialogs to SafeChoice, `ExternalClientSeen` in `ActionServer` with the watch held across server rebuilds and the probe marker in the bridge, `McpReadinessObserved`, the timer | 3–4 |
| Wizard UI: Screens 1, 2a, 2b, 3, 6.5; the start-page block and AI line; the panel's empty state; widths and themes | 3–4 |
| Coexistence: Assistant ▾ quick switch, removing `ProfileOverlay`, the AI menu, Settings button, the start page's Connect group, the Ask items' labels | 1–1.5 |
| Tests T1–T20, the walkthrough, capture-harness shots, docs site page, CHANGELOG | 3–4 |
| **Total** | **15–20 days (about 3–4 weeks)** |

The robot estimated 11–15 and r1 13–18; the difference is the parts the robot's draft did not cost — one node for the
profile choice and the wizard (§8.1), the derived start-up mode (§8.5), the parse boundary and the empty probe folder
(§8.6), the live check's exclusions (§9.4), one owner for external access across four doors (§9.2), the start-page
block (D-S1, §6.0) and removing the overlay. Prerequisites P-1 and P-2 are not counted (in flight elsewhere); P-3 (a hard
gate of M77-2) and P-4 are about 1–1½ days together.

## 18. Phasing — PRs under about 5,000 hand-written lines

Generated `SessionProcessor` lines are not counted. Each PR is a reviewed feature branch off `main` (or the M74 stack
while it is unmerged), with its CHANGELOG lines (rule 2) and its tests green (rule 4).

| PR | Content | Ships behind | Est. lines |
|---|---|---|---|
| **M77-1 — the node and the checks** | `AssistantProfileChoice` grown into `aiSetup`; the `origin` fields; probe adapters, the parse boundary and the empty probe folder; `AssistantMode.NONE` and the derived start-up mode if P-2 lacks them; T1–T8, T12–T14, T16 (node side), T21–T23 | the window not yet reachable: first use still opens the cards until M77-2 (a one-line switch in `aiSetup`, tested both ways) | ~3,000 |
| **M77-2 — the wizard, inside path** (after P-1, P-2 and **P-3**) | Screens 1, 2a, Summary, 6.5 (the in-app details extracted from `ProfileOverlay`); entry points 2–6; the start page's AI line and first-run block; the panel's empty state; Ask labels; first use opens the wizard. **Until M77-3, *Your own client* and *Both* open today's `McpSetupDialog`** at the chosen client, with the line *"Step-by-step set-up for your own client comes in the next update; this dialog does it today."* (review R1-11); T15, T17–T19, T24 (the Screen 1 half) | — (user-visible) | ~3,500 |
| **M77-3 — the external path** | Screen 2b; `ExternalAccessRequested` through `actionListener` from all four doors; the access and registration confirmations; `McpSetupDialog`'s dialogs to SafeChoice; the clients built from one resolver; `ExternalClientSeen`, the bridge's probe marker and (OD-77.8) label; `McpReadinessObserved`; the timer; *Both*; T9–T11, T24 | — | ~3,200 |
| **M77-4 — coexistence and docs** | Assistant ▾ quick switch; `ProfileOverlay` removed (OD-77.3); AI menu renames; Settings button; the Connect group removed; capture-harness shots; the docs site's *Set up AI* page; T20 | — | ~2,000 (net, after the overlay's ~650 lines go) |

## 19. What this does not fix (follow-ups)

Robot #2 (docked layout, P-3), #3 (one primary action), #6 (*Configure provider*'s page, P-4), #7 (P-1), switching
assistants later with a draft in the composer (#1 b–d, P-2), the status bar's *"bridge listener is up (external clients
are refused …)"* beside *"MCP off"*, the header diagnostics, the registered command naming an old copy after an upgrade,
the ~4,500 `analyser-claude-*` scratch folders never removed, and the endpoint reclaim decision that the 5 s readiness
poll makes in the view today (review R1-16 e). Each is a tracker line, not M77.

## 20. Open owner decisions

| # | Question | Recommendation |
|---|---|---|
| **OD-77.1** | First run: the wizard's first screen **inline on the start page** (keeps D-S1), or a modeless window that **opens by itself** after the splash (reverses D-S1/R6)? | inline |
| **OD-77.2** | When a question is waiting, **skip the Summary** (6 acts; the summary becomes the panel's status line) or always show it (7 acts)? | skip |
| **OD-77.3** | The profile cards: **remove `ProfileOverlay`** (Assistant ▾ becomes a quick-switch menu), or keep it as an advanced view? | remove |
| **OD-77.4** | AI ▸ *External MCP/REST access*: give turning it **on** the same SafeChoice confirmation (today one click)? | yes; off stays one click |
| **OD-77.5** | *No AI* while external access is on: also turn access off? | ask with a ticked box *"Also turn off External MCP/REST access"* on Screen 1 (turning off is the safe direction) |
| **OD-77.6** | `AssistantMode.NONE`: land it in P-2 or in M77-1? | P-2, if it is not too late; otherwise M77-1 |
| **OD-77.7** | The product name in the wizard's copy: `{Product}` = "the analyser" until the rename PR? | yes |
| **OD-77.8** | Forward the client's self-reported name to the live check as a display label? | yes, labelled as self-reported |
| **OD-77.9** | Offer the first-run block to an upgrader whose profile is still NONE? | yes, once (it is not a modal) |
| **OD-77.10** | 2b's default for Claude Code: **Register for me…** first (behind its confirmation), or **I'll run it myself** first? | Register for me… first |
| **OD-77.11** | Cancel after this wizard turned external access on: keep it on with a one-click **Turn it off** (§7.4), or roll back what this wizard turned on? | keep it on, offer Turn it off |
| **OD-77.12** | `McpSetupDialog`: keep it behind *More options…* (its dialogs converted to SafeChoice, §9.3), or retire it? | keep it |
| **OD-77.13** | One node: grow `AssistantProfileChoice` into `aiSetup` (§8.1), or keep two nodes with `aiSetup` downstream and request-keyed outcome facts published by `assistantProfile`? | one node |

## Revision history

- **r1, 2026-10-18** — first draft, from the owner's direction, the robot journey report and a read of
  `feature/claude-code-p3` and `review-fix/pr-151`; CLI shapes verified against Claude Code 2.1.282.
- **r2, 2026-10-18** — the independent review of r1 folded in
  ([`review_m77_r1_7f3c.md`](../handoff/review_m77_r1_7f3c.md), with its disposition table): all 30 findings
  accepted, one in part (R1-16 c dropped as moot: SafeChoice is application-modal). The blockers: **one node** —
  `AssistantProfileChoice` grows into `aiSetup` instead of a new node upstream of it (§8.1, R1-1); **placement waits for
  Finish, the Apply, the observation and the mode** (§12, R1-2); **the start-up mode is derived from the profile** so
  in-app users are not locked out by NONE (§8.5, R1-3). Also: the Ask trigger (R1-4); external access alone is not a
  client choice and the capture harness is not a first run (R1-5, a new key `assistant.setup.client`); probes run in an
  empty folder, after verifying that `auth status` reads the working folder's project settings (R1-6, §9); one owner
  for external access across four doors, and Settings' missing confirmation corrected (R1-7, R1-8); one resolver
  (R1-9); P-3 a hard gate of M77-2 and the interim routing of the client choices (R1-10, R1-11); copy, keyboard and
  test-witness fixes (R1-12–R1-30). Effort 15–20 days; three owner decisions added (OD-77.11–.13).
