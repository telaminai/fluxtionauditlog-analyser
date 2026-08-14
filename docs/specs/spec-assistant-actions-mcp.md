# Assistant Actions — MCP Transport (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-12

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

- **Framing (decided):** **newline-delimited JSON-RPC** on stdin/stdout (one JSON object per line) — *not*
  LSP-style `Content-Length` headers. Fixed now so no one implements the wrong framing; revisit only if a
  target client demands headers.
- **Methods handled:** `initialize` → **echo a mutually-supported `protocolVersion`** (negotiate: return
  the client's version if supported, else the newest we support) + capabilities `{tools:{}}`; `tools/list`
  → the five tool schemas (§4); `tools/call` → forward to REST, wrap the result (§6). Everything else → a
  JSON-RPC method-not-found (`-32601`).
- **No MCP SDK dependency.** The bridge is a separate artifact (or a `--mcp` launch mode of the same jar),
  so the **main analyser stays dependency-light**; even if a future maintainer prefers the SDK, it never
  touches the core.

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

One MCP tool per verb, each with a JSON-Schema `inputSchema` derived from the action params
(spec-assistant-actions §4). Names are prefixed to avoid collisions in a client with many servers.

| MCP tool | Kind | inputSchema (summary) |
|---|---|---|
| `analyser_aggregate` | query (read-only) | `{metric, groupBy, filter?: {dimensions[], from, to, text}, limit?}` |
| `analyser_filter` | render | `{from?, to?, dimensions?[], text?}` |
| `analyser_graph` | render | `{name?, series?[], style?, newTab?, rename?}` |
| `analyser_goto` | render | `{byteOffset?, recordIndex?}` |
| `analyser_flag` | render | `{byteOffsets?[], recordIndexes?[], note?}` |

- `tools/list` returns these with full schemas — this **replaces the prompt manifest** for MCP clients
  (discovery is native). The in-process/REST manifests are unaffected.
- Each `tools/call` forwards `{action: <verb>, params: <arguments>}` to REST `/action` (bridge) or the
  dispatcher (in-app), and wraps the `ActionResult` (§6).
- **Annotations:** mark `analyser_aggregate` `readOnlyHint: true`; the render verbs mutate UI state but are
  reversible — `readOnlyHint: false`, `destructiveHint: false` (nothing is deleted; flags/graphs/filters
  are undoable). This lets a client surface "this will change the app view" appropriately.

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
- transport failures (app not running, REST off) → a JSON-RPC error on the *call*, with the "enable REST"
  hint.

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
  stdio loop (`initialize` / `tools/list` / `tools/call`) using `llm.Json`; discovers the endpoint via the
  well-known file; forwards `tools/call` to REST `/action` with the token header; wraps results (§6).
  **Headless-safe:** the very first line sets `System.setProperty("java.awt.headless","true")` and the
  bridge path **touches no Swing/AWT class** — an MCP client launches this in an arbitrary environment, and
  initializing AWT (esp. on macOS) can fail oddly. `--mcp` short-circuits `Main` *before* any UI bootstrap.
- `net/RestEndpointFile` — write on REST start / delete on stop (in `ActionServer` or `MainFrame`), read by
  the bridge (with the pid liveness check, §3). `600` perms.
- `mcp/McpToolSchemas` — the five tool `inputSchema`s. **Single source of truth:** derive both the REST
  `/manifest` body and these MCP `inputSchema`s from **one** schema definition (a small per-verb descriptor)
  so a future verb-param change can't fork the two transports. Pure data; unit-testable (a test asserts the
  verb set matches the dispatcher's).
- _(option B, later)_ `net/McpHttpServer` — Streamable-HTTP MCP hosted in-app over the dispatcher.

No change to `ActionDispatcher` / `RenderExecutor` / `AggregateService` — MCP rides the existing seam.

---

## 10. Delivery slices

1. **`RestEndpointFile`** — the app publishes its live REST endpoint+token to `~/.fluxtion-analyser/rest-endpoint`
   on start, removes it on stop/exit. (Headless-testable.)
2. **`McpToolSchemas` + `McpBridge` handshake/list** — `initialize`, `tools/list` return the five schemas;
   hand-rolled JSON-RPC over stdio. (Testable by feeding JSON-RPC frames to the loop.)
3. **`tools/call` → REST forward** — map a tool call to `{action, params}`, POST to `/action`, wrap the
   result/error (§6). End-to-end against a live `ActionServer` in a test.
4. **Docs** — a short "connect an MCP client" section (help/README), client config snippet, tracker close-out.
5. _(later)_ **Resources/prompts** and/or **option B** HTTP-MCP.

Slice 1–2 are pure/headless; slice 3 reuses the `ActionServerTest` harness (real loopback HTTP).

---

## 11. Testing

- `RestEndpointFileTest` — write/read/round-trip; delete on stop; perms best-effort.
- `McpToolSchemasTest` — every verb present; schemas are valid JSON with the expected fields.
- `McpBridgeTest` — feed `initialize` → assert capabilities; `tools/list` → assert five tools;
  `tools/call analyser_aggregate` against a live `ActionServer` → assert a wrapped result;
  `tools/call` with a bad param → `isError:true` carrying the dispatcher's message; unknown method →
  JSON-RPC `-32601`.
- Manual: register the bridge in a real MCP client, run an aggregate + a graph build.

---

## 12. Open questions

**Resolved (folded into the spec):**
- ~~Framing~~ → **newline-delimited JSON-RPC** (§2); `initialize` negotiates `protocolVersion`.
- ~~`--mcp` environment~~ → **headless-safe**, no Swing/AWT on the bridge path (§9).
- ~~Stale endpoint file after a crash~~ → **pid liveness check** before erroring (§3).
- ~~Schema drift REST vs MCP~~ → **one source of truth** feeds `/manifest` and the MCP `inputSchema`s (§9).

**Still open:**
- **Multiple running analysers:** the endpoint file holds one endpoint; if two apps run, last-writer-wins.
  Encode the pid and let the bridge pick / error, or use per-pid files + a selector. (Deferred; single
  instance is the norm.)
- **SDK vs hand-roll:** hand-roll keeps the ethos and is small, but an official MCP SDK would track spec
  evolution for free. Revisit if MCP's surface we use grows beyond tools.
- **Resources/prompts:** worth it only if a concrete client workflow pulls context via MCP rather than the
  existing prompt seeding.
