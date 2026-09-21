# Review: revised source spotlight proposal — 2026-09-21 (reviewer: claude)

**Verdict: NOT READY FOR HANDOFF. Close to it.** The contract is materially better than the version the
first review saw: the design baseline is restored, `source:node` is correctly demoted to a declaration
target, the whole-pane fallback is gone, and the measurement section describes a line band rather than a
caret. Four corrections are required before an implementer can work from this text without re-deriving
decisions, and all four are about the *existing machinery the proposal must extend* rather than about the
contract it states.

The recurring shape of the findings: the proposal describes the behaviour it wants correctly, and
under-describes how that behaviour reaches the shared spotlight pipeline, which has no stage where the
proposal's promises can currently be kept. An implementer following this document would discover that
half way through.

Reviewed the **working-tree bytes** of `docs/proposals/sorce-spotlight.md` (223 lines, uncommitted, staged
as `AM`), against source at local `main` `9c10de82`. I did not modify the proposal, the earlier review, or
any other working-tree edit.

---

## Findings

### SR-1 · High · the batch has no stage in which "resolve everything before the first reveal" can happen

Proposal lines 56–62, 80–86 and 97–98 require three things of the batch as a whole: validate and resolve
every Java target's identity *before the first reveal*, choose one destination for the batch, and
re-measure everything against bound identity afterwards.

The pipeline has no such stage. `MainFrame.java:2448` calls `SpotlightTarget.precheck(...)`, which is pure
and never sees the surface (`SpotlightTarget.java:386-406`) — so it cannot resolve an FQN or consult the
source model. `MainFrame.java:2452` then calls `SpotlightTarget.resolveAll(names, spotlightSurface)`
(`SpotlightTarget.java:445-475`), which parses every name, then for each target in order calls
`surface.reveal(target)` and measures targets `0..i`. There is nothing between "all names parsed" and
"first reveal", and `Surface` (`:242-254`) has only `reveal`, `bounds` and `whyNotVisible`.

**Required correction.** Name the new stage and where it sits: either `Surface` gains a batch-preparation
method invoked once after `precheck` and before the first `reveal`, or the frame performs the resolution
and destination choice itself between lines 2448 and 2452. Say which, because `resolveAll` is shared by
every other family and any change to it has to be behaviour-preserving for all of them. Acceptance 1
(line 192) asks only that existing spotlight tests stay green; add an acceptance that the resolution and
destination choice demonstrably happen before any reveal — for example, that a batch whose second Java
target fails identity resolution leaves the view untouched.

### SR-2 · High · a Java target's binding cannot travel on the target, and the mechanism it needs already exists unnamed

Proposal line 177 asks for "a bound Java-location model so reveal and bounds cannot independently
reinterpret a mutable current file", and lines 143–145 bind a spotlight to a revision *for its lifetime*.
Neither is possible if the binding lives on the target.

`SpotlightTarget` is a record of `family, argument, name, graph` (`SpotlightTarget.java:24`) — no identity
field. The lit set stores the target as a **String**: `SpotlightOverlay.remeasure` takes
`Function<String, Optional<Rectangle>>` (`SpotlightOverlay.java:142-153`), and `relightSpotlight`
(`MainFrame.java:2056-2062`) re-parses that string and calls `bounds(parsed.target())`. A retained Java
target therefore arrives at `bounds()` as nothing but its name.

The mechanism the proposal needs is already in the code for design targets and is not cited:
`designSpotlightRevisions`, a `Map<String,String>` keyed by target name (`MainFrame.java:79`), populated
after resolution (`:2473`), cleared on log/design change (`:1092`, `:2287`), entries removed when a target
goes out (`:1145`), and read into the echo (`:2497-2499`) alongside `relationship: "unverified"`.

**Required correction.** State that the Java binding is a lifetime registry keyed by target name that
mirrors `designSpotlightRevisions`, and specify its four moments: populated at resolution, consulted by
`bounds`, entries removed when a target goes out, and cleared on the events in lines 144–145. Without
this an implementer will reasonably attach identity to the target and find it does not survive
`remeasure`.

### SR-3 · High · scrolling is not wired to re-measurement at all, so line 146 needs a hook that does not exist

Proposal line 146: "Scrolling, resizing and changing wrap either remeasure the same bound target
truthfully or extinguish it."

`installSpotlight` (`MainFrame.java:2039-2048`) registers exactly one listener: `componentResized` →
`relightSpotlight()`. `relightSpotlight` has three call sites (`:2045`, `:2456`, `:2466`) and none of them
is a viewport or scroll event; I found no scroll listener feeding it anywhere in `MainFrame`.

So resize is covered and **scrolling is not**. Today a design line spotlight goes stale if its pane is
scrolled; that has been tolerable because the design pane is rarely scrolled. A Java source pane is the
opposite case, and the proposal's own acceptance 5 (line 204) lists horizontal and vertical scrolling.

**Required correction.** Name the listener the feature adds and what it triggers, and say whether design
line targets gain it too. If design is deliberately left as it is, say so, because the two will then
behave differently for the same user action.

