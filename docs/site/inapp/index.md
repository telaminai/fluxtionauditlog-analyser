# Diagnose in the analyser

The analyser's assistant assembles the selected record(s), the node-type map and the relevant source, then asks a
model to explain what happened in the cycle and why.

Its real power is the **round trip**. The assistant doesn't just answer in a chat window — it can
**drive the analyser** and file its findings back as things you can *see and interrogate*: a plotted
graph, a filtered table, jumped-to and flagged records, all over the **same data** you can click into
and trace to source. The conclusion arrives as a chart, not a wall of text you have to trust — so the
**verification loop is built in**. When the assistant says "the quote calculator stopped because the
venue disconnected here", it can plot that series and flag those cycles, and you watch it on screen.

## Fault-finding in practice

1. **Scope it.** Drag the **Time range** window to the incident, then select the suspicious records in
   the table (Shift/⌘-click for several).
2. **Ask.** Right-click ▸ *Ask the assistant: What happened here?* (or *Compare these rows* for several), the toolbar's
   **Ask**, or **Ask about this** in the record detail. The question is placed in the composer — see
   [Asking about what is on screen](ask.md) — and goes when you press **Send**.
3. **Watch it work.** The assistant reads the surrounding records, aggregates over the index to spot the
   pattern, and when it finds the cause it **plots the offending series into a graph** (captioned with
   its reasoning), **flags** the culprit records, and **filters** the table down to them — the fault
   rendered in your own instrument, not described in prose.
4. **Verify.** Click the plotted points, jump to the flagged records, trace a nodeLog line to the exact
   source method. Nothing is taken on faith; the evidence is right there to challenge.

## Which assistant, for what

The assistant panel is one panel, whichever assistant answers in it. **Set up AI…** chooses how it works on this
machine, and the mode chip in its header (**Safe ▾**, **◆ Full ▾**, **In-app ▾**, **Off ▾**) switches it ([Assistant profiles](../admin/profiles.md)):

| You want | Choose | What it means |
|---|---|---|
| Answers about evidence, including logs you did not write | **Safe analysis** (the default) | Your installed Claude Code reads your evidence: Read, Grep and Glob in the project folder, and the analyser's own views. No shell, no edits, no web. A call that changes what is open or writes a file is asked of you first. |
| Claude Code that can also edit, build and run in your project | **Full Claude Code** | Your own Claude Code, with your settings, skills, hooks, MCP servers and tools, under your own permission rules. The analyser does not confine it to the folder. |
| An assistant without Claude Code, on an API key | **In-app assistant** | The analyser's own assistant, with a provider, model and key you choose. It cannot read your project folder or use your own tools. |
| No assistant at all | **Off** | Nothing is sent from the analyser. Every view, chart, report and walk still works. |

**No profile is local-only.** What you ask, and the evidence the assistant reads, is sent to its model provider:
Anthropic for Claude Code (on your Claude plan or an API key), or the provider you chose for the in-app assistant.
**What it costs:** for Claude Code on a Claude plan, usage counts against your plan and no dollar figure is shown;
for Claude Code on an API key, each finished answer shows its cost, and *Settings ▸ Assistant ▸ Limit spend per
session* (off by default) can cap a session. The in-app assistant shows no cost: your provider bills your key.

Prefer to work from your own terminal or IDE client? That is [Diagnose an incident](../diagnose/index.md): the same
verbs, driven from outside.

## In this section

- [The assistant panel](assistant-panel.md): the header, the composer, the in-app assistant and *Copy prompt*.
- [Ask the assistant](ask.md): every question you can ask from the screen, and where it is.
- [Claude Code in the analyser](claude-code.md): turning it on, Start, Safe analysis and Full Claude Code, using it.
- [Reading the transcript](transcript.md): chips, tool lines and their marks.
- [Approvals](approvals.md): what the approval dialog says, and what *Allow once* lets through.

The **work mode** (*AI ▸ Work mode ▸ Investigate / Build*) tells an assistant whether this session is for
investigating or building; [Runbooks, glossary and posture](../ai-and-runbooks.md#what-this-session-is-for-work-mode-and-the-mode-selector-record)
explains it.

## The canvas

Every assistant renders onto the same views you use yourself:

- [Records, detail & filtering](../user-guide/records-and-filtering.md)
- [Graphs](../user-guide/graphs.md)
- [Topology & step-through](../user-guide/topology.md)
- [Source navigation](../user-guide/source-navigation.md)
- [Investigation reports](../user-guide/reports.md)
- [The Project panel](../user-guide/project-panel.md)

Related pages in other sections: [The start page](../user-guide/start-page.md) and the
[sample prompt pack](../user-guide/sample-prompt-pack.md) under *Get started*;
[Projects, profiles and sharing](../user-guide/projects.md) and [Log-source plugins](../user-guide/plugins.md) under
*Administer and secure*.

## Where the old *Analyser assistant* page went

The single *Analyser assistant* page was split by task in October 2026. A link to one of its sections lands here;
this is where each section is now.

| Section | Now |
|---|---|
| One assistant, two modes; The in-app assistant; No API key? Copy-prompt mode | [The assistant panel](assistant-panel.md) |
| Choose how the assistant works; Setup | [Assistant profiles](../admin/profiles.md) |
| Asking about what is on screen | [Ask the assistant](ask.md) |
| Claude Code in the Assistant tab | [Claude Code in the analyser](claude-code.md) |
| How the conversation is drawn | [Reading the transcript](transcript.md) |
| Approvals in the analyser | [Approvals](approvals.md) |
| For administrators | [Managed policy (planned)](../admin/managed-policy.md) |
| Actions the assistant can take; Connect an MCP client | [Verbs](../reference/verbs.md) |
| Is the log whole? | [Log format ▸ Is the log whole?](../log-format.md#is-the-log-whole) |
| Ask it to show you; Save it as a walk; Walks with a conversation; Send it as a reel | [Spotlights, walks and reels](../teach/spotlights-walks-reels.md) |
| Set up a project with Claude | [Set up a project with Claude](../build/set-up-with-claude.md) |
| Reading coverage results | [Topology ▸ Reading coverage results](../user-guide/topology.md#reading-coverage-results) |
| Your first session; Hand the diagnosis on; Does my client launch the analyser?; If it isn't working | [Diagnose an incident](../diagnose/index.md) |
| Saved charts before opening a log; Explicit session restoration | [The Project panel](../user-guide/project-panel.md), [Working across projects ▸ Restore an earlier session](../user-guide/projects.md#restore-an-earlier-session) |
