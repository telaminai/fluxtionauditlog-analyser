# Beta: evidence-driven development with an LLM design partner

Status: **proposal, third draft, 2026-09-20.** Nothing here is scheduled or committed. Same discipline as
[`generalization-beyond-n1.md`](../generalization-beyond-n1.md): every claim is tagged *measured* — observed in a
real session against the real toolchain — or *proposed*, which means an inference or a design choice, never a fact.

**Provenance, and its bias.** The observations come from one long cold-start session on 2026-09-19/20 in which an
LLM with no prior Fluxtion exposure built an 8-node principal trading desk, integrated a vendor component shipped
as a jar, added end-of-day reporting, and attacked its own work with injected defects. The participant is also the
author of this document and ended the session favourably disposed. Discount the enthusiasm; the numbers came from
adversarial attempts that mostly failed to break the thing. Single-session findings remain hypotheses under the
existing adoption-threshold rule.

## Review history

**First draft** — ten findings, all addressed in the second draft; four changed the plan: jar A became
authentic-but-incorrect, the harness moved into this repository, the presets were replaced with shipping
templates, and the second-person test was added.

**Second draft** — nine findings (K1–K6 and three smaller), addressed in the third. Two changed the design:
the beta gained its own script, and the tester's LLM client keeps the journal.

**Third draft** — eight findings (L1–L6 and two minor), addressed in the fourth. Two were structural: the
defect moved out of the public template, and the act chain gained a rescue rule.

**Fourth draft** — five findings (M1–M5), addressed in the fifth. M1 changed what A3 measures.

**Fifth draft** — three consequences of that reframe, addressed here. The first was load-bearing: the act was
re-centred on an artefact that had no criterion.

| Finding | Change |
|---|---|
| **N1** A3 was re-centred on the report and §11 gained no row for it, so the act produced an observation and no signal. It is also a judgement — whether a cited record genuinely proves a fault is not a count — which places it in the hand-scored set | **The A3 report goes to the second-person reader**, and §11 gains its row. The instrument, the consent and the scoring shape already existed; *"does a stranger agree this record proves that fault"* is the same question with the author removed, and it removes the hand-scoring conflict rather than managing it (§9, §11) |
| **N2** The rescue rule and the M1 frame collide at A2 — the act most likely to need a rescue. If the operator completes A2, *"the code you just wrote"* is false, the tester notices, and the frame breaks | **A rescued-case variant** of the A3 wording (script), recorded in the operator log — because the rescued case is the **stronger** experiment: the client did not author the code, so the one-line defeat fails outright |
| **N3** A2's cap: putting the reference-mode decision inside the instrumental act places the framework's most confusing vocabulary under a ten-minute cap on the act A3 depends on. An earlier draft counted this as a bonus | **Counted as a cost and removed.** A2's prompt now names the relationship concretely — *"watches that total and reacts when it crosses"* — so the shape falls out without the vocabulary being discovered. **A2 needs the shape, not the discovery.** Whether it is discoverable belongs to the cold-start battery's T3, capped at thirty minutes, where a failure costs one data point rather than three |

---

## 1. What is being tested — and which claim is which

**Primary — the beta tests onboarding.** Can a newcomer reach a verified result using only what ships, and
*what routed them there*? This is what blocks launch.

**Demonstrated, and now also measured — the evidence claim:**

> **You can show what happened and why, without trusting whoever wrote it.**

The demo asserts it. §9 measures it, by handing the evidence pack to a second person who did not run the session.

**The recruitment hook** *(proposed)*: an LLM with no prior exposure produced a verified application here, and
every mistake it made was caught by the evidence rather than by review. That audience is being asked right now
whether machine-written code can be certified and mostly has no answer.

The audience is chosen because it attacks claims for a living: **assured software** — avionics, medical devices,
rail signalling, financial risk.

---

## 2. The arc

Two acts, each ending in a report the tester could hand to their own reviewer. Script:
[`PROMPTS-BETA.md`](coldstart/PROMPTS-BETA.md).

### Act 1 — a fault in code you wrote

1. **Acquire and run** a shipping template (§5). One act, not two: running two templates in a short session
   tests the same thing twice.
2. **Make it yours** — add a node that accumulates one value and records it. This is the first authoring task
   *and* it produces the surface act 3 needs.
3. **The operator seeds one defect into what the tester just wrote**, during a two-minute break, then
   rebuilds and confirms the evidence shows something before handing back. The report must cite the record
   that proves the fault.

