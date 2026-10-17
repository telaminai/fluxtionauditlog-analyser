# Check your criteria against the evidence

You stated what the application should do as acceptance criteria. This page shows how to check each criterion
against a recorded run with what the analyser offers **today**, and what is **planned**.

A check answers one question: *does this criterion hold on these recorded runs?* It does not show that the criterion
is the right one, that your criteria are complete, or that the application behaves the same on inputs you did not
record. Those judgements stay with you.

!!! info "Today the analyser computes; you, or the AI, conclude"
    The analyser has no form for an acceptance criterion yet. Today you turn each criterion into a question the
    analyser **computes** from the log (a count, a crossing, a coverage ledger), and you or the AI read the answer.
    A criterion the analyser evaluates itself is [planned](#planned-acceptance-checks).

## 1. Write the criteria down

Write each criterion as an observable result, before the run, in words that name what the log will show:

- *"Every `OrderPlaced` event is followed by exactly one `OrderAck`."*
- *"No `RiskBreach` is logged after the first `QuoteSuspended`."*
- *"`quoter.spread` stays between 0 and 5."*

A criterion about something the processor does not log cannot be checked from its log. Agree the audit fields first:
separate numeric or boolean keys, such as `quoter.spread`, can be counted and charted, while a number inside an
object's `toString()` cannot ([Producing an audit log](../producing-a-log.md)).

Keep the list where it travels with the project: a runbook in the repository, or a `narrative` section at the top of a
report. A narrative is labelled *"the author's account, not log evidence"* on screen and in the PDF, which is right
for a criterion: it is a statement of intent, not a result.

## 2. Turn each criterion into a computed answer

Each kind of criterion has a verb that computes its answer over the whole log, never over a sample of records:

| Criterion | Ask the analyser for | Verb |
|---|---|---|
| *How many* times something happened | the count, with the first and last record it counted | `aggregate {metric: count, filter: {dimensions: ["OrderAck"]}}` |
| A value stays inside a range | where the value **enters** a region, each crossing anchored to its record | `series {expr: "quoter.spread", crossings: {above: 5, below: 0}}` |
| A relation between two values | the same, over a formula | `series {expr: "ask.price - bid.price", crossings: {below: 0}}` |
| Which nodes logged on this run | the coverage ledger: covered, uncovered and excluded nodes, with the reasons | `coverage` |
| Values in particular records | the named fields of those records | `read {recordIndex, fields}` |

In the analyser itself the same questions are a chart with a guide line or a condition band
([Graphs ▸ Thresholds and condition bands](../user-guide/graphs.md#thresholds-and-condition-bands)), the
Summary tab's counts, and the Topology tab's coverage ([Reading coverage results](../user-guide/topology.md#reading-coverage-results)).

Ask the AI for these by name. *"Check criterion 2: use `series` crossings on `quoter.spread`, not a sample of
records"* gets a computed answer; *"does the spread look right?"* may get a reading of twenty records.

## 3. Record each result as a finding

Mark the record that settles a criterion with a **flag**. A flag of kind `confirmation` is labelled **Observation** and
**Assessment**, for a criterion that holds; the default kind, `fault`, is for one that fails
([Investigation reports](../user-guide/reports.md#references-never-a-document)).

Flags are session state: they last until another log is opened. A finding that must last goes into a report.

## 4. Assemble the account: a report

A report keeps the check. Put the criteria in a `narrative` section, then one section per criterion:

- a `finding` section for each flagged record;
- a `table` section that stores the **query**, not its rows: an `aggregate`, a `series` with crossings, `coverage`, or
  a `read`, with an optional row rule such as `rowWhen: "quoter.spread > 5"` printed under the table;
- a `chart` section for a chart with its guides and bands.

Because a report stores references and queries, **opening it again re-runs every check against the log open now**.
That makes the same report a check you can repeat: record a run, open the report, change the code, record the same
inputs again, open the same report against the new log. A report opened against a different log names the difference
before any section renders, and a reference that no longer resolves is reported, never dropped
([Evidence re-renders live](../user-guide/reports.md#evidence-re-renders-live)).

A report always shows the log open now. To keep an earlier run's numbers, export the report's PDF while that run's log
is open.

## 5. Show it, or send it

- **A walk** steps a reviewer through the evidence, one spotlight at a time: the criterion, the record, the chart
  ([Spotlights, walks and reels](../teach/spotlights-walks-reels.md)).
- **An evidence bundle** carries the log, the graph, the report and the walk to someone else, who opens the same
  bytes in their own analyser ([Evidence bundles](../evidence-bundles/index.md)).

## What other checks show, and what they do not

| Check | What it shows | What it does not show |
|---|---|---|
| **Producer findings** (*Sources ▸ Open producer diagnostics…*) | The Spring XML design validates, and what the build reported ([Spring authoring](../spring-authoring.md#view-design-and-producer-findings)) | Anything about how the application behaves |
| **Coverage** | Which nodes logged on this run | That a silent node did not run: a node can run and log nothing at its audit level |
| **Replay compare** (`--replay-compare`) | That your build, fed the bundle's recorded inputs, writes the same log, record by record ([Evidence bundle format](../evidence-bundles/reference.md#-replay-compare-comparing-a-replayed-log)) | Whether a *changed* build is correct. A fix that changes behaviour diverges by design; the first difference it names is useful context, not a verdict |
| **A passing build or a plausible chart** | That it compiled, or that a line looks right | That any criterion holds |

**"No failure" is not "covered".** An empty crossings list means the value never entered that region on this run, or
that the key was never logged. Check that the series has points (`series` reports them as `points`) before reading an empty
result as a pass. A criterion that the recorded inputs never exercised has not been checked.

## Planned: acceptance checks { #planned-acceptance-checks }

!!! warning "Planned, not built"
    Nothing in this section ships today. It describes a design under review, and its details may change.

The authoring journey being designed would let you write each acceptance criterion in a small declarative form:
counts, "none after", "for each … then exactly one …", and value ranges, over the records of a run. The analyser,
never a model, would evaluate each criterion and show it as **passes**, **fails** or **not decided** on a run before
a change and a run after it, and carry the result into reports and evidence bundles. An AI could propose criteria;
you would confirm each one. What such a check would claim stays the same as today: *these criteria pass, or fail, on
these recorded runs.*
