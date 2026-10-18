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
| `serviceRegistrations` | list of `ServiceBinding` inline beans | Attach a service to the node(s) that consume it. Each binding: `service` (an FQCN from `serviceTypes`), `serviceName` (the name the service is registered under — becomes the `@ServiceRegistered("name")` value; optional, defaulting to the lower-camel interface simple name, but always set it), and `nodeBeans` (the consuming nodes). The starter generates the `@ServiceRegistered` callback + a service-interface stub. |
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

That is the binding's one name, and the starter writes it out: the generated
callback always carries it — `@ServiceRegistered("primary")` above, and
`@ServiceRegistered("riskGateway")` for a binding with no `serviceName`. A service
reaches the callback only when it is registered under exactly that name, so in a
Mongoose `server-config.yml` the service entry's `name:` must equal it.

> **Always name services explicitly** — a `serviceName` on every `ServiceBinding`,
> and the same value as the Mongoose service's `name:`. An *unnamed*
> `@ServiceRegistered` in hand-written code does not fall back to the lower-camel
> name: the runtime keys it by the parameter type's canonical name
> (`com.acme.demo.service.RiskGateway`), which never matches `name: riskGateway`,
> and the callback silently never fires.

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
`@ServiceDeregistered` callback, carrying the same name, that clears the field. The
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

This workflow uses the starter tool named in the downloaded authoring record. Spring-side
callback auditing and reconciliation follow-ups require starter 1.0.74 or later. Setup reports
a clear failure if that coordinate cannot be fetched. Curated examples keep their independently
released dependency pins.

Spring AOT downloads include `fluxtion-authoring.json`, `RUNBOOK.md`, `setup.sh`, `validate.sh` and
`generate.sh`. Provision with `setup.sh` once. Edit the XML, run `validate.sh`, then `generate.sh`, read
the compiler's messages (not the generated processor) and use the emitted launcher: `run.sh` for standalone, `run-server.sh` for
hosted. If no launcher was selected, the runbook declines to invent one. Validation is the offline XML
tier: it does not load node classes or guarantee that they compile. Class/model checks happen during
the generation build.

Hosted Spring downloads include the same authoring record and local commands. The analyser bundle's first
run still uses `./mvnw package` and its shipped processor, without setup or a compilation key. Only
changing its graph needs setup/validate/generate and the selected generation backend. Its RUNBOOK is
linked from the project profile alongside the ordinary host operations.

Extended declarations require AOT in the download workflow. Interpreted/in-process downloads with
extended declarations are refused before ZIP creation, naming the required starter coordinate; legacy
interpreted XML remains supported. Spring AOT accepts only the matching pinned starter/BOM pair.
A different user BOM override is refused with both versions named, rather than leaving the record and
tool on another version. Remove the override or use the displayed matching version. Non-Spring version
overrides are unchanged.

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
| `referenceBindings` | `ReferenceBinding` | `ownerBean`, `referencedBean`, `mode` (`TRIGGER` default, `DATA`, `PUSH`), optional `referenceField`, optional `parentUpdateCallback` (`true`/`false`, default `false`) |
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
only in TRIGGER mode and is typed to the referenced node: `parentUpdateCallback` `true` generates
`@OnParentUpdate("<field>")`, which tells a node with several trigger parents which one changed.

An export generates an interface and an implementing node using the **type-use** form
`class Router implements @ExportService Commands`. `@ExportService` on the class declaration does not
export that interface. Export signatures are `methodName(paramType,...)`, always returning void; parameters
are primitives, FQCNs, arrays, or binary nested names such as `com.example.Outer$Inner`. Duplicate signatures,
multiple exporters of one interface and importing/exporting the same interface are refused. Imported
services appear as REQUIRED in the descriptor; exported commands appear as EXPORTED. The default service
name is the lower-camel interface simple name unless a filter names it; the generated
`@ServiceRegistered` callback carries that name explicitly, and a host must register the service
under it (see *Naming rules for LLM output* above — always name services).

