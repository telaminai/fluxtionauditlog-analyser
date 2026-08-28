# Review snapshot — Mongoose/Fluxtion/analyser bootstrap

**Status:** Review material, drafted 2026-08-28. This directory mirrors the working bootstrap
artefacts for the downloaded local Mongoose starter project.

It lets an independent reviewer assess the proposed AI/Mongoose/analyser workflow alongside the
analyser codebase. It is **not** an analyser feature, an executable project, a client registration, or
an assurance that the starter's audit output is already analyser-compatible.

## Contents

| Snapshot | Source role | Review question |
|---|---|---|
| `ai/CLAUDE.md`, `ai/AGENTS.md` | Root AI instructions for the starter. | Does an agent get a safe, fact-based author/build/run/analyse loop? |
| `starter-README.md` | Copied root README from the starter. | Does the human-facing entry point state the validation status rather than imply the workflow already works? |
| `.claude/skills/mongoose-local/SKILL.md` | Project-local candidate skill. | Is its discovery shape useful while its operations remain explicitly gated and ungraduated? |
| `specs/spec-mongoose-analyser-validation.md` | The validation contract. | Are the scope, gates, evidence and boundaries sufficient to make the end-to-end claim meaningful? |
| `specs/tracker.md` | Live delivery order. | Are dependencies, handoff gates and acceptance criteria in the right sequence? |
| `specs/review-onboarding-example-overlap.md` | M19 comparison and concrete current gaps. | Does this remain one onboarding path with an honest conformance bench? |
| `scripts/` | Current starter preflight and server-launch scripts. | Are the proposed skill inputs/outputs grounded in the real starter interface? |
| `config/server-config.yml` | Current Mongoose deployment descriptor. | Is the mapper/audit risk identified rather than assumed away? |
| `skill/mongoose-local-skill-contract.md` | Proposed shared user-local skill interface. | Which contract elements should become a reusable skill and which stay project-specific? |

## Reviews received

| Review | Date | Verdict |
|---|---|---|
| [`review_mongoose_bootstrap_artefacts.txt`](../../handoff/review_mongoose_bootstrap_artefacts.txt) | 2026-08-28, the first session | Sound, one structural finding: the work cited M19 but not [`spec-agent-brokered-dev-loop.md`](../spec-agent-brokered-dev-loop.md) (ACCEPTED v2), which owns the loop it describes. |
| [`report_mongoose_bootstrap_review_resolution.txt`](../../handoff/report_mongoose_bootstrap_review_resolution.txt) | 2026-08-28, source-author response | A1–A4 accepted with limits: the real-server bench is conditional VAL-12 and does not replace native audit evidence; registry publication stays server-side; UP-MNG-02 needs its own decision; **Find skills… offers and never auto-adds**. Awaiting independent review. |
| [`review_mongoose_bootstrap_review_resolution.txt`](../../handoff/review_mongoose_bootstrap_review_resolution.txt) | 2026-08-28, the second session (independent) | VAL-12 scope, the A4 offer-not-add correction and the M19.1a/V1 gating all verified against the bench, `PointerDialog` and both trackers. Source/snapshot parity UNVERIFIABLE from that machine — F1: this README records no source revision/date, which its own update rule requires; F2: two `../../CLAUDE.md` links in `specs/` are starter-relative and dead here. |

The headline of that review, if you read nothing else: **§H's conformance harness already exists**
(`tools/bench/loop-bench.py`, merged M19.6) and plays the registry/export/analyser leg with PASS/FAIL per
step. It becomes VAL-12 when a real Mongoose starter publishes the needed server-side contract; it does
not replace the M19 native text/YAML audit and visible-investigation evidence in V3.

## Links inside `specs/` are STARTER-relative (review F2, 2026-08-28)

`specs/tracker.md` and `specs/spec-mongoose-analyser-validation.md` link `../../CLAUDE.md`. That resolves
in the **starter**, where those files sit beside a root `CLAUDE.md`; it does not resolve here, where the
snapshot root is this directory and the file is at [`ai/CLAUDE.md`](ai/CLAUDE.md).

They are left unadapted on purpose. The alternative — rewriting paths so they resolve in the snapshot —
would make every future refresh re-apply the same edits, and would put intentional differences into a
copy whose parity with the source **already cannot be checked** (F1 below). Divergence that is
indistinguishable from drift is worse than a link a sentence explains. So the rule is stated once, here:
a starter-relative link in `specs/` points at the real project, not at this copy.

Note that `mkdocs build --strict` does not catch these — `docs/specs/` is not part of the built site, so
the link checker never sees this directory. It was found by reading.

## Source-of-truth and update rule

The starter project remains the source of truth for executable scripts, configuration, fixtures and
domain code. This directory is a review copy. When an artefact changes in the starter, refresh the
matching snapshot in the same review slice and record the source revision/date here. Do not edit a
snapshot as a stealth alternative to changing the real starter.

No API keys, tokens, client configuration files, captured audit data or user-specific paths belong in
this directory. The copied `check-fluxtion-key.sh` shows its existing user-local key lookup only; it
does not include a key.

## Current review focus

1. Validate the first known technical risk: CSV input versus a `PriceUpdate` handler needs an evidenced
   mapper, not an inference from comments.
2. Confirm the proposed evidence gates: typed result, server deployment, audit artifact, analyser UI
   investigation, then optional MCP comparison.
3. Decide whether the proposed `mongoose-local` skill should be implemented as one local shared skill
   after two proved starter workflows, rather than copied into generated projects.

The detailed open decisions and owner gates are in
[specs/spec-mongoose-analyser-validation.md](specs/spec-mongoose-analyser-validation.md) and
[specs/tracker.md](specs/tracker.md).
