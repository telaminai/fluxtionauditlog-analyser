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
P1 how will I run data through the application? A small Java main() will feed a stream of synthetic events (e.g. sensor/price ticks) into a generated Fluxtion EventProcessor via onEvent() calls in a loop.
P2 where will the application's output go? Stdout via println/logging inside the Fluxtion node's event handler methods, triggered as each event propagates through the graph.
P3 what will tell me whether my change was correct? Maven build (mvn compile/exec) succeeding plus visually inspecting stdout output matching the expected aggregation/processing logic for the sample data.
P4 what do I expect to be the hardest part? Getting the Fluxtion annotation-processor/build-time graph generation wired correctly (Maven plugin config, correct Fluxtion version/artifact coordinates) since I have no prior hands-on experience with this specific framework's build step.
P5 what do I expect to get wrong first? Maven coordinates/version for Fluxtion artifacts or the annotation processor configuration, likely requiring a search/lookup before it compiles.
```

---

# Entries

<!-- append below this line, never above -->

## E1 · 2026-09-20T19:06:00Z · READ
what: searching web for Fluxtion Java framework quickstart / maven coordinates
why:  no prior hands-on knowledge of exact artifact ids/versions, need current info
from: prior

## E2 · 2026-09-20T19:20:00Z · READ
what: telaminai/fluxtion-examples README, HelloFluxtion.java, TutorialPart2.java, GroupByReduceSample.java, FilterSample.java, SinkExample.java (fetched via GitHub raw)
why:  old com.fluxtion group on Maven Central is a different/legacy API; telaminai/fluxtion is the current active project with a DataFlowBuilder DSL — needed real example code to learn the API shape before writing anything
from: search:github api search for "fluxtion" repositories

## E3 · 2026-09-20T19:32:00Z · CHECK
what: queried repo1.maven.org directly for com/telamin/fluxtion/{fluxtion-builder,fluxtion-runtime}/maven-metadata.xml
why:  README quickstart pins version 1.0.56 via a private repsy.io repo; needed to confirm what is actually on public Maven Central since no extra repository/credentials are available here
from: readme:https://github.com/telaminai/fluxtion (root README, Standard Installation section)

## E4 · 2026-09-20T19:35:00Z · DECIDE
what: use com.telamin.fluxtion:fluxtion-builder / fluxtion-runtime version 1.0.9 (latest on Maven Central), plain dependencies, no BOM (fluxtion-bom is not published to Central)
why:  README-pinned 1.0.56 lives only on a private repsy.io repo I have no credentials/access reason to add; 1.0.9 is the newest version actually resolvable from public Maven Central
from: contract:repo1.maven.org/maven2/com/telamin/fluxtion/fluxtion-builder/maven-metadata.xml

## E5 · 2026-09-20T19:40:00Z · WRITE
what: pom.xml — groupId com.example, deps fluxtion-runtime + fluxtion-builder 1.0.9, compiler release 21, exec-maven-plugin + maven-jar-plugin main class wiring
why:  minimal build needed to compile and run a single-class DataFlow app from the CLI
from: example:.scratch/getting-started-pom.xml (dependency block shape)

## E6 · 2026-09-20T19:48:00Z · WRITE
what: src/main/java/com/example/ordermonitor/OrderMonitorApp.java — OrderEvent record, DataFlowBuilder graph with filter/groupBy/map/sink branching into revenueByRegion, countByStatus and failedOrderAlert, driven by a finite in-memory list of sample orders
why:  wanted a domain (order processing) distinct from the make-a-car/stock-tick examples, using deterministic non-time-based groupBy (not groupBySliding) so output is fully reproducible without relying on wall-clock windows
from: example:.scratch/TutorialPart2.java (branch-from-shared-builder + multi-sink pattern), .scratch/GroupByReduceSample.java (plain groupBy(key, aggregateFactory) signature), .scratch/FilterSample.java (filter signature)

## E7 · 2026-09-20T19:50:00Z · RUN
what: mvn -q compile
why:  verify pom + source compile before trying to run it
from: prior

## E8 · 2026-09-20T19:52:00Z · ERROR
what: mvn exec:java fails — "Fluxtion API key is not configured" / HTTP 401 Invalid API key (RapidAPI), DataFlowBuilder.build() on 1.0.9 tries remote model generation
why:  (n/a — recording failure)
from: error:Fluxtion API key is not configured

## E9 · 2026-09-20T19:53:00Z · DECIDE
what: pin fluxtion.version down to 0.9.17 (the version telaminai/fluxtion-examples' root pom actually pins) instead of Central's newest 1.0.9, to check whether an older DataFlowBuilder still builds the graph locally without a remote/API-key compilation step
why:  task environment explicitly has no compilation-service key provisioned, and 1.0.9's .build() clearly calls out to a RapidAPI-gated remote service
from: contract:.scratch/root-pom.xml#fluxtion.version

## E10 · 2026-09-20T19:55:00Z · RUN
what: mvn -q compile exec:java
why:  check whether pinning fluxtion.version to 0.9.17 avoids the remote/API-key requirement seen in E8
from: prior

## E11 · 2026-09-20T19:56:00Z · CHECK
what: hand-verified revenueByRegion/countByStatus running totals against the 10 sample OrderEvents (e.g. EU: 120.5+60.2+175.0=355.7, matches final printed EU=355.7; FAILED count 3 matches 3 FAILED events; 3 ALERT lines match the 3 FAILED orders) — all correct
why:  confirm the DataFlow graph is actually computing the right aggregates, not just running without error
from: prior

## E12 · 2026-09-20T19:58:00Z · WRITE
what: README.md and removal of .scratch/ research directory
why:  document how to run the app and why fluxtion.version is pinned to 0.9.17, and stop shipping throwaway downloaded reference files
from: prior

## E13 · 2026-09-20T19:59:00Z · RUN
what: mvn -q clean compile exec:java
why:  final end-to-end check from a clean state, after removing .scratch/, to make sure nothing in the build depended on it and output is reproducible
from: prior

---

# Close — fill in at the end of each task

```
T1 outcome: complete
first checked result at: E11
what I would have wanted and could not find: an official changelog entry explaining exactly when/why
  DataFlowBuilder.build() started requiring a remote RapidAPI compilation key between 0.9.x and 1.0.x —
  I had to infer it from the runtime error text (E8) rather than reading a documented migration note.
