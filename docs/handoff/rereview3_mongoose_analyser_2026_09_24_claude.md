# Third re-review: the fixes for the second re-review (S1–S5, O1–O5)

- **Subject:** `feat/mongoose-audit-production-rebased` at `fc9b1f9c` (`9404a5fc` P7 predictions and
  `fc9b1f9c` fixes, on top of `3d41c3a7`). The base is still `610d5777`: the branch has not been rebased or
  merged. I didn't merge, rebase, release or use an API key.
- **Reviewer:** the author of the second re-review (`68660535`).
- **Method:** I checked the fixes by running probes and planting mutations. The counts below come from my
  own runs; the last section separates what I ran from what I only read.

## Verdict

**The five findings and the five optional items are fixed, and each fix holds under its own witness.**

- Every one of the author's ten witnesses goes red at its named assertion. So do all the earlier witnesses.
- I found no negative test that refuses a record before reaching the rule it names.
- The suite is 1976/0/0/62, as claimed.

**Two small corrections are required before merge.**

- **R1** is the S3 pattern in a clause S3's fix didn't reach.
- **R2** is a witness gap in S2's own claim.

Neither needs a redesign. Each is a one-line change plus an assertion. Six optional items follow. None of
them blocks a merge.

## What I ran

| Check | Result |
|---|---|
| `RR1Edge` (mine, from round 2), four cases | quiet second poll **0**, still UNKNOWN · valid growth after `C0` **throws** · longer rotation **−1**, UNKNOWN, no COMPLETE beside the fault (was 2 and COMPLETE at `3d41c3a7`) · truncation **−1**. The fault is listed first as `SOURCE_DAMAGE` in all of them. |
| `MARereviewProbe` against the published `svc-admin-web-1.0.45.jar` and runtime 1.0.16 | 48 lines, as at `3d41c3a7`. The diff is exactly two line pairs: the `"null"` annotation and the "only later run" annotation. I read both by eye (below). |
| Sentence probe: 2 sources × 4 grouping cases × 3 boundary cases = 24, plus 4 extras | Every conclusion carries every open premise. The `"null"` reading is never asserted before it is disclosed. "this processor's" never appears. Wording findings: R1, O-A, O-C, O-D. |
| S1 edges | A longer valid replacement ending mid-character: the failed store returns **−1** and a reload reads it. The reloaded store waits for the rest of the character, then appends **1** row (thread `wé`). Three further polls return **0**, so the reload doesn't loop. A same-length valid replacement is read as a quiet poll: **0**, and the fault stays (O-E). |
| O2 cost, synthetic followed log growing past a bad byte, 6 ticks each | 100k records (15 MB): failed decode 9–22 ms plus `ProducerDiagnostics.of` 10–49 ms per tick. 1M records (153 MB): decode 105–269 ms plus diagnostics **73–201 ms** per tick, on the Swing timer (EDT) every 1000 ms (O-B). |
| Witnesses (a subagent in its own worktree, strict protocol, 86 runs) | See the witness table. |
| F-1 mutant, run again by me in my own worktree | The declared-branch scope set back to "this processor's records": `CoveragePerNodeLevelTest` 24/0, `ControlAddressAndScopeTest` 12/0 and `PerNodeLevelChangesTest` 5/0 all stay **green**. The source was restored (SHA identical) and `git status -- src` is clean (R2). |
| Full suite | **1976 tests / 0 failures / 0 errors / 62 skipped**, summed from 258 surefire XML files. |

The strict witness protocol, applied to every run:

- the anchor matches exactly once;
- the focused baseline is green;
- surefire reports are deleted before the run;
- the mutated run fails with a `<failure>`, not an `<error>` or a compile failure, in the named method (the
  `(Path)` suffix is normalised);
- the source is restored byte-identical;
- the focused re-run is green.

All 86 runs met every step, including the compile, restore and green re-run checks. There was no `<error>`
anywhere.

### Witnesses

