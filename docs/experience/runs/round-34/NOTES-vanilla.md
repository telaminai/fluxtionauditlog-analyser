# Round 34, vanilla arm — it passes, by doing by hand what the framework derives

**3/3 values, 3/3 order**, without the giveaway. **P1 is dead**: I predicted Fluxtion would pass the
order check and vanilla would not.

## How it got the order — its own account, and P3 confirmed

> *"I determined the correct order by **reading the vendor source code**… Analysed `PricingComponent`
> source… analysed `RiskComponent` source… **built a dependency DAG**… computed a **topological sort**.
> Only one valid ordering satisfies the DAG."*

So it did precisely what Fluxtion's generator does — walk the dependency graph and topologically sort
it — except by hand, from source, once, and hard-coded the answer. P3 predicted exactly this and it is
confirmed.

It also solved the circular *construction* problem cleanly, unprompted: a `LazyStage` proxy passed to
Pricing and bound to `risk.notional` after Risk is built. That is genuinely the right shape and it found
it in one build, with no failures across six `mvn` runs.

## What it says the cost is, unprompted

> *"The real cost is **understanding the dependency graph from vendor code**."*
>
> *"If dependencies contain cycles: cannot be solved by evaluation order alone… **approach would
> fail**."*
>
> *"I **hard-coded** the evaluation order after reading the vendor code. A reflective approach…
> would be more general but harder to implement correctly."*

That is the honest shape of the result. At two components and four stages, reading the source and
sorting by hand is cheap and correct. The cost it names — understanding the graph from vendor code —
scales with the number of components and with how opaque they are, and the hard-coded order is a
constant that must be revisited whenever a vendor ships a new version.

## And my spec is still not testing what the owner described

**I shipped the vendor source.** The premise was *prebuilt components* — in reality a jar, with no
source to read. Vanilla's entire method was reading `evaluate()` bodies to see which stage calls which.
Remove the source and that method is unavailable; the alternatives it named are reflection over
bytecode, or a vendor-supplied manifest.

Fluxtion needs neither: the dependency graph is expressed in the constructors, which are present in the
compiled classes, and the generator walks them.

**So the sixth version of this experiment is: compile the components to a jar, delete the source, and
re-run.** That is the condition the owner actually described, and it is the one my spec has not yet
created.
