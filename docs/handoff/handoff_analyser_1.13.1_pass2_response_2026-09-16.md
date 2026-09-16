# Handoff — response to the second-pass review, 2026-09-16

_Answers [`review_analyser_1.13.1_pass2_2026-09-16.md`](review_analyser_1.13.1_pass2_2026-09-16.md) (NOT READY:
R2-B1, R2-B2). Based on `657ab6e4`, the review committed unchanged. Author verification, kept distinct from the
independent review, which is pending on this response._

| # | Review said | What changed | Evidence |
|---|---|---|---|
| **R2-B1** P2 | `open {log, format}` calls `openFileWithReader` directly; only `openFile` started the pending lifecycle, so a graph opened during the load was judged against the previous log | load-start bookkeeping now lives in `openFileWithReader`, the common asynchronous entrance (`openFile` only delegates) | `PairingDuringLoadFrameTest.theSameThroughAnExplicitReaderFormat` — the review's sequence with `format: "yaml"`: echo pending, context pending with no `applies`, then B/B. Mutant (bookkeeping back on `openFile`): red |
| **R2-B2** P2 | the lazy session built on a socket arrival inherited `sessionInteractive = true` (set only by project transitions) and showed a modal "graph closed …" nobody could dismiss | `onLoaded` sets `sessionInteractive = !request.fromActionSocket()` before the observations; a non-interactive warning is written to the status bar | `…freshWindow_socketGraphThenMismatchingSocketLog_closesTheGraphWithoutADialog`: a dialog watchdog counts and disposes any dialog; asserts none, and that the graph closed. Mutant (assignment removed): red — the watchdog saw the dialog |
| R2-F3 P2 pre-existing | a refresh observation can close the graph that replaced the one judged | **not fixed** — the session processor must distinguish a real arrival from a refresh of observed state; that is M44.3's shape and is filed there | — |
| R2-F4 P3 | backslash-LF joined two lines; CR never terminated; text blocks lost their colour | the scanner breaks at LF/CR before honouring an escape; text blocks are found by their triple-quote delimiters and coloured whole | colour-span tests: `anEscapeBeforeALineEnd…`, `aTextBlockIsColouredAsOneLiteral` |
| R2-F5 P3 pre-existing | an unchanged processor lost its scroll position on a config refresh | `showSelectedProcessor` navigates only when the pane was re-read or its name changed | `SourcePanelRootChangeTest.unchangedHit_keepsItsCaretAcrossAConfigRefresh` |
| R2-F6 process | the frame suite is skipped by default; not a CI gate | `ci.yml` `ui-frame` job: xvfb, headless off, fails if Surefire recorded a skip | the job itself, on this push |

## Verification record

- Display suite: 3 tests, 0 failures, 0 skipped, with `-Djava.awt.headless=false -DargLine="-Djava.awt.headless=false"`.
- Headless `mvn -o clean verify`: 1406 tests, 0 failures, 3 skipped (the frame suite, by the pom's design); the new
  CI job runs it with a display.
- Mutants (fix reverted, suite run, code restored and diff-checked): R2-B1 red on the explicit-format case, R2-B2
  red on the no-dialog case.

## Open after this response

- R2-F3 → M44.3: the review's remedy ("bind a close effect to the graph identity actually judged") is the
  processor-side change; the adapter cannot do it honestly.
- The audience flag is now set by arrivals and by project transitions. A human File-menu close between them
  inherits the last one; the review may want a rule for that.
