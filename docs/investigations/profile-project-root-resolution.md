# Bug: a NAMED project profile anchors relative paths one directory too deep

**Status:** confirmed on `main` @ `abb4a697`, **fixed** on `fix/named-profile-project-root` (fix + test).

A project profile saved as `project.<name>.fluxtion-settings` beside the canonical
`project.fluxtion-settings` in the same `.analyser/` directory resolves every relative path against
`.analyser/` instead of the project root. Source roots, runbooks, vocabulary and report paths all land
one directory too deep, so the profile looks broken while the canonical profile beside it — same
values — works.

## Symptom

Swapping between per-concern profiles (`project.ws-feed.fluxtion-settings`,
`project.reciprocal.fluxtion-settings`) via `open {project: <path>}` swaps the investigation layer
cleanly (selected processor, named graphs, focuses, reports). But in the named profile:

- every **event processor** reports `source: "not found"`;
- every **runbook** reports `exists: false` (so AI ▸ runbooks ▸ find skills finds none);
- **source roots** resolve to non-existent directories.

## Evidence — the same project, two files, only the NAME differs

`analyser_context` with each profile active, identical root values in both files.
Project and workspace directory names below are placeholders; the path relationships are preserved:

| | canonical `project.fluxtion-settings` | named `project.ws-feed.fluxtion-settings` |
|---|---|---|
| `project.root` | `…/demo-project` ✅ | `…/demo-project/.analyser` ✗ |
| `workspaceDir` (`workspaceRoot=..`) | `…/demo-workspace` ✅ | `…/demo-project` ✗ |
| `sourceRoot.0=src/main/java` | `…/demo-project/src/main/java` ✅ | `…/demo-project/.analyser/src/main/java` ✗ |
| `sourceRoot.1=../demo-shared-lib/…` | `…/demo-workspace/demo-shared-lib/…` ✅ | `…/demo-project/demo-shared-lib/…` ✗ |
| processors resolved | **6 / 6** ✅ | **0 / 6** ✗ |
| runbooks resolved | **8 / 8** ✅ | **0 / 8** ✗ |

Reproduced 2026-09-22, re-confirmed 2026-09-24 against `main` @ `abb4a697`.

## Root cause

`config/ProjectProfile.baseDirFor(Path)` matches the canonical **file name** exactly, so a named
profile falls through to the "loose hand-imported file" branch and keeps `.analyser/` as its anchor:

```java
boolean canonical = dirName.toString().equals(".analyser")
        && file.getFileName().toString().equals("project.fluxtion-settings");   // ← exact name only
return canonical && dir.getParent() != null ? dir.getParent() : dir;            // ← else: .analyser/
```

`baseDirFor` feeds ~14 call sites (source roots, runbooks, vocabulary, reports, the reported project
root, export), so the single wrong anchor propagates everywhere at once.

## Fix

Anchor on the profile's **role**, not its exact file name. A new
`ProjectProfile.isProjectProfileFileName(String)` accepts the canonical name and the named form
`project.<name>.fluxtion-settings`; `baseDirFor` uses it.

Deliberately **scoped to the `project.*` naming** rather than "any file in `.analyser/`": the existing
test `baseDirForDistinguishesTheCanonicalProfileFromALookalike` pins that
`.analyser/other.fluxtion-settings` keeps its own directory, and that behaviour is intentional for a
loose settings file someone happened to save there. The new rule keeps that test green.

**Test:** `ProjectProfileTest.baseDirForAnchorsNamedProfilesToTheProjectRoot` — named profiles anchor
at the project root, the canonical form still does, and the name rule rejects `other.…`,
`projectX.…`, `project.….bak` and `null`. `ProjectProfileTest`: **20/20 green**.

## Consequence for users (why this mattered more than it looks)

