# Sharing setups

Share your whole analysis setup as one file — so a team investigating the same processor starts from
the same source roots, event processors and **named graphs** (formulas and pins included).

## Export

**File ▸ Export settings…** — tick what to include:

| Category | Contents |
|---|---|
| Source roots | your source directories |
| Maven repos | local repositories + the search toggle |
| Event processors | the FQN list and the selected one |
| Graphs and named focuses | named graphs (series, formulas, resolve policy, pinned windows) and named topology focuses (node sets + rationale) |
| View | hidden columns |
| Assistant | round / per-reply caps |
| LLM *(off by default)* | provider / model / base-URL — **never the API key** |

Then **Copy to clipboard**, **Save file…** (a `.fluxtion-settings` file, revealed in your file manager
for drag-drop into Slack/WhatsApp), or **Email…**.

## What is never shared

Your **API key**, AWS profile/region, recent files, search history and window/theme are never exported —
and the whitelist is enforced on **import** too, so a hand-crafted file can't plant or overwrite a
secret. Paths under your home are written `~`-relative so they survive a move to another machine.

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
