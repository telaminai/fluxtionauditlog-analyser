# Phase 1 — report, revised after review, 2026-09-23

Implementing [`spec-mongoose-audit-production.md`](../specs/spec-mongoose-audit-production.md) at
`fda01845`. **Phase 1 is NOT complete**: the ship-critical item and MA-5 are done, the analyser items
are done in substance but three acceptance clauses are outstanding and named below. Nothing is merged,
released or deployed. No Fluxtion API key was used.

## Branches

| Repo | Branch | Head | Base |
| --- | --- | --- | --- |
| `mongoose-plugins` | `feat/mongoose-audit-production` | `b208257` | `main` `40f01cf` (post-1.0.44) |
| `mongoose` core | `feat/mongoose-audit-production` | `2c4192e` | `develop` `17a03b4` |
| analyser | `feat/mongoose-audit-production` | `b181aa38` | `main` `fda01845` (needs rebase onto `7ecb0c38`) |

Predictions were committed before any trial: `07a13bf7`,
[`evidence/mongoose-audit-production-impl/phase1-predictions.md`](evidence/mongoose-audit-production-impl/phase1-predictions.md).
Three were confirmed by measurement (P1.2, P1.3, P3.4); one unknown (U1.1) resolved; **none was wrong**,
but see *What I got wrong* — the suite caught a defect the predictions did not anticipate.

---

## 1 · mongoose-plugins#39 — the export escape · DONE

`b208257`. Ship-critical, and live in the released 1.0.44.

**Reproduced first, through the real exporter.** A record whose text carries a separator line split into
two documents; so did the **indented**, **tab** and **CR** variants (P1.2), and so did a payload in a
**node value** rather than `eventToString` (P1.3).

