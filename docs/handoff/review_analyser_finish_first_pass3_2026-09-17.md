# Independent review — finish-first response, pass 3

## Verdict: READY — B2 closed; no remaining blocker in this review scope

Reviewed `d78a0144edc5daf1a7a0ecb8e18bce25e99a70c2`, in a fresh isolated worktree, against the
[second-pass review](review_analyser_finish_first_pass2_2026-09-16.md) and
[response](handoff_analyser_finish_first_response2_2026-09-16.md). Reviewer: OpenAI Codex, 2026-09-17.

The adapter now retires busy/pending state at the project boundary, **before the discarded reader returns**,
on both a successful switch and a genuine failed profile load. Pairing immediately follows the surviving
log and current graph. Legitimate pending loads remain pending; a retired reader cannot clear a newer open.

This closes the finish-first correctness review's last blocker. The earlier acceptance of B1/B3, export F4,
chart F5, goldens and skills stands; this pass did not reopen those implementations. M44.3b's close/reset
policy and the separately recorded owner/dependency decision remain separate work, not silently approved by
this verdict. No release workflow was run.

## B2 — CLOSED, independently reproduced

**Code inspected:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:3688`
(`requestProject` after `driver.submit`), `:3700` (`syncBusyWithGate`), `:2101` (`setBusy`), and the
existing stale-completion branches at `:2527` / `:2737`.

The new helper clears the adapter only when it still says loading and the session gate says no open is
outstanding. It does not introduce a second supersession decision: the gate remains authoritative.
`setBusy(false)` clears both `loadInFlight` and progress visibility, and restores the processor's pairing
verdict when a log/graph survives. The check runs after the synchronous project dispatch/effects finish,
whether the profile loaded or failed. An obsolete worker need not be cancelled to establish that state.

I re-ran the **unchanged second-pass public-frame probe**, not just the author's new tests:

| Sequence | Result at `d78a0144` |
|---|---|
| Delayed A, then successful project P; inspect before releasing A | `loading=false`, gate pending null, `context.inFlight` absent, no pending pairing. A remains blocked. Its later result is recorded stale and opens nothing. |
| Old project + log B, delayed A, then malformed project file that reaches the gate | Switch fails, Old/B survive, loading clears immediately. Opening graph B **before releasing A** returns `appliesToOpenLog=true`, with the logged/declared counts, not a pending answer. |
| Same active project reopened through the socket | A remains pending and later opens normally. |
| Missing project path rejected before the gate | A remains pending and later opens normally. |
| Old failing A completes while newer B is pending | B's description/loading survive, status is unchanged, no dialog; B subsequently opens. |

The decisive output from the first two cases was `BEFORE_OLD_READER_RELEASE loading=false inFlight=null`,
with `stale=0`: no discarded completion had run yet. This is the boundary the previous test missed.

### Additional checks beyond the author's two new cases

The [attached assertion probe](review_analyser_finish_first_pass3_2026-09-17_probe.txt) checks the real
frame's progress visibility as well as context and `loadInFlight`, while the old reader's latch is still
closed. Four additional cases passed:

1. B/B already paired → delayed A → failed project switch: the existing B/B verdict is restored immediately.
2. B/B → delayed A → deliberately open mismatching graph C → failed switch: the restored verdict is **B/C
   false**, not the obsolete B/B true. The stale A completion leaves that verdict intact.
3. Delayed A → successful project transition retires A → start delayed B → A succeeds stale: B's progress,
   loading and pending description remain; B subsequently lands and clears them.
4. The same project-retirement/newer-load sequence, but A fails stale: the same preservation holds.

All four socket-driven cases showed zero dialogs. The probe asserts progress is hidden at retirement and
visible while a genuinely newer load remains pending, rather than inferring that from a context string.

## Test effectiveness and CI

The committed suite now asserts B2 **before releasing the reader**, then waits for stale audit evidence and
checks the aftermath. The failed-switch fixture is a readable, malformed-properties file; it exercises
profile loading after the gate, not the missing-path preflight control. The two-reader control keeps B blocked
while A's stale result lands.

I independently ran the candidate's nine interleaving tests against both sets of production classes:

```text
d78a0144: 9 found, 9 passed, 0 failed, 0 aborted, 0 skipped
b8a93533: 9 found, 7 passed, 2 failed, 0 aborted, 0 skipped
```

Exactly these fail against the pre-fix jar:

- `b2_aProjectSwitchDuringAPendingLoad_retiresIt`: busy projection is still loading at the boundary.
- `b2_aFailedSwitchStillRetiresThePendingLoad_andTheSurvivingLogPairsAtOnce`: loading is still present.

The other seven, including the newer-pending control, pass on both. This is an independently executed
**pre-fix production-jar control**, not a claim that I edited and ran the author's source mutant. The only
application-source difference between those revisions is this `MainFrame` synchronization change. Repository
source, tests and generated classes were not patched for the comparison.

Exact-tip [CI run 35159012666](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35159012666)
is successful for **build, loop-bench and ui-frame**. I read its display-job log: pairing **5/0/0/0**,
interleavings **9/0/0/0**, total **14/0/0/0**, and the per-suite no-skips guard passed. Thus these tests are
running on the CI display, not merely being selected under a headless JVM.

## Documentation and test-barrier follow-ups — CLOSED

- The session decision table now qualifies “load failed ⇒ nothing closes, nothing changes”: an outstanding
  log request is superseded when the project request reaches the gate, even if profile loading fails. This
  matches both the accepted policy and the independently observed surviving-project/log behaviour.
- The settled `context` example no longer includes `inFlight`. Its separate pending example has
  `pairing`/`loading`/`inFlight` and no settled `applies` claim.
- The stale-failure test now waits for `staleResult` in the session audit on the EDT instead of a fixed sleep.
  Each tested frame has no earlier stale result to satisfy that barrier accidentally. The human-failure
  control waits for the dialog watchdog itself, not the status assigned before showing the modal.

No new blocking or non-blocking finding was established in this narrowed pass. The ledger's question about
syncing busy after `close` is deliberately **not** answered by adding this helper elsewhere: M44.3b first
needs a decision about whether close/reset invalidates a pending open. That policy remains deferred.

## Independent verification

macOS, Corretto 21.0.9; detached worktree `/private/tmp/analyser-finish-first-pass3-d78a0144`.
Real-frame probes use temporary homes, synthetic logs and profiles. They exercise `ActionExecutor` directly;
the separate built-jar checker covers the socket transport.

With Java 21 explicitly selected:

```sh
mvn -q -o test '-Dtest=AsyncOpenReplayTest,SessionAuditRecordTest'
mvn -q -o clean verify
mvn -q -o test '-Dtest=PairingDuringLoadFrameTest,AsyncOpenInterleavingFrameTest' \
  -Djava.awt.headless=false '-DargLine=-Djava.awt.headless=false'
