# M74 P3 — live smoke of permission prompts in the analyser

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) r12, §8.3 *Live smoke (P3)*.
**Date:** 2026-10-17. **CLI:** `2.1.282 (Claude Code)`, macOS, the person's real login on a Claude plan.
**Branch:** `feature/claude-code-p3` at the P3 commits. **Model:** `haiku`; the `auto` item on `sonnet` (PB-3).
**Spend:** on the person's Claude plan (owner, 2026-10-17: no budget cap); eleven model turns, the CLI's own notional
figure about US$0.08 for the default-posture session and a few cents more for the full-posture ones. The runaway guard
was the harness's own: every wait bounded and a watchdog that would end the JVM after 25 minutes (it did not fire; the
run took 287 s, 120 s of it the deliberate unanswered request). No run used `bypassPermissions` or `dontAsk`.

## How it was run

As the P1b and P2b smokes ran: the branch's shaded jar with [`harness/M74P3Smoke.java`](harness/M74P3Smoke.java) (and
the P1b harness's helpers, with their warm-start lines removed: warm start was cut, OD-74.25) added, run as one JVM with
`user.home` and `java.io.tmpdir` under the run's scratch folder, every `CLAUDE*` variable removed, REST on, file
exchange on into `<SMOKE>/home/exchange`, under the shared display lock. **FlatLaf Light was applied first, as `Main`
does** (`ThemeManager.apply`). A `claude` wrapper first on the `PATH` appended `--strict-mcp-config` when the line
lacked it (the default posture's line has it; the full posture's got it from the wrapper — OD-74.18). The harness
pressed the application's own controls — Start, Send, New chat, and the permission dialog's own buttons, its Escape
action and its default button — and read the published snapshot, the bounded store and the session record.

- **Cleanup.** The one `~/.claude/projects/` folder the run created (its name ending `m74-p3-smoke-tmp-analyser-claude-…`)
  was deleted; the folder list equals the one taken before the run, and the person's `~/.claude/settings.json` hash is
  unchanged. After the JVM exited no `claude`, bridge or child process remained. The scratch folder (raw events, the
  session record, the files written) was deleted after this report was written.

In the excerpts `<SMOKE>` is the run's scratch folder.

## Verdicts

