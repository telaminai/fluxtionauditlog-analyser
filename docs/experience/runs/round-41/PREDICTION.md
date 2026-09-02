# PREDICTION — round 41, the upgrade that announces only half of itself

**Written and committed BEFORE either arm is launched.** No result seen.

Round 40 failed to test what it set out to test: my release note named `spreadMult`, so the change
was never invisible and both arms wired it correctly. This round removes exactly that one sentence
and changes nothing else.

## The single variable

| | round 40 | round 41 |
|---|---|---|
| jars | pricing 2.0 | **identical** |
| starting point | each arm's round-39 solution | **identical** (fresh copies — round 40's arms know about the multiplier and are contaminated) |
| scoring scenario | exercises `spreadMult` twice | **identical** |
| release note | announced `Skew` **and** the multiplier | **announces `Skew` only** |

Nothing else differs. This is as close to a one-variable run as this series has managed.

## Why this is a fair thing to score

The multiplier is **unannounced, not hidden**. `javap` on the new jar shows
`public boolean onConfig(com.vendor.Events$Config)` on `PxRate` in plain sight. And the original
`TASK.md` requirement has been in force since round 39:

> *"Every class that should run for an event runs exactly once for that event, and no class that
> should not run runs at all."*

`PxRate` now handles `CONFIG`. An engine that satisfies that sentence routes the event to it. So this
does not score an arm on information it was denied — it scores whether its design makes the
information **necessary to look for**.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| R1 | **Fluxtion picks up the multiplier with no consumer action, exactly as in round 40** — the generator re-reads the jar, sees the new handler, and routes `CONFIG` to pricing. Nobody needs to notice. | high |
| R2 | **The plain-Java arm misses it.** Its round-39 `handleConfig` routes `CONFIG` to marketdata's and capital's adapters from a list it wrote once. `CONFIG,spreadMult` will produce nothing, `pricing.spread` will never recompute, and it will build green with all tests passing. | medium-high |
| R3 | **If it does catch it, it will be because it re-ran `javap` over the whole jar rather than only over `Skew`** — and it will say so. That is a habit, not a property of the design, and I will report it as such. | medium |
| R4 | Fluxtion's change stays ≤5 lines; the plain-Java arm's is ~15–20, as in round 40. | medium-high |
| R5 | **Neither arm's build fails and neither arm's own tests fail.** That is the point: the failure, if it comes, is silent in both senses — no build error and no test error. | high |

## The falsifier

**If the plain-Java arm catches the multiplier unprompted, R2 is wrong and the silent-failure
argument is substantially weaker than two rounds have suggested.** I will say so plainly. Round 40
already falsified my prediction in this same direction once, so this is not a hypothetical.

## Carried-over failures, again

The plain-Java arm still starts from its 31/37 round-39 base and its propagate-on-value-change defect
will recur on the same events. Scoring separates **carried-over** from **new**, and only the
`spreadMult` behaviour is evidence about this round's question.

## What this still cannot show

n=1. And one arm being told nothing is a fixture I built; a real supplier's note is incomplete for
reasons of its own, not because I removed a sentence to make a point. What generalises is the
mechanism — stored dispatch versus re-derived dispatch — not the frequency with which suppliers
under-document.
