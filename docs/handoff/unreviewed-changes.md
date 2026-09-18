# Un-reviewed changes on `main` — pending review

A running ledger of changes **committed directly to `main` without** the usual brief → report → review
cycle. These are small, ad-hoc fixes made by a session working primarily in **another repo** (a downstream
consumer of the analyser) that hit an analyser-side bug and fixed it in passing, rather than a delegated
work block.

**For the reviewing session:** on your next pull, review each `☐` entry below — read the commit, sanity
the change against the codebase and the repo rules (CLAUDE.md), run `mvn test`, and **verify anything the
entry says was not verified** (Swing UI changes are not unit-tested — build and run the jar). Then tick it
`☑ reviewed <date>` with a one-line verdict, and file any follow-up as a normal review. Fully-reviewed
entries move to `completed/` when this file is next tidied.

Every entry must carry: commit SHA, what & why, files, what was verified, and **what the reviewer must
still check**.

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-09.md`](completed/unreviewed-changes-2026-09.md) and, earlier, [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._

## 2026-09-17 — the ledger-review follow-ups, committed to main directly (one product change among test/tool changes)

- ☑ **reviewed 2026-09-18 — NOT READY:** external legend/plot order, held-out instructions and capture failure signals need fixes (F1-F3, [review](review_main_2026_09_18.txt)). **`9e1d0c7`** `fix: ledger-review follow-ups — one external series per label; the manifest guard reads argument lists; capture script gives two signals`
  **Why it is here:** answers to `completed/review_ledger_2026-09-17.txt` F1/F2 and tracker M46.11/M65.6, made by the
  reviewing session on main without a brief → report → review cycle.
  **What & why:** F2 — `graph` given the same `external` label twice in one call drew two identical legend rows; the
  later entry now replaces the earlier and the echo's `warnings` says so (`ActionExecutor.doGraph`;
  `GraphExternalDuplicateLabelTest`, 2 headless cases). F1 — `InProcessManifestNamesEveryVerbTest.declared()` now
  requires the parameter inside a `{…}` argument list, not merely `name:` in prose; the manifest's `graph` line gained
  the eight parameters it had only explained in prose. M46.11 — `tools/capture-conversations.py` exits 0 with a stderr
  WARNING when only the native image capture is unavailable (`--require-images` → exit 3). M65.6 — checked, not
  reproduced. Also on main the same day without review: `9e3b6b7` (`PersonAtTheScreenFrameTest`, display-only) and
  `deb227f8` (`tools/heldout-client.py`, local-only; a results record).
  **Verified by the author:** full suite green (1630/0/0/27 at the time); the new tests seen red before the fix for F2;
  the inventory guard seen red on `graph.to` and `goto.reveal` mutations; CI green on main.
  **The reviewer must still check:** (1) the dedupe's echo shape — `warnings` is the right key and a client reads it;
  (2) whether an identical external given twice should be a REFUSAL rather than last-wins (the change chose last-wins,
  matching `external`'s replace-by-label rule); (3) the manifest's completed `graph` argument list reads as a list an
  assistant can follow, not a wall.

## 2026-09-18 — M64.10/.11 post-verdict fixes, made after READY WITH FOLLOW-UPS and merged with the branch

- ☑ **reviewed 2026-09-18 — READY WITH FOLLOW-UPS:** F1/F5/F8 accepted on merged main; existing menu follow-ups remain M64.13 ([review](review_main_2026_09_18.txt)). **`a4c38bf`** + **`66f2a32`** `fix(spotlight): review F1, F5, F8 …` and its test alignment
  **Why it is here:** the branch was reviewed READY WITH FOLLOW-UPS at `c635882` with F1 required before release; the
  author then closed F1, F5 and F8 on the branch and merged (`completed/review_m64_10_11.txt`). Those three fixes
  reached main after the last independent look.
  **What & why:** F1 — the bare graph forms are the keyword WITH its colon (`note:`, `series:`), so a chart named
  "Series A" or "Notes on spread" is a chart, and a chart named exactly `note` is reachable as its plot
  (`SpotlightTarget` `case "graph"`; +7 parser cases). F5 — `graph:note:0` is refused (notes are numbered from 1), and
  the also-on hint for a note says "may be on" since a chart numbers only the notes in its window (`MainFrame.alsoOn`).
  F8 — spec D-SP8 and the report no longer claim every refusal preserves the selection. The reviewer's first-time
  named-note regression was aligned to the "may be on" wording (`66f2a32`) after `a4c38bf` was pushed with that one
  display case still red — a gating slip, corrected within the minute; both commits are in main's history.
  **Verified by the author:** clean suite 1654/0/0/31; `NamedGraphAndMenuSpotlightFrameTest` 4/4 on a real display and
  under CI's xvfb; `tools/verify-m64-spotlight.py` 94/94; docs build, links, sweep.
  **The reviewer must still check:** (1) `graph:note` (a chart literally named note) parses to GRAPH with graph="note"
  while `graph:note:2` stays the bare form — confirm that asymmetry is acceptable and documented; (2) the "may be on"
  hint never names the chart the call was about; (3) nothing else in the branch changed between `c635882` and merge
  (`git diff c635882 66f2a32 --stat` should show only the parser, `alsoOn`, the two tests, the spec, the report).
