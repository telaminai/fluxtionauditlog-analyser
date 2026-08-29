---
name: run-embedded
description: Start and stop this project's processor in-process, wire its audit log correctly, and find the file it writes.
x-analyser-min-version: 1.12.0
---

# Run the processor in-process

For a project that embeds the Fluxtion runtime rather than deploying to a server.

## STATUS: NOT PUBLISHABLE — the end-to-end route is unverified

**Do not ship this tier in a bundle yet (review F5/F8, 2026-08-29).** The mechanism below is correct and
the listener compiles, but *nobody has run a real embedded processor through it and opened the result in
the analyser*. Verifying that needs a compiled processor, which needs a Fluxtion API key. Until someone
with a key does it, this file is a design note, not a canonical skill.

What was actually verified, and what was not:

| Claim | Status |
|---|---|
| `addSink` is business output; `setAuditLogProcessor(LogRecordListener)` is the audit route | ✅ `javap`, runtime 1.0.13 |
| the listener below **compiles** against `LogRecordListener` | ✅ compiled |
| `processLogRecord` declares no checked exception, so IO must be handled inside | ✅ `javap` |
| the file it writes **parses in the analyser** | ❌ **NOT VERIFIED** — needs a real processor run |

A synthetic attempt made this concrete: a hand-built `LogRecord` fed straight to the listener produced
**malformed output** — no `eventLogRecord:` wrapper, no `logTime`, an unclosed brace — because the
framework completes a record during dispatch. **The listener cannot be validated in isolation.** That is
worth knowing before anyone tries to shortcut the verification.

## The audit log is NOT a sink

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

1. **Install the audit processor before running.** `LogRecordListener.processLogRecord(LogRecord)`
   declares **no checked exception**, so a `BufferedWriter` lambda does not compile — its `write`,
   `newLine` and `flush` all throw `IOException`. The shipped `JULLogRecordListener` shows the shape:
   **the constructor owns the file and throws; the listener method cannot.**

   This compiles against runtime 1.0.13 (verified by compiling it, not by reading it):

   ```java
   public final class AuditFileWriter implements LogRecordListener, AutoCloseable {
       private final BufferedWriter writer;

       public AuditFileWriter(Path file) throws IOException {         // the constructor throws
           if (file.getParent() != null) Files.createDirectories(file.getParent());
           this.writer = Files.newBufferedWriter(file,
                   StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
       }

       @Override public void processLogRecord(LogRecord record) {     // this one cannot
           try {
               writer.write("---");        // the separator is YOURS — record.toString() omits it
               writer.newLine();
               writer.write(record.toString());
               writer.newLine();
               writer.flush();
           } catch (IOException e) {
               throw new UncheckedIOException("audit log write failed", e);
           }
       }

       @Override public void close() throws IOException { writer.close(); }
   }
   ```

   Wire it, setting the **level before the listener** — setting the level dispatches a control event
   through the graph, so a listener attached first records your own configuration as a cycle:

   ```java
   dataFlow.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
   dataFlow.setAuditLogProcessor(auditFileWriter);
   ```

2. **Start it with this project's own entry point.**

   TODO(bundle): name the class and command. An embedded host has no standard main; do not invent one.

3. **Confirm the file is actually growing.** An embedded run with no audit processor, or a build without
   `addEventAudit()`, produces an **empty file** rather than an error. If it is empty, open the GraphML
   in the analyser and read `graphPairing.auditLogging` — `not_enabled` means the build is the problem
   and no amount of wiring here will fix it.

4. **Stop through the same entry point**, so the writer is flushed and closed. There is no admin endpoint
   to ask: the process *is* the deployment, so only a clean shutdown closes the file.
