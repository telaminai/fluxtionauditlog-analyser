# Changelog

All notable, user-visible changes to the Fluxtion Audit Log Analyser.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org).
Add a line under **[Unreleased]** with every user-visible change; the release workflow stamps it.

## [Unreleased]

### Added
- Assistant **file exports are now opt-in and confined**: the `screenshot` and `report` verbs require
  *Allow file exports* (Settings ▸ Assistant), write only inside the configured **export directory**,
  and never overwrite an existing file. Off by default — the action socket's out-of-box promise stays
  "nothing outside the loaded log".
- **Node coverage** — `analyser_coverage` answers "which of this processor's nodes never logged in this
  run?", by comparing the GraphML against the audit log. Raised by a 309-node test estate where 54 of
  275 nodes never ran because the harness could not reach them, with every test green. A gap means
  "never logged", not proven "never ran", and the result says so.
- **Diagnose one cycle, not just a trend.** A record's finding — what is wrong, and optionally the likely
  cause — is now painted as a callout over the Topology graph for that record, so a screenshot of the
  graph carries its own explanation. Write it from Records ▸ *Write a finding for this record…*, or from
  an assistant with `flag {note, fix}`. There is one place to write it and three places it shows: the
  table's note column, the callout, and an exported report.
- **Export a finding as a PDF.** Records ▸ *Export finding to PDF…* (or the `analyser_report` verb)
  writes one document containing the explanation and suggested fix, which record and log it is about,
  the full event record and node log, and **two views of the graph**: the cycle on its own, and the whole
  processor with that cycle lit — so you can see what the event *didn't* reach as well as what it did.
  Both are rendered for the page rather than screenshotted, so the document doesn't inherit your current
  zoom and exporting never changes what you are looking at. An included plot is marked with a dashed rule
  at the record being diagnosed, and the report records when the analysis was made. Dependency-free —
  nothing new in the build.
- **Charts can explain themselves.** A multi-line `explanation` and `notes` pinned to moments in time are
  drawn on the plot (so they survive an exported PNG), and a `rightAxis` gives a second vertical scale so
  series of very different magnitude stay readable together. All three are available through the
  `analyser_graph` verb over REST and MCP — or by right-clicking the plot to pin a note where you are
  reading. Annotations are saved with the graph.
- Fixed: clicking a node in the Topology tab reset the zoom, and clicking one in a focused view emptied
  it.

### Security
- **Documentation screenshots have been replaced.** The images shipped with the first public release
  were captured against a real audit log and contained live venue, vendor and project names. They are
  regenerated from the anonymised demo fixture; five that could not be regenerated automatically have
  been withdrawn. Screenshots are now produced by `tools/capture-docs.py`, which only ever loads the
  demo fixture.

### Added
- **Node log: Logical and Text views.** Logical gives each node a block with its values on their own
  lines in dispatch order; Text is the raw audit YAML. Traced-only keys (`thread`, `method`) are muted
  rather than hidden, so you can still see at a glance that a record is traced.
- **Source view: Processor · Node · Split.** Split shows the generated processor and the node class at
  once — the dispatch call site above, the method it runs below. Navigating to a node from the processor
  promotes to Split instead of replacing what you navigated from.
- **Collapsible event-type panel** behind a vertical nav rail on the left (state persists), with the
  column checkboxes available from the rail as well as the menu.
- **Topology step-through**: an `event 8 / 10 · step 2 / 5` header, whole-record skip buttons, and
  autoplay.
- **Clearer panel surfaces**: source, record-detail and topology now share one content surface with a
  hairline edge, and control clusters (the time-range bar) tint the other way — derived from the active
  theme, so it holds in Light, Dark, IntelliJ and Darcula.
- Switching side tabs no longer moves the main split divider.
- The `analyser_graph` verb now brings the Graph tab forward, so a scripted plot is visible.
- **Sync toggle** on the Topology toolbar: the source pane either follows what you click and step
  through, or stays where you put it. On by default, remembered between sessions.
- Fixed: a node that ran was drawn faded if an earlier selection left it outside the focus scope — the
  execution shading and the selection shading were compounding. Evidence now wins.
- Fixed: the Topology tab's source pane kept the old palette after a theme change.
- **Selecting a record syncs the source view you can see** — the Topology tab's embedded pane when that
  is what is open, the Source tab otherwise. It never switches tabs on you.
