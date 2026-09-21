# Proposal: spotlight Java source beside the topology

**Status: revised proposal, not implemented.** Revised 2026-09-21 in response to
[the first review](../handoff/review_source_spotlight_proposal_2026_09_21.md) and
[the revised-proposal review](../handoff/review_source_spotlight_revised_2026_09_21_claude.md).
[Disposition of SR-1–SR-10](../handoff/response_source_spotlight_revised_2026_09_21.md);
[round-four lookup correction](../handoff/response_source_spotlight_r4_2026_09_21.md).
Owner: analyser source navigation and spotlight UI. This extends M64 and the existing source-view
contracts; it does not add application execution or code analysis by the analyser.

## Checked release baseline

Re-baselined on fetched `origin/main` **`401da35bf43aee102d53be9c59d164af0fdd9eba`**, which includes
**v1.17.0**, on 2026-09-21. The primary working checkout remains at `9c10de82` to preserve concurrent
uncommitted work; it is **not** the source baseline for this revision. Implementation must start from
current main and re-check intervening changes before editing these shared surfaces.

In particular, `c6aeafde` already added `DesignSourcePanel.revealLine` and rejects an existing design
line band unless the whole band is inside the text viewport. This proposal preserves that released
contract. The [round-three response](../handoff/response_source_spotlight_r3_2026_09_21.md) records
T-1–T-4 and the source checks against this revision.

## Purpose and existing capability

An assistant should be able to point at a graph node and a specific Java statement at the same time,
so the person can inspect the code behind the explanation on their shared canvas.

Design XML already has working targets:

- `source:design` — the session design pane;
- `source:design:bean:<id>` — a bean declaration;
- `source:design:line:<n>` — a one-based design line.

