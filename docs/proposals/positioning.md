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

### The denominator has a name: a closed world

The proper term is the **closed-world assumption**, and it is worth using, because it is established
vocabulary (Reiter, 1977) that arrives with **negation as failure** already attached. In an open world,
absence of evidence is not evidence of absence. In a closed world it is — and that is the entire difference
between a tracer and this.

A tracer is open-world by construction. An absent record means *I did not observe it*, which is equally
consistent with did-not-happen, was-not-instrumented, was-sampled-out and buffer-dropped. It cannot tell
those apart, ever, however good it gets. Here an absent record means **it did not happen**, which is a
checkable statement.

**We do not assume a closed world. We compile one.** That is the sentence to lead with, because it separates
the claim from every architecture diagram, service catalogue and dependency manifest ever maintained by hand.
All of those *assert* closure and none *establish* it — which is precisely why negation-as-failure has a bad
name outside databases, and why nobody trusts a hand-drawn model to tell them what cannot happen. The
compiler closes the world by construction and emits the certificate. That is not a gloss on the patent:
claim 12, inferring a graph description where none was supplied, **is** the closure mechanism.

### Where the world stops being closed

Stating the boundary is not a caveat on the claim, it is what makes the claim survive contact with a sceptic.
The assumption is unsound the moment the world is not really closed, so be first to say where that is.

| Closed | Open |
|---|---|
| node-to-node dispatch: which nodes run, in what order, for each event type | **everything inside a node body** — it may call anything, open a socket, invoke a vendor method |
| one processor's generated dispatcher | a direct call on a node reference, a JVM agent, anything routed around the dispatcher |
| a single feed's ordering into that processor | ordering across two feeds into one processor, and anything spanning processors |

**The tampered dependency lives in the open half**, and that is the honest reason it passes: a risk component
rebuilt with its constant zeroed changes no node, no edge and no order. It is not a hole in the evidence — it
is a region the closed-world assumption never covered. Say it before a reader finds it and the rest of the
position gets stronger, not weaker.

Closure is also always **relative to the derived graph**, which is the closure certificate. Substitute that
and the world is closed around the wrong thing — the same provenance gap as the unpinned dependency, the
unsealed prediction and the unsigned record, in a fourth place.

### The consequence: the limits are a theorem, not modesty

Everything this product may assert is inside the closure; everything in *Do not claim yet* below is outside
it. That is far better footing than caution, because a stated boundary with a reason cannot be characterised
as timidity — and it is what makes the claims inside it credible.

It also hands the demo its sentence, falsifiable in five minutes, which no observability, tracing or APM
product can answer at all:

> **Show me the components that did not run, and prove the list is complete.**

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

## Hero options — four framings (added 2026-09-24)

**Nothing above is replaced.** The claim, its subtitle and the wording decisions stand. These are framings for a
hero slot, one per audience, drafted and then attacked twice for claims the product does not support. They exist
so the choice of audience is made deliberately rather than by whoever writes the page.

Every option below respects the same accuracy constraints, which are listed once at the end. Each was rewritten
at least once to get there, so do not "simplify" one without checking it against that list.

### A · Engineers, which is who buys it

> **Trust the evidence, not the author.**
> "Works on my machine" is testimony. Fluxtion derives the running order from your code, then records what ran,
> in what order, and what never reported. Open that record in the analyser and work the evidence like a notebook,
> whoever, or whatever, wrote the code.

The opening three words do a paragraph's work, and every engineer has said them. Recommended default.

### B · The AI-authored-code wave

> **Trust the evidence, not the author.**
> Something wrote this code. You, a model, or someone who left three years ago. Fluxtion works out the running
> order itself. Switch auditing on and it records what actually ran and what never reported, then opens it as a
> notebook you can question, rerun and argue with. Stop taking the author's word for it.

Stronger if the page is aimed squarely at AI-written code. Two costs: it dates faster, and the closing line is a
sharper elbow than a homepage usually throws.

### C · Shortest, for a tight hero slot

> **Trust the evidence, not the author.**
> Fluxtion knows the order your logic runs in, because it derived it. Turn auditing on and the run becomes a
> notebook: what ran, what stayed quiet, and whether that matches what you expected. Whoever, or whatever,
> wrote it.

Fits the slot, loses the hook.

### D · Support and on-call — the person who knows nothing about the system

> **Trust the evidence, not the author.**
> It is 3am, you have never seen this system, and something threw. Fluxtion's audit log tells you what ran, in
> what order, and what never reported, so you can find the fault without reading the code or waking whoever
> wrote it. Hand the evidence to an assistant, get a fix proposed against that evidence, then open your report
> against the next run to show the fault is gone. The report is what you file.

