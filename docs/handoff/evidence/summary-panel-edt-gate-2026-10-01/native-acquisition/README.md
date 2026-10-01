# Native press acquisition correction

PR #105's [first CI run](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36851563332)
at `5e250246` passed build, ui-frame, loop-bench, mutation self-test and three shards.
[Shard 2](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36851563332/job/110334248647)
stopped at `mouse-loss-column-adjustment`, so the collector failed too.

The uploaded artifact identifies a **skipped mutated test**, not a clean survivor or a crashed run:
`aModalEndsTheNativeTableDragAndTheNextDragStillWorks` aborted at
"native mouse press must reach the table, not be dropped by the desktop".
The counts were **1 / 0 / 0 / 1**; its restored run was **1 / 0 / 0 / 0**.
The shard requested 140 controls, ran 72, and caught 71; the later 68 never ran.
The mutation removes the later column-adjustment reset. Acquisition aborted before reaching that
boundary, so the failed attempt establishes no verdict about the reset's protection.
The runner correctly refused to count this as caught. [Preserved failing entry and accounting](ci-skipped-mutation.json).

RAN before editing: the same single control locally, under the shared display lock, was caught at
the column-adjustment assertion; restored source/classes were byte-identical and the rerun green.
[Original local result](before-local.json). This does not replace the failed Linux result.

## Correction

The native fixture now attaches a mouse-press observer, refreshes the target's screen coordinates
and focus request, and synchronises Robot input with the event queue. It retries at most three times,
and only when no press reached the component. Each unsuccessful attempt releases the button before
trying again. A delivered press that fails to start the gesture is an error immediately;
it is never retried. Exhausted acquisition and unavailable desktop focus are errors rather than
aborted tests or assertion failures. The mutation runner therefore cannot count them as caught
cancellation controls. Focus is verified after native delivery: an actual click may activate a
window whose earlier programmatic focus request was refused.
Where supported, the fixture also calls `Desktop.requestForeground(true)` for its own test JVM.
The JDK macOS peer rejects cross-application window focus requests while that JVM is inactive;
ordering a window forward alone does not activate the application.

After acquisition, the existing checks still use the real held button, real Settings modal, actual
autoscrolling and focus routing. All pre-release, timer-turn, release and next-gesture assertions remain.
There is no retry around a cancellation check or an entire mutated run, and no synthetic component
release or forced selection-model state. The six existing cancellation controls keep their names,
mutations and witnesses.

`TableDragCancellationFrameTest#aDroppedNativePressIsRetriedBeforeTheGestureStarts` deliberately
suppresses the first `Robot.mousePress` call; subsequent calls use the real OS router. It checks that
a later press creates a held selection gesture and that normal release ends it.
This acquisition regression uses a minimal native table; the three interruption witnesses still use
the real analyser. A second phase suppresses every press and checks that exhausting acquisition
throws an IllegalStateException, not an assertion that could be mistaken for protection.
The new control `mouse-loss-press-acquisition` restricts acquisition to one attempt and must fail this
test at `aDroppedNativePressMustBeRetried`, with exactly one deliberately suppressed press.
`mouse-loss-unavailable-input-is-error` changes exhaustion into an assertion failure; the same test
must fail at `unavailableNativeInputMustNotCountAsAnAssertionFailure`.
The existing column control remains independently required to fail at its cancellation assertion.

The slider fixture first sets its lower thumb inside the visible window through the normal `filter`
action, then acquires it and drags to the edge. Pressing on the edge already started the timer, which
could reach the absolute boundary during input synchronisation before the test observed it. The
edge-timer precondition and cancellation checks remain; the fixture no longer consumes its own setup.

READ: JDK 21 `Robot.waitForIdle` flushes pending toolkit events and synchronises native input; it
cannot run on the EDT. All Robot barriers here run on the test thread.
READ: JDK 21 `CPlatformWindow.rejectFocusRequest` and `Desktop.requestForeground` distinguish
window focus from application activation. The foreground request is conditional on platform support.

## Verification

JDK 21, DEMO data, isolated worktree. Every display/mutation run uses
`lockf -k /tmp/fluxtion-analyser-display.lock`; source/classes are restored from byte copies.
Earlier failures are preserved above and in the local attempt records below.

