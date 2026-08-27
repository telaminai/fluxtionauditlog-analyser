# Analyser assistant

The assistant assembles the selected record(s), the node-type map and the relevant source, then asks a
model to explain what happened in the cycle and why.

Its real power is the **round trip**. The assistant doesn't just answer in a chat window — it can
**drive the analyser** and file its findings back as things you can *see and interrogate*: a plotted
graph, a filtered table, jumped-to and flagged records, all over the **same data** you can click into
and trace to source. The conclusion arrives as a chart, not a wall of text you have to trust — so the
**verification loop is built in**. When the assistant says "the quote calculator stopped because the
venue disconnected here", it can plot that series and flag those cycles, and you watch it on screen.

## Fault-finding in practice

1. **Scope it.** Drag the **Time range** window to the incident, then select the suspicious records in
   the table (Shift/⌘-click for several).
2. **Ask.** Right-click ▸ *Explain selected with LLM*, or use *Explain with LLM* in the record detail.
3. **Watch it work.** The assistant reads the surrounding records, aggregates over the index to spot the
   pattern, and when it finds the cause it **plots the offending series into a graph** (captioned with
   its reasoning), **flags** the culprit records, and **filters** the table down to them — the fault
   rendered in your own instrument, not described in prose.
4. **Verify.** Click the plotted points, jump to the flagged records, trace a nodeLog line to the exact
   source method. Nothing is taken on faith; the evidence is right there to challenge.

## No API key? Copy-prompt mode

No key, or working in a different agent (Claude Code, Claude Desktop)? Hit **Copy prompt**. The copied
prompt is a complete, self-contained brief: the selected records, the node-type map, the relevant
source, **and** the log's file path, shape and per-record **byte offsets** — plus the analyser's
**action protocol** (the localhost REST endpoint, token and verbs). So an external agent can not only
reason about the evidence, it can **drive this analyser back** — seek more records, aggregate, and plot
a graph to show you the fault — exactly as the in-app assistant does.

## Setup

Open **Settings ▸ Assistant / LLM**:

- **Provider / model** — Anthropic (Claude) or OpenAI, and a model id.
- **API key** — stored locally (cleartext, single-user tool). **Never leaves your machine** and is
  never included in a shared settings file.

## Actions the assistant can take

From a reply the assistant runs bounded **actions** (within the round / per-reply caps in Settings) and
feeds the results back. An optional localhost **REST transport** (off by default) lets an external agent
drive the same verbs:

- **aggregate** — counts / rates over the index (the expensive parse is done once and shared).
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
- **goto** — select a record (by index, byte offset or `at` time); `reveal:true` un-hides one the current filter is hiding.
- **flag** — bookmark the culprit records with a `note` and an optional `fix`. This is the **one** place
  a finding is written; it then shows in the records table, as a callout on the Topology graph for that
  record, and in an exported report. Supplying only one of `note`/`fix` keeps the other, so adding a
  suggested fix can't wipe the explanation it's a fix for.
- **screenshot** — write a PNG of the app, or of one panel. `scope: "menu:File"` opens a top-level menu
  and leaves it open, so an assistant can *show you where a control is* rather than describe it
  (`menu:close` puts it back). The window is raised first: a native capture photographs a region of the
  screen, so anything sitting on top would otherwise be in the picture.
- **report** — two forms. The single-record form writes one record's finding out as a PDF: the
  explanation, the suggested fix, the event, the node log, a picture of the topology as currently
  focused, and optionally a plot. The **investigation form** (`report {name, sections}`) builds a
  named, persistent report from typed sections — findings, records, charts, tables, narrative — the
  *account* of an investigation, not just its evidence. It appears in the **Reports** tab, renders to
  PDF with `path`, and exports a table's rows to CSV with `csv`. A finding section renders what
  `flag` wrote and the verb **cannot** set or change that text; narrative is always visibly labelled
  as narrative. See [Investigation reports](reports.md).
- **coverage** — which of the processor's nodes never wrote audit output in this run. Needs a log *and* a
  graphml, and answers the question nobody can answer by eye on a large graph: what did this run never
  exercise? A gap means "never logged", not proven "never ran" — a node with no `auditLog` call, or one
  whose dirty contract stops it early, is silent by design, and the result says so.

