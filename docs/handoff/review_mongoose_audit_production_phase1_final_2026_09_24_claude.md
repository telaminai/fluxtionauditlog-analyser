# Final review — analyser side of phase 1, Mongoose audit production (reviewer: claude)

**Head:** `feat/mongoose-audit-production` at `7138dc6f`, base `fda01845`. `origin/main` is at
`7ecb0c38`. Plugins 1.0.45 is released and core is merged to `develop` at `2c4192e`; neither is
re-reviewed here.

**Verdict: CHANGES REQUIRED. One is blocking, and all are small.**

**The blocker.** The rebase is **not** mechanical. Rebased onto `7ecb0c38`, the suite fails.
- The branch rewrote pre-existing evidence: it stripped trailing spaces from
  `docs/handoff/evidence/mongoose-audit-production-2026-09-23/spike-output.txt` in `6ad7877d`.
- `main`'s new `TrailingWhitespaceTest` exists precisely to catch that.
- Restoring the file's original bytes makes the rebased branch pass.

That is also the sixth item missing from the report's "what I got wrong" (question 5).

**The other findings are smaller:**
- the structural BOM guard lets six other spellings through;
- the `RecordParser` narrowing is now measured: it is a real verdict change, and should be pinned by a
  test;
- F2 is fixed for Follow polls but not for opening a file that is still being written.

Nothing was fixed, merged or released on the branch. All experiments ran in disposable worktrees.

---

## F1 · BLOCKING · The rebase fails the suite: the branch rewrote byte-sensitive evidence (RAN)

**The run:**
- Rebased onto `origin/main` `7ecb0c38` in a scratch worktree: 16 commits replay with no textual
  conflict.
- `mvn -o test`: **1934 run, 1 failure**:
  `TrailingWhitespaceTest.everyByteSensitiveFixtureStillCarriesItsTrailingBytes:126`, "these fixtures lost
  their format-faithful trailing whitespace … `spike-output.txt`".

**Cause.**
- `6ad7877d` (MA-8) changed two lines of that file, dropping a trailing space after
  `eventLogRecord:` and after `nodeLogs:`.
- Those spaces are the captured output of the round-1 spike. That file is evidence, not prose, and `main`
  now lists it in `BYTE_SENSITIVE` (`TrailingWhitespaceTest.java:37,107`).
- On the branch alone nothing catches it. After a rebase, the suite does.

**Only file affected.** `git diff --name-status fda01845 7138dc6f -- docs/handoff/evidence`, excluding
additions, lists only that one file.

**Proved (RAN):** `git checkout origin/main -- <that file>` in the rebased tree →
`TrailingWhitespaceTest` and the BOM tests pass.

**Required:** restore the file's original bytes on the branch, as its own commit, then rebase. The
zero-filename-overlap check was right; the semantic check it stood in for was not. This is the question-4
answer.

## F2 · Medium · The structural guard is a text match and lets six spellings through (RAN)

I planted each spelling as one line in `HeaderParser.java` and ran
`ByteOrderMarkSitesTest#theBomRuleLivesInOneClass`, restoring by bytes after each:

| Spelling | Guard |
|---|---|
| `s.charAt(0) == 0xFEFF` (control) | red, caught |
| `s.charAt(0) == 0xfeff` (lowercase hex) | **green, hole** |
| `s.charAt(0) == 0xFE_FF` (digit separator) | **green, hole** |
| `(b[0]&0xff)==0xef && … 0xbb … 0xbf` (lowercase bytes) | **green, hole** |
| `b[0]==-17 && b[1]==-69 && b[2]==-65` (signed bytes) | **green, hole** |
| `s.charAt(0) == (char)(0xFE00 + 0xFF)` (constant expression) | **green, hole** |
| `s.charAt(0) == 0177377` (octal) | **green, hole** |

Your widening (`0xFEFF`, `65279`) closed the spelling you found; six more remain.

A text search cannot make "no seventh site" true, because a computed value always escapes it. **Required:**
1. Make the guard evaluate integer literals, not their text. Tokenise with a regex for Java integer
   literals (hex, octal, binary or decimal, underscores, either case) and flag any literal whose value is
   `0xFEFF`. Flag a file that holds all three of `0xEF`, `0xBB` and `0xBF`, as values, in either signed or
   unsigned form. That closes every row above except the constant expression.
2. **State the limit** in the test's javadoc and in the report: "catches literal spellings; a computed
   value is out of reach, so the behavioural tests are the real defence".
3. Keep the behavioural half load-bearing. It already drives every site with a BOM.

Until then, "the guard is load-bearing for no seventh site" claims more than the test does.

## F3 · Medium · The `RecordParser` narrowing is a measured verdict change: pin it (RAN)

**The measurement.** The same two headered records were run through `HeapLogStore` on the old head
`e8a1cfe7` (`String.strip()`) and the new one `7138dc6f` (ASCII strip), with the fields indented
differently each time:

| Indentation | Old | New |
|---|---|---|
| 4 ASCII spaces, or tab | parsed, clean | parsed, clean |
| U+3000 ideographic space | parsed, clean | **`event`=null, 0 node logs, `NO_NODE_LOGS`** |
| U+2003 em space | parsed, clean | **`event`=null, 0 node logs, `NO_NODE_LOGS`** |
| U+00A0 no-break space | already `NO_NODE_LOGS` (`strip()` does not treat it as whitespace) | unchanged |

