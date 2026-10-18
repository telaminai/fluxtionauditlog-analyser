# Claude Code in the analyser

If you have [Claude Code](https://docs.claude.com/en/docs/claude-code/setup) installed and logged in, the **Analyser assistant** tab
can host **one long-running Claude Code session** instead of the in-app assistant: you type into it here, and it drives
the analyser through the same verbs an external client uses. It is **off by default**.

## Turn it on

Choose **Safe analysis** in **Set up AI…** or the quick switch (the mode chip in the assistant's header), or *Settings ▸ Assistant
▸ Safe analysis ▸ Allow the analyser to start Claude Code*. One assistant drives the tab at a time; switching back stops
an idle Claude Code, and is refused while Claude is answering (*"Claude is still answering — Stop or wait, then change
how the assistant works."*). These settings — both switches, Full Claude Code's mode, the model, the spend limit and the
folders you have confirmed — are **this machine's and this user's only**: never in a project profile, a shared settings
file or an evidence bundle, so a cloned repository or a received bundle cannot turn any of them on. There is no central
administrator lock yet ([Managed policy](../admin/managed-policy.md)).

## Start

Press **Send** with a question in the composer, or **⋯ ▸ Start Claude Code** (*Resume Claude Code* when there is a
conversation to resume). What happens:

- The analyser runs **your own installed `claude`**, logged in as you. It never reads, stores or passes on your Claude
  Code credentials; if Claude Code is not logged in, Start says *"Claude Code is not logged in — run `claude` once in a
  terminal to log in."* If `claude` cannot be found — common when the analyser was started from the Finder or the
  Dock — the tab lists every place it looked.
- The first time in a folder, it asks: *"Claude Code will read this folder's CLAUDE.md as instructions. Trust <folder>
  for Claude Code in the analyser?"* — **Trust this folder** or **Don't trust** (the default, at the right, as in every
  consent dialog here). Your answer is remembered on this machine. With no project open — or with the sample project or
  a bundle open — Claude Code works in a private, empty scratch folder instead, and the question says so in words before
  its path. Claude Code is never started inside a bundle's working copy: with a bundle open it works in an empty
  scratch folder, and the working copy's `CLAUDE.md` is never loaded.
- Claude Code does **not** need *External MCP/REST access* turned on. With it off, the analyser opens a private listener that
  serves only this Claude Code session — it refuses every external client, even one holding the REST token, and
  publishes no endpoint file for other tools to find — and closes it when the session stops. With it on, the session
  shares that listener, and external clients are served as before — unless you choose **Close external access while
  this session runs** ([Connect your client ▸ While Claude Code runs in the analyser](../connect-an-llm.md#while-claude-code-runs-in-the-analyser)).
- Start **contacts Anthropic** (the sign-in check and connecting the analyser's bridge) **before your first Send**. No
  record content is sent until you press Send.
- The analyser checks that the session it started is the one it asked for: the bridge proves it belongs to this
  analyser window, and Claude Code's tools, permission mode and servers must be exactly what Safe analysis (or Full
  Claude Code) sets up, or the session is stopped and the tab names what differed. An older installed analyser bridge is refused by name.

## What it can do in Safe analysis

Claude Code here has:

- the folder's **`CLAUDE.md`**, and **Read, Grep and Glob confined to the folder** (a symbolic link pointing outside it
  is refused too);
- the analyser's **verbs that only read or change the view** — `read`, `aggregate`, `series`, `context`, `coverage`, `source`, `filter`,
  `goto`, `flag`, `topology`, `spotlight` — approved without asking; **`graph`**, judged call by call: creating a chart
  or showing one is approved without asking, while deleting, renaming or restoring a chart, or replacing an existing
  chart's definition (re-sent notes, series, guides or bands; `clearNotes`) is **asked of you** — the dialog says
  *"Claude Code asks to delete the chart “x” (it can be restored)"* or *"…to replace the chart “x” (its old definition
  can be restored)"*; and
  **`propose`**, which only Claude Code in the tab is offered: it records a set-up offer and changes nothing ([Set up a project with Claude](../build/set-up-with-claude.md));
- the verbs that change what is open or write files — `open`, `source_root`, `screenshot`, `report`, `walk`, `import` —
  **each call asked of you first** ([Approvals](approvals.md)), except the few
  sub-actions of them that change nothing stored (playing or ending a walk, listing the deleted reports or walks);
- nothing else. **Not loaded:** your own skills, commands, hooks and settings, your MCP servers and connectors, and the
  folder's `.claude/settings.json` hooks and `.mcp.json`. **No Bash, no file edits, no web, no subagents.** Anything
  else is denied at once, without asking you. The mode chip reads *Safe ▾*, and *⋯ ▸ About this session…* says *"Safe analysis — your
  own skills, hooks and MCP servers are not loaded"*. Your full Claude Code is a separate opt-in, below.

## Full Claude Code: your own Claude Code, by a second opt-in

**◆ Full Claude Code** (in Set up AI or the quick switch), or *Settings ▸
Assistant ▸ Full Claude Code ▸ …with my full Claude Code permissions* (off, and only available while Claude Code is
allowed), runs your own Claude Code in the tab as it runs in a terminal. Set up AI shows the warning's first three sentences;
choosing it there, in the quick switch, or with the Settings switch shows the same warning as a confirmation first, with
*Cancel* as its default — Enter and Escape cancel, and nothing changes until you press *Turn on full Claude Code* in it. It loads **your settings and permission rules, skills, commands, plugins,
subagents, hooks and MCP servers** (claude.ai connectors included), and the folder's own hooks and `.mcp.json`, and
Claude Code can use **your own tools — Bash, file edits, web access — as far as your permission rules and the mode you
choose allow**. **The analyser does not confine it to the folder**; your rules and the mode do.

- **The mode** (shown under the switch when it is on): `default` asks before anything your rules don't allow — the
  analyser shows each request in its approval dialog ([Approvals](approvals.md)); `acceptEdits` edits files in the folder without asking,
  otherwise as `default`; `auto` lets Claude Code's own safety classifier approve or block each action without asking you — it needs
  a model that supports it (sonnet or opus): with another, Claude Code starts in `default` instead, and the analyser
  stops it and says so; `plan` reads and plans, and edits and runs nothing. `bypassPermissions` and `dontAsk` are
  never offered; a hand-edited setting naming either reads as `default`, and Settings says so.
- **The folder is confirmed separately for full Claude Code**, because Start itself **runs the folder's hooks and
  connects its MCP servers**, before you send anything. The dialog names what it found — *"Hooks in this folder:
  SessionStart (1) — from .claude/settings.json"*, *"MCP servers in this folder: db-tools — from .mcp.json"*, the
  permissions the folder grants itself (*"allow Bash"*, an additional directory, a default mode), or that a
  file could not be read — and never shows the hook or server commands. What an MCP server's command runs (a
  `./server.js`) is not checked, and the dialog says so. The confirmation is tied to the content of
  `.claude/settings.json`, `.claude/settings.local.json` and `.mcp.json`, and of the folder's **instruction files**
  (`CLAUDE.md`, `CLAUDE.local.md`, `.claude/CLAUDE.md`, and everything under `.claude/commands`, `.claude/skills`,
  `.claude/agents`, `.claude/rules`, `.claude/output-styles` and `.claude/hooks`, read up to a bound and without
  following a link out of the folder): **when any of them changes** (a `git pull` that adds a hook or rewrites
  `CLAUDE.md`), **Start asks again**, and the dialog says which changed; the check is made again when the process is
  started, so a change between the question and the start is refused too. A session already running when they change
  is stopped at once at your next Send or Settings change — mid-answer too — and Start asks again.
  **Not re-checked:** files a `CLAUDE.md` imports with `@path`, `CLAUDE.md` files in the folders above this one, and
  the contents of the scripts a hook runs from outside `.claude/hooks` — Claude Code reads or runs them, and a change to
  them does not ask again; the dialog says so. A folder confirmed for the analyser assistant is not thereby confirmed
  for full Claude Code.
- **What the tab shows.** Each hook's event and outcome is a status line (*"SessionStart: 1 ran"*, or that one
  failed), never its output. The header says *"Full Claude Code (<mode>) — your settings, skills, hooks and n MCP
  servers are loaded"*, and an environment line under **Details**, *"PATH: inherited, plus ~/.local/bin, /opt/homebrew/bin"*: an analyser
  started from the Finder or the Dock has a minimal `PATH`, where your hooks and `npx` servers would not start, so the
  standard install folders that exist and are not already on your `PATH` are **appended** (your order wins). The
  analyser never runs a login shell to find a `PATH`; set anything else (an `nvm` node, a project's virtual
  environment) in Claude Code's own settings `env`.
