# Fifth re-review: fixes for the fourth re-review (R-A, R-B, R-C, O-1–O-3)

**Subject:** `feat/mongoose-audit-production-rebased` at `98148175`. I reviewed the diff from `74d5a009`: P9
`82858d92`, fixes `5000171f`, and report `98148175`. The base is still `610d5777`. I did not merge, rebase,
release, or use an API key.

**Reviewer:** the author of rounds 2–4 (`68660535`, `8514f91b`, `1c706216`).

**Labels:**
- **RUN (me):** I ran it.
- **RUN (sub):** a subagent ran it in `/private/tmp/rr5-mut`.
- **READ:** I read the code but did not run it.
- **REPORT:** the author's word, not checked.

**Scope.** This round's code change is about 30 lines in `PerNodeLevelChanges.closing()`, plus tests. I sized the
checking to that:
- my own strict witnesses on the changed code;
- plants in the new branches;
- an independent derivation of R-B;
- one full suite.

A subagent was also re-running every earlier witness. I stopped it after 114 runs, because nearly all of them
target code this round did not touch. The 114 completed runs all hold: 79 of the 80 expected-red runs went red,
and the one that did not is `F5-empty`, masked as known since round 2. None of the 114 failed a hygiene check.
The frame flake (optional item 10) was not re-run; round 4 measured it, and this round changes `MainFrame` in a
comment only.

## Verdict

**R-A, R-B, O-1, O-2 and O-3 are fixed and witnessed. R-C is only partly fixed.**
- The new matrix test does not reach four branches of the sentence.
- Its guard catches only the literal "this processor".
- The comment and the report say it covers every branch.

**One new Low finding is of the class this whole thread has been fixing.** When a change's applying is not
established, the sentence opens "this log sets riskMonitor's audit level to WARN". That asserts the setting, and
the next sentence then says it may not have applied. It has been there since RR-3; I missed it in rounds 2–4.

**Is the analyser side ready once these are addressed?** Yes. Both required items are test and wording changes,
with no logic change, and nothing else is open on this side beyond the carried and agreed items.

## Answers to the brief

1. **R-A: holds (RUN (me)).**
   - `aNodeNamedNullIsSetUnderBothReadings` now reaches `closing()`'s `"null".equals(nodeId)` branch.
   - Disabling that branch (`if (false)`) makes the test fail at "R-A: both readings end it", with a `<failure>`,
     not an `<error>`.
   - The source was restored to the same SHA, `git status -- src` was clean, and the class went green again.
2. **R-B: holds (RUN (me)).**
   - **My derivation of the premise.** The runtime rule is that a change applies to a processor with grouping G if
     G is null, or G equals the change's `groupId`.
     - *Declared context:* the closing change shares the context and affects the node, so it is YES. Its applying
       is never open.
     - *Absent context, given that c applied:* G is null or `c.groupId`. If `c.groupId` is null, G must be null,
       and every change applies. If `c.groupId` is some grouping a, then under G = a the closing change applies
       only when its `groupId` is a.
     - So the closing change's applying is open exactly when the context is absent, `c.groupId != null`, and
       `c.groupId` is not equal to `next.groupId` (null included). That matches `:386`.
   - **Probe:** the three closing kinds (per-node, `"null"`, and a node named `"null"`) × 3 contexts (absent,
     declared-null, `alpha`) × 3 values of `c.groupId` × 3 values of `next.groupId`. That gives 81 logs, 63 of
     which are annotated.
   - **Result:** 0 mismatches against the derivation. The disclosure appears in all 12 open cases, across all three
     `closing()` branches. It never appears where applying is established, and "sets it to" never appears where
     it is open.
   - **The positive control is right.** With the same grouping on both changes, c having applied implies the
     closing change applied, so "sets it to INFO" is definite under the premise the sentence carries.
   - **Witnesses:** `closeOpen = false` fails at "R-B: that the closing change applied is not established".
     `closeOpen = true` fails at the positive control. Both runs were restored, clean, and green again.
