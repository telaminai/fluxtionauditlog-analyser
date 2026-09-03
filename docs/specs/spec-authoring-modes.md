# SPEC (PROPOSED) — the four authoring modes, and the delivery order

**Status** proposed · **Owner ask** 2026-09-03, eleven items
**Evidence** rounds 48, 53, 54, 55, 57; `tools/bean-resolver.py`; `docs/proposals/upstream-asks.md`

This is the plan document for the authoring-experience work. It names the modes, maps the owner's
eleven items onto deliverables, adds what the list missed, and **commits to an order** with the
reasoning that produced it.

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

| # | owner's item | deliverable | notes |
|---|---|---|---|
| 1 | Spring authoring efficiency | **`spec-integrator-authoring.md`** | 80% written — `spec-minimal-authoring-instructions.md` + round 57's P4a. Must be rewritten as **mode 1 = selection only** |
| 2 | starter style, author writes nodes | **`spec-mode-starter.md`** | needs 4 first |
| 3 | Java builder, author writes nodes | **`spec-mode-builder.md`** | needs 4 first |
| 4 | the development harness loop | **`spec-fluxtion-dev-harness.md`** | the big one; defines 2 and 3 |
| 5 | namespace collision bug | **upstream issue, reproduction in hand** | ready to file |
| 6 | the blog | **two posts, not one** | see below |
| 7 | analyser / toolbench first-class | **`spec-authoring-in-analyser.md`** | needs 1, 4, 8 settled |
| 8 | manifest builder + annotations | **extend `spec-component-catalogue.md`** | **highest leverage** |
| 9 | process-class ordering | **fold into 1 and 4** | half-solved above |
| 10 | what was missed | below | |
| 11 | order | below | |

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

- **M4 — `springToFluxtion` has no audit option.** A bean-declared `EventLogManager` leaves its
  `LogRecord` null and NPEs; `setAuditLogRecordEncoder` does not reach that instance. This blocked
  round 57's figure-by-figure verification. **Mode-1 authors cannot enable the audit log** — which is
  awkward for a product whose value proposition is the audit log.
- **M5 — the audit default is the wrong way round.** `addEventAudit(level, true)` is the default and
  costs **184 bytes/event even at levels where nothing is published**; `addEventAudit(level, false)` is
  allocation-free. No doc points at it. (Round 54.)
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

### Tier 0 — this week. Cheap, and three of them unblock everything else.

1. **M2, the shared scorer.** Five defects, all flattering. Every number below depends on it. Half a day.
2. **Item 5, file the collision bug.** Reproduction is in hand; it is a component-market blocker. One hour.
3. **Item 9 → fold in.** Rebind mode-0/1 generation to `generate-sources` and delete the workaround
   from the template. Measured tonight. One hour.
4. **Item 1, `spec-integrator-authoring.md`.** Rewrite as *mode 1 = selection only*. 80% exists.
5. **M1, cache accounting in the harness.** Half a day, and it gates every future round.

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

### Refinement 1 — the mode is derived, not selected

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
