# Specs — index

What each live spec is for, where it stands, and which part of [tracker.md](tracker.md) owns its open work. A spec
is a design record: its decision IDs and section numbers are cited from code and reviews, so specs are archived
whole and never merged by rewriting. Status here is a one-line summary dated 2026-10-01; the spec's own status
block and the tracker are authoritative.

- **Start here:** [spec.md](spec.md) — the product spec; [tracker.md](tracker.md) — live work and the delivery order.
- **Shipped:** [completed/](completed/) — finished specs and the archived tracker ([completed/tracker.md](completed/tracker.md)).
- **Owned upstream:** [../proposals/upstream-specs/](../proposals/upstream-specs/README.md) — compiler, runtime,
  builder and template designs this repo measured but does not implement.

When a spec's delivery ships and only tracked follow-ups remain, move it to `completed/` with a dated *Archived*
line, and update this index in the same change.

## Session and dispatch

| Spec | Status | Tracker |
|---|---|---|
| [spec-session-processor.md](spec-session-processor.md) | M44.4/M44.5 shipped; §13 acceptance and M44.6 open | M44 |
| [spec-tool-agreement.md](spec-tool-agreement.md) | TA-1…TA-8 shipped in 1.17.0; TA-5b, TA-5c, TA-9, TA-B open | Tool agreement |

## Evidence

| Spec | Status | Tracker |
|---|---|---|
| [spec-evidence-integrity.md](spec-evidence-integrity.md) | analyser half shipped (1.21.0, 1.23.0); the producer half D-E9 is open | completed ▸ M68; MA-2/MA-7 |
| [spec-evidence-bundle-replay.md](spec-evidence-bundle-replay.md) | released in 1.28.0; whole-feature review (PR #70) and an owner question open | M70 |
| [spec-shared-evidence-canvas.md](spec-shared-evidence-canvas.md) | thesis; no independent read yet | M48.17 |

## Assistant

| Spec | Status | Tracker |
|---|---|---|
| [spec-onboard-assistant-journeys.md](spec-onboard-assistant-journeys.md) | released in 1.29.0; acceptance open | Onboard assistant and conversation journeys |
| [spec-assistant-actions-mcp.md](spec-assistant-actions-mcp.md) | M13.1–13.4 shipped; only M13.5 open | M13 |

## Authoring

| Spec | Status | Tracker |
|---|---|---|
| [spec-spring-authoring-edit-loop.md](spec-spring-authoring-edit-loop.md) | proposed v4; slices shipped in 1.21.0–1.22.1, the rest open | Spring authoring edit loop |
| [spec-authoring-toolchain-repair.md](spec-authoring-toolchain-repair.md) | analyser closure shipped in 1.14.0; U1–U8, X1–X4, H1–H2 open | M46 |
| [spec-authoring-experience.md](spec-authoring-experience.md) | proposed; the method for M19.14 and M19.15 | M19 |
| [spec-authoring-modes.md](spec-authoring-modes.md) | proposed; the canonical authoring architecture | M48 |
| [spec-authoring-mode-selector.md](spec-authoring-mode-selector.md) | proposed; M48.7 built | M48 |
| [spec-authoring-session-walkthrough.md](spec-authoring-session-walkthrough.md) | companion to the mode selector (real harness output) | M48 |

## Onboarding and journeys

| Spec | Status | Tracker |
|---|---|---|
| [spec-onboarding-example.md](spec-onboarding-example.md) | M19 in progress; most slices archived | M19 |
| [spec-project-starter-journey.md](spec-project-starter-journey.md) | analyser half shipped in 1.16.0; producer and download acceptance open | M47, M19.23 |
| [spec-extension-tour.md](spec-extension-tour.md) | specified and unblocked; not started | M67 |
| [spec-agent-brokered-dev-loop.md](spec-agent-brokered-dev-loop.md) | accepted v2 (replaced M18); the reference the bootstrap artefacts anchor to | M19; delivery order item 10 |
| [mongoose-bootstrap-artefacts/](mongoose-bootstrap-artefacts/README.md) | review snapshot of the starter's bootstrap files | delivery order item 10 |

## Producers and readers

| Spec | Status | Tracker |
|---|---|---|
| [spec-mongoose-audit-production.md](spec-mongoose-audit-production.md) | reader half shipped (1.22.0, 1.23.0); writer half open, cross-repo | Mongoose audit format |
| [spec-source-adapters.md](spec-source-adapters.md) | M34.0–.3 merged; M34.4/.5 open | M34 |

## Vision and quality — not scheduled

| Spec | Status | Tracker |
|---|---|---|
| [spec-trust-structure.md](spec-trust-structure.md) | framing; proposed | Framing · The trust structure |
| [spec-closed-loop.md](spec-closed-loop.md) | draft; the design for M12 | M12 |
| [spec-baselines.md](spec-baselines.md) | specified; four owner questions open | M39 |
| [spec-latency-model-and-controls.md](spec-latency-model-and-controls.md) | proposed; no tracker item owns it | none — owner to file or withdraw |
| [spec-formula-golden-fixtures.md](spec-formula-golden-fixtures.md) | harness and first tranche landed; the corpus grows | Hardening |

## Development and release tooling

| Spec | Status | Tracker |
|---|---|---|
| [spec-mutation-gate-iteration.md](spec-mutation-gate-iteration.md) | proposed r2; no implementation; grouping deferred | MG-1 |
