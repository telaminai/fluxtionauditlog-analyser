# M74 usability review: start page, unified assistant, profile cards, approvals, Settings

**Reviewer:** an independent UX and visual-design review, done before the owner's hands-on test.
**Code reviewed:** worktree `analyser-p3`, branch `feature/claude-code-p3`. HEAD was 91f65168 when the review began. It is now 1cb247ea, which changed docs only. Nothing in the worktree was edited.
**Evidence used:**
- the existing screenshot sets (`m74-ux-visual`, `m74-p3-visual`, `m74-p2-visual`, `m74-p2b-visual`, `m74-p1b-visual`);
- **fresh captures at HEAD** in `scratchpad/uxr/shots/`. They come from a copy of the worktree, painted offscreen with FlatLaf under a JUnit temp-dir `user.home`. They cover the start page, both assistant modes, all four profile cards, Settings ▸ Assistant, and three approval dialogs (`walk {delete}`, `walk {play}`, `report`).
- target sizes and accessible names, dumped from the live component tree (`scratchpad/uxr/run.log`, lines `TARGET` / `LISTROW`);
- contrast computed from the tokens in `UiTheme`, `TranscriptStyle` and FlatLaf 3.5.1's own `UIManager` values (`scratchpad/uxr/contrast.py`).

No live `claude` was run. Every process started for the captures has exited.

Severity: **Critical**: blocks a task or misleads on safety. **Major**: real friction or misunderstanding for a persona. **Minor**: noticeable, has a workaround. **Cosmetic**: polish. Anything marked **OPINION** is a taste call, not evidence.

---

## Executive summary: the top 10 fixes, ranked by user impact

1. **The default card's safety promise is cut off mid-sentence, with no ellipsis.** At HEAD the *Safe analysis* card reads "…writes a file only after you approve each call. No" and stops (fresh `profiles-safe-light.png`, card at x≈22–356, y≈276–352). "shell — safe with logs you did not write" never shows. The cause is a bug in `ProfileOverlay.wrap()`: when the line limit is reached it `break`s and drops the rest of the text. The ellipsis branch only fires if the last kept line is too wide, which by construction it never is. The *Off* and *Full* lines are one word from the same fate. **Major.** Fix: append "…" whenever words remain, and shorten the card line to fit two lines ("Reads your evidence. Asks before it changes or writes anything. No shell.").
2. **"Muted" text is FlatLaf's *disabled* grey (#8C8C8C), and it fails contrast almost everywhere** (2.5–3.3:1 in light, 3.1–4.1:1 in dark). This one token carries the start page's lede and guidance, every assistant-header note, the limits line, the card descriptions, Settings' explanations and the proposal reasons. The longest safety explanations in the product are therefore the hardest to read, and they look disabled. **Major.** Fix: one token change in `UiTheme.mutedForeground()` to #57606A light / #A9B1BA dark (≥4.8:1 on every surface measured; see the contrast table).
3. **The first question takes 5 clicks, 2 decisions, an overlay and a dialog, with three "send" presses.** The path is: Send → cards → Apply → press the button again ("Choose how the assistant works first — then ask again") → *Start — ask your question* → folder confirmation → it sends. **Major** for the newcomer. Fix: after Apply, carry the pending question through automatically (the person already pressed Send once), and let the folder question be the only thing between Apply and the answer.
4. **"posture" leaks into user-facing text in at least eight places, and the app already uses it for something else.** Examples: the header chip "Safe analysis (default posture)"; transcript marks "denied by the posture" and "denied: not asked in this posture"; the transcript note "denied by the analyser assistant's posture"; "Settings changed the posture…"; "the posture check failed"; "POSTURE_CHANGED: …"; Settings "(and in the full posture runs hooks)". The canvas also has a research/authoring "posture" (`open {posture}`, AI ▸ Posture). **Major.** Fix: say "profile", or name it ("Safe analysis", "Full Claude Code"); never print a verdict code.
5. **The screen reader is told the wrong decision maker.** `TranscriptStyle.Mark.DENIED.spoken` is "denied by the posture". Every denied line uses it, including **"denied by you"** and "denied: not answered within 120 s". The live tree shows `analyser_open — denied by the posture, collapsed…` beside visible text "denied by you". **Major** (WCAG 1.3.1 / 4.1.2: what is spoken disagrees with what is shown). Fix: speak the visible words.
6. **The approval dialog says what the *verb* can do, not what *this call* does.** For `walk {"delete": "DEMO first breach"}`, the headline is "walk saves, replaces, renames, plays or deletes a saved walk, or records one as a page…" (fresh `perm-walk-delete-light.png`). The person has to parse JSON to learn this call **deletes**. The same text appears for `walk {play}`, which changes nothing (`perm-walk-play-dark.png`). **Major.** Fix: derive a one-line sentence from the input's sub-action ("Delete the saved walk 'DEMO first breach' (restorable from Reports ▸ More)") and keep the verbatim input under it. Showing a derived sentence must never replace showing the raw input.
7. **Safe-dialog button order and defaults change from dialog to dialog.** The approval dialog puts the safe default (Deny) far right, with Allow once directly to its left. The full-folder trust dialog puts "Trust for full Claude Code" far right, where Deny was. The full warning, the project switch and the example confirmation put Cancel left. The proposal review puts Close right. The Safe folder confirmation is a stock Yes/No with **Yes** as the default. Someone who has learned "the right-hand button is safe" from ten approval dialogs will press Trust. **Major.** Fix: one rule everywhere. Recommended: the safe choice always sits in the same place and is the default, and the risky choice is never adjacent to it.
8. **There are two controls for one decision in the assistant header.** The mode combo ("Claude Code · your plan" / "In-app · API key") and the "Profile: …" button both choose the assistant, and the profile includes the mode. The combo's "your plan" is also a fixed string, even when Claude Code is on API-key billing. **Major.** Fix: one control, "Assistant: Safe analysis ▾", that opens the cards. Drop the combo.
9. **The hero action is the weakest-looking control on the start page.** The Send button is 72×23, grey, disabled until you type, and sits 6 px off the box's left edge (x=50 vs 44). The 13 strip chips below it are 34 px tall, accented and outlined. The prompt box in dark mode is a #46494B slab inside a blue band (1.52:1 against the band), so it reads as a disabled field. **Major** (visual hierarchy). Fix: make the button the default/accent button, at least 32 px tall and aligned to the box. Give the box a surface fill and an accent focus border. Show a placeholder ("Ask about a log, or choose an example above").
10. **Approval friction in Safe analysis is higher than the headline suggests, and some approvals are futile.** A normal investigation (ask about a record, chart a series, write a report, save a walk) costs at least 2 approvals, realistically 3–5 (see the friction table). Two problems make it worse. First, `report` or `screenshot` with a file path is *approved and then refused*, because file exchange is off by default (FAQ: "write only inside the exchange directory when file exchange is on"). Second, a second request arriving while a dialog is open is denied at once. **Major.** Fix:
    - pre-check: never ask for a call the settings will refuse; deny it with the reason and an *Open Settings* action instead;
    - queue a second request ("1 more waiting") rather than auto-denying it;
    - see §5 for an *optional*, explicitly weaker "restorable project writes" allowance.

A runner-up just outside the top ten: an external MCP client refused while *Close external access while this session runs* is in force is told "the REST transport for external clients is off (Settings ▸ Assistant)". That is false (Settings says on), and it will send an incident responder to the wrong place (§3b, `ActionServer.EXTERNAL_OFF`).

---

## 1. Heuristic evaluation (Nielsen's 10), screen by screen

### 1.1 Start page (`uxr/shots/start-page-*.png`, `m74-ux-visual/start-page-*.png`, `m74-p2b-visual/example-entry-chosen-*.png`)

| # | Heuristic | Finding | Grade | Fix |
|---|---|---|---|---|
| S1 | Visibility of system status | Nothing on the page says which assistant is in force, or that none is. "No AI assistant can answer here yet" only appears after an entry is pressed or in the fallback row. A newcomer types, presses Send and is surprised by cards. | Major | Add a one-line status under the box: "Assistant: not chosen — Choose…" / "Assistant: Safe analysis (Claude Code)". |
| S2 | Match with the real world | "Connect an assistant ▸ Claude" means *register the external bridge for your own Claude client*. The in-app "Claude Code" assistant is a different thing that is never offered on this page. Two "Claude"s, one unexplained. | Major | Rename the block to "Use an external AI client" and its button to "Claude (desktop or CLI)". Add "Choose the in-app assistant…" beside the box. |
| S3 | Consistency | In the start box **Enter inserts a newline** and there is no keyboard submit. In the assistant composer Enter sends and Shift+Enter is a newline (`AssistantSurface` l.77–78). | Major | Same binding in both: Enter sends, Shift+Enter is a newline. |
| S4 | Error prevention | Clicking a prompt entry (built-in 6 or any pack entry) **appends** its text every time, so a double click or a second click duplicates the prompt (`StartPromptView.choose`). | Minor | Don't append text the box already ends with. Or make prompt entries replace the box until the person has typed. |
| S5 | Aesthetic, minimalist | Two band lines repeat what the strip already says ("Open evidence, return to a project, or learn with DEMO. An assistant can drive the same views through the local bridge."). "Local bridge" is jargon to a newcomer. | Minor | Keep only the drop hint. Delete the first sentence. |
| S6 | Consistency / wording | After choosing an example entry, the fallback row reads "No AI assistant can answer here yet — these run without one:" followed by **nothing** (`m74-p2b-visual/example-entry-chosen-light.png`, y≈410). `renderFallbacks()` adds the label even when `shown` is empty. | Minor | Hide the label when no fallback is shown. |
| S7 | Recognition over recall | The Alt+1…9 shortcuts are only documented in the user guide. On macOS "Alt" is ⌥, and the tooltip says "(Alt+R)". FlatLaf on macOS binds mnemonics to Ctrl+⌥, so **verify that Alt+R works on the Mac**. ⌥+digit also types ¡™£… when the box has focus. Verify that choosing an entry does not also insert that character. | Minor (verify) | Show "⌥1" on macOS. Test in the hands-on session. |
| S8 | Help and documentation | Strip tooltips carry the old cards' sentences. That is good for hover, but invisible to keyboard and touch-pad users. The *Settings* block's caption ("Source settings apply to the open project…") describes a button labelled only "Settings". | Minor | Rename the button "Project & source settings". |
| S9 | User control | *← Return to main options* is good, with a clear way back. Praise earned. | — | — |
| S10 | Error recovery | A broken pack is refused with one cause, its line and *See the format*, and the built-in list stays. Praise earned. The message is red text plus words, so colour is not the only cue. | — | — |