- **Still the analyser's.** Its verbs that change what is open or write files are asked of you in the modes that ask
  (`default`, `acceptEdits`) and hidden under `auto` and `plan`; the tools of your own `fluxtion-analyser` registration,
  which is not bound to this analyser window, are hidden. Only the Start button starts it. Changing the switch or the
  mode means a New chat.

Turn it on only for folders and logs you trust: in Full Claude Code, text in a record that is written to steer an AI
reaches an agent that may have your shell, and the analyser cannot limit what your own tools do.

## What the analyser tells Claude Code

Every session starts with the analyser's own note, fixed text with nothing of yours in it: Claude Code is the
assistant inside this app; *project*, *starter* (or *template*), *log*, *graph* and *report* mean the analyser's; and
it calls `analyser_context` first, which says what is open and lists the starters. **Full Claude Code** adds: begin a
new application by offering one of the analyser's starters (never a web search or a hand-made scaffold), create
nothing in the scratch folder a session starts in, and use no `gh`, `curl` or other network command to look for
examples. **Full may read fluxtion-playground.dev documentation without asking**: one `WebFetch` rule for exactly that
site, over https; any other web fetch is asked, and Safe analysis has no web tool at all. *⋯ ▸ About this session…*
says the same. The note is part of what a session is created with, so a conversation from before this note is not
resumed: it begins a new chat.

