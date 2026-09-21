# Integrating a vendor component — what it takes, and what it changes for trusted systems

**Status:** evidence record, 2026-09-21 · **Experiment run:** 2026-09-19 · **Companion:**
[proposal to document this on the analyser site](site-proposal.md)

A vendor ships a Fluxtion component as a plain jar. The integrator references it once in the design, and
the compiler composes it into the host system: ordering, events, configuration, exported services and
audit trail. This document records how that works, how easy it was, and what it does and does not give a
team building systems that have to be trusted.

Everything below is taken from the experiment's own record: seventeen predictions sealed before any vendor
code existed, scored afterwards, with the evidence preserved
[in the repository](../../handoff/evidence/vendor-integration-2026-09-19/PREDICTIONS.md). Not everything is
built yet. **The integration itself works.**

---

## The setup

A fictional vendor, **Acme Risk**, sells a value-at-risk component as `acme-risk-1.x.jar`, compiled
against `fluxtion-runtime` only: no builder, no Spring, no knowledge of any host.

```
com.acmerisk.RiskEngine        root — the one class a customer references
  └─ VarCalculator             @OnTrigger: per-symbol and total value-at-risk
       ├─ QuoteFeed            @OnEventHandler: last price + EWMA volatility
       └─ PositionFeed         @OnEventHandler: positions
com.acmerisk.api.Quote, RiskQuote, RiskPosition    vendor event types
com.acmerisk.api.RiskControl                       @ExportService: setVarLimit(double)
```

The host is a principal trading desk: eight nodes, already validated against an independently written
model at 784 logged values and 33 sink messages.

## How it works

The integrator writes this, and nothing else:

```xml
<bean id="acmeRisk" class="com.acmerisk.RiskEngine">
    <property name="varLimit" value="20000"/>
</bean>
<bean id="riskLimitGuard" class="com.example.myapp.node.RiskLimitGuard">
    <constructor-arg ref="acmeRisk"/>
</bean>
```

From that one reference, the compiler walks the vendor root's fields and brings in the whole sub-graph:
**three vendor nodes, two vendor event types and one exported service**. It then derives a single dispatch
order across host and vendor together, and generates the processor.

What the integrator gets from it:

| | Evidence |
|---|---|
| **Ordering across the boundary.** Derived at compile time, not written by anyone | the audit log shows `acmeQuoteFeed → acmeVarCalculator → acmeRiskEngine → riskLimitGuard`, in dispatch order (P10) |
| **The vendor's audit trail inside the host's record** | vendor nodes log into the same evidence stream as the host's own (P10) |
| **Configuration** | the Spring property became `acmeRiskEngine.setVarLimit(20000.0);` in the generated code (P9) |
| **Control** | the vendor's `@ExportService` was callable from the host; `setVarLimit(100)` flipped the breach state live (P12) |
| **Undeclared vendor events still route** | the vendor's event types were never added to the design's `eventTypes`; both appear as processor inputs (P11) |

### No adapter code: the interface pattern

In vendor version 1.1.0 the handler is typed on an interface the vendor owns, `onQuote(com.acmerisk.api.Quote)`.
The host's `MarketPrice` record implements it. The compiler merged the two routes into one dispatch:

```java
public void handleEvent(MarketPrice typedEvent) {
    …  isDirty_acmeQuoteFeed = acmeQuoteFeed.onQuote(typedEvent);   // vendor
    …  isDirty_priceBook     = priceBook.onMarketPrice(typedEvent); // host
} else if (event instanceof Quote) { …                              // any other implementor
```

One market-data row now drives both the desk and the vendor. **The vendor publishes interfaces, the
customer's events implement them, and nobody writes an adapter.** None of the documentation available at
the time described this, and it was predicted as a coin flip (P17). It worked.

## How easy it was

**For the integrator: very.** One bean, one reference, one property.

- Of 17 predictions sealed before any vendor code existed, **15 were confirmed**, one was half right, and
  the coin flip worked.
- The vendor's VaR matched an independent calculation
  ([`check_var.py`](../../handoff/evidence/vendor-integration-2026-09-19/check_var.py)) on **11 of 11 rows,
  to 1e-6**.
- The desk's existing behaviour, **784 logged values and 33 sink messages**, was unchanged after every
  vendor step, including two version upgrades (P13).

**For the vendor: prescriptive, and worth saying up front.** The generated processor rebuilds every node
with `new`, from another package. So a vendor who wants to be integrable must:

- make every internal node a **public class with a public constructor** matching its `final` fields.
  Behaviour can stay encapsulated; the graph cannot (P6). An encapsulated first attempt failed with
  `com.acmerisk.QuoteFeed is not public in com.acmerisk` six times;
- implement **`NamedNode`**, or the nodes get generated names (`varCalculator_14`) and the integrator's
  bean id is lost (P8);
- expose **getters and setters** for anything configurable (P9);
- type handlers on **interfaces the vendor owns**, so customers can integrate without adapters (P17).

These fit on one "writing a redistributable component" page. No such page exists yet.

**Two traps.** One is minor: the run script read a cached classpath and failed with `NoClassDefFoundError`
at the first event (P2). The other is dangerous, and it is described below.

## What it changes for trusted composition

