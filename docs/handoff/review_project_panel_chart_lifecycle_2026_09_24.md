# Review: Project-panel navigation, chart lifecycle and the restore proposal

> **Status added when this was landed on `main`, 2026-09-24 — read this first.**
>
> This review is **pinned to `1e51545d`** and describes `main` as it was then. Most of what it reports has
> since been fixed, so a reader coming to it cold will see live defects that are no longer live. The
> review's findings are left exactly as written — this header is the only addition.
>
> | | Finding | Disposition |
> |---|---|---|
> | R1 | Deleting a chart destroys a different closed one | fixed, `f6e8d7e0` |
> | R2 | Auto-named new chart overwrites a closed definition | fixed, `f6e8d7e0` |
> | R3 | Rename leaves a permanent ghost | fixed, `f6e8d7e0` |
> | R4 | Two tabs sharing a name lose one | fixed, `f6e8d7e0` (rename now refuses a taken name) |
> | R5 | The data-loss fix has no test coverage | fixed, `f6e8d7e0` + `f1693c93` — the merge moved to `SavedGraphMerge` and the delete path to `deleteConfirmed` so tests can reach them; mutation-checked |
> | R6 | Style dropdown does not request a save | fixed, `90746e83` |
> | R7 | Settings import drops `style` and `open` | fixed, `90746e83` (`GraphSpec.withExternal`) |
> | R8 | The invented dead Open button reached four places | fixed, `1247aab4` — withdrawn in the spec, the CHANGELOG and the investigation |
> | R9 | Open alters durable state, unreliably; adapter untested | **partly fixed**, `f6e8d7e0` — `openSaved` now fires a change; the `MainFrame` adapter is still untested |
> | R10 | Cross-spec contradiction unresolved | resolved, this commit — `spec-project-starter-journey.md` records the supersession |
> | R11 | The restore proposal's central claim is false | fixed, `1247aab4` — proposal rewritten, its rename recommendation withdrawn |
> | R12 | "Existing profiles are untouched" is contradicted | fixed, this commit — CHANGELOG now states the first-save behaviour |
>
> **Still open:** R9's untested `MainFrame` adapter, and every display-dependent check this review could
> not run — notably that **Cancel on the Delete dialog changes nothing**, which remains unverified by
> anything. The review's own "Limits" section below states those constraints; they still apply.

Independent review of `35eeb320`, `43ce82fc`, `a27b4d13`, `38ecc7f3` and `1e51545d`, pinned to
**1e51545d** (verified identical to `origin/main` at fetch time). `fbe92074` is out of scope; its diff
is two README lines, as the prompt states. Reviewed in a disposable worktree; no application source,
specification, tracker or evidence belonging to the subject was changed, and the worktree was restored
byte-identically (`git status --porcelain` empty, `git rev-parse HEAD` = `1e51545d…`) before this
document was written.

---

## Verdict

**Reject the chart-lifecycle commit (`38ecc7f3`) as it stands, and reject the restore proposal
(`1e51545d`) as a factual document.** The navigation change (`35eeb320`) is sound in intent and its
report half is a real fix, but its history is invented and has now been written into a specification
and into the shipping release notes.

`38ecc7f3` was written to stop a silent, unconfirmed destruction of annotated charts. It replaces that
defect with a narrower but sharper one of the same kind, **and it is completely untested**: the merge
the commit calls "the change that stops the loss" can be reverted to its pre-fix destructive two-liner
and all 1,891 cases still pass (§Evidence, mutation M1). The persisted-close behaviour can likewise be
deleted with no test failing (M2). What the new tests actually protect is the `ConfigStore`
serialisation of the `open` flag — genuinely, and only that (M3).

Three concrete losses reachable from real buttons are in §R1–R4. The worst, **R1**, is a regression
introduced by `38ecc7f3` itself: deleting one chart can silently delete a *different*, closed,
annotated chart that the confirmation dialog never named.

The restore proposal's central factual claim — that the restoration record holds two inputs — is false
by a factor of two on roles and by roughly an order of magnitude on restored state, and the repository's
own committed fixture contradicts it (§R11). Its recommended rename would make the label less accurate
than the one it replaces, and its B1 contradicts a published, owner-accepted specification.

---

## Limits of this review — read before relying on it

**I have no display. Every display-dependent check in the reviewer prompt is NOT VERIFIED by me.** I
did not fake, simulate, or substitute an MCP/socket verb for a button click at any point, and I added
no verb. Specifically, the following remain **NOT VERIFIED** and require a human at a real screen:

| Required display check | Status | What a human must do |
|---|---|---|
| Alternating two report rows with distinct titles/names | **NOT VERIFIED by me** | Covered by the prior review's Robot-click probe (checks 1–2, passed). Re-run only if `showReport` changes. |
| Reopening a closed chart from its Project row | **NOT VERIFIED by me** | Click the row's real Open; confirm series, exprs, right axis, style, pin, notes and explanation appear; then inspect `graph.N.*` bytes. |
| Preserving edits on an already-open chart | **NOT VERIFIED by me** | Prior review check 4 passed at `43ce82fc`; re-confirm at this head. |
| The actual style dropdown, then project close/reopen | **NOT VERIFIED by me** | Reproduces R6. Pick Line from the combo, change nothing else, close and reopen the project and log. |
| Close / Delete confirmation and cancellation | **NOT VERIFIED by me** | `deleteCurrent` raises a `JOptionPane`, which cannot run headless. Cancel must change nothing; confirm must remove only the named chart — see R1, which I expect to fail this check. |
| R1 end to end through the real Delete button | **NOT VERIFIED at the button** | Proved in the code path below the dialog (§Evidence P5). The dialog itself is unexercised. |
| Visual inspection of screenshots | Not applicable | This review commits no images. |

