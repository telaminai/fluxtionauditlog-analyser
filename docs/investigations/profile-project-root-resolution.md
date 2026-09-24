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

`analyser_context` with each profile active, identical root values in both files:

| | canonical `project.fluxtion-settings` | named `project.ws-feed.fluxtion-settings` |
|---|---|---|
| `project.root` | `…/maker-fxoc` ✅ | `…/maker-fxoc/.analyser` ✗ |
| `workspaceDir` (`workspaceRoot=..`) | `…/IdeaProjects` ✅ | `…/maker-fxoc` ✗ |
| `sourceRoot.0=src/main/java` | `…/maker-fxoc/src/main/java` ✅ | `…/maker-fxoc/.analyser/src/main/java` ✗ |
| `sourceRoot.1=../market-maker-lib/…` | `…/IdeaProjects/market-maker-lib/…` ✅ | `…/maker-fxoc/market-maker-lib/…` ✗ |
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

The only way to make a named profile work locally was `~`-form absolute source roots, which resolve
regardless of anchor. But `ui/ProjectModel` correctly flags `~`/absolute roots as **not portable**
(the red rows, *"will not resolve it on a colleague's machine; declare a workspace anchor or move it
under the project"*). So the bug forced users into the exact form the panel warns about — and runbook
pointers, which must stay project-relative (D-C2), could not be worked around at all: `~`-forming them
gets them pruned on autosave. **The red source roots were a symptom of this bug, not a separate
defect.** After the fix, a named profile can use the same portable `workspaceRoot=..` +
`../sibling/…` forms as the canonical file.

## Related findings (separate defects, NOT fixed here)

Found while diagnosing the above; all are the same shape — `ui/ProjectPanel`'s `Target` switch
navigates to a **tab** but never selects the **item**:

```java
case REPORTS  -> small("Open", …, () -> navigator.showTab("Reports"));    // no report identity
case TOPOLOGY -> small("Open", …, () -> navigator.showTab("Topology"));   // no graph identity
```

1. **Reports ▸ Open always opens the first report.** Every report row carries its title as `primary`
   but the action is the identity-free `showTab("Reports")`, so you land on whichever report is
   already showing. Needs an item-aware navigation (e.g. `showReport(name)`) and the name on the row.
2. **Saved charts ▸ open does nothing.** Same gap for the `SAVED_GRAPHS` section — no way to open a
   specific chart.
3. **Graph ▸ Open appears to do nothing.** It is `Target.TOPOLOGY` → `showTab("Topology")`, which does
   resolve (the tab exists), but with this profile bug active the Topology tab is empty, so the click
   looks dead. Worth re-testing after the fix; the identity gap above still applies.

Suggested direction: give `Target` an optional item key and extend the `Navigator` interface with
`showReport(String)` / `showGraph(String)` so a row can open *its own* thing.

## Evidence pointers

- `config/ProjectProfile.java` (`baseDirFor`, `CANONICAL_RELATIVE`) — the fix.
- `ui/ProjectModel.java` — the `notPortable` badge (`form` is `absolute`/`~`), report rows
  (`Target.REPORTS`), graph pairing row (`Target.TOPOLOGY`), `SAVED_GRAPHS` section.
- `ui/ProjectPanel.java` — the `Target` → action switch.
- See the sibling `topology-focus-vs-logged-highlight.md` for the other open Project/Topology item.
