# Producing an audit log

The analyser reads the **event-audit log** a Fluxtion `EventProcessor` emits. This page is the whole
path from "my processor logs nothing" to a file the analyser opens. In a hurry, or just evaluating?
The analyser ships a recorded run inside the jar — see [Getting started](getting-started.md).

## The shortest complete example

Three things have to be true, and missing any one of them produces a log that is empty, or short, or
full of the wrong thing. This example is complete and runs:

```java
package com.acme.hello;

public record Temperature(String sensor, double celsius) { }
```

```java
package com.acme.hello;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

public class Thermostat extends EventLogNode {          // (1) the node can audit
    private boolean heating;

    @OnEventHandler
    public boolean onTemperature(Temperature t) {
        heating = t.celsius() < 18.0;
        auditLog.info("sensor", t.sensor())
                .info("celsius", t.celsius())
                .info("heating", heating);
        return true;
    }
}
```

```java
package com.acme.hello;

import com.telamin.fluxtion.Fluxtion;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import java.nio.file.Files;
import java.nio.file.Path;

public class AuditLogHello {
    public static void main(String[] args) throws Exception {
        StringBuilder log = new StringBuilder();

        DataFlow flow = Fluxtion.compile(cfg -> {
            cfg.addNode(new Thermostat(), "thermostat");   // the name becomes the instanceId
            cfg.addEventAudit();                           // (2) install the audit auditor
        });
        flow.init();
        flow.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        // (3) the sink — and it writes the `---` separator itself
        flow.setAuditLogProcessor(r -> log.append("---").append(System.lineSeparator())
                .append(r.toString()).append(System.lineSeparator()));

        flow.onEvent(new Temperature("hall", 21.5));
        flow.onEvent(new Temperature("hall", 16.0));

        Files.writeString(Path.of("thermostat-audit.yaml"), log.toString());
    }
}
```

Running it writes `thermostat-audit.yaml` — two events in, two records out, each carrying the three
keys the node logged under its `instanceId`:

```yaml
---
eventLogRecord: 
    eventTime: 1787696934848
    logTime: 1787696934848
    groupingId: null
    event: Temperature
    eventToString: Temperature[sensor=hall, celsius=21.5]
    thread: main
    nodeLogs: 
        - thermostat: { sensor: hall, celsius: 21.5, heating: false}
    endTime: 1787696934849
---
eventLogRecord: 
    eventTime: 1787696934849
    logTime: 1787696934849
    groupingId: null
    event: Temperature
    eventToString: Temperature[sensor=hall, celsius=16.0]
    thread: main
    nodeLogs: 
        - thermostat: { sensor: hall, celsius: 16.0, heating: true}
    endTime: 1787696934849
```

That is the whole contract: `thermostat` is the name the graph gave the node, and `sensor`, `celsius`
and `heating` are the keys it logged — so in the analyser you filter on `thermostat` and plot
`thermostat.celsius` over time. Open the file with **File ▸ Open log…**, or drag it onto the window.

!!! success "The analyser will tell you before you even run it"
    Open your processor's `.graphml` and the analyser reads whether audit logging was installed at
    build time — the compiler puts an `EventLogManager` node on the graph when `addEventAudit()` was
    called. If it is missing, the **Project panel's Graph section** says so: this processor will write **no** audit log,
    however carefully its nodes narrate themselves. That check needs no log, no run and no export,
    which matters because this is the one mistake that leaves nothing behind to diagnose.

!!! success "And if a log is wrong, it tells you that too"
    Opening a log runs three checks on the file and reports what it finds in the status bar and in
    `context`: records run together with no `---` between them, no `nodeLogs` anywhere, or a log
    containing nothing but the framework's own control event. Each names the cause and the fix. They
    are *reported*, never repaired — a mis-written log is a finding about the emitter.

!!! danger "Write the `---` separator, or the analyser silently reads fewer records"
    A text audit log is a **sequence of YAML documents separated by lines consisting of `---`**
    ([Format specification §1](format-spec.md)). `record.toString()` does **not** include the
    separator — the sink adds it. Omit it and the file still opens, still looks like a log, and is
    read as **one record** however many it contains — which is why the analyser now checks for it.
    The example above writes it in the sink for exactly this reason.

!!! warning "Set the level before you attach the sink"
    `setAuditLogLevel(...)` dispatches an `EventLogControlEvent` **through the graph**, so it produces
    a record of its own. Set the level first, as above, and the sink never sees it. If your code
    attaches the sink first, drop the control record explicitly:
    `if (record.toString().contains("event: EventLogControlEvent")) return;`

The three numbered pieces are each load-bearing: without **(1)** the node has no `auditLog`; without
**(2)** the `EventLogManager` auditor is never installed and `nodeLogs` is empty for every record;
without **(3)** the records are produced and thrown away.

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
- **File sink** — the server writes records to a `---`-separated text file. Open it locally, or copy it
  to S3 and open `s3://bucket/key`. **This is the sink the analyser reads.**

!!! note "The audit writer is pluggable — use the file sink for the analyser"
    `EventLogManager` takes a `LogRecordListener`, so a processor can write its audit stream to any
    back-end sink — a text file, or **Chronicle**, kafka, jdbc, etc. for higher throughput. The analyser
    reads the **text file sink**. If your production system logs to Chronicle (or another binary sink)
    for performance, add a text file sink alongside it to feed the analyser. (A file sink and a
    high-performance sink can run side by side on the same processor.)

In an **embedded processor** the three pieces above are the whole configuration. In a **Mongoose
server** the level and the sink are server configuration rather than code — see the Mongoose server
documentation — but the same three things must be true, and the same `---` rule applies to whatever
writes the file.

## What you get

Each cycle becomes one record — the triggering event, timing, and the ordered `nodeLogs`. See
[Log format](log-format.md) for the record shape and a field reference. That deterministic, replayable
record is what the whole analyser toolchain works from.
