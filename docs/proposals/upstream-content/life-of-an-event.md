# The life of a single event

*Every hook point a Fluxtion processor offers, in the order it fires.*

**Target** `fluxtion` docs — a reference page · **Status** draft for upstream

## Why this page exists

The two-phase execution model is documented — in the javadoc of `@OnParentUpdate`, which is not where
anyone looks for it. The rest is spread across seven annotations, three interfaces and a Velocity
template, and the only way to assemble it is to read generated source.

The cost of that is measurable, and it is not that people write slow code. It is that **they build
things the framework already has.**

* This project wrote an external effect drain — the host pulled a queue off a node after `onEvent` and
  performed the effects itself — and defended it in a design document with three reasons. Two were
  wrong. The third was that nobody had read `BatchHandler`. It is now four lines of `@OnBatchEnd`.
* An independent LLM author, on a different application, hit the same wall one step earlier: hand-rolled
  a read-then-write ordering dance, then found `@AfterEvent` and reported *"it was more correct than what
  I had written: my version had a case I had not thought of."* Same arc, same cause, and that author
  stopped at `@AfterEvent` without finding `@OnBatchEnd` either.
* Sibling dispatch order is unpublished, and **four of six independent sessions inferred the wrong rule**
  (it is natural order of node name, not declaration order).

Three different authors, three different applications, one missing page. Every wrong turn was a
*sequencing* fact, not a semantic one — and sequencing is the thing this framework is actually certain
about, because it compiles it.

---

## The timeline

One event, from `onEvent` to the flags resetting. Everything below is read from emitted source and from
`javaTemplate.vsl`, not inferred.

```
onEvent(event)
│
└─ processEvent(event)
   ├─ if (buffering)  triggerCalculation()
   ├─ if (processing) queueReentrantEvent(event) ─── RETURN. Not dispatched now.
   │                  (a cycle is already running; this goes to the BACK of the callback queue)
   └─ else
      processing = true
      │
      ├─ onEventInternal(event)
      │  └─ handleEvent(TypedEvent)          one per event type, selected by instanceof
      │     │
      │     ├─ auditEvent(event)             every Auditor.eventReceived(event)
      │     │
      │     │  ═══ EVENT-IN PHASE — topological order ═══
      │     │
      │     ├─ auditInvocation(node, …)      Auditor.nodeInvoked() — fires BEFORE the node runs
      │     ├─ node.@OnEventHandler(event)   returns boolean → sets isDirty_node
      │     ├─ child.@OnParentUpdate(parent) per updated parent, BEFORE this node's @OnTrigger
      │     ├─ if (guardCheck_x())           guard = OR of x's parent dirty flags
      │     │      x.@OnTrigger()            at most once per cycle, returns boolean → isDirty_x
      │     ├─ …                             down the topological order, each guarded
      │     │
      │     │  ═══ AFTER-EVENT PHASE — reverse topological order ═══
      │     │
      │     └─ afterEvent()
      │        ├─ FirstAfterEvent auditors' processingComplete()
      │        ├─ node @AfterEvent  / @AfterTrigger methods
      │        ├─ ordinary auditors' processingComplete()   ◀── THE AUDIT RECORD PUBLISHES HERE
      │        ├─ reset dirty flags
      │        └─ reset fork tasks
      │
      ├─ callbackDispatcher.dispatchQueuedCallbacks()
      │     anything re-dispatched during the cycle runs HERE, each as a full cycle
      │     with its own auditEvent → dispatch → afterEvent → own audit record
      │
      └─ processing = false
```

## Every hook, in firing order

| # | Hook | Fires | Use it for |
|---|---|---|---|
| 1 | `Auditor.eventReceived` | once, before any node | starting a record for this cycle |
| 2 | `Auditor.nodeInvoked` | before **each** node method | tracing; requires `auditInvocations() == true` |
| 3 | `@OnEventHandler` | on the node that declares the event type | turning an event into node state |
| 4 | `@OnParentUpdate` | event-in phase, **before** this node's `@OnTrigger` | knowing *which* parent changed |
| 5 | `@OnTrigger` | once per cycle, if any parent is dirty | recomputing from parents |
| 6 | `Auditor.processingComplete` *(FirstAfterEvent only)* | start of after-event phase | an auditor that must run before nodes unwind |
| 7 | `@AfterTrigger` | after-event phase, **only if** this node's own handler ran | cleanup belonging to work this node did |
| 8 | `@AfterEvent` | after-event phase, **unconditionally** | housekeeping that must happen whatever ran |
| 9 | `Auditor.processingComplete` *(ordinary)* | after all `@AfterEvent` | **publishing the cycle's audit record** |
| 10 | dirty-flag reset | end of `afterEvent()` | — |
| 11 | queued callbacks drain | after `afterEvent()` returns | where re-dispatched events actually run |
| 12 | `@OnBatchEnd` | only when the host calls `batchEnd()` | **effects at a transaction boundary** |

Positions 7 and 8 are the same phase; 8 always runs, 7 only when this instance's own event-in handler was
on the execution path. Position 12 is not part of an event cycle at all — see below.

## The two phases

* **Event-in**, in **topological order** — parents before children. This is where state changes.
* **After-event**, in **reverse topological order** on the unwind — children before parents. This is
  where the cycle is finished with.

