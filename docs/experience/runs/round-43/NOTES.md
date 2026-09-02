# Round 43 — node state survives a graph rebuild; the dispatcher changes, the nodes do not

The owner's claim: *"we also support keep the same instance nodes, but they put them into a new
compiled graph so the dispatcher changes, but the nodes do not."* This is the verification. It matters
because it is the only claim in this series that **dissolves** the compile-time-binding objection
rather than working around it.

## The API, read before use

`docs/claude.txt` gives three build entry points and one relevant sentence — with `compileDispatcher`,
*"the user reference IS the dispatch target."* It also says plainly that **rebuilding a processor with
the same instance is not covered**, so this was untested territory rather than a documented promise.

```java
DataFlow f = (DataFlow) Fluxtion.compileDispatcher(c -> { c.addNode(n); c.addEventAudit(INFO); });
```

## The test

Node instances are constructed **once** by the caller and never rebuilt. `TickCount` is deliberately
stateful — it accumulates, so reconstructing it would reset the count to zero and the test would fail.

1. **Graph A** — `mid`, `depth`, `config`, `counter`. No pricing. Three `Tick` events.
2. **Graph B** — the **same instances**, plus newly created `adjusted`, `rate`, `spread`. One `Tick`.

## Result: PASS

| | |
|---|---|
| graph A dispatch | `tickIn, depth, mid, tickCount` — ×3 |
| count after 3 events | **3** |
| **count after the rebuild** | **3** — state survived |
| count after B's first event | **4** — continued, not reset |
| `mid.value` across the rebuild | `103.0` carried through, then `205.0` |
| graph B dispatch | `tickIn, depth, mid, pricing.adjusted, pricing.spread, tickCount` |
| dispatch changed | **yes** — pricing now dispatches |

**The topology changed and no existing node was reconstructed.** `pricing.adjusted` and
`pricing.spread` entered the graph, ordered correctly against nodes that were already running and
already holding state.

## Why a hand-rolled integrator cannot reach this

Established earlier in the series and worth restating, because it is structural rather than a matter
of effort:

> The node-to-node references live in the **supplier's** classes. `Charge` holds
> `private final Exposure exposure`. The integrator does not own that field, cannot rebind it, and
> cannot subclass around `final`. So an integrator holding live nodes has no legal move to rewire
> them — they must reconstruct, and reconstruction loses state.

The plain-Java arm demonstrates the consequence without being asked to: 367 lines, every node
`private final`, constructed once, **no setters, no rebind path, no mutable edge structure**. Not a
failure of that arm — it copied the suppliers' idiom, which is good Java. It simply has no route to a
topology change with state preservation, and neither would any consumer of these jars.

Fluxtion escapes it only because **it owns construction**.

## Incidental confirmation

The runtime compile logged `Using compiler strategy: javac` and needed **no API key**, because the
compiler was on the classpath. That matches the conditional rule recorded in `CLAUDE.md` — *a key is
needed if the compiler is not on the classpath* — and is the tested version of a statement that three
agents in round 07 asserted wrongly **without testing it**.

## Scope: what is verified and what is not

**Verified:** same instances into a newly compiled graph; state intact; new nodes added; dispatch
re-derived and changed; runtime compile without a key when the compiler is present.

**Not verified, and I am not claiming it:**

- **Re-parenting an existing node** — pointing `Charge` at a different `Exposure`. That reference is
  `final` inside `Charge`, so I would expect it to require reconstruction. Untested.
- **Interpreted mode as a distinct thing.** `docs/claude.txt` names no `Fluxtion.interpret()` and
  draws no interpreted-vs-compiled distinction; the owner does. Unresolved, and the reference may
  simply be behind.
- **Removing** nodes across a rebuild, and what happens to their state.
