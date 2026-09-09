# Predictions — written BEFORE measuring, 2026-09-09

Baseline established this session, generated artefacts, no audit, checksum 488000000:

| arm | ns |
|---|---:|
| C++ generated, clang -O2, LOWEST_LATENCY | 3.351 / 3.359 |
| Java generated, JIT, LOWEST_LATENCY | 9.910 / 9.736 |
| Java generated, JIT, DEFAULT | 15.070 / 15.389 |

## P5 — Java native AOT vs Java JIT, on the DSL

**Predict: native is LEVEL WITH OR SLOWER THAN JIT — 9–13 ns, i.e. 0.9–1.3x of JIT.**

Reasoning: a DSL graph is a deep chain of small flow-function objects. The JVM's advantage here is
escape analysis on a processor that does not escape its loop, and that is precisely the bargain a
closed-world AOT compiler does not strike the same way. Earlier in this round the hand-rolled DSL
bench was the ONLY arm where native lost to JIT (10.821 against 9.853); if that was a property of the
shape rather than of that particular harness, it should reproduce on the generated artefact.

**This is the prediction I hold most weakly.** Every other Fluxtion arm measured this round has native
ahead, often by 3-6x, and one contrary data point on a hand-rolled bench is thin evidence.

## P6 — C++ compiler options beyond -O2

**Predict: total improvement 0–20%, landing around 2.8–3.3 ns. No step change.**

Per option:

- **`-O3`**: 0–5%. The whole chain is header-only in one translation unit and already fully inlined at
  -O2; -O3 mostly buys vectorisation and aggressive unrolling, and there is no loop here to vectorise —
  the work is a dependent chain of scalar int operations.
- **`-march=native`**: 0–3%. Scalar 32-bit integer arithmetic and predictable branches. Apple M4 has
  nothing in a wider ISA that helps this.
- **`-flto`**: ~0%. Everything is already in one TU; there is nothing cross-module to inline.
- **PGO (`-fprofile-generate` / `-fprofile-use`)**: **5–15%, the largest of the four.** The filter
  rejects a little over half the events and the pattern is periodic (`(i & 15) - 8`), so the branch is
  predictable in principle but the compiler cannot know the direction bias without a profile. This is
  the one option with a real mechanism to exploit.

**The reason none of these should be a step change:** 3.35 ns is not compiler slack, it is the
framework protocol — a dirty flag per node, a `guardCheck_` per node, an `inputUpdated` per edge. That
is real work expressing the DSL's semantics, and no optimisation flag can remove work that has to
happen. If any option shows a large win, the first hypothesis should be that it deleted something the
checksum does not cover, not that the protocol was free.

## P7 — will the checksum hold across every arm?

**Predict: yes, 488000000 everywhere.** Stated because it is the cheap check that has caught three
invalid comparisons this round, and because an optimisation that changes the answer is the failure
mode these flags actually have.

---

# Scored, after measuring — 20M events, min-of-6, 2 reps, every arm checksum 488000000

## C++ compiler options

| variant | rep1 | rep2 |
|---|---:|---:|
| `-O2` | 3.528 | 3.566 |
| `-O3` | **2.761** | **2.757** |
| `-O3 -march=native` | 2.770 | 2.785 |
| `-O3 -march=native -flto` | 2.775 | 2.782 |
| PGO (`-O3 -march=native`) | 2.931 | 2.918 |

## Java

| arm | rep1 | rep2 |
|---|---:|---:|
| JIT (OpenJDK 25.0.2) | 10.751 | 10.421 |
| native AOT, PGO + epsilon | 11.058 | 11.036 |
| native AOT + `-H:-SpawnIsolates` | 10.536 | **10.398** |

## Scorecard

| # | prediction | outcome |
|---|---|---|
| P5 | native AOT level with or slower than JIT, 0.9–1.3x | **CONFIRMED** — 11.04 against 10.42, ~1.06x; level at 10.40 with isolates off |
| P6a | `-O3` worth 0–5% | **WRONG** — worth **22%** (3.55 → 2.76), the largest single win |
| P6b | `-march=native` worth 0–3% | confirmed — nothing (2.77 against 2.76) |
| P6c | `-flto` ~0% | confirmed — nothing, everything is one TU already |
| P6d | PGO the largest win, 5–15% | **WRONG, AND BACKWARDS** — PGO is **6% SLOWER** (2.93 against 2.76) |
| P6e | total 0–20%, landing 2.8–3.3 ns | range caught it at 2.76, but for the wrong reason |
| P7 | checksum 488000000 everywhere | confirmed, 16 arms |

## What the two wrong ones mean

**P6a — I asserted the chain was "already fully inlined at -O2" without checking.** It is not. The
generated DSL is a chain of templates each holding a pointer to the next, and -O2 leaves enough of that
un-inlined to cost 22%. This is the same error as reasoning about the emitter instead of reading the
emitted source: I reasoned about the optimiser instead of measuring it. **-O3 should be the documented
default for the C++ target.**

