# External access (MCP / REST)

**External MCP/REST access** is the one switch that lets something outside the analyser drive it: Claude Code, Codex
or another MCP client in your terminal, or a script. It is **off by default**, it is this user's setting on this
machine, and nothing else in the analyser opens a port for outside callers.

Claude Code running *inside* the analyser does not need it: that session has its own private bridge
([Claude Code in the analyser](../inapp/claude-code.md)).

## What it opens

When the switch is on, the analyser listens on `127.0.0.1` only, so nothing on the network can reach it:

- on a **port chosen afresh** at each launch;
- guarded by a **token minted afresh** at each launch, which every request must carry;
- refusing any request that carries an `Origin` header, so a web page in your browser cannot drive it;
- rate-limited to 10 calls a second, after a burst of 10.

It serves two routes: `GET /manifest` (the verbs and their schemas) and `POST /action` (one verb call). An MCP client
reaches it through the analyser's own bridge, which you register once ([Connect your client](../connect-an-llm.md));
a script calls it directly ([Scripts and CI over REST](../diagnose/rest-automation.md)).

What an outside client can then do is the same verbs a person has, under the same switches: file exchange, project
creation ([Security model ▸ What an assistant can do](security.md#what-an-assistant-can-do-through-the-analyser)).

## Turn it on, and off

- **AI ▸ External MCP/REST access**, a checkbox; or
- **Settings ▸ Assistant ▸ Every assistant ▸ External MCP/REST access (for an AI client on this machine)**, the same
  setting; or
- start the analyser with **`--rest`** (`analyser --rest`, or `java -jar fluxtion-auditlog-analyser.jar --rest`). This
  turns the switch on **and keeps it on**: the setting persists, so later launches without the flag still have it
  on. Turn it off in Settings or the AI menu.

The status bar says *External MCP/REST access is on: listening on http://127.0.0.1:… for external clients — …*, and
the light beside it reads **MCP ready**. The console prints the URL and only the token's **first 8 characters**; the
full token is never printed.

!!! warning "A shared settings file can turn it on"
    A settings file exported with its *Assistant* category carries this switch, and importing that category applies it
    to your machine. Untick *Assistant* when you import a file you did not make
    ([Projects, profiles and sharing](../user-guide/projects.md#what-is-kept-where)). A project profile and an
    evidence bundle never carry it.

## The endpoint file

While the switch is on, the analyser writes `~/.fluxtion-analyser/rest-endpoint`, **mode 600** (readable only by
you):

```json
{"url":"http://127.0.0.1:52041","token":"…","pid":12684,"startedAt":"2026-10-17T09:31:04Z"}
```

- The MCP bridge reads it on every call, so a client's registration holds no port and no token.
- A script reads it every time and checks that `pid` is a live process: a crashed analyser can leave a stale file.
- The analyser removes it when its window closes or it quits normally.
- No file means no access: the analyser is not running, the switch is off, or access is closed for a Claude Code
  session (below).

Anything running as you can read this file, and so can drive the analyser while the switch is on. That includes your
own Claude Code with full permissions, in a terminal or inside the analyser.

## Which window serves

One analyser window at a time owns the endpoint file. The analyser started last takes it; a window takes it back
only when the live owner closes. The light in each window's status bar says which: **MCP ready** (this window serves)
or **MCP elsewhere** (another window does). [Troubleshooting ▸ Which window is serving](../diagnose/troubleshooting.md#which-window-is-serving)
has every state.

## Closing it for one Claude Code session

With *Full Claude Code* running in the analyser and the switch on, the session's own tools could read the endpoint
file and drive the analyser as an outside client, including the destructive verbs. So the panel offers **Close
external access while this session runs**.

When the person at the analyser chooses it, the endpoint file is withdrawn and outside clients are refused until that
session ends, although Settings still shows the switch on; the light reads **MCP starting**. It opens again by itself
when the session ends. What a client and a script are told meanwhile is quoted on
[Troubleshooting ▸ While Claude Code runs in the analyser](../diagnose/troubleshooting.md#while-claude-code-runs-in-the-analyser).

With the switch **off**, Claude Code in the analyser still works through its private listener, which refuses every
outside client, even one holding an old token, and publishes no endpoint file.

## Who can change it

Every user can turn the switch on for themselves; there is no administrator lock yet
([Managed policy](managed-policy.md)). The token is minted per launch and never stored anywhere else, and *Copy
prompt* includes it only when you tick *Include my local REST token*, which starts unticked each time.
