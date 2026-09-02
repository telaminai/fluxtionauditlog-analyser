# A Fluxtion project that already runs

`./run.sh` builds, generates, tests and runs. `mvn clean test` alone also works, from any state. It is green as it stands — start by running it, then
change it. Every non-obvious rule this framework enforces is already applied here and commented at the
place it applies, so the working code is the reference.

```
src/main/java/com/acme/app/
  Reading.java              an event — a plain record
  SensorState.java         a node: @OnEventHandler, transient state, boolean return stops the cycle
  ThresholdAlert.java           a node: @OnTrigger, and a PARENT IS A FIELD
  (no builder class — the bean file IS the graph)
src/main/fluxtion/designer/application-context.xml   the graph: beans, refs, eventTypes, logLevel
scaffold.sh              read the bean file, write a shell class for every missing bean
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


## How to build this — follow the steps, in order

Do not design the whole engine and then build it. Build the skeleton, prove it runs, then fill it in.
Each step below is cheap to check and rules out a whole class of failure, so a bug found at step *n*
cannot be hiding behind anything from steps 1..n-1.

**Step 1 — a graph exists and runs.** `GraphExistsTest` ships green and proves the generator produced a
processor and at least one node ran in it. Keep it passing from your first commit. If it fails your bean
file declared no usable nodes. This failure is otherwise silent.

**Step 2 — declare the graph in XML, then generate the shells.** The graph lives in
`src/main/fluxtion/designer/application-context.xml`. **Every node is a bean; every parent is a
`<constructor-arg ref="…"/>`.** Adding a node means adding a bean — nothing else registers nodes.

Then run **`./scaffold.sh`**. It reads the bean file and writes a shell class for every bean whose class
does not exist yet, deriving constructor parameters from the `constructor-arg ref` entries. You do not
hand-write skeletons: the XML is the design and the script makes it compile.

```
  created shell src/main/java/com/acme/app/OrderStore.java
  skip com.telamin…FluxtionSpringConfig — provided by a dependency
```

Declare your event types in the `fluxtionSpringConfig` bean's `eventTypes` list, and keep its
`<property name="logLevel" value="INFO"/>` — **that is what enables the audit log**, and without it
every check below is blind. Fill in the generated shells at step 4, not before.

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

## To build your own graph

1. Replace `Reading` with your events, `SensorState`/`ThresholdAlert` with your nodes.
2. Declare each node as a bean with its parents as `constructor-arg ref`, then run `./scaffold.sh`.
   Never hand-write into the `generated` package — that is output. **Never generate into a framework
   package either**: a stub over a framework class shadows the real one and produces errors that look
   like framework bugs.
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
   property with it. **Scope it by package, not by filename** — this template holds back `**/app/*.java`,
   so anything importing the generated processor goes in `…app` and anything you want declared as a bean
   goes anywhere else. Naming a single file there is what made six sessions rename a class to find out
   why pass 2 could not see `com.acme.generated`; excluding *all* source is what then made a consumer
   class impossible to declare as a bean.
2. **Never type an annotation import from memory.** `com.telamin.fluxtion`, never `com.fluxtion` — the
   latter is the pre-rename namespace, it is in old jars and probably in your memory, and it does not
   exist here. Beyond that, the sub-package is easy to get wrong in both directions:
   `@NoTriggerReference` is in `runtime.annotations`, `@FluxtionIgnore` is in
   `runtime.annotations.builder`, `@ServiceRegistered` is in `runtime.annotations.runtime`.
   **[`FQN.md`](FQN.md)** lists every one with its exact package, read out of the runtime jar rather
   than written down; regenerate it with `python3 tools/gen-fqn.py`. This is the single most repeated
   mistake in this project's history — it is loud, but it still costs a build every time.
3. **Parents are constructor-arg refs in the XML, and fields in the class.** A constructor argument you do not retain is not a parent —
   `FLX-1001: cannot find a matching constructor`. Non-parent fields must be `transient`.
4. **`cfg.addEventAudit(...)` is mandatory** and omitting it fails **silently**: empty log, no warning.
