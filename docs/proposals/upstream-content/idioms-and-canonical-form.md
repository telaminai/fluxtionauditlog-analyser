# Proposed upstream content — idioms and canonical form

**What this is.** Drafted content for the **static authoring resources**, not for this repo. Fourth in
the set, and the one the other three imply: they add *facts*, this adds **which construct to reach for**.

**Where it goes:** `telaminai/fluxtion` `docs/claude.txt` and the playground `CLAUDE.md`, as a section
after the annotation reference — because it is only useful to someone who has already met the
annotations and does not know which one their problem wants.

## Why this document, specifically

Measured over one real application — a twelve-node graph built into a Swing app over two days:

> **Every mistake worth recording was a case where the structure was defensible and the framework had a
> better construct for it.** Not one was a misunderstanding of dispatch, and not one would have been
> caught by a compiler diagnostic. Each was found by being corrected by someone who knew the idiom.

That is the gap. The existing docs are strong on **facts** — `@OnTrigger` appears 21 times in
`claude.txt`, `@FluxtionIgnore` is documented with its fully-qualified name and the exact
initialiser-still-runs semantics. What is missing is the mapping from **the shape you are trying to
build** to **the construct that builds it**.

### Evidence for the specific gaps — retrieved 2026-09-01

| term | `claude.txt` | playground `CLAUDE.md` | golden path |
|---|---|---|---|
| `@OnTrigger` | 21 | 13 | 5 |
| `@AfterEvent` | 1 | 1 | 0 |
| **`@AfterTrigger`** | **0** | **0** | **0** |
| **`reverse topological`** | **0** | **0** | **0** |
| **`processReentrantEvent`** | **0** | **0** | **0** |
| **`processAsNewEventCycle`** | **0** | **0** | **0** |

**The two-phase execution model and re-dispatch are undocumented**, and they are the two facts that
decide the answers to half the questions below.

---

## Draft — *Canonical form: which construct does your shape want?*

### 1 · "This node's answer depends on several other things"

**Reach for `@OnTrigger` with references to them. Not one `@OnEventHandler` per input.**

```java
public class CoverageClaim {
    private final Pairing pairing;              // references, so they are parents
    private final AuditInstallation audit;
    private final OpenGraph openGraph;
    private final OpenLog openLog;

    @OnTrigger                                   // ONE method
    public boolean recompute() { ... }
}
```

**Why, and it is worth seeing.** The compiler renders `@OnTrigger` as an OR of its parents' dirty flags,
evaluated once per cycle:

```java
private boolean guardCheck_coverageClaim() {
    return isDirty_auditInstallation | isDirty_openGraph | isDirty_openLog | isDirty_pairing;
}
```

So the node runs **at most once per cycle however many of its inputs moved**. Written as one handler per
event, you hand-roll that de-duplication and get it subtly wrong. *Measured: one graph carried nine such
handlers across three nodes before this was pointed out; they became three `@OnTrigger` methods.*

**Return the dirty flag honestly.** The boolean is propagation control: `true` means "I changed,
re-derive my dependents". Returning `true` unconditionally re-derives everything downstream on every
input, including the ones where nothing moved.

#### When several handlers are RIGHT — the counter-case

This idiom is easy to over-apply, and the test is not "how many handlers" but **what each one
contributes**.

> Use one `@OnTrigger` when several inputs feed **one derivation** — every input changes the same
> answer, and the node recomputes the same thing however many moved.
>
> Keep separate `@OnEventHandler`s when each event contributes **different data** — the handler is not
> a trigger for a shared recomputation, it is the only place that particular fact is available.

*Found by auditing a real graph against this document.* Of six nodes still carrying several handlers,
**five were correct** and would have been damaged by collapsing them:

| node | handlers | why separate is right |
|---|---|---|
| an operation gate | 10 | each validates a different event's correlation id; it is the most upstream node and has no parents to trigger on |
| an outcome recorder | 7 | each records a *different* effect name and reason — one trigger would lose which effect completed |
| a state node | 2–3 | each event is a different state transition, not a recomputation |
| a decision node | 2 | two genuinely different decisions, at two different moments in one operation |

Only the nodes that **derived one answer from several inputs** wanted collapsing. Applying the idiom
mechanically to handler *count* would have destroyed the information the others carry.

**The generated code tells you which case you are in.** A node with an `@OnTrigger` gets a
`guardCheck_` method — an OR of its parents' dirty flags. If you cannot name the parents whose dirtiness
should re-run your method, you do not have a derivation; you have several handlers, correctly.

### 2 · "My node needs some state"

**State comes from events that trigger the graph, and the graph holds it. Services are for querying
something the graph must not mirror, or for instigating an action.**

