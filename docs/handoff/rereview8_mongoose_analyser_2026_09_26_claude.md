# Eighth re-review: the seventh re-review's fixes (R7-1–R7-6, O7-1, O7-3)

**Subject:** `feat/mongoose-audit-production-rebased` at `e5541d5b`. I reviewed the diff from `d8512121`, the commit
the seventh re-review covered. The commits are:

- `74460804`: the P12 predictions;
- `1fa55f66`: the fixes;
- `e5541d5b`: the report, the tracker and the evidence.

`git rev-list --count HEAD..origin/main` prints **0**, and `origin/main` is `710dc2ca`. I did not merge, rebase,
release, use an API key, run earlier rounds' witnesses or run the full mutation gate. I re-ran round 7's four
witnesses under the strict protocol. I also ran two probe mutations of my own, P1 and P2, to test the matrix's
rules. They are labelled as probes, not witnesses.

**Reviewer:** the author of rounds 2–7, reviewing without a subagent's help. Probes and outputs are committed with
this review in `docs/handoff/evidence/mongoose-audit-production-impl/rereview8-probe/`:

| File | What it does |
|---|---|
| `R8Review.java` | Prints real annotations for 15 constructed logs, M–Y3, including a plugin-style store whose `record()` returns null |
| `witness8.py` | `witness12.py` unchanged in protocol. It also prints every failing assertion and runs probe mutations P1 and P2 |
| `ruleorder8.py` | Removes the matrix's reach assertion for one run, so the offender rules are judged alone. Both files are restored by SHA-256 |

Every quotation below is verbatim from their outputs.

The owner's decisions of 2026-09-26 are checked here, not reopened:
1. the conclusion is bounded by the change, the window's end and the grouping;
2. R-B's rule does not extend across a marker;
3. there are targeted witnesses for R7-1–R7-4.

## Verdict

**R7-1 to R7-4 are fixed for the cases they named, and the four witnesses hold.** I re-ran them under the strict
protocol. Each goes red with a `<failure>` at the named test, and the message carries its label. Each restore
matched the SHA-256, `git status -- src` was clean, and the re-run was green. All twelve of my round-7 probes, A–L,
now read correctly. R7-5, R7-6 and O7-3 are fixed. O7-1 is fixed for the window, with two wording gaps (R8-4).

**Not ready yet.** Two required findings remain, both in the text after a stream-end marker. Neither was introduced
by this round: both date from RR-4. This round's own claim, "Every conclusion is bounded" (CHANGELOG), makes them
the part of decision (1) that is still undone.

- **R8-1: the spanning branch's later-run clause is unbounded.** "for the records in view from record 3 on, those
  lines are absent only if … survived the marker" takes in three kinds of record it should not:
  - records after the closer (probe M);
  - records past a second marker, while it names only one marker (N);
  - another grouping's records (O).

  The matrix's bound rule never sees this clause, because the clause does not say "are not in this log".
- **R8-2: wholly after a marker, a second marker makes the note false.** "Every record in view is in a LATER run — a
  stream-end marker before record 2 begins it" is said when the only record in view, record 3, is past a second
  marker (P). The conclusion's new bound then excludes every record in view, so the note explains nothing it is
  attached to.

**Ready once R8-1 and R8-2 are fixed.** One owner decision comes first: how far a change's reach is carried past more
than one marker. The fix for both is the same as round 7's: bound the later-run text by the marker, the closer and
the grouping. The matrix also needs a second marker, which it has never had.

## Answers to the brief

### 1. R7-1 to R7-3: the bounded conclusion (RUN)

**Probes A–L on `e5541d5b` (`R7`, `R7b`, re-run).** Every conclusion is now true as written. My output matches the
implementer's `R7-after-fixes-output.txt` line for line, apart from its two header lines and the final blank line
that its header says it drops. Three probes show the fix most clearly:
- **D:** "After record 2 and before record 4, in the records sharing its grouping, riskMonitor's lines below WARN
  are not in this log". Record 1, where riskMonitor logs, is excluded.
- **F:** bounded to its grouping.
- **L:** open across the marker.

