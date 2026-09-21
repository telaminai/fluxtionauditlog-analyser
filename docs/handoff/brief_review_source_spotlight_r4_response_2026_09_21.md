# Review the source spotlight round-four response

Branch: `docs/source-spotlight-proposal`, based on main `401da35b` (v1.17.0).
This is a documentation-only proposal packet, not an implementation or a release request.

Read ONBOARDING and CLAUDE, then:

1. [Proposal](../proposals/source-spotlight.md), especially One lookup and acceptance 7.
2. [Round-four review](review_source_spotlight_r4_2026_09_21_claude.md).
3. [Round-four response](response_source_spotlight_r4_2026_09_21.md).

Judge U-1 against both real routes: source-glance through DesignWorkspace/DesignFiles, and Java
viewer lookup through SourceService. Confirm the proposal chooses the latter explicitly, preserves
the former, discloses first-match origin, and names a public repeat-spotlight reread path whose
future acceptance can reach the archive cache. Distinguish the proposed helper from an existing API.
Recheck the duplicate-root, jar-only, cached-miss and new-jar discovery cases in the contract.

Check L-1/L-2: correctly spelled proposal filename, every linked review/response committed together,
and a current-main tracker entry without stale implementation statuses. Markdown links in historical
reviews were corrected mechanically for the rename; verdicts/findings and quoted old paths remain.
Check L-3: implementation must record design scroll remeasurement as user-visible in CHANGELOG.
Preserve the prior decisions: Java clips, design refuses, node inference deferred, both CI frame
lists required. Do not reopen closed design choices without a concrete contradiction or new evidence.

Write `docs/handoff/review_source_spotlight_r4_response_2026_09_21_<reviewer>.md` on a separate review
branch with verdict, numbered findings, pinned source revisions and verified-versus-read evidence.
Do not implement, change the proposal or other reviews, merge, release, or touch another checkout.
Commit/push only your report if using the review branch; no runtime or LLM trials are needed.

Packet validation on current-main base: `mvn -q test` — 1,767 tests, zero failures/errors, 49 headless
skips. Strict docs, local packet file links, whitespace and tracked/untracked rule-1 sweep pass.
These establish repository consistency only; no proposed Java spotlight behavior is implemented or
verified by that run, and no display result is claimed for this documentation-only response.
