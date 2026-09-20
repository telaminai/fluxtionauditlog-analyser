# Independent review — project starter journey implementation

Reviewer: Claude (a session that did not implement this work). Date: 2026-09-20.
Brief: [`brief_review_project_starter_implementation_2026_09_20.md`](brief_review_project_starter_implementation_2026_09_20.md), followed as written.

Everything below was run by me in my own review worktrees. No author checkout, staged application or
production branch was modified. No merge, rebase, publish or paid key. No finding was fixed.

## 1. Reviewed heads, scope and environment

| Work | Branch | Reviewed range | Head at review |
|---|---|---|---|
| Analyser | `feat/project-starter-journey` | `3ed21ea..e2ada11` (7 commits, 89 files, +5012/−230) | branch tip `e4ce6a9` = brief commit, documentation only |
| Playground | `feat/project-starter-journey` | `affdd85..47b9952` (3 commits, 27 files, +867/−54) | `47b9952` |
| Compiler/starter | `feat/project-starter-journey` | authorised private range, 6 commits | `a80d4b52` |

Upstream divergence, read only: analyser local branch equals `origin/feat/project-starter-journey` at
`e4ce6a9`. Author worktrees were clean at `47b9952` and `a80d4b52` when I started and were not touched.
No upstream movement occurred during the review.

Review worktrees: `/private/tmp/journey-review-analyser` (branch
`review/project-starter-journey-2026-09-20-claude`), `/private/tmp/journey-review-web` and
`/private/tmp/journey-review-compiler` (both detached at the pinned revisions). Java 21.0.11, macOS,
Apple Silicon. Display tests ran against a real screen, not a virtual one.

Private compiler source detail is deliberately absent from this public report. Only artifact-facing
outcomes appear. No companion was needed, because no finding required private source detail.

## 2. Verdicts

| Scope | Verdict |
|---|---|
| **A — saved facts, landing, session recovery** | **NOT READY** — JI-1 to JI-4 |
| **B — full catalogue and acquisition** | READY WITH FOLLOW-UPS — JI-6 only; deployed acquisition NOT VERIFIED |
| **C — default-on support, headless parity, runbook routing** | **READY** — every attack in the brief passed; no finding |
| **D — canonical comments, ownership, publication** | READY WITH FOLLOW-UPS — parity verified locally; publication gate verified OPEN |
| **E — evidence and claims** | READY WITH FOLLOW-UPS — JI-5 |
| **Analyser repository** | **NOT READY** |
| **Playground repository** | READY WITH FOLLOW-UPS |
| **Compiler/starter repository** | READY WITH FOLLOW-UPS (artifact-facing only) |
| **Full journey / release** | **NOT READY**, and not claimed otherwise by the author |

The full-journey verdict is separate on purpose. What is implemented is a substantial and largely sound
slice. It is not the completed spec: publication is open and verified failing, and the fresh-client and
process-restart gates have not been performed by anyone, including me.

## 3. Findings

### JI-1 · Every local log open now makes three full passes over the file · Moderate · analyser

`src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java:3415-3421` hashes the whole
file, opens it with the reader, then hashes the whole file again. `SessionResumeStore.identity`
(`session/resume/SessionResumeStore.java:50-68`) is an unbounded streaming SHA-256 with a 64 KB buffer,
no size cap, no cache and no conditionality. Rolled sets are worse: `MainFrame.java:3449` and `:3455`
hash **every member** before and after. A GraphML open double-hashes at `TopologyPanel.java:764,771`.
A restore re-hashes every input twice more, and every project switch or exit hashes them again.

**Measured, not inferred.** 449 MB fixture, 400,001 records, warm page cache, hardware-accelerated
SHA-256:

| Step | Time |
|---|---|
| index-first parse | 2691 ms |
| one SHA-256 pass | 263 ms |
| **added by the two passes** | **527 ms, +20% on load** |

Hashing ran at about 2.1 GB/s, which is the favourable case. The measurement warms the cache first, so
the two extra passes are CPU-bound here; on a cold cache, a network mount or a spinning disk they are
I/O-bound and cost proportionally more. On hardware without SHA extensions the proportion rises further.

