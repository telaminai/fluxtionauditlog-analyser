# Proposals to other repos — what the analyser needs from upstream

Ideas that belong in **another** repo, raised from work done here. This file is the holding pen: an item
lives here until it is filed upstream, then the entry records where it went.

Everything below is written from something that actually happened while building the analyser. Where an
item says *measured*, it was checked against generated code, a real audit log, or the live source — not
inferred. That distinction matters: this repo's own history (`CLAUDE.md` rule 6) is a list of defects
caused by inferring framework behaviour instead of reading it, so an ask built on inference is worth
less than no ask at all.

| Field | Meaning |
|---|---|
| **ID** | stable handle, quote it in the upstream issue |
| **Target** | which repo it belongs to |
| **Evidence** | what makes this a fact rather than a preference |
| **Cost to us if unfixed** | the workaround the analyser carries today |

Status: ☐ not filed · ◐ filed · ☑ landed.

**FILED 2026-08-30 — [issues 19–23](https://github.com/telaminai/fluxtion/issues).** UP-FLX-32 → #19,
UP-FLX-33 → #20, UP-FLX-34 → #21, UP-FLX-35 → #22, and **UP-FLX-36 → #23** (sibling dispatch order), which
did not exist when this section was written — it came out of round 05, where four of six independent
authors formed the *same wrong rule*. Each issue carries the measured evidence rather than the argument.

**Raised 2026-08-30 (§1c):** UP-FLX-32…34 come from the
[experience loop](../experience/README.md) — three rounds of a fresh-context LLM working a real released
bundle. They are the loop's authoring findings turned into **diagnostics rather than documentation**, at
the owner's direction, and each names the class in `fluxtion-builder` 1.0.64 that already computes the
fact its message omits. §1c opens with the four rules that redirect argued for; the table there also
records the findings that are **not** compiler asks, so the redirect cannot silently drop them.
UP-FLX-32 is a second measured instance of UP-FLX-01 and belongs as a comment on issue 8, not a new issue.

**STATUS UPDATE 2026-08-29 — the §5/§6 gate opened and three of these asks are DONE.** The §H
harness landed in the M19 bench, the M19 bundle work then consumed these asks as a real client, and:
**UP-MNG-01 ☑ landed and RELEASED** in `mongoose-plugins` 1.0.39; **UP-MNG-03 ◐** declaration half
landed in the same release (enforcement half still waits on the UP-MNG-02 decision); **UP-PG-01 ☑**
landed; **UP-PG-03 ☑** landed and released 2026-08-30 (M19.5 — fetch a template by id; the analyser
half remains). UP-MNG-02 (D-05) and
UP-MNG-04 remain owner decisions, UP-PG-02's capability now exists but
its catalogue field does not, and UP-RDR-01 is untouched. The paragraph below is the round-4 framing,
kept because its reasoning about the gate is what produced this outcome.

**Raised 2026-08-25 (round 4), NOT yet filed — and gated:** §5–§7 are the server, playground and
reader halves of the **agent-brokered dev loop** (`spec-agent-brokered-dev-loop.md`, ACCEPTED v2, M18
closed in its favour): **UP-MNG-01…04** (`telaminai/mongoose` — endpoint registry file, MCP admin
tool, declared dev/prod environment, audit-sink descriptor), **UP-PG-01…02** (the two catalogue fields
that survived reading the live index; four were withdrawn and are recorded so they stay withdrawn),
**UP-RDR-01** (the Chronicle reader). None starts before the spec's §H conformance harness has a home
in the M19 bench — the dependency on another repo is acceptable because of that harness and not
otherwise. Until now the tracker said these asks "belong in upstream-asks.md" and none was here; a
session opened in the mongoose repo had the spec but no brief.

**Raised 2026-08-17 (round 3), NOT yet filed:** UP-FLX-25…27 (§2b) come from specifying M28 (rolling
windows) and M29 (external series) — the first asks here raised from making the log's own **clock**
load-bearing, and from putting a second clock beside it. UP-FLX-28…31 (§2c) answer the owner's
proposal to add backward-compatible GraphML attributes, and settle which of them belong on **edges**
rather than vertices. All seven are facts the compiler or runtime already holds at write time. §2c
opens with a **probe graph compiled 2026-08-17** that measures what actually survives into the
GraphML (answer: nothing — every edge is identical but for `id`/`source`/`target`), then the
**verified** backward-compatibility contract and the one constraint it carries. UP-FLX-26 carries a
**name collision** with the owner's proposed record header `source` that should be settled before
either is filed.

**Filed 2026-08-16 (round 2):** UP-FLX-20…22 are open as [telaminai/fluxtion issues 14–16](https://github.com/telaminai/fluxtion/issues). They come from the supermarket POC's *maintenance* round — injecting new subsystems upstream of working ones — and are the first asks here raised from changing a graph rather than building one.

**Filed 2026-08-16:** all nine fluxtion-owned asks are open as [telaminai/fluxtion issues 8–13](https://github.com/telaminai/fluxtion/issues) (some grouped: 11 covers UP-FLX-11+12, 12 covers UP-FLX-14+SHARED-01, 8 covers UP-FLX-01+SHARED-02). The four `svc-admin-web` asks (UP-WEB-01…04) belong to `telaminai/mongoose-plugins` and are **not yet filed** — see below.

---

## 1 · Fluxtion compiler — rejection codes and machine-readable diagnostics

The owner is adding rejection codes (2026-08-16). These are the asks that would have saved time in this
repo, in priority order.

### UP-FLX-01 ◐ Structured, coded diagnostics with a machine-readable form

_Filed: https://github.com/telaminai/fluxtion/issues/8_

**Target** `fluxtion` (compiler) · **Priority** high

An agent authoring a graph needs to *recover* from a rejection, not just read it. Today the message names
the symptom; the rule and the fix are in prose elsewhere, so a model guesses — and guessing at framework
semantics is exactly the failure mode this repo has documented.

What I hit, verbatim:

```
cannot find matching constructor for:Field{name=riskMonitor, fqn=com.acme.demo.node.Nodes.RiskMonitor, …}
failed to match for these fields:[orders, limit]
```

I had written `RiskMonitor(OrderTracker, int, String)`, passing the `SingleNamedNode` name as the third
argument. The message lists the mapped fields but never says *why* `name` is not one of them
(`@FluxtionIgnore` on the parent field), so nothing rules out "drop `limit`" as readily as the correct fix.

Proposed shape — the `why` line is the load-bearing part:

```
FLX-1001  constructor does not match mapped fields
  node      riskMonitor : com.acme.demo.node.Nodes.RiskMonitor
  mapped    [orders: OrderTracker, limit: int]
  found     RiskMonitor(OrderTracker, int, String)
  rule      Constructor parameters must correspond 1:1 to mapped fields, in order.
  why       'name' (SingleNamedNode) is @FluxtionIgnore'd, so it is not a mapped field and must be
            supplied by the subclass, not taken as a parameter.
  fix       RiskMonitor(OrderTracker orders, int limit) { super("riskMonitor"); … }
  see       https://fluxtion.dev/errors/FLX-1001
```

Plus `-Dfluxtion.errorFormat=json` (or a sidecar `target/fluxtion-diagnostics.json`) emitting
`{code, severity, element, rule, suggestedFix, sourceRef}`. This is the highest-leverage single item:
it turns "read English that changes between versions, guess, retry" into a deterministic loop.

**Evidence** measured — cost me one full build cycle on 2026-08-16.
**Cost to us if unfixed** none directly (we consume logs, not the compiler), but every agent authoring a
processor for the analyser to read pays it.

### UP-FLX-02 ◐ Codes for rules that are currently prose-only

_Filed: https://github.com/telaminai/fluxtion/issues/9_

**Target** `fluxtion` (compiler) · **Priority** high for the first two

| Suggested code | Rule | Why it matters |
|---|---|---|
| `FLX-1002` | `@ExportService` interface methods must return `void` | doc-only today; I had to read `claude.txt` to learn it |
| `FLX-1008` | `auditLog` used on a node in a graph built without `addEventAudit(…)` | **silent** today: builds fine, logs nothing, and the analyser then shows a node that "did not log" for a reason no one can see |
| `FLX-1003` | handler/trigger missing `boolean` return | exists as `failBuildIfMissingBooleanReturn`; give it a code |
| `FLX-1004` | `@OnTrigger(dirty=false)` with no sibling on the same parent | usually a mistake — warning, not error |
| `FLX-1005` | duplicate node name, or a name that collides with a generated identifier | |
| `FLX-1006` | `@Inject` service unresolved at build time | |
| `FLX-1007` | dependency cycle, printed as the path `a → b → c → a` | |

`FLX-1008` is the one with a direct analyser consequence: it produces a log that is *misleading* rather
than merely absent, and no downstream tool can distinguish it from a node that legitimately stayed quiet.

---

## 1b · Fluxtion compiler — the maintenance manoeuvre

Raised 2026-08-16 from the **supermarket POC round 2**: nine new subsystems injected *upstream* of ten
nodes that already worked (23 → 44 application nodes, 44 → 95 edges, 31 → 90 distinct dispatch paths).
That exercise is the first time this repo has evidence about **changing** a Fluxtion graph rather than
building one, and it produced two asks that greenfield work could not have surfaced.

### UP-FLX-20 ◐ Warn when a new parent is a data reference, not a trigger

_Filed: https://github.com/telaminai/fluxtion/issues/14_

**Target** `fluxtion` (compiler) · **Priority** high — this is the sharpest edge in the maintenance path

**Evidence — measured.** `PurchaseOrderRaiser` was given a `SupplierScorecard` reference so its purchase
orders could *quote* a reliability score. A plain field reference makes the referenced node a **parent**,
and a parent firing runs the child's `@OnTrigger`. So a scorecard update — which happens on every
delivery — fired the order raiser with no reorder decision behind it, and the app emitted:

```
PO,null,0,unknown,score=1.0
```

A purchase order for no product, from a node whose own logic had not changed. The build was clean, the
subsystem tests were green, and it took reading the output CSV to notice.

`@NoTriggerReference` is the correct answer and it works. The problem is that **the default is the wrong
way round for this manoeuvre**: when you inject a reference into working code you usually want the data
and not the trigger, and the quiet path silently gives you both. Five of this round's ten injections
turned out to be data-only.

**The ask.** A build warning when a node's `@OnTrigger` method body does not reference a field that is a
graph parent:

```
FLX-1020 WARN  purchaseOrders: 'suppliers' is a trigger parent but @OnTrigger raise() never reads it.
               If it is a data reference, mark it @NoTriggerReference.
```

Detectable statically — it is "does the trigger method touch this field" — and it would have caught the
defect above at build time rather than in a CSV. Not an error: a node may legitimately want to recompute
on a parent it does not read.

**Cost to us if unfixed.** The analyser can show that a node ran when it should not have, but only after
a log exists and only if someone steps the trace. This class is cheap to catch at build time and
expensive to catch afterwards, because the symptom appears in output rather than in behaviour anyone is
testing.

### UP-FLX-21 ◐ Regenerate before compiling, or fail with a message that says what happened

_Filed: https://github.com/telaminai/fluxtion/issues/15_

**Target** `fluxtion` (maven plugin) · **Priority** medium · supersedes the round-1 note on F6

**Evidence — measured, twice, and it scales with the size of the change.** The generated processor is
checked in and compiled as ordinary source, but the plugin regenerates it at `process-classes` — *after*
`compile`. So any change to a node constructor breaks the build against the stale generated file before
regeneration can run. Round 1 hit this with **one** changed constructor. Round 2 changed **ten** and got
ten errors, all inside generated code:

```
StoreProcessor.java:[131,7] constructor MaintenanceScheduler cannot be applied to given types;
  required: FridgeMonitor,CompressorHealth,ModelDrift
  found:    FridgeMonitor
```

The messages are accurate and point at the generator's output, so they read like a generator bug rather
than a stale artefact. The fix — delete the generated file and rebuild — is not discoverable from them.

**The ask**, in preference order:

1. Bind generation *before* `compile` when the output directory is a source root, so a constructor change
   simply regenerates.
2. Failing that, detect the case and say so: `FLX-1021: generated processor is stale — it was built
   against a different signature of com.acme.store.equipment.MaintenanceScheduler. Delete
   src/main/java/…/StoreProcessor.java and rebuild.`

**Cost to us if unfixed.** One wasted iteration per structural change, every time, for anyone who
checks generated source into their repo — which the golden-path examples encourage.

### UP-FLX-22 ◐ Give the dirty contract and reference-kind an artefact

_Filed: https://github.com/telaminai/fluxtion/issues/16_

**Target** `fluxtion` (compiler + GraphML) · **Priority** medium · **extends round 1's finding**

Round 1's assessment ended: *"a node's dirty contract has no artefact… it is the one interface element
between subsystem author and orchestration author with nothing machine-readable behind it."* Round 2
makes that concrete and adds a second field to it.

**Evidence — measured.** The most expensive bug of round 2 was a **getter's meaning**, not its type.
`StockLedger.lastQty()` returns the shelf level *after* the movement; a new subsystem read it as "units
just sold". Both are `int`, both read plausibly at the call site, and the result was demand predicted as
a function of stock on hand — a feedback loop that took the milk reorder point to 908 units and raised
264 purchase orders in a day.

That is not something a compiler can check. But two adjacent facts **are** machine-readable and would
have narrowed it:

- **Reference kind** — is this parent a trigger or a data reference? Already known to the compiler
  (`@NoTriggerReference`); not currently in the GraphML, so no downstream tool can show it.
- **Dirty contract** — a node's `@OnTrigger` returns `true` under conditions stated only in prose. An
  optional `@DirtyWhen("a reorder is needed")` carried through to the GraphML would let the analyser
  render, on the edge, *why* propagation happens.

**The ask.** Emit reference kind as a GraphML edge attribute now (free — the compiler has it), and
consider a declarative dirty-contract annotation.

**Cost to us if unfixed.** The analyser draws every edge identically, so a data reference and a trigger
reference are indistinguishable in the topology — including in the two-view finding report. A reader
cannot tell "this node ran because of that one" from "this node read that one" without opening source.

### UP-FLX-23 ◐ Replay cannot serialise record events

_Filed: https://github.com/telaminai/fluxtion/issues/17_

**Target** `fluxtion` (builder/replay) · **Priority** high — it is silent until the worst moment

**Evidence — measured.** `YamlReplayRecordWriter` serialises inbound events through SnakeYAML's JavaBean
representer, which needs `getX()`. A record exposes `x()`. A 309-node application with 24 record event
types throws on the first event:

```
YAMLException: No JavaBean properties found in com.acme.store.event.StoreEvents$TariffChanged
```

**Why it is worse than a missing feature.** The two capabilities pull against each other. Records are the
right way to write Fluxtion events *because* their generated `toString()` lands readably in the audit
log — which is what a reader sees in `eventToString` on every record. So an event vocabulary optimised
for the **audit log** cannot be recorded for **replay**, and nothing says so until you try. The recorder
is inert while its target writer is unset, so the failure surfaces the first time someone records a
production incident.

**Cost to us if unfixed.** The analyser's pitch — and three rounds of assessment in this repo — assume
the audit log plus a replay log is a complete, re-runnable account of a run. For any application written
in modern Java that pair is currently unavailable, so "replay it locally with a debugger" is advice that
does not work for the apps most likely to be written today.

### UP-FLX-24 ◐ Replay omits exported-service calls, so a replayed run diverges

_Filed: https://github.com/telaminai/fluxtion/issues/18_

**Target** `fluxtion` (runtime + builder/replay) · **Priority** highest of the replay asks

**Evidence — measured.** An `Auditor` tape records events; an `@ExportService` call is an entry point
that dispatches identically but is not an event, so it is never recorded. Replaying a 309-node
application's trading day:

```
replay: 582 live cycles, 574 replayed, 295 divergent
```

**295 of 574 replayed cycles produce different node output** — because three `setPrice` calls never
replayed, the price book was empty, and every downstream figure was computed from nothing. The wrong
numbers are plausible (`stockAtCost: 262.9`) and nothing throws.

**Why it matters here.** This repo's whole argument is that the audit log plus a replay log is a
complete, re-runnable account of a run. It is not, for any application with an operator surface — and
"what did the operator do" is usually the regulatory question. A replay that yields a believable
alternative history is worse than no replay, because it presents as evidence.

**Cost to us if unfixed.** The analyser cannot tell a replayed log from a live one, and has no basis to
warn that a log it is showing came from an incomplete replay.

---

## 1c · Fluxtion compiler — diagnostics measured by the experience loop

_Raised 2026-08-30 from [`docs/experience/`](../experience/README.md): three rounds in which a
fresh-context LLM built and diagnosed inside a **real released bundle**, on a different task shape each
round. Roughly half the findings turned out to be **Fluxtion authoring** friction rather than analyser
friction — which the owner named directly: *"It's descending into how do I author Fluxtion? Luckily the
compiler gives feedback we will extend to compiler codes on failure."* This section is that redirect.
The authoring findings are written up as diagnostics instead of as documentation, and the reason is in
the advice below._

**A branch is now working on the diagnostics.** Everything this section argues, plus the measured
evidence behind it and an offer to test candidate messages against fresh agents, is collected for them in
[`notes-for-the-compiler-diagnostics-work.md`](notes-for-the-compiler-diagnostics-work.md). Send that
rather than this file: it is written as input to their design rather than as asks against it.

### The advice — four rules the loop paid for

_The end-to-end method these asks serve — what belongs in a message versus a bootstrap doc, and how the
doc set shrinks as each diagnostic lands — is [`spec-authoring-experience.md`](../specs/spec-authoring-experience.md)
(D-AX1, D-AX3)._

**1 · Documenting around a bad message is the wrong repair, and the loop measured why.** Round 02's most
expensive finding (R2-A) was fixed here by adding three paragraphs to a bootstrap document. Round 03's
agent **never opened that document** — it was measured, not assumed — and still reached a *more* accurate
conclusion than the document contained. A diagnostic is read at the moment of failure by construction; a
document is read only if someone chooses to. Where both are possible, the message wins, and the prose
should be deleted once the message lands.

**2 · Name the remedy, not just the symptom.** This is not a new idea to the codebase: §UP-FLX-32 shows
two `throw` sites in the **same method**, one of which names the annotation that fixes it and one of
which names nothing. The second is the one that cost a round.

**3 · The fact needed for the remedy is almost always already in scope.** Each ask below names the class
that already computes it. None asks the compiler to derive anything new.

**4 · Codes matter most on the cases that do NOT fail the build.** The expensive findings in this loop
were silent: a bean that is simply never a node, a node that can never record a value. A build that goes
green and produces a wrong or empty answer is the class this whole product exists to catch, and it is the
class with no message at all today.

### Where each authoring finding actually belongs

Recorded in full so that "we turned the loop into compiler asks" cannot quietly mean "we turned *some* of
it into compiler asks". Not every finding is a diagnostic, and saying which are not is part of the ask.

| Finding | Shape of the right fix | Where it went |
|---|---|---|
| R2-A stateful field fails constructor matching | **diagnostic** | UP-FLX-32 |
| R1-G a bean absent from `nodeBeans` is silently not a node | **diagnostic** (silent case) | UP-FLX-33 |
| R1-A / R3-D a node that can never record its own values | **diagnostic + GraphML** | UP-FLX-34 |
| R1-G `@OnTrigger`'s boolean return | **already in `claude.txt`** — corrected 2026-08-30, see below | the bundle must POINT at the canon, not restate it |
| R2-F `getLatestEvent()` returns `Object`; R2-E `auditLog` value-type overloads | **documentation** — conventions, not failures | absent from `claude.txt`; UP-FLX-35 |
| R2-B "if the build stops at `process-classes`, the key is why" | **my error**, not upstream's — `fluxtion-maven-plugin:scan` binds to that phase | fixed in the doc set |
| R2-C thin fixture · R2-D hidden feed offset · R3 bundle defects | **bundle / server** | the bundle owner; feed offset belongs with §5 |

### UP-FLX-32 ◐ The constructor-match failure names one of three wiring strategies, and no remedy

_Filed: https://github.com/telaminai/fluxtion/issues/19_

**Target** `fluxtion` (`fluxtion-builder`) · **Priority** highest of this section — it is a one-line fix
against a measured, repeated cost · *a second measured instance of UP-FLX-01; attach to
[issue 8](https://github.com/telaminai/fluxtion/issues/8) rather than filing anew*

**Measured, round 02.** A node holding its own state — two `HashMap`s for a per-symbol running count and
maximum — fails the build with:

```
cannot find matching constructor for: Field{name=symbolStats, …}
failed to match for these fields:[countBySymbol, maxPriceBySymbol, rootNode]
```

The fix is to mark the state `transient`. **The message names *constructor matching*, so "add a
constructor taking the maps" is at least as plausible a reading** — and it is wrong. Both examples shipped
in the bundle hold only null-at-construction state, so there was nothing to copy from either.

**Located and verified in the artefact, 2026-08-30** (`fluxtion-builder` 1.0.64, entry
`com/telamin/fluxtion/builder/generation/model/LiveGraphSourceGenExtractor.class`, read from the jar):

- The throw is in **`generateComplexConstructors()`**, after
  `ReflectionUtils.getConstructors(class, ConstructorMatcherPredicate.matchConstructorType(…))` returns an
  **empty** set. It is a bare `RuntimeException` — no code, no rule line, no remedy.
- **The same method already names a remedy for its neighbour.** A second `throw`, thirty bytecodes later,
  handles `ConstructorMatcherPredicate.validateNoTypeClash(…)` and builds its message as
  `"cannot find matching constructor for:" + node + " use @" + AssignToField.getSimpleName() + " to
  resolve clashing types these fields:" + […]`. So one failure names the annotation that fixes it and its
  sibling names nothing, under the same message prefix.
- **The remedy is computed a few frames away.** The predicate that decides which fields must be
  constructor-matched is `lambda$generateComplexConstructors$22` — a lambda of the throwing method — and
  it reads `@ConstructorArg`, `@FluxtionIgnore`, `Modifier.isStatic` and **`Modifier.isTransient`**
  directly. The failing field list is therefore, by construction, a list of fields the compiler has just
  finished deciding are *not* transient and *not* `@FluxtionIgnore`d.

*Verified: the throw sites, the two message shapes, and the annotations/modifiers the predicate reads.
**Not** verified: the full boolean structure of that predicate — the bytecode suggests further conditions
and this ask does not depend on them.*

**The ask.** At the empty-match site, say what the sibling site already says:

```
FLX-1009  no constructor matches this node's mapped fields
  node      symbolStats : com.example.myapp.node.SymbolStats
  unmatched [countBySymbol: Map, maxPriceBySymbol: Map, rootNode: RootNode]
  rule      Every non-transient instance field must be reachable from a constructor parameter —
            the AOT generator rebuilds each node by calling one.
  fix       Node-local state is not graph structure: mark it `transient`
            (or @FluxtionIgnore). Only fields that are graph edges or configuration
            belong in the constructor.
  see       https://fluxtion.dev/errors/FLX-1009
```

The `fix` line is the whole ask. Everything above it is already in the message or in scope.

**The fact is ALREADY DOCUMENTED, and that strengthens this ask rather than retiring it (checked against
`claude.txt`, 2026-08-30, after the owner asked whether I had read it — I had not).** The framework canon
states it plainly: *"Source-gen IS serialisation. Fluxtion reflects over every node's fields and emits a
generated processor that reconstructs them via constructor calls and field assignments"*, with the remedy
*"annotate `@FluxtionIgnore` … or declare `transient`. Both are valid."*

So this was never a knowledge gap upstream. A correct, clearly-written rule existed and **the author still
guessed wrong**, because the rule was not where the failure was. That is the cleanest available case for
tiering a fact into a message rather than prose: documentation was not missing, it was **unread at the
moment it mattered**. Rule 1 of the advice above is no longer an argument — it is an observation.

**Retested 2026-08-30 (round 04), and the result is stronger than the original finding.** Two fresh agents,
same bundle, same task; one was given the playground's authoring resources and one was not. **Both hit this
error.** The resourced agent had the triage table *fetched and in context*, indexed by this very error
string — and still wrote the field wrong, still failed the build, then fixed it in one step by quoting the
table. **Documentation at maximum availability did not prevent it.** That is the case for a message, made
by measurement rather than by argument.

**The message must state the RULE, not a list of tricks — round 05 found three escapes and the owner named
a fourth route the message hides entirely.**

Across rounds 04-05, six agents produced **three different working fixes**, none identical: `transient`
(documented), **removing `final`**, and **leaving the field null and initialising it lazily**. All three
are sound against the field-inclusion predicate, which admits a field only when it is non-static, **final**
and non-transient. Enumerating remedies invites a fourth.

**And the message is worse than incomplete — it is misleading about what the builder supports.** Owner,
2026-08-30: *"Fluxtion supports the JavaBean pattern to set references if constructor references are not a
good fit or problematic."* **Verified in the same class**, which runs three assignment strategies from one
entry point:

```
generatePropertyAssignments()       // JavaBean setters — beanPropertyMap, "set" prefix
generatePublicMemberAssignments()   // public fields
generateComplexConstructors()       // constructors — the ONLY one that throws
```

So a field can be supplied by a **constructor argument, a setter, or a public member** — and the message
names only the strategy that failed. An author reading *"cannot find matching constructor"* has no way to
learn that a setter would have worked. **Not one of the six agents used it.** Three found accidental
escapes instead, and each shipped a fix it could not explain.

The message should therefore fork on the question the author actually faces:

> is this field **node-local state** (exclude it: `transient` / `@FluxtionIgnore`) or a **reference the
> graph must supply** (wire it: constructor arg, JavaBean setter, or public member)?

**Evidence** measured — one full build cycle in round 02, reproduced in both arms of round 04. **Cost to us if unfixed** none in the analyser directly; paid by every author of a stateful node,
which is most non-trivial nodes.

### UP-FLX-33 ◐ A Spring bean that is neither selected nor ignored is silently not a node

_Filed: https://github.com/telaminai/fluxtion/issues/20_

**Target** `fluxtion` (`fluxtion-builder`, `extern.spring`) · **Priority** high — this is the silent class

**Measured, round 01 (R1-G, a COULD-NOT-FIND).** Adding a `<bean>` to the designer context is not enough:
the bean id must also appear in `fluxtionSpringConfig`'s `nodeBeans` list. Omit it and **the build is
green, the app runs, and the node is simply not in the graph** — it never fires, logs nothing, and the
audit log cannot report it as missing because it was never declared. The fresh agent could not determine
from anything shipped whether the second edit was required.

**The documentation half of this is CORRECTED (2026-08-30) — the rule IS published, and the ask survives
anyway.** The playground's `spring-authoring/skill.md` states *"Every node is a `<bean>` in `nodeBeans`;
support beans go in `ignoredBeans`"* and *"Don't rely on implicit node discovery"*; `contract.md` is more
precise still: *"If present, only these beans are added as explicit Fluxtion nodes; **referenced children
are still discovered by Fluxtion**"*. So the bundle did not point at material that already existed — a
bundle defect, not an upstream doc gap, and my own doc set stated the rule **too strongly** by omitting
the transitive case (fixed).

What survives untouched is the **silent build**: a bean that is neither listed nor referenced by anything
listed is dropped without a word. Documentation reduces how often that happens; it cannot tell you it has
happened. That is what the ask below is for, and the precise condition it should report is *"declared in
the context, in neither list, and unreferenced"*.

**Verified in the artefact, 2026-08-30** (`fluxtion-builder` 1.0.64,
`com/telamin/fluxtion/builder/extern/spring/`):

- `FluxtionSpringConfig` declares both an include list, `nodeBeans`, and an explicit exclude list,
  **`ignoredBeans`** — so "not selected" and "deliberately excluded" are already distinguishable concepts
  in the model.
- The class already carries **two strict modes** for adjacent concerns: `strictEventHandlerBindings` and
  `strictServiceBindings`, with messages of the form *"Node 'x' … but is not listed in strict event
  handler bindings"*. The pattern this ask wants exists twice in the same file.
- The one nearby error, *"Unknown or unselected node 'x' in event handler binding"*
  (`IllegalArgumentException`), fires **only when a binding references the bean**. A bean that is merely
  never selected reaches no check at all.

**The ask**, in preference order:

1. A build-time **INFO or WARN listing every context bean that is in neither `nodeBeans` nor
   `ignoredBeans`** — "these beans exist and are not in the graph" — which is enough on its own, because
   the failure mode is not knowing the list is exhaustive.
2. A `strictNodeBeans` flag, mirroring the two strict modes already present, turning that list into a
   build failure for projects that want it.

**Cost to us if unfixed** the analyser reports the node as **absent from the declared graph**, which is
correct and unhelpful: coverage subtracts against the graph, so a node that was never declared cannot be
reported as one that never ran. This is precisely the case the product cannot see, and it is cheap to
catch one layer up.

### UP-FLX-34 ◐ Declare a node's audit capability in the GraphML

_Filed: https://github.com/telaminai/fluxtion/issues/21_

**Target** `fluxtion` (compiler, graphml emission) · **Priority** medium · *vertex-shaped; rides §2c's
verified additive mechanism · direct analyser payoff*

**Measured across rounds 01 and 03, and confirmed by an owner correction (R1-A, R3-A, R3-D).** Whether a
node can record its own values is decided by one fact: does it implement **`EventLogSource`**
(`void setLogger(EventLogger)`) — whether directly, or by extending the convenience class `EventLogNode`,
or by extending a Fluxtion base such as `SingleNamedNode` that already does. Verified against
`fluxtion-runtime` 1.0.13. A node that implements it neither way still **appears** in the log — the
generator emits an `auditInvocation` at each dispatch site — but can only ever show its method name.

That distinction cost this loop three separate errors, and it is not visible in any artefact a reader has.

**The ask.** A per-node data key, e.g. `auditCapable="true|false"`, plus the reason
(`interface | baseClass | none`). The compiler resolves the type hierarchy already; this is emitting a
fact it holds.

**Cost to us if unfixed — this one is paid in the analyser today.** `topology/NodeLogging.java` (M40.2b)
determines audit capability by **walking `fluxtion-runtime` itself** and maintaining an `AUDIT_CAPABLE`
set, with a deliberate rule that excluding a node requires proof — source in hand, no supertype, no
logger-type mention — because a wrong exclusion silently flatters the coverage number. That machinery
exists solely because the GraphML does not say. With the key declared, `SILENT_BY_CONSTRUCTION` becomes a
read rather than an inference, and the analyser stops shipping a copy of the framework's type hierarchy.

### UP-FLX-35 ◐ The audit contract is undocumented — usage is shown, `EventLogSource` is never named

_Filed: https://github.com/telaminai/fluxtion/issues/22_

**Target** `fluxtion` docs (`docs/claude.txt`) · **Priority** high — it is the LLM-facing canon, and this
is the one subject where its silence is most expensive · *pairs with UP-SHARED-02*

**Checked 2026-08-30 across FIVE sources**, prompted by the owner asking whether I had been reading the
canon — and then the playground's `build-with-ai` — while authoring the bundle's context assets. I had read
neither. Reading them changed two entries above and produced this one.

**Draft content ready:** [`upstream-content/audit-authoring.md`](upstream-content/audit-authoring.md) —
the section itself, every fact read from `fluxtion-runtime` 1.0.13 and `fluxtion-builder` 1.0.64.

**Confirmed by an agent holding all five resources (round 04).** Asked how it made its node record values,
it named the gap itself: *"The `EventLogNode`/`auditLog` API surface (method signatures, return type,
whether `.info()` chains) — not documented in any of the 5 listed resources."* It fell back on copying the
project's own example. A measurable cost followed: unable to confirm that `info` returns `this`, it wrote
three unchained statements, where the unresourced agent read `EventLogger` from the sources jar and
chained. **The gap does not merely fail to help; it produces worse code in the agent that behaves
correctly.**

| Source | Covers the audit-authoring contract? |
|---|---|
| `fluxtion` `docs/claude.txt` | **no** |
| playground `/CLAUDE.md` (the orientation an LLM is told to load first) | **no** |
| playground `/spring-authoring/skill.md` | **no** |
| playground `/spring-authoring/contract.md` | **no** — `logLevel` and `auditors` appear as config fields with no mechanics |
| playground `/audit-replay` | **no, and it implies the opposite** — see below |

**Present and clear elsewhere, so NOT asks:** the serialisation rule with its `transient` /
`@FluxtionIgnore` remedy (canon *and* the playground triage table); `@OnTrigger` propagation including
`dirty = false`; `nodeBeans` / `ignoredBeans` (the Spring skill and contract).

**`/audit-replay` is the sharpest instance, because it is not merely silent.** It states *"Audit and replay
are not a logging layer you bolt on. They fall out of how the processor is generated"* — true for which
nodes fired, **false for what each node reports**. An author who reads it concludes no instrumentation is
needed, which is precisely what round 01's agent concluded. A gap misleads by omission; this misleads by
assertion.

**Absent:**

| Missing | Why it costs | Loop evidence |
|---|---|---|
| **`EventLogSource` / `setLogger` / `EventLogNode`** — the contract by which a node gets an `auditLog` handle | an LLM cannot make a node record its own values, and cannot tell that a node still *appears* in the log without it | R1-A, R3-A, **R3-D** — three separate errors, mine and the agents' |
| **Position in `nodeLogs` is dispatch order** | the property the audit log's causal reading rests on; the term `nodeLogs` does not appear at all | R1-E — the agent saw the ordering and recorded *"I do not know whether it is meaningful"* |
| **The authoring loop** — compile → read the message → run → **read the audit log** → iterate | the canon describes `setAuditLogProcessor` mechanics but prescribes no cycle | D-AX2 in [`spec-authoring-experience.md`](../specs/spec-authoring-experience.md) |
| **`nodeBeans` / `ignoredBeans`** on `fluxtionSpringConfig` | Spring XML authoring is named as a supported route with no statement of what makes a bean a node | R1-G, and UP-FLX-33's silent case |
| `auditLog` value-type overloads; `getLatestEvent()` returning `Object` | small, but each sent an agent outside the project to read framework source | R2-E, R2-F |

**Why this is the sharpest of the doc asks.** `claude.txt` is written *for a model*, and it covers the
compiler thoroughly while leaving the **audit log** — the artefact the whole downstream toolchain exists to
read, and the thing that makes a Fluxtion application explicable — essentially undocumented for the author
who has to emit it. An LLM given the canon can build a correct graph that records nothing.

**Cost to us if unfixed** every generated bundle must restate the audit contract in its own `CLAUDE.md`,
which is how three wrong versions of it came to be written here. With it in the canon, the bundle **points**
rather than restates, and the analyser stops depending on per-template prose for its own precondition.

### UP-FLX-37 ◐ Invocation tracing is fixed at generation time, so an untraced processor cannot be made traced

_Filed: https://github.com/telaminai/fluxtion/issues/25_

**Target** `fluxtion` (runtime) · **Priority** high · *the PRODUCER half of UP-FLX-11, which asks the record
to declare the regime*

**Verified in `fluxtion-runtime` 1.0.13 and two generated processors, 2026-08-30.** `EventLogManager.trace`
is assigned in exactly three places — its initialiser, `tracingOff()` and `tracingOn(level)` — and the last
two are builder-time, reached from `EventProcessorConfig.addEventAudit`. The runtime control path,
`calculationLogConfig(EventLogControlEvent)`, adjusts **per-node logger levels** and never touches it.

So `DataFlow.setAuditLogLevel` can make a traced processor quieter; nothing can make an untraced one
traced. **Turning tracing on requires regenerating, which requires a key** — and for a committed AOT
processor running without one, the audit regime is not a setting at all.

That is the difference between *"an absent node did not run"* and *"an absent node said nothing"*: a team
that discovers mid-incident it needs the stronger regime cannot obtain it from the running system.

**Cost to us if unfixed** the analyser's coverage verdict is only as strong as a regime the operator cannot
change, and no tutorial or measurement can demonstrate the untraced case against a traced bundle — measured
in round 06, where an agent correctly reported it could not.

### UP-FLX-36 ◐ Sibling dispatch order is undocumented, and authors reliably infer the WRONG rule

_Filed: https://github.com/telaminai/fluxtion/issues/23_

**Target** `fluxtion` docs (+ optional GraphML) · **Priority** high — it is the only ask here whose gap
*manufactures* a false belief rather than leaving a hole

**Measured, round 05.** Ties between nodes at equal dependency depth break by **natural order of node
name** — `TopologicalOrderIterator(graph, new NaturalOrderComparator(inst2Name))` in
`TopologicallySortedDependencyGraph`. Published in none of the six authoring resources.

**Four of six independent sessions concluded the rule is "declaration order in `nodeBeans`"**, and the
error is structurally reproducible: append the bean last, pick a name that sorts after the existing
sibling, and declaration order and name order predict identical output — the obvious experiment cannot
discriminate. All six read the generated `handleEvent`; four were still wrong. One reached the truth only
by decompiling the builder.

**Cost to us if unfixed** the analyser recomputes layout layers rather than reading a declared order, so
its ordering is reconstructed where the compiler's is authoritative — see the dispatch-order-index note in
§2c, which this ask now has evidence for.

---

## 2 · Fluxtion runtime — metadata the audit log should carry

The analyser's hardest problems are all "the log does not say". Each item below is something the runtime
knows at write time and the analyser has to guess at afterwards.

### UP-FLX-10 ◐ Mark a re-dispatched record as re-dispatched

_Filed: https://github.com/telaminai/fluxtion/issues/10_

**Target** `fluxtion` (runtime, `EventLogManager`) · **Priority** high — biggest single win

**Measured, 2026-08-16.** A node calling `processReentrantEvent` queues the event; the generated
processor then runs

```java
afterEvent();                                  // publishes the audit record
callbackDispatcher.dispatchQueuedCallbacks();  // only now is the queued event dispatched
```

so the re-dispatch is **not** extra rows on the causing cycle — it is a **separate `eventLogRecord`**,
same thread, stamped with a normal `eventTime` (an exported call is stamped `-1`), carrying nothing that
says it came from inside. The graphml is no help: the raised event is an ordinary `EVENT` node with no
edge from the node that raises it.

Ask: a header field on the record, e.g.

```yaml
eventLogRecord:
    event: RiskBreachEvent
    origin: reentrant          # external | reentrant | newEventCycle | exportedCall | lifecycle
    causedBy: riskMonitor      # the node that raised it, when origin is reentrant
```

**Cost to us if unfixed** the analyser can only link effect to cause by parsing the **processor source**
for `processReentrantEvent`/`processAsNewEventCycle` call sites and correlating with record adjacency —
inference, so it must be presented as a guess or not at all. Fixture and two pinning tests live at
`src/test/resources/topology/demo-quote-audit.yaml` + `RealProcessorPairTest`.

### UP-FLX-11 ◐ Traced-regime marker in the record header

_Filed: https://github.com/telaminai/fluxtion/issues/11_

**Target** `fluxtion` (runtime) · **Priority** high · *(carried over: reviewer follow-up F2)*

Whether a record lists **every node invoked** or **only nodes that called `auditLog`** changes the meaning
of absence completely — it is the difference between "did not run" and "we cannot say". Today the analyser
infers it (`AuditTrace.tracesEveryInvocation`: every entry carries `thread` **and** `method`), which is
sound but fragile — one node logging a business key called `method` would break it, and a cycle where no
node logged is unclassifiable.

Ask: `auditLevel: TRACE` (or the owner's preferred minimal spelling, `trace: true|false`) in the
record header. **Per record, not per file**, deliberately: tracing is compiled in at build time but
gated at runtime (`EventLogControlEvent`), so one file can legitimately contain both regimes — a
file-level flag would lie across the switch.

**Owner endorsement + consumer contract (2026-08-17):** the owner independently proposed exactly this
("state trace:[true|false] in the header — this allows the analyser to either draw with certainty or
say the non-log is a potentially missing step"). On landing, the analyser will: (1) prefer the declared
flag over the `AuditTrace` heuristic wherever the header carries it, keeping the heuristic only for
legacy logs; (2) render classification language as DECLARED fact rather than inference —
`trace:true` → an absent node is drawn as **did not run** with certainty; `trace:false` → absence is
drawn as a **potentially missing step**, never as absence of execution; (3) resolve the currently
unclassifiable case (a cycle where no node logged) that the heuristic cannot answer at all.

**Cost to us if unfixed** an inference on every record, and no answer at all for empty cycles.

### UP-FLX-12 ◐ Populate `ProcessorDescriptor.Meta`

_Filed: https://github.com/telaminai/fluxtion/issues/11_

**Target** `fluxtion` / `mongoose` · **Priority** medium · *(carried over: reviewer follow-up F2)*

`sourceFingerprint()` and `graphmlResource()` are both emitted as `null` today. Populating them would let
the analyser pair a log with the right graphml automatically instead of asking the user to find the file,
and would let it say *this graph is not the build that produced this log* with certainty rather than by
counting unmatched instance ids. The reviewer noted `sourceFingerprint` is also the natural carrier for a
provenance stamp.

### UP-FLX-13 ◐ One spelling for exported-call signatures

_Filed: https://github.com/telaminai/fluxtion/issues/13_

**Target** `fluxtion` (runtime/compiler) · **Priority** medium

Two forms are in the wild for the same fact:

```
public boolean com.acme.VenueMonitor.onConnected(com.acme.ConnectedEvent)   ← declaring class present
@Override
public void suspendQuoting(String arg0)                                       ← method name only
```

The second names no class, so there is nothing to match a node against. The analyser now falls back to
"the sole authored exported service", which is inference-free only while there is exactly one.

Ask: always emit the declaring class, and drop the embedded newline (a raw `\n` inside a YAML scalar makes
every downstream reader handle a multi-line field for no gain).

**Cost to us if unfixed** entry-point resolution is ambiguous for any graph with two or more exported
services — see `EntryPointResolver.addSoleExportedService`.

### UP-FLX-14 ◐ Carry lifecycle/annotation facts into the GraphML

_Filed: https://github.com/telaminai/fluxtion/issues/12_

**Target** `fluxtion` (compiler, graphml emission) · **Priority** medium

GraphML carries no annotations, so the analyser cannot tell that a node's callback is `@AfterEvent`,
`@Initialise` or `@Start` — all of which **fire without upstream propagation**. "Something downstream
logged, therefore this ran" is unsound for exactly those nodes and the analyser has no way to detect them
(recorded in `docs/ONBOARDING.md`).

Ask: a style or data key per node for the callback kinds it declares. Even a coarse
`lifecycleOnly="true"` would let the view stop reasoning about propagation for those nodes.

---

## 2b · Fluxtion runtime — what the log must say to be correlated with anything else

Round 3, raised 2026-08-17 while specifying **M28** (rolling windows over the log's own clock) and
**M29** (`spec-external-series.md` — plotting a foreign CSV, e.g. a FIX log an agent parsed, beside the
audit-derived series). Both make the audit log's *time base* load-bearing in a way it has never been:
M28 computes `mean(x, "5m")` and `rate(x, "1m")` against `logTime`, and M29 puts a second clock on the
same axis. These three asks are the facts the log would need to carry for that to be sound rather than
assumed.

### UP-FLX-25 ☐ Declare the log's time base — source, zone and resolution

**Target** `fluxtion` (runtime, record header) · **Priority** high — blocks honest cross-log correlation

**Measured, 2026-08-17.** A record carries a bare number and nothing about what produced it:

```yaml
eventTime: 1767258000080
logTime:   1767258000090
```

(from `src/test/resources/topology/demo-quote-audit.yaml`). The analyser assumes **epoch
milliseconds** in six files — `Instant.ofEpochMilli` in `TimeFormat`, `TimeRangeSlider`, `SeriesScan`,
`AggregateService`, `SessionFacts` and `RecordExporter` — and `SeriesScan:211` additionally hardcodes
`ZoneOffset.UTC` when it buckets by minute or hour. Nothing in the log confirms any of that; if a
processor ever emitted micros, every timestamp in the app would be silently wrong by a factor of 1000
and no test anywhere would fail.

Ask: a once-per-file (or per-record-header) declaration, e.g.

```yaml
timeBase:
    epoch: millis            # millis | micros | nanos
    zone: UTC                # the zone the writer's clock was reporting
    source: wallClock        # wallClock | monotonic | injected(ClockStrategy)
```

`source` matters as much as the unit: a log written under an injected/simulated `ClockStrategy` (a
replay, a test) must not be silently aligned against a real venue log, and today nothing distinguishes
the two.

**Consumer contract (added 2026-08-17, review F2 — what the analyser will do the day this lands):**
(1) `TimeFormat` renders in the DECLARED zone for declared logs, retiring the display-decision UTC
default there (undeclared logs keep it, labelled as an assumption); (2) `SeriesScan` buckets in the
declared zone, retiring the hardcoded `ZoneOffset.UTC` at SeriesScan:212; (3) M29's external-series
echo upgrades from "declared offset shown" to a verdict — **"clocks comparable"** / **"NOT
comparable: audit log is monotonic, CSV declares wall-clock"**; (4) a `source: injected` log draws a
visible replay banner so simulated time is never silently aligned with venue time; (5) rolled sets
(M30) compare per-file declarations and refuse a set that mixes epochs or sources louder than any
timestamp heuristic could.

**There is now exactly one place to receive it (added 2026-08-18, after M31 shipped).** The reader SPI
makes `timeBase()` a **mandatory** declaration on every log source, and the built-in YAML reader
answers it at `spi/YamlAuditReader.timeBase()` — today returning `TimeBase.wallClockMillisUtc()` with
a comment saying, in as many words, that the native log declares nothing and this is the analyser's
long-standing assumption *stated in one place instead of six*. The day this ask lands, that single
method reads the header instead of asserting, and all five consumer behaviours above become true
without touching anything else. A plugin reader for parquet or Chronicle already declares its epoch
unit truthfully, so **the native format is currently the least self-describing source the analyser
can open** — which is the sharpest form of the argument.

**Cost to us if unfixed** M29 pushes the whole burden onto the user — the CSV's format and zone are
*required* inputs (D-F1: never inferred) precisely because the analyser cannot state its own side of
the comparison. It can show a declared offset but can never say whether the two clocks are actually
comparable. A `rate(x, "1m")` computed against an unlabelled clock is a number with no unit.

### UP-FLX-26 ☐ Identify the record's origin — process, host, processor instance

**Target** `fluxtion` (runtime, record header) · **Priority** medium

Nothing in a record says which process wrote it. A file that concatenates two processors' logs is
indistinguishable from one processor's, so every time-ordered operation the analyser performs —
the `at` anchor's binary search (M26.2), rolling windows (M28.3/.4), band intervals (M28.6) — silently
assumes one monotonic time base. That assumption was accepted explicitly as a stated boundary in the
review of `feat/b-m20-3-m26-m27` (assumption A2) rather than defended, because there is nothing in the
log to defend it with.

Ask: `writer: {host, pid, processorId}` in the header, or once per file.

!!! warning "Name collision — resolve before either is filed"

    This ask originally proposed the key `source`. The owner independently proposed (2026-08-17) a
    `source` property on the record header meaning **what caused this cycle** — external event,
    re-dispatch, exported service call — which is UP-FLX-10's `origin` enum arrived at from the other
    direction, and confirms it is the right shape. Two different facts cannot both be `source`.
    Suggested split: **`origin`** = why this cycle ran (UP-FLX-10's enum, plus `causedBy`), and
    **`writer`** = which process emitted the record (this ask). Both are header fields, both are known
    at write time, and they would file naturally as one issue.

**Cost to us if unfixed** the analyser cannot detect a merged multi-logger file, so it cannot warn;
it just interleaves and produces a plausible, wrong picture. Detection is the whole ask — the analyser
does not need to *handle* merged files, only to stop pretending they are single-source.

### UP-FLX-27 ☐ Let a logged key carry its unit and meaning

**Target** `fluxtion` (runtime, `EventLogger` API) · **Priority** medium · *audit-log twin of UP-FLX-22*

UP-FLX-22 asks for reference-kind and dirty-contract on the **GraphML edges**, from the round-2 finding
that `StockLedger.lastQty()` means *shelf level after the movement* and was read as *units just sold*.
The same ambiguity exists on the **value** side of the log and is now more expensive, because M28 lets a
formula compute over that number (`mean(stockLedger.lastQty, 10)`) and M29 will plot it against an
external series. A window over a misunderstood quantity is a confident wrong answer.

**REVISED 2026-08-17 (owner challenge: "how would the extra key information be added? on every
call? I don't see how it works").** The challenge is correct — the first draft put the metadata on
the logging CALL, which is the wrong transport: a key's unit and meaning are **static facts about
the key**, not about the observation, so carrying them per call would repeat an unchanging string on
every record (log bloat proportional to record count for zero information gain) and would let two
call sites disagree about what the same key means. **Per-call carriage is rejected.** The metadata
must be **declared once**. Owner's chosen shape (2026-08-17): the runtime transport, as

  ```java
  auditLog.keyMap("lastQty", "units — shelf level AFTER the movement");   // in @Initialise
  ```

**with a freeze-at-init semantic: `keyMap` calls are LIVE only during the lifecycle phase
(@Initialise/@Start); once event processing begins, further calls are ignored (no-ops).** That one
rule is what makes the transport sound end to end:

- the dictionary is **complete before record 1**, so the `EventLogger` can emit it as a single
  `keyMap:` block at the head of the file — parsers read it once, no mid-file scanning;
- a key's meaning is **constant for the run** — no call site can redefine `lastQty` between record
  100 and 101, so a tooltip, legend or LLM prompt quoting the description is quoting something that
  held for every record it describes;
- log rolling re-emits the header block at the top of **each rolled file**, so every file of a set is
  self-describing on its own (exactly what M30's rolled-set loader wants — no reaching back to file
  1 for meanings);
- ignored post-init calls are cheap to make debuggable (one debug-level "keyMap after init ignored"
  line) without ever affecting the log's content.

Covers runtime-computed key names and log-only sessions by construction; costs one header line per
described key per file, never per record.

*Complement, not the ask:* the same dictionary could ALSO be emitted compile-time into the GraphML
node payload from an annotation (a vertex-shaped fact riding §2c's verified additive mechanism) —
useful for graphml-first tooling, but secondary: it cannot cover dynamic keys, and the log-side
declaration wins on any disagreement (it is closer to what actually executed).

Surfaced by the analyser in the key picker, the series legend, marker/point tooltips (M32) and the
LLM prompt — the places where a plausible name and a bare number currently reproduce the original
defect.

**Cost to us if unfixed** the analyser presents `stockLedger.lastQty` as a bare label and an LLM
diagnosing from the chart has exactly the information that produced the original defect: a plausible
name and a number. The javadoc that would disambiguate it exists in source the log never references —
and in the round-2 case that getter was the only one of three with no javadoc at all.

---

## 2c · GraphML — richer edges and nodes, backward compatibly

Owner proposal, 2026-08-17: *"add backward compatible attributes to the graphml edge definitions such
as push edge. Maybe data only nodes."* The asks below answer that and the follow-up *"what else would
be useful?"*.

### MEASURED — a probe graph compiled 2026-08-17

Rather than reason about the emitter, a throwaway processor was compiled (`Fluxtion.compile(...)` with
`generateDescription(true)`, fluxtion-bom 1.0.64) containing one of each construct: a plain handler, a
`filterString="ACME"` handler, a `FilterType.defaultCase` handler, an `@OnEventHandler(propagate=false)`
handler, a node with **one trigger parent and one `@NoTriggerReference` parent**, an
`@ExportService(propagate=false)` service with a `@NoPropagateFunction` method, and a DataFlow
`.push(pushTarget::setPushed)`.

**Result: the emitted GraphML declares exactly two keys — `vertex_label` (node) and `edge_label`
(edge) — and EVERY edge in the file is identical but for `id`, `source` and `target`.** The
`edge_label` payload is `<jGraph:ShapeEdge/>`: pure rendering, zero semantics. Node payload is a
`<jGraph:label text="…">` holding `id:`/`class:` plus a stereotype, and `<jGraph:Style properties="…">`
holding the kind.

So these four edges are byte-identical in structure, though no two mean the same thing:

```
<edge id="17" source="PriceEvent" target="rawFeed">        always fires
<edge id="19" source="PriceEvent" target="acmeFeed">       only when filterString == "ACME"
<edge id="20" source="PriceEvent" target="fallbackFeed">   only when NOTHING else matched
<edge id="21" source="PriceEvent" target="quietFeed">      fires, but never propagates
```

as are these two, which are the trigger/data distinction itself:

```
<edge id="2" source="rawFeed" target="consumer">           TRIGGER parent
<edge id="3" source="dataBag" target="consumer">           @NoTriggerReference DATA parent
```

**And `.push()` is worse than unmarked — it is currently unrenderable.** The push materialises as a
chain of three framework nodes:

```
rawFeed → nodeToFlowFunction_8 → mapRef2RefFlowFunction_9 → pushFlowFunction_10 → pushTarget
```

all three of class `com.telamin.fluxtion.runtime.flowfunction.function.*`, which matches the analyser's
framework-package prefix. Running the real parser and `Scaffolding.authoredNodes` over the probe
(21 nodes → 10 authored) gives:

- **Scaffolding hidden (the DEFAULT):** all four push edges are dropped, because each touches a
  framework node. `pushTarget` survives as an authored node with **zero edges** — a disconnected box on
  the canvas, and the `rawFeed → pushTarget` relationship is *completely invisible*.
- **Scaffolding shown:** the relationship appears as four ordinary edges through three plumbing nodes,
  implying a propagating dependency chain — when the whole point of `.push()` is that downstream does
  not see the effect.

Neither view is correct, and the analyser has no way to produce a correct one from this file. That is
the concrete cost behind UP-FLX-28 and UP-FLX-29 together: the marking (`propagates`) and the
identification (`framework="true"`) are both needed, because with only the second, the push chain is
hidden and the relationship vanishes.

### The backward-compatibility contract — VERIFIED, with one constraint

**Measured against `topology/GraphmlParser.java`, 2026-08-17.** The analyser's reader takes `id` from
each `<node>`, `source`/`target`/`id` from each `<edge>`, and then hunts descendants for two attribute
*names*: `text` (the label) and `properties` (the style, from which node `Kind` is inferred). It
validates nothing else and there is no key whitelist — so **new `<key>` declarations and new `<data>`
children are invisible to today's build and cannot break it**. Backward compatibility is real.

**The one constraint:** `firstDescendantAttribute` returns the first non-empty value of the named
attribute on **any** descendant. A new element that carries an attribute literally called `text` or
`properties` on a node's subtree could therefore be picked up as that node's label or style. New data
must not reuse those two attribute names. Everything else is free.

*(Aside: node `Kind` being sniffed from a style string is itself a weakness. An explicit
`kind="EVENT|HANDLER|NODE|EXPORT_SERVICE"` data key would retire the sniffing — cheap, and covered by
the same additive mechanism.)*

### UP-FLX-28 ☐ Edge attributes: `propagates` and `refKind`

**Target** `fluxtion` (compiler, graphml emission) · **Priority** high · *implements the owner's "push
edge", extends UP-FLX-22*

Two **independent** facts, and the distinction matters — they are not the same attribute:

- **`refKind = trigger | data`** — does this parent *fire* the child, or is it only read? Already known
  to the compiler (`@NoTriggerReference`); this is UP-FLX-22's edge half. Measured in POC round 2,
  where an injected data reference silently became a trigger (`PO,null,0,unknown`).
- **`propagates = true | false`** — the **push edge**. Verified against the framework reference
  (`docs/claude.txt`): *"`.push()` is for side effects observable outside the graph (logging, sinks,
  metrics)"*, and of sinks, *"they fire externally to the graph dispatch, so **downstream nodes won't
  see the effect**"*. So a push edge carries data but **not propagation** — the opposite direction of
  the data-reference case, which carries propagation-relevance but no trigger.

**Verified against the runtime source, 2026-08-17** (`fluxtion-runtime/.../runtime/annotations/`):
non-propagation is already declarable at **four** granularities, none of which reach the GraphML:

| carrier | level | wording |
|---|---|---|
| `@ExportService(propagate = false)` | service (type-use) | *"permanently remove the event handler method from the execution path"* |
| `@NoPropagateFunction` | exported method | *"Marks an exported function as non propagating"* |
| `@OnEventHandler(propagate = false)` | handler method | overrides the handler's boolean return |
| `FilterType.matched` | edge condition | *"Only matching filters allow event propagation"* |

So this ask is "emit what you already know", not "invent a concept".

**On "data only nodes" — the name is already taken, and means something else.** `@FluxtionDataOnly`
exists, and it is a **lint-suppression marker**: *"The annotated type is intentionally not a Fluxtion
event-handling node — it is a data class, DTO, event type, helper or launcher"*, and explicitly *"has
no effect on Fluxtion source-generation, dispatch or runtime behaviour"*. It marks classes that are
**not in the graph at all** — the opposite of a graph vertex that carries data without triggering.
Emitting a GraphML node flag called "data only" would therefore collide head-on with an existing
annotation of the same name and the opposite meaning.

Independently of the name: the edge attribute is the primitive and a node classification is derivable
from it — a node whose every outgoing edge is `refKind="data"` is data-only. Deriving it cannot
disagree with the edges, which a separately-emitted node flag eventually would. **Recommend edges
only**, and if a node-level convenience is ever wanted, do not call it data-only.

**Cost to us if unfixed** the analyser draws every edge identically, so it will present a push target
as though it participates in propagation — a *wrong* picture, not merely an imprecise one, in exactly
the "did this node run because of that one?" question the topology tab exists to answer.

### UP-FLX-29 ☐ Mark framework-generated nodes as such

**Target** `fluxtion` (compiler, graphml emission) · **Priority** high — cheapest big win here

**Measured, 2026-08-17.** `topology/Scaffolding.java` classifies framework plumbing with three
heuristics over *names*: a hardcoded id list (`FRAMEWORK_EVENT_IDS`), framework package prefixes, and a
hardcoded simple-name list (`SinkRegistration`, `SinkDeregister`, `LifecycleEvent`, …). That is this
repo guessing at what the compiler generated, and it is wrong in both directions — a user class in a
matching package is hidden, a new framework type is shown until we notice and add its name.

Ask: `framework="true"` (or `role="scaffold"`) on every node the compiler generates.

The probe above shows the second half of the cost: name-matching happens to *succeed* on the DataFlow
plumbing (`…runtime.flowfunction.function.*` matches a package prefix), and succeeding is what makes
`pushTarget` an orphan. Identification alone is not enough — it must arrive with UP-FLX-28's `propagates`,
or hiding the plumbing silently deletes a real relationship.

**Cost to us if unfixed** the **Scaffolding** checkbox — which hides 10 of 20 nodes in the demo graph,
half the picture — is driven by a name list that must be maintained in this repo, in lockstep with a
framework it does not ship with. Every new framework node type is a silent defect here until someone
spots it.

### UP-FLX-30 ☐ A build fingerprint in the GraphML itself

**Target** `fluxtion` (compiler, graphml emission) · **Priority** medium · *pairs with UP-FLX-12*

UP-FLX-12 asks the runtime to populate `ProcessorDescriptor.Meta.sourceFingerprint()`. The graphml
side of the same fact would let the analyser answer *"is this graph the build that produced this log?"*
from **the two files alone**, with no running processor — which is the normal forensic case: someone
sends you a log and a graphml.

Ask: graph-level `<data>` for a build fingerprint, generator version and build timestamp.

**Cost to us if unfixed** the analyser answers the question by counting unmatched instance ids — a
heuristic that cannot distinguish "wrong build" from "this cycle simply didn't touch those nodes".

### UP-FLX-31 ☐ Filtered handler edges — the filter value and the match strategy

**Target** `fluxtion` (compiler, graphml emission) · **Priority** high

**Verified against the runtime source, 2026-08-17.** `annotations/FilterType.java` is documented as the
*"Filter match strategy for an `@OnEventHandler`"* with two values: **`matched`** — *"Only matching
filters allow event propagation"* — and **`defaultCase`** — *"Invoked when no filter match is found,
acts like a default branch in a case statement."* `annotations/FilterId.java` *"Marks a field as a
default filter for all event handlers in that class"* (field-level, so a per-class default).

So an event→handler edge is frequently **conditional**, and some edges are *default branches* that fire
only when every other edge did not. **Confirmed by the probe above**: the unfiltered, `"ACME"`-filtered,
`defaultCase` and `propagate=false` handler edges are byte-identical in the emitted file. The analyser
draws all four as though they always fire.

Ask: on the edge, the filter value and its `FilterType`; on the node, the class-level `@FilterId`
default when one is declared.

**Cost to us if unfixed** the topology cannot answer *"why didn't this handler run?"* — the commonest
question after *"why did it?"* — for any filtered graph. Worse, the answer it currently implies is
wrong: an unconditional arrow that did not fire reads as a defect in the processor rather than a filter
working correctly. A `defaultCase` edge is misleading in the opposite direction, appearing to be an
ordinary path when it is a fallback.

*Process note: this ask was drafted, then dropped, on 2026-08-17 because the framework reference
(`docs/claude.txt`) does not mention filters and this file does not accept asks built on inference. That
was the wrong call — absence from a curated LLM-facing reference is not absence from the framework, and
the rule forbids **inferring**, not **checking**. Reinstated after reading the annotation sources
directly, which is what should have happened first.*

### Where these properties belong — edges vs vertices

Owner question, 2026-08-17: *"a vertex may have a few properties: Filter, Reference, Propagation,
Push. Maybe more?"* Three of those four are **edge** facts, and putting them on the vertex would lose
information:

| fact | carrier | why |
|---|---|---|
| **Reference** (trigger \| data) | **edge** | the same node is commonly triggered by one parent and merely read by another — one value per vertex cannot express that |
| **Propagation** (does the effect continue downstream) | **edge** | `@NoPropagateFunction` is per exported *method*; `FilterType.matched` gates propagation per handler |
| **Push** (data flows against the dependency arrow) | **edge** | a property of the relationship, not of either end |
| **Filter** value + match strategy | **edge** | one handler node may take event `E` under several filter values via different edges |
| **Filter** *default* (`@FilterId`) | **vertex** | genuinely class-level — the default for all handlers in that class |

`Push` and `Propagation` are related but not the same axis: a push edge is non-propagating **and**
reverses the data direction, whereas `@NoPropagateFunction` is non-propagating without reversing
anything. Modelling them as `propagates` plus a direction flag keeps them orthogonal; collapsing them
into one attribute would make "push" unrecoverable. *(Worth the owner confirming how a `.push()` target
is emitted today — whether it appears as an edge at all is not something this repo can verify.)*

The genuinely **vertex**-shaped properties are a different list: `kind` (retiring the style-string
sniffing), `framework` (UP-FLX-29), declared lifecycle callbacks (UP-FLX-14 — note `@AfterTrigger`
exists alongside `@AfterEvent`, `@Initialise`, `@Start`), the `@FilterId` default, the exported
interface and its method signatures with per-method `noPropagate`, the dirty contract (UP-FLX-22), and
optionally source location and dispatch-order index.

### Also worth considering (not yet asks — no measured need in this repo)

- **Dispatch order index per node.** The analyser lays out by layers it recomputes; the compiler knows
  the real invocation order. Would make step-through ordering authoritative rather than reconstructed.
- **Declaring source location** (file + line) per node, so source navigation is exact rather than an
  FQN lookup — and can say *"generated, no source"* honestly instead of failing to find one.
- **Exported-service interface and method signatures** per node — pairs with UP-FLX-13's "one spelling
  for exported-call signatures"; the analyser currently matches exported calls to nodes by name.

---

## 3 · svc-admin-web — what it could take from the analyser

Reviewed 2026-08-16: `visualiser/scaffold-filter.js`, `replay/replay-engine.js`,
`replay/eventlog-parser.js`, `visualiser/graph-parser.js`. These flow *from* this repo rather than to it.

### UP-WEB-01 ☐ Execution classification, not just "active nodes"

**Priority** high — this is the substantive one

`replay-engine.js` highlights the nodes present in `nodeLogs` and nothing else, so a node that **ran and
logged nothing** is drawn exactly like a node that **did not run**. Under a sparse audit level that is
most of the graph. The analyser's `ProcessorTopology.classifyCycle` distinguishes LOGGED /
RAN_SILENTLY / MAY_HAVE_RUN / OFF_PATH / DID_NOT_RUN, gated on whether the record traces every
invocation, and draws MAY_HAVE_RUN **dashed** so the picture never states more than the log does.

This was the correction that produced the `fix/m21-execution-vs-logging` branch here; the same
misreading is live in the web UI.

### UP-WEB-02 ☐ An entry position, and regime-aware wording

**Priority** medium

`replay-engine.js` starts at `stepIndex = 0` — the first row. The analyser's `StepCursor` has an explicit
**ENTRY** position before the first row, which is where the entry point is marked before any node
highlights, and `prev()` from an entry lands on the *previous record's last row* so stepping reads the
same in both directions. Its labels also say which regime is in force: `row 3 / 8 (logged nodes)` versus
`invocation 3 / 16`. `step 2/5` alone invites reading 5 as "the nodes that ran".

### UP-WEB-03 ☐ Widen the scaffolding filter

**Priority** low

`SCAFFOLD_CLASS_NAMES` matches by class name only, so framework **EVENT** nodes (`ClockStrategyEvent`,
`EventLogControlEvent`, `SinkRegistration`, …) stay visible — they usually carry no `class` attribute at
all. The analyser's `Scaffolding` also matches by package prefix and by node id, hiding 10 of 20 in the
demo graph against roughly 7 for the JS filter.

### UP-WEB-04 ☐ Re-dispatch (see UP-FLX-10)

Neither svc-admin-web nor fluxtion-visualiser handles re-dispatch today. If UP-FLX-10 lands, both UIs get
the causal link for free; if it does not, both will keep showing internally-raised events as though they
arrived from outside.

---

## 4 · Shared assets

### UP-SHARED-01 ◐ A published fixture pack

_Filed: https://github.com/telaminai/fluxtion/issues/12_

**Target** `fluxtion` · **Priority** medium

`examples/fixture-generator/` in this repo produces a byte-reproducible, paired set: a compiler-emitted
`.graphml` plus audit logs from running **that** processor, at two audit levels, now including an exported
service and a re-dispatch. Every consumer of audit logs needs exactly this and they should not each invent
one — a hand-written fixture drifts from the log it claims to describe and still renders perfectly.

Ask: publish it (or its equivalent) as a small artefact from the fluxtion repo so svc-admin-web, the
visualiser and the analyser test against identical bytes.

### UP-SHARED-02 ◐ Error-code doc anchors

_Filed: https://github.com/telaminai/fluxtion/issues/8_

**Target** `fluxtion` docs · **Priority** medium — pairs with UP-FLX-01

Every rejection code should carry a stable URL (`fluxtion.dev/errors/FLX-1001`). That is what makes the
semantics in `claude.txt` reachable **at the moment of failure**, rather than requiring an agent to have
read them up front — which is precisely where inference creeps in.

---

## 5 · Mongoose — the agent-brokered dev loop

_Raised 2026-08-25 from [`spec-agent-brokered-dev-loop.md`](../specs/spec-agent-brokered-dev-loop.md)
(ACCEPTED v2). M18 — the analyser linking to a server itself — is **closed**; the adopted design puts
every server-side capability in **Mongoose's own repo**, reached by an agent over MCP, and the
analyser opens files. These four asks are that design's server half._

**THE GATE IS MET — these are ready to file (2026-08-26).** This section previously read "none of them
starts until the §H conformance harness has a home in the M19 bench". That harness shipped:
`tools/bench/loop-bench.py` (M19.6, merged 2026-08-25) plays §C3 steps 3–7 and asserts every one by
name, and `tools/bench/mongoose-stub.py` implements UP-MNG-01 and the export half of UP-MNG-02 **and
nothing else**. So the mongoose-side work has an executable definition of done that exists today:
*make the real server pass what the stub passes.* Pointed at a real `~/.mongoose/servers/`,
`loop-bench.py --registry <dir> --server <name>` **is** the acceptance test for UP-MNG-01/02 — no new
harness, no new agreement about what "done" means.

**These are no longer speculative.** The loop described here is in **daily use debugging a live trading
application**: an agent starts, stops and deploys across environments, pulls logs from remote sites,
drives the analyser, and a fix is proved by re-running and re-reading the record — hours of work
becoming minutes. A **support team** uses the same path to answer questions about a deployed system
they did not build, and reports the same collapse in time-to-answer. Everything below is already being
done; what is missing is that the server half is glued together with bespoke scripting per site
instead of being a capability of the server. That is the gap these asks close, and it is why the
priorities below are ordered by *what the humans are currently working around*.

_**Overtaken for two of the four (2026-08-29):** UP-MNG-01 and UP-MNG-03's declaration half were
implemented and RELEASED in `mongoose-plugins` 1.0.39 without ever being filed as issues — the M19
bundle needed them, so they were built and proved against a real server instead. UP-MNG-02 and
UP-MNG-04 are still unfiled and still owner decisions._

_Still **NOT FILED** as issues in `telaminai/mongoose`. The nine fluxtion-owned asks were filed as
issues 8–16 and moved; these have a spec, a bench and now production evidence, and no issue._

_Target repo for all four: `telaminai/mongoose` (the server runtime and its admin web service plugin —
the owner decides which module). Evidence throughout is **measured**: the `svc-admin-web` capability
check recorded in `spec-closed-loop.md` §B.2, the live playground read of 2026-08-21 (spec §C2), and
the analyser's own endpoint-file mechanism, in daily use by every script in `tools/`._

### UP-MNG-01 ☑ Each running server publishes an endpoint file — `~/.mongoose/servers/<name>`

**LANDED AND RELEASED — `mongoose-plugins` 1.0.39 (2026-08-29).** Implemented in `svc-admin-web`
(`ServerRegistryFile` + `WebAdminService` wiring), merged to `release`, released, and verified public:
the released jar carries `ServerRegistryFile` and the `publishRegistry` / `serverName` / `environment`
/ `registryDir` config. Config knobs also include a JVM-wide `mongoose.servers.dir` override so tests
never touch a developer's real `~/.mongoose`. One deviation from the ask as written, decided by the
implementation: removal hooks `Service.stop()`, **not** `tearDown()` — a live run proved Mongoose's
clean shutdown calls only `stop()`, so removal in `tearDown()` would never have run. A crash still
leaves a dead-pid entry, as specified.

**Verified end to end**, not just unit-tested: the analyser's `loop-bench.py` passes every
registry-side step against a real server, and the M19 P3 clean-machine run drove a generated bundle
through publish → export → clean stop with the entry removed. 96 module tests green.

**Target** `mongoose` (runtime or admin web service) · **Priority** highest — this is the discovery
glob; without it every other leg of the loop needs hand configuration, which is the M18 shape again

**The ask.** On startup, when the admin web service is enabled, write one file per server, mode 600:

```
~/.mongoose/servers/<name>
{
  "name":        "risk-engine",
  "home":        "/Users/dev/work/risk-engine",
  "url":         "http://127.0.0.1:8081",
  "token":       "…",
  "authMode":    "TOKEN",
  "environment": "dev",                                   ← UP-MNG-03
  "pid":         41221,
  "startedAt":   "2026-08-21T09:14:02Z",
  "processors":  [{"group":"main","name":"RiskProcessor",
                   "graphml":"/api/processors/main/RiskProcessor/graphml"}]
}
```

Remove it on clean shutdown; on a crash it stays, and `pid` is how a reader tells a stale file from a
live one — document that, do not build a cleaner. `processors[].graphml` is the **runtime** home of the
"where is this server's GraphML" fact (the catalogue field that was withdrawn in favour of this, §C2).

**Evidence.** The analyser publishes `~/.fluxtion-analyser/rest-endpoint` (`{url, token, pid,
startedAt}`, mode 600 via `PosixFilePermissions`) and every capture and drive script in `tools/` finds
the app that way with zero configuration — this is the same mechanism, one directory over. The
alternatives were rejected in the spec: a registry *inside the analyser* re-acquires the coupling M18
was closed to remove; MCP client config is static and cannot absorb a server deployed mid-session.

**Concrete consumer, 2026-08-28.** The local `mongoose-hosted-fluxtion` starter validation (M19.1a)
needs this file before it can run the existing `loop-bench.py` against a real server. A project-local
skill may read the registry and fall back to its YAML for one-project inspection, but it must not create
or repair it; that fallback is not conformance evidence. This ask therefore survives regardless of the
separate UP-MNG-02 decision.

**Trust model, stated because these tokens gate restarts, not renders (spec review F5).** Mode 600,
readable by any process running as the user — the same posture as the analyser's file. It is only
sufficient because UP-MNG-03 puts the real refusal server-side: a non-dev deployment declines admin
control regardless of who holds the file.

**Acceptance.** A server generated from a `mongoose` starter and run with its `addRunScript` writes
the file before the admin port answers; two servers → two files; `kill -9` → a file whose `pid` is
dead; the §H harness reads it at step 3–4 with no configuration. **Cost to us if unfixed:** step 4 of
the loop is a human typing a URL, and the "deploy a second server, it just appears" property is gone.

**Measured cost, 2026-08-26.** Teams already run this loop; without the file, each site hand-maintains
its own answer to "which servers are running and where" — the coupling M18 was closed to remove,
rebuilt per site in shell. The first thing support does with an unfamiliar system is find its log, and
that is exactly the step no one can automate portably today.

### UP-MNG-02 ☐ An MCP admin tool, in the Mongoose repo — audit level, export, restart

**Target** `mongoose` (a new module, or the admin web service plugin) · **Priority** high — it is the
deploy leg (§C3 step 9) and the moved M18.3/18.4. **`mongoose_export_audit` is the single most valuable
tool here**: it is the first move of every support investigation and every agent-driven diagnosis, and
it is the one currently done by hand

**Disposition is independent of UP-MNG-01 (recorded 2026-08-28).** Local scripts/skills may prove a
project-local start/stop workflow, but they neither remove the need for registry discovery nor decide
whether the Mongoose owner should provide MCP export/restart/audit-level tools. Before filing or
withdrawing this ask, record an explicit **retain / defer / withdraw** owner decision and the evidence
behind it. M19.1a's optional analyser-client MCP comparison is not evidence either way.

**The ask.** An MCP server (stdio, like the analyser's bridge) whose tools address a server by the
`name` in its registry file (UP-MNG-01), each mutation approved per call by the MCP client — the
transport's own permission model, not a hand-built dialog (D-B3):

| tool | does | notes |
|---|---|---|
| `mongoose_servers` | lists the registry — name, url, environment, pid liveness, processors | read-only |
| `mongoose_audit_level {server, level}` | sets the audit level **and returns the previous one** | today `EventLogControlEvent` is not a REST endpoint and has **no GET companion** (M18.3a) — the tool needs a read-back to record a baseline and to restore it. Capture-and-restore was M18.3's whole point |
| `mongoose_export_audit {server, format, path}` | writes the audit log to a file the analyser can open | `format: yaml` today; the analyser then does `open {log, provenance}` with the server's `name` as provenance (§E) |
| `mongoose_restart {server}` | dev restart | **its description must say, in its own words, "export the audit log first — a restart can roll or truncate the evidence" (D-B8)**, because the agent reads the tool, not the spec. `destructiveHint: true` |

Every mutation is **journaled by the server** to its own log (D-B5, free tier): who, what, when,
from which tool. A chat transcript is not an audit trail.

**Evidence.** The `svc-admin-web` check (spec-closed-loop §B.2) found three gaps a client cannot
paper over: no lifecycle endpoint (stop/start commented out), audit level reachable only through a
registered command with no read-back, and no audit-sink discovery (UP-MNG-04). The analyser's MCP
bridge (`McpTools`, 14 verbs, destructive hints on `open`/`source_root`/`screenshot`/`report`) is the
in-house precedent for the shape.

**Acceptance.** From a fresh Claude Code session with both MCP entries configured: `mongoose_servers`
→ the server; `mongoose_audit_level` up, investigate, restore → the server's log shows three
journal lines; `mongoose_restart` prompts in the client and its description mentions export-first;
the analyser's Follow picks up the fresh log. **Cost to us if unfixed:** the flagship cycle's deploy
leg does not exist and M18.3/18.4 stay deleted rather than moved.

### UP-MNG-03 ◐ The server declares whether it is a dev instance

**DECLARATION HALF LANDED — `mongoose-plugins` 1.0.39 (2026-08-29).** `WebAdminService` takes a
declared `environment` (default `dev`) and carries it into the UP-MNG-01 registry file, so an
exporting agent has an authoritative value instead of a hand-typed one — which is the correctness
half of this ask. The M19 bundle sets it explicitly, and `bundle-bench` asserts it.

**STILL OPEN: the ENFORCEMENT half.** Nothing yet refuses admin control outside `dev`. That is
deliberate: server-side refusal presupposes the UP-MNG-02 admin surface, whose retain/defer/withdraw
is still an unmade owner decision (D-05). Until it is made, `environment` is a declared fact only —
useful for provenance, not a licence boundary. Do not read the landed half as the paid line being
enforceable.

**Target** `mongoose` (server config) · **Priority** high — two independent reasons now: it makes the
free/paid line enforceable (D-B7), **and it is a correctness requirement for anyone answering questions
across environments**

**The ask.** A declared `environment` in the server's own configuration (`dev` | `prod` — the owner
names the values), carried into the endpoint file (UP-MNG-01) and honoured by the admin surface:
**outside `dev`, admin control (level changes, restart) is refused without a licence token — by the
server.** A licence check inside a readable client is an `if` anyone can delete; a server that
refuses has nothing to patch on the customer's side.

**Why declared and not inferred.** Non-loopback is the obvious signal and is too crude — a developer
on a remote box is not production. Declared-never-inferred is the rule this codebase applies to
graphs (D-A2), order (D-A1a) and provenance (§E); the environment is the same kind of fact.

**The correctness half, added 2026-08-26 from production use.** Support answer questions about live
systems from exported logs, across several environments. **Two environments running the same build
emit logs that are identical in shape and usually identical in filename** — the only thing that can
separate them is something a human or a script *declared* at export time. The analyser already carries
this the whole way (§E provenance in the status bar, report headers, and a mismatch banner that can say
*"same content — a different system"*), but it can only report what it was told. With `environment` in
the endpoint file, the exporting agent has an authoritative value to pass instead of a hand-typed one,
and it comes from the server rather than from whoever wrote the script.

Without it the failure is silent and expensive in exactly the way this project cares about: an answer
that is correct about UAT, read as production. Nothing errors, the log looks right, and the report
names a filename. This is the same class as the producer diagnostics the analyser added on 2026-08-25 —
a wrong answer with no symptom — and unlike those, the analyser cannot detect it, because both logs
are perfectly well-formed.

**Acceptance.** A starter-generated server declares `dev` by default; flipping it to `prod` makes
`mongoose_restart` return a refusal that names the reason; the free dev MCP pointed at a `prod` server
gets the same refusal. **Cost to us if unfixed:** the free dev MCP can be pointed at production and
the paid line evaporates — or the line is drawn client-side, where it cannot hold.

### UP-MNG-04 ☐ Describe the audit sink — its type and location — instead of assuming a file

**Target** `mongoose` (admin web service) · **Priority** medium — the export path (UP-MNG-02) covers
today's need; this is what lets UP-RDR-01 delete the export beat

**The ask.** `GET /api/audit/sink` (or a field in the endpoint file) → `{"type": "file" | "chronicle" |
"kafka" | "jdbc", "location": "…"}`. The audit writer is pluggable and **typed** (the `LogRecordListener`
seam): a file sink has a path a reader can open; a Chronicle sink has a directory; kafka and jdbc have
neither. Discovery that assumes a file path is wrong for three of the four.

**Evidence.** spec-closed-loop §B.2: "no audit-sink endpoint … the sink is pluggable and typed";
the `mongoose` starters already write `auditBackend: "chronicle"` (live read, spec §C2).

**Acceptance.** A chronicle-backed server answers `{type: chronicle, location: ./audit}`; a reader
that cannot open that type says so rather than failing on a path. **Cost to us if unfixed:** the
Chronicle reader (UP-RDR-01) has to guess where the store is.

---

## 6 · Playground — two catalogue fields, and four that were withdrawn

_The catalogue at <https://fluxtion-playground.dev/starter-templates/index.json> already exists and
is already agent-readable; spec §C2 was corrected twice by reading it. **Recorded here so nobody
re-raises the four that were withdrawn**: `mongoose.adminRest` (encoded by `type: mongoose` + the
`web-admin` tag + the generated services block), `analyser.sourceRoot` (already
`adminWebService.config.sourceRoots`), `run` (`addRunScript: true`), `analyser.graphml` (a runtime
fact — moved into UP-MNG-01's `processors[].graphml`). Two stand._

### UP-PG-01 ☑ `catalogue: 1` — a version integer on the index

**LANDED (2026-08-29).** `static/starter-templates/index.json` carries top-level `"catalogue": 1`,
pinned by a test in `templates-disk.test.ts`. The gallery loader already accepted the object form and
ignores unknown keys, so the change is additive for every existing consumer.

**Target** `fluxtion-playground` (starter-templates) · **Priority** high — D-B4 governs additive
evolution *of a field that does not exist*

**The ask.** Add a top-level `"catalogue": 1` to `index.json`. Fields may then be added within a
version; none removed or retyped; a breaking change increments the integer and the old file stays
served. **Evidence (live, 2026-08-21):** the only top-level key today is `templates`. Agents parse
this file — the playground's own `/build-with-ai` says so — and an unversioned schema read by agents
is rule 6 pointed outward: we would be the party shipping the breaking revision. **Cost to us if
unfixed:** D-B4 is a rule about nothing.

### UP-PG-02 ☐ `agentBootstrap` — where the generated project's agent instructions live

**The CAPABILITY now exists; the CATALOGUE FIELD does not (2026-08-29).** M19 bundles ship
`CLAUDE.md` + an `AGENTS.md` mirror, generated from the single bundle model and declaring the
contract version, the keyless-run and key-regeneration rules and the resolved MCP route. So the
thing the field would advertise is real. What is still missing is the advertisement: nothing in
`index.json` tells an agent, BEFORE generating, whether a template's project will ship agent
instructions. That is the ask, and it is unchanged — only now it is a one-line addition describing
shipped behaviour rather than a request for behaviour that does not exist.

**Target** `fluxtion-playground` (starter-templates + `/start`) · **Priority** medium

**The ask.** One field per template naming the `CLAUDE.md` (+ `AGENTS.md` mirror) stack the generated
project ships — the layered bootstrap M19.1 specifies (`spec-onboarding-example.md`: thin
example-specific file at generation, over the maintained
<https://fluxtion-playground.dev/CLAUDE.md>). It is the one M19.1 need with no equivalent in the
live catalogue. **Cost to us if unfixed:** step 1 of the loop cannot tell an agent whether the project
it is about to generate will know Fluxtion.

### UP-PG-03 ☑ Fetch a template by id, and two catalogue facts a picker needs

**ALL THREE LANDED AND RELEASED (2026-08-30)**, `fluxtion-web` `994e82a`, verified against the
deployed site: `GET /start/scaffold?template=<id>[&artifact=&group=&basePackage=]` returns the real
zip, the catalogue carries `tags: ["onboarding"]` and `keyNeed: "none"`, and (c) turned out to be a
**live bug** rather than a latent one — the gallery was badging `analyser-bundle` "Key once at build"
when it needs no key to build. Contract as shipped:
[handoff_30_aug_2026_1.txt](../handoff/handoff_30_aug_2026_1.txt).

**Raised 2026-08-30**, from the owner's ask that the analyser let you *choose a template inside the
app* ([spec-template-from-analyser.md](../specs/spec-template-from-analyser.md), M19.5). Three parts,
smallest first; all additive under `catalogue: 1`.

**Target** `fluxtion-playground` (`/start/scaffold` + starter-templates) · **Priority** medium

**(a) `GET /start/scaffold?template=<file>[&artifact=&group=&basePackage=]`.** The endpoint already
generates a real zip server-side and is already Worker-safe — but its only input is an lz-string spec
token from the `/start` page's "Copy curl" button. A Java client cannot reasonably produce one, and
reimplementing lz-string to talk to our own service would be absurd. Resolving a named template from
the directory the catalogue already indexes is ~20 lines in a 38-line file, reusing `validate()` and
`buildStarterZip()` unchanged. The optional overrides preserve the factoring
`spec-agent-brokered-dev-loop.md` §C2 established — *the template is a shape, the project is generated
to the user's names* — and stop this degrading into a fixed zip. Unknown template → 404 naming the
catalogue URL; both `s` and `template` → 400. **Cost to us if unfixed:** the analyser cannot acquire a
project at all, and M19.5 stops at the hop the owner actually cares about.

**(b) `tags: ["onboarding"]` on the templates worth showing a support engineer.** The catalogue's 14
entries are written for the playground gallery — someone learning to *build*. The analyser's audience
is someone diagnosing a system that already exists, and "Fluxtion DataFlow DSL" is noise to them. The
alternative is an allowlist inside the analyser, a second source of truth that drifts on the first
rename — the exact failure §C2 spent two corrections avoiding. `tags` already exists and is already
optional (3 entries carry it), so this adds a value, not a key. **Cost to us if unfixed:** the picker
either shows everything or drifts.

**(c) Separate "builds keylessly" from `mode`.** §C2 states the rule *"`interpreted` is keyless…
anything else is AOT and needs a subscribed compiler key."* True when written; **now false for exactly
one template.** `analyser-bundle` is `mode: aot` **and builds with no key**, because M19 commits the
generated processor and moves the `fluxtion-maven-plugin` scan behind `-Pgenerate-fluxtion`. A picker
deriving a "needs an API key" warning from `mode` would therefore show a **wrong warning on the one
template the tutorial recommends** — the first thing a new user sees. Building and regenerating are now
different facts and `mode` cleanly carries neither. **Cost to us if unfixed:** either the picker says
nothing about keys (today's workaround, spec §D-2) or it says something false at the front door.

---

## 7 · Out-of-tree readers on the shipped SPI

### UP-RDR-01 ☐ A Chronicle audit reader — open `./audit` directly, and delete the export beat

**Target** wherever Chronicle already is a dependency — `mongoose` or `mongoose-plugins` (not this
repo: it must not add Chronicle to the analyser's classpath; M31 D-P3 is the plugin directory for
exactly this) · **Priority** high — the spec calls it "the sleeper": it makes the analyse leg *live*

**The ask.** An `AuditLogReader` plugin: `formatId "chronicle"`, `canOpen` by the store directory,
`capabilities {follow: true, byteAnchors: false, randomAccess: …, ordering: TOTAL}` (a Fluxtion
processor's order is compiler-derived), `timeBase` declared from Chronicle's clock — never sniffed
(review X2), `graph(source)` optional (the registry's `processors[].graphml` is the better source).
Records are handed over as canonical text; **it passes the M34.3 conformance suite**
(`docs/site/format-spec.md`, `src/test/resources/conformance/`) — that suite is the contract, and
"passes it" is what conformant means.

**What it buys.** §C3 step 5 (`export?format=yaml` → temp file → open) disappears: the agent opens
`./audit` through the reader and **Follow tails the live store**. Edit → approve restart → watch the
log move. The spec notes this is a design that gets *simpler* over time through a mechanism the
analyser shipped in 1.5.0 — M18 could never have inherited that.

**Evidence.** The `mongoose` starters write `auditBackend: "chronicle"` (live read); the SPI shipped
in 1.5.0 and its `ServiceLoader` path was exercised end to end by the M34.2 probes; `M31.4r` (the
playground example reader) is the same kind of artefact and the two should share a build recipe.
**Cost to us if unfixed:** every cycle carries an export beat and a temp file, and the analyse leg is
a snapshot rather than a tail.