The consequence that matters: **inside `@AfterEvent`, every decision in the cycle has already been made.**
"Don't act mid-decision" is not a reason to leave the graph — it is what the after-event phase is *for*.

## Guards, and the one contract that is not machine-readable

`@OnTrigger` compiles to an OR over the parent dirty flags:

```java
private boolean guardCheck_pairing() {
    return isDirty_openGraph | isDirty_openLog;
}
```

So a node with nine parents needs **one** `@OnTrigger`, not nine handlers — it fires at most once per
cycle no matter how many parents changed. Writing one handler per parent is the single most common
shape mistake, and `@OnParentUpdate` is the answer when you genuinely need per-parent granularity.

The boolean return of `@OnEventHandler` / `@OnTrigger` is **the propagation decision**: return `false` and
nothing downstream runs this cycle.

> **The gap worth naming.** The compiler knows the boolean gates propagation — the generated dispatch is
> built around it. What does not exist anywhere machine-readable is **what `true` means for a given
> node**: under which conditions this node considers itself changed. That contract lives in prose, and
> when the prose is missing it is read wrongly. Independently reported by another author as the single
> artefact they would most like to see added.

## Re-dispatching from inside a cycle

Three verbs, and the difference between them is *where in the queue*, which decides ordering:

| Verb | Lands | Use when |
|---|---|---|
| `processReentrantEvent(e)` | **front** of the callback queue | this must be handled before anything already queued |
| `processAsNewEventCycle(e)` | calls `onEvent`, which — since `processing` is true — queues to the **back** | normal case: results must arrive in the order they were produced |
| `processAsNewEventCycle(iterable)` | back, in iteration order | a batch of results |

Both drain at `dispatchQueuedCallbacks()`, after `afterEvent()`, each as a **full cycle with its own
audit record**. Nothing is folded into the parent cycle's record.

**Getting this backwards reverses a batch.** Re-dispatching several results with `processReentrantEvent`
delivers them last-first, so an effect provoked by the first result is seen before the first result.

`SingleNamedNode` exposes all three as protected methods. Any node can get them by injecting
`DataFlowContext` and calling `getEventDispatcher()`.

## The other entry points share these phases

| Entry point | Shape |
|---|---|
| `init()` | `auditEvent(Init)` → `@Initialise` methods → `afterEvent()`. **No `processing` flag, no callback drain** |
| `start()` / `startComplete()` / `stop()` | `auditEvent` → the phase's methods → `afterEvent()` → drain |
| `batchPause()` / `batchEnd()` | `auditEvent` → `@OnBatchPause` / `@OnBatchEnd` methods → `afterEvent()` → drain |
| an `@ExportService` call | `beforeServiceCall` sets the flag → your method → `afterServiceCall` runs `afterEvent()` + drain |

**`batchEnd()` is a transaction boundary, and it is the answer to a question people keep re-answering.**
`BatchHandler`'s javadoc: *"a transaction of events have been received and complete… process a set of
events before publishing/exposing state changes outside of the Static Event Processor."* If you want
*"perform the side effects once this set of events has settled"*, this is it. It is called by the host,
not by the framework, so it is the host's decision where a transaction ends.

Because `onEvent` publishes its record before returning, and `batchEnd()` cannot be entered until it has,
work done in `@OnBatchEnd` is guaranteed to happen **after the deciding cycle is on the record**. That
ordering is structural. Do not try to get it from auditor ordering instead — `Auditor.FirstAfterEvent` is
a binary, there is no way for an auditor to declare itself *last*, so with more than one auditor "the
record has published" is not a single moment.

## What is *not* on this timeline

* **`@ExportService` calls are entry points that auditors never see.** An exported method dispatches into
  the graph exactly as an event does, but `Auditor.eventReceived` does not fire for it. A replay built
  from an audit log alone is therefore incomplete for any graph with exported services — measured
  elsewhere as 295 of 574 cycles diverging, with plausible numbers and no exception.
* **An exception has no defined behaviour.** There is no abort, no rollback, no failed state. A throw
  from any hook above skips `afterEvent()`, so the cycle's audit record is never published, the dirty
  flags are never reset, and `processing` is never cleared. Filed separately.

## Five things authors reliably get wrong

1. **One handler per parent.** `@OnTrigger` is already an OR over all of them.
2. **"A reference is just a read."** A plain field reference makes that parent a **trigger**. Use
   `@NoTriggerReference` for data-only dependencies. Independently reported by two authors; one shipped
   an application that emitted an order for no product because of it.
3. **Sibling order is declaration order.** It is not — it is natural order of node *name*. Four of six
   independent sessions inferred this wrongly, and the obvious experiment cannot discriminate.
4. **Building a drain, a barrier or an ordering dance.** `@AfterEvent` and `@OnBatchEnd` between them
   cover almost every version of this. Reach for a host-side mechanism only when the effect is genuinely
   asynchronous, or must survive the processor being unusable.
5. **Configuring the audit log after `init()`.** `setAuditLogProcessor` and `setAuditLogLevel` are **not
   setters** — each is `onEvent(new EventLogControlEvent(…))`, a dispatch. `init()` audits a lifecycle
   event, so a sink attached afterwards misses records, and `logLevel()` after `init()` is a **silent
   no-op** because `nodeRegistered` stamps the level onto each node's logger as it registers. Both must
   precede `init()`.
