# Answering questions about a running system

You have been handed a log and a question about an incident you were not present for. This page is for
that: not a tour of the analyser, but the path from *"a log arrived"* to *"here is what happened, with
the evidence"*.

It works because the log is **not anyone's account of the incident**. Nobody wrote it down afterwards
and nobody summarised it — the processor emitted it as it ran, in order, cycle by cycle. That is why
you can answer questions about a night you were asleep for.

## The first two minutes

1. **Open the log.** **File ▸ Open log…**, drag it onto the window, or **File ▸ Open from S3…** with an
   `s3://bucket/key` URL if it is already in object storage.
2. **Read the status bar.** It tells you what you have before you look at anything else: how many
   records, the time range, and where the log came from. Trust this over the filename.
3. **Add the topology if you have it** — **File ▸ Open GraphML…**, or **File ▸ Find GraphML in source
   roots…** to see which of the available graphs actually *fits* this log, ranked. The Topology tab
   then says, permanently, whether the graph matches the log you have open. If it says it does not,
   believe it: everything the graph tells you afterwards is about a different build.

!!! danger "Check which system the log came from, before you answer about it"
    Two environments running the same build produce logs that are identical in shape and often
    identical in filename. The analyser can only tell them apart if whoever exported the log
    **declared** where it came from. When it was declared you will see it in the status bar and in
    report headers — `risk-engine · host-7 · prod` rather than `export-2.yaml`.

    If your logs arrive without that, ask whoever automates the export to pass it: an agent or script
    calls `open {log, graphml, provenance: "<service> · <host> · <environment>"}`. It costs one field
    and it is the only thing standing between "the fix is live" and "the fix is live *in UAT*".

## "Nothing is in the log"

Before assuming the system was quiet, check that the log is *whole*. Opening one runs three checks and
reports what it finds in the status bar — hover it for the full sentence, which names the fix:

- **records run together** — the file is missing its `---` separators, so however many records it
  holds, the analyser reads **one**. The count is wrong and everything after the first record is
  invisible.
- **no `nodeLogs` in any record** — the processor was built without audit logging installed, so there
  is nothing to read, filter or plot. The run happened; the narration did not.
- **only `EventLogControlEvent`** — the log contains the audit configuration and none of the run.

All three are faults in how the log was *produced*, not in what the system did. Send the message
back to whoever owns the export — it names the cause and the fix — rather than debugging a shortage of
evidence. See [Producing an audit log](../producing-a-log.md).

## The four questions you will actually be asked

### "Did *X* happen at all?"

Search and filter, then read the count. The filter is **shared** — narrow it once and the table, the
summary, the graphs and the topology all follow, so a count you see in one place is the same
population everywhere. See [Records, detail & filtering](records-and-filtering.md).

An empty result is a real answer here, but be careful how you phrase it: it means *nothing was
logged*, which is not the same as *nothing ran*. A node can run, decide nothing changed, and say
nothing. The next section is the one that closes that gap.

### "The check never fired — is that true?"

This is the question a log alone cannot answer and the reason to open the topology. **Coverage** lists
the nodes that never wrote audit output in this run — the question nobody can answer by eye on a
300-node estate.

Two cautions, both of which the analyser states rather than leaves to you:

- a gap means **"never logged"**, not proven "never ran";
- against a graph that was *inferred* from the run itself, coverage refuses to answer instead of
  reporting a comfortable 100%. If you see that refusal, you need the real `.graphml` from the build.

### "Why is this number what it is?"

Select the record and read the **detail** pane — the cycle laid out logically: the event that arrived,
each node that reacted, and what each one computed. Then **step through** the cycle on the Topology
tab to watch it propagate node by node, in the order it actually executed.

For a value over time rather than one moment, plot it: `node.key` becomes a series you can chart and
export. See [Graphs](graphs.md).

### "Is it happening again / is it still happening?"

Turn on **Follow** (toolbar, or **File ▸ Follow (tail)**) on a local log and new records append as they
are written, keeping your flags, filters and selection. For a fixed export, compare two logs by
opening each in turn — the fingerprint in a saved report will tell you if you have reopened a different
one than the report was written against.

## Handing the answer back

Two things make an answer reviewable by someone who was not watching your screen:

**Flag the record.** The toolbar **Flag** button writes a `note` (what is wrong and why it matters) and
a `fix` (the likely cause, or where to look) onto that specific cycle. It is the one place a finding is
written — it then appears in the records table, as a callout painted on the topology for that record,
and in any report you export.

**Export a report.** The **Reports** tab builds a document out of *references* — this record, this
chart, this focus — rather than a copy of what you saw, so it re-renders against the live log instead
of going stale. Export it as a PDF for the person who asked. Crucially, it carries the log's identity
in the header: reopen it later against a different log and it says so, up front, before showing you
anything. See [Investigation reports](reports.md).

!!! warning "Reports about a live system contain real names"
    A report is evidence about your production estate — hosts, services, instruments, accounts. Treat
    the PDF like any other production artefact. In particular, never paste one into a public issue
    tracker or repository.

## Working with an AI on the same window

If your team connects an assistant, it drives **this** analyser through the same actions you use — it
opens the log, moves the selection, focuses the graph, and you watch it happen. You are both looking at
one state, and neither of you wrote the evidence you are reading. See
[Connecting an LLM to the analyser](../connect-an-llm.md) and [Assistant](assistant.md).

---

!!! note "This page is a draft written from the tool, not from a shift"
    It was assembled by someone who knows the analyser and has never worked a support queue. The
    fastest way to make it correct is to walk one real case with it open and fix what is wrong —
    especially the four questions above, which are a guess at yours. If the real ones are different,
    they are the page.
