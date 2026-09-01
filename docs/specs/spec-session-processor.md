# Spec — the session transition processor: the analyser's first Fluxtion graph

**Status:** PROPOSED 2026-08-30 · **REVISED 2026-08-31** after review
([`review_spec_session_processor_m44.txt`](../handoff/review_spec_session_processor_m44.txt) —
*architecture accepted, specification requires changes before implementation*).
**Tracker:** [tracker.md](tracker.md) ▸ M44.
**Related:** [spec-authoring-experience.md](spec-authoring-experience.md) (the loop this feeds),
[spec-trust-structure.md](spec-trust-structure.md) (why an auditable decision layer is on-thesis).

## Why this one first

An independent review proposed a standing rule: *before specifying substantial new behaviour, ask whether
the decision-making part can become an auditable Fluxtion processor, leaving Swing, HTTP and the
filesystem as thin translation layers.* The owner chose **session transitions** as the first application,
for a reason that is worth stating plainly:

> Using Fluxtion in a real application accelerates what we learn about it far more than measuring other
> agents does — and that learning is the raw material for the template bootstrap documents.

So this is **two deliverables in one**. The processor is the visible half. The invisible half is a record
of what a real author actually hits, written by someone who has to ship the result rather than demonstrate
it. Rounds 01–06 measured agents authoring toy nodes; this is us authoring a real one.

**Session transitions are the right first subject** on the review's own test: several inputs whose
ordering matters, state that evolves, rules with consequences, and behaviour currently spread across Swing
callbacks. M35 spent **eleven slices** getting these rules right, and they still live in `MainFrame`
listeners and `ProjectSession` methods rather than anywhere a person can read them as rules.

### What the first review changed, and why the change is the point

The first draft named the right categories and did not close them into a protocol. Its central defect,
in the reviewer's words:

> requests, fallible I/O results, authoritative state and completed effects are currently collapsed
> together. That would make the first audit log describe intended transitions rather than necessarily
> describing what the application actually did.

That is the exact failure this product exists to refuse in other people's systems. An audit log that
records `closingLog=true` and is read as evidence the log closed is inferred orchestration with extra
steps. **§0 below is the repair, and it is now the first section rather than an implementation detail.**

Everything in this spec that is stated as fact about the framework was **read from `fluxtion-runtime`
1.0.13 sources** (rule 6), not inferred. Where a fact was measured, the measurement is given.

---

## 0 · The transaction model — the contract everything else obeys

**Five kinds of fact, never conflated:**

```
  request  ->  decision  ->  effect request  ->  adapter result  ->  authoritative state
 (someone     (the graph's   (what the graph    (what actually     (what is true now)
  asked)       policy)        asks be done)      happened)
```

**D-S0.1 — state nodes consume completed facts, never intentions.** No state node may take a
`*Requested` event as its input for the purpose of updating what is true. `ActiveProject` changes when
`ProfileLoaded(ok)` arrives, not when `OpenProjectRequested` does. This is not fastidiousness: all three
open operations are fallible, and the existing code already gets the ordering right — `ProjectSession`
closes the current session only *after* `ProjectProfile.load` succeeds, so a bad path closes nothing.
Updating on the request would make the processor a worse recorder than the code it replaces.

**D-S0.2 — every fallible effect has a typed result.** `CloseLogEffect` says what should happen.
`LogClosed` / `LogCloseFailed` says what did. The audit schema in §5 separates them by construction, so
"requested close" cannot be read as "closed".

**D-S0.3 — correlation is carried AND constrained.** Every request carries an `opId`; every result
carries the `opId` it answers. The v1 driver is additionally **single-in-flight and synchronous**, and
that constraint is *enforced and tested*, not assumed. Both, deliberately: the `opId` makes the
constraint checkable from the audit log alone, so if the driver later goes asynchronous the record does
not silently start lying. A result whose `opId` is not the in-flight one is dropped and logged as
`staleResult`.

**D-S0.4 — the driver, stated exactly.** Effects are drained, not called.

```
  driver.submit(fact):
      processor.onEvent(fact)          // one dispatch
      batch = effects.drain()          // immutable snapshot, cleared inside the graph
      for each effect in batch:        // OUTSIDE Fluxtion dispatch
          result = adapter.perform(effect)
          driver.submit(result)        // re-enters at the top, in order
```

Prohibited, and this is the rule that keeps the graph honest:

- **no graph node may call an adapter** (Swing, filesystem, HTTP) directly;
- **no graph node may call `onEvent`**, its own or another's;
- an effect is performed only after `onEvent` has returned.

Recursion into `onEvent` from inside a node would make re-entrancy part of the model by accident and
recreate exactly the hidden orchestration this milestone removes. The `EffectQueue` node is the only
node the driver reads, and `drain()` returns an immutable batch and empties the queue.

