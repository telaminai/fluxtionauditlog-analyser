# Fluxtion Audit Log Analyser — Design Spec

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-12

A pure‑Swing desktop tool for reading, filtering, graphing and (LLM‑)explaining
**Fluxtion event‑audit logs**. Logs are machine‑readable YAML‑ish records emitted by a
Mongoose/Fluxtion `EventProcessor`; this tool turns them into a browsable, queryable,
explainable event history.

> Companion docs: **[tracker.md](tracker.md)** — phased work items and status ·
> **[spec-assistant-actions.md](completed/spec-assistant-actions.md)** — the two‑way LLM curation loop (M10) ·
> **[spec-settings-share.md](completed/spec-settings-share.md)** — settings export/import (M15) ·
> **[../admin/release-process.md](../admin/release-process.md)** — branching, GitHub Actions releases,
> fatjar + JBang distribution (M16) · **[../admin/docs-site.md](../admin/docs-site.md)** — the GitHub
> Pages user site (M17).

---


---

## Where this tool sits in the authoring architecture

_Canonical statement: **[spec-authoring-modes.md](spec-authoring-modes.md) ▸ THE TARGET
ARCHITECTURE**. This section answers the four questions a reader should not have to reconstruct._

### 0 · Why the authoring and runtime results belong together

They are usually read as two research programmes. They are one direction, and each link is measured
or explicitly marked as not:

| | |
|---|---|
| metadata and a resolver | remove mechanical authoring work |
| generated dispatch | removes handwritten ordering as independent mutable state |
| the runtime | makes the resulting structure cheap enough to keep — **8.44 ns/event, no measured per-event allocation** |
| the audit path | produces evidence, at an explicitly measured cost |
| this tool | makes that evidence queryable at a scale no reader can hold |
| human or model judgement | reserved for requirements and policy |

**The combined proposition:** *mechanically resolve what can be decided, compile correctness-bearing
structure into predictable allocation-free dispatch, and preserve enough identity and runtime evidence
to inspect what actually happened.*

The runtime measurements live in
[`round-54/BLOG-NUMBERS.md`](../experience/runs/round-54/BLOG-NUMBERS.md) and are deliberately **not**
merged into the authoring argument — they are a separate benchmark with their own method and their own
limits. The headline defensible claim there: **with a stream-driven clock a ten-node graph dispatched
at 8.44 ns/event with no measured per-event allocation — 19% slower than hand-written Java implementing
the same dirty-guard semantics.** The 2.92 ns inline loop is a useful physical floor, but it does not
purchase the same semantics.

### 1 · Which decisions are mechanical?

**Selection and wiring, wherever declared metadata and policy determine a unique answer.** Measured on
one fixture family: a resolver reproduced the components, the wiring and byte-identical configuration
from jar manifests alone, and the resulting application's audit log matched figure by figure. Where
the metadata does *not* decide — genuine ambiguity, a missing convention, a cycle, an unsatisfied
requirement — **the resolver fails closed and says which**, rather than guessing.

### 2 · Which component owns each fact?

| fact | authority |
|---|---|
| what a component provides, requires, consumes; eligible constructors | **compiler / build tooling** (proposed; hand-authored today) |
| descriptions, business conventions, constructor intent | **author or vendor** — the semantic residue compilation cannot infer |
| which components, wired how | **the resolver**, where policy decides; otherwise escalated |
| dependency identity, relationship kind, propagation, ordering, provenance | **Fluxtion** — authoritative model facts, substantially consumed here |
| what a run RECORDED | **the audit log** — evidence, not a complete account of what happened: an arrested path logs nothing, and a suppressing level records nothing at all |
| what the evidence establishes, and what it does not | **this tool** |

**The rule:** decide each fact once at the component with authority, serialise it, and have every
downstream surface consume that same typed result. Where two components decide the same fact
independently, they will eventually disagree — this repo has three instances on record (GraphML
relationship re-derivation, topological-rank inference, service-registry reclassification).

### 3 · Where is human or model judgement genuinely required?

Two places, and **only** two:

- **Turning an informal goal into explicit figures, policies and open choices.** *This is the largest
  evidence gap in the programme.* Every experiment to date was handed the figure list; this step has
  never been measured, and is a hypothesis rather than an established role.
