# Changelog

All notable, user-visible changes to the Fluxtion Audit Log Analyser.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org).
Add a line under **[Unreleased]** with every user-visible change; the release workflow stamps it.

## [Unreleased]

### Added
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
  `]` and `[`: arriving at a record marks where the cycle came in, each step lights the next node with
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
