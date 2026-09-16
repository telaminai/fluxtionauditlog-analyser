# Handoff — response to the third-pass review, 2026-09-16

_Answers [`review_analyser_1.13.1_pass3_2026-09-16.md`](review_analyser_1.13.1_pass3_2026-09-16.md) (NOT READY:
R3-B1). Based on `4363f439`, the review cherry-picked unchanged onto the branch; the fix is `1c3c817a`. Author
verification, kept distinct from the independent review._

| # | Review said | What changed | Evidence |
|---|---|---|---|
| **R3-B1** P2 | a human log arrival set the audience true as ambient state; a later socket `close {graph}` inherited it and showed the "graph closed …" modal | the audience belongs to the operation: socket `close`/`openGraphml`/`selectProcessor` declare `false` on entry; File-menu close/reset listeners and the human graph-open entrance declare `true`; arrivals and project transitions keep their own declarations | `PairingDuringLoadFrameTest.humanArrivalThenSocketGraphOpenAndClose_noDialogForTheSocketOperations`: human `openFile(A)`, socket graph B, socket close → 0 dialogs. Mutant removing all three socket-entrance declarations: red. Removing only the `close` line: NOT red, because the preceding socket graph open already declared the audience — stated rather than hidden |
| R3-F2 P3 | an escaped triple quote inside a text block ended the colouring | the closing-delimiter search skips a delimiter preceded by an odd number of backslashes | `anEscapedTripleQuoteInsideATextBlockDoesNotEndIt`: alpha, beta and gamma all literal-coloured; the code after is not |
| R3-F3 P3 | R2-F3 "filed" against M44.3 but recorded nowhere | tracker item **M44.3a** with the reproduction and the processor-side remedy | `docs/specs/tracker.md` ▸ M44 |

## Verification record

- Display suite: 4 tests, 0 failures, 0 skipped. Headless `mvn -o clean verify`: 1408 tests, 0 failures, 4 skipped.
- The CI `ui-frame` job runs the display suite on every push to `main`.

## Left open, deliberately

- `openLogs` and `discoverGraphs` do not declare an audience; neither raises a warning today.
- CR-only line comments still run past the CR (pre-existing, cosmetic).
- The reviewer's exact project-first sequence needs a project profile; the test drops that step and keeps the
  human-then-socket shape that produced the modal.
