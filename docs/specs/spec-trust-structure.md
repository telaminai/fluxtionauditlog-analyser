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

> **Do not explain the model. Check specified claims about what it built against a recorded execution.**

An LLM designs the system. The compiler derives execution order deterministically. The runtime writes an
audit record of what ran. A human — or another agent — interrogates that record afterwards.

**The claim is bounded, and the bound is not a caveat — it is the claim (corrected 2026-08-29, review F5).**
An earlier draft said *"every claim the AI makes can be checked against a record it did not author"* and
that **the agent cannot make the log say something**. Both are false as shipped, and the correction matters
most in front of the buyer this spec targets:

- the analyser **accepts a hand-written audit log** — demonstrated in this repo's own cold test, where one
  was typed by hand from the format spec and parsed;
- an agent that authors the project **writes the nodes**, and therefore writes the `auditLog` calls that
  decide what appears in `nodeLogs`;
- and there is **no signed artefact, runtime identity, append-only guarantee or chain of custody** in this
  product. Nothing here establishes who produced a file.

So the honest proposition is:

> **Given a trusted runtime and a record supplied with declared provenance, the analyser independently
> checks specified structural and recorded-execution claims against that record. It does not establish the
> record's origin, its completeness, its semantic correctness, or that the author could not have influenced
> what it contains.**

That is still a strong and unusual claim — nothing adjacent checks structural claims against a recorded
execution at all — and it is one a compliance officer cannot puncture with the first question they will
ask, which is *"how do you know this log wasn't edited?"* The answer today is **you don't, and we say so**.
If authenticated provenance is wanted, it is a separate capability to specify and build, not something to
imply.

## D-T7 — what is actually being sold: DERIVED orchestration, and a denominator

Owner, 2026-08-30, asking whether the sale is *"exogenous linkage that has been removed unrecoverable
before Fluxtion, and inferred orchestration"*. Yes — and it is worth splitting, because the two halves
have very different strength in front of someone who already owns tracing.

**1 · Derived, not inferred.** In a conventional system the orchestration is an *emergent runtime
property*: nothing writes down what called what, in what order, or why. Compilation destroys the design
intent, and the only route back is inference — from timestamps, thread ids, log adjacency — which
interleaving, async and retries degrade exactly when you need it. Fluxtion **derives the total order at
build time and emits it as an artefact**. The order is therefore not an observation to be reconstructed;
it is a fact that already existed before the run. That is the exogenous linkage: the record is checked
against something the runtime did not produce.

**2 · A denominator — and this is the half that cannot be answered by a better log.** Because the declared
set exists as an artefact, **absence becomes measurable**. `coverage` is declared minus logged. Tracing —
OpenTelemetry, spans, parent/child — records what *did* happen and has no declared set to subtract from,
so *"which nodes never ran"* is not a question it answers badly; it is a question it **cannot pose**. No
volume of logs supplies a denominator. This is why the analyser refuses coverage on an **inferred** graph
(`GraphSource.supportsCoverage()`): a denominator derived from the same record it is measuring is a
tautology that still prints.

**Say the second one first.** "Derived, not inferred" invites *"we have distributed tracing"*. "Which of
your components never executed, and how do you know the list is complete" does not, because the buyer
knows they cannot answer it.

**The boundary — corrected 2026-08-30 by review F1, and the correction narrows the claim.** Exogeneity
applies to **participation and order**, not to **values** — but only in a **traced** record, and only for
**registered** nodes. Verified in `fluxtion-runtime` 1.0.13, three conditions apply, not two:

1. `nodeRegistered` builds a logger for **every** registered node and stores it unconditionally; an
   unregistered invocation resolves to `NullEventLogger` and appears nowhere.
2. `nodeInvoked` calls `logNodeInvocation(traceLevel)`, which records the trace **only when the configured
   level admits it** — and `addEventAudit()` installs `tracingOff()`. **With tracing off, a node that ran
   and logged no value need not appear at all.**
3. `EventLogSource` controls only whether the runtime can *inject* that logger into the node, and hence
   whether the node can record its own values.

**An earlier version of this section said participation was automatic for any dispatched node.** That is
false, and false in the direction that flatters us: it would have let an *untraced* record be read as proof
of absence. The analyser itself already refuses that conflation — it distinguishes traced from untraced
because absence supports different claims in each — so the spec was overstating what the product is
willing to assert, which is precisely the D-T4 failure this document exists to prevent.

**So the honest claim.** In a traced record, that a registered node ran and where it sits in the order is
recorded by the runtime rather than authored. In an untraced record, absence means *"said nothing"*. What
a node **says** about itself is testimony in either case.

## D-T1 — the claim is NOT explainability, and saying "explainable AI" actively costs us

In regulated buying, *explainable AI* is a term of art meaning **interpreting a model's decisions** —
feature attribution, counterfactuals, model cards. We do not do that, and a buyer who hears the phrase
will evaluate us against interpretability tooling and correctly conclude we do not fit.

The claim we can actually support is narrower and stronger: **the system the AI produced is
deterministic, and what it did was recorded by execution rather than by the model's account of it.** That
asks the buyer to believe nothing about the model's *reasoning* — which is a much easier thing to sell to
someone whose job is not believing things. It does not ask them to believe nothing at all: they must still
trust the runtime that produced the record and the provenance under which it was supplied (F5).

