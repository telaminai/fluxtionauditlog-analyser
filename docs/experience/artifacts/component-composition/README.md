# Five subsystems, plugged together

**This is the composition artifact.** Five subsystems from five different vendors, each built and
validated on its own, published as jars with no source, integrated by one bean file.

*(The sibling `spring-fluxtion/` artifact is a different thing: a single 12-rule engine written by one
author, all in one package. Useful for what a Fluxtion project looks like — not an example of
composition.)*

## What the consumer received

```
consumer/lib/
  marketdata.jar   liquidity.jar   pricing.jar   risk.jar   capital.jar
```

**8 classes, 0 `.java` files.** No source. The vendors' internals are not readable.

`subsystems/` holds the sources for reference only — the consumer did not get them.

## What the consumer wrote

The whole of it, in [`consumer/src/main/fluxtion/designer/application-context.xml`](consumer/src/main/fluxtion/designer/application-context.xml):

```xml
<bean id="mid"      class="com.vendor.marketdata.Mid"/>
<bean id="depth"    class="com.vendor.marketdata.Depth"/>
<bean id="notional" class="com.vendor.risk.Notional"><constructor-arg ref="mid"/></bean>
<bean id="adjusted" class="com.vendor.pricing.Adjusted">
    <constructor-arg ref="mid"/><constructor-arg ref="depth"/></bean>
<bean id="score"    class="com.vendor.liquidity.Score"><constructor-arg ref="adjusted"/></bean>
<bean id="exposure" class="com.vendor.risk.Exposure">
    <constructor-arg ref="notional"/><constructor-arg ref="score"/></bean>
<bean id="charge"   class="com.vendor.capital.Charge"><constructor-arg ref="exposure"/></bean>
```

**Seven beans. No Java. No declared events. No declared order.** Only which subsystem supplies each
input.

## What the toolchain derived

[`generated/AppProcessor.java`](generated/AppProcessor.java) — nobody wrote this:

| # | stage | subsystem |
|---|---|---|
| 1 | `depth` | marketdata |
| 2 | `mid` | marketdata |
| 3 | `adjusted` | pricing |
| 4 | **`notional`** | **risk** |
| 5 | `score` | liquidity |
| 6 | **`exposure`** | **risk** |
| 7 | `charge` | capital |

**`risk` runs at 4 and 6, with `liquidity` between them.** That is the point of the artifact. Any
integration that treats a subsystem as a unit — run all of risk, then liquidity — gives `risk.exposure`
a score from the previous tick. The correct order interleaves *stages* across vendors, and it is not
something the consumer could read off: the jars have no source.

The build order was a clean chain — `marketdata → pricing → liquidity → risk → capital`, each compiled
against only the jars before it. **The runtime order is not that chain.**

## The test

[`consumer/src/test/java/com/acme/CompositionTest.java`](consumer/src/test/java/com/acme/CompositionTest.java) — three tests, green:

- every stage runs once, and after its dependencies
- **`liquidity` runs between `risk`'s two stages**, so risk cannot be run as a unit
- the arithmetic is right through all five subsystems

## What this does and does not establish

**Does:** integration of independently-built binary subsystems is a declaration of which supplies
which, and the global dispatch — including one vendor's stages split across two others' — is derived.

**Does not:** there is no comparison here. No hand-written arm has been run against these same jars.
Doing so is the measurement that would test whether the alternative is materially harder, and it has
not been done.
