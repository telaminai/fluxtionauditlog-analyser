# Vendor component integration — predictions, written BEFORE the attempt

**Written:** 2026-09-19, before any vendor code exists and before looking at how the starter or the generated
dispatcher treat classes that come from a jar. Confidence is my probability that the stated outcome happens.
Results are appended below the line at the end; nothing above the line is edited afterwards.

## The experiment

A vendor ("Acme Risk") sells a risk component as a jar: `acme-risk-1.0.0.jar`, compiled against
`fluxtion-runtime` only. It contains annotated Fluxtion nodes and its own event types:

```
com.acmerisk.RiskEngine            root: the one class a customer references
  └─ VarCalculator                 @OnTrigger: per-symbol and total value-at-risk
       ├─ QuoteFeed                @OnEventHandler RiskQuote: last price + EWMA volatility
       └─ PositionFeed             @OnEventHandler RiskPosition
com.acmerisk.api.RiskQuote, RiskPosition   vendor event types
com.acmerisk.api.RiskControl               @ExportService: setVarLimit(double)
```

The bank (this project) adds the jar to the classpath, declares ONE bean of class `com.acmerisk.RiskEngine`
in the Spring XML, and puts one host node (`RiskLimitGuard`) downstream of it. Claim under test: Fluxtion
builds the combined graph from that single reference.

I will write the vendor jar the way a vendor with normal Java instincts would — encapsulated internals — and
only relax that if the build forces me to. What it forces is itself a finding about what "certifiable" means.

## Predictions

| # | Step | Prediction | Conf. |
|---|---|---|---|
| P1 | Compile the vendor jar against `fluxtion-runtime-1.0.15` alone (no builder, no Spring) | PASS | 95% |
| P2 | Add the jar as a dependency | PASS, **but** `generate.sh` reads the cached `.fluxtion/classpath`, so without re-resolving it the build cannot see the jar. A trap, not a defect | 85% |
| P3 | `validate.sh` with `<bean class="com.acmerisk.RiskEngine">` | PASS — validation never loads classes | 95% |
| P4 | **Naive route:** list the vendor bean in `nodeBeans` and run `generate.sh` | **FAIL, and badly.** Regenerate works from *source*, not bytecode. It will not find `RiskEngine.java`, conclude the class does not exist and write a skeleton for it into my source tree. `target/classes` precedes the jar on the classpath, so an empty shell would **silently replace the certified class**. Alternatives: it refuses with a conflict (25%), or correctly leaves a classpath class alone (15%) | 60% |
| P5 | **Reference route:** vendor bean NOT in `nodeBeans`, only referenced by `constructor-arg` from a host node | PASS — the contract says referenced children are discovered by Fluxtion | 75% |
| P6 | Encapsulated vendor: public no-arg constructor, private wiring constructor, package-private internal nodes | **FAIL.** The generated processor lives in `com.example.myapp.generated` and reconstructs every node with `new`, so internals must be public with a public constructor matching their `final` fields. Expect "cannot find matching constructor" / `FLX-1009` or an access error in generated source | 80% |
| P7 | After opening the vendor classes up: internal nodes are discovered through `RiskEngine`'s fields | PASS — 3 extra vendor nodes in the graph from one bean reference | 85% |
| P8 | Names of the discovered vendor nodes in the audit log and graph | Auto-generated (`varCalculator_12`-style), not chosen by me or the vendor. Usable but ugly, and possibly unstable across builds — which matters for analyser series keys and for log diffing | 70% |
| P9 | Spring `<property name="varLimit" value="…"/>` on the vendor bean reaches the generated processor | PASS if the vendor exposes getter + setter; silently dropped if setter only | 75% |
| P10 | Vendor nodes extending `EventLogNode` appear in the host's audit log with their values | PASS | 85% |
| P11 | Vendor event types are NOT declared in XML `eventTypes` | Build still routes them — handlers are discovered from annotations, the XML list is a contract. If I do declare them, the starter will not write shells for them because they are outside the base package | 65% |
| P12 | Vendor `@ExportService RiskControl` is callable via `flow.getExportedService(RiskControl.class)` | PASS. Not reachable from the CSV feed, so checked from a small harness | 70% |
| P13 | Existing behaviour unchanged: desk day 784 log values + 33 sink messages, both old scenarios | PASS | 90% |
| P14 | Vendor VaR matches an independent calculation on a scripted feed | PASS once it runs; my own arithmetic is the likeliest error | 80% |
| P15 | Analyser: vendor nodes visible in topology; source navigation for them | Topology PASS; source FAIL ("no source mapping") — there is no vendor source under the roots | 90% |
| P16 | **Trust:** does anything record WHICH jar was built against? | **FAIL.** `fluxtion-run.json` hashes the XML, my source and the authoring record. I expect no hash of dependency jars, so swapping `acme-risk-1.0.0.jar` for a different build would leave every freshness check green. "Certified component" needs that hash in the receipt | 80% |
| P17 | Stretch — one market data row drives both desk and vendor: host `MarketPrice implements com.acmerisk.api.Quote`, vendor handler typed on the interface | Genuinely unsure whether dispatch is by exact class or honours supertypes | 50% |

