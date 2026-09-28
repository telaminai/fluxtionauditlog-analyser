# PR 62 — response to the independent review

Input: `66f9fddf`; comments 5871805743 and 5872915930. These are the implementer's measurements on macOS,
Corretto 21.0.8, not an independent approval or results from the reviewer's other machine.
Predictions were sealed in `5034abda` before code or trials. Native runs are sequential and take an advisory
exclusive file lock shared by the display commands. Robot input reached the components on this desktop.

## Corrections

- **R1:** table row/column adjustment and autoscroll configuration are checked after the real Settings action
  takes focus, while the mouse button is still held. Selection and viewport must remain stable across four EDT
  timer turns before release. Slider drag mode and its edge timer are checked before release too. Both tests
  retain post-release checks; the table test still proves a subsequent ordinary drag scrolls and ends normally.
- **O3:** the expected slider range is the range at the moment focus was lost. A Toolkit window-event observer
  captures it before the frame's focus listeners execute. It is compared immediately after cancellation,
  across timer turns before release, and again after release. Range changes during the earlier edge drag are
  legitimate and are not mistaken for cancellation changing the range.
- **O2:** both previously uncovered lines have controls: restoring the autoscroll setting and ending column
  adjustment. A native press must first establish column adjustment, avoiding a false control on a default false.
- **O1:** cancellation is deliberately narrower: an owned modal dialog must take focus. A non-modal focus change
  retains the gesture. The new native test observes the same final-selection condition as the app's listener:
  no final notification on focus loss or continued drag steps, then a notification on release. Removing the
  modal-only guard is a separate control. Unknown or external focus destinations do not trigger cancellation;
  this does not purport to fix an unobserved lost release with no owned modal.
- **CI:** the existing full workflow also accepts PRs into `diag/mouse-trace`. This runs its normal build,
  display, self-test, four mutation shards and collector on the stacked PR without retargeting or merging it.
  Both display lists remain identical; no new frame class was needed.

READ: JDK 21 `JComponent.setAutoscrolls` stops the timer on false and does not restart it on true;
`BasicTableUI.Handler.mousePressed` starts adjustment; `Component.dispatchEventImpl` notifies Toolkit observers
before component event processing. The range observer adds no product hook and manufactures no mouse release.

## Commands and accounting

From the branch worktree, with JDK 21 selected:

```text
mvn -o -q clean package
mvn -o -q test -Dtest=TableDragCancellationFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false
mvn -o -q test -Dtest=WalkPlaybackFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false
mvn -o -q test -Dtest=WalkReviewFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false
mvn -o -q test -Dtest=SpotlightFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false
python3 tools/verify_project_chart_review.py --mode mutations --engine fast --output <scratch>/controls.json --case mouse-loss-table-hook --case mouse-loss-table-timer --case mouse-loss-slider-hook --case mouse-loss-autoscroll-restored --case mouse-loss-column-adjustment --case mouse-loss-nonmodal-preserved
python3 tools/test_project_chart_review.py
python3 tools/verify_project_chart_review.py --mode preflight --output <scratch>/preflight.json
mkdocs build --strict
git diff --check
```

Counts and actual assertion messages are in `RESULTS.json`. Surefire reports were cleared per invocation,
matched against `src/test/java`, and copied before the next run. Mutation output is reduced to named verdicts,
counts and source/class restore flags; private absolute classpaths are omitted. Sources are restored from byte
copies by the gate, never by a checkout command.

The initial clean package attempt failed compilation because the implementation omitted imports for `Window`
and `Dialog`: zero tests ran. The imports were corrected; the failure is retained in RESULTS, not treated as a
test failure or omitted from the history. The earlier reproduction's interrupted and failed attempts remain
unchanged in the sibling `native-repro-2026-09-28` directory.

## Limits and next check

The native regressions exercise the real controlled modal interruption, not the unknown original incident.
The pre-release assertions no longer rely on whether the OS later delivers release. Local results do not
establish Linux/Xvfb success; the PR comment will give the exact CI head, run ID and job outcomes after push.
No merge, release, keys, provider, model session or participant project was used.

## First full CI attempt: 36445347305 at 0b1cdac7

RAN on Linux/Xvfb: build, self-test and loop benchmark succeeded. The registered display command reported
151 / 2 / 0 / 0. Both failures were the table tests' assertion that the frame regained focus after disposing
its dialog. The modal pre-release checks and the slider test completed; this was not a lost-release failure.
The mutation shards stopped at baseline, so no Linux caught-control claim can be made from this attempt.
A logged EDT exception is not itself the named test failure; the shard evidence must be read for that distinction.

Correction: explicitly request focus on the frame and table after closing either dialog. Bringing the frame
forward alone depended on the desktop window manager. The bounded focus conditions and native drag assertions
remain unchanged; no timeout extension, synthetic release, skip or assertion removal was introduced.
The sealed prediction that the first CI run would be green was wrong.

RAN after the explicit-focus correction on macOS: clean package 2708 / 0 / 0 / 153, 361 mapped reports,
no orphans; the four sequential display suites 20 / 0 / 0 / 0; all six controls caught again in 73.0 seconds,
with named failures, byte-identical source/class restores and green reruns. The five harness tests, preflight
(30 / 360), strict MkDocs and whitespace check remain green. Linux confirmation is still required at that head.
