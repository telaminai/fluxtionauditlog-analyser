# Java source spotlight — response to independent implementation review

The [review](review_java_source_spotlight_2026_09_23_claude.md) is preserved byte-for-byte from
`8b72d628`, branch `review/java-source-spotlight-2026-09-23-claude`. It reviewed `0fbbdace` against
`9b88e6ac` and returned **MERGE**, with three low findings. This response does not extend that
independent verdict to changes the reviewer has not seen. No merge or release was performed.

## Dispositions

**F1 — addressed.** Each witness now runs its named test unmutated first, removes stale JUnit output,
and requires exactly one matching test, exit zero and no failures/errors/skips. The JSON records the
command, outcome and `baselineGreen`. If the baseline fails, source edits and the mutated run never
happen and `seenRed` remains false. A failed baseline cannot borrow green from an earlier run.

`tools/test_java_spotlight_witness.py` exercises the runner with controlled Maven responses and actual
temporary source/report files: green then assertion failure; already-red/error/skipped/missing/wrong-name
baselines; a green XML report with a failed process; and mutant outcomes that are not a named assertion.
`tools/test_tools.py` runs it in CI. Removing the baseline guard makes
`test_bad_baseline_never_mutates_or_attempts_red` fail; see the
[negative-control record](evidence/java-source-spotlight-2026-09-23/baseline-guard-witness.json).
One passing baseline cannot rule out every intermittent display failure, but baseline evidence is now
part of each witness rather than an unstated dependency on another run.

**F2 — addressed.** `litEcho` emits `partial` only when `javaBounds` returns a measurable band.
`missingBandDoesNotClaimPartialDuringApply` reproduces the assembly window: light a line, scroll it fully
out while apply defers remeasurement, inspect the echo and then resume normal remeasurement. The old lit
entry has no `partial` claim; normal remeasurement extinguishes it. The test first requires a measured
`partial: false` on the visible band, so deleting the field unconditionally is not a passing repair.
The `missing-band-partial` mutation restores the old `orElse(true)` fallback.

**F3 — clarified, behavior retained.** A line number identifies an editor line, including its empty final
line after a trailing newline. The source guide and spotlight spec now state this. The existing plan
regression accepts exactly that line, refuses the next, then removes the trailing newline and requires
the formerly empty line to refuse. This preserves the self-consistent Swing behavior rather than changing
what a valid editor anchor means. No subtraction or new source mapping was introduced.

## Predictions and validation

The tracker recorded all three predictions before edits. All held:

- F1: all **13/13** current witnesses pass their unmutated baseline, fail their named assertion after
  mutation, and restore exact source bytes. [New record](evidence/java-source-spotlight-2026-09-23/review-followup-mutations.json).
  The baseline-guard negative test fails when that guard is removed; restored helper tests pass.
- F2: `missingBandDoesNotClaimPartialDuringApply` passes unmutated and fails with the old fallback.
  Normal remeasurement extinguishes the target. This is the thirteenth recorded mutation.
- F3: the editor-line boundary assertions in `JavaSpotlightPlanTest` pass.
- `mvn -q clean test`: **1,876 tests, zero failures/errors, 62 headless skips** (the new display test adds
  one to each of the original totals). `mvn -q package -DskipTests` passes after that clean gate.
- `python3 tools/test_tools.py`: all pass, including the four new verifier tests and their subcases.
- Strict docs, `git diff --check` and tracked/untracked rule-1 sweeps pass.

The complete twelve-suite display gate is delegated to this follow-up's PR CI; each of the thirteen
witness runs above independently used a real display and required its named unmutated test not to skip.
The review's original 94/94 socket checks and screenshot inspection stand; neither was repeated for this
small echo/verifier/documentation follow-up. The original evidence files remain unchanged.
