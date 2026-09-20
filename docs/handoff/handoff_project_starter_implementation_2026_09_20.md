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
