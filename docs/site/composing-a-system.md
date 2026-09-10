# Composing a system from supplier jars

An audit log is only as useful as the account it can give of the system that wrote it. This page is
about where that account comes from — and why a processor assembled from parts you did not write can
still produce a trace that names every node, in order.

## The bean graph is the node graph

Fluxtion can build a processor from a Spring context. The whole of the mechanism is one line:

```java
for (String beanDefinitionName : context.getBeanDefinitionNames()) {
    Object bean = context.getBean(beanDefinitionName);
    config.addNode(bean, beanDefinitionName);      // the bean IS the node
}
```

Every selected bean becomes a graph node, named by its bean id. Fluxtion reflects over each bean's
annotations — `@OnEventHandler`, `@OnTrigger`, `@ServiceRegistered` — and its references to other
beans, derives the dispatch order, and generates a processor.

There is an entry point that takes a classloader:

```java
FluxtionSpring.compileAot(ClassLoader classLoader, File springFile, String className, String packageName);
```

so the beans can come from jars the compiler was never built against. A supplier ships annotated
classes without knowing the graph they will be composed into. An integrator writes the wiring:

```xml
<bean id="market" class="vendor.pricing.MarketState">
    <constructor-arg ref="cycle"/><constructor-arg value="64"/>
</bean>
<bean id="signal" class="vendor.pricing.SignalState">
    <constructor-arg ref="cycle"/><constructor-arg ref="market"/><constructor-arg value="64"/>
</bean>
<bean id="risk"   class="vendor.risk.RiskLimits">…</bean>
<bean id="intent" class="vendor.orders.IntentPublisher">…</bean>
```

Nobody writes the dispatch order. The compiler derives it from the references.

## Spring is a build-time description, not a runtime container

This is the part that matters for performance and for auditing alike.

In an ordinary dependency-injection system the wiring is a *runtime* structure: a container holds
references, dispatch goes through indirection, and the topology exists only as live object references
that nobody has written down. Here the Spring context is read at **build time** and compiled away. The
generated processor is a plain Java class — no container, no reflection, no Spring anywhere on the
event path.

You pay for the description once, when you build, and it leaves nothing behind.

## The wiring is checked before it runs

Because the composition is a build step, mistakes in it are build failures rather than silence. The
adapter raises typed diagnostics: a handler bound to an event it cannot receive, a binding naming a
bean that was not selected, an undeclared service callback, conflicting log levels.

The reasoning attached to the first is the clearest statement of what this design removes:

> The binding tells Fluxtion to dispatch this event to this node. Fluxtion resolved the node's handler
> methods and none of them takes that event, so the generated processor would carry a route that can
> never fire. That is worse than a build failure: the graph would look wired and stay silent.

## Why the analyser can show you a topology

One build emits three artefacts keyed by **the same names**: the processor, a GraphML topology, and
audit records whose node ids are the bean ids.

So a name typed in a configuration file appears verbatim in the binary audit log:

```yaml
eventLogRecord:
    eventTime: 1789040605190
    logTime: 1789040605190
    event: MarketTick
    nodeLogs:
        - intent: { sym: 7, act: 2, px: 10109}
```

and in the graph the [Topology tab](user-guide/topology.md) renders and steps through.

**The topology is a build artefact, not a runtime reconstruction**, and that is the load-bearing
difference. In a runtime-wired system you can log a great deal, but you cannot produce a faithful graph
of a structure nobody ever declared, because it does not exist in written form anywhere. Here it does.
Stepping through a recorded cycle is then an account of what ran, in order — not an inference from
timestamps.

The consequence for integration is the useful one: you can compose a system out of parts you did not
write and still get a complete node-level trace of it, **across supplier boundaries**. A supplier's node
appears in your log under the name *you* gave it, in the dispatch position the compiler chose, with its
contributions ordered.

## The same description, a different target

Because the processor is generated rather than assembled, one description can be emitted for more than
one target. A graph built this way has been emitted as both a Java processor and a C++ processor and,
replaying the same deterministic event stream, the two agreed at **every one of 1,981,480 decision
points** — identical decisions, identical outputs.

That makes the audit log an equivalence proof: two builds of one description can be compared entry for
entry rather than argued about. A supplier's nodes can reach a C++ deployment without the supplier
writing C++.

## What this does not remove

Suppliers still ship classes carrying Fluxtion's **runtime annotations** — that is a real coupling, and
worth knowing before you plan around it. What they do not need is a build-time dependency on the
compiler, or any knowledge of the graph they will be part of.

Spring XML is one front end and the one that makes the point most plainly. It is not the only way to
describe a graph, and nothing above depends on Spring in particular — only on the composition being
described somewhere a compiler can read it.
