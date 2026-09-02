# The supplied components

Two components from different vendors. **You did not write them and you must not modify them.** Each is
a root class whose constructor builds its own internal subtree of instances. You integrate them by
constructing the roots and wiring them; what is inside is theirs.

**Pricing** (`com.vendor.pricing.PricingComponent`) — publishes a mid price and a spread-adjusted
price. Its constructor takes one source it needs from elsewhere.

**Risk** (`com.vendor.risk.RiskComponent`) — publishes a notional and a score. Its constructor takes two
sources it needs from elsewhere.

The two are mutually dependent: each needs something the other produces. **Neither can be evaluated as a
unit** — running one component's stages to completion before the other's will give some stage a value
from the previous tick rather than this one.

**Working out the correct order is your problem.** It is determined by which stage reads which, and
that is expressed in the constructors. Nothing in this document tells you the answer.

## The stage names

Your evaluation file must use the names the stages report through `name()`.
