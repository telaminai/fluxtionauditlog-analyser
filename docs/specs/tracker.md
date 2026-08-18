# Fluxtion Audit Log Analyser — Work Tracker

Companion to **[spec.md](spec.md)**. Status keys: ☐ todo · ◧ in‑progress · ☑ done · ⊘ dropped.

Legend for each item: **[id] status — title** · _acceptance_.

---

## Shipped — archived

Fully-delivered milestones and refinement rounds live in **[completed/tracker.md](completed/tracker.md)**:
M0 setup · M1 parser & index · M2 table + detail · M3 filters & summary · M4 source · M5 LLM ·
M6 graphing · M7 large-file mode · M8 polish/help · M9 UX pass · M10 assistant actions ·
M13.1–13.4 MCP bridge · M14 graph artifacts · M15 settings export/import · M16 release &
distribution · **M17 docs site** · M20 project profiles · M21 topology + step-through (core +
intra-record cursor) · M22 usability (36 of 41) · M23 explaining-what-you-found + charts ·
M24 coverage · M25 drift fixes · M26 agent-efficiency verbs · M27 focus as a filter context +
named focuses · M28 conditionals + rolling windows + guides/bands · refinement rounds 2–13 ·
assistant-vocabulary follow-ups.

---

## Hardening — test-only, ongoing (no user-visible change)
- ☑ **Cross-transport schema contract** — REST `/manifest` and MCP `tools/list` are proven to advertise
  the *same* `VerbSchemas` schema per verb at the **value** level, not just matching name sets:
  `McpToolsTest` pins every tool's `inputSchema` == its verb schema minus the lifted `description`;
  `ManifestVerbContractTest` pins `/manifest`'s `schemas` field == `VerbSchemas.all()` verbatim. The two
  transports can no longer fork a verb's parameters.
- ◧ **Formula golden fixtures** — a hand-derived expected-series corpus for the Expr engine, grown
  **without code** (`graph/FormulaGoldenTest` + `src/test/resources/formula-golden/*.golden`). First
  tranche: LOCF/STRICT/two-arg-if/NaN + rolling `mean` fill-before-speak / `lag` / `delta`. The rule
  (derive from intended semantics, **never snapshot output**) and the TODO taxonomy — `rate()`
  span-normalisation first, the c3094ea bug class — are in
  **[spec-formula-golden-fixtures.md](spec-formula-golden-fixtures.md)**.

---