**The predicate is the reader's**, not an exact match: `isSeparatorLine` trims space, tab and CR, as both
framers do. **Escape, never refuse** — refusing changes the count, which is itself a payload-driven
change of verdict. The price is stated in the javadoc: an affected line gains a leading `\`, so the
logged text is not byte-identical to what the event produced.

**Acceptance (V1), against the published 1.19.0 jar** — sha256
`826daf64788674511c23d591f4e5aff3df53a5e44709c8e3990829bbfde41f65`, 3,945,846 bytes, downloaded from the
release — reading exports produced through the real writer:

| Export | Result |
| --- | --- |
| benign | `records=3`, `{complete, recordsRead: 3, declaredRecords: 3}` |
| hostile | `records=3`, `{complete, recordsRead: 3, declaredRecords: 3}` |

Identical. Before the fix the same payload read as 4 records in two runs with `missing_records` against a
forged `declaredRecords: 0`.

**Mutation witnesses**, green baseline of 3, source restored byte-identical:

| Mutation | Result |
| --- | --- |
| exact-match predicate instead of the trimmed one | fails `indentedTabAndCrVariantsAlsoSplit` |
| escape removed | all 3 fail |

`svc-admin-web` suite: **131 tests, 0 failures**.

**U1.2, settled by review, not by me:** the JSON-lines export, the read endpoint and the websocket tail
all go through Jackson, one line per record, so they are not injectable. My expectation was right; the
verification is theirs.

---

## 2 · MA-5 — capture fans out and restores · DONE

`4e3dba7`, `35f9a13` for MA-5.4, then `2c4192e` for the listing freeze. Core.

The class comment promised both behaviours and the code did neither. Capture now composes in front of
**the server's configured listener**, handed to it at `attach` through a new overload that defaults to
the existing two-arg form, so the NoOp impl and any external implementor keep working.

**Captured at attach, per processor**, and that is load-bearing: `MongooseServer.logRecordListener` is a
`private static` that every `bootServer` overwrites, so reading it at stop would restore another server's
listener. Fan-out is isolated both ways and failures are **counted and logged**, never swallowed.

**Acceptances met:** MA-5.1, 5.2, 5.3, 5.5, 5.6 in round 1; **MA-5.4 only after round 2** — see below.
**MA-5.7** (the every-backend contract) arrives with MA-2's text writer; there is only one backend today.

**Mutation witnesses**, green baseline of 5, source restored byte-identical:

| Mutation | Result |
| --- | --- |
| `previousListener = null` again | 4 fail |
| `stopRecording` installs a no-op again | 3 fail, at *"MA-5.2: stopping capture restores the configured listener, not a no-op"* |
| the delegate never called | 2 fail |

Core suite: **214 tests, 0 failures**, 9 skipped.

**MA-5.4's re-add path is named in the test**: `addEventProcessor` on a *running* server does not call
`init()`, so a re-add through it leaves the processor uninitialised; the configuration path does call it.
**MA-5.4 itself was NOT met in round 1** — see the round-2 table.

---

## 3 · Analyser MA-0, MA-6 reader half, MA-8 · SUBSTANCE DONE, THREE CLAUSES OPEN

`cb97f8ad`, `bce830c4`, `d13006a7` for round 2, then `a3c7e4e4` for round 3.

**MA-0** fires immediately before the early return that made every empty log silent. It is a **finding
beside an unchanged state**, never a seventh state. Keyed on `size() == 0` only. Damage findings are
already in the list at that point, so **MA-0.6's "damage first" needed no ordering code**.

**MA-6 reader half** names a document with no `eventLogRecord:` key — counted as a record, so a marker
over it declares a count including it. Reports the first row and how many, once, and fires independently
of the other checks.

**MA-8** annotates an uncovered node whose level a log changed per node. **Annotate, never excuse**: the
node stays in `uncovered` and in the ratio. Read **unfiltered**. Keyed on the event **type**, with the
rendering parsed tolerantly so a format change loses the annotation rather than breaking the load.
`groupId` in scope, annotated at group level where the log cannot map it to nodes.

**Acceptances met:** MA-0.1 (all six shapes, and openable after round 2), MA-0.3, MA-0.4, MA-0.6,
MA-6.1, MA-6.2 (reader side), MA-8.1–8.5 **including 8.4 after round 2**. 24 tests across
`EmptyLogAndRecordKeyDiagnosticsTest`, `PerNodeLevelChangesTest`, `CoveragePerNodeLevelTest` and
`EmptyFileOpensTest`.

**Mutation witnesses**, green baseline of 10, source restored byte-identical:

| Mutation | Result |
| --- | --- |
| MA-0 finding removed | 4 fail, 1 error, at *"MA-0.1: it must raise a warning"* |
| MA-0 widened past zero records | 2 fail, incl. *"a healthy log must raise nothing"* |
| MA-6 check removed | 3 fail |

Full analyser suite: **1904 tests, 0 failures**, 62 skipped (round 2 figure).

### Still open in phase 1, and why

- **D-MA0c — the report surface. NOT STARTED.** `MainFrame` already consumes `ProducerDiagnostics`, so
  the status bar, tooltip and `context` carry the new findings without further work. `ReportRenderer`
  carries **no** producer findings today, so routing them there is a new surface — as predicted (P3.4),
  the largest single diff in phase 1. **MA-0.2 is therefore partly met**: three of its four surfaces.
- **MA-0.5 — the Follow case. NOT DONE.** An empty file opened before its first record must show the
  finding and clear on **both** surfaces when a record arrives. It needs Follow driven through the real
  store, not a unit stub.
- **MA-0.7 / MA-6.3 — conformance fixtures. NOT DONE.** The acceptances are covered by unit tests with
  mutation witnesses, but the spec asks for fixtures in the conformance corpus as well.

---

## Round 2 — what review found, and what changed

Review ran everything and confirmed #39, MA-5 (except 5.4), and the analyser suites. Six fixes
followed; all are in `35f9a13` (core) and `d13006a7` (analyser).

| # | Finding | Fix | Witness |
| --- | --- | --- | --- |
| 1 | **MA-5.4 still discarded silently on a live server.** My test asserted `isRecording` and the listener restored on stop — the *neighbours* of the spec's clause — never "the new instance's records reach the capture file" | `attach` now installs the capture listener on the new `DataFlow` when the sink is already recording | removing it fails at *"MA-5.4: NOTHING was installed on the new DataFlow"*. The witness needed tightening first: `assertNotSame(configured, null)` passed vacuously and the test died on an NPE instead of a named assertion |
| 2 | **MA-6 was a substring search** — a headerless document merely *mentioning* the key read as a record, so a marker over it read `complete`. A payload changing the verdict: V1 | keyed on **framing** — first non-blank, non-comment line trims to the key | two tests: the payload case is named, a leading comment is not |
| 3 | **MA-8 had no behavioural test**; four mutations survived | six tests through `CoverageService.assess` | all four now fail at named assertions |
| 4 | **MA-8.4 not met** — only the LAST change was used, so a quietened-then-restored node was annotated nowhere, and a late change "explained" earlier silence | levels apply over `[change, next change)`, clipped to the scope's end | mutation to last-change-only fails |
| 5 | MA-8 absent from the report path | **deferred to D-MA0c**, as review agreed | — |
| 6 | **MA-0 capped by the app** — `canOpen` rejected zero bytes and content without the key, so 3 of 6 shapes were refused by every reader and the finding never showed | when there is **nothing to sniff**, accept by extension, narrowly | five tests, incl. an empty `.png` still not claimed and a real log still recognised by content |

Low items all taken: `firstWarning()` instead of `isWarning()`; event-name matched with `contains`,
consistently with `onlyControlEvents`; the annotation states `logTime` windows rather than record
numbers.

## What I got wrong in round 2

**`logTime` is nullable** — untimed records are legal (§1, fixture `c05`) — and my scope-end scan
dereferenced it. The suite caught it, not me: `DispatchHierarchyTest` threw an NPE. Both the scope scan
and the change list now skip or floor nulls.

That is the second defect this round that my own tests missed and the existing suite caught. The first
was MA-0 firing on a null index.

## Round 3 — four items and four Lows

| # | Finding | Fix | Witness |
| --- | --- | --- | --- |
| 1 | **BOM regression, mine from round 2.** `opensWithRecordKey` used `trim()`, which keeps U+FEFF, so a healthy BOM'd file was flagged `NO_RECORD_KEY` — the substring check it replaced had passed it | calls `StreamEndMarker.strip`, made package-private, rather than a second notion of whitespace. `YamlAuditReader` likewise, so a BOM-only file still opens | `trim()` restored → fails `aHealthyFileWithAByteOrderMarkIsNotFlagged`; BOM-only refused → fails `aBomOnlyAuditFileIsRecognised` |
| 2 | **MA-8.4 scope start.** Only `from > scopeEnd` was clipped, so a window that CLOSED before the scope began still explained silence in it | both ends clipped; `CoverageService` computes the scope start | clip removed → fails `aWindowThatClosedBeforeTheScopeDoesNotExplainSilenceInIt` with the exact "WARN between 1001 and 1007" symptom |
| 3 | **All six MA-8 tests ran unfiltered**, which is why two mutations survived | six more through a real `FilterState` | scope-end clip removed, and annotations dropped when filtered → each fails at its named assertion |
| 4 | **Listing froze while the file grew** — cause not established | **diagnosed, and it is pre-existing, not the re-registration.** `invalidate()` had NO callers anywhere despite the cache comment claiming the capture service reports mutations; and handles carry `recordCount`/`lastWriteAt`, which change per record, served from a frozen snapshot. Mutation hook added; live handles overlaid on read while the directory walk stays cached | two witnesses reproduce the reported symptoms: count frozen at 1 while 3 were written with `lastWriteAt` identical, and a late sink absent entirely |

**Lows, all four:** group windows keyed by group; untimed control records say "untimed" rather than
printing `Long.MIN_VALUE`; the event name matches on the simple name exactly, so
`FakeEventLogControlEventX` is rejected while a fully-qualified name is accepted — `contains()` did
neither; the dead null guard removed.

**Suites after round 3:** `svc-admin-web` **131/0**, core **218/0/9**, analyser **1913/0/62**.

**On item 4's diagnosis:** I found the cause by reading, then **proved it by mutation** — reverting each
half reproduces the symptom measured on the live server. I did **not** re-boot the bundle myself, so the
end-to-end 23→45 observation remains review's, not mine.

## Round 3 follow-ups — three items, none blocking

Review cleared #39, core MA-5 and the analyser as a partial, and confirmed the listing fix **live**
(23 → 24 on one event; 46 after a re-registration plus 3, matching the export). Three follow-ups
followed, in `5d4bf116`.

| # | Finding | Fix | Witness |
| --- | --- | --- | --- |
| 1 | **BOM in the framers.** Neither `isSeparator` skipped a leading U+FEFF, so a healthy file whose first line was a separator behind a BOM ran its head together and raised `NO_RECORD_KEY`; a BOM-only file framed as ONE record with `NO_NODE_LOGS` | both framers skip it — the char one on the character, the byte one on `EF BB BF` — and both `isBlank` checks with it. Tested through **`HeapLogStore` and `MappedLogStore`**, two implementations of one rule | each framer's skip removed → fails its store's test with `[NO_RECORD_KEY]` |
| 2 | **Untimed windows.** An untimed close printed `-9223372036854775808`; a TIMED window closed by an untimed change was dropped entirely, because `MIN_VALUE` compared as before the scope start | `MIN_VALUE` is "no time given", not an early instant: the wording says "until an untimed change", and an untimed close no longer closes a window early | treating it as a real instant → fails `aTimedWindowClosedByAnUntimedChangeIsKept` |
| 3 | **Multi-group fall-through untested** | a test for it | reinstating the `break` → fails `aLaterGroupsWindowIsFoundWhenTheFirstDoesNotApply`, exactly as predicted |

**Worth stating plainly:** that BOM has now cost something in four places, and the rule lives in five —
`StreamEndMarker.strip`, `ProducerDiagnostics`, `YamlAuditReader`, and both framers. Consolidating them
is not this change, but five copies of one idea is the shape that produced the regression in the first
place.

**Suite:** analyser **1920/0/62**.

## Round 4 — F1, and the record of what went wrong

Review cleared plugins and core to merge, and the analyser as a partial **after one fix**. That fix is
`1aa346ff`.

**F1 — a sixth BOM site, and it changed a verdict.** `RecordParser` used `String.strip()`, so the `#`
header comment behind a byte-order mark was never recognised and the record lost its thread, **level**
and logger. The level is where `auditLevelFinest` comes from, so it fell from DEBUG to INFO and coverage
then said debug calls might be missing. Not a first-line problem either: a concatenated file carries a
BOM in the middle and every record behind it was affected. `HeaderParser` had the same pattern, and the
framing tests could not have caught it because they use no header comments.

