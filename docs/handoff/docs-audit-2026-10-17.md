# Docs site audit and proposed information architecture

> **Status (2026-10-17): the owner APPROVED the proposed task-first nav structure (§5) on 2026-10-17.**
> The restructure lands **after M74 merges and before the Telamin Canvas rename** (§5.4), as its own PR.
> The §4 "Fix in M74" list is done on `feature/claude-code-p3`, re-checked against the usability fix pass
> (`1cb247ea..a1053178`), which renamed the REST switch to *External MCP/REST access* and merged the header into
> *Assistant: \<profile\> ▾*; the line numbers below are those of `1cb247ea` and have since moved.

- **Source:** worktree `analyser-p3`, branch `feature/claude-code-p3`, HEAD `1cb247ea` (2026-10-17). The audit is read-only.
- **Scope:** `docs/site/` (41 nav pages, 57 PNGs plus 8 journey PNGs), `mkdocs.yml`, `src/main/resources/help/help.html`, and the prompt-pack and M75 branches.

> **The worktree is moving under this audit.** 49 files are uncommitted. They include `ConfigPanel`, `MainFrame`, `AssistantPanel`, `ProfileOverlay`, `StartPanel`, `StartPromptView`, the permission and transcript classes, and **`docs/site/connect-an-llm.md` and `docs/site/index.md` themselves**.
>
> Claims below are checked against **HEAD**. Where the work in progress (WIP) changes a label, both values are given. The headline case is the REST switch:
>
> | Place | HEAD | WIP |
> |---|---|---|
> | Settings checkbox | "Allow the assistant to drive the UI over localhost (REST)" | "External MCP/REST access (for an AI client on this machine)" |
> | AI menu item | "Local MCP / REST enabled" | "External MCP/REST access" |
> | Assistant header | a mode selector ("Claude Code · your plan" / "In-app · API key") plus **Profile…** | one control, **"Assistant: \<profile\> ▾"** (review M74 usability #8) |
>
> **Re-run the "fix in M74" list once the WIP lands.**

`mkdocs build --strict` passes on a copy of HEAD's site. There are no broken links or anchors, and there are no orphan pages: every `.md` is in the nav except `assets/README.md`, which is excluded on purpose.

---

## 0. Summary

### Page status counts (41 nav pages)

| Status | Count | Pages |
|---|---|---|
| ACCURATE | 15 | the-loop, sample-conversations, install, user-guide/start-page (at HEAD), sample-prompt-pack, topology, source-navigation, reports, plugins, evidence-bundles/opening, evidence-bundles/demo, log-format, composing-a-system, integrating-a-vendor-component, release-notes |
| STALE | 26 | home, connect-an-llm, support, ai-and-runbooks, spring-authoring, spring-authoring-conversations, guided-start, spring-authoring-getting-started, getting-started, tutorial-playground, user-guide/index, records-and-filtering, graphs, **assistant**, projects, project-panel, portable-context, sharing-setups, evidence-bundles/index, sending, reference, journeys/find-the-first-recorded-breach, format-spec, producing-a-log, audit-cost, faq |
| OBSOLETE (whole page) | 0 | — |
| DUPLICATE (whole page) | 0 | Heavy duplication exists, but at section level (§1.7) |

Even the ACCURATE pages carry **stale screenshots**: none of the 65 PNGs shows the M74 UI (§3).

### The structural finding

The site is organised by **feature** ("User guide" is a list of panels) and by **history**: "Working with AI" was written before Claude Code ran inside the app.

None of the 13 Working-with-AI and Getting-started pages mentions any M74 feature:
- Claude Code in the tab
- profiles
- Ask the assistant
- approvals
- `propose`
- the start prompt
- prompt packs

All M74 material lives in **one 1,166-line page**, `user-guide/assistant.md`. That page mixes all six usage paths and still contains the main P1 (external MCP) reference.

The commercial path (P1) has no page of its own that starts from "an incident happened". It has a plumbing page (connect-an-llm) and fragments in assistant.md, support.md and sample-conversations.md.

---

## 1. Page inventory

**Path key:**
- **P1** external MCP diagnosis
- **P2** in-app diagnosis
- **P3** authoring with Claude
- **P4** getting started
- **P5** demos and teaching
- **P6** admin and security
- **R** reference

`file:line` evidence is relative to `src/main/java/telamin/fluxtion/audit/analyser/analyser/` unless it starts with `docs/`.

### 1.1 Home and Working with AI

| Page | Purpose | Paths | Status | What is wrong (evidence) |
|---|---|---|---|---|
| index.md (Home) | Marketing overview | P1 P2 P4 | STALE | See below; also being edited in the WIP |
| the-loop.md | Five-stage build-with-AI loop; "shared research canvas" | P1 P3 P5 | ACCURATE, incomplete | See below |
| spring-authoring.md | What Spring authoring expresses; design → build → inspect | P3 | STALE (minor) | See below |
| spring-authoring-conversations.md | Illustrative design / "what if?" dialogues | P3 P5 | STALE | See below |
| connect-an-llm.md | External MCP client set-up and checks | **P1** P6 | **STALE** | See §4; also being edited in the WIP |
| sample-conversations.md | Seven generated MCP transcripts on DEMO | P1 P5 | ACCURATE | See below |
| support.md | On-call path from "a log arrived" to an answer | P1 P2 | STALE | See below; the page is self-labelled draft (129–133) |
| ai-and-runbooks.md | Runbook, glossary and saved-analysis pointers; posture | P1 P2 P6 | STALE | See below |

**index.md**
- Line 19 says the first button "loads a recorded run". The page now leads with the numbered prompt list.
- Line 52 still uses the *Explain* wording, which is gone (replaced by **Ask**).
- Line 100 calls the Codex / Claude / Other MCP client entries "cards". They are buttons in the *Connect an assistant* block.
- Nothing on Claude Code in the tab, the profiles or the start prompt.
- `screenshot-dark.png` is pre-M74.

**the-loop.md**
- Lines 102–106 present MCP as the *only* way an AI connects.
- Claude Code hosted in the tab is absent.
- The owner's thesis (intent and facts → LLM structure → Fluxtion mechanics → evidence validates intent) is only implicit.

**spring-authoring.md**
- Lines 55–62: "the analyser does not edit Java … build a project" needs restating, because full Claude Code in the tab can do both, with approval.
- No mention of `propose` / *Set up a project with Claude…*.

**spring-authoring-conversations.md**
- Lines 14–16 say the workflow "requires the next compiler release". It is published (spring-authoring.md:18).
- Line 144 says design and producer findings are "(next analyser release)". They shipped in 1.16.0.

**sample-conversations.md**
- The transcripts are external-client only. There is no in-tab transcript with chips.
- Line 4 carries an odd "Screenshots refreshed…" banner.
- The `conv-*` images have the new layout but the old **Explain** labels.

**support.md**
- Line 104: the toolbar **Flag** "writes a note and a fix". It only toggles the flag (`ui/MainFrame.java:4787`). The note and fix come from *Records ▸ Write a finding for this record…* (`:1057`).
- Line 53 says "a fourth case" after four bullets.
- The "Working with an AI" section (120–125) mentions neither Ask nor Claude Code. This page is the natural P2 home.

**ai-and-runbooks.md**
- Line 145 says the glossary goes into "every *Explain* prompt". *Explain* is gone.
- Lines 288–289 call M38.8 "a planned slice". It shipped, and the same page says so at 216–218.
- Lines 29–31 ("no verb writes a runbook") need a nuance: hosted-only `propose` can *offer* pointers.
- `ai-menu.png` and its caption are stale.
- The status-light table is duplicated verbatim in connect-an-llm.

### 1.2 Getting started

| Page | Purpose | Paths | Status | What is wrong |
|---|---|---|---|---|
| guided-start.md | Paste-in prompt for an external assistant: install, `--rest`, connect, tour | P1 P4 | STALE (image, gaps) | See below |
| spring-authoring-getting-started.md | Illustrated Spring starter path | P3 P4 | STALE (minor) | See below; line 3 admits the shots are old |
| getting-started.md | Quick start, own log, Settings tour, Follow | P4 P6 | **STALE** | See below |
| install.md | JBang, jar, source, `--rest` | P1 P4 P6 | ACCURATE | See below |
| tutorial-playground.md | Playground bundle → analyser | P4 P5 P1 | STALE | See below |

**guided-start.md**
- `start-page.png` is the old card page. Its alt text describes "the three questions" of the old page.
- Missing: the start page's own *Run me through a demo* (S1, which needs no AI).
- Missing: starting Claude Code *inside* the analyser.

**spring-authoring-getting-started.md**
- Line 111 says the provider and model are "under Settings ▸ Assistant". They are on the **LLM** tab (`ui/ConfigPanel.java:648–658`).
- Lines 84–87 cover the external route only. There is no *Set up a project with Claude…*.
- All four `spring-*` shots show the pre-1.20 **File** menu.

**getting-started.md**
- Line 11: *Open the demo log* no longer exists. The equivalents are *Run me through a demo*, *Guided tour* and *Sample project*.
- Lines 17–23 describe the old "three questions" page.
- Lines 99–100 list the Settings tabs without **Plugins** (`ConfigPanel.java:253`).
- Lines 138–140 describe the Assistant tab as max rounds / in-process / "localhost REST transport". It is now grouped *Safe analysis / Full Claude Code / In-app assistant / Every assistant* (`ConfigPanel.java:760–838`).
- Lines 126–128 say Copy prompt includes the action protocol. It now does so only when *Include my local REST token* is ticked.

**install.md**
- Line 45 says "localhost REST transport". It should use the final switch label.
- Nothing on the token: the console prints only an 8-character hint; read `~/.fluxtion-analyser/rest-endpoint`.

**tutorial-playground.md**
- Line 147: "select records and click **Explain**" is now **Ask** / **Ask about this** (`AskCatalogue.properties:76–80`).
- Lines 178–180 ("your IDE agent edits code") are blurred by full Claude Code in the tab.
- `ai-menu.png` is stale. The four `tutorial-*` shots have the old File menu and the M36 start page.
- **`tutorial-source.png` shows a real personal name** in a generated Javadoc `@author` line, and `tutorial-cycle.png` / `tutorial-source.png` show a real public equity ticker. See §3.

### 1.3 User guide

| Page | Purpose | Paths | Status | What is wrong |
|---|---|---|---|---|
| user-guide/index.md | Section landing | P4 | STALE | Lists only 8 of 13 pages (missing start-page, sample-prompt-pack, projects, reports, plugins); the assistant line says nothing of Claude Code or profiles; `screenshot-light.png` is pre-M74 |
| start-page.md | Start prompt, assistant choice, packs, examples, *Or open directly*, Recent | P4 P5 P3 P6 | ACCURATE at HEAD | Matches `AskCatalogue.properties` S1–S6 and `StartPanel.java:188–233`. **No screenshot of the page it documents.** WIP risk: Alt+1–9 / Alt+R become ⌘ on macOS, Enter sends, the badge wording changes |
| sample-prompt-pack.md | A droppable pack | P4 P5 | ACCURATE | Parsed by `PromptPackTest`. No prose; relies on start-page.md |
| records-and-filtering.md | Table, detail, search, filter, diff | P2 core | STALE (minor) | Line 16 lists the right tabs without Topology and Reports (`MainFrame.java:4538–4575`). Ask from right-click is not mentioned |
| graphs.md | Series, formulas, guides, markers, notes | core P1 P5 | STALE (minor) | Line 225 "Export CSV / Export PNG" is an **Export ▾** menu (`GraphPanel.java:148–153`); *Ask … explain this point* is not mentioned |
| topology.md | Graph view, step-through, node actions | core P1 P5 | ACCURATE | The node menu omits *Show declaration (matched by name)* (`TopologyPanel.java:1697`) and the Ask question |
| **assistant.md** | Everything assistant (1,166 lines) | **P1 P2 P3 P5 P6** | **STALE** | Section map below |
| source-navigation.md | Roots, click-through, Spring design, Java spotlight | P1 P3 | ACCURATE | Lines 25–37 are implementation detail; `java-source-spotlight.png` is admitted stale |
| projects.md | Profiles, templates, switching, restore | P4 P3 P1 P6 | STALE / disorganised | See below |
| project-panel.md | What-is-in-force panel | P1 P6 P3 | STALE (minor) | Line 30 "the **File menu**" no longer exists; 74–78 duplicated; `project-panel.png` shows the old start page beside it |
| portable-context.md | Tiers, runbooks, glossary, environments, analyses | P1 P6 | STALE | See below |
| reports.md | Reports tab, walks | P1 P5 | ACCURATE | — |
| sharing-setups.md | Export / Import settings | P6 P5 | STALE (minor) | See below |
| plugins.md | Log-reader SPI and its trust boundary | P6 | ACCURATE | — |

**projects.md**
- Lines 3–7 open with internal jargon ("session driver's dispatch cycle").
- The tier table (line 23) lists 5 things. Profiles also hold focuses, reports, walks, runbooks, glossary, environments and analyses.
- Lines 285–289 are duplicated in two other pages.
- The `template-picker.png` shot is admitted outdated.

**portable-context.md**
- Line 5 says "as of this milestone".
- Lines 77–78 say M38.8 "will surface" the description. It shipped.
- Lines 309–312, "What comes next", list features documented as shipped on the same page.
- Line 89: the category label now adds "and descriptions" (`config/SettingsShare.java:67`).
- Line 112 still uses the *Explain* wording.

**sharing-setups.md**
- The category labels differ from `SettingsShare.java:51–80`, including "…reports **and spotlight walks**" and "LLM provider/model/base-URL (never the API key)".
- The page doesn't say that Claude Code settings and profiles are machine-tier and never exported.

#### assistant.md, section by section

| Lines | Section | Status | Notes and evidence | Goes to (§5) |
|---|---|---|---|---|
| 1–11 | `# Analyser assistant` | ACCURATE | Rename hotspot (tab title `MainFrame.java:4575`) | Assistant panel |
| 13–25 | Fault-finding in practice | ACCURATE | — | Diagnose in the analyser ▸ overview |
| 27–47 | One assistant, two modes | ACCURATE at HEAD, **WIP-stale** | The mode selector is removed; one "Assistant: … ▾" control | Assistant panel |
| 49–71 | Choose how the assistant works | ACCURATE at HEAD, **WIP-stale** | "Profile…" → "Assistant: … ▾" | Admin ▸ Assistant profiles |
| 73–101 | The in-app assistant | ACCURATE | — | Assistant panel |
| 103–131 | Asking about what is on screen | ACCURATE | Line 124 "in-process actions" matches no switch label at HEAD or in the WIP | Ask the assistant |
| 133–220 | Claude Code: turn on, Start, Safe / Full | ACCURATE at HEAD, **WIP-stale** | "Claude Code · your plan" and "(default posture)" change | Claude Code in the analyser |
| 222–262 | Approvals | ACCURATE | `ui/LocalAgentPermissionDialog.java:49–51`; 120 s deny, 60 s single-use grant | Approvals |
| 264–301 | *Using it* (bold paragraph) | ACCURATE, **misfiled** under Approvals | — | Claude Code in the analyser |
| 303–323 | How the conversation is drawn | ACCURATE, WIP-stale badge wording | — | Reading the transcript |
| 325–339 | No API key? Copy prompt | ACCURATE | — | Assistant panel |
| 341–354 | Setup | **STALE** | "Settings ▸ Assistant / LLM" is two tabs; the provider is on **LLM** | Admin ▸ Settings |
| 356–429 | Actions the assistant can take | ACCURATE, wrong page | Line 359 uses the wrong switch label | Reference ▸ Verbs |
| 431–476 | Is the log whole? | **STALE** | "five answers"; the table and code have six (`StreamEndReport.java:46,67`) | The audit log |
| 478–531 | Ask it to show you (spotlight) | ACCURATE; stray technical paragraph at 499–502 | — | Demo & teach ▸ Spotlights |
| 533–580 | Save it as a walk | ACCURATE; caveats come before the intro | — | Spotlights, walks, reels |
| 582–608 | Set up a project with Claude | ACCURATE, **misfiled**; "the Claude Code tab" does not exist | `LocalAgentPanel.java:117`; `ProposalReviewDialog.java:40,59` | Build ▸ Set up a project with Claude |
| 610–662 | Walks with a conversation; reels | ACCURATE | Line 656 is a rename spot | Spotlights, walks, reels |
| 664–882 | Connect an MCP client, open / context reference, file exchange | Mostly ACCURATE, **STALE** in places | 861–864 say four destructive verbs; there are six (+`walk`, `import`) and the read-only list omits `source` (`mcp/McpTools.java:30,91–92`); 874 field label; 804 *Explain* | Diagnose with your AI client ▸ Connect, and Reference |
| 884–904 | Does my client launch the analyser? | **STALE** | 897 switch label; 901–902 error text ≠ `mcp/McpBridge.java:82–85`; 903 `--rest` prints the endpoint-file path only on a **first** run (`MainFrame.java:7298`), otherwise the URL and an 8-character token hint (`:5507`) | Troubleshooting |
| 906–943 | Set up an MCP client in the app | **STALE** | 908 "Settings ▸ Assistant / LLM"; the button is in Settings ▸ Assistant ▸ *Every assistant*, and **AI ▸ Connect an AI client…** is not mentioned | Connect your client |
| 945–1007 | Your first session | **STALE** | 954 "17 tools", no `analyser_import`; there are **18** (`llm/VerbSchemas.java:21–438`); 970 "the tab above" | Your first diagnosis |
| 1009–1059 | Hand the diagnosis on; let your agent set it up | ACCURATE | — | Your first diagnosis |
| 1061–1096 | Coverage, setup notes | ACCURATE, overlaps topology.md | — | Topology / Reference |
| 1098–1135 | How it finds the analyser; not working; can / can't | ACCURATE but incomplete | Does not say that full Claude Code plus external access lets the session's own tools act as an external client, or mention *Close external access while this session runs* | Security model |
| 1137–1166 | Saved charts; session restore; templates | **DUPLICATE** of projects.md and project-panel.md | 1163 "Create from template" is now **From template** (`StartPanel.java:204`) | Delete |

### 1.4 Evidence bundles and journeys

| Page | Purpose | Paths | Status | What is wrong |
|---|---|---|---|---|
| evidence-bundles/index.md | What a `.fexp` is | P5 P1 | STALE (minor) | "No replay" is untrue for format-2 bundles; no `replay/` member row; "two commands" when there are three (`--verify`, `--unpack`, `--replay-compare`, `Main.java:36`) |
| sending.md | Capture with `report {bundle}` | P1 P5 | STALE (P1 gap) | Never says capture needs **Allow assistant file exchange** (`ui/ActionExecutor.java:266`), or that under Safe analysis `report` triggers an approval |
| opening.md | Verify, unpack, open | P5 P1 | ACCURATE | Lines 9–20 belong on the reference page |
| reference.md | Verb, CLI, manifest, replay | R P5 P1 | STALE (one count) | Line 3 says "two headless commands"; the heading at 19 says three |
| demo.md | `tools/evidence-bundle-demo.py` round trip | P5 | ACCURATE | The script reads the endpoint file, so the token change doesn't break it |
| journeys/find-the-first-recorded-breach.md | Scripted walk journey (generated by `capture-journey.py`) | P5 P4 | STALE | See below |

**journeys/find-the-first-recorded-breach.md**
- Line 80 points to "Settings ▸ Assistant" for the provider; it is on the **LLM** tab.
- Line 80 offers no Claude Code in the tab and no profiles.
- Line 82 is a placeholder ("not in this build").
- `journey-assistant-step-*.png` show the old assistant panel.

### 1.5 The audit log

| Page | Purpose | Paths | Status | What is wrong |
|---|---|---|---|---|
| log-format.md | Friendly format tour | R P2 | ACCURATE | — |
| format-spec.md | Normative spec and conformance | R | STALE (minor) | The conformance table omits fixtures c19–c24 (`src/test/resources/conformance/`) |
| producing-a-log.md | Making a processor emit a log | P3 P4 R | STALE | Lines 175–182 say the analyser reads the **text** sink; it now opens FLXA binary logs too (`spi/binary/BinaryAuditReader.java`) |
| audit-cost.md | Benchmark of the cost of auditing | R (evaluation) | STALE | See below |
| composing-a-system.md | Supplier jars via Spring | P3 R | ACCURATE | — |
| integrating-a-vendor-component.md | Worked vendor example | P3 | ACCURATE | `vendor-topology.png` is kept on purpose as historical evidence |

**audit-cost.md**
- Headline arithmetic is wrong: 21.27 − 8.62 = 12.65, not 12.9; 13.32 − 3.73 = 9.59, not 9.3.
- Figures from different runs are mixed without saying so.
- Nothing links to the page.
- The memory note *Measure the shipped artefact* applies: re-measure, don't hand-fix the figures.

### 1.6 FAQ and release notes

| Page | Status | What is wrong |
|---|---|---|
| faq.md | **STALE** | See below. Questions accurate: size, API key, Follow, graphable, troubleshooting |
| release-notes.md | ACCURATE (mechanism) | A placeholder that `.github/workflows/pages.yml:30–33` overwrites with `cat CHANGELOG.md`. It publishes **[Unreleased]** from main and gives the page a second H1 |

**faq.md: what is wrong**
- *Profile…* (line 109) no longer exists in the WIP; the control is "Assistant: … ▾".
- `walk` "writes no file of its own" (line 98) is false: `walk {reel}` writes HTML into the exchange folder (`VerbSchemas.java:479`).
- "Nothing outside the loaded log" (72–73) is false: `source` rereads files under the source roots.
- Hosted-only `propose` is not mentioned (line 158).
- "Turn on local transport" does not match the real label.
- *Does it work offline?* omits that Claude Code Start contacts Anthropic.
- Keyboard shortcuts omit Alt+↑/↓ (transcript) and the start-prompt digits.
- `FaqSecurityContractTest` pins substrings only, so it catches none of the above.

### 1.7 Duplication (section level)

- **Install commands** appear in five places: install, index:22–31, getting-started:9, guided-start:33/116, tutorial-playground:61.
- **The `--rest` explanation** is in both install:39–50 and connect-an-llm:42–53.
- **The status-light table and owner-handover paragraph** are verbatim in connect-an-llm:108–122 and ai-and-runbooks:253–269.
- **processorDeclarations, saved charts without a log, session restore and templates** each appear three times: assistant.md 1137–1166, projects.md, project-panel.md.
- **Share categories** appear in three tables that disagree: sharing-setups, portable-context 296–307, projects 20–23.
- **"No key? Copy prompt"** appears in index, getting-started, tutorial-playground, start-page and assistant.
- **"Which assistant answers" / first-use cards:** start-page 35–57 and assistant 49–71.
- **Three "first ten minutes" pages** (getting-started, guided-start, tutorial-playground) and **none uses the start prompt**, which is now the in-app first ten minutes.
- **`help.html`** (in-app Help ▸ User guide) is a separately hand-written, older copy of the user guide (§5.5).

---

## 2. What is missing, per usage path

### P1: external MCP diagnosis (the commercial path)

The questions no page answers:

1. **"An incident happened. What do I do, start to finish?"** Nothing starts from the incident. The pieces are scattered across support.md (draft), assistant.md 945–1059 and sample-conversations.
2. **"What can the client do to my machine, and what must I approve?"** There is no one-page model per verb class (read-only / render / destructive-writes-file / opens-replaces). The destructive set is miscounted (4 vs 6).
3. **"Why does my client say *not running* while Claude Code runs in the tab?"** Full Claude Code plus external access offers *Close external access while this session runs* (`session/node/ActionListener.java:62`, `ui/LocalAgentPanel.java:279`). With that closed, external clients are refused. This is undocumented and has no troubleshooting row.
4. **"How does my automation get the token now?"** Read `~/.fluxtion-analyser/rest-endpoint` (mode 600, `{url, token, pid, startedAt}`) and check `pid` liveness. The console prints only an 8-character hint (`MainFrame.java:5505`). The CHANGELOG says this; no site page tells automation authors.
5. **"Which window will my client reach?"** The *MCP elsewhere* status light is documented, but not tied to the multi-window or incident workflow.
6. **"How do I hand the diagnosis to someone else?"** Evidence bundle capture, `report`, PDFs. Covered in pieces; no "hand-off" task page.
7. **"Which verbs exist, with their arguments?"** There is no verb reference page. It is buried in assistant.md 356–429 / 664–860 and miscounted (17 vs 18).
8. **"How do I run REST without MCP (scripts, CI)?"** `GET /manifest` gets one sentence (connect-an-llm:189). Nothing on headers, the token, the 429 rate limit or the `Origin` rule.
9. **A CLI reference**: `--mcp`, `--rest`, `--verify`, `--unpack [--into]`, `--replay-compare`, `--help`, exit codes. Split between install and evidence-bundles/reference.
10. **A prompt pack for incident triage** aimed at external clients. The sample pack is for the start page only.

### P2: diagnosing inside the analyser

1. **A picture of the assistant panel**, in both modes, with the header explained. No screenshot exists.
2. **"What can I ask, and from where?"** The Ask catalogue (six questions × surfaces: table right-click, Records menu, toolbar **Ask**, detail *Ask about this*, diff *Ask Claude about this diff*, node, chart point, time slider). Only part of it appears in assistant.md 103–131.
3. **"How do I read the transcript?"** Record and node chips (click → goto?), tool-line marks (`✓`, `refused: …`, `denied: not run`), expansion, Alt+↑/↓, Copy, the 500-entry cap, "the model's words, not a verified result".
4. **"Why did an approval dialog appear, and what does Allow once mean?"** Documented, but buried and not task-shaped.
5. **"Safe analysis or full: which do I need for this?"** A decision table.
6. **"What does each mode cost?"** Plan vs API key billing, the spend limit (OD-74.24), the per-answer cost shown with an API key.
7. **"It refused. Why?"** A troubleshooting table for the panel's refusals: no key, Off, mid-turn profile change, stale draft, `AskUserQuestion` denied, Plan mode denied.

### P3: authoring with Claude

1. **The thesis page**: *you state intent and facts; Claude structures components; Fluxtion writes the mechanics; the evidence validates the intent.* It is implicit in the-loop and spring-authoring and stated nowhere.
2. **"Set up a project with Claude"** as a task page: choose the folder → Start → `propose` → review dialog (what each item means, what is refused OUTSIDE the folder) → apply. Currently a misfiled section of assistant.md.
3. **"How do I check that what was built does what I said?"** The evidence-validates-intent step: coverage, producer findings, a report against the stated intent. No page connects them.
4. **"Full Claude Code in the tab vs my terminal or IDE"**: what differs (hooks re-checked by digest, `.mcp.json`, approvals in-app, ◆ cue).
5. **The authoring prompt pack** and the posture menu's *Authoring / deploy* setting: one sentence total.

### P4: getting started

1. **One "Start here"** that routes by who you are. Today there are three competing first-ten-minutes pages.
2. **A screenshot of the new start page**, and of the *Or open directly* grid.
3. **"What is an example entry, and is downloading it safe?"** Commit pinning, `sha256`, the third-party badge, the publisher line, the deadline, why release-asset URLs aren't accepted (OD-74.30). Covered in start-page.md but not as a safety explanation.
4. **The guided tour and the sample project**: what each is and where its files go (`~/.fluxtion-analyser/demo/`). Mentioned only.
5. **Starter templates**: which template for what. S4 asks the question; no page answers it.
6. **"Writing your own prompt pack"** (format, limits, lint): a section exists; a task page and a linter pointer do not.

### P5: demos and teaching

1. **"Run a demo for an audience"**: guided tour → pack → walk → reel → bundle, with timings.
2. **The philosophy packs** (branch `docs/m74-virgin-onboarding-pack`): no page yet; they would be downloadable from the site.
3. **Reels**: what they contain, where they are written, file-exchange prerequisite.
4. **Lessons (M75)**: a placeholder for when they land.

### P6: administration and security

1. **A one-page security model** (tracker B11, a pre-customer-release item). Today it is spread over FAQ:60–160, assistant.md 222–262 / 1098–1135 and the CHANGELOG ▸ Security. It should cover:
   - trust boundaries (loopback, token, `Origin`, rate limit; the hosted credential vs the REST token; per-instance endpoint file)
   - what each profile can do
   - single-use grants
   - folder trust digest
   - the stated limits: `@path` imports, parent `CLAUDE.md` files and hook script contents are not re-checked; full Claude Code can read the REST token
2. **A Settings reference**: every tab and every switch, with its machine vs project tier. No page documents Settings ▸ Assistant's grouped layout.
3. **Per-user switches and their defaults**, as one table: all AI off by default, `--rest` persists, create-projects, file exchange, spend limit.
4. **What is stored where**: `~/.fluxtion-analyser/config` (cleartext key), endpoint file, demo folder, scratch, profile files. Partly in the FAQ.
5. **Managed policy (P4)**: a "planned" page stating what an administrator can and cannot enforce today.
6. **Data egress**: what leaves the machine, per profile (Anthropic for Claude Code; your provider for in-app; nothing for Off), and what a bundle or report contains.

---

## 3. Screenshots

**Headline:** 65 images (57 in `assets/`, 8 in `assets/journeys/`). **None shows the M74 UI.**
- The newest images were committed on 2026-10-05; M74 is dated 2026-10-17.
- No image is referenced but missing. One is unused: `spotlight-walk-dark.png`.

### Three stale elements appear in every main-window shot

| Element | In the shots | In the code |
|---|---|---|
| Toolbar | **Explain** | **Ask**, disabled until a record is selected |
| Record detail | **Explain with LLM** | **Ask about this** |
| AI menu checkbox | "Local MCP / REST enabled" | That is HEAD; the WIP renames it "External MCP/REST access" |

Shots dated 2026-09-29 or earlier also show the old left rail and no Context / Facts / Canvas column headings.

### Per image

| Image(s) | Used by | Shows | Current? | Producer |
|---|---|---|---|---|
| start-page.png | getting-started, guided-start | Old card start page | **No.** No prompt, no block grid | capture-docs (just re-run) |
| screenshot-dark / -light | home, user-guide index | Full window | No (labels, layout) | capture-docs |
| project-panel, records-overview, flagged-only, source-navigation, topology-step-through, topology-explore | user-guide pages | Main window states | No (labels, layout) | capture-docs |
| graph-series-light/dark, graph-step-dark, graph-markers-dark, graph-bands-dark, reports-dark | graphs, reports, tutorial | Charts, reports | No (frame) | capture-docs |
| projects-file-menu, tutorial-new-project-menu | getting-started, projects, tutorial | Project menu | No (missing *Open recent log and topology…*; name says "file") | capture-docs `--projects-menu` |
| sources-menu, audit-log-menu | getting-started | Menus | Items current, frame old | capture-docs |
| **ai-menu.png** | ai-and-runbooks, connect-an-llm, tutorial | AI menu | **No.** Checkbox label, frame | capture-docs |
| projects-active, ai-runbooks-panel | projects, ai-and-runbooks | Demo project | No (frame) | capture-docs |
| spotlight(-dark), spotlight-findings-light/dark, spotlight-walk | assistant | Callouts, walk strip | Callout styling current, frame old | capture-docs |
| spotlight-walk-dark | **unused** | — | — | capture-docs |
| mcp-generic-setup, mcp-claude-code-confirm | connect-an-llm, tutorial | Connect dialogs | Probably current | capture-docs `--mcp` (`McpSetupDocCapture.java`) |
| template-picker, tutorial-template-picker | projects, tutorial | Template dialog | Probably current (projects.md says outdated) | capture-docs `--template-picker` |
| tutorial-project-open, tutorial-log-open, tutorial-cycle, tutorial-source | tutorial-playground | Bundle walkthrough | **No.** Old File menu, M36 start page. **tutorial-source shows a real personal name in a Javadoc `@author`; cycle and source show a real equity ticker** | capture-docs `--tutorial` |
| conv-* (5) | sample-conversations | Results of conversations | Layout current, Explain labels stale | capture-conversations.py |
| journeys/journey-step-1..4 | journey page | Walk strip | No | capture-journey.py |
| journeys/journey-assistant-step-1..4 | journey page | **Old** assistant panel | **No** | capture-journey.py |
| spring-project-landing, spring-design, spring-validation-finding, spring-validation-clean | spring-authoring-getting-started | Spring project | No. File menu; the text even says "File ▸ Open…" | capture-spring-authoring.py |
| java-source-spotlight | source-navigation | Java spotlight | No. Old toolbar; shows a machine-specific temp path | **No harness** |
| chart-delete-confirmation | graphs | Dialog | Probably current | Hand / test capture |
| vendor-topology | vendor example | Historical evidence | Kept on purpose | No harness |
| spring-starter-preview | spring getting-started | External website | n/a | No harness |

**The personal name in `tutorial-source.png` is a rule-1-class leak the four-term sweep cannot see.** Regenerate it with a placeholder author, or crop it, before the next release.

### What the capture harness can reach

`tools/capture-docs.py` runs the built jar under an isolated `user.home`, drives it over the REST socket and takes `screencapture -l <window id>`. Its siblings are `capture-conversations.py`, `capture-journey.py` and `capture-spring-authoring.py`.

There is a **spec gap**: `spec-claude-code-provider.md` (§5 visual acceptance and §10) says `capture-docs.py` drives the pane through `FixtureAgent`. No such code exists. The tracker lists "screenshots regenerated" as open M74 acceptance.

| M74 surface | Reachable now? | What is needed |
|---|---|---|
| Start prompt and block grid | **Yes**, re-run (`open {close:"all"}`) | A doc-capture class for a dropped pack or chosen-entry state |
| Unified panel, idle, each profile | Probably | Seed config keys (`claudeCodeAllowed`, `assistant.claudeCode.profile`, provider); a dummy `claude` on PATH; select the tab via a walk step's `view.tab` |
| Profile overlay (4 cards) | No (a header button) | Doc-capture class `doClick()` |
| Claude Code transcript with chips | No (Start is a button, by design, D-AI4) | Doc-capture class in `ui`: set `MainFrame.localAgentLauncher` to a fixture replayer (copy test `FixtureAgent`, replay `docs/specs/evidence/m74-p0/fixtures/*.jsonl` sanitised), `doClick` Start and confirm |
| Permission dialog | No | Same, with a permission-request fixture (as `LocalAgentPermissionFrameTest`) |
| Propose review dialog | No | Same, with an `analyser_propose` fixture (`SetupOnboardingRouteTest`) |
| In-app conversation | No | A fake provider endpoint, or feed `AssistantTranscript` directly |
| Ask menus | Records menu via `screenshot {scope:"menu:Records"}` after `goto`; right-click popups no | Doc-capture class calling the `AskMenus` builders |
| Settings ▸ Assistant | No (modal) | Doc-capture class calling `ConfigPanel.show(..., "Assistant")` |

One new `tools/M74DocCapture.java`, modelled on `McpSetupDocCapture.java` and reusing the `FixtureAgent` and fixtures, would cover six of these surfaces. The M74 smoke harnesses need a live logged-in `claude`, so they are not usable for docs, but their reflection helpers are reusable.

**Also stale:** `assets/README.md`. Its inventory omits about 30 assets, it has an empty broken fence at lines 80–81, and its last capture audit is dated 2026-09-29.

---

## 4. Fix in M74: wrong today for P1 (external MCP diagnosis)

These must be correct before release. Re-check each against the final labels once the WIP commit lands.

1. **One name for the REST switch, everywhere.** The code (HEAD vs WIP) and the docs disagree:
   - connect-an-llm:43 says "*Settings ▸ Assistant ▸ localhost REST transport*".
   - assistant.md 359, 897, 1114 and 1130 use the same wrong label.
   - getting-started:138–140 and install:45 say "localhost REST transport".
   - faq says "Turn on local transport".
   - connect-an-llm's "*AI ▸ External MCP/REST access*" matches the WIP, not HEAD.

   Pick the WIP label and apply it to every page, and recapture `ai-menu.png`.
2. **The tool count is 18, not 17. `analyser_import` is missing.**
   - connect-an-llm:30 ("seventeen in all"), :138 and the `/mcp` listing at :142–148.
   - assistant.md:954.
   - Also the contributor docs: ONBOARDING's "17 including M69 `walk`".
   - Say once that `propose` is hosted-only and never external.
3. **Destructive verbs: six, not four** (`open, source_root, screenshot, report, walk, import`; `mcp/McpTools.java:91–92`). The read-only list must include `source`. Fix at assistant.md 861–864.
4. **Token handling.** Add this to connect-an-llm (the technical check) and install (`--rest`): the console prints the URL and an 8-character hint only (`MainFrame.java:5505–5507`); automation must read `~/.fluxtion-analyser/rest-endpoint` (mode 600; `{url, token, pid, startedAt}`) and check `pid` liveness.
   - assistant.md:903 ("`--rest` prints where the endpoint file is") is true only on a first run (`MainFrame.java:7298`).
   - connect-an-llm:53 ("the console names the endpoint file") has the same problem.
5. **The bridge's error text, quoted verbatim** (connect-an-llm:162–164, assistant.md:901–902). The actual text is: "analyser not running, or REST transport disabled — enable it in Settings ▸ Assistant, or start the app with `java -jar fluxtion-auditlog-analyser.jar --rest` …" (`mcp/McpBridge.java:82–85`).
6. **Where *Connect an AI client…* lives.** It is **AI ▸ Connect an AI client…** and Settings ▸ **Assistant** ▸ *Every assistant*. Fix these, which say otherwise:
   - connect-an-llm:59 ("Settings ▸ Assistant / LLM")
   - assistant.md:908
   - the FAQ
   - The Start-page entry point is the *Connect an assistant* block: *Connect Codex* / *Connect Claude* / *Other MCP client — set up* (`StartPanel.java:208–218`).
7. **External access while Claude Code runs in the tab.** Add a section and a troubleshooting row to connect-an-llm:
   - With the REST switch off, the hosted listener refuses external clients.
   - With full Claude Code and the switch on, the panel offers *Close external access while this session runs*. Once chosen, external clients are refused until the session ends ("External MCP/REST access is closed while this Claude Code session runs…", `session/node/ActionListener.java:62`).
   - Neither is documented, so an external user sees *not running*-style failures with no explanation.
8. **FAQ security claims** (the test pins substrings only, so these slip past it):
   - `walk {reel}` writes a file.
   - `source` reads outside the loaded log.
   - Mention hosted-only `propose`.
   - Replace *Profile…* with the WIP control label.
9. **evidence-bundles/sending.md**: `report {bundle}` is refused unless *Allow assistant file exchange* is on, and the page should say so (`ui/ActionExecutor.java:266`). Also fix "two commands" → three in reference.md:3 and index.md:45.
10. ***Explain* → *Ask***, wherever a P1 page tells the person what to click:
    - tutorial-playground:147
    - index:52
    - ai-and-runbooks:145
    - portable-context:112
    - assistant.md:804
    - `help.html`'s "Toolbar shortcuts are unchanged"
11. **The CHANGELOG [Unreleased] entry itself** still describes the mode selector (*Claude Code · your plan* / *In-app · API key*) and **Profile…**, which the WIP replaced with "Assistant: \<profile\> ▾". It feeds the in-app notes and the site's release-notes page.
12. **Screenshots that are wrong on P1 pages:**
    - `ai-menu.png` (connect-an-llm, ai-and-runbooks, tutorial)
    - `start-page.png` (getting-started, guided-start)
    - `tutorial-source.png`, which must be regenerated anyway for the personal name

    Re-run `capture-docs.py` after the WIP lands; it already reaches both of the first two.

---

## 5. Proposed information architecture (task-first)

### 5.1 Nav tree

```
Home                                        index.md  (rewrite: four doors — Diagnose, Build, Start, Admin)
Start here                                  start-here.md  (NEW)

Diagnose an incident
  ├ With your AI client (MCP)               diagnose/index.md  (NEW: the incident → evidence → hand-off path)
  ├ Connect Claude Code, Codex or any client  connect-an-llm.md  (rewrite; M74 fixes)
  ├ Your first diagnosis session            diagnose/first-session.md  (NEW from assistant.md 945–1059 + support.md)
  ├ Sample conversations                    sample-conversations.md  (keep, regenerate)
  ├ Journey: find the first recorded breach journeys/find-the-first-recorded-breach.md  (keep, regenerate)
  ├ Runbooks, glossary and posture          ai-and-runbooks.md  (fix; dedupe status table)
  ├ Hand the diagnosis on                   diagnose/hand-off.md  (NEW: report, PDF, bundle — links into Evidence bundles)
  ├ Scripts and CI over REST                diagnose/rest-automation.md  (NEW: endpoint file, /manifest, headers, 429)
  └ Troubleshooting the connection          diagnose/troubleshooting.md  (NEW from connect-an-llm + assistant 884–904, 1098–1135)

Diagnose in the analyser
  ├ Overview: fault-finding with the assistant  inapp/index.md  (NEW from assistant 1–25)
  ├ The assistant panel                     inapp/assistant-panel.md  (NEW from assistant 27–101, 325–354)
  ├ Ask the assistant                       inapp/ask.md  (NEW: the question catalogue × surfaces)
  ├ Claude Code in the analyser             inapp/claude-code.md  (NEW from assistant 133–220, 264–301)
  ├ Reading the transcript                  inapp/transcript.md  (NEW from assistant 303–323 + chips/marks)
  ├ Approvals                               inapp/approvals.md  (NEW from assistant 222–262)
  └ The canvas
      ├ Records, detail & filtering         user-guide/records-and-filtering.md  (fix)
      ├ Graphs                              user-guide/graphs.md  (fix)
      ├ Topology & step-through             user-guide/topology.md  (keep)
      ├ Source navigation                   user-guide/source-navigation.md  (keep)
      ├ Reports                             user-guide/reports.md  (keep)
      └ The Project panel                   user-guide/project-panel.md  (fix, dedupe)

Build with Claude
  ├ Intent, mechanics, evidence             build/index.md  (NEW: the owner's thesis; absorbs the-loop.md)
  ├ Set up a project with Claude            build/set-up-with-claude.md  (NEW from assistant 582–608 + propose review)
  ├ Validate the intent with evidence       build/validate-intent.md  (NEW: coverage, producer findings, report)
  ├ Spring authoring                        spring-authoring.md  (fix)
  ├ Spring authoring step by step           spring-authoring-getting-started.md  (fix, regenerate)
  ├ Design and "what if?" conversations     spring-authoring-conversations.md  (fix)
  ├ Producing an audit log                  producing-a-log.md  (fix: binary)
  ├ Composing a system from supplier jars   composing-a-system.md  (keep)
  └ Integrating a vendor component          integrating-a-vendor-component.md  (keep)

Get started
  ├ Install & run                           install.md  (keep; owns install commands; token note)
  ├ The start page                          user-guide/start-page.md  (keep + screenshot)
  ├ Guided tour and sample project          start/tour-and-sample.md  (NEW from getting-started 1–40)
  ├ Starter templates and examples          start/templates-and-examples.md  (NEW from projects 43–90 + start-page example entries)
  ├ Prompt packs                            start/prompt-packs.md  (NEW: merges sample-prompt-pack.md + start-page § packs)
  ├ Your own log, step by step              getting-started.md  (rewrite as "open your own log")
  ├ Guided start with an external assistant guided-start.md  (fix)
  └ Playground to analyser in 10 minutes    tutorial-playground.md  (fix, regenerate)

Demo and teach
  ├ Running a demo                          teach/index.md  (NEW: tour → pack → walk → reel → bundle)
  ├ Spotlights, walks and reels             teach/spotlights-walks-reels.md  (NEW from assistant 478–580, 610–662)
  ├ Evidence bundles                        evidence-bundles/index.md  (fix; merge demo.md in as a section)
  │   ├ Sending an investigation            evidence-bundles/sending.md  (fix)
  │   └ Opening a bundle you were sent      evidence-bundles/opening.md  (keep; move 9–20 to reference)
  ├ Fluxtion philosophy packs               teach/philosophy-packs.md  (NEW when the pack branch lands)
  └ Lessons                                 teach/lessons.md  (NEW at M75: index of bundled lessons)

Administer and secure
  ├ Assistant profiles                      admin/profiles.md  (NEW from assistant 49–71 + Settings groups)
  ├ Security model and limits               admin/security.md  (NEW: the B11 one-pager; FAQ security moves here)
  ├ External access (MCP / REST)            admin/external-access.md  (NEW: switch, --rest persistence, endpoint file, closing per session)
  ├ Settings reference                      admin/settings.md  (NEW from getting-started 97–159 + ConfigPanel tabs)
  ├ What is stored where, what leaves       admin/data.md  (NEW)
  ├ Projects, profiles and sharing          user-guide/projects.md  (rewrite: merges portable-context + sharing-setups; one tier/category table)
  ├ Log-source plugins                      user-guide/plugins.md  (keep)
  └ Managed policy (planned)                admin/managed-policy.md  (NEW, P4 status page)

Reference
  ├ Verbs                                   reference/verbs.md  (NEW from assistant 356–429, 664–860; generated from VerbSchemas)
  ├ Command line                            reference/cli.md  (NEW: --mcp --rest --verify --unpack --replay-compare, exit codes)
  ├ Log format                              log-format.md  (keep; + "Is the log whole?" from assistant 431–476, fixed)
  ├ Format specification                    format-spec.md  (fix: c19–c24)
  ├ Evidence bundle format                  evidence-bundles/reference.md  (fix count)
  ├ What does auditing cost?                audit-cost.md  (re-measure; link from producing-a-log)
  └ Glossary                                reference/glossary.md  (NEW: posture, profile, walk, reel, bundle, hosted bridge…)

FAQ                                         faq.md  (slim: security answers move to admin/security, FAQ links)
Release notes                               release-notes.md  (keep; fix the double H1; consider stamping only released sections)
```

### 5.2 Where each existing page goes

| Page | Action | Destination |
|---|---|---|
| index.md | Rewrite | Home (four doors) |
| the-loop.md | Merge | build/index.md (thesis), with a short "the canvas" section in Diagnose ▸ With your AI client |
| spring-authoring.md, spring-authoring-getting-started.md, spring-authoring-conversations.md | Keep, fix | Build |
| connect-an-llm.md | Rewrite | Diagnose ▸ Connect; troubleshooting splits out |
| sample-conversations.md | Keep, regenerate | Diagnose |
| support.md | Rewrite | diagnose/index.md (the incident path); delete as a separate page |
| ai-and-runbooks.md | Keep, fix | Diagnose |
| guided-start.md, tutorial-playground.md, install.md | Keep, fix | Get started |
| getting-started.md | Split | Tour section → start/tour-and-sample.md; Settings → admin/settings.md; remainder → "open your own log" |
| user-guide/index.md | Delete | Replaced by section landing pages |
| start-page.md | Keep | Packs section moves to start/prompt-packs.md |
| sample-prompt-pack.md | Merge | start/prompt-packs.md (keep the file as a download asset) |
| records-and-filtering, graphs, topology, source-navigation, reports, project-panel | Keep, fix | Diagnose in the analyser ▸ The canvas |
| **assistant.md** | **Split into 11 and delete** | Per the section map (§1.3) |
| projects.md, portable-context.md, sharing-setups.md | Merge and rewrite | admin "Projects, profiles and sharing" (keep portable-context as a section or child if length demands) |
| plugins.md | Keep | Admin |
| evidence-bundles/* | Keep, fix | demo.md merges into the bundles index; reference.md goes to Reference |
| journeys/* | Keep, regenerate | Diagnose |
| log-format, format-spec, audit-cost | Keep, fix | Reference |
| producing-a-log, composing-a-system, integrating-a-vendor-component | Keep (fix producing) | Build |
| faq.md | Slim | Security answers move to admin/security.md |
| release-notes.md | Keep | — |
| assets/README.md | Rewrite | Inventory generated from the capture scripts |

### 5.3 The "Start here" page outline

1. **One paragraph:** what this is. A desktop canvas for Fluxtion audit evidence that you, or your AI, drive. It never runs your code unless you turn that on.
2. **Pick your door.** Four cards, each with three steps and one link:
   - **I have an incident and use Claude Code or Codex** → Install → Connect your client → Your first diagnosis session.
   - **I want to ask inside the analyser** → Install → Assistant profiles (Safe analysis is the default) → Ask the assistant.
   - **I'm building a Fluxtion app with Claude** → Install → Set up a project with Claude → Validate the intent with evidence.
   - **I'm looking around / running a demo** → Install → the start page's *Run me through a demo* → Running a demo.
   - Plus a fifth line for administrators → Security model and limits, Settings reference.
3. **What you need:** Java 21 / JBang; optionally Claude Code logged in, a Codex install, or an API key. Each AI is off by default, and "Off" is a full product.
4. **The start page in one picture** (generated), with its numbered entries mapped to the doors above.
5. **Three things that are always true:**
   - nothing is sent anywhere until you choose an assistant
   - every change the AI makes on the canvas is visible and reversible
   - files are written only with file exchange on, and with your approval in Safe analysis
6. **Where help lives:** this site (authoritative), Help ▸ User guide (the in-app shortcut into this site), prompt packs (questions to start from), lessons (concepts, when they land).
7. **Rename note** (once the rename PR lands): "Formerly *Fluxtion Audit Log Analyser*."

### 5.4 The rename to Telamin Canvas / Telamin AI: where the docs are touched

- **Prose:** about 600 case-insensitive "analyser" hits across the site; about 200 in the user guide, 124 of them in assistant.md. "Fluxtion Audit Log Analyser" appears 3 times: index:1, guided-start:29, assistant.md:656. "Analyser assistant" appears in the assistant.md title, the nav label, records-and-filtering:16, topology:317 and two links in connect-an-llm.
- **Config:** mkdocs.yml `site_name`, `site_description`, nav labels "Connecting an LLM to the analyser" and "Playground to analyser…".
- **Code strings that appear in docs and shots:** the tab and window title "Analyser assistant" (`MainFrame.java:4575`, `AssistantPanel.java:681`), Help ▸ About, S6's "What can the Fluxtion Audit Log Analyser do for me?" (`AskCatalogue.properties:66`).
- **Identifiers needing a separate decision, and probably unchanged:**
  - the `analyser` executable and JBang alias
  - the `analyser_*` tool names
  - the `fluxtion-analyser` MCP label
  - `~/.fluxtion-analyser/`
  - the jar name
  - the repo URL
  - the manifest `"analyser"` field
- **Suggestion:** do the IA rewrite first, using "the analyser" sparingly in new pages. Then the rename touches about a third as many places. MkDocs has no variables without a plugin; a `pymdownx.snippets` include for the product name in the few headline places is enough.

### 5.5 Docs site, in-app help, prompt packs and lessons: one source each

| Store | Owns | Must not | Relationship |
|---|---|---|---|
| **Docs site** | All task and reference prose, screenshots, security model | — | **Single source of truth.** Every other store links into it by stable anchors. |
| **HelpPanel** (`help.html`) | Nothing of its own: a launcher | Restate the site (today it is a stale, hand-written copy) | Replace with a short page of deep links into the site, ordered like the nav and offline-safe (the URL is shown). Keep a few-line offline fallback (what an audit log is, where Settings are). A static check (rule 8) that every `openDocsPage(...)` target and help link resolves against `mkdocs build` output. If M75 P4 lands, Help renders that same link page. |
| **Prompt packs** (built-in S1–S6, sample pack, philosophy packs) | *Questions* a person sends | Explain anything (the format forbids links anyway) | The site hosts the pack files as downloads, on Get started ▸ Prompt packs and Demo and teach ▸ Philosophy packs, with one paragraph per pack. Pack answers ground themselves in the site (the packs' SITE citation code), so the site's accuracy is the packs' accuracy. S6 already points to the docs URL. |
| **M75 lessons** | Concept teaching with live diagrams, bundled and reviewed | Duplicate task docs; restate the site's procedures | The site has one index page (Lessons) listing each bundled lesson's title and a two-line summary, and saying "open it with Help ▸ Learn". It never copies lesson text. Lessons link out to site pages for procedures. If OD-75.7 adds a pack `read` entry, packs → lessons → site is the one-way chain. |

Rule of thumb: **site = how and what; packs = what to ask; lessons = why (concepts); Help = the door.** Wherever a fact must appear in two stores, one of them links instead.

---

## 6. Effort estimate

The estimate is for one experienced docs or agent author working against the code, including review against code. Days are focused working days.

| Work | Effort |
|---|---|
| **Fix in M74 list (§4)** after the WIP lands, plus re-running `capture-docs.py` for `start-page` / `ai-menu` and fixing `tutorial-source` | **1–1.5 d** |
| New capture class for M74 surfaces (`M74DocCapture.java` with fixture replay: overlay, panel in two modes, transcript, permission, propose, Settings ▸ Assistant, Ask popup) and wiring into capture-docs.py; regenerating the journey, Spring, tutorial and conv sets | **2–3 d** |
| Split assistant.md into about 11 pages and delete it (mostly moving and fixing, with some new prose) | 2 d |
| New task pages: Start here, Diagnose index, first session, hand-off, REST automation, troubleshooting, Build index (thesis), set up with Claude, validate intent, tour and sample, templates and examples, Running a demo | 4–5 d |
| Admin: security model one-pager (B11; needs owner review), profiles, external access, settings reference, data, managed-policy stub | 2.5–3 d |
| Reference: generated verb page from `VerbSchemas` (with a test that keeps it in sync), CLI page, glossary | 1.5 d |
| Merges and fixes on the remaining ~20 pages (projects / portable-context / sharing; getting-started split; FAQ slim; evidence-bundles; format-spec; producing-a-log; audit-cost re-measure) | 3 d |
| HelpPanel to a link page, plus the link-resolution check; assets/README regenerated | 1 d |
| Nav, redirects for moved URLs (the in-app `openDocsPage` targets `ai-and-runbooks/`, `user-guide/start-page/#prompt-packs` and `evidence-bundles/opening/` must keep resolving, or the app must change in the same PR), `mkdocs build --strict`, sweep | 0.5–1 d |
| **Total** | **≈ 18–21 days**, of which M74-blocking ≈ 1–1.5 d (plus 2–3 d if the owner wants M74 screenshots before release, as the tracker's acceptance says) |

Suggested order:
1. The M74 fix list and screenshots.
2. Split assistant.md, and add Start here, the Diagnose section and the Security one-pager. These are the commercial and pre-customer items.
3. Build and Admin.
4. Teach.
5. The rename PR after the IA, so it lands on the smaller, final page set.
