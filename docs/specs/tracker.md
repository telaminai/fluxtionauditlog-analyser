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

## M20 · Project profiles — ☑ SHIPPED (2026-08-17)
_Brief: `docs/handoff/completed/handoff_16_aug_2026_2.txt` · Spec: `spec-project-profiles.md` (O1–O4 resolved)._
- [M20.5] ☐ **Project artifact pointers — offer, never act** (owner-requested 2026-08-17, revisits O3
  with the surprise removed). The profile MAY carry optional `graphml=<relative>` and
  `logDir=<relative>` / `logGlob=` entries; on open/switch the analyser **asks** — "Open this
  project's topology (and latest log)?" — the same ask-don't-act gate as auto-detect. Missing files
  ignored silently (never-fail rule); a stale graphml is caught by the existing build-mismatch check,
  which is what makes pointing at build output safe. Rationale for the original exclusion stands for
  *unasked* reopening; this is the offered middle path. Bundle synergy: M19's bundle names exactly
  these artifacts — with M20.5 the bundle profile carries them natively instead of via README prose.
- [B-M20-3] ☑ **FIXED (feat/b-m20-3-m26-m27): graph persistence ignores the
  active project tier — ALL new graphs write to GLOBAL.** With maker-fxoc ACTIVE, four named graphs
  (one UI-created, three verb-created) sit in `~/.fluxtion-analyser/config` (`graph.0..3`) while the
  project file says `graph.count=0`. Not verb-specific: the graph-save path routes to the global store
  regardless of the active project — the tier split (B-M20-1's mirror: that bug leaked project→global
  on snapshot; this writes new project-work→global). Consequence: on next launch the active profile's
  empty graph set REPLACES the in-memory graphs, so the user's graphs silently vanish from view (they
  resurrect on switching to no-project — maximally confusing). Fix at the tier-routing seam, not per
  entry point; regression tests for BOTH paths (UI save and dispatcher-created) asserting the active
  profile file gains the graph and global does not. _Fix shipped: GraphPanel/GraphTabs change
  notifications → sync + global save (snapshot-shielded) + debounced project save; ProjectSession
  preSave hook re-syncs live tabs before EVERY profile write (stale flush impossible); quit sequence
  flushes the project and uses the guarded sync (the old raw clear could wipe saved graphs when no log
  was open). 3 regression tests incl. the dispatcher-path one; suite 520 green._
- [M20.4] ☑ **Docs — "Working across projects"**, plus the harness work the screenshots needed.
  New user-guide page (nav + a cross-reference from Sharing setups, which now explains merge-vs-open).
  Says *why* a committed profile is safe: the key is not filtered out, it was **never in the project
  tier**, so no setting or mistake can put it there.
  - `screenshot` gained a **`menu:<Name>`** scope — raised as a judgement call (product surface added
    for docs) and **kept by owner decision**, documented in the user guide as a capability in its own
    right: an assistant can show a user where a control is instead of describing it. It: it opens a top-level menu via the selection manager
    and leaves it open so a native capture includes the popup (`menu:close` restores). The painted
    fallback can never show a menu — a Swing popup is a separate layer, not part of the content pane's
    paint. `setPopupMenuVisible` alone highlights the title without laying the popup out, which looks
    right on screen and is empty in the capture.
  - **`screenshot` now raises the window, and so does the harness.** `screencapture -R` photographs a
    REGION OF THE SCREEN, not a window: a browser sitting over the analyser was captured into a docs
    image complete with its URL bar and personal bookmarks. Caught by reading the image before
    committing, which is the only control that can catch it — rule 1 exists because a text sweep cannot
    see inside a PNG. **Nothing leaked was committed.**
  - Capture geometry is now **fixed** (1680×1050). Without it every run produced differently-sized
    images and the whole asset set churned for no visual change; documentation images being
    reproducible is the reason they are generated rather than taken by hand.
  - The harness **backs off on HTTP 429**. The added captures pushed it past the socket's rate limit and
    an unhandled 429 aborted the run mid-way.

- [M20.3] ☑ **Auto-detect a project beside an opened log** — the M19 zero-setup hook. Open a bundle's
  audit log and the profile committed at its repo root offers to configure roots, event processor and
  graphs. `config/ProjectAutoDetect` holds the policy (7 tests) because *when not to ask* is the hard
  part: no profile above the log, the profile is already the active project, this log was declined
  earlier this session, or the log has no local path at all (an `s3://` object streams to a temp
  directory, and a temp directory is not a project). Declines are per session and per log — a later
  launch is a fair time to ask again, and declining one file says nothing about another.
  Deliberately a **question**, not an action: loading a project replaces your roots and graphs, which
  is not something to do to someone because they opened a file. Nested repositories resolve to the
  nearest profile.
  _Verification note worth keeping:_ I tried to confirm the dialog appeared by checking whether the
  REST socket went unresponsive, reasoning that a modal dialog blocks the EDT. **It does not** —
  `JOptionPane` runs a nested event loop, so the EDT keeps pumping and the socket answers normally.
  The heuristic could never have worked; the owner seeing the dialog, plus a one-line diagnostic
  showing the computed offer, is what actually confirmed it.

