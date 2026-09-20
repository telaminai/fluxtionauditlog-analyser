# Fluxtion — author orientation

You are writing or modifying Fluxtion code. Read this once at session start; treat it as load-bearing project context. Everything else is a pointer.

This file is the canonical author-orientation document maintained by Telamin. The structure is symptom-organised so you can scan it quickly when you're stuck.

## The mental model in 60 seconds

Fluxtion is a **compile-time event-graph compiler** for Java. You author plain Java classes that hold state and react to events; Fluxtion statically analyses the dependency graph and generates a flat dispatch tree. There is **no scheduler, no thread pool, no async runtime** — events flow through compile-time-determined dispatch order, deterministically, with zero allocation in the hot path.

The graph is declared by **constructor reference passing**: `new Sigmoid(linear)` means "Sigmoid depends on linear's output." Fluxtion topo-sorts those references at compile time and emits the dispatch table. The graph IS the constructor wiring.

Three properties fall out of this model and are why people use Fluxtion:

1. **Deterministic per-event dispatch.** Same input → byte-identical output, every run.
2. **Audit + replay as primitives.** Every node activation is captured; replays reproduce exactly.
3. **Microsecond-bounded latency.** No scheduler, no allocation, single-threaded by design.

If you internalise nothing else, internalise this: **you write nodes, the compiler wires them.** Reason locally per node. Let the framework handle the global topology. This division is why Fluxtion stays composable — local reasoning per node is enough.

## The authoring surfaces

Three legitimate ways to declare the graph, all converging on the same generated processor:

- **Imperative** — classes with `@OnEventHandler` (entry points) and `@OnTrigger` (downstream propagation). Best for stateful nodes, ML/numerical, business rules, anything where domain state lives in fields.
- **DSL** — `DataFlowBuilder.subscribe(...).map(...).filter(...).groupBy(...)`. Best for streaming pipelines. Combinators take **method references**, so any Java method (static, instance, stateful, side-effecting) becomes an operator. **There is no closed operator library** — every Java method ever written is a candidate operator. Don't search for an "operator that does X" in Fluxtion's DSL the way you would in RxJava; just write the Java method and reference it.
- **Spring XML** — declarative bean wiring via `FluxtionSpring.compileAot("model.xml")`. Best for ops-handoff, configuration-as-deployment, or model-graph authoring (ML topology, agent topology).

Mix freely. A single graph can combine all three.

## Compile shapes — pick by what your code does after compile

Three APIs. **Symptom of picking wrong:** assertions on `myNode.someField` read zero/empty after events fire. That's the bystander shape; switch to a live-binding option.

```java
// (1) Fire-and-forget. Processor instantiates a fresh internal node;
// your reference is a bystander. Right for production deploy with no
// introspection. Smallest generated surface.
DataFlow flow = Fluxtion.compile(c -> c.addNode(myNode));

// (2) Same baked-instance shape, but retrievable by name. Right for
// production code that needs id-keyed state queries (inspection,
// monitoring). Note: getNodeById throws checked NoSuchFieldException.
DataFlow flow = Fluxtion.compile(c -> c.addNode(myNode, "name"));
MyNode found = flow.getNodeById("name");

// (3) Live-binding: the user reference IS the dispatch target. Right
// for tests, debugging, demos where holding the reference is more
// ergonomic than a name lookup.
DataFlow flow = Fluxtion.compileDispatcher(c -> c.addNode(myNode));
```

In tests, default to `compileDispatcher`. In production, default to `compile + addNode(name) + getNodeById` if you need monitoring; `compile + addNode(node)` if you don't.

## Required rules

These will save you 80% of cold-start iterations.

1. **Always declare a named package.** `package com.example;` on every Java file. Default-package types are unreachable from the auto-derived named-package generated processor (Java Language Specification rule).
2. **`flow.init()` does NOT reset user-node fields.** It only resets per-event Fluxtion machinery (dirty flags, queue state). If you reuse one processor across batches, add either:
   - `@Initialise` on a method that clears state (idiomatic), OR
   - an explicit `reset()` method called after `init()` (clearest in tests).
