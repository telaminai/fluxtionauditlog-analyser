# Round 33 — the maintenance claim: both arms perfect

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `c2e1bd1`. **1 of 5.**

The owner's contention, tested directly: *a fresh model primed with the Fluxtion template beats vanilla
on a maintenance change, even though greenfield has tied or lost three times.*

Both 12/12 engines from earlier rounds, handed to fresh Haiku agents with no memory of building them,
same change request, scored on 16 probes — the 12 they already passed, unchanged, plus 4 new.

| arm | regression (12) | new (4) | **total** | files | lines added | `mvn` | tests | weighted |
|---|---|---|---|---|---|---|---|---|
| **Fluxtion** | 12/12 | 4/4 | **16/16** | 28 → **31** | **+137** | **3** | 9 | 6.30 |
| **vanilla** | 12/12 | 4/4 | **16/16** | 5 → **5** | **+98** | 4 | 22 | **4.69** |

**Both perfect. Zero regression on either side.**

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| M1 | Fluxtion regresses less | **neither regressed at all** | ✗ |
| M2 | vanilla touches more files and lines | **vanilla touched fewer of both** — 0 new files and +98 lines against 3 new files and +137 | ✗ |
| M3 | vanilla names the dispatch chain as the hard part | it said *"Order of evaluation: UNCHANGED"* | ✗ |
| M4 | Fluxtion needs fewer build cycles | 3 vs 4 | ✓ |
| M5 | neither reaches 16/16 | **both did** | ✗ |

## Why the change was easy for both, and it is my spec's fault again

The discount touches **one** existing rule — credit-ok — and vanilla found it by reading:

> *"The dispatch change was identified by reading R5's implementation in `isCreditOk()` — it was the
> only rule that uses order value, so that's where the discount multiplier had to be applied."*

**One call site. Order of evaluation unchanged.** I designed this to be mid-chain, and it is
mid-*rule* but not mid-*graph*: nothing downstream of credit-ok reads a derived value that discounts
alter, so no re-ordering was required of either arm.

The change that would have tested the claim is one where the new input feeds a value that **several
derived levels** consume — so a hand-ordered engine must work out where in its `if/else` chain to
recompute, and in what order. This change had no such requirement, so the framework's ordering had
nothing to do.

## What each arm actually did

**Fluxtion** added a `DiscountStore` bean and a `DiscountAbuseDecider` bean, declared `discountStore` as
a `@NoTriggerReference` parent of `ReleaseDecider`, and let the generator re-derive dispatch:

> *"The dispatch order is automatically computed by the framework."*

**Vanilla** added a map, a getter, a multiplication inside `isCreditOk()`, and a
`checkDiscountAbuseAndReleaseForCustomer()` loop hung off the DISCOUNT branch of its event chain — and
was explicit that ordering did not change.

**Vanilla wrote fewer lines and more tests** (22 against 9) and cost less. Fluxtion needed one fewer
build cycle. Neither difference is large enough to matter at n=1.

## Standing after four specs

| spec | shape | result |
|---|---|---|
| round 21 | 3 detectors | tie on correctness, vanilla cheaper |
| round 27 | 12 rules | tie at 12/12 |
| round 30 | order-dependent graph | vanilla 3/3, Fluxtion 2/3 |
| **round 33** | **maintenance change** | **tie at 16/16** |

**Four specs, no separation on correctness.** The maintenance hypothesis is not refuted — it was not
tested, because the change I designed did not require re-ordering. That is the fourth time I have built
a task that could not distinguish the arms, and the pattern is consistent: *whenever recompute-in-a-
fixed-order is both correct and affordable, the two approaches converge.*

A change that would test it: one whose new input feeds a value **two or more derived levels** below,
where a fresh author must work out the propagation path in a chain they did not write. That is a
different experiment and worth running before the claim is judged either way.