**The rule now lives once**, in `AuditText` — §1a whitespace plus leading byte-order marks — with
`StreamEndMarker`, `ProducerDiagnostics`, `YamlAuditReader`, `RecordFramer`, `RecordParser` and
`HeaderParser` all routed through it. `ByteRecordFramer` keeps its own, because it works on bytes, and
says so. That consolidates the five copies whose drift caused the round-2 regression.

**A seventh candidate examined and scoped, not ignored:** `NodeLogTokenizer` also uses `strip()`, but it
parses the `nodeLogs` block *inside* a record, so a BOM reaches it only if a file was cut mid-record —
corrupt input, not the legitimate concatenation case.

**Lows:** a double leading mark now loops at all three sites; the untimed-window wording no longer
overclaims.

### What went wrong, completed

Review was right that the record was incomplete. Adding the three it named, and one of my own:

1. **F1 itself** — a sixth copy of a rule I had already been burned by twice, found by review rather
   than by me, in the one place where it altered a conclusion rather than a warning.
2. **Stale round-2 wording on control-event matching**, still inconsistent in code.
3. **`PerNodeLevelChanges.all()`'s javadoc promises a report path that does not exist.**
4. **Mine, from this round:** I "fixed" the double-BOM loop twice without it applying. A shell heredoc
   converted the `\uFEFF` escape into a literal BOM, so the match silently hit nothing and the test kept
   failing. I found it by printing the framed text, not by re-reading the patch — after two rounds of
   asserting a fix that was never applied.