| Set | Runs | Result |
|---|---|---|
| Author's new witnesses: S1, S2a, S2b, S2c, S3, S4, S5.1, S5.2, O1, O5 | 10, plus 5 variants (S2a with the old wording, S2b-within, S2c-yes, S3-nopremise, O1 moved back to completeness) | **All red at the named assertion** |
| Earlier witnesses: the first review's 15 and RR-1…RR-4 with their sub-variants | 46 runs | All red, except three that are **expected** to stay green: the old `F5-empty` (masked by RR-3, as O5 said), the old `RR-1-pending` on the old test (the reason S4 exists; now witnessed), and the `C1` controls |
| S5.1 `isControlEvent → return true` | whole of both classes | Red **only** at `aRecordThatIsNotAControlEventIsNotReadAsALevelChange` and `aLookalikeEventNameIsNotAControlEvent`. It was green at the first of these at `3d41c3a7`. The real-name twin held under the mutant. |
| S5.2 `contains()` mutant | whole of both classes | Red only at `aLookalikeEventNameIsNotAControlEvent`, at the final assertion. The new `groupingOf` equality precondition and the twin both held. |
| Early-refusal hunt: each negative test in `CoveragePerNodeLevelTest` and `ControlAddressAndScopeTest`, with **its own** rule mutated to accept | every negative test | All red at their own assertion. **None refuses before its named rule.** The other tests stay green under the S5 plants because their non-control records carry no `eventToString`, and none of those tests names the event-type rule. |

Only two anchors moved: RR-4a-spanTrue and spanFalse. `firstInView < boundary` is now nested inside the
`boundary != null` branch, at `PerNodeLevelChanges.java:351`. It is the same predicate choosing the same
branch, so forcing it to `true` or `false` is the same mutation.

## Findings

### Required

**R1 (Low): the restore clause still asserts the no-node reading.**
`PerNodeLevelChanges.java:341-342`: `"It holds until " + at(next) + " sets it to " + next.level() + (next.sourceId() == null ? " for every node" : "")`.

- **What's wrong.** S3's fix rewrote the opening of the `"null"` sentence. It left out the clause that
  describes the *next* change when that change's rendering is `sourceId=null`. The spec's MA-8 now says the
  annotation "leads with both readings … never asserting that reading first"
  (`spec-mongoose-audit-production.md:380-386`). This clause asserts the no-node reading and never discloses
  the other one.
- **Failure scenario.** Build this log:
  1. `groupingId: null`, a WARN control for `riskMonitor`;
  2. a `Quote` record;
  3. `groupingId: null`, a control rendered as `sourceId=null`, level INFO;
  4. a `Quote` record.

  With record 2 in view, the output is: *"… It holds until record 3 (logTime 3) sets it to INFO for every
  node, so riskMonitor's lines below WARN are not in this log."* If record 3 was a change to a node literally
  named `"null"`, this is false: it set one other node, and riskMonitor was still WARN. The window's closing
  at record 3 is conservative, since it only withholds annotations after that record, so nothing is
  *mis-annotated*. The fault is that the sentence states as fact a reading the log cannot establish.
- **Correction.** Use the same disclosure as the opening clause, for example: "… when record 3 records a
  change to INFO that names no node — or a node literally called "null"". Add an assertion that a `"null"`
  restore's clause does not contain `"for every node"` unqualified. At `3d41c3a7` this clause read the same
  (`:308-309`), so I missed it in round 2 as well.

**R2 (Low): "never this processor's" has no witness on the declared-grouping branch.**
`PerNodeLevelChanges.java:338`: `declared ? "the records sharing its grouping" : …`.

- **What's wrong.** The comment at `:336-337` says the S2 rule is "checked on every branch". The witnesses
  guard only the undeclared branch (S2c) and the YES parenthetical (S2c-yes).
