# Changelog

All notable, user-visible changes to the Fluxtion Audit Log Analyser.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org).
Add a line under **[Unreleased]** with every user-visible change; the release workflow stamps it.

## [Unreleased]

### Fixed
- **The action manifest advertised six verbs while thirteen shipped.** `GET /manifest` hardcoded its
  `verbs` list, so it contradicted its own `schemas` field and an external agent never learned that
  `topology`, `open`, `source_root`, `screenshot`, `report`, `context` and `coverage` existed. The
  copy-prompt handed to agents named five. Both are now derived from the schema set, with a test that
  fails if either is written out again.
- **`analyser_coverage` shipped undocumented**, and the assistant guide still described file exports as
  able to overwrite anything — which stopped being true when exports became opt-in and confined.
- **Documentation screenshots can be regenerated again.** `tools/capture-docs.py` now uses a throwaway
  export directory and unique capture names, so it works with the export guard rather than around it.


## [1.1.0] - 2026-08-16

**The topology release.** The analyser now draws the processor's graph, walks events across it step by
step, and is honest about what the log can and cannot prove. The whole app is scriptable by AI agents
over MCP, findings export as evidence-grade PDFs — and anything that writes a file is locked behind an
opt-in.

### Added
- **Topology tab** — open the processor's build-time `.graphml` (**File ▸ Open GraphML…**, or just
  **drag it onto the window** — drop a log + graphml pair together and both open) and see the node graph
  laid out by dispatch layers: pan/zoom/fit, top-down or left-right, node colours for events / handlers /
  nodes / exported services, spacing and text-size sliders, a collapsible name index with javadoc
  tooltips, and a status line that counts what the filters are hiding. The topology you had open reopens
  next start; **Open recent** is split into *audit log* and *GraphML*.
    - **Explore**: click a node repeatedly to widen its scope (*node → neighbours → routes → whole
      graph*), **F** to focus, Cmd/Ctrl-click to multi-select, **Show all** (or click empty canvas) to
      reset; Fluxtion's scaffolding nodes hide behind a checkbox.
    - **Act on a node**: right-click to open its source, plot one of its logged values, filter every
      view to records mentioning it, or copy its instance id; double-click jumps to source (nested
      holder classes resolve).
    - **Source opens beside the graph** (Processor · Node · **Split** — the dispatch call site above,
      the method below) with a **Sync** toggle: follows your stepping, or stays where you put it.
    - **Build mismatch is surfaced, never hidden**: the status line names instance ids that appear in
      the log but not the graph — treat that as a version mismatch, not a curiosity.
- **Step through an event on the graph** — select a record and its cycle lights up in dispatch order;
  one cursor walks *record → each nodeLogs row → next record* with **↓/↑** (plus whole-record skip and
  autoplay): the entry point is marked, the path trails behind the current node, the table selection and
  the detail viewer's highlighted line track every step. The readout says whether you are walking
  *logged rows* or *every invocation*; a node that logs twice gets two steps; only edges whose **both**
  ends ran are lit.
- **Execution honesty** — what makes the picture trustworthy:
    - A silent node is no longer drawn as "didn't run". The graph distinguishes *logged* / *ran but
      logged nothing* (it was the only route into something that ran) / *may have run* / *not on this
      path* — with an on-canvas legend and the claim in words on hover.
    - A **fully-traced log** (processor built with an audit level) is detected, and the tab stops
      hedging: absence becomes **did not run**, and the legend says so.
    - **Exported services are entry points**, drawn alongside events — an operator action reads like an
      event, not an unexplained cycle. Re-dispatched events (`processReentrantEvent`) are in the test
      fixtures because they are the case most likely to be misread.
    - The **assistant knows what silence means** too: its prompt carries the two audit regimes, how to
      settle them from the processor source it is given, and dirty/`@OnTrigger(dirty=false)`
      propagation — so "absent" can be read as "the branch not taken" instead of guessed at.
