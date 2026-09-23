# Beta: evidence-driven development with an LLM design partner

Status: **proposal, sixth draft, 2026-09-21 — rewritten after the public release.** Nothing here is scheduled or
committed. Same discipline as [`generalization-beyond-n1.md`](../generalization-beyond-n1.md): every claim is
tagged *measured* — observed in a real session against the real toolchain — or *proposed*, which means an
inference or a design choice, never a fact.

**Provenance, and its bias.** The first five drafts rested on one long cold-start session (2026-09-19/20) whose
participant also wrote this document. That orbit is now slightly wider: a separate uncoached session built three
rounds of work on the public Spring/Mongoose template on 2026-09-21
([report](../../handoff/evidence/unguided-session-2026-09-21/session-report.md)), and a fresh client acquired the
public analyser bundle and ran it to independently checked output
([release report](../../handoff/report_release_journey_2026_09_21.md)). Every session was still operated by the
author's own team against the author's own toolchain. Single-session findings remain hypotheses under the
existing adoption-threshold rule.

## What changed in the sixth draft

The beta was written to sit behind a cold-start programme ("slice C") that had not happened. It has now happened,
in a different form, and the proposal described a world that no longer exists.

| Then (fifth draft) | Now (measured, 2026-09-21) |
|---|---|
| Slice C: a cold-start **battery** gates the beta; *"the beta date is whatever C says it is"* | **Battery retired** by owner decision and replaced by [verification tiers](../../handoff/report_starter_verification_tiers_2026_09_20.md): static checks on every commit, an unattended empty-cache public preflight on publication, and one acquisition spot-check when routing paths change |
| Public acquisition: *"the remaining initial beta client trial"* | **Passed**, with limits: a fresh client acquired the public bundle, built it, ran and stopped it with the shipped helpers, all five sample rows independently verified, all 43 files byte-identical, 388 s, zero coaching ([release report](../../handoff/report_release_journey_2026_09_21.md)) |
| The tester's client keeps a `JOURNAL.md` | **Retired.** Three of three usable preview journals carried fabricated timing. Timing and reads now come from observed tool events; the subject is asked only for a short source pointer (§8) |
| Slice B, truthful echoes | Now [`spec-tool-agreement.md`](../../specs/spec-tool-agreement.md): 13 analyser-owned disagreements baselined, none built yet |
| Slice A, integrity | **Deliberately excluded** from the release. Still open |
| *"The handover is a jbang one-liner… no prerequisite list"* | **False for Act 1.** A2 changes the graph, and regenerating needs a Fluxtion API key; the customer key journey is untested (§7, B1) |

The design of the acts, the jar A story, the second-person test and the scoring discipline survive unchanged.
They were never the problem.

## Review history

Five review rounds shaped the fifth draft; their findings and resolutions are preserved in this file's git history
(commit `752d535` and earlier). The load-bearing ones, still in force: jar A is authentic but incorrect (first
round); the beta has its own script and tester-owned recording (second); the A3 defect is seeded into code the
tester wrote, not the public template, and prerequisite acts have a rescue rule (third); A3 is scored on its
report, not on how the fault was found (fourth); that report goes to the second reader, and A2 names the node
relationship concretely rather than making testers discover the reference-mode vocabulary (fifth).

**Sixth draft** — rewritten against the public release rather than reviewed. It has not been through a review
round, and should be before any approach is made.

---

## 1. What is being tested — and which claim is which

**Acquisition is no longer the question.** A fresh client reaching sample output from the public route is
*measured*. What remains unmeasured is everything a person does after that: changing the system, finding a fault
in it, and deciding whether to trust a component they cannot read.

**Primary — the beta tests the evidence loop with a human in it.** Can a newcomer, working with their own LLM
client, change a system, and show from the evidence what happened and why? And *what routed them there*?

**The claim being measured:**

> **You can show what happened and why, without trusting whoever wrote it.**

The demo asserts it. §9 measures it, by handing the evidence to a second person who did not run the session.

**The recruitment hook** *(proposed)*: an LLM with no prior exposure produced a verified application here, and
every mistake it made was caught by the evidence rather than by review. That audience is being asked right now
whether machine-written code can be certified and mostly has no answer. The hook claims onboarding and evidence;
it never claims certification (§3, §7).

The audience is chosen because it attacks claims for a living: **assured software** — avionics, medical devices,
rail signalling, financial risk.

---

## 2. The arc

