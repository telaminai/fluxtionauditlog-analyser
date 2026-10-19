# Guided tour and sample project

You can see what the analyser does before you have a log of your own. It ships with a recorded DEMO run, the
processor's graph and its source, inside the jar. Three start-page actions open them. None needs an AI, a key, a
server or a network connection.

![The start page: What would you like to do? with its numbered entries, a box and its button; Or open directly with the
Explore DEMO, Open, Create, Connect an assistant and Settings blocks; and Recent](../assets/start-page.png)

| Action | Where | What it does | Length |
|---|---|---|---|
| **Run me through a demo** | entry 1 in the list, then **Play** | Opens the sample project and plays the DEMO journey *Find the first recorded breach* (saved there as the walk *DEMO first breach*), with a scripted conversation beside it, labelled *Simulated conversation* | four steps, about 3 minutes |
| **Guided tour** | *Or open directly ▸ Explore DEMO* | Opens the sample project and plays a four-stop spotlight walk: the records, the topology with its coverage verdict, a record's detail, and the Reports tab | four stops |
| **Sample project** | *Or open directly ▸ Explore DEMO* | Opens the same DEMO log, topology and source, and leaves you to explore at your own pace | as long as you like |

**Teach me by building an example**, entry 3 in the list, also opens the sample project. It then drafts a lesson
request for your assistant; with no AI set up, it plays the guided tour instead ([Build with Claude](../build/index.md#where-to-start)).

## The guided tour

The tour is an ordinary saved spotlight walk, so it plays the way any walk does. Step with ◀ ▶ on the strip, or the
← → keys; a click elsewhere or a change of view ends it. Its four stops:

1. **The records.** Each row is one processor cycle. Select one to see what happened in it.
2. **The topology.** The processor's graph, with the coverage verdict: which nodes logged in this run, and what the
   log can and cannot say about the ones that did not.
3. **A record's detail.** The first cycle's event and the entries each node logged.
4. **The Reports tab.** Where the tour itself is saved, as the walk *DEMO product introduction*. Replay it from there, or make a walk of your own
   ([Spotlights, walks and reels](../teach/spotlights-walks-reels.md)).

## The demo journey

*Run me through a demo* plays a **conversation journey**: a walk over a longer DEMO log, with a question and an
answer at each step. The conversation is scripted, and every surface says so. Its numbers were asked of the analyser
when the journey was built, and a test checks them against the log on every build. Nothing is sent anywhere while it
plays. At the end, you can ask your own question about the same evidence with your own assistant.

[Journey: find the first recorded breach](../journeys/find-the-first-recorded-breach.md) shows every step as it
played, and offers the same journey as an evidence bundle you can download.

## What the sample project holds

The first time you use one of these actions, the analyser unpacks the demo set to `~/.fluxtion-analyser/demo/` and
makes it a project. It reuses that folder afterwards.

| File | Why it is there |
|---|---|
| `demo-quote-audit.yaml` | A 10-record run. Five of the graph's nodes log, so coverage has a real answer, not a vacuous 100% |
| `demo-quote-processor.graphml` | The processor's graph, so the topology renders and cycles can be stepped through |
| `demo-quote-audit-traced.yaml` | A run with invocation tracing on: the one log where a node that wrote nothing can be shown not to have run |
| `demo-quote-series.yaml` | 726 records, enough for a chart worth reading |
| `journey/` | The demo journey's copy of the 726-record log, and its own saved settings |
| `com/acme/demo/…` | The processor's Java source, so a node or a log line leads to its code |

Opening the sample project is a project switch: it closes the log and project you had open. The demo folder belongs
to the analyser. Claude Code, if you start it there, works in the analyser's private scratch folder instead, and the
session header says so.

## Where next

- [Open your own log](../getting-started.md): the same views, on a log from your processor.
- [Playground to analyser in 10 minutes](../tutorial-playground.md): download a small system, run it, and read its log.
- [Running a demo](../teach/index.md): show the analyser to an audience.
