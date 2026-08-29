---
name: replay-a-run
description: Re-run a recorded session and check the replay actually matches the original, before trusting any conclusion drawn from it.
x-analyser-min-version: 1.12.0
---

# Replay a recorded run

A replay that diverges from the original produces plausible numbers that are wrong, and nothing throws.
So the check is the point of this skill, not the replay.

## Steps

1. Replay the recorded tape into a fresh processor, using this project's own replay entry point.

   TODO(bundle): name the exact command for this project. There is no universal one — it depends on how
   the tape is recorded and how the processor is constructed.

2. **Compare, do not assume.** Open both logs in the analyser and check cycle counts and node output
   agree:

   ```
   analyser_open {"log": "<original>", "graphml": "<graphml>"}
   analyser_aggregate {"groupBy": "dimension"}
   ```

   then the same against the replayed log. A difference in cycle count is the first signal; identical
   counts with different node output is the second.

## The two failures worth knowing about in advance

Both are silent, both were measured on a real 311-node graph, and both are filed upstream:

- **Record events may not serialise.** The shipped recorder uses a JavaBean representer; a Java `record`
  exposes `x()`, not `getX()`. Records are the natural way to write these events — their `toString()`
  lands readably in the audit log — and that same choice can make the tape unwritable.
- **Exported-service calls are not events.** An `@ExportService` call is an entry point that dispatches
  into the graph exactly as an event does, but an auditor does not see it. A tape without them replays a
  different day: in the measured case, three missing `setPrice` calls left the price book empty and every
  downstream figure was computed against nothing, with 295 of 574 cycles diverging.

If your replay diverges, check these two before suspecting your own logic.
