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

**Then the recorded run, with its replay records.** The sender opens a run recorded with a replay writer and asks
for a bundle with its replay records. The analyser first refuses, by name:

- replay records that do not match the open log (with the longer DEMO series log open);
- a time window with replay records.

Then it writes the whole recorded run, format 2, carrying the run's 7 recorded inputs.

**The recipient** starts cold, with its own project and no source roots, and receives only the two good `.fexp`
files. For each, it:

1. verifies the bundle; for the excerpt, `--verify` must say which records it holds;
2. unpacks it and opens it: `--unpack`, then the three opens;
3. plays the walk and takes a screenshot of each step. Every target must be current and lit, and the breach record
   must be row 7 of the whole log and row 3 of the excerpt;
4. goes back to its own project.

For the recorded run it also compiles two builds of the processor from the committed DEMO sources: one as it is, and
one with the risk limit raised from 2 to 3. It then:

1. verifies it: `--verify` must state the replay limit, never *no replay*;
2. replays it into the first build with `tools/replay/ReplayBundle.java`, and `--replay-compare` must say
   **AGREES, 8 of 8**;
3. replays it into the changed build: the same graph, so it replays, and `--replay-compare` must say **DIVERGES at
   record 6**, naming the risk monitor entry that build never writes;
4. offers the runner a build carrying another processor's graph, which it must refuse before running anything;
5. opens the bundle and plays its walk.

Then it checks:

- that each received file is unchanged;
- that its own project profile is byte-identical;
- which of its machine settings changed. It lists them: recents and last-opened paths only.

The replay leg adds about 0.4 s to replay and 0.1 s to compare. A typical run on one machine: from a received file to the walk's last step in about **0.6 s** for the whole log and
**1.2 s** for the excerpt, plus about 2.5 s to start the analyser. Results, the timings and the screenshots go to
`/tmp/fluxtion-evidence-demo/results.json` and `…/recipient/shots/`.

The demo **pins the recipient's window at 1440×900**. At a fresh install's default size (1200×800) a chart step
reports *"no room — widen the window"*; the fix is to widen the window, not to change the layout.
`--default-window` leaves the window at the default, to show it.
