# Round 48 — a component catalogue the consumer must choose from

Rounds 44–47 handed the consumer five libraries with one entry point each. The only decision was
*declare them*. This round makes the consumer **choose**, which is what integrating a component market
actually involves.

## The shape

**A shared `contracts` artifact** holds the event types and the node-level interfaces. No subsystem
depends on another subsystem's classes any more — only on `contracts`. This removes the objection
recorded in round 42, that all five suppliers depended on one `Events` class shipping inside
marketdata's jar, so whoever owned the schema owned the market.

**Each jar publishes SEVERAL entry points** with different capabilities, declared in the manifest as
per-entry sections — the idiomatic mechanism, and machine-readable:

```
Name: com/vendor/marketdata/MarketDataPlus.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: mid,depth,vol,ewma
Fluxtion-Requires:
Fluxtion-Description: full market data, including volatility and a smoothed mid
```

The consumer reads the catalogue, matches it against the business requirements, and declares the
subset that satisfies them. **Choosing a smaller variant compiles, runs, and quietly produces fewer
figures** — the failure is a missing row in the audit trail, not an exception.

## The catalogue

| jar | entry point | provides | requires |
|---|---|---|---|
| marketdata | `MarketDataCore` | mid, depth | — |
| | **`MarketDataPlus`** | mid, depth, vol, ewma | — |
| pricing | `PricingSpot` | adjusted | `MidApi`, `DepthApi` |
| | **`PricingFull`** | adjusted, spread | `MidApi`, `DepthApi`, `RateApi` |
| liquidity | **`LiquidityStd`** | book, score | `DepthApi`, `AdjustedApi` |
| risk | `RiskBasic` | notional, exposure, var | `MidApi`, `VolApi`, `ScoreApi` |
| | **`RiskSupervised`** | notional, exposure, var, limitDetector, streak | `MidApi`, `VolApi`, `ScoreApi` |
| capital | `CapitalCore` | charge, buffer, fee | `ExposureApi`, `VarApi` |
| | **`CapitalRegulated`** | charge, buffer, fee, breachCount, alert, alertCount | `ExposureApi`, `VarApi`, `LimitApi` |

**Bold is the correct choice** for the requirements as written. Four of the five jars offer a wrong
option that still builds.

## What each requirement selects

| requirement | forces |
|---|---|
| the published figures include volatility and a smoothed mid | `MarketDataPlus` over `MarketDataCore` |
| …and a spread | `PricingFull` over `PricingSpot` |
| a breach alert is published when and only when the limit is breached | `RiskSupervised` **and** `CapitalRegulated` |
| breach count, streak and alert count reported | `CapitalRegulated`, and `RiskSupervised` for the streak |

`CapitalRegulated` requires `LimitApi`, which only `RiskSupervised` provides — so picking `RiskBasic`
makes the correct capital component **undeclarable**. That is the one place the wrong choice fails
loudly rather than quietly, and it is deliberate: a catalogue where every mistake is silent is not a
realistic one.

## What is being measured

The same 37-point business scoring as rounds 45–47, against a held-out scenario, plus:

- **did the consumer select the right components** — readable from which entry points it declared
- **how many builds** — the Fluxtion arm should need about two

Both arms get the same catalogue and the same manifests. Neither is told which variant to pick.
