# Narrow review — EB.F6 (`05f3ea3c`) and EB.F11 (`f573d792`)

**Verdict: merge now.** Two advisories, no REQUIRED. Every number reproduced, all eight named controls caught,
and four of my six predictions were wrong — three of them because the work is more careful than the brief's own
hints suggested.

## 1. Commands and outputs

| command | result |
|---|---|
| `mvn -o -q clean test`, fresh Surefire XML | **2785 tests, 0 failures, 0 errors, 164 skipped** — matches the author exactly |
| the eight named `cv-` controls, fast engine | **8 of 8 caught**, none missing from the requested list |

**Skips separate:** 164, the frame suites.

**Not run:** `EvidenceCaptureFrameTest` with a display; PR #63's CI jobs; the Follow-based provocations in §4.
Budget. Unrun is not passing.

## 2. Predictions, scored

| # | prediction | outcome |
|---|---|---|
| P1 | `store.size()` is not what the session published as read (off by a pending trailing record) | **NOT CHECKED** — needs a live Follow read |
| P2 | "under Follow, changed-on-disk is growth" is false for truncation or same-length rewrite | **NOT CHECKED** |
| P3 | `syncOpenGraphsIntoConfig()`'s early return leaves the bundle carrying stale charts, silently | **RIGHT on the mechanism, softer than I thought on severity** — F1 |
| P4 | `ProjectProfile.write` misbehaves against a DELETED profile | **WRONG.** `if (file != null && Files.isRegularFile(file))` — absent means nothing is carried over, no exception. Unreadable is caught too. |
| P5 | a recipient cannot tell `sourceRecords` means "read", not "the log's size" | **WRONG, and the wording is better than I would have written.** `"the bundle holds the N records read before the capture, not what was written after them"`, and the excerpt line reads `records 0..N-1 of N read` |
| P6 | carried-over unknown keys reach the bundle | **WRONG.** The bytes are written to a temp file and then go through `BundleProfile.export` (BundleWriter:133–135), which emits only the allow-listed categories. Unknown keys cannot survive. |

One right, three wrong, two unchecked.

## 3. Findings

### F1 — ADVISORY. Withheld chart definitions are captured silently

`MainFrame.syncOpenGraphsIntoConfig` (**:5428**) returns early when `graphTabs.definitionRefusal() != null`, and
`startCapture` calls it without checking. In that state the bundle's profile carries the **last saved** chart
definitions rather than what is on screen, and nothing in the capture's lines says so.

**Why it is only advisory.** EB.F11's contract is "serialised exactly as a save would write it", and while
definitions are withheld a save writes nothing either — so the bundle is *consistent with the contract*. The
codebase already treats this state as don't-touch-charts: `graph {close}` refuses outright while definitions are
withheld.

**Why it still matters.** The recipient sees charts that may not match the sender's session, and cannot tell.
This is the same class EB.F11 exists to close — a silent divergence between what is true and what is captured —
closed in one place and left in its neighbour.

**Reproduction:** put the session into definition refusal, edit an open chart, capture; the bundle's
`savedGraphs` are the pre-edit ones, with no line mentioning it.

**Regression:** capture while `definitionRefusal() != null` and assert a `lines` entry naming it — or assert a
refusal, if the owner prefers capture to decline rather than narrate. I would narrate: refusing would block a
capture over a condition the person may not be able to clear.

### F2 — ADVISORY. A javadoc that this commit made untrue

`BundleWriter.Job`'s `settingsBytes` (**:45**) still reads *"its bytes, read after the project's pending write
was flushed"*. `f573d792` removed that flush and takes the live config instead — the parameter's description now
describes the mechanism the commit deleted.

Trivial except for where it sits: this is a feature whose pitch is that stated guarantees match behaviour, and
its two REQUIRED findings so far were both a claim that had drifted from the code. One line.

**Regression:** none worth writing; a doc check here would cost more than it protects. Fix it in passing.

## 4. What I could not check

- **P1, the boundary that matters most.** Whether `store.size()` at the effect equals what the session published
  as read — the brief notes a live read's last record is pending, not read, and `context.log` carries
  `trailingRecordsPending` and `trailingRecordsIncluded` as separate counts. If they diverge, `sourceRecords` is
  off by one against `context.log.records`, and the manifest would misstate how much was read. **This is the one
  I would do next.**
- **P2**, truncation and same-length rewrite under Follow, and rotation. The commit states "under Follow,
  changed-on-disk is growth" as established; I did not test the cases where it would not be.
- The end-to-end re-basing claim for a walk saved under Follow (the brief's EB.F6 item 3).
- Follow restore reopening the log as a new generation.
- `EvidenceCaptureFrameTest` with a display, and PR #63's CI.

## 5. Verdict

**Merge now**, against a demo/MVP bar. Neither advisory can produce a wrong bundle: F1 produces charts a
recipient may not expect, with the log, walk and report evidence unaffected, and F2 is a comment.

The unchecked P1 is the item I would not leave open indefinitely — not because it is likely, but because it is
the one place in this pair where the manifest could state a number that is not true, and a number in a manifest
is exactly what this feature asks a recipient to trust.
