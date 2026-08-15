# Fluxtion Audit Log Analyser — Work Tracker

Companion to **[spec.md](spec.md)**. Status keys: ☐ todo · ◧ in‑progress · ☑ done · ⊘ dropped.

Legend for each item: **[id] status — title** · _acceptance_.

---

## Shipped — archived

Fully-delivered milestones and refinement rounds live in **[completed/tracker.md](completed/tracker.md)**:
M0 setup · M1 parser & index · M2 table + detail · M3 filters & summary · M4 source · M5 LLM ·
M6 graphing · M7 large-file mode · M8 polish/help · M9 UX pass · M10 assistant actions ·
M14 graph artifacts · M15 settings export/import · M16 release & distribution ·
**M17 docs site (live, MkDocs Material, v1.0.0 released)** · refinement rounds 2–13 ·
assistant-vocabulary follow-ups (read / rationale / schemas / reveal).

---

## M13 · MCP transport — ☐ NEXT (a third door over the same seam)
_Design: **[spec-assistant-actions-mcp.md](spec-assistant-actions-mcp.md)**. MCP as the preferred door for
MCP-native clients (Claude Code/Desktop) — one MCP tool per verb over the **same `ActionDispatcher` /
`RenderExecutor`**. Hand-rolled minimal JSON-RPC (no MCP SDK → keeps the near-zero-dep ethos). Not a
replacement: in-process drives the app's own chat; REST stays the universal zero-config door. AV.3's
`VerbSchemas` already provides the shared tool schemas._
- [M13.1] ☐ **`RestEndpointFile`** — app publishes its live REST url+token+**pid** to `~/.fluxtion-analyser/rest-endpoint`
  (mode 600) on REST start, deletes on stop/exit; bridge does a **pid liveness check** before trusting it
  (clean "not running" vs connection-refused) — so a static MCP client config finds the per-run endpoint.
- [M13.2] ☐ **`McpTools` adapter + `McpBridge` handshake/list** — `analyser.jar --mcp` (**headless-safe**: sets
  `java.awt.headless`, touches no Swing): hand-rolled **newline-delimited** JSON-RPC stdio (`initialize`
  negotiates `protocolVersion`; `tools/list`) returning one tool per verb (six today, incl. `read`)
  **adapted from the shipped `VerbSchemas`** (AV.3) — the same single source of truth as REST
  `/manifest`; no parallel schema holder.
- [M13.3] ☐ **`tools/call` → REST forward** — map a tool call to `{action, params}`, POST `/action` with the
  token, wrap `ok:true`→text result / `ok:false`→`isError:true` (same actionable feedback). Reuses slice 4.
- [M13.4] ☐ **Docs** — "connect an MCP client" (site assistant page + README) + client config snippet.
- [M13.5] ☐ _(later)_ **Resources/prompts** (`analyser://log|selection|node-types|source`) and/or **option B**
  in-app Streamable-HTTP MCP server.

## M12 · Diagnose → fix → prove flywheel — ☐ ACTIVE DESIGN
_Design: **[spec-closed-loop.md](spec-closed-loop.md)** Part A (the handoff mechanics) and
**[completed/spec-assistant-actions.md](completed/spec-assistant-actions.md)** §13 (the original
diagnose → fix → prove framing). The edit loop lives in the dev env (Claude Code / Codex + CI),
**not** the analyser — the analyser's role is the **briefing and verification instrument**._
- [M12.1] ☐ **`export_finding` action** — emit the **fix-brief** (structure in spec-assistant-actions
  §13.1): diagnosis, evidence (records + byte anchors + file-access seeding), resolved source targets
  (`instanceId → file:line`, EP FQN, roots), replay reference, task, and acceptance (replay-diff: only
  the targeted records change). Built on `PromptBuilder`. **Precondition:** journal ↔ audit-log pairing
  (the analyser loads the output log, not the input journal).
