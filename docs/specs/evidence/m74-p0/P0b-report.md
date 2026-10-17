# M74 P0b — the default command, the binding nonce, the P3 contract, slash commands

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) (r4 §12 at the time of the run; r5 folds
these results in). **Date:** 2026-10-16. **CLI:** `2.1.282 (Claude Code)`, macOS (Darwin 25), native install.
**Model:** `haiku` (claude-haiku-4-5) except one turn on `sonnet` after `/model sonnet` (item 4).
**Spend:** about **US$0.19** by the probe's ledger (`--cap 1.8`, owner cap US$2; every process ran with
`--max-budget-usd` 0.15 or 0.25). The CLI's own final `total_cost_usd` per process sums to US$0.13, because `/clear`
resets the session's running total, so the ledger figure is the one to use. No run used `bypassPermissions`, and none
named it.

**How it was run.** The experiments `b_*` in [`probe.py`](probe.py) (`--list` names them). The `--i-accept-spend` gate
is unchanged. The MCP server is a Python stdio stub (`STUB_B` in the script) named `fluxtion-analyser-hosted`, with
four tools: `context` and `echo` stand for the pre-allowed read/render verbs, `report` stands for a destructive verb,
and `approve` is a permission-prompt handler. Its `serverInfo.version` is set from its argument
(`1.35.0+bind.<random>`), and it can be told to answer `initialize` late (`STUB_DELAY`) or never (`STUB_HANG`). No
analyser was started or contacted. The work directory was a scratch folder; its project folder `b/proj` held a
`CLAUDE.md` with a codeword, a `.claude/settings.json` with `SessionStart`, `UserPromptSubmit` and `PreToolUse` hooks
that write a marker file, and a `.mcp.json` naming a second stub. `b/outside` sat beside it.

**Capture and sanitising** were done as in P0. Live runs used the real login with `--strict-mcp-config`, because an
isolated home loses the login (P0 §Isolation). Runs that need no model call (`b_account_fields`, `b_slash_config_iso`)
used an isolated `CLAUDE_CONFIG_DIR`. Every fixture went through `probe.py --sanitise`. Each distinct string value in
every fixture was read, and the rule-1 sweep plus a grep for home paths, user name, e-mail and token shapes came back
clean. **New in P0b:** streamed deltas split paths across chunks (`"…IdeaProjects-"`, `"telamin-flu…"`), and the
line-wise sanitiser missed those pieces. The sanitiser now rejoins each content block's deltas, sanitises the whole
text, and, when that changes anything, puts the sanitised text in the block's first delta and empties the rest. The
text a renderer assembles is kept; the chunk boundaries of those blocks are not. P0's fixtures were re-checked and
have no such fragments. The raw transcripts and the probe's one session folder under `~/.claude/projects/` (name
containing `scratchpad-m74-p0b`) were deleted after sanitising.

## 1. Verdicts

| # | Item | Verdict | Evidence (fixtures/) |
|---|---|---|---|
| 1 | §12.1 default command, in one line | **CONFIRMED** | `p0b-default-posture.jsonl`, `p0b-disallow-destructive.jsonl` |
| 2 | binding nonce in `serverInfo.version`, via `mcp_status` | **CONFIRMED** | `p0b-default-posture.jsonl`, `p0b-nonce-delay.jsonl`, `p0b-nonce-hang.jsonl`, `p0b-nonce-resume.jsonl` |
| 3a | `--permission-prompts host`: the host contract | **CONFIRMED, with a correction**: needs `--permission-prompt-tool stdio`; never asked under `dontAsk` | `p0b-p3-host.jsonl`, `p0b-p3-stdio.jsonl`, `p0b-p3-stdio-dontask.jsonl` |
| 3b | `--permission-prompt-tool mcp__…`: contract; can the model call it? | **CONFIRMED hidden when configured; a hazard otherwise** | `p0b-p3-prompttool-visible.jsonl`, `p0b-p3-prompttool-disallowed.jsonl`, `p0b-p3-prompttool-deny.jsonl` |
| 4 | `initialize.commands`; posture-widening commands | **REFUTES "pass everything through"**: `/config` writes the person's user settings; `/clear` changes the session id | `p0b-slash.jsonl`, `p0b-slash-config-iso.jsonl` |
| 5 | `initialize.account` field names | **CONFIRMED** (three shapes) | `p0b-default-posture.jsonl`, `p0b-account-iso.jsonl`, `p0b-account-iso-apikey.jsonl` |