## What I expect the headline to be

The core claim (P5, P7) holds and is impressive: one bean reference pulls in a sub-graph with its own event
entry points, ordering and audit. The problems will be at the **edges of the authoring workflow**, which was
built for classes it generates: P4 (the starter stubbing a class it cannot see source for) is the dangerous
one, P6 is a real constraint on how a vendor must write a certifiable component, and P16 is the gap between
"integrated" and "certified".

---

# Results — appended after the attempt

**Score: 15 of 17 confirmed, 1 half right (P2), and P17 — which I called a coin flip — worked.**
Two confirmations carry an untested clause: P9's "silently dropped if setter only" and P11's "declared vendor
events are not stubbed" were never tried, because the first route worked.
The core claim holds. The failures are where I said they would be — and P4 is worse than I predicted.

| # | Predicted | Actual | |
|---|---|---|---|
| P1 | jar builds against runtime only | Built with `javac` + `jar`, no Maven, no builder | ✅ |
| P2 | build cannot see the jar until the classpath is re-resolved | **Half right.** The *build* saw it (Maven reads the pom). The *run* did not: `run-server.sh` uses the cached `.fluxtion/classpath` → `NoClassDefFoundError: com/acmerisk/api/RiskControl` at first event | 🟡 |
| P3 | `validate.sh` passes | `XML: valid; 13 nodes, 17 edges` | ✅ |
| P4 | listing the vendor bean in `nodeBeans` makes the starter write a skeleton that shadows the certified class | **Confirmed, and worse.** See below | ✅ |
| P5 | reference route works | No stub written; vendor sub-graph discovered from one `constructor-arg ref` | ✅ |
| P6 | encapsulated vendor fails to build | `com.acmerisk.QuoteFeed is not public in com.acmerisk; cannot be accessed from outside package` ×6, and the processor calls `new RiskEngine(varCalculator_14)` — the constructor I had made private | ✅ |
| P7 | internals discovered through the root's fields | 3 vendor nodes + 2 vendor events + 1 exported service, from one bean. Discovery worked even while they were package-private; only *code generation* needed them public | ✅ |
| P8 | auto-generated names | `riskEngine_13`, `varCalculator_14`, `quoteFeed_15`, `positionFeed_16`. The Spring bean id `acmeRisk` is lost because the bean is not in `nodeBeans`. Fixed in v1.0.1 by the vendor implementing `NamedNode` → `acmeRiskEngine`, … | ✅ |
| P9 | Spring property reaches generated code | `acmeRiskEngine.setVarLimit(20000.0);` in `MyProcessor`; harness reads 20000.0 | ✅ |
| P10 | vendor audit logging appears in the host log | Yes, in dispatch order: `acmeQuoteFeed → acmeVarCalculator → acmeRiskEngine → riskLimitGuard` | ✅ |
| P11 | undeclared vendor events still route | Yes — never added to XML `eventTypes`; both appear as processor inputs in the descriptor | ✅ |
| P12 | vendor `@ExportService` callable | `getExportedService(RiskControl.class)` present; `setVarLimit(100)` took effect and flipped `breached` | ✅ |
| P13 | existing behaviour unchanged | Desk 784 + 33 PASS, both old scenarios PASS — after every vendor step including v1.1.0 | ✅ |
| P14 | VaR matches an independent calculation | 11 of 11 rows to 1e-6 (`vendor/check_var.py`), alerts published only on breach-state change (2) | ✅ |
| P15 | topology yes, source no | Topology shows the vendor chain. `analyser_source {fqn: com.acmerisk.RiskEngine}` → "class not under an authorised root" | ✅ |
| P16 | nothing records which jar was built against | **Confirmed by attack.** See below | ✅ |
| P17 | interface-typed dispatch (50%) | **Works.** See below | ✅ (I was unsure) |

## P4 — the starter silently replaces a certified class with an empty shell

With `acmeRisk` in `nodeBeans`, `generate.sh` wrote `src/main/java/com/acmerisk/RiskEngine.java`:

```java
package com.acmerisk;

public class RiskEngine {
}
```

and recorded `com.acmerisk.RiskEngine` under `ownership` in `fluxtion-authoring.json`. `target/classes`
precedes the jar, so the shell wins.

- **With** a Spring property on the bean the build fails — with a message that blames the vendor:
  `Invalid property 'varLimit' of bean class [com.acmerisk.RiskEngine]: Bean property 'varLimit' is not
  writable`. The vendor class *has* that setter. Nothing mentions that a stub now shadows it.
