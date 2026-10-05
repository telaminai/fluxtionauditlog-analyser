# Second CI failure: bundle processor fixture completion

[CI run 37278947713](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/37278947713)
tested PR head `0b2d7a89`. Build, ui-frame, loop-bench, self-test and mutation shards 1–3 passed.
Shard 0 failed its **unmutated baseline**, **784 / 1 / 0 / 0**, and ran no controls.
`BundleProvenanceFrameTest#aBundleCarriesItsEventProcessor` expected its direct config assignment
to remain `com.example.MyOwn`; it instead saw the bundled processor after a pending source refresh.
[Raw baseline](ci-baseline-failure.json), [jobs](ci-attempt-2-jobs.json),
[requested/ran/caught shard counts](ci-shard-counts.json).

This was separate from the first CI failure. The native mouse controls ran successfully in this
attempt, including [column adjustment](mouse-loss-column-adjustment-ci.json), which failed at
`column adjustment ends with the cancelled gesture`, with zero skips and byte-identical restoration.
The three successful shards requested and caught **475** controls; shard 0's **158** did not run.
That partial result is not a complete green mutation gate.

## Correction and limits

The processor fixture now captures and receives in separate sessions. Provenance can settle before
the bundled log arrives, so the recipient waits for the new store's two source-combo model refreshes:
the loaded-log render and the inference callback. Adding the recipient's DEMO source root also starts
inference; the fixture waits for that setup's two refreshes before selecting another processor through
the real `open {processor}` action. Listener removal on the EDT supplies the completion barrier.
There is no sleep-based readiness claim or assertion of a direct config assignment.

Manifest and adoption assertions remain, and the new `bundle-recipient-can-choose-processor` control
removes the real action's config update. It fails at `theRecipientCanStillChooseWithinTheSession`.
The application and generated session code are unchanged. This check covers a choice after inference
completes. It does **not** claim that a choice while inference is still running cannot be overwritten.

Two intermediate display attempts are preserved: [attempt 1](display-attempt-1.txt),
[attempt 2](display-attempt-2.txt), each **18 / 1 / 0 / 0**. Waiting on the combined sender/recipient
fixture was insufficient; the second attempt also started another inference by adding a root
immediately before choosing the processor. The final fixture explicitly completes both setup phases.

## Verification

All local rows are **RAN**, October 5, JDK 21, DEMO data, isolated worktree.
Display and mutations ran serially under `lockf -k /tmp/fluxtion-analyser-display.lock`.
Counts are total / failures / errors / skips.

| Check | Command | Result | Raw evidence |
|---|---|---|---|
| Bundle display suite | `mvn -o -q test -Dtest=BundleProvenanceFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false -Dsurefire.failIfNoSpecifiedTests=false` | **18 / 0 / 0 / 0** | [report](display-final.txt), [output](display-final.log) |
| Targeted fast controls | `python3 tools/verify_project_chart_review.py --mode mutations --engine fast --output <output> --case bundle-carries-its-processor --case bundle-processor-adopted-on-open --case bundle-recipient-can-choose-processor` | Requested **3**, ran **3**, caught **3**, 42.9 seconds; baseline **18 / 0 / 0 / 0** | [results](controls.json), [output](controls.log) |
| Preflight | `python3 tools/verify_project_chart_review.py --mode preflight --output <output>` | **43 frame suites, 634 anchors** | [result](preflight.json) |
| Final headless suite | `mvn -o -q test` | **3184 / 0 / 0 / 249**, 420 reports, no orphans | [counts](headless-counts.json), [output](headless.log) |
| Documentation | `mkdocs build --strict` | Exit 0 | [output](mkdocs.log) |
| Harness regressions | `python3 tools/test_project_chart_review.py` | **5 / 0 / 0 / 0** | Passed locally |

Each mutated run fails at its intended assertion: manifest inclusion, recipient adoption, or the real
recipient selection. All restored runs pass with zero skips, and source/class restoration is byte-identical.
The final headless and strict documentation runs are stored beside this record. Full final-head CI is
recorded on PR #105; no complete local mutation gate was run. Private machine paths are replaced
with placeholders. No provider calls or regeneration were used.
