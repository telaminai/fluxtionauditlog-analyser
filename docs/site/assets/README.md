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
| `mcp-generic-setup.png` | Working with AI ▸ Connecting an LLM — Generic MCP record, copy/save boundary |
| `mcp-claude-code-confirm.png` | Working with AI ▸ Connecting an LLM — explicit Claude Code registration confirmation |
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
python3 tools/capture-docs.py      # main/demo, chart, project/menu and spotlight shots
python3 tools/capture-docs.py --mcp # regenerates only the isolated MCP setup/dialog shots
```

The harness launches the analyser on the demo fixture, drives it over the localhost REST transport
(`topology`, `goto`, `flag`, `open`, `screenshot` verbs) and takes a **native** window capture so the
title bar is included. It sets the theme and resets the saved topology view first, so runs are
reproducible rather than depending on whatever state the app was left in.

The MCP dialog pair is different from the main window shots: `tools/McpSetupDocCapture.java` opens the
real Swing setup and confirmation dialogs directly. Before it does, `capture-docs.py` creates neutral
JBang and Claude executables only under `/tmp/analyser-docs`; the confirmation is photographed before
**OK**, so it cannot modify a real client configuration. This is how the visible command remains useful
without publishing a developer's launcher path.

Capturing by hand is a fallback for dialogs the harness cannot open. On macOS: **Cmd+Shift+4 → Space →
click the window**, then check every visible string before committing. Reference with a relative path and
real alt text, e.g. from a `user-guide/` page:

```markdown
```

- `start-page.png` — the start page (M36): what the analyser shows with no log open. Captured by closing the log (`open {close: "all"}`) so the shot is the real state, not a mode built for the camera.

## Capture audit — 2026-09-21

The tool-agreement branch refreshes the full 24-image demo suite, the five conversation shots
(and their recorded echoes), and four Spring project/design/validation shots. Old topology pictures
claimed off-path without complete dispatch metadata; old chart pictures put commentary and legends
over data. Both now show the changed UI. Demo files with an unterminated final record now show that
record as pending; counts in the conversations were corrected rather than editing fixture evidence.

Spring captures use the preserved public project copy with the released 1.0.73 keyless validator.
They establish design/validation rendering, not a new setup/generation or client trial.

Not recaptured in this pass: the MCP dialogs, template-picker and bundle tutorial sequence. Their
separate capture modes require their own fixtures. The owner-witnessed website Spring preview and
vendor topology picture are historical evidence, not screenshots of the new analyser. The vendor
evidence image is deliberately retained unchanged.
