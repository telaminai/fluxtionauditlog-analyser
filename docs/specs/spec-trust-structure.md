# Spec — the trust structure: AI you do not have to trust

**Status:** PROPOSED 2026-08-29 (owner-directed). A **framing** spec: it names the market position and
the properties the product must keep in order to hold it. It creates little new work; it constrains
existing work, and it should be read before anything that loosens what the analyser is willing to assert.
**Tracker:** [tracker.md](tracker.md). **Related:** [spec-agent-brokered-dev-loop.md](spec-agent-brokered-dev-loop.md)
(the loop), [spec-onboarding-example.md](spec-onboarding-example.md) (the gateway).

## The proposition

Regulated organisations are not blocked from using agentic AI because the models are not capable enough.
They are blocked because **nothing an agent produces can be independently checked**, and an industry
built on evidence cannot deploy a component whose only account of itself is its own narration.

The usual answer to that is *explainable AI*: interpret the model's decisions. This product offers a
different answer, and the difference is the whole position:

> **Do not explain the model. Make its output checkable against a record the model did not write.**

An LLM designs the system. The compiler derives execution order deterministically. The runtime writes an
audit record of what actually ran. A human — or another agent — interrogates that record afterwards.
Every claim the AI makes about what it built can be checked against something it did not author.

## D-T1 — the claim is NOT explainability, and saying "explainable AI" actively costs us

In regulated buying, *explainable AI* is a term of art meaning **interpreting a model's decisions** —
feature attribution, counterfactuals, model cards. We do not do that, and a buyer who hears the phrase
will evaluate us against interpretability tooling and correctly conclude we do not fit.

The claim we can actually support is narrower and stronger: **the system the AI produced is
deterministic, and what it did is recorded independently of the AI.** That asks the buyer to believe
nothing whatsoever about the model, which is a much easier thing to sell to someone whose job is not
believing things.

Use *verifiable*, *checkable*, *independently recorded*. Do not use *explainable*.

## D-T2 — the LLM is the least-trusted component, and nothing depends on trusting it

| Layer | Trusted? | Because |
|---|---|---|
| **LLM author** | **No** | fallible by demonstration, not by assumption — see the evidence below |
| **Compiler** | Verifiable | dispatch order is derived, and the generated Java is readable rather than reflective |
| **Audit log** | Evidence | written by the runtime, not by the agent — the agent cannot make it say something |
| **Analyser** | The instrument | states what the record supports and refuses what it does not (D-T4) |

The structure only works because the trust decreases in the direction the work flows. An architecture
where the agent narrated its own correctness would collapse the whole claim, which is why
**agent fixes arrive as evidence-linked PRs and never direct edits** (tracker ▸ Decisions) and why the
analyser has no verb that mutates a system.

## D-T3 — the load-bearing distinction is EVIDENCE versus TESTIMONY

An agent's account of what it did is **testimony**: it may be accurate, it cannot be checked, and it is
produced by the party with an interest in it. The audit log is **evidence**: produced by the runtime, at
the time, independently of any claim, and readable by someone who was not there.

Regulated industries exist because that distinction matters. It is also why the record has to be *on* by
default — a log enabled after an incident is not evidence of the incident — and why the audit
performance work is a market decision rather than an optimisation: at the measured throughput, leaving it
on always is affordable, so the honest engineering position and the commercially useful one are the same
position.

## D-T4 — the refusal to overclaim IS the product here, not a quality nicety

Under this framing, every place the analyser declines to assert something is load-bearing, because an
instrument that overstates becomes testimony too — and then the whole structure has no independent layer
left.

Already true, and now known to be *strategically* required rather than merely tasteful:

- `coverage` refuses an inferred graph — the subtraction would be empty by construction, so the number
  would be a tautology that still prints
- a `PARTIAL` ordering source gets **no dispatch badges**; position is arrival, and the status says so
- a coverage denominator that shrinks must NAME what it dropped, and a node that cannot log at all is
  reported as *unobservable*, never as fine
- *"did not log"* is never rendered as *"did not run"*
- an unrecognised node kind stays counted, because assuming silence flatters the score

**Consequence for review:** a change that makes the analyser assert more than the record supports is not
a small regression. It is a change to the product's market position, and should be reviewed as one.