5. **Mine, found by the final review: I rewrote committed evidence.** See *The sixth thing that went
   wrong* below. It is the worst item on this list, because unlike the other four it destroyed
   something rather than only stating something untrue, and it sat unnoticed for four rounds.
6. **Mine, found by me while answering the final review: seventeen commits, no CHANGELOG line.**
   CLAUDE.md rule 2 says every user-visible change adds one **in the same commit**. This branch adds a
   finding, a second finding, a coverage annotation and a fix that **changes a verdict** — and touched
   `CHANGELOG.md` not once. Five review rounds did not catch it either, which is the more useful half
   of the observation: the rule has no gate, so it holds only as long as everyone remembers it. Filled
   in now, retrospectively, which is strictly worse than doing it per commit.

**Review also confirmed my question-2 suspicion was wrong:** the byte framer checks every line, lines
are assembled across buffer reads before checking, and a BOM at five positions around the 64 KB boundary
gave identical results on both stores.

**Suite:** analyser **1925/0/62** at that point; **1934/0/62** after the round-4 additions below.

### Round-4 additions, reviewed and integrated (`a605bb7c` + `b181aa38`)

The reviewer supplied a commit on top of my head. I reviewed it rather than taking it, and re-ran every
witness.

- **F3 completed.** My `AuditText.strip` removed **one** leading mark while both framers already looped,
  so `cat bom-only.yaml run.yaml` still hid the record key and a `#` header. **This corrects my own
  round-3 claim**: I reported fixing "the double-BOM loop", and that held in the framers but **not in
  `strip`**. The report said more than was true.
