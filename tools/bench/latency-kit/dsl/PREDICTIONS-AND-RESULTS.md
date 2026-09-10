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

## P15 · groupBy at cardinality — predicted BEFORE measuring

M55.2. The 6.4x groupBy result was measured over **four** keys against a C++ store that was a linear
scan, so the shape was O(groups) while Java's `HashMap` was O(1). The C++ store is now split the way
Java's is — an insertion-ordered vector that IS the emit order, plus a hash index beside it that nothing
iterates — because `GroupByFlowFunctionWrapper` says first-key-seen order is a guarantee, not an
accident. Predictions, recorded before the controls were built:

- **P15a — at 4 keys the hash index is no better, and may be slightly WORSE.** A four-element scan of
  contiguous PODs is cache-resident and branch-predictable; `unordered_map` costs a hash and a pointer
  chase. If the index shows a win at 4 keys I have mismodelled the scan.
- **P15b — the OLD C++ store LOSES to Java somewhere below 1024 keys.** This is the sharp one. Java is
  O(1) per event and the old C++ was O(groups), so there is a crossover, and the 6.4x headline is an
  artefact of sitting far to the left of it. Predicted crossover: **between 32 and 128 keys.**
- **P15c — with the index, the ratio stops depending on cardinality** and lands near the plain-chain
  ratio, because both sides are then O(1) per event and what remains is dispatch and boxing.

If P15b is wrong in the direction of "C++ still wins at 1024", then Java's per-event boxing costs more
than a linear scan of a thousand entries, which would be worth knowing on its own.

### P15 scored — measured 2026-09-09

Both arms, 2M events per batch, best of 3, `-O3` C++ against JIT Java, DEFAULT profile, **checksums
identical at every cardinality** (they are in the output, and they differ per key count because key 0
takes a shrinking share of the events — a constant checksum across key counts would have meant the
store was not really grouping).

| keys | Java JIT | C++ linear scan | C++ hash index | index vs Java |
|---:|---:|---:|---:|---:|
| 4 | 30.55 | **4.15** | 5.86 | 5.2x |
| 16 | 31.66 | 4.27 | **4.31** | 7.3x |
| 64 | 31.43 | 11.11 | **3.78** | 8.3x |
| 256 | 32.04 | 38.16 | **3.74** | 8.6x |
| 1024 | 29.92 | 126.58 | **3.67** | 8.2x |

- **P15a — RIGHT.** At 4 keys the index is not better, it is **41% worse** (4.15 → 5.86). A four-element
  scan of contiguous PODs beats a hash and a pointer chase, as predicted.
- **P15b — WRONG, in the direction that matters least.** There IS a crossover and the old store does
  lose to Java, as predicted — but between **64 and 256** keys, not the 32–128 I wrote down. At 64 keys
  the scan was still winning 11.1 vs 31.4. I put the crossover about 2x too early.
- **P15c — RIGHT about the shape, WRONG about the level.** The indexed ratio does stop depending on
  cardinality. It does not land near the plain chain's ~4x: it lands at **8x** and, unexpectedly, the
  C++ arm gets FASTER as cardinality rises (5.86 → 3.67).

**The unexpected result is that one.** More groups should not make the C++ arm quicker. The likely cause
is a serial dependency: at 4 keys, consecutive events hit the SAME `Entry` and each `acc_ +=` waits on
the previous store, while at 1024 keys consecutive events touch different entries and the accumulates
pipeline independently. That is a hypothesis consistent with the shape of the curve and it has **not
been measured** — it is recorded as the next thing to test, not as a finding.

**What this says about the headline.** The 6.4x groupBy figure from M54.4 was measured at four keys
against the scan, and it is now clear it described the benchmark's cardinality rather than the target.
The honest statement is a curve, not a number: with the indexed store the C++ groupBy is 5–8.6x the
Java DSL across 4–1024 keys, and with the old store it ranged from 7.4x FASTER to 4.2x SLOWER over the
same range.

### P13/P14 scored — measured 2026-09-09, `build-shape-controls.sh`

Repeatable minimum of 4 reps per arm, 3M events per batch, 4 batches, `-O3` C++ against JIT Java,
DEFAULT profile. **All four shapes checksum −6250000**, which is the cross-check that they compute the
same total.

| shape | Java JIT | C++ | ratio |
|---|---:|---:|---:|
| plain (reference: mapToInt → aggregate) | 10.64 | 0.620 | 17.2x |
| merge | 15.69 | 2.427 | 6.5x |
| notify | 13.35 | 0.747 | 17.9x |
| mapOnNotify | 13.15 | 0.743 | 17.7x |

