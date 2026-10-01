---
name: add-a-node
description: Add a new node to a Spring-XML authored Fluxtion graph, or make an existing node log its values, and prove it ran. A new node needs regeneration; logging from an existing node is a body-only change and does not.
x-analyser-min-version: 1.12.0
---

# Add a node to this graph

This project's graph is authored as Spring XML and compiled ahead of time. A node is a Java class **plus**
a declaration; adding the class alone does nothing.

The field rules and the error triage live in the Fluxtion orientation — read
<https://fluxtion-playground.dev/CLAUDE.md> when the build rejects a field. This file covers only what is
specific to a Spring-authored project.

## The three edits

1. **Write the class.** Copy the shape of a node already in this project rather than inventing one.
2. **Declare the bean** in the designer context XML. Its `<constructor-arg ref="…"/>` entries **are the
   graph edges** — this is how the compiler learns what depends on what.
3. **Add the bean id to `fluxtionSpringConfig`'s `nodeBeans`.**

**Before you regenerate, check the fields.** Fluxtion constructor-maps every eligible **instance** field that is
`final` and neither `transient` nor `@FluxtionIgnore` (static fields are never mapped, and `@ConstructorArg` /
`@AssignToField` opt a field in explicitly), and the build stops with *cannot find matching constructor … failed
to match for these fields: […]* when no constructor accepts them. Derived local state — a map, a counter, a
buffer the node builds for itself — should be `transient` (or `@FluxtionIgnore`); only builder-supplied
configuration and references to other nodes belong in the constructor. This is the first error every new
node with a `Map` or `List` field hits; the triage table at the link above carries the full rule.

Then **regenerate** — a graph change needs it, a change inside a method body does not:

```
TODO(bundle): substitute this project's exact regeneration command (the Maven profile or script that
invokes the Fluxtion source generator), and say whether it needs a Fluxtion API key.
```

Read the regenerated processor afterwards to confirm your node was wired. Do not edit it: it is an output.

## Close the authoring loop with evidence

Before running the changed application, write down predictions that can be disproved:

- the authored node id and dependency edge;
- where the generated processor should dispatch it and which dirty guard should control it;
- which fixture events should and should not reach it, with the values expected there; and
- which existing sink output or other behaviour must remain unchanged.

Then verify the change using the project's existing entry points; do not ask the analyser to build, run,
export or decide whether the change is correct.

1. For a graph change, validate and regenerate as above. For a body-only logging change, rebuild without
   regenerating. Read the current authoring receipt and diagnostics before trusting either result.
2. Inspect the generated processor and GraphML for the predicted dependency, dispatch position and guard.
   This proves what was generated, not what a runtime event actually exercised.
3. Run the project's tests. Include a positive case and a control that should not propagate when the
   node's trigger is conditional.
4. Follow the project-declared `run-mongoose-server` skill (or the host skill named in
   `context.runbooks`) to run the real fixture, export its audit and stop it cleanly. Use a fresh capture
   location when retained records would mix this run with older evidence; preserve the earlier evidence
   before moving or deleting anything.
   The exported **Fluxtion audit log**, not Mongoose's ordinary server log, is the evidence for
   deterministic application dispatch, values and outcomes. Read Mongoose logs only when the host's
   stochastic boundary itself needs explaining — for example input arrival, connector delivery,
   lifecycle, transport, capture/export availability or shutdown — and label those observations as
   host-level rather than application-semantic evidence.
5. Follow `load-audit-log` to open that export with the generated GraphML. Wait for
   `analyser_context.graphPairing`: the initial open result can still be pending.
6. Compare the relevant records and business output with the written predictions. Graph pairing proves
   membership, and coverage proves that a node logged; neither alone proves the intended values, gating
   or unchanged output.

Report the change as verified only when the generated structure, tests and fresh runtime evidence agree.
If they disagree, keep the artifacts and report the conflicting observation rather than changing the
prediction after seeing the result.

The model owns this sequence and its judgement. Project scripts execute build and host operations;
Fluxtion audit records carry deterministic application evidence; the analyser opens and queries evidence
already produced. Do not introduce a compound analyser verb for this workflow.

## The two things that fail SILENTLY here

**A bean that is in no list is not in the graph.** The precise rule, from
<https://fluxtion-playground.dev/spring-authoring/contract.md>: *"If present, only these beans are added as
explicit Fluxtion nodes; referenced children are still discovered by Fluxtion."* So a bean reached by a
`constructor-arg ref` from a listed node **is** in the graph; a bean that is neither listed nor referenced
is **not** — and the build stays green. If your node never appears in the audit log, check this first.

## Make an existing node log its values — a body-only change, no regeneration

This section also answers the smaller task, *"make node X show its values in the audit log"*, which touches
no XML and needs no regeneration: change the class as described below, rebuild, run, export, and read the
node's entry.

**In an untraced record, a node that logs nothing is indistinguishable from a node that did nothing.** (In
a traced one it still appears, showing its method — see below.) To record its own values a
node needs an `EventLogger`, and the runtime can only hand it one if the node implements `EventLogSource`
(`void setLogger(EventLogger)`) — extend `EventLogNode`, or implement the interface directly when the
inheritance slot is already taken by a domain base class. Then `auditLog.info("key", value)`, which is
fluent and has typed overloads — prefer the typed one so numbers stay numbers and remain graphable.

Do not read a method-name-only line as "this node is fine". That line comes from **invocation tracing**,
which is a separate setting, and with tracing off a node that logs no value **may not appear at all** — so
its absence means *"said nothing"*, not *"did not run"*.

**Log the state behind a decision, not only this event's outcome.** A node that reacts once — on a
crossing, a first match, a limit — should log the state that explains why it did or did not react
(`auditLog.info("breached", breached)` alongside the value), or name the rule in its key
(`crossedThisEvent`). A bare `false` on the events after a crossing reads as a bug to whoever debugs the
log next.

## Prove it ran — do not assume

A green build proves nothing about whether your node executed. Run the project, export the audit log, and
look for your node's entry inside the same record as the event that should have triggered it.

Position within a record is **dispatch order**: a node listed after another ran after it, in the same
cycle, on the same event. Read it as causal — that is what this log is for.

If your node is absent from every record, work down this list before changing the code — **and note that
absence alone does not prove it did not run** unless this log has invocation tracing on:

- is invocation tracing enabled for this run, or does the log only carry what nodes chose to log?
- is it in `nodeBeans`, or referenced by something that is?
- did you regenerate after the graph change?
- does it have a trigger method at all?
- can it log — does it extend `EventLogNode` or implement `EventLogSource`?
