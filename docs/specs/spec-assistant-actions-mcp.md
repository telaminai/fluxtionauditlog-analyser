# Assistant Actions — MCP Transport (Design Spec)

Status: DRAFT v1.2 · Owner: greg.higgins · Last updated: 2026-08-15

> **v1.1 — reconciled to shipped code.** Written before the assistant-vocabulary round landed; now
> updated: **six verbs** (the `read` verb shipped, AV.1), current `graph`/`goto` params (`exprs`,
> `from/to`, `rationale` — AV.2; `reveal` — AV.4), and the schema single-source-of-truth **already
> exists**: `llm.VerbSchemas` (AV.3), backing REST `/manifest`, with `VerbSchemasTest`. The bridge
> **reuses it** — it does not create a parallel schema holder.
>
> **v1.2 — reconciled to MCP itself, which moved (M13.2).** MCP's current revision **`2026-07-28`**
> **removed the `initialize` handshake** this spec was written against. Versions now ride on every
> request in `_meta`, servers **MUST** implement `server/discover`, results carry `resultType`, and a
> version mismatch is `UnsupportedProtocolVersionError` (**-32022**). The handshake era
> (**`2025-11-25` and earlier**) is now formally "legacy". Decision: the bridge is **dual-era** — it
> answers both, which the MCP spec explicitly sanctions, and is the only posture that works for every
> client era. See §2.1. Also fixed here: the notification rule (§2.2) and the rate-limit case in the
> error mapping (§6).

Companion to **[spec-assistant-actions.md](completed/spec-assistant-actions.md)** (the action schema + the
in-process and REST transports) and **[tracker.md](tracker.md)** (milestone **M13**).

Adds **MCP (Model Context Protocol)** as a **third transport** over the *same* `ActionDispatcher` /
`RenderExecutor` seam. This is the natural door for MCP-native clients (Claude Code / Claude Desktop):
native structured tool-calls instead of fenced-block parsing, protocol-level discovery instead of a prompt
manifest, and a transport those clients already speak. It is an **addition, not a replacement** — see
spec-assistant-actions §5.3 for why the in-process and REST doors still exist.

> **Reframe holds:** the artifact is the action *schema*; MCP is plumbing. Every MCP tool maps 1:1 onto a
> verb the dispatcher already validates and executes. No new backend logic — a protocol adapter.

---

## 1. The core constraint that shapes the design

The server must drive the **running GUI** — the loaded log, the graph tabs, the table, the flag store all
live in one already-running Swing process. This rules out the usual MCP shape (a stdio subprocess the
client launches on demand), because a fresh subprocess holds none of that state. So an MCP transport must
either be **hosted inside the running app** or be a **thin adapter that forwards to it**. Two options:

| Option | Shape | Pros | Cons |
|---|---|---|---|
| **A — stdio bridge** (recommended first) | a tiny process the MCP client launches; speaks MCP on stdio; **forwards `tools/call` to the app's REST `/action`** (slice 4) | works with the common **stdio-only** MCP clients; reuses the whole REST backend; keeps MCP code out of the main app | needs the app's REST transport **on**; a hop |
| **B — in-app HTTP MCP server** | the app hosts MCP over **Streamable HTTP** on loopback (like `ActionServer`) | no bridge process; single hop | fewer clients speak HTTP-MCP today; more code in the app |

**Recommendation:** ship **A** first — it reuses slice 4 entirely and targets the clients people actually
run. Add **B** later if a target client needs direct HTTP-MCP.

---

## 2. Preserve the near-zero-dep ethos — hand-roll minimal MCP

MCP is **JSON-RPC 2.0** over a stream. Its useful core is small: the `initialize` / `initialized`
handshake, `tools/list`, and `tools/call` (optionally `resources/*`, `prompts/*`). Rather than pull an MCP
SDK (and its transitive deps) into the project, the bridge **hand-rolls** these few methods using the
existing `llm.Json` codec — the same choice already made for JSON, HTTP, YAML colouring, and charting.

