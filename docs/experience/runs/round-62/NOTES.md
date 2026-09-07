# Round 62 — build-time node selection vs runtime selection (the "Babylon-style" claim)

**Predictions committed before any measurement**, as in round 61. **Runtime** Oracle GraalVM 25.0.4+7.1,
macOS/aarch64. **Harness** `tools/bench/latency-kit`.

---

## 0. The claim being tested

Owner, 2026-09-07:

> *"You are going to use a builder that selects a node based on the build context — let's say a matrix
> multiplication. We have a number of matrix multiplication classes and orders, we specify the matrix
> size at build time and select the correct one. The hand-rolled must select the matrix class at
> runtime or just perform a basic multiplication regardless of size. We burn in the branch prediction
> and optimal size in the generated, whereas hand-rolled is conditional with no optimisation
> available."*

This is the first axis of [[specialised-generation-beats-handrolled]] made falsifiable. The generator
knows the matrix order **at build time**, so it can emit a node whose arithmetic is fully unrolled for
that order and whose field type is concrete. A hand-written *library* cannot: it must either dispatch
to an implementation chosen at runtime, or run a generic loop that handles any order.

## 1. The four arms

| arm | what it models |
|---|---|
| **generated** | builder picks `Mat4Node` because the build context says order 4. Concrete field, unrolled arithmetic, no interface |
| **hand-runtime** | a library: `MatMulNode` interface field, implementation chosen at construction, **several implementations loaded** so the call site is genuinely polymorphic |
| **hand-generic** | a library that does not specialise at all: one triple-nested loop over `n` |
| **hand-fixed** | the control nobody gets in a library: a human who *knew* the order was 4 and hand-wrote the unrolled version |

**`hand-fixed` is the honest ceiling** and it matters. If generated ≈ hand-fixed, the generator has
delivered what a human could only manage by abandoning generality. If hand-runtime ≈ hand-fixed, the
JIT devirtualised the call and the claimed advantage is not there on that runtime.

## 2. Predictions

| # | Prediction | Confidence |
|---|---|---|
| **Q1** | **On the JIT, `hand-runtime` will be much closer to `hand-fixed` than the claim expects** — with a handful of implementations the JIT devirtualises and inlines a bimorphic/polymorphic site well. Predict `hand-runtime` within **25%** of `hand-fixed`. | medium |
| **Q2** | **On native + PGO the gap widens in the generator's favour**, because closed-world AOT has no runtime profile to speculate on beyond what PGO recorded. Predict `hand-runtime` is **≥ 1.5×** `hand-fixed` natively — a bigger multiple than on the JIT. | medium |
| **Q3** | `generated` ≈ `hand-fixed` on both runtimes, within **10%**. The generator's specialisation should reach the same code a human writes when they know the order. | high |
| **Q4** | `hand-generic` is the worst arm on both runtimes by a wide margin — **≥ 2×** `hand-fixed` — because the loop bounds and index arithmetic cannot be constant-folded. | high |
| **Q5** | **The ranking `generated ≈ hand-fixed < hand-runtime < hand-generic` holds on both runtimes**, and the *interesting* result would be Q2: the same source ordering, but a wider spread on AOT than on JIT. That is the shape the Babylon argument predicts. | medium |

**What would falsify the claim as stated:** if `hand-runtime` matches `generated` on native + PGO, then
build-time selection buys nothing that PGO does not already recover, and the advantage is confined to
the second axis (code-model specialisation of the node body) rather than node selection.

---

## 3. JIT results, and a harness defect found by a wrong prediction

| arm | mean ns | vs fixed |
|---|---|---|
| fixed *(human, unrolled — not a library)* | 11.5264 | 1.00× |
| **generated** *(build-time selected)* | **11.8212** | **1.03×** |
| runtimeMono | 12.1300 | 1.05× |
| runtimePoly *(4 receivers, unpredictable)* | 13.6061 | 1.18× |
| **generic** *(order not known)* | **36.9636** | **3.21×** |

**Q1 ✓ Q3 ✓ Q4 ✓ Q5 ✓ on the JIT.** The claim holds and by a wide margin: the generated processor is
**3.13× faster than the library that cannot know the order**, and within 3% of a human who hand-unrolled
and abandoned generality to do it.

**Q4 only scored right after a harness fix, and the bug was the mechanism Q4 predicted.** The first
`Generic` arm declared `private final int n = 4;` — a compile-time constant, which javac and the JIT
fold, unrolling the loop into exactly the specialised code the arm existed to be slower than. It
measured **1.03×** and the round nearly concluded generic loops are free. Taking `n` from the
constructor moved it to **3.21×**. *I predicted constant-folding would matter and then wrote a constant
into my own control arm.*