**Impact.** The product's stated purpose is "index-first, so it stays fast on multi-GB logs"
(`docs/ONBOARDING.md:9`). This adds roughly a second per gigabyte to every local open, unconditionally.
It is off the EDT, so the UI does not freeze; this is a latency regression, not a hang.

**Correction.** Make the policy explicit rather than unconditional: skip or sample above a declared size
threshold and record that the identity is unknown, or derive identity from the reader's existing single
pass, or cache by path plus size plus mtime plus fileKey. Any of the three is acceptable; silence is not,
because an unknown identity is already a supported state everywhere else in this design.

**Acceptance check.** A timing test over a large disposable fixture asserting either that the added cost
stays inside a declared budget, or that hashing is skipped above the threshold and the input is reported
as unverified.

### JI-2 · The Swing adapter re-decides what the session graph already decided, with different rules · Moderate · analyser

The graph owns the plan. `session/resume/SessionRecovery.java:73-83` applies an all-or-nothing rule to
log-role inputs: if any log changed, `logsComplete` is false and no log is offered.

`MainFrame.finishRecoveryInputs` (`MainFrame.java:6115-6156`) then constructs a **fresh**
`SessionResumeStore`, re-runs `check(...)` over the same snapshot, derives its own unchanged set, and
withholds **per input**. It does not reapply `logsComplete`.

**Failure scenario.** A rolled set of logs A and B is offered. Both are unchanged at offer time, so the
graph marks both available. A changes between offer and apply. The adapter withholds A and applies B.
The brief requires the opposite: "The complete ordered log set must refuse." The graph would have
refused both; the adapter partially restores.

This is also the second of the two full re-hashes counted in JI-1.

**Correction.** The adapter applies the graph's plan and reports the outcome. If a re-verification is
wanted at apply time, submit the result as an event and let the graph decide again under the same rule.

**Acceptance check.** Offer a two-member log set, change one member between offer and apply, assert
neither is restored and the outcome says the set was withheld as a set.

### JI-3 · An accept or dismiss carries no generation, so a stale click can answer a different project's offer · Moderate · analyser

`session/resume/ResumeEvents.java:10` declares `public record Requested(boolean accept)`. Its siblings
`OfferLoaded`, `Checked` and `Finished` all carry a generation; this one does not.
`SessionRecovery.request` (`:47-62`) validates only that the state is `offered`.
`SessionRecoveryController.dismiss()/restore()` (`:56-61`) stamp nothing either.

**Failure scenario.** Project A arms an offer and the Start Page renders its buttons
(`StartPanel.java:191,194`). The user switches to project B, which arms a new offer. A click delivered
from the stale rendering is accepted against project B's candidate. The spec forbids restore decisions
mixing project state.

**Correction.** Stamp `Requested` with the generation observed when the control was rendered and refuse
a mismatch, exactly as the other three events already do.

**Acceptance check.** Arm offer A, switch project to arm offer B, deliver A's request, assert refusal
with a message naming the superseded offer.

### JI-4 · A superseded restore strands the recovery node in `restoring` and both buttons stop working · Moderate, blocking · analyser

`MainFrame.onLoaded` returns early when the operation gate refuses the result
(`MainFrame.java:3620-3624`) **without** calling `completeRecoveryLog`, which is reached only on success
(`:3789`) or failure (`:3386`). No `ResumeEvents.Finished` is submitted. `pendingRecovery` stays set and
`SessionRecovery` remains `state = "restoring"`, so `echo().available` is false and both
`restoreSession` and `dismissSessionRestore` refuse (`MainFrame.java:5017-5030`).

**Failure scenario.** Accept a restore, then open another log before it completes. The restore is
superseded, and the offer never returns to a decidable state. The user can neither restore nor dismiss
until some later unrelated open happens to resolve it through the opId-mismatch branch at `:6096-6099`.

**Impact.** A user-visible stuck state reachable by ordinary impatience, in the subsystem this slice
exists to deliver. This is the finding I would fix first.

**Correction.** Complete the pending recovery on the gate-refusal path with a superseded outcome, so the
graph receives `Finished` and returns to `offered` or `none`.

**Acceptance check.** Start a restore, supersede it with another open before completion, assert the
offer is decidable again and that both accept and dismiss work.

### JI-5 · The committed comment-mutation evidence names the wrong witness, and its guard cannot tell · Moderate · evidence integrity, analyser repository

