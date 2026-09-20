# Project starter journey — implementation

Owner authorized implementation, isolated branches, commits and pushes on 2026-09-20.
Read ONBOARDING, the two journey/template specs and the final review/response before continuing.
The specs are the contract; this file records execution evidence, not a second design.

## Scope and sequence

1. Preserve and push the reviewed specs, responses, feedback evidence and local rehearsal tooling.
2. Create `feat/project-starter-journey` in isolated analyser, playground and compiler worktrees.
   Integrate current upstream where needed without modifying the owner's other worktrees.
3. Playground: default-on support and headless overrides; unique file ownership; project-specific
   profile/bootstrap/runbooks; all-catalogue fixture checks; empty-directory instructions.
4. Analyser: saved-definition context, project landing, explicit project-scoped restore including
   relaunch migration, full catalogue/disclosures and optional authoring entry using the same controller.
5. Compiler/playground: canonical neutral comment resource and parity; selected actionable diagnostics;
   version-compatible references. Vendor journey depends on classpath-safe dependency handling.
6. Run targeted regressions, full owning-repo gates and isolated UI/download/restore acceptance.
   Run fresh-client/held-out experiments with predictions and independent result checks, never present
   an assisted or locally provisioned run as public-download acceptance.

## Boundaries

- Analyser renders facts and stores pointers; external tools/runbooks execute applications.
- No silent public release, owner key use, or changes to the running staged project.
- Preserve IDE edits and existing worktrees. New public artifacts must pass the named publication gate;
  local artifacts can validate development but cannot close that gate.
- Update this evidence record and owning trackers with each completed implementation slice.

## Initial state

Analyser main `703136e` equals fetched origin/main. Existing unrelated IDE edits remain local.
Playground reviewed Spring head `affdd85`; current origin/main `5d0505a`.
Compiler reviewed Spring head `fdd24d61`; current origin/develop `29ea9aba`.
These are observations, not assertions that upstream has already been integrated.

Pre-commit checks: MkDocs strict passes; launcher ownership tests 3/3 pass with process-inspection
permission. The sandboxed full Java run could not bind test sockets; permitted rerun passes:
1,688 tests, zero failures/errors, 31 display skips. Exact rule-1 sweep and staged secret-pattern check
are clean. Staged whitespace checking flags original logs/generated source in immutable evidence
snapshots; retain those bytes and their manifests. Authored changes outside evidence pass whitespace checks.
No implementation slice is marked complete yet.


## Checkpoint — first implementation slice and feedback intake

Specs/evidence base `3ed21ea` was pushed to analyser main. Isolated implementation worktrees now exist at
`/private/tmp/fluxtion-journey-{analyser,web,compiler}`, each on `feat/project-starter-journey`.
Playground starts at reviewed `affdd85` (already includes fetched main); compiler starts at `fdd24d61`
and has normally merged develop `29ea9aba` (documentation changes). Original checkouts are preserved.

Analyser saved definitions now appear through `context.savedGraphs` and the reveal-only Project panel,
separately from live tabs. Gate: **1,691 tests, zero failures/errors, 31 display skips**; MkDocs strict,
exact rule-1 sweep and authored diff whitespace check pass. The unchanged panel structural gate passes.
This is not yet the landing/restore implementation; no display interaction is claimed at this checkpoint.

The first permitted gate exposed an initial-commit blind spot: the tracked-only whitespace test had not
seen previously untracked immutable source snapshots. Its new explicit exemptions name the five captured
generator outputs with pinned manifests; their original bytes remain untouched. This is a test-fixture
classification correction, not a rewrite of evidence. The final suite includes those newly tracked files.

Cold-start participant proposal is preserved verbatim with review/probes. The scorer is not an acceptance
oracle: invalid chronology and self-baselining still pass, and static annotation detection still has a
comment false positive. The spec adopts attribution/imitation/isolation inputs with these qualifications.
Chart feedback 41–43 is separately preserved and source-checked; the actual axis/window contamination
and colon-target refusal were reproduced in a headless probe. The ninth feedback-review addendum records
UI removal and pin-echo qualifications. No chart correction is claimed by this checkpoint.

Playground initial support/API/profile/runbook slice passes 496 tests (four existing local-artifact cases
skipped), production build, and a subsequent focused 20-test run including absent-override preservation.
Type check still reports its four pre-existing SplitPane errors. Runtime runbook verification, shared
processor metadata consumption, pinned-reference/comment resource, browser witness and held-out journey
remain pending; this branch is not a release candidate yet.


## Checkpoint — project landing and declaration consumption

Analyser first slice/evidence pushed as `7044e9f`; playground initial support pushed as `0e7bd58`.
The analyser now consumes/exports version-1 `processorDeclaration` entries with the event-processor
category, snapshots them across project boundaries and keeps own settings separate from active profiles.
Malformed profile metadata refuses before replacing state. `context.processorDeclarations`, Project panel
and StartPanel landing share those facts. The landing offers explicit open actions; it adds no executor.

