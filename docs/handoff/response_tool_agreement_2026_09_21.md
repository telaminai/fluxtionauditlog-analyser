# Tool-agreement review response — 2026-09-21

Review: [Opus at cac590a3](review_tool_agreement_2026_09_21_opus.md), feature `fcaf14ad`.
Predictions were frozen in tracker commit `a5173933` before these corrections. No release or merge
into main is authorized or performed. The original review is preserved verbatim.

## F1 — corrected; F3 — shared rule, integration check pending

The review is right. I incorrectly made the live framing rule apply to ordinary opens, and then
changed screenshots to match that regression. Published Format 1 permits an EOF record with no
closing separator. Ordinary heap, mapped and rolled opens now include it. Their metadata explicitly
says an EOF record is included; it does not establish completeness or damage. Status, Project,
context and PDF source captions carry this qualification.

Starting Follow on such a snapshot explicitly reopens through the existing session gate as a live
read. This is a reload boundary: record-bound flags, selection and filters clear. It is documented;
it does not silently mutate an existing row or a background walker's snapshot. Stopping Follow does
not promote its pending record; an ordinary reopen with Follow off reads a fresh snapshot.

`ExportEofRecordTest` compares heap, mapped and both rolled-store backends on a committed constructed
25-record **export-layout** fixture (not a participant replay) and the actual shipped 726-record demo.
All records survive, including the last value. A separator-terminated file is the negative control.
`FollowAppendTest` starts a separate live view, pauses, appends fields and a split separator, and
checks one eventual publication with late fields intact. An older snapshot walker remains intact.
`PairingDuringLoadFrameTest.pendingTrailingRecordIsVisibleInContextAndFollowStatus` checks the real
ordinary-open → Follow transition and echo/status boundary.

Prediction held in the focused gate. [Mutation](evidence/tool-agreement-2026-09-21/f1-review-mutation.json)
restores strict framing only in `HeapLogStore.fromFile`: the named export assertion fails 25 versus 24.
Restored code passes. A failed initial compile (local variable name collision) was repaired and was
not counted as a witness. A sandbox-only full run denied 29 socket bindings; the unrestricted clean
run passed, 1,767 tests, zero failures/errors, 49 display skips.

The end-marker branch's newer `02fa62b3` already removed STOPPED_MID_WRITE and preserves UNKNOWN for
unmarked exports. This response follows that same rule. Its stream marker is not implemented here.

## F2 — integration and canonical pin

Merged main `ea865d2d` into this feature branch, preserving both main skill changes and the Mongoose
change. The index is temporarily DRAFT only while creating the combined source commit, then will be
re-pinned in a separate ordinary commit. No draft index will be handed over as the final result.
Playground re-vendoring is prepared on its own branch, not deployed.

## F4 — corrected, without another endpoint trial

D12 now names the later 1.0.43 endpoint finding: cross-thread tailer use throws
ThreadingIllegalStateException and the exception is swallowed at DEBUG. D13 retains the original
observation but explicitly says the later listing/export test did not reproduce it. Its upstream row
stays open as unresolved, so upstream counts remain 8. Canonical skill/runbook parity is tested.
The later owner decision is text-file delivery first; the earlier Chronicle-first decision is
explicitly superseded, without claiming that the new route has shipped.

## F5 — CI confirmation pending

The reviewer's environmental skip is accepted as a limit on that run. A draft PR will trigger the
existing Linux/Xvfb display job; its no-skip assertion is retained. Local success is not a substitute.

## F6 — corrected witness anchors

D16 now identifies MarkerExtractor's STRICT carry reset; D18 identifies ChartPanel.setViewWindow's
axis partition (second occurrence), both with original reviewed revision/line. Captured failures are
unchanged; the review independently reproduced them at those sites.

## F7 — already recorded; made prominent

The reviewed `fcaf14ad` report states **49** headless skips in both Full-gate correction and Final
handoff, and the tracker header records them too. The reviewer appears to have missed those entries.
The response repeats the count above. It does not conflate headless skips with the display job.

## Counts and remaining scope

Analyser open rows **1 → 1** (D20 producer-blocked); upstream **8 → 8**. F1 is a regression in the
D6 implementation, not a newly invented baseline row. TA-5b delivery and TA-5c post-shipment client
spot-check stay open. No client sessions, paid generation or upstream runtime edits ran here.
