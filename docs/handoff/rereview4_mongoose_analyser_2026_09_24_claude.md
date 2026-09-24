# Fourth re-review: fixes for the third re-review (R1, R2, O-A–O-F)

**Subject:** `feat/mongoose-audit-production-rebased` at `74d5a009`. It has two commits on top of `fc9b1f9c`:
`adc96d52`, the P8 predictions, and `74d5a009`, the fixes. The base is still `610d5777`, and nothing is merged.
I did not merge, rebase, release, or use an API key.

## Independence

I wrote **`68660535`** (the second re-review) and **`8514f91b`** (the third). I wrote neither `2d7ac12f` nor
`cd063e89`. R1 is a clause I missed in round 2 and named in round 3. **O-C's new wording is my own
suggestion**, and it causes finding O-1 below. Read this round with both facts in mind.

Every result below is marked:
- **RUN (me):** I ran it.
- **RUN (sub):** a subagent ran it under my brief, in its own worktree `/private/tmp/rr4-mut`.
- **READ:** I read code or logs and ran nothing.
- **REPORT:** the author's word, not checked.

## Verdict

**R1, R2 and O-A–O-F are fixed as described, and every claimed witness holds.**

- Headless suite: **1978/0/0/62** over 258 reports, and every report maps to a source class.
- The window closing at a `"null"` rendering only ever withholds an annotation. I found no case where it makes a
  definite conclusion false.

**Three small corrections are required before merge.** Each is a test, or a clause plus a test.
- **R-A:** O-A's closing branch has no witness.
- **R-B:** the closing clause asserts that the closing change applied, when that is not established.
- **R-C:** the source says "checked on every branch" for the processor-presumption rule. That is still not true;
  four of the sentence's branches have no guard.

**The intermittent frame failure reproduced here once in 10 runs at `74d5a009`, and 0 in 10 at `fc9b1f9c`.** The
evidence points to a flaky test that fails when its window loses focus, not to this round's change (section 6). It
is not a merge blocker, but a PR's CI frame job is the only clean way to settle it.

## 1. R1 and the closing clause (RUN (me))

**Sentence probe:** `Combos4.java`, 110 cases. The sweep crosses:
- the viewed node: `riskMonitor`, or a node literally named `"null"`;
- the source: per-node or `"null"`;
- 4 grouping cases: declared-null, absent, alpha=alpha, and absent with gid alpha;
- 3 closings: none, per-node, `"null"`;
- 3 boundary cases: none, spanning, wholly after.

Two attack cases were added on top.

**Results:**
- No case contains "this processor" or an unqualified "for every node".
- Every conclusion carries its open premises.
- The one `null` annotation, a view after the close, is a withheld annotation.

**Conservative-direction claim: held.** A closing change bounds only which in-view records the sentence speaks
about (`annotationFor`, `r >= until → break`). Every conclusion is about records before it. So a wrong close
withholds an annotation for later records and never adds a false one. I found no counter-case.

**The longest sentence is 1005 characters**, for a `"null"` source, gid alpha, not established, spanning, closed by
`"null"`. It is five sentences, each doing one job, so it is readable, but heavy. Wording aside, there is nothing to
fix there.

## 2. R2 and the processor-presumption hunt (RUN (sub))

**R2 holds.** The declared-branch mutant, from round 3's F-1, now goes **red** at
`positiveControls_aRealPerNodeAndARealGlobalChange` ("R2: a shared grouping is not a processor"). That same mutant
stayed green in round 3.

**Hunt method:** "this processor['s]" was planted into each prose literal of `sentence()` and `closing()` in turn.
Each plant ran against the 43 tests of `CoveragePerNodeLevelTest`, `ControlAddressAndScopeTest` and
`PerNodeLevelChangesTest`.

**Plants that stay all-green:**
- all three branches of `closing()` (Cp33–36);
- the YES note for an undeclared grouping, `:328` (Cp08);
- the NOT_ESTABLISHED addressed-grouping clause, `:334` (Cp11);
- the spanning branch's post-marker text, `:357-359` (Cp26, Cp27).