Their vocabulary and meaning remain unchanged, including the rule that a source glance at file B
never redirects a session-design target away from design A. Their revision handling and ambiguous
bean refusal remain governed by [spec-design-render.md](../specs/spec-design-render.md#d-3--spotlight-vocabulary).
This proposal does not add a `source:bean` alias or embedded XML design rendering.

The missing capability is **Java source targeting**, including in the Topology tab's embedded
source viewer. `tab:source` only lights the tab button. Existing FQN/method navigation can reveal
Java, but it does not provide a Java spotlight rectangle or bind a spotlight to a particular document.

## Proposed vocabulary

These are proposed additions, not commands available in the current release:

| Target | Meaning |
|---|---|
| `source:java:<fqn>` | The visible Java document viewport for the named class; explicitly requested whole-document view |
| `source:java:<fqn>:line:<n>` | One-based logical line in the document resolved for that class |

An FQN uses the spelling accepted by existing source navigation, including supported nested-class
names. Family keywords are case-insensitive; FQNs retain their spelling. The Java
parser recognises only an optional terminal `:line:<positive integer>` suffix. Unknown forms are errors.

There is no implicit “current file” target. A line always names its class/document, even when several
source panes are visible. There is no new method target in this slice: the assistant reads the
source, identifies the statement, and supplies its line. A later method-target proposal would need
an overload/ambiguity contract of its own.

**Scope decision after SR-4:** defer `source:node:<instanceId>` to a separate slice. The current
EventProcessorModel makes same-package guesses and collapses field declarations into a map; neither
is sufficient for the promised unambiguous mapping. This slice must not call `fqnForInstance` to
invent a Java target. The worked example already supplies its FQN and line, so the graph-and-code
payoff remains intact. A later node-target proposal must retain duplicate/conflict evidence before
map construction, distinguish explicit imports/qualified declarations from guesses, and test its
refusal policy. No node-target implementation or readiness is claimed here.

An XML bean remains a separate `source:design:bean` target. Supplying an FQN selects source for
inspection; it does not prove correspondence to a graph instance or the loaded binary.

## Resolve identity before changing the view

For every new Java target:

1. Validate the grammar and existing batch limits before revealing anything.
2. Resolve the explicit FQN through **SourceService, the Java viewer lookup**, using the fresh
   entry point specified below; no node/type inference. First configured root wins; disclose the
   selected root/document. This does not claim uniqueness across roots or build-classpath equivalence.
3. Bind the target to the resolved document identity, exact rendered text revision and requested
   anchor. Resolve all Java targets in the batch before the first reveal. Use that same snapshot
   for navigation, measurement and the echo; do not reread a different version halfway through.
4. Check that a requested line exists. Navigation's ordinary fallback to offset zero is not
   permitted for a line target. A whole-document target reveals the start of the named document.

Reuse existing source roots and configured source-archive lookup; spotlight grants no new file,
network or execution permission. If the resolver cannot supply a stable identity and revision for
what it will render, refuse rather than inventing them. Source identity may be a file or an archive
entry; it must distinguish entries in the same archive. Define the revision as SHA-256 of the
rendered source string encoded as UTF-8, without additional normalization, and label its basis as
`rendered-text-utf8` so it is not mistaken for a file-byte or build-artifact digest.

For this first slice, all Java targets in a single batch must resolve to **one document**. A whole
document target and a line in that document are allowed. Two different Java documents are refused with advice to
show them in sequence, even if SPLIT mode could display both. Multiple Java files at once are outside
this proposal. When `add: true` is used, the restriction also includes already-lit Java targets.

## Preparation stage and lifetime binding (SR-1 / SR-2)

Add a **frame-owned `prepareJavaSpotlights` stage** between the successful existing pure
`SpotlightTarget.precheck` and the first `SpotlightTarget.resolveAll` call. Keep the shared Surface
interface and resolveAll's non-Java behavior unchanged. Preparation receives the parsed requests,
retained lit targets, source configuration and current tab state. It returns either a refusal without
view changes or an immutable batch plan: bound Java documents/anchors and one destination.

Preparation is read-only with respect to the UI: it neither opens a pane nor navigates, scrolls,
changes selection, clears spotlights or publishes bindings. Include retained Java bindings in the
one-document check. A failure on the second Java request leaves the entire view and lit set unchanged.
Check that the source configuration/view state used to prepare is still current before applying a
plan; a superseded plan is refused, not applied to a different selection.

Invoke resolveAll with a **per-call Surface adapter**: delegate existing families to the existing
surface, but reveal/measure Java targets using the prepared plan. Do not make this adapter resolve
an FQN again. The empty-Java case delegates without changing existing reveals or their ordering.
Any scoped preparation state is discarded on success or refusal, including exceptional exits.

SpotlightOverlay stores names, and relightSpotlight re-parses those names. Therefore persist each
successful Java binding in a **frame-owned `javaSpotlightBindings` registry keyed by the exact target
name stored in Lit**, following the existing `designSpotlightRevisions` pattern. Each entry contains
FQN, document identity, immutable text/revision/basis, anchor and chosen pane identity. During initial
resolution the adapter uses the staged plan; only after the whole set succeeds and replace/add is
applied are the new entries published. A refused batch publishes no new entries. Retained entries
are never rebound to a new file just because a reveal or source lookup changed.

On later measurement and echo, look up the registry entry, verify the displayed identity/revision,
and measure that binding. A missing binding means the target goes out; do not reconstruct it by name.
Remove entries for every target that leaves the overlay: explicit clear, replace, refusal-induced
remeasurement, viewport invalidation, click, Escape and view-changing verbs. Wire the overlay's
existing dismissal callback to registry cleanup as well as clearSpotlightHere; reconcile registry
keys with the actual surviving Lit names after remeasurement. Release retained document snapshots when no binding
needs them; viewer subscriptions follow the viewer lifetime described below. Closing a project/log or changing the relevant source configuration clears affected
bindings with their spotlights. There must be no hidden registry that can resurrect a dismissed target.

### One lookup: SourceService, not the source-glance verb (U-1)

Java spotlight uses **SourceService → SourceRootResolver → optional MavenSourceResolver**, because
that is the document the Java viewer renders and the route that supports local sources jars. This
is distinct from `source {fqn}` → DesignWorkspace/DesignFiles: the released glance searches authorised
roots, refuses duplicate-root matches and never searches sources jars. **Do not change that verb**,
its authority checks, ambiguity behavior or lookup backend in this slice.

For spotlight, preserve SourceService's existing selection policy: the first matching configured
root wins; only when roots have no matching file does enabled local sources-jar lookup choose its
first candidate. The origin-preserving result must include the chosen root/file, or archive/entry.
Expose `lookup: source-viewer` and `selectionPolicy: first-match` alongside that origin in the echo;
show the chosen origin and first-match policy in the Java viewer's source label/qualification.
This is a disclosure of lookup precedence, not proof that this is the application's build source.

The glance and spotlight can legitimately differ: two roots containing the same FQN make the glance
refuse while spotlight selects the first root; jar-only source can be lit while the glance refuses.
Document these examples in the source/spotlight guide and in the spotlight description. A successful
spotlight must not be described as resolving the glance's ambiguity. The assistant must check the
disclosed origin before transferring a line number or claim from another source document.

The public **fresh reread route is a new Java spotlight request itself**, including repeating the
same target. Its frame preparation calls a proposed `SourceService.freshDocumentForSpotlight(fqn)`
once per distinct FQN in that batch, invalidates lookup hits/misses and returns an origin-bearing
snapshot. It performs no navigation. The prepared snapshot is then rendered directly; measurement
of an existing Lit binding never calls this reread entry point. No extra MCP verb, source-verb flag
or automatic polling is introduced. The method is new implementation work, not an existing API.

### Source identity must survive lookup (SR-10)

SourceService currently returns text, and MavenSourceResolver caches text without its archive path.
Add a resolved-document value containing text and origin (root file, or archive path plus entry),
with the rendered-text revision above. Have source lookup return that value internally and retain it
in caches; existing string-returning navigation APIs may delegate to its text. Do not resolve the
path separately from the read and then assume it describes the cached text. Render the prepared
snapshot directly rather than calling navigation's FQN lookup a second time.

The local `*-sources.jar` fallback exists today and is **required in this slice**, subject to its
existing enable/disable setting. Test two entries in one archive, root precedence, disabled archive
lookup, and refreshing/replacing a cached origin. On an explicit refresh or cache invalidation,
clear **positive and negative** lookup entries, then re-read text and origin together. When a refreshed
snapshot is committed to the viewer, invalidate lit entries whose identity/revision changes; preparation
alone must not remove an existing spotlight. Wire **lookup-cache** invalidation into
`freshDocumentForSpotlight`; registry invalidation belongs only to the later view-application stage.
`source {fqn}` remains unchanged and is not the archive-cache refresh route. Repeated measurement of
an already-lit binding does not reread or invalidate.

**Discovery limit retained:** the archive path list is discovered once per MavenSourceResolver
instance. Clearing cached hits/misses does not discover a newly added archive path. A new entry in an
already-discovered jar is visible after explicit reread; a newly added jar needs resolver recreation
through source reconfiguration (SourceService.configure already replaces the resolver) or application
restart. Disclose this distinction in the missing-source refusal/help. Test both cases, including
successful discovery after recreation. No new rescan command, watcher, archive downloader, class
loading or permission is added by this slice. If a source cannot be read, refuse.

## Choose the visible source surface deliberately

Choose the destination once for the batch, considering both requested targets and targets retained
by `add: true`:

- If the set includes a topology canvas target, keep Topology selected and open its embedded source
  pane if closed. Render the bound Java document there. Do not switch to the separate Source tab.
- Otherwise, if Topology is already selected, use/open its embedded source pane.
- Otherwise select the Source tab and use its Java viewer.

Add `TopologyPanel.ensureSourcePaneVisible()` (proposed name), distinct from the existing
`openSourcePane()` getter and `openSourceFor(instanceId)` navigation command. It opens the already
bound embedded viewer without navigating to a node or seeding a second document; it returns that
viewer or a specific unavailable result. The prepared reveal then renders its bound snapshot there.
Destination choice is a new batch function: **do not change `chooseSourceTarget`**, whose record-sync
contract deliberately leaves a closed pane closed. No pane opens until preparation has succeeded.

Within the chosen SourcePanel, use the EventProcessor pane for the selected processor and the Node
pane for another class, following existing navigation. In SPLIT mode, the explicit FQN still selects
one document; focus/caret position does not choose it. A missing selected processor or unavailable
source cannot be disguised by highlighting a different loaded document.

This routing applies only to the new Java targets. Existing design targets retain their Source-tab
behavior. A batch combining incompatible surfaces, including design XML and Java when both cannot
remain visible, is refused under M64's existing all-visible-or-refuse rule.

After all reveals, measure every target again against the **bound identity**, not merely against its
line number. The result must be independent of the ordering of the topology and Java requests. For
`add: true`, retained targets that the reveal makes unavailable go out and are reported through the
existing `wentOut` mechanism; they must not silently move to a different document.

## Measure a visible line, not a caret

`modelToView2D(offset)` gives the position of a character offset and may have zero or one pixel of
width. It is not the line rectangle. Add a source-view measurement helper with this contract:

- For Java, add a settle-before-measure entry point to its Pane, using the released design helper's
  pattern: supersede queued navigation with a ticket, validate/layout, then scroll the bound snapshot.
  Java has a different text component and cannot call DesignSourcePanel.revealLine directly. Design
  continues to use SourcePanel.revealDesignLine → DesignSourcePanel.revealLine unchanged. Prefer
  centring a Java logical line when its height permits, without a jump when already fully visible.
- For a line target, measure a positive-width band across the visible text viewport at that
  logical line. When wrapping is on, include its visual rows. Intersect the band with the viewport;
  never return a rectangle over a toolbar, label, neighbouring pane or off-screen text.
- For a wrapped line taller than the viewport, reveal its first visual row and light the visible
  portion. Echo `partial: true` for **any clipped Java line band**, including a tall wrapped line,
  and `false` for a fully visible Java line. A clipped line must not be described as wholly visible.
- For the whole-document target, measure only the visible text viewport. It is an explicitly partial
  view of a document, not a claim that the entire file is on screen.
- Convert from text-component coordinates to the existing spotlight overlay coordinates, then use
  the same screenshot-coordinate convention as other target echoes.

The Java whole-document rectangle deliberately excludes its label/status controls. Existing
`source:design` continues to mean the entire design panel, including its status label; it is not
silently narrowed here. This difference is explicit in the vocabulary descriptions.

**Two deliberate line policies:** Java measures the logical line's visual rows and may light their
visible intersection, labelled `partial`. Design retains v1.17.0's existing declaration/line band:
`DesignSourcePanel.lineBounds` returns empty unless that band is wholly contained by its viewport.
It neither clips the band nor adopts Java's expanded wrapped-row measurement. A partially visible
design band is refused on resolution or goes out on remeasurement. `partial` is **Java-line-only**;
no field is added to design echoes. Java's policy supports inspecting long wrapped implementation
statements with an explicit qualification; keeping design's policy preserves the released refusal
contract. No design geometry migration or replacement of its existing refusal tests is authorised.

If no positive-area visible band remains, return NOT_VISIBLE with a useful reason. Reuse the
existing overlay and callout placement machinery. This proposal does not claim to solve general
callout overlap; add a screenshot check for the graph-and-code example.

## Truthful results and lifetime

A precise target never falls back to the whole pane. For a nonexistent line, absent source, missing
document, invalid identity or unavailable surface, refuse and name the reason.
The assistant may then make a **separate explicit** whole-document request if that is useful.
Malformed inputs and known missing/out-of-range anchors must be refused before changing the view.
A later visibility refusal may have revealed a view; report its effects under existing M64 rules.

Each lit Java target's echo and `context.spotlight` entry include:

- requested target and resolved FQN, document identity, revision and revision basis;
- lookup/selection policy and chosen root/file or archive/entry, as specified above;
- anchor kind (`document` or `line`) and resolved one-based line for line anchors;
- destination (`source-tab` or `topology-source`); for Java line anchors, `partial` states whether
  any of the logical-line band was clipped. It is absent from document and design targets;
- `relationship: unverified` — displaying this source does not establish its relationship to the
  loaded run. Captions remain the assistant's testimony.

The human surface must retain the document label and show that source/run correspondence is
unverified; the warning must not exist only in MCP. A line target identifies source text on both
surfaces, never proof that the statement executed.

Java spotlights are bound to the rendered revision for their lifetime. Replacing or refreshing that
document to a different revision, changing its source mapping, closing its pane, or switching to an
incompatible mode extinguishes them. They are not re-anchored to the same line number in new text.
Scrolling, resizing and changing wrap either remeasure the same bound target truthfully or extinguish
it. Context must reflect that result rather than stale coordinates. Existing design-bean re-anchoring
and its stale-caption annotation remain unchanged. Java adopts neither: a caption may describe an
exact expression, so extinguishing it on any revision change is safer than leaving a marked but
potentially wrong statement on screen.

### Viewport and layout hooks (SR-3)

Window resize alone is insufficient. Expose a source-view invalidation subscription from SourcePanel
covering each Java JViewport and the DesignSourcePanel viewport. Register JViewport ChangeListeners
for scroll position/extent changes and notifications for wrap/font/layout, document replacement,
mode and pane visibility changes. MainFrame subscribes once per live viewer, including the embedded
viewer when created, and unsubscribes on disposal; do not accumulate listeners per reveal.

On the EDT, coalesce viewport/layout notifications into one pending remeasurement. During the
prepare/reveal/apply sequence, defer those callbacks until the batch commits or refuses so they
cannot discard provisional bindings or publish intermediate coordinates. Remeasure without revealing
or scrolling again, remove invalid registry entries, then repaint. Before echo/context returns,
flush any pending measurement needed for the current layout; do not report a previous scroll position.
Document identity/revision changes invalidate affected Java bindings before publishing new text.

**Design line and bean targets gain these hooks too**, but they call the released lineBounds
unchanged: a band not wholly contained in the viewport goes out. Do not clip it to keep it lit.
Preserve revealLine's navigation ticket/settling, current revision/re-anchoring rules and whole-panel
target. Listener-driven remeasurement is new; design settling and containment refusal already shipped.
Add regression coverage for this distinction rather than relabelling new geometry as compatibility.

Retain existing click, Escape, clear and view-changing-verb dismissal, transient-only storage,
caption limits, numbering, six-lit limit and batch refusal behavior. No saved source tours, reports
or findings are added by this work.

## Example: graph and implementation side by side

After reading the actual source, the assistant has established that the calculation is at line 42
of `com.acme.pricing.InverseQuotePriceSource`. The class and line below are illustrative; clients
must obtain the real location rather than copying the number.

```json
{
  "targets": [
    {"target": "topology:node:inverseQuotePriceSource", "caption": "The reciprocal book"},
    {"target": "source:java:com.acme.pricing.InverseQuotePriceSource:line:42", "caption": "The reciprocal is calculated here"}
  ]
}
```

The call opens the embedded source viewer and leaves the graph visible. Both anchors are lit in
the same final view. To show a declaration instead, read its line and use the same explicit Java
line form. The deferred node convenience target is not part of this delivery. Neither operation establishes that the loaded
run used those source bytes.

## Implementation boundaries

Extend SpotlightTarget parsing and SpotlightVocabulary together. Implement the frame preparation
stage and per-call Surface adapter, the lifetime registry, origin-preserving source lookup, the
navigation-free pane opener, line geometry and viewport notifications described above. Reuse the
existing overlay; keep its string-based Lit representation. Existing non-Java families keep their
resolution path; design changes are confined to the new listener-driven remeasurement using its
released geometry, refusal and settle behavior.
No class loading, application execution or inference of business behavior is required.

Document the new targets in the spotlight spec and user guide, with a side-by-side screenshot from
a neutral fixture under an isolated home. Update the canonical guidance/index only through the
normal pinning process if its text changes. Implementation must add CHANGELOG entries for the new
Java targets **and** design viewport-driven remeasurement/extinguishing: design's geometric refusal
is unchanged, but reacting when its pane scrolls is a user-visible change. No implementation entry
is added to the release changelog merely by committing this proposal. No release or publication is
implied by this proposal.

## Acceptance and review evidence

Write predictions before implementation. Commit each regression test and show it fails with the
relevant behavior disabled. Distinguish constructed fixtures from preserved session evidence.

1. **Preparation and compatibility:** existing design vocabulary and A-versus-B document behavior
   remain unchanged. Instrument preparation/reveal order: a second Java target that fails lookup
   causes zero reveals and leaves tabs, scroll, selection and lit bindings unchanged. Without Java
   targets, existing families retain their reveal sequence. Mutate the preparation ordering to show
   the no-side-effects assertion fail. Check superseded plans cannot apply.
2. **Explicit identity:** different Java documents and unavailable/out-of-range lines refuse before
   reveal. SPLIT mode obeys the explicit FQN, not caret focus. Two FQNs resolving to the same enclosing
   source document may coexist. `source:node` is unknown in this slice; no permissive model guess is
   used. Existing source navigation behavior stays unchanged.
3. **Central display case:** topology node plus explicit Java line work in either request order,
   with the embedded pane initially closed and initially open. The graph stays visible and the opener
   does not navigate to any other source. Assert rectangles against actual rendered components and
   echo/context agreement; inspect a captured neutral screen. Test retained targets with `add: true`.
4. **Registry lifecycle:** after successful application, re-parsing a Lit name still measures its
   original bound document. A refused batch adds no registry entries. Replace, selective clear,
   click/Escape, document refresh, source-configuration change and pane disposal remove the right
   entries. Mutate registry lookup to fresh FQN resolution and show a replacement-document case fail.
5. **Geometry:** first reveal, wrap on/off, a line taller than the viewport, horizontal/vertical
   scrolling, viewport edges and resize yield the correct band or refusal. Whole Java excludes the
   toolbar; whole design retains its panel. Use **three separate mutation witnesses**: raw caret
   rectangle; omitted vertical viewport intersection; first visual row only for a wrapped line that
   spans several visible rows. Each must fail its corresponding Java assertion. Verify `partial`
   for clipped Java bands and its absence from design. Separately preserve DesignSpotlightFrameTest's
   refusal/settling checks, add a partly visible design band that must refuse, and mutate containment
   to intersection-and-lighting so that this refusal check fails. Never reverse the released oracle.
6. **Listeners:** programmatic viewport movement with a lit target remeasures or extinguishes it
   without another spotlight call or window resize. Cover both viewers, both axes, design source,
   wrap/layout and disposal. Disable the viewport callback and demonstrate failure. Check repeated
   reveals do not multiply listeners and batched layout callbacks cannot publish intermediate state.
7. **Source archives and freshness:** a constructed local sources jar supplies two distinct entries;
   origin and hash match each rendered document. Root precedence and archive lookup disabled behave
   as documented. Exercise the real spotlight entrance: request a jar-only Java target while its
   entry is absent, add that entry to an already-discovered jar, then issue the **same Java spotlight
   request** and require success. Omitting negative-entry invalidation must fail this assertion.
   Replace a cached hit and repeat the Java spotlight request; text/origin/revision must update
   together and stale retained targets go out. Do not substitute a helper-only or `source {fqn}` test.
   Add a different sources jar after discovery: it remains unavailable with an actionable discovery
   limitation, and becomes available after resolver recreation. A preserved design bean still receives
   its existing stale-caption treatment. Also configure two roots containing different copies of
   one FQN: spotlight selects the first and echoes its root/hash, swapping root order changes that
   selection, while unchanged `source {fqn}` refuses ambiguity. For jar-only source, spotlight succeeds
   and that verb still refuses. Inspect the first-match disclosure on both surfaces. No network
   acquisition or executable component is needed.
8. **Refusals, echo and limits:** malformed FQN/line grammar, missing documents and unavailable panes
   never fall back to a whole pane or offset zero. Both surfaces retain the unverified source/run
   qualification and document label. Six-target limit, incompatible-surface batch refusal, caption
   attribution, clear, view-changing verbs and no persistence remain intact.

### Mandatory display gate (SR-6)

Headless unit tests alone cannot close the display acceptances. `.github/workflows/ci.yml` already
has a Linux/Xvfb `ui-frame` job with an explicit class list **and** a per-suite nonzero/no-skip check.
Add a `JavaSourceSpotlightFrameTest` suite to both lists (at baseline `401da35b`, lines 80 and 86);
DesignSpotlightFrameTest is already listed in both. Extend it for scroll-driven refusal and policy
separation, retaining its released settling/containment assertions. Preserve the other listed classes. Run with
`-Djava.awt.headless=false` in both Maven and the test JVM. A missing report, zero tests, any skip,
failure or error is a failed display gate. Confirm this on the implementation PR's CI run, not by
assuming a headless green Maven run reached a display.

Headless constructed-component tests cover interval/viewport intersection, positive width,
wrapped-row aggregation, `partial`, identity/registry and preparation ordering. Actual Swing layout,
first reveal, split panes, viewport listeners, screenshot alignment and dismissal require the
non-skipping display job as well. The developer also captures and inspects the side-by-side result
under an isolated home. Report headless and display totals separately, with the CI job and exact head.

The handoff includes all mutation witnesses and anything not verified. This remains a proposal for
re-review; no implementation, non-skipping display result or acceptance closure is claimed here.