Two things this act had to survive. **Seeding into the template** would not outlast a competent engineer — it is
public, and §8 requires a pristine baseline for scoring, so a clean copy is a fetch and a diff away. And
**scoring the discovery** would not outlast one sentence, because the tester's own client authored the code and
still holds it in context.

So the defect goes into authored code, and **what is scored is the artefact, not how they found it** — judged
by the second reader of §9, not by the operator. If A2 needed a rescue the wording changes, since *"the code you
just wrote"* would be false; that case is also the stronger experiment, because the client never authored it. That is
the correction, not a concession: the claim is *you can show what happened and why*, and a report citing the
record tests it directly. Six defects rotate so consecutive testers differ; each carries a precondition, and
A2's two-node requirement exists to guarantee two of them.

### Act 2 — a fault in code you did not write and cannot read

4. Integrate vendor jar **A**. It is authentic and it is wrong. **Reject it.**
5. Integrate vendor jar **B**. It is correct. **Accept and proceed.**
6. Report.

**Act 2 is the destination.** The supply-chain claim made concrete: *accept a component you cannot inspect, on
evidence rather than on the vendor's word.* Act 1 exists to make act 2 legible.

### 7. Invite the attack

> *Here is the application and its evidence pack. Try to make it lie to you.*

That audience will do this anyway, silently, and decide about us without saying so. **Only once §7's schedule is
complete** — otherwise we are inviting them to find our own open hole.

---

## 3. Jar A: authentic, and wrong

The first draft recommended a **tampered** jar — rebuilt with its risk multiplier zeroed. That was a mistake:
slice A makes tampering detectable by digest, so closing the gate would convert the recommended demo into a
structural catch and change the story mid-flight.

**Jar A is a component whose vendor built it honestly and got the logic wrong.** No integrity mechanism can catch
that, because there is nothing to detect: the artefact is exactly what the vendor shipped. Only an independent
calculation finds it.

**Recommended defect: a horizon convention mismatch** *(proposed)*. The vendor's risk figure is annualised; the
consuming specification says one day. Everything builds, every hash matches, the vendor is not at fault, and the
number is out by a factor of about 15.9. Among the most common real supply-chain failures, nobody's misconduct,
and caught on the first record by a check derived from the spec rather than from the component.

**On the dropped multiplier.** §3 rejects it as jar A's defect *and* the injection table uses it as probe 2. These
are different jobs and the operator should know why: **jar A is a narrative** — a story about how components fail
in the real world, where sabotage teaches the wrong lesson. **Injection 2 is a sensitivity probe** — a blunt,
unambiguous fault used to ask whether the tester's own check would have caught it. A probe may be crude; a
narrative may not.

### The boundary, stated

| Kind | Caught by | Status |
|---|---|---|
| **Structural** — non-public node, no accessible wiring constructor | the **toolchain**; the build refuses | *measured*: `com.acmerisk.QuoteFeed is not public in com.acmerisk; cannot be accessed from outside package`, ×6, before any run |
| **Tampering** — artefact altered after release | a **digest in the run receipt** | *measured as a gap*: a jar rebuilt under the same filename left `xmlHash`, `sourceHash` and `recordHash` byte-identical and the build green. Slice A closes this |
| **Authentic and incorrect** — builds clean, green everywhere, wrong numbers | an **independent check**; nothing else can | *measured by analogy*: the zeroed-multiplier jar produced VaR 0.00 on every row with all hashes identical; nine of eleven rows failed the independent calculation. The convention-mismatch artefact **does not exist yet** |

> The compiler refuses what it can prove wrong. A digest catches what was altered. The evidence catches what
> neither can see. Here is precisely where those boundaries sit.

**Hard dependency:** jar A's demo only works if the independent check ships with it, and the check must derive its
expectations from the specification, never from the component's own output.

---

## 4. Tool qualification — question one

In avionics (DO-178C), medical devices (IEC 62304), rail signalling (EN 50128) and financial risk
(model-governance regimes), anything that **produces code that ships** falls under a qualification obligation;
anything that **observes** does not, or falls into a lighter category *(proposed, from reviewer domain knowledge;
not independently verified here)*.

| Piece | Category |
|---|---|
| Fluxtion compiler / starter | **generates code that ships** — in scope for qualification |
| Mongoose, plugins, connectors | runtime libraries — ordinary component obligations |
| The analyser | **observes**; produces no shipped artefact |
| The validation pack (spec, oracle, scenario, mutation test) | **verification evidence about the output** |

