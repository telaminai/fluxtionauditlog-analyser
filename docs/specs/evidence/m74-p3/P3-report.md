# M74 P3 — the permission-prompt probes P3-0…P3-6

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) r11, §8.3 (the probe table), §5.7 and
OD-74.16–74.18. **Date:** 2026-10-17. **CLI:** `2.1.282 (Claude Code)`, macOS (Darwin 25), native install.
**Model:** `haiku` (claude-haiku-4-5); the isolated `auto` line on `sonnet` (PB-3).
**Spend:** on the person's Claude plan (owner, 2026-10-17: no budget cap); the probe's ledger of the CLI's own
`total_cost_usd` figures reads **US$0.24** across nine real-login processes. The runaway guard was the harness's: every
process ran with `--max-budget-usd 0.30`, every wait was bounded, a ledger cap of 2.0 and a 30-minute watchdog on the
whole run (neither fired). Everything else ran under an isolated config and cost nothing. No run used
`bypassPermissions`, `dontAsk`, `--dangerously-skip-permissions` or an MCP permission-prompt tool, and none named them.

## How it was run

[`p3.py`](p3.py) (`--list` names the experiments), reusing P0's helpers and sanitiser. **The hosted server is the real
bridge**, not P0b's stub: the analyser's own jar (`java -jar <JAR> --mcp --endpoint-file <file>`), behind a tee
(`tee.py`, written by the harness) that records every JSON-RPC line both ways. The bridge forwards to a fake analyser
REST endpoint inside the harness (named by the per-instance endpoint file, with the harness's pid and a hosted
credential), which records each `/action` it is sent and answers ok. So the CLI, the permission protocol and the bridge
are the real ones; the analyser's own admission and grants were not under test here (they are the product code P3
adds, tested in §10's rows).