Short form, for a tight slot:

> **Trust the evidence, not the author.**
> Never seen the system, and it just threw? The audit log says what ran, in what order, and what never reported.
> Diagnose it without reading the code, let an assistant propose the fix against that evidence, and re-open your
> report against the next run to show it is gone.

### Why D may be the strongest, and what gates it

The gold dust is not "the analyser helps with incidents". It is that **the audit log is the only artefact that
lets someone who knows nothing about a system reason about it correctly.** For a stateful event-driven
application the current answer to *what happened* is read the code and ask the author. Tracing gives spans
between services and cannot say which callbacks fired inside a processor, in what order, or what stayed silent.
At 3am the author is asleep or left years ago, so for this reader the headline is not a position, it is their
operating condition.

Two things compound it. Ruling things out is most of triage, and the denominator is the only feature that
supports ruling out. And an assistant debugging a system it has never seen fails for want of evidence it cannot
invent, not for want of intelligence, which is exactly what a bounded machine-readable record supplies.

The whole journey is already supported rather than proposed: reports ship with typed sections and stored
authoring context, the stored report call re-issues exactly against a new log, and the staleness banner
announces when the newer run does not match what the report was written against. The agent-brokered dev-loop
spec already calls the report the regression oracle, so D describes the designed path.

**Three limits decide whether it converts.**

1. **Audit has to be compiled in**, and the low-latency production profile is precisely the one that runs
   without it. An operations buyer asks this first. Answer it as a packaging decision — does the supported
   production build ship with audit on, at what level, at what cost — before this framing goes public.
2. **It only helps systems already built on the framework.** This is not a horizontal incident tool with its
   own market. Treat it as the argument that makes the framework buyable, and as the expansion story inside
   existing accounts.
3. **A support engineer is the reader least able to detect a false verdict**, having no model of the system to
   check it against. That makes them the highest-stakes audience for the evidence-integrity milestone, and it
   is why the 2026-09-24 client incident matters more than its size suggests.

**Commercial consequence, which is the useful part.** The before-and-after step — showing the fault is gone — is
exactly where a cross-run delta is worth money, and it is the step D currently has to describe as re-opening a
report rather than diffing two runs. That is where a paid delta feature naturally sits, and this framing also
explains what a long-running production customer is really paying for. It is not the compiler. It is being able
to operate what they built.

### The notebook: what is claimable today

The tracker's product-discovery section (2026-09-22) calls *cells, kernel, canvas* the clearest category anchor
this product has found, and says none of its three gaps needs a new engine. The framing is therefore claimable
now, and two of its lines are better copy than anything drafted here:

> Telemetry is evidence **about** a system. The audit log is evidence **from** it.

> What you explore with **is** the production artefact.

Hold to that section's direction: never let the analyser be described as a scratchpad, because the risk in the
word notebook is that it reads as disposable while the claim here is the opposite.

**One clause is not claimable yet: handing one artefact to a colleague.** ND-2 states in as many words that the
components all exist and there is no wrapper, so nobody can hand someone a single thing that opens to the same
view, the same evidence and the same story. *Rerun* is fine, because repeatable analyses shipped on 2026-08-27
and the stored report call re-issues exactly. *Hand to someone else* waits for ND-2.

### Further variants under consideration (another session, 2026-09-24)

Recorded verbatim, because each contains a phrase worth taking even though neither carries the hook.

**Variant 1 — more marketing**

> Trust the evidence, not the author.
>
> Turn intent into working logic, then bring the design, code and recorded behaviour together in one notebook.
>
> Compare what happened with what you expected. Discuss the differences with the author — human or AI — and
> improve the design.
>
> Fluxtion makes that conversation concrete: recorded facts, explicit expectations and checks someone else can
> repeat.

**Variant 2 — leans technical**

> Trust the evidence, not the author.
>
> Fluxtion derives an event processor's dispatch order at compile time from its code and declarations.
> Integrated audit capture records execution and the state exposed by participating nodes.
>
> Compare that record against independent expectations, keeping what was observed distinct from what logging
> cannot establish.
>
> Design, code, expectations and recorded behaviour meet in one notebook: a shared working record for reviewing
> the implementation, investigating differences and refining the design, whoever — or whatever — wrote the code.

**What to take from them.**

- **"Keeping what was observed distinct from what logging cannot establish."** The best single clause anyone has
  drafted for this page. It carries the denominator and its limit together, and it is the epistemics of the whole
  product in nine words. Strong candidate for the method section as well as a hero.
