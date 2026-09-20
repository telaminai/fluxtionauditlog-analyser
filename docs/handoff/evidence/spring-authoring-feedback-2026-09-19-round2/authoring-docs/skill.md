# Skill — design a Spring Fluxtion app with an LLM

**Use this when** a user wants to scaffold a Spring-defined Fluxtion application by conversation and get a runnable
skeleton from the starter. You act as a **design partner**: discuss the app, then emit one Spring XML file the user
drops on the starter.

**Read alongside:**
- `contract.md` (this folder) — the **output contract**: the exact XML shape, the `FluxtionSpringConfig`
  field table, naming rules, and how the starter imports/validates it. That file is authoritative for *what to emit*.
- [`claude.txt`](https://telaminai.github.io/fluxtion/claude.txt) — the **core model**: nodes, events, the
  compile-time graph, constructor-wiring, determinism, and the `@OnEventHandler` stub shapes. Authoritative for
  *how Fluxtion works*.

This skill is the **procedure** on top of those two. It does not restate the XML shape (see the contract) or the
Fluxtion model (see `claude.txt`).

> Requires the starter backed by compiler ≥ 1.0.62 (the `FluxtionSpringConfig` design extension); service
> registration (`serviceTypes` / `serviceRegistrations`) needs ≥ 1.0.63.

## The one-minute framing

You are scaffolding structure, not writing logic. The classes you name **do not need to exist** — the starter creates
them as stubs the user fills in. The running app is deterministic Java with no LLM in it; you are a design-time partner
producing a reviewable scaffold.

Vocabulary (shared with FSQL): **event** = an input contract (a Java type entering the system, declared in
`eventTypes`); **node** = a stateful graph component that reacts to events (a bean in `nodeBeans`); **imported service** = an
external boundary a node calls out to — declared in `serviceTypes` and attached to its consuming node(s) via
`serviceRegistrations` (the starter generates an `@ServiceRegistered` callback); **handler expectation** = a
design-time "event E should be handled by node N" (`eventHandlers`), validation only — Fluxtion still infers real
dispatch from `@OnEventHandler`.

Three relationships, never confused (full detail in the contract): `constructor-arg ref` = graph topology · a
`ServiceBinding` = a dynamic service attachment (never a constructor-arg) · `eventTypes`/`eventHandlers` = inbound
facts.

## The conversation flow

Drive the user through these, one or two at a time — do not dump a form. Confirm before emitting.

1. **What does the app do?** One sentence → names the base package.
2. **What events come in?** → the `eventTypes` list, and each event's fields (for the record stub).
3. **What nodes process them, and which node handles which event?** → `nodeBeans` + `eventHandlers` bindings.
4. **What are the dependencies between nodes?** → the `constructor-arg ref` edges (these ARE the graph wiring —
   `new B(a)` means B depends on a; see `claude.txt`).
5. **What external services does a node call out to?** (publishers/gateways/stores) → for each, the interface FQCN, an
   optional name filter if there are several instances, and which node(s) consume it → `serviceTypes` +
   `serviceRegistrations`. **Not** beans, **not** `constructor-arg`, **not** `ignoredBeans`. Ask whether generated
   nodes should clear service fields on runtime deregistration; this is the starter's opt-in
   `generateDeregistration` option and defaults to false.
6. **Emit the XML** per the contract, then **restate in plain English** what the skeleton will contain (including that
   services become `@ServiceRegistered` callbacks + interface stubs) so the user can confirm before the drop.

## Rules that keep the XML valid (summary — full detail in the contract)

- Events are **`eventTypes` (FQCN strings)**, never `eventClasses` (which would force class loading on not-yet-created
  classes). This is the single most important rule for the skeleton flow.
- Every node is a `<bean>` listed in `nodeBeans`; support/config beans that must not be nodes go in `ignoredBeans`.
- Every `eventHandlers` binding references a declared event (an `eventTypes` FQCN) and declared node bean ids.
- Services: declare the interface FQCN in `serviceTypes`, attach with a `ServiceBinding` (`service` ∈ `serviceTypes`,
  `nodeBeans` ⊆ declared nodes). Never a service `<bean>`, never a `constructor-arg`/`property ref` to a service.
- Unique bean ids; every `ref` points at a declared bean; a bean is exactly one role.

Follow these and the drop passes the starter's structural validation (well-formedness, unique ids, name cross-refs,
dangling-ref check) with no classes present. Handler-compatibility is checked later, in the generated project.

## The stub shapes you describe

Tell the user the shape each generated stub takes, so the project compiles and the handler expectations become real
once filled (details and annotations in `claude.txt`):

