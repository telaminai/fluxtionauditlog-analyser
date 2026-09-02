# SPEC (PROPOSED) — `fluxtion-component-maven-plugin`: a catalogue a jar carries about itself

**Status** proposed · **Target** a new goal in `fluxtion-maven-plugin`, or a sibling plugin
**Evidence** `docs/experience/runs/round-48/` — fifteen measured cells, one model, one problem

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

A goal — `fluxtion:catalogue` — bound to `package`. It scans the module's compiled classes and writes
per-entry sections into the jar manifest. **No new source annotations**: everything it emits is
already derivable from the node annotations and the published types.

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

- **The consumer still writes the bean file.** With `Provides`/`Requires` in the manifest the wiring
  is a resolution rather than an authoring task, so a small tool could emit the XML from a list of
  required figures. That is a separate proposal and it is the natural next step.
- **It does not make anything more correct.** Round 48 measured no correctness advantage from the
  catalogue; it measured a large *cost* advantage. The correctness argument for Fluxtion rests
  elsewhere — on dispatch being derived rather than stored (round 41).
- **The measurements are n=1 per cell**, one model, one problem shape, and the fixture's dependency
  chain forces every component choice. A catalogue with genuine ambiguity has not been tested.

## Why the vendor should pay for this

The component author performs the analysis once, at the moment it is certain, and ships the result.
Every consumer stops repeating it. That is the same trade the framework already makes twice — binding
time analysis shipped in `@NoTriggerReference`, dispatch specialisation shipped in the generated
processor — applied to **discovery**, which the measurements say is the expensive half.
