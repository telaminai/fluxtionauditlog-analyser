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
