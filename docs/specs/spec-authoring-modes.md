# SPEC (PROPOSED) — the four authoring modes, and the delivery order

**Status** proposed · **Owner ask** 2026-09-03, eleven items
**Evidence** rounds 48, 53, 54, 55, 57; `tools/bean-resolver.py`; `docs/proposals/upstream-asks.md`

This is the plan document for the authoring-experience work. It names the modes, maps the owner's
eleven items onto deliverables, adds what the list missed, and **commits to an order** with the
reasoning that produced it.

---

# THE TARGET ARCHITECTURE — canonical

**This section is the single agreed statement of where the authoring work is going.** Where any other
document in this repo disagrees with it, this wins and that document is stale. It replaces the
overlapping architecture narratives that had accumulated across three specs.

**Working rule, applied throughout:** *correctness should increasingly be structural rather than
something an author or model must remember. Decide each fact once, at the component with authority
over it, serialise it, and make every downstream surface consume the same typed result.*

## The eight stages, and the honest status of each

Status is one of four things, and they are not interchangeable:
**MEASURED** (a result exists) · **IMPLEMENTED** (code ships, not necessarily measured) ·
**PROPOSED** (specified, not built) · **HYPOTHESIS** (believed, no evidence).

| # | stage | who owns it | status |
|---|---|---|---|
| 1 | informal goal → explicit figures, policies, unresolved choices | human or model | **HYPOTHESIS — the largest evidence gap.** See below |
| 2 | mechanically derivable component metadata emitted at build | compiler / build tooling | **PROPOSED** — `spec-component-catalogue.md`; today's manifests are hand-authored |
| 3 | semantic facts compilation cannot infer — descriptions, conventions, constructor intent | author / vendor | **PROPOSED**, mechanism **MEASURED**: `Fluxtion-Convention` + a site profile resolves a six-way type-identical ambiguity |
| 4 | deterministic selection and connection where metadata and policy decide | resolver | **MEASURED on one fixture family** — byte-identical XML, audit log identical figure by figure. **Prototype, not product** |
| 5 | compile the declared graph; own dependency identity, relationship kind, propagation, provenance, ordering | Fluxtion | **IMPLEMENTED and substantially consumed here** — M45.1/.2/.3/.5 |
| 6 | runtime artefacts carry the compiler's identity forward | runtime + log format | **PARTLY IMPLEMENTED** — `fluxtion.sourceFingerprint` is pinned compiler → GraphML/descriptor; the **audit-log header carrier is unpinned** |
| 7 | reduce logs and graph metadata to bounded evidence; refuse what it cannot establish | the analyser | **IMPLEMENTED** — verbs, coverage, reports; verification only against an explicit expectation |
| 8 | irreducible intent and genuine ambiguity, recorded as reviewable policy | the model | **PROPOSED**; the recording half is measured (a convention becomes a profile line) |

### Stage 1 is the largest evidence gap, and must not be presented otherwise

**Every experiment in this programme was handed the figure list.** Turning *"a risk engine that alerts
on breach"* into the figures, policies and open choices that follow from it has **never been
measured** — not once in 57 rounds. It is listed first because it is upstream of everything else, and
labelled HYPOTHESIS because presenting it as an established LLM role would be inventing evidence.

### Stage 6 — corrected TWICE, and the second correction was also wrong

**Two errors, both mine, recorded because the pattern is the lesson.** First I wrote that
`sourceFingerprint` "appears nowhere in this repository" — I had grepped `src/main/java` and the site
docs, and missed that it is a **GraphML graph fact**. Then I "corrected" that to say the capability
ships as `descriptorFingerprint` — which is a **private test-helper name**, not the contract.

**The contract is `fluxtion.sourceFingerprint`.** It is emitted by the compiler into the GraphML and
the generated descriptor, it appears in committed `.graphml` artefacts, and it is pinned by
`GraphVocabularyTest` (reading `graphFacts().get("fluxtion.sourceFingerprint")`) and by
`DescriptorFingerprintTest`, which parses it out of the GraphML to prove the envelope crossed the
gateway.

**Three identities, none of which replaces another:**

| identity | answers | status |
|---|---|---|
| `fluxtion.sourceFingerprint` | *which compiled model is this?* | **PINNED** — compiler → GraphML / generated descriptor |
| the audit-log header | *which model produced this run?* | **UNPINNED** — the header carries no model identity |
| `report.LogFingerprint` | *which log was this report authored against?* | shipped, and a **different question** — name, record count, first/last `logTime`, provenance |

