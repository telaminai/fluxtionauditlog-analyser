# Spec — `fluxtion-audit-reader`, a binary audit log reader that is itself a Fluxtion processor

**Status:** PROPOSED · **Owner:** fluxtion-compiler (new module) · **Repo:** `telaminai/fluxtion-compiler`
**Depends on:** [`spec-binary-audit-encoding.md`](spec-binary-audit-encoding.md) §6.3 (the wire format)
**Blocks:** that spec's §10 step 6 — `LOW_LATENCY_AUDIT` cannot select the binary record until something
can read it.

## 1. Why this exists, and why here

The binary encoder spec records a hard dependency in its §9: **a binary record with no reader is a log
nobody can open.** This is that reader.

It is specified as a **Fluxtion event processor** rather than a parser loop, for three reasons that are
not decoration:

1. **Filtering is conditional propagation.** A time range, a node pattern, a value predicate — each is a
   node returning `boolean` from `@OnTrigger`, and a non-matching record stops the wave at the first
   filter that rejects it. That is the framework's own primitive, and it means the cost of a rejected
   record is the cost of the *cheapest* filter that rejects it, not the sum of all of them.
2. **The pipeline is the extension point.** "Print to text" and "publish to Aeron" are the same graph
   with a different terminal node. So is "filter, then re-encode, then republish".
3. **It is the honest dogfood.** If a Fluxtion graph is the right way to build a filtered stream
   processor, the tool that reads Fluxtion's own audit log should be one.

**Why a separate project from core:** the reader *is* a generated processor, so it needs the Fluxtion
compiler at build time. Core cannot depend on the compiler — the compiler depends on core. The
dependency order forces the module downstream of both, and the compiler repo is where that is.

**Placement:** a new module `fluxtion-audit-reader` under `com.telamin.fluxtion:master`, alongside
`fluxtion-generator-app` — which is the existing precedent for a shaded, main-class module in that repo.

## 2. Goals

| # | Goal |
|---|---|
| G1 | Read a binary audit log at a rate that is **I/O bound, not decode bound** |
| G2 | Memory-map the file when it fits a single mapping; work correctly when it does not |
| G3 | Filter by **time range, node, key, event type, and value predicate**, with pattern matching |
| G4 | Print human-readable text **by default**, with the sink **pluggable** |
| G5 | Compile to a **native AOT binary** in the default text configuration |
| G6 | Zero allocation per record on the steady-state path |

**Non-goal:** replacing the analyser. This is a command-line filter and a pipeline host — `grep` and
`tail` for audit logs. The analyser remains the place where a log is explored, correlated and reported on.

## 3. What the reader forces the format to settle

The encoder spec left **dictionary publication** as an open question (its §11). A reader closes it,
because a reader that cannot resolve id → name cannot print anything.

**Normative — the format MUST carry its own dictionary.**

- A **dictionary entry record** (`0x02`) MUST be written the first time a name is interned:
  `0x02, id:u16, len:u16, utf8 bytes`.
- The **full dictionary MUST be re-emitted at the start of every file/roll**, so a reader can start at
  any file in a rolled set without replaying earlier files. This is the same constraint the analyser's
  rolled-log-set support (M30) already lives with.
- A reader encountering an unknown id MUST NOT fail. It MUST render the name as `#<id>` and continue,
  and MUST report at exit how many unresolved ids it saw. A truncated or mid-roll file is a normal
  thing to be handed, not an error.

That last rule is the one that matters in practice: the common case for reading an audit log is that
something went wrong, and the file is exactly as complete as the process managed to make it.

## 4. Reading the file

### 4.1 Normative — mapping

- If file size **≤ 2 GiB − 1**, the reader MUST map it in a single `FileChannel.map` call.
  The limit is not arbitrary: `MappedByteBuffer` inherits `Buffer`'s `int` capacity, so one mapping
  cannot address more.
- Above that, the reader MUST NOT fail and MUST NOT silently truncate. It MUST use a chunked mapping
  with an explicit **straddle rule**: a record that crosses a chunk boundary is copied into a small
  reassembly buffer and decoded from there. Chunk size and overlap are implementation detail; the
  straddle case MUST have a test with a record deliberately placed across the boundary.
