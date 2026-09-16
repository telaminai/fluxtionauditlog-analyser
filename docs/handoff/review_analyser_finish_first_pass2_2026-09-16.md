# Independent review — finish-first response, pass 2

## Verdict: NOT READY to close M44.3 — B2 is only partially fixed

Reviewed `5be00dc4..b8a93533` at `b8a93533289fbf72b9c24c267356d657dede36bf`, in an isolated worktree.
This is independent verification of the [response](handoff_analyser_finish_first_response_2026-09-16.md),
not adoption of its test claims. Reviewer: Codex, 2026-09-16.

**B1 and B3 close. The export defect F4 closes, and F5's new call-boundary test is effective.** B2's gate
retirement works, and the original permanent-pending symptom is gone *after the discarded worker returns*.
But the adapter continues to advertise loading until that return. The remaining blocker is narrower than
round 1: project retirement must immediately retire the UI's pending/busy projection too.

No application source, repository tests, generated processor or skills were changed. No release verdict for
the runtime/compiler is reopened. Review evidence is attached as a
[portable probe](review_analyser_finish_first_pass2_2026-09-16_probe.txt), using the first review's helpers.

## Remaining blocker — B2, P2, CONFIRMED

**Files:**

- `src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/OperationGate.java:60`
- `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:3688`, `:2101`, `:2737`, `:4597`
- `src/test/java/telamin/fluxtion/audit/analyser/analyser/ui/AsyncOpenInterleavingFrameTest.java:155`

Sequence through the real frame and action dispatcher:

1. Socket-open A through a latch-controlled reader; wait until the reader enters, but keep it blocked.
2. Socket-open valid project P and wait for that synchronous project transition to return.
3. Read context **before releasing A**.

Observed:

```text
project open: successful
OperationGate.inFlightWhat(): null
context.inFlight: absent
MainFrame.loadInFlight: true
context.graphPairing.loading: true
context.graphPairing.pairing:
  pending — a log is loading; the graph is judged against it when the load lands, and the verdict appears here
status: opened project P
```

A is already superseded: no accepted completion can deliver the promised log/pairing. Releasing A eventually
sets busy false and records its stale result. Until then, the adapter is waiting for obsolete physical work
which the processor has correctly retired. If that reader hangs, the false pending state has no deadline.

This has a functional consequence beyond a spinner. I also tested:

1. Open project Old and log B.
2. Start delayed A.
3. Request another existing, regular project file containing a malformed properties Unicode escape. This
   reaches the gate and then genuinely fails to load; it is **not** the missing-path preflight control.
4. While A is still blocked, socket-open graph B, which matches the log still open.

Old and log B remain intact, the gate has no pending open, but graph-open returns `loading: true` and defers
pairing against B until the already-discarded A returns. Only then does context report the correct B/B match.

**Cause:** `onOpenProjectRequested` clears the authoritative description, but `requestProject` never
synchronizes `loadInFlight`/progress/pairing after `driver.submit`. Those remain governed by callbacks in
`onLoaded`/`onLoadFailed`. The new display test releases A at line 156 and waits for `Discarded` before
asserting no loading at lines 159–161; it therefore does not test retirement at the project boundary.

The changelog claims that "`context.inFlight` and the busy indicator stop reporting a load that can never
complete". Only the first half holds immediately.

**Required remedy:** synchronize the adapter's pending/busy projection from the gate after a project request
settles, on both success and failure, without waiting for an obsolete worker. Preserve the legitimate
same-project/missing-path no-op controls. Do not let a later stale result clear a genuinely newer pending
open, and do not broaden this fix into the deferred close/reset policy.

**Required regression:** keep A's latch closed while asserting `inFlight` absent, `loading` absent, progress
not busy, and immediate pairing against the surviving log after a failed switch. Then release A and assert
its stale evidence without resurrecting pending state. Add the newer-pending-open control beside it.

## Per-finding disposition

