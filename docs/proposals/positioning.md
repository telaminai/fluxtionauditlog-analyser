# Positioning — what Fluxtion is for, and how to say it

Draft for launch, 2026-09-20. Written from one long build session against the real toolchain; every number
here is cited with its own limits. Everything in **Do not claim yet** is currently falsifiable — fix first,
say later.

---

## The claim

> ## Trust the evidence, not the author.

**Fluxtion derives the order everything runs in, at compile time, and records what actually ran, including what did not. 
Correctness stops being a matter of faith and becomes a comparison you can run, whoever, or whatever, wrote the code.**

That last clause does the AI work without the page being about AI. **"Including what did not" is the
load-bearing phrase**, and it is there to satisfy D-T7: derivation on its own invites *we already have
tracing*,
so the derivation has to arrive as the mechanism and the denominator as the payoff. A tracing reader knows
instantly that they cannot answer it. Note this reads D-T7's *lead with the denominator* as "make it the
thing the derivation is for", not "put it in the first clause" — leading absolutely would open on a
capability before saying what the product is.

**The line cannot travel alone.** The title does not locate the product, so the subtitle goes everywhere the
title goes — slides, posts, the repo header. That is a discipline to hold, not a defect to fix; a title that
self-locates would be a description rather than a position.

### Wording decisions, so they are not relitigated

This line will be attacked on three words. Two survive the attack and one did not.

**"Correctness" stays, and the mechanism is now named.** The fair criticism is that the log proves what ran
and in what order, not that the business logic was right — D-T5 says exactly this, and an engineer will catch
it. The tempting fix is to weaken the noun to *what ran becomes something you check*. **Do not.** That is
tracing's sentence, it is the crowded shelf ruled out below, and it discards the denominator that D-T7 says
is the actual product. The gap is closed by saying who supplies the expectation instead: *a comparison you
can run* is honest about the tool providing the substrate and someone else providing the prediction, while
keeping correctness as the subject. Claim the ambitious noun; name the mechanism.

**"The order everything runs in" replaced "your system's execution structure."** Abstract on a first read,
and *order* is the concrete thing the log actually carries. "At compile time" is the phrase doing the real
work and is untouched.

**"Derives" is not negotiable.** The suggested *works out how your system runs* hands back the differentiator
in two words. **Derived, not inferred, is the whole of D-T7**: the order is computed at build time and exists
as an artefact *before the run*, which is exactly what separates it from tracing, which reconstructs order
afterwards. *Works out* connotes inference — it would equally describe a profiler. Fix the abstraction, keep
the verb.

**"Not the author" stays, and the reason is structural rather than stylistic.** The tone risk is real: it can
read as a dig at the reader's own developers, and for a general engineering audience it does. Two things
outweigh it. First, the front page is the **regulated buyer** row of the audience table below, and for that
reader it is not a slight — it is **independence**, the term their process is built on and the thing their
regime makes them pay for. Second and decisively: **the method is called evidence-gated development.** Drop
*evidence* from the claim and the claim and the method no longer share a word, which breaks the hinge this
document is built on — and the same word carries the beta's scoring (*which record in the evidence proves
it*), the deliverable (an evidence pack) and D-T3. The corpus runs on it.

*Trust what ran, not who wrote it* is the better sentence and the worse keystone: precise, keeps a person in
it, and implies provenance rather than competence, which removes the sting. **It is the developer-facing
variant.** Do not pair it with a subtitle containing *what ran* — that is the same phrase twice in three
lines, which is why the subtitle above says *a comparison you can run* and is compatible with either title.
*Trust the evidence, not the testimony* is the internal D-T3 form and too inside-baseball for a cold reader;
it also loses the person, which is what makes the line land.

**The line already describes the analyser**, which is where the checking happens, so resist appending *and
shows you*. Three reasons: a third verb turns a position into a product list; *shows you* understates an
analyser that does not display a record so much as let you compare it against an expectation; and it lands
immediately before the strongest clause and dilutes it. *A comparison you can run* covers the canvas
implicitly, and the first ten minutes below sells it properly.

### Why this one

It is the only claim that is **simultaneously true, differentiating and falsifiable**. Falsifiable matters:
a sceptic can attempt to disprove it in an afternoon, and when they fail, they have sold themselves.

It also survives the thing every other trust pitch trips over — your own best demo is a machine writing
the code. A claim built on the author being reliable would collapse there. This one gets stronger.

### Why not the alternatives

| Framing | Why not |
|---|---|
| *Facts, not probability* | Right instinct, wrong contrast. Most systems you compete with are already deterministic — their failure is that the structure is **unknowable**, not that it is random. And it reads as "we don't do AI", which repels the audience that needs you most. |
| *Microsecond latency, zero GC* | True and irrelevant to the decision. It gets weaker every year as hardware and JIT improve, and it is the only leg a competitor can copy. Nobody chose this for speed. |
| *Observability for event-driven systems* | Puts you in a crowded category, judged on dashboards. Your log is not telemetry — it is a trace of a known topology. Different thing, worse shelf. |
| *Trusted AI agents* | Unprovable by anyone today, including you. Making it invites exactly the scrutiny you would fail. |