**The S2 guard catches only the possessive form.** The guard at `ControlAddressAndScopeTest.java:161` checks
`"this processor's"`, so a plain "this processor" plant passed it: Cp12 and Cp22 were green, while their `'s`
variants were red. Several other branches are caught only because an exact phrase breaks, not by a presumption
guard. Those are listed in the subagent's table (Cp04, 07, 09, 13, 23–25, 28, 29, 31).

## 3. O-A (RUN (me) and RUN (sub))

**Probe (me):** a node named `"null"` × {not established, declared} × {none, spanning, wholly after} × {no close,
closed}.
- The *named no node* premise is dropped everywhere for that node.
- *applied here* and *survived the marker* are still carried in every conclusion.
- The closing branch ("names no node or this node, which is named "null" — either way it ends here") agrees with the
  opening ("either way it sets this node").

**The opening witness holds (sub):** `aNodeNamedNullIsSetUnderBothReadings` goes red. The closing branch has no
witness; see R-A.

## 4. O-B (RUN (me) and READ)

- **Could a new failure be skipped?** No, not within one store. `liveReadFailed` is sticky, and the sentence
  depends on `index.size()`, which cannot change once reads fail. So a second failure is the same fault, and
  skipping it is right. After a reload, `MainFrame.java:4000` rebuilds `producerDiagnostics` from the new store,
  which has no damage. The first failure there is therefore never contained, and is not skipped (READ). This is the
  hole the author's first version had. **The author's account of that version is right.**
- **`containsAll` with several sentences** is the right test: every current damage sentence must already be shown.
  `messages()` is `findings.map(Finding::message)`, verbatim (READ).
- **An empty damage list** can come from an `IOException` other than malformed bytes, such as a vanished file.
  `containsAll([])` is true, so the tick skips. That is correct, because nothing changed.
- **Headless model of the rule, 1M records, 8 failed ticks (RUN (me)):**
  - 1 rebuild on the failed ticks, 108.9 ms;
  - the checks total 5.5 ms;
  - `SOURCE_DAMAGE` first.

  This is consistent with the author's **2 with the check** (the load's rebuild plus the first failure) and **10
  without**. I did not reproduce the through-the-jar counter; that part is REPORT.

## 5. O-C, O-D, O-E, O-F (RUN (me) and RUN (sub), except where marked READ)

- **O-C:** "within the run it was made in", now at `:356`. The witness is red in both halves (sub). The new wording
  has a readability issue; see O-1.
- **O-D:** the grouping note is now its own sentence on every branch. MARereviewProbe's one changed annotation shows
  it ("…at record 1 (logTime 1). It was addressed to processor grouping 'alpha', …"). The witness is red (sub).
- **O-E:**
  - The CHANGELOG (`:54-56`) matches the code.
  - A same-length replacement keeps the fault. I ran this at `fc9b1f9c`, and `appendFrom`'s logic is unchanged in
    `74d5a009`, which touches only the javadoc (READ).
  - A longer replacement reloads.
  - A reload that meets a replacement caught mid-character is the carried cold-open limit, and the javadoc now names
    it.
- **O-F:** the spec quotes both of the code's phrases verbatim.

**MARereviewProbe (RUN (me)).** Run against `svc-admin-web-1.0.45.jar` (SHA-256 `6839817621a57fcb…`) and runtime
1.0.16, its non-annotation lines match `rereview2-` and `rereview3-probe-after-fixes.txt`. The only changed
annotation is O-D's.
- The committed `rereview3-probe-after-fixes.txt` is 37 lines; the raw stdout is 48.
- The 11-line difference is the runtime's own `updating event log config:` lines, which are filtered out without a
  note. See O-3.

## 6. The intermittent frame failure (RUN (me) and READ)

