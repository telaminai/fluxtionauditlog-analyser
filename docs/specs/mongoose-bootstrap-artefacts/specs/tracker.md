# Mongoose → analyser validation tracker

**Purpose:** Deliver the evidence-backed workflow in
[spec-mongoose-analyser-validation.md](spec-mongoose-analyser-validation.md), in the stated order.
This is the live tracker: it contains in-progress and future work only. Completed milestones move to
`docs/specs/completed/` when their review is accepted.

## Delivery protocol

Before implementing a milestone, create a self-contained brief at
`docs/handoff/handoff_<date>_<n>.txt`: orientation reading, scope, acceptance evidence, constraints and
the exact tracker item. When it is done, add the paired `_report.txt` with actual evidence, deferrals and
spec-versus-reality findings. Keep the pair in `docs/handoff/` until an independent reviewer records a
verdict; then archive brief, report and review together. A handoff is not a substitute for a test or a
human check.

Every implementation slice must state the input fixture, expected output/audit, exact commands, actual
artifact path and reviewer. Never record a client registration as a successful AI investigation without
a tool result that agrees with the visible analyser workspace.

## Current delivery order

- [V0] ◧ **Orient the AI and agree the validation contract** — _review findings resolved 2026-08-28;
  owner decision D-02 is still required before application implementation._
  - [V0.1] ☑ Root [`CLAUDE.md`](../../CLAUDE.md) authored: project map, authoring rules, compile/run
    loop, analyser/MCP boundaries and reusable-skill direction.
  - [V0.2] ☑ This specification records scope, gates, requirements, evidence and open decisions.
  - [V0.3] ☑ This live tracker records dependency order and the handoff rule.
  - [V0.4] ☐ Owner selects the first bounded `PriceUpdate` question/result (D-02) and accepts or amends
    the contract. No implementation beyond the starter baseline before this review.
  - [V0.5] ◧ **M19 onboarding alignment** — `AGENTS.md` is present. Capture the relevant framework
    orientation/golden-path material under `docs/ai/` with version/date, retain canonical URLs as
    possibly newer references, and reduce root `CLAUDE.md` to the project-specific adapter before a
    bundle claim. This starter currently has no profile, text audit log or embedded snapshot.
  - [V0.6] ☑ A project-local candidate
    [`.claude/skills/mongoose-local/SKILL.md`](../.claude/skills/mongoose-local/SKILL.md) records the
    safe workflow in the frontmatter shape the analyser can discover. It is not a graduated shared skill:
    **Find skills…** only offers it, and a person must explicitly add it before it becomes a runbook or
    AI context.

### Review follow-ups A1–A4 — each remains open until its stated evidence exists

- [A1] ☐ **Run the brokered-loop bench against the real starter (VAL-12).** Blocked until Mongoose
  publishes UP-MNG-01's registry entry and the export/GraphML endpoints. A `--stub` pass or a hand-run
  local deployment is not this acceptance.
- [A2] ☐ **Exercise registry-first discovery with the YAML fallback.** The candidate skill records the
  rule but no actual published registry exists yet. Prove it reads a real entry when available and uses
  YAML only as the declared single-project fallback; it must never create or repair the entry.
- [A3] ☐ **Record UP-MNG-02's independent owner disposition (D-05).** The upstream record now separates
  it from UP-MNG-01; retain, defer or withdraw still needs Mongoose-owner evidence.
- [A4] ☐ **Prove the project-local skill's manual adoption path.** With a completed project profile,
  use **Find skills…**, confirm the candidate is only offered, then explicitly add it and verify the
  resulting pointer/context. This does not graduate the shared skill (V5).

- [V1] ☐ **Make the starter's first typed vertical slice real** — _depends on V0.4._
  - Prove what `FileEventSource` emits from `data/input.txt`; it currently appears to be CSV while the
    graph handles `PriceUpdate`.
  - Add the smallest supported pure mapper/parser and an explicit invalid-input policy if required.
  - Add tests proving typed handler delivery and a deterministic named output/audit result (VAL-02/03).
  - Handoff gate: implementation brief, focused test evidence, full `./mvnw test`, owner/reviewer run.

