# Fluxtion Audit Log Analyser — Work Tracker

Companion to **[spec.md](spec.md)** and the [spec index](README.md). Status keys: ☐ todo · ◧ in progress · ◐ implemented,
acceptance open · ◑ part shipped · ☑ done · ⊘ dropped.

**How this file works (slimmed 2026-10-01).** One line per open item: its id, status, what it is, and where its detail
lives. History, evidence and acceptance text are in the item's spec, issue or evidence folder; everything this file held
before 2026-10-01 is kept verbatim in [the full-detail snapshot](completed/tracker-detail-2026-10-01.md) — **▸ detail**
below means the section of that name there. Resolved decisions are in [decisions.md](decisions.md). Shipped work moves
to [completed/tracker.md](completed/tracker.md).

**Rules.** An item marked ◧ or ◐ never leaves this file, whatever ☑ marks appear in its detail (the M45.4 lesson). When
an item closes, tick it here with the release or commit that closed it, then archive it at the next tidy. Keep each
entry to one or two lines; put anything longer in the spec, an issue or an evidence folder and link it.

---

## Delivery order

- ◧ [INTEG-R7] Desktop acceptance remains open: complete Mac run 326/1/0/3 (T2 right-click control missing plus native skips); final ownership correction passes 21 affected cases. ▸ [review](../handoff/review_integration_2026_10_18_codex.md)
- ☐ [INTEG-R8] Diagnose background EDT ownership exceptions in one passing grouped usability run; no proven production cause or named failing regression yet. ▸ [review](../handoff/review_integration_2026_10_18_codex.md)
- ☐ [INTEG-R9] Owner scope needed for historical host-path redaction and stale screenshots; not introduced by this integration. Preserve evidence while planning the cleanup. ▸ [review](../handoff/review_integration_2026_10_18_codex.md)

