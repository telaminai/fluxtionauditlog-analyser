# M74 P1b — live smoke of the full posture through the Assistant tab

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) r9, §8.1 *Live smoke (P1b)*.
**Date:** 2026-10-17. **CLI:** `2.1.282 (Claude Code)`, macOS, native install, the person's real login.
**Branch:** `feature/claude-code-p1b` at `c4e4b729`. **Model:** `haiku`, and `sonnet` for the `auto` turn (PB-3).
**Spend:** not recorded exactly — the harness stopped (below) before it printed the session total; the run made 11
model turns (10 on haiku, 1 on sonnet), which at PB-1's measured rates is **about US$0.3–0.6**. The analyser's own
limit was set to US$2 as a runaway guard (*Limit spend per session*, OD-74.24), so `--max-budget-usd` was on every
line. No run used `bypassPermissions` or `dontAsk`.

## How it was run

As the P1a smoke ran, under OD-74.18's method: the branch's shaded jar with [`harness/M74P1bSmoke.java`](harness/M74P1bSmoke.java)
added, run as a separate JVM with `user.home` and `java.io.tmpdir` under the run's scratch folder and REST on. The
harness opened a project in a planted folder (a `SessionStart` hook in `.claude/settings.json` writing a marker
file, a `.mcp.json` stub server `smoke-stub`, a `CLAUDE.md`), opened `src/test/resources/sample.yml` (21 records),
and pressed the application's own controls: *Settings* and its full switch and warning, *Claude Code*, *Start*, the
folder dialog's buttons, *Send*, *Stop*, *New chat*, *In-app assistant*. It read only the published snapshot and the
bounded store.

- **`--strict-mcp-config` came from the harness, never the analyser**: a `claude` wrapper first on the JVM's `PATH`
  execs the real CLI with the analyser's argument vector plus `--strict-mcp-config`. The process table shows the
  analyser's line without `--restricted`, `--tools` or `--add-dir`, and the wrapper's flag at the end. Consequence: the
  planted `.mcp.json` server could not load in this run (strict mode stops it); its loading is PB-2's isolated
  evidence.
- **The JVM ran with every `CLAUDE*` variable of the host session removed** (see *Findings*, 1).
- **Cleanup.** The one `~/.claude/projects/` folder the run created (its name containing `scratchpad-m74-p1b-smoke`)
  was deleted; the folder list equals the one taken before the run, and the person's `~/.claude/settings.json` hash is
  unchanged. Raw events and the scratch folder were deleted after this report was written.

In the excerpts `<SMOKE>` is the run's scratch folder and `<subscription>` the account's plan.

