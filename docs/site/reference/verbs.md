# Verbs

Every assistant drives the analyser through the same verbs: the in-app assistant, Claude Code in the analyser, and an
outside MCP or REST client. An MCP client sees each one as a tool named `analyser_<verb>`. `GET /manifest` on the REST
endpoint publishes every verb's JSON schema, and is always current
([Scripts and CI over REST](../diagnose/rest-automation.md)).

## Actions the assistant can take

From a reply the assistant runs bounded **actions** (within the round / per-reply caps in Settings) and
feeds the results back. Optional **External MCP/REST access** (off by default) lets an external agent
drive the same verbs:

- **aggregate** — counts / rates over the index (the expensive parse is done once and shared), with
  `firstRecordIndex` and `lastRecordIndex`: the first and last record it counted. Filter to an event to learn when
  the application first logged it.
- **read** — N records around an anchor, so an agent can seek the log through the socket without its
  own file access. The anchor can be a record index, a byte offset, or `at` (epoch millis — the record
  at-or-before that moment). By default each record is raw text; `fields: ["instanceId.key"]` projects
  just the named values per record instead — far cheaper when the question needs two numbers, not the
  whole record.
- **series** — stats, threshold crossings and time buckets over any key or formula, computed in the
  analyser. "Where does the spread exceed 0.004?" is one call returning the exact records (each with a
  `recordIndex`/`byteOffset` anchor for a follow-up `read`), not a page-through of raw text.
- **filter** — narrow every view to the records in question.
- **graph** — plot a series or formula, with an optional `rationale` that **captions the plot** with why
  it was drawn (durable provenance). `guides` draws labelled threshold rules; `bands` shades the
  intervals where a condition held; `external` plots an agent-prepared `(timestamp, value)` CSV beside
  the audit-derived series — the clock is declared, never guessed, reads are confined to the exchange
  directory, and the chart is stamped so a foreign line can never pass as audit evidence; `markers`
  plots discrete events as glyphs (buys ▲ / sells ▼ on a price line) with a payload — an order id —
  on hover, and clicking a marker selects its record.
- **graph {name, delete: true}** — remove a chart's definition. Charts accumulate the way reports
  used to: a probe built to check an expression resolves is still in the profile a week later. It can
  be restored: the definition moves to this machine's chart bin, as does the previous definition of a
  chart a later call replaced.
- **graph {restore: true}** / **graph {restore: "name or id"}** — list the chart bin (each entry with an
  id, its name, whether it was deleted or replaced, and when), or bring one back into the project under its
  name. Restoring never replaces a chart that has the name now.
- **graph {name, close: true}** — take a chart off the screen and keep its definition, the way *Close
  graph* does. For a chart worth keeping; a closed probe still sits in the profile, so delete those.
- **goto** — select a record (by index, byte offset or `at` time); `reveal:true` un-hides one the current filter is hiding.
- **flag** — bookmark records with a `note` and an optional `fix`. Use `kind: "confirmation"` for
  correct behavior, labelled Observation / Assessment; otherwise new findings default to `fault`. This is the **one** place
  a finding is written; it then shows in the records table, as a callout on the Topology graph for that
  record, and in an exported report. Supplying only one of `note`/`fix` keeps the other, so adding a
  suggested fix can't wipe the explanation it's a fix for.
- **screenshot** — write a PNG of the app, or of one panel. `scope: "menu:Audit log"` opens a top-level menu
  and leaves it open, so an assistant can *show you where a control is* rather than describe it
  (`menu:close` puts it back); the reply then lists the menu's items with where each one is in the window.
  The window is raised first: a native capture photographs a region of the
  screen, so anything sitting on top would otherwise be in the picture.
- **report** — two forms. The single-record form writes one record's finding out as a PDF: the
  explanation, the suggested fix, the event, the node log, a picture of the topology as currently
  focused, and optionally a plot. The **investigation form** (`report {name, sections}`) builds a
  named, persistent report from typed sections — findings, records, charts, tables, narrative — the
  *account* of an investigation, not just its evidence. It appears in the **Reports** tab, renders to
  PDF with `path`, and exports a table's rows to CSV with `csv`. A finding section renders what
  `flag` wrote and the verb **cannot** set or change that text; narrative is always visibly labelled
  as narrative. `report {name, delete: true}` removes one — recoverably: it can be brought back with
  `report {restore: "<name>"}` or the Reports tab's **Restore deleted…** — and `report {name, rename}` renames one,
  so an assistant can clear away the diagnostics it built along the way. See
  [Investigation reports](../user-guide/reports.md). Use `restore` alone: boolean `true` lists deleted reports;
  a string names the report to restore, including the name `"true"`.
