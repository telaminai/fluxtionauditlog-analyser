# PREDICTION — round 11: the vanilla control, and the change round

**Committed before launch.** Two questions in one design, both previously unmeasured.

## Q1 — the vanilla control (settles the "5% of vanilla Java" claim)

Same behaviours-only clearing task, same model (**Haiku**), **no framework** — plain Java, hand-wired
dispatch, and it must still evidence M1–M6 from a log it writes itself. Everything else held constant, so
token counts are directly comparable to the three correct Haiku Fluxtion runs already measured
(mean **91,471** tokens).

## Q2 — the change round (owner's real test)

*"Once a solution is working, add some new requirements — with Fluxtion I think it will be a wash."*

Three new requirements are handed to a **working** solution: a house buffer that sits **between** the
requirement and the call, a **new event type** with its own change-detection semantics, and a
concentration surcharge that modifies the requirement **before** the call sees it. **Two of the three
reorder dispatch.** That is deliberate — it is the case a hand-wired system must re-derive by reading its
own code, and the case Fluxtion claims to derive for you.

Round 11 measures the Fluxtion change arm now (on two already-working solutions). The vanilla change arm
follows once Q1 produces something to change.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| V1 | **Vanilla builds green in 2/2** — plain Java is not hard, and Haiku is competent | high |
| V2 | **Vanilla uses MORE tokens than Fluxtion for the build: >91,471, and I will say ≥140,000** | medium |
| V3 | Vanilla's M5/M6 evidence is **weaker** — it must *assert* ordering it chose, where Fluxtion agents quoted a derived order and a `topologicalRank` | medium-high |
| **C1** | **The Fluxtion change costs FEWER tokens than the original build** — under 91,471, and I will say **under 60,000** | medium |
| C2 | **The Fluxtion change requires NO edit to the report node**, because it is a sink and the new nodes insert upstream of it | medium |
| C3 | **Dispatch order changes and no agent has to work out the new order** — they will observe it in the generated source rather than derive it | medium-high |
| C4 | M4 (one report per cycle) survives the new event type **without extra work**, because the always-true heartbeat node already handles every event type | medium |
| C5 | **M5 survives untouched in both change runs** — it is structural, not coded | high |

## The claim this round can actually settle

Not "Fluxtion is cheaper" — a build-cost ratio at 8 nodes understates it, because the hand-wired cost
scales with **edges and orderings** while the declared cost scales with **nodes**.

What it can settle is **the shape of the change cost**: whether adding a requirement that reorders
dispatch is a local edit (Fluxtion's claim) or a re-derivation (the hand-wired reality). That is the
claim the whole framework rests on, and four rounds of greenfield could not test it.

## Standing caveat

n=2 per arm. Direction, not rate. My quantitative misses have been pessimistic in five of six rounds —
so V2 and C1 are stated at the optimistic end deliberately.
