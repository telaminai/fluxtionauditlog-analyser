---
name: add-a-node
description: Add a node to a Spring-XML authored Fluxtion graph, or make an existing node log its values, and prove it ran; or show whether a change altered the logged business values by comparing before and after audit logs. A new node needs regeneration; a body-only logging change does not.
x-analyser-min-version: 1.12.0
---

# Add a node to this graph

This project's graph is authored as Spring XML and compiled ahead of time. A node is a Java class **plus**
a declaration; adding the class alone does nothing.

The field rules and the error triage live in the Fluxtion orientation — read
<https://fluxtion-playground.dev/CLAUDE.md> when the build rejects a field. This file covers only what is
specific to a Spring-authored project.

## Shape the graph: one node per owner of state

Before declaring anything, split the work by who owns which state — items, reservations and customers, say —
one node each, joined by references or derived events. One node with every handler compiles and runs, but its
audit can only ever say "that node ran"; separate nodes make the log say *which* responsibility acted and why.
This is guidance, not a gate: more nodes cost more declarations.

## Declare it in the XML; regeneration writes the class

All three edits are in the designer XML that `fluxtion-authoring.json` names:

1. **Declare the bean.** Its `<constructor-arg ref="…"/>` entries, one per parent, **are the graph
   edges** — this is how the compiler learns what depends on what.
2. **Add the bean id to `fluxtionSpringConfig`'s `nodeBeans`**, and any bean a binding names.
3. **Bind its references and outputs** in `fluxtionSpringConfig`. A parent that must not trigger the node
   is a `ReferenceBinding` with `mode` `DATA`. With several trigger parents, `parentUpdateCallback` `true`
   on a `TRIGGER` binding adds an `@OnParentUpdate` callback naming the parent that changed. An output is
   a `SinkBinding` under `sinks`:

   ```xml
   <property name="referenceBindings"><list>
     <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.ReferenceBinding">
       <property name="ownerBean" value="myNode"/><property name="referencedBean" value="parentA"/>
       <property name="mode" value="TRIGGER"/><property name="parentUpdateCallback" value="true"/>
     </bean>
     <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.ReferenceBinding">
       <property name="ownerBean" value="myNode"/><property name="referencedBean" value="lookup"/>
       <property name="mode" value="DATA"/>
     </bean>
   </list></property>
   <property name="sinks"><list>
     <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig.SinkBinding">
       <property name="name" value="out"/>
       <property name="nodeBeans"><list><value>myNode</value></list></property>
     </bean>
   </list></property>
   ```

**`eventHandlers` does not route events.** An `EventHandlerBinding` states a design-time expectation —
this event type should be handled by these beans (enforced only with `strictEventHandlerBindings`); it
delivers nothing.
Fluxtion dispatches an event to every node whose `@OnEventHandler` method takes that event type. When you
explain what happened to an event, cite that method and the audit record, never "the XML routes it".

