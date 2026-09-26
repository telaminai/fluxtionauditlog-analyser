# The review's probes, re-run on the fix (set 12, P75)

The review's probes in `../review-probe/` are preserved unchanged. They were first re-run on `0bb01fa8` and
reproduced byte for byte (RESULTS, set 12). These are their runs on the fixed branch, built with
`mvn -q package -DskipTests`, JDK 21.

| File | What it shows |
|---|---|
| `review-probe-after.txt` | `ReviewProbe` stops at its first line after `snapshot.before`: `clear()` on a published qualification now throws (R4). That is the fix, not a failure of it. |
| `ReviewProbeGuarded.java`, `review-probe-guarded-output.txt` | The same probe with only that call guarded, so every later line runs. Each observation the review recorded is reversed — ordered delivery (O1), a refused save that leaves the selection (R5), a rolled set that reports its changed member (R3), the BOM collapse suspected (R6), the pending oversized frame not assessed (R7), no address for a saved quote name (R8) — except `rolled.rawChanged=true`, which is by design: as for a single mapped store, the refusal is at the request boundary (the dispatcher), not in the store's raw read. |
| `coverage-race-after.txt` | `CoverageRaceProbe` cannot run against the fix. Its store supplier blocks inside the coverage capture waiting for the probe's own log switch; the capture is now one EDT task, so the switch cannot run until it ends, and the probe's own 10-second timeout fires. The interleaving it depends on is what R1 made impossible. `CoverageCaptureTest` is its deterministic replacement. |
| `report-probe-after.txt` | `ReportCoverageProbe` (built jar, isolated home): the coverage verb refuses the retained foreign pair and the exported PDF now states "coverage REFUSED" and prints no ratio (R2). |
