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
| analyser | `feat/mongoose-audit-production-rebased` | see below | `main` `610d5777` — **rebased, not force-pushed** |

The analyser branch was **rebased onto a new branch name** rather than rewritten in place: CLAUDE.md
rule 3 forbids force-pushing, and the reviewed history stays reachable at `feat/mongoose-audit-production`
`7138dc6f` as evidence. Seventeen of eighteen commits replayed untouched; the eighteenth conflicted in
`CHANGELOG.md`, where main had added an entry under `[Unreleased]` — resolved by keeping both, main's
line unaltered. `origin/main` had moved twice since review measured it (`7ecb0c38` → `610d5777`).

**Every analyser SHA quoted in this report is a PRE-rebase SHA**, reachable on
`feat/mongoose-audit-production`. The rebase rewrote all eighteen; the mapping is positional, same order,
same messages. Quoting the old ones keeps this report and the five reviews talking about the same things.

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
`groupId` in scope, annotated at group level where the log cannot map it to nodes. **Wrong, and superseded after the independent review** — `groupId` gates a change by the processor's grouping; see *Independent review* below.

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
   Widened at the time to `0xFEFF` and `65279`. **Superseded in the final round** — widening a text
   match was still the wrong instrument, and six more spellings got past it; see *The guard rewritten*.

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

The narrowing is **kept**, because no producer emits such a file. *(Qualified after the independent
review, O2: this paragraph also said widening would necessarily put the parser and the framers out of
step. Too strong — record parsing and marker recognition need not share one whitespace policy. What is
true is narrower: `AuditText.strip` also serves the marker recogniser, where Unicode spaces were measured
unsafe, so any future widening must be a separate strip for record parsing only. The misleading
`NO_NODE_LOGS` wording for this case remains an explicit follow-up.)*

### The sixth thing that went wrong: I rewrote committed evidence

Final review's F1, and the only blocking finding of the round. My MA-8 commit `bce830c4` stripped two
trailing spaces from `docs/handoff/evidence/mongoose-audit-production-2026-09-23/spike-output.txt`, a
**captured producer output committed as evidence three commits earlier**. The lines were the audit
writer's own `nodeLogs: ` and `... allEventHandlereventLogRecord: `, each with the trailing space the
writer actually emits — precisely the format-faithful detail `TrailingWhitespaceTest` exists to protect.

It went unnoticed for four rounds because the gate that catches it did not yet cover that path: main has
since added the file to both `EVIDENCE` and `BYTE_SENSITIVE`, so the rebased tree fails without the
bytes. **Restored byte-identical from `ee2c5148`, the commit that captured it.**

**Why the restore is not on the pre-rebase branch.** The exclusion that makes those bytes legal lives in
main's `TrailingWhitespaceTest`, which the old base has never seen — restoring there turns the branch
red, and did. So the restore is the first commit **after** the rebase, where both halves of the rule are
present. The commit before it says so, rather than leaving a reader to wonder.

**Witnessed:** green baseline on the rebased tree; strip the two spaces again and
`everyByteSensitiveFixtureStillCarriesItsTrailingBytes:126` fails naming the file — *evidence was
rewritten, most likely by a formatter or a gate missing its exclusion*; restore, byte-identical, green.
Which is the answer to review's fourth point from the other direction: **the gate that would have caught
me now exists and I have seen it fire.**

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
pending forever: `completeUtf8` waits for the rest of a character that is never coming. ~~The verdict
stays honest — the state is `unknown`~~ **That sentence was false, and the independent review measured it
(F2):** when the stuck byte followed a marker, Follow compared decoded lengths, saw no growth, and kept
**COMPLETE**. It is true now — held-back bytes withhold the verdict — and only since `87a442d4`. Nothing
yet names the stuck tail, so a reader sees a file permanently about to finish; naming it needs a rule for
how long is too long, which is a decision, not a fix.

## Independent review — three High, three Medium, and an error of mine it did not name

Review `2d7ac12f` on `review/mongoose-analyser-independent-2026-09-24`, against `0b7076fd`: a reviewer
with no stake in the earlier rounds, asked to go where they had not. **Changes required**, and right on
every count. All six required findings reproduced on my checkout **before any change**, with the
reviewer's own probe, against the published `svc-admin-web-1.0.45.jar` (SHA-256 matching the review) and
runtime 1.0.16. Predictions `P5` were committed first (`dbc51ae9`); the probe's output before and after
is kept beside them (`probe-0b7076fd.txt`, `probe-after-fixes.txt`).

