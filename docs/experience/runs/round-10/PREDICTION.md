# PREDICTION — round 10: does the toolchain substitute for model capability?

**Committed before launch**, and run **concurrently with round 09** so conditions are identical. Same
task ([round-09/TASK.md](../round-09/TASK.md)), same doc arms, same prompts — **the only new variable is
the model**.

## Why this may be the round that explains the previous five

Rounds 05–09 all returned null on the docs: the control arm knew the idioms already. **That is what a
ceiling effect looks like.** A document cannot demonstrate value where there is no headroom.

So the interesting question is not *"can a cheaper model do this"* — it is:

> **Does the toolchain (docs + diagnostics + readable generated source + audit log) substitute for model
> capability?**

That is an **interaction**, not a main effect. If it holds, the commercial claim is strong and concrete:
a cheaper model plus this toolchain reaches what an expensive model reaches unaided.

## The 2×2

| | **docs** | **no docs** |
|---|---|---|
| **Opus** (round 09) | armA-1, armA-2 | armB-1, armB-2 |
| **Haiku 4.5** (round 10) | haikuA-1, haikuA-2 | haikuB-1, haikuB-2 |

n=2 per cell, 8 runs total. Behaviours-only clearing task; no node list.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| U1 | Haiku reaches a green build in **at least 2 of its 4** runs. | medium |
| **U2** | **The docs matter MORE for the weaker model.** haikuA beats haikuB on idiom and/or on the M3–M6 behavioural requirements, where Opus showed no arm difference at all. **This is the hypothesis.** | medium |
| U3 | Haiku uses **fewer capability layers** — less likely to read the generated processor, much less likely to run the analyser. The layers are available to everyone; using them is a behaviour, not a feature. | medium-high |
| U4 | Haiku's decomposition is **flatter** — fewer, larger nodes, more logic per node. | medium |
| U5 | **Haiku+docs does NOT reach Opus parity on the behavioural requirements** (M3–M6), even if it reaches parity on the build. Compiling is the easy half. | medium |
| U6 | Haiku's failures, where they occur, are **design failures rather than syntax failures** — the diagnostics will carry it to green and then leave it there. | medium |

## What each outcome would mean

- **U2 holds and U5 fails** — *cheap model + toolchain ≈ expensive model*. The strongest possible result
  for the product, and it makes the doc set's value measurable for the first time.
- **U2 holds and U5 holds** — the toolchain narrows the gap without closing it. Still meaningful: it says
  where the remaining gap is (design, not syntax).
- **U2 fails** — docs do not compensate for capability, and five null rounds were not a ceiling effect
  after all. The deletion case for §6 becomes very strong.

## Caveats, stated before the numbers

n=2 per cell. This is a **trajectory and a direction**, not a rate, and certainly not significance. And my
quantitative misses have been pessimistic **five rounds running** — so discount U1 and U5 toward the
optimistic side rather than the other way.
