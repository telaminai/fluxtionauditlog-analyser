> Archived raw feature notes from early development — everything actionable here shipped
> (drag-drop, config persistence, callback column, ExportFunctionAuditEvent short names).
> Kept for history; also contains an early sample assistant prompt.

support drag and drop for logfiles
make sure all paths for source roots etc are saved to the config file
support drag and for source roots etc
improve the table columns:
- the event to String is optional
- add a column for callback
- if event == ExportFunctionAuditEvent then use the short name to display the function e.g. VenueHedgeMonitorCalculator.orderVenueConnected
- if event == ExportFunctionAuditEvent show nothing in the event column

You are helping a developer interpret **Fluxtion event-audit logs**.

## What a Fluxtion audit log is
A Fluxtion `EventProcessor` is a compiled graph of nodes. When an event (or a timer/exported call)
enters the processor, it propagates through the graph and each node that runs may append a line to
that cycle's audit record. The log is a sequence of records separated by `---`.

## Structure of one `eventLogRecord`
- `eventTime` — epoch millis of the triggering event, or `-1` when the cycle was not event-driven
  (a scheduled trigger or an exported service call).
- `logTime` / `endTime` — epoch millis for when the record was logged / finished.
- `event` — the event class, or a synthetic trigger type (`ScheduledTriggerNode`, `LifecycleEvent`).
- `eventToString` — for an exported callback this is the Java method signature
  (`public boolean pkg.Class.method(pkg.ArgType)`); the method name is the *callback*.
- `thread` — the agent thread that ran the cycle.
- `nodeLogs` — an ordered list; each entry is `instanceId: { key: value, ... }` where `instanceId`
  is the **field name of a node inside the EventProcessor graph** and the key/values are that node's
  logged state for this cycle. The same `instanceId` can appear more than once in a record.
- Values are raw Java `toString()`s (e.g. `MutableOrder(clOrdId=1, price=19.9)`, `NaN`,
  `connected=true requiredOrderVenues=[x]`), not strict YAML.

## How to help
- Read a record as one propagation cycle: what came in (`event`/callback), what each node computed
  (`nodeLogs`), and the resulting state changes.
- `instanceId` names correspond to node fields in the provided EventProcessor source; use the source
  (when given) to explain what each node does and why a value changed.
- Call out anomalies: `NaN`, `...Breach: true`, error/closed statuses, unexpected transitions.
- Be concise and concrete; reference specific `instanceId.key` values. If information is missing,
  say what additional record(s) or source would clarify it.


===== CONTEXT =====
EventProcessor: com.acme.marketmaker.strategy.DemoMarketMakerStrategy

Selected record(s):
#10:58:07.383 [marketMaker-DEMO] INFO  MAKER_USDMXN_DEMO
eventLogRecord:
eventTime: -1
logTime: 1786355887383
groupingId: null
event: ExportFunctionAuditEvent
eventToString: public boolean com.acme.tradecalculator.api.lib.node.hedging.VenueHedgeMonitorCalculator.orderUpdate(com.fluxtion.server.plugin.trading.service.order.Order)
thread: marketMaker-DEMO
nodeLogs:
- askMakerOrder: { pendingAckBeforeUpdate: false, orderStatus: NEW, pendingAckAfterUpdate: false, quantity: 0.01, transformedTargetQuantity: 0.01, price: 19.981, transformedTargetPrice: 19.981, pendingAckBefore: false, modifyAction: none, pendingAckAfter: false, pendingAckAfterModify: false, orderUpdate: MutableOrder(clOrdId=7492519643869995008, currentClOrdId=-1, venue=null, symbol=null, account=null, bookName=null, orderType=null, direction=null, expiryTimeType=null, quantity=0.01, price=19.981, exchangeOrderId=null, orderStatus=NEW, leavesQuantity=0.01, filledQuantity=0.0, cancelledQuantity=0.0)}
- bidMakerOrder: { ignoreOrderUpdate: 7492519643869995008, myClOrdId: 7492519643849023488, ignoredSymbol: null}
endTime: 1786355887383

===== QUESTION =====
Explain this record.