**P6d — PGO made it slower, and the reasoning that predicted a win is what was wrong.** I argued the
filter's branch is data-dependent and a profile would help. But the profile was collected on the SAME
periodic input the benchmark replays, so it is not new information — the branch predictor already has
it at run time. What PGO adds is layout and inlining decisions taken from an instrumented build, and
those cost more here than the branch hint saves. A profile that tells the compiler what the hardware
already knows is not free.

Both errors share a shape: I predicted from a model of the tool rather than from a measurement of it.
The predictions were still worth writing — being wrong in a recorded way is what made the -O3 result
worth acting on rather than passing over.

## Best of each, and the gap

- C++ generated DSL: **2.757 ns** (`-O3`)
- Java generated DSL: **10.398 ns** (native AOT, isolates off) / 10.421 JIT

**C++ is 3.8x the Java DSL** on the generated artefact. Not the 23x once claimed from a hand-written
stand-in, and not the 2.9x measured at `-O2` either.

## Not measured, and it will matter

Aggregates here are a single `int32_t` accumulator living inside the node struct — stack-resident, no
allocation, which is part of why the C++ arm is cheap. **Windowing will not be.** A sliding or tumbling
window needs buffers whose lifetime spans events, and the stack-versus-heap choice for those is a real
design decision with a real cost, not a translation detail. The figures above should not be read as
predicting what a windowed graph will do.

---

# P8 — written BEFORE the fix, 2026-09-09

**The change:** emit the four mutable trigger flags, and the clears of them in
`fireEventUpdateNotification()`, only for nodes that actually HAVE a trigger override. The emitter
already knows — the `Triggers` record reaches every struct builder and is all-false for most nodes.

**Baseline to beat:** C++ `-O3` at 3.343 / 3.363 ns. The figure before the trigger machinery was
**2.757**.

**Predict: it recovers most of the regression, landing 2.80–3.00 ns.**

Reasoning: the measured graph is `map -> map -> filter -> aggregate`, so four nodes carry flags and
none of them has an override. That is 16 stores per event that cannot affect any result. Removing work
that provably does nothing should return the arm close to where it was, and the gap between 2.757 and
3.35 is almost exactly what four-times-four dead stores would cost at this scale.

**The falsifier, and I hold it seriously.** If `-O3` had already eliminated those stores as dead — the
flags are private, written and read only within the struct, and the reads fold to constants — then the
0.6 ns is NOT the flags at all, and removing them buys ~0. In that case the regression came from
somewhere else the same commits introduced: `reset()` and `statefulFunction_` on every struct, wider
structs changing layout, or the extra `hasDefaultValue()` on every node. I would then have to bisect
rather than guess again.

**Predict the ratio:** 10.5 / 2.9 ≈ **3.6x**, against 3.1x now and 3.8x before.

**Predict Java is unchanged**, because none of this touches the Java arm — stated only so that if Java
moves, I know the measurement is not comparable rather than concluding something about the fix.

## P8 scored

| | predicted | measured |
|---|---|---|
| C++ `-O3` | 2.80–3.00 ns | **2.653 / 2.663** |
| ratio to Java | 3.6x | **4.0x** |
| Java unchanged | yes | 10.511 / 10.690 — yes |

**Direction right, magnitude under-predicted.** It recovered the entire 21% regression AND beat the
pre-M53 figure of 2.757. The falsifier — that `-O3` had already killed the stores and the cost lay
elsewhere — is refuted: the stores were real, and the compiler had not removed them despite the flags
being private and every read folding to a constant.

Worth keeping as the general lesson: **"the optimiser will handle it" is a prediction, not a fact.**
This is the second time in this kit it has been wrong in the same direction — the first was asserting
`-O2` already inlined the templated chain, where `-O3` then bought 22%.

The best C++ figure so far, on the artefact that ships, is **2.653 ns against Java's 10.51 — 4.0x**.

---

# P9–P11 — the shapes that were never measured. Written BEFORE measuring, 2026-09-09

Baseline: `map -> map -> filter -> aggregate`, C++ `-O3` **2.653 ns**, Java JIT **10.51**, ratio 4.0x.

## P9 — a tumbling window

**Predict C++ 2.5–3.5 ns, ratio 3.5–4.5x — i.e. broadly the same as the chain.**

A tumbling window embeds its accumulator in the node struct: no allocation, no buffer, and the roll
trigger is one clock read plus a comparison per event. The per-event work is a `FixedRateTrigger` read
on top of an aggregate, which is close to what the chain already does. **This is the prediction I hold
most strongly** — there is no mechanism here for a large difference.

## P10 — flatMap

**Predict C++ 40–120 ns, ratio WORSE than 1x — i.e. C++ may LOSE to Java.**

Per element it allocates a `std::function` into a `std::deque` and runs a whole graph cycle. Three
elements per event means three cycles plus three heap allocations, against a JVM whose allocator is a
pointer bump and whose escape analysis may remove the closure entirely. **This is the first shape where
I expect Java to be competitive or ahead**, and if it is, the honest read is that `std::function` +
`deque` is the wrong C++ structure rather than that C++ is slower — a fixed-capacity ring of PODs would
avoid both.

## P11 — groupBy