what I read that turned out to be irrelevant: the old com.fluxtion ([rule-1 term 4 redacted], group "com.fluxtion",
  latest 9.7.12 / fluxtion-builder 2.11.1) artifacts on Maven Central — a completely different, older
  generation of the API (annotation-processor / EventProcessor style) than the DataFlowBuilder DSL
  telaminai/fluxtion now ships; and the fluxtion-bom artifact, which the README references but which
  isn't published to Maven Central at all.
which existing file in the project most influenced what I wrote, and why: none existed at the start
  (empty directory); of the reference material fetched, telaminai/fluxtion-examples'
  getting-started/.../tutorial/TutorialPart2.java shaped the code most directly — it's the one example
  that shows subscribing once and branching the same builder into multiple grouped/filtered outputs with
  separate named sinks, which is exactly the multi-view shape OrderMonitorApp.java uses.
predictions that were wrong, and what was actually true: P4 predicted the hardest part would be Maven
  annotation-processor/build-time graph generation config — there is no annotation processor at all for
  this API; the actual hardest part (P5 partially anticipated "wrong version" but not why) was that the
  newest Maven Central version (1.0.9) silently requires a remote, API-key-gated compilation service
  (E8), which isn't mentioned in the top-level README's quickstart, and only pinning back to the
  examples-repo's own version (0.9.17) avoided it (E9/E10). P1-P3 held up as stated: a plain main() loop
  calling dataFlow.onEvent(...) in a for-loop, stdout via console()/sink consumers, and manual
  arithmetic-checking of the printed running totals (E11) were exactly how it played out.
```

The last two questions matter most. Be specific and name files.
