# Spec — an asynchronous session driver, so opening can become a decision

**Status:** PROPOSED 2026-08-31. **Tracker:** [tracker.md](tracker.md) ▸ M44.3.
**Extends:** [`spec-session-processor.md`](spec-session-processor.md) — D-S0.3 and D-S0.4, which this
changes deliberately and in one place.

## Why this exists

M44 has moved four decisions into the processor. The fifth — **what opening a log or a graph means** —
has been declined twice, on the same ground both times, and the ground is worth stating rather than
working around:

> Loading a log is `Background.run`: the work runs on a pool thread and the result is delivered to the
> EDT by `SwingUtilities.invokeLater`. The v1 driver is **synchronous and single-in-flight** by design
> (D-S0.3/D-S0.4): `submit` performs every effect inline and does not return until the operation is
> finished. There is no way to express "this effect will finish later" without either lying about when
> it finished or making the driver asynchronous.

Forcing it would have produced a worse driver to close a milestone. This is the design that lets it
move honestly.

**What is at stake is not tidiness.** M35.2's rule — an arriving log closes a graph that does not
describe it — is already in the processor, but it fires on an *observation* the adapter posts after the
fact. So today the processor learns a log opened; it does not decide that it should. Every rule about
*whether* to open, *what to close first*, and *what a failed open leaves behind* is still in Swing.

## D-A0 — what already exists because this was anticipated

Three pieces of v1 were built for this moment and need no change:

* **`opId` on every request and result** (D-S0.3). The spec said at the time: *"the `opId` makes the
  constraint checkable from the audit log alone, so if the driver later goes asynchronous the record
  does not silently start lying."* This is that later.
* **`OperationGate`** already refuses a result whose `opId` is not the expected one, and records it as
  `staleResult`. That is precisely the defence an asynchronous driver needs.
* **`EffectOutcomes`** already separates *asked* from *happened*, which is what makes a suspended
  operation readable at all.

**And one accident worth naming, because it decides D-A3 below.** The gate overwrites `expectedOpId`
on each new request. That was written to detect staleness. It *also* implements
**supersede**: a second open while the first is still loading makes the first's result arrive stale and
be refused. The policy fell out of the mechanism, and it is the policy we would have chosen.

## D-A1 — one thread calls `onEvent`, and it is the EDT

**Non-negotiable, and it is the whole safety argument.** A Fluxtion processor is not thread-safe, and
an audit record interleaved from two threads is worse than unreadable — it would present two cycles as
one, which is exactly the confusion this milestone exists to remove.

So the asynchrony lives entirely at the boundary. The analyser already has a single designated thread
that every UI mutation runs on, and `Background.run` already marshals results back to it:

```java
POOL.submit(() -> {
    T result = work.get();
    SwingUtilities.invokeLater(() -> onSuccess.accept(result));   // ← already on the EDT
});
```

**The driver therefore runs on the EDT and only on the EDT.** No executor is introduced, no queue is
invented; the completion path that exists is the completion path used.

`SessionDriver.submit` asserts its thread and throws `ProtocolViolation` otherwise — the same class the
single-in-flight guard uses, for the same reason: a violation is a programming error and must reach the
caller rather than becoming a diagnostic. **Headless tests set the designated thread to the calling
thread**, so the replay suite stays single-threaded and deterministic.

## D-A2 — asynchrony is a property of the ADAPTER, not of the effect

An effect does not declare itself slow. `SessionEvents.Result` gains one case:

```java
/** The adapter has started the work and will submit the real result later, with this opId. */
record Pending(long opId, String what) implements Result { }
```

The driver, on receiving `Pending`, stops draining that branch and returns. The operation is *in
flight*: nothing is closed, nothing is applied, and the processor knows only that it asked.

**Why this shape and not an `AsyncEffect` marker.** Whether opening a log is asynchronous is a fact
about the implementation performing it, not about the request. The consequence is the one that
matters:

> **`FakeSessionAdapter` never returns `Pending`, so every existing replay test stays synchronous and
> deterministic.** Only the real adapter goes async.

A design that made effects intrinsically asynchronous would have made the whole replay suite
timing-dependent, and a flaky suite protecting a concurrency change is worse than no suite.

## D-A3 — a second request SUPERSEDES; it does not queue and is not refused

Three policies were available and only one is right for a person at a screen:

| policy | what it does to someone who picked the wrong file |
|---|---|
| refuse while busy | makes them wait for an open they no longer want |
| queue | opens the unwanted file *and then* the wanted one |
| **supersede** | the newer request wins; the older result is refused when it lands |