- [M20.2] ☑ **Open / New / Save-as / Close project, recent projects, and auto-persist.**
  `config/ProjectSession` owns the lifecycle and is headless (13 tests); the File menu is a thin caller.
  Project items are their own group after the log/graph openers — the items above open a *file to look
  at*, these change *which project's settings are in force*, and appending them to the end would file
  "switch my whole working set" next to "Exit".
  - **Auto-persist rides `onConfigChanged()` and nowhere else.** That funnel is what `source_root` and
    `open {processor}` already go through, so verb-driven edits persist with no second code path —
    the brief's NOTE (b). Verified against a running app: three scripted `source_root` calls landed in
    the project profile.
  - Debounced at 800ms, and the *semantics* are tested rather than the timer: fifteen edits, one write.
    A profile is often a committed file and a legible diff is what gets it reviewed. Leaving or closing
    a project **flushes first** — a debounce window is exactly when the last edit would be lost.
  - Import gains the explicit choice: **Merge (share)** stays additive, **Open as project (replace)**
    swaps the project tier and makes the file active. Conflating the two is what made switching pile one
    setup on the last.
  - _Bug found by driving the app, not by a test:_ one `AppConfig` holds both tiers in memory, so
    `saveConfigQuietly()` wrote the open project's roots into the **global** file. Delete that project
    directory afterwards and the user is left with a stale project's settings as their own, with their
    pre-project configuration gone — the thing the spec promises survives. `ConfigStore.save` now takes
    the global tier to persist, and **`ProjectSession` owns startup activation** so the snapshot is taken
    *before* the profile overwrites it. The first fix was incomplete: it was correct code in the wrong
    order, and only re-driving the app showed it.

- [M20.1] ☑ **Tier the config; load/save a profile with REPLACE semantics.** `config/ProjectProfile` is
  pure and headless; 13 tests written as the spec's two-project acceptance story, because an additive
  implementation would pass a shallower one.
  - **The project tier is FIVE categories, not the seven-category M15 whitelist.** The brief's shorthand
    ("the M15 shareable whitelist") is one category too broad in two places: `ASSISTANT` caps and `LLM`
    provider/model are *shareable* with a colleague but are not *project* facts, and the spec's own tier
    table lists them under global. Shareable and project-scoped are different questions.
  - **Decision recorded (the brief asked for one): `graphmlFile` and `recentGraphml` stay GLOBAL.**
    `graphmlFile` is the topology *currently open* — session state of exactly the same kind as the loaded
    log, and open question O3 deferred coupling log state to a profile precisely to avoid that surprise;
    deciding differently for the graph than for the log would reopen half a workspace on every switch.
    `recentGraphml` is a recent-files list, which the spec's global tier names explicitly. **The boundary
    is unchanged** — nothing here required widening it.
  - Replace is **total over the tier**, not over the categories a file happens to contain: a profile with
    no graphs must leave you with no graphs, or A's graphs leak into B and the pile-up M20 exists to fix
    comes back. The scalars (`selectedEventProcessor`, `searchMavenRepos`, `hiddenColumnsSet`) reset with
    their categories — a stale selected processor names a class that need not exist in the new project.
  - One deliberate exception: a profile naming **no Maven repo** keeps the default rather than emptying
    the list. An empty list silently disables source lookup for every dependency, and "I did not say" is
    not "never search".
  - Startup is global → profile. A moved repository clears the pointer, reports it once in the status
    bar, and the app opens exactly as it did before projects existed.

## M25 · Post-1.1.0 drift fixes — ☑ SHIPPED (2026-08-16)
_Found reviewing v1.1.0 main after the release. Three were pre-existing and mine; one is fallout from
review B1._
- [M25.1] ☑ **The manifest lied about its own verb set.** `ActionServer.handleManifest` hardcoded
  `List.of("aggregate","read","filter","graph","goto","flag")` and never grew, so `/manifest` published
  `verbs` naming six and `schemas` describing thirteen — an internally inconsistent document, and the
  `verbs` field is the one a foreign agent reads. `PromptBuilder.restActionManifest` repeated a
  five-verb list in prose, which is the *only* verb list a copy-prompt session ever sees.
  Both now derive from `VerbSchemas`. `ManifestVerbContractTest` pins all three published lists
  (manifest, copy-prompt, assistant guide) and refuses a literal list in the manifest stanza.
  _Why it rotted: `VerbSchemasTest` and `McpToolsTest` pin the schema set and the MCP tool set, so the
  two places that tell a **foreign** agent what it may call were the two nothing guarded._
- [M25.2] ☑ **`analyser_coverage` was undocumented**, and the assistant guide's destructive paragraph
  still claimed exports "can overwrite a file the app knows nothing about" — untrue since B1, and in
  direct contradiction of the FAQ answer the same commit corrected.
