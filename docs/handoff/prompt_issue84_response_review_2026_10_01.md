# Independent review of the issue #84 third-review response

You are the independent reviewer, not the implementer. Judge the candidate; do not fix it.

Repository: `telaminai/fluxtionauditlog-analyser`.
Candidate branch: `fix/issue84-third-review-response-2026-10-01`.
Starting SHA: `258da371d957044625da4b88f5a433922f987b00`.
Fetch and report the actual candidate HEAD and PR CI revision. Use your own isolated worktree.
Do not modify another session's worktree or generated files. Do not merge or release.

Read, in order:

1. `CLAUDE.md` and `docs/ONBOARDING.md`.
2. Issues #93–#102, including their reproduction and regression requirements.
3. The original review and evidence at `5df6e097`, preserved on
   `review/issue-84-third-review-2026-10-01`:
   `docs/handoff/review_issue84_bundle_provenance_2026_10_01.md` and its evidence directory.
4. `docs/handoff/handoff_issue84_third_review_response_2026_10_01.md` and linked verification data.
5. The diff from the starting SHA, including the generated processor, tests, controls and docs.

#84 closed the review, not the defects. The response is author verification, not independent approval.
Scope is #93–#101 plus the explicit #102 disclosure decision. Do not re-review PR #87.

## Reproduce the boundaries

- **#93:** abort a verified bundle apply, replace its working profile at the same pathname, then independently
  open that profile. It must not inherit bundle identity. Attack operation reuse, loaded-content mismatch,
  the legitimate internal plan-less load, two successful bundles and an unrelated failure while a genuine
  bundle remains in force. Inspect the actual profile content being verified and applied, not only node state.
- **#94:** import GRAPHS over an existing chart. Assert incoming notes AND series on the live chart, in config
  and in the written profile. `applied=true` proves none of these.
- **#95:** offline source directory → reopen → REPORTS-only import → directory returns → reopen. Its remembered
  anchor must return. Deliberate deletion of a visible root must still stick. Judge the visible-root-delta rule.
- **#96–#98:** hold the real source/archive reader. Before completion check PREPARING, the accepted dialogue
  prefix, visible strip and empty overlay; after completion check what is actually lit. Invoke the native
  Previous-tab action during the hold, not merely a mouse click. Check cold lookup responsiveness, Next→Back
  with the original read held, invalid UTF-8, unavailable lines, expiry and stale completion.
- **#99:** permission and origin must be operation facts decided by a node. Disabled offers record skipped;
  legitimate person offers happen outside dispatch. A later socket operation, including the same profile path,
  must not inherit an earlier offer. Attack delayed startup presentation as well as ordinary project opens.
- **#100:** execute the exact nested-helper and inherited-Navigator mutants from the review. Both must compile
  and fail named assertions; retain direct-frame and extra-declared-action controls. Confirm the authorized
  Navigator-to-adapter route and the named action list remain intact.
- **#101:** read the real dialog. Project and machine origins are independent for logs and topology. Machine
  fallback remains intentional; it must not claim project membership.
- **#102:** the candidate chooses explicit name/line lookup, **without comparing the saved source revision**.
  CURRENT has not been redefined as content verification. Replay the `0.004` → `999` witness and judge whether
  both the visible caption and target reason adequately disclose this. Source/run pairing is a different claim.

## Verify rather than inherit numbers

Use JDK 21. Re-run `mvn -o -q test`; report totals/failures/errors/skips, report count, source mapping and orphans.
Run real display suites sequentially under `/tmp/fluxtion-analyser-display.lock` with
`-Djava.awt.headless=false -DargLine=-Djava.awt.headless=false`. Inspect the UI and screenshots. The response's
jar runner lets maintained witnesses run against an explicitly selected shaded jar without `target/classes`
substituting for application code.

Re-run targeted controls and neighbouring provenance, anchor and Java-walk protections. Compare REQUESTED and
CAUGHT **names**, not just totals: the ordinary gate is fail-fast. Require named assertion failures, source/class
byte-identical restoration and restored-green runs. Restore from byte copies, never checkout. Inspect the retained
unsuccessful attempts: two provenance controls were initially incomplete, and an offer test initially left an
independent guard effective. The final provenance mutant intentionally reintroduces the complete pathname-only
defect across two safeguards; it is not claimed to be a one-line mutation.

Run preflight, `tools/test_project_chart_review.py`, strict MkDocs, `git diff --check` and the documented public-data
sweep. Independently read exact-head CI: build, zero-skip ui-frame, mutation self-test, all four shards and the
collector. A pending, skipped or waived required gate is not green.

## Output

Write a review under `docs/handoff/`, with verdict **READY / NOT READY / CONDITIONAL** first. Give a disposition
for every issue, rank findings, and name file/line, exact sequence, observed wrong result and concrete remedy.
Distinguish confirmed results from plausible concerns. Include **WHAT I DID NOT CHECK**. Do not implement fixes,
push or close issues as part of the review. Use DEMO data only; sweep any public review text before publishing.
