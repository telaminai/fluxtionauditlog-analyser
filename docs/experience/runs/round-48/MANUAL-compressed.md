# Assembling components with Fluxtion — everything you need

## What you write

**The bean file, and a short runner.** Nothing else.

### The bean file — this is the integration

One bean per component you selected, using the constructor its manifest names.

**Cross-component wiring names the NODE, not the entry point.** An entry-point class is a *holder*:
it constructs the component's nodes and exposes them as public final fields, but carries no Fluxtion
annotations itself — so it never becomes dirty and can never trigger anything. Wiring through it
compiles, runs, and silently produces a smaller graph.

```xml
<bean id="alpha" class="com.vendor.alpha.AlphaComponent"/>
<bean id="beta"  class="com.vendor.beta.BetaComponent">
    <constructor-arg value="#{alpha.someNode}"/></bean>
```

`ref=` reaches top-level beans only; `value="#{bean.field}"` reaches a node inside one. The field
names match the figures the manifest lists under `Fluxtion-Provides`.

**Do not declare a component's internal nodes individually.** It works, and it puts the vendor's
structure into your integration, so their next release breaks your bean file.

**Each entry point has two constructors.** The one its manifest names is yours. The other takes every
node field and exists only so the generator can rebuild the subtree — never declare a bean against it.

### The runner — about 40 lines

```java
import com.acme.generated.AppProcessor;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.service.Service;

AppProcessor p = new AppProcessor();
p.setAuditLogProcessor(r -> lines.add(r.asCharSequence().toString()));
p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
p.init();
```

Those three imports are the only framework packages you need. **Do not guess a package** — every
other type you touch comes from the vendors' `contracts` jar.

Then read the scenario, call `p.onEvent(...)`, and write the collected lines out.

**To hand a service to a running engine** — a fee strategy, a policy, anything a component accepts:

```java
p.registerService(new Service<>(FeeStrategies.byName(name), FeeStrategy.class));
```

The component that wants it declares `@ServiceRegistered`; the framework delivers it to whatever
accepts that type. **There is nothing to look up and nothing to wire.** A public method on the node
(`p.capital.fee.feeStrategy(s, name)`) also works.

**Reading state back:** every published figure is a `public` field — `p.capital.fee.value`,
`p.risk.streak.streak`. Direct field access. Never reflection.

## What you must NOT write

- **No node classes.** Every node is supplied by a vendor. If you are writing `@OnTrigger` or
  `@OnEventHandler`, you have misread the task.
- **No new event types.** They are in the contracts jar.
- **No output, report or aggregator class.** Components record themselves into the audit log.
- **No reflection.** Everything you need is a public field or a public method.
- **No fat jar.**

## The build

- The generator runs at `process-classes`. `mvn compile` alone is not enough — use **`mvn -q -o test`**.
  The `-q` matters: Maven's default output is thousands of words of noise per run.
- Anything importing the generated processor goes in **`com.acme.app`**, which compiles after
  generation. Everything else compiles before it and may be declared as a bean.
- **Trust the compiler. Do not read the generated processor.** It is thousands of lines, it is
  correct by construction, and reading it tells you nothing the bean file does not.
- Run with `mvn -q -o dependency:build-classpath -Dmdep.outputFile=cp.txt`, then
  `java -cp "target/classes:lib/*:$(cat cp.txt)" ...`

## If you get stuck

Almost every problem is one of three things: you wired through an entry point instead of a node; you
picked a component whose manifest does not provide what you need; or you are writing code that a
component already provides. Re-read the manifests before writing anything.
