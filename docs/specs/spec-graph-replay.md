# Topology view + event step-through (Design Spec)

Status: DRAFT v0.1 · Owner: greg.higgins · 2026-08-15 · Milestone **M21**

Bring the web-admin's two best ideas into the analyser: **render the processor graph** (GraphML) and
**step through events on it**, lighting up the nodes that fired. Prompted by the
[M18.0 spike](spike-m18.0-admin-surface.md), which found `svc-admin-web` shipping exactly this.

## 1. Positioning — why replicate rather than defer (resolves O5)

The two tools serve different moments, and the overlap is deliberate:

| | **svc-admin-web** | **the analyser** |
|---|---|---|
| Scope | **one live server**, its current log | **many logs**, any age, any origin |
| Log lifetime | ephemeral — may be rolled or deleted | archived in a shared store, kept |
| Needs | a running server with admin-web enabled | **nothing but the file** |
| Fits | dev servers, live poking, MCP-driven inspection | **offline forensics; production support** |
| Prod reality | may not be deployed at all; **no MCP in prod** | the log is transported out; the analyser is where it lands |

So the analyser is the **dev tool and the production-support tool**. In a low-latency production system
there may be no admin-web and no MCP — logs are shipped to a shared store, and offline analysis across
many files is exactly where the analyser wins. Replicating the topology view is not duplication; it is
making the *good* view available where the logs actually end up.

What the analyser adds that a per-server web tab structurally cannot: the graph wired into the **index,
the time filter, the record table, the value graphs, click-to-source and the assistant socket**. Selecting
a record moves the topology; clicking a node filters the table; the LLM can drive both.

## 2. GraphML — where it comes from

The topology is Fluxtion's build-time GraphML for the processor. Two sources, in priority order:

1. **A file (primary — the offline path).** Fluxtion emits GraphML beside the generated processor. The
   analyser already resolves that processor's *source* through configured source roots and Maven repos;
   GraphML resolution follows the same path and the same UI. **This path needs no server**, which is the
   whole point of §1.
2. **The server (secondary, when present).** `GET /api/processors/{group}/{name}/graphml` — the M18.0
   free win. Useful on a dev box; never required.
3. **Explicit override.** Point at a `.graphml` by hand, as with source roots.

A `.graphml` may also travel **with the log** in a shared store, or in an M19 bundle / M20 project
profile — the profile is the natural place to record which topology pairs with which logs.

> **Pairing is the risk, not parsing.** A topology from a different build than the log will mislead
> quietly. Match on the processor FQN plus the `instanceId` set the log actually exhibits (the same
> scoring `EventProcessorModel` already does for processor inference), and **say so in the UI when the
> match is partial** — an unknown node is a strong signal of a version mismatch.

## 3. Rendering — Swing/Java2D, not an embedded browser

**Can Swing look as good as Cytoscape.js? Yes — the rasteriser was never the problem.** Java2D with
antialiasing, controlled strokes, rounded shapes and decent typography renders a DAG cleanly; the app's
`ChartPanel` (370 lines of Java2D) already sets the bar and is theme-aware.

Two things do the actual work of "looking good", and only one is hard:

- **Layout — the hard part.** Cytoscape.js gets layered DAG layout free from dagre/elk. We must supply
  it. A **Sugiyama layered layout** (layer assignment → crossing reduction by median heuristic →
  x-coordinate assignment → orthogonal/spline edge routing) is well-understood and entirely tractable at
  processor scale (tens to low hundreds of nodes, not thousands). Estimate ~600–800 lines, testable
  headlessly as pure geometry — which suits this repo, where logic is unit-tested and Swing is not.
- **Craft — the easy part to underestimate.** Consistent spacing, a restrained palette, edge bundling,
  label elision, hover/selection states. This is design attention, not technology.

**Escape hatch:** [ELK](https://eclipse.dev/elk/) (`elk-alg-layered`) is **pure Java** — the same
algorithm dagre ports — if the hand-rolled layout disappoints. It is a real dependency, so it is the
fallback, not the opening move.

**Rejected: embedding a webview** (JCEF / JavaFX WebView) to reuse `replay-engine.js` directly. It would
reuse proven code, but it adds a ~100 MB **native, per-platform** dependency and destroys the single
shaded fatjar that makes `jbang analyser@…` and one-file distribution work. That trade is far worse than
writing a layout algorithm. The near-zero-dep ethos (FlatLaf is the only runtime dependency) is load-
bearing for how this tool ships.

Where Swing will genuinely trail: animated transitions, and graphs of thousands of nodes where canvas or
WebGL wins. Neither is the target.

## 4. Step-through

The audit record already names the nodes that fired, in dispatch order (`nodeLogs`). So step-through is a
**join between the record and the topology**, not new data:

- **Scrub the event sequence** — next/previous record, or jump from any table selection; the topology
  shows that cycle: nodes that fired highlighted **in dispatch order**, the triggering event marked, nodes
  that did not fire dimmed.
- **Within a cycle**, step node-by-node through the dispatch order, showing each node's logged key/values
  at that point — the "which nodes lit up, what did they hold" view.
- **Bidirectional** — the log is a complete record; stepping back is just moving the cursor.
- **Bound to the shared filter**, so the time window and dimension filters scope the sequence like every
  other view.

Cross-view wiring is the differentiator, and each direction is cheap once the join exists: record
selection → topology; node click → filter/flag/graph that node's keys; node → source (existing
`SourceNavigation`); and the assistant reaching all of it over the existing action socket.

## 5. Slices

1. **M21.1 — GraphML parse + model.** `graph/topology`: parse GraphML (nodes, edges, ids, types) into a
   `ProcessorTopology`; resolve from file/source root; pair-check against the log's `instanceId` set.
   Pure logic, headless-tested. **No UI.**
2. **M21.2 — Layered layout.** Sugiyama layers → ordering → coordinates → routed edges, emitting plain
   geometry. Pure logic, headless-tested (assert layering, crossing counts, determinism). **No UI.**
3. **M21.3 — Topology panel.** Java2D render of §2's geometry: theme-aware, pan/zoom, hover, selection.
   Verified by running the jar.
4. **M21.4 — Step-through.** Record → fired-node highlighting in dispatch order; within-cycle stepping;
   wired to the shared filter and the record table.
5. **M21.5 — Cross-view wiring.** Node → source, node → graph a key, node → filter/flag. Assistant verbs
   for topology follow only if the earlier slices prove out.
6. **M21.6 — _(later)_** server-sourced GraphML via `/api/processors/.../graphml` (needs M18.1).

Slices 1–2 carry the risk and are pure logic, so they are testable before a pixel is drawn — deliberately
front-loaded.

## 6. Open questions

- **O1 — GraphML shape.** Fluxtion's emitted GraphML has not been inspected here. `fluxtion-visualiser`
  already ships a Java `GraphMlTopologyParser` (`com.telamin.fluxtion.visualiser.llm`) — **read it before
  writing M21.1**; it may be liftable, and it is certainly the reference for what the attributes mean.
- **O2 — logRecord output configuration.** How the audit sink is configured/transported (file vs
  Chronicle vs shipped to a store) is unresolved (M18.0 §3.2, §4). **It does not gate this milestone**:
  M21 needs a log and a GraphML, not a live server. Only server-sourced GraphML (M21.6) and M18.2 depend
  on it.
- **O3 — does the topology deserve to be a tab or a split?** Sequencing suggests a tab beside Graphs;
  the cross-view wiring may argue for a dockable split. Decide with the panel in hand (M21.3).
- **O4 — very large topologies.** Elision/clustering strategy is undecided; defer until a real graph
  hurts.
