# Evidence bundles — discussion log

The record of **why** the [combined proposal](combined-proposal.md) says what it says. The proposal states the current
position; this file keeps:
- the arguments that produced it;
- the options rejected, and why;
- the disputes still open;
- the evidence each side offered.

Several authors, human and AI, contribute. **Agreement between authors does not establish a fact.** Evidence does.

## How to add an entry

Append; never rewrite an earlier entry. If a position changes, add a new entry that names the one it supersedes.

```
### L-<n> · <topic> · <open | resolved | superseded by L-m>
- **Raised by / date:** who, and when
- **Proposal refs:** the D-/A-/EB-/§ ids it concerns
- **Positions:** each side, one line each, attributed
- **Evidence:** labelled READ (code read, with file or symbol), RAN (command run, with its result),
  REPORTED (someone else's claim, not re-checked), or OWNER (a stated owner decision)
- **Outcome:** what the proposal now says, and the reason — or what would settle it
```

**Evidence labels:**
- **READ:** the author read the code or document cited.
- **RAN:** a command or test was run, and its result is recorded.
- **REPORTED:** stated by another author or tool and not re-checked. Treat it as a lead, not a fact.
- **OWNER:** a decision the owner stated. Only the owner changes these.

---

## Owner direction

### L-1 · First-delivery scope · resolved (OWNER)
- **Raised by / date:** the owner, 2026-09-27; recorded in the Codex proposal and the combined proposal's D-0.
- **Proposal refs:** D-0, §3.
- **Positions:** draft B r1 and r2 put replay and comparison after v0.1. Draft A put exact replay in phase 2, but kept
  re-run and compare. The Codex proposal records the owner's scope.
- **Evidence:** OWNER. "Both portable investigations **and** replay/fix/comparison are required for the first
  delivery. The browser viewer is optional."
- **Outcome:** a bounded replay → fix → comparison → response journey is required. An inspection-only release is a
  checkpoint, not completion. Draft B's v0.1 scope is superseded on this point.

### L-2 · Deliver in about two days · resolved (OWNER)
- **Raised by / date:** the owner, 2026-09-27.
- **Proposal refs:** §11.
- **Outcome:** a planning target. The combined proposal adds that it is not grounds for waiving an acceptance check,
  and that a missed time box is reported with the actual blocker.

---

## How the drafts converged

### L-3 · Where the five versions came from · resolved
- **Raised by / date:** the analyser session, 2026-09-27.
- **Positions and evidence:** READ; all are archived unchanged in `versions/`.
  - the positioning doc (owner);
  - the prototype proposal;
  - draft A, a tool-first client over REST (another model);
  - draft B, analyser-native (the analyser session);
  - the Codex proposal.
- **Outcome:** the combined proposal consolidates all five, and none is promoted wholesale. Drafts A and B, and the
  Codex proposal, were brought onto one branch (`proposal/evidence-bundles`, merge `fca81754`) before combining.

### L-4 · Is a report evidence or testimony? · resolved
- **Raised by / date:** draft A vs draft B r1, 2026-09-27.
- **Proposal refs:** §15; the manifest member roles.
- **Positions:**
  - Draft B r1: a report is evidence, because its tables and charts derive from the excerpt.
  - Draft A: "nothing the author wrote in prose may appear under evidence".
- **Evidence:** argument. Choosing which tables and charts make the case is itself the author's act.
- **Outcome:** draft B r2 conceded to A. The combined proposal refines it: a report is an attributed argument
  container, while separately emitted measured tables or results may be evidence, with their own derivation and scope.

### L-5 · Signing in the first delivery · open, leaning unsigned
- **Raised by / date:** draft A vs draft B, 2026-09-27.
- **Proposal refs:** D-3, A-12.
- **Positions:**
  - Draft A: optional Ed25519, with the signer marked `declared: true`.
  - Draft B r1: unsigned, because a signature without a trust model reads as more than it is.
  - Draft B r2: A's declared signer is acceptable, provided `verify` never prints a signer without "not vouched for".
  - Combined: unsigned first. A signature needs a reviewed envelope (the signed bytes, key identity, trust source,
    rotation, failure behaviour) and must not create a digest cycle.
- **Evidence:** argument only. Nothing has been built.
- **Outcome:** unsigned first. Reopen if the owner weighs signing's demo value above its delivery cost.

