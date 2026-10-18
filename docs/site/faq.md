# FAQ

## How big a log can it open?

Large ones. Files above the **memory threshold** (Settings ▸ Performance & S3, default 500 MB) are
memory-mapped rather than loaded into heap, so multi-GB logs work. Set the threshold to `0` to always
memory-map.

## Where are my settings stored?

In `~/.fluxtion-analyser/config` — cleartext properties (this is a local, single-user tool). It holds
your source roots, event processors, saved graphs, hidden columns and your LLM API key. The distinct
Fluxtion processor-build key uses the builder's established `~/.fluxtion/fluxtion.apiKeyFile` instead.

## Why does the Graph panel say “Charts not loaded”?

Older versions could save two charts with the same name. The analyser cannot tell which definition a
name refers to, so it withholds that chart set and explains the conflict on the Graph panel. Log
inspection still works. Both definitions remain saved; the analyser does not rename or discard either.

The message identifies the settings file. Close the analyser before editing global settings, or close
the project before editing its profile, so autosave cannot overwrite the correction. Give each chart a
unique `graph.N.name` there, keeping its other settings, then restart or reopen the project. You can also open a different,
valid project while leaving the original definitions intact. An ambiguous project or imported chart set
is refused before it replaces the current project.

## Is my API key safe? Does it leave my machine?

There are two unrelated keys. The **LLM key** is stored locally and used only to call the provider you
configured. The **Fluxtion build key**, managed from **AI ▸ Fluxtion API key…**, is written directly to
the builder's canonical file and is used only when you regenerate a processor. Neither value is included
in a project or exported settings file. The analyser reports build-key presence but never validates or
redisplays it. See [Sharing setups](user-guide/sharing-setups.md).

## Does it work offline?

Yes. Everything except the optional assistant is local. **No assistant profile is local-only — Safe analysis
included:** what you ask, and the evidence Claude reads through the analyser, is sent to the model provider
(Anthropic, on your Claude plan or API key), as in any Claude Code session; with the in-app assistant, what you ask
and the evidence it reads are sent to the model provider you chose, on your API key. Opening from S3 uses your local `aws` CLI. The
in-app assistant needs network only when you actually send it a question (and even then, no key =
copy-prompt mode). Claude Code in the Assistant tab contacts Anthropic when you press **Start** (its sign-in
check and the bridge connection), before your first Send; with the assistant **Off**, nothing in the analyser
goes on the network.

## What does Claude Code itself send to Anthropic?

Besides your question and the evidence Claude reads, Claude Code has usage data of its own, the same in the
analyser's panel as in your terminal. The *Where data goes* picture in **Set up AI** does not draw it; it links
here. Anthropic's [Claude Code data usage](https://code.claude.com/docs/en/data-usage) page is the authority. In
short, as that page stated it in October 2026:

- **Metrics**: latency, reliability and usage patterns, never your code, prompts or file paths. `DISABLE_TELEMETRY=1`
  turns them off.
- **Error reports**: Claude Code's own error messages and stack traces, with known secrets, paths and e-mail
  addresses redacted first; on for Pro and Max sign-ins. `DISABLE_ERROR_REPORTING=1` turns them off.
- **Feedback you choose to send**: `/feedback` (and `/bug`, `/share`) sends a copy of the conversation; a session
  survey's follow-up uploads the transcript only if you answer **Yes**.
- `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` turns all of these off at once. On Amazon Bedrock, Google Cloud and
  Microsoft Foundry, metrics, error reports and feedback reports are off by default.

The analyser turns none of these on or off. It runs your own installed `claude`, and the environment variables the
analyser itself was started with reach it. Full Claude Code also reads your Claude Code settings, as in your
terminal; Safe analysis does not load them. How long Anthropic keeps your conversations, and whether they train
models, depends on your account; the same page says so for each plan.

## Can I follow a growing log?

Yes — **Audit log ▸ Follow (tail)** polls an open local file and appends newly-completed records live,
preserving flags and filters; open graphs re-extract as records arrive (a chart showing the whole log grows with
it, one at the live edge slides, one zoomed into the middle holds). (Heap-loaded local files only; not S3.)

For an ordinary open, the final EOF record is included even without a closing `---` separator,
as Format 1 permits. Status, Project and `context.log.tailNote` disclose that completeness is unknown.
Starting Follow reopens that snapshot as a live read (clearing record-bound flags and selection through
the normal reload boundary). Its trailing record is then **pending** until a complete separator arrives.
Status and `context.log.trailingRecordsPending` disclose it; live-read counts
exclude it. Pausing the writer does not complete a record. After the separator and its newline arrive,
the record appears with all fields written before the separator. Do not add a separator to a live export
merely to make it appear sooner.

## Why is a value in a record not graphable?

