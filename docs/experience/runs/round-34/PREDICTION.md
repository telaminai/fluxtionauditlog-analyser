# PREDICTION — round 34: prebuilt components, and the order that cannot be read

**Committed before launch.** The owner's hypothesis, and the first spec in this project with an
asymmetry my earlier four all lacked: **the author does not know the components' internal structure.**

## Why the previous four specs could not separate the arms

In every one, the author wrote every node. So the dependency graph was fully known to them, and a
hand-ordered `if/else` chain was achievable — vanilla just wrote the order down. Four specs, four ties
or losses, and a consistent explanation: *whenever recompute-in-a-fixed-order is correct and
affordable, the approaches converge.*

## What is different here

Two vendor components, each a root class whose constructor builds its own internal subtree. **Neither
may be modified.** They are mutually dependent **at their internal stages, not at their boundaries**:

```
tick ──▶ pricing.mid ──▶ risk.notional ──▶ pricing.adjusted ──▶ risk.score
```

So **neither component can be run as a unit.** Pricing-then-Risk gives `risk.notional` a stale mid or
`pricing.adjusted` a stale notional; Risk-then-Pricing is worse. The only correct order alternates
between the two components' internals — and the author cannot read it off, because the structure is
inside classes they did not write.

Fluxtion is handed the two roots and walks the constructor graph. Vanilla is handed the two roots and
must discover the same thing some other way.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **P1** | **Fluxtion passes the order check; vanilla does not.** This is the whole hypothesis, and the first time I have predicted a clean separation. | medium |
| **P2** | **Vanilla treats each component as a unit** — evaluating Pricing fully then Risk fully, or the reverse — and produces stale values on the first tick. | medium |
| **P3** | **If vanilla gets it right, it does so by reflection or by hand-reading the vendor source**, and says so. Either is a real cost the Fluxtion arm does not pay. | medium |
| **P4** | **Fluxtion needs no explicit ordering work at all** — it declares the two roots and the generator derives the interleaving. | medium-high |
| **P5** | **Values and order fail together in whichever arm fails.** A stale read is exactly what wrong order produces here, so a correct-values/wrong-order split would mean my oracle is not measuring what I think. | medium |

## Falsifier

**If vanilla produces the interleaved order without reflection and without reading vendor internals,
then component composition does not separate them either** — and after five specs I would conclude the
framework's advantage is not reachable by any correctness test at this scale, and say so plainly.
