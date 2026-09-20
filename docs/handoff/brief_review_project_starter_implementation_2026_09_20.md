# Independent review — project starter journey implementation

2026-09-20. Review the implemented slices across the analyser, playground and local starter.
This is an implementation review, not another review of the agreed design and not release approval.
The full journey is unfinished. Verify claims against code and independent reproductions, not the
number of green tests or the author's account. Return an actionable handoff for the author.

## Start safely and freeze the reviewed revisions

Use separate reviewer worktrees. Do not switch, clean or reset an owner's checkout, the author's
implementation worktrees, or the staged application/project. Fetch the implementation branches,
record exact HEADs, and review the pinned scope below. Later author pushes do not silently extend it.
Use a distinct review branch in the analyser for the report; leave production branches untouched.

| Work | Implementation branch | Review range |
|---|---|---|
| Analyser | feat/project-starter-journey | `3ed21ea..e2ada11` |
| Playground | feat/project-starter-journey | `affdd85..47b9952` |
| Compiler/starter | feat/project-starter-journey in the owner's authorised checkout | The reviewed Spring baseline through the neutral-comment implementation; exact private revisions are supplied in the starting prompt and recorded in its tracker |

The commit adding this brief is documentation-only. Include it for instructions, but use the ranges
above for the substantive review. Record current upstream divergence read-only; do not merge/rebase as
part of this review. If a repository is unavailable, continue the independent work and report that gap.
Do not substitute an old staged jar for the newly built starter.

The analyser is public. Keep private compiler implementation paths, source excerpts and internal
revision details in a companion review in that repository if needed. The public handoff can record
artifact-facing outcomes and finding IDs; public artifact/resource digests are appropriate evidence.
Never include keys, customer data or machine-specific credentials.

## Reading order

1. `docs/ONBOARDING.md`, `CLAUDE.md` and `docs/handoff/REVIEWER-ORIENTATION.md`. Use their handoff
   protocol and public-content rules. Some orientation prose predates this work: the actual display
   job now runs real frames, and installed local generation providers exist. Judge current source.
2. `docs/specs/spec-project-starter-journey.md`, `spec-template-from-analyser.md` and the imitation/
   example requirements in `spec-onboarding-example.md` (all under docs/specs).
3. `docs/handoff/rereview_final_project_starter_journey_2026_09_20.md`, then
   `docs/handoff/handoff_project_starter_implementation_2026_09_20.md` **through its final checkpoint**.
4. Current entries in `docs/specs/tracker.md`; playground
   `docs/implementation/fluxtion-web/current-status.md`; the compiler checkout's AGENTS instructions,
   Spring-authoring briefing and active tracker, in their prescribed order.
5. The diffs, source, tests and evidence packets below. Earlier checkpoint statements are historical;
   flag any live tracker, user guide or changelog that incorrectly represents current completion.

## What to attack

### A. Saved facts, landing and session recovery

- Saved chart/processor declarations must remain visible before any log is loaded, separately from
  live tabs and observed output. Invalid profile metadata must refuse before replacing current state.
  Check profile import/export and project switching, not only a generated JSON map.
- The Project panel stays reveal-only; StartPanel/menu actions use existing controllers. New context
  facts must agree with the human surface and user-guide descriptions.
- The session graph must own recovery decisions. Check the model, generated processor copies and
  GraphML agree; adapters do I/O/rendering and must not invent a second state machine.
- Project close/reopen and quit/relaunch offer explicit Restore/Dismiss. Neither startup path may
  implicitly load remembered evidence. A command-line log is explicit input, not permission to restore
  other previous-project state. Test actual process quit/relaunch in an isolated home if possible;
  the author's existing restart witness creates another MainFrame, not another OS process.
- Attack canonical profile isolation, no-project state, failed activation, queued capture/exit draining,
  repeated accept/dismiss, and stale callbacks after another open/close/project switch. Check the
  `open {restore: last|dismiss}` exclusivity rule and actual completion versus a pending plan.
