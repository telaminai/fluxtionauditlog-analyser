# M74 P1a — live smoke against the installed Claude Code

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) (r6), §8 P1a acceptance, §10 last row.
**Date:** 2026-10-16. **CLI:** `2.1.282 (Claude Code)`, macOS, native install, the person's real login.
**Branch:** `feature/claude-code-provider` at the delta-review commit (`f7968e4c`) plus the two fixes this run found
(below). **Model:** `haiku`. **Spend:** about **US$0.07** by the CLI's own figures (the UI run's session total
US$0.0447, flagged *at least* because one turn was interrupted; the raw probe process US$0.0272). Cap was US$2; every
process ran with `--max-budget-usd` (the node's remainder of US$1.20 for the UI run, US$0.25 for the probe).
No run used `bypassPermissions`, and none named it.

## How it was run

Authorised by the owner under CLAUDE.md rule 1's recorded capture exception: the real login (an isolated `HOME` loses
it), always `--strict-mcp-config`, every captured string read, sanitised, swept, and the raw capture deleted.

- **An isolated analyser.** The branch's shaded jar, with a small harness class added, was run as a separate JVM with
  `user.home` and `java.io.tmpdir` under the run's scratch folder and `--rest` on. Its config there had
  *Allow the analyser to start Claude Code* on and the model `haiku`. The person's own analysers and their
  `~/.fluxtion-analyser` were not touched; nothing was registered with the person's Claude Code.
- **The analyser's own path.** The harness built a real `MainFrame`, opened `src/test/resources/sample.yml` (21
  records), and pressed the Assistant tab's own buttons: *Claude Code*, *Start Claude Code*, *Send*, *Stop*, *In-app
  assistant*. It read only the published snapshot and the bounded store. So the facts, the node, the adapter, the
  launcher, the action server with its hosted gate and the hosted bridge were all the shipped code; the bridge was
  launched from the running jar (`McpLaunchCommand.runningJar()`), exactly as in the application.
- **No project** was open, so Claude Code worked in the analyser's private scratch folder (§4.3).
- **Raw probes.** The open acceptance lines (§5.4), which the classifier refuses, were written RAW on the exact default
  command line (`ClaudeCodeProvider.command`) to a separate `claude` process, to record what the CLI itself does.
- **Cleanup.** The two `~/.claude/projects/` folders the run created (both names containing `scratchpad-m74-smoke`)
  were listed and deleted; the folder list is identical to the one taken before the run. The person's
  `~/.claude/settings.json` hash is unchanged and no `~/.claude/CLAUDE.md` was created. Raw transcripts deleted.

In the excerpts, `<SMOKE>` is the run's scratch folder, `<id-n>` a session id and `<subscription>` the account's plan.

## Verdicts

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 1 | Start: `initialize`, logged in, nonce binding, posture | **PASS** | READY / `BOUND` 1.0 s after Start (cold start of the real bridge JVM included); 0.6 s on a second start. The first turn's `system/init` passed the posture assertion (`verdict OK`, the generation admitted). Not-logged-in path not taken; header billing `<subscription>`, provider `firstParty`; no e-mail or organisation anywhere |
| 2 | a turn calling `analyser_context` and `analyser_read` | **PASS** | answer from assistant events: *"The log has 21 records. The first record's event type is LifecycleEvent (StartComplete)."* — both true of the log; session total recorded (US$0.035 after it) |
| 3 | a destructive verb | **PASS** (CLI side) | asked to call `analyser_open`: *"The analyser_open tool is not available; the available analyser tools are for reading, filtering, graphing, and analyzing the currently open log."* — hidden by `--disallowedTools`, so it never reached the server; the server-side hosted refusal is covered by `HostedCredentialTest` |
| 4a | blocked slash commands, locally | **PASS** | `/config`, `/clear`, `<NBSP>/config`, `  /config`, `!echo hi`, `# note`, `@x.txt hi` each refused with the §5.4 words (`/clear` offers New chat); the person's turns stayed at 2 — none was sent |
| 4b | stream-json probes (open acceptance) | **RECORDED** | see below |
| 5 | Send while busy | **PASS** | *"Claude is still answering — Stop or wait."*; nothing written. **Defect found:** the refusal stayed on the status line after the turn ended (fixed) |
| 6 | Stop mid-turn, then Send | **PASS** | interrupt ended the counting turn `CANCELLED` 149 ms after Stop (lifecycle `cancelled` for its uuid); the next Send ran in the same process and session: *"resumed-ok"* |
| 6b | kill, then Start resumes | **PASS** | after a kill, Start launched `--resume <id-3>` — the same id as before the kill — and the turn answered *"after-resume"*; the posture was checked again on the new generation |
| 7 | M2: stop before the first Send | **PASS** | Start → BOUND → switch away (kill) → Start: `--session-id <id-1>` then `--session-id <id-2>`, never `--resume`; and a Stop pressed WHILE starting (`HANDSHAKE`) then Start: `--session-id <id-3>` |
| 8 | nothing remains | **PASS** | after the stop before the first Send and after the final stop: 0 live descendants (the CLI, its bridge JVM and their children all gone within 2.5–4 s) |
| 9 | a second instance | **PASS** | a second JVM trying the live session's lock: *"LOCKED — refused"*; the lock files of the stopped sessions were free (released on stop) |