**It removes the class of defect integration work mostly consists of.** Integration bugs are usually
orchestration bugs: callbacks wired in the wrong order, a missed notification, an adapter that translates
wrongly, two components disagreeing about when something happens. Here the orchestration across the vendor
boundary is **derived, not written**. The combined order is a compile-time artefact that can be reviewed
before anything runs, and the audit log shows it being followed.

**The closed world extends over the vendor.** The vendor's internal structure is part of the host's derived
graph. So "did the vendor component react to that price?" is a checkable fact, not a question for the
vendor's support desk.

**Upgrades become provable.** Swapping vendor versions and re-running the host's validation (784 + 33,
unchanged) shows the upgrade did not change host behaviour. Normally that takes a regression campaign and
some faith.

**It fits how regulated supply chains already work.** The vendor ships a component with its own evidence,
and the integrator gets structural evidence of how it was composed. That is the reusable-component model
used in regulated industries. Integration assurance shrinks from "does the orchestration work" to "is the
vendor's computation right".

This is *compose tested components into larger systems, knowing the orchestration is correct*, demonstrated.

## What it does not give yet

**Integrated is not certified (P16).** The jar was rebuilt under the same file name with its risk constant
zeroed (`Z_99 = 0.0`, so VaR is always 0 and the limit can never breach), dropped in place, and rebuilt:

```
genuine  c7ea80c545f947bb…   tampered 8256468880567acf…
BEFORE inputs: xmlHash 870beac4…  sourceHash 794797fb…  recordHash 542a7b08…
AFTER  inputs: xmlHash 870beac4…  sourceHash 794797fb…  recordHash 542a7b08…   ← identical
outcome ok · 'acme' appears nowhere in fluxtion-run.json
```

The build was green and the component reported zero risk. Only the independent calculation caught it, with 9
of 11 rows wrong. The orchestration guarantee held perfectly; the computation was authentic and wrong. Closing
this needs dependency coordinates and digests in the run receipt. That work is deliberately excluded from
the current release.

**The dangerous trap (P4, feedback #29).** If the integrator puts the vendor bean in the Spring design's
`nodeBeans` list, the starter writes an **empty `com/acmerisk/RiskEngine.java` into the project**. Because
`target/classes` precedes the jar on the classpath, the empty class **silently replaces the vendor's**:

- without a Spring property on the bean: `validate ok · regenerate ok · preflight ok · build ok`, zero
  diagnostics, and a processor containing `new RiskEngine()` with nothing inside. **A green build in which
  the vendor component has been deleted.**
- with a property: the build fails, with a message that blames the vendor (`Bean property 'varLimit' is
  not writable`) and never mentions the stub that now shadows it.

The rule until this is fixed: **vendor classes are referenced, never listed in `nodeBeans`.** The starter
should refuse to write a skeleton for any class found on the resolved classpath.

**Not built, or not tested.** Jar signing. Maven-coordinate resolution, and version conflicts with the host
runtime. Two vendors choosing the same `NamedNode` name (names are global, so this would collide). Bridging
host state such as positions into the vendor. Source navigation into vendor classes (P15). And the analyser
draws `Quote → QuoteFeed` but not the `MarketPrice → acmeQuoteFeed` route that actually runs: tracked as
TA-9 in [the tool-agreement spec](../../specs/spec-tool-agreement.md).

**In one line:** integrating a component you did not write is proven, cheap, and removes the orchestration
risk. Certifying it needs provenance, and that is the part still to build.

---

## Reproduce it

Everything needed is in [`docs/handoff/evidence/vendor-integration-2026-09-19/`](../../handoff/evidence/vendor-integration-2026-09-19/):

| Path | What |
|---|---|
| `PREDICTIONS.md` | the 17 sealed predictions, results appended below the line, and the P4, P6, P16 and P17 write-ups |
| `acme-risk/src`, `acme-risk/build.sh` | the vendor component; builds with `javac` + `jar` against `fluxtion-runtime` only |
| `acme-risk/dist/*.jar` | one jar per vendor rule, verified from the bytecode: **1.0.0** is the encapsulated build (package-private nodes, rejected at generation); **1.0.1** makes the nodes public and adds `NamedNode`; **1.1.0** adds the interface-typed handler |
| `check_var.py`, `validate_audit.py` | the independent VaR calculation that caught the tampered build, and the audit-log parser it imports |
| `host/` | the host's `RiskLimitGuard` and the Mongoose config for the vendor feed |
| `data/risk-day.csv` | the vendor feed, at the path `check_var.py` expects |
| `runs/risk-run3`, `runs/risk-tampered` | genuine and tampered runs: audit log and server log |
| `runs/desk-with-vendor-quotes` | the desk day with one `MarketPrice` feed driving both desk and vendor |
| `runs/vendor-topology.png` | the analyser's topology with the vendor sub-graph |

To check a run, from that directory: `python3 check_var.py runs/risk-run3/audit.yaml`. Re-run from the
repository copy on 2026-09-21: the genuine run reports `rows 11 records 11 mismatches 0`, the tampered run
(`runs/risk-tampered/audit.yaml`) reports `mismatches 9`, and both show the node order
`acmeQuoteFeed → acmeVarCalculator → acmeRiskEngine → riskLimitGuard`. To reproduce the tamper yourself, change
`Z_99 = 2.326` to `0.0` in `VarCalculator.java`, rebuild with `build.sh` under the same version, regenerate
the host, and run `check_var.py` against the new audit log.
