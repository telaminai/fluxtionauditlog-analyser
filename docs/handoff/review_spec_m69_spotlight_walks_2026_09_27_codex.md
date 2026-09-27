# M69 spotlight walks — source review, 2026-09-27

**Verdict: r2 needs corrections before implementation.** The owner-directed feature is feasible, but its proposed
reuse of existing playback and identity plumbing is not safe as written. The accompanying r3 makes the corrections
below explicit while preserving the original decision text. It is a reviewed proposal, not a built or accepted feature.

Reviewed `main` at `cdda86d5`. No product code, implementation spike, participant project, provider or key was used.
This is source inspection, not a reproduction of a walk: no walk implementation exists. I participated in the earlier
evidence-bundle proposal discussion; agreement with that discussion is not evidence for these findings.

Evidence labels: **READ** means inspected source/documentation; **RAN** means an executed check with its result;
**REPORTED** means someone else's result, not reproduced; **OWNER** means the fixed direction supplied for this review.
Java paths below are relative to `src/main/java/telamin/fluxtion/audit/analyser/analyser/`; test paths use the corresponding
`src/test/java/` package. Line references identify the inspected parent, before r3's documentation insertions.

## Required proposal corrections

### R1 — D-W1, P-W3, W-A1/W-A9/W-A17: REPORTS does not confer storage parity

**READ:** `config/SettingsShare.java:193` exports reports only; `:383` detects REPORTS through `report.count`;
`:540` applies reports by name; `ImportPlan` also carries a reports list. `config/ProjectProfile.java:242` and
`:274–316` snapshot, restore and clear reports. `config/ConfigStore.java:176` chooses the report definitions from
`globalTier` during a project-active machine save, while `:177` always saves the live machine-local bin.
`config/KnownKeys.java:33–47` separates profile families from the deleted-report CONFIG-only family.

**Concrete failure/cost:** implement only `walk.N.*` reading/writing → a walk-only share file is not recognised as
REPORTS; omit snapshot/tier handling → switching projects or saving machine settings can lose or leak walks.
These are failure scenarios derived from the existing paths, not executed new-feature failures.

**Replacement:** retain REPORTS, but explicitly extend all these paths, disclosure, key cleanup and regression tests.
Walk-only imports must leave reports alone; the bin must never cross the share boundary. A WALKS category improves
independent selection but still needs every storage/tier change, plus another selection/disclosure surface. There is
no evidence it is the smaller implementation. **Delivery impact:** S1 is more than another serializer; requires tier,
share and bin checks. r3 §9.1 records the contract without reopening the owner's storage direction.

### R2 — D-W3/D-W6/D-W8, P-W2, W-A4/W-A12: existing view verbs save edits and leave grouping implicit

**READ:** `ui/GraphPanel.java:890` calls `mutated()` when pinning. `ui/GraphTabs.java:289` wires mutations to its
change callback; `:494–517` reopens a saved chart and calls `fireChanged`. `ui/MainFrame.java:5016` handles graph
edits through the settings/project save path. `ui/ActionExecutor.java:388` retains unspecified filter state and has
no grouping parameter. `filter/FilterState.java:62` changes the meaning of dimensions with `groupMode` and resets
the selection when that mode changes.

**Concrete failure:** play a step selecting a saved closed chart and a time window → the normal graph path saves
its open state and pin, contradicting “never writes a file”. Play the same dimensions under RAW_EVENT instead of
DIMENSION → the replayed population differs despite identical saved D-W3 fields.

**Replacement:** share validation and view primitives, with explicit transient playback semantics; include grouping
and all cleared filter values, applied in a defined order. Test saved definitions and settings after deferred saves
settle. **Delivery impact:** real presentation plumbing is needed; direct verb reuse is not a zero-change shortcut.
r3 §9.2 supersedes that premise, not the owner's presentation-state decision.

### R3 — D-W6/D-W7, P-W4, W-A2/W-A4/W-A6: a completed action is not a completed chart

**READ:** `ui/MainFrame.java:6520–6541` waits only after an `open` of a log/set. D-W3 excludes that wait correctly.
However `ui/GraphPanel.java:1082–1104` extracts in the background; `:860–871` already distinguishes pending, failed
and complete extraction. `ui/ChartPanel.java:246` returns the last paint's message, assigned in `paintComponent`
`:585`/`:592`. The field starts null; null also precedes the distinct empty-window messages at `:593–600`.

**Concrete failure:** a chart has painted successfully, then a walk changes its filter or shrinks its pane and reads
the getter before repaint → null falsely proves the new step drew. An unpainted or sample-empty chart also defeats
the proposed null-message test. Checking target geometry before changing tabs can reject a target that the step
would reveal. D-W6 also calls partial target availability “all-or-nothing” without separating view refusal.

