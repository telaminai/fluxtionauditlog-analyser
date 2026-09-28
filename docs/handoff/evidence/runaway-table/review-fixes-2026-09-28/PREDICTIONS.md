# PR 62 review corrections — predictions before trials

Pinned input: 66f9fddf. Review comments 5871805743 and 5872915930 were read before these predictions.
This file is committed before implementation or test runs. These are predictions, not evidence.

1. Removing the table hook will fail the pre-release row-adjustment assertion; removing the slider hook
   will fail the pre-release drag-mode assertion. Native release delivery will no longer decide either result.
2. Removing the table timer stop will fail the pre-release selection/viewport stability assertion.
3. Removing the autoscroll-setting restore will fail the pre-release configured-autoscroll assertion;
   removing the column-model adjustment reset will fail the pre-release column-adjustment assertion.
   The native press must first establish both models adjusting, so a default false cannot satisfy the control.
4. Capturing the slider range in the window-focus event observer, before the frame processes that event,
   will let the test assert that cancellation retains the range at focus loss, rather than the range before
   edge scrolling began. Both pre-release and post-release timer observations should retain it.
5. Restricting cancellation to an owned modal dialog will preserve adjustment across non-modal focus loss.
   A focused boundary regression will show the selection listener stays deferred during continuation and
   publishes on final release. Restoring unconditional cancellation must fail its named assertion.
6. Expected full headless count: 2708 / 0 / 0 / 153, 361 source-mapped reports, no orphans (one new frame test).
   Targeted display suites, sequential: 3 + 2 + 11 + 4 = 20 / 0 / 0 / 0. Native-input skips are blockers.
7. Six mouse-loss controls should be caught: the three retained controls, the two missing-line controls,
   and a non-modal policy control. Expected preflight: 30 frame suites / 360 anchors.
8. Allowing the existing full CI workflow on pull requests into diag/mouse-trace should run the build,
   display and four mutation shards at the new PR head. Linux native routing remains unverified until that run.

No test or mutation has run for this correction yet. Record misses in RESULTS rather than editing these predictions.