- **The P3 lines, exactly §4.3's.** Default posture: `--tools "Read,Grep,Glob" --restricted --add-dir <proj>
  --strict-mcp-config`, the hosted bridge inline, `--allowedTools` the thirteen pre-allowed verbs by name, **no
  `--disallowedTools`**, `--permission-mode default --permission-prompts host --permission-prompt-tool stdio`. Full
  posture: no confining flag, `--disallowedTools "mcp__fluxtion-analyser"` (plus the six destructive verbs under `auto`
  and `plan`), the chosen mode and the same two prompt flags.
- **OD-74.18.** `p3_lines_iso` ran under an isolated `CLAUDE_CONFIG_DIR` (not logged in: `initialize`, `mcp_status`
  and a turn's `system/init` cost nothing). Every real-login run carried `--strict-mcp-config` (the harness asserts it
  and adds it to a full line), so none of the person's MCP servers or connectors loaded. The full-posture real runs
  loaded the person's own settings, skills and hooks, as the full posture intends; their raw transcripts were **not**
  committed — only the observations below.
- **Capture and sanitising.** Committed (`fixtures/`): the default-posture captures (`--restricted`, strict MCP) and two
  isolated ones, through P0's sanitiser (it rejoins split deltas), then two fixes by hand: the JVM and jar paths became
  `<JAVA>` and `<JAR>`, and **the sanitiser's user-name rule had rewritten the analyser's own verb `aggregate`** (the
  user name is a substring of it) — restored in every fixture (`aggregate`, `aggregation`). Every distinct string value
  in every fixture was then read; the four-term sweep and a grep for home paths, the user name (whole words), e-mail
  and token shapes came back empty. The account's `email` and `organization` are `<redacted>`, the plan
  `<subscription>`; the tool-use ids and bind nonces are random per run and kept (the tests compare them).
- **Cleanup.** The two `~/.claude/projects/` folders the real-login runs created (names ending `scratchpad-m74-p3-proj`
  and `-full`) were deleted; the folder list equals the one taken before the run, the person's `~/.claude/settings.json`
  hash is unchanged (`2903b6a6fe2cec0e` before and after) and no `~/.claude/CLAUDE.md` exists. The scratch folder and
  every raw transcript were deleted after this report was written.

## Verdicts

| # | Question | Verdict | Evidence (fixtures/) |
|---|---|---|---|
| P3-0 | the whole P3 default line on the real bridge | **CONFIRMED** | `p3-0-default-real.jsonl`, `p3-lines-default-posture-iso.jsonl` |
| P3-1 | an unanswered `can_use_tool` | **BLOCKS** — no CLI timeout in 200 s; a late answer is accepted | `p3-1-unanswered-real.jsonl` |
| P3-2 | an interrupt while one is pending | **the CLI withdraws it** with `control_cancel_request`; the turn ends cancelled; a late answer does nothing | `p3-2-interrupt-real.jsonl` |
| P3-3 | what the bridge's `tools/call` carries | **`_meta["claudecode/toolUseId"]`** = the request's `tool_use_id`; the arguments are **`updatedInput`**, not `input` | `p3-0-default-real.bridge.jsonl` |
| P3-4 | does anything but an MCP tool ask in the default posture | **NO** | (observations below) |
| P3-5 | full posture: Bash/Edit asks; a hook and an allow rule bypass | **CONFIRMED**, with a nuance on which settings file | (observations below; not committed) |
| P3-6 | two requests from one message | **the CLI serialises them**: the second is asked only after the first is answered | `p3-6-parallel-real.jsonl` |

### P3-0: the P3 line on the real bridge

**Isolated, at no cost** (`p3_lines_iso`): with the P3 default line `initialize.current_permission_mode` is `default`
and so is every `system/init.permissionMode`; the real bridge binds (`mcp_status` `connected`, `serverInfo.version`
`0.0.0-SNAPSHOT+bind.<nonce>` with exactly the nonce of the endpoint file); `init.tools` is **exactly** `Glob, Grep,
Read` plus **all nineteen** hosted verbs (`VerbSchemas.hosted()`: the eighteen public verbs and `propose`) — so the
posture assertion's expected set in P3 is the built-ins plus every hosted verb. In the full posture under `default`
and `acceptEdits` the hosted tools are the nineteen; under `auto` (sonnet; it held `auto` not logged in) and `plan` they
are the thirteen pre-allowed ones, the destructive six hidden. `mcp_servers` was only the hosted bridge in every line.

**Real login** (`p3_0_real`), three turns asking for `analyser_report`, each answered differently:

| Turn | The request | The answer | What followed |
|---|---|---|---|
| deny | `can_use_tool` for `mcp__fluxtion-analyser-hosted__analyser_report`, `mcp_server {name: fluxtion-analyser-hosted, source: dynamic}`, `display_name` *Analyser Report*, `input` as the model wrote it, `tool_use_id`, `permission_suggestions [addRules]` | `{behavior: deny, message: "Denied by the person in the analyser"}` | the model was told (its answer quoted the message); `permission_denials` lists the call; **the bridge received no `tools/call`** and the server no `/action` |
| allow | the same shape | `{behavior: allow, updatedInput: <the request's input>}` | the bridge's `tools/call` carried exactly that input; the server received one `report` with the hosted credential and **no shared token**; the model reported the result |
| changed (probe only) | the same shape, input `title: p3-zero-original` | allow with `updatedInput` whose `title` differs | **the bridge and the server received the host's `updatedInput`, not the model's `input`** (P3-3) |

### P3-1: unanswered

After the `can_use_tool` the CLI sent the message's `message_delta` and `message_stop` and one `rate_limit_event`, then
**nothing at all for 200 s** — no keep-alive, no timeout, no cancel, no `result`. A deny written after 200 s was
accepted at once: the tool call failed with the host's message (*"Not answered in the analyser within 120 s"*) and the
turn completed normally. **So the analyser's own 120 s deadline (OD-74.17) is the only thing that ends an unanswered
request**, and a request left pending holds the turn open indefinitely.

### P3-2: interrupt while pending

With a `can_use_tool` open, an `interrupt` control request produced, in this order: a **`control_cancel_request`
`{request_id}` naming the pending request**, the interrupt's `control_response` (`still_queued: []`), two `user` events
(the tool's error result), a `result` with `subtype error_during_execution`, `terminal_reason aborted_streaming`,
`permission_denials` listing the call, then lifecycle **`cancelled`** for the turn's uuid. An `allow` written after
that produced nothing: no tool call, no `/action`. The next turn in the same process worked. **So the CLI withdraws a
pending request itself on an interrupt**; the spec's order (deny first, then the interrupt, §4.5) is still kept — it
is harmless and does not depend on the CLI's behaviour — and the cancel is a new fact that closes the dialog.

### P3-3: what the bridge receives

Every `tools/call` from the CLI carried `params._meta = {"claudecode/toolUseId": "<the request's tool_use_id>",
"progressToken": n}` (2 of 2 executed calls; the denied call never reached the bridge), and `params.arguments` equal to
the host's `updatedInput` (above). **So a grant can bind to the `tool_use_id`**: the analyser's bridge forwards it to
the server, and the server's presentation digests the same id the request carried. This resolves §11's limit
*"Binding an approval to the tool_use_id depends on probe P3-3"*.

### P3-4: what asks in the default posture

Under `--restricted` with mode `default`: `Read`, `Grep` and `Glob` inside the folder ran with **no** `can_use_tool`;
the same three outside the folder were **denied outright** (`permission_denied`, reason type `other`, the
`--restricted` words) with no `can_use_tool`; an `@path` mention inside the folder was inlined and one outside was not
readable — again with no request. **Nothing but an MCP tool asks** in the default posture (the policy's "no other class
is expected" holds; anything else is a diagnostics line).

### P3-5: the full posture

Real login, the full line plus the harness's `--strict-mcp-config`, a scratch folder whose `.claude/settings.json` the
probe planted (never the person's):

| Mode | Bash (`touch <marker> && echo …`) | Edit inside the folder |
|---|---|---|
| `default` | **`can_use_tool`** (`tool_name: Bash`, `mcp_server: null`, input `{command, description}`, request also carries `blocked_path` and `description`; suggestions `addRules`, `addDirectories`, `setMode`); allowed → it ran (marker created) | **`can_use_tool`** (input `{file_path, old_string, new_string, replace_all}`; suggestion `setMode`); allowed → edited |
| `acceptEdits` | **`can_use_tool`**; denied → not run, `permission_denials [Bash]` | **no request**; edited (acceptEdits approves it) |

Hosted tools under both modes were all nineteen; no `mcp__fluxtion-analyser__…` tool appeared.

**The bypasses** (`p3_5b_real`): a planted `PreToolUse` hook (a script printing `permissionDecision: allow`) matching
`analyser_report`, and allow rules for `analyser_screenshot` and `Bash(echo P35RULE:*)` in the folder's
`.claude/settings.json` **and** `.claude/settings.local.json`. All three calls ran **with no `can_use_tool`**: the bridge
forwarded `report` and `screenshot` to the server, and the Bash command printed its marker. **This is why the grant is
server-side** (§5.7 item 5): without it, those two hosted destructive calls would have reached the analyser approved
by nobody in it. *Nuance:* in the first run the rule for `analyser_screenshot` was only in `.claude/settings.json`, the
hook had failed on a shell-quoting error of the probe's own, and that call **was** asked; with the rule in both files it
was not. Which file applied was not isolated; either way an allow rule can bypass the host, which is the case the
server-side grant exists for. The person's own allow rules were not read (only the settings file's hash).

Also seen in the full posture's `init.tools`: the person's built-ins include `AskUserQuestion`, `ToolSearch`,
`Workflow`, `Task…`, `Cron…` and `WebFetch`/`WebSearch` (26–29 names, vocabulary only). `ToolSearch` was called before
each hosted tool (deferred MCP tools), and asked nothing.

### P3-6: two requests from one message

The model put **both** tool uses (`analyser_report`, `analyser_screenshot`) in **one** assistant message (one message
id, two `tool_use` blocks). The CLI sent the first `can_use_tool`, and **no second one in the 8 s the harness waited**;
the second arrived only after the first was answered (allow → it ran), and was then answered deny → not run, the model
told. **The CLI asks one at a time**, so OD-74.17's "a second request while a dialog is open is denied" is a guard for a
case this CLI does not produce; it is kept, and its test uses a synthetic second request.

## Findings that change the spec (folded into r12)

1. **The grant binds to the `tool_use_id`** (P3-3): the bridge forwards `_meta["claudecode/toolUseId"]` as a header to
   the server; a grant matches only a call with the same id (when the call has none — an older CLI — the generation,
   ticket, verb and input digest still bind it, single-use and short-lived). §11's corresponding limit is removed.
2. **The server sees `updatedInput`** (P3-0, P3-3): the adapter answers allow with the stored input byte for byte, so
   the server's input digest is computed over what the person was shown.
3. **An unanswered request never ends on its own** (P3-1): the 120 s deadline is the analyser's alone, and a late answer
   is still accepted; the deadline is decided by the node from the adapter's clock fact.
4. **An interrupt withdraws a pending request** (P3-2) with `control_cancel_request{request_id}`: parsed, a fact, and the
   dialog closes; Stop still answers deny first.
5. **Requests are serialised by the CLI** (P3-6): one dialog at a time is the CLI's behaviour too; the busy denial stays
   as a guard.
6. **The default posture asks only for MCP tools** (P3-4), and **the default posture's expected tools in P3 are the
   built-ins plus all nineteen hosted verbs** (P3-0).
7. **In the full posture `default` asks for Bash and Edit, `acceptEdits` for Bash only** (P3-5); a hook or an allow rule
   bypasses the host, for the person's tools and for the hosted verbs alike.

## Reproduce

```
python3 docs/specs/evidence/m74-p3/p3.py --list
mvn -o -q package -DskipTests
python3 docs/specs/evidence/m74-p3/p3.py --workdir <scratch>/m74-p3 --jar target/fluxtion-auditlog-analyser-*.jar \
    --only p3_lines_iso
python3 docs/specs/evidence/m74-p3/p3.py --workdir <scratch>/m74-p3 --jar target/fluxtion-auditlog-analyser-*.jar \
    --i-accept-spend --only p3_0_real,p3_2_real,p3_4_real,p3_6_real,p3_5_real,p3_5b_real,p3_1_real
python3 docs/specs/evidence/m74-p3/p3.py --sanitise <scratch>/m74-p3/raw/<name>.jsonl fixtures/<name>.jsonl \
    --workdir <scratch>/m74-p3 --cwd proj
```

Then replace the JVM and jar paths with `<JAVA>` and `<JAR>`, restore `aggregate`/`aggregation` if the user name is a
substring of them, read every string, and delete `<scratch>/m74-p3` and the run's `~/.claude/projects/*scratchpad-m74-p3*`
folders.