**The load-bearing property is determinism, not speed.** Every verification technique in the proof points
below depends on it. Same feed, different host, identical records is the sentence that sells this.

---

## The method — and where it stops being true

The claim above is a property of the system. This is the practice that produces it, and it is what a buyer
actually adopts:

> **No stage passes until the evidence meets a prediction made before the run.**

**Method, not headline.** Lead with this and the room answers *we have gates too*. Lead with the outcome —
you can trust the software without trusting whoever wrote it — and the room asks *how*, at which point this
is the answer rather than the claim. The outcome survives the pitch; the method survives the technical
session afterwards. Order matters more here than wording.

### The word to keep, and the word to drop

**Keep "prediction".** Most quality vocabulary says *requirements* or *acceptance criteria*, and a
requirement that is met tells you only that a requirement was met. A prediction that is falsified tells you
something you did not know. That is why the vendor experiment scored **15 of 17** and why the two misses were
the valuable part. Say plainly that falsification is a result rather than a failure, or "prediction" decays
into a synonym for "requirement" and the edge is gone.

The discipline needs a mechanism, though, not just a word: a prediction is only worth the paper if it was
**sealed** before the run. Ours were, by hand. **Nothing in the product seals one today** — that is a gap,
and it is the same gap as the missing run receipt, one level up.

**Drop "evidence-based".** Two reasons. *Evidence-based software engineering* is already taken as an academic
term — Kitchenham and others, mid-2000s, meaning empirical research informing practice — so the phrase is not
free in this field. And *based* is advisory where *gated* is a refusal. **Evidence-gated development.**

### Three kinds of gate, not one

The sentence reads as though every stage gates the same way. They do not, and compressing to two columns
loses the one in the middle.

| Kind | Who supplies the expectation | Where | What it establishes |
|---|---|---|---|
| **Proof** | nobody — the tool establishes the property | validation, reconciliation, the compiler, the build | the artefact is consistent with what the tool was shown |
| **Provenance** | nobody — identity is recorded | dependency digests, run receipts | the artefact is the one you think it is |
| **Comparison** | someone else, in advance | audit vs oracle, run vs rerun, host vs host | the behaviour is what was predicted |

**The tampered jar passed every proof.** The compiler proved the declaration consistent with the code it was
shown; it had no opinion about whether that was the code we thought we had. Proof is a property of an
artefact, not of a system, until provenance pins which artefact — which is why digests are a launch gate
below rather than a nicety.

### "Cannot pass" means two different things

**Structural — the artefact does not come into existence.** Validation refuses, reconciliation writes nothing
on conflict, the compiler refuses what it cannot prove, the build fails. There is nothing to pass through.

**Semantic — nothing can prove correctness, so the gate is mechanical rather than absolute.** Three legs, and
the third is the one that is easy to drop:

1. the named checks ran and every one passed;
2. **mutation** — an injected fault makes a named assertion fail, so the check *could* have failed;
3. **coverage** — the run actually reached the state the assertion is about.

One and two without three is not a hypothetical. It is the failure we hit, twice: both mutation rounds ended
with survivors that were **scenario gaps, not check gaps** — prediction sound, assertion sound, killable in
principle, and the feed never created the condition. A check that could have failed but was never reached
passes exactly like one that was.

The honest caveat is about enforcement, not truth. Nothing physically stops someone skipping a stage. What
they cannot do is skip it **and still produce the pack** — and for an assurance audience those are the same
sentence, because that is how their gates already work.

### The denominator is the part nobody can copy

The objection is *everyone has gates*. The answer is not that ours are stricter.

Tracing records what did happen and has no declared set to subtract from, so *which components never ran* is
not a question it answers badly — it is one it **cannot pose**. Here the declared graph is the denominator,
so absence is a fact. This is already a standing decision (`spec-trust-structure.md` **D-T7**, *derived
orchestration, and a denominator*), and it is the single hardest thing in this position to copy.

The companion decision is **D-T3**: order in the audit log is **evidence**, values a node writes about itself
are **testimony**. Both layers are real; presenting them as one is the overclaim, and it is in *Do not claim
yet* below because the product does not yet draw the line for the reader.

### Sell the cost, not the idea

Stage gates with objectives met under independence are not new. That is DO-178C, IEC 62304, EN 50128 — and
the people worth recruiting as beta testers have practised it for twenty years. Presenting the thesis as an
insight invites one of them to say so in public.