python3 tools/verify-session-transitions.py --jar target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar
```

- Focused tests passed.
- Full verify: **1,431 / 0 failures / 0 errors / 14 skipped**, counted from Surefire XML before the display run.
  All 14 skips are the two display-only suites under the normal headless setting.
- Local display run: **14 / 0 / 0 / 0**, split 5 pairing + 9 interleavings. This overwrites those two XML
  reports; it is not a full-suite display run.
- Built-jar transition checker: **ALL PASS**.
- Original independent probe: five selected sequences; additional assertion probe: four sequences; results
  detailed above. No sleep is used as proof that an old worker has completed.
- After writing the review, `SpecLinksResolveTest` passed **3/0/0/0**; the new handoff's local links resolve.
  `git diff --check` and the tracked-file sweep were clean; the two new review files were also swept explicitly.

Local evidence:

- `/private/tmp/analyser-finish-pass3-verify.log`
- `/private/tmp/analyser-finish-pass3-display.log`
- `/private/tmp/analyser-finish-pass3-original-probe.log`
- `/private/tmp/analyser-finish-pass3-additional-probe.log`
- `/private/tmp/analyser-finish-pass3-negative-controls.log`
- `/private/tmp/analyser-finish-pass3-transitions.log`
- `/private/tmp/analyser-finish-pass3-ci.log`

To reproduce the additional probe, copy the first and second reviews' `*_probe.txt` attachments to
`FinishFirstProbe.java` and `FinishFirstPass2Probe.java`, and this attachment to `FinishFirstPass3Probe.java`
in a temporary directory. Compile them against the built jar, then run with a display:

```sh
java -Djava.awt.headless=false -cp <probe-classes>:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar \
  FinishFirstPass3Probe restoreExisting changedGraph retireThenNewer retireThenNewerFailure
```

## WHAT I DID NOT CHECK

- No new S3/rolled-set/follow acquisition race, performance run, native packaging or exhaustive concurrency
  exploration. These probes test the shared local completion/project boundary.
- No exhaustive human/dialog-only project-entry audit: adoption, create, fork, startup activation and import
  remain outside these executions, as the built-jar checker also states. The shared request method was read.
- No new hosted processor regeneration, runtime/compiler re-review, release workflow, dependency change,
  publishing or push. The separate recorded owner/dependency decision is not adjudicated here.
- No fresh local strict MkDocs build. I checked the changed examples/decision table semantically; full verify
  includes the repository's documentation-link tests, and exact-tip docs deployment is green.
- No repeat of the earlier export/marker mutation campaign or independent skill/golden review. Those files
  are unchanged by this response; their existing tests ran with the full suite.

Review/ledger/tracker files only. Maven's generated `dependency-reduced-pom.xml` has a line-ending-only change
in the isolated worktree; it is not a proposed change and was not copied to the main checkout. No implementation
fix is included.
