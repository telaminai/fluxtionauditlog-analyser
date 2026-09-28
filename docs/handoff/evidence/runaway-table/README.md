# Runaway records table — captured traces

**Controlled native captures are now available:** [native-repro-2026-09-28](native-repro-2026-09-28/README.md).
Opening the real Settings dialog during a native drag reproduced the runaway twice. It does not establish
the original walkthrough incident's trigger; that still needs its own capture.

## What a diagnosing reader gets, and does not get

| | status |
|---|---|
| the instrument | **built and verified** — `MouseTrace`, driven by `-Danalyser.mouseTrace` |
| the listener records real mouse events | **verified** — `MouseTraceTest#itRecordsAMouseEventThroughTheToolkit` posts through the event queue and sees the line |
| a trace from a controlled native runaway | **captured twice** — native Robot drag, Settings modal; original incident unconfirmed |

## Capturing one

Build the diagnostic branch first; the published 1.26.0 jar has no mouse trace.

```bash
java -Danalyser.mouseTrace="$PWD/docs/handoff/evidence/runaway-table/trace-<date>.log" \
     -jar target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar
```

Then reproduce: open a log, play a spotlight walk, and **click around the records table while steps land** —
pressing on the table as a step arrives is the shape that produced it. **Stop as soon as the table starts
scrolling on its own**, and commit the file here.

Native Robot input worked in the 2026-09-28 reproduction session. Verify delivery first on any other desktop;
permission or focus restrictions can make it unavailable. A component-dispatched synthetic event remains an
invalid substitute for native routing.

## Reading it

Format and field meanings: [`../../runaway-table-mouse-trace.md`](../../runaway-table-mouse-trace.md).

**One useful line** is `SUSPECT` — the table believes a drag is in progress while no observed button remains down.
Read *upwards* from it to the last `RELEASED`, which names the window and component that took the release. If
there is no `RELEASED` at all, record that limit. In particular, the ledger can retain the press forever,
suppressing `SUSPECT` even during a real runaway. The new probes also sample the timer and window events.

## Two things the first capture already taught us

A trace was left running for 75 minutes on 2026-09-28 while the app was driven **programmatically** over the
action socket. It recorded **no** press or release, correctly — nobody touched the mouse. But it produced three
false `SUSPECT` lines during a log open:

```
SUSPECT tableIsMidDragButNoButtonIsDown size=0 grew=false dragsSinceLastPress=0
```

Opening a log churns the selection model, so `valueIsAdjusting` goes true with no button down and an **empty**
selection. The rule now also requires `size > 0` and that a real press has been seen at some point, because a
runaway always has rows selected and always follows a gesture. Both are regressions
(`modelChurnWithAnEmptySelectionIsNotSuspect`).

**So: a `SUSPECT` line with `size=0` in any older trace is noise, not the fault.**
