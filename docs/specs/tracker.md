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

_Refreshed 2026-10-01, after 1.30.1._ **Shipped since the 2026-09-27 refresh:** **1.25.0** (chart and report lifecycle,
PR #51); **1.26.0 / 1.26.1** (M69 spotlight walks); **1.27.0** (evidence bundles, first delivery); **1.28.0** (M70
replay, M71 Workspace Start); **1.29.0** (the onboard assistant and conversation journeys, PR #77); **1.30.0** (bundles as
experiments, a project that opens with its settings applied); **1.30.1** (a walk step can point at Java source, #72).
**Upstream since:** fluxtion runtime **1.1.0** and compiler **1.0.76** (`runInEventCycle`); mongoose **1.0.31** (replay
recorded at dispatch) and **1.0.32** (admin commands run in the event cycle and audit as their own record);
mongoose-plugins **1.0.45** (separator escaping, #39). The 2026-09-27 order is in the
[detail snapshot](completed/tracker-detail-2026-10-01.md) ▸ *Suggested delivery order*.

1. **Assurance debt on shipped work.**
   - **#84 / #93–#102 ◧**: corrections and the explicit source-revision disclosure decision await independent review;
     [response, evidence and reviewer prompt](../handoff/handoff_issue84_third_review_response_2026_10_01.md). Closing #84 was not approval.
   - **PR #70**: the whole-feature review of M70 replay, most of which reached `main` unreviewed.
   - **PR #87** (#79, redaction of non-Latin paths, trust-class) and **PR #88** (#83, #85): green, awaiting review.
2. **Owner decisions — they unblock the most.**
   - **M70 vs Mongoose replay:** whether bundles and `--replay-compare` accept mongoose 1.0.31's dispatch-time
     recording (▸ M70).
   - **G14:** accept its three residual risks, then run it once, supervised; on demand, not a release gate.
   - **Branch protection** requiring `mutation-gate`; **M44.6**; **BETA-B3**; **M39**'s four questions; **M34.5**'s
     field name; **AF-8/AF-9** together and **OD-5**; **O2** (graph-content identity); **#74** (is a bundle bound to a
     project or standalone?).
3. **The analyser against the new upstream stack.** Move the DEMO and replay fixtures from runtime 1.0.16 / builder
   1.0.71 to runtime 1.1.0 / compiler 1.0.76, then capture a mongoose 1.0.32 log with an admin command and check how
   1.30.x frames, covers and tables an `AdminCommandEvent` record — the defect mongoose#45 was raised from (▸ UPS-1).
4. **Finish M44** — §13's remaining acceptance, the "store installed" fact, M44.6 once decided.
5. **Small correctness items, ready now:** N2; OBL-1, OBL-2; the restart checker's path aliases; feedback 36 and 42's
   MCP series remove/replace; the evidence-bundle issues #64–#69 and #73–#85.
6. **The onboard assistant's acceptance (OA-1…OA-6)** — an authorised live-provider run, native input and IME, the
   journey's native screenshots, a cross-machine recipient.
7. **The beta** — B3 (item 2), B4's binary publication, BETA-8's A3 scoring gap.
8. **The tool agreement** — TA-5b then TA-5c; TA-9 (producer-blocked); TA-B.
9. **SG-2's hosted half**, **M67** (M67.3–.5 are ready), **M33.6**, then **M39** once answered.
10. **The Mongoose audit format, phases 2–3** (cross-repo writer half) and the **bootstrap artefacts**.
11. **M34.4/.5**, **M19**'s remnants, the small schedulable remnants (M64.13, M65.5, M48.17, M40.2c, M20.5, M29.5,
    M13.5, M21, M22, M33.5, ND-1), and the cross-repo asks in [upstream-asks.md](../proposals/upstream-asks.md).
12. **Not analyser-session work:** M50's determinism spine (compiler), M52.6 (mongoose), M57.4 (generator-http), M51
    (starter template).

---

## Upstream releases to absorb — 2026-10-01

- [UPS-1] ☐ **Admin commands now audit as their own record** (fluxtion 1.1.0, compiler 1.0.76, mongoose 1.0.32). Capture
  a real log with an admin command and check framing, coverage, topology and the table; then close mongoose#45.
