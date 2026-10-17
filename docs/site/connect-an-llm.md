# Connecting an LLM to the analyser

The analyser is the **shared research canvas** of the [build-with-AI loop](the-loop.md): you ask in your
own words, the AI investigates, and it renders what it finds *in the analyser* — filters, flags, charts,
topology steps, a report — for you to review. This page is the plumbing that makes that possible: how an
LLM client connects, and how to check the connection is really working before you rely on it.

## How the connection works

```mermaid
flowchart LR
  C["Your MCP client<br/>(Claude Code, Claude Desktop, Codex…)"] -->|stdio| B["Local bridge<br/>resolved launcher + --mcp"]
  B -->|localhost REST<br/>url + token from ~/.fluxtion-analyser/rest-endpoint| A["The running analyser<br/>(your live session)"]
```

Three parts, and it matters which is which:

1. **The analyser you already have open** — the desktop app, with the log you loaded, the graphs you
   opened and the flags you set. That is the session the AI works on.
2. **External MCP/REST access inside the app** (a loopback REST listener). Off by default. When it is on, the app listens on
   `127.0.0.1` on an ephemeral port with a per-run token, and publishes both to
   `~/.fluxtion-analyser/rest-endpoint` (mode 600) so nothing has to be copied into a prompt.
3. **The bridge.** Your MCP client launches the exact local command shown by **Connect an AI client**
   (an installed JBang launcher or an absolute Java-and-jar command, followed by `--mcp`) and talks MCP
   to it over stdio. The bridge is headless; it reads the endpoint file and forwards each tool call to
   the running app. It does **not** start the app — the point is to drive *your* live session, not a
   fresh one with nothing in it.