- **Failure scenario.** Mutate `:338` so the declared branch reads `"this processor's records"`. All 41
  tests in the three MA-8 classes stay green (verified above). A future edit can therefore put back the
  presumption S2 removed, for every runtime log, since the runtime always declares a grouping, and no test
  notices.
- **Correction.** Add an `assertFalse(note.contains("this processor"))` to one declared-grouping test, for
  example `positiveControls_aRealPerNodeAndARealGlobalChange`. Then witness it with the mutant above.

### Optional

**O-A: a node literally named `"null"` gets a false "otherwise".** `PerNodeLevelChanges.java:346`, and the
same `premises` list at `:313`.

- **Failure scenario.** A topology node named `"null"`, with a WARN control rendered `sourceId=null` in
  `groupingId: null`. The annotation for that node reads: *"If it named no node, null's lines below WARN are
  not in this log; otherwise this change explains nothing here."* Under the other reading the change named
  exactly this node, so it explains the node's silence either way. The "otherwise" is false.
- **Correction.** When `nodeId.equals("null")`, drop the *named no node* premise; both readings then
  conclude the same way.
- **Why it's optional.** It is an edge case. Node names come from `NamedNode`, so `"null"` is reachable but
  unlikely.

**O-B: O2 recomputes identical diagnostics on every failed tick.**

- **What's wrong.** `MainFrame.java:4344` bypasses `followNeedsDiagnosticRefresh` whenever
  `readFailed == true`. The comment at `:4304` says "the state change is what the refresh keys on", but the
  code keys on nothing. After the first failure, no rows are added and the state stays UNKNOWN, so every
  later failed tick rebuilds the same findings.
- **Failure scenario.** A 1M-record, 153 MB followed log growing past a bad byte. The diagnostics rebuild
  adds **73–201 ms** of EDT work every second. The failed re-decode, which already existed at `3d41c3a7`,
  adds 105–269 ms. Together they block the UI for 178–470 ms of every second.
- **Correction.** Refresh on a failed tick only while the cached `producerDiagnostics` does not yet carry
  the `SOURCE_DAMAGE` finding (or on the first failure), and fix the comment. The re-decode cost existed at
  `3d41c3a7` and is not re-reported.

**O-C: "that run" has no antecedent.** `PerNodeLevelChanges.java:353-354`.

- **Example.** In the spanning case, *"Nothing later in the records sharing its grouping changes it, so
  within that run riskMonitor's lines…"*. No run has been mentioned yet; the marker is introduced only in
  the following sentence.
- **Correction.** "within the run it was made in".

**O-D: the YES parenthetical attaches to the wrong clause in the `"null"` sentence.** `:311` and
`:321-325`.

- **Example.** For a `"null"` source with a matching `groupId`, the parenthetical lands straight after "the
  log renders both identically": *"…the log renders both identically (addressed to processor grouping
  'alpha', which is the grouping the control record itself declares)."* It reads as a gloss on the
  rendering.
- **Correction.** Start a new sentence: "It was addressed to processor grouping 'alpha', …".

**O-E: the CHANGELOG's replacement promise is broader than the code.** `CHANGELOG.md:53-54` says "If the
file is later replaced by a readable one, it is reloaded". Two cases don't do that:

- **A same-length valid replacement is read as a quiet poll.** It returns **0** and keeps the fault (probe
  above). This is honest, since the fault says "unknown until it is reopened", but it is not a reload.
- **A reload of a replacement caught mid-character hits the carried cold-open item.** `HeapLogStore.fromFile`
  throws `MalformedInputException` at `:138`. S1 routes this case into that item; before S1, the same bytes
  were read as an append.

**Correction.** "…replaced by a longer readable one…", plus a note on the javadoc's QUIET route at
`HeapLogStore.java:46-50` that a same-length replacement takes it.

**O-F: the spec's quote doesn't match the code.** `spec-mongoose-audit-production.md:385-386` quotes
"records sharing this grouping". The code says "the records sharing its grouping" and, for an undeclared
grouping, "the records that, like it, state no grouping". Quote both, or neither.

