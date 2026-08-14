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

- **Trunk-based**: `master` is the only long-lived branch and is always releasable; short-lived feature
  branches optional (see `docs/admin/release-process.md`).
- **Changelog is the one manual habit**: add a line under `[Unreleased]` in `CHANGELOG.md` with any
  user-visible change; the release workflow stamps it. It's bundled into the jar (Help ▸ Release notes).
- **Releases**: Actions ▸ Release ▸ type a version — CI tests, stamps, tags, builds, publishes.
- **Docs site**: `docs/site/` (Jekyll + Just the Docs) deploys via `.github/workflows/pages.yml`.

## Where to read next

- `docs/specs/spec.md` — the product spec (start here for the "what/why").
- `docs/specs/tracker.md` — **live** work items only; finished milestones are archived under
  `docs/specs/completed/`.
- `docs/specs/completed/` — shipped milestones and their design specs (history).
- `docs/admin/release-process.md`, `docs/admin/docs-site.md` — ops.

## Gotchas

- The fatjar version comes from the manifest via `ReleaseNotes.version()` — use
  `getPackage().getImplementationVersion()`, NOT a classpath manifest scan (that returns a dependency's).
- Shade regenerates `dependency-reduced-pom.xml` on `package`; it's tracked, so commit or revert it
  deliberately.
