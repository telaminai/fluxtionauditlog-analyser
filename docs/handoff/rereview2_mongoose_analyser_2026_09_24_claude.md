# Second re-review — analyser audit-production fixes RR-1..RR-4 (reviewer: claude)

**Subject:** `feat/mongoose-audit-production-rebased` at **3d41c3a7** (base still `610d5777`). Reviewed
`719b8167` (P6 plus the probe output) and `3d41c3a7` (RR-1..RR-4), on top of `6998fcc8`.

**Independence:** I did **not** write the first re-review (`cd063e89`) or its probe. That was a separate
reviewer session. I re-ran its embedded `MARereviewProbe` (byte-identical to the review's appendix) and its
mutation runner, adapting anchors where the fix moved code. I added my own probes; I did not reuse any of
its outcomes as evidence.

Nothing was merged, rebased, released or deployed, and no key was used. Only this report is committed.

**Verdict: CHANGES REQUIRED. Narrow: two Medium and three Low, no High.** All four re-review findings
are fixed for the cases they named.
- The probe prints every expected line.
- The runtime agrees with every address decision.
- The nine claimed witnesses, and the first review's fifteen, go red at their named assertions.
- The suite is 1971/0/62.

What remains:
- RR-1's new failure state can be contradicted by a later read;
- RR-4's combined sentences still say more than the log establishes in one case;
- the `"null"` sentence leads with an assertion that its own disclosure undoes;
- two witnesses don't guard what their names claim.

Source paths are under `src/main/java/telamin/fluxtion/audit/analyser/analyser/`. Line numbers are at
`3d41c3a7`.

---

## Required corrections

### S1 · Medium · A failed live read is never cleared, so COMPLETE and "unknown until reopened" coexist (RAN)

**Site:** `parse/HeapLogStore.java`. `liveReadFailed` (`:48`) is set in the decode catch (`:190`) and read at
`:311` and `:322`. Nothing ever resets it.

**Scenario (RAN, my probe `RR1Edge`):**
1. Open a marked one-record file for Follow; it reads COMPLETE.
2. Append `C0`; the poll throws, and the store reads UNKNOWN with the fault. That is correct.
3. The writer then **replaces** the file with a longer, valid three-record marked log.
4. The next poll decodes fine. The file is longer, so it is not detected as a rotation and is read as an
   append.

```text
3 after C0:               state=UNKNOWN  rows=1  firstWarning=COMPLETENESS_GAP fault=true
  rotated-longer poll returned 2
3 after longer rotation:  state=COMPLETE rows=3  firstWarning=COMPLETENESS_GAP fault=true
```

The store now says COMPLETE and, in the same diagnostics, "whether this log is complete is unknown until
it is reopened".

**Required.** After a failed live read, any later successful decode means the bytes changed under the
store, so there is no append to trust. Return `-1` so the caller reloads, which is the only path that clears
the flag. Add this case as a regression, with a witness that removes the reload.

(A shorter replacement returns `-1` and reloads correctly: I ran a truncation as well.)

### S2 · Medium · RR-4's combined conditions: the second "if it did" drops the first condition (RAN)

**Site:** `topology/PerNodeLevelChanges.java`, `sentence` (`:292–328`).

**Scenario (RAN, the probe's "only later run selected" case):** the control has no grouping line, and the
selected record is wholly after a run boundary. The sentence reads:

> …whether this change applied is not established; **if it did**, nothing later in this processor's
> records changes it. Every record in view is in a LATER run… the log does not say whether the level
> survived into it: **if it did**, riskMonitor's lines below that level are not in this log; if it did not,
> this change explains nothing here.

The two "if it did"s have different antecedents: *applied*, then *survived*. The second conclusion needs
**both**, but after the full stop it reads as if survival alone is enough. It therefore states more than the
log establishes whenever applicability is NOT_ESTABLISHED.

"This processor's records" also presumes a processor that, by the sentence's own admission, is not
established.

**Required:**
- compose the conditions, e.g. "…if it applied here and survived the marker, riskMonitor's lines below WARN
  are not in this log; otherwise it explains nothing here";
- say "records sharing this grouping" rather than "this processor's";
- assert the NOT_ESTABLISHED × wholly-after sentence, with a witness.

### S3 · Low-Medium · The `"null"` sentence asserts, then discloses (RAN)

**Site:** `PerNodeLevelChanges.java:293–295`.

**Scenario (RAN):** the probe dispatched the literal string `"null"` to a real `EventLogManager`. The runtime
changed nothing (`stillINFO=true`). The annotation says:

> every node's audit level (**the change names no node**; the log renders that exactly as it would a node
> literally named "null") … if it did, … riskMonitor's lines below that level are not in this log.

P6 promised "say the log cannot tell". The sentence instead **asserts** the Java-null reading, puts the
ambiguity in a parenthesis, and draws the conclusion from the asserted reading. In this very probe, that
reading was false.

**Required:** lead with the ambiguity, and condition the conclusion on it:

> names no node — or a node literally called "null"; the log renders both identically. If it named no node
> and applied, …

### S4 · Low · RR-1's `pendingBytes = 0` has no witness (RAN, witness agent)

**Site:** `HeapLogStore.java` decode catch.

**Scenario:** deleting `pendingBytes = 0` leaves `bytesThatCanNeverBeginACharacterFailRatherThanWait` green.
Every case appends invalid bytes to a *whole* file, so the counter is already 0.

**Required:** a case that first leaves a valid partial character pending (e.g. `E2 82`), then appends an
impossible continuation, and asserts `trailingRecordsPending()==0` after the throw.

### S5 · Low · Two negative tests don't guard what they name (RAN, witness agent)

1. **`CoveragePerNodeLevelTest#aRecordThatIsNotAControlEventIsNotReadAsALevelChange` (MA-8.5).** It stays
   green even with `isControlEvent → return true`. Its fixture's `eventToString` lacks
   `logRecordProcessor`, so `parse()` refuses it first. Give it a complete, valid rendering, so the event
   type is the only thing rejecting it.
2. **`aLookalikeEventNameIsNotAControlEvent` depends on grouping.** With the `plainRecord` helpers declaring
   `groupingId: null` and the inline control fixture not, it **passes under the `contains()` mutant**,
   because RR-3 scoping hides the record. It is valid only while the two declare the same grouping. Add a
   comment, or a fixture assertion that the contexts match.

   The author's "three negative tests" claim: the witness run reproduced this dependency for one test and
   could not identify a third.

---

## Optional

- **O1 · Category (read).** The live-read failure is emitted as `COMPLETENESS_GAP`, whose javadoc is "the
  container's own claim about completeness did not check out". Undecodable bytes are what `SOURCE_DAMAGE`
  describes: "the READER could not read part of the source… listed FIRST". Consider routing it through
  `sourceDiagnostics()`. The state is UNKNOWN either way.
- **O2 · UI propagation (read).** `pollFollow` returns in its catch (`ui/MainFrame.java:4299–4301`) without
  refreshing `producerDiagnostics`. `context`'s `streamEnd` reads the store live (`:6108`, so UNKNOWN at
  once), but its `producer` list (`:6435`) and the tooltip refresh only on a tick that does not throw.
  - For a quiet file, that is the next tick: RAN, a second poll returns 0 and the state change triggers the
    refresh.
  - For a file that **keeps growing**, every tick throws, and the fault sentence never reaches `context` or
    the tooltip. Only the status line's "Follow read failed" shows.
  - Refresh in the catch too.
