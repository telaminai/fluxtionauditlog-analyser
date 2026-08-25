# Onboarding — get up to speed fast (for humans and LLMs)

A one-page orientation to this repo. Read this first, then dive into the specs for detail.

## What this is

A **Java 21 + Swing desktop app** that reads **Fluxtion event-audit logs** — the per-cycle execution
records a Mongoose/Fluxtion `EventProcessor` emits — and lets you browse, filter, summarise, **graph**,
navigate to source, and **explain** them with an LLM. Index-first, so it stays fast on multi-GB logs.
One UI dependency (FlatLaf); everything else is the JDK.

- Entry point: `telamin.fluxtion.audit.analyser.Main`
- Maven coordinates: `com.telamin:fluxtion-auditlog-analyser` (pom `<version>` is a `0.0.0-SNAPSHOT`
  placeholder; real versions are stamped at release).

## Build, test, run

```bash
mvn package                                             # build the runnable fatjar (FlatLaf shaded in)
java -jar target/fluxtion-auditlog-analyser-*.jar [log] # run it
mvn test                                                # run the unit tests
mvn -o clean verify                                     # offline full build + tests
```

`src/test/resources/sample.yml` is a representative audit log to open.

## Architecture — packages under `telamin.fluxtion.audit.analyser.analyser`

| Package | Role |
|---|---|
| `parse` | lenient, depth-aware tokenizer → `LogStore` (heap or memory-mapped) |
| `index` | compact columnar `LogIndex` for fast browse/filter/summary |
| `model` | `LogRecord`, `NodeLog` and friends |
| `filter` | `FilterState` — shared time-range + dimensions + text + group mode |
| `summary` | `SummaryBuilder` — per-dimension counts/rates |
| `graph` | `SeriesExtractor`, `Expr` (formula engine), `GraphKey`, `Series` |
| `source` | `SourceService`, `EventProcessorModel`, `SourceNavigation`, Maven source resolver |
| `llm` | prompt building + the two-way assistant action loop |
| `net` | localhost REST transport for the assistant actions |
| `config` | `AppConfig`, `ConfigStore`, `GraphSpec`, `SettingsShare` (export/import) |
| `export` | CSV/YAML record export |
| `core` | `Background` (off-EDT work) |
| `ui` | all Swing (`MainFrame`, panels, dialogs, `ChartPanel`, theming) |

Data flows: `parse` → `LogStore`/`index` → `filter` scopes everything → `summary`/`graph`/`ui` read it.

## Understand Fluxtion's execution model before touching the analyser's model of it

The analyser interprets logs a Fluxtion processor emits, so several of its behaviours are only correct if
the framework's semantics are. **Read these rather than inferring them** — every defect found in the
topology work (M21) came from inferring:

- **[`docs/claude.txt`](https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt)** in the
  fluxtion repo — the framework reference, and authoritative on semantics.