**Replacement:** prevalidate the whole view, then apply it; use cancellable, bounded completion for extraction and
current layout before resolving/lighting targets. Preserve numbered unavailable targets and disclose partial display.
Use the authoritative chart rendering result, not a duplicate size calculation. **Delivery impact:** no dependency
on #56's expand command is needed, but its drawing/layout fact must have one owner. r3 §9.3 adds the readiness controls.

**READ / REPORTED distinction:** issue #56's description and owner direction were read; its original 900×620 failure
was not reproduced in this review. It remains useful as a proposed acceptance fixture, not new RAN evidence.

### R4 — D-W4/D-W4a/D-W2, W-A5/W-A13–W-A17: record identity does not bind a chart or certify a caption

**READ:** `report/LogFingerprint.java` (`sameContent`, `mismatch`) compares count and endpoint times with provenance
qualifications, not all contents. `llm/ReadService.java` uses `rawText` for unprojected reads. `parse/HeapLogStore.java`
(`rawText`) serves retained decoded text; `parse/MappedLogStore.java` reads the channel; `parse/RolledLogStore.java`
delegates to a member; `spi/SpiLogStore.java` retains the reader's text representation. P1 in the feedback file
`:879–900` explicitly requires changed-code/same-graph, absent provenance and mixed steps as well as graph changes.

**Concrete failure:** logs with equal count/first/last times differ at one middle value. A chart-only step has no
record target, so there is no record digest to catch the change. Even a matching digest for another record proves
nothing about that chart. Separately, “historical, shown as what it was” cannot recreate old data from stored pointers.
W-A5's unavailable target and W-A16's historical step need separate availability and identity semantics.

**Replacement:** define the record digest representation and bind capture to one coherent store/generation. A chart
needs a population/definition basis or an explicit unknown result. Preserve historical commentary without claiming
a historical rendering; keep per-target states in mixed steps. Graph equality qualifies structure, not unchanged
code behaviour. Retain declared author/time: an imported person's caption cannot automatically become “you”.
**Delivery impact:** v1 can withhold a chart's currentness when identity is unavailable, rather than inventing an
evidence engine. Claiming current chart populations requires specifying and implementing the stronger basis.
r3 §9.4 restores the missing P1 acceptance cases and makes this trade explicit.

### R5 — D-W1/D-W4a, W-A14/W-A17: the available graph digest can belong to the previous graph

**READ:** `ui/TopologyPanel.java:838–848` sets `loadedGraphSha256` after a stable file load. `:709` (`clearGraph`)
and `:805` (`loadFromSource`) change the graph without clearing that field; the getter at `:836` returns it directly.

**Concrete sequence:** load file graph A → clear → accept source-supplied graph B → getter still returns A's digest.
A walk consuming that getter as proposed can label B's structure current against A. This is a source-proven dependency
hazard; no UI probe was run and no existing product code was changed.

**Replacement:** bind the digest to the current graph/source and invalidate it on clear/source replacement; unknown
when no basis exists. **Delivery impact:** a small production dependency plus a transition regression is necessary
before M69 consumes the getter. r3 §9.5 records it; this documentation commit does not claim to fix the implementation.

### R6 — D-W3a, W-A9/W-A15/W-A17: following a name can follow a different focus

**READ:** `ui/TopologyPanel.java:358–372` implements save-by-name replacement; its delete menu removes by name.
`config/SettingsShare.java` (`apply`, named-focus merge) likewise replaces by name. I found no dedicated focus-rename
operation in this source; the proposed rename behaviour is a future contract, not an existing facility.

**Concrete failure:** delete focus DEMO, then create a different focus DEMO → a walk that resolves only its name now
points at different nodes instead of staying unresolved. Overwrite/import can do the same without a delete.

**Replacement:** bind the definition too; no inferred rename, and no silent repair after name reuse. Explicit supported
renames may update references with collision checks. **Delivery impact:** additional reference/acceptance work, not
necessarily a new focus-management UI. r3 §9.6 adds the cases P1 requested.

### R7 — D-W5/D-W8, P-W1, W-A2/W-A3/W-A12: the existing hook is too late, and keyboard focus is a separate requirement

**READ:** `ui/SpotlightOverlay.java:74–77` snapshots lit targets, dismisses, then invokes `onPressed` with only a
point and targets. The original MouseEvent exists before that dismissal, so pre-dismiss classification is feasible.
`PersonAtTheScreenFrameTest.java:62–84` explicitly guards keyboard focus: posted keys can be discarded without it,
and an open combo can consume Escape first.

**Concrete failure:** attach the walk popup to the existing callback → the spotlight is already gone, and button
identity is unavailable. Add only window arrow bindings → a focused table/combo can consume them, or no focus owner
can receive them. The popup's outside-dismiss rule also needs priority over O-2's outside-walk rule.

**Replacement:** classify input before dismissal; account for platform popup triggers; specify focus acquisition and
restoration, and test actual keys with a focus precondition. **Delivery impact:** preserve existing menu-click behaviour
with neighbouring tests; no reason to alter how spotlight targets are lit. r3 §9.7. P-W1 is plausible READ, not confirmed RAN.