Gate: **1,695 tests, zero failures/errors, 31 display skips**, MkDocs strict and authored whitespace clean.
Headless panel construction verifies display text and explicit callbacks; this is not a browser/desktop
witness. The existing global startup restore is still present and the new project restore offer is not
implemented yet. This branch remains in progress. Profile format is pinned in the spec before further
producer/consumer changes. The next acceptance is isolated UI and per-project restore, including feedback
41's disjoint saved pin and independently active filters.


## Checkpoint — recovery storage and decision foundation

Landing/declaration consumption pushed as `d5fcc98`. Compiler tracker/upstream integration pushed as
`e54372f2`; compiler source remains unchanged. New recovery storage captures ordered file identities in
user-local per-canonical-profile files, writes atomically, and verifies SHA-256. A same-size rewrite with
the original mtime restored is detected; missing inputs and unknown capture identity stay explicit.
The session graph now owns the offer/accept/verification plan, rejects late completions from another
project, and refuses an incomplete rolled set as a unit. Snapshot identity concerns file bytes at capture,
not proof of build/execution identity. The model records that limitation.

The graph was regenerated through the installed local provider with the pinned released builder 1.0.71,
Java 21, isolated user.home and explicit local selection. Provider dependency jars came from the existing
local rehearsal classpath; no owner key or HTTP generation was used. Generated attribution stripping is
unchanged, and both emitted source copies pass the publication guard. Full regression: **1,700 tests,
zero failures/errors, 31 display skips**. Seven focused store/model/publication tests passed first.
This foundation is not wired to the UI/MCP or lifecycle yet; implicit startup restoration remains until
the complete adapter/offer migration. Next work must not mistake a verified plan for completed opens.

## Checkpoint — explicit recovery adapter and startup migration

The UI and MCP now accept or dismiss one per-project offer. Project close captures before clearing;
shutdown drains queued capture writes before exit. Main opens only a command-line log, and the former
remembered-GraphML entrance delegates to the same offer. Failed project activation cannot fall into the
no-project bucket. Recovery never executes application processing, scripts or runbooks.

Files are checked before opening; actual asynchronous reader completions produce the final outcome.
One missing/changed rolled member refuses the complete set. Independent topology/design/diagnostics
may still reopen, and their failures are named. Design reads are guarded against newer requests and
project/log transitions. Saved record filters, multiple selection and topology contexts/cursor/zoom
require the relevant identities; commentary and spotlight captions are not added to persistence.

A gap found during author self-review is covered explicitly: hashing only at close could attach an old
view to a newly edited file. Local log/rolled readers now observe SHA-256 before and after reading and
retain matching identities. Recovery compares those observations with the saved snapshot and the new
read before attaching positions. This adds two streaming file reads to a normal local log load; no large-log
latency benchmark has been run. Growing or remote inputs without matching observations can reopen,
but their saved record bindings are withheld. This is a file-content check, not build/execution certification.

Verified: **37 display tests, zero failures/errors/skips**, including five real-frame recovery cases:
close/reopen, changed-log partial restoration, same-home restart offer/dismiss, pre-close edit refusal
for saved bindings, and a missing rolled member. Removing the loaded-view hash comparison makes the
pre-close edit case fail at its withheld-view assertion; source restored, then the display gate rerun.
The first mutation runner misclassified that genuine failure because it searched for text absent from
JUnit's failure message; its corrected assertion checks the precise test line. Both outcomes are recorded.

[Evidence packet](evidence/project-session-recovery-2026-09-20/README.md): actual frame captures and
context before/after acceptance. Images were inspected and exposed an alignment/scroll defect in the
landing, which was corrected and recaptured. The witness uses synthetic fixtures and an isolated home;
no owner key, live project, or application execution. Restart constructs another real MainFrame with
persisted state after draining capture, not an OS process launch. Main's CLI/implicit-open change is
source-verified. The display CI job now includes this suite and rejects skips.

The preceding full headless gate passed 1,705 tests (35 display skips); a protocol-isolation test and a
rolled-set frame case were then added. Final clean headless/package and strict-doc counts follow below.
Feedback 41–43 remains open outside this slice: recovery reports disjoint pins/filter counts, but ordinary
log switching still needs the chart-level scope disclosure; series replacement/removal, axis fitting,
colon-name addressing and the nullable pin schema are not claimed fixed.

Remaining at this recovery checkpoint: catalogue metadata/picker, producer comment resource/parity and diagnostics,
capability runbook validation, hardened coldstart scoring and an uncontaminated held-out journey.
Publication remains its separate existing release gate. This is an implementation checkpoint, not a
claim that the entire reviewed journey or its independent review is complete.

Final checkpoint gates: `mvn -q -o clean package` — **1,707 tests, zero failures/errors, 36 display
skips** (the five recovery cases join the previous skipped frame tests). The separate 37-test display
gate ran all its cases with zero skips. MkDocs strict passed. Exact tracked-file rule-1 sweep passed;
staged whitespace is clean after normalising trailing whitespace in the captured JUnit log, with its
original digest recorded. The temporary mutation is absent from the staged source.

