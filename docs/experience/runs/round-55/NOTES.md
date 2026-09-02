# Round 55 rung 1 — Haiku selects correctly from six type-identical candidates

**PASS.** Prediction: [`PREDICTION.md`](PREDICTION.md), committed before the fixture was built, with a
dated amendment recorded before any run.

## The fixture

The pricing jar carries **seven** entry points. `PricingSpot` is type-eliminable. The other six —
`PricingFull`, `PricingHedged`, `PricingNetted`, `PricingGross`, `PricingCapped`, `PricingSmoothed` —
have **byte-identical** `Fluxtion-Provides`, `Fluxtion-Requires`, `Fluxtion-Constructor` and
`Fluxtion-Consumes`, verified mechanically. Only `Fluxtion-Description` differs. The brief specifies
the desk's hedging overlay in business terms and never names a class.

**The correct answer is deliberately not the obvious one:** `PricingFull` was correct in every prior
round and is the plainest name in the family.

## Result

`PricingHedged`, and **verified in the generated processor rather than from the report** — the emitted
`AppProcessor` contains `HedgedSpread`, so the right component is in the graph, not merely named in
XML. `mvn -q -o compile` green.

Its own ruling-out of `PricingFull` is the part worth quoting, because it is the correct reason:

> *"Description says 'adds the spread' but provides no specification of hedge inclusion; unclear
> whether it includes hedging overlay."*

That is discrimination on absence of a promise, not on presence of a keyword.

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| S1 | rung 1 passes | **passed** | ✓ |
| S2 | breaks the zero-`javap` result | **`javap` 0 → 11** | ✓ |
| S5 | if it fails, the manual's requires-chain line is the cause | **not exercised — no failure** | – |
| S6 | if it fails, it picks `PricingFull` | **not exercised**, and actively ruled out with sound reasoning | – |

## What this costs, and what it says about the manifest

**`javap` went from 0 to 11.** Cell O's headline result was that the indexed manifest removed class
inspection entirely; one description-only choice put eleven calls back. The model did not trust prose
where it had trusted the index.

**That is the finding, and it is about the catalogue, not the model.** The manifest carries the *type
surface* and not the *selection criterion*, so the moment types stop discriminating, the memoised
analysis stops paying and the author reverts to inspection. A field carrying the discriminator —
`Fluxtion-Convention: hedged` beside `Fluxtion-Provides` — would collapse this back to string
matching. **Untested, and it is the experiment that would actually demonstrate the
partial-evaluation claim: memoising the analysis lets a cheaper model do the job.**

## Scope — what this round did NOT test

**Nothing here is Fluxtion-specific.** Six type-identical entry points discriminated by prose is a
property of the manifest convention; a Spring catalogue, an OSGi bundle or a Maven multi-module repo
would pose it identically. This measured **catalogue ergonomics**, not framework semantics.

**The ceiling is above this rung**, so per this round's own falsifier it is reported as "the titration
was too easy" rather than as a limit. The Fluxtion-semantics axis — shared instances, cross-component
trigger references, graph shape — is a different experiment and is where the framework's own limits
would appear.
