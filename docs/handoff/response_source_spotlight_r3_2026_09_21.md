# Source spotlight: round-three response — 2026-09-21

**Lookup correction:** the reread route described below is superseded by the
[round-four response](response_source_spotlight_r4_2026_09_21.md). Java spotlight uses SourceService;
the released source-glance verb remains root-only and unchanged.

[Review](review_source_spotlight_r3_2026_09_21_claude.md) ·
[Revised proposal](../proposals/source-spotlight.md).

The blocker was valid. The previous proposal read local main `9c10de82` while released main had
already changed design geometry. Calling a replacement of that behavior a compatibility correction
was wrong. This response changes the proposal, not the released code or the reviewer's report.

## T-1 — re-baseline and preserve the released design policy

Fetched origin and read **401da35bf43aee102d53be9c59d164af0fdd9eba**, including v1.17.0 and
`c6aeafde`. The primary checkout stays at its existing revision to preserve the concurrent working
edits. All baseline claims in this revision refer to the fetched source, not that checkout.

Decision: **Java clips; design refuses.** Java's new line target measures all visual rows of its
logical line, intersects with the viewport and reports `partial` when clipped. Existing design
line/bean targets retain `DesignSourcePanel.lineBounds`' whole-band containment test, existing
wrapped behavior, and no partial echo. A partly visible design band refuses/goes out. This preserves
the released behavior rather than silently migrating its geometry.

Java benefits from a qualified partial view of long wrapped implementation statements. Design keeps
the stricter released policy. The whole-document distinction also remains explicit: Java text
viewport versus the existing design panel including status.

Design settling already exists. Preserve `SourcePanel.revealDesignLine` →
`DesignSourcePanel.revealLine`, including the navigation ticket, validation and scrolling. Java needs
an equivalent helper for its separate Pane component; it cannot call a method on the design widget
to settle Java text. The proposal names reuse of the pattern and does not claim design settling is
new work.

Acceptance now preserves the existing design frame oracle and adds a partly visible design band
that must refuse. Replacing containment with intersection-and-lighting must fail that assertion.
Java retains its separate caret, clipping and wrapped-row mutations. New viewport listeners remeasure
design through the released helper without changing its visibility policy.

## T-2 — hits, misses and discovery are different caches

The resolver caches Optional values, including misses, and separately caches the jar path list.
Explicit Java source reread and new Java spotlight preparation must invalidate positive **and
negative** lookup entries, then read text/origin together. Merely remeasuring an existing spotlight
does not cause a reread. The preparation-stage no-view-change rule still applies: invalidate old
lit bindings when the replacement snapshot is published, not while preparing a possibly refused call.

The existing once-per-resolver jar discovery limit remains. New entries in a known jar become visible
on explicit reread. A new jar path is not discovered by clearing the lookup cache: source
reconfiguration recreates the resolver (`SourceService.configure`), or an application restart does.
Missing-source help must state that distinction. No rescan command or watcher is introduced.

Acceptance covers a cached miss followed by a new entry in a known jar, with a mutation omitting
negative-cache invalidation. It separately covers a newly added jar, the stated refusal, and success
after resolver recreation. This is no longer an acceptance that only re-reads cached hits.

## T-3 / T-4 — vocabulary and partial disclosure

Removed the leftover instance-id clause from the new Java vocabulary. The deferred node target remains
out of scope. `partial` is explicitly Java-line-only, true whenever its measured band is clipped and
false when fully visible; it is absent from whole-document and design targets. Design cannot produce
an accepted clipped band under the preserved rule.

## Source checks and limits

Read the current DesignSourcePanel, SourcePanel wrapper, released CHANGELOG, DesignSpotlightFrameTest,
MainFrame's reveal/apply/remeasure paths, source resolvers and source configuration. Compared the
relevant source files from `9c10de82` to `401da35b`: the shared parser/overlay, source model/resolvers
and preparation insertion point still support the proposed mechanisms. Rechecked the topology opener
and existing source-sync distinction against current source.

The current CI workflow has DesignSpotlightFrameTest in both the Xvfb execution list and no-skip
loop. The proposal now names both list locations (lines 80 and 86 at the pinned baseline), preserves
the existing suite, and requires the new Java suite in both.

Verified by source inspection and Git reads; no test or UI run for this documentation revision.
Earlier test totals are historical and are not acceptance evidence for the new feature. Local
Markdown file targets, whitespace and the rule-1 sweep were checked on the changed documents.
No branch switch, main pull, implementation, release or review-report edit was performed.
