# Fluxtion Audit Log Analyser — Work Tracker (completed)

Archived, fully-shipped milestones and refinement rounds. The **live** tracker with in-progress
and future work is **[../tracker.md](../tracker.md)**. Status keys: ☑ done · ◧ partial · ⊘ dropped.

---

## M0 · Project setup — ☑ DONE
- [S0.1] ☑ **Maven/JDK21 skeleton + Swing entry point** · `Main` sets system L&F and shows
  `MainFrame`; zero runtime deps; JUnit5 test scope; runnable jar via maven-jar-plugin
  (`Main-Class: telamin.fluxtion.audit.analyser.Main`), `mvn package` verified.
- [S0.2] ☑ **AppConfig + ConfigStore** (`~/.fluxtion-analyser/config`) · cleartext properties;
  round-trip + cleartext-key + missing-file-defaults tested (3 tests).
- [S0.3] ☑ **Background executor + EDT marshalling** · `core/Background.run(work, onSuccess, onError)`
  on a daemon pool, results delivered on the EDT.

## M1 · Parser & index (foundation) — ☑ DONE (24 tests green)
- [P1.1] ☑ **RecordFramer** streaming `---` boundaries → `[offset,length]` slices · CRLF/LF,
  blank lines, no‑trailing‑separator, offset round‑trip all tested; sample → 21 records.
- [P1.2] ☑ **HeaderParser** + scalar fields · `eventToString` never re‑split on inner `:`;
  `eventTime -1`→null; missing header tolerated.
- [P1.3] ☑ **NodeLogTokenizer** (§4.2) depth‑aware split · `MutableOrder(a=1, b=2)`,
  `[demoRfqOrders]`, `NaN`, space‑separated `venueStatus`, duplicate instanceIds, unbalanced
  braces (no throw) all covered.
- [P1.4] ☑ **Derived dimension** (§6) · method‑sig→callback (+declaringType) else event; verified
  on `orderVenueConnected`/`ScheduledTriggerNode`/`LifecycleEvent`.
- [P1.5] ☑ **LogIndex (columnar) + Dictionary** · parallel primitive arrays; interns
  dimension/logger/thread; min/max logTime; per‑dimension counts (ExportFunctionAuditEvent grouped
  by callback).
- [P1.6] ☑ **LogStore + HeapLogStore** · index built in one streaming pass; `record(row)`
  re‑slices + parses on demand; `nodeLogs()` lazy + memoised.
- [P1.7] ☑ **Parser test corpus** · `sample.yml` on the test classpath + golden assertions across
  4 test classes (framer/tokenizer/parser/store).

**Classes delivered:** `model/{EventKind,KV,NodeLog,LogRecord}` ·
`parse/{RecordFramer,RawRecord,HeaderParser,RecordHeader,NodeLogTokenizer,EventDimension,RecordParser,LogStore,HeapLogStore}`
· `index/{Dictionary,LogIndex}`.

## M2 · Table + detail viewer — ☑ DONE (GUI compiles; model path tested headless)
- [U2.1] ☑ **Virtual `JTable` model over LogIndex** · 8 columns, fully index-backed (event/
  eventToString/groupingId added to the index so scrolling never parses); sortable; multi-select.
- [U2.2] ☑ **Date/number renderers** · times rendered `yyyy-MM-dd HH:mm:ss.SSS` **UTC**;
  `eventTime -1`→blank.
- [U2.3] ☑ **nodeLogs preview cell** · shows `N nodes` over a relative-size **bar** (count ÷ max, from
  the index; round 5). Full instanceIds are in the detail viewer.
- [U2.4] ☑ **DetailPanel + YamlHighlighter** · read-only colourised record; multi-select concatenates
  with `---`. (Right-click menu lands in M4.)
- [U2.5] ☑ **Row anomaly cues** · parse-error / breach / NaN tinted, from index-resident flags
  (computed cheaply during the index pass, no node-log parse).

**Classes delivered:** `core/Background` · `config/{AppConfig,ConfigStore}` ·
`ui/{TimeFormat,LogTableModel,YamlHighlighter,DetailPanel,LogTablePanel,MainFrame}` · `telamin.fluxtion.audit.analyser.Main`
· index extended (event/eventToString/groupingId/nodeLogsCount/flags). Tests: 31 green.

## M3 · Filters & summary — ☑ DONE (40 tests green)
- [F3.1] ☑ **FilterState + wiring** · one observable filter (time range + dimensions + text + group
  mode) drives table (`RowFilter`), summary and event checklist; listeners propagate live; 6 tests.
- [F3.2] ☑ **EventFilterPanel** checklist w/ counts + group‑by toggle (callback/event ↔ raw event) ·
  All/None; syncs when cross‑filtered from the summary (ActionListener avoids feedback loops).
- [F3.3] ☑ **Text filter** (eventToString/thread), debounced 250 ms, case‑insensitive.
- [F3.4] ☑ **TimeRangeSlider** custom two‑thumb draggable picker · spans index min/max, live UTC
  labels, updates the filter on drag, syncs back on external change. (Numeric entry + density
  histogram deferred — nice‑to‑have.)
- [F3.5] ☑ **SummaryPanel** grouped by dimension · count/first/last(UTC)/span/rate; row click
  cross‑filters the whole app; rebuilds on filter change; 3 tests.

**Classes delivered:** `filter/FilterState` · `summary/{SummaryRow,SummaryBuilder}` ·
`ui/{EventFilterPanel,TimeRangeSlider,SummaryPanel}` + `MainFrame` filter bar & right‑hand
tabs + `LogTablePanel` row‑filter/sorter. Tests: **40 green**.

