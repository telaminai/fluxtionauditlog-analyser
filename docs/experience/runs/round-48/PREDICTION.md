# PREDICTION — round 48, choosing from a catalogue

**Written and committed BEFORE either arm is launched.** No result seen.

## What changed

Rounds 44–47 gave each library **one** entry point, so the only decision was *declare it*. This round
makes the consumer **choose**, which is what integrating a component market actually involves.

- **A shared `contracts` artifact** holds the events and the published interfaces. No component
  depends on another component's classes. This removes the objection recorded in round 42 — that all
  five suppliers depended on one `Events` class shipping inside marketdata's jar, so whoever owned
  the schema owned the market.
- **Nine entry points across five jars**, declared in each jar's manifest with what they provide,
  require and consume. Four of the five jars offer a *smaller* option that still builds.
- **Choosing wrong is silent.** `MarketDataCore` omits `vol` and `ewma`; `PricingSpot` omits the
  spread; `RiskBasic` omits limit supervision; `CapitalCore` omits alerting and the counts. Each
  compiles, runs, and produces fewer figures.
- One coupling makes a wrong choice **loud**: `CapitalRegulated` requires `LimitApi`, which only
  `RiskSupervised` publishes. Picking `RiskBasic` makes the correct capital component undeclarable.

Both arms get the same catalogue, the same manifests and the same brief. Neither is told which
variant to pick — the requirements are stated as business needs and the mapping is the work.

## Three things I verified before writing this

1. **The trigger edge survives an interface.** A node declaring a constructor parameter of interface
   type still gets the edge — `guardCheck_adjusted() { return isDirty_depth | isDirty_mid; }`.
2. **An entry-point class is not a node.** It carries no annotations, so `isDirty_marketdata` does
   not exist and anything wired through it never fires. Measured: **8 stages instead of 17**, with a
   clean build. Filed as `UP-FLX-29`, which also asks for a diagnostic.
3. **One instance satisfying several contracts is ambiguous.** When a component provides both
   `MidApi` and `DepthApi`, a node taking both gets the same instance twice and needs
   `@AssignToField`. Without it: `FLX-1001 … these fields share a type`.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| W1 | **Both arms select all five correct components.** The manifest states provides/requires plainly and the brief lists the required figures; this is a matching exercise, not an inference. | medium-high |
| W2 | **If either mis-selects, it is `MarketDataPlus` vs `MarketDataCore`** — the only choice driven purely by the figures list, with no structural consequence to catch it. | medium |
| W3 | **The Fluxtion arm wires at least one component through the entry point rather than the node**, producing a quietly smaller graph — the failure `UP-FLX-29` describes. The guide warns about it explicitly, which is exactly the kind of warning round 47 showed an arm can read and still walk into. | medium |
| W4 | **The plain-Java arm writes a dispatch engine again** (round 47: `Node` + `Graph` + `RiskEngine`, 207 lines). The catalogue changes what it assembles, not that it must assemble it. | high |
| W5 | **The Fluxtion arm needs ≤4 `mvn` runs.** Round 47's ten were an FQN guessed from memory (now `FQN.md`), a phantom generator bug, a fat-jar detour and a reinvented `registerService` — all four addressed in the toolkit note and the guide. | medium |
| W6 | **Neither arm's own tests catch its own defect**, if it has one. That has held every round. | medium-high |

## Falsifier

**If the plain-Java arm again scores full marks**, then three rounds running it has matched Fluxtion
on correctness given a capable model, and the honest position is that the framework's case rests on
surface area and on failure modes being loud — not on getting more answers right. I have said this
twice; a third would settle it.

## The measurement I owe

Cost is reported per arm **with the model named** — Fluxtion on Haiku, plain Java on the stronger
model — because the commercial claim is the priced cost of a *correct* result, not tokens. Round 47
showed the Fluxtion arm's token count is dominated by Haiku's step size (76 output tokens per turn
against 672), so raw totals across models measure granularity, not approach. **A third cell —
Fluxtion on the stronger model — is what would make the priced comparison clean, and it is not in
this round.**
