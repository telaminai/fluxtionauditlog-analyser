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

---

## 7. The receiver-count sweep — there is no cliff, and receiver count is not the variable

All arms agree bit-for-bit. Native + PGO, profile collected for every k, 2 cycles.

| arm | native | vs fixed | JIT | JIT vs fixed | AOT penalty ÷ JIT penalty |
|---|---|---|---|---|---|
| fixed | 1.1469 | 1.00× | 11.7683 | 1.00× | — |
| **k1** | **7.2878** | **6.35×** | 12.6299 | 1.07× | **5.9×** |
| k2 | 7.2850 | 6.35× | 12.4811 | 1.06× | 6.0× |
| k4 | 7.3082 | 6.37× | 13.3029 | 1.13× | 5.6× |
| k8 | 7.8490 | 6.84× | 13.1584 | 1.12× | 6.1× |
| k16 | 8.4823 | 7.40× | 13.1901 | 1.12× | 6.6× |

**k=1 is already 6.35×.** A site with exactly one implementation, profiled, in a closed world, pays
almost the entire penalty. Going from 1 receiver to 16 adds 17%; having the indirection at all costs
535%.

| # | predicted | measured | verdict |
|---|---|---|---|
| S1 | k=1 within 5% of fixed | 6.35× | **WRONG** |
| S2 | cliff between k=2 and k=4 | no cliff anywhere; penalty present at k=1 | **WRONG** |
| S3 | k=16 ≤ 1.5× k=4 | 1.16× | **RIGHT** |
| S4 | JIT curve far gentler | 1.07–1.13× against 6.35–7.40× | **RIGHT** |

### 7.1 What it actually is — and §5 already contained the control

Compare two arms that differ **only** in how the receiver is reached:

| | how the receiver is obtained | native | vs fixed |
|---|---|---|---|
| §5 `runtimeMono` | `private final MatMul impl = new Mat4A();` | **0.7299** | **0.95×** |
| §7 `k1` | `impls[pick]` — one element, but an **array load** | **7.2878** | **6.35×** |

Identical arithmetic, identical receiver count, identical PGO treatment. **The only difference is
whether the compiler can prove the receiver type statically.** A `final` field initialised with
`new Mat4A()` is provable, so it devirtualises and inlines. An array element is not, so it does not —
and an un-inlined 4×4 multiply pays real array accesses instead of being folded into its caller.

**So the variable is static provability of the receiver, not the number of implementations**, and
**PGO does not recover it.** That is a simpler rule than the one this round set out to find, and a
sharper one:

> On closed-world AOT, any call the compiler cannot resolve statically costs about 6 ns here — whether
> it has one possible target or sixteen. A JIT hides this almost entirely, because it watches what
> actually happens; AOT bills you for what it cannot prove.

### 7.2 Consequence for the Babylon argument

This strengthens it and simplifies it. The generator's advantage is not that it avoids *polymorphism* —
it is that **build-time selection produces a statically provable receiver**, which is the thing AOT
requires and cannot infer. A library that resolves an implementation from a map, an array, a registry
or a config lookup is unprovable by construction, and on AOT that is a 6× tax on the work behind it,
independent of how many implementations exist.

**And my mechanism was wrong for the fourth time today.** §5 blamed receiver count; §6.1 blamed the
inlining boundary at k=4; both were built from an option default (`MaxPolymorphicDispatches=4`) rather
than from a measurement. The control that settles it — `runtimeMono` — had already been measured in §5
and I did not think to compare against it until the sweep made the shape obvious.

---

## 8. Two corrections to how this round has been framed

**Owner, 2026-09-07:** *"Remember — this is human vs human, it will be Babylon vs human. Imagine a graph
with 50 nodes and a human trying to optimise all of that."*

Both are right and both change the reading of §3–§7.

### 8.1 What has been measured is human vs human, so it is a LOWER bound

Every arm in this round is code a person wrote. The builder's contribution is only that it **selects**
between human-written implementations using build context. **The node bodies are not derived by
anything.**

So this round measures the *selection* axis alone, and the selection axis alone is worth **6.35×**
(unprovable receiver) to **26×** (unspecialised body). **The Babylon claim — deriving a specialised body
from a code model — is a second axis this round does not touch at all.** Everything here is a floor
under that argument, not a test of it.

### 8.2 `fixed` is not a ceiling anyone can reach at scale, and I have been quoting it as one