The temptation, in an application with an existing state-holder, is to inject that state as a service and
have nodes ask it. Resist it for *state*:

* the graph stops being replayable — replaying the events no longer reproduces a decision, because you
  would also need to know what the service returned at the time;
* in a UI it is a second mechanism next to the one the platform already gives you. **A UI is events.**

**Use a service when the graph must not hold the thing at all** — a source tree, a filesystem, a remote
lookup. Constrain it with an interface: the node can then only ask what the interface exposes, and a test
supplies a *test implementation* rather than a mock.

**A smell worth naming, because it is easy to write by accident.** An event carrying
`open=true/false, path, ids, level` is **a state snapshot pretending to be an event** — it says "here is
the current state" rather than "this happened". Snapshots need mirror-maintenance: guards against
re-entrancy, change-detection to avoid spurious re-derivation. Real domain events —
`LogOpened(path, ids)` and `LogClosed()` — need none of it.

### 3 · "I need to DO something as a result" — and the two-phase model

**An event is processed in two phases**, and this is the fact that decides where side effects go:

| phase | order | annotations |
|---|---|---|
| event-in | **topological** — parents before children | `@OnEventHandler`, `@OnTrigger` |
| after-event | **reverse topological** — children before parents, on the unwind | `@AfterEvent`, `@AfterTrigger` |

**So the canonical place for a side effect is the after-event phase.** By then every decision in the
cycle has been made, and acting is no longer acting mid-decision.

**`@AfterEvent` versus `@AfterTrigger`**, which is a real distinction and appears in none of the
authoring docs:

* **`@AfterEvent`** is called **always**, regardless of the incoming event.
* **`@AfterTrigger`** is called only if the same instance has an event-in handler **and that handler was
  on the current execution path**. It is the "I actually participated in this cycle" hook.

Choose `@AfterTrigger` when the effect belongs to work this node did; `@AfterEvent` for housekeeping that
must happen whatever ran.

**When to go outside the graph instead.** Two cases, and they are narrow:

* **the result must re-enter as a fact** — if you need to record that the effect *happened* and not only
  that it was decided, the outcome has to arrive as a new event, so something outside must feed it back;
* **the effect is asynchronous** — no in-graph phase can wait for it.

Otherwise the after-event phase is the idiom, and an external drain is machinery you did not need.

### 4 · "A decides, B reacts, and A must see the result"

**The static dependency graph must be acyclic** — a cycle is refused at build time. But that constrains
the *graph*, not the *design*: a node can queue an event that starts a **new cycle**.

```java
processReentrantEvent(e);      // inserts at the FRONT of the queue; must already be in a cycle
processAsNewEventCycle(e);     // appends to the END, and forces a cycle if none is running
```

The queued event is dispatched after the current cycle finishes and publishes its audit record, so it
arrives as a genuinely separate cycle with its own record.

**Before reaching for it, check whether `@OnTrigger` (§1) does what you want in one cycle** — it usually
does, and it costs no extra cycle and no ambiguity about what caused what.

**One consequence to know.** A re-dispatched record looks, in the audit log, like an event fed in from
outside. If your log is something people read to reconstruct causality, use re-dispatch deliberately.

### 5 · "Which thread runs the processor?"

**A processor is not thread-safe. Exactly one thread may call `onEvent`.** Three ways to arrange that:

| option | when |
|---|---|
| a server hosts it and owns the thread | a deployed service |
| `DataFlowConnector` — an Agrona `AgentRunner` brokering `EventFeedAgent`s into a `DataFlow` | headless or embedded, where no suitable thread exists |
| embed in a thread the application already has | a UI: Swing's EDT already runs everything else |

For a web application, prefer **one confined processor per session** — genuinely concurrent sessions
cannot share one.

### 6 · Field wiring, and the order to work in

See [`node-field-wiring-and-workflow.md`](node-field-wiring-and-workflow.md). In short: **final** is what
triggers constructor mapping; a non-final field is setter-wired; and while the graph's shape is still
moving, develop bean-style and harden to constructors once it settles — otherwise a changed constructor
signature stops the committed generated source compiling, which blocks the regeneration that would fix it.

---

## A note for whoever takes this

The four documents in this set differ in kind and it is worth keeping them apart. `audit-authoring.md`
and `audit-runtime.md` are **facts** — things that are true and were not written down.
`node-field-wiring-and-workflow.md` is half fact, half **workflow**. This one is **idiom**: every entry
has the form *"you are trying to build X; the construct is Y; here is why the obvious alternative is
worse."*

That form is the point. An author who has read the annotation reference knows what `@OnTrigger` does.
They still write nine handlers, because nothing told them that is what `@OnTrigger` is *for*.