**P14 — RIGHT, and cleanly so.** notify and mapOnNotify share every node but their last, so a difference
between them is a difference between the constructs. C++ 0.7465 vs 0.7432 and Java 13.35 vs 13.15 — 0.4%
and 1.5%, both within run-to-run noise and not consistently signed across reps. The extra `get()` that
returns the notified node instead of the parent value is free, as predicted.

**P13 — NOT CLEANLY ANSWERABLE FROM THIS CONTROL, and the fault is in the control.** The merge shape is
two filtered flows joined by a merge; the plain reference is one map. So the 6.5x reflects a graph with
two filters, two subscription paths AND a merge, and the difference from the reference is not merge's
cost. Two things can still be said honestly:

- The prediction "well below groupBy's 6.4x" is **wrong as stated** — the merge shape measures 6.5x,
  level with it, not below.
- The prediction "near the plain-chain ratio, ~4x" is wrong in both parts: the plain chain in THIS kit
  measures 17.2x, and merge is far below it rather than near it.

A clean isolation needs a shape with merge's node count that does not merge, and I do not have one. What
the numbers do show is where the cost lands: merge adds 5.05ns to Java and 1.81ns to C++, so in absolute
terms C++ pays less — but against a 0.62ns baseline that is a near-quadrupling, which is what drags the
ratio down. **A ratio against a baseline this small is mostly a statement about the baseline.**

**Two cautions that belong with this table.** The 17.2x plain figure is NOT comparable to the 3.96x
recorded elsewhere in this kit: that control is `map → map → filter → aggregate` under
`LOWEST_LATENCY`, this one is `mapToInt → aggregate` under `DEFAULT`. Two nodes fully inlined to ~0.62ns
is about two cycles, and quoting it as a headline would repeat the mistake the 23x withdrawal was about.
And the first measurement pass interleaved the Java and C++ arms, which inflated every C++ number by
roughly 2x (notify read 1.59 interleaved, 0.75 alone). The numbers above are from arms run
without the other language in the same pass.

## P16 · why the indexed groupBy speeds up with cardinality — predicted before measuring

§46 left an unexplained result: with the hash index, the C++ groupBy gets FASTER as the key count rises
(5.86 → 3.67 ns from 4 to 1024 keys). The hypothesis was a serial dependency — at four keys consecutive
events hit the same `Entry` and each `acc_ +=` waits on the previous store to land, while at a thousand
they touch different entries and the accumulates pipeline.

The confound in the cardinality sweep is that TWO things changed together: the store got bigger, and
consecutive events stopped colliding. The test separates them by holding the store at **1024 entries in
both arms** and varying only the access pattern:

- **round-robin** — `key = i % 1024`, consecutive events touch different entries
- **single key** — `key = 0` always, after warming all 1024 so the store and the hash probe are identical

- **P16a — single-key is SLOWER than round-robin in C++**, by roughly the 2ns the sweep showed. That is
  the hypothesis: same store, same lookup, only the dependency chain differs.
- **P16b — Java shows the same effect, and smaller in relative terms.** Java has the same read-modify-
  write on one accumulator, so the dependency exists there too, but it is a smaller share of a ~30ns
  event.

If P16a comes back flat, the hypothesis is wrong and the cardinality effect is something else — most
likely the hash probe's branch behaviour, which is more predictable when the answer keeps changing than
this reasoning assumes.

### P16 scored — REFUTED, and it refuted the finding it was invented to explain

Store held at 1024 entries in both arms, only the access pattern varying, C++, 4 reps, minimum:

| pattern | ns |
|---|---:|
| round-robin (`key = i % 1024`) | 3.6695 |
| single key (`key = 0`, all 1024 warmed) | 3.5845 |

**P16a — WRONG.** Hammering one key is not slower. It is marginally *faster*, which is the opposite of
a serial-dependency stall and is consistent with nothing more interesting than one hot cache line.
P16b was not worth running once P16a failed.

**And then the effect itself failed.** If the dependency chain is not the cause, what is? The dullest
candidate: `keys=4` was simply the FIRST point in the sweep. Re-running the sweep in reverse order with
four reps per point and no Java arm interleaved gives a flat curve — 3.55, 3.71, 3.74, 4.11, 3.82 from
1024 down to 4. **The indexed store does not speed up with cardinality. It is flat, as an O(1)
structure should be, and the original 5.86 → 3.67 was a cold first measurement.**

