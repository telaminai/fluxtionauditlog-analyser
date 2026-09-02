# PREDICTION — round 56: how complex a Fluxtion GRAPH can Haiku get right?

**Committed before the fixture is built and before any agent runs.**

## The question, and why it is different from round 55

Round 55 measured **catalogue ergonomics** — selecting from type-identical entry points on prose. It
passed, and nothing in it was Fluxtion-specific.

This round measures **framework semantics**: graph shape, instance identity, and trigger propagation.
It uses round 50's designed-but-never-built fixture
([`round-50/DESIGN.md`](../round-50/DESIGN.md)), run as a single Haiku arm rather than a two-arm
comparison, because the question is a ceiling and not a race.

## The fixture — a shared stateful instance

A new figure, `netPosition`, accumulated per symbol across trades. It is **stateful and
order-sensitive**, and **two components need the same one**:

- `Risk` — exposure becomes a function of `netPosition`
- `Capital` — the buffer is scaled by the absolute net position

> **`netPosition` must advance exactly once per TRADE.** Advance it twice and every downstream figure
> is wrong. Advance it zero times and they are stale.

Both consumers publish a constructor that **accepts** a `PositionsApi`, and each also ships a variant
that builds its own internally. **Both wirings compile and run.** Declaring two `Positions` beans
produces two instances that diverge silently — no error, no warning, wrong numbers.

**Noticing is the test.**

## Metric — exact, from the generated source, not from the report

Fluxtion resolves node identity by instance, so the count is unambiguous:

1. **Primary: how many `Positions` nodes exist in the generated `AppProcessor`?** Correct answer is
   **exactly 1**.
2. **Secondary: is `positions.update` invoked once per `Trade` in `handleEvent(Trade)`?**
3. Turns, `javap` calls, `mvn` runs, weighted cost, `cache_read_input_tokens`.

Both primary and secondary are `grep`-able from the emitted processor. No scenario run required, and
no reliance on the agent's own account — this session has already had one result reported wrongly by
a scorer and one asserted without measurement.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| T1 | **Haiku wires ONE `Positions` instance and passes.** The manifest's `Fluxtion-Requires: PositionsApi` on both consumers makes the shared dependency explicit, and the bean file makes sharing the *easier* thing to write — one bean, two references. | medium |
| T2 | **`javap` stays elevated** — 5 or more, versus 0 at cell O and 11 at round 55 rung 1. A stateful shared node invites inspection. | medium |
| T3 | **If it fails, it declares two `Positions` beans**, one per consumer, rather than omitting it. Duplication is the natural reading of "each component requires one". | medium-high |
| T4 | **Turn count rises above round 55's** — this fixture adds a component and a cross-cutting dependency. | medium |

## T1 deserves its reasoning stated, because it may be the interesting result

Round 50's design predicts that **plain Java fails here structurally** — encapsulation and instance
sharing are in direct conflict, so a component that builds its own `Positions` internally cannot be
made to share one without the vendor having anticipated it.

**Fluxtion's claim is that this failure mode does not exist**: the generator resolves two references
to one instance and dispatches it once per event by construction. If Haiku gets this right, the
claim is demonstrated on the cheapest available model. **If Haiku gets it wrong, the claim is still
true of the framework but false of the workflow**, and that distinction must be reported plainly
rather than blurred — the framework guaranteeing single-instance dispatch is worth nothing if the
bean file routinely declares two beans.

## Falsifiers

- **If Haiku passes easily** (one instance, few turns), this rung is too easy and the next axis is
  cross-component `@NoTriggerReference` — a data-only reference that must not trigger — where the
  failure is silent and the diagnostic points the wrong way (round 08's H1).
- **n = 1.** A failure MUST be repeated before it is reported as a ceiling.
- **The transcript must be read before attributing any failure**, per the standing rule.

---

## CORRECTION — the framing above is wrong, recorded before any run

**Owner, on reading the prediction:** *"It is just xml we are building from descriptors in jar
manifests."*

That is correct and it invalidates this document's framing. **The author never touches Fluxtion
semantics.** Single-instance resolution, dispatch order and trigger propagation are the *generator's*
job, downstream of everything the model writes. The model's entire task is:

> **descriptors → XML.**

So "how complex a Fluxtion graph can Haiku get right" is close to a category error. There is no
framework-semantics ceiling for the author, because the author does not author the framework. What
this fixture actually asks is narrower and better:

> **Is the need to SHARE an instance derivable from the descriptors alone?**

If `Fluxtion-Requires: PositionsApi` appears on two entry points, does that determine one bean
referenced twice, or is it under-specified — leaving the model to guess? T1 and T3 stand unchanged as
predictions; only what they are evidence *about* changes. They are evidence about the **descriptor
set**, not about the model's grasp of dataflow.

## The consequence, which is larger than this round

If the deliverable is descriptors → XML, then **for the wiring half there is nothing for a language
model to understand.** Round 48's own notes said so and the point was not followed up:

> *"the bean file becomes derivable: a small tool can resolve which entry points satisfy a stated set
> of required figures and emit the XML deterministically. That is a better answer than asking a
> language model to write it."*

That splits the task cleanly, and the split is the finding:

| half | input → output | needs a model? |
|---|---|---|
| **wiring** | type surface → XML structure | **no** — a resolver can do it, deterministically and for free |
| **selection** | business requirement → `Fluxtion-Description` | **yes** — round 55 showed six type-identical candidates discriminated only by prose |

**So the LLM's necessary contribution is selection, not assembly.** Round 55 measured the half that
genuinely needs a model and it passed. Everything measured before it — 51 turns, 5 builds, a bean file
— was a model doing a resolver's job.

**This reframes the whole series' headline.** "7.5× cheaper to author" understates the available win,
because the cheapest correct implementation of the wiring half is **not a cheaper model — it is no
model at all.** The right architecture is a resolver that emits the XML, with a model consulted only
where the requirement must be matched to a description.

**That is now the most valuable untested claim in this project**, and it is cheap to test: write the
resolver, run it against the round-48 fixture, and compare its output to cell O's bean file. If they
match, the wiring half is solved deterministically and every future round should measure only
selection.