3. **Source-gen IS serialisation.** Fluxtion reflects over every node's fields and emits a generated processor that reconstructs them via constructor calls and field assignments. Once you grok this, every gotcha below is obvious — they're all "this field doesn't render as a Java literal" or "this constructor binding is ambiguous."
4. **Single-threaded by design.** Do not introduce executors, threads, or async APIs inside nodes. Concurrency breaks the determinism guarantee.
5. **Mutate state through events, not from outside.** External mutation breaks audit/replay.

## Failures that compile, run, and are still wrong

The triage below covers errors the build **tells** you about. These three do not fail — they produce a
green build and a graph that is not the one you described. Nothing will ever warn you, which is the only
reason they are written down here.

### A plain field reference IS a trigger

Holding another node in a field makes it a **trigger parent**: any change to it fires your `@OnTrigger`.
That is usually what you want, and it is the surprising default exactly when you meant "read this, don't
react to it".

```java
public class ReorderPolicy {
    private final StockLedger ledger;
    private final SkuCatalog catalog;   // intended as a lookup only
    @OnTrigger public boolean recompute() { ... }
}
```

That compiles to `guardCheck_reorderPolicy() { return isDirty_skuCatalog | isDirty_stockLedger; }` — so
every price change now recomputes reorders.

**Fix:** `@NoTriggerReference` (FQN: `com.telamin.fluxtion.runtime.annotations.NoTriggerReference` — note
this one is **not** in `annotations.builder`, unlike `@FluxtionIgnore` and `@AssignToField`) on any
reference you only read from. `@TriggerEventOverride` is the inverse when exactly one parent should be
the trigger and the rest data.

### One `@OnTrigger`, not one per parent

`@OnTrigger` is already an OR over **all** parent dirty flags, so a node with five parents needs **one**
method, not five. Writing one per parent does not give you per-parent dispatch — it gives you every
method firing on every change, and each one **overwrites the node's dirty flag**:

```java
if (guardCheck_revenueLedger()) { isDirty_revenueLedger = revenueLedger.onCatalogChange(); }
if (guardCheck_revenueLedger()) { isDirty_revenueLedger = revenueLedger.onPolicyChange();  }
if (guardCheck_revenueLedger()) { isDirty_revenueLedger = revenueLedger.onStockChange();   }
```

Only the **last** return governs propagation. The first two methods can change state and return `true`,
and downstream still will not run.

**Fix:** one `@OnTrigger`. If you genuinely need to know *which* parent changed, that is
`@OnParentUpdate` — it takes the parent's type as its single argument and runs in the event-in phase,
before this node's `@OnTrigger`.

### Configure the audit log BEFORE `init()`

`setAuditLogProcessor` and `setAuditLogLevel` are **not setters** — each is
`onEvent(new EventLogControlEvent(...))`, a dispatch. `init()` audits a lifecycle event of its own, so:

```java
processor.setAuditLogProcessor(sink);   // BEFORE init(), or the first records go to the default sink
processor.init();
```

The default sink when you configure nothing is `System.out::println`, so "my audit records are printing
to the console" means the sink was attached late, not that logging is broken.

And the level must precede `init()` whichever route you take: `nodeRegistered` stamps each node's logger
with the level **as the node is registered**, so calling `logLevel(...)` afterwards changes the field and
none of the loggers already built from it. It returns normally and does nothing.

## Source-gen annotation triage

Three error patterns, three fixes — pick by symptom.

### Symptom: `cannot find matching constructor for ... fields:[fieldA]`

**The rule is finality, not renderability.** Fluxtion constructor-maps **every `final`, non-`transient`, non-`@FluxtionIgnore` instance field**, because generating a node's source means reproducing its state and a `final` field can only be set by a constructor. The error fires when **no constructor accepts the mapped fields** — whether or not they would render.

