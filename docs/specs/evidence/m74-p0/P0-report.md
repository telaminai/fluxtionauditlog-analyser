# M74 P0 — protocol probe report

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) (DRAFT r3), §8 row P0.
**Date:** 2026-10-16. **CLI:** `claude --version` → `2.1.282 (Claude Code)`, macOS (Darwin 25), native install.
**Model:** `haiku` (claude-haiku-4-5) for every live run. **Spend:** ≈ US$0.85 by the CLI's own figures (sum of each
session's final `total_cost_usd`, 71 sessions) plus a few cents for turns killed before a `result`; the probe's ledger,
which deliberately over-counts resumed sessions, says US$1.24. Cap was US$5; every process ran with `--max-budget-usd`.
No run used `bypassPermissions`.

**How it was run.** [`probe.py`](probe.py) is the reproducible script (`--list` names the experiments; it refuses to
run without `--i-accept-spend`, takes `--model`, `--budget`, `--cap`). A first exploratory pass was followed by a
script pass that reproduced every live result below except compaction (not re-run: the most expensive item). The
fixtures in [`fixtures/`](fixtures) are sanitised copies of real transcripts (`probe.py --sanitise`): session ids →
`00000000-0000-4000-8000-00000000000n`, paths → `<CWD>`/`<SCRATCH>`/`<HOME>`, the person's own skill and command names →
`<skill-n>`/`<slash_command-n>`, account email/organisation/plan → placeholders, rate-limit utilisation zeroed, product
prose in `initialize` → `<description>`; costs and token counts are kept as numbers.

**Isolation.** An isolated `CLAUDE_CONFIG_DIR` **or** `HOME` does **not** keep authentication (both answer *Not logged
in*), so live fixtures were captured under the real login, always with `--strict-mcp-config` (the person's own MCP
servers and claude.ai connectors never loaded) and sanitised before commit. Experiments that only need the CLI's
start-up behaviour (hooks, `.mcp.json`, name collision, not-logged-in, API-key source, `initialize`) ran under the
isolated config, at no cost. The MCP experiments used a 40-line Python stdio server (tools `echo`, `add`, `sleep`) under
several names, including `fluxtion-analyser`; no analyser was started or contacted.

## 1. Checklist

