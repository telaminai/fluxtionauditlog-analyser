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

## ☐ pending review · `92ad3ba` · M19.8/9 Linux loop-bench CI and launch parsing tests

**What.** Extracts desktop launch-argument stripping into a pure `Main.parseDesktopArgs` decision with
headless tests, and adds a Linux CI job that packages the analyser then runs the existing 23-step
stubbed registry/export/analyser/MCP loop under `xvfb-run`.

**Why.** The end-to-end bench covered `--rest` only on machines able to launch Swing and was not run by
CI. A typo could regress into a fake log path unnoticed, while the cross-repo registry/export contract
could rot between local runs.

**Files.** `.github/workflows/ci.yml`, `Main`, `MainLaunchArgsTest`, `tools/bench/README.md`, M19 tracker.

**Verified.** `MainLaunchArgsTest` passed; full `mvn -q test` passed; a freshly packaged local
`tools/bench/loop-bench.py --stub --launch` passed 23/23 outside the socket sandbox; pinned strict-site
build, workflow YAML parse, diff check and tracked-file four-term sweep passed.

**What the reviewer must still check.** Read the first GitHub Actions run: the `loop-bench` job must
actually install/use `xvfb`, reach all 23 passes, terminate its analyser/stub children, and not hang until
the 10-minute timeout. Confirm the separate build job remains headless and that caching/duplicate package
cost is acceptable. In `Main`, verify MCP/help still short-circuit before desktop parsing and that keeping
additional positional arguments matches the pre-existing behaviour.

## ☐ pending review · `1f30213` · M19.13 New project offers discovered setup

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

## ☐ pending review · `db42919` · M19.12/12a safe Fluxtion build-key management

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
