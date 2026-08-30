# Spec — the LLM authoring experience on a fresh template

**Status:** PROPOSED 2026-08-30 (owner-directed). Recommendations, written up for review. **Tracker:**
[tracker.md](tracker.md) — this is the method for **M19.14** (rewrite the context assets from a measured
run) and **M19.15** (the seeding prompt). **Related:** [spec-onboarding-example.md](spec-onboarding-example.md)
(the bundle contract this constrains), [`docs/experience/`](../experience/README.md) (the loop that
produced the evidence), [`upstream-asks.md` §1c](../proposals/upstream-asks.md) (the diagnostics).

## The goal, in the owner's terms

> *The product should be better bootstrap documentation — `CLAUDE.md`, `AGENTS.md`, how to author Fluxtion
> with a loop and compiler feedback, any upstream improvements. That is the end goal: to improve the
> authoring experience of an LLM on a fresh template.*

So the deliverable is **the context assets a generated bundle ships**, and the upstream changes that let
them be smaller. Everything else — the loop, the rounds, the metrics — is instrumentation.

And the constraint, which shapes the whole measurement section:

> *LLMs are probabilistic, so we can't have deterministic testing — just general improvement and trajectory.*

Accepted, and §D-AX5 is built on it. My previous suggestion of replacing the loop with a conformance test
was wrong in scope: a bench can only pin the **substrate** (do the documented commands work), never the
**authoring experience**. Both are needed, and §D-AX6 keeps them apart.

## The correction that changes the design

Round 03's headline was that the agent **never opened `CLAUDE.md`** and still reached a more accurate
conclusion than it contained. I read that as structural. **It is mostly an artefact of my harness.**

In the real target environment, `CLAUDE.md` is **injected automatically** — it does not need to be opened.
The evidence is this session: this repo's `CLAUDE.md` is in my context and I never read it. The loop's
agent was a general-purpose subagent that had to *choose* to read the file, so what round 03 measured is
the harness, not the product.

Two things follow, and they point in opposite directions:

- The "nobody reads it" conclusion is **withdrawn**. A bootstrap doc in the right harness is read every
  time, before the first tool call.
- Which makes its **cost** the real constraint, not its reach. It is in context for every turn of every
  session, competing with the task for attention. **Every line must earn its place, and most did not.**

*Unverified and flagged: the same auto-load claim for `AGENTS.md` under Codex is the convention as I
understand it, but I cannot check it from here. Confirm before relying on it — the last three times I
asserted product behaviour from memory in this project I was wrong.*

## D-AX1 — where a fact belongs, and the discriminator is whether it fails LOUDLY

Four tiers. Putting a fact in the wrong one is the commonest defect in the doc sets I wrote.

| Tier | Holds | Read when |
|---|---|---|
| **Compiler diagnostic** | anything that fails the build, or silently produces a wrong graph | at the moment of failure, unavoidably |
| **`CLAUDE.md`** | facts that are **not discoverable by trying**, and failures that are **silent** | always in context — so it is a tax on every turn |
| **A skill** | procedures — run, regenerate, read the log | on demand, so length is cheap |
| **Nowhere** | anything the model reliably knows, or can settle in one command | — |

**The discriminator: does getting this wrong fail loudly?** If yes, let it fail and fix the message
(§1c) — prose that pre-empts a good error message is dead weight in every session that never hits it. If
no, it must be written down, because nothing else will ever tell the author.

This is derived from the loop's data rather than from taste: **every expensive finding across three rounds
was a silent one** — a bean that is never a node, a node that can never record a value, a declared sink
that nothing writes to. The loud ones cost a build cycle each.

## D-AX2 — the bootstrap doc teaches the LOOP, not the facts

The current doc set is a list of rules to know **before** starting. That is the wrong shape for a
probabilistic author: it can only ever cover what I anticipated, and every gap is silent.

Teach the recovery loop instead, because it covers gaps I did not anticipate:

```
change → compile → READ THE MESSAGE → change → run → READ THE AUDIT LOG → verify
```

with, explicitly: what a Fluxtion build failure looks like and that its message names the fix; that the
generated processor is an output to **read** for confirmation and never to edit; that the audit log is the
verification step, not a debugging aid; and that a green build proves nothing about whether the node ran.

**Why this is the higher-leverage half.** A fact stated up front helps only if the author hits exactly
that case. A loop taught up front helps on every case, including the ones I have never seen. The loop is
also the thing this whole stack exists to serve, so the bootstrap doc should describe the product's own
mechanism rather than a list of gotchas.

