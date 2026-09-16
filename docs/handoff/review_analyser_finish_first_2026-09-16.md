# Independent review — finish-first analyser work, 2026-09-16

## Verdict: NOT READY — M44.3 has three remaining adapter/lifecycle defects

Reviewed `f249113a..1af7bbe5`, with the candidate built at
`1af7bbe5d9550d47d21fd441f30fae3af5c00fcd` in an isolated detached worktree.
This is independent reviewer verification, not a restatement of the author's evidence.

**B1–B3 must be fixed before calling the asynchronous session slice complete.** The ordinary
slow-A/fast-B supersession path works, and the new processor records its decisions. But those decisions
do not yet govern every adapter side effect, and project switching leaves the pending state inconsistent.
The formula fixtures and skill rewording are accepted. The marker correction is accepted, with the
test-coverage qualification in F5.

F4 is a separately confirmed, **pre-existing** session-export defect: the handoff's requested read-back
check fails. It is not a regression introduced by this range, but the round-trip evidence claim cannot
be marked verified. No runtime/compiler release verdict is reopened by this review.

No application source, repository tests, generated processor, or skills were edited.
The review probe is supplied as a documentation attachment, not installed into the test suite.

## Reproduction method

I registered a deterministic, latch-controlled `AuditLogReader` through the existing reader registry,
then drove the real `MainFrame` adapter on the EDT. Socket operations went through
`ActionExecutor.render`, the same verb path used by the socket, without the HTTP transport.
Human operations used `openFile(..., OpenRequest.HUMAN)` or the actual Recent GraphML menu item.
A display watchdog counted and dismissed real `JDialog` instances. Each case used an isolated
temporary `user.home`; no user project, config, log or running analyser was changed.

Reflection obtains the frame's existing services and observes state; it does **not** replace the
session driver, set the audience flag, inject gate decisions, or call `onLoaded` directly.
The reader barrier makes the interleavings deterministic, rather than hoping a large file loads slowly.

Portable source: [review probe](review_analyser_finish_first_2026-09-16_probe.txt).
Its output records the observed behaviour; it is a diagnostic probe, not a proposed regression suite.
The initial same-project probe waited only for a discard and timed out. I corrected that assumption:
a surviving load is also a legitimate completion. The repeated same-project and bad-path controls
both preserve the load, as recorded below.

## Findings, worst first

### B1 — P1, CONFIRMED: arrival effects run before restoring that operation's audience

