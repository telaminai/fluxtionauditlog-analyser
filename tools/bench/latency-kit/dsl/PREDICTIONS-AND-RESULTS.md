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
