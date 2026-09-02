# Round 20 — the annotation table does not earn its tokens

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `159e5a0` before launch. **4 of 4.**

Identical task, hold-out, oracle, model and template **code**. Only the README's annotation table varied.

| cell | table | doc tokens | `mvn` runs | **hold-out** | output | weighted |
|---|---|---|---|---|---|---|
| **T4** | **none** | **742** | **5** | 8/8 | 13,668 | **5.81** |
| T2 | 6 rows | 1,144 | 6 | 8/8 | 13,649 | 7.18 |
| T3b | **29 rows** | 1,820 | **7** | 8/8 | 26,438 | **11.23** |

**Correctness is flat at 8/8. Build cycles and cost rise monotonically with table size.**

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| X1 | T4 still scores 8/8 without any table | 8/8, in the fewest runs of any cell in the series | ✓ |
| X2 | T3 scores 8/8 but does not beat T2 on cycles | 8/8, and **worse** — 7 runs to T2's 6 | ✓ |
| X3 | both credit the code over the table | both did, explicitly | ✓ |
| X4 | the curve is flat above six rows | flat on correctness, **rising** on cost | ✓ |

> **T3b, with the complete table in front of it:** *"Both taught the same thing, but `ThresholdAlert`
> came FIRST and was concrete. The table confirmed what the example showed… `ThresholdAlert` was
> trusted first because it was a working, annotated implementation; the table validated it."*
>
> **T4, with no table at all:** *"Where learned: from example code in the template's `ThresholdAlert`
> node."* It applied `@NoTriggerReference` to all four data-only parents unprompted.

Of 29 rows, T3b used **three**. It named the rest as read-and-never-needed, and volunteered that it
would cut the seven lifecycle rows.

## The self-reported difficulty metric is noise, and I was citing it

| cell | `mvn` runs | self-reported framework share |
|---|---|---|
| T4 | 5 (best) | **65%** |
| T2 | 6 | **25%** |
| T3b | 7 (worst) | **75%** |

Non-monotonic, and inversely related to cost at the extremes. **It measures how the work felt, not what
it cost.** I quoted these percentages three times as evidence — including in `BOOTSTRAP.md`, where the
60→50→25 progression was presented as the headline result. That reading does not survive this round and
the document needs correcting.

## What T3b asked for instead of more rows

1. **A decision procedure**, not a reference: *"If a parent must trigger, make it a field (default). If
   a parent must NOT trigger, add `@NoTriggerReference`."*
2. **Show the bug, not the rule** — it named the README's worked cycle-6 example (`limitStore` ran,
   `thresholdAlert` did not) as what made the distinction *"visceral instead of abstract"*.
3. **Constructor injection** — that fields must match constructor parameters. Not an annotation, and
   it cost trial and error. This is the third cell to ask for something the table cannot carry.

## Conclusion

**Ship the code, cut the table to a decision rule.** Three points, n=1 each, but two independent
measures (build cycles and weighted cost) move together and correctness never moves at all. There is no
evidence the table helps and consistent evidence it costs.

The honest caveat: T3b's output tokens are nearly double the other two, which could be run variance
rather than the table. The `mvn` count — 5, 6, 7 — is the cleaner signal.
