# AI working agreement — Mongoose-hosted Fluxtion

This repository is a small, local validation project. Its purpose is to prove the complete
developer loop, not merely to make a Java graph compile:

1. design a deterministic Fluxtion process with an LLM;
2. generate, test and package it locally;
3. deploy it in the local Mongoose server;
4. capture enough audit evidence to inspect it in the Fluxtion Audit Log Analyser; and
5. explicitly connect an AI client to that already-open analyser workspace when it is useful.

The LLM helps author and investigate. It is never part of the deployed decision path.

## Onboarding-contract alignment

This local project is a conformance exercise for the analyser's `spec-onboarding-example.md` (M19), not
a competing onboarding route. M19 owns the download → run → analyser journey. This project must prove
its current bundle assumptions or record the gap.

The current starter is not yet M19-conformant: audit capture is disabled, there is no predictable text
audit log under `./logs/`, no `.analyser/project.fluxtion-settings`, no `AGENTS.md`, and no version-pinned
local snapshot of the framework orientation/golden path. These are tracked validation deliverables, not
things an agent may silently assume. The target audit is the analyser-readable INFO text/YAML file at
`./logs/audit-<name>.yaml`; do not substitute Chronicle output while no compatible analyser reader exists.

MCP sharing is optional only after that standard analyser loop works. It supplements the analyser's
Explain/copy-prompt capability and the IDE agent that edits this project; it does not replace either.

## Read before changing anything

Read these project files in this order:

1. `docs/specs/spec-mongoose-analyser-validation.md` — the validation contract, boundaries and
   acceptance evidence.
2. `docs/specs/tracker.md` — the only authoritative ordering and current state of this work.
3. `README.md` — the supported build and local-server entry points.
4. `config/server-config.yml` — deployment topology: feeds, agents, processor, sinks, services,
   performance monitoring and audit capture.
5. `src/main/java/com/example/myapp/builder/MyProcessorBuilder.java` — the one graph recipe used for
   AOT generation and by Mongoose.
6. `src/main/java/com/example/myapp/node/RootNode.java` and `event/PriceUpdate.java` — the current
   domain boundary.
7. `src/test/java/com/example/myapp/MyProcessorGraphTest.java` — the existing minimum executable
   check.

The project will embed a version-pinned authoring snapshot under `docs/ai/` before it is presented as a
starter bundle. Until then, obtain the current Fluxtion authoring material before improvising:

- orientation: <https://fluxtion-playground.dev/CLAUDE.md>;
- AI authoring bootstrap: <https://fluxtion-playground.dev/build-with-ai>;
- Mongoose event-source and value-mapping guide:
  <https://telaminai.github.io/mongoose/overview/event-sources-overview/>.

If a linked resource cannot be read, say so and use the repository's working code and tests as the
source of truth; do not invent an API or configuration property from memory.

For non-Claude agents, `AGENTS.md` is the entry point and directs them to this canonical file.

## The current system, precisely

```text
data/input.txt
  -> FileEventSource named "input" on feeds-agent
  -> broadcast delivery
  -> MyProcessor on processor-agent
  -> RootNode.onPriceUpdate(PriceUpdate)
  -> audit log / named output sink data/output.txt
```

`MongooseMain` boots `config/server-config.yml`. The YAML names `MyProcessorSupplier`, which loads the
AOT-generated `com.example.myapp.generated.MyProcessor`; that generated source is deliberately not
versioned. `MyProcessorBuilder` is therefore the editable graph definition. Mongoose owns the agent
threads, feed/sink lifecycle and local admin console; Fluxtion owns the deterministic processor
dispatch inside the processor agent.

**First fact to validate, not an assumption:** `data/input.txt` contains CSV text while `RootNode`
only handles `PriceUpdate`. `MyProcessorSupplier` prints unmatched events specifically to make that
mismatch visible. Before adding business behaviour, prove what `FileEventSource` publishes and add a
tested value mapper/parser if the feed is delivering raw `String` values. Do not call a file-to-domain
flow working until a test and a local run show `PriceUpdate` reaching the handler.

## Authoring rules

- Start from the smallest vertical slice. State the input event, fields, node responsibilities,
  upstream dependencies, expected sink/audit evidence and acceptance test before writing code.
- Model domain facts as explicit event types. Keep nodes small and local: fields, constructor
  dependencies, event handler/trigger methods and audit fields. Let Fluxtion infer dispatch order and
  change propagation; do not write a second hand-rolled event router, executor or thread model.
- Match the closest working starter/example before introducing a new framework shape. Compiler and
  generated-source errors are instructions: make the named fix, rerun, then reassess.