| | Finding | Cause | Fix | Regression and witness |
|---|---|---|---|---|
| F1 High | a BOM-prefixed separator in a node value passes the released exporter and forges COMPLETE | **mine, round 3**: the framers skipped U+FEFF on every line; #39's escape does not | a BOM counts before `---` only at the file's first byte | `ExporterFramingAgreementTest`: real runtime record → real 1.0.45 exporter → heap, mapped, Follow; 9 spellings × 2 settings. Undo either framer's restriction → that store's named assertion fails |
| F2 High | one incomplete byte after a marker left Follow COMPLETE, identity kept | **mine, round 4**: growth measured in decoded characters | growth in bytes; held-back bytes withhold the verdict; identity clears; only a valid RFC 3629 prefix waits | `FollowPendingBytesTest`: 2/3/4-byte characters cut after every prefix byte from a complete file, then recovered; 8 never-valid sequences throw. 5 protections witnessed separately |
| F3 High | every healthy binary record raised NO_RECORD_KEY | mine: the positional rule stopped at the binary reader's leading `---` | plain leading separators are part of the record boundary | `RecordKeyBoundaryTest` through the real binary writer, reader and SPI store with the real index; headerless and later-mention records still named |
| F4 Medium | a global restore never closed a per-node interval | mine: only per-node changes were modelled | intervals by record order, closed by the next per-node **or global** change | `aGlobalRestoreClosesAPerNodeInterval`, plus denominator/ledger equality asserted separately |
| F5 Medium | the restore instant fell inside the window; an empty selection read as all time | mine: time clipping with `MIN`/`MAX` sentinels | position, half-open; no rows in view explains nothing | four tests; three mutations, each failing its own assertion |
| F6 Medium | MA-6 claimed "reads as complete … keys and newlines are gone" of every input | mine: one AFMT-3 case written as every case | quotes what it saw; conditional on a marker | three states × wording assertions; restoring the old text fails |
| O1 | `0_177377` passed the guard | lexer checked for an octal digit, not `_` | fixed | every claimed spelling asserted on the lexer directly |
| O3 | `not_sourceId=` annotated the node | substring search | whole fields of the pinned rendering | see below — my first fix made this **worse** |
| O4 | "one predicate" was not one | two scopes presented as one | documented as two; the sniff uses ASCII whitespace | — |

**The error the review did not name.** Reading `EventLogManager.calculationLogConfig` for F4, as rule 6
requires, showed that `groupId` is **not a group of nodes**: it gates the whole change against the
PROCESSOR's `groupingId`, which every runtime record writes, and a change with no `sourceId` then sets
every node. MA-8 treated `groupId` as node membership and annotated "this may or may not cover" each node.
**The spec said so too** (MA-8.5, "treated exactly as `sourceId`"), and it passed five spec reviews and
four implementation reviews that way. Inferred, not read. Corrected in the code, the spec (the old clause
marked superseded, not deleted), the tracker and this report, and
`theRuntimeOnTheClasspathRendersWhatThisClassReads` now pins the rendering against the runtime jar the
build actually links rather than a typed string.

**Where my own fix went wrong, caught before commit.** Making `field` whole-field for O3 turned
`not_sourceId=riskMonitor` into an ABSENT `sourceId` — absent means `null`, and `null` means every node,
so the probe then showed all five nodes annotated. A rendering the class did not recognise was read as a
global change. The runtime writes all four fields every time, so a record lacking one is now skipped.

**Two predictions were wrong.** P5.2 expected at least one round-3 test to break when the BOM widening was
reversed; **none did, because no test ever pinned a BOM before a mid-file `---`** — the widening was
untested in exactly the direction that mattered. P5.5 expected several `CoveragePerNodeLevelTest`
assertions to change; three did, and one of them (`oneGroupsChangeDoesNotCloseAnothersWindow`) asserted
the wrong group model and is rewritten to assert the runtime's rule in both directions.

**Not fixed, stated.** The pre-existing `UNSEPARATED` misreading of a payload (base does it too). A cold
**mapped** open of a file ending in a lone `C3` counts that byte as a record where heap Follow now waits;
the state is UNKNOWN either way, and cold-open UTF-8 is the limitation the review accepted, but the two
stores disagree on the count. `RollSetResolver`'s tail-chunk probe frames from a chunk start; it cannot
change a count, but its `logTime` does feed `FILE_OVERLAP` — *corrected after the re-review*, see below.

**Suite:** 1961/0/62, up from 1939 — 22 tests added.

## Re-review — one High and three Medium, each checked against the real runtime

