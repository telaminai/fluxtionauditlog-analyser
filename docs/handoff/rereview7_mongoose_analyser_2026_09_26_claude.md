# Seventh re-review: the sixth re-review's fixes (R6-1, R6-2, R6-3, O6-1–O6-4)

**Subject:** `feat/mongoose-audit-production-rebased` at `d8512121`. I reviewed the diff from `e82808e7`, the merge
of main 1.20.1, which the integration review `99f9ec47` already covered. The commits are:

- `ba743a29`: P11, committed before the fixes;
- `772f690e`: the fixes;
- `d8512121`: the report and tracker.

`git rev-list --count HEAD..origin/main` prints **0**, and `origin/main` is `710dc2ca`. I did not merge, rebase,
release, use an API key or run the mutation gate. This round has no witnesses, at the owner's instruction, so I
judged the tests by reading them.

**Reviewer:** the author of rounds 2–6, reviewing without a subagent's help. Two sets of probes are committed with
this review in `docs/handoff/evidence/mongoose-audit-production-impl/rereview7-probe/`:

- `R7.java` and `R7b.java` print real annotations for twelve constructed logs;
- `R7Matrix.java` replays the test's 360-log matrix and records which cells produce each reach phrase.

Every finding below quotes their output verbatim. The outputs have the runtime's stdout line filtered out.

## Verdict

**R6-1 and R6-2 are fixed for the case they named, and the tests would not pass on the old code.** An unreadable
control record now ends the window, and a closer across a marker is named rather than said to hold.

**The same class is back in this round's own new text, where the conclusion's bound is written.** Of the round's
three self-found items, the first two made the conclusion "its own sentence, bounded by where the window ends".
That is done only on the branch with no premise. The bound itself starts at the beginning of the log, not at the
change. So the analyser now states two things the log does not establish:

- **R7-1:** with no grouping declared, after an unreadable closer or a closer across a marker, the note concludes
  "If the change at record 1 applied here, riskMonitor's lines below WARN are not in this log". The only condition
  is *applied*, and the conclusion runs past the record that may have restored INFO, or past the marker.
- **R7-2:** "Before record 4, riskMonitor's lines below WARN are not in this log" is **false as written** when
  riskMonitor logs at record 1, before the WARN change at record 2. It is also false when another grouping's record
  before record 4 has such a line. The same holds for "Before the stream-end marker preceding record K".

Read the same way, the rest of the sentence gives:

- **R7-3:** the same unbounded conclusion, **already present** before this round, in the plain readable closer and
  in "Nothing later";
- **R7-4:** a closer across a marker in an ungrouped context says the next change "sets it to INFO". That is the
  word this round's own item 3 says may not be said there;
- **R7-5:** "It holds" is said of a change whose applying is not established;
- **R7-6:** "The next control record in the same grouping" is false when a readable control record for another node
  comes first.

**Is the analyser side ready once these are addressed? Yes.** R7-1 to R7-3 share one fix: bound every conclusion
from the change to the window's end, within its grouping. R7-4 to R7-6 are wording. The regressions need the
matrix to see what it cannot today: a change that is not at record 1, a second grouping, and a view wholly before a
marker with the closer past it.

## Answers to the brief

### 1. R6-1: an unreadable control record

- **The mechanism is right (READ, RUN).**
  - `of()` records a control row whose `eventToString` is missing or does not parse, with its context (`:145-148`).
  - `annotationFor` takes the first such row in the same context that comes before the first readable change
    affecting the node, and ends the window there (`:282-290`).
  - "Same context" is the rule readable changes use, and it is the right one: another grouping's record cannot
    change this processor's loggers. It closes whatever the record's target is, which is correct, because an
    unreadable record's target is unknown. The conservative direction is preserved.
- **The other shapes (RUN):**
  - A **fully-qualified** control event is recorded, because `isControlEvent` compares the simple name (probe G).
  - An unreadable record in an **absent-grouping** context closes an absent-grouping change (probe A, H). Its
    conclusion is R7-1.
  - A row whose `record(row)` returns null is still skipped silently (`:139`). **No current store can reach this**:
    all four (`HeapLogStore`, `MappedLogStore`, `RolledLogStore`, `SpiLogStore`) return
    `RecordParser.parse(...)`, which never returns null. It is latent, so see O7-1.