## Checkpoint — full catalogue and declared prerequisites

The analyser journey branch now lists every catalogue entry, labels recommendations without filtering,
and renders build/regeneration requirements plus absent/empty/populated agent entry declarations.
Missing key metadata stays “not declared”; neither AOT mode nor recommendation supplies an inferred
requirement. Malformed bootstrap declarations refuse instead of disappearing. The generic welcome
adds an Author a new project action using the existing File-menu controller. Download/archive policy
and legacy discovery remain unchanged; the optional fallback guide still supplies pointers only.

[Display evidence](evidence/project-template-catalogue-2026-09-20/README.md) retains the exact 14-entry
producer catalogue and inspected screenshot. One real-modal test passes with no skips; it selects every
entry and verifies its disclosure, then closes without starting a download. CI includes the test in its
display job. Parser tests cover mixed/no tags and absent/empty/declared metadata. Live tracker rows have
been reconciled with the completed recovery slices; historical checkpoint results remain dated evidence.

Full `mvn -q -o test`: 1,710 tests, zero failures/errors, 37 display skips. Strict MkDocs, the
tracked rule-1 sweep and whitespace checks pass. The first full run found one old structural test
looking for `entry.keyNeed()` in the dialog; it now checks the catalogue-owned disclosure call, with
behavioural tests proving declared key requirements stay independent of mode/tags. The execution
prohibitions in that structural test remain intact. Untagged end-to-end download/open,
independent review and producer-first deployment remain open. This is branch implementation, not a
claim that the public website or released analyser has changed.

## Checkpoint — producer routing and actual profile imports

Playground `0b5660a` follows `0e7bd58`: both bundle agent entries point to PROJECT.md and the bundle
profile registers the common project task runbooks beside its vendored skills. Each path retains one
emitter. Skills-none removes vendored skills only, preserving task guidance. The expanded producer
check exposed an earlier mistake: space-bearing runbook names were rejected by the analyser. Names
now follow the actual identifier grammar across all templates; bundle profiles also declare processor
metadata version 1.

[Cross-repo evidence](evidence/project-starter-profile-import-2026-09-20/README.md): 14 real scaffold
HTTP-handler ZIP responses read by ProjectProfile.load, no rejected/dropped declarations, every
runbook/source pointer resolves. The retained negative witness restores the former spaced name and
gets an explicit refusal. The helper is read-only; no app, local host or paid generator ran.

Producer gates: 501/501 full tests, no skips, with the provisioned local starter jar; production build
passes. Type check reports only the four existing SplitPane errors (and six warnings). Sandbox-denied
loopback/fetch attempts were rerun with permissions and passed. This closes bundle entry routing and
profile interoperability, not interactive public acquisition, runtime runbook validation, comment
resource/parity, vendor prerequisites or the isolated fresh-client acceptance.

## Checkpoint — neutral comments and artifact parity

Java now packages and reads the canonical comment resource; playground `47b9952` vendors bytes from the
built jar, annotates only freshly generated Spring nodes, and ships resource/provenance with Spring
downloads. The comment text describes constructs without fixing the initial propagation/lifecycle choice.
Existing developer prose/bodies and ownership hashing are unchanged. The public verifier uses the setup
script's Repsy endpoint, checks resource bytes/digest, and additionally checks immutable released jar digests.
It reuses the playground artifact fetch helper; no private source checkout is part of that verification.

[Evidence and reproduction](evidence/project-starter-comment-contract-2026-09-20/README.md): clean Java
84 + 47 tests; jar check; real browser/Java emitted-text parity; DATA→TRIGGER→DATA compilation and no-op;
wording mutation red with ownership hashes unchanged, restored green; playground 504/504 and production
build. Final focused gate 23 tests and type check only the four known SplitPane errors.

Publication remains OPEN: the actual public artifact request returned HTTP 404, so the committed manifest
says local-development. The new CI public parity step must fail until publication/public re-vendoring.
Do not mark acceptance 11 fully closed: selected diagnostic cases and public verification are outstanding.
The resource pin does not by itself establish compatibility of every live documentation reference.
Next implementation work remains diagnostics, vendor safeguards, resolved-runtime runbooks and isolated
browser/fresh-client acceptance; none has silently become a release claim.


## Implementation review response — 2026-09-20

The independent [implementation review](review_project_starter_implementation_2026_09_20_claude.md)
found two analyser merge blockers and four follow-ups; producer work was accepted within its stated
scope. The [author response](response_project_starter_implementation_2026_09_20.md) records all six
corrections and the precise evidence and limitations. Owner chose full verification with reader
optimisation, not a size threshold. Native ordinary opens hash during indexing; opaque plugins retain
before/after checks, and restore retains a post-index whole-set check. Clean package: 1,718/0/0 with
40 headless skips; display: 41/0/0/0. Three recovery mutations failed their intended assertions, then
restored source passed. The comment witness is refreshed against the current assertion. Re-review is
pending; this checkpoint does not replace the independent verdict or close any full-journey gate.