Re-review `cd063e89` on `review/mongoose-independent-rereview-2026-09-24`, against `6998fcc8`, by the
reviewer of `2d7ac12f`. It confirmed F1–F6, O1, O3 and O4, and **all fourteen mutation witnesses plus a
fifteenth of its own**, and found four more. All four reproduced on my checkout with its probe before any
change. Predictions `P6` committed first (`719b8167`); probe output before and after is kept
(`rereview-probe-6998fcc8.txt`, `rereview-probe-after-fixes.txt`; the first review's probe, re-run on this round's code, is `probe-after-rereview-fixes.txt`).

| | Finding | Cause | Fix | Regression and witness |
|---|---|---|---|---|
| RR-1 High | a byte Follow refuses (`C0`) left COMPLETE and the old identity | **mine, F2**: identity and verdict were retired only AFTER the decode that throws | retire both before decoding; a failed live read forces UNKNOWN and states itself as a fault | the invalid-byte test asserts state, identity, pending, fault and re-poll AFTER the exception. Witness: invalidation moved back below the decode → `RR-1 ([192])` fails |
| RR-2 Medium | real addresses — `riskMonitor, DEMO`, `riskMonitor}DEMO`, `" riskMonitor "`, `""`, group `alpha, DEMO` — reached no node in the runtime and were read as riskMonitor, every node, or alpha | **mine, O3**: counting fields is not parsing values; values were cut at `,`/`}` and trimmed | parse by the rendering's fixed separators, values taken whole; exact comparison; `""` is a node named "" | `ControlAddressAndScopeTest`, every case checked against a real `EventLogManager`'s logger. Three witnesses: trim, truncate, empty-as-global |
| RR-3 Medium | an alpha change explained a beta record; beta's restore closed alpha's window; absent grouping read as ungrouped, also rolled | **mine, F4**: applicability checked against the change's processor, then applied to every record | each record's context is the grouping its own `groupingId:` declares (before `event:`, so no payload can declare one); changes explain and are closed only within their context; absent is qualified | mixed-processor, absent-vs-declared, payload-declared and rolled (heap and mapped) cases. Three witnesses |
| RR-4 Medium | a scope wholly after a run boundary was told the lines "are not in this log", then that survival was unknown | **mine, F4's boundary sentence**: the caveat was appended to a claim it undermines | definite only within the change's run; conditional after the boundary; no definite claim for a scope wholly after | wording asserted for the wholly-after and spanning scopes. Two witnesses — the second plants the definite claim back beside the caveat, and fails at the assertion forbidding it |

**Two limits, stated in advance in P6 and now stated in the product, not presented as fixes.** The
runtime renders Java `null` and the string `"null"` identically, so a change naming `"null"` is read as
"no node" and the sentence says the log cannot tell. *(Superseded by the second re-review, S3: the sentence
asserted the no-node reading first and disclosed the ambiguity in a parenthesis. It now leads with both
readings and conditions its conclusion on one.)* And records that share a grouping are read as one
processor's, which nothing in a record establishes; that is in the note travelling with every annotation.

**What my own fixtures were hiding.** The coverage tests' fixture records carried no `groupingId:` line,
where every runtime record carries one. Under RR-3 that made three negative tests — the lookalike and
fully-qualified event-name tests — pass or fail on grouping rather than on the thing they name. Found
because one of them failed; every fixture now declares `groupingId: null`, as the runtime does.
*(Wrong as written — corrected in the second re-review round, below: the fully-qualified test is a POSITIVE
test, and only ONE negative test, the lookalike, depended on grouping.)* U6.2 was a
non-issue in practice: no corpus fixture mixes records with and without the line.

**Also corrected:** `isQuiet` listed `FATAL` and `OFF`, which the runtime does not have; the level must now
be one of the runtime's six names. And the report said `RollSetResolver`'s tail-chunk framing was harmless
because it reads only `logTime` — too broad: that time feeds the `FILE_OVERLAP` ordering finding. It still
cannot change a count or a completeness verdict, which is all F1's closure needs; the chunk-origin
precondition is an open follow-up, not a reproduced defect.

**Suite:** 1971/0/62, up from 1961.

## Second re-review — two Medium, three Low, and a witness of mine that passed for the wrong reason

Second re-review `68660535` on `review/mongoose-second-rereview-2026-09-24`, against `3d41c3a7`, by a
reviewer who wrote neither earlier review. It confirmed all four RR fixes for the cases they named — the probe
prints every expected line, and the nine claimed witnesses and the first review's fifteen go red — and found
five things they still got wrong. Predictions `P7` were committed first (`9404a5fc`).

