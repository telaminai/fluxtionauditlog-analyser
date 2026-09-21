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
| `c16-quoted-scalars.yaml` | Under a READER that declares `TextEncoding.QUOTED_SCALARS`, a double-quoted scalar (instance id, key or value) is decoded with the escapes \\ \" \n \r \t and read as a STRING whatever it spells: "42.0" is not a figure, "null" is not null, "true" is not a flag, and the commas, colons, braces and line breaks inside it split nothing. The same bytes through the built-in text reader are legacy: quotes kept, nothing decoded. The grammar is the reader's declaration, never the text's. |
| `c17-legacy-quotes.yaml` | Text is read with the legacy grammar, byte for byte as it always was: a backslash is a character, a quote mark is the producer's data (`"hello"` keeps its quotes), `prefix "C:\"` closes at its second quote so the `price` after it is still a figure, and a multiline value containing a line that LOOKS like a control field (`nodeLogsEncoding: quoted`) is value data folded by continuation - nothing in the text selects a grammar. |
| `c13-exported-call.yaml` | An exported service call: eventToString is a Java method signature, so the record's dimension is the CALLBACK name and the declaring type is captured; eventTime is -1 because no event drove it. |

| `c18-stream-end.yaml` | A stream-end marker is a CONTAINER fact, not a record: the file holds two records and a marker claiming two, and both the built-in reader and the SPI path report a size of 2. The marker never reaches the index, a count, the timeline or the table (spec-audit-stream-end D-E4), and its `logTime` does not extend the time range. A file carrying no marker is UNKNOWN, never complete. |

C10 (the ordering claim), C14 (synthesised text) and C15 (graph provenance) have no file: their subject is
the reader — its declaration, its constructed text, the graph it hands over — not a record.