- **open {project, create}** — create a project in a directory, from what *Project ▸ New project…* would offer
  there. `create: true` is a preview: it writes nothing and returns the source roots, skills, GraphML (to open
  afterwards with `open {graphml}`), whether a `CLAUDE.md` can be offered, and an `offerDigest`.
  `create: {sourceRoots, skills, referenceGuide, offerDigest}` creates it with only what is named from that
  offer, makes it active, and closes the open log and graph (a project is a session boundary). It never
  overwrites an existing project, and an assistant may do it only while **Settings ▸ Assistant ▸ Allow the
  assistant to create projects** is on — off by default; see [the `open` verb](#open) below.
- **coverage** — which of the processor's nodes never wrote audit output in this run. Needs a log *and* a
  graphml, and answers the question nobody can answer by eye on a large graph: what did this run never
  exercise? A gap means "never logged", not proven "never ran" — a node with no `auditLog` call, or one
  whose dirty contract stops it early, is silent by design, and the result says so.

- **spotlight** — point at what it is talking about: dim the window, cut out up to six things, and give
  each a short numbered callout. See [Ask it to show you](../teach/spotlights-walks-reels.md#ask-it-to-show-you).
- **walk** — save a sequence of spotlight steps as a named **spotlight walk** you step through yourself with
  ◀ ▶ on the spotlight, later and without the assistant. One operation per call: `walk {name, title?, steps}`
  saves or replaces; `{name, play: true, step?}` presents it; `{end: true}` ends it; `{name, delete: true}`,
  `{name, rename}` and `{restore}` tidy up, recoverably. See [Save it as a walk](../teach/spotlights-walks-reels.md#save-it-as-a-walk).

`GET /manifest` publishes a JSON schema for every verb, so a foreign agent learns the shapes up front
instead of trial-and-erroring against the structured errors.

## The tools an MCP client sees

The client discovers one tool per verb — `analyser_aggregate`, `analyser_read`, `analyser_series`,
`analyser_filter`, `analyser_graph`, `analyser_goto`, `analyser_flag`, `analyser_coverage`,
`analyser_topology`, `analyser_report`, `analyser_context`, `analyser_screenshot`, `analyser_open`,
`analyser_source_root`, `analyser_source`, `analyser_spotlight`, `analyser_walk` and `analyser_import` — with full parameter schemas, so
there's nothing to paste into a prompt.

### spotlight

`spotlight` lets an AI client **point** ([Ask it to show you](../teach/spotlights-walks-reels.md#ask-it-to-show-you) has the pictures and
what to say). `spotlight {target, caption}` lights one thing; `spotlight {targets: [{target, caption}, …]}`
lights up to six together, numbered `n` on screen and in the echo so the client's sentence can refer to
them; `{add: true}` keeps what is already lit, and `{clear: true, target}` puts out just one (the others keep
their numbers — the chat that named them has already been read). A set is **all or nothing**: one target that
does not exist, or two that cannot be on screen together, refuses the whole call with the reason.

The targets are a small fixed vocabulary, named as you would say them — `source:design`,
`source:design:bean:<id>`, `source:design:line:<n>` (the session design), `tab:topology`,
`records:row:12`, `detail:node:<instanceId>`, `topology:node:<instanceId>`, `topology:verdict` (the line where the
analyser states how the graph fits the log), `graph`,
`graph:note:2`, `graph:series:<label>` (the selected chart) or `graph:<name>:note:2`, `graph:<name>:series:<label>`
(a chart by name — lighting it selects that chart). A chart name may not contain `:` or `"`, or be exactly `note`
or `series`, because those would make it unaddressable; naming a chart that way is refused. A chart saved with such a
name before this rule is reached quoted, `graph:"a:b":note:2`, and `context.graphAddresses` gives that address. A
saved name containing `"` has no address at all: `context.graphAddresses` gives null for it and
`context.graphAddressUnavailable` says why; the chart is kept, and renaming it gives it an address. Then `project:log`, `toolbar:flag`, `menu:Audit log` and
`menu:Project:New project from template…` (the menu opens and the item is lit; a click on it chooses it, and the
spotlight goes out with the menu), `status`. Menu names are the visible labels: Project, Sources and Audit log replace File.
**The ask items' labels depend on the assistant in force** (*Ask Claude: What happened here?* in the Claude Code mode,
*Ask the assistant: What happened here?* in the in-app one), so a saved step naming one of them reaches it only in that
mode: read the current labels from `context.menus` before naming one.
Update saved spotlight steps that name `menu:File`; old names are refused rather than redirected, and the refusal says
where the item is now — `menu:File:Follow (tail)` answers that it is in the Audit log menu, and `menu:File:Reset` that it
was renamed Project > Close log and topology. `context.menus` lists the menu bar as spotlight targets name it — each
top-level menu and its direct items, in order; not submenu contents, and not whether an item is enabled or checked — and
`context.menuChanges` lists what was retired or renamed. An assistant can read where an action lives instead of guessing. A target that is off
screen is brought on screen first (its tab selected, its row scrolled to — a filtered-out record is
revealed the way `goto` reveals one — its node centred); one that does not exist is **refused with the
reason**, never lit on nothing. `screenshot` and `context` leave a spotlight lit: they are how the client
checks it lit what it meant — `context.spotlight.lit` lists what is lit, and a screenshot shows it exactly as
you see it.

### source

`source` is a read-only file glance: `{file}`, `{file,line}`, `{line}`, `{bean}`, `{file,bean}`, `{fqn}` or
`{fqn,method}`. It re-reads the local XML/Java file under authorised roots. `open {design}` declares the
session design; `open {diagnostics}` explicitly replaces its producer findings and clears them on refusal.
Both work without a log. See [Source navigation](../user-guide/source-navigation.md#spring-design-and-file-glances).
The source-root grant now covers Java, XML designs and producer JSON; project membership alone is not a grant.

### open

`open` also writes to the **shared canvas** — state you and the AI client both see and either of you can
set — because `open` is the verb that already means *put this in force*. Two things live there. **The work mode**: whether this session is for *investigating* or
*building*. The analyser guesses it from what is open (a project open reads as Build) and
**says when it is only guessing**; *AI ▸ Work mode ▸ Investigate / Build* — or `open {posture: "authoring"}`
from a client (`posture` in the API, shown as Work mode in the app) — sets it, which
matters because intent changes before any file does (*"let's build something new"*). **The authoring
mode selector's record**: `open {record: {…}}` places the selector's `--json` output, so the modes in
force, the figures the catalogue resolved and what is left to author sit where both of you can read
them (*AI ▸ Place mode-selector record…* does the same from a file). The analyser **never runs the
selector** and does not check the record — it carries it, attributed to whoever placed it. Both are read
back in `context.handoff` and shown on the **Project panel**; both belong to the session — a project
switch clears them, `open {close: "handoff"}` takes them off again (the same idiom as
`open {close: "project"}`), and nothing is written to the project profile. A malformed record is refused
whole, with the reason; and a canvas write goes **alone** — combined with a log, a graph or anything else
`open` does, the call is refused rather than half applied.

`open {analysis: name, bind: {…}}` recalls a saved analysis (*Portable context ▸ Repeatable analyses*) —
`context.analyses` lists them with their parameters; steps run through this surface and stop at the first failure.

`open` also takes `logs: [...]` — an explicit rolled set, loaded as one log in content order, the
echo carrying the order chosen and the time-order report (see [Rolled log sets](../user-guide/records-and-filtering.md#rolled-log-sets)).

Three more on `open`, so an agent can manage what is loaded rather than only add to it:

- `open {close: "log" | "graph" | "all"}` — the counterpart of opening, and the way to switch cleanly
  between systems. Log-derived state clears; your named graphs, focuses, source roots and reports are
  profile state and survive, each saying why it cannot resolve rather than vanishing. Combining it
  with `log`/`graphml` closes and **names what it ignored**, rather than leaving you to guess which
  half of an incoherent request was honoured. Closing the log (or `all`) **while a log is still loading
  cancels that load** — the last deliberate request wins, so a log you no longer want does not arrive a
  moment after you closed — and the echo says so in `supersededPendingOpen`. Closing only the graph
  leaves a loading log alone.
- `open {discover: "graphml"}` — lists every `.graphml` under the source roots, ranked against the
  open log, with each one's node count and how many of the log's nodes it declares. **It opens
  nothing**: pick one and pass it as `graphml`.
- `open {graphml: …}` now answers *does this fit?* in the same call — `appliesToOpenLog`, the counts,
  and the verdict — so switching processor needs no follow-up `context`.

- `open {project: "<project dir, or its .analyser/project.fluxtion-settings>"}` — switch to a
  project, the same act as **Project ▸ Open project…**, and the way to *accept* the `projectOffer` that
  `context` reports. It **applies rather than asks** — a dialog cannot be answered over the socket —
  so its echo carries the safety: every category the switch replaced with before/after counts, what
  it closed and where those files were (the log and graph — a project is a session boundary), which
  project was active before, and the one call that puts it back. `open {close: "project"}` is that
  call when the answer was *your own settings*. Re-opening the project already in force changes
  nothing and says so. Your MCP client's per-call approval on `open` is the human gate.
- `open {project: "<absolute dir>", create: true}` — **preview** creating a project there. It writes
  nothing, selects nothing, and returns `createPreview`: the directory's real path, `profileExists`
  (reported, not refused — a directory that already has a project is opened with `open {project}`), the
  project kind, the project-relative `sourceRoots` (`"."` for the directory itself; a root that resolves
  through a link outside the directory is listed with `outside: true` and can never be adopted), the
  `skills` by path, the `graphs` (informational), `referenceGuide` (`CAN_CREATE`, `EXISTS` or
  `NOTHING_AGREED`), `truncated` when a discovery cap was reached, the `offerDigest`, the raw value of the
  switch below as `createProjectsSwitch`, and the `next` call.
- `open {project: "<absolute dir>", create: {sourceRoots?, skills?, referenceGuide?, offerDigest?}}` —
  **create** it, adopting only what you name from the offer (`create: {}` is an empty project). One write,
  `.analyser/project.fluxtion-settings`, never over an existing file; the reference guide only when chosen
  and offered, after the profile is written. A stale `offerDigest` is refused with "the offer changed since
  your preview — preview again". The echo is the `open {project}` echo plus `created` — the settings
  path, the source roots and skill pointers actually adopted (with each skill's resolved path),
  `skillsNotAdopted` with the reason (a duplicate name, say) and the `referenceGuide` outcome (`WROTE`,
  `EXISTS`, `NOT_REQUESTED` or `FAILED: …`). Opening a graph is not part of a create: call
  `open {graphml}` next. Read each adopted skill's file before acting on the project — the analyser stores
  pointers, not their text. **An assistant needs the person's consent**: *Settings ▸ Assistant ▸ Allow the
  assistant to create projects*, off by default and kept on this machine only. With it on, an assistant
  is still refused inside an evidence bundle's working copy, inside the exchange directory, inside an
  existing project, and through a `.analyser` that is a symbolic link. `create` goes with `project`
  alone; with `follow`, `posture`, `record`, `close`, `analysis`, `restore` or `bundle` the call is
  refused whole, and an unknown key inside `create` is refused (`profile` is reserved: named profiles are
  not created over the socket yet).
- `open {log, provenance: "…"}` — say WHERE the log came from. A file name is not a system: an agent
  exporting three servers' logs to `/tmp` produces three artefacts nobody can tell apart. Provenance
  rides the status bar, `context`, report headers and PDFs, and lets the mismatch banner name a
  *system* rather than a temp file. Omit it and the analyser says nothing — it is never inferred from
  the path, because a guessed system name is worse than none.

!!! note "Opening a log is asynchronous — read `context` or `topology` after it"

    `open {log}` returns as soon as the load *starts*, so its echo cannot carry what the load
    discovers: the declared ordering, the graph the source supplied, the time-order report, the
    project offer, or — when the file is one member of a rolled set — the set offer
    (`rolledSetOffer`, with the member files; `open {logs: [...]}` loads them). All are reported by
    `context` (and `topology`) once the load lands, and none of them is ever a dialog on this path. This is
    one pattern, not four exceptions — if you need any of them, call `context` after opening.

### context

When the project points at a glossary (*Portable context ▸ Vocabulary*), its text leads every *Ask*
prompt and is served as `context.vocabulary.text`, so the assistant reads `live` the way this system means it.

!!! tip "Ask `context` for part of it — `sections`"

    `context` with no parameters is the whole payload, as it always was. `context {sections: [...]}` returns
    only the named sections: `log`, `project` (with the portable context — runbooks, glossary, analyses,
    environments, report destinations), `pairing` (`graphPairing`), `processors`, `source`, `topology`,
    `view` (filter, counts, selection, flags, spotlight), `charts`, `menus`, `design` and `handoff`. The
    file reads and lookups behind unselected sections are skipped (session facts, processor sources, the
    glossary and runbook files, source roots, the topology cursor, chart scopes, skills and the key file);
    cheap in-memory work such as the pairing verdict still runs and is filtered out. Each returned key is exactly what the full context holds for the same state — a
    projection, never a second answer. A warning that qualifies a selected fact travels with it:
    `inFlight`, `dispatchOrder`, `timeOrder` and `producer` are carried whenever they apply (producer
    faults also qualify `topology`), and a rolled set's member list `files` travels with `view`, whose
    selection reports file-local byte offsets. `scope.carried` says why each rode along, so
    `sections: ["pairing"]` during a load or on a log with producer faults still says so. `scope` also names what was selected and every available section. An unknown name, an
    empty list or a non-list is refused before anything is read; a name that is a key rather than a section — `walks`,
    `reports`, `graphPairing` — is refused with the section that carries it (`walks` and `reports` are in `project`). The Project panel is drawn only from a
    full `context`. On the fixed test fixture the full response is 1709 bytes, `["menus"]` 541 and
    `["pairing"]` 817 — measured, not a promise about your session.

`context` is also what the **Project panel** draws ([The Project panel](../user-guide/project-panel.md)): one payload, two
readers. It reports the graph whether or not a log is open, `log.openedBy` (you, the action socket, the
command line that started the app, or — for a log the app reopened by itself — *the previous session,
restored at startup*: an agent should not treat that one as a log anybody chose),
`graphPairing.graphPath`, `processors` as a list with `selected` and whether `source` was `found`, and
`source.rootTiers` — each root with the tier that supplied it (`project`, `own settings`, `demo (transient)`) and
the `form` it is stored in (`project-relative`, `workspace-relative`, `~`, `absolute`), plus `source.workspaceRoot`
when the project declares an anchor.

Five keys an agent looks for by name:
`dispatchOrder` (whether position in `nodeLogs` is dispatch order — *total* — or merely arrival — *PARTIAL*;
never read PARTIAL as causality), `timeOrder` (present only when the log's timestamps are out of order —
the report summary, so disorder is announced rather than discovered through wrong answers), `graphPairing
.sourceGraphDeclined` (a reader offered a graph but an OPENED one holds the slot — opened beats supplied),
`reports[].createdAt`, and `source.workspaceDir` (where the declared `workspaceRoot` resolves on this machine —
two facts, not one).

The project's portable context ([Portable context](../user-guide/projects.md#portable-context-what-the-team-knows); the AI-side manual is
[Runbooks, glossary and posture](../ai-and-runbooks.md)) rides the same payload: `runbooks[]` (pointers —
read the file from the repository; the analyser never executes one), `vocabulary` (with the glossary's `text`),
`environments[]` and `provenanceSource` (who supplied the log's provenance — *declared by the opener* always
wins), `analyses[]` (the offer; recall with `open {analysis, bind}`), and `reportDestinations[]` (places the
publisher acts on; the analyser never publishes).

`graphPairing` also carries **`auditLogging`** (`enabled` / `not_enabled` / `unknown`) with an
`auditLoggingNote`. That is read from the graph, not the log, so it answers **before a log exists** — a
processor built without `addEventAudit()` writes no audit log at all, and an agent told that up front
does not open an empty file and conclude the system was quiet. `unknown` with no graph loaded: with no
evidence the answer is "unknown", never "probably fine".

`context` carries `graphPairing` for the same reason: whether the loaded graph belongs to the loaded
log is something to know **before** deriving anything from it, not after `coverage` returns a
suspicious number. It also reports `projectOffer` when the log sits inside a project — the offer a
human would see as a dialog, which an agent gets as data instead, because a dialog nobody can answer
would simply stall the session.

### Read-only, reversible and destructive

`aggregate`, `read`, `series`, `context`, `coverage` and `source` are marked read-only (`source` rereads XML or Java
under your source roots, and moves the transient source view). The verbs that only change what the app shows are
reversible and marked accordingly. Six are marked **destructive**, so a client can prompt before running them: `open`
replaces the loaded log (taking the session's flags with it), `source_root` writes the persisted config (never the home
folder or a credential folder — `~/.ssh`, `~/.aws`, `~/.config`, `~/.gnupg`, and `~/.m2` itself but not
`~/.m2/repository` — whoever calls), `screenshot`
and `report` write files, `walk` can replace a saved walk by name (and `walk {reel}` writes an HTML file), and `import`
merges a bundle's saved state into your project. A further verb, `propose`, is offered **only** to Claude Code in the
Assistant tab ([Set up a project with Claude](../build/set-up-with-claude.md)), never to an external client.

`screenshot` and `report`'s file outputs are **off by default**. Turning on *Allow assistant file exchange* (Settings ▸ Assistant) lets them
write **only inside the exchange directory you choose**, and they never overwrite an existing file — so a
second export under the same name is refused rather than silently replacing the first. Exports you drive
yourself, through a menu chooser, are unaffected: picking a location in a dialog *is* the
authorisation.

**A project may say where that directory is — never whether.** A repository knows something your machine
does not: that its exports belong in `src/report/shared` beside the evidence they are about. So a profile
can carry *Exchange folder (this project)* (Settings ▸ Assistant), a path **relative to the project root** with
no `..`, and the assistant writes there instead. The permission is untouched: it is yours, it stays in
your own settings, and opening someone's project can never turn the exchange on. A value that is
absolute, home-relative, escapes the project, or names a directory that is not there is **refused with
its reason** and the machine setting is used — nothing is created on your disk by opening a profile.
`context` reports which tier answered under `exports.source`.
Read and export paths also resolve symbolic links beneath that directory; a symbolic link cannot grant access outside
it. These checks use the filesystem as it stands when the request is resolved. They do not lock
directories against a concurrent local process changing them during an operation.

## Every verb's arguments

The tables below are generated from the analyser's own verb schemas, the same ones `GET /manifest` and the MCP bridge
publish, and a test fails the build when they drift. Each verb's description is the text a model is given.

<!-- BEGIN GENERATED by VerbReferenceSyncTest from VerbSchemas: do not edit by hand -->

### `aggregate` arguments

MCP tool `analyser_aggregate`.

Read-only counts/rates over the whole log; never mutates the UI. Answer 'how many' here: records you happened to read are only a sample. With the total it returns firstRecordIndex and lastRecordIndex, the first and last record it counted: filter to the event an application logs to learn when it first logged it. Example: {metric: count, filter: {dimensions: \["&lt;EventName&gt;"\]}} returns how often the application logged that event and, as firstRecordIndex, the record where it first did.

| Argument | Type | Required | Description |
|---|---|---|---|
| `metric` | one of `count`, `rate_per_min`, `nan_count`, `breach_count` | yes | what to compute. breach\_count counts records where the application itself logged a breach flag (a key ending in Breach, set true); a value exceeding a limit is not the same thing |
| `groupBy` | one of `dimension`, `thread`, `hour`, `minute`, `day`, `none` |  | bucketing |
| `filter` | object (`dimensions`, `from`, `to`, `text`) |  | optional scope for the aggregation |
| `limit` | integer |  | max buckets returned (default 500) |

### `series` arguments

MCP tool `analyser_series`.

Read-only: stats and threshold crossings over any key or formula, computed in the analyser — ask 'where does X exceed Y' in ONE call instead of paging records. Crossings are edge events with recordIndex/byteOffset anchors for a targeted 'read'; capped with an explicit truncated flag.

| Argument | Type | Required | Description |
|---|---|---|---|
| `expr` | string | yes | a key ("instanceId.key") or a formula over keys, e.g. "ask.price - bid.price" |
| `resolve` | one of `STRICT`, `LOCF` |  | STRICT (default): all refs co-occur in one record; LOCF: carry each ref's last value |
| `filter` | object (`dimensions`, `from`, `to`, `text`) |  | optional scope (from/to/dimensions; text is refused here — narrow with the 'filter' verb instead) |
| `crossings` | object (`above`, `below`) |  | report where the value ENTERS a region: {above} and/or {below} |
| `limit` | integer |  | max crossing events per direction (default and cap 200) |
| `buckets` | one of `minute`, `hour` |  | per-bucket count/min/max/mean instead of one whole-window summary |

### `read` arguments

MCP tool `analyser_read`.

Read-only: the raw text of N records around an anchor, so you can seek the log through this socket without filesystem access. Max 25 records/call. A window of records is a sample: do not count events from it; aggregate counts the whole log.

| Argument | Type | Required | Description |
|---|---|---|---|
| `recordIndex` | integer |  | anchor by record index (0-based) |
| `byteOffset` | integer |  | anchor by byte offset (resolves to the containing record). On a rolled set offsets are file-local — pass 'file' too |
| `file` | string |  | rolled sets only: which member file a byteOffset is into (name or index) |
| `at` | integer |  | anchor by time (epoch millis) — resolves to the record at-or-before that moment; no need to estimate record indexes from times |
| `count` | integer |  | total records, centred on the anchor (default 5) |
| `before` | integer |  | records before the anchor (overrides count) |
| `after` | integer |  | records after the anchor (overrides count) |
| `fields` | array of string |  | project just these "instanceId.key"s (or "instanceId.\*") per record instead of raw text — 10-50x fewer tokens when you only need specific values; last occurrence per record, same as graphing. A record may also list \`tracedOnly\` (a wire trace marker said the node ran and it logged no value in this record) and, for legacy text logs, \`traceLikeOnly\` (every entry the node wrote is the tracing regime's thread/method and nothing else — inferred from spelling). Both are decided per node over ALL its contributions to the record; an empty projection is not the same as absent. Omit for the full raw record (quoting evidence) |

### `filter` arguments

MCP tool `analyser_filter`.

Narrow every view. A missing field is unchanged; null clears it.

| Argument | Type | Required | Description |
|---|---|---|---|
| `from` | integer |  | window start (epoch millis) |
| `to` | integer |  | window end (epoch millis) |
| `dimensions` | array of string |  | event dimensions to keep (OR); omit/null = all |
| `text` | string |  | free-text match (SLOW raw byte scan) |

### `graph` arguments

MCP tool `analyser_graph`.

Create/append a named time-series graph, or rename, close or DELETE one. CLEAN UP after yourself: a probe chart left behind reopens with the project and sits in the tab strip indistinguishable from the one carrying the finding.

| Argument | Type | Required | Description |
|---|---|---|---|
| `name` | string |  | target graph name (null = current tab) |
| `series` | array of string |  | raw keys, each "instanceId.key" |
| `exprs` | array of object (`label`, `expr`, `resolve`) |  | formula series over keys |
| `style` | one of `step`, `line`, `points` |  | plot style |
| `explanation` | string |  | multi-line write-up drawn ON the plot — what this chart shows and why it matters. Survives an exported PNG. |
| `notes` | array of object (`at`, `recordIndex`, `text`, `series`) |  | REPLACES the pinned set — notes pinned to moments in time, numbered on the plot and listed beneath it |
| `clearNotes` | boolean |  | drop existing pins (keeps the explanation). Only needed to clear WITHOUT supplying a new set: supplying 'notes' already replaces |
| `rightAxis` | array of string |  | series to measure against a SECOND vertical scale; use when magnitudes differ enough that one scale flattens the smaller series into the axis |
| `from` | integer |  | pin window start (epoch millis) — survives filter changes |
| `to` | integer |  | pin window end (epoch millis) |
| `newTab` | boolean |  | open a new graph tab |
| `external` | array of object (`path`, `label`, `time`, `timeFormat`, `zone`, `value`, `offsetMillis`) |  | external CSV series (M29) — REPLACES the set. The clock is DECLARED, never sniffed; reads are confined to the exchange directory (Settings ▸ Assistant) or user-chosen files |
| `markers` | array of object (`label`, `glyph`, `when`, `y`, `payload`, `resolve`, `external`) |  | marker series (M32) — discrete events drawn as glyphs: buys/sells on a price line with per-point payloads (order ids) on hover; click a marker to select its record. REPLACES the set |
| `guides` | array of object (`value`, `label`, `rightAxis`) |  | labelled horizontal threshold rules — REPLACES the set; draw the 0.004 the reader would otherwise interpolate |
| `bands` | array of object (`expr`, `label`) |  | shade the time intervals where a condition held (e.g. {expr: "ask.price - bid.price &gt; 0.004", label: "in breach"}) — REPLACES the set; intervals recompute with the data like any series |
| `rationale` | string |  | why you built this graph — captions the plot (provenance) |
| `refresh` | boolean |  | re-extract this graph from the log now. Rarely needed: with Follow on, open graphs re-extract as records arrive. A re-send that changes nothing re-extracts nothing; this is the one word for 'do it anyway'. The echo says refreshed: "scheduled" — the walk lands after the call returns, so read fresh values with \`series\`; the chart is what lags |
| `rename` | string |  | with {name}, rename that graph to this |
| `close` | boolean |  | with {name}, take that chart off the screen and KEEP its definition — for a chart worth keeping; a closed probe still sits in the profile, so delete those. Goes alone; refused for the last open chart |
| `delete` | boolean |  | with {name}, remove that chart's DEFINITION — series, formulas, pinned notes and explanation. Goes ALONE, like rename. Restorable, like a report delete: it moves to this machine's chart bin (see restore); to put a chart away without deleting it, use close instead |
| `restore` | string or one of `true` |  | use alone: true lists the chart bin — deleted charts, and the definitions a later call replaced (re-sent notes, series, guides or bands; clearNotes), each with an id; a string restores that chart by name (its newest entry) or by id (that version), refusing a name a chart has now |

### `goto` arguments

MCP tool `analyser_goto`.

Select the record containing an anchor in the table.

| Argument | Type | Required | Description |
|---|---|---|---|
| `byteOffset` | integer |  | anchor by byte offset (rolled sets: pass 'file' too) |
| `file` | string |  | rolled sets only: the member file a byteOffset is into |
| `recordIndex` | integer |  | anchor by record index |
| `at` | integer |  | anchor by time (epoch millis) — the record at-or-before that moment |
| `reveal` | boolean |  | if the record is filtered out, relax the filter to show it |

### `flag` arguments

MCP tool `analyser_flag`.

Bookmark records so your findings are reviewable in the UI. This is the ONE place a finding is written: the note and fix appear in the records table, as a callout painted on the Topology graph for that record, and in an exported report. Supplying only one of note/fix keeps the other.

| Argument | Type | Required | Description |
|---|---|---|---|
| `byteOffsets` | array of integer |  | anchors by byte offset |
| `recordIndexes` | array of integer |  | anchors by record index |
| `kind` | one of `fault`, `confirmation` |  | finding category; new findings default to fault. Omission preserves an existing category. Confirmation labels are Observation / Assessment. |
| `note` | string |  | what you observed (confirmation), or what is wrong (fault) |
| `fix` | string |  | your assessment (confirmation), or likely cause / suggested fix (fault) |

### `coverage` arguments

MCP tool `analyser_coverage`.

Which of the processor's nodes never wrote audit output in this run — coverage, for a graph. Needs a log AND a graphml. Answers the question a human cannot answer by looking at a 300-node estate: what did this run never exercise? A gap means 'never logged', not proven 'never ran' — see the note in the result.

| Argument | Type | Required | Description |
|---|---|---|---|
| `filtered` | boolean |  | score only the records the current filter shows (default false: the whole log) |
| `limit` | integer |  | how many never-logged nodes to list (default 100) |

### `report` arguments

MCP tool `analyser_report`.

Two forms. SINGLE RECORD (sugar): pass 'path' + 'recordIndex' to export one record's finding as a PDF — write the finding with 'flag' first. INVESTIGATION (M33): pass 'name' + 'sections' to build or REPLACE a named report — an ordered list of REFERENCES with connective prose, never a free-form document. A finding section renders what 'flag' wrote and this verb CANNOT set or change that text. Evidence re-renders live against the loaded log; the report stores the authoring context (log fingerprint + filter) and announces when either differs. Add 'path' to also render the PDF, or 'csv' (a table section index) + 'path' to export that table's rows. Any 'path' requires 'Allow assistant file exchange' and resolves INSIDE the exchange directory; existing files are never overwritten. CLEAN UP after yourself: 'name' + 'delete: true' removes a report, and 'name' + 'rename' renames one. A throwaway diagnostic left behind becomes a permanent fixture of a shared profile, indistinguishable from a real finding.

| Argument | Type | Required | Description |
|---|---|---|---|
| `bundle` | object (`path`, `notes`, `from`, `to`, `replay`) |  | EVIDENCE BUNDLE: pack the open investigation (the log, its graph, saved charts, reports and walks, and optional notes) as ONE verifiable .fexp. Goes alone. Refused, by name, with no log, a load pending, a log file that changed or is not one plain file. Written off the event thread: context.capture says when, its identity, and what was left out or redacted. Unsigned. It carries a replay only when 'replay' names the run's replay records |
| `name` | string |  | the report's identity — building again with the same name REPLACES it |
| `delete` | boolean |  | true removes the report named by 'name', RECOVERABLY: it moves to a recently-deleted list on this machine (restore it with 'restore'). The log, the charts it cited and any PDF already rendered are untouched |
| `restore` | string or one of `true` |  | Use alone. Boolean true lists what can be restored; a string is the deleted report's name (including the name 'true'). Restores into this profile only, and refuses a taken name |
| `rename` | string |  | the new name for the report named by 'name'. Refused if that name is taken — renaming onto an existing report would destroy it |
| `title` | string |  | the headline |
| `notes` | string |  | prose about the report — rendered visibly as narrative |
| `sections` | array of object (`kind`, `recordIndex`, `file`, `graph`, `focus`, `call`, `text`, `columns`, `rowWhen`, `rowWhenLabel`) |  | the ordered sections; invalid ones are skipped and named in warnings\[\] |
| `csv` | integer |  | export this table section's rows as CSV (needs 'name' of an existing report + 'path') |
| `path` | string |  | where to write the .pdf (or .csv with 'csv') |
| `recordIndex` | integer |  | single-record form: which record; defaults to the current selection |
| `graph` | string |  | single-record form: an open graph to include |
| `topology` | boolean |  | single-record form: include the graph picture (default true) |

### `context` arguments

MCP tool `analyser_context`.

Read-only: what the user is currently looking at — the active filter, their selection and flags, the topology cursor, the open graphs, and the source configuration. Returns POINTERS (record indexes and byte offsets), not record text; fetch what you need with 'read'. The returned 'filter' is in the exact shape 'aggregate' accepts, so you can scope a query to the user's own filter by passing it straight back. Pass 'sections' to get only part of it. 'assistant' (in the project section) is the onboard assistant's status — its mode, host, conversation and turn ids, phase and workspace — never its conversation.

| Argument | Type | Required | Description |
|---|---|---|---|
| `sections` | array of one of `log`, `project`, `pairing`, `processors`, `source`, `topology`, `view`, `charts`, `menus`, `design`, `handoff` |  | optional: only these sections; omit for the full context (the default, unchanged). Each returned key equals the same key of a full context on the same state; a warning that qualifies a selected fact (inFlight, dispatchOrder, timeOrder, producer) is carried with it, and 'scope' says what was selected and carried. Unknown or empty refuses |

### `topology` arguments

MCP tool `analyser_topology`.

Drive the Topology tab: what is shown, what is selected, and where the step cursor is. All reversible view state — nothing is loaded or written.

| Argument | Type | Required | Description |
|---|---|---|---|
| `select` | string |  | instanceId to select (null clears the selection) |
| `scope` | one of `node`, `neighbours`, `routes`, `all` |  | how far around the selection to reach; default keeps the current scope |
| `routeBound` | boolean |  | with scope 'routes': on a large graph where every route would cover more than half of it — a sink, where the routes ARE the graph — the answer stops at 3 hops each way and the echo says so in scopeBounded/scopeNote. Pass false for every route, however many. Default true, matching the toolbar checkbox |
| `focus` | any |  | boolean or string. TRUE filters the view to the selection's scope — that context becomes the whole graph and later calls operate inside it (contexts nest); FALSE exits every context. A STRING recalls a saved named focus by name (replaces the context stack) |
| `pop` | any |  | true steps out one context level; "all" returns to the full graph |
| `saveFocusAs` | string |  | save the current context as a named focus with this name (replace-by-name; project-tier, shared with saved setups) |
| `rationale` | string |  | with saveFocusAs: why this view exists — shown in the picker so the focus is a finding, not an unexplained view |
| `scaffolding` | boolean |  | show the framework nodes Fluxtion adds to every graph |
| `step` | integer |  | advance the cursor by N rows (negative steps back) |
| `recordIndex` | integer |  | move the cursor to this record in the filtered view |
| `source` | boolean |  | show the source pane beside the graph |
| `sync` | boolean |  | does the source pane follow what is selected, or stay put? |
| `orientation` | one of `top_down`, `left_right` |  | layout direction |
| `fit` | boolean |  | frame the whole graph |
| `callout` | boolean |  | show the current record's finding painted over the graph (default on). The TEXT comes from that record's flag — write it with 'flag', not here |
| `showAll` | boolean |  | exit every focus context and clear selection and cycle shading — the plain full graph |

### `screenshot` arguments

MCP tool `analyser_screenshot`.

Write a PNG of the app's own window to a path. Painted by the app, so it needs no screen-recording permission — and captures exactly the state the other verbs just set up. Requires 'Allow assistant file exchange' (Settings &gt; Assistant); the path resolves INSIDE the configured exchange directory (pass a relative name) and existing files are never overwritten.

| Argument | Type | Required | Description |
|---|---|---|---|
| `path` | string | yes | where to write the .png |
| `scope` | string |  | "window" (default), "topology", "records", or "menu:Audit log" to open a top-level menu and leave it open so a native screen capture includes the popup — "menu:close" puts it back. The painted PNG never contains a popup; a Swing menu is a separate layer. With a menu open the echo adds menuItems: \[{text, enabled, bounds}\] — where each item is, RELATIVE TO windowBounds, top to bottom. |

### `import` arguments

MCP tool `analyser_import`.

BORROW from an evidence bundle into the project you already have, instead of becoming it — open {bundle} REPLACES the session, this does not. Called with only 'bundle' it PREVIEWS: it changes nothing and returns what each category would bring. Name 'categories' to apply exactly those. A bundle's source roots and Maven repos are stripped at capture, so they are never on offer. REPORTS carries prose the sender wrote about their data and is never applied unless you name it.

| Argument | Type | Required | Description |
|---|---|---|---|
| `bundle` | string | yes | path to the .fexp to borrow from |
| `categories` | array of string |  | GRAPHS \| REPORTS \| VIEW — apply exactly these. Omit to preview and change nothing |

### `open` arguments

MCP tool `analyser_open`.

Open an audit log and/or a processor .graphml — or CLOSE what is open. Reaches the FILESYSTEM: it points the app at any readable path. Also puts the two things of the SHARED CANVAS in force — the session's posture and the authoring mode selector's record (posture / record below; read them back in context.handoff).

| Argument | Type | Required | Description |
|---|---|---|---|
| `follow` | boolean |  | Start or stop Follow on the loaded log. Use alone after opening completes; unsupported readers refuse. Echo and toolbar show actual state. |
| `restore` | one of `last`, `dismiss` |  | Accept this project's explicit last-session offer. Use alone; verifies saved inputs and reports partial refusals in context.restoration. Never runs the application. |
| `log` | string |  | path to an audit log, or an s3:// URI |
| `design` | string |  | Session XML design under an authorised source root; read-only. Relationship to a loaded run is unverified. |
| `diagnostics` | string |  | Explicit producer-result intake under authorised roots: validation, reconciliation or compiler sidecar. Replaces the previous result; refusal clears it. Works without a log. |
| `logs` | array of string |  | an explicit ROLLED SET (M30): the member files, any order — content decides the load order (each file's first timed logTime); the echo reports the order and the time-order report |
| `graphml` | string |  | path to a processor .graphml |
| `processor` | string |  | fully-qualified EventProcessor class to resolve nodes against; needed before source navigation works |
| `format` | string |  | force a specific installed reader (M31 plugins) — e.g. "yaml"; omit to let readers claim the file by content |
| `discover` | one of `graphml`, `diagnostics` |  | diagnostics lists candidate producer results under the authorised project's target/ without loading one. graphml lists the .graphml files under the configured source roots, RANKED against the open log (M35.4) — each with total/authored node counts, fingerprint, modification time and missing logged ids. copyGroups report agree/disagree/unknown for copies sharing a basename or declared processor class, even without a log; no copy is declared correct. Opens NOTHING: pick one and pass it as 'graphml'. Auto-selecting would be the convenience that reintroduces the defect M35 exists to prevent |
| `provenance` | string |  | WHERE this log came from — free text, declared alongside 'log' (e.g. "risk-engine · localhost:8081 · ~/dev/risk · exported 09:14Z"). A file name is not a system: exporting three servers' logs to /tmp gives three artefacts nobody can tell apart. It rides the status bar, context, report headers and PDFs, and lets the mismatch banner name a SYSTEM rather than a temp file. Never inferred — omit it and the analyser says nothing rather than guessing |
| `posture` | one of `research`, `authoring`, `derived` |  | SHARED CANVAS (M48.7): this session's posture — research = research/support, authoring = authoring/deploy. Set it when intent changes before any artefact does ("let's build something new"). 'derived' returns to the analyser's guess from what is open. Read back in context.handoff and shown to the person in the Project panel, attributed to you. Session-scoped: never persisted, and a project switch clears it. A canvas write goes ALONE — combined with any other open param it is refused whole, never half-applied |
| `record` | any |  | SHARED CANVAS (M48.7): the authoring mode selector's --json record, placed so you and the person read one state: {branch, modes\[\], skills\[\] (parallel to modes; null = that mode loads nothing), resolved\_figures\[\], authoring\_required\[\], selection\_candidates{}}. The analyser never runs the selector — you do (it is a headless CLI). Typed and bounded; refused WHOLE with the reason if malformed, and a posture in the same call is then not applied either. May accompany 'posture', nothing else |
| `close` | one of `log`, `graph`, `all`, `project`, `handoff` |  | close what is open (M35.1) — the counterpart of opening, and the way to switch cleanly between systems. Log-DERIVED state clears (records, filter, shading, step cursor, flags); named graphs, focuses, source roots and reports are PROFILE state and survive, each saying why it cannot resolve rather than vanishing. 'project' (M35.8) leaves the active project and restores YOUR OWN settings — the ones in force before any project was opened — which is a session boundary too: the log and graph close with it, and the echo says so. Ignored when combined with log/graphml/processor. 'handoff' takes posture AND record off the shared canvas (posture returns to derived) and goes alone, like the write it undoes; 'all' does not touch the canvas — it is about the log and the graph |
| `bundle` | string |  | path to an evidence bundle (.fexp). VERIFIES it, then opens its project, graph and log together — a bundle SUPPLIES a project, so this replaces what is open, exactly as Open evidence bundle does. The echo names its identity, the sender's note, what verification does not claim, and whether a source root remembered for it was restored. To borrow from a bundle WITHOUT replacing your project, use 'import' instead |
| `project` | string |  | path to a project's .analyser/project.fluxtion-settings, or the project directory (M35.8). APPLIES the project — it does not ask: source roots, Maven repos, event processors, named graphs, focuses, reports and hidden columns are REPLACED by the project's, and the open log and graph are CLOSED, because a project is a session boundary. The echo names everything it replaced with before/after counts, what it closed, the previously active project, and the one call that puts it back — so the mutation is reversible from the answer you were given. This is how to ACCEPT the projectOffer that context reports. Any other param in the same call is ignored and named |
| `create` | any |  | M73: CREATE a project in the absolute directory 'project' names, with discovery's offer — never a hand-written profile. create: true is the PREVIEW: it writes nothing, selects nothing, and returns createPreview — source roots, skills, graphs (informational), referenceGuide, profileExists, createProjectsSwitch and an offerDigest. create: {sourceRoots?, skills?, referenceGuide?, offerDigest?} APPLIES: adopts only what you name from the offer, writes .analyser/project.fluxtion-settings once and makes it active (a session boundary — the open log and graph close). It never overwrites an existing profile; a stale offerDigest is refused. An assistant's create needs the person's Settings ▸ Assistant ▸ Allow the assistant to create projects. Goes with 'project' alone |
| `analysis` | string |  | M38.4: recall a SAVED ANALYSIS by name — the ones context.analyses lists, each with its rationale and the parameters it declares. Its steps are analyser verbs and run through this same surface in order, stopping at the first failure; the echo reports each step. An offer made real by this call, never automatic. |
| `bind` | object |  | values for the analysis's declared parameters, e.g. {"log": "/path/a.yaml"}; a parameter with no value and no default refuses the run and names itself |

### `source` arguments

MCP tool `analyser_source`.

Read-only source glance; rereads XML or Java under authorised source roots. Exclusive selectors: {file}, {file,line}, {line}, {bean}, {file,bean}, {fqn}, {fqn,method}. A glance never replaces open.design. Bean matches aid navigation, not evidence; relationship to the loaded run remains unverified.

| Argument | Type | Required | Description |
|---|---|---|---|
| `file` | string |  | Project-relative or absolute path under an authorised source root |
| `bean` | string |  | Bean id in the session design, or the specified XML file; duplicates are refused |
| `line` | integer |  | One-based line in file or session design |
| `fqn` | string |  | Java class under authorised source roots |
| `method` | string |  | Method name, only with fqn |

### `source_root` arguments

MCP tool `analyser_source_root`.

Inspect or change the configured source roots. Reaches the FILESYSTEM: a root grants Java, XML design and producer-result reading beneath it.

| Argument | Type | Required | Description |
|---|---|---|---|
| `add` | array of string |  | roots to add |
| `remove` | array of string |  | roots to remove |

### `spotlight` arguments

MCP tool `analyser_spotlight`.

POINT at what you are talking about, for the person you are working with: the window dims, each target is cut out, and a short callout with an arrow says why. Use it BEFORE you talk about something on screen — a finding, an anomaly, the step of an explanation — then take a screenshot to check it is lit where you meant. A target is revealed first (its tab selected, its row scrolled to, its node centred). ONE thing: {target, caption}. A RELATION between things — this node, that record, the crossing on the chart — is up to 6 lit together: {targets: \[{target, caption}, …\]}. Several are NUMBERED on screen and in the echo (n): say the number in your sentence. A set is all-or-nothing, and things that cannot be on screen together (different tabs) are refused — light them one after the other. A call REPLACES what is lit unless {add: true}. Transient: never saved, and it all goes out on any click, Escape, {clear: true}, or any verb that changes the view (open, filter, goto, graph, topology) — so light AFTER the view is how you want it. The callout shows WHERE; your chat says WHAT. Durable findings are flags, notes and reports. Java: source:java:&lt;fqn&gt;\[:line:&lt;n&gt;\] uses the viewer's first matching root, then enabled local sources jars; source {fqn} instead refuses duplicate roots and never reads jars. Check the echoed origin before using a line number. A repeated Java spotlight rereads source; preparation expires after ten seconds without lighting late. One Java document per set; combine with topology targets to open source beside the graph. Java line partial means a clipped logical line; source/run relationship is unverified. Menus: context.menus lists every menu and its items, exactly as menu:&lt;Menu&gt;:&lt;item&gt; names them, and context.menuChanges says what was renamed or retired; a miss says where the item is if it moved or was renamed. To show where a command is, light its ITEM, menu:&lt;Menu&gt;:&lt;item&gt; — not the menu alone, the status bar, or a toolbar button that is not that command. Targets: tab:&lt;summary\|source\|graph\|topology\|reports\|assistant&gt; · source:design · source:design:bean:&lt;id&gt; · source:design:line:&lt;n&gt; · source:java:&lt;fqn&gt; · source:java:&lt;fqn&gt;:line:&lt;n&gt; · records · records:row:&lt;recordIndex&gt; · detail · detail:node:&lt;instanceId&gt; · topology · topology:node:&lt;instanceId&gt; · topology:verdict · graph\[:&lt;name&gt;\] · graph\[:&lt;name&gt;\]:note:&lt;n&gt; · graph\[:&lt;name&gt;\]:series:&lt;label&gt; · project · project:&lt;log\|graph\|processors\|roots&gt; · toolbar:&lt;open\|flag\|explain\|follow&gt; · menu:&lt;Menu&gt; · menu:&lt;Menu&gt;:&lt;item&gt; · status

| Argument | Type | Required | Description |
|---|---|---|---|
| `target` | string |  | one of the targets above, e.g. tab:topology, records:row:12, topology:node:priceListener, graph:note:2, menu:Audit log:Follow (tail). With {clear: true} it names the ONE spotlight to put out |
| `caption` | string |  | ONE short line (at most 160 characters) saying why to look here. It is shown as YOUR words (testimony), not as a fact the analyser established. |
| `targets` | array of object (`target`, `caption`) |  | several at once, instead of target/caption: up to 6 entries, each {target, caption?} |
| `add` | boolean |  | true keeps what is already lit and adds to it (default: replace). A standing spotlight the new reveal takes off screen goes out, and the echo's wentOut names it |
| `clear` | boolean |  | true puts every spotlight out — or, with 'target', just that one |

### `walk` arguments

MCP tool `analyser_walk`.

A SPOTLIGHT WALK: a saved, named sequence of spotlight steps the person steps through with ◀ ▶ on the analyser's own overlay — an explanation that outlives this chat. Stored like a report (in the project, shared by Settings export), attributed to the assistant. ONE operation per call, and fields of another operation are refused before anything changes. SAVE (create, or replace by name): {name, title?, steps: \[{caption?, view?, targets: \[{target, caption?}\]}\]} — up to 50 steps, at most 6 targets each, spotlight targets except source, toolbar and menu ones. A view may set tab, filter (complete: from, to, groupMode, dimensions, text), record, graph (an OPEN chart to select — a walk never opens or edits one) and focus (a saved topology focus's name); anything else is refused, naming it. The analyser binds every record, chart and graph target to what is loaded NOW, so a step pointing at records or charts needs the log open; played against another run it is marked historical or not shown, never re-pointed. PLAY: {name, play: true, step?} (step counted from 1). END: {end: true}. CLEAN UP: {name, delete: true} (recoverable), {name, rename}, {restore: true} lists the bin, {restore: "name"} restores. DIALOGUE (a conversation journey): save 'conversation' {version: 1, kind: scripted\|recorded\|edited-recording, author?, turns: \[{id, role: user\|assistant, text}\]} with the steps, giving steps 'id' and 'conversationThrough' (the last turn visible there), or attach one to a saved walk: {name, conversation, through: \[turn id or null per step\]}; conversation: null removes it. Dialogue is labelled and shown beside the real view, never run. REEL (send it to somebody who does not have the analyser): {name, reel: "&lt;file&gt;.html"} plays the walk, photographs every settled step and writes ONE self-contained page — a title page naming the log, its record count, its time span and the event processor; a frame per step with its captions; and a finish page naming the EVIDENCE. If this session came from an evidence bundle the finish page carries its sha256 identity, its file name and the limits the bundle states; if it did not, the page says so plainly, because a reel with nothing behind it is a claim. context.walks lists the walks and the showing step's target states.

| Argument | Type | Required | Description |
|---|---|---|---|
| `name` | string |  | the walk's identity — saving again with the same name REPLACES it |
| `title` | string |  | the headline shown on the strip |
| `steps` | array of object (`caption`, `id`, `conversationThrough`, `view`, `targets`) |  | the ordered steps (save) |
| `conversation` | object (`version`, `kind`, `author`, `turns`) |  | OA-3: the walk's dialogue (with steps, or alone to attach, replace, or with null remove it) |
| `through` | array of string |  | with conversation alone: per step, the id of the last turn visible there, or null |
| `delete` | boolean |  | true moves the walk named by 'name' to this machine's recently-deleted list |
| `rename` | string |  | the new name for the walk named by 'name'; refused if taken |
| `restore` | string or one of `true` |  | use alone: true lists what can be restored; a string restores that walk, refusing a taken name |
| `play` | boolean |  | true presents the walk named by 'name' to the person |
| `step` | integer |  | with play: the step to start from, counted from 1 (default 1) |
| `reel` | string |  | record the walk named by 'name' as one self-contained HTML page at this path (inside the exchange directory, never overwriting); the page ends by naming the evidence bundle behind it, or by saying there is none |
| `end` | boolean |  | true ends the showing walk |

### `propose` arguments

*Offered only to Claude Code in the analyser: never on `/manifest`, the MCP bridge or the in-app assistant.*

Hosted Claude Code only. Offer the person a project set-up. Records an offer and changes NOTHING: the analyser checks each item, the person answers each one, and the analyser applies the accepted ones itself. Call context afterwards to see each item's outcome. Paths are relative to your working directory or absolute inside it.

| Argument | Type | Required | Description |
|---|---|---|---|
| `summary` | string |  | one sentence: what this set-up is for |
| `items` | array of object (`kind`, `path`, `className`, `reason`) | yes | the set-up items, 1 to 10 |

<!-- END GENERATED -->
