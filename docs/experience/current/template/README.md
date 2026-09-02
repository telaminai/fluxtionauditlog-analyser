# A Fluxtion project that already runs

`./run.sh` builds, generates, tests and runs. `mvn clean test` alone also works, from any state. It is green as it stands — start by running it, then
change it. Every non-obvious rule this framework enforces is already applied here and commented at the
place it applies, so the working code is the reference.

```
src/main/java/com/acme/app/
  Reading.java              an event — a plain record
  SensorState.java         a node: @OnEventHandler, transient state, boolean return stops the cycle
  ThresholdAlert.java           a node: @OnTrigger, and a PARENT IS A FIELD
  AppGraphBuilder.java   the builder Maven calls — including the audit line you cannot omit
  Main.java              wiring the audit log, lifecycle, and onEvent(Object)
  generated/AppProcessor.java   written by the generator; edit nothing here
src/test/java/com/acme/app/
  AppTest.java           tests that assert on the AUDIT LOG, not on node state
run.sh                   build + generate + test + run, with the classpath incantation
trace.sh                 show what actually ran, per event cycle — your first debugging tool
```

## What makes a node run — the whole of it

A node's **fields are its parents**. By default **every parent is a trigger**: when a parent propagates,
this node runs. That default is the right one for inputs and the wrong one for lookups, and choosing
wrongly is the commonest correctness bug in this framework — it compiles, the tests pass, and only the
audit log shows it.

| annotation | on | what it does |
|---|---|---|
| `@OnEventHandler` | method | entry point for an event type. Returning `false` stops the cycle here |
| `@OnTrigger` | method | runs after any triggering parent propagated. Returning `false` stops the cycle here |
| `@OnParentUpdate` | method | called per parent that updated — use it when you must know *which* one |
| **`@NoTriggerReference`** | **field** | **this parent is data only.** Read it; never be triggered by it |
| `@TriggerEventOverride` | field | this parent is the *only* trigger; every other field is treated as `@NoTriggerReference`. One annotation instead of many |
| `@AfterTrigger` | method | runs in the after-event phase, in reverse topological order |

`ThresholdAlert` shows the distinction: `sensorState` triggers it, `limitStore` is
`@NoTriggerReference`. Run `./run.sh` and read the log:

```
cycle 5: Reading(120.0)      ['sensorState', 'thresholdAlert']
cycle 6: Limit(temp, 150.0)  ['limitStore']      <- the limit CHANGED and the alert did not run
```

Delete that one annotation and cycle 6 runs `thresholdAlert` too — re-judging the *previous* reading,
on a cycle where nothing was measured. Three independent authors have shipped that bug.


## Why the steps are in this order

Building an event-driven app is a **combinatorial problem made of known parts**. You have an
orchestration builder you trust and nodes you trust. You hand the nodes to the builder, it produces the
graph, and then there are exactly two things that can be wrong and two places to look:

| question | answer it with | if wrong, fix |
|---|---|---|
| **is the path right?** did the nodes you expect run, in the order you expect | the audit log / `./trace.sh` | the **orchestration** |
| **is the behaviour right?** given that path, are the values and decisions correct | the audit log and your tests | the **node** |

**Fix one or the other, never both at once.** That is the whole discipline. A wrong value in a node you
have not proved is reachable tells you nothing — you cannot know whether the number is wrong or the node
simply never ran with the inputs you assumed. Change both together and you learn nothing from the
result, because two variables moved.

This is why the build order puts shell nodes before logic. With every node empty, **the only thing that
can be wrong is the path**, so a single trace answers the first question completely. Once the path is
proved, every later failure is a node, and the log names which one. Authors who skip this — including
the author of this file, on the first attempt — spend their build cycles unable to tell the two apart.

**Batch freely within a dimension. Never batch across one.**

Build cycles are the dominant cost — over 90% of a run is re-reading the conversation, so every extra
`mvn` round trip is charged the whole accumulated context. So do batch: write all your shell nodes
before the first trace, implement several node bodies before rebuilding. Within one dimension a failure
is still unambiguous, because the other dimension is already proved.

What you must not do is change the path and a node in the same cycle. Then a wrong answer has two
possible causes and the cycle taught you nothing — you pay for it and learn nothing, which is the worst
trade available. The author of this file did exactly that on his first attempt at a large spec: thirteen
nodes and their wiring in one pass, then three consecutive failed builds unable to distinguish a
structural error from a logic one.

The two places this matters most:

- **Steps 4 and 5 are different dimensions and must not be collapsed.** Unit-testing a node's logic and
  testing that it fires when driven by events are separate questions. A node whose logic is correct and
  which is never reached passes the first and fails the second — that exact defect cost one author four
  probes, with nine traces run and none on the path that mattered.
- **Never fix a value in a node whose path you have not proved.** You cannot tell a wrong number from a
  node that never ran with the inputs you assumed.

## How to build this — follow the steps, in order

Do not design the whole engine and then build it. Build the skeleton, prove it runs, then fill it in.
Each step below is cheap to check and rules out a whole class of failure, so a bug found at step *n*
cannot be hiding behind anything from steps 1..n-1.

**Step 1 — a graph exists and runs.** `GraphExistsTest` ships green and proves the generator produced a
processor and at least one node ran in it. Keep it passing from your first commit. If it fails, nothing
below is worth attempting: your builder registered nothing, or `configureGeneration` set no class name.
This failure is otherwise silent and has cost two authors fifteen build cycles each.

**Step 2 — shell nodes, no logic.** Write every node you think you need with **no implementation**: the
fields that make it a parent, the `@OnEventHandler` or `@OnTrigger` method, one `auditLog.info(...)`
line, and `return true` or a hardcoded value. Nothing else. This is quick and it is throwaway thinking
only if you skip it.