- **Diagnose one cycle, not just a trend** — write a **finding** on a record (Records ▸ *Write a
  finding…*, or `flag {note, fix}` from an agent). One write site, three readers: the table's note
  column, a callout painted on the topology, and **Export finding to PDF** — the explanation and
  suggested fix, the full event record and node log, and **two graph views** (the cycle alone, and the
  whole processor with that cycle lit, so you see what the event *didn't* reach). Rendered for the page,
  not screenshotted; dependency-free.
- **Charts explain themselves** — a multi-line explanation block, **notes pinned to moments in time**
  (right-click the plot where you are reading), and a second **right axis** for series of different
  magnitude. Drawn on the plot, so an exported PNG carries them; saved with the graph; scriptable via
  `analyser_graph`.
- **Node coverage** — *"which of this processor's nodes never logged in this run?"*
  (`analyser_coverage`): compares the GraphML against the log, keeping *covered* / *never logged* /
  *silent-by-design* apart, with a separate build-mismatch signal. Born from a 309-node test estate
  where 54 nodes were unreachable — with every test green and nothing to say so.
- **MCP bridge** — `java -jar analyser.jar --mcp` runs the analyser as an MCP server over stdio: an
  MCP-native client (Claude Code, Claude Desktop, Codex) discovers **one tool per assistant verb —
  thirteen of them — automatically**, with no prompting and no copied token. The app publishes its live
  REST endpoint to `~/.fluxtion-analyser/rest-endpoint` (mode 600) while the transport runs, so a client
  configured once keeps working across analyser restarts; both the legacy MCP handshake and the current
  handshake-free revision (`2026-07-28`) are spoken.
- **Fully scriptable** — new verbs `topology`, `open`, `source_root`, `screenshot`, `report`,
  `coverage` join the existing set, published as MCP tools automatically; `tools/drive-analyser.sh`
  scripts them over the localhost REST transport. Scripted plots bring the Graph tab forward so you see
  what the agent built.
- **Node log, two ways** — **Logical** (each node a block, values on their own lines, dispatch order)
  and **Text** (the raw audit YAML). Traced-only keys are muted rather than hidden, so a traced record
  is recognisable at a glance.
- **Collapsible event-type panel** behind a vertical nav rail (state persists); column checkboxes on the
  rail and the table right-click. Better code typography (best installed monospaced family).

### Changed
- The launcher **rejects unknown `--options`** with usage and exit 2, and adds `--help`. *Upgrading
  note:* JBang serves its cached jar, so run **`jbang --fresh analyser@…`** to get this version — on the
  cached 1.0.0 jar, `analyser --mcp` shows usage instead of starting the bridge.
- JBang launches no longer print JVM native-access warnings (the catalog passes
  `--enable-native-access=ALL-UNNAMED`).
- **Right-click the records table** for record actions (flag, copy as YAML, diff, export) and the column
  chooser; Columns is no longer a top-level menu. *Open from S3…* is now *Open log from S3…*.
- One shared content surface (source, record detail, topology) with hairline edges, derived from the
  active theme; switching side tabs no longer moves the main divider.

### Security
- **Documentation screenshots were replaced.** The images shipped with 1.0.0 were captured against a
  real audit log and contained live venue, vendor and project names; they are regenerated from the
  anonymised demo fixture (five that could not be regenerated were withdrawn). Screenshots are now
  produced by a capture harness that only ever loads the demo fixture.
- **File-writing verbs are opt-in and confined.** `screenshot` and `report` require **Allow file
  exports** (Settings ▸ Assistant, off by default), write **only inside the export directory you
  choose**, and never overwrite an existing file — so the action socket's out-of-box promise stays
  *"nothing outside the loaded log"*. The FAQ's security answer documents every mutating verb, and a
  test now fails the build if that ever stops being true.

### Fixed
- **The app could become impossible to close** — a failing step in the quit sequence escaped the
  window-closing handler before the exit call. Quitting is now step-isolated and the exit always runs
  (most likely to bite when the jar is rebuilt underneath a running app).
- Topology: a node that ran was drawn faded when execution shading and selection shading compounded —
  evidence now wins; clicking a node no longer resets the zoom; a focused view no longer empties on
  click; the embedded source pane re-themes on theme change and no longer clips the graph or steals
  ↓/↑ from stepping; adding a source root re-runs EventProcessor inference.
- A release now refreshes the **docs site's release-notes page** automatically.

### Docs
- User guide: **Topology & step-through** — reading the graph, opening a `.graphml`, stepping a cycle,
  node actions, the build-mismatch warning, and why the offline case is the one this tab is for.
- Assistant guide: **"Connect an MCP client"** — copy-paste config for Claude Code, Claude Desktop and
  Codex, a worked session transcript, troubleshooting, and exactly what an agent can and cannot reach
  (your client does not launch the analyser — it starts only the bridge).
- Install guide: the JBang **first-run trust prompt**, and that a new release needs `jbang --fresh`.

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
