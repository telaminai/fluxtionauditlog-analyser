# Changelog

All notable, user-visible changes to the Fluxtion Audit Log Analyser.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org).
Add a line under **[Unreleased]** with every user-visible change; the release workflow stamps it.

## [Unreleased]

### Added
- **An empty log now says it is empty.** A file with no records reads as exactly that, in all six shapes
  it can take, instead of opening silently with nothing in it and leaving you to guess whether the run
  produced nothing or the reader found nothing. A file with no index supplied is untouched — that means
  *no index*, not *no records*.
- **A document that is not a log is named as one.** A YAML file that never opens a record is reported
  rather than read as a log with no content. The test is the file's framing — its first non-blank,
  non-comment line — not a search for the key somewhere in the text, so a document that merely mentions
  `eventLogRecord` is still not a log. The warning quotes the line it found and says what a stream-end
  marker would and would not establish, rather than asserting a verdict.
- **An uncovered node whose audit level was changed now says so.** A node set to `WARN` still runs, but
  its info lines are suppressed, so it carried no entries and coverage listed it as uncovered with no
  explanation — while the record stating the change sat in the same file. That change is now shown
  beside the node, naming the records that open and close it. It is an annotation, never an excuse: the
  node stays in the uncovered list and in the ratio, and the annotation is read even when a filter hides
  the record it came from. A change applies exactly as the runtime applies it: a change naming no node
  sets every node, a change addressed to another processor grouping does not apply, and a later change —
  per-node or global — ends it. A node name is compared exactly as written, so a name with extra
  spaces or punctuation addresses no node. Records are matched to a processor by the grouping each
  declares, so one processor's change never explains or ends another's, and a record that declares no
  grouping is qualified rather than assumed. For records after a stream-end marker the explanation is
  conditional: the log does not say whether the level survived into the later run. Every explanation
  states each thing the log leaves open — whether the change named no node or a node literally called
  "null", whether it applied, whether it survived a marker — in the one condition its conclusion rests on,
  and the change that ends a window is described with the same care as the one that began it. A change
  the log does not show to have applied is described as recorded, never as having set the level; one
  described as setting the level applied whenever the change before it did, within the same run. A control
  record the analyser cannot read ends the explanation there and says so, rather than being passed over as
  if it changed nothing, and a change after a stream-end marker is named without claiming the level lasted
  until it. Every conclusion is bounded: it speaks for the records after the change and before the window
  ends — the next change, an unreadable control record or a stream-end marker — in the change's own
  grouping, never for the whole log.
- An empty or blank file now opens by its extension rather than being refused as unreadable.

### Fixed
- **A byte-order mark no longer changes a verdict.** A record behind a BOM lost its thread, level and
  logger, and because the finest level is read from there, `auditLevelFinest` fell from DEBUG to INFO and
  coverage went on to say debug calls might be missing. It was not only a first-line problem: a file made
  by concatenating two runs carries a mark in the middle, and every record behind it was affected the
  same way.
- A BOM before a file's first `---` stopped it separating, so the head of a healthy file ran together; a
  file containing only byte-order marks framed as one record instead of reading as empty. A BOM counts
  **only at the very start of a file**: an event value containing a BOM-prefixed `---` line and marker
  lines is not a record boundary and cannot make a log read as complete. Two BOM'd files concatenated
  therefore no longer separate at the join, and the missing-separator warning says so.
- Following a growing log no longer fails when a poll lands inside a multi-byte character; the rest of
  the character is awaited, and until it arrives the log does not claim to be complete. Bytes that can
  never form a character fail loudly rather than being waited for, and the log then says its
  completeness is unknown until it is reopened, rather than keeping the verdict it had before them. That
  failure is reported as damage to the source, first among the log's findings, and reaches the assistant
  and the status tooltip even while the file keeps growing. If the file is later replaced by a longer
  readable one, it is reloaded rather than read as though the new content had been appended; a replacement
  of the same length is not detected, and the log keeps saying its completeness is unknown until reopened.
- A healthy record read through the binary reader is no longer reported as missing its record key.
- An event whose name merely resembles the framework's own control event is no longer counted as one.

### Changed
- Record lines are trimmed of **ASCII whitespace only** — space, tab, CR, LF — matching the format
  specification and the rest of the reader, where one path previously trimmed every Unicode space. A line
  indented with an ideographic or em space is no longer trimmed to its content, and the fields on it are
  lost. YAML permits only the space character for indentation and no known producer emits one.

## [1.20.1] - 2026-09-24

- **An assistant can find its way around the new menus.** Pointing at a menu item that is not where it was asked
  for now says where it is — "'Follow (tail)' is in the Audit log menu" — and a renamed item names its new name:
  File > Reset is now Project > Close log and topology. Asking for the old File menu says what replaced it.
  The assistant's `context` also lists every menu and its items, so an assistant can read where an action
  lives instead of guessing.

## [1.20.0] - 2026-09-24

- The README names both Export settings and Import settings under Project for sharing setups.

- **Project, Sources and Audit log now have separate menus.** Project holds profiles, saved analyses
  and settings; Sources holds source configuration, topology, design and producer diagnostics;
  Audit log holds acquisition, Follow and record export. Source settings shortcuts open their named
  page. Records, Theme, AI and Help remain separate. The toolbar and assistant verbs keep their existing
  behavior. Reset is renamed **Close log and topology** and moved to its own group in Project.
  Recent logs and topologies sit beside their open actions. Saved `menu:File…` spotlight steps are
  now refused; update them to the new visible menu names. Guides and screenshots follow the layout.

## [1.19.3] - 2026-09-24

- **An action that cannot happen now says so.** Open on a saved chart that cannot be shown used to bring
  the Graph tab forward and then do nothing at all; it now explains why — no log loaded, duplicate names
  withholding the definitions, or no such chart — naming the chart. Nothing is said when it succeeds.

- **Zoom and pin now say which one keeps its window.** Both set the visible time range and sit side by
  side, but a zoom is a view and is forgotten, while a pin is saved with the chart and comes back on
  reload. The zoom controls had no tooltip at all and the pin's did not mention that it persists.

- **Duplicate chart names can now be repaired in the app.** A project holding two charts under one name
  still withholds both rather than guessing, but the Graph panel now offers **Repair names…**, which
  names each contested chart, says what it contains, and offers rename or delete for each. Nothing is
  chosen by default and a partial answer is refused, so no definition is removed without being asked for.

- **An ambiguous project no longer blocks unrelated chart work.** Only Delete is withheld while duplicate
  names are unresolved; New graph, Rename and Close work as usual, both in the app and through the
  assistant's `graph` action, and a chart made while names are unresolved is kept when they are repaired.
  Nothing is written to the project until then. Duplicates were creatable by earlier releases, so this
  affected people who had done nothing wrong.

- **An assistant that sends a chart series in the wrong shape is now told so.** `graph {series}` takes
  `"instanceId.key"` strings. An object such as `{expr, label}` used to be turned into a key that could
  never match, saved with the chart, and answered as a success, leaving an empty chart that looked
  finished. A bare string instead of a list was silently ignored. Both are now refused with the right
  shape named (`exprs` for a labelled or computed series), and nothing is changed.

- **An assistant can now read back a chart's plot style.** The `graph` reply and the `context` list of
  saved charts report each chart's style (step, line or points), and saved charts list their series as
  `instanceId.key` rather than in the internal stored form.

## [1.19.2] - 2026-09-24

- Legacy global settings with duplicate chart names no longer interrupt log loading. The Graph panel
  explains why the whole chart set is withheld, chart actions refuse with that explanation, and all
  definitions are retained for manual correction. A valid project still opens normally; no chart is
  silently renamed or chosen over another.

- Chart imports now apply incoming definitions before autosave snapshots the old tabs. Named actions
  reopen saved charts with their metadata; explicit new-tab names cannot overwrite another chart.
  Duplicate chart names in a project/import are refused before applying it instead of choosing one.
  Delete confirmations cannot delete a replacement tab loaded while the question was open. Rename
  collisions on the action socket return a refusal without opening a blocking dialog.

- **Choosing a plot style from the dropdown is now saved.** Setting a chart to Line or Points from the
  style control kept the change on screen but never asked to be persisted, so it reverted to stairs on the
  next load. Only the assistant's `graph {style}` path saved correctly. Fixes the user-facing half of the
  style persistence added earlier in this release.

- **Sharing or importing settings no longer resets a chart's style or reopens a closed chart.** Rewriting
  an external series or marker path rebuilt the chart and silently dropped both.

- **Closing a chart no longer deletes it.** Close now puts a chart away and keeps its definition — series,
  formulas, right axis, explanation and pinned notes — so it stays listed in the Project panel and reopens
  from there, and stays closed across a reload rather than reappearing. Previously the project's saved-chart
  list mirrored the open tabs, so closing a tab silently and unrecoverably destroyed the chart and its
  annotations.

- **New: Delete chart**, beside Close on the Graph toolbar, for removing a chart's definition on purpose.
  It names the chart, says what is lost, and asks first. Existing projects are unaffected: a chart that has
  never been closed carries no new setting and opens exactly as before.

- **Open on a Project-panel row now opens that row's thing.** Open on a saved report reveals *that*
  report instead of whichever one was already selected, and saved charts gain an Open they never had —
  those rows previously offered no action at all. A chart that is saved but not currently
  a tab is opened from the profile and selected; one already open is selected rather than rebuilt, so
  nothing you changed since is discarded.

- **A chart's plot style is saved with it.** Stairs, line and points are part of a saved chart and
  survive a reload. Previously the choice was never written to the profile, so a chart deliberately set
  to line or points silently came back as stairs — the reading of the chart changed without anyone
  touching it. A chart saved before this release carries no stored style and opens as stairs, exactly as
  it did before; the first save after upgrading then records every chart's current style, so an older
  project file does gain a style line per chart once you save it. An unrecognised style in a hand-edited
  profile is dropped rather than applied.

## [1.19.1] - 2026-09-24

- Named project profiles (`.analyser/project.<name>.fluxtion-settings`) now resolve relative paths from the project root, matching the canonical profile. Source roots, runbooks and other project-relative pointers no longer resolve one directory too deep.

- Docs: describe starter 1.0.74 standalone and hosted Spring authoring, generated callback audit facts, matching-version upgrades, and the version-scoped protection for dependency classes. Older-release evidence and remaining limitations stay explicit.

## [1.19.0] - 2026-09-23

- Java spotlight echoes omit `partial` when no line band is measurable, rather than describing an invisible line as partly visible. Line numbers follow the editor, including its final empty line after a trailing newline.

- Spotlight explicit Java documents and logical lines beside topology. Ask an assistant for a Java spotlight to use this feature; opening a log does not activate it automatically. The source pane label and assistant echo disclose what is highlighted. Source reads prepare off the UI thread, expire after ten seconds, and disclose origin, text revision, first-match lookup and unverified relationship to the run. Wrapped Java lines light their visible portion with a `partial` qualification.
- Source viewport changes now remeasure or extinguish Java and design spotlights. Design lines keep the released wholly-visible refusal rule; Java captions go out when their bound source revision changes.

## [1.18.0] - 2026-09-23

### Added
- **A log can say whether it is whole** — audit format 1.1 §1a adds an optional stream-end marker, and
  `context` reports `log.streamEnd` as `complete`, `missing_records`, `more_than_declared`, `unverified`,
  `unterminated_marker` or `unknown`. A file that makes no claim reads as **unknown**, never as complete, so "this node never
  ran" stays a conclusion you have earned rather than one the file's shape implied. The status bar says
  *complete* when a file claims it. The marker is never shown as a record. A **rolled set is never
  reported as complete**, however many of its files say they are: a marker vouches for the file that
  carries it, and nothing records how many files a set should hold, so a set with a whole file missing
  looks exactly like one with nothing missing. A member that lost records still makes the set say so, and
  names the file. See *Analyser assistant ▸ Is the log whole?* and *format specification §1a*.
