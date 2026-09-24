# Results — M68.1 re-review fixes

Each set answers the predictions committed before it in `PREDICTIONS.md`. Raw outputs sit beside this file.
Counts are summed from Surefire XML, not read from the console. Java 21 (Corretto 21.0.8), macOS, a real display.

## Set 1 — the review's findings reproduced on the merged tree `b5cc7772`, before any fix

| Prediction | Result | Right? |
|---|---|---|
| P1 — one frame failure, at the parity assertion, scopes `1,1` against `-1,-1` | 12 classes, **63 tests, 1 failure, 1 skipped**: `PairingDuringLoadFrameTest.committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession`, `frame/discovery parity`, frame `recordsScanned=1, recordsTotal=1`, discovery `-1, -1`. File `set1-p1-frame-failure.txt` | **Yes.** The one skip under a display was not predicted and is examined in set 2 |
| P2 — unscoped for combined and graph-first, scoped for log-first | exactly that, three rows in `set1-p2-probe-before.txt` | **Yes** |
| P3 — the brief's third mutation stays green | STILL GREEN, 0 failing. `set1-p3-p4-harness.txt` | **Yes** |
| P4 — an uncompilable mutation is reported as STILL GREEN | STILL GREEN, 0 failing; source restored byte-identical (SHA-256 `56169deca9a61408…`) | **Yes** — so the harness reports a *missing* run as a result. It cannot report a *stale* one: it deletes old reports before each run. The worse gap is the absent baseline: one pre-existing failure among its targeted tests would make every mutation read RED |
| P5 — the four quoted conclusions survive | all four present; line numbers moved by the merge (`MainFrame.java:1581`, `TopologyPanel.java:1645`, `help.html:172–173`, `support.md:20`) | **Yes** — and a **fifth the review missed**: `docs/site/user-guide/topology.md:101`, "Treat the warning as a version mismatch" |
| P6 — headless 0 / 0 / 62, count = `main` + 18 | merged **1,912 / 0 / 0 / 62**; `main` at `90746e83` **1,894 / 0 / 0 / 62**; difference 18 | **Yes** |
