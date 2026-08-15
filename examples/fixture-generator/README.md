# Fixture generator

Regenerates the analyser's topology test fixtures from a **real Fluxtion build**:

| Fixture | Produced by |
|---|---|
| `src/test/resources/topology/demo-quote-processor.graphml` | the Fluxtion compiler (`setGenerateDescription(true)`) |
| `src/test/resources/topology/demo-quote-audit.yaml` | running that generated processor with audit logging at INFO |

```bash
mvn -o clean compile exec:exec        # writes both fixtures, byte-reproducible
```

Standalone on purpose — **not** a module of the analyser build. It pulls the Fluxtion compiler in; the
analyser keeps FlatLaf as its only runtime dependency and CI must not resolve a compiler to run unit
tests.

## Why the fixtures are generated and not written by hand

They are a **matched pair from one build**: the instance ids in the log are exactly the node ids in the
graph. A hand-edited `.graphml` drifts from the log it claims to describe, and that drift renders
perfectly while being wrong — which is the failure the analyser's build-mismatch warning exists to catch.
An earlier version of these fixtures was hand-written, and its dispatch order was fiction.

The clock is pinned to a fixed instant so regeneration is byte-reproducible. Otherwise every run rewrites
the log with new timestamps, a real change is lost in the noise, and nobody re-runs it.

## The graph

```
MarketDataEvent → priceListener → spreadCalculator → quotePublisher ← orderTracker ← OrderUpdateEvent
                  (logs)          (SILENT)           (logs)
```

`spreadCalculator` deliberately does not extend `EventLogNode`, so it **runs on every market-data cycle
and never appears in `nodeLogs`**. That is the case the topology view must not report as "did not run",
and it is why these fixtures are worth generating rather than mocking.

## Why not `fluxtion-maven-plugin`

The plugin is the right way to build a Fluxtion *application*, and its `scan` goal binds to
`process-classes` so the builder is compiled before it runs. But that goal calls a hosted
source-generation service and needs an API key at build time. These fixtures must be regenerable by
anyone with a checkout and no credentials, so this calls the compiler directly — `Fluxtion.compile`
writes the description *and* returns a live processor, so one pass produces both artefacts with no
generated sources to compile.

Note it forks a JVM (`exec`, not the in-process `java` goal): Fluxtion compiles the generated processor
with javac, which needs a real classpath rather than Maven's isolated classloader.