- **F2 — Follow polls only.** `HeapLogStore.appendFrom` used `Files.readString`, which threw
  `MalformedInputException` when a Follow poll landed inside a multi-byte character. It now decodes to
  the last complete character and leaves the rest pending; malformed bytes elsewhere still throw. I
  checked `completeUtf8` at every edge I could construct and it holds. **The fix does not cover
  opening a file**: `HeapLogStore.fromFile` (`HeapLogStore.java:117`) still calls `Files.readString`,
  so opening a log while it is being written can fail exactly the way Follow did. Final review found
  that; my round-4 wording implied the path was closed, and it is not. Left as it is on purpose —
  `completeUtf8` there would turn a loud failure into a quiet truncation on the main open path, and
  that is a decision to take with a test in front of it, not in the last commit before a merge.
- **F4** — one `isControlEvent` predicate shared by `ONLY_CONTROL_EVENTS` and MA-8, so a lookalike cannot
  count for one and not the other; and `PerNodeLevelChanges.all()` no longer claims a report path that
  does not exist.
- `AuditText` made public for one helper. Acceptable: only `isBlankIgnoringBoms` is public.

**Two corrections from re-running the witnesses myself:**

1. The YAML-reader witness fires in `EmptyFileOpensTest`, not the class the summary implied.
2. **The structural guard had a hole.** It matched `\uFEFF`, the literal character and the three byte
   constants, but **not the numeric form** — a site written `charAt(0) == 0xFEFF` passed it silently.
   Now widened to `0xFEFF` and `65279`, and it names file and line.

## Final review — one blocker, and the instrument changed

Verdict was *changes required*, four items, one blocking. All four are answered below. The blocker was
not a defect in the feature: it was **evidence I had quietly rewritten**, which is the failure this
whole branch is about — an instrument that says more than it established.

### A behaviour change worth stating — now measured

`RecordParser` now strips **ASCII whitespace only** (space, tab, CR, LF) where it used
`String.strip()`, which removes every Unicode space. That is closer to §1 and it is what the rest of the
parser already did.

