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
> spec had drawn, when the row still rendered an Open button and simply had nothing behind it.

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

## Resolution of the three observations — 2026-09-24 (M68.2)

The owner answered the decision question above with **"still navigation"**, so the spec,
`ProjectPanelIsRevealOnlyTest` and the implementation changed together as one deliberate change, exactly
as this note said they would have to. `spec-loaded-panel.md` D-L3 now carries the amendment and its
limit: a `Navigator` method must reveal something that already exists; creating, editing or discarding
belongs on the action surface the panel still cannot reach.

1. **Reports ▸ Open** — fixed. `Row` gained an `item` carrying the row's identity, and the report row
   sets it to the report's **name** while still displaying its **title**. That split was the substance of
   the bug: `ReportsPanel.select` matches on name, so a panel passing its label along would have selected
   nothing even after the identity was threaded through.
2. **Saved charts** — fixed, and the earlier reading of `Target.NONE` as "consistent with D-L3" was
   wrong in effect. The rows still rendered an Open button; it was simply wired to nothing, which is not
   a boundary, it is a dead control. They now carry `Target.CHART`, and `GraphTabs.openSaved` opens a
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