Only **top-level** numeric or boolean nodeLog keys can be plotted. A number sitting inside a `toString()`
(like `price=` inside `MutableOrder(price=…)`) is text within one value, not its own key. See
[Log format](log-format.md).

## Is the assistant's action socket safe to enable?

Yes — it's built to be. *External MCP/REST access* (*Settings ▸ Assistant*, or *AI ▸ External MCP/REST access*),
which lets an external agent drive the analyser, is **off by default**, and when on it is:

- **loopback-only** — bound to `127.0.0.1`; not reachable from the network,
- **token-guarded** — every request needs a **per-run** secret token (regenerated each launch),
- **CSRF-hardened** — requests carrying an `Origin` header are rejected, so a browser page can't reach it,
- **rate-limited** and **bounded** — capped actions per reply; no shell access, ever.

Out of the box an agent can compute, filter, graph, flag and read records, and `source` can reread the XML
and Java files **under the source roots you configured** (to show a declaration) — **no other file reads, and
no file writes**. These groups of verbs go further, honestly labelled:

- **File exchange — `screenshot` and `report` writes, and external-series reads — is off by
  default.** Enabling *Allow assistant file exchange* (Settings ▸ Assistant) lets the write verbs write
  **only inside the exchange directory you choose** (never overwriting an existing file), and lets the
  `graph` verb's `external` series **read only from that same directory** — one opt-in, one directory,
  both directions. A file you pick in a chooser yourself is readable for that session: the chooser is
  the grant. `walk {reel}`, which records a walk as one self-contained HTML page, is the same kind of
  write, and so is `report {bundle}`, which captures an evidence bundle. `screenshot` and `report` are marked
  destructive to MCP clients so your agent asks first.
- **Scripting verbs — `open` and `source_root` — can change which log, event processor or source roots
  are open**, the same things you change through the UI. Still nothing outside the analyser: no shell,
  no arbitrary file reads, and your API key is never reachable. A source root is never your home folder (or a
  folder containing it) or a credential folder — `~/.ssh`, `~/.aws`, `~/.config`, `~/.gnupg`, or inside one, and `~/.m2`
  itself (`~/.m2/repository` stays allowed) — for any caller, links resolved; a project profile, a shared settings file or a set-up proposal cannot carry one in either.
  An assistant (the in-app one or Claude Code in the tab) is held to the open project: the in-app assistant is refused
  a folder outside it (add that folder yourself in *Settings ▸ Sources*), and Claude Code's approval dialog says
  *"outside this project"*.
- **Creating a project — `open {project, create}` — is off by default for an assistant.** Enabling
  *Allow the assistant to create projects* (Settings ▸ Assistant) lets an agent, over MCP/REST or the in-app
  assistant, create a project in a directory it names; you using *Project ▸ New project…* are never gated by it.
  The switch is **this machine's only**: it is never in a project profile, a shared settings file or an
  evidence bundle, so a cloned repository or an imported share cannot turn it on. The **preview**,
  `create: true`, writes nothing; it reads the chosen directory as *New project…* does — skills to depth 7,
  GraphML to depth 12, each file at most 32 MB, all under the chosen directory. An **apply** writes exactly
  these, and never over an existing project: (1) `<dir>/.analyser/` (only if absent) and
  `<dir>/.analyser/project.fluxtion-settings`, created new; (2) `<dir>/CLAUDE.md`, only if the reference guide
  was chosen and offered, never over an existing one; (3) the outgoing project's pending edits, to its own
  profile, as any project switch does; (4) this machine's config — the active project and the recent-projects
  list, so **the created project is the one activated at the next start**; (5) the session-recovery journal
  entry for the new session. An assistant is also refused inside an evidence bundle's working copy, inside the
  exchange directory, inside an existing project, and through a symbolic-link `.analyser`.
- **`walk` edits the project's saved spotlight walks** — it can save one, replace one by name, rename one or
  move one to the recently-deleted list (restorable). Its one file write is **`walk {reel}`**, which writes a
  walk as an HTML page — only with file exchange on, only inside the exchange directory, never overwriting. It
  reads no log data beyond the records a walk points at; it is marked destructive to MCP clients because
  replacing a walk by name overwrites a person's work.
- **`import` borrows an evidence bundle's saved state into the project you already have** — its charts
  and named focuses, its investigation reports and spotlight walks, and your column choices. Called with
  only a bundle path it previews and changes nothing; it applies only the categories you name, which is
  the verb's equivalent of ticking the consent boxes in Project ▸ Import settings. A bundle's source
  roots and Maven repos are removed at capture, so it can never point your analyser somewhere new.
  Reports carry prose the sender wrote about their data, so they are applied only when named explicitly.
  It is marked destructive to MCP clients because it merges someone else's work into your project.
