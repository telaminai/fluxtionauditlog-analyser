# Working in this project

## Read the framework reference FIRST

Two authoritative documents ship with this project, offline copies of the published set:

- **[`docs/fluxtion-claude.txt`](docs/fluxtion-claude.txt)** — the framework reference: the mental
  model, the authoring surfaces, `FluxtionGraphBuilder`, the required rules, annotation triage,
  conditional dispatch, services, sinks, anti-patterns.
- **[`docs/fluxtion-golden-path.md`](docs/fluxtion-golden-path.md)** — the worked golden path.

Read both before writing code. They are the source of truth; where this file disagrees with them about
the framework, they win.

## What those two documents do NOT tell you

Four things, each measured as a top cost in real authoring runs. All four are absent upstream.

**1. Write `Main` LAST.** `Main` imports the *generated* processor, which does not exist until
`mvn process-classes` has run — and that phase compiles your sources first, so `Main` breaks the very
step that would create the class it imports. Build the graph and generate first; add `Main` after. If
`Main` already exists, move it out of the source tree, run `mvn process-classes`, and put it back.
*Highest measured cost in the corpus, and it has caught authors who had already read a warning about it.*

**2. The package root is `com.telamin.fluxtion`, never `com.fluxtion`.** `com.fluxtion.*` is the
pre-rename namespace. It exists in old jars and probably in your memory; it does not exist here. Every
wrong import observed across every measured run was this one substitution.

Note the builder types sit under two different roots:
```java
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;   // generation, not compile
```
They come from `fluxtion-builder-api`, pulled in transitively — they are not inside the
`fluxtion-builder` artifact you declared.

**3. A node's parents ARE its fields.** A constructor argument you do not retain as a field is not a
parent, and the generator rejects the class with
`FLX-1001: cannot find a matching constructor — no constructor accepts the mapped fields`. Keep every
parent as a field; mark every non-parent field `transient`. Classes and constructors must be `public`,
one public class per file — the generated processor is in another package and calls them.

**4. Running a Maven-built processor.** The upstream reference documents jbang, which is not offline.
For Maven:
```bash
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" com.acme.Main <args>
```
`target/classes` alone gives `NoClassDefFoundError` — the runtime jar must be on the classpath.

## The audit log is your feedback channel

Enabling it takes two calls and **omitting the first fails silently — empty log, no warning**:

```java
cfg.addEventAudit(EventLogControlEvent.LogLevel.INFO);        // build time, in buildGraph
flow.setAuditLogProcessor(rec -> audit.add("---\n" + rec));   // runtime, BEFORE init()
```

The log records one entry per event and, within it, the nodes that ran **in dispatch order**. So "which
nodes ran", "in what order" and "how long was this path" are already answered — do not read generated
source for them, and do not build a record of your own. The framework's log is worth more than one you
write, precisely because yours cannot contradict your own code.
