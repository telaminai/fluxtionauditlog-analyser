# Runaway records table — captured traces

**There is no captured trace here yet.** This directory is where one goes when the reproduction happens.
A diagnosis cannot start before that: the whole point of the instrument is that the mechanism was guessed once
already and the guess was wrong.

## What a diagnosing reader gets, and does not get

| | status |
|---|---|
| the instrument | **built and verified** — `MouseTrace`, driven by `-Danalyser.mouseTrace` |
| the listener records real mouse events | **verified** — `MouseTraceTest#itRecordsAMouseEventThroughTheToolkit` posts through the event queue and sees the line |
| a trace from a real runaway | **not captured** — needs a person with a physical mouse |

## Capturing one

```bash
java -Danalyser.mouseTrace="$PWD/docs/handoff/evidence/runaway-table/trace-<date>.log" \
     -jar fluxtion-auditlog-analyser-1.26.0.jar
```

Then reproduce: open a log, play a spotlight walk, and **click around the records table while steps land** —
pressing on the table as a step arrives is the shape that produced it. **Stop as soon as the table starts
scrolling on its own**, and commit the file here.

`java.awt.Robot` is not a substitute: its input is dropped without Accessibility permission on the affected
machine, the same limit behind the focus-bound test skips.

## Reading it

Format and field meanings: [`../../runaway-table-mouse-trace.md`](../../runaway-table-mouse-trace.md).

**The one line that matters** is `SUSPECT` — the table believes a drag is in progress while no button is down.
Read *upwards* from it to the last `RELEASED`, which names the window and component that took the release. If
there is no `RELEASED` at all, that is itself the answer.

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