The brief flagged a seam here. It is real, and it has already drifted.

`docs/handoff/evidence/project-starter-comment-contract-2026-09-20/mutate-browser.py` asserts
`'browser reference' in output` and prints "Seen red: packaged-resource comparison fails at browser
reference".

I re-ran it against the current tree. The helper exited 0 and printed that line. The **actual** failing
assertion was:

```
AssertionError: browser handler: expected [ …(10) ] to include 'Implement this event callback accordi…'
```

The committed `parity-seen-red.log` records a different failure again:

```
AssertionError: browser reference: expected [ …(9) ] to include 'This reference's propagation mode is…'
```

Nine emitted comments then, ten now. So the evidence log predates the current code, the failing
assertion has moved, and the helper's substring guard did not notice because vitest echoes surrounding
source lines that contain the words "browser reference".

**What is still true.** The mutation is genuinely detected: prefixing the emitted text does fail the
parity test for the right reason. The defect is in the evidence, not the product.

**What is not true.** That the recorded witness describes what happens today, and that a nonzero helper
exit demonstrates the intended assertion fired. The guard would also pass on an unrelated failure.

**Correction.** Assert on the parsed failing assertion label rather than substring presence, and
recapture the log. **Acceptance check.** The helper must fail when the failure label differs from the
expected one; prove it by pointing it at a different induced failure.

### JI-6 · Dead fallback helper left behind · Minor · analyser

`template/TemplateCatalogue.java:73-75` still defines `Entry.mongooseHosted()`. After `forPicker()`
replaced the old selection rule it has no call site in `src/main`, `src/test` or `tools`. Harmless
today; it is the residue of the removed hosted-only fallback and invites accidental reuse. Remove it, or
state why it is retained.

## 4. Verified, read only, not verified

### Verified by running it

| Check | Result |
|---|---|
| Analyser `mvn clean package` | success |
| Analyser headless `mvn test` | **1710 tests, 0 failures, 0 errors, 37 skipped** — matches the author's checkpoint |
| Analyser display set, both headless properties false | **38 tests, 0 failures, 0 errors, 0 skipped**, Surefire XML inspected per class |
| `mkdocs build --strict` | passes (needed `~/.local/bin/mkdocs`; the default python had no mkdocs module) |
| `python3 tools/test_tools.py` | all passed |
| `git diff --check` | clean |
| CLAUDE.md rule 1 sweep, exact tracked form plus untracked files | clean |
| Compiler focused suite | **84 builder + 47 starter, 0 failures, 0 skips** — matches checkpoint |
| `verify-jar.py` | 2060 classes, Java 17 floor, keyless validation 0.178 s |
| Playground `pnpm vitest run` with the freshly built jar | **504 passed, 36 files** — matches checkpoint |
| `pnpm build` | passes |
| `pnpm check` | **4 errors, 6 warnings, 611 files** — matches the author's claim exactly |
| `verify-comment-contract.mjs --local-artifact` | passes, printing "LOCAL parity only; publication remains OPEN" |
| `verify-comment-contract.mjs` public | **HTTP 404**, correctly failing, correctly declared |
| `VerifyStarterProfiles` over 14 handler-produced ZIPs | **14/14 import, all pointers resolve, no rejected declarations** |
| Recovery mutation `mutation.py` | genuine seen-red, line-matched to the intended assertion, source restored, test green again afterwards |
| Comment mutation `mutate-browser.py` | red produced, but see JI-5 |
| Triple-read timing, 449 MB / 400k records | parse 2691 ms, one hash 263 ms, +20% added |

Two observations better than claimed: `TemplateCatalogueFrameTest` passed inside the single display run
rather than needing a separate one, and the display run showed zero skips across all nine classes.

### Verified by reading source, with citations above