A `Map`/`List`/`Set`/custom-class field is the common case because you rarely pass one to a constructor. It is not the only case.

**Fix:** annotate `@FluxtionIgnore` (FQN: `com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore`) or declare `transient`. Both are valid — Fluxtion's source-gen honours the standard Java keyword. The field initialiser still runs in the generated processor's no-arg constructor, so runtime semantics are preserved.

```java
@FluxtionIgnore
private final Map<String, BookState> books = new HashMap<>();

// equivalently:
private transient final Map<String, BookState> books = new HashMap<>();
```

Primitives, `String`s, enums and arrays of those **render** fine — but rendering is not what decides it. A `final int` still fails if no constructor takes it:

```java
public class ReorderPolicy {
    private final StockLedger ledger;
    private final int reorderLevel;                 // config, set in the constructor body
    public ReorderPolicy(StockLedger ledger) {      // ...which does not accept it
        this.ledger = ledger; this.reorderLevel = 10;
    }
}
// FLX-1009: the fields [reorderLevel] look like node-local state rather than
//           references to other nodes
```

**Before choosing the fix, decide what the field is**, because the two cases want opposite answers and the message reads the same for both:

* **derived local state** — a counter, a buffer, a cache the node builds for itself → `@FluxtionIgnore` or `transient`. The initialiser still runs, so nothing is lost.
* **configuration the builder supplied** — `new ReorderPolicy(ledger, 25)` → **do not exclude it**, or the 25 is silently discarded. Add a constructor that accepts it, or make the field non-`final` and give it a setter.

Excluding a field stops Fluxtion supplying its **value**. That is exactly right for state a node builds itself, and wrong for a value you passed in.

### Symptom: `use @AssignToField to resolve clashing types these fields:[a, b]`

Two or more constructor parameters of the **same type** (two `String`s, two `double`s, two same-typed references). Fluxtion can't pick the constructor binding by type.

**Fix:** annotate each clashing parameter with `@AssignToField("fieldName")` (FQN: `com.telamin.fluxtion.runtime.annotations.builder.AssignToField`). The string names the **field** the parameter binds to. Unique-typed parameters don't need the annotation.

```java
public LinearLayer(InputLayer upstream,
                   @AssignToField("weightsPath") String weightsPath,
                   @AssignToField("biasPath") String biasPath) {
    this.upstream = upstream;
    this.weightsPath = weightsPath;
    this.biasPath = biasPath;
}
```

### Symptom: `cannot find symbol: class Foo` in the generated source

Your user types are in the unnamed (default) package. See rule 1 above. Add `package com.example;` to every file.

### Symptom: `cannot find symbol: method lambda$null$...` in DSL-flow generated code

The DSL was passed a state-capturing lambda inside `Fluxtion.compile(cfg -> { DataFlowBuilder...; })`. Source-gen tried to render it as a method ref to a synthetic `lambda$null$<hash>` method — the synthetic only exists as bytecode in the original class, not as a nameable symbol.

**Fix:** replace the lambda with a **named method reference**. Three shapes work — pick by what the function needs to do:

```java
DataFlowBuilder
    .subscribeToNode(classifier)
    .map(QueryClassifier::getCurrentIntent)   // unbound instance — reads upstream type's property
    .filter(Planner::isActionable)            // static — pure transform
    .map(Planner::toToolCall)                 // static
    .push(plannerState::setCurrentCall);      // bound instance — push into a node in the graph
```

If you genuinely need lambda capture, move the consumer outside the compile boundary via `.sink("name")` + `flow.addSink("name", lambdaWithCapture)` after compile (sink callbacks register at runtime, not source-gen'd — but they fire externally to the graph dispatch, so downstream nodes won't see the effect).