- **Framing (decided, and confirmed still current in `2026-07-28`):** **newline-delimited JSON-RPC** on
  stdin/stdout (one JSON object per line, no embedded newlines) — *not* LSP-style `Content-Length`
  headers. MCP also requires that **nothing but MCP messages** goes to stdout, so all bridge diagnostics
  go to **stderr** (the protocol's own logging feature is deprecated in favour of exactly this).
- **Methods handled:** `initialize` (legacy), `server/discover` (modern), `tools/list`, and `tools/call`
  → forward to REST, wrap the result (§6). Unknown methods → `-32601`, subject to §2.2.
- **No MCP SDK dependency.** The bridge is a `--mcp` launch mode of the same jar, so the **main analyser
  stays dependency-light**; even if a future maintainer prefers the SDK, it never touches the core.

### 2.1 Dual-era: the handshake became optional, then went away

MCP split into two eras, and the bridge answers **both**. The era is decided per request by how the
client opens — an `initialize` is legacy; a request carrying `_meta.io.modelcontextprotocol/protocolVersion`
is modern:

| | **Legacy** (`2025-11-25` and earlier) | **Modern** (`2026-07-28`) |
|---|---|---|
| Opening | `initialize` handshake + `notifications/initialized` | none — stateless, per-request |
| Version | negotiated once: echo the client's if supported, else our newest | declared per request in `_meta`; mismatch → **-32022** with a `supported` list |
| Discovery | `tools/list` after the handshake | `server/discover` (**mandatory**) + `tools/list` |
| Results | bare result object | `resultType:"complete"`; list results also carry `ttlMs` + `cacheScope` |

**We advertise `["2026-07-28", "2025-11-25", "2025-06-18"]`.** Dual-era is the only row in MCP's own
compatibility matrix that works for *both* client eras; a legacy-only server hard-fails a modern client,
which has no fall-forward mechanism. Legacy results are emitted **without** the modern-only fields, so an
old client never sees a shape its schema rejects.

### 2.2 Notifications are never answered

A JSON-RPC **notification has no `id`, and a response to one is a protocol violation** — so "everything
else → `-32601`" is wrong as stated. The rule is:

- **no `id`** → handle silently, emit nothing (`notifications/initialized` arrives on every legacy
  connection; answering it with `-32601` breaks the handshake);
- **unknown method *with* an `id`** → `-32601`.

---

## 3. The per-run token problem, and its fix

REST uses a **per-run** endpoint (ephemeral port) and **per-run** token — they change every app launch, so
a static MCP client config can't hard-code them. Fix: when the REST transport starts, the app writes its
current endpoint to a well-known file:

```
~/.fluxtion-analyser/rest-endpoint      (mode 600)
{ "url": "http://127.0.0.1:53411", "token": "…", "pid": 12345, "startedAt": "…" }
```

The stdio bridge **reads this file at startup** (and re-reads on connection error) to discover the live
endpoint + token. **Crash safety:** delete-on-stop won't run if the app is killed, leaving a stale file —
so the bridge does a **pid liveness check** (`ProcessHandle.of(pid).isPresent()`, the portable `kill -0`)
before trusting it, giving a clean "analyser not running" message instead of connection-refused noise. The
MCP client config is then stable:

```jsonc
// e.g. an MCP client's servers config
"fluxtion-analyser": { "command": "java", "args": ["-jar", "analyser.jar", "--mcp"] }
```

The bridge fails cleanly ("analyser not running, or REST transport disabled — enable it in Settings →
Assistant") when the file is absent. The endpoint file is deleted on a clean REST stop / app exit.

---

## 4. Tool mapping (verbs → MCP tools)

One MCP tool per verb. The `inputSchema`s are **not defined here** — they come from the shipped
`llm.VerbSchemas` (single source of truth, already backing REST `/manifest`; `VerbSchemasTest` pins
the verb set to the dispatcher's). The table below is an illustrative summary of the **current** six:

| MCP tool | Kind | inputSchema (summary — canonical in `VerbSchemas`) |
|---|---|---|
| `analyser_aggregate` | query (read-only) | `{metric, groupBy, filter?: {dimensions[], from, to, text}, limit?}` |
| `analyser_read` | query (read-only) | `{byteOffset? / recordIndex?, before?, count?}` — raw text of N records around an anchor (rate-limited) |
| `analyser_filter` | render | `{from?, to?, dimensions?[], text?}` |
| `analyser_graph` | render | `{name?, series?[], exprs?[{expr, label?, resolve?}], style?, newTab?, from?, to?, rename?, rationale?}` |
| `analyser_goto` | render | `{byteOffset?, recordIndex?, reveal?}` |
| `analyser_flag` | render | `{byteOffsets?[], recordIndexes?[], note?}` |

- `tools/list` returns these with full schemas — this **replaces the prompt manifest** for MCP clients
  (discovery is native). The in-process/REST manifests are unaffected.
- Each `tools/call` forwards `{action: <verb>, params: <arguments>}` to REST `/action` (bridge) or the
  dispatcher (in-app), and wraps the `ActionResult` (§6).
- **Annotations:** mark `analyser_aggregate` and `analyser_read` `readOnlyHint: true`; the render verbs
  mutate UI state but are reversible — `readOnlyHint: false`, `destructiveHint: false` (nothing is
  deleted; flags/graphs/filters are undoable). This lets a client surface "this will change the app
  view" appropriately.
- **A new verb costs zero MCP work by construction**: the bridge enumerates `VerbSchemas` at
  `tools/list` time — adding a verb to the dispatcher + schemas automatically publishes the tool.

---

## 5. Resources & prompts (optional, later)

MCP also has **resources** (read-only data the client can fetch) and **prompts** (reusable templates).
Natural, but overlapping with the file-access seeding — deferred past the tools:

- **Resources:** `analyser://log` (path + shape), `analyser://selection` (current selected records),
  `analyser://node-types` (instanceId → type map), `analyser://source/<fqn>`. These let a client pull
  context on demand instead of it being pushed in the prompt.
- **Prompts:** an `explain-record` prompt template (the current default question + context assembly).

Tools first; resources/prompts only if a client workflow wants them.

---

## 6. Error mapping

The dispatcher never throws — it returns a structured `ok:false` (spec-assistant-actions §3, review #3).
MCP `tools/call` maps that cleanly:

- `ok:true`  → tool result `content: [{type:"text", text: <result JSON>}]`, `isError:false`.
- `ok:false` → tool result `content: [{type:"text", text: <error message>}]`, **`isError:true`** — so the
  model gets the same actionable feedback it gets in-process, in MCP's native error shape.
- **any HTTP status with an `ok:false` body → `isError:true`** carrying that error text. This covers the
  cases the list above missed: `401` (bad token), `403` (`Origin` present) and especially **`429` rate
  limited** — `ActionServer` runs a 10/s token bucket, so a runaway agent *will* meet it, and it must read
  as a retryable tool error rather than a dead transport.
- transport failures (app not running, REST off, endpoint file stale) → a JSON-RPC error on the *call*,
  with the "enable REST" hint. Only an absent/dead endpoint is a transport failure.

---

## 7. Security posture

- **stdio bridge:** the client launches it locally; the stdin/stdout channel is inherently local and
  client-authenticated — no token/Origin needed *on that channel*. The **bridge→REST hop carries the
  per-run token** and is loopback-only, so the REST guards (token, reject-Origin, rate-limit) still apply
  underneath. The endpoint file is `600`.
- **in-app HTTP MCP (option B):** identical guards to `ActionServer` — loopback bind, token, reject any
  `Origin`, rate limit.
- **Verb whitelist unchanged:** read-only *over the log*; no config/file/venue mutation; render verbs
  reversible. MCP does not widen the blast radius.

---

## 8. Coexistence with the existing transports

All three doors share one `ActionDispatcher` + `RenderExecutor`:

```
in-process loop  ─┐
REST /action     ─┼──►  ActionDispatcher ──► AggregateService (query)
MCP tools/call   ─┘                        └► RenderExecutor  (filter/graph/goto/flag, EDT-marshalled)
```

- In-process stays the only path that can drive the app's **own** chat.
- REST stays the **universal, zero-config** door (any client that can `curl`; endpoint inline in the
  copy-prompt).
- MCP is the **preferred door for MCP-native agents** — and, via option A, is *implemented on top of* REST.

Config: `assistantActionsMcp` is not a separate runtime toggle for option A (the bridge is a client-side
process); it only requires `assistantActionsRest = true` so the endpoint file exists. Option B would add
an `assistantActionsMcpHttp` toggle mirroring the REST one.

---

## 9. Components (new)

- `mcp/McpBridge` — a `main(String[])` (launched via `analyser.jar --mcp`): a hand-rolled JSON-RPC 2.0
  stdio loop (`initialize` / `server/discover` / `tools/list` / `tools/call`) using `llm.Json`; discovers
  the endpoint via the well-known file; forwards `tools/call` to REST `/action` with the token header;
  wraps results (§6). **Watch `llm.Json`:** it parses every number to `Double` and writes it back as
  `1.0`, so a JSON-RPC `id` echoed straight through stops matching a strict client's request — the bridge
  narrows integral doubles back to `long` on its write path rather than changing the shared codec.
  **Headless-safe:** the very first line sets `System.setProperty("java.awt.headless","true")` and the
  bridge path **touches no Swing/AWT class** — an MCP client launches this in an arbitrary environment, and
  initializing AWT (esp. on macOS) can fail oddly. `--mcp` short-circuits `Main` *before* any UI bootstrap.
- `net/RestEndpointFile` — write on REST start / delete on stop (in `ActionServer` or `MainFrame`), read by
  the bridge (with the pid liveness check, §3). `600` perms.
- ~~`mcp/McpToolSchemas`~~ **not built — the single source of truth already shipped**: `llm.VerbSchemas`
  (AV.3) defines every verb's params/types/enums/required and backs REST `/manifest`;
  `VerbSchemasTest` asserts the verb set matches the dispatcher. The bridge adds only a thin
  **`mcp/McpTools` adapter** that maps `VerbSchemas` entries to MCP tool descriptors
  (`analyser_<verb>` name, description, `inputSchema`, read-only annotations). Creating a parallel
  schema holder here would fork the transports — the exact drift this section exists to prevent.
- _(option B, later)_ `net/McpHttpServer` — Streamable-HTTP MCP hosted in-app over the dispatcher.

No change to `ActionDispatcher` / `RenderExecutor` / `AggregateService` — MCP rides the existing seam.

---

## 10. Delivery slices

1. **`RestEndpointFile`** — the app publishes its live REST endpoint+token to `~/.fluxtion-analyser/rest-endpoint`
   on start, removes it on stop/exit. (Headless-testable.)
2. **`McpTools` adapter + `McpBridge` handshake/list** — `initialize`, `tools/list` return one tool per
   `VerbSchemas` verb (six today); hand-rolled JSON-RPC over stdio. (Testable by feeding JSON-RPC
   frames to the loop.)
3. **`tools/call` → REST forward** — map a tool call to `{action, params}`, POST to `/action`, wrap the
   result/error (§6). End-to-end against a live `ActionServer` in a test.
4. **Docs** — a short "connect an MCP client" section (help/README), client config snippet, tracker close-out.
5. _(later)_ **Resources/prompts** and/or **option B** HTTP-MCP.

Slice 1–2 are pure/headless; slice 3 reuses the `ActionServerTest` harness (real loopback HTTP).

---

## 11. Testing

- `RestEndpointFileTest` — write/read/round-trip; delete on stop; perms best-effort.
- `McpToolsTest` — the adapter exposes **exactly** the `VerbSchemas` verb set (no more, no fewer);
  descriptors are valid MCP tool JSON; read-only annotations on `aggregate`/`read` only.
- `McpBridgeTest` — feed `initialize` → assert capabilities; `tools/list` → assert one tool per verb;
  `tools/call analyser_aggregate` against a live `ActionServer` → assert a wrapped result;
  `tools/call` with a bad param → `isError:true` carrying the dispatcher's message; unknown method →
  JSON-RPC `-32601`.
- Manual: register the bridge in a real MCP client, run an aggregate + a graph build.

---

## 12. Open questions

**Resolved (folded into the spec):**
- ~~Framing~~ → **newline-delimited JSON-RPC** (§2), re-confirmed against `2026-07-28`.
- ~~Which era to speak~~ → **dual-era** (§2.1): legacy `initialize` negotiation *and* modern per-request
  `_meta` + `server/discover`.
- ~~Are unknown notifications `-32601`?~~ → **no**: never answer a message without an `id` (§2.2).
- ~~`--mcp` environment~~ → **headless-safe**, no Swing/AWT on the bridge path (§9).
- ~~Stale endpoint file after a crash~~ → **pid liveness check** before erroring (§3).
- ~~Schema drift REST vs MCP~~ → **one source of truth** feeds `/manifest` and the MCP `inputSchema`s —
  and it **already ships** as `llm.VerbSchemas` (AV.3); the bridge only adapts it (§9).

**Still open:**
- **Multiple running analysers:** the endpoint file holds one endpoint; if two apps run, last-writer-wins.
  Encode the pid and let the bridge pick / error, or use per-pid files + a selector. (Deferred; single
  instance is the norm.)
- **SDK vs hand-roll:** hand-roll keeps the ethos and is small, but an official MCP SDK would track spec
  evolution for free. **Sharper after v1.2:** MCP made a breaking change (`2026-07-28`) within months of
  this spec being written, and we only caught it by reading the live spec during M13.2. The hand-roll is
  still tiny — dual-era cost ~60 lines — but the maintenance signal is real. Revisit if a third era lands
  or if our surface grows beyond tools. Mitigation meanwhile: the supported-version list is one constant.
- **Resources/prompts:** worth it only if a concrete client workflow pulls context via MCP rather than the
  existing prompt seeding.