**Filtered handlers.** A binding with a `filterString` makes regeneration write a handler that receives only
events whose `filterString()` matches. The method is `on<Event>` when the node has one binding for that event,
`on<Event>Filter<n>` (n = the binding's position) when it has several; regeneration prints one
`<bean>.<method> ← <Event> filterString=<value>` line per handler it adds. Three rules, each refused by
`./validate.sh` before anything is generated (starter-core 1.0.86+; older ones fail later, at the build):
one binding per event and filter value (`SPRING_FILTER_DUPLICATE`); a binding sets `filterString` or
`filterId`, not both (`SPRING_FILTER_CONFLICT`); and one node does not mix filtered and unfiltered bindings
of the same event (`SPRING_FILTER_MIXED`).

Then **regenerate** — a graph change needs it: an XML change, a field added or removed, or an annotation added
or changed on a field or method, **`@Inject` included**. Only a change confined to a method body does not. `./validate.sh` and the run scripts refuse to rebuild a node whose fields or annotated
signatures changed since the last generation (starter-core 1.0.86+), naming the class and the change:


```
TODO(bundle): substitute this project's exact regeneration command (the Maven profile or script that
invokes the Fluxtion source generator), and point to the project's Compiler sign-in instructions.
```

Then read only the compiler's own messages — the validation report (its node and edge counts), the
authoring receipt and the compiler diagnostics. They say whether the graph compiled as declared. Do not
read the regenerated processor, its GraphML text or the builder to confirm the wiring: they are outputs,
they cost thousands of tokens to read, and the audit log shows the dispatch order directly.

**Regeneration writes the class shell**: constructor, fields (a DATA field carries `@NoTriggerReference`),
a `SinkPublisher` field per sink (`sinkOut`) and the callback methods, annotations fully qualified —
there are no imports to find. Fill only the method bodies, then rebuild, run and prove it from the audit
log as below.

**Do not write the class yourself, and do not wire a sink outside the XML** (a hand-written
`@ServiceRegistered` callback delivers at runtime but is not declared in the design). Either leaves the
design XML without what the code does, and a hand-written class that disagrees with the XML is refused
(`SPRING_RECONCILE_CONFLICT`). If a class must be hand-written, mark each DATA field `@NoTriggerReference`.
The host still needs a destination of the sink's name — in Mongoose, a `server-config.yml` `eventSinks`
entry.

**Check any field you add.** Fluxtion constructor-maps every eligible **instance** field that is
`final` and neither `transient` nor `@FluxtionIgnore` (static fields are never mapped, and `@ConstructorArg` /
`@AssignToField` opt a field in explicitly), and the build stops with *cannot find matching constructor … failed
to match for these fields: […]* when no constructor accepts them. Derived local state — a map, a counter, a
buffer the node builds for itself — should be `transient` (or `@FluxtionIgnore`); only builder-supplied
configuration and references to other nodes belong in the constructor. This is the first error every new
node with a `Map` or `List` field hits; the triage table at the link above carries the full rule.

## Close the authoring loop with evidence

Before running the changed application, write down predictions that can be disproved:

- the authored node id and dependency edge;
- where it should appear in each event's dispatch order, and which dirty guard should control it;
- which fixture events should and should not reach it, with the values expected there; and
- which existing sink output or other behaviour must remain unchanged.

Then verify the change using the project's existing entry points; do not ask the analyser to build, run,
export or decide whether the change is correct.

1. For a graph change, validate and regenerate as above. For a body-only logging change, rebuild without
   regenerating. Read the current authoring receipt and diagnostics before trusting either result.
2. Check the predicted node and edge against the validation report's counts and the compiler
   diagnostics. Do not read the generated processor, GraphML text or builder: the dispatch position and
   guard are checked in step 6, from what the audit log shows an event actually did.
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

Report the change as verified only when the compiler's messages, tests and fresh runtime evidence agree.
If they disagree, keep the artifacts and report the conflicting observation rather than changing the
prediction after seeing the result.

The model owns this sequence and its judgement. Project scripts execute build and host operations;
Fluxtion audit records carry deterministic application evidence; the analyser opens and queries evidence
already produced. Do not introduce a compound analyser verb for this workflow.

## Verify from the audit log, not from generated code

The generated processor and GraphML show what the generator **wrote**; they cannot show what an event
**did**. Do not read the processor or the GraphML text: the GraphML is opened in the analyser (step 5),
never read. The compiler's messages say whether the declared graph compiled, and the audit
log answers every behavioural question. Generated source costs thousands of tokens to take in and answers
nothing those two do not. With invocation tracing on, a record lists every
node invoked for its event, in dispatch order, each with its `method` (and, from Fluxtion runtime 1.1.1,
the `annotation` that dispatched it) — that is the evidence for dispatch order, not a reading of the
generated `onEvent` body.

When the log cannot answer — a node ran but logged none of the state behind its decision, or the level
in force hid it — make the log answer: add `auditLog` calls to the node, or raise its `logLevel`, then
run again. Do not fill the gap by reasoning from generated code, and do not report a conclusion the
log does not show.

## Prove a change by comparing audit logs

A refactor, a new node or a moved edge should change **framework** records — another dispatch path, a
renamed callback, a new trace-only record — and leave **business** records alone unless the change was
meant to alter them. Prove which happened by comparing two runs of the **same input**:

1. Capture the audit log **before** the change: run the fixture, export it, keep the file under a name of
   its own. A bundle that hosts several processors exports one file per processor
   (`logs/audit-<processor>.yaml`); compare each processor's export with its own counterpart, never one
   processor's log with another's.
2. Make the change, rebuild (regenerate if it was a graph change), and capture the **after** log from the
   same input, in a fresh capture location so older runs do not mix in.
3. Compare them:

   ```
   python3 audit-compare.py before/audit-<processor>.yaml after/audit-<processor>.yaml
   ```

   `audit-compare.py` is published beside this skill (Python 3, standard library only). If this
   project's copy of the skill does not carry it, take the canonical copy,
   `docs/skills/spring/add-a-node/audit-compare.py` in the analyser repository, at the revision this skill
   came from — do not write your own on the spot. It splits every node entry into **business** data —
   the keys the node chose to log — and **framework** data — the keys invocation tracing writes FIRST in an
   entry: an optional `thread`, then `method` (or `forkedExecution` and `asyncMethod`), then an optional
   `annotation` naming an annotation. Only that leading run is framework, as it is in the analyser; a key
   after it, or a lone `thread` or `annotation`, is the node's own. It compares the business records
   in order — event, event type, event text, any field the reader does not know, and every node's business
   entries; never times, the thread or the grouping id, which vary between runs — and
   reports framework differences separately. Exit `0`: the logged business entries match; `1`: they
   differ, and the changed records are printed; `3`: inconclusive — they match only if an `annotation`
   straight after the method is tracing's (runtime 1.1.1+) rather than an older node's own key; pass
   `--annotations-are-trace` only when both captures come from runtime 1.1.1 or later and no node logs a
   key called `annotation` first; `2`: nothing follows — a log holds no business record, or is in a shape
   the script refuses to guess at (records run together without `---`, a list-shaped export, a value whose
   line break broke the record's structure).

4. Conclude **"the logged business entries changed"** or **"the logged business entries did not change,
   for this input"** from the business comparison **only** — not "behaviour did not change": an output or
   side effect no node logs is not compared, and logging a new value shows as a difference. When the
   conclusion needs an output, make a node log it (or assert it) first. A framework difference never
   decides it either way: a new trace-only record or a renamed callback is not a business change, and a
   quiet framework section does not prove business output is right. Report the framework differences as what they are — evidence about dispatch — and
   check each one against your written prediction.

The comparison is only as good as the input being identical. If the two runs read different input, or
one export carries an extra run (for example, `ALL_RUNS=1` was used on only one side — see `run-mongoose-server`), the difference is
in the evidence, not the code; fix the capture and compare again before concluding anything.

## Configuration, propagation and sink values

- A generated `@OnTrigger` boolean reports a change to downstream dispatch: `true` propagates to
  ordinary dirty-trigger children; `false` does not. It is not a success/failure result. A child with
  `@OnTrigger(dirty=false)` follows the inverse condition. Keep that distinction in your predictions.
- A `SinkBinding` may declare `valueType`; reconciliation uses it for `SinkPublisher<T>` and defaults
  to `java.lang.Object` when omitted. Publish a value compatible with that declared type and with the
  host sink's accepted representation. Do not guess a type from the sink's name or hand-wire a publisher.
- Literal configuration is not a graph reference. The current reconciler cannot infer a new class's
  constructor types from literal or inline-bean arguments; do not add a numeric `constructor-arg value`
  and assume it will generate a threshold field. For a fixed rule, a `static final` constant avoids
  constructor mapping. If the value must be configurable, agree the source/ownership change explicitly
  and validate against the pinned authoring contract before changing XML; do not fabricate a recipe.

## Raise a derived event

A node that raises a follow-on event — `ITEM_AVAILABLE` after a return, say — asks for the dispatcher with a
field `@Inject EventDispatcher dispatcher;` (`com.telamin.fluxtion.runtime.annotations.builder.Inject`,
`com.telamin.fluxtion.runtime.callback.EventDispatcher`). The field is a graph change: regenerate after adding
it, or the processor never sets it and the first dispatch fails with a NullPointerException. In the body,
`dispatcher.queueReentrantEvent(event)` appends the event to the queue, so derived events run one at a time, in
the order raised, after the current cycle finishes (`processReentrantEvent` puts it at the front instead). Each
is its own audit record.

## Replace the starter's sample nodes

Remove the sample beans from the XML and regenerate: an unchanged, unreferenced sample file is deleted
(starter-core 1.0.86+); an edited one, or one still named by host wiring such as `server-config.yml` or the
main class, is kept, and the reconciliation report says what references it. To give up a generated class or
member explicitly: `java -jar .fluxtion/fluxtion-starter-core.jar withdraw --class <fqcn> [--member <name>]`
(`--still-declared` while the XML still names it). Never delete the ownership record by hand.

## Recover a partial generation attempt

Regeneration can write a class shell and update `fluxtion-authoring.json` before the following build
fails. Keep those outputs, read the attempt's `preflight` and `build` stages, fix the reported cause,
and rerun `./generate.sh`. Do not hand-write a replacement class, edit the receipt to say success, or
substitute bare Maven: it does not request the authoring diagnostics or update the attempt record.
For compiler sign-in and older starters that reject it, follow `run-mongoose-server` and the project's
Compiler sign-in / RUNBOOK instructions. A successful separate build is useful evidence, but does not
complete the authoring attempt.

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

**The audit is evidence of dispatch and decisions, not a byte copy of business output.** The generated
handlers make filter text safe for the reader by replacing every character outside `a-zA-Z0-9_. /@+-` with
`_`, and code that copies the pattern logs `16_30` for `16:30`. Check exact output text in the sink's output,
never in the audit.

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
