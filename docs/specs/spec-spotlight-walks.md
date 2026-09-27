# M69 · Spotlight walks — saved, stepped explanations on the overlay

**Status: DRAFT r3 (2026-09-27). O-1 to O-4 decided by the owner. Source-checked corrections below; implementation and acceptance remain unverified. Nothing built yet.**

**r3 changes:** [Source review](../handoff/review_spec_m69_spotlight_walks_2026_09_27_codex.md)
R1–R9 correct storage, playback side effects/readiness, identity, input handling and contract coverage.
Section 9 supersedes the identified r2 clauses and acceptance assumptions. The original wording is retained
for review history, not as a second implementation contract. Owner directions and O-1 to O-4 are unchanged.

**Owner direction (2026-09-27), not open for review:**
> Not a player — forward/back arrows on the overlay on the analyser's UI. Spotlight walkthroughs are stored like
> reports. They are useful when not in an evidence bundle.
>
> Right click while in spotlight gives a popup menu for save.

> Regardless of the evidence-bundle proposal, we will need the spotlight walkthrough. *(2026-09-27)*

Background: the evidence-bundle proposal §8 and discussion log L-24
(`docs/proposals/evidence-bundle/`, branch `proposal/evidence-bundles`); `spec-spotlight.md` (M64).

## 0. Prior work this spec absorbs: feature request 40 / proposal P1

A saved walkthrough was proposed before, on 2026-09-20:
- tracker ▸ *Spring authoring observed acceptance* ▸ **"Feature request 40 (intake alias P1) — saved focus
  captions"**;
- the full text is `docs/handoff/evidence/spring-authoring-feedback-2026-09-20-tours/ANALYSER-FEEDBACK.md`
  ▸ *Proposal P1*.

A participant recalled a saved focus twice and retyped the same six-target caption sequence each time. The node set
survived; the explanation did not. **This spec delivers that request**, and the tracker item points here.

Where the owner's 2026-09-27 direction changes P1:

| P1 said | now |
|---|---|
| optional ordered captions **on an existing focus**; "the proposal extends focuses rather than introducing persistence generally" | a walk is **its own saved artefact, stored like reports** (owner). A step may still apply a named focus (D-W3), so a focus-led explanation remains one kind of walk. |
| "sequential playback is a useful follow-up" | stepping, with ◀ ▶ on the overlay, **is** v1 (owner) |

What P1 established and this spec keeps (each is cross-referenced where it is applied):
- **Saving is explicit.** Spotlights stay transient by default; a walk is only ever saved by a deliberate act: a
  right-click save, or the verb. Changing the pinned never-saved rule is done here, as a reviewed extension (D-W1).
- **Targets are semantic, never screen rectangles.** They resolve at playback (D-W6).
- **Captions are attributed commentary**, not facts the analyser certifies. Author identity is a declaration (D-W2).
- **The identity contract states its strength.** `LogFingerprint` compares record count and first/last log times,
  so different contents can pass it. A walk adds a per-record digest, and a graph digest, and says "unknown" rather
  than accepting missing identity (D-W4).
- **Structural and observational steps are checked differently** (D-W4a):
  - a structural step (topology, focus) is checked against graph identity;
  - an observational step (a record, a chart) is checked against the run.
