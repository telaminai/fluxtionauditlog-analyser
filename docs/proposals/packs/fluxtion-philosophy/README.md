# Fluxtion philosophy prompt packs (draft)

Start-page prompt packs that help someone get to grips with Fluxtion as a way of writing code — in short,
"what on earth is Fluxtion, as a code philosophy?" — and then with the places a Fluxtion processor lives: its
audit log and the evidence made from it, a Mongoose server, an application it is embedded in, the C++ preview, and
Mongoose plugins. They are drafts: most teaching examples are placeholders until a published example exists (see
*The teaching example* below), and the gaps the sources leave are listed under *Needs owner input*.

## Intent and audience

| Pack | Title | For | What it should leave the person with |
|---|---|---|---|
| [01-basic.md](01-basic.md) | What is Fluxtion? | A Java developer who has never used Fluxtion | What an event, a handler, a trigger and dispatch are; how to read them in the records; which code they wrote and which the compiler generated; why "it wires my objects at build time" is a useful (and limited) DI comparison; how their own classes would look as nodes. |
| [02-intermediate.md](02-intermediate.md) | Local knowledge, global behaviour | Someone who has run a Fluxtion processor and read its records | Each node declares only its parents; the compiler derives the global order. The diamond, the returned boolean, guards and change detection — each checked against the records rather than asserted — and how this compares with hand-written callbacks, an event bus or a scheduler. |
| [03-advanced.md](03-advanced.md) | Thinking in Fluxtion | Someone designing a graph, or reviewing one | The closed-world build step and its assumptions; where the model stops (hidden shared state, undeclared dependencies, side effects, threads, mutation from outside); determinism and replay; composing components they did not write. |
| [04-audit-and-evidence.md](04-audit-and-evidence.md) | Audit logs and evidence | Anyone reading a log in the analyser, or handed one | What a record shows; audit levels (INFO against TRACE); what a silent node means; whether order in a record is meaningful; flags, reports and walks; packing and verifying an evidence bundle; trusting the evidence rather than the author. |
| [05-deploy-mongoose.md](05-deploy-mongoose.md) | Running it in Mongoose | Someone taking a processor to a Mongoose server | The Fluxtion / Mongoose split; how configuration composes an app; how events reach the processor and on which thread; server and processor lifecycle; sinks and services; getting the audit log to the analyser; observing a running server. |
| [06-deploy-embedded.md](06-deploy-embedded.md) | Embedding the processor | Someone putting the generated processor straight into their own application | Embedded against hosted; creating the processor by name; wiring the event input and its thread; the lifecycle in order; reusing an instance; testing; audit output; changing a node from outside safely. |
| [07-cpp-preview.md](07-cpp-preview.md) | C++ generation (preview) | Someone curious about, or evaluating, the C++ target | That it is a **preview** and evolving: what the path takes and produces, what the supported subset is *today* (asked, never assumed), what is tested against Java, its audit output, and what to check before depending on it. |
| [08-mongoose-plugins.md](08-mongoose-plugins.md) | Mongoose plugins | Someone extending a Mongoose server | What a plugin is against a graph node; the three kinds; a tour of the reference repository's scheduler plugin; how a plugin reaches a processor; plugin threads against processor threads; which server version the reference targets; adding and writing one. |

The prompts are written for the **person** to send, in their own voice. They are not instructions to the analyser.

**The deployment packs (05, 06, 08) explain; they never deploy.** A standing design decision holds that the analyser
never deploys or controls a server — no server verbs on its action socket; server control is a Mongoose-side tool.
So no prompt asks the analyser or its assistant to deploy, start, stop or reconfigure anything: each explains, points
at the person's own tools and docs, and several say "don't start or change any server" outright. The one prompt that
asks the analyser to *write* something (04/7, an evidence bundle) asks it to explain first and to ask before writing.

## How to use them

1. Open the analyser's start page.
2. Drop one pack file onto it. The analyser's prompt-pack parser checks the file; a pack it refuses is named with
   the line and the cause.
3. Entry 1 of each pack is the teaching example. Download it (once it is pinned — see below), build and run it so
   there is an audit log, its graph and its generated source on screen.
4. Choose an entry and send it. The entries run roughly in order, but each stands on its own.

Check a pack before dropping it, with no analyser built:

```
python3 tools/m74_onboarding_runner.py lint-pack docs/proposals/packs/fluxtion-philosophy/01-basic.md
```

