# Spec — baselines: "is this normal here?"

**Status:** PROPOSED 2026-08-27 (owner-directed: split out of M38 open question 4).
**Milestone:** M39. **Tracker:** [tracker.md](tracker.md) ▸ M39. **Depends on:** M37 (visible), M38 (portable).

## The proposition

The question a support engineer cannot answer about a system they did not build, and the one a
deterministic record uniquely can: **is what I am looking at normal for this system?**

Everything the analyser does today answers *what happened*. Nothing answers *whether that is unusual*.
A support engineer holding a log sees `riskMonitor` logged 4 times, `spreadCalculator` never, a spread
of 0.021 — and has no way to know which of those is remarkable. The person who could tell them is the
one they are trying not to wake up.

That gap is the whole reason this milestone exists, and it is disproportionately a **support** feature:
the author of a system carries the baseline in their head.

## D-N1 — a baseline is a NAMED REFERENCE RUN, never an abstract "normal"

The tempting shape is a statistical model of normality. It is the wrong one, and the reason is this
project's own discipline: an abstract "normal" is unfalsifiable authority. Nobody can check it, nobody
can regenerate it, and when it disagrees with reality there is no way to tell which is wrong.

So a baseline is **derived from specific recorded runs, and says which**. It carries the provenance,
the record count and the time range it was built from, exactly as a report's fingerprint does. Every
statement it supports is then of the falsifiable form:

> *"in `nightly-2026-08-20` (5,821 records, prod), `riskMonitor` logged in every cycle; in this log it
> logs in none."*

Not *"riskMonitor is behaving abnormally."* The first is evidence a stranger can check; the second is
the tool asserting a judgement it cannot support.

## D-N2 — the baseline is a DERIVED SUMMARY, not the reference log

A pointer to the reference log (the D-C2 shape) fails here: the log is large, often not portable, and
frequently the very thing you cannot get hold of. So the artefact is a **small derived summary**
generated *from* a log and stored in the project — inert, diffable, reviewable, and readable without
the log it came from.

What it captures maps onto machinery that already exists rather than inventing a second analysis:

| Fact | Built from | Already exists as |
|---|---|---|
| which nodes log at all, and how often | the log ∩ the graph | `NodeCoverage` |
| the event mix and rates | the log | `SummaryRow` (count, span, rate/min) |
| the value range each `node.key` took | the log | the series/expression engine |
| which nodes normally log **together** in one cycle | the log | the cycle model behind step-through |

It is regenerable by construction: *"rebuild this baseline from that log"* must always be available, or
the summary becomes a fact nobody can re-derive — the failure D-N1 exists to prevent.

## D-N3 — a comparison states TWO facts; it never renders a verdict

The output of comparing a log to a baseline is a **difference**, phrased as two measurements:

```
riskMonitor        baseline: logged in 5,821 of 5,821 cycles   ·  this log: 0 of 412
quotePublisher.spread   baseline: 0.0197 – 0.0203              ·  this log: 0.0210 – 0.0244
OrderUpdateEvent   baseline: 41/min                            ·  this log: 0/min
```

No severity, no colour-coded alarm, no "anomaly" label. The analyser knows two numbers; the reader
knows the system. This is the same refusal that makes `coverage` decline an inferred graph and the
topology decline an ordinal badge under `PARTIAL` ordering — and it is what keeps the feature useful
after the first false alarm, which is when a scoring tool becomes something people ignore.

**Inherited caveats are inherited, not restated as new facts.** "Did not log" is still not "did not
run" (M34.2), and a node the graph shows as silent by construction is silent in both columns and must
not be listed as a difference at all (M40.2, when it lands).

## D-N4 — one baseline per environment, because "normal" is environment-shaped

UAT is quieter than prod, and a baseline taken from one says nothing useful about the other. Baselines
are therefore keyed by the **environment** M38.3 declares, and a comparison names which baseline it
used and why it chose it — the same `provenanceSource` discipline: *"compared against `prod/nightly`
because this log's provenance is prod"*.

