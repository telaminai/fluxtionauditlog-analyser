# FAQ

## How big a log can it open?

Large ones. Files above the **memory threshold** (Settings ▸ Performance & S3, default 500 MB) are
memory-mapped rather than loaded into heap, so multi-GB logs work. Set the threshold to `0` to always
memory-map.

## Where are my settings stored?

In `~/.fluxtion-analyser/config` — cleartext properties (this is a local, single-user tool). It holds
your source roots, event processors, saved graphs, hidden columns and your LLM API key.

## Is my API key safe? Does it leave my machine?

The key is stored locally and is used only to call the LLM provider you configured. It is **never**
included in an exported settings file — see [Sharing setups](user-guide/sharing-setups.md).

## Does it work offline?

Yes. Everything except the optional LLM call is local. Opening from S3 uses your local `aws` CLI; the
assistant needs network only when you actually ask it to explain something (and even then, no key =
copy-prompt mode).

## Can I follow a growing log?

Yes — **File ▸ Follow (tail)** polls an open local file and appends newly-completed records live,
preserving flags and filters. (Heap-loaded local files only; not S3.)

## Why is a value in a record not graphable?

Only **top-level** numeric or boolean nodeLog keys can be plotted. A number sitting inside a `toString()`
(like `price=` inside `MutableOrder(price=…)`) is text within one value, not its own key. See
[Log format](log-format.md).

## Is the assistant's action socket safe to enable?

Yes — it's built to be. The optional REST transport that lets an external agent drive the analyser is
**off by default**, and when on it is:

- **loopback-only** — bound to `127.0.0.1`; not reachable from the network,
- **token-guarded** — every request needs a **per-run** secret token (regenerated each launch),
- **CSRF-hardened** — requests carrying an `Origin` header are rejected, so a browser page can't reach it,
- **rate-limited** and **bounded** — capped actions per reply; no shell access, ever.

Out of the box an agent can compute, filter, graph, flag and read records — **nothing outside the loaded
log, and no file writes**. Two groups of verbs go further, honestly labelled:

- **File exchange — `screenshot` and `report` writes, and external-series reads — is off by
  default.** Enabling *Allow assistant file exchange* (Settings ▸ Assistant) lets the write verbs write
  **only inside the exchange directory you choose** (never overwriting an existing file), and lets the
  `graph` verb's `external` series **read only from that same directory** — one opt-in, one directory,
  both directions. A file you pick in a chooser yourself is readable for that session: the chooser is
  the grant. `screenshot` and `report` are marked destructive to MCP clients so your agent asks first.
- **Scripting verbs — `open` and `source_root` — can change which log, event processor or source roots
  are open**, the same things you change through the UI. Still nothing outside the analyser: no shell,
  no arbitrary file reads, and your API key is never reachable.
- **Log-source plugins are jars you install yourself, and installing a jar is arbitrary code
  execution.** Nothing is bundled or downloaded — without plugins this application is byte-identical
  to a plain build — and a plugin can only ever be a log *reader*: it cannot add verbs to the action
  socket, so the verb list above stays complete with or without plugins.

## How do I connect Codex, Claude Code or another MCP client?

With the analyser open, use **Connect an AI client** on the Start page or **Settings ▸ Assistant / LLM**.
Turn on local transport and use **Check connection** to prove the local bridge reaches this live window.
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
