# M74 P1b — the full-posture probes PB-1…PB-4

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) r8, §8.1 (the probe table) and OD-74.18
(the method). **Date:** 2026-10-17. **CLI:** `2.1.282 (Claude Code)`, macOS (Darwin 25), native install.
**Model:** `haiku` (claude-haiku-4-5) except the `auto` turn of PB-1 on `sonnet` (PB-3 shows why).
**Spend:** **US$0.2433** by the probe's ledger (`--cap 1.5`; every real-login process ran with `--max-budget-usd 0.25`,
the PB-3 starts with `0.05`): PB-1's four turns US$0.0252 (`default`), 0.0167 (`acceptEdits`), 0.0699 (`auto`,
sonnet), 0.1315 (`plan`). Everything else ran under an isolated config and cost nothing. No run used
`bypassPermissions` or `dontAsk` on a full-posture line, and none named them; `/config` was not run.

**How it was run.** [`pb.py`](pb.py) (`--list` names the experiments), which reuses P0's helpers and P0b's hosted stub
(`STUB_B` in [`../m74-p0/probe.py`](../m74-p0/probe.py): `context` and `echo` stand for the pre-allowed verbs, `report`
and `approve` for hidden ones). Every experiment builds **§4.3's full line exactly** — no `--restricted`, `--tools`,
`--add-dir`, `--setting-sources`, `--settings`, `--safe-mode`, no `CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD` —
with `--disallowedTools "mcp__fluxtion-analyser,<hidden hosted verbs>"`, an explicit `--permission-mode`,
`--permission-prompts none`, `--max-budget-usd` and the full-posture note. Per OD-74.18:

- **Isolated** experiments (`*_iso`) set `CLAUDE_CONFIG_DIR` to a scratch folder and, for PB-2, `HOME` too, and plant
  the person's side there: user and project skills, commands, an agent, `SessionStart` hooks, a user-scope
  `fluxtion-analyser` registration (`claude mcp add --scope user`), and project `.mcp.json` servers. Not logged in:
  `initialize`, `mcp_status`, hooks and a turn's `system/init` all happen without a login.
- **Real-login** experiments (`*_real`) add **`--strict-mcp-config`** (the harness's addition, never the analyser's),
  so none of the person's servers or connectors loaded. The person's own settings, skills, plugins and hooks did load,
  as the full posture intends; their raw transcripts list those names and were **not** committed — only the
  observations below are.

**Capture and sanitising.** Only isolated captures are committed (`fixtures/`), through `pb.py --sanitise` (P0's
sanitiser, which rejoins split deltas). Every distinct string value in every fixture was read; the four-term sweep and
a grep for home paths, the user name, e-mail and token shapes came back empty. The hooks' outputs in
`pb-hooks-iso.jsonl` are planted markers (`HOOK-STDOUT-MARKER-…`), kept on purpose: the analyser's tests assert they
never reach its store or snapshot. The one `~/.claude/projects/` folder the real-login runs created (its name
containing `scratchpad-m74-p1b-real-proj`) was deleted; the folder list is identical to the one taken before the run,
the person's `~/.claude/settings.json` hash is unchanged and no `~/.claude/CLAUDE.md` was created. Raw transcripts and
the scratch folder were deleted.

## 1. Verdicts

