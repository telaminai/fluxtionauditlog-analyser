
## Evidence

**Do not build a log format of your own.** The subsystems already record themselves: each writes
`(stageName, newValue)` to `com.vendor.Audit.SINK`, a `BiConsumer<String,Double>` you assign. Wire
that sink to the output file and write each record **as it comes**, one per line, exactly:

```
<stageName>,<value>
```

The sink records stages but does not say which event caused them, so **before dispatching each
incoming event, write one line**:

```
EVENT,<lowercased event type>
```

`<lowercased event type>` is one of `tick`, `trade`, `rate`, `config`. Nothing else goes in the file.
Do not summarise, reorder or post-process — write records in the order they occur.
