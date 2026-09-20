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
P1 how will I run data through the application? A main() that feeds hardcoded/synthetic events into an EventProcessor via onEvent(), run with mvn exec or java -cp.
P2 where will the application's output go? stdout via System.out/println in the node classes, seen in the terminal.
P3 what will tell me whether my change was correct? The printed output matching hand-computed expected aggregates, plus it compiling and running without exceptions.
P4 what do I expect to be the hardest part? Getting the Fluxtion Maven coordinates/version and the AOT-vs-interpreted graph build API right without a key for the compile service.
P5 what do I expect to get wrong first? The annotation set (@OnEventHandler vs @OnTrigger) and how nodes are registered/wired into the graph builder.
```

---

# Entries

<!-- append below this line, never above -->

## E1 · 2026-09-20 · READ
what: http://127.0.0.1:5187/ and /start
why:  find the supported way to bootstrap a Fluxtion project from nothing
from: operator

## E2 · 2026-09-20 · DECIDE
what: no API key provisioned, and /start says interpreted downloads need a key every run — so look for keyless public Maven artifacts instead
why:  need something that runs on the local JVM, not only in the browser playground
from: contract:http://127.0.0.1:5187/start#cloud-service-api-keys

## E3 · 2026-09-20 · READ
what: telaminai.github.io/fluxtion quickstart-1-minute, quickstart (interpreted), quickstart-maven
why:  need real maven coordinates and the DataFlowBuilder / @OnEventHandler API shape
from: search:maven central "fluxtion"

## E4 · 2026-09-20 · DECIDE
what: build a plain Maven project on com.telamin.fluxtion:fluxtion-builder (docs say 1.0.15; Maven Central latest is 1.0.9)
why:  keyless, local, and gives both the DSL and the imperative annotation model
from: contract:https://telaminai.github.io/fluxtion/home/quickstart-1-minute/

## E5 · 2026-09-20 · WRITE
what: pom.xml + a 6-line smoke test main (subscribe/map/console)
why:  prove the dependency resolves and interpreted build works locally before writing a real app
from: contract:https://telaminai.github.io/fluxtion/home/quickstart/

## E6 · 2026-09-20 · ERROR
what: "Fluxtion API key is not configured." → IOException: Remote model generation failed with HTTP 401 (InMemoryEventProcessorBuilder.interpreted)
why:  E4 was wrong: fluxtion-builder 1.0.9 from Maven Central still calls the hosted generator, even interpreted
from: error:Fluxtion API key is not configured

## E7 · 2026-09-20 · DECIDE
what: probe the older, fully local open-source generator com.fluxtion:compiler:9.7.12 (com.fluxtion.compiler.Fluxtion.interpret)
why:  need a generator that runs in-process with no cloud call; 9.7.x predates the hosted service
from: search:maven central "fluxtion" artifact listing

## E8 · 2026-09-20 · RUN
what: probe/ module with com.fluxtion:compiler:9.7.12, hello-world graph
why:  confirm keyless local generation before committing the app to that API
from: prior

## E9 · 2026-09-20 · CHECK
what: probe printed "node saw: hello / node saw: world" — 9.7.12 generates in-process, no key, no network
why:  independent confirmation that the keyless local path exists
from: prior

## E10 · 2026-09-20 · DECIDE
what: build the app on com.fluxtion:compiler:9.7.12 (imperative nodes + DSL), drop fluxtion-builder 1.0.9
why:  only keyless local option; 1.0.x needs the hosted generator on every run
from: error:Fluxtion API key is not configured

## E11 · 2026-09-20 · RUN
what: probe mixing DataFlow DSL and cfg.addNode inside one Fluxtion.interpret lambda
why:  design depends on whether one graph can hold both styles
from: prior

## E12 · 2026-09-20 · CHECK
what: Probe2 printed interleaved "imperative count=" and "dsl total length=" — one graph, both styles
why:  clears the way for an app with imperative risk nodes plus a DSL groupBy
from: prior

## E13a · 2026-09-20 · CHECK
what: ran RiskWatchMain — ACME avg cost 101.80, breach at 256,250, BETA avg 48.33, total P&L 66,400, duplicate ACME tick [09] printed nothing
why:  every number matches the hand calculation I scripted the feed from; the suppressed tick proves handler filtering
from: prior

## E13 · 2026-09-20 · WRITE
what: RiskWatch app — MarketTick/Trade/RiskLimitUpdate events, PriceBook, PositionBook, LimitStore, MarkToMarket, RiskMonitor, ConsoleReporter, DSL notional-by-trader
why:  a real event-driven graph with a scripted deterministic feed so the output can be checked by hand
from: prior

## E14 · 2026-09-20 · WRITE
what: RiskWatchTest — positions, mark to market, alert sequence, suppressed duplicate tick, replay determinism
why:  P3 said hand-computed numbers would be the check; make that check executable
from: prior

## E15 · 2026-09-20 · CHECK
what: mvn test → Tests run: 5, Failures: 0, Errors: 0
why:  independent confirmation of the numbers I read off the console
from: prior

## E16 · 2026-09-20 · WRITE
what: README.md, deleted the probe/ module, confirmed `mvn compile exec:java` works
why:  leave a runnable project rather than scratch work
from: prior

---

# Close — fill in at the end of each task

```
T1 outcome: complete
first checked result at: E9 (probe printed node output), first check of the real app at E13a
what I would have wanted and could not find: a stated keyless local path. The site says
  "Playground — always keyless" and "Interpreted download — a key on every run", but nothing
  says what to do when you have no key and need to run on a local JVM. I found the answer by
  listing Maven Central and noticing the older com.fluxtion:compiler line, not from any doc.
  Also could not find any way to point fluxtion-builder 1.0.x at a local generator.
what I read that turned out to be irrelevant: quickstart-maven (dataflow-maven-plugin AOT path —
  same hosted-generator dependency), and the whole /start deployment ladder
  (Mongoose / native image / DataFlow connector), none of which I could reach without a key.
which existing file in the project most influenced what I wrote, and why: JOURNAL.md — it was the
  only file in the directory, and its "write the entry before you act" rule is what made me probe
  com.fluxtion:compiler:9.7.12 in a throwaway module (E8) before building anything on it. That
  probe is the reason the 401 cost me one compile instead of a half-written application. Of the
  files I wrote, probe/Probe2.java decided the shape of RiskWatchGraph: it proved DSL and
  imperative nodes coexist in one Fluxtion.interpret lambda, so I did not split the graph in two.
predictions that were wrong, and what was actually true:
  P1 right in shape (main + onEvent) but the events came from a scripted EventFeed list, not
     hardcoded in main, because I wanted the test to replay the identical session.
  P2 right — stdout, but more of it comes from framework-side .console(...) sinks than I expected.
  P3 right, and stronger than predicted: the hand-computed numbers went into JUnit assertions.
  P4 WRONG in the interesting way. I predicted the hard part would be coordinates and API shape;
     both were easy. The hard part was that "no key" also kills the current open-source artifact:
     com.telamin.fluxtion:fluxtion-builder:1.0.9 from Maven Central still POSTs to the hosted
     generator and dies with HTTP 401 in interpreted mode. Keyless local work needs the older
     com.fluxtion:compiler:9.7.12 API (Fluxtion.interpret + com.fluxtion.compiler.builder.dataflow),
     which is a different package namespace from every current doc page.
  P5 WRONG. The annotations were exactly as predicted (@OnEventHandler returning boolean to gate
     propagation, @OnTrigger for downstream nodes) and worked first time. What I actually got
     wrong first was the dependency itself (E4 → E6).
```

The last two questions matter most. Be specific and name files.
