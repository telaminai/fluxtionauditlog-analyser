# Journal — copy this file into your working directory and fill it in as you go

You are building something. Alongside the work, keep this journal. It is not a summary written at the end:
it is a running record, and the timing of each entry is part of what it records.

## Three rules

1. **Append only.** Never edit or delete an entry once written, even when it turns out to be wrong. A wrong
   entry followed by a correction is exactly the data this is for.
2. **Write the entry BEFORE you act**, not after. Before you open a file, before you run a command, before
   you choose between two approaches. If you find yourself writing an entry about something already done,
   mark it `(retro)` and carry on.
3. **`from:` is the important field.** Be honest about it. `prior` is a perfectly good answer and is more
   useful to us than a flattering one.

## Entry format

Four lines. Keep them short — this should cost you seconds, not minutes.

```
## E12 · 2026-09-21T10:44:07Z · RUN
what: ./validate.sh
why:  check the design parses before generating anything
from: runbook:RUNBOOK.md#step-2
```

`kind` is one of:

| kind | when |
|---|---|
| `READ` | opening a file or page to learn something |
| `DECIDE` | choosing between two or more approaches |
| `WRITE` | creating or editing a file |
| `RUN` | executing a command |
| `ERROR` | something failed — put the first line of the message in `what:` |
| `ASK` | you asked the operator anything at all |
| `CHECK` | you verified a result against something independent |

`from:` must be exactly one of these, with the detail filled in:

| `from:` | Use when |
|---|---|
| `runbook:<path>#<section>` | a runbook or bootstrap file pointed you here |
| `readme:<path>` | a README pointed you here |
| `contract:<path>#<section>` | a reference or contract document you looked up |
| `error:<first few words>` | an error message told you what to do |
| `stub:<path>` | generated code showed you the shape to follow |
| `example:<path>` | you copied or adapted an existing file in the project — **name the file** |
| `search:<what you searched for>` | you went looking, grep/find/listing |
| `prior` | you already knew it, or inferred it from general Java/Spring/other-framework knowledge |
| `operator` | you asked the human |

If two things led you there, name the one that actually decided it.

---

# Predictions — write these BEFORE starting task 1, then never change them

Answer in one line each. You will be wrong about some of these; that is fine and expected.

```
P1 how will I run data through the application?
P2 where will the application's output go?
P3 what will tell me whether my change was correct?
P4 what do I expect to be the hardest part?
P5 what do I expect to get wrong first?
```

```
P1 A Maven exec:java / JUnit main class that feeds a hardcoded list of events into an
   EventProcessor via onEvent(...), probably from a main() I write by hand.
P2 stdout — System.out.println from inside the graph nodes plus a summary at the end.
P3 Running it and eyeballing the printed output against hand-computed expected values;
   ideally a JUnit test asserting the aggregates.
P4 Getting the Fluxtion build/codegen set up: which artifact coordinates and which
   version, and whether the processor is generated at build time (APT plugin) or at
   runtime via Fluxtion.interpret/compile. Guessing wrong there costs the most time.
P5 The Maven coordinates/version of Fluxtion, and reaching for an annotation-based
   API (@OnEvent/@EventHandler) whose names have changed in the current release.
```

---

# Entries

<!-- append below this line, never above -->

## E1 · 2026-09-20T20:05Z · READ
what: http://127.0.0.1:5187/ and /start
why:  operator gave a website base URL; only project-supplied source of truth
from: operator

## E2 · 2026-09-20T20:07Z · READ (retro)
what: /start says AOT download needs a key at build time, interpreted needs a key every run
why:  operator said no compilation-service key is provisioned — need to know if that blocks me
from: contract:http://127.0.0.1:5187/start#cloud-service-and-api-keys

## E3 · 2026-09-20T20:08Z · DECIDE (retro)
what: ignore the cloud starter download path, use plain Maven + Maven Central artifacts
why:  no key provisioned; the key story on /start is about the cloud *source generator*,
      the OSS builder on Central should compile in-process locally
from: contract:http://127.0.0.1:5187/start#cloud-service-and-api-keys

## E4 · 2026-09-20T20:10Z · READ (retro)
what: https://telaminai.github.io/fluxtion/home/quickstart/ (linked from /start "developer guide")
why:  need the actual API names and maven coordinates
from: contract:http://127.0.0.1:5187/start

## E5 · 2026-09-20T20:12Z · CHECK (retro)
what: probed repo1.maven.org for com.telamin.fluxtion:fluxtion-builder versions
why:  quickstart pins 1.0.15; verify it exists before writing a pom around it
from: contract:https://telaminai.github.io/fluxtion/home/quickstart/

## E6 · 2026-09-20T20:13Z · ERROR
what: fluxtion-builder 1.0.15 -> HTTP 404 on Maven Central; latest published is 1.0.9
why:  docs are ahead of the published artifacts; must pin 1.0.9 instead of the documented version
from: error:404 fluxtion-builder-1.0.15.pom

## E6b · 2026-09-20T20:14Z · DECIDE
what: build a hello-world spike first, then the real app
why:  docs version is already wrong once; want the API surface confirmed against 1.0.9
      before I write a graph that depends on it
from: error:404 fluxtion-builder-1.0.15.pom