## Verdicts (§8.1's checklist)

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 1 | the warning on enabling; Cancel leaves it off; enabling records the mode | **PASS** | the warning (§5.1's text) with *Cancel* the default button; Cancel → switch off, mode hidden; *Turn on full Claude Code* → switch on, the mode shown (`default`); Settings ▸ OK saved `full=true`, `mode=default` |
| 2 | the unconfirmed planted folder: the dialog names the hook and the server; No starts nothing; Yes starts; the hook's line before the first Send | **PASS** | *"Hooks in this folder: SessionStart (1) — from .claude/settings.json · MCP servers in this folder: smoke-stub — from .mcp.json"*, in a plain-text area; *Don't trust* → `OFF`, no process; *Trust* → `BOUND` 0.9 s later; the note *"Hook SessionStart: 1 ran"* and the marker file both present before any Send |
| 3 | the header: full posture, mode, server count, appended PATH, no e-mail | **PASS** | *"Claude Code · model: haiku (confirmed after the first turn) · full Claude Code (default) · `<subscription>` · firstParty · usage counts against your Claude plan"*; posture line *"full Claude Code (default) — your settings, skills, hooks and 0 MCP servers are loaded / PATH: inherited"* (0: strict mode; nothing appended because a JVM started from a terminal already had those folders on its `PATH`); no `@` anywhere |
| 4 | one Bash turn per mode | **RECORDED, differs from the checklist's expectation** | `default`: **ran** (`echo SMOKE-BASH-DEFAULT`, ✓, 4.5 s); `acceptEdits`: **ran**; `plan`: refused by the model (*"plan mode is active"*), no tool call; `auto` on haiku: **refused by the analyser** with PB-3's words (`MODE_MISMATCH`, stopped before a turn); `auto` on sonnet: ran. See *Findings*, 2 |
| 5 | `analyser_context` and `analyser_read` work; a destructive verb hidden; `graph {delete}` refused | **PASS / inconclusive** | *"The open log has 21 records"* (true); `analyser_open`: *"does not exist among the available fluxtion-analyser tools"*; `graph {delete}`: the model sent unparseable input and each call was `refused`, so the refusal's reason was not this rule's (it stays covered by `HostedCredentialTest`) |
| 6 | a changed hook file: the next Start asks again | **PASS** | after adding a `UserPromptSubmit` hook the dialog reappeared naming *"UserPromptSubmit (1), SessionStart (1)"*; the next Start with the file unchanged asked nothing |
| 7 | the mode changed with a resumable conversation: Start offers New chat, never `--resume` | **PASS** | for `acceptEdits`, `plan` and `auto` in turn: `POSTURE_CHANGED`, New chat offered; after New chat a fresh `--session-id` each time |
| 8 | warm start with the full switch on: a direct tab click starts nothing | **NOT RUN here; PASS in the re-smoke below** | the harness failed before it (an evidence message dialog was open over the frame and the tab's bounds were null); covered by `LocalAgentFullPostureTest.warmStartNeverFiresInTheFullPosture` and its witness only |
| 9 | Stop, a kill and resume, and the tree empty | **PASS (Stop, resume) / partial (tree)** | Stop ended the turn `CANCELLED` 151 ms after the press; after a kill Start launched `--resume <id>` of the same id and answered *"after-resume"*; every mode switch above left the phase `OFF` before the next Start; the final Stop did not run (item 8's failure), and after the JVM was stopped no `claude`, bridge or child process remained |

## Findings

1. **The analyser passes its own environment to Claude Code, including a host Claude Code session's variables.** The
   first attempt of this run was launched from a shell inside a Claude Code session; its JVM inherited `CLAUDECODE`,
   `CLAUDE_CODE_SESSION_ID`, `CLAUDE_CODE_ENTRYPOINT`, `CLAUDE_CODE_MESSAGING_SOCKET`/`_TOKEN` and others, and one
   `claude` was started with them before the run was stopped (the run was then repeated with every `CLAUDE*` variable
   removed; its turns are the ones above). This is P1a behaviour as well as P1b's. A person who starts the analyser from
   a terminal Claude Code session would hand a child those variables. **Proposed, for the owner:** the adapter removes
   the host-session variables (`CLAUDECODE`, `CLAUDE_CODE_SESSION_ID`, `CLAUDE_CODE_ENTRYPOINT`,
   `CLAUDE_CODE_MESSAGING_*`, `CLAUDE_CODE_SSE_PORT`, `CLAUDE_CODE_BRIDGE_SESSION_ID`, `CLAUDE_CODE_CHILD_SESSION`,
   `CLAUDE_PID`) from the child's environment while keeping the person's configuration variables. **Fixed afterwards** (review finding 1, `ChildEnvironment`).
2. **In `default` and `acceptEdits`, a harmless `echo` through Bash ran without being asked.** PB-1's `touch` of a file
   outside the folder was denied (`asyncAgent`, *"no approval surface"*); `echo` was not asked at all — the CLI's own
   allowance for read-only commands or the person's allow rules, which is exactly what §3 says the full posture is
   ("as far as your permission rules and the mode allow"). §8.1's checklist expected a denial; the expectation was
   wrong, not the behaviour. A command the person's rules do not allow is still denied without blocking (PB-1).
3. **The model may mention the person's own paths.** In `plan` the answer named the CLI's plan file under the person's
   home folder (no file was created). The full posture's answers are the person's own Claude Code's; nothing in the
   analyser records them beyond the bounded store.

## Re-smoke after the review fixes (2026-10-17)

The same isolation, wrapper and cleanup, with [`harness/M74P1bFixSmoke.java`](harness/M74P1bFixSmoke.java) and
[`harness/M74P1bWarmSmoke.java`](harness/M74P1bWarmSmoke.java), on `feature/claude-code-p1b` after the 14 review fixes.
No project was opened, so Claude Code worked in the analyser's scratch folder. **Spend:** the session total reported
was US$0.094 (four haiku turns, on the Claude plan); the warm-start runs sent no turn.

| Item (finding) | Verdict | Evidence |
|---|---|---|
| the hosted bridge on its credential alone (6) | **PASS** | the per-instance file's keys were `url, nonce, hosted, pid, startedAt` — no `token`; `analyser_context` and `analyser_read` then worked over it (*"The open log has 21 records"*) |
| Settings applied while idle (4) | **PASS** | mode `default` → `plan` applied through the frame's own Settings path: `POSTURE_CHANGED`, the process stopped at once, the note *"Settings changed the posture … so it was stopped; Start begins a New chat"* |
| Settings applied mid-turn (4) | **PASS** | mode `plan` → `default` while counting: the turn ran to `COMPLETED`, then the session stopped, with the note *"… after the turn in progress"* |
| consent withdrawn mid-turn (4) | **PASS** | *Allow the analyser to start Claude Code* off: `SWITCHED_OFF`, stopped at once, no process left |
| a hook planted in the scratch folder (3) | **PASS** | Start raised the full dialog naming *"SessionStart (1) — from .claude/settings.json"*; after *Don't trust* nothing started and the hook's marker file was never written |
| §8.1 item 8: warm start with the full switch on | **PASS, after a fix** | first attempt: the Assistant tab's click selected the tab but **did not count as the person's in either posture** — with the Aqua tab UI the selection is made inside the `MOUSE_RELEASED` dispatch while `EventQueue.getCurrentEvent()` reads as a `HierarchyEvent` left by a nested dispatch, so P1a's `WarmStart` never recognised a click (P1a defect). Fixed (`WarmStart.Tracker`: the tab strip's own input, held while the queue's most recent input time is still its own) and re-run: full posture — the click selected the tab and **started nothing**; control, the same click with the full switch off — **started Claude Code** (`--session-id …`, `READY`) |
| process tree after the final stop | **PASS** | 0 live descendants |

A mid-session **mode change through the CLI** (`system/status`, finding 2) was not provoked live: it is captured
(`../m74-p1b/fixtures/pb-status-mode-iso.jsonl`) and replayed through the real adapter in
`LocalAgentFullReplayTest.aRecordedModeChangeIsRefused`.

## Reproduce

Build the jar (`mvn -o package -DskipTests`), add the compiled harness class to a copy of it, plant the folder as
above, put a `claude` wrapper that appends `--strict-mcp-config` first on the `PATH`, remove every `CLAUDE*` variable
from the environment, and run `java -Duser.home=<SMOKE>/home -Djava.io.tmpdir=<SMOKE>/tmp -cp smoke.jar
telamin.fluxtion.audit.analyser.analyser.ui.M74P1bSmoke <SMOKE>` under `/tmp/fluxtion-analyser-display.lock`. Delete
`<SMOKE>` and `~/.claude/projects/*scratchpad-m74-p1b-smoke*` afterwards.