- [M25.3] ☑ **`tools/capture-docs.py` was broken by the export guard** — it set no export directory,
  wrote an absolute `/tmp` path, and reused one filename for every capture, so it failed all three of
  the guard's rules at once. Now points the app at a throwaway export directory, asks for a unique name
  per capture, and copies into `docs/site/assets` itself.
  **The guard was not weakened.** Confinement exists because a verb-driven write is one no human
  approved; regeneration is the script's problem and the script solves it on its own side of the socket.
  Verified end to end: all ten assets regenerate, and the output is pixel-identical to what shipped
  (82 differing pixels of 11.6M — PNG encoding noise), so no image churn was committed.

## M24 · Coverage for a graph — ☑ SHIPPED (2026-08-16, owner-requested)
- [M24.1] ☑ **`coverage` verb.** Which declared nodes never wrote audit output in a run. Came out of the
  POC's 309-node round: the harness emitted chiller readings at `i % 12` against a **24**-wide estate, so
  only 2 of 24 chillers were ever reachable and **54 of 275 nodes never ran** — through a clean build and
  a green suite. Nothing in the tool could have told you.
  `NodeCoverage` is pure (5 tests) and keeps three outcomes apart that a naive implementation collapses:
  covered, never-logged, and **silent by design** (a node with no `auditLog` call can never appear, and
  listing it would be the noise that trains people to ignore the report). The reverse direction —
  instanceIds in the log that the topology does not contain — is reported separately as a **build
  mismatch**, because if that is true no other figure on screen can be trusted.
  Read-only, scans off the EDT, and honest in the result: absence is only conclusive under
  `addEventAudit(TRACE)`, and the payload says so.
  _Measured on the POC: 299 declared, 217 covered, 82 uncovered, ratio 0.726._

## M23 · Explaining what you found — ☑ SHIPPED (2026-08-16, owner-requested)
_M23.1–23.6 explain a **trend**; M23.7–23.9 explain a **single cycle**. The owner's framing: "the graph
plot shows trends, this is a particular issue diagnosis."_

- [M24.2] ☑ **Export guard (review B1).** `screenshot`/`report` are opt-in (*Allow file exports*,
  Settings ▸ Assistant, default off) and confined to one export directory via pure `llm/ExportGuard`
  (relative paths land inside it; escapes and overwrites refused; 7 tests). FAQ + assistant.md rewritten
  to the new truth; `FaqSecurityContractTest` asserts every destructiveHint verb is named in the FAQ's
  security answer, so the promise can't silently drift again. Implemented by the reviewing session.
- [M23.7] ☑ **A finding callout on the topology** (owner). A record's diagnosis is painted bottom-right
  over the graph — note in ink, suggested fix in green, an amber bar down the left edge so it reads as
  commentary rather than more log output. On the canvas, not in a side panel, for the same reason the
  chart's explanation is: this picture gets screenshotted into a ticket, and a diagnosis that lives only
  in the app is gone the moment the image leaves it. Clear of the legend (top-left), the HUD and the index
  overlay (bottom-left). Both themes verified against a running app.
  _Design decision worth keeping: the callout has **no text of its own**. It renders the record's
  `Finding` — the flag's note and fix. One write site, three readers (table note column, callout,
  exported report). The `topology` verb therefore gets `callout` as a **visibility** switch only; adding
  an `explanation` field there would have been a second place to write the same sentence, which is how
  this codebase has produced disagreeing halves three times already._
- [M23.8] ☑ **Export a finding as a PDF** (owner). One document: coloured header, provenance strip
  (record / time / event / log / processor), the explanation, the suggested fix, a picture of the
  topology as currently focused, an optional plot, then the event record and the full node log in
  monospace. Everything is taken from **what is on screen** rather than recomputed — a report assembled
  from a parallel query is a document that can disagree with the app it came from.
  - `report/PdfDoc` — a small dependency-free writer: pages, coloured text and rects, Flate/DeviceRGB
    image XObjects, standard-14 fonts only (nothing embedded, opens anywhere). Exposes **top-left**
    coordinates and flips into PDF's bottom-left space internally, so no call site does that arithmetic.
  - `diff/TextPdf` was **reimplemented on top of it**. Two writers emitting the same format is a standing
    invitation for one to acquire a bug the other lacks. `DiffExportTest` unchanged and green.
  - Node logs paginate rather than truncate — a log cut off at the page break is exactly where the
    interesting line tends to be. Every page carries the record anchor and `n / m`, because printed pages
    get separated from each other.
  - Pictures shrink to fit the space left on the page (down to 260pt, below which a screenshot is
    unreadable and a page break is better). A first draft always pushed the image to a new page and
    produced a third-full page 1 — caught by looking at the output, not by a test.
  - 17 tests (`FindingReportTest`) covering the value type's merge semantics, the coordinate flip, PDF
    string escaping, image embedding, wrapping, pagination and footers.
  - _Two defects found by **looking at the exported PDF**, both invisible to the tests that existed:_
    (a) em dashes rendered as `?` — the standard-14 fonts are single-byte and my fallback replaced
    everything above U+00FF with a question mark, which reads as a corrupted file rather than a
    typographic limit. Common punctuation is now transliterated, and the fonts declare
    `/WinAnsiEncoding` so bytes 0x80–0xFF are not read from a 1980s glyph table.
    (b) a 3-line node log widowed across a page break, leaving one line at the foot of one page and two
    on an otherwise blank next page — a section heading was placed with room for less than it needed.
    Both now have regression tests; the widow test **sweeps** the variable that moves the cursor rather
    than guessing one value, because the first version of it passed with the fix deliberately disabled.