### L-6 · Bundle identity: canonical JSON, or exact manifest bytes · open
- **Raised by / date:** drafts A and B vs the combined proposal, 2026-09-27.
- **Proposal refs:** D-2, A-1.
- **Positions:**
  - Drafts A and B: SHA-256 over canonicalised JSON with the id field blanked or removed.
  - Combined: SHA-256 of the exact manifest bytes, computed outside the manifest.
- **Evidence:** argument. Exact bytes avoid a recursive hash and a canonicalisation step every reader must implement
  identically. Canonical form survives reformatting, but nothing needs to reformat a manifest.
- **Outcome:** combined recommends exact bytes. The two algorithms are incompatible, so one must be chosen before any
  bundle is written, and golden fixtures pin it.

### L-7 · Profile layout inside the bundle · resolved
- **Raised by / date:** draft B r2, against draft A, 2026-09-27.
- **Proposal refs:** §5.1, §6.3, D-5.
- **Positions:**
  - Draft A: the profile at `analyser/.analyser/project.fluxtion-settings`, with the log at `../log/…`.
  - Draft B: the profile at the bundle root's `.analyser/`.
- **Evidence:** READ. `ProjectProfile.baseDirFor` anchors a `.analyser/project*.fluxtion-settings` profile on its
  parent. `AnalysisSpec` refuses `..` in path parameters. `PROJECT_SCOPED` has no log or graph category, so draft A's
  "log and graph paths written into the profile" has no key to write to.
- **Outcome:** the root `.analyser/project.bundle.fluxtion-settings` layout. The profile names no log or graph; the
  open operation loads them explicitly.

### L-8 · Should a bundle carry, and run, its own saved analysis? · resolved against draft B
- **Raised by / date:** draft B r1 (its D-5) vs the combined proposal, 2026-09-27.
- **Proposal refs:** §6.3, §7.
- **Positions:**
  - Draft B: open by running the bundle's own saved analysis, which opens the excerpt and topology with
    project-relative paths. Its argument was that this needs no new orchestration.
  - Combined: do not carry arbitrary runbooks or saved analyses, and do not run bundle-supplied analyses. The client
    opens with checked absolute paths.
- **Evidence:** READ. `AnalysisSpec` documents that a saved analysis's steps can include `report` and `screenshot`,
  which write files, and `source_root`, which mutates the recipient's configuration. Path gating limits where they
  point, not what the verbs do.
- **Outcome:** combined's position stands. A received attachment must not choose actions on the recipient's machine,
  even gated ones. Draft B's argument (no new orchestration) is kept, and met by client sequencing instead.

### L-9 · Can a failed bundle be opened with `--force`? · resolved
- **Raised by / date:** draft A vs the combined proposal, 2026-09-27.
- **Proposal refs:** §7.
- **Positions:**
  - Draft A: `open --force` on a verify failure, stated in the provenance.
  - Combined: no forced normal open in v1. Any future forensic mode is kept separate from verified operation.
- **Outcome:** no `--force`. A forced open looks like a normal session once it is running, and the qualification is
  easy to miss.

### L-10 · A second audit-log reader in the client · resolved
- **Raised by / date:** draft B r2, against draft A, 2026-09-27.
- **Proposal refs:** §4, §6.2, §9.
- **Positions:**
  - Draft A: the tool cuts the YAML itself and computes comparison rows by parsing logs in Python.
  - Draft B r2: that is a second reader, and this repository keeps a conformance suite because readers drift.
- **Evidence:** READ. `read` returns the same raw text the exporter writes (`rawText(row)`), and the analyser's own
  verbs compute series and aggregates.
- **Outcome:** prohibited. The client uses the analyser's services for records, series and verdicts, and assembles
  results; it never re-parses the format.

---

## Review r2 of the combined proposal (analyser session, 2026-09-27)

### L-11 · EB-0: what a client can capture over the socket today · resolved by READ, re-check at build
- **Proposal refs:** §4 (the EB-0 table), D-1, D-1a.
- **Evidence:** READ at `main` `d82f1487`.
  - **Flags:** `context.flags` lists each flag's `recordIndex`, `kind`, `note` and `fix`. The index is into the live
    store, so an excerpt must remap it.
  - **Raw record text:** `ReadService` sets `text` from `rawText.apply(row)`, the same source `RecordExporter.toYaml`
    writes. Anchored by index, byte offset or time; `MAX_COUNT = 25` per call.
  - **Filtered view:** no verb lists the filtered record indices. The `filter` reply carries only `from`, `to`,
    `dimensions` and `text`.
  - **Charts:** `SessionFacts.savedGraphs` publishes `name`, `open`, `series`, expression strings and `style` only.
  - **On-disk profile:** `ProjectSession` coalesces (debounces) writes, so the file can lag the live charts.