**So the unpinned joint is narrower than either of my earlier statements: `generated model →
audit-log header`.** Compiler-to-descriptor is already pinned; `LogFingerprint` is not a substitute
for the missing carrier and was never trying to be.

> **Working rule, earned twice in one session.** *Absence of a name is not absence of a capability —
> and presence of a similar name is not presence of the contract.* The first error produced a false
> upstream ask (UP-FLX-47); the second produced a false correction to it.

## Duplicated authority — the actual examples

The failure this architecture removes is **two components deciding the same fact independently**.
Real instances, from this repo's own history:

- **GraphML relationship re-derivation** — the analyser inferring relationship kind from adjacency
  when the compiler already knows it and can emit it.
- **Topological-rank inference** — dispatch order reconstructed downstream rather than read from the
  model that computed it.
- **Service-registry reclassification** — a node's service role decided a second time by a surface
  that did not create it.

**Not an example of this:** the scorer comparing event names instead of full types. That was a missing
identity guard in one component, not two components claiming the same authority. Recorded because the
distinction decides which fix is right — a guard, versus moving the decision upstream.

## The end goal, stated so it is checkable

> **Make `declare → resolve → compile → run → inspect → correct` one coherent loop whose boundaries
> are machine-checkable.**

**The loop is not verified end to end, and this document does not claim it is.** Joint by joint:

| joint | pinned by | status |
|---|---|---|
| declare → resolve | byte-identical XML reproduction from committed manifests | **pinned** |
| resolve → compile | green build from resolver output | **pinned** |
| compile → run | real audit log produced from the generated processor | **pinned** |
| run → inspect | shipped reader parses it; 12/12 figure comparison | **pinned** |
| inspect → **detect divergence** | mutation-tested scoring: 5 of 5 caught on a real log | **pinned** |
| **detect → correct** | nothing | **UNPINNED** — catching a divergence is not evidence that the loop closes on a fix |
| compiler → GraphML / generated descriptor | `fluxtion.sourceFingerprint`, `GraphVocabularyTest`, `DescriptorFingerprintTest` | **pinned** |
| **generated model → audit-log header** | nothing | **UNPINNED** — the header carries no model identity |
| **build → component metadata** | nothing | **UNPINNED** — manifests are hand-authored |
| **goal → declaration** | nothing | **UNPINNED** — never measured |

Compatibility goldens exist and are real: `src/test/resources/conformance` (record format) and
`src/test/resources/formula-golden`. They pin the format and formula surfaces, not the loop's joints.

### The architecture above covers COMPOSITION, not authoring

**Stages 2–4 assume the components exist.** Modes 2 and 3 — where the author writes the nodes — are
not decomposed by these eight stages at all, and nothing in them is measured. The authoring path has
its own unaddressed questions: what a node shell must contain, how graph correctness is asserted
before any test runs, and how "fix the node" is distinguished from "fix the orchestration". Those are
M48.9 and M48.10, and until they are specified this architecture describes **half** the problem.

## Scope of the whole decomposition

**It rests principally on one fixture family** — the round-48 catalogue (nine entry points across five
jars) and its round-55 extension (fourteen, after six ambiguous pricing variants were added). Modes 2
and 3 are unmeasured. The plain-Java comparison is n=1. Treat the decomposition as a well-evidenced
hypothesis about where boundaries belong, not as a general result.


## The four modes, and what each costs

The series has been measuring one mode without naming it. Naming all four is the first useful act,
because the instructions, the harness and the ceiling are different in each.

| mode | who writes what | model needed? | status |
|---|---|---|---|
| **0 — resolved** | nobody. A resolver reads manifests and emits the bean file | **no** | **built and verified** (round 57) |
| **0+ — resolved with a profile** | nobody; a one-line site profile decides conventions | **no** | **built and verified** (round 57 addendum) |
| **1 — integrator** | the author selects components and writes the bean file | only for **selection** | measured (rounds 48, 55) |
| **2 — starter** | the author describes beans that generate node classes, then writes the nodes | yes | **unmeasured** |
| **3 — builder** | the author writes a Java `FluxtionGraphBuilder` and the nodes | yes | **unmeasured** |

