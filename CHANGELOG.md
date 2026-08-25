# Changelog

All notable, user-visible changes to the Fluxtion Audit Log Analyser.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org).
Add a line under **[Unreleased]** with every user-visible change; the release workflow stamps it.

## [Unreleased]

### Added
- **A log can say which *system* it came from** — `open {log, provenance: "risk-engine ·
  localhost:8081 · ~/dev/risk"}`. A file name is not a system: export three servers' logs and you get
  three artefacts nobody can tell apart, and a report headed *"written against export-1.yaml"* tells
  a reader nothing. Provenance rides the status bar, `context`, report headers and PDFs, and lets the
  mismatch banner say **"same content but a different system"** — the case two servers running the
  same build produce, where the file name never could. **Never inferred**: omit it and the analyser
  says nothing rather than guessing. Shared reports carry it, so the Reports sharing row now names it.
- **An agent-driven open can no longer strand the app on a dialog** (M35.7). Opening a log that sits
  inside a project popped a **modal** "load this project's settings?" question from inside the load
  path — fine for a human, fatal over the action socket, where nobody can answer it and every later
  verb blocks behind it. Worse, the new log was already live behind the invisible dialog, so it was
  answerable against the *previous* log's graph. Opens that come from the socket now **report** the
  offer instead: it appears in `context` as `projectOffer`, and in the status bar. Opening by hand is
  unchanged — you still get the dialog. Loading a project is still never automatic, because it
  replaces your source roots, event processors, graphs and hidden columns.
- **Closing a log no longer leaves a stale record count on screen.** The time-range header went on
  reading "showing 582 of 582" after the log had gone.
- **The Topology tab now says whether its graph fits the open log** (M35.6) — permanently, beside
  the thing it qualifies: *"⚠ DOES NOT FIT THIS LOG — the graph declares only 0 of the 211 node(s)
  this log writes"*. The main status bar said it at load time, but 32 other things write that line,
  so the warning vanished on the next filter change — while the shading, step order and coverage
  figures it qualifies stayed on screen.
- **Switching or closing a project now closes the log and graph with it** (M35.5). A project owns
  your source roots, event processors, named graphs, focuses and reports — swap it and all of those
  change underneath the open log, so keeping it meant viewing one project's log through another's
  settings, with focuses pointing at nodes from a graph that was no longer the right one. The one
  exception: accepting the *"this log sits inside a project"* offer adopts the settings and **keeps
  the log**, because there the project is being adopted precisely because that log was opened.
- **Find the GraphML for the log you have open** (M35.4). *File ▸ Find GraphML in source roots…*
  lists every `.graphml` under your configured roots, **ranked by how well each fits the open log** —
  node count, and how many of the log's nodes it declares. Agents get the same list from
  `open {discover: "graphml"}`. **Nothing is opened until you pick one**: an auto-selected graph is a
  graph nobody chose, and the moment it is wrong the analyser is confidently describing a system it
  is not looking at. Unreadable files are listed with the reason rather than silently dropped, and a
  scan that hit its bound says so instead of presenting a partial list as the whole answer.
- **Switch processor without reopening the log** (M35.3). A multi-processor server emits one GraphML
  per processor; opening a second one now judges it against the log you already have and says so
  immediately — `open {graphml}` returns the node count, how many of the log's nodes the graph
  declares, and the verdict. A graph you opened **deliberately is kept even when it does not fit**,
  because comparing one build's graph with another build's log is a real thing to want; only a
  *stale* graph, found sitting there when a new log arrives, is closed.
- **A graph loaded for one log no longer survives into another** (M35.2). Opening a log now
  re-checks the loaded topology against it: kept when it still declares the nodes the log writes,
  otherwise **closed, with the reason and its counts** — *"the graph declares only 0 of the 17
  node(s) this log writes, so it describes a different system or build. Reopen it deliberately if
  you meant to compare them."* Previously the first log's graph stayed silently, and coverage,
  "did not run" shading and step-through all answered from it. The verdict is in `context` as
  `graphPairing`, so an agent can see the pairing before deriving anything from it.
