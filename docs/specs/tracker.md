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
(core + .7 report table sources) · M34.0–.3 source adapters (SPI, degradation, format spec + conformance suite) · M35 log +
graph lifecycle (all eleven) + §E provenance · **M36 start page (.1–.5) · M37 Project panel · M38 portable
context (.1–.7) · M40 audit readiness (.1/.2a/.2b/.3) · M42 Connect an AI client · M43 the AI menu (+ M38.8)** · refinement rounds
2–13 · assistant-vocabulary follow-ups. _(Polish H1, 2026-08-25: verified every section left in this
file has open items; the archaeology the 2026-08-17 brief asked for had been done by the per-merge
tidies.)_

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
assessed in [review_spec_agent_brokered_dev_loop.txt](../handoff/completed/review_spec_agent_brokered_dev_loop.txt).
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

## M19 · Onboarding example — playground download → running Mongoose → analyser — ◧ IN PROGRESS
_Design: **[spec-onboarding-example.md](spec-onboarding-example.md)**. The playground's Download button
ships a runnable Mongoose example with Chronicle audit capture pre-enabled and one YAML export command
targeting a predictable project-relative path,
bundled source, and a **project profile at `.analyser/project.fluxtion-settings`** (M20's canonical path —
the bundle *is* a project profile) — so onboarding becomes: download → run → jbang the analyser →
project auto-loads (M20; **File ▸ Import** until it lands) → Follow a live log with click-to-source and Explain working.
Target: under 10 minutes on a fresh machine with only a JDK. The bundle's README links back to the
analyser (reverse funnel)._
- [M19] ➜ **REVISED 2026-08-29 · independently reviewed — ACCEPT WITH AMENDMENTS** in
  [`review_m19_onboarding_and_trust.txt`](../handoff/review_m19_onboarding_and_trust.txt). Owner-directed:
  one download should produce a project where an LLM already knows Fluxtion, is connected to the analyser
  over MCP, and is told by the analyser which skills run/stop/read the local app. The spec pre-dated M38,
  M42 and M43, so five additions: **R1** the bundle ships `.claude/skills/*/SKILL.md`; **R2** the shipped
  profile REGISTERS them as `runbook.N.*` (and states why that does not violate D-AI5 — a bundle author
  declaring their own runbooks is the author declaring, not the analyser inferring); **R3** MCP
  pre-wiring, and step 6's division-of-labour paragraph is now WRONG and rewritten (M42 made it one agent
  that both edits and drives; the surviving principle is that the ANALYSER edits no code); **R4** the
  licence key is the first wall after first success and the seeded CLAUDE.md must pre-empt it; **R5** open
  the analyser on the GRAPH before the first run, so M40.1 has something true to say at minute two.
- [M19] ➜ **REVISION RETURN IS SUPERSEDED by M19.16/.17/.18 and the owner's start signal.** The third review
  originally left F1/F4/F5/F8 open. F1 and F4 are now closed by signed `m19-bundle/3` + `m19-skills/1`
  at `b0fdb86`; F5/F8 are a bounded deferral because the embedded tier is explicitly NOT PUBLISHABLE and
  outside the Mongoose bundle. Embedded graduation still needs a key-holder run through the listener and
  analyser; it does not block the selected Mongoose tier.
- [M19.18] ☑ **Bundle contract v3 corrects the profile ABI before publication** _(2026-08-29)_ — P3
  scaffolding checked P1's emitted profile against the real importer and found v2's one-based
  `sourceRoot.1`/`runbook.1` members and singular `eventProcessorFqn` are ignored. v3 specifies the
  zero-based list families, `selectedEventProcessor`, explicit `share.version=1`, and no fictitious
  `projectName`; `ProjectProfileTest` loads the exact generator-facing shape and asserts roots,
  processors, selection, runbooks/descriptions and provenance arrive. No v2 bundle was published or
  accepted, so consumers move directly to `m19-bundle/3`; `m19-skills/1` is unchanged.
