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
