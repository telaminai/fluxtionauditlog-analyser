# Audit record format — specification

**Format 1 · status: published, open.** This page is the normative description of the record format
the analyser reads. It exists so that anything that can describe a run — a Fluxtion processor, a
Mongoose server, a workflow engine, a translator over someone else's trace — can emit records the
analyser understands, and know *what the analyser will do with them*. The [Log format](log-format.md)
page is the friendly tour; this one is the contract.

The format is open by decision (spec *source adapters*, D-A6): the layout is not the thing worth
protecting, and an adapter ecosystem only exists if emitting it is safe and obvious. What is held is
the **name**. The analyser is the reference implementation, and the
[conformance fixtures](#conformance) are what "the analyser understands it" means, pinned.

The key words MUST, SHOULD and MAY are used as in RFC 2119.

## 1. Container and framing

- A **text container** is a sequence of records separated by lines consisting of `---`. A record is
  everything between two separators, from its first non-blank line. **Blank** text before the first
  separator, after the last, or between two separators is skipped; **non-blank** text anywhere is a
  record — there is no banner or comment-block position in Format 1, and a file that starts with one
  gets a `PARSE_ERROR` record for it (§2). The built-in reader handles this container.
- A **reader plugin** ([log-source plugins](user-guide/plugins.md)) hands records over one at a time
  as **canonical record text** — the same shape a text container holds between separators. For a
  container that is not text (a database, a columnar file, an engine's own trace store) that text is a
  *rendering* the reader produces. Everything above the reader — the detail view, the free-text
  filter, `read`, report quoting — works on that text, so it MUST be complete: a record with no text
  form is not a record.
- Records are consumed **in container order**. The analyser never re-sorts.

## 2. The record

A record is an optional **header line** followed by an `eventLogRecord:` mapping.

```yaml
---
#10:57:37.431 [marketMaker-DEMO] INFO  MAKER_DEMO        # header: time [thread] LEVEL logger — all optional
eventLogRecord:
  eventTime: -1                        # epoch millis of the driving event; -1 = not event-driven
  logTime: 1786355857430               # epoch millis — THE timeline
  groupingId: null                     # optional correlation id
  event: ExportFunctionAuditEvent      # event class or trigger type
  eventToString: public boolean com.acme.demo.VenueHedgeMonitor.orderVenueConnected(com.acme.demo.OrderVenueConnectedEvent)
  thread: marketMaker-DEMO
  nodeLogs:                            # ORDERED — see §4
    - hedgeConnectionMonitor: { status: CLOSED, hedgeQuantity: NaN}
    - bidMakerOrder: { orderStatus: noLiveOrder, isOrderClosed: true}
    - bidMakerOrder: { connected: true}
  endTime: 1786355857431
---
```

| Field | Type | Rule |
|---|---|---|
| header `#…` | text | MAY be present. Lenient: `#time [thread] LEVEL logger`, every part optional. Supplies `thread`, `logger` and `level` when the record does not. |
| `logTime` | integer | SHOULD be present. The primary timeline; the analyser windows, slides and validates on it and nothing else. Units are the reader's declared time base (§6); the built-in reader's is epoch milliseconds UTC. |
| `eventTime` | integer | MAY be present. The driving event's time. **`-1` is a sentinel** meaning *not event-driven* (a timer, a lifecycle step, an exported service call) and reads as absent. Never consulted for order. |
| `endTime` | integer | MAY be present. When the cycle finished. Absent means *not recorded*; an emitter constructing text from a source that did not record it MUST omit the field rather than write `0`. |
| `event` | string | MAY be present. The event class name or trigger type. |
| `eventToString` | string | MAY be present. The event's `toString()`. When it is a Java method signature the record is an **exported call** and its dimension is the callback name (§5). Kept whole; never re-split on inner `:`. |
| `groupingId` | string | MAY be present; the literal `null` reads as absent. |
| `thread` | string | MAY be present. Wins over the header's thread when both are given. |
| `nodeLogs` | list | SHOULD be present. The cycle's contributions, §3–§4. |
| anything else | — | **Ignored, never rejected.** A newer producer MUST NOT break an older analyser; an emitter MAY add fields, and MUST NOT expect them to mean anything yet. |

**Every record is kept.** A slice that cannot be parsed becomes a `PARSE_ERROR` record carrying its
raw text; counts are preserved and nothing is dropped silently. A record with no `logTime` is a
legitimate record: it is kept and readable, it is **off the timeline** (it does not move the log's
start or end), and it **orders nothing** (§2.1). *(Fixtures C01, C02, C03, C04, C05, C09.)*

### 2.1 Time order is a claim the analyser checks

`logTime` SHOULD be non-decreasing in container order. When it is not, the analyser **reports** it —
kind `OUT_OF_ORDER`, with the first offending record — in the status bar, in `context`, and as a
caveat on every time-anchored verb. It **never repairs**: a backwards timestamp is evidence of a clock
step, a bad merge or a bad transport, and re-sorting would destroy it. An emitter that goes backwards
is told, every time. *(Fixture C06.)*

## 3. `nodeLogs` — values and attribution

Each item is `- instanceId: { key: value, key: value, … }` — one component's contribution to this
cycle. `instanceId` is the component's stable identity (in Fluxtion, the node's field name in the
generated processor); it is the **join key** to a declared graph (§7), to source navigation, and to
every `instanceId.key` series.

- **Values are not YAML.** They are raw `toString()` output: inner commas, brackets, `NaN`,
  `key=value` runs. The analyser splits only on **top-level** separators, respecting `()`, `[]`,
  `{}` and quotes, and never fails a record on a value. Only top-level numeric and boolean values are
  graphable; a number inside a `toString()` is text. *(Fixture C08.)*
- **Quote marks are the producer's characters.** Under this grammar a `"` protects the separators
  inside it while the tokenizer scans, and that is all: nothing is decoded, a backslash is a
  character, `"hello"` keeps its quotes, and `prefix "C:\"` closes at its second quote mark. This is
  how every text log has always been read, and it does not change. *(Fixture C17.)*

### 3a. The quoted-scalar grammar — declared by the reader, never by the text

The **reader** that constructs record text from *typed* values declares a second grammar through the
SPI: `AuditLogReader.textEncoding()` returns `QUOTED_SCALARS`, and the parser applies it to every
record that reader delivers. It adds one rule to §3: **a double-quoted scalar is a string, whatever
it spells.** An instance id, key or value that is *entirely* `"…"` is decoded — the escapes are
`\\`, `\"`, `\n`, `\r`, `\t`, and while scanning a backslash inside double quotes escapes the next
character — and read as text: `"42.0"` is not a figure, `"null"` is not null, `"true"` is not a flag,
and the commas, colons, braces and line breaks inside it split nothing. Anything not entirely one
quoted scalar is the raw text it always was. *(Fixture C16, through a reader that declares it.)*

This is the one lossless spelling for text that would otherwise *be* syntax. The built-in binary
reader declares it and MUST use the quoted form for any string the bare form would mis-split or
mistype; a reviewer showed a logged `"ok, price: 42.0"` written bare manufacturing a numeric figure
the producer never published, and a logged `'` character deleting the entry after it. An adapter for
another typed engine that constructs text does the same: declare, and quote.

**Reserved keys.** Under the declared grammar, a *bare* key beginning with `@` belongs to the
reader, never to the producer: a business key containing `@` is quoted on the way out and decodes
as an ordinary key. Two are defined, and neither becomes a business entry. `@invoked: true` marks a
wire TRACE entry (tag 8, key 0) and becomes node **metadata** — `traced` on the node-log, beside the
entries and never in them, so a business key that decodes to the same spelling keeps its own slot in
every lookup, diff and field projection. It is evidence that *its* node ran, and nothing about nodes
that did not log; it is not a declaration that every invocation was traced. The binary format carries
no such declaration, so for a binary log absence stays *may have run*, and the text format's
`method`-on-every-node heuristic applies only to records the text reader produced — applicability is
carried on the record, from the reader's declaration, never taken from a property spelling.
`@unkeyed: value` carries a wire entry that had no key and was not a TRACE, kept as an entry with no
key — **unnamed evidence**, distinct from every named property including one named `null`, shown
beside the node and outside every named-figure reduction: the scorer, series, the graph menu and
field projections address names, and a keyless value has none. The legacy grammar has no reserved
keys.

Both markers are the reader's **display vocabulary**, and every consumer that shows or re-reads a
record works from the parsed model, never from that spelling. The consumers pinned by test are:
the `read` verb and the report table assembly (the same call, the same parsed record — a table never
re-parses the record's text under the other grammar, `ReportBinaryEvidenceTest`); the record detail's
exact-key click (a token is a key only when the parsed node logged that name, so a click on
`@unkeyed: 42` never offers the property `unkeyed`, `DetailPanelExactClickTest`); the logical view
(a keyless entry prints as `@unkeyed`, never as the word `null`, `LogicalLogViewTest`); the topology
graph menu, series, diff and the scorer (rounds 8 and 9).

**Encoding is selected from the reader's declared context. Logged content MUST NOT select or change
it.** There is no field in the text that switches grammar, because the same bytes cannot say whether
a quote mark — or a line spelled like a field — was the producer's data or encoding syntax. An
earlier draft put a `nodeLogsEncoding: quoted` scalar in the record; a reviewer then logged a
multiline legacy value whose middle line *was* that scalar, and the parser promoted value data into a
control field and deleted the entry after it. The declaration now lives where the text cannot reach
it. A text file opened by the built-in text reader is therefore always legacy, byte for byte as
before; that reader has no declaration to make and the text runtime never quotes. *(Fixture C17
pins the round-5 record, `"hello"` keeping its quotes, and the multiline record.)*

## 4. Order is a claim, and the reader declares it (D-A1a)

The `nodeLogs` list is ordered. **What that order means depends on the reader's declared
`ordering` capability (§6):**

| `ordering` | position in `nodeLogs` means | the analyser will |
|---|---|---|
| `TOTAL` | the real execution sequence within the cycle — safe to read as causality | step through it as "step *n* / *m*", paint dispatch-order badges, escalate routes along it |
| `PARTIAL` | the order the entries arrived in; the source could not decide a sequence | **not paint** ordinal badges, say "logged *n* / *m*" instead of "step", carry a standing *arrival order, not dispatch order* warning in the Topology status, and put `orderMeaningful: false` with its caveat in the `topology` echo |

A Fluxtion processor's order is derived by the AOT compiler and **is** dispatch order: the built-in
reader is `TOTAL`. An adapter over a concurrent engine — parallel super-steps, concurrent activities,
overlapping spans — MUST declare `PARTIAL` rather than invent a sort key. The spike found the two
cases *indistinguishable on screen* without the declaration; with it, the tool says which it is
showing. *(Fixture C10.)*

**Not in Format 1:** a *per-cycle* concurrency marker, so that a mostly-sequential engine can be
honest about the cycles that were not without disclaiming every record. D-A1a specifies it; the
format does not yet carry it, and this page will say so until it does (see §8).

## 5. Exported calls and dimensions

When `eventToString` is a Java method signature — `public boolean com.acme.X.method(com.acme.Arg)` —
the record is an **exported service call**: something outside the processor invoked one of its
exported functions. Its **dimension** (the value the event filter and grouping use) is the callback
name (`method`), and the declaring type is captured for source navigation. Otherwise the dimension is
the `event` value. `eventTime` is `-1` on such records because no event drove them. *(Fixture C13.)*

## 6. What a reader declares

A reader plugin ([SPI](user-guide/plugins.md)) declares, and the analyser degrades loudly where the
answer is *no* rather than assuming:

| declaration | meaning |
|---|---|
| `timeBase` | **mandatory**: epoch unit (`millis`/`micros`/`nanos`), zone, and source (`wallClock`/`monotonic`/`injected`). Declared, never sniffed. |
| `capabilities.follow` | the source can be tailed |
| `capabilities.byteAnchors` | records have real byte offsets in a file (text containers only); otherwise anchors are by record index |
| `capabilities.randomAccess` | records can be re-read by position |
| `capabilities.ordering` | `TOTAL` or `PARTIAL`, §4. The pre-M34 three-argument form defaults to `TOTAL`, which is true of every container that existed before the claim did. |
| `graph(source)` | MAY return the source's own graph, §7. Empty is an answer. |

## 7. A graph, and where it came from (D-A2, D-A4)

A source MAY supply the structure its records refer to, in the analyser's own vocabulary: nodes
`{id, label, className, kind}` and edges `{id, source, target}`, with `kind` one of `NODE`, `EVENT`,
`EVENT_HANDLER`, `EXPORT_SERVICE`. It MUST say whether the graph is **`DECLARED`** (the source states
its own structure — a compiler emitted it, a registry holds it) or **`INFERRED`** (reconstructed from
what ran). A graph without a provenance cannot be constructed.

The distinction is load-bearing: **coverage is *declared minus observed*.** Against an inferred graph
the subtraction is empty by construction — the answer is always 100% — so the analyser refuses to
compute it and says why, and the Topology view states that an absence of "did not run" nodes proves
nothing there. Node ids MUST match `nodeLogs` instance ids, because that is the join. An edge to an
undeclared node is dropped. A graph the user opened by hand always wins over one the source supplied;
the view always says which it is showing.

Engine scaffolding — pseudo-nodes, schedulers, the engine's own plumbing — MUST NOT be declared as
authored nodes, or it will count as *never ran*. The analyser hides Fluxtion's own scaffolding by
class name; a foreign adapter declines to emit its equivalent.

## 8. What absence means (UP-FLX-11)

A component that does not appear in a record either **did not run** or **ran and logged nothing**,
and the record alone cannot say which. Format 1 has no field for it. The analyser infers a **traced**
record — one where *every* entry carries a `method` key, which only Fluxtion's invocation tracing
adds — and only then treats absence as *did not run*; otherwise it says *may have run*, never *did
not*. A single component logging a business key called `method` MUST NOT make a sparse record look
complete, and does not. *(Fixture C12.)*

A declared marker is an open upstream ask (UP-FLX-11). Until it lands, an emitter that traces every
invocation SHOULD carry `method` on every entry so the inference holds; one that does not MUST NOT
fake it.

**Open in Format 1** — specified elsewhere, not yet carried by the record, and stated here so no
emitter fills the gap with an invention:

1. the per-cycle concurrency marker (§4, D-A1a);
2. the traced-regime marker (§8, UP-FLX-11).

## Conformance

The fixture set lives at
[`src/test/resources/conformance/`](https://github.com/telaminai/fluxtionauditlog-analyser/tree/main/src/test/resources/conformance)
— one file per pinned semantic, with a README table saying what each pins — and
`FormatConformanceTest` is what *passing* means. Every fixture runs twice: through the built-in text
reader (the reference implementation) and through the plugin SPI over a reader that hands the same
records over unchanged. The two MUST agree record for record — which is the guarantee to an adapter
author: *emit these records and you get exactly what the native log gets.*

| fixture | pins |
|---|---|
| C01 minimal | `logTime` + one entry is a whole record; it is already graphable |
| C02 unknown fields | ignored, never rejected — forward tolerance |
| C03 header | optional, lenient; the scalar `thread` wins |
| C04 times | `logTime` is the timeline; `eventTime: -1` is absent; `endTime` optional; `eventTime` never orders |
| C05 untimed | kept, off the timeline, orders nothing |
| C06 out of order | reported with its first record; never re-sorted |
| C07 duplicate instanceId | every occurrence kept; last wins; one point per record |
| C08 lenient values | only top-level separators split; nothing fails; `NaN` detected |
| C09 garbage | a `PARSE_ERROR` record with its text; neighbours untouched; count preserved |
| C10 ordering claim | `TOTAL`/`PARTIAL` is the reader's and reaches the index; the old constructor means `TOTAL` |
| C11 attribution | the core attributes by position and never merges — broadcast makes duplicates; a component-less key is not even expressible |
| C12 traced regime | absence is *did not run* only when every entry is traced; one `method` key proves nothing; a business `invoked: true` proves nothing in either spelling — the binary reader's trace marker (`@invoked`, §3a) is provenance that its node ran, never a completeness declaration |
| C13 exported call | dimension is the callback; declaring type captured; `eventTime` absent |
| C14 synthesised text | text an adapter *constructs* (no trailing newline, a leading `---`, CRLF) reads exactly as sliced file text |
| C16 quoted scalars | through a reader that declares `QUOTED_SCALARS`, entirely `"…"` is a string whatever it spells; escapes decode; its insides split nothing; the same bytes through the text reader are legacy |
| C17 legacy quotes | text is legacy, byte for byte: quotes are data, a backslash is a character, the figure after `prefix "C:\"` is still a figure, and a value line spelled like a field is value data |
| C15 graph provenance | a `SourceGraph` cannot exist without DECLARED/INFERRED; INFERRED forbids coverage; an opened graph outranks a supplied one; dangling edges dropped |

To check an emitter: write its records to a file, open it in the analyser (or run the fixture
through `SpiLogStore` with your reader), and compare against the table. To propose a fixture, add the
file **and** the assertion — a fixture nobody asserts on is a promise nobody keeps.
