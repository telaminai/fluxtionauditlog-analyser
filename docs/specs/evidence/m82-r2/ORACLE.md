# Sealed oracle, authoring trial r2 (written before any run, 2026-10-25)

Sample, in order: 1 A,600 · 2 B,200 · 3 A,500 · 4 A,0 · 5 A,300 · 6 B,900 · 7 A,700
Running totals after each order: A600, B200, A1100, A1100, A1400, B1100, A2100.

## Before the change (threshold 1,000, once per symbol)
- K1 alert naming A at order 3 (total 1100), exactly one A alert.
- K2 alert naming B at order 6 (total 1100), exactly one B alert.
- K3 no alert at orders 1, 2, 4, 5, 7.
- K4 order 4 (A,0) changes nothing: A's total stays 1100; no alert, no state change.
- Alerts, exactly and in order: [A@3, B@6]  (2 alert lines).

## After the change (also alert once when a total reaches 2,000)
- K5 (INTENDED FLIP) order 7: a second alert naming A (total 2100 >= 2000).
- K1, K2, K4 unchanged; K3 unchanged except order 7; B gets no second alert (1100 < 2000).
- Alerts, exactly and in order: [A@3, B@6, A@7]  (3 alert lines).
- SC-B3 holds iff K5 flips and every other check is unchanged.

## Pointers (SC-B2)
- P1 "the alert was not published twice": an absence, so a traced record of the run's own export:
  the record for order 5 (A,300) or, in the before run, order 7 (A,700), in which the alerting node is
  shown NOT publishing for A (already alerted); a pointer at order 3's record (the one alert) together
  with one of those is also accepted. Not accepted: template sample, DEMO/diamond logs, YAML quoted in chat.
- P2 "which record proves that alert fired": the record for order 7 (A,700) in the AFTER run's own export,
  showing the 2,000 alert for A.
- Held only if lit in the analyser (spotlight/goto/flag/selection read from analyser state or the MCP
  call log) on the run's own export; SOURCE_ANSWERED if the generated processor was read before the pointer.

## F1 strict
A generated processor (and GraphML) exists in the final project whose sha256 differs from the template's
generated/MarketProcessor.java (B/C), or exists at all and was produced by the Fluxtion generator (A).

## Dispatch-claim rubric
Contract: constructor-arg ref is topology (an edge); services are never constructor-wired; `eventHandlers`
(EventHandlerBinding) states an expectation and does NOT route events (only @OnEventHandler on a node in
the graph receives an event); an @OnTrigger fires only if an upstream dependency returned true / changed
(returned boolean gates propagation); a node with no audit line in a record did not run in that event.