It prints the parsed pack, or one refusal with its line and cause (the runner's mirror of the Java parser).

## The teaching example each pack expects

Packs 01–04 and 06 assume the same tiny project, so the answers can point at something real:

- **A four-node diamond: A → B, A → C, B → D, C → D.** A handles the event with `@OnEventHandler`; B, C and D are
  `@OnTrigger` nodes that take their parents through their constructors.
- **Change-propagating, stateful nodes.** Each node keeps a little state (a running value or a count) and returns
  `true` only when that state changed, so some events stop part-way and the returned boolean is visible.
- **Every node writes audit output** (`auditLog.info(...)`, with event audit enabled in the builder), because a node
  that writes nothing does not appear in a record and its silence proves nothing.
- **Built by a `FluxtionGraphBuilder`** with `setWriteSourceToFile(true)` and `setWriteGraphMlToFile(true)`, so the
  generated processor and its graph are on disk next to the log — the shape of the golden path.
- **A short event script** that includes a first event, an event that changes only one side of the diamond, and an
  event that changes nothing.

For 06 the same project serves, with a `Main` that loads the generated processor by name and a JUnit test of the
golden-path shape. The others:

- **05 (Mongoose)** — the diamond hosted in a Mongoose server: a YAML configuration with one in-memory or file feed,
  one processor agent, a file sink, and the audit log configured to a `---`-separated text file.
- **07 (C++)** — the diamond generated for the C++ target, with the author-written parts (node bodies, driver) beside
  the generated header, and a binary audit log the analyser can open.
- **08 (plugins)** — the reference repository itself, `gregv12/fluxtion-server-plugins`, **pinned** to commit
  `9fce03a1229b1b80dd7ef7abd4353b5c77f3a13d`, the one read for this draft (2026-10-17). It is the only real pin.

No teaching project is published yet. Entry 1 of packs 01–07 is therefore a clearly marked **PLACEHOLDER**
(owner `example`, an all-zero commit), and each pack's description line says so. **Pin it before use**: replace it
with the published project's `…/archive/<40-hex commit>.zip` URL (and, ideally, its `sha256:`), then re-run the lint.

## The grounding rule

Every answer should come from these places, and say which:

- **`claude.txt`** — the Fluxtion framework reference (fluxtion repo, `docs/claude.txt`), authoritative on semantics;
- **the golden path** — the playground's blessed shape for an audited AOT graph;
- **the topic's own docs** — the Fluxtion repo's how-to and reference pages, this analyser's docs site (the audit-log
  format specification, evidence bundles), the Mongoose docs, and for 08 the reference repository's code
  (RATIONALE.md lists each);
- **what is on screen** — the audit records, the graph and the generated source of the loaded project.

Where those do not settle a question, the answer should say so rather than fill the gap from general knowledge.
Every prompt ends by asking for that ("tell me if you can't tell from what's here"). The questions most likely to
tempt a confident guess — how often the diamond's downstream node runs, what happens when only one side changes,
first-event behaviour, what a silent node means — are deliberately phrased as "check it against the records".

## Needs owner input

Where no authoritative public source states something, the prompt asks rather than asserts, and the gap is here:

1. **The teaching projects.** None of the seven placeholder examples exists; each needs a published, pinned project
   of the shape described above.
2. **C++ supported subset (07/3).** No public page states which annotations, node shapes, event types, services or DSL
   features the C++ target supports. The public material (FLXA format page, performance pages, the compiler config's
   `targetId`, this repo's latency-kit README) says only that a C++ generator and header-only runtime exist, that the
   C++ audit writer is held to Java by parity tests rather than the conformance corpus, and that several items are
   "deferred to the C++ round". A public "what C++ supports today" page is needed before 07 can be answered.
3. **C++ availability (07/7).** The public README builds against a locally installed `fluxtion-generator-cpp`; whether
   it is published, and to whom, is not stated.
4. **The plugins reference targets an older server line (08/6).** `fluxtion-server-plugins` builds against
   `com.fluxtion:server` 0.2.16 and Fluxtion runtime 9.7.14 (packages `com.fluxtion.server…`, `com.fluxtion.runtime…`),
   while the current Mongoose docs use `com.telamin.mongoose…` and `com.telamin.fluxtion.runtime…`. Whether the
   repository is the intended reference for today's Mongoose, or `telaminai/mongoose-plugins` is, needs deciding.
5. **The plugins reference carries an employer-domain address** in its SPDX copyright headers (the fourth sweep
   term's domain). Nothing here copies it, but a capture or transcript of a session on that repository would show it
   (CLAUDE.md rule 1). Decide before using 08 in any recorded run.
6. **Mongoose audit configuration (05/7).** This repo's *Producing an audit log* says the level and file sink are
   server configuration and defers to the Mongoose docs; the Mongoose pages show a per-handler `logLevel` and
   `setAuditLogLevel`, but no page states end to end how a hosted processor's audit records reach a `---`-separated
   file. A worked Mongoose example is needed.
7. **Mongoose deployment guide quality (05).** The Mongoose *Deployment Guide* mixes concrete API with generic advice
   (clustering, state replication, tracing) that reads as aspirational; 05 avoids those topics. Owner to confirm what
   is supported.
8. **`@ExportService` with a non-void method.** `claude.txt` says it fails compilation; the Fluxtion SEP-lifecycle page
   says the export is dropped silently. No pack depends on it, but the two disagree.

[RATIONALE.md](RATIONALE.md) lists, for every entry, the concept it teaches and the section of `claude.txt` or the
golden path it rests on.
