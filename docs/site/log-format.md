# Log format

## What a Fluxtion audit log is

A Fluxtion `EventProcessor` can emit a structured **event-audit log**: one record per **event cycle**,
recording the triggering event, the timing, and the `nodeLogs` — the values each node reported as it ran
during that cycle. It's the processor's own account of what it did, in order — deterministic and
replayable. See [Producing an audit log](producing-a-log.md) for how a processor emits one.

!!! tip "Try it now"
    Download a real sample — [**sample-audit-log.yaml**](assets/sample-audit-log.yaml) (22 records) —
    and open it with **Audit log ▸ Open log…**. Everything in [Diagnose in the analyser](inapp/index.md) works
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

A **reader** that constructs text from typed values (the binary reader) declares a second grammar
through the plugin SPI: then a value, key or instance id that is entirely double-quoted (`"…"`, with
`\"`, `\\`, `\n`, `\r`, `\t` escapes) is a *string* whatever it says — `"42.0"` is not a number and
`"null"` is not null — and the binary reader writes a logged String or char that way whenever the
bare text would split, end the line or read as a number, so a string can never pose as a figure.
Nothing in the text itself selects a grammar. A text file — every log the text runtime writes — is
read exactly as it always was: quote marks are the producer's characters and nothing decodes.

## Versions

The analyser reads the audit-log format emitted by current Fluxtion / Mongoose server builds. The format
is intentionally lenient and forward-tolerant — unknown fields are ignored rather than rejected — so a
newer producer won't break an older analyser.

Writing an emitter or an adapter for another engine? The **[format specification](format-spec.md)** is
the normative version of this page — MUST/SHOULD rules, what absence and order mean, and the
conformance fixtures the analyser itself passes.

## Performance model

Browsing, filtering and summarising run off a compact in-memory **index**; node-logs are parsed only for
the detail view and graphing, so the tool stays fast. Files above the **memory threshold** (Settings)
are memory-mapped instead of loaded into heap, which scales past multi-GB logs.

## Is the log whole?

A log file can be cut short. A server killed mid-write, a partial copy, a transfer that stopped — the
file still opens, and the records in it are still real. What changes is whether you can treat *absence*
as evidence.

`context` reports this under `log.streamEnd`, and it has six answers:

| state | what it means |
|---|---|
| `complete` | the file says it finished, and the count it declares matches what was read |
| `missing_records` | the file claims more records than it holds: records were lost |
| `more_than_declared` | the file holds more records than its marker claims — the marker is wrong, or it is not the end |
| `unverified` | the file says it finished but carries no readable count, so nothing backs the claim |
| `unknown` | the file makes no claim either way |
| `unterminated_marker` | the file ends with a completeness claim its writer never finished: the marker has no closing `---` |

**`unknown` is the ordinary answer, and it is not a fault.** Nothing that writes audit logs today emits a
completeness claim, so almost every file you open will say `unknown`. It is reported rather than hidden
because "I cannot tell" and "it is whole" are different answers, and only one of them lets you say a node
never ran.

**What `unknown` covers, and why there is no "truncated" answer.** A text log is a sequence of records
*separated* by `---`, so a whole file may simply end after its last record with nothing following it —
and the usual writers do end that way. The absence of anything after the last record is therefore not a
sign of damage, and the analyser will not call it one. A server killed mid-record wrote no marker, so its
file reads as `unknown`. That is the honest answer: the records in it are still real, and what you cannot
do is treat a missing node as proof it never ran. A count in a marker *can* catch records lost from the
middle of a run, which is the case nothing else would find.

`recordsRead` is always the number of records in the **file**. When a file holds more than one run, and
the verdict concerns one of them, that run's own numbers sit under a `run` key beside it, with the run's
position and the records it covers. Nothing about a single run is ever reported as though it were the
file, which is a mistake worth naming because an agent calculates with these numbers.

When a file does claim completeness, the status bar says **complete** beside the record count. The states
with something to report appear as a source diagnostic, the same place a cut binary tail is reported.

**A rolled set is never reported as complete**, however many of its files say they are. A marker is
written by the process that wrote that one file, and nothing records how many files a set should hold, so
a set whose middle file was never copied looks exactly like a set with nothing missing. A file that lost
records still makes the set say so, and names the file. Everything else about a set is `unknown`.

The marker a writer emits to make this claim is defined in the
[format specification §1a](format-spec.md). It is never shown as a record: it is a fact about the
file, so it is absent from the table, from counts, from series and from the time range.