**Step 3 — prove the orchestration with the shells.** Register them, run `./trace.sh` on a scenario per
event type, and check the audit log: **for each event, do exactly the nodes you expect appear, in the
order you expect?** A node missing from a path is a wiring problem — not reachable, parent isn't a
field, or a `@NoTriggerReference` on the wrong field. A node appearing where it should not is the
reverse. **Fix all of this before writing a single line of business logic.** This is the only thing the
graph can get wrong, and with empty nodes it is the *only* thing that can be wrong.

**Step 3b — for every EDGE rule, list what turns it OFF as well as ON.** An EDGE fires when something
becomes true. Write down, per rule, the complete set of events that can make it *stop* being true and
then true again — and check each of those paths reaches the deciding node in the trace. This is the one
failure the shell-node trace cannot show you, because with empty nodes there is no "became true again"
to observe. Two independent authors shipped engines that emitted a decision the first time and never
again, because the node was reachable from the event that first satisfied it and not from the events
that could re-satisfy it later.

**Step 4 — implement one node, and unit test its logic directly.** Not through the graph. Construct the
node, call its method, assert the result. This test does not involve the framework at all.

**Step 5 — test that the same node fires when driven by events.** Now through the graph: feed the events
that should reach it, and assert from the audit log that it ran and decided what you expect. Steps 4 and
5 are different questions and both are needed — a node whose logic is right can still never be reached.

**Step 6 — repeat 4 and 5 for every node**, then assemble and test the whole engine.

## When something does not work, ask these in order

Do not guess and do not restructure the builder hopefully. Work down the list; the first "no" is the bug.

1. **Is the node's logic right?** Unit test it directly, no framework. If it is wrong here, the graph is
   irrelevant.
2. **Does the node fire when driven by events?** Feed the events through the graph and read the audit
   log. If it never appears, it is a wiring problem, not a logic problem. Check every event that should
   reach it, not just the obvious one — a node reachable from one path and not another produces a
   decision that fires once and then never again.
3. **Is the graph built?** Run `GraphExistsTest`. If it fails, your builder registers nothing.
4. **Does the orchestration behave?** `./trace.sh <scenario>` — per event, which nodes ran and what came
   out. If the right nodes ran and no decision appeared, the decision is being lost between the node and
   your output, and nothing in the graph is wrong.

`./trace.sh` is what you use **when a test fails**. It is not a substitute for tests: it shows you only
the scenarios you thought to run, and the bugs that survive are always in the ones you did not.

## The two phases: event-in, then unwind

A cycle has **two** phases, and the second is the one people miss.

**Event-in** runs your `@OnEventHandler` and `@OnTrigger` methods in **topological order** — parents
before children. Returning `false` stops the cycle there.

**After-event (the unwind)** runs `@AfterTrigger` methods in **reverse topological order** — children
before parents. This is where you commit, flush, publish, or release: work that must happen only once
the whole cascade has settled, and must happen inside-out.

```java
@AfterTrigger
public void commit() {
    auditLog.info("commit", "sensorState");
    Cycle.committed("sensorState");
}
```

Two properties you get for free, and both matter:

- **Reverse order is derived**, not declared. `thresholdAlert` commits before `sensorState` because it
  is downstream. You never write that ordering.
- **Only what ran, commits.** `@AfterTrigger` fires only for nodes that were on the execution path this
  cycle. The generated dispatch guards it — `if (isDirty_limitStore) { limitStore.commit(); }` under a
  comment reading *"event stack unwind callbacks"*. A node that evaluated and propagated nothing does
  not commit, so the commit record is an honest statement of what happened rather than a list of
  everything that exists.

Run `./run.sh` and read `logs/decisions-cycles.txt`:

```
2,sensorState|thresholdAlert|commit:thresholdAlert|commit:sensorState
```

Evaluation outward, commit inward. `@AfterEvent` is the sibling annotation that fires on **every**
cycle regardless of the execution path — use it for housekeeping, not for commits.


## To build your own graph

1. Replace `Reading` with your events, `SensorState`/`ThresholdAlert` with your nodes.
2. Register the root of your instance tree in `AppGraphBuilder.buildGraph` — anything reachable by
   constructor reference from what you `addNode` is in the graph. **Keep a class implementing
   `FluxtionGraphBuilder`**: it is what the Maven goal scans for, and with none present generation
   silently produces nothing. Never hand-write into the `generated` package — that is output.
3. `./run.sh`, then read `logs/audit.yaml`.

## What the audit log gives you free

One record per event, and within it the nodes that ran **in dispatch order**. So "which nodes ran",
"in what order", and "was propagation stopped" are already answered. You never need to build a record
of your own, and you should not read the generated source to find out what ran — the log says it, and
a log the framework wrote is worth more than one you wrote, because yours cannot contradict your code.

A short cycle is propagation correctly arrested. A node absent from a cycle did not run.

## The four things that cost people the most

All four are already handled in this template — this list is here so you recognise them if you break one.

1. **A class that imports the generated processor cannot normally compile**, because generation runs
   after compilation — hence the usual advice to "write `Main` last". **This pom removes the problem**:
   it compiles in two passes and deletes the stale generated source every build, so changing a node's
   shape and running `mvn clean test` just works. If you copy the pom, copy the `generated.dependents`
   property with it.
2. **`com.telamin.fluxtion`, never `com.fluxtion`.** The latter is the pre-rename namespace: it is in
   old jars and probably in your memory, and it does not exist here.
3. **Parents are fields.** A constructor argument you do not retain is not a parent —
   `FLX-1001: cannot find a matching constructor`. Non-parent fields must be `transient`.
4. **`cfg.addEventAudit(...)` is mandatory** and omitting it fails **silently**: empty log, no warning.