- [M12.4] ☐ **"Fix with agent…" handoff launcher** _(spec-closed-loop §A)_ — writes the brief to
  `<sourceRoot>/.analyser/fix-brief-<ts>.md` **plus `.analyser/.gitignore` (`fix-brief-*` — scoped so
  M20's committed project profile in the same dir isn't ignored) so briefs can never be committed**; v1 copies a ready-to-paste launch command (presets for Claude Code / Codex; template
  placeholders `{brief}` `{sourceRoot}` `{logPath}` — **no token in the command**: the agent reads the
  rotating endpoint+token from M13.1's `~/.fluxtion-analyser/rest-endpoint`). Every brief embeds the
  **git-hygiene contract** (branch, evidence-linked PR, never merge autonomously, prove by replay
  test) — the brief *instructs*; enforcement is the user's branch protection + PR review. _Accept:
  paste one command → agent opens holding the brief, works on a branch, PRs with evidence cited._
- [M12.2] ☐ **`export_test_fixture`** — record range → regression test oracle: a journaled slice driven
  through the processor / one node asserting its `nodeLogs`. Production incidents → real-sequence
  **red tests** the fixing agent turns green.
- [M12.3] ☐ **`DiffBuilder` additive-vs-value classification** — report new `nodeLogs` keys separately
  from changed values, so an instrumentation-only change shows as **pure-additive** (a verifiable property).
- _Out of scope (dev-env / Telamin): the LLM fix itself; replay-diff + unit tests as **mandatory CI
  gates**; **AOT regeneration** when an edit adds a handler/node; guardrail **propose → prove → human
  approves, never autonomous merge** (review the diff of **behaviour**, not just code)._

## M18 · Mongoose server link — ☐ PROPOSED (instrument gains a control plane, carefully)
_Design: **[spec-closed-loop.md](spec-closed-loop.md)** Part B. The analyser connects to a **locally
running** Mongoose server's admin REST (`serverplugin-rest`; nodes already register
`AdminCommandRegistry` commands) to discover the log, control audit verbosity at runtime, and (dev)
restart to pick up a fix. Capabilities tiered by risk; **server verbs are never assistant actions**
(the FAQ's "nothing outside the loaded log" guarantee stays true for agents); every mutation is
human-confirmed and journaled to `~/.fluxtion-analyser/ops-log`. Localhost-only in v1._
- [M18.0] ☐ **Spike — verify the admin surface (gates all of Part B)** — `svc-admin-web` (validated)
  already serves **status/identity** (`GET /api/server`) and **processor enumeration**
  (`/api/services`,`/api/agents`,`/api/queues`); the spike's real job is the three gaps — **audit-sink
  discovery, audit level (`EventLogControlEvent`), lifecycle** — none are REST endpoints, so confirm
  each as a registered `AdminCommandRegistry` command (`POST /api/commands/{name}`) or file a
  `fluxtion-server-plugins` PR **before** M18.1+ is scheduled. Note `WS /ws/logs` is app logging, **not**
  the event-audit sink. **Test bench: the M19 example bundle** (admin REST on, disposable, predictable
  log) — M19.1 and M18.0 co-develop.
- [M18.1] ☐ **Link + status (read-only)** — Settings ▸ Server link (admin base URL, loopback-enforced;
  per-link **"development server — restarts allowed"** opt-in flag); status-bar chip
  (connected/name/uptime); Server menu scaffold.
- [M18.2] ☐ **Log discovery** — "Open server's audit log": resolve the **sink descriptor (type+path)**
  from server config; text file sink → open + offer Follow; Chronicle/kafka/jdbc sink → say so plainly
  (analyser reads the text file sink). The audit **writer is pluggable** so discovery must branch on
  sink *type*, not assume a file path. _One-click "point the analyser at your running system"._
  _(Future, uncommitted: pluggable analyser readers mirroring the sinks.)_
- [M18.3] ☐ **Audit level control** — raise/lower the processor's audit level
  (`EventLogControlEvent`) while tailing, with **capture-and-restore** (record the found level,
  auto-restore on disconnect/exit — never strand a server at TRACE); confirm dialog names the
  volume/latency/disk cost; ops-journal entry. _Diagnosis-grade telemetry on demand, no restart._
- [M18.4] ☐ **Dev restart** — stop/start/restart the linked server, **only where the per-link dev
  opt-in is set** (localhost ≠ disposable); confirm dialog carries live context from the log
  ("published quotes 2s ago — restart?"). Composes with M12: fix lands → restart → Follow verifies on
  the fresh log.
- [M18.5] ☐ _(deferred)_ deploy-jar-and-restart; non-loopback/production posture (only alongside the
  regulated-tier approvals/attestation story); agent-initiated server actions behind per-action human
  approval.
- Open questions: **O1** admin endpoint surface — **partly resolved by `svc-admin-web`** (status +
  enumeration served; audit-sink/level/lifecycle still spike-gated in **M18.0**) · **O2** multi-processor
  servers (list from `/api/services`; per-processor *typed* sink) · **O3** admin auth beyond localhost.
  _(O4 gitignore: resolved — the analyser writes it.)_

## M19 · Onboarding example — playground download → running Mongoose → analyser — ☐ PROPOSED
_Design: **[spec-onboarding-example.md](spec-onboarding-example.md)**. The playground's Download button
ships a runnable Mongoose example with audit logging pre-enabled (file sink at a predictable path),
bundled source, and a **project profile at `.analyser/project.fluxtion-settings`** (M20's canonical path —
the bundle *is* a project profile) — so onboarding becomes: download → run → jbang the analyser →
project auto-loads (M20; **File ▸ Import** until it lands) → Follow a live log with click-to-source and Explain working.
Target: under 10 minutes on a fresh machine with only a JDK. The bundle's README links back to the
analyser (reverse funnel)._
- [M19.1] ☐ **Bundle contract (playground-side)** — **full Maven project** (O1 resolved: user edits
  it in their IDE with their own LLM) with audit enabled + generated/EP source + settings file +
  **`CLAUDE.md` agent bootstrap** (canonical Fluxtion authoring prompt, maintained with
  fluxtion-compiler's LLM-authoring guidance) + admin REST on + README with run command and analyser
  link; tracked in the playground repo, contract recorded in the spec.
- [M19.2] ☑ **`SettingsShare`: resolve relative roots against the import file's parent** — `preview`
  gained a `baseDir` overload; import resolves bundle-relative source roots / Maven repos against the
  settings file's directory (absolute & `~`-paths untouched; clipboard imports pass no baseDir). 2 tests.
  Unblocks the tutorial's "zero manual setup" claim.
- [M19.3] ☐ **Tutorial page** `docs/site/tutorial-playground.md` — four parts (run+import ·
  analyse/tail · assistant · edit-with-your-IDE's-AI) + the 8-screenshot set (spec §Part 2, anonymised
  per policy), nav under Getting started. **Publish-gated on the bundle shipping** (write against the
  contract; publish only when Download delivers). **Two authoring notes:** (a) the pathway table names
  the Support leg "run, observe, diagnose *and fix*" — but "fix" is M12/M18; keep the page copy honest
  to what's shipped that week (don't promise fixing before M18 lands — the end-bridge already phases it
  as "+ server link once M18 ships"). (b) In-page links must be **site-relative** (`producing-a-log.md`,
  not the spec's `../site/producing-a-log.md`) or `mkdocs build --strict` fails the link-check.
- [M19.4] ☐ **Cross-links** — getting-started step 2, producing-a-log, landing "Get going"; **and the
  tutorial's end-bridge**: a closing "Do this on your own system →" section linking producing-a-log
  (+ the server link once M18 ships) — the demo must hand off to the user's real adoption, not stop at
  the toy.
- [M19.5] ☐ _(defer unless tutorial reads clunky)_ **File ▸ Open example…** one-action helper
  (import + open + Follow).
- Open: O2 which example. _(O1: Maven project · O3: bundles generated at Download time, nothing to
  regenerate · O4: committed as M19.2 — all resolved.)_

## M20 · Project profiles — global vs local settings — ☐ PROPOSED
_Design: **[spec-project-profiles.md](spec-project-profiles.md)**. Give the analyser a first-class
**project** concept so a user jumps between Fluxtion projects without re-importing. Two disjoint tiers
reusing M15's whitelist as the boundary: **global (machine)** = API key · LLM · AWS · theme · perf ·
history (`~/.fluxtion-analyser/config`, as today); **project profile** = source roots · event processor ·
Maven repos · graphs · hidden columns (at `<project>/.analyser/project.fluxtion-settings` — one canonical
path, shared with the M19 bundle).
Switching a project **replaces** the project-scoped set (never merges into global); the API key can't
leak by construction, so a profile is **git-shareable** (commit it like `.vscode/settings.json`).
Generalises M19 (the playground bundle is a project profile → auto-configure on open). Import (M15) stays
the additive *share* flow, gaining an explicit "open as project (replace)" option._
- [M20.1] ☐ **Tier the config** — mark project-scoped categories; add `activeProjectPath`; load/save a
  project profile via `SettingsShare` (REPLACE for the project set). Headless-testable.
- [M20.2] ☐ **Open / Switch / New project + Recent projects** UI; merge-vs-open-as-project choice on Import.
- [M20.3] ☐ **Auto-detect** a project file beside an opened log → "Load this project?" (the M19 hook).
- [M20.4] ☐ **Docs** — user-guide "working across projects"; git-shareable profile; M19 tutorial upgrades
  from "import once" to "auto-loaded project".
- Depends on **M19.2** (relative-root import, shipped). Open questions all resolved in the spec: O1
  Maven repos project-scoped · O2 in-repo profile path · O3 defer reopen-last-log · O4 auto-persist
  (debounced), no explicit Save.

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

---

## Suggested delivery order (post-1.0.0)

_M13 and M18 are **independent tracks** (spec-closed-loop §Delivery order) — M12.4's floor is the
already-shipped REST + brief file, so neither blocks the other; run them in parallel or in this order:_
1. **M13.1–13.4** (MCP bridge) — smallest step, biggest reach: every MCP-native agent can drive the
   analyser with zero prompting; AV.3 already built the schemas.
2. **M18.0 spike, then M18.1 → M18.2 → M18.3** (verify admin surface; server link, read-only → log
   discovery → audit level) — small slices, each immediately useful with Follow.
3. **M12.4** (fix-with-agent launcher, v1 copy-command) — with M13 live, the handed-off agent can
   query back while it works.
4. **M18.4** (dev restart) — completes the local diagnose → fix → redeploy → verify demo
   (spec-closed-loop §B.7), the demo that sells the platform.
5. **M12.1 / M12.2** (export_finding structure; replay-test fixture) — upgrades the brief's
   acceptance from prose to a red test. Journal↔log pairing is the precondition to resolve first.
6. **M20.1 + M20.3** (tier the config + auto-detect a project profile beside a log) — **pulled ahead of
   the tutorial**: these turn M19's onboarding from "import once (additive into global)" into
   "open the log → project auto-loads", which is the experience the tutorial should teach. Small,
   headless-testable, depends only on the shipped M19.2.
7. **M19** (onboarding example) — mostly docs + playground-side; M19.1's bundle contract can proceed
   in parallel with anything above; write the tutorial against the M20 auto-load flow. Highest funnel
   value per effort after launch.
8. **M20.2 + M20.4** (switch/new/recent UI + docs) — completes project profiles once the auto-load path
   is proven by the tutorial.
9. **M11** stays vision until a real Grafana consumer appears.

## Decisions (resolved)
- **API key at rest:** stored **cleartext** in `~/.fluxtion-analyser/config`.
- **Display time zone:** **UTC** for all date/time rendering.
- **EventProcessor:** **infer when possible** by scoring candidate processors' `instanceId→field`
  sets against the log's observed instanceIds; fall back to configured/default
  `DemoMarketMakerStrategy` (user can override). Implemented in M4 (needs source parsing).
- **Server control is not an assistant capability** (spec-closed-loop §B.5) — server verbs never
  appear on the action socket; any future agent-initiated server action requires per-action human
  approval. Keeps the FAQ's security guarantee simple and true.
- **Agent fixes arrive as evidence-linked PRs on a branch, never direct edits** (spec-closed-loop
  §A.4) — the M12 guardrail, embedded verbatim in every generated brief.

## Open questions
- Graph "last occurrence per record" vs "all occurrences" default. (spec: last; expose toggle.)
- spec-closed-loop **O1–O4** (admin endpoint surface · multi-processor discovery · admin auth ·
  brief-file gitignore).
