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

**Be precise about what this buys you, because it is easy to over-read** (an earlier version of this file
got it wrong). Extending `EventLogNode` gives your node an `auditLog` handle so it can record **its own
key/values**. It is *not* what makes a node appear in the log at all: the generator emits an
`auditInvocation` at each dispatch site, which is why `RiskCheck` — which does **not** extend
`EventLogNode` — still appears in every record, showing only its method name.

So: without `EventLogNode` your node may still appear, but it can record **nothing about what it computed**
— and a node that runs and reports no value is indistinguishable from one that did nothing. Extend it.

## 2. `@OnTrigger`'s boolean return decides whether anything downstream runs

`return true` propagates to dependent nodes; `return false` stops the cascade for this cycle. It is not a
success flag. If nothing depends on your node the value is unobservable, and returning `true` is the safe
default.

## 3. Position in `nodeLogs` IS dispatch order

Within one record, nodes appear in the order the compiler dispatched them — not alphabetically, not by
declaration. That ordering is derived, guaranteed, and the reason the log can answer *why* a value is what
it is. Read it as causal: a node listed after another ran after it, in the same cycle, on the same event.

## 4. Adding a node: THREE requirements

1. `src/main/fluxtion/designer/application-context.xml` — add a `<bean>`. Its `<constructor-arg ref="..."/>`
   entries are the graph edges. `<constructor-arg value="..."/>` passes a plain value (a threshold, a
   limit) and is supported on graph nodes.
2. In the same file, add the bean's id to `fluxtionSpringConfig`'s `nodeBeans` list. The precise rule, from
   the playground's `spring-authoring/contract.md`: *"If present, only these beans are added as explicit
   Fluxtion nodes; **referenced children are still discovered by Fluxtion**"*. So a bean reached by a
   `constructor-arg ref` from a listed node **is** in the graph; a bean that is neither listed nor
   referenced is **not**, silently. List your node unless something already listed points at it.
3. **Every non-transient field of your node must be reachable from a constructor argument.** The AOT
   generator rebuilds each node by matching its instance fields to a constructor. So a node that carries
   its own state — a counter, a map, a running maximum — **will not build** unless that state is marked
   `transient`:

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

Then regenerate — see `regenerate` in `.claude/skills/`.

## 5. The generated processor exists in two places and both are outputs

`src/main/java/.../generated/MarketProcessor.java` and `src/main/resources/.../generated/` are both
written by the generator. **Edit neither.** Read them to confirm your node was wired — that is what they
are useful for. The `.graphml` beside the resources copy is what the analyser pairs with the audit log.

## Running, in one line

`./run-server.sh` **blocks** — it ends in `exec java -jar`. Background it, or use a second terminal. The
exact sequence is in `.claude/skills/run-mongoose-server/SKILL.md`; follow it rather than the README's
three-line summary.

## What needs a key and what does not

The generated processor **ships with this project**, so it builds and runs with **no Fluxtion API key**.
A key is needed **only to regenerate** after you change the graph — which the task above requires. See the
`regenerate` skill.
