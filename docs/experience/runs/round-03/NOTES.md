# NOTES.md — friction log

Task: run the system as it ships and use the audit log to diagnose a vague "one of the price events
isn't being handled properly" report. One line per moment of friction, in the order it happened.

- **Project told me wrong.** `.analyser/project.fluxtion-settings` declares
  `runbook.0.path=.claude/skills/load-audit-log/SKILL.md`. That file does not exist. The real file is
  `.claude/skills/read-audit-log/SKILL.md`. Anything that resolves the runbook pointer literally gets
  nothing; I found the skill only by listing `.claude/skills/`.

- **Project told me wrong.** `README.md` ▸ *What's here* lists
  "`ServerSmokeTest` — boots the server in-process via `MongooseTestHarness`". There is no
  `ServerSmokeTest` anywhere in the tree. The only test is
  `src/test/java/com/example/myapp/MarketProcessorGraphTest.java`, which asserts the supplier returns
  non-null and nothing else. I went looking for a smoke test as a cheap way to reproduce a run and
  wasted the lookup.

- **Project failed to tell me / I read generated source.** Nothing in README or either skill states
  **which nodes are even eligible to appear in `nodeLogs`**. That is the single fact the whole task
  turns on: it decides whether "node X is absent from the log" is evidence. The `read-audit-log`
  skill gets close ("a node that does not extend `EventLogNode` never appears at all") but that is
  **not the rule** — `RiskCheck` does *not* extend `EventLogNode` and appears in every record. I had
  to read the generated processor,
  `src/main/java/com/example/myapp/generated/MarketProcessor.java`, to find the actual mechanism:
  `auditInvocation(...)` (line 385) calls `eventLogger.nodeInvoked(...)`, and it is emitted by the
  generator only at dispatch sites. So the eligible set is "nodes the generator wrapped in an
  `auditInvocation` call on a dispatch path", not "nodes extending EventLogNode".

- **Project failed to tell me.** Nothing says where the per-node invocation counters produced by
  `PerformanceMonitorAudit` (declared as `perfMon` in `src/main/fluxtion/designer/application-context.xml`)
  can actually be read. This is the only independent cross-check available for "did a node run", so
  the log's blind spots cannot be checked without it.

- **I guessed at an API, and went outside the project to do it.** To find those counters I probed the
  admin HTTP API by hand: `/api/counters` → 404, `/api/processors` → 404, `/api/commands` lists no
  counter command. I then downloaded the served console (`http://127.0.0.1:8181/` and `/app.js` —
  code that is not in this repo) and reverse-engineered that per-node counters are pushed over a
  WebSocket at `ws://127.0.0.1:8181/ws/monitor` inside `throughput.nodes`. I then wrote my own
  raw WebSocket client to capture one frame, because no project script or documented endpoint
  exposes it. None of this is in README, the skills, or `config/server-config.yml`.

- **Project told me something that is not true at runtime.**
  `src/main/java/com/example/myapp/builder/MarketProcessorSupplier.java` sets
  `flow.setAuditLogProcessor(logRecord -> System.out.println(logRecord))` with the comment "Print
  audit records to stdout … an audited graph would otherwise look idle." Under the Mongoose server
  this does not hold: `server.log` shows `updating event log config:EventLogConfig{...}` at boot and
  the record processor is then replaced by
  `ChronicleAuditCaptureService$ProcessorSink`. Result: **not one of the five `PriceEvent` records
  ever reached stdout.** `server.log` contains 6 `eventLogRecord` entries, all boot-time control /
  lifecycle events. I spent a cycle believing no price event had been processed. This is exactly the
  failure mode `run-mongoose-server/SKILL.md` warns about ("looks identical to a node that never
  fired") but attributes to a different cause.

- **Project doc shows a node that does not exist here.** The worked example in
  `read-audit-log/SKILL.md` renders a record containing `priceThresholdAlert: { threshold: 500.0,
  price: 905.6, priceAboveThreshold: true }`. There is no such node in this project — the graph is
  `rootNode` → `riskCheck` only, and neither logs any value beyond the echoed input event. The
  example advertises exactly the evidence this project's log cannot produce.

- **Project failed to tell me.** `config/server-config.yml` declares an event sink `output` writing
  `data/output.txt`, and README states the default wiring is
  "file feed → `MarketProcessor` → a file sink (`data/output.txt`)". After a complete run
  `data/output.txt` is **0 bytes**, and no class under `src/main/java` (outside `generated/`) obtains
  or publishes to a `MessageSink`. Nothing states which node is supposed to feed the sink, or that an
  empty sink is the expected shipped state. Since the audit log records nothing about sinks, I cannot
  tell from the project whether this is the reported defect or the intended blank slate.

- **Something worked and I am not sure why (1).** On the very first run in this directory — no
  `audit/`, no `logs/`, no `data/output.txt` existed — the file feed logged
  `Found previous offset, trying to skip to file offset 0`, i.e. it claimed a persisted offset from a
  prior run that never happened. It then read all 5 rows, so the run was valid. The skill explicitly
  says "Where the offset is stored is not documented in this project", which makes this message
  impossible to verify rather than merely surprising.

- **Something worked and I am not sure why (2).** The generated processor calls
  `auditInvocation(serviceRegistry, "serviceRegistry", "registerService", …)` and
  `auditInvocation(perfMon, "perfMon", "registerService", …)` (lines 329/331) on every
  `registerService` call, yet **all 15 `ExportFunctionAuditEvent` records in the export have an empty
  `nodeLogs`**. So passing a node to `auditInvocation` is *not* sufficient to make it appear. My
  best inference — unverified, and not stated anywhere in the project — is that `EventLogManager`
  only records nodes handed to it via `nodeRegistered`, and `initialiseAuditor` (line 390) registers
  exactly five: `riskCheck`, `rootNode`, `callbackDispatcher`, `subscriptionManager`, `context`.
  `perfMon` and `serviceRegistry` are not among them. If that inference is right it is the real rule
  governing what the log can ever say, and it is documented nowhere.

- **Project failed to tell me.** The `read-audit-log` skill's "with the analyser" route lists
  `analyser_open` / `analyser_context` MCP tools. No such tools were available to me and the project
  gives no way to start the analyser from here (README points at
  `jbang analyser@telaminai/fluxtionauditlog-analyser`, an external fetch). I used the grep route,
  which the skill does provide — that half of the skill worked well.

- **Minor: README's run recipe hangs if followed literally.** README ▸ *Analyse a run* lists
  `./run-server.sh`, `./export-audit.sh`, `./stop-server.sh` as three consecutive commands.
  `run-server.sh` ends in `exec java … -jar` and never returns. Only `run-mongoose-server/SKILL.md`
  says to background it; README does not, and README is the file a newcomer opens first.

## Files I never opened

- `CLAUDE.md` — not read. (I did run `cmp` against `AGENTS.md`: the two are **byte-identical**,
  4640 bytes each. I did not read the contents of either.)
- `AGENTS.md` — not read. Byte-identical to `CLAUDE.md`, as above.
- `.claude/skills/regenerate/SKILL.md` — not read. This was a diagnosis task with no graph change, so
  regeneration never came up.

Read: `README.md`, `.claude/skills/run-mongoose-server/SKILL.md`, `.claude/skills/read-audit-log/SKILL.md`.