- A record is never removed from a log because of its own contents. An event whose `toString` happens to
  contain a line shaped like the stream-end marker is an ordinary record, and is indexed, counted and
  shown like any other.
- The marker must be followed by its `---` separator to count. Until it is, a writer may be halfway
  through writing it, and the same bytes mean two different things — so an unterminated final record is
  an ordinary record, never a completeness claim. This is what keeps a live tail and a fresh open of the
  same file in agreement.
- Every number is reported in the scope it belongs to: a run inside a file, a file inside a rolled set,
  the whole log at the top. A verdict about one run of several names that run, and a set's verdict names
  the file it came from. Every run that fails its count is reported, not only the first.
- A log read through a reader plugin, or followed as it grows, shows its completeness verdict to the
  person at the screen as well as to an assistant.
- A log file saved with a byte-order mark reads its stream-end marker correctly.
- A file that ends with a stream-end marker its writer never terminated is reported as exactly that,
  and the unfinished marker is not shown as a record. Producers must write the separator after their
  marker; until they do, completeness is unknown rather than guessed at.
- Inside a rolled set, a verdict about one run reports the rows `read` and `goto` accept — the whole
  set's numbering — with that file's own numbering kept beside it.


## [1.17.0] - 2026-09-21

- Context keeps loaded log sizes separate from on-disk metadata, flags changed graph/log/result inputs, and labels producer hash comparisons as of intake. The Project panel and exported reports carry the qualification.

- Design spotlights settle scrolling before measuring and refuse lines outside their text viewport; adding a distant target reports departed highlights.

- New marker conditions use same-record values by default. Explicit LOCF and older saved definitions retain carried state, disclosed in the legend, echo and PDF notes.

- Saved chart pins now state their scope and explain windows outside the new log’s series; context, graph echoes and report captions carry filters and extraction status.

- Hidden Project spotlight sections reveal before measurement; marker-only targets give an explicit unsupported-target refusal. Design-root refusals name the exact opt-in call, and PDF flag glyphs use readable text.

- Formulas accept `true`/`false` literals and typed text equality/inequality, shared by charts, series queries, markers and report row highlights.

- Topology and coverage disclose unknown dispatch hierarchy; missing supertype edges no longer shade unlogged branches as off-path. Complete invocation traces retain their stronger claim.

- Assistants can start/stop the existing Follow control with standalone `open {follow: true|false}`; unsupported or still-loading readers refuse.

### Fixed
- Topology Show all exits every focus through both the toolbar and MCP, including calls that change
  scaffolding visibility. Chart windows keep left/right scales separate through pin/filter/restore.
- Chart legends reserve space beside the plot; explanations and note text sit below it. Nearby note
  pins combine into numbered ranges above the plot, and exported charts include the legend.
- Ordinary YAML opens include the final EOF record, consistent across heap and mapped stores.
  Explicit Follow reopens an EOF snapshot as a live read and withholds its tail until a complete
  separator arrives; a quiet interval never completes it. Missing separators do not prove damage.
- Windowed rolling-series queries retain earlier history, so delta changes at the lower bound match
  whole-log results and an existing threshold state is not reported as a new crossing.
- Confirmation findings use Observation / Assessment across the shared views and PDF exports.
  Flag categories and text survive explicit session recovery against the verified same log.
- Graph discovery reports disagreeing copies with fingerprints, node counts and missing logged ids.
  Opening a graph also checks configured roots and its directory, announcing conflicts without refusing it.
- Graph/log pairing includes every declared node, including framework nodes that log, consistently
  across discovery, the topology view and session state. Hiding scaffolding no longer changes the verdict.

### Documentation
- Refresh demo, chart, topology, conversation and Spring guide screenshots; update the guides and
  recorded conversation echoes for pending tails, unknown hierarchy and the chart layout.
- Canonical Mongoose guidance documents snapshot audit export, the observed 1.0.43 tail/listing limits,
  and the live-reader delivery boundary; no polling command or completed starter update is implied.
- `point-at-the-fault` now opens with what to do when fixing a fault — keep the broken run's export,
  rebuild rather than regenerate for a body change, never edit shipped data, and no running analyser is
  needed — and `add-a-node` asks authors to log the state behind a decision. Skills index re-pinned.
- The `point-at-the-fault` skill now says how to write up a fault: the symptom from the cited record, the
  cause from the fix diff plus before/after copies of the changed sources, so a reader without the session can
  check both. Skills index `m19-skills/2` re-pinned; the playground must re-vendor.
- Qualify C++ as a preview target and limit the reported Java/C++ equivalence to the tested graph
  and event stream; the vendor experiment does not establish general supplier compatibility.
- Add a vendor-integration worked example and supplier checklist, qualify composition and topology
  claims, and document the open dependency-shadowing and new-node audit-scaffolding gaps.
- Add a guided Spring authoring path with real project/design/validation screenshots, a client prompt,
  runbook and prerequisite checkpoints.
- Update the Spring guide for the verified 1.0.73 provisioning fix, with upgrade instructions and
  the separate hosted-template tooling limitation.

## [1.16.0] - 2026-09-20

### Fixed
- Session recovery returns to an actionable offer when a newer open supersedes it, and stale start-page
  buttons cannot answer another project's offer. Recovery rechecks are decided by the session graph;
  changed log sets are withheld before publication as one unit.
- Built-in YAML and rolled-log opening compute full content identities from the indexing read rather
  than making two extra file traversals. Full verification remains required at every file size.

### Added
- **Cold-start corpus check** — `tools/check_coldstart_corpus.py` replays the reviewed scorer defects
  against preserved public evidence, without starting model sessions or using a key.
  It also fingerprints finished projects with `--project` and `--baseline`, without a journal;
  the corpus and observer-clock regressions run in static CI on every commit.
- **Whole starter catalogue** — File ▸ New project from template and the start page's authoring action
  show every entry, marking recommended starting points and disclosing declared build/regeneration key
  needs and agent entry files. Missing and explicitly empty declarations remain distinct.
- **Explicit session recovery** — project reopen and app relaunch offer the previous local session.
  Accept or dismiss on the start page or with `open {restore}`. Recovery verifies file bytes, restores
  unchanged log sets, topology, design and diagnostics, and reports missing inputs and withheld views.
  Remembered global log and topology paths no longer open implicitly at launch.
- **Project landing and processor intent** — a project opened without a log shows its own roots,
  runbooks, saved charts and declarations, with explicit evidence-open actions. Profiles can distinguish
  a declared generated type, runtime processing and an unspecified type without inventing an FQCN.
- **Saved chart definitions before a log is opened** — the Project panel and `context.savedGraphs`
  list saved charts separately from live graph tabs, with an explicit waiting-for-input state.
  An open tab does not claim that its series are bound to valid data.
- **Local Spring rehearsal launcher** — `tools/start-spring-demo.sh` provisions the existing acceptance
  sample, its project MCP configuration and an isolated analyser, plus playground/docs previews.
  `tools/stop-spring-demo.sh` stops its services while retaining project edits and evidence.
- **Spring design on the canvas (M66)** — open a session XML design, browse its bean index in Source ▸
  Design, and use `source` for a fresh, read-only file/bean/line/class glance. Design Follow works without a
  log; bean spotlights follow identity across edits and qualify old captions. File ▸ Open producer diagnostics
  shows validation, reconciliation and compiler findings with explicit location and input-freshness limits.
  Bean/node/record links are navigation by name; their relationship to a loaded run stays unverified.
  Configured source roots now also authorise XML and producer JSON reads; opening a project does not grant
  access by itself. Unsupported or unreadable producer results clear the previous findings.
- **Spring authoring guide** — working with an LLM as a design partner, the local XML/source workflow,
  and the analyser as the shared canvas for evidence, charts and investigation reports. Flow diagrams
  and illustrative design/"what if?" conversations show how a question becomes a repeatable scenario.
  The guide distinguishes the published local starter workflow from analyser features and names
  the minimum analyser version for design and producer findings.

### Fixed

- Correct the Mermaid sequence-diagram syntax on the Spring authoring conversations page.
- Keep both Spring authoring guides tied to the compiler release that includes the starter tool; the downloaded project holds the exact version pin.

### Changed
- Upgrade the analyser's Fluxtion runtime to released 1.0.16 and its regeneration builder to 1.0.71.
  This is an owner-requested dependency update alongside M66; XML rendering itself needs no newer API.

## [1.15.0] - 2026-09-18

### Added
- **A spotlight target can name its chart.** `graph:<name>:note:<n>` and `graph:<name>:series:<label>` light a
  note or series on the chart called `<name>`, selecting that chart first; the bare forms still mean the selected
  chart. When the selected chart does not have the target, the refusal now names the selected chart and the charts
  that do, in the form to send next.
- **A spotlight target can be a menu item.** `menu:File` lights a top-level menu's open popup and
  `menu:File:New project from template…` lights one item in it: the menu opens, the item is cut out inside the
  window, a `screenshot` paints the open menu into the shot, a click on the lit item chooses it, and the spotlight
  goes out when the menu closes (Escape, a click elsewhere, `{clear: true}`). A submenu's items and dialogs are
  not reachable. An assistant asked *"where is that setting?"* can now point at it.

### Changed
- The spotlight guidance every client is handed says that a call **replaces** what is lit unless it sends
  `add: true`, so a step-by-step walk lights its steps in one call or adds — a context-free client narrating a
  cycle lost step 1 when it lit step 2.

### Fixed
- **`graph` given the same `external` label twice in one call now draws one series, not two — and keeps it in its
  first slot,** so with `x, y, x` the legend's swatches still name their own lines (the first cut of the fix
  appended the replacement, and the legend and plot disagreed on the colours).
- **The REST `/manifest` now carries `instructions`** — the same text the MCP bridge hands every client — so a
  REST client reads the one guidance rather than a copy. `external` is
  replace-by-label, but one call carrying a label twice drew two identical legend rows — which a spotlight then
  rightly refused to point at. The later entry applies and the echo's `warnings` says so.

## [1.14.1] - 2026-09-17

### Changed
- **`screenshot {scope: "menu:File"}` says where each menu item is.** With a menu open, the reply now lists the
  menu's items — text, whether enabled, and bounds relative to the window it reports — so an assistant, or the
  docs build, can point at *New project from template…* instead of describing where it is.
- **The tutorial's first step shows where to click.** The File-menu and template-picker pictures on *From
  playground to analyser* are now marked: a ring on *New project from template…*, then ① the bundle and ② *Use
  this template*. The marks are drawn by the documentation build from positions the app reports, and are styled
  unlike the analyser's own spotlight on purpose — that covers the main window, not menus or dialogs.
- **The two getting-started pages now show what they describe.** *From playground to analyser* adds the in-app
  route to the same bundle — *File ▸ New project from template…*, with the File menu and the template picker
  pictured — and says what the bundle had all along but the page never mentioned: it declares `guided-start` as
  one of its runbooks, so with an AI client connected *"give me the guided tour of this project"* is the whole
  prompt. *Guided start* gains its first screenshots (the Start page it opens on, and a spotlight mid-tour) and a
  closing pointer to the tutorial. Its prompt still uses the demo set, on purpose: nothing to build, no key.

