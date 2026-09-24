# Predictions — M68.1 re-review fixes, written before each trial

Committed **before** the trial it predicts, so a result cannot be read back into it. Tree under test for the
first set: `b5cc7772`, the branch with current `main` merged in (R5). Outcomes are recorded in `RESULTS.md` beside
this file, including the ones that were wrong.

## Set 1 — reproducing the review's findings, before any fix

- **P1 (R1).** With a display, the twelve `*FrameTest` classes run with **exactly one failure**:
  `PairingDuringLoadFrameTest.committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession`, at its parity
  assertion, with the frame's pairing carrying `recordsScanned=1, recordsTotal=1` and discovery's `-1, -1`.
- **P2 (R2).** The review's probe on a jar built from this tree prints *scope not recorded* and
  `sampled=False` for **combined open** and **graph first**, each with the verdict "declares all 3 node(s) this
  log writes", and *first 500 of 600 records* for **log first**.
- **P3 (R4).** The brief's third mutation — `ratioAvailable = coverage.denominator() > 0 && !logged.isEmpty()` —
  leaves every current test **green**.
- **P4 (R4, my own harness).** If a mutation stops the code compiling, the current `tools/mutate-m68-1.py` reports
  it as **STILL GREEN**, because it counts failures in reports that were never written. The review describes the
  harness as able to print a stale result; I predict the sharper defect is that it prints a *missing* result as a
  pass. (It already deletes old reports before each run.)
- **P5 (R3).** On this tree the four quoted conclusions are still present: the finding-export echo in
  `MainFrame.java`, "different build?" in `TopologyPanel.java`, "Treat a mismatch as a version problem" in
  `help.html`, and the different-build sentence in `docs/site/support.md`.
- **P6 (R5).** The headless suite on this tree has **0 failures, 0 errors, 62 skips**, and its test count is
  `main`'s count plus this branch's 18.

## Set 2 — the fixes, written before any of these trials ran

Fixes in the working tree at the time of writing: R2 (session and discovery scoped, discovery facts, whole-log
qualification bound to the published pairing), R1 (parity test reads the product's own discovery path), R3 (one
wording class, four code sites, help, two docs pages, a repository guard), R4 (zero-ratio test, new harness), O1
(pairing note first, full-line tooltip), O3 (audit label), O4 (level caveat kept beside pairing qualifications), O5
(focus-recall wording).

- **P7.** Headless suite: **0 failures, 0 errors, 62 skipped, 1,925 tests** — set 1's 1,912 plus 13 new test
  methods. Named risk: an existing headless test that asserts discovery's exact map keys or the old warning text.
- **P8.** Frame suite with a display: **63 tests, 0 failures, 1 skipped** — the same skip as set 1. Named risk: a
  frame test that asserts the *start* of the Topology status line, which O1 changes.
- **P9.** `tools/mutate-m68-1.py`: baseline **green**; every headless mutation **RED at its named test**; every
  restore byte-identical.
- **P10.** `tools/mutate-m68-1.py --frame`: the three frame-only mutations (M6f, M7f, M15f) **RED at the parity test**.
- **P11.** `tools/verify-m68-1-coverage.py` on the branch jar: **every check passes**, including all three open orders.
- **P12.** The same script on a `main` jar: fails every scenario-5 scope and qualification check, as well as the 17
  earlier failures.
- **P13.** The review's own probe on the branch jar prints *first 500 of 600 records*, `sampled=True`, for **all
  three** open orders.
- **P14.** A screenshot through the jar's `screenshot` verb at the default window size shows the pairing note as the
  **first readable text** of the Topology status line.

## Set 3 — the note leads with what qualifies it (from P14), written before the fix exists

Planned fix: a sampled pairing's note starts with its scope ("first 500 of 600 records: …"); once a whole-log
comparison supersedes the sample, the panel note starts with that finding and puts the sampled verdict after it.
The composition moves into a pure method so it can be tested without a display.

- **P15.** A sampled pairing's `note()` **starts with** "first 500 of 600 records: ". An unsampled one is unchanged,
  so the existing frame assertion `startsWith("every node id checked is declared (3/3")` still passes.
- **P16.** The panel note after a superseding whole-log comparison **starts with** "whole log: 1 of 4 logged id(s)
  not declared". After a confirming one it starts with the sampled note and ends with the confirmation.
- **P17.** Screenshots at the default size: before coverage the first visible text is "first 500 of 600 records";
  after coverage it is "whole log: 1 of 4". I expect the rest of each line to be clipped, as before.
- **P18.** Headless **1,927 / 0 / 0 / 62** (two new test methods). Frame **63 / 0 / 1 skip**. Harness: every
  mutation still RED at its named test, plus two new ones for this fix.

## Set 4 — reproducing the re-review's findings on `550f98d8`, before any fix

Re-review: `review/m68-1-rereview-2026-09-24` at `6a7042e7`. Its author also wrote the first review, so it is not
independent; its findings are checked here, not assumed. Instruments added before this trial and committed with
it: end-to-end scenarios 7 (N1, Follow append) and 8 (N2, whole then filtered, and the reverse), and a sampled
parity frame test (O-c). No product code is changed for this set.

- **P19 (N1).** On a jar built from `550f98d8`, scenario 7: the store reaches 601 records; *the qualification no
  longer claims to confirm* **fails**; *says the log has grown* **fails**; *the published pairing's scope counts
  the appended record* **fails**, because I read that `pollFollow` never touches the published pairing, so its
  scope still says "first 500 of 600 records". A fresh coverage does find `lateForeign`.
- **P20 (N2).** Scenario 8, whole then filtered: all three checks **fail** — the whole-log finding is gone from
  `context`, nothing states the filtered comparison beside it, and the filtered reply does not mention the whole
  log. Filtered then whole: both checks **pass**, because a plain overwrite by the wider comparison is what this
  order wants.
- **P21 (N3).** Three plants, each through the harness with a byte-identical restore — a text block carrying the
  incident sentence in `MismatchWording.java`, the sentence split across two literals where neither half matches,
  and a line in `src/main/resources/llm/system-prompt.md` — each leaves `UserVisibleWordingGuardTest` **green**.
- **P22 (O-b).** A mutation that makes a named test **throw** rather than fail its assertion is reported **RED** by
  the current harness.
- **P23 (O-c).** The new sampled parity test **passes** on the current code: the three loops share one constant and
  one first-N rule, so I expect them to agree today. The value of the test is that they cannot drift apart unseen.
