# FAQ

## How big a log can it open?

Large ones. Files above the **memory threshold** (Settings ▸ Performance & S3, default 500 MB) are
memory-mapped rather than loaded into heap, so multi-GB logs work. Set the threshold to `0` to always
memory-map.

## Where are my settings stored?

Your own settings are in `~/.fluxtion-analyser/config`, in cleartext (this is a single-user tool), including the
in-app assistant's API key. A project's settings are in its own `<project>/.analyser/project.fluxtion-settings`. The
Fluxtion build key is in `~/.fluxtion/fluxtion.apiKeyFile`. [What is stored where, what leaves](admin/data.md) lists
every file.

## Is my API key safe? Does it leave my machine?

There are two unrelated keys. The **LLM key** is stored locally, in cleartext in your config file, and used only to
call the provider you configured. The **Fluxtion build key**, managed from **AI ▸ Fluxtion API key…**, is written
directly to the builder's canonical file and is used only when you regenerate a processor. Neither value is included
in a project, an exported settings file or an evidence bundle. The analyser reports build-key presence but never
validates or redisplays it. See [What is stored where](admin/data.md).

## Does it work offline?

Yes. Everything except the optional assistant, and the buttons that fetch a template or an example, is local.
**No assistant profile is local-only — Safe analysis included:** what you ask, and the evidence an assistant reads, goes
to its model provider. Claude Code in the Analyser assistant tab contacts Anthropic when you press **Start** (its sign-in check
and the bridge connection), before your first Send. With the assistant **Off**, and External MCP/REST access off,
nothing in the analyser goes on the network unless you open from S3 or fetch a template or an example. [What leaves the machine](admin/data.md#what-leaves-the-machine) has the
full list.

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

## Is the assistant's action socket safe to enable?

*External MCP/REST access* is off by default. When on, it listens on `127.0.0.1` only, needs a per-run token, rejects
requests from web pages, and is rate-limited; it runs no shell. The verbs that replace what is open or write files are
marked destructive, so your client can ask you first, and file writes need their own switch. The
[security model](admin/security.md) is the full statement: what each assistant profile can do, what is asked, the
limits that are stated rather than hidden, and what an administrator cannot yet lock.

## Why does the Graph panel say “Charts not loaded”?

Older versions could save two charts with the same name. The analyser cannot tell which definition a
name refers to, so it withholds that chart set and explains the conflict on the Graph panel. Log
inspection still works. Both definitions remain saved; the analyser does not rename or discard either.

The message identifies the settings file. Close the analyser before editing global settings, or close
the project before editing its profile, so autosave cannot overwrite the correction. Give each chart a
unique `graph.N.name` there, keeping its other settings, then restart or reopen the project. You can also open a different,
valid project while leaving the original definitions intact. An ambiguous project or imported chart set
is refused before it replaces the current project.

## Can I follow a growing log?

Yes: **Audit log ▸ Follow (tail)** polls an open local file and appends newly completed records live, preserving
flags and filters; open charts re-extract as records arrive (a chart showing the whole log grows with it, one at the
live edge slides, one zoomed into the middle holds). It works for local text logs loaded into memory: not S3, a
memory-mapped file or a binary log.

For an ordinary open, the final EOF record is included even without a closing `---` separator,
as Format 1 permits. Status, Project and `context.log.tailNote` disclose that completeness is unknown.
Starting Follow reopens that snapshot as a live read (clearing record-bound flags and selection through
the normal reload boundary). Its trailing record is then **pending** until a complete separator arrives.
Status and `context.log.trailingRecordsPending` disclose it; live-read counts
exclude it. Pausing the writer does not complete a record. After the separator and its newline arrive,
the record appears with all fields written before the separator. Do not add a separator to a live export
merely to make it appear sooner.

An assistant can use `open {follow: true}` after a local log finishes loading, or `open {follow: false}` to stop,
each alone. Unsupported readers refuse; `context.log.following` and `supportsFollow` mirror the Follow toolbar and
menu state. You can stop Follow with either control.

## Why is a value in a record not graphable?

Only **top-level** numeric or boolean nodeLog keys can be plotted. A number sitting inside a `toString()`
(like `price=` inside `MutableOrder(price=…)`) is text within one value, not its own key. See
[Log format](log-format.md).

## How do I connect Codex, Claude Code or another MCP client?

With the analyser open, use **AI ▸ Set up AI…** and choose *Your own AI client connects* (it opens there when a
client is already set up): choose the client, turn on **External MCP/REST access** (confirmed first), register the
analyser with your client, and its last step says when a client actually reached this window. The same door is the
start page's AI line and *Settings ▸ Assistant ▸ Set up AI…*. The **Connect an AI client** dialog is behind that
screen's **More options…** (its only door since M77-4), for a project-scope command, removing a registration and Claude
Desktop's note. **Check connection**, on the screen itself, proves the local bridge reaches this live window.
The screen can explicitly register Codex or user-scoped Claude Code when their CLIs are available. For
Claude Desktop and any other client, **Generic MCP setup** supplies the exact no-token stdio JSON for you
to copy or save to a file you choose; it never guesses or edits a client configuration location. See
[Connect your client](connect-an-llm.md) for the full sequence.

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
| **Your MCP client says the analyser is not running** | See [Troubleshooting the connection](diagnose/troubleshooting.md). |

## Keyboard shortcuts

| Key | Action |
|---|---|
| **F** | Flag / unflag the selected rows |
| **F3** / **Shift+F3** | Jump to the next / previous anomaly |
| **Alt+Left** (or ⌘/Ctrl+`[`) | Source view: back to the previous file |
| **Ctrl/⌘-click** | Source view: navigate to the identifier under the cursor |
| **Enter** (search box) | Remember the search term |
| **Alt+1** … **Alt+9** (**⌘1** … **⌘9** on macOS) | Start page: choose that numbered entry |
| **Alt+R** (**⌘R** on macOS) | Start page: return to the analyser's own list after a prompt pack |
| **Enter** / **Shift+Enter** | Start page box and assistant composer: press the button or Send / start a new line |
| **Alt+↑** / **Alt+↓** | Assistant transcript: move between turns |
| **← / →** | A playing spotlight walk: previous / next step |