3. **R-C: partly fixed (RUN (me)).** Finding **R5-1**. The matrix reaches 108 of 108 annotations, but none of
   those 108 inputs pairs a declared-null grouping with a non-null `groupId`, and none closes with a *different*
   `groupId`.
   - **Unreached branches:**
     - the YES note at `:328` ("which applies because the control record declares no grouping");
     - all three `closeOpen` variants in `closing()`, `:390-404`.
   - **What the plants show:**
     - A plant in `:328` leaves all 45 tests green.
     - A plant in the `open` literal at `:388` is caught only by R-B's exact-phrase assertion, not by the matrix.
     - My plant in the generic closing literal at `:402`, a branch the matrix does reach, goes red at
       `noBranchOfTheSentencePresumesAProcessor`.
   - **The guard is too narrow:** "…sets it to INFO for its processor" passes it, and all 45 tests stay green.
4. **O-1: reads correctly (RUN (me), across 191 sentences).**
   - "it" can no longer refer to the closing change.
   - In the spanning case, "before the marker" comes one sentence before "A stream-end marker before record N"
     introduces the marker. It reads acceptably; see optional item O5-3.
5. **O-2: holds (READ).** The `MainFrame` diff changes comment lines only, and the comment now states the rule at
   `:4353`: skip when the current findings already carry the damage.
6. **O-3: holds (RUN (me)).**
   - All five headed files are byte-identical beneath the header to their state at `74d5a009`.
   - The two unheaded files, `probe-0b7076fd.txt` and `probe-after-fixes.txt`, are raw: each still contains its
     two `updating event log config` lines, so no header is owed.
   - `probe-after-rereview-fixes.txt` equals `probe-after-fixes.txt` minus those lines, so its header is accurate.
7. **Witnesses (RUN (me), strict protocol, 8 runs).**
   - **Red at the named test:** R-A, R-B-false, R-B-true, R-C (post-marker plant), and R-C (my generic-closing
     plant).
   - **Not red:** the `:328` plant (0 failures), the `open`-literal plant (red only at R-B's phrase test, not at
     the matrix), and the "its processor" plant (0 failures).
   - Every run started from a green 45-test baseline, restored to the same SHA with a clean git status, and went
     green again afterwards.
   - This agrees with the report's five witnesses. It disagrees with the report's R-C row (item 9).
8. **Suite (RUN (me)).** **1980 / 0 / 0 / 62** across 258 XML reports, every one mapped to a class in
   `src/test/java`. There were no orphans; `DoubleBomDiagTest` is gone from this worktree's reports. This matches
   the claim.
9. **Report accuracy (READ).**
   - The R-C row is wrong in two places:
     - It says the fix covers "the undeclared YES note", and that the matrix makes the comment "true by
       construction". R5-1 shows neither holds.
     - *My error:* in round 4 I called `:328` "the YES note for an undeclared grouping". It is reached with a
       **declared-null** grouping and a non-null `groupId`, and that mislabel may be why the matrix missed it.
   - The 84-versus-86 attribution is right. It was my miscount, relayed from a subagent.
   - The frame-flake paragraph is a faithful account of what I measured.
10. **Frame flake:** not run this round (see Scope).
11. **Anything new:** R5-2 below.

## Findings

### Required

**R5-1 (Low): R-C's matrix does not cover every branch, and its guard is narrower than its name.**
- **Where:** `ControlAddressAndScopeTest.java:257-297`; the comment at `PerNodeLevelChanges.java:341-342`; the
  report's R-C row.
- **Failure scenario:** plant "…, so it is this processor's" into `:328`, or "for its processor" into the per-node
  closing at `:393`. All 45 MA-8 tests stay green, and the comment still says every branch is checked.
- **Cause:**
  - The matrix's groupings are {declared null / no gid, absent / no gid, alpha / alpha, absent / alpha}, and its
    closing change always reuses the opening's `groupId`.
  - The guard is the literal substring "this processor".
