# Recovery note — the P1a holistic review's unrecorded findings (2026-10-17)

**Why this exists.** The holistic review of P1a (2026-10-17, before P1b merged) was answered in two commits —
`a1d94fd5` ("review H5, H4, L2") and `37d992ee` ("review H4, M2, M6, M7, M8, H8") — and a third on P1b, `89a2740c`
("review H6"). The review itself was never filed in `docs/handoff/`, so only the findings those commits name are on
record. Its numbering shows more: H1–H12, M1–M13 and at least L2. This note records, for each finding the commits do
NOT name, what the spec's revision history and the code show — and says plainly where they show nothing.

**Searched (2026-10-17):** this branch's `docs/` and spec (the r10 revision line and *Implementation notes (P1a)*,
"Holistic review fixes"), every local branch whose name contains `claude` (`git grep` over `docs/`), and the commit
messages from `14765831` back to P1a. No copy of the review text was found.

**A label collision to avoid.** The spec's *Implementation notes (P1a)* also carries "Review fixes (2026-10-16, ready
after fixes)" with **M1, M3, M4, S1, S10** and a delta review **R1–R4**. That is an EARLIER, different review; its M3
(a spawn waits for this analyser's own previous run) and M4 (the consent fact carries a `FolderVerdict`) are fixed and
witnessed there. They are not the holistic review's M3/M4, which are unknown.

## Recorded (for completeness)

| Finding | What it was, as the fix records it | Disposition |
|---|---|---|
| H4 | Start needed the external REST switch | fixed `a1d94fd5`, `37d992ee` (hosted-only listener) |
| H5 | a verb added to the schema was auto-allowed (deny-list) | fixed `a1d94fd5` (explicit pre-allow list) |
| H6 | full-posture trust did not cover instruction files | fixed `89a2740c` (on P1b) |
| H8 | no frame test of the tab | fixed `37d992ee` (`ClaudeCodeTabFrameTest`) |
| M2 | leaving Claude Code mid-turn | fixed `37d992ee` (refused by name) |
| M6 | hosted admission read from the snapshot on the frame | fixed `37d992ee` (`HostedAdmission`, the node answers) |
| M7 | the adapter did not check the folder digest | fixed `37d992ee` |
| M8 | the folder answer was not a fact | fixed `37d992ee` (`LocalAgentFolderAnswered`) |
| L2 | one rate bucket for hosted and external callers | fixed `a1d94fd5` |
| S6 (follow-up) | incremental rendering; the node's unbounded entry list | not done — recorded as follow-ups in the P1a notes |

## Unrecorded — what the record shows

| Finding | What the spec / code show | Disposition |
|---|---|---|
| H1 | nothing: no commit, spec line or test names it | **unknown** — no text survives |
| H2 | nothing | **unknown** |
| H3 | nothing (the `H3` in `7bc54231`, 2026-09-26, is a Follow-refresh polish item, not this review) | **unknown** |
| H7 | nothing | **unknown** |
| H9 | nothing | **unknown** |
| H10 | nothing | **unknown** |
| H11 | nothing | **unknown** |
| H12 | nothing | **unknown** |
| M3 | nothing (not the 2026-10-16 review's M3; see above) | **unknown** |
| M4 | nothing (not the 2026-10-16 review's M4) | **unknown** |
| M5 | nothing | **unknown** |
| M9 | nothing | **unknown** |
| M10 | nothing | **unknown** |
| M11 | nothing | **unknown** |
| M12 | nothing | **unknown** |
| M13 | nothing | **unknown** |

**What this means.** None of these can be called fixed or closed (rule 8 asks for the check that would catch each, and
without the finding there is nothing to check against). Some may have been folded into the r10 text without a label —
r10 lists "the explicit pre-allow list, a hosted-only listener, session-thread admission, the folder answer as a fact,
the adapter's folder check, leaving Claude Code mid-turn refused, a separate hosted bucket, a frame test", which is
exactly the labelled set above, so the record gives no hint of any further fix.

**Owner decision needed:** if the review text exists outside the repository (a chat transcript, a reviewer's
scratchpad), file it here and disposition each item; otherwise accept that these findings are lost, and rely on the
three final reviews of 2026-10-17 (`review_m74_final_{p2,p3ux,holistic}_2026-10-17.md`), which covered the same code
after P3, as the replacement record.

**Lesson, recorded so it is not repeated:** a review is filed in `docs/handoff/` BEFORE its fixes land, with a
disposition table, so that every finding — fixed or not — has a row.