- [M23.10] ☑ **The report renders its own graph views, and two of them** (owner). Screenshotting the
  live panel meant the document inherited whatever zoom, pan and toolbar the user had left on screen —
  and the only way to make it look right was to change what they were looking at. `renderCycleViews`
  builds a **detached** `TopologyCanvas`, sizes it for the page, fits and paints it, then discards it:
  the export is side-effect free and framed for the paper rather than the window.
  Two views, because they answer different questions and the second is the one people forget to ask:
  **the trace** (only the nodes the event reached) and **the whole processor with that cycle lit**. What
  stayed grey in the second is what the event did *not* reach — which is the entire evidence for "the
  stock check never fired". A trace alone cannot show an absence.
  `fitToView(maxScale)` was added rather than a second fit method: on screen the 1:1 ceiling is right (a
  four-node graph blown up to fill a window looks broken); in a fixed report frame nothing else can use
  the space, so it magnifies to 2.2×.
  _Evidence was restructured from three nullable image fields to a `List<Picture>` (heading + caption +
  image) — captions matter here, because the same picture of three lit nodes means "this is all that
  ran" or "this is a filtered slice" depending on which view it is, and those support opposite
  conclusions._
- [M23.11] ☑ **The plot is marked with the record under diagnosis** (owner). A chart pasted beside a
  finding shows a trend but says nothing about *which* point of it the finding is about, leaving the
  reader to join a header timestamp to an axis by eye. `ChartPanel.setRecordMarker` draws a dashed rule
  and a `record #N` label. Deliberately **not** a `ChartNotes.Note`: a note is authored and saved with
  the graph, this is a transient pointer at whatever is being looked at, so it is held in its own field
  and can never leak into a saved graph's annotations. Cleared in a `finally`.
  The meta strip also gained **ANALYSED** — when the report was produced, which on an archived log is
  months from when the event happened. A report carrying only the second reads as if it were live.
- [M23.9] ☑ **`flag` carries a `fix`; a human can write one too.** `flag {note, fix}` — supplying one
  keeps the other (`Finding.merge`), so an agent adding a suggested fix cannot wipe the note that says
  what the fix is for. Records ▸ *Write a finding for this record…* is the human path into the same
  store, and *Export finding to PDF…* the human path out. Verified live: a fix-only re-flag preserved the
  note.
  _Also corrected: `screenshot` was never marked `destructiveHint` even though it writes a
  caller-supplied path unconditionally and can overwrite a file the app knows nothing about. It and
  `report` now are._

## M23 · Charts that explain themselves — ☑ SHIPPED (2026-08-16, owner-requested)
- [M23.1] ☑ **Second vertical scale** (`rightAxis`). One shared range is right until two series differ in
  magnitude, and then it is actively misleading — a revenue line at 2,000 beside a stock level at 20
  renders the stock as a flat smear. Both facts on screen, neither readable, and it *looks* like an
  answer. Two axes only: past two, a height needs a legend to interpret and the chart has stopped being a
  picture. `AxisAssignment` is pure with a `suggestFor` heuristic that deliberately refuses to split a
  bid from an ask (comparable series must stay comparable). 8 tests.
- [M23.2] ☑ **Explanation block and pinned notes** (`explanation`, `notes`, `clearNotes`). Drawn **on**
  the plot rather than beside it, so they survive an exported PNG — a rationale that lives only in the app
  is lost exactly when the picture is shared. Notes are numbered on the chart and listed beneath, anchored
  by `at` (epoch millis) **or** `recordIndex`, because a caller that just found something with `read` has
  the index to hand. Colliding notes stack by pixel column instead of overprinting. `ChartNotes` is pure;
  7 tests.
- [M23.4] ☑ **Right-click a chart to pin a note** (owner) — reading a chart is when you notice the thing
  worth writing down, and an annotation you must leave the chart to add is one you mostly do not add.
  Right-click inside the plot gives *Add note here* (the time comes from the cursor), *Add/Edit
  explanation*, and *Clear notes* — which keeps the explanation, because they are different statements.
  The note picks up the series whose line passes nearest the click, in **both** axes: matching on y alone
  labels the note with whatever series happens to cross that height at some other time entirely.
  Annotations persist with the graph (`GraphSpec` + `ConfigStore`), because a note that does not survive
  a restart is one nobody bothers to write. A pre-M23 config still loads, with empty annotations.