**All 62 skipped cases in the suite are display-gated `*FrameTest` classes** — `AsyncOpenInterleaving`,
`PairingDuringLoad`, `PersonAtTheScreen`, `SessionRecovery`, `LoadedFileObservation`, `MenuScreenshot`,
`TemplateCatalogue`, `WestColumnStartsCollapsed`, `DesignSpotlight`, `JavaSourceSpotlight`, `Spotlight`,
`NamedGraphAndMenuSpotlight`. These are precisely the classes that construct a real `MainFrame`. The
author's headless runs therefore skipped the entire category of test that could have covered the
`MainFrame` Navigator adapter and the toolbar buttons this work adds. Both commit messages report "62
skips" without noting that.

I also did not: push (no credentials — the branch is local only), merge, touch `main`, force-push,
release, deploy, use a compilation key, run an LLM client trial, or enter any other session's worktree.

---

## Required corrections

### R1 · **Critical, regression** — deleting one chart silently destroys a different, closed chart

**Sites:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java:430–440` (order
inside `deleteCurrent`), `:151–155` (`addGraph` naming), `:332–344` (`doRestore` resets `counter` and
skips closed charts); `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:4699–4713`
(the merge).

**Input.** A project with two charts: `Graph 1` (open) and `Graph 2` (closed earlier, carrying a line
style, an explanation and two pinned notes). Select the only tab, `Graph 1`. Press **Delete chart**.
The dialog says: *Delete the chart "Graph 1"?* Confirm.

**Wrong result.** `Graph 1` is removed, as asked — and `Graph 2` is *also* destroyed: explanation gone,
both notes gone, style reset to stairs, and it is now marked open. Nothing warned about `Graph 2`; the
dialog named only `Graph 1`. Executable proof: §Evidence P5, final state
`[Graph 2 open=true style=step explanation='' notes=0]`.

**Mechanism** — three separately correct-looking pieces composing into a loss:

1. `doRestore` (`GraphTabs.java:334–338`) sets `counter = 0` and then `continue`s past every closed
   chart, so a **closed definition never reserves its name**. After restoring only `Graph 1`,
   `counter == 1`.
2. `deleteCurrent` (`GraphTabs.java:435`) removes the last tab and calls `addGraph()`, which at
   `:154–155` does `counter++` and names the placeholder `"Graph " + counter` → **`Graph 2`**, colliding
   with the closed definition.
3. `addGraph` then calls `fireChanged()` at `:161`. `restoring` is false here, so this **saves**, and
   the merge at `MainFrame.java:4705–4707` finds `Graph 2` in the live-tab map and takes
   `live.withOpen(true)` — the empty placeholder — in place of the stored annotated definition.

Note the save at step 3 fires *before* `deleteListener.accept(name)` at `GraphTabs.java:439`, despite
the comment on that line claiming the listener runs "BEFORE the change listener persists the list". It
does so for the trailing `fireChanged()`, but not for the one `addGraph()` raises first.

**Blast radius.** Any project where the auto-name sequence can reach a closed chart's name — which is
the default naming scheme, so any project whose charts were never renamed. Unrecoverable without a file
backup, exactly as the defect `38ecc7f3` set out to fix.

**Required.** Make `counter` account for closed definitions (or make names unique across the saved
collection, not merely across open tabs); do not save between the tab removal and the delete-listener;
and make the merge refuse to replace a stored definition with a chart that is not the same chart.

### R2 · **Critical, regression** — a new chart whose auto-name matches a closed definition overwrites it

**Sites:** `MainFrame.java:4705–4707`; `GraphTabs.java:151–155`, `:334–338`.

**Input.** Same profile as R1 (`Graph 1` open, `Graph 2` closed and annotated). Press **New graph**.

**Wrong result.** The new empty tab is named `Graph 2` (§Evidence P3b — this is a real
`GraphTabs.addGraph(null)`, the exact call the toolbar button makes). On the next save the merge
replaces the closed `Graph 2` definition with the empty one (§Evidence P4b: `explanation=''`,
`notes=0`). No confirmation, no warning, no undo.

This is the same class of loss `38ecc7f3` names in its own commit message — "a silent unconfirmed delete
of annotated state" — relocated rather than removed, and it is newly *reachable* because closed
definitions now persist where before they did not exist to be clobbered.

### R3 · **High, regression** — renaming a chart leaves a permanent ghost

**Site:** `MainFrame.java:4704–4709`.

**Input.** One saved chart `Alpha`, open. Rename its tab to `Beta` (`GraphTabs.promptRename` →
`renameAt` → `fireChanged` → save).

**Wrong result.** The profile now holds **two** charts: `Alpha`, marked closed and holding the stale
pre-rename definition, and `Beta` (§Evidence P4a: `[Alpha:false, Beta:true]`). The Project panel lists
`Alpha` for ever as a saved chart; its Open reconstructs a second, stale copy. Every rename adds one
ghost. The reviewer prompt names this outcome explicitly ("must not retain the old name as a ghost
chart"); the merge does exactly that, because a name-keyed merge cannot distinguish "renamed away" from
"closed".

**Required.** Carry a stable identity on `GraphSpec` (or pass the rename through the same explicit
channel `deleteCurrent` uses) so the merge can tell a rename from a close.

### R4 · **High** — two tabs sharing a name lose one of them

**Sites:** `MainFrame.java:4701` (`open.put(g.name(), g)` into a `LinkedHashMap`);
`GraphTabs.java:261–267` and `:251–259` (rename accepts a name another tab already holds).

**Input.** Two open charts `Graph 1` and `Graph 2`. Rename `Graph 2` to `Graph 1` — accepted, no
uniqueness check (§Evidence P3a: `specs()` returns `[Graph 1, Graph 1]`).

**Wrong result.** The merge's map keeps only the last entry, so the **first** tab's live content is
discarded from the profile, and the renamed-away `Graph 2` definition is retained as a stale closed
ghost (§Evidence P4c: `[Graph 1:true:beta, Graph 2:false:beta]`). Two losses from one keystroke.

The same collapse occurs if the profile itself holds duplicate names (hand-edited, or imported): both
stored definitions are replaced by the one live tab (§Evidence P4d).

### R5 · **High** — the data-loss fix has no test coverage at all

**Sites:** `MainFrame.java:4696–4714`; `GraphTabs.java:336–338`;
`src/test/java/telamin/fluxtion/audit/analyser/analyser/ui/ClosingAChartKeepsItsDefinitionTest.java`.

Two mutations, each run against the **full** suite on JDK 21:

- **M1** — replace the whole merge with the pre-fix `config.savedGraphs.clear();
  config.savedGraphs.addAll(graphTabs.specs());` → **1891 run, 0 failures, 0 errors, 62 skipped, BUILD
  SUCCESS.**
- **M2** — delete `if (!g.open()) continue;` from `doRestore` → **1891 run, 0 failures, 0 errors, 62
  skipped, BUILD SUCCESS.**

So both behaviours the commit message presents as the fix ("syncOpenGraphsIntoConfig MERGES … This is
the change that stops the loss" and "doRestore skips closed charts, so a close survives a reload") are
entirely unprotected. The commit's own claim that the tests "pin the merge and the round trip" is
false for the merge half: `ClosingAChartKeepsItsDefinitionTest` never constructs a `MainFrame`, never
calls `syncOpenGraphsIntoConfig`, and never uses `GraphTabs`. It is a `ConfigStore` round-trip test.

For contrast, **M3** — delete `if (!g.open()) p.setProperty(…)` from `ConfigStore.java:449` → 2 named
failures in that class (`…:52` and `…:87`). The serialisation *is* protected. Nothing else is.

**Required.** A regression that drives `syncOpenGraphsIntoConfig` (or an extracted, testable merge) with
at least: close, rename, a colliding new tab, duplicate names, and delete-the-last-tab. Show it red
before green.

### R6 · **Medium, pre-existing defect left unfixed** — the style dropdown still does not request a save

Confirms the prior review's **F1**, independently and at this head.

**Sites:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphPanel.java:176–180` (the
combo listener calls `chart.setStyle(...)` and nothing else) vs `:981–990` (`setStyleByName` calls
`mutated()` at `:989`).