`GET /manifest` publishes a JSON schema for every verb, so a foreign agent learns the shapes up front
instead of trial-and-erroring against the structured errors.

## Connect an MCP client

!!! tip "Step-by-step, with a working check"
    [Connecting an LLM to the analyser](../connect-an-llm.md) walks the connection through in order and
    shows how to confirm each link works before relying on it. This section is the reference.

If your agent speaks **MCP** (Model Context Protocol), it can drive the analyser with **no prompting and
no copied token**. The same jar doubles as an MCP server — your client runs it as
`java -jar fluxtion-auditlog-analyser.jar --mcp` (you don't type that yourself; it goes in the client's
config, below).

The client discovers one tool per verb — `analyser_aggregate`, `analyser_read`, `analyser_series`,
`analyser_filter`, `analyser_graph`, `analyser_goto`, `analyser_flag`, `analyser_coverage`,
`analyser_topology`, `analyser_report`, `analyser_context`, `analyser_screenshot`, `analyser_open` and
`analyser_source_root` — with full parameter schemas, so there's nothing to paste into a prompt.

`open {analysis: name, bind: {…}}` recalls a saved analysis (*Portable context ▸ Repeatable analyses*) —
`context.analyses` lists them with their parameters; steps run through this surface and stop at the first failure.

`open` also takes `logs: [...]` — an explicit rolled set, loaded as one log in content order, the
echo carrying the order chosen and the time-order report (see *Records ▸ Rolled log sets*).

Three more on `open`, so an agent can manage what is loaded rather than only add to it:

- `open {close: "log" | "graph" | "all"}` — the counterpart of opening, and the way to switch cleanly
  between systems. Log-derived state clears; your named graphs, focuses, source roots and reports are
  profile state and survive, each saying why it cannot resolve rather than vanishing. Combining it
  with `log`/`graphml` closes and **names what it ignored**, rather than leaving you to guess which
  half of an incoherent request was honoured.
- `open {discover: "graphml"}` — lists every `.graphml` under the source roots, ranked against the
  open log, with each one's node count and how many of the log's nodes it declares. **It opens
  nothing**: pick one and pass it as `graphml`.
- `open {graphml: …}` now answers *does this fit?* in the same call — `appliesToOpenLog`, the counts,
  and the verdict — so switching processor needs no follow-up `context`.

- `open {project: "<project dir, or its .analyser/project.fluxtion-settings>"}` — switch to a
  project, the same act as **File ▸ Open project…**, and the way to *accept* the `projectOffer` that
  `context` reports. It **applies rather than asks** — a dialog cannot be answered over the socket —
  so its echo carries the safety: every category the switch replaced with before/after counts, what
  it closed and where those files were (the log and graph — a project is a session boundary), which
  project was active before, and the one call that puts it back. `open {close: "project"}` is that
  call when the answer was *your own settings*. Re-opening the project already in force changes
  nothing and says so. Your MCP client's per-call approval on `open` is the human gate.
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

When the project points at a glossary (*Portable context ▸ Vocabulary*), its text leads every *Explain*
prompt and is served as `context.vocabulary.text`, so the assistant reads `live` the way this system means it.

`context` is also what the **Project panel** draws (*User guide ▸ The Project panel*): one payload, two
readers. It reports the graph whether or not a log is open, `log.openedBy` (you or the action socket),
`graphPairing.graphPath`, `processors` as a list with `selected` and whether `source` was `found`, and
`source.rootTiers` — each root with the tier that supplied it (`project`, `own settings`, `demo (transient)`) and
the `form` it is stored in (`project-relative`, `workspace-relative`, `~`, `absolute`), plus `source.workspaceRoot`
when the project declares an anchor.

Five keys the closing M38 review found described in prose but not by name — an agent looks for the handle:
`dispatchOrder` (whether position in `nodeLogs` is dispatch order — *total* — or merely arrival — *PARTIAL*;
never read PARTIAL as causality), `timeOrder` (present only when the log's timestamps are out of order —
the report summary, so disorder is announced rather than discovered through wrong answers), `graphPairing
.sourceGraphDeclined` (a reader offered a graph but an OPENED one holds the slot — opened beats supplied),
`reports[].createdAt`, and `source.workspaceDir` (where the declared `workspaceRoot` resolves on this machine —
two facts, not one).