## D-T5 — what this does NOT give the buyer, stated before they find out

A position that oversells collapses on first contact with a serious buyer. The limits are real and
should be in the pitch, not discovered in the pilot:

- **It does not explain the model's reasoning.** Nothing here says why the LLM chose a design.
- **It is total on structure and silent on semantics.** The compiler will not let you build a cycle and
  will happily let you feed shelf level into a demand forecast. Every expensive defect in the measured
  three-round exercise lived in that gap.
- **The record covers what was logged.** Absence is only conclusive at a level of audit that captures the
  node in question; below that, absence is a level, not a silence.
- **It requires determinism, which requires the compiler.** That is the adoption wall, and it is why the
  reader SPI matters strategically: a foreign source is the only way a buyer meets the instrument
  without first adopting the engine.

## D-T6 — the forcing function is UNKNOWN and must not be assumed

The thesis is that regulated buyers need this now. The honest position is that regulated industries
tolerate significant pain for long periods, and what moves them is a regulation with a date, an incident,
or a competitor who moved — not the availability of a better answer.

**Open, and the most valuable thing to learn:** for the first serious prospect, what is the forcing
function and does it have a date? Record the answer against this decision. If it is *"eventually"*, the
adoption curve is longer than the thesis implies, and that changes runway rather than direction.

## The evidence this rests on

Measured, and none of it produced to support this document.

- **A regulated return that was false, caught by the record.** A simulated compliance ledger reported
  *"7 of 7 excursions foreseen"*. The trace showed four nodes running in a single cycle off one reading:
  the "prediction" was computed from the excursion it claimed to have foreseen. Corrected, the return read
  *0 of 7*. Invisible in the output, invisible in the tests, obvious in the record. **That is this spec's
  thesis in one example** — the failure was a confident, plausible, regulator-facing claim, and only the
  independent record refuted it.
- **The author was the least reliable component, three rounds running**, against pre-registered
  predictions committed before the code. Zero compiler-derived ordering defects across 503 edges; every
  defect that reached an output was semantic. That is D-T2's premise measured rather than asserted.
- **54 declared nodes never ran** — proof-of-absence, which needs the declared graph *and* the record and
  is not available from either alone.
- **A replay that diverged in 295 of 574 cycles** and reached 0 after the missing capture was added:
  correctness measured, not argued.
- **The discipline generalised to an engine it was never written for.** Against a translated LangGraph
  trace the analyser reported a node the adapter had fabricated, and declared every other figure in the
  same response suspect rather than quietly excluding it.
- **Field signal.** A trad-fi organisation already using the analyser: its support team reported that
  questions which took hours now take minutes, and its CTO — having seen the tool — now wants to
  introduce it to peer institutions. That is the first signal in this project not generated by the system
  assessing itself, and the introduction matters more than the opinion: it puts someone else's
  credibility behind the claim.

## Non-goals

- **Not model interpretability.** See D-T1.
- **Not a compliance product.** It produces evidence; it does not assert that any regulation is met, and
  it must never imply that it does.
- **Not a guarantee of correctness.** It makes wrong behaviour *findable*, which is a different and more
  honest claim (D-T5).

## Acceptance — what makes this framing true in the product rather than in a deck

- [ ] No user-facing surface, doc or pitch uses *explainable AI* for what this does (D-T1).
- [ ] The docs site states the evidence-versus-testimony distinction where a buyer will meet it, in those
      terms (D-T3).
- [ ] Audit logging is on by default and never behind a paywall, because a record that must be enabled is
      not evidence (D-T3, and see the pricing note in the tracker).
- [ ] Every existing refusal in D-T4 is covered by a test, and a review that loosens one treats it as a
      position change rather than a tweak.
- [ ] The limits in D-T5 appear in the buyer-facing material, not only here.
- [ ] D-T6's forcing function is answered for the first serious prospect and recorded against this spec.

## Why this is a spec and not a slide

Because it constrains code. If the position is *"the record is the independent layer"*, then the analyser
overclaiming is a defect in the product's reason to exist, and the reviewer needs that written down
somewhere they will actually read it. A deck cannot fail a build.
