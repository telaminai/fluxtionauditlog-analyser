# SPEC (PROPOSED) — a catalogue a Fluxtion component jar carries about itself

**Status** proposed · **Target** the existing `fluxtion-builder` jar, exposed by a new goal in
`fluxtion-maven-plugin`
**Evidence** `docs/experience/runs/round-48/` — fifteen measured cells, one model, one problem

**Paired output contract:**
[`spec-builder-component-resolution.md`](spec-builder-component-resolution.md). This document owns
what a component publishes; that spec owns how the builder validates, resolves and renders it. The
two are one build-time feature, not competing proposals.

## The problem, measured

A consumer integrating Fluxtion components spends **32–50% of total effort on comprehension** —
reading the jars to work out what is in them — and **5–10% writing the integration itself**. The
comprehension is almost entirely `javap`: recovering field names, which interface each node
publishes, and which constructor to call.

That work is **reflection performed by the party who does not know the answer.** The component author
knows all of it at build time. Every consumer repeats it.

Adding a hand-written catalogue to the manifest and nothing else took a fixed task from
**111 turns and 12 `javap` calls to 72 and 6**. Completing the catalogue and removing the harness
took it to **51 turns and zero `javap` calls** — the consumer read a text index and never inspected a
class.

**This spec proposes generating that catalogue from the code, because a hand-written one drifts.**
While writing the fixture by hand I shipped a value that silently truncated at 72 bytes and a
notation (`buffer=(none)`) that one consumer read as "this field does not exist" and spent four
lookups checking.

## What the plugin does

A goal — `fluxtion:catalogue` — bound to `package`. Its implementation lives in the existing
`fluxtion-builder` jar; the Maven plugin invokes that implementation rather than owning another
catalogue generator. It scans the module's compiled classes and writes per-entry sections into the
jar manifest. Most fields are derived from bytecode and the compiler model. Three small declarations
carry the semantic facts those sources cannot establish; they are specified below.

### Detection

- **A node** is a class carrying `@OnEventHandler`, `@OnTrigger`, or any other Fluxtion callback.
- **An entry point** is a public class that (a) is not itself a node, and (b) holds nodes as public
  final fields, and (c) has a public constructor taking only non-node types **plus** a second
  constructor taking exactly its node fields — the composite idiom
  (`docs/proposals/upstream-asks.md` ▸ `UP-FLX-28`).

### Emitted keys

```
Name: com/vendor/marketdata/MarketDataPlus.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: mid=MidApi, depth=DepthApi, vol=VolApi, ewma=EwmaApi
Fluxtion-Requires: MidApi, DepthApi
Fluxtion-Constructor: (MidApi, DepthApi)
Fluxtion-Consumes: Tick, Config[volFactor]
Fluxtion-Description: <the class javadoc's first sentence>
```

| key | derived from | why the consumer needs it |
|---|---|---|
| `Provides` | each public final node field, and the interfaces that node implements | `name=Interface` is **the field name for `#{bean.name}` and the type that satisfies a downstream `Requires`** — the single highest-value item measured |
| `Requires` | the consumer-facing constructor's parameter types | closes the dependency chain; usually forces the variant choice |
| `Constructor` | the same constructor's signature | what to write in the bean file |
| `Consumes` | the event types of `@OnEventHandler` methods on nodes in the subtree | which events reach this component |

A field publishing no interface is emitted **bare** (`buffer`), never as `buffer=(none)`, which reads
as absence.

### Deliberately NOT emitted

**Runtime concerns.** No `Accepts-Service`, no `Publishes-Alerts`. The processor routes a registered
service to whatever node accepts that type; the consumer never needs to know which component that is,
and a manifest entry would only drift from the `@ServiceRegistered` annotation that already says it.
**The catalogue is for build-time decisions.**

### A companion for the shared artifact

An artifact of event records and interfaces — no nodes — emits:

```
Fluxtion-Contracts: true
Fluxtion-Events: Tick(String symbol, double bid, double ask); Trade(...)
Fluxtion-Services: AlertSink.publish(String); FeeStrategy.fee(double)/name()
```

so an adapter author never inspects a record to construct one.

## Three annotations the goal needs, and only three

Most of the catalogue is derivable from bytecode: field names, the interfaces a node implements,
constructor parameter types, and the event types of `@OnEventHandler` methods. **Four facts are not**,
and three annotations carry them: entry-point intent and description share `@FluxtionComponent`,
constructor intent uses `@ConsumerConstructor`, and a semantic event filter uses `@EventFilter`.
Nothing else is added — an annotation that restates something the compiler already knows is another
thing that can drift.

### `@FluxtionComponent` — on the entry-point class

```java
@FluxtionComponent(description = "adds volatility and a smoothed mid")
public class MarketDataPlus { ... }
```

Marks a class as a **published entry point** rather than an internal holder, and carries the one-line
description a consumer reads when choosing between variants. Detecting entry points by heuristic
(public final node fields + two constructors) works but silently reclassifies any class that happens
to fit the shape; declaring it is cheaper than debugging it. The description cannot come from javadoc
because javadoc is not in the bytecode the goal reads.

### `@ConsumerConstructor` — on the constructor a consumer calls

```java
@ConsumerConstructor
public MarketDataPlus() { ... }                                   // yours
public MarketDataPlus(MdTick t, MdConfig c, Mid m, ...) { ... }   // the generator's
```

Every entry point has two constructors and the distinction is invisible to bytecode — both are public,
both take reference types. The goal must know which to publish as `Fluxtion-Constructor`, and a
consumer who declares a bean against the wrong one gets `FLX-1001` at best and a silently smaller
graph at worst.

### `@EventFilter` — on a filtered event handler

