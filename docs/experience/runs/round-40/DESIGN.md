# Round 40 — a compatible upgrade from one supplier

Round 39 measured the cost of **integrating** five prebuilt subsystems. This measures the cost of
**living with them**: pricing ships 2.0, and the consumer already has a working engine.

The upgrade is **binary compatible** — nothing is removed, no existing signature changes — and it is
the ordinary kind a vendor ships. It contains two changes that behave very differently.

## Change A — additive, and INVISIBLE to the consumer

`PxRate` gains one method:

```java
public boolean onConfig(Events.Config c)     // reacts to key "spreadMult"
```

`Spread` keeps its exact constructor and now multiplies by that value. **No class is added, no
signature changes, and the consumer has nothing to declare.** But the correct behaviour has changed:
`CONFIG` events must now reach pricing, and in 1.0 they never did.

This is the change that separates *deriving* dispatch from *recording* it. An engine that
re-derives from the jars picks it up with no consumer action at all. An engine holding a stored
event→handler map keeps running, keeps building green, and **silently stops computing a stage that
should now run**. Nothing fails; the numbers are just wrong.

## Change B — additive, and visible

A new public stage:

```java
public Skew(com.vendor.marketdata.Vol vol, Adjusted adjusted)
```

A **cross-vendor** dependency: `Skew` must run after marketdata's `Vol`, which is itself deep
(`MdConfig` → `Mid` → `Vol`). Adopting it requires the consumer to declare it, so unlike change A
nobody can miss that something happened — the question is only what it costs to place it correctly.

## What is measured

| | |
|---|---|
| **Variable** | the composition mechanism, as in round 39 |
| **Held constant** | each arm starts from **its own working round-39 solution**, unmodified |
| **Handed over** | `pricing-2.0.jar`, and a note saying only that pricing has released a compatible version adding a spread multiplier configured by `spreadMult`, and a new `Skew` stage worth adopting |
| **Scored** | the same 37-point scheme, against a held-out scenario that exercises `spreadMult` |

Neither arm is told that change A alters which stages run for a `CONFIG` event. That is the finding,
not the instruction — telling them would be round 34's defect repeated.

## Prediction is committed separately before either arm is launched.
