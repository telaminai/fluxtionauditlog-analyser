# Proposed upstream content — how a node participates in the audit log

**What this is.** Drafted content for the **static authoring resources**, not for this repo. Owner-directed
2026-08-30: *"We have static resources that help author… your improvements should be aimed at making the
content of these files better."* This is the one subject those resources do not cover, and it is the
subject the whole downstream toolchain reads.

**Where it goes**, in priority order:

| File | Why |
|---|---|
| `fluxtion-playground.dev/CLAUDE.md` | the canonical author orientation an LLM is told to load first; add as a section, plus one row in *Source-gen annotation triage* |
| `telaminai/fluxtion` `docs/claude.txt` | the framework canon (UP-FLX-35) |
| `fluxtion-playground.dev/audit-replay` | **correct** the standing claim — see the note at the end |
| `fluxtion-playground.dev/spring-authoring/contract.md` | one line under `logLevel`, which is where the Spring author turns audit on |

**Evidence that this gap is real:** checked 2026-08-30 across `claude.txt`, the playground `CLAUDE.md`,
`spring-authoring/skill.md`, `spring-authoring/contract.md` and `/audit-replay`. **None of the five states
how a node emits a value into the audit log.** Every fact below was then read from
`fluxtion-runtime` 1.0.13 sources and `fluxtion-builder` 1.0.64, not inferred.

---

## Draft — *Audit: what the log records, and what your node must do*

The generated processor writes an audit record per event cycle. Two different mechanisms decide what is in
it, and confusing them is the commonest audit mistake.

### 1 · Appearance is automatic. Values are not.

**Every node that is dispatched appears in the record**, whatever its type — the processor calls the
auditor's `nodeInvoked(node, nodeName, methodName, event)` at each dispatch site. You get the node's name
and the method that ran, for free, with no code.

**Only a node that implements `EventLogSource` can record its own values.** The auditor hands out loggers
in `nodeRegistered(Object node, String nodeName)`, and it does so under exactly one test:

```java
if (node instanceof EventLogSource) { ... setLogger(...) ... }
```

So a node with no logger still shows that it *ran* and can never show *what it computed* — and a node that
runs and reports no value is indistinguishable from one that did nothing.

### 2 · The contract is an interface — one method

```java
public interface EventLogSource {
    void setLogger(EventLogger log);
}
```

Three ways to satisfy it, all equal:

```java
// 1. extend the convenience class — it implements the interface and holds the field
public class PriceAlert extends EventLogNode { … }

// 2. implement it — when your inheritance slot is already taken
public class RiskLimits extends AbstractRiskRule implements EventLogSource {
    private transient EventLogger auditLog;                    // transient: node-local state
    @Override public void setLogger(EventLogger log) { this.auditLog = log; }
}

// 3. nothing at all — a Fluxtion base already does it
public class Position extends SingleNamedNode { … }            // extends EventLogNode
```

The framework calls `setLogger` for you; never construct an `EventLogger`.

**Prefer the interface whenever a domain base class exists.** Java gives a class one inheritance slot, and
documentation that offers only route 1 leads authors to contort a hierarchy or conclude their node cannot
be audited.

### 3 · Logging a value

```java
auditLog.info("breached", breached)           // fluent — every call returns the logger
       .info("limit", limit);
```

Overloads exist for `String`, `boolean`, `int`, `long`, `double`, `char` and `Object`, at five levels:
`error`, `warn`, `info`, `debug`, `trace`. **Use the typed overload rather than stringifying** — a number
that stays a number is queryable and graphable downstream; one wrapped in a string is not.

**Calling `auditLog` when audit is off is safe.** The field defaults to `NullEventLogger.INSTANCE`, so it
is a no-op, never an NPE. Instrument freely; do not guard the calls.

### 4 · Turning audit on

Off by default. `LogLevel` is `NONE(0) · ERROR(1) · WARN(2) · INFO(3) · DEBUG(4) · TRACE(5)`, and a node's
message appears only at or below the level in force.

- **Spring XML:** the `logLevel` field on `FluxtionSpringConfig`.
- **Builder/DSL:** `EventProcessorConfig.addEventAudit`, and `DataFlow.setAuditLogLevel` at runtime.

A graph built without audit enabled compiles and runs, and logs nothing. **A green build proves nothing
about whether your node recorded anything** — read the log.

### 5 · Order in the record is dispatch order

Within one cycle, entries appear in the order the compiler dispatched them: not alphabetical, not
declaration order. **This is the property that makes the record causal** — an entry after another ran after
it, in the same cycle, on the same event. It is derived by the compiler, not observed at runtime, which is
why it is reliable enough to reason from.

### 6 · Triage row, in the existing house style

> **Symptom:** a node runs but the audit record shows only its method name, never the values it computed.
> **Fix:** the node does not implement `EventLogSource`. Extend `EventLogNode`, or implement
> `setLogger(EventLogger)` and keep the field `transient`. Appearance in the record is automatic;
> recording values is not.

---

## The correction `/audit-replay` needs

That page currently says:

> *"Audit and replay are not a logging layer you bolt on. They fall out of how the processor is
> generated."*

**True for appearance, false for values** — and an author who reads it reasonably concludes no
instrumentation is required. Measured consequence: in a fresh-context run against a real generated bundle,
an author copied the shipped example that does **not** implement the contract, and produced a node that ran
and left no evidence of what it did. That is the exact failure the audit log exists to prevent.

Suggested replacement:

> Audit and replay are not a logging layer you bolt on — **which nodes fired, in what order** falls out of
> how the processor is generated. What each node *reports about its own state* is one interface away:
> implement `EventLogSource` (or extend `EventLogNode`) and call `auditLog`.

The page is also written for readers and reviewers rather than authors, so it should link to the section
above rather than carry it.
