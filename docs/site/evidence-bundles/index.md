# Evidence bundles

An **evidence bundle** is one `.fexp` file that carries an investigation to someone else: the audit log you read,
the graph you read it against, and the charts, [reports](../user-guide/reports.md) and spotlight walks you saved.
The person you send it to opens it in their own analyser, from a fresh copy, and plays your walk. Every record,
chart and graph target is lit as you saved it, because the bytes are the same.

It is built for one job: **showing what you found**.

## Two limits, stated everywhere

Every command and operation that reports on a bundle states them:

- **Unsigned.** Verification shows that nothing in the bundle has changed since it was packed. It does not show
  who packed it. If the sender gives you the bundle's *identity* line by another route, comparing the two is what
  ties the file to them.
- **No replay, or replay only on your build.** An ordinary bundle shows an investigation. It does not re-run the
  processor, reproduce the incident or fix anything, and says *no replay*. A bundle that also carries the run's
  replay records (format 2) states a narrower limit instead: the records reproduce the log only on a build whose
  graph matches, and only as far as the processor reads nothing the records do not carry. You replay them yourself,
  on your own build; the analyser never runs them
  ([Commands and file format](reference.md#the-manifest-with-replay-records-format-2)).

## What is inside

| member | what it is |
|---|---|
| `manifest.json` | every member's path, sha256 and size, plus the limits. Its sha256 is the bundle's **identity** |
| `log/<file>` | the whole log, byte for byte, or an **excerpt** of it, stated in the manifest |
| `graph/<file>.graphml` | the graph that was open, if one was |
| `profile/project.fluxtion-settings` | saved charts and named focuses, reports and walks, hidden columns |
| `notes/NOTES.md` | your account, when you gave one |
| `replay/<file>` | the run's replay records, only in a format 2 bundle captured with `replay` |

## What does not travel, and why

| left behind | why |
|---|---|
| **flags** | they are not exported or packed: on your machine only local session recovery keeps them, for the same unchanged log. Put a finding in a walk step's caption or a report's narrative instead: a report's *finding* section shows a flag, so on the recipient's side it shows as unresolved, naming the missing flag |
| **a chart with an external CSV series or markers** | its data is a file on your machine, so its path would point at nothing on theirs. It is left out and named |
| **source roots, Maven repos, processors, runbooks, environments, keys, assistant settings** | these describe your machine. Leaving all of them out means nothing can quietly point somewhere else on the recipient's machine |

So on the recipient's side **source navigation is absent**, by design, while the walk, the report and the charts
all work.

## Where to go next

- [Sending an investigation](sending.md): one operation on the running analyser, whole log or a time window.
- [Opening a bundle you were sent](opening.md): verify it, open it and play the walk.
- [Commands and file format](reference.md): the capture operation, the three commands, and the manifest.
- [Try it with the demo](#try-it-with-the-demo), below: the whole round trip on the DEMO log, in one command.

To see a bundle you were sent without making one, download the DEMO journey
[Find the first recorded breach](../journeys/find-the-first-recorded-breach.md): it is a bundle, with its identity
published beside it.

## Try it with the demo

`tools/evidence-bundle-demo.py`, in the analyser's repository, runs the whole round trip on the DEMO log with two
real analysers. It is both a demo and a test: every step prints `PASS` or `FAIL`, and the script exits non-zero
on any failure.

```
mvn package                                   # the jar the demo runs
python3 tools/evidence-bundle-demo.py         # sender, capture, cold recipient, walk; both analysers stopped
python3 tools/evidence-bundle-demo.py --keep  # leave the recipient's analyser open on the walk, to look at
python3 tools/evidence-bundle-demo.py --open received.fexp   # recipient only, on a bundle you were sent
```

It needs a display. Both analysers run under their own isolated home, on separate paths under
`/tmp/fluxtion-evidence-demo/`, so nothing on screen comes from your own settings.

### What it does

**The sender** opens a DEMO project, log and graph, then saves:

- a chart;
- a chart with an external CSV series, which is expected to be left out;
- a report;
- a three-step walk: where every price enters, the breach record, and the spread before it.

Before any log is open it asks for a capture, which must refuse. Then, through `report {bundle}`, it makes three:

- the **whole log**, with notes;
- an **excerpt**, records 4..8, which hold the breach: the walk and the report are re-based onto it;
- an excerpt that **misses** the breach: the walk and the report must be left out and named.

**The recipient** starts cold, with its own project and no source roots, and receives only the two good `.fexp`
files. For each, it:

1. verifies the bundle; for the excerpt, `--verify` must say which records it holds;
2. unpacks it and opens it: `--unpack`, then the three opens;
3. plays the walk and takes a screenshot of each step. Every target must be current and lit, and the breach record
   must be row 7 of the whole log and row 3 of the excerpt;
4. goes back to its own project.

Then it checks:

- that each received file is unchanged;
- that its own project profile is byte-identical;
- which of its machine settings changed. It lists them: recents and last-opened paths only.

A typical run on one machine: from a received file to the walk's last step in about **0.6 s** for the whole log and
**1.2 s** for the excerpt, plus about 2.5 s to start the analyser. Results, the timings and the screenshots go to
`/tmp/fluxtion-evidence-demo/results.json` and `…/recipient/shots/`.

The demo **pins the recipient's window at 1440×900**. At a fresh install's default size (1200×800) a chart step
reports *"no room — widen the window"*; the fix is to widen the window, not to change the layout.
`--default-window` leaves the window at the default, to show it.
