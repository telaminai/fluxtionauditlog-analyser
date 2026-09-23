# Phase 1 — report for review, 2026-09-23

Implementing [`spec-mongoose-audit-production.md`](../specs/spec-mongoose-audit-production.md) at
`fda01845`. **Phase 1 is NOT complete**: the ship-critical item and MA-5 are done, the analyser items
are done in substance but three acceptance clauses are outstanding and named below. Nothing is merged,
released or deployed. No Fluxtion API key was used.

## Branches

| Repo | Branch | Head | Base |
| --- | --- | --- | --- |
| `mongoose-plugins` | `feat/mongoose-audit-production` | `b208257` | `main` `40f01cf` (post-1.0.44) |
| `mongoose` core | `feat/mongoose-audit-production` | `4e3dba7` | `develop` `17a03b4` |
| analyser | `feat/mongoose-audit-production` | `bce830c4` | `main` `fda01845` |

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

**U1.2 is still unchecked:** whether the JSON-lines export branch needs the same treatment. My
expectation is no — a newline inside a JSON string is escaped by the mapper — but I did not verify it.

---

## 2 · MA-5 — capture fans out and restores · DONE

`4e3dba7`, core.

The class comment promised both behaviours and the code did neither. Capture now composes in front of
**the server's configured listener**, handed to it at `attach` through a new overload that defaults to
the existing two-arg form, so the NoOp impl and any external implementor keep working.

**Captured at attach, per processor**, and that is load-bearing: `MongooseServer.logRecordListener` is a
`private static` that every `bootServer` overwrites, so reading it at stop would restore another server's
listener. Fan-out is isolated both ways and failures are **counted and logged**, never swallowed.

**Acceptances met:** MA-5.1, 5.2, 5.3, 5.4, 5.5, 5.6 — five tests in `AuditCaptureFanOutTest`.
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

---

## 3 · Analyser MA-0, MA-6 reader half, MA-8 · SUBSTANCE DONE, THREE CLAUSES OPEN

`cb97f8ad` and `bce830c4`.

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

**Acceptances met:** MA-0.1, MA-0.3, MA-0.4, MA-0.6, MA-6.1, MA-6.2 (reader side), MA-8.1–8.5.
15 tests across `EmptyLogAndRecordKeyDiagnosticsTest` and `PerNodeLevelChangesTest`.

**Mutation witnesses**, green baseline of 10, source restored byte-identical:

| Mutation | Result |
| --- | --- |
| MA-0 finding removed | 4 fail, 1 error, at *"MA-0.1: it must raise a warning"* |
| MA-0 widened past zero records | 2 fail, incl. *"a healthy log must raise nothing"* |
| MA-6 check removed | 3 fail |

Full analyser suite: **1891 tests, 0 failures**, 62 skipped.

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

**Ran:** every test and mutation above; the #39 acceptance against the downloaded published 1.19.0 jar;
the pre-fix reproduction of all four injection variants; all three full suites.

**Read, not run:** the JSON-lines export branch (U1.2); `ReportRenderer`'s current contents; the Follow
path.

**Not done:** the three clauses listed above. AFMT-3 is untouched, as instructed, and no `NONE` default
was shipped onto that path.

## Public-repo discipline

Sweep clean in the analyser (`git ls-files | xargs grep -ril` over the four terms, excluding the two
files that state the rule). In both public repos the added lines were checked for local paths, keys and
personal data before each push. Only files I authored were committed.
