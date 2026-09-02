# PREDICTION — round 32: the updated template, and where it stops

**Committed before launch.** Haiku 4.5 both cells. The template has gained, since these specs were last
attempted: the `@AfterTrigger` unwind demonstrated with a test, `EdgeDetector` and `Decisions`, the
nested-public-records pattern, and the governing principle — *fix the path or the node, never both*.

| cell | spec | last result on it |
|---|---|---|
| **GRAPH2** | round 30's order-dependent graph | Haiku: 8/8 decisions, **2/3 order** — lost O4 by never using `@AfterTrigger` |
| **CEIL3** | round 28's 24 rules / 28 event types | Haiku: **0/18**, twelve builds, none compiling, killed by one-public-class-per-file |

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **T1** | **GRAPH2 scores 3/3 on order.** The single reason it failed is now demonstrated in the template with a passing test. This is the narrowest claim. | medium-high |
| **T2** | **CEIL3 compiles.** The nested-records pattern removes the exact failure — 28 event types in one file instead of 28 files. | medium |
| **T3** | **CEIL3 scores between 8/18 and 14/18** — better than round 28's 8/18 because the helpers exist, short of complete because 24 rules is a lot of domain logic and nothing in the template writes that. | medium |
| **T4** | **CEIL3's remaining failures cluster in rules with two interacting quantities** — `OVERSOLD` (available vs on-hand), `PO_LATE` (a gap between two events) — not the flat lookups. Same as round 28. | medium |
| **T5** | **Neither cell reports the path/node confusion.** Both should locate failures as one or the other, because the principle is now stated. If a report still describes changing wiring and logic together, the prose has not worked. | medium-low |

## Falsifier

**If CEIL3 again fails to compile, the file-per-class problem is not what killed round 29** and my
diagnosis was wrong. **If it compiles and still scores ≤8/18, the ceiling is the model's capacity for
24 interdependent rules**, and no further template work will move it — which would end the harness line
of investigation and is worth knowing.
