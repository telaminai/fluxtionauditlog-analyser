# Sharing setups

Share your whole analysis setup as one file — so a team investigating the same processor starts from
the same source roots, event processors and **named graphs** (formulas and pins included).

## Export

**File ▸ Export settings…** — tick what to include:

| Category | Contents |
|---|---|
| Source roots | your source directories — and the project's **workspace anchor** (`workspaceRoot=..`), which lets a sibling checkout travel as `../shared-lib/…` ([Portable context](portable-context.md)) |
| Maven repos | local repositories + the search toggle |
| Event processors | the FQN list and the selected one |
| Graphs and named focuses | named graphs (series, formulas, resolve policy, pinned windows, notes, guides, condition bands, external-CSV definitions, marker definitions — never their extracted values) and named topology focuses (node sets + rationale) |
| Investigation reports | report **definitions** — section references, table column specs, the authoring context (log fingerprint + filter, **including any `provenance` string naming the system the log came from** — e.g. `risk-engine · localhost:8081 · ~/dev/risk`), and any **narrative text the author (often an agent) wrote about your data**. Never log records: evidence re-renders from the recipient's own log, and unresolved references say so. Narrative is the reason this is its own checkbox rather than a passenger on Graphs |
| View | hidden columns |
| Assistant | round / per-reply caps |
| LLM *(off by default)* | provider / model / base-URL — **never the API key** |
| Domain glossary LOCATION (a markdown file in your repository — never its contents) | the `vocabulary=docs/glossary.md` pointer — where the team's terms are defined; the file itself stays in the repository ([Portable context](portable-context.md)) |
| Environments (names, the provenance string each stamps — which may name systems and hosts — and their log directories; never log data) | `environment.N.name/provenance/logDir` and `environment.default` — which system a log from here came from ([Portable context](portable-context.md)) |
| Saved analyses (named analyser-verb sequences with their rationale — they can only drive this viewer, never a server) | `analysis.N.*` — the steps and their rationale; a step can only be an analyser verb, and never a project switch ([Portable context](portable-context.md)) |
| Report destinations (where reports are published — a bucket, directory or base URL; never a credential) | `destination.N.name/location` — places only; anything shaped like a credential is refused on export and import ([Portable context](portable-context.md)) |
| Runbook LOCATIONS (paths in your repository — never their contents) *(off by default)* | name → project-relative path, e.g. `deploy → ops/deploy.md`. A **pointer** into your repository; the exporter refuses anything that is not a plain relative path, and so does the importer — see [Portable context](portable-context.md) |

A shared graph that uses **external series** carries the CSV's *definition* (path, columns, clock),
never its data — so it may depend on a **local file the recipient does not have**. Opening such a graph
draws everything else and says what did not resolve; nothing fails silently.

Then **Copy to clipboard**, **Save file…** (a `.fluxtion-settings` file, revealed in your file manager
for drag-drop into Slack/WhatsApp), or **Email…**.

## What is never shared

Your **API key**, AWS profile/region, recent files, search history and window/theme are never exported —
and the whitelist is enforced on **import** too, so a hand-crafted file can't plant or overwrite a
secret. Paths under your home are written `~`-relative so they survive a move to another machine.

A runbook's **contents** never travel: the profile may point at `ops/deploy.md`, never carry the commands,
and a value shaped like a command is refused with a reason on both export and import.

## Import or open?

Importing asks which of two things you mean. **Merge** adds the file's settings to what you have — the
share-a-setup flow this page is about. **Open as project** replaces your project settings and makes the
file active; see [Working across projects](projects.md).

## Import

**File ▸ Import settings…** shows a per-category **summary** — what's new, what would be replaced —
before anything changes. Deselect any category you don't want. On apply:

- **Lists merge** (source roots, Maven repos, event processors) — nothing local is deleted.
- **Graphs merge by name** — an incoming graph replaces a same-named local one; new names are added.
- **Scalars** (selected processor, assistant caps, LLM fields) overwrite only when present and selected.