| Original finding | Disposition | Independent reproduction/result |
|---|---|---|
| B1 audience at completion | **CLOSED** | Socket graph B → socket delayed A → human Recent GraphML B → A arrives: zero dialogs, graph closed. Inverse human-arrival/socket-graph control: exactly one dialog. |
| B2 project retirement | **PARTIAL / OPEN** | Gate description retires, old completion is refused, and after completion busy clears. Before completion the adapter still says loading; detailed above. Socket same-project and missing-path controls preserve A. |
| B3 superseded failures notify | **CLOSED** | Both human- and socket-origin stale failures after newer B succeeds: zero dialogs, unchanged B status/state, stale audit record retained. Also tested A's failure while newer B is still pending: B's busy/description survive; B later opens. Accepted human failure: exactly one dialog. |
| F4 export framing | **CLOSED** | Exported a real supersession session and reopened it through the frame's normal log-open path: 15 captured records → 15 store rows. Parsed `operationGate` entries retain opIds 1 and 2, two distinct Pending rows and one stale LogOpened row naming expected id 2. Parsed levels are null: the exporter does not invent a header/level. |
| F5 chart call-boundary pin | **CLOSED** | New panel test passes. Restoring only the two casts at `ChartPanel.paintNotes` makes it fail: expected rule column 579 is absent; the old caller paints at 620. This specifically tests the caller, not a changed `ChartNotes` formula. |
| F6 close/reset during pending | **DEFERRED, correctly filed** | M44.3b exists. This pass did not change or reopen that policy; round 1's observed behaviour remains the recorded follow-up. |

### The failed-switch choice

I would accept the stated policy: a project request which reaches the session gate invalidates an outstanding
open even if profile loading subsequently fails. It avoids reviving an old request after an attempted session
transition. A missing path rejected before the gate, and the socket's same-project no-op, remain distinct
controls. The genuine failed-load probe above confirms the chosen behaviour while preserving the old project
and already-open log.

However, qualify `docs/specs/spec-session-processor.md:341`, which still promises **"load failed ⇒ nothing
closes, nothing changes"**. Existing project/log state is retained; a pending log request is not. State that
exception in the canonical decision table, not only the response handoff. This policy choice does not justify
leaving busy state attached to the superseded worker.

## Non-blocking follow-ups

1. **The new context example depicts an impossible combination.**
   `docs/site/ai-and-runbooks.md:67–68` shows `graphPairing.applies: true` together with `inFlight: "opening …"`.
   `MainFrame.context():4597` deliberately replaces the verdict with pending/loading during an outstanding
   open. Move the optional-key explanation outside the settled example, or show a separate pending example
   without `applies`. The implementation's refusal to claim the old pairing is correct.

2. **Finish the deterministic completion barrier in the frame tests.**
   `AsyncOpenInterleavingFrameTest:217` waits 400 ms after releasing the stale failure; an EDT flush does not
   prove the background task has submitted its callback. Wait for its recorded stale result before asserting
   silence. Likewise, the accepted-human-failure control at `:235` waits for the status set *before* the modal,
   then reads the watchdog counter immediately. Wait for the dialog observation itself. My initial diagnostic
   probe using that status-only pattern observed zero too early; with a dialog barrier the control reliably
   observes one. This is not a newly discovered product failure or a claim that the committed test failed here.

## Independent verification

Environment: macOS, Corretto 21.0.9; detached worktree
`/private/tmp/analyser-finish-first-pass2-b8a93533`. All real-frame probes use temporary homes and synthetic
logs/projects. Socket verbs in the custom probe use the actual `ActionExecutor` path without the HTTP
transport; the existing built-jar transition checker exercises that transport separately.

Commands, with Java 21 explicitly selected:

```sh
mvn -q -o test '-Dtest=AsyncOpenReplayTest,SessionAuditRecordTest,ChartNotesCallBoundaryTest'
mvn -q -o clean verify
mvn -q -o test '-Dtest=PairingDuringLoadFrameTest,AsyncOpenInterleavingFrameTest' \
  -Djava.awt.headless=false '-DargLine=-Djava.awt.headless=false'
python3 tools/verify-session-transitions.py --jar target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar
```

