# PR #87 correction evidence — 2026-10-01

Implementer's report, not independent approval. Starting head: `4f12c47c`.

## Changes and owner decision

R1: quoted Unicode final segments are now removed completely. The test drives `BundleProfile.export`,
reads its written Properties, and checks both the remaining prose and the exact reported removal.
Seven path cases run with four delimiter pairs: double, single, backtick and curly double quotes.

R2: every unquoted path form now goes through the same final-segment check. The owner selected:
“Refuse export and ask the author to quote the path”. Han, Hiragana, Katakana, Hangul and Thai in an
unquoted final segment are ambiguous between a filename and adjoining prose. Refuse with the key and
recovery instruction before creating the output. Quote the complete path to retain adjoining prose.
This supersedes the earlier test's expectation of guessing the boundary of an unquoted Japanese suffix.
Its quoted replacement still checks preservation; ten refusal cases check the new policy independently.

## RAN

JDK 21 on macOS; isolated worktree. Counts are total / failures / errors / skips.

- New regressions against the original production file, restored from a byte copy afterward:
  `mvn -o -q test '-Dtest=BundleProfileTest#quotedUnicodePathsAreRemovedWhole+ambiguousUnquotedEndingsRefuseWithoutWriting'`
  — **17 / 12 / 0 / 0**. Ten ambiguous endings wrongly exported; two quoted POSIX paths leaked their
  Unicode tail. Five quoted non-POSIX cases already worked. Named failures are retained in `before.txt`.
- An earlier loop-based version ran **2 / 2 / 0 / 0** on the original production file. It was expanded
  into parameterized cases so each path form has an independent outcome; neither baseline was green.
- Fixed `BundleProfileTest`: **48 / 0 / 0 / 0**.
- Restored full `mvn -o -q clean test`: **3064 / 0 / 0 / 218**, **406** source-mapped reports,
  **no orphans**. The 218 skips remain skips; no display gate was run for this non-UI change.
- Fast controls: all fourteen `rf2-*` plus `oa3-redaction-covers-dialogue`, **15/15 caught**, exact
  requested/caught name lists compared. Every case had a green baseline, named assertion failure,
  byte-identical source and class restores, and green rerun. See `controls.json` for the assertion rows.
  Two new controls remove quoted recognition and restore the truncated POSIX final-segment candidate.
  The existing prose-boundary control now removes the owner's ambiguity refusal.
- Preflight: **40** frame suites, **555** anchors.
- `python3 tools/test_project_chart_review.py`: **5 / 0 / 0 / 0**. An earlier accidental run during
  the mutation gate failed its anchor check because a source anchor was actively mutated; after the
  gate restored the source, this serial rerun was green. That failed attempt was not a product defect.
- Strict MkDocs build and `git diff --check`: clean.

## Limits and final review

No merge, release, display gate or full local mutation gate. CI results are reported on the PR after push.
This remains a lexical guard: unquoted paths containing spaces are cut at whitespace, numeric
`~123/secret` retains its ratio exemption, and a POSIX path immediately after a colon retains its URI
exemption. Quoting the path avoids those ambiguous spellings. The colon case is a remaining issue-79
policy item, not something these corrections silently claim to solve.

The final reviewer should check the quoting policy, refusal before output, and the re-anchored controls.
