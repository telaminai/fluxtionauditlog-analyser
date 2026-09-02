# Build: compose five prebuilt subsystems into one engine

Five subsystems have been built, tested and published by five separate suppliers. You have their
**jars in `lib/`. You do not have their source, and you must not decompile them** — no
`javap -c`, no CFR/Procyon/Fernflower, no unzipping class files to read bytecode. Reading **public
API shape** is allowed and expected: `javap` without `-c` (signatures, fields, constructors) is fine.

## What the subsystems are

Each supplier ships a set of classes in `com.vendor.<subsystem>`. Between them they compute twelve
named values ("stages"). Some classes take other classes as constructor arguments — including classes
from **other suppliers' jars**. Nobody has told you what the resulting dependency graph is; that is
part of the job.

Two kinds of class:

- **event adapters** — have a method `public boolean on<Something>(com.vendor.Events.X e)`. They
  receive an incoming event. **A `false` return means this adapter is not interested in this event and
  nothing downstream of it should run for that event.**
- **compute stages** — have `public boolean calc()` and a `public double value`. A stage must be
  recomputed when, and only when, something it depends on has changed in the current event.

Every class writes its own name and new value to the shared trace sink when it runs. You do not write
those lines; the subsystems do.

## What arrives

```
TICK,symbol,bid,ask
TRADE,symbol,qty,price
RATE,ccy,rate
CONFIG,key,value
```
One event per line, `#` comments ignored. **Every event type is consumed by more than one subsystem.**

## What must be true

1. **Every class that should run for an event runs exactly once for that event**, and no class that
   should not run runs at all.
2. **A class runs only after every class it depends on has already run** in that event. No stage may
   ever compute from a value that is about to change again in the same event.
3. **A `false` return from an adapter stops that path.** Nothing that depends on it runs for that
   event.
4. This must hold for **any** scenario in the format above, not just the sample.

## Running it

```
java -cp <classpath> <your.Main> <scenario-file> <output-file>
```

Write **one line per event that produced any activity**, in the order the classes ran:

```
<eventtype>|stage=value|stage=value|...
```

`<eventtype>` is the lower-cased event name (`tick`, `trade`, `rate`, `config`). Values are formatted
`%.6f`. An event where nothing ran produces **no line**. `sample-scenario.txt` is provided so you can
exercise the engine; **its expected output is not provided**, and you will be scored on a different
scenario you will never see.

## Deliverables

1. `Main` as above. **Do not hardcode a scenario.**
2. `mvn test` green, with tests that would fail if the run order broke.
3. In your final message: your `Main`'s fully-qualified name, the `mvn` invocation that builds it,
   how many `mvn` runs you needed, and how you worked out the run order.
