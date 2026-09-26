# Sixth re-review: fixes for the fifth re-review (R5-1, R5-2, O5-1–O5-3, the unnamed "it")

**Subject:** `feat/mongoose-audit-production-rebased` at `4bb68d08`. I reviewed the diff from `98148175`: P10 in
`ae9fba62`, the fixes in `8200c4ab` and `7fd8d6a8`, and the report in `4bb68d08`. The base is still `610d5777`.
I did not merge, rebase, release, or use an API key.

**Reviewer:** the author of rounds 2–5. I used no subagent and did not re-run earlier rounds' witnesses. Every
result below is marked:
- **RUN**: I ran it.
- **READ**: I read it but did not run it.

## Verdict

**R5-1, R5-2, O5-1, O5-2, O5-3 and the unnamed-"it" fix all work.** Plants and mutations of each go red, and the
suite is 1980/0/0/62.

Re-reading every clause for the old class of flaw found two more instances:
- **R6-1: a definite conclusion that can be false.** It is the first one found in this thread, as opposed to
  wording. A control record the reader cannot parse is dropped silently, so the sentence says "Nothing later …
  changes it, so riskMonitor's lines below WARN are not in this log" past a record that may have restored INFO.
- **R6-2: "It holds until record N" asserts survival across a stream-end marker.** The next sentence of the same
  annotation then says survival is not established.

There is also one coverage-claim finding, **R6-3**. Two of this round's witnesses go red through the matrix's
*reach* assertion, not the check their label names. And two of the 24 reach phrasings can be satisfied by a
different branch.

**Is the analyser side ready once these are addressed?** Yes.
- **R6-1** is a small logic change: record the unreadable control rows and close a window at one.
- **R6-2** is wording.
- **R6-3** is two test phrasings, plus correcting the report.

Nothing else is open on this side beyond the carried and agreed items.

## Answers to the brief

### 1. R5-1

**Branches, enumerated from the code (READ).**
- **Openings:**
  - per-node, applied;
  - per-node, open;
  - null source for another node;
  - null source for a node named "null", applied;
  - null source for a node named "null", open.
- **YES notes:** none (`groupId` null), declared-null, and own grouping.
- **NOT_ESTABLISHED:** without a `groupId`, and with one.
- **Scope:** declared and undeclared.
- **`closing()`:** per-node, "null"-node and generic, each definite or open, which makes 6.
- **Condition:** no boundary, with and without premises; spanning, with and without premises; wholly after.
- **Subject:** "it", or "the change at record N".

**Coverage.** Every branch is reached by the matrix's inputs, and every one has a phrasing in the list, except one.
The YES note with a null `groupId` appends nothing, so it has no phrasing. That is correct, since it has no text to
guard.

**Two phrasings are loose** (R6-3):
- `"; otherwise this change explains nothing here"` and `"If the change at record 1 (logTime 1) applied here"` are
  both also produced by the wholly-after branch. Rewriting the no-boundary conditional (`:357-358`) to "Supposing …;
  else no explanation" leaves the matrix **green** (RUN). Only two other classes' exact-phrase tests catch it.
- `"that names no node — which would set every node's"` is shared by all three null-source openings. It does not
  prove the other-node opening, the one that adds the *named no node* premise, was reached.

**Counts.** 315 = 3 openings × 5 groupings × 3 boundaries × (1 + 2 × 3) closings. The count is right, and the test
asserts all 315 annotate. That holds: a declared `alpha` context closed by `beta` or none does not close, so the
annotation still reads "Nothing later".

**The guard `processor(?! grouping)`.**
- No legitimate text trips it. The only uses are "processor grouping" and "states no processor grouping".
- It is case-sensitive, so "this Processor's" passes (RUN, all 45 tests green). See O6-1.
- A presumption made without the word, such as "its producer" or "this flow", passes any word guard. That is
  acceptable; no such phrasing exists today.

**Plants (RUN).**
- At the declared-null YES note (`:332`): **red**, with an `R5-1 …` offender.
- **Mine, untried, in the new "That marker begins a later run" (`:372`): red**, with an `R5-1 …` offender, at
  `noBranchOfTheSentencePresumesAProcessor`.

### 2. R5-2

- "this log records a change setting …" appears exactly when `applies() == NOT_ESTABLISHED`. `applied = YES`, and
  NO never reaches a sentence (READ; the probe agrees).
- For a node named "null", "either way it addresses this node" is right while applying is open. "sets" is right for
  YES, because both readings set that node.
- The matrix asserts only the absent-grouping cases. Absent grouping is `!declared`, which is exactly
  NOT_ESTABLISHED, so it is the same set.

### 3. The same class, re-read clause by clause (RUN, `R6.java`, and READ)

- **(a) The definite closing "It holds until record N sets it to INFO" in a declared context.** Setting is right:
  the closing change shares the context and affects the node, so it is YES. *Holding* until then is not right when
  a stream-end marker lies between the two changes → **R6-2**.
- **(b) "Nothing later in … changes it".** This is false as stated when a later control record in the same context
  could not be parsed → **R6-1**.