The client sees **one tool per verb**, `analyser_open`, `analyser_context`, `analyser_filter`,
`analyser_graph`, `analyser_topology`, `analyser_flag`, `analyser_report` and so on — eighteen in all,
each with its parameter schema, so there is nothing to teach the model. The read-only ones are marked
read-only; the six that write files or replace what is loaded (`open`, `source_root`, `screenshot`, `report`,
`walk`, `import`) are marked destructive so your client asks you first. The full verb reference is in
[Analyser assistant](user-guide/assistant.md#actions-the-assistant-can-take). A nineteenth verb, `propose`, is
offered **only** to Claude Code running inside the analyser's Assistant tab: it is never on `/manifest`, never
served to an external MCP client and never given to the in-app assistant.

For clarity, JBang installs the local executable as **`analyser`**. The in-app setup registers the
separate MCP client label **`fluxtion-analyser`**, whose command is the resolved
`~/.jbang/bin/analyser --mcp` (or the equivalent packaged-JAR command). Those names have different jobs;
the registration does not require an executable rename.

## Connect, step by step

1. **Start the analyser and turn External MCP/REST access on.** Tick *AI ▸ External MCP/REST access* (it is
   remembered, and it is the same setting as *Settings ▸ Assistant ▸ External MCP/REST access (for an AI
   client on this machine)*), or start the app with the flag that does the same and says so on the console:

    ```bash
    java -jar fluxtion-auditlog-analyser.jar --rest
    # or: jbang analyser@telaminai/fluxtionauditlog-analyser --rest
    ```

    The status bar reads *External MCP/REST access is on: listening on http://127.0.0.1:… for external
    clients*.
    `--rest` is also what an agent runs on a machine that has never seen the analyser: no dialog stands in
    its way. The console prints the URL and only the **first 8 characters of the token**
    (`[analyser] assistant REST: http://127.0.0.1:… (token 1a2b3c4d…)`), says so when `--rest` has just turned
    the setting on, and names the endpoint file only on a first run (no configuration yet). Neither route
    puts the per-run token in client configuration — see [The token, for scripts](#the-token-for-scripts).

![The AI menu: connect a client, the External MCP/REST access toggle, runbook and glossary pointers, the exchange directory](assets/ai-menu.png)

2. **Set up the bridge from the analyser.** **AI ▸ Connect an AI client…** — reachable at any time,
   including with a log open, which is usually when the thought occurs. The same screen is on the Start
   page's *Connect an assistant* block (**Codex**, **Claude**, **Other MCP client**) and at
   *Settings ▸ Assistant ▸ Every assistant ▸ Connect an AI client…*.
   It shows the *resolved*, absolute bridge command for this installation rather than a command you have
   to reconstruct.

    - **Codex** and **Claude Code** offer explicit, confirmed CLI registration. Claude Code's automatic
      route is user-scoped; its project command is deliberately copy-only, so run it yourself from the
      project root you mean to change.
    - **Claude Desktop** has no bundled extension for this per-machine JBang/Java bridge. Select its
      **Generic MCP setup** fallback instead; the analyser does not edit Claude Desktop configuration.
    - **Generic MCP setup** shows a complete `mcpServers` JSON record. Copy it or save it to a file you
      choose, then put it in the location your client documents. The JSON preserves every command
      argument separately and never contains the endpoint or token.

    Opening the setup screen changes nothing. Use **Check connection** after turning on External MCP/REST access (the screen's
    *Turn on External MCP/REST access…* button does it, after a confirmation):
    it starts the exact bridge command and asks it only for `analyser_context`. A green result proves the
    local analyser-to-bridge chain, not that a client has imported the registration. Restart or reload
    your client where that client requires it.

    Which model to run the client with, for Spring and bundle authoring, is measured in
    [Model choice](spring-authoring-getting-started.md#model-choice).

### The registration screens

The Generic MCP choice shows a complete, selectable `mcpServers` record. **Copy configuration** copies
only that JSON; **Save snippet…** writes it only to a file you select and confirms before replacing one.
Neither action changes a client configuration. The path below is deliberately a neutral documentation
path — use the exact launcher shown by *your* analyser, not the one in this image.

![Generic MCP setup shows the resolved JBang launcher as a standard mcpServers JSON record, with Copy configuration and Save snippet actions](assets/mcp-generic-setup.png)

For Codex and Claude Code, choose **Check … registration** first. The app reports only the named
`fluxtion-analyser` entry; it does not infer that an entry at a different scope wins. **Register** or
**Replace** then shows the full command and a clear statement of what will change. It is still only a
proposal: **Cancel** does not start the client or write a configuration.

![Claude Code user-registration confirmation shows the exact command and Cancel and OK choices before the client CLI runs](assets/mcp-claude-code-confirm.png)

The Claude Code example is user-scoped. Codex uses the same readable confirmation layout for its shared
local registration. A project-scope Claude Code command remains copy-only; run it from the project root
yourself if that is the configuration you intend to change. A successful confirmation says that the
client command completed—not that a client is currently signed in, connected, or approved to call a tool.

3. **Open a log in the analyser** — or let the AI do it with `analyser_open` once you have checked the
   connection. Everything the AI renders lands in the window you are looking at.

## Check it is working

Do these in order; each one isolates a different part of the chain.

**Is this window even serving?** Read the status bar first — it costs nothing:

| Light | Means |
|---|---|
| **MCP ready** | this window is serving; a client pointed here reaches *this* log |
| **MCP elsewhere** | **another analyser window owns the endpoint** — a client is reading that window's log, not this one |
| **MCP starting** | enabled, but no live endpoint published yet |
| **MCP off** | External MCP/REST access is off — tick *AI ▸ External MCP/REST access* |

If the window that owns the endpoint **closes**, a window with External MCP/REST access on takes the endpoint back within a few
seconds and its light returns to **MCP ready** — a live owner is never displaced, so two windows cannot fight over it.

*Ready* is not *connected*: this window being reachable and a client actually talking to it are different
facts, and only the second needs a probe. **MCP elsewhere** is the one that quietly wastes time — two
analysers open, and the answers are about the other one.

**Does the bridge reach this app?** The quickest normal check is **AI ▸ Connect an AI client… ▸ Check
connection**. It runs the exact command shown on that screen and makes the read-only `analyser_context`
call. It is deliberately a bridge check, not an imitation of Codex, Claude Code or another client.

For a technical check, the endpoint file exists while the analyser is listening and names a URL:

```bash
cat ~/.fluxtion-analyser/rest-endpoint
# {"url":"http://127.0.0.1:52041","token":"…","pid":12684,"startedAt":"…"}
```

No file means External MCP/REST access is off — tick *AI ▸ External MCP/REST access*, or restart with `--rest`.

### The token, for scripts

The bridge reads the token for you, so an MCP client never needs it. A script, a CI job or any other agent
that calls the REST endpoint directly must read it from the same file:

- `~/.fluxtion-analyser/rest-endpoint` is written with **mode 600** (readable only by you) and holds
  `{url, token, pid, startedAt}`. The token is fresh on every launch, so read the file each time; never
  cache it.
- **Check `pid` is alive before trusting the file.** A file left by an analyser that crashed still names its
  old port and token; the bridge ignores such a file, and so should your script.
- The console shows only the token's **first 8 characters** (`(token 1a2b3c4d…)`), as a hint to match the
  file against, never the token. Automation that used to read the token from the console must read the file.
- *Copy prompt* includes the token only when you tick *Include my local REST token* (off every time).

**Does your client see the tools?** In Claude Code, `/mcp` should show the server connected with
**18 tools**:

```text
> /mcp
  ⎿ fluxtion-analyser   ✔ connected · 18 tools
       analyser_aggregate · analyser_series · analyser_read
       analyser_filter · analyser_graph · analyser_goto
       analyser_flag · analyser_coverage · analyser_report
       analyser_context · analyser_topology · analyser_screenshot
       analyser_import · analyser_open · analyser_source
       analyser_source_root · analyser_spotlight · analyser_walk
```

`analyser_propose` is not in the list, and never will be for an external client: it is offered only to the
analyser's own Claude Code session.

If the server is missing, the client did not launch the bridge. Reopen the analyser setup screen and use
the exact resolved record or registration command; then restart/reload the client if it requires that.

**Does a call reach the app?** Ask for something harmless and read-only:

```text
> Call analyser_context and tell me what log is open.
```

- A description of your session — the log, its record count, the filter, any flags — means the whole
  chain works. Nothing changed on screen: `context` only reads.
- *"analyser not running, or External MCP/REST access is off — turn it on in Settings ▸ Assistant, or start
  the app with `java -jar fluxtion-auditlog-analyser.jar --rest` (External MCP/REST access on, no first-run
  dialog; the setting persists)"* means the tools are registered but the bridge found no endpoint file or could
  not reach it: the app is closed, or External MCP/REST access is off. Fix step 1 and ask again — no client
  restart needed.
- The same *not running* message also appears while Claude Code runs **inside** the analyser with external
  access off or closed for that session: the analyser withdraws the endpoint file then. See
  [While Claude Code runs in the analyser](#while-claude-code-runs-in-the-analyser).
- *"no log loaded"* means the chain works and the analyser is simply empty. Open a log, or ask the AI
  to: `Open /path/to/audit.yaml in the analyser.`

**Does a render land on the canvas?** Ask for one visible, reversible change:

```text
> Filter the analyser to the last 30 seconds and flag the record with the highest spread.
```

The records table narrows and a flagged row appears — in your window, as you watch. Everything the AI
does from here is like that: a change you can see, click into and undo. If you would rather script the
same check without a model in the loop, `tools/drive-analyser.sh context` in the repository sends the
same call the bridge does.

## While Claude Code runs in the analyser

The analyser can run Claude Code inside its own Assistant tab ([Claude Code in the Assistant
tab](user-guide/assistant.md#claude-code-in-the-assistant-tab)). That session has its own private bridge and
credential, and it changes what an *external* client can reach:

- **External MCP/REST access off.** Claude Code in the tab still works: the analyser opens a private listener
  that serves only that session. It publishes no endpoint file, so your MCP client's bridge says *"analyser not
  running, or External MCP/REST access is off …"*. A script that still holds a URL and token from earlier is refused
  with *"External MCP/REST access is off in this analyser (Settings ▸ Assistant); this listener serves only the
  analyser's own Claude Code session"*. Turn the switch on if you want both.
- **External MCP/REST access on, with Full Claude Code in the tab.** The session's own tools can then drive
  the analyser as an external client through the endpoint file, so the assistant panel says so and offers
  **Close external access while this session runs**. If the person chooses it, external clients are refused
  until that session ends: the endpoint file is withdrawn, so a bridge says *"analyser not running, or External
  MCP/REST access is off …"* even though Settings shows the switch on, and a script still holding the URL and
  token is told *"external access is closed while an in-app Claude Code session runs (the person chose "Close
  external access while this session runs"); it reopens when that session ends — the External MCP/REST access
  setting is still on"*. The analyser's status line reads *"External MCP/REST access is closed while
  this Claude Code session runs; Claude Code keeps its own connection. It opens again when the session ends."*
  The setting itself stays on; stop the session (or wait for it to end) and external clients are served again.

## If something is off

| Symptom | Cause | Fix |
|---|---|---|
| Tools listed, every call says *not running* | app closed, or External MCP/REST access off | start the app; *AI ▸ External MCP/REST access*, or `--rest` |
| *not running* while the analyser is plainly open, and its Assistant tab runs Claude Code | External MCP/REST access is off: Claude Code's private listener serves only that session and publishes no endpoint file (a script holding an old token is told *External MCP/REST access is off in this analyser*) | *AI ▸ External MCP/REST access* on, then call again |
| *not running* although *AI ▸ External MCP/REST access* is ticked, while Claude Code runs in the tab | the person chose *Close external access while this session runs*; the endpoint file is withdrawn until that session ends (a script holding the token is told *external access is closed while an in-app Claude Code session runs*) | wait for that Claude Code session to end, or ask them to stop it; the setting stays on |
| Server never appears in the client | stale launcher or client not reloaded | reopen **AI ▸ Connect an AI client…**, use its resolved registration/JSON, then restart or reload the client if required |
| Calls time out on a very large log | an `aggregate` over millions of records | raise the client's tool timeout (Codex: `tool_timeout_sec`) |
| `screenshot` / `report` refused | file exchange is opt-in | Settings ▸ Assistant ▸ *Allow assistant file exchange*, and pick the directory |
| The AI opened a log but `context` shows nothing yet | opening is asynchronous | ask for `context` again — it reports the log, its time-order report and any offers once the load lands |

The same token-guarded, loopback-only transport also lets a plain script or another agent drive the
analyser without MCP at all — `GET /manifest` on the endpoint URL publishes every verb's schema. See
[Analyser assistant](user-guide/assistant.md) for the action protocol and the [FAQ](faq.md) for what the
socket can and cannot do to your machine.