- [M23.5] ☑ **Fixed: clicking a node reset the topology zoom** (owner). Self-inflicted in M22: opening
  the source pane re-fits the canvas (correct — its width changed), but `showSourcePane(true)` re-fit
  **unconditionally**, so with Sync on every node click ran `openSource → showSourcePane(true) → fitToView`
  and threw away the zoom. Now it re-fits only when the pane actually appeared or disappeared.
- [M23.6] ☑ **Fixed: clicking a node in a focused view emptied it** (owner). Two causes. The mouse path
  reset the scope to NODE on a new selection, which under focus collapses the view to a single box; a
  focused width is a width the user *chose*, so it now survives a new selection (unfocused still starts
  the cycle fresh — nothing is hidden, so it is harmless). And the saved zoom/pan was being kept across a
  **re-layout**: a different node set is a different coordinate space, so the old view addressed
  coordinates that no longer existed and left the user staring at empty space. The view is now preserved
  only while the visible node set is unchanged.
  _Also aligned `selectNode` (verb) with `onNodeClicked` (mouse) — they had different scope rules, which
  is how a scripted session and a hand-driven one stop agreeing._
- [M23.3] ☑ **All of it over REST/MCP** — the `analyser_graph` verb carries the new fields, so the MCP
  bridge publishes them with no extra work. Verified by driving a live analyser and capturing the result.
  _One bug caught in review: epoch millis do not fit in an `int`, and my first `anchorMillis` parsed `at`
  with `intOrNull` — silently wrapping to a negative and pinning notes somewhere in 1969._

## M22 · Topology view usability — ◐ IN PROGRESS (36 of 41 shipped, 2026-08-16)
_Open: **22.3** PNG export · **22.6** alternative layouts · **22.11** re-dispatch cause (needs
`UP-FLX-10` upstream, see `docs/proposals/upstream-asks.md`) · **22.19** partial (chips deliberately not
built). Superseded: 22.5, 22.9._
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
- [M22.4] ☑ **Text-size and separation sliders** — shipped, plus a **Show all** reset (clears the
  selection and focus so nothing is dimmed; clicking empty canvas does the same, but only if you know it
  does). `Config.withSpacing()` scales the **gaps only** — growing the boxes would also move the
  label-visibility threshold, which is keyed to box pixels. Label size is deliberately **independent of
  zoom**, which settles M22.5: labels that scale with zoom read well zoomed in and become unreadable
  zoomed out, exactly when you most need to know what you are looking at. The spacing slider reports on
  drag-settle so a 300-node layout does not re-run per pixel. _(Not yet persisted in `AppConfig`.)_
- [M22.5] ⊘ **Text scales with zoom** — DECIDED AGAINST, see M22.4: label size is a slider, independent
  of zoom. _Original note:_ **Text scales with zoom** _(design question, not just work)_ — today the font is a fixed
  screen size and labels disappear below a pixel threshold, so zooming out shrinks boxes around
  constant-size text. Growing text with zoom reads better while zoomed in but re-introduces unreadable
  labels when out. Likely answer: scale within a clamped band, keeping the existing threshold.
- [M22.6] ☐ **Alternative layouts** — the largest item. `LayeredLayout` is Sugiyama; candidates are
  breadthfirst-from-entry and a compact/orthogonal variant. Keep `TopologyLayout` as the output contract
  so the canvas is unchanged.
- [M22.7] ☑ **Split Open Recent** — shipped: *Open recent audit log* and *Open recent GraphML*
  (`AppConfig.recentGraphml`). One list would mean scrolling past logs to find a graph, and picking the
  wrong kind silently does nothing useful. A `onTopologyLoaded` listener records the graph from **every**
  entry point — the toolbar chooser, the recent list and a drop — rather than each caller remembering to.

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
- [M22.47] ☑ **The app is scriptable end to end** (owner) — four new verbs take the set from six to ten:
  **`topology`** (select · scope · focus · scaffolding · step · record · source pane · orientation · fit ·
  showAll, echoing the full cursor state), **`open`** (log / graphml / **processor**), **`source_root`**
  (add / remove), **`screenshot`**. The MCP bridge picked all four up with no work, as
  `spec-assistant-actions-mcp` promised — the verb set is enumerated from `VerbSchemas`.
  Design points worth keeping:
    - the filesystem-reaching verbs live behind their own `AppControl` interface, separate from
      `RenderExecutor`. Render verbs rearrange what is loaded and are reversible; `open` replaces the log
      (losing in-session flags) and `source_root` writes config. They are marked
      **`destructiveHint: true`** to MCP for that reason — calling them reversible because "no file is
      deleted" would be true and useless;
    - **`screenshot` has the app paint itself** rather than asking the OS. A macOS screen grab needs the
      Screen Recording permission, which a headless caller cannot grant; painting has no such gate and is
      deterministic. It cannot draw the native title bar, so the echo carries `windowBounds` for a caller
      that *does* hold the permission to capture the same window with `screencapture -R`.
  `tools/drive-analyser.sh` + `tools/README.md` document the whole loop.
