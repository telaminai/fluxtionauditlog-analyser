# Handoff — the finish-first round after 1.13.1, 2026-09-16

_For the reviewing session. Four pieces of work on `main` since the 1.13.1 tag (`f249113a`), each with its own
ledger entry in [`unreviewed-changes.md`](unreviewed-changes.md); this page is the map. Every claim names a
commit, a test or a file. Author verification only — the independent pass is yours._

| # | Item | Commits | Ledger / spec |
|---|---|---|---|
| 1 | **M44.3 — the asynchronous session driver; the open as a decision. M44.3a with it.** | `9e685e89` | ledger *M44.3 the asynchronous session driver…*; `spec-async-session-driver.md` ▸ *As built* (status block); tracker M44 |
| 2 | **Formula golden: N1 (duplicate metadata rejected) + two clamp fixtures (min/max elementwise, the M28 guarantee)** | `fe2cd88f` | tracker ▸ Hardening; `spec-formula-golden-fixtures.md` taxonomy rows unchanged |
| 3 | **Skills reworded after the 1.13.1 review** (eligible instance fields; the capture rolls and nothing deletes; input-tailing scoped to the starter's file source) + index re-pin | `b71164ae` (bytes, red on the index test by design) → `b31cf945` (re-pin) | ledger entries of the review; `docs/skills/m19-skills/2/index.json` revision `b71164ae` |
| 4 | **Note rules drift when zoomed in** (owner report, reproduced over MCP on the live 1.13.1) | this commit | ledger *Note rules drift…* |

## What to read first for M44.3

1. `docs/specs/spec-async-session-driver.md` — the status block says what was built and names the two
   deviations from the text (Pending IS dispatched so it is recorded; superseded work is not cancelled).
2. `session/node/LogOpening.java` (new), `OperationGate` (`inFlightWhat`), `LogArrival` (judges on `LogOpened`
   only), `SessionEffects.CloseGraphEffect(opId, graphPath)`, `SessionDriver` (thread confinement).
3. `MainFrame`: `requestOpenLog` / `requestOpenRolledSet` / `startLoad` / `onLoaded` (submits `LogOpened`
   FIRST; discards a refused store) / `onLoadFailed`; the `OpenLogEffect` and identity-bound `CloseGraphEffect`
   cases in `performSessionEffect`; `context.inFlight`.
4. The regenerated processor `session/generated/SessionProcessor.java` (+ `.graphml`, + the resources copy):
   generated, not hand-edited; 38 nodes; builder 1.0.68 through the hosted generator.
5. Why the three vocabulary GraphML fixtures did NOT change: they are a frozen pair with the legacy export for
   the exporter-compatibility tests; the regen script's refresh is now opt-in; `DescriptorFingerprintTest`
   compares the live processor with the live GraphML.

## Evidence the author ran

- `AsyncOpenReplayTest` (6): Pending leaves state untouched + one round + recorded; landed result opens and
  judges, closing the graph it NAMED; supersede refused and recorded as `staleResult`; a refresh observation
  judges nothing (the M44.3a case); wrong thread → `ProtocolViolation`; failed open leaves the previous log.
- `LogArrivalReplayTest` and `EffectDrainAtBatchEndTest` rewritten to request-then-result; all other replay
  tests unchanged.
- `PairingDuringLoadFrameTest` 5/5 on a display (CI `ui-frame` job too). The positive control moved: a human
  ARRIVAL closing a mismatching graph warns as a dialog; a human File-menu close no longer warns — that warning
  was the R2-F3 re-judgement.
- `tools/verify-session-transitions.py` ALL PASS on the built jar. `mvn -o clean verify` green; strict docs;
  rule-1 sweep.

## Open, stated

- `LogObserved`/`GraphObserved` keep their `open` flag; retiring observations is the next slice.
- Supersede on a genuinely slow load is replayed, not exercised live.
- `openLogs` and `discoverGraphs` declare no audience (they raise no warning today).
