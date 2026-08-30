# Round 05 — the control arm. My primary metric did not survive it.

**Attribution round**, n=3 per arm, one variable (the published authoring resources). Predictions
registered at `c543277` before any agent started.

## Results

| | C1 | C2 | C3 | T1 | T2 | T3 |
|---|---|---|---|---|---|---|
| **WENT-OUTSIDE** | 0 | 0 | 1 | 0 | 1 | 0 |
| **Build failures** | 1 | 1 | 1 | 0 | 0 | 1 |
| Constructor fix used | drop `final` | drop `final` | null + lazy init | `@FluxtionIgnore` *preemptively* | `transient` *preemptively* | `transient`, from the triage table after failing |
| Sibling dispatch order | **wrong** | **wrong** | **wrong** | **right** | **wrong** | *honestly unresolved* |
| Task | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

**WENT-OUTSIDE: control mean 0.33, treatment mean 0.33. Identical. Zero separation.**

**Build failures: control 1.0, treatment 0.33 — separated on the mean, but the arms OVERLAP** (T3 failed
once, exactly like every control).

## P1 FALSIFIED — and D-AX5's primary signal is retired

I predicted every T run below every C run on WENT-OUTSIDE. The arms came out **exactly equal**.

Round 04's 4-vs-0 — the sole basis for designating this the primary signal — was **noise plus a harness
artefact**. Of those four instances, one was reading the API key file, and I removed that motive in this
round's setup by telling both arms the key was configured. The remainder was one agent choosing to
decompile a jar out of curiosity. **What WENT-OUTSIDE actually measures is how curious a given run feels
like being**, which varies more between runs than between conditions.

**D-AX5 is corrected**: WENT-OUTSIDE is a useful *qualitative* flag — it names a specific thing the docs
failed to supply — and it is **not** a metric that separates conditions at this sample size. It also
conflates two opposite behaviours: C3 went outside and learned nothing; round 04's control went outside
and got the only correct answer to a question the others got wrong.

## P4 FALSIFIED, in the direction that embarrasses the write-up

I predicted build attempts would **not** separate the arms, having dismissed build/task metrics in D-AX5
as "near-ceiling and uninformative". **Build failures separated them better than anything else measured**
— 3/3 in control, 1/3 in treatment. Not cleanly (T3 overlaps), but it is the only signal that moved.

Correction to the metric itself: count **failures**, not attempts. T2 ran two builds and failed neither.

## P2 CONFIRMED — and I called it falsified after one result

P2 was *"at least one T run hits the constructor-match error"*. **T3 hit it.** So the prediction held.

After T1 came in clean I wrote that P2 was falsified. It says *at least one*; a single clean run could not
falsify it. **That is the third time today I drew a firm conclusion from n=1** — after round 04's
"resolve, not prevent" (itself an n=1 over-claim, corrected below) and the original WENT-OUTSIDE
designation. The instrument built to catch this failure keeps catching it in its author.

**"Resolve, not prevent" is retired.** The resources do both: T1 and T2 applied the remedy preemptively
and never failed; T3 failed and recovered from the triage table in one step. The honest statement is that
it depends on whether the run reads before writing, and I had no basis for either firm version.

## The strongest finding is qualitative, and it is about a wrong belief

**Four of six agents hold the same confidently wrong rule for sibling dispatch order** — "declaration
order in `nodeBeans`" — with the true rule being natural order by node name (round 04, verified by
decompilation).

The wrongness is *structurally reproducible*. The obvious move is to append the new node last in the XML,
and most names chosen (`symbolStats`) sort after the existing `riskCheck`, so declaration order and name
order predict the same thing and the experiment cannot discriminate. Only T1 and T3 happened to pick
`priceStats`, which sorts *before* `riskCheck` — and of those two, T1 concluded the correct rule and T3
looked at the same discriminating evidence and declined to conclude.

So: whether an author learns the truth depends on **what they happened to name their node**. Every one of
the six read the generated source; four still came away wrong. **This gap does not produce uncertainty —
it produces a specific false belief that the author cannot detect.** That is a much stronger argument for
documenting it than "nobody could find it", and it is the round's most useful output.