```java
@OnEventHandler @EventFilter("volFactor")
public boolean onConfig(Events.Config c) {
    if (!"volFactor".equals(c.key())) return false;
    ...
}
```

A handler that accepts only some instances of an event type filters on a **string literal inside a
method body**, which the goal cannot see without bytecode analysis. This is what lets the catalogue
emit `Fluxtion-Consumes: Config[volFactor]`, and it is the fact that tells a consumer which component
owns which configuration key. In round 48 an integrator that could not see this **hard-coded a
whitelist of config keys into its own application** — the vendor's internal knowledge copied into the
consumer's code, guaranteed to break on the vendor's next release.

**Optional, and only if the framework wants it:** the same annotation could feed the generator's
filter dispatch, replacing the hand-written `if (!"volFactor".equals(...)) return false;` guard. That
is a separate change and this spec does not depend on it.

## Publishing the instructions: what belongs in the AI-facing documentation

The catalogue only pays off if the consumer knows how to read it. Round 48 measured which
instructions are load-bearing, by deleting each and re-running. **The result is small enough to state
in full**, and it belongs in Fluxtion's public AI documentation
([`docs/claude.txt`](https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt)) —
the file an agent reads before writing anything.

| section | words | measured cost of removing it |
|---|---|---|
| **reading a catalogue** — the one unfolding command, and that `name=Interface` is *field name* and *published type* | ~120 | 111 → 72 turns when added; javap 12 → 6 |
| **an entry point is not a node** — it carries no annotations, never becomes dirty, cannot be a trigger parent; wire `#{bean.field}`, never `ref="bean"` | ~170 | consumer declared **27 vendor internals** instead of 5 components |
| **what you must not write** — no node classes, no event types, no aggregator, no reflection | ~166 | **wrong answer**, 3 failed builds, +5.8M tokens |
| **the runner shape** — capture the audit log, register services before `init()`, pump events | ~200 | 130 → 102 turns |

**Total ≈ 660 words.** That is the measured adoption cost of authoring correctly against Fluxtion as
a component consumer, and it is the number worth quoting rather than any claim of superiority.

Two things the same measurements say should **not** go in:

- **A worked example.** It produced the fewest builds in the study (2) — and then became *harmful*
  once the catalogue was indexed, because it teaches an agent to `javap` for facts the manifest now
  answers. Discovery aids expire when discovery is precomputed.
- **A step-by-step procedure.** Its "one `javap` per entry point" step produced the most `javap` calls
  of any cell.

Both were written to help and both became instructions to do unnecessary work. **Documentation that
describes *how to find out* should be deleted the moment the artefact can *tell* you.**

## Validation the goal should perform, and fail the build on

Each corresponds to a silent failure measured in round 48. **Every one of these currently produces a
smaller graph and a green build**, which is the wrong failure mode for this framework.

1. **An entry point with no all-fields constructor** — `UP-FLX-28`. Without it the generator emits
   `FLX-1001`; with the documented `@FluxtionIgnore` "fix" it emits a processor containing only the
   roots and **none of the subtree** (495 lines, all twelve compute stages absent).
2. **`@FluxtionIgnore` on a field whose type is a node** — always a mistake, currently silent.
3. **Simple-name collisions across the reactor path** — `UP-FLX-27`. Two vendors shipping `TickIn`
   emits uncompilable code because the constructor is not qualified. The catalogue goal can detect
   the collision at publish time, where the vendor can still act.
4. **A published field whose node implements no interface**, where a sibling does — a likely
   omission, worth a warning rather than an error.

## What this does not solve

- **Production resolution is not built yet.** `tools/bean-resolver.py` now proves that the consumer
  need not write the bean file: it reproduces the measured-optimal selection and wiring from these
  manifests alone, byte-identically, at zero model tokens. The reviewed production target is the
  existing `fluxtion-builder` jar, with its own dependency-free Spring document parser/writer and the
  typed result specified in
  [`spec-builder-component-resolution.md`](spec-builder-component-resolution.md). The Python tool
  remains evidence until that builder path passes its end-to-end gate.
- **It does not make anything more correct.** Round 48 measured no correctness advantage from the
  catalogue; it measured a large *cost* advantage. The correctness argument for Fluxtion rests
  elsewhere — on dispatch being derived rather than stored (round 41).
- **The measurements are n=1 per cell**, one model and one problem shape. Round 55 subsequently
  tested a six-way type-identical ambiguity and round 57 recorded it as a convention/profile choice;
  that establishes one refusal-and-policy mechanism, not general selection behaviour.

## Why the vendor should pay for this

The component author performs the analysis once, at the moment it is certain, and ships the result.
Every consumer stops repeating it. That is the same trade the framework already makes twice — binding
time analysis shipped in `@NoTriggerReference`, dispatch specialisation shipped in the generated
processor — applied to **discovery**, which the measurements say is the expensive half.

---

## Additional build-failing validations (added 2026-09-03, both found by measurement)

**V-A — every entry point sharing a type surface MUST declare a `Fluxtion-Convention`.**
Round 55 + round 57 addendum: where two entry points have identical `Provides`/`Requires`/
`Constructor`/`Consumes`, only a declared convention can discriminate them, and **silence is not a
match** — an undeclared variant becomes unselectable at any site carrying a profile for that figure.
The generator can detect the sharing and must refuse to emit an undeclared variant.

**V-B — the generator MUST verify attributes survive into the JAR, not merely into the source.**
A manifest file without a trailing blank line **silently loses its last attribute**: `jar` dropped
`Fluxtion-Convention: spread=smoothed`, and `PricingSmoothed` became unselectable with no warning from
any tool. The defect was invisible in the `.mf` file, which contained the line.

Both are second independent arguments for this spec's central claim — **manifests must be generated,
not hand-written** — and V-B extends it: *generated is not sufficient; the generator must read back
what it wrote.*