- **Fix:**
  - Add a `G("null", "alpha")` grouping row, and a closing-`groupId` dimension of {same, `"beta"`, none}.
  - Keep the "annotated count ≥ N" floor, raised to the new count.
  - Replace the guard with: every occurrence of "processor" in a note must be followed by " grouping", for
    example `Pattern.compile("processor(?! grouping)")`. The only legitimate uses today are "processor grouping"
    and "states no processor grouping".
  - Correct the report's R-C row.
- **Regression and witness:** the matrix goes red under each of these plants:
  - the `:328` plant;
  - a plant in each `closeOpen` string;
  - the "for its processor" plant.

**R5-2 (Low): an opening whose applying is open still says "sets".** `PerNodeLevelChanges.java:317`.
- **Failure scenario:** the round-4 attack log (no `groupingId:` lines; WARN addressed to `alpha`; a `Quote`;
  INFO addressed to `beta`; a `Quote`) annotates as: "this log sets riskMonitor's audit level to WARN at record 1
  (logTime 1). The control record states no processor grouping, so whether this change … applied here is not
  established."
- **Why it's wrong:** the first sentence states as established what the second says is not. R-B removed exactly
  this for the closing change, and its test asserts `!contains("sets it to INFO")`. The opening change is held to
  a weaker standard.
- **Cause:** the opening was written before RR-3 introduced NOT_ESTABLISHED, and no round re-read it.
- **Fix:** when `c.applies() == NOT_ESTABLISHED`, open with "this log records a change setting riskMonitor's audit
  level to WARN at record 1 …". Keep "this log sets" for YES.
- **Regression and witness:** in the matrix, for every absent-grouping case, assert that the note does not start
  with "this log sets". Witness it by restoring the old opening.

### Optional

**O5-1: "It holds until" overstates when the closing change's applying is open.** In an open case, "It holds until
record 3, which records a change … whether that applied here is not established either" still says the level ends
at record 3. "It holds at least until record 3, which …" says only what is known. The window closing there stays
conservative.

**O5-2: the generic closing with the disclosure repeats itself.** `:402-404` renders as: "…and only the first would
end it there, if it applied — it was addressed to no processor grouping; whether that applied here is not
established either". Suggested: "…and only the first would end it there, and only if it applied here: it was
addressed to no processor grouping, and whether it applied is not established either."

**O5-3: "before the marker" comes before the marker is introduced.** In the spanning sentence, "the marker" is used
one sentence before "A stream-end marker before record N begins a later run". "before the stream-end marker at
record N" names it where it is first used.

## What I got wrong

- **R-B's premise.** R-B in round 4 was right, and the premise I derived there holds.
- **The `:328` label.** Round 4 called it "the YES note for an undeclared grouping". It is the declared-null case.
- **R5-2 is four rounds old.** It is the same class as R1, which I required. I should have checked the opening
  when I checked the closing.
- **The earlier witnesses.** I set a subagent re-running all 84 of them for a 30-line change. That was more than
  the change needed, and the owner said so. I stopped it after its 114 runs had come back clean.

## Ran vs read

- **RUN (me):**
  - the R-B probe, 81 logs, with its independent derivation;
  - the combined sentence probe, 191 sentences;
  - 8 strict witnesses (45-test baseline each), all restored with the same SHA and a clean git status;
  - the O-3 byte comparisons across 7 evidence files;
  - the full headless suite, 1980/0/0/62 over 258 mapped reports.
- **RUN (sub, stopped early):** 114 strict runs:
  - the earlier witnesses, all as expected;
  - the round-4 author witnesses (R1, R2, O-A, O-C ×4, O-D), plus O-A-closing, which is now red at
    `aNodeNamedNullIsSetUnderBothReadings`;
  - C-plants up to Cp21. Its Cp08 plant, at `:328`, also produced no failure, which confirms R5-1 independently of my run.

  The remaining C-plants and the closing-disclosure plants were not run by the subagent; my own runs cover the
  latter.
- **READ:** the `MainFrame` comment diff; the report and tracker text.
- **Not run:** MARereviewProbe; frame tests.
