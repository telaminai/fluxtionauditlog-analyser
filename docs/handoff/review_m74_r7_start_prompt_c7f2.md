# Review — M74 r7, the start-page prompt (§5.8) and what it depends on

**Reviewer:** independent (did not write the spec). **Short id:** c7f2. **Date:** 2026-10-16.
**Subject:** `docs/specs/spec-claude-code-provider.md` at `bbb78223` (branch `feature/claude-code-provider`), mainly §5.8,
plus §5.5, §5.9, §2 OD-74.14/.19/.20, §8.2 and the P2 rows of §10.
**Method:** I read the spec, then checked each claim against the code on this branch: the start page, the demo, the
right-click and explain paths, the in-app assistant, the profile and share writers, bundles, templates and the
local-agent folder rules. No live `claude` was run. Nothing was edited or committed except this file.

Paths are repository-relative. `MF` is `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java`, and
other source paths drop the `src/main/java/telamin/fluxtion/audit/analyser/` prefix.

Severity: **blocker** means the spec as written produces a dead end, a wrong result, or breaks a refusal that is
load-bearing today. **should** means it must be fixed before build. **nit** means wording or completeness.

---

## Findings

### F1 — blocker — On the start page the Assistant tab does not exist, so "hand the draft to the Assistant tab and arm Start" cannot be done

- **Spec:** §5.8 bullet 2 (line 781): *"Send hands the draft to the Assistant tab and **arms** Start; the person presses
  Start there"*. The free-text bullet (line 787) and S2, S4 and S6 also place a draft "in the active assistant".
- **Code:** the start page and the investigation are two cards of one `CardLayout` (`MF:4517-4518`). `sideTabs`, which
  holds the Assistant dock, lives only inside the investigation card (`MF:4315`, `MF:4480`). `showWorkspace(true)` shows
  the start card and **hides the menu bar, the toolbar and the status bar** (`MF:1360-1366`). `revealAssistant()`
  (`MF:4875-4879`) only selects a tab in `sideTabs` and never changes the card. `syncRecordsCard()` puts the start card
  back whenever there is no log, graph, project or design (`MF:1356`).
- **Failure:** a first-run user with nothing open chooses S2, S4 or S6, or types a question, and presses Send. The draft
  goes to a composer they cannot see, and an armed Start sits on a hidden tab. Nothing on the page tells them where
  their question went. That is exactly the dead end OD-74.19 rules out. The same applies to the in-app column whenever
  the assistant is docked.
- **Fix:** specify where the conversation is shown when there is no workspace. Either (a) the start page hosts the
  Assistant panel itself, as a third host beside the dock and the pop-out (OA-2 already reparents one panel), or (b)
  Send pops the assistant out into its own window, the existing `showAssistantHost(false)` path, through an effect.
  Then add a frame test: from the empty start page, S6 then Send leaves the composer showing and focused, with Start
  armed and visible (witness: the current `revealAssistant()`).

### F2 — blocker — S1 and S3 end in a Start the analyser refuses, in the Claude Code mode

- **Spec:** S1 (line 793): the demo's last step *"arms the live path: Start armed"*. S3 (line 795): after the bundle
  opens, the draft is placed and Start is armed. §5.2 item 6 even gives *"Start — ask about this evidence"* as its
  example.
- **Code:** the session's working directory is the project root whenever a project is open (`MF:4750-4752`).
  - The sample project's root is `DemoAssets.root()` = `~/.fluxtion-analyser/demo` (`ui/DemoAssets.java:61-63`;
    `openSampleProject` makes it the active project at `MF:1412-1432`). `LocalAgentFolders.check` refuses anything
    inside `~/.fluxtion-analyser` as `EXPOSES_SECRETS` (`assistant/localagent/LocalAgentFolders.java:30-33`).
  - A bundle opens as a project whose root is its working copy under `~/.fluxtion-analyser/bundles`
    (`bundle/EvidenceBundle.java:411-425`). That copy is refused **twice**: as `BUNDLE` (OD-74.5;
    `session/node/LocalAgentSession.java:473-475`) and as `EXPOSES_SECRETS`.
