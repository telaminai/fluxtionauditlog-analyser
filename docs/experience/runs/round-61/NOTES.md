# Round 61 — dispatch cost: predictions committed BEFORE the experiments

**Runtime** Oracle GraalVM 25.0.4+7.1, macOS/aarch64 · **Harness** `tools/bench/latency-kit`, three-event-type graph
**Discipline** the owner asked for predictions recorded first, then results, then analysis. §1 was written
and committed before any experiment in §3 was run. Rounds 58–60 lost days to reading a result and then
constructing the mechanism that fitted it; a committed prediction cannot be retrofitted.

---

## 0. Facts first — read from the generated source, not assumed

**The question that prompted this round:** *"How can the hand-rolled be faster than a switch statement?
The pipeline failures would make you think if was more expensive?"*

**The premise is wrong twice, and both halves matter.**

**There is no switch.** The generated dispatch is an `if`/`else if` chain of `instanceof` tests:

```java
public void onEvent(MarketTick event) { processEvent(event); }        // ← call site knew the type
private void processEvent(Object event) {                             // ← and it is thrown away here
    if (processing) { callbackDispatcher.queueReentrantEvent(event); }
    else { processing = true; onEventInternal(event);
           callbackDispatcher.dispatchQueuedCallbacks(); processing = false; }
}
public void onEventInternal(Object event) {
    if (event instanceof LimitEvent) { … }            // ← 0.1% of events, tested FIRST
    else if (event instanceof MarketTick) { … }       // ← 75%, tested second
    else if (event instanceof TradeEvent) { … }       // ← 25%, tested third
    else if (event instanceof ClockStrategyEvent) { … }
    else { unKnownEventHandler(event); }
}
```

**And the hand-rolled arm does no type dispatch at all.** In `BenchMulti` the caller binds
`h.onTick(...)` / `h.onTrade(...)` statically, so it never asks what type the event is. The generated
arm is not beating a switch and losing — **it is doing work the hand-rolled arm never does**: widening
a known type to `Object`, then re-deriving it with up to three `instanceof` tests and a cast.

So the honest framing of the 0.309 ns gap (1.681 generated − 1.372 hand-rolled) is *"a typed call site
is thrown away and reconstructed"*, not *"switch versus if"*.

**Two further facts, checked rather than assumed:**

- **The `instanceof` order is declaration/alphabetical, not frequency.** `LimitEvent`, at ~0.1% of the
  stream, is tested on every single event before `MarketTick` at ~75%.
- **The callback drain is already optimised.** W2 is present on this branch:
  `dispatchQueuedCallbacks()` returns early on `eventProcessor == null || myStack.isEmpty()`, the queue
  is an `ArrayDeque<BooleanSupplier>`, and the unconditional `dispatching = false` store is gone. So
  the re-entrancy machinery is *not* obviously unoptimised, which is what the next section has to test.

---

## 1. Predictions — committed before running anything in §3

Each is falsifiable and carries a number. Confidence is stated so that a wrong high-confidence
prediction is worth more than a wrong hedge.

| # | Prediction | Confidence |
|---|---|---|
| **P1** | Making the typed entry call `handleEvent` **directly**, keeping the re-entrancy guard, recovers **≥ 0.15 ns** of the 0.309 ns gap — landing at **≤ 1.53 ns**. The widen-and-re-narrow is the largest single removable cost. | high |
| **P2** | Reordering the `instanceof` chain so the **most frequent type is tested first** is worth **< 0.05 ns** — effectively nothing. A correctly-predicted not-taken branch is nearly free, and the pattern here is perfectly predictable. *This contradicts the intuitive "put the common case first", which is why it is worth measuring.* | medium |
| **P3** | Giving the hand-rolled arm the **same `Object` entry point and its own `instanceof` chain** — the fair comparison, and what a real system actually has — collapses the gap to **< 0.10 ns**. Most of the 0.309 is the generated side doing dispatch the hand-rolled side was being spared. | high |
| **P4** | On the owner's crossover hypothesis: with both arms doing `Object` dispatch, **there is no crossover in the generated code's favour from an `if` chain alone**, at any N. Both are O(N/2) expected tests and scale together. A crossover requires the generator to emit something better than a chain — **a `switch` on an int type-id would beat the chain for N ≥ 8** and be indistinguishable below ~4. | medium |
| **P5** | The re-entrancy guard plus the (already W2-guarded) drain costs **< 0.10 ns** combined: one predictable branch, two stores, and an inlined call that returns immediately. **It is not where the money is.** | medium |
| **P6** | Combining P1 with the shipped configuration leaves the generated arm **within 0.15 ns of a fair hand-rolled arm** on the three-type graph — i.e. the remaining gap is the node-call structure, not dispatch. | low |

**What would falsify the round's thesis:** if P1 recovers < 0.05 ns, then the widen/re-narrow is not
the cost and the gap is somewhere I have not looked — most likely the ten virtual calls in
`handleEvent` versus ten inlined statements.

---

## 2. Results — JIT, 3 interleaved reps, 100M mixed events, all arms output-verified identical

