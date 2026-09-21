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

When the integrator declares and selects each class separately, its bean id names the graph node.
Nodes discovered inside a supplier's root need their own naming convention, described below.
Fluxtion reflects over each bean's
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

!!! warning "Never list a supplier's class in nodeBeans"
    Reference it from one of your own nodes instead. The starter writes skeleton classes for
    `nodeBeans` entries it cannot find source for. A class that exists only in a dependency jar
    looks like a class that does not exist yet, and the skeleton silently replaces it: the build
    stays green and the supplier's component is gone. This is the open
    [dependency-shadowing defect (feedback #29)][tracker], reproduced in [the experiment's P4 record][predictions].

Nobody writes the dispatch order. The compiler derives it from the references.

## Two ways to compose a supplier's component

**Declare each class.** The integrator wires the individual supplier classes as beans, as in the
example above. When selected directly for the graph, those beans retain the integrator's names.
This describes the compiler's composition model; the starter's open `nodeBeans` defect makes the
reference route below the safe authoring path for dependency-only classes today.

**Reference a root.** The supplier constructs its own sub-graph. The integrator declares its root
bean and references it from a host node; the compiler discovers the internals through fields.
The root's bean id does not name those discovered nodes. The supplier should implement `NamedNode`
for stable names; otherwise generated names can change between builds. Names are global, so
suppliers must coordinate them: the same name in two components can collide. A collision between
suppliers was not exercised in this experiment. See [the naming result and limits][predictions].

## Components that speak your events

A supplier can type its event handler on an interface it owns. The customer's event implements
that interface, and the compiler composes the routes. In the experiment, the fictional Acme Risk
component handles `com.acmerisk.api.Quote`; the host's `MarketPrice` implements it.

This abbreviated generated-code excerpt is transcribed from [the experiment's P17 record][predictions].
The ellipses omit code, and the final line is from the outer event dispatch; it is not a complete
Java method to copy:

```java
public void handleEvent(MarketPrice typedEvent) {
    …  isDirty_acmeQuoteFeed = acmeQuoteFeed.onQuote(typedEvent);   // vendor
    …  isDirty_priceBook     = priceBook.onMarketPrice(typedEvent); // host
} else if (event instanceof Quote) { …                              // any other implementor
```

The supplier publishes interfaces, the customer's events implement them, and neither side needs
an adapter for that shared event. This does not bridge host state automatically: the experiment
shared quotes, while vendor positions still came from its own feed.

The [worked example](integrating-a-vendor-component.md) shows the component, its audit trail and
the independent calculation used to check its results.

## Spring is a build-time description, not a runtime container

This is the part that matters for performance and for auditing alike.

In an ordinary dependency-injection system the wiring is a *runtime* structure: a container holds
references, dispatch goes through indirection, and the topology exists only as live object references
that nobody has written down. Here the Spring context is read at **build time** and compiled away. The
generated processor is a plain Java class — no container, no reflection, no Spring anywhere on the
event path.

You pay for the description once, when you build, and it leaves nothing behind.

## The wiring is checked before it runs

Composition at build time catches several wiring mistakes. The
adapter raises typed diagnostics for a handler bound to an event it cannot receive, a binding naming a
bean that was not selected, an undeclared service callback, conflicting log levels.

It does not catch every mistake. In particular, the starter's dependency-shadowing defect above
can remove a supplier's component while the build stays green. A successful build alone does not
establish that the intended supplier graph was included.

The reasoning attached to the first is the clearest statement of what this design removes:

> The binding tells Fluxtion to dispatch this event to this node. Fluxtion resolved the node's handler
> methods and none of them takes that event, so the generated processor would carry a route that can
> never fire. That is worse than a build failure: the graph would look wired and stay silent.

## Why the analyser can show you a topology

One build emits the processor, a GraphML topology and audit records keyed by node names.
For directly selected beans these are the bean ids; discovered supplier nodes use the supplier's
`NamedNode` names or generated names.

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
The audit record names the nodes that logged during a cycle, in dispatch order, rather than asking
you to infer their order from timestamps. The topology still has a known display gap: a concrete
event that implements a supplier's interface can dispatch down both routes while the graph shows
them as separate event types. Until [TA-9][ta9] ships, do not read the highlighted route as the
complete path for such an event. Absence from the log is also not proof that an unlogged node did
not execute.

The consequence for integration is the useful one: you can compose a system out of parts you did not
write and still get a node-level trace **across supplier boundaries**. An audited supplier node
appears under the integrator's name when directly selected, or the supplier's name when discovered
through its root, in the dispatch position the compiler chose.

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

They do have construction obligations. The generated processor rebuilds nodes from another package,
so every internal node needs a public class and a public wiring constructor matching its final
fields. Suppliers should provide `NamedNode` names, getters and setters for configurable properties,
and interface-typed handlers when customers need to supply their own event types. The
[supplier checklist](integrating-a-vendor-component.md#for-suppliers) collects these requirements.

In the preserved tampered-jar run, the component reported zero risk. Re-running the independent
VaR calculation finds **9 mismatches in 11 records**, against **none in 11** for the genuine run
([checker and inputs][evidence]). The experiment's [P16 record][predictions] reports a green build
and unchanged receipt hashes after the jar was replaced. The trace names the nodes that logged
and their order; it does not establish which build of the supplier jar produced those values or
whether the calculation is right.

Spring XML is one front end and the one that makes the point most plainly. It is not the only way to
describe a graph, and nothing above depends on Spring in particular — only on the composition being
described somewhere a compiler can read it.

[evidence]: https://github.com/telaminai/fluxtionauditlog-analyser/tree/597c5886eafa07f5bc22143589e923dd0d63599a/docs/handoff/evidence/vendor-integration-2026-09-19
[predictions]: https://github.com/telaminai/fluxtionauditlog-analyser/blob/597c5886eafa07f5bc22143589e923dd0d63599a/docs/handoff/evidence/vendor-integration-2026-09-19/PREDICTIONS.md
[tracker]: https://github.com/telaminai/fluxtionauditlog-analyser/blob/main/docs/specs/tracker.md
[ta9]: https://github.com/telaminai/fluxtionauditlog-analyser/blob/main/docs/specs/spec-tool-agreement.md#ta-9--p1--draw-the-route-that-actually-runs-when-dispatch-goes-through-a-supertype