> Verifying the output is the recognised alternative to qualifying the tool. The audit log and the independent
> check are that verification evidence. That is why the boundary in §3 is stated rather than blurred.

Do not claim compliance with any named regime. State the categories, offer the evidence, let their qualification
lead draw the conclusion.

---

## 5. Cohort, recruitment and session shape

**Ten named people, approached directly** *(proposed)*. Assured software is a small world; a direct approach gets
answered. Ten warm testers beat a hundred cold ones and can be followed up. Approach message and handover: §13.

**LinkedIn before HN.** LinkedIn reaches the segment, is repeatable and targetable. HN is one-shot, largely
generalist, judges on the first ten minutes, and its likely top comment is *"isn't this just X"*. Hold it until
there are two testimonials and a recorded two-minute demo.

### Templates — from what ships

The first draft named *guided*, *trading* and *logistics*. **No catalogue entry carries those names.** The
catalogue holds fourteen *(read 2026-09-20 via `starter-templates/index.json`)*; the trading domain additionally
depends on a plugin namespace port that has not happened
*(see [coinbase-connection](../coinbase-connection/README.md))*.

**One template, chosen once.** A1 now merges acquisition and first run, so the beta uses a single template
throughout rather than a hello-world followed by a second app.

| Candidate | For | Against |
|---|---|---|
| **Audit analyser bundle** | purpose-built for evidence, which is what acts 2–3 exercise | need to confirm it accepts an added node without ceremony |
| **Fluxtion Spring in Mongoose** | the full stack — AOT, hosted, audit capture — closest to what they would deploy | heavier first run, more to go wrong inside a ten-minute cap |
| **Fluxtion connector** | keyless, interpreted, fastest to a first event | thinnest; may not carry enough for A2's accumulator to be interesting |

Recommendation: **Audit analyser bundle**, subject to confirming A2 is comfortable inside ten minutes on it.

Owner decision, §15. Building three domain templates is a slice in its own right and is not costed here.

### Session shape

**Acts 1–3 is the beta: 45–60 minutes.** Acts 4–7 is a second conversation, 60 minutes, with whoever returns.

The earlier thirty-to-forty figure did not survive arithmetic — the caps alone totalled forty, before preface,
consent, setup and the tester's own questions. Under-budgeting a **dependent chain** produces sessions that end
mid-experiment, which costs more than an honest hour. The chain also needs a rescue rule: a capped prerequisite
scores as failed, then the operator completes it as `operator-completed` and the session continues. A second cap
ends the session. Prepared state for each act is on disk before it starts.

Attrition on two-hour tasks is still severe, and those who finish one were already converted — the least
informative sample. Whoever returns for act 2 is a design-partner candidate, and we know it *because* they
returned.

**One template, fixed for the whole beta.** Varying it across testers would let template choice interact with
the defect rotation and destroy comparability at a sample size that cannot absorb it.

**"Create a project from scratch" is session three**, not part of the beta.

### Interventions — two categories, one of them scored

| | Permitted | Counts in §11 |
|---|---|---|
| **Environment answer** — network, JDK, where files go | yes | **no**; logged as `ASK` |
| **Substantive** — anything about Fluxtion, the project, the tooling, or what to do next | avoid | **yes**; logged verbatim as it happens |

The standard deflection is *"use what the project gives you."* Never name a defect or a trap; let the act fail and
record it capped.

---

## 6. Consent, recording and data handling

A participation gate for this cohort, not a formality. Agree in writing **before** the first approach *(proposed;
subject to whatever legal review you normally apply)*.

- **What is recorded** — transcript, journal, resulting project tree, screen recording if any.
- **Where it is kept**, for how long, who can read it.
- **What is published** — aggregate findings and anonymised quotation; named attribution only on request.
- **Onward disclosure.** §9 requires showing **one tester's evidence pack to a second person outside the
  session**. Either obtain explicit consent for that, naming the role of the reader, or anonymise the pack —
  strip organisation, tester identity and any domain-identifying data — before it leaves the session. State which.
- **LLM client handling.** The tester uses their own client and it keeps the journal (§8), so state plainly what
  leaves their machine, and be ready for the answer *"our client is self-hosted and nothing leaves"*, which is the
  best case and must not break the extraction.