| # | Question | Verdict | Evidence (fixtures/) |
|---|---|---|---|
| PB-1 | the four modes reported; `--permission-prompts none` denies without blocking; `mcp__fluxtion-analyser` hidden | **CONFIRMED** for `default`, `acceptEdits`, `plan`; **`auto` depends on the model** (PB-3) | `pb-mode-default-iso`, `pb-mode-plan-iso`, `pb-mode-auto-iso`, `pb-mode-witness-iso`; real turns below |
| PB-2 | hooks and stdio servers see the OD-74.11 `PATH` | **CONFIRMED**, with a failing control | (results below; the run's hook events are those of `pb-hooks-iso`) |
| PB-3 | is `auto` available, and what happens when not | **REFINES the spec**: model-dependent, not account-dependent; the CLI falls back to `default` **silently** | `pb-mode-auto-iso` (haiku → `default`) |
| PB-4 | the person's skills, commands and `CLAUDE.md` load under the full line | **CONFIRMED** (plugins not planted) | `pb-mode-default-iso` (`commands`, `skills`, `agents`) |
| — | hook event shapes, for the status lines | **RECORDED** | `pb-hooks-iso` |

### PB-1: modes, denial without blocking, the person's registration

**Isolated (no login), each mode on the full line:**

| `--permission-mode` | `initialize.current_permission_mode` | `system/init.permissionMode` |
|---|---|---|
| `default` | `default` | `default` |
| `acceptEdits` | `acceptEdits` | `acceptEdits` |
| `auto` (haiku) | **`default`** | **`default`** |
| `plan` | `plan` | `plan` |

In every mode the hosted tools in `init.tools` were exactly `mcp__fluxtion-analyser-hosted__context` and `…__echo` (the
two hidden ones absent), beside 26 of the person's built-in tools (`Bash`, `Edit`, `Write`, `Task`, `WebFetch`, …).
**The person's user-scope `fluxtion-analyser` registration** was spawned and `connected` (`mcp_status`), and **none of
its tools** was in `init.tools`. **Witness:** the same line without `mcp__fluxtion-analyser` in `--disallowedTools` lists
all four of them (`mcp__fluxtion-analyser__approve`, `…__context`, `…__echo`, `…__report`; `pb-mode-witness-iso`).

**Real login, one Bash-asking turn per mode** (`touch <marker> && echo PB1-BASH-<MODE>`, the marker in the run's scratch
folder; the full line plus `--strict-mcp-config`):

| Mode | Result | `permission_denied` | `control_request` | Marker | Time to `result` |
|---|---|---|---|---|---|
| `default` | turn completed, `permission_denials: [Bash]` | `tool_name: Bash`, `decision_reason_type: "asyncAgent"`, *"no approval surface in this session; permission request denied automatically"* | none | not created | 6.4 s |
| `acceptEdits` | the same | the same | none | not created | 5.6 s |
| `auto` (sonnet) | the classifier **allowed** it; output `PB1-BASH-AUTO` | none | none | **created** | 8.1 s |
| `plan` | the model declined on its own (*"plan mode is active, which restricts me to read-only actions"*); no tool call | none | none | not created | 10.0 s |

So `--permission-prompts none` **denies at once and never blocks** a request the mode would ask, and the analyser is
never asked (no `can_use_tool`), as §3 assumes for P1b and P2. The denial event is the one P1a already parses
(`PermissionDenied`); its reason type is `asyncAgent`, not `mode` as under `dontAsk` [P0b-1], which lets the tab say
*"needs your approval, which the analyser cannot ask for yet"* rather than *"denied by the posture"*. In every turn
`init.permissionMode` equalled the requested mode, the hosted tools were exactly the pre-allowed two, `mcp_servers` was
only the hosted bridge (strict), and no `mcp__fluxtion-analyser__…` tool appeared.

### PB-2: the child's `PATH` (OD-74.11)

Isolated config **and** isolated `HOME`. A marker binary `pb2-marker` in `<home>/.local/bin`; a user and a project
`SessionStart` hook that print `command -v pb2-marker`, `command -v npx` and `command -v node`; two project `.mcp.json`
servers: `pb2-npx`, launched as **`npx --offline --yes <local package>`** (the real `/opt/homebrew/bin/npx`, a
`#!/usr/bin/env node` script, run offline against a local package so nothing was fetched), and `pb2-envnode`, a bare
command name resolving to a `#!/usr/bin/env node` server in `<home>/.local/bin`.

| Child `PATH` | Hooks see | `pb2-npx` | `pb2-envnode` | Hosted bridge |
|---|---|---|---|---|
| the minimal GUI `PATH` only (`/usr/bin:/bin:/usr/sbin:/sbin`, the control) | `marker=MISSING npx=MISSING node=MISSING` (both hooks) | **failed** | **failed** | connected |
| GUI `PATH` + OD-74.11's appended directories that exist (`<home>/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`) | `marker=<home>/.local/bin/pb2-marker npx=/opt/homebrew/bin/npx node=/opt/homebrew/bin/node` (both hooks) | **connected** (0.98 s) | **connected** (0.72 s) | connected |

Both `SessionStart` hooks (user and project) ran at spawn, before any stdin line, and were reported on stdout. The
hosted bridge is launched by absolute path, so it is unaffected. **What it decides:** OD-74.11 is sufficient for the
common `npx`/`node` case from a Finder- or Dock-started analyser, and **without it the person's `npx` servers fail**
(`npx` is itself a `node` script, so `/opt/homebrew/bin` is needed for `env node` even when `npx` is found by path).
`$NPM_CONFIG_PREFIX/bin` and `~/.npm-global/bin` did not exist here and were not exercised.

### PB-3: is `auto` available?

`--permission-mode auto`, start only (`initialize`, `mcp_status`, no turn), so free even on the real login:

| Login | `--model` | `initialize.current_permission_mode` |
|---|---|---|
| real | `haiku` | **`default`** |
| real | `sonnet` | `auto` |
| real | `opus` | `auto` |
| isolated (not logged in) | `haiku` | **`default`** |
| isolated (not logged in) | `sonnet`, `opus` | `auto` |

**`auto` depends on the model, not the account or an opt-in**: with `haiku` the CLI starts in `default`, with no error,
no stderr line and no event saying so; with `sonnet` or `opus` it holds, logged in or not (`auto-mode-setup` was not
needed). PB-1's real `auto` turn, on `sonnet`, was in `auto` throughout. **What it decides:** §4.4's mode check already
catches the fallback (`initialize` reports `default` where the line set `auto`, so the verdict is `MODE_MISMATCH` and
the process is stopped before any turn); the words (§8.1's failure mode) name the cause the evidence shows: *"Claude
Code started in default mode instead of auto: auto mode is not available for the configured Claude Code model —
choose a model that supports it (sonnet or opus) or another mode in Settings ▸ Assistant."* Settings' note for `auto`
says the same. The person's folder and user hooks have already run by then (they run at spawn), which the warning
covers.

**Also observed:** CLI 2.1.282's `--help` lists the modes as `acceptEdits, auto, bypassPermissions, manual, dontAsk,
plan` — **`default` is not listed, but is accepted**, and `--permission-mode manual` reports `default` too (an unknown
value is refused at once: *"argument 'bogus' is invalid. Allowed choices are …"*, exit 1). The analyser keeps passing
`default`, the value `initialize` and `system/init` report and that P0b's P3 line ran; a CLI that drops the alias would
exit at once with that message, shown as the process's last stderr line.

### PB-4: the person's skills, commands, agents and `CLAUDE.md`

Isolated config with a planted user skill, project skill, user command, project command and user agent:

| | full line | default posture (control: `--restricted --tools … --strict-mcp-config`) |
|---|---|---|
| `initialize.commands`, `builtin:false` | `pb4-user-cmd`, `pb4-proj-cmd`, **and the two skills** (`pb4-user-skill`, `pb4-proj-skill`) | none |
| `initialize.commands`, `builtin:true` | 47 | 47 |
| `system/init.skills` | `pb4-user-skill`, `pb4-proj-skill` among the bundled ones | none of them |
| `system/init.agents` | includes `pb4-agent` | — |
| `system/init.permissionMode` | `default` | `dontAsk` |

A person's skills are also listed as `builtin:false` commands, so §5.4's *the person's own* row covers them (they are
sent; a name the table classifies stays the table's). **`CLAUDE.md`:** `system/init.memory_paths` lists only the
auto-memory folder, not `CLAUDE.md` files, so it is not a free check; the folder's `CLAUDE.md` codeword came back in
**all four** real-login turns of PB-1 with **no** env flag, as §4.3 says. **Plugins** need a marketplace install and
were not planted; their loading under the full line is not probed (the real-login `system/init` listed the person's
plugins, so they load, but which of their skills and hooks run was not separated out).

### Hook events (the status lines' input)

`pb-hooks-iso.jsonl`: a user `SessionStart` hook that succeeds and a project `SessionStart` hook that fails:

```
{"type":"system","subtype":"hook_started","hook_id":"…","hook_name":"SessionStart:startup","hook_event":"SessionStart",…}
{"type":"system","subtype":"hook_response","hook_id":"…","hook_name":"SessionStart:startup","hook_event":"SessionStart",
 "output":"HOOK-STDOUT-MARKER-FAIL\nHOOK-STDERR-MARKER-FAIL\n","stdout":"…","stderr":"…","exit_code":1,"outcome":"error",…}
```

`outcome` is `success` or `error`; `hook_response` carries the hook's **output, stdout and stderr**, which the analyser
must never store or show (§4.4 step 1). Both hooks are reported before any stdin line. With no login, the project's
`UserPromptSubmit` hook did not fire on the failed turn; per-turn hook events under a login were not separated out.

## 2. What changes in the spec

1. **`auto` is model-dependent** (PB-3): §5.1's mode list, §8.1's failure mode and §9 get the words above; the
   mode check of §4.4 is unchanged and is what catches it.
2. **The P1b denial is `asyncAgent`, not `mode`** (PB-1): the tab's note in the full posture says the request needed
   the person's approval, which the analyser cannot ask for until P3, instead of *"denied by the posture"*.
3. **Hook output is in the event** (hook shapes): the adapter reads only `hook_event` and `outcome` at the parse
   boundary; `output`, `stdout` and `stderr` have no field in the parsed record.
4. **`default` is an accepted, unlisted alias of `manual`** (PB-3, also observed): recorded, no change to the line.

## 3. Reproduce

```
python3 docs/specs/evidence/m74-p1b/pb.py --list
python3 docs/specs/evidence/m74-p1b/pb.py --workdir <scratch>/m74-p1b \
    --only pb2_path_iso,pb4_loading_iso,pb1_modes_iso,pbx_hooks_fixture_iso          # isolated, free
python3 docs/specs/evidence/m74-p1b/pb.py --workdir <scratch>/m74-p1b --i-accept-spend --cap 1.5 \
    --only pb3_auto_real,pb1_bash_real                                                # real login, about US$0.25
python3 docs/specs/evidence/m74-p1b/pb.py --sanitise <scratch>/m74-p1b/raw/<name>.jsonl fixtures/<name>.jsonl \
    --workdir <scratch>/m74-p1b --cwd <subdir>
```

`pb3_auto_real` with `--model sonnet` / `--model opus` gives PB-3's other rows. Real-login runs refuse to start without
`--strict-mcp-config`. Delete `<scratch>/m74-p1b` and `~/.claude/projects/*scratchpad-m74-p1b*` afterwards.
