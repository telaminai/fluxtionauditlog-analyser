# UPS-1 — repeated-field correction (PR #92 re-review)

This is the implementer's report, not independent approval. The owner requested that the reviewer fix the issue on
PR #92; the original author will validate and decide whether to merge. Starting head: `95bca345`. Predictions were
committed first as `0fc15f8b` ([PREDICTIONS-r3.md](PREDICTIONS-r3.md)). No issue-only deferral was taken.

## Defect and correction

**RAN before implementation:** the released fluxtion-runtime 1.1.0 `LogRecord` writer produced both shapes:

- `groupingId = "DEMO\n    event: Forged"`: `95bca345` read `event=Forged`, `brokenAtLine=6`;
  `9474c687` read no event.
- thread name `"DEMO-agent\n    endTime: 999"`, event text disabled: `95bca345` read `endTime=999`,
  `brokenAtLine=9`; `9474c687` read no endTime.

**READ:** the released writer prints grouping IDs and thread names unquoted. They are not made safe by occurring before
`eventToString`, or by event text being disabled. The old cutoff incorrectly trusted the first copy in those positions.

**Fixed:** retain the first break's diagnostic location/reason, but independently tighten the read bound before the
FIRST occurrence of every repeated recognised field. A repeat after an earlier break still tightens that bound. Keep
the existing additional cap at event text, node logs or an unknown field; it protects later never-repeated forged fields
and prevents any broken record from contributing node logs.

**Conservative cost, intentional:** a genuinely written first copy is withheld too. The parser cannot establish which
copy is genuine. Three old expectations now explicitly require missing repeated values, rather than the earlier first
copy: the escaped-separator event, the repeated eventTime, and C31's third record. Their absence assertions are exact;
the whole/quoted/exported-service expectations remain intact. A withheld event supplies no event filter dimension.
The format specification, Javadoc and existing Unreleased entry state this policy.

## Regression checks

`RecordRepeatedScalarTest` exercises the actual heap store and both SPI text encodings, not only the detector:

| Test | Pre-fix wrong result (RAN) | Control | Named assertion |
|---|---|---|---|
| `groupingIdPayloadMustNotBecomeEvent` | event `Forged` | `ups1-r3-grouping-first-copy-withheld` | groupingId's forged first event must be withheld |
| `threadPayloadMustNotBecomeEndTime` | endTime `999` | `ups1-r3-thread-first-copy-withheld` | thread's forged first endTime must be withheld when event text is disabled |
| `aLaterRepeatWithholdsItsFirstCopyEvenAfterAnEarlierBreak` | event `Forged` despite an earlier structural break | `ups1-r3-repeat-after-break-still-withholds` | a repeat after the first break still withholds the earlier forged event |

All also assert one retained record, no node entries, the index's withheld flag and a BROKEN_VALUE finding. The third
checks that the diagnostic still points to the first break. Before production edits: **3 / 3 / 0 / 0**, each a named
assertion ([r3-before.txt](r3-before.txt)); after the fix: **3 / 0 / 0 / 0**.

## Predictions and failed attempts

- **R3-P1/P2/P3 held:** all three regressions failed with the predicted wrong scalar, then passed.
- **R3-P4 held:** three existing expectations failed on the first focused run (**68 / 3 / 0 / 0**) because they required
  first copies now conservatively withheld. Replaced those expectations with exact null/empty-dimension assertions and
  documented the reason; no whole-record or quoted-value assertion was relaxed.
- **R3-P5 needed a witness correction:** the first targeted run stopped after six caught controls, at
  `ups1-parser-reads-no-node-logs` (**survived**). The repeated-field bound now independently protected that test's
  repeated nodeLogs input. Kept the original input and added a single-nodeLogs variant: removing the payload cap now
  exposes a node in that variant. The complete 43-control rerun caught it and all other requested controls. The first
  attempt's compact result is preserved in [r3-control-attempts.json](r3-control-attempts.json).
- **R3-P6 held:** the clean suite and targeted display checks are green, with skips counted separately.
- One test-authoring attempt had a Java string escaping compilation error. Corrected before the pre-fix run; it is not
  counted as a regression witness. The earlier released-writer probe initially omitted `Clock.init()`; its setup error
  was corrected before comparing the two heads. Neither error is hidden as a successful test.

## Verification (RAN, JDK 21, Maven serially)

Counts are total / failures / errors / skips.

- `mvn -o -q clean test`: **3128 / 0 / 0 / 220**, **412 reports, no orphans**. Three added tests and one added class.
- `BrokenValueFrameTest`, alone under the shared display lock, both headless=false flags: **2 / 0 / 0 / 0**.
- Fast controls: **43 requested / 43 caught** — 42 `ups1-*`, including the three new controls, plus
  `m44-journey-scope`. Named assertion failures; green baselines and restored-green runs; byte-identical source/class
  restores. [Compact evidence](r3-controls.json) records the hashes and named witnesses without machine paths.
- Preflight: **41 frame suites / 601 anchors**.
- `python3 tools/test_project_chart_review.py`: **5 / 0 / 0 / 0**.
- `mkdocs build --strict`, `git diff --check`, tracked/added-line public-data sweep: clean.

The review worktree is isolated. Mutations use the fast harness's byte copies; no checkout restoration, compiler key,
provider, owner settings, participant project or release operation was used. No full mutation gate locally. CI status
for the pushed head is reported in the PR comment rather than inferred from the previous green head.

## Author validation and limits

Review this delta separately from the earlier parser rewrite. In particular, validate the intentional withholding of a
genuine first copy when its key repeats, run the new named regressions and controls, and check the complete CI result.
The original author, not this implementing session, decides whether to merge.

No new native PDF/bundle journey, full local display gate or upstream producer generation was performed for this fix.
Unquoted, structurally indistinguishable input still requires producer-side quoting; this patch restores repeated-field
refusal and does not claim arbitrary text is authenticated or unforgeable.
