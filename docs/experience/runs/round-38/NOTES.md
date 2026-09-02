# Round 38 — vanilla composes the same five jars, and finds the same source of truth

The fair version at last: five subsystems built and validated separately, shipped as jars with **no
source**, decompiling forbidden. Both arms given equivalent binaries.

| | Fluxtion | vanilla |
|---|---|---|
| values | correct | **correct** |
| order | correct | **correct** |
| the consumer wrote | 7 bean declarations, no Java | an `Engine` + `Main`, ~50 LOC of hard-coded order |
| `mvn` runs | 3 | 10 (no failures) |
| how the order was found | derived by the generator | **`javap` on the jars** |

Vanilla's output, verified by hand:

```
1,marketdata.mid=101.00|marketdata.depth=200.00|risk.notional=101000.00|pricing.adjusted=103.02|
  liquidity.score=10.30|risk.exposure=102040.50|capital.charge=8163.24
```

Identical values to the Fluxtion composite. The two orders differ only where the graph does not
constrain them — `notional` and `adjusted` are independent, so either relative position is correct.

## The finding: both arms use the same source of truth

> *"Constructor signature analysis via `javap` bytecode inspection… Constructor parameters are a
> definitive record of what each stage reads from its dependencies. **This is the source of truth that
> cannot be wrong.**"*

That is exactly what the generator reads. **A jar does not hide the dependency graph** — constructor
parameter types survive compilation, and `javap` prints them. My round-34 prediction that removing
source would separate the arms was wrong for a reason I had not considered: I removed the *bodies*,
which is what vanilla read last time, but the *signatures* carry the graph and they remain.

So the honest position after six specs: **binary delivery does not create the asymmetry either.**

## What vanilla says it did not do, and what it would cost

> *"**Hard-coded stage order.** The engine constructs stages in a fixed order rather than discovering
> them via reflection."*
>
> *"With a more complex dependency DAG… a proper topological-sort algorithm would be needed instead of
> the hard-coded order. Generalisation would require reflection-based instantiation, building an
> adjacency list, running topological sort, and error handling for cycles. **Current approach ~50 LOC.
> Generalised approach ~200–300 LOC.**"*

That is the shape of the difference, stated by the arm that does not have the framework. At seven
stages it wrote the answer down. It believes the general case is a 4–6× larger piece of infrastructure
— which is, precisely, a topological sort over a dependency graph extracted from constructors. **The
alternative to using the framework is writing the framework.**

Whether 200–300 LOC is a meaningful cost is a judgement, not a measurement. It is written once, and a
competent engineer would write it once.

## Still not measured

Every comparison so far is one composition, seven stages, one event type, one tick. Round 39 raises it
to **twelve stages, five subsystems and four event types each shared by two subsystems**, so the
dispatch has several entry points fanning into one interleaved graph rather than a single chain. That
is the first version where a hard-coded order has to be right in several places at once.
