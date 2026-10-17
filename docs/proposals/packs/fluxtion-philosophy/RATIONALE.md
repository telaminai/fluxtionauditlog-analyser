# Rationale — what each entry teaches, and where the docs say it

Sources, read 2026-10-17 (CLAUDE.md rule 6):

- **CT** — `claude.txt`, the Fluxtion framework reference (fluxtion repo, `docs/claude.txt`, `main`). Sections are cited by heading.
- **GP** — the golden path (fluxtion-playground.dev, `fluxtion-golden-path.md`). Sections are cited by number.
- **FX** — the Fluxtion repo's own docs pages (`telaminai/fluxtion`, `docs/…`, `main`), cited by path:
  `how-to/audit-logging-dataflow.md`, `how-to/unit-testing-dataflow.md`, `how-to/replay-functionality.md`,
  `reference/sep-lifecycle.md`, `reference/flxa-format.md`, `reference/performance.md`,
  `reference/performance-profiles.md`, `integration/dataflow-connector.md`,
  `sample-apps/embedding-dataflow-file-feed-sink-demo.md`, `home/fluxtion-with-mongoose.md`,
  `getting-started/run-fluxtion-in-mongoose.md`, `runtime/run-in-event-cycle.md` (by CT's pointer), and the public
  `fluxtion-builder-api` source `FluxtionCompilerConfig.java` (its `targetId` javadoc).
- **MG** — the Mongoose docs (`telaminai/mongoose`, `docs/…`, `main`), cited by path:
  `overview/configuration-and-composition.md`, `overview/plugin_extension_architecture.md`,
  `architecture/event-flow.md`, `architecture/deployment.md`, `architecture/threading-model.md`,
  `architecture/server-control.md`, `example/plugin/writing-a-service-plugin.md`,
  `example/how-to/how-to-using-the-scheduler-service.md`, `example/how-to/how-to-performance-monitoring.md`,
  `example/how-to/how-to-record-and-replay-a-processor.md`.
- **SITE** — this repo's docs site, `docs/site/`: `log-format.md`, `format-spec.md` (the audit record format
  specification), `producing-a-log.md`, `audit-cost.md`, `evidence-bundles/*.md`, `user-guide/records-and-filtering.md`,
  `user-guide/reports.md`, `user-guide/assistant.md`; and `tools/bench/latency-kit/cpp/README.md` for C++.
- **PL** — the reference plugin repository `telaminai/mongoose-plugins` at its `main` commit
  `ace31f301bfb8b2e035c035a25b8856f9111c873` (2026-09-24, the commit after its `v1.0.45` release): `README.md`,
  `docs/architecture.md`, the parent `pom.xml`, `connector/connector-file` (`README.md`, `FileEventSource.java`,
  `FileMessageSink.java`), `service/svc-cache` (`README.md`, `Cache.java`, `JsonFileCache.java`),
  `service/svc-admin-web` (`WebAdminService.java`, `AuditRecordProjection.java`), and
  `test-support/mongoose-test-support` (`README.md`). It has no scheduler module; the scheduler is Mongoose's own
  service (MG `example/how-to/how-to-using-the-scheduler-service.md`), so the tour uses `connector-file`.
- **MGS** — Mongoose source (`telaminai/mongoose`, `main` at `6e8c7ce784fe296faf266ea98dfa8a38d04643e7`), read
  only where its docs are silent: `config/AuditCaptureConfig.java` (the `performanceMonitoring.auditCapture`
  settings, in releases since `v1.0.16`) and `internal/ChronicleAuditCaptureService.java` (its class javadoc).
- **OB** — this repo's `docs/ONBOARDING.md` ▸ *Understand Fluxtion's execution model*, which records what the
  analyser learned from the framework's author and the M21 work. Cited only where it sharpens how a prompt is
  phrased, never as the source of a concept.

"Check, don't assert" marks an entry whose answer the sources do not state outright, so the prompt makes the
assistant check the records and generated code and say when it cannot tell.

Entry 1 of every pack is the same **PLACEHOLDER** teaching example (owner `example`, all-zero commit). It teaches
nothing by itself; its shape (builder, written source and GraphML, audit on) follows GP §2 and CT *Build-time
generation — `FluxtionGraphBuilder`*. See README ▸ *The teaching example each pack expects*.