Round 4 recorded this as *untested, because nothing in the corpus produces it*. **Final review's answer
was that untested is not the same as unmeasurable, and it was right.** Measured, and pinned by
`UnicodeIndentationTest`:

- a record indented with **U+3000** (ideographic space) or **U+2003** (em space) loses every indented
  field — `event`, `logTime` — and its `nodeLogs` block never opens, because `splitScalar`'s key then
  fails `isIdentifier` at character 0;
- the record is still **`OK`, not `PARSE_ERROR`**: `eventLogRecord:` sits at column 0 so `sawFields` is
  set. **The loss is quiet**, which is the part worth pinning;
- the only signal is `NO_NODE_LOGS`, **whose message names the wrong cause** — it says the graph was
  built without `addEventAudit()`. Recorded as a known misattribution rather than fixed: no producer,
  fixture or conformance case emits such a file (YAML permits only the space character for indentation),
  and inventing a finding for an unobserved shape is how a diagnostic surface rots.

The narrowing is **kept**. Widening `AuditText.strip` to accept Unicode spaces would put the parser and
the framers, which were always ASCII-only, back out of step — the drift `AuditText` exists to end. If a
real producer is ever found emitting one, the fix is one place and this test is what changes.

### The sixth thing that went wrong: I rewrote committed evidence

Final review's F1, and the only blocking finding of the round. My MA-8 commit `bce830c4` stripped two
trailing spaces from `docs/handoff/evidence/mongoose-audit-production-2026-09-23/spike-output.txt`, a
**captured producer output committed as evidence three commits earlier**. The lines were the audit
writer's own `nodeLogs: ` and `... allEventHandlereventLogRecord: `, each with the trailing space the
writer actually emits — precisely the format-faithful detail `TrailingWhitespaceTest` exists to protect.

It went unnoticed for four rounds because the gate that catches it did not yet cover that path: main has
since added the file to both `EVIDENCE` and `BYTE_SENSITIVE`, so the rebased tree fails without the
bytes. **Restored byte-identical from `ee2c5148`, the commit that captured it.**

What this says about the rest of the branch, checked rather than assumed: every file the branch
*modifies* rather than adds, compared before and after, changes no other line's trailing whitespace.
That check is by count and so would miss one line losing a space while another gained one — a shape I
have no reason to expect and did not separately exclude.

### The guard rewritten: values, not text

Round 4 widened the structural guard to `0xFEFF` and `65279` after I found it blind to the numeric form.
**Final review found six more spellings of the same number that still got past it**, each planted and
run: lowercase `0xfeff`, the digit separator `0xFE_FF`, lowercase byte constants, the signed bytes
`-17/-69/-65`, octal, and a constant expression. Matching text was the wrong instrument.

The guard now **lexes integer literals and compares values**. The character forms stay textual, because
`'\u` `FEFF'` is not an integer literal — and that check is now case- and repetition-insensitive,
because `﻿` and `\uuFEFF` are the same escape to `javac` and **both would have passed the round-4
guard too**; neither was planted by review, and neither is hypothetical. The byte triple is a finding
only when all three of `EF`, `BB`, `BF` appear in one file, by value: a BOM in bytes is that sequence
and a real site must test all three, whereas flagging a lone `191` by value would have made this gate
something people silence with exclusions.

**Witnessed, not reasoned.** Thirteen spellings planted into `ProducerDiagnostics` one at a time, guard
re-run against each, source restored between: **eleven named with file and line** — uppercase and
lowercase hex, `0xFE_FF`, decimal, octal, the escape in three forms, and the byte triple written
lowercase, signed and octal. **Two pass, both on purpose and both stated in the test:** a single byte
value alone, and the constant expression `0xFE00 + 0xFF`.

**The limit, stated where it belongs.** No lexical check can make *there is no seventh site* true — a
constant expression, a value read from another class, or any arithmetic decomposition passes it and
always will. The behavioural half is the defence; this guard only makes the cheap mistake loud.