- **An unresolved target stays visible, with its reason.** It is never silently dropped, the surviving targets are
  never renumbered, and nothing is re-mapped by a guessed name (D-W6). Auto-named nodes (`varCalculator_14`) change
  between builds (#32), so they are the expected case, not an edge case.
- **A focus rename or delete must be defined** for a step that names it (D-W3a).
- **It is not a second evidence or report engine.** A walk holds pointers and captions; the evidence stays in records,
  charts, flags and reports (§1).
- **P1's future-design acceptance cases** become W-A13 to W-A17 (§5).

---

## 1. What a walk is

A **walk** is a named, saved, ordered list of **steps**. Each step:
1. restores a view: tab, filter, record, chart, topology focus;
2. lights up to six targets on the spotlight overlay, each with its caption;
3. can carry one sentence for the step as a whole.

The person moves through it with **◀ Back** and **Next ▶** on the overlay itself, not in a separate player. A walk
is the author's argument, told at the places it rests on. It is **testimony**: the captions are the author's words,
and every stop points at something the reader can check.

It stands on its own. A walk is saved in the project like a report, so it is:
- useful in the author's own project;
- shared when the profile is shared;
- carried inside an evidence bundle, because the bundle carries the profile.

## 2. Decisions

> **r3:** storage integration and acceptance are qualified by §9.1; automatic report parity is superseded.

**D-W1 — a walk is a saved project artefact, beside reports.**
- It is stored in the project profile under the REPORTS category, as a `walk.N.*` family, so share, import and
  project scoping behave exactly as they do for reports.
- Deleting one is recoverable, through the same bin pattern as reports.
- **This supersedes `spec-spotlight.md` D-SP4 for walks only.** A live spotlight stays transient by construction: the
  overlay stays "dumb", and it holds nothing a profile reads. A walk *re-creates* its spotlights from saved steps each
  time a step is shown.
- The walk also records the **graph's identity** (its SHA-256 when it was loaded from a stable file, otherwise
  "unknown", said so) for structural steps (D-W4a).
- *Alternative:* a new `WALKS` settings category. It gives separate share and import control, but adds a category to
  every import surface. Would change if the owner wants to share walks without reports.

> **r3:** the portable meaning of attribution is qualified by §9.4; an imported author is not automatically “you”.

**D-W2 — captions are testimony, attributed.**
- The overlay's callouts already say "assistant" (D-SP2). A walk's callouts say *who authored the walk* ("assistant"
  or "you"), and the control strip names the walk.
- A walk never presents a caption as a fact the analyser established.

> **r3:** direct verb reuse and the filter allow-list are superseded by §9.2–§9.3.

**D-W3 — a step restores its view from a fixed allow-list.** A step may set:
- **`tab`**: one of the spotlight vocabulary's tabs;
- **`filter`**: `from`, `to`, `dimensions`, `text`;
- **`record`**: a record index, selected as `goto` would;
- **`graph`**: a saved chart, opened or selected **by name only**, plus its window (`from`/`to`) — no edits;
- **`focus`**: a named topology focus, applied.

Nothing else. In particular, never `open`, `report`, `screenshot`, `source_root`, a delete, a rename, or a project or
log switch. A walk shows; it never changes what is loaded, and never writes a file. That is also why no step ever
waits for a load. The saved-analysis runner's busy-wait on `loadInFlight` is not repeated here.

> **r3:** digest scope and mismatch handling are superseded by §9.4.

**D-W4 — a walk knows what it was written against.**
- At save, a walk records the log's fingerprint, the way a report does (`writtenAgainst`).
- Each record target also carries a SHA-256 of that record's raw text.
- Played against a different log, the strip says so. Any record target whose digest does not match is shown
  **unavailable**, never re-pointed at a nearby record.

> **r3:** the current/historical/unresolved contract is qualified by §9.4–§9.5; “shown as what it was” does not promise a retained old rendering.

**D-W4a — structural and observational steps are checked against different things** (P1).
- **Observational targets** (`records:row`, `detail:node`, `graph:…`) are checked against the *run*: the record
  digest, and the log fingerprint.
- **Structural targets** (`topology:node`, a focus) are checked against the *graph*: the recorded graph digest, when
  one was known.
- Each step shows its own state in the strip: **current**, **historical** (the run or graph differs, shown as what
  it was, not as a current observation), or **unresolved** (the target cannot be found, with the reason). The policy
  is visible per step, never averaged across the walk.

> **r3:** name-only reference tracking is superseded by §9.6.

**D-W3a — a step that names a focus follows the focus's name.** A rename of that focus updates the walk's reference. Deleting the focus leaves the step **unresolved: focus deleted**. The
walk is never deleted with it.

> **r3:** input precedence and focus requirements are specified by §9.7.

