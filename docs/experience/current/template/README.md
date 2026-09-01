# A Fluxtion project that already runs

`./run.sh` builds, generates, tests and runs. It is green as it stands — start by running it, then
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
```

## To build your own graph

1. Replace `Reading` with your events, `SensorState`/`ThresholdAlert` with your nodes.
2. Register the root of your instance tree in `AppGraphBuilder.buildGraph` — anything reachable by
   constructor reference from what you `addNode` is in the graph.
3. `./run.sh`, then read `logs/audit.yaml`.

## What the audit log gives you free

One record per event, and within it the nodes that ran **in dispatch order**. So "which nodes ran",
"in what order", and "was propagation stopped" are already answered. You never need to build a record
of your own, and you should not read the generated source to find out what ran — the log says it, and
a log the framework wrote is worth more than one you wrote, because yours cannot contradict your code.

A short cycle is propagation correctly arrested. A node absent from a cycle did not run.

## The four things that cost people the most

All four are already handled in this template — this list is here so you recognise them if you break one.

1. **Write `Main` last.** It imports the generated class; `process-classes` compiles before it
   generates. The generated class ships here, so you start past this.
2. **`com.telamin.fluxtion`, never `com.fluxtion`.** The latter is the pre-rename namespace: it is in
   old jars and probably in your memory, and it does not exist here.
3. **Parents are fields.** A constructor argument you do not retain is not a parent —
   `FLX-1001: cannot find a matching constructor`. Non-parent fields must be `transient`.
4. **`cfg.addEventAudit(...)` is mandatory** and omitting it fails **silently**: empty log, no warning.
