# Beta session script — give verbatim, one act at a time

> **Scope.** This is the script for the beta described in [the proposal](../README.md). The subject is a
> **human assurance engineer working with their own LLM client as a design partner**. It is not the
> [cold-start battery](PROMPTS-COLDSTART.md), whose subject is a model and whose purpose is measuring routing
> in the starter. The two share the intervention rules below and the `from:` vocabulary in
> [`JOURNAL.md`](JOURNAL.md), and nothing else.

**Session one is acts 1–3: 45–60 minutes.** Session two is acts 4–7, 60 minutes, with whoever returns.

The earlier thirty-to-forty figure did not survive arithmetic: the caps alone totalled forty, before preface,
consent, setup and the tester's own questions. Under-budgeting a dependent chain produces sessions that end
mid-experiment, which is worse for attrition than an honest hour.

---

## Preface — say once, then A1

> You'll work with your own LLM client as a design partner. Ask it to keep a running journal in `JOURNAL.md` —
> the format is in that file, four lines an entry, written before each action. It writes the journal, not you.
>
> I'll give you one task at a time. I won't answer questions about Fluxtion, the project or the tooling — use
> what the project gives you. Ask me anything about the environment.

Confirm consent and journal extraction are agreed **before** A1, not after (proposal §6).

---

## Intervention rules — binding on the operator

Two categories, and only one is scored.

| | Permitted | Counts in proposal §11 |
|---|---|---|
| **Environment answer** — network, JDK, where files go, is my screen shared | yes | **no**; logged as `ASK` |
| **Substantive** — anything about Fluxtion, the project, the tooling, or what to do next | avoid | **yes**; logged verbatim as it happens |

The standard deflection is *"use what the project gives you."* Never name a defect, a file or a trap.

If asked whether this is a test: *"Yes, of the starter, not of you. Carry on as you would normally."*

### When an act caps — the rescue rule

The acts are a **dependent chain**: A2 needs A1's output, A3 needs A2's. The battery's *"let it fail and record
it capped"* is right for independent tasks and wrong here — one slow toolchain install would cost all three data
points.

So, when a prerequisite act reaches its cap:

1. **Score that act as failed.** It is a real result and it goes in the scoresheet.
2. **The operator then completes it** — or hands over a prepared working state — and records it
   `operator-completed`. This is **not** a substantive intervention; it is a rescue, logged separately.
3. **The session continues.** Later acts still produce their data.
4. **If a second act caps, stop.** Record the session incomplete; three rescues is no longer an experiment.

Have the prepared state for each act on disk before the session starts.

---

# Session one — 45–60 minutes

## A1 · Acquire and run — cap 10 minutes

> Get a Fluxtion application running from the `<template>` template, and show me it processing an event.

Handover is the jbang one-liner; the toolchain installs itself. Acquisition and first run are one act, not two —
running two templates in a short session tests the same thing twice.

**Watch for:** where they go first, and whether the LLM finds the route or guesses.

## A2 · Make it yours — cap 10 minutes

> Add two nodes of your own: one that keeps a running total of a value in the data, and a second that watches
> that total and reacts when it crosses a threshold.

**Two nodes, not one, and the relationship is named concretely on purpose.** A lone accumulator has no
dependent and no node-to-node reference, so half the defect rotation could not be seeded into it. *"Watches
that total and reacts when it crosses"* produces the shape in plain English, **without requiring the tester to
discover the reference-mode vocabulary.**

That omission is deliberate and it reverses an earlier judgement. A previous draft counted "it exercises the
reference-mode decision" as a bonus. **It is a cost here.** A2 is instrumental — it exists to create the
seeding surface, and A3 depends entirely on it. Putting the framework's most confusing vocabulary under a
ten-minute cap, on the act the rest of the session rests on, places the highest-variance learning task on the
critical path. `DATA` and `TRIGGER` are not parallel terms and careful readers converge on misreading them;
that is why a rename has been proposed.

**A2 needs the shape, not the discovery.** Whether the vocabulary is discoverable is a real question with a
better home: the cold-start battery's T3, capped at thirty minutes, where it is the whole point of the task and
a failure costs one data point instead of three.

**Nothing in the prompt says to record anything.** An earlier version ended *"and records it so you can see it
afterwards"*, which nudged toward the exact thing the watch-for measures. Whether they make the value
observable, and whether they get there by imitating the template's own nodes, **is** the measurement.

**Watch for:** whether the value reaches the audit log or only the console, and whether the template's existing
nodes were the route.

**If they log nothing at all**, that is a finding rather than a failure, and A3 still works — seed a structural
defect whose evidence is which nodes ran. See the seeding procedure.

## A3 · Something is wrong — cap 20 minutes

**Operator takes a two-minute break and seeds one defect into the code the tester wrote in A2.** Not into the
shipped template.

> *"Give me two minutes to set the next part up."*

This is the fix for a fatal weakness in the previous draft. The template is public and the proposal itself
requires a pristine baseline download for scoring, so a defect seeded into template code can be found by fetching
a clean copy and diffing — which is the rational first move for a competent engineer and would destroy the act in
under a minute. **There is no pristine copy of code the tester wrote ten minutes ago.**

> Something in the code you just wrote is now wrong. Find out what it is, fix it, and produce a report that
> shows me **which record in the evidence proves it**.

**If A2 was rescued, use this instead:**

> Something in this application is now wrong. Find out what it is, fix it, and produce a report that shows me
> **which record in the evidence proves it**.

The rescue rule and the seeding frame collide, and A2 is the act most likely to need a rescue. If the operator
completed A2, *"the code you just wrote"* is false, the tester notices, and the frame that makes the act work is
the one that breaks.

