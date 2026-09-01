# There is no recovery model for an exception thrown inside a cycle

**Target** `fluxtion-runtime` (semantics) → `fluxtion-compiler` (`javaTemplate.vsl`) ·
**Severity** high · **Found** 2026-09-01, building an `@OnBatchEnd` effect drain

**The reported symptom is that `processing` is left `true` and the processor silently stops dispatching.
That is real and reproduced below, but it is the smallest part.** The actual gap is that a Fluxtion
processor has **no defined behaviour after an exception escapes user code mid-cycle** — no abort, no
rollback, no failed state, no way for a host to ask whether the processor is still usable. It simply
carries on being called, with a half-updated graph and a flag that says a cycle is still running.

Everything below is what one throw leaves behind, in the order it matters.

---

## 1. The audit record for the failed cycle is the one record you never get

This is the headline, and for this framework it is close to the worst possible ordering.

`eventLogger.processingComplete()` — the call that **publishes the cycle's audit record** — lives in
`afterEvent()`, which is the last statement of every `handleEvent`:

```java
public void handleEvent(GraphObserved typedEvent) {
    auditEvent(typedEvent);
    auditInvocation(operationGate, "operationGate", "onGraphObserved", typedEvent);
    isDirty_operationGate = operationGate.onGraphObserved(typedEvent);
    ...
    afterEvent();          // <-- eventLogger.processingComplete() is in here
}
```

So a cycle that throws publishes **nothing**. The auditors received `eventReceived` and a run of
`nodeInvoked` calls and are left mid-record; the record is discarded.

**A Fluxtion audit log is therefore systematically silent about exactly the cycles that failed.** For a
framework whose central claim is *"absence from the log means the node did not run"*, that inverts the
guarantee at the only moment anyone is reading the log. Whatever else changes, the partial record should
be flushed and marked failed.

## 2. Dirty flags survive the failed cycle and poison the next one

`afterEvent()` is also where `resetDirtyFlags` lives, so the flags set before the throw stay set. The
generated guards are ORs over parent dirty flags:

```java
private boolean guardCheck_pairing() {
    return isDirty_openGraph | isDirty_openLog;
}
```

The next event therefore runs with dirty flags from an event that never completed, and `@OnTrigger`
methods fire that this event did not justify. **This is a correctness bug that survives fixing
`processing`** — it is not the wedge, it is silent wrong dispatch afterwards. Fork tasks
(`resetForkTasks`) are in the same position.

*Read from the generated source and the template, not independently reproduced — the wedge in §3 masks
it, because nothing dispatches afterwards to observe.*

## 3. `processing` stays `true`, so the processor silently accepts and drops everything

Every dispatch entry point sets `processing = true`, runs user code, then sets `processing = false` —
**with no `try`/`finally`**. After a throw, `processEvent` takes the other branch forever:

```java
if (processing) {
    callbackDispatcher.queueReentrantEvent(event);   // queued, and never drained
} else {
    ...
}
```

`onEvent` returns normally and nothing happens. No exception, no log line, no degraded mode. The queued
events accumulate in `CallbackDispatcherImpl.myStack` without bound, so it is also a slow leak.

### Reproducer — verified, no custom nodes needed

`setUnKnownEventHandler` is used only because it is the shortest way to get user code to throw inside a
dispatch. Any `@OnEventHandler`, `@OnTrigger`, `@AfterEvent` or `@OnBatchEnd` method that throws does the
same thing.

```java
SessionProcessor p = new SessionProcessor();   // any generated processor
p.init();
p.setUnKnownEventHandler(e -> { throw new RuntimeException("boom"); });

try {
    p.onEvent("an unhandled event type");
} catch (RuntimeException expected) {
    // the throw does reach the caller — that part is fine
}

// A perfectly ordinary event, of a type this processor HAS a handler for:
p.onEvent(new LogObserved(true, "/logs/run.yaml", "DECLARED"));

System.out.println(p.openLog.isOpen());   // false — the handler never ran
```

```
1. the throw reached the caller: boom
2. openLog.isOpen() = false   (expected true; false means the event was never dispatched)
3. still silent after a second event = true

REPRODUCED — processing stayed true; every later event is queued and never dispatched.
```

### Every affected site