**The owner's branch-prediction constraint is what makes the dispatch axis visible.** `runtimeMono` is
only 5% behind because a monomorphic site is devirtualised outright; `runtimePoly` costs 18%. A real
library pays both penalties — polymorphic dispatch *and* an unspecialised body.

## 4. Predictions for native + PGO — committed before the run reported

`LOWEST_LATENCY`, the generated inlining directive, and a PGO profile collected for **every** arm.

| # | Prediction | Confidence |
|---|---|---|
| **R1** | `fixed` and `generated` land within **5%** of each other, as on the JIT. | high |
| **R2** | **§2's Q2 will FAIL.** `runtimePoly` will *not* be ≥1.5× `fixed` natively; predict **≤ 1.25×**, i.e. no worse than the JIT's 1.18× and possibly better. Reason: `-H:MaxPolymorphicDispatches` defaults to **4** and this site has exactly 4 receivers, so PGO hands Graal a complete type profile and it can emit a guarded inline cascade covering all of them. **Closed-world AOT may handle this site *better* than the JIT, not worse.** | medium |
| **R3** | `generic` stays **≥ 2.5×** `fixed`. PGO records which branches are taken; it does not constant-fold a loop bound that genuinely varies. Specialising the *body* is not something a profile recovers. | high |
| **R4** | Absolute figures land within **±20%** of the JIT's for the arithmetic-heavy arms. This work is 64 multiply-adds, not allocation, so scalar replacement — worth 3.3× on the dispatch benchmark — has almost nothing to win here. | medium |
| **R5** | The ranking is unchanged: `fixed ≈ generated < runtimeMono < runtimePoly ≪ generic`. | high |

**If R2 is right, it qualifies the Babylon argument in a way worth stating:** the *node-selection* axis
is largely recoverable by PGO when the receiver count is small, and the durable advantage is R3 — the
**specialised body**, which no profile can reconstruct. That would make the second axis the load-bearing
one, not the first.

---

## 4a. The Fluxtion compiler caught a semantic error the harness could not have

Worth recording separately, because it is the only error today caught by *the framework* rather than by
a measurement.

The first `Mat4Node` declared its state as ordinary fields:

```java
public final double[] a = new double[16], b = new double[16], out = new double[16];
```

Generation failed:

```
FLX-1009: cannot find a matching constructor for com.benchv.MatrixNodes$Mat4Node
          — the fields [a, b, out] look like node-local state rather than references to other nodes
```

**The compiler was right and I was wrong about the model.** In Fluxtion a node's non-transient fields
*are the graph edges* — they are how the compiler discovers what a node depends on. I had written them
as private implementation state, which is the habit from ordinary Java, and the model does not work
that way. `transient` is how a node says "this is state, not an edge".

**This is exactly the failure CLAUDE.md rule 6 is about** — *"this app's model of dispatch, audit
logging and propagation is only correct if the framework's is… every defect in the M21 topology work
came from inferring instead of reading"*. I spent the whole day insisting on measuring rather than
inferring, and then inferred the node model.

**And the diagnostic did its job**, which is worth saying given M46's finding that the framework's
problems are communication failures rather than correctness failures. This one reached the console,
named the offending fields, and named the likely cause in the same sentence — *"look like node-local
state rather than references to other nodes"*. It did not merely say "no matching constructor" and
leave me to guess; it told me which of my beliefs was wrong. **Two minutes to fix, because the message
contained the fix.** That is the standard the rest of the diagnostics work is aiming at, and a data
point that at least one of them is already there.

---

## 5. Native + PGO results — three of five predictions wrong, and the retraction was the worst of them

`LOWEST_LATENCY`, the generated inlining directive, PGO collected for every arm, 2 cycles, all arms
agreeing bit-for-bit.

| arm | native | vs fixed | JIT | vs fixed | native speed-up |
|---|---|---|---|---|---|
| fixed | **0.7656** | 1.00× | 11.5264 | 1.00× | **15.1×** |
| runtimeMono | 0.7299 | 0.95× | 12.1300 | 1.05× | 16.6× |
| **generated** | **1.1337** | **1.48×** | 11.8212 | 1.03× | 10.4× |
| **runtimePoly** | **7.3640** | **9.62×** | 13.6061 | 1.18× | 1.8× |
| **generic** | **30.0746** | **39.28×** | 36.9636 | 3.21× | 1.2× |

### 5.1 Scoring

| # | predicted | measured | verdict |
|---|---|---|---|
| R1 | fixed ≈ generated within 5% | **1.48×** | **WRONG** |
| **R2** | runtimePoly ≤ 1.25× fixed; AOT handles 4 receivers well | **9.62×** | **WRONG, and it was a retraction of a correct prediction** |
| R3 | generic ≥ 2.5× fixed | 39.3× | **RIGHT** |
| R4 | native within ±20% of JIT | 15× faster on `fixed` | **WRONG** |
| R5 | ranking unchanged | `runtimeMono` overtook `fixed`; `generated` fell behind both | **WRONG** |

