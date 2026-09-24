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

---

## 2026-09-24 — three documentation commits by the M68 session, straight to `main`

These change specs, the tracker and a proposal, not code, so `mvn test` is not the check that matters. They are
logged because two of them change **release-gate state** and one of those corrects a false closure written by
the same session hours earlier. Earlier commits the same day (`610d5777` spec v2, `abb4a697` spec v3 and the
archive sweep) were reviewed in rounds 3 and 4 and are not listed.

- ☐ **`b4dbc2bd` — round-4 corrections to the M68 spec and both trackers.** *What & why:* folds in round 4
  (`review/m68-v3-tracker-2026-09-24`, `c583edf3`). **SG-2 was split** after the session had recorded it as fully
  closed from a claim copied out of a CI run: acquisition, setup and validation on the hosted archive are closed;
  hosted changed-graph generation/run is not. Three archived obligations were given live homes (OBL-1 background
  load progress/cancel, OBL-2 the autoscale-Y drag rescan, OBL-3 the first-key-seen iteration-order acceptance).
  The freshness policy, disposition table and safety argument in the spec were corrected. *Files:*
  `docs/specs/spec-evidence-integrity.md`, `docs/specs/tracker.md`, `docs/specs/completed/tracker.md`.
  *Verified:* full suite 1,876 at the time, `SpecLinksResolveTest`, the public sweep; the SG-2 split re-checked
  against the CI run's job list with `gh run view 35929392911 --repo telaminai/fluxtion-web`.
  **Reviewer must still check:** that the SG-2 split is now right in **both** directions — neither the earlier
  "NOT READY" nor the brief "fully closed"; that OBL-1 to OBL-3 are genuinely unfinished rather than obsolete
  wording; and that no other archived item carries the same pattern.
- ☐ **`aacc1ed4` — six hero framings added to the positioning proposal.** *What & why:* additive only, 202 lines,
  none removed; four framings by audience, two recorded from another session, and eight accuracy constraints
  each written after a draft made a claim the product does not support. *Files:*
  `docs/proposals/positioning.md`. *Verified:* every original heading still present; the public sweep.
  **Reviewer must still check:** each accuracy constraint against current product behaviour, especially that
  repeatable analyses are shipped (claimed) and that handing one artefact to a colleague is not (claimed). The
  file is public: check nothing in it would read as a published capability claim before the notebook work lands.
- ☐ **`296b5438` — the M68.1 cold-start brief, rebased onto 1.19.1.** *What & why:* the brief half of the M68.1
  cycle; also records in acceptance 8 that 1.19.1's named-profile fix overtakes the anchoring half of that
  acceptance. *Files:* `docs/handoff/handoff_m68_1_coverage_pairing_scope.md`,
  `docs/specs/spec-evidence-integrity.md`, `docs/specs/tracker.md`. *Verified:* full suite 1,877 on the rebased
  tree; `SpecLinksResolveTest`; the public sweep. **Reviewer must still check:** that the brief's source
  locations match the code, and that the acceptance-8 boundary agrees with the 1.19.1 fix's own tracker note.

The M68.1 **implementation** itself is not on `main` and is not in this ledger: it follows the normal cycle on
branch `feat/m68-1-coverage-pairing-scope`, with its report at
`docs/handoff/report_m68_1_coverage_pairing_scope_2026_09_24.md` on that branch.

---

## 2026-09-24 — Project-panel and chart-lifecycle work, straight to `main`

Eight commits from a session working primarily in a downstream repo (maker-fxoc), driving the analyser
against a live audit log. **Three change application code.** They were not logged here as they landed,
which they should have been; this entry is written after the fact by the same session, so read it as a
statement of what to check rather than as assurance.

**Two independent reviews already cover part of this** and are the place to start, not this ledger:

- `docs/handoff/review_project_panel_chart_lifecycle_2026_09_24.md` (on `main`) — covers `35eeb320`,
  `43ce82fc`, `a27b4d13`, `38ecc7f3`, `1e51545d`. **Verdict: reject.** It found two critical regressions in
  the very commit written to stop chart loss, and that the fix had no effective test coverage. Its status
  header tables all twelve findings against the commit claimed to fix each — those claims are the author's
  and want checking.
- `review/chart-style-fixes-2026-09-24` (`25dad52b`) — covers `90746e83`. Both fixes confirmed working,
  two findings left open (below).

**Unreviewed, and the scope for the next reviewer:**