- **Is the new clause true in every case?** Taking it in parts:
  - "It holds at least until record N, a control record this reader could not read; whether that record changed
    riskMonitor's audit level is not established" is true when the change applied.
  - Where applying is open it says "It holds" anyway (R7-5).
  - "Before record N, …" is false whenever record N-1 or earlier includes records the level never reached (R7-2).
  - Across a marker the clause says "The next control record", which can be false (R7-6).
- **`anUnreadableControlRecordEndsTheWindowAndSaysWhy` (`ControlAddressAndScopeTest.java:273`) (READ).**
  - It would fail on `e82808e7`, which said "Nothing later". It covers all three unreadable forms, and another
    grouping's record.
  - It uses only a declared-null context with the change at record 1, so it cannot see R7-1 or R7-2.
  - It **asserts R7-2's false wording** ("Before record 3, riskMonitor's lines below WARN are not in this log",
    `:283`), so it will need changing along with the fix.

### 2. R6-2: a stream-end marker between a change and its closer

- The closer is now named on all three kinds:
  - per-node: "which sets it to INFO";
  - "null"-node: "either way it changes this node", or "would change this node, and only if …";
  - generic: "only the first would change it".

  Each is covered definite and open, and the unreadable closer is covered too (`:444-459`, `:493-495`). There is no
  "It holds" past a marker in any matrix cell (RUN, `R7Matrix`).
- **Still said, or implied, to hold or survive across a marker:**
  - **"Nothing later … changes it, so … not in this log"** with a later run after the view (probe C, R7-3). No
    closer means no `crossing` is computed (`until == MAX_VALUE`), so the later run is never looked at.
  - **"which sets it to INFO" across a marker in an ungrouped context** (probe L, R7-4). R-B's premise, that the
    closer applied whenever the change before it did, rests on both changes reaching the same processor. A later
    run is not shown to be that processor.
  - **After an unreadable closer or a closer across a marker, with a premise:** the conclusion is unbounded (R7-1).
- **`aClosingChangeAcrossAMarkerIsNamedNotHeldUntil` (`:298`) (READ).**
  - It would fail on the old code, which said "It holds until".
  - Its positive control (no marker, still "It holds until record 4 … sets it to INFO") is sound.
  - Its `onlyBefore` case is the **only** thing anywhere that reaches the marker-bound branch (see 5).
  - It has no absent-grouping case, so R7-1's premise form is untested.

### 3. The implementer's own findings

1. **"The conclusion is now a separate sentence."** This is true only where there is no premise
   (`:396`, `ownSentence` and `closerAt`). The premise branch at `:397` is unchanged, as R7-1 shows (probes A, B, H).
