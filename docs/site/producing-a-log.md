# Producing an audit log

The analyser reads the **event-audit log** a Fluxtion `EventProcessor` emits. If you don't have one yet,
here's how a processor produces it — and you can always start with the
[sample log](assets/sample-audit-log.yaml) to explore the tool first.

## How a node appears in the log

A node that should show up in the audit log gets an **audit logger** (typically by extending
`EventLogNode`) and calls `auditLog.info(key, value)` / `auditLog.warn(key, value)` during its
callbacks. Each key/value becomes an entry under that node's `instanceId` in the cycle's `nodeLogs`:

```java
public class VenueMonitorQuoteCalculator extends EventLogNode implements QuoteCalculator {

    @Override
    public boolean calculateQuote() {
        boolean connected = venueMonitor.isConnected();
        auditLog.info("connected", connected);            // → connected: true
        if (!connected) {
            auditLog.warn("quoteCalculationSkipped", venueMonitor.getVenueStatus());
        }
        // …
        return true;
    }
}
```

The Fluxtion `EventLogManager` auditor gathers every node's entries for the cycle into one
`eventLogRecord`. So the log is a by-product of your nodes narrating what they did — no separate
instrumentation pass.

!!! tip "Name your fields well"
    An `instanceId` is the node's **field name** in the generated processor. Those names are exactly what
    you see, filter and graph in the analyser, so give nodes meaningful field names and log **stable key
    names** — the analyser plots `instanceId.key` over time.

## Turning it on

Audit logging is enabled on the processor / server and has a **log level** that controls verbosity
(higher = more `nodeLogs`). In a **Mongoose server** you configure the audit log and point it at a file
sink; that file is the one you open here.

- **Log level** — raise it (e.g. `DEBUG`) while diagnosing to capture more per-node detail; lower it
  (`INFO`) in steady state.
- **File sink** — the server writes records to a `---`-separated file. Open it locally, or copy it to S3
  and open `s3://bucket/key`.

The exact configuration lives with the producer — see the **Fluxtion / Mongoose server documentation**
for enabling the `EventLogManager` auditor, setting the level, and wiring the file sink.

## What you get

Each cycle becomes one record — the triggering event, timing, and the ordered `nodeLogs`. See
[Log format](log-format.md) for the record shape and a field reference. That deterministic, replayable
record is what the whole analyser toolchain works from.