- **Without** the property: `validate ok · regenerate ok · preflight ok · build ok, compilerRan true`, zero
  diagnostics, and `MyProcessor` contains `new RiskEngine()` — the empty one. No `VarCalculator`, no
  `QuoteFeed`, no event handlers. **A green build in which the certified component has been deleted.**

Cause: regenerate reasons from *source*; a class that exists only as bytecode looks like a class that does
not exist yet. Wanted: before writing any skeleton, check the resolved classpath; a `nodeBeans` class found
in a dependency is `foreign — never generated, never owned`, reported in the reconciliation output. And a
source file whose FQCN also exists in a dependency jar should be a build error whoever wrote it.

## P6 — what a vendor must do to be integrable

Source generation is serialisation: the processor rebuilds every node with `new`, from another package. So a
vendor component must expose **every internal node as a public class with a public constructor matching its
`final` fields**, including the root's wiring constructor. Encapsulation of the *graph* is not available;
encapsulation of *behaviour* is (fields and helpers stayed package-private and that was fine).

Note that discovery succeeded and the failure came late, as `javac` errors in generated code. A vendor
should get this as one early diagnostic: "node X is reachable but not constructible from the generated
package". Worth a "writing a redistributable component" page: public nodes + constructors, `NamedNode` for
stable names, `transient` collections, getters and setters for configurable properties, events as public
types, and interface-typed handlers (P17).

## P16 — "integrated" is not "certified"

I rebuilt the jar under the same file name with `Z_99 = 0.0` (VaR is always 0, the limit can never breach),
dropped it in place and ran `generate.sh`.

```
genuine  c7ea80c545f947bb…   tampered 8256468880567acf…
BEFORE inputs: xmlHash 870beac4…  sourceHash 794797fb…  recordHash 542a7b08…
AFTER  inputs: xmlHash 870beac4…  sourceHash 794797fb…  recordHash 542a7b08…     <- identical
outcome ok · 'acme' appears nowhere in fluxtion-run.json
```

The tampered component ran: VaR 0.00 on every row, 0 alerts published, all freshness checks green. Only my
independent calculation caught it (9 of 11 rows mismatched). Wanted: a `dependencyHash` (or a per-artefact
list: coordinates + sha256) in the run receipt and in the analyser's freshness chain, and optionally an
allow-list in the authoring record — "this graph was certified against acme-risk 1.0.1 sha256:c7ea…". Then
"certified component" is a checkable statement, and the analyser can say *which* vendor build produced a log.

## P17 — one market data row drives desk and vendor

Vendor v1.1.0 types its handler on an interface it owns, `onQuote(com.acmerisk.api.Quote)`. The host's
`MarketPrice` record implements it (`price()` = mid). The compiler merged the two:

```java
public void handleEvent(MarketPrice typedEvent) {
    …  isDirty_acmeQuoteFeed = acmeQuoteFeed.onQuote(typedEvent);   // vendor
    …  isDirty_priceBook     = priceBook.onMarketPrice(typedEvent); // host
} else if (event instanceof Quote) { …                              // any other implementor, e.g. RiskQuote
```

All 9 `MarketPrice` records of the desk day now show the vendor's feed logging alongside the desk's, the desk
validation is unchanged (784 + 33), and the vendor's own feed still passes. This is the integration pattern
to recommend: **the vendor publishes interfaces, the customer's events implement them, nobody writes an
adapter.** I did not know Fluxtion dispatched on supertypes; it is not in any doc I was given.

Gap: the analyser topology draws `Quote → QuoteFeed` only. The `MarketPrice → acmeQuoteFeed` route, which
is what actually runs, is not an edge in the graphml.

## Not done

- **Positions are not bridged.** Quotes reach the vendor from live desk data; positions do not —
  `PositionKeeper` state is not an event, so the vendor's `totalVar` stays 0 on the desk day. The vendor's
  own feed (`RPOS` rows) drives it instead. A proper bridge needs either a re-entrant event from a host node
  or a vendor *node* interface the host's `PositionKeeper` could implement and be passed in as a
  `constructor-arg`. I did not try either.
- The vendor jar is not signed and lives at a `system`-scope path; nothing here tests Maven-coordinate
  resolution, version conflicts with the host's `fluxtion-runtime`, or two vendors using the same node names
  (`NamedNode` names are global — two jars both choosing `quoteFeed` would collide; untested).

## Files

`vendor/acme-risk/` (source, `build.sh`, `dist/acme-risk-1.0.0|1.0.1|1.1.0.jar`) · `vendor/check_var.py` ·
`config/risk-config.yml` · `data/risk-day.csv` · `src/main/java/com/example/myapp/node/RiskLimitGuard.java` ·
evidence: `evidence/risk-run3`, `evidence/risk-tampered`, `evidence/desk-with-vendor-quotes`,
`evidence/vendor-topology.png`. Project is left on **acme-risk 1.1.0, genuine build**.
