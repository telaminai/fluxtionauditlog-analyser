# PREDICTION — round 45, an effects-sensitive graph

**Written and committed BEFORE either arm is launched.** No result seen.

## Why round 44's fixture was not good enough

The owner read the plain arm's integration code — five constructor lines and fourteen hard-coded
`calc()` calls — and asked whether the problem was complex enough. It was not, and the reason is
specific:

- **`BreachCount` was a leaf.** Its `return false` arrested *nothing*. I built a detector and then
  gave it nothing to detect for.
- **It was the only stateful node** out of 25.
- **Every other node was `value = f(parents)`** — pure. Recompute-everything produced *identical*
  values.

So the fixture had exactly one point where running the whole graph was wrong, and it was a leaf
counter. A single fixed order sufficed because the problem really was one pipeline.

## What changed

| added | why running it unconditionally is now WRONG |
|---|---|
| `risk.LimitDetector` | arrests unless exposure > 250,000, **based on the value, not the event type** |
| `capital.Alert` | **side effect** — publishes to the desk and the regulator. Below the arrest. |
| `capital.AlertCount` | stateful, **two** levels below the arrest |
| `capital.BreachCount` | moved off `Exposure` to hang off the **detector** |
| `risk.Streak` | stateful, run-count sensitive, hangs off `Exposure` so it can see clean events and reset |
| `marketdata.Ewma` | stateful rolling average — an extra update skews it, a missed one lags it |

**Measured on the reference over 12 data events:** a `TRADE` runs **9** stages when exposure is under
the limit and **13** when it is over. The live subgraph changes event to event and cannot be predicted
from the event type. **3 alerts are published.** An engine that walks the whole graph unconditionally
publishes **12** — nine of them for breaches that did not happen.

## Two fixture defects of mine, fixed

1. **The pom.** Round 44's FX arm lost its first build to *"StrategyHandler not found during Spring
   generation"* — caused by my own earlier fix setting `generated.dependents` to `**/*.java`, which
   excluded *all* consumer source from pass 1 and so made a consumer class impossible to declare as a
   bean. Now scoped to `**/app/*.java`, and the toolkit note says so.
2. **The vendor did not publish its strategies.** Round 44's FX arm hand-wrote 26 lines of fee
   strategy because there was nothing to reuse. The capital library now publishes
   `FeeStrategies.byName(...)`. That is the subsystem being finished, not the arm being helped —
   **both arms get it.**

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| T1 | **The plain-Java arm publishes false alerts.** The arrest is data-dependent, so no fixed list of `calc()` calls can be right; it must test the detector's return value on every path. | medium-high |
| T2 | **If it avoids false alerts, it is because it threaded the detector's boolean through by hand** — and it will then be at risk on `Streak` or `Ewma` instead, which fail on run-*count* rather than on reachability. | medium |
| T3 | **Fluxtion scores full marks with zero failed builds.** Both causes of round 44's three failures are removed. | medium-high |
| T4 | **Fluxtion writes no Java that wires or dispatches anything** — 0 lines, as in round 44 — and now also writes no fee strategies. | high |
| T5 | Plain-Java integration code exceeds round 44's 35 lines, because reachability must now be computed rather than assumed. | medium-high |
| T6 | Neither arm's own tests catch its own defect, if it has one. | medium |

## The estimator warning, again

**I have under-predicted the plain-Java arm four rounds running.** T1 is held at medium-high, not
high, for that reason. It has twice built machinery I said it would skip, and in round 44 it derived
a correct dependency order unaided.

## Falsifiers

- **If the plain-Java arm publishes exactly 3 alerts and gets every count right**, then even an
  effects-sensitive, data-dependent-arrest graph does not separate the arms, and I will say so
  plainly. That would be the strongest negative result in the series.
- **If Fluxtion fails a build**, T3 is wrong and my subsystems are still not finished.

## Not measured

n=1. Cost is recorded but **not led with** — round 41 and round 44 both had the losing arm cheaper,
because being wrong is cheap. Performance is a separate run, deliberately not mixed in.