- [M22.48] ☑ **Four bugs the scripted run exposed** — none would have been found by reading the code:
    - opening the source pane left the graph **clipped**: the canvas kept a frame sized for the old width.
      It now re-fits;
    - both source panes were **blank** before anything was opened, which reads as broken rather than
      empty. They now say what they are waiting for;
    - `source: true` showed an empty pane next to a selected node. Asking for the source view is asking
      to see source, so it now opens the selection;
    - **adding a source root did not re-run processor inference**, so source navigation kept reporting
      "no source mapping" with the source sitting right there. Adding a root *is* the statement "the code
      is here", so it re-infers. That in turn exposed the need for `open {processor}`: inference only
      considers candidates in the package of the currently-selected processor, so a differently-packaged
      one is invisible to it.
- [M22.46] ☑ **Docs and in-app help brought up to date** (owner). `user-guide/topology.md` gains the
  exploration model (scaffolding, scope cycle, focus, index), the new open/persistence behaviour, record
  skip + autoplay, the edge-highlight rule and the source split; its screenshot was **three months of
  features stale** — it still showed the scaffolding nodes that are now hidden by default. The bundled
  `help/help.html` had **no topology section at all**, which was the bigger gap: it is the largest feature
  in the app and the in-app guide did not mention it. Screenshots are regenerated by a script against the
  demo fixture, so they can be refreshed rather than re-photographed. `mkdocs build --strict` green. (owner) — `topologyZoom` / `topologyPanX` /
  `topologyPanY` / `topologyOrientation`. Applied **after** a load rather than before, because loading
  fits the graph and would overwrite them, and used **once**: a later load of a different graph fits
  normally rather than jumping to where an unrelated graph happened to be scrolled. Saved on zoom, on
  pan-*end* and on Fit — never mid-drag, which would rewrite the config file hundreds of times per
  gesture. Zoom `0` is the "never saved" marker.
- [M22.45] ☑ **Settings ▸ History clears the new settings too** (owner asked whether it did — it did not).
  *Clear recent files* now also clears the recent-GraphML list and both "last opened" paths — one idea to
  the user ("forget what I have had open"), and leaving the topology quietly remembered would look like a
  bug. A new **Reset topology view** button covers zoom/pan/orientation/spacing/label size, and *Clear
  all* includes both. Tested that the reset restores **every** display default: a reset that leaves one
  behind looks broken.
- [M22.43] ☑ **Dark-theme node fills lifted for contrast** (owner: "getting lost when not dimmed"). M22.28
  fixed the *light* theme's invisible plain node but left dark only a few steps off its canvas, so an
  undimmed node barely read as filled. All five kinds are raised, and **the border moved with them** — at
  `0x30363D` it was darker than the new fills, so lifting the fills alone would have traded one vanishing
  element for another. Both states re-checked by render: undimmed nodes read against the canvas, dimmed
  ones still recede. (owner). A `JSplitPane`'s look-and-feel binds
  Up/Down/Left/Right to move its divider in the **ancestor** input map, and key lookup walks *up* from the
  focused component — so the split added in M22.37 sat between the canvas and the panel and was consulted
  first. Adding a source pane silently broke stepping, and nothing threw. The four strokes are now
  shadowed on the split with a name no `ActionMap` defines, which makes `processKeyBinding` return false
  and keep walking rather than consume the key; drag and F6/F8 still move the divider.
  Pinned by a test that **mirrors Swing's own lookup** — walks from the canvas up and asserts the first
  ancestor that both binds the stroke and has an action is the panel. Asserting the panel's own map (as
  the existing tests did) passes happily while an ancestor eats the key.
- [M22.42] ☑ **The topology showing at shutdown reopens on start** (owner). `AppConfig.graphmlFile`
  alongside `logFile` — the graph is half of the same working state, and having to find it again every
  launch is what stops people leaving it open. Silent when the file has moved: a startup dialog about a
  file you have not thought about in a week is noise, and the tab already says nothing is loaded.
- [M22.39] ☑ **Topology toolbar is controls only** (owner: "too busy and confused"). The two readouts —
  step position and selection scope — moved to the status line, and *Open .graphml…* moved to the File
  menu. A toolbar is for controls: readouts wedged between a slider and a play button are hard to find
  and make the toolbar's width jump as their text changes. The status line is now composed from four
  independently-owned parts (what is happening · step position · selection scope · what is hidden) rather
  than each caller overwriting one string.
- [M22.40] ☑ **File menu: added *Open GraphML…*, renamed *Open from S3…* → *Open log from S3…*** (owner).
  Opening a topology is the same kind of act as opening a log and belongs beside it; the S3 rename says
  which of the two it opens, now that there are two.