Files:
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:2721`
- same file `:2755`, `:3744`, `:3754`, `:3951`

Sequence:
1. Socket-open graph B.
2. Socket-open delayed log A, whose node set does not match B.
3. While A is pending, a person reopens B through **Recent GraphML**.
4. Let A complete.

Observed: **one modal dialog**, although the arrival belongs to a socket request. The graph closes
and the load finishes only after the watchdog dismisses the warning. Without that watchdog, the
warning requires a human response.

`OpenLogEffect` sets the audience at load start. The human entrance changes it while the load is
pending. `onLoaded` now calls `driver.submit(LogOpened)` at line 2721, which synchronously executes
the close/warning effects, before resetting `sessionInteractive` from the immutable request at line
2755. The comment promising that these effects use this request's audience is therefore false for
this interleaving.

Baseline source at `f249113a` set the request's audience before `repairLoadedGraph`; the new
result-before-adapter-state ordering moved the judgement ahead of that assignment. I verified that
ordering in baseline source, not by rebuilding the old release.

**Required remedy:** establish the completing operation's audience before dispatching any of its
effects, and keep it scoped to the operation rather than inherited from whichever entrance last ran.
Add this delayed-load/Recent-GraphML sequence and its inverse human-arrival positive control to the
display gate. Merely assigning the audience when starting the load is insufficient.

### B2 — P1, CONFIRMED: a successful project switch leaves an obsolete open permanently pending

Files:
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/OperationGate.java:52`
- same file `:75`, `:81`
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:2723`, `:4607`

Sequence:
1. Socket-open delayed log A in a fresh frame.
2. Before A finishes, socket-open a valid project P.
3. Let A complete.

Observed after the reader has finished:
```text
loading=true
inFlight=opening .../a.slow
actualLog=null
processorLog=null
status=Discarded .../a.slow — a later open superseded it while it was loading
AUDIT_STALE=1
```

`onOpenProjectRequested` changes `expectedOpId` but does not retire `inFlightWhat`.
The old load is correctly rejected, but only an accepted log result clears that string.
The adapter then declines to clear busy because the obsolete string remains non-null.
There is no remaining completion that can clear it. A subsequent new log open can recover, but waiting
cannot; `context` falsely reports an outstanding operation.

This is narrower than “any project request cancels a load.” I also exercised **same active project**
and **nonexistent project path** through the public verb path. Both short-circuit before changing the
gate, preserve A, and finish with `loading=false`, `inFlight=null`, no stale result. Do not break
those positive controls while fixing the successful transition.

**Required remedy:** define ownership and retirement of the pending log operation across a successful
project transition, and derive busy/context from that authoritative state. Superseding must retire the
old pending description, without allowing an old completion to clear a genuinely newer pending load.
Pin successful switch, same-project no-op, failed switch, and second-log-open cases independently.
Update the gate's old “no-op is harmless” / synchronous-driver javadoc as part of the correction.

### B3 — P2, CONFIRMED; blocking the claimed refusal contract: stale failures still notify the user

File: `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:2522`.

Sequence:
1. Human-open delayed A; arrange for its reader to fail.
2. Socket-open B and allow B to finish successfully.
3. Release A's failure.

Observed:
```text
dialogs=1
loading=false
inFlight=null
actualLog=.../b.yaml
processorLog=.../b.yaml
status=Failed to load .../a.slow: IOException: review delayed failure
AUDIT_STALE=1
```

The gate and processor reject A, but `onLoadFailed` never checks acceptance before writing the
failure status and showing the dialog. With a socket-origin A, the same experiment produces no dialog
but still overwrites B's successful status with A's obsolete failure.

**Required remedy:** apply the refusal to the failure adapter path too. Preserve the stale-result
audit evidence, but do not present a superseded operation's failure as the current operation's failure
or show its modal. Test delayed failure after a newer success and while a newer load is still pending;
also retain an accepted human-failure control that must warn.

### F4 — P2, CONFIRMED, pre-existing: the session snapshot merges all dispatches into one analyser record

Files:
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/session/SessionAuditSink.java:125`
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/parse/RecordFramer.java:46`
- `src/test/java/telamin/fluxtion/audit/analyser/analyser/session/SessionAuditRecordTest.java:95`

The handoff explicitly asks for “the audit record of one operation across dispatches (D-A5) read in
the analyser itself.” I exported the real supersession session with `driver.auditSink().export(...)`
and reopened that fixed snapshot through the same frame's ordinary log-open path.

There were **15 `eventLogRecord:` blocks, but the reopened store contained 1 record**.
Its parsed node contributions included request/Pending for opId 1, request/Pending/completion for
opId 2, and opId 1's stale result, all inside that one record. The opIds survive as text/KVs, but the
dispatch boundaries and individual record headers do not survive the round trip.

`export` concatenates raw runtime records with newlines, without the `---` record framing consumed
by the standard reader. I checked `f249113a`: that exporter is unchanged, so this is **not introduced
by M44.3**. The test labelled “export writes a snapshot the analyser can open” checks substrings and
snapshot immutability; it never opens the file through the reader.

**Remedy:** export correctly framed records, then test through the actual reader/store: exported
record count, per-dispatch event identity and opId, Pending, completion and stale refusal.
Check the header/level convention as well; do not invent an audit level that the sink did not retain.
Until that round trip passes, qualify the completion claim: in-memory recording is verified;
analyser-readable session evidence is not.

### F5 — non-blocking test qualification: the marker fix works, but distinguish the mutants

Files:
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/graph/ChartNotes.java:99`
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ChartPanel.java:666`
- `src/test/java/telamin/fluxtion/audit/analyser/analyser/graph/ChartNotesTest.java:91`

I compared note columns with the actual private `ChartPanel.xToPx` after painting a real panel,
including fractional bounds and an exact right-bound note. They agree. The right-edge pin is clipped
to its left half by the plot clip, rather than moved one pixel left; that is a presentation limitation,
not evidence that the corrected coordinates are wrong. I inspected the rendered image.

The new fractional-bounds test passes on the candidate and fails with the old mapping restored in a
scratch class: expected 453, observed 488. That mutant restores the old `width - 1` formula as well as
truncated bounds; 489 corresponds to truncation while retaining the new rounded-width formula.
The existing collision test stays green against my old-mapping mutant. I did **not** reproduce the
ledger's statement that its mutant also kills that test; its exact mutant patch was not supplied.

The new test calls `ChartNotes` directly. It does not pin the equally necessary change at the
`ChartPanel` call site: reintroducing only the caller's casts would not be exercised by that test.

**Follow-up:** add a panel/call-boundary regression and record the exact mutation used.
Keep the coordinate fix; do not narrow it back to integral bounds.

### F6 — non-blocking lifecycle policy follow-up: close does not supersede an outstanding open

File: `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:2613`.

Observed: load B, start delayed A, socket-close the log while A is pending, then release A.
A opens and is accepted (`AUDIT_STALE=0`); the close only closed B.
This review does not claim a newly introduced regression: close was already outside the operation
gate, and the async spec settles later-open supersession, not the meaning of close/reset during a load.

Decide that policy explicitly before the next lifecycle slice. If “close/reset” means cancel the
pending open too, model its invalidation and test it. Do not fix B2 by incidentally giving all closes
a new meaning.

## Disposition of the requested work

| Item | Result |
|---|---|
| M44.3 asynchronous open | NOT READY: B1–B3; pending and ordinary log supersession work |
| M44.3a arrival-only judgement | Correct direction; generated normal dispatch orders gate → open-log state → pairing → arrival; refresh no longer invokes arrival; existing frame/replay tests pass |
| D-A5 session evidence | OpIds/Pending/stale refusal verified in memory; export/read-back fails, F4 |
| N1 duplicate fixture metadata | Accepted; independently removing only the duplicate guard makes its test fail for the expected reason |
| Two clamp goldens | Accepted: clamp yields 2,3,4; subtracting min(4,2) yields -1,1,3; exercised in full suite |
| Skill wording/index | Accepted against review dispositions; all six current files equal bytes at pinned b71164ae and their declared SHA-256 values |
| Fractional-bound note mapping | Accepted, with F5 coverage/wording follow-up |

The Java source/resources copies of the generated processor are byte-identical. The live GraphML has
38 nodes. The new live-GraphML fingerprint comparison is appropriate; the old vocabulary fixtures
remain a frozen exporter-compatibility pair. I inspected the changed dispatch paths and opt-in fixture
refresh; I did not regenerate via the hosted service or independently establish the historical
provenance of every generated byte.

The two audience entrances called out by the author do not need a ceremonial flag assignment just
because they are socket verbs: `openLogs` supplies `OpenRequest.socket` to the shared asynchronous path,
and `discoverGraphs` is a query. B1 is at the later completion boundary.

Documentation still needs the usual surface inventory for `context.inFlight`: the new key occurs in
the spec and MainFrame, but not in the site docs or Project-panel implementation. The existing
Loading status is a human surface, but name it and its docs page explicitly; do not imply the Project
panel already renders the new key.

## Independent verification record

Environment: macOS, Corretto 21.0.9, isolated candidate worktree
`/private/tmp/analyser-finish-first-review-1af7bbe5`. Maven ran offline.

- `mvn -q -o clean verify`: exit 0 outside the sandbox. 1,419 tests, no failures/errors,
  five display-only skips under the headless configuration.
- `mvn -q -o test -Dtest=PairingDuringLoadFrameTest -Djava.awt.headless=false -DargLine="-Djava.awt.headless=false"`:
  **5 tests, 0 failures, 0 errors, 0 skips**. These overwrite that suite's headless XML; the combined
  report directory therefore totals 1,419 / 0 / 0 / 0, not a single all-display full run.
- The initial sandboxed verify had 29 socket-permission errors. Those are environmental, not product
  findings; the unrestricted rerun passed.
- `tools/verify-session-transitions.py --jar target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar`:
  **ALL PASS** on the built jar, including its failed-project control.
- Independent delayed-reader cases: ordinary supersession, mixed audience, project switch,
  stale socket failure, stale human failure, same-project no-op, bad project path, close during pending.
  Session snapshot was reopened through the analyser, not just searched as a string.
- Independent scratch negative controls: fractional-bound test killed the restored old mapping;
  duplicate-metadata test killed removal of its guard. No repository class/test was changed.
- Exact-tip [CI run 35150762453](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35150762453):
  build, loop-bench and ui-frame succeeded. I read the ui-frame log: **5 / 0 / 0 / 0**, and its
  zero-skips enforcement step succeeded. Docs CI also succeeded at this tip.
- Generated-source byte comparison, six skill hash/revision checks, review diff whitespace check,
  and repository public-data sweep were run.

Reproduce the frame cases after building the candidate, with a real display:
```sh
mkdir -p /private/tmp/analyser-finish-first-review-probe/classes
cp docs/handoff/review_analyser_finish_first_2026-09-16_probe.txt /private/tmp/analyser-finish-first-review-probe/FinishFirstProbe.java
javac -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar -d /private/tmp/analyser-finish-first-review-probe/classes /private/tmp/analyser-finish-first-review-probe/FinishFirstProbe.java
java -Djava.awt.headless=false -cp /private/tmp/analyser-finish-first-review-probe/classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar FinishFirstProbe audience project humanStaleFailure staleFailure supersede sameProject failedProject close
```

## WHAT I DID NOT CHECK

- No implementation fixes, release workflow, push, or runtime/compiler re-audit.
- Main advanced during review to docs-only `504a68c3` (the M64 spotlight spec and tracker).
  That separate proposal is outside this verdict; its files were left untouched.
- No fresh hosted regeneration; no independent audit of the hosted generator deployment.
- No S3 credentials/network loads, real rolled-set race, follow rotation, or native packaged-app run.
  The delayed reader exercises the shared local completion boundary, not every acquisition source.
- No new exhaustive state-space/long-running concurrency or performance benchmark. Superseded workers
  remaining alive is the documented design choice, not itself a new defect here.
- No transport-layer race in my custom probe; existing transport tests and the built-jar transition
  checker passed separately.
- No new end-to-end starter/Mongoose deployment to re-prove the skill facts; this pass checked the
  revisions against the earlier review dispositions and verified published-byte integrity.
- No live recapture of the owner's chart; I used synthetic data and inspected the rendered boundary case.
- No independent local strict MkDocs build in this pass; exact-tip docs CI succeeded.
- No proof of every close-identity interleaving or of the inverse mixed-audience positive control.
  Those are explicit regression requirements for the fix, not claims that this review already ran them.

Reviewer: Codex. Review-only handoff; author verification remains distinct.
