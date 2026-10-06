# Analyser 1.32.0 release evidence

Status: published and verified on 2026-10-06.

Candidate: `4ffd0ac62c7b9e1f762783c374cbb59bdfef9381`, main after PR #123. This release includes
PR #108 (the walkthrough reel, #82, with its text redacted by the bundle's own rule, #113), PR #88
(bundle import off the event thread and working-copy reaping with cross-process ownership, #83 and #85),
PR #107 (MG-1 mutation-gate iteration), PR #105 (EDT-fault and native-input test guards) and PR #123
(the assistant's settings-save redraw, pinned by a test and a mutation control; supersedes #78).

PR #88 and PR #108 each had a correction-delta review before merge. #88's restored a released 1.30.0
changelog line its corrections had overwritten. #108's found that the F1 fix's own decision
(`logRelationTo`) had no control — all 25 reel tests stayed green with it forced to "covered" — and added
one. Each was merged only against complete CI evidence for the exact merge candidate.

## Executed checks

Counts below are total / failures / errors / skips.

| Check | Result |
|---|---|
| JDK 21, `mvn -B -o package` at the candidate | 3228 / 0 / 0 / 263; 425 Surefire reports |
| Main CI build, run 37456461803 | 3228 / 0 / 0 / 263 |
| Main CI display, same run | 261 / 0 / 0 / 0; 45 frame suites, none skipped |
| Main CI mutation collector, same run | complete; 664 controls caught across eight shards; byte-identical restores; revision = candidate |
| `python3 tools/verify-m46-agent-api.py` | 16 checks; no failures |
| `python3 tools/verify-m48-handoff.py` | 18 checks; no failures |
| `python3 tools/verify-m64-spotlight.py` | 94 checks; no failures |
| `python3 tools/verify-session-restart.py` | PASS; five checks across three independent JVMs |
| `python3 tools/capture-conversations.py` | Seven scenarios completed; five native screenshots captured, no warning |
| `mkdocs build --strict` at the candidate | Exit 0 |
| Whitespace (`v1.31.0..candidate`) and tracked public-data sweeps | Clean |
| Starter evidence static checks on the candidate | Success |

The four smoke scripts and the conversation capture ran sequentially under the shared display lock,
against the built jar, with isolated homes and DEMO data. No hosted provider, compiler key or model-client
session was used. The full mutation gate was read from CI, not repeated locally.

**Conversation capture.** Every screenshot was read by eye. The only differences from the committed set
were the capture run's scratch directory name in the Context column and the window's focus state; the
transcript changed only in JSON member order. Per the procedure, the original bytes were restored and no
documentation commit was made, so the candidate stayed as CI verified it.

**Person at the screen.** A reel was recorded from the built release jar against the DEMO set (a three-step
walk over a record, a node and the pairing verdict) and read by the owner, who approved it. The page's text
contained no machine path; its title page states that frame images are not redacted; its finish page states
plainly that it was not captured from an evidence bundle. The tracker's other person-at-the-screen items
(the onboard assistant's OA-1…OA-6 live, native and cross-machine acceptance) were open at 1.31.0 and remain
open; OA-1 needs an authorised live-provider run that was not made.

## Attempts retained

- Main CI on the PR #105 merge (`3d7767bd`) failed one control, `mouse-loss-column-adjustment`, as
  `not-restored`: source and classes restored byte-identically, but the restored run errored with
  `nativePressDeliveredBeforeGestureChecks: the desktop dropped all three presses`. PR #105's own guard
  reported a desktop that did not deliver input rather than scoring it. A re-run passed.
- PR #88's run at `1e353e50` failed shard 3 in setup, before any control ran: `No plugin found for prefix
  'dependency'` (Maven plugin resolution). A re-run passed; the head later moved to `6730000a` and was gated
  in full.
- PR #123's first CI attempt failed `ui-frame` at a `control:` precondition in an unrelated suite,
  `PersonAtTheScreenFrameTest` (a combo popup did not open on the virtual display). The new test was checked
  for leaked windows first — its Settings dialog's OK disposes it, and the dialog is owned by the frame — and
  a single re-run passed. Its mutation gate on the first attempt was already complete.
- A CI watcher for PR #123 first reported the separate starter-evidence workflow, which finished before CI.
  The collector check before merge found no artifact and caught it; the merge waited for the real CI run.
- The first reel for the owner's check was refused because file exchange is off in a fresh isolated home,
  the correct default. It was recorded again with exchange enabled in that home.

## Commit identity

Personal commit identity was checked (`user.email` is the personal address). The release stamp uses the
documented GitHub Actions bot identity. **Two restricted-domain authors were introduced since 1.31.0**, and
the owner accepted them for this release: the merge commits of PR #88 (`a636959f`) and PR #108
(`35c2f78d`). Both were made with `gh pr merge`, which stamps the GitHub account's default commit email.
They are public history on `main`; rewriting `main` would breach the trunk rule and every clone, so they are
recorded here instead. PR #123's merge (`4ffd0ac6`) set the author explicitly, and later merges should too,
or the account's default commit email should change.

## Remaining work is not claimed complete

- **#119**: no bundle opens on a filesystem without `fcntl` locks, where opens worked before PR #88 — a
  capability regression on those filesystems. Found by reading, not reproduced.
- PR #88's other follow-ups: #118, #120, #121. PR #108's: #114–#117 and #122, including an owner decision
  (a CJK-named source file in a failing step blocks the reel).
- MG-1 acceptance: MG-A3's main/release event isolation was source-reviewed, not exercised; MG-D6 and
  MG-A7/A8 remain deferred.
- **The published `SHA256SUMS.sha256` lists the versioned jar as `target/fluxtion-auditlog-analyser-1.32.0.jar`,**
  a build path, so `shasum -c` reports it as unreadable while the stable-name line verifies. The bytes are
  correct (below). This is not new: 1.31.0 and 1.30.1 carry the same prefix. The release workflow should write
  asset names.
- M70 whole-feature assurance (PR #70), the onboard assistant's acceptance, and the other tracker items
  retain their status.

## Publication

- [Release 1.32.0](https://github.com/telaminai/fluxtionauditlog-analyser/releases/tag/v1.32.0), published 2026-10-06 11:39:18 UTC.
- Tag: `v1.32.0`, commit `2aee7a9e37b0b0df5803813b11a6bb445ab8bf6e` (the changelog stamp; its parent is the candidate).
- [Release workflow 37457710639](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37457710639): success; `mvn -B verify` returned 3228 / 0 / 0 / 263.
- [Release docs deployment 37457841298](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37457841298): success; the public release-notes page was checked for the version.
- Downloaded the versioned jar, the stable-name jar and the checksum file. Both jars are 4,699,294 bytes and
  byte-identical. SHA-256: `f125050b33a1ac4c62682927f46b74cfd68b683bb2511c693f1bd63fd9d8c149`.
- The manifest declares `Implementation-Version: 1.32.0`; the jar bundles the stamped changelog, with
  `[1.32.0] - 2026-10-06` (12 entries) under a fresh `[Unreleased]`, matching the release body.
- Separately downloaded the public `releases/latest/download` jar; its hash matches.

All main application CI evidence was read directly. This publication does not claim the open acceptance
items, follow-up issues and owner decisions above are complete.
