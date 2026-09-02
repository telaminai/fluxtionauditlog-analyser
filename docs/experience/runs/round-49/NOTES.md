# Round 49 — idiomatic plain Java, and a correction to how this series has been reported

## The result

Idiomatic components — one class per subsystem, ordering written by hand inside each method, no node
graph. **9 classes and 140 lines against Fluxtion's 56 and 534**, which is a real point in plain
Java's favour on the supplier side.

The consumer arm, on the stronger model, got a great deal right: **3 alerts with exact charges, and
all three stateful counters — `breachCount`, `alertCount`, `streak` — identical to Fluxtion.** It
established its dispatch policy empirically: 3,000 randomised runs, behavioural bisection of the limit
to `250000.00000000006`, 200,000-sample differential testing to recover the fee function.

**And it reports Value at Risk 88% too low.**

| figure | Fluxtion | idiomatic | |
|---|---|---|---|
| `risk.var` | 3,369,923.85 | 421,240.48 | **88% understated** |
| `capital.buffer` | 134,846,511.71 | 16,899,176.95 | **87% understated** |

A risk manager raises `volFactor` 8× after the book is live. `MarketData` recomputes `vol`; nothing
tells `Risk` to look again, because a coarse component cannot be told to. It builds, it runs, and all
25 of the arm's own tests pass.

**The arm knew and had no correct option.** Its report: *"A `volFactor` change moves `marketdata.vol`
and leaves `risk.var` computed from the old vol until the next real event… the only way to fake one
— re-dispatching the last tick — would advance the breach streak, the breach count and the alert
count for an event that never arrived."* It chose staleness over corrupting the counters. Given the
components it was handed, that was the right call, and it still produced an order-of-magnitude error
in a regulatory figure.

## A correction to how this series has been reported

The owner pointed out that I have applied asymmetric standards, and the audit confirms it.

**Across ~22 Fluxtion runs there is exactly one wrong answer** — round 48's fee — and I established at
the time that I caused it by dropping the `registerService` line when I replaced the per-library docs
with manifests. I recorded *"round 48's Fluxtion result is not a valid measurement"* **and then cited
it in every subsequent summary.**

**The 14.80M cost figure measures a bad brief**, not the framework. I rewrote the brief; the same
model on the same jars produced 5.03M, then 1.98M once the catalogue carried its own index. I have
been using my own worst instruction set as Fluxtion's "before" number.

Meanwhile I carefully separated my `PREMIUM` fixture defect out of the plain-Java result. Applying
that standard in one direction only is not caution, it is false balance.

**Symmetrically stated: no Fluxtion failure in this series has ever traced to the framework.** Every
one traced to my instructions or my fixture. The idiomatic arm's 88% VaR error does not — I removed
my contribution and it remained.

## Two false findings of mine, retracted

1. **A "phantom breach count"** at the stray-config event. My parser attributed records following a
   `STRATEGY` event to the previous data event. **The counters are identical.** I reported this as an
   architectural failure before checking.
2. **"Very nearly correct."** Wrong as framing — in finance a wrong VaR is a fail, not a near miss —
   and wrong on the facts, because my first scenario put `volFactor` before any tick, so the gap was
   never exercised. The real failure is larger than the one I reported, not smaller.

## What still limits this

- **n = 1.** Not one configuration in this series has been run twice. Variance is unmeasured.
- **I wrote both libraries**, and iterated the Fluxtion one across rounds 39–48 while writing the
  idiomatic one in a single pass. A polished artefact against a first draft.
- **A vendor could close the gap** by adding `Risk.onConfig`. It is a library-design omission I
  authored, not a property of Java — though the general point stands: nothing in the language tells
  the vendor which omissions matter, and the consumer cannot fix it from outside.