**What is new is the price.** Prediction, independence, sufficiency and provenance previously took a
qualified toolchain and a team, which is why the practice stayed inside aviation, medical devices and rail.
Here it is a declaration, a generated dispatcher and a log that is a structural fact rather than an emission.
The idea is theirs and they will agree with it on sight; the cost is the argument.

### The counterexample, kept in view

The rule is not universal yet, and the way to hold a principle honestly is to name where it fails.

**A data mapper returning null is logged at fine level and dropped, while a throw is reported as an error.**
A null-returning mapper therefore loses input invisibly at default log levels: every stage reports success
and you pass straight through. It is specified — D-T8 requires a connector to *count what it dropped, so a
denominator exists* — and open in the code.

Note the word. *Denominator*, at the far end of the pipe from the tracing argument above. The same rule keeps
arriving under different names: coverage refuses an inferred graph, reconciliation writes nothing on
conflict, a report section renders or states why it could not, a connector may not conceal a gap (D-T8) or
fabricate a value (D-T9). Independently converging on one sentence — *you do not pass this point with a
silent hole* — is how you tell a principle from a preference.

---

## What you are selling

Nobody buys a compiler, an analyser, a playground, a server and a set of runbooks. They buy **one loop**:

> **Declare it → have it refused or generated → run it → read the audit → get a verdict.**

That loop is real today. Put it on the front page and let everything else be supporting cast. Each stage is
a proof point rather than a feature:

| Stage | What the buyer gets |
|---|---|
| **Declare** | The design is ~10 reviewable lines, readable before any code exists. The cheapest checkpoint in the process. |
| **Refuse or generate** | The toolchain declines what it cannot prove. It refuses a build that contradicts a declaration, rather than warning and proceeding. |
| **Run** | Deterministic dispatch. The same inputs produce byte-identical records, on your laptop and in production. |
| **Read the audit** | Order in the log *is* causality, not correlation. "This node did not react" is provable, not inferred. |
| **Verdict** | Compare a run against a spec, a previous run, or an independent model — mechanically. |

---

## Proof points

Numbers from one cold-start session: a model with no prior Fluxtion exposure built a principal trading desk
— 8 nodes, four products across three exchanges, hedging with lot rounding, partial fills, session gating,
control signals — then integrated a third-party component and added end-of-day reporting.

| Evidence | Number |
|---|---|
| Logged values, published messages and rendered report lines checked against an independently written model | **844 + 34 + 55, zero mismatches** |
| Deliberately injected bugs caught | **21 of 21** — 20 by the audit log, 1 by the toolchain refusing to build |
| Behavioural fixes needed after the first run | **zero** |
| Build cycles, and how many failed | **8, of which 2** — one a documentation gap, one a hole in the author's own test. Neither a logic bug. |
| Separate runs, compared record by record | **3, byte-identical** |
| Integrating a vendor component shipped as a jar | **one bean reference** pulled in its sub-graph, events, ordering, audit trail and exported service. No adapter code. |
| Framework facts the author had to learn | **12** |

**How to use these honestly.** One session, one model, with two operator interventions and access to the
source tree. State that. The claim it supports is *"an author with no prior exposure produced a verified
non-trivial application, and every mistake they made was caught by the evidence"* — not *"LLMs can build
production trading systems unaided"*. The narrower claim is the impressive one and it holds up.

---

## Three audiences, three opening sentences

| Audience | Say | Then show |
|---|---|---|
| **Engineer** | *Execution order is a compile-time artefact, not a runtime accident.* | The generated dispatcher next to the 10-line declaration that produced it. |
| **Regulated buyer** — risk, compliance, audit | *You can show what happened and why, without trusting whoever wrote it.* | One audit record: the inputs, which nodes ran, in what order, and the decision that came out. |
| **AI / platform** | *The model proposes. The graph decides. The log proves.* | An injected bug, and the audit record that exposes it. |

The middle row is the front page. It is the one that is provable, the one nobody else can say, and the one
that describes a buyer with a budget and a deadline.

---

## The first ten minutes

Every model and every evaluator arrives unimpressed, because the value is invisible in the API — it lives in
what you never have to write. The arc is consistent: *unimpressed → grudging respect → reliance*. Grudging
respect arrives at the first moment the toolchain catches something the reader would have shipped.

**So engineer that moment into minute three.**

1. **Break it and watch the log catch you.** A working three-node graph, then: "now make this handler return
   `true`". Show the audit record where a position doubles and a phantom order goes out. The reader learns
   the propagation rule, sees what an audit log is *for*, and gets the conversion moment — before any
   conceptual material.
2. **Drop in a jar.** One dependency, one bean reference, regenerate. A sub-graph appears with its own
   events, ordering and audit. It is the most surprising thing the product does and it is currently
   documented nowhere.
3. **Then** the mental model, the annotations, the contract.

