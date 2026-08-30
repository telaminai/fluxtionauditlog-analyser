# From playground to analyser in 10 minutes

Download a runnable event-processing system, run it, and watch the analyser explain what it did —
on your machine, with code you can edit.

You need a **JDK 21+** and nothing else installed. The project you download builds and runs with
**no Fluxtion API key**: its event processor is already generated and committed. (Two small
exceptions, stated once rather than discovered later: the Maven wrapper downloads Maven on its
first run, which uses `curl` or `wget`; and *regenerating* the processor after you change the graph
does need a key — [step 5](#5-change-something) says where.)

## 1. Download a bundle

Open the [project starter](https://fluxtion-playground.dev/start), choose the **Audit analyser
bundle** template, and download the zip. You get a full Maven project — not a jar — because the
point is that you can open it, read it and change it.

```bash
unzip audit-analyser-bundle.zip
cd audit-analyser-bundle
```

Worth knowing what is in there, because the rest of this page uses all of it:

| Path | What it is |
|---|---|
| `src/main/fluxtion/designer/application-context.xml` | the **graph definition** — nodes as beans, edges as constructor args |
| `src/main/java/.../generated/` | the **generated event processor** and its GraphML — committed, which is why no key is needed |
| `config/server-config.yml` | the Mongoose deployment descriptor: feed → processor → sink, plus audit capture |
| `.analyser/project.fluxtion-settings` | the **analyser project profile** — source roots, processor, runbooks |
| `.claude/skills/` | agent skills describing this project's own commands |
| `CLAUDE.md` / `AGENTS.md` | what an LLM opened in this directory needs to know |

## 2. Run it

```bash
./run-server.sh
```

The server boots, reads `data/input.txt` through a file feed, and records every event cycle. While
it is up it publishes a small registry entry at `~/.mongoose/servers/audit-analyser-bundle`
describing where it is listening — that is how the next command finds it, with nothing to configure.

## 3. Export the run

Mongoose captures to a Chronicle store, which is fast but not something the analyser reads directly.
One command turns the captured run into an analyser-readable log:

```bash
./export-audit.sh          # writes logs/audit-audit-analyser-bundle.yaml
```

This is a separate step on purpose, and the page says so rather than pretending the run writes the
file itself: the capture format and the analysis format are not the same thing.

## 4. Open it in the analyser

```bash
jbang analyser@telaminai/fluxtionauditlog-analyser
```

(That needs [JBang](https://www.jbang.dev/); the [install page](install.md) has the plain
`java -jar` alternative if you would rather not add another tool.)

Then, in the analyser:

1. **File ▸ Open project…** and choose the bundle's folder. The profile it ships means the source
   roots, the event processor and the skills are already configured — there is nothing to fill in.
2. **File ▸ Open log…** and choose `logs/audit-audit-analyser-bundle.yaml`, alongside the GraphML
   under `src/main/resources/`.

Open the project *before* the log. A project switch replaces your source roots and graphs, so the
analyser treats it as a session boundary and deliberately ignores a log passed in the same breath.

![The bundle's project profile in force: the analyser has the source roots, the event processor and
both shipped skills without anything being typed in, and states that no Fluxtion key file is present
— which the bundle does not need in order to run.](assets/tutorial-project-open.png)

With the log and its GraphML open, the analyser reports what it has: 23 records, the event types it
found, and — bottom right — whether the graph you gave it actually fits this log.

![The exported log open with its GraphML: 23 records, the event types listed with their counts, and
the topology showing PriceEvent reaching RootNode and RiskCheck. The status bar reads "fits this log
(2/2)".](assets/tutorial-log-open.png)

Now the things worth doing:

- **Read a cycle.** Each record shows which nodes ran, in dispatch order, and what each logged —
  not a sample or a trace, the actual order the compiled processor dispatched.
- **Click a node line.** The bundled source opens, because the profile already named the source root.
- **Check the pairing.** The Project panel states whether the graph you opened actually describes
  this log. If it does not, a node's absence proves nothing — and the analyser says so rather than
  letting you conclude it.
- **Graph a value** over time from the records.

Selecting one `PriceEvent` cycle shows what that event actually did — which nodes ran, in dispatch
order, and what each of them logged:

![One PriceEvent cycle: the record detail lists rootNode.onPriceEvent receiving
PriceEvent{symbol=AAPL, price=195.3, volume=1200} and then riskCheck.onRiskCheck, while the topology
numbers the two nodes that ran.](assets/tutorial-cycle.png)

Clicking a node line opens the bundled source beside the graph — the project profile already named
the source root, so there is nothing to configure first:

![Source navigation: RootNode selected in the topology, with its source and the generated
MarketProcessor shown alongside.](assets/tutorial-source.png)

## 5. Change something

This is the part that makes it yours. Open the project in your IDE and edit the graph — either the
Java node classes, or `application-context.xml` to change the shape of the graph itself.

Then regenerate and re-run:

```bash
./mvnw -Pgenerate-fluxtion package    # regenerating needs a Fluxtion API key
./run-server.sh
./export-audit.sh
```

**This is the one step that needs a key.** Running the bundle as shipped does not; regenerating
calls the hosted compiler. If the build stops at `process-classes`, that is what happened — the
project is not broken. Put the key in `~/.fluxtion/fluxtion.apiKeyFile` as `apiKey=…`; the compiler
reads that file, or a `-Dfluxtion.apiKey=…` passed to the build. It does **not** read
`FLUXTION_API_KEY`, so exporting that variable alone will not work.

Open the new log next to the old one and the change you made is visible as a difference in what ran.

## Stop cleanly

```bash
./stop-server.sh
```

This checks that the process it is about to signal really is this project's server before signalling
it, then waits until the capture is closed and the registry entry has gone — so a clean stop is
something you observe, not something the script asserts.

## Working with an AI

The bundle ships `CLAUDE.md`, an `AGENTS.md` mirror, and skills under `.claude/skills/` describing
its own run, export and stop commands. An agent opened in this directory has them without being told.

To let that agent query the analyser directly, start the analyser and use
**AI ▸ Connect an AI client…**, which shows the exact configuration for your client — see
[connecting an LLM](connect-an-llm.md). The division of labour worth keeping: the analyser reads and
explains, it never edits your code.

## Do this on your own system

The bundle is a demonstration; the point is your processor. Two steps get you there:

- **[Produce a log](producing-a-log.md)** from your own processor — the audit configuration is a few
  lines.
- **[Open a project](user-guide/projects.md)** for your repository. `File ▸ New project…` offers what
  it finds — source roots, skills, a GraphML beside your log — and adds only what you confirm.

Everything on this page works the same way against your own system. The bundle exists so you can see
it work once before you point it at something that matters.