- **"The state exposed by participating nodes."** Precise where earlier drafts overstated: it is what nodes chose
  to expose, and *participating* carries the opt-in without a disclaimer. Dry, but accurate.
- **Variant 1's discussion framing resolves a tension worth naming.** Earlier drafts were criticised for making
  the author the interlocutor when the headline says not to trust them. The resolution is that the headline is
  not anti-author, it is pro-evidence: the conversation becomes productive *because* it is grounded. Say
  *settle the differences against the record* rather than *discuss the differences with the author*, and the
  headline and the body stop pulling in opposite directions.
- **"Checks someone else can repeat" is claimable, and is narrower than a handoff.** Repeatable analyses live in
  the project profile, which is portable context by design. What ND-2 lacks is one artefact carrying the view,
  the evidence and the narrative together. So a check someone else can repeat is fair; handing them a single
  document is not. Keep that line where it is.

**What each still misses.**

- **Neither carries the denominator in the hero.** *Recorded behaviour* and *records execution* are tracing's
  sentences. The variant that fixes this is variant 2, and only in its third line rather than its first.
- **Variant 1 has no mechanism at all**, which puts it on the same shelf as every AI coding product shipping
  this year, and *turn intent into working logic* is the crowded-shelf phrasing the alternatives section already
  ruled out.
- **Both drop "you never declare the order."** *From its code and declarations* is accurate and describes every
  dependency-injection framework ever shipped. The order clause is the cheap differentiator; keep it.
- **Variant 2's closing line is three gerunds in a row.** Reviewing, investigating, refining reads as committee
  prose. The notebook sentence is where the category is won, so it should be the liveliest line on the page, not
  the flattest.

### Accuracy constraints every version must respect

Each of these was a real error in a draft, caught in review. They are recorded so the same sentence is not
written again.

- **"The order is derived, not declared"**, never *no graph description is supplied*. The latter is true of the
  annotation route and false of the Spring authoring path, where declarations define the graph. What is true on
  both paths, and still differentiating, is that you never declare the **order**.
- **Auditing is opt-in.** A processor built without it records nothing, and the supported low-latency profile is
  built that way. Say *with auditing on*, or fold it in as *switch auditing on and…*.
- **"What never reported", never "what never ran".** Coverage deliberately reports never-logged rather than
  never-ran, because a node can be silent by design, and a per-node level can leave a node that ran with
  nothing written. Claiming never-ran on the front page would be the exact defect the evidence-integrity
  milestone exists to fix, printed in the largest type on the site.
- **Split the mechanism from the values.** Which callbacks fired, and in what order, is recorded by the
  generated dispatch. The values each node reports come from audit calls its author wrote, so they are the
  author's own claims, and the comparison is what tests them. This strengthens the pitch rather than weakening
  it, and it keeps the headline honest about its own record.
- **Prefer "evidence" to "proof".** Proof invites a demand for tamper resistance. Runtime value escaping is
  still open, so no stronger integrity claim than provenance should be published until it lands.
- **"Correctness" survives as the subject**, per the wording decisions above. Do not weaken it to *conformance*
  or *whether it did what you expected* as the headline noun. The fix for the fair objection is to name who
  supplies the expectation, which *a comparison you can run* already does. What does overreach is making
  evidence the direct object of correctness, as in *correctness becomes evidence rather than testimony*.
- **Assistant fixes arrive as evidence-linked pull requests, never direct edits.** Say *get a fix proposed
  against that evidence*, not *fix it here*. It is also the more credible claim to an operations buyer.
- **Do not claim the analyser diffs two runs and reports the difference.** Cross-run delta is a wanted feature,
  not a shipped one. Do not claim a generated fix brief either; that item is open and gated on pairing the
  journal to the log.

**One reopened decision, recorded so it is not silently relitigated.** The technical framings use *evidence
rather than testimony* in body copy. The wording decisions above reject that form as a headline for a cold
reader, on the grounds that it is inside-baseball. In body copy, to a technical audience, it earns its place —
but it is the same form, and this note exists so the next reader knows it was a choice.

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
- **Slice C (zero-intervention cold start)** is no longer a battery. It was retired and replaced by
  verification tiers, and public acquisition to independently checked output was measured on 2026-09-21. The
  beta's blockers are now its own short list (beta proposal §7), and the first of them — how a tester gets a
  generation key — is a customer journey that has not been run.

Slice A is small and unlocks the strongest sentence on the page. Given what is being claimed, "true under
adversarial test" is the only kind of claim worth making.
