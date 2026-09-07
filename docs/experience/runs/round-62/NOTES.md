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
