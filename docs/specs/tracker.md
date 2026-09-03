# Fluxtion Audit Log Analyser — Work Tracker

Companion to **[spec.md](spec.md)**. Status keys: ☐ todo · ◧ in‑progress · ☑ done · ⊘ dropped.

Legend for each item: **[id] status — title** · _acceptance_.

---

## Shipped — archived

**Tidied 2026-08-30 (rule 7).** Fourteen shipped M19 slices, plus the closed M18 and withdrawn M41, moved
to [`completed/tracker.md`](completed/tracker.md) verbatim. This file went 949 → ~720 lines. Relative links
in the moved blocks were rewritten for their new depth — `SpecLinksResolveTest` caught four that a move
silently breaks, which is what it is for.

**The rule, stated once so two claims cannot both be made** (review F3): rule 7 says a finished item is
ticked **☑ here**, and only a fully-shipped milestone or round MOVES. So this file legitimately holds ☑
items — they are work completed since the last tidy, and they leave at the next one. It is **not** true
that this file contains open work only, and an earlier version of this note said so while ticking two
items in the same commit.

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

## Upstream template content — three drafts, ready to be taken

_`docs/proposals/upstream-content/`. Drafted FOR the static authoring resources
(`claude.txt`, the playground `CLAUDE.md`, the golden path), not for this repo. Each carries a
retrieval-dated evidence table, because those are live documents that can change under a claim._

