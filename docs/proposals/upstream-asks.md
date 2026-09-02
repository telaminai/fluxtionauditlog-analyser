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

### UP-FLX-39 ◐ The two-phase execution model is undocumented, so authors build around it

_Filed: https://github.com/telaminai/fluxtion/issues/27_

**Target** `fluxtion` docs · **Priority** high for the authoring experience — it is a small doc change
that removes a whole class of unnecessary machinery

An event is processed in two phases: **event-in in topological order, then after-event in REVERSE
topological order on the unwind.** Retrieved 2026-09-01: `@OnTrigger` appears 21 times in `claude.txt`,
`reverse topological` **zero** times in any of the three sources, and **`@AfterTrigger` zero** times.
The annotation reference is strong; the execution model the annotations sit in is absent.

**What it cost here, which is the argument.** Not knowing there was an after-event phase, this repo built
an effect queue drained by a driver *outside* the processor — and wrote into its spec that acting inside
a node would put an irreversible act *"inside a dispatch that has not finished deciding"*. **That is
wrong**: by the after-event phase every decision in the cycle is made. The external mechanism is still
right for this application — outcomes must re-enter as facts, and one effect is asynchronous — but that
is the narrow case, and there was no way to know it was narrow.

Also asks for the `@AfterEvent` / `@AfterTrigger` distinction, which is real and appears nowhere:
`@AfterEvent` always runs; `@AfterTrigger` runs only when the same instance's event-in handler was **on
the current execution path**.

**No diagnostic can reach this.** Nothing fails — the author ships more machinery than they needed and
never sees an error.

### UP-FLX-40 ◐ Re-dispatch is undocumented, and authors infer a FALSE design rule from it

_Filed: https://github.com/telaminai/fluxtion/issues/28_

**Target** `fluxtion` docs · **Priority** high — the failure mode is a propagated false belief, not a
missed feature

`processReentrantEvent` and `processAsNewEventCycle` are at **zero occurrences in all three sources**
(2026-09-01). Without them, the acyclic constraint on the dependency graph reads as a constraint on the
design.

**This repo formed exactly that false rule** — *"you cannot have a node both read and be read by the same
node"* — and **offered to put it into a bootstrap document an LLM loads first.** It was caught only
because the owner said re-dispatch exists. That is the shape worth fixing: not a feature unused, but a
false rule formed and about to be propagated into the documents that teach the next author.

Asks for both methods and the distinction that is not guessable —
`processReentrantEvent` inserts at the **front** and requires an existing cycle;
`processAsNewEventCycle` appends and **forces** one — plus the sentence that stops the false rule: *the
static graph must be acyclic, and that is not a limit on the design.*

