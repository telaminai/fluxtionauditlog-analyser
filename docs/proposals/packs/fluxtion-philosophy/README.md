# Fluxtion philosophy prompt packs (draft)

Three start-page prompt packs that help someone get to grips with Fluxtion as a way of writing code — in short,
"what on earth is Fluxtion, as a code philosophy?". They are drafts: the teaching example each one names is a
placeholder until a published example exists (see *The teaching example* below).

## Intent and audience

| Pack | Title | For | What it should leave the person with |
|---|---|---|---|
| [01-basic.md](01-basic.md) | What is Fluxtion? | A Java developer who has never used Fluxtion | What an event, a handler, a trigger and dispatch are; how to read them in the records; which code they wrote and which the compiler generated; why "it wires my objects at build time" is a useful (and limited) DI comparison; how their own classes would look as nodes. |
| [02-intermediate.md](02-intermediate.md) | Local knowledge, global behaviour | Someone who has run a Fluxtion processor and read its records | Each node declares only its parents; the compiler derives the global order. The diamond, the returned boolean, guards and change detection — each checked against the records rather than asserted — and how this compares with hand-written callbacks, an event bus or a scheduler. |
| [03-advanced.md](03-advanced.md) | Thinking in Fluxtion | Someone designing a graph, or reviewing one | The closed-world build step and its assumptions; where the model stops (hidden shared state, undeclared dependencies, side effects, threads, mutation from outside); determinism and replay; composing components they did not write. |

The prompts are written for the **person** to send, in their own voice. They are not instructions to the analyser.

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

All three packs assume the same tiny project, so the answers can point at something real:

- **A four-node diamond: A → B, A → C, B → D, C → D.** A is the `@OnEventHandler` entry point; B, C and D are
  `@OnTrigger` nodes that take their parents through their constructors.
- **Change-propagating, stateful nodes.** Each node keeps a little state (a running value or a count) and returns
  `true` only when that state changed, so some events stop part-way and the returned boolean is visible.
- **Every node writes audit output** (`auditLog.info(...)`, with event audit enabled in the builder), because a node
  that writes nothing does not appear in a record and its silence proves nothing.
- **Built by a `FluxtionGraphBuilder`** with `setWriteSourceToFile(true)` and `setWriteGraphMlToFile(true)`, so the
  generated processor and its graph are on disk next to the log — the shape of the golden path.
- **A short event script** that includes a first event, an event that changes only one side of the diamond, and an
  event that changes nothing.

No such project is published yet. Entry 1 of each pack is therefore a clearly marked **PLACEHOLDER**
(owner `example`, an all-zero commit), and each pack's description line says so. **Pin it before use**: replace it
with the published project's `…/archive/<40-hex commit>.zip` URL (and, ideally, its `sha256:`), then re-run the lint.

## The grounding rule

Every answer should come from three places, and say which:

- **`claude.txt`** — the Fluxtion framework reference (fluxtion repo, `docs/claude.txt`), authoritative on semantics;
- **the golden path** — the playground's blessed shape for an audited AOT graph;
- **what is on screen** — the audit records, the graph and the generated source of the loaded project.

Where those do not settle a question, the answer should say so rather than fill the gap from general knowledge.
Every prompt ends by asking for that ("tell me if you can't tell from what's here"). The questions most likely to
tempt a confident guess — how often the diamond's downstream node runs, what happens when only one side changes,
first-event behaviour, what a silent node means — are deliberately phrased as "check it against the records".

[RATIONALE.md](RATIONALE.md) lists, for every entry, the concept it teaches and the section of `claude.txt` or the
golden path it rests on.
