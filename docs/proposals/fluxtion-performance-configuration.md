# The performance configuration — a ladder, not a switch

**Status** DRAFT for the Fluxtion docs site · **Item** M50/W9 · **Date** 2026-09-06
**Evidence** [`round-58`](../experience/runs/round-58/NOTES.md) — ~700 measured runs, medians of
5 × 200M events, output verified on every arm.
**Belongs upstream** (`telaminai/fluxtion`); held here per `docs/proposals/upstream-asks.md`.

---

## Read this first

A Fluxtion processor has been measured from **9.41 ns/event down to 1.42 ns/event** — 106M to 703M
events per second on one core.

**That span is not a switch.** It is five independent decisions, three of which are free and two of
which cost you something real. Quoting the endpoints as though one flag connects them is the mistake
this page exists to prevent: while round 58 was being written up, **four successive drafts carried a
wrong headline figure**, every one of them a true measurement of a different shape, generalised too
far.

So every number below names its shape. **A figure without its shape is not a result.**

| | ns/event | events/sec | shape |
|---|---|---|---|
| stock | 9.41 | 106M | native-image + PGO, auditors + guards + re-entrancy wrapper |
| **floor** | **1.42** | **703M** | native-image, **no PGO**, non-escaping processor, base case |
| JIT floor | 4.80 | 208M | GraalVM CE 25.3.4.1, Graal JIT, base case |
| hand-written Java at the same floor | 1.49 | 671M | — |

The last row is the one worth pausing on: at the floor, **generated dispatch is 0.95× the cost of
hand-written Java** — marginally faster, because the generator emits a smaller method than a person
writes. You are not paying for the framework at that point. You are paying for the configuration you
chose above it.

---

## The five decisions

### 1 · Deployment shape — the largest lever, and it is not a flag

**Free. AOT only. Worth more than everything below combined: −54% (3.10 → 1.42 ns).**

Where the processor reference lives decides whether the compiler can scalar-replace it:

| processor reached via | native + PGO |
|---|---|
| **a local that never escapes, or a private field of an object that never escapes** | **1.57** |
| a `static` / `static final` field | 2.57 |
| an instance field of a statically-held object | 4.82 |

A `private final` field is fine **provided the holding object never escapes** — an event-loop worker
that builds its own processor and never publishes the reference. It is broken by storing the processor
in a static, a registry, an opaque factory, a thread pool, or a getter that anything actually calls.

**Reading the field into a local before the loop does not help.** The escape happened at construction.

On a JIT every shape measures ~4.8 ns. **This is an AOT-only consideration** — and it is the one most
likely to be given away by ordinary-looking application structure.

### 2 · The guarded callback drain

**Free. No flag. No semantic change. −18% native, −2% JIT.**

Nothing to configure — it is a framework change (W1). It is on this list so you can tell whether your
version has it: the processor gates `dispatchQueuedCallbacks()` on a boolean the callback path sets,
rather than calling into the dispatcher on every single event and discovering the queue is empty.

### 3 · `noReentrancy`

**−26% native, −7% JIT. Opt-in, and the build proves you may have it.**

Omits the re-entrancy wrapper when no node can raise a re-entrant event or register a callback. The
build **fails and names the offending node** if that is not true, and a runtime guard still throws,
because build-time detection cannot be complete.

Semantics are unchanged *given the proof*. If the build refuses, the flag is not for you — that is the
mechanism working.

### 4 · The base case — void triggers and no dirty filtering

**This one changes behaviour. It is your decision, not a defect.**

```java
@OnTrigger(failBuildIfMissingBooleanReturn = false)        // void trigger: no dirty flag, no guard
@OnEventHandler(failBuildIfMissingBooleanReturn = false)
config.setSupportDirtyFiltering(false);
```

**What you give up: every node fires on every event.** Conditional propagation is gone. If your graph
relies on a node declining to propagate — and most graphs that model real logic do — this changes
results, not just speed.

Take it when the graph is a pipeline that recomputes everything anyway. Do not take it because it is
the fastest row in a table.

### 5 · PGO — measure it, and never carry a profile across image kinds

**PGO is not a free win, and on the fastest shape it is a catastrophic loss.**