**The hunt** (`R8Review`):

| Case | Probe | Result |
|---|---|---|
| a second marker inside the window | N, P, Q | **wrong** (R8-1, R8-2) |
| a closer before the first marker, with records in view after it | R | right: "It holds until record 3 (logTime 3) sets it to INFO, so after record 1 and before record 3, …". The records after the marker are outside the window and get no annotation |
| records in several groupings in view | S; O for the spanning clause | S is right. **O is wrong** (R8-1) |
| an untimed change | T | right: "at record 1 (untimed) … so after record 1, in the records sharing its grouping, …" |
| the change's own record is the last in view | U | right: no annotation, because nothing after it is in view |
| records in view after the closer, spanning | M | **wrong** (R8-1) |

**Is "in the records that, like it, state no grouping" right as a bound?** It is right relative to the model this
thread accepted in RR-3: records that share a grouping, or that all lack one, are read as one stream. It is also as
strong as that model and no stronger.

Take two ungrouped producers interleaved in one file:
- a change reaches only one of them;
- the note's definite form ("in the records sharing its grouping, … are not in this log") then covers the other
  producer's records too.

The class Javadoc states this limit (`PerNodeLevelChanges.java:57-63`). No note does. Now that the scope phrase is in
print, whether the note should say so is an owner decision (below). With no grouping declared, "If the change at
record C applied here" softens it, because "here" can be read as these records. With a declared grouping nothing
softens it.

### 2. R7-4: a closer past a marker with no grouping declared (RUN, READ)

- **Open in every closing kind.** With `crosses` and no grouping declared, `closeOpen` is always true
  (`PerNodeLevelChanges.java:463-464`), so every kind comes out open:

  | Kind | Wording |
  |---|---|
  | per-node | "which records a change to INFO addressed to …; whether that applied here is not established either" (probe L) |
  | a node named "null" | "…either way it would change this node, and only if it applied here…" |
  | generic | "…only the first would change it, and only if it applied here…" |
  | unreadable | only named ("A later control record … could not be read") |

- **The declared positive control stays definite.** `anUngroupedCloserAcrossAMarkerIsOpen` asserts "which sets it to
  INFO" for the declared log, and probe E shows the same.
- **The widened R5-2 check is sound.** With no grouping declared, `applies()` is `NOT_ESTABLISHED`, so neither the
  opening nor any closer can take a "sets" branch. The check is also not vacuous: with the R7-4 mutation and the
  reach assertion removed, it alone reports **72** offenders (`ruleorder8-output.txt`).

### 3. R7-5 and R7-6 (RUN, READ)

- **"If the change at record C applied here, it holds …" is right where applying is the only open premise.** It is
  incomplete where the naming premise is open too (R8-3):
  - **Probe X:** "If the change at record 1 (logTime 1) applied here, it holds until record 3". The next sentence
    conditions on "named no node and it applied here".
  - **Probes V, W** (declared, naming open): "It holds until record 3 (logTime 3) sets it to INFO" and "It holds at
    least until record 3" are said with no condition. If record 1 named a node called "null", riskMonitor was never
    at WARN.
- **"…, whose change to INFO applied wherever this one did" is a true reading of R-B within one run.** Using the
  runtime's rule (the processor is ungrouped, or its grouping equals the change's `groupId`):
  - a change with no `groupId` applies only to an ungrouped processor, which every closer reaches;
  - equal `groupId`s apply to the same processors.

  `crosses` forces the open branch first, so the phrase never appears across a marker. It carries RR-3's one-stream
  assumption, like every same-context reading.
- **"A later control record" is right in every case.** The unreadable record named is the first in the same context
  after the change and before the next readable change to the node (`:297-303`). That is a later control record,
  though not necessarily the next one (probe J).
- **Repeating the premise reads correctly** (probes A, H). "If the change at record 1 (logTime 1) applied here, it
  holds at least until record 3 …. If the change at record 1 (logTime 1) applied here, then after record 1 and
  before record 3, …" is redundant, but both conditions are the same and both are named. The one mismatch is X
  (R8-3).

