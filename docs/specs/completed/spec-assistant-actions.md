# Assistant Actions — Two‑Way Curation Loop (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-12

Companion to **[spec.md](../spec.md)** and **[tracker.md](../tracker.md)** (milestone **M10**).

Turns the LLM assistant from a one‑way explainer into a **two‑way tool**: the app already seeds
the prompt with whole‑log file access (path + per‑record byte offsets, see spec.md §10 / round 9);
this adds the return path. The assistant can ask the app to **compute** over the index (query verbs)
and to **build curation** in the UI (render verbs). The division of labour stays: *the app curates
(records, sources, anchors, aggregations); the model retrieves and reasons.*

---

## 1. Motivation

The current loop is: seed context → model reads/greps the log → model replies in **text**. Two gaps:

1. **Findings are unverifiable claims.** "failedValidation clusters at 14:00–15:00" is a sentence,
   not an artifact. The human must manually re‑curate (filter, open Summary, add a graph) to check it.
2. **The model can't cheaply aggregate.** Raw file `grep` finds and counts lines, but cannot do typed
   aggregation (rates, spans, per‑dimension counts, NaN counts, "bucket by hour") — and on the 2 GB
   memory‑mapped case even counting gets expensive. The **`LogIndex` does this in milliseconds.**

The fix is a small **action schema** the model can invoke: **query** actions (app computes over the
index, returns JSON) and **render** actions (app builds a filter / graph / navigation in the UI).

> **Reframe:** the artifact is the *schema*, not the transport. Transport is cheap plumbing and we
> want two (in‑process + localhost REST) over the same JSON.

---

## 2. Scope

**In scope (v1):**
- One versioned **action schema** (verbs + params + result shapes).
- An **in‑process executor** that runs actions parsed from the assistant's own chat reply (works with
  the built‑in Anthropic/OpenAI path — no network).
- A **localhost REST transport** carrying the *same* JSON, for external agentic clients (e.g. a Claude
  Code / tool‑runner harness that can make HTTP calls).
- A read‑only **`aggregate` query** verb over `LogIndex`.
- **`filter` / `graph` / `goto`** render verbs mapping onto existing `FilterState` / `GraphTabs` / table.
- Prompt **seed manifest**: endpoint URL + session token + verb list appended to the file‑access block.

**Non‑goals (v1):** mutating config or writing files; anything that isn't read‑only *over the log*;
issuing orders or touching venues; authentication / multi‑user; remote (non‑loopback) binding; a
generic scripting surface. Explicitly excluded so the inbound control path stays low‑stakes.

---

## 3. Why this is a thin seam

Every verb maps onto capability that already exists — same economics as the byte‑offset work:

| Verb | Kind | Backed by (existing) |
|---|---|---|
| `aggregate` | query | `summary.SummaryBuilder`, `LogIndex` columns, histogram bucketing |
| `filter` | render | `filter.FilterState` (already the one observable filter driving every view) |
| `graph` | render | `ui.GraphTabs.addSpecs` / `SeriesExtractor` |
| `goto` | render | table selection + `scrollRectToVisible` (round 8) |
| `flag` | render | bookmark/flag **bit** reuses H8.3; the optional **note** is a *small new* store (see §4.5) |

New code is: the schema types, a dispatcher, an HTTP listener, and the seed lines. The *work* each verb
does is already written and tested.

---

## 4. Action schema (v1)

A single request shape, transport‑independent. JSON (mini `llm.Json` codec, already present).

```jsonc
// request
{
  "v": 1,                       // schema version
  "token": "<session-nonce>",   // must equal the token seeded in the prompt
  "action": "aggregate",        // one of: aggregate | filter | graph | goto | flag
  "params": { ... }             // per-verb (below)
}

// response
{ "ok": true,  "action": "aggregate", "result": { ... } }   // query verbs carry a result
{ "ok": true,  "action": "filter",    "applied": { ... } }  // render verbs echo what was applied
{ "ok": false, "error": "unknown verb 'foo'" }
```

### 4.1 `aggregate` (query — read‑only, returns data)

**Index‑path example** — market‑data cadence per hour (a real question from the validation session).
`onMultilevelMarketData` *is* an event dimension, so this stays O(index):

```jsonc
"params": {
  "metric":   "count",              // count | rate_per_min | nan_count | breach_count
  "groupBy":  "hour",               // dimension | thread | hour | minute | day | none
  "filter": { "dimensions": ["onMultilevelMarketData"] },   // index-resident → O(index)
  "limit": 500                       // cap buckets returned (default 500)
}
```

Result — **echoes the exact filter it computed over** (a count is meaningless without its population):

```jsonc
"result": {
  "metric": "count", "groupBy": "hour", "total": 30339,
  "buckets": [ { "key": "2026-08-06T14:00Z", "count": 118 },
               { "key": "2026-08-06T15:00Z", "count": 120 } ],
  "population": { "records": 30339, "filter": { "dimensions": ["onMultilevelMarketData"], "from": null, "to": null, "text": null },
                  "scan": "index" }         // "index" (O(index)) | "raw" (streaming byte pass)
}
```