- **Failure:** in the Claude Code mode with no process running, the person completes the demo or opens a bundle and
  presses the armed Start. It is refused, and the "never a dead end" claim fails on the two entries most likely to be
  used first. The P2 test (§10, *the start-page prompt*) checks dead ends only in the no-AI column, so it would not
  notice.
- **Fix:** decide the working directory for a project the analyser will not start in. The natural answer is the
  analyser's private scratch directory: Claude reads the evidence through the verbs, the bundle's own `CLAUDE.md` is
  never loaded (which is the reason for OD-74.5), and the header says *"working in a scratch folder: this project's
  folder is not one Claude Code is started in"*. Record this as an amendment to OD-74.5. Add a starred test in which
  S1 and S3 in the Claude Code mode end with a Start that is accepted (witness: cwd = project root).

### F3 — blocker — S3 would let a person open a bundle that did not verify, which today is refused

- **Spec:** S3 (line 795): *"the existing verification, then 'Open this bundle? It verified / did not verify: <why>'
  **Yes / No**, then the existing open"*. §8.2's failure modes list *"a bundle that did not verify (said before
  Open)"*.
- **Code:** an unverified bundle is refused, and nothing is written: `unpackOwned` returns no working copy when
  verification fails (`bundle/EvidenceBundle.java:434-436`), and `prepareBundleProfile` returns *"Evidence bundle
  refused: …"* (`MF:1514-1517`). The 1.27.0 contract is *"nothing written until verified"*.
- **Failure:** the dialog's wording offers *Yes* on *"did not verify"*. That either weakens a load-bearing refusal
  (D-T4), or gives a Yes that cannot work. Either way it contradicts the existing flow it says it reuses.
- **Fix:** *"It did not verify: <why>"* gets **Close** only, and the bundle is not opened. For a verified bundle, keep
  the existing open, with no extra confirmation unless the owner wants one. Add a starred test: an unverified `.fexp`
  through S3 is refused in its own words, and no working copy is created (witness: a Yes that proceeds).

### F4 — blocker — In the in-app mode a "pointer-only" question still sends raw record text, and the text of other records

- **Spec:** OD-74.14 (line 56) and §5.5: *"pointers … and never record text"*, *"the same pop-up"* in both modes. Q2
  from the diff dialog is bound to the dialog's own two records [R F5].
- **Code:** the in-app first turn attaches `PromptBuilder.recordContext(...)` built from the **live selection**, which
  is `env.selection()`, read when the message is prepared (`assistant/AssistantAdapter.java:146-153`;
  `session/node/AssistantLoop.java:146`, `includeRecordContext = history.isEmpty()`). `recordContext` puts in each
  record's `rawText()` (`llm/PromptBuilder.java:91-99`).
- **Failure:** the diff dialog's Q2 names records #12 and #40, but the person has since selected #7. The provider
  receives #7's raw text under a question about #12 and #40. That is a wrong result, and record text reaches the model
  through the analyser's own question. The binding protects only the pointers, not the content sent beside them. The
  same thing happens for S5/S6 and free text sent from the start page while a selection exists.
- **Fix:** a bound draft carries its own pointer set. For a bound draft, `PrepareAssistantContextEffect` attaches no
  record context, or attaches only the bound records' context, stated as such. With in-process actions off
  (`assistantActionsInProcess`, `config/AppConfig.java:91`) the templates' *"Read it with analyser_read"* cannot be
  followed, so either disable the catalogue items or say in the pop-up that record text will be included. Add a starred
  test: compose Q2 in the dialog, change the selection, Send in-app, and the captured request holds no `rawText` of the
  new selection (witness: the current `env.selection()` path).

### F5 — should — `assistant.startPrompts` would be silently deleted by every existing release, and has no share category

- **Spec:** §5.8 source 2 (lines 805-810) and OD-74.20: a portable-context key, *"gated like the runbook pointer"*, that
  *"travels in a project share … labelled"*.