Two acts, each ending in a report the tester could hand to their own reviewer. Script:
[`PROMPTS-BETA.md`](coldstart/PROMPTS-BETA.md).

### Act 1 — a fault in code you wrote

1. **Acquire and run** the chosen template (§5). The beta hands over a direct link, so A1 measures running, not
   website discovery — discovery is a public-launch problem, tracked separately (§7).
2. **Make it yours** — add two nodes: one keeps a running total of a value in the data, the second watches that
   total and reacts when it crosses a threshold. This is the first authoring task *and* it produces the surface
   act 3 needs. **It is a graph change, so it needs regeneration** — see §7, B1 and B2.
3. **The operator seeds one defect into what the tester just wrote**, during a two-minute break, then rebuilds and
   confirms the evidence shows something before handing back. The report must cite the record that proves the
   symptom, and carry what supports the cause: the fix diff and the changed source files.

Two things this act had to survive. **Seeding into the template** would not outlast a competent engineer — it is
public, so a clean copy is a fetch and a diff away. And **scoring the discovery** would not outlast one sentence,
because the tester's own client authored the code and still holds it in context. So the defect goes into authored
code, and **what is scored is the artefact, not how they found it** — judged by the second reader of §9. If A2
needed a rescue the wording changes, since *"the code you just wrote"* would be false; that case is also the
stronger experiment, because the client never authored it. Six defects rotate so consecutive testers differ; each
carries a precondition, and A2's two-node requirement exists to guarantee two of them.

### Act 2 — a fault in code you did not write and cannot read

4. Integrate vendor jar **A**. It is authentic and it is wrong. **Reject it.**
5. Integrate vendor jar **B**. It is correct. **Accept and proceed.**
6. Report.