2. **"The bound becomes the marker."** It does, for the premise-free case (probe E, and the test's `onlyBefore`).
   But **"Before X" takes in records the level is not known to reach**, which is the question the brief asks
   (R7-2):
   - records before the change itself (probe D: riskMonitor logs at record 1, WARN is at record 2, and the note
     says "Before record 4 … not in this log");
   - other groupings' records, which the window excludes but the bound does not (probe F).
3. **"changes" replaced "sets"** for the "null"-node closer across a marker. This is true, since both readings
   change a node named "null". **The same flaw nearby:** the per-node closer across a marker in an ungrouped
   context still says "which sets it to INFO" (probe L, R7-4). By this item's own reasoning, that is exactly what
   may not be said there. The R5-2 check (`ControlAddressAndScopeTest.java:409`) looks only at the opening and at
   "either way it sets", so it passes.

### 4. R6-3: phrasings and attributions (RUN, `R7Matrix-output.txt`)

**The tightened phrasings are each unique to their branch:**

| Phrasing | Cells | Where it appears |
|---|---|---|
| "If the change at record 1 (logTime 1) applied here, riskMonitor's" | 14 | only no-boundary, closed |
| "…applied here, riskMonitor's lines below WARN are not in this log; otherwise" | 32 | only no-boundary, i.e. the one conditional branch at `:397` |
| "the log renders both identically. " | 120 | exactly the other-node null opening's count: 1 × 5 groupings × 3 boundaries × 8 closings |

**The new phrasings** are each unique to their branch:

- ", a control record this reader could not read; " (15 cells, no-boundary unreadable);
- "could not be read by this reader; " (30, cross-marker unreadable);
- the two "…is at " leads (56 declared, 72 undeclared);
- "either way it would change this node, and only if" (8);
- "either way it changes this node" (44);
- "only the first would change it" (52);
- "is not established. Before record " (6).

The exception is **"which sets it to INFO. Before the stream-end marker"**, which is reached **only by spanning
cells** (22). It does not show that item 2's branch, every record in view before the marker, is reached, and
nothing in the matrix reaches it (see 5).

**The in-place corrections are accurate:**

- the round-5 R5-1 row's "two of the 24 were loose";
- the R5-2 and unnamed-"it" rows' "through reach";
- the "24 wordings" sentence.

### 5. The matrix

- **Checked:** 360 logs, all 360 annotated (RUN).
- **The new offender rules, each exercised and none vacuous as written:**
  - **R6-1:** 45 unreadable cells.
  - **R6-2:** every spanning and wholly-after closed cell crosses the marker, because the closer is at record 90,
    after it.
  - **O5-1 rewritten:** reached by the 8 open cross-marker cells.
  - **`bareIt`:** runs on every closed note.
- **Vacuous, to the brief's example: yes.** No cross-marker cell has every record in view before the marker.
  "spanning" views `{1, 2}` straddle it, and "whollyAfter" views `{1}` sit after it. The marker-bound branch is
  reached only by the dedicated test.
- **What the matrix cannot see, and so why R7-1 and R7-2 pass it:**
  - the opening change is always record 1, so nothing precedes it;
  - every log has one grouping;
  - no rule inspects the conclusion's bound.

  The report's "with every branch phrasing reached" (`report…:672`) overclaims by the one branch above.

### 6. The optional items (READ)

- **O6-1 is in:** the guard is `(?i)processor(?! grouping)`.
- **O6-2 is in:** `bareIt` is `(?i)\bif it (applied|survived|named)\b(?! here:)`, and it now runs on every note with
  a closing clause, including both new lead forms.
- **O6-3 is in:** the text is "either way it would end the window there".
- **O6-4, the CHANGELOG line.** It is accurate for the round's cases, but two clauses will be false once R7-1 and
  R7-4 are understood:
  - "A control record the analyser cannot read ends the explanation there" is not so for an ungrouped log, whose
    conclusion runs on (R7-1).
  - "one described as setting the level applied whenever the change before it did" is not so across a marker
    (R7-4).

  Revise the line with the fixes.

### 7. The same class, again: every clause of `sentence()`, `closing()` and `unreadableClosing()`

Each clause was read against the question "does it state as established something the log does not establish?",
especially for what the window does not see: records before the change, other groupings, later runs, and control
records it skipped.

| Clause | Verdict |
|---|---|
| opening: "this log sets …" / "records a change setting …" | right: R5-2 holds |
| YES groupId sentence; NOT_ESTABLISHED sentence | right |
| "Nothing later in <scope> changes it" | factual: no later readable change affects the node |
| "…, so X's lines below WARN are not in this log" | **not established** past a later run, and false for records before the change (R7-3) |
| "It holds until record N sets it to INFO" (declared, no marker) | right |
| the same in an ungrouped context | "It holds" is said without the premise (R7-5) |
| "It holds at least until …" (open closer) | right within a run |
| cross-marker "The next change … is at record N, which sets it to INFO" | right when declared; **not established** when ungrouped (R7-4) |
| cross-marker "The next control record … record N, could not be read" | "next" can be false (R7-6) |
| "Before record N / the stream-end marker preceding record K, …" | **false** when earlier records or other groupings contain such lines (R7-2) |
| ". If <premises>, … not in this log; otherwise …" after an unreadable or cross-marker closer | **unbounded** (R7-1) |
| spanning: "…those lines are absent only if … survived the marker" | right: a necessary condition |
| wholly-after: "If … survived the marker, …" | right, but its conclusion inherits R7-3's unbounded "not in this log" |

### 8. Gates (RUN, JDK 21.0.9)

- **`mvn -q clean package`:** exit 0. I summed the XML reports myself: **2102 / 0 / 0 / 98 over 278 reports, every
  one mapped to a class in `src/test/java`, with no orphans.** This matches the claim.
- **`MARereviewProbe`: not run.** Its source is not in any branch's history or anywhere on this machine. The
  report calls it "the reviewer's" probe. I verified only that the recorded outputs `rereview5-probe-after-fixes.txt`
  and `rereview6-probe-after-fixes.txt` are **byte-identical** (38 lines each). That is the claim as recorded, not
  a re-run. It is weak evidence either way, since the report says none of its cases reaches the new branches.
- **`mkdocs build --strict`** (Python 3.13): clean.
- **`git diff --check e82808e7 HEAD`:** clean.
- **The rule-1 sweep:** clean, over the branch and over this review's added files.
- **`git diff --stat e82808e7 HEAD`:** 7 files, and `MainFrame` is not among them, so the display suite is not
  needed.

### 9. Report accuracy (READ)

- **The sixth-round table matches the code, with two exceptions:**
  - "the first one in the same context, **before any readable change**, ends the window" (`report…:651`) should
    say "before the next readable change **to that node**". A readable change to another node does not stop the
    search (probe J).
  - "bounded by where the window ends" (`:663`) holds only without a premise (R7-1).
- **Nothing is claimed as witnessed or shown red this round.** The R6-3 row says "**Not shown red**" explicitly.
  The one red mentioned is item 3's: an existing check caught a first draft. That is a test failing on real code
  during the work, not a planted fault, and it is described accurately.
- **The status lines are correct as of `d8512121`.** "NOT ready until the sixth re-review's fixes are reviewed"
  (`:816`) now needs to read "…until the seventh re-review's findings are fixed". The tracker's "Suite 2102/0/98"
  (`tracker.md:227`) drops the errors column; the report writes 2102/0/0/98.

## Findings

### Required

**R7-1 (Low–Medium): after an unreadable closer, or a closer across a marker, the premise branch's conclusion is
unbounded.** This is R6-1's defect, in the branch the fix did not touch.

- **Where:** `PerNodeLevelChanges.java:397`. `ownSentence` and `closerAt` (`:391-396`) are used only when
  `premises.isEmpty()`.
- **Input → wrong result** (probe A, RUN): `control(no grouping line, WARN riskMonitor)`, `Tick`, an unreadable
  control record, `Tick`, viewing record 2. The note says: "…It holds at least until record 3 (logTime 3), a
  control record this reader could not read; whether that record changed riskMonitor's audit level is not
  established. **If the change at record 1 (logTime 1) applied here, riskMonitor's lines below WARN are not in this
  log**; otherwise this change explains nothing here."
- **Why it is wrong:** if record 3 restored INFO, riskMonitor's INFO lines after it contradict the conclusion,
  whether or not record 1 applied.
- **Also affected:** probe B, a closer across a marker, and probe H, an unreadable closer with `groupId` alpha.
- **Fix:** bound the conditional as the premise-free branch is bounded, for example "…is not established. If the
  change at record 1 applied here, then before record 3, …".
- **Regression:** add an absent-grouping case to both new tests, and a matrix rule (below).

**R7-2 (Low–Medium): the new bound starts at the beginning of the log.** "Before record N" and "Before the
stream-end marker preceding record K" take in records the level never reached.

- **Where:** `:393-396`, the marker form in the spanning branch, and this round's two tests, which assert the
  wording (`ControlAddressAndScopeTest.java:283`, `:312`).
- **Input → wrong result:**
  - **Probe D (RUN):** riskMonitor logs at record 1; `WARN riskMonitor` at record 2; `Tick`; an unreadable record;
    `Tick`; viewing record 3. The note says "**Before record 4, riskMonitor's lines below WARN are not in this
    log**". Record 1 contains one.
  - **Probe E:** the same with the marker bound. "Before the stream-end marker preceding record 4, …" with the
    same record 1.
  - **Probe F:** WARN in grouping `alpha`; a grouping-`beta` record with a riskMonitor line; …. The note says
    "Before record 4, …", and the window excludes the beta record while the bound includes it.
- **Fix:** bound from the change and within its grouping, for example "From record 2 until record 4, in the
  records sharing its grouping, riskMonitor's lines below WARN are not in this log", or "…until the stream-end
  marker preceding record 4".
- **Regression:** give the matrix an opening change that is not at record 1, with an earlier record in which the
  node logs, and a second grouping with such a line.

**R7-3 (Low, present before this round): ", so X's lines below WARN are not in this log" and "Nothing later …
changes it, so …" are unbounded in the same way.**

- **Where:** `:380`, `:396`, and the wholly-after conclusion.
- **Input → wrong result:**
  - **Probe I:** WARN at record 1; INFO at record 3; riskMonitor logs at record 4; viewing record 2. The note says
    "It holds until record 3 (logTime 3) sets it to INFO, **so riskMonitor's lines below WARN are not in this
    log**". Record 4 contains one.
  - **Probe C:** WARN at record 1, `Tick`, a marker, `Tick`, viewing record 2. The note says "Nothing later …
    changes it, so riskMonitor's lines below WARN are not in this log". The later run is never looked at.
- **Fix:** the same bound as R7-2. With no closer, bound it by the end of the change's run, or by the last record in
  view.

**R7-4 (Low): a closer across a marker in an ungrouped context says "sets" definitely.**

- **Where:** `:452`, and `closeOpen` at `:437`.
- **Input → wrong result** (probe L, RUN): no grouping lines; WARN riskMonitor with groupId `alpha`; `Tick`; a
  marker; `Tick`; INFO riskMonitor with groupId `alpha`; `Tick`. The note says "The next change to riskMonitor's
  audit level among the records that likewise state no grouping is at record 4 (logTime 4), **which sets it to
  INFO**", right after saying that record 1's applying is not established.
- **Why it is wrong:** R-B's premise, that the closer applied whenever the change before it did, holds for two
  changes reaching the same processor. A later run is not shown to be that processor. This round's item 3 makes
  the same point about "sets" in an ungrouped context.
- **Fix:** when `crosses` and the context is not declared, treat the closer as open: "…which records a change to
  INFO addressed to processor grouping 'alpha'; whether that applied here is not established either".
- **Regression:** extend the R5-2 check to any " sets it to " in an absent-grouping note.

### Optional

- **R7-5 (Low wording):** "It holds …" is said of a change whose applying is not established (probes A, H). "It"
  reads as the level, which is conditional. Write "If it applied, it holds …", or name the change.
- **R7-6 (Low wording):** the cross-marker unreadable clause says "The next control record in the same grouping,
  record N". A readable control record for another node can come first (probe J: record 3 is a readable control
  record for priceListener, yet record 4 is called "the next"). Write "A later control record in the same
  grouping, record N, could not be read …".