### Item 1: the exact default command

The command, as run (stdio basics `-p --model haiku --input-format stream-json --output-format stream-json --verbose
--max-budget-usd 0.15` first):

```
--tools "Read,Grep,Glob" --restricted --add-dir <root>
--strict-mcp-config --mcp-config '{"mcpServers":{"fluxtion-analyser-hosted":{…stub…}}}'
--allowedTools "mcp__fluxtion-analyser-hosted__context,mcp__fluxtion-analyser-hosted__echo"
--permission-mode dontAsk --permission-prompts none
--replay-user-messages --include-partial-messages
--session-id <uuid>
env CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD=1, cwd = <root>
```

| Check | Observed |
|---|---|
| MCP server loads | `mcp_status` answered `connected` on the first answered poll (0.49 s after spawn). `system/init.mcp_servers` = `[{name:"fluxtion-analyser-hosted", status:"connected", source:"dynamic"}]`, and nothing else |
| pre-allowed MCP calls succeed | `context` and `echo` ran (stub log), no denial |
| non-pre-allowed MCP tool is denied without blocking | `report`: `permission_denied` (`decision_reason_type:"mode"`), the tool error *"…denied because Claude Code is running in don't ask mode"*, the turn completed, and `result.permission_denials` lists it. The stub log shows no `report` call |
| CLAUDE.md loaded | codeword answered `PINEAPPLE-7` |
| no hooks run | marker file absent after spawn, after six turns and after tool calls (`SessionStart`, `UserPromptSubmit`, `PreToolUse` all silent). No `hook_*` events |
| project `.mcp.json` ignored | its stub never started (no marker); not in `mcp_servers` |
| out-of-dir read denied | `Read` outside: `decision_reason: "--restricted: path outside the working directory"`, message *"<path> is outside <CWD>; --restricted confines the file tools to the working directory."* In-dir `Read` worked; `Bash` is absent (`NO-BASH`) |
| mode | `initialize.current_permission_mode` and `system/init.permissionMode` both `dontAsk` |
| tools | `system/init.tools` = `Glob, Grep, Read` **plus every tool the hosted server advertises**, the non-allowed `report` and `approve` included. `--allowedTools` decides approval, not visibility |
| skills | `init.skills` has 18 entries, all bundled built-ins; none of the person's own skills |

**`system/init.tools` therefore cannot equal "Read/Grep/Glob + the pre-allowed verbs" unless the rest are removed.**
`p0b-disallow-destructive.jsonl`: adding `--disallowedTools "mcp__fluxtion-analyser-hosted__report,…__approve"`
gives `init.tools` = exactly `Glob, Grep, Read, mcp__…__context, mcp__…__echo`. The model is then told there is no
such tool, and `report` never reaches the server. This is what makes r5's start-time posture assertion exact.

### Item 2: the binding nonce

- `mcp_status` returns `serverInfo: {name: "fluxtion-analyser-hosted", version: "1.35.0+bind.6f0ccc1c850248be"}`, the
  version string exactly as the server reported it. After six turns it was **byte-identical**.
- **Timing.** Five spawns with a stub that answers at once were all `connected` on the first answered poll, 0.44 to
  0.51 s after spawn. The CLI does not answer a control request sooner than that, so no `pending` was seen. A stub
  that waits 3 s read `pending` at 0.47 s and `connected` at 3.29 s. A stub that never answers `initialize` read
  `pending`, then **`failed` at 30.48 s** with `error: "MCP server \"[redacted]\" connection timed out after 30000ms"`.
  That 30 s is the CLI's own connect timeout, and the CLI redacts the server name in the error.
- **Resume:** `--resume <id>` with a fresh nonce reports the fresh nonce (`connected` at 0.49 s), so a nonce minted per
  spawn works across resume.
