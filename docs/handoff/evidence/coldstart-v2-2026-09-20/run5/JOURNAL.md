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
P2 where will the application's output go? stdout via System.out/println from node code, plus maybe generated dispatcher source on disk.
P3 what will tell me whether my change was correct? The printed output matching hand-computed expected values; a JUnit test asserting them.
P4 what do I expect to be the hardest part? Getting the right Fluxtion maven coordinates/version and knowing whether to use the AOT compiler plugin or the runtime interpreted builder.
P5 what do I expect to get wrong first? The API surface — annotation names (@OnEventHandler vs @OnEvent) and the builder entry point class name.
```

---

# Entries

<!-- append below this line, never above -->

## E1 · 2026-09-20T20:10:00Z · READ
what: curl http://127.0.0.1:5187 and /start, listed all hrefs
why:  need Fluxtion maven coords + API shape before writing anything
from: operator

## E2 · 2026-09-20T20:14:00Z · READ
what: /build-with-ai then downloaded /CLAUDE.md (26KB orientation doc)
why:  the site names CLAUDE.md as the canonical author-orientation doc
from: contract:http://127.0.0.1:5187/build-with-ai#the-bootstrap

## E3 · 2026-09-20T20:18:00Z · READ
what: /starter-templates/index.json — 14 templates, each with mode + keyNeed
why:  CLAUDE.md "Start a project from an empty directory" says choose a template id first
from: contract:CLAUDE.md#start-a-project-from-an-empty-directory

## E4 · 2026-09-20T20:20:00Z · DECIDE
what: target an AOT template (keyNeed=build) not interpreted (keyNeed=run), no key here
why:  interpreted needs the cloud generator on every run; AOT should ship pre-generated dispatcher source that plain mvn can compile
from: contract:http://127.0.0.1:5187/start#when-do-i-need-a-key

## E5 · 2026-09-20T20:24:00Z · RUN
what: curl /start/scaffold?template=fluxtion-dag-multi-io&group=com.example&artifact=riskflow, unzip
why:  CLAUDE.md gives the scaffold URL; multi-IO DAG is the richest runnable shape
from: contract:CLAUDE.md#start-a-project-from-an-empty-directory

## E6 · 2026-09-20T20:28:00Z · READ
what: riskflow/README.md + pom.xml — plugin `scan` goal calls the cloud source-gen API at process-classes
why:  need to know exactly where the missing key will bite
from: readme:riskflow/README.md

## E7 · 2026-09-20T20:30:00Z · RUN
what: ./mvnw package (expect failure at fluxtion-maven-plugin:scan — no key provisioned)
why:  errors here are directive; I want the exact message before choosing a fallback
from: runbook:riskflow/runbooks/build.md#build-and-verify

## E8 · 2026-09-20T20:32:00Z · ERROR
what: mktemp: mkdtemp failed on /var/folders/.../tmp.EIkhEqjrY4: Operation not permitted — ./mvnw cannot bootstrap
why:  sandbox blocks the system temp dir; system `mvn` 3.9.9 is on PATH with java.io.tmpdir already redirected
from: error:mktemp: mkdtemp failed

## E9 · 2026-09-20T20:36:00Z · ERROR
what: Fluxtion API key is not configured ... set -Dfluxtion.apiKey (fluxtion-maven-plugin:1.3.0:scan)
why:  AOT source-gen is a remote service call; no key is provisioned here, so the AOT path cannot generate
from: error:Fluxtion API key is not configured

## E10 · 2026-09-20T20:38:00Z · DECIDE
what: look for a key-free local build path in fluxtion-builder/runtime jars before changing approach
why:  CLAUDE.md says the `DataFlowBuilder...build()` path runs "no source-gen" — that may need no service
from: contract:CLAUDE.md#symptom-cannot-find-symbol-method-lambdanull-in-dsl-flow-generated-code

## E11 · 2026-09-20T20:42:00Z · READ
what: javap on fluxtion-builder-1.0.71.jar — found com.telamin.fluxtion.FluxtionInterpreter.interpret(...)
why:  Fluxtion.compile* routes through RemoteHttpCombinedGenerator; interpret() may not
from: search:javap fluxtion-builder jar for compile entry points

## E12 · 2026-09-20T20:45:00Z · RUN
what: spike a throwaway Main calling FluxtionInterpreter.interpret on the scaffold nodes
why:  must prove a key-free local build path exists before rewiring the project
from: stub:fluxtion-builder-1.0.71.jar!com/telamin/fluxtion/FluxtionInterpreter.class

## E13 · 2026-09-20T20:50:00Z · ERROR
what: Fluxtion API key is not configured — at RemoteHttpModelGenerator.generate ... FluxtionInterpreter.interpret
why:  interpret() ALSO calls the remote service; the website's "key on every run" is literal. No key-free path in com.telamin 1.0.67–1.0.71 (checked 1.0.67 jar too, same remote/client classes)
from: error:Fluxtion API key is not configured

## E14 · 2026-09-20T20:54:00Z · DECIDE
what: build the app on com.fluxtion:compiler/runtime 9.7.12 from Maven Central (the predecessor OSS line, local source-gen) instead of com.telamin 1.0.71
why:  it is the same framework and annotation model, generates the dispatcher in-process with no API key; every com.telamin path is gated on a key I do not have
from: search:maven central g:com.fluxtion artifact list

## E15 · 2026-09-20T20:58:00Z · CHECK
what: spike2 — Fluxtion.interpret on com.fluxtion:compiler:9.7.12 dispatched 2 events, printed count=2, no key
why:  independent proof the OSS line needs no compile service
from: prior

## E16 · 2026-09-20T21:02:00Z · DECIDE
what: keep the scaffold's project shape (event/ node/ builder/ FluxtionMain + runbooks) but rewrite the POM for com.fluxtion 9.7.12 and fill the TODO nodes with a real trading-risk graph
why:  the scaffold encodes the house layout; only the coordinates and the generation route change
from: example:riskflow/src/main/java/com/example/riskflow/builder/PricingDagBuilder.java

## E17 · 2026-09-20T21:05:00Z · WRITE
what: pom.xml — com.fluxtion compiler+runtime 9.7.12, drop fluxtion-maven-plugin scan goal, keep shade
why:  AOT generation now happens in-process at startup via Fluxtion.compile, so no build-time plugin
from: prior

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
