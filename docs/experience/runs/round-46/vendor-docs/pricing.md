# pricing 1.0 — vendor documentation

## Entry point

```java
new Pricing(marketData)                // takes marketdata; builds its own subtree
```

## What it consumes

| event | who handles it |
|---|---|
| `Events.Rate` | `Pricing.rate` — `onRate(Rate)` |

## What it publishes

| field | records as | derived from |
|---|---|---|
| `adjusted` | `pricing.adjusted` | marketdata `mid`, `depth` |
| `spread` | `pricing.spread` | our `rate`, `adjusted` |

## For Fluxtion users

```xml
<bean id="pricing" class="com.vendor.pricing.Pricing">
    <constructor-arg ref="marketdata"/></bean>
```