**D-S0.5 — state comes from EVENTS; services are for QUERY and for INSTIGATING ACTIONS.**

Owner, 2026-09-01, correcting a proposal made here to move `OpenLog`/`OpenGraph` state out of the graph
and behind an injected service. The rule:

* **State comes from events that trigger the graph.** The graph holds it.
* **Services are for querying something the graph must not mirror, or for instigating an action.**
* And the reason it lands this way here: **this is a UI, so almost everything is event-triggered
  already.** A UI generates events natively — a menu click, a load completing. Reaching for a service to
  carry state would be inventing a second mechanism next to the one the platform already gives you.

**Why the proposal was wrong, stated plainly because the reasoning is reusable.** Moving state behind a
service would have cost the property this milestone exists for: *replaying the same typed inputs, can
every decision be reconstructed from the graph and the audit log?* With state in a service, replaying
events no longer reproduces a decision — you would also need to know what the service returned. I had
noticed that and was about to work around it by logging every value read. **The rule removes the need
for the workaround instead of managing it**, which is the better shape.

**But the criticism underneath the proposal was right, and it points somewhere else.** `LogObserved`
carries `open`, the path, the logged ids and the level — that is **a state snapshot pretending to be an
event**. It says *"here is the current state"* rather than *"this happened"*, which is why it needed a
mirror-maintenance guard (`isDispatching()`) and dirty-on-change logic that domain events would not need.

The fix is not a service. It is honest events — `LogOpened(path, ids, level)` and `LogClosed()` — which
[`spec-async-session-driver.md`](spec-async-session-driver.md) already plans, and which the driver
change unblocks. **The mirror was never the mechanism's fault; it was an event modelled as a snapshot.**

**Where a service IS right here**, and there is a live candidate: the source resolver that
`NodeLogging`/`CoverageScope` use to answer *"can this class log?"*. It is threaded through as a
`Function<String, Optional<String>>` parameter today — already service-shaped, just hand-carried. The
graph must never mirror a source tree, and it only ever queries it. That is exactly (a).

**Where we deliberately do NOT use (b) — and the answer we arrived at after being wrong twice.**
Effects are not invoked from inside a node's event handler. **They are performed at
`BatchHandler.batchEnd()`, by an `@OnBatchEnd` method on `EffectQueue`** (shipped 2026-09-01). Getting
here took three corrections, and the sequence is the useful part.

