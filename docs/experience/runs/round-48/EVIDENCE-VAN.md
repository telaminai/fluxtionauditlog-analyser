
## Evidence

**Do not build a log format of your own.** The components already record themselves: each writes
`(name, value)` to `com.vendor.contract.Audit.SINK`, a `BiConsumer<String,Double>` you assign, and
notes to `Audit.NOTE`, a `BiConsumer<String,String>`. Wire both to the audit output file and write
each record **as it comes**, one per line:

```
<name>,<value>          from SINK, value formatted %.6f
<key>,<text>            from NOTE
```

The sink records what ran but not what caused it, so **before dispatching each incoming event write
one line**:

```
EVENT,<lowercased event type>
```

one of `tick`, `trade`, `rate`, `config`, `strategy`. Nothing else goes in the audit file.
