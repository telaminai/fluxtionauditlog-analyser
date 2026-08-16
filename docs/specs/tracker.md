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

## M13 · MCP transport — ◧ M13.1–13.4 SHIPPED (a third door over the same seam)
_**Reviewed, approved and merged to main** (report + review: **[handoff_15_aug_2026_1_report.txt](../handoff/handoff_15_aug_2026_1_report.txt)**).
Stays live only because **M13.5** (resources/prompts, in-app HTTP-MCP) is open; move the milestone to
`completed/` once that lands or is dropped. Review decisions: hand-roll **kept** over an MCP SDK (the era
change was absorbed in ~60 lines — evidence *for* it); `structuredContent` parked with M13.5; move
`ReleaseNotes` to a neutral package as a later non-blocking refactor._
_Design: **[spec-assistant-actions-mcp.md](spec-assistant-actions-mcp.md)**. MCP as the preferred door for
MCP-native clients (Claude Code/Desktop) — one MCP tool per verb over the **same `ActionDispatcher` /
`RenderExecutor`**. Hand-rolled minimal JSON-RPC (no MCP SDK → keeps the near-zero-dep ethos). Not a
replacement: in-process drives the app's own chat; REST stays the universal zero-config door. AV.3's
`VerbSchemas` already provides the shared tool schemas._
- [M13.1] ☑ **`RestEndpointFile`** — app publishes its live REST url+token+**pid** to `~/.fluxtion-analyser/rest-endpoint`
  (mode 600) on REST start, deletes on stop/exit; bridge does a **pid liveness check** before trusting it
  (clean "not running" vs connection-refused) — so a static MCP client config finds the per-run endpoint.
  Publishing is an **opt-in `ActionServer` collaborator** (the path is injected, not baked in) so the
  server started inside unit tests can't clobber a running app's endpoint; exit-time cleanup is
  ownership-checked (pid) so a second instance's endpoint survives. 9 tests.
- [M13.2] ☑ **`McpTools` adapter + `McpBridge` handshake/list** — `analyser.jar --mcp` (**headless-safe**: sets
  `java.awt.headless`, touches no Swing — enforced against the compiled bytecode, not by review):
  hand-rolled **newline-delimited** JSON-RPC stdio returning one tool per verb (six today, incl. `read`)
  **adapted from the shipped `VerbSchemas`** (AV.3) — the same single source of truth as REST
  `/manifest`; no parallel schema holder. **Shipped dual-era** (spec §2.1, v1.2): MCP's current revision
  `2026-07-28` **deleted the `initialize` handshake** this milestone was specced against, so the bridge
  answers both the legacy handshake *and* modern per-request `_meta` versioning + the now-mandatory
  `server/discover`, with `-32022` for unsupported versions. 26 tests.
- [M13.3] ☑ **`tools/call` → REST forward** — map a tool call to `{action, params}`, POST `/action` with the
  token, wrap `ok:true`→text result / `ok:false`→`isError:true` (same actionable feedback). Reuses slice 4.
  The endpoint is re-read **per call**, so a long-lived MCP client survives an analyser restart (new port
  + token) without being restarted itself. Tool vs transport failure kept distinct: a dispatcher rejection
  or a `429` is `isError:true` (retryable, actionable); only an absent/dead endpoint is a JSON-RPC error,
  carrying the "enable it in Settings ▸ Assistant" hint. 12 tests, incl. a live `ActionServer` round-trip.
- [M13.4] ☑ **Docs** — "Connect an MCP client" on the site's assistant page (tabbed config snippets for
  **Claude Code** `.mcp.json` / **Claude Desktop** `claude_desktop_config.json` / **Codex**
  `~/.codex/config.toml`, all three verified against current vendor docs, not written from memory), plus
  how the per-run endpoint is discovered, a troubleshooting list, the capability boundary, and a README
  highlight linking in. `mkdocs build --strict` green locally.
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