- [M22.35] ☑ **Fixed: Show all sometimes left nodes dimmed** (owner). `showRecord` re-shaded
  unconditionally, and the table re-fires its selection for reasons the user never caused — a re-filter, a
  repaint, regaining focus — so the clear was silently undone. Now a repeat notification for the **same
  record** (by identity) neither resets the cleared flag nor re-applies the shading; only a genuinely
  different record does. This is why it was intermittent rather than broken.
- [M22.36] ☑ **Repeated clicks always cycle the scope** (owner: "sometimes misinterpreted as a
  double-click"). Java increments `clickCount` for successive clicks, so click 2 of a fast cycle arrived
  as a double-click and opened source. Double-click activation is **removed from the canvas** — the two
  gestures cannot coexist when one of them *is* repeated clicking. Source navigation is now **Enter** on
  the selected node, the node context menu, or a double-click in the index overlay, all of which are
  unambiguous.
- [M22.37] ☑ **Source opens beside the graph, draggable** (owner; completes what M22.13 began). The
  Topology tab holds its own `SourcePanel` in a horizontal split, sharing the app's `SourceService` so the
  processor selection and roots are the configured ones rather than a second set. Navigating no longer
  switches to the sibling Source tab, which hid the thing you navigated from.
- [M22.38] ☑ **Nested classes resolve to their enclosing file.** Exposed by M22.37: the demo's own nodes
  are `Nodes.QuotePublisher`, and the resolver looked for `Nodes/QuotePublisher.java`. Fluxtion's examples
  group nodes inside a holder, so source navigation failed on exactly the shape the framework teaches.
  Trailing capitalised segments are now dropped in turn, stopping at the package — a lower-case segment
  cannot enclose a class. 8 tests, including that a real file still wins over the fallback.
- [M22.34] ☑ **Show all / background click returns the plain, fully-lit graph** (owner). Clearing the
  selection was not enough: **selection dimming and execution dimming look identical on screen**, so a
  graph with a record selected stayed half-faded and there was no way to see the whole thing plainly. Both
  are now dropped together — selection, focus, emphasis *and* the cycle shading. Nothing is lost: stepping
  (↓/↑), the record buttons, *Whole cycle* and selecting a row all restore the shading, and `stepBy`
  restores it **before** moving so the first keypress after a clear does not silently do half of what the
  second does.
  A background press is also how a **pan** starts, so the clear is held until release and dropped if the
  mouse moves — otherwise dragging the canvas would wipe the shading every time.
- [M22.29] ☑ **Only edges that carried dispatch light up while stepping** (owner). Highlighting every
  edge touching the current node is right with no cycle on screen and **wrong** the moment one is on:
  stepping into `quotePublisher` on an order cycle lit its `QuoteControl` edge, asserting that an operator
  called the service — the one thing that definitely did not happen. An edge is now hot only when **both
  ends ran** (LOGGED or RAN_SILENTLY). MAY_HAVE_RUN is excluded on purpose: it means the log does not say,
  and a highlighted arrow is an assertion.
- [M22.30] ☑ **Bend points clear the boxes beside them** (owner: "the box clips the arrow… they look like
  dependants, not siblings"). `dummyWidth` 8 → 28. A long edge's bend sat close enough to the real node
  next to it that the line appeared to touch the box, and a line touching a box reads as an edge into it.
  _(The layering itself was correct: `riskMonitor` is one layer above `quotePublisher` because
  `quotePublisher` also waits on `spreadCalculator`. Longest-path layering, not a sort-order bug.)_
- [M22.31] ☑ **Index click scrolls the node into view** (owner) — `canvas.centreOn`, pan only. Picking a
  name is a request to go there, not to change how much of the graph you can see.
- [M22.32] ☑ **Topology display prefs persist and are never exported** (owner) — `topologySpacing` /
  `topologyTextSize` in the config file. `SettingsShare`'s whitelist is opt-in in both directions, so
  leaving them out of every `Category` *is* the mechanism; a test asserts they never appear in a share
  file. Same reasoning as the theme: a fact about this screen and these eyes.
- [M22.33] ☑ **One right-click menu on the records table, not two.** M22.22 added a full popup while an
  older three-item `installTableContextMenu` (Diff / Explain / Flag) was still installed — two listeners
  on the same table, and the narrow one is what the owner was seeing. Removed, and *Explain selected with
  LLM* folded into the shared `addRecordActions` so both entry points carry it.
- [M22.24] ☑ **Selection is marked positively, not by dimming alone** (owner: "too subtle"). The clicked
  nodes get a heavy accent ring and an accent-tinted fill; nodes their scope *reaches* get a lighter ring;
  everything else fades harder (0.22 → 0.16). "What did I select" should be answerable by looking at the
  selection, not by comparing the whole graph against itself.
- [M22.25] ☑ **"N scaffolding node(s) hidden" on the status line**, not just beside its checkbox — it is a
  statement about what you are looking at, and half the graph being absent is the most misleading thing
  this view can do quietly. All eight status writes now go through one setter so no caller can drop it.
- [M22.26] ☑ **Collapsible index overlay**, bottom-left of the canvas (owner, from the playground
  reference): sections for Nodes / Events / Services, click to select, double-click to open source.
  Hunting for a box does not scale — zoomed out the labels are gone, zoomed in most of the graph is off
  screen; a list is immune to both. Built from the **full** graph, so it is how you reach a node the
  filters have hidden. Sections start collapsed (expanded, three lists cover half the canvas) and an empty
  section is omitted rather than shown — most graphs export no services.
- [M22.27] ☑ **Node tooltips show the class javadoc** (owner asked whether they did — they did not).
  `Javadoc.forType` is a deliberate text scan, not a parse: the analyser reads generated processors and
  classes whose dependencies are absent, so anything needing a resolvable compilation unit would fail on
  exactly the files most worth reading. Cached per class — a tooltip fires on every hover. 11 tests,
  including that it does not steal a comment belonging to something else.
- [M22.28] ☑ **Node boxes smaller and the plain-node fill visible** (owner). 160×48 → 132×40: the boxes
  carried more weight than the edges, which are what the graph exists to show. And the `NODE` fill was
  `0xFFFFFF` on a `0xFCFDFF` canvas — invisible, **caused by M22.18** moving the canvas to a near-white
  surface; dark was one hex step away too. Both are now a distinct slate.
- [M22.23] ☑ **The side-split divider stays put when you change tab** (owner). A `JTabbedPane` reports the
  **selected** tab's preferred size as its own, and the tabs differ widely (the topology canvas asks for
  640×420, a chart more), so the split re-laid out to suit whichever tab was showing and the divider
  walked about. Fixed by pinning each tab's minimum size, fixing the divider size, and restoring the
  divider location after a tab change.
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

## M27 · Topology focus as a filter context — ◧ M27.1–3 ON BRANCH (docs M27.4 remaining)
_Design: **[spec-topology-focus.md](spec-topology-focus.md)**. Owner correction to M22's focus model:
focus is a **filter operation**, not a view toggle — the focused subgraph becomes "the whole graph" for
every subsequent operation (contexts NEST); node clicks cycle scope within the context; canvas click
clears dimming only and never exits the filter; exit is explicit and stack-shaped (Esc pops, Show all
pops-to-full). Execution shading stays computed on the full graph with out-of-context propagation
indicated at the boundary, never cropped silently. Plus **named focuses** — save/recall/share a context
by name (project-tier, instanceId-based, mismatch-surfaced) — the large-graph payoff. Same seam as
in-flight H4; coordinate._
- [M27.1] ☑ **FocusContext + context stack** in `topology/TopologyFocus` — pure, headless: nesting,
  context-relative scope cycling, boundary detection, pop semantics.
- [M27.2] ☑ **UI rewiring** — canvas-click = clear-dim only, Esc = pop, Show all = pop-to-full,
  breadcrumb in the status line, boundary indication. (Behaviour change to a shipped gesture.)
- [M27.3] ☑ **Named focus** — save/picker/persist (project tier; keep PROJECT_SCOPED at five pinned
  categories — fold deliberately, say so) + SettingsShare + verb alignment: `topology {focus: name,
  pop}` recalls/exits, `{saveFocusAs, rationale}` saves — **agents may create named focuses** (owner
  decision; AV.2 graph precedent: rationale-captioned, replace-by-name, not destructive-hinted, no
  FAQ change). Changelog must call out the gesture change.
- [M27.4] ☐ **Docs** — exploration section rewritten around the filter-context model; harness
  screenshots.

## M26 · Agent-efficiency verbs — ☐ PROPOSED (the analyser computes, the agent concludes)
_Design: **[spec-agent-efficiency.md](spec-agent-efficiency.md)**. The analyser is an un-metered local
JVM holding the whole log behind the index; the agent is token-metered — so any question answerable by
an index/series scan should be a **verb**, not a paged raw read. Every item came from a real friction
in a production-log MCP session (finding "spread > 0.004" took five hand-anchored reads and manual
arithmetic; one scan should answer it). All read-only — no change to the FAQ security answer, and
`FaqSecurityContractTest` must not need touching._
- [M26.1] ☐ **`series` scan** — stats + threshold crossings over any key or formula (STRICT/LOCF),
  filter-scoped, edge-events with a hard cap and an explicit `truncated` flag, off the EDT. Reuses
  `SeriesExtractor`/`Expr`; auto-publishes over MCP via `VerbSchemas`.
- [M26.2] ☐ **Time anchors** — `read {at}` / `goto {at}` resolve epoch millis to the record
  at-or-before (index binary search); kills records-per-minute estimation arithmetic.
- [M26.3] ☐ **`read.fields` projection** — compact `{recordIndex, logTime, values{}}` rows for named
  `instanceId.key`s (wildcards ok; last-occurrence semantics); raw text stays the default.
- [M26.4] ☐ **Echo hardening** — `graph` warns on a `rightAxis`/note series not in the graph; verbs
  name ignored parameters in their echoes. Docs + changelog.

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
