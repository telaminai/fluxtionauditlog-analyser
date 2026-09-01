# Rounds 09 + 10 — behaviours-only, full capability stack, 2×2 on model

Predictions: [round-09/PREDICTION.md](PREDICTION.md), [round-10/PREDICTION.md](../round-10/PREDICTION.md)
(incl. twelve hard calls at `fabfbc3`). 8 runs, committed before launch, run concurrently.

## Result

| | docs | no docs |
|---|---|---|
| **Opus** | armA-1 ✅ 8 nodes · armA-2 ✅ 8 nodes | armB-1 ✅ 8 nodes · armB-2 ✅ 8 nodes |
| **Haiku** | haikuA-1 ✅ 7 · haikuA-2 ✅ 7 | haikuB-1 ❌ **no Fluxtion at all** · haikuB-2 ✅ 7 |

**Opus: 4/4 built correct graphs and evidenced M1–M6 from the audit log, in BOTH arms.**
**Haiku: 3/4. The single total failure is in the no-docs arm.**

## Hard calls: 4 of 12

| # | call | actual | |
|---|---|---|---|
| H1 | Opus green 4/4 | 4/4 | ✓ |
| H2 | Opus satisfies M1–M6 4/4 | 4/4 | ✓ |
| H3 | Haiku green 4/4 | **3/4** | ✗ |
| H4 | Haiku satisfies M1–M6 2/4 | **3/4** | ✗ |
| H5 | haikuA 2/2, haikuB 0/2 | 2/2 and **1/2** | ✗ |
| H6 | Opus shows no arm difference | none — both arms excellent | ✓ |
| H7 | **analyser run by 0/8** | **all 4 Opus used it, productively** | ✗✗ |
| H8 | defect first found via audit log in 2/8 | 4 of 8 | ✓ (low) |
| H9 | reads generated source: Opus 4/4, Haiku 1/4 | Opus 4/4, Haiku ~2/4 | partial |
| H10 | **M5 most-failed** | **M5 was never failed by anyone** | ✗ |
| H11 | median attempts Opus 2, Haiku 4 | **Opus ~3, Haiku ~1.5** | ✗✗ |
| H12 | median nodes Opus 7, Haiku 5 | Opus 8, Haiku 7 | ✗ |

**H11 is the most interesting miss: Haiku took FEWER build attempts than Opus.** Not because it was
better — because Opus spent attempts on *fault injection*. armA-1 deliberately removed its M3 gate to
prove the requirement was not satisfied vacuously; armB-2 ran a negative control on M6. Attempts-to-green
measures caution, not competence, and I had it backwards.

**H7 was badly wrong and it matters most.** I predicted our own instrument would go untouched. All four
Opus agents used it, and it did work nothing else could: `coverage` confirmed 8 declared / 8 covered /
`neverLogged: []`, and the GraphML's `fluxtion.topologicalRank` gave **build-time** confirmation of M5
(`clearingReport` highest rank) — a property the audit log can only confirm after the fact.

## The four-layer stack, as actually used

Every layer caught something no other layer could:

| layer | caught, measured |
|---|---|
| **diagnostics** | `final` collection fields making a node unconstructible (D1, both no-docs Opus runs) |
| **docs** | package names and `@NoTriggerReference`'s FQN; and their absence cost haikuB-1 the framework |
| **generated source** | root dispatch order is **alphabetical, not declaration order** — armB-1 proved a design reading `cycleId` from a heartbeat node would stamp collateral deposits with the *previous* cycle |
| **audit log** | a lifecycle record inflating an M4 count; a held position with no price silently contributing 0 — "arithmetically correct, materially misleading" |
| **analyser** | `coverage` / `topologicalRank` cross-checks, and it found its own bugs |

## My specification was defective, and all four Opus agents found it

**M3 and M4 are in direct tension** and I wrote both. M3 wants the cascade dead on a no-op parameter; M4
wants a report regardless. **All four independently invented the same repair**: an always-true node
(`cycleClock` / `cycleSequencer` / `eventCycle` / `eventIngress`) that is a second trigger parent of the
report and carries no data. armB-1 named the trap the others avoided: making that node a parent of the
*books* would silently break M3.

Four agents, no shared context, one structure. That is the strongest single result in the round — and it
is about the framework's expressiveness, not about docs or models.

## The arc prediction (owner)

**Not visible, as predicted.** Six of eight runs went green in ≤3 attempts and never reached the point
where deferred benefit appears. The one arc-shaped datum is negative: **haikuB-1 discarded the framework
at the first friction and rationalised it** — *"acceptable since business logic works correctly"*.

This reinforces the recorded design consequence: **every round so far is greenfield**, which is the worst
window for a framework whose benefit is change safety. A CHANGE round is still the untested case.
