# M74 P2b — live smoke of onboarding, the switch offer and the transcript

**Spec:** [`spec-claude-code-provider.md`](../../spec-claude-code-provider.md) r11, §8.2 *Live smoke (P2)*.
**Date:** 2026-10-17. **CLI:** `2.1.282 (Claude Code)`, macOS, the person's real login on a Claude plan.
**Branch:** `feature/claude-code-p2` at `71e0c57f` plus the `armed.SETUP` fix below. **Model:** `haiku`.
**Spend:** on the person's Claude plan (the header said *usage counts against your Claude plan*); four model turns.
No spend limit was set (owner, 2026-10-17); the runaway guard was the harness's own: every wait bounded, and a
watchdog that would kill the JVM after 25 minutes (it did not fire; the run took 78 s). No run used
`bypassPermissions` or `dontAsk`.

## How it was run

As the P1a and P1b smokes ran: the branch's shaded jar with [`harness/M74P2bSmoke.java`](harness/M74P2bSmoke.java)
(and the P1b harness's helpers) added, run as a separate JVM with `user.home` and `java.io.tmpdir` under the run's
scratch folder, every `CLAUDE*` variable of the host session removed, and REST on. A `claude` wrapper first on the
JVM's `PATH` added `--strict-mcp-config` when the analyser's line lacked it (the default posture's line already has
it). The harness pressed the application's own controls — the start page's S2, the folder chooser, the folder
confirmation, Start, Send, the banner's *Review…*, the review's radio buttons and *Apply chosen*, Settings, the
project switch and its offer — and read only the published snapshot and the bounded store.

- **Cleanup.** The one `~/.claude/projects/` folder the run created (its name containing `m74-p2b-smoke`) was
  deleted; the folder list equals the one taken before the run, and the person's `~/.claude/settings.json` hash is
  unchanged. Raw events and the scratch folder were deleted after this report was written. A first attempt ran the
  harness from a two-entry classpath; Start refused it (*"the analyser bridge launcher is unavailable … a
  multi-entry classpath cannot launch the bridge"*) and no `claude` process ran.

In the excerpts `<SMOKE>` is the run's scratch folder; the set-up folder was a copy of `examples/fixture-generator/`.

## Verdicts

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 4 | onboarding: choose the set-up folder, confirm, Start, ask; `analyser_propose` called; the banner; the review's verdicts; apply *Create a project* and two items; `context.proposals`; Keep | **PASS** | the chooser *"Set up a project with Claude — choose your code's folder"*; Start armed *"Start — set up this project"* with the SETUP draft, **0 processes**; the folder question, *Yes*; `BOUND` 2 s later with `--add-dir <SMOKE>/setup-proj`; Claude called `analyser_context`, `analyser_read`, Glob, Grep and Read, then `analyser_propose` with three items; the analyser's checks *"DIRECTORY (13 .java files)"*, *"SOURCE_FILE (not in the source roots yet)"*, *"READABLE (fit UNKNOWN until opened)"*; banner *"Claude Code proposes 4 changes to set up …/setup-proj. Nothing has been applied — review each one."*; the review answered Yes on the create row, the source root and the GraphML (the processor left at No); after *Apply chosen*: create `APPLIED`, source root and GraphML `APPLIED`, processor `DECLINED`, check `pairing=UNKNOWN`; `context.proposals` = ids, kinds, states and verdicts only; the session kept running with *"this session started before the project existed — New chat starts a session in the project"* |
| 5 | a free-text "proposal" in an answer creates no banner | **PASS** | Claude answered with the propose-shaped JSON as text; the proposal stayed `p-1`, no banner |
| 6 | an external client calling `propose` gets the unknown-verb error | **PASS** | REST `/action` with the shared token: `400 {"ok":false,"error":"unknown verb 'propose'"}` |
| 7 | a project switch while a session runs (the simplified Move, r11-1) | **PASS** | the switch to a second project was held and offered (*"Claude Code is running in setup-proj. You asked to switch to other-proj. …"*); *Stop, and get Start ready in the new project*: phase `OFF`, the switch applied, Start reads *"Start a new session in this project"*, **0 processes** |
| 9 | the REST transport off and on while a session runs: the next call works | **PASS** | after REST off the session stayed `READY`; the next turn's `analyser_context` was `✓` |
| 10 | the transcript: heading, list, Java block, link and a literal `<img src=…>` | **PASS (fetch) / not checked (visual)** | the answer held all five; the loopback server named in the `<img>` received **0 requests**; the rendering itself is covered by `TranscriptViewTest` and the P2a captures |
| 0–3, 8, 9b | the start page with no AI, the Ask catalogue, the stale draft, recording a walk | **not run live** | P2a behaviour, or covered on the real processor and frame: `StartPagePromptTest`, `AskDraftSessionTest` (stale draft), `StartPromptFrameTest`, `ConversationV2RecordingTest`; opening a v2 walk in a pre-P2 build is covered by `ConversationWalkStorageTest#aNewerVersionSurvives` |

## Findings

1. **The SETUP draft armed Start with the generic words** (*"Start — ask your question"*): the arming key followed the
   start page, not the entry. **Fixed** (`armed.SETUP`, *"Start — set up this project"*), with a witness
   (`SetupOnboardingRouteTest#theSetupDraftArmsItsOwnWords`); the verdicts above are from the run after the fix.
2. **Creating the project in the set-up folder closed the open log** — a project is a session boundary (M35.5), so
   *Create a project* is one too; Claude's next `analyser_context` said no log was open. The proposal can carry a
   `logToOpen` item to reopen it; whether the analyser's own create should keep the log it was set up for is an owner
   question (it would be a new exception to M35.5).
3. One of Claude's `Read` calls was denied by the posture (the analyser assistant's folder confinement), as designed;
   the tool line said *"denied by a permission rule"* and the note named the posture.
