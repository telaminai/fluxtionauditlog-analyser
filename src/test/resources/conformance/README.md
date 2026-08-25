# Conformance fixtures — the audit record format (Format 1)

One file per pinned semantic. `FormatConformanceTest` is what *passing* means: every fixture is run
through the built-in text reader and through the plugin SPI over a pass-through reader, and the two
must agree record for record. The normative page is `docs/site/format-spec.md`.

A fixture is a plain log — it starts with `---` and carries no banner, because **a non-blank leading
segment is a record** (the framer skips only blank ones). The first version of this set learned that
the hard way: twelve banner comments became twelve `PARSE_ERROR` records, and the spec page's claim
that a leading comment block is skipped was wrong. The fixtures corrected the spec, which is the job.

| fixture | pins |
|---|---|
| `c01-minimal.yaml` | The smallest conforming record: logTime and one nodeLogs entry. Everything else is optional. |
| `c02-unknown-fields.yaml` | Forward tolerance: unknown top-level scalars are IGNORED, never rejected. A newer producer must not break an older analyser. |
| `c03-header.yaml` | The # header line is optional and lenient: #time [thread] LEVEL logger. The thread scalar wins when both are present; the header supplies it otherwise. |
| `c04-times.yaml` | logTime is the primary timeline. eventTime: -1 is the NOT-EVENT-DRIVEN sentinel (a timer or an exported call) and reads as absent. endTime is optional. |
| `c05-untimed.yaml` | A record with no logTime is a legitimate record: it is kept, indexed and readable, it is excluded from the timeline (min/max), and it ORDERS NOTHING - the time-order validator ignores it. |
| `c06-out-of-order.yaml` | logTime SHOULD be non-decreasing. When it is not, the analyser REPORTS it (kind OUT_OF_ORDER, with the first offending record) and never re-sorts: a backwards timestamp is evidence. |
| `c07-duplicate-instance.yaml` | The same instanceId MAY appear more than once in a cycle (a node logging at several points). Every occurrence is kept in order; when one value is needed, the LAST occurrence wins. |
| `c08-lenient-values.yaml` | nodeLogs values are NOT YAML: raw toString() output with inner commas, brackets, NaN and key=value runs. Only TOP-LEVEL separators split; nothing fails the record; NaN is detected. |
| `c09-garbage.yaml` | A slice that is not a record is kept as a PARSE_ERROR record with its raw text: the count is preserved and nothing is silently dropped. The records around it are unaffected. |
| `c11-attribution.yaml` | D-A3 attribution: a value appears under a component only if THAT component produced or changed it. The core attributes strictly by position - instanceId.key - and never merges; an emitter that broadcasts shared state under every component creates duplicate series. Decline to emit rather than broadcast. |
| `c12-traced-regime.yaml` | What ABSENCE means (UP-FLX-11). Record 0 is TRACED: every entry carries a method key, which only invocation tracing adds, so an unlogged declared node DID NOT RUN. Record 1 is untraced: absence says nothing, and the analyser must say 'may have run', never 'did not'. |
| `c13-exported-call.yaml` | An exported service call: eventToString is a Java method signature, so the record's dimension is the CALLBACK name and the declaring type is captured; eventTime is -1 because no event drove it. |

C10 (the ordering claim) has no file: the claim belongs to the reader, not the record — the test opens
`c01-minimal.yaml` under `TOTAL` and `PARTIAL` declarations and reads the index.