- Change a log before close, after capture, during verification/read, and without changing size/mtime.
  Remove one rolled member. The complete ordered log set must refuse while independent eligible
  design/results may reopen; old filters, row selection and topology cursor must not attach to new bytes.
- Inspect remote/growing inputs, graph-only recovery and mismatched graph/log identities. Missing
  identity remains unknown; file hashes do not establish build/run provenance. Check saved focus/view
  restoration, design/diagnostic failures and truthful partial outcomes.
- Two additional full streaming reads now accompany a local log load. Assess the performance impact
  against the index-first/large-log purpose; record timings on an appropriate disposable fixture if
  practical. No author benchmark is claimed. Do not infer this is negligible from unit tests.

Start at SessionResumeStore, ResumeEvents, SessionRecovery, SessionRecoveryController, MainFrame,
Main, OpenRequest, LogTablePanel, TopologyPanel and the recovery tests. Evidence:
`docs/handoff/evidence/project-session-recovery-2026-09-20/`.

### B. Full catalogue and project acquisition

- All entries remain available; onboarding tags only label recommendations. No tags must not cause
  a hosted-only fallback. Missing key metadata, explicit none, build and runtime requirements differ;
  recommendation/AOT mode cannot manufacture a key requirement.
- Bootstrap absent/empty/populated and malformed values must remain distinguishable. Check the actual
  dialog, scrollability and declarations against the current producer's output, not just a screenshot.
- Select an untagged entry, download/install/open it, and separately exercise legacy/profile-less
  discovery with nothing implicitly preselected. Opt-out must remain off. Existing archive refusal,
  cancellation, no-overwrite and copy-only command boundaries remain load-bearing.
- Author-new-project and the File menu use the same flow. Expanded picker deployment must follow
  default-on producer profiles. The public website has not been claimed updated by these branches.

Start at TemplateCatalogue, TemplateClient, TemplateProjectDialog, StartPanel and the archive tests.
Evidence: `project-template-catalogue-2026-09-20` and `project-starter-profile-import-2026-09-20`
under docs/handoff/evidence. The first is a real picker over branch metadata; the second invokes the
real scaffold handler and importer. Neither alone proves deployed network/UI acquisition.

### C. Default-on support, headless parity and runbook routing

- Legacy links with an absent setting migrate to on; explicit false survives. Absent HTTP override
  preserves a template's parsed choice. Invalid/repeated override and token-plus-override refuse;
  bundle plus false refuses. Exercise returned ZIP contents, not just schema booleans.
- Every generated path has one owner; duplicates fail even when content is identical. Bundle files
  must not win by accidental ZIP map ordering. Project guidance survives opting out; analyser-specific
  bootstrap/profile does not. Skills-none retains ordinary bundle task guidance.
- Check all catalogue profiles with the real analyser importer. Identifiers, descriptions, relative
  paths and processor intent must survive. Runtime processors must not acquire invented generated FQCNs.
- Follow PROJECT.md and each applicable runbook against actual emitted files and resolved dependencies.
  In particular, compare standalone Spring, hosted Spring and bundle routes: a Spring label does not
  prove setup/validate/reconcile scripts or extended declarations are present. Flag gaps precisely.
- Mongoose guidance must distinguish feed ordering, restart/reset, pre-start cached dispatch and replay,
  audit capture/export, retention and per-processor recording. Use the project's resolved version as
  the oracle. No analyser execution engine, guide verb, invented script or unapproved runtime default.
- Inspect the nearest shipped example/harness as something a new client will imitate. Good prose alone
  does not close an example that teaches the wrong shape.

### D. Canonical comments, ownership and publication

- Java must read the packaged canonical resource. Browser wording must come from the extracted resource,
  with the starterCore coordinate, artifact/resource digests and truthful provenance. Inspect emitted
  text directly; comments being ignored by ownership hashes proves nothing about their truth.
- Test DATA→TRIGGER→DATA, developer implementation and repeat no-op. Comments must remain choice-neutral
  and code must compile. No body rewrite, re-parenting or default change may be hidden as guidance.