**Measured** (§Evidence P1, using the real `styleCombo` located in the live component tree of a real
`GraphPanel` inside a real `GraphTabs`):

```
edits after HUMAN dropdown        = 0
edits after VERB setStyleByName   = 1
```

**Input → wrong result.** Open a chart, pick **Line** from the dropdown, change nothing else, quit.
Reopen: the chart is stairs. The CHANGELOG entry "A chart's plot style is saved with it … survive a
reload" describes behaviour the only user-facing control does not have.

**A second case the prior review did not cover, and which the obvious partial fix would not solve.**
Pick Line from the dropdown, then **Close** that chart. `closeCurrent` (`GraphTabs.java:404–411`) does
fire a save, but the tab is already removed, so the merge takes the `existing.withOpen(false)` branch at
`MainFrame.java:4707` — the *stored* spec, with the *old* style. The dropdown change is lost even though
a save occurred.

`GraphStylePersistenceTest.styleIsAPersistableMutation` (`:82–91`) asserts exactly the property that is
missing, on the one path where it holds. Mutation **M4** (remove `mutated()` from `setStyleByName`)
fails it, confirming it protects the verb path only.

### R7 · **Medium, pre-existing shape, newly widened** — settings import drops both `style` and `open`

Confirms the prior review's **F2**, and extends it: `38ecc7f3` added a second component to the same
dropped set without revisiting the call site.