**Mode 0 is the finding that reorganises everything.** Round 57 showed the wiring half of mode 1 is a
constraint solve: a unique selection identical to the measured optimum, a green build, and
byte-identical alerts, at **zero token cost** against cell O's 1.98M. Where the declared surface does
not decide, the resolver reports the ambiguity and refuses to guess.

**So mode 1 collapses to selection**, and modes 2 and 3 are the ones where a model still does real
authoring. That is where the unfound ceiling lives.

## Measured tonight, and it changes two items

**A. The process-classes ordering problem does not exist in modes 0 and 1.** The generator's only
inputs are the bean file and the *vendor* classes, which arrive as dependencies. Nothing the author
writes is an input. Rebinding `springToFluxtion` from `process-classes` to **`generate-sources`**
makes a plain `mvn compile` work, and **removes the whole ordering workaround** — the
`generated.dependents` property, the `default-compile` exclusion, and the second compiler execution.
Verified: build green in one pass, `Main.class` and `AppProcessor.class` both emitted, and the
application's alerts still byte-identical.

> **The ordering problem is a mode-2/3 problem only**, because only there does the generator depend on
> classes the author wrote. That reframes item 9 from "solve it" to "eliminate it in 0/1, and design
> for it in 2/3".

**B. The namespace collision is real, and here is the minimal reproduction.** Two classes with the
same simple name in different packages. The generator qualifies the declared *type* but not the
*constructor call*:

```java
public final transient Spread spreadA = new Spread();
public final transient com.b.Spread spreadB = new Spread();   // resolves to com.a.Spread
```
```
error: incompatible types: com.a.Spread cannot be converted to com.b.Spread
```

Twelve lines of input, uncompilable output, no diagnostic. **This is a component-market blocker**: two
vendors cannot both ship a `Spread` node.

## The eleven items, as deliverables

