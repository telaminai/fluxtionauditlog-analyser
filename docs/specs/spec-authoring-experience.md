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

### D-AX1a — the bundle POINTS at `claude.txt`; it restates nothing the canon already says

Added 2026-08-30 after the owner asked whether I was authoring against
[`docs/claude.txt`](https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt). **I was
not** — v1–v4 of the doc set and §1c were written without reading it, which is a rule 6 miss and, on the
count that matters, a **fourth instance of the pattern the loop keeps catching**. Reading it changed three
things:

- **`transient` is already in the canon**, clearly, with both remedies. Round 02's most expensive finding
  was not an upstream knowledge gap — a correct rule existed and the author still guessed wrong, because it
  was not where the failure was. That does not retire UP-FLX-32; it is the strongest possible case for it.
- **`@OnTrigger`'s boolean return is already in the canon.** My §1c table filed it as an upstream
  documentation ask. Corrected.
- **The audit contract is NOT in the canon** — no `EventLogSource`, no `setLogger`, no `nodeLogs`, no
  statement that position is dispatch order. That is now **UP-FLX-35**, and it is the reason three wrong
  versions of that rule were written here: the bundle had to invent what the canon does not carry.

**The decision.** A bootstrap doc that restates the canon inherits every drift between them and adds
tokens to every turn. So: **link, with a one-line reason to open it**, and write only what the canon does
not say. A line that duplicates `claude.txt` is a D-AX1 "Nowhere" line — and the preflight (D-AX6) should
fail on the ones that are checkably duplicated, since the canon is a fetchable file.

Sequencing consequence: **UP-FLX-35 lands before the doc set is rewritten**, or the rewrite will once
again encode the audit contract locally instead of pointing at it.

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

## The rig — everything local, so the cycle is fast and both ends are editable

Owner-directed, 2026-08-30:

> *When you run the experiment the changes should be local for quick cycles. Playground as a local web app,
> set the URL of analyser to use that. Fluxtion compiler backend on the classpath removes the network call
> to compilation. You can then update both as part of the test. If we need to change Fluxtion runtime itself
> that can happen but the gate is strong for comparability and regressions.*

This changes what the experiment **is**. Until now a round could only measure documentation against a
fixed upstream, so §1c's diagnostics were asks to file and wait on. With the rig, the message and the
template are inside the loop: change the diagnostic, re-run the round, and see whether the prose it
replaces is still needed. **That is the difference between proposing a fix and measuring one.**

### D-AX9 — four surfaces, three of them mutable in-cycle

| Leg | Local form | Mutable in a round? |
|---|---|---|
| **Playground** | local web app serving the catalogue and template zips | **yes** — the bundle contract and its examples (D-AX4) |
| **Analyser** | this repo, run from `target/` | **yes** — but see D-AX10 |
| **Fluxtion compiler** | generation backend on the classpath, no network call, **no API key** | **yes** — diagnostics (§1c) |
| **Fluxtion runtime** | the substrate every measurement is taken against | **gated** — D-AX12 |

Two consequences worth stating before they are discovered:

- **The keyless path stops being exercised.** With generation local there is no key and no
  `process-classes` network call, so the bundle's "runs without a key, regenerates with one" claim — the
  most likely first-run failure in the field (M19 R4) — is no longer touched by any round. It must stay in
  the **preflight** (D-AX6) against the real hosted path, or it silently rots.
- **Local generation is not the user's generation.** A round on the rig measures the authoring experience,
  not the install experience. Any claim about what a real fresh user hits still needs a run on the shipped
  path, and the round record must say which it was.

### D-AX10 — the analyser cannot point at a local playground today, and the fix must not be a setting

**Verified in the code, 2026-08-30.** `TemplateClient.playground()` is the only production factory and
hardcodes `PLAYGROUND = URI.create("https://fluxtion-playground.dev")`. The package-private constructor
does take an origin, but `validateOrigin` **rejects any non-HTTPS scheme** — there is a test asserting
exactly that (`TemplateCatalogueTest`, `http://templates.example` throws). Redirects are `NEVER`. So a
local `http://127.0.0.1:PORT` playground is refused twice over, and there is **no override path at all** —
not a property, not a setting, not a flag.

That refusal is correct for shipped behaviour and must survive. The rig therefore needs a deliberate,
narrow hole:

- **A JVM system property, not a persisted setting** — e.g. `-Dfluxtion.analyser.playgroundOrigin=…`.
  It must not be reachable from the Settings UI and must never be written into a project profile, because
  a persisted origin is a supply-chain surface that outlives the experiment that created it (D-R4 in
  [spec-onboarding-example.md](spec-onboarding-example.md) is the same concern for the skills source).
- **Loopback only.** A non-HTTPS origin is accepted **only** when the host is `127.0.0.1`, `::1` or
  `localhost`. Every other origin keeps the HTTPS rule unchanged. This is the same shape as M42's
  loopback probe, and it is what makes the hole un-exploitable from a document or a config file.
- **Visible.** When the override is in force, the template dialog says which origin it is using. An
  experiment that silently downloads from somewhere else is how a rig artefact gets mistaken for product
  behaviour.
- **Tested at the boundary**, not just the happy path: `http://evil.example` still refused,
  `https://` unaffected, and the property absent leaves today's behaviour byte-identical.

### D-AX11 — diagnostics are BUILDER-side, so two thirds of §1c needs no backend at all

**Verified while writing §1c, 2026-08-30**, and it makes the rig cheaper than the plan assumes:

- **UP-FLX-32** (constructor match) throws from `LiveGraphSourceGenExtractor.generateComplexConstructors()`
  in **`fluxtion-builder` 1.0.64** — a client-side jar, read from `~/.m2`.