- **O3 · Report accuracy (RAN).** "Later polls re-decode the whole file and hit the same byte" is true only
  while the file grows. A quiet second poll returns 0 at the byte-length check (`byteLength` is updated
  before the decode) and never decodes. The outcome is the same (still UNKNOWN); the stated mechanism is
  not.
- **O4 · A limit that bites the chosen design (read).** RR-3's disclosed limit ("records that share a
  grouping are read as one processor's") is fine for the analyser partial. Chronicle exports are
  per-processor directories.
  - But OD-4's text writer is the server's **configured listener**, which `MongooseServer` sets on
    **every** processor. So the developer-default file will mix processors, all ungrouped by default:
    exactly the shape this limit cannot separate.
  - That is an MA-2 requirement to record in the spec: the text writer must carry processor identity,
    either one file per processor or a declared grouping.
  - Note that using `groupId` as identity changes which control events apply under the runtime rule, so
    this is a design question, not a one-liner.
- **O5 · Runner maintenance (RAN).** The first review's `F5-empty` mutant is now masked by RR-3: its injected
  out-of-range row gets `ABSENT` context and is filtered out. The protection still exists. A mutant that
  widens an empty selection to all rows goes red at `anEmptySelectionIsExplainedByNothing`. Update the
  runner's anchor.

---

## Your six questions, answered