- **`propose` is offered only to Claude Code in the Assistant tab** — never on `/manifest`, never to an external
  MCP or REST client and never to the in-app assistant. It records an offer of up to ten set-up items (source
  roots, the processor, a GraphML, runbooks, a log) and **changes nothing**; you review each item, every answer
  starts at No, an item outside the chosen folder can never be applied, and only *Apply chosen* acts.
- **No assistant profile is local-only, Safe analysis included.** What you ask, and the evidence Claude reads
  through the analyser, is sent to the model provider (Anthropic, on your Claude plan or API key), as in any Claude
  Code session. The in-app assistant sends what you ask, and the evidence it reads, to the model provider you chose,
  on your API key. *Safe* describes what Claude Code may do on this machine, not where your words go.
- **Claude Code in the Assistant tab is off by default.** **Set up AI…** (in the assistant header's **Assistant: … ▾**
  control, the AI menu and the start page) turns it on — **Safe analysis** for what is described below, **Full Claude
  Code** for yours — after checking that Claude Code is installed, and the **Assistant: … ▾** quick switch changes it
  later in one step; both write the same switches.
  *Allow the analyser to start Claude Code* (Settings ▸ Assistant) lets the tab's **Start** button run **your own installed `claude`**, logged in as you; the
  analyser never reads or stores your Claude Code credentials. Start contacts Anthropic (the sign-in check and the
  bridge connection) **before your first Send**; no record content is sent until you press Send. By default Claude
  Code runs as **Safe analysis**: the folder's `CLAUDE.md`, **Read, Grep and Glob confined to the
  folder**, and the verbs above; **no Bash**, no file edits, no web, none of your skills, hooks, settings or MCP
  servers — so "no shell access" stays true there. The verbs that change what is open or write files (`open`,
  `source_root`, `screenshot`, `report`, `walk`, `import`) are visible to it, but **before Claude Code runs any of these
  verbs the analyser asks you**, in a dialog that says what this call does and shows its exact input. **Deny is the
  default** (Enter, Escape and closing the dialog deny; unanswered for 120 s, it is denied), and **an approval covers one
  call**: the analyser itself refuses the call unless your *Allow once* for exactly that call — that verb, that input,
  that turn — is waiting, used at most once within 60 s. Three refinements keep the questions to the ones that matter,
  without weakening that: a call that **changes nothing stored** — playing or ending a walk, listing the deleted reports
  or walks — is allowed without a dialog, the analyser judging the call's own input, and every other call of those verbs
  is still asked; a file write the settings would refuse (file exchange off) is **refused at once with the reason**,
  never asked and then refused; and a second request arriving while a dialog is open **waits** for it, and is denied if
  its turn ends or you press Stop first. There is no "allow for this turn". Each folder is
  confirmed once before Start, a bundle's working copy is refused, and its bridge carries its own per-session
  credential: a call with only the REST token is never treated as Claude Code's, Claude Code cannot delete a chart, and
  only the verbs listed above are served to it — a verb added in a later release stays refused until it is listed.
  Claude Code does not need External MCP/REST access on: with it off, its private listener refuses every external client,
  even one holding the REST token, and publishes no endpoint file. Start is always the explicit button; opening the
  Assistant tab starts nothing. The switches and the confirmed folders are this machine's only — never in a project profile, a shared
  settings file or an evidence bundle.

  **Every switch is per user.** *Allow the analyser to start Claude Code*, Full Claude Code, External MCP/REST access,
  file exchange and project creation live in each user's own `~/.fluxtion-analyser/config`, and any user can turn them
  on for themselves. **There is no central administrator lock yet**: an organisation cannot today forbid Full Claude
  Code for everyone from one place. A managed policy that can (a machine-wide file the analyser reads, showing *Disabled by
  your administrator*) is planned — P4 in the tracker — and not built.

  **Full Claude Code is a separate switch with its own warning** (*…with my full Claude Code permissions*, off; the
  **Full Claude Code** choice in Set up AI and the quick switch's *◆ Full Claude Code…* ask for the same
  confirmation as the switch — the warning again, *Cancel* the default — before anything changes). It
  runs your own Claude Code as in a terminal, with **your settings, skills, hooks and MCP servers** (claude.ai
  connectors included), and **the folder's hooks and MCP servers run when you press Start**, before you send anything, so
  the folder is confirmed for full Claude Code separately — the dialog names the hooks and servers it found — and the
  analyser **asks again if those files change** (`.claude/settings.json`, `.claude/settings.local.json`, `.mcp.json`,
  and the instruction files: `CLAUDE.md`, `.claude/CLAUDE.md`, `.claude/commands`, `.claude/skills`, `.claude/agents`,
  `.claude/rules`, `.claude/output-styles`, `.claude/hooks`), checked again when the process starts; a session already
  running when they change is stopped, and Start asks again. Files a `CLAUDE.md` imports, `CLAUDE.md` files in the
  folders above, and the contents of hook scripts kept outside `.claude/hooks` are **not** re-checked, and the dialog
  says so.
  Claude Code can then use **your own tools, including Bash**, file edits and web access, **as far as your permission
  rules and the mode you chose allow** (`default`, `acceptEdits`, `auto` or `plan`; **bypassPermissions is never
  offered**, nor `dontAsk`) — **the analyser does not confine it to the folder**. In Claude Code's own `default` and
  `acceptEdits` modes, whatever it would ask you in a terminal it asks in the analyser's dialog — Bash, file edits, web
  access, your MCP servers' tools and the analyser's own verbs above — one at a time, Deny the default; under `auto` and
  `plan` the analyser's verbs that change what is open stay hidden. **A call your own rules or hooks approve is not
  asked in the analyser** (nor one Claude Code's `auto` classifier allows): for the analyser's own verbs the analyser
  still refuses it without your approval in its dialog, but for your own tools nothing in the analyser can. Your own
  `fluxtion-analyser` registration is hidden, and only the Start button starts full Claude Code. This is the stated exception to "no shell
  access": record text that steers an AI reaches an agent with your shell, under your rules, so turn it on only for
  folders and logs you trust. **Full Claude Code cannot stop your own tools reading the analyser's REST token**
  (it lives in a file in your home folder, as for any external client); with it, Claude Code could drive the analyser
  as an external client, including the destructive verbs above. The per-session file Claude Code's own bridge reads
  carries only that session's credential, not the token. Agent edits then happen as your own Claude Code makes them, within your rules.
  Claude Code's own question tool (`AskUserQuestion`) is denied without a dialog: Claude is told to ask in its reply,
  and you answer in the conversation — an approval dialog is never used for a question.
- **Log-source plugins are jars you install yourself, and installing a jar is arbitrary code
  execution.** Nothing is bundled or downloaded — without plugins this application is byte-identical
  to a plain build — and a plugin can only ever be a log *reader*: it cannot add verbs to the action
  socket, so the verb list above stays complete with or without plugins.

## How do I connect Codex, Claude Code or another MCP client?

With the analyser open, use **AI ▸ Set up AI…** and choose *Your own AI client connects* (it opens there when a
client is already set up): choose the client, turn on **External MCP/REST access** (confirmed first), register the
analyser with your client, and its last step says when a client actually reached this window. The same door is the
start page's AI line and *Settings ▸ Assistant ▸ Set up AI…*. The **Connect an AI client** dialog is behind that
screen's **More options…** (its only door since M77-4), for a project-scope command, removing a registration, Claude
Desktop's note and **Check connection**, which proves the local bridge reaches this live window.
The screen can explicitly register Codex or user-scoped Claude Code when their CLIs are available. For
Claude Desktop and any other client, **Generic MCP setup** supplies the exact no-token stdio JSON for you
to copy or save to a file you choose; it never guesses or edits a client configuration location. See
[Connecting an LLM to the analyser](connect-an-llm.md) for the full sequence.

## How do I check for updates?

Each release is on the [releases page](https://github.com/telaminai/fluxtionauditlog-analyser/releases/latest); the JBang alias and the download
link always point at the latest. The app shows a **what's new** note the first time you run a new version.

## Troubleshooting

| Symptom | What it means / fix |
|---|---|
| A **source root shows red** | The folder doesn't exist (or moved) on this machine. Fix the path in **Settings ▸ Source roots**; imported setups keep `~`-relative paths but a machine-specific root can still be missing. |
| **Clicking a node line doesn't open source** | That node's class isn't under a source root or in a searched Maven repo. Add the root, or add the `*-sources.jar` repo (**Settings ▸ Maven repos**). Check the right **Event processor** is selected. |
| **Rows tinted as parse errors** | The record didn't fully parse — but the file still loaded and the raw text is shown. The lenient parser never fails a whole file on one bad record. |
| **Open from S3 fails** | The `aws` CLI isn't installed or the profile/region is wrong. Install the AWS CLI and set **AWS profile / region** in **Settings ▸ Performance & S3**. |
| **LLM 401 / auth error** | The API key or provider/model is wrong in **Settings ▸ LLM**. No key? Use **Copy prompt** instead. |

## Keyboard shortcuts

| Key | Action |
|---|---|
| **F** | Flag / unflag the selected rows |
| **F3** / **Shift+F3** | Jump to the next / previous anomaly |
| **Alt+Left** (or ⌘/Ctrl+`[`) | Source view: back to the previous file |
| **Ctrl/⌘-click** | Source view: navigate to the identifier under the cursor |
| **Enter** (search box) | Remember the search term |

An assistant can use `open {follow: true}` after a local log finishes loading, or
`open {follow: false}` to stop. Use this parameter alone. Unsupported readers refuse;
`context.log.following` and `supportsFollow` mirror the Follow toolbar/menu state.
The person can stop Follow using either control.
