# Topology & step-through

The **Topology** tab draws the processor's node graph and lights up what happened in a cycle. The log
tells you *what* fired and in what order; the graph tells you *why* — which node feeds which.

![A cycle on the topology: fired nodes ringed and numbered in dispatch order, everything else faded back](../assets/topology-step-through.png)

Eight nodes fired in this event, numbered **1–8** in the order the processor dispatched them. The
nineteen-node graph stays on screen but recedes, because "this node was not involved" is usually part of
the answer.

## Open a topology

Fluxtion emits a `.graphml` for the processor when it builds. In the Topology tab, **Open .graphml…** and
pick it — typically beside the generated processor source in your build output.

You don't need a server for any of this. The graph is a file, the log is a file, and the analyser works
on both offline — which is the point when you're supporting a system whose logs were shipped somewhere
else days ago.

!!! warning "Check it's the same build"

    A topology from a *different* build of the processor renders perfectly and misleads silently. The
    status line tells you what it found — `topology matches the log (8 nodes)`, or a warning naming the
    instance ids that appear in the log but not in the graph. **Treat that warning as a version
    mismatch**, not a curiosity: the picture is wrong in ways you can't see.

## Read the graph

Layers run in dispatch order — **lower is later**. Every edge points from a node to something it feeds,
so nothing sits above the thing that triggers it.

Colour distinguishes what a node *is*:

| | |
|---|---|
| **Events** | classes entering the graph |
| **Event handlers** | the nodes that take them |
| **Nodes** | ordinary compute nodes |
| **Exported services** | what the processor publishes outward |

Drag to pan, scroll to zoom (the point under the cursor stays put), **Fit** to frame the whole graph.
Labels fade out when boxes get too small to read them, so a big graph zoomed out stays legible as shape
rather than noise. **Left→right** flips the orientation if a wide graph suits your screen better.

## Step through a cycle

Select any record — in the table, or by jumping to one from a graph — and the topology shows that cycle.
It follows the **table's selection**, so the record you're reading in the detail pane is the cycle you're
looking at here; there's no separate cursor to keep in sync.

- **◀ ▶** walk the dispatch order one node at a time. The current node takes the accent ring and the
  status line shows **what it logged at that point** — the values it held as the event passed through.
- **Whole cycle** goes back to showing them all at once.
- A node that fired **twice** in one cycle gets two steps, because that's information.

This is the "which nodes lit up, and what did they hold" view. Pair it with
[Graphs](graphs.md) when you want the same value across many cycles instead of one.

## Act on a node

Right-click any node:

- **Open source** — jumps to that node's class in [Source navigation](source-navigation.md).
  Double-clicking does the same.
- **Graph ▸** — plot one of the values this node logged. Only values that *can* be plotted are offered,
  which follows the same rule as everywhere else: a number inside a `toString()` is text, not a series
  (see [Graphs ▸ Adding series](graphs.md#adding-series)).
- **Filter records to this node** — narrows every view to records mentioning it, via the ordinary search
  box, so you can edit or clear it as usual. It's a free-text scan, so it's slow on a very large log.
- **Copy instance id** — for pasting into a prompt or a search.

## Where it fits

A Mongoose server with the admin web console can show you a live processor's graph. This tab is for the
other situation, which is most of production support: **many logs, archived, no server to ask** — and
here the graph is wired into the rest of the analyser, so a node reaches its source, its values and the
records it appears in.
