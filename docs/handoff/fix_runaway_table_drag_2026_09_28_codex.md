# Runaway table drag: controlled reproduction and fix

**Verdict: a reproduced window-focus-loss defect is fixed and regression-protected. The original demo's
trigger remains unconfirmed.** Nothing has been merged or released by this work.

The evidence is [preserved here](evidence/runaway-table/native-repro-2026-09-28/README.md), including predictions,
two before captures, the interrupted attempt and its valid repeat, probe sources, and control/test summaries.

## Reproduced failure and correction

1. **RAN — lost table gesture.** Hold a native table drag outside its viewport until autoscrolling starts;
   schedule the real Settings action; release after Settings takes focus. On the diagnostic baseline the
   selection grows from 16 to 24 after release while the viewport moves by 160 pixels. A fresh process repeats it.
   `MainFrame.windowLostFocus` now calls `LogTablePanel.cancelMouseGesture`, which stops Swing's Autoscroller
   through its public setting, restores the setting for the next gesture and ends selection adjustment.
   It neither synthesizes mouse input nor clears selection. `TableDragCancellationFrameTest` fails before
   the fix and checks actual continued scrolling, selection stability and a working next native drag.
   Controls: `mouse-loss-table-hook`, `mouse-loss-table-timer`.
2. **RAN — corresponding slider lifecycle.** A native time-slider edge drag starts its own timer. A modal
   can leave that request active too. The same window boundary calls `TimeRangeSlider.cancelMouseGesture`:
   stop the timer and clear drag mode, without changing/publishing another time range. The second native
   regression fails before the correction and then verifies stable endpoints. Control: `mouse-loss-slider-hook`.

This is a small Swing input-lifecycle change in three classes, not a second session dispatch system.
No generated processor or session verdict changed. Both CI frame lists register the new class.

## Evidence versus inference

**RAN:** native Robot input works on this desktop. The controlled Settings interruption failed twice before
and succeeded three times after. Nine ordinary/overlay/walk controls also succeeded after the fix.
The attempt covered by another application had 12 inconclusive gestures and zero valid gestures; it is retained
and excluded from the passing count. Eighteen slider/walk combinations before the fix did not reproduce the
reported incident. They are negative observations, not proof that every such interleaving is safe.

**READ:** JDK 21 `JComponent`, `Autoscroller`, `Container.LightweightDispatcher` and
`EventDispatchThread` support the timer and routing explanation. No release appeared even in a pre-filter
queue observer during the failure. The native/OS reason is not established; blaming the Java modal filter
or the glass pane would exceed the evidence. MouseTrace's button ledger can stay down forever when a release
never arrives, so absence of `SUSPECT` does not clear an incident.

The permanent tests use OS-routed mouse input. Settings is opened deliberately through its actual menu action
while held. That is a controlled boundary test, not a claim that this exact sequence happened in the demo.

## Verification

| Check | Total | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Headless baseline | 2705 | 0 | 0 | 150 |
| New native regressions before fix | 2 | 2 | 0 | 0 |
| Final clean package | 2707 | 0 | 0 | 152 |
| New and neighbouring display suites | 19 | 0 | 0 | 0 |

Final package: 361 source-mapped reports, no orphans. The final documentation recheck initially had
29 socket-permission errors inside the sandbox; the permitted retry was 2707 / 0 / 0 / 152, no orphans. Three targeted mutation controls caught with named
assertion failures, source/class byte restore and restored-green runs. Harness Python tests 5 green;
preflight 30 frame suites / 357 anchors; strict docs build clean. Exact commands, failed harness attempts,
public-data handling and per-suite counts are in the evidence README.

## Still open

- A trace matching the owner's original walkthrough/slider incident. The correction covers loss of window
  focus; it does not claim to fix a lost release with no such event.
- Linux/Xvfb CI and the full registered display/mutation lists were not run locally.
- No native screenshot/visual-design claim was made. No keys, providers or participant data were accessed.