**Act 2 is the destination.** The supply-chain claim made concrete: *accept a component you cannot inspect, on
evidence rather than on the vendor's word.* The integration route is now published and verified
([worked example](https://telaminai.github.io/fluxtionauditlog-analyser/integrating-a-vendor-component/)), so act 2
no longer waits on an unshipped catalogue entry.

### 7. Invite the attack

> *Here is the application and its evidence pack. Try to make it lie to you.*

That audience will do this anyway, silently, and decide about us without saying so. **Only once the integrity and
truthful-echo work in §7 has closed** — otherwise we are inviting them to find our own open hole.

---

## 3. Jar A: authentic, and wrong

**Jar A is a component whose vendor built it honestly and got the logic wrong.** No integrity mechanism can catch
that, because there is nothing to detect: the artefact is exactly what the vendor shipped. Only an independent
calculation finds it. (The first draft recommended a *tampered* jar instead; that was a mistake, because a digest
in the run receipt would turn the demo into a structural catch.)

**Recommended defect: a horizon convention mismatch** *(proposed)*. The vendor's risk figure is annualised; the
consuming specification says one day. Everything builds, every hash matches, the vendor is not at fault, and the
number is out by a factor of about 15.9. Among the most common real supply-chain failures, nobody's misconduct,
and caught on the first record by a check derived from the spec rather than from the component.

**On the dropped multiplier.** The injection table uses a zeroed multiplier as a sensitivity probe. That is a
different job: jar A is a narrative about how components really fail; the probe is a blunt fault used to ask
whether the tester's own check would have caught it. A probe may be crude; a narrative may not.

### The boundary, stated

| Kind | Caught by | Status |
|---|---|---|
| **Structural** — non-public node, no accessible wiring constructor | the **toolchain**; generation refuses | *measured*: `com.acmerisk.QuoteFeed is not public in com.acmerisk`, ×6, before any run |
| **Tampering** — artefact altered after release | a **digest in the run receipt** | *measured as a gap*: a jar rebuilt under the same filename left every receipt hash byte-identical and the build green. Integrity work is excluded from the current release and remains open |
| **Authentic and incorrect** — builds clean, wrong numbers | an **independent check**; nothing else can | *measured by analogy*: the zeroed-constant jar produced VaR 0.00 on every row; 9 of 11 rows failed the independent calculation, re-verified from the [preserved evidence](../../handoff/evidence/vendor-integration-2026-09-19/). The convention-mismatch source and local jars now exist (`b05f6e2`, 2026-09-23): A mismatches 5 of 6 synthetic rows and B matches all 6 against the spec-derived oracle. Publication and the integrated beta run remain open |

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
> check are that verification evidence.

Do not claim compliance with any named regime. State the categories, offer the evidence, let their qualification
lead draw the conclusion.

---

## 5. Cohort, recruitment and session shape

**Ten named people, approached directly** *(proposed)*. Assured software is a small world; a direct approach gets
answered. Ten warm testers beat a hundred cold ones and can be followed up. Approach message and handover: §13.

**LinkedIn before HN.** LinkedIn reaches the segment, is repeatable and targetable. HN is one-shot, judges on the
first ten minutes, and its likely top comment is *"isn't this just X"*. Hold it until there are two testimonials
and a recorded two-minute demo.

### The template — what the public release actually offers

One template, fixed for the whole beta: varying it would let template choice interact with the defect rotation at a
sample size that cannot absorb it. The candidates, as they stand on the public route *(measured, 2026-09-21)*:

| Template | Acquisition | A2 — adding nodes | Verdict |
|---|---|---|---|
| **Audit analyser bundle** | **verified** by a fresh client, five rows checked | ships a pre-generated processor and **no** local authoring scripts; its README: *"A key is needed only to REGENERATE after a graph change"* | acquisition proven; A2 needs a key and hand-written node classes |
| **Fluxtion Spring (standalone)** | verified by the 1.0.73 release preflight | ships `setup`/`validate`/`generate`; stub reconciliation is local, generation needs a key; new-node stubs cannot audit until hand-fixed (#6, [issue 3](https://github.com/telaminai/fluxtionauditlog-analyser/issues/3)) | the only template with a verified authoring route |
| **Fluxtion Spring in Mongoose** | public template | no local authoring files (**SG-2**, open) | not usable for A2 until SG-2 closes |

The fifth draft recommended the bundle "subject to confirming A2 is comfortable inside ten minutes on it". **That
confirmation has not been made, and the table shows why it matters**: A2 is a graph change on every local template,
so every tester needs a key (§7, B1). **Recommendation** *(proposed)*: choose the standalone Spring template, the
only one with a verified authoring route, and decide the bundle only after a dry run of A1–A2 on it. Owner decision,
§15.

### Session shape

**Acts 1–3 is the beta: 45–60 minutes.** Acts 4–7 is a second conversation, 60 minutes, with whoever returns.
Under-budgeting a **dependent chain** produces sessions that end mid-experiment, which costs more than an honest
hour. The chain has a rescue rule: a capped prerequisite scores as failed, then the operator completes it as
`operator-completed` and the session continues. A second cap ends the session. Prepared state for each act is on
disk before it starts. Whoever returns for act 2 is a design-partner candidate, and we know it *because* they
returned. **"Create a project from scratch" is session three**, not part of the beta.

### Interventions — two categories, one of them scored

| | Permitted | Counts in §11 |
|---|---|---|
| **Environment answer** — network, JDK, where files go, key provisioning (§7, B1) | yes | **no**; logged as `ASK` |
| **Substantive** — anything about Fluxtion, the project, the tooling, or what to do next | avoid | **yes**; logged verbatim as it happens |

The standard deflection is *"use what the project gives you."* Never name a defect or a trap.

---

## 6. Consent, recording and data handling

A participation gate for this cohort, not a formality. Agree in writing **before** the first approach *(proposed;
subject to whatever legal review you normally apply)*.

- **What is recorded** — transcript, observed tool events, the resulting project tree, screen recording if any.
- **Where it is kept**, for how long, who can read it. Raw provider records are not published, as in the release
  acquisition run.
- **What is published** — aggregate findings and anonymised quotation; named attribution only on request.
- **Onward disclosure.** §9 shows **one tester's evidence pack to a second person outside the session**. Obtain
  explicit consent naming the role of the reader, or anonymise the pack before it leaves the session. State which.
- **LLM client handling.** The tester uses their own client, so state plainly what leaves their machine, and be
  ready for *"our client is self-hosted and nothing leaves"* — the best case, which must not break recording.
- **If tool events cannot be captured** from the tester's client, agree the fallback at consent: operator notes
  against the same `from:` vocabulary, or a screen recording the tester keeps and sends.
- **Keys.** If a key is provisioned for the tester (§7, B1), say who owns it, what it can do, and that it is
  revoked afterwards. Keys are never put in the project.
- **Their material.** Nothing the tester brings is retained. Templates and vendor jars are ours.
- **Withdrawal** — they can have their session deleted.

---

## 7. What must exist first — now a short list

The fifth draft's slices A–D are replaced by the table below. Each blocker names its evidence.

**Blocks acts 1–3**

| # | Blocker | Evidence | Resolution options |
|---|---|---|---|
| **B1** | **A2 needs a Fluxtion API key, and the customer key journey is untested** | bundle README; the SG-1 release report: generation "separately provisioned", *"Customer credential acquisition… not established"* | (a) test the key journey end to end first — it is what a real customer meets next; (b) the operator provisions a revocable key per tester (§6); (c) redesign A2 so it changes node logic without changing the graph, at the cost of the two-node surface defects 1 and 2 need |
| **B2** | New-node stubs cannot audit (#6) — and A2's own watch-for is whether values reach the audit log | re-observed on public 1.0.73; hand workaround [verified reconcile-safe](../../handoff/evidence/stub-reconcile-1.0.73-2026-09-21/README.md); [issue 3](https://github.com/telaminai/fluxtionauditlog-analyser/issues/3) | fix the stubs before the beta, or accept that A2 measures #6 rather than the tester. **Fix first** *(proposed)* — it is small, and it is upstream of the "always use the generator" advice |
| **B3** | The template decision | §5 | a dry run of A1–A2 on the chosen template by someone other than the author |

**Blocks act 2**

| # | Blocker | Evidence |
|---|---|---|
| **B4** | Jar A (convention mismatch), jar B, and the spec-derived check: **built and locally checked**, source commit `b05f6e2`. Public resolution and integrated beta run remain open; see M67.1 | §3 |

**Blocks act 7 and any public claim beyond onboarding**

| # | Blocker | Evidence |
|---|---|---|
| **B5** | Integrity: dependency digests in the run receipt; no generated shell over a dependency-only class | excluded from the release; [issue 2](https://github.com/telaminai/fluxtionauditlog-analyser/issues/2) for shadowing |
| **B6** | Truthful echoes | [`spec-tool-agreement.md`](../../specs/spec-tool-agreement.md): 13 analyser-owned rows open. *Proposed:* acts 1–3 wait only for its P0 items (TA-1 to TA-4), which are the ones a tester meets first — a false "different build" warning, and confirmations labelled "WHAT IS WRONG" |

**Does not block the beta, blocks the public launch**

- **Discovery from the website root.** Both acquisition attempts that reached the site probed guessed routes and
  scraped its JavaScript; the passing one used the catalogue JSON and the site's encoder rather than the documented
  shortcut *(measured)*. Tracked as the "short entry point" item. The beta hands over a direct link, so it does not
  measure this.
- **SG-2**, unless the Mongoose template is chosen.

**No battery is needed** before the beta, and none is proposed. Acquisition is measured; the tiers keep it true.
What the beta measures — a human changing a system and reasoning from its evidence — is not something the battery
ever measured.

---

## 8. Instrumentation

**Observed tool events replace the journal.** The fifth draft had the tester's client keep a four-line journal
before each action. The preview trials showed why that fails: three of three usable journals carried fabricated
timing, and entries were written after the actions they describe *(measured)*. The release acquisition run used an
observer-clock event recorder instead, with drift under 0.01 s *(measured)*.

**The source pointer is requested, not relied on.** Subjects are asked for a one-line `from:` pointer before each
batch of actions. In the release acquisition run, **84 of 87 tool batches had none** *(measured)*. Missing pointers
stay in the denominator as *unattributed* — never dropped, never reconstructed from file reads. Observed reads say
*what* was opened; only a pointer says *why*.

**The tree fingerprint remains authoritative for imitation outcomes.** The v1 headline failure is a hand-rolled
harness, which a subject who invents one would never record as imitation. `T-MAIN` and `T-SLEEP` catch the artefact
regardless of what anyone says. The scorer's fingerprints are regression-tested against a preserved corpus
(`tools/check_coldstart_corpus.py`).

Harness files live at [`coldstart/`](coldstart/). **Status:** `PROMPTS-BETA.md` is the beta script;
`PROMPTS-COLDSTART.md` and `HARNESS.md` describe the retired battery and are kept for record; `JOURNAL.md` is
superseded by tool-event recording; `score_journal.py`'s tree fingerprints remain in use.

Always score against the **pristine download** so fingerprints measure the tester, not the template — which is also
why the A3 defect is seeded into authored code (§2). The transcript is the evidence of record.

---

## 9. The second-person test — measuring the headline

**Procedure.** Take the evidence pack from a completed act 2 — audit log, report, the independent check and its
result, the design, nothing else. **Give it to a second person who did not run the session and does not know what
was wrong.** Ask one question:

> *Is this component fit to use? If not, what is wrong with it, and which record shows you?*

**Record:** whether they reach the same conclusion; whether they can point at the record that proves it; how long
it takes; and **what they asked for that was not in the pack** — the most valuable output in the beta, a direct list
of what the evidence is missing from someone with no context to fill the gaps.

**These are additional people**, not among the ten, recruited separately: two of them, from different testers'
sessions. They need no Fluxtion knowledge — that is the point. **Recruitment is a criterion, not a question asked at
act six**: onward-disclosure consent is asked at approach (§13), and the anonymised-pack route (§6) is the fallback.

**Why this is the right instrument.** The claim is *without trusting whoever wrote it*. A second person who never
met the author, working from the artefact alone, is that claim with the trust removed.

### The same instrument, applied to A3

Hand the A3 report, its logs, the fix diff and the changed source files to the same second reader: *"Does the
record this report cites prove the symptom it states — and does the pack support the cause it states?"* A yes or
no on each half; the report passes only if both are yes. Roughly ten minutes, the identical question with the
author removed, and it removes the hand-scoring conflict instead of managing it.

**Why two halves (2026-09-21).** The single question — does the record *prove the fault* — drew *partly* from two
blind second readers, for the same sound reason: a record proves what the system **did**, and the stated cause was
a claim about what the code **says**, which no record can prove. Asked that way a strict reader never answers yes
for a code-level cause, so the row measured the question rather than the report. With the question split and the
changed sources in the pack, a third reader answered yes to both. Evidence:
`.local-evidence/coldstart-v2-2026-09-20/reader-a3-*` (git-ignored); n=1 per wording.

---

## 10. Freeze the predictions first

Before the first session, write down and seal:

```
how many of ten approach.  how many complete act 1.  how many return for act 2.
where do we expect them to stall.  which seeded defect is diagnosed fastest.
what share of tool batches will carry a source pointer.  what will they reach for when invited to attack.
will the second-person test succeed, and what will they say is missing.
```

**Pre-register what counts as a successful break** (§11). Name the classes that count: producing a wrong number the
evidence pack does not expose; making the analyser report something the log does not support; getting a component
accepted that the independent check should have rejected.

Then score the **reasons**, not the outcomes. The instrument is proven on software — the vendor experiment scored
15 of 17 sealed predictions, and the release acquisition run published every miss, including a 148-second duration
error and the failed attribution prediction — and has never been pointed at our own strategy.

---

## 11. Success criteria — fixed before starting

*(proposed; adjust the numbers, but fix them now)*

**Denominators**, named rather than numbered: *agree to take part* is out of **ten approached**; the completion
rows, *interventions* and *traps* are out of **those who started act 1**; *returned for act 2* is out of **those
who completed act 1**; *A3 reports* is out of **completed A3 acts**; *break attempts* is out of **those who reached
A7**.

| Signal | Go | Inconclusive | Think again |
|---|---|---|---|
| Agree to take part | ≥ 6 of 10 | 4–5 | ≤ 3 |
| Completed all three acts **unaided** — zero operator-completed | ≥ 6 | 4–5 | ≤ 3 |
| Completed all three acts **with one rescue** | recorded, not scored | — | — |
| **Substantive** interventions | 0–1 per session | 2 | ≥ 3 |
| v1 traps hit (`T-MAIN`, `T-EVENTLOG`, `T-NODEBEANS-JAR`, `T-RETURN-TRUE`) | 0 | 1 | ≥ 2 |
| Attempted to break the evidence claim, per §10's pre-registration, and failed | ≥ 2 | 1 | nobody tried — wrong audience or wrong framing |
| Returned for act 2 | ≥ 3 | 2 | 0–1 |
| **A3 reports a second reader agrees on both halves: the cited record proves the symptom, and the pack supports the cause** | all but at most one | two fail | three or more fail |
| **Second-person test reaches the right conclusion from the act-2 pack alone** | 2 of 2 | 1 of 2 | 0 of 2 |
| **Design-partner conversation opened** | ≥ 1 | — | 0 |

**Unaided means zero operator-completed acts.** A rescued act is recorded in its own row, neither a pass nor a
failure; a second rescue ends the session as incomplete. **Inconclusive means run five more**, not proceed. The
last two rows are the objective; everything above is diagnostics.

**If B2 is not fixed before the beta**, a `T-EVENTLOG` hit on A2 measures the product, not the tester. Record it
separately and do not count it in the traps row.

---

## 12. Scoring, and the conflict it carries

- **Seal the rubric with the predictions** (§10), before any session runs.
- **Automatic where it can be.** Tree fingerprints and pointer counts involve no judgement.
- **Hand-scored rows are blinded to session identity**, or given to someone who ran no sessions. Preferably both.
- **The A3 report row is delegated, not blinded** — it goes to the second reader (§9).
- **The break-attempt row cannot be blinded**; §10's pre-registration is the substitute control.
- **The second-person test is never run by anyone who ran the session.**
- The author may operate **or** score, not both, in the same session.

---

## 13. The approach, and what they receive

Not a marketing plan. The minimum that must exist for ten direct approaches to work.

**The approach message** — one paragraph, drawn from [`positioning.md`](../positioning.md)'s regulated-buyer
opener. It must carry: what the claim is; that it is **an hour**; what they get back; the consent position from §6;
and **the key position from §7, B1** — whether we provide one, or they obtain one, and how long that takes.

**Everything sent to a tester is a publication.** The approach message, the handover and anything shown in a
session describe only released capability and the public claims in positioning. Nothing unreleased goes into a
message, a demo or a slide.

**Ask for onward-disclosure consent at approach**, not at act six: *"would you be willing for the report you produce
to be read by one other engineer, anonymised if you prefer?"* Record the answer in the operator log.

**The handover is a direct link** to the chosen template, plus the key arrangement. The fifth draft said "no
prerequisite list and no pre-flight"; that stays true for acquiring and running, and is false for A2 until B1 is
resolved.

**In the meeting** they need only the template, the script, and their own LLM client.

**Deferred to the public phase:** a landing page, a public sign-up, the HN post — and the website-discovery fix
(§7), which the public launch needs and the beta does not.

---

## 14. Risks

- **The audience is the one most likely to break the open claims.** Mitigated by B5 and B6 before act 7.
- **Key provisioning is on the critical path of A2** (B1), and the customer journey for it has never been run.
- **The product can fail A2 on the tester's behalf** (B2) — and would be scored as the tester's failure unless §11's
  carve-out is applied.
- **Tester clients fail for their own reasons.** The preview trials saw five provider refusals in six sessions, and
  the release acquisition needed three attempts after two authentication failures *(measured)*. Agree what happens
  to a session whose client fails, at consent, and never count a client failure as a product result.
- **Attribution is weak.** Subjects mostly omit source pointers (84 of 87 batches). Routing claims from the beta will
  rest on observed reads plus the few pointers given, and should say so.
- **The imitation channel is unclosed.** A tester copies the nearest example whatever the prose says. *(measured)*
- **Corporate network restrictions.** Anything requiring an outbound connection — including key-gated generation —
  needs a named one-line diagnostic, never a timeout. The wrapper bootstrap already failed in one client sandbox
  *(measured)*.
- **Narrow provenance.** Every measured claim here comes from sessions the author's team operated. The beta is partly
  an attempt to escape that orbit and should be described that way to testers.

---

## 15. Open decisions for the owner

| # | Decision | Recommendation |
|---|---|---|
| 1 | **B1** — how testers get a key | **Test the customer key journey first**, then decide whether to provision. It is what a real customer meets straight after acquisition |
| 2 | **B2** — fix new-node stubs before the beta | **Yes.** Otherwise A2 measures #6 |
| 3 | **The template** | **Standalone Spring**, unless a dry run of A1–A2 on the bundle succeeds within its caps (§5) |
| 4 | Which defect jar A carries | Horizon/convention mismatch (§3). Needs building |
| 5 | Do acts 1–3 wait for the tool-agreement P0 items | **Yes** — TA-1 to TA-4 are what a tester meets first |
| 6 | Who scores the hand-scored rows | Not the operator. Blind at minimum (§12) |
| 7 | Who runs the second-person test, and on whom | Two people outside the sessions entirely (§9) |
| 8 | LLM client retention policy, the self-hosted answer, and the failed-client rule | Needed before the first approach (§6, §14) |
| 9 | Which of the six defects enter the A3 rotation | Consecutive testers must differ. Defect 3 needs a boundary case in the scenario. Defect 5 (logging before update) is the most instructive |
| 10 | Review this draft | One review round before any approach — it was rewritten, not reviewed |
