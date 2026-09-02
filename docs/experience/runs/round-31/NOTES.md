# Round 31 — I built it myself, and the harness caught me not using it

The owner's suggestion: build the round-30 spec with the strongest model and richest context, see where
the effort and the failures actually are, then fold that back into the template.

| engine | model | decisions | O1 | O2 | O4 | `mvn` runs |
|---|---|---|---|---|---|---|
| vanilla | Haiku 4.5 | 8/8 | ✓ | ✓ | ✓ | 4 |
| Fluxtion | Haiku 4.5 | 8/8 | ✓ | ✓ | ✗ never committed | 3 |
| **Fluxtion** | **Opus 5, full session context** | **8/8** | ✓ | ✓ | **✓** | **~9** |

## The first attempt failed, and it failed the way the failing cells fail

I went at it freeform: five chain nodes, two store files and three deciders written in one pass, then
regex surgery to restructure. Three consecutive failed builds on brace balancing and package
qualification. The owner asked whether I was following the harness or attacking the spec free-form.
I was attacking it free-form. **The author of the build order did not follow the build order**, and
paid for it exactly as rounds 22–23's cells did.

Restarted from step 1. The difference was immediate.

## Step 3 found the halt defect with no logic written at all

Thirteen shell nodes, every method a one-liner returning `true`. One trace:

```
  6  HALT           ['haltStore']
  7  TRADE          ['position', 'mark', 'base', 'exposure', 'concentration', 'util', 'riskDecider']
```

Event 7 is a `TRADE` for a **halted** book and the entire chain runs — O3 violated, visible in one line,
before a single rule existed. After implementing the gate as `position` returning false:

```
  7  TRADE          ['position']
```

**That is the argument for the build order in one screen.** The same defect buried in 600 lines of rule
code is what cost rounds 23 and 24 their scores.

## What the framework gave, and what it did not

Free, and I wrote no code for either: **O1** (each node evaluates once per event however many parents
moved) and **O2** (parents settled before children, so the diamond reports a mark and a utilisation from
the same generation). The vanilla engine spent **40% of its effort** constructing exactly these.

**O4** was free once I used `@AfterTrigger` — reverse topological order is derived, and the generated
dispatch guards each call with `if (isDirty_x)` under a comment reading *"event stack unwind
callbacks"*, so only nodes on the path commit. The round-30 Haiku cell lost the round purely by not
reaching for it, and the template had no example to copy. It does now.

**Not free:** O3 halting (no framework concept — I implemented the gate), and O5 unchanged-reference-data
(each store compares and returns false).

## Two defects of mine worth folding into the template

1. **Commit identity must match evaluation identity.** `PositionNode` evaluated as `position(BK:AAA)`
   and committed as `position`. The behaviour was right; the record was unmatchable. A node keyed by
   subject must commit under the same key.
2. **The one-public-class-per-file rule is what killed round 29**, and the fix is nested public records
   in a single holder — `Events.Trade`, `Events.Price` — which the generator accepts. Round 29 spent
   twelve builds splitting 28 event types into 28 files and never compiled.

## And a third oracle correction

O4 has now been wrong three ways: *exact reverse of every evaluation* (fails when a node returns false
and does not commit), *globally descending depth* (fails when two independent books interleave), and
*exact reverse of the committing subset* (fails when a multi-key node commits its keys in forward
order). The correct statement is the **mirror of O2**: within a subject, event-in goes shallow to deep
and the unwind goes deep to shallow; independent subjects may interleave in either phase.

Re-validated four ways — vanilla's book-by-book unwind passes, my per-node unwind passes, an engine
emitting no commits fails, a glitchy log fails.

## The honest summary

With the strongest model, full context, and the harness followed properly, Fluxtion reaches **8/8 and
3/3** — matching vanilla-with-Haiku exactly. The framework demonstrably supplies O1, O2 and O4, and
vanilla demonstrably pays for them. **On this spec that cost still does not buy a better outcome; it
buys the same outcome for less of the author's attention**, which is a real claim but a different one
from the one this project set out to test.
