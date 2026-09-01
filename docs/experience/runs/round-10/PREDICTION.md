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

---

# HARD CALLS — committed, no hedging

Owner's challenge: *"make your predictions and stand by them."* Fair. The caveats above are real but I
have been using them as cover. Below are point predictions with numbers. **Corrected for my own bias:**
every quantitative miss across five rounds has been pessimistic, so I have deliberately moved each of
these toward the optimistic side rather than splitting the difference.

Scored right/wrong. No partial credit.

| # | Call | Number |
|---|---|---|
| H1 | Opus builds green | **4 of 4** |
| H2 | Opus satisfies all of M1–M6 | **4 of 4** |
| H3 | **Haiku builds green** | **4 of 4** |
| H4 | **Haiku satisfies all of M1–M6** | **2 of 4** |
| H5 | Haiku-with-docs beats Haiku-without on behaviour | **haikuA 2/2, haikuB 0/2** |
| H6 | Opus shows an arm difference | **no — 2/2 and 2/2** |
| H7 | The analyser is run by anyone | **0 of 8** |
| H8 | A defect is FIRST found via the audit log | **2 of 8** |
| H9 | Reads the generated processor before declaring done | **Opus 4/4, Haiku 1/4** |
| H10 | Single most-failed requirement | **M5** (the report is last) |
| H11 | Median `mvn` attempts | **Opus 2, Haiku 4** |
| H12 | Median application node count | **Opus 7, Haiku 5** |

## The two that matter, stated as claims rather than ranges

> **H4+H5 together are the whole hypothesis: Haiku fails this task without docs and passes it with them,
> while Opus passes either way.** If that holds, the toolchain demonstrably substitutes for model
> capability, and five null rounds were a ceiling effect rather than a verdict on the docs.

> **H7: nobody runs the analyser.** I am predicting our own instrument goes unused by all eight agents
> even though the task hands them the command line. If that is right it is the most actionable finding of
> the whole exercise — availability is not adoption, and the loop has to *prompt* the layer, not offer it.

## What I expect to be wrong about

H3 (Haiku 4/4 green) is the riskiest. It is a nine-ish node graph from an empty directory with no node
list. If Haiku comes in at 2/4 I will have over-corrected for my pessimism — which would be a new failure
mode rather than the old one, and worth recording as such.

---

# OWNER'S PREDICTION — the arc, recorded before results

> *"I often see that LLMs are undervaluing the Fluxtion philosophy at the beginning and grudgingly
> respect it at the end."* — owner, 2026-09-01, before any round-09/10 result was visible.

## Why I think this is right, and the mechanism

**Fluxtion's cost is immediate and its benefit is deferred.** The cost lands in the first hour:
annotations, a regeneration step, constructor-mapping rules, a build that calls out, a generated file you
must not edit. The benefit lands later — at the first change to a working graph, the first incident, the
first time someone asks *why is this number what it is*.

Any evaluator who samples early therefore sees **cost without benefit**, and reports it honestly. That is
not scepticism as a disposition; it is an accurate reading of an unrepresentative window.

**Two long engagements support the arc, both documented.** The supermarket-poc author recorded a
sceptical prior — *"a compile-time dataflow framework mostly relocates complexity into a build step"* —
and closed with *"I started sceptical; the evidence moved me."* This session's author did the same, and
the moment it turned was moving an effect drain through the middle of a live control layer with
confidence, because a replay suite existed that only existed because the decisions were in a graph.

## The design consequence, and it may explain the null rounds better than the ceiling effect does

**Every round so far — 07, 08, 09, 10 — is GREENFIELD. Each builds from an empty directory.**

That is the worst possible window for this framework's value proposition. Greenfield shows the cost in
full and the benefit not at all, because the benefit *is change safety*: ordering that is derived rather
than maintained, a graph that can be reasoned about after it grows, a log that explains a run nobody
predicted. None of that can appear in a task that ends at the first green build.

So a one-shot greenfield task may be structurally incapable of showing what the docs — or the framework —
are worth. That is a sharper explanation for five nulls than the ceiling effect alone, and the two are
compatible.

## Falsifiable form, for these eight runs

| | signal | where |
|---|---|---|
| **undervalues early** | hand-rolls something the framework provides; treats an annotation as ceremony; calls the regeneration step overhead | the "confusing / missing" section, and the transcript's first third |
| **respects late** | credits a framework property for catching something it would otherwise have shipped; names the constraint as having bought something | the closing report |

**Prediction: the arc will NOT show cleanly in these eight**, because a single greenfield task is too
short a window — most runs go green on the first or second attempt and never reach the point where the
benefit appears. Round 08's `armA-3` is the one prior case that did, and only because it went wrong.

**The round that would test it properly is a CHANGE round**: hand an agent a working graph plus an audit
log, and ask it to add a behaviour that reorders dispatch. That is where the framework pays, and no round
so far has looked there.
