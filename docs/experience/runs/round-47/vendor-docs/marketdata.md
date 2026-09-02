# marketdata 1.0 — vendor documentation

## Entry point

```java
new MarketData()                       // no arguments; it builds its own subtree
```

`MarketData` is the whole library. Construct it once and hold it.

## What it consumes

| event | who handles it | notes |
|---|---|---|
| `Events.Tick` | `MarketData.tick` — `onTick(Tick)` | |
| `Events.Config` | `MarketData.config` — `onConfig(Config)` | **only** the key `volFactor` |

## What it publishes

| field | records as | constructed from |
|---|---|---|
| `mid` | `marketdata.mid` | `tick` |
| `depth` | `marketdata.depth` | `tick` |
| `vol` | `marketdata.vol` | `config`, `mid` |
| `ewma` | `marketdata.ewma` | `mid` — **stateful**: it accumulates across calls |

Other libraries take `MarketData` in their constructor and read these.

## For Fluxtion users

Declare one bean:

```xml
<bean id="marketdata" class="com.vendor.marketdata.MarketData"/>
```

`MarketData` carries a second, all-fields constructor so the generator can reconstruct the subtree.
Do not declare our internal nodes individually — the composite is the supported unit.