**Note:** this rule **inverts** for the runtime-only `DataFlowBuilder...build()` path — there, lambdas are required because no source-gen runs and bound method refs trip CheerpJ's invokedynamic resolution. The right shape depends on the compile API:
- `Fluxtion.compile(cfg -> ...)` (AOT source-gen) → method refs yes, captured lambdas no.
- `.build()` (runtime DSL) → lambdas yes, bound method refs no.

## Bridging DSL flows into imperative `@OnTrigger` nodes

A node only propagates change to its dependents if it has at least one trigger annotation (`@OnEventHandler`, `@OnTrigger`, `@AfterEvent`). A plain POJO registered via `cfg.addNode(...)` has no triggering semantics — downstream nodes that depend on it never fire even though the dependency looks structurally correct.

**Wrong shape** (POJO setter target — silently does nothing useful):
```java
// ❌ tools' @OnTrigger never fires; PlannerState has no triggering annotation
DataFlowBuilder.subscribeToNode(classifier)
    .map(...).filter(...).map(...)
    .push(plannerState::setCurrentCall);
```

**Right shape** (FlowSupplier terminal + `@OnTrigger` holder):
```java
// ✅ FlowSupplier IS a triggering node; PlannerState's @OnTrigger drives downstream tools
FlowSupplier<ToolCall> plannedCall = DataFlowBuilder
    .subscribeToNode(classifier)
    .map(QueryClassifier::getCurrentIntent)
    .filter(Planner::isActionable)
    .map(Planner::toToolCall)
    .flowSupplier();

PlannerState plannerState = new PlannerState(plannedCall);
// PlannerState:
//   private final FlowSupplier<ToolCall> upstream;
//   @OnTrigger boolean onPlannerUpdate() {
//       currentCall = upstream.get();
//       return currentCall != null;
//   }
```

Rule of thumb: `.push()` is for side effects observable outside the graph (logging, sinks, metrics). `.flowSupplier()` + `@OnTrigger` holder is for in-graph state propagation that downstream imperative nodes need to react to.

## Services — typed integration boundaries

Two complementary annotations bridge the dataflow with a host application (Spring, Mongoose, REST controller, test harness). Pick by direction.

### Consuming an external service — `@ServiceRegistered`

A node receives an injected service from outside the graph. The annotated method is invoked when the host calls `flow.registerService(impl, ServiceClass.class)`:

```java
public class TradeEnricher {
    @FluxtionIgnore
    private ProductCatalog catalog;     // not source-gen renderable

    @ServiceRegistered
    public void onCatalogRegistered(ProductCatalog catalog) {
        this.catalog = catalog;
    }
}

// host:
flow.registerService(new ProductCatalogImpl(), ProductCatalog.class);
```

The node imports only the **interface**; production wires a real impl, tests wire a mock, the node code never changes.

### Exposing a typed command surface — `@ExportService`

A node implements an interface and marks the `implements` clause with `@ExportService`. The host retrieves the handle and calls it like any Java reference; each call dispatches as an event through the flow.

**Critical constraint: methods on an `@ExportService` interface MUST return `void`.** Fluxtion's source-generator wraps each method with audit-before / audit-after dispatch and discards return values. Non-void methods fail compilation:

```
error: positionFor(String) cannot implement positionFor(String) in OrderEntry
       return type void is not compatible with int
```

So exported methods are **typed command surfaces** (writes IN), not query surfaces (reads OUT).

```java
public interface OrderEntry {
    void submitBuy(String symbol, int qty);   // void ✓
    void submitSell(String symbol, int qty);  // void ✓
    // int positionFor(String s);             // NON-VOID ✗ — compile error
}

public class PositionTracker implements @ExportService OrderEntry {
    @Override public void submitBuy(String symbol, int qty)  { /* mutate state */ }
    @Override public void submitSell(String symbol, int qty) { /* mutate state */ }
}

// host:
OrderEntry handle = flow.getExportedService(OrderEntry.class);
handle.submitBuy("AAPL", 100);   // dispatched as event into the flow
```