- [UPS-2] ☐ **Fixtures on the released stack** — the DEMO and replay fixtures still use runtime 1.0.16 / builder 1.0.71.
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
- ☐ #73 discover/manage received bundles · #74 (design) project-bound or standalone · #75 source links across capture ·
  #76 the UI says you are in a bundle · #79 non-Latin path redaction (PR #87) · #80 bundles invisible to an agent ·
  #82 a walkthrough reel · #83 import off the event thread (PR #88) · #84 close-the-loop review · #85 working copies
  never reaped (PR #88).

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
- ☐ **(owner)** the menu's name (shipped as `AI`). ☐ **(owner)** D-AI9 wording addendum. ▸ detail: *M43*.

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

### M48 · Authoring modes ([spec](spec-authoring-modes.md)) — .1–.4, .7, .11 shipped
- [M48.17] ☐ **(owner)** The shared-evidence-canvas thesis has had no independent read.
- [M48.5] ☐ mode-1 selection asset · [M48.6] ☐ `generate-sources` rebind · [M48.8] ☐ cache accounting ·
  [M48.9] ☐ modes 2/3 · [M48.10] ☐ the dev harness loop · [M48.12] ☐ header fingerprint carrier · [M48.13] ☐
  compiler-generated manifests · [M48.14] ☐ resolver productisation · [M48.15] ☐ one-command reproduction ·
  [M48.16] ☐ goal → formal requirements. ▸ detail: *M48*.

### Upstream template content — three drafts
- [UC5] ◧ Idioms audited against the app that produced them.
- [UC1] ☐ `audit-authoring.md` · [UC2] ☐ `audit-runtime.md` · [UC3] ☐ `node-field-wiring-and-workflow.md` ·
  [UC4] ☐ `idioms-and-canonical-form.md`. ▸ detail: *Upstream template content*.

## Onboarding and journeys

### M19 · Onboarding example ([spec](spec-onboarding-example.md)) — in progress
- [M19.1] ◧ Released bundle; a refreshed final evidence artefact remains.
- [M19.1a] ◧ Mongoose starter conformance bench (waits on D-02).
- [M19.3] ◧ Tutorial screenshots (need a connected browser).
- [M19.15] ☐ The seeding prompt for step 2.
- [M19.19] ◧ Guided start — the person-watching check.
- [M19.22] ◐ The generated header claims confidentiality — fluxtion#24.
- [M19.23] ◧ UP-PG-02 landed; producer default-on not deployed. ▸ detail: *M19*.

### M67 · The extension tour ([spec](spec-extension-tour.md)) — unblocked
- [M67.1] ☐ The catalogue (cross-repo) · [M67.2] ☐ the tour's project (playground) · [M67.3] ☐ the skill and
  verifier · [M67.4] ☐ the held-out record and docs page · [M67.5] ☐ beat 3 lights the declaration ·
  [M67.6] ☐ beat 4, after the compiler's B0 check. ▸ detail: *M67*.

### M51 · Native-ready starter template ([upstream spec](../proposals/upstream-specs/spec-native-ready-template.md)) — cross-repo
- [M51.1] ☐ UP-PG-05 catalogue field · [M51.2] ☐ UP-PG-04 starter schema · [M51.3] ☐ `template-bench.py --native`.

### Beta ([proposal](../proposals/beta-testing/README.md))
- [BETA-6] ◧ Sixth draft; one review round before any approach.
- [BETA-B1] ◧ A2 needs a generation key; the customer key journey is untested (acquisition is a future item).
- [BETA-B3] ☐ **(owner)** Template decision, then a dry run of A1–A2 by someone other than the author.
- [BETA-B4] ☐ Jar A, jar B and the spec-derived check — publication and integration open.
- [BETA-8] ◧ The journey on Haiku — A3's scoring gap. ▸ detail: *Beta*.

## Views and investigation

### M64 · Spotlight — shipped ([spec](completed/spec-spotlight.md))
- [M64.13] ☐ Menu-target follow-ups from the M64.11 review.

### M65 · Follow refreshes open graphs — shipped ([spec](completed/spec-follow-refreshes-graphs.md))
- [M65.5] ☐ D-F5 measurement: extraction time per follow tick, demo and ~100k-record logs.

### M69 · Spotlight walks — follow-ups ([spec](completed/spec-spotlight-walks.md))
- [M69.F2] ☐ The skills mention of walks, at the next skills publication.
- [M69.F4] ☐ Strip reasons beyond three lines (optional).
- [M69.F5] ☐ The two project-transition report sites carry no control.

### M71 · Workspace Start — shipped ([spec](completed/spec-workspace-start.md))
- [M71.F1] ☐ Optional: the tour's instructional sequence, reading width, the template dialog's hierarchy.

### Project / Sources / Audit log menus — remaining follow-ups
- ☐ Display stability (the second-menu spotlight flake) · ☐ optional menu guard refinements ·
  ☐ **(owner)** D1/D2 CSV placement and a legacy File menu · ☐ D3 identity: GitHub web merges bypass the email pin ·
  ☐ **(owner)** CI policy: require the full mutation gate in branch protection.

### M21 · Topology view + step-through — core shipped
- [M21.7] ☐ _(later)_ server-sourced GraphML · [M21.8] ☐ _(later)_ node → flag · [M21.9] ☐ use `ProcessorDescriptor` ·
  [M21.11] ☐ consume the declared trace flag (needs UP-FLX-11).

### M22 · Topology view usability — 36 of 41 shipped
- [M22.3] ☐ export the view as PNG · [M22.6] ☐ alternative layouts · [M22.11] ☐ show re-dispatch's cause ·
  [M22.20] ☐ a DataFlow `.push()` target renders as an orphan. ▸ detail: *M22*.

### M20 · M29 · M33 · M40 — follow-ups
- [M20.5] ☐ Project artifact pointers — offer, never act.
- [M29.5] ☐ **(owner, optional)** `embed: true` for small external series.
- [M33.5] ☐ Fold M12.1's fix-brief onto the model (gated) · [M33.6] ☐ **YES — build it** (owner, 2026-08-27).
- [M40.2c] ☐ _(optional)_ follow the supertype chain.

### Release tooling
- ☐ Canonicalise the restart checker's compared file paths on macOS.

### Hardening — test-only, ongoing ([spec](spec-formula-golden-fixtures.md))
- ◧ Formula golden fixtures — the corpus grows; the next taxonomy tranche is unscheduled.

## Vision and later

### Product discovery — the notebook for event-driven applications
- [ND-1] ☐ Iteration — the cycle is a redeploy · [ND-2] ☐ the document · [ND-3] ☐ re-execute the document.
  ▸ detail: *Product discovery*.

### M12 · Diagnose → fix → prove ([spec](spec-closed-loop.md)) — design
- [M12.1] ☐ `export_finding` · [M12.2] ☐ `export_test_fixture` · [M12.3] ☐ `DiffBuilder` additive-vs-value ·
  [M12.4] ☐ "Fix with agent…" launcher.

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