- **Close a log, close a graph, or reset** (M35.1) — *File ▸ Close log / Close graph / Reset*, and
  `open {close: "log"|"graph"|"all"}` for agents. Until now the app could only ever load: there was
  a "Close project" but no "Close log", and opening a second log left the **first** log's topology
  on screen, so coverage, "did not run" shading and step-through described a graph that had nothing
  to do with the records. Closing clears everything **derived** from the log — records, filter,
  search, time slider, event checklist, summary, detail, flags, execution shading, the step cursor,
  follow — while **profile state survives**: named graphs, focuses, source roots and saved reports
  are yours, not the log's. Anything that can no longer resolve says so instead of vanishing, so a
  saved report with no log reads "written against risk.yaml · 726 records; no log is loaded" with
  every anchor named.
- **A log now says whether its order means anything** (M34.1, first slice). Fluxtion's `nodeLogs`
  order is derived by the AOT compiler, so step-through and the topology's order badges are reading
  back real causality. A source whose components run concurrently has no such order to report — and
  the M34.0 spike found the on-screen presentation identical either way. Readers now declare
  `ordering: TOTAL | PARTIAL`, the claim reaches the index, `context` reports it before anything is
  derived from position, and Settings ▸ Plugins marks a partial-order reader. **Nothing changes for
  an audit log**: text containers are totally ordered and say so. Readers published against 1.5.0
  keep compiling — the capability is additive and defaults to `TOTAL`.

### Fixed
- **Relative paths in a committed project profile are relative to the project, not to `.analyser/`.**
  A profile that said `sourceRoot.0=src/main/java` was resolved against the folder the file sits in,
  landing at `<project>/.analyser/src/main/java` — a directory that does not exist — so the playground
  bundle contract's "open the log and everything is configured" could not have worked. The canonical
  `.analyser/project.fluxtion-settings` now anchors at the project root; a loose `.fluxtion-settings`
  file imported from elsewhere still anchors at its own directory.

## [1.7.0] - 2026-08-20

### Added
- **Investigation reports** — the *account* of an investigation, not just its evidence. A report is
  an ordered list of **references** with connective prose, never a free-form document: findings,
  records, charts, focuses, derived tables and visibly-labelled narrative, built with
  `report {name, sections}`, rendered in the new **Reports** tab (every evidence section clicks
  through to what it references), exported to PDF, persisted with your profile and shared under
  their **own category** — a shared report carries prose written about your data, which deserves its
  own consent checkbox. Two rules are enforced, not encouraged: a finding section renders what
  `flag` wrote **byte-identically** (the verb cannot author or override a diagnosis), and narrative
  always wears its standing label so an assertion can never pass as a record. Evidence **re-renders
  live** — a stored report is a re-runnable claim — and the report captures the log fingerprint and
  filter it was authored under, announcing before anything renders when either differs (a renamed
  copy with identical content gets a softer "same content — a different file" notice). Clicking an
  evidence link whose record is hidden by the filter **offers** to widen it, never fails silently. Table rows
  are **derived** from a stored query with declared presentation; a row highlight is a **rule that
  is printed with the table** and re-evaluates strictly against each row's own record; tables export
  to CSV.

### Fixed
- **The chart's empty state no longer lies about why.** "No numeric series selected" painted
  equally for a chart with no series and for one whose configured series matched no records under
  the current filter — the second case now says so and names the filter as the reason.
- **A changed external-marker CSV is re-read.** The per-definition cache now checks the
  file's modification time, so a file that changed on disk cannot leave the chart showing evidence
  that no longer exists; previously a stale read survived until the definition changed or a restart.
- **A row-highlight rule that needs history is now refused and named, never quietly applied to a
  one-sample window.** `rowWhen` evaluates against each row's own record, so a rolling window holds a
  single sample: `mean`, `sum`, `rollingMin` and `rollingMax` collapsed to their bare argument and
  highlighted rows as though the window had been computed, while the label printed under the table
  still claimed the window — a report stating a rule the analyser never applied. `lag`, `delta` and
  `rate` failed the other way and could never fire. All seven are now rejected with the reason, in
  the table, in the verb echo and in the parameter schema; compute the window with `series` and
  compare on a plain value.