- [UC-LANDED] ☑ **MERGED AND LIVE 2026-09-01 — [fluxtion-web PR #1](https://github.com/telaminai/fluxtion-web/pull/1)**,
  merged to `main` as `a4a124f` and deployed by Cloudflare (`5bf137a4`). **Verified on the live URLs**, not
  just in the repo: `/CLAUDE.md` 20,453 → 25,059 bytes and `fluxtion-golden-path.md` 13,179 → 15,167, both
  carrying `NoTriggerReference`, the finality rule and the green-build section. That distinction mattered —
  the first check after merge showed the OLD text, because the repo and the site are separated by an
  external deploy with its own timing. **The cascade is now real**: every project referencing the agreed set
  gets this without being regenerated. Content went to the two resources that are in the AGREED SET — `/CLAUDE.md` (the
  orientation) and `fluxtion-golden-path.md` — **not** to `claude.txt`, which `reference-set.json` records
  as deliberately EXCLUDED ("redundant, not wrong"). Writing there would have been writing where no
  generated project points. Branched from `main`, not the in-flight `feature/spring-author-extended`.
  **Selected by D-AX1's discriminator — silent failures only:** the plain-reference-is-a-trigger rule
  (`@NoTriggerReference`), one-`@OnTrigger`-not-many with the dirty-flag overwrite, audit wiring before
  `init()`, and the bean-style→constructor workflow. **Plus one correction the run forced:** the triage
  said constructor mapping is triggered by RENDERABILITY and that primitives are safe — a `final int`
  failed FLX-1009 in the run, so the rule is FINALITY, and the old text sends an author looking elsewhere.
  **Deliberately NOT written (D-AX3 shrink, first time with evidence):** the `@FluxtionIgnore` repair
  itself. FLX-1009 named the field and its import unaided, three times, one-step repair each — prose that
  pre-empts a good error message is dead weight. Evidence:
  [`ASSESSMENT-diagnostics-2026-09-01.md`](../experience/runs/ASSESSMENT-diagnostics-2026-09-01.md).
  ☐ **Still to place:** the phase/hook ordering table (filed separately as
  [fluxtion#29](https://github.com/telaminai/fluxtion/issues/29), different destination), and UC1's
  `EventLogSource` contract detail — partly carried now by FLX-1008's own `suggestedFix`.
- [UC1] ☐ **`audit-authoring.md`** — how a node participates in the audit log. The three conditions, the
  `EventLogSource` contract, the `addEventAudit` overloads.
- [UC2] ☐ **`audit-runtime.md`** — getting the log OUT. Six measured wirings, the `System.out` default
  sink, `logLevel()` after `init()` being a silent no-op, and the fact that the audit "setters" are
  dispatches. Evidence **re-verified unchanged 2026-09-01**.
- [UC4] ☐ **`idioms-and-canonical-form.md`** — NEW 2026-09-01, owner-directed, and the one the other three
  imply. They add FACTS; this maps **the shape you are trying to build** to **the construct that builds
  it**. The thesis is measured: over one real twelve-node graph, *every* mistake worth recording was a
  case where the structure was defensible and the framework had a better construct — none was a
  misunderstanding of dispatch, and none would have been caught by a diagnostic. Each was found by being
  corrected by someone who knew the idiom.
  **Two undocumented facts decide half of it**, confirmed by retrieval: `reverse topological`,
  `@AfterTrigger`, `processReentrantEvent` and `processAsNewEventCycle` are at **zero occurrences in all
  three sources**, while `@OnTrigger` appears 21 times in `claude.txt`. The annotation reference is
  strong; the two-phase execution model and re-dispatch are absent.
  Six idioms: derive with one `@OnTrigger` rather than N handlers (with the generated OR-of-dirty guard
  as evidence); state from events, services for query or action, plus the *state-snapshot-pretending-to-
  be-an-event* smell; side effects belong in the after-event phase, and when to go outside instead;
  re-dispatch and its two routes; the three threading options; and field wiring.
- [UC5] ◧ **Idioms audited against the app that produced them — 2026-09-01.** A document that says "do X",
  written by someone doing Y, is not evidence. Audited, with results recorded rather than tidied:
  ☑ **Idiom 1 applied AND corrected by the audit.** Six nodes still carry several handlers, and **five
  are right** — a gate validating ten different correlation ids, an outcome recorder whose seven handlers
  each contribute a *different* effect name, state nodes whose events are transitions rather than
  recomputations. Collapsing on handler COUNT would have destroyed the data they carry. The doc now
  states the real test — one derivation from several inputs, versus each event contributing different
  data — with the counter-case table. **The idiom was over-applicable as written, and only building
  against it showed that.**
  ☐ **Idiom 2a not applied, and it is our own named smell.** `LogObserved(boolean open, …)` and
  `GraphObserved` are state snapshots pretending to be events — the exact shape the doc warns about,
  still in the app. Sequenced, not ignored: the honest events (`LogOpened`/`LogClosed`) come from the
  open path, which [M44.3](spec-async-session-driver.md) unblocks.
  ☐ **Idiom 2b not applied, and actionable now.** The source resolver is hand-threaded as a
  `Function<String, Optional<String>>` across **8 call sites** — already service-shaped, and the doc's
  own live candidate.
  ☑ **Idiom 3 PROBED 2026-09-01, and the probe beat both of my arguments.** Added an `@AfterEvent` method
  to `EffectQueue`, regenerated, and read the emitted `afterEvent()` block. It runs
  `effectQueue.probeAfterEvent()` **first**, then `clock.processingComplete()`, then
  **`eventLogger.processingComplete()` — which is where the cycle's audit record is published** — then
  resets the dirty flags. So **`@AfterEvent` runs BEFORE the audit record exists.** For a processor whose
  log is the product that ordering is the wrong way round: the irreversible act would happen before the
  evidence of deciding it, and an effect that threw could cost the decision record too. The external
  drain runs after `onEvent` returns, so it guarantees **decided, recorded, then acted** — a sharper and
  more specific reason than either "the result must re-enter" or "opening is async", both of which are
  general rather than about this product. Recorded in the spec and in the idioms draft.
  ☑ **Idiom 2b applied, and it corrected the doc a third time.** `SourceResolver` is a named interface
  across the eight call sites — but auditing first showed the doc's "live service candidate" was **not
  one**: nothing in the graph resolves source, so it wanted an interface, not a service registration. The
  doc now carries the test that would have caught it — *if no node queries it you want an interface; if a
  node queries it you want a service*.
- [UC3] ☐ **`node-field-wiring-and-workflow.md`** — NEW 2026-09-01. Two halves of one gap.
  **The rule:** *final* is the trigger for constructor mapping, and the word appears **nowhere** in any
  of the three sources — nor do `non-final`, JavaBean setter-wiring, or `@ConstructorArg`. The canon
  covers how to STOP a field being mapped and never what decides that it is. Route 3 — a non-final field
  is setter-wired and never constructor-mapped — is the one three measured agents found by accident and
  none could explain.
  **The workflow:** develop bean-style, harden to constructors when the shape settles, treat the
  migration as one deliberate break. Measured — four constructor-shape breaks while the node set churned,
  none after it stabilised — and **no diagnostic can carry it**, because it is advice about the order to
  work in rather than a failure to report.
  _Also records what is already RIGHT and must not be touched: `claude.txt` states the exclusion remedy
  with its FQN and says the field initialiser still runs, which this repo measured independently before
  finding it already documented._

## M45 · Consuming the GraphML vocabulary — ◧ .1/.2/.3/.5 SHIPPED 2026-08-31

**Shipped detail archived to [`completed/tracker.md`](completed/tracker.md) on 2026-09-03.** Open slices only below.

- [M45] ☐ **The vocabulary answers as DATA what we answer by HEURISTIC** — framework-generated nodes,
- [M45.1c] ☐ **Original slice text, for the record — prove reachability, and measure the ceiling.** Install the branch, point `-Pregen` at it
- [M45.2b] ☐ **Original slice text — read the vocabulary, change no behaviour.** `metaVersion` (1.x reader accepts every 1.y;
- [M45.3a] ☐ **Original slice text — the audit trio is a DUO** — `auditCapable`/`auditCapableVia` are emitted, `eventAudit` is
- [M45.5a] ☐ **Original slice text — parallel edges and dispatch rank.** Checked, not assumed: `ProcessorTopology.of` does no
- [M45] ☐ **Backwards compatibility, assessed — and the risk is not in the GraphML.** At `OFF` the only
  our parser against before/after at `dd36bc5` and found adjacency and node facts identical. ☐ **That

## M44 · Session transitions as a Fluxtion processor — ◧ SLICE 1 SHIPPED 2026-08-31

**Shipped detail archived to [`completed/tracker.md`](completed/tracker.md) on 2026-09-03.** Open slices only below.

- [M44] ☐ **Spec written: [`spec-session-processor.md`](spec-session-processor.md).** Session transitions
  recommends.** ☐ **Blocker before the dependency lands:** the runtime's published POM declares AGPL-3.0
  jar. ☐ **Residue:** the five dialog-only entrances (`ADOPT_FOR_OPEN_LOG`, `CREATE`, `FORK`,
- [M44.3] ☐ **SPEC'D 2026-08-31: [`spec-async-session-driver.md`](spec-async-session-driver.md)** — the
  ☐ **New surface it unblocks:** a hung load is today indistinguishable from no load; the processor will
- [M44.2x] ☐ **Original next-slice list:** `IgnoredParameters`, then split `GraphPairing` /
- [M44.3] ☐ **Owner decision still open:** the runtime's published POM declares AGPL-3.0 and the analyser

## M19 · Onboarding example — playground download → running Mongoose → analyser — ◧ IN PROGRESS
_Design: **[spec-onboarding-example.md](spec-onboarding-example.md)**. The playground's Download button
ships a runnable Mongoose example with Chronicle audit capture pre-enabled and one YAML export command
targeting a predictable project-relative path,
bundled source, and a **project profile at `.analyser/project.fluxtion-settings`** (M20's canonical path —
the bundle *is* a project profile) — so onboarding becomes: download → run → jbang the analyser →
project auto-loads (M20; **File ▸ Import** until it lands) → Follow a live log with click-to-source and Explain working.
Target: under 10 minutes on a fresh machine with only a JDK. The bundle's README links back to the
analyser (reverse funnel)._
- [M19] ➜ **SECOND ARCHIVE PASS 2026-08-30** — five more completed slices moved to
  [`completed/tracker.md`](completed/tracker.md): **M19.21**, **M19.20**, **M19.14a**, **M19.14**, **M19.5**. Two contradictions were
  removed on the way: M19.5 carried both an ACCEPTED and an AWAITING-REVIEW entry, and M19.21 carried
  both a SHIPPED one and a "brief written, not pushed yet" one that had been true for two hours. Both
  are the drift an accumulating tracker produces, and both were found by counting duplicate ids rather
  than by reading.

- [M19] ➜ **SHIPPED SLICES ARCHIVED 2026-08-30** — fourteen completed slices moved to
  [`completed/tracker.md`](completed/tracker.md) per rule 7. What remains here is open work **plus any
  slice finished since that tidy** (☑, awaiting the next one) — see the note at the top of this file.
  Archived: **M19.2** (SettingsShare relative roots), **M19.4** (cross-links), **M19.6–.9**
  (the loop bench, agent-driven fresh start, bench green in CI, headless launch args), **M19.10**
  (canonical skills), **M19.11** (onboarding bench), **M19.12/.12a** (key management and licence
  placement), **M19.13** (day two), **M19.16–.18** (review amendments, bundle contract v2 then v3).
  Nothing about them changed; they are findable there with their commits and evidence.

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
- [M19.15] ☐ **The seeding prompt for step 2** _(owner, 2026-08-29; spec has it verbatim)_. Step 2 is only
  a measurement if the prompt does not contaminate it: leading the witness produces agreement, manufactured
  hostility produces theatre, instructing the task tests the prompt instead of the docs, and revealing it is
  a test makes the model evaluate rather than use. **The risk that is easy to miss is not failure — it is
  SUCCESS BY COMPENSATION**: a capable model fills a gap from training data or by reading generated source,
  finishes the task, and the gap is invisible. So the prompt's job is to make compensation VISIBLE, not to
  prevent it. Measurement is mostly external — the git history, the code and the audit log are evidence; the
  model's account is testimony (D-T3 applied to assessing the product).
- [M19.23] ◐ **UP-PG-02 `agentBootstrap` — IN PROGRESS, playground session** _(2026-08-30)_ —
  plan: [`plan_playground_agent_bootstrap.txt`](../handoff/plan_playground_agent_bootstrap.txt). No spec
  exists (D-B5 lists it as "still open, and NOT specified here"), and the catalogue is a contract the
  M19.5 picker consumes, so the shape is being agreed before it ships rather than after.
  **Evidence the ask did not have:** all fourteen templates were generated — `analyser-bundle` ships
  `CLAUDE.md` + `AGENTS.md`, the other **thirteen ship neither**. So the field carries real information.
  **The gap that matters more than the field:** the `onboarding` subset the picker lists is TWO
  templates and only ONE ships agent instructions, so a user choosing `fluxtion-spring-mongoose` from
  inside the analyser gets a project with no CLAUDE.md, no AGENTS.md and no skills — chosen from a list
  whose purpose is onboarding. The field DISCLOSES that; it does not fix it. **Whether `onboarding`
  should MEAN "arrives ready for an agent" is an analyser decision** (it owns the selection rule,
  spec-template-from-analyser D-1) and is explicitly not being taken unilaterally. Shipping now: the
  field, plus a test that generates each template and asserts the field matches what the project
  actually contains, because a hand-maintained boolean rots in a week.
- [M19.22] ◐ **The generated processor's header claims CONFIDENTIALITY — filed as
  [fluxtion#24](https://github.com/telaminai/fluxtion/issues/24)** _(found 2026-08-30 against the live
  bundle)_ — [`spec-onboarding-example.md` ▸ D-B6](spec-onboarding-example.md). Every generated processor
  carries *"This file is confidential and only available to authorized individuals"* plus all-rights-
  reserved, **into the user's own repository**, in a starter that exists to be built on. The live bundle's
  copy additionally names a personal address on a vendor domain (one of rule 1's four terms; the analyser's
  demo copy does not, so that line is version-dependent or stripped somewhere).
  **THE ANALYSER IS AFFECTED TOO** — `src/main/resources/demo/com/acme/demo/generated/DemoQuoteProcessor.java`
  carries the same confidentiality notice and ships **inside the analyser jar**. Two independently generated
  processors, same header, so it is the generator's template.
  **Deliberately NOT hand-patched here.** The demo processor is a generated artefact reproduced by
  `examples/fixture-generator/`; editing the shipped copy would make it an unfaithful example and would
  drift from what the generator emits — which is precisely the "every check ran on a repaired copy" failure
  the playground just spent a day on. It is fixed when #24 lands and the fixture is regenerated. Recorded
  here so the exposure is visible rather than forgotten.
  **UPDATE 2026-08-30, verified against production independently:** the playground scrubbed the bundle and
  the live zip is now clean on both the four-term sweep and on confidentiality/rights language, with a
  provenance-only header that says what generated the file and how to regenerate it. **The scope of #24 is
  unchanged.** The playground can only scrub the one artefact it commits; the generator still emits the old
  header to anyone who regenerates — and **our own canonical `add-a-node` skill hands users exactly that
  command**, so the analyser routes people into it. The starter has stopped being a carrier; the generator
  has not. Exposure window on the live bundle was roughly two hours (their measurement, not ours).
  **UPDATE 2026-08-30 (comment on #24):** the playground has scrubbed its bundle, verified here against
  production — so **the analyser jar is now the remaining public carrier**, and the generator keeps
  stamping every user who regenerates. Still deliberately not hand-patched; it clears when #24 lands and
  the fixture is regenerated. **No second issue was filed:** #24 already carries the analyser demo as
  evidence item 2, and a duplicate would dilute the one that exists rather than add to it.

- [M19.19] ◧ **Guided start — an install prompt, and an LLM tutor that drives the UI** _(owner idea,
  2026-08-30; **and the experiment's baseline**, D-G8)_. **Skill and docs page shipped; first real drive
  done 2026-08-30** — [`runs/guided-start-01/run.md`](../experience/runs/guided-start-01/run.md). All
  three beats work against a running `--rest` analyser, and the drive found two defects in the skill:
  **beat 2 reported ZERO on the traced demo log I had chosen** (the distinctive beat, showing nothing —
  it now uses the untraced log where one node is uncovered, and reads the analyser's own
  "never logged, not never ran" note aloud, which is a stronger demo than the number); and `flag` takes
  `recordIndexes[]`, so the sketch would have made an agent guess wrong in front of the audience. Also:
  no verb lists graphable keys, the demo set cannot be installed by the agent (a second human pause), and
  a returning analyser restores its previous session. **STILL OPEN:** the held-out run — a fresh
  context-free client following the docs-site prompt end to end. Nothing so far substitutes for it. — [`spec-guided-start.md`](spec-guided-start.md). Zero to a running analyser showing
  capabilities, driven by a prompt an LLM executes. **Verified: the tutor needs NO new verbs** — `open`,
  `filter`, `topology`, `goto`, `graph` and `flag` already drive the UI, and `context` + `screenshot` tell
  the agent what the user can actually see. The load-bearing rule is D-G2, from D-T3: **the tutor points,
  the screen proves** — it may not state a figure the user cannot see, which makes the tutorial a live
  demonstration of the thesis rather than a chatbot describing software. Setup is shell, not analyser
  surface, and the whole path is **keyless** (a bundle ships its generated processor). One real gap: MCP
  registration is an in-app flow — v1 asks the human to do it rather than adding a headless path. Also
  D-G5: this is the best held-out task the experience loop has, because its outcome is objective.
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
- [M19.3] ◧ **Tutorial page corrected against the shipped bundle; screenshot set in progress.**
  `docs/site/tutorial-playground.md` now uses the real Audit analyser bundle name and concrete paths,
  opens project/GraphML/log separately, distinguishes a fixed export from a followable log, teaches
  Explain/copy-prompt/MCP, and refuses to promise a two-run diff that is not built. Four generated
  screenshots now show the real bundle's project, log, PriceEvent cycle and source navigation; three
  existing isolated-DEMO figures cover graphing, the AI menu and MCP setup. The remaining spec captures
  are the live playground Download, terminal lifecycle, an actual Explain answer and IDE edit; no connected
  browser was available in this session, so those were not fabricated. **RESOLVED 2026-08-30:** the
  producer gap that blocked the graph step is fixed — the released bundle now logs `price` and `volume` as
  numeric keys, the tutorial tells readers to chart one from their own run, and the four screenshots were
  re-shot against the real bundle. The DEMO chart is kept only because five cycles make an illegible plot,
  and the page says so rather than implying the demo is the bundle. Remaining: the three neutral captures
  that need a connected browser (live Download, terminal lifecycle, an Explain answer).
## M21 · Topology view + step-through — ◧ CORE SHIPPED (archived; 21.7–21.9 open)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M21.7] ☐ _(later)_ server-sourced GraphML via `GET /api/processors/{group}/{name}/graphml` (needs M18.1).
- [M21.11] ☐ **Consume the declared trace flag** _(owner ask 2026-08-17; needs UP-FLX-11 upstream)_ —
- [M21.9] ☐ **Use `ProcessorDescriptor` instead of inferring** _(found 2026-08-16 reading a generated
- [M21.8] ☐ _(later)_ **node → flag** — "flag every record where node X fired" needs an `instanceId`

## M22 · Topology view usability — ◧ 36 of 41 SHIPPED (archived; 5 open)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M22.3] ☐ **Export the view as PNG** — reuses the offscreen render already used to verify the canvas;
- [M22.6] ☐ **Alternative layouts** — the largest item. `LayeredLayout` is Sugiyama; candidates are
- [M22.11] ☐ **Re-dispatch (`processReentrantEvent`) — show the cause.** A node can raise an event on its
- [M22.20] ☐ **A DataFlow `.push()` target renders as an orphan.** *Measured 2026-08-17 against a probe

## M29 · External series — ◧ SHIPPED 2026-08-18 (archived; M29.5 optional embed open)
_M29.1–.4 shipped, reviewed and merged — full record in **[completed/tracker.md](completed/tracker.md)**.
Design: **[completed/spec-external-series.md](completed/spec-external-series.md)**._
- [M29.5] ☐ *(optional, owner decides)* **`embed: true`** — carry small series inside the saved graph
  for fully-portable sharing (D-F5's alternative).

## M31 · Log-source plugins — ◧ SHIPPED 2026-08-18 (archived; example reader is cross-repo)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M31.5] ☐ **NOT YET** _(owner, 2026-08-27)_ · **Separate `analyser-reader-spi` artifact** — needs a multi-module

## M33 · Investigation reports — ◧ CORE SHIPPED 2026-08-20 (archived; M33.5 gated)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M33.5] ☐ **Fold M12.1's fix-brief onto the model** (D-I6) — after the closed-loop precondition
- [M33.6] ☐ **YES — build it** _(owner, 2026-08-27; support are non-agent users and the CSV source is

## M36 · Start page — follow-up (.1–.5 SHIPPED 2026-08-25; the milestone is in completed/tracker.md, design **[completed/spec-start-page.md](completed/spec-start-page.md)**)
### Rule 1 — owner decisions (raised M36, sharpened by the polish round; the two resolved ones are archived with M36)
- ⚠ **ANSWERED 2026-09-01: YES, it still does — measured on a build against the deployed 1.0.65 backend.**
  Every generated processor opens with:
      Copyright: © 2025.  Gregory Higgins <…> - All Rights Reserved
      This source code is protected under international copyright law…
      This file is confidential and only available to authorized individuals…
  Stamped onto the USER'S generated code — an artefact derived from their graph, which upstream's own
  positioning calls the deliverable. Two problems, and neither is the analyser's to fix: it asserts
  all-rights-reserved and CONFIDENTIALITY over a file the user generated and ships, and the year reads
  2025. Raised with the owner as a licensing decision rather than changed unilaterally.
  **Originally:** an upstream ask, not an analyser one.

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

## M46 · Authoring-toolchain repair — ☐ SPEC'D 2026-09-01

Spec: [`spec-authoring-toolchain-repair.md`](spec-authoring-toolchain-repair.md). Evidence:
`docs/experience/runs/round-07…10` — 23 fresh-context runs, two model tiers, predictions committed first.

**The framing that makes this one programme rather than a list:** the framework's core claims held
perfectly — **M5 and M6 were never violated by any agent in any run**, and 22 of 23 runs produced a
correct graph. **Every item below is a communication failure, not a correctness failure.**

- [M46.1] ☐ **U1 · Structured diagnostics never reach the console.** `code`/`rule`/`suggestedFix` go to
  the sidecar; the default path prints a raw `DiagnosticException` in 60–80 lines of stack trace. Round 08
  measured the good version only because it passed `-Dfluxtion.diagnostics.sidecar=true`. **Highest-value
  item in the programme** — the diagnostics work, they just are not where authors read. Upstream.
- [M46.2] ☐ **U2 · `target/classes` lags the generated source by one build.** A run straight after a graph
  change executes the PREVIOUS graph and writes an audit log describing a version that no longer exists.
  Found independently by two Opus agents. Upstream (plugin warning); docs half **DONE** in `142b1e1`.
- [M46.3] ☐ **U3 · `scan` silently no-ops with no builder.** A project containing zero Fluxtion code builds
  green — which let an agent ship plain Java and report all six requirements met. Upstream.
- [M46.4] ☐ **U5–U8 · bootstrap deadlock, audit "setters" that dispatch, lifecycle records, `addEventAudit`
  naming.** Four agents hit the bootstrap; four hit the log pollution. Upstream + docs.
- [M46.5] ☐ **A1 · the first pairing/coverage verdict after `open` is computed against pre-call state.**
  Reported by **all four Opus agents**; one reproduced it on three instances including an isolated
  `user.home`. Worse than a wrong number: `read-audit-log` tells the reader to check `graphPairing`
  before concluding anything, so following our own guidance on a first open discards a correct graph.
  **Ours, and the priority.**
- [M46.6] ☐ **A2 · the REST endpoint hangs permanently.** Two routes: a modal on a load path (`jstack`:
  `showConfirmDialog` ← `maybeOfferProject` ← `onLoaded`), and mixed `coverage`/`topology` calls. Kills the
  agent API silently. **`verify-session-transitions.py` cannot catch the modal** — it never opens a log
  inside a project directory over the socket. Ours.
- [M46.7] ☐ **A3–A5 · `open` publishes the VISIBLE node count as `nodes` (12 for a 22-node graph), state
  leaks across instances via the shared user home, `topology` reports `rowCount: 0` with records open.**
- [M46.8] ☐ **X1–X4 · doc gaps** — how to write the audit log to a file, the `addEventAudit` three-arg
  overload, and the fact that every skill describes a project that does not exist in a fresh template
  (reported by every agent in every round).
- [M46.9] ☐ **H1–H2 · harness.** Parallel runs shared one analyser and one `user.home` — one agent
  `pkill`ed another's JVM mid-sequence. And round 09's own specification was self-contradictory (M3 vs M4),
  found by all four Opus agents, who all invented the same repair. Isolate runs; check a behaviours spec
  for internal consistency before using it as an oracle.

**Deliberately not in scope:** making the compiler catch design errors. Four rounds produced no idiom
error a diagnostic could have caught, and the one real design defect was caught by reading the generated
source.

## M47 — start from a template (proposed, owner's steer 2026-09-01)

**The idea.** The analyser offers a set of Fluxtion project templates. A user picks one; that is what
their AI client starts from, rather than an empty directory plus prose.

**Why it belongs here rather than upstream.** A template that already runs produces an audit log on
first execution — which is the one thing the analyser needs and cannot supply for itself. Today a new
user has an analyser and nothing to open.

**The measured case.** Round 16 (`docs/experience/runs/round-16/`) established that four blockers
dominate authoring cost, and that all four are absent from the published reference: the bootstrap trap
(`Main` imports the generated class, so compilation breaks generation), `com.fluxtion` vs
`com.telamin.fluxtion`, parents-are-fields (FLX-1001), and how to run a Maven build. A working
template removes all four **structurally** — there is no sentence to skim past. Two independent agents
and one session author all hit the bootstrap trap *after reading a warning about it*, which is the
strongest available argument that prose is the wrong instrument for this class.

- [ ] **M47.1** the template itself — `docs/experience/current/template/` exists and is green today
      (`./run.sh` builds, generates, tests, runs; two nodes, propagation arrest visible in its own log)
- [ ] **M47.2** template picker in the analyser; scaffold to a chosen directory
- [ ] **M47.3** the scaffolded project registers as a project profile, so its log opens on first run
- [ ] **M47.4** more than one template — the shape catalogue, not just a hello-world

**Open question, not yet answered:** whether a template plus a short pointer beats the full published
doc set, or whether it is additive. That is what round 17 measures.


## M48 · Authoring modes — the catalogue resolver, the mode selector, the scorer — ◧ PART SHIPPED 2026-09-03

Specs: **[`spec-authoring-modes.md`](spec-authoring-modes.md)** (the taxonomy, the owner's eleven items,
the delivery order), **[`spec-authoring-mode-selector.md`](spec-authoring-mode-selector.md)** (the end
state, the handoff contract, the analyser's role),
**[`spec-authoring-session-walkthrough.md`](spec-authoring-session-walkthrough.md)** (four sessions, real
output). Evidence: `docs/experience/runs/round-5[3-7]/`.

**The finding that reorganises the programme.** The bean-file half of component integration is a
**constraint solve, not a model task**. `tools/bean-resolver.py` reproduces the measured-optimal
selection *and* wiring from jar manifests alone, builds green, and produces **byte-identical alerts** to
the reference — at **zero token cost** against the measured optimum's 1.98M weighted / 51 turns. Where
the declared surface cannot decide, it reports the ambiguity and refuses to guess.

**So four modes, and which one you are in is derived, not chosen:**

| mode | who writes what | model needed | status |
|---|---|---|---|
| 0 / 0+ | nobody; a resolver emits the bean file | **no** | **built, verified** |
| 1 | selects components, writes beans | selection only | measured (r55, r57) |
| 2 | describes beans, then writes the nodes | yes | **unmeasured** — playground's `spring-authoring/*` is the baseline |
| 3 | writes a Java builder and the nodes | yes | **unmeasured** — `CLAUDE.md` + golden path is the baseline |

- [M48.1] ☑ **the resolver** — `tools/bean-resolver.py`; unique selection identical to the optimum,
      green build, byte-identical alerts (round 57)
- [M48.2] ☑ **selection is memoisable** — a `Fluxtion-Convention` manifest field plus a one-line site
      profile resolves round 55's six-way type-identical ambiguity; changing the profile word changes the
      selected component (round 57 addendum)
- [M48.3] ☑ **the mode selector** — `tools/fluxtion-harness.py`; derives the mode per FIGURE, emits a
      machine-readable handoff record, verified on four scenarios
- [M48.4] ☑ **the shared scorer** — `analyser.score.ExpectationScorer` / `ScoreCommand`, built on the
      shipped reader. **Ten** guards, 21 tests. Five guards were added by three rounds of independent
      review, each reproducing a false PASS by execution: event-sequence identity, extra figures,
      non-finite values, fully-qualified event identity, and a vacuous zero-figure comparison.
      **Every defect found erred toward agreeing with the author** — the same direction as the five
      historical ones the class was written to stop. Dialect is now caller-declared. **Analyser code.**
- [M48.11] ☑ **the full chain, end to end** — `FluxtionSpringConfig.logLevel` → generated processor →
      real audit log → shipped reader → declared dialect → scorer. **PASS 12/12 events, 27 figures**;
      5 of 5 mutations of that real log caught. The historical reference is preserved and a
      provenance-carrying conforming derivative added beside it. See `round-57/M48-11.md`.
- [M48.5] ☐ **the mode-1 selection asset** — small; how to read a `Fluxtion-Description`, that absence of
      a promise rules a candidate out, how an answer becomes a profile line
- [M48.6] ☐ **`generate-sources` rebind** — measured: in modes 0/1 nothing the author writes is a
      generator input, so a plain `mvn compile` works and the whole ordering workaround
      (`generated.dependents`, the `default-compile` exclusion, the second compiler execution) can go.
      **The analyser ships the template, so this is ours.**
- [M48.7] ☐ **`analyser_context` handoff section** — the selector's record as shared canvas state,
      readable by the LLM through `context`, rendered for the human; posture SETTABLE by either party
      with derivation only as the default (R7 revised, R10)
- [M48.8] ☐ **cache accounting in the experiment harness** — Haiku 4.5 silently uncaches below 4,096
      tokens, so any prefix-size comparison without `cache_read_input_tokens` is meaningless (P3a)
- [M48.9] ☐ **modes 2/3 — spec, asset, and ABLATION.** The unmeasured half, and where the Haiku ceiling
      actually is. **Every ablation this project has run was over *assembly* guidance**; no authoring
      instruction has ever been ablated.
- [M48.10] ☐ **the dev harness loop** (owner's item 4). The analyser contributes exactly two things — a
      queryable graph and a queryable log. Every judgement in the loop is the LLM's or the human's.

**Relationship to M46/M47.** M46 is *toolchain repair* — communication failures found by measuring
authoring. M47 is *start from a template*. M48 is upstream of both: it says which mode an author is in,
and for modes 0/0+ there is nothing to author at all.

**Upstream filed from this work:** **UP-FLX-45** (the wall clock is 55% of dispatch by default; replay
mode already avoids it — low priority, the author's default is defensible) and **UP-FLX-46**, lodged as
[telaminai/fluxtion#31](https://github.com/telaminai/fluxtion/issues/31) — two classes sharing a simple
name in different packages emit uncompilable code with no diagnostic; a component-market blocker with a
12-line reproduction.

## Decisions (resolved)

- **The commercial model, and where the analyser sits in it** _(owner, 2026-09-01)_. Recorded because it
  bears on **D-S1.2**, the open licence choice that blocks a release, and because the reasoning would
  otherwise be lost in conversation. **Owner's model:** the compiler is priced (hosted, or **hosted
  on-prem for enterprises**, which removes generation friction while keeping enforcement); the analyser is
  **per seat per month**; **redistribution/deployment licences are required for the generated event
  processor and for Mongoose**; and auditors are a pricing dimension.
  - **The deciding argument is redistribution, and it settles the shape.** *"One generation deployed in a
    million drones."* Generation is a one-time act; the artefact replicates without limit. Pricing only
    generation sells a million-unit fleet for the price of one build, so value capture has to sit at
    deployment. That is enforced by contract, procurement and audit rights rather than by code — the
    generated processor is committed Java in the customer's tree, with no wire to gate — which is a normal
    enterprise model but a **different competence** from the rest of the stack, and should be resourced
    deliberately rather than discovered at the first renewal.
  - **Consequence the owner's own model implies: the compiler gate is then a tax on the funnel.** If the
    revenue is at deployment, the drone customer pays regardless of what generation cost, while a public
    repo or a thirty-node experiment never deploys anything and so collects nothing — the gate only
    suppresses the reading and trying that feed the deployments. The narrow ask that survives is
    **generation free for public repositories**, on evidence grounds rather than price sensitivity: an
    open Fluxtion project is the only place a prospect can read a real graph, its annotations and its
    generated dispatch before spending. **This repository is the case in point** — anyone can clone,
    build, test and fix the analyser, and nobody without a key can add a node to the one part that
    demonstrates the thesis.
  - **Withdrawn after owner pushback: "free at entry is required".** That was reasoning from the
    open-source-tooling era. Developers now pay per seat and per token for tooling as a matter of course,
    so entry friction is a far weaker objection than it was, and on-prem hosting removes it for the buyer
    who matters. The public-repo carve-out above is argued from *evidence access*, not from price.
  - **The one part still argued against: charging per auditor.** Two reasons, the first specific to a
    known framework constraint. **(1)** Invocation tracing is fixed at generation time (UP-FLX-37 /
    `fluxtion#25`) — so a customer who wants more audit detail mid-incident must regenerate and redeploy.
    Metering that means metering the thing they most need at the moment they can least obtain it, and the
    incident where the product could not help is the story that travels. **(2)** Auditors are how the
    audit log exists, and the audit log is what creates demand for analyser seats — the razor, not the
    blade. Most graphs need exactly one auditor, so the revenue is small while the incentive it creates
    ("use fewer auditors") points against *audit everything, always*, which is the strongest claim in the
    product. **Node count is a cleaner complexity proxy** and tracks the measured value curve (≈0 glitch
    sites under 10 nodes, ≈0.1/node above 50) without discouraging anything.
  - **Per-seat for the analyser is right and needs no metering** — its value grows with graph size and
    incident count, which seats already track.
  - **One trade to state publicly rather than discover:** an on-prem compiler loses the "the generator
    improves without every project upgrading a plugin" property that hosted generation provides.

- **A processor with no source keeps offering "Add source", whatever the cause** _(owner, 2026-08-30;
  re-affirming 2026-08-27)_. The live v4 bundle raised a case the original decision may not have had in
  view — a declared processor class, no generated source shipped, and `src/main/java` already configured,
  so adding a root cannot help. **The owner's answer is to keep the button as it is.** The Project panel's
  *wording* still distinguishes the two causes, which is where the correction belongs: one remedy button
  that is occasionally unhelpful beats a row whose control changes shape depending on why something is
  missing. `ProjectModelTest` records the target as deliberate so a later reader does not "fix" it.
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

- ~~**When the playground lands a numeric `price` node-log key, the tutorial's graph step becomes
  executable**~~ — **CLOSED 2026-08-30, the same day it was raised.** The playground shipped the numeric
  keys, the tutorial step is executable rather than illustrative, and the figures were re-shot. Left struck
  rather than deleted because a question that was live for four hours is still evidence of how the two
  repos actually worked.


- Graph "last occurrence per record" vs "all occurrences" default. (spec: last; expose toggle.)

_(spec-closed-loop O1–O4 all resolved — statuses recorded in the M18 block above; O5 in Decisions.)_
