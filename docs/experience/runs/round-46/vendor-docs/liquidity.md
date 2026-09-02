# liquidity 1.0 — vendor documentation

## Entry point

```java
new Liquidity(marketData, pricing)
```

## What it consumes

| event | who handles it |
|---|---|
| `Events.Tick` | `Liquidity.tick` — `onTick(Tick)` (ours, separate from marketdata's) |

## What it publishes

| field | records as | derived from |
|---|---|---|
| `book` | `liquidity.book` | our tick, marketdata `depth` |
| `score` | `liquidity.score` | pricing `adjusted`, our `book` |

## For Fluxtion users

```xml
<bean id="liquidity" class="com.vendor.liquidity.Liquidity">
    <constructor-arg ref="marketdata"/><constructor-arg ref="pricing"/></bean>
```