- **(c) "which would set every node's audit level".** Conditional and accurate.

### 4. The unnamed "it"

- The fix is applied on every path that follows a closing clause. The first premise becomes "the change at record
  N" for:
  - the plain condition;
  - the spanning "if" and "only if";
  - the wholly-after "If".

  Later premises say "it" directly after that name, so the reference is unambiguous.
- The `bareIt` exemption for "only if it applied here:" cannot hide a real case, since that colon form exists only
  in `closing()`'s own clause.
- The regex is narrower than it looks: `if it applied here[,.]` misses "if it applied here and …" (O6-2).
- Restricting the check to notes containing "It holds " is right, because without a closing clause "it" has only
  one candidate.

### 5. O5-1

- "at least until" is produced by `closeOpen` alone, so it appears exactly where the disclosure does.
- Both disclosure strings end "is not established either". The opening's NOT_ESTABLISHED sentence ends "is not
  established." with no "either", so nothing else can produce the phrase.
- The XOR is the right pin, and its witness goes red for its own reason (RUN).

### 6. O5-2 and O5-3 (RUN, read in full)

**The generic open closing reads:** "…and only the first would end it there, and only if it applied here: it was
addressed to processor grouping 'beta', and whether it applied is not established either." It is correct.

**The "null"-node open closing reads:** "…— either way it would end it there, and only if it applied here: …". The
two "it"s refer to different things (O6-3).

**"the stream-end marker preceding record N" is accurate.** A marker is not a row, and `boundary + 1` numbers the
first record after it. For example, with a control, a plain record, a marker, then a plain record, the text says
"preceding record 3". The wholly-after branch keeps "a stream-end marker before record N begins it", which is also
accurate.

### 7. Witnesses (RUN, strict protocol, 9 runs)

Every run met these checks:
- the focused baseline was green (45 tests);
- reports were deleted before the mutated run;
- the failure was a `<failure>`, not an `<error>`;
- the source was restored to the same SHA, and `git status -- src` was clean;
- the re-run afterwards was green.

| Run | Mutation | Red at `noBranchOfTheSentencePresumesAProcessor`? | Which assertion caught it |
|---|---|---|---|
| R5-1 plant | `:332` "…, so it is this processor's" | yes | offenders: `R5-1 …` ✔ |
| R5-1 plant (mine) | "That marker begins a later run for this processor" | yes | offenders: `R5-1 …` ✔ |
| R5-1 plant, capital | "this Processor's" | **no, all green** | — (O6-1) |
| R5-2 | `applied = true` | yes | **reach**: "a branch … no longer reaches: [this log records a change setting, either way it addresses this node]" |
| R5-2, reach disabled (diagnostic) | same | yes | offenders: `R5-2 …` ✔ |
| unnamed "it" | `subject = "it"` | yes | **reach**: "[If the change at record 1 (logTime 1) applied here, only if the change at record 1 (logTime 1) survived]" |
| unnamed "it", reach disabled (diagnostic) | same | yes | offenders: `unnamed condition …` ✔ |
| O5-1 | `holds = "It holds until "` | yes | offenders: `O5-1 …` ✔ |
| loose phrasing | no-boundary conditional rewritten | **no** (matrix green; two other tests red) | — (R6-3) |

**What the table shows:**
- The R5-2 and unnamed-"it" checks do work: with the reach assertion disabled, each catches its mutation.
- But the witnesses as run go red for **reach**, not for the check their labels name. My R5-2 mutation changes both
  openings at once. I did not run the author's two per-opening variants, but reverting either opening also stops
  its reach phrase appearing, so reach should fire first there too.

### 8. Suite (RUN)

**1980 / 0 / 0 / 62** over 258 XML reports, every one mapped to a class in `src/test/java`. There are no orphans.
This matches the claim.

### 9. Report accuracy (READ)

- **The fifth-round table matches the code**, except the witness column for R5-2 and the unnamed "it". "red there"
  and "Witness: the bare 'it' restored → red" imply the named check caught them; reach did (R6-3).
- **"reaches each of 24 branch wordings, so a branch the inputs stop reaching fails"** overclaims for the two loose
  phrasings.
- **The in-place "Corrected in round 5 (R5-1)" note on the round-4 R-C row is accurate**, including the correction
  of my declared-null mislabel.
- **CHANGELOG.** "A change the log does not show to have applied is described as recorded, never as having set the
  level" is true of the opening. A definite closing in an absent context still says "sets it to INFO". That is
  right, because it applied whenever the change it closes did, but the line does not say so (O6-4).

## Findings

### Required

**R6-1 (Low–Medium): an unreadable control record is silently skipped, and the window runs on past it.**
- **Where:** `PerNodeLevelChanges.of()`, `:131-133` (`if (r == null) continue;`), with the sentence at `:350`.
- **Failure scenario (RUN):**
  1. `groupingId: null`, WARN for `riskMonitor`;
  2. a `Quote` record;
  3. `groupingId: null`, `EventLogControlEvent` with `eventToString: EventLogConfig{level=INFO,
     logRecordProcessor=Proc{a, sourceId=x}, sourceId=riskMonitor, groupId=null}`. This is refused by `parse()`,
     because `, sourceId=` occurs twice. A `logRecordProcessor` whose `toString()` contains a separator is enough.
  4. a `Quote` record.

  With record 4 in view, the output is: "this log sets riskMonitor's audit level to WARN at record 1 (logTime 1).
  Nothing later in the records sharing its grouping changes it, so riskMonitor's lines below WARN are not in this
  log."