### SR-4 · High · "unambiguous declared type mapping" cannot be read off the current model: it guesses, and conflicts are gone before it is asked

Proposal lines 57–59 require that a node target resolve through "an unambiguous declared type mapping from
the selected source model", and that "missing or conflicting mappings refuse".

The chain is `SourceService.fqnForInstance` (`SourceService.java:74-76`) →
`EventProcessorModel.fieldTypeFqn` (`EventProcessorModel.java:71-74`) → `resolveSimpleType` (`:77-84`).
Two properties of that code defeat the requirement as written:

1. **It guesses.** `resolveSimpleType:82` returns `packageName + "." + simple` when the type is neither
   already qualified nor imported. The caller cannot tell that answer apart from a confident one: all
   three paths return a bare `String`. A same-package guess that happens to collide with a real class of
   that simple name resolves to the wrong type silently, which is exactly the failure class this feature
   exists to avoid.
2. **Conflicts are already collapsed.** `fieldTypes` is a `Map<String,String>` (`:29`) built at
   construction. Two declarations of one instance id cannot both be present by the time `fieldTypeFqn`
   is called, so "conflicting mappings refuse" has nothing left to detect at that point.

**Required correction.** Either require `fieldTypeFqn` to report *how* it resolved (qualified, imported,
or same-package guess) and state what a guess does — refuse, or resolve and label itself on both surfaces
— or state plainly that a same-package guess is acceptable and must be disclosed in the echo. Separately,
say where conflict detection happens, since the map cannot provide it; if conflicts are detected when the
model is built, the proposal should say the model must carry that fact forward.

### SR-5 · Medium · opening a closed embedded pane has no side-effect-free entry point

Proposal line 83: "keep Topology selected and open its embedded source pane if closed."

`TopologyPanel.openSourcePane()` (`TopologyPanel.java:1412-1415`) is a **getter** despite its name — it
returns the pane only when it is already showing, otherwise null. The opener is the private
`showSourcePane(true)`, reachable publicly only through `openSourceFor(instanceId)` (`:1418-1420`), which
also navigates the pane to that node's source. Opening a closed pane through today's public API therefore
changes the displayed document as a side effect, which collides directly with binding a target to a
document resolved beforehand (lines 60–62).

Note also that `MainFrame.chooseSourceTarget` (`MainFrame.java:3065-3075`) deliberately refuses to open a
closed pane — its documented rule is that when the topology is in front without its source pane open,
"there is nothing visible to sync". The proposal's rule is a different rule for a different purpose.

**Required correction.** Say that batch destination selection is a new function rather than an extension
of `chooseSourceTarget`, and specify the pane-opening entry point so that opening does not navigate.

### SR-6 · Medium · the real-display acceptance silently skips on headless CI, so it is not a gate

Acceptance 3 (lines 199–200) is right to demand real display evidence: "Capture and inspect the real
screen; a fake Surface alone does not establish this result." The precedent exists — there is an
established `*FrameTest` convention, including `SpotlightFrameTest` and `NamedGraphAndMenuSpotlightFrameTest`.

**Reproduced:** `mvn -o -Dtest=SpotlightTargetTest,SpotlightSetTest,SourceNavigationTargetTest,SpotlightFrameTest test`
→ **115 tests, 0 failures, 0 errors, 4 skipped**. The four skipped are the whole of `SpotlightFrameTest`,
guarded by `assumeFalse(GraphicsEnvironment.isHeadless())` (`SpotlightFrameTest.java:58,122`). A headless
run reports success while asserting none of the geometry.

CLAUDE.md rule 8 says a finding is not closed until the check that would catch it exists. A check that
skips in the environment where regressions are caught does not close it.

**Required correction.** State that the display acceptances are developer-run, that the handoff must
report their measured results rather than a build log showing "skipped", and which of the promises in
lines 104–117 have a headless equivalent (band width, viewport intersection and `partial` can be tested
against a constructed component; caret-versus-band cannot be established from a map surface).

### SR-7 · Medium · the whole-document rule contradicts the existing design behaviour it sits beside

Proposal lines 114–115 require the whole-document target to measure "only the visible text viewport" and
line 111 forbids a rectangle over "a toolbar, label, neighbouring pane".

The existing whole-document design target does the opposite: `SourcePanel.designBounds` (`:503-505`)
returns `designPane.getVisibleRect()` when the line is null — the whole `DesignSourcePanel`, which
contains a status label as well as the text.

This is a defensible tightening, but the proposal reads as though it is describing one contract.

**Required correction.** Say the Java rule is deliberately stricter than the design one, and whether
design will be aligned. If not, two whole-document targets will behave differently and a future reader
will treat one of them as a bug.

### SR-8 · Medium · the existing line-band helper passes the stated mutation witness, so acceptance 5 needs a second one

`DesignSourcePanel.lineBounds` (`:80-88`) already converts a `modelToView2D` rectangle into a band:
`at.width = Math.max(20, text.getVisibleRect().width)` and `at.x = text.getVisibleRect().x`, then converts
coordinates. What it does **not** do is intersect vertically with the viewport, or account for a wrapped
logical line's visual rows.