- **If the journal cannot be extracted.** It is written by the tester's client on the tester's machine, and
  extraction can fail — the operator log already concedes as much. Agree the fallback at consent, not at the
  end: operator-side notes against the same `from:` vocabulary, or a screen recording the tester keeps and
  sends. A session whose journal never arrives otherwise yields no routing data at all, which is the measurement
  the beta exists for.
- **Their material.** Nothing the tester brings is retained. Templates and vendor jars are ours.
- **Withdrawal** — they can have their session deleted.

---

## 7. What must exist first — a schedule, not a checklist

**Slice A — integrity (small, self-contained)**
- Dependency coordinates and digests in the run receipt. *(measured gap)*
- No generated shell over a class that exists only in a dependency. *(measured gap)* Listing such a class in
  `nodeBeans` writes an empty class of the same FQCN into the source tree, shadowing the jar, while every stage
  reports success.

**Slice B — truthful echoes (a programme)**
The whole group-B family: stale graph and log after regeneration; spotlights lit in the wrong place and reported
`ok`; marker counts that disagree with the data; a report section silently dropped from the PDF; a pinned chart
window surviving a log change and rendering an empty plot with no explanation. *(all measured)* A claim about
evidence will be measured against the tool that presents it.

**Slice C — zero-intervention cold start (the routing and learning work, plus its acceptance)**
Acceptance 4 and 12 of the journey spec. The v1 session needed two interventions. **This is the beta's real
predecessor** and should be scheduled as such.

**Slice D — beta artefacts**
- Jar A (authentic, convention-mismatched), jar B, and the independent check that catches A.
- **The vendor-integration path**: act 2 is the destination, so it cannot be conditional on an unshipped
  catalogue entry. Either the entry, or a documented manual jar-drop route that works today.
- **The scorer fixture**: one worked journal and its expected scoresheet. Without it, an empty run and a broken
  scorer produce the same sheet — zero entries, zero of one routed — and neither is distinguishable.
- The two-minute break-it demo, scripted and recorded. Template selection per §5.

Slices A and D are small. **B and C are the schedule, and the beta date is whatever C says it is.**

---

## 8. Instrumentation

**The tester's LLM client keeps the journal.** That is the premise, not a workaround: the beta is
*evidence-driven development with an LLM design partner*, so the model does the work and records it while the
human directs and judges. A four-line entry before each action costs the model seconds and the human nothing.
Extraction is trivial — the journal is written into the project directory and the tester sends the directory.

The harness lives at [`coldstart/`](coldstart/):

| File | Use |
|---|---|
| [`HARNESS.md`](coldstart/HARNESS.md) | operator protocol, setup, scoring |
| [`PROMPTS-BETA.md`](coldstart/PROMPTS-BETA.md) | **the beta script** — acts 1–7, intervention rules, defect rotation |
| [`PROMPTS-COLDSTART.md`](coldstart/PROMPTS-COLDSTART.md) | the slice-C battery, subject is a model. **Not the beta script** |
| [`JOURNAL.md`](coldstart/JOURNAL.md) | the format the LLM client fills in |
| [`score_journal.py`](coldstart/score_journal.py) | parses the journal, fingerprints the tree, emits a scoresheet |

**The `from:` field is the routing experiment.** `runbook:` `readme:` `contract:` `error:` `stub:` mean a shipped
artefact did the routing; `search:` `prior` `operator` mean the subject did.

**The tree fingerprint is authoritative for imitation outcomes, not `from:`.** The v1 headline failure is a
hand-rolled harness. A subject who *invents* one has copied nothing, records it as `prior`, and would be invisible
in the imitation section. `T-MAIN` and `T-SLEEP` catch the artefact regardless of what the journal says;
`from: example:<path>` is supporting evidence about *why*, not whether.

**The operator takes live `from:` notes regardless**, as a backup against extraction failure (§6). Four or five
observations across a session is enough to reconstruct the routing picture if the journal is lost.

Always score with `--baseline <pristine download>` so the fingerprints measure the tester and not the template.
Note the tension this creates with A3 and why the defect is seeded into authored code rather than template code:
the same pristine copy that makes scoring possible would make a template-seeded defect trivially diffable.
The transcript is the evidence of record. Spot-check 10% of `from:` fields against it.

---

## 9. The second-person test — measuring the headline

**Procedure.** Take the evidence pack from a completed act 2 — audit log, report, the independent check and its
result, the design, nothing else. **Give it to a second person who did not run the session and does not know what
was wrong.** Ask one question:

> *Is this component fit to use? If not, what is wrong with it, and which record shows you?*

**Record:** whether they reach the same conclusion; whether they can point at the record that proves it; how long
it takes; and **what they asked for that was not in the pack**. That last is the most valuable output in the
beta — a direct list of what the evidence is missing, from someone with no context to fill the gaps.

**These are additional people.** Not among the ten, recruited separately. Two of them, from different testers'
sessions. They need no Fluxtion knowledge — that is the point.

**Recruitment is a criterion, not a question asked at act six.** §11 requires two of two, so the denominator has
to exist before the sessions run. **Onward-disclosure consent is asked at approach** (§13) and at least two
testers must grant it; the anonymised-pack route in §6 is the fallback when they will not. Discovering at act six
that nobody consented would leave this row unscoreable and the headline unmeasured.

**Why this is the right instrument.** The claim is *without trusting whoever wrote it*. A second person who never
met the author, working from the artefact alone, is that claim with the trust removed.

### The same instrument, applied to A3

A3 is scored on whether its report **cites the record that proves the fault** — and deciding whether it does is
a judgement, not a count, which would otherwise land it in the hand-scored set with all the conflict §12 exists
to manage.

It does not have to. Hand the A3 report and its log to the same second reader:

> *Does the record this report cites prove the fault it states?*

Lighter than the act-2 pack — a report, a log, a yes or no with reasoning, roughly ten minutes — and it is the
identical question with the author removed. The instrument, the consent and the scoring shape are already in
place, so this costs one further ask rather than a parallel mechanism.

---

## 10. Freeze the predictions first

Before the first session, write down and seal:

```
how many of ten approach.  how many complete act 1.  how many return for act 2.
where do we expect them to stall.  which seeded defect is diagnosed fastest.
what percentage routed do we expect.  what will they reach for when invited to attack.
will the second-person test succeed, and what will they say is missing.
```

**Pre-register what counts as a successful break** (§11, row 5). Judging it needs full session context, so it
cannot be blinded afterwards — it must be decided before anyone tries. Name the classes that count: producing a
wrong number that the evidence pack does not expose; making the analyser report something the log does not
support; getting a component accepted that the independent check should have rejected.

Then score the **reasons**, not the outcomes. This instrument is already used for software — the vendor experiment
scored 15 of 17 sealed predictions — and has never been pointed at our own strategy.

---

## 11. Success criteria — fixed before starting

*(proposed; adjust the numbers, but fix them now)*

**Denominators**, named rather than numbered because this table has been renumbered twice by findings:
*agree to take part* is out of **ten approached**; the two completion rows, *interventions* and *traps* are out
of **those who started act 1**; *returned for act 2* is out of **those who completed act 1**; *A3 reports* is out
of **completed A3 acts**; *break attempts* is out of **those who reached A7**.

| Signal | Go | Inconclusive | Think again |
|---|---|---|---|
| Agree to take part | ≥ 6 of 10 | 4–5 | ≤ 3 |
| Completed all three acts **unaided** — zero operator-completed | ≥ 6 | 4–5 | ≤ 3 |
| Completed all three acts **with one rescue** | recorded, not scored | — | — |
| **Substantive** interventions | 0–1 per session | 2 | ≥ 3 |
| Traps hit that slice C claims to have closed | 0 | 1 | ≥ 2 |
| Attempted to break the evidence claim, per §10's pre-registration, and failed | ≥ 2 | 1 | nobody tried — wrong audience or wrong framing |
| Returned for act 2 | ≥ 3 | 2 | 0–1 |
| **A3 reports whose cited record a second reader agrees proves the fault** | all but at most one | two fail | three or more fail |
| **Second-person test reaches the right conclusion from the act-2 pack alone** | 2 of 2 | 1 of 2 | 0 of 2 |
| **Design-partner conversation opened** | ≥ 1 | — | 0 |

The A3 row is out of **completed A3 acts**, and it is the criterion the M1 reframe required and the fourth
draft omitted — without it the act produced a recorded observation and no go or think-again signal.

**Unaided means zero operator-completed acts.** The rescue rule (script) lets a session continue after one
capped prerequisite, which created a state the first version of this table could not express: a rescued A1 with
a successful A2 and A3 is neither a pass nor a failure. It is now its own row, recorded and visible rather than
merged into the row above or silently dropped. A second rescue ends the session as incomplete.