Use *verifiable*, *checkable*, *independently recorded*. Do not use *explainable*.

## D-T2 — the LLM is the least-trusted component, and nothing depends on trusting it

| Layer | Trusted? | Because |
|---|---|---|
| **LLM author** | **No** | fallible by demonstration, not by assumption — see the evidence below |
| **Compiler** | Verifiable | dispatch order is derived, and the generated Java is readable rather than reflective |
| **Audit log** | Evidence **about execution**, not about origin | written by the runtime as the system ran, independently of any later account of it. **Not tamper-evident, and not independent of the author who wrote the logging calls** (F5). Its force is that it was produced by execution rather than by narration — not that it could not have been influenced. |
| **Analyser** | The instrument | states what the record supports and refuses what it does not (D-T4) |

The structure only works because the trust decreases in the direction the work flows. An architecture
where the agent narrated its own correctness would collapse the whole claim, which is why
**agent fixes arrive as evidence-linked PRs and never direct edits** (tracker ▸ Decisions) and why the
analyser has no verb that mutates a system.

## D-T3 — the load-bearing distinction is EVIDENCE versus TESTIMONY

An agent's account of what it did is **testimony**: it may be accurate, it cannot be checked, and it is
produced by the party with an interest in it. The audit log is **evidence**: produced by the runtime, at
the time, independently of any claim, and readable by someone who was not there.

Regulated industries exist because that distinction matters.

**The boundary, stated with it rather than after it (F5).** Evidence-not-testimony is a claim about *how
the record was produced* — by execution, at the time — not a claim that it is authenticated. A supplied
file's origin rests on declared provenance and on trusting the runtime that wrote it. Anyone who can write
a file can write one of these; the analyser will read it and say what it says. Treat the distinction as
what it is: it rules out an agent's *narration* being the only account, and it does not rule out tampering. It is also why the record has to be *on* by
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
- **It does not establish the record's origin.** There is no signing, no runtime identity, no append-only
  guarantee. Anyone who can write a file can write one of these, and the analyser will read it. Origin
  rests on declared provenance and on trusting the runtime — say so before a compliance officer asks.
- **The author influences what the record contains.** Whoever wrote the nodes wrote the `auditLog` calls.
  The log is evidence of execution; it is not independent of the person or agent who decided what to log.
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
- **The reaction is consistent across LLM sessions, and it is always the same shape** (owner, 2026-08-30):
  *"Every LLM session, real evidence tied back to compiled artefacts deterministically surprises and
  impresses. They all under-value it until they try it."* That is a repeated observation across many
  sessions, not one anecdote, and it has a **measured instance in this repo's own work**: over one day,
  the session writing this spec asserted framework behaviour from recall **four times** and was wrong
  every time — the `transient` rule, the `EventLogSource` contract, and twice walking past authoritative
  documents this repo itself points at. Each was settled in under a minute by reading a jar. The document
  arguing that narration cannot be trusted was being written by a narrator who could not be trusted, and
  only the artefacts caught it.

  **Why the under-valuation is systematic rather than careless.** A recalled fact and a read fact arrive
  in identical form — a confident sentence with no marker distinguishing them — so checking presents as
  pure overhead. And the failures are *plausible* rather than absurd (`EventLogNode` is real, and does
  supply `auditLog`; the error was that it is not the contract), so they survive self-review, because
  self-review uses the same faculty that produced them. The artefact breaks that loop only because it is
  **exogenous**: `javap` output is not generated by the thing being checked. That is D-T3 applied to the
  author rather than to the system — the model's recall is testimony, the compiled artefact is evidence.

  **What it does to probabilism, stated precisely.** It does not make the model deterministic and should
  never be sold as if it did. It converts an **unbounded error rate into a bounded one**: wrongness
  becomes *detectable* rather than absent (D-T5). The second-order effect is the commercially useful one —
  it turns probabilistic **outcomes** into probabilistic **iterations**. Whether the node logged is
  deterministic; only how many loops it took to get there varies. A buyer can budget time under that
  regime; they cannot budget correctness under the other.

  **Consequence for how this is sold.** If every session under-values it until they try it, then no amount
  of explanation substitutes for the first moment a claim is checked and survives or dies. That is
  precisely what [spec-guided-start.md](spec-guided-start.md) D-G2 is for — the tutor points, the screen
  proves — and it makes the demo's job *reaching that moment quickly*, not enumerating features.
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
- [ ] **No surface, doc or pitch claims the record is tamper-evident, authenticated, or beyond the
      author's influence** (F5). The bounded proposition is used verbatim where the claim is made.
- [ ] The answer to *"how do we know this log wasn't edited?"* is written down and is "you don't, today" —
      not improvised in a meeting.
- [ ] D-T6's forcing function is answered for the first serious prospect and recorded against this spec.

## Why this is a spec and not a slide

Because it constrains code. If the position is *"the record is the independent layer"*, then the analyser
overclaiming is a defect in the product's reason to exist, and the reviewer needs that written down
somewhere they will actually read it. A deck cannot fail a build.