**Site:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SettingsShare.java:350–352` —
the 13-argument `GraphSpec` constructor. That overload (`GraphSpec.java:37–43`) fills `style = null` and
`open = true`.

**Measured** (§Evidence P2, through the real `SettingsShare.export` / `preview`):

| Chart | Written | Read back |
|---|---|---|
| Plain (control) | `style=line`, `open=false` | `style=line`, `open=false` ✅ |
| With an external CSV series | `style=line`, `open=false` | `style=step`, `open=**true**` ❌ |

The export side is correct — `.style=` and `.open=` are both present in the shared text. The loss is
purely in the path-rewrite reconstruction, which is guarded by
`if (spec.external().isEmpty() && !extMarkers) continue;` at `:323`. **Blast radius:** charts carrying
an external CSV series or an external marker source, on every settings import and every share preview.
For those charts an import now *revives a chart the user deliberately closed*, which is a new symptom
`35eeb320` alone did not have.

**Root cause worth naming.** `GraphSpec`'s back-compatibility constructors (`:28–34`, `:37–43`) let every
existing call site keep compiling while silently defaulting new components. I swept all four
construction sites in `src/main/java`: `GraphTabs.java:285` (14-arg — correct, `specs()` walks open tabs
so `open=true` is right), `ConfigStore.java:603` (15-arg — correct), `GraphSpec.java:50` (`withOpen` —
correct), `SettingsShare.java:350` (13-arg — **the defect**). I found no fifth. `ProjectProfile`,
`SessionFacts`, `ActionExecutor`, `MarkerExtractor`, `ReportSpec`, `ChartPanel` and `GraphPanel` either
pass `GraphSpec` through unchanged or construct only its nested records. The class of defect is
nonetheless live: the next component added will be dropped here again unless the overloads are removed
or the call site is changed to a `with…` copy.

### R8 · **High, documentation integrity** — the invented dead Open button is now in four places, including a specification and the shipping release notes

Confirms the prior review's **F3**. I re-verified it independently from the parent revision rather than
taking the prior review's word.

**Evidence at `35eeb320^`:**
- `ProjectModel.java:334–337` (that revision) builds saved-chart rows with **`path = null`** and
  **`Target.NONE`**.
- `ProjectPanel.java:142–154` (that revision) adds `Copy`/`Show file` only `if (r.path() != null)`, and
  its `switch` reaches **`default -> { }`** for `NONE`.
- `ProjectPanel.java:155` then attaches the actions strip only `if (actions.getComponentCount() > 0)`.

Therefore a saved-chart row rendered **zero buttons**. There was no Open. The earlier reading of
`Target.NONE` as a deliberate D-L3 boundary was **correct on the facts**; the author's rebuttal of it
rests on a control that did not exist.

**Where the false claim now lives:**

1. `35eeb320` commit message — *"the rows still rendered an Open button, so it was a dead control rather
   than a boundary"*. Immutable on `main`.
2. `docs/investigations/profile-project-root-resolution.md:130–131` — *"The rows still rendered an Open
   button; it was simply wired to nothing, which is not a boundary, it is a dead control."*
3. **`docs/specs/completed/spec-loaded-panel.md:88–90`** — *"saved-chart rows carried no target at all,
   so the Open a person could see was wired to nothing. Both read as broken software…"* This is the
   most damaging copy: a specification amendment now records a fabricated history as its own reason.
4. **`CHANGELOG.md`** (Unreleased) — *"Open on a saved chart works at all — those rows previously
   carried no action, so the button did nothing."* This is user-facing and ships.

**Does the D-L3 amendment's justification still stand on corrected facts?** Partly, and the distinction
matters. The amendment argues from two cases joined by *"Both read as broken software"*:

- **The report case is real.** A report row displays its title while `ReportsPanel.select` matches on
  name, so Open on any report revealed whichever report was already selected. That is a genuine
  trust-breaking visible defect and, on its own, a sufficient reason to let `Navigator` carry identity.
- **The chart case is fabricated.** Nothing read as broken, because nothing was rendered. It read as a
  boundary — the boundary D-L3 states.

So the amendment reaches a conclusion the owner is entitled to reach, but it reaches it through an
argument that is half false, and the false half is the half that covers the change actually made to
charts. **The owner's decision to allow item-specific reveal is not in question here; the author's
argument for it is, and it does not survive.** Charts need to be justified on their own ground — "a
saved definition the profile already holds may be shown" — which the amendment's second paragraph does
say, and which stands without the invented button.

**Required.** Correct sites 2, 3 and 4. Site 1 is immutable; note the correction where the history is
read. Do not present the change as repairing a control the predecessor did not render.

### R9 · **Medium, D-L3 boundary** — Project-panel Open *does* alter durable state, unreliably, and the adapter is untested

**Sites:** `MainFrame.java:366–374` (the `showGraph` adapter), `GraphTabs.java:312–330` (`openSaved`),
`MainFrame.java:4705–4707` (the merge).

Two findings, in tension with each other, both created by `38ecc7f3` landing on top of `35eeb320`:

**(i) It persists, which the amendment says it must not.** Before `38ecc7f3`, `open` was not durable, so
opening a saved chart from a Project row changed nothing on disk and the reveal-only reading was clean.
Now, adding the tab puts the chart into `GraphTabs.specs()`, so the *next* save flips the persisted
`graph.N.open` from `false` to `true`. The amendment written one commit earlier states the limit as: *"A
`Navigator` method must reveal something that **already exists**; one that creates, **edits**, discards
or reorders state belongs on the action surface, which this panel still may not reach"*
(`spec-loaded-panel.md:93–95`). Flipping a persisted flag is editing state. `38ecc7f3` crossed the
boundary its predecessor had just drawn, and did not amend the spec.

**(ii) It does not persist reliably, which makes the behaviour non-deterministic to a user.**
`openSaved` sets `restoring = true` across the whole build (`GraphTabs.java:319–329`) and never calls
`fireChanged()`. So the reopen requests no save of its own. Concretely: close chart *B*, reopen *B* from
its Project row, quit → *B* is still closed next session. Close *B*, reopen *B*, then edit an unrelated
chart *A* → *B* is now persisted as open. The persisted state depends on whether some later, unrelated
edit happened. The reviewer prompt asks to "verify reopening persists the intended open state": it does
not, except by accident.

**(iii) The adapter is unprotected.** Mutation **M5** — replace the bodies of `MainFrame`'s `showReport`
and `showGraph` with `{ }` — leaves the full suite at **1891 run, 0 failures, 0 errors, 62 skipped,
BUILD SUCCESS**. `ProjectPanelOpenRevealsTheRowsItemTest` proves the panel *asks* the right thing of a
recording `Navigator`; nothing in the suite proves the frame *does* anything. The prior review covered
this with a real-display Robot probe, which is the right instrument and is not a substitute for a
committed gate. The investigation's line *"The UI-test gap is closed"*
(`profile-project-root-resolution.md`) overstates the reach of the new test, as the prior review also
noted.

### R10 · **Medium, cross-spec contradiction left unresolved**

**Site:** `docs/specs/spec-project-starter-journey.md:283–286`, an active spec (*"Status: owner
requirements recorded 2026-09-20; implementation pending"*), which states:

> The Project panel **states** these facts in a **Saved charts** row and remains reveal-only; **it gains
> no open/restore control or mutation callback**. … Keep `ProjectPanel.Navigator` unchanged.

`35eeb320` gave saved-chart rows an Open control and added two methods to `ProjectPanel.Navigator`. The
starter-journey spec was not amended, so two live specifications now disagree. The D-L3 amendment was
made without sweeping for other governing documents.

**Owner decision** as to which wins; **required** that the contradiction be recorded and resolved rather
than left for a future reader to discover.

### R11 · **High** — the restore proposal's central factual claim is false

`docs/proposals/restore-and-project-reload-semantics.md`. I did not accept the two-role claim on the
strength of the sample fixture, and it does not survive contact with the code.

**The record holds four input roles, not two.** The whitelist is a single enumerated line:
`src/main/java/telamin/fluxtion/audit/analyser/analyser/session/resume/SessionResumeStore.java:18` —
`Set.of("log", "topology", "design", "diagnostics")`. I read it directly. The proposal's claim at
L31–32 (*"`context.restoration.inputs` lists exactly two entries, `role: log` and `role: topology`"*) is
therefore false, and knowably so.

**The repository's own committed fixture contradicts it.** `docs/handoff/evidence/
project-session-recovery-2026-09-20/recovery-offer.json` lists all four roles — I counted them: one each
of `log`, `topology`, `design`, `diagnostics`. The companion `recovery-finished.json` records the applied
outcome in prose: *"Log loaded; topology opened; design opened; diagnostics opened; Record filter
restored … Topology cursor/focus restored"*.

**"Not captured, so never restored" is false for most of its list.** L34–35 names the filter, flags, the
selection and step cursor, the spotlight, the open/selected tab and the posture. Against
`MainFrame.captureSession` (`:6536–6565`), `applyRecovery` (`:6567–6595`), `finishRecoveryInputs`
(`:6639–6704`), `restoreRecoveryView` (`:6707–6748`) and `TopologyPanel.restoreRecoveryView`
(`:1304–1336`), the correct classification is:

- **Captured and applied:** the record filter (`:6548–6553` → `:6724–6732`), flags/findings
  (`:6555–6556` → `:6709–6722`), selected records (`:6557` → `:6733–6736`), the selected **chart** tab
  (`:6559` → `:6737`), topology focus stack, node selection, scope, orientation, zoom/pan and step
  cursor (`TopologyPanel:1288–1298` → `:1306–1334`), plus design and diagnostics files.
- **Captured but withheld**, deliberately and with a user-visible message, when the reloaded log's or
  graph's bytes do not match the captured hashes: `MainFrame:6667–6669`, `:6693–6699`, `:6722`, `:6745`;
  `TopologyPanel:1320`, `:1335`. This is a designed refusal path, not an omission.
- **Genuinely not captured:** the spotlight (deliberate — `TopologyPanel.java:1284`: *"Persist navigation
  only. Commentary, spotlights and findings are deliberately excluded."*), the posture, and the main
  right-hand tab (only the chart sub-tab is captured).
- **Not present in that fixture** rather than unsupported: design and diagnostics, in any session where
  neither was open.
- **Lost by a bug:** none found.

**The proposed label is worse than the current one.** A1 proposes *"Reopen last log and topology"*. That
names 2 of 4 files and 2 of roughly 16 restored things, and it displaces *"Restore last session"*, which
is an owner-accepted spec term (`spec-project-starter-journey.md:277`, `:298`). A2's premise ("add
filter, flags and selection to the restoration record") asks for work that already exists, including the
refusal story A2 says is missing.

**B1's reveal-only claim is wrong, and contradicts a published spec.** What the panel can do *today* is
genuinely reveal-only and cannot load a log: `GraphTabs.openSaved` early-returns at `:313` when
`store == null`, and the bytecode gate in `ProjectPanelIsRevealOnlyTest:41–55` prevents the panel from
naming `MainFrame`, `ActionExecutor` or `AppControl`. But B1 proposes that the panel *"offers the
reopen"* of a log. That needs a fifth `Navigator` method that opens a file; it fails the pinned method
set outright; and it falls outside the amendment's own limit, because a closed log is a file on disk,
not "a definition the profile already holds" — the exact reason the amendment gave for allowing charts
does not transfer. It also contradicts `spec-project-starter-journey.md:283–286` verbatim.

**On which surface may offer what:** a surface may *navigate to* the existing restore decision — the
Project panel already does, via the `"Session recovery: …"` row at `ProjectModel.java:106–108` — while
only the landing may *execute* it, which `StartPanel.renderProject` (`:186–199`) already does with a
literal "Restore last session" button and a Dismiss. The proposal's L47 claim that *"nothing on screen
says so"* is false on both counts.

I did not approve, and this review does not approve, any automatic restoration or any new capture
policy. Those remain owner decisions; the point here is only that the proposal misdescribes what exists.

### R12 · **Low, but user-facing** — "existing profiles are untouched" is contradicted by the author's own record

`CHANGELOG.md` (Unreleased) says *"Charts saved before this release have no stored style and open as
stairs, exactly as they did before"*, and `35eeb320`'s message says *"existing profiles are untouched"*.
The verification record added by `a27b4d13` states the opposite plainly and correctly:
*"the first save after upgrading adds a style key to every chart … 'existing profiles are untouched'
holds only until the first save"* — because `GraphTabs.specs()` (`:285–288`) always reports
`gp.styleName()`, which is never null. The record is right; the release notes and the commit message
are not. Credit where due: the author disclosed this against their own claim.

---

## Optional improvements

1. `ConfigStore.java:605` parses the new flag as
   `!"false".equalsIgnoreCase(String.valueOf(p.getProperty(…)).trim())` while every other boolean in the
   file goes through `ConfigStore.parseBool`. A hand-edited `graph.0.open=0` or `=no` reads as open. Use
   `parseBool`.
2. `ProjectPanelIsRevealOnlyTest`'s class javadoc still says the Navigator has *"two navigation
   methods"* while the test below asserts four. `35eeb320` updated the assertion and not the prose, in
   the one file whose job is to make additions deliberate.
3. `closeCurrent` (`GraphTabs.java:405`) returns early when `tabs.getTabCount() <= 1`. With every other
   chart closed there is no way to put the last one away — the reviewer prompt's "last tab" case is a
   silent no-op rather than a close.
4. `MainFrame.java:3982` defensively copies before `graphTabs.restore(...)` with the comment *"belt to
   GraphTabs' braces"*; `:4720` and `:5262` pass the live `config.savedGraphs` without it. Make the three
   consistent.
5. Remove `GraphSpec`'s back-compatibility constructors (or mark them deprecated) once the call sites
   are migrated to `with…` copies. They are what allowed R7 to compile silently, and they will allow the
   next one.
6. `dependency-reduced-pom.xml` is tracked and is rewritten by `mvn package`, so a packaging run dirties
   a clean checkout. I restored it; a `.gitignore` entry would remove the trap.

## Owner decisions

1. **Does the D-L3 amendment stand on corrected facts?** The report half of its argument is real and
   sufficient; the chart half is invented (R8). The decision to allow item-specific reveal is yours and
   is not challenged — only the written justification needs rebuilding.
2. **May a Project-panel Open flip a chart's persisted `open` flag?** (R9.) Saying yes widens the
   amendment you just wrote; saying no means the reopen must be transient, and the Project panel cannot
   be the way a closed chart is durably reopened.
3. **Which spec governs the panel** — the D-L3 amendment or `spec-project-starter-journey.md:283–286`?
   (R10.)
4. **Should Close be permitted on the last tab**, now that closing is non-destructive? (Optional 3.)
5. **Should the restore proposal be corrected or withdrawn?** (R11.) Its A1/A3 recommendation is
   actively harmful if adopted; B2's conclusion is already an owner decision at
   `spec-project-starter-journey.md:298–303`; B3 duplicates `:341–345`.
6. **Do the Unreleased CHANGELOG entries get corrected before the release cut?** Two of them are
   currently inaccurate (R8 site 4, R12).

---

## Per-item dispositions

### The prior review (`review/project-panel-open-style-2026-09-24`, at `43ce82fc`)

| Prior finding | Disposition at `1e51545d` |
|---|---|
| **F1** — UI dropdown loses the style | **Still holds.** Reproduced independently and headlessly (§P1): human 0 notifications, verb 1. **Extended:** the style is also lost when a save *does* occur, if the chart is closed after the dropdown change (R6). |
| **F2** — external-data charts lose style on import | **Still holds, and is now worse.** Reproduced through the real `SettingsShare` (§P2). `38ecc7f3` added `open` to the same dropped set, so an import now also revives a deliberately closed chart (R7). |
| **F3** — the invented dead Open button | **Still holds.** Re-verified independently from `35eeb320^` source rather than from the prior review (R8). **Propagation is wider than F3 records:** it is also in the shipping CHANGELOG. |
| D-L3 judgement (accepts opening a saved chart as reveal) | **Was correct at `43ce82fc`; needs revisiting at this head.** `38ecc7f3` made `open` durable, so the reveal now has a persisted side effect the prior review could not have seen (R9(i)). |
| "Method-set and bytecode tests do not prove the adapter's behaviour" | **Confirmed mechanically.** Mutation M5 guts both adapter methods; the suite stays green (R9(iii)). |
| Wording suggestion: replace "nothing … mutates state" | **Endorsed, and now necessary rather than optional**, given R9(i). |
| Topology-verb caution; "UI-test gap is closed" overstates | **Endorsed.** |
| Skips are not passes (1,825 executed at their head) | **Endorsed and repeated.** 1,829 executed here. |

### The Close/Delete change (`38ecc7f3`)

| Aspect | Disposition |
|---|---|
| Diagnosis of the original loss | **Correct**, and well evidenced in the profile bytes. |
| `GraphSpec.open` + `withOpen` | **Accept.** Defaults are right; `withOpen` is a clean copy and is tested (`withOpenChangesNothingElseAboutTheChart`). |
| `ConfigStore` writes `open=false` only when closed | **Accept.** Genuinely protected — mutation M3 fails two named assertions. |
| `syncOpenGraphsIntoConfig` merge | **Reject.** Name-keyed, so it cannot tell rename from close (R3), collapses duplicates (R4), and lets a new tab overwrite a closed definition (R2). Untested (M1). |
| `doRestore` skips closed charts | **Accept in intent, reject as delivered.** Untested (M2), and resetting `counter` past closed charts is the first link in R1. |
| Delete chart button + confirmation | **Reject as delivered.** The dialog text is good; the ordering around the fallback `addGraph()` destroys an unrelated chart (R1). Cancel/confirm behaviour at the dialog is **NOT VERIFIED** — headless cannot raise a `JOptionPane`. |
| Delete deliberately kept off the Project panel | **Accept**, and correctly reasoned. |
| "Tests pin the merge and the round trip" | **False for the merge.** Only the round trip is pinned. |
| Empty saved collection / all definitions closed | Reachable only via delete-the-last-tab or an import; both route into R1/R2. The placeholder tab that `doRestore:343` and `deleteCurrent:437` create becomes a **new saved definition the user never authored** on the next save. |

### The restore proposal (`1e51545d`)

| Claim | Disposition |
|---|---|
| "exactly two entries, `role: log` and `role: topology`" | **False.** Four roles enumerated at `SessionResumeStore.java:18`; the committed fixture shows four (R11). |
| "Restore last session reopens the log and the topology" | **False.** Also design, diagnostics, filter, flags, selections, topology cursor/focus/zoom, selected chart tab. |
| "Not captured, so never restored: filter, flags, selection and step cursor, spotlight, open/selected tab, posture" | **Mostly false.** Correct only for the spotlight, the posture and the main tab. |
| "nothing on screen says so" | **False.** `StartPanel:186–199` renders the offer with a button; `ProjectModel:106–108` states it in the panel. |
| B1 — a Project-panel reopen offer is reveal-only | **False**, and contradicts `spec-project-starter-journey.md:283–286`. Navigating *to* the decision is reveal-only; *offering the reopen* is not. |
| B2 — do not carry a log across a project boundary | **Correct**, but already an owner decision at `spec-project-starter-journey.md:298–303`; presented as new. |
| B3 — offer it, never take it | **Duplicates** `spec-project-starter-journey.md:341–345`, already specified and built. |
| A1/A3 — rename to "Reopen last log and topology" | **Reject.** Less accurate than the current label, and overrides an owner-accepted spec term. |
| A2 — add filter/flags/selection to the record | **Already built**, including the refusal story A2 says is missing. |
| "close a chart → definition kept, `graph.N.open=false`"; "reopen the log → open ones return, closed stay closed" | **Correct.** |
| Chart definitions are not in the restoration record | **Correct** — they are project-profile state. |
| `GraphTabs.restore` early-returns when `store == null` | **Correct**, including the attributed commit. |
| "verified, not inferred … by reading the profile bytes after each step" | **Overstated.** The recovery snapshot lives in the session store (`MainFrame:6516–6517`), not the project profile; profile bytes cannot observe the restoration row of that table. |

### The verification record (`a27b4d13`)

| Aspect | Disposition |
|---|---|
| Checks 1–4, at a real display | **Accepted as reported**; consistent with the prior review's independent Robot-click probe. I could not re-run them. |
| "**Check 5 passes**, driven over the action socket" | **The claim is true of the path it drove and false of the feature it certifies.** The socket path is `setStyleByName`, the one path that calls `mutated()`. The dropdown — the only control a person has — was not exercised, and does not work (R6). A check driven exclusively through the verb cannot certify a UI fix. |
| "the first save after upgrading adds a style key to every chart … benign" | **Correct and creditable**, though it contradicts the CHANGELOG (R12). |
| Diagnosis of the close-deletes-chart defect | **Correct and well evidenced.** |
| "an earlier check drove `analyser_topology`, the **verb**, which … proves nothing about the button" | **Correct**, and the right instinct — which makes it the more striking that check 5 was then certified the same way. |
| The suite line "1,891 tests, 0 failures, 0 errors, 62 skips" | **Accurate as a count, misleading as a result.** 1,829 cases executed; all 62 skips are the display-gated `*FrameTest` classes, i.e. exactly the coverage this work needed. |

---

## Evidence: what I ran

All commands from a disposable worktree pinned to `1e51545d`, `JAVA_HOME=$(/usr/libexec/java_home -v 21)`
(Corretto 21.0.10).

**Baseline.** `mvn -B test` → **Tests run 1891, Failures 0, Errors 0, Skipped 62**, BUILD SUCCESS.
That is **1,829 cases executed and passing**; 62 skipped cases are not passing cases.
`mvn -B -DskipTests package` → BUILD SUCCESS, `target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar`.
`mkdocs build --strict` → exit 0, no warnings. `git diff --check` → clean.

**Mutations.** Each applied to the pinned head and reverted with `git checkout --` immediately after;
`git status --porcelain` was empty before this document was written.

| # | Mutation | Scope run | Result |
|---|---|---|---|
| M1 | `syncOpenGraphsIntoConfig` → pre-fix `clear()` + `addAll(specs())` | full suite | **1891 / 0 / 0 / 62, BUILD SUCCESS** → the fix is unprotected |
| M2 | delete `if (!g.open()) continue;` from `doRestore` | full suite | **1891 / 0 / 0 / 62, BUILD SUCCESS** → unprotected |
| M3 | delete `if (!g.open()) p.setProperty(…)` from `ConfigStore` | `ClosingAChartKeepsItsDefinitionTest` | **2 failures** at `:52` and `:87` → protected (green→red→restored) |
| M4 | delete `mutated()` from `setStyleByName` | `GraphStylePersistenceTest` | **1 failure** (`styleIsAPersistableMutation`) → protects the verb path only |
| M5 | `MainFrame.showReport`/`showGraph` bodies → `{ }` | full suite | **1891 / 0 / 0 / 62, BUILD SUCCESS** → the adapter is unprotected |

**Probes.** Disposable JUnit classes, run on the pinned head and then deleted; they are not committed,
because they are review instruments, not regression gates. Each used real product objects where one
exists.

| # | What it drove | Result |
|---|---|---|
| P1 | Real `GraphPanel` inside a real `GraphTabs`; the actual `styleCombo` located in the live component tree | human dropdown → **0** change notifications; `setStyleByName` → **1** |
| P2 | Real `SettingsShare.export` → `preview` | external-series chart: `style` line→**step**, `open` false→**true**; plain chart unchanged |
| P3a | Real `GraphTabs.renameNamed` | accepted a name another tab holds; `specs()` → `[Graph 1, Graph 1]` |
| P3b | Real `GraphTabs.restore` then `addGraph(null)` | new tab auto-named **`Graph 2`**, colliding with the closed definition |
| P4a–d | The merge, **transcribed verbatim** from `MainFrame.java:4699–4713` | rename → `[Alpha:false, Beta:true]`; collision → `explanation=''`, `notes=0`; duplicates → first tab's content discarded; profile duplicates → both overwritten |
| P5 | Real `GraphTabs` + real `restore`/`addGraph`, with `deleteCurrent`'s own tab removal and ordering, plus the transcribed merge | deleting `Graph 1` left `[Graph 2 open=true style=step explanation='' notes=0]` — the unrelated closed chart destroyed |

**Honest label on P4/P5:** `syncOpenGraphsIntoConfig` is private to `MainFrame`, and `MainFrame` cannot
be constructed headless (the frame tests all `assumeFalse(isHeadless())`). Those probes therefore ran a
**verbatim transcription** of the fourteen lines at `MainFrame.java:4699–4713` rather than the live
method. The transcription is quoted line for line in the finding text so it can be checked against the
source. The `GraphTabs` half of P3 and P5 — the naming, the restore, the tab removal — is the real
product code. This is the one place in this review where the instrument is a copy rather than the
original, and I am flagging it rather than letting the table imply otherwise.

**What I inspected without running.** Root `CLAUDE.md` and `docs/ONBOARDING.md`; the five scoped commits
in full; `spec-loaded-panel.md` D-L3 and its amendment; `spec-project-starter-journey.md` §"Re-entry and
reported missing context"; `profile-project-root-resolution.md` including the new verification record;
`restore-and-project-reload-semantics.md`; the recovery model in `MainFrame`, `TopologyPanel`,
`SessionResumeStore`, `SessionRecovery` and `StartPanel`; the committed recovery fixtures; the prior
review and its README of probes; `35eeb320^` sources for `ProjectModel` and `ProjectPanel`; every
`new GraphSpec(` site in `src/main/java`.

**Public-repository rule one.** Run before committing:
`git ls-files -z | xargs -0 grep -ril <the four sweep terms> | grep -vE '^(CLAUDE\.md|docs/ONBOARDING\.md)$'`
printed nothing. This document names no venue, vendor, book, thread, logger or account, spells none of
the four terms, and contains no absolute path from the reviewing machine. It commits no images, so the
"the sweep cannot see inside images" caveat does not apply.

---

## Summary

| Severity | Findings |
|---|---|
| **Critical (data loss, regression)** | R1 delete-one-destroys-another · R2 new-tab overwrites a closed definition |
| **High** | R3 rename ghosts · R4 duplicate names lose a chart · R5 the fix is untested · R8 fabricated history in a spec and the release notes · R11 the restore proposal is factually wrong |
| **Medium** | R6 the style dropdown · R7 import drops style and open · R9 the D-L3 side effect and the untested adapter · R10 cross-spec contradiction |
| **Low** | R12 CHANGELOG vs the verification record |

The single sentence worth carrying out of this review: **`38ecc7f3` is a fix for silent unconfirmed
chart destruction that itself silently and unconfirmably destroys charts, and no test in the suite would
notice if it were deleted entirely.**