RAN display:
`mvn -o -q test -Dtest=TableDragCancellationFrameTest,LogFindingsOnEverySurfaceFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false -Dsurefire.failIfNoSpecifiedTests=false`.
Native cancellation/acquisition: **4 / 0 / 0 / 0**. Log findings: **14 / 0 / 0 / 0**.
[Native report](TableDragCancellationFrameTest.txt), [log-findings report](LogFindingsOnEverySurfaceFrameTest.txt),
[output](display.log).

RAN preflight: `python3 tools/verify_project_chart_review.py --mode preflight --output <output>`:
40 frame suites, 564 anchors. [Result](preflight.json).
RAN `python3 tools/test_project_chart_review.py`: **5 / 0 / 0 / 0**.

RAN final full headless suite: `mvn -o -q test`: **3089 / 0 / 0 / 219**, 407 source-mapped
reports, no orphans. [Per-suite counts](headless-counts-final.json), [output](headless-final.log).

RAN final fast-engine gate: `python3 tools/verify_project_chart_review.py --mode mutations --engine fast --output <output>`
with one `--case` for each of the names below. Requested **13**, ran **13**, caught **13**, with
baseline **34 / 0 / 0 / 0**, restored runs green and source/classes byte-identical.
The assertion messages were checked individually, beyond the runner's method-name classification:

| Control | Intended assertion |
|---|---|
| `mouse-loss-press-acquisition` | `aDroppedNativePressMustBeRetried` |
| `mouse-loss-unavailable-input-is-error` | `unavailableNativeInputMustNotCountAsAnAssertionFailure` |
| `mouse-loss-table-hook` | before release, losing window focus cancels the table's adjusting gesture |
| `mouse-loss-table-timer` | no table selection growth after window loses drag |
| `mouse-loss-slider-hook` | before release, losing window focus cancels the slider gesture |
| `mouse-loss-autoscroll-restored` | cancellation preserves the configured autoscroll behaviour |
| `mouse-loss-column-adjustment` | column adjustment ends with the cancelled gesture |
| `mouse-loss-nonmodal-preserved` | non-modal focus loss keeps selection adjusting |
| `m44-5-f1-new-generation-resets-outstanding` | the new generation asks for a scan of its own |
| `m44-5-f1-frame-next-log-is-scanned` | F1: the next log's evidence is scanned and rendered |
| `edt-unexpected-failure-fails-the-test` | `anUnexpectedEdtExceptionMustFailTheTest` |
| `summary-injected-failure-must-be-observed` | `theInjectedEdtFailureWasObserved` |
| `summary-injected-failure-is-accounted` | `uncaughtEdtFailure` |

[Raw final gate](controls-final.json), [output](controls-final.log), [assertion and name comparison](intended-assertions.json).
No full mutation gate was run locally. Strict MkDocs, whitespace and public-data sweeps passed before push.

### Preserved local attempts

These are not final-head acceptance evidence:

- [Attempt 1](controls-attempt-1.json): baseline **34 / 4 / 0 / 0**; acquisition/focus setup failed, zero controls ran.
- [Attempt 2](controls-attempt-2.json): isolated native-class baseline **4 / 4 / 0 / 0**; focus setup failed, zero controls ran.
- [Attempt 3](controls-attempt-3.json): baseline green, runner reported 12 caught. Manual inspection rejected
  the column result: it failed at a focus precondition, not cancellation. This is why setup failures now
  throw an ordinary exception, which the runner refuses to count as caught.
- [Attempt 4](controls-attempt-4.json): baseline **34 / 1 / 0 / 0**; slider edge-timer precondition failed, zero controls ran.
- [Attempt 5](controls-attempt-5.json): baseline **34 / 1 / 0 / 0**; acquisition of the deliberately dropped-press
  regression failed, zero controls ran. Its utility scene was reduced to a minimal native table.
- [Attempt 6](controls-attempt-6.json): 12 controls caught at the intended assertions, including
  `column adjustment ends with the cancelled gesture`; all restored green and byte-identical.
  This preceded the additional check protecting error classification.
- [Attempt 7](controls-attempt-7.json): baseline **34 / 0 / 3 / 0**; unavailable native input was
  correctly reported as errors, zero controls ran. The subsequent change explicitly activates the
  test application where supported, rather than relying solely on window focus requests.

Application behaviour and generated session source are unchanged. Public evidence substitutes
`<worktree>`, `<home>`, `<ci-home>` and `<temp>` for private machine paths. Final-head Linux results belong on
the PR; the previous failed run must not be presented as green.