### The posture the real CLI was started in (from the process table)

```
claude -p --input-format stream-json --output-format stream-json --verbose --include-partial-messages
  --replay-user-messages --session-id <id-1> --model haiku --tools Read,Grep,Glob --restricted
  --add-dir <SMOKE>/tmp/analyser-claude-<n> --strict-mcp-config --mcp-config {…fluxtion-analyser-hosted…} …
java -Duser.home=<SMOKE>/home -jar <SMOKE>/smoke.jar --mcp --endpoint-file <SMOKE>/home/.fluxtion-analyser/local-agent/endpoint-<pid>-<gen>.json
```

The bridge's arguments carry the endpoint file's path only, never the token, the nonce or the credential.

### 4b. What the CLI does with the lines the analyser refuses (raw, default posture)

| Line sent | What happened | Consequence for §5.4 |
|---|---|---|
| `   /usage` (leading ASCII spaces) | **not run as a command**: the model answered it as prose (and called `analyser_context`) | the CLI does not strip leading whitespace. The classifier's refusal of `  /config` is conservative, not needed; and a **passed** command typed with a leading blank was sent raw and so did nothing — **defect, fixed**: a passed command is now sent normalised (`/name args`) |
| `<NBSP>/usage` | prose to the model | as above |
| `# PROBE-HASH remember …` | prose to the model; **no memory file written** (no `CLAUDE.md` in the folder, none in `~/.claude`) | `#` is not the terminal's memory shortcut over stream-json |
| `!echo PROBE-BANG-$((6*7))` | prose; **no shell ran** (no Bash tool, no local-command output); the model said it has no shell and computed 42 itself | `!` is not shell mode over stream-json |
| `@note.txt What is the codeword…` (inside the folder) | the CLI **inlined the file**: answered `MANGO-3` with no tool call | an in-folder mention reads like `Read` |
| `@<SMOKE>/probe/outside/secret.txt …` (outside) | **not inlined**; the model tried `Read` and `--restricted` denied it (`permission_denied`); the outside codeword never appeared in the stream | an `@` mention does not widen the folder |

So none of the four refused shapes does anything dangerous in the default posture. Relaxing the `#`, `!` and `@`
refusals (and the leading-blank refusal of a blocked command, which is already harmless) is now an **owner decision**,
not a safety requirement; P1a keeps them refused.

## Defects found, and fixed in the same branch

1. **A passed command with a leading blank did nothing** (4b). `ClaudeCodeProvider.classifyLine` now returns the
   normalised line for a passed command (`Slash.send`), and the frame stores and sends that. Test:
   `ClaudeCodeProviderTest.slashClassificationIsPinned` (`"   /usage"` → `/usage`); witness: return no normalised line —
   `expected: </usage>` fails.
2. **A refused-while-busy reason outlived its turn** (5): the tab read *"Ready — Claude is still answering — Stop or
   wait."* after Stop. `localAgentSession` now clears a `BUSY` refusal when the turn ends. Test:
   `LocalAgentSessionTest.sendWhileBusyIsRefused`; witness: skip the clearing — the "cleared when the turn ends"
   assertion fails.

## Not covered here

- Windows (§11).
- The server-side hosted refusals with a real CLI: `--disallowedTools` already hides those verbs, so they cannot be
  reached from Claude in this posture; they are covered by `HostedCredentialTest`.
- A real rate-limit rejection, an expired login mid-session, and the full posture (P1b).

## Reproduce

Build the jar (`mvn -q -o -DskipTests package`), add the harness classes to a copy of it, and run it as a separate JVM
with `-Duser.home=<SMOKE>/home -Djava.io.tmpdir=<SMOKE>/tmp -Danalyser.rest=true`, the copy alone on the classpath (so
the bridge launches from it). The two classes are in [`harness/`](harness) (package
`telamin.fluxtion.audit.analyser.analyser.ui`, compiled against the jar with `javac -cp <jar>` and added with
`jar uf`): `M74Smoke <SMOKE>` drives the tab, `M74Probe <SMOKE> <path to claude>` sends the raw probes. They are not
part of the build. Delete `~/.claude/projects/*scratchpad-m74-smoke*` afterwards.
