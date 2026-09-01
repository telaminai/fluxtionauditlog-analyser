# ASSESSMENT — the docs delta run, scored

**Prediction:** [`PREDICTION-docs-delta-2026-09-01.md`](PREDICTION-docs-delta-2026-09-01.md), committed at
`44fe75e` before re-running. **Baseline:**
[`ASSESSMENT-diagnostics-2026-09-01.md`](ASSESSMENT-diagnostics-2026-09-01.md).

**Score: 6 of 6.** No prediction was made about the behavioural question, for the reason below.

## What this did not measure, restated because it is the important caveat

**I wrote the documents this afternoon.** This run cannot say whether they *teach*. Every fix I applied I
already knew. The behavioural question — *would an author who reads these avoid the error?* — remains
open and needs a session that has not read this one.

What follows is a coverage-and-correctness audit of the documents, which is worth having and is a
narrower claim.

## Loop 1 — unchanged, as predicted

| # | Prediction | Result |
|---|---|---|
| Q1 | loop-bench unchanged: 23/23, no diagnostic signal | ✓ **23 passed, 0 failed** |

## Loop 2 — the compiler is unmoved; the documents are what changed

| # | Prediction | Result |
|---|---|---|
| Q2 | identical compiler behaviour: 3 builds to green, 2 distinct codes | ✓ **exact** — build 1 `ReorderPolicy`, build 2 `SkuCatalog`, build 3 green; FLX-1009 ×2 and FLX-1008 throughout |
| Q3 | coverage rises 3/5 → 5/5 | ✓ all five planted errors now documented in the live set |
| Q4 | FLX-1009 guidance distinguishes derived state from configuration | ✓ *"the rule is finality"* ×2, *"derived local state"* present |
| Q5 | the two idiom fixes are unreachable by symptom search | ✓ **held** — see below |
| Q6 | the documented fix produces a correct graph | ✓ **verified in generated dispatch** |

### Q2 — the confound did not bite

Builder 1.0.66 landed between the runs and flips the emission default, so a change in the diagnostic
count would have been ambiguous. It did not change: same three builds, same sequence, same two codes.
The compiler behaves identically and the only variable that moved is the prose.

### Q6 — the documented fixes work, measured not asserted

`@NoTriggerReference` on the data-only lookup:

```
baseline  guardCheck_reorderPolicy() { return isDirty_skuCatalog | isDirty_stockLedger; }
after     guardCheck_reorderPolicy() { return isDirty_stockLedger; }
```

One `@OnTrigger` instead of three: baseline emitted three methods **in a single dispatch**, each
overwriting `isDirty_revenueLedger` so only the last return propagated. After, it is one
`revenueLedger.recompute()` per dispatch path. Both repairs are exactly what the pages instruct, and both
produce the graph the author described.

### Q5 — held, and it is the finding that matters

The loud errors carry a **searchable symptom**: an author copies `cannot find matching constructor` or
`use @AssignToField` out of a build failure and lands on the fix. Both strings are in the live pages.

The idiom errors have **no such entry point**, because their symptom is absence. The golden path row now
literally reads *"nothing — it compiles and runs"*, and that phrase is in the text — but nobody searches
for a string describing the absence of a problem they have not noticed. It is reachable by **reading the
section**, and only by that.

> **So: the documents closed the coverage gap and did not close the discovery gap.** An author who reads
> the orientation before starting is now protected. An author who reads it when something breaks is not,
> because nothing breaks.

## An unplanned corroboration

Applying the idiom fixes broke the build — the committed generated processor still referenced the three
method names I had just removed:

```
WarehouseProcessor.java:[333,44] cannot find symbol
```

That is the stale-generated-source problem the new golden-path section §4b is about, encountered without
looking for it, the first time a node's shape changed. It is also the strongest argument that §4b belongs
in prose rather than in a diagnostic: nothing is wrong at either end, and the cost is only visible as a
count of how many times you pay it.

## What follows from this

**The honest conclusion of the whole exercise, across both runs:**

| gap | closed by | evidence |
|---|---|---|
| structural rejection | **the diagnostic** | 3 failures, 3 one-step repairs, no other document consulted |
| knowing the fix exists at all | **the docs** | coverage 3/5 → 5/5 |
| **discovering you have the problem** | **neither, yet** | both idiom errors still produce a green build and nothing prompts a lookup |

The remaining gap is upstream and was already the other author's top ask: **a build warning when a
reference the author never triggers on becomes a trigger parent.** The doc now tells you the fix; only a
diagnostic can tell you that you need it. That is the same D-AX1 discriminator the docs were selected
by, pointing one step further on.

**Still unmeasured:** whether a fresh author, reading these, avoids the errors. The protocol is fixed —
same five plants, same baseline — and needs an uncontaminated session.
