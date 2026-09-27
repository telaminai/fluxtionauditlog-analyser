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

---

## Open disputes, at a glance

| log | question | what settles it |
|---|---|---|
| L-5 | Sign in the first delivery? | owner: demo value versus delivery time |
| L-6 | Exact manifest bytes, or canonical JSON? | choose one; pin it with golden fixtures |
| L-12 | Where the one native adapter goes (D-1a) | the cheaper reviewed change, measured by the contract tests it touches |
| — | Replay route: does the Mongoose/template path already run recorded inputs? (§9) | read Fluxtion's replay guide and the runner's code, then run it (EB-0). **Still unknown.** |
| — | Which "two bugs" the demo discussion meant (EB-0) | the owner names them, by issue and failing check |