No implicit startup load remains: `Main.java:80-84` opens only an explicit command-line path and then
calls `offerSessionRecovery()`; `MainFrame.reopenLastGraphml` is now a no-op redirect to the offer at
`:6010`; `config.logFile` survives only as a file-chooser directory hint. Restore and dismiss
exclusivity is enforced at `ActionExecutor.java:873-877` with `params.size() != 1`. Change detection is
content hash only, with size, mtime and fileKey used solely as a torn-read guard
(`SessionResumeStore.java:41-48, 60-63`); identical size and mtime with different bytes is refused and
is pinned by a test. The picker lists every entry with no hosted-only fallback; key and bootstrap
disclosure distinguish declared, absent, explicitly none and unrecognised, and nothing infers a key
requirement from `mode` or from a recommendation. An absent `analyserSupport` query parameter preserves
the template's parsed value; invalid, repeated, token-plus-override and bundle-plus-false all refuse.
`assertUniquePaths` runs before the ZIP map is populated and fails on identical content. Opting out
retains `PROJECT.md` and the build runbook while dropping the analyser profile and agent entry files.
Standalone Spring does ship `setup.sh`, `validate.sh`, `generate.sh`, `run.sh` and
`fluxtion-authoring.json`, so the Spring-label concern in the brief is not a gap here. The Mongoose
hosting runbook covers ordering, reset, restart, cached dispatch, replay, capture, retention,
auto-start and per-processor recording, pins mongoose 1.0.29 matching the project's resolved version,
and promises no script that is not emitted. Remote inputs degrade to unknown identity rather than a
false one, which is the correct behaviour under the stated design.

### Not verified, and nobody should read this review as closing them

- **Process quit and relaunch.** Not performed. The brief itself notes the author's witness constructs
  another `MainFrame` rather than another OS process, and I did not close that gap either. The
  no-implicit-load conclusion above rests on source reading plus in-process tests.
- **Deployed network and UI acquisition.** The catalogue evidence is a real picker over branch metadata
  and the profile evidence invokes the real handler and importer. Neither is a download from the live
  site through the shipped dialog. Not performed.
- **Public publication.** Verified *failing* today, 404. Correctly declared open by the author. It must
  not be recorded as anything else.
- **Fresh-client run.** Not performed, and not closable by reading source.
- **Browser preview.** Out of scope for these branches and not attempted.

## 5. Separation of concerns

**Newly found regressions:** JI-1, JI-2, JI-3, JI-4. All four arrive with this slice.

**Pre-existing defects:** none found in the reviewed ranges.

**Promised but unimplemented:** the full journey remains unfinished by the author's own statement. The
comment resource exists and is verified locally but is not published, so the parity gate is structurally
unsatisfiable today.

**Already declared release gates:** publication, fresh-client, process restart, browser preview.

**Explicit judgement on deferral.** JI-4 blocks merge in my view, because it is a reachable stuck state
in the subsystem this branch delivers, and the fix is small and local. JI-2 blocks merge because the
adapter can partially restore a log set the graph refused, which contradicts a stated requirement rather
than merely falling short of one. JI-1 and JI-3 are follow-ups that do not block merge but should not
reach a release. JI-5 blocks nothing in the product and should be corrected before the evidence packet
is cited anywhere, because it currently misdescribes its own witness.

## 6. Author handoff, in order

1. **JI-4** — complete the pending recovery on the gate-refusal path. Smallest fix, worst symptom.
2. **JI-2** — remove the adapter's second decision, or feed its re-check back through the graph so one
   rule governs both.
3. **JI-3** — stamp `Requested` with a generation.
4. **JI-1** — decide and state the hashing policy: threshold, reuse, or cache. Measured at +20% on a
   449 MB log under favourable conditions.
5. **JI-5** — fix the mutation helper's guard and recapture the log before the packet is cited.
6. **JI-6** — delete or justify the dead helper.

**Cross-repo dependencies.** None of the six crosses a repository boundary. The playground and compiler
work needs nothing from this list. The only cross-repo item outstanding is publication of the starter
artifact, which unblocks the public parity gate and nothing else in this slice.

**Outstanding experiments**, none of which this review performed or closes: a real process restart in an
isolated home, a download through the shipped dialog against the deployed site, the public artifact
check after publication, and a fresh-client journey.

**Owner decisions.** One only, and it belongs to JI-1: whether the analyser is willing to declare a size
threshold above which an input's identity is recorded as unknown. That is a trust-surface choice about
what the product will assert, not an implementation detail, and it is the kind of call the standing
decisions say should be made deliberately. Everything else on this list is a defect with an obvious
correction, and I have not reopened any settled design choice.
