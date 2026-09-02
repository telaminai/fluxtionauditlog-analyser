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

---

## Phase 2 — local search, and when to start it

**Written before cells G/H/I reported.**

The ablation so far is coarse search: whole documents in or out. That is the right move while the
gradient is steep — from 14.80M and a wrong answer down to 3.91M and correct, a 3.8× reduction from
the instruction set alone on the same model and the same problem.

**Coarse search stops being the right move once the surface flattens.** The owner's proposal, adopted:
near an optimum, switch to **local search with introspection** — take the best cell and its nearest
neighbours, and ask the model to explain *why* it failed or spent, rather than only measuring that it
did.

### The stopping criterion, fixed now so it cannot be chosen to fit a result

Phase 1 continues while a new cell improves on the incumbent best by **more than 10% weighted cost**
*at equal or better correctness*. When two consecutive cells fail to clear that, the surface is
treated as flat and phase 2 begins.

Incumbent at the time of writing: **B — 879 words, correct, 10 builds, 3.91M.**

### What phase 2 asks

Not "what was hard" — every report so far answers that with build errors already visible in the log.
The directed questions are:

1. **At each build failure, what did you not know?** Name the fact, not the symptom.
2. **Which sentence of the manual, had it existed, would have prevented it?** Write the sentence.
3. **Which parts of the manual did you read and not use?** Those are deletion candidates that a
   whole-document ablation cannot find.

Question 3 is the one coarse search cannot reach. Removing a document tests the document; only the
model can say which *paragraphs* it never needed.

### The known caveat

Self-reports in this project have been unreliable in a specific way: they are accurate about what the
model *did* and unreliable about *why*. Round 48's arm reported a generator bug that did not
reproduce; cell E justified reflection with a constraint that did not exist. **Phase 2 answers are
therefore hypotheses to test by ablation, not findings.** Each proposed sentence gets added or removed
and re-measured — the introspection generates candidates, the measurement decides.