- **[golden path](https://fluxtion-playground.dev/fluxtion-golden-path.md)** — the blessed shape of an
  audited AOT graph, and the gotchas.
- **[playground CLAUDE.md](https://fluxtion-playground.dev/CLAUDE.md)** — the agent-facing guide.

The three that bite hardest here:

1. **A node appears in `nodeLogs` only if it writes audit output**, at the level in force. Silence is not
   absence of execution — most of `topology`'s `Execution` model exists for this.
2. **The boolean a handler/trigger returns is the dirty/propagation control**, and
   `@OnTrigger(dirty=false)` fires on the **inverse**. A child running proves its parent ran, not which
   way the parent answered.
3. **`@AfterEvent` and lifecycle callbacks (`@Initialise`/`@Start`) fire without upstream propagation**, so
   "something downstream logged, therefore this ran" is unsound for them. GraphML carries no annotations,
   so the analyser cannot detect them.

`examples/fixture-generator/` is a real, minimal Fluxtion project (starter-shaped, AOT via
`fluxtion-maven-plugin`) that regenerates the topology fixtures — the fastest way to see the model
behave, and to produce a paired graph + log to test against.

## Conventions that matter

- **Pure logic is unit-tested; GUI is not** — anything headless-testable has tests (parse, index,
  filter, graph, expr, config, llm parsing). Swing panels are constructed in tests but never shown; the
  CI runner is headless. Don't expect to verify UI interactions in tests — build the jar and run it.
- **Off-EDT work** goes through `core.Background`; results are applied back on the EDT.
- **Theme-aware**: colours derive from FlatLaf via `ThemeManager.isDark()` / `UiTheme`. Support light
  and dark.
- **Graphable values are TOP-LEVEL numeric/boolean nodeLog keys** (`{ key: value }`). A number nested
  inside a Java `toString()` (e.g. `price=` in `QuoteLadder(price=…)`) is text, not a key — not graphable.
- **Config** persists to `~/.fluxtion-analyser/config` (cleartext, single-user). The API key lives there
  and is never exported by `SettingsShare`.

## Process

- **This repo is PUBLIC** (since 2026-08-14, fresh single-commit history). Everything committed is
  world-readable. **Anonymisation is policy**: no real venue / vendor / book / thread / logger /
  account names anywhere — samples, fixtures, screenshots, javadoc examples all use neutral
  placeholders (`DEMO`, `marketMaker-DEMO`, `com.acme…`). Sweep before committing:
  `grep -ri "aquis\|talos\|nonco\|v12technology" --exclude-dir=target .` — four terms. It matches
  this file and CLAUDE.md by necessity (they name the terms); "clean" means no OTHER file. CLAUDE.md
  rule 1 has the exemption-free form of the check.
- **Trunk-based**: `main` is the only long-lived branch and is always releasable; short-lived feature
  branches optional (see `docs/admin/release-process.md`). `pull.rebase` is on — keep history linear.
- **Changelog is the one manual habit**: add a line under `[Unreleased]` in `CHANGELOG.md` with any
  user-visible change; the release workflow stamps it. It's bundled into the jar (Help ▸ Release notes)
  **and** injected into the docs site's release-notes page at deploy.
- **Releases**: Actions ▸ Release ▸ type a version — CI tests, stamps, tags, builds, publishes, and
  re-deploys the docs site. v1.0.0 shipped 2026-08-14. JBang users need `--fresh` to pick up a new
  release (cached stable-name jar).
- **Docs site**: **MkDocs Material** (matches mongoose-plugins; migrated from Jekyll — the
  `github-pages` gem can't run on modern Ruby). Live at
  <https://telaminai.github.io/fluxtionauditlog-analyser/>. Sources in `docs/site/`, config in root
  `mkdocs.yml`. Local preview: `pip3 install -r docs-requirements.txt && mkdocs serve`. Before pushing
  site changes: `mkdocs build --strict` must pass (CI enforces; it link-checks).

## Where to read next

- `docs/specs/spec.md` — the product spec (start here for the "what/why").
- `docs/specs/tracker.md` — **live** work items only, with the current delivery order (next up:
  **M13** MCP bridge and the **M18.0** admin-surface spike — independent tracks); finished milestones
  are archived under `docs/specs/completed/`.
- `docs/specs/spec-closed-loop.md` — the active design: agent fix handoff (M12.4) + Mongoose server
  link (M18). Its two load-bearing decisions are recorded in the tracker's Decisions: server verbs are
  **never** assistant actions, and agent fixes arrive as **evidence-linked PRs**, never direct edits.
- `docs/specs/spec-assistant-actions-mcp.md` — the M13 MCP transport design.
- `docs/specs/completed/` — shipped milestones and their design specs (history).
- `docs/admin/release-process.md`, `docs/admin/docs-site.md` — ops.

## Handoffs between sessions/machines

Work delegated to another session (human or LLM, any machine) travels through the repo:

- **Brief**: `docs/handoff/handoff_<date>_<n>.txt` — self-contained task brief (orient-first reading
  list, slices, constraints, process). Commit and **push** it; the receiving session's starting prompt
  is just "Read docs/handoff/<file> and do what it says."
- **Report**: when the work completes, the receiving session writes
  `docs/handoff/handoff_<date>_<n>_report.txt` — what shipped, what was skipped/deferred and why, and
  any spec-vs-reality mismatches found (fix the spec too; never silently diverge) — committed with its
  final change.

The paired brief/report trail makes every delegated work block auditable from the repo itself.
When a cycle is fully done (merged/shipped, review answered), its brief/report/review files move to
`docs/handoff/completed/` — the live directory holds only in-flight correspondence.

## Tracker discipline

When you finish work: mark items ☑ in `docs/specs/tracker.md`; when a whole milestone/round is done,
**move it to `docs/specs/completed/tracker.md`** and keep the live tracker to in-progress + future
only. New designs get a `spec-<name>.md`; superseded ones move to `completed/`.

## Gotchas

- The fatjar version comes from the manifest via `ReleaseNotes.version()` — use
  `getPackage().getImplementationVersion()`, NOT a classpath manifest scan (that returns a dependency's).
- Shade regenerates `dependency-reduced-pom.xml` on `package`; it's tracked, so commit or revert it
  deliberately.
