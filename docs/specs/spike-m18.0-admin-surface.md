# Spike M18.0 — verify the Mongoose admin surface

Status: **DONE — O1 resolved** · 2026-08-15 · Gates: all of `spec-closed-loop.md` Part B

**Verdict: the three "gaps" M18.0 was created to chase are all closed already.** The admin surface is
substantially richer than [spec-closed-loop §B.2](spec-closed-loop.md) describes, no
`fluxtion-server-plugins` PR is needed to unblock M18.1–M18.3, and **M18.2's design must change** — log
discovery is an API call, not a file path resolved from config.

One finding is bigger than the spike's remit and needs a product decision, not an engineering one: see
[§5 Strategic overlap](#5-strategic-overlap-read-this-one).

## 1. How this was verified — and its limit

By reading source in the sibling checkouts (`telaminai/mongoose`, `telaminai/mongoose-plugins`) rather
than the published docs, which are behind.

> **The local `mongoose-plugins` checkout is stale.** It sits on `develop` at `eeccf1b` with only
> `svc-admin-rest` / `svc-admin-telnet`. **`origin/develop` has far more** — `svc-admin-web`,
> `svc-loader-sink`, `svc-loader-feed`, `svc-micrometer`. Everything below is read from `origin/develop`.
> Anyone re-checking this must `git fetch` first or they will reproduce the spec's stale conclusions.

**Limit, stated plainly: no endpoint here was exercised against a running server.** The routes are
registered in source; their behaviour, auth interaction and payload shapes are not confirmed. The spike's
intended test bench — the M19 example bundle — does not exist yet. Treat the API shapes as
*verified to exist*, not *verified to work*. M18.1 should re-confirm against a live server as its first act.

## 2. What the admin surface actually serves

`svc-admin-web` (`WebAdminService`, `origin/develop`) registers:

| Area | Endpoints |
|---|---|
| identity / health | `GET /api/server` · `/api/version` · `/api/jvm` · `/healthz` · `WS /ws/monitor` |
| inventory | `GET /api/services` · `/api/agents` · `/api/queues` · `/api/pipes` · `/api/config` |
| **audit files** | `GET /api/audit/files` · `/api/audit/file/{id}` · `/{id}/metadata` · **`/{id}/export`** |
| **audit capture** | `POST /api/audit/{processor}/start` · `/stop` · **`WS /ws/audit-tail/{processor}`** |
| **audit level** | **`POST /api/processors/{group}/{name}/audit/level`** |
| processor internals | `GET /api/processors/{group}/{name}/graphml` · `/compliance` |
| source | `GET /api/source?fqn=<FQN>` |
| generic commands | `GET /api/commands` · `POST /api/commands/{name}` |
| session | `POST /api/session/login` · `/logout` |
| misc | `GET /api/files` · `POST /api/loader/upload` |

## 3. The three gaps, answered

### 3.1 Audit level — **CLOSED**, and not where the spec looked

`POST /api/processors/{group}/{name}/audit/level` → `WebAdminService.handleSetAuditLogLevel` →
`match.eventProcessor().setAuditLogLevel(level)` (`WebAdminService.java:270, 670, 725`), validating
against `EventLogControlEvent.LogLevel`.

The spec predicted this would need a registered `AdminCommandRegistry` command. It is a **dedicated REST
endpoint** — simpler, typed, per-processor. **M18.3 is unblocked** and needs no server-side work.

> Note for M18.3's capture-and-restore: this is a **setter only**. Nothing found *reads* the current
> level back, so "record the level we found, restore it on exit" has nothing to read. Either the
> analyser tracks only levels it set itself (and restores to a user-declared baseline), or a `GET`
> companion is the one small server-side ask worth filing.

### 3.2 Log discovery — **CLOSED**, and M18.2's design must change

The audit capture plugin (designed in `fluxtion-web/docs/audit-log-viewer-plugin-mongoose/README.md`,
2026-05-23) ships `MongooseAuditIntrospectionService` — a read-only catalog returning `AuditSinkHandle`
records: `{ id, processorName, path, cycle, sizeBytes, recordCount, startedAt, lastWriteAt }`. Exposed as
`GET /api/audit/files` and `/api/audit/file/{id}/metadata`.

**That is exactly the "sink descriptor (type + path)" §B.2 asked for** — including the path, size and
record count the Open dialog wants.

**But the on-disk format is Chronicle, not text.** Decided 2026-05-23: *"Chronicle BinaryWire on disk +
JSON/YAML projections via the `/export` endpoint"*. So:

- **`GET /api/audit/file/{id}/export?format=yaml` returns the `---`-separated `eventLogRecord:` documents
  the analyser already parses.** The plugin README calls this out explicitly as the desktop hand-off path.
- `format=jsonl` exists for `jq`/Splunk/Loki.

So M18.2 becomes: **list via `/api/audit/files`, fetch via `/export?format=yaml`, open**. Not "resolve a
file path from server config and open it locally" — the file on disk is Chronicle and the analyser cannot
read it. The spec's "degrade honestly when the sink isn't one we read" branch is **no longer needed for
Chronicle**, because the server projects on demand.

### 3.3 Lifecycle — **STILL A GAP** (the only one)

`MongooseServerAdmin.start()` (`mongoose`, `MongooseServerAdmin.java:73-79`) registers:

```java
registry.registerCommand("server.service.list",     this::listServices);
// registry.registerCommand("server.service.start", this::startServices);   // COMMENTED OUT
// registry.registerCommand("server.service.stop",  this::stopServices);    // COMMENTED OUT
registry.registerCommand("server.processors.list",  this::listProcessors);
registry.registerCommand("server.processors.stop",  this::stopProcessors);
```

The `startServices`/`stopServices` methods exist and `MongooseServerController` exposes
`startService` / `stopService` / `stopProcessor` — but the commands are **commented out**, and there is
**no restart anywhere**. Audit *capture* start/stop is served
(`POST /api/audit/{processor}/start|stop`); that is not server lifecycle.

→ **M18.4 stays blocked.** It is a one-line uncomment plus a restart verb — a small, well-scoped
`mongoose` PR. Worth confirming *why* they were commented out before assuming it is safe to re-enable.

## 4. Corrections to spec-closed-loop §B.2

1. The B.2 endpoint table is **incomplete** — it predates the audit endpoints, `/api/source`,
   `/api/processors/.../graphml`, `/api/services/{name}/config` and `/api/version`.
2. "Audit-sink path, audit level and lifecycle are **not** REST endpoints" — **wrong now** for the first
   two; right only for lifecycle.
3. "`WS /ws/logs` is application logging, not the event-audit sink" — **still correct and still
   important**, but the reason is sharper than stated: Mongoose's *default* `LogRecordListener` is
   `logRecord -> log.info(logRecord.toString())` (`MongooseServer.java:114`), i.e. by default audit
   records **are** dumped into the application log. The dedicated audit sink only exists when the capture
   plugin is wired. `WS /ws/audit-tail/{processor}` — not `/ws/logs` — is the audit stream.
4. **The audit sink is not declarative config in core Mongoose.** `logRecordListener` is a static field
   passed to `MongooseServer.bootServer(...)` in application code; it appears nowhere under
   `config/`, and no `LogRecordListener` implementation exists in `mongoose-plugins`. The pluggable
   *typed sink* the spec imagines is provided by the **audit-capture plugin**, not by the core config
   model. (`svc-loader-sink` is a different seam entirely — `EventSinkConfig`, event output, not audit.)

## 5. Strategic overlap — read this one

The audit-capture plugin's Phase 2 is **a web-based audit log viewer with graph replay**, inside
`svc-admin-web`: it ports `replay-engine.js` and `eventlog-parser.js` from `fluxtion-visualiser` into a
"Replay" sibling tab on the processor GraphML view — scrub events, see which nodes fired, inspect state,
replay forwards and backwards.

That overlaps this analyser's core job: read audit records, see the graph, understand a cycle.

Where the analyser still stands alone: **multi-GB index-first browsing**, **formula graphing**,
**click-to-source over your own repo**, and the **LLM assistant + the MCP/REST action socket** (M13).
Where the web viewer wins: zero install, already inside the ops surface, live tail, node-level replay.

**This is a positioning question, not an engineering one, and it is above the spike's pay grade.** It
should be settled before M18.2–18.4 are scheduled, because "the analyser opens the server's log" reads
very differently if the server already ships its own viewer. Three coherent answers, none obviously right:

- **Complement** — the web viewer is the ops-surface glance; the analyser is the deep-dive. M18.2 becomes
  the deliberate hand-off (the plugin README already frames `/export?format=yaml` as exactly that).
- **Converge** — the analyser becomes the desktop client of the same API and stops caring where the log
  came from.
- **Diverge** — the analyser leans into what the web tab cannot do (scale, source, agents) and drops the
  control-plane ambition, shrinking M18 to link + discovery.

## 6. Recommended changes to the M18 plan

| Slice | Was | Now |
|---|---|---|
| M18.1 link + status | as specced | **unchanged, ships** — `GET /api/server` + `/api/version`; add auth for `/api/session/login` (`authMode` may not be `NONE`) |
| M18.2 log discovery | resolve sink path from config; degrade if not a file sink | **redesign** — `GET /api/audit/files` → pick → `GET /api/audit/file/{id}/export?format=yaml` → open. Consider `WS /ws/audit-tail/{processor}` for Follow |
| M18.3 audit level | via registry command, capture-and-restore | **unblocked**; capture-and-restore needs a `GET` companion or a declared baseline (§3.1) |
| M18.4 dev restart | via registry command | **still blocked** — needs a small `mongoose` PR (uncomment service start/stop, add restart) |
| _new_ | — | **`/api/source?fqn=` and `/api/processors/.../graphml` are unplanned free wins** — the server can resolve source and hand over the processor graph, both of which the analyser currently infers locally. Worth a follow-up item |

**Do not schedule M18.2–18.4 until §5 is settled.** M18.1 is safe to start regardless.
