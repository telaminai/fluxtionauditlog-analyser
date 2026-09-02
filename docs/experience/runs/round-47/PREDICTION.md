# PREDICTION — round 47, the same test with the answer taken out

**Written and committed BEFORE either arm is launched.** No result seen.

## Why this round exists

Round 46's plain-Java arm, on a stronger model, was **fully correct** — 3 alerts, every counter exact.
That fired the falsifier I had stated, and it would have been the headline. Except that **I had put
the answer in the vendor documentation.** Two sentences did the work:

> `streak` — *"It hangs off `exposure`, **not** the detector, because it must also see the clean events
> in order to reset."*
>
> `alert` — *"**Running it when the detector reported `false` publishes an alert for a breach that did
> not happen.**"*

The arm's own report names them: *"The docs distinguish 'derived from' from 'below the detector'…
That is the false-alert trap and it is the one place the naive OR-of-all-constructor-args wiring
fails."* I removed one confound (undocumented vendors) and introduced a larger one.

## The change

**Every statement of design guidance is removed from BOTH arms' documentation** — the arrest rule,
`Streak`'s trigger, the false-alert warning, and both "advance exactly once" instructions. What
remains is factual: entry points, what each library consumes, what it publishes, what each node is
constructed from, and which nodes are stateful or side-effecting.

**This is not a handicap on one arm. It restores the real asymmetry**, which is that the vendor
already encoded the fact once, in the code:

| | what the consumer can read from the jar |
|---|---|
| Fluxtion build | `RuntimeVisibleAnnotations: NoTriggerReference` on `Streak.limits` **and** `Alert.charge` |
| plain build | `private final Exposure exposure;`<br>`private final LimitDetector limits;` — **indistinguishable** |

In the plain jar both fields are constructor parameters and nothing marks one a trigger and the other
a lookup. The generator reads the annotation; a hand-written engine has nothing to read. **That
difference is the thesis, and this round is the first time it is actually being tested** rather than
papered over by my prose.

## Design

| | |
|---|---|
| **Variable** | the composition mechanism, as before |
| **Held constant** | libraries, jars, brief, held-out scenario, factual documentation |
| **Models** | Fluxtion = Haiku 4.5. Plain Java = the stronger model, as in round 46. |
| **n** | 1 per arm |

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| V1 | **Fluxtion is unaffected — still fully correct, still 5 beans.** It never used the removed prose; the annotation carries it. | high |
| V2 | **The plain arm publishes false alerts.** With `Alert(LimitDetector, Charge)` and nothing marking `charge` as a lookup, the natural reading is that both parents trigger — so a `chargePct` change, or any trade that moves the charge without breaching, fires an alert. | medium |
| V3 | **If it avoids that, `streak` is where it fails instead** — hanging it off the detector makes it never reset, giving a monotonic streak. | medium |
| V4 | **It discovers at least one of the two traps by experiment**, not by reading — it has the jars and can run them. Round 46's arm already probed `FeeStrategies.byName` by executing it. | medium-high |
| V5 | **It writes a graph engine again** (round 46: `Node` 41 + `Graph` 96 + `RiskEngine` 70 = 207 lines). Removing prose does not remove the need for the machinery. | high |

## Falsifier

**If the plain arm is fully correct again, with no design guidance at all**, then the annotation buys
nothing a capable author cannot infer from the jars, and the strongest remaining claim for the
framework is surface area rather than correctness. I will say that plainly. It is the second time I
have set this falsifier and the first time firing it was my own fault.

## Standing caveats

n=1. Different models per arm, deliberately — the commercial question is the priced cost of a
**correct** result, so tokens are reported per arm with the model named, and the ratio left explicit.
I wrote the libraries and the documentation; a reader should discount accordingly.
