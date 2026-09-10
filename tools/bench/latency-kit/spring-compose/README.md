# Composing the quoting core from supplier jars

The same ten-node venue-lifecycle graph the benchmark measures, assembled instead from **three
independent supplier jars and one Spring file**. Nothing here writes graph code.

## The suppliers

The graph's classes were split the way a real integration would be, each into its own artefact:

| jar | package | ships |
|---|---|---|
| `acme-marketdata-2.3.jar` | `acme.marketdata` | `Cycle`, `MarketTick`, `MarketState`, `SignalState` |
| `bolt-pricing-1.7.jar` | `bolt.pricing` | `FairValue`, `QuoteBuilder` |
| `cedar-oms-4.1.jar` | `cedar.oms` | `Execution`, `OrderUpdate`, `TimerTick`, `WorkingOrders`, `InventoryState`, `FreshnessState`, `RiskLimits`, `OrderDiff`, `IntentPublisher` |
| `shared-venue-1.0.jar` | `shared.venue` | `Venue` — tick scale, side and action encodings, limits |

Each is compiled against **`fluxtion-runtime` only**. No supplier has a build-time dependency on the
compiler, and none of them knows the graph it will be composed into.

## The integrator's authoring act

`quoting-core.xml` — eleven beans and their references. That is all of it. Nobody writes the dispatch
order; the compiler derives it from the references and the annotations on the supplied classes.

`BuildFromSpring.java` hands Fluxtion a `URLClassLoader` over the jars, the XML, and the one decision
that is genuinely the integrator's: which performance profile the deployment wants.

## What comes out

Verified against the hand-built graph in `dsl/GenVenueCore.java`:

- **Every causal path is identical.** MarketTick, Execution, OrderUpdate and TimerTick dispatch
  sequences and the shared tail all match call for call.
- **Zero guards, zero dirty flags** — once `LOWEST_LATENCY` is applied. Built without a profile the
  processor carries dirty-flag guards, because the framework default keeps conditional propagation.
  That is a deployment decision and it belongs to the integrator, not to any supplier.
- **All eleven bean ids appear as GraphML node ids**, so the names typed in the XML are the names in
  the topology the analyser renders and in the audit records.
- Classes from all three suppliers sit side by side in one generated processor with no indirection
  between them.

## Two details worth knowing

**Spring is `provided` scope in `fluxtion-builder`.** Fluxtion does not bundle it; the integrator
supplies the Spring version they already run. The build here uses 6.2.3.

**Close the context.** `FileSystemXmlApplicationContext` keeps a non-daemon thread alive, so a build
harness that does not close it will generate correctly and then hang rather than exit.

## Running it

```bash
javac -d out -cp "<builder-cp>:<spring-cp>" BuildFromSpring.java
java -cp "out:<builder-cp>:<spring-cp>" BuildFromSpring <jarDir> quoting-core.xml <outDir>
```