- **Why that is false:** if record 3 restored INFO, the explanation is false, and it is stated as established. A
  level the reader doesn't know (`level=FINE`) or a missing `eventToString` gives the same result (RUN).
- **Cause:** RR-2 made ambiguous renderings "skipped, never read as global". The window logic was never told that
  something was skipped.
- **Fix:**
  - In `of()`, keep the rows of unreadable control records along with their context.
  - In `annotationFor`, an unreadable row in the same context closes the window. This is the conservative
    direction, like a `"null"` close.
  - Add a closing clause: "It holds at least until record 3, a control record this reader could not read; whether
    it changed the level is not established."
- **Regression and witness:** a test with the four-record log above. Records in view after record 3 are not
  explained, and records before it carry the new clause. Witness: restore `continue` → red.

**R6-2 (Low): "It holds until record N" asserts survival across a stream-end marker.** `closing()`, `:392-419`.
- **Failure scenario (RUN):**
  1. declared `groupingId: null`, WARN for `riskMonitor`;
  2. a `Quote` record;
  3. a stream-end marker;
  4. a `Quote` record;
  5. INFO for `riskMonitor`;
  6. a `Quote` record.

  Viewing records 2 and 3 gives: "It holds until record 4 (logTime 5) sets it to INFO, so before the stream-end
  marker preceding record 3, … That marker begins a later run, and the log does not say whether the level survived
  into it."
- **Why that is wrong:** the first clause says the level held into the later run; the third says that is unknown.
  "It holds at least until" has the same problem across a marker.
- **Fix:** when a run boundary lies between `c.row()` and `next.row()`, describe the closing change without holding.
  For example: "The next change to it is at record 4 (logTime 5), which sets it to INFO", or "…which records a
  change …" in the open case.
- **Regression and witness:** in the matrix, for spanning and wholly-after logs whose closing change is after the
  marker, assert the note has no "It holds". Witness: the current wording → red.

**R6-3 (Low): two witnesses and two reach phrasings claim more than they check.**
- **Where:** `ControlAddressAndScopeTest.java`, the `reached` list; the report's fifth-round R5-2 and unnamed-"it"
  rows.
- **Failure scenario (RUN):**
  - Rewrite the no-boundary conditional: the matrix stays green, because its phrases also come from wholly-after.
  - The R5-2 and unnamed-"it" mutations go red at reach, not at their own checks.
- **Fix:**
  - Tighten the two phrasings:
    - the no-boundary conditional: `"If the change at record 1 (logTime 1) applied here, riskMonitor's"`, with the
      comma directly after "here";
    - the other-node null opening: `"the log renders both identically. "`, with no " — either way".
  - For the two witnesses, either state in the report that they fire through reach, and keep the reach-disabled
    diagnostic as the evidence that each check works, or choose mutations that keep the reach phrases.
  - Correct the report's two rows and its "reaches each of 24 branch wordings" sentence.
- **Regression and witness:** the loose-phrasing rewrite above goes red at the matrix.

### Optional

- **O6-1:** make the guard case-insensitive: `(?i)processor(?! grouping)`. The witness is my capital-P plant, which
  is currently green.
- **O6-2:** widen `bareIt` to `(?i)\bif it (applied|survived|named)\b(?! here:)`. Today it misses "if it applied
  here and …". The subject replacement is global, so no current output is affected.
- **O6-3:** in the "null"-node open closing, write "either way it would end the window there". "it would end it"
  has two referents.
- **O6-4:** extend the CHANGELOG line with "; a change described as setting the level applied whenever the change
  before it did", so it covers the conditional definite closing.

## What I got wrong

- **R6-1 has been there since RR-2**, and I read `of()` in rounds 2–5. I checked what the window does with changes
  it has. I never checked what it does with control records it could not read.
- **R6-2 has been there since RR-4.** My round-3 sentence probe printed it ("It holds until record 4 (logTime 9)
  sets it to INFO. Within that run, …"), and I read it for a different flaw.

## Ran vs read

- **RUN:**
  - `R6.java`: 7 sentences read in full, including the three unreadable-control cases;
  - 9 strict runs as tabled: 7 witnesses or plants, plus 2 diagnostics with the reach assertion disabled;
  - the full headless suite, 1980/0/0/62 over 258 mapped reports.
- **READ:**
  - the branch enumeration of `sentence()` and `closing()`;
  - the report's fifth-round section and the round-4 correction;
  - the CHANGELOG;
  - P10.
- **Not run:**
  - earlier rounds' witnesses, as the brief asked;
  - MARereviewProbe;
  - frame tests (`MainFrame` is not in the diff).