- Verify all claimed construct coverage, both host shapes, and generated resource/manifest bytes.
  Test a missing resource, changed vendored bytes, wrong version and a repacked local artifact.
- Public parity must resolve the same authoritative Repsy coordinate as setup.sh. Enforce whole-jar
  digests only for immutable releases; resource digest is the content oracle. Local development must
  never pass the public gate. A live documentation URL does not prove version compatibility.
- Review the new smoke-workflow gate: it is intentionally unsatisfied while the artifact is unpublished.
  The author's public request returned 404. Report today's result, never turn that into a passing check.
- Repeat a wording mutation with the new jar. **Known harness seam:** the retained mutation helper
  expects a failure labelled `browser reference`; the final test also checks `browser handler` earlier.
  Inspect the actual failing assertion and whether the helper misclassifies a valid red. Do not equate
  a nonzero helper exit with successful mutation detection. Report reproduction-harness drift explicitly.

Evidence: `docs/handoff/evidence/project-starter-comment-contract-2026-09-20/`. Its resource manifest
is local-development, and neither public publication nor the full acceptance-11 diagnostic work is closed.

### E. Evidence and claims

Check manifests and read visible screenshot text. Distinguish participant testimony, source reading,
synthetic probes, actual display observations and independent reruns. The coldstart scorer is preserved
as a proposal with known defects, not an accepted oracle. Do not start a fresh paid session or reuse the
exposed participant solution as held-out evidence. Feedback 41–43 is not generally fixed: ordinary log
switching/pin disclosure, series lifecycle, axis fitting, colon targeting and nullable schema remain open.
Review the narrower recovery caveats without broadening their claimed coverage.

## Commands — run them yourself

Use Java 21 and separate review worktrees. Set these variables to the **review copies**, not the author
worktrees: JOURNEY_ANALYSER, JOURNEY_WEB (repository root) and JOURNEY_COMPILER (authorised repository
root). Set JOURNEY_FIXTURES to a fresh empty disposable output directory. Install dependencies normally
if offline caches are insufficient; do not use a paid compilation key. Do not run a general smoke command
that may use a configured key. The focused starter tests use local providers/loopback fixtures.

```sh
# Analyser review worktree
cd "$JOURNEY_ANALYSER"
git diff 3ed21ea..e2ada11 --stat
mvn -q clean package
mvn -q test -Dtest='PairingDuringLoadFrameTest,AsyncOpenInterleavingFrameTest,SpotlightFrameTest,WestColumnStartsCollapsedFrameTest,MenuScreenshotFrameTest,PersonAtTheScreenFrameTest,NamedGraphAndMenuSpotlightFrameTest,SessionRecoveryFrameTest,TemplateCatalogueFrameTest' -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false
python3 -m mkdocs build --strict
python3 tools/test_tools.py
git diff --check

# Compiler review worktree; follow its AGENTS first.
cd "$JOURNEY_COMPILER"
./mvnw -q -pl fluxtion-starter-core -am '-Dtest=*Spring*Test,*Diagnostic*Test,SchemaConformanceTest,ContractTest,ReconcilerTest,ExtendedDeclarationsTest,DeclarationBehaviorTest,StarterTest,BuildWorkflowTest,LocalGenerationTest' -Dsurefire.failIfNoSpecifiedTests=false clean package
python3 tools/spring-authoring/verify-jar.py
export FLUXTION_STARTER_TEST_JAR="$JOURNEY_COMPILER/fluxtion-starter-core/target/fluxtion-starter-core-1.0.72-SNAPSHOT-all.jar"
test -f "$FLUXTION_STARTER_TEST_JAR"

# Playground review worktree; install with pnpm install --frozen-lockfile if needed.
cd "$JOURNEY_WEB/web"
git diff affdd85..47b9952 --stat
pnpm vitest run
pnpm build
pnpm check
node scripts/verify-comment-contract.mjs --local-artifact "$FLUXTION_STARTER_TEST_JAR"
node scripts/verify-comment-contract.mjs
JOURNEY_FIXTURES_OUT="$JOURNEY_FIXTURES" pnpm vitest run src/lib/starter/project-support.test.ts

# Actual consumer over all handler-produced ZIPs.
cd "$JOURNEY_ANALYSER"
java --class-path target/classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar tools/VerifyStarterProfiles.java "$JOURNEY_FIXTURES" 14
```

