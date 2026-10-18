# Troubleshooting the connection

Find what your client said, or what you see, and change the one thing the row names. If nothing here matches,
**AI ▸ Connect an AI client… ▸ Check connection** runs the exact bridge command and makes one read-only call: a green
result proves the analyser and the bridge reach each other, and isolates the problem to the client.

## "analyser not running"

Every call your client makes through the bridge fails with this message when the bridge cannot reach a serving
analyser. It is quoted here exactly as the bridge says it:

> analyser not running, or External MCP/REST access is off — turn it on in Settings ▸ Assistant, or start the app with `java -jar fluxtion-auditlog-analyser.jar --rest` (External MCP/REST access on, no first-run dialog; the setting persists)

It means the bridge found no endpoint file, found one whose analyser is gone, or could not connect to the address in
it. In order of likelihood:

| What is true | Fix |
|---|---|
| The analyser is not open. | Open it. No client restart is needed: the bridge reads the endpoint on every call. |
| *External MCP/REST access* is off. The status bar's light reads **MCP off**. | Tick **AI ▸ External MCP/REST access**, or start the app with `--rest`. |
| The analyser that wrote the endpoint file crashed or was killed. | Open the analyser again. The error's detail says *the analyser that published … (pid …) is no longer running*. |
| Claude Code is running in the analyser's assistant panel, and external access is off, or was closed for that session. | See [below](#while-claude-code-runs-in-the-analyser). |

## While Claude Code runs in the analyser

The analyser can run Claude Code in its own assistant panel
([Claude Code in the Analyser assistant tab](../inapp/claude-code.md)). That session has its
own private bridge, and it changes what an outside client can reach.

**External MCP/REST access is off.** Claude Code in the panel still works: the analyser opens a private listener that
serves only that session, and publishes no endpoint file your client can use. So your client's bridge says *analyser
not running*. A
script that still holds an old URL and token is refused with:

> External MCP/REST access is off in this analyser (Settings ▸ Assistant); this listener serves only the analyser's own Claude Code session

The status line reads *"Claude Code's bridge listener is up (external clients are refused: External MCP/REST access
is off)"*. **Fix:** tick **AI ▸ External MCP/REST access**, then call again.

**External MCP/REST access is on, and someone chose *Close external access while this session runs*.** With *Full
Claude Code* in the panel and access on, the panel offers that button, because the session's own tools could drive the
analyser as an outside client through the endpoint file. Once chosen, the endpoint file is withdrawn until the session
ends, so your bridge says *analyser not running* even though Settings shows the switch on, and the status bar's
light reads **MCP starting**. A script still holding the
URL and token is refused with:

> external access is closed while an in-app Claude Code session runs (the person chose "Close external access while this session runs"); it reopens when that session ends — the External MCP/REST access setting is still on

The analyser's status line reads *"External MCP/REST access is closed while this Claude Code session runs; Claude Code
keeps its own connection. It opens again when the session ends."* **Fix:** wait for that session to end, or ask the
person at the analyser to stop it. The setting stays on throughout, and outside clients are served again as soon as
the session ends.

## Which window is serving

The status bar's light says whether **this** window is the one your client reaches.

| Light | Means | Do |
|---|---|---|
| **MCP ready** | This window is serving: a client pointed here reaches *this* log. | Nothing. *Ready* is not *connected*: whether a client is talking to it is a separate fact. |
| **MCP elsewhere** | Another analyser window owns the endpoint. A client is reading **that** window's log. The analyser started last takes the endpoint, so the earlier window shows this. | Close the other window, or ask in that one. When the owner closes, a window with access on takes the endpoint back within a few seconds. |
| **MCP starting** | Access is on, but this window has published no live endpoint. | Wait a moment. If it stays: access may be closed while an in-app Claude Code session runs ([above](#while-claude-code-runs-in-the-analyser)), when the light reads **MCP starting** for the whole session; or the listener failed to start, and the status line says *Could not start the assistant REST transport: …* with the reason. |
| **MCP off** | *External MCP/REST access* is off. Nothing is wrong. | Tick **AI ▸ External MCP/REST access** when you want a client. |

A window never takes the endpoint back from a live owner, only when that owner closes, so two windows do not take it
in turns. **MCP elsewhere** is the state that quietly wastes time: two analysers open, and the answers are about the
other one.

## Everything else

| Symptom | Cause | Fix |
|---|---|---|
| The server never appears in the client (`/mcp` shows nothing) | The client never started the bridge: a stale registration, or a client not reloaded. | Reopen **AI ▸ Connect an AI client…**, use its registration or its generic JSON exactly as shown, then restart or reload the client if it needs that. |
| The bridge command exits at once when you run it in a terminal | A stale or shortened command. | Use the exact command **Connect an AI client** resolves for this installation. Run by hand, it should wait for input. |
| `--mcp` is not recognised | JBang is running an older cached build. | `jbang cache clear`, or run once with `--fresh`. |
| *no log loaded* | The chain works; the analyser is empty. | Open a log, or ask the client to: `Open /path/to/audit.yaml in the analyser.` |
| The client opened a log but `context` shows nothing yet | Opening is asynchronous. | Call `context` again once the load has landed. |
| Calls time out on a very large log | An `aggregate` over millions of records. | Raise the client's tool timeout (Codex: `tool_timeout_sec`). |
| A *rate limited* tool error | A burst of more than 10 calls a second. | Retry; the agent should pace itself. The connection is not broken. |
| `screenshot`, `report` or `walk {reel}` refused: *file exports are disabled …* | File exchange is opt-in. | *Settings ▸ Assistant ▸ Allow assistant file exchange*, and choose the exchange directory. |
| The analyser will not start on a remote machine | It is a desktop application. | Run it in a desktop session, not a headless SSH shell. |
| A setting you changed in `~/.fluxtion-analyser/config` came back | The running analyser holds settings in memory and writes the file when it exits. | Edit the file only while the analyser is closed, or change the setting in *Settings ▸ Assistant*. |

**Claude Desktop**: per-server logs are at `~/Library/Logs/Claude/mcp-server-fluxtion-analyser.log` on macOS. The
bridge writes all its diagnostics to stderr, so they land there.

Scripts calling REST directly get HTTP status codes instead: see
[Scripts and CI over REST ▸ What the endpoint refuses](rest-automation.md#what-the-endpoint-refuses).
