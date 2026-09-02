# The Spring route builds a graph but I could not enable its audit log

Investigated as a harness variant: declare the graph in Spring XML, let tooling generate the node
shells, and let generation itself be the orchestration proof. **The idea is sound and the wiring half
works. It is blocked on audit.**

## What works

`springToFluxtion` generates a correct processor from a bean file. Verified end to end:

```xml
<bean id="sensorState" class="com.acme.app.SensorState"/>
<bean id="alerter" class="com.acme.app.Alerter">
    <constructor-arg ref="sensorState"/>
</bean>
```
```java
isDirty_sensorState = sensorState.onReading(typedEvent);
  alerter.check();
```

Two things the plugin needs that are not obvious and are undocumented in the design directory:

1. **Spring must be on the *plugin's* classpath**, not the project's — without
   `spring-context`/`spring-beans` as `<dependencies>` of the plugin itself, the goal dies with
   `A required class was missing … org/springframework/context/ApplicationContext`.
2. **Plugin declaration order still matters.** `springToFluxtion` must be declared before the compiler
   execution bound to `process-classes`, exactly as with `scan`.

**The stub generator is ~40 lines and works.** A script reads the bean file and writes a shell class for
every bean whose class is missing, deriving constructor parameters from `constructor-arg ref`. Deleting
a node class and re-running it regenerates a compiling shell. This is the mechanical no-thought
translation the owner described, and it needs no service.

## What blocks it

Enabling the audit log fails. Both documented-looking routes are rejected:

```
Invalid property 'logLevel' of bean class [FluxtionSpringConfig]:
  Bean property 'logLevel' is not writable or has an invalid setter method.
Invalid property 'auditors' of bean class [FluxtionSpringConfig]: … not writable …
```

`javap` on `fluxtion-builder-1.0.66` shows both setters present with matching getters
(`setLogLevel(EventLogControlEvent$LogLevel)`, `setAuditors(List<Auditor>)`), and `LogLevel` is a real
enum, so `value="INFO"` should convert. A `FieldRetrievingFactoryBean` for the constant fails
identically. The most likely explanation is version skew between the class the plugin resolves and the
builder on the project classpath, but I did not confirm it.

**Neither the design directory nor the published docs show how to enable audit from Spring XML** —
searched `spring-authoring/*.md` and the reference XML.

## Why this matters more than a missing feature

Everything the harness has been built on depends on the audit log: `GraphExistsTest` asserts a node ran,
`trace.sh` shows what ran per cycle, and steps 1 and 3 of the build order are checks against it. **On the
Spring route none of that is available**, so the variant cannot be compared against rounds 22–24 on
equal terms — it would be measured without the instrument that produced the gains.

## What I would ask upstream

1. Is `logLevel` intended to be settable from XML in 1.0.66, and if so what is the correct declaration?
2. If audit is meant to be enabled another way on this route, document it beside `addEventAudit`.
3. The plugin needing Spring on its own classpath deserves a line in the authoring docs — it is a hard
   stop with an error that points at Spring rather than at the plugin configuration.
