# Site assets

Screenshots, the loop diagram and the downloadable sample log. This page is excluded from the built site
(`exclude_docs` in `mkdocs.yml`).

**Anonymise before committing.** Sample logs and screenshots must contain **no real venue, vendor, book,
thread, logger or account names** — use neutral placeholders (e.g. `DEMO`, `marketMaker-DEMO`,
`com.acme…`). This is a public site.

> ⚠️ **The `grep` sweep in `CLAUDE.md` rule 1 cannot see inside a PNG.** The screenshots shipped with the
> first public release were taken against a real audit log and carried live venue, vendor and project
> names onto this site; the sweep passed the whole time, because it only reads text. Screenshots are
> therefore no longer taken by hand — they are produced by `tools/capture-docs.py`, which drives a real
> analyser **loaded only with the demo fixture**, so an image is anonymous by construction rather than by
> inspection. If you must capture something the harness cannot reach, load the demo fixture first and
> check the title bar, status bar and every visible path before committing.

## Inventory

| File | Used on |
|------|---------|
| `screenshot-dark.png` | Home — hero (the tool photographs better dark: the canvas, source panes and plots all use a recessed dark surface, and light flattens all three) |
| `screenshot-light.png` | User guide — index (the light theme) |
| `audit-loop.svg` | Home — the closed-loop diagram (inlined via a snippet) |
| `records-overview.png` | Records, detail & filtering — the surfaces at a glance |
| `flagged-only.png` | Records, detail & filtering — flagging & focus |
| `source-navigation.png` | Source navigation |
| `topology-step-through.png` | Topology — stepping a cycle |
| `topology-explore.png` | Topology — scope and the index |
| `graph-series-dark.png` | Graphs — a value plotted over time (dark: plots read better on it) |
| `graph-step-dark.png` | Graphs — stairs style |
| `graph-series-light.png` | spare — the same plot on the light theme, if a page needs to match |
| `sample-audit-log.yaml` | Downloadable sample (Home, Getting started, Install, Log format) |

### Withdrawn — need recapturing

These illustrated dialogs and popup menus, which the capture harness cannot reach (a modal dialog or a
transient menu). They were **removed rather than left published**, because every one of them was taken
against a real log. The prose on each page already describes what they showed, so the pages read fine
without them. To restore any of them, load the demo fixture and capture by hand — or extend the harness.

| Was | Used on |
|------|---------|
| `assistant-explain.png` | Home spotlight + Analyser assistant |
| `record-diff.png` | Records, detail & filtering — diff |
| `adding-to-graph.png` | Graphs — adding series |
| `series-editing.png` | Graphs — formula / Edit-series panel |
| `settings-source-roots.png` | Getting started — Settings |

## Capturing screenshots

```bash
mvn package                        # the harness drives the built jar
python3 tools/capture-docs.py      # regenerates every image in the inventory above
```

The harness launches the analyser on the demo fixture, drives it over the localhost REST transport
(`topology`, `goto`, `flag`, `open`, `screenshot` verbs) and takes a **native** window capture so the
title bar is included. It sets the theme and resets the saved topology view first, so runs are
reproducible rather than depending on whatever state the app was left in.

Capturing by hand is a fallback for dialogs the harness cannot open. On macOS: **Cmd+Shift+4 → Space →
click the window**, then check every visible string before committing. Reference with a relative path and
real alt text, e.g. from a `user-guide/` page:

```markdown
```
