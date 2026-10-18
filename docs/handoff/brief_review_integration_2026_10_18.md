# Independent review — the 2026-10-18 integration

_The integration branch and tip are filled in (2026-10-18). **v2:** review `test/usability-2026-10-18b`, which adds `fix/wizard-scorecard-regressions`, `fix/owner-test-small`, `feature/propose-starter` (draft #167) and `fix/starter-followups`; its full gate ran at `ddccd5c5` (review map §6), and the commits after it change only the review documents. v1 (`test/usability-2026-10-18`, gated at `5971608c`) is §5._

Review the integration branch **`test/usability-2026-10-18b` at its tip** (gated at `ddccd5c5`) together with the PRs it gathers. It is M74's P3
plus seven fix branches, Option 2 (a starter proposed by Claude Code, draft #167), M77-1 to M77-5, M78 D1 and D2, the usability scorecard and the spec branches. Use a separate
worktree and your own review branch; the owner's checkout and other sessions stay untouched. This brief does not
permit you to merge, release, rebase or force-push.

Read, in order:

1. `CLAUDE.md`. Rules 1 (public repo, the sweep), 4 (tests gate), 6 (read the source of truth) and 9 (one dispatch
   model) are the ones this review leans on.
2. `docs/handoff/REVIEWER-ORIENTATION.md`, all of it. §6 is this review: the stack's shape, the seams, rule 9, the
   customer API, the safety surfaces, hygiene and the fix policy.
3. `docs/handoff/review_map_integration_2026_10_18.md`. It lists every branch and PR with its tip, base, spec, claims
   and evidence, the **fourteen flags** (§2) and **eight more for v2** (§6), the known gaps and the full-gate results (§5 for v1, §6 for v2).
4. The specs, for the parts you judge: `spec-claude-code-provider.md` (M74), `spec-ai-setup-wizard.md` (M77 r5),
   `spec-quiet-assistant-panel.md` (M78), `spec-first-minute.md` (M79, for what M77 must leave room for), and
   `docs/usability/scorecard.md`.
5. The diff: `git diff a875fcba..origin/test/usability-2026-10-18b` (v2 alone: `git diff origin/test/usability-2026-10-18..origin/test/usability-2026-10-18b`), then each PR's own diff for anything the seams point you to.

## What to attack

- **Seams.** The branches were checked one at a time; the defects that remain are where two of them meet. Look at the
  regenerated `SessionProcessor` (it must come from the merged nodes, not a hand-merge), `MainFrame`,
  `AssistantPanel`, `StartPanel`, the snapshot/effects/events, the CI frame-suite list, `CHANGELOG.md` ▸ Unreleased,
  and the docked column at 1200×800 and 1200×680, where the quiet panel, the wizard and the records fixes all land.
- **Rule 9.** Every new piece of state is a node, an effect and a fact. In the generated order, each new node
  (`aiSetup`, `recordsView`, `startPageNoLogHint`) reads its parents after they handled the fact. No listener
  dispatches by hand, recomputes a value or composes a status line. `OneDispatchModelTest` keeps every branch's
  additions.
- **The customer API.** `WorkModeKeepsTheApiTest`'s digests are unchanged. `context`'s `hidden` and the two bridge
  headers are additive and nothing else moved. A renamed, removed or re-typed field is a blocker.
- **Safety.** Flags 1–5, 11 and 14: executable bits, the in-project `open` pre-allow and its time-of-check residual,
  `STAY_IN_PROJECT_NOTE` as a request rather than a sandbox, Full acceptEdits preselected, the external-access
  confirmation at every door, and an auth parse that keeps email and organisation out. Check that each claim made in
  the FAQ or trust dialog is true of the code.
- **Plain text.** Everything a person reads is plain words: no milestone ids, no internal jargon, no auditor prose on
  a first view. Count the words the specs budget, on the merged build.
- **Hygiene.** The CLAUDE.md rule 1 sweep prints nothing; read every new screenshot yourself; no absolute local
  paths are committed; the repo-local author email is the personal one, including on merges.

Say what you did **not** check. A test that matches nothing passes for the wrong reason, so for each fix you rely on,
make the change that should turn its test red and confirm it does.

## Gates

Run the full gates on the integration tip yourself (JDK 21; one Maven process per checkout): `mvn test`, the
registered frame suites under Xvfb with no skips, `mvn package`, `mkdocs build --strict`, `git diff --check`. Report
passes and skips separately. Compare your results with the map's §5; a gap between the two is a finding.

## Output

Write `docs/handoff/review_integration_2026_10_18_<reviewer>.md` on your review branch:

- the reviewed SHAs, and a verdict for the integration and for each PR;
- **findings ranked by severity**, each tagged to its PR or branch, with file:line, the **failure scenario** (what a
  person or client does, and what goes wrong), and **a fix**;
- for each flag, accepted or not, and why;
- what you verified, what you only read, and what you did not check.

## Fix policy

**Fix the findings yourself unless the fix needs a redesign.** Make one commit per finding on your review branch, with
its regression check (CLAUDE.md rule 8). Put a redesign to the owner as a finding with a proposed design, and do not
build it. **Give the owner the commit range of every fix** (branch, first and last SHA). Authorship cannot identify a
reviewer's commit, so the owner reviews the delta from the range. Push only your review branch.