- **Settling ambiguity the metadata cannot** — which of several type-identical components matches the
  business intent. Once settled, the answer is **recorded as reviewable policy** so no later build
  repeats the inference.

Everything between those two points is mechanical or should become so.

### 4 · What evidence can establish that the application behaves as intended?

**This tool is an evidence renderer and query surface, not a general correctness verifier**, and the
distinction is load-bearing rather than modest.

It reduces logs and graph metadata into bounded evidence — aggregates, coverage, topology, series —
and answers questions about them at a scale no reader or model can hold: at 460 bytes per event, a
ten-thousand-event run exceeds a 200k-token context roughly six times.

**Verification happens only where an explicit expectation, invariant or oracle exists.** With one, the
tool can state a verdict and defend it: `ExpectationScorer` compares two logs on published figures and
**refuses to score** a comparison it cannot trust — mismatched lengths, differing event sequences,
differing event identity, an empty or figure-less expectation, a figure outside the contract, a
non-finite value. Ten guards, five of them added by independent reviewers who found the earlier
version producing false passes.

**Without an expectation there is no verdict, and the tool says so rather than implying one.** "The
graph looks reasonable" is not a claim it makes.


## 1. Purpose & scope

- Load a Fluxtion audit log file (best‑effort‑parse; the format *aims* at YAML but is not
  strictly valid YAML — see §4).
- Render one **row per `eventLogRecord`** in a table; inspect the full `nodeLogs` for a row
  with colourised YAML.
- Filter by **log‑time range** (draggable range picker) and by **event type / callback**.
- Summarise the log grouped by event type and time range.
- Graph any numeric `instanceId.key` from `nodeLogs` over time.
- Explain selected record(s) via an LLM (Claude or OpenAI), with a two‑way conversation; or,
  with no API key, emit a ready‑to‑paste prompt.
- Open the **source** for the node behind a record (read‑only, colourised), resolved through
  the selected EventProcessor.
- Handle files **up to ~500 MB in heap**; beyond that, switch to a memory‑mapped + indexed
  mode (§7).

**Non‑goals:** editing logs; live agent control; a web/native build; database persistence;
authenticated multi‑user features.

> _"Live agent control" stands._ M18 (spec-closed-loop §B) adds **human-driven** server control —
> localhost-only, confirm-per-action, and **fenced from agents** (§B.5: server verbs are never
> assistant actions). The non-goal here is agents controlling live systems, and that remains excluded.

---

## 2. Background — the Fluxtion audit log format

