# The supplied components

Two components from different vendors. **You did not write them and you must not modify them.** Each is
a root class whose constructor builds its own internal subtree of instances. You integrate them by
constructing the roots and wiring them; what is inside is theirs.

**Pricing** (`com.vendor.pricing.PricingComponent`) — internally: a mid-price stage, then a
spread-adjusted stage.

**Risk** (`com.vendor.risk.RiskComponent`) — internally: a notional stage, then a score stage.

They are mutually dependent **at their internal stages, not at their boundaries**:

```
tick ──▶ pricing.mid ──▶ risk.notional ──▶ pricing.adjusted ──▶ risk.score
```

`risk.notional` reads `pricing.mid`. `pricing.adjusted` reads `risk.notional`. `risk.score` reads
`pricing.adjusted`.

**So neither component can be run as a unit.** Running all of Pricing then all of Risk gives
`risk.notional` a stale mid or `pricing.adjusted` a stale notional; running Risk first is worse. The
correct order alternates between the two components' internals, and you do not get to see inside them
to work that out by reading.