## The eight areas in the brief

1. **S1's rule.** Correct and loop-free:
   - a successful decode after a failure returns −1;
   - the reload builds a store without the failure;
   - growth past the bad byte keeps throwing, with no reload;
   - a quiet poll returns 0.

   The partial-character path works once the reload's read is whole. The mid-character reload is the
   carried cold-open item (O-E).
2. **S2.** All 24 combinations are correct. "this processor's" appears in none of them. Its witness gap on
   the declared branch is R2. Readability issues are O-C and O-D.
3. **S3.** The opening clause leads with both readings, and the parenthetical "which would set every node's
   audit level" is inside the disclosure. `positiveControls_aRealPerNodeAndARealGlobalChange`'s
   `contains("every node")` assertion is satisfied by that disclosure and does not pin an assertion. The
   restore clause still asserts (R1).
4. **S5.** Both plants go red at the named tests, with preconditions holding. No early refusal was found in
   either class.
5. **O1.**
   - The fault is in `sourceDiagnostics()` and listed first; the stream-end state is UNKNOWN with the
     ordinary note.
   - Only the two `ProducerDiagnostics.of` call sites (`MainFrame.java:4001`, `:4347`) read
     `completenessIsNote()` or `sourceDiagnostics()`, and both pass all three. Nothing else relied on the
     old `completenessIsNote() == false`.
   - The move is witnessed in both directions (O1-to-completeness, O1-away-from-source).
6. **O2.** Correct; the per-tick cost is O-B. I did not see the tooltip. The author's frame tests
   (63/0/0 with a display) were not re-run, because a background session would open windows on the owner's
   screen.
7. **O4.** The MA-2.9 wording and the tracker pointer are right. I verified the premise from source: mongoose
   `2c4192e`, `MongooseServer.addEventProcessor`, `:758`, sets
   `eventProcessor.setAuditLogProcessor(logRecordListener)` for **every** processor, with the one static
   listener. The report's "the reviewer's reading, not verified here" can now say "verified from source".
   Running it was not needed for a design requirement.
8. **CHANGELOG, markers and javadoc.**
   - The "superseded" and "wrong as written" markers are accurate. The witness hunt confirms that one
     negative test depended on grouping, not three.
   - The `liveReadFailed` javadoc names the three routes correctly, though its QUIET route also covers a
     same-length replacement (O-E).
   - The CHANGELOG is accurate apart from O-E.

## The report's "what I got wrong this round"

All five items are right as stated.

- **The O2 wrong-reason probe (item 4) matches the code.** At `3d41c3a7`, a quiet tick reaches
  `followNeedsDiagnosticRefresh` with the state moved from COMPLETE to UNKNOWN, so it refreshed. My
  `RR1Edge` quiet poll returns 0 on that path. Writes every 1.2 s against 1 s polls leave quiet ticks;
  writes every 200 ms leave none. The pre-fix, fix and fix-minus-catch results the report gives are what
  that code predicts. Keeping the wrong-reason probe on record is good practice.
- **Items 1, 2, 3 and 5** are consistent with the diff, the runner behaviour I saw in round 2, and the
  spec, where MA-2.9 comes last.

## Ran vs read

- **Ran (by me):**
  - `RR1Edge`;
  - `MARereviewProbe`, reading its two changed annotations by eye;
  - the 28-case sentence probe;
  - the S1 edge probe;
  - the O2 cost probe (12 ticks over two sizes);
  - the F-1 mutant, one focused run of three classes (41 tests), restored.
- **Ran (by a subagent under my brief, in its own worktree):**
  - 86 strict witness runs;
  - the early-refusal hunt;
  - the whole-class S5 plants;
  - the full suite, 1976/0/0/62 summed from 258 XML files.
- **Read, not run:**
  - the frame tests;
  - the tooltip;
  - the MA-2.9 premise in mongoose source;
  - the report's account of its own O2 probe runs.