- [V2] ☐ **Deploy the tested slice to the local Mongoose server** — _depends on V1 review._
  - Run key preflight, package, one server, admin-console check, known output and clean stop (VAL-04).
  - Record the actual command, JDK/Mongoose/Fluxtion versions, port, fixture, process/stop procedure and
    output path; do not retain secrets in the report.
  - **Brokered-loop condition (VAL-12):** when Mongoose supplies UP-MNG-01's registry and the export
    endpoints, run the analyser repository's `tools/bench/loop-bench.py` against the real server. Until
    then the manual local deployment proof is valid V2 evidence, but not an end-to-end brokered-loop or
    distribution claim.

- [V3] ☐ **Capture and investigate an audit artifact** — _depends on V2 review; D-01 must resolve._
  - Identify version-supported audit-capture settings for M19's INFO text/YAML file at the predictable
    `./logs/audit-<name>.yaml` path; do not substitute Chronicle while no analyser reader exists.
  - Add `.analyser/project.fluxtion-settings` with relative source roots and the generated EventProcessor
    FQN; verify import/auto-detection and click-to-source against the artifact.
  - Produce one known local artifact, open it in the Audit Log Analyser with Follow, and answer the V0
    question with a visible filter/record/graph/report result (VAL-05/06/10/11).
  - Record artifact retention/cleanup and do not overwrite the reviewed evidence with a later run.
  - An unstubbed `loop-bench.py` pass complements this work; it does not prove the starter wrote the
    required local INFO text/YAML audit file or that the file answered the V0 question.

- [V4] ☐ **Share the open analyser workspace with an AI client** — _optional; depends on V3 review and
  D-03._
  - Use the analyser UI's explicit registration/check flow for the selected client; import/approve in
    the client separately.
  - Ask a bounded evidence question over MCP and compare its result with the V3 human-visible result
    (VAL-07). Registration/bridge success alone does not close this item.

- [V5] ☐ **Extract the proven operations into one reusable local skill** — _depends on V1–V4 evidence
  and D-04._
  - Define registry-first discovery with a YAML fallback, preflight, build/test/package, single-server
    status/start/stop and artifact handoff interfaces (VAL-08). The skill reads a Mongoose-published
    registry entry when present; it never creates or repairs one.
  - Exercise the skill twice on starter-shaped projects, including missing-mapper or missing-audit
    failure, before declaring it reusable. Add custom Mongoose/analyser support only for a demonstrated
    gap the skill/scripts cannot cover.

## Decisions waiting for the owner

| ID | Needed before | Question |
|---|---|---|
| D-02 | V1 | What one domain result should a `PriceUpdate` produce in this proof slice? |
| D-01 | V3 | Which installed-version configuration writes the required INFO text/YAML audit file at the declared `logs/` path? |
| D-03 | V4 | Which client gets the first end-to-end MCP acceptance: Claude or Codex? |
| D-04 | V5 | After validation, where will the shared `mongoose-local` skill live and which starter markers must it require? The current `.claude/skills/...` file is project-local only. |
| D-05 | Distribution / brokered-loop claim | Does the Mongoose owner retain, defer or withdraw UP-MNG-02 independently of UP-MNG-01? Local skills do not decide it. |

## Evidence ledger (populate as work lands)

| Milestone | Fixture / question | Command and result | Artifact / visible evidence | Independent review |
|---|---|---|---|---|
| V0 | Contract and AI orientation | Docs authored and review resolution recorded 2026-08-28 | `CLAUDE.md`, candidate `SKILL.md`, spec, tracker | D-02 owner decision pending; no application implementation started |
| V1 | — | — | — | — |
| V2 | — | — | — | — |
| V3 | — | — | — | — |
| V4 | — | — | — | — |
| V5 | — | — | — | — |
