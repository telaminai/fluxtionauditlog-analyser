# PREDICTION — round 33: maintenance, not greenfield

**Committed before launch.** The owner's contention, tested directly:

> *a complex solution handed to a fresh model primed with the Fluxtion template will beat vanilla, when
> both are loaded into a fresh session for a maintenance change to functionality.*

Every head-to-head in this project so far has been **greenfield**, and vanilla has tied or won all of
them. This is the first that gives each arm a codebase **it did not write** and asks it to change.

## Setup

Both engines already solve the 15-event fulfilment spec at **12/12**, built by Haiku, saved in
`docs/experience/artifacts/`. Each is handed to a fresh Haiku agent with the identical change request
and no memory of building it.

**The change is deliberately mid-chain, not a leaf.** Customer discounts alter the *value* of an order,
which R5 credit-ok already consumes — so it is not a new rule bolted on the end, it is a new input to
an existing derived value, plus a new EDGE decision. A discount arriving must re-evaluate affected
orders exactly as a payment does.

Scored on **16 probes: the 12 the engines already pass, unchanged, plus 4 new ones.** Regression counts
as much as the new behaviour.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **M1** | **Fluxtion regresses less.** It keeps ≥11 of the 12 existing probes; vanilla keeps ≤11. The new input must reach an existing derived value, and in vanilla that means editing a hand-ordered `if/else` chain that the agent did not write. | medium |
| **M2** | **Vanilla touches more files and more lines** to make the change. | medium |
| **M3** | **Vanilla's report names the dispatch chain as the hard part** — working out where a discount change must re-trigger, and in what order. Fluxtion's does not, because declaring a parent is the whole of it. | medium |
| **M4** | **Fluxtion needs fewer build cycles.** | medium-low — greenfield has not shown this, and change may not either. |
| **M5** | **Neither reaches 16/16.** `DISCOUNT_ABUSE` needs the *undiscounted* value while everything else needs the discounted one, and that distinction is easy to miss. | medium |

## Falsifier

**If vanilla regresses no more than Fluxtion and changes comparable amounts of code, the maintenance
claim fails as the greenfield claim did** — and the framework's case would rest on properties this
project has been unable to measure at all. That is the outcome three previous specs have produced, so
it is the one to expect unless the evidence says otherwise.

## Limit

n=1 per arm. A single change on a single spec. If Fluxtion wins clearly, the follow-up is a second,
differently-shaped change before the claim is made.