| | Finding | Cause | Fix | Regression and witness (strict runner: surefire `<failure>` at the named test, SHA-256 restore, green again) |
|---|---|---|---|---|
| S1 Medium | a failed live read was never cleared: a longer valid replacement read as an append, COMPLETE beside "unknown until reopened" | **mine, RR-1** | after a failure, a decode that SUCCEEDS means the file was replaced: return `-1` and reload | `aSuccessfulReadAfterAFailedOneReloadsRatherThanAppends` — the reviewer's exact reproduction. Witness: remove the return → red |
| S2 Medium | NOT_ESTABLISHED × a scope after a run boundary: two "if it did"s with different antecedents; "this processor's records" | **mine, RR-4** | every open premise — *named no node*, *applied here*, *survived the marker* — collected and carried in ONE condition per conclusion; "records sharing its grouping" on every branch | wholly-after and spanning sentences asserted. Three witnesses: survival-only (wholly after), survival-only (spanning), "this processor's" back |
| S3 Low-Med | the `"null"` sentence asserted the no-node reading, then disclosed | **mine, RR-2** — P6's limit, written as an assertion | leads with both readings; concludes "if it named no node" | `theLiteralNull…` asserts the order and the condition; the combined null × not-established case too. Witness: the old form → red |
| S4 Low | RR-1's reset of the pending count had no witness | mine: every invalid case appended to a whole file | a VALID partial character pending first (`E2 82`), then `C0` | `aRefusedByteAfterAPendingCharacterLeavesNothingPending`. Witness: delete the reset → red |
| S5 Low | MA-8.5's test was refused by `parse()` first; the lookalike test's validity depended on grouping | mine: fixtures that tested the wrong refusal | complete rendering; a **positive twin** in each (the same record with the real event name MUST annotate); the lookalike's contexts asserted equal | witnesses: `isControlEvent → true` and the `contains()` mutant, each red at its named test |
| O1 | the fault was a `COMPLETENESS_GAP`; undecodable bytes are `SOURCE_DAMAGE` | category | moved to `sourceDiagnostics()`, listed first | RR-1 test asserts `SOURCE_DAMAGE` first. Witness: empty it → red |
| O2 | a file growing past the bad byte throws every tick; the fault never reached `context` or the tooltip | `pollFollow` returned before refreshing | the catch refreshes too, through one shared method | **RAN through the jar**, both builds — see below |
| O3 | "later polls re-decode and hit the same byte" named one route of three | my account, in the re-review prompt | `liveReadFailed`'s javadoc states all three routes | — |
| O4 | OD-4's text writer sees every processor, so its file mixes ungrouped processors — the shape MA-8's limit cannot separate | a design gap | **recorded, not implemented**: spec MA-2.9 (appended, so no clause number moved) and the tracker | — |
| O5 | the first review's `F5-empty` mutant is masked by RR-3 | runner maintenance | no runner of that kind is committed here; the all-rows mutant was run instead | red at `anEmptySelectionIsExplainedByNothing` |

**O2, and a witness of mine that passed for the wrong reason.** The review flagged O2 from reading. My first
probe appended bytes every 1.2 s, and it showed the fault reaching `context` on the PRE-fix jar too — which
looked like the review was wrong. It was my probe: the follow timer polls every 1000 ms, so quiet ticks fell
between my writes, and a quiet tick refreshes, which is exactly the case the review said already worked. Rerun
with the file growing every 200 ms and `context` read while it grows: pre-fix jar **fault never reaches
context**; this branch **it does**; the branch with the new refresh removed from the catch **it does not
again**. So the fix is witnessed through the product, and the first probe is kept as a record of how a
wrong-reason pass looks.

**What I got wrong this round:**

1. **The "three negative tests" claim** (above, now marked). One negative test depended on grouping, not three,
   and one of the two I named was a positive test. Corrected in P7 before this report was written.
2. **P7.2 undercounted.** It predicted one test's wording would move; four did, because I also changed "below
   that level" to name the level, which the run-boundary pair pinned.
3. **My first witness runner reported three witnesses as missing.** JUnit names a `@TempDir` test
   `name(Path)`, and I matched the bare name. Fixed in the runner and rerun; the three then held. The runner's
   first output looked like three broken witnesses, and was not.
4. **The O2 probe** above.
5. **I placed MA-2.9 as clause 5 first**, which renumbered every later clause that other documents cite by
   number. Moved to the end before commit.

**Ran:** the reviewer's `MARereviewProbe` against the published `svc-admin-web-1.0.45.jar` (SHA-256
`6839817621a57fcb…`) and runtime 1.0.16 — every line as expected, the 315,793-case UTF-8 oracle at 0
mismatches, output kept as `rereview2-probe-after-fixes.txt`; ten witnesses through the strict runner, all
holding; the O2 probe on three jars (pre-fix, fixed, fixed-with-mutant); the full suite **1976/0/0/62**,
summed from 258 surefire XML files written by that run (1971 + 5 new tests); **all twelve frame-test classes
with a display, 63/0/0** — run because `MainFrame.pollFollow` changed, and because I had just told another
implementer the headless suite skips them.