### Fixed
- **The left column no longer starts expanded and empty.** With *Event types* and *Project* both toggled off, the
  analyser opened with the left column as wide as it was last dragged (517 px in the report) and nothing in it —
  it looked like a panel that had failed to draw. Toggling the panels off while running collapsed the column to
  its rail; starting that way did not, because the rule was written twice and the startup copy ignored what was
  showing. It is one rule now, and a panel that IS showing still reopens at the width you chose.
- **`context` no longer tells an assistant to call a verb that does not exist.** While the session's posture is
  only a guess, `context.handoff.posture.note` said *"set it with handoff {posture}"* — but that verb was folded
  into `open` before 1.14.0 shipped, so an assistant that followed the note was refused. It now says
  `open {posture}`, and a test holds every shipped text to never showing a call to the removed verb.

## [1.14.0] - 2026-09-17

### Added
- **An AI client can now point.** It could already open, filter, select, draw and screenshot; what it could
  not do was say *"this, here"*. The new `spotlight {target, caption}` action dims the window, cuts one named
  thing out, and draws a short caption with an arrow to it — so when an assistant explains a result it can
  light the record, the node's lines in the detail, the node on the graph, a numbered note on a chart or a
  series in its legend, rather than describing where to look. Targets are a small fixed vocabulary named as
  you would say them (`tab:topology`, `records:row:12`, `detail:node:<id>`, `topology:node:<id>`, `topology:verdict`,
  `graph:note:2`, `graph:series:<label>`, `project:log`, `toolbar:flag`, `status`). A target that is off screen
  is brought on screen first — a filtered-out record is revealed the way `goto` reveals one — and one that
  does not exist is refused with the reason, never lit on nothing. A chart series is named by its **exact**
  label: a prefix of one (`graph:series:quote`) is refused rather than bound to the first series that starts
  with it, and a label that two series on the chart share is refused too, saying there are two — lighting the
  first would be a guess. The caption is tagged *assistant*, because
  it is the client's words and not something the analyser established. A spotlight goes out on any click,
  Escape, `{clear: true}` or any action that changes the view, and nothing about one is ever saved. The
  guided-start skill now spotlights before each beat speaks. **MCP clients see 15 tools** — `analyser_spotlight`
  is the new one.
- **A runbook that finds a fault now ends in "here it is".** A new skill ships with the analyser,
  `point-at-the-fault` (*AI ▸ Find skills…*, and in every generated starter once the playground re-vendors): the
  shape of a runbook that checks a log for ONE class of fault — state it as something checkable, let the analyser
  scan for it, confirm it on the record, then light the record and the node that reported it, with a caption quoted
  from the match. Its two rules are the point: the pointer follows the evidence, never the script; and finding
  nothing is a result — it says what it checked and lights nothing. Its worked example runs on the demo data, and
  the release checklist replays it call for call.
- **Several things can be lit at once, each with its own numbered callout — and assistants are now told
  when to point.** A finding is usually a relation: *this* node, *that* record, the crossing on the chart.
  `spotlight {targets: [{target, caption}, …]}` lights up to six together; they are numbered on screen and in
  the reply, so the assistant's sentence ("1 feeds 2, and 2 never logged") finds its place on your screen.
  `{add: true}` keeps what is lit, `{clear: true, target}` puts out just one, and a set is all-or-nothing —
  one target that is not there, or two that cannot be on screen together (different tabs), refuses the whole
  call with the reason, and a call that is simply wrong (a misspelt target, a seventh, a record the log does
  not have) is refused before anything moves: your filter and selection are exactly as they were. A number is never reused while the set
  stands, even after the highest one is put out, because your chat has already said "2". Callouts are placed
  to avoid covering one another or another lit target where there is room; in a crowded window the
  least-covering side wins, and a callout can still sit over content that is not lit.
  The guidance *point before you explain* now reaches an assistant however it connects — the built-in
  assistant, a copied prompt, or an MCP client — instead of living only in one skill; so you can simply say
  *"show me"* or *"highlight it"*. The user guide's new **Ask it to show you** section has what to say, in
  light and dark.
- **A screenshot shows the spotlight.** The `screenshot` action paints the window's content, which a
  glass-pane overlay is not part of, so a client checking what it had lit would have got an undimmed image.
  It now composites a live spotlight exactly where it is on screen.
- **A shared canvas for you and an AI client: the session's posture, and the authoring handoff.** Two
  things both of you can now see and either of you can set. **Posture** — whether this session is
  *research/support* or *authoring/deploy*. The analyser guesses it from what is open and says when it is
  only guessing; *AI ▸ Posture*, or the assistant's `open {posture}`, sets it, because intent
  changes before any file does. **The authoring mode selector's record** — the modes in force, the figures
  the catalogue resolved and what is left to author — placed with `open {record}` or *AI ▸ Place
  mode-selector record…*. Both appear on the Project panel and in `context.handoff`, each attributed to
  whoever set it. The analyser never runs the selector and does not check the record; a malformed one is
  refused whole, with the reason — including one whose `branch` is not a string, which used to be stringified
  into a record that looked valid. It is session state: a project switch clears it, `open {close: "handoff"}`
  takes it off again (the same idiom as `open {close: "project"}`), and nothing is written to the project
  profile. It lives on `open` — the action that already means *put this in force* — so it adds no tool; a
  canvas write goes alone, and combined with anything else `open` does it is refused rather than half applied.
- **The in-app help explains posture and the mode-selector record.** *AI ▸ Posture* and *AI ▸ Place
  mode-selector record…* were described only by their tooltips and on the docs site; the Help menu never said
  what posture is for, what the authoring mode selector is, or that the analyser carries its record without
  running or checking it. The help's *LLM assistant* section and the docs' *Working with AI* page now do.
