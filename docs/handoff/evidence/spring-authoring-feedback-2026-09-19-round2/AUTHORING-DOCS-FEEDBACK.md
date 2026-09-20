# Authoring docs feedback: did they make a new LLM a competent Spring / Fluxtion author?

**Date:** 2026-09-19
**From:** the LLM (Claude Code, MCP client) that worked in `/private/tmp/fluxtion-spring-demo/project`
**For:** the Fluxtion analyser / starter developer
**Companion:** `ANALYSER-FEEDBACK.md` (same directory) covers analyser and starter *defects*. This document is
about the *docs*: what they taught me, what I never read and why, and where I went wrong because of a gap.

I arrived with no project knowledge and close to no Fluxtion-specific training signal. Everything below is
from one session: add a `Trade` event, a position node and a mark-to-market node; run data through; plot it;
then re-host the processor in Mongoose behind a CSV file feed.

## Verdict

**Yes for the graph. No for everything around the graph.**

- Designing, declaring, generating and implementing nodes: the docs were enough. The XML validated first
  time, generation succeeded first time, reconciliation kept my bodies, and the acceptance scenario never
  broke. That is a real result for a niche framework — I would normally hallucinate APIs.
- Hosting, feeding data, replay, and logging-for-analysis: the docs say almost nothing on the re-entry path,
  so I improvised, and what I improvised was the wrong shape (a hand-written `main` with hardcoded events
  and `Thread.sleep`). You had to tell me Mongoose + a CSV file feed existed.

The pattern: **the docs are strong where they are a contract and weak where they are a route.** I followed
every pointer I was given. I was given pointers to two of the five authoring docs.

## What I read, and when

| Doc | Lines | Read before authoring? | How I got there |
|---|---|---|---|
| `RUNBOOK.md` | 17 | Yes | `analyser_context.runbooks` pointer |
| `LOCAL-DEMO.md` | 36 | Yes | `analyser_context.runbooks` pointer |
| `authoring-docs/contract.md` | 434 | Yes | `LOCAL-DEMO.md`: "Read RUNBOOK.md and authoring-docs/contract.md" |
| `authoring-docs/skill.md` | 146 | **No** — read only when writing this | nothing pointed to it |
| `authoring-docs/fluxtion-golden-path.md` | 261 | **No** — first 60 lines after you mentioned templates | nothing pointed to it |
| `authoring-docs/playground-CLAUDE.md` | 414 | **No** — read only when writing this | nothing pointed to it |
| `authoring-docs/example.md` | 287 | **No** — still unread | `skill.md`, which I had not read |
| `README.md` | ~100 | Skimmed late | — |
| `claude.txt` (remote) | ? | **No** | referenced by docs I had not read |

`playground-CLAUDE.md` opens with "Read this once at session start; treat it as load-bearing project
context." It is the most valuable file in the folder and nothing on my path led to it. Its name also reads
as "for the browser playground", which this project is not.

**The hazards live in the files I did not read.** Three of them applied directly to code I wrote:

