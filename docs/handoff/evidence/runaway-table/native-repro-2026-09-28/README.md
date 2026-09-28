# Native drag interruption: reproduction and correction

2026-09-28; macOS; Corretto JDK 21.0.8; demo fixtures; isolated user home.
Baseline diagnostic branch: `e225897e`. Predictions: `d4d4b3ae`, before the correction.
The first discovery probes preceded those predictions and are explicitly observations.

## Finding and limits

**RAN:** native table drag → programmatically invoke the real Settings menu action while held → main frame
loses focus → send native release → table continues scrolling and extending selection. Reproduced in v2,
then repeated in a fresh process in v3. The invocation of Settings is a controlled intervention, not a claim
that the owner did this during the demo. The owner suggested walks and the time slider; those trials did not
reproduce the original incident.

`native-scenarios-v3.txt` is the shortest before capture:

- 12:15:45.741: native press reaches the main frame's queue.
- 12:15:46.288: table Autoscroller active, selection adjusting.
- 12:15:46.569: main frame loses focus; Settings gains focus and shows.
- 12:15:46.793: Robot has sent the release, but neither the pre-filter queue observer nor MouseTrace saw it.
- 12:15:47.861: selection grows from 16 to 24; viewport moves from y=900 to y=740; timer remains active.

There is **no observed recipient** for that release. Even observation before Java event filtering saw none.
Do not attribute it specifically to a Java modal filter or to a tooltip. The OS/native bridge boundary
remains unlocated. No `SUSPECT` is expected here: MouseTrace's observed-button ledger never saw the release.
`popupShowing` alone is insufficient when no mouse event arrives after the dialog opens; v3 adds window events.

**READ:** JDK 21 `JComponent.processMouseEvent` normally stops Autoscroller on release;
`JComponent.setAutoscrolls(false)` also stops it, and restoring true does not restart the timer.
`Autoscroller.actionPerformed` repeats the stored drag without going back through the event queue.
`Container.LightweightDispatcher.isMouseGrab` explains why raising a glass pane does not steal the
release from a held table gesture. `EventDispatchThread.pumpOneEventForFilters` gets the queue event before
filtering: the absence at our earlier observation point leaves that filter theory unproved.

## Preserved trials, including invalid attempts

| Capture | Result |
|---|---|
| native-input.txt | One native press, release and action on a disposable button: native input works here |
| native-scenarios.txt | 18 attempted; 13 started the timer, 5 invalid due to a remaining popup; no runaway in valid trials |
| native-scenarios-v2.txt | Cleanup added: 10 valid; ninth normal control completed, Settings reproduced on the tenth |
| native-scenarios-v3.txt | Fresh process, Settings only: 1 valid, 1 runaway; pre-filter and window observations added |
| slider-scenarios.txt | 18 slider/walk combinations and 9 follow-up table drags; no runaway; some walk presses dismiss the overlay rather than begin a slider drag |
| native-scenarios-fixed.txt | Another application covered the analyser: **0 valid / 12 inconclusive**, not passes |
| native-scenarios-fixed-retry.txt | **12 valid / 0 inconclusive / 0 runaway**: Settings, ordinary, overlay, walk step, three each |

The first two discovery harness versions evolved during investigation; their outputs are preserved, not
presented as reproducible from the final source byte-for-byte. `RunawayNativeProbe.java` is v3;
`RunawayNativeProbeAfter.java` additionally closes the Settings dialog between repetitions so successful
fixed runs can continue. Probe sampling delays provide observation windows, not production synchronization.
The permanent regression instead uses bounded state waits and explicit EDT timer turns.

The trace files are unchanged except the START line's scratch destination is replaced by `<scratch>/…`.
No event source, timestamp, selection, timer or result line was rewritten. `controls-summary.json` and
`test-counts.json` are extracts of the runner JSON and Surefire XML, not raw logs.

## Repeating the bounded jar probe

Build with JDK 21. From the repository root, choose an empty scratch directory and isolated home. Compile
`RunawayNativeProbe.java` or `RunawayNativeProbeAfter.java` with `javac -cp` pointing at the built fat jar and
`-d` pointing at scratch. Run its fully qualified class under
`telamin.fluxtion.audit.analyser.analyser.ui`, with the scratch directory and jar on the classpath:

```text
java --add-opens java.desktop/javax.swing=ALL-UNNAMED
     -Djava.awt.headless=false -Duser.home=<scratch>/home
     -Danalyser.mouseTrace=<scratch>/trace.log
     -cp <scratch>:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar
     telamin.fluxtion.audit.analyser.analyser.ui.RunawayNativeProbeAfter
     settings-dialog,ordinary,overlay,walk-step
```

The add-opens flag is for observation of Swing's private timer in the scratch probe. The product correction
and registered tests do not require it. Leave the desktop untouched; each gesture must establish an active
timer or is inconclusive. The harness uses native Robot mouse input; setup and the deliberate Settings
intervention use the real app actions. No component-dispatched replacement release is used.

## Predictions and results

1. **Confirmed:** the final new regressions against the unchanged production source reported
   **2 tests / 2 failures / 0 errors / 0 skips**, at the table-adjustment and slider-drag cancellation assertions.
2. **Confirmed:** both pass after cancellation; the next native table drag still autoscrolls and ends normally.
3. **Confirmed:** slider edge timer stops, drag mode clears and its chosen endpoints remain unchanged.
4. **Confirmed with wording correction:** P4 loosely said “slider timer stop”; the registered slider control
   removes the window's call to cancel the slider, rather than only its internal timer.stop line.
   All three controls fail at named assertions, restore source/classes byte-identically and rerun green:
   `mouse-loss-table-hook`, `mouse-loss-table-timer`, `mouse-loss-slider-hook`. Total 42.1 seconds.
5. **Confirmed for the stated controlled boundary:** three fixed-jar Settings interruptions stop scrolling;
   ordinary/spotlight/walk gestures complete. This does not close the unknown original trigger.

Before the useful red run, one test attempt did not compile (ambiguous Frame import). The next had
1 failure and 1 error because the shared fixture's dialog watchdog closed Settings. The test now stops that
watchdog and owns dialog cleanup. Neither unsuccessful harness attempt is counted as a product witness.
The interrupted post-fix jar attempt is also retained above, followed by the valid repeat.

## Gates run

Counts are total / failures / errors / skips, **not passed / failed**.

- Before correction: `mvn -o -q test`: **2705 / 0 / 0 / 150**, 360 source-mapped reports, no orphans.
- After correction: `mvn -o -q clean package`: **2707 / 0 / 0 / 152**, 361 source-mapped reports, no orphans.
- After documenting the trials, `mvn -o -q test` inside the restricted sandbox returned
  **2707 / 0 / 29 / 152**: all 29 errors were local socket binds denied with “Operation not permitted”.
  Retrying with local socket access returned **2707 / 0 / 0 / 152**, 361 reports, no orphans.
  This environment-error attempt is recorded, not folded into the passing run.
- `mvn -o -q test -Dtest=TableDragCancellationFrameTest,WalkPlaybackFrameTest,WalkReviewFrameTest,SpotlightFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false`:
  **19 / 0 / 0 / 0**, sequential display execution, four suites (2 + 2 + 11 + 4).
- The new class alone, before that neighbour run: **2 / 0 / 0 / 0** after the fix.
- Fast engine, only the three `mouse-loss-*` cases: **3 caught**, named failures, byte-identical restore and green rerun.
- `python3 tools/test_project_chart_review.py`: **5 green**.
- `python3 tools/verify_project_chart_review.py --mode preflight`: **30 frame suites / 357 anchors**.
- `mkdocs build --strict`: clean. Whitespace and public-data sweep checked before commit.

Not run locally: the full display list and full mutation list; CI must establish Linux/Xvfb behaviour.
No hosted provider, key, LLM session, participant project or real log was used. No screenshot was required
or captured for the fix; the native input and component state were measured.

## Independent-review correction

The original results above are preserved as measurements of `66f9fddf`. The review found that assertions
following native release depend on whether that release is delivered. The [correction evidence](../review-fixes-2026-09-28/README.md)
adds pre-release assertions, range-at-focus-loss checks, two missing-line controls, and a native non-modal
continuation control. Product cancellation is now restricted to an owned modal taking focus. The original
capture does not establish the cause of the owner's incident or justify cancellation on every focus change.