| # | owner's item | deliverable | status 2026-09-03 |
|---|---|---|---|
| 1 | Spring authoring efficiency | mode-0/0+/1 specs + resolver | **largely DONE** — resolver built & verified, selector built, walkthrough written. Remains: the **mode-1 selection asset** |
| 2 | starter style, author writes nodes | mode-2 asset + ablation | **not started**; the playground's `spring-authoring/*` is the baseline |
| 3 | Java builder, author writes nodes | mode-3 asset + ablation | **not started**; `CLAUDE.md` + golden path is the baseline |
| 4 | the development harness loop | `spec-fluxtion-dev-harness.md` | **not started** — the big one; defines 2 and 3 |
| 5 | namespace collision bug | **UP-FLX-46, with a 12-line repro** | **☑ DONE** — lodged 2026-09-03 as [telaminai/fluxtion#31](https://github.com/telaminai/fluxtion/issues/31) |
| 6 | the blog | two posts | **blocked** on M3 (n=1) and P3b restatement |
| 7 | analyser / toolbench first-class | `spec-authoring-mode-selector.md` §toolbench | **spec'd** — R6–R9, and the arithmetic that settles it. Remains: the analyser view |
| 8 | manifest builder + annotations | `spec-component-catalogue.md` + V-A/V-B | **de-risked** — `Fluxtion-Convention` mechanism built & verified. Remains: the annotation + plugin goal |
| 9 | process-class ordering | measured | **half DONE** — `generate-sources` verified for modes 0/1. Remains: update the template pom; design for 2/3 |
| 10 | what was missed | M1–M10 below | recorded |
| 11 | order | below | **DONE**, refreshed here |

**Artefacts produced 2026-09-03:** `tools/bean-resolver.py`, `tools/fluxtion-harness.py`,
`spec-authoring-modes.md`, `spec-authoring-mode-selector.md`,
`spec-authoring-session-walkthrough.md`, `assessment-playground-ai-prompts.md`, UP-FLX-46,
catalogue validations V-A/V-B, rounds 55 and 57.

### Item 4 — the harness loop, stated as the owner described it

> single node shell, create if missing → build an orchestration graph → **check correctness of the
> graph** → put the graph under test, check correctness of the app → fix the node if wrong → fix the
> orchestration if wrong → test.

Two things make this specifiable rather than aspirational, and both are already measured:

- **"Check correctness of the graph" is mechanical.** Every round from 07 onward scored the *generated
  dispatch* — `guardCheck_x()` membership, invocations per cycle, node instance counts — not the
  agent's report. The harness can assert graph shape before any test runs.
- **The three-artefact triangulation is the debug loop.** Code, GraphML and audit log cannot disagree,
  which is what makes "fix node vs fix orchestration" a decidable question rather than a guess.

### Item 6 — the blog, and it must be split

The honest scope is **assembly of existing components — the integrator's approach**, and round 57
changed what that post says. It is no longer "a cheap model beats an expensive one"; it is:

> We measured a cheap model doing this 7.5× cheaper than the expensive one — **and then found it
> should not be a model at all.** Here is the line between what a resolver does for free and what
> still needs judgement.

That is a better post and a more defensible one. **The second post — the ceiling when the author
creates components and binds the graph — cannot be written yet**, because modes 2 and 3 are unmeasured.

**Blocking issue for post one:** the comparison arm is **n = 1** against Fluxtion's n = 4, and round 53
showed that arm can pass. Also, per P3b, every ratio published so far is raw weighted units, which
understates the dollar difference by 5× and overstates like-for-like work by ~1.3×. **Both must be
fixed before publication.**

## Item 10 — what the list missed

**Blocking, do before any further measurement:**

- **M1 — the harness must record `cache_creation_input_tokens` and `cache_read_input_tokens`.** Per
  P3a, Haiku 4.5 has a 4,096-token minimum cacheable prefix and short prompts are *silently* uncached.
  A cost comparison between two prefix sizes is meaningless if one was uncached.
- **M2 — a shared scorer with length assertions.** **Five scoring defects in this project, three in one
  session, every one in the direction of agreeing with me** — including a check that printed "12/12
  identical" while comparing 12 events against 0. Any comparison must assert equal lengths before
  reporting a rate.
- **M3 — close the n=1 asymmetry** on the plain-Java arm. Blocks item 6.

**Product / usability gaps found in passing:**

- **M4 — ☒ WITHDRAWN, the finding was false.** It claimed `springToFluxtion` exposes no audit
  configuration. `FluxtionSpringConfig.logLevel` in the bean file is all that is needed; see UP-FLX-47.
  Retained here because how it was wrong is the transferable part — the goal's parameters were read,
  the config bean the goal consumes was not.
- **M5 — the audit default is the wrong way round.** `addEventAudit(level, true)` is the default and
  costs **184 bytes/event even at levels where nothing is published**; `addEventAudit(level, false)` is
  showed no measured per-event allocation. No doc points at it. (Round 54.)
- **M6 — the audit log's cost is undocumented**: 26× throughput and 460 B/event at INFO. Nothing in
  this repo said so before round 54.
- **M7 — `setAuditLogRecordEncoder(new LogRecord(clock))` is a footgun**: zero-allocation at a
  suppressing level, unbounded growth and OOM at INFO.
- **M8 — UP-FLX-45**, the lazy wall-clock read. Filed, narrowed, low priority — replay mode already
  avoids it.
- **M9 — the template's package mismatch**: it generates `com.acme.app.generated` while the supplied
  runner imports `com.acme.generated`. Small, and it cost a build.

**Component-authoring guidance for modes 2/3, already measured:**

- **M10 — an API decides whether its integrator can be correct.** Round 53: without a counter-free
  `refresh()`, both available behaviours were wrong; with it, an idiomatic author scored 17/17. This is
  the single most useful rule for whoever writes components, and it belongs in the mode-2/3 spec.

**Unfinished experiments:** round 55 rungs 2–3 (built, unrun), round 56's shared-instance fixture
(designed, unbuilt), and GraalVM `setGenerateReachabilityMetadata` (exists, untested — relevant to
toolbench packaging).

## Item 11 — the decided order

Ordered by *what unblocks the most per hour*, not by importance.

### Tier 0 — what is actually left, in this order

1. **M2, the shared scorer — ☑ SHIPPED.** `analyser.score.ExpectationScorer` / `ScoreCommand`, built
   on the shipped reader. **Ten guards, 21 tests.** Five of the ten were added by independent reviewers
   who found the earlier implementation producing false passes, and **every one of those five erred
   toward agreeing with its author** — the same direction as the five historical defects it was written
   to stop. Dialect is caller-declared. First real-log run is M48.11.
2. **Item 9, finish it.** Rebind the template pom to `generate-sources` and delete the
   `generated.dependents` workaround. Measured and verified; ~1 hour. Do it early because it removes
   friction from every experiment that follows — it cost me a build tonight.
3. **M1, cache accounting.** Record `cache_creation_input_tokens` / `cache_read_input_tokens`. Half a
   day, and it gates every future round: Haiku silently uncaches below 4,096 tokens, so any
   prefix-size comparison without it is meaningless.
4. **Item 1, the mode-1 selection asset.** Small: how to read a `Fluxtion-Description`, that absence
   of a promise rules a candidate out, and how an answer becomes a profile line.

### Tier 1 — the enabler. Do this before modes 2 and 3.

6. **Item 8, the manifest builder.** One annotation plus a plugin goal, and — the part that matters —
   **a `Fluxtion-Convention:` field carrying the selection criterion.** Round 55 measured `javap`
   going 0 → 11 the moment descriptions had to be read; this closes the last judgement rung and makes
   mode 0 total. It is also step one of the mode-2/3 loop, so items 2, 3 and 4 all sit on it.

   **De-risked 2026-09-03: the mechanism is already built and measured** (round 57 addendum). A
   `Fluxtion-Convention: spread=hedged` line plus a one-word site profile resolves round 55's
   six-way ambiguity uniquely, and changing the profile word changes the selected component. Tier 1
   is now specification and productisation of a working mechanism, not a design risk.

   **It carries one new build-failing validation:** a variant sharing a type surface with another
   MUST declare a convention, because silence is not a match and an undeclared variant becomes
   unselectable at any site with a profile for that figure.

### Tier 2 — the unmeasured half.

7. **Item 4, the development harness.** Defines 2 and 3; the graph-correctness check is already
   mechanical.
8. **Items 2 and 3**, specced then *measured* — this is where the Haiku ceiling actually is.
9. **M10** folded into the component-authoring guidance.

### Tier 3 — outward-facing, once the above is true.

10. **Item 6 post one** (integrator), after **M3** closes the n=1 gap and the ratios are restated per P3b.
11. **Item 7, the analyser/toolbench experience** — it should be designed against modes 0–3 as they
    actually are, not as they were before round 57.
12. **Item 6 post two** (the authoring ceiling), only after Tier 2 has measured it.

**Deferred, deliberately:** M4–M9 are real but none blocks anything above. M4 is the first to promote
if mode-1 users hit it.

## The one-line rationale for this order

**Fix the instruments, remove the work that should not exist, then measure the half that is still
unknown.** Tiers 0 and 1 delete work; Tier 2 is the only place a model's ceiling is still an open
question; Tier 3 publishes what by then will be true.

---

## The session protocol — how a session opens

**Owner, 2026-09-03:** *"we start a session with working out what the user wants to build, what
resources are available and then select the authoring mode for that session?"*

Correct in shape, with two refinements that the harness now implements.

### Three inputs, established once at the top of a session

| input | how it is obtained | cost |
|---|---|---|
| **the figures the business needs** | from the requirement — **this is judgement**, and the first place a model earns its place | one-time |
| **the catalogue** | scan the jars on the path; mechanical | free |
| **the site profile** | the conventions in force — `spread=hedged` | one-time per installation |

The first row deserves naming: **turning "we need a risk engine that alerts on breach" into a figure
list is itself a selection problem**, and it is upstream of everything measured so far. Every round in
this series was handed the figure list. That step has never been measured.

### Refinement 1 — the AUTHORING mode is derived; the ANALYSER's posture is set

**Corrected 2026-09-03.** An earlier version headed this "the mode is derived, not selected" and
applied it to both. The single authority is
[`spec-authoring-mode-selector.md`](spec-authoring-mode-selector.md) ▸ R10: the **authoring** mode is a
fact about a catalogue and is derived; the **analyser's posture** is SET by either party through MCP or
the UI, with derivation only as a fallback default — because derivation lags intent, and a person
saying "let's build something new" is known before any artefact changes.

The harness runs the catalogue first, always, and the mode falls out:

```
RESOLVED               -> mode 0    nobody writes anything
RESOLVED (+ profile)   -> mode 0+   one line decided it
AMBIGUOUS              -> mode 1    one question needs judgement
UNSATISFIABLE          -> mode 2/3  author nodes for the named gap
```

**You never author what you could have resolved**, and you learn which figures need authoring before
writing a line.

### Refinement 2 — the mode is per FIGURE, not per session

The realistic case is mixed, and treating it as a single mode would be wrong. The harness resolves
the covered subset and scopes authoring to the remainder:

```
  MIXED SESSION — 18 of 20 figures resolve mechanically, 2 require authoring.

  MODE 0+ for 18 figures — resolved, nobody authors:
      marketdata MarketDataPlus / pricing PricingHedged / liquidity LiquidityStd
      risk RiskSupervised / capital CapitalRegulated
      -> a buildable bean file

  MODE 2/3 for 2 figures no component provides:
      hedgeRatio, netPosition
```

**Authoring is scoped to the gap.** The resolved half is already buildable; the new nodes join it
rather than replacing it. That is the difference between "this project needs a graph written" and
"this project needs two nodes written" — and it is the difference the whole catalogue exists to make.

### Cost, measured

| | tokens | weighted | wall clock |
|---|---|---|---|
| cell O — a model authored it | ~2.0M | 1,980,000 | minutes, 51 turns |
| **mode 0 / 0+** | **0** | **0** | **38 ms** |
| deciding the profile line | ~2–5k | — | once per *installation* |

The profile is written once and reused by every subsequent build, jar upgrade and rebuild. **The
per-build authoring cost is zero.**

### What this means for item 7 (the analyser)

The three session inputs map exactly onto concepts the analyser already has: the **project profile**
(M20, M38) holds the conventions and the catalogue location, and **M38.2's vocabulary pointer** is the
mechanism for mapping the customer's words to the supplier's convention names. The authoring
experience is therefore not a new subsystem — it is a new *view* over portable context that already
exists, plus the harness as a runnable step.

---

## The architecture: a meta selector, then mode skills loaded on demand

**Owner, 2026-09-03:** *"so we have a meta harness selector when using fluxtion to build, then once
you select/choose your path the mode you are on is brought in as an optional skill?"*

**Yes, and the measurements say this is the right shape rather than merely a tidy one.**

### Why the split is load-bearing

Round 48's optimum is **659 words**, and it was optimal for a task that **bundled assembly with
selection**. Once assembly is resolved mechanically, most of those words are being charged to sessions
that cannot use them. The ablation already showed what that costs: **a worked example went from
best-in-study to +28 turns** the moment the catalogue answered what it taught. P1 states it as
*discovery aids expire*. The mode split generalises it:

> **An instruction is not globally good or bad. It is load-bearing in one mode and dead weight — or
> actively harmful — in another.**

### The shape

| stage | what it is | cost |
|---|---|---|
| **meta selector** | `tools/fluxtion-harness.py` — a **tool**, not a prompt. Reads the catalogue, derives the mode per figure | **0 tokens, 38 ms** |
| **mode skill** | loaded *after* the selector reports, and only the one(s) the session needs | paid only when used |

**The selector must be a tool, not an instruction.** Anything that runs deterministically should never
be spent as context — that is the same partial-evaluation move as the manifest itself, applied to the
harness.

### What each mode's skill has to carry

| mode | skill needed | content | status |
|---|---|---|---|
| **0 / 0+** | **none** | nobody authors anything | — |
| **1** | *selection only* | how to read a `Fluxtion-Description`, and that absence of a promise is a ruling-out — measured in round 55 | a fraction of the 659 words |
| **2 / 3** | *node authoring* | N1 scope, N2 entry-point-is-not-a-node, the two-constructor idiom, `@NoTriggerReference`, one `@OnTrigger`, the harness loop | **does not exist yet** |

Two consequences worth stating plainly:

**Mode 0 needs no instructions at all.** The cheapest session is one where the skill system loads
nothing, because there is nothing for a model to do. That is the strongest form of the result.

**The mode-2/3 skill has never been written, let alone ablated.** Everything measured in this series —
including the 659 words and every N-item in `spec-minimal-authoring-instructions.md` — is
**assembly** guidance. The node-authoring content that modes 2 and 3 need is a different document,
and its ablation is the unmeasured work.

### The mixed case

A mixed session loads the mode-2/3 skill **for the gap only**, while the resolved half needs nothing.
So instruction cost scales with the size of the gap, not the size of the graph — which is the same
scoping property the harness already applies to the authoring work itself.

### Consequence for the delivery order

This does not move the tiers, but it sharpens two of them:

- **Tier 0, item 1** becomes *write the mode-1 selection skill* — small, and most of the 659 words do
  not belong in it.
- **Tier 2, items 2/3/4** become *write and then ablate the mode-2/3 authoring skill*. That is where
  both the unfound ceiling and the unmeasured instruction set live, and they should be measured
  together: an ablation over authoring instructions, on a task the catalogue cannot satisfy.
