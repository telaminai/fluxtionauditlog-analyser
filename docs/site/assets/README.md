# Site assets

Screenshots, the loop diagram and the downloadable sample log. This page is excluded from the built site
(`exclude_docs` in `mkdocs.yml`).

**Anonymise before committing.** Sample logs and screenshots must contain **no real venue, vendor, book,
thread, logger or account names** — use neutral placeholders (e.g. `DEMO`, `marketMaker-DEMO`,
`com.acme…`). This is a public site.

## Inventory

| File | Used on |
|------|---------|
| `screenshot-light.png` | Home — hero |
| `screenshot-dark.png` | User guide — index (dark theme) |
| `audit-loop.svg` | Home — the closed-loop diagram (inlined via a snippet) |
| `assistant-explain.png` | Home spotlight + Analyser assistant |
| `records-overview.png` | Records, detail & filtering — the surfaces at a glance |
| `flagged-only.png` | Records, detail & filtering — flagging & focus |
| `record-diff.png` | Records, detail & filtering — diff |
| `adding-to-graph.png` | Graphs — adding series |
| `series-editing.png` | Graphs — formula / Edit-series panel |
| `source-navigation.png` | Source navigation |
| `settings-source-roots.png` | Getting started — Settings |
| `sample-audit-log.yaml` | Downloadable sample (Home, Getting started, Install, Log format) |

## Capturing screenshots

Capture on macOS with **Cmd+Shift+4 → Space → click the window**. Trim to the window (no desktop). Aim
for ~1600px wide, then downscale (`sips -Z 1600 in.png --out out.png`). Reference with a relative path
and real alt text, e.g. from a `user-guide/` page:

```markdown
![Editing a series and its formula](../assets/series-editing.png)
```