Each record is separated by a YAML `---` document separator. A record has a leading `#`
comment header (wall‑clock time, thread, level, **logger = the processor's audit name**),
then an `eventLogRecord:` block.

```yaml
---
#10:57:37.431 [marketMaker-DEMO] INFO  MAKER_USDMXN_DEMO
eventLogRecord:
  eventTime: -1                     # epoch millis; -1 = not event‑driven (timer/export call)
  logTime: 1786355857430            # epoch millis — the primary timeline
  groupingId: null
  event: ExportFunctionAuditEvent   # event class OR trigger type (see §6)
  eventToString: public boolean com.acme....VenueHedgeMonitorCalculator.orderVenueConnected(com.fluxtion.server.plugin.trading.service.order.OrderVenueConnectedEvent)
  thread: marketMaker-DEMO
  nodeLogs:                         # ordered list; one entry per node that logged this cycle
    - hedgeConnectionMonitor: { orderVenueConnected: OrderVenueConnectedEvent[name=demoRfqOrders], status: CLOSED, hedgeQuantity: NaN}
    - hedgePositionMonitor: { hedgePositionBreach: false, hedgeStatus: CLOSED}
  endTime: 1786355857431
```

**Semantics that the tool relies on:**

- A record is **one graph propagation cycle**: an event (or trigger) entered the processor and
  each node that ran appended a `instanceId: { key: value, … }` map to `nodeLogs`.
- `instanceId` is the **field name** of that node inside the generated `EventProcessor`
  (e.g. `hedgeRateSource`, `bidMakerOrder`, `scheduledTriggerNode_1`). The same `instanceId`
  may appear **more than once** in a record (a node can log at multiple points in the cycle).
- A `nodeLogs` value is an arbitrary Java `toString()`. This is the crux for parsing (§4).
- `eventTime = -1` marks non‑event‑driven cycles (scheduled triggers, exported service calls).
- `logTime` is the reliable timeline; `#header` time == `logTime` wall‑clock.

### 2.1 Why it is *not* valid YAML (worked examples)

Standard YAML flow‑maps break on these real lines:

- `hedgeQuantity: NaN` — `NaN` is not a YAML float (YAML wants `.nan`).
- `orderUpdate: MutableOrder(clOrdId=7492519643849023488, currentClOrdId=-1, venue=null, …)`
  — the value contains **top‑level commas**, so a YAML flow parser splits it into bogus
  entries.
- `venueStatus: connected=true requiredOrderVenues=[demoRfqOrders] missingOrderVenues=[]`
  — a space‑separated `toString` with `=` and `[]`.
- `connectedVenues: [demoRfqOrders]` — a bare (unquoted) flow‑seq inside the flow‑map.

⇒ We **must not** hand `nodeLogs` to SnakeYAML. We parse it with a bespoke, depth‑aware,
brace/paren/bracket‑respecting tokenizer (§4.2) and keep values as **raw strings**, typing
them lazily only when needed (graphing).

---

## 3. Goals / non‑goals for the implementation

- **Near‑zero runtime dependencies** beyond the JDK (Java 21, Swing). Rationale: the leniency
  requirement forces a custom parser anyway; HTTP is `java.net.http`; JSON for LLM payloads is
  a ~150‑line internal codec; YAML/Java colouring is a regex `StyledDocument` highlighter;
  charting is a custom `JPanel`. JUnit is test‑scope only. **One deliberate exception:** FlatLaf
  (UI theming, ~1 MB, MIT), added at user request and shaded into the runnable jar. The planned
  M10 REST transport uses the JDK's `com.sun.net.httpserver` — still no new dependency.
- **Streaming, index‑first** load so the same code path scales from 1 MB to multi‑GB.
- **Never fail a whole file** on a bad record — mark it `PARSE_ERROR`, keep going, show the raw
  text.
- Responsive UI: all parsing/LLM/IO off the EDT (`SwingWorker` / background executor).

---

## 4. Lenient parser (the crux)

### 4.1 Record framing (streaming)

1. Scan the file for record boundaries: a line that is exactly `---` (trim‑equal) starts a new
   record. Everything up to the next `---` (or EOF) is one raw record slice `[startOffset,
   endOffset)`.
2. Within a slice, recognise, leniently and independently:
   - the `#…` header line → `headerTime`, `thread`, `level`, `logger` (regex; optional).
   - scalar fields under `eventLogRecord:` matched by a simple `^\s{2}key:\s?(.*)$` line
     scanner: `eventTime`, `logTime`, `endTime` (→ `long`, `-1`→null), `groupingId`, `event`,
     `eventToString` (whole remainder of the line — never re‑split on inner `:`), `thread`.
   - `nodeLogs:` marks the start of the list; each following `^\s*- ` line is one node‑log item
     parsed by §4.2. Parsing of node‑logs is **deferred** (lazy) — the index stores only the
     `[offset,length]` of the `nodeLogs` block.
3. Any line we do not recognise is ignored for structure but retained in the raw slice (shown in
   the detail viewer verbatim).

### 4.2 Node‑log item tokenizer

Input example: `    - bidMakerOrder: { orderStatus: NEW, price: 19.977, orderUpdate: MutableOrder(clOrdId=1, venue=null)}`

1. Strip leading spaces and `- `. Split on the **first** `": "` → `instanceId` = left,
   `body` = right.
2. If `body` starts with `{` and ends with `}`, strip them → `inner`. Otherwise treat the whole
   `body` as a single unnamed value (lenient fallback).
3. Split `inner` on **top‑level commas only**: walk char‑by‑char tracking nesting depth for
   `()`, `[]`, `{}` and quote state (`"`/`'`); a comma at depth 0 and outside quotes is a
   separator. This protects `MutableOrder(a=1, b=2)` and `[a, b]`.
4. For each segment, split on the **first** `": "` → `key` / `rawValue` (trim both). A segment
   with no `": "` becomes `key=segment, rawValue=null` (a bare flag/token).
5. Result: `NodeLog{ instanceId, List<KV>{ key, rawValue } }`, **order‑ and duplicate‑
   preserving**.

**Typed view (lazy, for graphing/summaries):** `rawValue` → `long` → `double` (accept `NaN`,
`Infinity`, `-Infinity`) → `boolean` (`true`/`false`) → else `String`. `null` literal → null.

**Robustness:** unbalanced braces, missing `}`, embedded newlines inside a `toString`
(rare) are handled by "consume until the record's next `- ` at the same indent, else until
`endTime:`/`---`". Every fallback is silent and lossless (raw text preserved).

### 4.3 Output model

```
LogRecord {
  long   fileOffset, byteLength         // for lazy re‑read / mmap
  Long   eventTime, logTime, endTime    // null when absent or -1 (eventTime)
  String groupingId, event, eventToString, thread, logger, level, headerTime
  EventKind kind                        // OK | PARSE_ERROR
  // derived (§6)
  String  callback                      // e.g. "orderVenueConnected" or null
  String  eventDimension                // callback ?: event   (the group/filter key)
  // lazy:
  List<NodeLog> nodeLogs()              // parsed on demand from the slice/mmap
  String rawText()                      // the verbatim slice
}
NodeLog { String instanceId; List<KV> entries }
KV      { String key; String rawValue }
```

---

## 5. High‑level architecture

Packages under `telamin.fluxtion.audit.analyser.analyser`:

- `model` — `LogRecord`, `NodeLog`, `KV`, `EventKind`, typed‑value helpers.
- `parse` — `RecordFramer` (streaming boundaries), `HeaderParser`, `NodeLogTokenizer`,
  `LogIndex` (§7), `LogStore` (in‑memory vs mmap backends behind one interface).
- `index` — `RecordIndexEntry` (compact per‑record scalar index), builders.
- `filter` — time‑range + event‑dimension predicates, composable.
- `summary` — grouping/aggregation for the summary table.
- `graph` — series extraction (`instanceId.key` → points) + `ChartPanel`.
- `source` — `EventProcessorModel` (instanceId→type→FQN), `SourceRootResolver`, `JavaHighlighter`.
- `llm` — `LlmClient` (Anthropic/OpenAI), `PromptBuilder`, `Conversation`, `Json` (mini codec).
- `ui` — `MainFrame`, `ConfigPanel`, `LogTablePanel`, `DetailPanel`, `TimeRangeSlider`,
  `EventFilterPanel`, `SummaryPanel`, `GraphPanel`, `SourcePanel`, `LlmPanel`, `HelpPanel`,
  `YamlHighlighter`.
- `config` — `AppConfig` load/save (properties under `~/.fluxtion-analyser/`).

Threading: a single background `ExecutorService` for parse/index/LLM/IO; results marshalled to
the EDT. The table model is **virtual** (reads from `LogStore` by row index).

---

## 6. Derived event dimension (filter/summary key)

From the `(event, eventToString)` pair:

- If `eventToString` **is a method signature** (matches
  `^(public|private|protected)?\s*\w[\w.<>\[\]]*\s+[\w.$]+\.(\w+)\((.*)\)$`) → `callback` =
  the **method name** capture (e.g. `orderVenueConnected`, `onMultilevelMarketData`,
  `orderUpdate`). `eventDimension = callback`.
- Else → `eventDimension = event` (e.g. `ScheduledTriggerNode`, `LifecycleEvent`). For
  `LifecycleEvent`, optionally refine with `eventToString` (e.g. `LifecycleEvent:StartComplete`).

This dimension drives the **event‑type filter** (§8.4) and the **summary** (§8.6). We keep both
the raw `event` and the derived `callback` so the user can group either way.

---

## 7. Performance & memory strategy

**One index‑first design covers all sizes.**

1. **Index pass (streaming, off‑EDT):** memory‑map the file in chunks (`FileChannel.map`) or
   stream via `BufferedInputStream`; for every record produce a compact `RecordIndexEntry`:
   `{ long fileOffset; int byteLength; long logTime; long eventTime; long endTime; int
   eventDimId; int loggerId; int threadId; short flags }` where `eventDimId/loggerId/threadId`
   intern into dictionaries. ≈ 48–64 bytes/record ⇒ ~1 M records ≈ 50–64 MB regardless of file
   size. Scalar header fields needed by the table/filters/summary all live here — **no nodeLogs
   parse required** for browse/filter/summarise.
2. **Backends behind `LogStore`:**
   - `HeapLogStore` (file ≤ threshold, default 500 MB): slurp bytes into memory; slices are
     substrings; nodeLogs parsed lazily and optionally cached.
   - `MappedLogStore` (file > threshold): keep only the index in heap; re‑read a record's bytes
     from the mmap on demand (detail view, graph extraction). LRU cache of recently parsed
     records.
   - Threshold configurable; auto‑selected from file size and `-Xmx`.
3. **Table** is virtual over the index (columns are all index‑resident). **Detail/graph** trigger
   lazy nodeLogs parse for just the needed records.
4. **Graphing a key** over a huge file = one streaming pass extracting `instanceId.key` (progress
   bar, cancellable), producing a downsampled series if points ≫ pixels.

Progress + cancel for: index build, graph extraction, export, LLM calls.

---

## 8. UI design (Swing)

`MainFrame`: menu/toolbar + a `JTabbedPane` or split layout. Core layout: left = **log table**
(top) over **detail viewer** (bottom); right/tabs = **Summary**, **Graph**, **Source**,
**LLM**, **Help**. A top **filter bar** (time range + event filter + text search) applies to the
table, summary and graph consistently.

### 8.1 Config panel
Fields (persisted, §11):
- Log file to analyse (file picker) → triggers index build.
- Source roots (list, add/remove) — default seeds: `market-maker-lib`,
  `trade-calculator-api-lib`, `trade-calculator-impl-lib` source dirs.
- LLM: provider (Anthropic / OpenAI), API key (masked), model dropdown (per provider),
  optional base URL.
- Maven repos (list, add/remove; default `~/.m2/repository`) searched for `*-sources.jar` when a
  class isn't under any source root, plus a "don't search local repos" opt‑out. Lookups are lazy,
  session‑cached, and warmed in the background.
- EventProcessor: FQN list (add / edit — double‑click — / remove) with one marked **active**. Seeded by
  scanning `market-maker-lib/.../marketmaker/strategy` for `*.java` top‑level classes; default selected
  `com.acme.marketmaker.strategy.DemoMarketMakerStrategy`.
- Memory threshold (MB) for heap‑vs‑mmap (default 500).
- History: clear search history / saved graphs / recent files, individually or all at once.
- On a **first run** (no config file yet) the Settings dialog opens automatically after launch.

### 8.2 Log table (`JTable`, virtual model)
Columns: `eventTime`, `logTime`, `groupingId`, `event`, `eventToString`, `thread`, `nodeLogs`,
`endTime`.
- `eventTime`/`logTime`/`endTime` rendered as `yyyy‑MM‑dd HH:mm:ss.SSS` in **UTC**
  (`eventTime = -1` → blank).
- `nodeLogs` column shows a compact preview (e.g. `n nodes · instanceId, instanceId…`); full
  content in the detail viewer.
- Sortable, column show/hide, copy‑row‑as‑YAML, multi‑select. Row colour cues for anomalies
  (§10): `PARSE_ERROR`, any `…Breach: true`, `NaN`, status transitions.

### 8.3 Detail viewer
Selecting a row shows the record's **full `nodeLogs`** (and header/scalars) in a read‑only
`JTextPane` with **colourised YAML** (`YamlHighlighter`: keys, strings, numbers, booleans/null,
`NaN`, comments, punctuation). Multi‑select concatenates records (separated by `---`).
Right‑click an `instanceId` → **Open source** (§9) / **Graph this key** / **Copy**.

### 8.4 Event‑type filter (`EventFilterPanel`)
A checklist of **event dimensions** (§6) with counts, built from the index. Selecting a subset
filters table/summary/graph. Toggle "group by callback vs raw event". Free‑text search box
filters on `eventToString`/`thread`/(optionally) nodeLogs text.

### 8.5 Time‑range filter (`TimeRangeSlider`)
A custom two‑thumb, **draggable date‑time range** component spanning `[minLogTime, maxLogTime]`
from the index, with live labels and numeric entry. Dragging updates the active filter for
table/summary/graph. Histogram of record density drawn behind the track (nice‑to‑have).

### 8.6 Summary panel
A table grouped by **event dimension** within the active time range: columns `dimension`,
`count`, `firstLogTime`, `lastLogTime`, `span`, `rate/min`. Row click cross‑filters the main
table. Sort by count/time.

### 8.7 Graph panel
- Pick a series key `instanceId.key` (combobox populated by scanning nodeLogs keys — lazy /
  on demand; typeahead). Add multiple series.
- Custom `ChartPanel` line chart: X = `logTime`, Y = numeric value; `NaN`/non‑numeric → gaps.
  When a key appears multiple times per record, use the **last** occurrence (final state) and
  note it in the legend.
- Zoom/pan (drag‑select to zoom), hover tooltip (time + value + link to the record row),
  downsampling for large series, CSV export of the series.

### 8.8 Source panel
Given source roots + selected EventProcessor: resolve the class behind a record/instanceId (§9)
and show it in a read‑only `JTextPane` with **Java syntax colouring** (`JavaHighlighter`).
Class/FQN dropdown + "jump to declaring field / method". Shows the EventProcessor source with the
selected node field highlighted.

### 8.9 LLM panel (§10)
Conversation view (user/assistant bubbles), input box, **Send**, **Reset conversation**,
**Copy prompt**. Context chips show what will be sent (selected record(s), source snippets,
EventProcessor). If no API key: Send is replaced by **Copy prompt** with the fully assembled
text.

### 8.10 Help panel
`JEditorPane` (HTML) explaining: what Fluxtion audit logs are and how to read them; each column;
the derived event dimension; how nodeLogs map to graph nodes; graphing keys; the LLM workflow
(with/without key); source navigation; performance modes. Ships as a bundled resource.

---

## 9. Source resolution (instanceId → source file)

The link is the **generated EventProcessor**:

1. Parse the selected EventProcessor source (e.g. `DemoMarketMakerStrategy.java`) for **field
   declarations**: `… <Type> <instanceId> (= …)?;` → map `instanceId → simpleType`. The sample
   confirms fields like `MarketDataBookNode hedgeRateSource`, `MakerConfigNode makerContext`,
   `ScheduledTriggerNode scheduledTriggerNode_1`.
2. Resolve `simpleType → FQN` using that file's `import`s (and same‑package fallback).
3. Resolve `FQN → File` by walking the configured **source roots**
   (`<root>/<pkg-as-path>/<Simple>.java`).
4. For a record, the openable targets are: (a) each `instanceId`'s declaring node class, and
   (b) the class named in `eventToString` (the method's declaring type / the event type) — both
   parsed straight out of the signature.
5. If roots are incomplete, degrade gracefully: show the FQN and offer "locate…".

**Inferring the EventProcessor (decision: infer when possible).** The log header's `logger`
(e.g. `MAKER_USDMXN_DEMO`) is the strategy's audit *name*, not its FQN, so we infer by structure:
for each candidate EventProcessor discovered under the strategy source dir, build its
`instanceId→field` set (step 1) and score it against the **distinct `instanceId`s observed in the
log**. The best‑covering processor is auto‑selected (ties → the configured default
`DemoMarketMakerStrategy`); the user can always override. Inference runs after the index pass has
collected the log's instanceId set (a cheap by‑product of the first detail parse, or a quick scan).

A light lexer (no external parser) suffices: we only need field decls and imports, so a
regex/line scan over the single EventProcessor file plus a filesystem lookup. Cache the
`instanceId→FQN→File` map per EventProcessor.

---

## 10. LLM integration

**Providers:** Anthropic Messages API and OpenAI Chat Completions, via `java.net.http.HttpClient`
(async, streaming optional). Model dropdown per provider (editable). Key + optional base URL from
config. **Recommendation: seed the prompt programmatically — no "skill" is required.** A skill
adds packaging/versioning overhead we do not need for a single embedded prompt; instead ship a
`system` template as a bundled resource so it is easy to edit and review.

**Prompt assembly (`PromptBuilder`):**
- **System:** an explainer of Fluxtion audit semantics — a record = one propagation cycle;
  `nodeLogs` = ordered `instanceId: {key: value}` maps where `instanceId` is a node field in the
  EventProcessor graph; `eventTime=-1` meaning; the derived callback; that values are Java
  `toString()`s. (Bundled `llm/system-prompt.md`.)
- **Context:** the selected record(s) rendered as their raw `nodeLogs`/YAML; the selected
  **EventProcessor FQN**; and, when source roots are present, **source snippets** for the
  classes referenced by the record (the `instanceId` node types + the `eventToString` type) —
  trimmed to declaration + relevant method. Guard total context size (token budget); truncate
  snippets first, with a note.
- **User turn:** the question typed in the panel (default seed: "Explain this eventLogRecord").

**Conversation:** an ordered `List<Message{role, content}>`; each Send appends the user turn and
the assistant reply. **Reset** clears the list (keeps the system template). Context (records/
source) is re‑attached to the first user turn (or refreshed when the selection changes — user
choice).

**No‑key mode:** `PromptBuilder` produces the full text (system + context + question); the panel
shows it read‑only with **Copy**. Same builder path guarantees parity with the API mode.

**Mini JSON (`llm/Json`):** minimal object/array/string/number/bool/null reader+writer (~150
lines) — enough for request bodies and to pluck `content[0].text` (Anthropic) /
`choices[0].message.content` (OpenAI). Keeps the zero‑dependency goal; Jackson remains an easy
drop‑in if we later relax it.

**Safety:** API keys stored per §11 (never logged); requests time‑boxed; errors surfaced
non‑fatally in the panel.

**Whole‑log file access (agentic seeding — round 9).** When the loaded log has a readable local file,
`PromptBuilder` also seeds a **file‑access block**: the file **path** (for S3 the *fetched local temp
file*, via `LogStore.localFile()`), its **shape** (human size, record count, UTC time span), a one‑line
**framing** description (records split on `---`; `#HH:MM:SS.mmm [thread] LEVEL logger` header; `nodeLogs`
= `- instanceId: {…}`), and, per selected record, its **byte offset + length + logTime** (`LogFileInfo`).
A file‑reading model treats the selection as a *starting point*: it can grep/seek **both directions**
from an anchor (read‑behind for causes, read‑ahead for consequences) and count occurrences, instead of
relying only on the pasted records. Inert text for a non‑agentic target — no toggle, graceful
degradation like source roots. This is the seed that **[spec-assistant-actions.md](completed/spec-assistant-actions.md)**
(M10) completes with a return path.

---

## 11. Configuration & persistence

`AppConfig` persisted as properties (or small JSON via the mini codec) under
`~/.fluxtion-analyser/config`. Fields: last log file, source roots, maven repos + search toggle,
provider, model, base URL, **API keys stored in cleartext** (decision: local single‑user convenience
tool; the file lives in the user's home dir), EventProcessor FQN list + selection, memory threshold,
column visibility, window bounds. Recent‑files list. **Display time zone is UTC** (all epoch‑millis fields rendered as
`yyyy‑MM‑dd HH:mm:ss.SSS 'UTC'`).

---

## 12. Colouring approach (no deps)

- `YamlHighlighter` and `JavaHighlighter`: regex‑driven token styling into a `StyledDocument`
  (keys, strings, numbers, booleans/`null`/`NaN`, comments, punctuation / Java keywords,
  literals, comments, annotations). Applied on a background pass for large detail, then set on
  the EDT. Because detail = one record (or one source file), cost is bounded.

---

## 13. Additional useful features (status)

_Most of this list shipped; see tracker.md for the round it landed in._
- ☑ **Quick text filter** across `eventToString`, `thread` **and** nodeLogs text.
- ☑ **Export** filtered rows to CSV / YAML; export a graph series to CSV (+ graph→PNG).
- ☑ **Bookmarks/flags** on rows; a "flagged only" filter.
- ☑ **Anomaly highlighting** (`PARSE_ERROR`, `NaN`, `…Breach: true`) + **jump to next anomaly**
  (F3 / Shift+F3), round 8.
- ☑ **Diff two records** side‑by‑side (menu + table right‑click).
- ☑ **Follow/tail mode** for a growing log file (round 8).
- ☑ **Themes** (FlatLaf Light/Dark/IntelliJ/Darcula) and **S3 sourcing** (`s3://` via the `aws` CLI to a
  temp file) — added at user request; not in the original spec body.
- ☐ **Column for `logger`/processor**, filterable (multi‑processor files).
- ☐ **Deep‑link**: from a graph point or summary row back to the exact table row.
- ☑ **Session**: remember filters via saved graphs / columns / window bounds per profile.
- ☐→ **Assistant actions** (two‑way LLM curation loop) — designed in
  **[spec-assistant-actions.md](completed/spec-assistant-actions.md)** (M10).

---

## 14. Risks & edge cases

- Node‑log `toString`s with embedded `}` or newlines → tokenizer fallbacks (§4.2); always keep
  raw text so nothing is lost.
- Duplicate `instanceId` within a record (confirmed in sample) → model preserves order; graph
  uses last, detail shows all.
- `eventToString` that is *not* a method sig but *looks* like one → regex is conservative;
  mis‑classification only affects grouping, never data.
- Very wide `nodeLogs` (single line > MB) → detail viewer chunk‑renders; table preview truncates.
- Multi‑GB files → mmap + index; ensure 64‑bit offsets and chunked mapping (2 GB map limit).
- Time zone: epoch millis are absolute; **display zone is UTC** (resolved decision, §11) — not
  system‑dependent, so timestamps compare cleanly across machines.
- API keys at rest: obfuscation ≠ encryption; documented + optional non‑persist.

---

## 15. Build & run

- Maven, Java 21, `groupId com.telamin / artifactId fluxtion-auditlog-analyser`. Near‑zero runtime deps (FlatLaf only,
  §3); JUnit 5 test‑scope; `maven-shade` for a runnable jar; `Main` launches `MainFrame`.
- Target: `java -jar analyser.jar` (double‑click‑able). No native bits.

---

## 16. Package / class map (initial)

```
telamin.fluxtion.audit.analyser
├─ Main                          (launch MainFrame; package sits one level above the app packages)
└─ analyser/
   ├─ model/   LogRecord NodeLog KV EventKind TypedValue
   ├─ parse/   RecordFramer HeaderParser NodeLogTokenizer LogStore HeapLogStore MappedLogStore
   ├─ index/   RecordIndexEntry LogIndex Dictionary
   ├─ filter/  TimeRangeFilter EventDimensionFilter TextFilter FilterState
   ├─ summary/ SummaryModel SummaryBuilder
   ├─ graph/   SeriesExtractor Series Expr ChartPanel
   ├─ source/  EventProcessorModel SourceRootResolver MavenSourceResolver SourceService SourceNavigation
   ├─ llm/     LlmClient AnthropicClient OpenAiClient PromptBuilder Conversation Json
   ├─ config/  AppConfig ConfigStore GraphSpec
   └─ ui/      MainFrame ConfigPanel LogTablePanel DetailPanel TimeRangeSlider
               EventFilterPanel SummaryPanel GraphPanel GraphTabs SourcePanel LlmPanel HelpPanel
               YamlHighlighter JavaHighlighter Renderers
```

_(Historical note: the project originally lived under `com.acme.analyser`; it moved to
`telamin.fluxtion.audit.analyser.*` with maven coordinates `com.telamin:fluxtion-auditlog-analyser`.
Older tracker entries refer to the pre-move class names.)_

---

## 17. Milestones

See **[tracker.md](tracker.md)**. Ordering: parser+index first (M1), then table+detail (M2),
filters+summary (M3), source (M4), LLM (M5), graph (M6), large‑file mode (M7), polish/help/extras
(M8).