With no environment declared and one baseline present, it is used and says so. With several and no way
to choose, the analyser **asks rather than guesses** — an unlabelled comparison against the wrong
environment is the "right about UAT, read as production" failure with extra steps.

## D-N5 — it is offered where the question is asked, and it is never automatic

A comparison runs when someone asks for it: a `baseline` verb (compare / build / list), the Project
panel's own row, and a report section. Opening a log does **not** silently compare it — a load-time
verdict trains people to skim past the one that matters, and this project has spent M35–M37 removing
things that fire at load.

The Project panel states what is in force (*"baseline: prod/nightly — built 2026-08-20 from 5,821
records"*) and, per M37 D-L3, offers to run the comparison rather than running it.

## D-N6 — a baseline travels as a fact; the log it came from does not

Tier 1 in D-C1's model: inert, shareable, and its own `SettingsShare` category — *"Baselines (derived
summaries of reference runs — never log data)"*. The distinction matters and the label must carry it: a
summary holds node names, key names, counts and ranges; it must never carry record text, event payloads
or anything a `nodeLogs` value said. That is the same line M33 drew for reports, and the same one D-C6
draws for destinations.

## Non-goals

- **Not anomaly detection, alerting or scoring.** No thresholds, no severities, no learned model.
- **Not a replacement for tests.** A baseline says what a run looked like, never what it should be.
- **Not automatic.** Nothing compares on load.
- **Not a second analysis engine** — it reuses coverage, summary and the expression engine.

## Open questions (owner)

1. **Where does a baseline live?** In the profile (portable, one file) or a pointed-at JSON in the repo
   (reviewable, diffable — the answer given for vocabulary)? Proposed: **pointed-at**, for consistency
   with M38's answer, since a baseline is exactly the kind of artefact a team should review changes to.
2. **Ranges, or distributions?** min/max is honest and cheap; percentiles are more useful and invite
   the statistical framing D-N1 rejects. Proposed: min/max plus count, and stop there until asked.
3. **Should a baseline be buildable from a ROLLED SET** (M30) rather than one file? Probably yes —
   "normal" over a week beats normal over an hour — but it multiplies the build cost.
4. **Multiple baselines per environment** (nightly vs peak-hours)? Proposed: allow, name them, choose
   explicitly; never blend, because a blended baseline is an abstract normal by the back door.

## Acceptance

- [ ] A baseline names the runs it came from — provenance, record count, time range — and can always be
      rebuilt from them (D-N1, D-N2).
- [ ] A comparison prints two measurements per line and no verdict; a test asserts no severity,
      "anomaly" or threshold vocabulary appears in its output (D-N3).
- [ ] Nodes silent by construction never appear as a difference; "did not log" is never rendered as
      "did not run" (D-N3).
- [ ] A comparison names the baseline used and why it was chosen; with several candidates and no
      environment, it asks (D-N4).
- [ ] Nothing compares on load; the panel offers, never runs (D-N5).
- [ ] Own share category, labelled to say it carries no log data; a test asserts no record text can
      reach a baseline file (D-N6).
- [ ] Docs page under *The analyser*, with a generated screenshot; CHANGELOG; tracker.

## Slices

- **M39.1** The baseline artefact — build from a log, name its source runs, store and rebuild (D-N1/2).
- **M39.2** The comparison — two-measurement output, inherited caveats, the no-verdict test (D-N3).
- **M39.3** Environment keying and choice, including the ask-rather-than-guess path (D-N4).
- **M39.4** Surfaces: the `baseline` verb, the Project panel row, the report section (D-N5).
- **M39.5** Share category, docs, CHANGELOG, tracker (D-N6).

## Why this is the support milestone

Every other milestone helps whoever holds the question. This one helps whoever holds it **without the
context to interpret the answer** — which, on the evidence of who is using this tool most enthusiastically,
is the larger group. It is also the only feature on the roadmap that a competitor without a deterministic
record cannot copy: you cannot baseline "which nodes ran" if you were never able to say which nodes ran.