| arm | exe, no PGO | exe, PGO | shared lib, no PGO | shared lib, PGO |
|---|---|---|---|---|
| **non-escaping processor** | 1.53 | 1.64 | **1.42** | **6.28** |
| processor in a `static final` field | 3.10 | 2.51 | 3.10 | 6.25 |

PGO helps the shapes that **block** scalar replacement (−32%) and hurts the one that does not — mildly
in an executable, and **4× worse in a shared library**. An executable's profile applied to a shared
library was the single worst configuration measured in the whole round.

**Two rules.** Never assume PGO helps; measure it against your own shape. Never reuse a profile across
image kinds.

---

## The decision you should think hardest about: auditors

Removing auditors is on the fast path. **It also removes the audit log** — the record stream that makes
a run explainable, replayable and reviewable after the fact.

This page is written from a repo whose entire product is reading those logs, so take the bias into
account and then take the point anyway: **the audit log is usually worth more than the nanoseconds.**
Hoisting auditor calls was measured at **0% on both runtimes** — it only makes generated handlers ~35%
smaller — so there is no throughput argument for a partial retreat. Auditors are close to a binary
choice, and the honest framing is *what is this run for* rather than *how fast can it go*.

If you need both, the answer is a deployment split — audited runs for investigation and replay,
unaudited for the throughput path — not a compromise inside one binary.

---

## Two things you do NOT need to optimise

Both were proven from machine code, so that nobody spends effort in the wrong direction.

**Interface separation between components is free.** Splitting a computation behind interfaces costs
**~0.03 ns per call site**. With a single implementor visible, the AOT compiler emits **zero indirect
branches** — verified by counting `blr` instructions in the disassembly — and needs no profile to do
it. Structure your components for clarity.

The caveat that makes it a real claim rather than a slogan: this holds for a **single implementor**.
Three implementations of the same interface reachable in one image left 10 indirect branches standing
and cost +115%. Closed-world AOT devirtualises what is provably monomorphic, and nothing more.

**Event-type dispatch is not a scaling risk.** Going from 2 to 16 event types cost **+0.26 ns** total.
The switch-on-type-id alternative measured worse. Add event types freely.

---

## JIT deployment

The JIT floor is **4.80 ns (208M/s)** on GraalVM CE 25.3.4.1 with Graal JIT — the fastest JIT measured;
C2 on Corretto 21 and OpenJDK 25 both sat at 5.33 ns.

Decisions 1 and 5 do not apply — deployment shape is irrelevant on a JIT, which profiles and folds what
AOT must prove. Decisions 2, 3 and 4 apply with smaller effects (−2%, −7%, and the base case
respectively).

**One honest gap.** The largest JIT lever found — hoisting node state into the processor and re-emitting
bodies as private methods — takes the JIT from 4.82 to **2.50 ns**, within 6.8% of hand-written. It is
**not recommended and not planned**, because it requires the node's method body and private field
layout: *"I read your declarations"* would become *"I read your implementation"*, which cannot be done
to a component you do not own. So for **JIT deployment with vendor jars there is currently no lever at
all** (4.74 ns). If that is your configuration, the number above is the number.

---

## Verifying it yourself

**Do not take these figures on trust, including from us.** The four wrong headlines were not sloppy
arithmetic; each was a real measurement whose shape went unnamed.

`tools/bench/dispatch-bench.py` in the analyser repo exists to make that failure mode impossible. It
refuses to report unless: both arms ran **in one binary** (a multi-arm binary and a single-purpose
binary gave 4.86 vs 1.58 ns for identical source); the runtime kind is **single and recorded**; every
arm emits **identical check values** before any timing is believed; and no arm falls below an
elimination floor — twice in round 58 a probe measured 0.0000 ns because the compiler had deleted the
loop whose result nothing read.

It reports **paired differences from interleaved rounds**, and says so explicitly when two ranges
overlap rather than presenting the medians as a win.

---

## Summary

| decision | cost to you | JIT | native |
|---|---|---|---|
| non-escaping processor | none — structural | — | **−54%** |
| guarded callback drain | none — framework | −2% | −18% |
| `noReentrancy` | build must prove it | −7% | −26% |
| void triggers + no dirty filtering | **every node fires every event** | large | large |
| PGO | must be measured per shape | — | −32% or **+340%** |
| drop auditors | **the audit log** | — | — |

**Start with deployment shape.** It is free, it is the largest single lever, and it is the one most
often lost by accident.
