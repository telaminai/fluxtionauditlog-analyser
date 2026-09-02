# Integrating these five libraries with Fluxtion

## One entry point per library

Each library publishes **exactly one entry-point class**, and that class is the unit you declare.
Construct it with the constructor named here:

| library | entry point | constructor |
|---|---|---|
| marketdata | `com.vendor.marketdata.MarketData` | **the no-argument one** |
| pricing | `com.vendor.pricing.Pricing` | `Pricing(MarketData)` |
| liquidity | `com.vendor.liquidity.Liquidity` | `Liquidity(MarketData, Pricing)` |
| risk | `com.vendor.risk.Risk` | `Risk(MarketData, Liquidity)` |
| capital | `com.vendor.capital.Capital` | `Capital(Risk)` |

The generator walks into each entry point and places every internal node in the global dispatch.

## Do not declare our internal nodes

`Mid`, `Depth`, `Vol`, `Ewma`, `Adjusted`, `Spread`, `Book`, `Score`, `Notional`, `Exposure`, `Var`,
`LimitDetector`, `Streak`, `Charge`, `Buffer`, `Fee`, `BreachCount`, `Alert`, `AlertCount` and the
event adapters are **ours**. They are reachable through the entry point and you never name them.
Declaring them individually still works, but it puts our internal structure into your integration —
so our next release breaks your bean file.

## Why each entry point carries two constructors

The one in the table is **yours**. Each class also has a second constructor taking every one of its
node fields; that one exists **only** so the generator can reconstruct the subtree in the generated
processor. **Never declare a bean against it.**

## Reading state back

Entry points are public fields on the generated processor, and their nodes are public fields on them:

```java
processor.capital.breachCount.breaches
processor.risk.streak.longest
```