## M13 · MCP transport — ◧ M13.1–13.4 SHIPPED (archived; M13.5 open)
_M13.1–13.4 (endpoint file, bridge, tools/call forward, docs) shipped 2026-08-15,
reviewed and merged — full record in **[completed/tracker.md](completed/tracker.md)**.
Design: **[spec-assistant-actions-mcp.md](spec-assistant-actions-mcp.md)** (stays live for
M13.5). Review decisions: hand-rolled JSON-RPC kept over an SDK; `structuredContent` parked
with M13.5._
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
- _Decision (2026-08-16): **the analyser prompt distils Fluxtion semantics; the fix brief carries the
  authoring rules**. The assistant reads logs, so it gets the reading-relevant subset (propagation/dirty,
  audit regimes, wiring-by-constructor) plus a fetch-on-demand pointer — not `claude.txt` inline, which is
  ~30KB of authoring guidance per request and invites answering the wrong question. **M12.4's brief is
  where the full authoring rules belong**, since that agent edits code; M19.1's bundle already plans the
  same via its `CLAUDE.md` bootstrap._
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
- [M18.0] ☑ **Spike — verify the admin surface (gates all of Part B)** — **done; O1 resolved.** Findings:
  **[spike-m18.0-admin-surface.md](spike-m18.0-admin-surface.md)**. Two of the three "gaps" are already
  **closed** on `mongoose-plugins@origin/develop` (the local checkout was stale): **audit level** is a
  REST endpoint (`POST /api/processors/{group}/{name}/audit/level`), not a registry command; **log
  discovery** is `GET /api/audit/files` + `/api/audit/file/{id}/export?format=yaml`, which returns the
  exact YAML the analyser parses. **Lifecycle is the only real gap** — service start/stop are commented
  out in `MongooseServerAdmin` and no restart exists → small `mongoose` PR. No `fluxtion-server-plugins`
  PR needed to unblock M18.1–18.3. _Caveat: verified by reading source, **not** against a running server
  (the M19 bench doesn't exist yet) — M18.1 must re-confirm live._
  **⚠ Gating question raised, not answered — see [Decisions ▸ open](#open-questions): the audit-capture
  plugin's Phase 2 is a web audit-log viewer with graph replay inside `svc-admin-web`, overlapping this
  analyser. Settle positioning before scheduling M18.2–18.4.**
- [M18.1] ☐ **Link + status (read-only)** — Settings ▸ Server link (admin base URL, loopback-enforced;
  per-link **"development server — restarts allowed"** opt-in flag); status-bar chip
  (connected/name/uptime); Server menu scaffold.
- [M18.2] ☐ **Log discovery** — **redesigned by M18.0**: not "resolve a path from config" (the on-disk
  format is **Chronicle**, which the analyser cannot read). Instead `GET /api/audit/files` → pick from the
  catalog (`path`, `sizeBytes`, `recordCount`, `startedAt`) → `GET /api/audit/file/{id}/export?format=yaml`
  → open the projected YAML the analyser already parses. `WS /ws/audit-tail/{processor}` is the candidate
  for Follow. _One-click "point the analyser at your running system"._ **Blocked on the positioning
  question** (tracker ▸ Open questions).
- [M18.3a] ☐ **DECIDE before M18.3** _(review F2)_ — the audit-level endpoint is a **setter with no `GET`
  companion**, so capture-and-restore has nothing to read. Either file the small server-side `GET` ask, or
  re-spec restore to a **user-declared baseline**. Blocks M18.3 only; M18.1 is unaffected.
- [M18.4a] ☐ **ASK mongoose before M18.4** _(review F3)_ — `server.service.start`/`stop` exist but are
  **commented out** in `MongooseServerAdmin`. Find out **why** before sending the uncomment-plus-restart
  PR; the reason may be load-bearing.
- [M18.6] ☐ _(post-M18.1, review F4)_ **Free wins from the spike** — `GET /api/source?fqn=` and
  `/api/processors/{group}/{name}/graphml` let a linked server resolve **source** and **topology**,
  complementing local source roots (and feeding M21.7).
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
- Open questions: **O1 admin endpoint surface — RESOLVED by M18.0** (audit level + log discovery served;
  only lifecycle gapped — see [spike-m18.0-admin-surface.md](spike-m18.0-admin-surface.md)) ·
  **O2** multi-processor servers — **largely answered**, and a terminology fix: **event processors**
  (the DataFlow graphs that emit `nodeLogs`) are *not* **services** (environmental deps Mongoose manages —
  DB, Kafka). Processors come from `server.processors.list` / `/api/processors/{group}/{name}/…`, and the
  audit + level + graphml routes are already per-processor. `/api/services` enumerates something else
  entirely, and `/api/services/{name}/config` says nothing about the audit sink. · **O3** admin auth beyond localhost — **now concrete**: `svc-admin-web`
  has `POST /api/session/login` and `authMode` may not be `NONE`, so M18.1 needs auth from day one.
  · **O5 (NEW, gating) positioning vs the server's own audit viewer** — see Open questions below.
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
  **`CLAUDE.md` agent bootstrap** (the layered prompt stack in spec §Contract — thin example-specific
  layer, snapshot of the canon at generation time, canonical-reference line) + admin REST on + README
  with run command and analyser link; tracked in the playground repo, contract recorded in the spec.
  _**The authoring path is not a gap — use its front door.** It is already layered and maintained:
  [`/build-with-ai`](https://fluxtion-playground.dev/build-with-ai) →
  [`CLAUDE.md`](https://fluxtion-playground.dev/CLAUDE.md) (orientation) → `spring-authoring/skill.md`
  (how to run the design conversation) → `contract.md` (the exact `FluxtionSpringConfig` XML to emit) →
  `example.md` (a worked run) → the **project starter generates the build** — the pom is generated
  output, not something an author writes. Design work: `fluxtion-compiler/design/spring-authoring`.
  The bundle's job is to **reference and snapshot** that canon plus what only it knows (log path, admin
  port, the analyser's endpoint file), never to author a rival prompt. Add `skill.md`/`contract.md` to
  the snapshot set for the XML-defined example (spec O2), since those are what make the design-level
  edit in tutorial part 4 possible._
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
- Open: O2 which example — **tiebreaker: prefer Spring-XML-defined** (design-IR edit variant in the
  tutorial + the design→graph→record provenance chain; spec §Contract notes). _(O1: Maven project · O3: bundles generated at Download time, nothing to
  regenerate · O4: committed as M19.2 — all resolved.)_

## M21 · Topology view + step-through — ◧ CORE SHIPPED (archived; 21.7–21.9 open)
_M21.1–21.6 (parse, layout, panel, step-through, wiring, docs) and M21.10 (intra-record
cursor) shipped and reviewed — full record in **[completed/tracker.md](completed/tracker.md)**.
Design: **[completed/spec-graph-replay.md](completed/spec-graph-replay.md)**._
- [M21.7] ☐ _(later)_ server-sourced GraphML via `GET /api/processors/{group}/{name}/graphml` (needs M18.1).
- [M21.11] ☐ **Consume the declared trace flag** _(owner ask 2026-08-17; needs UP-FLX-11 upstream)_ —
  when the record header carries `trace: true|false` (per record — the level is runtime-gated, one
  file can hold both regimes), prefer the declaration over `AuditTrace.tracesEveryInvocation` and keep
  the heuristic only for legacy logs. Classification language shifts from inference to declared fact:
  `trace:true` → absence drawn as DID NOT RUN with certainty; `trace:false` → absence drawn as a
  potentially missing step; the empty-cycle case (unclassifiable today) resolves. Small: parser reads
  one header key; `AuditTrace` gains a declared-first path; topology legend wording updates.
- [M21.9] ☐ **Use `ProcessorDescriptor` instead of inferring** _(found 2026-08-16 reading a generated
  processor)_ — AOT processors carry a self-description: `inputs()` (name + FQN of every accepted event),
  `sinks()`, `services()`, plus `graphmlResource()`, `sourceFingerprint()` and `toolchainVersion()`.
  Two of those would replace guesswork outright:
  **`graphmlResource()`** is "the classpath resource name of the graphml sidecar describing this
  processor's topology" → auto-resolve the topology instead of *Open .graphml…*;
  **`sourceFingerprint()`** is "a fingerprint of the source graph… the cache/staleness key" → exact build
  pairing instead of `Match` inferring it from instanceIds.
  **Blocked, not ready:** verified on two independently generated processors that `Meta` is emitted as
  `(null, null, null, null)` — the contract exists, the values are unrecorded. `inputs()`/`services()`
  *are* populated and could sharpen `EntryPointResolver`, though the graphml's EVENT/EXPORTSERVICE nodes
  already carry equivalent information. **Next step is an ask upstream** (populate `Meta`), not code here.
- [M21.8] ☐ _(later)_ **node → flag** — "flag every record where node X fired" needs an `instanceId`
  lookup in `LogIndex`; flags are per-record and no such index exists, so M21.5 shipped filter-to-node
  (existing free-text scan) instead. Index work, not wiring.
- Open: **O1** GraphML attribute shape (read the visualiser's parser) · **O2** logRecord sink/transport
  config — **does not gate M21**, only M21.6 and M18.2 · **O3** tab vs dockable split · **O4** very large
  topologies (elision/clustering) — defer until a real graph hurts.

## M22 · Topology view usability — ◧ 36 of 41 SHIPPED (archived; 5 open)
_The shipped 36 (and 2 superseded) are in **[completed/tracker.md](completed/tracker.md)**.
Open: **22.3** PNG export · **22.6** alternative layouts · **22.11** re-dispatch cause (needs
`UP-FLX-10`, see `docs/proposals/upstream-asks.md`) · **22.19** partial (chips deliberately
not built) · **22.20** `.push()` targets render as orphans._
- [M22.3] ☐ **Export the view as PNG** — reuses the offscreen render already used to verify the canvas;
  pairs with the existing graph/record exports.
- [M22.6] ☐ **Alternative layouts** — the largest item. `LayeredLayout` is Sugiyama; candidates are
  breadthfirst-from-entry and a compact/orthogonal variant. Keep `TopologyLayout` as the output contract
  so the canvas is unchanged.
- [M22.11] ☐ **Re-dispatch (`processReentrantEvent`) — show the cause.** A node can raise an event on its
  own graph. Measured against generated code and a real fixture: `afterEvent()` publishes the audit record
  **before** `dispatchQueuedCallbacks()` runs, so a re-dispatch is **not** extra rows on the causing
  cycle — it is a **separate `eventLogRecord`**, same thread, normal `eventTime` (an exported call is
  stamped `-1`), carrying nothing that says it came from inside. The graphml is no help either: the raised
  event is an ordinary EVENT node with no edge from the node that raises it. So linking effect to cause
  needs the **processor source** (which node calls `processReentrantEvent`/`processAsNewEventCycle` with
  which type) plus record adjacency. Without source, claim nothing. Fixture: `riskMonitor` →
  `RiskBreachEvent` → `breachHandler` in `demo-quote-audit.yaml`, pinned by two tests.
- [M22.19] ◐ **Step-through header, playground-style** — header (`event 8 / 10 · step 2 / 5`), whole-record
  skip (`◀◀`/`▶▶`) and autoplay shipped; autoplay drives the same `stepBy(1)` path as ↓ and stops at the
  end of the log rather than sitting on the last row. The compact wording is deliberately terse: the full
  regime-aware form ("row 2 / 5 (logged nodes)") stays in the status line, because two differently-worded
  claims about the same position is how the regime distinction gets lost.
  **Event-kind chips NOT built, on purpose:** the app already has an event-type filter (the panel M22.17
  just made collapsible). A second one in the topology tab would be a second source of truth for which
  records are in scope — the exact failure `spec-graph-replay` §6 rules out for record selection. If chips
  are wanted, they should *render* the existing `FilterState`, not hold their own.

- [M22.20] ☐ **A DataFlow `.push()` target renders as an orphan.** *Measured 2026-08-17 against a probe
  graph compiled for the GraphML investigation (`docs/proposals/upstream-asks.md` §2c), fixture
  `src/test/resources/topology/push-probe.graphml`, current behaviour pinned by `PushChainTest`.*
  A `.push(target::setter)` materialises as a chain of three framework nodes —
  `rawFeed → nodeToFlowFunction_8 → mapRef2RefFlowFunction_9 → pushFlowFunction_10 → pushTarget` — all of
  class `com.telamin.fluxtion.runtime.flowfunction.function.*`, which **matches `Scaffolding`'s framework
  package prefix**. So with scaffolding hidden (**the default**) all four edges drop and `pushTarget`
  survives as an authored node with **zero edges**: a disconnected box with no explanation, the
  `rawFeed → pushTarget` relationship gone. With scaffolding shown it reads as four ordinary edges through
  three plumbing nodes — implying a propagating chain, when `.push()` is *defined* by downstream not
  seeing the effect (framework reference `docs/claude.txt`). Neither view is correct.
  **The honest fix needs `UP-FLX-28` + `UP-FLX-29` together** (marking *and* identification — with only
  the second, hiding the plumbing is what deletes the relationship).
  **Available now, without upstream:** stop orphaning silently. The status line should say *N nodes are
  connected only through hidden scaffolding* — the same move M27 already makes for a cycle that ran
  through nodes the current context cannot show. Do that first.
  **Deliberately NOT proposed:** synthesising a direct `rawFeed → pushTarget` edge by recognising
  `PushFlowFunction` by class name. It would draw the right picture by exactly the name-matching
  fragility `UP-FLX-29` exists to remove, and a wrong-but-confident edge is worse here than an honest
  gap. Revisit only if the owner rules the upstream attributes out.

## M29 · External series — ☑ ON BRANCH `feat/m29-external-series` (M29.1–.4; M29.5 embed deferred by design) (plot what the outside world did)
_Design: **[spec-external-series.md](spec-external-series.md)**. Owner ask: an agent filters and parses a
foreign log (FIX to begin with) into a CSV, hands the analyser the file location, and the analyser plots it
beside the audit-derived series. **The analyser never learns a foreign format** — the agent adapts, the tool
stays hermetic; spec'd as external timeseries, never as "FIX support". The drawing is free: `Series` is
`(long[], double[], label)` and `key == null` for derived series is already supported, so a foreign series
is structurally what a formula series already is — renderer, legend, axes and exports need no change. The
work is honesty, posed as five decisions for review: the clock domain is **declared, never inferred**
(D-F1); foreign series are **permanently second-class** — no recordIndex, so no goto/flag/anchors, and
stamped external in every export (D-F2); **no foreign refs in formulas** — cross-clock carry semantics
deserve their own proposal, not namespace accident (D-F3, rationale made durable in review); reads are
**confined and their diagnostics sanitised**, the read counterpart to `ExportGuard` — allowlist narrowed
in review to project dir + chooser-as-grant, source roots deliberately excluded, FAQ gains the read rule
(D-F4); saved graphs store project-relative paths and **degrade out loud** (D-F5, the F1 lesson). Review
also closed three contract gaps: out-of-order rows sort with an echo, duplicate timestamps both kept,
5M-row cap refused loudly._
- [M29.1] ☑ **Loader + CSV contract** *(shipped on `feat/m29-external-series`)* — explicit time/zone/value columns, no sniffing; sort-on-load
  with reorder echo; duplicates kept; 5M-row cap; bounded sanitised parse diagnostics. Headless and
  pure; full D-F1/D-F4 tests before any UI.
- [M29.2] ☑ **UI** *(shipped on `feat/m29-external-series`)* — *File ▸ Add series from CSV…*, legend marking, offset display, D-F2 export stamping.
- [M29.3] ☑ **`graph {external}` verb** *(shipped on `feat/m29-external-series`)* — M26.4-style echo (rows loaded/skipped/reordered, range,
  offset — the range echo is the wrong-pattern defence); read confinement wired to the allowlist
  (project dir + chooser grants); FAQ security answer gains the read rule, contract-test pinned.
- [M29.4] ☑ **Persistence + sharing** *(shipped on `feat/m29-external-series`)* — project-relative paths, honest degradation, export-side disclosure;
  docs + changelog.
- [M29.5] ☐ *(optional, decide after 29.4)* **`embed: true`** — carry small series inside the saved graph
  for fully-portable sharing (D-F5's alternative).

## M30 · Rolled log sets — ◧ IN PROGRESS (ACCEPTED v2) (one session, many files)
_Design: **[spec-rolled-logs.md](spec-rolled-logs.md)**. Owner ask: open a set of same-rooted rolled
files (date-time or index suffixes) as ONE log, with time validation catching sets that are not
correctly ordered. Principle: **names discover; content orders; violations are reported, never
repaired** — suffix conventions are ambiguous (logrotate's `.1` is newest, a writer's `.1` is oldest),
so file order comes from each file's first `logTime`, and disordered records are surfaced as a
`TimeOrderReport` (UI banner, `open` echo, `context`), never silently re-sorted. `recordIndex` stays
the global gap-free anchor; byte offsets become (file, offset) pairs so the copy-prompt's
grep-the-file promise stays true. Opening a set is offered, never assumed (M20.5's offer-never-act);
memory scales per SET — the heap threshold applies to the member-size total, all-mapped above it
(D-R6 corrected in review: per-file thresholding was a confirmed defect). Monotonicity checking also lands
for single files — A2 (time order is load-bearing for `at`/windows/buckets) finally checked, with
loud degradation notes instead of wrong answers._
- [M30.1] ☑ **`RollSetResolver`** *(shipped on `feat/m29-external-series`)* (pure) — suffix grammars, head/tail time probe, content ordering,
  `TimeOrderReport`; both logrotate-convention fixtures pass without configuration.
- [M30.2] ☑ **Composite store** *(shipped on `feat/m29-external-series`)* — per-file backends under one global index, per-record file id,
  (file, offset) anchors through read/goto/crossings/context/copy-prompt.
- [M30.3] ☐ **Validation surfaced** — banner + go-to-violation, verb echoes, single-file
  monotonicity check, D-R4 caveats on time-anchored features.
- [M30.4] ☐ **Offer + `open {logs}`** — offer-never-act UI, verb + schema, docs + changelog.

## M31 · Log-source plugins — ☐ ACCEPTED (other containers, same records)
_Design: **[spec-log-source-plugins.md](spec-log-source-plugins.md)**. Owner ask: parquet / Chronicle /
DB audit sources as **plugins, not a requirement**. The core understands ONE thing — the Fluxtion audit
record — and containers adapt to it: a tiny reader SPI (identity, `canOpen`, record stream in container
order, capability flags) with the CORE building index/store above it; every record carries a canonical
text rendering so the text-shaped surfaces keep working; plugins are jars the user explicitly installs
(isolated classloaders, arbitrary-code warning named in FAQ + Settings, nothing bundled, no network).
The fatjar stays FlatLaf-only — no format dependency ever enters this pom; the shipped text parser
refactors to BE the built-in reader (the seam proven on the format that matters). Capabilities degrade
loudly (a parquet file can't follow; a DB row has no byte offset — recordIndex anchors, per M30 D-R2).
Sequencing: M30.2 and M31.1 touch the same store-assembly seam — serialise them._
- [M31.1] ☐ **The SPI + text parser behind it** — `analyser-reader-spi` artifact; suite green
  unchanged (M28.2-shaped inversion).
- [M31.2] ☐ **Registry + isolation + Settings ▸ Plugins** — trust boundary in FAQ, contract-test
  pinned.
- [M31.3] ☐ **Capability wiring + `open` integration** — loud degradation, `format` override,
  refusal names installed plugins.
- [M31.4] ☐ **Out-of-tree example reader + plugin-author guide** (playground repo) + changelog.

## M32 · Marker series — ☐ ACCEPTED (events on a value chart)
_Design: **[spec-marker-series.md](spec-marker-series.md)**. Owner ask: buys/sells on a price plot
with the client order id and a distinctive point style, plus point-snapped mouseover. A marker series
is `(time, y, payload)` drawn as glyphs — the one legitimate path for categorical/per-event data onto
a chart (text values are unplottable today by design). Three sources, one model: key triples from the
log, M28 condition exprs, the M29 CSV loader (severable coupling). Payloads are DISPLAY CARGO — hover,
click→goto, exports — never computable (`Expr` stays numeric; the record is the queryable form).
Density degrades to a count glyph, never silence and never soup (cap-honesty, drawn). Persisted as
SOURCE not points (M28.6's rule); fifth artifact on the Graphs share category — full checklist +
disclosure row. Subsumes M28's P3: the rug strip is `y: "axis"`, and flagged records become a built-in
rug. Mouseover generalises to EVERY series: snap to the nearest sample (label · time · value; payload
for markers; min/max on a decimated column), coordinate readout as fallback._
- [M32.1] ☐ **Point-snapped mouseover, all series** (severed in review — independent, shippable
  first; changes a shipped surface so it gets its own changelog + acceptance).
- [M32.2] ☐ **Model + extraction** (pure) — MarkerSeries, key-triple + condition sources on the
  existing record walk, series-pinned `y` with the dangling-pin loud-degrade rule, density
  aggregation as data (headless-testable D-M3).
- [M32.3] ☐ **Rendering** — glyphs, count badges, payload on the M32.1 tooltip, click→goto, the axis
  lane + Flags rug; offscreen-PNG verification (the eyeball-heavy slice).
- [M32.4] ☐ **Verb** — `graph {markers}`, REPLACE + warnings contract (incl. dangling-pin), M26.4
  echoes.
- [M32.5] ☐ **Persistence + share + exports** — D-M4 checklist, PDF markers table with cap note,
  capture-harness screenshot, docs + changelog; external-CSV source here iff M29 shipped.

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

## Suggested delivery order

_M13 (bridge) · M20–M28 all shipped — see completed/tracker.md. What remains:_
1. **M29.1–29.3** (external series: loader → UI → verb) — spec ACCEPTED path pending the
   review's three spec edits (`docs/handoff/review_m29_external_series.txt`).
2. **M18.0 spike, then M18.1 → M18.2 → M18.3** (verify admin surface; server link, read-only →
   log discovery → audit level) — small slices, each immediately useful with Follow.
3. **M12.4** (fix-with-agent launcher, v1 copy-command) — with M13 live, the handed-off agent
   can query back while it works.
4. **M18.4** (dev restart) — completes the local diagnose → fix → redeploy → verify demo
   (spec-closed-loop §B.7).
5. **M12.1 / M12.2** (export_finding structure; replay-test fixture) — journal↔log pairing is
   the precondition to resolve first.
6. **M19** (onboarding example) — mostly docs + playground-side; M19.1's bundle contract can
   proceed in parallel; write the tutorial against the shipped M20 auto-load flow.
7. **M20.5** (project artifact pointers, offer-never-act) and the M22 remnants — small,
   schedulable any time.
8. **M11** stays vision until a real Grafana consumer appears.

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
- **O5 RESOLVED (2026-08-15) — the analyser and `svc-admin-web` complement, and the overlap is
  deliberate.** The analyser is a **dev tool _and_ a production-support tool**; web-admin views **one
  live server** whose log may be rolled or deleted, and suits dev + MCP-driven poking. A low-latency
  production system may have **no admin-web and no MCP at all** — logs are transported to a shared store,
  and **offline analysis across many files is where the analyser shines**. So the topology view and event
  step-through are **replicated into the analyser** (**M21**), because the good view has to exist where
  the logs actually land. Consequence: the analyser needs the **GraphML**, sourced from a file first and
  the server only when one happens to be there.
- **Rendering stays Swing/Java2D — no embedded browser.** Reusing the JS replay engine via JCEF/JavaFX
  WebView would cost a ~100MB native per-platform dependency and destroy the single shaded fatjar that
  `jbang analyser@…` depends on. FlatLaf remains the only runtime dependency; a hand-rolled layered
  layout is the work, with pure-Java ELK as the fallback (spec-graph-replay §3).

- Graph "last occurrence per record" vs "all occurrences" default. (spec: last; expose toggle.)
- spec-closed-loop **O1–O4** (admin endpoint surface · multi-processor discovery · admin auth ·
  brief-file gitignore).
