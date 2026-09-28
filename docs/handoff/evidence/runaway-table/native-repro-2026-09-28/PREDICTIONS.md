# Window-loss gesture cancellation: predictions

2026-09-28, before the correction. Baseline: diagnostic branch `e225897e`.

## Already observed, not predictions

On macOS with JDK 21, Robot delivered native press/release events. Two controlled runs opened the real
Settings dialog during an active table drag. After native release, the table kept scrolling and selecting.
The second run started a fresh process. Neither the diagnostic listener nor the second probe's pre-filter
event-queue observer saw that release. The main window lost focus as Settings opened.

The Settings opening was scheduled by the probe, not triggered by the owner during the captured demo.
This establishes a mechanism, not the cause of the original incident. No tooltip explanation is established.

Eighteen time-slider/walk combinations and nine follow-up table drags did not reproduce the symptom.
The slider has a separate edge-pan timer, so table timer cancellation alone would leave that lifecycle open.

## Predictions to test

1. A native drag followed by the real Settings dialog will fail the new frame assertion that losing window
   focus ends the table's adjusting gesture. Native input availability must be established first, or the test
   explicitly skips; it must not inject a release directly into the table.
2. Cancelling via JTable's public autoscroll setter will stop its timer, while restoring the configured
   autoscroll flag will allow the next normal drag. Selection intervals must be retained, with adjustment ended.
3. The same window-loss boundary must stop the time slider's edge timer and clear its drag mode without
   publishing a new time range. A normal native slider release must still work.
4. Controls removing the window hook, the table timer stop or the slider timer stop should each fail their
   corresponding named regression assertion; sources/classes must be restored byte-identically and rerun green.
5. Repeating the original controlled native probe against the fixed jar should report no running table timer
   after Settings opens. The ordinary, spotlight and time-slider gestures must remain usable.

This is Swing input/timer cancellation, not a new session verdict or a replacement dispatch model. No session
processor regeneration should be needed. Full counts will be measured, not inferred from the earlier release.