I ran the CI job's 12 frame classes on this Mac's display, 10 times at each commit, alternating.

| Commit | Runs | Result |
|---|---|---|
| `74d5a009` | 10 | 9 × 63/0/0/0; **run 9: 63/1/0/1** |
| `fc9b1f9c` | 10 | 10 × 63/0/0/0 |

**What failed in run 9:**
- The failure was `NamedGraphAndMenuSpotlightFrameTest.lightingASecondMenu_keepsWhatTheEchoSaid_byReplaceAndByAdd`
  at `:377`: "the non-menu spotlight survived the File menu closing; the AI item is lit ==> expected: <[status,
  menu:AI:Posture]> but was: <[status]>". That is the failure the author reported.
- The same run had the suite's one skip: `PersonAtTheScreenFrameTest`'s `assumeTrue` on keyboard focus. That test
  skips only when the window cannot hold focus. So the failure co-occurred with lost focus, on a machine where I
  share the screen with its owner.

**Why this round's change is unlikely to be the cause (READ):**
- The test class never mentions follow or `producerDiagnostics`.
- This round's only main-code change outside the sentence is `refreshFollowDiagnostics`. It is reached only from
  `pollFollow`, and only while following.
- One failure in 10 against zero in 10 does not tell the two commits apart statistically.

**CI history (READ):** the `ui-frame` job ran 154 times on main and PRs between 2026-09-16 and 2026-09-24. There
were 3 failures:
- `DesignSpotlightFrameTest` ×2;
- a `SpotlightFrameTest` skip-guard;
- and **none in this test**.

`ci.yml` triggers on pushes to main and on pull requests, so **CI's frame job has never run on this branch**.

**My conclusion:** the failure is a focus-sensitive flake that this code change did not cause. Whether it is
pre-existing is shown only as far as "0 in 10 at the previous commit, 0 in 154 CI runs on main". CI's xvfb job on a
PR is where to settle it, since it has no competing focus.

## 7. The stale XML (RUN (sub))

- **My counts** come from 258 reports, and every one maps to a class in `src/test/java`. There are no orphans in
  `rr4-mut`.
- **The author's `target/`** holds 259. `TEST-…parse.DoubleBomDiagTest.xml` (1/0/0/0, 08:07) has no source.
  Excluding it gives 1978, which matches the author's account.
- **The class was never in git.** `git log --all -S DoubleBomDiagTest` hits only the report text, and a path search
  is empty.
- **Round 3's 1976** was summed over 258 reports in `rr3-mut`, with no orphan, so it was not inflated.
- **Round 2's 1971** cannot be checked, because its `target/` no longer exists.

**The author's account is right.**

## Findings

### Required

**R-A (Low): O-A's closing branch has no witness.** `PerNodeLevelChanges.java:379-382`.
- **Failure scenario:** set `:379` to `if (false)`. All 43 MA-8 tests stay green (RUN (sub)). With that mutant:
  - A node named `"null"`, with a WARN change rendered `sourceId=null` and later an INFO change rendered
    `sourceId=null`, is told "…and only the first would end it there".
  - For that node this is false: under either reading the INFO change sets it, so the window ends there either way.
- **Correction:** extend `aNodeNamedNullIsSetUnderBothReadings` with a closing `"null"` change. Assert "either way it
  ends here", and assert the absence of "only the first would end it". Witness it with the mutant above.

**R-B (Low): the closing clause asserts the closing change applied, when that is not established.**
`PerNodeLevelChanges.java:378`, `"It holds until " + at(next) + " sets it to " + next.level()`.
- **Failure scenario:** four records with no `groupingId:` line, the shape of every binary-reader log:
  1. WARN for `riskMonitor`, `groupId=alpha`;
  2. a `Quote` record;
  3. INFO for `riskMonitor`, `groupId=beta`;
  4. a `Quote` record.

  With record 2 in view, the annotation reads "If it applied here … It holds until record 3 (logTime 3) sets it to
  INFO". (RUN (me), attack case.)