- **Outcome:** the proposal's EB-0 question is mostly answered. A client can capture flags, raw records, a whole-log
  or explicit-index excerpt, and the pairing verdict. It **cannot** capture complete chart definitions, or the
  filtered view's indices. Added as the §4 table and D-1a.

### L-12 · D-1: internal core, or external client · open, narrowed by L-11
- **Proposal refs:** D-1, D-1a.
- **Positions:**
  - Draft A: external client, no analyser changes.
  - Draft B: analyser-native.
  - Combined r1: internal evidence core with a thin client, with external-only as an alternative if complete exports
    are proved.
- **Evidence:** L-11. The one proved gap for a client is complete chart state, plus the filtered indices if that
  selection mode is kept.
- **Outcome (proposed):** the smallest adapter is a **read-only fact** publishing full chart definitions through the
  production serializer, not a capture verb or a new UI. That keeps most capture in the client, as draft A wanted, and
  keeps semantic authority in the analyser, as draft B and the combined proposal want. **What settles it:** whether a
  `context` extension or a separate read-only option is the smaller reviewed change; the contract tests decide the
  cost.

### L-13 · The archive limits are an existing implementation · resolved
- **Proposal refs:** §7, A-2.
- **Evidence:** READ. `template/TemplateArchive` defines `MAX_ENTRIES = 4096`,
  `MAX_ENTRY_BYTES = 64 MiB` and `MAX_EXPANDED_BYTES = 512 MiB`, matching §7 exactly. It already stages and moves
  atomically.
- **Outcome:** reuse its extractor; don't write a second one.

### L-14 · "No pending load" and M44.6 · resolved
- **Proposal refs:** §6.1, §7.
- **Evidence:** READ. Tracker M44.6 (an owner decision, still open): a superseded load's tail can install its table
  over a newer log when a modal runs inside the load.
- **Outcome:** the proposal now names M44.6 as the reason capture and open must not overlap a pending load, and why a
  client-led open must await completion before reading state.

### L-15 · The status of PR #53 and #55 · resolved
- **Proposal refs:** §10, D-9.
- **Evidence:** RAN `gh pr view`, 2026-09-27.
  - #53 (the Surface/SVG drawing layer) is open, unmerged, and retargeted to `main`; its full CI has not run since.
  - #55 (the view-model spike) is a draft, green on all 12 checks, marked not for merge.
- **Outcome:** neither is a delivery dependency, as the proposal already said. Their status is now stated as fact.

## Review r3 (Codex, 2026-09-27)

Codex contributed combined r1; this is not independent of that authorship. Earlier agreement is not evidence.
Unless another revision is named, analyser READ evidence below is at `d82f1487`, as carried by proposal `9640780d`.
All failure scenarios below are source-derived unless explicitly labelled RAN; none is a replay trial.

### L-16 · Available facts do not establish coherent capture · open
- **Raised by / date:** Codex, 2026-09-27.
- **Proposal refs:** D-1, D-1a, EB-0, A-3, §4, §6.1. Supersedes L-12's conclusion that only one adapter gap is proved.
- **Positions:** r2 concludes that full chart definitions, optionally filtered indices, complete the socket capture
  surface. Codex disputes that conclusion while confirming most of L-11's individual observations.
- **Evidence:** READ `MainFrame.context` iterates every `flaggedRows` entry, with optional note/fix, and publishes
  session pairing. `ReadService.read` returns `rawText` without fields projection, capped at 25; `RecordExporter.toYaml`
  adds separators/newlines around that same text. This is not an original-file byte export. `SessionFacts.savedGraphs`
  also carries `input` but omits most `GraphSpec` components. `ProjectSession.requestSave/flush` confirms debounce.
  `ReadService`, `VerbSchemas` and the filter reply do not supply a filtered-index listing. These are READ confirmations,
  not socket reproductions. `ActionExecutor` assembles context on the EDT; `ActionDispatcher` independently obtains
  the snapshot/raw accessor for a later read. Neither API accepts an expected capture revision binding the two calls.