**Read, not run:** that the tooltip shows the fault — the O2 probe reads `context`'s producer list, which is
built from the same `producerDiagnostics` the tooltip is set from, but no screenshot was taken. O4's claim
about `MongooseServer` installing the listener on every processor is the reviewer's reading, adopted in the
spec as a requirement to design, not verified here. *(Now verified from source, in the third re-review round:
at mongoose `2c4192e`, `MongooseServer.java:116` declares one `private static LogRecordListener`, and
`addEventProcessor` installs it with `setAuditLogProcessor` at `:758` for every processor it adds. READ, by me
and independently by the third reviewer.)*

**Suite:** 1976/0/62.

## Third re-review — two Low, six optional, all taken

Third re-review `8514f91b` on `review/mongoose-third-rereview-2026-09-24`, against `fc9b1f9c`, by the author
of the second. It found S1–S5 and O1–O5 fixed under their own witnesses: 86 strict runs, 24 sentence
combinations plus four extras, and no negative test refusing a record before its named rule. It asked for two
small corrections. Predictions `P8` were committed first (`adc96d52`).

| | Finding | Cause | Fix | Regression and witness (strict: `<failure>` at the named test, SHA-256 restore, green again) |
|---|---|---|---|---|
| R1 Low | the clause for the change that CLOSES a window said "sets it to INFO for every node" for a `sourceId=null` rendering | **mine, S3**: I fixed the opening clause and missed this one | `closing()` discloses both readings, and says only the no-node one would end the window there. The window still ends there, the conservative direction | `aClosingChangeRenderedNullIsDisclosedNotAsserted`, the reviewer's four-record log. Witness: the old string → red |
| R2 Low | S2's "never this processor's" had no witness on the DECLARED branch, which every runtime log takes | mine: witnesses covered only the undeclared branch | `positiveControls…` asserts no "this processor" and the grouping phrase, for a global and a per-node change | witness: the declared scope back to "this processor's records" → red |
| O-A | a node literally NAMED `"null"` was told "otherwise this change explains nothing here" | the premise list ignored that both readings set that node | no *named no node* premise for that node; "either way it sets this node"; the closing clause too | `aNodeNamedNullIsSetUnderBothReadings`. Witness → red |
| O-B | a failed tick rebuilt identical findings every second: 73–201 ms of EDT work on a 1M-record log | O2's refresh keyed on nothing | a failed tick refreshes only if the current findings do not already carry the damage | **RUN through the jar**, rebuild counter: **2** rebuilds over about eight failed ticks with the check, **10** without it. The fault still reaches `context` on a growing file, and after a reload, a new failure reaches it too |
| O-C | "within that run" before any run was named | wording | "within the run it was made in" | witness: the old phrase → red at the spanning test |
| O-D | in the `"null"` sentence, the grouping parenthesis read as a gloss on "the log renders both identically" | placement | its own sentence, on every branch | new assertion on the sentence form. Witness: the parenthesis → red |
| O-E | the CHANGELOG promised a reload for any readable replacement | too broad | narrowed to a longer one; a same-length one is not detected and the fault stays; the javadoc names that route | — |
| O-F | the spec quoted "records sharing this grouping", which the code never says | paraphrase | quotes both of the code's phrases exactly | — |
| O4 | MA-2.9's premise was the reviewer's reading | not verified by me | verified from mongoose source, and recorded above | READ |

**What I got wrong this round:**

1. **My first O-B key would have hidden a real failure.** I remembered the damage list each rebuild used, and
   skipped a failed tick whose damage matched. But the findings are also rebuilt on load, so a reloaded store
   failing at the same row would have matched the stale copy and been skipped. Caught on reading it back, before
   any test. The key is now what the CURRENT findings carry, and the jar probe checks a failure after a reload.
2. **R1 is the S3 pattern, in a clause S3 did not reach.** I rewrote the opening and did not look at the
   sentence's other clause built from the same rendering. So did the second reviewer, who says so.
3. **My headless count first read 1,979 from the XML, against 1,978 on the console.** The difference is a stale
   report, `TEST-…parse.DoubleBomDiagTest.xml`, from a scratch diagnostic class of mine that no longer exists in
   source, left in `target/` rounds ago. The true count is **1,978**, over the 258 reports of classes that exist.
   The stale file is left in place and named here, rather than deleted.

