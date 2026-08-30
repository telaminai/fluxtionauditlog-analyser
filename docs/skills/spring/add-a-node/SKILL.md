---
name: add-a-node
description: Add a new node to a Spring-XML authored Fluxtion graph and prove it ran. Use when adding behaviour, not when only changing code inside an existing node.
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

Then regenerate. See the `regenerate` skill; a graph change needs it, a change inside a method body
does not.

## The two things that fail SILENTLY here

**A bean that is in no list is not in the graph.** The precise rule, from
<https://fluxtion-playground.dev/spring-authoring/contract.md>: *"If present, only these beans are added as
explicit Fluxtion nodes; referenced children are still discovered by Fluxtion."* So a bean reached by a
`constructor-arg ref` from a listed node **is** in the graph; a bean that is neither listed nor referenced
is **not** — and the build stays green. If your node never appears in the audit log, check this first.

**A node that logs nothing is indistinguishable from a node that did nothing.** Appearing in the record is
automatic; recording *values* is not. To record its own values a node needs an `EventLogger` — extend
`EventLogNode`, or implement `EventLogSource` (`void setLogger(EventLogger)`) when its inheritance slot is
already taken by a domain base class. Then `auditLog.info("key", value)`, which is fluent and has typed
overloads — prefer the typed one so numbers stay numbers and remain graphable.

## Prove it ran — do not assume

A green build proves nothing about whether your node executed. Run the project, export the audit log, and
look for your node's entry inside the same record as the event that should have triggered it.

Position within a record is **dispatch order**: a node listed after another ran after it, in the same
cycle, on the same event. Read it as causal — that is what this log is for.

If your node is absent from every record, work down this list before changing the code:

- is it in `nodeBeans`, or referenced by something that is?
- did you regenerate after the graph change?
- does it have a trigger method at all?
- can it log — does it extend `EventLogNode` or implement `EventLogSource`?
