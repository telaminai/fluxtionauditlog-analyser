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
