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