- **Why that is false:** under the premise, the processor was ungrouped or grouped `alpha`. If it was grouped
  `alpha`, record 3 did not apply and did not set it. The window closing there is conservative, as in §1, but the
  clause states as fact something the log leaves open. That is R1's class, in the other `closing()` branch.
- **When it arises:** only in an undeclared context, when `c.groupId() != null` and `!c.groupId().equals(next.groupId())`.
  In every other case, "c applied" implies "next applied".
- **Correction:** in that case, say so: "…record 3, which records a change to INFO addressed to processor grouping
  'beta' — whether that applied here is not established either". Add a test and a witness.

**R-C (Low): "checked on every branch" is still untrue.** `PerNodeLevelChanges.java:340-341`.
- **Failure scenario:** plant "this processor's records" into any `closing()` literal, the `:328` YES-undeclared
  note, the `:334` addressed-grouping clause, or the post-marker text at `:357-359`. All 43 tests stay green (RUN
  (sub), Cp08, 11, 26, 27, 33–36).
- **Correction:** one assertion over the whole sentence matrix is enough. For example, extend the combination tests,
  or add one parameterised test over {source} × {grouping} × {boundary} × {closing}, asserting
  `!note.contains("this processor")`. Also widen the `:161` guard from `"this processor's"` to `"this processor"`.
  Or drop the comment's claim. Round 3's R2 was required for the same comment, so it should be made true.

### Optional

**O-1: "the run it was made in" now has two candidate antecedents.** `:355-356`.
- **Example:** in a spanning view with a closing change after the marker, the sentence reads "…It holds until record
  4 (logTime 9) sets it to INFO. Within the run it was made in, if it applied here, …". The nearest "it" is record
  4's change, which was made in the *later* run.
- **Cause:** my round-3 suggestion.
- **Correction:** "Before the marker, …", or "Within the run the change to WARN was made in, …".

**O-2: the O-B comment at `MainFrame.java:4304-4305`** says the refresh is "keyed on the source DAMAGE having
changed". The code keys on whether the current findings already carry it (`:4352`), which is the right rule; only
the comment's phrasing lags.

**O-3: `rereview3-probe-after-fixes.txt` omits the runtime's 11 stdout lines without saying so.** Add a one-line
header naming the filter, so that a diff against a raw run does not look like drift.

## What I got wrong

**Round 3's witness count.** My round-3 review said "86 strict witness runs". `/private/tmp/rr3-mut-out` holds
**84** distinct runs: 83 in `results-all.json` plus `C-RR2-brace-only`, which matches 84 `*-mut.log` files. I
relayed a subagent's count without checking it. All 84 were re-run at `74d5a009` and all match round 3, except
`S2c-declared`, which now goes red; that is R2 working.

## Ran vs read

- **RUN (me):**
  - MARereviewProbe, and a diff against both committed outputs;
  - `Combos4`, 110 sentences;
  - the O-B headless model, 1M records;
  - the CI history scan: 200 `ci.yml` runs, 154 `ui-frame` results, 3 failure logs read;
  - **20 display runs of the 12 frame classes**, 10 per commit.
- **RUN (sub):**
  - 84 earlier runs;
  - the five author witnesses in 8 variants, plus `O-A-closing`;
  - 36 presumption plants;
  - the full headless suite: **1978/0/0/62**, 258 XML files, every one mapped to source;
  - the orphan-XML analysis.
- **READ:**
  - the load-path rebuild at `MainFrame.java:4000`;
  - `messages()`;
  - the frame test's independence from `pollFollow`;
  - that `appendFrom` is unchanged since `fc9b1f9c`.
- **REPORT (not checked):**
  - the O-B rebuild counter through the jar (2 vs 10);
  - the author's two display runs.
- **Only CI's `ui-frame` job on a PR would show** whether the frame failure happens without a shared, focus-competing
  screen.