T3's refusal to assert on non-discriminating evidence was the best epistemic behaviour of the six, and it
is worth noting that it looks identical to ignorance in any report that only records answers.

## Three escape routes, so the diagnostic must state the RULE

The unresourced arm found **three different ways** to make the constructor failure go away — drop `final`
(C1, C2, and round 04's control), leave the field null and initialise lazily (C3) — and neither is the
documented remedy. The resourced arm used the documented one all three times.

**UP-FLX-32 sharpened again:** I previously wrote that the message must "name both remedies". Wrong —
enumerating tricks invites a fourth. The message must state **which fields must be reachable from a
constructor and why**. C3 put it exactly: it *"could not find, anywhere in the project, an explicit
specification of what makes the AOT extractor require a constructor argument"*, and shipped a fix it did
not understand.

## UP-FLX-35, from six sources and five independent agents

All six learned to log values from the project's `RootNode`, not from any resource. Two agents report the
framework canon **self-declaring the gap** (*"explicitly stated it does not document `EventLogNode`/
`auditLog.info()`"*). Four guessed at multi-call semantics — whether repeated `auditLog.info` calls append
or overwrite — and were right without reading it anywhere.

**Correction to the ask.** I filed UP-FLX-35 after checking five sources and never fetching a sixth,
`fluxtion-golden-path.md`, which is in my own proposed agreed set. Having now read it: it **does** carry a
worked `auditLog.info(k,v).info(k,v)` chaining example and `addEventAudit(LogLevel.INFO)` — three
treatment agents used it. It **never names** `EventLogNode`, `EventLogSource` or `setLogger`, and never
states the contract; it assumes the reader knows.

So the ask narrows and improves: **usage is demonstrated, the contract is documented nowhere.** A minor
sibling finding — golden-path names `@FluxtionIgnore` without its package, which is why T2 chose
`transient` rather than chase the FQCN.

## What round 05 changes

1. **WENT-OUTSIDE retired as the primary metric** (D-AX5) — equal across arms at n=3.
2. **Build failures promoted** as the best available quantitative signal, with the caveat that it overlaps.
3. **"Resolve, not prevent" retired** — the resources do both.
4. **UP-FLX-32** must state the rule, not a list of remedies. Three escapes found.
5. **UP-FLX-35** narrowed to the *contract*, and corrected for a sixth source I had not read.
6. **New ask candidate**: sibling dispatch order, because the gap manufactures a false belief in 4 of 6.

## Honest limits

n=3, one task, one model family, one machine. The arms overlap on the only signal that moved, so this
supports a **direction** and no effect size. And the round's most valuable output was not a number — it
was noticing *what the agents believed*, which no metric here was designed to capture.

## Post-round owner correction — a fourth route, which none of the six found

Owner, 2026-08-30: *"Fluxtion supports the JavaBean pattern to set references if constructor references
are not a good fit or problematic."*

**Verified in `LiveGraphSourceGenExtractor`** — three assignment strategies, invoked from one entry point:

```
generatePropertyAssignments()       // JavaBean setters — beanPropertyMap, "set" prefix
generatePublicMemberAssignments()   // public fields
generateComplexConstructors()       // constructors — the only one that throws
```

**I had already read this class.** `beanPropertyMap` and `generatePropertyAssignments` are both in the
javap dump I used to write UP-FLX-32, and I did not register what they meant — I wrote the ask as though
constructor reachability were the only route. That is the fifth over-narrow rule of the day and the
second one where the evidence was already in front of me.

**Why it matters more than a missing option.** The failure message names the strategy that failed and
says nothing about the two that would have worked. Six agents, and **not one used a setter**; three
invented accidental escapes instead. The gap is not "the docs omit a feature" — the *diagnostic actively
narrows the reader's option space*, which is the sharpest possible version of §1c's argument.

Consequence: the doc set's rule 4.3 is corrected, and UP-FLX-32 now asks for a message that forks on the
real question — is this field node-local **state** (exclude it) or a **reference** the graph must supply
(wire it, three ways) — rather than teaching one trick.