### P15 — RE-SCORED on repeatable minima, superseding the table above

The first M55.2 table was single-shot per point, in ascending key order, with the Java and C++ arms
interleaved. All three of those are wrong, and the third inflates C++ by roughly 2x. Re-measured with
3–4 reps per point, minimum, arms run separately, keys descending:

| keys | Java JIT | C++ linear scan | C++ hash index | index vs Java |
|---:|---:|---:|---:|---:|
| 4 | 25.43 | **2.17** | 3.82 | 6.7x |
| 16 | 27.85 | **3.38** | 4.11 | 6.8x |
| 64 | 27.65 | 11.00 | **3.74** | 7.4x |
| 256 | 31.07 | 37.57 | **3.71** | 8.4x |
| 1024 | 30.62 | 126.49 | **3.55** | 8.6x |

What survives, and what does not:

- **The scan is O(groups)** — 2.17 to 126.49. Confirmed, and more starkly than before.
- **The crossover is still between 64 and 256 keys** (scan 11.00 vs Java 27.65; scan 37.57 vs Java
  31.07), so **P15b remains wrong in exactly the same way** — predicted 32–128.
- **P15a is right, and by more than recorded**: at four keys the index is 76% slower than the scan
  (3.82 vs 2.17), not 41%.
- **P15c is right about the shape and the level is now 6.7–8.6x**, not 5–8.6x.
- **The anomaly is withdrawn.** There was nothing to explain.

**The lesson is the kit's own rule, ignored by me.** `control-bands.tsv` says bands are "the REPEATABLE
minimum from measure.sh (3 batches x 6 reps, gated on CV of the batch minima)". I took single shots, in
one order, with the arms interleaved, then wrote a microarchitectural story to explain the shape that
produced. The story was plausible, which is precisely why it was worth testing rather than recording.

## P17 · Java native AOT on the audit path — predicted, and NOT YET MEASURED

**Every audit figure in this kit is Java JIT.** `build-shape-controls.sh` now builds a native arm with
PGO, gated on `GRAALVM_HOME` and skipped loudly when it is unset — which is the state of this machine:
there is no `native-image` on it, and the Oracle GraalVM 25.0.4 that `control-bands.tsv` records is
gone. So the arm exists and has never run.

That matters more here than usual. The recorded dispatch figures have Java at **12.44 ns JIT against
2.05 native** — a different performance class — so a JIT-only audit comparison may be describing the
JIT rather than the language.

- **P17a — Java native AOT lands NEAR C++ on audit cost, not far below it.** C++ is 13.17 ns/event and
  Java JIT 18.83. Of Java's, about 8.4 ns is the clock read, which native cannot make cheaper: it is a
  `System.nanoTime()` and the same instruction either way. So the ~10.4 ns of record work is all that
  is available, and even halving it lands at ~13.6 — level with C++, because **both are then dominated
  by a timestamp neither can avoid.**
- **P17b — the flatMap gap closes far more than the plain one.** The re-entrant sharing is worth ~20 ns
  to Java JIT and nothing measurable to C++, which is consistent with the read being latency the larger
  event hides. If native hides it the way C++ does, that 20 ns shrinks — and if it does not, the
  latency-hiding story is wrong and worth abandoning.

If P17a comes back with native FAR below C++, then the clock is not the floor I think it is and the
whole "both pay for a timestamp" framing needs re-examining.

### P17 scored — measured 2026-09-10, Oracle GraalVM 25.0.4+7.1

Installed to measure this (the machine had none, despite `control-bands.tsv` naming one — it is the
same version the bands were recorded on, so these are comparable). Same shape, audited and unaudited,
four reps, minimum:

| arm | unaudited | audited | audit cost/event |
|---|---:|---:|---:|
| Java native AOT + PGO | 11.84 | 29.07 | **17.23 ns** |
| Java JIT | 13.39 | 30.55 | **17.16 ns** |
| C++ `-O3` | 0.63 | 14.72 | **14.09 ns** |

- **P17a — right about the outcome, wrong about the mechanism.** I predicted native would land near C++
  rather than far below it, reasoning that it would improve on JIT until the clock floor stopped it.
  It lands near C++ (17.23 against 14.09) — but **not by improving at all**. Native's audit cost is
  within noise of JIT's. The prediction was right for a reason that turned out not to be the reason.
