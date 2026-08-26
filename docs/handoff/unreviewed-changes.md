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

## ☑ 2026-08-26 · RETRACTED — "saved graphs destroyed on project open" was a MISDIAGNOSIS

An earlier revision of this file logged a graph-loss bug claiming
`syncOpenGraphsIntoConfig()` rewrites `savedGraphs` from the open tabs **with no guard**, so opening a
project with no log clobbers the profile's graphs. **That root cause was wrong.** The diagnosis grep
matched only lines containing `savedGraphs`, so it never surfaced the guard on the line directly above:

```java
private void syncOpenGraphsIntoConfig() {
    if (store == null) return;   // no log → tabs are empty; config already holds the profile's graphs
    config.savedGraphs.clear();
    config.savedGraphs.addAll(graphTabs.specs());
}
```

That guard has been present since the initial public release (`e965afa`). **Verified 2026-08-26** by
driving the running build over MCP: `close project` → reopen the project **with no log** → the profile's
`graph.count` stayed **5**, not clobbered (md5 changed only where expected). Single-instance
open-project-without-log is safe. No code change made — there is nothing to fix here.

**What actually happened.** The 6→1 graph loss observed earlier was a **two-instance auto-save race**: two
analyser GUIs (the jbang/MCP-driven one and an IntelliJ-launched local build) were open on the *same*
project profile at once, and project auto-save has no multi-instance merge, so one overwrote the other's
graph set. That is the known **one-instance-per-project** operational hazard, not a defect.

**Possible future hardening (design question, not a bug):** multi-instance protection on the project file
— a lock, or last-writer/mtime detection that warns instead of silently overwriting. Flagged for
consideration only.

**Lesson for this ledger:** a grep that matched the payload lines but not the guard produced a confident,
wrong bug report that was committed. Read the whole method, not the matching lines.
