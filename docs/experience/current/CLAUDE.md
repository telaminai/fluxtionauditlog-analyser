# Working in this project

**Read these first — they are the canonical Fluxtion authoring resources, and this file does not repeat
them:**

- <https://fluxtion-playground.dev/build-with-ai> — how to author with an LLM, and the compile/run loop.
- <https://fluxtion-playground.dev/CLAUDE.md> — the author orientation, including the **source-gen triage
  table** (symptom → fix) that covers `transient` / `@FluxtionIgnore`.
- <https://fluxtion-playground.dev/spring-authoring/contract.md> — the `FluxtionSpringConfig` field table:
  `nodeBeans`, `ignoredBeans`, `eventTypes`, and the XML shape this project uses.

*(Pending the owner's sign-off on the agreed set — `docs/specs/spec-authoring-experience.md` D-AX1c.)*

Below is only what those do **not** cover: this project's paths and commands, and how a node participates
in the **audit log**.

A Fluxtion event-processing application hosted by a Mongoose server. You declare what each node depends
on; a compiler derives the execution order and generates the dispatch code. Every run writes an **audit
log** recording which nodes ran, in order, and what each one logged.

Read this before changing anything — three of the five rules below produce **silent** failures if you
guess.

## 0. The shortest path to a working graph — read this first

Ordered by what actually costs build cycles, measured across agent runs.

**0.1 Write `Main` LAST.** `Main` imports the *generated* processor, which does not exist until
`process-classes` has run — and `process-classes` compiles first, so `Main` breaks the very step that
would create the class it needs. Build and generate the graph first; add `Main` after. If `Main`
already exists, move it out of the tree, run `mvn process-classes`, then put it back.
*This is the single most expensive mistake in the corpus. It has caught every author who met it,
including one who had already read a document describing it.*

**0.2 The package root is `com.telamin.fluxtion`, never `com.fluxtion`.** `com.fluxtion.*` is the
pre-rename namespace. It is in old jars and very likely in your memory; it does not exist here. Every
wrong import observed in the corpus was this one substitution.

```java
// the only three builder imports you need — note two roots, they differ
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;    // you implement this
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;  // -> configureGeneration
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig; // -> buildGraph
// runtime
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.OnParentUpdate;
import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;   // note: .builder
import com.telamin.fluxtion.runtime.annotations.builder.Inject;          // note: .builder
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
```
These live in `fluxtion-builder-api`, pulled in transitively — looking inside the `fluxtion-builder`
artifact you declared will not find them.

**0.3 Nodes are ordinary Java classes, and a node's parents ARE its fields.** A constructor argument
you do not keep as a field is not a parent, and the generator will reject the class
(`FLX-1001: cannot find a matching constructor … no constructor accepts the mapped fields`). Keep every
parent as a field. Every field that is *not* a parent must be `transient`. Classes and constructors
must be `public`, and each public class needs its own file — the generated processor sits in a
different package and calls them.

**0.4 Audit logging takes TWO calls, and omitting the first fails silently.**

```java
// build time, in buildGraph — WITHOUT THIS THE LOG IS EMPTY AND NOTHING WARNS YOU
cfg.addEventAudit(EventLogControlEvent.LogLevel.INFO);

// runtime, BEFORE init()
List<String> audit = new ArrayList<>();
flow.setAuditLogProcessor(rec -> audit.add("---\n" + rec));   // or processor.eventLogger.setLogSink(..)
```

**0.5 Run it.** One `onEvent`, taking `Object` — there is no `onOrder(Order)`. Lifecycle is
`init() / start() / stop() / tearDown()`; there is no `shutdown()`.

```java
flow.init();
flow.onEvent(new Order(...));
flow.tearDown();
java -cp target/classes:$(cat cp.txt) com.acme.Main   # cp.txt from: mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
```

**0.6 The audit log is your feedback channel — do not read generated source to learn what ran.**
The log already states it: one record per event, and `nodeLogs` lists the nodes that ran **in dispatch
order**. A node absent from a cycle did not run. A short cycle is propagation correctly arrested. You
do not need to build any record or `path` field of your own — the framework wrote it, which is exactly
why it can be trusted over anything you would write by hand.


## 1. To log its own values, a node needs an `EventLogger` — by interface or by base class

```java
public class PriceThresholdAlert extends EventLogNode {   // <- this is what supplies auditLog
    @OnTrigger public boolean check() {
        auditLog.info("price", price);                     // key/value; lands in this cycle's nodeLogs
        return true;
    }
}
```

`auditLog.info` is overloaded for `int`, `long`, `double`, `boolean`, `char`, `CharSequence` and `Object`,
so numbers stay numbers in the log rather than being stringified — which matters, because only top-level
numeric and boolean values are graphable in the analyser.

### The contract is an INTERFACE — extending is only the convenient route

`EventLogNode` is a convenience class. The actual contract is one method:

```java
public interface EventLogSource {
    void setLogger(EventLogger logger);
}
```

`EventLogNode` implements exactly that and holds the field for you. **Implement the interface instead
whenever your node already has a superclass** — Java gives you one inheritance slot and a real
application's nodes usually want it for a domain base class:

```java
public class RiskLimits extends AbstractRiskRule implements EventLogSource {
    private transient EventLogger auditLog;                       // transient — see §4.3
    @Override public void setLogger(EventLogger logger) { this.auditLog = logger; }

    @OnTrigger public boolean check() {
        auditLog.info("breached", breached);
        return true;
    }
}
```

The framework calls `setLogger` for you; you never construct an `EventLogger`. Both routes are equal —
choose the interface when the base class is taken, and note that anything extending a Fluxtion base such
as `SingleNamedNode` already inherits `EventLogNode`, so it is covered without doing anything.

**Be precise about what this buys you, because it is easy to over-read** (two earlier versions of this file
got it wrong in opposite directions). Extending `EventLogNode` — or implementing `EventLogSource` — is what
lets the runtime hand your node a logger, so it can record **its own key/values**. It is *not* what puts a
node in the record at all.

Three separate conditions govern the record, and collapsing them is the common mistake:

1. the node is **registered** with the auditor (the generated processor registers graph nodes);
2. **invocation tracing** is on at a level that admits the trace — this is what produces the method-name
   line you see for `RiskCheck`, which does not extend `EventLogNode`. With tracing **off**, a node that
   logs no value **need not appear at all**;
3. the node implements **`EventLogSource`**, which is what lets it record values of its own.

So a method-name-only line does not mean the node is fine, and in an untraced log an absent node means
*"said nothing"* rather than *"did not run"*. Implement the contract.

**If you want the propagation path, turn tracing on at build time.** In the Java builder that is the
overload you choose — `addEventAudit(LogLevel.INFO)` installs `tracingOn`, while the no-arg
`addEventAudit()` installs `tracingOff`. With tracing on you get which nodes ran and in what order
**whether or not any of them logs a message**; without it you get only what nodes chose to say.

Watch one silent case: `addEventAudit(null)` is guarded by `if (tracingLogLevel != null)`, so it adds
**no auditor at all** — green build, running application, no record. If the level comes from config,
check it before passing it.

## 2. `@OnTrigger`'s boolean return decides whether anything downstream runs

`return true` propagates to dependent nodes; `return false` stops the cascade for this cycle. It is not a
success flag. If nothing depends on your node the value is unobservable, and returning `true` is the safe
default.

## 3. Position in `nodeLogs` IS dispatch order

Within one record, nodes appear in the order the compiler dispatched them — not alphabetically, not by
declaration. That ordering is derived, guaranteed, and the reason the log can answer *why* a value is what
it is. Read it as causal: a node listed after another ran after it, in the same cycle, on the same event.

## 4. Adding a node

**First work out which route this project uses — they are different and only one applies.** Check the
`fluxtion-maven-plugin` goal in `pom.xml`:

| goal | route | you write |
|---|---|---|
| `scan` | **Java builder** | a class implementing `FluxtionGraphBuilder` |
| `springToFluxtion` | **Spring XML** | a `<bean>` in `src/main/fluxtion/designer/application-context.xml` |

Requirement 3 below applies to **both**. Requirements 1 and 2 are the Spring route only.

### 4.0 The Java builder route (goal `scan`)

The packages are not guessable and are the most likely thing to cost you a compile cycle:

```java
// the builder side
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
// the runtime side — every one of these is needed by an ordinary node, and none is guessable
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;      // an EVENT enters the graph here
import com.telamin.fluxtion.runtime.annotations.OnTrigger;           // a PARENT changed
import com.telamin.fluxtion.runtime.annotations.OnParentUpdate;      // WHICH parent changed
import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;  // note: NOT in .annotations.builder
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.telamin.fluxtion.runtime.audit.EventLogNode;              // supplies auditLog
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;      // .LogLevel for addEventAudit

public class MyGraphBuilder implements FluxtionGraphBuilder {
    @Override public void buildGraph(EventProcessorConfig cfg) {
        StockLedger stock = new StockLedger();
        ReorderPolicy policy = new ReorderPolicy(stock);
        cfg.addNode(stock, "stockLedger");          // the name becomes the instanceId in nodeLogs
        cfg.addNode(policy, "reorderPolicy");
        cfg.addEventAudit(EventLogControlEvent.LogLevel.INFO);   // see §1
    }
    @Override public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.className("MyProcessor");
        cfg.packageName("com.example.generated");
        cfg.outputDirectory("src/main/java");
        cfg.resourcesOutputDirectory("src/main/resources");      // writes the .graphml the analyser pairs with
    }
}
```

**An event gets INTO the graph through `@OnEventHandler`** — nothing else does it, and no example
above shows it:

```java
public class DemandTracker extends EventLogNode {
    @FluxtionIgnore private final Map<String, Double> mwByZone = new HashMap<>();
    @OnEventHandler                       // one per event TYPE this node consumes
    public boolean demandReading(DemandReading e) {
        mwByZone.put(e.zone(), e.megawatts());
        return true;                      // propagation, not success — see §2
    }
}
```

**The membership rule, which is the same in both routes:** a node is in the graph if it is `addNode`d
**or reachable by constructor reference from something that is**. A node that is neither is silently
absent.

### 4.1–4.2 The Spring XML route (goal `springToFluxtion` only)

1. `src/main/fluxtion/designer/application-context.xml` — add a `<bean>`. Its `<constructor-arg ref="..."/>`
   entries are the graph edges. `<constructor-arg value="..."/>` passes a plain value (a threshold, a
   limit) and is supported on graph nodes.
2. In the same file, add the bean's id to `fluxtionSpringConfig`'s `nodeBeans` list. The precise rule, from
   the playground's `spring-authoring/contract.md`: *"If present, only these beans are added as explicit
   Fluxtion nodes; **referenced children are still discovered by Fluxtion**"*. So a bean reached by a
   `constructor-arg ref` from a listed node **is** in the graph; a bean that is neither listed nor
   referenced is **not**, silently. List your node unless something already listed points at it.

### 4.3 Both routes: every non-transient field must be generator-suppliable

The AOT generator rebuilds
   each node, and it can supply a field three ways: a **constructor argument**, a **JavaBean setter**, or a
   **public member**. Use a setter when a constructor argument is awkward. A field that is none of those and
   is not excluded fails the build. So node-local state — a counter, a map, a running maximum — must be
   marked `transient` (or `@FluxtionIgnore`), because it is state rather than a reference the graph
   supplies:

   ```java
   public class SymbolStats extends EventLogNode {
       private final RootNode root;                                  // a constructor arg: fine
       private final transient Map<String, Integer> counts = new HashMap<>();   // state: MUST be transient
   ```

   Miss it and the build fails at `process-classes` with:

   ```
   cannot find matching constructor for: Field{name=…} failed to match for these fields:[counts, root]
   ```

   That message names *constructor matching*, so "add a constructor taking the map" looks like the fix —
   it is not; mark the state `transient`. **Neither shipped example shows this**, because `RootNode` and
   `RiskCheck` hold only null-at-construction state and never hit it.

Then regenerate with `mvn process-classes` (or see `regenerate` in `.claude/skills/` if this
project ships one).

## 5. The generated processor exists in two places and both are outputs

`src/main/java/.../generated/MarketProcessor.java` and `src/main/resources/.../generated/` are both
written by the generator. **Edit neither.** Read them to confirm your node was wired — that is what they
are useful for. The `.graphml` beside the resources copy is what the analyser pairs with the audit log.

## Running, in one line

`./run-server.sh` **blocks** — it ends in `exec java -jar`. Background it, or use a second terminal. The
exact sequence is in `.claude/skills/run-mongoose-server/SKILL.md`; follow it rather than the README's
three-line summary.

## What needs sign-in and what does not

The generated processor **ships with this project**, so it builds and runs with **no compiler sign-in**.
Sign-in is needed **only to regenerate** after you change the graph — which the task above requires. See the
`regenerate` skill.