Note that **the rescued case is the stronger experiment**: the tester's client did not author the code and does
not hold it in context, so the one-line defeat fails outright. Record which wording was used — a rescued A2
followed by a completed A3 is the cleanest evidence the scored artefact stands on its own.

### What is scored, and why it moved

An earlier version instructed *"from the evidence rather than by reading the code"* and scored the discovery.
That is not enforceable. The defect sits in code the tester's own client authored ten minutes earlier and still
holds in context, so *"is this what you wrote?"* defeats the act in one line — and the previous wording invited
exactly that. Requiring a fresh client session would break the design-partner continuity that is the premise,
and would not close the route anyway, since a fresh client can simply be handed the file.

**So discovery is free and the artefact is scored.** What counts is whether the report cites the record that
proves the symptom and carries what supports the cause — the fix diff and the changed source files — however they
first came to suspect it. The client's `point-at-the-fault` skill asks for exactly this shape.

This is a correction rather than a concession. The product's claim is *you can show what happened and why* — not
*you can find bugs without reading code*. Scoring the report tests the claim directly; scoring the discovery
tested a proxy a competent engineer routes around in one sentence.

**Still record how they first suspected it** — asked the client, diffed, read the log, noticed the output — as a
`from:` observation. Interesting data about routing. Not the scored thing.

### Seeding procedure — the one step that cannot be prepared in advance

Rescue states sit on disk beforehand. **Seeding cannot**, because the code does not exist until A2 ends. The
operator then has two minutes to read unfamiliar code, choose a defect, edit it and hand back — and an edit that
breaks the *build* rather than the *behaviour* costs the session its most valuable twenty minutes.

1. **Read what they wrote** and check the precondition column below. Pick a defect whose precondition holds.
2. **If they logged nothing**, pick a structural defect (1 or 2), whose evidence is which nodes ran.
3. **Make the smallest possible edit** — one token where it can be one token.
4. **Rebuild. Mandatory, no exceptions.** A defect that fails to compile is not a defect.
5. **Run it, and confirm the evidence actually shows something** — a changed value, a node running where it
   should not, a log disagreeing with the output. A fault that builds and produces no observable difference is
   as useless as one that will not build.
6. Record which defect, and the previous tester's, so consecutive testers differ.

Steps 4 and 5 are why the break is two minutes rather than thirty seconds. If they cannot be completed, abandon
that seed, take a simpler defect, and say nothing about it.

### Defect rotation

Six defects. Ten testers, so repeats are unavoidable — **consecutive testers must differ**, which is achievable;
"no two see the same" was not. Each is chosen to apply to almost any small accumulator node, so the operator can
seed whichever fits what the tester actually wrote.

Every defect has a **precondition**. This discipline was already applied to defect 3; it belongs on all of them.
Check the precondition before choosing, or the act tests nothing.

| # | Seeded defect | Precondition in the tester's code | What the evidence shows |
|---|---|---|---|
| 1 | a handler returns `true` where it should return `false` | a **downstream dependent** exists | a node runs in a cycle where it should not have |
| 2 | a reference mode flipped `DATA` → `TRIGGER` | a **node-to-node reference with a declared mode** | a node reacts to an input it should only read |
| 3 | a comparison boundary `>=` → `>` | the scenario has a case **exactly on the threshold** | correct everywhere except at the boundary |
| 4 | a scale factor dropped from one calculation | any arithmetic on a logged value | one value wrong by a constant ratio |
| 5 | the value is logged **before** it is updated | the value is **logged at all** | the audit log disagrees with the published output — a log that lies |
| 6 | an accumulator that never resets | a reset condition exists, or is added in the seed | a value drifts across a boundary it should have crossed |

A2's two-node requirement guarantees the preconditions for 1 and 2. Defect 3's is a property of the shipped
scenario and must be verified once, before the beta. Defect 5's fails if the tester logged nothing, which M5's
change makes possible on purpose.

**Defect 5 is the most instructive**, because the fault is in the evidence itself and cannot be found by
reasoning about outputs alone. **Defects 1 and 2 are structural** — their evidence is which nodes ran rather
than what values they carried — so they are the fallback when nothing was logged.

---

# Session two — 60 minutes

## A4 · A vendor component — cap 20 minutes

> Here's a component from a vendor. Integrate it, and tell me whether we can use it.

**Not** "find the bug". The question is the real one, and what they reach for to answer it is the measurement.
Jar A is authentic and carries a horizon convention mismatch (proposal §3).

## A5 · A second build — cap 10 minutes

> Here's a different build from the same vendor. Same question.

Jar B is correct. **Watch for:** whether they apply the same check, or accept it because the first was rejected.

## A6 · The report — cap 5 minutes

> Produce the report you'd attach to a sign-off.

This artefact is the input to the second-person test (proposal §9). Keep it exactly as produced.

## A7 · Invite the attack — **protected 15 minutes**

> Here's the application and its evidence pack. Try to make it lie to you.

**Protected, not leftover.** This is the most informative act in the plan and it sat last and open-ended after
thirty-five minutes of capped work, which is how it would never have happened. If A4 and A5 overrun, cut A6 to a
stub and keep this.

Record what they reach for **first** — the most valuable single observation in the beta. What counts as a
successful break is pre-registered with the frozen predictions (proposal §10), before any session runs.

---

## Operator log — per session

```
tester (initials), organisation type:
date, session number (1 or 2):
template (fixed across the beta; record for completeness):
seeded defect (A3), and previous tester's defect:
acts completed / capped / operator-completed:
A3 wording used (authored / rescued):
substantive interventions (verbatim, or none):
environment answers (count):
journal extracted:  yes / no      if no, fallback used:
transcript saved at:
consent on file:    yes / no      onward-disclosure consent (§9): yes / no
```