_Refreshed 2026-10-06, after 1.32.0._ **Shipped since the 2026-09-27 refresh:** **1.25.0** (chart and report lifecycle,
PR #51); **1.26.0 / 1.26.1** (M69 spotlight walks); **1.27.0** (evidence bundles, first delivery); **1.28.0** (M70
replay, M71 Workspace Start); **1.29.0** (the onboard assistant and conversation journeys, PR #77); **1.30.0** (bundles as
experiments, a project that opens with its settings applied); **1.30.1** (a walk step can point at Java source, #72).
**Upstream since:** fluxtion runtime **1.1.0** and compiler **1.0.76** (`runInEventCycle`); mongoose **1.0.31** (replay
recorded at dispatch) and **1.0.32** (admin commands run in the event cycle and audit as their own record);
mongoose-plugins **1.0.45** (separator escaping, #39). The 2026-09-27 order is in the
[detail snapshot](completed/tracker-detail-2026-10-01.md) ▸ *Suggested delivery order*.

**Released in 1.31.0 (2026-10-05):** PR #87 redaction; PR #103's ten #84 corrections (#93–#102, all
closed); PR #104's released-stack adoption; PR #92's admin-record acceptance. [Final integration review and CI](https://github.com/telaminai/fluxtionauditlog-analyser/pull/92#issuecomment-5931355484).

**Released in 1.32.0 (2026-10-06):** PR #108 the walkthrough reel (#82) with its text redacted by the bundle's
rule (#113); PR #88 bundle import off the event thread and working-copy reaping with cross-process ownership (#83,
#85); PR #107 MG-1 mutation-gate iteration; PR #105 EDT-fault and native-input test guards; PR #123 the assistant's
settings-save redraw, pinned (supersedes #78). [Release receipt](../handoff/release_analyser_1_32_0_2026_10_06.md).

**Released in 1.36.0 (2026-10-19), PR #168:** the 2026-10-18 integration stack — M77 Set up AI, M78 the quiet
assistant panel, FIX-1…FIX-10, UX-SC, the OPT-2 Claude-Code-proposed starter, and the integration review's R3–R11 /
C1–C8 corrections; M74's P1a…P3, UX-unification, r14/r15, and [DOCS-IA]. Archived in
[completed/tracker.md](completed/tracker.md) ▸ *1.36.0*.

_Refreshed 2026-10-19, after 1.36.0._ **Delivery order:**

1. **MVP, in parallel:** M79-1a (the first launch) and the M76 criterion chip + before/after run comparison.
2. **Commercial track (owner):** the gateway hotfix (compiler#113) deploys, then RapidAPI self-serve and a
   trial/teaching key tier.
3. **The security one-pager** (OD-74.2), before any customer-facing Claude Code release.
4. **2–3 friendly-team trials.**

Big builds wait **for the owner's plan usage reset (2026-10-22)**.

**Still carried:** M74 follow-ups (Settings ▸ Assistant around the four profiles, now partly superseded by M77's
wizard and quick switch — re-scope or close; the spec split; `LocalAgentWiring`; the CLI-drift canary and
managed-settings probe; Windows, #148) · virgin-LLM cycles (#155; T4 authoring first, T5 the external-loop gate) ·
M75 (#153) once its owner decisions are answered · assurance debt (#70, #119) · owner decisions (M70 vs Mongoose
replay; branch protection requiring `mutation-gate`; M44.6; #74; BETA-B3; M39; M34.5; AF-8/AF-9 and OD-5; O2) ·
M48.17, M72, M46.1 · small correctness items (N2; OBL-1, OBL-2; #143; #149; the restart checker's path aliases;
feedback 36 and 42; the evidence-bundle items) · then OA acceptance, the beta (B4, BETA-8), TA-5b/5c/9/B, SG-2's
hosted half, M67, M33.6, the Mongoose audit format phases 2–3, M34.4/.5 and the C++ target's compiler issues
(compiler#106–#109) · the Telamin Canvas / Telamin AI rename, as its own PR, after M74.

---

## Upstream releases to absorb — 2026-10-01

- [UPS-1a] ☐ **Upstream: quote line breaks in values** — [UP-FLX-55](../proposals/upstream-asks.md); the raw record stream
  (no export escaping) still splits on a `---` inside a value, which is MA-7's framing fault.
- [UPS-2a] ☐ Review of PR #104, follow-ups: N2 (the source/GraphML test checks seven names, not generation identity) and
  `tools/gen-fqn.py`'s glob-chosen runtime jar; one header policy for generated source (M19.22).
- [UPS-2b] ☐ **(owner) Refresh the start-page DEMO set** (screenshots: folded into [DOCS-IA] and the M74 capture harness) — deliberately unchanged by PR #104; includes dependent screenshots
  and journey bundles. Test-fixture adoption is complete and does not settle this presentation decision.
- [UPS-3] ☐ **Mongoose replay at dispatch** (mongoose#47, 1.0.31) — input to the M70 owner decision; see ▸ M70.
- [UPS-4] ☐ **Failure-mode follow-ups** fluxtion#35 / compiler#98 (a throwing node no longer wedges the processor) are
  open drafts upstream; when they ship, check how a failed cycle appears in the log.

## Evidence and bundles

### M70 · Evidence bundle replay — released in 1.28.0 ([spec](spec-evidence-bundle-replay.md))
- [M70.F1] ☐ **Whole-feature review** — [PR #70](https://github.com/telaminai/fluxtionauditlog-analyser/pull/70).
- [M70.F2] ☐ **(owner) Processor vs agent, and R-D8's own writer** — now with mongoose 1.0.31's recording as a fact;
  licensing deferred. ▸ detail: *M70*.

### Evidence bundles — follow-ups as issues
- ☐ #64 withheld chart definitions captured silently · #65 Follow-growth premise · #66 restoring Follow after capture ·
  #67 excerpt of a non-time-ordered log · #68 walk save's generation check in the frame (rule 9) · #69 small leftovers.
- ◑ #73 discovery: recents and project inventory exist; pre-open preview and working-copy management remain open.
- ☐ #74 (design) project-bound or standalone.
- ☐ #109 a bundle's working copy should be read-only evidence, not the project the session writes into. Raised from
  the #88 review: sharing one copy per identity made a reopened bundle lose its provenance in silence, because the
  session had saved its own settings over a member. #88 takes a fresh copy per open and reaps only provably unused managed copies, which keeps
  the invariant but re-unzips every time; #109 is the fix that would make the class of bug impossible.
- Optional improvements from PR #103’s later review (not unfinished #84 corrections or release blockers): generic-action
  guard hardening, fallback preselection, caption/revision refinement, and unavailable-anchor presentation/removal.
- ☐ Redaction: colon policy and disclosed ambiguous-path residuals remain owner/follow-up work; closure is not a universal redaction guarantee.
- ☐ #56 general graph/context/spotlight drawn-state reporting remains open; walk playback already uses the drawn fact.
- #84's review and all ten corrections are complete; see [the 1.31.0 archive](completed/tracker.md).

### M68 · Evidence integrity — analyser side shipped ([spec](spec-evidence-integrity.md))
- The producer half (D-E9) is tracked under MA-2 / MA-7 below. ▸ detail: *M68*.

## Assistant

### Onboard assistant and conversation journeys — released in 1.29.0; acceptance open ([spec](spec-onboard-assistant-journeys.md))
- [OA-1] ◐ Session-owned live loop — the authorised live-provider run (OA-A2) is unverified.
- [OA-2] ◐ Docked/pop-out host — native input on a delivering desktop; IME untested.
- [OA-3] ◐ Typed dialogue on walks — old-reader behaviour demonstrated in OA-5.
- [OA-4] ◐ Lock-step journeys — native cross-window playback checks, with OA-2's native suite.
- [OA-5] ◐ The DEMO journey — native screenshots (`capture-journey.py --images`) and a cross-machine recipient.
- [OA-6] ◐ Release acceptance — closes when OA-A2, A6/A11, A14/A18 close. ▸ detail: *Onboard assistant…*

### M13 · MCP transport — M13.1–13.4 shipped ([spec](spec-assistant-actions-mcp.md))
- [M13.5] ☐ _(later)_ Resources/prompts (`analyser://…`) and/or option B. ▸ detail: *M13*.

### M43 · The AI menu — follow-ups
- ☐ **(owner)** D-AI9 wording addendum. ▸ detail: *M43*.

### M74 · Claude Code as the analyser's assistant ([spec](spec-claude-code-provider.md), r15)
- [M74] ◧ **P0…P3, UX-unification, r14, r15 — released in 1.36.0 (PR #168).** ▸ detail: *completed/tracker.md ▸ 1.36.0*.
  **Acceptance remains open:** Windows (§11, #148).
- [M74] ☐ **Settings ▸ Assistant around the four profiles** (usability review T1) — partly superseded by M77's wizard
  and quick switch; re-scope or close.
- [M74] ☐ **Split the spec** (review B9): `spec-claude-code-provider.md` is ~2,600 lines; propose a core spec (§1–§11) and per-phase notes files, keeping the OD table and §10 in the core. Not done in r14 by direction.
- [M74] ☐ **`LocalAgentWiring`** (review B10): `MainFrame`'s local-agent wiring (the adapter's environment, the panel's intents, the listener performers) into one class the frame constructs, so the frame stops growing with each phase.
- [M74] ☐ **CLI-drift canary and managed-settings probe** (review B8): a scheduled zero-cost check that the installed `claude`'s stream shapes still match the fixtures, and a probe of what an administrator's managed settings do to the full posture.
- [M74] ☐ **OD-74.2 terms check and a one-page security model** (review B11): before a customer release.
- ☐ **Skill and system-note guidance from the Haiku trials:** never bypass Fluxtion when a command is denied (use
  the project scripts or ask); log decision reasons through `auditLog`; split independent decisions into separate
  nodes; the starter's approval gate should work for unattended runs.
- [DOCS-IA] ◧ **Docs site task-first restructure — released in 1.36.0 (PR #168).** ▸ detail: *completed/tracker.md ▸ 1.36.0*.
  **Still stale:** `ai-menu.png` and the journey step shots (M77-4).

### M74 stack — the 2026-10-18 integration ([review map](../handoff/review_map_integration_2026_10_18.md))
- [INTEG] ◧ **Integration branch, M74-FR, FIX-1…FIX-10, UX-SC — released in 1.36.0 (PR #168).**
  ▸ detail: *completed/tracker.md ▸ 1.36.0*.
- ☐ **FIX-8's open decision** (sample-session reads of the demo folder still prompt): likely resolved by
  `source {text}` (FIX-9) — confirm and close.

### M80 · Design definitions ([spec](spec-design-definitions.md))
- [M80-1] ◧ Source-root discovery, additive context and zero/one Design states implemented; verification/review pending. Header discovery remains gated; OD-2–OD-5 accepted by owner.
- [M80-2/3] ☐ Bounded Java anchors/spotlight synonyms, then the multi-file chooser, as separate dependent PRs.

### M77 · Set up AI — the first-run wizard ([spec](spec-ai-setup-wizard.md), r5; OD-77.1 superseded by M79 OD-79.5)
- [M77] ☑ **Set up AI wizard, with M77-1…M77-5 — released in 1.36.0 (PR #168).**
  ▸ detail: *completed/tracker.md ▸ 1.36.0*.

### M78 · The quiet assistant panel ([spec](spec-quiet-assistant-panel.md), ACCEPTED)
- [M78] ☑ **Quiet assistant panel, with D1 and D2 — released in 1.36.0 (PR #168).**
  ▸ detail: *completed/tracker.md ▸ 1.36.0*.

### M79 · The first minute ([spec](spec-first-minute.md), r6)
- [M79] ☐ **The first minute** (insider + stranger reviews and OD-79.1–.10, 2026-10-18; ACCEPTED r1; r2 one independent review; r3–r4 the lead's round 2 and the owner's correction; r5 owner answers; r6 the second review, OD-79.20–.25 accepted): the real-run diamond walk autoplays once, its Spring XML beside the topology, three doors, a start page under 40 words; then a recorded, composition-first teaching conversation in the onboard assistant that continues live, the evidence chip, the broken-diamond quest, *Start here*, and later the external teacher kit. 8 PRs, sized S/M/L; variants pinned at `fluxtion-diamond` `24d6d74`. No M79 code is in the integration.
- [M79-1] ☐ The first launch: the `firstMinute` node, the diamond walk autoplayed once, three doors, a start page under 40 words (spec §5; spec under independent review on [#164](https://github.com/telaminai/fluxtionauditlog-analyser/pull/164)).
- [M79-2] ☐ Quiet evidence wording: the verdict chip and glossary tooltips (§8).
- [M79-3] ☐ The broken-diamond quest: works with No AI, confirmed by record digest (§9).
- [M79-4] ☐ The docs front door and a docs/app label parity test (§10).
- [M79-5] ☐ *Connect your own Claude*: M77-3's client path with a prompt pack; no new verb (§11).
- [M79-D1] ☑ The diamond domains — done in another repo. ▸ detail: *completed/tracker.md ▸ 1.36.0*.
- [M79-D2] ☑ The broken-diamond teaching run — published. ▸ detail: *completed/tracker.md ▸ 1.36.0*.

### M76 · The authoring journey: build, run, explain, change, compare ([spec](spec-authoring-journey.md), r2)
- [M76] ☐ **Specified** (r2, reviewed): the investigation checkpoint (replaces M74's handoff report), acceptance checks evaluated by the analyser, scenarios, the explanation tour, the start-page pathways S7/S8; eleven PRs A1–A8 (~44k lines, each ≤5k); OD-76.1–.19 open. [Review](../handoff/review_m76_r1_k4m9.md) folded.
- ☐ **A scorecard starter-flow task**, and re-measuring the authoring approval count (still predicted, about 3; last
  measured live at 14 before FIX-4/M77's fixes).

## Session and dispatch

### M44 · Session transitions as a Fluxtion processor ([spec](spec-session-processor.md))
- [M44] ◧ Umbrella — residue: the five dialog-only entrances' kinds are verified by reading, not running (§11).
- [M44.4] ◧ The single-state model (released 1.21.0) — ☐ **open acceptance**: the filter-change and close-during-pending
  journeys headless, §13's four predictions scored as a set, the reviewers' pass.
- [M44.4r] ◧ The independent review's findings — accepted except F1's reviewer-written correction; **O2** (graph-content
  identity) open.
- [M44.6] ☐ **(owner)** A superseded load's tail still installs its log. ▸ detail: *M44*.
- ☐ _Proposed, not yet an item:_ make "this generation's store is installed" a session fact, removing
  `scanWhenInstalled`.
- ☐ **Rule-9 status-bar leftovers:** `onLoaded` and `offerSourceGraph` append notes to the status bar outside the
  `statusLine` node — the two hand-placed writers INTEG-R10 was meant to retire.

### M73 · Create a project over the action socket ([spec](spec-create-project.md))
- [M73] ☑ `open {project, create}` — archived. ▸ detail: *completed/tracker.md*. Follow-up: #143 (*Save project as*
  overwrite and nonce inheritance; `openSampleProject` routing).

### Tool agreement ([spec](spec-tool-agreement.md)) — TA-1…TA-8 shipped in 1.17.0
- [TA-5b] ☐ Implement and vendor the chosen route (owner named in TA-5a); open until shipped in a starter.
- [TA-5c] ☐ One spot-check session, after TA-5a and TA-5b ship.
- [TA-9] ☐ Analyser half of "Authoritative dispatch metadata (34)" — blocked on producer metadata.
- [TA-B] ☐ Category B of the 2026-09-19/20 feedback (D14–D20).
- [N2] ☐ Follow's snapshot-to-live reload clears flags, selection and filters.
- ☐ **F3 integration gate** — possibly obsolete; UNVERIFIED either way. ▸ detail: *Tool agreement*.

### Archived obligations restored to the live order — 2026-09-24
- [OBL-1] ☐ Background file load has no progress or cancel surface.
- [OBL-2] ☐ Autoscale-Y rescans every point on a slider drag.
- [OBL-3] ☐ First-key-seen iteration-order acceptance (M55.2). ▸ detail: *Archived obligations…*

## Producers and readers

### Mongoose audit format ([proposal](../proposals/mongoose-audit-format/README.md), rev 9; [spec](spec-mongoose-audit-production.md))
- [AF-8] ☐ **(owner)** Does a record scalar support a trailing `#` comment? Decide with AF-9.
- [AF-9] ☐ No real Mongoose export is classified as an exported call (§5).
- [AF-10] ☐ A reader plugin can apply §1a rule 1 but cannot report that it did.
- [MA-1] ☐ A processor that cannot audit says so.
- [MA-2] ☐ The text writer and the marker — MA-2.9: the writer carries processor identity. Needs MA-5, MA-6, MA-7.
- [MA-4] ☐ The developer journey — gated on MA-2's text half.
- [MA-5] ◧ Capture must fan out, and restore on stop; MA-5.7 (every-backend contract) open.
- [MA-6] ◧ A document without `eventLogRecord:` is named.
- [MA-7] ◧ Framing injection — gates MA-2; the producer escape shipped in mongoose-plugins 1.0.45.
- [MA-R] ◧ The rewritten spec; phase 1 shipped (1.22.0/1.23.0), phases 2–3 open.
- [OD-5] ☐ **(owner)** Does the Chronicle backend get a marker?
- [AF-4] ☐ Mongoose writes the text file (not this repository). [AF-6] ☐ the coupled analyser documents (waits on AF-4).
  [AF-7] ☐ release 2, blocked on the NONE-corruption diagnosis (AFMT-3). [MA-3] ☐ → mongoose-plugins#38.
  ▸ detail: *Mongoose audit format*.

### M34 · Source adapters ([spec](spec-source-adapters.md)) — .0–.3 merged
- [M34.4] ☐ First foreign adapter, out of tree (LangGraph).
- [M34.5] ☐ **(owner: field name)** Per-cycle concurrency marker. ▸ detail: *M34*.

### M31 · Log-source plugins — shipped
- [M31.5] ☐ NOT YET (owner) — a separate `analyser-reader-spi` artifact.

### M52 · Binary audit encoding — part shipped ([upstream spec](../proposals/upstream-specs/spec-binary-audit-encoding.md))
- ☐ **(owner)** The binary reader's home ([reader spec §11](../proposals/upstream-specs/spec-binary-audit-reader.md)).
- [M57.1] ☐ The last ~8 ns is a clock read. [M57.4] ☐ `fluxtion-generator-http` shades a stale runtime (upstream).
- [M52.6] ☐ mongoose `ValueOut.text` → `bytes` (2.20×) — upstream. ▸ detail: *M52*.

## Authoring and the Spring side

### Spring authoring edit loop — session intake 2026-09-25
[Spec](spec-spring-authoring-edit-loop.md).
- ◧ **Java snapshot freshness** — shipped in 1.21.0 (PR #30); vendor-archive rediscovery (§C) open.
- ◧ **Design-first project and admin-console offer** — §I1 shipped in 1.21.0 (PR #31); the §I2 console offer not started.
- ☐ **Project input grants** — policy approved (D3); implementation open.
- ☐ **Upstream handoff** — routed through [upstream asks](../proposals/upstream-asks.md); published-download acceptance
  open. ▸ detail: *Spring authoring edit loop*.

### Spring-side work block — one piece of work
- ☐ **G14** — the acceptance run from a real download; the owner accepts three residual risks, then one supervised run.
- ☐ **G14-W** — warm cache is the default for LLM runs; next: the G14 `--seed-m2` flag. PR #155's six fixes plus the independent review's answer-schema and current-control remainders are implemented, awaiting re-review; no live trial claimed. ▸ [response](../handoff/handoff_pr155_tooling_response2_2026_10_19.md)
- [SG-2] ◧ Provisioning closed; **changed-graph generation and run on the hosted ZIP** open.
- ☐ **The jars**, built once for BETA-B4 and M67.1.
- ☐ Reconciler follow-ups **G12, G5, F5/G13/F10, G7, F9** and the non-blocking P1, NF1, G22, G23 (compiler-owned).
- ☐ **(owner)** What a newly generated node logs — decided in the compiler's Spec 3 B-series. ▸ detail: *Spring-side work block*.

### Spring authoring documentation and observed acceptance — 2026-09-19
- ☐ Independent review of the two Spring guide pages.
- ☐ Participant follow-up — imitation and held-out isolation.
- ◧ Feedback 41–43 — open: 42's MCP series remove/replace.
- ◧ Cold-start measurement proposal; starter-journey review F1–F16 and its learning-routes scope; cold-start v2
  rehearsal; battery readiness R2; starter verification tiers; choice-neutral comments; default-on analyser support;
  feedback 38/39 and recurring 6.
- ☐ Journey delivery dependencies; journey starts before a project exists; topology callout placement; feedback 36
  (focus lifecycle over MCP); feature request 40 (focus captions).
- ☐ Staged feedback: authoring route and hosted audit boundary; next chart capabilities; presentation and maintenance;
  reports as evidence, not defects.
- ☐ Desk-session intake (22/23/28); validation-pack discovery; EOD reporting follow-up; two-run comparison experiment.
- ☐ Producer asks: dependency classes stay foreign (29); dependency identity (30/33); dispatch metadata (34); vendor
  diagnostics (31/32/35); a bounded session diagnostic snapshot. ▸ detail: *Spring authoring observed acceptance*.

### M46 · Authoring-toolchain repair ([spec](spec-authoring-toolchain-repair.md)) — analyser closure shipped in 1.14.0
- [M46.1a] ◑ U1 refined: the message reaches the console; `suggestedFix` does not.
- [M46.1] ☐ U1 structured diagnostics. [M46.2] ☐ U2 `target/classes` lags by one build. [M46.3] ☐ U3 `scan` no-ops
  silently. [M46.4] ☐ U5–U8. [M46.8] ☐ X1–X4 doc gaps. [M46.9] ☐ H1–H2 harness. ▸ detail: *M46*.

### M47 · Start from a template
- [M47.1] ☐ Do the onboarding templates remove the four round-16 blockers structurally? ▸ detail: *M47*.
- [OPT-2] ☑ **A starter proposed by Claude Code, with INTEG-R4, OPT-2-R1, OPT-2-R2 — released in 1.36.0 (PR #168).**
  **Acceptance open:** a scorecard task; UP-PG-10 (the pages bundled with the publisher's permission, owner
  2026-10-18; a stated licence pending). ▸ detail: *completed/tracker.md ▸ 1.36.0*.

### M48 · Authoring modes ([spec](spec-authoring-modes.md)) — .1–.4, .7, .11 shipped
- [M48.17] ☐ **(owner)** The shared-evidence-canvas thesis has had no independent read — now the Telamin Canvas product thesis; raised by M74.
- [M48.5] ☐ mode-1 selection asset · [M48.6] ☐ `generate-sources` rebind · [M48.8] ☐ cache accounting ·
  [M48.9] ☐ modes 2/3 · [M48.10] ☐ the dev harness loop · [M48.12] ☐ header fingerprint carrier · [M48.13] ☐
  compiler-generated manifests · [M48.14] ☐ resolver productisation · [M48.15] ☐ one-command reproduction ·
  [M48.16] ☐ goal → formal requirements. ▸ detail: *M48*.

### M72 · Authoring token lowering ([spec](spec-authoring-token-lowering.md)) — proposed 2026-10-13
- [M72] ☐ Lower facts locally, send the residual (L0–L7); first slice L0 + L7 model choice + capped bundle output vs an interleaved control (reference: PR #131); UP-PG-06.
  - [M72.L0] ☑ `tools/token_split.py` + tables backfilled over both ladders ([TOKEN-SPLIT](../handoff/evidence/authoring-ladder-baseline-2026-10-13/TOKEN-SPLIT.md)); L1 table view deferred (audit log 8.8% of baseline tool bytes, classifier upper bound). L7 ☑ Model choice section + `ModelChoiceEvidenceTest`. L2 D-N3 decided (no verdict word). Open: interleaved pair, L4 (i)(ii) bundle, UP-PG-06.
  - [M72.L4iii] ☐ Diagnosis in a fresh context: `load-audit-log` section + brief, `token_split.py` contexts, `g14_runner.py --no-subagent` (Agent was never gated by the allow list) (spec L4 *as designed*); open: the interleaved rungs 2–5 run, UP-PG-08.

### Upstream template content — three drafts
- [UC5] ◧ Idioms audited against the app that produced them.
- [UC1] ☐ `audit-authoring.md` · [UC2] ☐ `audit-runtime.md` · [UC3] ☐ `node-field-wiring-and-workflow.md` ·
  [UC4] ☐ `idioms-and-canonical-form.md`. ▸ detail: *Upstream template content*.

## Onboarding and journeys

### M19 · Onboarding example ([spec](spec-onboarding-example.md)) — in progress
- [M19.1] ◧ Released bundle; a refreshed final evidence artefact remains.
- [M19.1a] ◧ Mongoose starter conformance bench (waits on D-02).
- [M19.3] ◧ Tutorial screenshots (need a connected browser).
- [M19.19] ◧ Guided start — the person-watching check.
- [M19.22] ◐ The generated header claims confidentiality — fluxtion#24.
- [M19.23] ◧ UP-PG-02 landed; producer default-on not deployed. ▸ detail: *M19*.
- ☐ **#155 virgin onboarding packs** (`docs/m74-virgin-onboarding-pack`): its own PR on main (Job 2 of the
  post-1.36.0 tidy); `tools/m74_onboarding_runner.py` and the record-pack crosscheck.
- ☐ **Non-transactional skill vendoring:** the copy-in has no all-or-nothing guarantee; a partial vendor leaves a
  project with some skills placed and some not, undetected.
- ☐ **Windows first-run records for skill placeholders:** FIX-10's filled-marker approach (flag 22) was built and
  checked on the owner's Mac; confirm the same first-run record answers the markers on Windows (with M74 §11).

### M67 · The extension tour ([spec](spec-extension-tour.md)) — unblocked
- [M67.1] ☐ The catalogue (cross-repo) · [M67.2] ☐ the tour's project (playground) · [M67.3] ☐ the skill and
  verifier · [M67.4] ☐ the held-out record and docs page · [M67.5] ☐ beat 3 lights the declaration ·
  [M67.6] ☐ beat 4, after the compiler's B0 check. ▸ detail: *M67*.

### M51 · Native-ready starter template ([upstream spec](../proposals/upstream-specs/spec-native-ready-template.md)) — cross-repo
- [M51.1] ☐ UP-PG-05 catalogue field · [M51.2] ☐ UP-PG-04 starter schema · [M51.3] ☐ `template-bench.py --native`.

### Beta ([proposal](../proposals/beta-testing/README.md))
- [BETA-6] ◧ Sixth draft; one review round before any approach.
- [BETA-B1] ◧ A2 needs a generation key; the customer key journey is untested (acquisition is a future item).
- [BETA-B3] ☐ **(owner)** Template decision, then a dry run of A1–A2 by someone other than the author — the LLM half is covered by the M74 virgin protocol; the human dry run remains.
- [BETA-B4] ☐ Jar A, jar B and the spec-derived check — publication and integration open.
- [BETA-8] ◧ The journey on Haiku — A3's scoring gap. ▸ detail: *Beta*.

## Views and investigation

### M64 · Spotlight — shipped ([spec](completed/spec-spotlight.md))
- [M64.13] ☐ Menu-target follow-ups from the M64.11 review — M74 made Ask labels depend on the assistant in force (documented; stable ids not given).

### M65 · Follow refreshes open graphs — shipped ([spec](completed/spec-follow-refreshes-graphs.md))
- [M65.5] ☐ D-F5 measurement: extraction time per follow tick, demo and ~100k-record logs.

### M69 · Spotlight walks — follow-ups ([spec](completed/spec-spotlight-walks.md))
- [M69.F2] ☐ The skills mention of walks, at the next skills publication.
- [M69.F4] ☐ Strip reasons beyond three lines (optional).
- [M69.F5] ☐ The two project-transition report sites carry no control.

### M75 · Document renderer ([spec](spec-document-renderer.md)) — draft r2, 2026-10-17
- [M75] ☐ One layout, two backends (PDF + a document window) for reports and teaching lessons; owner decisions OD-75.1–.17 first, then P0–P3 (~15–20k lines, four PRs).

### M71 · Workspace Start — shipped ([spec](completed/spec-workspace-start.md))

### Project / Sources / Audit log menus — remaining follow-ups
- ☐ Display stability (the second-menu spotlight flake) · ☐ optional menu guard refinements ·
  ☐ **(owner)** D1/D2 CSV placement and a legacy File menu · ☐ D3 identity: GitHub web merges bypass the email pin ·
  ☐ **(owner)** CI policy: require the full mutation gate in branch protection.

### M21 · Topology view + step-through — core shipped
- [M21.7] ☐ _(later)_ server-sourced GraphML · [M21.8] ☐ _(later)_ node → flag · [M21.9] ☐ use `ProcessorDescriptor` ·
  [M21.11] ☐ consume the declared trace flag (needs UP-FLX-11).
- ☐ **GraphML text spotlights:** a spotlight target that names GraphML (not Java source or a chart/menu item).

### M22 · Topology view usability — 36 of 41 shipped
- [M22.3] ☐ export the view as PNG · [M22.6] ☐ alternative layouts · [M22.11] ☐ show re-dispatch's cause ·
  [M22.20] ☐ a DataFlow `.push()` target renders as an orphan. ▸ detail: *M22*.

### M20 · M29 · M33 · M40 — follow-ups
- [M29.5] ☐ **(owner, optional)** `embed: true` for small external series.
- [M33.5] ☐ Fold M12.1's fix-brief onto the model (gated) · [M33.6] ☐ **YES — build it** (owner, 2026-08-27).
- [M40.2c] ☐ _(optional)_ follow the supertype chain.

### Release tooling
- [MG-1] ◐ **Mutation-gate iteration policy and runtime** — PR #107, released in 1.32.0: minimal local checks, draft
  feedback, full merge gate, eight workers, supersession cancellation and named baselines. Acceptance open: MG-A3's
  main/release event isolation was source-reviewed, not exercised; MG-D6 grouping and MG-A7/A8 stay deferred.
  [Spec](spec-mutation-gate-iteration.md) · [acceptance](../handoff/evidence/mutation-gate-iteration-2026-10-05/ACCEPTANCE.md).
- ☐ Canonicalise the restart checker's compared file paths on macOS.

### Hardening — test-only, ongoing ([spec](spec-formula-golden-fixtures.md))
- ◧ Formula golden fixtures — the corpus grows; the next taxonomy tranche is unscheduled.

## Vision and later

### Product discovery — the notebook for event-driven applications
- [ND-1] ☐ Iteration — the cycle is a redeploy · [ND-2] ☐ the document (first step: M75's document model) · [ND-3] ☐ re-execute the document.
  ▸ detail: *Product discovery*.

### M12 · Diagnose → fix → prove ([spec](spec-closed-loop.md)) — design
- [M12.1] ☐ `export_finding` · [M12.2] ☐ `export_test_fixture` · [M12.3] ☐ `DiffBuilder` additive-vs-value.
  M12.1/M12.2 move to M75 as document sources and the intent ledger.

### M11 · Research → monitoring promotion (Grafana) — vision
- [M11.1] ☐ `export_promotion` · [M11.2] ☐ a Telamin-side tap plugin (not analyser) · [M11.3] ☐ the closed loop.

### Framing · The trust structure ([spec](spec-trust-structure.md))
- ☐ Trust-boundary amendment (review F5): a supplied audit record is evidence about the recorded run.

### M39 · Baselines ([spec](spec-baselines.md))
- [M39] ☐ **(owner)** Four questions first; the first is where a baseline lives.

### M50 · Compiler and runtime optimisation ([upstream spec](../proposals/upstream-specs/spec-generated-dispatch-performance.md)) — not analyser work
- [M50.3] ◧ W2/W3 shipped; W8 deferred · [M50.10] ◧ W9/W10 docs and conformance bench.
- [M50.5] ☐ W5 · [M50.6] ☐ W11 · [M50.7] ☐ W13 · [M50.8] ☐ W6 replay capture set · [M50.9] ☐ W7 (gated) ·
  [M50.11] ☐ W14 manifest metadata · [M50.14] ☐ the end of source-level tuning. ▸ detail: *M50*.

---

## Decisions (resolved)

Moved to [decisions.md](decisions.md) on 2026-10-01; cited as *tracker ▸ Decisions*.

## Open questions

- Graph "last occurrence per record" vs "all occurrences": the default is last and it is documented
  (`docs/site/log-format.md`); the toggle was never built. **Owner:** record the default as a decision, or make the
  toggle an item.
- Earlier closed questions are in the [detail snapshot](completed/tracker-detail-2026-10-01.md) ▸ *Open questions*.
