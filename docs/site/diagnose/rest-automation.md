# Scripts and CI over REST

An MCP client needs nothing on this page: its bridge finds the analyser and its token for you. This page is for a
script, a CI job, or an agent that calls the analyser's REST endpoint directly, without MCP.

The REST endpoint is the same one the bridge uses, with the same verbs, guards and limits. It listens on `127.0.0.1`
only, on a port chosen afresh each launch, with a token minted afresh each launch.

## Start the analyser with access on

```bash
java -jar fluxtion-auditlog-analyser.jar --rest
# or: jbang analyser@telaminai/fluxtionauditlog-analyser --rest
```

`--rest` opens the desktop app with *External MCP/REST access* on, and **keeps it on**: the setting is saved, and
*Settings ▸ Assistant ▸ External MCP/REST access* shows it. No dialog stands in the way, so a process can start the
analyser on a machine that has never run it. When the flag has just turned the setting on, the console says so:

```text
[analyser] --rest: External MCP/REST access turned on and saved (Settings ▸ Assistant ▸ External MCP/REST access); turn it off there if this machine should not offer it
```

Every launch with access on then prints the URL and only the **first 8 characters** of the token, as a hint to match
against the file below. The full token is never printed.

```text
[analyser] assistant REST: http://127.0.0.1:52041  (token 1a2b3c4d…)
```

On a first run, with no configuration yet, the console also names the endpoint file. The analyser is a desktop
application: it needs a desktop session, and does not start over a headless SSH shell.

## Read the endpoint file

While access is on, the analyser writes its live endpoint to `~/.fluxtion-analyser/rest-endpoint`, with
**mode 600** (readable only by you):

```json
{"url":"http://127.0.0.1:52041","token":"…","pid":12684,"startedAt":"2026-10-17T09:31:04Z"}
```

- **Read it every time.** The port and the token change on every launch, so never cache them.
- **Check `pid` is alive before you trust the file.** The analyser removes the file when its window closes, and when it
  quits without closing it (the macOS *Quit*, a `SIGTERM`). A file left behind by an analyser that crashed or was
  killed with `kill -9` still names its old port and token. The bridge ignores such a file, and so should your script.
- **No file means no access.** Either the analyser is not running, *External MCP/REST access* is off, or access is
  closed while an in-app Claude Code session runs ([Troubleshooting](troubleshooting.md#while-claude-code-runs-in-the-analyser)).

In a shell, after starting the analyser with `--rest` (wait for the file to appear, then check it):

```bash
EP=~/.fluxtion-analyser/rest-endpoint
for i in $(seq 1 60); do [ -f "$EP" ] && break; sleep 1; done
[ -f "$EP" ] || { echo "analyser not serving"; exit 1; }
URL=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["url"])' "$EP")
TOK=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["token"])' "$EP")
PID=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["pid"])' "$EP")
kill -0 "$PID" 2>/dev/null || { echo "stale endpoint file (pid $PID is gone)"; exit 1; }
```

## Learn the verbs: `GET /manifest`

`GET /manifest` needs no token (it is still refused while external access is off or closed). It returns every verb with its JSON schema, so a client learns the shapes before its
first call rather than from errors:

```bash
curl -s "$URL/manifest"
```

| Field | What it holds |
|---|---|
| `v` | the protocol version (`1`) |
| `tokenHeader` | `X-Analyser-Token` |
| `verbs` | every verb's name |
| `schemas` | each verb's JSON schema |
| `maxActionsPerReply` | the per-reply action cap set in Settings |
| `rateLimitPerSec` | the rate limit (below) |
| `instructions` | the guidance every MCP client is given, word for word |

## Call a verb: `POST /action`

Send the token in the `X-Analyser-Token` header and one verb per request:

```bash
curl -s -X POST "$URL/action" \
  -H 'Content-Type: application/json' -H "X-Analyser-Token: $TOK" \
  -d '{"v":1,"action":"context","params":{}}'
```

A verb that runs answers `200` with `{"ok":true,"action":"context",…}`. A verb that refuses answers `400` with
`{"ok":false,"error":"…"}`, and the error says why: *no log loaded*, a bad anchor, a path outside the exchange
directory. The source repository's [`tools/drive-analyser.sh`](https://github.com/telaminai/fluxtionauditlog-analyser/blob/main/tools/drive-analyser.sh)
`<verb> ['<json params>']` sends exactly this request (it does not check `pid`).

## What the endpoint refuses

| Status | When | What to do |
|---|---|---|
| `401` | the `X-Analyser-Token` header is missing or wrong | re-read the endpoint file; the token changed when the analyser restarted |
| `403` | the request carries an `Origin` header (*"requests carrying an Origin header are rejected"*) | call from a script, not a web page: a browser sends `Origin` with a cross-site request, so a web page cannot drive the analyser |
| `403` | external access is off, or closed for an in-app Claude Code session; this applies to `GET /manifest` too | the error quotes which; see [Troubleshooting](troubleshooting.md#while-claude-code-runs-in-the-analyser) |
| `404` | the path is neither `/manifest` nor `/action` | the body names both routes, the request envelope and the token header |
| `405` | the wrong method (`GET /action`, `POST /manifest`) | `GET` the manifest, `POST` an action |
| `429` | more than 10 calls a second, after a burst of 10 (*"rate limited (10.0/s)"*) | slow down and retry; nothing is lost |
| `500` | *"server error: …"*, an unexpected failure inside the analyser | report it, with the message |

The analyser's own Claude Code session has a separate rate limit, so a script cannot starve it, nor it a script.

## Guards that also apply to scripts

- **File writes are opt-in.** `screenshot`, `report` and `walk {reel}` need *Allow assistant file exchange*, write only
  inside the exchange directory, and never overwrite. A refused write says *"file exports are disabled — enable
  Settings ▸ Assistant ▸ 'Allow assistant file exchange' and choose an exchange directory"*.
- **Opening is asynchronous.** `open {log}` returns when the load starts (its echo carries `loading: true`). Poll
  `context` until its log is the one you opened, with its record count, before you query it. `analyser --rest <log>`
  opens a log at start-up instead.
- **Creating a project needs consent.** `open {project, create}` needs *Settings ▸ Assistant ▸ Allow the assistant to
  create projects*, off by default.
- **`propose` is not served.** It is offered only to Claude Code running inside the analyser; a script asking for it is
  told it is an unknown verb.

Each verb's arguments are in [Analyser assistant ▸ Actions the assistant can take](../user-guide/assistant.md#actions-the-assistant-can-take)
and in `GET /manifest`, which is always current.

## The token anywhere else

- *Copy prompt* (in the in-app assistant) includes the token and the action protocol only when you tick
  *Include my local REST token*, which is off every time the dialog opens.
- Claude Code running in the analyser's own assistant panel uses its own credential, not this token.