**Reason 1, given first and wrong.** *"Acting inside a node would put an irreversible act inside a
dispatch that has not finished deciding."* Refuted by the owner: an event is processed in **two phases**
— event-in in topological order, then **after-event in reverse topological order on the unwind**. An
`@AfterEvent` method runs when every decision in the cycle has already been made, so "mid-decision" is
exactly what the framework has a phase to avoid. (`@AfterTrigger` is the narrower form: same phase, but
only when this instance's own event-in handler was on the execution path.)

**Reason 2, found by probing, and stated as a stronger claim than the evidence supported.** An
`@AfterEvent` method was added to `EffectQueue` and the graph regenerated. The emitted block runs it
*before* `eventLogger.processingComplete()` — before the cycle's audit record publishes. That much is
documented: `Auditor.processingComplete()` says it is *"called following all the nodes annotated with
`@AfterEvent` have been invoked"*, and `Auditor.FirstAfterEvent` is the marker that flips it.

**The correction is the owner's.** `FirstAfterEvent` is a **binary** — first, or with-the-rest. Among
several normal auditors there is no ordering, and **an auditor cannot declare itself last**. So "the
audit record has published" is not a single moment in a processor with more than one auditor. Our
processor has one, so the ordering holds; **building on it would be building on a property we happen to
have rather than one we are promised**. Filed as [UP-FLX-41](../proposals/upstream-asks.md).

**Reason 3 — the one that is actually right, and it names a primitive we had not read.**
`BatchHandler.batchEnd()`, bound by `@OnBatchEnd`, is documented as *"a transaction of events have been
received and complete… process a set of events before publishing/exposing state changes outside of the
Static Event Processor."* That is this, exactly. **The external drain was a reimplementation of a
framework feature, written because nobody had read `BatchHandler`.**

So the drain moved into the graph. The emitted method, read rather than assumed:

```java
public void batchEnd() {
    auditEvent(Lifecycle.LifecycleEvent.BatchEnd);
    processing = true;
    effectQueue.performRequestedEffects();        // adapter called, results re-dispatched
    afterEvent();                                 // the BatchEnd record publishes
    callbackDispatcher.dispatchQueuedCallbacks(); // each result runs as its own audited cycle
    processing = false;
}
```

**The ordering guarantee is unchanged and now rests on something specified.** `onEvent` ends in its own
`afterEvent()`, publishing the decision's record, and `batchEnd()` cannot be entered until `onEvent` has
returned. **Decided → recorded → acted**, with no dependence on auditor ordering.

**What this cost, and it is a real cost.** The generated `batchEnd()` sets `processing = true` with **no
try/finally**. An exception escaping the `@OnBatchEnd` method leaves that flag set, and every later event
is queued as re-entrant and never dispatched — a permanently wedged processor, silently. `EffectQueue`
therefore catches everything: an effect failure becomes `EffectFailed` as before, and a protocol
violation is *stashed* for the driver to rethrow once `batchEnd()` has returned. The violation still
reaches the caller; it no longer takes the processor with it. Pinned by
`EffectDrainAtBatchEndTest.aViolationDoesNotLeaveProcessingStuckOn`, which submits a further event after
the throw and asserts it dispatches.

**Two properties the old shape got for free and the new one had to earn**, both now tested:

1. **The result re-enters as a fact.** `EffectOutcomes` records *happened*, not only *asked*. Results
   re-enter through `processAsNewEventCycle`, which — with `processing == true` — queues to the **back**
   of the callback stack and dispatches after `afterEvent()`. Back, not front: `processReentrantEvent`
   would reverse a multi-effect batch, so the graph close would be seen before the log close that
   provoked it.
2. **The cascade settles.** A result can provoke another effect, which lands in a fresh batch. The driver
   calls `batchEnd()` until nothing more is asked, **bounded at 64 rounds** — because the recursion this
   replaced ended a runaway in `StackOverflowError`, and an unbounded `while` would hang instead. A
   desktop application that stops repainting tells nobody why.

**What is still external, and it is the whole of M44.3.** Opening is asynchronous: no in-graph phase can
wait for a `Background.run` completion. `batchEnd()` is the right home for a *synchronous* effect batch;
the `Pending` result of [`spec-async-session-driver.md`](spec-async-session-driver.md) is how an
asynchronous one declines to answer inside it. The two compose — the drain does not need to change.

**The general lesson, since the point of this section is to be reusable.** For a graph whose effects are
synchronous, **`@OnBatchEnd` is the idiom and an external drain is machinery you did not need**. For
fire-and-forget work with no result to feed back, `@AfterEvent` is simpler still. Reach for a driver-side
loop only when something must genuinely happen outside every phase — and check `BatchHandler` first.

**A framework fact that constrains the driver, verified in source.** `DataFlow.setAuditLogProcessor`,
`setAuditLogLevel`, `setAuditLogRecordEncoder` and `setAuditTimeFormatter` are **not setters** — each is
`onEvent(new EventLogControlEvent(...))`. They are dispatches. They must therefore run **at wiring time,
before the first request and outside any dispatch**, and never from inside a node. This is the first
thing the framework taught us that we would have got wrong by inference.

---

## 1 · Feasibility — corrected by measurement, not settled by reading POMs

The first draft called this settled from the artefact list. The reviewer built it. The numbers moved.

| Question | Answer |
|---|---|
| Which artefact? | **`fluxtion-runtime` only** (owner, 2026-08-30) — not the compiler, not the builder |
| Version | **pinned `1.0.13`**, matching `examples/fixture-generator` |
| Transitive deps | **`org.agrona:agrona:2.3.0`** — one, and it must be named |
| Repository | **not on Maven Central.** `https://repo.repsy.io/mvn/fluxtion/fluxtion-public` must be declared, as `examples/fixture-generator/pom.xml` already does |
| Size | **+1,164,013 bytes shaded**, 2,209,502 → 3,373,515 (measured). Not the 0.6 MB the first draft claimed: that counted the direct jar and omitted its transitive |
| Does the build need a key? | **No.** Generation is a hosted service, so the processor is generated **once, committed as source**, exactly as the starter bundle does. `mvn test` and CI stay keyless |

The honest sentence is **"one new direct runtime dependency, plus Agrona transitively, and a repository
this project does not currently declare"** — three facts where the draft had one. The cost is still
acceptable; the description was not.

**D-S1.1 — regeneration lives behind a profile-added source root, not in `src/main/java`.** A graph
builder imports `fluxtion-builder`. Put it in the default source root and the ordinary keyless build
needs the builder resolved even though nothing generates. So:

- authoring source in **`src/graph/java`**, added by `build-helper-maven-plugin` **only under `-Pregen`**;
- `fluxtion-builder` and `fluxtion-maven-plugin` declared **inside that profile only**;
- generated source committed under `src/main/java/.../session/generated/`.

A separate module was considered and rejected: the builder must see the node classes, so a module would
need a dependency on the analyser artefact and an install-first build order. The profile keeps the
builder able to see main sources while keeping it off the default build entirely.

**Acceptance is mechanical, because a claim like this rots silently:** in the default profile
`mvn dependency:tree` contains `fluxtion-runtime` and `agrona` and **contains no builder, compiler or
plugin execution**; and a **cold build against an empty local repository** passes without a key.

**D-S1.2 — the distribution licence decision is recorded, not inferred.** `fluxtion-runtime` 1.0.13's
published POM declares **AGPL-3.0**; the analyser declares its own source-available commercial licence.
Telamin may hold a separate right to combine them, but a commercial product spec must record that
decision rather than leave a reviewer to infer it from common ownership. **Owner decision required
before the dependency is added.**

*An adjacent fact found while checking this, worth filing upstream:* the runtime's **source headers**
declare `SPDX-License-Identifier: AGPL-3.0-only OR SSPL-1.0` — a dual licence — while the **published
POM declares AGPL-3.0 alone**. A consumer reading the POM sees a narrower grant than the source offers.
That discrepancy is an upstream ask in its own right.

**This still makes the analyser eat its own dog food in precisely the configuration it recommends**:
committed AOT processor, runtime-only dependency, key needed only to change the graph. If that
arrangement is awkward for us, it is awkward for every user, and we find out first-hand.

## 2 · What moves, and what explicitly does not

**Into the processor:** the *decisions* about what a session transition means.

- whether opening a project is a session boundary that must close the log and graph (M35.5);
- whether an `open` carrying both `project` and `log` should ignore the log and say so (M35.8, the
  `ignored` echo);
- whether a graph applies to the open log, and what may therefore be asserted (M35.6, M40.1);
- what the active project is, and whether the session is dirty;
- which of those facts a surface is entitled to state.

**Staying in adapters, and prohibited from acquiring policy:** Swing construction and painting; turning a
menu click into a typed event; rendering a decision into a status line or dialog; `ProjectProfile`'s file
reading and writing; the REST envelope. `ProjectSession` keeps the IO and loses the rules.

## 3 · Input events

Typed facts, no UI or infrastructure types. Every request carries `opId`.

**Requests**

| Event | Carries |
|---|---|
| `OpenProjectRequested` | profile path, **`kind`** (§4), source (`menu`/`recent`/`template`/`socket`), `opId` |
| `OpenLogRequested` | log path, optional graphml path, declared provenance, `opId` |
| `OpenGraphRequested` | graphml path, `opId` |
| `CloseProjectRequested` | `opId` |
| `CloseLogRequested` | `opId` |
| `SaveProjectRequested` | target path or none (save vs save-as), `opId` |
| `ProfileEdited` | which setting group changed |

**Adapter results — the half the first draft was missing**

| Event | Carries |
|---|---|
| `ProfileLoaded` | `opId`, **the requested path**, ok/failed, name, unknown-key count, failure reason |
| `LogOpened` / `LogOpenFailed` | `opId`, path, provenance / reason |
| `GraphOpened` / `GraphOpenFailed` | `opId`, path, source, reason |
| `LogClosed`, `GraphClosed` | `opId` |
| `ProfileApplied` | `opId` — settings are actually in force |
| `ProfileSaved` / `ProfileSaveFailed` | `opId`, path / reason |
| `SettingsRestored` | `opId` — pre-project settings back in force |
| `EffectFailed` | `opId`, which effect, reason — the catch-all so no effect can fail silently |

**Observations**

| Event | Carries |
|---|---|
| `TopologyObserved` | node ids, node **types**, node count, graph provenance — raw, not pre-computed |
| `LogObserved` | distinct logged instanceIds, observed audit level / tracing regime |

`PairingComputed` is **deleted**. It carried an already-computed answer under an input name, which put
policy in the adapter — the leak §2 forbids. The graph computes pairing from `TopologyObserved` and
`LogObserved`.

## 4 · The project-transition decision table

The first draft inferred intent from `source` (menu/recent/template/socket). That is unsound, and the
existing code proves it: `MainFrame.applyProjectResult(result, false)` exists for exactly one path —
adopting the project that was offered *because a log was just opened* — and its own comment says why:
*"closing there would destroy the log that just arrived."* An explicit menu switch **with a log open**
must close it; adoption **for that same log** must keep it. Same surface, same state, opposite rule. The
intent must be **carried**, not guessed.

`OpenProjectRequested.kind` is therefore one of:

| kind | Ends session? | Notes |
|---|---|---|
| `EXPLICIT_SWITCH` | **yes** — close log and graph | menu, recent, socket `open {project}` |
| `ADOPT_FOR_OPEN_LOG` | **no** | the M35 offer path; closing would destroy the log that caused the offer |
| `STARTUP_ACTIVATION` | no — nothing is open yet | applies settings only |
| `CREATE` | yes | a new project is a new session |
| `FORK` | yes | save-as/fork adopts the new profile as active |
| `CLOSE` | yes, **and restores pre-project settings** | `RestoreSettingsEffect`, absent from the first draft |

Plus two rules that are not a `kind` but a decision-table row each:

- **same project already active** ⇒ no-op: nothing closes, nothing is re-applied, and the record says
  `noOp=true` rather than being silent;
- **load failed** ⇒ nothing closes, nothing changes, `ProfileLoaded(ok=false)` is the only state effect,
  and the reason is logged.

## 5 · State-owning nodes

| Node | Owns | Advanced by |
|---|---|---|
| `ActiveProject` | active profile path and name, or none | `ProfileLoaded(ok)`, `SettingsRestored` |
| `OpenLog` | open log path and provenance, or none | `LogOpened`, `LogClosed` |
| `OpenGraph` | open graphml path and source (`OPENED`/`DECLARED`/`INFERRED`) | `GraphOpened`, `GraphClosed` |
| `SessionDirty` | whether unsaved edits exist | `ProfileEdited`, `ProfileSaved` |

Never by a `*Requested` event (D-S0.1).

## 6 · Derived decisions

The first draft merged two different questions into `AuditReadiness`. They are not the same question and
the analyser already keeps them apart:

- **`GraphPairing`** compares declared node ids with logged node ids: *does this graph describe this
  log?* It needs a log.
- **`AuditInstallationReadiness`** looks for Fluxtion's `EventLogManager` in the topology: *can this
  processor emit any audit log at all?* It is intentionally answerable **without a log**, which is the
  whole point — it catches the mistake before the run.
- **`CoverageClaim`** is the policy that combines them with graph provenance and the observed audit
  regime, and it is the only one of the three that says what a surface may assert.

| Node | Computes | Consumed by |
|---|---|---|
| `SessionBoundary` | whether this transition closes the log and graph, and why (§4) | `EffectQueue` |
| `IgnoredParameters` | which parameters of a combined request were not honoured, and why | the socket echo |
| `GraphPairing` | applies / does not apply / cannot say, with the counts | Project panel, `coverage` |
| `AuditInstallationReadiness` | ENABLED / NOT_ENABLED / UNKNOWN, from topology node types | Project panel, reports |
| `CoverageClaim` | what may be asserted, and `whyNot` when it refuses | Project panel, reports |

Collapsing these would regress a distinction the product already ships. `AuditReadiness`'s existing
javadoc states the limit that makes it useful — it is evidence *from the graph, before any log exists* —
and a node that needs a pairing verdict cannot answer it.

## 7 · Outputs — effect REQUESTS, never effects

The processor never touches a file or a widget. It emits into `EffectQueue`: `CloseLogEffect`,
`CloseGraphEffect`, `LoadProfileEffect`, `ApplyProfileEffect`, `RestoreSettingsEffect`,
`PersistProfileEffect`, `ShowStatusEffect`, `ShowWarningEffect`.

**Every one of these is answered by a result event from §3.** An adapter performs each one and reports
back. **If an adapter ever decides whether to perform one, the decision has leaked back out.**

## 8 · Audit contract

**D-S8.1 — nodes implement `EventLogSource` directly; they do not extend `EventLogNode`.** Verified in
runtime 1.0.13: `EventLogManager` discovers *`EventLogSource` instances* and injects via
`setLogger(EventLogger)`; `EventLogNode` is a convenience base class that does exactly that and adds a
protected `auditLog` field. The injection is identical. We take the interface (owner's call) and keep the
inheritance slot free, since our decision nodes are small and hold their own state. The cost is one field
and one method per node, and it is written down here so nobody re-litigates it per node.

**Pinned keys — decisions and outcomes are separate records:**

```
SessionBoundary            decision=close|keep, closingLog, closingGraph, kind, reason, opId
IgnoredParameters          ignored, reason, opId
GraphPairing               applies, declared, logged, matched, verdict
AuditInstallationReadiness verdict, auditorPresent, nodeCount
CoverageClaim              claim, whyNot, pairing, provenance, auditRegime
EffectOutcome              effect, success, opId, reason
```

**Only `EffectOutcome` proves anything happened.** That row is the whole of F1 in one line.

**D-S8.2 — build with `addEventAudit(LogLevel.INFO)`** (tracing ON, so participation and order are
recorded), **and set the runtime level explicitly at wiring.** The compiled tracing level and the runtime
level are different gates: `EventLogManager.logLevel` defaults to `INFO`, so `auditLog.info(...)` would
be emitted anyway — but "it works by default" is how a regime becomes invisible. The adapter calls
`setAuditLogLevel(INFO)` so the level is *stated*. Per
[fluxtion#25](https://github.com/telaminai/fluxtion/issues/25) the compiled choice is fixed at generation
time and cannot be changed without a key: we are about to live with the constraint we filed.

**D-S8.3 — the sink is ours, bounded, and never the user's business audit log.** The absence of this from
the first draft made the final acceptance bullet unreachable: nothing said where the record goes.

- **A `LogRecordListener` into a bounded in-memory ring**, attached at wiring via
  `setAuditLogProcessor(...)` **before the first request**.
- **Export writes a snapshot file** the analyser can then open. Export is the snapshot boundary, and it
  is explicit for a reason: capture scenario A, snapshot it, *then* open that fixed capture in scenario
  B. Otherwise inspecting the log changes the log — self-observation altering the evidence, which is the
  one failure mode this product may not have.
- **Failure policy:** a sink that throws must not break a session transition. It is caught, counted, and
  surfaced once.

**The trap this must avoid, verified in source and worth the whole paragraph:** the no-arg
`new EventLogManager()` — which is exactly what the generated processor declares
(`DemoQuoteProcessor.java:88`) — **defaults its sink to `System.out::println`**. An analyser that embeds a
processor and forgets `setAuditLogProcessor` does not lose its audit log; it *prints every record to
stdout*. That is a silent-green failure of precisely the class §4 of
[`notes-for-the-compiler-diagnostics-work.md`](../proposals/notes-for-the-compiler-diagnostics-work.md)
argues is the most expensive kind, and we found it by reading rather than by shipping it.

## 9 · Replay acceptance

A fixed event sequence with expected decisions, state, effects **and outcomes**. At minimum, the M35
rules that cost eleven slices — now including the exceptions the first draft could not express:

1. project A → log L → **`EXPLICIT_SWITCH`** to B ⇒ L closed, graph closed, boundary logged, `LogClosed`
   observed;
2. `open {project, log}` in one call ⇒ project honoured, log **ignored**, `ignored` names it;
3. log open, then a non-matching graph ⇒ pairing *does not apply*, `CoverageClaim` refuses with `whyNot`,
   **`AuditInstallationReadiness` is unaffected** (it does not depend on the log);
4. `CLOSE` ⇒ settings revert to pre-project (`RestoreSettingsEffect` → `SettingsRestored`), log and graph
   closed;
5. **`ADOPT_FOR_OPEN_LOG` ⇒ the log stays open** — the exception that the first draft would have broken;
6. **failed switch ⇒ nothing closes**, `ProfileLoaded(ok=false)`, state unchanged;
7. **same project reopened ⇒ no-op**, recorded as `noOp=true`, nothing re-applied;
8. **menu and socket routes reach the same decision** for the same `kind`;
9. **a stale result is dropped**: a result whose `opId` is not in flight changes no state and is logged.

## 10 · Graph acceptance

The emitted GraphML must show that **every effect request descends from a decision node rather than from
an input event** — so an accidental short-circuit from input straight to effect is visible in the
picture — and that **no state node has a `*Requested` event as an ancestor** except through a decision
and a result.

Node-completeness is asserted **per slice**, not up front (§11).

## 11 · Acceptance, sliced to match delivery

The first draft's acceptance described the finished graph while its risk section said slice one moves one
decision. That leaves the first implementer with a permanently red checklist or permission to ignore it.
Split:

**Slice 1 — `SessionBoundary` only, and independently green — SHIPPED 2026-08-31**

- [x] POM shape: `fluxtion-runtime` 1.0.13 + Repsy repository; `dependency:tree` shows runtime + Agrona
      and **no builder/compiler/plugin**. Asserted mechanically by `PomShapeTest`, which also fails if
      the graph source root or the AOT plugin escapes the profile.
- [x] Regeneration profile `-Pregen` with `src/graph/java`; the default build never resolves the builder.
- [x] The driver of D-S0.4 exists, with single-in-flight enforced **and a test that fails when it is
      removed** (mutation-checked; see below — the first version of that guard did not work).
- [x] `SessionBoundary` node, its exact graph edges, and its pinned audit keys. Edges checked with the
      analyser's own `GraphMlParser`, so our emitted topology is read by the code that reads a
      customer's.
- [x] The sink of D-S8.3; a test proves records are captured **and that nothing reaches stdout**.
- [x] Replay 1, 5, 6, 7 pass, plus 6b (a throwing adapter), 7b (adoption is never a no-op), 9 (a stale
      result), the single-in-flight guard and an unanswered effect.
- [x] **Named** old branches deleted: `afterProjectChange(String, boolean)`'s `endsSession` block and
      the flag itself, the `applyProjectResult` two-arg overload with its unnamed `false`, and
      `sessionEndEcho`'s predict-before-the-switch capture. All nine project entrances — menu, recent,
      import dialog, template, new-project, new-project-exists, fork, close, socket — now state a
      `TransitionKind` and go through one funnel.
- [x] ~~**Behavioural characterisation tests written against the OLD implementation first.**~~ **Not
      possible as asked, and closed a different way.** The old implementation is `MainFrame`, and rule 4
      does not unit-test Swing on headless CI, so there was nothing to characterise against. Instead
      **`tools/verify-session-transitions.py` drives the rules through the BUILT JAR** over the
      assistant socket, which reaches the same `SessionDriver` and the same `SessionBoundary` node as
      the menus — only the failure rendering differs. 23 checks, all passing: the switch closes, the
      no-op does not, a bad path costs nothing, leaving restores, the echo reports what closed rather
      than predicting it, and **nothing reaches stdout across the whole session**. Both load-bearing
      claims are mutation-checked against a rebuilt jar: making `EXPLICIT_SWITCH` not end the session
      fails four checks, and moving the sink attachment back after `init()` fails the stdout check.
      **The residue, stated exactly:** five entrances exist only behind a dialog —
      `ADOPT_FOR_OPEN_LOG`, `CREATE`, `FORK`, `STARTUP_ACTIVATION` and the import dialog's *open as
      project*. They reach the same decision node with a different kind, so the *rule* is covered;
      whether each call site passes the *right kind* is verified by reading, not by running.
      `ADOPT_FOR_OPEN_LOG` is the one worth a human's two minutes, because it is the exception a reader
      is most likely to think is a bug.

**Later slices** — one row per decision in §6, each deleting its old branch in the same commit.

**Final convergence**

- [ ] The complete graph of §10, pinned by a test.
- [ ] All nine replay journeys pass, against the processor rather than the UI.
- [ ] No decision from §6 remains in `MainFrame` or `ProjectSession`; every surface consumes the same
      decision snapshot, with no duplicate policy.
- [ ] The processor's own audit log is opened **in the analyser** and answers "why did this log close?"
      — and, because of `EffectOutcome`, answers "did it actually close?" separately.
- [ ] The learning predictions below are scored honestly, including the ones that were wrong.

## 12 · Adapter boundary

`MainFrame` and `ProjectSession` translate and perform. **No business decision may live in either.** The
review question applies to us: *replaying the same typed inputs, can every important decision be
reconstructed from the graph and the audit log?* — and, after this revision, *can every completed effect
be distinguished from the intention that requested it?*

## What we expect to LEARN — recorded now so it can be wrong

Written before the work so it cannot be retrofitted.

- **The `transient` rule will bite us** on `ActiveProject`/`OpenLog`, which hold `Path` state. Six
  measured agents hit it; we will find out whether knowing about it in advance is enough.
- **The adapter boundary will be the hard part**, not the graph. The temptation will be to let
  `MainFrame` decide "should I really close this?" — precisely the leak the rule forbids.
- ~~**Effect requests will multiply.**~~ **SCORED — CONFIRMED at spec review, 2026-08-31, before a line
  was written.** The first draft listed six effects. Review found five missing:
  `RestoreSettingsEffect`, apply-vs-read profile, open outcomes, close outcomes, and audit-sink delivery
  — and the whole result half of §3 with them. The prediction was that late-discovered effects are
  decisions that had been hiding in a callback; the restore-settings one had been hiding in
  `closeProject()`. Recorded as an early result rather than left for implementation to rediscover.
- **The audit log will be genuinely useful for our own debugging**, or it will not — and if it is not,
  that is the most valuable finding available, because it is the claim we make to buyers.

**A fourth, added by the revision:** *reading the framework beats inferring it, measurably.* Three facts
in this spec were wrong or absent until someone read runtime 1.0.13 or built the POM — the dependency
size, the `System.out` default sink, and that the audit "setters" are dispatches. Each would have been
found the expensive way. That is rule 6 earning its place again, and it is bootstrap-document material.

## SCORED — what slice 1 actually taught us, 2026-08-31

Written after implementing, against predictions written before. This is the deliverable the owner asked
for: *"using Fluxtion in a real application accelerates what we learn about it far more than measuring
other agents does."*

**1 · The `transient` rule did NOT bite — because the rule is not the one we had written down.**
Predicted as the most likely friction. It never fired. The reason is worth more than the prediction was:
the field-inclusion predicate takes **final, non-transient, non-ignored** instance fields. Node-local
state written as ordinary mutable private fields is never constructor-mapped, so it never participates.
Every node here holds `Path`-ish state and an `EventLogger`, and not one needed `transient`.

That is also the correction to our own upstream note, which said six agents hit this rule and framed
`transient` as *the* remedy. The compiler-diagnostics branch confirmed the same predicate independently
from the mapping loop on 2026-08-31. **So "remove `final`" — which two measured agents found and which
we recorded as an undocumented workaround — is a first-class fix**, and the message that names only
`transient` is steering authors away from the simpler one. `UP-FLX-32` is sharper for it.

**2 · The adapter boundary was NOT the hard part. Re-entrancy was.** Predicted the temptation would be
letting `MainFrame` decide. It was not — the effect list makes the boundary obvious. What was hard is
that the analyser has *two* ways into the same state: File ▸ Close log closes a log, and so does a
project switch. The second runs inside a dispatch cycle, the first does not, and the code path is
identical. The processor has to be told about the first and must not be told about the second. That is
now one guarded funnel on `updateLifecycleMenu`, and it is the shape any real migration will hit:
**a graph adopted incrementally has to coexist with the callbacks it has not replaced yet.**

**3 · Effect requests multiplied again, after the spec had already been corrected for it.**
`CreateProfileEffect` was missing — creating a profile is a different act from loading one, with a
different failure — and so was `StatusShown`, the answer to a notification effect. The second is the
interesting one: it looks like ceremony for something that cannot fail, and without it the contract
"every effect is answered" acquires an exception, which means a missing result stops proving anything.
**The prediction has now scored twice, once at review and once at implementation.**

**4 · Two defects were found by tests failing, and both were ours, not the framework's.**

* The single-in-flight guard threw `IllegalStateException`; the effect-failure handler caught
  `Exception`; so a re-entrant adapter had its violation silently converted into a routine
  `EffectFailed` and the guard did nothing. **The error handling that makes effects safe was swallowing
  the check that makes the record trustworthy.** Now a distinct `ProtocolViolation` that is rethrown.
* The sink was attached after `init()`, on the reasoning that "before the first request" was early
  enough. `init()` audits a lifecycle event, so the analyser printed audit records to stdout before the
  sink existed. Both were caught only by running it, and both are now mutation-checked.

**5 · Three framework facts that a reader would not infer, all read from runtime 1.0.13.**

* `setAuditLogProcessor` / `setAuditLogLevel` / `setAuditLogRecordEncoder` / `setAuditTimeFormatter` are
  **not setters**. Each is `onEvent(new EventLogControlEvent(...))` — a dispatch. They therefore cannot
  be called from inside a node, and their position relative to `init()` is significant in a way a
  setter's would not be. **Corrected 2026-08-31 by measuring six wirings:** this spec first concluded
  that the `DataFlow` route was itself the problem and reached for the auditor directly. It is not —
  the *ordering* was. `setAuditLogProcessor(sink)` before `init()` is clean and captures one record more
  than the auditor route. Only the LEVEL still bypasses it, because `setAuditLogLevel` prints
  `updating event log config:` to stdout on every call. The full table is in
  [`upstream-content/audit-runtime.md`](../proposals/upstream-content/audit-runtime.md).
* `EventLogManager.nodeRegistered` stamps each node's logger with the level **as the node is
  registered**, during `init()`. Calling `logLevel(...)` afterwards changes the field and none of the
  loggers built from it — configuration that appears to work and does nothing. The control-event route
  does not have this problem because it rebuilds the loggers; it has the next one instead.
* The runtime prints an unconditional `"updating event log config:"` line to **stdout** when it handles
  that control event. In a desktop application.

**6 · The picture claim was mutation-checked, and it is real.** `@PushReference` on the effect-queue
field is what makes the emitted GraphML draw `sessionBoundary → effectQueue`. Removing it and
regenerating genuinely reverses the arrow, and `SessionGraphShapeTest` catches it. So "an accidental
short circuit from input straight to effect is visible in the picture" is a property, not a hope.

**7 · The measured cost, on our tree.** Shaded jar 2,427,471 → 3,644,768 bytes: **+1,217,297**. The
review measured +1,164,013 against an older tree; both are the same fact and neither is the 0.6 MB the
first draft claimed.

**8 · An authoring-experience finding nobody was looking for.** Compiling with the builder on the path
runs a `fluxtion-substrate-lint` annotation processor that warns on **every class in the project with
instance state and no Fluxtion annotation** — thirty-odd Swing panels, a PDF writer, a source resolver.
A project with one six-node graph gets a wall of warnings about classes that will never be nodes. The
signal-to-noise makes the one legitimate warning (on `EffectQueue`, which really has no trigger
annotation, deliberately) invisible. Filed as an upstream ask.

**Every one of these becomes bootstrap-document material** if it holds, and a correction if it does not.
That is the closed loop the owner asked for: we author, we hit what an author hits, and what we hit is
what the documents should say.

## Risks

**Scope.** M35 took eleven slices; re-homing its rules is not a small change. Mitigation: slice one moves
**one** decision — `SessionBoundary` — end to end and nothing else, with its own green acceptance set
(§11), so the boundary, the driver and the build shape are proved before anything depends on them.

**Two sources of truth during migration.** A decision half-moved is worse than either place. Each slice
removes the old branch in the same commit that adds the node, and §11 names the branches for slice one
rather than trusting a later reader to find all three.

**The driver is where re-entrancy will try to get in.** Every temptation to have a node call `onEvent`
or an adapter will look locally reasonable. D-S0.4 is a hard prohibition for that reason.

**Regeneration friction in review.** A reviewer without a key cannot regenerate. The committed source and
the pinned GraphML are what they review; the profile is how the author changes it.

**The licence decision is a blocker, not a footnote.** D-S1.2 must be answered before the dependency
lands, because reversing a shipped dependency is far more expensive than deciding about it now.
