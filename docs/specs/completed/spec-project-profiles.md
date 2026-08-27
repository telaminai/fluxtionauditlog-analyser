# Project Profiles — Global vs Local Settings (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-15

Companion to **[tracker.md](tracker.md)** (milestone **M20**),
**[completed/spec-settings-share.md](spec-settings-share.md)** (M15 — the export/import
whitelist this builds on), and **[spec-onboarding-example.md](../spec-onboarding-example.md)** (M19 — the
playground bundle *is* a project profile; M20 is how it auto-configures).

## The problem

Today there is **one** config file: `~/.fluxtion-analyser/config`. Everything lives in it — the API key
*and* the source roots, the event processor, the saved graphs. **File ▸ Import settings (M15) merges into
that single global file and saves.** So working across several Fluxtion projects is awkward:

- switch project → import its settings → the new roots/EP/graphs **pile up on top of** the last
  project's (import is additive by design — right for *sharing*, wrong for *switching*);
- there's no one-click "I'm on project B now";
- and the playground onboarding (M19) has to lean on that additive import, which subtly pollutes the
  user's global config with a demo's roots.

The fix is not a general field-by-field "local overrides global" merge (that invites precedence
tangles). It's **profile switching over a boundary that already exists**.

## The model — two tiers, reusing the M15 whitelist as the line

M15 already split settings into shareable vs never-shared. That **is** the global/local boundary:

| Tier | Categories | Where it lives |
|---|---|---|
| **Global (machine)** | API key · LLM provider/model/base-URL · AWS profile/region · theme · memory threshold · recent files · search history · window bounds · assistant caps | `~/.fluxtion-analyser/config` (as today) |
| **Project profile** | **source roots · event processor(s) + selected · Maven repos · saved graphs · hidden columns** | a `.fluxtion-settings` file, e.g. `<project>/.analyser/project.fluxtion-settings` |

The project set is **exactly the M15 shareable whitelist** — no new boundary to invent, and the API key
can never land in a project file by construction.

**Precedence is trivial because the sets are disjoint:** global holds machine things, the active project
holds project things. There is no field that lives in both, so "local overrides global" never needs a
tie-break. Switching a project replaces the project-scoped working set; global is untouched.

## Flows

- **Open / Switch project** (new, **REPLACE** semantics) — pick a project file (or a recent one). The
  analyser **swaps** the project-scoped categories to that file's values (does not merge), keeps global
  as-is, and records it as the **active project**. This is the "jump project to project" action.
- **Persistence — auto-persist, debounced** (resolves **O4**) — edits made while a project is active
  persist to the *project* file (never the key), exactly as today's single config auto-saves; writes are
  debounced so a burst of graph tweaks is one write, not many. **Save project as…** exists only to *fork*
  the profile to a new path (then that becomes active). There is no manual "Save project" — matching
  today's no-surprise behaviour. Because a profile may be a committed file, the docs note that a
  committed profile evolves like any config-as-code (`.vscode/settings.json`): expect diffs, review them
  like code. (See O4 for why not explicit-save.)
- **New project…** — start an empty project profile at a chosen path.
- **Recent projects** — a list (like Open Recent) for one-click switching.
- **Import settings (M15) is unchanged** — it stays the **additive, share-a-setup** flow (merge into the
  current context). The Import dialog gains one option: *"Open as project (replace)"* vs *"Merge"*, so
  the two intents are explicit and never conflated.
- **No active project** → behaviour is exactly today's single-config app (fully backward compatible).

## Active project & startup

- Global config gains one field: `activeProjectPath`. On launch the analyser loads global, then (if set
  and the file exists) loads the active project profile over the project-scoped categories.
- If the active project file is missing (moved repo), fall back to global-only and clear the pointer with
  a status note — never fail to start.

## Auto-detect (ties M19 together)

When a log is opened, if it sits under a directory containing `.analyser/project.fluxtion-settings`,
offer **"Load this project?"**. Detection is **one rule, one path** — the M19 playground bundle ships
its profile at exactly this path (spec-onboarding-example §Part 1), so it *is* a project profile, not a
separately-named file the detector also has to accept. This is what makes the **playground template
auto-configure**: download the template → open its audit log → the analyser detects the bundled project
profile and loads roots/EP/graphs → **zero manual setup**, and it's a real switchable project you can
leave and return to (not a one-off merge into global). M20 generalises M19's import into a first-class
profile.

## Team-share via git

Because a project profile **excludes the API key** (M15 guarantee), a team can **commit
`.analyser/project.fluxtion-settings` to the repo** — like `.vscode/settings.json`. Clone → open the
analyser → the project's source roots, event processor and curated graphs are already configured. (The
M19 fix-brief `.analyser/` gitignore must *not* exclude the committed profile — scope the ignore to
`fix-brief-*.md`, not the whole dir.)

## Backward compatibility & migration

- Existing users: nothing changes until they open/create a project. Global config keeps working.
- First time a project is opened, the project-scoped categories currently in global are **not** deleted —
  they remain the "no project" defaults; switching back to *no project* restores them.
- Relative source roots in a project file resolve against the file's directory (**M19.2, shipped**).

## Delivery slices

1. **M20.1 — tier the config** — mark the project-scoped categories; add `activeProjectPath`; load/save a
   project profile via the existing `SettingsShare` machinery (reuse export/apply with REPLACE for the
   project set). Headless-testable.
2. **M20.2 — Open/Switch/New project + Recent projects** UI (File menu), REPLACE semantics; the Import
   dialog's merge-vs-open-as-project choice.
3. **M20.3 — Auto-detect** a project file beside an opened log → "Load this project?" (M19 hook).
4. **M20.4 — Docs** — user-guide "Project profiles / working across projects"; note the git-shareable
   profile; M19 tutorial upgrades from "import once" to "auto-loaded project".

## Open questions

- ~~**O1** — Maven repos global or project-scoped?~~ **resolved: project-scoped** (identical to the M15
  whitelist). The portability worry dissolves because `SettingsShare` writes `~`-relative paths, so
  `~/.m2/repository` expands correctly on every teammate's machine — no absolute-path leak. Consistency
  with M15 wins.
- ~~**O2** — profile location by default~~ **resolved: the in-repo path**
  `<project>/.analyser/project.fluxtion-settings` — team-share and M19 auto-detect both depend on it.
  The managed list under `~/.fluxtion-analyser/projects/` is *only* the recent-projects index, not the
  profile store.
- ~~**O3** — should switching projects also re-open that project's last log?~~ **deferred** — nice, but
  coupling log state to the profile is the kind of convenience that becomes a surprise; revisit if asked.
- ~~**O4** — Save semantics: explicit-save vs auto-persist?~~ **resolved: auto-persist, debounced** (see
  Flows). Explicit-save was rejected as a surprise — today's app auto-saves config, so a project profile
  behaving differently would violate least-astonishment. Debounce keeps a committed profile's git diffs
  legible; `Save as…` remains for forking to a new path. Teams committing the profile treat it as
  config-as-code (evolves, review the diffs).

## Acceptance

Two projects on one machine: **Open project A** → its roots/EP/graphs load, global key untouched; work,
graphs save to A's file. **Switch to project B** → A's roots are gone, B's are present (replace, not
pile-up). Restart → the active project reloads. Commit B's `.analyser/project.fluxtion-settings`, clone
elsewhere, open the analyser → B is configured with no manual setup and no key leaked.
