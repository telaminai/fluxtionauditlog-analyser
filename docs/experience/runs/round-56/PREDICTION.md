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