**Pairs with [UP-FLX-10](#up-flx-10--mark-a-re-dispatched-record-as-re-dispatched) (#10).** A
re-dispatched record is indistinguishable from an external one in the log, so documenting the mechanism
without that consequence is half the story — and it is the half that matters to anyone reconstructing
causality. The `redispatch: true` attribute discussed on #10 resolves it.

### UP-FLX-38 ◐ `FLX-1009` names annotations without their packages — the last measured retry

_Filed: https://github.com/telaminai/fluxtion/issues/26_

**Target** `fluxtion-builder` diagnostics · **Priority** low effort, and it closes the best case

The successor to [UP-FLX-32](#up-flx-32--the-constructor-match-failure-names-one-of-three-wiring-strategies-and-no-remedy),
which is now implemented: `FLX-1009` forks state from reference, names all four wiring routes, and
certifies when excluding the state alone is a complete repair. Measured, it works — see below. This is
the one thing left on the best-case path.

**Measured 2026-08-31**, two fresh sessions given a real failing build and the sidecar that build emitted
(`docs/experience/runs/ceiling-2026-08-31/`): both fixed it in **one attempt**, both stated the complete
rule, both attributed the rule to the JSON rather than training. Then both named the same single risk of
a second build:

> if `com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore` is the wrong package, the compile
> fails on the import

and both said they would use `transient` **first, specifically to avoid guessing it**. So the message
steers authors away from the annotation it leads with, for want of a package name.

**Ask:** give each annotation named in `suggestedFix` its fully-qualified name once — `@FluxtionIgnore`,
`@ConstructorArg`, `@AssignToField` — and the same for any other code whose fix text names an annotation.

**Why not leave it to an IDE.** The sidecar exists so a machine consumer can act without inference
(UP-FLX-01/#19). An agent applying the fix from JSON has no autocomplete, so the one field the message
omits is exactly the one a non-human reader cannot recover. It is one token per annotation.

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

### UP-FLX-44 ● A node inside a COLLECTION argument is default-constructed, silently

**Target** `fluxtion-builder` — source-gen extraction · **Priority** high — green build, wrong values, and
the failure is invisible in the run that causes it

**Measured 2026-09-01**, round 08 of the authoring experiment, by an agent that had not been told to look
for it. A builder passed operator limits as nodes inside a `List`:

```java
new DispatchPolicy(spread, demand, fleet, List.of(new ZoneLimit("NORTH", 20.0),
                                                  new ZoneLimit("SOUTH", 35.0)))
```

The generated processor emitted:

```java
new com.acme.grid.DispatchPolicy(spreadSignal, demandTracker, fleet,
    Arrays.asList(new ZoneLimit(), new ZoneLimit()));
```

**Both limits are gone.** `ZoneLimit` has no no-arg constructor, so the generated file does not even
compile — but `mvn process-classes` reported **SUCCESS**, because `compile` runs *before*
`process-classes` and the file just written is not compiled until the next build. The author gets a green
build and a processor that has silently discarded configuration.

**Two things make this worse than an ordinary mistake.** The next `mvn process-classes` fails on the
*previous* run's bad output, so the error points at generated code rather than at the builder; and the
only repair — delete the generated directories first — is not obvious from the message. The agent
recovered only by reading the generated constructor, which is not a step anything prompts.

**What the compiler already knows.** It resolved the collection, so it knows the elements are nodes and
knows their declared constructors. Either render them properly (the scalar path already does — a
`Map<String,Double>` constructor argument renders correctly via `MapBuilder`), or refuse with a coded
diagnostic naming the element type and the fix — register each element with `addNode` so it serialises
by reference.

**Cost to us if unfixed** this is the same class as UP-FLX-32…34: a silent value loss the author can only
catch by inspection. It is also the one defect in two measured rounds that produced a *wrong business
outcome* from a green build, so it is the highest-value diagnostic remaining.

### UP-FLX-41 ○ An auditor can ask to be FIRST but cannot ask to be LAST

**Target** `fluxtion-runtime` — `Auditor` · **Priority** medium — a real gap, but one with three
workarounds, at least one of which is already first-class

**What exists.** `Auditor.processingComplete()` is documented as being called *"following all the nodes
annotated with `@AfterEvent` have been invoked"*, and `Auditor.FirstAfterEvent` is a marker interface that
moves an auditor **before** them. So there is exactly one ordering control and it has one direction.

**What is missing.** Among several ordinary auditors there is no defined order, and none of them can
declare itself last. A processor with two auditors therefore has no single moment that means *"the audit
record for this cycle has been published"* — the phrase names a different instant depending on which
auditor you meant and an order nobody specified.

**How we hit it.** This repo's session processor has one auditor, so the ordering holds, and we very
nearly wrote a design note treating *"`@AfterEvent` runs before the record publishes"* as a framework
guarantee. It is a guarantee about a processor with one auditor. The owner's objection is the ask:

> *"there could be multiple auditors who all want to be last, how do you know which one to fire?"*

**Four candidate answers, owner's, cheapest last.** Recorded together because the choice is a design
call for the framework and not ours:

| answer | shape |
|---|---|
| **an end-of-transaction event** | the boundary becomes an event rather than a phase position |
| **an auditor re-dispatches one** | an auditor needing to be last raises an end-of-transaction event, which arrives as a fresh cycle after the current one has fully completed |
| **`BatchHandler.batchEnd()`** | already exists, bound by `@OnBatchEnd`, already documented as a transaction boundary — the answer for *"publish once the set of events is complete"* |
| **a preferred firing number on `Auditor`** | a sortable key so `afterEvent` emits auditors in a declared order; smallest change, and the only one that answers the question *as asked* |

**Cost to us if unfixed** low today and it is worth saying so: our external effect drain runs after
`onEvent` returns, which is after every phase and every auditor however many there are, so it depends on
no ordering at all. The cost is to the *authoring docs* — the gap is invisible until you have two
auditors, and until then the natural reading of `FirstAfterEvent` is that a symmetric `LastAfterEvent`
exists.

### UP-FLX-42 ◐ BUG — no recovery model for an exception thrown inside a cycle

_Filed: https://github.com/telaminai/fluxtion/issues/30_

**Target** `fluxtion-runtime` (semantics) → `fluxtion-compiler` (`javaTemplate.vsl`) ·
**Priority** high — silent, permanent, and the audit log is quiet about exactly the failed cycle

**Full report: [`upstream-content/bug-processing-flag-not-restored.md`](upstream-content/bug-processing-flag-not-restored.md).**
Owner is fixing it next release (2026-09-01).

**Reproduced.** One exception from user code anywhere in a dispatch leaves `processing = true`, after
which `processEvent` queues every later event to the callback stack and never drains it. `onEvent`
returns normally and nothing happens — no exception, no log line, no degraded mode. Seven unguarded
sites in the template, the worst being `beforeServiceCall`/`afterServiceCall`, where the set and the
clear are in different methods.

**But the flag is the smallest part, and the owner's framing is the right one: there is no recovery
model at all.** A throw skips `afterEvent()`, so — in order of how much it matters — **the cycle's audit
record is never published** (`eventLogger.processingComplete()` lives there), the **dirty flags are never
reset** and poison the next cycle's `@OnTrigger` guards, and the callback stack retains a dead cycle's
events for the next unrelated one to run.

**The record one is the cheapest to fix and was miscalled in the first draft.** The failed cycle's
record is **not lost** — `DataFlow#getLastAuditLogRecord()` returns it in full, every node that ran with
everything it logged (verified), and `EventLogManager#publishLastRecord()` flushes it to the sink. Both
are javadoc'd *"Useful when error handling if an exception is thrown"*. So the framework anticipated
this and the methods simply have no caller on the exception path: **the `finally` should call
`publishLastRecord()`**, and that one line covers the symptom that matters most. Hosts can do it today
in a `catch`. That neither of us knew until the owner pointed at it is itself evidence for UP-FLX-43.

**Cost to us if unfixed** we already pay it: `EffectQueue` catches everything and stashes fatals for the
driver to rethrow after `batchEnd()` returns. It works, and it is a workaround every author must invent
independently and most will not know they need.

### UP-FLX-43 ◐ DOC — "The life of a single event": every hook point, in firing order

_Filed: https://github.com/telaminai/fluxtion/issues/29_

**Target** `fluxtion` docs · **Priority** high — it is the cheapest ask here and it retires several others

**Draft written and ready to submit: [`upstream-content/life-of-an-event.md`](upstream-content/life-of-an-event.md).**

**The evidence that this page is missing is behavioural, not aesthetic.** The two-phase model IS
documented — in `@OnParentUpdate`'s javadoc, which is not where anyone looks. Consequently:

* this project built an **external effect drain** and defended it in a spec with three reasons, one of
  which was that nobody had read `BatchHandler` (now `@OnBatchEnd`, four lines);
* an independent LLM author on a different application hand-rolled a **read-then-write ordering dance**,
  then found `@AfterEvent` and reported it was *"more correct than what I had written"* — same arc, one
  step earlier, and that author never found `@OnBatchEnd` either;
* **four of six sessions** inferred sibling dispatch order wrongly (UP-FLX-36, filed as
  `fluxtion` issue 23 — the page carries that fact, so issue 29 may supersede it).

Three authors, three applications, one missing page — and every wrong turn was a **sequencing** fact,
which is the thing this framework is most certain about because it compiles it.

**One gap the page names but cannot close.** The boolean return is the propagation decision, and the
compiler knows it; what `true` *means for a given node* — under which conditions it considers itself
changed — exists only in prose. Independently reported by the other author as the artefact they would
most like added.

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

### UP-FLX-28 ☐ The composite-node idiom works but is undocumented, and getting it wrong fails silently

**Target** `fluxtion` (docs, and one diagnostic) · **Priority** high — it is the load-bearing
requirement of the component-composition story

**This entry originally claimed a composite node was impossible. That was wrong.** The owner supplied
the pattern and it works; what follows is the corrected version. The ask is now documentation plus
one diagnostic, not a feature.

## The pattern

A supplier publishes **one class representing a subtree**, with **two constructors**:

```java
public class MarketData {
    public final MdTick tick; public final MdConfig config;
    public final Mid mid; public final Depth depth; public final Vol vol;

    public MarketData() {                        // what a CONSUMER declares - builds the subtree
        this.tick = new MdTick(); this.config = new MdConfig();
        this.mid = new Mid(tick); this.depth = new Depth(tick); this.vol = new Vol(config, mid);
    }
    public MarketData(MdTick tick, MdConfig config,   // what the GENERATOR needs, to reconstruct it
                      Mid mid, Depth depth, Vol vol) { ... }
}
```

The consumer-facing constructor takes only the subsystem's **external** dependencies
(`Pricing(MarketData)`, `Capital(Risk)`); the second accepts **every node-typed field**, which is what
the generator matches against.

**Measured**: five beans — `marketdata`, `pricing`, `liquidity`, `risk`, `capital` — generate **975
lines** containing all twenty nodes, and produce a trace **identical** to the hand-declared twenty-bean
version across nine events. That is the component boundary the market story needs: the consumer names
five components and never sees a supplier's internals.

## Why this still needs upstream work

**It is not in the reference.** `docs/claude.txt` says the opposite —

> *"**No such pattern is documented in this material.** … If you need to group a subtree, the standard
> approach is to wire dependencies explicitly through a parent node's constructor."*

and *"self-constructed fields are not the idiomatic pattern."* Following the reference leads away from
the pattern that works.

**And the two ways of getting it wrong differ badly in kind:**

| shape | result |
|---|---|
| root holds self-constructed children, **no** all-fields constructor | **FLX-1001** — *"no constructor accepts the mapped fields [buffer, charge, config, trade]"*. Loud, fixable. |
| same, plus the reference's suggested `@FluxtionIgnore` on those fields | **495 lines containing only the five roots.** All twelve compute stages absent. |

The second is the problem. The generator also drops the roots' constructor arguments — emitting
`new Pricing()` for `Pricing(MarketData)` — so roots *taking arguments* fail to compile. **A root with
a no-arg constructor compiles cleanly and contributes nothing**: `new MarketData()` is emitted,
accepted, and its five nodes never dispatch. Green build, empty subtree, no diagnostic. An author who
hits FLX-1001 and applies the documented `@FluxtionIgnore` fix is walked from a loud failure into a
silent one.

## The ask

1. **Document the two-constructor composite idiom** in `docs/claude.txt` and the golden path, and
   correct the statement that no such pattern exists.
2. **Make the silent case loud.** `@FluxtionIgnore` on a field whose type is a node (one carrying
   `@OnEventHandler` / `@OnTrigger` methods) is almost certainly a mistake and deserves a diagnostic
   rather than a quietly smaller graph.

**Evidence.** `docs/experience/runs/round-42/` — the five published roots, both failure modes, the
generated source, and the identical-trace comparison.

### UP-FLX-29 ☐ Let a component publish its nodes under namespaced bean names

**Target** `fluxtion` (Spring extension) · **Priority** medium — it is the ergonomic half of the
component story, and it makes the integration machine-generatable

**The problem.** A component ships one entry-point class holding its subtree. Another component needs
one of the *nodes* inside it — `Adjusted` needs marketdata's `mid`. The entry point cannot stand in:
it carries no Fluxtion annotations, so it never becomes dirty and cannot be a trigger parent.
Measured: wiring through the entry point generates 1083 lines that **compile, run, and silently fire
8 stages instead of 17**, because `isDirty_marketdata` does not exist.

**What works today** is a SpEL expression reaching inside the bean:

```xml
<bean id="pricing" class="com.vendor.pricing.PricingFull">
    <constructor-arg value="#{marketdata.mid}"/>
    <constructor-arg value="#{marketdata.depth}"/></bean>
```

That is correct — `guardCheck_adjusted() { return isDirty_depth | isDirty_mid; }` — but it has three
costs. It is not discoverable; it breaks if the entry point also exposes a same-named accessor (SpEL
prefers the property, and `#{marketdata.mid}` silently became a `Double`); and it reaches through a
component boundary by field name, so the vendor cannot rename an internal field without breaking
consumers.

**The ask.** Let a component declare the nodes it publishes, and have the container register them
under namespaced names, so cross-component wiring is an ordinary reference:

```xml
<constructor-arg ref="marketdata.mid"/>
```

The name is an **alias to the same instance**, so identity and the trigger edge are unchanged; it is
only the addressing that improves. The publishing set is exactly what the jar's manifest already
declares — `Fluxtion-Provides: mid,depth,vol,ewma` — so the container can register the aliases from
metadata the component already ships, and a consumer never writes an expression.

**What it buys beyond ergonomics.** With `provides`/`requires` in the manifest and aliases derived
from them, **the bean file becomes derivable**: a small tool can resolve which entry points satisfy a
stated set of required figures and emit the XML deterministically. That is a better answer than asking
a language model to write it, and it is the shape of a real component market — resolution, not
authorship.

**Also worth a diagnostic.** Passing an entry-point class where a node is expected is always a
mistake, and today it is silent. A class with no Fluxtion annotations used as a constructor argument
to a node cannot ever trigger it; that deserves a build error, not a smaller graph.

**Evidence.** `docs/experience/runs/round-48/` — both wirings, the generated guards for each, and the
17-versus-8 stage counts.

---

## UP-FLX-45 — make the WALL-CLOCK read lazy (narrowed after the author's design rationale)

**This entry was filed overclaiming and is corrected here rather than quietly edited.** It originally
called an unconditional clock call "the single largest performance item" and framed it as a defect.
Two things narrowed it: the author's stated rationale — *every node gets a data-driven clock it can
consult for predictable time in replay mode, and anyone needing the last nanosecond can configure the
auditors away* — and the measurements that rationale prompted, which **support it**.

**The feature is nearly free where it matters.** 10-node graph, one reused event, 200,000,000 events
under `-XX:+UseEpsilonGC`, all arms byte-identical at 0.0 bytes/event:

| arm | throughput | ns/event |
|---|---|---|
| shipped, default wall-clock strategy | 57,785,590 /s | 17.31 |
| **replay — `ClockStrategy.registerClockEvent(() -> t)`** | 117,687,130 /s | **8.50** |
| auditor map cleared at build time | 121,624,745 /s | 8.22 |

**Replay mode costs 0.28 ns more than removing the clock entirely.** So the deterministic data-driven
clock — the actual feature — is ~3% of dispatch, not 55%. The 9.43 ns measured earlier is
**entirely `System.currentTimeMillis()`** in the default strategy; corroborated by that call measuring
12.32 ns/call standalone on the same machine.

**Both escape hatches exist and were verified**, not taken on trust:
`config.getAuditorMap().clear()` at build time emits an `auditEvent` containing only
`nodeNameLookup.eventReceived` — `clock` and `serviceRegistry` are gone — and runs at 8.22 ns.

**What remains, and it is narrow.** In **live** mode a graph that never consults the clock still pays
~9.1 ns per event (53% of its dispatch) for a timestamp nothing reads. The ask is only: **read the
wall clock lazily** — on first `getProcessTime()`/`getEventTime()`/`getWallClockTime()` request per
cycle, then cache — so same-value-within-a-cycle is preserved exactly and non-time-reading live graphs
pay nothing. Replay is already unaffected because the strategy is not a system call.

**Priority: low, and the author's "edge case" judgement is defensible.** The default trades ~9 ns for
a uniform time source; the workaround is one line; and the users who care most about the nanosecond
are the ones running replay, who already do not pay it. This is recorded as a measurement worth having
rather than a change worth making.

**Evidence.** `docs/experience/runs/round-54/` — arms, ablations, and the generated sources.

**Caveat.** Single machine (JDK 21 Corretto, macOS), one process per configuration, closed loop, no
coordinated-omission correction.