**One frame run failed, and I am not calling it a pass.** The first frame suite this round had **1 failure**:
`NamedGraphAndMenuSpotlightFrameTest.lightingASecondMenu_keepsWhatTheEchoSaid_byReplaceAndByAdd`, whose AI-menu
spotlight was not lit after the File menu closed. That class passed **alone, twice**, and the **second full frame
run was 63 / 0 / 0**, with one skip (the focus-dependent test). This round changes nothing in menus or spotlights,
only `pollFollow`'s refresh condition. So it is intermittent, one full run in two. Whether it is new is not
established here.

**Ran:**
- predictions first;
- five strict witnesses (R1, R2, O-A, O-C, O-D), plus the O-B rebuild counter through the jar;
- the O-B jar probes: growing file, reload, then a new failure;
- a by-eye read of the R1, O-A and O-D sentences;
- the reviewer's `MARereviewProbe` against the published jars, whose non-annotation lines are identical to round 2's, kept as `rereview3-probe-after-fixes.txt`;
- headless **1,978 / 0 / 0 / 62**;
- frame **63 / 0 / 0** on the second full run, after the one intermittent failure above;
- `SpecLinksResolveTest`.

**Read, not run:** the mongoose source line for O4; that a hover shows the tooltip.

**Suite:** 1,978/0/62 — 1,976 plus two new tests (R1 and O-A). R2 and O-D are assertions added to existing tests.

## Fourth re-review — three Low, three optional, all taken

Fourth re-review `1c706216` on `review/mongoose-fourth-rereview-2026-09-24`, against `74d5a009`, by the author of
the second and third. It found R1, R2 and O-A–O-F fixed, every claimed witness red, and all 84 earlier runs holding.
*(Round 3 said 86; the reviewer corrects that to 84, their own miscount, and this report takes the 84.)* It asked for
three small corrections. Predictions `P9` were committed first (`82858d92`), and they include R-B's premise derived
from the runtime's rule rather than taken from the review.

| | Finding | Cause | Fix | Regression and witness (full protocol: reports deleted, a `<failure>` at the named test, SHA-256 restore, clean `git status -- src`, green again) |
|---|---|---|---|---|
| R-A Low | the `closing()` branch for a node named "null" had no witness | **mine, O-A**: I witnessed the opening, not the closing | — | `aNodeNamedNullIsSetUnderBothReadings` now closes the window with a `sourceId=null` INFO: "either way it ends here", never "only the first would". Witness: the branch disabled → red |
| R-B Low | "It holds until record 3 sets it to INFO" where the closing change's applying is not established | **mine, R1**: R1's class, in the other branch | with an absent grouping and differing `groupId`s, the only case where `c` applying does not imply `next` applied (derived in P9), all three `closing()` branches say the change was addressed to that grouping and "whether that applied here is not established either". The window still closes there, the conservative direction | `aClosingChangeWhoseApplyingIsOpenSaysSo`, the reviewer's four-record log, plus a same-grouping positive control that stays definite. Witness: `closeOpen = false` → red |
| R-C Low | "checked on every branch" was untrue: plants in `closing()`, the undeclared YES note, the addressed-grouping clause and the post-marker text all stayed green, and the S2 guard caught only the possessive | mine: the claim outran the tests | `noBranchOfTheSentencePresumesAProcessor`: the whole matrix — source × 4 groupings × 3 boundaries × 3 closings, plus a node named "null" — **108 of 108 annotated, none presuming a processor**, and failing if the matrix stops annotating. The S2 guard is widened to "this processor"; the comment now names the test. **Corrected in round 5 (R5-1): this was not the whole matrix.** It had no declared-null grouping with a `groupId`, so it never reached the YES note that row names — which is the *declared-null* case, not the undeclared one — and its closing change always reused the opening's `groupId`, so it never reached the three open closings. "for its processor" also passed the guard | two witnesses: planted in a `closing()` literal, and in the post-marker text → red |
| O-1 | "within the run it was made in" followed the closing clause, so "it" could be the closing change | the round-3 wording, the reviewer's suggestion | "before the marker" | witness: the old phrase → red |
| O-2 | the O-B comment described a different rule from the code | wording | comment only; `MainFrame` has no code change | — |
| O-3 | probe outputs dropped the runtime's `updating event log config:` lines without saying so | unstated filter | a one-line header naming the filter, prepended to **all five** outputs that used it (the review named one), content verified byte-identical beneath each; this round's `rereview4-probe-after-fixes.txt` carries it from the start | — |

