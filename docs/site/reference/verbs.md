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
  used to: a probe built to check an expression resolves is still in the profile a week later. Not
  recoverable.
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
    empty list or a non-list is refused before anything is read. The Project panel is drawn only from a
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
replaces the loaded log (taking the session's flags with it), `source_root` writes the persisted config, `screenshot`
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
