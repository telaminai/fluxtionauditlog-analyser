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

## Set 2 — the fixes, on the working tree that became the fix commit

| Prediction | Result | Right? |
|---|---|---|
| P7 — headless 1,925 / 0 / 0 / 62 | **1,925 / 0 / 0 / 62** | **Yes**, exactly |
| P8 — frame 63 / 0 failures / 1 skip | **63 tests, 0 failures, 1 skipped.** The skip is `PersonAtTheScreenFrameTest.escapeWithTheSearchHistoryPopupFocused`, which aborts on its own assumption: *the display did not give the frame keyboard focus — a posted Escape would be dropped, not tested*. Same skip as set 1, predates the branch, and only runs for real under CI's xvfb | **Yes** |
| P9 — harness headless: baseline green, every mutation RED at its named test | baseline **41 tests green**; all **16** headless mutations RED at the named test; every restore byte-identical; working tree unchanged. `set2-p9-harness-headless.txt` | **Yes** |
| P10 — `--frame`: the frame-only mutations RED at the parity test | baseline **50 green**; all **19** RED at the named test, M6f / M7f / M15f at `PairingDuringLoadFrameTest.committedGraphPairs…`. `set2-p10-harness-frame.txt` | **Yes** |
| P11 — end to end on the branch jar, every check passes | **46 pass, 0 fail**, including all 24 three-order checks. `set2-p11-e2e-branch.txt` | **Yes** |
| P12 — on a `main` jar, every scenario-5 scope and qualification check fails | **34 failures.** In scenario 5, **17 of 24 fail**, but not all the ones I predicted: *log first: the verdict sentence carries the scope* **passes** on `main`, because an older fix (review F1) already appended the sample to the sentence on that one path; and the three *coverage finds the foreign id* checks pass, because coverage always warned about a foreign id. `set2-p12-e2e-main.txt` | **Partly wrong.** The checks that pass on `main` are the ones that were never the defect |
| P13 — the review's probe prints the sample scope in all three orders | all three rows `first 500 of 600 records`, `sampled=True`. `set2-p13-probe-after.txt` | **Yes** |
| P14 — the pairing note is the first readable text of the status line at the default size | **It is**, and that exposed a defect the prediction did not anticipate. The clip falls inside the note: before coverage the visible text is "every node id checked is declared (3…", which hides that it was a 500-of-600 sample; after coverage it shows the same words, the *superseded* verdict, while the correction is clipped off the end. `set2-p14-*.png` | **Yes as predicted — and the prediction was the wrong test.** Leading with the note is not enough; the note has to lead with whatever qualifies it. Fixed in set 3 |

## Set 3 — the note leads with what qualifies it

| Prediction | Result | Right? |
|---|---|---|
| P15 — a sampled note starts with its scope; unsampled notes unchanged | `theNoteLeadsWithItsScope` passes; the existing frame assertion on an unsampled note still passes | **Yes** |
| P16 — after a superseding comparison the panel note starts with "whole log: 1 of 4 logged id(s) not declared"; after a confirming one it starts with the sampled note | superseding: **yes**. Confirming: **no, by a design change made while implementing** — a whole-log confirmation also leads ("whole log: all 3 logged id(s) declared — confirms the sample taken on open"), because the broader fact is the one a clipped line should show, and after confirmation the sample adds nothing. A *narrower* comparison (a filter) is appended, as predicted | **Half right; the other half changed deliberately and is recorded here** |
| P17 — screenshots lead with "first 500 of 600 records" before coverage and "whole log: 1 of 4" after, the rest clipped | exactly that: "first 500 of 600 records: every node…" and "whole log: 1 of 4 logged id(s) not de…". `set3-p17-*.png` | **Yes** |
| P18 — headless 1,927 / 0 / 0 / 62; frame 63 / 0 / 1 skip; harness all RED plus two new | headless **1,927 / 0 / 0 / 62**; frame **63 tests, 0 failures, 1 skip** (the same focus assumption); harness with `--frame`: baseline **52 green**, **21 of 21** RED at the named test, every restore byte-identical, M16 and M17 new. End to end on the branch jar: **46 pass, 0 fail**. `set3-p18-harness-frame.txt`, `set3-e2e-branch.txt` | **Yes** |
