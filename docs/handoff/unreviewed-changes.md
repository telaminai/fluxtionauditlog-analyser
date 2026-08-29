# Un-reviewed changes on `main` — pending review

A running ledger of changes **committed directly to `main` without** the usual brief → report → review
cycle. These are small, ad-hoc fixes made by a session working primarily in **another repo** (a downstream
consumer of the analyser) that hit an analyser-side bug and fixed it in passing, rather than a delegated
work block.

**For the reviewing session:** on your next pull, review each `☐` entry below — read the commit, sanity
the change against the codebase and the repo rules (CLAUDE.md), run `mvn test`, and **verify anything the
entry says was not verified** (Swing UI changes are not unit-tested — build and run the jar). Then tick it
`☑ reviewed <date>` with a one-line verdict, and file any follow-up as a normal review. Fully-reviewed
entries move to `completed/` when this file is next tidied.

Every entry must carry: commit SHA, what & why, files, what was verified, and **what the reviewer must
still check**.

---

_Reviewed entries are archived verbatim in
[`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._

## ☐ `3720ef9` + `4860513` · correct the M19 profile ABI and add the P3 static bundle preflight

**What.** `m19-bundle/3` replaces v2's unusable profile table with the real zero-based ConfigStore list
families, selected processor and explicit share version; a `ProjectProfile.load` test pins the exact
generator-facing file. `tools/bench/bundle-bench.py` then checks a generated directory or zip for that
profile ABI, contract/guide mirror, safe inventory, committed source, declared/discoverable GraphML,
runbook/frontmatter/provenance/version parity, executable commands, placeholders, developer paths and
literal key material. Eight deterministic fixtures run in CI.

**Why.** P3 scaffolding caught that P1 emitted `sourceRoot.1`, one-based runbooks and singular
`eventProcessorFqn`. The analyser iterates list members from zero and recognises processors only through
`eventProcessorFqn.count`/members plus `selectedEventProcessor`; the apparently complete v2 profile
therefore loaded none of those facts. No v2 bundle was published or accepted.

**Files.** M19 spec/tracker and cross-repo handoff/review; `ProjectProfileTest`; `bundle-bench.py` and its
Python tests; bench/tool documentation; CI workflow. The exact v3 handoff commit is `a3769bd`.

**Verified.** Profile/spec-link focused tests pass; full Maven suite passes 1,110/1,110; bundle fixtures
pass 8/8; packaged stub/analyser/MCP loop passes 23/23; pinned strict-site build, Python compile, diff
check and tracked-file four-term sweep pass. This is intentionally static P3 scaffolding, not a live
generated-bundle verdict.

**What the reviewer must still check.** Compare every v3 key/index directly with `ConfigStore` and
`SettingsShare`, including project-name derivation. Challenge the checker for false passes/failures,
especially zip path/mode handling, Java-properties/frontmatter parsing, `none` versus mirror provenance,
GraphML discovery depth and secret/developer-path heuristics. Run it against the playground's actual
canonical, `none` and non-canonical generated fixtures when available; confirm it rejects the old P1
profile. Inspect the post-push CI run. The real keyless run/export/stop plus fresh analyser/MCP path
remains a separate P3 gate.

## ☐ `297c4c1` · atomically publish the REST endpoint found by the Linux loop

**What.** `RestEndpointFile` now writes complete JSON to an owner-only sibling and atomically replaces
the well-known endpoint, with a same-filesystem replace fallback. The loop bench treats absent,
malformed or incomplete endpoint data as "not ready yet" rather than escaping with a JSON traceback.

**Why.** GitHub run `33273004452` passed all registry/export steps, then read the endpoint between its
creation and JSON write and failed with `JSONDecodeError` after ten passes. The previous implementation
explicitly deleted, created and then wrote the public token file, so this was a product publication race,
not merely a slow runner.

**Files.** `RestEndpointFile`, `RestEndpointFileTest`, `loop-bench.py`, changelog. Exact tracker evidence
is in the following metadata commit.

**Verified.** Focused endpoint/discovery tests pass outside the loopback socket sandbox; full Maven suite
passes 1,109/1,109; packaged `tools/bench/loop-bench.py --stub --launch` passes 23/23; pinned MkDocs
strict, `git diff --check` and the tracked-file four-term sweep pass. After push, GitHub run
`33273629437` passed both the build and Linux/xvfb loop-bench jobs.

**What the reviewer must still check.** Challenge atomic replacement and the non-atomic filesystem
fallback, including owner-only permissions and cleanup of the sibling. Confirm the bench retry is bounded
by the existing deadline and does not hide a server that never becomes valid. Inspect run `33273629437`
for independent workflow-log verification.

## ☐ `389d331` · discover the generated bundle's Maven-resource GraphML

**What.** `NewProjectDiscovery` adds an existing `src/main/resources` directory to its already bounded
GraphML roots, without adding it as a Java source root or selecting the graph. A regression fixture puts
the graph at the exact package-shaped location the playground download injector uses.

**Why.** The accepted discovery review found that source-root-only scanning misses Maven-resource
GraphML. P0 fixed the generated bundle's concrete location at `src/main/resources/...`, turning the
review's conditional concern into a reproducible analyser-side miss.

**Files.** `NewProjectDiscovery`, `NewProjectDiscoveryTest`, changelog. Tracker/handoff evidence and the
P0 review are in the following metadata commit.

**Verified.** Focused discovery test passes; full Maven suite passes 1,108/1,108; pinned MkDocs strict,
`git diff --check` and the tracked-file four-term sweep pass.

**What the reviewer must still check.** Confirm the extra root remains bounded by `GraphmlDiscovery`
and changes only what is offered, never what is selected or persisted. P3 separately must generate a
real bundle and assert its emitted graph is in the offer; this fixture does not replace that check.

## ☐ `6243a89` · resolve the accepted M19 slice-review follow-ups

**What.** Moves named-profile validation inside the key store's wipe guard and pins the rejected-name
path with a buffer assertion; adds the missing clean-stop `TODO(bundle)` marker plus a canonical-skill
assertion; updates both CI jobs to checkout/setup-java v5. The paired response also makes the cross-repo
version gate explicit: local work may use the `1.0.39-SNAPSHOT` built from Mongoose Plugins `6e7a2cc`,
while downloadable/clean-machine acceptance waits for a published version containing it.

**Why.** These are F1 from `review_m19_key_slice.txt`, F1 from
`review_m19_skill_provenance_slice.txt`, and F3 hygiene from
`review_m19_ci_and_discovery_slices.txt`. The discovery review's GraphML finding cannot be solved by
guessing the generator's path, so P3 now has an explicit discover-the-generated-path assertion and a
defined return route if it fails.

**Files.** `FluxtionKeyStore`, its test, canonical Mongoose skill + parity test, CI workflow and
changelog. Exact tracker/handoff disposition and the response report follow in the metadata commit.

**Verified.** Focused key/skill tests pass; full Maven suite passes 1,107/1,107; pinned MkDocs strict
build, workflow YAML parse, `git diff --check` and the tracked-file four-term sweep pass. After push,
GitHub Actions run `33272924784` passed both the v5 build job and the xvfb loop-bench job.

**What the reviewer must still check.** Confirm every `saveProfileAndActivate` exit now wipes a non-null
buffer without masking the null-key refusal. Confirm all three project-owned operations (start, export,
clean stop) carry a substitution marker and that the generator consumes `6243a89`, not its earlier
snapshot. Inspect run `33272924784` if independent workflow-log verification is desired. For the
cross-repo side, distinguish a SHA-recorded local SNAPSHOT run from the eventual published-version clean
run.

## ☑ reviewed 2026-08-29 (the Mongoose/playground session — the generator side check #1 is addressed to) · `5c72e21` · M19 skill provenance and Chronicle-export skill correction

**Verdict.** Accepted — and check #1 is answered from the generator's side: the four emission modes
(`canonical@<sha|tag>`, `mirror:<clean https base>@<rev>`, `local@<rev>`, `none`) all fall inside the
accepted grammar, revisions are committed to `[A-Za-z0-9._-]` (slugified if not), and P3 gains a
conformance fixture asserting `skillsProvenance()` accepts each emitted string verbatim. The corrected
skills match the REAL server contract at every live-verified point (registry fields, export endpoint,
key precedence, registry removal on stop — the last true only since this afternoon's `stop()` fix).
Two findings: F1 mongoose skill step 5 (stop) lacks a `TODO(bundle)` marker so the no-marker gate
cannot force a real stop command; F2 two named upstream events the skills' claims wait on (playground
P0 keyless pom; a mongoose-plugins release containing the registry branch). Details:
[`review_m19_skill_provenance_slice.txt`](review_m19_skill_provenance_slice.txt).

**What.** Generated profiles may carry sanitized, value-free `skills.provenance`; context and the
Project panel show it as an inert project declaration. A project-supplied `skills.source` is ignored
with a visible refusal and stripped on the next profile save, while provenance is preserved. The
canonical Mongoose/load-log skills now say registry → Chronicle capture → bundle-owned YAML export →
open with GraphML, and a test pins all four skill documents and the embedded publication gate.

**Why.** The signed `m19-skills/1` contract separates build-time retrieval control from the portable
fact recording what was vendored. The live Mongoose reconnaissance also disproved the skills' remaining
deployment-descriptor/direct-YAML story.

**Files.** `ProjectProfile`, `MainFrame`, `ProjectModel`; three test classes; canonical Mongoose and
load-log skills; Projects guide, changelog and M19 tracker. The same commit records the successful Linux
M19.8 run in the earlier ledger entry.

**Verified.** Focused profile/model/parity/canonical-skill tests passed; full `mvn -q test`, pinned
`mkdocs build --strict`, diff check and leak sweep passed. GitHub Actions run `33271896191` had already
proved the separate M19.8 loop job on Linux/xvfb.

**What the reviewer must still check.** Challenge `skillsProvenance`'s accepted grammar against the
generator's exact emitted strings, especially mirror URLs and revision characters. Confirm an existing
profile containing both keys reports the refusal without failing to load, then loses only
`skills.source` after a real UI edit/save. Review the canonical skills with the playground generator:
`TODO(bundle)` remains intentional in source but must be substituted and refused in every shipped bundle.

## ☑ reviewed 2026-08-29 (the Mongoose/playground session) · `92ad3ba` · M19.8/9 Linux loop-bench CI and launch parsing tests

**Verdict.** Accepted — verified against the LIVE Actions run (33271896191), not the prose: all 23
bench steps PASS by name on the runner under xvfb, MCP bridge included, job done in 42s with clean
child teardown; `--mcp` short-circuits ahead of the desktop parse and `parseDesktopArgs` reproduces
the old strip/retain semantics exactly. One hygiene note: checkout@v4/setup-java@v4 carry deprecation
annotations — bump on the next workflow touch. Details:
[`review_m19_ci_and_discovery_slices.txt`](review_m19_ci_and_discovery_slices.txt).

**What.** Extracts desktop launch-argument stripping into a pure `Main.parseDesktopArgs` decision with
headless tests, and adds a Linux CI job that packages the analyser then runs the existing 23-step
stubbed registry/export/analyser/MCP loop under `xvfb-run`.

**Why.** The end-to-end bench covered `--rest` only on machines able to launch Swing and was not run by
CI. A typo could regress into a fake log path unnoticed, while the cross-repo registry/export contract
could rot between local runs.

**Files.** `.github/workflows/ci.yml`, `Main`, `MainLaunchArgsTest`, `tools/bench/README.md`, M19 tracker.

**Verified.** `MainLaunchArgsTest` passed; full `mvn -q test` passed; a freshly packaged local
`tools/bench/loop-bench.py --stub --launch` passed 23/23 outside the socket sandbox; pinned strict-site
build, workflow YAML parse, diff check and tracked-file four-term sweep passed. After push, GitHub Actions
run `33271896191` completed both `build` and the Linux `loop-bench` job successfully; the xvfb loop step
finished in ten seconds rather than reaching the timeout.

**What the reviewer must still check.** Confirm the separate build job should remain headless and that
caching/duplicate package cost is acceptable. In `Main`, verify MCP/help still short-circuit before
desktop parsing and that keeping additional positional arguments matches the pre-existing behaviour.

## ☑ reviewed 2026-08-29 (the Mongoose/playground session) · `1f30213` · M19.13 New project offers discovered setup

**Verdict.** Accepted — the safety claims all hold in code: Cancel returns BEFORE `project.create`
(no profile written), every control starts unselected (asserted structurally), apply() admits only
offered-AND-selected items with the refuse gates re-run and putIfAbsent for duplicates, and the
truncation cap is stated in the dialog. One medium follow-up (F1): GraphML is discovered only under
offered source roots, so a graph at the project root, in src/main/resources or under
target/generated-sources is missed — check against the bundle's declared GraphML path when the first
generated bundle exists. Nested modules (F2) accepted as the v1 one-level guess. Click-through and
restart-persistence stay on the human list. Details:
[`review_m19_ci_and_discovery_slices.txt`](review_m19_ci_and_discovery_slices.txt).

**What.** `File ▸ New project…` now composes the existing bounded source-root, skill and GraphML
discoveries into one confirmation. Every checkbox/radio begins off. Confirmed source roots and
skill-shaped runbook pointers persist to the new profile; at most one confirmed topology opens. An empty
directory yields an empty offer and can still create an empty project.

**Why.** M19's prepared bundle is day one. Without this slice, reproducing the setup on a user's own
project required four undocumented actions. The signed R7 rule is still D-AI5: discovery offers and a
person declares; the analyser never silently adopts repository content.

**Files.** `NewProjectDiscovery`, `NewProjectOfferDialog`, `MainFrame`, one headless test class, Projects
guide, changelog and M19 tracker.

**Verified.** `NewProjectDiscoveryTest` plus the existing project/session, skill and GraphML tests pass;
full `mvn -q test` passed; pinned `mkdocs build --strict`, `git diff --check` and tracked-file four-term
sweep passed.

**What the reviewer must still check.** Run the jar under an isolated home against (a) an empty directory
and (b) a small multi-module project with several skills/graphs. Confirm one dialog is readable at normal
and narrow sizes, nothing is preselected, Cancel writes no profile, empty-confirm creates a usable empty
profile, selected roots/skills survive restart, duplicate skill names do not replace earlier choices,
and selecting one topology opens only that graph. Challenge the one-level Maven source-root guess and
the decision to search GraphML under offered source roots rather than the whole repository.

## ☑ reviewed 2026-08-29 (the Mongoose/playground session) · `db42919` · M19.12/12a safe Fluxtion build-key management

**Verdict.** Accepted — the R8/D-X3 boundary holds at every reachable surface, verified live over REST
under an isolated home (presence flips without restart; a sentinel key value is absent from the full
context response; no first-run gate; 1,086 tests + mkdocs strict + four-term sweep green). One
low-severity follow-up: `saveProfileAndActivate` validates the profile name BEFORE the try/finally
that wipes the key, so an invalid name returns the caller's `char[]` unwiped — hoist the name check
inside the try. Both of the entry's code challenges are answered (that one; and
preserve-unrelated-properties is ENDORSED). Details, notes F2–F5 and the still-manual Swing
checklist: [`review_m19_key_slice.txt`](review_m19_key_slice.txt).

**What.** Adds the three signed-spec surfaces: a Start-page card, *AI ▸ Fluxtion API key…*, and a
Project-panel/context row. `FluxtionKeyStore` writes the established
`~/.fluxtion/fluxtion.apiKeyFile` format, preserves unrelated properties, applies owner-only POSIX
permissions where available, wipes caller buffers, and supports named profiles under
`~/.fluxtion/profiles/`. The masked dialog never reloads or validates a stored value. Public docs now
distinguish this processor-build key from the analyser's existing optional LLM-provider key.

**Why.** M19 R8/D-R1 made the analyser the owner of local key-file convenience but forbids it from
becoming a licence enforcer or a credential propagation path. The analyser can observe canonical-file
presence and document builder precedence; it cannot claim which source a future Maven JVM resolves.

**Files.** `FluxtionKeyStore`, `FluxtionKeyDialog`, `MainFrame`, `StartPanel`, `ProjectModel`; four
focused test changes; Getting started, Projects, FAQ, changelog and M19 tracker.

**Verified.** Full `mvn -q test` passed; focused key/model/menu/parity tests passed; pinned
`mkdocs build --strict` passed; `git diff --check` and the tracked-file four-term sweep passed.

**What the reviewer must still check.** Build and run the jar under an isolated `user.home`. Confirm
the Start-page card reflows and changes from absent to present after save; the dialog begins with an
empty masked field even when a key exists; named save/activate/delete works; *AI ▸ Fluxtion API key…*
opens the same owner; and the Project row states only presence plus the `-D`/environment rule. Inspect
the screen for paths or entered values before taking any screenshot. In code, challenge whether every
exception path wipes the temporary password array and whether preserving unrelated canonical-file
properties is the right compatibility decision.

## ☑ reviewed 2026-08-28 (the second session — the review F2 came from) · `bc36c53` (entry written as `9d5f1a2` before the push rebase) · docs: F2 fixed by stating the starter-relative link rule; F1 left for the author

**Verdict.** Accepted — the right option, for the reason given: a snapshot whose parity cannot be checked (F1)
must not acquire intentional differences, or drift becomes indistinguishable from intent. The section is where a
reader following the dead link will find it (the README's review table sits above it), and the `mkdocs --strict`
blind spot it records is real — `docs/specs/` is outside the built site; ten stale links between specs were
repaired by hand on 2026-08-27 for the same reason. Re-ran the snapshot link check after the pull: the two
`../../CLAUDE.md` links are the only unresolved ones and are now explained. Item 1 (browse vs compare) is put
to the owner in the session log rather than decided here; item 2 (F1) stays open for whoever holds the starter.


**What.** One section added to `docs/specs/mongoose-bootstrap-artefacts/README.md` naming that links
inside `specs/` are STARTER-relative and do not resolve in the snapshot. Responds to F2 of
`review_mongoose_bootstrap_review_resolution.txt`.

**Why this option and not the other.** That review offered two fixes — adapt the paths, or state the
rule. They are not equal. Adapting would make every future refresh re-apply the same edits, and would put
intentional differences into a copy **whose parity with the source already cannot be checked** (that is
the same review's F1). Divergence indistinguishable from drift is worse than a link one sentence
explains, so the snapshot stays byte-faithful to the starter and the README carries the explanation.

Also recorded there, because it explains how two dead links passed every gate: `mkdocs build --strict`
cannot catch this class at all — `docs/specs/` is not part of the built site, so the link checker never
sees that directory. They were found by reading.

**Files.** `docs/specs/mongoose-bootstrap-artefacts/README.md` (+1 section).

**Verified.** Both links confirmed dead independently before fixing — `../../CLAUDE.md` from `specs/`
resolves to nothing; the file is at `ai/CLAUDE.md`. mkdocs strict still passes; four-term sweep clean;
1069 green.

**What the reviewer must still check.**
1. **Whether the owner prefers the other option.** If the snapshot is meant to be browsed standalone,
   adapt the paths instead and delete this section. Both are defensible; only the author knows whether
   this copy exists to be read or only to be compared.
2. **F1 is NOT fixed, and I cannot fix it.** The README's own update rule requires the source revision
   and date to be recorded; none is anywhere in the directory. That needs the starter, which is not on
   this machine. Until it is recorded, "the copy matches the source" is unverifiable by anyone —
   including the author, later.