## D-AX3 — the doc set is a PLACEHOLDER for missing diagnostics, and must shrink as they land

Each of §1c's asks has a corresponding block of prose in `current/CLAUDE.md` that exists only because the
compiler does not say it. When UP-FLX-32 lands, the `transient` paragraphs are **deleted**, not kept "for
completeness". Same for UP-FLX-33 and `nodeBeans`.

That gives the work a target that is measurable rather than aesthetic: **lines removed per diagnostic
landed**, tracked in the round record. A doc set that only grows is failing regardless of how good each
addition looked in isolation.

**Consequence for review:** a change that adds a bootstrap rule for something the compiler could say is a
regression, even when the rule is true. The right response is an upstream ask.

## D-AX4 — the shipped EXAMPLES are documentation, and today they teach the easy cases

An LLM authoring in an unfamiliar codebase copies the nearest example. This is the cheapest lever
available and it currently points the wrong way.

**Measured, round 02 (R2-A).** Both nodes shipped in the bundle hold only null-at-construction state, so
neither demonstrates the `transient` rule — *"there is nothing to copy from"*. The most expensive finding
of the round was a case the examples structurally could not show. **Measured, rounds 01/03 (R1-A, R3-A).**
One shipped node extends `EventLogNode` and one does not, with nothing stating that the difference is
deliberate or what it changes — so the pair teaches, by silence, that it does not matter.

**The ask on the bundle:** the shipped nodes must cover the cases that are otherwise silent —

- one node that **holds state**, with `transient` and a one-line comment saying why;
- one that **implements `EventLogSource`** rather than extending `EventLogNode`, so the interface route is
  visible where the inheritance slot is taken;
- one that **logs values** with a typed `auditLog` overload, so a reader sees numbers stay numbers;
- and the existing not-audited node kept, but **labelled** as the deliberate contrast.

Zero bootstrap lines, and it removes the copy-the-wrong-example failure at its source.

## D-AX5 — measurement when the subject is probabilistic

No run is a verdict. What follows is how to get a **trajectory** out of a noisy instrument without
pretending it is a test.

**Observe things that are objective and countable, not "did it succeed".** Task success is near the
ceiling — all three rounds passed, which is the least informative fact about them — and it is the noisiest
thing on offer. These are better, and every one is a direct proxy for a documentation defect:

| Signal | What it means | Direction |
|---|---|---|
| **WENT-OUTSIDE** | left the project to answer a question — unpacked `fluxtion-runtime-sources.jar` from `~/.m2` (R2-E), fetched docs, read framework source | **down** |
| **BUILD ATTEMPTS to green** | cycles burned on things a message could have said | **down** |
| **COULD-NOT-FIND** | questions it recorded as unanswerable from the seeded context | **down** |
| **INVENTED** | an API that does not exist — the M21 failure mode, already named in M19.14 | **down** |
| **UNOPENED** | context shipped and never used; unused context is cost, not safety | **down** |
| **CORRECTED-BY-LOG** | needed the audit log to fix itself | **UP** — that is the product working |

The last row matters: not every metric should fall. An author that self-corrects from the record is the
behaviour this stack exists to produce, and a doc set that raises it is winning even if it grows.

**WENT-OUTSIDE is the primary signal.** It is unambiguous — the agent went looking somewhere the bundle
did not put the answer — and it is the least contaminated by model capability, because a more capable
model finds the answer *faster*, not less often. R2-E is the exemplar: the agent unpacked a sources jar
from the local Maven repository to learn the `auditLog` overloads. Nothing about that is a judgement call.

**Run n > 1 per condition, and report a rate.** A single agent hitting something once is a hypothesis; the
same friction across runs is a defect. That is already the loop's rule and it was never honoured, because
each round was n=1. **Three agents per condition is the floor.**

**Parallelism makes this cheaper, not slower.** Three fresh agents on the same task, run concurrently,
cost one round of wall-clock and yield n=3. The fast cycle the owner wants and the sample size the
measurement needs are the same change, not competing ones.

**Report the noise.** A round record states n, how many runs hit each finding, and what varied. "2 of 3
runs went outside for the `auditLog` overloads" is evidence; "the agent went outside" is an anecdote.

## D-AX6 — do not spend probabilistic runs on deterministic defects

This is where a bench belongs, and it is the correction to my earlier over-reach.