### Queries out of the dataflow — use `getNodeById`, not `@ExportService`

For synchronous reads of derived state, use the compile-mode pattern (2) above:

```java
PositionTracker tracker = flow.getNodeById("positionTracker");
int aapl = tracker.positionFor("AAPL");   // plain getter, no dispatch, no audit
```

The two surfaces compose cleanly: `@ExportService` for typed write commands, `getNodeById` + plain getters for typed read queries. Same node, same JVM heap, two complementary mechanisms — no eventual consistency, no separate read model.

## Browser-runtime constraint (CheerpJ playground only)

This section applies only if your code targets the in-browser Fluxtion playground (CheerpJ Java 8 emulation). It does NOT apply to standard JVM execution.

- **Primitive `float` fields trip `UnsatisfiedLinkError: Java_sun_misc_Unsafe_getFloatVolatile`** during Fluxtion's reflective dependency walk. CheerpJ implements the `int` / `long` / `double` / `Object` / array variants of `Unsafe.get*Volatile`, but not `float`.
  - **Fix:** type tunable scalars as `double`, not `float`. Cast at the use site if needed. Hardcoded constants as `static final` are fine.

```java
// Bad in CheerpJ:
private float threshold = 0.5f;

// Good (and more idiomatic — bean-style mutability):
private double threshold = 0.5;
public void setThreshold(double t) { this.threshold = t; }
```

- **Call `Fluxtion.compile()` at most once per JVM lifetime.** Back-to-back compiles in the same JVM throw `NoClassDefFoundError: <class>$$Lambda$<N>` on the synthetic config-lambda class — CheerpJ unloads it between calls.

- **`CloneableDataFlow.newInstance()` on a DSL-containing topology fails** with `ClassCastException` inside `LambdaReflection.serialized()` when `FilterFlowFunction` (or any DSL operator) re-binds its method reference. CheerpJ's serialised-lambda path doesn't round-trip on the second instantiation.

  These two constraints combine: you can't compile twice, and you can't `newInstance()` a DSL topology either. **The only viable replay-equivalence shape under CheerpJ is single compile + single instance + multiple event passes through the same instance.** This works because Fluxtion agents are per-event stateless when there's no cross-event aggregation — running the same event sequence through the same instance multiple times produces byte-identical audit records (modulo clock fields).

```java
private static volatile DataFlow sharedFlow;
private static volatile boolean sharedFlowInited;

private static synchronized DataFlow sharedFlow() {
    if (sharedFlow == null) {
        sharedFlow = Main.generateFluxtionProcessor();
    }
    if (!sharedFlowInited) {
        sharedFlow.init();
        sharedFlowInited = true;
    }
    return sharedFlow;
}

private static List<String> captureRun() {
    DataFlow flow = sharedFlow();
    List<String> records = new ArrayList<>();
    // setAuditLogProcessor REPLACES the previous listener — each captureRun()
    // collects its own records even when multiple tests share one flow.
    flow.setAuditLogProcessor(record -> records.add(normalise(record.toString())));
    for (Event e : events) flow.onEvent(e);
    return records;
}
```

Standard JVM has neither limitation — the constraint is CheerpJ-specific. If your topology has cross-event aggregation (counters, sliding windows), this pattern won't give byte-identical runs; you'd need an explicit `reset()` method on stateful nodes, called between runs.

## The reference corpus

When you need a working example of a pattern, **scan https://github.com/telaminai/fluxtion-examples** rather than guessing. The repo contains canonical shapes for every Fluxtion authoring pattern. Match the structure of the closest existing example before improvising.