Run the public verifier separately and retain its nonzero result; it must not prevent the remaining
local checks. Do not use --vendor or change pins to make review green. On Linux use xvfb-run for display
commands. Both headless properties matter; inspect Surefire XML for nonzero counts and zero skips in
the display run. Capture headless counts before display tests overwrite their reports.

Repeat the recovery mutation in a disposable copy with Java 21/display:

```sh
cd "$JOURNEY_ANALYSER"
python3 docs/handoff/evidence/project-session-recovery-2026-09-20/mutation.py "$JOURNEY_ANALYSER"
# Confirm the intended red, restored source, then rerun that test green.
mvn -q '-Dtest=SessionRecoveryFrameTest#fileEditedBeforeCloseCannotAcquireTheOldViewsIdentity' -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false test
```

Also repeat comment wording mutation (see D) and the invalid runbook-name consumer refusal on copied
fixtures. Restore mutations in finally and verify diffs before any commit. Never mutate the author's
worktree. The supplied helpers use /private/tmp logs; adapt log locations if the host lacks that directory.
Run the **exact tracked-file sweep in CLAUDE.md rule 1**, then check every new/untracked report/evidence
file too. Do not copy the four terms into another document. Inspect screenshots; the text sweep cannot.

Author-observed checkpoints, **not expected-result substitutions**: analyser 1,710 tests with 37
headless display skips; recovery/lifecycle display 37 passing plus the separately run catalogue display
case; compiler 84 + 47 passing; playground 504 passing with its freshly built local jar; type check four
existing SplitPane errors and six warnings. Record differences and causes, not just “green”. Production
build and strict docs passed. Public artifact resolution did not.

## Required handoff and push

Create a uniquely named branch such as `review/project-starter-journey-2026-09-20-<reviewer>` in your
analyser review worktree. Write:

`docs/handoff/review_project_starter_implementation_2026_09_20_<reviewer>.md`

The report must include:

1. Exact reviewed heads/bases and any upstream movement. Per-area A–E and per-repository verdicts:
   READY / READY WITH FOLLOW-UPS / NOT READY / NOT VERIFIED. Give a separate full-journey/release
   verdict: accepted partial implementation is not completion of the unfinished spec.
2. Numbered findings `JI-1`, `JI-2`, ... with severity, owner, public file:line (private source details
   stay in the private companion), reproduction/prediction, observed versus required behaviour, impact,
   proposed correction and a regression acceptance check. Do not implement the correction.
3. Verified / read only / not verified for each material claim; commands, counts, artifact digests,
   display observations and exact red/green witnesses. Name inaccessible prerequisites and failed gates.
4. Separate newly found regressions, pre-existing defects, promised-but-unimplemented scope, and already
   declared release gates. Deferred work can still block merge or release: make that judgement explicit.
5. An author handoff ordered by what to fix next, with blocking findings, follow-ups, cross-repo
   dependencies, outstanding experiments and any genuine owner decision. Do not reopen settled owner
   choices merely because another design is possible. No review may close an unperformed fresh-client,
   process-restart, browser or publication gate by reading source alone.

Commit and push the review report/evidence on your review branch. If private compiler findings need
source-level detail, commit a companion under its Spring-authoring design folder on a separate review
branch and return both branch/commit/report references to the owner. Do not edit existing reviews,
implementation/spec/tracker/skill files, amend, rebase, merge, force-push, publish, use the owner's key,
or send messages to other people. This review-specific no-fix rule overrides generic “fix the spec”
wording in the handoff protocol: report the mismatch and hand it back to the author.
