# Worked example — designing a Spring Fluxtion app with an LLM

A complete walk-through of the flow in `skill.md`, against the output contract in
`contract.md`: a user and an LLM design a small app by conversation, the LLM emits a Spring XML file,
and the starter turns it into a skeleton project. The app: **an order-alerting service** that watches order flow
against live prices and raises an alert on large orders.

---

## The conversation (design partner in action)

**User:** I want a service that listens to orders and price updates, and alerts when a large order comes in for a
symbol whose price just moved a lot.

**LLM:** Good — one input-driven graph. Let me pin the inputs first. Two events, I think:
- `OrderPlaced(orderId, symbol, qty)`
- `PriceUpdate(symbol, price)`

Does that cover the inputs, or is there a reference-data feed too?

**User:** That's it for now.

**LLM:** Then the processing nodes:
- `PriceCache` — remembers the latest price and recent move per symbol (handles `PriceUpdate`).
- `OrderHandler` — on each `OrderPlaced`, checks size and asks `PriceCache` whether that symbol just moved (handles
  `OrderPlaced`, depends on `PriceCache`).
- `Alerter` — turns a flagged order into an alert action (depends on `OrderHandler`).

`Alerter` will call out to something to send alerts — that's an external **service**, not a graph node. What's the
service interface, and does it come in named variants?

**User:** Call it `AlertPublisher`; just one for now.

**LLM:** Good — a single unnamed service. `AlertPublisher` goes in `serviceTypes` and I'll attach it to `alerter` with a
`serviceRegistration` (never a constructor-arg — services are dynamic attachment ports, not graph edges). Here's what
the skeleton will contain: two event records, three node classes wired `PriceCache → OrderHandler → Alerter`, one
service interface, `OrderPlaced` expected on `OrderHandler` / `PriceUpdate` on `PriceCache`, and an `@ServiceRegistered`
callback on `Alerter` for `AlertPublisher`. Emitting the XML.

---

## The emitted XML (`order-alerts.xml`)

Drop this onto the starter.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="fluxtionSpringConfig"
          class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig">

        <property name="eventTypes">
            <list>
                <value>com.acme.orderalerts.event.OrderPlaced</value>
                <value>com.acme.orderalerts.event.PriceUpdate</value>
            </list>
        </property>

        <property name="nodeBeans">
            <list>
                <value>priceCache</value>
                <value>orderHandler</value>
                <value>alerter</value>
            </list>
        </property>

        <!-- External service this design consumes — interface FQCN, never a bean. -->
        <property name="serviceTypes">
            <list>
                <value>com.acme.orderalerts.service.AlertPublisher</value>
            </list>
        </property>

        <property name="eventHandlers">
            <list>
                <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.EventHandlerBinding">
                    <property name="event" value="com.acme.orderalerts.event.PriceUpdate"/>
                    <property name="nodeBeans">
                        <list><value>priceCache</value></list>
                    </property>
                </bean>
                <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.EventHandlerBinding">
                    <property name="event" value="com.acme.orderalerts.event.OrderPlaced"/>
                    <property name="nodeBeans">
                        <list><value>orderHandler</value></list>
                    </property>
                </bean>
            </list>
        </property>

        <!-- Attach AlertPublisher to alerter → generates an @ServiceRegistered callback. NOT a constructor-arg. -->
        <property name="serviceRegistrations">
            <list>
                <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.ServiceBinding">
                    <property name="service" value="com.acme.orderalerts.service.AlertPublisher"/>
                    <property name="nodeBeans">
                        <list><value>alerter</value></list>
                    </property>
                </bean>
            </list>
        </property>
    </bean>

    <!-- Graph nodes; constructor-arg refs are the graph edges (services are NOT here). -->
    <bean id="priceCache"   class="com.acme.orderalerts.node.PriceCache"/>
    <bean id="orderHandler" class="com.acme.orderalerts.node.OrderHandler">
        <constructor-arg ref="priceCache"/>
    </bean>
    <bean id="alerter" class="com.acme.orderalerts.node.Alerter">
        <constructor-arg ref="orderHandler"/>
    </bean>
</beans>
```

**Why it validates without any classes present:** the starter reads this with Spring's `XmlBeanDefinitionReader` at
the *definition* level — it never loads `com.acme.orderalerts.*`. It checks: well-formed XML, unique ids, every
`ref` (`priceCache`, `orderHandler`) resolves to a declared bean, every `eventHandlers` node names a declared bean, and
every binding `event` is one of the `eventTypes` strings. The **service rules** pass too: `AlertPublisher` is declared
in `serviceTypes`, the `ServiceBinding` names it + a declared node (`alerter`), `AlertPublisher` is *not* a bean, and no
node `constructor-arg`s it. Because events and services are inert **strings**, reading `fluxtionSpringConfig` does not
force class loading.

> **Name-filtered variant.** If there were two alert channels, you'd declare `AlertPublisher` once in `serviceTypes` and
> add two bindings with a `<property name="serviceName" value="email"/>` (and `"pager"`) — each generates an
> `@ServiceRegistered("email")` / `@ServiceRegistered("pager")` callback and a distinct field. Unnamed (as above) means
> "any instance of the interface."

---

## What the starter generates (the skeleton)

```text
order-alerts/
├── pom.xml
├── src/main/fluxtion/designer/application-context.xml   (regenerated — round-trips the design incl. serviceRegistrations)
└── src/main/java/com/acme/orderalerts/
    ├── event/
    │   ├── OrderPlaced.java        (record stub — fields to confirm)
    │   └── PriceUpdate.java        (record stub)
    ├── node/
    │   ├── PriceCache.java         (@OnEventHandler onPriceUpdate — TODO body)
    │   ├── OrderHandler.java       (ctor PriceCache; @OnEventHandler onOrderPlaced — TODO)
    │   └── Alerter.java            (ctor OrderHandler; @OnTrigger; @ServiceRegistered AlertPublisher — TODO)
    └── service/
        └── AlertPublisher.java     (service interface stub)
