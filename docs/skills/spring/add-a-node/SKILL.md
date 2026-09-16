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

**Before you regenerate, check the fields.** Fluxtion constructor-maps every `final` field that is not
`transient` or `@FluxtionIgnore`, and the build stops with *cannot find matching constructor … failed to
match for these fields: […]* when no constructor accepts them. Derived local state — a map, a counter, a
buffer the node builds for itself — should be `transient` (or `@FluxtionIgnore`); only builder-supplied
configuration and references to other nodes belong in the constructor. This is the first error every new
node with a `Map` or `List` field hits; the triage table at the link above carries the full rule.

Then **regenerate** — a graph change needs it, a change inside a method body does not:

```
TODO(bundle): substitute this project's exact regeneration command (the Maven profile or script that
invokes the Fluxtion source generator), and say whether it needs a Fluxtion API key.
```

Read the regenerated processor afterwards to confirm your node was wired. Do not edit it: it is an output.

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
