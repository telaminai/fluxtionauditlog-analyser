# Round 24 — skeleton-first works

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `d6f18a0` before launch. **3 of 5.**

| round | cell | bootstrap | `mvn` | own tests | traces | score (8 probes) | score (11 probes) |
|---|---|---|---|---|---|---|---|
| 22 | A | plain template | 12 | 21 | 0 | 5/8 | — |
| 23 | C | + trace.sh | 15 | 0 | 0 | 0/8 | — |
| 23 | D | + trace.sh | 22 | 3 | 9 | 3/8 | — |
| **24** | **E** | **+ failing test + build order** | **~8** | 9 | 4 | **8/8** | **9/11** |
| **24** | **F** | same | 10 | 12 | 4 | **8/8** | **8/11** |

**Both cells built, both followed the build order, both scored full marks on the corpus that had defeated
every previous cell.** Round 22's best was 5/8 in 12 cycles; round 24's best is 8/8 in about 8.

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| Z1 | no cell fails like C — empty/unregistered graph | both built; `GraphExistsTest` survived in both | ✓ |
| Z2 | no cell ships D's failure — a decision node off a path it must serve | **both miss R6 re-release**: same class, milder | ✗ |
| Z3 | at least one cell ≥6/8 | both 8/8 | ✓ |
| Z4 | test counts recover to double figures | 9 and 12, versus 0 and 3 | ✓ |
| Z5 | build cycles stay high, 12–20 | **~8 and 10** | ✗ |

**Z5 failed in the direction I said I would not predict.** I deliberately forecast no cost saving to avoid
repeating an optimistic error, and the staged order was cheaper anyway — roughly a third fewer cycles
than round 22 with a better score.

## What step 3 caught, in both cells, before any logic existed

Both reported the *same* wiring bug, found by tracing shell nodes:

> **E:** *"PaymentTracker methods run for PAID events but didn't trigger downstream evaluators… made
> PaymentTracker a triggering parent for both AllocatableEval and CreditCheckEval."*
>
> **F:** *"PaidAmountState was initially marked `@NoTriggerReference` in ReleaseDecision, preventing
> release check after payment. Changed it to a trigger parent."*

That is round 23 cell D's exact failure — a decision node unreachable from a path that must reach it —
**caught in step 3 with empty nodes, in both cells, instead of surviving to the score.**

## The defect that survived, and my corpus missed it

Cell E scored 8/8 and then failed three probes it had never seen. R6 says an order that stops being
releasable and becomes releasable again must **emit again**. Neither cell re-fires: their allocatability
evaluators are triggered by orders and payments but **not by stock**, so a `RECEIPT` that restores stock
never re-evaluates.

**My eight probes did not cover the re-release clause** — the most complex sentence in the spec. Three
probes added (re-release via stock, via amend, and two interleaved orders). Both cells drop to 8–9/11.

So Z2 is properly scored as failed: the *severe* form of D's bug is gone, the mild form is not, and step 3
does not catch it because with shell nodes there is no notion of "becomes releasable again" to trace.

## What this establishes

1. **A failing test in the suite binds where a tool does not.** Round 23's `trace.sh` was skipped by the
   cell that most needed it. `GraphExistsTest` runs on every build and both cells kept it.
2. **Skeleton-first is cheaper *and* more correct**, which nothing else in this series has achieved —
   every previous intervention traded one for the other.
3. **Traces stopped displacing tests** once the procedure said what each is for: 4 traces and 9–12 tests,
   against round 23's 9 traces and 3 tests.
4. **Two changes moved at once** — the test and the build order — so this cannot attribute between them.
   The obvious next step is one cell with the build order and no `GraphExistsTest`.
