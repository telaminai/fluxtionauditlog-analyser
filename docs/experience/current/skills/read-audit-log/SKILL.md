---
name: read-audit-log
description: Read the audit log and get evidence a node fired — with the analyser if you have it, and from the file directly if you do not.
x-analyser-min-version: 1.12.0
---

# Get evidence from the audit log

The export is **plain YAML**. You do not need any tool to read it, and this skill covers both routes —
earlier versions assumed the analyser was connected and left you stuck if it was not.

## Without the analyser — always available

```bash
grep -n "yourNodeName" logs/audit-*.yaml | head
```

To see one whole cycle, print the record around a hit. Each record starts at a `---` line.

### How to read a record

```yaml
eventLogRecord:
    eventToString: PriceEvent{symbol=AAPL, price=195.3, volume=1200}
    nodeLogs:
        - rootNode:  { thread: processor-agent, method: onPriceEvent, receivedEvent: PriceEvent{…} }
        - riskCheck: { thread: processor-agent, method: onRiskCheck }
```

That is a real record from **this** project as it ships. Note what it does *not* contain: no computed
value, no decision, no state. Both shipped nodes echo their input and nothing else, so **this log can
show you what ran and in what order, and cannot tell you whether the result was right.**

- **One record is one event cycle.** `eventToString` is what entered the graph.
- **Order in `nodeLogs` is DISPATCH ORDER.** `priceThresholdAlert` ran after `rootNode`, in the same cycle,
  because of it. This is the property that lets the log answer *why* a value is what it is — read it as
  causal, not as arrival.
- **A node absent from a record did not log in that cycle. That is NOT the same as "did not run",** and
  the rule for which nodes can appear is subtler than it looks — an earlier version of this skill stated it
  wrongly, so do not shortcut it:
    - Appearing is driven by the **generator**, which emits `auditInvocation(node, "name", "method", …)` at
      dispatch sites. `RiskCheck` in this project does **not** extend `EventLogNode` and appears in every
      record.
    - Having an `EventLogger` — by extending `EventLogNode` **or** implementing `EventLogSource`
      (`setLogger(EventLogger)`) — is what lets a node add **its own key/values**. That is a different
      thing from appearing at all, and it is an interface, so a node with a superclass is not excluded.
    - Infrastructure nodes can run and never appear: `perfMon` runs on every event and is in **zero**
      records.
  So absence is evidence only for application nodes on a dispatch path. For anything else, absence means
  *not instrumented*, not *did not run*.
- **Severity is not in the export.** `auditLog.info` and `auditLog.warn` land in the same flat map with no
  level marker. If you need severity to be visible, put it in the key or the value yourself.

## Before you conclude anything: can the data show what you are testing?

`data/input.txt` ships with **one row per symbol**. Any per-symbol accumulation — a count, a running
maximum, a moving average — is therefore always 1, or always the only value, and the log looks **identical
to a broken implementation**. If you are testing behaviour that builds across events, add rows first so
the value changes, and put the interesting one in the *middle* so a running maximum can be told apart from
"the latest value".

## With the analyser — when you want to ask questions instead of grep

If the analyser's MCP tools are available:

```
analyser_open    {"log": "<the path from export-audit.sh>", "graphml": "<the .graphml under src/main/resources>"}
analyser_context {}
```

Then check `graphPairing` **before concluding anything**: if the graph does not apply to this log, a
node's absence is not evidence of anything.

**The log path is not discoverable from the analyser.** `context.log` describes a log already open, and no
project setting records one. Use the path `export-audit.sh` printed.
