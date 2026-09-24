# PR #13 round-2 predictions — frozen before implementation

Written and committed before the fixes, so they cannot be adjusted to match what happened.

## R13-2b — a rename onto an unsaved chart's name destroys that chart

**Cause I expect to find.** `DuplicateChartRepair.apply` validates rename targets against the *saved*
list only, and `MainFrame` carries a live tab forward only when its name is not already in the repaired
list. So renaming a duplicate to "First" while an unsaved tab is called "First" produces a repaired list
containing the duplicate under that name, the carry-forward skips the live tab as "already present", and
the unsaved chart is gone with no message.

**Fix I intend.** Pass the names of live tabs that have no saved definition into `apply` as additional
taken names. A rename onto one is refused, naming it. Nothing else changes.

**Prediction.** Reverting `apply` to ignore that parameter makes the new test fail at
*"a rename onto an unsaved chart's name must be refused"*. I predict the mutation goes red there and
nowhere else — the existing 13 policy cases do not pass the new argument.

## R13-4b — the assistant can create a chart during the refusal but cannot edit it

**Cause I expect to find.** `ActionExecutor` treats `hasDefinition(target)` as "withheld", and
`GraphTabs.takenNames()` is open tabs **plus** saved names. So a chart the assistant just created is an
open tab, therefore "taken", therefore treated as withheld on the next call.

**Fix I intend.** Ask whether the name is held by a *withheld saved definition*, not whether it is taken.

**Prediction.** Reverting to `hasDefinition` makes the create-then-edit test fail on the second call,
at *"the assistant must be able to edit the chart it just created"*. I do **not** expect the
`DuplicateGlobalChartsFrameTest` verb refusal to change: "Same" is a withheld definition either way.

## What I expect to be true already

The reviewer's R13-1, R13-3, O13-1 and O13-2 fixes are confirmed fixed on a display, so I predict no
change is needed there and no existing control goes red from this work.