Acceptance 5 (line 207) asks for a witness that fails "when returning the raw `modelToView2D` rectangle
instead". An implementation that copies `lineBounds` would pass that witness and still return a band over
the toolbar for a line scrolled above the viewport — the precise case line 111 forbids.

**Required correction.** Add a second witness for the missing vertical clip, and one for the wrapped-row
case, since those are the two behaviours the nearest existing code does not have.

### SR-9 · Low · Java lifetime silently differs from design lifetime

Design keeps a caption's revision and marks it stale — `captionRevision` at `MainFrame.java:2498`, and the
"(design edited since this caption)" mark specified at `spec-design-render.md:172`. Java instead
extinguishes on any revision change (proposal lines 143–145). Line 147 says design *re-anchoring* is not
extended to Java, but does not say the staleness *mark* is also not adopted, or why extinguishing is
preferred.

One sentence. Extinguishing is the stricter and I think better choice for code; it should be visibly a
choice.

### SR-10 · Low · a conditional acceptance is not an acceptance

Lines 68–69 claim source identity "may be a file or an archive entry; it must distinguish entries in the
same archive". Acceptance 7 (line 215) then says "Include an archive-entry source **if that lookup path is
supported by the implementation**."

Either the archive path exists — in which case confirm it before handoff and make the acceptance
unconditional — or it does not, in which case the identity claim in line 69 should not be made. As
written, the promise can be shipped untested by discovering at implementation time that the path is
unsupported.

The revision definition itself (lines 70–71: SHA-256 of the rendered string as UTF-8, labelled
`rendered-text-utf8`) is good and the explicit basis label is the right instinct, given `DesignDocument`
already carries a `revision` of its own (`DesignDocument.java:10`).

---

## Scope: one thing worth cutting

**Consider deferring `source:node:<instanceId>` to a second slice.** It is the only target that drags in
the resolution path behind SR-4, it needs the declaration-versus-statement caveat spelled out on both
surfaces (lines 48–50, 140–141), and it carries its own acceptance (item 2). The central promise of the
proposal — graph and implementation side by side — is delivered entirely by `source:java:<fqn>:line:<n>`,
and the worked example at lines 160–167 uses only that form. Cutting node targeting would remove the
riskiest resolution work from the first slice without weakening the payoff.

I am not recommending this, only noting that the proposal does not consider it. If node targeting stays,
SR-4 must be answered.

Everything else in the proposal is scoped tightly and I would not cut it. The one-document-per-batch
restriction (lines 73–76) is a good simplification and the reason to keep the whole-document target
explicit (lines 125–127) is sound — it is what stops a failed precise target becoming a false success.

---

## What I verified versus what I only read

**Reproduced by running:**

- `mvn -o -Dtest=SpotlightTargetTest,SpotlightSetTest,SourceNavigationTargetTest,SpotlightFrameTest test`
  → 115 run, 0 failures, 0 errors, **4 skipped**; the skips are all of `SpotlightFrameTest` (SR-6).
- Confirmed the working-tree proposal is the 223-line staged version, not the 83-line version the first
  review measured.

**Read in source, not executed** (every file:line above):

`SpotlightTarget.java` (families, parse, precheck, resolve, resolveAll, Surface); `SpotlightOverlay.java`
(`Lit`, `remeasure`); `MainFrame.java` (`installSpotlight`, `relightSpotlight`, `spotlightSurface`
reveal/bounds for the design families, the spotlight executor at 2448–2484, `litEcho`,
`designSpotlightRevisions`, `chooseSourceTarget`, `syncSourceForRecord`); `SourcePanel.java` (`Mode`,
`designBounds`, `designComponent`); `DesignSourcePanel.java` (`lineBounds`, `scroll`);
`SourceService.java` (`fqnForInstance`); `EventProcessorModel.java` (`fieldTypes`, `fieldTypeFqn`,
`resolveSimpleType`, `stripType`); `TopologyPanel.java` (`openSourcePane`, `openSourceFor`);
`SpotlightVocabulary.java` (`MAX_LIT` = 6, `MAX_CAPTION` = 160); `spec-design-render.md` §D-3.

**Not done:** no UI was driven, no window opened, no LLM session run, no implementation attempted, no
release or branch work touched. I did not run the full suite. Findings are against the proposal text and
the code it must extend; none of them is a reproduced failure of an implementation, which does not exist.

**Claims in the proposal I checked and found accurate:** `tab:source` lights only the tab button
(`spotlightSurface` reveal `case TAB`, bounds via `sideTabs.getBoundsAt`); existing Java targeting has no
spotlight rectangle (`designBounds` returns empty unless `mode == Mode.DESIGN`); family keywords are
case-insensitive while arguments keep their spelling (`parse:111`, `sub:184-198`); an instance id keeps
embedded colons (`sub` splits on the first colon only); the six-target limit is real
(`SpotlightVocabulary.MAX_LIT = 6`); `wentOut` exists as the proposal describes (`MainFrame.java:2465-2478`);
and the existing design vocabulary and its session-design semantics are as the proposal now describes
them, which was the first review's SS-1.
