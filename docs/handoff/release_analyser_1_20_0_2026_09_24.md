# Analyser 1.20.0 release receipt — 2026-09-24

Status: **RELEASED and public assets verified** — tag `v1.20.0` at `67c672cc`.

## Scope

Owner authorised 1.20.0 from merged main. Candidate `2a15f0ba` includes PR #15's Project,
Sources and Audit log menus, PR #17's menu guards/README correction, and PR #18's fast mutation
engine (proposal PR #16). The user-visible notes are the existing CHANGELOG section, stamped
by the release workflow. Saved `menu:File…` spotlight steps require the new visible menu names.
The chart fixes were already released in 1.19.2/1.19.3. This release does not include the separate
Mongoose audit-production or M68 implementation branches, nor a compiler/playground release.

## Checks run locally

JDK 21, isolated release worktree at `2a15f0ba`; no application source changes. Counts below are
**total / failures / errors / skips**, never skips presented as passes.

| Command | Result |
|---|---|
| `JAVA_HOME=<JDK21> mvn -q clean package` | **1985 / 0 / 0 / 97**, 265 source-mapped suites, no orphan reports |
| `python3 tools/verify-m46-agent-api.py` | Passed, 24.1 s |
| `python3 tools/verify-m48-handoff.py` | Passed, 12.8 s |
| `python3 tools/verify-m64-spotlight.py` | 94 checks passed, 73.2 s |
| `python3 tools/verify-session-restart.py --output <isolated-output>` | Passed, 15.4 s; separate JVMs, explicit restore and CLI isolation |
| `python3 tools/capture-conversations.py --require-images` | Seven scenarios and five native images completed |
| `python3 tools/test_project_chart_review.py` | 5 tests, passed |
| `mkdocs build --strict` | Passed |
| `git diff --check` | Passed |

[Package counts](evidence/release-1.20.0/package-counts.json),
[preflight commands, timings and log names](evidence/release-1.20.0/preflight.json).
The built-jar scripts ran serially under isolated homes. They drive action-socket behaviour;
they are not substitutes for menu-button tests. No new LLM session or compilation key was used.

## CI inspected, not re-run locally

[Main CI at the candidate](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36065365418)
is green: build, loop-bench, ui-frame and mutation-gate. Its log reports **98 / 0 / 0 / 0** for
the display suite and **50 controls caught** by the fast engine in **327.6 s**. The compile-fallback
selftest includes the annotation-retention and inlined-constant counterexamples. The full mutation
and frame gates were not rerun locally for this release; these results are from the CI log.

## Documentation and image inspection

The three dedicated menu screenshots already show the new layout and were inspected. All five
conversation images still showed File; this run regenerated and inspected them with the new
menus, using only neutral demo data. The transcript's calls completed; its changed JSON field
order is not a changed result. Its old-menu caveat and the generator's matching text now say
when the screenshots were refreshed. Specialised historical captures on other pages retain
their explicit older-layout caveats; this is not a claim that every historical image was recaptured.

The release procedure now points at the executable CI/release workflows instead of stale YAML
copies, describes the display and mutation jobs, seven conversation scenarios, and post-publication
checks. CLAUDE.md's obsolete assertion that Swing has no CI tests is corrected. Existing
ONBOARDING guidance already describes the display gate correctly and is unchanged.

## Identity and remaining decisions

The local email is the personal address. The source rule-one sweep excludes only its two rule
files and is checked before commit. Metadata inspection found two additional web merges since
1.19.3 (`7254c29d`, `2a15f0ba`) using non-personal metadata. The repository-local pin does not
control GitHub web merges. This extends the existing D3 owner follow-up; no history was rewritten.
The release workflow uses the approved GitHub Actions bot identity, and this documentation commit
uses the personal identity explicitly.

Menu D1 (CSV placement), D2 (no legacy File spotlight alias), D3 (web-merge identity), the
unreproduced spotlight flake and optional guard refinements remain live. Shipping the reviewed
menu behaviour does not decide those policies or claim a focus-stability improvement.

## Publication verified

[Release 1.20.0](https://github.com/telaminai/fluxtionauditlog-analyser/releases/tag/v1.20.0),
[release workflow](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36067298957)
and [release Pages deployment](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36067389846)
all succeeded. The workflow stamped CHANGELOG.md; both downloaded jars match the published
SHA256SUMS, carry `Implementation-Version: 1.20.0`, and bundle the menu release notes.
The latest-download route returns the same bytes, and the live release-notes page contains 1.20.0.
[Downloaded asset hashes](evidence/release-1.20.0/public-assets.json).

The release tag precedes this documentation-only receipt, tracker housekeeping and regenerated
conversation images. The published application code is the tested candidate; the docs follow-up
changes no application code and does not restamp the release.

## Documentation commit check

After staging the refreshed images, transcript, release receipt and tracker updates, `mvn -q test`
passed again: **1985 / 0 / 0 / 97**. An initial sandboxed attempt had **0 failures, 29 errors**,
all localhost socket binds refused by the sandbox; the unrestricted rerun above passed. Strict
MkDocs, diff whitespace checks, new report/procedure links and the exact tracked rule-one sweep
passed. This is a documentation follow-up, not another mutation or real-display run.
