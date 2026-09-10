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

## Measured

The Spring-composed processor was run through the same harness (`BenchSpringCore.java`, generated from
`dsl/BenchVenueCore.java` by substituting type names only — measurement logic, venue model, workload
and all twelve invariants unchanged).

| | Spring-composed | builder-API | delta |
|---|---:|---:|---:|
| throughput, unaudited | 23.440 ns | 23.583 ns | 0.6% |
| throughput, audited | 32.805 ns | 33.651 ns | 2.5% |
| causal latency, unaudited | 24.882 ns | 24.932 ns | 0.2% |
| causal latency, audited | 35.108 ns | 35.006 ns | 0.3% |

All `measure.sh` REPEATABLE; every delta is inside the JIT noise band.

## The audit-log oracle

Both builds replayed the same 300,000-event stream under a **data-driven clock**, wrote binary audit
logs, and were decoded record-for-record:

```
records=67247  entries=533328
sha256 builder: 097cbeee8c1633ccd7b528d7fcc79834
sha256 spring : 097cbeee8c1633ccd7b528d7fcc79834
```

**Identical, timestamps included.** The `.flxa` files differ by 22 bytes — the dictionary interns the
fully-qualified event-type names, and `acme.marketdata.MarketTick` is longer than
`app.GenVenueCore$MarketTick`. Every node id, key, value and timestamp matches.

Run without the data-driven clock the record headers differ, because `eventTime` and `logTime` then
come from the wall clock and the two runs happened seconds apart. That is worth knowing before
comparing two logs of anything.

## An invariant that fired, correctly

The first Spring run was REFUSED by invariant 10 — *the processor loaded is the build being tested* —
which asserted a hardcoded `app.gen.VenueCoreProcessor`. The Spring build is `app.spring.SpringQuotingCore`,
so the check was right and could not know the substitution was intended. It is now a declared
expectation (`-DexpectProcessor`, defaulting to the type the harness was compiled against) rather than
a literal, so it still catches a stale or substituted class arriving from the classpath.

## The same wiring, targeting C++

`BuildFromSpringCpp.java` is the same build with `-DgenId=cpp`. Three supplier jars and the same XML
emit a **C++** processor:

```
  MarketTick : market.onTick signal.onMarket fair.onInputs quote.onFairValue risk.onQuote diff.onInputs intent.onDiff
  Execution  : working.onExecution inventory.onExecution fair.onInputs quote.onFairValue risk.onQuote diff.onInputs intent.onDiff
  OrderUpdate: working.onOrderUpdate diff.onInputs intent.onDiff
  TimerTick  : freshness.onTimer risk.onQuote diff.onInputs intent.onDiff
```

Against the C++ emitted from the builder-API graph: **345 lines each, identical multiset of lines**,
differing only in order. So a supplier's Java-annotated nodes reach a C++ deployment without the
supplier writing any C++ — which was asserted in the docs before it was demonstrated, and now is.

## Running it

```bash
javac -d out -cp "<builder-cp>:<spring-cp>" BuildFromSpring.java
java -cp "out:<builder-cp>:<spring-cp>" BuildFromSpring <jarDir> quoting-core.xml <outDir>
```
