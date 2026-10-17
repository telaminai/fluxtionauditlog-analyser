# Command line

The analyser is one program with a desktop mode and a few headless modes. An installed analyser is `analyser …`
([Install & run](../install.md)); from a jar it is `java -jar fluxtion-auditlog-analyser-<version>.jar …`.

| Command | What it does | Window |
|---|---|---|
| `analyser [log-file]` | Opens the desktop app, optionally on a log | yes |
| `analyser --rest [log-file]` | Opens the desktop app with **External MCP/REST access** turned on, and keeps it on | yes |
| `analyser --mcp` | Runs the MCP bridge on stdio, for an MCP client to launch | no |
| `analyser --verify <bundle.fexp>` | Verifies an evidence bundle against its manifest | no |
| `analyser --unpack <bundle.fexp> [--into <dir>]` | Verifies a bundle, then extracts it into a new working copy | no |
| `analyser --replay-compare <bundle.fexp> <replayed-audit.yaml>` | Verifies a bundle, then compares its log with a replayed one | no |
| `analyser --help` (or `-h`) | Prints the usage | no |

An option the analyser does not know is refused, with the usage and exit code 2, rather than being opened as a log
file.

## `analyser [log-file]`

Opens the desktop app. A log path opens that log at start-up, and only that log; a log you opened last time is
offered from the recent menus or session recovery, never opened by itself. With no path, the app opens on the start
page ([The start page](../user-guide/start-page.md)).

## `--rest`

Opens the desktop app with *External MCP/REST access* on, for an agent or a script starting the analyser on a
machine that has never run it. No dialog opens first. The setting **persists**: later launches without the flag still
have it on, until you turn it off in *Settings ▸ Assistant* or the *AI* menu.

The console prints the URL and only the token's **first 8 characters**: `[analyser] assistant REST:
http://127.0.0.1:… (token 1a2b3c4d…)`, and on a first run, where the endpoint file is. The full token is never printed:
a script reads it from `~/.fluxtion-analyser/rest-endpoint` and checks its `pid`
([Scripts and CI over REST](../diagnose/rest-automation.md), [External access](../admin/external-access.md)).

## `--mcp`

Runs the analyser's MCP bridge on stdio, with no window. Your MCP client launches this command; you do not. It needs
the desktop app running separately with External MCP/REST access on, and reads the endpoint file on every call. Use
the exact command **AI ▸ Connect an AI client…** shows for your machine ([Connect your client](../connect-an-llm.md)).

When the app is not reachable, each tool call answers *analyser not running, or External MCP/REST access is off — turn
it on in Settings ▸ Assistant, or start the app with `java -jar fluxtion-auditlog-analyser.jar --rest` …*
([Troubleshooting the connection](../diagnose/troubleshooting.md)).

## `--verify`, `--unpack`, `--replay-compare`

The recipient's side of an evidence bundle. They read only the file, so they need no running analyser.

| Command | Prints | Exit code |
|---|---|---|
| `--verify <bundle.fexp>` | `identity: sha256:…`, `verified: N members…`, an `excerpt:` line for an excerpt, a `replay:` line for a bundle with replay records, and one `limit:` line per stated limit | 0 verified · 1 refused, naming the member · 2 usage |
| `--unpack <bundle.fexp> [--into <dir>]` | as `--verify`, then `working copy: <dir>`. Nothing is extracted if verification fails. Without `--into`, the copy goes under `~/.fluxtion-analyser/bundles/` | 0 · 1 refused · 2 usage |
| `--replay-compare <bundle.fexp> <replayed-audit.yaml>` | `replay: AGREES, N of N records (endTime and thread excepted, …)` or `replay: DIVERGES at …`, naming the first difference, then the limits | 0 agrees · 1 diverges or refused · 2 usage |

A refusal prints `REFUSED: <reason>` to standard error. No command says or implies that the sender is authenticated:
a bundle is unsigned. [Evidence bundle format](../evidence-bundles/reference.md) has the manifest, the checks and the
replay runner.

`--pack` and `--bundle-profile` were removed; they exit with code 2 and say that the running analyser writes bundles,
through `report {bundle}`.

## `--help`

Prints the usage and the version, and exits.