## M21 · Topology view + event step-through — ◧ M21.1–21.5 SHIPPED (the web-admin's best idea, offline)
_**Reviewed and approved** ([review_feat_m21-topology.txt](../handoff/review_feat_m21-topology.txt)),
merged from `feat/m21-topology`. **O5 is formally resolved as complement** by §1 of the design spec —
recorded here per the review's F5, so M18.2–18.4 are no longer positioning-blocked._
_Design: **[spec-graph-replay.md](spec-graph-replay.md)**. Render the processor **GraphML** and **step
through events on it** — nodes that fired lit in dispatch order, with their logged values. Resolves O5:
web-admin sees one live server whose log may vanish; the analyser works on **many archived logs with no
server at all**, which is production support. So the view belongs where the logs land. Wired into the
index, filter, table, value graphs and click-to-source — the cross-view coupling a per-server web tab
structurally cannot have. **Swing/Java2D, no embedded browser** (tracker ▸ Decisions)._
- [M21.1] ☑ **GraphML parse + model** — `topology/GraphMlParser` + `ProcessorTopology` (nodes with
  `instanceId`/class/`Kind`, directed edges, parents/children, roots) and `Match` — the **pair-check**
  against the log's `instanceId` set, distinguishing *a node that never fired* from *a topology from a
  different build*. Lifted from `fluxtion-visualiser`'s Java `GraphMlTopologyParser` (our code): same
  document shape and label conventions, IntelliJ logger dropped, model widened for rendering. Lenient
  like the log parser; XXE refused (a `.graphml` can arrive from a shared store). 24 tests, plus
  validation against three real emitted graphs (69/16/**300** nodes). _Resolution via source roots is
  M21.3's UI work — the parser takes text or a Path today._
- [M21.2] ☑ **Layered layout** — `LayeredLayout` (Sugiyama): break cycles → longest-path layering →
  dummy bend points → median sweeps + adjacent-exchange crossing reduction → median coordinate
  assignment → routed polylines, emitting `TopologyLayout` (plain geometry, no Swing). Deterministic by
  construction. 22 tests on **invariants** (every edge points downward, no overlap, same graph lays out
  identically) rather than coordinates. **Two bugs only the real 300-node graph exposed** — an
  intransitive comparator that made TimSort throw, and bend points consuming a full node's width
  (135661px canvas). Both fixed and regression-tested; layout went 2000ms → **13ms** at 300 nodes.
  Real graphs: 69 nodes → 8 layers, 5924×888. ELK not needed.
- [M21.3] ☑ **Topology panel** — `TopologyCanvas` (Java2D: pan, zoom-at-cursor, fit, hover, select,
  tooltips, kind-coloured boxes, arrowheads, viewport culling, label LOD by rendered pixel width) +
  `TopologyPanel` (toolbar, open `.graphml`, orientation toggle, status line incl. the M21.1 pair-check).
  Added as a **Topology** tab. Verified by **rendering offscreen to PNG and inspecting it** — which
  caught three defects a green test suite did not: the layout sheared into a diagonal (systemic drift in
  the coordinate pass, fixed in `LayeredLayout`), arrowheads were painted underneath node boxes (edges now
  stop at the border), and labels were hidden by a zoom-based LOD threshold.
- [M21.4] ☑ **Step-through** — selecting a record lights the nodes that fired, **numbered in dispatch
  order** (green ring + ordinal badge), fades the ones that didn't, and ◀ ▶ walks the cycle node by node
  with that node's logged key/values on the status line. Bidirectional. **Driven by the table's existing
  selection** — one `topologyPanel.showRecord(focus)` in `onRowsSelected`, no second cursor, per the
  binding reuse constraint. Flags instanceIds absent from the loaded topology (build mismatch) inline.
- [M21.5] ☑ **Cross-view wiring** — right-click a node: **open source** (the same `openNodeSource` the
  detail viewer uses), **graph a key** (the *same* `DetailPanel.GraphTargets` instance, now shared by both
  panels rather than duplicated), **filter records to this node** (routed through the existing search
  field so the box shows what is filtered and can be cleared normally), copy instance id; double-click →
  source. Offered keys come from `KV.graphValue()`, so "graphable" means what it means everywhere else.
  6 headless tests on the menu-population rules (panel constructed, never shown).
  _Node → flag not done: flags are per-record and the index has no instanceId lookup, so "flag every
  record where node X fired" needs index work — filter-to-node covers the same intent for now._
- [M21.6] ☑ **Docs — the Topology tab** _(review F1)_ — `user-guide/topology.md`: reading the graph,
  opening a `.graphml`, the **build-mismatch warning**, stepping a cycle, node right-click actions, and
  why the offline case is the one this tab is for. Nav entry + cross-links from graphs / records /
  user-guide index. Screenshot is a **real render** of the canvas over `sample.yml` against a new
  anonymised `demo-marketmaker.graphml` fixture — and a test pins that fixture to the sample log, so the
  page can't quietly start depicting a mismatch. `mkdocs build --strict` green. **Release gate cleared.**
- [M21.10] ☑ **Intra-record step-through** _(brief: [handoff_16_aug_2026_1.txt](../handoff/handoff_16_aug_2026_1.txt))_ —
  one cursor walking record → nodeLog row → next record, the topology following it.
  - [S1] ☑ **`StepCursor`** — pure two-depth model over the filtered record sequence: next/prev with
    entry-as-a-stop, backwards roll-over to the previous record's *last* row, per-cycle accumulation,
    entry-point resolution, and **regime-aware labels** ("row 3/8 (logged nodes)" vs "invocation 3/16")
    so a row count is never read as "the nodes that ran". Repeated rows are separate steps, never
    deduped. 16 tests, driven by both real fixtures. No Swing.
  - [S2] ☑ **Cursor overlay** — current position is a **halo drawn outside the box**, the trail a
    weaker one, the entry a dashed one; the node keeps its own execution border and fill underneath.
    Recolouring the border (the obvious implementation) would hide what the log establishes in order to
    show where you are standing — two different questions, both needed at once. Verified offscreen.
  - [S3] ☑ **Wiring** — cursor walks the **filtered** view (`RecordSource` over the table's visible
    rows, so stepping honours the shared filter); `[` / `]` keys (F3 left alone — one key meaning two
    kinds of "next" is worse than a second pair); rolling into another record re-shades the canvas and
    moves the table selection; the row under the cursor is highlighted in the detail viewer's
    `nodeLogs` text **by occurrence**, so a node logging twice highlights the right line. A guard flag
    stops the table ⇄ cursor sync looping.
  - [S4] ☑ **Docs** — `topology.md` "Step through a cycle" rewritten to the two-depth walk (entry as a
    stop, `[`/`]`, halo-over-shading, filtered sequence, detail sync) with the regime readout and the
    logs-twice rule called out; spec-graph-replay §4 records the finalised granularity. `--strict` green.
- [M21.7] ☐ _(later)_ server-sourced GraphML via `GET /api/processors/{group}/{name}/graphml` (needs M18.1).
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
- _M21.1–2 carry the risk and are pure logic — front-loaded deliberately, testable before a pixel exists._
- Open: **O1** GraphML attribute shape (read the visualiser's parser) · **O2** logRecord sink/transport
  config — **does not gate M21**, only M21.6 and M18.2 · **O3** tab vs dockable split · **O4** very large
  topologies (elision/clustering) — defer until a real graph hurts.

## M22 · Topology view usability — ☐ PROPOSED (parity with fluxtion-visualiser's exploration model)
_Owner-specified batch (2026-08-16), ported from what fluxtion-visualiser already does well. The
topology renders correctly but does not yet let you **explore** — these are the affordances that turn a
picture into a tool. Ordered by value/effort; 22.1 and 22.2 are the ones that change daily use._
- [M22.1] ☑ **Hide framework scaffolding** — shipped: a toolbar checkbox (off = hidden, the default).
  **10 of 20 nodes in the demo graph
  are scaffolding** (`context`, `clock`, `nodeNameLookup`, `callbackDispatcher`, `subscriptionManager`,
  `serviceRegistry`, `eventLogger`, `ClockStrategyEvent`, `EventLogControlEvent`, `ServiceListener`), so
  the user's actual graph is a third of what is drawn. Detection must handle **both** label shapes seen in
  real graphml — a package-qualified `class:` (`com.telamin.fluxtion.runtime.…`) and a bare simple name —
  plus EVENT nodes whose `class:` is absent entirely, which need matching by id.
- [M22.2] ☑ **Selection-driven focus** — shipped. `TopologyFocus` (pure, 15 tests) holds the cycle
  *node → +neighbours → +all routes → whole graph → node*; clicking the same node widens one step,
  Cmd/Ctrl-click (Cmd on macOS — plain Ctrl-click is the popup trigger there) adds to the selection, **F**
  or the Focus button hides everything outside the scope. Unfocused, the scope is **dimmed rather than
  hidden**: a node you cannot see reads as a node that is not there, which is the one confusion this view
  exists to prevent.
  Three things that had to be right, each a place the obvious implementation is wrong:
    - **classification is pinned to the full graph** (`setClassificationTopology`). `classifyCycle`
      reasons from parents and reachability, so filtering the drawn graph would change what the view
      claims about the same log — ticking a checkbox could turn RAN_SILENTLY into MAY_HAVE_RUN;
    - **every graph question uses the full topology** — entry-point resolution, build-mismatch matching,
      "not in this topology", parent/child counts. What is drawn is a display choice; what the graph says
      is not;
    - **filters keep the zoom/pan and selection** (`setTopology(view, keepView)`); re-fitting on each
      toggle discards where the user had navigated to, which defeats the exploring.
- [M22.8] ☑ **Shape carries the kind** — event = stadium, exported service = hexagon, everything that
  computes = rounded rect. Fill alone failed in greyscale, on a projector and for colour-blind readers,
  and the three roles are the first thing you need to read. _(Answers "how do you render exported
  services": they are the hexagon — the only one in the demo graph is the framework's `ServiceListener`,
  which 22.1 hides, so a user-authored `@ExportService` in the fixture would show it better — see 22.10.)_
- [M22.9] ⊘ **Source without losing the graph** — SUPERSEDED by M22.13, which answers the layout question
  it left open (the source view splits Processor/Node rather than the topology tab embedding a viewer).
  _Original note:_ **Source without losing the graph** — double-click currently opens the Source tab, which is a
  *sibling* of Topology in the same tabbed pane, so navigating to source hides the thing you navigated
  from. Options: split the side pane (graph above, source below) when navigating from the topology; or
  give the Topology tab its own embedded source view. Owner-raised; needs a layout decision before code.
- [M22.10] ☑ **A user-authored exported service in the demo fixture** — shipped. `QuotePublisher`
  implements `@ExportService QuoteControl` (`suspendQuoting`/`resumeQuoting`, both `void` per
  `claude.txt`), so the compiler emits a **separate `QuoteControl` EXPORTSERVICE node** with an edge into
  `quotePublisher` — an exported service is its own entry-point node, not a marking on the implementing
  node. Two defects came out of using real artefacts: the generator was copying a **stale** graphml from
  `target/generated-resources` (the plugin writes back into the source tree), and `EntryPointResolver`
  knew only the fully-qualified signature spelling, so every exported call resolved to no entry point.
- [M22.3] ☐ **Export the view as PNG** — reuses the offscreen render already used to verify the canvas;
  pairs with the existing graph/record exports.
- [M22.4] ☐ **Text-size and separation sliders** — separation re-runs `LayeredLayout` (`nodeWidth`,
  `siblingGap`, `layerGap` are already config); text size is a canvas concern. Persist in `AppConfig`.
- [M22.5] ☐ **Text scales with zoom** _(design question, not just work)_ — today the font is a fixed
  screen size and labels disappear below a pixel threshold, so zooming out shrinks boxes around
  constant-size text. Growing text with zoom reads better while zoomed in but re-introduces unreadable
  labels when out. Likely answer: scale within a clamped band, keeping the existing threshold.
- [M22.6] ☐ **Alternative layouts** — the largest item. `LayeredLayout` is Sugiyama; candidates are
  breadthfirst-from-entry and a compact/orthogonal variant. Keep `TopologyLayout` as the output contract
  so the canvas is unchanged.
- [M22.7] ☐ **Split Open Recent** — separate *Recent audit logs* and *Recent GraphML*; needs a second
  recent list in `AppConfig` (the existing one is log-only) and menu wiring.

### Added 2026-08-16 (owner, from the playground visualiser reference)
_Reference implementations reviewed: `mongoose-plugins/service/svc-admin-web/src/main/resources/web`
(`visualiser/scaffold-filter.js`, `replay/replay-engine.js`, `replay/eventlog-parser.js`) and the
playground's `audit/step-through` screen. Findings: our `Scaffolding` is already a **superset** of
`scaffold-filter.js` (which matches by class name only, so framework EVENT nodes stay visible); our
`StepCursor` is a superset of `replay-engine.js` **except** it has no play/pause autoplay; and **neither
reference handles re-dispatch at all** — their replay engines are record→step only, so M22.11 is new
design, not a port._

- [M22.11] ☐ **Re-dispatch (`processReentrantEvent`) — show the cause.** A node can raise an event on its
  own graph. Measured against generated code and a real fixture: `afterEvent()` publishes the audit record
  **before** `dispatchQueuedCallbacks()` runs, so a re-dispatch is **not** extra rows on the causing
  cycle — it is a **separate `eventLogRecord`**, same thread, normal `eventTime` (an exported call is
  stamped `-1`), carrying nothing that says it came from inside. The graphml is no help either: the raised
  event is an ordinary EVENT node with no edge from the node that raises it. So linking effect to cause
  needs the **processor source** (which node calls `processReentrantEvent`/`processAsNewEventCycle` with
  which type) plus record adjacency. Without source, claim nothing. Fixture: `riskMonitor` →
  `RiskBreachEvent` → `breachHandler` in `demo-quote-audit.yaml`, pinned by two tests.
- [M22.12] ☑ **Node-log panel: Logical and Text views** — shipped. Logical gives each node a block with its
  values on their own lines, in dispatch order; Text is the raw YAML, kept one click away because it is the
  **evidence** for anything Logical re-arranges. Framework keys (`thread`, `method`) are **muted, not
  hidden** — they are the marker that says the record is traced, so dropping them would hide the regime.
  Layout is separated from colouring (`LogicalLogView.Layout`) because the block offsets drive
  click-to-source and the step cursor's highlight: an off-by-one opens the wrong file rather than merely
  looking wrong. 9 tests.
- [M22.13] ☑ **Source view: three modes — Processor · Node · Split** — shipped. `SourcePanel` now holds two
  independent panes, each parsing what it shows into its own `EventProcessorModel` so Ctrl-click navigation
  works from either half. Navigating to a node while in Processor mode **promotes the view to Split**
  rather than replacing what you navigated from — the whole point being that the call site (and the guard
  above it that decides whether the node runs) and the method body need to sit still at the same time.
- [M22.14] ☑ **Source view fills the viewport with wrap off** — `getScrollableTracksViewportWidth()`
  returned the wrap flag, so the pane sized to its longest line and the rest of the viewport showed the
  scroll pane's background. Now tracks the viewport whenever the text is narrower (and the same
  vertically); long lines still scroll horizontally.
- [M22.15] ☑ **"No source to show" instead of a 5%-tall empty editor** — the empty state now fills the
  panel and names the roots actually searched plus `File ▸ Settings… ▸ Source roots ▸ Add…`. An empty
  editor says "nothing here" when the truth is usually "looking in the wrong place".
- [M22.16] ☑ **Window-span selector moved to the left** of the time-range bar (owner).
- [M22.17] ☑ **Collapsible event-types panel behind a vertical nav bar** — shipped. `NavRail` draws
  bottom-to-top labels as an `Icon`, so hover/pressed/selected stay FlatLaf's to render. Collapsed state
  persists (`AppConfig.eventFilterCollapsed`) — a window that forgets its layout teaches people not to
  adjust it. **Deviation:** Columns is *added* to the rail as a popup rather than *moved* off the menu bar;
  removing a menu that people already know costs more than the duplication saves.
- [M22.18] ☑ **Panel surfaces must read as surfaces** — shipped. `UiTheme.surface()` / `surfaceEdge()` /
  `applySurface()` give the source, record-detail and topology panels one content surface with a hairline
  edge. The canvas's old light value (`0xF6F8FA`) sat within a shade of FlatLaf's panel grey and lost its
  own boundary. `applySurface` paints the **viewport** as well as the view: the viewport is what shows
  through around the margins and during a resize, which is what made a short document look like a strip.
- [M22.19] ◐ **Step-through header, playground-style** — header (`event 8 / 10 · step 2 / 5`), whole-record
  skip (`◀◀`/`▶▶`) and autoplay shipped; autoplay drives the same `stepBy(1)` path as ↓ and stops at the
  end of the log rather than sitting on the last row. The compact wording is deliberately terse: the full
  regime-aware form ("row 2 / 5 (logged nodes)") stays in the status line, because two differently-worded
  claims about the same position is how the regime distinction gets lost.
  **Event-kind chips NOT built, on purpose:** the app already has an event-type filter (the panel M22.17
  just made collapsible). A second one in the topology tab would be a second source of truth for which
  records are in scope — the exact failure `spec-graph-replay` §6 rules out for record selection. If chips
  are wanted, they should *render* the existing `FilterState`, not hold their own.
- [M22.22] ☑ **Records: right-click on the table; Columns off the menu bar** (owner). The record actions
  (flag, show-flagged-only, clear, copy as YAML, diff, export CSV/YAML) plus the column chooser are now on
  the table's context menu, and **Columns is no longer a top-level menu** — the nav rail and the
  right-click both reach it, and you are at the table when you notice a column is missing. The popup is
  rebuilt per click so enabled states match the selection (Diff needs exactly two); right-clicking outside
  the selection selects the row under the cursor first, as elsewhere. One shared `addRecordActions` builds
  both entry points so they cannot drift, and the *Show flagged only* checkboxes are kept in step.
- [M22.21] ☑ **Control clusters tint away from content** — the time-range bar is a *control*, not a
  document, and shared the panel background with everything around it. `UiTheme.controlSurface()` shifts
  the **theme's own** `Panel.background` (lighter in dark themes, darker in light) rather than naming a
  colour, so it holds for Light/Dark/IntelliJ/Darcula and anything added later. Theme switching re-applies
  it: `updateComponentTreeUI` preserves an explicitly-set colour, so a stale tint would otherwise survive.
- [M22.20] ☑ **Code type** — Swing's logical `Monospaced` is a per-platform alias that lands on Courier on
  some machines, so `UiTheme.mono()` picks the best installed family (JetBrains Mono → SF Mono → Menlo →
  …) and `applyReadingRhythm()` opens line spacing to 0.18. Swing sets lines at the font's own leading,
  which is most of why dense key/value output reads as a block next to the same content on a web page.

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
1. ~~**M13.1–13.4** (MCP bridge)~~ — **shipped** (branch `handoff/15_aug_2026_1`, pending merge): every
   MCP-native agent can drive the analyser with zero prompting; AV.3's schemas fed it directly.
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