1. **RR-1.**
   - The fix does what P6.1 said: `C0` after a marker reads UNKNOWN, pending 0, identities 0. That is RAN
     with the probe, and the four invalidations are individually witnessed except pending (S4).
   - "Nothing more is read" holds while the file grows (RAN: valid growth after `C0` throws again).
   - A truncation reloads (RAN).
   - Faults: S1, and O1 to O3.
2. **RR-2.**
   - The fixed-separator parse matches the runtime for all five of the earlier addresses and the group
     address (RAN against a real `EventLogManager`).
   - I found no real rendering it **misreads**. Every ambiguous shape (a value containing a separator, a
     listener `toString` containing `, sourceId=`, a multi-line listener rendering) is **refused**, which is
     the safe direction: no annotation, never a wrong one.
   - An empty source as a node named `""` matches the runtime (`stillINFO=true`, analyser null).
   - The runtime's levels are exactly `NONE ERROR WARN INFO DEBUG TRACE` (`javap`), so dropping FATAL and
     OFF is right.
3. **RR-3.**
   - The "before `event:`" fence holds for every runtime path I rendered (RAN): `triggerEvent`,
     `triggerObject`, and an anonymous class (bare `event: `), each with `groupingId:` first.
   - The **binary reader renders no `groupingId:`** because the wire format has none
     (`spi/binary/BinaryAuditReader.java:36–38`). Every binary-log annotation is therefore NOT_ESTABLISHED.
     That is honest, not vague: the information doesn't exist.
   - The same holds for the 20 text fixtures without the line.
   - The two-ungrouped-processors limit is acceptable for the partial, but see O4.
4. **RR-4.** It is definite only within the run, conditional after, and makes no definite claim wholly after
   (RAN; the forced-branch and planted-claim witnesses go red). The combined sentence with
   NOT_ESTABLISHED says more than the log does: S2.
5. **Changed tests.**
   - The `groupingId: null` fixtures are right, and removing the line from **all** fixtures keeps every test
     valid (ABSENT equals ABSENT).
   - The wrong-reason pass needs the helpers and a fixture to disagree: S5.2. MA-8.5's test is S5.1.
   - Both rewritten run-boundary tests and the new note assertion go red under their mutants (C2 and RR-4).
6. **Other changes.**
   - `isQuiet` and the six level names are correct (runtime `javap`).
   - The CoverageService note states the processor-identity limit, witnessed by `theAnnotationCarriesItsOwnCaveat`.
   - The report's `RollSetResolver` "harmless" correction is accurate as recorded.

Both P6 limits are stated in the product and the spec, and neither is claimed as solved. The one caveat is
S3's ordering of the `"null"` sentence.

## What I ran versus what I only read

**Ran (me):**
- the first re-review's `MARereviewProbe` (identical to the review's appendix) against the branch classes,
  the published **svc-admin-web 1.0.45** jar (SHA-256 `6839817621a5…a72b3fa39273d`, recomputed) and
  **fluxtion-runtime 1.0.16**. Every expected line was reproduced, and the UTF-8 oracle ran 315,793 cases
  with 0 mismatches;
- a new `RR1Edge` probe with four cases: quiet second poll, valid growth, longer rotation, truncation;
- runtime rendering order for `triggerEvent`, `triggerObject` and an anonymous event;
- `javap` of the runtime's `LogLevel`.

**Ran (a witness agent, in its own disposable worktree; RAN, not read):**
- 50 mutated runs, each a focused green baseline, then a mutant, then a byte-identical restore, then a
  green re-run. 44 went red at the named test with a surefire `<failure>` (never `<error>` or a compile
  failure);
- the six green runs are two expected controls and four findings (S4, S5.1, S5.2, O5);
- that covers the first review's 15 witnesses (three anchors adapted), all 9 claimed new witnesses plus
  sub-variants, the event-predicate and fixture checks, and the note witness;
- full suite **1971 tests, 0 failures, 0 errors, 62 skipped**, summed from 258 surefire XML files.

**Read:**
- `appendFrom`, the `completenessDiagnostics`/`IsNote` overrides and `MainFrame.pollFollow`/`context`
  (O1, O2);
- `PerNodeLevelChanges.parse`, `groupingOf`, `annotationFor`, `sentence`;
- `BinaryAuditReader`'s rendering;
- `MongooseServer`'s listener installation (O4).

**Not done:** no UI driven, no server booted, no rebase onto current `main`, no key used.
