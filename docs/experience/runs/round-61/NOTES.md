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
