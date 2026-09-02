# Round 34 — both arms pass, and the experiment is invalid on both sides

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `1e8159e`. **1 of 5.**

| arm | values | order | how it got the order |
|---|---|---|---|
| vanilla | 3/3 | 3/3 | read the vendor source, built a DAG by hand, topologically sorted it |
| Fluxtion | 3/3 | 3/3 | **reflection to patch a private field**, then the generator derived it |

**P1 dead** — I predicted a clean separation and got a tie. **P3 confirmed** — vanilla read the source
and said so. Vendor sources unmodified in both arms.

## Defect one: I wrote the answer into the task

The first vanilla run scored 3/3 and told me why: *"the evaluation order was explicitly given in
`COMPONENTS.md`."* I had drawn `tick ▶ pricing.mid ▶ risk.notional ▶ pricing.adjusted ▶ risk.score` in
the file whose premise was that the order could not be read off. Removed; vanilla re-run and still
passed, this time by reading the vendor source. That run stands.

## Defect two: my components defeat the mechanism I was testing

I typed the cross-component constructor parameters as `Object` and had the vendors read each other
through a reflective `Bridge`, to make them opaque. **`Object` fields are not node references**, so
Fluxtion could not see the cross-component dependency at all, and the agent had to reach for
`setAccessible()` to patch a private final field before the generator could derive anything:

> *"Using `setAccessible()` to update a private final field is fragile and violates encapsulation. A
> cleaner approach would require vendor component source changes (adding `@AssignToField`), which I
> cannot do."*

It is right, and the fault is mine. A component built for this framework exposes **typed** node
references, and the graph is then derived from the constructors with no reflection. **I made the
components opaque in the one way that disables the property under test**, then measured whether the
property held.

## Defect three: I shipped the source

The premise is *prebuilt* components — a jar. Vanilla's entire method was reading `evaluate()` bodies.
Remove the source and that method is gone; its own alternatives are bytecode reflection or a
vendor-supplied manifest. Fluxtion needs neither, because constructor references survive compilation —
but only if they are typed, which brings it back to defect two.

## What the run is still worth

Vanilla's unprompted account of its own cost:

> *"The real cost is **understanding the dependency graph from vendor code**."*
> *"I **hard-coded** the evaluation order after reading the vendor code."*
> *"If dependencies contain cycles… **approach would fail**."*

And both arms independently reached the same shape for the circular *construction* problem — a mutable
placeholder passed to the first component and bound after the second exists. Neither needed telling.

## The valid version of this experiment

1. **Typed node references** in the vendor constructors, not `Object`, so the graph is expressed in the
   way the framework reads.
2. **Compiled to a jar, source removed**, so vanilla cannot read `evaluate()` bodies.
3. **More than two components**, since vanilla's method is a one-off manual sort whose cost is claimed
   to scale.

Only the third of those is about scale; the first two are corrections to defects I introduced. **Five
of six specs in this project have now failed to measure what they were built to measure**, and the
pattern in every case is the same: I left the answer reachable by a route I had not thought about.