When Claude Code proposes a starter, the review and what follows are in
[Create from a starter with the assistant](../user-guide/projects.md#create-from-a-starter-with-the-assistant).

## Using it

- **Send** (or Enter; Shift+Enter is a new line) sends one turn. While Claude is answering, Send is refused — *"Claude is still answering — Stop
  or wait."* — and your draft stays; nothing is queued.
- **Stop** interrupts the turn. If Claude Code does not acknowledge within 5 s, or the turn has not ended within 15 s,
  the analyser ends the process; your next Send resumes the same conversation in a new process.
- **New chat** starts a new conversation. Changing the folder (opening another project), the profile or Full Claude
  Code's mode means a New chat: a conversation is never resumed under different settings.
- **Changing a setting while Claude Code runs.** Claude Code takes its settings when it starts, so a change made in
  Settings ▸ Assistant, Set up AI or the quick switch applies at the next start. What happens to the session running now
  depends on the change:

    | Change | What happens |
    |---|---|
    | *Safe analysis* → *Full Claude Code*; a full mode that asks **less** (for example `default` → `acceptEdits`); the model; the spend limit; the folder | **Offered.** The panel asks *"Settings changed — restart Claude Code now to apply them?"* with **Restart** and **Later**. Nothing stops until you answer. **Yes** stops Claude Code and starts it again on the new settings — the conversation continues when only the model or the spend limit changed, and a **new chat** begins when the profile, the mode or the folder changed (the prompt says which); while Claude is answering, it restarts when that answer ends. **Later** keeps the session running on the settings it was started with and leaves a **settings changed — restart to apply** chip in the header — click it to be asked again. Putting the setting back withdraws the offer. |
    | *Allow the analyser to start Claude Code* off (or the *In-app assistant* or *Off* profile); Full Claude Code switched **off**; a full mode that asks **more** (for example `auto` → `default`, or any mode → `plan`); the folder no longer confirmed for the profile running | **Stopped at once**, mid-answer too, and never offered: each takes away something the running session can still do. The transcript says why. |
    | External MCP/REST access, file exchange, creating projects, the in-app assistant's settings | Nothing to restart: Claude Code keeps its own connection to the analyser. |

  After you choose **Keep it running here** when switching projects, the folder change is yours: it is not offered
  again. Turning *Allow the analyser to start Claude Code* back on after it stopped a session clears the "switched off"
  line — press Start.
- **Slash commands** you type are sent as in a terminal — `/compact`, `/model`, `/context`, `/usage` and the like —
  except those that would change how Claude Code works here or the conversation's identity, or act outside this session: `/config`,
  `/mcp` with an argument, `/add-dir`, `/permissions` and others are refused with *"… is not available in the analyser —
  use Claude Code in a terminal for that."*; `/clear` offers New chat instead. A command the analyser has not
  classified (from a newer Claude Code) is refused the same way. Lines starting with `!` or `#` (the terminal's shell and
  memory shortcuts) are refused. An `@file` mention works as in a terminal for a file inside the folder; a file
  outside it is not read.
- **The header** shows the model, the profile (*Safe analysis*, *Full Claude Code (<mode>)*), the folder (shortened; the full path is its tooltip),
  your subscription type or the API-key source, and the API provider when it is not Anthropic's own. The operator lines —
  the Claude Code CLI version, where requests go, the `PATH` line, diagnostics and the last error line — sit behind
  **▸ Details**, collapsed by default (Tab reaches it; Space or Enter opens it). It opens by itself, reading
  *"▾ Details — needs attention: …"*, only when something needs you: a session that did not match how Claude Code was
  set up, a start that failed or was refused, or a warning (diagnostics, or an error line Claude Code left). Your own
  choice to open or close it is kept until the analyser closes, never saved. It never
  shows your e-mail address or organisation. When the environment the analyser passes to Claude Code sets
  `ANTHROPIC_BASE_URL`, a line says *"requests go to https://proxy.example:8443"* — the scheme, host and port only,
  never a path, query or credentials; with full Claude Code it adds that your own settings may set another endpoint,
  which the analyser does not read. A **diagnostics** line appears only when something needs it — *"Diagnostics: 3
  unknown stream events · 1 command not classified for the analyser (/foo)"*. Each finished answer shows its **time**,
  and with an API key its **cost** (the difference of the session's running totals); on a Claude plan no dollar figure
  is shown. A long conversation keeps its last 500 entries, with one line saying how many earlier ones are not shown. **Cost:** on a Claude plan
  it shows no dollar figure — *"usage counts against your Claude plan"*, whose own usage limits apply. With an API key
  it shows what the session has used, and, if you set one, the limit: *Settings ▸ Assistant ▸ Limit spend per session*
  is **off by default** and is meant for API-key billing; when the limit is reached the analyser stops Claude Code and
  says so. Changing the limit while a session runs offers a restart (*Restart* or *Later*); the restart resumes the
  conversation under the new limit.
- **Switching projects while Claude Code runs.** Opening, creating or closing a project from the menus while a session
  runs asks first: **Stop, and get Start ready in the new project** (the session ends; nothing starts until you press
  *Start a new session in this project*, and the new folder is confirmed then), **Keep it running here** (the switch
  happens; the session keeps working in its own folder, and its context describes the previous project), or
  **Cancel**. An assistant's `open {project}` is never held this way. (A handoff report from the old session to the new
  one is planned for later.)
- **Don't resume this conversation in a terminal while it is open here** — the history would fork. A second analyser
  window cannot open it either: *"This conversation is open in another analyser window."*
