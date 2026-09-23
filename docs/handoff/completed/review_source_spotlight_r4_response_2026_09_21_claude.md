# Review: source spotlight, round-four response — 2026-09-21 (reviewer: claude)

**Verdict: READY FOR HANDOFF.** No blockers. Two findings are worth settling in the proposal before
implementation starts: S-1 (where the fresh reread runs) and S-2 (a second cache the reread does not
reach). One optional design note follows.

**Pinned revisions:**

- proposal branch `docs/source-spotlight-proposal` at `2b17209d`, based on main `401da35b` (v1.17.0);
- all source claims read at `401da35b`.

**A correction to my round-four review.** I cited the viewer lookup as `SourceService.read (:69–70)`.
The method is `sourceForFqn` (`:68`), as the response says. The behaviour I described is unaffected:
roots first, then the sources jars.

---

## Round-four findings

| Finding | Status | Evidence |
|---|---|---|
| **U-1** one lookup, and a reread route that reaches it | **Closed.** | See below. |
| **L-1** misspelt filename | **Closed.** `docs/proposals/source-spotlight.md`; no path containing `sorce` is tracked on the branch. | `git ls-tree` |
| **L-2** packet committed together | **Closed.** The proposal plus eight review/response/brief files are in one commit, based on current main, and 0 of the local Markdown links in those 9 files are dead. The tracker entry (`tracker.md:50–54`) records proposal/review status with no stale implementation claims. | checked with a link script over `git archive` of `2b17209d` |
| **L-3** changelog for design scroll remeasurement | **Closed.** The implementation must add an entry for Java targets **and** for viewport-driven design remeasurement/extinguishing (proposal: Implementation boundaries). | read |

**U-1 in detail.** The proposal now names `SourceService → SourceRootResolver → MavenSourceResolver`,
because that is what the Java viewer renders ("One lookup", proposal `:134–140`). It:

- keeps `source {fqn}` → `DesignWorkspace` / `DesignFiles` unchanged, with its authorised-roots-only
  search, duplicate refusal and no jars;
- discloses `lookup: source-viewer`, `selectionPolicy: first-match` and the chosen origin on both
  surfaces;
- states that the two routes may legitimately disagree, and how the assistant must treat that.

The public reread route is now the spotlight request itself. It calls a **proposed**
`SourceService.freshDocumentForSpotlight(fqn)`, which is explicitly labelled as new work rather than an
existing API. Acceptance 7 exercises that real entrance for every case I raised:

- jar-only: absent, then added to a known jar, then the same request repeated;
- the negative-cache mutation;
- a replaced hit;
- a new jar after discovery, then resolver recreation;
- root order selecting between two copies;
- the source verb's refusals kept as they are.

Nothing a helper-only test could satisfy remains. The two routes behave exactly as the response states;
checked at `401da35b`:

- `DesignFiles:50` refuses a duplicate across roots, and `DesignFiles:72–84` is its FQN lookup;
- `SourceService:68–70` is `sourceForFqn`;
- `SourceRootResolver:58–60` takes the first root that matches;
- `MavenSourceResolver:32` caches hits and misses.

Prior decisions are preserved and not reopened:

- Java clips and design refuses;
- node inference stays deferred;
- both CI frame lists are required;
- design settling is credited to `revealLine`.

---

## Findings

### S-1 · Should fix · the fresh reread runs on the EDT, and the proposal does not say so

`ActionExecutor.render` runs the whole verb on the EDT: `case "spotlight" -> … onEdt(() ->
doSpotlight(params))` (`ActionExecutor:146–150`). The proposed preparation stage runs inside
`doSpotlight`, between `precheck` (`MainFrame:2478`) and `resolveAll` (`:2482`). So
`freshDocumentForSpotlight` would do file and archive I/O on the EDT on every Java spotlight request.

The new invalidation makes this worse than today's cached lookups:

- Clearing the **negative** entry means every request for a missing or jar-only FQN runs `lookup()`
  again, which opens discovered jars in turn until one matches.
- On a resolver whose jar list has not been warmed, the first request also pays the discovery
  filesystem walk. `MavenSourceResolver.warm()` is documented as "call off-EDT" (`:46`) for exactly this
  reason.

**Failure scenario:** sources-jar lookup is enabled over a large local repository, and the FQN is
missing. Every repeat of the spotlight request blocks the UI for a full jar scan, while the person is
watching the canvas the spotlight is meant to point at.

**Required.** State where the fresh read runs. The proposal already has the right machinery: preparation
returns an immutable plan, and "a superseded plan is refused, not applied". So the read can run **off the
EDT**, before the frame's apply step, with the existing supersession check made on the EDT. Add one
acceptance clause for this, for example: an instrumented or slow archive read is shown not to run on the
EDT. If the author prefers to keep it on the EDT, say so and bound the cost. Leaving it implicit repeats
the same class of gap as U-1.

### S-2 · Low · the reread does not reach `SourceService`'s other cache

`SourceService` also caches the parsed model of the selected processor: `selectedModel` (`:24`,
populated at `:59–64`, cleared only by `configure` (`:35`) and `select` (`:54`)). It backs
`fqnForInstance`, which the source glance uses to go from a bean to an FQN (`MainFrame:5115`).

Scenario: a Java spotlight rereads the **selected processor's** FQN and gets a new revision. The prepared
snapshot is rendered in the EventProcessor pane, but `selectedModel` still describes the old text. The
field types it reports can then disagree with the source on screen.

**Required.** Say whether `freshDocumentForSpotlight` invalidates `selectedModel` when it rereads the
selected processor's FQN, or state that this is out of scope, with the consequence. One sentence either
way.

---

## Design note (optional, not a finding)

The `javaSpotlightBindings` registry has a precise lifetime:

- bind only after the whole set succeeds;
- remove on clear, replace, click, Escape, refresh, configuration change and pane disposal;
- never resurrect a removed binding;
- reconcile with the surviving lit targets.

That is the shape `SessionProcessor` already handles well (`session/node/*`, decisions as nodes, effects
as records, `SessionDriver` as the thin Swing adapter), and 20 test files already drive that processor
directly. Building the registry as processor nodes, with viewport and refresh notifications as events,
would make acceptances 4 and 6 headless unit tests rather than frame tests. Only the geometry would stay
on the Xvfb job. This is offered as an option for the implementer, not as a condition of handoff.

---

## Verified versus read

**Run:**

- `git fetch`;
- confirmed the branch is based on `origin/main` `401da35b` and consists of the one commit `2b17209d`;
- a link check over the 9 packet files from `git archive` (0 dead links);
- confirmed no tracked path containing `sorce`.

**Read at `401da35b`, not executed:**

- `DesignFiles` (`:50`, `:72–84`), `DesignWorkspace.source`;
- `SourceService` (`:24–70`), `SourceRootResolver` (`:58–60`), `MavenSourceResolver` (`:32`, `:40–57`);
- `ActionExecutor.render` (`:112–150`);
- `MainFrame` (`:2460–2482`, `:5115`).

**Not done:**

- The author's packet validation was not re-run (1,767 tests, 0 failures/errors, 49 skips, strict docs);
  it is a repository-consistency check and proves nothing about the unimplemented feature.
- No UI run, no implementation, no edits to the proposal or other reviews, no merge or release.