- The implementation MAY instead use `java.lang.foreign.MemorySegment.ofMappedFile`, which has no 2 GiB
  limit and removes the straddle case entirely. If it does, the ≤ 2 GiB path MUST still be a single
  mapping, and the straddle test MUST still exist to prove the fallback works.

### 4.2 Normative — decoding

- A record MUST be decoded as a **flyweight over the mapped memory** — a cursor object that moves, not
  an object allocated per record. G6 depends on this and so does native AOT under a non-collecting GC.
- The decoder MUST validate the record tag and length before reading fields, and MUST stop cleanly at
  the first malformed record, reporting the byte offset. A partially-written trailing record is the
  expected end state of a crashed process; it is a normal termination, not a stack trace.

## 5. The graph

```
   main loop                 generated Fluxtion processor
   ---------                 ----------------------------
   mmap ─► decode ─► onEvent ─► TimeRange ─► EventType ─► NodeMatch ─► KeyMatch ─► Value ─► Sink
   (flyweight cursor)          └─ each returns boolean; false stops the wave here ─┘
```

### 5.1 Normative

- Every filter node MUST be a `@OnTrigger` returning `boolean`, so rejection stops propagation. A filter
  that cannot reject (because it was not configured) MUST be absent from the graph, not present and
  returning `true` — the graph is built per invocation, and an unconfigured filter is a node that costs
  a call per record for nothing.
- Filters MUST be ordered **cheapest-and-most-selective first**. The default order is time range, then
  event type, then node, then key, then value predicate. Time range is first because it is an integer
  compare and because a time-bounded query is the common one.
- The processor MUST be generated **AOT at build time** via the `fluxtion-maven-plugin` `scan` goal.
  No runtime graph construction, no reflection: G5 depends on it.

### 5.2 The optimisation that makes pattern matching cheap

**Normative:** node and key patterns MUST be resolved against the dictionary **once**, into a set of
matching `short` ids, and per-record matching MUST then be an id comparison — not a string or regex
match against a rendered name.

The dictionary is small (tens of entries), fixed after warm-up, and known before the first data record
of a file. So a `--node "order*"` query does one glob evaluation per dictionary entry at startup and an
integer set-membership test per record thereafter. A reader that matched text per record would spend
more time on the pattern than on the I/O.

A pattern that matches **no** dictionary entry MUST short-circuit the whole scan and report zero matches
without reading the data records at all.

### 5.3 Time ranges

`--from` / `--to` filter on `logTime`. Records in a queue are written in processing order, so a scan
MAY terminate early once `logTime > to`.

**Normative:** early termination MUST be **opt-in** (`--assume-time-ordered`), not the default. Time
ordering is a property of how the log was written, not of the format, and the analyser already carries
explicit time-order validation (M30) precisely because rolled sets have been found out of order. A
reader that silently stops early on an unordered log returns a wrong answer that looks like a right one.

## 6. The sink

### 6.1 Normative

- The terminal node MUST write through an `AuditRecordSink` interface, never directly to `System.out`.
- The default implementation MUST render the **same YAML shape the text `LogRecord` produces**, so that
  output piped into the analyser is readable by the existing `YamlAuditReader` with no new code. This is
  what makes the reader useful on day one rather than after a reader plugin lands.
- Sink selection MUST be by name from a **build-time registry**, not `ServiceLoader` or reflection.

That last point is a real constraint, stated because it will otherwise be discovered late: under
closed-world AOT there is no runtime discovery. Adding an Aeron publisher means adding a module and
producing a binary that contains it. The pluggability is at **build** time. A JVM-mode build MAY
additionally support `ServiceLoader`, but the native binary is the reference configuration and it cannot.

### 6.2 Sinks in scope

| Sink | Status | Notes |
|---|---|---|
| `text` | **required**, default | YAML shape, analyser-readable |
| `binary` | required | passthrough re-encode — makes the reader a filter, so `reader \| reader` composes |
| `null` | required | for measuring decode+filter cost with no output, as §7 requires |
| `aeron` | future | separate module, separate binary; named here so the interface is shaped for it |

