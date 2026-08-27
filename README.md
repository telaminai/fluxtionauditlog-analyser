# Fluxtion Audit Log Analyser

A desktop tool for reading, filtering, graphing and **explaining** [Fluxtion](https://github.com/telaminai)
event-audit logs — the machine-readable, per-cycle execution records emitted by a Mongoose/Fluxtion
`EventProcessor`.

Because a Fluxtion processor is deterministic and its graph is generated from source, each log record is
a faithful, replayable trace of one propagation cycle: the triggering event, then every node that reacted
and the state it logged. This tool turns that into a browsable history you can filter, chart, trace back
to the exact source, and hand to an LLM for a plain-English, code-grounded explanation.

> Built with Java 21 + Swing. One UI dependency ([FlatLaf](https://www.formdev.com/flatlaf/)); everything
> else is the JDK. Design notes and the full work log live in [`docs/specs/`](docs/specs).

📖 **Full documentation:** <https://telaminai.github.io/fluxtionauditlog-analyser/>

---

## Highlights

- **Fast on big logs** — an index-first design (compact columnar index; node-logs parsed lazily) keeps
  browse/filter/summarise responsive. Files above a threshold are **memory-mapped** rather than loaded
  into heap (scales past 2 GB); smaller files stay in heap.
- **Lenient parser** — the logs *aim* at YAML but node values are raw Java `toString()`s (`NaN`,
  `MutableOrder(a=1, b=2)`, `connected=true …`); a bespoke depth-aware tokenizer parses them without
  ever failing a whole file.
- **One shared filter** across the table, summary and graphs: time-range slider, event-type checklist,
  and full-text search (matches `eventToString`, `thread` **and** node-logs).
- **Per-node time-series graphing** — plot any `instanceId.key` over time (stairs/line/points; booleans
  as ±1; zoom/pan; multiple graphs; CSV/PNG export). Right-click an attribute in the detail viewer to add
  it to any named graph. Derived **formula series** (`f(x)`, e.g. `ask.price − bid.price`) with
  autocomplete over keys *and* other formulas' labels, plus an editable formula list per graph.
- **Log → source navigation** — click a node line (or Ctrl/⌘-click in the source) to open the exact
  node class/method, resolved through the EventProcessor's field declarations. Selecting a record scrolls
  the processor to its dispatch method.
- **LLM assistant** — assembles the selected record(s), the node-type map, and the relevant source, and
  asks Claude/OpenAI to explain what happened and why. No API key? It produces a ready-to-paste prompt.
  The prompt is also **seeded with the log's file path, shape and per-record byte offsets**, so an agentic
  model can grep/seek the whole file (read-behind and read-ahead from any anchor) to answer follow-ups —
  the selection is a curated starting point, not the entire evidence base.
- **MCP server** — the Start page and Assistant settings can connect Codex or Claude Code through their
  installed CLIs, or render the exact no-token JSON for Claude Desktop and any other MCP client. The
  bridge exposes the assistant's verbs as native **MCP tools**, so an AI can query the running analyser,
  plot a series and flag culprit records without a copied port or token. See
  [Connect an MCP client](https://telaminai.github.io/fluxtionauditlog-analyser/user-guide/assistant/#connect-an-mcp-client).
- **Triage aids** — anomaly row tints (parse-error / breach / NaN) with **jump-to-next-anomaly**
  (F3 / Shift+F3), a node-count bar, bookmarks/flags, and a two-record **diff** (menu or table right-click).
- **Follow / tail mode** — poll a growing local log and append newly-completed records live (auto-scroll),
  preserving flags and filters.
- **Open from S3** — `s3://bucket/key` via your local `aws` CLI (streamed to a temp file), so large
  objects use the memory-mapped path too.
- **Shareable setups** — File ▸ Export/Import settings writes a versioned `.fluxtion-settings` file
  (or clipboard / email) carrying source roots, Maven repos, event processors, **named graphs**
  (formulas and pins included), hidden columns and assistant prefs. Import merges behind a confirmable
  summary (lists add, graphs replace by name). A whitelist enforced **both ways** means the API key,
  AWS details, recent files and search history can neither leak out nor be planted on the way in.
- **Themes** — FlatLaf Light / Dark / IntelliJ / Darcula; source/record colouring adapts to the theme.

---

## Run it (released build)

No build needed — just **JDK 21+**. With [JBang](https://jbang.dev):

```bash
jbang analyser@telaminai/fluxtionauditlog-analyser [optional-log-file]
jbang app install analyser@telaminai/fluxtionauditlog-analyser   # installs an `analyser` command
```

The JBang alias and installed executable are deliberately named **`analyser`**. MCP clients use the
separate registration label **`fluxtion-analyser`** and launch that executable with `--mcp`; no rename
is required. See [Connect an MCP client](https://telaminai.github.io/fluxtionauditlog-analyser/user-guide/assistant/#connect-an-mcp-client).

Or download the latest fatjar and run it:

```bash
curl -LO https://github.com/telaminai/fluxtionauditlog-analyser/releases/latest/download/fluxtion-auditlog-analyser.jar
java -jar fluxtion-auditlog-analyser.jar [optional-log-file]
```

## Build & run (from source)

Requirements: **JDK 21+** and Maven.

```bash
mvn package
java -jar target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar [optional-log-file]
```

`mvn package` produces a self-contained runnable jar (FlatLaf is shaded in). You can also pass a log path
(local file or `s3://…`) as the first argument.

Run the tests:

```bash
mvn test
```

---

## Using it

1. **Open a log** — the toolbar (Open / Open S3), `File` menu, or drag a file onto the window.
2. **Narrow to the moment** — drag the time-range slider (drag the *middle* to pan; double-click to
   reset), tick event types in the left rail, and/or type in the search box (remembers history with
   autocomplete).
3. **Inspect** — select a record to see its full `nodeLogs` (colourised) in the detail panel. Row tints
   flag anomalies; the `nodeLogs` cell shows a bar sized by how many nodes fired.
4. **Trace to source** — click a node-log line to open that node's class at the method it ran; Ctrl/⌘-click
   identifiers in the Source tab to follow the graph; **Back** (Alt+Left / ⌘|Ctrl+[) returns.
5. **Graph** — in the Graph tab, add series (`Pick…` for a searchable multi-select), click a legend entry
   to remove one, export CSV/PNG.
6. **Explain** — select record(s) and hit **Explain** (toolbar or detail panel) to ask the LLM, or copy
   the assembled prompt for another model.
7. **Triage** — press `F` to flag rows, "Flagged only" to focus, and `Records → Diff selected two records`
   to compare two cycles.

Press **Help → User guide** in the app for the full walkthrough.

---

## Configuration

Settings live in `~/.fluxtion-analyser/config` (cleartext — a local single-user tool) and are editable via
**File → Settings**:

- **Source roots** — Java source dirs (e.g. `.../src/main/java`); a project folder is auto-expanded to its
  `src/main/java` (incl. sub-modules).
- **Maven repos** — local Maven repositories (default `~/.m2/repository`) searched for `*-sources.jar`
  when a class isn't under any source root; multiple repos supported, plus a "don't search" opt-out.
- **Event processor** — the list of candidate FQNs (add / edit / remove; double-click to edit) with one
  marked **active**; auto-inferred from the log's instanceIds, overridable.
- **LLM** — provider (Anthropic / OpenAI), model, API key, optional base URL.
- **S3** — AWS profile / region for `aws s3 cp`.
- **History** — clear search history / saved graphs / recent files (individually or **Clear all**).

On a **first run** (no config file yet) the Settings dialog opens automatically to walk you through setup.
All date/times are shown in **UTC**. Graphs, columns, flags-view, window bounds and history persist.

---

## Architecture (packages under `telamin.fluxtion.audit.analyser.analyser`)

| Package | Role |
|---|---|
| `model` | `LogRecord`, `NodeLog`, `KV`, `EventKind` — the parsed record model (lazy node-logs). |
| `parse` | Framers (`RecordFramer`, `ByteRecordFramer`), `NodeLogTokenizer`, `RecordParser`, and `LogStore` backends (`HeapLogStore`, `MappedLogStore`, `LogStores` factory). |
| `index` | Columnar `LogIndex` + `Dictionary` — the browse/filter/summary substrate. |
| `filter` | `FilterState` — the one observable filter shared by all views. |
| `summary` / `graph` / `diff` | Aggregation, series extraction, and record diffing (all pure/testable). |
| `source` | `EventProcessorModel`, `SourceService`, `SourceNavigation`, `MavenSourceResolver` — instanceId → field → FQN → file (source roots, then `*-sources.jar` fallback). |
| `llm` | `PromptBuilder`, `LlmClient` (Anthropic/OpenAI over `java.net.http`), mini `Json` codec. |
| `io` | `S3Source` — `aws s3 cp` to a temp file. |
| `config` | `AppConfig` + `ConfigStore`. |
| `ui` | Swing panels + `MainFrame`, `ThemeManager`, highlighters, chart. |

The non-UI logic is covered by unit tests against a bundled `sample.yml` (see `src/test`).

---

## Status & notes

- The log format is not strict YAML; the parser is deliberately lenient and never fails a whole file.
- Full-text search on a memory-mapped multi-GB file reads records from disk per evaluation (browse/filter
  stay index-fast).
- Deferred: index progress-percentage + cancel, LLM response streaming, graph↔table deep-link.

See [`docs/specs/spec.md`](docs/specs/spec.md) and [`docs/specs/tracker.md`](docs/specs/tracker.md) for the
design and the full history of decisions.

---

## License

© 2026 Telamin Limited. **Source-available, not open source** — see [`LICENSE`](LICENSE).

Development Use (compile, run, test, evaluate, modify) is permitted. **Production Use is not permitted**
under this license and requires a separate commercial license from Telamin Limited.