Verdicts: **CONFIRMED** (the spec's assumption holds), **REFUTED** (it does not), **PARTIAL**, **NOT TESTED**.
"Breaks" names the decision a finding reopens; §2 says how.

| # | Item | Verdict | Evidence | Consequence for the spec |
|---|---|---|---|---|
| 1 | stdin turn shape | CONFIRMED | `multiturn-partial-replay-midturn.jsonl` | One line per turn: `{"type":"user","message":{"role":"user","content":"<text>"},"parent_tool_use_id":null,"uuid":"<fresh uuid>"}`. `session_id` may be omitted. |
| 2 | many turns over one process | CONFIRMED | same | Four turns, one process, exit 0. |
| 3 | one `result` per turn | CONFIRMED | same | Every turn ends in exactly one `result` — **except** a turn whose message uuid is already in the session (#27). `result.total_cost_usd` is cumulative for the **session**, not per turn (#22). |
| 4 | `system/init` fields | CONFIRMED, with a twist | same | Keys: `agents, analytics_disabled, apiKeySource, capabilities, claude_code_version, cwd, fast_mode_disabled_reason, fast_mode_state, mcp_servers[{name,status,source}], memory_paths, messaging_socket_path, model, output_style, per_turn_effort_active, permissionMode, plugins[{name,path,source}], product_feedback_disabled, session_id, skills[], slash_commands[], terminal_slash_commands[], tools[], uuid, view_mode`. **It is emitted at the start of every turn, not at process start** — apart from hook events, nothing about the session is on stdout until the first Send. The header at Start must come from the `initialize` control request (#5). |
| 5 | control protocol at Start (new) | CONFIRMED | `control-initialize.jsonl`, `control-mcp-status.jsonl` | `{"type":"control_request","request_id":"…","request":{"subtype":"initialize"}}` answers before any turn, at no cost: `account{apiProvider, email, organization, subscriptionType}` (not logged in: `{tokenSource:"none", apiProvider}`), `current_permission_mode`, `models[]`, `commands[]`, `agents[]`, `pid`, `session_state`. **`account.email` and `organization` are personal data**: they must never reach the transcript store, the audit record or a bundle. `mcp_status` likewise answers at Start. |
| 6 | `apiKeySource` | REFUTED as a "who pays" signal | `multiturn…`, `inherited-invalid-api-key.jsonl` | Under a claude.ai subscription login it is **`"none"`**; with an inherited `ANTHROPIC_API_KEY` it is `"ANTHROPIC_API_KEY"`. The header's *billed via* needs `initialize.account` (subscription type) as well — §4.3, §5.3. |
| 7 | `--verbose` required | CONFIRMED | `probe.py` `verbose_required` | Without it: exit 1, `Error: When using --print, --output-format=stream-json requires --verbose`. |
| 8 | `--include-partial-messages` deltas | CONFIRMED | `multiturn…`, `interrupt-still-queued.jsonl` | `stream_event` wrapping Messages-API events: `message_start`, `content_block_start/delta/stop` (`thinking_delta`, `signature_delta`, `text_delta`), `message_delta`, `message_stop`; also `system/thinking_tokens` estimates. The full `assistant` message follows each block — coalesce, never render twice. |
| 9 | `--replay-user-messages` correlation | CONFIRMED | `multiturn…` | The sent line comes back with the **same `uuid`**, `isReplay:true`, a timestamp. Better still, `command_lifecycle` events (`queued → started → completed / cancelled`, keyed by `command_uuid` = our uuid) arrive with or without replay: use them for the turn ticket. |
| 10 | a turn sent mid-turn | CONFIRMED (queued) | `multiturn…` | Lifecycle `queued`; it runs as a separate turn after the current one, with its own `result`. It does not interrupt or merge. |
| 11 | stream-json **interrupt** | CONFIRMED — exists and works | `interrupt-still-queued.jsonl`, `interrupt-cancel-queued-during-tool.jsonl`, `interrupt-hits-next-turn-race.jsonl` | `{"type":"control_request","request_id":"r1","request":{"subtype":"interrupt"}}` → `control_response{subtype:success, response:{still_queued:[uuids]}}`; the running turn ends `result{subtype:"error_during_execution", terminal_reason:"aborted_streaming"\|"aborted_tools"}`, lifecycle `cancelled`; the process stays alive and takes the next turn. `"cancel_queued":true` also cancels queued turns (`response.cancelled:[uuids]`). Advertised by `init.capabilities` `interrupt_receipt_v1`, `interrupt_cancel_queued_v1`. The interrupt names no turn: it stops whatever is running, which may already be the *next* queued turn (race fixture) — map it back through lifecycle `cancelled`. **§5.5's Stop uses this; killing the process is only the fallback.** |
| 12 | automatic compaction headless | CONFIRMED | `autocompact-headless.jsonl` | With `--autocompact 100k` and a ~100k-token turn: `system/status` `compacting` (twice `compact_result:"failed", compact_error:"too_few_groups"`, then `success`), `system/compact_boundary{compact_metadata:{trigger:"auto", pre_tokens, post_tokens,…}}`, a synthetic summary user message; recall survived. Cost of the experiment ≈ US$0.23. Default threshold (without `--autocompact`) not measured. |
| 13 | `/compact` and skill commands over stream-json | CONFIRMED | `skill-and-manual-compact.jsonl` | Sending the text `/compact` compacts (`compact_boundary` `trigger:"manual"`, `<local-command-stdout>Compacted </local-command-stdout>`, a `result` with empty text); `/probe-skill` ran a project skill. So **typed slash commands are live** in the Assistant tab — the analyser must decide whether to pass them (§5.4). `--disable-slash-commands` exists. |
| 14 | `--session-id <uuid>` | CONFIRMED | `multiturn…` | Works; reusing an id that already has a transcript fails: exit 1, `Error: Session ID … is already in use.` — New chat must mint a fresh uuid and Resume must use `--resume`. |
| 15 | `--resume` after kill | CONFIRMED | `probe.py` `resume` | After SIGTERM, `--resume <id>` recalls the conversation. |
| 16 | `--resume` from another cwd | CONFIRMED | `resume-other-cwd.jsonl` | Works by id from any directory; tools run in the new cwd; the transcript keeps being appended to the **original** cwd's project folder (`~/.claude/projects/<encoded original cwd>/<id>.jsonl`), not the new one. |
| 17 | `--resume` with a changed `--append-system-prompt` | REFUTED — the change is ignored | `resume-changed-append-prompt.jsonl` | Resumed with "colour is RED", answered TEAL. The system prompt is recorded on the first request and reused (`--system-prompt-snapshot on` is the default). **Breaks** the assumption behind §3's system note and OD-74.3 *Keep*: a revised note never reaches a resumed session. Pass `--system-prompt-snapshot off`, or treat the note as fixed per session and say so. |
| 18 | `--resume` with a changed `--mcp-config` | CONFIRMED | `probe.py` `resume` | The new server set takes effect (`init.mcp_servers` shows only the new server). Instance rebinding on resume is possible. |
| 19 | resume a headless session interactively, and the reverse | PARTIAL | session-file listing | Headless sessions are written to the same store as interactive ones (`~/.claude/projects/<encoded cwd>/<id>.jsonl`, entries tagged `entrypoint:"sdk-cli"`), so the interactive `/resume` picker in that directory should list them; resume-by-id is cwd-independent (#16), so a terminal session can be resumed headless by id. The picker itself (a TTY) and resuming one of the person's real interactive sessions were deliberately not exercised. |
| 20 | the same session opened twice | CONFIRMED hazard | `concurrent-open-a/b.jsonl`, `concurrent-after-fork.jsonl` | Two processes `--resume` the same id concurrently with **no lock or warning**; both answer; the history forks and a later resume sees only the last writer's branch (BRAVO, not ALPHA). The analyser must hold its own per-session lock, and cannot stop an external terminal doing the same. |
| 21 | `dontAsk` + `--permission-prompts none` deny-not-block | CONFIRMED | `tools-allowlist-dontask-denials.jsonl`, `mcp-allowedtools-explicit.jsonl` | A non-allowed call returns a tool error at once (`Permission to use X has been denied because Claude Code is running in don't ask mode…`), the turn completes, and `result.permission_denials[{tool_name, tool_use_id, tool_input}]` lists it; `system/permission_denied` events carry `decision_reason_type`. Never blocks. |
| 22 | `--max-budget-usd`, per process or per session | CONFIRMED per process | `budget-exhausted-per-process.jsonl` | The cap counts **this process's** spend. The turn that crosses it ends `result{subtype:"error_max_budget_usd", is_error:true, terminal_reason:"budget_exhausted", errors:["Reached maximum budget ($…)"]}` with an **empty `result` text** although the `assistant` event carried the answer; every later turn is refused the same way; the process stays alive; exit 1 at EOF. **But `total_cost_usd` is cumulative per session across resumes** (restored from the transcript): the node's session total (R2-8) is the last `total_cost_usd` of a session, summed across New chat — not a sum of per-turn values. An interrupted turn once reported `total_cost_usd: 0` (first pass) and once a real figure (script pass): treat an interrupted turn's cost as possibly under-reported. |
| 23 | rate-limit events | PARTIAL | `multiturn…` | `rate_limit_event{rate_limit_info:{status:"allowed", rateLimitType:"five_hour", resetsAt, overageStatus, isUsingOverage, unifiedWindows:{five_hour,seven_day}}}` after the first turn. A rejected/limited state was not provoked. |
| 24 | `--tools "Read,Grep,Glob"` allow-list; subagents | CONFIRMED | `tools-allowlist-dontask-denials.jsonl` | `init.tools` is exactly `["Glob","Grep","Read"]`: no Bash, Write, Agent/Task, Skill, Web*. `init.agents` still lists agent *definitions*, but with no Agent tool none can be spawned, so no subagent can widen the set. |
| 25 | Read/Grep/Glob under `dontAsk` | CONFIRMED | same | In-cwd Read/Glob/Grep run without a rule. Out-of-cwd Read is **denied even without `--restricted`** unless an allow rule exists. |
| 26 | `--restricted` and out-of-directory reads despite an allow rule | CONFIRMED | `restricted-outside-read-denied.jsonl`, `restricted-grep-symlink-denied.jsonl` | With `--settings '{"permissions":{"allow":["Read(//**)"]}}'` (applies even under `--restricted`, so stronger than a user-file rule, which `--restricted` ignores anyway): without `--restricted` the outside file is read; with it, Read and Grep are refused (`decision_reason: "--restricted: path outside the working directory"`), and a **symlink** inside the cwd that points outside is refused too. **But `--restricted` also drops CLAUDE.md, user/project/local settings and user skills** — see #28; this **breaks** §1/§3/OD-74.5 as written. |
| 27 | message-uuid reuse (new) | REFUTED (silently dropped) | `resume-reused-message-uuid-dropped.jsonl` | A user line whose `uuid` already exists in the session is accepted (`command_lifecycle … completed`) but never runs: **no `result`**. The parser must end a turn on lifecycle `completed`/`cancelled` even without a `result`, and tickets must be fresh uuids. |
| 28 | untrusted directory: project hooks and `.mcp.json` under `-p` | CONFIRMED hazard | `untrusted-hooks-and-project-mcp.jsonl`, `probe.py` `untrusted_dir`, `claudemd` | Under `-p` a project's `.claude/settings.json` hooks **run at process start, before any stdin line** (`SessionStart` fired within seconds of spawn; `UserPromptSubmit` on the first turn), and a project `.mcp.json` server is **spawned and connected without approval** — with or without `enableAllProjectMcpServers`. Hooks are reported on stdout (`system/hook_started`, `system/hook_response`). What stops them: `--strict-mcp-config` stops `.mcp.json` but **not hooks**; `--setting-sources user`, `--safe-mode` and `--restricted` stop both. CLAUDE.md loads only with the `project` source: lost under `--setting-sources user`, `--safe-mode`, `--restricted`; **restored under `--restricted` by `--add-dir <cwd>` plus env `CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD=1`**, which still runs no hooks and no `.mcp.json`. **Consequence:** OD-74.5's confirmation must happen before Start (as written) — in the full posture Start itself executes the folder's hooks. In the default posture nothing of the folder runs except CLAUDE.md, if the analyser adds it. |
| 29 | `--strict-mcp-config` excludes user and connector servers | CONFIRMED | `probe.py` `collision`; every live fixture | Isolated config with two user-scope servers: without strict both load; with strict only `--mcp-config` servers. Under the real login every strict run shows `mcp_servers` with only the probe's own servers — none of the person's user servers or claude.ai connectors. |
| 30 | `--allowedTools` explicit names; wildcard over-match | CONFIRMED (no over-match) | `mcp-allowedtools-explicit.jsonl`, `mcp-allowedtools-wildcard-exact-server.jsonl`, `mcp-server-allow-and-disallow.jsonl` | `mcp__fluxtion-analyser-hosted__echo` allows exactly that tool. `mcp__fluxtion-analyser__*` matches only server `fluxtion-analyser`, **not** `fluxtion-analyser-hosted`; a server-level `mcp__fluxtion-analyser-hosted` allows all its tools; `--disallowedTools mcp__fluxtion-analyser` removes only that server's tools from the model's list (its process is still spawned). The full-posture disallow in §3 is safe. |
| 31 | server-name collision with M42's `fluxtion-analyser` | CONFIRMED (silent shadowing) | `probe.py` `collision` | A `--mcp-config` server with the same name as a user registration **replaces it silently**, strict or not (`source:"dynamic"`; the user-registered process never starts). The distinct name `fluxtion-analyser-hosted` (§4.2) stands: it also keeps the person's own permission rules for `mcp__fluxtion-analyser…` from applying to the hosted bridge. |
| 32 | bridge binding verified from `system/init` / server status | REFUTED | `control-mcp-status.jsonl`; source of the bridge | `init.mcp_servers` carries only `{name,status,source}`. The `mcp_status` control request carries `serverInfo` but **only `{name, version}`** — the stub's extra `boundEndpoint` field was dropped. Right after spawn a server may still be `pending` (`control-mcp-status-pending-not-logged-in.jsonl`): poll. **Breaks §4.2**: the binding must be encoded in `serverInfo.name`/`version` or proven by a tool result (e.g. `context` returning the instance id). |
| 33 | older JBang bridge given the new endpoint-path argument | CONFIRMED by source (not executed) | `Main.main` → `McpBridge.main(args)`; `McpBridge()` uses `RestEndpointFile.wellKnown()` | Today's bridge ignores every argument after `--mcp` and binds to the well-known endpoint file, i.e. whichever analyser wrote it last. An old bridge therefore connects silently to the wrong instance; the verification in #32 is mandatory, and an absent binding must refuse the session. |
| 34 | explicit `--permission-mode` vs settings `defaultMode`; reported in init | CONFIRMED (settings); NOT TESTED (managed) | `permission-mode-explicit-over-settings.jsonl` | `--settings '{"permissions":{"defaultMode":"acceptEdits"}}'` alone → `init.permissionMode:"acceptEdits"`; adding `--permission-mode dontAsk` → `"dontAsk"`. `initialize.current_permission_mode` reports it at Start. Managed (admin) settings need a root-owned policy file: not tested. |
| 35 | stdin EOF ends the process | CONFIRMED, with a caveat | `stdin-eof-mid-turn.jsonl`, `probe.py` `process_tree` | Idle: exit 0 in < 1 s. **Mid-turn: the turn runs to completion first** (a 25 s tool call finished, `result` emitted, then exit 0). EOF is not a Stop. |
| 36 | process tree after SIGTERM | CONFIRMED, plus a SIGKILL hazard | `probe.py` `process_tree` | SIGTERM (exit 143, < 2 s) removes the MCP child **even one that ignores SIGTERM**. **SIGKILL orphans it** (re-parented, so it is no longer a descendant). §4.4: snapshot `descendants()` *before* killing, prefer SIGTERM-then-wait, then kill the snapshot. |
| 37 | not logged in | PARTIAL | `not-logged-in.jsonl`, `control-mcp-status-pending-not-logged-in.jsonl` | Under an isolated config: `system/init` (`apiKeySource:"none"`), then `result{subtype:"success", is_error:true, terminal_reason:"api_error", result:"Not logged in · Please run /login"}`, exit 1, empty stderr, within about a second; no login flow started and nothing waited for input. Note `subtype` is `success` — use `is_error`. Detectable at Start for free: `initialize.account.tokenSource:"none"`. That no browser opened was not observed directly (no screen capture). |
| 38 | `PATH` a GUI-launched app sees | REFUTED for the current resolver | `probe.py` `path` | `claude` is `<HOME>/.local/bin/claude` → `<HOME>/.local/share/claude/versions/2.1.282`. With macOS's minimal GUI `PATH` (`/usr/bin:/bin:/usr/sbin:/sbin`; `launchctl getenv PATH` is empty) it is **not found**, and `ClaudeMcpClient.findExecutable` reads only `System.getenv("PATH")`. **Breaks §4.3** ("PATH resolved as ClaudeMcpClient does") for an analyser started from Finder/Dock: add known install locations (`~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`, the npm global prefix) or a login-shell lookup, and show the path searched (§9). |
| 39 | Windows: `claude.cmd`, signals, inline JSON through `cmd.exe` | NOT TESTED | — | No Windows host. Must test: whether the native installer still uses a `.cmd` shim or an `.exe`; inline `--mcp-config` JSON and `--append-system-prompt` text through `cmd.exe` quoting (`"`, `&`, `\|`, `^`, `%`) versus a private temp file; Stop via the interrupt control request (no SIGTERM on Windows) and tree kill (`taskkill /T` semantics vs `ProcessHandle.descendants()`); `--restricted` path confinement with drive letters, UNC paths and junctions; where `~/.claude/projects` lives. |
| 40 | minimum CLI version | PARTIAL | `init.claude_code_version`, `init.capabilities` | Only 2.1.282 was available. Set the floor at the tested version for P1a and feature-detect rather than version-sniff: require `interrupt_receipt_v1` and `msg_lifecycle_v1` in `init.capabilities` (or `initialize`), and refuse with the §9 wording when absent. Older versions untested. |

## 2. Findings that break or change a decision

1. **`--restricted` removes CLAUDE.md, settings and user skills (#26, #28) — §1, §3 (default posture), OD-74.5.**
   `--restricted` is what confines Read/Grep/Glob to the folder (and it does so robustly, symlinks included), but it
   also ignores user, project and local settings and drops CLAUDE.md and the person's own skills (31 → 18 in the probe
   account). The default posture therefore loses most of what §1 says Claude Code adds. Two clean options for r4:
   (a) keep `--restricted` and restore CLAUDE.md with `--add-dir <project root>` plus
   `CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD=1` (verified: CLAUDE.md loads, hooks and `.mcp.json` still do not); or
   (b) drop `--restricted` and rely on `dontAsk` with no allow rule (out-of-cwd reads were denied without it, #25) —
   weaker, because a user-settings allow rule such as `Read(//**)` would then apply. (a) is the stronger posture.
   OD-74.5's dialog text ("will load this folder's settings, hooks and CLAUDE.md") is then only true for the full
   posture; in the default posture it is "CLAUDE.md".
2. **Project hooks run at Start; project `.mcp.json` is auto-approved under `-p` (#28) — OD-74.5, §7 "Network
   transmission starts with Send".** In the full posture, pressing Start executes the folder's `SessionStart` hook and
   spawns its MCP servers before any Send, without the CLI's trust dialog (skipped under `-p`, as `--help` says). The
   folder confirmation must stay strictly before Start (it is), its warning must name hooks and MCP servers explicitly,
   and §7's row should say Start may also run the folder's hooks in the full posture. `--strict-mcp-config` alone does
   not stop hooks.
3. **Binding cannot be read from `system/init`, and extra `serverInfo` fields are stripped (#32, #33) — §4.2.**
   Encode the instance in `serverInfo.name` or `version`, or verify it from the first tool result (e.g. `context`
   reporting the instance id); an old JBang bridge silently binds to the well-known endpoint, so a missing proof must
   refuse the session.
4. **The system note is frozen at session creation (#17) — §3 System note, OD-74.3 *Keep*.** A resumed session keeps
   the note it was created with. Either pass `--system-prompt-snapshot off` (renders afresh each request — costs prompt
   cache) or state that changing the note needs New chat.
5. **`system/init` arrives per turn, not at Start (#4) — §4.1 `LocalAgentStarted{… model, cliVersion, apiKeySource}`,
   §5.3 header.** Use the `initialize` control request at Start (free, no model call) for model list, permission mode,
   account; `system/init` then confirms per turn. `initialize.account` contains the account email and organisation:
   keep it out of every fact, transcript and bundle (only `apiProvider` / `subscriptionType` / `tokenSource` are needed).
6. **`apiKeySource` is `"none"` for subscription logins (#6) — §4.3, §5.3 *billed via*.** Derive the billing line from
   `initialize.account` (`subscriptionType` vs `apiKeySource:"ANTHROPIC_API_KEY"`).
7. **Stop uses the stream-json interrupt (#11) — §5.5 as anticipated.** Process stop becomes a fallback. The interrupt
   targets whatever turn is running, so the node maps the cancellation through `command_lifecycle`, not through its own
   idea of the current ticket.
8. **Turn tickets: use `command_lifecycle`; never reuse a uuid (#9, #27) — §4.1 facts.** A reused uuid yields no
   `result`; a turn must also end on lifecycle `completed`/`cancelled`.
9. **Budget arithmetic (#22) — §3 Budget [R2-8].** The CLI's cap is per process (confirmed), but `total_cost_usd` is
   already a per-session cumulative that survives resume; the node keeps the last value per session and sums across
   sessions. Budget exhaustion leaves the process alive and refusing: the node should stop it. Interrupted turns may
   under-report.
10. **Concurrent opens are not prevented (#20) — §4.1 one-mode rule.** Add a per-session lock in the node and a
    warning that an external terminal resuming the same id forks the conversation.
11. **SIGKILL orphans MCP children (#36) — §4.4.** Snapshot descendants before the kill.
12. **GUI `PATH` (#38) — §4.3, §9.** `ClaudeMcpClient`'s resolver needs fallbacks for an app not started from a shell.
13. **Typed slash commands are executed (#13) — §5.4.** `/compact` and skills work over stream-json, which is useful;
    decide whether the Assistant tab passes `/…` through (and say so) or adds `--disable-slash-commands`.

No finding contradicts OD-74.1–74.4, the allow-list choice (`--tools`), `dontAsk` + `--permission-prompts none`, the
explicit pre-allow list without wildcards, `--strict-mcp-config`, or the distinct hosted server name.

## 3. Not tested, and why

- **Windows** (#39): no Windows host.
- **Managed settings precedence** (#34): needs a root-owned policy file; not created on the owner's machine.
- **Interactive resume picker** (#19): a TTY UI; and resuming one of the person's real interactive sessions headless
  was avoided deliberately.
- **Rate-limit rejection** (#23): not provokable without exhausting the account.
- **Older CLI versions** (#40): only 2.1.282 installed; not downgraded.
- **Browser on not-logged-in** (#37): no screen observation; the process returned at once with nothing on stderr.
- **Older JBang bridge** (#33): established from source, not by running a published bridge.
- **Default auto-compact threshold** (#12): only the explicit `--autocompact 100k` window was exercised.

## 4. Reproduce

```
python3 docs/specs/evidence/m74-p0/probe.py --list
python3 docs/specs/evidence/m74-p0/probe.py --i-accept-spend --model haiku --budget 0.25 --cap 5
python3 docs/specs/evidence/m74-p0/probe.py --i-accept-spend --only interrupt,process_tree
python3 docs/specs/evidence/m74-p0/probe.py --sanitise <workdir>/raw/<name>.jsonl fixtures/<name>.jsonl --workdir <workdir> --cwd trusted
```

Raw transcripts stay in the temporary work directory and are sensitive (they hold the person's skill names, paths and,
for `initialize`, the account email); commit only sanitised copies, read them first, and run the rule-1 sweep.

Side effect to know about: live runs write session transcripts for the probe's temporary directories under the real
`~/.claude/projects/`, where `claude --resume` in those directories would list them; they can be deleted with the
work directory.
