# Five subsystems: a clean build DAG whose stages still interleave

Each subsystem is built and validated on its own, then published as a jar. The **build** order is a
clean chain — each depends only on ones built before it — so they can genuinely be staged:

```
marketdata ──▶ pricing ──▶ liquidity ──▶ risk ──▶ capital
```

But the **stage** order is not that chain, because `risk` has two stages at different depths:

| # | stage | subsystem | reads |
|---|---|---|---|
| 1 | `mid` | marketdata | Tick |
| 2 | `depth` | marketdata | Tick |
| 3 | **`notional`** | **risk** | `mid` |
| 4 | `adjusted` | pricing | `mid`, `depth` |
| 5 | `score` | liquidity | `adjusted` |
| 6 | **`exposure`** | **risk** | `notional`, `score` |
| 7 | `charge` | capital | `exposure` |

**`risk` runs at positions 3 and 6, either side of two other vendors' subsystems.** Any composition that
treats a subsystem as an atomic unit — run all of risk, then all of pricing — gives `risk.exposure` a
score from the previous tick, or `pricing.adjusted` a stale depth. The correct order is only reachable
by ordering *stages*, not components.

That is the property the whole experiment turns on, and it is why a build DAG being a chain does not
make the runtime order a chain.
