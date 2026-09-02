# Ablation — which of the Fluxtion manual's 2,608 words earn their place

## Why

The purpose of this series is not to win a comparison. It is to find the **minimum instruction set**
that makes Fluxtion an efficient authoring target for a language model. Round 48b showed the
instruction set is the dominant variable: same model, same jars, same requirements — 16 builds and a
silently wrong fee under an open brief, **4 builds and every figure correct** under a closed one.

But I have only ever *added*. Every round answered a failure with another paragraph, and no round has
ever checked whether an earlier one had stopped earning its keep. This repo's own protocol says the
opposite — *"the protocol rewards deletion"* — and it has never been applied to the Fluxtion manual.

## The control and the cells

| cell | manual | words | removes |
|---|---|---|---|
| **A** | full set (= 48b) | **2,608** | — |
| **B** | no template `README.md` | **879** | 1,725 words, **66% of the manual** |
| **C** | no `FQN.md` | 2,342 | the generated annotation-package list |
| **D** | no `GUIDE-fluxtion.md` | 2,431 | how components are assembled |
| **E** | no "what you must NOT write" | 2,306 | the closed scope that fixed 48 |

The shared 473-word business task is excluded from every count — both arms get it, so it is not part
of the *adoption* cost. Each cell is one Haiku run, scored identically against the held-out scenario.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| Y1 | **B passes.** The README is the template's general guide; the task-specific facts it carries (`process-classes`, the `app` package) are already repeated in the toolkit note. | medium |
| Y2 | **C passes.** `javap` on the runtime jar recovers every annotation package, and 48b's arm did not obviously need the list. | medium |
| Y3 | **D fails or degrades.** The guide carries the one fact nothing else states — that an entry point is a holder, not a node, so wiring through it silently yields a smaller graph. That is `UP-FLX-29`, and the manifests do not say it. | medium-high |
| Y4 | **E degrades** — more builds, more invented code — but stays **correct**, because 48's failure was the fee, and `registerService` is in the scope section that E removes. So E may well fail on the fee specifically. | medium |
| Y5 | **At least two cells pass**, so the manual can be cut by a third or more. | medium |

## What a pass means

Full marks on the held-out scenario — all figures, three alerts, exact counters — **and** ≤5 `mvn`
runs. A cell that is correct but takes twelve builds has not earned deletion; it has just moved the
cost.

## The falsifier

**If every cell passes, the whole manual is unnecessary** and the 2,608 words are ceremony — the
model can do this from the manifests, `javap` and the business requirements alone. That would be the
most useful result of the series, and the cheapest thing to ship.

**If no cell passes**, every document is load-bearing and 2,608 words is the honest adoption cost.
That is also a result, and a quotable one.
