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
    eventToString: PriceEvent{symbol=NVDA, price=905.6}
    nodeLogs:
        - rootNode: { method: onPriceEvent, receivedEvent: … }
        - priceThresholdAlert: { threshold: 500.0, price: 905.6, priceAboveThreshold: true }
        - riskCheck: { method: onRiskCheck }
```

- **One record is one event cycle.** `eventToString` is what entered the graph.
- **Order in `nodeLogs` is DISPATCH ORDER.** `priceThresholdAlert` ran after `rootNode`, in the same cycle,
  because of it. This is the property that lets the log answer *why* a value is what it is — read it as
  causal, not as arrival.
- **A node absent from a record did not log in that cycle.** That is not the same as "did not run": a node
  that does not extend `EventLogNode` never appears at all.
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