### R8 — D-W9/D-W6, W-A3/W-A8: generation is necessary, but not the whole presentation lifecycle

**READ:** `session/node/OpenLog.java:58–99` clears a log without advancing generation; an accepted open advances it.
`onLogIdentityObserved` updates identity without an open. The generated `session/generated/SessionProcessor.java`
routes these events through the existing nodes; `MainFrame.onSessionSnapshot` is the rendering boundary.
`ui/ActionExecutor.java:133–151` clears the spotlight after a successful view-changing action.

**Concrete failure:** treating new generation as the only invalidation leaves record currentness behind after close
or an identity degradation. Ending a walk on every normal view-action dismissal would instead end it during its own
filter/graph step. A late asynchronous chart completion can also resurrect a previous step unless cancelled.

**Replacement:** retain O-3 exactly: generation-driven ending, presentation state, no node/regeneration. Render target
availability from the same snapshot on other relevant transitions; distinguish own-step origin from external actions,
and cancel obsolete preparation with a presentation ticket. **Delivery impact:** one presentation lifecycle boundary,
not scattered event handlers recomputing session verdicts. r3 §9.8. This is not a proposal to replace Fluxtion dispatch.

### R9 — D-W8, P-W5, W-A10: the seventeenth-verb inventory is incomplete

**READ:** `llm/ActionDispatcher.java:92–116` routes supported actions and applies record-identity policy;
`ui/ActionExecutor` routes UI actions; `llm/ContextSections.java:35–42` enumerates projection keys;
`llm/PromptBuilder.java:274` distinguishes hand-described verbs. `CloseVerbTest.java:50` and
`ProjectVerbTest.java:45` explicitly assert sixteen verbs, beyond the four contract tests the prompt names.
Project-model and spotlight-dismissal contracts also read the affected surfaces.

**Concrete failure:** add schema/manifest/prompt entries only → dispatcher rejects `walk`, projected context omits
its state, or no-log listing is refused by an indiscriminate record-read gate. A direct view option would not touch
all of these contracts. Ambiguous mutation/play combinations also need an explicit refusal contract.

**Replacement:** include routing, per-operation read boundaries, context projection, MCP metadata, count tests,
project facts, disclosure/security/docs tests and both CI frame lists. **Delivery impact:** larger than a graph option,
but bounded and testable. r3 §9.9 replaces the understated inventory.

## Owner decisions and optional improvements

**OWNER:** overlay arrows, independent report-like storage, right-click saving in v1, O-1–O-4 stand unchanged.
No majority vote or model agreement is offered as evidence. REPORTS versus WALKS remains technically open to a future
independent-sharing requirement; there is no present source-based reason to add a category.

**Optional:** consider a concise state/availability table in the eventual user guide. The spec's original decision
text is intentionally retained with supersession notes, so implementers should read §9 rather than treating r2 and
r3 as concurrent contracts. Once this revision is accepted, a separate editorial consolidation could reduce that
reading burden without losing the review history.

## Predictions and verification

| prediction | disposition |
|---|---|
| P-W1 | READ: feasible before dismissal, not verified on a display. The existing post-dismiss callback alone cannot do it. |
| P-W2 | READ: refuted as safe direct mapping; graph actions persist and grouping is absent from filter parameters. |
| P-W3 | READ: unconfirmed effort estimate; report-specific storage/disclosure/tier contracts need extension. Existing report tests may remain unchanged alongside new tests. |
| P-W4 | READ: literal getter-access prediction confirmed; its use as current-step drawn-state proof is refuted. |
| P-W5 | READ: refuted as the complete set of affected contract surfaces. |

**RAN — documentation checks:**

- Fetched `origin/main`; isolated checkout at `cdda86d5`.
- `mkdocs build --strict`: passed (documentation build reported 0.63 seconds).
- `git diff --check`: passed.
- The exact multiline CLAUDE rule-one tracked-file sweep: empty output. Both changed documents also passed a
  separate content sweep and local-path/address check. Initial attempts to extract that command omitted its
  exclusion line or flattened its continuation incorrectly; they were corrected before recording the clean result.
- Checked the new relative Markdown link resolves locally. No product test or Java compilation was run, following
  the owner's clarification that this task is a documentation review/edit. No new tests or spikes were written.
- Personal commit identity checked. Only this report and the M69 spec are intended for the commit.

No RAN item above establishes a future M69 acceptance. The code-based findings are READ, as labelled.

## Not verified

No walk was implemented, no interactive display trial or mutation was run, and no performance/paint timing was
measured. Proposed failure sequences above are READ deductions unless explicitly marked RAN. Issue #56's original
visual failure and the participant's P1 experience are REPORTED behaviour, not reproduced here. No claim is made
that the future walk acceptance checks pass. The framework reference and generated processor were read for the
lifecycle claim; no generator or hosted compiler was run. Only the spec and this review are changed; tracker status
marks, archived evidence and product code are untouched.
