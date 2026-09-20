# Re-review — project starter journey implementation fixes

Reviewer: Claude, the session that wrote
[the first review](review_project_starter_implementation_2026_09_20_claude.md). Date: 2026-09-20.
Subject: analyser `9ff95e7`, and the
[author response](response_project_starter_implementation_2026_09_20.md).

Run in my own worktrees. No author checkout, production branch or staged application was modified.
No merge, rebase, publish or paid key. No finding was fixed by me.

## 1. Scope

| Repository | Previously reviewed | Now | Moved? |
|---|---|---|---|
| Analyser | `3ed21ea..e2ada11` | `e2ada11..9ff95e7`, 37 files, +1414/−106 | yes |
| Playground | `affdd85..47b9952` | `47b9952` | no |
| Compiler/starter | authorised range to `a80d4b52` | `a80d4b52` | no |

Because the other two repositories did not move, I did not re-run their gates. Their results in the
first review stand unchanged. Verification worktree at `9ff95e7`; report written on
`review/project-starter-journey-2026-09-20-claude`.

## 2. Verdict

**All six findings fixed. The analyser moves from NOT READY to READY WITH FOLLOW-UPS.**

| Scope | Was | Now |
|---|---|---|
| A — saved facts, landing, recovery | NOT READY | **READY**, with process restart still NOT VERIFIED by anyone |
| B, C, D, E | READY / READY WITH FOLLOW-UPS | unchanged, plus JI-5 and JI-6 closed |
| Analyser repository | NOT READY | **READY WITH FOLLOW-UPS** |
| Playground, compiler | READY WITH FOLLOW-UPS | unchanged, not re-run |
| Full journey / release | NOT READY | **NOT READY**, unchanged and not claimed otherwise |

The release verdict does not move, and nothing in this commit was supposed to move it. Publication is
still open, and the fresh-client and process-restart gates remain unperformed.

## 3. Findings, re-verified

### JI-4 · Superseded restore stranding the offer · FIXED, independently reproduced

`ResumeEvents.Outcome` is now a typed result carrying a `superseded` flag, and both superseded paths
complete their own pending recovery rather than returning early.

I did not take the recorded mutation on trust. I removed **one** of the two `supersedeRecoveryLog(opId)`
call sites and ran the guard test: case `[2]` failed with `Timed out` waiting for the offer to become
decidable, case `[1]` still passed. That is a finer-grained witness than the recorded one, which removes
both and fails both cases. Source restored, worktree clean. The test genuinely guards the defect.

### JI-2 · Adapter re-deciding what the graph decided · FIXED in substance, and my reproduction was wrong

Apply-time checks now return to the graph through `ResumeEvents.Checked` carrying both generation and
operation identity. The graph applies its whole-log-set rule and can only narrow the original plan. The
adapter's independently constructed store and its per-input filter are gone.

**I accept the author's precision correction, and it corrects me rather than the finding.** My stated
failure scenario had a rolled set where member A is withheld and member B restored at the cited lines.
The author is right that the old loop visited topology, design and diagnostics rather than individual log
members, and that the rolled log had already loaded before that method ran. So the mechanism I described
was not reachable at those lines. I derived that scenario from reading and presented it as a failure
scenario without having reproduced it; the label should have carried that distinction more clearly than
it did.

What was real, and what the author also accepts as real, is the independent adapter policy and the gap
between initial verification and publication. The replacement closes both. I am satisfied the finding was
sound and that its illustrative scenario was not.

### JI-3 · Stale accept or dismiss answering a newer offer · FIXED

`Requested` now carries the observed generation and `SessionRecovery.request` refuses a mismatch. Start
Page actions close over the generation of the offer they rendered. Guarded by a mutation that disables
the check and fails a named test.

### JI-1 · Three full passes per local open · FIXED for native readers, plugin cost explicitly retained

The owner decision I flagged has been taken, and taken the other way from my suggestion: no size
threshold, no metadata shortcut, full content verification at every size, with the reader optimised
instead. `FileReadIdentity` digests the exact bytes the indexer consumes, so the separate traversals are
gone for native readers.