The project's portable context (*User guide ▸ Portable context*; the AI-side manual is *Working with AI ▸ Runbooks,
glossary and saved analyses with an AI*) rides the same payload: `runbooks[]` (pointers —
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

`aggregate`, `read`, `series`, `context` and `coverage` are marked read-only. The verbs that only change what the
app shows are reversible and marked accordingly. Four are marked **destructive**, so a client can prompt
before running them: `open` replaces the loaded log (taking the session's flags with it), `source_root`
writes the persisted config, and `screenshot` and `report` write files.

Those last two are **off by default**. Turning on *Allow assistant file exchange* (Settings ▸ Assistant) lets them
write **only inside the exchange directory you choose**, and they never overwrite an existing file — so a
second export under the same name is refused rather than silently replacing the first. Exports you drive
yourself, through a File menu chooser, are unaffected: picking a location in a dialog *is* the
authorisation.

### Does my client launch the analyser?

**No — you start the analyser yourself, and leave it open.** Your MCP client launches a small *bridge*
process (that's the `--mcp` command in the config below); the bridge then talks to the analyser you
already have running.

It works this way because the point is to drive **your live session** — the log you have loaded, the
graphs you have open, the flags you have set. All of that lives in the running desktop app. A freshly
spawned subprocess would have none of it, so the bridge forwards to the app instead of trying to be one.

So the working setup is:

1. **You** open the analyser and load a log, as usual.
2. **You** enable Settings ▸ Assistant ▸ **localhost REST transport** (off by default) — once; it's
   remembered.
3. **Your client** starts the bridge on its own, whenever it needs a tool.

If you skip step 1 or 2, the tools still appear in your client, but calling one answers *"analyser not
running, or REST transport disabled — start the app with `--rest`"*. That flag turns the transport on
(persistently) and prints where the endpoint file is, so an agent can bring the analyser up on a fresh
machine without a human at the keyboard.

### Configure your client

Use an **absolute path** to the jar in all three. Desktop apps don't inherit your shell's `PATH`, so if
`java` isn't found, use its full path too (`which java` to get it).

=== "Claude Code"

    Add it to `.mcp.json` in your project root:

    ```json
    {
      "mcpServers": {
        "fluxtion-analyser": {
          "command": "java",
          "args": ["-jar", "/absolute/path/to/fluxtion-auditlog-analyser.jar", "--mcp"]
        }
      }
    }
    ```

    Or from the CLI — everything after `--` is the launch command:

    ```bash
    claude mcp add fluxtion-analyser \
      -- java -jar /absolute/path/to/fluxtion-auditlog-analyser.jar --mcp
    ```

    Add `--scope user` to make it available in every project instead of just this one. A project-scoped
    `.mcp.json` is committable, so a team shares one setup.

=== "Claude Desktop"

    Settings ▸ Developer ▸ **Edit Config**, which opens
    `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or
    `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

    ```json
    {
      "mcpServers": {
        "fluxtion-analyser": {
          "command": "java",
          "args": ["-jar", "/absolute/path/to/fluxtion-auditlog-analyser.jar", "--mcp"]
        }
      }
    }
    ```

    Quit and restart Claude Desktop completely — it only reads this file at startup.

=== "Codex"

    In `~/.codex/config.toml` (or a project's `.codex/config.toml`):

    ```toml
    [mcp_servers.fluxtion-analyser]
    command = "java"
    args = ["-jar", "/absolute/path/to/fluxtion-auditlog-analyser.jar", "--mcp"]
    ```

    Or `codex mcp add fluxtion-analyser -- java -jar /absolute/path/to/analyser.jar --mcp`.

    Codex caps each tool call at 60s by default; raise `tool_timeout_sec` if you aggregate over a very
    large log.

### Your first session

Start your CLI client anywhere — the analyser tools aren't tied to a project directory. First check the
server actually connected, then say what you want:

```text
$ claude

> /mcp
  ⎿ fluxtion-analyser   ✔ connected · 14 tools
       analyser_aggregate · analyser_read · analyser_series
       analyser_filter · analyser_graph · analyser_goto
       analyser_flag · analyser_report · analyser_coverage
       analyser_context · analyser_topology · analyser_screenshot
       analyser_open · analyser_source_root

> I have a Fluxtion audit log open in the analyser. Use the fluxtion-analyser
  tools to work out why the hedge stopped quoting.
```

That first message is the whole handshake on your side. You mention the server by name once so the agent
reaches for these tools rather than, say, grepping your filesystem; after that it just uses them. There's
no path to give it and no token to paste — it's already pointed at whatever log you have open.

If `/mcp` shows nothing, the client never started the bridge: re-check the config paths in the tab above.
If it shows *connected* but a call comes back **"analyser not running"**, the bridge is fine and the
**app** is the missing half — open it and turn on the REST transport.

From there the agent works, and you watch it work:

```text
⏺ analyser_aggregate(metric: "count", groupBy: "dimension")
  ⎿ total 21 records
     orderUpdate 10 · ScheduledTriggerNode 3 · orderVenueConnected 2
     onMultilevelMarketData 2 · registerService 2 · LifecycleEvent 1
     marketDataVenueConnected 1

⏺ analyser_read(recordIndex: 1, count: 3)
  ⎿ hedgeConnectionMonitor: { orderVenueConnected: …, status: CLOSED, hedgeQuantity: NaN }
     hedgePositionMonitor:  { hedgePositionBreach: false, hedgeStatus: CLOSED }

⏺ analyser_graph(name: "hedge quantity",
                series: ["hedgeConnectionMonitor.hedgeQuantity"],
                rationale: "hedgeQuantity goes NaN when the venue drops")
  ⎿ {"ok":true,"applied":{"name":"hedge quantity",
      "resolved":["hedgeConnectionMonitor.hedgeQuantity"],"unresolved":[]}}

⏺ analyser_flag(recordIndexes: [1, 2], note: "hedge went NaN here")
  ⎿ {"ok":true,"applied":{"flagged":2}}

  The hedge monitor reports status CLOSED with hedgeQuantity NaN from the venue-connect
  event onward, so the quote calculator had nothing to size against. I've plotted
  hedgeQuantity as "hedge quantity" and flagged the two records where it flips.
```

**Watch the analyser while this runs.** A *hedge quantity* tab appears, captioned with the agent's
`rationale`; two rows pick up flags. The findings land **in your instrument**, as things you can click,
zoom and trace to source — not as prose you have to take on trust. That's the whole point of the round
trip: you verify the claim against the same data it was made from.

Note what the agent *didn't* need: no file path, no token, no pasted records. It read the log through
`analyser_read` over the socket.

## Hand the diagnosis on

A plot answers *"this trend is wrong"*. To answer *"**this cycle** is wrong, and here's why"*, compose
the verbs you already have with `report`:

```text
⏺ analyser_flag(recordIndexes: [40],
               note: "quotePublisher republished before riskMonitor re-evaluated the limit,
                      so the quote on the wire was priced against stale risk state.",
               fix: "riskMonitor must be upstream of quotePublisher — check the @OnTrigger ordering")

⏺ analyser_goto(recordIndex: 40, reveal: true)

⏺ analyser_topology(select: "quotePublisher", scope: "routes", focus: true)
  ⎿ {"ok":true,"topology":{"visibleNodes":7,"totalNodes":20,"callout":true,
      "finding":{"note":"quotePublisher republished before …","fix":"riskMonitor must be …"}}}

⏺ analyser_report(path: "finding-40.pdf", title: "Stale risk state at record 40",
                 graph: "Mid price")
  ⎿ {"ok":true,"wrote":{"path":"…/finding-40.pdf","recordIndex":40,
      "hasExplanation":true,"hasFix":true,"topology":true,"graph":"Mid price"}}
```

`topology` echoes back the finding it is showing, so an agent can confirm the graph is displaying its
diagnosis rather than assume it. The PDF is the artefact you send to someone who wasn't watching: the
explanation next to the event, the node log, the graph and the plot it rests on.

The `topology` verb's `callout` field is a **visibility** switch and nothing more — the text always comes
from the record's flag. One place to write, several to read.

**`scope: "routes"` on a sink comes back bounded, and says so.** Every route into a node that everything
feeds *is* the graph, so on a large graph where all routes would cover more than half of it the answer
stops at three hops each way. The echo is explicit rather than quietly returning less than the scope's
name promises:

```json
{"scope": "routes", "routeBound": true, "scopeBounded": 3,
 "scopeNote": "'routes' was bounded to 3 hops because all routes would cover 198 of 309 nodes …"}
```

Pass `routeBound: false` for every route, however many — the same switch as the **≤3 hops** checkbox in
the toolbar, so the screen and the socket never disagree about what is being shown.

### Let your agent set it up

Every step above is an ordinary shell command, so you can hand the whole setup to the agent you're
already talking to — *"install the Fluxtion analyser with jbang and register it as an MCP server"* — and
approve the commands as they come. The recipe, if you'd rather run it yourself:

```bash
jbang app install analyser@telaminai/fluxtionauditlog-analyser    # 1. install → ~/.jbang/bin/analyser
printf 'assistant.rest=true\n' >> ~/.fluxtion-analyser/config     # 2. enable REST (analyser CLOSED)
claude mcp add fluxtion-analyser -- ~/.jbang/bin/analyser --mcp   # 3. register with your client
~/.jbang/bin/analyser my-log.yaml                                 # 4. open the app on a log
```

Order matters, and step 2 in particular has a trap:

- **Edit the config only while the analyser is closed.** The running app holds settings in memory and
  writes the file on exit, so an edit made while it's open is overwritten. Toggling it in
  Settings ▸ Assistant is always safe.
- **jbang caches jars.** If `--mcp` isn't recognised you're on an older cached build — `jbang cache clear`,
  or run once with `--fresh`.
- **Launching needs a desktop session**; the app is a GUI, so step 4 won't work over a headless SSH shell.

!!! note "Setup is shell work, not an MCP tool"

    Installing, configuring and launching are deliberately **not** exposed as MCP tools. Partly because
    it would be circular — a tool that installs the bridge needs the bridge already installed — and
    partly because the analyser's tool surface is kept to the log verbs on purpose. Shell commands
    are yours to approve; tools are the model's to invoke, and that difference is the whole security
    story below.

### How it finds your running analyser

REST picks a fresh port and mints a fresh token every launch, so there is nothing stable to hard-code.
Instead, while the transport is running the app writes its live endpoint to
`~/.fluxtion-analyser/rest-endpoint` (owner-readable only), and the bridge reads it **on every call**.

Two things follow, both useful:

- **Configure once.** No token in your client config, and nothing to update after a restart — the bridge
  picks up the new port and token by itself.
- **Connecting doesn't need the app.** Your client can start and list the tools with the analyser closed;
  only the *calls* need it running.

### If it isn't working

- **"analyser not running, or REST transport disabled"** — exactly what it says: either the app isn't
  open, or the REST transport is off in Settings ▸ Assistant. The same message appears if the app was
  killed, because the bridge checks the recorded process is still alive before trying to connect.
- **The server won't start at all** — usually the jar path or `java` not being found. Try the exact
  command from your config in a terminal; it should sit and wait for input rather than exit.
- **Claude Desktop**: per-server logs are at `~/Library/Logs/Claude/mcp-server-fluxtion-analyser.log`
  (macOS). The bridge writes all diagnostics to stderr, so they land there.
- **Rate limiting** — a burst of calls gets a "rate limited" tool error rather than a broken connection.
  It's retryable; the agent should pace itself.

### What it can and can't do

The MCP door opens the **same verbs** as the other transports and nothing more — one tool per verb,
discovered live, so the list you see in your client is the truth. An agent can read the loaded log,
change what the app displays, and (via `open` / `source_root`) switch which log, processor or source
roots are open — the same things you change through the UI. **File writes are off by default**: the
`screenshot` / `report` verbs work only after you enable *Allow assistant file exchange* (Settings ▸ Assistant),
write **only inside the exchange directory you configure**, and never overwrite. It **cannot** touch your
API key, run anything, or read files outside the log and sources you configured. Server control is
deliberately not an assistant capability. The channel is loopback-only and the endpoint file is
owner-readable. (The [FAQ's security answer](../faq.md#is-the-assistants-action-socket-safe-to-enable)
is the canonical statement of this boundary.)
