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

| field | records as | derived from | notes |
|---|---|---|---|
| `notional` | `risk.notional` | our trade, marketdata `mid` | |
| `exposure` | `risk.exposure` | `notional`, liquidity `score` | |
| `var` | `risk.var` | our rate, `exposure`, marketdata `vol` | |
| `limitDetector` | `risk.limitDetector` | `exposure` | **a detector.** It reports `false` unless `exposure` exceeds `limit` (default 250000). **Anything downstream of it must not run when it reports `false`.** |
| `streak` | `risk.streak` | `exposure` | **stateful.** Consecutive breaches. It hangs off `exposure`, *not* the detector, because it must also see the clean events in order to reset. Advance it exactly once per change to `exposure`. |

## For Fluxtion users

```xml
<bean id="risk" class="com.vendor.risk.Risk">
    <constructor-arg ref="marketdata"/><constructor-arg ref="liquidity"/></bean>
```
