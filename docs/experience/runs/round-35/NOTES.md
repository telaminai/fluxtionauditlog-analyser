# Round 35 — composition is as simple as it should be, once the components are typed

Two owner questions, both answered by testing rather than argument.

## 1. "The Spring file probably should have no events in it, just nodes"

**Correct.** A bean file containing only nodes and `constructor-arg ref` generates a working processor.
`eventTypes` is not required — Fluxtion finds the handlers from the `@OnEventHandler` annotations on
the nodes it was given.

Tested twice. On the new component graph, a file with **no events at all** generated a processor that
handles `Tick` — 13 references to it in the generated dispatch, none of them declared. And on the
round-26 artifact that scores 12/12, deleting the `eventTypes` block took the file from **86 lines to
67** and it still builds, still passes its 6 tests, and still scores **12/12** on the probes.

So roughly a fifth of that bean file was inert.

## 2. "It should be a very simple thing of adding a few components together"

**It is — once the components expose typed references.** Round 34's components took `Object`
parameters and read each other through a reflective bridge, which is invisible to the generator, and
the agent had to `setAccessible()` a private final field to make the dependency visible at all. That
was my design error, not a framework property.

With typed references the whole integration is four beans and no code:

```xml
<bean id="mid"      class="com.vendor.pricing.PricingComponent.Mid"/>
<bean id="notional" class="com.vendor.risk.RiskComponent.Notional">
    <constructor-arg ref="mid"/>
</bean>
<bean id="adjusted" class="com.vendor.pricing.PricingComponent.Adjusted">
    <constructor-arg ref="mid"/><constructor-arg ref="notional"/>
</bean>
<bean id="score" class="com.vendor.risk.RiskComponent.Score">
    <constructor-arg ref="adjusted"/>
</bean>
```

and the generator derives the interleaving:

```java
isDirty_mid = mid.onTick(typedEvent);   // pricing
  isDirty_notional = notional.calc();   // risk
  isDirty_adjusted = adjusted.calc();   // pricing
  score.calc();                         // risk
```

**Pricing → Risk → Pricing → Risk, from constructor references alone.** Nothing in the bean file says
what order anything runs in, and nothing says what the events are.

## What this changes about round 34

Round 34's Fluxtion arm needed reflection, and I recorded that as a defect in my components. This
confirms it: the same composition, with typed parameters, needs no reflection and no ordering work.
**The reflection was caused by `Object` in a constructor signature I wrote.**

It also sharpens what the vanilla comparison would actually be measuring. Vanilla got the order by
reading `evaluate()` bodies and sorting by hand — a method that needs source. Fluxtion reads
constructor parameter *types*, which survive compilation into a jar. That is the asymmetry the owner
described, and it is still untested, because I have not yet built the jar-only version.

## Two things worth carrying elsewhere

- **`eventTypes` is optional and its absence is cheaper.** Worth stating in the authoring docs; the
  published contract lists it without saying it can be omitted.
- **Component authors should expose typed node references.** An `Object` parameter silently removes a
  dependency from the graph, and the failure mode is a wrong evaluation order rather than an error —
  the silent class this project has repeatedly found to be the expensive one.