Supersede matches what a user means and — per D-A0 — **the gate already implements it.** The older
operation's result arrives with a superseded `opId`, is refused, and is recorded as `staleResult`. No
new mechanism; a test that pins the behaviour and a sentence in the record.

The adapter should also *cancel* the superseded work where it can — `Background.run` returns a
`Future` — but cancellation is an optimisation. **Correctness comes from refusing the result, not from
stopping the work**, because a load that ignores its interrupt must still not be believed.

## D-A4 — an operation that never completes

A synchronous driver cannot have this failure; an asynchronous one can, and the spec must say what it
means rather than discovering it.

* **The processor holds no timer.** It has no clock of its own that a replay could reproduce, and a
  timeout in the graph would make replays depend on wall time. The adapter owns progress, cancellation
  and any deadline.
* **The application does not wedge.** Because a later request supersedes, an operation that never
  completes is overwritten by the next one. What is left is a UI showing progress for work that will
  not finish — an adapter problem, and a visible one.
* **The processor can still SAY an operation is outstanding.** `OperationGate` exposes the in-flight
  `opId` and what it was for, so `context` can report *"opening /path — started, not yet completed"*
  rather than a screen that looks idle. **This is new surface and it is the point**: today a hung load
  is indistinguishable from no load at all.

## D-A5 — what the audit record must let a reader do

One operation now spans several dispatches separated in time, possibly interleaved with another
operation's dispatches. The record has to survive that.

* **Every record in an operation carries its `opId`.** Already true; it becomes load-bearing for
  *readability* and not only for staleness.
* **A suspended operation is visible as suspended.** The `Pending` result is recorded, so a reader sees
  `asked → pending → (later) outcome` rather than a gap.
* **A superseded operation ends explicitly**, with `staleResult` naming the `opId` that replaced it. An
  operation that simply stops appearing is indistinguishable from one the log lost.
* **The existing acceptance stands unchanged**: opening the processor's own audit log in the analyser
  must still answer *"why did this log close?"* — and now also *"which open was in flight when it
  did?"*

## What moves once this lands

Not part of this spec, but this is what it unblocks, and the spec is only worth it if these follow:

1. **`OpenLogRequested` / `OpenLogEffect` / `LogOpened` / `LogOpenFailed`** — the open becomes a
   request the processor decides on, not an observation it is told about.
2. **`LogObserved` / `GraphObserved` lose their `open` flag**, keeping only the evidence
   (`loggedNodeIds`, `nodeTypes`) that pairing and audit installation need. The duplication that slice
   1 introduced and M44.2 halved disappears entirely.
3. **The `isDispatching()` guard on the observation funnel goes**, because the funnel goes.
4. **M35.9's rule — an `OpenRequest` travels with every load so no dialog fires at an empty screen** —
   becomes a decision rather than a convention six modals were closed to enforce.

## Acceptance

- [ ] `submit` from a thread other than the designated one throws `ProtocolViolation`, and a test proves
      it — mutation-checked by removing the assertion.
- [ ] **Every existing replay test still passes unchanged and still runs on one thread.** If the replay
      suite has to be rewritten for this, the design is wrong.
- [ ] A `Pending` result leaves state untouched: nothing closed, nothing applied, and the record shows
      the operation asked and suspended.
- [ ] A superseded operation's late result changes no state and is recorded as `staleResult` naming the
      superseding `opId`.
- [ ] An operation that never completes leaves the application usable: the next request supersedes it,
      and `context` reports the outstanding open rather than showing an idle screen.
- [ ] The processor's own audit log, opened in the analyser, answers *"which open was in flight when
      this graph closed?"*
- [ ] `tools/verify-session-transitions.py` passes unchanged against the built jar — the socket path is
      the one place the behaviour is checked end to end, and it must not need to know about any of this.

## Risks

**This makes the driver harder to reason about, and that is the real cost.** The synchronous driver
could be understood by reading twelve lines. Mitigation is the acceptance above: the replay suite stays
synchronous, so the thing protecting the change is not itself changed by it.

**A concurrency bug here is silent and intermittent** — the worst pair. Mitigation is D-A1: exactly one
thread touches the processor, asserted rather than documented, so the failure mode is a loud throw on a
wrong thread rather than corrupted state.

**Supersede can surprise.** A user who opens A then B gets B, and A's work is discarded — correct, but
the record must show A was abandoned rather than silently forgotten, which is D-A5's third bullet.

**Scope creep into a general async framework.** This spec deliberately introduces no executor, no
queue, no timeout and no cancellation contract. It uses the completion path the application already
has. If a second asynchronous effect ever needs different machinery, that is a later decision with its
own evidence — not a generalisation made in advance of one example.