- **Proposed poll:** the first `mcp_status` right after `initialize` answers, then every **250 ms**. Accept only on
  `connected` with the exact version string. Refuse **at once** on `failed`, on a mismatch, or on a version without
  `+bind.`. Stop waiting at **35 s** (the CLI's 30 s plus a margin). Show "connecting to the analyser bridge…" after
  2 s. The real bridge is a JVM started by JBang, and its cold start was not measured here: P1a measures it and may
  lower the 35 s. It is unlikely to need raising, because the CLI gives up at 30 s anyway.

### Item 3: the P3 permission contract

**3a. `--permission-prompts host` alone does not reach a stdio host.** With `--permission-mode default
--permission-prompts host` and no `--permission-prompt-tool`, a non-allowed call is denied at once with *"Claude
requested permissions to use mcp__…__report, but you haven't granted it yet."* (`non_execution_kind:"user-rejected"`),
and **no `control_request` arrives on stdout** (`p0b-p3-host.jsonl`). The host is asked only with
**`--permission-prompt-tool stdio`**, which is what the Agent SDK passes for `canUseTool`. `p0b-p3-stdio.jsonl`:

```
CLI → host: {"type":"control_request","request_id":"<id>","request":{
   "subtype":"can_use_tool", "tool_name":"mcp__fluxtion-analyser-hosted__report",
   "mcp_server":{"name":"fluxtion-analyser-hosted","source":"dynamic"}, "display_name":"Report",
   "input":{"title":"allowed-1"}, "tool_use_id":"toolu_…",
   "permission_suggestions":[{"type":"addRules","rules":[{"toolName":"mcp__…__report"}],
                              "behavior":"allow","destination":"localSettings"}]}}
host → CLI (allow): {"type":"control_response","response":{"subtype":"success","request_id":"<id>",
   "response":{"behavior":"allow","updatedInput":{"title":"allowed-1"}}}}
host → CLI (deny):  {"type":"control_response","response":{"subtype":"success","request_id":"<id>",
   "response":{"behavior":"deny","message":"Denied by the person in the analyser"}}}
```

On allow, the tool ran with `updatedInput`. On deny, the model received the host's message as the tool error and
`permission_denials` listed the call.

- **Under `dontAsk` the host is never asked** (`p0b-p3-stdio-dontask.jsonl`): the call is denied by mode. P3 therefore
  changes the session's mode from `dontAsk` to `default`, and its posture assertion changes with it.
- **An out-of-directory `Read` under `--restricted` is never offered to the host.** It is denied outright (same
  `--restricted` reason) under stdio and under an MCP prompt tool alike, so an approval dialog cannot widen the folder.
- `permission_suggestions` proposes **writing an allow rule to local settings**. The analyser must never return one
  (`updatedPermissions`); it answers per call.

**3b. `--permission-prompt-tool mcp__fluxtion-analyser-hosted__approve`.** The CLI calls the tool with
`{tool_name, input, tool_use_id}` and expects a text result holding `{"behavior":"allow","updatedInput":{…}}` or
`{"behavior":"deny","message":"…"}`. Both worked, and on deny the message reached the model. **When the tool is
configured as the prompt tool, the CLI removes it from the model's tools**: `init.tools` has no `approve`, and asked to
call it, the model answered that no such tool exists. Adding `--disallowedTools` for it changed nothing; it kept
working as the prompt tool. **But when the flag is absent, the tool is an ordinary visible tool.** In `p0b-p3-host`,
asked only to call `report`, the model **also called `approve` on its own initiative**, with
`{tool_name:"mcp__…__report", input:{…}}`. That was denied only because it was not pre-allowed. In `p0b-p3-stdio`,
asked to call it, the model reached it through the ordinary permission path.

**Verdict on P3 safety: use `--permission-prompt-tool stdio`, never an MCP prompt tool.**
- With stdio, nothing approval-shaped exists that the model could call. The dialog lives in the host, which is the
  analyser process holding the session, and the request names the server (`mcp_server.name`) and the tool-use id.
- An MCP approval tool is hidden only while the flag is set correctly. Otherwise the model sees it and, by P0b's
  evidence, calls it unasked. It also lives on the bridge, which other clients of the same bridge binary would see.
  Its safety rests on a configuration detail, so it is rejected.
- The host answers `allow` only with `updatedInput` equal to the input shown to the person; the dialog never lets the
  model's input be edited silently. It never applies a suggestion, and denies on dialog close, Stop or a timeout.
- **Not tested** (carried to P3): a `can_use_tool` left unanswered (assumed to block the turn), an interrupt while one
  is pending, and whether a hook-less `default` mode under `--restricted` ever prompts for anything besides MCP tools.

### Item 4: slash commands under the default posture

`initialize.commands` has 54 names, all `builtin: true`, because `--restricted` drops the person's own commands and
skills (r5 §5.4 keeps the full list). They are, in order: deep-research, design, design-sync, dataviz, update-config,
verify, debug, code-review, simplify, batch, fewer-permission-prompts, doctor, loop, schedule, claude-api,
workflow-authoring, run, run-skill-generator, advisor, agents, auto-mode-setup, autocompact, clear, color, compact,
config, output-style, context, effort, fast, focus, heapdump, init, mcp, import, model, `__remote-workflow`,
workflow-launch-exec, reload-plugins, reload-skills, rename, ultrareview, security-review, usage-credits,
extra-usage, usage, insights, recap, skill-doctor, goal, design-consent, design-revoke, list-agents, team-onboarding.
Aliases include `settings` → config, `reset`/`new` → clear, `cost`/`stats` → usage, `routines` → schedule. Each
command was sent as a user line over stream-json, and every local command ended in a `result` carrying its output.

| Command | Observed | Changes posture / identity? |
|---|---|---|
| `/add-dir <outside>` | *"/add-dir isn't available in this environment."* (`kind:"unavailable_headless"`); the outside read afterwards was still denied | no (CLI refuses headless) |
| `/permissions`, `/allowed-tools` | *"…isn't available in this environment."* | no (CLI refuses headless) |
| `/config` (bare) | usage text listing `key=value` settings, including **`permissionMode=default\|plan\|acceptEdits\|auto\|dontAsk`** and `model=…` | — |
| **`/config permissionMode=auto`**, **`/config model=opus`** (isolated config only) | *"Set Default permission mode to auto"*, *"Set Model to opus"*. **The live session is unchanged** (`initialize` and `system/init` still `dontAsk`, model haiku). But **the user `settings.json` was rewritten** with `permissions.defaultMode:"auto"` and `model:"opus"`, **although `--restricted` ignores that file for reading.** A fresh process with `--permission-mode dontAsk` is still `dontAsk`. | **yes: a persistent change to the person's own Claude Code**, outside the analyser |
| `/output-style Proactive` | refused: saved to local settings, which this session does not load | no |
| `/mcp` (bare) | status line *"1 MCP server(s): 1 connected…"* | no |
| `/mcp enable projstub` | *"There's no MCP server named projstub"*: strict mode holds | no; `/mcp disable <hosted>` / `reconnect` were **not** run (they may persist, and would cut the bridge) |
| `/effort high` | session only | no (cost) |
| `/fast` | *"not available in the Agent SDK"* | no |
| `/reload-plugins` | *"0 plugins · 0 skills · 5 agents · 0 hooks"*: reloading does not pick up the project's hooks | no |
| **`/model sonnet`** | *"Set model to Sonnet 5 for this session only"*; a `model_changed` event; the next `system/init.model` is `claude-sonnet-5`, and the turn cost about 20× the haiku turn | no posture change; **changes the header and the cost** |
| **`/clear`** | `conversation_reset` (`trigger:"clear"`), a `result` with cost 0, and **the next `system/init` has a new `session_id`** | **yes: the session identity changes under the analyser** (lock, budget total, resume id, transcript) |

After all of these, tools, mode, servers and cwd were unchanged (`system/init` diff: only `model`). The nonce was
unchanged, and the project `.mcp.json` stub never started.

**Must be blocked by the analyser:** `/config` and alias `/settings` (it writes the person's user settings, including
the permission mode their terminal Claude Code starts in); `/clear` and aliases `/reset`, `/new` (route to **New
chat** instead); `/mcp` with any argument (enable, disable and reconnect act on servers and can cut the hosted
bridge). `/add-dir`, `/permissions` and `/allowed-tools` are refused by the CLI today and are blocked anyway, so a
future CLI that allows them headless cannot widen the folder. **Not exercised, blocked on their own description:**
`/auto-mode-setup` (sets up permission modes and applies a settings file), `/schedule`/`/routines`, `/loop`,
`/ultrareview`, `/__remote-workflow`, `/workflow-launch-exec` (remote or scheduled agents), `/extra-usage`,
`/usage-credits` (billing), `/design-consent`/`/design-revoke`, `/import`, `/heapdump` (writes a file),
`/team-onboarding`. `/model` passes through, and the header follows `model_changed` / `system/init.model`.

**Consequence:** the analyser cannot keep a deny-list current against a CLI that adds commands. r5 §5.4 classifies the
built-in names (pass, block, or route to New chat) and refuses **any unclassified built-in** with "not available in
the analyser — use a terminal". The person's own commands (`builtin:false`, full posture only) pass through. This is
the allow-list rule §3 already applies to tools.

### Item 5: `initialize.account` field names (values never read)

| Login | `account` keys |
|---|---|
| claude.ai subscription (real login) | `apiProvider, email, organization, subscriptionType` (no `tokenSource`) |
| not logged in (isolated config) | `apiProvider, tokenSource` (`tokenSource:"none"`) |
| inherited `ANTHROPIC_API_KEY` (isolated, a fake key) | `apiKeySource, apiProvider, tokenSource` (`apiKeySource:"ANTHROPIC_API_KEY"`, `tokenSource:"none"`) |

So the header reads `subscriptionType` when present, otherwise `apiKeySource`, plus `apiProvider` in both cases. **Not
logged in** means `tokenSource:"none"` with no `apiKeySource` and no `subscriptionType`. An *invalid* API key passes
this check (the key is not validated at `initialize`), so its first turn fails with an auth error instead (§9).
`email` and `organization` occur only in the subscription shape; r5 drops them at the parse boundary.

## 2. Findings that change a decision

1. **Slash commands cannot all pass through (item 4).** `/config key=value` rewrites the person's user settings,
   permission mode included, from inside a `--restricted` session. `/clear` changes the session id under the
   analyser. The owner's pass-through decision stands for commands that do neither, and an unclassified built-in is
   refused (r5 §5.4).
2. **P3 uses `--permission-prompt-tool stdio` and mode `default` (item 3).** `--permission-prompts host` alone never
   reaches a stdio host, and `dontAsk` never asks anyone. An MCP prompt tool is visible to the model unless the flag
   is exactly right, and the model called one unasked, so it is rejected.
3. **The posture assertion needs `--disallowedTools` for the non-pre-allowed hosted verbs (item 1).** `--allowedTools`
   approves; it does not hide. Without the disallow list, `init.tools` lists the destructive verbs too.
4. **The nonce check is a 250 ms poll bounded by the CLI's 30 s connect timeout (item 2).** `failed` arrives on its
   own at 30 s for a hung bridge.
5. **Not-logged-in is `tokenSource:"none"` without `apiKeySource` (item 5).** An inherited key, valid or not, is
   "logged in" at Start.

## 3. Reproduce

```
python3 docs/specs/evidence/m74-p0/probe.py --list
python3 docs/specs/evidence/m74-p0/probe.py --i-accept-spend --model haiku --budget 0.15 --cap 1.8 \
    --workdir <scratch>/m74-p0b --only b_account_fields,b_default_posture,b_disallow_destructive,b_nonce_timing,\
b_p3_host_prompts,b_p3_prompt_tool,b_p3_stdio,b_p3_prompt_tool_deny,b_slash_commands,b_slash_config_iso
python3 docs/specs/evidence/m74-p0/probe.py --sanitise <scratch>/m74-p0b/raw/p0b-<name>.jsonl \
    fixtures/p0b-<name>.jsonl --workdir <scratch>/m74-p0b --cwd b/proj
```

`b_slash_config_iso` must stay on the isolated config: run against the real login, it would rewrite the person's own
`settings.json`. Delete `<scratch>/m74-p0b` and `~/.claude/projects/*scratchpad-m74-p0b*` afterwards.