- **Native barely helps this shape even unaudited: 13.39 → 11.84, about 12%.** That is worth stating
  next to the figure this kit already records — Java dispatch at *12.44 ns JIT against 2.05 native*, a
  6× gain. That was a different graph under `LOWEST_LATENCY`; this is `mapToInt → aggregate` under
  `DEFAULT`. **A 6× native advantage is not a property of the language, it is a property of that
  graph and that profile**, and quoting it as the former would be the withdrawn-23× mistake again.
- **P17b is unscored, and the reason is not the harness.** The flatMap native arm does not build, and
  the first explanation — `--gc=epsilon` never collects, flatMap allocates per element — was WRONG.
  Rebuilding with a collecting GC failed identically. Reading the log instead of guessing again:

    ```
    java.lang.NoSuchMethodException: app.gen.ShapeProcessor$$Lambda/…writeReplace()
      at LambdaReflection$MethodReferenceReflection.serialized(LambdaReflection.java:30)
      at FlatMapFlowFunction.<init>(FlatMapFlowFunction.java:48)
      at app.gen.ShapeProcessor.<init>
    ```

    **`FlatMapFlowFunction`'s CONSTRUCTOR reflects on a serialized lambda.** It calls
    `iterableFunction.captured()`, which calls `serialized()`, which does
    `getDeclaredMethod("writeReplace")` and invokes it. GraalVM does not emit `writeReplace` for lambda
    classes unless serialization is registered for them, so **a generated processor containing a
    flatMap cannot be CONSTRUCTED in a native image.** It fails at startup, not on the event path, and
    it is a property of Fluxtion rather than of this benchmark.

    Two ways out, neither taken here: register those lambdas for serialization in a native-image
    config, or have the generator pass the captured instance directly — it knows it at generation time,
    which is the same move that removed the audit key lookup.

**What this says about the audit path.** The cost is not code the JIT was failing to optimise — if it
were, AOT with a good profile would have moved it. Both Java arms sit at ~17 ns and C++ at 14 ns, and
about 8 ns of every one of those is a clock read. The remaining gap is small and the floor is shared.

### P17b scored — flatMap, once the native arm could be built at all

Built with a serialization config registering the lambda-capturing type, serial GC (epsilon cannot
serve a shape that allocates per element), PGO collected from a real run. **Not comparable to the plain
shape's native arm**, which uses epsilon.

| arm | flatMap, audited |
|---|---:|
| Java native AOT + PGO | 158.66 |
| Java JIT | 122.44 |
| C++ `-O3` | 75.62 |

**Native is 30% SLOWER than JIT on this shape**, which is the opposite of the direction the kit's
dispatch figures suggest. The plausible reason is the one the arm had to be built around: flatMap
allocates per element, so it is the shape most exposed to the collector, and the native arm runs serial
GC against a JIT with a generational collector. That is a hypothesis about the GC, not a measurement of
it — and it is a fair warning that "native is faster" is a claim about a workload, not a runtime.

### P13 re-scored — merge, isolated at last (M56.2, 2026-09-10)

The earlier attempt compared a merge SHAPE against the plain chain, and the shape carried two filters
and a second subscription the reference did not — so 6.5x measured a graph, not an operator.

The isolation that works needs no second topology: **the same graph, with a different number of merge
inputs firing.** Two builds, identical node counts and wiring (12 structs, 6 `inputStreamUpdated` sites
in both), differing only in the filter predicates — opposite filters so exactly one branch passes,
against two filters that both pass.

| | C++ | Java JIT |
|---|---:|---:|
| merge, one input per event | 2.402 | 15.836 |
| merge, two inputs per event | 2.721 | 16.299 |
| **a second merge input** | **0.319 ns** | **0.463 ns** |

**P13's reasoning is supported, and by a wider margin than the prediction guessed at.** The claim was
that merge is the case where neither side allocates, so C++'s advantage should be at its smallest.
Isolated, a merge input costs Java **1.45x** what it costs C++ — against **17x** on plain dispatch.
Merge is one of the operations where Java is closest to C++, which is what "neither side allocates"
predicts. The 6.5x figure was never about merge.

**Both C++ arms were REFUSED by the gate at first** — CV 2.25% and 3.24% against the 2% C++ limit —
and settled to 1.10% and 0.50% at 10 reps of 5M events. Worth stating rather than quietly using the
second run: this shape is noisier than the others, and a single-shot number for it would have been
luck.
