# UPS-1 — repeated-field regression correction: predictions

Starting head: `95bca345`. The independent re-review found a trust-class regression in the replacement cutoff.
The owner requested a fix and regression checks on PR #92; the original author will validate and decide whether to merge.
This file is committed before implementation or new trials. The review probes already ran and are prior evidence, not
predictions: the released runtime 1.1.0 writer emits the two inputs below, and `95bca345` keeps their forged scalar;
`9474c687` withholds it.

## Intended correction

Keep the first structural break's location/reason for diagnostics. Independently bound reading before the FIRST copy of
any repeated recognised field, including when the repeated copy follows another visible break. Retain the existing
first-text cap: it also withholds never-repeated forged fields and all node logs in broken records.

Do not assume grouping IDs or thread names are safe because they precede event text. The producer writes them unquoted.
A conservative consequence is intentional: a genuinely written first copy can also be withheld when later text repeats
its key. Repeated values cannot establish which copy is genuine. Tests must state that consequence rather than insist
that the first event/time always survives. Whole records and the exported-service allowance should be unchanged.

## Predictions

- R3-P1: grouping ID `DEMO\n    event: Forged` yields a broken record with no event (not `Forged`), on built-in,
  legacy SPI and typed SPI paths. A new named assertion fails on `95bca345` and passes with the correction.
- R3-P2: thread name `DEMO-agent\n    endTime: 999`, with event text disabled, yields no endTime, not 999, on those
  same paths. Its new named assertion fails on `95bca345` and passes with the correction.
- R3-P3: a visible break BEFORE the repeated genuine field does not let a forged first copy survive. The first break
  still supplies the diagnostic location. A regression fails before the correction.
- R3-P4: existing tests that demand the first copy of a repeated scalar may fail and need a conservative expectation,
  explicitly explained; quoted values and whole exported-service records must retain their existing expectations.
- R3-P5: controls removing the first-copy bound fail at the new named wrong-result assertions. The existing UPS-1
  controls remain caught; any equivalent control or changed witness will be recorded, not silently counted.
- R3-P6: headless tests and the targeted display suite are green after restoration. No full mutation gate locally;
  CI remains the complete gate. Sources and classes restore byte-identically.

## Boundaries

No producer change, merge, regeneration, provider call or release. This restores the repeated-field refusal, not a claim
that arbitrary unquoted input is safe. The existing indistinguishable-input limitation still applies. Predictions and
results use DEMO data only.
