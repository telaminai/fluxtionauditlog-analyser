# Source spotlight: round-four response — 2026-09-21

[Review](review_source_spotlight_r4_2026_09_21_claude.md) ·
[Proposal](../../proposals/source-spotlight.md).

## U-1 — one named lookup, and a reread route that actually reaches it

The finding is correct. The previous proposal conflated the Java viewer's SourceService with the
source-glance verb's DesignWorkspace/DesignFiles route. Read both at current main `401da35b`.

Decision: **Java spotlight uses SourceService**, matching what the viewer renders. First configured
root wins; enabled local sources-jar lookup follows only when no root supplies the FQN. The resolved
snapshot retains its actual root/file or archive/entry. Echo and visible source qualification disclose
`lookup: source-viewer`, first-match selection and chosen origin; no claim of source/build equivalence.

The released `source {fqn}` verb is unchanged: authorised roots only, duplicate-root refusal, no
sources jars. It may legitimately refuse where spotlight selects a source. The guide and spotlight
description must explain both duplicate-root and jar-only examples so the assistant does not treat
spotlight success as resolution of a source-glance ambiguity.

The proposed reread entry point is `SourceService.freshDocumentForSpotlight(fqn)`, invoked during
preparation of every new Java spotlight request, including a repeat of the same target. It invalidates
positive/negative lookup entries and returns origin plus text; it does not navigate or clear lit
bindings. A later successful apply handles view/binding replacement. Existing Lit remeasurement
never rereads. There is no new verb or modification to source-glance semantics.

Acceptance 7 uses the actual spotlight entrance: missing archive entry, add it to a known jar,
repeat the same spotlight request, require success. It also covers root-order selection versus the
source verb's ambiguity refusal, jar-only success versus the source verb's refusal, cached hits,
negative-cache mutation and the separate new-jar discovery limit. A helper-only test is insufficient.

## L-1 / L-2 — correct name and a complete committed packet

Renamed the proposal to `docs/proposals/source-spotlight.md` before its first content commit. Updated
Markdown links in the response/review packet and tracker. Review findings, verdicts and quoted
historical paths are preserved; only links to the renamed proposal were mechanically corrected.

The packet is prepared on `docs/source-spotlight-proposal`, based on current main `401da35b`, in a
separate worktree. All linked source-spotlight reviews and responses accompany the proposal. The
primary checkout's IDE edits and unrelated review remain untouched. The current-main tracker entry
records proposal/review status, without copying the stale tracker's old implementation statuses.

## L-3 — design scroll response needs a changelog entry

The implementation instructions now explicitly require an Unreleased entry for viewport-driven
design remeasurement/extinguishing, as well as Java targets. Preserving design's containment geometry
does not make the new scroll reaction invisible to users. No implementation changelog claim is added
for this documentation-only commit.

## Verification

Read DesignFiles.fqn/resolve, DesignWorkspace.source, SourceService.sourceForFqn/configure,
SourceRootResolver and MavenSourceResolver at `401da35b`. The two routes differ exactly as the review
states (the viewer method is named sourceForFqn in source). No implementation or UI/client trial.
The packet's tests and documentation checks are recorded in the handoff brief after running them;
they establish repository consistency, not implementation of the proposed capability.

Packet validation: Maven 1,767 tests, zero failures/errors, 49 headless skips; strict docs and local
file-link checks pass. [Final review brief](brief_review_source_spotlight_r4_response_2026_09_21.md).
These are repository checks; the proposed feature remains unimplemented.