**Question 1:** is any producer affected? None that I can find.
- A scan of `src/test/resources`, `src/main/resources` and `docs` for any line starting with non-ASCII
  whitespace finds one line in one docs evidence log, which is not an audit file.
- The Fluxtion runtime indents with spaces, and YAML forbids anything but spaces for indentation.

**My call: keep the narrowing, but not "recorded, untested".**
- It is consistent with §1a and with the framers and the marker. It is **not silent**: such a file raises
  `NO_NODE_LOGS`.
- That warning's message blames `addEventAudit()`, which would mislead whoever hits it.

**Required:**
- a test that pins the new behaviour for U+3000 and U+2003 (the two that changed);
- the report's "untested" entry replaced with this measurement.

**Optional:** a producer finding naming non-ASCII indentation, in place of the misleading `NO_NODE_LOGS`
cause.

## F4 · Low · F2 is fixed for Follow polls, not for opening a file mid-write (read)

`HeapLogStore.appendFrom` now decodes only complete UTF-8 characters. But `HeapLogStore.fromFile`
(`:117`) still uses `Files.readString`, and `MainFrame` enters Follow by opening with it and then calling
`forFollow()` (`MainFrame.java:3633`).

So opening a live file at the moment its tail is mid-character still fails, with the same
`MalformedInputException`, on the open rather than on a tick. The report's F2 entry should say "for Follow
polls".

Fixing it needs care: `fromFile` records a byte-exact read identity, so the identity must cover the
decoded prefix. Low, since a retry succeeds.

## F5 · Low · A byte-level stuck tail is now indistinguishable from a slow writer (read)

`completeUtf8` treats an incomplete trailing sequence as pending. A file whose last bytes are a lone lead
byte that will **never** be completed (corruption, a killed writer) now looks pending for ever instead of
failing a tick.

The record-level pending rule already reads such a tail as `unknown`, so no verdict claims more than it
knows (V3 holds). But nothing names it. Worth one sentence in the report; no code change is required.

---

## Question 3 · Is the partial still safe to merge after the additions? Yes, once F1 is fixed (RAN + read)

- **`completeUtf8`** runs only on Follow polls (`appendFrom`), not on every file.
  - Genuinely malformed bytes elsewhere still throw (`genuinelyMalformedBytesStillFailLoudly`).
  - On the rebased tree with F1 restored, the full suite passes.
- **`isControlEvent`** does run for every file, through `ONLY_CONTROL_EVENTS`.
  - The change narrows `contains` to an exact simple-name match.
  - The runtime writes the simple name (`event: EventLogControlEvent`, seen in every live run this cycle).
  - A fully-qualified name is still accepted, and no fixture in the suite depends on the lookalike.
  - A producer writing an inner-class name (`Outer$EventLogControlEvent`) would no longer count. I found
    none.
- Every open clause still leaves a surface **as it was** rather than wrong. That is the round-4 answer,
  unchanged.

## Question 5 · The record of what went wrong

The report's round-4 record lists five items: F1, the stale round-2 wording, the `all()` javadoc, the
double-BOM "fix" that did not apply, and the two witness corrections. **A sixth is missing: F1 above**,
where pre-existing evidence was rewritten in `6ad7877d`.

It matters more than its size:
- It is the same "a gate missing its exclusion" failure `TrailingWhitespaceTest` names.
- It went unnoticed because the branch predates that test.
- The claim that the rebase is mechanical rested on it not existing.

**Two smaller corrections to entries:**
- F2 should read "for Follow polls" (F4 above).
- The `RecordParser` entry should say it is measured (F3 above).

## Required corrections

1. **F1 (blocking):** restore `spike-output.txt`'s original bytes as their own commit, then rebase onto
   `origin/main` and re-run the full suite.
2. **F2:** make the guard evaluate literal values, and state its limit in the javadoc and the report.
3. **F3:** add a test pinning the U+3000 and U+2003 behaviour; replace "untested" in the report with the
   measurement.
4. **Report:** add F1 as the sixth item; qualify F2 as "for Follow polls"; add F5's one sentence.

With (1) done, the analyser merges as a partial. (2) to (4) can land in the same pass.

## What I ran versus what I only read

**Ran:**
- the rebase onto `7ecb0c38` in a scratch worktree, and the full suite on it (1934 run / 1 failure);
- the one-file restore, followed by the failing test plus the BOM tests (pass);
- seven guard spellings, each planted and restored by bytes;
- the whitespace-indentation matrix on old `e8a1cfe7` and new `7138dc6f` builds;
- a corpus scan for non-ASCII-indented lines.

**Read:**
- `main`'s diff for semantic overlap (0 `src/main` files, 1 `src/test` file);
- `HeapLogStore.fromFile`/`appendFrom`, and `MainFrame`'s Follow entry;
- `isControlEvent`'s call sites;
- the report's ledger.

**Not done:**
- no boot. The listing-freeze diagnosis and the app-level MA-0 claims remain as the report states them
  (mutation-proved; `ReaderRegistry` level).
