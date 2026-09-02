# risk 1.0 — vendor documentation

## Entry point

```java
new Risk(marketData, liquidity)
```

## What it consumes

| event | who handles it |
|---|---|
| `Events.Trade` | `Risk.trade` — `onTrade(Trade)` |
| `Events.Rate` | `Risk.rate` — `onRate(Rate)` (ours, separate from pricing's) |

## What it publishes

| field | records as | constructed from | notes |
|---|---|---|---|
| `notional` | `risk.notional` | our `trade`, marketdata `mid` | |
| `exposure` | `risk.exposure` | `notional`, liquidity `score` | |
| `var` | `risk.var` | our `rate`, `exposure`, marketdata `vol` | |
| `limitDetector` | `risk.limitDetector` | `exposure` | a detector. `calc()` reports whether `exposure` exceeds `limit` (default 250000). |
| `streak` | `risk.streak` | `exposure`, `limitDetector` | **stateful**: consecutive breaches, and it resets |

## For Fluxtion users

```xml
<bean id="risk" class="com.vendor.risk.Risk">
    <constructor-arg ref="marketdata"/><constructor-arg ref="liquidity"/></bean>
```
