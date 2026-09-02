
## Evidence

**Do not build a log format of your own.** Enable the framework's audit log and write it to the audit
output file **exactly as it comes** — every record, unmodified, in the order emitted, one per line.

The generated processor implements `DataFlow`, which carries the two calls you need:

```java
processor.setAuditLogProcessor(record -> lines.add(record.asCharSequence().toString()));
processor.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
processor.init();
```