**Predict C++ 4–8 ns, ratio 2–4x — better than flatMap, worse than the chain.**

A `std::vector` scan over a handful of keys is cache-friendly and allocation happens only when a NEW key
appears, which is rare after warm-up. Against Java's `HashMap` with boxed `Integer` keys, C++ should
still lead. The linear scan is the risk: it is O(groups), and the benchmark has few groups, so this
figure will NOT generalise to high-cardinality grouping — a caveat worth recording whatever the number.

## P12 — the checksum holds on all three

**Predict yes.** Stated because it is the cheap check that has caught every invalid comparison in this
kit, and because two of these three shapes allocate, which is where a wrong answer would come from.

## P9–P12 scored — 5M events, min-of-4, two reps, checksums identical per shape

| shape | C++ `-O3` | Java JIT | ratio |
|---|---:|---:|---:|
| `map -> map -> filter -> aggregate` (baseline) | 2.653 | 10.51 | 4.0x |
| tumbling window | 2.618 / 2.818 | 6.907 / 6.950 | **2.5x** |
| **groupBy** | **2.295 / 2.360** | 14.791 / 15.038 | **6.4x** |
| flatMap (3 elements/event, `DEFAULT` profile) | 22.686 / 22.968 | 77.21 / 84.39 | **3.5x** |

**Three of four wrong, all in the same direction: I under-estimated C++ and over-estimated Java.**

| # | prediction | outcome |
|---|---|---|
| P9 | window C++ 2.5–3.5 ns | **right** |
| P9 | window ratio 3.5–4.5x | **wrong — 2.5x**, and not because C++ was slow. JAVA got FASTER (6.9 against 10.5 on the chain): a window aggregates on the input path and only publishes on a roll, so most events do less work downstream. I predicted the C++ number and forgot the Java one moves too. |
| P10 | flatMap C++ 40–120 ns | **wrong — 22.7**, half the bottom of my range |
| P10 | flatMap: C++ may LOSE to Java | **wrong, and backwards — C++ wins 3.5x.** I argued a JVM pointer-bump allocator plus escape analysis would beat `std::function` in a `std::deque`. It does not: Java is 77–84 ns for three cycles an event. |
| P11 | groupBy C++ 4–8 ns | **wrong — 2.30, FASTER than the baseline chain** |
| P11 | groupBy ratio 2–4x | **wrong — 6.4x, the best result in the kit** |
| P12 | checksums hold | **right**, all three shapes |

### The finding I would keep

**Where Java allocates is where C++ wins biggest, and I predicted the opposite twice.** groupBy is the
best ratio in the whole kit (6.4x) because Java pays for boxed `Integer` keys in a `HashMap` on every
event, while the C++ side scans a small insertion-ordered vector of PODs and allocates only when a new
key appears. flatMap is the same story one level up.

I reasoned that allocation was C++'s weakness because it is the thing C++ makes you think about. It is
Java's, because it is the thing Java lets you not think about.

### Two caveats that belong with these numbers

- **groupBy's 6.4x will not hold at high cardinality.** The store is a linear scan and the benchmark has
  four keys. It is O(groups), so a graph grouping by thousands of keys inverts the argument — and that
  is a real workload, not a corner.
- **flatMap runs on `DEFAULT`, not `LOWEST_LATENCY`.** That profile drops node-name lookup, and
  `FlatMapFlowFunction` extends `BaseNode` which needs its context injected — refused at build time by
  `RequiredCapabilityCheck`. Both targets use `DEFAULT`, so the comparison is sound, but the absolute
  numbers are not comparable to the other rows.

## P13–P14 · merge and mapOnNotify — emitted 2026-09-09, NOT measured

M55.1 added both constructs to the C++ target. **No control build covers either**, so neither appears
in `build-dsl-controls.sh` and no band in `control-bands.tsv` constrains them. They are proven correct
by audit-oracle chains and are unmeasured — recorded here so the gap is stated rather than inferred
from the absence of a row.

Predictions, to be scored when a control is built:

- **P13 — merge lands near the plain-chain ratio (~4x), well below groupBy's 6.4x.** The reasoning that
  was wrong twice before is now the reasoning here: C++ wins biggest where Java allocates, and merge is
  the case where **neither side allocates**. Java stores a reference into a field; C++ stores a
  `const void*` into a field. Both then test it against null. If the ratio comes back near groupBy's,
  the allocation story is not doing the work I think it is.
- **P14 — mapOnNotify is indistinguishable from notify.** Same struct, same two audit entries, one
  extra pointer returned from `get()`. Predicted difference: none outside noise. This one is worth
  measuring precisely BECAUSE it should be null — a difference would mean the extra `get()` is not
  free, and that would say something about the templated-parent design rather than about mapOnNotify.

A caveat that applies to P13 and belongs with it: **the C++ merge stores a pointer INTO the event**.
That is sound while the value is read within the cycle, which is the only time the generated dispatch
reads it, but it is not the same lifetime story as Java's reference to a heap object. Any future shape
that holds a merged value ACROSS cycles needs that revisited before a number is quoted for it.