- **Code:** `KnownKeys.family` is the key up to its first dot (`config/KnownKeys.java:66-70`), so the key's family is
  `assistant`. That family is **owned** (`config/KnownKeys.java:36`). M38.7's preserve-unknown-keys therefore does not
  protect it: a writer that owns `assistant` rewrites the whole family. On top of that, `Category.ASSISTANT` is not in
  `ProjectProfile.PROJECT_SCOPED` (`config/ProjectProfile.java:86-97`; *"LLM and assistant settings … stay global"*).
  The family also holds the machine-tier consent keys (`assistant.claudeCode*`, `config/ConfigStore.java:117`, `:219`),
  and `assistant.exchangeDir` already shows the strain: it is family `assistant` but category `DESTINATIONS`
  (`config/SettingsShare.java:229-232`).
- **Failure:** a team commits start prompts. A teammate on 1.30.x opens the profile and edits anything, and the prompts
  vanish from the committed file with no diff anyone asked for. That is the M38.7 hazard exactly. Inside a P2 build,
  the key has no category to be written, read or previewed under.
- **Fix:** give it its own family, e.g. `startPrompt.N.*` or `startPrompts`. Older writers then preserve it as unknown,
  and it does not share a namespace with consent keys. Add a new `Category.START_PROMPTS`, labelled with its cargo
  (*"Start-page prompts — words placed in a person's draft, never sent by themselves"*), in `PROJECT_SCOPED`, and
  probably **off by default** in a share, like RUNBOOKS, because it is words, not a location. Make the import preview
  list each label and text, not just a count. Specify the properties layout of the inline form. Add tests: an old-writer
  round trip with `KnownKeys.PROFILE_FAMILIES` as shipped preserves the key (witness: family `assistant`), and the
  category is absent from `BundleProfile.CATEGORIES` (`bundle/BundleProfile.java:81-82`).

### F6 — should — The project's list is almost never seen: the start page does not appear while a project is open

- **Spec:** §5.8 lines 802 and 807-808: configurable *"to present one journey"*. The list *"applies only while that
  project is open; the start page with no project shows the built-in list"*.
- **Code:** the start card is shown only when there is no log, graph, project **and** design (`MF:1356`). With a
  project open, the person sees it only through *Help ▸ Start page* (`MF:1379-1384`, `MF:2639`). M71 made opening a
  recent project go to the project workspace even when it has no log
  (`docs/specs/completed/spec-workspace-start.md:19`).
- **Failure:** the team preset is hidden behind a Help item. A teammate who opens the project never sees *"Run our
  incident journey"*. The enterprise case gets nothing in P2, because the managed source waits for P4.
- **Fix:** show the resolved list where a project lands when it has no log: the empty state of the project workspace,
  or a Project-panel section. Also state who shows it once a log is open. Alternatively, accept and record that
  project lists are reachable only through Help ▸ Start page. Add a test that the project's list renders on that landing
  surface.

### F7 — should — The start page offers fallbacks as menu paths, but the menu bar is hidden there

- **Spec:** S2's no-AI column *"Project ▸ New project…"*, S6's *"Help ▸ User guide"*, and *"Settings ▸ Assistant"*
  (lines 788, 793-798).
- **Code:** `showWorkspace(true)` hides the menu bar (`MF:1364`). The start page reaches flows only through its own
  `Actions` interface (`ui/StartPanel.java:29-84`).
- **Failure:** a fallback that says *"use Help ▸ User guide"* points at a menu that is not on screen. Read literally,
  that is a dead end.
- **Fix:** each fallback is a **button** that runs the existing action (`Actions.newProject`, `openSettings`,
  `openMcpSetup`, plus a new `openUserGuide`), with the menu path given only as the button's tooltip. Pin it in
  `StartPanelNamesTest`.

### F8 — should — "AI configured" does not cover a missing CLI or a fresh launch, so the routing usually takes the wrong column

- **Spec:** line 784: *"the Claude Code mode with Allow … on, or the in-app mode with a provider that can answer"*.
- **Code:** the mode is not persisted. It starts as `IN_APP` on every launch (`session/AssistantMode.java:9-14`;
  `ui/AssistantPanel.java:68-69`), and no config field records it (`config/AppConfig.java:126-140`). Whether `claude`
  is installed is known only from the resolver (`assistant/localagent/ClaudeResolver.java`), and the definition does
  not use it.
