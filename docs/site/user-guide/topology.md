# Topology & step-through

The **Topology** tab draws the processor's node graph and shows what a cycle did to it. The log tells
you which nodes *logged*, and in what order; the graph tells you how they are wired — and therefore what
the log implies about everything that stayed quiet.

![A cycle on the topology: nodes that logged are ringed and numbered in dispatch order; the rest are shaded by what the log supports](../assets/topology-step-through.png)


A `MarketDataEvent` arrived. Two nodes **logged** — `priceListener` ① and `quotePublisher` ② — and the
rest of the graph is shaded by what the log actually supports, which is not the same as what ran.

Look at `spreadCalculator`, sitting between them with a **dashed** outline. It certainly executed: the
spread it computes is in `quotePublisher`'s log line. It simply writes no audit output of its own, so the
log never mentions it. The order-handling branch is faded because a market-data event cannot reach it.

!!! danger "No audit entry does not mean the node didn't run"

    A node appears in `nodeLogs` only if it **writes** audit output, and only at the audit level in
    force. Plenty of nodes execute silently. So the tab never colours a node "didn't run" — it shows
    four different claims, and says which is which:

    | On screen | What it means |
    |---|---|
    | **green ring + number** | **logged** — it wrote audit output, and the number is its dispatch position. The only thing directly observed. |
    | **solid outline** | **ran, logged nothing** — it is the *only* way into something that ran, so dispatch had no other route. Certain. |
    | **dashed outline** | **may have run** — connected to something that logged, but the log cannot say whether dispatch reached it. A genuine unknown. |
    | **faded** | **not on this path** — nothing that logged is connected to it. |

    The distinction between the last two matters: a node with several parents only needs *one* of them
    to have fired, so its other ancestors are unknowns, not certainties. Hover any node and it tells you
    in words.

    "Ran, logged nothing" is only claimed when the log says how the cycle started. A record that isn't
    event dispatch at all — a startup callback, say — makes no such claim, because nothing upstream ran
    to cause it.

!!! tip "Turn the guesswork off: build with node-invocation tracing"

    If the processor is built with an audit **level** — `cfg.addEventAudit(LogLevel.TRACE)` — Fluxtion
    emits an `auditInvocation(...)` call before every node it invokes, so **every node that runs logs a
    `method` entry** whether or not it makes `auditLog` calls of its own. The runtime level then gates
    whether those fire, so you can raise it on a live processor to get the detail.

    The analyser detects such a record and stops hedging: the log is now a complete list of what ran, so
    a node's absence is **proof it did not run**, and the legend changes to say exactly that. All the
    "may have run" shading above exists because most production logs aren't traced — not because the
    distinction is unknowable.

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

Every edge points from a node to something it feeds, and layers respect that: **a node always sits below
everything that feeds it**. So for any two connected nodes, lower means later.

Two nodes on the same layer are simply unrelated — the layout says nothing about which of them the
processor dispatched first. When you need the true order, that's what the **numbers** give you: they come
from the log, not the layout.

Fill colour distinguishes what a node *is*. Note that **execution enters the graph two ways**, and both
appear at the top with nothing above them:

| | |
|---|---|
| **Events** | event classes arriving at the processor |
| **Event handlers** | the nodes that take them |
| **Exported services** | a service interface the processor exports. An **entry point, not an output**: an external caller invokes the interface and dispatch flows from there, exactly like an event |
| **Nodes** | ordinary compute nodes |

Output goes the other way and isn't a node kind: a node publishes to a **sink**, a Mongoose-supplied
service the graph registers with. You'll see that registration in the graph as a `SinkRegistration` event
feeding the publishing node.

Drag to pan, scroll to zoom (the point under the cursor stays put), **Fit** to frame the whole graph.
Labels fade out when boxes get too small to read them, so a big graph zoomed out stays legible as shape
rather than noise. **Left→right** flips the orientation if a wide graph suits your screen better.

## Step through a cycle

Select any record — in the table, or by jumping to one from a graph — and the topology shows that cycle.
It follows the **table's selection**, so the record you're reading in the detail pane is the cycle you're
looking at here; there's no separate cursor to keep in sync.

- **◀ ▶** walk the dispatch order one node at a time — that is, the nodes that logged. The current
  node takes the accent ring and the status line shows **what it logged at that point**, plus what the
  log does or doesn't establish about it.
- **Whole cycle** goes back to showing them all at once.
- A node that logged **twice** in one cycle gets two steps, because that's information.

This is the "what did the log actually witness, and what does that imply" view. Pair it with
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
