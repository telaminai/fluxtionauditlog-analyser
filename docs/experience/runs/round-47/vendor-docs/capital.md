# capital 1.0 — vendor documentation

## Entry point

```java
new Capital(risk)
```

## What it consumes

| event | who handles it | notes |
|---|---|---|
| `Events.Trade` | `Capital.trade` — `onTrade(Trade)` | |
| `Events.Config` | `Capital.config` — `onConfig(Config)` | **only** the key `chargePct` |

## What it publishes

| field | records as | constructed from | notes |
|---|---|---|---|
| `charge` | `capital.charge` | our `config`, risk `exposure` | |
| `buffer` | `capital.buffer` | our `trade`, `charge`, risk `var` | |
| `fee` | `capital.fee` | risk `exposure` | see *Fee strategy* |
| `breachCount` | `capital.breachCount` | risk `limitDetector` | **stateful** |
| `alert` | `capital.alert` | risk `limitDetector`, `charge` | **side effect**: publishes to `AlertSink.PUBLISH` |
| `alertCount` | `capital.alertCount` | `alert` | **stateful** |

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