| case | r1 | r2 | r3 | mean | vs baseline |
|---|---|---|---|---|---|
| baseline, typed entry | 5.5977 | 5.5795 | 5.5695 | **5.5822** | — |
| **X1** typed entry → `handleEvent` direct, guard kept | 5.5769 | 5.6316 | 5.6463 | **5.6183** | **+0.036** |
| **X2** `instanceof` chain in frequency order | 5.5022 | 5.6221 | 5.5848 | **5.5697** | **−0.013** |
| **X5** direct, **no guard, no drain** | 5.1798 | 5.1529 | 5.2963 | **5.2097** | **−0.373** |
| generated entered via `Object` | 5.9067 | 5.9338 | 5.9656 | **5.9354** | +0.353 |
| **hand-rolled via `Object`** (fair) | 4.0301 | 4.2511 | 4.0249 | **4.1020** | −1.480 |
| hand-rolled, statically bound | 2.6961 | 2.7063 | 2.7116 | **2.7047** | −2.878 |

## 3. Scoring the predictions — 1 right, 4 wrong, 1 partly tested

| # | predicted | measured | verdict |
|---|---|---|---|
| **P1** | direct dispatch recovers ≥ 0.15 ns | **+0.036 ns — very slightly worse** | **WRONG**, and it was high confidence |
| **P2** | frequency ordering worth < 0.05 ns | −0.0125 ns | **RIGHT** — the counter-intuitive one |
| **P3** | fair comparison collapses the gap to < 0.10 ns | **1.833 ns** | **WRONG**, high confidence |
| **P4** | no crossover from an `if` chain alone | not yet tested at varying N — but see §3.2 | **partly** |
| **P5** | guard + drain costs < 0.10 ns | **0.409 ns** (X1 5.6183 − X5 5.2097) | **WRONG by 4×** |
| **P6** | direct dispatch lands within 0.15 ns of fair hand | 1.833 ns | **WRONG** |

### 3.1 The re-entrancy guard is the cost, and I predicted it was not

**This is the round's finding and it is the opposite of P1/P5.** Removing the widen-and-re-narrow buys
**nothing** — the JIT was already eliminating it. Removing the **guard and the drain call** buys
**0.409 ns**, which on a 5.58 ns baseline is 7% and the largest single removable dispatch cost measured
in three rounds.

The owner's question — *"is the re-entrancy guard optimised?"* — has a sharper answer than the one I
gave before measuring. The **drain** is optimised: W2's early return on `myStack.isEmpty()` is present
on this branch. The **guard around it is not the cheap thing I assumed**: `processing` is a real field
on a real object, so it is a load, a predictable branch, two stores, and a non-inlined call into
`CallbackDispatcherImpl` that immediately returns. 0.409 ns for machinery that does nothing on 100% of
events in this benchmark.

**That is a live optimisation target and it is W4's, not W1's.** X5 is not shippable — it removes
re-entrancy entirely — but the measurement says the prize is real and worth designing for: a guard that
costs nothing when the graph provably never re-enters, without giving up the loud failure when it does.

### 3.2 The fair comparison, and the owner's crossover hypothesis

*"I suspect there is a point when depth and number of event types balance towards generated."*

The fair comparison moves a long way in that direction, though it does not cross:

| | generated | hand-rolled | gap |
|---|---|---|---|
| hand-rolled given the type by the call site (**unfair**) | 5.582 | 2.705 | 2.878 |
| both entered through `Object` (**fair**) | 5.935 | 4.102 | 1.833 |

**Making it fair closes 1.05 ns of a 2.88 ns gap**, because dispatch costs the two sides very
differently: adding an `Object` entry costs the hand-rolled arm **1.397 ns** and the generated arm only
**0.353 ns**. The generated `instanceof` chain is roughly **four times cheaper per event** than the
hand-written one that does the same thing at the same three types.

**Why is not yet established** — the likely cause is that the hand-written `onEvent(Object)` adds a
call layer the JIT does not fully inline through, where the generated chain is already inside the path
the compiler has been forced to inline. **That is a hypothesis, not a result**, and it is the next
thing to measure rather than to assert.

**What it means for the crossover:** at three types the generated side is still 1.83 ns behind, so
there is no crossover here. But the *slope* is in its favour — every additional event type costs the
hand-written chain about 4× what it costs the generated one. P4 said an `if` chain cannot produce a
crossover because both are O(N/2); that reasoning ignored the per-test constant, which is not equal.
**P4 is not yet falsified but its argument is already wrong.**

### 3.3 What this round changes about where to spend effort

1. **Stop trying to remove the type dispatch.** Frequency ordering: nothing. Direct typed dispatch:
   nothing. The JIT already handles both.
2. **The re-entrancy guard and its drain call are worth 0.409 ns** — measure a design that keeps the
   capability and the loud failure while costing less.
3. **Quote the fair comparison.** The 22.5% figure published earlier today compares a processor that
   dispatches against hand-written code that was handed the answer. Entered the same way, the honest
   multi-type gap is 5.935 against 4.102.
4. **Test the crossover properly** — 2, 4, 8, 16 event types, both arms through `Object`.
