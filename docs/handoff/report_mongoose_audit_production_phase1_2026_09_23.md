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
| analyser | `feat/mongoose-audit-production` | `5d4bf116` | `main` `fda01845` |

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
  myself.

**Not done:** D-MA0c, MA-0.5, the MA-0.7/MA-6.3 fixtures, MA-5.7, and MA-8's report path. Round 3 added
no new gaps. AFMT-3 is
untouched, as instructed, and no `NONE` default was shipped onto that path.

## Public-repo discipline

Sweep clean in the analyser (`git ls-files | xargs grep -ril` over the four terms, excluding the two
files that state the rule). In both public repos the added lines were checked for local paths, keys and
personal data before each push. Only files I authored were committed.