- **Failure / cost:** capture context for log A, open same-sized log B, then page records. Both calls can succeed while
  chart/flag indices from A are combined with B. Or change a chart between the definition read and capture completion.
  Follow off and idle observations do not prevent this; an A→B→A sequence defeats simple before/after equality checks.
- **Replacement:** retain complete chart export as a useful fact, but require a session-owned snapshot/retained-source
  or equivalent validated capture contract. Reuse canonical readers and serializers. No client-side inferred verdict.
- **Outcome / delivery impact:** r3 removes “every capture need except one” and the assertion that architecture was
  decided. EB-0 must cost and prove coherence, including A-3's competing-change refusal. API completeness alone is not
  that proof. No runtime race was reproduced in this docs-only review.

### L-17 · Context extension is smaller for the chart fact, not a capture transaction · open
- **Raised by / date:** Codex, 2026-09-27.
- **Proposal refs:** D-1a, §4; refines L-12.
- **Positions:** compare a nested complete `definition` on existing `savedGraphs` entries with a new read-only option
  on `graph`. A new verb would add still more contract surface; it is not needed merely to read chart definitions.
- **Evidence:** READ `SessionFacts.savedGraphs` and its `MainFrame.context` call are **two existing assembly sites**.
  `ContextSections.SECTIONS` already includes `savedGraphs` under `charts`; a nested field needs no new section or input
  parameter. Preserve existing name/open/input/series/expressions/style meanings. `ConfigStore.writeGraphs/readGraphs`
  are package-private: either route needs a reviewed serializer boundary, not copied property names.
  `ActionExecutor.renderVerb/doGraph` currently requires a log, reveals Graph, and may reopen a saved definition via
  `graphForAction`; a read-only option must bypass those effects. That route also touches `VerbSchemas` and the
  hand-described `PromptBuilder` manifest. Merely invoking the current graph verb is not a read-only export.
- **Contract inventory (READ, class counts, not claims that every file needs editing):**
  - Context path: **3** directly relevant existing classes: `GraphSeriesShapeAndStyleEchoTest` (saved chart echo),
    `ContextSectionsTest` (section membership/projection), `ContextSectionsVerbTest` (routing/full versus projected).
  - A new graph input option: the first echo class plus **4** schema/description transport classes = **5**:
    `VerbSchemasTest`, `ManifestVerbContractTest`, `McpToolsTest`, `InProcessManifestNamesEveryVerbTest`. The latter
    checks every parameter appears in the hand-written manifest. New no-log/no-side-effect coverage is also required.
  - Either route shares **3** persistence fidelity classes to extend/check: `GraphProfileMetadataTest`,
    `GraphStylePersistenceTest`, `SettingsShareTest`. None currently proves complete exported-definition round trips.
  These are a bounded inspection inventory, not an exhaustive dependency count or a test execution result.
- **Failure / cost:** routing a “describe” call through `doGraph` can reveal/reopen a chart; extending default context
  instead increases every full context reply. Project-panel consumers read selected summary fields (`ProjectModel`),
  while REST, MCP and the in-process caller share the payload. Preserve summaries and measure response size.
- **Outcome / delivery impact:** recommend the context extension for this fact alone; no second full-state serializer,
  no new input contract. Approval still needs full/open/closed/unsaved-chart round trips, payload-size measurement and
  the distinct capture solution in L-16. The overall native-versus-client decision remains open.