- **Source navigation lands where you meant**: clicking an event in the topology opens the processor at
  that event's own `handleEvent` overload; clicking a node scrolls to its class declaration, which
  matters when a file holds a dozen nested node classes.
- **The app can be driven end to end by an assistant**: four new verbs — `topology`, `open`,
  `source_root` and `screenshot` — join the six existing ones and are published as MCP tools
  automatically. `tools/drive-analyser.sh` scripts them over the localhost REST transport.
- Fixed: opening the source pane left the graph clipped; an unopened source pane was blank rather than
  explaining itself; adding a source root did not re-run EventProcessor inference.
- Documentation: the Topology guide covers the exploration model, stepping controls and source split,
  with fresh screenshots; the in-app help (Help ▸ User guide) gains a Topology section.
- The Topology tab remembers its zoom, pan, orientation, spacing and label size between sessions.
  **Settings ▸ History ▸ Reset topology view** puts them back, and *Clear recent files* now also clears
  the recent-GraphML list.
- Topology nodes have more contrast in the dark theme — they were getting lost against the canvas.
- Fixed: arrow-key stepping in the Topology tab stopped working once the source pane was added — the
  split pane was intercepting Up/Down to move its divider.
- The topology you had open is reopened on the next start, alongside the log.
- **File ▸ Open GraphML…** opens a processor topology (and *Open from S3…* is now *Open log from S3…*).
  The Topology toolbar loses its own Open button and its two readouts — step position and selection
  scope now appear on the status line, leaving the toolbar to controls.
- **Source opens beside the graph** in the Topology tab, with a draggable divider, instead of switching
  to the Source tab. Open it with **Enter** on a selected node, the node's right-click menu, or a
  double-click in the index. Repeated clicks on a node now always cycle its scope.
- Nested classes (e.g. `Nodes.QuotePublisher`) now resolve to their enclosing `.java` file, so source
  navigation works for graphs whose nodes are grouped in a holder class.
- **Show all**, or a click on empty canvas, returns the topology to the plain full graph — selection,
  focus and cycle shading all cleared, every node at full strength. Stepping brings the shading back.
- **Open recent** is split into *audit log* and *GraphML*.
- While stepping, only edges whose **both** ends ran are highlighted — an arrow from a node that did not
  run is no longer drawn as though the event arrived that way.
- **Topology**: a collapsible index overlay (Nodes / Events / Services) for picking a node by name and
  jumping to its source; node tooltips now show the class javadoc; selected nodes are marked with a ring
  and tint rather than by dimming everything else; smaller node boxes with a visible fill for plain nodes;
  and the status line says how many nodes the filters are hiding. Clicking an index entry scrolls that
  node into view. Spacing and text size are remembered between sessions (and never included in an
  exported settings file).
- **Topology**: spacing and text-size sliders, and a **Show all** button that clears the selection and
  focus so nothing is dimmed.
- **Topology exploration**: hide Fluxtion's scaffolding nodes with a checkbox (off by default — they are
  half the graph and none of your application); click a node repeatedly to widen its scope
  *node → neighbours → all routes → whole graph*; **F** or the Focus button shows only that scope.
  Cmd/Ctrl-click selects several nodes. What the log establishes about execution is unaffected by what is
  filtered from view.
- **Right-click the records table** for the record actions (flag, copy as YAML, diff, export) and the
  column chooser. **Columns is no longer a top-level menu** — it is on the nav rail and the right-click.
- **Better code type**: picks the best monospaced family installed rather than Swing's logical
  `Monospaced` (which lands on Courier on some platforms), with slightly opened line spacing.
- Topology fixtures now include a **re-dispatch** (`processReentrantEvent`): a node raising an event on
  its own graph. It lands in the log as a separate record that looks externally caused, which is the
  case most likely to be misread when stepping through a cycle.
- Topology: **exported services are entry points too.** A cycle that arrived through an
  `@ExportService` call now resolves to the service node and shades the path from it, so an operator
  action reads like an event rather than an unexplained cycle. Both signature spellings the runtime
  emits are understood — the fully-qualified one, and the method-name-only one, which resolves when the
  graph declares a single exported service.
- Topology: **drag-and-drop a `.graphml`** anywhere on the window to load it into the Topology tab —
  and drop a log + graphml pair together to open both in one gesture.
