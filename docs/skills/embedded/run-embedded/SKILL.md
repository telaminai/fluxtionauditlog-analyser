---
name: run-embedded
description: Start and stop this project's processor in-process, wire its audit log correctly, and find the file it writes.
x-analyser-min-version: 1.12.0
---

# Run the processor in-process

For a project that embeds the Fluxtion runtime rather than deploying to a server.

## The audit log is NOT a sink — this is the part that is easy to get wrong

There are two unrelated output mechanisms on `DataFlow`, and only one produces the records the analyser
reads. Verified against `fluxtion-runtime` 1.0.13:

| Method | Takes | Produces |
|---|---|---|
| `addSink(String, Consumer<T>)` | a business consumer | **application output** — a named graph output |
| `setAuditLogProcessor(LogRecordListener)` | `LogRecordListener` | **the audit log** — the `eventLogRecord` stream |

`FileMessageSink` extends `AbstractMessageSink`, **not** `LogRecordListener`. It cannot be passed to
`setAuditLogProcessor` at all, and wiring it through `addSink` gives you business output while the audit
file stays empty. An earlier version of this skill said otherwise and was wrong — a review caught it
before it shipped.

## Steps

1. **Install the audit processor before running.** `LogRecordListener` has one method,
   `processLogRecord(LogRecord)`, and the record's `toString()` is the analyser's format — except that
   **the `---` separator is yours to write.** Omit it and a whole file reads as one record, silently:

   ```java
   dataFlow.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);   // set the level FIRST
   dataFlow.setAuditLogProcessor(record -> {
       writer.write("---");            // the separator the format requires — record.toString() omits it
       writer.newLine();
       writer.write(record.toString());
       writer.newLine();
       writer.flush();
   });
   ```

   Set the level **before** attaching the processor. Setting it dispatches a control event through the
   graph, so a processor attached first captures that event as a record about your own configuration.

2. **Start it with this project's own entry point.**

   TODO(bundle): name the class and command. An embedded host has no standard main; do not invent one.

3. **Confirm the file is actually growing.** An embedded run with no audit processor, or a build without
   `addEventAudit()`, produces an **empty file** rather than an error. If it is empty, open the GraphML
   in the analyser and read `graphPairing.auditLogging` — `not_enabled` means the build is the problem
   and no amount of wiring here will fix it.

4. **Stop through the same entry point**, so the writer is flushed and closed. There is no admin endpoint
   to ask: the process *is* the deployment, so only a clean shutdown closes the file.