- **Failure:** (a) a Claude Code user with no API key relaunches. The start page sees `IN_APP` with no provider and
  takes the no-AI column, every time, until they visit the tab. (b) With *Allow* on but no `claude` installed, S5 arms a
  Start that fails *"claude not found"*. The review brief's *"no Claude Code installed"* case is not specified.
- **Fix:** define it as: Claude Code mode, switch on, **and the resolver found the executable**. That is a file check
  only; running `claude` here would be outside OD-74.1. Decide whether the chosen mode is remembered on this machine
  (machine tier, like the switches). If it is not, decide whether the start page routes to Claude Code when it is the
  only assistant that can answer, without changing the mode. Add a test row per combination: mode × switch × resolver ×
  key.

### F9 — should — One text box carries both the words and the flow, and the spec does not say what Send does when they disagree

- **Spec:** line 778: choosing a number *appends* that entry's text. Line 845: `StartPagePromptSent{request, entryId,
  draftRef}`. §8.2 adds `consent, inAppCanAnswer`, so the two places disagree on the fields.
- **Failure:** the person chooses S5, then S2 (both texts appended), edits the text, and presses Send. Which `entryId`
  is sent: the last, both, or none? If they delete S5's text and type their own question, does Send still run *Open
  log…*? There is a further problem: S3's and S5's texts contain `{bundle}`, `{log}` and `{gen}`, which cannot be
  rendered until after the flow has run, so "fills the box with that entry's text" has nothing well defined to show.
- **Fix:** keep the *chosen entry* separate from the text: a selected-row state, or a removable chip above the box.
  Choosing a flow entry (S1, S2, S3, S5) selects it and shows a one-line description of what Send will do (*"Send:
  choose an audit log, then draft a question about it"*). The words are composed after the flow, through the node.
  Prompt-only entries append their text as now. State that free text with a flow entry selected is appended to the
  flow's draft. Unify the fact's fields between §5.8 and §8.2.

### F10 — should — S4 reaches the network on *choose*, and puts remote-authored names into a draft

- **Spec:** S4 (line 796): `{template names}` *"come from the template catalogue's **declarations** (shipped data,
  pointers)"*.
- **Code:** the catalogue is fetched from `https://fluxtion-playground.dev` (`template/TemplateClient.java:22`,
  `:86-90`), and the origin can be overridden by a system property, including loopback `http`
  (`template/TemplateClient.java:76-98`). The dialog says *"The playground catalogue…"*
  (`ui/TemplateProjectDialog.java:102`). It is not shipped data.
- **Failure:** filling the box with S4's text means fetching the catalogue at choose time, which is a network contact
  before Send. That contradicts line 778 and the P2 test *"choosing a number fills the box only"*. The names are text
  from a remote (or overridden) origin, and §5.5's pointer table has no row for them.
- **Fix:** render S4's draft only on Send, after the existing fetch, inside `RunStartPageFlowEffect`. Add a *template
  name* pointer kind to §5.5 (identifier text, at most 80 characters, at most 20 names). With the catalogue
  unreachable, the draft says *"(the template list could not be read)"*. Add a test: choosing S4 makes no request (a
  recording `Fetcher`).

### F11 — should — §5.5's pointer table does not cover the start page's own placeholders

- **Spec:** S3's `{bundle}` and `{verdict}`, S6's `{docsUrl}`, configured prompts' `{project}` (line 821), the journey
  draft's `'<title>'` (line 590), and S4's template names (F10).
- **Failure:** none of these has a sanitiser row. The walk title in a journey handoff is the **sender's** words when the
  walk came in a bundle: attacker-influenced text interpolated into the analyser's own draft, which is what §5.5
  forbids for note text. `{log}`/`{gen}` in a configured prompt while no log is open (the usual start-page state) are
  not specified either.
- **Fix:** add rows: bundle name = file name as identifier text; verdict = enum; project = project folder name as
  identifier text; docsUrl = a compile-time constant; walk title = **replaced by the walk's position or id**, never its
  words, or put through the identifier sanitiser. A configured prompt that uses `{log}` while no log is open is shown
  but disabled, with *"open a log first"*. Move the JOURNEY, HANDOFF and SETUP drafts into `AskCatalogue.properties` so
  the static rule in F12 covers them.