I have repeatedly written that `fixed` beats `generated` by 1.48×, as though a human specialising by
hand were the standard to beat. **For one 4×4 matrix that is true and it is also irrelevant.**

The costs behave completely differently as a graph grows:

| | scales with | measured here |
|---|---|---|
| Fluxtion event wrapper | **per event** — constant | ~0.37 ns, once, however many nodes |
| unprovable receiver | **per node** | ~6.1 ns each |
| unspecialised body | **per node** | ~29 ns each at 4×4 |

**A human hand-specialising one node is a weekend. Fifty nodes, each with its own configuration, kept
consistent as the configuration changes, is not a thing that happens** — and the penalty for not doing
it is paid fifty times while the processor's own overhead is paid once.

That is the claim worth measuring next, and it is testable: **hold the node work constant and scale the
node count.** If the wrapper is per-event and the penalty is per-node, the two curves diverge linearly,
and the 1.48× I have been quoting is an artifact of a single-node graph — the worst case for the
generator and the best case for the hand-written arm.

**Prediction, committed before building it:** at 50 nodes the generated processor's overhead is still
~0.37 ns of wrapper, while a library-shaped equivalent pays ~50 × 6.1 ns. Predict the ratio moves from
today's 1.48× *against* the generator at one node to **> 5× in its favour at fifty**, with the crossover
below ten nodes.

---

## 9. The 50-node test — my scaling prediction is FALSIFIED, and the reason is the real finding

Same work per node (a 4×4 multiply), same implementation class in both arms. The only difference is
whether the receiver is a concrete field (generated) or an array element (library). Native + PGO,
3 attempts per point, all node counts correctness-gated.

| nodes | generated | library | gen/node | lib/node | ratio |
|---:|---:|---:|---:|---:|---:|
| 1 | **1.39** | 8.84 | **1.39** | 8.84 | **0.16×** |
| 5 | 41.48 | 37.70 | 8.30 | 7.54 | 1.10× |
| 10 | 80.23 | 74.91 | 8.02 | 7.49 | 1.07× |
| 25 | 197.00 | 186.57 | 7.88 | 7.46 | 1.06× |
| 50 | 487.94 | 372.48 | 9.76 | 7.45 | **1.31×** |

**§8 predicted the ratio would move from 1.48× against the generator at one node to > 5× in its favour
at fifty, crossing over below ten. The opposite happened.** At one node the generator is **6.4× faster**;
by five nodes the advantage is gone; at fifty the generator is **1.31× slower**.

### 9.1 What actually happens: the advantage is bounded by the inliner's budget, not by node count

Read the per-node columns. The library costs a flat **~7.5 ns/node at every n** — it never inlines,
exactly as §7 established, and it never gets worse. The generated processor costs **1.39 ns at n=1 and
~8–9.8 ns/node from n=5 onward.** Its per-node cost rises **six-fold between one node and five**.

**The specialisation does not compose.** One 4×4 multiply inlines into the dispatch method and vectorises
(1.39 ns, the 10× native speed-up §5 found). Five of them do not: the total inlined body exceeds what
the compiler will take, the calls stay out of line, and each one reverts to roughly what the un-inlined
library pays.

**This is consistent with everything else measured today and it is the same threshold under a different
name** — round 58's "dissolution cliff", §5's 15× vectorisation win, and `PriorityForceInline` being the
only lever that works. **It is a budget on total inlined code size, not a property of nodes.** The
original 10-node latency kit reaches 1.67 ns/event — 0.167 ns/node — because those nodes are a few
flops each. Fifty trivial nodes inline; five matrix nodes do not.

### 9.2 What this costs the argument, stated plainly

The "50 nodes a human could never hand-optimise" case is **not** demonstrated by this measurement. On
heavy nodes the generator's per-node advantage disappears at n≥5 and inverts slightly by n=50. The
result stands as measured:

- **the provable-receiver advantage is real and large (6.4×) while the work still inlines;**
- **it is capped by the compiler's inlining budget, and the cap arrives early for heavy nodes.**

### 9.3 Two process failures in this section, both mine

**I ran the first sweep with `--attempts 1`.** This morning I built the landing harness *because* a
fresh collection lands only sometimes, wrote "collect until it lands" into the docs and the checklist,
and then ran the round's most important test with a single attempt. The n=1 point read 12.04 on that
attempt and 1.39 on the next two — a 8.7× artifact I was one paragraph away from publishing as
"the library beats the generated processor at scale".