**The frame flake, recorded as the reviewer measured it (REPORT).** `NamedGraphAndMenuSpotlightFrameTest` failed
**once in 10** display runs at `74d5a009` and **0 in 10** at `fc9b1f9c`. The failing run was the one where
`PersonAtTheScreenFrameTest` skipped its focus-dependent test, so the window had lost focus. The test never reaches
`pollFollow`. CI's `ui-frame` job ran 154 times on `main` and PRs without this test failing, but it has **never run
on this branch**: it triggers only on `main` and on pull requests. If the owner opens a PR, the xvfb job settles
it. This round changes `MainFrame` in a comment only, so no frame run was required, and none was made.

**What I got wrong this round:**
1. **R-A and R-B are R1's pattern again.** I disclosed the closing change's `"null"` reading and did not ask whether
   the closing change had applied at all, or whether my new branch had a witness.
2. **R-C: I wrote "checked on every branch" in round 3 and checked two.** ~~The matrix test now makes the comment
   true by construction rather than by assertion.~~ It did not; see the fifth re-review, R5-1.
3. **O-3 was wider than reported.** The review named one filtered output; five had the same unstated filter,
   including three from rounds before this reviewer's.

**Ran:**
- P9 first;
- five witnesses under the full protocol;
- the matrix's exact count (108 of 108), read from a temporary threshold and restored byte-identical;
- `MARereviewProbe` against the published jars, non-annotation lines identical to round 3;
- headless **1,980 / 0 / 0 / 62** over 258 reports mapped to source classes, matching the console. The one
  orphan, `DoubleBomDiagTest`, is excluded and named; its XML has now been removed from `target/`.

**Read, not run:** that the O-B jar counter from round 3 still describes the code, which is unchanged but for a
comment.

**Suite:** 1,980/0/62 — 1,978 plus R-B's and R-C's tests. R-A extends an existing test.

## Fifth re-review — two Low, three optional, all taken

Fifth re-review `79a51d27` on `review/mongoose-fifth-rereview-2026-09-24`, against `98148175`, by the author of
rounds 2–4. It found R-A, R-B, O-1, O-2 and O-3 fixed and witnessed, and R-C only partly fixed. It re-derived R-B's
premise and probed 81 logs against it with no mismatch. Predictions `P10` were committed first (`ae9fba62`); fixes
are `8200c4ab` and `7fd8d6a8`.

| | Finding | Cause | Fix | Regression and witness (strict protocol: reports deleted, a `<failure>` at the named test, SHA-256 restore, clean `git status -- src`, green again) |
|---|---|---|---|---|
| R5-1 Low | R-C's "every branch" matrix missed four branches — the declared-null YES note and the three open closings — and its guard passed "for its processor" | **mine, R-C**: the matrix was built from my list of branches, not from the code, and inherited the round-4 mislabel of the declared-null note | the matrix gains `G("null", "alpha")` and a closing-`groupId` dimension {same, `beta`, none}: **315 logs, 315 annotated**, both asserted exactly. It now also asserts that it **reaches** each of 24 branch wordings, so a branch the inputs stop reaching fails rather than going unchecked. The guard is `processor(?! grouping)`. The comment says what the matrix covers and why round 4's did not | six witnesses, all red at `noBranchOfTheSentencePresumesAProcessor`: a plant at the declared-null YES note; "for its processor" in each open-closing literal (per-node, the shared disclosure, the null-node clause) and in the per-node definite closing; and the YES note's wording changed, so the matrix no longer reaches it |
| R5-2 Low | the opening said "this log sets riskMonitor's audit level to WARN" while the next sentence said its applying is not established | **mine, since RR-3**: the opening was written before NOT_ESTABLISHED existed and no round re-read it | while applying is open the opening says "this log records a change setting …". **Re-reading every clause for the same flaw found one more:** for a node named "null", "— either way it sets this node" was appended whatever `applies()` said; it now says "addresses" when applying is open | the matrix asserts no absent-grouping note starts "this log sets" or says "either way it sets". Two witnesses, one per opening, red there |
| O5-1 | "It holds until" said the level ends at a change whose applying is open | wording | "It holds at least until" in the open case, and only there | the matrix asserts "at least until" appears exactly where "not established either" does — **added after the first fix commit**, when setting up the witnesses showed the wording had no assertion. Witness: "It holds until" restored → red |
| O5-2 | the generic open closing said "if it applied" twice | wording | "…and only the first would end it there, and only if it applied here: it was addressed to …, and whether it applied is not established either", shared with the null-node clause | the matrix's reach assertion pins it. Witness: the round-4 wording restored → red |
| O5-3 | "before the marker" came one sentence before the marker was introduced | wording | "before the stream-end marker preceding record N"; the next sentence says "That marker". ("preceding", because "before the stream-end marker before record 3" read badly) | the two tests that pin the phrase, rewritten. Witness: "the marker" restored → red at `aScopeSpanningARunBoundaryIsDefiniteOnlyBeforeIt` |
| found, mine | after a closing clause, the condition "if it applied here" could read as the CLOSING change — O-1's ambiguity, in the condition rather than the conclusion | found reading the new sentences, not in the review | when a closing clause intervenes the condition names "the change at record N" | the matrix asserts no bare "if it applied/survived/named" in a note with a closing clause. Witness: the bare "it" restored → red |