- **The "different log" banner names the log you are actually on.** It described the loaded log using
  the *report's own* stored file name, so re-opening a report against another log announced the
  mismatch while naming the file you were not looking at. The verdict still turns on content (record
  count and time range), so a renamed or moved file is still not a mismatch.

## [1.6.0] - 2026-08-18

### Added
- **Marker series now appear in the chart legend** — each with its own glyph, in its own colour, and
  the number of events it holds. Previously a chart could draw triangles and crosses while the key
  named only the value series, so nothing on screen said what a glyph meant. A marker series that
  resolved to nothing still gets a row, reading `(0)` with the reason on hover — an event type that
  never fired is a finding, not something to hide. Right-click a row to remove that marker series.
- **The topology index is sorted, and reads numbers as numbers.** Each of Nodes, Events and Services
  is now alphabetical, with digit runs compared by value — so `CHILL-2` comes before `CHILL-10`
  instead of after it. On a generated estate (chillers, tills, zones) the previous graph-emission
  order was arbitrary, and plain alphabetical order would have been actively misleading.

## [1.5.0] - 2026-08-18

### Added
- **External series** — plot what the outside world did. An agent (or you) adapts a FIX log, GC log
  or venue export into a `(timestamp, value)` CSV and the analyser plots it beside the audit-derived
  series: *File ▸ Add series from CSV…*, or `graph {external: [{path, label, time, timeFormat, zone,
  value, offsetMillis}]}`. The clock domain is **declared, never guessed** (a wrong guess reverses
  causality invisibly; a wrong declaration is at least visible); the chart is **stamped** with each
  external series' clock and offset so a foreign line can never pass as audit evidence in a PNG or
  PDF; loads report rows skipped, reordered and the resolved time range, with diagnostics that never
  echo file contents. Verb reads are confined to the exchange directory — the renamed **Allow
  assistant file exchange** opt-in now covers writes *and* these reads, one switch, one directory —
  or to files you picked in a chooser yourself. External definitions persist with the graph and
  travel in shared setups (the data never does — a recipient without the file sees the rest of the
  graph and a note naming what did not resolve).

- **Rolled log sets open as one log.** Opening any member of a set (`maker.log.1`, date-stamped
  siblings) offers the whole set; the load order comes from each file's **content** — its first timed
  record — never the name, so logrotate's newest-first `.1` and an incrementing writer's oldest-first
  `.1` both load correctly with zero configuration. Record numbering is global and gap-free; byte
  offsets stay real per-file offsets (verbs take `file` alongside `byteOffset` on a set). Memory
  scales with the set total, not per member. Agents: `open {logs: [...]}`.
- **Time order is now validated on every load** — within each file and across roll boundaries — and
  violations are reported with record anchors, never silently repaired: a backwards timestamp is a
  finding, and re-sorting would destroy the evidence. While violations exist, time-anchored answers
  (`at`, windows, buckets) carry a caveat; the full report is in `context` and the load dialog.

- **Log-source plugins.** The analyser can read audit records from other containers — parquet,
  Chronicle, a database — through reader plugins: jars you explicitly install in
  `~/.fluxtion-analyser/plugins/`, listed with their declared time base and capabilities in
  **Settings ▸ Plugins**. The trust boundary is stated plainly there and in the FAQ: installing a
  jar is arbitrary code execution, nothing is ever bundled or downloaded, and a plugin can only be a
  *reader* — it cannot add verbs. Every reader declares its clock domain up front, capabilities
  degrade loudly (a container without byte anchors is anchored by record index and says so), and
  `open {format}` forces a specific reader. Without plugins, nothing changes.

- **Point-snapped chart tooltips.** Hovering a plot now snaps to the **nearest actual sample**
  within a small radius and reads `series · time · value` — instead of reporting the cursor's raw
  coordinates whether or not data was there. A series dense enough to be decimated on screen answers
  its cursor column's **min/max range** rather than pretending one sample is the truth; with no
  sample in range, the coordinate readout remains as before.

