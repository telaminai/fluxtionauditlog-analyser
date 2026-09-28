# M69 · Spotlight walks — saved, stepped explanations on the overlay

**Status: SHIPPED in 1.26.0 (2026-09-28)**, merged as `5cdd12ec` (PR #57). ACCEPTED r4 (2026-09-27), consolidated. The
owner accepted r3 and asked for one contract. This file is that contract. [§9](#9-revision-history) records how
r1–r3 reached it, and what each review changed. The r3 wording that r4 folds in is in git history at `80decad7`.

**Owner direction (2026-09-27), not open for review:**
> Not a player — forward/back arrows on the overlay on the analyser's UI. Spotlight walkthroughs are stored like
> reports. They are useful when not in an evidence bundle.
>
> Right click while in spotlight gives a popup menu for save.
>
> Regardless of the evidence-bundle proposal, we will need the spotlight walkthrough.

**Owner decisions:**
- **O-1:** a new `walk` verb, mirroring `report`.
- **O-2:** a press outside the control strip ends the walk, and its list offers *Play from step N*.
- ~~**O-3:** playback is presentation state, ended by the published snapshot; no session node.~~ **Superseded by the
  owner, 2026-09-27, before any code:** *"if we have to use events and regenerate the event dispatcher do that, we want
  the logic, transitions and state mutation in the orchestrator in a single place."* Walk playback is therefore a
  **session node** (§3.8).
- **O-4:** the right-click save menu is in v1.

---

## 1. What a walk is

A **walk** is a named, saved, ordered list of **steps**. Each step:
1. restores a view from a fixed allow-list (§3.3);
2. lights up to six targets on the spotlight overlay, each with its caption;
3. may carry one sentence for the step as a whole.

The person moves through it with **◀ Back** and **Next ▶** on the overlay itself.

**A walk is testimony.** Its captions are its author's words; every stop points at something the reader can check.
It holds pointers and captions only, never evidence. The evidence stays in records, charts, flags and reports. It is
not a second report engine.

**It stands on its own.** A walk is saved in the project, as reports are. So it is:
- useful in the author's own project;
- shared when the profile is shared;
- carried inside an evidence bundle, because the bundle carries the profile.

It delivers **feature request 40 / proposal P1** (2026-09-20): tracker ▸ *Spring authoring observed acceptance*;
`docs/handoff/evidence/spring-authoring-feedback-2026-09-20-tours/ANALYSER-FEEDBACK.md` ▸ *Proposal P1*. A participant
recalled a saved focus twice and retyped the same six captions each time. The node set survived; the explanation did
not.

## 2. What a walk promises, and what it does not

| it promises | it does not promise |
|---|---|
| each target shows what it pointed at when saved, **or** says why it cannot: current, historical, or unresolved, with a reason | that a caption is true — it is the author's claim |
| a step never silently drops a target, renumbers the rest, or re-points a target at a guessed replacement | that "current" structure means unchanged code behaviour: graph equality establishes structure only |
| playing a walk writes nothing: no profile, no settings, no chart edit | to reconstruct a historical record or chart — a historical target is shown as not current, not re-drawn as it was |
| a chart target is lit only when the chart actually drew, at the size on screen now | exact identity where the analyser has none. Unknown stays unknown, never "equal". |

## 3. The contract

### 3.1 Storage (owner: "stored like reports"; review R1)

- **Where.** In the **REPORTS** settings category, as a `walk.count` / `walk.N.*` family beside `report.*`. The
  category's label becomes *"Investigation reports and spotlight walks (definitions + commentary — never log
  data)"*.
- **Tiers.** Walk definitions follow the report definitions' path everywhere they go:
  - `ProjectProfile.Snapshot` snapshot, restore and clear;
  - `ConfigStore.save(config, globalTier)`, using the same tier choice;
  - `SettingsShare` export, preview and apply.
- **Sharing.** Export and preview recognise `walk.count` independently of `report.count`. A walk-only import leaves
  reports untouched, and a report-only import leaves walks untouched. Replacement is by name within each family.
- **Deleting.** A delete is recoverable through a **machine-local** `deletedWalk.*` bin, like `deletedReport.*`. The
  bin is never exported or imported, and it stays machine-local while a project is active.
- **Keys.** The live and deleted families are registered in `KnownKeys` at their scopes (the profile and the machine
  config). A save removes obsolete indexed entries. Unknown keys inside a walk are preserved.
- **Names.** Walk names follow chart-name rules (`ChartNames`), so that a name is also a spotlight address.

### 3.2 Attribution (P1; review R4)

- Every walk records `author`, `createdAt` and `updatedAt`.
- `author` is **declared, never authenticated**. It is one of:
  - `assistant`, for a walk made through the verb;
  - `person`, for one made with the right-click menu;
  - the text an imported walk carried, preserved as received.
- The strip shows "by the assistant", "by a person" or "by \<declared name\> (declared)". **It never shows "you"**: a
  walk shared from another machine cannot claim the reader wrote it.

### 3.3 The step's view: a fixed allow-list (review R2)

| field | meaning | when missing from a stored step |
|---|---|---|
| `tab` | one of the spotlight vocabulary's tabs | the tab is left as it is |
| `filter.from`, `filter.to` | the time window (epoch ms), or none | none: the whole log |
| `filter.groupMode` | `DIMENSION` or `RAW_EVENT` | `DIMENSION` |
| `filter.dimensions` | the dimension set, or all | all |
| `filter.text` | text search | empty |
| `record` | a record index to select | no selection change |
| `graph` | the name of a chart to select | no chart change |
| `focus.name`, `focus.digest` | a named topology focus, and its definition's digest (§3.5) | no focus change |

Rules for the table:
- **A filter is always complete.** A stored filter states all five fields. A field missing from an older step takes
  the default in the table, never "whatever is selected now".
- **Order.** The filter is applied in one change, grouping before dimensions (`FilterState.setAll`, §6). Then the tab
  is selected, then the record, the chart and the focus.
- **Excluded.** Nothing else may appear in a step: in particular `open`, a log or project switch, `report`,
  `screenshot`, `source_root`, a delete, a rename, and **a chart's window or pin**. v1 leaves a chart's window as it
  is; a right-click capture names it as not saved.
- **Refusal.** An unknown field is refused at save, naming it (W-A7).

**A chart step selects; it never opens or edits.**
- Selecting an open chart is transient: `GraphTabs.selectGraph` persists nothing.
- A **closed** saved chart is not opened by the walk, because that path persists its open state. The strip instead
  says *"chart 'X' is closed"* and offers **Open chart**. That is the person's own act, persisted as it is from the
  Project panel. Once the chart is open, the step re-prepares.

### 3.4 Showing a step: validate, apply, prepare, resolve, light (reviews R2, R3, R8)

1. **Validate the whole view** before applying any of it. A refused view leaves the previous step on screen, and the
   strip says why.
2. **Apply the view** under a **presentation ticket**: a counter that marks this walk's own changes. The spotlight
   dismissal that normally follows a view-changing action does not end the walk for its own ticket. A view change
   from anywhere else (a verb, a click) still ends it (O-2).
3. **Prepare.** Extraction, filter refresh and painting are asynchronous. The step waits for them through a
   non-blocking Swing timer, bounded at 5 s and cancellable:
   - a chart's extraction must read `complete` in `GraphPanel.scopeFacts()`;
   - its paint must match the current size and data (§3.6).

   A completion from a superseded ticket, or a different log generation, is discarded. It cannot light a target,
   advance the step, or overwrite the strip's reason. No step waits on `loadInFlight`, because no step opens a log.
4. **Resolve each target** against the view now on screen. Each gets a **state** (§3.5).
5. **Light the available targets** through the existing spotlight path, `applySpotlight`, numbered in step order. An
   unavailable target keeps its number, is listed in the strip with its reason, and is not lit.

The strip states **preparing**, **shown**, **partly shown (n of m)** or **not shown**, with the reasons. It never
claims that a previous screen depicts the requested step.

### 3.5 Identity: every state names its basis (P1; reviews R4, R5, R6)

| target | its basis at save | current when | historical when | unresolved when |
|---|---|---|---|---|
| `records:row:N`, `detail:node:…` | a **record digest**: SHA-256 over the UTF-8 bytes of the exact `LogStore.rawText(N)` string, untrimmed and unframed; the store's kind recorded as the representation basis | the digest matches | the digest differs: the target is shown **not available**, never re-pointed | the index is out of range, the digest is absent, or the representation basis differs |
| `graph:<name>…` | a **run basis** (the loaded files' SHA-256s from the read identity) plus a **chart-definition digest** | both match, **and** the chart drew (§3.6) | a basis is known and differs | a basis is unknown (a file changed during its read; a chart with external series); or the chart is missing |
| `topology:node:<id>` | a **graph digest** of the current graph (R5, §6) | it matches, and the node exists | it differs, and the node exists | the digest is unknown (for example a source-supplied graph); or the node is missing or ambiguous |
| a step's `focus` | the name, plus a **focus-definition digest** | the name resolves to that definition | — | the focus is missing, or its name now carries a different definition (**no silent repair**). There is no rename-following in v1, because no focus-rename operation exists. |

- **A step's summary is never better than its worst required target.** One historical target makes the step
  partly historical, and the strip says which.
- **A target that describes the SELECTION is available only if the selection is what the step asked for** (review
  R5, extended by its fix review). That is the detail pane, its node blocks, and the **topology canvas**, which
  carries a step cursor bound to a record and kept in step with the table. `records:row:<n>` names its own index and
  is already refused by its bounds when the filter hides it; `topology:node:<id>` names a node that is there
  whatever is selected; `topology:verdict` states the pairing, which is not record-scoped.
- **`LogFingerprint`** (count and first/last times) stays a mismatch *warning* shown beside the walk. It is never a
  basis for "current".
- **Capture is coherent.** Digests and the view are read in one EDT turn, against one store and one log generation.
  If the generation changes before the save completes, the save is refused, and the person or agent is told.
- **A verdict the session has not formed is not a clean bill of health** (PR57 fix review). A record digest binds one
  record's text under one store representation; it says nothing about the file on disk, and neither does the run
  basis, whose file digests are taken when the log opens and whose record count does not move for an in-place
  rewrite of the same length. The session's file-identity verdict is the only thing that sees such a rewrite, and an
  unassessed log — Follow has not polled, or this reader cannot say — has no verdict. A step that rests on a record
  or chart basis therefore states, once, that *current* here means unchanged since the step was saved, not unchanged
  on disk. It is a caveat, not a refusal: refusing would stop every walk on a reader that cannot report.

### 3.6 A chart drew only if its paint says so (review R3)

`ChartPanel` records a **paint outcome** on every paint: the size painted, a data stamp, the empty-plot message and the
empty reason. A chart target is *drawn* only when all of these hold:
- the outcome's size equals the chart's current size;
- its data stamp equals the chart's current data stamp;
- there is no empty message and no empty reason;
- the chart is showing.

A null message alone proves nothing (R3). The reason shown for an undrawn chart comes from the same outcome:
- *no room at W×H px, widen the window* — only for insufficient room;
- *no data under this step's filter*;
- *no samples in this window*;
- *the chart did not finish drawing in 5 s*.

There is no second plot-size formula. When #56 lands, its layout-override owner is the one place the walk asks for
room.

### 3.7 Controls on the overlay (owner; review R7)

- **The strip:** `◀ Back · <title> — step 2 of 5 · <state> · Next ▶ · ✕`, drawn on the overlay's glass pane.
- **Input is classified before the overlay's dismissal:**
  1. A press on a strip control runs that control.
  2. A **popup trigger** (right-click; the platform's trigger is checked on press and on release, and acts once)
     opens the save menu. It does not dismiss.
  3. While the save menu is open, its own dismissal takes priority, and the spotlight stays.
  4. Any other press ends the walk (O-2) and dismisses, as it does today.

  The existing `onPressed` hook (M64.11) runs after dismissal, so this classification is added ahead of it. Its
  menu-item behaviour is unchanged.
- **Keyboard.** When a walk shows, the strip takes keyboard focus and remembers the previous focus owner. ← and →
  move; Esc ends the walk. Focus is restored when the walk ends. The strip's bindings are on the focused strip, so a
  focused table or combo cannot consume the arrows first.
- **The save menu:**
  - *Save as new walk…* asks for a name.
  - *Add to walk ▸ \<name\>* appends a step.
  - *Replace this step* appears while a walk is showing.

  A saved step is what is on screen: the lit targets and captions, plus the current view through §3.3's allow-list,
  with digests computed then (§3.5). Anything outside the allow-list is named as not saved, starting with a chart's
  window. The menu is not modal.

### 3.8 Playback lives in the session processor (owner, superseding O-3; CLAUDE.md rule 9; review R8)

All of a walk's playback state, its transitions and the decisions about them are in one generated node,
**`walkPlayback`**. That covers which walk is showing, which step, the phase and the ticket. Nothing else holds or
decides them.

- **Facts in** (posted by adapters and input; each names the ticket or generation it is about):
  - `WalkPlayRequested(walk, step, origin)`: carries the saved walk's definition, read from config by the adapter;
  - `WalkNavigated(delta)`: from ◀ ▶ or ← →;
  - `WalkEndRequested(reason)`: Esc, ✕, an outside press, or an external view change;
  - `WalkViewApplied(ticket, ok, reason)`;
  - `WalkStepPrepared(ticket, generation, targetStates)`.
- **Its parents:** `openLog` (generation, open state and identity) and the operation gate.
- **Decisions, all in the node:**
  - **Which step next.** Out-of-range navigation is refused.
  - **When to request the view.** `ApplyWalkViewEffect(ticket, generation, step)`.
  - **When to light.** `LightWalkTargetsEffect(ticket, targets, states)`. Only for the current ticket and the current
    generation; a stale fact is refused and logged, like `LogEvidence`'s generation check.
  - **When to end.** Any `WalkEndRequested`, a new generation, or no log open ends the walk, requesting
    `EndWalkEffect`. The last step shown is remembered for *Play from step N*.
  - **When to re-resolve.** A changed published log identity requests `ResolveWalkTargetsEffect(ticket)` with the new
    verdict. Observational targets become unresolved.
- **Adapters only perform.** The frame applies the view through the transient primitives (§3.3), waits for readiness
  on a non-blocking timer (§3.4), resolves targets against the view on screen, lights them, and posts each result as a
  fact. It never decides what the walk does next.
- **Surfaces only render.** The strip and the Reports tab render `SessionSnapshot.walkPlayback()`: the walk, step,
  count, phase, states and reasons. `context.walks` reads the same snapshot.
- **The audit record** of the session now includes walk transitions, so "why did the walk end?" is answerable from the
  session audit log.

### 3.9 The `walk` verb (O-1; review R9)

| call | effect |
|---|---|
| `walk {name, title?, steps: [...]}` | create, or replace by name. Digests are computed by the analyser from the open log **at save**; a step with observational targets needs a log open. Structural-only walks save without one. |
| `walk {name, delete: true}` | move to the bin |
| `walk {name, rename: "…"}` | rename; its steps are kept |
| `walk {restore: "…"}` · `walk {restore: true}` | restore from the bin · list the bin |
| `walk {name, play: true, step?}` | present the walk to the person, from `step` (1-based) |
| `walk {end: true}` | end a showing walk |

- **One operation per call.** Incompatible fields (for example `steps` with `delete`) are refused before anything
  changes.
- **Steps.** Each is `{caption?, view: {…§3.3…}, targets: [{target, caption?}]}`, with at most six targets. Unknown
  fields are refused.
- **`context`** gains `walks`: each walk's name, title, author, step count and warning, plus the showing walk and its
  step's target states. The strip and `context` report the same states.
- **Contract surfaces updated together:**
  - `VerbSchemas`;
  - `ActionDispatcher` routing, and its read-identity policy per operation;
  - `ActionExecutor`;
  - the in-process and REST prompt manifests;
  - `ContextSections`;
  - Project-panel facts;
  - assistant docs and skills.

  The explicit verb counts in `CloseVerbTest` and `ProjectVerbTest` go to 17.

### 3.10 Where walks appear

- **The Reports tab:** a *Walks* list under the reports, with **Play**, **Play from step N**, **Rename**, **Delete**
  and **Restore deleted…**.
- **The Project panel:** a walk count beside reports.
- **`context.walks`** (§3.9).

## 4. Acceptance

Each check has a wrong-result witness and a registered mutation control (rule 8). Frame checks use real mouse and key
events, with an asserted focus owner, and are registered in both CI frame lists.

| id | check |
|---|---|
| W-A1 | Storage round trip. A walk saved through the verb round-trips through the profile and survives restart. It works under a project A → B → close sequence, and a machine save while A is active. It is listed in the Reports tab and in `context`. |
| W-A2 | Next and Back step 1 → 2 → 1, each restoring its view and lighting exactly its targets. ← and → do the same with a pre-focused combo. Rapid Next → Back lands on the last requested step. |
| W-A3 | Strip presses don't dismiss. An outside press ends the walk, and *Play from step N* resumes there. The walk's own view changes don't end it; an external successful view action does. |
| W-A4 | A dirty start (another filter in the opposite grouping mode, a dimension and text filter, another tab and record) does not leak into step 1. Saved definitions and both settings files are byte-identical before and after playback, once pending saves settle. A control that plays through the persisting graph path fails. |
| W-A5 | Played against a different log, a record target with a mismatched digest is shown not available, keeps its number, and is not re-pointed. |
| W-A6 | Chart readiness. A chart step waits for delayed extraction. At 900×620 it reports *no room*. After a resize that follows a good paint, it does not light before the repaint. An unpainted chart, and an empty window, give their own reasons. A null-message-only control fails. |
| W-A7 | A step naming a field outside §3.3 is refused at save, naming the field; so are incompatible verb fields. |
| W-A8 | Ending. A new log generation ends a showing walk; so does a close. A same-generation identity degradation re-resolves targets to unresolved. A late completion after a log switch lights nothing. |
| W-A9 | Delete moves a walk to the bin, and Restore brings it back. Rename keeps its steps. Deleting down to an empty bin works. The bin never appears in an export. |
| W-A10 | The verb contract: schema, dispatcher, manifests, MCP tool list, context projection and the verb-count tests all agree. |
| W-A11 | Docs: a user-guide section, the assistant verb reference and a CHANGELOG line; a capture shows a walk on the demo fixture. |
| W-A12 | A right-click on a live spotlight opens the save menu and keeps the spotlight. *Save as new walk* saves exactly the lit targets, captions and allow-listed view, and names the chart window as not saved. Replaying lights the same targets. Esc closes the menu and keeps the spotlight. |
| W-A13 | Same graph, new run: structural targets are current, and observational ones historical or not available, with reasons. |
| W-A14 | File graph A → clear → source graph B: A's digest cannot qualify B, so B's structural targets are unresolved. A changed graph with the same names is historical. |
| W-A15 | Renamed, auto-renamed and ambiguous nodes are unresolved with reasons; the rest keep their numbers. A focus deleted and recreated under the same name is unresolved, not repaired. |
| W-A16 | Two logs with the same record count and first/last times, differing in a middle record. The record digest catches it for a record target; the run basis catches it for a chart-only step. |
| W-A17 | Walk-only export and import leaves reports untouched, keeps the declared author, and never shows "you". The human UI and `context` report identical states. |

## 5. Predictions — committed before code

The predictions live in [`docs/handoff/evidence/m69-spotlight-walks-2026-09-27/PREDICTIONS.md`](../handoff/evidence/m69-spotlight-walks-2026-09-27/PREDICTIONS.md),
and are scored in `RESULTS.md` beside it.

## 6. Plan, re-planned against R1–R9

| slice | what | acceptance |
|---|---|---|
| **S0** | **R5 fix.** The topology panel's graph digest belongs to the current graph: cleared on `clearGraph` and `loadFromSource`, and unknown for a source-supplied graph. It is used by session recovery today, where a path check happens to guard it. | a transition regression test; W-A14's first half |
| **S1** | `WalkSpec` model and validation (§3.3); identity helpers (§3.5); storage in every path in §3.1 | W-A1 (headless), W-A7, W-A9, W-A16 (digests), W-A17 (storage half) |
| **S2** | `FilterState.setAll`, a single change; `ChartPanel`'s paint outcome (§3.6); **the `walkPlayback` session node**, its facts and effects, regenerated with `-Pregen`; the adapter's effect performers (apply, prepare, resolve, light, end); the snapshot field | W-A4, W-A5, W-A6, W-A8, W-A13 to W-A15; node-level tests driving facts headless |
| **S3** | the overlay strip; input classification before dismissal; keyboard focus; the right-click save menu | W-A2, W-A3, W-A12 |
| **S4** | the `walk` verb and its contract; `context`; the Reports tab and Project panel; docs, CHANGELOG, a capture | W-A10, W-A11, W-A17 (UI half) |

Each slice lands with its tests and mutation controls before the next begins. The tracker's M69 items follow the
slices.

## 7. Not in v1, and why

- **A chart's window or pin in a step.** Restoring it transiently needs a non-persisting window override in
  `GraphPanel`, and the normal path persists (R2). The capture names it as not saved.
- **Opening a closed chart from a step.** It would persist the open state (R2). The step offers **Open chart** to the
  person instead.
- **Rename-following for focuses.** No focus-rename operation exists to follow (R6).
- **Making room for an undrawn chart.** That is #56's `expand` and `view`; the walk reports *no room* until then.
- **Authenticated authorship.** Declared only (§3.2).

## 8. Relationship to other work

- **M64 (`spec-spotlight.md`)**: D-SP4, "transient by construction", is superseded **for walks only**. A live
  spotlight stays transient, and the overlay stays dumb. A walk re-creates its spotlights from saved steps.
- **#56**: its drawn fact and layout owner. M69's paint outcome (§3.6) is the drawn fact #56 needs to publish; the two
  must not diverge.
- **The evidence-bundle proposal** (D-0b, L-31): a bundle carries walks because it carries the profile.
- **The view-model spike (#55)**: a walk step's view is a natural future view-model input. There is no dependency.

## 9. Revision history

| rev | date | by | what changed |
|---|---|---|---|
| r1 | 2026-09-27 | Claude (analyser session) | First draft from the owner's direction: overlay arrows, stored like reports, standalone. |
| r2 | 2026-09-27 | the same | Right-click save (owner). Feature request 40 / P1 absorbed. O-1 to O-3 answered by the owner. |
| r3 | 2026-09-27 | Codex | [Source review](../handoff/review_spec_m69_spotlight_walks_2026_09_27_codex.md), R1–R9. Storage needs every tier and share path (R1). View verbs persist, and grouping was missing (R2). A completed action is not a drawn chart (R3). A record digest does not bind a chart; attribution is never "you" (R4). The graph digest can belong to the previous graph (R5). Focus names do not bind definitions (R6). The press hook runs after dismissal; focus needs a policy (R7). Generation alone is not the whole lifecycle (R8). The verb inventory was incomplete (R9). |
| r4a | 2026-09-27 | the owner, recorded by Claude | Before any code: playback moves into the session processor (§3.8), superseding O-3. |
| r4 | 2026-09-27 | Claude (analyser session) | The owner accepted r3. This revision consolidates it into one contract, with these v1 choices made explicit: charts selected, never opened or edited, with an *Open chart* affordance; the chart window out of v1; the run basis from the read identity's file digests; a paint outcome as the drawn fact; a presentation ticket for own-step changes; the R5 fix as slice S0. It re-plans the work as S0–S4. |
| r5 | 2026-09-28 | Claude (analyser session) and the implementation reviewers | Implemented as S0–S4, then two review rounds on the implementation. PR57 R1–R9 are all fixed: identity constrains playback, no rebinding on another run, numbering, hidden records, a frozen definition with definition-change facts, correlated play answers, filter defaults, range checks, and record representation (R4). The fix review closed the bulk-path reports (F1, including a stale-baseline follow-up), topology as selection-dependent (F4), the unassessed-log caveat (F5) and restore reports (F6), and withdrew F3. §3.5's unassessed-log paragraph was added by the fix review and ships with 1.26.0. W-A4 is shown in bytes; W-A11 (native capture) remains open. |