1. *`final` collection fields need `transient` / `@FluxtionIgnore`* (golden path §3, CLAUDE "Source-gen
   annotation triage"). I wrote three `Map` fields in `PositionNode`. I marked them `transient` on a hunch
   about source generation, not because I had read the rule. Without the hunch: `FLX-1009`, one lost cycle.
2. *A plain field reference IS a trigger* (CLAUDE "Failures that compile, run, and are still wrong").
   `MarkToMarketNode` holds `rootNode` and `positionNode`. I *wanted* both to trigger, so it came out right —
   but I did not know there was a decision to make. A lookup-only reference would have been silently wrong,
   and the doc itself says nothing will ever warn you.
3. *Configure the audit log before `init()`*. I copied the order from `AcceptanceScenario`, so it was right
   by imitation.

Three for three by luck, prior knowledge or copying. The docs get no credit for those, and should.

### Fix: one entry point, in reading order

- Add an `authoring-docs/README.md` (or put it at the top of `RUNBOOK.md`) that lists the files in the order
  to read them, with one line each on *when it applies*:
  1. `playground-CLAUDE.md` — the model and the silent failures. **Always.**
  2. `contract.md` — what XML to emit. **When editing the design.**
  3. `RUNBOOK.md` — the validate / generate / verify loop. **Always.**
  4. `fluxtion-golden-path.md` §3 and §5 — gotcha table. **Before writing node bodies.**
  5. `skill.md` — conversation procedure. **When designing from scratch with a person.**
  6. hosting and data (see below — does not exist yet).
- Rename `playground-CLAUDE.md` to something like `AUTHOR-ORIENTATION.md`, and move its CheerpJ section to the
  end or a separate file. For a local project roughly a fifth of it is browser-runtime detail.
- Have `analyser_context.runbooks` carry a third pointer: `author-orientation`. It is the channel that
  demonstrably works — I read everything it named.
- `LOCAL-DEMO.md` line 7 should name the orientation doc, not only the runbook and contract.

## What worked, specifically

Keep these; they are why the core loop was clean.

- **`contract.md` "Three kinds of relationship".** Stating what `constructor-arg`, `serviceRegistrations` and
  `eventTypes` each *mean*, with the prohibition spelled out, stopped me wiring things the Spring way.
- **The `FluxtionSpringConfig` field table and the extended-declarations table.** I wrote the `Trade` binding
  and the two beans from these alone.
- **"Handler expectations are not routing."** Prevented a wrong mental model before it formed.
- **Structural validation with no classes.** Declaring `com.example.myapp.event.Trade` before it existed, and
  having that be *correct*, is unusual and the contract explains why (`eventTypes` strings vs `eventClasses`).
- **`RUNBOOK.md` step 4 (read the run receipt before trusting diagnostics).** Dense, but it is the reason I
  checked `compilerRan` and hashes instead of assuming a green build. It made me a more honest reporter.
- **Runbook on reconciliation and ownership.** I knew before generating that my bodies would survive and that
  a conflict would change nothing. Both held.
- **Golden path §3 gotcha table.** Symptom → cause → fix is exactly the right format for an LLM. It should be
  on the main path, not a side file.
- **`skill.md` "verify" step 2** ("read the generated dispatch") is the right instruction; I did a weaker
  version of it (read the run log) because I had not read the skill.

## Gaps that sent me the wrong way

### 1. Nothing says how to run data through the graph — so I wrote a `main` (biggest gap)

`skill.md` verify step 4 says "Exercise one event, one declared signal and one exported command." It does not
say *how*. `RUNBOOK.md` ends at `./run.sh`, which sends one hardcoded `PriceUpdate`. The only harness in the
project is `AcceptanceScenario.java`: hardcoded events, `Thread.sleep(25)`, a `PrintWriter` for the audit log.
When asked for a trade scenario I copied it: `TradeScenario.java`, ~100 lines, almost none of it business
logic, inputs buried in Java, wall-clock timing so no two logs match.

The Mongoose + file feed + CSV mapper shape is strictly better for an LLM author — inputs are an inspectable
file, a new case is an appended row, I change no compiled code, and re-running is the determinism test. The
only mention on disk is a parenthetical in `fluxtion-golden-path.md` line 16: "(embedded, connector,
dag-multi-io, aot, audit, service-export, dsl, spring, mongoose)".

**Wanted**

- A `HOSTING-AND-DATA.md` (name is yours) on the re-entry path, covering: plain `main` vs Mongoose and when
  each is right; the file feed + `valueMapper` pattern; "to exercise the graph, append rows to `data/*.csv` —
  do not write a `main`"; where the audit log goes under Mongoose; how to re-run from the start; replay.
- Make the sample's harness the pattern you want copied. An LLM imitates the nearest example far more
  reliably than it follows prose. If `AcceptanceScenario` read a CSV, so would `TradeScenario` have.
- Say, in the runbook, what the host *is*: `context` could report `host: none (plain main)` /
  `host: mongoose, feeds: [data/market.csv]`.

### 2. Templates cannot be used without a browser

Golden path says "**Pick the shape here first**" and points at `starter-templates/index.json`. Each entry is
"a generator *spec*, not code", and the generator is the playground web page. `fluxtion-starter-core.jar`
offers `validate | regenerate | link` — no `generate`.

To get the "Fluxtion Spring in Mongoose" template I had to: find the generator in the `fluxtion-web` source
tree, bundle `src/lib/starter/index.ts` with esbuild through a custom plugin for Vite's `?raw` imports, patch
three `import.meta.glob` calls out of the bundle, and call `generate(spec)` from Node. It produced the 22
files correctly — the generator is fine, it is just unreachable. A client without access to your source tree
could not have done this at all.

**Wanted:** `java -jar fluxtion-starter-core.jar generate --spec <file|url> --out <dir>`, or publish each
template's generated tree (zip or directory) next to its spec. Then "pick the shape first" is actionable by
an agent.

### 3. No path from a bare project to a hosted one

Projects start small, as this one did. Re-hosting meant hand-transplanting from the generated template:
a pom dependency, `MongooseMain`, a `Supplier<DataFlow>`, `config/server-config.yml`, a CSV mapper, a run
script with seven `--add-opens` flags, and re-resolving `.fluxtion/classpath`. It worked (results below), but
each piece was copied by eye from a template for a *different* graph (`MarketProcessor` / `PriceEvent`).

