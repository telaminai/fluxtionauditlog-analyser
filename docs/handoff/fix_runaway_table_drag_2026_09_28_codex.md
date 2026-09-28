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

## PR 62 independent-review response

The earlier verification table records `66f9fddf`, before the review corrections. See the
[correction evidence and sealed predictions](evidence/runaway-table/review-fixes-2026-09-28/README.md).

R1 is addressed by asserting cancellation while the button is still held, before any native release can make
an unfixed test green. O3 now binds the expected slider endpoints to the focus-loss event before cancellation.
O2 registers the autoscroll-restore and column-adjustment controls. For O1, cancellation is restricted to an
owned modal; non-modal focus loss and resumed drag steps keep selection notifications deferred until release,
which a new native test witnesses. The original Settings reproduction remains the product boundary covered.

The full CI workflow now triggers for the stacked diagnostic base as well as main. Its result must be checked
at the pushed head; local evidence and the other reviewer's results cannot substitute for that run.


## Release integration (1.26.1 preparation)

The independent re-review accepted R1 and O1–O3 at `5d790275`. Full CI run `36447058069` reported build
2708 / 0 / 0 / 153, display 151 / 0 / 0 / 0, and 360 controls caught exactly once across four shards.
The six mouse controls failed at their named assertions on Linux, including the pre-release checks.

PR 62 is merged locally into the diagnostic stack before PR 61 goes into main, using the personal identity.
The temporary diagnostic-base CI trigger is removed as the stack lands; main retains the full CI gates.
The release must wait for CI on that final main tree. Cancellation remains limited to an owned modal taking
focus: switching to another application does not cancel a drag, and the original incident remains unconfirmed.