- **MCP bridge** — `java -jar analyser.jar --mcp` runs the analyser as an MCP server over stdio, so an
  MCP-native client (Claude Code, Claude Desktop, Codex) drives the running app with **no prompting
  and no copied token**: it discovers one tool per assistant verb (`analyser_aggregate`,
  `analyser_read`, `analyser_filter`, `analyser_graph`, `analyser_goto`, `analyser_flag`) natively.
  Point the client at the jar once — the bridge finds the app's per-run port and token by itself, and
  keeps working across analyser restarts. Speaks both the legacy `initialize` handshake and the
  current handshake-free MCP revision (`2026-07-28`). Needs the REST transport enabled
  (Settings ▸ Assistant); if the app isn't running you get a plain "analyser not running" message.
- Assistant: while the localhost REST transport is running, the app publishes its live endpoint to
  **`~/.fluxtion-analyser/rest-endpoint`** (mode 600 — url, token, pid, start time) and removes it on
  stop/exit. A client configured once can now find the per-run port and token instead of you copying
  them each launch; a file left by a crash is detectable via its pid.
- **Topology tab** — open a processor's `.graphml` to see its node graph laid out by dispatch order, with
  pan, zoom, fit, hover, selection and a top-down/left-right toggle. Node colour distinguishes events,
  event handlers, nodes and exported services; selecting a node highlights what feeds it and what it
  feeds.
- **Step through an event on the graph** — select a record and the Topology tab lights up the nodes that
  fired, numbered in dispatch order, with everything that didn't fire faded back. Walk the cycle node by
  node with ◀ ▶ to see what each one logged at that point. It follows the table's selection, so the
  record you're looking at everywhere else is the cycle you're stepping through.
- **Act on a node from the graph** — right-click any node in the Topology tab to open its source, plot one
  of its logged values, filter every view to records mentioning it, or copy its instance id. Double-click
  jumps straight to the source. Plots land on the same graphs, and filtering uses the same search box, as
  everywhere else in the app.

### Changed
- The launcher now **rejects an unknown `--option`** with a usage message and exit code 2, and adds
  `--help`. Previously any unrecognised argument was treated as a log file to open, so running an older
  build with `--mcp` silently launched the desktop app trying to load a file called `--mcp`.
- JBang launches no longer print JVM native-access warnings — the catalog alias now passes
  `--enable-native-access=ALL-UNNAMED` (pick it up with `jbang --fresh analyser@…`).

### Added
- **Step through the log on the graph.** One cursor now walks record → `nodeLogs` row → next record with
  **↓** and **↑**: arriving at a record marks where the cycle came in, each step lights the next node with
  the path so far trailing behind it, and stepping past the last row rolls into the next record. It
  follows the filtered view, moves the table selection with it, and highlights the matching line in the
  detail viewer — so the graph and the text narrate each other. The readout says whether you are walking
  *logged rows* or *every invocation*, and a node that logs twice gets two steps.
- **The assistant now knows what silence means.** Its prompt carries Fluxtion's execution semantics: a
  node missing from `nodeLogs` has not necessarily failed to run, the two audit regimes and how to tell
  them apart, and that it can settle the question from the EventProcessor source it is already given
  (grep for `auditInvocation`) rather than guessing. Also covers dirty/`@OnTrigger(dirty=false)`
  propagation, so "absent" can be read as "on the branch not taken".
- **Topology recognises a fully-traced log.** If the processor was built with an audit level, Fluxtion
  records every node it invokes, so the log is a complete list of what ran. The tab detects that and
  stops hedging — absence becomes *did not run*, and the legend says so.