**R2 is the one to learn from.** §2's Q2 predicted `runtimePoly ≥ 1.5×` natively. An hour later I
retracted it on a plausible-sounding argument — `MaxPolymorphicDispatches` defaults to 4, the site has
exactly 4 receivers, so PGO should let Graal inline the cascade. **Measured: 9.62×.** The original
prediction was right and the reasoning that overturned it was wrong. *Reading an option's default and
constructing a mechanism from it is exactly the move that failed nine times in round 61.*

### 5.2 What the numbers actually say — and it is the owner's thesis, larger than predicted

**Read the last column.** Native + PGO makes the *specialised* arithmetic **15× faster** than the JIT
managed. It makes the *generic* loop **1.2×** faster. The compiler can only optimise what is statically
apparent, and 4×4 unrolled arithmetic is; a loop over a runtime `n` is not.

That produces the result the round was built to test, and it is far larger on AOT than on the JIT:

| what the code cannot know at build time | JIT penalty | **AOT penalty** |
|---|---|---|
| which implementation (4 receivers, unpredictable) | 1.18× | **9.62×** |
| the matrix order (generic loop) | 3.21× | **39.3×** |

**Closed-world AOT punishes late binding an order of magnitude harder than a JIT does**, because the
JIT can speculate on what it observes at runtime and the AOT compiler cannot go beyond what the profile
recorded. Every unresolved decision that a generator could have made at build time is paid for, at
native speed, for ever.

**So the Babylon-style argument is not merely supported; the JIT measurement understated it by roughly
8×.** The generated processor is not competing with `fixed` — a human who knew the order and gave up
generality — it is competing with what a *library* can ship, and that is 9.6× or 39× behind.

### 5.3 The one honest debit: the generated processor pays 1.48× fixed

`generated` at 1.1337 against `fixed` at 0.7656 is **+0.368 ns**, and that is the Fluxtion event
wrapper — `onEvent → processEvent → guard → onEventInternal → instanceof → handleEvent → mat.onEvent`.
It is consistent with the 0.12–0.43 ns of dispatch overhead measured all day on a different graph.

It is a real cost and it should be quoted, not buried: **a processor costs about 0.37 ns more per event
than a bare method call doing the same arithmetic.** Against 6.6 ns for late-bound dispatch or 29 ns for
an unspecialised body, it is the cheap part — but it is the part this project can still shrink, and
rounds 61's W1 is where that work lives.

---

## 6. Two follow-up tests — predictions committed before building

### 6.1 The receiver-count cliff

`runtimePoly` at 4 receivers cost **6.6 ns** over `fixed` natively. That is far too much for an indirect
branch (a few cycles). **So the cost is almost certainly not the branch — it is that the callee stops
being inlined**, and an un-inlined 4×4 multiply runs with real array accesses and no cross-inlining
with the caller, instead of being folded into it.

If that is right, the interesting boundary is where **inlining** stops, not where
`MaxPolymorphicDispatches=4` says dispatch changes shape. All arms now pay the identical selector
computation and index a k-element array, so k is the only variable — this also removes the two-ALU-op
bias charged against `runtimePoly` in §5.

| # | Prediction | Confidence |
|---|---|---|
| **S1** | k=1 lands within 5% of `fixed` — a one-element array is monomorphic and inlines. | high |
| **S2** | **The cliff is between k=2 and k=4, not at k=5.** Bimorphic inlining is routine, so k=2 stays under 2× `fixed`; k=4 is already ≥ 5×. | medium |
| **S3** | k=8 and k=16 are **not much worse than k=4** — once inlining is lost the extra receivers cost only a bigger dispatch table, not another cliff. Predict k=16 ≤ 1.5× k=4. | medium |
| **S4** | The JIT shows a far gentler curve than AOT at every k, because it can speculate on what it observes. | high |

### 6.2 The matrix-order sweep

Does the 39× generic penalty survive a larger matrix, or is it an artifact of N=4 where unrolling is at
its most favourable?

| # | Prediction | Confidence |
|---|---|---|
| **S5** | **The generic/fixed ratio shrinks as order grows.** At N=8 the loop does 8× the arithmetic per iteration of overhead, so the overhead amortises. Predict N=8 gives **under half** the ratio N=4 gives. | medium |
| **S6** | **`generated` stays ≈ 0.37 ns above `fixed` at every order.** The Fluxtion event wrapper is a constant per event and does not scale with the node's work, so its *relative* cost falls as the node does more. | high |

**S6 is the one worth having:** if the wrapper is constant, then the heavier the node, the less the
processor costs in relative terms — and the 1.48× measured at N=4 is close to the worst case rather
than typical.