**What caught it was a cross-check, not the harness.** A 4×4 multiply costs ~11.5 ns on a JIT and
~1.15 ns native. The native n=1 reading of 12.00 had *no* native speed-up in it, which is only possible
if the build missed. The harness reported every arm as `[missed]` against my deliberately-unreachable
target and I read past it.

**Next test, and it follows directly:** if the cap is the inlining budget, then
`-H:PriorityForceInline` on the *node* class should lift it. The page currently records that adding node
classes to the directive "gains nothing" — but that was measured on trivial nodes, where nothing needed
lifting.

---

## 10. §9's library arm is not a library — five advantages the benchmark handed it

**Owner:** *"I think we are giving hand rolled all the advantages. For a real app with multiple
directions of freedom and injected items, different event types and specialisation, I'm sure Fluxtion
wins."*

Reviewing §9's `Fleet` against what a hand-written system actually has to do, this is correct, and the
bias is structural rather than a detail:

| what §9's library arm got | what a real one faces |
|---|---|
| **a flat loop over a homogeneous array** — one call site, one type, sequential memory | 50 *different* node types with different signatures; no loop can express that, so it becomes a graph walk, a visitor, or a chain of conditionals |
| **50 independent cells, no edges** | dependency edges — node B reads node A's output, so somebody must order the calls and propagate |
| **one event type** | many, each reaching a different subset; the library must resolve type → subscribers at runtime |
| **no configuration** | injected services, conditional wiring, per-deployment options — every one a runtime decision for a library and a build-time constant for a generator |
| **every node fires every event** | conditional propagation, so a library carries dirty checks the generator can emit or omit at build time |

**§9 removed, by construction, every axis on which the generator differs from a library, and then
measured the one axis left.** That axis — per-node specialisation — turned out to be capped by the
inliner, which is a true and useful result. It is not a result about real systems.

**What still stands from §9, and it applies to both sides:** the inlining budget is real, and at 50
heavy nodes neither arm's arithmetic inlines. So at that scale the specialisation axis is not where the
argument can be won for *either* party — and Fluxtion's advantage has to come from the axes §9 deleted:
build-time dispatch per event type, build-time topological ordering, and build-time resolution of
injected configuration.

**That is the test worth building**, and it is the one that matches the owner's framing. Recorded here
before building it so the design cannot drift toward whichever arm the first numbers favour.

---

## 11. The realistic test — predictions committed before building

Restores the five axes §10 listed. **Same node classes in both arms**; the only difference is how they
are wired and dispatched.

