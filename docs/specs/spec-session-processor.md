# Spec — the session transition processor: the analyser's first Fluxtion graph

**Status:** PROPOSED 2026-08-30 (owner-directed). **Tracker:** [tracker.md](tracker.md) ▸ M44.
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

## Feasibility — settled before designing, because it decides the shape

**The analyser today has no Fluxtion dependency at all.** Its only runtime dependency is FlatLaf; it reads
audit logs as text and never links the framework. So this adds one.

| Question | Answer |
|---|---|
| Which artefact? | **`fluxtion-runtime` only** (owner, 2026-08-30) — not the compiler, not the builder |
| Size | 0.6 MB against a 2.1 MB fatjar |
| Does the build need a key? | **No.** Generation is a hosted service, so the processor is generated **once, committed as source**, exactly as the starter bundle does. `mvn test` and CI stay keyless |
| Regeneration | behind a profile that is the only step mentioning a key — the same shape we already document for users |

**This makes the analyser eat its own dog food in precisely the configuration it recommends**: committed
AOT processor, runtime-only dependency, key needed only to change the graph. If that arrangement is
awkward for us, it is awkward for every user, and we will find out first-hand.

## D-S1 — what moves, and what explicitly does not

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

## 1 · Input events

Typed facts, no UI or infrastructure types.

| Event | Carries |
|---|---|
| `OpenProjectRequested` | profile path, source (`menu`/`recent`/`template`/`socket`) |
| `OpenLogRequested` | log path, optional graphml path, declared provenance |
| `OpenGraphRequested` | graphml path |
| `CloseProjectRequested` | — |
| `CloseLogRequested` | — |
| `ProfileLoaded` | outcome of the adapter's read: ok/failed, name, unknown-key count |
| `PairingComputed` | declared node ids, logged node ids |

## 2 · State-owning nodes

| Node | Owns |
|---|---|
| `ActiveProject` | the active profile path and name, or none |
| `OpenLog` | the open log path and its provenance, or none |
| `OpenGraph` | the open graphml path and its source (`OPENED`/`DECLARED`/`INFERRED`) |
| `SessionDirty` | whether unsaved edits exist |

## 3 · Derived decisions

| Node | Computes | Consumed by |
|---|---|---|
| `SessionBoundary` | a project change closes the log and graph (M35.5) | `EffectRequests` |
| `IgnoredParameters` | which parameters of a combined request were not honoured, and why | the socket echo |
| `GraphPairing` | applies / does not apply / cannot say | the Project panel, `coverage` |
| `AuditReadiness` | what the pairing entitles a surface to assert (M40.1) | Project panel, reports |

## 4 · Outputs — effect REQUESTS, never effects

The processor never touches a file or a widget. It emits: `CloseLogEffect`, `CloseGraphEffect`,
`LoadProfileEffect`, `ShowStatusEffect`, `ShowWarningEffect`, `PersistProfileEffect`. An adapter performs
each one. **If an adapter ever decides whether to perform one, the decision has leaked back out.**

## 5 · Audit contract

Every decision node implements `EventLogSource` and logs enough to explain itself, not merely to announce:

- `SessionBoundary` — `closingLog`, `closingGraph`, `becauseProjectChanged`
- `IgnoredParameters` — `ignored`, `reason`
- `GraphPairing` — `applies`, `declared`, `logged`, `verdict`
- `AuditReadiness` — `level`, `whyNot` when it refuses

**Build with `addEventAudit(LogLevel.INFO)`**, i.e. tracing ON, so participation and order are recorded and
absence is meaningful — and note that per [fluxtion#25](https://github.com/telaminai/fluxtion/issues/25)
that choice is fixed at generation time and cannot be changed without a key. We are about to live with the
constraint we filed.

## 6 · Replay acceptance

A fixed event sequence with expected decisions, state and audit records. At minimum, the M35 rules that
cost eleven slices:

1. open project A → open log L → open project B ⇒ L closed, graph closed, boundary logged;
2. `open {project, log}` in one call ⇒ project honoured, log **ignored**, `ignored` names it;
3. open log then a non-matching graph ⇒ pairing `does not apply`, readiness refuses coverage;
4. close project ⇒ settings revert to pre-project, log and graph closed.

## 7 · Graph acceptance

The emitted GraphML must show the four state nodes, the four decision nodes, and that every effect request
descends from a decision node rather than from an input event — so an accidental short-circuit from input
straight to effect is visible in the picture.

## 8 · Adapter boundary

`MainFrame` and `ProjectSession` translate and perform. **No business decision may live in either.** The
review question applies to us: *replaying the same typed inputs, can every important decision be
reconstructed from the graph and the audit log?*

## What we expect to LEARN — recorded now so it can be wrong

This half is the point, and it is written before the work so it cannot be retrofitted. Predictions:

- **The `transient` rule will bite us** on `ActiveProject`/`OpenLog`, which hold `Path` state. Six measured
  agents hit it; we will find out whether knowing about it in advance is enough.
- **The adapter boundary will be the hard part**, not the graph. The temptation will be to let
  `MainFrame` decide "should I really close this?" — and that is precisely the leak the rule forbids.
- **Effect requests will multiply.** The first design usually under-counts them, and every one discovered
  late is a decision that had been hiding in a callback.
- **The audit log will be genuinely useful for our own debugging**, or it will not — and if it is not, that
  is the most valuable finding available, because it is the claim we make to buyers.

**Every one of these becomes bootstrap-document material** if it holds, and a correction if it does not.
That is the closed loop the owner asked for: we author, we hit what an author hits, and what we hit is what
the documents should say.

## Acceptance

- [ ] `fluxtion-runtime` is the only added dependency; `mvn test` and CI remain keyless.
- [ ] The generated processor is committed; regeneration sits behind a key-gated profile.
- [ ] The four replay sequences pass as tests against the processor, not against the UI.
- [ ] The emitted GraphML matches §7 and is pinned by a test.
- [ ] No decision from §3 remains in `MainFrame` or `ProjectSession`.
- [ ] The processor's own audit log is opened **in the analyser** and answers "why did this log close?".
- [ ] The learning predictions above are scored honestly, including the ones that were wrong.

## Risks

**Scope.** M35 took eleven slices; re-homing its rules is not a small change. Mitigation: the first slice
moves **one** decision — `SessionBoundary` — end to end, and nothing else, so the boundary and the build
shape are proved before anything depends on them.

**Two sources of truth during migration.** A decision half-moved is worse than either place. Each slice
removes the old branch in the same commit that adds the node.

**Regeneration friction in review.** A reviewer without a key cannot regenerate. The committed source and
the pinned GraphML are what they review; the profile is how the author changes it.
