# Round 37 — five subsystems, five jars, one bean file

The owner's method, followed properly: **split into subsystems, build and validate each separately,
publish binaries, and then the test is writing the Spring file.**

## What was built

Five subsystems, each compiled and jarred in dependency order, each depending only on jars built before
it: `marketdata → pricing → liquidity → risk → capital`. **Five jars, 8 classes, zero `.java` files.**

## The consumer's entire integration

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

**Seven beans. No Java. No declared events. No declared order.** The consumer states which subsystem
supplies each input and nothing else.

## The order the generator derived

| # | stage | subsystem |
|---|---|---|
| 1 | `depth` | marketdata |
| 2 | `mid` | marketdata |
| 3 | `adjusted` | pricing |
| 4 | **`notional`** | **risk** |
| 5 | `score` | liquidity |
| 6 | **`exposure`** | **risk** |
| 7 | `charge` | capital |

**`risk` runs at 4 and 6, with `liquidity` between them.** No composition that treats a subsystem as an
atomic unit can produce that: running all of risk, then liquidity, gives `risk.exposure` a score from
the previous tick. The build order was a chain; the runtime order is not, and nobody wrote it.

Three tests green, including one asserting exactly that split — *"liquidity must run between risk's two
stages, so risk cannot be run as a unit"* — and one checking the arithmetic through all five.

## What this shows, and what it does not

**Shown:** with subsystems delivered as binaries, integration is a declaration of which subsystem feeds
which, and the global dispatch — including one vendor's stages being split across two others' — is
derived. That is the "integration cost near zero" half of the component-market thesis, demonstrated at
five subsystems.

**Not shown:** the comparison. No vanilla arm has run against these jars. Vanilla's method in round 34
was reading `evaluate()` bodies, which the jars remove, but "it would be harder" is a prediction until
someone measures it. That run is the obvious next step and it is now genuinely fair — same binaries,
same validated components, and the only question is whether the consumer declares the order or works it
out.