`binary` passthrough is not a luxury: it is what makes "filter a 40 GB log down to the 200 records that
matter, then hand that to the analyser" a single command.

## 7. Performance

**Normative — these MUST be measured and recorded before the module is called done**, in the style of
`docs/experience/runs/` (predictions written down first, then results, then scoring):

| # | What | Target |
|---|---|---|
| P1 | decode + reject, `null` sink, filter matching nothing | **I/O bound** — within 20% of `wc -c` on the same file |
| P2 | decode + accept + `text` sink | reported, not targeted — text is known to be the expensive part |
| P3 | allocation per record, steady state | **zero** |
| P4 | native AOT vs JIT | reported both ways |

P4 is reported rather than targeted deliberately. Round 63 §8 measured native AOT at **1.44× slower
than JIT at building text records** and 1.59× slower at building binary ones, while being 2.9× faster at
dispatch. A reader is dispatch-light and encode-heavy, so **it is genuinely unclear which way AOT will
land**, and this spec declines to guess. Startup time will favour native regardless, which for a CLI run
against a small file may dominate everything else.

## 8. Command line

```
fluxtion-audit-reader [options] <file>...

  --from <time>            logTime lower bound (ISO-8601 or epoch millis)
  --to <time>              logTime upper bound
  --assume-time-ordered    permit early termination once past --to (see §5.3)
  --event <glob>           event type
  --node <glob>            node name
  --key <glob>             property key
  --value <expr>           value predicate, e.g. 'price > 100'
  --grep <text>            substring match against any rendered string value
  --limit <n>              stop after n matching records
  --sink text|binary|null  default: text
  --out <file>             default: stdout
  --stats                  record counts, unresolved ids, bytes read, elapsed
```

**Normative:** `--stats` MUST report **unresolved dictionary ids** and **records skipped as malformed**.
Both are silent data-loss conditions, and a reader that hides them is worse than one that refuses to run.

## 9. Module shape

```
fluxtion-audit-reader/
  pom.xml                        parent com.telamin.fluxtion:master
  src/main/java/…/reader/
      AuditReaderMain.java       arg parsing, mapping, the feed loop
      RecordCursor.java          flyweight decoder over mapped memory
      Dictionary.java            id -> name, glob -> id-set resolution (§5.2)
      nodes/                     the filter nodes — scanned by the maven plugin
      sink/AuditRecordSink.java  + TextSink, BinarySink, NullSink
  src/main/resources/            native-image config
  src/test/java/…                incl. the straddle test (§4.1) and a malformed-tail test (§4.2)
```

Build: `fluxtion-maven-plugin:scan` generates the processor into `target/generated-sources`;
`maven-shade-plugin` produces the runnable jar, as `fluxtion-generator-app` already does;
a `native` profile produces the AOT binary.

## 10. Sequencing and dependencies

| # | Step | Blocked by |
|---|---|---|
| 1 | dictionary records in the format (§3) | encoder spec §6 |
| 2 | module skeleton, cursor, `text` sink, single mapping | 1 |
| 3 | filters + dictionary-resolved patterns (§5.2) | 2 |
| 4 | chunked/straddle path, malformed-tail handling | 2 |
| 5 | native AOT profile + P1–P4 measurements | 3 |
| 6 | `binary` passthrough sink | 3 |
| 7 | Aeron sink | 6, separate module |

Steps 1–3 are the useful minimum: at step 3 the tool can filter a binary log and print YAML the analyser
already reads.

## 11. Open questions

- **Does the analyser consume this, or duplicate it?** The cleanest answer is that the analyser's binary
  reader (UP-RDR-01) and this tool share the cursor and dictionary code as a small library, and this
  module is the CLI around it. That means a fourth artifact, which nothing here has budgeted for.
- **`--value` expression syntax.** The analyser already has an expression language (M28: conditionals,
  rolling windows). Reusing it is attractive and is a dependency in the wrong direction. Unresolved.
- **Multi-file input.** `<file>...` implies rolled sets, which implies the analyser's time-order
  validation. Whether this tool reimplements that or declares it out of scope is not settled here.