**Measured cost:** a large share of rounds 01–03 was consumed by defects that are fully deterministic and
would be caught by a script in seconds — the documented three-command recipe that hangs because
`run-server.sh` blocks (R1-B), profile pointers that no longer resolve after a rename (R3-B), a fixture
that cannot demonstrate accumulation (R2-C), a declared sink nothing writes to, a README citing a test
that does not exist. **Every one of those burned an expensive probabilistic run to find something a cheap
deterministic check would have caught before the agent started.**

**The recommendation:** a template **preflight** — extending `tools/bench/loop-bench.py`, which already
plays the loop contract — asserting that the documented commands run as documented, every profile pointer
resolves, the fixture demonstrates what the docs claim it demonstrates, and each shipped example compiles.
It must pass before any agent round begins.

Then the agent rounds test **the documentation**, which is the only thing they are good at testing.

## D-AX7 — one variable per round, and a control arm

The loop's central weakness: rounds 01–03 each changed **both** the doc set and the task, so "six of seven
findings did not recur" cannot be attributed. A different task does not walk the same paths — which the
record concedes for R1-C ("not exercised") and quietly does not concede for the other six.

- **Vary one thing per round.** New doc set on a task shape already run, or the current doc set on a new
  task. Not both.
- **Add a control arm when attribution matters:** same task, two conditions — old doc set and new — run in
  parallel. One extra agent per round, and it is the only way "the docs moved the needle" becomes a
  measurement rather than a claim.
- **Actually run the held-out task.** The stopping rule depends on it and it was never run.
- **Put the analyser in the harness.** No round had it reachable, so a loop meant to measure the
  LLM/analyser experience measured the bundle and the framework instead. The analyser is the *verify* step
  of D-AX2's loop; without it that half of the doc set is untested.

## D-AX8 — generate `AGENTS.md`, never hand-maintain two

The two files are byte-identical by intent, so that different harnesses each find one they recognise.
Round 02 recorded `AGENTS.md` as "never opened" and correctly refused to treat that as a deletion
signal — the measurement is confounded, because its twin had already been read.

Keep both; **generate one from the other at bundle-build time** and let the preflight assert they match.
Two hand-maintained copies is a divergence waiting to happen, and the divergence would be silent.

## What to build

1. **Preflight** in `tools/bench/` (D-AX6) — commands, pointers, fixture, examples. Blocks agent rounds.
2. **Rewrite `current/` against D-AX1–AX3** — retier every line: to a skill, to an upstream ask, or
   deleted. Expect the bootstrap doc to get materially shorter.
3. **The authoring-loop section** (D-AX2) — the one substantial *addition*.
4. **Example set** (D-AX4) — a bundle ask, cheapest item here.
5. **Harness**: n=3 parallel, analyser reachable, control arm, held-out task (D-AX5, D-AX7).
6. **File §1c** upstream; delete the prose each landed diagnostic replaces (D-AX3).

Order matters: 1 before 5, and 6 before the second pass of 2 — otherwise the doc set is rewritten around
diagnostics that are about to make it redundant.

## Acceptance

- [ ] Preflight passes before every agent round, and its failures never appear in a round record again.
- [ ] Every line of the bootstrap doc survives the D-AX1 test — silent or undiscoverable — and the rest
      has moved to a skill, an upstream ask, or nothing.
- [ ] The doc set is **shorter** than v4 while covering the authoring loop, and each round records lines
      added and removed.
- [ ] The shipped examples demonstrate `transient`, the `EventLogSource` interface, and a typed
      `auditLog` overload.
- [ ] Round records report **n**, per-finding hit counts, and the six signals — not a pass/fail.
- [ ] One variable per round, stated in the record; any attribution claim has a control arm behind it.
- [ ] A round has run with the analyser reachable and a task that needs it.
- [ ] Each §1c diagnostic that lands upstream deletes its prose here, and the round record names the
      lines removed.

## What I have not verified

Stated because this project's failure mode is asserting product behaviour from memory, three times in one
week by my own hand:

- **`AGENTS.md` auto-loading under Codex** — assumed, not checked (flagged above).
- **That a shorter bootstrap doc performs better.** It follows from the cost argument; it is not measured.
  D-AX5's instrumentation is what would settle it, and the first control-arm round should test exactly
  this rather than assume it.
- **That WENT-OUTSIDE is the most stable signal.** It is the most *objective*; whether it is the least
  noisy across runs needs n > 1, which no round has had.