- Treat time, external I/O, randomness and mutable cross-agent data as design decisions. Make their
  source and ownership explicit; do not hide them in a node. Keep input mapping pure and validate it.
- Add or strengthen a focused JUnit test with every behaviour change. A graph-building test alone is
  not enough once the project has business behaviour: assert the observable outcome/audit/sink value.
- Keep `config/server-config.yml` truthful. A feed's delivery mode, mapped event type, agent and idle
  strategy are part of the design, not deployment decoration.
- Never put API keys, passwords, tokens, audit data containing secrets, or an AI-client configuration
  in this repository. `check-fluxtion-key.sh` reads the key from the user's environment or
  `~/.fluxtion/fluxtion.apiKeyFile`; leave it that way.

## Compile, test and run loop

Use JDK 21+. Generation happens during the Maven build and needs a valid, subscribed Fluxtion API key;
the generated processor is compiled into the runnable JAR.

```bash
./check-fluxtion-key.sh     # preflight before generation
./mvnw test                 # compile/generate and run focused checks
./mvnw package              # create the runnable fat JAR after a successful test run
./run-server.sh             # start Mongoose from the repository root
```

The local admin console is <http://127.0.0.1:8181>. Run one server instance per working directory and
confirm it is stopped before changing the same input/output files or starting another run. `run-server.sh`
only builds when the JAR is absent, so run `./mvnw package` explicitly after source or configuration
changes. Do not hand-edit `target/`, generated processor sources, or `data/output.txt` as a substitute
for a real run.

When a build fails, report the exact command and relevant compiler/generator output, make the smallest
directed correction, then run the loop again. Do not suppress generator errors, weaken tests, or replace
the generated processor with a hand-written dispatcher just to get a green build.

## Investigation and analyser workflow

The intended evidence loop is:

```text
tested domain events -> local Mongoose run -> captured audit/replay artifact
  -> Audit Log Analyser workspace -> optional explicit AI-client MCP connection
```

`performanceMonitoring.auditCapture` is currently `false`. Do not guess its storage settings or turn it
on as an unrelated edit. The target is the M19 text/YAML audit artifact at a predictable
`./logs/audit-<name>.yaml` path. First design a tiny acceptance slice, then consult the installed
Mongoose documentation/version and make one reviewed configuration change that produces that known
local artifact. Record where it lives and add it to the project documentation or test evidence.

The same investigation slice must create `.analyser/project.fluxtion-settings` with bundle-relative
source roots and the generated EventProcessor FQN. Do not claim zero setup until import/auto-detection
and click-to-source work against the produced audit log.

Open that artifact in the Fluxtion Audit Log Analyser for visual inspection, filters, graph/series work
and reports. If sharing the open analyser workspace with Claude/Codex/another MCP client is useful, use
the analyser's **Connect an AI client** screen and its explicit registration/check flow. A successful
bridge check proves only the local bridge; the AI client must still approve/import the registration.
Never assume an agent can see the analyser or start a second analyser instance.

Before an AI-assisted investigation, write down: the input fixture/artifact, question, expected
observation, relevant record/field/node, and what output will count as evidence. Save reports and export
data only to intentional local paths; do not treat the model's prose as audit evidence.

## Reusable local-skill direction

Prefer one user-local `mongoose-local` skill shared by starter projects over copying opaque automation
into every new project. Its stable contract should be:

- discover this repository's `pom.xml`, `run-server.sh`, YAML config, admin URL and output/audit paths;
- preflight the Fluxtion key without exposing it;
- build/test/package, start exactly one local server, report status and stop it cleanly;
- collect the declared audit artifact and hand its path to the analyser workflow;
- fail clearly when a project has not yet declared a mapper, audit destination or analyser-compatible
  artifact.

Keep project-specific decisions versioned here: event schemas, graph nodes, YAML topology, fixtures and
acceptance tests. Add a project script only after the shared skill has a genuinely different per-project
operation; document its inputs, outputs and safe stop behaviour. Do not create a custom Mongoose plugin
or MCP tool merely to automate a one-off command—first prove the workflow with the shared skill/scripts.

## First proposed validation slice

1. Establish a CSV-to-`PriceUpdate` mapping and assert it with a focused test.
2. Make `RootNode` emit a small, deterministic observable result to the named output sink and assert it.
3. Package and run the local Mongoose server; verify the admin console and the expected output/audit.
4. Enable and verify the smallest supported audit capture configuration, then open its artifact in the
   analyser and answer one concrete question from the captured data.
5. Only then connect an AI client to that analyser workspace and repeat the same question through its
   MCP tools, comparing the reported evidence with the visible analyser state.

Pause after each step for review. Record any discovery that changes the shared-skill contract before
automating it for future starter projects.
