# Analyser 1.19.0 — release execution

Local branch: `release/java-source-spotlight-1.19.0`. Rebased feature source: `d3b98982`.
Base: `f91919eb`. Original independently reviewed implementation: `4558babc`.
Source tree is identical across the rebase. Removing the inserted spotlight section from the tracker
reproduces `f91919eb` byte-for-byte, including its Spring-side work block. The additional release notes
state that Java highlighting is requested through an assistant, not activated by opening a log.

No force-push, merge bubble, push to main or release has occurred. The published feature branch and
PR #6 remain intact at `4558babc`. The owner initially requested a pre-push re-verification, then explicitly instructed the author to
proceed on the existing independent reviews and the passing rebased gates (2026-09-23). No further
review round is required; the source tree remains identical to the reviewed implementation.

| Rebased gate | Result |
|---|---|
| `mvn -q clean test` | 1,876 / 0 failures / 0 errors / 62 headless skips |
| Exact twelve-suite display list in CI | 63 / 0 failures / 0 errors / 0 skips |
| Mutation witnesses | 13/13 green baselines, named assertion failures, exact restoration |
| `python3 tools/test_tools.py` | passed |
| Strict MkDocs | passed |
| Packaged agent API / handoff / spotlight | passed; spotlight 94/94 |
| Separate-JVM restart / restore | passed using canonical output root; original path-alias assertion below |
| Generated conversations | all scenarios complete; five native captures; transcript refreshed |
| Rule-1 sweep / diff check / new author addresses | clean / clean / personal |

[Gate receipt](evidence/java-source-spotlight-1.19.0/gates.json).
[Rebased mutation results](evidence/java-source-spotlight-1.19.0/mutations.json).
The reviewer's verdict and low-finding response remain in the original handoff packet.

## Release sequence

Re-fetch main and fast-forward it without rewriting history; watch main CI and Pages. Dispatch
`release.yml` on main with `version=1.19.0`, watch the workflow and its explicit Pages dispatch, then
verify published jar checksums, version manifest and release notes. Results are recorded below as
steps complete.

Analyser only. No skill changed, so no playground re-vendor. Mongoose socket work and its remaining
UP-MON-01 export work are not part of this release. No new LLM session or starter battery is required:
this change does not alter acquisition or starter routing surfaces.

## Host-path limitation encountered

The first restart-check attempt reached its final CLI scenario with the correct explicit log and
restore offer but compared `/var/...` with `/private/var/...` as different strings. The checker failed;
that is retained in the gate receipt. Re-running the supported `--output` option under `/private/tmp`
passed all three independent JVM checks, including restore, dismissal and CLI isolation, with no product
or checker changes. The checker should eventually compare canonical paths; this is not presented as a
fixed portability defect.

Conversation capture completed all scenarios and captured five native images. Four were byte-identical;
the fifth was visually compared with its prior image and differed only in the worktree source-root path,
so it was restored. The generated transcript's new `streamEnd` facts are retained. All visible data are
the neutral demo fixture; no user profile was used.