Do not open with architecture. Open with the toolchain being right when the reader was wrong.

---

## Do not claim yet

Each of these is falsifiable today by a sceptic with an afternoon. Fix, then say.

| Claim | Why it fails right now |
|---|---|
| **"Certified components."** | A dependency jar can be rebuilt with its risk calculation zeroed and dropped in under the same filename. Every hash in the build receipt stays byte-identical, the build is green, and the component reports zero risk. Needs dependency coordinates and digests in the run receipt. |
| **"The toolchain protects your code."** | A class that exists only in a dependency can be silently replaced by an empty generated shell, with every stage reporting success. |
| **"What the tool tells you is what happened."** | The analyser returns `ok` for a spotlight drawn in the wrong place, renders an empty chart with no explanation when a pin falls outside the loaded log, and reports marker counts that do not match the data. On a shared human/LLM canvas this is the one thing that must not be wrong — the human catches it instantly and the model does not. |
| **"Every node's behaviour is audited."** | Nodes write their own audit entries. The unforgeable layer is the structural trace of which node ran; node-written values are the author's account. Say which is which. |

The headline is about evidence. It will be measured against the tools that produce the evidence.

---

## Objections, and honest answers

**"It's a niche Java framework from a small team."**
True, and the maintenance surface is real. Two years in production in a crypto market maker — pricing,
client orders, hedging, credit, spreads, exchange connectivity — is the answer on the runtime. The adoption
story is genuinely behind the engineering; do not pretend otherwise.

**"Why not just add tracing?"**
Tracing records what code chose to emit, after the fact, and changes the thing being measured. The deeper
answer is the denominator: tracing has no declared set to subtract from, so *which nodes never ran* is not a
question it answers badly — it is one it cannot pose. Here the order in the log **is** the dispatch order the
compiler derived, against a known topology, so "this node did not react to that price" is a checkable
statement rather than an absence of data. No tracing product can make it.

**"You say trust the evidence — but the record isn't signed, it has no append-only guarantee, and the author
wrote the logging calls."**
The right question, and you want it asked, because two thirds of the answer is strong. **The layer that
carries the claim is not author-written.** Which nodes ran, and in what order, is emitted by the generated
dispatcher from the declaration; the author writes the *values*. Tampering with a logging call corrupts
testimony, not the trace — which is D-T3, and why the distinction has to be drawn for the reader rather than
left implicit. **Determinism gives a second check**: same feed, different host, byte-identical records, so a
doctored value fails against a rerun or an independent model rather than sitting undetected.

Then the answer runs out, and say so. **The record itself has no provenance** — not signed, not append-only,
not bound to the run that produced it. That is the same hole as the unpinned dependency and the unsealed
prediction, in a third place, and it is the strongest argument for treating provenance as its own column
rather than folding it into proof.

**"Spring XML in 2026?"**
It is not runtime wiring — it is compiled into a fixed dispatch table. It is also the artefact that makes
review possible: ten lines a person can check before any code exists, and the place where reconciliation
refuses a contradiction.

**"Our people won't write specs and oracles."**
They do not have to. The loop pays off at the first line. The verification pack is the advanced move for
components where being wrong is expensive — and it is what turns *auditable* into *audited*.

**"Isn't this just for trading?"**
The fit is any deterministic, stateful, event-driven system where being wrong is expensive and "why did it
do that" needs an answer: pricing, risk, control loops, compliance, monitoring, routing. Not a fit for
CRUD, UI-heavy work or I/O orchestration — say so, it buys credibility.

---

## Launch gates

The analyser and onboarding are the last pieces. **The schedule lives in
[the beta proposal](beta-testing/README.md) §7 and is not restated here** — an earlier version of this section
carried five checkboxes, including "one cold-start run with zero interventions" as a tick-box, while that
proposal correctly calls it a programme and dates the beta by it. Two documents disagreeing about the one point
that decides timing is worse than one document being terse.

What matters for the headline, in the language of that schedule:

- **Slice A (integrity)** is what makes the claim true under adversarial test. Dependency digests in the run
  receipt, and no generated shell over a class that exists only in a dependency. Without these, a sceptic
  falsifies "certified component" in five minutes. **The D-T8 null-mapper drop belongs here too** if the
  method section goes public: publishing *you do not pass this point with a silent hole* while a
  null-returning mapper loses input invisibly at default log levels is a one-line rebuttal, and it is the
  counterexample the method section already names.
- **Slice B (truthful echoes)** is what makes the claim survive the tool that presents it. A headline about
  evidence is measured against the analyser's own output.
- **Slice C (zero-intervention cold start)** is the claim behind every number quoted above, and it is a
  programme rather than a checkbox. **The beta date is whatever slice C says it is.**

Slice A is small and unlocks the strongest sentence on the page. Given what is being claimed, "true under
adversarial test" is the only kind of claim worth making.
