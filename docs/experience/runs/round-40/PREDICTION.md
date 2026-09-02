# PREDICTION — round 40, a compatible upgrade from one supplier

**Written and committed BEFORE either arm is launched.** No result seen.

Round 39 measured the cost of **integrating** five prebuilt subsystems. This measures the cost of
**living with them**. Each arm starts from **its own round-39 solution, unmodified**, and is handed
`pricing-2.0.jar` plus the supplier's release note.

## The upgrade, and why its two halves differ

Verified binary compatible before launch — `javap` diff shows nothing removed from `PxRate`,
`Spread` or `Adjusted`, only additions.

**Change A — additive and invisible.** `PxRate` gains one method, `onConfig`, reacting to key
`spreadMult`; `Spread` keeps its exact constructor and now applies the multiplier. **There is nothing
for the consumer to declare and no signature to update.** But `CONFIG` events must now reach pricing,
and in 1.0 they never did. Measured on the reference: `CONFIG,spreadMult` produces
`pricing.multIn, pricing.spread` in 2.0 and produced **nothing at all** in 1.0.

**Change B — additive and visible.** A new published stage `Skew(Vol, Adjusted)` — a cross-vendor
dependency on marketdata. Adopting it requires an explicit declaration, so nobody can miss it.

A is the experiment. B is there so both arms have something they cannot overlook, which keeps A from
being the only thing either arm is looking for.

## The confound, named before the numbers exist

**The plain-Java arm starts from a broken base.** It scored 31/37 in round 39, losing six points to
propagating on value-change rather than on dirty. Those failures will recur here and are **not** a
round-40 result. Scoring therefore separates:

- **carried-over** — a failure whose cause is the round-39 defect
- **new** — a failure introduced by, or not handled by, the upgrade

Only the second is evidence about maintenance cost. The alternative was to repair the arm's code
myself before handing it over, which is a larger intervention than reporting the split honestly.

The Fluxtion arm starts from 37/37, so it has no carried-over failures. **That asymmetry favours it
and is not a finding.**

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| Q1 | **Fluxtion's upgrade is one bean line plus a rebuild.** ≤5 lines changed, no Java touched. | high |
| Q2 | **Fluxtion handles change A with no consumer action at all** — the new `CONFIG` routing appears because the graph is re-derived from the jar. | high |
| Q3 | **The plain-Java arm misses change A**, or catches it only by re-running its discovery over the new jar. If its event→handler map is built once from a hard-coded list, `CONFIG,spreadMult` silently produces nothing. | medium-high |
| Q4 | **The plain-Java arm changes ≥20 lines**, against Fluxtion's ≤5. | medium |
| Q5 | **Neither arm's build fails on the drop-in itself** — binary compatibility holds, so any failure is semantic, not structural. | high |
| Q6 | Cost gap widens versus round 39's 1.38×, because one arm re-derives and the other re-reasons. | low-medium |

## Falsifiers

- **If the plain-Java arm handles change A cleanly**, Q3 is wrong and the maintenance claim is much
  weaker than round 39 suggested — most likely because it built genuinely general reflection-driven
  discovery, which would be the more interesting finding of the two.
- **If Fluxtion needs Java changes**, Q1 is wrong.
- **If either arm fails to build**, Q5 is wrong and my compatibility check was inadequate.

## What this round cannot show

n=1 again. And round 39's cost gap (1.38×) was **inside** the noise threshold this series set for
n=1, so Q6 is a direction to watch, not a number to quote.
