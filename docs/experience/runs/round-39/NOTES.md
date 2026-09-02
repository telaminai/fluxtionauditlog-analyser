# Round 39 — the arms separate, and the losing defect is silent

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `68b9c1b` before either arm launched, with
an amendment at `6599e69` before the second launch (the first was killed and is not scored — see
there for why). Design: [`DESIGN.md`](DESIGN.md). Both arms: Haiku 4.5, fresh context, identical task
text, five jars, no source, decompiling forbidden.

## Result

| | Fluxtion | plain Java |
|---|---|---|
| **score** | **37/37** | **31/37** |
| `mvn` runs | 4 (0 failed) | 11 (1 failed) |
| consumer Java | **93 lines** | **367 lines** |
| configuration | 21 beans / 74 lines XML | 0 |
| wall-clock | 4m 30s | 10m 36s |
| output tokens | 13,113 | 21,289 |
| cache-read | 3.92M | 5.27M |
| **weighted** | **4.58M** | **6.34M** (1.38×) |

**Seven specs in, this is the first separation on correctness.**

## The defect that cost it, in its own words

> *"5. Stops when no more stages change value"*

It propagates on **value change** where the requirement is to propagate on **dirty**. Every one of the
six lost points is that single decision:

| event | what happened |
|---|---|
| `CONFIG volFactor=3.5`, before any tick | `vol = 0 × 3.5/100 = 0.0` — unchanged, so it stopped. `var`, `buffer` never ran |
| `CONFIG chargePct=0.25` | `charge = 0 × 0.25 = 0.0` — unchanged, stopped. `buffer` never ran |
| `RATE 0.8` | `spread = 0 × 0.8 = 0.0` — unchanged, stopped. `var`, `buffer` never ran |

**It builds green and its own 276 lines of tests pass.** The engine only skips stages when a
recomputed value coincides with its previous one, which is *data-dependent*: a scoring scenario whose
arithmetic avoided those coincidences would have scored it 37/37 and hidden the bug entirely. This is
the same failure class as the defect I hit building the subsystems — propagation without
recomputation — and both are silent.

That is the honest shape of the finding. Not "plain Java cannot do this": it got twelve stages,
twenty nodes, eight entry points and conditional propagation nearly right in 367 lines. It got one
semantic wrong, in a way nothing it could run would tell it about.

## What each arm reported doing

**Fluxtion** — *"Each compute stage's constructor parameters define its parents… The Fluxtion Maven
plugin reads this graph, generates a topologically-ordered processor, and handles all orchestration."*
It derived no order at all; it declared 20 beans. What it called hard was unrelated to composition:
*"Finding the audit log API took investigation."*

**Plain Java** — built the machinery round 38's vanilla priced at *"~200–300 LOC"* and declined to
write. It landed at 367. Its three "what was hard" items are the framework's problem statement almost
verbatim: dependency ordering within an event, a stage running twice, and *"distinguishing between
'won't change this event' versus 'hasn't been marked dirty yet but will be soon'"*. The last one is
the dirty-propagation semantic, identified correctly and then implemented as a value comparison.

## Two scorer defects were mine, and are recorded rather than quietly fixed

The raw run scored the plain-Java arm **21/37**. Six of those sixteen lost points were my own errors:

1. **C4 demanded the reference's exact sequence.** `mid` and `book` have no path between them, so
   either order is correct — round 38 had already recorded exactly this and I did not carry it into
   the scorer. C4 now checks topological validity against the published constructor graph
   (`deps.py`).
2. **My evidence spec for the plain-Java arm never pinned decimal places**, so it chose `%.1f`. The
   framework's audit log emits full precision natively and never had to choose — an asymmetry I
   introduced. Values are now compared at the precision each arm emitted, with `HALF_UP` to match
   Java rather than Python's banker's rounding.

**31/37 is the honest number.** Both fixes were applied to both arms; Fluxtion scores 37/37 either
way.

## Predictions scored: 5 of 6

| # | Predicted | Actual | |
|---|---|---|---|
| P1 | both arms reach a correct result | **no — 37 vs 31** | ✗ |
| P2 | vanilla writes a real topological sort, not a hard-coded list | yes, and said so | ✓ |
| P3 | vanilla's consumer code exceeds 150 LOC; Fluxtion writes none for the graph | **367 vs 0** | ✓ |
| P4 | vanilla costs ≥1.5× in weighted tokens | 1.38× — **short, and inside n=1 noise** | ✗ |
| P5 | the `false`-return path is where vanilla loses marks if it loses any | it lost them on propagation, not suppression — the `unrelatedKey` filter was handled correctly | ✗ |
| P6 | Fluxtion's `mvn` count is lower | 4 vs 11 | ✓ |

**P1 is the interesting miss, and it is the first optimistic one in this project's history.** Every
previous quantitative miss here was pessimistic about the model's baseline competence. This one
over-predicted it.

**P4 failed and I am not going to round it up.** 1.38× is below the ~2× threshold `PREDICTION.md`
set for n=1, so the cost difference is **not established** by this round. The score difference does
not depend on it.

## What is NOT shown

- **n=1.** One cell per arm. The 37-vs-31 gap is a single sample; the defect behind it is
  data-dependent, so a different scoring scenario could plausibly have produced a tie.
- **Fluxtion's build count is partly bought.** My task text told it *"`mvn compile` is not enough —
  the generator runs at `process-classes`"*, which is the trap that has silently cost builds in
  earlier rounds. The template README mentions two-pass compilation only in passing. That hint lands
  on the `mvn` count, not on the score.
- **Supplier cost is excluded, deliberately.** The two builds I spent fixing the subsystems (the
  simple-name collision `UP-FLX-27`, and the node that never triggers itself) are the supplier's,
  paid once and amortised over every consumer. This round measures *integration* cost. They are
  recorded in the artifact README and charged to neither arm.
- **The scoring scenario is mine**, and I chose arithmetic that starts from zero. That is what
  exposed the value-change bug. A defender of the plain-Java arm could fairly call that a scenario
  chosen to bite; the answer is that a real engine has to be right from a cold start too.

## What follows

Round 40 is built and tests the maintenance claim directly: pricing ships a **binary-compatible**
2.0 whose new `onConfig` method changes *which stages must run for an event*, with nothing for the
consumer to declare. An engine that re-derives from the jars picks it up; an engine holding a stored
event→handler map keeps building green and silently stops computing a stage that should now run —
the same failure class this round found, arriving through the front door of a routine upgrade.