- **Event** → a plain record: `public record OrderPlaced(String orderId, String symbol, int qty) {}`
- **Node** → a class with a constructor taking its dependencies, and one `@OnEventHandler` per handled event returning
  `boolean` (true = propagate):

  ```java
  public class OrderHandler {
      private final PriceCache priceCache;
      public OrderHandler(PriceCache priceCache) { this.priceCache = priceCache; }
      @OnEventHandler
      public boolean onOrderPlaced(OrderPlaced order) { /* TODO */ return true; }
  }
  ```
- **Downstream node** (depends on other nodes, no event of its own) → the constructor plus an `@OnTrigger` method,
  which fires after an upstream dependency updates. Without `@OnTrigger` a dependent node **never fires** — the
  starter always generates it:

  ```java
  public class Alerter {
      private final OrderHandler orderHandler;
      public Alerter(OrderHandler orderHandler) { this.orderHandler = orderHandler; }
      @OnTrigger
      public boolean onAlerter() { /* TODO: react to updated upstream state */ return true; }
  }
  ```
- **Service** → an **interface** (stubbed under `<base>.service`) with the action/query method(s) the node calls. The
  node gets a nullable field + an `@ServiceRegistered` callback (generated); the implementation is registered at runtime
  outside the graph. By default the generated handler says the field can be `null` only before registration; runtime
  deregistration leaves the last reference in place. If the user enables `generateDeregistration`, the starter also
  emits a matching `@ServiceDeregistered` callback that clears the field, and the handler guard covers both absence
  cases. Decide queue/drop/fail.

## Do / don't

- **Do** use `eventTypes` strings; **don't** use `eventClasses` in starter XML.
- **Do** list every node in `nodeBeans`; **don't** rely on "everything is a node".
- **Do** declare graph edges as `constructor-arg ref`; **don't** hand-wire dispatch — Fluxtion infers it.
- **Do** attach services with `serviceRegistrations`; **don't** put a service in `ignoredBeans`, declare it as a
  `<bean>`, or `constructor-arg`-wire it — that's the single most common mistake and a validation error.
- **Don't** put business logic in the XML; it scaffolds structure. Logic lives in the filled stubs.
- **Don't** claim a node handles an event you didn't give it an `@OnEventHandler` for — the design expectation must be
  honoured by the filled stub, or the post-generation build flags it.
- **Don't** invent FQCNs of "existing" library classes you can't verify; prefer creating new ones in the app's package.

## Definition of done

A single well-formed Spring `<beans>` XML that: includes one `FluxtionSpringConfig` bean; declares events as
`eventTypes`; lists nodes in `nodeBeans`; expresses node dependencies as `constructor-arg ref`; and passes the
starter's structural validation. Then a plain-English summary of what the skeleton will contain.

See `example.md` for a complete worked run.

## Re-enter a downloaded project (local workflow; publication pending)

Read its `RUNBOOK.md`, `fluxtion-authoring.json`, XML and node sources first. The project is the state between
sessions. Run `validate.sh` after editing XML, repair ERROR diagnostics, then run `generate.sh`. A reconciliation
conflict leaves source unchanged; resolve it from `target/fluxtion-reconciliation.json`. Check the run receipt
before reading compiler diagnostics, since validation, reconciliation or preflight may have stopped the build.

When discussing boundaries, distinguish imported services (calls out), exported commands (calls into the graph)
and named sinks (fire-and-forget output). Elicit control verbs separately from data events: simple no-payload
verbs can be signals. Ask whether each reference should trigger the owner or only supply data. Prefer natural
trigger direction; explain each exceptional PUSH edge explicitly. Offer parent callbacks when multiple parents
need distinct handling. Use the exact declarations and version requirements in `contract.md`.

Finish with **verify**, after generation:

1. Compare declared inputs, node edges and binding tables with `validate.sh --summary-detail`.
2. Read the generated dispatch: handler filters match the declaration; a non-propagating path does not trigger
   dependents; DATA contributes no dependency guard; a PUSH target runs after its source and obeys its guard.
3. Check descriptor REQUIRED/EXPORTED services and named sinks. Sink descriptors currently report Object;
   do not infer typed descriptor support from a parameterised Java field.
4. Exercise one event, one declared signal and one exported command, plus host lifecycle calls. Compare observed
   order and output with the design. Compilation alone does not prove dispatch semantics.
5. On failure, use the diagnostic code and suggested fix. Correct this runbook or project wiring where appropriate;
   do not introduce a design-time server to replace this filesystem-and-script workflow.