## 01-basic.md — What is Fluxtion?

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | What is Fluxtion, in plain words? | Events, `@OnEventHandler` handlers that receive them, `@OnTrigger` downstream reactions | CT *The mental model in 60 seconds*; CT *The authoring surfaces* (Imperative); CT *House style* |
| 3 | Follow one event through | Events flow in a compile-time-determined dispatch order; a silent node proves nothing | CT *The mental model in 60 seconds* ("flat dispatch tree", "compile-time-determined dispatch order"); CT *You author at the logical level* ("A flat dispatch tree in topological order"). Silence: OB item 1 — check, don't assert |
| 4 | Show me in the records | Audit records are derived from the topology; `auditLog.info(k, v)` is the business-readable part the author writes | CT *You author at the logical level* ("Per-event audit records derived from the topology, not hand-written"); GP §2 (`auditLog.info`, `cfg.addEventAudit(LogLevel.INFO)`) |
| 5 | What I wrote versus what Fluxtion generated | The author writes specification; the compiler emits the processor and fixes the order | CT *You author at the logical level*; CT *Build-time generation*; GP §2 (`setWriteSourceToFile`) |
| 6 | Who calls my methods? | The generated processor dispatches; there is no scheduler | CT *The mental model in 60 seconds* ("no scheduler, no thread pool, no async runtime"); CT *Anti-patterns* (don't bypass `@OnTrigger` with direct calls) |
| 7 | Fluxtion as a DI container | Wiring by constructor reference; roots registered, the rest discovered; source-gen reconstructs fields by constructor calls and field assignments; setter wiring of non-`final` fields; services injected with `@ServiceRegistered` | CT *The mental model in 60 seconds* ("The graph IS the constructor wiring"); CT *Required rules* 3; GP §2 (register only the roots); GP §4b (setter-wiring); CT *Services — typed integration boundaries*. The docs never use the phrase "DI container", so the prompt asks whether the comparison is fair and where it breaks down |
| 8 | How would my own code look? | Plain Java classes, state in fields, upstreams in the constructor; side effects at terminal nodes | CT *House style*; CT *Why this framework fits AI authoring* (local reasoning per node) |

## 02-intermediate.md — Local knowledge, global behaviour

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | A node only knows its parents | Local declaration, global order derived by the compiler (topo-sort) | CT *The mental model in 60 seconds* ("Reason locally per node. Let the framework handle the global topology"); CT *Why this framework fits AI authoring* ("The compiler does global reasoning") |
| 3 | The diamond: how often does D run? | Topological dispatch over a shared descendant | CT *The mental model in 60 seconds* (topo-sort, flat dispatch tree). **Check, don't assert**: neither source states how many times a node with two changed parents runs in one cycle |
| 4 | When only one side changes | Propagation follows the dirty flag of whichever parent changed. The prompt names neither branch, because which one can stay unchanged depends on the node bodies: in the built diamond (`demos/fluxtion-diamond`, observed 2026-10-17), C = 3x changes whenever x does, so no cycle has B changed and C unchanged. On x = 3, C changed and B (floor(x/2)) stayed 1, and D still ran, reading B's kept value | CT *Conditional dispatch — `@OnTrigger(dirty)`*; GP §3 (`@OnParentUpdate` to know *which* parent changed). **Check, don't assert**: the sources do not state what D reads from an unchanged parent or how a mixed diamond is guarded; OB item 2 (a child running proves its parent ran, not which way it answered) |
| 5 | The very first event | State lives in user fields; `init()` resets only per-event machinery | CT *Required rules* 2; GP §5 rule 2. **Check, don't assert**: neither source describes first-event propagation |
| 6 | The true or false a node returns | The returned boolean is the dirty / propagation control; `dirty=false` fires on the inverse | CT *Conditional dispatch — `@OnTrigger(dirty)`*; OB item 2 |
| 7 | Guards and change detection | The returned boolean, `@OnTrigger(dirty=false)`, `@OnParentUpdate`, `@NoTriggerReference`; a node needs a trigger annotation to propagate; one `@OnTrigger` per node | CT *Conditional dispatch*; CT *Bridging DSL flows into imperative `@OnTrigger` nodes*; GP §3 (the `@NoTriggerReference` and multiple-`@OnTrigger` rows) |
| 8 | Compared with hand-written callbacks | Direct calls between nodes reintroduce ordering bugs; compiler-determined order does not | CT *Anti-patterns* ("direct calls reintroduce ordering bugs"); CT *You author at the logical level* (bypassing `@OnTrigger` loses dispatch ordering). The callback half is general knowledge; the prompt keeps the Fluxtion half to the docs and records |
| 9 | Compared with an event bus or a scheduler | No scheduler, no thread pool, single-threaded deterministic dispatch | CT *The mental model in 60 seconds*; CT *Required rules* 4; CT *Anti-patterns* (RxJava-style habits) |

## 03-advanced.md — Thinking in Fluxtion

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | The closed-world build step | The compiler statically analyses the graph from registered roots and constructor references; compile-time reachability | CT *The mental model in 60 seconds* ("statically analyses the dependency graph"); CT *You author at the logical level* ("Compile-time reachability that proves which nodes a given event can reach"); GP §2 (roots only, upstream discovered). "Closed world" is this pack's term, not the docs' |
| 3 | Source generation is serialisation | Fields reconstructed by generated code; non-renderable fields need `@FluxtionIgnore`/`transient`; the bystander instance | CT *Required rules* 3; CT *Source-gen annotation triage*; CT *Compile shapes* (bystander); GP §3 (assertions read 0/null; finality rule) |
| 4 | Hidden shared state | State changed outside events breaks audit/replay | CT *Required rules* 5; CT *Anti-patterns* ("Don't mutate state from outside the graph"). **Check, don't assert**: the docs do not discuss static shared state specifically, so the prompt asks which parts are stated and which inferred |
| 5 | Undeclared dependencies | The graph is the constructor wiring; a direct call bypasses dispatch ordering | CT *The mental model in 60 seconds*; CT *You author at the logical level* (bypassing `@OnTrigger`); CT *Anti-patterns*. **Check, don't assert** for the static-lookup case |
| 6 | Side effects and the outside world | Side effects at terminal nodes; no threads; mutation through events or `runInEventCycle`; named sinks | CT *House style*; CT *Required rules* 4 and 5; CT *Emitting output — named sinks* |
| 7 | Determinism and replay | Same input, byte-identical output; replay reproduces exactly; clock fields aside | CT *The mental model in 60 seconds* (properties 1 and 2); CT *You author at the logical level* (byte-equal replay); CT *Browser-runtime constraint* ("byte-identical audit records (modulo clock fields)"; cross-event state needs `reset()`) |
| 8 | Composing components I didn't write | The compiler infers the graph from constructor references and emits topologically ordered dispatch; any Java method can be an operator; services are bound by interface | CT *The mental model in 60 seconds*; CT *The authoring surfaces* (DSL: "every Java method ever written is a candidate operator"); CT *Why this framework fits AI authoring* (open-set operators); CT *Services*. Deliberately kept to what US 11,074,079 B2 publishes and nothing more |
| 9 | Where the model stops | The compiler is the safety boundary; what it guarantees and what it cannot | CT *You author at the logical level* ("The compiler is the safety boundary; you operate inside it"); CT *Required rules*; CT *Anti-patterns* |

## Where the sources were unclear

- **What the audit log records.** CT *The mental model in 60 seconds* says "Every node activation is captured";
  OB item 1 says a node appears in a record only if it writes audit output at the level in force. FX
  `how-to/audit-logging-dataflow.md` ▸ *Logging vs tracing* reconciles them: at INFO/DEBUG/WARN/ERROR only nodes that
  logged appear; at TRACE every node visited does. SITE `format-spec.md` §8 adds that a record cannot itself say
  which regime it was written under (the traced-regime marker is an open upstream ask). The prompts follow OB and §8
  (silence proves nothing unless the record is traced) and ask the assistant to say when it cannot tell.
- **The diamond and mixed propagation.** Neither source states how often a node with two changed parents runs in
  one cycle, nor what it observes from an unchanged parent. Entries 02/3 and 02/4 are phrased to be checked.
- **First-event behaviour** is not described in either source. Entry 02/5 is phrased to be checked.
- **"Guards".** Neither source uses the word; entry 02/7 names the mechanisms they do describe.
- **"DI container" and "closed world"** are this pack's framings. The docs support the substance (constructor
  wiring, setter wiring, service injection; static analysis from roots) but not the labels, so the prompts ask
  whether the framing is fair rather than asserting it.