### F12 — should — The "one registry" misses existing explain call sites, and its preconditions read state that is not in the snapshot

- **Spec:** §5.5 (lines 538-545): the records menu, the diff dialog, topology, chart and time-slider menus and the start
  page build their items from the registry, with *"a precondition over the published snapshot"*. The §10 test lists the
  same surfaces.
- **Code:** today there are three Explain entrances, all calling `explainSelection()` with a literal draft at `MF:5088`:
  the records menu (`MF:1013-1016`, built into both the Audit log menu and the right-click popup), the toolbar
  (`MF:4566`), and **the detail panel's "Explain with LLM" button** (`ui/DetailPanel.java:109-111`, wired at `MF:347`).
  The detail button's tooltip says *"Send the selected record(s) to the LLM assistant"* (`ui/DetailPanel.java:110`),
  which is now false. The askAboutEvidence handoff composes its own draft (`MF:4657-4660`). The selection, flags and
  findings are frame fields (`MF:182`, `flaggedRows`/`findings`), not snapshot state (`session/SessionSnapshot.java`
  has none). The time slider has no popup today (`ui/TimeRangeSlider.java` has no `JPopupMenu`), so Q6's surface is
  new, not rebuilt.
- **Failure:** the detail-panel button and the toolbar escape the registry test and keep their own wording. A precondition
  that is said to read the snapshot either reads frame fields (the spec does not say so) or needs facts that do not
  exist.
- **Fix:** list the detail button, the toolbar and the menu-bar copy among the surfaces. Make the source-text rule scan
  every `ui/` file for `primeDraft(` and `"Explain` literals. Say that preconditions are evaluated over a small
  `AskContext` value (selection indexes, flag set, current record, generation) that the frame assembles; that is
  rendering input, not a decision. Alternatively, make the selection a fact if rule 9 is meant to cover it. Define what
  the toolbar Explain does with **no** selection.

### F13 — should — S1 conflicts with the tour as shipped, existing users, the project switch, and the in-app demo mode