- Focused tests pass. Full verify, outside the sandbox for loopback tests: **1,429 / 0 failures / 0 errors /
  12 skipped**. The 12 are the display suites under Maven's headless default.
- Local display run: **12 / 0 / 0 / 0**, split 5 pairing + 7 asynchronous interleavings. This overwrites those
  two headless XML reports; it is not a single full-suite display run.
- Exact-tip [CI run 35155162288](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35155162288)
  succeeded for build, loop-bench and ui-frame. I read the display job log: **5 + 7 = 12**, none skipped,
  and the per-suite no-skips guard succeeded.
- Built-jar transition checker: **ALL PASS**. Its existing failed-switch check uses a missing path; the new
  custom malformed-profile case is the additional check across the gate.
- Eleven custom scenarios: mixed audience and its inverse; successful project transition; genuine failed
  switch from Old; same-project/missing-path controls; human/socket stale failure; stale failure while the
  newer request is pending; accepted human failure; export/reopen with parsed opIds.
- **Negative control:** selected ten response tests pass against the candidate. The same newly compiled tests
  against the original `1af7bbe5` built jar yield exactly six failures: both B1 cases, B2's transition case,
  B3's stale-failure case, gate-retirement replay, and export round trip. The other controls remain green.
  This is an original-code control, not a claim that I independently ran the author's three separate mutants.
- **Caller-only mutant:** a scratch `ChartPanel` class changes only
  `notes.byColumn(vx0, vx1, plotW)` to `notes.byColumn((long) vx0, (long) vx1, plotW)`; its one test goes red.
  No repository source, test or compiled output was patched for these controls.

Local evidence:

- `/private/tmp/analyser-finish-pass2-verify.log`
- `/private/tmp/analyser-finish-pass2-display.log`
- `/private/tmp/analyser-finish-pass2-probe-final.log`
- `/private/tmp/analyser-finish-pass2-negative-controls.log`
- `/private/tmp/analyser-finish-pass2-transitions.log`

To reproduce the custom probe after packaging, copy the first review's `*_probe.txt` attachment to
`FinishFirstProbe.java` and this pass's attachment to `FinishFirstPass2Probe.java` in a temporary directory,
then compile both against the built jar and run with a display:

```sh
javac -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar -d /private/tmp/finish-review-classes \
  /private/tmp/FinishFirstProbe.java /private/tmp/FinishFirstPass2Probe.java
java -Djava.awt.headless=false \
  -cp /private/tmp/finish-review-classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar \
  FinishFirstPass2Probe audience humanAudience project malformedProject sameProject badPath \
  humanFailure socketFailure staleWhilePending acceptedHumanFailure export
```

The probe prints observations; exit zero does not mean B2 is fixed. Its `BEFORE_OLD_READER_RELEASE` output
is the acceptance seam to turn into a regression test.

## WHAT I DID NOT CHECK

- No S3/rolled-set acquisition race, follow rotation, native package, performance or exhaustive concurrency run.
  The probes exercise the shared local completion boundary.
- No new hosted processor regeneration, framework/compiler re-audit, release workflow, push or dependency change.
- No fresh strict MkDocs build; the documentation follow-ups above are semantic inconsistencies, not link errors.
- No independent rerun of every first-pass accepted skill/golden fixture. They are unchanged in this response;
  the full suite includes their existing checks.
- No expanded audit of every human project entrance, save-as, adoption, create or reset policy. Same-project
  and missing-path preservation here are verified through the socket verb, exactly as in the original finding.
- I did not reproduce the author's three individual B1/B2/B3 mutation runs. The independently executed
  original-jar controls and caller-only chart mutant are stated separately above.

Review and tracker annotations only; no fixes implemented. Next pass should target B2's immediate
post-transition projection while the discarded reader remains blocked, retaining all controls that now pass.
