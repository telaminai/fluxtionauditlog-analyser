# Settings Export / Import — Shareable Analysis Setups (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-13

Companion to **[spec.md](../spec.md)** (§11 configuration) and **[tracker.md](../tracker.md)** (milestone
**M15**).

Let a user **export their analysis setup to a single file and import someone else's** — so a team
investigating the same processor shares source roots, event-processor lists, maven repos and (the real
prize) **named graphs with formulas and pinned windows** — without ever moving secrets. "Here's my
pick-off-exposure workspace" becomes an email attachment.

---

## 1. Scope

**In scope (v1):** File → *Export settings…* / *Import settings…*; a versioned, human-readable file;
category selection on export; safe merge on import; hard exclusion of secrets and machine-local noise.

**Non-goals:** syncing (no cloud, no watch); exporting logs or analysis *results* (flags/notes are
per-file session state, and log data may be sensitive — the file shares the *setup*, not the evidence);
any remote fetch on import.

## 2. File format

Same technology as the config itself — **Java properties written by the mini `ConfigStore` machinery**
(one honest format; no new codec, diff-friendly, hand-editable):

```properties
# fluxtion-analyser shared settings
share.version=1
share.exportedAt=2026-08-13T14:02:11Z
sourceRoot.count=2
sourceRoot.0=~/IdeaProjects/market-maker-lib/src/main/java
...
graph.count=3
graph.0.name=Pick-off exposure
graph.0.expr.0.label=quoted spread
graph.0.expr.0.expr=askMakerOrder.price − bidMakerOrder.price
...
```

Suggested extension: **`.fluxtion-settings`** (properties inside; the extension makes the file-chooser
filter and double-click association possible later). `share.version` gates future format changes —
an importer rejects a *newer* major version with a clear message, and reads older ones forever.

## 3. What is exported — whitelist, never a dump

Export is a **whitelist copy** of selected categories; anything not listed below can never leak into a
share file, no matter what the config file accrues later.

| Category (export checkbox) | Keys | Default |
|---|---|---|
| **Source roots** | `sourceRoot.*` | ✅ on |
| **Maven repos** | `mavenRepo.*`, `mavenRepoSearch` | ✅ on |
| **Event processors** | `eventProcessorFqn.*`, `selectedEventProcessor` | ✅ on |
| **Graphs** | `graph.*` (names, series, formulas, resolve policy, pinned from/to) | ✅ on |
| **View** | `hiddenColumn.*` | ✅ on |
| **Assistant** | `assistant.inProcess`, `assistant.rest`, `assistant.maxRounds`, `assistant.maxActionsPerReply` | ✅ on |
| **LLM (no key)** | `llmProvider`, `llmModel`, `llmBaseUrl` | ⬜ off |

LLM defaults **off**: provider/model are harmless, but `llmBaseUrl` can reveal an internal proxy —
the user opts in knowingly. The checkbox is labelled *"LLM provider/model/base-URL (never the API key)"*.

**Never exported, not even optionally:** `apiKey` · `awsProfile`/`awsRegion` (local account details) ·
`logFile` + `recentFile.*` (paths to possibly sensitive data) · `searchHistory.*` (queries can quote log
contents) · window bounds/theme (machine-local) · `lastRunVersion`.

### 3.1 Path portability

Absolute paths rarely survive a machine hop. On **export**, any path under the user's home directory is
rewritten to a `~/` prefix; on **import**, `~/` expands to the importer's home. Paths outside home are
kept verbatim. Imported roots that don't exist simply show **red in Settings** (that affordance already
exists) — import never fails on a missing path, it just leaves visible feedback.

## 4. Import — safe merge, visible summary

1. Parse; verify `share.version`; **silently drop any non-whitelisted key** (a hand-crafted file
   containing `apiKey=…` is ignored — the whitelist applies on the way *in* too, so import can never
   plant a secret or overwrite one).
2. Show a **summary dialog** before touching anything: per category, what the file contains and what
   will happen (e.g. "Source roots: 2 new, 1 already present · Graphs: adds 'Pick-off exposure',
   replaces 'Spreads'"). Categories are individually deselectable at import time too.
3. Apply on OK:
   - **Lists merge additively** (dedup): source roots, maven repos, EP FQNs. Import never deletes a
     local entry.
   - **Graphs merge by name**: an incoming name that exists **replaces** that graph (the file is the
     sender's curated artifact — half-merging series inside one graph would produce charts nobody
     designed); new names append.
   - **Scalars overwrite** only when present in the file *and* the category was selected:
     `selectedEventProcessor`, `mavenRepoSearch`, assistant caps, LLM provider/model/base-URL.
4. Persist via the normal `ConfigStore.save`, refresh the open UI (same path as Settings-OK —
   `onConfigChanged`), and restore imported graphs into the Graph tabs.

Nothing is destructive beyond named-graph replacement, which the summary calls out explicitly.

## 5. UI

- **File → Export settings…** — category checkboxes (defaults per §3), file chooser
  (`analysis-setup.fluxtion-settings` suggested name). Footer note: *"API keys and machine-local
  settings are never exported."*
- **File → Import settings…** — file chooser → summary dialog (§4.2) → OK/Cancel.
- Both live under File, next to Settings; no toolbar presence (occasional-use actions).

## 6. Seam & components

- `config/SettingsShare` (new, **pure/headless**): `export(AppConfig, Set<Category>) → String` and
  `preview(String) → ImportPlan` and `apply(ImportPlan, AppConfig)` — the whitelist, `~` rewriting,
  version gate and merge rules all live here, unit-testable without Swing. Reuses `ConfigStore`'s
  list read/write helpers (extract them to package-visible statics rather than duplicating).
- `ui/` — two dialogs (export categories, import summary) + the File menu items; thin wrappers over
  the seam.
- `ImportPlan` — the parsed, filtered, categorised content + per-category counts; the summary dialog
  renders it and `apply` consumes it (parse once, no re-read between preview and apply).

## 7. Delivery slices

1. **`SettingsShare` core** — export/preview/apply with whitelist, `~` paths, version gate, merge
   rules; full unit tests (this is nearly all of the feature's logic).
2. **UI** — menu items + the two dialogs, wired to `onConfigChanged`/graph restore.
3. **Docs** — help page section + user-site page ("Share your setup"); changelog entry.

## 8. Testing

- Round-trip: export all categories → import into a fresh `AppConfig` → equals on whitelisted fields.
- **Secrets can't cross:** exported text never contains `apiKey`/`awsProfile`/`recentFile`/
  `searchHistory` even when set; importing a malicious file with `apiKey=x` leaves the local key
  untouched.
- Merge: additive lists dedup; graph name collision replaces exactly that graph; deselected category
  in the import dialog is untouched.
- Paths: home-relative exported as `~/…`, expanded on import; non-home path verbatim; missing dir
  imports fine (red in Settings).
- Version gate: `share.version=2` (major) → clear rejection message; missing version → treated as 1.

## 9. Open questions

- **Flags/notes export** — assistant `flag` notes are findings, not setup; sharing them means sharing
  log content. Leaning: separate feature (an "investigation bundle") if ever, not part of settings.
- **Import from URL** — convenient for teams (settings file in a repo), but adds a network fetch to a
  deliberately offline tool. Leaning: no; the OS file dialog can open network mounts anyway.
- **`.fluxtion-settings` double-click association** — needs installer-level integration; revisit with
  native packaging (release-process.md §10).