### Fixed
- **Topology: a node with no audit entry is no longer shown as if it didn't run.** Nodes log only if they
  write audit output, at the level in force, so silence is not absence of execution. The tab now
  distinguishes *logged* (observed), *ran but logged nothing* (it was the only route into something that
  ran), *may have run* (connected, but the log can't say), and *not on this path* — with a legend on the
  canvas and the claim in words on hover.
- Topology: exported services are drawn as what they are — **inbound callback entry points**, alongside
  events — rather than as outputs.
- **The app could become impossible to close.** If any step of the quit sequence failed, the exception
  escaped the window-closing handler before the exit call, so the window stayed on screen and every
  further click on the close box failed the same way — with the assistant's REST transport already shut
  down. Quitting is now step-isolated and the exit always runs. Most likely to bite when the jar is
  rebuilt underneath a running app.
- A release now refreshes the **docs site's release-notes page** automatically — the release workflow
  dispatches the docs deploy (the changelog-stamp push alone never triggered it).

### Docs
- User guide: **Topology & step-through** — reading the processor graph, opening a `.graphml`, stepping
  through a cycle, and the node right-click actions. Includes the build-mismatch warning and why the
  offline case is the one this tab is for.
- Assistant guide: **"Connect an MCP client"** — copy-paste config for Claude Code, Claude Desktop and
  Codex, a worked session transcript, how the bridge finds your running analyser, troubleshooting, and
  exactly what an MCP agent can and cannot reach. Spells out that **your client does not launch the
  analyser**: you keep the app open with the REST transport on, and the client starts only the bridge.
- Install guide: documented the JBang **first-run trust prompt** and that a **new release** needs
  `jbang --fresh` (or `jbang cache clear`) — JBang otherwise keeps serving its cached jar.

## [1.0.0] - 2026-08-14

### Added
- Assistant: **`read` verb** — fetch the raw text of N records around a record/byte anchor over the
  localhost socket, so an agent can seek the log without filesystem access (rate-limited).
- Assistant: **per-verb JSON schemas** in `GET /manifest` — every verb's params are now self-describing.
- Assistant: **`goto {reveal:true}`** un-hides a filtered-out record; otherwise the echo names which
  filter hides it.
- Graphs: agent-built graphs can carry a **rationale** caption (provenance) shown under the plot.
- Records ▸ **Copy selected as YAML**; the record detail pane gained a **Copy** button.
- Diff viewer: **export as CSV, JSON or PDF**.
- Source viewer: a **Wrap** toggle (off by default).
- **Share your analysis setup**: File ▸ Export settings… / Import settings… — save a versioned
  `.fluxtion-settings` file (or copy it to the clipboard / email it) carrying source roots, Maven
  repos, event processors, named graphs (formulas and pins included), hidden columns and assistant
  preferences. Import merges safely (lists add, graphs replace by name) behind a summary you confirm.
  API keys, AWS details, recent files and search history are never included.
- Settings: **Maven repos** tab — local repositories (default `~/.m2/repository`) are searched for
  `*-sources.jar` when a class isn't under any source root; "don't search" opt-out.
- Settings: Event processor tab is now an add/edit/remove **list** of FQNs with one marked active
  (double-click to edit).
- Settings: History tab **Clear all** button.
- First run with no saved configuration opens the Settings dialog automatically.
- Graphs: **formula list** per graph with Edit and Remove; the f(x) field **autocompletes** from
  discovered keys and existing formula labels; formulas can **reference other formulas** by label.
- Detail viewer: **right-click an attribute** to add it as a series to the current, a named, or a
  new graph.

### Changed
- Toolbar buttons now pair each label with a small **icon** (hand-drawn, theme-aware).
- Event types panel: split into **Event types** and **Callbacks** sections with **Select all / Select
  none** and a right-click **Only this / Add / Remove** (the group-by radio is gone).
- Detail viewer: **right-click anywhere on a node line** to add any of its values to a graph (no longer
  only when you click exactly on a key); the event/`eventToString` line navigates to the handler source.
- Summary rows: a left-click no longer changes the filter — **right-click** a row to filter by it.
- The **LLM** tab is now **Analyser assistant**; the search field grows to fill the width with a
  **Clear history** button; chart gridlines have more contrast.
- Graphs: the f(x) formula field now shows a **dropdown of matching keys/labels** (↓/↑ to move, Enter
  or Tab to accept, Esc to dismiss) instead of inline ghost-text completion.
- Record detail **word-wrap is now off by default** (toggle still in the detail toolbar).
- Subtle panel backgrounds: the graph plot in light mode and the source viewer now read apart from the
  surrounding panels.
- Settings dialog no longer opens over-wide; Assistant tab fields are no longer clipped.
- Main window layout rebalanced — the records table leaves more room for the detail panel and tabs.

### Fixed
- Help ▸ About now shows the analyser's own version instead of a bundled dependency's version.
- Help ▸ User guide now renders as a readable light document in the dark theme (previously dark text on
  a dark background), and its links now open in the system browser.