- **The built-in assistant is told about every action and every parameter.** Its list of actions is the one
  written by hand, and it had drifted: it never mentioned `open`, `topology`, `coverage`, `report`,
  `screenshot`, `context` or `source_root`, nor seven parameters of the ones it did (a flag's `fix`, a chart's
  `notes` and `explanation`, a rolled set's `file`, …) — while an MCP or REST client, whose list is generated,
  was offered all of them. The missing ones are now derived from the same schemas, and a test holds every
  published action AND parameter to having a line, so the next one cannot be forgotten.

### Changed
- **Closing the log while one is still loading now cancels that load.** *File ▸ Close log*, *Reset* and
  the assistant's `open {close: "log" | "all"}` used to close the previous log and let the pending one
  land anyway — you asked for nothing to be open and a log arrived two seconds later. A close is now a
  request the session processor hears, and one that covers the log supersedes a pending open exactly as a
  newer open or a project switch does: the late result is refused, the busy indicator clears at once, and
  the assistant's echo names what was cancelled in `supersededPendingOpen`. Closing only the graph leaves a
  loading log alone. *Close log* and *Reset* are available while a log is still arriving — including the very
  first load on a fresh analyser, when nothing is open yet and there was otherwise no menu command to ask with.
- **The `open {graphml}` echo names the graph's size and its authored count separately.** One key,
  `nodes`, held the AUTHORED count — 10 for the demo graph whose status bar says 20 — beside a pairing
  verdict, where it read as the graph's size. It is now `graphNodes` and `authoredNodes`, and
  `open {discover}` lists `authoredNodes`. **This removes a reply key, deliberately and with no alias:** there
  is no `nodes` key any more, because a key that misleads is not made safe by putting a better one beside it.
  A client that read `nodes` gets nothing where it used to get a number — read `authoredNodes` for the same
  value, or `graphNodes` for what it was usually taken to mean.
- **Follow now refreshes open graphs (M65).** With Follow on, a chart that was already open kept its old points —
  through zoom, Fit, a narrower time range and even an identical re-send of its definition — because the follow
  poll told the table, slider and status bar about new records but never the graphs. Every open chart now
  re-extracts as records arrive. Where the view lands is a rule, not a reset — the view moves only to reveal a point
  that would otherwise be hidden: a chart that already shows the new point does not move; one showing the whole log
  grows with it; one pressed to the live edge slides with it; one zoomed into the middle holds exactly; a pinned
  chart keeps its window; changing the chart's definition still resets the view as before.
- **`graph` gains `refresh: true`** — re-extract now, for an agent driving an analyser whose Follow is off. The echo
  reports `refreshed: "scheduled" | false`; a re-send that changes nothing (same `series`, same `markers`, same
  `bands`) now re-extracts nothing, where `markers`/`bands` used to re-extract on every presence.

### Fixed
- **Recalling a saved analysis over the action socket works again.** Since 1.13.0 every agent
  `open {analysis, bind}` ran its steps and then failed with *"SessionDriver is confined to the thread
  that created it"*: the recall deliberately runs off the UI thread, but the decision it records
  afterwards was submitted to the session processor from that same thread. The decision is now made on
  the processor's thread. Found by re-running the sample-conversations harness, which had not been run
  since the asynchronous open landed; `tools/verify-m46-agent-api.py` now checks it on the built jar.
- **A log reopened at startup is no longer attributed to "you".** `context.log.openedBy` had two values,
  so a fresh `--rest` instance that restored the previous session's log told an agent it had opened a
  log it had never asked for — a sibling run's, when two runs shared a home. It now says *"the previous
  session — restored at startup, not opened in this one"*, and a path given on the command line says so
  too. The Project panel shows the same words.
- **`topology` no longer says "no records" while records are open.** Until a record is selected the step
  cursor is empty, and its label claimed the log was. The position now reads *"no record selected — 10
  open; goto {recordIndex} to step through one"* and the echo carries `recordsOpen`.
- **The slider's follow echo no longer resets an unpinned zoom on every growing tick.** Extending the slider's range
  re-sent the same time window, and a chart re-windowed on it each second — invisible until now only because the
  chart never refreshed at all.
- **The log store is safe to read while Follow appends to it.** Series extraction and the `series` verb walk a
  bounded view (size and spans captured under the index lock, then the text); the store publishes the text before
  the rows that point into it. Before, a walk overlapping an append could throw on its thread, and the graph's
  best-effort error path swallowed it — the chart just failed to update.

## [1.13.2] - 2026-09-17

### Changed
- **Opening a log is now a decision of the session processor, not something it is told about afterwards
  (M44.3).** Every open — File menu, drop, Recent, socket `open {log}`, S3, a rolled set — is a request with an
  operation id; the load is the effect the processor asks for, answered `Pending` at once and `LogOpened` when it
  lands. A load that lands after a later open superseded it is refused and discarded instead of shown over the
  newer log. `context` reports `inFlight: "opening …"` while a load is outstanding, so a hung load no longer
  looks idle. The session driver is confined to the Swing thread and says so loudly if crossed.
- **A graph is judged only when a log really arrives (M44.3a).** The refresh a menu close performs used to re-judge
  the unchanged log and could close the graph that had REPLACED the one judged (1.13.1 review R2-F3); the
  judgement now fires on the arrival only, and the close names the graph it judged. A consequence a person will
  notice: closing a mismatching graph from the File menu no longer pops the "graph closed — does not fit" warning,
  which was that spurious re-judgement.
- **Two more formula golden fixtures, and a stricter fixture parser.** `min(max(x, lo), hi)` is pinned as an
  elementwise clamp and `min(4, 2)` as the number 2 — the M28 guarantee that kept a `min(x, N)` window overload
  out of the language (`rollingMin`/`rollingMax` are the windowed forms). A fixture with a doubled metadata line
  (`expr:` twice) is now rejected instead of silently taking the last (hardening N1).
- **Canonical skills reworded after the 1.13.1 review** (`docs/skills`, index `m19-skills/2` re-pinned):
  `add-a-node` says constructor mapping covers eligible *instance* fields (static fields never; `@ConstructorArg`
  / `@AssignToField` opt in); `run-mongoose-server` says the capture ROLLS daily and nothing deletes old files,
  prefers a fresh capture location over deletion, and scopes the input-tailing advice to the starter's file
  source.
- **Install page: how to pick up a newer release under JBang.** The examples now include `jbang --fresh …` and
  `jbang cache clear`, and the page says plainly that JBang runs its cached jar until you do one of them (a user on
  1.13.0 kept getting 1.13.0 after 1.13.1 shipped).

### Fixed
- **Three lifecycle defects in the asynchronous open (M44.3), found by its independent review.** The completing
  operation's audience is now established before its effects run, so a person re-opening a graph from *Recent* while a
  socket load is in flight no longer makes that load's arrival modal (B1). A project switch during a pending load
  retires the load's "opening …" description at once, and the busy indicator, the pending pairing and a graph opened
  next follow the gate rather than the discarded reader — immediately, whether the switch then succeeds or fails; a
  no-op re-open of the active project and a bad project path still leave the load alone (B2, and its second-pass
  remainder). A superseded load's failure is recorded as a stale result but never shown: no status overwrite, no dialog
  (B3). Each is pinned on a real frame with a latch-controlled reader, with a positive control beside it.
- **The session processor's exported audit snapshot reopens as one record per dispatch.** The export carried no
  `---` framing, so the analyser read fifteen records as one (pre-existing; review F4). Framed now, and the test opens
  the file through the real store and counts.
- **Note rules stay on their points when zoomed in.** The dashed rule a note pins to a moment was placed from
  the view bounds truncated to whole milliseconds, while the series use the exact bounds; zoomed to a window of a
  few milliseconds every rule drifted up to a millisecond's width (tens of pixels) right of its point. Notes now
  use the series' own pixel mapping.

## [1.13.1] - 2026-09-16

### Added
- **`read … fields` names the nodes that ran but logged nothing.** A record's projection carries
  `tracedOnly: [instanceId…]` when a wire trace marker said the node ran and it logged no value in the record, and
  for legacy text logs `traceLikeOnly` when every entry the node wrote is the tracing regime's `thread`/`method`
  and a `method` entry is present. Both are decided per node over ALL its contributions to the record, so a
  trace-only first contribution beside a value-bearing second one is not "logged nothing", and a lone business
  key spelled `thread` is not trace evidence (independent review B2). An empty projection read exactly like "did
  not appear"; the difference is now visible where the values are.

### Changed
- **Builds against `fluxtion-builder` 1.0.68** (was 1.0.67) for the fixture-regeneration profile; the default build
  still resolves only `fluxtion-runtime` 1.0.15.
- **The canonical skills learned from an agent session on the template bundle** (`docs/skills`, index
  `m19-skills/2` re-pinned): `add-a-node` also covers making an existing node log its values (a body-only change,
  no regeneration) and states the constructor-mapping rule — derived state `transient` or `@FluxtionIgnore` —
  before the regenerate step; `run-mongoose-server` says the export is cumulative across restarts, that a stop
  must be confirmed clean (the registry entry has been seen left behind), and that the input file is edited only
  between a stop and a start; `load-audit-log` says the `open` echo cannot judge the pairing and
  `context.graphPairing` is the authority. Bundles pick the new bytes up when the playground next vendors them.

### Fixed
- **The Source panel no longer crashes the event thread with a `StackOverflowError` on a generated processor.**
  The Java highlighter matched string and char literals with a regex whose alternation sat inside a repetition,
  which the regex engine matches by recursing once per character; an UNPAIRED quote — the apostrophe in a
  generated javadoc's "the auditor's HashMaps" — made it scan the 4 KB of file after it and overflow the stack
  (reported on 1.13.0 from a JBang install). Literals are now scanned by hand: a literal ends at a line end (LF or
  CR, even after a backslash), an unterminated one colours nothing, and a text block (`"""…"""`) is coloured whole
  across its lines, an escaped triple quote inside it being content rather than its end (reviews R2-F4, R3-F2).
- **A graph opened while a log is loading is not judged against the previous log — anywhere.** The log loads
  in the background, so the graph was judged against whatever was loaded when the call ran: "no log is open" on a
  first open, the old log's node counts on a re-open, while `context` was right a moment later. The log echo now
  says `loading: true`; a graph opened while a load is in flight (same call or the next one) echoes
  `pairing: pending`; and — independent review B1 — the verdict in force retires with the log it was about, so
  `context.graphPairing`, the topology note and the Project panel say *pending* during the load instead of
  attaching the previous pair's verdict to the new graph. A load that fails restores the still-true verdict.
  The explicit `format` path (`open {log, format: "yaml"}`) now starts the same lifecycle: it used to bypass the
  load-start bookkeeping and judge a graph against the previous log (second-pass review R2-B1).
- **A fresh window judges the pair when the log lands, and a socket-driven arrival never waits on a dialog.** With
  no project opened first, nothing had built the session driver, so the promised verdict was null and `context`
  showed log and graph with no `applies` at all (pre-existing on 1.13.0; independent review F3). A log arriving now
  builds it — and the warnings that arrival raises (a mismatched graph closed) are rendered for THAT request's
  audience: a socket caller gets the text in the status bar, never a modal it cannot dismiss (second-pass review
  R2-B2; the flag used to be set only by project transitions). The audience belongs to each OPERATION, not to the
  session: the socket verbs `close`, `openGraphml` and `selectProcessor` declare it on entry, and the File-menu
  close/reset actions, *File ▸ Open GraphML*, a file drop and *Open recent GraphML* declare it at their entrance, so
  a socket close after a person opened the log no longer inherits the person's audience (reviews R3-B1, R4-F1/F2).
- **A configuration refresh no longer moves the reader.** Re-applying unchanged roots and processor used to scroll
  the processor pane back to its type declaration; an unchanged hit now keeps its viewport and caret (second-pass
  review R2-F5, pre-existing).
- **The Source panel re-reads both panes when the source roots change, and its "No source to show" placeholder
  says where the roots came from.** Switching project (or adding a root) with the same processor selected left
  the previous placeholder on screen — naming the previous project's root — while the new roots already resolved
  the file, because an unchanged class name was never re-read. Both panes now re-read a file whose content
  changed with the roots, a pane showing a miss is always re-rendered so its placeholder names the roots NOW
  searched (independent review F4: the node pane kept the old root when the file was missing under both), and a
  miss is retried on every navigation. The placeholder names the project the listed roots belong to and, when
  the open log sits inside a project that is not in force (a socket-driven open never shows the "Load this
  project?" dialog, so the offer was only a status-line note), names that project with both ways to load it:
  *File ▸ Open project…* and `open {project: …}`.

## [1.13.0] - 2026-09-16

### Added
- **The binary format has a specification and a conformance corpus, and the analyser passes it.**
  The runtime now publishes *FLXA — the binary audit log format* with **twenty-six** fixtures shipped in
  its jar (`f01`–`f26`); the analyser's `FlxaConformanceTest` reads every one through its reader, parser and
  tokenizer, and the format page links the two. "Reads FLXA" now means passing that suite.
- **Open a binary audit log in the analyser.** A `FLXA` binary log now opens like any other, recognised
  by its magic bytes rather than a file extension, so the faster record format is no longer
  command-line only. Requires `fluxtion-runtime` 1.0.15 or later. Truncated logs open too — a half-written trailing record is the normal end state
  of a crashed process, and the reader reports the unusable bytes instead of refusing the file. What
  the binary format does not carry (`groupingId`, `thread`) is left out rather than invented.
- **Compare two audit logs on business outcomes, headlessly.** `ScoreCommand` reads both logs through
  the shipped reader and parser and reports whether every published figure agrees after every scored
  event. It reads Fluxtion's natural form (`book: { mid: 17.1}` → `book.mid`) and a tagged
  `stage`/`value` convention — **the dialect is declared by the caller, never inferred from key names**,
  because the record format reserves none. **It refuses to report a score it cannot stand behind**: an
  event-sequence mismatch, a differing event type, an empty expectation, an expectation carrying no
  figures at all, a figure never published, a figure outside the contract, and any non-finite value are
  each a distinct verdict rather than a silently favourable number. Ten guards, one per comparison
  defect found in this project's history — **five of them found by independent reviewers executing
  against the first implementation.** Exit codes: `0` pass, `1` differences, `2` untrustworthy,
  `3` usage or I/O.
- **The analyser now reads the Fluxtion compiler's graph metadata when a `.graphml` carries it**, and
  says when it does not. A processor built with a recent compiler can declare which nodes are able to
  write audit output at all, what order nodes dispatch in, and whether an update crosses each edge —
  facts the analyser previously had to work out by reading source, and often could not. **Coverage is
  the visible difference:** a node the graph says cannot log is no longer counted as one that stayed
  silent, and that now works without the source in hand, which is the normal case for someone else's
  log. Graphs without the metadata — every graph produced before it existed — are read exactly as
  before, and every answer says which of the two it came from.
- **Guided start: install and tour the analyser with an AI assistant.** A new docs page carries a
  copy-paste prompt that installs the analyser via JBang, opens it, has you connect your assistant, and
  then walks you through three things by driving the UI. A matching `guided-start` skill runs the tour
  inside an already-connected session. The assistant is instructed to point at the screen rather than
  state figures you cannot see, to use your own log if one is open, and never to open anything over your
  work without asking. No key or account is needed.
- **Create a playground starter without leaving the analyser.** *File ▸ New project from template…*
  reads the live versioned catalogue, lets you choose a starter and identity, safely downloads and
  atomically extracts it, then opens its project profile. The HTTPS origin is pinned; traversal,
  expansion, overwrite and executable-bit attacks are refused. Downloaded code is never executed—the
  final dialog only shows and copies fixed lifecycle commands.
- **New project now offers the setup already present in the directory.** After choosing a directory,
  one confirmation lists detected Java source roots, skill-shaped runbooks and GraphML. Every choice
  starts off: discovery never silently adopts project content. Confirmed source roots and skill
  pointers are saved to the new profile, and at most one confirmed topology opens. An empty directory
  is an ordinary empty offer and can still become an empty project.
- **A new project can be given a `CLAUDE.md` that points at the canonical Fluxtion authoring resources.**
  *New project…* offers it as one unchecked box beside the source roots, skills and GraphML it found —
  including for an empty directory, where it helps most. Only entries marked agreed are ever written; a
  Spring-only link is written only for a Spring-authored project; an existing `CLAUDE.md` is never
  overwritten and you are told rather than left wondering; and the file carries links with a reason each
  rather than restating any rule, so improving those pages improves the project too.
- **A downloaded template can be given a `CLAUDE.md` too.** *New project from template…* now offers it as
  one unchecked box on the destination dialog. Only one of the catalogue's templates ships agent
  instructions of its own, so the rest arrive with nothing for an AI assistant to read; a template that
  does ship one keeps it untouched, and the status line says which happened.
- **A generated project's skills are now selected by the template it came from.** The canonical library
  gains an `m19-skills/2` index: `common` is always shipped and a template names the specialisations it
  wants (`mongoose`, `embedded`, `spring`). A new `spring/add-a-node` skill covers adding a node to a
  Spring-XML graph, including the two ways that fail silently. `m19-skills/1` is unchanged.
- **A versioned canonical skills index for generated bundles.** Build/release tooling can retrieve the
  analyser-owned `m19-skills/1` Mongoose snapshot directly from the repository's public raw HTTPS root;
  tests pin the selected tiers, source revision and exact skill bytes. Generated projects remain offline
  snapshots and never fetch this index at runtime.
- **Local Fluxtion build-key management without a first-run gate.** The Start page and
  *AI ▸ Fluxtion API key…* now open one masked dialog for the established
  `~/.fluxtion/fluxtion.apiKeyFile`, including named local profiles. The Project panel states only
  whether that canonical file has a configured key and documents the builder rule: a
  `-Dfluxtion.apiKey` passed to a future build overrides it; `FLUXTION_API_KEY` is not read. The
  analyser never validates the key, redisplays it, stores it in app settings, or puts it in a project,
  share export, action response, status message or console output.
- **A loopback-only playground origin override for local experiments.** `-Dfluxtion.analyser.playgroundOrigin=http://127.0.0.1:PORT`
  points *New project from template…* at a playground served locally. Plain `http` is accepted **only** for
  `127.0.0.1`, `[::1]` and `localhost`; every other origin keeps the HTTPS rule, and the origin must still be
  bare — no path, query or credentials. The template dialog states which origin is in force whenever the
  override is set. It is a JVM property by design and is not reachable from Settings or storable in a project
  profile, because a persisted origin would outlive the experiment and travel with a shared project.
- **Collect a PGO profile until the native build lands, then keep the profile.**
  `tools/bench/land-native.py`. A GraalVM image lands at either ~1.6 ns/event or ~5.5, and **the
  profile decides which**: hold it fixed and four rebuilds reproduce it (1.60/1.66/1.68/1.67), while a
  profile that misses reproduces that too (5.71/5.63/5.61). The compiler is deterministic in the
  decision that matters; what varies is profile *collection*, which is a measurement — two collections
  of one workload minutes apart differ in over a thousand call-count contexts. So the harness collects,
  builds and measures until one lands, then keeps **both** the binary and the profile pair that produced
  it, and prints the `--profile` command that rebuilds it with nothing left to chance. **It refuses rather than reports**: arms that disagree on output are
  discarded unmeasured, a figure at or below the elimination floor is a deleted loop and not a result,
  a run that produced no `RESULT` line is a failure and never a zero, and every attempt — including
  every discarded one — is printed, so exhausting the attempts cannot read as coverage.

### Changed
- **The analyser builds against the released Fluxtion runtime 1.0.15 and compiler 1.0.67**, and its own
  committed processor carries a build fingerprint for the first time. Nothing about the application
  behaves differently; the fingerprint is what lets a future build prove its generated source still
  matches the graph it was generated from. The analyser's own processor graph therefore ships with the
  compiler's full metadata vocabulary (the default since builder 1.0.66): which nodes are framework
  plumbing, which can write audit output, and the dispatch order — the same facts it reads from anyone
  else's graph.
- **The audited event path is faster, and level with native.** Measured on the release candidate
  (RECORDED-BASELINES M61, GraalVM 25.3.4, six-node quote engine with sparse logging): unaudited dispatch
  **7.5 ns JIT / 13.2 native**; the audited binary record under `LOW_LATENCY_AUDIT` **23.8 JIT /
  23.1 native**, with Temurin 21 C2 at 23.2. Separately, an earlier JFR profile had put 56% of the audited path in code that
  resolves names which never change: an `IdentityHashMap` lookup per event to id the event type, while
  the identity table built for exactly that sat unused; per-logger key caches spread across three cache
  lines per entry; and a resolved-once decision re-checked on every entry. The 1.5–1.9× native deficit
  recorded through this work was never a property of the toolchain — it was the JIT speculating through
  pointer-chasing that closed-world compilation has to execute. Interim figures along the way (M40–M60)
  stay in the baselines file as history; these notes carry only what the release candidate measured.
- **An ordinal audit-key API, added during this cycle, was dropped before release.** It let a code
  model replace `auditLog.info("v", v)` with an indexed call, and measured a real gain — against the
  data-structure fault above. With that fixed it was **slower** than the plain `String` path on both
  toolchains. No API that shipped in 1.0.14 is removed or changed in 1.0.15; node code stays
  `auditLog.info("v", v)`.
- **The analyser's own session audit log now shows where each transaction closed and its effects ran.**
  Effects the session processor decides on — closing a log, applying a project, showing a warning — used
  to be carried out entirely outside the audit record; only the decision appeared. They now run at the
  processor's transaction boundary, so opening the analyser's own log shows the decision, the boundary
  and each outcome in one sequence. No change to what the application does.
- **Project transitions are now decided by an auditable Fluxtion processor, and the analyser can open
  its own audit log to see why.** The rule that a project switch closes the log and graph — and the
  exceptions that it must not, when the project is being adopted *because* a log was just opened, when
  the load failed, and when the project is already active — lived in three places in the UI and could
  only be checked by running the app. It is now one decision graph with a replayable record, which
  distinguishes what was decided from what actually happened: *asked to close* and *closed* are separate
  entries. Behaviour is unchanged, with one improvement: `open {close: "project"}` now reports what it
  really closed rather than what it predicted before closing it.
- **`coverage` now refuses to print a number in two more cases where it would have been misleading**, and
  qualifies it in a third. It already declined over a graph inferred from what ran. It now also declines
  over a graph whose processor was built without audit logging — every declared node would read as never
  logged, blaming the nodes for the build — and over a graph you deliberately opened against a log it does
  not describe, where the denominator belongs to a different system. Keeping that graph on screen is still
  right; scoring against it was not. And where the log was captured below TRACE, the number is still given
  but now says what it hides: a node may have run, logged, and had its output discarded.
- **When one `open` call names several things at once, the reply lists what it did not honour in the
  order they would have been honoured** — largest act first — rather than in an arbitrary order. The same
  parameters are reported; only the ordering changed, and it now tells you which act won.
- **The runnable jar is about 1.2 MB larger** (2.43 MB → 3.64 MB), which is the Fluxtion runtime and its
  one transitive dependency. Building the analyser still needs no Fluxtion API key and no compiler.
- **The playground-to-analyser tutorial now describes the released bundle honestly.** It uses the real
  catalogue entry and paths, opens project/GraphML/log as separate session-safe actions, distinguishes
  the fixed Chronicle export from a followable file, and adds generated anonymous screenshots for
  records, source navigation, graphing and AI-client setup. It also names the remaining starter gap:
  the current audit records contain no numeric business value to graph.
- **The canonical Mongoose skills now describe the real Chronicle export beat.** They discover the
  running server through its registry entry, run the generated project's own YAML export command, then
  open that concrete export with GraphML. They no longer imply that starting Mongoose directly writes
  an analyser-readable YAML file or that a deployment descriptor reveals one.

### Fixed
- **A click in the record detail selects the entry you clicked, never a different property's series.**
  The exact-key click used to extract an identifier from the displayed line, so the reader's `@unkeyed: 42`
  marker resolved to a property named `unkeyed`, a click inside the quoted key `"price: adjusted"` or the
  dotted key `desk.price` resolved to `price`, and a click inside a string value containing `price: 42`
  did the same. The parser now records each key's complete source position with its parsed entry and the
  panel resolves a click only through those positions; a click on a value, on a marker, or on a multiline
  item without an exact position falls back to the node's named-key menu. The Logical view's right-click
  resolves its node through its own layout rather than the raw text's offsets. Logical view, step status
  and report evidence share one formatter that keeps keyless values (shown as `@unkeyed`) distinct from
  business keys named `null` or `"@unkeyed"`, while legacy text values are shown exactly as written.
- **A report table read binary evidence under the wrong grammar.** The table assembly re-parsed each
  record's text as legacy YAML instead of taking the store's parsed record, so a binary log's quoted
  business key `"@invoked": 123` became the trace marker's `true`, a keyless value became a named field
  `@unkeyed`, and a string value kept its quotes. The table now reads the same parsed record the `read`
  verb does, and a test holds the two to the same answer.
- **A logged String or char can no longer pose as a figure, a null, or another record's identity.**
  The binary reader wrote string values bare into the record text it constructs, so
  `"ok, price: 42.0"` read as a second entry carrying a number the producer never published, a
  logged `'` character deleted the entry after it, and a value with a line break could rewrite
  `eventType`. Values that would be syntax are now written in a quoted form the format specification
  defines (§3a, fixture C16), which the **reader declares** through the plugin SPI
  (`AuditLogReader.textEncoding()`) and the tokenizer then decodes losslessly: a quoted scalar is a
  string whatever it spells. Nothing in the text selects a grammar, so a text log — every log the
  text runtime writes — is read exactly as before, including a multiline value whose middle line is
  spelled like a field (fixture C17).
- **The record diff compares values by kind, and numbers exactly.** A number `42.0` and the string
  `"42.0"` were SAME; they are now CHANGED, and the kind is shown when it is what differs. Two
  different logged longs above 2^53 are CHANGED, not narrowed to one double.
- **A keyless value is unnamed evidence, never a figure.** A value logged under a null key reached the
  scorer as the business name `null` and could hide a change under a real key of that name; it
  reached the topology's graph menu and threw. It is kept beside the node and offered to nothing
  that addresses a name.
- **Trace provenance is node metadata, and completeness is never inferred for a binary log.** The
  binary reader's trace marker is a reserved key only it can emit bare (`@invoked`), carried in the model
  as `traced` beside the node's entries — set only by a wire TRACE entry with key 0, never by a business
  property spelled `invoked: true`, and never sharing a value slot with a business key of the same
  spelling (a review had shown the diff and an agent's field read returning `true` where the log said
  `99`). Nothing infers completeness from it, because the binary format carries no such declaration; the
  text format's `method`-on-every-node heuristic applies only to records the text reader produced.
- **Scoring a damaged binary log is untrustworthy, not a PASS.** The score command read a cut file
  through a path that discarded the reader's damage report and printed a normal PASS on the readable
  prefix. It now carries the report: the comparison is printed as readable-prefix only, stderr names
  the damage on whichever side, and the exit code is 2 — for a cut tail and for names the file
  never defined alike.
- **A YAML export of a binary-derived log is refused.** It would re-open as legacy text and change
  every quoted String value; the `.flxa` file is the lossless artefact. CSV export is unaffected.
- **"Copy selected as YAML" consults the same eligibility as the file export**, and declines for a
  binary-derived log with the same explanation.
- **A binary log no longer claims to follow or to anchor by byte.** Its reader declared both and the
  store can do neither, so an agent reading by `byteOffset` was addressing nothing. It declares
  random access by row only, and the index refuses anchoring.
- **A damaged binary log says so.** A cut tail, or references to names the file never defined
  (including String values), used to open silently as a whole log. The reader now reports them
  through the plugin SPI, the store keeps them, and they appear as a *source damage* finding in the
  status bar and the `context` echo — beside the records, never as one.
- **A binary log whose time unit is wrong, undefined, or unstated is refused before a record is
  delivered, not after.** The analyser no longer assumes a header that states no unit means
  milliseconds — a pre-release runtime could write nanoseconds under it. Declare the unit into a copy
  with the runtime's `AuditLogTool --declare-unit millis|nanos --out <copy>`, then open the copy.
- **The neighbours of the round-6 fixes, pre-empted.** The score command reads a binary log through
  the binary reader under its grammar instead of hard-coding the text reader; a binary file in a roll
  set is refused by name rather than probed as an untimed text file; a binary record whose producer
  did not record `endTime` reads as absent rather than as an instant in 1970; a dictionary id
  redefined in a file is reported as source damage; the scorer's verdict says *within tolerance*
  rather than *identical*, which is what it always was. The FLXA conformance corpus grew to the
  twenty-six fixtures the runtime ships, and the analyser passes all of them.
- **The binary record's clock readings are the framework's, and `endTime` is a choice the profile
  makes.** `BinaryLogRecord` took *both* `logTime` and `endTime` from a method chosen by a `-Dclock=`
  **system property** — a benchmark switch that had reached production code — and its default re-read
  the wall clock instead of using the reading `Clock.eventReceived` had already taken. `logTime` is
  now that reading, `endTime` a live second reading when it is recorded, and the property selects
  nothing. **What ships:** the default clock strategy is unchanged, `System::currentTimeMillis`
  (12.9 ns a call, millisecond resolution); `ClockStrategy.fastEpochMillisClock()` (8.0 ns) and
  `nanoEpochClock()` are opt-in projections of `nanoTime` that never re-anchor to a wall-clock
  correction, which is why neither is the default; and `endTime` stays **on by default** — 1.0.14 and
  every release before it emitted it on every text record — and is **off under `LOW_LATENCY_AUDIT` and
  `addLowLatencyEventLog`**, restorable with `recordEndTime(true)`. The unit of `getWallClockTime()`
  does not change; nanosecond timestamps are the next release. Measured on the release candidate
  (`tools/bench/latency-kit/RECORDED-BASELINES.md` M61, GraalVM 25.3.4): the audited binary record
  under the profile is **23.8 ns on JIT and 23.1 native**, against 7.5 / 13.2 unaudited; recording
  `endTime` costs a further 13 ns. Figures this project published before M61 were measured under
  development defaults later reverted by review; M61 supersedes them.
- **Method tracing works in a binary audit log.** `addTrace` wrote into a byte buffer that the record's
  `length()` does not describe, so a trace produced no visible bytes — and because nothing marked the
  record as having content, a **trace-only record was never published at all**. `AUDITED` + `BINARY`
  therefore lost every trace, silently. Traces are now ordinary two-slot entries carrying a new
  `TAG_TRACE(8)`: a node id, no key, no value. Every entry stays exactly two slots, which is the property
  that lets a reader skip one without decoding it. A second defect surfaced with the first: the reader
  counted a trace's absent key as an **unresolved id**, the diagnostic that distinguishes a rolled file
  from a corrupt one, so every traced log would have looked corrupt. Tag 8 was unallocated and no log in
  the wild contains a trace — `LOW_LATENCY_AUDIT` disables tracing — so nothing that exists is broken by
  the change, and readers built against the earlier format report tag 8 as unknown rather than
  mis-decoding it. The normative format specification is updated.
- **A near-miss path on the local assistant socket no longer reaches the handler.** The JDK's HTTP server
  dispatches by longest path *prefix*, so `POST /action/not-a-route` with a valid token executed the
  action, and `/manifest/anything` served the manifest. Both routes now require their exact path, enforce
  their method, and refuse any request carrying `Origin` before anything else.
- **A wrong path on the local assistant socket now says where the right one is.** Requesting anything
  other than `/action` or `/manifest` returned a bare "no context found", so a client holding a valid URL
  and token had no way to learn the protocol — and `/manifest`, which lists every verb and schema, was
  itself undiscoverable. The 404 now names both routes, the request envelope and the auth header.
- **The Project panel now says WHY a processor's source is missing.** *"Source not found"* had two causes
  with opposite remedies — no source roots configured, or roots configured and the class simply absent —
  and one message for both sent half of readers to add a root that could not help.
- **The canonical load-log skill now respects project switching as a session boundary.** When the
  generated project is not already active, it opens the project first and the log plus GraphML in a
  second call; combining them causes the analyser to ignore the log/graph parameters deliberately.
- **The REST endpoint file is now published atomically.** MCP clients and startup automation no longer
  catch the file between creation and JSON write and mistake an empty token file for a broken analyser.
- **New-project discovery now finds committed AOT GraphML under `src/main/resources`.** This is the
  standard Maven location used by generated bundles; previously only Java source roots were scanned.
- **Rejected Fluxtion key-profile names now wipe the submitted credential buffer.** Validation failures
  receive the same caller-buffer hygiene as successful saves and later write failures.


## [1.12.0] - 2026-08-28

### Fixed
- **The MCP status light no longer says "MCP starting" for ever after another analyser window closes.**
  Two windows: the second takes the endpoint (the first correctly reads *MCP elsewhere*); the second closes and
  takes the endpoint file with it — and the first window, whose server had been listening all along, read *MCP
  starting* indefinitely because nothing ever wrote its endpoint again. The 5-second watch now **re-publishes**
  this window's endpoint when the file is missing or names a dead process, and never displaces a live owner.
  Found by the owner's first eyeball run of `tools/verify-m43.py`, which now checks the reclaim too. The light's
  ATTENTION states are also **amber, not red**, as D-AI9 promised — the previous colour was the ⚠ brick shared
  with the Project panel, which reads as red in a one-word status label.
- **A refused table highlight rule no longer prints its legend on screen.** When a `rowWhen` is refused
  because the table's rows have no records (aggregate buckets, coverage rows, series buckets/stats),
  the Reports tab no longer prints "… — rows where …" under the un-highlighted table; the screen now
  matches the PDF, which was already correctly silent.

- **A secret on the analyser's own command line can no longer reach an AI client's config file.** The
  MCP bridge command reconstructed from a `java -jar` launch copied every JVM option before `-jar`, and
  that command is written into the client's own configuration (`claude mcp add`, Codex) and offered for
  copying — so starting the analyser with something like `-Dhttp.proxyPassword=…` put it in a file a
  user may sync or commit. Credential-shaped options are now refused by the same gate that already
  guards report destinations, using one shared definition rather than a second copy.

- **Report table calls now retain nested parameters after restart.** A report can store and reissue
  structured `filter`, `crossings`, and similar call values without flattening them into Java display
  text; existing scalar-only report settings remain compatible.

### Changed
- **Docs: the AI menu is now the documented route to connecting an LLM.** *Connecting an LLM* leads with
  *AI ▸ Connect an AI client…* (reachable mid-session, unlike the Start page), shows the menu, and adds a
  table for reading the status light — including **MCP elsewhere**, the state that quietly wastes time
  when two analysers are open. The Project panel page notes the runbook **description** and where
  pointers are edited.
- **The reports guide and recorded AI conversation now cover every table source.** The guide explains
  aggregate, series, coverage, structured call persistence, scalar context, and record-only
  highlighting; the regenerated “Chart it and write it up” conversation proves an aggregate table and
  CSV export from a real isolated run.

### Added
- **Find skills… — the runbooks your project already has.** *AI ▸ Runbooks…* can now scan the project for
  skill-shaped files (`SKILL.md` with `name`/`description` frontmatter) and offer them, instead of making
  you go and find them with a file chooser. Ones already declared are shown greyed rather than hidden.
  Finding is not adding: picking one opens the same confirm step a hand-typed pointer goes through. The
  scan never leaves the project, skips build output and vendored trees, and names its own cap when it
  stops early.
- **An `AI` menu — everything an AI client needs, where you can find it.** MCP setup used to live in
  Settings and on the Start page, which only appears via *Help ▸ Start page* — so it was invisible
  mid-session, which is exactly when you think to connect an LLM. The menu gathers *Connect an AI
  client…*, the local MCP/REST toggle, the report exchange directory and the docs, and adds the one
  thing that had no UI at all: **Runbooks…** and **Domain glossary…** for the pointers a project
  declares. An item whose precondition is missing is **disabled with the reason in its tooltip** rather
  than opening a dialog that explains itself after the click.
- **An AI status light in the status bar.** It answers one question — would an AI client asking right
  now reach *this* window? **MCP ready** (green) means the analyser is reachable, deliberately not
  "connected": whether a client is actually talking to it is a separate fact. **MCP elsewhere** (amber)
  is the one worth building it for — another analyser window owns the endpoint, so your AI client is
  answering questions about a different log. **MCP off** is grey, not red: it is a choice, not a fault.
- **Runbooks can say what they are for.** A runbook pointer now carries an optional one-line
  `description`, served in `context.runbooks[]` and shown on the Project panel row, so an AI client can
  choose the relevant runbook without opening every file. When you point *Add runbook…* at a
  skill-shaped file, its `name`/`description` frontmatter **prefills the fields** — but what is stored
  is what you leave in them: editing that file later never silently changes what the analyser reports.
- **Report tables now assemble aggregate and coverage calls.** Aggregate buckets carry their metric,
  population, scope and truncation; coverage exports the complete graph-ordered ledger of covered,
  uncovered and excluded nodes with the same denominator and caveats as the MCP action. Empty tables
  state the verb's reason rather than presenting a blank grid.
- **Report tables now assemble all three series shapes.** Bucket, crossing, and whole-window statistic
  tables use the series verb's own result data. Crossing rows link back to their source records and
  retain `rowWhen`; ambiguous bucket-plus-crossing calls refuse explicitly.

## [1.11.0] - 2026-08-27

### Fixed
- **A direct-JAR MCP registration now refuses debug and instrumentation JVM flags.** Setup will not
  copy a JDWP or Java-agent option into the client bridge command, where it could bind an occupied
  debug port or suspend before the client can talk to it.
- **Generic MCP setup shows its complete identity disclosure immediately.** The JSON field is more
  compact and the dialog opens tall enough on ordinary screens to show the JBang, registration, and
  bridge names without clipping the final explanation.
- **Client-registration confirmations no longer squeeze the command.** Codex and Claude Code now use
  a wider confirmation window and command field, so the exact Java/JBang bridge vector is readable
  before either client CLI is allowed to run.

### Changed
- **Installation and MCP names are now explained consistently.** JBang's installed command is
  `analyser`; the separately named MCP client registration is `fluxtion-analyser`. The guides now state
  that this is intentional, correct a stale catalogue slug in the specification, and show where each
  name appears.
- **Working-with-AI MCP guidance now follows the in-app setup.** It explains the resolved JBang or
  Java bridge command, confirmed Codex/Claude Code registration, and the no-token generic fallback
  instead of implying that every client needs a hand-written jar command.
- **The connection guide now shows the setup before it changes anything.** Anonymised screenshots cover
  Generic MCP's copy/save JSON and the explicit Claude Code confirmation, including what Cancel and a
  successful client command do—and do not—prove.

### Added
- **Connect an AI client, without a first-run pop-up.** The Start page now offers Codex, Claude and
  generic MCP setup, with the same entry under Settings ▸ Assistant. It explains which current analyser
  window an agent can operate, asks before it persistently enables local transport, and can check the
  exact local bridge command without exposing the endpoint token. Its status distinguishes this app,
  the bridge, and a third-party client registration instead of calling a local check “connected”.
- **Packaged launches can check their own bridge.** When the documented JBang launcher is not present,
  setup now safely uses a directly launched single jar from the current process. Ambiguous classpaths
  remain unavailable rather than becoming a guessed command.
- **Bridge checks retain the app’s local-home context.** A packaged app launched with a custom Java
  `user.home` now passes that same setting to its bridge child, so the child reads this window’s endpoint
  instead of a different home directory’s stale or absent one.
- **Long bridge commands are now readable.** MCP setup renders the resolved command in a selectable,
  word-wrapping multi-line field instead of clipping an absolute Java or jar path at the window edge.
- **Codex can now receive a confirmed local MCP registration.** Setup detects the local CLI without
  launching it; an explicit check, add, replace, or remove names only `fluxtion-analyser`, shows the
  exact no-token command, and uses the Codex CLI rather than editing its configuration file. A command
  success is kept distinct from a connected client session or approved tool call.
- **Claude Code can now receive a confirmed user-scoped MCP registration.** Setup uses its current
  CLI with an explicit `--scope user`; a project-scope command is copy-only, so the analyser never
  creates or modifies a repository’s `.mcp.json`. A check makes clear that Claude Code may start its
  configured bridge, and a client scope can override another one.
- **Claude Desktop now has an explicit, honest path.** Setup distinguishes it from Claude Code and
  explains why no portable `.mcpb` package is shipped for a per-machine JBang/Java bridge. It directs
  to Generic MCP setup instead of inventing a second launcher or writing Claude Desktop configuration.
- **Generic MCP setup now gives you the complete configuration.** It renders the exact local stdio
  command as selectable JSON, lets you copy it, or saves it only to a file you choose (with overwrite
  confirmation). It never puts the per-run endpoint or token into that configuration.

## [1.10.0] - 2026-08-27

### Fixed
- **A report's table section kept its numeric call parameters.** `recordIndex: 0` in a table's `call` arrived as a
  JSON double and was re-issued to `read` as `"0.0"`, which is no anchor — so the table failed with *read needs a
  recordIndex* although the author had given one. Integral numbers are now rendered whole. Found while recording
  the sample conversations: the harness refuses to publish a failed call, which is what it is for.
- **Coverage no longer counts event classes as nodes that never ran.** The denominator included things
  that can never write audit output — event classes (data entering the graph, not code that runs) and
  exported service interfaces — so a gap that is a **category error** was reported as a low score. On
  the shipped demo that alone read **50%** where the honest figure is 100% of what can log; it now
  reads 83% with the one genuinely silent node remaining. Anything left out is named, with its reason,
  in `excludedFromDenominator` and summarised in `excludedNote` — a denominator that quietly shrinks
  is the same dishonesty as one that quietly includes, and harder to notice because the number improves.
  The excluded list and the scored list now come back in **graph order**, as documented, so a reader can
  line them up against the Topology tab — previously both were scrambled by an unordered copy.

### Changed
- **The Project panel and the Event types panel follow a theme switch** (Theme ▸ …) — both painted their rows,
  section borders and group headers from the theme at build time and kept the old colours; they now re-render on
  the switch, and the panel's warning colour is theme-aware.
- **Project panel buttons say what they do**: *Show* → **Show file** (the file manager), *Go* → **Open** (the
  thing in its tab). A runbook or glossary row's **Open** now reads the file in a read-only viewer inside the
  app — as written, nothing run, nothing handed to an agent — with *Show file* and *Copy path* beside it.

### Added
- **Docs: Working with AI ▸ Sample conversations** — seven LLM → MCP → analyser conversations on the demo set, among
  them *chart it and write it up* (a report with a derived table) and *deploy the fix to UAT and show me it worked*
  (a project environment stamps the UAT log's provenance; the report header says it was matched, not declared). The
  asks and answers are authored; every tool call and echo is recorded from a real run by
  `tools/capture-conversations.py` (the transcript counterpart of the screenshot harness), so the page regenerates
  with the verbs instead of going stale. The tool calls sit in collapsed blocks — open one to see the wire — so the
  page reads as a conversation first. The shipped demo project now declares a `uat` environment over `logs/uat`.
  The compiler-generated no-audit graph is now a checked-in fixture and a real-negative test for M40.1.
- **Coverage no longer counts nodes that cannot log, and says which.** A class with no way to reach an
  audit logger is silent by construction, so its absence from a log is not evidence of anything. With
  source configured, coverage proves this from the class itself — and the note is careful not to sound
  like a clean bill of health: such a node **cannot be observed in any audit log**, so the ratio is
  silent about whether it ran rather than vouching for it. On the shipped demo this is the last
  remaining gap, taking it from 83% to 100% of what can log with the unobservable node named. Proof is
  required to exclude: no source, or a supertype the analyser cannot recognise, leaves the node counted.
- **A coverage gap can be the audit LEVEL, not a silence — and the answer now says so.** When nodes are
  uncovered, the echo carries the levels the log was actually captured at and names what that would have
  discarded: *"every record in this log was written at INFO; if any node logs at debug or trace, those
  calls wrote nothing here."* It states the two facts and renders no verdict — the log genuinely cannot
  distinguish "the threshold excluded them" from "nothing called debug()", so it does not pretend to.
- **Docs: Working with AI ▸ Runbooks, glossary and saved analyses with an AI** — how the in-app assistant or an
  LLM over MCP finds them in `context`, uses them (read the file yourself; the analyser never runs one), and
  creates them (write the file and the pointer into the repository, show the diff — there is no verb, by design).
  Runbook files should be written in the skill shape (frontmatter `name`/`description`, then steps) so one file
  serves the team, Claude Code and the analyser.

### Fixed
- **An older analyser no longer strips a newer one's settings on save.** Both the project profile and the
  own-settings file used to be rebuilt from scratch, so a build that did not know a key dropped it the next
  time it saved — for a team on mixed versions, a committed profile silently losing facts. Writers now carry
  over every key family they do not own and rewrite only the ones they do, so lists still shrink and nothing
  unknown is lost.

### Added
- **Path anchors — a sibling checkout travels.** A project may declare `workspaceRoot=..` (or `../..`): a
  source root or Maven repo under that anchor is written relative to the project with `..` steps instead of
  `~/…`, so `../shared-lib/src/main/java` resolves on a colleague's checkout. One automatic rule, no per-path
  toggle. Runbook and glossary pointers keep refusing `..`. The Project panel shows each root's **stored form**
  and warns when a root under a project is *absolute* or *~* — before the profile is shared, not after it fails.
- **Environments — which system a log came from, declared once per project.** `environment.N.name`,
  `.provenance` and optional `.logDir` (plus `environment.default`) in the profile. A log opened without a
  declared provenance takes the environment whose log directory contains it, else the default — never a
  guess — and `context.provenanceSource` (and the Project panel) say who supplied it: *declared by the
  opener* always wins. A report written against a matched provenance says so in its header — *matched by
  directory, not declared* — so the qualification survives the session. Travels by default under an **Environments** share checkbox whose label names the
  cargo.
- **Vocabulary — the project's glossary reaches the assistant.** `vocabulary=docs/glossary.md` in the
  profile points at a markdown file in the repository (the same pointer rules as a runbook). Its text is
  served in `context.vocabulary.text` and placed first in every *Explain* prompt, so `live` is read the way
  this system means it — for an LLM on an unseen processor and for a support engineer in their first week.
  Travels by default under a new **Domain glossary LOCATION** share checkbox; shown in the Project panel.
- **Report destinations — a place, never a credential.** A project can record where its reports are
  published (`destination.N.name/location`: an S3 bucket, a directory, a ticket system's base URL). The
  analyser states it — `context.reportDestinations`, the Project panel's Reports section — and never
  publishes; the publisher acts with its own credentials. Anything shaped like a credential (user info,
  query tokens, `AKIA…`, `password=`) is refused with the reason at every entrance, and so are the known
  webhook hosts — a webhook URL is a credential in path form. The **Report destinations** share checkbox is
  off by default for that reason; the M38 share categories are now complete and documented.
- **Repeatable analyses.** A project can save *the analysis we run every time* — a named sequence of
  analyser verbs with its rationale and declared parameters (`analysis.N.*` in the profile; `{log}`-style
  placeholders bound at run time). Recall it from **File ▸ Run analysis** or `open {analysis, bind}`;
  `context.analyses` lists the offer. Steps run through the action socket's own dispatcher and stop at the
  first failure. A step can only be an analyser verb, never a project switch, and every path a step names
  must be inside the project (or a `{parameter}` bound at run time) — so a shared analysis can drive this
  viewer, read and write inside the project and the exchange directory, and nothing else; travels by default
  under a **Saved analyses** share checkbox.
- **Runbook pointers — the first slice of portable context.** A project can now record *where* its
  runbooks live — `runbook.0.name=deploy` / `runbook.0.path=ops/deploy.md` in the committed profile — a
  pointer into the repository, never the commands, and the analyser never executes it. Absolute paths, `..`,
  URLs and anything shaped like a command are refused with a reason at every entrance — the profile loader
  (the status bar says what was dropped and why), share import and share export. Deliberately no
  action-socket verb: the reviewed file is the writer. Pointers appear in the Project panel and in `context.runbooks`, and travel only
  under a new **Runbook LOCATIONS** share checkbox that is off by default. Docs: *User guide ▸ Portable
  context* — the three-tier rule the rest of the milestone follows.
- **The Project panel — what is in force, in one place.** A new *Project* toggle on the left rail, under
  *Event types*, states the active project (name, directory, settings file), the open audit log as you
  named it, the graph and its pairing verdict, every configured event processor with whether its source
  was found, and each source root with the tier that supplied it. Every empty section is a sentence saying
  what would fill it. Every button reveals or navigates — copy a path, show it in the file manager, go to
  the tab — nothing on it changes state; a processor whose source was not found offers *Add source* (Settings ▸
  Source roots) instead of a *Go* with nowhere to go. It is drawn from the same `context` payload the assistant reads,
  so a person and an agent see one set of facts. Paths are abbreviated on screen; *Copy* gives the full
  value. The left column is now draggable and its width persists. A sixth section, **Reports**, shows
  where files leave — the assistant's exchange directory, or *File exchange off* and where to turn it on
  (the state that silently refuses an agent's `screenshot`) — and the project's saved reports by title.
- **`context` reports more, and earlier.** The graph is reported whether or not a log is open; `log.openedBy`;
  `graphPairing.graphPath`; `processors` (class, selected, source found, from); `source.rootTiers`; and
  `source` now appears on a fresh start instead of only after the first log has loaded. Also `exports`
  (`enabled`, `dir`) and `reports` (`name`, `title`, `sections`, `from`).
- **The analyser tells you a processor will produce no audit log — before you run it.** Open a
  `.graphml` and it reads whether audit logging was installed at build time: the compiler puts an
  `EventLogManager` node on the graph when `addEventAudit()` was called, and leaves it out when it was
  not. Without it a processor writes **nothing** — not a sparse log, none — however carefully its nodes
  call `auditLog.info(…)`. Measured, not assumed: the same program with and without that one call
  produced two records and an empty file. The **Project panel's Graph section** says so outright, and
  the verdict rides `context.graphPairing.auditLogging`, so a person and an agent are both told before
  opening an empty log and concluding the system was quiet.

### Fixed
- **A project's saved graphs are no longer wiped when you open a log.** With a project active, the
  first log opened under it replaced the profile's graphs with a single empty "Graph 1" — silently, on
  the next auto-save, and on every release since 1.1. Binding a log opened a placeholder tab and that
  counted as an edit, so the profile was rewritten from the one placeholder a moment before the saved
  graphs were restored from it. A placeholder is structure, not an edit; the saved graphs are also
  snapshotted before the log binds. Reported as "opening a project with no log destroys the graphs" —
  that path was checked and is fine; the loss happens on the log open that follows.
- **The Topology tab's split-view *EventProcessor* dropdown is now populated.** It is a separate
  `SourcePanel` instance from the Source tab and was never handed the processor list, so it stayed
  empty even when the Source tab's dropdown was full — the topology's selected processor still
  navigated (it shares the `SourceService`), but you could not switch processors from the split view.
  The choices are now mirrored into it whenever the Source tab's are refreshed.

### Added
- **A guide for answering questions about a running system** — the support path, written round the
  questions rather than the features: what to check in the first two minutes, how to tell a quiet
  system from an incomplete log, the four questions that actually get asked (including "the check
  never fired — is that true?", which is the one a log alone cannot answer), and how to hand an
  answer back so someone who was not watching your screen can review it. It is marked as a draft to
  be corrected by the people doing the work.
- **The docs say the analyser answers to people who did not build the system.** Both top-level pages
  assumed you wrote it — the home page opened "*Your* processor…", and the loop reads
  Author → Guarantee → Deploy → Prove → Assure, which is the builder's path and easy to mistake for a
  prerequisite. Support and on-call arrive cold at *Prove*, and the record answers them anyway,
  because it was emitted by the processor rather than written by whoever built it. Named on the home
  page, in *Why it matters*, and at the point in the loop where you can join it.
- **A "Working with AI" section on the docs site**, replacing the lone *The build-with-AI loop* page:
  the loop, connecting an assistant (moved out of Getting started, so the AI story is in one place),
  and the support guide above. Getting started is now just install and open a log. The support guide
  is also linked from the top of the User guide, because most of it needs no assistant at all.
- **The analyser now says what the PRODUCER got wrong**, instead of opening a broken log without
  comment. Three mistakes, all of which used to fail in silence:
    - **missing `---` separators** — the whole file is read as one record, so the count is wrong and
      every record after the first is invisible. Nothing errored; the log just looked short.
    - **no `nodeLogs` in any record** — usually a graph built without `addEventAudit()`, so there is
      nothing to read, filter or plot.
    - **nothing but `EventLogControlEvent`** — the sink was attached before the audit level was set,
      so the log holds the logging configuration and none of the run.
  Each names the cause *and* the fix. They appear in the status bar, in full on its tooltip, and as
  `producer` in the `context` echo — reported, never repaired, and never as a dialog: an open arrives
  from an agent as often as from a person.

### Changed
- **"Producing an audit log" can now be followed to a working log.** The page explained the ideas and
  then handed the one thing you need — how to actually turn it on — to another project's
  documentation. It now carries a complete, runnable example, and names the three things that must all
  be true: the node extends `EventLogNode`, the graph calls `addEventAudit()`, and something is
  attached as the sink. It also documents the trap found by following it: `record.toString()` does
  **not** write the `---` document separator, and a file without it opens fine and is read as **one
  record** however many it holds — silently. Setting the audit level dispatches a control event
  through the graph, so the page says to set the level before attaching the sink.
- **Getting started is two minutes and needs no configuration.** The Quick start told you the analyser
  opens Settings on a first run — it hasn't since the start page shipped — and sent you off to download
  a sample log, which the jar now contains. It is now: run it, click **Open the demo log**. Source
  roots, the event processor and the topology moved below, where they belong: things you do when you
  bring your own log, none of which block you from reading records. The start page is documented and
  has a generated screenshot (M36.5).

## [1.9.0] - 2026-08-25

### Added
- **"All routes" from a node everything feeds is bounded, and says so.** Focusing scope=routes on a
  sink — a publisher, a P&L aggregate — used to select most of the graph (198 of 309 in the case that
  raised it), because every route into a sink *is* the graph. On a large graph where routes would cover
  more than half, the scope now stops at 3 hops each way and the status line says exactly that — how
  many nodes "all routes" would have been, and that the **≤3 hops** checkbox in the Topology toolbar
  lifts the bound. Small graphs and mid-graph nodes are unchanged. An agent gets the same switch:
  `topology {routeBound: false}` returns every route, and the echo carries `routeBound`,
  `scopeBounded` and a note naming the parameter — being *told* the answer was narrowed is no use
  without a way to widen it.
- **A finding PDF's "where it sits" picture is readable on a large processor.** Above 60 nodes the
  whole-estate view rendered as a grey band — checked at 309 nodes: 8% zoom, the lit path reduced to
  specks. The second picture now shows the cycle's nodes and their immediate neighbours, and its caption
  counts what was left out ("+249 nodes not shown"), so the reader sees the unlit nodes *next to* the
  path — where "the check never fired" is actually visible. Small processors are unchanged.
- **Report pictures fit their labels.** In a finding PDF, node boxes now widen to the longest label
  instead of eliding to "Category…" — on screen hover reveals the rest; on a page nothing does. The
  on-screen view is unchanged.

## [1.8.0] - 2026-08-25

### Added
- **`analyser --rest` — start it for an agent** (M19.7). Opens the app with the localhost REST
  transport on, so a process can drive it on a machine that has never run the analyser; on a first
  run it prints where the REST endpoint file is, rather than expecting anyone to read a dialog. The
  setting persists (Settings ▸ Assistant shows it on) and the console says so. The MCP bridge's
  "analyser not running" error now names this command. Human launches are unchanged.
- **The analyser opens on something you can use** (M36). With no log loaded, the records pane now
  shows a start page instead of an empty table: what the tool does, three questions a log alone will
  not answer, where it sits in the cycle (build → deploy → **ANALYSE** → commit), and three ways in
  phrased as the sentence you would say — *I am building a processor* / *Something is wrong in
  production* / *I want the numbers out of this*. Every action on it runs against a demo log that
  ships inside the jar, so nothing on the page needs a server, a source root or an API key first. It
  is a **state, not a screen**: opening a log replaces it, closing one (File ▸ Close log) brings it
  back, and there is nothing to dismiss. **Help ▸ Start page** recalls it at any time without closing
  the log you are working on — the page offers its own way back.
- **The audit record format has a published specification** (M34.3) — *The audit log ▸ Format
  specification* on the docs site: Format 1, normative, with MUST/SHOULD rules for framing, every
  field, `nodeLogs` values and attribution, what order means under a `TOTAL` or `PARTIAL` reader, what
  absence means, and what a reader declares. It is honest about the two things the design specifies
  that the record does not yet carry. Alongside it, a **conformance fixture set** (`src/test/resources/
  conformance/`) pins thirteen semantics, and every fixture is run through both the built-in reader and
  the plugin SPI — the two must agree record for record, which is the guarantee to anyone writing an
  emitter or an adapter: emit these records and you get exactly what a native log gets.
- **A graph built from what ran no longer implies nothing was missed** (M34.2). On a topology a
  source *inferred* from its own run, every node logged by construction — so "did not run" and "not
  on this path" can never appear, and their absence reads as a clean bill of health. The Topology
  status now says so outright, and the `topology` echo carries the same caveat for agents. `coverage`
  already refuses such a graph; this is the shading half of the same argument.
- **When a log's source offers a graph you already have one open, the analyser says so.** The graph
  you opened still wins — a graph someone named outranks one that merely arrived — but the offer is
  no longer silent, so you know a second, possibly better-matched topology came with this log.
- **A source that cannot promise an order no longer gets one drawn for it** (M34.2). The topology's
  ordinal badge — the small number saying "this ran third" — is the most confident thing the canvas
  draws, and on a concurrent engine that number is arrival order, not causality. When a reader
  declares `ordering: PARTIAL` the badge is **not painted** (the node still shows that it ran, which
  is true, and stops claiming *when*), step-through says "logged 3 / 5" rather than "step 3 / 5", the
  Topology status carries a standing warning, and the `topology` echo carries `orderMeaningful` with
  its caveat so an agent reading the data rather than the picture is told too.
- **An agent can open a project — and close one** (M35.8): `open {project: "<dir or its
  .analyser/project.fluxtion-settings>"}` and `open {close: "project"}`. Until now `context` could
  report *"this log sits inside a project"* and nothing at the socket could accept it — an offer with
  no accept button — and an agent could open and close a log and a graph but not the thing that owns
  both. The verb **applies; it does not ask**: a dialog cannot be answered over the socket, so the
  safety lives in the **echo**, which names every category the switch replaced with before/after
  counts (source roots, Maven repos, event processors, named graphs, focuses, reports, hidden
  columns), what it closed and where those were (the log and graph — a project is a session
  boundary), the project that was active before, and the one call that puts it back. Re-opening the
  already-active project changes nothing and says so. `context` now names the project in force.
- **A log source can now supply its own graph** (M34.1). A reader plugin that knows its engine's
  structure — a workflow registry, a compiler's output — can hand it over with the log, so the
  topology is there without hunting for a `.graphml`. **A graph you opened is never displaced by
  one that merely arrived**: the source's graph fills an empty slot, and `open {graphml}` replaces
  anything. The view always names which it is, because a graph is evidence and evidence has a
  source. And when a graph was **inferred from what ran**, `coverage` refuses to answer rather than
  printing 100% — it subtracts what ran from what was declared, and there the two are the same set.
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


### Changed
- **First run no longer opens a dialog before you can use the app.** Launching without a saved
  configuration used to announce "No configuration was found" and force Settings open — reporting the
  ordinary condition of a first launch as a fault, and demanding source roots and an API key from
  someone who had not yet seen a single record. The start page does that job now, and Settings is
  where it always was (File ▸ Settings, and one click from the start page).
### Fixed
- **The loop bench cleans up after itself when a run fails.** If the analyser died mid-run the bench
  printed a traceback instead of a named failure and left the analyser and the stub server running.
- **Reports no longer run off the right-hand edge.** A report's widest line — usually the
  *"written against … · N record(s) · <range>"* provenance header — was drawn on a single
  unwrappable line, and because it set the preferred width of the whole detail pane, every section
  under it was laid out to match and the tab grew a horizontal scrollbar. Report text now wraps to
  the pane at any width, and callout boxes and tables are as tall as their content instead of
  stretching to fill the pane.
- **A rotating log no longer starts interrupting an agent.** With *Follow* on, a log an assistant had
  opened would re-open as though a person had asked for it when the file rotated — so every dialog
  the assistant path suppresses came back, on the one workflow where nobody is watching the screen.
  A reload now keeps both facts about the open that started it: what it declared, and who asked.
- **Two more dialogs no longer hang an agent-driven open** (M35.9). `open {logs: [...]}` — the
  rolled-set verb — never carried the "this came from the socket" fact, so a set with out-of-order
  records put the time-order report on screen as a modal; and `open {log}` on one *member* of a
  rolled set stopped at the "open the whole set?" question that only a person can answer. Both are
  now data: the report reaches `context` as before, and the set offer appears as
  `context.rolledSetOffer` with the member files, so the agent decides. Under the hood the
  who-asked-and-what-they-declared facts now travel *with* each load (`OpenRequest`) instead of
  sitting in fields that one step could consume before another read them — the shape behind four
  earlier defects in this area, including a suppression that had never worked.
- **Relative paths in a committed project profile are relative to the project, not to `.analyser/`.**
  A profile that said `sourceRoot.0=src/main/java` was resolved against the folder the file sits in,
  landing at `<project>/.analyser/src/main/java` — a directory that does not exist — so the playground
  bundle contract's "open the log and everything is configured" could not have worked. The canonical
  `.analyser/project.fluxtion-settings` now anchors at the project root; a loose `.fluxtion-settings`
  file imported from elsewhere still anchors at its own directory.
- **Opening a project no longer rewrites its committed profile.** Every flush wrote the profile back
  with absolute paths and a fresh `share.exportedAt`, so a project you merely opened produced a diff
  on every teammate's machine. A project profile is now written the way it is read — paths under the
  project are project-relative (before the `~` rule, so a project inside your home still travels),
  no timestamp, no date comment — and a write that would change nothing does not happen. A diff in
  `.analyser/project.fluxtion-settings` now means a setting changed. One-off share exports are
  unchanged and keep their timestamp.
- **The time-order report no longer interrupts an agent.** Opening a log with out-of-order records
  from an assistant or MCP client put a modal dialog on screen that nobody at the keyboard had asked
  for — and which a later passer-by would find describing a log that might already be closed. The
  suppression added for this was reading a flag another step in the same load had already consumed,
  so it never took effect. The report still reaches the status bar, `context`, and the caveat on
  every time-anchored verb; opening by hand is unchanged.
- **`context` no longer fails on a fresh start.** Before any log had ever been loaded, the first
  `context` call an agent makes against a just-launched analyser threw instead of answering "nothing
  is open" — found driving M35.8 against an isolated, never-used config. It now answers with what it
  knows: which project is in force, the source roots, and an empty filter.

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
