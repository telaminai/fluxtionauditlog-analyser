# UPS-2, PR #104 — author's response to the independent review of fdb1c7d9

This is the implementer's response, not an independent approval.

- **Review:** [review_pr104_ups2_released_stack_2026_10_01.txt](../../review_pr104_ups2_released_stack_2026_10_01.txt),
  preserved byte-for-byte (sha256 `62e19e37cd86e4b999eb55be32b144626f38ee08a65ae7ccfd789e72f2690d80`).
- **Starting SHA:** `fdb1c7d96311be23bc99cc5defe946aaa5e3c402`, the reviewed head. It was fetched first; local and
  remote agreed, and the branch had not advanced.
- **Final head:** the commit that adds this file. Commits since the review:
  - `2c79edb5` — R1, its regressions, the publication pin, the N1 pin, four controls;
  - `a0e52383` — N3, the capture tool and its guard and control;
  - this documentation commit.
- **Setup:** an isolated worktree, JDK 21, Maven serially. No hosted generation was used this round, and none was
  needed: every check is keyless. No generated processor or GraphML was edited except by the recorded, byte-restored
  mutations below.

## R1 — fixed

**Reproduced before any edit**, through the actual Ant execution. The candidate fixture pom was copied into a scratch
project and only `maven-antrun-plugin:3.1.0:run@strip-generated-attribution` was run. The review's input
(`/*` … `*/` at column 0, a package, later documentation, a class) came out as exactly `public class NoSpace {}`. The
space-prefixed positive control kept its package and documentation.

**Fix** (`examples/fixture-generator/pom.xml`, the second `<replaceregexp>`):

```
\A/\*(?=(?:[^*]|\*(?!/))*This source code is protected under international copyright law)(?:[^*]|\*(?!/))*\*/[ \t]*(?:\r?\n)?
```

- Every body character is "not a star, or a star not followed by a slash". The match therefore cannot cross the first
  comment terminator, however it is indented.
- The lookahead that requires the notice text is bounded the same way.
- The expression is anchored at the file start, so it removes the leading comment only, and only when it is the notice.
  Anything else is left untouched for the publication check to refuse.
- No DOTALL is needed now (`flags=""`), and a CRLF line end is consumed with the terminator.
- The same scratch antrun execution on the fixed pom keeps the review's input whole, apart from the notice.

**Regressions: `GeneratedHeaderStripTest`, keyless, running the actual transformation.** Each `<replaceregexp>` of the
pom's `strip-generated-attribution` execution is read from the pom: pattern, flags, by-line, replacement and filesets.
Each is then run, in declaration order, by Ant 1.10.12's own `ReplaceRegExp` task (the class maven-antrun-plugin 3.1.0
runs, now a test-only dependency) over scratch copies of both configured directories.