## M4 · Source resolution & viewer — ☑ DONE (7 tests green)
- [C4.1] ☑ **EventProcessorModel** — field/import scan → `instanceId→FQN`; generics/array stripped;
  same-package fallback; `coverage()` for inference. 3 tests.
- [C4.2] ☑ **SourceRootResolver** FQN→File over roots; graceful miss.
- [C4.3] ☑ **Discovery + inference** — `SourceService.discover` scans the strategy package;
  `infer` scores candidates by instanceId coverage (decision) with fallback to the default;
  overridable in Settings. 4 tests.
- [C4.4] ☑ **SourcePanel + JavaHighlighter** — read-only colourised source; processor picker;
  **right-click a node instanceId in the detail viewer → opens its declaring class** and switches to
  the Source tab.
- Extra: **ConfigPanel** (Settings) for source roots (add / remove / **drag-drop folders**),
  EventProcessor FQN, and LLM settings — all persisted (improvements.md #2/#3).

## M5 · LLM — ☑ DONE (7 tests green)
- [L5.1] ☑ **Json** mini codec (reader+writer, path navigation) — 4 tests.
- [L5.2] ☑ **PromptBuilder** — bundled system prompt + record context + budgeted source snippets;
  same builder feeds API and copy modes. 3 tests.
- [L5.3] ☑ **LlmClient** (Anthropic + OpenAI) over `java.net.http`; provider factory + default models;
  non-fatal errors.
- [L5.4] ☑ **Conversation + LlmPanel** — two-way chat, **Reset**, context attached to first turn.
- [L5.5] ☑ **No-key copy mode** — Copy prompt (and Send with no key) produces the full prompt to the
  clipboard.
- [L5.6] ☑ **Bundled `llm/system-prompt.md`** explaining Fluxtion audit semantics.
- [L5.7] ⊘ streaming (deferred).

## M6 · Graphing — ☑ DONE (5 tests green)
- [G6.1] ☑ **SeriesExtractor** — `GraphKey(instanceId,key)` → points; last-occurrence per record;
  booleans → ±1; **NaN/non-finite omitted** (round 5); streams over the store. `discover()` finds
  numeric+boolean keys. 5 tests.
- [G6.2] ☑ **Series key picker** — combo from discovered keys; add / **remove individual** / clear;
  multiple graph panels (`GraphTabs`) for comparisons (round 5).
- [G6.3] ☑ **ChartPanel** — custom multi-series chart, auto-scale, legend, UTC axis (no charting dep);
  **stairs/line/points** styles, **zoom/pan** (precise-wheel; +/−/Fit buttons) and hover tooltip
  (rounds 2/4/5).
- [G6.4] ☑ **CSV export** of active series. (Graph↔table deep-link still deferred — nice-to-have.)

**Improvements.md folded in:** log-file **drag-and-drop** onto the window (#1); source roots + EP +
all paths **persisted** to config, drag-drop folders in Settings (#2/#3); table columns — added
**callback**, blanked `event` for `ExportFunctionAuditEvent`, short `Class.method` in place of the
full signature, `eventToString` optional (#4). Search now also covers **nodeLogs** and combines
with the other filters.

## Refinements (round 2) — ☑ DONE (66 tests green)
- **LLM: show the prompt** — Copy prompt (and no-key Send) now opens a resizable, selectable dialog
  with the full prompt + a Copy button (still auto-copied to the clipboard).
- **LLM: richer context** — the prompt now includes a **Node types** section (each nodeLogs
  `instanceId → declared field type`, resolved from the loaded EventProcessor) and the
  **EventProcessor source** itself, in addition to the node-class snippets. 1 new test.
- **Source roots: detection + guidance** — `ConfigPanel.detectSourceRoots` accepts a **project dir**
  and finds its `src/main/java` (incl. sub-modules), or takes an already-correct source root;
  non-source folders are flagged red in the list and warned on add. Clearer help text. 4 new tests.
- **Graph: zoom/pan** — mouse-wheel zoom (Shift = X-only, Ctrl = Y-only), drag to pan, double-click
  to fit, hover tooltip (time + value); view bounds decoupled from data bounds.
- **Graph: booleans** — `KV.graphValue()` maps `true`/`false` → `+1.0`/`-1.0` (symmetric around zero
  so flips are obvious); extractor and key discovery include boolean keys. 1 new test.

## Refinements (round 3) — ☑ DONE (70 tests green)
- **Click-to-source navigation** (`SourceNavigation`, 4 new tests):
  - selecting a record scrolls the Source tab's EventProcessor to the **dispatch method** (the
    record's callback);
  - **click a node-log line** in the detail viewer → opens that node's class scrolled to the method
    it ran (the line's first key), resolved instanceId → EP field → type → file;
  - **Ctrl/⌘-click** in the source viewer → `receiver.method()` opens the node's method, a field opens
    its type, a Type opens its source (using the shown file's own fields/imports).
- **App icon + splash** — `AppImages` draws the icon (all sizes, via `setIconImages`) and the
  `SplashScreen` banner at runtime; no image files, shown briefly at launch.

## Refinements (round 4) — ☑ DONE (70 tests green)
- **Graph wheel-zoom fixed for trackpad/Magic Mouse** — uses `getPreciseWheelRotation()` with a
  clamped per-event factor (consistent direction, no runaway from momentum); added **+ / − / Fit**
  zoom buttons as a reliable alternative.
- **Plot styles** — default **Stairs** (step; values hold until next update), plus **Line** and
  **Points**, chosen from a style dropdown.
- **Node-log viewer Wrap toggle** — checkbox; off → horizontal scroll for long lines.
- **EDT + thread exception reporting** — `ExceptionHandling.install()` sets a default uncaught handler
  and pushes a wrapping `EventQueue` so EDT-dispatch errors are printed to the console too.
- **Richer Java colouring** — distinct hues for keywords, types, methods, constants, numbers,
  annotations, strings and comments.
- **Source Back navigation** — history stack with a **Back** button and **Alt+Left** / **Cmd|Ctrl+[**
  key bindings.

## Refinements (round 5) — ☑ DONE (71 tests green)
- **Range slider: pan the window** — grabbing the middle of the selected span drags the whole range,
  preserving its width (start+end move together); cursor hints (resize on thumbs, move on the span).
- **S3 connector** — `io/S3Source` streams `s3://bucket/key` via the local `aws` CLI
  (`aws s3 cp … - --quiet`, stderr drained to avoid deadlock); **File → Open from S3…**, AWS
  profile/region in Settings, recent-files reopen s3 URIs. Zero new deps (reuses the user's aws auth).
- **Custom app icon + Dock icon** — `AppImages` drawing used for the window icon *and* `Taskbar`
  (replaces the Java "Duke" on the macOS Dock / taskbar).
- **Graph: remove individual series** — a "Shown" combo + Remove (plus Clear).
- **Graph: multiple panels** — `GraphTabs` (New graph / Close graph) for side-by-side comparisons,
  each bound to the shared filter; `FilterState.removeListener` for clean teardown.
- **Table column show/hide + persistence** — View→Columns checkboxes; hidden by default:
  eventTime, groupingId, eventToString, endTime. Stored in config.
- **endTime parsing fix** — end-of-nodeLogs detection is now indentation-independent, so `endTime`
  (the only scalar after nodeLogs) parses even when scalars are indented > 2 spaces. Regression test.
- **nodeLogs count bar** — the nodeLogs cell draws a translucent bar sized by count ÷ max, so
  heavy-fan-out cycles stand out at a glance.
- **NaN in graphs = no data point** — non-finite values are omitted entirely (not a gap, not a parse
  failure). Test updated.
- **Back-to-EventProcessor scrolls to the dispatch method** for the current record's callback.

## Refinements (round 6) — ☑ DONE (74 tests green; uber-jar bundles FlatLaf)
- **Repaint bug fix** — removed the custom `EventQueue` push (interfered with the macOS paint/validate
  pipeline → uncommitted splitter drags, partial text wrap); EDT exceptions still print by default.
  Also `setContinuousLayout(true)` on the splitters.
- **Range → focus/context** — selecting a range shows the **final** record in detail; the whole range
  feeds the LLM (last = "record to explain", earlier = preceding context).
- **Professional L&F: FlatLaf** (MIT, ~1 MB, the one runtime UI dep) with **Light/Dark/IntelliJ/Darcula**
  themes (Theme menu, persisted); bundled into the runnable uber-jar via shade.
- **Theme-aware rendering** — YAML + Java highlighters and the chart pick light/dark palettes and
  re-render on theme change, so text/code stay readable on any theme.
- **Custom Dock/taskbar icon** via `Taskbar` (no Java "Duke").
- **Graph**: precise-wheel zoom (trackpad/Magic-Mouse safe) + +/−/Fit buttons; **stairs/line/points**;
  booleans → ±1; **NaN omitted** (no point); **remove individual series**; **multiple graph tabs**;
  **PNG export**; **saved graphs persist** in the profile and reopen on load; **searchable multi-select
  Pick…** dialog to add several series.
- **Records**: **CSV/YAML export** of the filtered set; **bookmarks/flags** ('F') + flagged-only filter.
- **Search box**: history with **inline autocomplete** + dropdown; **Settings → History** clears
  search history / saved graphs / recent files.
- **LLM prompt**: adds **source roots + resolved file paths** (for agentic exploration) alongside the
  embedded snippets and node-type map; **Copy prompt** now prints the full prompt into the transcript
  bracketed by clear "COPIED — NOT sent" markers (and to the clipboard).
- **S3**: open `s3://…` logs via the local `aws` CLI (round 5) — profile/region in Settings.

_Note: the "zero runtime dependencies" goal now has one deliberate exception — FlatLaf (UI only),
added at the user's request for a professional themed look._

## M7 · Large‑file mode — ☑ DONE (80 tests green)
- [M7.1] ☑ **MappedLogStore + ByteRecordFramer** — streaming byte-offset framer (64-bit offsets,
  scales past 2 GB) builds the index without loading the file; records read on demand from a
  `FileChannel` + LRU cache. Verified byte-for-byte against `HeapLogStore` on the sample (incl. CRLF).
- [M7.2] ☑ **`LogStores.open` threshold auto-select** — ≤ `memoryThresholdMb` (default 500) → heap,
  else memory-mapped (0 → always mapped); used by local and S3 opens; previous store closed on reload.
- [M7.3] ◧ **Background load** keeps the UI responsive with a "Loading…" status; explicit
  progress‑percent + cancel deferred (indexing a multi‑GB file runs off‑EDT and can't be cancelled yet).
- **S3 → temp file** — `S3Source.fetchToFile` streams `s3://…` to a local temp file via the `aws` CLI
  (never into heap), which then goes through `LogStores.open`, so **large S3 logs get the mmap path**
  too. Temp file deleted on exit. 2 tests.

## M9 · UX pass (discoverability) — ☑ DONE (82 tests green)
- [U9.1] ☑ **Toolbar** — Open, Open S3, Flag, Flagged-only (toggle), Explain, Export CSV, all with
  tooltips (`JToolBar` above the filter bar).
- [U9.2] ☑ **Explain button** on the detail panel (and toolbar) → jumps to the LLM tab and primes a
  default question with the selection as context.
- [U9.3] ☑ **Event-types moved to a left rail** (WEST); right tabs are now pure outputs
  (Summary / Source / Graph / LLM).
- [U9.4] ☑ **Feedback** — detail bar shows "N records selected — last shown here; all sent to the LLM";
  slider tooltip + duration label; indeterminate **progress bar** in the status bar during loads;
  empty-state hints in the table and detail panel.
- [U9.5] ☑ **Clickable legend** removes a series (dropped the "Shown" combo; kept Add / Pick… / Clear).
- [U9.6] ☑ **Record diff** — `DiffBuilder` (2 tests) + `RecordDiffDialog`: Records → "Diff selected two
  records" shows a side-by-side table with changed / only-A / only-B rows highlighted.
- Extra: **time slider polished** — density histogram behind the track (in-range brighter), duration
  label, centre grip (pan affordance), double-click to reset, theme-aware colours.
- Extra: **empty state** — the records table paints an "open a log" hint when nothing is loaded.

## Refinements (round 12) — ☑ DONE (144 tests green)
- **Graph action wasn't plotting valid keys (bug fix).** The `graph` action resolved series names by
  enumerating keys via `SeriesExtractor.discover`, which is **front-truncated** (first ~20k records only),
  and then `addKeys(resolved)` added **only** the discovery-resolved keys — so a valid `instanceId.key`
  whose node first fires later (or fires rarely, e.g. a locked-book flag) was reported "unresolved" and
  **never added**, even though `extract` scans the whole log and could plot it. Fixes:
  - **Add every well-formed key/formula regardless of discovery** (`GraphKey.fromDisplay` splits
    `instanceId.key` on the first dot) — extraction is whole-log, so a real-but-rare key now plots.
  - **`Expr.parse(String)`** (no known-set) resolves refs by the same split, so derived series parse and
    extract **without** the front-truncated discovery gate (removed from `GraphPanel` re-extract, the f(x)
    field, and CSV export).
  - **`SeriesExtractor.resolveExisting`** — a *targeted, whole-log, early-exit* check used only for accurate
    action feedback; the echo now carries `resolved`/`unresolved`, per-expr `unseenRefs`, a sample of real
    `availableKeys`, and a note that **graphable keys are top-level numeric/boolean nodeLog values**
    (a price nested in `MutableOrder(price=..)` is not itself a key) — the likely cause of the agent's
    unresolved names. _New tests: `ExprTest` (fromDisplay + no-known-set parse), `SeriesExtractorExprTest.resolveExistingFindsAKeyThatOnlyFiresLate`._

## Refinements (round 11) — ☑ DONE (125 tests green)
- **Graph click → nearest record** — clicking the plot selects the record whose `logTime` is closest to
  the clicked time and scrolls the table to it (`ChartPanel.setOnPlotClick` → `GraphTabs.setTimeClickHandler`
  → `MainFrame.gotoNearestRecordByTime`). Reports if the nearest record is filtered out of the view.
- **Main-screen layout** — the filter controls are now one **titled "Filter" group**: **Search** (top-left),
  **showing N of M** (centred above the slider), **Window** combo (top-right), the slider, then the pan bar
  — search no longer floats disconnected, and the count sits over the time bar.
- **Slider selection labels** — the from/to are drawn **centred under the thumb ends** (the blue drag bar),
  **rounded to the window granularity** (seconds ≤3 min, minutes ≤3 h, day+minutes ≤3 d, else date) — so
  they read as the *selected window*, not absolute log bounds.
- **Theme-switch NPE fix** — `updateComponentTreeUI` briefly sets a combo's editor to null during
  `BasicComboBoxUI.uninstallUI`, firing our `"editor"` property listener while `getEditor()` was null →
  NPE that aborted the whole tree update (and cascaded into a FlatLaf `arrowButton` paint NPE).
  `HistoryComboBox.editor()`/`getText`/`setText`/`complete` are now null-safe on the teardown path.
- **`doGraph` series resolution** (reviewer #1/#2) — resolve requested `instanceId.key` against the
  **whole log** (a neutral filter), not the filtered view, so a key outside the active window still
  resolves; and echo `discoveryLimited` when the ~20k-record discovery cap was hit, so
  unresolved-because-unscanned is distinguishable from unresolved-because-typo (no silent caps).
- **Graph smoothness — decoupled extraction + decimated paint** (replaces the earlier throttle, which
  still froze because each redraw re-parsed the whole log). Root cause was two O(N) costs on the
  time-drag hot path: `extract` re-parsed **every record's node-logs** on each redraw (time bound baked
  into the extraction filter), and `drawSeries` was **O(points) on the EDT** (a `drawLine`+`fillOval` per
  point). Fixes:
  - **Extract once, across all time** (`FilterState.testExceptTime` + `SeriesExtractor.extract(…, acrossAllTime=true)`);
    a **time-only** filter change now just **windows the cached series** via `ChartPanel.setViewWindow`
    (no parsing). Re-extraction happens only on a **structural** change (dimensions/text/keys),
    **debounced** (`extractDebounce`, 200 ms) and **generation-guarded** (stale results dropped).
  - **Decimated drawing** — series denser than ~3×plot-width downsample to one representative point per
    pixel column and render with the **same STEP/LINE/POINTS logic** (a faint min/max envelope behind
    preserves spikes), so paint is **O(plot width)** not O(points) *without* changing how a style looks
    (an earlier envelope-only version was a regression — stairs/line/points all looked like a line).
  Net: dragging the time slider/window re-parses nothing and repaints in O(plot width) — no EDT freeze.
  `SeriesExtractorTest.acrossAllTimeIgnoresTheTimeWindow…` locks the decoupling. 126 tests green.
  - _Known residual (deferred): `ChartPanel.setViewWindow` rescans every point to autoscale Y on each drag
    tick — O(points), no parse/paint. Invisible until a **single** series covers ~every record of a
    multi-million-record log, where it'd show as drag lag. Fix if it bites: a coarse time-bucketed min/max
    Y index built once at extraction (query O(buckets) per tick), or fix Y during the drag and refit on
    settle. Not built — proportionate to the rare trigger._

## Refinements (round 7) — ☑ DONE (82 tests green)
- **Dark-theme contrast** — slider drag-bar/label text brightened for dark themes; anomaly/flag row
  tints in the records table are now theme-aware (light tints on light themes, dark tints on dark so the
  light foreground text stays readable): ERR / BREACH / NaN / FLAG each have a light+dark variant chosen
  via `ThemeManager.isDark()` in `LogTablePanel.prepareRenderer`.
- **Time-window granularity** — the slider now separates an *outer visible window* from the *selection*.
  A **Window** combo (All / 1 week / 1 day / 6h / 1h / 30m / 15m / 5m / 1m) shrinks the visible span and a
  **pan scrollbar** below the slider moves that window across the full log range, so a ~40-minute window of
  interest isn't a sliver of a ~50-hour log. `TimeRangeSlider` gained `absMin/absMax` + `setWindowMillis`,
  `setWindowStartFraction`, `isWindowed`, `windowSpanFraction`, `windowStartFraction`; the density histogram
  is now positioned by **time** (window-aware) not array index; `publish()` nulls the bound only at the
  absolute edges; double-click resets window **and** selection.
- **Right-click on the records table** — Diff (enabled only with exactly 2 rows) / Explain / Flag, mirroring
  the Records menu; right-clicking an unselected row selects it first.
- **Settings dialog redesign** — replaced the blocky `GridLayout(0,2)` tabs with a shared two-column
  `GridBagLayout` form (right-aligned labels, fields that grow to fill, muted help notes, top-pinned rows);
  EP tab no longer a cramped `FlowLayout`; History buttons left-aligned + uniform width; right-aligned
  button bar with **OK as default** (Enter) and **Esc to cancel**; consistent 14/16px tab padding.

## Refinements (round 8) — ☑ DONE (85 tests green)
- **Search bug fix (theme change)** — `updateComponentTreeUI` (fired on every theme switch) recreates the
  editable combo's editor (new `JTextField` + `Document`), which stranded the autocomplete `DocumentFilter`
  and the debounce listener on the discarded document and left `getText()` reading a stale editor — so
  search silently died after any theme change. `HistoryComboBox` now resolves the editor dynamically and
  re-installs the filter + change bridge on every editor swap (`propertyChange("editor")`).
- **Memory threshold in Settings** — the heap-vs-mmap cutoff (`memoryThresholdMb`) is now a spinner in
  Settings → *Performance & S3* (was hand-edit-only); closes the M7 loop. Applies to the next file opened.
- **Anomaly navigation (H8.4)** — ⚠ Next / ⚠ Prev toolbar + F3 / Shift+F3 select the next/previous
  anomaly row (parse-error / breach / NaN), wrapping, over the current filtered/sorted view.
- **Window follows selection** — dragging a selection thumb to the visible-window edge now auto-pans the
  window (Swing timer) so you can extend the selection past the current window; the pan bar stays in sync
  via a `setWindowChangeListener` callback.
- **Follow / tail mode (H8.7)** — File → "Follow (tail)" / toolbar toggle polls the open **local** file
  (~1 s) and appends newly-*completed* records live (auto-scroll to newest), preserving flags/filter/
  selection. Built on `RecordFramer.frame(…, requireTerminator)` (a record isn't indexed until its
  closing `---` arrives) + `HeapLogStore.appendFrom` (append-only re-read; returns count / 0 / −1 for
  shrink-rotation → reload) + `LogTableModel.rowsAppended` + `TimeRangeSlider.extendAbsMax`. Heap-loaded
  local files only (not S3 / not mmap). 3 new `FollowAppendTest` cases.

## Refinements (round 9) — ☑ DONE (88 tests green)
- **LLM whole-log access (agentic retrieval)** — the prompt is now seeded with the audit log's file path,
  shape (human size, record count, UTC time span) and framing description, plus each selected record's
  **byte offset + length + logTime**, so a file-reading model can grep/seek the log in **both directions**
  from an anchor (read-behind for causes, read-ahead for consequences) and count occurrences — the curated
  selection becomes a starting point, not the whole evidence base. `PromptBuilder.recordContext` gained a
  `LogFileInfo` overload (path/localPath/size/count/min/max); `LogStore.localFile()` exposes the real local
  file (the fetched **temp file** for S3, so grep targets a readable path); the system prompt tells the
  model to use it. Inert text for non-agentic targets — no toggle, same graceful degradation as source
  roots. Byte offsets were already in the index, so this is a thin seam. 4 new `PromptBuilderTest` cases.

## M8 · Polish, help, extras
- [H8.1] ☑ **HelpPanel (HTML)** — logs, columns, dimension, filtering/search, summary, graphing,
  source, LLM, config &amp; perf · bundled `/help/help.html`, opened via Help &rarr; User guide.
- [H8.2] ☑ **Export** rows→CSV/YAML (`RecordExporter`, filtered; YAML re-parses) + series→CSV + graph→PNG.
- [H8.3] ☑ **Bookmarks/flags** — 'F' toggles a flag on selected rows (tinted); Records → "Show flagged
  only" filter + Clear all flags.
- [H8.4] ☑ **Anomaly navigation** — ⚠ Next / ⚠ Prev toolbar buttons + F3 / Shift+F3 jump to the next /
  previous parse-error / breach / NaN row (wraps; respects filter/sort). Round 8.
- [H8.5] ☑ **Record diff** side‑by‑side (M9/U9.6; also on the table right-click, round 7).
- [H8.6] ◧ **Column show/hide** ☑ (round 5), **recent files** ☑ (config), **window persist** ☑ (M0);
  **copy‑row‑as‑YAML** still ☐.
- [H8.7] ☑ _(stretch)_ **Follow/tail mode** — round 8.

## M10 · Assistant actions — two‑way curation loop — ☑ DONE (all slices, 125 tests green)
_Design: **[spec-assistant-actions.md](spec-assistant-actions.md)**. Completes the LLM loop: the app
seeds file access (round 9); this adds the return path so the assistant can **compute over the index**
(query verbs) and **build curation** in the UI (render verbs). App curates, model retrieves + reasons.
Two peer‑review rounds folded in: `text` filter = honest raw‑scan (`scan:index|raw`); agent loop
auto‑resends **results and errors** with a **round cap (3)** + per‑reply action cap (20); reject **any**
`Origin` header + REST token‑bucket; snapshot guard captures **columns AND dictionary copies** vs
follow‑mode appends; `flag` verb in v1 (note = small new store); worked examples split index‑path
(`onMultilevelMarketData`) vs raw‑path (`failedValidation` is a nodeLogs key, not a dimension)._
- [A10.1] ☑ **Action schema + `ActionDispatcher`** · `ActionResult` + `ActionFilter` over `llm.Json`
  (dispatcher parses the request map directly — no separate `ActionRequest` type); validates
  version/token/verb; routes to handlers; structured `ok:false` (never throws). _(Per‑reply cap lands
  with the loop in A10.3, where a "reply" exists.)_
  _Done: `ActionDispatcherTest` (7) — version mismatch, missing/wrong/correct token, unknown verb, missing action, render‑not‑enabled, malformed JSON._
- [A10.2] ☑ **`AggregateService` + `LogIndex.snapshot()` (query, read‑only)** · count / rate_per_min /
  nan_count / breach_count; groupBy dimension / thread / hour / minute / **day**; optional filter that
  reports `scan:index|raw` (dimension/flag = O(index); `text` = streaming raw pass); result echoes its
  population; defined rate denominator; **snapshot guard captures columns + `Dictionary.copyValues()`**
  so off‑EDT reads don't tear vs follow‑mode `appendFrom` (`add` synchronized).
  _Done: `AggregateServiceTest` (9) + `LogIndexSnapshotTest` (2, incl. concurrent new‑dimension append)._
- [A10.3] ☑ **In‑process executor + bounded agent loop** · `LlmPanel.runRound` parses **exact‑tag**
  ```analyser-action``` blocks (`ActionParser`; `-example` + other fences ignored), runs them via an
  in‑process `ActionDispatcher` (fresh snapshot per round), **auto‑resends query AND error results** with
  a `maxActionRounds`=3 cap + **per‑reply action cap (20)** + **Cancel** + "↻ round n/m" markers; results
  shown as a **visible, marked** transcript turn. No open port. `PromptBuilder.inProcessActionManifest`
  seeds the verbs (no URL) on the first turn. Config `assistantActionsInProcess` (default **on**) +
  `maxActionRounds`/`maxActionsPerReply` persisted. _(Settings UI toggle is A10.6.)_
  _Done: `ActionParseTest` (5) — none/one/many/malformed; example‑fence + json‑fence ignored._
- [A10.4] ☑ **Render verbs** (`filter`, `graph`, `goto`, `flag`) via `ui/ActionExecutor` (implements the
  UI‑free `llm/RenderExecutor` seam; dispatcher delegates render verbs to it). Bridges to `FilterState`
  (echoes **full** resulting state; missing=unchanged/null=cleared via `containsKey`), `GraphTabs`
  (**named** graphs: `graphForAction`/`renameNamed`; series resolved against `SeriesExtractor.discover`,
  echoing **resolved‑vs‑unresolved**; style by name), table select+scroll (`selectModelRow`), and the flag
  store (+ **note** map + hover tooltip). Offsets **floor‑resolve + clamp** to the containing record and
  echo the resolution; `recordIndex(es)` accepted too. All UI mutation **EDT‑marshalled** (`invokeAndWait`).
  Loop resend condition refined: resend on a **query result or any error**, never a render success
  (reviewer #1); manifest attaches on the first **enabled** turn (#3); manifest now advertises all verbs.
  _Done: `GotoResolveTest` (4) — exact/mid‑record floor, out‑of‑range clamp, recordIndex clamp._
- [A10.5] ☑ **localhost REST transport** · `net/ActionServer` (JDK `HttpServer`, `127.0.0.1`, ephemeral
  port) with `POST /action` + `GET /manifest` (**advertises verbs + caps**); same dispatcher (+ render
  executor); **`X‑Analyser‑Token` header guard + reject any `Origin` + token‑bucket rate limit (10 req/s →
  429)**. **Opt‑in** (default **off**); when on, MainFrame starts it, shows URL+token in the status bar/
  console, and the copy‑prompt seeds `restActionManifest(url, token)`. Per‑run UUID token.
  _Done: `ActionServerTest` (6) — loopback bind, `/manifest` shape, missing/wrong token 401, any‑Origin 403, 429._
- [A10.6] ☑ **Config + Settings** · `assistantActionsInProcess` (on) / `assistantActionsRest` (off) /
  `maxActionRounds` (3) / `maxActionsPerReply` (20), persisted; a new **Assistant** tab in Settings with the
  two toggles + the two spinners + an explainer; REST start/stop applied on save. _(Live port/token shown
  in the status bar rather than the dialog.)_
- [A10.7] ☐ _(deferred)_ **`aggregate` over `nodeLogs` numeric keys** (needs per‑record node‑log parse);
  **index‑time text flags** for O(index) anomaly counts (would make the `failedValidation` headline case
  index‑fast); flag‑note **persistence** across reload.

### Named graphs (landed ahead of the render slice)
- [A10.8] ☑ **Graphs are named** — tab title is the name; **Rename…** button + double‑click to rename;
  `config/GraphSpec` (name + series) persists names in the profile (round‑trip tested); `GraphTabs`
  gains `addGraph(name)` / `graphNamed` / `graphForAction(name,newTab)` / `renameSelected` so the future
  `graph` action can build‑then‑refine a graph by name. The named series definition is the **promotion
  contract** to monitoring (M11). _Done: `ConfigStoreTest.savedGraphsRoundTripWithNames`._

## M14 · Graph artifacts — pinned range + derived series — ☑ DONE (M14.1–M14.5; v2.0 windowed transforms deferred; 141 tests green)
_Design: **[spec-graph-artifacts.md](spec-graph-artifacts.md)**. Turn LLM-built graphs from views into
artifacts (pinned window + formula-defined series) that survive filter changes, persist, and promote to
Grafana (M11). Both slot into existing seams — `GraphSpec`, the `graph` verb, `SeriesExtractor`._
- [M14.1] ☑ **Pinned range** · `GraphSpec` gains nullable `from`/`to` (null = follow; set = pin);
  `GraphPanel` holds a logical `graphName` + pin state and windows via `ChartPanel.setViewWindow`
  (`applyWindow`: pinned → fixed window, ignores filter time changes; following → filter window). A **📌
  toggle** pins to the current filter window — or the effective data bounds when the filter is unbounded —
  and the tab shows a **📌** prefix + tooltip; the `graph` verb's `from`/`to` now **pin** and are echoed;
  `ConfigStore` persists `graph.i.from/to` (backward compatible). _Done: `ConfigStoreTest.savedGraphsRoundTripPinnedWindow`;
  pinned-ignores-filter verified by compile + manual._
- [M14.2] ☑ **Expr engine** `graph/Expr` · sealed AST (Num/Ref/Neg/Bin/Call) + recursive-descent parser
  (refs `instanceId.key` resolved to `GraphKey` at parse time, numeric literals, `+−×÷`, parens,
  `abs`/`min`/`max`) + evaluator. **Hermetic** (no eval/SDK — zero-dep), serializable, portable to Grafana.
  Lexer takes **ASCII and Unicode** operators (`−`/`×`/`÷`); unknown ref error **names the ref + suggests
  the nearest** (edit distance); div-by-zero / missing ref → NaN (omitted point); backtick escape for odd
  keys; `refs()` lists referenced keys for the extractor. _Done: `ExprTest` (9)._
- [M14.3] ☑ **Derived extraction** · `SeriesExtractor.extractExpr(store, filter, Expr, label, acrossAllTime,
  Resolve)` in **row order** with **LOCF** + `STRICT`; carry rule implemented: **finite updates, explicit
  NaN/non-numeric clears** (no fabricated continuity), absent leaves unchanged, point only on a record
  touching ≥1 ref; non-finite result omitted; `Series` gained a **display label** (+ nullable key for
  derived). _Done: `SeriesExtractorExprTest` (4) — LOCF carry + NaN-clears (omits the NaN record, resumes
  after), strict co-occurrence, missing-ref omits all, single-ref exactness._
- [M14.4] ☑ **Action wiring** · `graph` verb `exprs:[{label,expr,resolve?}]` parsed against discovered keys,
  echoed **per-expr ok/error** (parse error names the ref); `GraphPanel` holds derived series (re-extract
  parses off-EDT + generation-guarded), legend/CSV show the **label**, legend-click removes raw or derived
  by label; `GraphSpec.ExprSpec` + `ConfigStore` persist formulas (backward compatible); manifest documents
  exprs + pinning. _Done: `ConfigStoreTest.savedGraphsRoundTripDerivedFormulas`._
- [M14.5] ☑ **UI** · 📌 pin/unpin (M14.1) + a second graph toolbar row with a plain **`f(x)` field** (+ label
  + locf/strict), which discovers keys off-EDT, parses with the **same inline error** the LLM path returns,
  and adds the derived series (Enter or "Add f(x)"). Agent-first: the UI authors simple formulas, the LLM
  the gnarly ones. _Verified by compile + manual pass._
- [M14.6] ☐ _(v2.0)_ closed-set windowed transforms `delta`/`dt`/`rate`/`rolling(x,w,agg)` as pure
  `Series→Series`, **top-level wrap only** (O(n) each). _(v2.1: series-level arithmetic + alignment — separate.)_

## Refinements (round 12) — ☑ DONE (152 tests green)
- **Package move** — the code moved from `com.acme.analyser` to `telamin.fluxtion.audit.analyser.*`
  (`Main` at `telamin.fluxtion.audit.analyser.Main`, app packages under `…analyser.analyser.*`); maven
  coordinates are now `com.telamin:fluxtion-audiitlog-analyser`; the default strategy FQN seeds moved to
  `com.acme.…`. README/specs updated; older tracker entries keep the pre-move names.
- **Settings dialog tidy-up** — the dialog no longer opens over-wide: HTML help notes are
  **width-constrained** (`ConfigPanel.mutedNote` wraps at ~500 px; unconstrained HTML labels report a
  one-line preferred width that blew up `pack()`). Assistant-tab spinner rows get proper GridBag weights
  (label column fixed, value column takes the slack) so fields are no longer squeezed/cut off.
- **Event processor tab redesign** — the single editable combo is now a **list of FQNs** (like source
  roots): Add… / **Edit…** (also double-click an entry) / Remove / **Set active**; the active processor is
  bold with an "(active)" suffix. Save writes the full list + active selection.
- **Maven repos (new Settings tab + fallback resolver)** — a list of local Maven repositories (default
  `~/.m2/repository`, multiples supported) searched for **`*-sources.jar`** when an FQN has no `.java`
  under the source roots, with a **"Don't search local Maven repositories"** opt-out.
  `source/MavenSourceResolver`: lazy one-time jar walk (warmed off-EDT on configure), group-path-prefix
  candidate ordering, per-FQN hit/miss cache; `SourceService.sourceForFqn` = roots first, then repos —
  so the detail/source viewers and LLM snippets resolve library nodes. 4 tests + config round-trip tests.
- **History tab: Clear all** — one button runs the three individual clears (each still available).
- **First-run Settings popup** — no `~/.fluxtion-analyser/config` at launch → a welcome note + the
  Settings dialog open automatically (after the splash); saving creates the file so it shows once.
  `ConfigStore.exists()`.
- **Formula management UI (M14 follow-on)** — each graph shows its derived formulas in a combo with
  **Edit** (loads expr/label/policy back into the fields; Add f(x) applies, handling renames) and
  **Remove** (legend-click removal stays). The **f(x) field autocompletes** the token being typed from
  the union of discovered `instanceId.key`s and existing formula labels (backticked when the label
  isn't a plain token).
- **Formulas referencing formulas** — `GraphPanel.expandFormulaRefs` textually expands other formulas'
  labels (bare or backticked) into parenthesised sub-expressions before `Expr.parse`; longest-label-first
  (no partial shadowing), a real key wins over a same-named label, chains resolve across passes, cycles
  are stopped by a pass cap and surface as the standard unknown-key error. 6 tests.
- **Detail viewer → graph** — right-click an attribute on a node-log line ("Add `instanceId.key` to
  graph") with a submenu: **current graph** (default), any **named graph**, or **New graph…**;
  `GraphTabs.addSeriesTo/graphNames/selectedGraphName`; the right rail switches to the Graph tab to show
  the result. Key hit-detection reuses `SourceNavigation.lineAt/parseNodeLogLine` plus a token scan.
- **Main layout rebalance** — the records table no longer dominates: vertical split 440→330 px
  (resize weight 0.6→0.45), horizontal split 840→630 px (0.72→0.55), giving the detail panel and the
  right-hand tabs breathing room.

## M15 · Settings export / import — shareable analysis setups — ☑ SHIPPED
_Design: **[spec-settings-share.md](spec-settings-share.md)**. File → Export/Import settings: a
versioned properties file carrying a **whitelisted** subset of the config (roots, maven repos, EP list,
named graphs incl. formulas/pins, view, assistant caps; LLM provider opt-in) — never the API key, AWS
details, recent files or search history. `~`-relative paths for portability; additive list merge,
graphs replace-by-name, import summary dialog; whitelist enforced on import too._
- [S15.1] ☑ **`config/SettingsShare` core** — export/preview/apply, whitelist, `~` paths, version gate,
  merge rules; 11 unit tests (`SettingsShareTest`). Reuses `ConfigStore`'s list/graph serialization
  (helpers widened to package-private). `ImportPlan` carries a per-category summary for the dialog.
- [S15.2] ☑ **UI** — `ExportSettingsDialog` (category checkboxes + Copy / Save-and-reveal / Email) and
  `ImportSettingsDialog` (per-category summary + deselect); File-menu items; refresh via
  `onConfigChanged` + `graphTabs.restore` + `setVisibleColumns`. Slack-webhook transport deferred
  (offline ethos) — clipboard/file/email cover messaging.
- [S15.3] ☑ **Docs** — help section, README, changelog entry. Site page (`sharing-setups.md`) lands
  with the M17 docs site.

## M16 · Release & distribution — GitHub Actions, fatjar, JBang — ☑ SHIPPED (bar one repo toggle)
_Design: **[../admin/release-process.md](../admin/release-process.md)**. Trunk-based on master;
releases are one workflow-dispatch with a version number: tests → changelog stamp ([Unreleased] →
version) → tag → fatjar build (`versions:set` from input; pom stays a placeholder) → GitHub release
with notes + versioned & **stable-name** jars + sha256. `CHANGELOG.md` (Keep-a-Changelog, seeded) is
the one manual habit and is **bundled into the jar** for Help → Release notes + a what's-new-on-upgrade
note. JBang catalog alias points at `releases/latest/download/` so it never needs updating._
- [R16.1] ◐ **Pre-flight** — ☑ artifactId typo fixed (`audiitlog`→`auditlog`; jar is now
  `fluxtion-auditlog-analyser-*.jar`), ☑ pom version placeholder `0.0.0-SNAPSHOT`. ☐ repo Actions
  **write permissions** — a GitHub settings toggle, done in the UI before the first release.
- [R16.2] ☑ **`ci.yml`** — `mvn -B verify` gate on push/PR to master.
- [R16.3] ☑ **`release.yml`** — dispatch-driven: verify → stamp+commit changelog → tag → fatjar
  (`versions:set` from input) → GitHub release with versioned + stable-name jars + sha256. Guards a
  bad version input, an existing tag, and an empty changelog section.
- [R16.4] ☑ **Bundled notes + version** — CHANGELOG shipped as `/release-notes/CHANGELOG.md`,
  `Implementation-Version` in the (shaded) manifest, `ReleaseNotes` helper (3 tests), Help → Release
  notes panel, Help → About shows the version, and a `lastRunVersion` what's-new-on-upgrade dialog
  (suppressed for dev/`-SNAPSHOT` builds and fresh installs).
- [R16.5] ☑ **`jbang-catalog.json`** (alias → `releases/latest/download/…`) + README run-it-now
  (JBang + curl) snippets.

