# Spring XML authoring contract for the Fluxtion starter

**Audience:** LLMs and authoring tools that need to create Spring XML which the
Fluxtion project starter can import.

**Goal:** produce one valid Spring `<beans>` document that describes a Fluxtion
processor design. The starter can import the XML by file picker or drag/drop,
then generate matching Java skeleton classes, a Maven project, and a Spring
`application-context.xml` ready for Fluxtion AOT/in-process compilation.

**Related:**
- Core Fluxtion model (nodes, events, compile-time graph, determinism):
  [`claude.txt`](https://telaminai.github.io/fluxtion/claude.txt). Read it
  first; this file is the Spring-XML output contract layered on that model.
- How to *run the design conversation* that produces this XML: `skill.md` (this folder).
- A full worked walk-through: `example.md` (this folder).

## Three kinds of relationship (get this right — it is the core of the model)

The XML declares exactly three kinds of relationship. Do not conflate them:

1. **`constructor-arg ref` between node beans** — *static topology.* Compile-time graph edges (`new B(a)` means B depends
   on a). Always present, ordering-relevant.
2. **`serviceRegistrations`** — *dynamic attachment ports.* Named points where the outside world plugs an external
   service into a node at runtime (`@ServiceRegistered` callbacks). A service may register late, deregister, or be
   replaced. **Never** part of graph topology; never affects dispatch order.
3. **`eventTypes` / `eventHandlers`** — *inbound facts.* The events the outside world pushes into the graph.

**Prohibition:** a service is **never** wired into a node with `constructor-arg ref` or `property ref`, and a service
interface is **never** declared as a Spring bean. Services are declared in `serviceTypes` and attached via
`serviceRegistrations`; the starter generates the `@ServiceRegistered` callbacks. A `constructor-arg ref` to a
service-typed bean is a validation error (services are not graph dependencies — a constructor reference asserts
presence-at-construction, which a service cannot guarantee). This replaces the old habit of parking a service in
`ignoredBeans`; `ignoredBeans` is only for genuine non-service support/config beans.

## The shape to generate

Always generate standard Spring beans XML. Do not invent a custom namespace. Add
one `FluxtionSpringConfig` bean to separate graph nodes, input event contracts,
helper beans, and handler expectations.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="fluxtionSpringConfig"
          class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig">

        <property name="eventTypes">
            <list>
                <value>com.acme.demo.event.TradeEvent</value>
                <value>com.acme.demo.event.MarketDataEvent</value>
            </list>
        </property>

        <property name="nodeBeans">
            <list>
                <value>tradeHandler</value>
                <value>marketDataHandler</value>
                <value>riskNode</value>
            </list>
        </property>

        <!-- External services this design consumes (interface FQCNs, inert strings — never beans). -->
        <property name="serviceTypes">
            <list>
                <value>com.acme.demo.service.RiskGateway</value>
            </list>
        </property>

        <property name="ignoredBeans">
            <list>
                <value>calibrationSettings</value>
            </list>
        </property>

        <property name="eventHandlers">
            <list>
                <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.EventHandlerBinding">
                    <property name="event" value="com.acme.demo.event.TradeEvent"/>
                    <property name="nodeBeans">
                        <list>
                            <value>tradeHandler</value>
                        </list>
                    </property>
                </bean>
                <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.EventHandlerBinding">
                    <property name="event" value="com.acme.demo.event.MarketDataEvent"/>
                    <property name="nodeBeans">
                        <list>
                            <value>marketDataHandler</value>
                        </list>
                    </property>
                </bean>
            </list>
        </property>

        <!-- Attach RiskGateway to riskNode: the starter generates an @ServiceRegistered callback.
             NOT a constructor-arg — services are dynamic attachment ports, not graph edges. -->
        <property name="serviceRegistrations">
            <list>
                <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.ServiceBinding">
                    <property name="service" value="com.acme.demo.service.RiskGateway"/>
                    <property name="nodeBeans">
                        <list>
                            <value>riskNode</value>
                        </list>
                    </property>
                </bean>
            </list>
        </property>
    </bean>

    <bean id="tradeHandler" class="com.acme.demo.node.TradeHandler"/>
    <bean id="marketDataHandler" class="com.acme.demo.node.MarketDataHandler"/>
    <bean id="riskNode" class="com.acme.demo.node.RiskNode">
        <constructor-arg ref="tradeHandler"/>
        <constructor-arg ref="marketDataHandler"/>
    </bean>

    <bean id="calibrationSettings" class="com.acme.demo.config.CalibrationSettings"/>
</beans>
```

## What each `FluxtionSpringConfig` field means

| Field | XML value | Meaning to Fluxtion / starter |
|---|---|---|
| `nodeBeans` | list of Spring bean ids | Explicit graph root/node beans. If present, only these beans are added as explicit Fluxtion nodes; referenced children are still discovered by Fluxtion. |
| `eventTypes` | list of fully-qualified class names | Declared input event types. Preferred for XML authoring because no event bean instance is created. |
| `eventClasses` | list of `Class<?>` values (Java config) | Same event contract as `eventTypes`, for Java `@Configuration` authoring. **Do not use in starter XML for not-yet-created events:** if the config bean is ever instantiated, Spring's `ClassEditor` loads these classes and fails with `ClassNotFoundException` for skeleton classes that don't exist yet. Always use `eventTypes` (inert strings) in XML the starter imports. |
| `eventBeans` | list of Spring bean ids | Compatibility path: the named beans define event types and are not graph nodes. Prefer `eventTypes` for new starter/LLM output. |
| `ignoredBeans` | list of Spring bean ids | Beans that must never be added as graph nodes. Use for genuine non-service support/config/factory beans only — **services go in `serviceRegistrations`, not here.** |
| `eventHandlers` | list of `EventHandlerBinding` inline beans | Design-time expectations: this event type should be handled by these node beans. It does not manually route events; Fluxtion still discovers dispatch from generated or authored Java annotations. |
| `strictEventHandlerBindings` | boolean | Optional. If `true`, the compiler rejects nodes that handle declared events but are not listed in the matching binding. Leave absent/false unless the design needs a locked handler surface. |
| `serviceTypes` | list of fully-qualified **interface** names | External services the design consumes, as inert strings (like `eventTypes`). Not beans, not graph nodes; never loaded at import time. |
| `serviceRegistrations` | list of `ServiceBinding` inline beans | Attach a service to the node(s) that consume it. Each binding: `service` (an FQCN from `serviceTypes`), optional `serviceName` (name filter — becomes the `@ServiceRegistered("name")` value), and `nodeBeans` (the consuming nodes). The starter generates the `@ServiceRegistered` callback + a service-interface stub. |
| `strictServiceBindings` | boolean | Optional, default `false`. If `true`, a node that registers a service **whose type is declared in `serviceTypes`** but has no matching `ServiceBinding` fails the build. Scoped to the declared service namespace — a node's other/framework service registrations are ignored. Only meaningful for graphs whose node classes you fully own; leave absent for imported/third-party nodes. |
| `logLevel` | `INFO`, `DEBUG`, etc. | Enables Fluxtion event audit at that level. |
| `auditors` | list of auditor bean refs | Registers Fluxtion auditors. |

## Naming rules for LLM output

- Use stable Spring bean ids in lower camel case: `tradeHandler`,
  `marketDataHandler`, `riskNode`.
- Use fully-qualified class names for `eventTypes`, `eventClasses`, node bean
  `class` attributes, and `eventHandlers.event`.
- Avoid simple event names such as `TradeEvent` in `eventHandlers.event`. Use
  `com.acme.demo.event.TradeEvent`.
- For `eventBeans`, `eventHandlers.event` may use the event bean id, but this is
  a compatibility form. Prefer FQCN event types.
- Do not list a bean in more than one bean-name role:
  `nodeBeans`, `eventBeans`, and `ignoredBeans` are mutually exclusive.
- Do not compare event class names with bean ids. `eventTypes`/`eventClasses` are
  type-space declarations; `nodeBeans`/`eventBeans`/`ignoredBeans` are bean-name
  declarations.
- Use **singular** role packages — `<base>.node.MyNode`, `<base>.event.MyEvent`,
  `<base>.service.MyService` — never plural (`.nodes.`/`.events.`/`.services.`). Only the
  singular suffixes are recognised as role directories; with any other layout the starter
  still recovers a base package (common prefix) but emits the skeleton under its singular
  convention, so every generated FQCN silently differs from the FQCNs authored in the XML.
- Declare services as FQCN **interfaces** in `serviceTypes`; every
  `serviceRegistrations.service` must be one of them. `serviceRegistrations.nodeBeans`
  must be declared `nodeBeans`. **Never** declare a service as a `<bean>` and **never**
  `constructor-arg ref`/`property ref` a node to a service — use a `ServiceBinding`.

A binding can attach one service to several consumers and can filter by runtime
name. For example, this binds only the `"primary"` `RiskGateway` instance to two
nodes:

```xml
<bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.ServiceBinding">
    <property name="service" value="com.acme.demo.service.RiskGateway"/>
    <property name="serviceName" value="primary"/>
    <property name="nodeBeans">
        <list>
            <value>tradeHandler</value>
            <value>riskNode</value>
        </list>
    </property>
</bean>
```

The generated descriptor records a required service under `serviceName` when
present; otherwise its name is the lower-camel interface simple name
(`RiskGateway` → `riskGateway`).

## How the starter imports the XML

The starter treats the XML as a design contract and generates a conventional
project:

- each `nodeBeans` entry becomes a Java node skeleton under
  `src/main/java/<basePackage>/node/`;
- each declared event type becomes a Java event skeleton under
  `src/main/java/<basePackage>/event/`;
- `constructor-arg ref="..."` and `property ref="..."` become dependency edges;
- `eventHandlers` controls which generated node skeletons get
  `@OnEventHandler` methods for which event types;
- `serviceRegistrations` gives each consuming node a nullable service field and an
  `@ServiceRegistered` callback (the `serviceName`, if any, rides in the annotation
  value); each declared service also gets an interface stub under
  `src/main/java/<basePackage>/service/`;
- beans in `ignoredBeans` are not generated as Fluxtion nodes;
- beans in `eventBeans` are used only to resolve event types and are not graph
  nodes.

The generated nullable service field needs neither `@FluxtionIgnore` nor
`transient`. By default the starter generates registration only: the field can
be null before registration, and a later runtime deregistration leaves the last
reference in place. Enable the starter option **Clear consumed services on
deregistration** (`generateDeregistration=true`) to also generate a matching
filtered/unfiltered `@ServiceDeregistered` callback that clears the field. The
option defaults to `false` and is starter metadata, not a
`FluxtionSpringConfig` XML property.

The starter normalises generated packages to:

```text
<basePackage>.node.<NodeClass>
<basePackage>.event.<EventClass>
```

If the XML uses that convention already, drag/drop import strips the terminal
`.node` and `.event` segments to recover `<basePackage>`. If the XML uses a
different package layout, the starter preserves class simple names and emits the
generated skeletons in its conventional layout.

## Structural validation (no classes required)

The starter validates the dropped XML **before** generating anything, at the **definition
level** — it reads bean definitions as data and never loads or instantiates any referenced
class. (The starter runs in the browser, so it uses its own definition-level reader; the
semantics were verified against Spring 6.x, where `XmlBeanDefinitionReader` into a
`DefaultListableBeanFactory` — without `refresh()`, `getType()` or bean instantiation —
parses definitions even when the referenced classes are **not on the classpath**. That is
exactly the skeleton case: the classes are about to be generated. A JVM-side tool can use
that Spring path directly and match this contract.)

At the definition level the starter can check, with zero user classes present:

1. XML well-formedness and Spring schema validity;
2. unique bean ids;
3. every bean `class` is a readable class-name **string** (`BeanDefinition#getBeanClassName`);
4. the `FluxtionSpringConfig` lists (`nodeBeans`, `eventTypes`, `eventHandlers`) read from the definition as string
   values — no config-bean instantiation;
5. cross-references by name: every `nodeBeans` / `ignoredBeans` / `eventHandlers` node is a declared bean id, and every
   `eventHandlers.event` is one of the declared `eventTypes`;
6. **dangling refs**: every `ref` points at a declared bean. Scan **both** `getConstructorArgumentValues()`
   buckets — `getGenericArgumentValues()` (a `constructor-arg` with no `index`) **and** `getIndexedArgumentValues()` —
   plus property refs;
7. **service rules**: every `serviceRegistrations.service` is declared in `serviceTypes` and its `nodeBeans` are
   declared nodes; no FQCN is both an `eventType` and a `serviceType`; no service interface is declared as a bean; and
   **no node `constructor-arg`/`property ref` resolves to a service-typed bean** ("services must not be
   constructor-wired"). Declared-but-unused `serviceTypes` and `strictServiceBindings` with no bindings are warnings.

What it cannot check without the classes — whether a node actually has an `@OnEventHandler` for the event — is
deliberately deferred: that class-level validation runs later in the generated project, once the stubs are filled and
`FluxtionSpring.compile` runs (compiler ≥ 1.0.62). A "declared event has no handler" condition is a post-generation
build warning, not a drop-time error.

This is why events must be declared as `eventTypes` (inert FQCN strings), never `eventClasses` (see the field table):
strings never force class loading at the definition level.

## Handler expectations are not routing

`eventHandlers` is a validation/design aid. It says, "this Java node should have
a handler for this event." Fluxtion still builds the executable graph by
inspecting Java structure:

- `@OnEventHandler` methods define event entry points;
- `@OnTrigger` methods define reactions to upstream node updates;
- constructor/setter/public-field references define graph dependencies;
- lifecycle and service annotations are discovered by the compiler.

This keeps the XML declarative and inspectable without turning it into a manual
event-routing DSL.

## Good prompt for an LLM

Use this prompt shape when asking an LLM to author XML for the starter:

```text
Create a Spring beans XML document for the Fluxtion starter.

Use standard Spring <beans> XML only.
Include a com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig bean.
Declare event input types with eventTypes using FQCN strings.
Declare graph nodes with nodeBeans using bean ids.
Use eventHandlers with EventHandlerBinding inline beans to say which nodes
handle which declared events.
Use constructor-arg ref or property ref to describe dependencies between nodes.
Declare external services in serviceTypes (FQCN interfaces) and attach them to
consuming nodes with serviceRegistrations / ServiceBinding — never as beans and
never via constructor-arg. Use ignoredBeans only for non-service helper/config
beans.

Generate no Java source in the response; the starter will generate skeleton
classes from the XML.
```

## Minimal complete example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans">
    <bean id="fluxtionSpringConfig"
          class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig">
        <property name="eventTypes">
            <list>
                <value>com.acme.example.event.PriceUpdate</value>
            </list>
        </property>
        <property name="nodeBeans">
            <list>
                <value>priceHandler</value>
                <value>alertNode</value>
            </list>
        </property>
        <property name="eventHandlers">
            <list>
                <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.EventHandlerBinding">
                    <property name="event" value="com.acme.example.event.PriceUpdate"/>
                    <property name="nodeBeans">
                        <list>
                            <value>priceHandler</value>
                        </list>
                    </property>
                </bean>
            </list>
        </property>
    </bean>

    <bean id="priceHandler" class="com.acme.example.node.PriceHandler"/>
    <bean id="alertNode" class="com.acme.example.node.AlertNode">
        <constructor-arg ref="priceHandler"/>
    </bean>
</beans>
```

When dropped onto `/start`, this produces:

```text
src/main/java/com/acme/example/event/PriceUpdate.java
src/main/java/com/acme/example/node/PriceHandler.java
src/main/java/com/acme/example/node/AlertNode.java
src/main/fluxtion/designer/application-context.xml
```

`PriceHandler` gets an `@OnEventHandler` method for `PriceUpdate`.
`AlertNode` gets a constructor dependency on `PriceHandler` and an `@OnTrigger`
method.

## Compatibility fallback

If XML has no `FluxtionSpringConfig`, the starter keeps the older import rule:
every non-framework bean is treated as a Fluxtion node, refs become dependency
edges, and a default `InputEvent` skeleton is generated.

For new LLM-authored XML, always include `FluxtionSpringConfig`; otherwise event
beans and helper beans can accidentally become graph nodes.

## Local authoring workflow and extended declarations

Publication is pending. This workflow requires a compiler release that includes the local starter
tool. The downloaded authoring record names its coordinate; setup reports a clear failure if it
cannot be fetched. Curated examples keep their independently released dependency pins.

Spring AOT downloads include `fluxtion-authoring.json`, `RUNBOOK.md`, `setup.sh`, `validate.sh` and
`generate.sh`. Provision with `setup.sh` once. Edit the XML, run `validate.sh`, then `generate.sh`, inspect
the generated processor and run `run.sh`. Validation is the offline XML tier: it does not load node classes
or guarantee that they compile. Class/model checks happen during the generation build.

Commit the authoring record with the source. It records the XML path, template mode, reconciliation policy,
last-written wiring annotations and original stub hashes. Reconciliation plans every edit before writing;
any conflict preserves source and the record and writes `target/fluxtion-reconciliation.json`. A developer's
implemented body is retained. Existing unowned members are adopted only when they already match and are
never rewritten. Read `target/fluxtion-run.json` to identify stages that ran and their input hashes before
using a validation, reconciliation or compiler report. `validate.sh --summary-detail` includes node edges
and full binding tables. No local server is required.

The builder selects the generation route. An installed local provider works without a key or network;
custom HTTP endpoints use the client's existing authentication; the RapidAPI route requires its subscribed
key and gateway preflight. All dependencies must be provisioned before an offline build.

These additional configuration properties are lists on `FluxtionSpringConfig`. Nested beans use the prefix
`com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.`:

| Property | Nested bean / values | Fields |
|---|---|---|
| `referenceBindings` | `ReferenceBinding` | `ownerBean`, `referencedBean`, `mode` (`TRIGGER` default, `DATA`, `PUSH`), optional `referenceField`, optional `parentUpdateCallback` |
| `exportedServices` | `ExportedService` | `service` (interface FQCN), `nodeBean`, `exportedMethods` (list of signatures) |
| `signalHandlers` | `SignalBinding` | `name`, `nodeBeans` |
| `sinks` | `SinkBinding` | `name`, optional `valueType` FQCN, `nodeBeans` |
| `lifecycleNodes` | bean-id strings | Each listed node gets `@Initialise`, `@Start`, `@Stop`, `@TearDown` stubs |

`EventHandlerBinding` also accepts mutually exclusive `filterString` and integer `filterId`, and boolean
`propagate` (default true). Distinct filters for the same event are legal; duplicate event/filter pairs are
rejected. Filtered event shells implement the runtime `Event` interface and expose a selectable filter
constructor; the example constructor selects the first declared filter. Existing filtered event types must
implement `Event`. Signals generate filtered `Signal<?>` handlers; the host invokes `flow.publishSignal(name)`.
Signal and sink names must be nonempty and unique across the merged configuration.

A reference binding annotates an existing constructor/property reference; it cannot invent an edge.
Owner and target must be selected, different nodes. `referenceField` identifies the owner's field and is
required when an edge has multiple slots. DATA adds `@NoTriggerReference`: the object remains in the graph,
but its dirty flag does not trigger the owner. PUSH adds `@PushReference`: **the owner is the source** and
pushes into the referenced target, whose trigger runs afterwards only when the source propagates.
Prefer natural dependency direction; use PUSH sparingly for fan-in. Repeated bindings sharing a field
produce one list reference (heterogeneous targets use `List<Object>`). A parent-update callback is allowed
only in TRIGGER mode and is typed to the referenced node.

An export generates an interface and an implementing node using the **type-use** form
`class Router implements @ExportService Commands`. `@ExportService` on the class declaration does not
export that interface. Export signatures are `methodName(paramType,...)`, always returning void; parameters
are primitives, FQCNs, arrays, or binary nested names such as `com.example.Outer$Inner`. Duplicate signatures,
multiple exporters of one interface and importing/exporting the same interface are refused. Imported
services appear as REQUIRED in the descriptor; exported commands appear as EXPORTED. The default service
name is the lower-camel interface simple name unless a filter names it.

Sinks generate `SinkPublisher<T>` fields and publish TODOs. This release implements names-only descriptors:
`Sink.typeName` is `java.lang.Object`, even when the publisher has a declared Java value type. An absent
value type beneath the project base package becomes a record stub; external types must already exist.
Lifecycle methods are host-invoked: call `init()` before `start()`, then `stop()` and at most one `tearDown()`.
The runtime does not enforce the at-most-once teardown rule.

For existing classes, XML declarations are checked against actual fields, annotations, interface uses and
methods. The compiler never modifies bytecode; a mismatch reports
`SPRING_DECLARATION_UNSATISFIED_BY_EXISTING_CLASS`. Run reconciliation first where ownership permits it.
A pre-existing exported interface implementation with missing methods is a reconciliation conflict;
the tool will not invent its business methods.