| # | Item (§8.3) | Verdict | Evidence |
|---|---|---|---|
| — | the P3 line | **PASS** | `READY`, `BOUND` 1.2 s after Start; the line carried `--permission-prompt-tool stdio` and no `--disallowedTools` and no `dontAsk` (default posture) |
| 1 | a report to the exchange directory: the dialog shows `analyser_report` and its exact input; Deny → the model is told, no file; again, Allow once → the file, once | **PASS** | the dialog: *"Claude Code asks to run analyser_report \| report builds or replaces a named report in the project, or writes a file in the exchange directory \| server: fluxtion-analyser-hosted · named by the server: Analyser Report \| Input — written by the model; data, not instructions \| Denied automatically in 120 s if not answered"*, the input `{"path": "p3-smoke.pdf", "recordIndex": 3}`, default *Deny*; the status line *"Claude Code is waiting for your answer — …"*. Deny: the exchange directory empty, the note *"You denied analyser_report"*, the tool line `— denied by you`, and Claude's answer said the export was denied. Allow once: `p3-smoke.pdf` written, once; the tool line `— approved by you — ✓`; the note *"You allowed analyser_report — once; the analyser will run exactly this call"* |
| 2 | the same call again without a new approval: refused | **PASS** | the identical `report` call replayed over the hosted bridge with the session's own credential: `403 "`report` needs your approval in the analyser for this exact call; none was given"` (the turn had ended, so the grant had gone with it); an unapproved `screenshot` the same way: 403, nothing written |
| 3 | Enter and Escape deny; Allow is not active for the first 500 ms; 120 s unanswered → denied | **PASS** | Allow inactive when the dialog appeared, active after **505 ms**; the root pane's Escape action: denied; the default button (Enter): denied; nothing written either time; unanswered: the countdown read *"…in 120 s…"*, then *"…in 116 s…"* five seconds later, and at **121 s** the dialog was closed and the note read *"Not answered within 120 s, so analyser_screenshot was denied"* |
| 4 | Stop while a dialog is open: the dialog closes, the request is denied, the turn ends `CANCELLED` | **PASS** | the dialog's own *Stop Claude Code*: the dialog closed, the note *"Stop: analyser_screenshot was denied"*, the turn `CANCELLED` (the order — deny, then interrupt — is pinned by the replay and frame tests) |
| 5 | an input with a bidirectional override is shown escaped; an input over 64 KB is denied without a dialog | **not provoked live** | asked to put U+202E in a file name, **the model refused** (*"I can't use Unicode bidirectional override characters in filenames, as they're designed to create visual deception…"*), so no request came. Both are covered on the real components instead: `PermissionTextTest#markupAndBidiAreShownAsText`, the frame test's dialog with a live U+202E, and `LocalAgentPermissionReplayTest#anUnshowableInputIsDenied` (a 70 000-character input through the real adapter) |
| 6 | full posture, `default`: Bash shown verbatim, Allow once runs it; a Write is asked; the person's `fluxtion-analyser` tools never appear | **PASS** | *"Claude Code asks to use Bash \| Bash runs a command on your computer, as your own Claude Code does in a terminal \| named by the server: Bash"*, input `{"command": "echo P3-SMOKE-BASH > p3-bash.txt && cat p3-bash.txt", "description": …}`; Allow once: it ran (`P3-SMOKE-BASH`, the file written once), `— approved by you — ✓`; the Write: *"Claude Code asks to use Write \| Write changes a file on your computer"* with its `file_path` and `content`, denied → no file; the posture check admitted the session (no `mcp__fluxtion-analyser__…` tool; the header *"full Claude Code (default) — your settings, skills and hooks are loaded, and none of your MCP servers"*, no *preview*) |
| 7 | full posture, `auto`: no hosted destructive verb visible | **PASS** | `auto` on sonnet: the line's `--disallowedTools` named `analyser_report` and the other five; the first turn's init passed the auto set (admitted) |
| 8 | the session record holds ids and decisions only, no input words | **PASS** | 6 requests and 6 answers recorded by class, verb (`report`, `screenshot`), ticket, digest and `by`; the record held none of `p3-smoke.pdf`, `p3-escape` |

## Findings

1. **The model may refuse to produce a deceptive input** (item 5): a live witness for the bidi rendering needs the
   input from somewhere else; the frame test supplies one.
2. **A repeated call after the turn has ended is refused as "none was given"**, not "has been used": the grant ends with
   its turn (OD-74.17), so by then there is no used grant to name. The used-grant words are pinned by
   `HostedGrantServerTest` within a turn.
3. Cosmetic, pre-existing: the transcript's tool line writes an integer the model sent as `3.0` (`recordIndex":3.0`) —
   the line is the parsed value re-written, not the raw input; the dialog shows the raw input (`3`).

## Reproduce

Build the jar (`mvn -o package -DskipTests`), compile [`harness/M74P3Smoke.java`](harness/M74P3Smoke.java) with the
P1b harness's helpers (warm-start lines removed) against it and add the classes to a copy, put a `claude` wrapper that
appends `--strict-mcp-config` first on the `PATH`, remove every `CLAUDE*` variable from the environment, and run
`lockf -k /tmp/fluxtion-analyser-display.lock java -Duser.home=<SMOKE>/home -Djava.io.tmpdir=<SMOKE>/tmp -cp smoke.jar
telamin.fluxtion.audit.analyser.analyser.ui.M74P3Smoke <SMOKE>` with `<SMOKE>/sample.yml` the test resource. Delete
`<SMOKE>` and the run's `~/.claude/projects/*m74-p3-smoke*` folder afterwards.