- **Marker series — events on a value chart.** Plot buys/sells (or any event) as glyphs on a price
  line, each point carrying a payload (a client order id) shown on hover — and **clicking a marker
  selects its record**, because a marker is a signpost to the evidence, never a substitute for it.
  Three pieces: `graph {markers: [{label, glyph, when, y, payload}]}` — a bare key fires wherever it
  was logged, a formula fires where truthy; `y` can ride a plotted series or `axis` for a rug lane
  under the plot; dense columns render one glyph with a count badge instead of soup or silence.
  Payloads are display cargo only — they never enter formulas or filters. Marker definitions persist
  and share with the graph (never their extracted values), and a marker pinned to a series that
  isn't on the graph says so instead of vanishing. A marker that rides a series rides its **scale**
  too: pinned to a right-axis series, the glyphs draw against the right scale — and follow if the
  series is later moved between axes. Markers draw from their own palette, distinct from every
  series colour, so a glyph riding a line never vanishes into it.

### Fixed
- **The `series` verb could answer from superseded data, disagreeing with the chart for the same
  formula.** A record carrying no `logTime` cannot be plotted, but it has still *observed* values — and
  under `locf` the carry is "last known value". The verb skipped such records outright, so a formula
  whose inputs moved on an untimed record was evaluated afterwards from values already known to be
  stale: on one log the chart plotted `10` where `series` reported `22`. Both looked plausible, which
  is the dangerous kind of wrong for a forensic tool. The verb now updates the carry from every record
  and only declines to emit a *point* where there is no time, matching the chart exactly.

## [1.4.0] - 2026-08-17

### Added
- **Conditional formulas** — graph and `series` expressions can now judge their inputs: comparisons
  (`> < >= <= == !=`, Unicode forms accepted) and `if(condition, then[, else])` with `and`/`or`/`not`.
  The two-argument `if` plots **only while the condition holds** — `if(ask − bid > 0.004, ask − bid)`
  draws the spread only in breach, gaps elsewhere — and an unknowable condition plots nothing rather
  than guessing a branch. Existing formulas are untouched (`min(4, 2)` still means the smaller of 4
  and 2).
- **Threshold guide lines** — `graph {guides: [{value: 0.004, label: "4bp limit"}]}` draws a
  labelled horizontal rule on either scale, so the threshold an investigation turns on is visible on
  the plot instead of interpolated by eye. Persisted with the graph, shared and exported with it.
- **Condition bands** — `graph {bands: [{expr: "ask − bid > 0.004", label: "in breach"}]}` shades
  the time intervals where the condition held. The condition is what persists; its intervals
  recompute with the data by the same extraction pass as the series, so a band can never disagree
  with a plotted series about when the condition was true.
- **Rolling-window formulas** — `lag(x, N)`, `delta(x)`, and `mean`/`sum`/`rollingMin`/`rollingMax`
  `(x, N)` over the last N samples, in graphs and the `series` verb alike. Windows fill before they
  speak, a non-numeric sample leaves a window unchanged (so a rolling mean survives a no-quote gap
  instead of erasing its history — and, stated plainly, a full count window holds its value
  indefinitely after the last contributing sample; time windows go empty instead), and conditionals
  compose: `mean(if(c, x), 10)` is the mean of the last 10 samples where `c` held;
  `if(c, mean(x, 10))` shows the all-samples mean only while `c` holds. Time-windowed forms take a duration instead of a count — `mean(x, "5m")`, `rate(x, "1m")`
  (change per minute, scaled from however much of the minute the samples actually cover, so a filling
  window reads the true rate rather than understating it) — and age samples out against each record's
  own clock, which is the right tool when record arrival rate varies.

## [1.3.0] - 2026-08-17

### Changed
- **Topology focus is now a filter, not a toggle** — and this changes two familiar gestures. Applying
  **Focus** (button or **F**) filters the view to the selection's scope, and that context becomes the
  whole graph: clicking nodes explores *within* it, and focusing again drills deeper. **Esc** steps back
  out one level; **Show all** returns to the full graph; a clickable breadcrumb
  (`All (62) ▸ hedge path (12) ▸ …`) shows where you are. **Clicking empty canvas now clears only the
  selection and dimming — it no longer exits the focus.** If a shown cycle ran through nodes the
  current context can't show, the status line says so rather than cropping the propagation silently.