| Test | Covers | Before the fix (`fdb1c7d9`'s regex) | After |
|---|---|---|---|
| `theStripCoversBothGeneratedDirectories` | the two configured directories | pass | pass |
| `theGeneratedHeaderIsRemoved` | the current generated header, attribution included: positive control | pass | pass |
| `aColumnZeroTerminatorNeverDeletesCode` | `*/` at column 0; package, imports and code byte-preserved | **FAILURE** "the package declaration was deleted" | pass |
| `theReviewsInputKeepsItsPackage` | the review's exact input | **FAILURE** "UPS2-STRIP: real Java package declaration was deleted" | pass |
| `crlfLineEndingsAreStrippedSafely` | CRLF header and body | **FAILURE** (the header was not removed) | pass |
| `aSecondCommentIsNeverConsumed` | a later, notice-looking comment | pass | pass |
| `aNonLeadingNoticeDeletesNothing` | code first, notice later: byte-identical | pass | pass |
| `anUnrecognisedLeadingCommentIsLeftAlone` | a leading comment that is not the notice | pass | pass |
| `bothDirectoriesAreStripped` | output in both configured directories | pass | pass |

Totals: **9 / 3 / 0 / 0 before, 9 / 0 / 0 / 0 after.** Each failure is a named assertion, and no compile error
counts as a witness.

**The notice-free DEMO output is now pinned.**
`GeneratedSourceIsPublishableTest#theDemoProcessorsCarryNoConfidentialityNotice` requires that none of the six DEMO
copies carry the notice, and that exactly six are checked. The analyser's `SessionProcessor` is named in that test as
an owner-decided exception (M19.22) and is not held to it.

## Wrong-result controls and restoration

Five controls are registered in `tools/mutation_controls_session.py`, so CI's gate runs them. Each was run once by the
fast engine: named assertion red, then restored green, with source and classes byte-identical.

| Control | Mutation | Caught at |
|---|---|---|
| `ups2-notice-strip-stops-at-the-first-terminator` | restores the old pattern **and** its `flags="s"` | `GeneratedHeaderStripTest#theReviewsInputKeepsItsPackage`: "UPS2-STRIP: real Java package declaration was deleted" |
| `ups2-notice-strip-removes-the-header` | the notice strip never matches | `GeneratedHeaderStripTest#theGeneratedHeaderIsRemoved` |
| `ups2-demo-publication-has-no-notice` | the notice put back into a DEMO copy: the keyless equivalent of the review's disable-and-regenerate | `GeneratedSourceIsPublishableTest#theDemoProcessorsCarryNoConfidentialityNotice` |
| `ups2-demo-implements-the-released-cycle` | `runInEventCycle` renamed in the recorded DEMO processor (N1, the review's own mutation) | `FixtureGeneratorToolchainTest#theDemoProcessorsImplementTheReleasedCycle` |
| `ups2-capture-tool-takes-the-pom-runtime` | the capture tool's jar re-pinned to 1.0.16 (N3) | `FixtureGeneratorToolchainTest#theCaptureToolTakesTheRuntimeFromThePom` |

**Manual cycle**, independent of the harness, for the notice-return control on
`examples/fixture-generator/src/main/java/com/acme/demo/generated/DemoQuoteProcessor.java`:
1. A byte copy was saved; sha256 `e7137e7212eeddef7f9ad966dfa896b03dc7653f391d2c1aa0d0c32f3cd380a6`.
2. With the notice prepended, `GeneratedSourceIsPublishableTest` gave 3 / 1 / 0 / 0 at the named test.
3. The file was restored with `cat copy > file`; sha256 identical.
4. The rerun gave 3 / 0 / 0 / 0.

Nothing was restored with `git checkout` or `reset`. `git status` showed no mutated file afterwards.

## Notes N1–N4 — dispositions

- **N1 — accepted, small check added.** The six DEMO copies must declare
  `public void runInEventCycle(Object auditEvent, Runnable action)`
  (`FixtureGeneratorToolchainTest#theDemoProcessorsImplementTheReleasedCycle`, control above).
  - It is a declaration check only. The method's behaviour is the runtime's and the generator's, and is exercised
    upstream (mongoose `GeneratedCycleAdminAuditTest`). A behavioural probe in this repository is left as a follow-up.
  - RESULTS.md no longer implies that byte-identical fixtures say anything about the new method.
- **N2 — accepted, recorded, not changed.** The source/GraphML test checks seven names, not generation identity.
  RESULTS.md does not cite it as provenance. Strengthening it is tracker **UPS-2a**.
- **N3 — accepted in part.**
  - **Fixed:** `tools/capture-bundle-conversations.py`, the active tool, now reads `fluxtion.version` from the root pom.
    It is guarded and has a control.
  - **Recorded as UPS-2a:** `tools/gen-fqn.py` chooses a jar by a sorted glob. It is an explicit-argument doc
    generator, and not a build dependency.
  - **Left alone, as the review advised:** the historical latency-kit scripts (`1.0.15-SNAPSHOT`), historical spikes
    and vendored upstream documents. They are not current dependency declarations.
- **N4 — accepted.** RESULTS.md now states the header policy as a table:
  - DEMO fixtures strip both the attribution and the notice;
  - `SessionProcessor` keeps the notice, an owner decision (M19.22);
  - the shipped start-page DEMO set is not regenerated;
  - the filesets are the two non-recursive DEMO directories.

  The CHANGELOG now says "DEMO and replay **test** fixtures", and that the start page's DEMO set is unchanged.

## My counts (total / failures / errors / skips)

| Run | Result |
|---|---|
| Focused: publication, toolchain, strip, `PomShapeTest`, replay runner, `*Replay*Test` | **87 / 0 / 0 / 0**, 13 reports |
| `mvn -q clean test` | **3096 / 0 / 0 / 218, 407 reports, no orphans.** That is `fdb1c7d9`'s 3084 / 218 / 406 plus this round's 12 tests (strip 9, publication 1, toolchain 2) and one new report class |
| Controls preflight | 40 frame suites, **564 anchors** (559 + the 5 new) |
| `tools/test_project_chart_review.py` | 5 tests, OK |
| `mkdocs build --strict` | clean |
| `git diff --check` (staged and `origin/main..HEAD`) | clean |
| CLAUDE.md sweep, exactly as written | empty |
| Attribution or any email in the 27 tracked generated and DEMO source files | none |
| Private paths in added lines | none |

The frame suites were not re-run locally: no UI code changed. CI's ui-frame job at the new head is the zero-skip
display gate, and its result is reported on the PR as read.

## Unverified, and awaiting the owner

- **Not verified:**
  - a hosted regeneration under the fixed strip: unnecessary, since the transformation is tested directly;
  - `runInEventCycle`'s behaviour in this repository (N1 follow-up);
  - the full documentation-capture journey after the N3 change. Only the tool's syntax and its pin were checked.
- **Owner decisions:**
  - whether the shipped start-page DEMO set is refreshed;
  - one header policy for every generated artefact (M19.22: whether `SessionProcessor` also drops the notice).
