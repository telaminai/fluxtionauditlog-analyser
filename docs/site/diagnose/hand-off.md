# Hand the diagnosis on

A chart answers *"this trend is wrong"*. The person who asked usually needs more: *"**this cycle** is wrong, here is
why, and here is how you can check"*. There are three ways to hand a diagnosis on, from lightest to fullest.

| You want them to | Hand on | They need |
|---|---|---|
| see the finding next time anyone opens the log in this project | a **flag** with a note and a fix | the same log, in the analyser |
| read the account, with the evidence beside it | a **report**, rendered to PDF | a PDF reader |
| reopen the whole investigation and check it themselves | an **evidence bundle** (`.fexp`) | the analyser |

## Write the finding: a flag

A flag is the **one place a finding is written**. It then appears in the records table, as a callout on the
topology for that record, and in every report that cites it.

- **Yourself:** select the record, then **Records ▸ Write a finding for this record…**. You write a note (what is wrong
  and why it matters) and a fix (the likely cause, or where to look). The toolbar's **Flag** button and the **F** key
  only flag or unflag the selected rows; they write no note.
- **From your client:** `analyser_flag` with `note` and `fix`. Sending only one of them keeps the other, so adding a
  fix cannot wipe the explanation it is a fix for. Use `kind: "confirmation"` for behaviour that is correct; a report
  then labels it *Observation / Assessment* rather than as a fault.

Flags belong to the loaded log: opening another log clears them, and they do not travel in an evidence bundle. A
finding that must travel goes into a report, or into a walk step's caption.

## Write it up: a report

A report is the **account** of an investigation: an ordered list of references to records, findings, charts,
topology focuses and tables, with connective narrative. It never holds its own copy of the evidence, so opening it
again re-reads the records and re-runs the queries against the loaded log, and it says up front when that log is not
the one it was written against. [Investigation reports](../user-guide/reports.md) covers the Reports tab and every
section type.

A client composes the verbs it already has. For one record, the single-record form writes the finding, the event, the
node log, a picture of the topology and an optional chart to a PDF. An illustrative sequence:

```text
⏺ analyser_flag(recordIndexes: [40],
               note: "quotePublisher republished before riskMonitor re-evaluated the limit,
                      so the quote on the wire was priced against stale risk state.",
               fix: "riskMonitor must be upstream of quotePublisher — check the @OnTrigger ordering")

⏺ analyser_goto(recordIndex: 40, reveal: true)

⏺ analyser_topology(select: "quotePublisher", scope: "routes", focus: true)
  ⎿ {"ok":true,"topology":{"visibleNodes":7,"totalNodes":20,"callout":true,
      "finding":{"note":"quotePublisher republished before …","fix":"riskMonitor must be …"}}}

⏺ analyser_report(path: "finding-40.pdf", title: "Stale risk state at record 40",
                 graph: "Mid price")
  ⎿ {"ok":true,"wrote":{"path":"…/finding-40.pdf","recordIndex":40,
      "hasExplanation":true,"hasFix":true,"topology":true,"graph":"Mid price"}}
```

- `topology` echoes back the finding it is showing, so the client can confirm the graph displays its diagnosis rather
  than assume it. Its `callout` field only switches the callout on or off: the text always comes from the record's
  flag.
- **`scope: "routes"` on a node that everything feeds comes back bounded, and says so.** On a large graph where every
  route would cover more than half of it, the answer stops at three hops each way, and the echo carries
  `routeBound: true` and a `scopeNote`. Pass `routeBound: false` for every route; it is the same switch as the
  **≤3 hops** checkbox on the Topology tab.
- For more than one record, the investigation form, `report {name, sections}`, builds a named report you can keep,
  re-open and render with `path`. A finding section renders what `flag` wrote, and the report verb cannot change that
  text.

Writing a PDF needs *Allow assistant file exchange* (*Settings ▸ Assistant*, off by default). The file is written only
inside the exchange directory you chose, and never over an existing one. A PDF you export yourself, from
**Records ▸ Export finding to PDF…** or the Reports tab, goes where you choose in the dialog.

## Send the investigation: an evidence bundle

An **evidence bundle** packs the log, its graph, your saved charts, focuses, reports and walks, and optional notes,
into one `.fexp` file the recipient verifies before they open it. The running analyser writes it, because only the
live session knows whether a copy would be coherent:

```text
report {bundle: {path: "breach-0900.fexp", notes: "# The 09:00 breach\n\nThe spread moved first."}}
```

Capture is refused unless *Allow assistant file exchange* is on. `context.capture` says when the bundle is written,
with its identity, and what was left out or redacted. For a large log, `from` and `to` pack only the records in a time
window. [Sending an investigation](../evidence-bundles/sending.md) has the details;
[Opening a bundle you were sent](../evidence-bundles/opening.md) is the recipient's side.

Flags do not travel in a bundle. A walk step with a caption on the record says the same thing, and it does.

## Show it: a walk or a reel

To show the reasoning rather than only state it, save the explanation as a **spotlight walk**: numbered steps that
anyone who opens the project, or the bundle, replays on the analyser itself. A **reel** turns a walk into one HTML page
that opens anywhere, for a reader who does not have the analyser
([Save it as a walk](../user-guide/assistant.md#save-it-as-a-walk), [Send it as a reel](../user-guide/assistant.md#send-it-as-a-reel)).

!!! warning "Reports and bundles about a live system contain real names"
    They are evidence about your production estate: hosts, services, instruments, accounts. Treat them like any other
    production artefact, and never paste one into a public issue tracker or repository.
