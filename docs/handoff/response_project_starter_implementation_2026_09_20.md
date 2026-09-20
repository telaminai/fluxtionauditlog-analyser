# Author response — project starter journey implementation review

2026-09-20. Responds to all six findings in
[the independent review](review_project_starter_implementation_2026_09_20_claude.md).
Review preserved unchanged on the implementation branch. These are author dispositions, not an
independent READY verdict. The full journey and public release remain incomplete.

## Independent closure received

The [re-review](rereview_project_starter_implementation_2026_09_20_claude.md), review-branch commit
`1d19f33`, closes JI-1–JI-6 at implementation `9ff95e7`. Analyser: READY WITH FOLLOW-UPS. Full journey
and release: NOT READY. The dispositions below retain the author's original claims; the linked report
is the independent closure. The reviewer's suggested bulk-read comment is now in `FileReadIdentity.open()`;
it changes no behaviour. Plugin hashing cost and the outstanding release/experiment gates remain open.

## Dispositions

| Finding | Author disposition | Correction and evidence |
|---|---|---|
| JI-4 | Fixed; re-review requested | Both superseded reader-success and reader-failure callbacks finish their own pending recovery with a typed superseded outcome. The graph returns to `offered`; accept/dismiss works again. A stale callback cannot finish a newer recovery. Two latch-controlled real-frame cases pass; removing the completion calls makes both fail. |
| JI-2 | Fixed; re-review requested | Apply-time checks return through `ResumeEvents.Checked`, with generation and operation identity. The graph applies its whole-log-set rule and can only narrow the original plan. Reader identities are checked before publishing any recovered log; changed sets are closed and withheld together. Independent design/results still restore. A two-member graph regression and a delayed-reader display regression pass; disabling graph rechecks makes the set regression fail. |
| JI-3 | Fixed; re-review requested | `Requested` carries the observed generation. Start-page actions close over the rendered offer's generation. Old accept and old dismiss are refused against a newer offer. MCP continues to address the current offer when handled. Generation is exposed in the existing restoration context and documented. Removing the guard makes the stale-request regression fail. |
| JI-1 | Fixed for native indexed opens; plugin cost retained explicitly | Owner rejected a size cutoff and chose full verification plus reader optimisation. Heap YAML hashes the exact losslessly decoded UTF-8 buffer; streamed YAML hashes the raw bytes as indexing consumes them. Rolled stores retain ordered member identities. Ordinary native opens no longer make two additional file traversals. No sampling or metadata cache. Full capture/restore checks remain; restoration also checks the entire set after indexing. Opaque SPI readers retain before/after hashing because they own their I/O. |
| JI-5 | Fixed; refreshed witness | Mutation helper reads structured Vitest results: exactly one failed packaged-resource test, two passes, no skips, and the first assertion line must name `browser handler`. Surrounding source text cannot satisfy it. Rerun: real mutation fails at handler with ten comments; restored source passes 3/3. The same captured failure is rejected when checked against the old reference label. Four guard tests include an unrelated assertion whose code frame quotes the expected label. |
| JI-6 | Fixed | Removed unused `mongooseHosted()`. The full-catalogue tests and display case still pass. |

A precision correction to JI-2's literal reproduction: the old `finishRecoveryInputs` loop visited
**topology/design/diagnostics**, not individual log members; the rolled log had already loaded before
that method ran. I therefore do not claim to have reproduced “A withheld, B restored” at those lines.
The independent adapter policy and the gap between initial verification and publication were real.
The replacement submits checks to the graph and rejects changed log sets before publication. The
new test evidence separates the two-member graph decision from the real delayed-reader integration;
it does not pretend those are a witnessed two-member filesystem race in a running UI.

The generated session dispatcher and GraphML are unchanged: existing event types, handler signatures
and node edges are unchanged. Record payloads and node decision bodies changed; generated-processor
regressions exercise the committed dispatcher directly. No generator or paid key was needed.

## JI-1 performance and identity limits

[Reproducible benchmark and raw rounds](evidence/project-starter-review-fixes-2026-09-20/README.md).
Same reviewer fixture: 470,820,000 bytes, 400,001 records. Three warm-cache rounds, Java 21. Median
indexed-SHA time was 2660.9 ms, versus 2422.6 ms without digesting (+9.8%) and 2886.8 ms for the previous
three-pass shape (7.8% faster). This is below a 15% additional median-time budget, recorded after this first measurement
for subsequent comparisons; it was not a preregistered prediction. All digests matched the independent full-file SHA-256 exactly.

This is **not zero-cost hashing**, a cold-cache benchmark, a portable latency promise or a plugin-reader
optimisation. The raw timings vary significantly; do not present the reviewer's 20% and this run's 9.8%
as a controlled cross-session speedup. Within this run, old and new shapes were measured together.
Hashing during capture and explicit verification is intentionally retained. Metadata guards detect
observed concurrent changes; metadata alone never establishes identity. File identities still do not
prove the application's build or execution identity. Growing/remote inputs keep their existing caveats.

## Verified in this response

- Clean Java package: 1,718 tests, zero failures/errors, 40 expected headless display skips.
- Full nine-class display gate: 41 tests, zero failures/errors/skips, including catalogue and new recovery
  interleavings. No separate catalogue rerun was required.
- New digest tests cover heap/streamed/rolled raw bytes (Unicode and CRLF), ordered member hashes,
  incomplete reads, changed-during-read refusal, same-size/same-mtime replacement and follow invalidation.
- Three deliberately reverted behaviours fail their named assertions, recorded in
  [mutations.json](evidence/project-starter-review-fixes-2026-09-20/mutations.json). Source restored before
  the final clean and display gates.
- Comment mutation and restored three-test parity run used the already-built local starter jar. The
  playground source is byte-restored; no playground/compiler implementation change was required.
- Python tools gate (including four new witness-guard tests), strict MkDocs, diff check and rule-1 sweep.
  Initial sandbox-only socket/display failures were rerun outside the sandbox; they are not counted as
  successful gates or product failures.

Not rerun: full compiler/playground suites, public artifact fetch, actual process restart, deployed
acquisition or the held-out fresh-client journey. The unchanged producer heads retain the independent
review's evidence. The prior public-artifact 404 remains an open publication gate, not today's fetch.
No owner key was used, no publication performed, no app execution added to the analyser.

## Re-review handoff

Review this response and the changes after the preserved review commit on `feat/project-starter-journey`.
Use a separate review worktree and retain the original review. Concentrate first on JI-4 and JI-2:
late successful and failed reads, a newer recovery while old callbacks arrive, changed rolled membership,
and whether every recheck still goes through the graph. Then attack stale UI buttons and the full-byte
identity/performance tradeoff, especially plugin and follow boundaries. Repeat the supplied mutations,
restoring each before the green run. Judge the JI-2 precision correction explicitly rather than carrying
forward an unobserved reproduction.

Append a re-review to the original report or write
`docs/handoff/rereview_project_starter_implementation_2026_09_20_<reviewer>.md` on your own review branch,
with CLOSED / NOT CLOSED / CLOSED WITH FOLLOW-UP per JI item, commands and verified/read/not-verified
separation. Commit/push the report and return an ordered author handoff. Do not fix source, merge,
rebase, force-push, publish or spend a key. READY for these fixes does not close the full-journey gates.