- [M19.17] ☑ **Bundle contract v2 written, profile table superseded by v3** _(2026-08-29, third review's F1 + live export-beat correction)_ — normative table in the
  spec: required files, exact profile keys verified against `ConfigStore`/`ProjectProfile`, the commands a
  bundle must document, generated-bundle acceptance, and per-repo ownership. Versioned `m19-bundle/2`:
  v2 replaces the impossible run-writes-YAML assumption with the owner-selected Chronicle export beat and
  makes `m19-skills/1` normative (index/layout, error distinctions, bounds, provenance and enforced minimum
  analyser version). **Bounded deferral:** embedded remains NOT PUBLISHABLE and is outside the Mongoose tier.
- [M19.16] ☑ **Review amendments signed before bundle implementation at `b0fdb86`** — correct the MCP bootstrap to the
  supported resolved Claude/Codex/generic routes; distinguish the JDK-only and AI-assisted paths; give
  key provenance a value-free observable source rather than guessing a separate build's winner; and make
  the generated load-log skill name the bundle's actual path (review F1–F4). Parallel Mongoose/playground
  work is coordinated by [`handoff_29_aug_2026_1.txt`](../handoff/handoff_29_aug_2026_1.txt): phase-zero
  live-source reconnaissance only until an amended **M19 bundle contract** is signed at an exact SHA;
  after that, repository-owned slices may proceed independently and converge on one generated-bundle bench.
  - **Phase-zero report RECEIVED 2026-08-29** —
    [`handoff_29_aug_2026_1_report.txt`](../handoff/handoff_29_aug_2026_1_report.txt) (Mongoose/playground
    session). Both live-source tables delivered; seven mismatches recorded (M-1 the "Mongoose starter" is
    actually the playground's `mongoose.ts` — reassigns the brief's M1/M3 slices; M-2 NO current
    mongoose-hosted starter mode runs keyless; M-3 no file audit sink exists — chronicle-only capture,
    YAML is export-at-read, so the bundle's `./logs/audit-<name>.yaml` line needs an owner route decision;
    M-4/M-5 small loop-bench adaptations, analyser-owned; M-6 bundles are generated client-side in the
    browser — skills should be VENDORED at build time, not fetched at download). Bundle implementation
    NOT STARTED, per the gate. Two slices landed under their own already-accepted specs, on unpushed
    local branches: **UP-MNG-01 registry file** (mongoose-plugins
    `feature/up-mng-01-server-registry` @ 6e7a2cc — writes `~/.mongoose/servers/<name>`, mode 600,
    with `environment` per UP-MNG-03; 96 module tests green) and **UP-PG-01 `catalogue: 1`**
    (fluxtion-web `feature/up-pg-01-catalogue-version` @ 4bb71b5). Pull/test/runbook in the report §4/§7.
  - **OWNER DECISION 2026-08-29: M-3 resolved — the EXPORT BEAT is v1's audit path** ("go with the
    export beat for v1"): Chronicle capture + `/api/audit/file/{id}/export?format=yaml`; the bundle
    contract's file-sink line amends accordingly; bootstrap D-01 answered (no file sink exists or is
    needed for v1); UP-RDR-01 later deletes the beat. **Live acceptance ran the same day** (report §7):
    a real server published the registry file and loop-bench passed ALL SIX registry-side steps; the
    export step fails on the recorded M-4 bench adaptation (bare-array listing — analyser-owned edit,
    with M-5's BEARER mapping). The shutdown check caught and fixed a real bug: Mongoose's clean stop
    calls `Service.stop()`, never `tearDown()` — removal now hooks `stop()` (commit 6e7a2cc).
- [D-R1] ☑ **RESOLVED 2026-08-29 — the analyser owns local key-file management, not build-winner provenance.**
  The visualiser is not shipping (too IDE-specific); its masked field, canonical file and
  `~/.fluxtion/profiles/` concept moved into the analyser at `db42919`. The later D-X3 correction is
  load-bearing: this process can observe only whether the canonical file has a configured key and cannot
  know a `-Dfluxtion.apiKey` passed to a future Maven JVM. It documents that precedence and that
  `FLUXTION_API_KEY` is not read, without claiming a winner. The VALUE never reaches `AppConfig`, context,
  an echo, profile, share export, status/console or screenshot.
- [D-R2] ☑ **RESOLVED 2026-08-29 — skills are a LIBRARY KEYED BY HOST**, not per-project authoring:
  `common` (load log, record, replay) + one of `mongoose` / `embedded` (audit via `setAuditLogProcessor(LogRecordListener)` — **NOT** `addSink`/`FileMessageSink`,
  which review F1 showed was wrong). A bundle SELECTS a tier. The rule: a skill describes the
  project's own entry points and never invents a CLI — invented commands are fiction an agent cannot
  distinguish from fact.
- [D-R3] ☑ **RESOLVED 2026-08-29 — late-bind at DOWNLOAD, never at RUNTIME.** Guidance (prose, skills,
  CLAUDE.md canon) lives on the website; anything the analyser must BEHAVE by (verb schemas, format
  conformance) ships in the jar. O3 already generates bundles at Download, so updating the site needs no
  analyser release — and nothing fetches at runtime, which is what preserves the offline story,
  reproducibility, and version safety.
- [D-R4] ☑ **`skills.source` override — BUILD/RELEASE contract fixed in `m19-skills/1`.** Owner-required for test and corporate mirrors.
  Machine tier ONLY: never read from a project profile, because a profile travels between people and must
  not redirect the instructions an agent reads. Accepts a URL, a local path, or `none` as a first-class
  value. The source used is recorded and shown. The versioned index/layout, bounds, distinct failures,
  sanitised provenance and `x-analyser-min-version` refusal are normative in the M19 v2 contract; the
  playground vendors the snapshot at build/release, never in the user's browser. Blast radius stated:
  nothing executes a skill, so the exposure is an agent READING a procedure — real and bounded.
- [M19.13] ☑ **DAY TWO shipped at `1f30213`: `New project…` offers what it found** _(owner, 2026-08-29; spec R7)_. Day one is
  a primed bundle; day two is the user's own project. Before this slice, `File ▸ New project…` created an
  **empty** profile and discovered nothing, so a user who had
  just watched the bundle work must then perform four undocumented actions to reach the same state on
  their own code. Every ingredient is already built — `SkillDiscovery`, M35.4's graph discovery, the build
  layout for a source-root guess — so this is one dialog that OFFERS and never selects (D-AI5). **The only
  part of M19 that needs no other party to agree**, and the part that decides whether the bundle is a demo
  people can reproduce.
  - 2026-08-29 implementation evidence: `NewProjectDiscovery` composes the existing bounded
    `SkillDiscovery`, `GraphmlDiscovery` and source-root layout detection; `NewProjectOfferDialog` puts
    all three in one confirmation with every choice off. Confirmed roots and skill pointers persist; at
    most one confirmed topology opens. `NewProjectDiscoveryTest` proves an empty directory is an empty
    offer, default adoption changes nothing, and explicit choices alone apply. Public Projects journey
    and `[Unreleased]` updated. Full Maven suite, pinned strict-site build, diff check and tracked-file
    leak sweep pass; independent Swing checks are logged in
    [`unreviewed-changes.md`](../handoff/unreviewed-changes.md).
  - Independent review accepted the slice and found one concrete bundle-shaped reach gap: GraphML is
    searched only below detected source roots. This is now a P3 gate, not an independently guessed path:
    generated-bundle acceptance must call day-two discovery at the bundle root and assert the declared
    graph is offered. If it misses, the generator moves the graph beneath a detected root or returns the
    exact path for an analyser-side bounded-scan change before sign-off.
  - The first P0 output fixed the path at `src/main/resources/<package>/<processor>.graphml`, so the
    analyser-side return is closed at `389d331`: New-project discovery now includes that bounded Maven
    resource root and a regression fixture uses the generated-bundle layout. P3 must still prove the
    actual emitted path is offered; this unit fix is not generated-bundle evidence.
- [M19.15] ☐ **The seeding prompt for step 2** _(owner, 2026-08-29; spec has it verbatim)_. Step 2 is only
  a measurement if the prompt does not contaminate it: leading the witness produces agreement, manufactured
  hostility produces theatre, instructing the task tests the prompt instead of the docs, and revealing it is
  a test makes the model evaluate rather than use. **The risk that is easy to miss is not failure — it is
  SUCCESS BY COMPENSATION**: a capable model fills a gap from training data or by reading generated source,
  finishes the task, and the gap is invisible. So the prompt's job is to make compensation VISIBLE, not to
  prevent it. Measurement is mostly external — the git history, the code and the audit log are evidence; the
  model's account is testimony (D-T3 applied to assessing the product).
- [M19.21] ◧ **REVIEWED 2026-08-30 — REVISE BEFORE ACCEPTANCE; DO NOT DEPLOY OR PUBLISH v2** —
  `fluxtion-web` @ `ea00075` (committed, **not deployed**: the live site still serves v3 bundles).
  Report: [`report_playground_bundle_v4.txt`](../handoff/report_playground_bundle_v4.txt). Independent
  review: [`review_playground_bundle_v4.txt`](../handoff/review_playground_bundle_v4.txt) — the
  restatement failure really is an analyser bench defect, but sign-off is blocked by an unpinned,
  unsafe-provenance reference-set fetch (separate from the skills SHA), a red analyser bench unit test,
  and the unproved non-Spring `appliesTo` path. The bundle
  declares `m19-bundle/4` and selects `common` + the template's declared specialisations —
  load-audit-log, guided-start, spring/add-a-node, mongoose/run-mongoose-server — with `replay`
  correctly not selected. `bundle-bench.py` on a generated bundle: **68 passed, 1 failed**.
  **The one failure is OURS, and the report proves it rather than asserting it:** `check_v4`'s
  restatement guard greps for `source-gen triage`, which is the wording of `reference-set.json`'s own
  `why` for the CLAUDE.md resource — so `ReferenceSet.markdown("spring")`, the rendering the brief
  names as reference and reprints verbatim in its §4b worked target, fails our own bench. Any
  faithful generator fails it. Fix is ours: scope the check to the text below the rendered block.
  Two findings worth keeping: the previous bundle shipped
  `raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt`, which our set marks
  **EXCLUDED** — every bundle generated before today carried it, and vendoring only `agreed` entries
  closes that; and their tool now **resolves the ref to a commit before reading**, so a DRAFT index
  that pins nothing still vendors reproducibly and every byte comes from one commit rather than from
  whatever `main` was at each fetch. `canonical-draft@` was tried and correctly refused by our
  provenance grammar; draftness now lives in their manifest. **v2 stays DRAFT until the bench defect
  is settled** — if it is not, the next generator to render our set faithfully will hit the same
  check and may "fix" it by rewording our text. Original brief below.
- [M19.21] ☐ **Playground-side: bundle contract v4** — **brief written for the playground session:**
  [`brief_playground_bundle_v4.txt`](../handoff/brief_playground_bundle_v4.txt). It leads with the two
  things that stop a cold start: the analyser inputs are committed but **not pushed**, so the canonical raw
  URLs are not live yet; and `m19-skills/2` is DRAFT, which is not a blocker for them because their
  integration is what lifts it. Also records the retrieval path for `reference-set.json`, which is
  deliberately outside the skills root and had none. _(specified 2026-08-30 after the owner asked where this
  work was written down — it was not)_ — [`spec-onboarding-example.md` ▸ BUNDLE CONTRACT v4](spec-onboarding-example.md).
  A delta on v3, because v3's tables remain correct except where this session changed them. Four
  obligations: consume `m19-skills/2` (common + specialisations) and own the **template → specialisations**
  mapping for the real catalogue; **never select `replay`** without a real replay entry point, since its
  `TODO(bundle)` could not be substituted and v3 already forbids shipping an unsubstituted marker; render
  the bundle's `CLAUDE.md` from `reference-set.json` shipping **only agreed entries** with `appliesTo`
  selecting rather than annotating; and verify vendored bytes against v2's provenance **once v2 is
  published**. Six added acceptance items. **`m19-skills/2` is DRAFT** — its selected skills carry
  `TODO(bundle)` markers only a generator can substitute, so v1 stays the published contract and F3 is
  only partly closed until a real consumer proves substitution (review C1). The template →
  specialisations mapping is **playground-owned**; the canonical index declares no `templates` map.
  `agentBootstrap` (UP-PG-02) stays open and adjacent.
- [M19.20] ☐ **Sibling dispatch order is natural-order by node name** _(found in round 04, 2026-08-30)_ —
  the generated processor orders nodes by `TopologicalOrderIterator(graph, new NaturalOrderComparator(
  inst2Name))`: dependency order first, then **name order** among nodes at equal depth. Found by an agent
  that placed a node later in the XML, observed it dispatched earlier, ruled out declaration order and
  decompiled `fluxtion-builder` 1.0.64. Published in none of the five authoring resources. Two consequences
  to work: it belongs in the upstream audit/authoring content (UP-FLX-35), and the analyser recomputes
  layers rather than reading a declared order — worth checking whether its topology layout agrees with this
  rule (see the dispatch-order-index note in `upstream-asks.md` §2c).
- [M19.19] ☐ **Guided start — an install prompt, and an LLM tutor that drives the UI** _(owner idea,
  2026-08-30; **and the experiment's baseline**, D-G8)_ — [`spec-guided-start.md`](spec-guided-start.md). Zero to a running analyser showing
  capabilities, driven by a prompt an LLM executes. **Verified: the tutor needs NO new verbs** — `open`,
  `filter`, `topology`, `goto`, `graph` and `flag` already drive the UI, and `context` + `screenshot` tell
  the agent what the user can actually see. The load-bearing rule is D-G2, from D-T3: **the tutor points,
  the screen proves** — it may not state a figure the user cannot see, which makes the tutorial a live
  demonstration of the thesis rather than a chatbot describing software. Setup is shell, not analyser
  surface, and the whole path is **keyless** (a bundle ships its generated processor). One real gap: MCP
  registration is an in-app flow — v1 asks the human to do it rather than adding a headless path. Also
  D-G5: this is the best held-out task the experience loop has, because its outcome is objective.
- [M19.14a] ☐ **The method for M19.14/.15, written up for review** _(owner-directed, 2026-08-30)_ —
  [`spec-authoring-experience.md`](spec-authoring-experience.md). The end goal restated by the owner: the
  product is the bundle's context assets (`CLAUDE.md`, `AGENTS.md`, the authoring loop, upstream asks), and
  measurement is trajectory rather than pass/fail because the subject is probabilistic. Eight decisions:
  where a fact belongs and why the discriminator is whether it fails **loudly** (D-AX1); the doc teaches the
  compile→read-the-message→run→read-the-log **loop** rather than a list of rules (D-AX2); the doc set is a
  **placeholder for missing diagnostics** and shrinks as §1c lands (D-AX3); the shipped **examples are
  documentation** and currently demonstrate only the easy cases (D-AX4); six countable signals with
  **WENT-OUTSIDE** primary, n≥3 in parallel, and one that should go **up** (D-AX5); a deterministic
  **preflight** so probabilistic runs are not spent on script-findable defects (D-AX6); one variable per
  round plus a control arm (D-AX7); generate `AGENTS.md` (D-AX8). Withdraws my "nobody reads `CLAUDE.md`"
  conclusion — that measured the harness, not the product.
- [M19.14] ☐ **Step 2 — rewrite the LLM context assets from a MEASURED run** _(owner, 2026-08-29)_. The
  owner's sequencing, and better than the advice it replaced: build the mechanism first, then have a
  context-free LLM develop and analyse a real project with it, and let the gaps it actually hits rewrite
  the assets. Authoring first means guessing what a model will lack; this observes it — the prediction-file
  discipline applied to documentation. Record four things or it is a story rather than evidence: the
  question it could not answer from the seeded context, whether it INVENTED an API (the M21 failure mode),
  where it needed the audit log to correct itself, and what it never used (unused context is cost). Caution
  in the spec: "context-free" is a property of the session, not the model — an easy pass is weak evidence,
  the failures are the output.
- [M19.11] ☑ **Onboarding bench — the bundle consumer path is reproducible** _(completed 2026-08-30)_. My claim that
  the headline had never been run cold was **wrong**: the owner ran it the previous Friday — bare Java
  project, one `jbang` install, existing Claude skills picked up as-is, and **Codex** driving the analyser
  over MCP. That is the first evidence in this project from a **different LLM** and from a **project not
  prepared for it**, which is stronger than the fixture written for M43. The producer's static
  `bundle-bench.py` guards the generated ZIP. This repo now supplies the other half:
  `tools/bench/bundle-client-bench.py` launches a never-configured analyser, opens project then YAML +
  GraphML in the correct two-call order, checks profile/runbooks/provenance/pairing/coverage, and drives
  a separately packaged MCP stdio bridge through current discovery, tools/list and analyser_context. It
  passed 19/19 against artefact branch `893fbdf` and its 23-record released-1.0.39 bundle. This is the
  ordinary regression guard (three repos change independently; nothing says when it stops), scoped to
  the bundle path, and is **not** a precondition.
- [M19.12] ☑ **Key management shipped at `db42919` (R8)** _(owner asked 2026-08-29: "is
  key management in this spec?" — it was not; D-R1 resolved the DECISION and nothing described the
  feature)_. Three surfaces, no first-run modal: a start-page card, an `AI ▸ Fluxtion API key…` item, and a
  Project-panel row stating **locally observable setup facts only** (D-X3) — the key file present or absent,
  with the precedence rule documented beside it. It must NOT claim which source a future Maven build
  resolved, and `FLUXTION_API_KEY` is not an answering source: the builder never reads it. Lift `FluxtionAccountDialog` including its `~/.fluxtion/profiles/`
  concept. Never validates against a service: presence is local, validity is the build's business, and an
  analyser phoning home to check a key is the enforcement this product argues against.
  **The limit now has ACCEPTANCE rather than prose**, which is the half that matters for a credential: a
  test asserts the value reaches no `context`, no echo, no profile, no share export, no status bar or
  console. `AppConfig` does not hold it; `KnownKeys` owns no family for it. Not hypothetical — the M42
  review found JVM options carrying secrets into a client config file, and the sweep cannot see inside a
  screenshot.
  - 2026-08-29 implementation evidence: `FluxtionKeyStore` writes only the established `apiKey=…` file,
  preserves unrelated builder properties, enforces owner-only permissions where supported, wipes the
  caller buffer, and supports named profiles without a value-read API. `FluxtionKeyDialog` is masked and
  never validates; Start page, AI menu and Project-panel/context row receive presence only. Targeted
  `FluxtionKeyStoreTest,FluxtionKeySafetyTest,ProjectModelTest,ProjectPanelIsRevealOnlyTest,AiMenuTest`
  pass; full Maven suite, pinned strict-site build, diff check and tracked-file leak sweep pass; public
  guide and `[Unreleased]` updated. Pending independent Swing eyeball is logged in
  [`unreviewed-changes.md`](../handoff/unreviewed-changes.md).
  - Independent review accepted the slice. Its one code follow-up is closed at `6243a89`: profile-name
    validation now runs inside the same `finally`-guarded region as writes, and a regression test proves
    the submitted `char[]` is wiped when a traversal-shaped name is refused.
- [M19.12a] ☑ **Licence registration placement shipped at `db42919` (R6)** — `showFirstRunSettingsIfNeeded`
  is a no-op for humans since M36 **D-S1** removed the first-run modal on owner report; the start page IS
  the first run and already carries the MCP cards. A key card states the fact and names the remedy without
  gating anything. **Re-adding a first-run modal would reverse D-S1.**
- [M19.10] ☑ **Canonical skills accepted and the P3 correction is shipped** — authored in [`docs/skills/`](../skills/README.md); four
  written and verified discoverable by the shipped `SkillDiscovery`. Canonical `TODO(bundle)` markers
  remain authoring inputs only; the generator grounds the selected copies and rejects any survivor.
  - 2026-08-29 at `5c72e21`: `CanonicalSkillsTest` pins the four names, descriptions and minimum analyser version;
    the Mongoose tier now follows the live registry → Chronicle capture → bundle-owned YAML export beat,
    and the embedded tier's NOT-PUBLISHABLE gate is pinned. The canonical `TODO(bundle)` markers remain
    intentionally because commands/paths are project-owned; M19.10 closes only when the playground
    substitutes them and its generated-bundle checker proves none survive.
  - At `5c72e21` the analyser preserves and shows only sanitised inert `skills.provenance`, while a project
    `skills.source` is ignored with an explicit refusal and removed on save. Tests cover canonical,
    local, mirror, none and credential-capable/malformed shapes; no runtime retrieval exists.
  - Independent review accepted the grammar and real-server semantics. Its marker finding is closed at
    `6243a89`: clean stop now has its own `TODO(bundle)`, so P3's no-marker check cannot pass without a
    real project stop procedure. The generator must vendor `common + mongoose` from that revision (not
    the earlier `5c72e21`) and emit `skills.provenance=canonical@6243a899774d591119559305a137ecf144819efd`.
  - Playground P1 `3c0f926` vendors byte-identical copies from `6243a89`, substitutes the two applicable
    skills and refuses surviving markers across generated files; the review at branch head `73565fc`
    runs 22 focused tests and confirms the copies remain byte-identical. Omitting
    `replay-a-run` is accepted because this bundle declares no replay entry point. The P1 single-model
    finding was corrected at `8f20016`; the actual Download zip now proves the substituted files contain
    no surviving marker and passes bundle-bench 49/49. P2 retrieval was accepted at `4eabc1c`.
  - P3 then proved a valid-looking combined `open {project, log, graphml}` call ignores the log and graph:
    a project switch is deliberately a session boundary. The canonical load-log skill is corrected at
    `f5efe17` to check the active project, open a different project alone, then open log + GraphML in the
    second call. The published index now names that source revision.
  - Closed 2026-08-30: the playground re-vendored the public canonical root at full revision
    `f5efe17e1b234bdb6c55cd8fada27d2bdc8d2bc8`; the delivered ZIP reports that provenance, both pointers
    resolve to concrete marker-free skills, and the fresh analyser plus MCP bridge report the same value.
- [M19.1] ◧ **Released bundle produced; implementation accepted, refreshed final evidence artefact remains** — **full Maven project** (O1 resolved: user edits
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
  - **Dependency gate ☑ released and consumed as mongoose-plugins 1.0.41:** local implementation/bench work used
    `svc-admin-web:1.0.39-SNAPSHOT` from Mongoose Plugins `6e7a2cc`. On 2026-08-29 the final
    `svc-admin-web:1.0.39` and `mongoose-test-support:1.0.39` POMs both resolved publicly from the Repsy
    repository generated bundles already declare. Public 1.0.40 added the `startComplete` registry refresh;
    public 1.0.41 added its strengthened behavioural test and the regenerated schema golden. The playground
    now pins 1.0.41; a generated bundle publishes its processor immediately without a dashboard poll and its
    declared GraphML endpoint returns 200. Version 1.0.42 only removes release-plugin scratch from source
    control (the relevant runtime source is identical), so it is not an M19 consumption gate.
  - **P0 ☑ accepted at reviewed head `73565fc`:** fluxtion-web
    `feature/m19-p0-keyless-bundle` moves scan/second-compile behind
    `-Pgenerate-fluxtion`, adds deterministic registry identity plus export/stop scripts, and keeps the
    classic shape additive. `d43552e` closes disabled capture, export-script `eval` and forced serverName;
    `73565fc` closes the Builder-inaccurate preflight, validates bundle invariants, forces both identity
    fields, passes the stop registry path as argv, and refuses a reused/mismatched PID. The live Bash
    fixture keeps hostile registry values as data. Details and exact disposition:
    [`review_m19_p0_fixes_and_p1.txt`](../handoff/review_m19_p0_fixes_and_p1.txt).
  - **Branch-level key-advice follow-up ☑ closed at `3acaf9b`:** ordinary Fluxtion README/run-script
    output now names only the Builder's file/-D sources and a non-bundle fixture pins both files.
  - **P1 ☑ accepted at generator head `8f20016`:** `m19-bundle/3` emits the real zero-based profile ABI;
    one AnalyserBundleModel supplies scripts/README/profile/skills/guides; the acceptance fixture is the
    Spring-XML template with design XML + maintained authoring canon; minimum-version refusal is present.
    Independent gates: 27/27 focused, 376/376 full and production build pass. Review:
    [`review_m19_p1_response_and_download_zip.txt`](../handoff/review_m19_p1_response_and_download_zip.txt).
  - **Download seam ☑ closed at `266132a`:** the actual `buildMavenZip` preserves root CLAUDE/AGENTS,
    Maven wrappers and lifecycle scripts; `mvnw` plus the scripts retain executable modes. The focused
    packaging test passes and the exact Spring Download zip passes analyser bundle-bench 49/49. Stale v2
    implementation comments are gone. Evidence is in the cross-repo report §7g.
  - **Contract-version declaration for v3 — no profile key:** the authoritative marker is the exact
    `Bundle contract: **m19-bundle/3**` line in required root `CLAUDE.md`; required `AGENTS.md` is its
    byte-for-byte mirror. P3 parses that marker, rejects unknown versions and checks the mirror. The
    profile comment is informational. This selects a checker route already emitted by P1 and does not
    change the v2 inventory or profile schema.
  - **P2 ☑ accepted at `4eabc1c`:** source parsing/refusals, bounded
    index/set, distinct outcomes, sanitised provenance and project-input refusal are sound; current
    canonical skill bytes matched analyser `6243a89` at acceptance; the post-P3 instruction correction
    is published at `f5efe17` for the final re-vendor. The empty eager content registry now lets a written
    `none` snapshot build; strict leading frontmatter/exact versions close both false passes; required
    Mongoose skill-set and duplicate-name gates close incomplete `ok` results. Independent gates: 44/44
    focused at `050c0ab`; 395/395 full at `5f01cab`. **F4 ☑:** the public raw analyser root serves the
    versioned index; the playground now selects it as CANONICAL_ROOT, has removed `--declare-canonical`,
    and the independently-run default CLI emits canonical@6243a89 with byte-identical content.
    **F5 ☑:** the matrix now fails closed, asserts exact leg identities, and independently passes
    canonical 49/49, none 35/35 and local 49/49 through build → actual zip → checker. A five-test real
    loopback-TLS fixture covers successful mirror retrieval/provenance, redirects and distinct HTTP/
    transport outcomes; mirror/local reconverge before the shared non-none snapshot/build path.
    Independent gates: 74/74 focused, 401/401 full. **Low follow-up ☑ closed at `2ad5289`:** the fixture
    supplies its generated certificate as the private client's `ca` with verification enabled. Review
    and dispositions:
    [`review_m19_p2_skills_retrieval.txt`](../handoff/review_m19_p2_skills_retrieval.txt).
  - **P3 ◧ implementation accepted; refreshed shared evidence artefact remains:** `tools/bench/bundle-bench.py` checks an
    unzipped project or download zip against `m19-bundle/3`, including the real zero-based profile ABI,
    guide mirror/version, committed processor source, declared/discoverable GraphML, exact shipped
    runbooks + frontmatter/provenance/minimum version, executable lifecycle scripts, safe inventory and
    placeholder refusal. Nine deterministic Python fixtures run in CI, including rejection of v2's
    one-based/singular profile plus canonical, `none` and clean HTTPS-mirror provenance. The real
    canonical Spring Download zip now passes 49/49 static checks. A real SNAPSHOT-based run at playground
    `4eabc1c` proved empty-HOME keyless package, real processor registry publication, 18 audit records and
    declared-path YAML export. It found that generated MongooseMain lacked a shutdown hook; the generator
    now calls server.stop() from one, and the live rerun removed the registry entry cleanly.
    Local gates: 9/9 Python fixtures, 1,112/1,112
    Java tests, strict docs and the existing packaged stub/analyser/MCP loop 23/23.
    The final artefact is reproducible on fluxtion-web `m19/p3-artifacts` @ `893fbdf`; its ZIP SHA-256 is
    `a5fba6c3d07cae710b825131403b1fa8d350fc6e6a284c5d95b03e94f29c9ba6`. Public 1.0.39 keyless build,
    run, five typed PriceEvent cycles, 23-record export and clean ordinary-home stop are producer-proven.
    This session independently passed the ZIP 49/49 and its fresh analyser/MCP leg 19/19: active project,
    two described/existing runbooks, canonical@f5efe17 provenance, pairing 2/2, coverage 1.0, 14 tools and
    analyser_context returning the same state.
    **Lifecycle response accepted at deployed fluxtion-web `c15ed9f`:** `280898e` restores exact Java/JAR/
    start-time stop identity, observes exit plus registry removal, passes the registry override through all
    three commands, and moves runtime claims into a committed real-bundle bench reported 11/11. Public
    mongoose-plugins 1.0.41 refreshes the entry at `startComplete`; its test observes the exact processor,
    group and GraphML route, and a generated-bundle run fetched that route without a dashboard poll. This
    session's current Download ZIP passes 49/49 and pins 1.0.41. **The shared `m19/p3-artifacts` branch is
    still `893fbdf` / 1.0.39**, so refresh it with the current ZIP, generated source/GraphML/YAML/hashes before
    the final current-version 19/19 rerun and P3 completion. Disposition:
    [`review_m19_p3_lifecycle_final.txt`](../handoff/review_m19_p3_lifecycle_final.txt).
- [M19.1a] ◧ **Mongoose starter conformance bench (validation only; not a bundle shipment)** — the
  downloaded `mongoose-hosted-fluxtion` starter now has a reviewable contract snapshot in
  [`mongoose-bootstrap-artefacts/`](mongoose-bootstrap-artefacts/), with its source project retaining
  ownership. It tests this M19.1 contract **and** the accepted agent-brokered dev-loop where they meet:
  M19's bundle-owned YAML export at `./logs/audit-<name>.yaml`, profile and source evidence stay required; the
  registry/export/GraphML leg is **VAL-12**, exercised by `tools/bench/loop-bench.py` only when Mongoose
  supplies UP-MNG-01 and the export surface. The current starter supplies neither, so it makes no
  brokered-loop or distribution claim. Review resolution:
  [`report_mongoose_bootstrap_review_resolution.txt`](../handoff/report_mongoose_bootstrap_review_resolution.txt).
  V0 is documentation-complete except for owner decision D-02; V1 application work has not started.
  The project-local `.claude/skills/mongoose-local/SKILL.md` is discoverable-but-not-auto-added and is
  not the graduated shared skill (V5). Its local tracker carries the four explicit review follow-ups:
  A1 real-server bench, A2 registry-first discovery, A3 UP-MNG-02 disposition, and A4 manual skill
  adoption; none is complete merely because the documentation exists.
- [M19.2] ☑ **`SettingsShare`: resolve relative roots against the import file's parent** — `preview`
  gained a `baseDir` overload; import resolves bundle-relative source roots / Maven repos against the
  settings file's directory (absolute & `~`-paths untouched; clipboard imports pass no baseDir). 2 tests.
  Unblocks the tutorial's "zero manual setup" claim.
- [M19.3] ◧ **Tutorial page corrected against the shipped bundle; screenshot set in progress.**
  `docs/site/tutorial-playground.md` now uses the real Audit analyser bundle name and concrete paths,
  opens project/GraphML/log separately, distinguishes a fixed export from a followable log, teaches
  Explain/copy-prompt/MCP, and refuses to promise a two-run diff that is not built. Four generated
  screenshots now show the real bundle's project, log, PriceEvent cycle and source navigation; three
  existing isolated-DEMO figures cover graphing, the AI menu and MCP setup. The remaining spec captures
  are the live playground Download, terminal lifecycle, an actual Explain answer and IDE edit; no connected
  browser was available in this session, so those were not fabricated. **One producer gap blocks the graph step:**
  the released bundle logs `rootNode.receivedEvent` as a string but no numeric key, so the tutorial cannot
  honestly graph `price` until the starter logs it separately. Original requirement: four parts + the
  8-screenshot set (spec §Part 2, anonymised
  per policy), nav under Getting started. **Publish-gated on the bundle shipping** (write against the
  contract; publish only when Download delivers). **Two authoring notes:** (a) the pathway table names
  the Support leg "run, observe, diagnose *and fix*" — but "fix" is M12/M18; keep the page copy honest
  to what's shipped that week (don't promise fixing before M18 lands — the end-bridge already phases it
  as "+ server link once M18 ships"). (b) In-page links must be **site-relative** (`producing-a-log.md`,
  not the spec's `../site/producing-a-log.md`) or `mkdocs build --strict` fails the link-check.
- [M19.4] ☑ **Cross-links SHIPPED** _(2026-08-30, `2e02205`)_ — all three entry points route to the
  tutorial, and the end-bridge closes it with "Do this on your own system →" linking producing-a-log
  and the project guide. `mkdocs build --strict` green, so every link is checked. Original scope:
  getting-started step 2, producing-a-log, landing "Get going"; **and the
  tutorial's end-bridge**: a closing "Do this on your own system →" section linking producing-a-log
  (+ the server link once M18 ships) — the demo must hand off to the user's real adoption, not stop at
  the toy.
- [M19.5] ◧ **IMPLEMENTED END TO END — AWAITING INDEPENDENT REVIEW** _(2026-08-30; playground
  `994e82a` live, analyser `9d38cc4`)_ — `File ▸ New project from template…` reads the live
  catalogue-owned onboarding set and `keyNeed`, loads catalogue-owned identity defaults, downloads
  from the pinned HTTPS origin, installs through a fail-closed archive boundary, opens the bundled
  profile (or reuses day-two discovery for an older template), and shows copyable analyser-owned
  lifecycle commands without executing downloaded content. Network and extraction work is background,
  modeless and cancellable. `template-bench.py` attacks traversal, absolute paths, populated targets,
  count/per-entry/total expansion limits, multiple roots and an archive-marked `evil.sh`; its live leg
  passed 6/6 against the deployed `analyser-bundle`. Full suite: 1129; strict docs: green. Review brief:
  [handoff_30_aug_2026_1_report.txt](../handoff/handoff_30_aug_2026_1_report.txt).
  **D-3 decided NO for this slice:** download/extract/open, then show/copy fixed commands; never run.
  This was **widened from its original scope** _("defer unless tutorial reads clunky — File ▸ Open example…
  one-action helper (import + open + Follow)")_ on the owner's ask: *"I thought we would be able to
  choose a template from the swing app to make it seamless to get started."* The old entry automated
  the **last** hop and assumed the bundle was already downloaded; the owner meant the **first**. The
  old scope survives as the new one's final step. Full design and decision record:
  [spec-template-from-analyser.md](spec-template-from-analyser.md).
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
- [M19.8] ☑ **The bench is green in Linux CI at `92ad3ba`** — the analyser is Swing, so the new
  job installs a display (`xvfb-run` on the Linux runner), packages the jar and runs the exact
  `tools/bench/loop-bench.py --stub --launch` path. Local isolated run passes **23/23** on 2026-08-29;
  GitHub run `33271896191` completed both `build` and `loop-bench` successfully, including the
  registry/export/analyser/MCP step under xvfb.
  - Independent review accepted the live run. Its CI hygiene note is folded into `6243a89`:
    `actions/checkout` and `actions/setup-java` now use v5. GitHub run `33272924784` completed both jobs
    successfully at `7384d0e`; the loop step passed under xvfb.
  - A later metadata-only run (`33273004452`) caught a real startup race: the endpoint publisher
    created the public file before writing JSON, and the bench could read it empty. Fixed at `297c4c1`
    by private owner-only write + atomic replace; the bench also treats a malformed/stale file as not
    ready instead of crashing. Full 1,109-test suite and packaged local loop pass 23/23; GitHub run
    `33273629437` passed both the build and xvfb loop-bench jobs at `2e63b8c`.
- [M19.9] ☑ **Headless launch-argument tests shipped at `92ad3ba`** — `Main` strips `--rest`, rejects an
  unknown flag AFTER stripping, and lets a log path fall through. This was pure logic with three
  behaviours and no unit test (rule 4); the loop bench covered it only where a jar, JVM and window
  existed. From `review_feat_m19_bench.txt` F3.
  - `Main.parseDesktopArgs` is pure and `MainLaunchArgsTest` pins strip/remember `--rest`, leave an
    unknown flag for the existing loud rejection, preserve a log path, and accept a fresh empty launch.
    Full Maven suite and the packaged 23/23 loop bench pass.
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
- [M31.5] ☐ **NOT YET** _(owner, 2026-08-27)_ · **Separate `analyser-reader-spi` artifact** — needs a multi-module
  build; deferred in review (D9). Plugin authors compile against the fatjar meanwhile.

## M33 · Investigation reports — ◧ CORE SHIPPED 2026-08-20 (archived; M33.5 gated)
_M33.1–.4 shipped, twice-reviewed, owner-eyeballed and merged — full record in
**[completed/tracker.md](completed/tracker.md)**.
Design: **[completed/spec-investigation-reports.md](completed/spec-investigation-reports.md)**._
- [M33.5] ☐ **Fold M12.1's fix-brief onto the model** (D-I6) — after the closed-loop precondition
  (journal ↔ audit-log pairing) resolves, not before. The brief inherits D-I3a for free when it lands.
- [M33.6] ☐ **YES — build it** _(owner, 2026-08-27; support are non-agent users and the CSV source is
  otherwise verb-only)_ · **chooser dialog for external marker CSVs** —
  markers are verb-first by design; *File ▸ Add series from CSV…* covers series only. Decide whether
  markers deserve the same dialog before advertising the CSV source to non-agent users.

## M35 · Log + graph lifecycle — ☑ SHIPPED 2026-08-25, all eleven slices + §E (archived)
_Everything in [completed/tracker.md](completed/tracker.md) ▸ M35. Nothing open._

## M36 · Start page — follow-up (.1–.5 SHIPPED 2026-08-25; the milestone is in completed/tracker.md, design **[completed/spec-start-page.md](completed/spec-start-page.md)**)
### Rule 1 — owner decisions (raised M36, sharpened by the polish round; the two resolved ones are archived with M36)
- ☐ **Ask upstream whether the compiler still emits that header** — every generated processor in every
  user's repo carries it. An upstream ask, not an analyser one.

## M38 · Portable context — follow-up (M38.1–.7 shipped 2026-08-27; the milestone is in completed/tracker.md, design **[completed/spec-portable-context.md](completed/spec-portable-context.md)**)
- [M38.8] ➜ **ABSORBED BY M43.2** _(owner, 2026-08-28: "the skills binding to playbook at the same time")_.
  The menu is the first surface that must ASK for a description, so shipping the storage separately would
  add an entry point needing immediate revision. Spec: **[completed/spec-ai-menu.md](completed/spec-ai-menu.md)** ▸ D-AI5.
  Original note follows — a
  runbook pointer is skill-shaped in storage but not in discovery: a model must open every file to know which
  is relevant. Add optional `runbook.N.description` (inert, gated like the name), serve it in
  `context.runbooks[]`, show it on the Project-panel row. The file-shape convention (frontmatter `name`/
  `description`, so a pointer may target a `SKILL.md`) is documented ahead of it, so no runbook needs
  rewriting. Content delivery stays as it is: the analyser serves the pointer, never the instructions.

## M43 · The AI menu — follow-up (COMPLETE 2026-08-28; the milestone is in completed/tracker.md, design **[completed/spec-ai-menu.md](completed/spec-ai-menu.md)**)
- ☐ **Owner question: the menu's name** — shipped as `AI` (proposed over *AI assistant*, since "assistant" names the
  in-app panel and the docs nav settled on *Working with AI*). Rename is a one-line change if the owner prefers otherwise.
- ☐ **D-AI9 wording addendum (owner's call, from the `c4d1db3` review)** — the light's reclaim is a POLICY: when the
  window owning the endpoint closes, the survivor re-publishes and an AI client mid-session silently reaches the
  survivor's log, where a person sees the light change. Both reviewers judge it the right policy (a dead endpoint hides
  the same change behind a hard failure); the spec should name the residual, not only the choice. One line in
  `completed/spec-ai-menu.md` ▸ D-AI9; no code.

## Framing · The trust structure — "AI you do not have to trust" — ☐ PROPOSED 2026-08-29
_Owner-directed. Spec **[spec-trust-structure.md](spec-trust-structure.md)**. Not a milestone: it creates
little new work and instead CONSTRAINS existing work. Read it before any change that loosens what the
analyser is willing to assert._
- The position: regulated buyers are blocked on agentic AI because nothing an agent produces can be
  independently checked. The answer is not to explain the model — it is to make the model's output
  checkable against a record the model did not write.
- **D-T1** do not say *explainable AI*: it is a term of art meaning model interpretability, we do not do
  that, and a buyer who hears it will correctly conclude we do not fit. Say *verifiable* / *independently
  recorded* — which asks them to believe nothing about the model.
- **D-T3** the distinction that carries the position: an agent's account is TESTIMONY, the audit log is
  EVIDENCE **about execution** — produced by running, not by narration. Why the record must be on by
  default: a log enabled after an incident is not evidence of the incident.
  **BOUNDED (review F5, 2026-08-29):** it is *not* tamper-evident, *not* authenticated, and *not*
  independent of whoever wrote the logging calls — the analyser parses a hand-written log, and an agent
  that authors the project writes the `auditLog` calls. Origin rests on declared provenance and on
  trusting the runtime. Never claim more than that.
- **D-T4** every refusal in the analyser is now load-bearing rather than tasteful. A change that makes it
  assert more than the record supports is **a change to the market position**, and reviewers should treat
  it as one.
- **D-T6 ☐ OPEN, and the most valuable thing to learn:** what is the forcing function for the first
  serious prospect, and does it have a date? Regulated industries tolerate pain for years; availability of
  a better answer is not what moves them. If the answer is "eventually", runway changes, not direction.
- Evidence is measured and none of it was produced for this document — including a simulated regulatory
  return that was FALSE (*"7 of 7 foreseen"*, actually 0) and was refuted only by the record.
- ☐ **Trust-boundary amendment (review F5)** — a supplied audit record is evidence about the recorded
  execution only within a declared, trusted runtime/deployment boundary. The analyser does not establish
  the record's origin, completeness, semantic correctness or freedom from author influence; narrow the
  spec and buyer-facing wording before treating "independently recorded" as a market claim.

## M39 · Baselines — "is this normal here?" — ☐ SPEC'D 2026-08-27 (owner decision 4; spec **[spec-baselines.md](spec-baselines.md)**)
- [M39] ☐ **Baselines** — ☑ **SPEC'D 2026-08-27**, `spec-baselines.md`. "Is this normal here?" — the
  question support cannot answer about a system they did not build, and the one a deterministic record
  uniquely can. Five decisions, the load-bearing two: **D-N1** a baseline is a NAMED REFERENCE RUN, never
  an abstract "normal" (an abstract normal is unfalsifiable authority — nobody can check it, and when it
  disagrees with reality there is no way to tell which is wrong); **D-N3** a comparison prints TWO
  measurements and no verdict, because a scoring tool becomes a tool people ignore after its first false
  alarm. Keyed per environment (M38.3), offered never automatic (M35–M37 spent three milestones removing
  things that fire at load), and it carries no log data. Slices M39.1–.5; four open questions for the
  owner, the first being where a baseline lives.

## M40 · Audit readiness — follow-up (.1/.2a/.2b/.3 COMPLETE 2026-08-27; the milestone is in completed/tracker.md; post-merge review `docs/handoff/completed/review_main_m40_2b_3.txt`)
- [M40.2c] ☐ **Follow the supertype chain** _(optional)_ — a node extending a project-local base that itself extends
  `EventLogNode` currently lands in UNKNOWN and stays counted. Correct but conservative; resolving one more hop needs
  the file's imports (`EventProcessorModel.resolveSimpleType`).

## M41 · One-command install — ☒ **WITHDRAWN 2026-08-27** (owner: "we use jbang, it just works")
_Spec'd the same day as a `jpackage`/Homebrew/`--print-mcp-config` milestone and withdrawn before it started. The
premise was wrong: `jbang app install analyser@telaminai/fluxtionauditlog-analyser` IS the one-command install (JBang
fetches a JDK itself), `~/.jbang/bin/analyser` is the stable launcher path the MCP recipe already uses, and `--rest`
already turns the transport on without editing `config`. Native bundles would have added a Dock icon and a signing
bill. Recorded under Decisions so it is not re-raised; reopen only when a user who cannot run JBang actually appears._

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

_Refreshed 2026-08-28. Shipped since the last refresh: **1.11.0** (M42 connect an AI client), then **M33.7** report
table sources and **M43** the AI menu (+ M38.8), both reviewed SOUND on main, unreleased. **M41** was spec'd and
withdrawn. Open on main: two ledger entries (`ac6a559` the status-light poll; `7e8e859` the Mongoose spec addendum)
and the M43 menu-name question for the owner._

1. **Release 1.12.0** — gates passed 2026-08-28: 1069 green, `mkdocs --strict`, sweep, ledger clear (archived to
   `handoff/completed/unreviewed-changes-2026-08.md`), eyeball CHECK C passed by the owner (ready → elsewhere in amber →
   ready in green). `[Unreleased]` carries M33.7 + M43 + three fixes.
2. **M39 baselines** — spec'd; the mixed-version hazard is built (M38.7, D-C10). Next model-level feature.
3. **The Mongoose bootstrap artefacts** (`docs/specs/mongoose-bootstrap-artefacts/`, reviewed with §10a A1–A4
   written in) — anchor to `spec-agent-brokered-dev-loop.md` and back its gates with `tools/bench/loop-bench.py`
   before filing UP-MNG-01…04.
4. **M34.4/.5** (first foreign adapter; per-cycle concurrency marker — needs the owner to name the field).
5. **M19.1a** (Mongoose starter conformance bench: D-02 then the first typed slice; no bundle claim
   before its native audit and conditional VAL-12 evidence), **M19.3/.4** (tutorial, publish-gated on
   the playground Download), and **M19.8** (bench in CI).
6. **The small schedulable remnants**, any time: **M40.2c**, **M20.5** (project artifact pointers — tier 1 of M38's
   model, share its path validation), **M29.5**, **M13.5**, **M21.7–.9**, the **M22** five
   (`docs/handoff/completed/handoff_17_aug_2026_1.txt`), **M33.5** (gated), **M33.6** (owner said YES), the M36
   rule-1 upstream ask.
7. **Cross-repo — the §H gate is MET; DRAFTED and READY TO FILE, still unfiled: UP-MNG-01…04, UP-PG-01…02,
   UP-RDR-01 in [upstream-asks.md](../proposals/upstream-asks.md) §5–§7**, **UP-MNG-03** (the server supplying the environment) has its analyser-side
   counterpart in M38.3: where both exist the declaration wins and `context.provenanceSource` says so.
8. **M12** (diagnose → fix → prove) stays active design; **M11** stays vision until a real Grafana consumer appears.

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
- **Distribution is the shaded fatjar + JBang; no native bundles** _(2026-08-27)_ — `jbang app install analyser@…`
  is the one-command install (JBang supplies the JDK), `~/.jbang/bin/analyser` is a stable launcher path for MCP
  configs, `--rest` enables the transport without a config edit. A `jpackage`/Homebrew milestone (M41) was spec'd
  and withdrawn the same day: it would have added a Dock icon, a four-runner release matrix and a code-signing
  bill, and solved nothing a user has asked for. Reopen only for a real user who cannot run JBang.
- **The MCP server identity is `fluxtion-analyser`; its executable need not share that name** _(2026-08-27)_ — a
  client registration names the server independently and launches a resolved absolute command. The current JBang
  launcher remains `analyser`; M42 uses it rather than making an install-name migration a prerequisite. A compatible
  `fluxtion-analyser` JBang alias is welcome only after it has been proven to coexist and upgrade cleanly.
- **Rendering stays Swing/Java2D — no embedded browser.** Reusing the JS replay engine via JCEF/JavaFX
  WebView would cost a ~100MB native per-platform dependency and destroy the single shaded fatjar that
  `jbang analyser@…` depends on. FlatLaf remains the only runtime dependency; a hand-rolled layered
  layout is the work, with pure-Java ELK as the fallback (spec-graph-replay §3).

## Open questions

- Graph "last occurrence per record" vs "all occurrences" default. (spec: last; expose toggle.)

_(spec-closed-loop O1–O4 all resolved — statuses recorded in the M18 block above; O5 in Decisions.)_