**Measured on the same 449 MB, 400,001-record fixture as the first review:**

| | Before | After |
|---|---|---|
| open including identity | 2691 ms parse + 527 ms hashing = 3218 ms | **2905 ms** |
| marginal cost of identity | 527 ms, +20% | **214 ms, +8%** |

Two correctness checks that mattered more than the timing, both of which I ran rather than inferred:

- The single-pass digest is **byte-identical to an independent full-file SHA-256**. I compared both store
  implementations against `SessionResumeStore.identity` on the same file: `HeapLogStore` MATCH,
  `MappedLogStore` MATCH. This is the risk the heap path carried, because it digests an already-decoded
  buffer; the round trip is lossless in practice as well as by claim.
- A partial or disturbed read cannot produce a wrong hash. `Capture.finish()` refuses unless the consumed
  byte count equals the file size and size, mtime and fileKey are all unchanged, returning
  `"changed or incomplete during indexed read"` with a null digest. That degrades to unknown, which is
  the behaviour the rest of this design already uses.

Residual, correctly documented rather than hidden: opaque SPI readers still take two extra traversals,
because they own their I/O. That is the foreign-adapter path, so a third-party reader pays the full cost.
The response and the spec both say so. I record it as an accepted limitation, not a finding.

One observation for whoever touches `FileReadIdentity` next: its stream digests per byte in the
single-byte `read()` override, so its performance depends on callers using bulk reads. The native framer
does, which is why the measurement is good. It is worth a comment before someone adds a reader that does
not.

### JI-5 · Mutation witness naming the wrong assertion · FIXED, and better than I proposed

I asked for the guard to assert on the parsed failure label. The author did that and added the part I did
not think to ask for: four negative tests proving the guard can fail, including one whose code frame
quotes the expected label while a different assertion fails, which is precisely the false positive I
found.

Verified by running it. The guard tests pass, `test_tools.py` now carries the case, and the real mutation
against the playground now reports its true witness verbatim: `browser handler: expected [ …(10) ] to
include 'Implement this event callback accordi…'`. That matches what I observed independently in the
first review and no longer matches the stale `browser reference` label, which the guard now rejects.

### JI-6 · Dead fallback helper · FIXED

Zero references remain.

## 4. Gates I ran

| Check | Result |
|---|---|
| `mvn clean package` at `9ff95e7` | success |
| Headless `mvn test` | **1718 tests, 0 failures, 0 errors, 40 skipped** — matches the author's claim |
| Display set, both headless properties false | **41 tests across the nine named classes, 0 failures, 0 skips** — matches; `SessionRecoveryFrameTest` grew 5 → 8 |
| `mkdocs build --strict` | passes |
| `python3 tools/test_tools.py` | all passed, including the new witness case |
| `python3 tools/test_comment_mutation_witness.py` | 4 tests, OK |
| Recovery mutation | seen red on the intended assertion, source restored |
| Comment mutation | correct label reported, playground worktree restored clean |
| JI-4 mutation, my own variant | reproduced the timeout, source restored |
| Identity cross-check, heap and mapped versus raw file | both MATCH |
| Load timing, 449 MB | 2905 ms including identity |
| Rule-1 sweep, tracked and untracked | clean |
| `git diff --check` | clean |

## 5. Still not verified, and not closed by this commit

Unchanged from the first review, and none of it is the author's failure to have done something in this
commit. A real process quit and relaunch in an isolated home. Acquisition through the shipped dialog
against the deployed site. Publication of the starter artifact, which still returns 404 and is correctly
recorded as failing. A fresh-client journey. No amount of source reading closes any of them, including
mine.

## 6. Handoff

Nothing is owed to me. The six findings are closed, three of them guarded by mutations I either
reproduced or re-ran, and the one place the author pushed back on my reasoning he was right and I have
said so above.

For the author's own sequencing, the remaining work on this branch is release-gate work rather than
defect work: publication, then the public parity check, then the two experiments that need a real process
and a real network. The plugin-reader hashing cost is the only accepted limitation carried forward, and
it is documented where a reader will meet it.
