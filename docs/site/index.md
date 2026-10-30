# Fluxtion Audit Log Analyser

A desktop canvas for **Fluxtion audit evidence**. A Mongoose/Fluxtion `EventProcessor` writes down every cycle it runs:
the event that arrived, and each node that logged, in order, with what it wrote. The analyser indexes that record,
so multi-GB logs stay responsive. You, or an AI you connect, can then browse it, filter it, graph it, step through it
on the processor's own topology, and report on it.

**You do not have to have written the system.** Nobody wrote the log up afterwards: the processor emitted it as it ran.
So it answers a support engineer in their first week as readily as the person who built the graph, and an AI's
conclusion arrives as something on screen you can check, not prose you have to trust.

![The analyser on a demo log: the records table, one cycle read out logically, and the processor's own graph with that
cycle shaded](assets/screenshot-dark.png)

## Choose your door

<div class="grid cards" markdown>

-   :material-lan-connect: **Diagnose an incident**

    ---

    Something went wrong in a running system. Point Claude Code, Codex or another MCP client at the analyser you
    have open, ask in your own words, and watch the client filter, plot and flag the evidence on your screen. Or ask
    the assistant built into the analyser.

    [:octicons-arrow-right-24: With your AI client](diagnose/index.md) ·
    [:octicons-arrow-right-24: In the analyser](inapp/index.md)

-   :material-hammer-wrench: **Build with Claude**

    ---

    You state the intent and the acceptance criteria. The AI structures the components, Fluxtion writes the
    mechanics, and you check your criteria against the recorded evidence of your runs. Whether the criteria
    capture what you meant stays your judgement.

    [:octicons-arrow-right-24: Build your first application](start-with-claude.md) ·
    [:octicons-arrow-right-24: How building with Claude works](build/index.md)

-   :material-rocket-launch-outline: **Get started**

    ---

    Install with one command, open the built-in DEMO (*Explore DEMO ▸ Guided tour* on the start page), and find the
    route that fits what you came to do.
    No AI and no key are needed to begin.

    [:octicons-arrow-right-24: Start here](start-here.md) ·
    [:octicons-arrow-right-24: Install & run](install.md)

-   :material-shield-lock-outline: **Administer and secure**

    ---

    What each assistant profile can do, how external access is opened and closed, what is stored on the machine, and
    what leaves it.

    [:octicons-arrow-right-24: Security model and limits](admin/security.md) ·
    [:octicons-arrow-right-24: Assistant profiles](admin/profiles.md)

</div>

## Run it now

You need **Java 21 or later**. With [JBang](https://jbang.dev):

```bash
jbang analyser@telaminai/fluxtionauditlog-analyser
```

With no log to hand, choose **1. Run me through a demo** on the start page and press **Play**: it opens a recorded
DEMO run and its topology from inside the jar. [Install & run](install.md) has the plain-jar download, building from source, and `--rest` for an
agent.

## One log, every view

The audit log is the single source of truth, and every feature reads the same records. Narrow the view once and the
table, the charts and the topology all follow.

--8<-- "assets/audit-loop.svg"

- **[Records, detail & filtering](user-guide/records-and-filtering.md).** A columnar index keeps browsing and filtering
  fast; files over a size threshold are memory-mapped rather than loaded into the heap.
- **[Graphs](user-guide/graphs.md).** Any top-level numeric or boolean node value over time, with formula series,
  guides, bands and markers.
- **[Topology & step-through](user-guide/topology.md).** The processor's own node graph, which nodes ran in a cycle,
  and which never logged at all.
- **[Source navigation](user-guide/source-navigation.md).** A node log line opens the class and method that wrote it.
- **[Investigation reports](user-guide/reports.md)** and **[evidence bundles](evidence-bundles/index.md).** An
  account of what you found, and a verifiable package for someone who was not watching.

## Why it matters

- **Minutes to root cause, with evidence.** An incident in an event-driven system usually means grepping a large log
  and reconstructing by hand what fired, in what order. Here the log is indexed in seconds, any value plots over time,
  two cycles diff in a click, and a log line opens the source method that wrote it.
- **The audit log becomes an asset.** The bytes you already keep become incident forensics (what actually ran, with
  its state), a compliance answer (why this order was amended, cycle by cycle, traceable to source) and a shared team
  artefact.
- **Only possible with Fluxtion.** A Fluxtion processor is deterministic and its graph is generated from source, so a
  record is not just a log line: it is a trace of one cycle with an address in the code.

## Elsewhere

[FAQ](faq.md) · [Log format](log-format.md) · [Release notes](release-notes.md) ·
[Source on GitHub](https://github.com/telaminai/fluxtionauditlog-analyser)