**Raw‑path example** — the headline `failedValidation` question. `failedValidation` is a **nodeLogs key**
(emitted by `quoteValidator`), **not** a dimension — so it *must* go through `text`, which is a raw scan:

```jsonc
"params": { "metric": "count", "groupBy": "hour", "filter": { "text": "failedValidation" } }
// -> "population": { ..., "scan": "raw" }   // honest: streaming byte pass, seconds on a multi-GB file
```

#### 4.1.1 Fast path vs raw scan — be honest about cost

The performance claim ("answers in ms") holds **only for index‑resident filters**. `LogIndex` holds
`dimension` (event/callback types) and `thread`, the three time columns, and the `parseError` / `nan` /
`breach` flags — filtering or grouping on those, and every `metric`, is O(index) with **no node‑log
parsing**.

`filter.text`, however, matches **nodeLogs text**, which is *not* index‑resident (the current UI text
filter reads record bytes for exactly this reason). So a `text` filter forces a **streaming raw pass**
over the framer — one sequential read of the file: fine for MB‑scale logs, **seconds** on the 2 GB mmap
case. When a `text` filter is present the result reports `"scan": "raw"`; the model is told (system
prompt) to prefer a dimension/flag filter where one exists, or to accept the cost knowingly. **The spec
must not imply index speed with raw semantics** — hence the two worked examples above are explicit about
which path each takes.

> **The uncomfortable truth (and why index‑time text flags matter).** The *motivating* question —
> "`failedValidation` by hour" — has **no index‑resident answer in v1**: the index flags are
> parse‑error / NaN / breach only, and `failedValidation` lives in `nodeLogs`. So the headline use case
> is exactly the raw‑scan path today. Configurable **index‑time text flags** (conditions matched once
> during framing → index bits, same class as `FLAG_NAN` / `FLAG_BREACH`) would make precisely this class
> of anomaly count genuinely O(index). Deferred past v1 (A10.7 / open questions) — it adds an
> index‑build config surface, but the payoff is the most common question users actually ask.

**`rate_per_min` denominator (defined):** for **time buckets** (`hour`/`minute`/`day`) the rate is
`count / bucket‑width‑minutes`; for **non‑time** group‑bys (`dimension`/`thread`/`none`) it is
`count / filtered‑span‑minutes` (the span of the filtered population), and `groupBy:none` also carries a
top‑level `rate_per_min` over the whole population.

This is the **high‑value verb**: it lets the model offload typed aggregation (rates, spans, per‑dimension
counts, hourly buckets) that file `grep` can't do at all.

### 4.2 `filter` (render — mutates the shared FilterState)

