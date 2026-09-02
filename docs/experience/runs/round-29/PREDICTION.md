# PREDICTION — round 29: does a shipped edge detector move the ceiling?

**Committed before launch.** Identical spec, probes, model and harness as round 28. One change: the
template now ships `EdgeDetector` and `Decisions`, both demonstrated in the worked example and covered
by a mutation-checked test.

## Why

Round 28 scored **8/18** and stopped, unfinished, with a precise diagnosis in its own words:

> *"Tracking state transitions for EDGE rules — knowing 'it just became true' versus 'it's been true'
> requires storing previous state. The framework doesn't provide this automatically; **I need custom
> tracking in each node**."*

Every probe it passed was a single-event lookup; every one it failed needed state carried across
events. Vanilla independently reported the same work as **20% of its effort** at half the size. With 14
EDGE rules it is the dominant repeated cost.

`EdgeDetector.roseFor(key, nowTrue)` is that state, written once. `Decisions.add(...)` is a list rather
than a field, which is the other structural failure — three engines dropped all but one decision when
an event produced several.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **Z1** | **Score above round 28's 8/18.** The narrowest claim: the missing piece is now provided. | medium-high |
| **Z2** | **At least 13/18.** The failures that remain will be the compound gates — quarantine and embargo on release — rather than the transition mechanics. | medium |
| **Z3** | **Probe 05 passes** (several decisions of one kind in one event). `Decisions` is a list in the template and the example drains it. Three engines have failed this; a shipped shape is the fourth attempt at fixing it and the first that is code rather than prose. | medium |
| **Z4** | **The engine finishes rather than stopping.** Round 28 ran out of room writing per-rule transition tracking fourteen times; not writing it should buy that room back. | medium |
| **Z5** | **The report cites `EdgeDetector` by name.** If it scores well without using it, the gain is not from this change and Z1 is confounded. | medium-high |

## Falsifiers

- **If it scores 8/18 or below, a shipped helper does not fix a per-rule cost**, and the ceiling is the
  model's capacity for a 24-rule spec rather than any repeated mechanical work. That would end the
  harness line of investigation at this size.
- **If it finishes but scores below 13/18 with `EdgeDetector` used throughout**, the transition
  tracking was not the binding constraint and Y3 from round 28 was the wrong diagnosis.

## Note

Round 27's process miss is not repeated: this is committed before the agent is launched.