- **O7-1:** `of()` skips `record == null` before it can mark the row unreadable (`:139`). No current store reaches
  this. If a future store could return null, treat a row whose raw text names the control event as unreadable.
- **O7-3, outside this round's diff:** `mvn -q clean package` rewrites the committed `dependency-reduced-pom.xml`,
  adding `com.telamin:svc-admin-web:1.0.45`, so the committed copy is stale against `pom.xml` (RUN; I restored the
  file and did not commit the rewrite). Regenerate and commit it, or stop tracking it.
- **O7-2:** fix the report's two table phrasings, the "every branch phrasing reached" sentence, the status line at
  `:816`, and the tracker's suite count, as in 9.

### Owner decisions

- **The scope of the conclusion.** R7-2 and R7-3 can be fixed in two ways:
  - **(a)** bound "not in this log" by the change, the window's end and the grouping, which keeps a log-wide
    sentence;
  - **(b)** restate it about the records in view.

  (a) keeps what the annotation is for. (b) is simpler to keep true. I recommend (a).
- **Whether R-B's premise extends across a run boundary.** I read it as not extending (R7-4). If the owner knows
  that a stream-end marker in these logs never separates processor configurations, that is a statement about the
  producer, and it should be written down, not inferred.

## Ran vs read

- **RUN:**
  - `git rev-list --count HEAD..origin/main`: 0, and `origin/main` is `710dc2ca`.
  - `mvn -q clean package` on JDK 21.0.9: exit 0; **2102 / 0 / 0 / 98 over 278 mapped reports, no orphans**
    (summed by me).
  - `R7.java` (nine cases, A–I) and `R7b.java` (three, J–L), every annotation read in full.
  - `R7Matrix.java`: the test's 360-log matrix replayed; phrase-to-cell counts recorded.
  - `mkdocs build --strict`, `git diff --check e82808e7 HEAD`, the rule-1 sweep and `git diff --stat`.
- **READ:**
  - the full diff of `PerNodeLevelChanges` and `ControlAddressAndScopeTest`;
  - the whole of `of()`, `annotationFor`, `sentence()`, `closing()` and `unreadableClosing()`;
  - the four stores' `record()`;
  - the sixth-round report section, the CHANGELOG and tracker lines, "Where phase 1 stands", and my sixth
    re-review.
- **Not run:**
  - `MARereviewProbe`: the source is unavailable; only the recorded outputs were compared.
  - the mutation gate: at the owner's instruction.
  - the display suite: `MainFrame` is not in the diff.
  - earlier rounds' witnesses.
- **Unverified:**
  - whether any SPI plugin store in use outside this repository can return a null `record()` (O7-1);
  - the producer-side question behind the second owner decision.
