# P2 results — final-review corrections for PR #87

Implementer's evidence, not an independent approval. Starting production head: `11df0d26`.
Predictions were committed as `d9081aad` before any change or trial. This report supersedes the earlier
README only for F1–F4 and the additional quote-bound policy below; preserved evidence is unchanged.

## Dispositions

| Finding | Disposition | Regression | Control and observed red |
|---|---|---|---|
| F1 | Fixed: quotes reuse the ordinary supported shape grammar, allowing spaces within quoted segments. Ratios, one-segment POSIX strings and protocol-relative URLs keep their exemptions. | Three narratives added to `ordinaryProsePassesUntouched`; all assertions now run even if an earlier case fails. | `rf2-quoted-path-keeps-exemptions`: removing `quotedShape(close)` fails `ordinaryProseKeepsItsExemptions`, with `ordinary writing is left alone` wrong-output assertions. |
| F2 | Fixed: a trusted quoted span is one supported path, with no sentence punctuation, other quote type, parenthesised message or second path start after whitespace. An untrusted span falls back to unquoted redaction/refusal. | `aQuotedSpanEndsAtThePath`: seven narratives, including the three review inputs, straight and curly apostrophes, another delimiter type and two paths. Exact output and exact removals are asserted. Seven space-containing positive cases remain accepted. | `rf2-quoted-span-ends-at-the-path`: removing `quotedBound(open, close)` fails `quotedSpanEndsAtPath` for the apostrophe and two-path cases. |
| F3 | Fixed, option (a): add `「」`, `『』`, `‘’`; the refusal also names double quotes as a working spelling. The owner clarified that both straight and curly closing single quotes need a separator before a following letter. | Seven path forms × seven delimiter pairs in `quotedUnicodePathsAreRemovedWhole`; `theRefusalRecommendsAWorkingDelimiter`; two `aSingleQuotedUnicodePathNeedsASeparator` cases. | `rf2-native-quote-delimiters`: removing the three additions fails the helper's named `prose never refuses the export` assertion. `rf2-refusal-names-working-delimiter`: removing the recommendation fails `refusal names a supported delimiter: double quotes`. |
| F4 | Disclosure fixed; underlying pre-existing behaviour intentionally unchanged. Lao, Khmer and Myanmar suffixes can still be consumed as path text. | Three `theUnquotedScriptLimitIsDisclosed` cases pin the actual exported prose and reported removals. Spec/Javadoc disclose it. | No new mutation: this is a disclosed limit, not a newly claimed runtime protection. The three cases were green on the original production file. |

The accepted unquoted ambiguity check is unchanged apart from its recovery message. R1/R2 were not
reworked. Existing residuals for spaces, numeric homes and `log:/…` are not claimed fixed. PR #87 must
reference, not close, issue #79; its colon policy is a separate owner decision.

## RAN

JDK 21 on macOS, isolated worktree, Maven serially. Counts are total / failures / errors / skips.

- Starting production file with final new/expanded tests:
  `mvn -o -q test '-Dtest=BundleProfileTest#ordinaryProsePassesUntouched+aQuotedSpanEndsAtThePath+quotedUnicodePathsAreRemovedWhole+theRefusalRecommendsAWorkingDelimiter+aQuotedPathCanStillContainSpaces+theUnquotedScriptLimitIsDisclosed+aSingleQuotedUnicodePathNeedsASeparator'`
  → **28 / 17 / 0 / 0**. `regressions-P2.json` retains the named failures, counts and SHA-256 restore.
  The F1 assertion collects all three wrong outputs. Six of seven F2 cases fail before the fix;
  the curly-apostrophe case already used the ordinary path correctly before curly delimiters were added.
  Delimiter and message checks also fail before the fix. Positive space cases and the F4 limit remain green.
- Byte-copy restore with `cat`, SHA-256 verified, then `mvn -o -q test -Dtest=BundleProfileTest`
  → **68 / 0 / 0 / 0**.
- Fast engine: four new controls, fourteen retained `rf2-*`, and `oa3-redaction-covers-dialogue`:
  **19/19 caught**, requested names equal caught names. The gate executes in registration order,
  regardless of the order of `--case` arguments. All mutated runs contained the target method's
  assertion failure, no errors/skips; all source/class byte restores and restored-green runs verified.
  `controls-P2.json` keeps the outcomes and assertion rows without machine command paths.
- After mutation testing, `mvn -o -q clean test` → **3084 / 0 / 0 / 218**, **406** source-mapped
  reports, **no orphans**. **20 added test invocations** relative to 3064. `BundleProfileTest` is 68,
  up from 48; three existing ordinary-prose fixtures and the expanded delimiter loop add further coverage.
- Preflight: **40 frame suites / 559 anchors**, four more than 555.
- `python3 tools/test_project_chart_review.py`: **5 / 0 / 0 / 0**.
- Strict MkDocs build and whitespace checks: clean. Public-data sweep performed before commit/push.
- No local display or full mutation gate; CI must establish those at the pushed head.

## Prediction scores and failed attempts

| Prediction | Result |
|---|---|
| P20 | Confirmed: all three quoted-prose exemptions failed before and are preserved after. |
| P21 | Confirmed: the three review narratives lost prose before; after, only their paths are removed. |
| P22 | Confirmed: the additional conservative-bound cases and seven space-containing positive controls pass. |
| P23 | Partial miss: adding delimiters is not alone sufficient to preserve the prior single-quote/Japanese-adjacency fixture. The explicit F2 apostrophe rule sees Japanese characters as letters too. The owner chose a separator after both straight and curly single quotes; the tests and docs now state that rule. Double quotes and corner brackets remain adjacent to prose. |
| P24 | Confirmed: the three script-limit cases are unchanged; disclosure, not a runtime fix. |
| P25 | Confirmed: both required new controls and two additional F3 controls are caught at named assertions; all retained controls remain caught. |
| P26 | Confirmed: 218 skips and 406 reports remain; 40 suites, 559 anchors. CI is checked separately on the final pushed head. |

Preserved failed attempts: the initial pre-fix selection was **25 / 15 / 0 / 0**. The first fixed
focused run was **65 / 7 / 0 / 0**: its seven failures were the old straight-single-quote/Japanese
adjacency expectation, incompatible with the conservative bound. That surfaced the owner clarification,
not a reason to weaken the bound. The final pre-fix and restored counts above include the extra cases
for the clarified rule. No mutation survived; no compilation error was counted as a witness.

## Current-main integration for CI

After pushing `e1ca9dfe`, GitHub reported PR #87 as conflicting and started only the push-triggered
static check, not the pull-request CI workflow. Main had advanced to `473cd674` with the documentation
reorganisation (including the packaging spec's move to `docs/specs/completed/`). Bringing that main
into the PR branch applied cleanly, with no textual conflict. Main itself was not changed.

The redaction production file, its tests and the mutation-control definitions are byte-for-byte the
same as `e1ca9dfe`; the corrected policy remains in the moved spec. The integration changes no runtime
behaviour: main's Java/tool edits in this interval are documentation pointers, and its spec-link test
also checks the moved upstream-spec directory.

RAN again on the combined tree: `mvn -o -q clean test` **3084 / 0 / 0 / 218**, **406 reports, no
orphans**; strict MkDocs; harness tests **5 / 0 / 0 / 0**; preflight **40 suites / 559 anchors**;
whitespace and public-data sweeps clean. Targeted mutations were not repeated on identical code.
CI must be read at the resulting merge commit on the PR branch, not at the earlier correction SHA.