The observed workaround used `~`-form absolute source roots, which resolve
regardless of anchor. But `ui/ProjectModel` correctly flags `~`/absolute roots as **not portable**
(the red rows, *"will not resolve it on a colleague's machine; declare a workspace anchor or move it
under the project"*). So the bug forced users into the exact form the panel warns about — and runbook
pointers must stay project-relative (D-C2), so that same `~` workaround is refused for them. **The red source roots were a symptom of this bug, not a separate
defect.** After the fix, a named profile can use the same portable `workspaceRoot=..` +
`../sibling/…` forms as the canonical file.

## Related observations (not fixed or re-tested in the UI here)

> **Superseded — all three are resolved. See "Resolution of the three observations" below.** This section
> is kept as written because it records what was known before the owner ruled on D-L3, including one
> reading that turned out to be wrong: `Target.NONE` on a saved-chart row was taken as a boundary the
> spec had drawn. **That dismissal was itself wrong — see the correction below.** The reading recorded
> here turned out to be right.

Source inspection distinguishes the following cases; they are not three proven instances of one bug:

1. **Reports ▸ Open reveals the Reports tab without selecting the named report.**
   `ProjectPanel` calls `navigator.showTab("Reports")` without passing the row's report identity.
   This explains why the currently selected report remains visible; it does not establish that the
   first report always opens. Selecting an already-loaded report by identity is a possible follow-up.
2. **Saved charts have no per-row Open action.** `ProjectModel` assigns `Target.NONE` to saved-chart
   rows, so there is no failing Open handler on those rows. Adding an action needs a design decision:
   the Project panel is reveal-only (D-L3), and instantiating a saved chart is not merely revealing it.
3. **Graph ▸ Open reveals the Topology tab.** The reported empty tab should be re-tested after the
   profile-root fix. This action alone neither loads a graph nor establishes why the tab is empty.

These observations do not expand this path-resolution fix into Project-panel navigation work.

## The question this needed, and the answer — 2026-09-24 (35eeb320)

A first attempt to patch the two Open defects was abandoned on reading the tests: D-L3 is not a
convention here, it is asserted. `ProjectPanelIsRevealOnlyTest` pins the exact `Navigator` method set —

```java
assertEquals(Set.of("showTab", "openSettings"),
        Set.of(…ProjectPanel.Navigator.class.getDeclaredMethods()…),
        "the Navigator moves the eye, not the state; adding a method here is a spec change (D-L3)");
```

— plus a constant-pool check that `ProjectPanel` and `ProjectModel` never name `MainFrame`,
`ActionExecutor` or `AppControl`: *"it renders, it does not act"*. So giving a row the ability to open
*its own* report or chart would fail that assertion **by construction**. It was a spec change, and the
test said so in its own failure message. The question put to the owner was therefore:

> Is revealing a *specific* already-loaded report or chart still "moving the eye" (in scope for D-L3, so
> `Navigator` may name the item), or is selecting an item a state change (out of scope, so the Project
> panel should keep revealing the tab only)?

**The owner answered "still navigation"** (2026-09-24), so the spec, `ProjectPanelIsRevealOnlyTest` and
the implementation changed together as one deliberate change, exactly as that question anticipated.
`spec-loaded-panel.md` D-L3 now carries the amendment and its limit: a `Navigator` method must reveal
something that already exists; creating, editing or discarding belongs on the action surface the panel
still cannot reach.

## Resolution of the three observations

1. **Reports ▸ Open** — fixed. `Row` gained an `item` carrying the row's identity, and the report row
   sets it to the report's **name** while still displaying its **title**. That split was the substance of
   the bug: `ReportsPanel.select` matches on name, so a panel passing its label along would have selected
   nothing even after the identity was threaded through.
2. **Saved charts** — fixed, and the earlier reading of `Target.NONE` as "consistent with D-L3" was
   wrong in effect. ~~The rows still rendered an Open button; it was simply wired to nothing, which is not
   a boundary, it is a dead control.~~ **Withdrawn — that claim was false; see the correction below.**
   They now carry `Target.CHART`, and `GraphTabs.openSaved` opens a
   saved-but-not-open chart from the profile — reveal, not create, because the definition already exists.
   A chart already open is selected rather than rebuilt, so Open never discards later edits.
3. **Graph ▸ Open reveals the Topology tab** — re-tested after the profile-root fix, and the tab is
   populated (13 nodes, `graphSource: OPENED`, the log's records loaded). This observation was downstream
   of the anchoring bug. Note the re-test that matters: an earlier check drove `analyser_topology`, the
   **verb**, which loads the graph itself and therefore proves nothing about the button. Only the
   button-level test below covers the reported behaviour.

### The UI-test gap is closed

`ProjectPanelOpenRevealsTheRowsItemTest` is the harness this note called for and could not assume:
headless Swing, a fixture `ProjectModel`, the row's actual Open button found by the label it displays,
clicked, asserting the call made on a recording `Navigator`. It pins that Open on report B reveals report
B, that the two report rows do different things, that a saved chart reveals itself, and that a
placeholder row naming no item still only reveals the tab. The fixture's reports deliberately have titles
that differ from their names, so a regression that collapses label and identity fails here.

Suite after the change: **1,887 tests, 0 failures, 0 errors, 62 skips** (1,877 before; the ten new tests
are this class and `GraphStylePersistenceTest`). `ProjectModelTest`'s saved-chart assertion changed with
the spec and was renamed to say what it now pins.

### Correction: the "dead control" claim was false — 2026-09-24

Found by independent review (`review/project-panel-chart-lifecycle-2026-09-24-indep`) and confirmed here
by inspection at `35eeb320^`:

- the saved-chart rows had `path == null` and `Target.NONE`;
- `ProjectPanel`'s target switch fell through to `default -> { }`;
- the action strip was attached only `if (actions.getComponentCount() > 0)`.

So **no button was rendered on those rows at all**. There was no Open, and therefore no dead control. The
author's repeated claim that "the rows still rendered an Open button, so it was a dead control, not a
boundary" is false, and it was used to dismiss the earlier reading — which was, on the corrected facts,
**right**: `Target.NONE` there was a boundary the spec had genuinely drawn.

This matters beyond tidiness because the false premise propagated into four places: this note, the
commit message of `38ecc7f3`, the **D-L3 amendment** in `spec-loaded-panel.md`, and the **shipping
CHANGELOG**. All four are now corrected. The consequence for the spec: the report leg of the amendment is
a real defect and justifies the `Navigator` change on its own; the chart leg is a **deliberate widening of
D-L3** chosen by the owner, not the repair of something broken. The owner's decision stands either way —
only the argument for it changes.

The lesson worth keeping is narrower than "check your facts": the claim was never verified against the
parent commit, only reasoned from a `Target.NONE` in the current source. One `git show 35eeb320^` would
have settled it before it reached a specification.

### What is still NOT verified — checks for a person at a real display

The new test asserts that the panel **asks** for the right thing: a click on report B's Open calls
`showReport("B")`. Nothing in the suite asserts that `MainFrame`'s implementation of those two methods
**does** the right thing on screen. That half is genuinely out of reach here, and saying so is the point:

- The python harnesses (`verify-m46-agent-api.py`, `verify-m64-spotlight.py`, `verify-m43.py`) drive the
  built jar over the **action socket**, which has verbs, not button presses. The socket has no verb that
  clicks a Project-panel row and must not gain one — the same reasoning `verify-m43.py` records for the
  modal `PointerDialog`. So these three defects cannot be closed by extending those scripts.
- `ProjectPanelIsRevealOnlyTest` is structural (bytecode, Navigator shape) by design.
- `ProjectPanelOpenRevealsTheRowsItemTest` stops at the `Navigator` boundary, deliberately: past it lies
  `MainFrame`, which the panel may not name.

**Five checks, needing a display** (the reviewer, or the author with a GUI):

1. With two saved reports, Open on the second shows the **second** report's body in the Reports tab —
   not the tab with the previous selection still in it.
2. Open on the first then shows the **first**. Alternating must alternate; a single correct-looking
   result proves nothing, because the pre-fix behaviour was "whatever was already selected".
3. Open on a saved chart that is **not** an open tab opens it and selects it, with its series, notes,
   right axis and pin intact — `openSaved` applies the same spec path as a profile restore.
4. Open on a chart that **is** already a tab selects that tab and does **not** rebuild it: make a change
   (add a series), press Open, confirm the change survives.
5. Set a chart to **Line**, save, reopen the profile, confirm it comes back as Line. Then confirm a chart
   saved before this change still opens as Stairs.

Checks 1–2 are the reported defect; 4 is the regression risk the fix introduces; 5 is the style fix.

### Verification record — 2026-09-24, against a build of `main` at `43ce82fc`

All five checks were run by the owner at a real display, on a locally built jar (not the 1.19.1
release). **Checks 1–4 pass**: Open on the second report reveals the second report, alternating between
the two rows alternates correctly, a saved chart that is not an open tab opens with its series and
annotations intact, and Open on an already-open chart selects it without discarding a series added since.

**Check 5 passes**, driven over the action socket and confirmed in the profile bytes: setting a chart to
Line writes `graph.0.style=line`; closing the project, reopening it and reopening the log then forcing a
save from live panel state leaves `line` in place. That last step is the one that proves the restore
path — a save writes from the panel, so had `applySpec` not applied the style the panel would have been
at stairs and would have overwritten `line` with `step`. The same sequence on 1.19.1 wrote no style key
at all.

One consequence worth stating plainly: **the first save after upgrading adds a style key to every
chart**, not only deliberately styled ones, because `GraphTabs.specs()` always reports `styleName()` and
never null. The value written is always the chart's actual style and nothing reads differently, so this
is benign — but "existing profiles are untouched" holds only until the first save.

## Closing a chart destroys it — found during the same session (FIXED, 38ecc7f3)

Reported by the owner immediately after the checks: close a chart tab and the chart is gone for good,
and its row vanishes from the Project panel's *Saved charts* section. Reproduced in the profile bytes —
`graph.count` went 2 → 1 and every `graph.1.*` key was deleted, taking the chart's series, expressions,
right-axis assignment, explanation and all three pinned notes with it. The definition was recovered from
a file backup; without one it would have been unrecoverable.

**Cause.** `GraphTabs.closeCurrent` removes the tab and fires the change listener;
`MainFrame.syncOpenGraphsIntoConfig` then does `config.savedGraphs.clear()` followed by
`addAll(graphTabs.specs())`. `specs()` walks the **open tabs**, so the profile's saved-chart list is not
a list of saved charts at all — it is a mirror of what is currently open. Closing a tab is therefore a
silent, unconfirmed delete of persistent annotated state.

**This is pre-existing and not caused by 35eeb320** — closing a chart has always destroyed its notes. But
35eeb320 makes it matter more, and made it visible: the *Saved charts* rows now offer an Open, which
promises a recoverability the model does not provide. `SessionFacts.savedGraphs` already reports an
`open` flag per chart, so the vocabulary for "saved but not open" exists; today it can only ever be false
while a log is loaded, because the two lists are kept identical.

**The shape of a fix** (needs an owner decision before building):

- *Close* should close the tab and keep the definition, so the Project panel's Open can reopen it —
  which already works, via `GraphTabs.openSaved`.
- *Delete* becomes a separate, explicit action for removing a definition. The owner has asked for this.
  It cannot live on the Project panel: deleting is mutation, and D-L3's amendment covers revealing an
  item, not destroying one. It belongs on the Graph tab's toolbar beside Close.
- The open question is what a **reload** should do with a chart that was closed but not deleted: reopen
  every saved chart as a tab (today's `restore` behaviour, which would undo the close), or persist the
  `open` flag per chart and reopen only those that were open. The second matches what a person means by
  closing something, and the flag is already in the model — but it adds state that a hand-edited profile
  can contradict.

### What was built (owner decision, 2026-09-24)

The owner chose to **persist the open flag** and to land **Close and Delete together**, on the reasoning
that without a Delete, charts could never be removed once Close stopped removing them.

- `GraphSpec` gains `open`, defaulting **true**, with a `withOpen` copy. Every pre-38ecc7f3 constructor
  delegates with `true`, so no existing profile or caller changes behaviour.
- `ConfigStore` writes `graph.N.open=false` **only for a closed chart**. An untouched profile gains no
  key, and a missing key reads as open.
- `syncOpenGraphsIntoConfig` now **merges** rather than clearing: an open tab wins as live state, a chart
  that is no longer a tab is kept and marked closed, and order follows the existing profile so charts do
  not shuffle on every save. This single change is what stops the data loss.
- `GraphTabs.doRestore` skips charts marked closed, so closing one survives a reload instead of being
  undone by it.
- **Delete chart** is a new toolbar button beside Close. It confirms first, names the chart, and says what
  is lost; it tells `MainFrame` to drop the definition before the change listener writes the merged list.
  It is deliberately NOT on the Project panel: deleting is mutation, and D-L3's amendment covers revealing
  an item, not destroying one.

`ClosingAChartKeepsItsDefinitionTest` pins the behaviour at the level where the loss happened — the merge
and the round trip, not the button: a closed chart survives with its explanation and pinned notes, open is
the default for a legacy profile, only a closed chart writes the key, and `withOpen` changes nothing else
about a chart. Suite: **1,891 tests, 0 failures, 62 skips**.

Still unverified at a display: that the two toolbar buttons behave as described in a real window.

### A separate defect found while re-testing: the plot style was never saved

Not a navigation issue and not D-L3-bounded. `GraphPanel` has always treated style as a persistable
mutation — it calls `mutated()`, and its own doc lists style beside series, pins and notes — but
`GraphSpec` had no such component and `ConfigStore` wrote no key, so the choice was dropped on every
save. It stayed invisible because stairs is `ChartPanel`'s default: a stairs chart round-tripped by
accident, and only a deliberate line or points chart came back changed. `GraphSpec` now carries `style`,
`ConfigStore` writes `graph.N.style` only when one was declared (so existing profiles are untouched and
still open as stairs), and an unrecognised hand-edited value is dropped rather than carried to a panel
that would fall back silently.

## Review verification — 2026-09-24

At `c3523506`, the reviewer ran:

```sh
JAVA_HOME=/path/to/jdk21 mvn -q -Dtest=ProjectProfileTest,ProjectSessionTest,PathFormTest,SettingsShareTest test
```

**52 tests passed, zero failures/errors/skips:** ProjectProfileTest 20, ProjectSessionTest 16,
PathFormTest 3, SettingsShareTest 13. Restoring the canonical-only lookup made
`ProjectProfileTest.baseDirForAnchorsNamedProfilesToTheProjectRoot` fail at its first named-profile
assertion: the actual base was the `.analyser` directory instead of its parent. Restoring the source
byte-for-byte returned all 52 tests to green. No UI reproduction of the related observations was run.

After the documentation corrections, `JAVA_HOME=/path/to/jdk21 mvn -q test` passed:
**1,877 tests, zero failures/errors, 62 skips**. The initial sandboxed run had 29 socket-permission
errors and zero assertion failures; the run with loopback permission passed. `mkdocs build --strict`
also passed. The skips are retained in the count; this was not a real-display run.

## Evidence pointers

- `config/ProjectProfile.java` (`baseDirFor`, `CANONICAL_RELATIVE`) — the fix.
- `ui/ProjectModel.java` — the `notPortable` badge (`form` is `absolute`/`~`), report rows
  (`Target.REPORTS`), graph pairing row (`Target.TOPOLOGY`), `SAVED_GRAPHS` section.
- `ui/ProjectPanel.java` — the `Target` → action switch.
