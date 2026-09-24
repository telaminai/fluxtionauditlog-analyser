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

- ☑ reviewed 2026-09-24 — **SG-2 split confirmed** against run 35929392911's own artefacts (three jobs, no generation job, `generationAttempted: false` on both Spring results); OBL-1/OBL-2 confirmed open in source, OBL-3 not inspected. **Disputed:** `tracker.md:1094–1098` still restates the overturned closure, and three archived items (H8.6, A10.7, M14.6) have no live home. See the M68.1 [review](review_m68_1_coverage_pairing_scope_2026_09_24.md) ▸ question 11.
  **`b4dbc2bd` — round-4 corrections to the M68 spec and both trackers.** *What & why:* folds in round 4
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
- ☑ reviewed 2026-09-24 — every accuracy constraint checks out against the product; not published on the site; sweep clean. **Disputed, minor:** one hero draft says the analyser "opens it as a notebook", which presents ND-2 (☐) as shipped. See the [review](review_m68_1_coverage_pairing_scope_2026_09_24.md) ▸ question 11.
  **`aacc1ed4` — six hero framings added to the positioning proposal.** *What & why:* additive only, 202 lines,
  none removed; four framings by audience, two recorded from another session, and eight accuracy constraints
  each written after a draft made a claim the product does not support. *Files:*
  `docs/proposals/positioning.md`. *Verified:* every original heading still present; the public sweep.
  **Reviewer must still check:** each accuracy constraint against current product behaviour, especially that
  repeatable analyses are shipped (claimed) and that handing one artefact to a colleague is not (claimed). The
  file is public: check nothing in it would read as a published capability claim before the notebook work lands.
- ☑ reviewed 2026-09-24 — every source location the brief names matches `main` at that commit; acceptance 8 agrees with the fix's own note. **Disputed, minor:** `tracker.md:557–562` still says the named-profile fix is "Pending merge/release"; it merged (PR #7) and shipped in 1.19.1. See the [review](review_m68_1_coverage_pairing_scope_2026_09_24.md) ▸ question 11.
  **`296b5438` — the M68.1 cold-start brief, rebased onto 1.19.1.** *What & why:* the brief half of the M68.1
  cycle; also records in acceptance 8 that 1.19.1's named-profile fix overtakes the anchoring half of that
  acceptance. *Files:* `docs/handoff/handoff_m68_1_coverage_pairing_scope.md`,
  `docs/specs/spec-evidence-integrity.md`, `docs/specs/tracker.md`. *Verified:* full suite 1,877 on the rebased
  tree; `SpecLinksResolveTest`; the public sweep. **Reviewer must still check:** that the brief's source
  locations match the code, and that the acceptance-8 boundary agrees with the 1.19.1 fix's own tracker note.

The M68.1 **implementation** itself is not on `main` and is not in this ledger: it follows the normal cycle on
branch `feat/m68-1-coverage-pairing-scope`, with its report at
`docs/handoff/report_m68_1_coverage_pairing_scope_2026_09_24.md` on that branch.