## E7 · 2026-09-20T20:14Z · DECIDE
what: pin com.telamin.fluxtion:fluxtion-builder:1.0.9, target Java 21 not the docs' JAVA 25
why:  1.0.9 is the newest that resolves; environment has Java 21 only
from: error:404 fluxtion-builder-1.0.15.pom

## E8 · 2026-09-20T20:20Z · WRITE (retro)
what: pom.xml + src/main/java/com/example/spike/Spike.java (hello-world from the quickstart)
why:  spike to confirm the API before building the real graph
from: contract:https://telaminai.github.io/fluxtion/home/quickstart/#1-hello-world

## E9 · 2026-09-20T20:22Z · RUN (retro)
what: mvn compile, then java -cp ... com.example.spike.Spike
why:  confirm both compile and run work on 1.0.9 / Java 21
from: contract:https://telaminai.github.io/fluxtion/home/quickstart/#1-hello-world

## E10 · 2026-09-20T20:23Z · ERROR
what: "Fluxtion API key is not configured." / IOException: Remote model generation failed with HTTP 401
why:  compile PASSED (API names right for 1.0.9) but DataFlowBuilder.build() calls
      RemoteHttpModelGenerator — the interpreted path needs the cloud key on every run
from: error:Fluxtion API key is not configured

## E11 · 2026-09-20T20:24Z · DECIDE
what: E3 was WRONG. The key requirement on /start is real and applies to the OSS builder too.
      Next: check whether AOT via dataflow-maven-plugin is also remote, or whether the
      older fully-OSS com.fluxtion:compiler:9.7.12 generates locally with no key.
why:  need a generator that works with no key; those are the only two candidates
from: error:Fluxtion API key is not configured

## E12 · 2026-09-20T20:27Z · CHECK
what: unzip META-INF/services/...SimpleEventProcessorModelGenerator from fluxtion-builder-1.0.9.jar
why:  model generation is ServiceLoader-pluggable; check if a local impl ships alongside the remote one
from: search:Generator classes in the fluxtion jars

## E13 · 2026-09-20T20:28Z · ERROR
what: sole registered generator is RemoteHttpModelGenerator — no local impl in the jar
why:  fluxtion-builder 1.0.9 cannot generate a processor without the cloud key, by design.
      AOT via dataflow-maven-plugin is the same story per /start ("key once at build time").
from: stub:META-INF/services/com.telamin.fluxtion.runtime.meta.model.SimpleEventProcessorModelGenerator

## E14 · 2026-09-20T20:29Z · DECIDE
what: switch to the older fully-open-source line com.fluxtion:compiler:9.7.12 + runtime:9.7.12
why:  it predates the cloud split and does all codegen in-process, so it works with no key.
      Only remaining candidate that can actually run here. Different package root
      (com.fluxtion.*, not com.telamin.fluxtion.*) so the quickstart code needs porting.
from: search:maven central com.fluxtion artifact listing

## E15 · 2026-09-20T20:32Z · RUN (retro)
what: ported Spike.java to Fluxtion.interpret(c -> DataFlow.subscribe...) and ran it
why:  prove 9.7.12 generates in-process with no key before committing to it
from: stub:javap com.fluxtion.compiler.Fluxtion

## E16 · 2026-09-20T20:33Z · CHECK
what: spike printed msg:HELLO / msg:WORLD — no key, no network
why:  first green result; the 9.7.12 path is viable and the whole app can be built on it
from: stub:javap com.fluxtion.compiler.Fluxtion

## E17 · 2026-09-20T20:34Z · DECIDE
what: build "GridWatch" — an electricity-grid substation load monitor
why:  needs multiple event types, per-key aggregation, a config/reference stream and
      stateful alerting, so it exercises DSL + imperative nodes rather than a toy map
from: prior

## E18 · 2026-09-20T20:35Z · READ
what: javap the annotations, GroupByFlowBuilder, Aggregates and sink API in 9.7.12
why:  docs on the website describe the 1.0.x com.telamin API, which does not apply to
      9.7.12 — the jar itself is now my only reliable reference
from: error:Fluxtion API key is not configured

## E19 · 2026-09-20T20:40Z · DECIDE
what: graph shape — RatingBook + DeratingModel as imperative parents of OverloadMonitor,
      then DSL (groupBy / aggregate / sink) hanging off the monitor
why:  wanted the demo to show the thing Fluxtion actually claims: a temperature event
      re-evaluates every feeder without any explicit orchestration code
from: prior

## E20 · 2026-09-20T20:41Z · DECIDE
what: no time-based windows; use running aggregates instead
why:  tumbling/sliding take millis and are wall-clock driven, which makes output
      non-reproducible and untestable. Determinism matters more than showing windowing.
from: stub:javap com.fluxtion.compiler.builder.dataflow.FlowBuilder

## E21 · 2026-09-20T20:42Z · WRITE
what: event records, imperative nodes, graph builder, CSV feed reader, main, JUnit test
why:  building the app now

---

# Close — fill in at the end of each task

```
T<n> outcome: complete | incomplete | abandoned
first checked result at: <entry id>
what I would have wanted and could not find:
what I read that turned out to be irrelevant:
which existing file in the project most influenced what I wrote, and why:
predictions that were wrong, and what was actually true:
```

The last two questions matter most. Be specific and name files.