- **Spec:** S1 (line 793) and *The demo's conversation* (line 838).
- **Code:**
  - The tour is saved into the sample profile only if it is absent (`MF:1445`, `WalkBin.find(...) == null`), so a
    returning user keeps the 4-step walk with no conversation forever.
  - Opening the sample project is an `EXPLICIT_SWITCH` (`MF:1429`), which §5.3 holds behind Move/Keep/Stop while a
    Claude Code process runs.
  - While a walk with a conversation is playing, the in-app composer is hidden and *Copy prompt* is disabled
    (`ui/AssistantPanel.java:324-335`), yet S1's no-AI last step *"offers Copy prompt"*.
  - *"No walk with a conversation ships today"* is half true: the journey *Find the first recorded breach* (walk "DEMO
    first breach", scripted) has shipped since 1.29.0 as a site bundle, pinned by `JourneyCatalogueTest`
    (`docs/site/journeys/find-the-first-recorded-breach.md`). It is just not in the jar.
- **Failure:** returning users get the old tour. With a session running, S1 raises a Move dialog first. The last step's
  fallback is a disabled button. A second demo conversation gets authored next to one that already exists and is
  tested.
- **Fix:** the S1 walk has its own name, or a version that `finishSampleWalk` upgrades. Say that the demo's switch
  follows §5.3 (or is exempt, as a sample, with the reason). The last step **ends the walk** (as *Ask about this
  evidence* does) before it offers anything live. Reuse "DEMO first breach" as S1's walk, shipped in the jar, or say
  why not. Keep M71's tour acceptance (`tools/test_start_workspace.py`) in step.

### F14 — should — The fallback reuses *Copy prompt*, which copies raw record text and the REST token

- **Spec:** line 788: *"Copy prompt (the in-app tab's existing composer …)"*; S5's no-AI column.
- **Code:** `copyAssistantPrompt` (`MF:4855-4869`) prepends `PromptBuilder.recordContext(selectedRecords, …)`, which
  holds the raw record text plus the glossary. When REST is on it also appends `restActionManifest(restUrl, restToken,
  …)`, which holds **the token** in clear (`llm/PromptBuilder.java:313-316`).
- **Failure:** from the start page, which can be raised over an open log with a selection, the person copies "the same
  draft" and pastes it into a web chat. The record text and the loopback token go with it. This is the person's act
  and nothing is "sent", but the start page's promise of pointers only is not what lands on the clipboard, and the
  dialog title *"NOT sent anywhere"* does not mention the token.
- **Fix:** the start page's *Copy prompt* copies the catalogue draft only, plus at most the manifest **without** the
  token, or with it named in the dialog (*"includes your local REST token"*). Alternatively, say plainly that it is the
  existing full copy. Add a test pinning the start-page copy contents.

### F15 — should — Configured labels and texts can be rendered as Swing HTML

- **Spec:** OD-74.20 and §5.8: *"Entries are data, never instructions"*. The labels come from a project profile, which
  can arrive by git clone or a share.
- **Code:** the start page's cards are `JButton`s with `setToolTipText(why)` and accessible names
  (`ui/StartPanel.java:762-764`). Swing renders any button text or tooltip that begins with `<html>` as HTML, and
  `<img src=http://…>` in it is **fetched**. Nothing in `src/main` sets `html.disable`.
- **Failure:** a cloned project's label `<html><img src='http://x/b'>` sends a request on hover. That is a network
  contact from configuration, before any Send. The same applies to chips and tooltips in §5.9 when a chart name or node
  id came from a bundle.
- **Fix:** every component that shows configured, log-derived or model-derived text sets
  `putClientProperty("html.disable", Boolean.TRUE)`, or the label is prefixed so it can never start with `<html>`. Pin
  it with a source rule over the new start-page and transcript components, and add a frame test: a label beginning
  with `<html>` renders as its characters (witness: a plain `JButton`).

### F16 — should — §5.9 does not say what happens to control characters, ANSI escapes and bidi text in Claude's words, or how links are shown

- **Spec:** §5.9 safe Markdown (line 873), links (line 881), copy.
- **Failure:** model text can contain ESC `[31m` sequences, C0/C1 controls, zero-width characters and U+202E. The spec
  escapes these for tool input (§5.7) but not for answers. A U+202E or hidden characters can make a shown command
  differ from what *Copy* puts on the clipboard (paste-jacking into a terminal). A link's confirmation that shows the
  "full URL" can still mislead with bidi text or a homograph host.
- **Fix:**
  - ANSI sequences are never interpreted: shown as `␛[31m`, or stripped and counted in the diagnostics line.
  - C0/C1 controls other than `\n`/`\t`, and bidi overrides, are shown as visible escapes in body text and code blocks,
    so what is seen is what is copied.
  - The link dialog shows the scheme and **host** on their own line, the host IDN-decoded to punycode, with bidi
    escaped.
  - State whether bare URLs autolink.
  - Add a starred test with an answer holding ESC, U+202E and U+200B: the document shows visible marks, and Copy
    equals the source (witness: plain `insertString`).

### F17 — should — Strict validation of unknown keys conflicts with ignore-never-reject

- **Spec:** line 831: *"an unknown key"* refuses the entry.
- **Failure:** a newer analyser adds an optional entry field (an icon, a description), and every older build then
  refuses those entries and hides them. M38.7 and the format spec ask readers to ignore what they do not know.
- **Fix:** refuse an unknown entry **kind** (no `builtin`, `text` or `walk`) and over-cap fields. Ignore unknown
  optional keys, and note them in the muted line. Bound that line (*"and 9 more"*), because a 64 KB pointer file can
  hold hundreds of bad entries.

### F18 — should — D-C2 is changed and not recorded

- **Spec:** §7's D-C2 row (line 968): *"no change to the claim"*. ONBOARDING's D-C2 says *"The analyser stores no
  instructions"*.
- **Failure:** inline `text` entries are prose prompts stored in the profile, which the person then sends to a model.
  That is "storing instructions", even though nothing is run. In the full posture a 500-character text can ask Claude to
  run something, under a harmless 60-character label.
- **Fix:** record it in §7: *"the profile may store start-page prompt text; it is placed in a draft and shown in full
  before Send; nothing runs it"*. Require the box to show the whole text without hidden overflow, and show the
  origin label. Alternatively, allow only the pointer form and keep D-C2 literally true.

### F19 — should — The page's question, layout and keyboard are not specified, and four entries duplicate existing cards

- **Spec:** §5.8 says *"at the top of the start page"* and *"numbered entries"*, with nothing on layout, keyboard or
  theme.
- **Code:** the hero already asks *"What would you like to work on?"* (`ui/StartPanel.java:402`). Under it, the cards
  already offer the tour, the evidence bundle, the template and the audit log (`ui/StartPanel.java:132-157`). M71
  removed the incident chooser precisely because it was redundant (`spec-workspace-start.md:26`), and its acceptance
  says every action reaches *"a distinct … flow"* (line 47).
- **Failure:** two near-identical questions stacked on one page. S1/S3/S4/S5 next to the cards *Take a guided tour*,
  *Open evidence bundle*, *Create from template* and *Open audit log*. A configured `replace` that drops S1 still leaves
  the tour card, so "present one journey" cannot be achieved. Keyboard: typing `1` in a focused text box types a digit,
  and entries 10 to 12 need two keys.
- **Fix:**
  - Replace the hero's question with §5.8's prompt, and say whether the cards stay, as *"or choose directly"*.
  - Make the list a `JList` or buttons with accessible names (*"Prompt 3 of 6: Open an evidence bundle someone sent
    me — from this project"*). Up/Down and Enter choose. Alt+1…9 as mnemonics, none past 9.
  - The Send button's label follows the chosen entry (*Send*, *Open…*, *Play*). *"Send"* on S3 opens a file chooser.
  - First run versus returning user: say whether recent projects outrank the list for a returning user.
  - Colours through `UiTheme` and the page's `recolour` list. Add start-page shots in both themes to the `capture-docs.py`
    acceptance and `StartWorkspaceFrameTest`.

### F20 — nit — The node is missing parents, and the project source's lifetime is not specified

- `StartPagePrompt` lists `assistantMode, localAgentSession` (lines 845 and 1106). S5 also needs `openLog` (whether a
  log is open, to bind the generation), and the project source needs `activeProject`. A `StartPromptsObserved{PROJECT}`
  from a previous project must be refused by identity, and a project close must retract it. Say so.
- A configured `walk` entry naming a demo-set walk (line 826) means a project switch to the sample project. Say so, or
  restrict `walk` to the open project's walks.
- *"The current locale"* (line 820): the JVM default? The app has no localisation today (no `ResourceBundle` in `src/main`).
- With warm start on, a direct tab click can start the process while Start is armed. Say whether the armed draft is
  then placed (§5.2 item 6).
- The chart popup already has *"Add explanation…"* (`ui/ChartPanel.java:1138-1139`), the person's own words. Q4's
  *"Explain this point"* beside it reads as the same thing. Label it *"Ask Claude: explain this point"*.
- Node-id chips (line 884) on ordinary words ≥3 characters (`map`, `risk`) will chip prose. Consider requiring code
  formatting or a `node` prefix, and bound the matching cost.

### F21 — nit — Test gaps against rule 8

- None of F1-F4, F5's old-writer round trip, F10's no-fetch-on-choose, F14's copy contents or F15's `html.disable`
  has a row in §10. Each needs a starred row with the witness given above.
- `OneDispatchModelTest` P2 rules (last P2 row) are unstarred and have no witness. Give the start-page rule one: a
  `StartPanel` method that branches on `AssistantMode` fails it.
- The managed-source rows test P4 behaviour with no P2 producer. That is fine as a pure `AskCatalogue.resolve` test;
  say that it is.
- The visual acceptance covers the transcript, not the start page (F19).
- The Markdown lexers need a bounded-time test with pathological input (unclosed emphasis 64 KB long), since parsing
  runs again on every streamed revision.

---

## Checked and found sound

- **D-AI4 / OD-74.1 on the start page:** Send never starts a process, and the start page is not on the AI menu. The
  programmatic reveal never fires warm start, because `WarmStart.isDirectSelection` requires a mouse or key event whose
  source is the tab strip (`ui/WarmStart.java:20-24`), so the spec's "programmatically, so warm start never fires" holds.
- **Choosing a number posts no fact**, and nothing is sent before Send. This holds apart from F10 (S4's fetch) and F15
  (HTML tooltips).
- **Bundles never carry the start prompts** as long as the key stays out of GRAPHS/REPORTS/VIEW:
  `BundleProfile.CATEGORIES` is exactly those three (`bundle/BundleProfile.java:81-82`). The §10 witness is right.
- **Machine-tier consent:** the switches, warm start and folder trust are separate config fields
  (`config/AppConfig.java:126-140`) and are not written by `SettingsShare`'s ASSISTANT category
  (`config/SettingsShare.java:237-241`).
- **The S2 onboarding route** goes through §5.6's person paths. `propose` stays hosted-only, and *Create a project* is
  the analyser's own row. That is consistent with M73's person path (`OpenProjectRequested{CREATE}`).
- **Labels for recorded and simulated conversations** exist and match the spec (`walk/WalkSpec.java:140-142`), and a
  walk's conversation kinds are validated (`walk/WalkConversation.java:29`, `:58-59`).
- **The transcript base** is safe today and the spec keeps it so: both panels draw with `StyledDocument.insertString`
  and no HTML kit (`ui/LocalAgentPanel.java:270-278`, `ui/AssistantPanel.java:395-422`). §5.9's "never an HTML editor
  kit", its witness, and the image-as-text and link-confirmation rules are right. Only http and https open.
- **Chips are reveal-only (D-L3)** and muted across generations. That is the right rule and has a good witness.
- **The diff dialog binding** (R F5) is correctly identified: `RecordDiffDialog.show` gets only the records and labels
  today (`ui/RecordDiffDialog.java:26`), so a `DiffContext` is needed. (F4 is about the in-app send that comes after.)
- **`primeDraft`'s blank-only rule** is correctly named as the witness for "append, never drop"
  (`ui/AssistantPanel.java:258-260`, `ui/LocalAgentPanel.java:153-155`).
- **The precedence model** (built-in, then project, then managed; replace/prepend/append; `lock` only from managed) is
  coherent. The caps (12 entries, 60-character labels, 500-character texts, 64 KB pointer file) are reasonable, and the
  "invalid source falls back to the earlier result" rule is right.
- **The no-AI demo** plays through the existing walk path (`MF:1455`, `playWalk(DemoTour.NAME, 0, "start-page")`) with
  no provider. The zero-process, zero-network test is the right check.

## Summary

There are four blockers.

- **F1:** with nothing open, the start page hides the Assistant tab and the menus, so "hand the draft to the tab and arm
  Start" goes nowhere.
- **F2:** S1 (the sample project under `~/.fluxtion-analyser`) and S3 (a bundle working copy) end in a Start that
  `LocalAgentFolders` and OD-74.5 refuse.
- **F3:** S3 offers *Yes* on a bundle that did not verify, which today is refused.
- **F4:** in the in-app mode a pointer-only question still sends the *live selection's* raw record text, which can be
  other records than the draft names.

The main should-fixes:

- **F5:** `assistant.startPrompts` sits in the owned `assistant` key family, so every current release deletes it on
  save, and it has no share category.
- **F6:** a project's list is visible only through Help ▸ Start page.
- **F7:** fallbacks are given as menu paths on a page with the menu bar hidden.
- **F8:** the mode is not persisted, and an installed CLI is not part of "AI configured".
- **F9:** the box mixes the chosen flow with the free text.
- **F10:** S4 fetches the catalogue on choose.
- **F11:** placeholders have no sanitiser rows.
- **F12:** the registry misses the detail-panel and toolbar Explain entrances.
- **F13:** S1 conflicts with the shipped tour and the existing conversation journey.
- **F14:** *Copy prompt* copies record text and the REST token.
- **F15:** Swing HTML in configured labels.
- **F16:** control characters, ANSI and bidi text in the transcript.
- **F17:** strict refusal of unknown keys.
- **F18:** D-C2 is changed but not recorded.
- **F19:** layout, keyboard and duplicated cards are not specified.

F20 and F21 are nits.

Review file: `docs/handoff/review_m74_r7_start_prompt_c7f2.md` (uncommitted).