**Inconclusive means run five more**, not proceed. At n=10 the difference between four and six is noise; the bands
exist to stop a generous reading, not to do statistics. The last two rows are the objective; everything above is
diagnostics.

---

## 12. Scoring, and the conflict it carries

- **Seal the rubric with the predictions** (§10), before any session runs.
- **Automatic where it can be.** `score_journal.py` produces routing distribution, imitation fingerprints and
  trap hits with no judgement involved.
- **Hand-scored rows are blinded to session identity**, or given to someone who ran no sessions. Preferably both.
  Rows are named here rather than numbered, because §11's table has been renumbered twice by findings.
- **The A3 report row is delegated, not blinded.** Whether a cited record proves a fault is a judgement, and it
  goes to the second reader (§9) rather than to a blinded operator. That removes the conflict instead of
  managing it, and costs one extra ask because the instrument already exists.
- **The break-attempt row cannot be blinded** — judging it needs full session context. §10's pre-registration is
  the substitute control, and it is the only row that relies on one.
- **The second-person test is never run by anyone who ran the session.** That is its entire point, and it now
  covers two artefacts: the act-2 pack and the A3 report.
- The author may operate **or** score, not both, in the same session.

---

## 13. The approach, and what they receive

Not a marketing plan. The minimum that must exist for ten direct approaches to work.

**The approach message** — one paragraph, drawn from [`positioning.md`](../positioning.md)'s
regulated-buyer opener rather than invented again. It must carry: what the claim is; that it is **an hour** with
no preparation; what they get back; and the consent position from §6 stated up front, because for this cohort
that is the first question, not the last.

**Ask for onward-disclosure consent at approach**, not at act six. §9 needs at least two testers willing to have
their evidence pack read by someone outside the session, and §11 scores that row two-of-two. Discovering at the
end that nobody agreed leaves the headline unmeasured. Phrase it as what it is — *"would you be willing for the
report you produce to be read by one other engineer, anonymised if you prefer?"* — and record the answer in the
operator log.

**The handover is a jbang one-liner.** The toolchain installs itself, so there is no prerequisite list and no
pre-flight — a genuine advantage over most beta invitations and worth saying in the message.

**In the meeting** they need only the template, the script, and their own LLM client.

**Deferred to the public phase, explicitly not now:** a landing page, a public sign-up, the HN post. Ten named
people need a paragraph and a calendar link. A website is a dependency of the HN moment, which §5 already holds
until there are two testimonials and a recorded demo. Building it now is scope at the finish line, which is the
standing risk two independent reviewers have raised.

**Easy onboarding instructions are slice C**, not a parallel stream. Cited, not restated.

---

## 14. Risks

- **The audience is the one most likely to break the open claims.** Mitigated only by slices A and B.
- **The beta sits behind slice C.** Naming it as a schedule is the mitigation; pretending otherwise is the failure.
- **The imitation channel is unclosed.** A tester copies the nearest example whatever the prose says. *(measured)*
- **Jar A does not exist**, nor the check that catches it, nor the scorer fixture. Slice D.
- **Corporate network restrictions.** This cohort works behind restrictive networks. Anything requiring an
  outbound connection needs an offline path and a named one-line diagnostic, never a timeout.
- **Single-session provenance.** Every measured claim here is N=1 against the author's own toolchain. The beta is
  partly an attempt to escape that orbit and should be described that way to testers.

---

## 15. Open decisions for the owner

| # | Decision | Recommendation |
|---|---|---|
| 1 | Which defect jar A carries | Horizon/convention mismatch (§3). Needs building |
| 2 | **The** template — one, fixed for the whole beta | **Audit analyser bundle**, if A2 fits its ten-minute cap (§5) |
| 3 | Build the three named domain templates, or defer | **Defer** — building them puts the beta behind another slice |
| 4 | Who scores the hand-scored rows | Not the operator. Blind at minimum (§12) |
| 5 | Who runs the second-person test, and on whom | Two people outside the sessions entirely (§9) |
| 6 | Whether `coldstart/` stays here or moves to a tools path | Left beside the proposal; referenced from §8 |
| 7 | LLM client retention policy, and the self-hosted answer | Needed before the first approach (§6) |
| 8 | Which of the six defects enter the A3 rotation, and whether the scenario contains a boundary case | Consecutive testers must differ. Defect 3 tests nothing without a boundary case — the scenario-gap failure measured twice in v1. Defect 5 (logging before update) is the most instructive |