### Added
- **`series` verb** — stats, threshold crossings and minute/hour buckets over any key or formula,
  computed inside the analyser (assistant, REST and MCP alike). "Where does the spread exceed 0.004?"
  is now one call returning the exact crossing records — each with a `recordIndex`/`byteOffset` anchor
  for a follow-up `read` — instead of an agent paging raw text to do arithmetic. Crossing lists are
  capped with an explicit `truncated` flag, and `filter.text` is refused loudly rather than running an
  index-speed verb at scan speed.
- **Time anchors on `read` and `goto`** — pass `at` (epoch millis) and the analyser resolves it to the
  record at-or-before that moment, so "show me 09:14:03" is one call instead of estimating record
  indexes from record rates. Clamping to the first timed record (when `at` predates the log) is
  declared in the reply, never silent.
- **Field projection on `read`** — pass `fields: ["instanceId.key", "instanceId.*"]` and each record
  comes back as compact `{recordIndex, logTime, event, values{}}` rows instead of ~2 KB of raw text —
  a 10–50× token saving when an agent needs two numbers, not the whole record. Last occurrence per
  record (identical to graphing, so a projected value always matches the plotted one); requested
  fields that matched nothing are named in the reply. Raw text stays the default.
- **Verb echoes now name what they ignored** — a parameter no verb schema declares (usually a typo) is
  listed as `ignoredParams` in the reply instead of vanishing; and a `graph` call whose `rightAxis` or
  note names a series that isn't on the graph gets a `warnings` entry naming it (previously a silent
  no-op, discoverable only by looking at the plot). The `aggregate` verb's `limit` parameter is now
  declared in its schema (it always worked; the manifest just didn't say so).
- **Named focuses** — save the current topology context by name with a rationale (**Focuses ▾** on the
  toolbar, or `topology {saveFocusAs, rationale}` from an agent), recall it by name (picker or
  `topology {focus: "name"}`), delete from the picker. Saved with the project (never the API key),
  shared like saved graphs (replace-by-name), and honest across builds: recalling a focus whose nodes
  aren't all in the loaded topology says how many resolved instead of silently dropping the rest.
  Agents stepping out of contexts use `topology {pop: true | "all"}`.

### Fixed
- **The share dialog now discloses that named focuses travel with the Graphs category** — the
  checkbox reads "Graphs and named focuses" and the sharing guide's category table says what rides
  along (node sets and the rationale; nothing secret can — the API key is excluded by construction).
  Found in review: the import preview said it, the export consent didn't.
- **Graphs made while a project was active were never saved to the project — and could be lost
  entirely.** Graph edits only reached disk at exit, and the project file was always written with a
  stale (empty) graph list; quitting with no log open could even wipe previously saved graphs. Graph
  changes (from the UI or the `graph` verb alike) now persist as they happen, to the active project's
  file; every profile write captures the live tabs first; and the project is flushed on quit.

## [1.2.0] - 2026-08-17

### Added
- **An assistant can show you where a control is.** `analyser_screenshot` accepts
  `scope: "menu:File"`, which opens a top-level menu and captures it — so "it's under File" can be a
  picture instead of a sentence. The window is raised first, because a screen capture would otherwise
  include whatever happens to be sitting on top of it.
- **Project profiles.** Settings now have two tiers: machine things (API key, theme,
  recent files) stay in the global config, while a project's source roots, Maven repos, event
  processors, saved graphs and hidden columns can live in a `.analyser/project.fluxtion-settings` file
  beside the project. Switching projects **replaces** those settings instead of piling them on top of
  the last project's. A profile can never contain your API key, so a team can commit one.
  **File ▸ Open project / New project / Save project as / Close project**, with a recent-projects list
  and the active project in the window title. Edits save to the project as you make them (debounced, so
  a committed profile keeps legible diffs) — including edits made by an assistant over the socket.
  **File ▸ Import settings** now asks whether you mean *Merge* (share a setup, additive) or *Open as
  project* (replace).
  Documented in **[Working across projects](user-guide/projects.md)**.
  Opening a log that sits inside a project **offers to load that project** — so a downloaded bundle
  configures itself. It asks once per log, never for a project already open, and takes "no" for an
  answer for the rest of the session.

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