**D-W5 — the controls live on the overlay.**
- The overlay gains a **control strip**: `◀ Back · <walk title> — step 2 of 5 · Next ▶ · ✕`. Keyboard: ← and → to
  move, Esc to end.
- A press on the strip is handled by the strip. Today any press dismisses the overlay; the strip is the one exception.
- A press anywhere else, or any verb or click that changes the view, ends the walk, as it ends a spotlight today. The
  walk's list then offers **Play from step N**, where N is the last step shown. That position is transient and never
  saved (O-2).
- **A right-click is not a dismissing press** (owner direction). It opens the save menu (D-W8).

> **r3:** synchronous execution and the ambiguous all-or-nothing claim are superseded by §9.2–§9.3.

**D-W6 — showing a step is all-or-nothing, and never silent.**
- To show a step, the frame: checks every target; applies the step's view through the same verb executor saved
  analyses use (synchronous view changes only, D-W3); then lights the targets.
- If a target is unavailable or a view change is refused, the step is shown with that target marked unavailable and
  the reason in the strip. Or, if nothing can be shown, the walk stops at the previous step and says why. A step is
  never skipped silently, and never reported as shown when it was not.

> **r3:** the empty-message proof and complete independence from #56 are superseded by §9.3.

**D-W7 — a chart step checks that the chart drew.**
- Inside the analyser, the walk can ask the chart panel directly whether it drew (`ChartPanel`'s empty-plot message,
  the fact #56 proposes to publish). A chart target on a chart with no room is shown unavailable, with the size and
  "widen the window".
- This removes the walk's dependency on #56 (L-24). When #56's `expand` and `view` land, the walk requests room
  through #56's single layout-override owner, not a mechanism of its own.

> **r3:** authoring capture and verb integration are qualified by §9.2, §9.4 and §9.9.

**D-W8 — authoring (O-1).**
- **The assistant** creates, replaces, deletes, renames and restores walks through a verb, mirroring `report`:
  `walk {name, title?, steps: [...]}`, `walk {name, delete: true}`, `walk {name, rename: "…"}`,
  `walk {restore: "…"}`.
- `walk {name, play: true, step?}` lets the assistant *present* a walk to the person.
- **A person** saves the spotlight in front of them by **right-clicking while it is showing** (owner direction). A
  popup menu offers:
  - *Save as new walk…*, which asks for a name;
  - *Add to walk ▸ <name>*, which appends this spotlight as the next step of an existing walk;
  - while a walk is showing, *Replace this step*.

  The saved step is **what is on screen**: the lit targets and their captions, plus the current view, taken through
  D-W3's allow-list. Record targets get their digest (D-W4). Anything outside the allow-list is not saved, and the menu
  says which part was left out. The popup is not modal: Esc or a press elsewhere closes it, and the spotlight stays.

> **r3:** lifecycle details are qualified by §9.8; O-3 remains unchanged.

**D-W9 — playback is presentation state; ending it is the snapshot's decision (O-3).**
- Which walk is showing, and which step, is view state, like which tab is selected. It is not session state. Nothing
  in the session reads it, and nothing stale is decided from it.
- The one "when" it has is **ending when the log changes**. That is rendered from the published snapshot: a new log
  generation ends the walk, the same way a changed generation already retires record-bound views. There is no
  hand-placed dispatch.

## 3. Where walks appear

- **The Reports tab** lists walks under the reports, with **Play**, **Rename**, **Delete** and **Restore deleted…**
  (the report actions).
- **The Project panel** counts them beside reports.
- **`context`** lists each walk's `name`, `title`, step count and `writtenAgainst`. While a walk is showing, it also
  names the walk and the current step, so the assistant's `context` → `screenshot` loop can confirm what the person
  sees (the D-SP4 exception, extended).

## 4. Storage

Under the REPORTS category:

```
walk.0.name=alarm-reset
walk.0.title=The admin reset re-arms the alarm
walk.0.createdAt=2026-09-27T18:02:11Z
walk.0.fp.*                                   the log fingerprint, as report.N.fp.*
walk.0.step.0.caption=The alarm is raised on the third rejected row.
walk.0.step.0.view.tab=graph
walk.0.step.0.view.graph=Alarm lifecycle
walk.0.step.0.target.0=graph:Alarm lifecycle:note:1
walk.0.step.0.target.0.caption=RAISED: 3 rejected
walk.0.step.1.view.record=36
walk.0.step.1.target.0=records:row:36
walk.0.step.1.target.0.digest=sha256:…
walk.0.step.1.target.0.caption=Signal resetAlarm, via admin
```

Unknown keys are preserved on rewrite, as the profile already does. Walk names follow chart-name rules, so a
spotlight can address them.

## 5. Acceptance

Each check has a wrong-result witness and a registered mutation control (rule 8).

| id | check | how |
|---|---|---|
| W-A1 | A walk saved over the socket round-trips through the profile byte for byte, survives restart, and is listed in the Reports tab and in `context`. | headless model + profile test |
| W-A2 | Next and Back on the strip show steps 1 → 2 → 1, each restoring its view and lighting exactly its targets; the ← and → keys do the same. | frame test, real clicks on the strip |
| W-A3 | A press on the strip does not dismiss the overlay; a press elsewhere ends the walk; **Play from step N** resumes there. | frame test |
| W-A4 | A dirty starting view (another filter, tab and record) does not leak into step 1. | frame test |
| W-A5 | Played against a different log, a record target with a mismatched digest is shown unavailable with its reason, and is not re-pointed. | frame test with two fixture logs |
| W-A6 | A chart step on a chart with no room (the frame at 900×620) shows the target unavailable, with the size. | frame test; the L-24 witness |
| W-A7 | A step whose view names a verb or field outside D-W3 is refused at save, naming the field. | headless |
| W-A8 | Opening another log ends a showing walk (a new generation). | frame test |
| W-A9 | Delete moves a walk to the bin; Restore brings it back; rename keeps its steps. | headless + frame |
| W-A13 | P1: same graph, new run. Structural steps are current; observational steps are historical, with a reason. | frame test, two runs of the demo fixture |
| W-A14 | P1: changed graph, same node names. Structural steps show historical, not current, even though every name resolves. | headless + frame |
| W-A15 | P1: a renamed or auto-renamed node, and an ambiguous one, are unresolved with reasons; the other targets keep their numbers. | frame test |
| W-A16 | P1: two logs with the same record count and first/last times but different contents. The per-record digest shows the observational step historical, where the fingerprint alone would have passed it. | headless |
| W-A17 | P1: restart, and export then import of the profile, keep every walk; the human UI and `context` report the same current/historical/unresolved states. | frame test + `context` |
| W-A12 | A right-click on a live spotlight opens the save menu without dismissing it. *Save as new walk* saves exactly the lit targets, captions and allow-listed view, and a replayed step lights the same targets. Esc closes the menu and keeps the spotlight. | frame test, real right-click |
| W-A10 | The verb's contract: schema, manifest, prompt and MCP tool list agree. | the existing contract tests, extended |
| W-A11 | Docs: a user-guide section, the assistant verb reference and a CHANGELOG line; the capture harness shows a walk on the demo fixture. | docs gates |

## 6. Predictions — committed before code

- **P-W1.** The overlay's press handler can hit-test the strip, and tell a right-click from a dismissing press, before
  dismissing, with no change to how targets are lit.
- **P-W2.** Every D-W3 view field maps onto an existing verb's parameters, with no new view capability.
- **P-W3.** The REPORTS category's reader and writer take a `walk.N.*` family without touching the report family's
  tests.
- **P-W4.** The chart panel's empty-plot message is readable from the frame at step time, with no layout change.
- **P-W5.** A seventeenth verb touches the same four contract classes a new `graph` option would (L-17's inventory),
  plus the prompt's verb list.

## 7. Plan

| slice | what | done when |
|---|---|---|
| S1 | `WalkSpec` model, validation (D-W3/D-W4) and profile storage | W-A1, W-A7 and W-A9 headless |
| S2 | the `walk` verb and its contract | W-A10 |
| S3 | the overlay's control strip, step showing (D-W6), the chart check (D-W7) and the right-click save menu | W-A2 to W-A6, W-A8 and W-A12 as frame tests, registered in CI's frame list |
| S4 | the Reports tab and Project panel listing, `context`, docs, CHANGELOG, a capture | W-A11; the full gates |

## 8. Open questions for the owner

- ~~**O-1.**~~ **Decided by the owner, 2026-09-27: a new `walk` verb**, mirroring `report` (D-W8). It is the
  seventeenth verb, so its schema, the manifest, the prompt and the MCP tool list are updated together (W-A10).
- ~~**O-2.**~~ **Decided by the owner, 2026-09-27:** a press outside the strip **ends** the walk, and its list
  offers **Play from step N**, remembered only for the session (D-W5).
- ~~**O-3.**~~ **Decided by the owner, 2026-09-27: presentation state**, held by the frame like the selected tab.
  The published snapshot's log generation ends it; there is no session node and no regeneration (D-W9).
- ~~**O-4.**~~ Settled by the owner: a right-click on a live spotlight opens the save menu, in v1 (D-W8).


## 9. r3 source-checked corrections and acceptance extensions

These requirements supersede the marked r2 clauses above. The review records source evidence and delivery
costs; it does not claim that a new walk implementation or its future controls have run.

### 9.1 Storage: REPORTS is a category choice, not an existing walk serializer (R1)

Retain REPORTS. A separate WALKS category offers independent selection but does not remove the need to change
serialization, preview, application, project snapshots and machine-tier saves. Broaden the category's visible
label/disclosure to include walks and commentary. Both export and preview recognise `walk.count` independently
of `report.count`; a walk-only import must not remove reports. Replacement is by name within each family.

Walk definitions participate in `ProjectProfile.Snapshot`, snapshot/restore/clear, and
`ConfigStore.save(config, globalTier)` with the same tier choice as report definitions. The deleted-walk bin
is machine-local even while a project is active, never exported or imported. Register live and deleted key
families at their respective profile/config scopes, and remove obsolete indexed entries on save.
W-A1/W-A9/W-A17 additionally cover project A → B → close, a machine save while A is active, walk-only sharing,
category disclosure, and deletion down to an empty bin. Existing report tests remain regression gates;
P-W3's no-test-change estimate is unconfirmed; existing tests may stay unchanged where new tests cover the additions.

### 9.2 View replay is transient and complete (R2)

Do not implement D-W3 by blindly issuing the current `graph` verb. Pinning a graph and reopening a saved closed
graph currently request persistence. Walk playback must use shared validation and view operations with an
explicit transient presentation mode: no saved-chart pin/open-state edits, no profile or machine-settings
write caused by playback. Normal graph actions keep their current persistence semantics. Ending a walk clears
its temporary chart overrides; it does not overwrite a later explicit edit by the person.

Extend the filter allow-list with `groupMode` (`DIMENSION` or `RAW_EVENT`). Save all filter constraints explicitly,
including cleared values; restore grouping before dimensions. Missing filter fields in a stored legacy step
must have documented defaults, not mean “keep whatever was selected”. Bind searchable text to the current
store; do not serialize a function or carry a previous store's text source. A right-click capture must either
capture this complete allowed view or name the part it cannot reproduce.

W-A4/W-A12 cover opposite grouping modes, a dirty dimension/text filter, a pinned chart and a saved closed chart.
Compare saved definitions and both settings files before/after playback, after pending saves have settled.
A control that routes playback through the persisting graph operation must fail the no-write assertion.
This is additional presentation plumbing, not a claim that every field already has a suitable public verb.

### 9.3 Preparation, availability and actual drawing (R3)

Validate the entire view request before applying any of it; a refused view leaves the previous step/view intact.
After an accepted view change, resolve targets against that view. Missing individual targets remain numbered
and explicitly unavailable; partial target resolution is allowed and is not called all-or-nothing success.
The strip states preparing, shown, partly available or unavailable truthfully. If no target is usable, report
that state without claiming the previous screen depicts the requested step. This supersedes D-W6's ambiguous
choice between showing a failed step and silently remaining on the previous view.

No step opens a log or waits on `loadInFlight`. Chart extraction, debounced filter refresh and painting are
nevertheless asynchronous. Use a bounded, cancellable completion condition for the requested view and current
layout; never block the EDT or copy the saved-analysis busy-wait. A superseded completion cannot light targets,
advance the step or overwrite the current reason. On expiry, publish unavailable with a reason.

`ChartPanel.lastEmptyMessage() == null` is not proof of drawing: it can mean not painted yet, a previous paint,
or a window with no samples. Use extraction completion and the chart's authoritative rendering/geometry result,
bound to the current view and layout. Do not introduce a second plot-size formula. Check showing/visible bounds
as well as the empty state. “Widen the window” applies only to insufficient room, not to missing samples.
Coordinate this fact with #56's single layout owner; v1 need not implement its expand/view commands.

W-A2/W-A4/W-A6 cover delayed extraction, immediate resize after a good paint, an unpainted chart, an empty window,
and rapid Next → Back. Named controls must distinguish stale paint from current drawing and a late completion
from the current step. A null-message-only control must fail. P-W4 is not sufficient as an acceptance premise.

### 9.4 Identity states describe their actual basis (R4)

A per-record digest binds only the indexed record's raw-text representation, not the chart population, build
or entire run. Define it as SHA-256 over UTF-8 encoding of the exact unprojected `LogStore.rawText(index)` string,
without trimming or inserted export framing. Record the representation/reader basis; a missing or incompatible
basis is unknown, not equal. This is not a claim about original file bytes. Capture identities and view facts
coherently for one store/session generation and bounded population, respecting the existing read-identity gate;
if that changes during capture, refuse or retry the save without silently rebinding old captions to new data.

For a chart, matching count/end times or one record is insufficient. “Current” requires an established identity
for the data population the chart uses and its saved definition, including external-series dependencies where
present. If such a basis is unavailable in v1, show unresolved with “chart population identity unknown”; do not
invent a whole-log guarantee. Any new population digest must define ordering, boundaries and representation
before it is used, and hashing must not block the EDT. The coarse report fingerprint remains a useful mismatch
warning, never a substitute for this basis.

Keep the three strip states, but retain each target's reason and availability. Known mismatch is historical;
unknown identity or an absent/ambiguous target is unresolved. A historical caption can be displayed as prior
commentary, but a mismatched record target stays unavailable (W-A5). No original chart/record is reconstructed
by this feature. A mixed step must disclose its target states, never label the whole step current when one
required basis is historical or unknown. UI and context expose the same qualification.

W-A13/W-A16/W-A17 add a chart-only step on same-count/same-time logs with a changed middle value, absent identity,
and a mixed structural/observational step. Historical attribution retains the declared author and capture time;
sharing cannot turn another person's declaration into “you”. Graph identity establishes structure only, not the
truth of code-level commentary: retain relevant declared code/build provenance when available and disclose its
absence. W-A14 adds changed code with identical GraphML; do not certify unchanged behaviour from graph equality.

### 9.5 A graph digest must belong to the current graph (R5)

Do not consume `loadedGraphSha256()` without establishing its association with the displayed graph. At the
reviewed source, `clearGraph` and `loadFromSource` leave that field unchanged. M69 implementation must invalidate
or rebind the identity on those transitions. A source-supplied graph without an established content identity is
unknown. W-A14/W-A17 include file graph A → clear → source graph B, and require that A's digest cannot qualify B.
This is a dependency to close in implementation, not a claim that the source defect has been fixed by this spec.

### 9.6 Names locate focuses; they do not establish unchanged definitions (R6)

Bind a focus reference to its saved definition as well as its name. The existing save/import paths can replace
that definition under the same name. A missing focus remains unresolved; deleting then recreating that name
must not silently repair an old reference to different contents. An explicit supported rename may update
references atomically, with collisions refused; do not infer a rename from absence plus a similar name. This
contract does not require adding a new focus-rename UI to deliver walks.
W-A9/W-A15/W-A17 add replace-by-name, delete/recreate, import collision and an explicit rename if supported.

### 9.7 Input precedence must precede dismissal (R7)

Classify strip actions and popup requests before the existing dismissal path. Its current `onPressed` hook
runs after dismissal and receives only a point, so it cannot implement this contract unchanged. Preserve ordinary
spotlight/menu activation behaviour outside the exceptions. Handle platform popup triggers on press/release
without activating twice. While the save popup is open, its dismissal takes priority and keeps the spotlight;
otherwise an outside-strip press ends the walk as O-2 requires.

Provide an explicit keyboard-focus policy for the strip, restore prior focus when appropriate, and avoid arrow
keys being consumed by a focused table/combo. W-A2/W-A3/W-A12 use real mouse/key events with an asserted focus
owner, including a pre-focused combo, popup Escape and ordinary outside dismissal. Calling the bound action
alone is not keyboard acceptance. P-W1 is feasible from the event API, but remains an unrun prediction.

### 9.8 Presentation lifecycle consumes the snapshot (R8)

Keep O-3: no new session node or regeneration. New log generation ends the walk through snapshot rendering.
Do not assume generation changes on close or on an identity observation: current source does neither.
An absent log or a changed published identity must therefore invalidate affected target availability/currentness
from that same snapshot, even without a new generation. Do not recompute session verdicts in input listeners.

Distinguish a walk's own view application from an external view-changing action; otherwise the existing
`ActionExecutor.render` spotlight dismissal can end the walk at its own first step. Use a scoped presentation
origin/ticket, not a global suppression of external dismissals. Pending preparation checks its ticket and captured
generation before publishing. W-A8 extends to close, same-generation identity degradation and a late completion
after a log switch; W-A3 proves own-step changes survive while an external successful view action ends playback.

### 9.9 Verb integration and delivery checks (R9)

W-A10 covers dispatcher/executor routing, operation-specific read-identity checks, `VerbSchemas`, MCP metadata,
the in-process and REST prompt manifests, context projections, Project-panel facts, assistant docs/help and skills.
Listing/editing stored walks can work without an open log; record-dependent playback/capture still honours the
existing read boundary. Define accepted operation combinations and reject incompatible create/delete/rename/
restore/play fields before mutating anything. Unknown nested view fields remain refusals under W-A7.

Tests include `ManifestVerbContractTest`, `McpToolsTest`, `InProcessManifestNamesEveryVerbTest`,
`VerbSchemasTest`, the explicit verb counts in `CloseVerbTest` and `ProjectVerbTest`, plus `ContextSectionsTest`,
Project-model contracts and spotlight dismissal tests. Inventory affected security/disclosure tests too; adding
a verb is not merely adding an option to an existing one. Register new frame suites in both CI lists and controls
in the gate. S1 includes §9.1/§9.4–§9.6 acceptance, S2 includes this inventory, S3 includes §9.2–§9.3/§9.7–§9.8,
and S4 includes cross-surface identity/attribution parity. These are future delivery gates, not completed checks.

### Prediction disposition at r3

| id | source-review disposition |
|---|---|
| P-W1 | READ: pre-dismiss classification is feasible, but requires changing the existing callback path. Keyboard/popup behaviour is not verified. |
| P-W2 | READ: refuted as direct safe reuse; graph operations persist, and grouping restoration is missing from the filter verb. |
| P-W3 | READ: not established; storage paths are report-specific. Additional share/tier regressions are necessary, but inspection does not prove existing report test bodies must change. |
| P-W4 | READ: confirmed literally that the getter is accessible without a layout change; refuted as proof of the requested step's drawn state. |
| P-W5 | READ: refuted as a complete inventory; routing, identity policy, context projection and additional count tests also participate. |
