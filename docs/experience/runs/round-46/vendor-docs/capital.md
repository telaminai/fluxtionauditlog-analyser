# capital 1.0 — vendor documentation

## Entry point

```java
new Capital(risk)
```

## What it consumes

| event | who handles it | notes |
|---|---|---|
| `Events.Trade` | `Capital.trade` — `onTrade(Trade)` | |
| `Events.Config` | `Capital.config` — `onConfig(Config)` | **only** the key `chargePct`. Returns `false` otherwise. |

## What it publishes

| field | records as | derived from | notes |
|---|---|---|---|
| `charge` | `capital.charge` | our config, risk `exposure` | |
| `buffer` | `capital.buffer` | our trade, `charge`, risk `var` | |
| `fee` | `capital.fee` | risk `exposure` | see *Fee strategy* |
| `breachCount` | `capital.breachCount` | risk `limitDetector` | **stateful**, and below the detector |
| `alert` | `capital.alert` | risk `limitDetector`, `charge` | **SIDE EFFECT.** Publishes to `AlertSink.PUBLISH`. Below the detector. **Running it when the detector reported `false` publishes an alert for a breach that did not happen.** |
| `alertCount` | `capital.alertCount` | `alert` | **stateful**, two levels below the detector |

## Fee strategy

We publish the strategies; you select one, you do not write one.

```java
FeeStrategies.byName("premium")        // or "default"
FeeStrategies.DEFAULT / FeeStrategies.PREMIUM
```

Apply it with `capital.fee.feeStrategy(strategy, name)`. Under Fluxtion, `Fee` also accepts it as a
registered service:

```java
processor.registerService(new Service<>(FeeStrategies.byName(n), FeeStrategy.class));
```

## Alerts

```java
AlertSink.PUBLISH = alert -> ...;      // you supply the destination
```

## For Fluxtion users

```xml
<bean id="capital" class="com.vendor.capital.Capital">
    <constructor-arg ref="risk"/></bean>
```
