# Round 19 — showing the annotation fixed the bug it prevents

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `9572b5f` before launch. **4 of 4.**

Identical task, hold-out, oracle and model as round 18. The only change: the template now demonstrates
`@NoTriggerReference` in working code, with a mutation-checked test and a six-row annotation table.

| cell | context | template | `mvn` runs | **hold-out** | output | weighted |
|---|---|---|---|---|---|---|
| COLD (r18) | cold | plain | 8 (4 failed) | 6/8 | 15,809 | 12.13 |
| WARM (r18) | **warm from problem 1** | plain | 6 (3 failed) | 6/8 | — | — |
| **T2 (r19)** | **cold** | **annotation shown** | **6** (3 failed) | **8/8** | **13,649** | **7.18** |

**Against the cold baseline: same problem, 25% fewer build cycles, 41% lower weighted cost, and the
first perfect score on this task.**

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| W1 | 8/8 — the demonstrated fix removes the only bug both cells had | E3 trips at **[15, 16]** exactly; P4 and P7 both pass | ✓ |
| W2 | fewer `mvn` runs than COLD's 8; expect 5–6 | **6** | ✓ |
| W3 | a cold cell with the artefact beats the warm cell without it | 8/8 vs 6/8, same 6 runs | ✓ |
| W4 | the agent cites the annotation rather than reinventing the guard | named it, and named where it learned it | ✓ |

> *"`@NoTriggerReference` — learned from the README.md code comments which explained the distinction
> between triggering parents and data-only parents at the exact point it's needed."*

It applied it to **all four** data-only parents unprompted: `limitStore`, `serviceStore`,
`fleetRosterStore`, and `telemetryStore` on the alert gate.

## The number that answers the seeding question

Self-reported framework share of effort, same task throughout:

| cell | framework | ordinary Java |
|---|---|---|
| COLD, plain template | 60% | 40% |
| WARM, warm session, plain template | 50% | 50% |
| **T2, cold, annotation shown** | **25%** | **75%** |

**The artefact beat warm session context.** A worked example carrying one annotation halved the
framework's share of perceived difficulty relative to a warm session — and unlike session context, it
survives to the next cold start. That is the whole answer to "how do we seed this": put the semantics
in code the author has to look at anyway.

## The template's own defect, found by using it

> *"The template's `SensorState` returns false for unchanged readings, which is correct for threshold
> alerts but **wrong for overheat streak detection** — consecutive identical readings above a limit
> should still count."*

T2 lost 2 test runs to this. The template teaches "return false when nothing changed" as though it were
universal; it is a choice about what counts as activity, and a streak detector needs every reading. The
template should show the choice, not one side of it.

## What this establishes, across rounds 13–19

1. **Docs move cost; code moves correctness.** Four doc versions (2,955 → 12,443 tokens) left the score
   at 7/8. A 662-token template scored 8/8. Adding one demonstrated annotation took a different task
   from 6/8 to 8/8.
2. **The expensive failures are silent.** Every one — E3 over-firing, the materiality gate suppressing
   everything, a monolith narrating a graph it did not have, and this project's own template tests
   passing by reading the `TearDown` record.
3. **Warm context transfers mechanics, not domain reasoning** — and can carry a pattern past its
   validity boundary, which is how E3 broke.
4. **A worked example substitutes for session context, and outperforms it.**

## Next, and it is a real question

The complete annotation table is 29 rows, ~857 tokens, grouped by failure mode: 12 that decide what
runs (silent), 7 lifecycle (loud), 10 generation (diagnostic-covered). T2 succeeded with only the
six-row subset. **A follow-up cell with the complete table and nothing else changed measures whether
the other 23 rows earn their tokens or are inert weight.**
