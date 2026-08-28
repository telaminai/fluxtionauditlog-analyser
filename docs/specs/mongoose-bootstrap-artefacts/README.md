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
| `specs/spec-mongoose-analyser-validation.md` | The validation contract. | Are the scope, gates, evidence and boundaries sufficient to make the end-to-end claim meaningful? |
| `specs/tracker.md` | Live delivery order. | Are dependencies, handoff gates and acceptance criteria in the right sequence? |
| `specs/review-onboarding-example-overlap.md` | M19 comparison and concrete current gaps. | Does this remain one onboarding path with an honest conformance bench? |
| `scripts/` | Current starter preflight and server-launch scripts. | Are the proposed skill inputs/outputs grounded in the real starter interface? |
| `config/server-config.yml` | Current Mongoose deployment descriptor. | Is the mapper/audit risk identified rather than assumed away? |
| `skill/mongoose-local-skill-contract.md` | Proposed shared user-local skill interface. | Which contract elements should become a reusable skill and which stay project-specific? |

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
