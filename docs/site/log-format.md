# Log format

## What a Fluxtion audit log is

A Fluxtion `EventProcessor` can emit a structured **event-audit log**: one record per **event cycle**,
recording the triggering event, the timing, and the `nodeLogs` — the values each node reported as it ran
during that cycle. It's the processor's own account of what it did, in order — deterministic and
replayable. See [Producing an audit log](producing-a-log.md) for how a processor emits one.

!!! tip "Try it now"
    Download a real sample — [**sample-audit-log.yaml**](assets/sample-audit-log.yaml) (22 records) —
    and open it with **File ▸ Open log…**. Everything in the [user guide](user-guide/index.md) works
    against it.

## An example record

Records are separated by a `---` line. Here is one real record (an exported service call):

```yaml
---
#10:57:37.431 [marketMaker-DEMO] INFO  MAKER_USDMXN_DEMO   # wall-clock · thread · level · logger
eventLogRecord:
  eventTime: -1                       # epoch millis; -1 = not event-driven (a timer or exported call)
  logTime: 1786355857430              # epoch millis — the primary timeline
  groupingId: null
  event: ExportFunctionAuditEvent     # the event class, or the trigger type
  eventToString: public boolean com.acme...VenueHedgeMonitorCalculator.orderVenueConnected(...OrderVenueConnectedEvent)
  thread: marketMaker-DEMO
  nodeLogs:                           # ordered — one entry per node that logged this cycle
    - hedgeConnectionMonitor: { orderVenueConnected: OrderVenueConnectedEvent[name=demoRfqOrders], status: CLOSED, hedgeQuantity: NaN}
    - hedgePositionMonitor:   { hedgePositionBreach: false, hedgeStatus: CLOSED}
    - venueMonitorQuoteCalculator_2: { connected: true}
    - bidMakerOrder: { ignoreCancelOrder: noLiveOrder, orderStatus: noLiveOrder, isOrderClosed: true}
    - bidMakerOrder: { connected: true}   # the same instanceId can appear more than once in a cycle
  endTime: 1786355857431
```

## Field reference

| Field | Meaning |
|---|---|
| `#…` header | Wall-clock time · thread · log level · **logger** (the processor's audit name). |
| `eventTime` | Epoch millis of the source event; **`-1`** means non-event-driven (a scheduled trigger or an exported service call). |
| `logTime` | Epoch millis — the reliable timeline the analyser sorts and windows on. |
| `groupingId` | Optional correlation id, or `null`. |
| `event` | The event class name, or the trigger type. |
| `eventToString` | The event's `toString()` — often a method signature for exported calls. Kept whole (never re-split on inner `:`). |
| `thread` | The thread that ran the cycle. |
| `nodeLogs` | Ordered list; each entry is `instanceId: { key: value, … }` for one node that logged. |
| `endTime` | Epoch millis when the cycle finished. |

**`instanceId`** is the node's field name inside the generated `EventProcessor` (e.g. `bidMakerOrder`,
`hedgeConnectionMonitor`). The **same `instanceId` can appear more than once** in a record — a node can
log at several points in the cycle. When the analyser needs a single value (graphing, diff) it uses the
**last occurrence** within the record.

## Why it isn't strict YAML

The log *aims* at YAML, but node values are raw Java `toString()` output, which regularly isn't valid
YAML:

- `hedgeQuantity: NaN` — `NaN` isn't a YAML float.
- `orderUpdate: MutableOrder(clOrdId=1, venue=null)` — top-level commas would split a YAML flow-map.
- `venueStatus: connected=true requiredOrderVenues=[demoRfqOrders]` — a space-separated `toString` with
  `=` and `[]`.

So the analyser **never hands `nodeLogs` to a YAML parser**. A bespoke, depth-aware tokenizer parses them
leniently and **never fails a whole file** on one odd value. A number nested *inside* a `toString()`
(e.g. `bidPrice=` in `QuoteLadder(bidPrice=…)`) is text within one value, not a top-level key — so it
isn't itself graphable; only top-level numeric/boolean nodeLog keys are.

## Versions

The analyser reads the audit-log format emitted by current Fluxtion / Mongoose server builds. The format
is intentionally lenient and forward-tolerant — unknown fields are ignored rather than rejected — so a
newer producer won't break an older analyser.

## Performance model

Browsing, filtering and summarising run off a compact in-memory **index**; node-logs are parsed only for
the detail view and graphing, so the tool stays fast. Files above the **memory threshold** (Settings)
are memory-mapped instead of loaded into heap, which scales past multi-GB logs.
