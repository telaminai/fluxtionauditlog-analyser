# Handoff — response to the 1.13.1 review, 2026-09-16

_Answers [`review_analyser_1.13.1_2026-09-16.md`](review_analyser_1.13.1_2026-09-16.md) (verdict NOT READY: B1, B2
blocking; F3, F4, F5). Based on `26c10d45` (the review committed unchanged). Every claim names a test or a file;
author verification is kept distinct from the independent review, which is still pending on this response._

## Per finding

| # | Review said | What changed | Evidence |
|---|---|---|---|
| **B1** P1 | pending applied to the echo only; `context` attached graph A's verdict to graph B during B's load | `MainFrame.setBusy(true)` retires `lastPairing` when a load starts; `openGraphml` retires it when a graph opens during a load; `context.graphPairing` reports `pairing: pending…` + `loading: true` while `loadInFlight` and no `applies`/counts; `setBusy(false)` after a FAILED load restores the session's verdict (the previous log is still the open one). `publishPairing()` runs at each point so the topology note and Project panel agree | `PairingDuringLoadFrameTest`: the review's sequence (log A, graph A → A/A; log B + graph B + context in one EDT turn → echo pending, context pending, no `applies`; after landing → B/B 1/1, `graphPath` ends `b.graphml`). Mutant (context reports old verdict, no invalidation): fails with `applies=true, declaredByGraph=1` during the load |
| **B2** P2 | `tracedOnly` per entry: a trace-only contribution beside a value-bearing one, and a business `thread` key, both mis-reported | `ReadService.traceOnly(rec)` decides per instance over every contribution. `tracedOnly`: a wire marker and no entry in any contribution (explicit, any grammar). `traceLikeOnly`: legacy grammar only, a `method` entry present and every entry `thread`/`method` (an inference, reported under its own name). The verb schema says so | `ReadServiceTest.legacyTraceLikeOnly_…` (both counterexamples verbatim; value-bearing control) and `markedTracedOnly_…` (declared grammar: bare `@invoked` positive control, marker + value contribution, business `method`) |
| **F3** P2 pre-existing | fresh window, no project: the session driver is never built, so the promised final verdict is null | `repairLoadedGraph` calls `session()` when there is none — a log arriving is a session event; the driver's construction observes the log and graph now in force | the same frame test opens no project; mutant (no lazy driver) fails: no verdict within 20 s |
| **F4** P2 | node pane missing under both root sets keeps the old placeholder (content-only comparison: empty equals empty) | `SourcePanel.rerenderIfChanged` always re-renders a pane showing a miss; `processorPaneText()`/`nodePaneText()` for tests | `SourcePanelRootChangeTest.nodePaneMissingUnderBothRoots_…`: both placeholders name the new root and not the old; mutant (content-only) fails on the node pane |
| **F5** P2 | handoff §4 blamed the remote path; the check is client-side | §4 rewritten to the client-side boundary; the compiler spec `entry-point-rendering.md` (branch `docs/diagnostics-entry-point-rendering`) was already written at that boundary | the handoff text; the spec |

## Verification record

- `mvn -o clean verify`: see the commit message for the count; the frame test is **skipped under Maven** because
  the pom forces `-Djava.awt.headless=true` for every run (repo convention: panels must be constructible headless).
  It was run on this machine with `-Djava.awt.headless=false -DargLine="-Djava.awt.headless=false"`: 1 test, 0
  failures, ~21 s. The reviewer's own frame probe remains the independent measurement.
- Mutation controls (fix reverted, test run, code restored, `git diff` confirmed identical): B1 red, F3 red, F4 red.
  B2 has no single-line mutant; its tests are the review's counterexamples, which the reviewer measured red on
  `ec4c43c5`.
- Strict docs build and the rule-1 sweep: run before the commit.

## Not done here, deliberately

- **Skill wordings from the review's other dispositions** ("every final field" → eligible instance fields; the
  Mongoose capture roll is not a one-day retention; scope the input-tailing advice; prefer a fresh capture location
  over deletion). Each is a canonical-skill edit and therefore an index re-pin (two commits). Owner's call; the
  wording corrections themselves are agreed.
- **Plotting items a, b, c, f**: pre-existing, confirmed by the review from source; product/spec work, not 1.13.1.
- **The compiler side of F5**: specified, not implemented (`docs/diagnostics-entry-point-rendering` in the
  compiler repo, awaiting its own review).

## For the next review

Return B1/B2/F3/F4 to the reviewer with this table. The frame test's silent skip on CI is the one thing a reader
of the CI log cannot see; if that is unacceptable, the alternative is a headless-constructible seam for
`context` that the test can drive without a `JFrame`, which is a larger change than this response.