`fluxtion-generator-core/src/main/resources/template/base/javaTemplate.vsl` — seven, none guarded:

| line | method | notes |
|---|---|---|
| 85 | `start()` | |
| 98 | `startComplete()` | |
| 111 | `stop()` | |
| **152** | **`processEvent()`** | **the main event path** |
| **174** | **`beforeServiceCall()` / `afterServiceCall()`** | **worst shape: the set and the clear are in two different methods, so a throw from an exported service method can never reach the clear** |
| 192 | `batchPause()` | |
| 202 | `batchEnd()` | |

`init()` is not affected — it does not set the flag. The same shape is hand-written twice more and needs
the same treatment: `fluxtion-runtime/.../DefaultEventProcessor.java` (seven sites, one `try` in the
whole file) and `fluxtion-builder/.../generation/target/InMemoryEventProcessor.java` (six).

## 4. Queued callbacks from the failed cycle leak into the next one

On the exception path `dispatchQueuedCallbacks()` is skipped, so anything the failed cycle re-dispatched
is still on `myStack` — and would be dispatched by the **next, unrelated** event as if it belonged to it.
An event from an abandoned cycle silently replayed inside a later one is worse than the wedge, because it
is not visibly broken.

## 5. A poison pill in the drain itself

`CallbackDispatcherImpl.dispatchQueuedCallbacks()`:

```java
while (!myStack.isEmpty()) {
    dispatching = true;
    Supplier<Boolean> callBackItem = myStack.peekFirst();     // peek, not poll
    if (!callBackItem.get()) { myStack.remove(callBackItem); }
}
dispatching = false;
```

If `callBackItem.get()` throws, the item is **still at the front of the stack** and `dispatching` stays
`true`. The next drain picks the same item, which throws again — every subsequent cycle fails
identically. Remove before invoking, not after.

*Read from source; not independently reproduced, for the same masking reason as §2.*

---

## What a recovery model has to decide

The mechanical `try`/`finally` is necessary and not sufficient, because it only decides *when the flag is
cleared* — not what the processor **is** afterwards. Four questions, and they are genuine design calls:

**1. Is the processor still usable?** Today it is neither usable nor visibly broken, which is the only
answer that cannot be right. The minimum honest alternative is a **failed state**: the processor records
that a cycle aborted and either refuses further events with a clear exception, or accepts them while
`isFailed()` reports true. A host can then restart, rebuild or alert. Silence gives it nothing to act on.

**2. What happens to the half-updated graph?** Nodes upstream of the throw have mutated; nodes downstream
have not. Three options, in increasing cost:

| option | what it gives | what it costs |
|---|---|---|
| **leave it, but say so** | cheap; the audit record names the node that threw and the state is inspectable | the graph is inconsistent and only the author knows what that means |
| **reset the cycle's bookkeeping** | dirty flags, fork tasks and the callback stack cleared, so the *next* cycle is clean even though node state is not | does not undo node mutations |
| **rollback** | real transactional semantics | needs per-node snapshots; only viable if nodes opt in |

Rollback is worth a mention rather than a dismissal: `Auditor`'s own javadoc already lists *"Commit/rollback
functionality"* among its use cases, and an auditor sees `nodeInvoked` for every node on the execution
path — which is precisely the set that would need undoing.

**3. Where does the author get to intervene?** There is currently no hook. An `@OnError`-style callback —
given the event, the node and the throwable, and able to say *continue / abort cycle / fail processor* —
would let the author decide per graph. Without it, every host reinvents the workaround.

**4. What does the record say?** Whatever is decided in 1–3, the partial record must be published and
must name the node and method that threw. That single change would make every one of these failures
diagnosable, and it is independent of the rest.

## Our workaround, and why it argues for fixing this upstream

We moved an effect drain into `@OnBatchEnd`. The adapter it calls can throw a deliberate protocol
violation that must reach the host. It does — and it took the processor with it. So `EffectQueue` now
catches **everything**, stashes anything fatal in a field, and the host rethrows it after `batchEnd()`
has returned and the flag is clear.

That works. It is also a workaround every author will have to invent independently, most will not know
they need it, and it is invisible until the first exception in production — at which point the audit log
that would have explained it is the one record that was never written.
