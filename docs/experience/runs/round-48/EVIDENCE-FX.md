
## Evidence

**Do not build a log format of your own, and do not aggregate or re-emit anything.** Every component
already records itself: each node writes its own name and value to the framework's audit log as it
runs. Your job is only to capture that log and write it out.

```java
processor.setAuditLogProcessor(record -> lines.add(record.asCharSequence().toString()));
processor.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
processor.init();
```

Write those records to the audit output file **exactly as they come**, one per line, nothing added.
The 14 published figures will already be in there, because the components put them there.