### L-18 · Replay exists below the experiment boundary · open
- **Raised by / date:** Codex, 2026-09-27.
- **Proposal refs:** D-7, EB-0, §9, A-8/A-9; resolves primitive availability, not executable feasibility.
- **Positions:** neither “no replay exists” nor “the existing replay route already implements this journey” is supported.
- **Evidence:** READ public Fluxtion at `3de39f55`: the [framework reference](https://github.com/telaminai/fluxtion/blob/3de39f55/docs/claude.txt),
  [replay guide](https://github.com/telaminai/fluxtion/blob/3de39f55/docs/how-to/replay-functionality.md), and
  [YamlReplayRunner](https://github.com/telaminai/fluxtion/blob/3de39f55/fluxtion-builder-api/src/main/java/com/telamin/fluxtion/builder/replay/YamlReplayRunner.java).
  RAN public `gh api` retrieval at that revision; read the retrieved source. The runner takes an existing processor,
  iterates decoded `ReplayRecord`s in order, sets the clock then calls `onEvent`. Its bounds are strict inequalities;
  `callInit/callStart` run immediately, before `runReplay` installs the clock. There is no reset/build-selection/receipt.
  The guide describes fresh instances and calls this an AOT commercial feature; availability in a chosen released,
  keyless toolchain was not established. The reference's broad determinism language is not evidence of captured inputs
  or reset state; its stateful replay caveat explicitly requires reset between passes.
- **Evidence:** READ public Mongoose core `2c4192ed`, `EventToQueuePublisher.publish/publishReplay`,
  `EventQueueToEventProcessorAgent.doWork`, `AbstractEventToInvocationStrategy.processEvent(Object,long)`.
  Normal publish invokes the mapper; replay publish queues its argument directly. The agent unwraps the event and
  timestamp and the invocation strategy installs/updates a synthetic processor clock. READ public examples `27ad4fda`,
  `ReplayExample.demonstrateReplayReproducibility`: it clears the sink, not the handler's `eventCount`; output includes
  the incrementing count. That example's same-instance repetition is not a fresh-state proof.
  READ public plugins `ee3fc12d`, admin `replay-engine.js`: record/node cursor playback, no application execution.
  READ the template repository at `15afb92c`: emitted hosting guidance explicitly supplies no generic feed reset or
  recorded-session replay command. No private source location or excerpt is reproduced here.
- **Failure / cost:** a mapper converts a DEMO input wrongly; replaying its already-mapped event through `publishReplay`
  never executes that mapper. Swapping the mapper can therefore leave the replay unchanged. Reusing a stateful processor
  can also change the second result despite identical input/time. Lifecycle reads of time need their own policy.
- **Replacement:** a locally selected application runner consumes the finite pre-mapper input, invokes the actual
  component under test, starts fresh isolated state and controls clock/lifecycle/side effects. Reuse framework dispatch
  at its documented boundary; do not rewrite the mapper in the harness. Verify consumed input counts and chosen build.
- **Outcome / delivery impact:** primitive availability and the mapper bypass are settled by READ. An adapter and its
  owner are real EB-0 dependencies; a released codec/runner combination, fresh-state control, original failure and fixed
  pass remain unverified. No real-system replay, compilation or provider was run. Owner scope still requires the journey.

### L-19 · Archive limits are reusable; the installer contract is not · resolved by READ
- **Raised by / date:** Codex, 2026-09-27.
- **Proposal refs:** §7, A-2. Supersedes L-13's unqualified “reuse its extractor” conclusion.
- **Evidence:** READ `TemplateArchive.install`, private `extract`, `soleProjectRoot`, `setFixedPermissions`.
  The three bounds match §7, but install requires exactly one top-level directory, applies template permissions and
  checks template-profile roots before moving. The proposed archive instead contains root `manifest.json`, `.analyser/`
  and other member directories. It cannot pass that install contract unchanged.
- **Failure / cost:** feed the proposed archive layout to the existing installer → refusal before a bundle opens.
  Making it a nested starter project would instead change the proposal's profile/manifest layout.
- **Replacement:** factor/reuse bounded extraction mechanics with explicit bundle layout and permission policy; retain
  template-specific validation in the template route. Add both consumers' regression checks and bundle manifest checks.
- **Outcome / delivery impact:** r3 stops calling this a ready extractor. Reuse is plausible, not free; include this
  small shared-code review in the delivery estimate. No archive was created or installed during this review.

### L-20 · Exact bytes versus canonical identity remains a choice · open
- **Raised by / date:** Codex, 2026-09-27.
- **Proposal refs:** D-2, A-1, §5.2; adds to L-6 without deciding it.
- **Positions:** Codex recommends retaining exact-byte identity for immutable received manifests. Canonical identity
  is preferable if independently formatted manifests must intentionally name the same capture. Neither property is
  established as an owner requirement; both avoid self-reference when the identity is kept outside the hashed object.
- **Evidence:** READ [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785.html), §§3.1–3.2: canonical JSON specifies numbers,
  Unicode preservation and property ordering, not just whitespace removal or sorted keys. It rejects duplicate names
  and constrains numeric representation. This is a standards inspection, not a cross-language verifier test.
- **Failure / cost:** exact-byte readers deliberately assign different IDs after pretty-printing; canonical readers
  using different numeric serialisers can disagree despite key sorting. A manifest with duplicate keys can be hashed
  successfully yet interpreted differently by two readers: reject it under either identity policy.
- **Replacement:** choose one explicit identity contract; require formatting, numeric, Unicode and duplicate-key golden
  fixtures. Do not support two silent identity algorithms. Canonical JSON is a viable alternative, not intrinsically unsafe.
- **Outcome / delivery impact:** r3 records these costs and keeps D-2 open for the owner/review discussion. Exact bytes
  is still the recommendation, not a unilateral decision. No bundle identity implementation was tested.

### L-21 · Unsigned first needs an explicit substitution limit · open
- **Raised by / date:** Codex, 2026-09-27.
- **Proposal refs:** D-3, A-11/A-12, §5.3; adds to L-5.
- **Positions:** Codex supports unsigned first for the controlled DEMO journey; optional signing remains a scope choice,
  not a prerequisite established by the portable investigation requirement.
- **Evidence:** READ §5.2–§5.3's hash and trust contract. Inference from that contract: replacing both a data member and
  its manifest hash produces another internally consistent unsigned bundle. A separately trusted original identity
  detects the substitution. A signature verified only against a key supplied in the same substituted archive does not
  establish the claimed person's identity either. No cryptographic implementation or signing experiment was run.
- **Failure / cost:** presenting “hashes match” as “this came from the ticket author” overstates what was checked.
  Optional signing without a trust source changes the mechanism but does not establish that missing attribution.
- **Replacement:** label unsigned provenance honestly. If authenticated ticket authorship is required now, approve a
  pinned/trusted key source and explicit signature-envelope acceptance; do not quietly substitute a self-declared key.
- **Outcome / delivery impact:** D-3 remains open, with unsigned first recommended. This adds no implementation scope
  unless the owner chooses authenticated authorship for this delivery. D-0 is unchanged.

### L-22 · Review checks and limits · resolved (RAN/READ, limited scope)
- **Raised by / date:** Codex, 2026-09-27.
- **Proposal refs:** EB-0; evidence labelling across r3.
- **Evidence:** READ source and contracts as pinned above. The new conclusions are source inspections, not product
  acceptance, replay or socket reproductions. The archived drafts and prior L-entry bodies were not edited.
- **Evidence:** RAN JDK 21 `mvn -q test`: **2583 total / 0 failures / 0 errors / 131 skips**, 344 source-mapped
  Surefire reports, no orphans. Skips are not passes. The first sandboxed attempt was **2583 / 0 / 29 / 131**: all
  29 errors were denied localhost socket operations. Its reports/log were preserved locally; the authorised retry
  passed. RAN `mkdocs build --strict`, `git diff --check`, the documented `git ls-files` rule-one sweep (no output),
  added-line sweep and private-path/address check: clean. RAN preservation checks: previous discussion prefix is
  byte-identical; `git diff` reports no changes under `versions/`, and no product-source changes against `d82f1487`.
- **Outcome:** these tests establish the existing docs/headless baseline, not bundle acceptance. No implementation, bundle creation, replay,
  model session, hosted provider or participant access. Renderer PR status remains L-15's REPORTED result for this
  reviewer; it is not needed for these changes. Choosing the two demo bugs still needs the owner's issue references.

---

## Open disputes, at a glance

| log | question | what settles it |
|---|---|---|
| L-5, L-21 | Sign in the first delivery? | owner: whether authenticated author identity is required; otherwise unsigned with explicit limits |
| L-6, L-20 | Exact manifest bytes, or canonical JSON? | choose the intended identity semantics; pin one algorithm with cross-reader fixtures |
| L-12, L-16, L-17 | Chart fact placement and coherent capture (D-1a) | context is the smaller fact-export candidate by READ; a separate snapshot/revision contract still needs proof and costing |
| L-18 | Complete replay route (§9) | dispatch/clock primitives and mapper bypass established by READ; pin and run a released pre-mapper runner with reset/build identity/oracle (EB-0) |
| — | Which “two bugs” the demo discussion meant (EB-0) | the owner names them, by issue and failing check |
