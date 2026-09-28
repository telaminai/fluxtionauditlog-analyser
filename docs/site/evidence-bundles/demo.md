# Try it with the demo

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

## What it does

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

`--default-window` leaves the window at a fresh install's size, which shows the one known rough edge: the chart
step reports *"no room — widen the window"*.