- ☐ **`f6e8d7e0` — chart name collisions, rename, delete ordering, and testability.** *What & why:* fixes
  R1–R5 of the review above. Deleting one chart could silently destroy a different, closed, annotated one:
  `deleteCurrent` ran its fallback `addGraph()` (which ends in a save) before dropping the definition, and
  a generated name could land on a closed chart because `doRestore` resets the counter and skips closed
  charts. `GraphTabs` now takes a supplier of every name the project knows; rename refuses a taken name and
  moves the stored definition; `openSaved` fires a change so a reopen persists. The merge moved out of
  `MainFrame` into `SavedGraphMerge` because `MainFrame` is not headless-constructible — the review had
  reverted the merge to its destructive form with the whole suite still green. *Files:* `GraphTabs.java`,
  `MainFrame.java`, `GraphSpec.java`, `SavedGraphMerge.java` (new), two new tests. *Verified:* suite 1,908;
  mutations — reverting the merge fails 10 assertions, removing the closed-chart skip 3, `openSaved`
  without its change the named one. **Reviewer must still check:** the merge against renames, duplicate
  names, project switching and the placeholder fallback; and the author's own admission that reverting the
  delete *ordering* alone does NOT fail a test, because name reservation makes the collision impossible
  either way — judge whether that defence-in-depth argument holds.
- ☐ **`f1693c93` — the delete path made reachable by a test.** *What & why:* `deleteConfirmed` split from
  the modal dialog. *Files:* `GraphTabs.java`, one test. **Reviewer must still check:** nothing else calls
  `deleteConfirmed` without confirmation.
- ☐ **`1247aab4` — two false claims by this session, corrected.** *What & why:* the "saved-chart rows
  rendered a dead Open button" history was invented — at `35eeb320^` no button was rendered at all — and it
  had reached the D-L3 amendment and the shipping CHANGELOG; and the restore proposal's central claim (two
  captured roles) was false, there are four, plus an applied `view` map. **Reviewer must still check:** that
  the corrections are complete, and that the D-L3 amendment now rests only on the report leg.
- ☐ **`6145acdf` — the review landed on `main`, plus R10 and R12.** R10: `spec-project-starter-journey.md`
  said "Keep `ProjectPanel.Navigator` unchanged", contradicted by `35eeb320`; the supersession is now
  recorded there. R12: the CHANGELOG's "exactly as they did before" corrected — the first save after
  upgrading does add a style key to every chart. **Reviewer must still check:** the supersession is accurate
  and that no other governing document still contradicts the amendment. The amendment was originally made
  without sweeping for other specs, which is how R10 arose.
- ☐ **`b8197eb9` — milestone-number clash removed.** This session invented `M68.2`–`M68.5` as labels and
  stamped them across 21 files including the D-L3 spec and ONBOARDING, colliding with the **active M68
  evidence-integrity milestone and its named future slices**. Replaced with the commit sha each change
  landed in. *Verified:* the five genuine M68 documents were excluded; suite green. **Reviewer must still
  check:** no sha substitution misattributes a change to a commit that does not contain it.
- ☐ **`84a8133c` — zoom versus pin, in the restore proposal.** Zoom is a lens and is never persisted; pin is
  `graph.N.from`/`to` and is. Both sit on one toolbar and nothing says which is kept.

**Known open, carried forward — confirm rather than rediscover:**

1. **The `SettingsShare` regression test misses its call site.** `StyleDropdownRequestsASaveTest` exercises
   `GraphSpec.withExternal` directly, never the importer, so reverting `SettingsShare.java:~350` to the
   shorter constructor leaves the suite green. Needs a round-trip through the real import path;
   `SettingsShareTest` already has the harness.
2. **Import refresh loses incoming changes** — importing Points/closed over an open Line chart restores the
   old Line/open state. Pre-existing, reproduced by the second review, unfixed.
3. **Nothing here is verified at a real display.** The 62 skips are display-gated `*FrameTest` classes and
   are not passes. Unverified: the Delete dialog appears and reads correctly; Cancel changes nothing; Close
   keeps the chart and its row; Open reopens with notes and pin intact and does not discard a series added
   since; a reload leaves a closed chart closed; alternating two report rows reveals the right one.
   **Do not substitute an MCP verb for a button press** — this session did exactly that once and reported a
   false all-clear from it.

**A caution about this session's claims generally.** Three times it asserted something about this repository
without checking: the dead-Open-button history, the restore proposal's capture model, and the M68 numbering.
Each was cheap to verify — `git show 35eeb320^`, a fifteen-line file, one `grep`. Commit messages and the
review status header are claims by the author, not findings.

**Related, not in this ledger:** PR branch `fix/chart-delete-cancel-and-revealer` (`bd5cfe40`) closes the
review's R9 and the Cancel gap, and touches `GraphTabs` and `MainFrame` — a fix branch cut from `b8197eb9`
should expect conflicts there.
