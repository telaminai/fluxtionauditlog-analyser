# Graphs

Plot any node value over time. Open the **Graph** tab; each graph is its own sub-tab (add several).

## Adding series

- Click **Edit series** on the plot to open the series panel.
- **Add key** — pick any `instanceId.key` numeric or boolean node value (booleans plot as ±1).
- Or **right-click an attribute** in the record detail view to add it straight to the current, a
  named, or a new graph.

The series key sits as an overlay on the top-right of the plot; right-click a label to remove it.

![A node value plotted over time — priceListener.mid across 400 market-data cycles](../assets/graph-series-dark.png)

That is one node's `mid` across a few hundred cycles. Nothing was extracted or transformed to get it:
the value was in the audit log because the node logged it, and every point on the line is a record you
can click back to.

## Formula series — f(x)

Beyond raw keys, add a **derived series** from a formula over other keys, e.g.:

```
askMakerOrder.price − bidMakerOrder.price
```

- The **f(x)** field shows a **dropdown of matching keys and formula labels** as you type — ↓/↑ to
  move, Enter or Tab to accept, Esc to dismiss.
- Formulas can **reference other formulas** by their label.
- **Resolve policy**: *locf* carries each ref's last value (for cross-node formulas); *strict* only
  evaluates within a single record.

## Two scales

A revenue line reaching 2,000 and a stock level oscillating around 20 share a chart where the stock line
is a flat smear along the axis. Both facts are on screen and neither is readable — which is worse than
plotting them apart, because it looks like an answer.

Put the smaller series on the **right-hand scale** and both become legible against a shared grid:

```json
{"series": ["revenueLedger.gross", "stockLedger.onHand"],
 "rightAxis": ["stockLedger.onHand"]}
```

Two scales, not three: past two, a reader has to consult a legend to know what a height means, and the
chart has stopped being a picture.

## Explaining a chart

A plot says *what happened*. It never says *why that matters*, and that second half is usually lost with
the screenshot. Both are held with the graph and drawn **on** it, so they survive an exported PNG:

- **`explanation`** — a multi-line write-up in a box on the plot.
- **`notes`** — pinned to moments, numbered on the chart and listed beneath it. Anchor one with `at`
  (epoch millis) or `recordIndex`, whichever you have to hand.

```json
{"explanation": "Revenue is priced from the request, not from what the shelf could supply.",
 "notes": [{"recordIndex": 99, "text": "first oversell — shelf at zero, till still ringing",
            "series": "stockLedger.onHand"}]}
```

Notes landing on the same pixel column stack rather than overprinting, and `clearNotes` drops the pins
while keeping the write-up.

## Styling, zoom and pins

![The same log plotted as stairs — an order book filling and draining](../assets/graph-step-dark.png)

**Stairs** is the honest style for a value that holds between updates — an order count, a state, a
threshold. A line between two samples implies the value passed through everything in between, which for
`orderTracker.live` it never did.

- **Style** — stairs (step), line or points.
- **Zoom / pan** — `+` / `−` / **Fit**, or drag to pan.
- **Pin** — 📌 fixes a graph to a time window so it stops following the shared filter.

## Export

- **Export CSV** writes the plotted series; **Export PNG** saves the chart image.

Saved graphs (names, series, formulas and pins) persist in your profile and reopen with the next log —
and can be shared, see [Sharing setups](sharing-setups.md).

A series can also be started from the graph of the processor itself — right-click a node in
[Topology & step-through](topology.md) and pick one of the values it logged.