### A tail that will never complete

Final review, Low, and carried rather than fixed. A lone byte left behind by a killed writer now sits
pending forever: `completeUtf8` waits for the rest of a character that is never coming. **The verdict
stays honest** — the state is `unknown`, which is what §1a says about anything after the last marker —
but nothing names the stuck tail, so a reader sees a file that is permanently about to finish. Naming it
needs a rule for how long is too long, which is a decision, not a fix.

## Two existing tests changed, both rewritten rather than deleted

- `ProducerDiagnosticsTest.anEmptyLogSaysNothing` asserted **exactly the behaviour MA-0 reverses**. It now
  asserts the finding fires, and that a null index still says nothing.
- `BinaryAuditReaderTest`'s cut-file echo expected only the damage message. That file parses **zero**
  records, so it is MA-0.6's case: both findings, damage first.

## What I got wrong

**I fired MA-0 on `idx == null` as well as `size() == 0`.** A null index means *no index supplied* —
the binary conformance suite passes null while having parsed a whole record — so that would have put an
"empty file" warning on a file with records in it. D-MA0b says `size() == 0` and only that.

**The existing suite caught it, not me**, and not the predictions: the f20 conformance case failed and
the diagnosis came from reading why. It is now fixed, with `aNullIndexIsNotAnEmptyLog` naming the
distinction.

Two smaller harness bugs of mine, fixed rather than worked around: a `documents()` helper that counted
split parts so a clean export read as two, and a hand-rolled counters proxy where
`NoOpCountersService.INSTANCE` exists.

## What I ran versus what I only read

**Ran, round 1:** the pre-fix reproduction of all four injection variants through the real exporter; the
#39 acceptance against the published 1.19.0 jar, downloaded and digest-checked; every test and mutation
in sections 1–3; all three full suites.

**Ran, round 2:** the MA-5.4 witness, including reading the Chronicle queue back so the assertion is
about the FILE rather than a counter; the four MA-8 mutations review said survived, each now failing at
a named assertion; the MA-6 framing cases; the five `canOpen` cases. Final suites —
`svc-admin-web` **131/0**, core **214/0/9 skipped**, analyser **1904/0/62 skipped**.

**Ran, final review round:** the U+3000 and U+2003 measurement, five tests, which confirmed prediction
P4.1 including the quiet-`OK` part; the guard witness — thirteen spellings planted into
`ProducerDiagnostics` one at a time, the guard re-run against each, the source restored between and the
worktree verified clean afterwards; the before/after trailing-whitespace comparison over every file the
branch modifies; the full suite on this base and again on the rebased tree.

**Read, not run:**

- `ReportRenderer`'s contents — enough to establish it carries no producer findings today, which is why
  D-MA0c is real work, but nothing there was exercised;
- the Follow path (MA-0.5);
- the app's own open path beyond the registry: `EmptyFileOpensTest` drives `ReaderRegistry.readerFor`,
  **not** `MainFrame.loadFile`. Swing is not unit-tested here by convention, so the end-to-end claim
  "the app now shows the empty-log warning for all six shapes" is **NOT** established — only that every
  shape is now recognised by a reader;
- `U1.2` — review settled it independently: the JSON-lines export, the read endpoint and the websocket
  tail all go through Jackson, one line per record, so they are not injectable. I did not verify that
  myself;
- `HeapLogStore.fromFile`'s mid-write behaviour — final review states it can fail the way Follow did,
  and reading the code agrees, but I did not construct that failure.

**Not done:** D-MA0c, MA-0.5, the MA-0.7/MA-6.3 fixtures, MA-5.7, and MA-8's report path. Round 3 added
no new gaps. AFMT-3 is
untouched, as instructed, and no `NONE` default was shipped onto that path.

## Public-repo discipline

Sweep clean in the analyser (`git ls-files | xargs grep -ril` over the four terms, excluding the two
files that state the rule). In both public repos the added lines were checked for local paths, keys and
personal data before each push. Only files I authored were committed.