```jsonc
"params": { "from": 1754445600000, "to": 1754449200000,
            "dimensions": ["orderVenueConnected"], "text": "failedValidation" }
```
Applies to `FilterState`; the table, summary, graph and slider all refresh (already wired). Missing
fields = unchanged; `null` = cleared. **Echoes the resulting full filter state** (not just the delta) —
the model needs to know the *total* population every view now shows, not only what it changed (review #6).

### 4.3 `graph` (render — opens/updates a *named* graph tab)

```jsonc
"params": { "name": "Hedge vs position",   // names the tab; addresses an existing graph to update
            "series": ["hedgeToOrdersNode.hedgeQuantity", "positionNode.position"],
            "style": "step",          // step | line | points
            "from": null, "to": null,
            "newTab": false }         // omitted/false → reuse the named graph if it exists, else create
```
**Graphs are named** (the tab title): `GraphTabs.graphForAction(name, newTab)` reuses the named graph
when it exists and `newTab` is not true, else creates one (named if `name` given) — so the model can build
a graph, then *refine the same graph* by name across turns rather than spawning duplicates. **`newTab`
default (omitted) = reuse the named graph if it exists, else create.** Rename requires an **explicit
target** — `{"action":"graph","params":{"name":"old","rename":"new"}}` — never selection‑dependent, since
"the selected tab" is UI state the model can't observe.
Names persist in the profile (`config/GraphSpec`) and are the **promotion contract** to Grafana (§12).
Non‑numeric / NaN handled by the existing series extractor. **Echoes which series resolved and which did
not** (`{name, resolved:[…], unresolved:[…]}`) rather than silently opening an empty tab on a typo'd
`instanceId.key` (review #6) — an unresolved series is feedback the model can fix.

> **Extensions (M14, designed): pinned range + derived series.** The `from`/`to` params (above) will
> **pin** a graph to a fixed window instead of following the shared filter, and an `exprs` param will plot
> **formula-defined** series (`askMakerOrder.price − bidMakerOrder.price`) via a tiny hermetic arithmetic
> grammar — turning an LLM-built graph into a persisted, promotable artifact. See
> **[spec-graph-artifacts.md](spec-graph-artifacts.md)**.

### 4.4 `goto` (render — jump the table to a cited record)

```jsonc
"params": { "byteOffset": 14397634 }   // or "recordIndex": 1203441
```
Selects and scrolls to that record. **Floor semantics:** an offset from a `grep` match usually lands
*inside* a record, not on its start — so resolve to *the record whose `[offset, offset+length)` contains
the byte* (binary search over `LogIndex.offset` for the greatest start `<=` the given offset), never an
exact‑match that would silently miss. **Out of range** (before the first record / past EOF): **clamp to
the nearest record and echo the resolution** (`{recordIndex, byteOffset}`) rather than failing — the
model then knows exactly where it landed. Lets the model say "see the fill at byte X" and the human lands
on it.

### 4.5 `flag` (render — bookmark records the assistant found interesting)

```jsonc
"params": { "byteOffsets": [14397634, 14402110],   // and/or "recordIndexes": [1203441]
            "note": "duplicate re-send of the 20.082 modify" }
```
Flags those records — the **flag bit** reuses H8.3 exactly; offsets are floor‑resolved like `goto`
(§4.4). The optional **`note` is a small new feature**, honestly: H8.3 today is a boolean tint +
flagged‑only filter with *no* note storage, so the note adds a `row → String` map, a hover tooltip, and a
**persistence decision** — do assistant notes survive a reload? (Lean: session‑only in v1, like flags,
which are already per‑file model‑row state; persistence is a follow‑up.) This directly serves the spec's
motivation (*"findings are unverifiable claims"*): the assistant's findings become **reviewable,
reversible artifacts in the table** (clear‑all‑flags undoes them). Promoted into **v1** alongside the
other render verbs; `note` may ship a step later if the store proves fiddly.

---

## 5. Transports

### 5.1 In‑process executor (no network)

For the app's **own** chat (LlmClient → Anthropic/OpenAI). The reply is plain text, so the app extracts
actions from it and runs them:

- The model emits a fenced block:
  <pre>```analyser-action
  { "action": "aggregate", "params": { ... } }
  ```</pre>
  (token not required in‑process — provenance is the local reply itself.)
- On receiving a reply, `LlmPanel` scans for `analyser-action` blocks, hands each to the
  `ActionDispatcher`, runs render verbs (ack line) and computes query verbs.

#### 5.1.1 The round trip is an agent loop — bound it (resolves review #2)

A query result appended to the transcript is only visible to the model **on the next API call**. For a
*true* round trip the app therefore **auto‑resends**: after executing the query verbs in a reply, it
appends their JSON results as a context turn and calls the model again so it can reason on real data.
That is an **agent loop**, and it needs explicit control (previously under‑specified):

- **Round cap:** at most **3 action‑rounds per user turn** (`maxActionRounds`, configurable). After the
  cap the app stops resending and shows the last reply plus a "reached action‑round limit" note.
- **Per‑round action cap:** at most **N actions per reply** (default 20) — the runaway‑agent guard;
  pulled forward from A10.7 into A10.3 (in‑process) and A10.5 (REST). ~5 lines, but it's the loop's brake.
- **Cancel + progress:** the loop runs off‑EDT with a visible spinner and a **Cancel** in `LlmPanel`;
  cancelling stops after the in‑flight call.
- **Cost visibility:** each auto‑round is a billed API call — the transcript marks auto‑rounds ("↻ round
  2/3") so the cost is legible, and render‑only replies (no query) never trigger a resend.
- **Errors are fed back too (review #3).** An `ok:false` (malformed params, unknown series, cap exceeded)
  is the feedback the model most needs to self‑correct — it is appended and resent **exactly like a
  result** (visible + marked), and it **counts toward the round cap**. Without this a single typo would
  make the model reason on silence and burn its turn producing nothing. A render‑verb error also triggers
  a resend (unlike a render *success*, which doesn't) precisely so the model can fix and retry.

Render verbs alone do **not** auto‑resend (nothing to feed back) — only query results do. The whole
in‑process path opens **no port** and works even when the REST transport is disabled.

**Result‑back is a visible turn (decided).** The query JSON is appended as a **visible, clearly‑marked**
transcript entry ("▸ aggregate result …"), not a hidden context turn — same "verify, don't infer"
principle as file‑access seeding: the human sees the exact numbers the model reasoned on.

#### 5.1.2 Only execute exact‑tag blocks (resolves review #5c)

If the user asks "what actions can you emit?", the model will *illustrate* an `analyser-action` block —
which the executor would then run (harmless, since read‑only + reversible, but confusing). Mitigation:
the seed instructs the model to use an **`analyser-action-example`** fence for illustration, and the
executor runs **only** blocks tagged exactly `analyser-action`. Example fences are inert.

### 5.2 localhost REST (external agents)

For an agentic client that can make HTTP calls (e.g. the harness that ran shell commands in the
validation transcript):

- `com.sun.net.httpserver.HttpServer` (JDK‑native — **no new dependency**, consistent with the
  near‑zero‑dep ethos), bound to `127.0.0.1` on an **ephemeral port**.
- Single endpoint: `POST /action` with the request JSON; `GET /manifest` returns the verb list, schema
  version **and the caps** (`maxActionsPerReply`, the REST rate limit) so an external agent can **pace
  itself instead of discovering limits by failing** (review #6).
- The **same `ActionDispatcher`** handles both transports.
- **REST pacing (review #5).** The per‑reply cap has *no* REST analogue — there is no "reply" to count
  per, and an external agent could POST in an unbounded loop. Since the surface is read‑only + loopback,
  the blast radius is low, but the server still applies a small **token‑bucket rate limit** (default ~10
  req/s) so a runaway agent degrades gracefully (`429`) instead of pegging the EDT with render verbs.
  Stated explicitly so the guard isn't merely implied.

The schema is identical across both; only the door differs.

### 5.3 MCP as a third transport (future — and why not *instead*)

An MCP server would map each verb to an MCP tool over the **same `ActionDispatcher`/`RenderExecutor`
seam** — a third door, not a redesign ("schema is the artifact, transport is plumbing"). It is the
**cleaner transport for MCP-native clients** (Claude Code / Desktop): structured native tool-calls instead
of fenced-block parsing, protocol-level discovery instead of a prompt manifest, and stdio transport that
is inherently local (less need for the token/Origin/rate-limit guards REST carries). Worth adding when the
primary external consumer is MCP-capable.

But MCP is **not a replacement** for either transport already built:
- The app's **own chat** (built-in Anthropic/OpenAI panel) has no MCP client — the **in-process**
  fenced-block loop is the only thing that can drive it. MCP can't cover this case at all.
- The **universal "paste this prompt into whatever LLM you have"** flow needs a self-describing endpoint
  the model can `curl`; **REST + an inline endpoint/token in the copy-prompt** works with any client that
  can make an HTTP call and needs **zero client-side config**, whereas MCP requires the user to register
  the server in their MCP client and restart.

So: in-process (essential, MCP-incapable case) + REST (universal, zero-config) today; MCP as a **preferred
add-on transport for MCP-native agents** later. The dispatcher/executor architecture already supports it.
Full design: **[spec-assistant-actions-mcp.md](../spec-assistant-actions-mcp.md)** (M13).

---

## 6. Security & posture

This adds an **inbound control path** to a currently hermetic desktop tool — treated conservatively:

- **Read‑only over the log.** The verb whitelist never mutates config, writes files, or touches
  anything outside curation. Even a hostile caller can only build graphs and read aggregations.
- **Loopback only**, ephemeral port. Never binds a routable interface.
- **Session token** (a nonce generated per app run) seeded into the prompt; REST calls must echo it in
  an `X‑Analyser‑Token` header. Reject requests missing/wrong token.
- **Reject any request carrying an `Origin` header at all** (resolves review #5a) — agents/`curl` never
  send one; browsers always do on cross‑origin POSTs. Strictly simpler and safer than parsing origins,
  and it moots DNS‑rebinding (where the `Origin` is the attacker's own domain) — the token remains the
  real defence regardless.
- **Token leaves the machine, by design** (resolves review #5b): the seeded token rides the prompt into
  third‑party LLM providers and into clipboard/copy‑prompt history. That is the intended use; the blast
  radius is bounded to *"read this local log's aggregates and drive graph tabs/flags until the app
  restarts"* (read‑only, reversible, no file/config/venue access) — which is exactly why the token is
  **per‑run ephemeral** and the verb set is read‑only. Stated so a security reviewer sees it was weighed.
- **Opt‑in.** REST transport is **off by default**; a Settings toggle *"Allow the assistant to drive the
  UI (localhost)"* enables it. When off, the seed omits the endpoint (graceful degradation, same as
  source roots / file access). The in‑process executor may remain on when off (it opens no port) — its
  own toggle *"Let the assistant build views from its replies."*
- **EDT discipline & the follow‑mode race** (resolves round‑1 #3 + round‑2 #2). `LogIndex` is **not**
  immutable during a session — round‑8 follow/tail (`HeapLogStore.appendFrom`) grows it live, so an
  off‑EDT query racing the tail poller could tear. Fix: queries take an **immutable snapshot** —
  `LogIndex.snapshot()` captures, **under the index's lock**, `size`, the current column‑array
  references, **and defensive copies of the `dimension`/`thread` dictionaries** (`Dictionary.copyValues()`
  — the dictionaries also grow when a tailed record introduces a new callback/event type, and their
  `ArrayList` backing would tear a concurrent `get`, review #2). `appendFrom` publishes each row under
  the same lock (`add` is `synchronized`). The query then computes lock‑free over `[0,size)` with the
  captured refs/copies (primitive arrays only grow via `copyOf`, so an older ref still holds valid
  `[0,size)` data; the dictionary *copy* holds no live reference at all). Follow‑appended rows and new
  dimensions simply appear in the *next* snapshot, never mid‑scan. Render verbs mutate the UI only via
  `SwingUtilities.invokeLater`.
- **Attribution & reversibility.** Assistant‑built views are labelled *"built by assistant"* and are
  trivially undone (reset the filter, close the graph tab) — so actions apply live without a modal
  gating every step, which would kill the flow.

---

## 7. Prompt seeding

The file‑access block (round 9) gains, only when a transport is enabled:

```
Assistant actions: you may POST JSON to http://127.0.0.1:53411/action
(header X-Analyser-Token: 9f2c…) to compute over the index or build views.
GET /manifest for the schema. Verbs:
  aggregate {metric, groupBy, filter?}  -> typed counts/rates/spans over the index; the result
                                           echoes its population + scan:index|raw. Prefer a
                                           dimension/flag filter (O(index)); filter.text forces a
                                           slow raw byte scan. Use this instead of grepping for counts.
  filter    {from,to,dimensions[],text} -> narrows every view
  graph     {series:[instanceId.key], style} -> opens a time-series graph
  goto      {byteOffset|recordIndex}    -> selects the record containing that byte (clamped + echoed)
  flag      {byteOffsets[]|recordIndexes[], note?} -> bookmarks records so your findings are reviewable
Caps: <=20 actions per reply; GET /manifest reports the exact caps + REST rate limit so you can pace.
Prefer `aggregate` over file greps for counts/rates; the index answers in ms over millions of records.
To *illustrate* an action without running it, use a ```analyser-action-example``` fence (never executed).
```

For the in‑process path the same manifest is seeded minus the URL/token, telling the model to emit
```analyser-action``` blocks (only that exact tag is executed). If no transport is enabled the block is
omitted entirely.

---

## 8. Components (new)

_Status: ☑ = built (slices 1–2, 115 tests green), ☐ = later slice._
- ☑ `llm/ActionResult` — the outcome record (`ok`/`action`/payload/`error`) + `toMap()`/`toJson()`;
  errors carry the message that is fed back to the model (#3). (No separate `ActionRequest` type — the
  dispatcher parses the request map directly via `llm.Json`.)
- ☑ `llm/ActionFilter` — the population selector (`dimensions`/`from`/`to`/`text`); `matches()` mirrors
  `FilterState`; `isRawScan()` flags the `text` path; `toMap()` echoes it back.
- ☑ `llm/ActionDispatcher` — validates version/token/verb, routes to a handler, returns `ActionResult`;
  **never throws**. Transport‑agnostic; slice‑1 wires `aggregate`, render verbs report not‑yet‑enabled.
- ☑ `llm/AggregateService` — pure computation over a `LogIndex.Snapshot` (count / rate_per_min / nan /
  breach; group by dimension / thread / hour / minute / day; optional filter, reporting `scan:index|raw`;
  defined rate denominator). No Swing.
- ☑ `index/LogIndex.snapshot()` (+ `Dictionary.copyValues()`) — synchronized immutable view capturing
  `size`, column‑array refs **and dictionary copies**; `add` is `synchronized`, so off‑EDT queries never
  tear against follow‑mode appends (§6; review #2).
- ☑ `llm/RenderExecutor` (UI‑free seam) + ☑ `ui/ActionExecutor` — applies **render** verbs on the EDT
  (`invokeAndWait`); bridges to `FilterState`, `GraphTabs` (named build/refine/rename), table selection,
  flag store; floor‑resolves + clamps offsets; echoes full filter state / resolved‑vs‑unresolved series /
  goto resolution; `flag` **note** map + hover tooltip. `floorRow`/`clampRow` are pure (unit‑tested).
- ☑ `net/ActionServer` — the loopback `HttpServer` wrapper (start/stop, port, `X‑Analyser‑Token` guard,
  **reject any `Origin` header**, token‑bucket rate limit, `/manifest` with verbs + caps).
- ☑ `llm/ActionParser` — extracts exact‑tag `analyser-action` block bodies; `-example` / other fences ignored.
- ☑ `ui/LlmPanel.runRound` — the **bounded agent loop** (auto‑resend query **and error** results,
  `maxActionRounds` cap, per‑reply cap, Cancel, "↻ round n/m" markers, visible marked result turns).
- ☑ `llm/PromptBuilder.inProcessActionManifest` — seeds the verbs on the first turn (no URL/token).
- ☑ `config/AppConfig` — `assistantActionsInProcess` (**on**), `assistantActionsRest` (**off**),
  `maxActionRounds` (**3**), `maxActionsPerReply` (**20**), persisted; Settings **Assistant** tab toggles +
  spinners; live URL/token shown in the status bar + console when REST starts.

---

## 9. Delivery order (thin slices)

1. ✅ **DONE** — **Schema + `AggregateService` (incl. `LogIndex.snapshot()` + `Dictionary.copyValues()`)
   + `ActionDispatcher`** — query only, headless, unit‑tested (18 new tests; 106 total green). Snapshot
   race‑guard incl. the concurrent new‑dimension append case. _(Per‑reply cap lands with the loop in
   slice 2, where a "reply" exists.)_
2. ✅ **DONE** — **In‑process executor + bounded agent loop** in `LlmPanel.runRound` (parse exact‑tag
   blocks via `ActionParser`, run `aggregate`, auto‑resend query + error results, `maxActionRounds`=3 cap
   + per‑reply cap + Cancel + round markers) + `PromptBuilder.inProcessActionManifest` (no URL) + config
   defaults. Zero network surface. `ActionParseTest` (5); 115 total green.
3. ✅ **DONE** — **Render verbs** (`filter`, `graph`, `goto`, `flag`) via `ui/ActionExecutor` (through the
   `llm/RenderExecutor` seam), EDT‑marshalled; named‑graph build/refine/rename; floor+clamp offset
   resolution; full‑filter/resolved‑series/goto‑resolution echoes; loop resend refined to query‑or‑error
   only; manifest on first enabled turn. `GotoResolveTest` (4); 119 total green.
4. ✅ **DONE** — **localhost REST** (`net/ActionServer`, `/action` + `/manifest`, `X‑Analyser‑Token` +
   no‑`Origin` guard + 10 req/s token bucket) + Settings **Assistant** tab opt‑in + URL/token in the status
   bar and the copy‑prompt seed. `ActionServerTest` (6); 125 total green.
5. Docs (help/README), tracker close‑out.

Slice 1 is the recommended first build: highest value (index as the model's aggregation backend),
lowest surface (no port, no UI mutation), and unit‑testable exactly like the offset work.

---

## 10. Testing

- ☑ `AggregateServiceTest` — count/rate/nan/breach × group‑by dimension/hour/day against `sample.yml`
  (21 records); dimension‑filter (index) vs text‑filter (raw) both correct with the right `scan`; text
  with no raw source matches nothing (never a silent full‑scan miss); population echo; empty‑result safety.
- ☑ `LogIndexSnapshotTest` — a snapshot taken before `appendFrom` sees only the pre‑append rows and
  resolves its dimensions unaffected; a **concurrent append that grows the dictionaries with a new
  dimension** does not tear a reader (threaded stress).
- ☑ `ActionDispatcherTest` — version mismatch, missing/wrong/correct token (REST), unknown verb, missing
  action, render‑verb‑not‑enabled, malformed JSON → structured `ok:false` (never throws).
- ☑ `ActionParseTest` — extract only exact‑tag `analyser-action` blocks (none/one/many/malformed);
  `analyser-action-example` and ```json fences ignored.
- ☑ `GotoResolveTest` — exact‑start + mid‑record offset floor to the containing record; out‑of‑range +
  recordIndex clamp to first/last.
- ☐ `ActionServerTest` (slice 4) — loopback bind, `/manifest` shape (incl. caps), token rejection, **any
  `Origin` header rejected**, rate‑limit `429`.
- Render verbs + the agent loop (cap/cancel) verified by compile + manual GUI pass (as with the rest of the UI).

---

## 11. Open questions

**Resolved by the round‑1 review (folded into the spec above):**
- ~~Multiple actions per reply~~ → run **all**, in order, each ack'd; capped at `maxActionsPerReply` (§5.1.1).
- ~~Round trip ownership~~ → app **auto‑resends** query results; **agent loop** bounded by `maxActionRounds`=3 (§5.1.1).
- ~~Rate‑limit / action cap~~ → **in v1** (per‑reply cap + round cap), not deferred.
- ~~Fenced‑block execution hazard~~ → execute only exact‑tag `analyser-action`; `-example` is inert (§5.1.2).
- ~~Origin handling~~ → reject **any** `Origin` header outright (§6).
- ~~`text` filter cost~~ → allowed, but reports `scan:"raw"`; index/flag filters stay O(index) (§4.1.1).

**Decided:**
- **Result‑back visibility** → **visible, clearly‑marked** transcript turn (§5.1.1).
- **Defaults** → in‑process **on**, REST **off**, `maxActionRounds` **3**, `maxActionsPerReply` **20**.

**Still open — future work:**
- **Index‑time text flags** (future, review #1c): configurable conditions (e.g. `failedValidation`)
  matched once at framing → index bits, making common anomaly counts genuinely O(index). Adds an
  index‑build config surface — worth it if `text` aggregates prove hot.
- **`aggregate` on `nodeLogs` numeric keys** (e.g. histogram `instanceId.key`): needs per‑record node‑log
  parsing — deferred beyond v1 (index‑only metrics first).

---

## 12. Roadmap — research → monitoring promotion (Grafana)

_Vision, not v1. Captured here because it reframes what the named `graph` verb (§4.3) and saved
`GraphSpec`s are **for**: they are the contract between two complementary systems._

**Two question types, two tools.** The analyser answers **unknown, one‑off questions** — *"why did this
happen?"* — exploratory, source‑linked, LLM‑assisted forensics. Grafana answers **known questions asked
continuously** — *"is the `failedValidation` rate normal right now?"* — dashboards, alerting, team
visibility. The workflow between them is a **promotion pipeline**: research a series in the analyser until
it proves diagnostic, then promote it to production monitoring. Every finding from the validation session
maps to such a promotion — `failedValidation` rate (alert on clusters), fill count (alert on
zero‑fills‑while‑quoting), modify‑message rate per side (would have caught the amplification bug
operationally), market‑data inter‑arrival gap (the 30‑second cadence). Those become *known* questions and
belong in Grafana, not repeated forensic sessions.

**Feed Grafana from the source, not by re‑parsing the log (Route B).** The audit file is one sink of the
`LogRecord` stream (`EventLogManager` takes a `LogRecordListener`). A **metrics sink alongside the file
sink** — publishing selected `instanceId.key` values as typed time‑series (Prometheus / InfluxDB line
protocol / Kafka) — gives Grafana clean structured data with **no log parsing**. Cardinality is naturally
bounded: the graph is static, so the set of `instanceId.key` pairs is known at build time. This is a
**Telamin‑side product piece** — a `serverplugin-metrics` / `serverplugin-grafana` next to the existing
kafka/jdbc/chronicle server plugins — *not* an analyser feature. (Route A — Loki/Alloy tailing the file +
LogQL regex extraction — re‑fights the exact battle the analyser's lenient parser already won against raw
Java `toString()`s, and loses the deliberate semantics: last‑occurrence‑per‑record, NaN‑means‑no‑point,
booleans→±1. Rejected.)

**The analyser is the authoring tool for the promotion — build this first.** It already persists named
saved graphs (`GraphSpec` = name + series of `instanceId.key`, style, extraction semantics). An
**`export_dashboard` action / File action** emits (a) the **metric allowlist** for the tap plugin and
(b) a **generated Grafana dashboard JSON** with matching panel definitions — turning *"we researched this
and liked the graph"* into a one‑click production artifact. The **named series definition is the contract**
between the two systems; the analyser is where it is discovered, named, and validated (which is exactly
why graphs are named and persisted, §4.3).

!!! warning "Superseded in part — see tracker M11.1 (2026-08-20)"

    The **allowlist** half stands. The **generated Grafana dashboard JSON** does not: it would have the
    analyser learning a versioned foreign schema, which contradicts the rule M29 and M31 later settled —
    *the analyser never learns a foreign format; the agent adapts it.*

    `export_dashboard` becomes **`export_promotion`**, emitting a **neutral manifest** (series, allowlist,
    thresholds, window, rationale, provenance). An agent renders that into Grafana — or Datadog, or
    Perses — and the manifest stays a checkable contract: every metric in the dashboard must appear in
    the allowlist. The paragraph above is otherwise unchanged, including the part that matters most:
    **the named series definition is the contract between the two systems.**

**The loop closes both ways.** A Grafana alert fires → on‑call opens the analyser on that log at that time
window → forensic session with the LLM → root cause → maybe a new series is promoted. Known‑question
monitoring feeds unknown‑question investigation feeds better monitoring.

**Boundary (keep the moat clean).** Resist making the analyser itself a **live dashboard** — follow‑mode
(round 8) is already at the edge of that, and real‑time visualisation is Grafana's job; duplicating it
spends effort on the half where the analyser has no differentiation. The analyser's moat stays the
**graph‑aware, source‑linked, LLM‑driven deep dive** that generic log tools cannot do, because they don't
know the log is the execution trace of a compiled graph with resolvable source.

---

## 13. Roadmap — the diagnose → fix → prove flywheel (dev‑environment)

_Vision, and deliberately **mostly out of the analyser**. Captured to fix the analyser's *one* role in it:
the handoff. The edit loop lives in the dev environment (Claude Code against the source repos) and in
Telamin's compiler/CI — **not** inside the analyser, which stays a curation tool that touches no code._

**Why the loop is defensible here when it isn't elsewhere: replay‑diff is the proof mechanism.** A
Fluxtion processor is deterministic — journal in, byte‑identical audit log out — so any behavioural change
from an edit is visible as a **record‑level diff** over a whole replayed session. That turns "LLM edits
market‑making code (trust the unit tests)" into "LLM proposes a change and **proves its exact behavioural
footprint**: these records changed, every other record in the production day is byte‑identical." No
conventional suite can utter that last clause; it's what makes a human comfortable approving an
LLM‑authored change to order logic.

**The flywheel** (each pass leaves the system more observable, tested, and explained):
audit log → LLM **diagnosis** (this analyser) → LLM **fix + LLM‑authored regression test from real data**
→ **replay‑proven** behavioural diff → **improved instrumentation** for the next round.

**What belongs in the analyser (the only near‑term build here):**
- [D1] **`export_finding` action** — the baton pass. Bundle the selected records, their byte anchors,
  the resolved source paths, and the **replay‑journal reference** into a single context block a dev‑env
  agent can pick up. Same `PromptBuilder` pattern already used for file‑access seeding — it's the
  investigation→fix handoff, not the fix itself.
- [D2] **`export_test_fixture` (record range → test oracle)** — an audit record *is* an exact statement
  of "given this event history, this node computed these values". So a journaled slice around a finding
  becomes a regression test **mechanically** (drive the slice through the processor / one node, assert the
  `nodeLogs`). Production incidents → tests with *real* event sequences, one click. (E.g. the locked‑book
  episode → "feed this exact tick sequence, assert the validator closes and both orders cancel".)
- [D3] **Diff classifies additive vs value changes** — instrumentation edits (new `nodeLogs` keys) shift
  the replay‑diff baseline, so `DiffBuilder` should report **new keys** separately from **changed values**.
  An instrumentation‑only PR then shows as **pure‑additive** — itself a verifiable property. (A concrete
  analyser‑side change to the existing §13/round‑7 diff.)

### 13.1 The `export_finding` handoff prompt (structure)

The output of `export_finding` is a **self‑contained task brief** a dev‑env agent (Claude Code against the
source repos) picks up — the investigation→fix baton. It is built by the **same `PromptBuilder` machinery**
as the LLM context (file‑access seeding, source resolution), re‑purposed from "explain" to "fix + prove".
It is a *proposal request*, never an instruction to merge. Sections, in order:

```
===== ANALYSER FINDING → FIX HANDOFF =====
Guardrail: PROPOSE a change + PROVE its behavioural footprint. Do NOT merge. A human reviews the
behavioural diff (replay), not just the code diff. This is venue-touching order logic.

## 1. Diagnosis (the analyst's conclusion)
<free text the investigating LLM/analyst wrote — the defect and why>
e.g. "Duplicate modify re-sends in the 00:26:19 burst: MakingOrderNode recomputes from a stale ack
because it has no in-flight-request ledger — MakingOrderNode.java:259."

## 2. Evidence (so you can re-verify, not just trust)
- Audit log: <localPath> (<size>, <count> records, <UTC span>)   # reuses §-file-access seeding
- Selected records (raw), each with its byte anchor:
    - byte 14,397,634 (len 421), logTime 2026-08-08T00:26:19.362Z
      <raw record text>
    ...
- Framing note (records split on ---, header/nodeLogs shape) so you can grep the log both directions.

## 3. Source targets (resolved via the EventProcessor)
- EventProcessor: <FQN>
- Implicated nodes (instanceId -> declared type @ file:line):
    makingOrderNode -> com...MakingOrderNode @ .../MakingOrderNode.java:259
    quoteValidator  -> com...QuoteValidator  @ .../QuoteValidator.java
- Source roots: <roots>   # for opening related classes / hierarchy

## 4. Replay reference (the PROOF input)
- Journal: <journal artifact ref>          # the INPUT event stream (not the audit log)
- Window to replay: 2026-08-08T00:25:49Z .. 00:26:25Z   (the burst)
- Baseline audit log for diff: <path or hash of the current replay output>

## 5. Task
1) Fix: <scoped ask> (e.g. add an in-flight-request ledger; ~10 lines).
2) Add a regression test from real data (see export_test_fixture / D2): drive the journaled slice
   through the processor/node, assert the nodeLogs.
3) If the change adds an event handler or node: AOT-regenerate (Telamin compiler) before proving.

## 6. Acceptance (how the change is proven)
- Replay the journal window; DIFF the audit log against the baseline.
- Expect: ONLY the targeted records change (the duplicate modifies disappear); EVERY OTHER record in
  the replayed session is byte-identical.  <- the claim no unit suite can make
- Unit test passes; CI gates = replay-diff + tests (mandatory).
```

**What the analyser supplies vs. references.** Sections 1–3 the analyser already has in hand (the
selection, byte anchors, `SourceService` resolution — §-file-access seeding + node‑type map). Section 4 is
a **reference**, because the analyser reads the **audit log** (output trace); the **journal** (input event
stream) is a *separate artifact* it does not load — so `export_finding` carries a pointer + window, and
**pairing journal ↔ audit‑log** (locating the journal for a given log/time‑window) is the one open
precondition gating this and D2. Sections 5–6 are boilerplate templated from the finding.

**What stays OUT of the analyser (dev‑env / Telamin, listed for completeness):** the LLM fix itself;
running replay + audit diff as a **mandatory CI gate**; the **AOT regeneration** step when an edit adds an
event handler or node (changes the graph → Telamin's compiler regenerates → retest → replay‑diff, as a
first‑class CI stage); and the hard **guardrail — propose → prove → human approves, never autonomous
merge**, with the human reviewing the **diff of behaviour**, not just the diff of code.

**Two extensions worth naming** (both dev‑env, both enabled by the analyser's handoff):
- *Audit‑driven instrumentation is a self‑improving investigator* — the investigating LLM knows exactly
  which `instanceId.key`s it wished existed (it just spent a session wanting them, e.g. a sizing node that
  transformed `170.14 → 0.01` without logging its reasoning). Letting it add those `auditLog.info(...)`
  lines means the next investigation doesn't hit the same wall — observability improves where
  investigations actually go, not where a developer guessed.
- *Journaled sequences are test oracles* (D2) — real production event sequences as fixtures, not synthetic.