### 4. O7-1: a null record whose raw text names the control event (RUN with a plugin-style store, READ)

- **A payload cannot write an `event:` line before the real one.** `rawEvent` returns the first line that starts
  with `event:` (`:167-174`). The runtime writes `eventLogRecord`, `logTime`, `groupingId` and then `event`, before
  any payload-reachable field (`eventToString`, `nodeLogs`). So the first match is the real one, and this holds on
  the same field order `groupingOf` relies on.
- **A raw text with no `event:` line is not safe.** `rawEvent` searches every line, including node values. Probe Y2
  has no top-level `event:` line, and a node value line reading `event: EventLogControlEvent`. It is called "a
  control record this reader could not read". That ends the window, which is conservative. It also states a fact the
  log does not establish (R8-4). With no such line anywhere, `isControlEvent(null)` is false and the row is ignored
  (Y3).
- **The time is lost.** Probe Y1's raw text has `logTime: 3`. The note says "record 3 (untimed)", because the
  `Unreadable` is built with a null `logTime` (`:142`). "(untimed)" is a claim about the record, and it is false
  here (R8-4).
- **Reach.** No store in this repository returns a null `record()`. I read the four `record()` implementations again
  and agree. Both gaps need a plugin store.

### 5. The matrix (RUN: replay, rule isolation, two probe mutations)

- **1440 logs, all 1440 annotated.** `R8Matrix` reproduces the implementer's output byte for byte, apart from its
  header.
- **The new dimensions are real.**
  - "lead" puts the node's line before the change (nodeLogsBefore) or in grouping `gamma` after it
    (otherGrouping).
  - "beforeMarker" gives a view wholly before a marker with the closer past it.
- **Each bound pattern is produced by exactly one (boundary, premise) branch**, and every conclusion branch that
  says "are not in this log" has a pattern. Two things have none:
  - the spanning clause "those lines are absent only if …" (R8-1);
  - `end2` or `end1` equal to a second marker, which the matrix never builds.

**The rules, taken alone** (`ruleorder8.py`, reach assertion removed):

| Mutation | Offender rule alone |
|---|---|
| R7-1 | R7 bound, **432** offenders |
| R7-2 | R7 bound, **1080** |
| R7-3 | R7 bound, **168** |
| R7-4 | R5-2/R7-4, **72** |
| **P1** — the definite bound ignores the first marker when a closer comes later | **0**: the rule passes |
| **P2** — the wholly-after bound loses its end (`end2` dropped) | **0**: the whole matrix passes |

- **Where the rule is vacuous.**
  - **P1:** `endsBeforeMarker` accepts any `"before record "` (`ControlAddressAndScopeTest.java:569`), so a bound
    running to a closer past the marker passes. Only the reach patterns and two dedicated tests catch P1.
  - **P2:** the `"after that marker"` exemption (`:564`, `:569`) lets a wholly-after bound lose its end. The matrix
    stays green, and only `aScopeWhollyAfterARunBoundaryGetsNoDefiniteClaim` and
    `notEstablishedAndWhollyAfterABoundaryConcludesOnBothPremises` go red.
  - The exemption as written ("a bound that starts after a marker may run on") is right only while there is one
    marker.
- **The sentence split** (`:559`) checks only sentences that contain "are not in this log". The spanning branch's
  later-run conclusion is outside it (R8-1).

### 6. The four witnesses (RUN, `witness8-output.txt`)

