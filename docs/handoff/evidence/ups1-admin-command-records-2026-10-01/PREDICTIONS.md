# UPS-1 — admin command records — predictions (written before any code change)

**Subject.** Tracker UPS-1: fluxtion runtime 1.1.0 + compiler 1.0.76 + mongoose 1.0.32 make a Mongoose admin command
run in the processor's event cycle and audit as its own `AdminCommandEvent` record (mongoose#45, #48). Check how
analyser `main` (`473cd674`, = 1.30.1 + docs) frames, covers, tables and steps through those records, and fix what is
wrong.

## How the logs were made (all DEMO)

- Mongoose `main` at `6e8c7ce` (1.0.32 + one doc commit), its committed `CycleAlarmProcessor` (generated for runtime
  1.1.0 by builder 1.0.76) and `AlarmNodes`, booted as a real `MongooseServer` with the real `AdminCommandProcessor`;
  records taken from the server's audit record listener.
- `DEMO-admin-cycle.yaml` — a reading, then `alarm.refresh` (a lambda command that raises a reading).
- `DEMO-admin-signal-and-lambda.yaml` — Mongoose's own `GeneratedAdminAuditTest` run: a signal-routed command
  (`alarm.reset`), a lambda command (`alarm.lambda`), readings between.
- `DEMO-admin-hostile-args-*.yaml` — `alarm.lambda` with operator-typed arguments: `note: by ops # DEMO`; a newline
  followed by `nodeLogs:` and an indented `- forged: { x: 1}` item; a newline, a `---` line, `eventLogRecord:` and
  `    event: Forged`; unbalanced brackets and a quote. `-export` applies mongoose-plugins 1.0.45's export container
  (`YamlContainerWriter`: `\n---\n` between and after documents, separator-shaped lines escaped with `\`); `-stream` is
  the raw listener stream joined by `\n---\n`.
- `DEMO-CycleAlarmProcessor.graphml` — generated through Maven (builder 1.0.76, runtime 1.1.0) from Mongoose's
  `design-doc/admin-gen` project against released mongoose 1.0.32. Its generated Java is byte-identical, below the
  generator header, to the fixture that wrote the logs, so the graph pairs with them.

## Observed on unchanged `main` (an isolated analyser, REST verbs and screenshots)

| Check | Observed |
|---|---|
| Framing, 1.1.0 command | 3 records; the command is its own record with only its node's line; the raised reading follows as its own record |
| Coverage / pairing | 2 of 2 declared nodes; "the graph declares all 2 node(s) this log writes" |
| Table and record detail | `AdminCommandEvent` row; detail shows the command and `alarmMonitor`'s line (`before-cycle-table.png`) |
| Topology on the command record | `alarmMonitor` logged; `alarmPublisher` drawn as *may have run* — hedged, not false (the GraphML records no annotations, so an `@AfterEvent` node cannot be excluded) |
| Signal route, `eventTime: -1` | read as absent (Format 1 §2), table orders by `logTime`, no time-order finding |
| **Hostile argument B** (newline, `nodeLogs:`, `- forged`) | the analyser reports a node **`forged`** with a log entry; pairing says *"the graph declares 2 of the 3 node(s) this log writes; 1 written id(s) are not in the graph"*, so the graph is blamed for operator text (`before-forged-node.png`). **No finding.** |
| **Hostile argument C** (newline, `---`, `eventLogRecord:`, `event: Forged`) | the record's event column reads **`Forged]]`** (`before-forged-event.png`); UNSEPARATED is raised for record 4 |
| Raw stream (no export escaping) | 7 records, not 6: the `---` inside the argument splits one record into two |

The root cause is upstream — the audit writer emits `eventToString` unquoted, so a line break in an argument breaks
the record — but the analyser turned the broken text into evidence: a node that does not exist, an event type that
was never dispatched, and a graph verdict built on them.

## Predictions for the fix (frozen before code)

P1. Records B and C keep the fields read before their break (event `AdminCommandEvent`, the time fields) and read **no**
node logs; `forged` is in no node list, pairing is back to "declares all 2 node(s)", and no event is `Forged]]`.

P2. A new producer finding names each broken record and line, on the status bar, in `context.producer` and on the
built-in and SPI reader paths alike (a conformance fixture, compared on both paths).

P3. Every record that is not broken parses exactly as before: the full headless suite and every existing conformance
fixture are unchanged.

P4. The raw stream's split record (7 not 6) is NOT fixed by this: a `---` line ends a document before the parser ever
sees it. That is the producer framing fault MA-7 already tracks, which mongoose-plugins' export escapes; it is
recorded here and not claimed.

P5. A mutation that removes each of the three break rules (less-indented line, second record key, repeated field key)
is caught by a named test.