**Wanted:** a `rehost` (or `link --host mongoose`) command, or a short doc section listing exactly those
pieces and which are graph-specific.

### 4. Three things I could only learn from Mongoose source

- **Mongoose replaces the processor's audit listener.** `ServerConfigurator` calls
  `eventProcessor.setAuditLogProcessor(logRecordListener)` on every hosted processor, so a listener set in
  the `Supplier` is silently dropped. My first Mongoose run wrote **1 record** to my audit file and the rest
  to `java.util.logging`. The supported route is `MongooseServer.bootServer(reader, logRecordListener)`.
  **The starter template has this bug**: `MarketProcessorSupplier.get()` calls
  `flow.setAuditLogProcessor(logRecord -> System.out.println(logRecord))`, which is dead code under Mongoose
  (output appears anyway, via the server's JUL default, which hides the problem).
- **One feed per ordering domain.** Trades and prices must share one file feed or their relative order is
  decided by thread timing and the run stops being reproducible. The template shows a single-type mapper
  (`CsvToPriceEvent`), so the question never comes up. For "trusted, replayable" this is the most important
  sentence missing from the docs. I used one file with a leading `TRADE,` / `PRICE,` discriminator.
- **Feed read position.** You described the loop as "reboot the server after a clean". In my runs
  `FileEventSource` with `readStrategy: EARLIEST` re-read the whole file on every boot and I found no
  read-position file beside `data/market.csv`, so I do not know what "clean" needs to remove or where
  position is kept. My `run-server.sh --clean` deletes `data/market.csv.*`, which matched nothing. Whatever
  the real answer is, it should be written down next to the feed config.

### 5. The docs disagree with each other and with the starter's output

An LLM reading all five cannot tell which is current. Each of these cost, or would have cost, a wrong turn.

| Topic | Says one thing | Says another |
|---|---|---|
| Sinks in an imperative AOT node | Golden path §1 table: hand-wired `SinkPublisher` "❌ **fails source-gen**: `constructor should be pre-generated`"; blessed route is `@ServiceRegistered MessageSink` | `contract.md`: "Sinks generate `SinkPublisher<T>` fields". The starter generated `public SinkPublisher<Checked> sinkChecked` in `Child`, and it builds and publishes |
| Base class for an audited node | Starter output and `README.md`: `extends EventLogNode` | Golden path §2: `extends SingleNamedNode` ("gives auditLog + a name"). `skill.md` stub shapes: no base class at all |
| What an event stub looks like | `skill.md`: "a plain record: `record OrderPlaced(String orderId, String symbol, int qty)`", with fields elicited in conversation step 2 | The XML contract has nowhere to put event fields. Generated: `public record Trade() {}`. The original `PriceUpdate` is a `final class implements Event`, not a record |
| Runtime version | `README.md`: "Fluxtion 1.0.16 runtime" | Resolved classpath: `fluxtion-runtime-1.0.15.jar`; template pom: `fluxtion.version` 1.0.15 |
| Keys | `README.md` leads with RapidAPI key setup and `./check-fluxtion-key.sh` as step one | Line 1 of the same file, and `LOCAL-DEMO.md`: local generation needs no key. `.demo-env.sh` sets `-Dfluxtion.apiKey=MISSING_KEY` |

**Wanted:** pick one answer per row and delete the other. For the base class, say which one a *new node in an
existing audited project* should use — that is the case the re-entry docs never cover, and it is the reason
my two new nodes logged no values until I hand-edited them (see `ANALYSER-FEEDBACK.md` #6). For event fields,
either give the contract a way to declare them or have `skill.md` say plainly "fields are yours to add after
generation; the XML cannot carry them."

### 6. Nothing on designing audit output for analysis

`positionNode` logged `symbol` and `position`. Correct, readable, and unplottable per instrument: the analyser
addresses series as `instanceId.key`, so every symbol lands on one line. I changed node code to log
`position_AAPL` / `pnl_AAPL`. That does not scale past a handful of instruments, and nothing warned me at
authoring time that the log I was designing would not answer the obvious question.

**Wanted:** a short "logging for the analyser" section: what makes a key plottable, the per-entity key
convention (or, better, an analyser group-by so it is unnecessary — see the addendum in
`ANALYSER-FEEDBACK.md`), and that values logged are what `series` / `graph` can see.

### 7. Replay is promised in the orientation and absent from the workflow

`playground-CLAUDE.md` lists "Audit + replay as primitives" as one of three reasons to use Fluxtion, and says
"replays reproduce exactly". No local doc says how. The website how-to describes `YamlReplayRecordWriter`,
`YamlReplayRunner` and the injected `Clock`, and says replay is commercial-only. The template spec has
`auditors.replayRecord: false` and a Chronicle `auditCapture` block. None of this is connected to
`RUNBOOK.md`.

Two rules an LLM author needs and does not get: **use the injected `Clock`, never
`System.currentTimeMillis()`, inside a node** (replay depends on it, and it is exactly the rule I would break
without noticing), and **what is and is not available without a licence**, so I do not design around a
feature the project cannot run.

### 8. Smaller items

- `RUNBOOK.md` is accurate but very dense: step 4 is one 120-word paragraph holding six rules. A short list
  would be followed more reliably.
- `skill.md` "Definition of done" stops at emitting XML; "Re-enter a downloaded project" is an appendix. For
  an agent in an existing repo the appendix is the whole job. Consider splitting: *design skill* and
  *re-entry skill*.
- `contract.md` mixes the browser-import story (drag/drop onto `/start`) with the local workflow. A reader in
  a local project has to work out which paragraphs apply.
- The template index `description` for "Fluxtion Spring in Mongoose" does not mention the CSV file feed or
  typed mapper — the two things that make it the right starting point for data-driven work.

## What the Mongoose re-host showed

Done in this session; all files are in the project.

| | |
|---|---|
| Host | `MongooseMain` → `MongooseServer.bootServer(reader, listener)`, Mongoose 1.0.29, resolved offline from `~/.m2` |
| Config | `config/server-config.yml`: one `FileEventSource` feed on `data/market.csv`, `valueMapper` `CsvToMarketEvent`, two sleeping agents. Admin console and perfMon left out |
| Data | `data/market.csv`: 19 lines — 11 `TRADE`, 8 `PRICE`, 3 instruments; the same inputs as `TradeScenario` |
| Run | `./run-server.sh --drain <dir>` boots, waits until the audit log is quiet for 2 s, sends SIGTERM |
| Output | `evidence/mongoose-run1/audit.yaml`, `evidence/mongoose-run2/audit.yaml`: 36 records each (19 business, 11 `ExportFunctionAuditEvent`, 3 `EventLogControlEvent`, 3 `LifecycleEvent`) |

**Results**

- The 19 business records from Mongoose are **identical** to the 19 from `TradeScenario` — the run whose
  positions and PnL were checked against an independent ledger at every step — after normalising time
  fields and thread names. Final `totalPnl` 3160.0 in both. Same graph, different host, same behaviour.
- Two separate boots of the server produced identical logs except for **2 of 36 records**, both
  `EventLogControlEvent`, which differ only by a lambda identity hash in
  `EventLogConfig{… logRecordProcessor=…MongooseMain$$Lambda/0x…@28dada3f …}`. **Wanted:** a stable
  `toString` there, so two runs are byte-comparable once times are normalised. It is the only thing between
  this setup and "diff two audit logs" as a one-line regression test.
- All 19 events dispatched within about 5 ms. `TradeScenario` needed `Thread.sleep(25)` per event just to
  spread the timestamps for a chart — another sign it was the wrong shape.

The `.fluxtion/classpath`, authoring record and `validate` / `generate` loop were unaffected by adding the
host: the scripts still pass, reconciliation reports every class unchanged, and the acceptance scenario still
passes.

## Corrections to things I said earlier in the session

So the record is straight for whoever reads both documents:

- I described the trade scenario as "12 trades and 7 price updates". It is **11 trades and 8 price updates**
  (5 buys, 6 sells). I miscounted my own code; the analyser's `aggregate` and the CSV both say 11 / 8.
- Because of that miscount I told the user the chart's **8 price-update markers looked wrong. They were
  right.** The buy / sell marker counts (11 / 10 shown, 5 / 6 actual) are still unexplained and still worth
  looking at — see the addendum in `ANALYSER-FEEDBACK.md`.
- The provenance string I set on `evidence/trades/audit.yaml` in the analyser carried the wrong counts. The
  Mongoose log was opened with corrected provenance.

This is the failure mode the whole approach is built around: my sentence was wrong, the log was right, and
the tools made it cheap to find out. It would have been cheaper still if a marker count I doubted could be
clicked through to its records.

## If you only do five things

1. One entry-point doc in reading order, and a third `context.runbooks` pointer to the author orientation.
2. `HOSTING-AND-DATA.md`, and make the sample harness a CSV feed so imitation produces the right shape.
3. A headless `generate --spec … --out …`.
4. Resolve the five contradictions in the table above — sinks and node base class first.
5. Fix the dead `setAuditLogProcessor` in the hosted template's supplier, and document
   `bootServer(reader, listener)` and the one-feed ordering rule.