Pattern-to-directory hints:
- DSL pipelines (filter/map/groupBy, windowing, aggregation): `compiler/dataflow/...`
- Imperative `@OnEventHandler` / `@OnTrigger` nodes: `compiler/imperative/...`
- AOT-compiled processors: `compiler/aot-compiler/...`
- Spring XML topology authoring: `compiler/spring/...`
- ML inference (model graph as Fluxtion graph): `compiler/ml-inference/...`
- Audit log emission and replay: any example using `EventLogManager` or `[FLUXTION_AUDIT]` markers
- JUnit 5 testing: any `*Test.java` under `compiler/`; use `compileDispatcher` for live binding

If unsure which example matches your pattern, fetch the repo's top-level README and scan the directory layout. Don't invent new shapes when an existing one applies.

## When you're stuck

1. **Read the error message carefully.** Fluxtion's compile errors are written to be directive — "use @AssignToField" tells you exactly what to do. Don't pattern-match against generic Java exception advice.
2. **Match the shape of the closest fluxtion-examples sample.** Don't invent topology if a precedent exists.
3. **Troubleshooting index** (symptom-organised, every gotcha with a fix): https://telaminai.github.io/fluxtion/troubleshooting/troubleshooting_index/
4. **How-to guides** (replay, ML inference, monitoring, integrations): https://telaminai.github.io/fluxtion/how-to/
5. **Concepts** (event-oriented programming, execution inference, why Fluxtion is structurally different): https://telaminai.github.io/fluxtion/

## Anti-patterns

- **Don't reach for RxJava-style operator chaining habits.** Fluxtion's DSL is sparser by design; combinators take method references and the user-supplied Java IS the operator. Searching for an "operator library" is a category error here.
- **Don't introduce threads, executors, or async inside nodes.** Single-threaded by design; concurrency violates determinism.
- **Don't mutate state from outside the graph.** All state changes flow through events. External mutation breaks audit/replay.
- **Don't bypass `@OnTrigger` propagation with direct method calls between nodes.** The compiler's topological dispatch is what gives determinism; direct calls reintroduce ordering bugs.
- **Don't add features the task didn't ask for.** Fluxtion idiomatic code is small and focused. Three similar lines is better than a premature abstraction; resist generalising until a second use case appears.
- **Don't comment what the code does.** Names already do that. Comment only when WHY is non-obvious — a hidden invariant, a workaround, a CheerpJ-specific constraint, behaviour that would surprise a reader.

## House style

- Plain Java classes for nodes; fields for state; `@OnTrigger` for downstream reactions; `@OnEventHandler` for entry points.
- Constructor takes upstream references — that IS the wiring declaration. No separate wiring file unless you're using Spring XML deliberately.
- Side effects (println, sinks, alerts, network calls) at terminal nodes only.
- Tests use `Fluxtion.compileDispatcher` so assertions read live state.
- Annotations split by package:
  - `com.telamin.fluxtion.runtime.annotations.*` — lifecycle (`@OnEventHandler`, `@OnTrigger`, `@Initialise`, `@Start`, `@Stop`)
  - `com.telamin.fluxtion.runtime.annotations.builder.*` — source-gen hints (`@FluxtionIgnore`, `@AssignToField`)

## Why iteration cycles are short

You don't need to track this consciously, but it's why edits converge quickly:

- **Local reasoning per node.** Each node is ~30 lines: a few fields, a constructor naming its upstreams, one `@OnTrigger` body. You write it without holding the topology in context.
- **The compiler does global reasoning.** Topo-sort, dispatch order, change propagation — all compile-time. You don't need to.
- **No scheduler, no async, no thread bugs.** The framework structurally prevents the concurrency failure modes that bite hand-written event systems.
- **Errors are directive.** "Use @AssignToField" tells you the next move. You converge fast.
- **Open-set operators.** Any Java method is a candidate operator. The "library" you're authoring against is the JDK plus your domain code, not a finite framework API.

Trust the architecture. Reason locally. Let the compiler handle wiring. The framework actively prevents the bugs you'd otherwise write.
