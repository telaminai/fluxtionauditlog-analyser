# Fluxtion Audit Log Analyser — Work Tracker

Companion to **[spec.md](spec.md)**. Status keys: ☐ todo · ◧ in‑progress · ☑ done · ⊘ dropped.

Legend for each item: **[id] status — title** · _acceptance_.

---

## Shipped — archived

Fully-delivered milestones and refinement rounds live in **[completed/tracker.md](completed/tracker.md)**:
M0 setup · M1 parser & index · M2 table + detail · M3 filters & summary · M4 source · M5 LLM ·
M6 graphing · M7 large-file mode · M8 polish/help · M9 UX pass · M10 assistant actions ·
M14 graph artifacts · M15 settings export/import · M16 release & distribution · refinement rounds 2–12.

---
## M17 · Documentation site — GitHub Pages — ◐ IN PROGRESS (authored + screenshots; needs Pages enabled)
_Design: **[../admin/docs-site.md](../admin/docs-site.md)**. Jekyll + Just the Docs (search, nav,
dark mode) built from `docs/site/` by an Actions workflow; landing page with JBang/fatjar run-it-now,
user guide, log-format explainer, FAQ; release-notes page injected from CHANGELOG.md at build so site,
GitHub release and in-app notes are one source._
- [D17.1] ◐ **Skeleton** — `_config.yml` + `index` + `install` + `pages.yml` + `404` authored. ☐ one-time
  repo step: Settings → Pages → Source = **GitHub Actions**, then push to publish.
- [D17.2] ☑ **User guide pages** — index + graphs + assistant + source-navigation + sharing-setups
  written; **light** hero (landing) + **dark** (user guide) screenshots captured from a real 30k-record log.
- [D17.3] ☑ **Log format + FAQ**; changelog injection in `pages.yml`; README + in-app help link to the
  site.

## Refinements (round 13) — ◐ IN PROGRESS (UI polish backlog)
User-driven UI polish, gathered live. Status kept current here.
- [R13.1] ☑ **Chart gridlines** — more contrast in both light and dark themes.
- [R13.2] ☑ **Search box** — vertical padding below it (above the Records table).
- [R13.3] ☑ **Source view** — Wrap checkbox, default **no-wrap** (shared `WrapTextPane` extracted from
  DetailPanel and reused).
- [R13.4] ☑ **Record detail** — **Copy** button copies the shown record(s) to the clipboard.
- [R13.5] ☑ **Record detail** — clicking the event / `eventToString` line navigates the Source view to
  the processor's event-handler method (`showDispatchFor`).
- [R13.6] ☑ **Graph without source** — the detail right-click now offers **Add to graph** anywhere on
  a node line (every graphable key of that node, from the parsed record), not only when the click lands
  exactly on a key token. Graphing never depended on source; this removes the discoverability trap.
- [R13.7] ☑ **Summary rows** — left-click no longer cross-filters; right-click a row → **Filter to /
  Add / Remove** its event dimension.
- [R13.8] ☑ **Event types panel** — group-by radio + label removed; **Select all / Select none**
  buttons; split into **Event types** + **Callbacks** sections (divider) by
  `eventDimension = callback ?? event`; right-click an item → **Only this / Add / Remove**. Purely
  visual — filtering stays a single-grouping **OR** over the selected dimensions (no `FilterState` change).
- [R13.11] ☑ **Tab rename** — the "LLM" tab is now **"Analyser assistant"**.
- [R13.12] ☑ **Diff viewer export** — CSV / JSON / PDF buttons on the diff dialog. `DiffExport` (pure
  formatters) + `TextPdf` (dependency-free Courier text-PDF, paginated); 4 tests.
- [R13.13] ☑ **Correctness pass** — "Select none" now clears the view (`FilterState`: empty dims = none,
  null = all); PDF diff lines are clipped to fit a US-Letter page. Plus a review-agent sweep of the
  round-13 / AV code (fixed the filter-echo all-vs-none and a before-heavy `read` anchor drop).
- [R13.14] ☑ **Toolbar icons** — `ToolIcons` draws crisp 16px vector glyphs (folder, bucket, flag,
  funnel, warning, chat, download, play) in the button's foreground colour (theme- and enabled-aware),
  paired with the existing labels. No image assets.
