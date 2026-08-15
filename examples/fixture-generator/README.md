# Fixture generator

Regenerates the analyser's topology test fixtures from a **real Fluxtion AOT build**:

| Fixture | Produced by |
|---|---|
| `src/test/resources/topology/demo-quote-processor.graphml` | `fluxtion-maven-plugin` (`scan` goal, `setGenerateDescription(true)`) |
| `src/test/resources/topology/demo-quote-audit.yaml` | running the generated processor with event audit on |

```bash
mvn process-classes exec:java -Dexec.mainClass=com.acme.demo.GenerateFixtures
```

`process-classes`, not `compile` — the plugin's `scan` goal binds to that phase, which is where it can see
the compiled `FluxtionGraphBuilder`.

The build follows the [project starter](https://fluxtion-playground.dev/start) output: the `fluxtion-bom`
for coordinates, `fluxtion-runtime` at compile scope, `fluxtion-builder` `provided`, and the AOT plugin.
It is standalone — **not** a module of the analyser build, which keeps FlatLaf as the analyser's only
runtime dependency and stops CI needing a Fluxtion toolchain to run unit tests.

> **Regenerating the graph needs a Fluxtion API key** (`~/.fluxtion/fluxtion.apiKeyFile`) — the compiler
> is closed-source and the `scan` goal calls the hosted source-gen service. The generated processor is
> **checked in**, as the starter intends, so regenerating just the *audit log* works from a bare checkout.

## Why generated, not hand-written

The two fixtures are a **matched pair from one build**: the instance ids in the log are exactly the node
ids in the graph. A hand-edited `.graphml` drifts from the log it claims to describe and still renders
perfectly — the failure the analyser's build-mismatch warning exists to catch. An earlier version of these
fixtures was hand-written, and its dispatch order was fiction.

Two details keep regeneration honest:

- **the clock is pinned** to a fixed instant, or every run rewrites the log with new timestamps and a real
  change is lost in the diff noise;
- **`EventLogControlEvent` records are filtered out** — attaching the audit listener is itself audited,
  and that record embeds the listener lambda's identity hash, which differs every run.

## The graph

```
MarketDataEvent → priceListener → spreadCalculator → quotePublisher ← orderTracker ← OrderUpdateEvent
                  (logs)          (SILENT)           (logs)
```

`spreadCalculator` writes no `auditLog` entries, so it **runs on every market-data cycle and never appears
in `nodeLogs`** — the case the analyser's topology view must not report as "did not run".

That only holds because the builder calls `cfg.addEventAudit()` **without a level**. Passing
`LogLevel.INFO` additionally traces every node invocation (`thread` + `method` per node), which makes every
executed node appear — useful in itself, but the opposite of the case these fixtures capture, and unlike
the production logs the analyser is usually pointed at.

## Reference

The authoring rules are documented, not guesswork — see
[the golden path](https://fluxtion-playground.dev/fluxtion-golden-path.md) and
[the agent guide](https://fluxtion-playground.dev/CLAUDE.md). Two rules this module leans on:

- **the boolean returned by a handler or trigger is the dirty/propagation control** — `true` propagates to
  dependents, `false` stops the branch. That is exactly why an audit log is not a complete record of
  execution, and why the analyser models "may have run" as a distinct state;
- **load the generated processor reflectively** (`Class.forName`), so this module compiles before the
  processor it runs has been generated.
