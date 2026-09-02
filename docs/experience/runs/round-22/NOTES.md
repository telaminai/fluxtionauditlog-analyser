# Round 22 — 15 event types, a real graph, and neither cell works

Two independent Fluxtion cells, same bootstrap (round 20's winner: template, no annotation table,
798 tokens), same 15-event-type spec with every rule marked EDGE or CONDITION.

| cell | `mvn` runs | classes | own tests | result |
|---|---|---|---|---|
| A | 12 (2 failed) | ~20 | **21 green** | **3 defects** |
| B | 6 (1 failed) | 41 | **3 green** | **emits nothing at all** |

**Neither produced a working engine.** Both shipped green test suites.

## Cell B: the graph is right, the output path is dead

On a scenario that must emit `RELEASE`, B emits nothing. The audit log shows the graph doing the work:

```
event 4 Order  ['orderStore','orderStateTracker','timestampTracker',
                'releaseDecider','releaseTracker','releaseTimestampStore','releaseDecider']
```

`releaseDecider` fires; `decisionCollector` never appears in any cycle. The decision is computed and
dropped. Its own report named the smell — *"accessing DecisionCollector from Main via reflection"*.
**The framework part came out right; the hand-written extraction from graph to file is what failed.**

Its test `releaseEmitsWhenAllocatableAndCreditOk` is green while the engine emits nothing.

## Cell A: three defects behind 21 passing tests

| # | defect | probe |
|---|---|---|
| 1 | **R8 is suppressed when R7 fires on the same DISPATCH.** Both rules share an "already dispatched this order" guard, but they are independent rules and my output format explicitly allows both. | hazardous + overweight → only `HAZARD_BLOCK`. Non-hazardous + overweight → `OVERWEIGHT` fires correctly, which isolates it |
| 2 | **R10 re-fires on `COUNT` when stock is already negative.** −5 → −3 is not a crossing from zero-or-above. Two consecutive `ADJUST`s are handled correctly, so the defect is in the `COUNT` path only. | `RECEIPT 5, ADJUST −10, COUNT −3` → two `STOCKOUT`s |
| 3 | **R9 never fires.** Release at t=2000, `PICKDONE` at t=3602001, delta 3600001 > 3600000. | no `SLA_BREACH` emitted, at any delta tried |

Its tests `overweightDetected`, `stockoutWhenGoingBelowZero` and `slaBreachDetected` are all green.

## The differential method was defeated, and that is itself informative

The design was: build two engines, diff them, and treat divergence as evidence my spec is
under-determined. **B emits nothing, so every comparison diverges and none of them mean anything.**
Differential testing needs two *working* implementations; with one broken it degenerates to a single
engine with extra steps. The defects above were found by hand-built probes attacking boundaries, not by
the diff.

**What the method did deliver, before any code ran:** the two agents between them listed **sixteen**
places the spec is under-determined, and several are real — which timestamp R9 measures from, whether
"stops being releasable" means either condition or both, whether a `COUNT` to a negative value is a
crossing, whether two decisions may fire on one `DISPATCH`, and that the framework has no notion of an
event number so it must be threaded in from the parser. That is a specification review obtained from
agents who had to make each call in order to compile.

## The scale answer, third attempt

Rounds 13 and 14 failed at ~50 nodes in both arms. This round is smaller — 15 event types, 12 rules,
~20 nodes — with a far better bootstrap and a spec whose EDGE/CONDITION distinction was written
specifically to remove the ambiguity class that broke round 21. **It still did not produce a working
engine in either cell.**

The honest reading: **somewhere between 3 detectors and 12 interdependent rules, Haiku stops producing
correct engines, and the bootstrap work does not move that ceiling.** Rounds 19–20 got 8/8 at three
detectors in five build cycles. The same bootstrap at twelve rules gets zero working engines out of two.

What did not fail is the framework part. B's graph dispatched correctly across 15 event types and a
join-of-joins; A's release, allocation and credit logic are right. **Both failures are in
hand-written application code** — an extraction path, a shared guard, a boundary comparison — which is
the part no framework covers.
