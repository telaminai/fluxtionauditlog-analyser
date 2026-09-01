# PREDICTION — the docs delta run (resources now live)

**Written before either loop was re-run.** Baseline:
[`ASSESSMENT-diagnostics-2026-09-01.md`](ASSESSMENT-diagnostics-2026-09-01.md).
**Changed variable:** the agreed resource set now carries the new material (fluxtion-web PR #1, merged
`a4a124f`, Cloudflare `5bf137a4`, verified live). **Also changed since baseline:** builder 1.0.66, which
flips the emission default to PARALLEL. That is a confound and is called out in Q2.

## What this run CANNOT measure, stated first

**I wrote the documents this afternoon.** A re-run cannot measure whether the docs *teach*, because I
already know the answers. If I reach for `@NoTriggerReference` it is memory, not the page.

So the behavioural question — *would an author who reads these avoid the error?* — is **not answerable by
me** and I make no prediction about it. It needs a session that has not read this one.

**What is honestly measurable, and is what these predictions are about:**

1. whether the live resources **cover** each planted error,
2. whether they say the **right** thing (checkable against the compiler),
3. whether the documented fix, applied, **produces a correct graph** (checkable in generated dispatch),
4. whether a fix is **reachable from the symptom** an author actually experiences.

That is a coverage-and-correctness audit of the documents. It is worth doing and it is not the same claim
as "the docs helped".

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| Q1 | **loop-bench is unchanged: 23/23, zero diagnostic signal.** Nothing in this change touches it. | very high |
| Q2 | **The compiler behaviour is identical: 3 builds to green, 2 distinct codes** (FLX-1008 WARN, FLX-1009 ERROR on two fields). Docs cannot change diagnostics. *Confound:* builder 1.0.66 also landed; if the count moves, suspect the builder, not the docs. | high |
| Q3 | **Coverage rises from 3/5 to 5/5.** At baseline the live set documented the three loud errors and neither idiom error. All five now have a documented fix. Checkable by fetching the live pages. | high |
| Q4 | **The FLX-1009 guidance now distinguishes derived state from configuration.** At baseline the canon said the trigger was renderability and that primitives were safe — which the run falsified. Checkable in the live text. | high |
| Q5 | **The two idiom fixes are NOT reachable by symptom search.** Their symptom is *absence* — nothing fails — so no error string leads to them. An author reaches them only by reading a section they have no prompt to open. | medium-high |
| Q6 | **Applying the documented fix produces a correct graph:** with `@NoTriggerReference` on the lookup, `skuCatalog` disappears from `guardCheck_reorderPolicy()`. Verifiable in generated dispatch. | high |

## The prediction I most want falsified

> **Q5.** If a symptom-driven search *can* reach the idiom sections, the documents are doing more than I
> think and the "silent failures need prose" argument gets stronger. If it cannot — which is what I
> expect — then **prose alone is not enough for this class**, and the honest follow-up is upstream work:
> a build warning for a data-only reference that became a trigger, which is
> [UP-FLX](../../proposals/upstream-asks.md) territory and was already the other author's top ask.

That would make the real conclusion of this whole exercise: *docs close the coverage gap, and only a
diagnostic closes the discovery gap.*

## Scoring

`ASSESSMENT-docs-delta-2026-09-01.md`, ✓/✗ per prediction, evidence inline, and an explicit statement of
what remains unmeasured.
