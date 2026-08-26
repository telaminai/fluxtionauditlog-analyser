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

## ☐ 2026-08-26 · `881b047` · fix(topology): populate the split-view EventProcessor dropdown

**What.** The Topology tab embeds its own `SourcePanel` (`TopologyPanel.embeddedSource`), separate from
the Source tab's `MainFrame.sourcePanel`. `SourcePanel.setProcessors(...)` — the only thing that fills the
`EventProcessor:` dropdown — was called **only** on the Source-tab panel (`MainFrame` lines ~2254, ~2632,
~2692). So the topology split-view's dropdown was **always empty**, even with a project's
`eventProcessorFqns` fully populated. Its *selected* processor still navigated (both panels share the
`SourceService`), but you could not switch processors from the split view.

**Fix.** Added `TopologyPanel.setEmbeddedProcessors(fqns, selected)` (remembers the last choices and
forwards to `embeddedSource`), seeded it in `bindSource()`, and mirrored each of the three Source-tab
`setProcessors(...)` calls into it.

**Files.** `TopologyPanel.java` (field pair + `setEmbeddedProcessors` + `bindSource` seed);
`MainFrame.java` (three mirror calls).

**Why un-reviewed.** Found and fixed from a downstream-consumer session (the topology dropdown was empty
against that project); too small to warrant a full handoff brief, but it touches shared UI wiring, so it
wants a second pair of eyes.

**Verified.** `mvn test` green (865, 0 failures) on JDK 21 — compiles, nothing regressed. Diff leak-swept
(no real venue/account/domain names). CHANGELOG updated (`### Fixed`).

**Reviewer must still check.** Swing is not unit-tested — **build and run the jar, open a project with
≥2 event processors, open the Topology tab, hit Split, and confirm the `EventProcessor:` dropdown lists
all of them** (and switching one re-navigates the embedded source). Confirm no double-refresh flicker on
log open. Consider whether the same mirroring belongs anywhere else `sourcePanel` state is pushed.