- **All four hold under the strict protocol:**
  - a green focused baseline, with the named test present;
  - reports deleted;
  - it compiled;
  - a `<failure>`, not an `<error>`, at the named test, whose message starts with its label ("R7-1 A: …", "R7-2 D:
    …", "R7-3 I: …", "R7-4 L: …");
  - SHA-256 restored, `git status -- src` clean, green after.

  "R7-4 L" labels two assertions in the same test. The one that fired is "not definite across a marker in an
  ungrouped context".
- **Each mutation restores the defect it names.**
  - R7-1 is round 6's premise branch exactly.
  - R7-3 is round 6's plain ", so " + lines exactly.
  - R7-4 is round 6's `closeOpen` exactly.
  - R7-2 restores round 6's "Before E, …" on the path probe D exercises, and D's assertion is the one that fires. On
    the plain path it writes ", so before record 3, …", which round 6 never did. That is harmless here: the witness is
    about D.
- **Which of the matrix's assertions fired**, which the report did not record:
  - Under R7-1, R7-2 and R7-3 it was the **reach** assertion ("a branch of the sentence the matrix no longer
    reaches"). That comes first and masks the offender rules.
  - Under R7-4 it was the **offenders** assertion.

  With reach removed, the new bound rule catches R7-1 to R7-3 on its own (5 above).

### 7. The rewritten assertions (READ; each one's failure under the witnesses RUN)

Each of the eleven still tests what its name says:
- `theLiteralNullIsTheStatedLimitNotAClaimedFix`, `notEstablishedAndWhollyAfterABoundaryConcludesOnBothPremises`,
  `notEstablishedAndSpanningABoundaryConditionsBothHalves` and `aNullSourceInAnUndeclaredGroupingCarriesBothPremises`:
  still one condition carrying every premise, now bounded.
- `aNodeNamedNullIsSetUnderBothReadings`: still definite.
- `aClosingChangeWhoseApplyingIsOpenSaysSo`: the positive control still asserts not-open.
- `anUnreadableControlRecordEndsTheWindowAndSaysWhy` and `aClosingChangeAcrossAMarkerIsNamedNotHeldUntil`: the
  bound is from the change.
- `aScopeWhollyAfterARunBoundaryGetsNoDefiniteClaim` and `aScopeSpanningARunBoundaryIsDefiniteOnlyBeforeIt`: see
  below.
- The matrix: extended.

**`aScopeSpanningARunBoundaryIsDefiniteOnlyBeforeIt` is a sound rewrite.**
- **The case:** the within-run view, record 2, precedes a marker, so its bound now names that marker. The old
  assertion, that no marker is mentioned, was pinning the unbounded form.
- **What the old assertion guarded:** a within-run note that claims something about a later run.
- **What guards it now:**
  - "nothing conditional" (no "survived"). In this declared log survival is the only possible condition, so the
    check is exact.
  - The positive bound, "so after record 1 and before the stream-end marker preceding record 3". The R7-3 witness
    fails exactly this assertion.

**Three negative guards can no longer fail** (R8-6). Each pins a form the code can no longer write, so it passes
whatever the note says:
- `aScopeWhollyAfterARunBoundaryGetsNoDefiniteClaim`: `assertFalse(", so X's lines below WARN …")`
  (`CoveragePerNodeLevelTest.java:543`);
- `notEstablishedAndWhollyAfter…`: `assertFalse("If it survived the marker, riskMonitor's")`;
- `notEstablishedAndSpanning…`: `assertFalse("so within the run")`.

Each test's positive assertion still catches the regression it names, so no test is weaker in effect. But the
guards are dead.

### 8. The same class, again: every clause of `sentence()`, `closing()` and `unreadableClosing()` (READ, RUN)

The question for every clause: does it state as established something the log does not establish?

| Clause | Verdict |
|---|---|
| opening ("this log sets …" / "records a change setting …", the null forms) | right |
| YES groupId sentence; NOT_ESTABLISHED sentence | right |
| "Nothing later in <scope> changes it" | factual about readable changes |
| definite bound "after record C [and before E], in <scope>, …" (every no-marker and before-marker form) | right under RR-3's model; E is the closer, the unreadable record, or the first marker, whichever comes first |
| premise bound "If …, then after record C …; otherwise …" | right |
| "It holds until … sets it to INFO" (declared, naming open) | **not established**: the naming premise is not carried (R8-3, V) |
| "It holds at least until …, a control record this reader could not read" (declared, naming open) | **the same** (R8-3, W) |
| "If the change at record C applied here, it holds …" (naming also open) | **incomplete** (R8-3, X) |
| "…, whose change to INFO applied wherever this one did" | right within one run |
| cross-marker closers: "The next change … which sets it" (declared) / "records a change … not established either" (ungrouped) | right |
| "A later control record …, could not be read" | right, except that a null-record row may not be a control record at all (R8-4, Y2) |
| "record N (untimed)" for a null-record row | **false** when its raw text has a time (R8-4, Y1) |
| **spanning: "for the records in view from record K on, those lines are absent only if … survived the marker"** | **unbounded**: after the closer (M), past a second marker (N), other groupings (O) (R8-1) |
| **wholly after: "Every record in view is in a LATER run — a stream-end marker before record K begins it"** | **false** with a second marker before the records in view (R8-2, P, Q) |
| wholly after: "If … survived the marker, then after that marker [and before E2], in <scope>, …" | the bound is right; the premise names one marker when two are crossed (R8-2) |

**What the new bound text itself asserts.** "and before the stream-end marker preceding record K" asserts nothing
beyond the window, and "in <scope>" asserts RR-3's model, as in 1. The bound text is sound. The defects are in the two
later-run sentences, which were not given the bound.

### 9. Gates (RUN, JDK 21.0.9)

- **`mvn -q clean package`:** exit 0. I summed the XML reports myself: **2104 / 0 / 0 / 98 over 278 reports, every
  one mapped to a class in `src/test/java`, no orphans.** This matches the claim.
- **`mkdocs build --strict`** (Python 3.13): clean.
- **`git diff --check d8512121 HEAD`:** clean.
- **The rule-1 sweep:** clean over the branch and over this review's added files.
- **`dependency-reduced-pom.xml`:**
  - **The content is now current.** After a clean package, the file with CRs removed is byte-identical to `HEAD`,
    so O7-3's staleness is fixed.
  - **A clean package still rewrites it.** The shade plugin writes it with **CRLF** line endings
    (`git ls-files --eol`: `i/lf w/crlf`). `git status` then reports it modified, with an empty `git diff`, until it
    is checked out again (R8-7).
- **`MARereviewProbe`:** run.
  - Its output is byte-identical to `rereview7-probe-after-fixes.txt` after the filter header.
  - Against round 6's recorded output, exactly three annotation lines differ, each with its `CoverageService` echo
    (12 diff lines). Each differs only by gaining its bound.
- **`git diff --stat d8512121 HEAD`:** 17 files, and `MainFrame` is not among them, so the display suite is not
  needed.

### 10. Report accuracy (READ)

- **The seventh-round table matches the code, with one overstatement.** The matrix paragraph says "a bound that
  starts at the change stops at the marker after it". The rule accepts any "before record N", including a closer
  past the marker (P1). Separately, the CHANGELOG's "Every conclusion is bounded … never for the whole log" is false
  for the spanning clause (R8-1).
- **The in-place "corrected in round 7" marks are accurate.**
  - The R6-1 row now says "before the next readable change *to that node*".
  - Found-item 1 now says the bound was on one branch only and started at the log's start.
  - The 360-matrix sentence now says one branch was reached only by a dedicated test.
  - The status line and the tracker's "2102/0/0/98" are corrected.
- **Nothing beyond the four witnesses is claimed as shown red.** "The fix was to make the matrix able to fail, which
  the witnesses now show it does" is true in substance, but not as recorded: under three of the four, the matrix
  went red through reach, not through the new rule. With reach removed, the new rule does catch them (5, 6).
  - Found-while-doing item 2 describes a first draft of the rule failing on correct code. That is not a planted
    fault, and it is described accurately.
  - P12.4 predicted "between 8 and 14 assertions", and the report answers "11 tests". The units differ, and it
    should say which it counted.
- **The two evidence files handled for `git diff --check` are faithful records.**
  - **`R8Examples-output.txt`:** the committed `R8Examples.java` prints the `«…»` headings itself (`:70`). My
    re-run is byte-identical after the two header lines. It is the program's output, not an edit of it.
  - **`R7-after-fixes-output.txt`:** its header says it omits the program's final blank line and nothing else. My
    re-run of `R7` and `R7b` differs from it by exactly the header and that line.

## Findings

### Required

**R8-1 (Low–Medium, present since RR-4): the spanning branch's later-run clause is unbounded.**
- **Where:** `PerNodeLevelChanges.java:434-436`. The matrix rule that would catch it looks only at sentences
  containing "are not in this log" (`ControlAddressAndScopeTest.java:559`).
- **Input → wrong result** (`R8Review`):
  - **M:** declared ungrouped; WARN riskMonitor at record 1; record 2; a marker; record 3; INFO riskMonitor at
    record 4; record 5; viewing records 2, 3 and 5. The note says "…for the records in view from record 3 on, those
    lines are absent only if the change at record 1 (logTime 1) survived the marker". Record 5 is after the INFO
    that "sets it to INFO", where survival is irrelevant.
  - **N:** two markers inside the window, viewing records 2, 3 and 4. It says "from record 3 on, … only if it
    survived the marker". Record 4 is past the second marker.
  - **O:** grouping alpha, and a beta record 4 in view. "from record 3 on" takes it in. Every other conclusion is
    now limited to the change's grouping.
- **Why it matters:** owner decision (1) bounds every conclusion by the change, the window's end and the grouping.
  This clause has none of the three past the marker. The CHANGELOG says there is no exception.
- **Fix:** "for the records in view after that marker and before E2, in <scope>, those lines are absent only
  if …", where E2 is the closer or the next marker, as `end2` already computes. How to treat records past a second
  marker is the owner decision below.
- **Regression:**
  - add a `twoMarkers` boundary and a view that includes a record past the closer to the matrix;
  - apply the bound rule to "absent only if" sentences as well as "are not in this log".

**R8-2 (Low–Medium, present since RR-4): wholly after a marker, a second marker makes the note false.**
- **Where:** `:437-442`. `marker` is the first marker after the change (`boundary`), not the one that begins the run
  holding the records in view.
- **Input → wrong result:**
  - **P:** WARN at record 1; a marker; record 2; a marker; record 3; viewing record 3. The note says "Every record in
    view is in a LATER run — a stream-end marker before record 2 begins it". Record 3's run begins at the marker
    before record 3. The conclusion, "then after that marker and before the stream-end marker preceding record 3,
    …", covers no record in view. So the note is attached to record 3 while explaining nothing about it, and
    "otherwise this change explains nothing here" implies the opposite.
  - **Q:** the same, viewing records 2 and 3. Record 3 is outside the bound, and the premise names one marker when
    two are crossed.
- **Fix:** name the marker that begins the first record in view's run, and carry survival of every marker between
  the change and the records concluded about. Alternatively, stop the annotation at the second marker. When no
  record in view lies inside the bound, do not annotate.
- **Regression:** the same `twoMarkers` boundary, and a rule that the marker named in "Every record in view is in a
  LATER run" precedes the first record in view with no other marker between them.

### Optional

- **R8-3 (Low wording, R7-5's class, for the naming premise).** `holdsLead` (`:510-513`) conditions only on
  applying.
  - **The symptoms:** with "named no node" open, "It holds until record 3 (logTime 3) sets it to INFO" (V) and "It
    holds at least until record 3 …" (W) are unconditional. X's closer condition omits naming.
  - **Fix:** pass the premise list to `holdsLead`, so its condition is the same as the conclusion's.
  - **Regression:** a matrix rule that a "holds" clause carries every premise the conclusion carries.
- **R8-4 (O7-1's follow-up).**
  - **The symptoms:** a null-record row is given a null `logTime`, so it reads "(untimed)" even when its raw text is
    timed (Y1, `:142`). `rawEvent` accepts an `event:` line anywhere in the raw text, so a node value can make a row
    "a control record" (Y2, `:169-171`).
  - **Fix:**
    - read `rawEvent` only up to the first payload field, as `groupingOf` stops at `event:`;
    - read `logTime` from the raw text, or say "its time was not read";
    - say "a record this reader could not read, whose text names the control event".
  - **Regression:** a unit test with a stub store, as in `R8Review`'s `NullingStore`.
- **R8-5 (matrix rules).**
  - `endsBeforeMarker`'s `"before record "` escape passes a bound that runs past the marker (P1).
  - The `"after that marker"` exemption passes a wholly-after bound with no end (P2).
  - Tighten both: when a marker lies between the change and E, E must be a marker. The exemption should hold only
    when there is no later closer and no later marker.
- **R8-6 (dead guards).** Update or remove the three negative assertions in 7, so each can fail under the current
  wording.
- **R8-7 (`dependency-reduced-pom.xml`).**
  - **Symptom:** it is current, but every clean package rewrites it with CRLF, and `git status` shows it modified.
  - **Fix:** stop tracking it (it is a build output), or set `createDependencyReducedPom` to false if nothing
    consumes it. **Not verified:** why the shade plugin writes CRLF on this machine.
- **R8-8 (Javadoc).** `rawEvent` was inserted between `groupingOf`'s Javadoc and `groupingOf` (`:159-166`), so
  `groupingOf` has no Javadoc and `rawEvent` has two. Move the new method above the old comment.
- **R8-9 (report).** Correct the three report points from 10:
  - the matrix paragraph's rule description (P1);
  - the "11 tests" unit against P12.4's assertions;
  - which of the matrix's assertions fired under each witness (reach for R7-1–R7-3, offenders for R7-4).

### Owner decisions

- **A change's reach past more than one marker (R8-1, R8-2).**
  - **(a)** Carry survival of every marker crossed ("…only if it survived the markers before records 2 and 3").
  - **(b)** Stop the annotation at the second marker, so records past it get none.

  (a) keeps more annotations and adds longer conditions. (b) is simpler to keep true, and it makes the same kind of
  call as decision (2). I recommend (b).
- **Whether a note should disclose RR-3's one-stream limit.**
  - **The problem:** "in the records sharing its grouping, … are not in this log" is definite, and it is false for
    a second producer that shares the grouping. The limit is stated only in the class Javadoc.
  - **Options:** a short clause ("read as one stream") or leaving it to the documentation.
  - **Recommendation:** documentation, if the owner knows these logs come from one producer per grouping. That is a
    statement about the producer, and it should be written down.

## Ran vs read

- **RUN:**
  - `git rev-list --count HEAD..origin/main`: 0, and `origin/main` is `710dc2ca`.
  - `mvn -q clean package` on JDK 21.0.9: exit 0; **2104 / 0 / 0 / 98 over 278 mapped reports, no orphans**
    (summed by me).
  - A second `mvn -q clean package -DskipTests`, to observe `dependency-reduced-pom.xml`.
  - My `R7` and `R7b` (A–L), re-run; `R8Review` (M–Y3, 15 cases), every note read in full.
  - The implementer's `R8Matrix`, `R8Examples` and `MARereviewProbe`: compiled and run, all byte-identical to the
    recorded outputs after their headers.
  - `witness8.py`: round 7's four witnesses under the strict protocol, **4/4 hold**, plus probe mutations P1 (caught
    by reach and two dedicated tests, not by the bound rule) and P2 (the matrix green, caught by two dedicated
    tests). Its closing "A WITNESS DID NOT HOLD" refers to P1 and P2, which are probes and are marked BAD by design.
  - `ruleorder8.py`: each offender rule judged alone, with the counts in 5. Both files restored by SHA-256, and 49
    MA-8 tests green after.
  - `mkdocs build --strict`, `git diff --check d8512121 HEAD`, the rule-1 sweep and `git diff --stat`.
- **READ:**
  - the full diff of `PerNodeLevelChanges`, `ControlAddressAndScopeTest` and `CoveragePerNodeLevelTest`;
  - all of `of()`, `annotationFor`, `sentence()`, `closing()`, `unreadableClosing()` and `holdsLead`;
  - `witness12.py`;
  - the P12 predictions, the seventh-round report section and its in-place corrections, and the CHANGELOG and
    tracker lines.
- **Not run:**
  - earlier rounds' witnesses and the full mutation gate, at the owner's instruction;
  - the display suite, because `MainFrame` is not in the diff.
- **Unverified:**
  - whether any SPI plugin store outside this repository returns a null `record()` (R8-4);
  - why the shade plugin writes CRLF here (R8-7);
  - the producer-side facts behind both owner decisions.