- [R13.9] ☑ **Copy row as YAML** (M8 H8.6) — Records ▸ **Copy selected as YAML** copies the selected
  record(s) raw YAML to the clipboard.
- [R13.10] ☑ **Search row** — **Clear history** button on the right; the search field now grows to
  fill the width (BorderLayout).

## Assistant vocabulary — follow-ups (external review) — ☐ PROPOSED
Round out the action socket so it's a complete, self-describing interface for any agent (synergises
with **M13** MCP). From an external review; #1 (MCP wrapper) is already **M13**.
- [AV.1] ☑ **`read` verb — N records around an offset/index** (rate-limited, max
  `ReadService.MAX_COUNT`). `ReadService` (6 tests) over a `LogIndex.Snapshot` (now carries byte
  offsets + `rowForOffset`) + the raw-text accessor; wired into `ActionDispatcher`, the `/manifest`
  verb list and the manifest prompt. A sandboxed / remote / S3-temp-file agent can now seek the log
  through the socket without filesystem access.
- [AV.2] ☑ **Provenance on agent-created graphs** — the `graph` verb takes an optional `rationale`,
  stored on `GraphSpec.note`, persisted, shared via `SettingsShare`, and shown as an italic caption
  under the plot. Documented in the manifest prompt. (Confirmed as a real gap by the drive-the-UI review.)