- **12 light nodes** (a few flops each, like a real graph and unlike §9's matrices) so the inlining
  budget is *not* the binding constraint and the wiring difference is what shows.
- **Dependency edges** — nodes read other nodes' outputs, so order matters.
- **Three event types** reaching different subsets.
- **Config-driven wiring**: each of two nodes takes its strategy from injected configuration.
- **Library arm written the way a competent engineer would**: topologically pre-sorted arrays, a switch
  on event type to a subscriber array, dependencies held as interface fields set at wiring time. No
  reflection, no per-event map lookup. It simply cannot bind statically, because its wiring is data.

| # | Prediction | Confidence |
|---|---|---|
| **T1** | Generated lands **under 4 ns/event** natively — 12 light nodes inline and scalar-replace, as the original 10-node kit does at 1.67. | medium |
| **T2** | The library pays roughly the §7 rate per node: **≥ 50 ns/event** natively. | medium |
| **T3** | **Ratio > 10× in the generator's favour** — this is the case §9 could not show, because §9's nodes were too heavy to inline for either arm. | medium |
| **T4** | On the JIT the ratio is **under 3×**, because the JIT devirtualises what it observes. The AOT/JIT divergence is the point. | high |

**If T3 fails, the "real app" claim does not hold on this evidence** and the honest position becomes that
Fluxtion's measured advantage is the 8.6% dispatch figure plus provable receivers on graphs small enough
to inline — which is a much narrower claim than the one being made.

---

## 12. The realistic test — results

14 nodes, three event types, dependency edges, injected strategies. **Same node classes in both arms.**
Outputs identical on all five published values.

| | JIT *(3 reps)* | native + PGO *(3 attempts)* | native speed-up |
|---|---|---|---|
| **generated** | 6.221 | **2.123** | **2.93×** |
| **library** | 18.841 | 20.057 | **0.94× — slower** |
| **ratio** | **3.03×** | **9.45×** | |

### 12.1 The result

**On a realistic graph the generated processor is 3× faster than a competently-written library on a
JIT, and 9.5× faster on native + PGO.**

**The line that matters is the last column.** Native + PGO makes the generated processor **2.93×
faster**. It makes the library **6% slower**. The library gets *nothing* from ahead-of-time compilation
— everything it does is decided by data the compiler cannot see, so there is nothing for AOT to
specialise, and it pays the closed world's costs without collecting any of its benefits.

That is the owner's argument, measured, on the shape it was claimed for.

### 12.2 Scoring

| # | predicted | measured | verdict |
|---|---|---|---|
| T1 | generated under 4 ns natively | 1.96–2.21 | **RIGHT** |
| T2 | library ≥ 50 ns natively | 19.4–21.0 | **WRONG** |
| T3 | ratio > 10× native | 9.45× | **marginally wrong** |
| T4 | ratio < 3× on JIT | 3.03× | **marginally wrong** |

**T2 is wrong for a reason worth keeping.** I scaled from §7's *6 ns per unprovable call site* — but
that figure was measured on a 4×4 matrix multiply, where the penalty is dominated by an un-inlined body
doing real work. These nodes are a few flops each, and the measured penalty is **1.28 ns/node**. So the
per-site cost of an unprovable receiver is not a constant: **it is roughly the cost of the work that
fails to inline with it.** Light nodes, small penalty; heavy nodes, large penalty.

T3 and T4 both missed by under 5% and in the direction of the claim being slightly weaker than
predicted. Recorded as misses rather than rounded into wins.

### 12.3 What §9 got wrong, and why this test is the right one

§9 measured 1.31× **against** the generator at 50 nodes and could not show a wiring advantage at all.
The difference is node weight: §9's nodes were 4×4 matrix multiplies, far too heavy for either arm to
inline, so both reverted to un-inlined per-node cost and the wiring difference was invisible underneath.
**These nodes are light, which is what a real graph looks like**, so the wiring difference is the
dominant term and the generated arm inlines the lot.

Both results are true and they bound the claim from either side:

- **light nodes (realistic): 9.45× on AOT**, and the library gains nothing from AOT at all;
- **heavy nodes: the advantage is capped by the inlining budget** and can invert.

---

## 13. Light nodes at scale, and why the fast hand-rolled shape is fragile

### 13.1 The cliff for light nodes sits between 25 and 49

Fan-out of uniform 3-node lanes, one event type. Native + PGO, 3 attempts each.

| nodes | generated (best) | library | ratio | gen/node | all three attempts |
|---:|---:|---:|---:|---:|---|
| 10 | 0.4438 | 12.25 | **27.6×** | 0.044 | 5.85, 5.76, **0.44** |
| 25 | 0.7739 | 28.01 | **36.2×** | 0.031 | 1.28, **0.77**, 1.32 |
| 49 | 21.9485 | 60.02 | **2.7×** | 0.448 | **21.95, 21.97, 22.01** |

**The attempts column is the evidence.** At 10 and 25 the spread is the build lottery. At 49 all three
agree to 0.3% — **a hard structural cap, not a lottery** — and per-node cost jumps 14×.

Three regimes now measured, all consistent with one rule (a budget on total inlined code size):

| | inlines | ratio |
|---|---|---|
| ≤ 25 light nodes | yes | 27–36× |
| ~50 light nodes | no | 2.7× |
| ≥ 5 heavy nodes | no | 1.1×, inverting to 0.76× at 50 |

**Caveat on shape:** this is a pipeline fan-out — one event type, uniform depth, no fan-in. A DAG with
several event types puts only the reachable subset in each `handleEvent`, which may move the cliff. That
test is outstanding.

### 13.2 The fast hand-rolled shape is real, and it is one refactor deep

**Owner:** *"When a user builds a hand-rolled calculation they use interfaces and indirection a lot.
Only if they use pure pull and no object creation do they get close to Fluxtion. If they built
something from scratch it would be so bespoke it could not be refactored easily and would miss
optimisations in many places."*

The first half is an assertion about how code is typically written and is not measured here. **The
second half — that the fast shape is fragile and easily lost — was demonstrated twice today, by
accident, in this round's own harness:**

| what changed | before | after | cost |
|---|---|---|---|
| `private final MatMul impl = new Mat4A();` → the same single implementation reached from an **array** | 0.73 ns | 7.29 ns | **10×** |
| `private final int n = 4;` → the same value taken from a **constructor parameter** | 11.53 ns | 36.96 ns | **3.2×** |

Neither is a change a reviewer would question. Moving an implementation into a collection and passing a
size as a parameter are both ordinary, sensible refactorings. Each cost an order of magnitude or close
to it, **and neither produced any diagnostic**.

**Both were mine**, made while deliberately writing fast code and knowing what to watch for. That is the
strongest available evidence for the claim: the fast hand-rolled shape requires holding several
invariants at once — concrete types on the path, no collection indirection, constants inline, no
interface fields — every one of them invisible when broken, and none of them expressible in the type
system.

**A generator re-derives all of them on every build.** That is a maintainability argument rather than a
performance one, and it is the reason the 9.45× measured in §12 is more representative of a real
alternative than the 8.6% measured against hand-written flat code: the flat shape exists, it is faster
than Fluxtion, and it survives contact with exactly one refactor.

---

## 14. The realistic graph at scale — and the rule that now explains every result

Built to the owner's stated shape: **54 nodes, 12 event types, depth 8 max / 1 min / 3.6 average, 32
fan-in nodes**, and Fluxtion emitted **7 `commonDispatchTail` methods** factoring the shared suffixes.
Correctness gated; both arms identical.

| | JIT *(3 reps)* | native + PGO *(3 attempts)* | native speed-up |
|---|---|---|---|
| generated | 15.03 | **12.79** | 1.18× |
| library | 38.94 | 32.67 | 1.19× |
| **ratio** | **2.59×** | **2.55×** | |

The three native attempts span 12.74–12.82 — **0.6%, so this is structural, not the lottery.**

### 14.1 The rule

Every result in rounds 62 now falls out of one thing: **whether the graph fits the compiler's inlining
budget.**

| graph | nodes | generated (native) | ratio |
|---|---:|---:|---:|
| RealGraph | 14 | 2.12 | **9.45×** |
| LaneGraph | 10 | 0.44 | **27.6×** |
| LaneGraph | 25 | 0.77 | **36.2×** |
| **LaneGraph** | **49** | **21.9** | **2.7×** |
| **DAG, 12 event types** | **54** | **12.8** | **2.55×** |
| Fleet, heavy nodes | 50 | 488 | 0.76× |

**Under the budget the graph dissolves into the dispatch method and the advantage is an order of
magnitude. Over it, the advantage is ~2.5×.** The boundary for light nodes is somewhere around 25–30
nodes; for heavy nodes it arrives at five.

**Common-tail factoring did not move the boundary.** Seven tails were emitted and the 54-node graph
still landed above the budget. It reduces emitted *statements* — 30 for an event type whose plan is 35
nodes — but the tail bodies still have to be inlined somewhere, and total inlined size is what the
budget counts.

### 14.2 What this does to the claim

**The 9.45× headline is a small-graph number.** It was measured at 14 nodes. At the owner's stated
scale — 50-ish nodes, many event types — the honest figure is **2.5×**, and it is stable rather than
lottery-dependent.

That is still a real advantage and it is still the right architectural argument: the library arm gets
**1.19× from AOT** and the generated arm **1.18×** — at this size *neither* benefits much, and the
generated arm's lead comes from binding rather than from inlining. But **an order of magnitude is not
available at 50 nodes on this evidence**, and any published claim should say which regime it is quoting.

### 14.3 In the deployment that exists

Mongoose measures **p50 250 ns** publish-to-handler and ~10M msgs/sec sustained
(`telaminai.github.io/mongoose/reports/server-benchmarks-and-performance/`). Against that envelope:

| | 14-node graph | 54-node graph |
|---|---|---|
| generated | 2.12 ns — **0.8% of p50** | 12.8 ns — **5% of p50** |
| library | 20.06 ns — 8% of p50 | 32.7 ns — 13% of p50 |

At 10M msgs/sec the budget is 100 ns per message, so the same figures are **13% versus 33% of the
throughput budget** on the larger graph. **The advantage shows up as throughput headroom, not as p50
latency** — and the p99.9+ percentiles are OS-jitter dominated on that hardware, so nothing in the graph
reaches them.
