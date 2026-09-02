# Assembling these components with Fluxtion

## One bean per component

Declare each component you have selected as a bean, using the constructor its manifest entry names.

## Cross-component wiring names the NODE, not the entry point

A component's entry-point class is a **holder**: it constructs the component's nodes and exposes them
as public final fields. It carries no Fluxtion annotations itself, so **it never becomes dirty and
cannot be a trigger parent.** Wiring one component to another through the entry point compiles, runs,
and silently produces a smaller graph.

Reference the publishing node instead:

```xml
<bean id="alpha" class="com.vendor.alpha.AlphaComponent"/>
<bean id="beta"  class="com.vendor.beta.BetaComponent">
    <constructor-arg value="#{alpha.someNode}"/></bean>
```

The field names on an entry point match the figures its manifest lists under `Fluxtion-Provides`.

## Two constructors per entry point

The one its manifest names is **yours**. Each also has a second constructor taking every one of its
node fields; that exists only so the generator can reconstruct the subtree. **Never declare a bean
against it.**

## Reading state back

```java
processor.capital.breachCount.breaches
processor.risk.streak.longest
```
