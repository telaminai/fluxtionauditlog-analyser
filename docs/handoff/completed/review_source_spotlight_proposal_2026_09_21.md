# Review: source spotlight proposal — 2026-09-21

**Verdict: useful extension, CONDITIONAL before implementation.** Keep the graph-and-code payoff and
reuse the overlay. Revise the baseline, target identity and measurement contract first. No runtime
execution belongs in this feature.

Reviewed `docs/proposals/sorce-spotlight.md`, SHA-256
`a90ab633505fb8642bd073095dc9de437c29c37f132840e944ac2093f02d57e7` (83-line formatted version).
Source read at local main `9c10de82`; also checked the tool-agreement worktree at `d1bb7a1a` for an
already-built Java target. The pending release is another session's work; this review changes neither
that branch nor the proposal. Findings below are against the proposal, not claims of reproduced
failures in an implementation that does not yet exist.

## Findings

### SS-1 · High · the baseline misses existing design-source spotlights

Proposal lines 6–10, 28, 32–41 say no source line or bean is targetable and propose a new `SOURCE`
family. `SpotlightTarget.java:34–36,113–120` already provides `source:design`,
`source:design:bean:<id>` and `source:design:line:<n>`. `MainFrame.java:2069–2080,2120–2127` reveals
and measures them, and `docs/specs/spec-design-render.md:114` specifies them. This is not merely a
parser stub. The actual missing capability is Java source, including the embedded topology viewer.

**Correction:** retain the existing design vocabulary and its session-design meaning. Scope this as
Java source targeting plus any deliberately chosen embedded-design extension. If `source:bean` is
kept, define whether it is an alias; do not silently change which XML document it names. Existing
M66 behavior deliberately resolves the session design A even after a source glance at file B.

### SS-2 · High · a node declaration cannot identify the calculation in the example

Proposal lines 21–22, 38, 41 and 69 conflate three different anchors: an XML bean, a Java class
declaration, and an implementation statement. `SourceService.fqnForInstance` (`:74`) resolves the
selected processor's declared field type. `SourcePanel.navigate` (`:398–427`) separately searches
for a method or type declaration and otherwise navigates to offset zero. There is no single
bean/node-to-implementation-line map to reuse. A node can also have several handler methods.

**Correction:** define `source:node` as a declaration target and caption it accordingly. For
“bid = 1/ask”, explicitly select the Java file and its one-based statement line, then spotlight that
line. Do not infer a method or calculation from a node id. Missing/ambiguous mappings must refuse;
normal navigation's offset-zero fallback must not become a successful precise spotlight.

### SS-3 · High · “the pane's current file” has no unique identity or routing rule

Proposal lines 35–38 and 49–55 leave the implementation to choose which pane and file a target
means. `SourcePanel.java:69–87` has PROCESSOR, NODE, SPLIT and DESIGN modes, including two Java
panes. There are also separate Source-tab and embedded-topology SourcePanel instances;
`MainFrame.chooseSourceTarget` (`:3071`) currently routes record synchronization, not this new API.

Opening the Source tab while revealing the second target would hide the topology target and defeat
the central example. Resolving bare line numbers again after another reveal changes the file could
instead light the same line number in a different document. These are design hazards, not observed
runtime failures of the proposed API.

**Correction:** specify pane selection, how a closed embedded pane is opened, and how every target
binds to an exact document before the batch's reveals. Echo the resolved file/FQN, line and document
revision, and retain the “relationship to the loaded run unverified” qualification used by design
source. Define invalidation on document replacement, refresh, mode change and scrolling. Graph plus
source must work in either request order; two incompatible document targets must refuse honestly.

### SS-4 · Medium · modelToView2D returns an offset rectangle, not a line rectangle

Proposal lines 26–28, 53–54 and 78 describe character-offset geometry as a whole code line. A
headless Swing probe with `double reciprocal = 1.0 / ask;` returned:

```
offset 0:  Rectangle[x=3,y=3,width=0,height=15]
offset 29: Rectangle[x=176,y=3,width=0,height=15]
```

Directly returning that rectangle would fail `SpotlightTarget.resolve`'s positive-width test, or
light a caret-width sliver on a platform that returns width one. Existing
`DesignSourcePanel.lineBounds` (`:80–88`) already expands to a visible row and converts coordinates.

**Correction:** define a positive-width visible line band (and whether a wrapped logical line
includes all visual rows), clip it to the actual viewport, lay out after reveal, and convert from the
text component to overlay coordinates. Cover horizontal scrolling and the viewport edges. This is
small geometry work, but is not just exposing modelToView2D unchanged.

### SS-5 · Medium · automatic whole-pane fallback would turn failure into false success

Proposal line 35 calls `source` a “fallback if a line can't be resolved”, while lines 55–56 promise
NOT_VISIBLE for unavailable targets. A request to show line 900 in a 40-line file must not succeed by
lighting the whole file. That repeats the misleading-echo failure this product is correcting.

**Correction:** make the whole-pane target explicitly requested only. Missing line, bean, source,
node mapping or visible area returns a specific refusal. If the author meant an assistant may choose
to make a second, separate whole-pane request, say that rather than calling it a fallback.

## Minimum acceptance before handoff

The proposal currently has no executable acceptance list. Add:

- Existing session-design bean/line targets retain their vocabulary, revision handling and A-versus-B
  document behavior. Duplicate bean ids and missing source refuse.
- Java declaration and statement-line targets light different intended locations; a missing method
  cannot fall back to a class or line one with success.
- Topology node plus Java line remain visible together in either request order, from both a closed
  embedded pane and an already-open one. Confirm echo/context bounds against the actual components.
- SPLIT mode with both Java documents loaded resolves the declared pane consistently. Switching files
  in a batch never retargets an earlier bare line to the new document.
- First reveal, wrapping, horizontal/vertical scroll, resize and document refresh keep a truthful
  anchor or extinguish it; click/Escape and view-changing verbs retain existing lifetime rules.
- Line zero, out-of-range line, absent class, ambiguous mapping and unavailable pane refuse without
  an automatic whole-pane substitute. Retain the six-target and all-visible-or-refuse rules.

Use real display assertions for the side-by-side promise, not only a Surface map. Each corrected
behavior needs a negative control/mutation that fails when that behavior is disabled.

## Verified versus read

**Run:** existing `SpotlightTargetTest` (86), `SpotlightSetTest` (15), `SourceSyncTargetTest` (4), and
`SourceNavigationTargetTest` (10) on `d1bb7a1a`: **115 tests, zero failures/errors/skips**. Command:
`mvn -q -Dtest=SpotlightTargetTest,SpotlightSetTest,SourceSyncTargetTest,SourceNavigationTargetTest test`.
Also ran the separate headless JTextPane geometry probe above. These verify existing contracts, not
acceptance of the new feature.

**Read:** proposal, current parser/vocabulary, SourcePanel/SourceService navigation, design line
geometry, MainFrame reveal/bounds and source routing, source verb schema, spotlight batch resolution,
and design-render spec. No UI was driven, no release branch changed, no client session run.
