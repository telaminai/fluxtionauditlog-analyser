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

**Evidence that this gap is real.** Six sources, **retrieved 2026-08-30** (review N3: these are live
documents, not a local check — record what was read and when, because they can change under this claim):

| # | Source | States the value-logging contract? |
|---|---|---|
| 1 | `https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt` | no |
| 2 | `https://fluxtion-playground.dev/CLAUDE.md` | no |
| 3 | `https://fluxtion-playground.dev/fluxtion-golden-path.md` | **usage only** — a worked `auditLog.info(k,v).info(k,v)` example and `addEventAudit(LogLevel.INFO)`; never names `EventLogSource`/`setLogger` |
| 4 | `https://fluxtion-playground.dev/spring-authoring/skill.md` | no |
| 5 | `https://fluxtion-playground.dev/spring-authoring/contract.md` | no — `logLevel` and `auditors` appear as config fields, no mechanics |
| 6 | `https://fluxtion-playground.dev/audit-replay` | no, **and it implies the opposite** |

Source 1 is fetched from a mutable branch head; the others are unversioned pages. **Nothing pins these**,
so re-check before relying on the claim.

Every fact below was read from `fluxtion-runtime` 1.0.13 sources and `fluxtion-builder` 1.0.64 —
reproducible offline, unlike the table above.

---

## Draft — *Audit: what the log records, and what your node must do*

The generated processor writes an audit record per event cycle. **Three** conditions decide what is in it,
and collapsing them is the commonest audit mistake — it is easy to conclude that a node's absence proves it
did not run, when in an untraced record it proves only that it said nothing.

### 1 · Three conditions, not two — and only one of them is about your node's type

Read from `fluxtion-runtime` 1.0.13. Getting this wrong overstates what an ordinary record proves.

**a · Registration.** The generated processor registers its graph nodes with each auditor.
`EventLogManager.nodeRegistered(node, nodeName)` builds an `EventLogger` for **every** registered node and
stores it in `node2Logger` **unconditionally**. An invocation of an unregistered node resolves to
`NullEventLogger` and appears nowhere.

**b · Invocation tracing decides whether a node shows up merely for RUNNING.**
`nodeInvoked(...)` calls `logger.logNodeInvocation(traceLevel)`, and that method adds the trace **only
when the configured level admits it**:

```java
public EventLogger logNodeInvocation(LogLevel logLevel) {
    if (this.logLevel.level >= logLevel.level) { logrecord.addTrace(logSourceId); }
    return this;
}
```

`EventProcessorConfig.addEventAudit()` installs `EventLogManager.tracingOff()`; `addEventAudit(level)`
installs `tracingOn(level)`. **So with tracing off, a node that ran and logged no value need not appear at
all** — and its absence is therefore not evidence that it did not run.

**c · `EventLogSource` decides whether the runtime can inject the node's own logger**, and hence whether
it can record its own values:

```java
if (node instanceof EventLogSource) { calcSource.setLogger(logger); ... }
```

Note what this does **not** say: the logger exists either way. What the interface controls is whether your
node gets a handle to it.

**The practical summary.** In a **traced** record, a registered node's presence and position are recorded
by the runtime rather than authored. In an **untraced** record, only what nodes chose to log appears, so
absence means *"said nothing"* and not *"did not run"*. A node that runs and reports no value is
indistinguishable from one that did nothing — which is the reason to implement the contract.

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

### 4 · Turning audit on — and the overload you pick decides what you can prove

Off by default. `LogLevel` is `NONE(0) · ERROR(1) · WARN(2) · INFO(3) · DEBUG(4) · TRACE(5)`, and a node's
message appears only at or below the level in force.

**In the Java graph builder, the choice is which overload you call** —
`EventProcessorConfig.addEventAudit`, whose javadoc reads *"Add an `EventLogManager` auditor to the
generated SEP. Specify the level at which method tracing will take place."*

```java
config.addEventAudit();                 // -> new EventLogManager().tracingOff()   values only
config.addEventAudit(LogLevel.INFO);    // -> new EventLogManager().tracingOn(INFO)
```

**A third case worth knowing, because it fails silently.** The level overload is guarded:

```java
public EventProcessorConfig addEventAudit(LogLevel tracingLogLevel) {
    if (tracingLogLevel != null) {
        addAuditor(new EventLogManager().tracingOn(tracingLogLevel), EventLogManager.NODE_NAME);
    }
    return this;
}
```

So `addEventAudit(null)` — a level read from configuration that turned out absent, say — adds **no
auditor at all**. Not tracing-off: no audit. The build is green, the application runs, and there is no
record. If you configure the level dynamically, check it before you pass it.

**This is the most consequential line in this document.** With tracing **on at build time you get the
propagation path — which nodes ran, and in what order — whether or not any of them logs a message.**
That is what makes the record an account of execution rather than a collection of the messages someone
remembered to write. With the no-arg form you get values only, and a node that logged nothing simply is
not there.

Further overloads take additional flags: `addEventAudit(LogLevel, boolean)` and
`addEventAudit(LogLevel, boolean, boolean)`.

- **Spring XML:** the `logLevel` field on `FluxtionSpringConfig`.
- **At runtime:** `DataFlow.setAuditLogLevel`.

A graph built without audit enabled compiles and runs, and logs nothing. **A green build proves nothing
about whether your node recorded anything** — read the log.

**Downstream consequence worth stating for the reader:** tooling that answers *"which declared nodes never
ran"* is only sound against a traced record. Against an untraced one the same absence means *"logged
nothing"*, which is a much weaker claim.

### 5 · Order in the record is dispatch order

Within one cycle, entries appear in the order the compiler dispatched them: not alphabetical, not
declaration order. **This is the property that makes the record causal** — an entry after another ran after
it, in the same cycle, on the same event. It is derived by the compiler, not observed at runtime, which is
why it is reliable enough to reason from.

### 6 · Triage row, in the existing house style

> **Symptom:** a node runs but the audit record shows only its method name, never the values it computed.
> **Fix:** the node does not implement `EventLogSource`, so the runtime has no way to hand it a logger.
> Extend `EventLogNode`, or implement `setLogger(EventLogger)` and keep the field `transient`. (The
> method-name line you can see comes from invocation tracing, which is a separate setting — it does not
> mean the node can record values.)

---

## The correction `/audit-replay` needs

That page currently says:

> *"Audit and replay are not a logging layer you bolt on. They fall out of how the processor is
> generated."*

**True of a traced record's participation and order; false for values, and false for an untraced record**
— and an author who reads it reasonably concludes no instrumentation is required. Measured consequence: in
a fresh-context run against a real generated bundle, an author copied the shipped example that does **not**
implement the contract, and produced a node that ran and left no evidence of what it did. That is the exact
failure the audit log exists to prevent.

Suggested replacement:

> Audit and replay are not a logging layer you bolt on — **with invocation tracing enabled, which nodes
> fired and in what order** falls out of how the processor is generated. What each node *reports about its
> own state* is one interface away: implement `EventLogSource` (or extend `EventLogNode`) and call
> `auditLog`. Without tracing, a node that logs nothing does not appear, so absence means "said nothing"
> rather than "did not run".

The page is also written for readers and reviewers rather than authors, so it should link to the section
above rather than carry it.