```

> Note the singular role packages (`event/`, `node/`, `service/`) — the starter only recognises the singular form, so
> author the XML FQCNs the same way (`…event.OrderPlaced`, not `…events.OrderPlaced`) or the generated tree won't match.
> And the dropped XML is regenerated as `application-context.xml`, which round-trips the full contract (nodes, events,
> **and** `serviceRegistrations`) — that regenerated file is what the build compiles.

Generated stubs (the human fills the `TODO`s):

```java
// event/OrderPlaced.java
package com.acme.orderalerts.event;
public record OrderPlaced(String orderId, String symbol, int qty) {}

// event/PriceUpdate.java
package com.acme.orderalerts.event;
public record PriceUpdate(String symbol, double price) {}
```

```java
// node/PriceCache.java
package com.acme.orderalerts.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.acme.orderalerts.event.PriceUpdate;

public class PriceCache {
    @OnEventHandler
    public boolean onPriceUpdate(PriceUpdate update) {
        // TODO: store latest price / recent move for update.symbol()
        return true;
    }
    // TODO: query method OrderHandler can call, e.g. boolean movedSharply(String symbol)
}
```

```java
// node/OrderHandler.java
package com.acme.orderalerts.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.acme.orderalerts.event.OrderPlaced;

public class OrderHandler {
    private final PriceCache priceCache;
    public OrderHandler(PriceCache priceCache) { this.priceCache = priceCache; }

    @OnEventHandler
    public boolean onOrderPlaced(OrderPlaced order) {
        // TODO: flag if order.qty() is large AND priceCache.movedSharply(order.symbol())
        return true;
    }
}
```

```java
// node/Alerter.java — downstream node: @OnTrigger fires after OrderHandler updates.
// Without @OnTrigger a dependent node never fires; the starter always generates it.
package com.acme.orderalerts.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.acme.orderalerts.service.AlertPublisher;

public class Alerter {
    private final OrderHandler orderHandler;
    public Alerter(OrderHandler orderHandler) { this.orderHandler = orderHandler; }

    /** Runtime service — may be null until registered. */
    private AlertPublisher alertPublisher;

    @ServiceRegistered
    public void alertPublisher(AlertPublisher service) {
        this.alertPublisher = service;
    }

    @OnTrigger
    public boolean onAlerter() {
        // TODO: alertPublisher may be null here (service not yet registered).
        //       Decide: queue, drop-and-record, or fail.
        // TODO: on a flagged order from orderHandler, alertPublisher.publish(alert);
        return true;
    }
}
```

The default skeleton deliberately has no deregistration callback. If the user
enables **Clear consumed services on deregistration**
(`generateDeregistration=true`) in the starter, it additionally imports
`ServiceDeregistered` and emits:

```java
@ServiceDeregistered
public void alertPublisherRemoved(AlertPublisher service) {
    this.alertPublisher = null;
    // TODO: decide behavior for in-flight work when this service disappears
}
```

Only that opt-in form changes the handler guard to “service not yet registered
/ deregistered.”

```java
// service/AlertPublisher.java  (service interface stub)
package com.acme.orderalerts.service;

/** Service boundary — implemented OUTSIDE the graph and registered at runtime
 *  (flow.registerService(...)); injected into Alerter via @ServiceRegistered. */
public interface AlertPublisher {
    // TODO: declare the action method(s) the node calls, e.g. void publish(String alert);
}
```

---

## What happens after the human fills the stubs

- The generated project builds `FluxtionSpring.compile(...)` (or the AOT builder) over the XML. **Now** the classes
  exist, so the design extension's **class-level handler validation** runs: it confirms `PriceCache` really has an
  `@OnEventHandler(PriceUpdate)` and `OrderHandler` an `@OnEventHandler(OrderPlaced)` — the expectations become
  enforced, not just declared. A binding whose node has no matching handler now fails with a clear error.
- Fluxtion topo-sorts the constructor graph (`priceCache → orderHandler → alerter`) and generates the deterministic
  dispatch. The running app has no LLM in it.

---

## What this example demonstrates

- The LLM is a **design partner** that produced a scaffold, not the app's logic.
- The XML is **structurally valid with zero classes present** — the starter validates definitions, not instances,
  which is exactly why drag-and-drop works.
- `eventTypes` strings (not `eventClasses`) are what make that possible in this flow.
- Handler *expectations* declared at design time become **enforced** once the stubs are filled and compiled — the
  design contract and the runtime graph converge.
- **Services are dynamic attachment ports, not graph edges** — `AlertPublisher` is declared in `serviceTypes` and
  attached with a `ServiceBinding` (generating an `@ServiceRegistered` callback + an interface stub), never
  constructor-wired. Constructor-args stay reserved for the deterministic node topology.
- Same node/event/service vocabulary as FSQL — one mental model, two authoring surfaces.
