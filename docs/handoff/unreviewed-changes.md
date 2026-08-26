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
(no real venue/account/domain names). CHANGELOG updated (`### Fixed`). **Verified live 2026-08-26** by
driving the running fixed build over the MCP bridge: the Topology ▸ Split `EventProcessor:` dropdown, empty
before, lists **all 5** processors (author confirmed the open combo shows all five). No flicker observed.

**Reviewer must still check.** Swing is not unit-tested — re-confirm on a fresh build/run, and consider
whether the same mirroring belongs anywhere else `sourcePanel` state is pushed.

---

# Bugs found (not yet fixed) — for the next session

Not changes to review — defects surfaced while working, logged here so the next puller can pick them up and
fix them properly. Promote to a tracker item / spec when triaged.

## ☐ 2026-08-26 · Saved graphs are destroyed when a project is opened without a log

**Symptom.** Open a project that has ≥1 saved graph. The graph tabs collapse to the empty default
"Graph 1" and the profile's saved graphs are lost — `graph.count` in the profile drops to 1 on the next
autosave. Reproduced repeatedly: a profile with `graph.count=6`, opened in the GUI, becomes
`graph.count=1`.

**Root cause.** `MainFrame.saveConfigQuietly()` calls `syncOpenGraphsIntoConfig()` (~line 2780), which does
`config.savedGraphs.clear(); config.savedGraphs.addAll(graphTabs.specs())` — it rewrites the saved-graph
list from the **currently-open tabs**. On project open the profile's graphs only reopen as tabs via
`graphTabs.restore(config.savedGraphs)`, and that restore is gated on a bound log (e.g. the `store != null`
guard, ~line 2791; the log-bind path at ~2238). Opening/switching a project **closes the log** (session
boundary), so `restore` produces no tabs; the next `saveConfigQuietly()` (fired via `onConfigChanged` /
`onGraphsEdited`) then syncs `savedGraphs` down to the lone default tab — **permanently clobbering the
saved graphs**. There is no manual save to avoid it (auto-persist only).

**Repro (clean).** 1) Start the app with a profile that has several saved graphs, **no log open**.
2) `File ▸ Open project`. 3) Tabs show only "Graph 1"; the profile's `graph.count` is now 1.

**Suggested fix (reviewer picks one).** (a) Don't shrink `config.savedGraphs` in
`syncOpenGraphsIntoConfig()` when the tabs weren't (re)instantiated — skip the sync when no log is bound,
or diff against the last-loaded set; or (b) restore saved graphs as tab *definitions* even with no log,
plotting lazily when a log arrives; or (c) treat the loaded saved-graph definitions as authoritative and
only fold in additive edits. Add a regression test around *open project → autosave with no log*.

**Workaround today.** Keep a backup of the profile and restore it with the app closed; or recreate the
graphs live once a log is open. **Impact:** a project profile's saved graphs are wiped the first time the
project is opened without a log — silent data loss.