## 04-audit-and-evidence.md — Audit logs and evidence

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | What does a record show? | One record per event cycle: event, times, thread, ordered `nodeLogs` under each node's `instanceId`; the node writes key/values, the framework the rest | SITE `log-format.md` ▸ *Field reference*; SITE `producing-a-log.md` ▸ *How a node appears in the log*; FX `how-to/audit-logging-dataflow.md` ▸ *How it works* |
| 3 | Audit levels: INFO versus TRACE | INFO/DEBUG/WARN/ERROR record nodes that logged; TRACE records every node visited | FX `how-to/audit-logging-dataflow.md` ▸ *Logging vs tracing*, *Enabling and configuring logging*; SITE `producing-a-log.md` ▸ *Turning it on*. **Check, don't assert**: whether the level can be read off a record — SITE `format-spec.md` §8 (inferred only from `method` keys on every entry). Under the pinned toolchain, `addEventAudit(LogLevel.INFO)` generates **traced** records (README ▸ *Toolchain observations*), so "INFO records only nodes that logged" does not hold for a build-time level |
| 4 | What silence means | Absence means "did not run" or "ran and logged nothing", and the record alone cannot say which unless traced | SITE `format-spec.md` §8; OB item 1 |
| 5 | Is the order in a record meaningful? | Fluxtion's order is compiler-derived dispatch order (`TOTAL`); another engine's adapter may declare `PARTIAL` | SITE `format-spec.md` §4; FX `how-to/audit-logging-dataflow.md` ("records are emitted in topological order") |
| 6 | Flags, reports and walks | Flags bookmark records with a note; a report is ordered references plus labelled narrative; a walk is saved spotlight steps; flags do not travel in a bundle | SITE `user-guide/records-and-filtering.md` ▸ *Flagging & focus*; SITE `user-guide/reports.md`; SITE `user-guide/assistant.md` ▸ *Save it as a walk*; SITE `evidence-bundles/index.md` ▸ *What does not travel, and why* |
| 7 | Packing an evidence bundle | Capture is one operation on the running analyser; what it keeps, what it leaves out, when it refuses | SITE `evidence-bundles/sending.md` ▸ *Ask for it*, *What the analyser does, and when it refuses*; SITE `evidence-bundles/index.md`. The prompt asks the assistant to explain and to ask before writing |
| 8 | Checking a bundle I was sent | `--verify` / `--unpack`; verification detects change but does not authenticate the sender; compare the identity line; no replay by the analyser | SITE `evidence-bundles/index.md` ▸ *Two limits, stated everywhere*; SITE `evidence-bundles/opening.md`; SITE `evidence-bundles/reference.md` ▸ *Reading: three commands* |
| 9 | Trust the evidence, not the author | A report includes findings and never authors them; narrative is labelled "the author's account, not log evidence" | SITE `user-guide/reports.md` ▸ *References, never a document*. The phrase is the owner's principle; the mechanism is the docs' |