**R-A and R-B re-run** under the same protocol, because their code sits beside this change: both red at their named
tests. **14 witnesses in all, every one holding** (`witness10.py`). I did not re-run the 84 earlier witnesses.

**What I got wrong this round:**
1. **R5-1 is R-C again.** I claimed "every branch" from a list I wrote, and the reviewer found the gaps by reading
   the code. The matrix now checks that it reaches each branch's wording, so the claim has a test of its own.
2. **R5-2, and the "null"-node instance, are R1's class a fourth time.** I checked the closing and not the
   opening. This round I re-read every clause of `sentence()` and `closing()` with one question: does it state as
   established something the log does not establish? That found the "null"-node opening; the condition's unnamed
   "it" came from reading the output, not the code.
3. **O5-1's first fix had no assertion.** I found that while writing its witness, not before committing.
4. **P10.3 is badly worded** ("change no assertion except none"). The prediction held: no existing assertion
   broke. P10.1 and P10.4 held exactly; P10.5 held (1,980 run).

**Ran:**
- P10 first;
- 14 strict witnesses;
- the focused MA-8 classes (45 tests) after each change;
- `MARereviewProbe` against the build, run twice with identical output
  (`rereview5-probe-after-fixes.txt`): the only change from round 4 is the R5-2 opening, in the two
  absent-grouping cases;
- the new sentences printed and read: the declared-null YES, all three open closings, both "null"-node openings,
  and the spanning variants with and without a closing clause;
- the full headless suite, summed over mapped reports (see "Suite" below).

**Read, not run:** that `MainFrame` is unchanged (it is not in the diff). No frame run was needed or made.

**Suite:** **1,980 / 0 / 0 / 62** over 258 reports, every one mapped to a class in `src/test/java`, no orphans;
matching the console. Run at `8200c4ab` and again with this report in the tree. Unchanged in count: R5-1, R5-2 and
O5-1 extend the existing matrix test.

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
branch modifies; the evidence-gate witness, mutated and restored; the rebase itself.

**Suites, final review round.** Pre-rebase base: **1939/0/62**. `origin/main` `610d5777` alone:
**1876/0/62**. Rebased tree with the restore: **1939/0/62**, green. The branch therefore adds **63**
tests and changes nothing that main's own suite asserts — which is the measured form of review's
question 4, *zero SEMANTIC overlap*, and it is weaker than it sounds: it says main's assertions still
hold, not that no behaviour main relies on changed unasserted.

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
untouched, as instructed, and no `NONE` default was shipped onto that path. Final review adds two
**carried, not closed**: `HeapLogStore.fromFile`'s mid-write open, and a tail that can never complete
(both Low, both above).

## Public-repo discipline

Sweep clean in the analyser (`git ls-files | xargs grep -ril` over the four terms, excluding the two
files that state the rule), re-run on the rebased branch. `git config user.email` is the personal
address. In both public repos the added lines were checked for local paths, keys and
personal data before each push. Only files I authored were committed.

## Where phase 1 stands

- `mongoose-plugins` — **merged and released as 1.0.45**, carrying #39.
- `mongoose` core — **merged to `develop`** at `2c4192e`. Merging is not delivering: the bundle's
  mongoose pin is still 1.0.29, so nothing reaches a developer until core is released and that pin moves.
- analyser — **NOT ready until the fifth re-review's fixes are reviewed.** Six review rounds' findings are
  fixed on `feat/mongoose-audit-production-rebased`, each with a regression and a mutation witness. **CI's frame
  job has never run on this branch**; a pull request is what would run it. Still based on `610d5777`; `origin/main` has moved, and
  the rebase comes after review, not under it. Not merged: the owner's call.

Three release-note items stand, unchanged by this round: the producer findings are not in the report
surface yet (D-MA0c); the `attach` default overload quietly drops fan-out for any other capture-service
implementation; and **OD-5 is still the one open owner decision** — whether Chronicle gets a marker —
which is why MA-2's Chronicle half was not implemented.
