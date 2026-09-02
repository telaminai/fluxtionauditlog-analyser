# Round 32 — the ceiling moved, and the unwind transferred

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `87f9c64`. **4 of 5.**

## CEIL3 — the ceiling probe, third attempt

Same 24-rule / 28-event spec, same model, three templates:

| attempt | template | `mvn` | output tok | weighted | **score** |
|---|---|---|---|---|---|
| r28 | plain | 1 | 12,354 | 6.00 | **0/18** |
| r29 | + `EdgeDetector` | 12 | 17,820 | 12.28 | **0/18** |
| **r32 CEIL3** | **+ unwind, nested records, the principle** | **6** | **10,587** | **6.54** | **13/18** |

**13/18 at the same cost as the run that scored zero, and fewer output tokens than either.** It also
passed probe 05 — several decisions of one kind in one event — which had defeated every previous engine
in three different ways.

T2 ✓ it compiled; the nested-records pattern removed exactly what killed round 29. T3 ✓ 13 is inside the
predicted 8–14.

**T4 ✓ — the failures are where predicted.** Every pass is a single-event lookup or a gate; every
failure needs state carried across events: `stockout` and `oversold` (*"EdgeDetector created but never
used"*), `sla-breach`, `re-release`, `bin-overflow`.

**T5 ✓ — the separation held**, unprompted:

> *"The node was running (path was correct), but the decision-emission logic inside the node was wrong…
> Distinguishing 'path is wrong' from 'node is wrong' required only the trace. This is exactly what the
> README meant by 'fix one or the other, never both at once.'"*

**The limit it names is architectural and I had not identified it:**

> *"Graph is read-only. Decisions emit to a static list, but no event feeds back into the graph. This
> blocks recording release time on RELEASE, and auto-quarantine on QUALITY_HOLD."*

**Rules where a decision must become state that later rules read** have no pattern in the template or
the framework, and account for at least two of the five failures.

## GRAPH2 — the order-dependent spec, four engines, one oracle

| engine | template | decisions | order |
|---|---|---|---|
| vanilla, Haiku | — | 8/8 | 3/3 |
| Fluxtion, Haiku | old | 8/8 | **2/3** |
| Fluxtion, Opus | new | 8/8 | 3/3 |
| **Fluxtion, Haiku** | **new** | **7/8** | **3/3** |

**T1 ✓ — the unwind transferred.** The Haiku cell that scored 2/3 by never reaching for `@AfterTrigger`
now scores 3/3, on the same spec, because the template demonstrates it with a test. That is a
model-independent gain from one worked example.

**Its one real decision failure is instructive.** It fails the halt probe: a `PRICE` event still
propagates for a halted book. Its own report claims halting works — *"event 18 shows `tradeStore|haltGate`
only"* — verified on a **trade** event and never on a price. **The gate was placed on one inbound path
and not the other**, and the self-check confirmed the path it had thought about.

## Two scorer conventions, applied to both arms alike

A uniform difference is one defect, not one per probe: **0-based event numbering** (vanilla) and
**numbers not formatted to two places** (GRAPH2) are each reported once, with the result otherwise
scored on behaviour. Without that, GRAPH2 reads as 0/8 when it is behaviourally 7/8.

## Cost structure, measured

For a representative cell: **91.4% of the weighted cost is re-reading the accumulated conversation**,
8.5% is writing code, and **0.1% is everything deliberately sent** — task, template, audit logs, all of
it, totalling 1,552 tokens across a 193-turn run whose context grew 13k → 103k.

So documentation size is not a cost lever and never was; **build cycles are**, because each is several
turns charged at the full accumulated context. The template at ~3,400 tokens is 0.04% of a run.
