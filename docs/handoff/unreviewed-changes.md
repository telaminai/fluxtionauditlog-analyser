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

## 2026-09-17 — the answers to the M46-closure re-review's R4, R5, R6 reached main unreviewed

- ☐ **`442582f`** `fix(spotlight): re-review R4, R5, R6 - a label must name ONE series; a row the log lacks is refused before goto`
  **Why it is here:** the block `fix/m46-agent-api-closure` was re-reviewed by two readers who DISAGREED — READY WITH
  FOLLOW-UPS, and NOT READY (R4, R5 required). The owner authorised the merge on the first verdict. The author judged
  the second reader right on both points, fixed them BEFORE the merge, and merged. So this one commit is on main
  without any reviewer having seen it. Everything else in the block was reviewed; see
  [`completed/sha-map_m46_closure_branch.txt`](completed/sha-map_m46_closure_branch.txt).
  **What & why:** R4 — two legend rows can read identically (the same external spec given twice; a formula labelled
  with the legend's own `  (external)` suffix) and `graph:series:<label>` lit the first; now refused, saying how many
  share the label (`GraphPanel.legendMatches`, `seriesLegendMatches`; `MainFrame` refusal text). R5 — `goto` CLAMPS an
  index, so `spotlight {target: "records:row:99999"}` relaxed the filter, selected the LAST record, then refused
  mentioning neither; `SpotlightTarget.precheck` now takes the log's record count and refuses first
  (`ActionExecutor.doSpotlight`, `MainFrame.applySpotlight`). `goto`'s clamping is untouched. R6 — the in-app manifest
  inventory test accepted a prose word as a parameter; it now requires a declaration. Also R2 (four stale "handoff
  verb" mentions), R3 (`context.showing` ordered), `.gitignore` `.mcp.json`, `docs/admin/release-process.md` §4.0.
  **Verified by the author:** each of R4, R5, R6 SEEN RED under mutation; `SpotlightSeriesLabelTest` +3,
  `SpotlightSetTest` +1, `SpotlightFrameTest` (real frame) reproduction 3; both reviewer reproductions on the built
  jar — `tools/verify-m64-spotlight.py` 78/78; full gates on the MERGED tree (1615 / 0 / 0, 21 display skips).
  **The reviewer must still check:** (1) R4 — I refused at the POINTER rather than enforcing unique labels at the
  `graph` verb's entrance (the reviewer's alternative); is that the right contract? (2) R5 — `precheck`'s record count
  is `store.index().size()`: is that the same index `goto` clamps against for a ROLLED set and while Follow is
  appending? (3) R6 — the declaration regex: can a parameter still be "declared" by prose ending in `:` or `,`?
  (4) M64.10, found on the way — a graph target addresses the SELECTED chart only — is it a defect or a limitation?
  Response file: [`completed/response_rereview_m46_closure_branch.txt`](completed/response_rereview_m46_closure_branch.txt).

## 2026-09-17 — M64.8 + M64.9, done on main after the merge, as the owner directed; not yet reviewed

- ☐ **`01b6a4f`** `skills+spotlight: M64.8 point-at-the-fault …; M64.9 coverage -> topology:verdict` and
  **`473482d`** `skills: re-pin the m19-skills/2 index …`
  **What & why:** ONE skill edit and ONE re-pin, before 1.14.0. M64.8 — a new canonical skill,
  `docs/skills/common/point-at-the-fault/SKILL.md`, the shape of a fault-finding runbook that ends by spotlighting
  the evidence it matched (spec-spotlight ▸ M64.8's two rules, nearly verbatim). It joins `m19-skills/2` ▸ `common`, so
  THE SELECTION GREW BY ONE for every template. M64.9 — the spotlight target that lights the Topology tab's pairing
  line is `topology:verdict`, not `coverage`: rename, NO alias (the verb is unreleased).
  Files: the skill; `guided-start/SKILL.md` (one line); `docs/skills/README.md`; `m19-skills/2/index.json`;
  `SpotlightTarget`, `SpotlightVocabulary`, `MainFrame` (three `case` labels); `SpotlightTargetTest`,
  `CanonicalSkillsTest` (the discoverable set); `tools/verify-m64-spotlight.py`, `tools/capture-docs.py`; user guide,
  spec, tracker, CHANGELOG, CLAUDE.md.
  **Verified by the author:** every number in the skill's worked example was read off the built jar BEFORE it was
  written, and `tools/verify-m64-spotlight.py` now replays the example call for call (83/83); `coverage` as a target
  is asserted UNKNOWN; full suite 1618 / 0 / 0 (21 display skips), display command 154 / 0 / 0 / 0, `mkdocs --strict`,
  the rule-1 sweep; the pinned revision is an ancestor of main (`git merge-base --is-ancestor`).
  **The reviewer must still check:** (1) READ the skill as someone who has never seen the tool — does it make an agent
  point at what it FOUND, and light nothing when it finds nothing? Nobody has run it on a context-free client.
  (2) Adding to `common` changes what every generated bundle carries: is "additive, same contract" right, or does the
  playground's generator pin the `common` list? (3) No alias for `coverage` — confirm nothing outside this repo names
  the old target (the playground's vendored guided-start WILL until it re-vendors: that re-vendor is the owner's, and
  until it happens a generated starter teaches a target this analyser refuses). (4) `docs/site/assets/spotlight-findings-*.png`
  were NOT regenerated: the captions in them are unchanged and no target name is visible, but `capture-docs.py` now
  sends the new name and has not been re-run since.