- [AV.3] ☑ **Per-verb JSON schemas in `/manifest`** — `VerbSchemas` publishes a draft-07-style object
  per verb (params, types, enums, required) under `manifest.schemas`; single source of truth (also
  feeds the MCP bridge, M13). Agents no longer reverse-engineer shapes from source. _(Review #1.)_
- [AV.4] ☑ **`goto {reveal: true}`** — when the target record is filtered out, `reveal:true` relaxes the
  filter minimally (widen window, add its dimension, drop text) to show it; without it the echo names
  which constraint hides it. _(Review #2.)_

## M13 · MCP transport — ☐ FUTURE (a third door over the same seam)
_Design: **[spec-assistant-actions-mcp.md](spec-assistant-actions-mcp.md)**. MCP as the preferred door for
MCP-native clients (Claude Code/Desktop) — one MCP tool per verb over the **same `ActionDispatcher` /
`RenderExecutor`**. Hand-rolled minimal JSON-RPC (no MCP SDK → keeps the near-zero-dep ethos). Not a
replacement: in-process drives the app's own chat; REST stays the universal zero-config door._
- [M13.1] ☐ **`RestEndpointFile`** — app publishes its live REST url+token+**pid** to `~/.fluxtion-analyser/rest-endpoint`
  (mode 600) on REST start, deletes on stop/exit; bridge does a **pid liveness check** before trusting it
  (clean "not running" vs connection-refused) — so a static MCP client config finds the per-run endpoint.
- [M13.2] ☐ **`McpToolSchemas` + `McpBridge` handshake/list** — `analyser.jar --mcp` (**headless-safe**: sets
  `java.awt.headless`, touches no Swing): hand-rolled **newline-delimited** JSON-RPC stdio (`initialize`
  negotiates `protocolVersion`; `tools/list`) returning the five tool schemas. Schemas are the **single
  source of truth** shared with the REST `/manifest` (no drift).
- [M13.3] ☐ **`tools/call` → REST forward** — map a tool call to `{action, params}`, POST `/action` with the
  token, wrap `ok:true`→text result / `ok:false`→`isError:true` (same actionable feedback). Reuses slice 4.
- [M13.4] ☐ **Docs** — "connect an MCP client" (help/README) + client config snippet.
- [M13.5] ☐ _(later)_ **Resources/prompts** (`analyser://log|selection|node-types|source`) and/or **option B**
  in-app Streamable-HTTP MCP server.

## M11 · Research → monitoring promotion (Grafana) — ☐ FUTURE (vision)
_Design: **[spec-assistant-actions.md](completed/spec-assistant-actions.md) §12**. Two complementary systems: the
analyser answers **unknown, one‑off** questions (forensic, source‑linked, LLM‑assisted); Grafana answers
**known, continuous** questions (dashboards, alerting). The workflow is a **promotion pipeline** — research
a series in the analyser until it's diagnostic, then promote it to production monitoring._
- [M11.1] ☐ **`export_dashboard`** (analyser authoring action / File export) — emit (a) the metric
  **allowlist** and (b) a generated **Grafana dashboard JSON** from the named saved graphs. The named
  `GraphSpec` is the contract; A10.8 built the naming/persistence it depends on.
- [M11.2] ☐ **Telamin‑side tap plugin** (`serverplugin-metrics` / `-grafana`, *not* an analyser feature) —
  a `LogRecordListener` metrics sink alongside the file sink, publishing selected `instanceId.key` as
  typed time‑series (Prometheus / Influx / Kafka). Route B (tap at source), **not** Loki/LogQL re‑parsing
  of raw `toString()`s (Route A rejected — re‑fights the parser battle, loses NaN/boolean/last‑occurrence
  semantics). Cardinality bounded because the graph is static.
- [M11.3] ☐ **Closed loop** — Grafana alert → open the analyser on that log+window → LLM forensics → root
  cause → maybe promote a new series. **Boundary:** the analyser stays a deep‑dive tool; it does **not**
  become a live dashboard (real‑time viz is Grafana's job — don't duplicate where there's no moat).

## M12 · Diagnose → fix → prove flywheel — ☐ FUTURE (mostly out of the analyser)
_Design: **[spec-assistant-actions.md](completed/spec-assistant-actions.md) §13**. The edit loop lives in the dev
env (Claude Code + Telamin CI), **not** the analyser. Replay‑diff is the proof mechanism: deterministic
processor → journal in, byte‑identical audit log out → any behavioural change is a record‑level diff over
a whole replayed day. The analyser's role is the **handoff** — three near‑term, analyser‑side pieces:_
- [M12.1] ☐ **`export_finding` action** — emit the **handoff‑prompt** (structure specced in §13.1): a
  self‑contained fix‑and‑prove task brief — diagnosis, evidence (records + byte anchors + file‑access
  seeding), resolved source targets (`instanceId → file:line`, EP FQN, roots), replay reference (journal +
  window + baseline), task, and acceptance (replay‑diff: only the targeted records change, all others
  byte‑identical). Built by the same `PromptBuilder` machinery. **Precondition:** journal ↔ audit‑log
  pairing — the analyser loads the audit log (output) but not the journal (input); resolve locating the
  journal for a log/time‑window before this and M12.2.
- [M12.2] ☐ **`export_test_fixture`** — record range → regression test oracle: a journaled slice driven
  through the processor / one node asserting its `nodeLogs`. Production incidents → real‑sequence tests.
- [M12.3] ☐ **`DiffBuilder` additive‑vs‑value classification** — report new `nodeLogs` keys separately
  from changed values, so an instrumentation‑only change shows as **pure‑additive** (a verifiable property).
- _Out of scope (dev‑env / Telamin): the LLM fix itself; replay‑diff + unit tests as **mandatory CI
  gates**; **AOT regeneration** when an edit adds a handler/node; guardrail **propose → prove → human
  approves, never autonomous merge** (review the diff of **behaviour**, not just code)._

---

## Suggested delivery order & first slice
1. **M1** (parser+index) — the risk sits here; land it against `sample.yml` with a golden test set.
2. **M2** — table+detail makes it demoable end‑to‑end on a real log.
3. **M3** — filters+summary.
4. **M4 → M5 → M6** — source, LLM, graph (independent; parallelisable).
5. **M7** — large‑file mode once the model is stable.
6. **M8** — help + extras.

**MVP definition:** M0–M3 + M4 (open source) + M5 (explain with key or copy‑prompt). Graph and
large‑file mode follow.

## Decisions (resolved)
- **API key at rest:** stored **cleartext** in `~/.fluxtion-analyser/config`.
- **Display time zone:** **UTC** for all date/time rendering.
- **EventProcessor:** **infer when possible** by scoring candidate processors' `instanceId→field`
  sets against the log's observed instanceIds; fall back to configured/default
  `DemoMarketMakerStrategy` (user can override). Implemented in M4 (needs source parsing).

## Open questions
- Graph "last occurrence per record" vs "all occurrences" default. (spec: last; expose toggle.)