## 05-deploy-mongoose.md — Running it in Mongoose

The pack never asks the analyser to deploy or control anything (README ▸ *Intent and audience*; tracker ▸ Decisions:
server verbs never appear on the analyser's action socket).
It also never teaches the *Deployment Guide*'s clustering, state replication or distributed tracing sections, which the
owner says are aspirational (README ▸ *Resolved*); MG `architecture/deployment.md` is cited only for *Logging*.

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | Fluxtion and Mongoose: who does what? | Fluxtion owns execution semantics; Mongoose owns feeds, threads, lifecycle, scheduling, sinks | FX `home/fluxtion-with-mongoose.md` ▸ *How the responsibilities split*; FX `getting-started/run-fluxtion-in-mongoose.md` |
| 3 | How the configuration builds my app | YAML or the builder declares feeds, processors, sinks, threads; boot binds them | MG `overview/configuration-and-composition.md` ▸ *How the config composes your app*, *Equivalent YAML sketch* |
| 4 | How events reach my processor | Source → `EventFlowManager` → queue → `EventQueueToEventProcessor` → processor on its agent thread | MG `architecture/event-flow.md` ▸ *Event Flow Process*; MG `overview/configuration-and-composition.md` ("Always run on a processor agent thread"); CT *Required rules* 4 |
| 5 | Lifecycle inside a server | Server lifecycle init → start → startComplete; Fluxtion's `@Initialise` / `@Start` / `@Stop` | MG `overview/configuration-and-composition.md` (step 6); FX `reference/sep-lifecycle.md`. **Check, don't assert**: no page states how the two line up exactly |
| 6 | Sinks and services in a server | `@ServiceRegistered` injection; the node depends on interfaces; the same registration wires a test or a server | MG `overview/plugin_extension_architecture.md` ▸ *Keeping business logic separate from infrastructure*; CT *Emitting output — named sinks* ("the node code never changes") |
| 7 | Getting its audit log into this analyser | Level (`logLevel` per handler or group, `INFO` by default) and the file sink are server configuration; a `LogRecordListener` given to `bootServer` that writes `---`, or audit capture exported by `svc-admin-web`; the `---` separator rule | SITE `mongoose-audit-to-file.md`; SITE `producing-a-log.md` ▸ *Turning it on*; MG `overview/configuration-and-composition.md` (`logLevel` on a handler); MG `architecture/deployment.md` ▸ *Logging*; MGS `AuditCaptureConfig.java`. The page's *To confirm* steps are README ▸ *Needs owner input* item 6 |
| 8 | Watching a running server | Admin commands, performance counters, audit log; the analyser reads the log only | MG `architecture/server-control.md`; MG `example/how-to/how-to-performance-monitoring.md`; SITE `producing-a-log.md` |

## 06-deploy-embedded.md — Embedding the processor

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | Embedded, with no server | Fluxtion does not require Mongoose; embedding trades the managed runtime for your own wiring | FX `home/fluxtion-with-mongoose.md` ("Fluxtion does not require Mongoose"); FX `sample-apps/embedding-dataflow-file-feed-sink-demo.md` |
| 3 | Creating the generated processor | Load by name with `Class.forName`, `init()`, `registerService`, `onEvent` | CT *Build-time generation*; GP §2 (`Main.java`) |
| 4 | Wiring the event input | Call `onEvent` yourself, or `DataFlowConnector` polling feeds on one runner thread; sinks touching other threads must be thread-safe | FX `integration/dataflow-connector.md` ▸ *What is DataFlowConnector?*, *Threading model*; FX `how-to/unit-testing-dataflow.md` ▸ *Single-Threaded Execution* |
| 5 | The lifecycle, in order | BUILD → CONSTRUCT → INIT → START → EVENTS → TEARDOWN; `@ServiceRegistered` may fire after `@Initialise`; the build-time instance is not the runtime one | FX `reference/sep-lifecycle.md` ▸ *The phases at a glance*, *Common gotchas* 1 and 4; CT *Compile shapes* |
| 6 | Reusing one processor | `init()` resets only per-event machinery; clear state with `@Initialise` or `reset()` | CT *Required rules* 2; GP §5 rule 2 |
| 7 | Testing it | Fresh instance per test, collecting sink, mocked services | GP §2 (*Test*); CT *Build-time generation* (*Tests*); FX `how-to/unit-testing-dataflow.md` |
| 8 | Audit output from an embedded app | `addEventAudit`, `setAuditLogLevel`, `setAuditLogProcessor`, the `---` separator, level before sink | SITE `producing-a-log.md` ▸ *The shortest complete example*; GP §3 (the `setAuditLogProcessor` row); FX `how-to/audit-logging-dataflow.md` |
| 9 | Changing a node from outside | Mutate through events; `runInEventCycle` for host actions | CT *Required rules* 5 |

## 07-cpp-preview.md — C++ generation (preview)

Every entry treats the target as a preview and asks for the supported subset rather than assuming Java parity
(README ▸ *Needs owner input*, items 2 and 3).

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | What is the C++ path? | The Java generator emits C++ for a graph when the target is `cpp`; a header-only C++ runtime | FX `FluxtionCompilerConfig.java` (`targetId`, `sourceGeneratorId`); SITE `tools/bench/latency-kit/cpp/README.md` |
| 3 | What is supported today? | Not stated publicly | **Gap** — asked, never asserted |
| 4 | What I write and what is generated | In the public bench, each `.cpp` supplies node bodies and the driver; the processor header is generated | SITE `tools/bench/latency-kit/cpp/README.md` ▸ *What is here*. **Check, don't assert** beyond that bench |
| 5 | Same graph, same behaviour? | The C++ audit writer is held to Java by parity tests, not by byte-equality against the corpus | FX `reference/flxa-format.md` (footnote 1, *Non-JVM readers*) |
| 6 | Audit logs from C++ | The C++ runtime writes FLXA; no runtime record swap or live setter, settings fixed at generation | FX `reference/flxa-format.md` (introduction); FX `reference/performance-profiles.md` (the *Java runtime* behaviour paragraph); SITE `audit-cost.md` |
| 7 | Before I depend on it | Items deferred to "the C++ round"; generator availability unstated | FX `reference/flxa-format.md` (footnote 2); FX `reference/performance.md` (the C++ control not rebuilt). **Gap** |

## 08-mongoose-plugins.md — Mongoose plugins

Entry 1 is a real pin (PL), not a placeholder. Nothing in the pack touches the repository's loader or tooling modules.

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | What is a plugin? | Connectors (an `EventSource` or `MessageSink`) on the dispatcher's input and output sides; services injected through `@ServiceRegistered`; libraries used by both. A node is business logic inside the graph; a plugin is infrastructure the server wires at boot | PL `docs/architecture.md` ▸ *Where plugins fit*; PL `README.md` ▸ *Plugin catalogue*; MG `overview/plugin_extension_architecture.md` ▸ *Types of plugins* |
| 3 | A tour of the file connector | `FileEventSource` extends `AbstractAgentHostedEventSourceService` and tails a line file, with five read strategies (`COMMITED` keeps a `<filename>.readPointer`); `FileMessageSink` extends `AbstractMessageSink` and appends a line per message, with optional size or interval rotation and a backup cap | PL `FileEventSource.java` (`start`, `doWork`, `readStrategy`); PL `FileMessageSink.java` (class javadoc, `rotateOnSizeBytes`, `rotateOnIntervalMillis`, `maxBackupFiles`); PL `connector/connector-file/README.md` ▸ *Read strategies*. The README's source links use an older package path, hence "the code rather than the README" |
| 4 | How a service reaches my processor | `@ServiceRegistered` matches by parameter type, so a service registered under its concrete class cannot be injected as its interface; `serviceClass` chooses the registered type. `JsonFileCache` itself takes an `AdminCommandRegistry` the same way | PL `docs/architecture.md` ▸ *Service registration and the `serviceClass` field*; PL `svc-cache/README.md` ▸ *Usage — Java config*; PL `JsonFileCache.java` (`@ServiceRegistered register`, `setEventFlowManager`) |
| 5 | Threads: plugin versus processor | Each plugin runs on its own agent thread; services are invoked from the dispatch thread. `JsonFileCache.put` writes the file on the caller's thread unless `asyncWrite`, and its `doWork` also writes on the agent thread | PL `docs/architecture.md` ▸ *Threading model*; PL `JsonFileCache.java` (`put`, `doWork`, `asyncWrite`); MG `architecture/threading-model.md` ▸ *Where does my code run?*, *Moving work between threads safely*; CT *Required rules* 4. **Check, don't assert** that the two writers cannot overlap |
| 6 | Which server does this target? | The parent `pom.xml` builds `1.0.46-SNAPSHOT` against Mongoose `1.0.29` and the Fluxtion BOM `1.0.67`; the root README still says `0.2.8-SNAPSHOT`, Mongoose `1.0.8`, Fluxtion `0.9.33`; Mongoose's newest tag on 2026-10-17 is `v1.0.32` | PL parent `pom.xml` (`mongoose.version`, `fluxtion.bom.version`); PL `README.md` ▸ *Versioning*; Mongoose tags. **Check, don't assert** which pairing is current |
| 7 | Adding a plugin to my server | Add the module as a dependency and declare it under `eventFeeds`, `eventSinks` or `services` (YAML) or the `MongooseServerConfig` builder (Java) | PL `README.md` ▸ *Common usage shape*; PL each module's README ▸ *Usage*; MG `overview/configuration-and-composition.md` |
| 8 | Getting audit records out | With `performanceMonitoring.auditCapture.enabled`, Mongoose records a processor's audit records to a Chronicle queue, one YAML document per excerpt; `svc-admin-web`'s `/api/audit/file/{id}/export` writes them as `---`-separated YAML, "drop-in for the analyser reader", or JSONL | PL `WebAdminService.java` (`handleAuditExport`, the audit routes); PL `AuditRecordProjection.java` (class javadoc); MGS `AuditCaptureConfig.java`, `ChronicleAuditCaptureService.java`; SITE `format-spec.md` §1, §2, `mongoose-audit-to-file.md`. **Check, don't assert** that the analyser reads an export cleanly |
| 9 | Writing a plugin of my own | Pick the kind; extend `AbstractAgentHostedEventSourceService` or `AbstractMessageSink`, or implement a service for `@ServiceRegistered`; validate in `init()`/`start()`, idempotent `tearDown()`; test against a real server with `MongooseTestHarness` and in-memory feeds and sinks | PL `docs/architecture.md` ▸ *Lifecycle contract*, *Plugin authoring rules*; PL `test-support/mongoose-test-support/README.md`; MG `example/plugin/writing-a-service-plugin.md` |