- **UP-FLX-33** (`nodeBeans`) lives in the same jar, `com.telamin.fluxtion.builder.extern.spring`.

Both are therefore editable and testable **today**, against a locally-built `fluxtion-builder`, before any
classpath-backend work lands. That matters for sequencing: the highest-value diagnostic in §1c can enter
the loop first and cheapest.

*Not verified: which side emits the GraphML, so **UP-FLX-34**'s home is unknown to me. Establish it before
scheduling that ask — do not assume it is the same jar because its siblings are.*

**The principle this generalises to:** a fix belongs in the **builder** wherever it can go there. The
builder is instrumentation; the runtime is the thing being measured. Keeping them separate is what stops
D-AX12's gate from firing on ordinary work.

### D-AX12 — the runtime is the SUBSTRATE, and changing it invalidates more than the round

Changing `fluxtion-runtime` mid-experiment is allowed and gated hard, for the reason the owner gives —
comparability and regressions. Concretely, what a runtime change costs:

- **It resets the baseline.** Rounds either side are not comparable, and no trend claim may span the
  change until a **control-arm round has been re-run on the new runtime**. Without that, an improvement in
  the metrics and a change in the substrate are indistinguishable.
- **A change to the audit-log format invalidates prior evidence outright**, not just the trend — the log
  is the measurement instrument, and every round's findings were read through it. The concrete gate is
  the M34.3 conformance suite (`docs/site/format-spec.md`, `src/test/resources/conformance/`), which the
  built-in reader and the SPI both pass today.
- **Regression gate before any round runs on it:** conformance suite green, the analyser's full suite
  green against the new runtime, and `tools/bench/loop-bench.py` green.
- **The round record names the reason.** A runtime change made to serve a documentation experiment is a
  strong signal that the experiment found something real — it should be conspicuous, not routine.

### D-AX7 restated — three mutable surfaces make attribution harder, not easier

With the rig, a single round can plausibly change the doc set, the template's examples **and** a compiler
message. That is the fast cycle the owner wants, and it is also how attribution dies. Rounds therefore
declare which kind they are:

- **Exploration round** — change whatever is useful, record findings, **claim no attribution**. Fast, and
  most rounds should be these.
- **Attribution round** — one variable, control arm, n≥3. Only these may support a claim that something
  improved, and only these count toward the trend.

Both are legitimate; conflating them is not. Round 01–03's flaw was making attribution claims from
exploration rounds without noticing the difference.

### The rig manifest — every round records it, or the round proves nothing

Extends the existing "record the environment" rule to the surfaces the rig makes mutable:

```
analyser        <git SHA>            playground      <content SHA / commit>
fluxtion-builder <version or SHA>    template zip    <SHA-256>
fluxtion-runtime <version>           doc set         current/ vN
generation       local | hosted      analyser reachable  yes/no
round kind      exploration | attribution   n = <runs per condition>
```

## What to build

1. **Loopback playground origin** (D-AX10) — the analyser change, and the only one in this repo. Property
   only, loopback only, visible, refusals tested. **Nothing else on the rig works until this lands.**
2. **Preflight** in `tools/bench/` (D-AX6) — commands, pointers, fixture, examples, **plus the keyless
   hosted path** that the rig stops exercising (D-AX9). Blocks agent rounds.
3. **Rewrite `current/` against D-AX1–AX3** — retier every line: to a skill, to an upstream ask, or
   deleted. Expect the bootstrap doc to get materially shorter.
4. **The authoring-loop section** (D-AX2) — the one substantial *addition*.
5. **Example set** (D-AX4) — served from the local playground, so it is testable in-cycle.
6. **Harness**: n=3 parallel, analyser reachable, control arm, held-out task, rig manifest per round.
7. **UP-FLX-32 in a locally-built `fluxtion-builder`** (D-AX11) — the cheapest diagnostic to try, and the
   first real test of D-AX3: does the prose it replaces become deletable?
8. **File UP-FLX-35** (D-AX1a) — the audit contract into `claude.txt`, **before** item 3, so the rewrite
   points at the canon instead of inventing the rule locally for a fourth time.

Order matters. **1 before anything**; 2 before 6; and 7 before the second pass of 3, or the doc set gets
rewritten around diagnostics that are about to make it redundant. Items 5 and 7 are the two that only the
rig makes possible — everything else was already available and simply not done.

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
- [ ] The playground origin override is **property-only and loopback-only**, unreachable from Settings,
      never written to a profile, stated in the UI when in force, and covered by refusal tests — with the
      property absent, shipped behaviour is unchanged.
- [ ] Every round record carries the **rig manifest** and declares itself **exploration** or
      **attribution**; only attribution rounds support a trend claim.
- [ ] A `fluxtion-runtime` change has passed the M34.3 conformance suite, the analyser's suite and
      `loop-bench.py` **before** any round runs on it, and a control-arm round has been re-run on the new
      runtime before any trend claim spans it.
- [ ] The keyless hosted path is still exercised somewhere, despite the rig no longer touching it.
- [ ] No bootstrap line duplicates `claude.txt`; the doc links to it with a reason to open it, and the
      preflight fails on checkable duplication (D-AX1a).

## What I have not verified

Stated because this project's failure mode is asserting product behaviour from memory, three times in one
week by my own hand:

- **`AGENTS.md` auto-loading under Codex** — assumed, not checked (flagged above).
- **That a shorter bootstrap doc performs better.** It follows from the cost argument; it is not measured.
  D-AX5's instrumentation is what would settle it, and the first control-arm round should test exactly
  this rather than assume it.
- **That WENT-OUTSIDE is the most stable signal.** It is the most *objective*; whether it is the least
  noisy across runs needs n > 1, which no round has had.
