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
named focuses · M28 conditionals + rolling windows + guides/bands · M29 external series (core) ·
M30 rolled log sets · M31 log-source plugins (core) · M32 marker series · M33 investigation reports
(core) · M34.0–.3 source adapters (SPI, degradation, format spec + conformance suite) · M35 log +
graph lifecycle (all eleven) + §E provenance · refinement rounds 2–13 · assistant-vocabulary
follow-ups. _(Polish H1, 2026-08-25: verified every section left in this file has open items; the
archaeology the 2026-08-17 brief asked for had been done by the per-merge tidies.)_

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
  Review endorsed (all 7 re-derived correct — `completed/review_formula_golden_fixtures.txt`). **G1 and G2
  closed** (58879d7): an empty `EXPECT` now requires a declared `expectEmpty: true`, and every
  fixture runs through BOTH engine arms — `SeriesExtractor.extractExpr` *and* `SeriesScan` — with
  agreement asserted. Closing G2 immediately caught the `series` verb answering from stale carries
  (the 1.5.0 headline fix): the corpus's first scalp, on the day the cross-path check landed.
  Still open: **N1** (duplicate metadata key assertion — a doubled `expr:` line silently takes the
  last), plus one taxonomy add: a `min(4, 2)` / clamp-idiom fixture pinning the M28 compatibility
  guarantee.

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

## M18 · Mongoose server link — ☒ **CLOSED 2026-08-22 in favour of the agent-brokered dev loop**
_Superseded by **[spec-agent-brokered-dev-loop.md](spec-agent-brokered-dev-loop.md)** (ACCEPTED v2),
assessed in [review_spec_agent_brokered_dev_loop.txt](../handoff/review_spec_agent_brokered_dev_loop.txt).
The deciding argument is no longer between two specs: **M34.0 passed its gate and M34.1's ordering
slice shipped**, so M18 as specced would contradict merged code — the analyser cannot be made
engine-agnostic and taught one server's REST API in the same quarter._

_**Closed as "not the analyser's questions"** (acceptance 5): **M18.1** link/status · **M18.3**
audit level · **M18.3a** the missing `GET` companion · **M18.4** dev restart · **M18.4a** why
`start`/`stop` are commented out · **M18.6** source+graphml over REST · **O3** admin auth. They move
to a Mongoose-side MCP tool, in the repo whose release cadence owns them._

- [M18.2] ⏸ **PARKED, not deleted** — log discovery (*point the analyser at your running system*).
  **Revival trigger, stated so it is falsifiable:** revive as an onboarding affordance only if
  evidence shows no-agent developers bouncing off the export step. Until then the agent exports and
  calls `open {log}`, which shipped.
- [M18.5] ☐ _(unchanged, still deferred)_ deploy-jar-and-restart; non-loopback/production posture.
  Now the natural home of **D-B7's paid production MCP** — enforcement server-side, since a licence
  check inside a source-available client is an `if` anyone can delete.
- **O5 — RESOLVED 2026-08-22 and CLOSED** (spec §G). Under M18 the overlap with `svc-admin-web`'s
  audit viewer was a positioning problem settled by assertion. Under the adopted design it is
  structural: the server's viewer is the **live, in-situ, one-server** surface; the analyser is the
  **deep** one, fed by exports and adapters across many logs and systems. They are not fed by the
  same thing, so they do not compete.
- **Before any cross-repo work starts:** the loop is a contract and gets a **conformance harness**
  (spec §H) — a scripted end-to-end of steps 3–7, homed in the **M19 bench**, so a break fails in the
  owning repo rather than in a user's session. The three-repo dependency is acceptable *because* of
  this and not otherwise.

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
- [M19.6] ☑ **The loop's conformance bench — §H's home** _(DONE 2026-08-25, merged to main)_ —
  `tools/bench/loop-bench.py` plays the agent of dev-loop §C3 steps 3–7 (glob the registry → pick →
  export log + GraphML → `open {log, graphml, provenance}` + `source_root` → assert from `context` /
  `coverage` / `topology` that the loop closed), PASS/FAIL per step, non-zero exit on failure.
  `mongoose-stub.py` plays a server reduced to the contract (UP-MNG-01 registry file, mode 600; the export
  endpoints) from the in-tree demo set, so the bench runs today with no Mongoose — and pointed at a real
  `~/.mongoose/servers/` it is the acceptance test for UP-MNG-01/02. Assumptions (auth header, files
  listing shape) flagged in its README, not baked in.
