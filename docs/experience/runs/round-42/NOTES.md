# Round 42 — the composite node, instance sharing, and what a consumer actually ships

Not an arm-vs-arm round. Three properties of the composition model, **measured** after two of my own
claims turned out to be wrong when checked.

## 1. A supplier CAN publish a subtree as one declarable unit

The owner supplied the pattern; I had filed `UP-FLX-28` claiming it was impossible. A root class
carries **two constructors** — a consumer-facing one taking only the subsystem's *external*
dependencies and building the subtree, and one accepting **every node-typed field**, which is what the
generator matches against.

| | 20-bean (round 39) | **5-bean composite** |
|---|---|---|
| consumer declares | 20 beans | **5** — `marketdata`, `pricing`, `liquidity`, `risk`, `capital` |
| generated | 920 lines | **975 lines** |
| nodes in the graph | 20 | **25** (20 + the 5 roots) |
| trace over 9 events | reference | **identical** |

The internal nodes are given synthesised names the consumer never writes — `mid_7`, `depth_8`,
`exposure_19` — while the five roots keep the bean-file names. The component boundary is visible in
the generated source: **the consumer's vocabulary is five names; the graph's is twenty-five.**

## 2. Instance sharing across suppliers is by identity, and the alternative is publishing internals

Three suppliers reference `marketdata`. The generated graph contains **exactly one** `Mid`, one
`Depth`, one `Vol` — every node constructed exactly once, verified across the whole file.

The plain-Java arm also achieved one shared `Mid`. It is *how* that matters:

```java
mid      = new Mid(mdTick);
vol      = new Vol(mdConfig, mid);      // marketdata
adjusted = new Adjusted(mid, depth);    // pricing
notional = new Notional(rkTrade, mid);  // risk
```

**23 hand-wired construction lines**, possible only because the suppliers ship flat, individually
constructible nodes whose constructors name other vendors' classes —
`Adjusted(com.vendor.marketdata.Mid, com.vendor.marketdata.Depth)`. That is not a component boundary;
it is the internals published as the integration surface.

> **For plain Java, instance sharing and encapsulation are in direct conflict.** Move to composites
> and the consumer can no longer share `Mid`, because it never sees one — each composite builds its
> own, giving two `Mid` nodes with divergent state from the same tick. Re-expose the internals to fix
> it and the boundary is gone. Fluxtion does not have to choose: `ref="marketdata"` three times binds
> one instance.

Suppliers still integrate against each other's *published* nodes (`Pricing(MarketData md)` reaches
`md.mid`), which is normal. What changed is whose surface it is: **the consumer went from 23 lines
naming five vendors' internals to five names.**

## 3. What the consumer ships is the runtime alone

| | jars | framework content |
|---|---|---|
| build | **47** | builder, builder-api, runtime, plus a shaded `javac`, jgrapht, guava, classgraph, reflections… |
| **deploy** | **7** | **`fluxtion-runtime` only** — the rest are the five vendor jars and the generated classes |

Tested with the builder removed **and** `~/.fluxtion/fluxtion.apiKeyFile` moved aside: exit 0, 13
records, **trace identical** to the built-with-builder run. So a pregenerated processor needs neither
the builder nor a key.

Recorded carefully because `CLAUDE.md` notes that three agents in round 07 asserted the opposite about
the key **without testing it**. This is the tested version. Runtime compilation is a different mode
and does need the builder API on the classpath; that half is **not** verified here.

## Two corrections I had to make against myself

1. **"A composite node is impossible."** Filed as `UP-FLX-28`, wrong, corrected at `18d56f5`. The ask
   shrank from a feature request to *document this idiom, and add one diagnostic*.
2. **I read a stale trace.** Generation had failed while `target/classes` still held the previous
   processor, so a passing trace meant nothing. Third occurrence of this trap in the project; the fix
   is always the same — clean before trusting output.

Both were found by checking rather than by reasoning, which is the whole content of rule 6.

## Still open

- **`UP-FLX-27`** — simple-name collisions across vendors. Unfixed, and `Tick`/`Config`/`Event` are
  exactly what independent suppliers name things.
- **The shared event schema.** All five suppliers here depend on one `com.vendor.Events` class
  shipping in marketdata's jar. Real markets have no such luck. Instance sharing solves *node*
  identity; it does not answer who owns the *event types*.
- **Runtime modes** — interpreted, compile-at-runtime, and same-instances-into-a-new-compiled-graph.
  The last would dissolve the compile-time-binding objection entirely. All three are the owner's
  claims and **none is verified**; the decisive test is accumulating state in a node, rebuilding into
  a new graph with that same instance, and showing from the audit log that the state survived *and*
  the dispatch changed.