### 1.2 Unified assistant panel (`uxr/shots/assistant-*.png`, `m74-p3-visual/header-full-posture-*.png`, `status-waiting-*.png`)

| # | Heuristic | Finding | Grade | Fix |
|---|---|---|---|---|
| A1 | Minimalist design | About 210 px of chrome sit above the transcript in a 950 px window: two rows of six chips, Folder, a posture note, a set-up button, status, and a three-line limits paragraph. Two lines repeat each other: "Safe analysis — your skills and hooks are not loaded; choose Full Claude Code (Profile…) for those" and "Safe analysis reads your evidence; … — choose Full Claude Code (Profile…) for those." | Major | Collapse to one chip row plus one status line. Put the limits behind an ⓘ on the profile chip. |
| A2 | Match / safety framing | Both repeated lines *upsell Full Claude Code* on every view. To an admin, a persistent nudge toward the riskier profile reads as pressure. | Minor (OPINION on tone) | State what Safe does. Mention Full once, in the cards. |
| A3 | Consistency | Two choosers: the mode combo and the "Profile:" button (see top 10, #8). | Major | One control. |
| A4 | Visibility of status | The Start button's label becomes "**Running…**" (disabled) while the status line says "**Ready**". A button is being used as a status display, and the two disagree in tone. | Minor | Keep Start's label fixed ("Start"), disable it, and let the status line carry the state. |
| A5 | Wording | "Copy prompt is for an external assistant." appears in the Safe and Full limits lines even when no Copy prompt button is visible (Claude Code mode). | Minor | Say it only where the button is. |
| A6 | Developer info at top level | Chips for the CLI version, the raw model id (`claude-haiku-4-5-20251001` in `status-waiting-Dark.png`) and "model: haiku (confirmed after the first turn)" are diagnostics. | Minor | Move them into a "Session details" tooltip or disclosure. |
| A7 | Visibility (Full cue) | In a narrow docked tab the Full badge ellipsises to "…your tools and permissions — ru…" (`header-full-posture-Light.png`, y≈468), losing "running"/"on". | Minor | Put the state first: "◆ Full Claude Code (default) — running". |
| A8 | Help users recognise errors | Verdict codes reach the user: "POSTURE_CHANGED: …" and "BUDGET_SPENT: …" (`LocalAgentSession` l.1047–1052). | Minor (verify where rendered) | Strip the code prefix in the view. |
| A9 | Consistency (in-app) | The in-app transcript shows a raw placeholder: "[an action was requested — its result follows]" (`assistant-in-app-dark.png`, y≈232). | Cosmetic | Render it as a tool line, as Claude Code's are. |
| A10 | Trust | "the model's words, not a verified result" on every answer, and tool lines with words plus colour. Praise earned. | — | — |

### 1.3 Profile cards (`uxr/shots/profiles-*.png`)

| # | Heuristic | Finding | Grade | Fix |
|---|---|---|---|---|
| P1 | Visibility | Safe card text truncated (top 10, #1). | Major | — |
| P2 | Consistency | The Safe card line says it "changes what is open or writes a file only after you approve each call". The Safe *details* (three bullets) never mention approvals. The details also say "Read, Grep and Glob". Those are tool names, not user words. | Minor | Bullet 1: "Reads files in the project folder and uses the analyser's views." Bullet 2: "Asks you before it opens a log, saves a report or walk, or writes a file." |
| P3 | Aesthetic, minimalist | The Full card embeds the whole ~180-word warning in a 7-row scroll box. Pressing *Turn on full Claude Code* then shows **the same warning again** as a modal. That is two reads of one text. | Minor | Show three short bullets inline. Keep the full text for the modal only. |
| P4 | User control | Escape closes and Close is present. The header says "Profile: Not chosen…" until Apply. Clear. Praise earned. | — | — |
| P5 | Consistency | The selected card has a ✓ and a filled tint, so colour is not the only cue. Praise earned. But the cards are `JToggleButton`s, so a screen reader announces "toggle button", not "radio button 1 of 4", and the arrow keys do not move between cards. | Minor | Give them a radio role (an AccessibleRole override) and arrow-key navigation. |
| P6 | Match | "External MCP / REST access is off — Settings ▸ Assistant turns it on for an MCP client or a REST caller." The Settings checkbox is labelled "Allow the assistant to drive the UI over localhost (REST)". The vocabulary doesn't match, so the admin cannot find the switch the cards name. | Major (persona d) | Rename the checkbox "External MCP / REST access (for an AI client on this machine)". |

### 1.4 Approval dialog (`m74-p3-visual/permission-dialog-*.png`, `uxr/shots/perm-*.png`)

| # | Heuristic | Finding | Grade | Fix |
|---|---|---|---|---|
| D1 | Match | The title is "Claude Code asks to run **analyser_walk**", a tool identifier. The server line reads "server: fluxtion-analyser-hosted · named by the server: Analyser walk": implementation jargon ("hosted", "named by the server") for the analyser's own tools. | Major | For the analyser's own verbs: "Claude Code wants to **delete a saved walk**". Drop the server line, or say "the analyser's own tool". |
| D2 | Error prevention | A verb-level rationale instead of a call-level one (top 10, #6). | Major | — |
| D3 | Visibility | "Denied automatically in 120 s if not answered" ticks every second, but nothing tells a screen reader user about the deadline (the label is not announced). | Minor | Put "Denied automatically after 120 seconds" in the dialog's accessible description once. |
| D4 | Error prevention | Deny is the default, and Enter, Escape and close all deny. Allow is inactive for 500 ms, has no mnemonic and cannot become the default. The input is verbatim, with invisible characters escaped. **Praise earned: this is a well-built consent dialog.** | — | — |
| D5 | Flexibility | "Stop Claude Code" sits next to "Allow once" with equal weight. | Cosmetic | Separate it with space or move it to the left edge, as the countdown is. |

### 1.5 Full warning, folder trust, Safe folder confirmation (`m74-p1b-visual/2-*.png`, `4-*.png`; `MainFrame` l.5134)

| # | Heuristic | Finding | Grade | Fix |
|---|---|---|---|---|
| F1 | Aesthetic | The warning is one 180-word block in two paragraphs. Its key sentence ("they can read files in your home folder, including the analyser's local REST token") sits in the middle of paragraph two. | Minor | Lead with three bullets ("Runs your hooks and MCP servers when you press Start", "Can use your shell, edits and web under your rules", "Log text written to steer an AI reaches that shell"), then a "Details" disclosure. |
| F2 | Consistency | The Safe folder confirmation is a stock `YES_NO_OPTION`: buttons "Yes"/"No", default **Yes**. The Full one is "Trust for full Claude Code"/"Don't trust", default No. | Major | Verb labels on both ("Trust this folder" / "Cancel"). One default rule. |
| F3 | Match (newcomer) | For the sample project and bundle copies, the folder named is the analyser's scratch folder, a long temp path the person never chose ("Trust /var/…/analyser-claude-1307… for Claude Code?"). | Major | When the folder is the analyser's own scratch folder, say so in words first ("the analyser's private working folder for the sample project"), path second. Or skip the question for analyser-owned folders in Safe analysis (it reads only the analyser's copy). |
| F4 | Visibility | The folder dialog names the hooks and servers found, and says it will ask again when they change. Praise earned. | — | — |

### 1.6 Prompt pack, example confirmation, proposal banner and review, project switch

| # | Heuristic | Finding | Grade | Fix |
|---|---|---|---|---|
| E1 | Match | The example confirmation's lead line is bold **brick red** (`warnForeground`) for any third-party address (`example-confirmation-light.png`). Red reads as "error". In dark mode it is 3.72:1 on the dialog panel, a fail. | Minor | Use the attention amber plus the ⚠ icon the dialog already has, at ≥4.5:1. |
| E2 | Minimalist | The integrity line prints a 64-hex digest across the dialog. | Cosmetic | Show the first and last 8 characters, with the full digest in a tooltip and on copy. |
| R1 | Recognition | The proposal review shows the *kind* as a code identifier (`sourceRoot`, `processor`, `graphml`, `runbook`) and the analyser's check as enum shouting: "DIRECTORY (12 .java files)", "RESOLVES (pairing UNKNOWN)", "READABLE (fit UNKNOWN until opened)". | Major | Plain words: "Source folder: found, 12 Java files"; "Processor class: found; whether it matches the log is not known yet". |
| R2 | Flexibility | **Claude's reason appears only after you choose an answer** ("Claude's reason — unverified (choose an answer to see it)"). The reason is input *to* the decision. | Major | Show the reason when a row is selected or focused, not when it is answered. Or show it inline as a muted second line. |
| R3 | Consistency | Buttons are [Apply chosen][Close], with Close the default. The profile overlay is [Close][Apply]. | Minor | See top 10, #7. |
| B1 | Visibility | The proposal banner is a banner, not a pop-up, with "Nothing has been applied — review each one." Praise earned. | — | — |
| W1 | Clarity | The project-switch dialog's three options are well written. Its buttons are three equal-width slabs ("Cancel" default, filled), with the long label "Stop, and get Start ready in the new project". | Cosmetic | Shorter labels: "Stop and switch" / "Keep running here" / "Cancel". |

### 1.7 Settings ▸ Assistant (`uxr/shots/settings-assistant-*.png`)

| # | Heuristic | Finding | Grade | Fix |
|---|---|---|---|---|
| T1 | Match / mental model | The master switch "Allow the analyser to start Claude Code" sits in the box titled **"Safe analysis — Claude Code reads your evidence"**, yet it also gates Full. The Full box's only control is a sentence fragment, "**…with my full Claude Code permissions**", continuing a sentence from another box. | Major (persona d) | One "Claude Code" box: master switch → radio "Safe analysis / Full Claude Code" → Full's mode and warning. |
| T2 | Accuracy (trust) | The help text still says the REST "URL + token appear in the status bar (and console)". At HEAD, stdout prints only the first 8 characters (`MainFrame` l.5505), and the docs now send clients to `~/.fluxtion-analyser/rest-endpoint`. | Major (persona d: a security claim that is no longer true) | Rewrite: "The URL and token are in ~/.fluxtion-analyser/rest-endpoint (readable only by you); the console shows the first 8 characters." |
| T3 | Consistency | "Export directory" (the label), "exchange directory" (the help text and the approval dialog), "This project's directory" (an ambiguous label for the project exchange directory). | Minor | One name: "Exchange folder (machine)" / "Exchange folder (this project)". |
| T4 | Alignment | "Max action rounds:" is indented (x≈177) and "Max actions per reply:" is not (x≈158). "Claude Code model:" sits at inset 24, its sibling checkbox at inset 20. | Cosmetic | One indent per level. |
| T5 | Visibility | Children grey out while their parent is off (`settings-assistant-claude-off-*.png`). Praise earned. | — | — |
| T6 | Jargon | "compute over the index", "build curation", "In-process opens no port", "(in-process)", "the LLM page" (the profile is called *In-app assistant*). | Minor | "Provider, model and API key…" → "In-app assistant: provider and key". |

---

## 2. Visual design critique

**Hierarchy: is the hero action clear?** Only partly. The headline and the numbered list are clear. The *action* is not: the Send button is grey, disabled-looking, 23 px tall, and it is the only control in the band without accent treatment. Thirteen blue-outlined 34 px chips below it compete and win (start page, y 486–642). In dark mode the input box (#46494B) is the dimmest object in the band. The eye lands on the list, then on the strip, and never on the place to act. *Fix:* accent-filled default button, the box on `surface()` with a 2 px accent focus ring, placeholder text.

**Alignment and grid.**
- Start band: the lede and box start at x=44. The list numbers, the Send button and the fallback row start at x=50 (FlowLayout's 6 px hgap). That is two left edges 6 px apart, which reads as a mistake. (Cosmetic.)
- Strip: five equal columns (x = 26, 275, 524, 773, 1022): clean. The *Connect* and *Settings* captions wrap to four lines at 12 px under their buttons, so the row's height is set by its two smallest texts.
- Profile cards: a 2×2 grid with an 8 px gutter: clean. The details below jump to the panel's left edge (x=22) with bullets at x=28: fine.
- Settings: TitledBorder boxes with mixed insets (T4).

**Spacing rhythm.** `UiTheme` declares a scale (4/8/12/20/28, target 32), but the M74 surfaces mostly ignore it:
- `ProfileOverlay` uses 12/14/4/10/6;
- `StartPromptView` uses 6/4/8 struts;
- `LocalAgentPanel` uses 4/2 flow gaps;
- Settings uses 2/4/20/24 insets.

The result is that the assistant header (rows 2–4 px apart) feels cramped while the start page breathes. OPINION: route the M74 code through the `SPACE_*` tokens. The header especially needs `SPACE_SM` between its lines.

**Density.** The assistant header is the densest region in the product (A1). The transcript is comfortable. Settings is a wall of 12 px grey prose (four paragraphs of 3–5 lines each).

**Typography scale.** Sizes in use: 18 bold (hero), 16 bold (cards title, transcript H2), 15 bold (section headings), 14 bold (dialog titles), 13 (body), 12 (muted, chips, card lines, captions), plus monospace at 12–13. That is six steps with ratios near 1.07, so headings barely separate from body. OPINION: four steps (20 / 15 / 13 / 12) would give clearer levels. The 12 px muted text at 2.5–3.3:1 compounds the problem (see §4).

**Component consistency.**
- **Buttons:** three families. FlatLaf stock buttons (23 px), custom `ActionChip`s (34 px, accent outline) and link-buttons ("All options: Settings ▸ Assistant…", 19 px, no underline, black text, so not recognisable as a link). The strip chips and the hero button belong to different families. OPINION: the hero should be the strongest member of the chip family.
- **Chips:** header chips (wash 0.10, 1 px hairline at 1.24:1 that is effectively invisible), the start-page chosen-entry chip (wash 0.22, no border, with a separate 18 px "×"), transcript record/node chips (19 px, bordered). Three looks for one concept.
- **Cards:** profile cards (10 px radius, 2 px accent when selected) and strip chips (16 px radius) don't share a radius. `CONTROL_RADIUS = 8` exists; the cards use 10/2, the chips 2×8.
- **Dialogs:** custom (`LocalAgentPermissionDialog`, `ProposalReviewDialog`) and stock `JOptionPane` (folder trust, Safe confirm, example, project switch) are mixed, with different paddings, icon use and button orders (top 10, #7).
- **Badges:** the ⤓ download badge, the ◆/◆◆ Full badge, ✓ marks, the ★ create row. Each is fine alone; together they don't form a visible set, but they don't clash either.

**Colour use and meaning.**
- **Blue is everything.** Links, list entries, chips, section rules, the selected card, strip outlines, the transcript role labels. When everything is accent, nothing is. OPINION: keep the accent for interactive elements and selection, and use neutral ink for headings and rules.
- **Orange/amber means three things.** The Full-posture cue (#B35900 standard), a *denied* tool call (#9A6700 / #E3B341) and status notes (`noteForeground` #B05A00). A person in Full mode sees an orange border around the composer and orange "denied by you" in the transcript, and cannot tell "danger mode" from "something was refused". *Fix:* reserve the Full colour for the cue only. Make *denied* a neutral or dark-grey mark with its words; it is the person's own choice, not a warning.
- **Red vs amber.** Refused (red) vs denied (amber) is a subtle split that most people won't need. With words present it is acceptable.
- **The Full-posture cue is not colour-only.** It has a border, a ◆ or ◆◆ glyph, words naming the mode, and "Send to full Claude Code" on the button. **Praise earned.** Its standard colour is 4.32:1 on the composer panel in light mode (a text fail for 13 px bold, which is not large text). The strong colour is 4.09:1 on the dark panel.

**Consistency across surfaces.** Start page and assistant share the accent and the chip vocabulary. Settings is plain FlatLaf with TitledBorders and reads like a different product. The dialogs are stock. OPINION: the "Telamin Canvas in waiting" feel is strongest on the start page and weakest in Settings and the approval dialogs.

---

## 3. Cognitive walkthroughs

At each step the walkthrough asks three questions: *Will they know what to do? Will they see how? Will they understand the feedback?*

### (a) A newcomer who has never heard of Fluxtion, first launch

| Step | Action | Know what to do? | See how? | Understand feedback? |
|---|---|---|---|---|
| 1 | The app opens on the start page. | Yes: "What would you like to do?" and six plain options. Entry 6, "What can this tool do?", is well chosen. | Partly. The list is clickable text in blue, but "Fluxtion", "DEMO", "GraphML", ".fexp" and "local bridge" appear before any explanation. | — |
| 2 | Clicks 1 "Run me through a demo". | Yes. | The chip appears and the button changes to "Play". The verb change is good feedback. | Yes. "no AI, no network, nothing sent" is reassuring. |
| 3 | The demo plays, labelled *Simulated conversation*. | Yes. | Yes. | Yes (per spec; not re-verified here). |
| 4 | Back on the start page, types "what does this log show?" and presses Send. | Yes. | Send was disabled until they typed. There is no placeholder, so it was not obvious the white strip is a text box (dark mode especially). | **No.** Cards open in another window with "How should the assistant work on this machine?". The newcomer doesn't know what Claude Code is, why "Safe analysis" is about *logs you did not write*, or what an MCP server is. "Safe analysis · default" is preselected, which helps. The truncated card line hides "No shell — safe with logs you did not write". |
| 5 | Presses *Apply — Safe analysis*. | Yes. | Yes. | Partly. Nothing happens to their question. The start page now says "Choose how the assistant works first — then ask again". **They must press the button a second time.** |
| 6 | Presses again → draft placed in the assistant, button "Start — ask your question". | Partly. Why Start, after Send? | Yes. | Partly. "Start contacts Anthropic (sign-in check, MCP connect)…" is accurate but jargon-heavy. |
| 7 | Presses Start → "Claude Code — confirm this folder", naming a temp folder they never chose, with Yes/No. | **No.** "Trust this folder" with an unknown path is the classic moment a careful newcomer presses No. | — | If they press No, are they told how to recover? (Verify in the hands-on test.) |
| 8 | Presses Yes → the question is sent → an answer with tool lines. | Yes. | Yes. | Yes. The "model's words, not a verified result" note and the clickable record and node chips are excellent. |
| 9 | The model asks to save a report → approval dialog "Claude Code asks to run analyser_report … server: fluxtion-analyser-hosted". | Partly. They understand "Deny / Allow once" but not "analyser_report" or "hosted". | Yes. | Yes. The transcript marks "denied by you" / "approved by you · ✓". |

**Where the newcomer is lost:** step 4 (cards with no context), steps 5–6 (asking again), step 7 (an unknown folder). **What helps most:** the S1 demo, the chip and verb feedback, "nothing is sent until…", and the transcript.

### (b) A customer's developer diagnosing a production incident, mostly through an external MCP client

| Step | Action | Assessment |
|---|---|---|
| 1 | Launches the analyser and opens the incident log. | The start page has changed: the old cards are now a compact strip *below* a prompt. **Open ▸ Audit log** is still there, one click. Mild re-learning; no blocker. |
| 2 | Their MCP client connects as before (REST on). | Tools are unchanged and `propose` is hidden (per checklist §H). Good. **But** if they relied on the console for the token, it now shows only 8 characters. The docs point to `~/.fluxtion-analyser/rest-endpoint`, and Settings' help still says the token is in the console (T2). Under incident pressure this is a 10-minute detour. |
| 3 | They type in the start box out of habit. | The profile cards open once. The *Off* card and the external line say "external MCP and REST clients are not affected". **Good: this is exactly the reassurance they need.** |
| 4 | A colleague on the same machine turns on Full Claude Code and presses *Close external access while this session runs*. | The external client's next call gets "the analyser's REST transport for external clients is off (Settings ▸ Assistant)" (`ActionServer.EXTERNAL_OFF`). **Settings says it is on.** The developer will toggle settings, restart and lose time. **Major.** *Fix:* a distinct refusal: "external access is closed while the analyser's own full Claude Code session runs; it reopens when that session ends". |
| 5 | Right-click a record ▸ "Ask Claude: What happened here?" | If they never use the in-app assistant, the menu item drafts into a composer they don't use. The draft text includes "(log generation 1)" and "Read it with analyser_read {recordIndex: 16}", which is fine for a model but odd if they copy it into their own client. | Minor. Offer "Copy as prompt for my AI client" beside it. |

**Verdict:** the in-app changes mostly stay out of their way. The two real hazards are the stale token wording and the misleading closed-for-session refusal.

### (c) A demo presenter dropping a prompt pack in front of an audience

| Step | Action | Assessment |
|---|---|---|
| 1 | Prepares beforehand. | If no assistant was chosen on this machine, the **first** Send in front of the audience opens the cards. The presenter must choose a profile beforehand; nothing on the start page reminds them (S1). |
| 2 | Drops the pack on the start page. | The list is replaced by the pack's title and entries, with *← Return to main options*. Clear and quick. **Praise earned.** |
| 3 | Clicks entry 2. | The text is appended to the box. Clicking it again appends a duplicate (S4), which is embarrassing on a projector. |
| 4 | Presses Send. | With a session not yet started: Start, then the folder question. Three acts on stage. *Tip for the checklist: start the session before presenting.* |
| 5 | The audience reads the screen. | Light theme on a projector: the guidance text at #8C8C8C on the blue band is 2.9:1 and washes out. The list and the headline are fine. Dark theme: 4.1:1, marginal. |
| 6 | Drops a broken pack by mistake. | A red one-line cause with its line number and *See the format*; the list in force stays. Recovers gracefully. |
| 7 | Example entry ⤓. | The confirmation leads with the publisher and the third-party label in bold red. An audience may read red as "error"; amber would read as "note" (E1). |

**Verdict:** good bones. The fixes are a pre-flight cue ("Assistant: not chosen"), the duplicate-append fix, and contrast.

### (d) An enterprise admin deciding whether to allow full Claude Code

| Step | Action | Assessment |
|---|---|---|
| 1 | Opens Settings ▸ Assistant to find the controls. | The master switch is in a box titled "Safe analysis", and the Full switch is a fragment, "…with my full Claude Code permissions", in another box (T1). The structure doesn't match the four profiles the cards present. |
| 2 | Looks for "External MCP / REST access" (the cards' wording). | It is labelled "Allow the assistant to drive the UI over localhost (REST)" (P6). |
| 3 | Reads the help text. | It finds a stale claim that the token appears in the console (T2). For a security reviewer, one wrong claim discounts the rest. |
| 4 | Reads the Full warning. | Honest and specific, including that your own tools can read the REST token and drive the analyser. **Praise earned for candour.** But it is one 180-word block, shown twice (P3, F1). |
| 5 | Wants to enforce "no Full Claude Code" across the team. | **No managed or locked setting exists.** Every switch is per-user, in the home folder, and any user can turn Full on. Nothing in the docs tells an admin this. **Major for this persona.** *Fix (product):* a machine or managed policy file (or a launcher property) that disables Full or Claude Code, with the card showing "Disabled by your administrator". *Fix (docs, now):* a short "For administrators" section that states plainly that there is no central lock. |
| 6 | Reads the FAQ. | The Claude Code bullet is about 600 words in one paragraph. *Fix:* a capability matrix (profile × {reads files, writes files, shell, web, your MCP servers, hooks, asks before analyser writes}), plus the "not confined to the folder" line. |

**Verdict:** the safety design is strong and honestly described. Its presentation is hard for an admin to audit, and the lack of a central lock is the decision-relevant gap.

---

## 4. Accessibility

### 4.1 Contrast (WCAG 2.2 AA), computed

Method:
- the WCAG relative-luminance formula;
- token values from `UiTheme` (`surface`, `accent`, `accentText`, `accentWash`, `mix`, `warn/note/attention`) and `TranscriptStyle.LIGHT/DARK`;
- FlatLaf 3.5.1 `UIManager` values (FlatLightLaf / FlatDarkLaf; the app sets no overrides; `grep UIManager.put` is empty).

Washes are mixed exactly as `accentWash()` does (accent into `surface()`). Threshold: 4.5:1 for text, 3:1 for UI components and large text (≥18.66 px bold / 24 px). Nearly all text measured is 12–13 px, so 4.5 applies.

**Every pair below threshold:**

| Theme | Where | Foreground | Background | Ratio | Needs |
|---|---|---|---|---|---|
| Light | Start: lede, band lines, "No AI…" (muted) | #8C8C8C | #E6EFFD hero band | **2.90** | 4.5 |
| Light | Start: strip group labels, captions, Recent text | #8C8C8C | #FCFDFF surface | **3.30** | 4.5 |
| Light | Start: strip chip outline at rest (UI) | #96B5E4 | #FCFDFF | **2.06** | 3.0 |
| Light | Start: prompt box fill vs band (UI boundary) | #FFFFFF | #E6EFFD | **1.16** | 3.0 |
| Light | Start: prompt box border vs band (UI) | #C2C2C2 | #E6EFFD | **1.54** | 3.0 |
| Light | Start: Full cue badge, standard | #B35900 | #E6EFFD | **4.17** | 4.5 |
| Light | Assistant: notes, limits, posture lines (muted) | #8C8C8C | #F2F2F2 panel | **3.00** | 4.5 |
| Light | Assistant: header chip hairline (UI) | #D6DBE1 | #F2F2F2 | **1.24** | 3.0 |
| Light | Composer: Full cue badge, standard | #B35900 | #F2F2F2 | **4.32** | 4.5 |
| Light | Composer: status note (`noteForeground`) | #B05A00 | #F2F2F2 | **4.35** | 4.5 |
| Light | Cards: one-line description | #8C8C8C | #F3F7FE (wash .04) | **3.13** | 4.5 |
| Light | Cards: description on the selected card | #8C8C8C | #D0E1FB (wash .20) | **2.54** | 4.5 |
| Light | Cards: border at rest (UI) | #9FBBE4 | #F2F2F2 | **1.75** | 3.0 |
| Light | Cards: hover/**focus** border (UI; the only focus cue) | #689AE7 | #F2F2F2 | **2.55** | 3.0 |
| Light | Cards: subtitle "Choose once…" | #8C8C8C | #F2F2F2 | **3.00** | 4.5 |
| Light | Transcript: code comment | #6E7781 | #FCFDFF | **4.47** | 4.5 |
| Light | Transcript: code comment on the code block | #6E7781 | #F2F4F7 | **4.13** | 4.5 |
| Light | Settings: muted help paragraphs | #8C8C8C | #F2F2F2 | **3.00** | 4.5 |
| Light | FlatLaf: checkbox border (UI) | #AFAFAF | #F2F2F2 | **1.96** | 3.0 |
| Light | FlatLaf: text-field border (UI) | #C2C2C2 | #F2F2F2 | **1.59** | 3.0 |
| Light | FlatLaf: focus ring (UI) | #98C3EB | #F2F2F2 | **1.65** | 3.0 |
| Light | FlatLaf: default-button border, i.e. Deny's (UI) | #4E9DE7 | #F2F2F2 | **2.57** | 3.0 |
| Light | FlatLaf: button border (UI) | #C2C2C2 | #F2F2F2 | **1.59** | 3.0 |
| Light | FlatLaf: placeholder / inactive text | #8C8C8C | #FFFFFF | **3.36** | 4.5 |
| Dark | Start: lede, band lines (muted) | #8C8C8C | #232E3A hero band | **4.10** | 4.5 |
| Dark | Start: strip chip outline at rest (UI) | #4C6A89 | #1B1F24 | **2.94** | 3.0 |
| Dark | Start: prompt box fill vs band (UI) | #46494B | #232E3A | **1.52** | 3.0 |
| Dark | Start: prompt box border vs band (UI) | #616365 | #232E3A | **2.29** | 3.0 |
| Dark | Assistant: notes and limits (muted) | #8C8C8C | #3C3F41 panel | **3.15** | 4.5 |
| Dark | Assistant: header chip hairline (UI) | #3A4149 | #3C3F41 | **1.03** | 3.0 |
| Dark | Composer: Full cue badge, strong (◆◆) | #FF6FA8 | #3C3F41 | **4.09** | 4.5 |
| Dark | Cards: description on the selected card | #8C8C8C | #2B3D50 | **3.31** | 4.5 |
| Dark | Cards: border at rest (UI) | #496480 | #3C3F41 | **1.73** | 3.0 |
| Dark | Cards: hover/**focus** border (UI) | #5887B6 | #3C3F41 | **2.81** | 3.0 |
| Dark | Cards: subtitle (muted) | #8C8C8C | #3C3F41 | **3.15** | 4.5 |
| Dark | Cards: Apply refusal (red) | #FF7B72 | #3C3F41 | **4.21** | 4.5 |
| Dark | Settings: muted help | #8C8C8C | #3C3F41 | **3.15** | 4.5 |
| Dark | FlatLaf: checkbox / text-field / button borders (UI) | #696B6D / #616365 / #606263 | #3C3F41 | **1.98 / 1.76 / 1.73** | 3.0 |
| Dark | FlatLaf: focus ring (UI) | #3C628C | #3C3F41 | **1.68** | 3.0 |
| Dark | FlatLaf: default-button border (UI) | #557394 | #3C3F41 | **2.16** | 3.0 |
| Dark | FlatLaf: placeholder text | #8C8C8C | #46494B field | **2.70** | 4.5 |
| Dark | Example dialog: third-party lead (warn) | #E87A5A | #3C3F41 | **3.72** | 4.5 |
| Dark | Default button text (Deny, filled) | #BBBBBB | #375A81 | **3.72** | 4.5 |

**Passing and worth noting:** the transcript palette passes everywhere in both themes, except the light code comment (4.47 and 4.13). `TranscriptStyleTest` evidently tests against the transcript background only; the cues that sit on the *panel* (the composer badge) are not tested. Also passing: `accentText` on every wash (5.4–8.4); the start list (6.4 / 7.8); the hero title (18.1 / 7.2).

**Suggested replacements** (verified):
- muted → **#57606A** light (5.71 on panel, 5.52 on band, 4.82 on the selected card) and **#A9B1BA** dark (4.89 panel, 5.14 selected card, 6.36 band);
- Full standard light → **#9A4D00** (5.46 panel, 5.28 band);
- Full strong dark → **#FF8FBF** (5.02);
- dark refusal → **#FF9A92** (5.20);
- dark warn → **#F4A082** (5.15);
- dark default-button text → **#E0E0E0** (5.41);
- card and strip outlines → **#6E7F95** light (3.65) / **#7FA6CF** dark (4.17);
- code comment light → **#5C6670** (5.75 / 5.31).

The FlatLaf borders and focus ring are platform defaults. Override them once with `UIManager.put("Component.focusColor", …)` and `Component.borderColor`, rather than per component.

### 4.2 Keyboard-only paths and focus order

- **Start page:** list (↑/↓ select, Enter chooses) → the "×" on the chip → box → action button → fallback buttons → 13 strip chips → Recent. This is logical. Problems:
  - There is no initial focus target. The code scrolls to the top in `addNotify`, but focus goes to whatever is first. Put focus on the list or the box.
  - Alt+1…9 work only while focus is *inside* the prompt view (`WHEN_ANCESTOR_OF_FOCUSED_COMPONENT`). From a strip chip, Alt+2 does nothing. Bind them on the page instead.
  - There is no keyboard submit from the box (S3).
- **Profile cards:** Tab through four cards, Space to choose, Tab to the fields, Close, Apply. Escape closes. Arrow keys do not move between cards (P5). The focused card's only cue is the hover border at 2.55:1 (light): **focus is not visibly distinct enough** (WCAG 2.4.7 visible focus, 1.4.11 contrast). Give the cards a 2 px outer ring, as the strip chips have. `ActionChip` does it well: an outside ring, inset 2 px. Copy it.
- **Approval dialog:** focus starts on Deny. Enter and Escape deny. Allow is reached with Shift+Tab, then Space after 500 ms. Correct, and safe.
- **Proposal review:** radio pairs per row, Tab through rows, Enter = Close (good: no accidental apply). The reason box is not reachable as text (Fluid text, non-focusable?), so a keyboard user cannot scroll a long reason. Verify.
- **Transcript:** chips and ▸ expanders are focusable buttons, which is good. In a long transcript Tab walks through every chip; there is no skip-to-composer. Add a shortcut (Ctrl/⌘+L or Esc → composer).

### 4.3 Target size (WCAG 2.5.8, 24×24 minimum or the spacing exception)

From the live component tree (`uxr/run.log`):

| Target | Size | Verdict |
|---|---|---|
| Strip `ActionChip`s | 92–153 × 34 | Pass |
| Profile cards | 334 × 79 | Pass |
| FlatLaf buttons (Send, Stop, New chat, Profile…, Close, Apply, Allow once, Deny) | ×23 | 1 px short. Passes under the spacing exception (≥4 px gaps) |
| Transcript tool-line expanders "▸" | **19 × 19**, stacked at 19 px pitch | **Fail.** Adjacent 24 px circles overlap; spacing exception not met |
| Transcript "Copy" | **37 × 16** | Passes by spacing (isolated), but the 16 px height is hard to hit |
| Transcript record/node/chart chips | ×19 | Inline in a sentence → exempt |
| Start chip "×" (remove the chosen entry) | ~18 × 18 (margin 0,4) | **Fail** unless isolated. It is 6 px from the chip, which is not itself a target, so it marginally passes by spacing. Make it 24 × 24 |
| "All options: Settings ▸ Assistant…" link-button | 200 × 19 | Passes by spacing. It also does not look like a link: black text, no underline |
| Start list rows | 1192 × 23 | 1 px short. Rows are contiguous, so the spacing exception fails. Border 3→4 px fixes it |

### 4.4 Screen-reader names and descriptions

- **Mark speech is wrong** for "denied by you" and timeouts (top 10, #5). `TranscriptStyle.Mark.DENIED.spoken = "denied by the posture"`.
- **Label in name (WCAG 2.5.3, Level A):** the strip chip visibly labelled **"Other MCP client"** has the accessible name **"Generic MCP setup"**. **"Fluxtion key"** is named "Fluxtion API key — not found". Neither name contains the visible label, so a speech-input user saying "click Other MCP client" fails. "Settings" → "Source and assistant settings…" contains it but does not start with it. Fix: start each name with the visible label.
- "Profile: Safe analysis…" is named "Choose how the assistant works". The current profile is not spoken. Use "Assistant profile: Safe analysis. Choose…".
- Profile cards: their names and descriptions are set. **Praise earned.** The role should be radio, not toggle.
- Start list rows: "Prompt 3 of 6: …". **Praise earned.**
- The approval countdown is not announced (D3).
- The Full badge's name and description are set. **Praise earned.**
- The proposal radios are named "Item 2 processor: Yes". Use the plain kind words (R1).

### 4.5 Not relying on colour alone

- Full-posture cue: border + ◆/◆◆ + words + the Send label. **Pass.**
- Tool-line marks: ✓, "refused", "denied by you", "approved by you · ✓". **Pass.**
- Selected profile card: ✓ + tint + border. **Pass.**
- Pack refusal: words + red. **Pass.**
- Example third-party: the words "third-party" + red. **Pass.**
- Disabled children in Settings: greyed *and* not editable. **Pass.**
- **Header chips:** the "strong" chips (Claude Code, the Full profile) differ from the others by colour and weight only. Acceptable, since the words carry the meaning.

---

## 5. Friction count per key task

The counts are from the code paths and docs at HEAD, without a live `claude` run. Treat them as minimums.

| Task | Clicks / keys | Dialogs or overlays | Decisions | Notes |
|---|---|---|---|---|
| First question from the start page, nothing set up (→ Safe) | 5 (Send, Apply, press again, *Start — ask your question*, Yes) | 1 overlay (cards) + 1 dialog (folder) | 2 (profile, trust folder) | Three "send"-type presses for one question (top 10, #3). |
| Ask about a record, session running | 3 (right-click, Ask…, Send) | 0 | 0 | Good. No pop-up, as the owner asked. |
| Ask about a record, session not started | 3 (right-click, Ask…, *Start — ask about this*) | 0, or 1 the first time a folder is trusted | 0–1 | Good. |
| Chart a series (Claude Code) | 0 | 0 | 0 | `graph`, `series` and `spotlight` are pre-allowed. Good. |
| Write a report into the project | 1–2 per `report` call | 1 per call | 1 per call | A report built in two or three calls means two or three dialogs. |
| Write the report as a PDF, file exchange off (the default) | the approval, then a refusal, then Settings (≈4 clicks), then ask again and approve again | **2 approvals + Settings** | 3 | **Futile approval.** The dialog should not ask for a call the settings will refuse. |
| Save a walk | 1 | 1 | 1 | — |
| Claude plays a saved walk | 1 | 1 | 1 | **Unnecessary**: `walk {play}` changes nothing, but `walk` is in the destructive set as a whole. |
| Claude opens a different log | 1 | 1 | 1 | Correct to ask. `open` replaces what is open. |
| Turn on Full Claude Code, then first question | ~7 (Profile…, Full card, *Turn on full Claude Code*, *Turn on* again in the warning, Start, *Trust for full Claude Code*, Send) | 1 overlay + 2 dialogs | 3 | The same warning is read twice (P3). |
| Drop a prompt pack and run entry 2 | 3 (drop, click, Send) | 0 | 0 | Plus Start if the session isn't running. |
| Download an example | 4–5 (choose, *Download example…*, folder, Download, open offer) | 2 | 2 | Appropriate for a remote fetch. |
| Switch project while Claude Code runs | 2 (switch, choose) | 1 | 1 (three choices) | Clear choices. |

**A normal Safe-analysis investigation** (ask about a record, chart a series, write a report, save a walk):

| | Approvals | Dialogs |
|---|---|---|
| Minimum | **2** | 2 |
| Realistic (report in 2 calls, a walk save plus a play, one `open` by the model) | **4–5** | 4–5 |
| A file write with file exchange off | +2 (approved then refused, then approved again) | +Settings |

Each dialog costs a read, a 0.5 s wait, then Shift+Tab + Space or a click. That is fine for a destructive act, tedious for an additive one.

**Recommendations, keeping the security model intact.** None of A–C weakens an approval for a destructive act. D does, and says so.

- **A. Never ask a futile question.** Before showing the dialog, check whether the call would be refused anyway: file exchange off, a path outside the exchange directory, `open {project, create}` without its switch. Deny it with a named reason and an *Open Settings ▸ Assistant* button. This removes approvals; it does not weaken any.
- **B. Split `walk` and `report` by sub-action.** `walk {play}` and `walk {list}` (and a report *preview* or read, if one exists) change nothing. Add them to `HOSTED_PRE_ALLOWED` as sub-actions. `walk {delete|replace|rename}`, every file write and every overwrite keep their dialog. This removes approvals for **non-mutating** calls only.
- **C. Queue, don't auto-deny, a second request** that arrives while a dialog is open. Show "1 more request waiting" in the dialog. Today the second call is denied at once and Claude Code may retry, which shows up as a "denied" line the person never decided.
- **D. Optional, and an explicit weakening; the owner must decide:** a checkbox in the approval dialog for `report` / `walk` *saves inside the project*: "Also allow saving reports and walks in this project until this turn ends". It applies only to restorable project writes (the Reports tab can restore deleted reports and walks). It must **never** cover `open`, `import`, `source_root`, `screenshot`, any file write, or any delete. Each covered call still appears in the transcript as "approved by you (turn allowance) · ✓". **This does weaken per-call approval for those two verbs, and that must be stated in the FAQ's security answer if adopted.** My recommendation is to ship A–C first and measure.

---

## 6. Wording

**Assistant and profile vocabulary is not yet one set.** The names seen in the product and its docs:

| Where | Name used |
|---|---|
| Header | "Live assistant" |
| Mode combo | "Claude Code · your plan", "In-app · API key" |
| Cards | "Safe analysis", "Full Claude Code", "In-app assistant", "Off" |
| Settings sidebar | "LLM" (for In-app) |
| Header chip | "Safe analysis (default posture)" |
| Transcript note | "the analyser assistant's posture" |
| Settings tooltip | "the analyser-assistant posture" |
| `S6.hint` | "In the analyser assistant's posture" |
| User guide title | "Analyser assistant" |
| Transcript role label | "Claude Code" (and "Assistant" for in-app) |
| Start entry button | "Ask with Claude Code" |
| Context-menu prefix | "Ask Claude:" |

Recommended set for now (before the rename):

| Concept | Word |
|---|---|
| The thing | **the assistant** |
| The way it works | **profile** (Safe analysis / Full Claude Code / In-app assistant / Off) |
| Who answers | **Claude Code** or **the in-app assistant** |

Remove "posture", "Live assistant", "analyser assistant" and "LLM page" from user-facing text. "Telamin AI" can later replace "the assistant" in one place if the strings are centralised. Today they live in at least `LocalAgentPanel`, `LocalAgentSession`, `ConfigPanel`, `TranscriptStyle`, `AskCatalogue.properties` and `StreamJson`.

**Jargon leaking to users:**
- "posture" (see the top 10);
- "hosted" ("server: fluxtion-analyser-hosted");
- "named by the server";
- "log generation 1" in drafted questions (it is fine for the model, but the person sees it in their composer);
- "MCP connect";
- "local bridge";
- "in-process";
- "compute over the index";
- "build curation";
- "firstParty" (already hidden; good);
- `sourceRoot` / `RESOLVES` / `UNKNOWN` in the proposal review;
- "Diagnostics: 3 command(s) not classified for the analyser";
- "POSTURE_CHANGED:" / "BUDGET_SPENT:" codes.

**Alarming or unclear warnings:**
- The Full warning is accurate but front-loads mechanism (settings, plugins, subagents, connectors) before consequence. Lead with consequence (F1).
- "Don't resume this conversation in a terminal while it is open here — the history would fork." "Fork" is developer slang. Try: "…or the two copies of the conversation will diverge." (This now shows only once a conversation exists. Good.)
- "Audit-log records are data from the system you are investigating and can contain text written to steer an AI" is a good, plain statement of prompt injection. Keep it.
- "Denied automatically in 120 s if not answered": clear.
- "Not answered — it is being denied": fine.
- Example confirmation: "Downloading does not trust the folder for Claude Code." That is correct but cryptic to anyone who hasn't met folder trust. Try "Downloading does not let Claude Code work in that folder; it will ask when you press Start."

**Small wording fixes:**
- "No AI assistant can answer here yet — these run without one: [User guide]". A guide doesn't "run". Try "No assistant is set up yet. Without one you can:".
- "Send" on the start page with nothing set up doesn't send; it opens the cards. Label it "Ask" or "Continue".
- "Set up…" / "Open bundle…" / "Show templates" verbs: good. "Play": good.
- "Profile: Not chosen…" is fine; "Assistant: not chosen…" would be clearer.

---

## Quick wins (each under an hour)

1. `ProfileOverlay.wrap()`: append "…" when words remain, and shorten `SAFE_CARD_LINE` to fit two lines. (Top 10, #1.)
2. `UiTheme.mutedForeground()` → #57606A / #A9B1BA instead of `Label.disabledForeground`. One line; fixes about 20 contrast failures. (Top 10, #2.)
3. `TranscriptStyle.Mark`: speak the visible words. Map "denied by you" and "denied: not answered…" to their own spoken text. (Top 10, #5.)
4. Rename the chip `"Safe analysis (default posture)"` → "Safe analysis". Change "denied by the posture" → "denied: not available in Safe analysis", and the `StreamJson` and `LocalAgentAdapter` "posture" strings to profile names.
5. `StartPromptView.renderFallbacks()`: don't add the "these run without one:" label when there are no fallback buttons. (S6.)
6. The start box: bind Enter to the action and Shift+Enter to a newline, as the composer does. Add a placeholder. (S3.)
7. Make the start action the default and accent button, at least 32 px tall, aligned to x=44 (FlowLayout hgap 0 with an explicit gap). (Top 10, #9.)
8. Fix the label-in-name mismatches: "Generic MCP setup" → "Other MCP client — set up", "Fluxtion API key — not found" → "Fluxtion key — not found".
9. Settings help: replace "URL + token appear in the status bar (and console)" with the endpoint-file wording. (T2.)
10. Rename the REST checkbox to the cards' words, "External MCP / REST access…". (P6.)
11. `ActionServer`: a distinct refusal text when external access is closed for a full session. (Persona b, step 4.)
12. The Safe folder confirmation: verb buttons "Trust this folder" / "Cancel"; for analyser-owned scratch folders, say what the folder is before the path. (F2, F3.)
13. Proposal review: show Claude's reason on row selection, not on answer. (R2.)
14. Tool-line expanders and the start chip "×": minimum 24×24. Start list cell border 3→4 px. (§4.3.)
15. Full badge: the state first ("◆ Full Claude Code (default) — running"), so ellipsis never eats it. (A7.)
16. The duplicate append on a repeated click of a prompt entry. (S4.)
17. Profile cards: a 2 px outer focus ring like `ActionChip`'s. (§4.2.)
18. Drop "Copy prompt is for an external assistant." from the limits lines where no Copy prompt button shows. (A5.)

## Larger items (worth a tracker line)

- One assistant control in the header (merge the mode combo and Profile…). (Top 10, #8.)
- Carry the pending question through card selection, so the first question takes one press after Apply. (Top 10, #3.)
- Call-level sentences in the approval dialog, derived from the input's sub-action. (Top 10, #6.)
- Pre-flight denial of futile approvals; sub-action pre-allow for `walk {play|list}`; a queue for second requests. (§5 A–C.)
- Restructure Settings ▸ Assistant around the four profiles (T1), and an administrator section with a capability matrix. Decide whether a managed lock for Full Claude Code is in scope (persona d).
- A colour-semantics pass: reserve the Full colour for the Full cue only; make *denied* neutral. (§2.)
- A dialog convention: one position for the safe default across all M74 dialogs. (Top 10, #7.)

## What is genuinely good (earned)

- The approval dialog's mechanics: Deny default, Enter/Escape/close deny, a 500 ms Allow delay, no mnemonic, never default-capable, verbatim input with invisible characters escaped, the input labelled as the model's data.
- "Nothing is sent, opened or started until you press the button": true at every step checked, and said at the point of action.
- The transcript: role labels and marks in words, clickable record and node chips, "the model's words, not a verified result", and a palette that meets contrast in both themes.
- Prompt-pack refusal: one cause, its line number, and a way to the format.
- The example download confirmation leads with who publishes it and refuses moving references.
- The Full cue is redundant across border, glyph, words and the button label: never colour alone.
- The candour of the Full warning about what the analyser cannot confine.
- *Off* and the external line on the cards answer the question an external-client user will ask.

---

*Artefacts produced for this review (not committed):*
- `scratchpad/uxr/shots/*.png`: fresh HEAD captures, light and dark;
- `scratchpad/uxr/run.log`: the target-size and accessible-name dump;
- `scratchpad/uxr/contrast.py`: the contrast computation;
- `scratchpad/uxr/wt/`: the build copy with the two capture harnesses, `UxrShotsFrameTest`, `UxrDialogShotsFrameTest`.

Every visible string in the fresh captures was read. They contain only DEMO fixture names, temp paths and placeholder values.

---

## Disposition (2026-10-17, branch `feature/claude-code-p3`)

Filed with the fix pass that answers it. Every behaviour fix has a test with a wrong-result witness (rule 8): the
witness is named in each test's comment. Commits: **`3bce1ac2`** theme tokens and contrast; **`d132906a`** session decisions
(the carried question, approvals, external refusal, words the nodes write); **`15af62a3`** dialogs (the call's own sentence,
one consent rule, the proposal review); **`fc2d264d`** start page, header and cards; **`59cd52c5`** plain words across the panels and
Settings; **`bbf8dd27`** spotlight captions; **the docs commit that files this review** this file, spec r15, CHANGELOG and docs. Screenshots for the hands-on test
were captured (not committed) under an isolated `user.home`, FlatLaf applied, light and dark, and every visible string
was read.

### The coordinator's list

| # | Item | Disposition | Commit | Test (★ = with a witness) |
|---|---|---|---|---|
| 1 | `ProfileOverlay.wrap()` drops words; the Safe card is cut off | **Fixed.** `wrap` never drops a word: with a line limit the last line ends "…", with none every line is kept; each card is as tall as its text at its width (it re-asks the grid when its width changes); the whole line stays the tooltip and accessible description. The Safe line itself is unchanged (pinned by `FaqSecurityContractTest`). | `fc2d264d` | ★ `ProfileCardLineTest` |
| 2 | Contrast: muted token; every pair below AA, light and dark; the dark start box; the card focus border | **Fixed.** `UiTheme.mutedForeground()` is #57606A / #A9B1BA. New tokens with explicit-theme forms: `controlBorder`, `focusRing`, `placeholderForeground`, `noteForeground`, `errorForeground`, `deniedForeground`, `fullCue`; FlatLaf's borders, focus ring, default-button border, placeholders and the dark default button's text corrected once in the look and feel's defaults (`ThemeManager.overrides`). The start box is a surface-filled field with a 3:1 border on its band (accent while focused); the profile cards' rest border is `controlBorder` and focus is an outer 2 px accent ring. Transcript code comment #5C6670; full cue #9A4D00 / #FF8FBF; dark refusal #FF9A92; dark warn #F4A082; light attention #8A5A00. Settings' help notes use the muted token, not the disabled grey. | `3bce1ac2`, `fc2d264d`, `59cd52c5` | ★ `ThemeContrastTest` (witness: the old #8C8C8C, #B35900, #C2C2C2, the 1.52:1 box and the 2.55:1 card border fail the same check) |
| 3 | First question friction: carry the pending question through Apply | **Fixed (rule 9, `startPagePrompt`).** The question that opened the cards is carried; after a successful Apply, once the frame has reported the new consent, what can answer and the profile, it is composed for the applied profile's assistant exactly as a start-page press would be: placed, Start armed for Claude Code (folder trust still asked at Start), never sent. Off, closing the cards, or an assistant that still cannot answer: nothing placed, the box keeps its words, the page says why. The composer's own first-use Send keeps its text in the composer (unchanged). | `d132906a` | ★ `StartPageCarryTest` |
| 4 | "posture", "hosted", "generation", codes must not reach users; a static rule | **Fixed.** Every string the review listed, and others found, reworded (*Safe analysis*, *Full Claude Code*, "the analyser's own tools", "load 3 → 4"); note labels in words instead of "[posture changed]"; the server line no longer names the bridge. `UserVisibleJargonTest` scans every literal in `ui/` and the M74 sources the panel shows, with a two-entry reviewed exemption list (the shared canvas's research/authoring posture — AI ▸ Posture — is that feature's own name) and `ActionExecutor` exempt (its strings are the model's, not the person's). Drafted questions still name the log "generation" for the model (see below). | `d132906a`, `fc2d264d`, `59cd52c5` | ★ `UserVisibleJargonTest` |
| 5 | `Mark.DENIED.spoken` says "denied by the posture" for every denial | **Fixed.** `TranscriptStyle.spoken(mark, outcome)` speaks the line's own words; the adapter's marks name who decided (*denied by you*, *denied: not answered within 120 s*, *denied: not allowed in Safe analysis*, *allowed: it changes nothing*); the CLI's mode denial reads *denied by Claude Code's permission mode*. | `3bce1ac2`, `d132906a` | ★ `TranscriptStyleTest.theSpokenWordsFollowTheReason` (live accessible names), ★ `DenialWordsTest` |
| 6 | The approval dialog describes the verb, not the call | **Fixed.** `PermissionText.hostedCall` gives one sentence per sub-action of every asked verb (walk, report, screenshot, open, source_root, import) and per built-in with a target (Bash, Edit, Write, NotebookEdit, WebFetch, WebSearch), with its consequence; quoted values escaped and cut at 60 characters; the verb's rationale only as a fallback; the exact input still shown below. | `15af62a3` | ★ `PermissionTextTest.eachSubActionSaysWhatThisCallDoes` (25 sub-actions), ★ `deleteAndPlayAreDifferent` |
| 7 | One button order and one default across the approval, full-folder-trust and Safe folder dialogs | **Fixed.** Convention: the safe choice is the default and the right-most button (the platform's default-button place); the risky one stands alone at the left, apart, never default-capable, no mnemonic; Escape and closing choose the safe one. `SafeChoice` builds the row and the two folder dialogs; the Safe question is *Trust this folder* / *Don't trust* (it was a stock Yes/No defaulting to Yes). | `15af62a3` | ★ `SafeChoiceFrameTest` (new; in both `ci.yml` lists), `SafeChoiceTest` |
| 8 | Two header controls for one choice; the fixed "your plan" | **Fixed.** One control, *Assistant: <profile> ▾*, opens the cards; the mode follows the applied profile (`ChooseAssistantModeEffect`); the combo and "your plan" are gone (the billing chip says plan or key). Its accessible name starts with the visible label. The "Live assistant" label shows only in a demonstration. | `fc2d264d` | ★ `AssistantVocabularyTest.oneControlForHowTheAssistantWorks` |
| 9 | The start page's main action; the dark box | **Fixed.** The button is accent-filled (bold) while it can be pressed, at least 32 px and as tall as the box, at the box's right edge; the box is a surface-filled field with a visible border and a placeholder. | `fc2d264d` | ★ `StartPromptViewTest.theMainActionIsTheStrongest` |
| 10a | Check settings before asking (file exchange off) | **Fixed.** `McpTools.hostedCallKind` classifies a call from its input (`WRITES_FILE`: every screenshot, a walk's reel, a bundle, a report with a path, the single-record report); with file exchange off (`AssistantFileExchangeObserved`, a new fact) `localAgentPermission` denies it without a dialog, telling the model why and the person where to turn it on. | `d132906a` | ★ `ApprovalFrictionTest.aFutileWriteIsNeverAsked` |
| 10b | Pre-allow non-mutating sub-actions | **Fixed, verified from the code.** Exactly `walk {name, play: true, step?}`, `walk {end: true}`, `walk {restore: true}`, `report {restore: true}` (no other field): `WalkVerb.play`/`end` post a request and persist nothing; the two `restore: true` forms list a bin. Allowed by the node without a dialog (`PermissionBy.NON_MUTATING`, recorded, no grant) and admitted by `ActionServer` without a grant, both judging the call's own input. `import {bundle}` (a preview) and `source_root {}` (a listing) change nothing too but read a file the model names or show machine paths: kept asked. Every mutating sub-action is still asked. Spec: a refinement of OD-74.16 (r15). FAQ updated. | `d132906a` | ★ `ApprovalFrictionTest.aNonMutatingSubActionIsNotAsked`, ★ `.theClassification`, ★ `HostedGrantServerTest.nonMutatingSubActionsNeedNoGrant` |
| 10c | Queue a second request instead of denying it | **Fixed.** Waits behind the open dialog (up to 8; one more is denied as busy), shown after the first is answered (also after a timeout or the CLI's withdrawal); denied if its turn ends, the session exits, on Stop, New chat or a settings change. The dialog says *"1 more request is waiting"*, rendered from `PermissionState.queued`. Spec: OD-74.17 amended (r15). | `d132906a`, `15af62a3` | ★ `LocalAgentPermissionTest.aSecondRequestWaitsUnshowableAndStaleAreDenied`, ★ `ApprovalFrictionTest.aWaitingRequestEndsWithItsTurn` |
| — | "Allow for this turn" bulk approval | **Not built** — owner decision: no per-turn bulk approval for now; revisit after the hands-on test. | — | — |
| 11 | External refusal when closed for a session | **Fixed.** `actionListener` tells the performer why external clients are refused (`ConfigureActionListenerEffect.closedForSession`); the server answers *"external access is closed while an in-app Claude Code session runs … the External MCP/REST access setting is still on"*. | `d132906a` | ★ `HostedCredentialTest.theRefusalSaysWhyExternalAccessIsClosed`, `ActionListenerTest` |
| 12 | Settings help says the token is in the console | **Fixed.** It names `~/.fluxtion-analyser/rest-endpoint`, readable only by you, and that the console shows the token's first 8 characters. | `59cd52c5` | ★ `AssistantVocabularyTest.theSettingsHelpIsTrue` |
| 13 | One name for external access | **Fixed.** *External MCP/REST access* on the cards' line, the Settings checkbox, the AI menu item (was *Local MCP / REST enabled*; `MenuInventory` and the docs follow), the limits line, the `--rest` console line and the MCP server instructions. | `15af62a3`, `fc2d264d`, `59cd52c5`, `bbf8dd27` | ★ `AssistantVocabularyTest.oneNameForExternalAccess` |
| 14 | Label in name; 24×24 expanders | **Fixed.** *Other MCP client — set up*, *Fluxtion key — not found*, *Settings — source and assistant…*, and a rule in `StartPanel.accessibleName` that prefixes a title missing its label; the assistant control's name starts with its label. Transcript expanders and *Copy* buttons, and the start chip's ×, are at least 24×24 (each tool line is then 24 px apart, so the targets do not overlap); list rows 4 px padding. | `3bce1ac2`, `fc2d264d` | ★ `AssistantVocabularyTest.theAccessibleNameContainsTheVisibleLabel` |
| 15 | Enter in the start box and the composer | **Fixed** (the composer's behaviour): Enter sends, Shift+Enter is a newline, IME composition respected; the hint *Enter sends · Shift+Enter starts a new line* is shown under the box and in the composer's placeholder and accessible description. | `fc2d264d` | ★ `StartPromptViewTest.enterSendsAsTheComposerDoes` |
| 16 | Proposal review: plain labels; the reason before answering | **Fixed.** *Source folder*, *Processor*, *Graph file*, *Runbook*, *Log to open*; verdicts in words (*found: a folder with 12 Java files*, *found in the source; whether it matches the log is not known yet*, …); *Claude's reason — unverified — item n* shows the first row's reason at once and the pointed-at or focused row's after. | `15af62a3` | ★ `P2bSurfacesTest.theReviewSpeaksPlainly` |
| 17 | One colour for three meanings | **Fixed.** Full mode keeps its own cue colour with its ◆ text and border; a denied call is a neutral ink, bold, with its words; status notes (the composer's hint) are teal; none is colour alone. | `3bce1ac2` | ★ `ThemeContrastTest.eachMeaningHasItsOwnColour` |
| 18 | Mac checks: Alt+R, Alt+digit | **Verified broken, fixed.** A probe under FlatLaf 3.5.1 on macOS showed the mnemonic bound to Ctrl+⌥+R (so "(Alt+R)" was wrong) and that ⌥+1 types "¡" into a focused text area. The page's shortcuts are now bound explicitly with ⌘ on macOS and Alt elsewhere, at window level while the page shows; each row's tooltip and the Return button's name the shortcut that works. | `fc2d264d` | ★ `StartPromptViewTest.keyboardAndNames` (on macOS: ⌘ bound, ⌥3 not) |
| 19 | The review's other quick wins (under an hour) | See the quick-win table below. | | |
| 20 | Per-user switches; the central admin lock is P4 | **Documented, not built.** FAQ security answer and the user guide's new *For administrators* section; Settings ▸ Assistant's header note says so too. | `59cd52c5`, the docs commit that files this review | `FaqSecurityContractTest` (unchanged claims hold) |
| 21 | Owner: spotlight captions never cut silently | **Fixed.** Wrap at `UiTheme.CALLOUT_LINE_CHARS` (40) characters, at most `CALLOUT_MAX_LINES` (3), the third ending "…"; a word longer than a line is broken, never left to run out of the card; on hover or keyboard focus the callout expands in place, in the same card, on top, kept inside the frame, and collapses when the pointer or focus leaves; each callout is a focusable target whose accessible name is the whole caption (pure presentation, no session state). The guidance every entrance carries (MCP server instructions, in-app and REST manifests) adds *"Keep a caption under about 80 characters … and put the detail in your answer."* | `bbf8dd27` | ★ `SpotlightCaptionNeverCutTest`, ★ `SpotlightGuidanceIsAtEveryEntranceTest.everyEntranceSaysKeepCaptionsShort` |

### The review's quick wins

| # | Quick win | Disposition |
|---|---|---|
| 1 | `wrap()` ellipsis; shorten the Safe line | Fixed (the card grows; the line is pinned, so it is not shortened). |
| 2 | Muted token | Fixed. |
| 3 | Mark speech | Fixed. |
| 4 | Rename the chip; posture strings | Fixed (*Safe analysis*). |
| 5 | No "these run without one:" label with no buttons (S6) | Fixed. |
| 6 | Start box: Enter, Shift+Enter, placeholder (S3) | Fixed. |
| 7 | Start action: accent, 32 px, aligned | Fixed (beside the box, at its right edge). |
| 8 | Label-in-name mismatches | Fixed. |
| 9 | Settings help: endpoint file (T2) | Fixed. |
| 10 | REST checkbox in the cards' words (P6) | Fixed. |
| 11 | `ActionServer` refusal when closed for a session | Fixed. |
| 12 | Safe folder confirmation: verb buttons; scratch folder said in words (F2, F3) | Fixed. |
| 13 | Proposal review: reason on selection (R2) | Fixed (on pointing or focus, and the first row's at once). |
| 14 | Expanders and the start chip's × at 24×24; list rows | Fixed. |
| 15 | Full badge: the state first (A7) | Fixed: *Full Claude Code (mode) — running · your tools and permissions*. |
| 16 | No duplicate append on a repeated click (S4) | Fixed. |
| 17 | Profile cards: a 2 px outer focus ring | Fixed. |
| 18 | Drop "Copy prompt is for an external assistant" where no Copy prompt shows (A5) | Fixed (dropped from the Claude Code limits; the in-app mode shows the button). |

### Other findings in this review

| Finding | Disposition |
|---|---|
| A1/A2 duplicated Safe lines that upsell Full | Fixed: Safe's posture line states what Safe does (*your own skills, hooks and MCP servers are not loaded*); the limits line no longer says "choose Full Claude Code". The chrome is otherwise unchanged (collapsing it to one row is a layout change, not a quick win). |
| A8 verdict codes | The codes are parsed off before display (`verdictOf`); the words after them are reworded; note labels are words. |
| T3 one name for the exchange folder | Fixed in Settings (*Exchange folder (this machine)* / *(this project)*). |
| T6 jargon in Settings | Fixed ("compute over the index", "build curation", "In-process", "the LLM page"). |
| D1 "hosted", "named by the server" | Fixed (*the analyser's own tool (analyser_walk)*; MCP tools: *its name for the tool*). |
| D3 the deadline for a screen reader | Fixed: the dialog's accessible description states it once. |
| T1 Settings ▸ Assistant restructure around the four profiles | **Not done**: a larger restructure, not asked for in this pass (tracker-worthy). |
| S1, S2, S5, S7 (status line, external-client naming, band lines), S8, A4, A6, A9, P2, P3, P5, F1, E1, E2, R3, W1, T4, the target-size rows not listed above, §4.2's skip-to-composer and page-level focus | **Not done** in this pass: each is outside the coordinator's list and over the hour, or a design call (copy and layout) for the owner's hands-on test; recorded here so none is lost. |
| Drafted questions say "(log generation 1)" and "analyser_read {…}" | **Kept**: the words are for the model, which needs the generation to bind the draft; the person sees them in the composer. Revisit with the owner (the review called this "fine for the model"). |
| §5 D, a per-turn allowance for restorable project writes | **Not built** — the owner decided: no bulk approval for now. |

### Owner items

- **Spotlight captions** (owner, 2026-10-17): delivered as item 21 above.
- **No per-turn bulk approval for now** (owner decision, 2026-10-17): recorded; revisit after the hands-on test.
- **A central administrator lock** (P4): documented as planned and not built.