- [M19.7] ☑ **An agent-driven fresh start** _(DONE 2026-08-25, merged to main; review_feat_m35_project N2)_
  — `analyser --rest` starts with the REST transport on (persisted, and stdout says so) and skips the
  first-run modal that otherwise blocked an agent on a never-configured machine before the socket existed;
  the MCP bridge's "not running" error names the command. Exercised by the bench's `--launch`, which
  starts a fresh install in an isolated home.
- [M19.8] ☐ **The bench in CI** — the analyser is Swing, so the job needs a display (`xvfb-run` on the
  Linux runner) plus the stub; not verified from a Mac, so recorded rather than claimed. Until then the
  bench is run locally before any of the three repos' loop code changes.
- [M19.9] ☐ **A headless test for the launch arguments** — `Main` now strips `--rest`, rejects an
  unknown flag AFTER stripping, and lets a log path fall through. That is pure logic with three
  behaviours and no unit test (rule 4). The loop bench covers it end-to-end, but only where a jar, a
  JVM and a window exist, so a headless CI run would not catch a regression. From
  `review_feat_m19_bench.txt` F3.
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

## M29 · External series — ◧ SHIPPED 2026-08-18 (archived; M29.5 optional embed open)
_M29.1–.4 shipped, reviewed and merged — full record in **[completed/tracker.md](completed/tracker.md)**.
Design: **[completed/spec-external-series.md](completed/spec-external-series.md)**._
- [M29.5] ☐ *(optional, owner decides)* **`embed: true`** — carry small series inside the saved graph
  for fully-portable sharing (D-F5's alternative).

## M31 · Log-source plugins — ◧ SHIPPED 2026-08-18 (archived; example reader is cross-repo)
_31.1–.3 + the plugin-author guide shipped, reviewed and merged — full record in
**[completed/tracker.md](completed/tracker.md)**. Design: **[completed/spec-log-source-plugins.md](completed/spec-log-source-plugins.md)**._
- [M31.4r] ☐ **Out-of-tree example reader** — lives in the playground repo (this repo cannot ship it);
  also the ONE M31 acceptance only a real jar can settle (two conflicting plugin jars coexisting).
  The in-tree toy reader in ReaderSpiTest is the seam proof meanwhile. Cross-repo slice.
- [M31.5] ☐ *(owner decision)* **Separate `analyser-reader-spi` artifact** — needs a multi-module
  build; deferred in review (D9). Plugin authors compile against the fatjar meanwhile.

## M33 · Investigation reports — ◧ CORE SHIPPED 2026-08-20 (archived; M33.5 gated)
_M33.1–.4 shipped, twice-reviewed, owner-eyeballed and merged — full record in
**[completed/tracker.md](completed/tracker.md)**.
Design: **[completed/spec-investigation-reports.md](completed/spec-investigation-reports.md)**._
- [M33.5] ☐ **Fold M12.1's fix-brief onto the model** (D-I6) — after the closed-loop precondition
  (journal ↔ audit-log pairing) resolves, not before. The brief inherits D-I3a for free when it lands.
- [M33.6] ☐ *(owner call, from the eyeball pass — E4)* **chooser dialog for external marker CSVs** —
  markers are verb-first by design; *File ▸ Add series from CSV…* covers series only. Decide whether
  markers deserve the same dialog before advertising the CSV source to non-agent users.

## M35 · Log + graph lifecycle — ☑ SHIPPED 2026-08-25, all eleven slices + §E (archived)
_Everything in [completed/tracker.md](completed/tracker.md) ▸ M35. Nothing open._

## M36 · Start page — ◧ **.1–.4 MERGED to main 2026-08-25** (the empty state, doing a job); .5 docs page open
_Design: **[spec-start-page.md](spec-start-page.md)**. The owner's four sections — what it does, how
it helps, where it fits in the cycle, who you are — placed where they cost a returning user nothing._
_The framing that shaped it: **the analyser already HAS a start page**, and it reads "No log loaded —
File ▸ Open, drag a file in, or File ▸ Open from S3". Honest and useless. Meanwhile "what is this
for" lives in HelpPanel, a static page nobody opens before they have a problem. So this is not a new
surface; it is the empty state finally earning its keep, and the test of the design is that opening
a log from the command line means never seeing it._
- [M36.1] ☑ **The state, not a screen** (D-S1) — occupies the main area whenever no log is open;
  a log replaces it, closing one brings it back, `Help ▸ Start page` recalls it. No splash, no modal,
  no dismissal to remember.
- [M36.2] ☑ **Every section ends in an ACTION** (D-S2) — each of the four sections links into the
  BUNDLED DEMO LOG, so every button works with no configuration, no server and no API key. A start
  page whose buttons need setup first is one that lies on first contact.
- [M36.3] ☑ **Three audience lanes, phrased as the user's own sentence** (D-S3) — "I am writing the
  graph", "something is wrong in production", "I want the numbers out". **Never a question the app
  asks**: people recognise their situation faster than they classify themselves, and nothing is
  remembered or personalised.
- [M36.4] ☑ **No feature list** (D-S4) — a page that enumerates capabilities is stale the release
  after it is written, and it is the first thing a new user reads, so its errors are the ones they
  carry. Three problems and three lanes; anything version-specific belongs in the release notes.
- **O-S1 RESOLVED — bundle a demo SET, not a log.** `DemoAssets` ships the walkthrough log, a traced
  log, a series log and the GraphML in the jar and unpacks them to `~/.fluxtion-analyser/demo`. One
  log was the wrong answer: three of the four sections ask a question one log cannot answer (coverage
  needs a TRACED run; a chart needs a series; step-through needs the graph).
- **O-S2 RESOLVED — the whole LEFT COLUMN, tabs kept.** "Records pane only" was tried first and
  failed its own acceptance: the page was clipped and a scrollbar appeared, because the detail pane
  below it has nothing to say with no log and was still holding half the height. The right-hand tabs
  stay, so the product's structure is still visible.
- **O-S3 HELD, and it bit.** `DemoAssetsTest` asserts the shipped demo carries no real names — and
  caught a vendor-domain copyright header in generated source on its way into the jar. That term is
  now the FOURTH in rule 1's sweep, and the four `examples/` files carrying it are clean.
- Closes **review_feat_m35_project N2** ("the first-run modal blocks an agent-driven start
  entirely"), together with M19.7. M19.7 suppressed the modal for `--rest`; M36 removed it outright,
  because the same three objections apply to the human at a fresh install. The `--rest` stdout note
  M19.7 added stays — see `MainFrame.showFirstRunSettingsIfNeeded`.
- [M36.5] ☑ **The start page is documented, with a generated screenshot** _(2026-08-25)_ —
  `getting-started.md` is rebuilt around it: the Quick start is now "run it, click Open the demo log",
  and the sentence promising that a first run opens Settings is gone (it had been false since M36).
  `capture-docs.py` takes the shot by CLOSING the log — the page is a state, so the only honest way to
  photograph it is to be in that state. Doing so exposed that `launch()` opens the log from the command
  line while `seed()` does not, so the first run of the new step photographed an empty analyser for five
  light-theme shots; the harness now reopens the log and waits for it.

### Rule 1 — owner decisions (raised M36, sharpened by the polish round)
- ☑ **The sweep is extended to four terms, and its exemption is written down** _(2026-08-25)_ — run
  literally the sweep can never be empty: `CLAUDE.md` states the rule and `ONBOARDING.md` restates it,
  so both must spell the terms. Two sessions had been reporting "sweep clean" under different unwritten
  exemptions. Rule 1 now says the exemption out loud and gives the form that needs no remembering —
  `git ls-files | xargs grep -ril …` minus those two paths — which must print nothing. The mechanical
  cost is real and is stated with it: a swept term may not be spelled anywhere else, **including in
  prose about the rule**, so five documents were reworded to describe the fourth term rather than
  print it, and `DemoAssetsTest` now parses all four from the sweep line at runtime instead of
  concatenating one locally.
- ☑ **The four `examples/fixture-generator` files are clean** _(2026-08-25)_ — the compiler-emitted
  block carried a personal address on a vendor domain inside an "all rights reserved / confidential,
  delete this file" notice, on files published in a public repo, where the notice was both a leak and
  untrue. Header removed from all four. This also closes the live hazard the polish round exposed:
  `tools/capture-docs.py` adds that directory as a source root and `source-navigation.png` renders one
  of those files, so the docs screenshots were one scroll position away from publishing it.
- ☐ **Ask upstream whether the compiler still emits that header** — every generated processor in every
  user's repo carries it. An upstream ask, not an analyser one.

## M37 · Loaded panel — what is in force, stated in one place — ☐ SPEC'D 2026-08-26 (owner-requested)
_Design: **[spec-loaded-panel.md](spec-loaded-panel.md)**. The owner's ask: a tab on the west rail that
shows the loaded graphml(s), the event processors (Java classes), the audit logs, and the project's name
and file location — "currently it is not clear what is loaded in the current project"._
_The framing that shaped it: the app already knows all of this and says it to **agents** (`context`), while
the human gets five scattered fragments (title, status line, Topology header, two dialogs). So the panel
is `context` rendered for the human — one model, two readers — and it goes **before M20.5**, whose
project pointers are invisible without it. The 2026-08-26 graph-loss defect is the motivating case:
saved graphs fell 6 → 1 and no surface showed the count._

- [M37.1] ☐ **`context` parity** — add the facts the panel needs that `context` lacks (project file
  location, source-root tiers, rolled-set members, per-processor source resolution); parity test scaffold.
- [M37.2] ☐ **The panel** — `NavRail` toggle "Loaded" beside *Event types*, persisted, default shown once;
  five sections (Project · Audit log · Graph · Processors · Source roots), every empty state a sentence.
- [M37.3] ☐ **Provenance + reveal actions** — per-row where-from (`OpenRequest` provenance, the tiers);
  actions are copy-path / show-in-folder / go-to only — a test proves nothing on the panel mutates.
  The pairing verdict is a row (applies · declared/inferred · opened beats supplied).
- [M37.4] ☐ **Lifecycle wiring** — re-renders on the M35 events, no polling; the open→graph→project→close
  sequence test.
- [M37.5] ☐ **Docs page + generated shot**; CHANGELOG; spec → SHIPPED.

**Owner calls before .2:** the name (*Loaded* / *Session* / *In force*); stacked with Event types or
exclusive; whether the start page shows the PROJECT section inline (proposed: no).

## M34 · Source adapters — ◧ **.0–.3 MERGED to main 2026-08-25** (format spec + conformance suite published); .4/.5 open
_Design: **[spec-source-adapters.md](spec-source-adapters.md)**. Owner ask: make the app general
purpose by identifying the Fluxtion-specific elements and making them plugins — then write adapters
that transform LangGraph/Temporal runs into the audit-log format and get the whole toolset for free.
The model is not Fluxtion-shaped: *an ordered sequence of cycles, each triggered by an event, each
recording which components ran in what order and what each logged, with a static graph alongside*.
M31 made CONTAINERS pluggable; M34 makes the **engine** pluggable._

_**The asymmetry is the finding, and it is a first-class decision.** The audit log generalises cleanly;
the topology does not. Some engines can hand over a **declared** graph (LangGraph); others only what
was **observed** (Temporal has no static workflow structure — but has native replay, which fits
replay-diff better than Fluxtion does). **Coverage is "declared minus observed"** — with no declared
set there is nothing to subtract from, so the feature that found the POC's 54 dead nodes cannot exist
on such a source. D-A1: adapters declare what they can supply and the core degrades LOUDLY per
capability; inferring a declaration from observed history is rejected because it always reports 100%.
D-A2: a graph is DECLARED or INFERRED and the view says which. D-A5 is the real test — GraphML moves
out of the core and becomes what the Fluxtion adapter uses, because an SPI its own built-in cannot use
is decoration. D-A6: publish the format openly and hold the NAME; the defensibility was never the
schema — it is the reference tool and the disciplines in it._

_**Review amendments (v2):** the spec generalised the record and the graph but **not the ORDER** —
and `nodeLogs` order IS dispatch order in Fluxtion, consumed as meaning by step-through, route
escalation and the M21 classification. LangGraph super-steps, Temporal activities and OTel spans
are concurrent, so an adapter would have to INVENT a total order with nothing on screen marking it
as invented. **D-A1a** adds `ordering: TOTAL | PARTIAL` plus a per-cycle concurrency marker, and
consumers qualify loudly — UP-FLX-11's lesson one level up. **D-A3** gains the attribution rule (a
value appears under a component only if that component produced or changed it — a LangGraph state
channel is SHARED, and broadcasting it would make series into cross-component duplicates that
still "work"). **D-A6**'s fixtures pin SEMANTICS not layout. Graph provenance moved onto the
returned `SourceGraph` because availability is per SOURCE, not per adapter._
- [M34.0] ☑ **The LangGraph spike, against CURRENT code** — **DONE 2026-08-20, the gate OPENS**
  (`docs/handoff/completed/report_m34_0_spike.txt`, code `tools/spikes/m34-langgraph/`). Every verb worked on a
  LangGraph run with zero analyser changes: 720 records, series/crossings/aggregate/read/coverage/
  topology/graph all live. **Two findings change M34.1.** (a) D-A1a is now OBSERVED, not inferred:
  *all 720* records contain a concurrent super-step, and step-through walks them in stream-arrival
  order while the topology paints dispatch badges — identical presentation to a Fluxtion log, where
  the same badges are meaning. Ordering moves from amendment to **precondition**. (b) coverage's
  figures were right and its reading was false — `__start__`/`__end__` counted as uncovered, so the
  declared graph needs a **structural/scaffolding flag** or an adapter must not emit pseudo-nodes.
  D-A3 needs nothing: LangGraph's per-task `result` IS the attribution rule. And the analyser caught
  the translator's invented node unprompted (`loggedButNotInTopology`), declaring every other figure
  suspect — the honesty disciplines transfer to a foreign source unmodified.
- [M34.1] ☑ **`RunAdapter` SPI** *(MERGED to main 2026-08-25)* — _ordering slice DONE 2026-08-22_: `Capabilities` gained
  `Ordering {TOTAL|PARTIAL}` **additively** (the 3-arg constructor kept — it is a published surface
  since 1.5.0, and TOTAL is correct for every container that existed then); the claim is carried to
  `LogIndex.totalOrder()` beside `byteAnchors`, reported by `context` before anything is derived from
  position, and marked in Settings ▸ Plugins. Native path verified unchanged in the running jar.
  **Second slice, 2026-08-25:** `graph(Path)` added as a DEFAULT returning empty (published surface
  since 1.5.0 — every existing reader keeps compiling); `SourceGraph {nodes, edges, provenance}` in
  the core's own vocabulary, with provenance riding the RETURNED graph because availability is per
  SOURCE (review F4). Reconciliation settled as **`GraphSource`**, which is M35.3's asymmetry one
  level out: a graph someone OPENED is intent and wins; one an adapter SUPPLIED is convenience and
  yields. `coverage` now REFUSES on an INFERRED graph rather than printing the 100% it gets by
  construction — the M34.0 spike's §4 finding turned into a guard.
- [M34.2] ☑ **Capability degradation wired** — _ordering half DONE 2026-08-25 on
  `feat/m34-adapters`_: the ordinal badge is not painted on a PARTIAL source, step-through says
  "logged N / M" not "step N / M", the Topology status carries a standing warning, and the echo
  carries `orderMeaningful` + `orderCaveat` because an agent reads the data, not the picture.
  Verified against a real PARTIAL source — a throwaway reader plugin, which also exercised M31's
  ServiceLoader path end to end for the first time since it shipped. `coverage` already refuses an
  INFERRED graph (M34.1). **Remaining:** "did not run" shading and replay-diff, each to degrade
  loudly with its reason rather than silently.
  _**Shading half DONE 2026-08-25:** an INFERRED graph's execution categories are hollow by
  construction — every node in it ran — so the status and the `topology` echo say that an absence of
  "did not run" nodes proves nothing. **Replay-diff has nothing to degrade: the feature does not
  exist yet** (the spec names it as something Temporal's native replay would fit better than
  Fluxtion). M34.2 is therefore complete against what is built; revisit when replay-diff lands._
- [M34.3] ☑ **Format specification + conformance fixtures** (D-A6); the built-in adapter passes them.
  _DONE 2026-08-25, merged to main_ — `docs/site/format-spec.md` (Format 1, MUST/SHOULD, in the
  site nav under *The audit log*) and `src/test/resources/conformance/` (12 files) + `FormatConformanceTest`
  (14 tests): C01–C13 pin the minimal record, forward tolerance, the header, the `-1` sentinel, untimed
  records, out-of-order reporting, duplicate ids, lenient values, garbage retention, the ordering claim,
  attribution-by-position, the traced regime and exported calls. **Every fixture runs through the built-in
  text path and the SPI pass-through path, and the two must agree** — that agreement is the promise to an
  adapter author. Report: `docs/handoff/completed/report_feat_m34_conformance.txt`.
- [M34.5] ☐ **Per-cycle concurrency marker — specified in D-A1a, absent from Format 1** _(surfaced by
  writing the spec page, 2026-08-25)_ — a mostly-sequential engine cannot be honest about the cycles that
  were concurrent without declaring the whole source PARTIAL. The M34.0 spike smuggled one through a
  `nodeLogs` item and it resolved as a data series with a mangled value (report §3). Needs a real field,
  a parser change, and the badge/step logic honouring it per record. The spec page says "not in Format 1"
  until it lands; the traced-regime marker (UP-FLX-11) is the other gap it names, and is upstream.
- [M34.4] ☐ **First foreign adapter, out of tree — LangGraph**, the throwaway translator of M34.0
  rebuilt against the SPI: same engine, now a supported source rather than a hand-fed file.
- _**Sequencing.** M34.0 is the gate and nothing else starts until it reports. It and M34.4 were two
  descriptions of one idea at different costs — the earlier draft named M34.4 as "the experiment that
  decides whether the rest is worth building", which is M34.0's job now that the spike is a slice of
  its own. M34.4 is no longer an experiment: by then the question is answered and the work is
  conformance. If M34.0 says a foreign run cannot be made legible by today's tool, no SPI fixes that
  and M34.1–.4 do not begin._

## M11 · Research → monitoring promotion (Grafana) — ☐ FUTURE (vision)
_Design: **[spec-assistant-actions.md](completed/spec-assistant-actions.md) §12**. Two complementary systems: the
analyser answers **unknown, one‑off** questions (forensic, source‑linked, LLM‑assisted); Grafana answers
**known, continuous** questions (dashboards, alerting). The workflow is a **promotion pipeline** — research
a series in the analyser until it's diagnostic, then promote it to production monitoring._
- [M11.1] ☐ **`export_promotion`** (analyser authoring action / File export) — emit a **neutral
  promotion manifest** from the named saved graphs; the named `GraphSpec` is the contract, and A10.8
  built the naming/persistence it depends on. _Renamed and rescoped 2026-08-20 by the decision below:
  the analyser emits the manifest, **an agent renders the Grafana JSON**._
  Manifest contents, all exactly reproducible from the `GraphSpec`: the **series** (keys/formula,
  resolve policy, label), the **allowlist** (the precise `instanceId.key` set the tap must publish),
  **thresholds** from the graph's guides, the pinned **window**, the **rationale** (explanation +
  notes — why this is worth watching), and **provenance** (log fingerprint + analyser version, reusing
  M33's D-I3a identity data).
- _**Decision (2026-08-20) — the analyser emits a manifest; the agent renders the dashboard.** M11.1
  as originally written had the analyser learning Grafana's dashboard schema, which contradicts the
  rule M29 and M31 both settled: **the analyser never learns a foreign format — the agent adapts it.**
  If it is wrong to teach the tool FIX on the way in, it is wrong to teach it Grafana on the way out,
  and a versioned foreign schema is a permanent maintenance tax on a hermetic core.
  The two artefacts have opposite requirements, so they split along the derived/declared seam this
  codebase already uses everywhere (M33 D-I7 rows-derived/presentation-declared; M28.6
  condition-persists/intervals-are-data): the **allowlist must be deterministic and analyser-generated**
  because M11.2's tap consumes it and the bounded-cardinality guarantee only holds if it is *derived*;
  the **dashboard JSON is presentation over a foreign schema** and is agent work.
  What makes it safe is that the manifest is a **checkable contract**: every metric the generated
  dashboard references must appear in the allowlist, and every promoted series must appear as a panel —
  a mechanical round-trip. Fidelity ("the chart I validated is the chart that alerts") is preserved by
  the series definition travelling verbatim rather than being re-derived.
  Consequences: multi-target for free (Grafana, Datadog, Perses) with no schema version matrix; the
  agent contributes what the analyser cannot know — dashboard conventions, folder structure, alert
  routing; and M11.1 becomes a serialisation of state already held rather than a foreign-format
  generator. **M11.2 is unaffected** — it consumes the allowlist either way.
  **Validate before speccing the verb:** have an agent build one real Grafana dashboard from a
  hand-written manifest first. If it has to ask questions the manifest cannot answer, the manifest is
  wrong — the same spike-before-SPI logic as M34.0, for the cost of one dashboard._
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

_Refreshed 2026-08-22. Shipped since the last refresh: **M34.0** (spike, gate opened), **M34.1**
ordering slice, **M35.1/.2/.3/.4/.7** (on `feat/m35-lifecycle`). **M18 is closed** in favour of
spec-agent-brokered-dev-loop; its old slices are gone from this list._

1. **M35 remnants** — **M35.5** (project switch closes log + graph) and **M35.6** (state the pairing
   up front, largely delivered already by `context.graphPairing` and the status line). Finish the
   branch and merge.
2. **§E provenance** (spec-agent-brokered-dev-loop) — **before any agent swaps logs between
   servers.** One field on `LogFingerprint`, plus the share-disclosure row and the Q1 soft-banner
   treatment, in the same commit. The design creates the hole; this closes it first.
3. **M34.1 remainder** — `graph(Path)` on the SPI, and reconciling an adapter-supplied graph with
   `open {graphml}`. Deliberately after M35, which is where that reconciliation is being designed.
4. **M12.4** (fix-with-agent launcher, v1 copy-command) — with M13 live, the handed-off agent can
   query back while it works.
5. **M12.1 / M12.2** (export_finding structure; replay-test fixture) — journal↔log pairing is the
   precondition to resolve first, and it also gates **M33.5**.
6. **M19** (onboarding example) — and it now carries the **§H conformance harness**, which must have
   a home before any cross-repo work starts.
7. **The small schedulable remnants**, any time: **M20.5** (project artifact pointers), **M35.8**
   (`open {project}` — so an agent can accept the offer M35.7 reports, and E7-E10 become drivable), **M29.5**, **M13.5**,
   **M21.7–.9**, and the **M22** five
   (`docs/handoff/handoff_17_aug_2026_1.txt`).
8. **Cross-repo — the §H gate is MET (M19.6 shipped the bench 2026-08-25); DRAFTED and READY TO FILE,
   still unfiled: UP-MNG-01…04, UP-PG-01…02, UP-RDR-01 in
   [upstream-asks.md](../proposals/upstream-asks.md) §5–§7, each with evidence and acceptance, so a session
   opened in the mongoose or playground repo has a brief.** The Mongoose **MCP admin tool** + `~/.mongoose/servers/` endpoint
   files, and the playground's two catalogue asks (`agentBootstrap`, the `catalogue` version
   integer). All belong in [upstream-asks.md](../proposals/upstream-asks.md).
   _Alongside them, and promoted from a footnote: **the out-of-tree Chronicle reader**. The Mongoose
   starters already write `auditBackend: "chronicle"`, so a reader on M31's shipped SPI
   (`supportsFollow: true`) tails the live audit store and **deletes the export beat entirely** —
   turning the dev cycle into edit → approve restart → watch the live log move. It is the cheapest
   large upgrade on this list, because the SPI it needs shipped in 1.5.0._
9. **M11** stays vision until a real Grafana consumer appears — and when one does it is
   `export_promotion` (a neutral manifest the agent renders), not a dashboard generator.

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

## Open questions

- Graph "last occurrence per record" vs "all occurrences" default. (spec: last; expose toggle.)

_(spec-closed-loop O1–O4 all resolved — statuses recorded in the M18 block above; O5 in Decisions.)_
