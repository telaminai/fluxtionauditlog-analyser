# Topology test fixtures

## Real, compiler-emitted (prefer these)

`demo-quote-processor.graphml` and `demo-quote-audit.yaml` are **genuinely generated**, not hand-written:
the graphml came out of the Fluxtion compiler and the log came out of running the processor it generated.
They are a matched pair from one build, which is what makes them usable for the pairing check and for the
user-guide screenshot.

The graph is deliberately shaped around the case that matters:

```
MarketDataEvent → priceListener → spreadCalculator → quotePublisher ← orderTracker ← OrderUpdateEvent
                  (logs)          (SILENT)           (logs)
```

`spreadCalculator` extends nothing that writes audit output, so it **runs on every market-data cycle and
never appears in `nodeLogs`** — the "no audit entry does not mean it didn't run" case, in real data.

### Regenerating

Build a small Maven project against the public Fluxtion compiler (`com.fluxtion:compiler`), declare the
four nodes, and switch on the description output:

```java
cfg.setGenerateDescription(true);              // emits the .graphml
cfg.setResourcesOutputDirectory("target/generated-resources");
```

Then run the generated processor with an audit log listener at INFO, appending `---` between records:

```java
processor.setAuditLogProcessor(r -> log.append("---\n").append(r).append('\n'));
processor.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
```

## Synthetic (format coverage only)

`sample-processor.graphml` is hand-written to exercise parser edge cases the real graph doesn't contain —
an unstyled node, an inner class, a stereotype line. It is **not** a realistic dataflow and must not be
used to illustrate what a processor looks like.