Sinks generate `SinkPublisher<T>` fields (`sink<Name>`, publishing under the binding's `name`) and publish
TODOs. Declare an output here, not with a hand-written `@ServiceRegistered` callback: the host connects a
destination registered under the same name (in Mongoose, an `eventSinks` entry whose `name:` equals it). This release implements names-only descriptors:
`Sink.typeName` is `java.lang.Object`, even when the publisher has a declared Java value type. An absent
value type beneath the project base package becomes a record stub; external types must already exist.
Lifecycle methods are host-invoked: call `init()` before `start()`, then `stop()` and at most one `tearDown()`.
The runtime does not enforce the at-most-once teardown rule.

For existing classes, XML declarations are checked against actual fields, annotations, interface uses and
methods. The compiler never modifies bytecode; a mismatch reports
`SPRING_DECLARATION_UNSATISFIED_BY_EXISTING_CLASS`. Run reconciliation first where ownership permits it.
A pre-existing exported interface implementation with missing methods is a reconciliation conflict;
the tool will not invent its business methods.

### Callback auditing (starter 1.0.74 and later)

The generated-callback policy is INFO: an event handler records `handledEvent` (the incoming
class name) and any incoming `filterId`/`filterString`; a trigger records `triggerFired: true`;
a lifecycle method records its `lifecycle` phase. An unfinished stub emits the same facts.
They show execution, not correct business state. Keep or add explicit audit values when implementing
a decision, and enable INFO in the graph's audit configuration. Existing sample value logging stays.
The policy does not change propagation returns or rewrite implemented callback bodies.

Fresh browser classes use `EventLogNode`. The local tool preserves
an existing superclass and adds owned `EventLogSource.setLogger(EventLogger)` injection where needed,
initially using `NullEventLogger`. Developer-owned injection collisions refuse rather than overwrite.
Older tools through 1.0.73 do not add these callback facts; their newly added nodes need manual
audit support. Existing implemented bodies are not retrofitted: preserve their intended logging
or add the required execution/state facts yourself.

### Generated guidance and ownership

The starter's decision-point comments explain the construct and link this contract without claiming
which propagation or lifecycle choice is currently selected. Reconciliation preserves developer prose;
changing DATA/TRIGGER or a callback declaration must not leave a comment asserting the old choice.
Comment tokens are excluded from ownership hashes, which checks content ownership, not the truth of
an explanation. Follow the XML declaration and the relevant callback contract when implementing logic.
A DATA reference remains a mutable Java reference; a callback returning false does not prove that every
other route through the graph is inactive. Useful audit evidence requires the node's logging calls and
the runtime audit configuration, not only a base class.

New journey downloads include runbooks/comment-contract.json and its manifest, with the applicable
starter coordinate and resource digest. Released downloads identify a verified public artifact;
a manifest identifying local development is not evidence of public availability.

### Whole-type ownership and reconciliation (starter 1.0.74 and later)

The tool records whole-type ownership, protects dependency-only classes from
shadowing shells using the classpath written by setup, resolves withdrawn reference fields by type,
preserves indentation/CRLF and reports independent declaration failures with bean identities.
Existing source files and older records without whole-type baselines are
never made disposable; edited or still-referenced types are retained. This does not hash dependency
artifacts in the run receipt or establish their behaviour.

Whole-type entries carry `type:<fully-qualified-class-name>`;
older bare `type:` entries migrate without changing the file baseline. Withdrawal
checks only these authoring inputs: the effective source root and conventional `src/`
tree (including tests, Kotlin, resources, service descriptors and design files); root
`pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`,
`gradle.properties`; source/test/resource and build-helper roots declared in the local
POM; the effective authoring XML; and emitted `config/server-config.yml`.
Local POM paths/properties and `basedir`/`project.basedir` are read without executing
a build. Unresolved or escaping paths refuse. External-parent roots, arbitrary plugins
and Gradle source-set evaluation are not resolved: place such inputs in `src/`, the
effective source root or supported local POM declarations before using automatic
whole-type deletion. All files within declared roots participate, including legacy
resource text. Unrelated runtime `audit/`, `logs/` and `data/` trees do not participate
unless explicitly declared as inputs. Their output writes and symlinks cannot block
regeneration or retain a withdrawn type. Symlinks within inputs still refuse. Changes
to the allow-listed inputs between planning and application refuse an outdated plan;
per-run source-root and XML overrides stay part of that snapshot.

Newly emitted filter-string audit fields (including signal names) are **altered for
display**: CR/LF become spaces, then every character outside ASCII letters, digits,
underscore, dot, space, slash, `@`, `+` and `-` becomes `_`. Commas, colons, braces,
brackets, parentheses, quotes and backslashes therefore cannot become flow-map
syntax or hide the next entry. Unicode characters outside that set are altered too;
this representation is not a lossless event value. Different original values can produce
the same displayed text, and the record does not yet mark substitutions. Blank-only values
and edge spaces also have a known reader limitation; do not use this display field as a
lossless filter identity. This interim guard applies only
to generated node fields. Durable runtime escaping of arbitrary node-field values
and event metadata remains open at [mongoose-plugins#39](https://github.com/telaminai/mongoose-plugins/issues/39). Existing implemented bodies stay owned
by their author. Interface-default logger callbacks are developer code too; a
conflicting logger addition refuses instead of silently overriding them.

### Withdrawing a node or reference (starter 1.0.79 and later)

**Withdrawing a node or reference the starter generated.** Remove it from the XML (the bean and its
`nodeBeans` entry, or the `constructor-arg`/`property` ref). Then run `./generate.sh`. If reconcile refuses
with `SPRING_RECONCILE_CONFLICT` because `fluxtion-authoring.json` still records it, run the command the
`fix:` line prints: `java -jar .fluxtion/fluxtion-starter-core.jar withdraw --class '<fully.qualified.Class>'`
for a whole class, or add `--member '<key>'` (for example `--member 'field:rootNode'`) for one member. Then
re-run. `withdraw` edits only the ownership record, never Java source. After withdrawing a member, remove that field and its constructor parameter or setter from the Java yourself, as the `fix:` line says. It refuses a class the XML still
declares unless you pass `--still-declared`. Do not hand-edit `fluxtion-authoring.json`.
