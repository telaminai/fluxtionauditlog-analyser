# Mutation CI sharding: predictions before implementation

The full set remains mandatory. Four independent CI jobs each have their own checkout, class trees and Xvfb
server; execution within each job stays sequential. The existing mutation-gate check becomes the final collector
and also requires the engine self-test job. No control's baseline, named failure, restore or restored-green check
is removed. No application code changes.

Predictions:

1. Deterministic longest-first allocation using the measured timings from CI run 36270986642 assigns all 174
   current controls exactly once. New controls receive a positive default duration and are never omitted. The
   slow MainFrame controls are distributed across jobs rather than grouped by source file.
2. Removing one shard result or one control, duplicating a control, mixing revisions, skipping a baseline or
   restored test, substituting an error for a named failure, or losing a byte-restoration flag makes the collector
   fail at an assertion naming the missing or invalid evidence. Permanent fixture tests cover these failures.
3. The design-status-capped control runs first in its assigned shard. It no longer needs a separate compile and
   duplicate control execution. Its early failure remains visible, without duplicating a control across shards.
4. Unsharded and branch-subset commands keep their behaviour. Sharding refuses combinations with subset selection,
   an explicit case list, or modes other than fast mutations. Every shard preflights every anchor before execution.
5. The current headless Java gate stays 2414 total / 0 failures / 0 errors / 111 skips, over 325 reports. The existing
   five Python harness tests stay green; new Python checks test allocation and rejection of incomplete evidence.
6. Representative real controls run sequentially on the local display through the shard path, with named assertion
   failures and byte-identical restoration. The complete 174-control set runs in CI, across all four shards.
7. Estimated CI elapsed time is 5–7 minutes instead of 15–17, assuming four runners start promptly. This is a target,
   not a result. Duplicated shard setup/baselines can increase total runner time. Record actual timing and any misses.

Verification will record the actual commands, counts, CI results and unverified limits in the companion report.
