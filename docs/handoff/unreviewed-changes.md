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

## ☑ reviewed 2026-08-26 (the second session) · `881b047` · fix(topology): populate the split-view EventProcessor dropdown

**Verdict.** Correct and minimal: a second `SourcePanel` instance that was never handed the processor
list, now mirrored at the three `setProcessors` sites. `mvn test` green on main (865); the fixed jar
renders the embedded source pane in the Topology split view (painted shot read; the combo's contents are
not legible at the split's width, so the author's live confirmation of all five stands). Follow-up, not
blocking: `setProcessors` now has two consumers kept in step by hand — a `Runnable`/listener on the
Source tab's panel would remove the mirroring. Recorded here rather than filed.


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

## ☑ reviewed 2026-08-27 (the first session) · `32db461` · fix(graphs): a project's saved graphs survive the first log opened under it

**Verdict.** Correct, minimal, and the right layer — the placeholder tab is structure, so suppressing the
*echo* rather than the *tab* is the fix, and the `onLoaded` snapshot is honest belt-and-braces. Both
reviewer checks done and both pass. **(a)** a fresh log with no saved graphs still opens `Graph 1`, and
`restore(List.of())` returns early leaving it — verified headless and live (`context.graphs` → `["Graph
1"]` on a clean home). **(b)** a hand-added graph still persists — live: `graph {name:"Mid"}` →
`graph.count=2`, `graph.0.name=Graph 1`, `graph.1.name=Mid` in the profile. `mvn test` green.

I pinned check (a) as a third test in `GraphTabsBindIsNotAnEditTest` rather than leaving it verified
once: it is the regression this fix could plausibly cause (a suppressed tab, not a suppressed edit) and
nothing else covers it.

Follow-up, not blocking: `bind()` and `restore()` both end `restoring = false` unconditionally rather
than restoring the previous value. Unreachable today — `bind()` is called from exactly one place, a line
before `restore()`, never nested — but the guard collapses silently if that ever stops being true. A
`boolean prev = restoring; … finally { restoring = prev; }` in both costs two lines.

**What.** `GraphTabs.bind()` opened its placeholder tab through `addGraph()`, which fires the change
listener; since B-M20-3 that listener persists the open tabs, and `MainFrame.onLoaded` assigns the store
before binding, so the profile's graphs were overwritten with `["Graph 1"]` a line before
`restore(config.savedGraphs)` read them. Fix: the placeholder is opened under the `restoring` guard, and
`onLoaded` restores from a snapshot taken before binding. See the bug entry below for the three write-ups.

**Files.** `GraphTabs.java` (bind), `MainFrame.java` (onLoaded), `GraphTabsBindIsNotAnEditTest.java` (2),
`CHANGELOG.md`.

**Verified.** `mvn test` 867 green; sweep clean (four-term form). Live, one JVM, isolated home: project
with 4 saved graphs → open a log → `graph.count` stays 4 through the open and a forced save (was 1 on
the unfixed jar, same script).

**Reviewer must still check.** That the placeholder tab still appears on a fresh log with NO saved graphs
(the `restoring` guard suppresses the edit, not the tab — `addGraph()` still runs); and that a graph
edited by hand still auto-persists (pinned by the second test, but Swing-side confirmation is cheap).

## ☑ reviewed 2026-08-27 (the first session) · `5e73bae` · fix(project-panel): Show file / Open; runbook + glossary open read-only in the app

**Verdict.** Correct, and the D-C2 reasoning is right rather than convenient: the analyser still never
executes a runbook and never serves its contents to an agent, and a person reading the file the profile
points at is neither hazard. The model side is exactly as claimed — `exists ? VIEW_FILE : NONE`, so a
missing runbook has no *Open* and the red row still says why. The path shown is the row's `resolved`
value, which reached `context` through the M38.1 gate, so the viewer inherits the containment rule
rather than re-deriving it. 958 green; the reveal-only bytecode test still holds.

**F1 (low, unfixed — owner's call).** *The 256K cap is announced but applied after the whole file is
read.* `Files.readString(p)` materialises the entire file, then truncates; so the cap bounds what is
DISPLAYED, not what is read. Point a runbook at a multi-gigabyte file inside the project — a profile is
portable context that arrives from a colleague, and `runbook.0.path=data/dump.jsonl` is not exotic — and
the read fails with `OutOfMemoryError`, which `catch (Exception e)` does **not** catch, so it escapes the
EDT action rather than becoming the "(could not read …)" message the code intends. Two-line fix: check
`Files.size(p)` first, or read bounded chars. Not urgent (the path is contained and the file is the
user's own), but the code currently promises a cap it does not enforce.

**N1.** An open viewer is modeless and does not follow a theme switch — `updateComponentTreeUI(this)` in
MainFrame walks the frame only, not other windows. Only reachable in combination with `3d27f3d`; noted
there too.

**What.** Owner-requested naming: *Show* → *Show file*, *Go* → *Open*. New: *Open* on a runbook or vocabulary row
opens a read-only viewer (plain text as written, 256K cap announced, Show file / Copy path / Close, modeless).
D-C2 wording sharpened in the spec and both docs pages: the analyser never executes a runbook and never serves
its contents to an agent; a person reading it in the app is neither.

**Files.** `ProjectPanel` (labels, `viewFile`), `ProjectModel` (`Target.VIEW_FILE` for runbook/glossary rows when
the file exists), `ProjectModelTest`, docs (project-panel, portable-context, ai-and-runbooks, spec-portable-
context D-C2), CHANGELOG, regenerated shots + conversations.

**Verified.** 933 green; reveal-only bytecode test holds (no MainFrame reference; Navigator unchanged); mkdocs
strict; sweep; screenshots regenerated and read.

**Reviewer must still check (Swing, not unit-tested).** Click *Open* on the `restart runbook` row of the demo
project: a modeless dialog titled with the path, monospace text of `ops/restart-quote-service.md`, *Show file*
opens Finder, *Copy path* copies, *Close* closes; the row for a MISSING runbook has no *Open*. Check the Graph
row's *Open* still lands on the Topology tab and a processor's *Open* on the Source tab.

## ☑ reviewed 2026-08-27 (the first session) · `3d27f3d` · fix(ui): Project panel + Event types panel follow a theme switch

**Verdict.** Correct and well-diagnosed. The cause is stated accurately — `updateComponentTreeUI` leaves
EXPLICIT colours alone, and both panels set foreground/font/border explicitly at render time, so a
re-render is the right remedy rather than a wider sweep. Replacing the hard-coded WARN brick with
`UiTheme.warnForeground()` (recomputed per call) fixes the root cause rather than the symptom. 958 green.

**N1 (not blocking).** `EventFilterPanel.refreshTheme` identifies group headers by *"is this JLabel's font
bold"*. It works, but it is a heuristic standing in for a fact the panel knows when it builds the label;
if a bold non-header label ever appears it will be recoloured silently. Cheap to make explicit (tag the
headers, or keep a list) whenever that code is next touched.

**N2 (not blocking, spans `5e73bae`).** A theme switch does not reach an OPEN modeless window, because
`MainFrame` calls `updateComponentTreeUI(this)` — the frame only. The runbook viewer added in `5e73bae`
is exactly such a window, so a viewer left open across Theme ▸ … keeps the old palette. This gap exists
only because the two changes met; neither is wrong alone.

**What.** Both panels painted colours/fonts/borders from UiTheme at build time and kept them across Theme ▸ …
(updateComponentTreeUI leaves explicit values alone). `ProjectPanel.refreshTheme()` re-applies the surface and
re-renders; `EventFilterPanel.refreshTheme()` rebuilds its section border and group-header colours; `applyTheme`
calls both. New `UiTheme.warnForeground()` is theme-aware.

**Files.** `ProjectPanel`, `EventFilterPanel`, `UiTheme`, `MainFrame.applyTheme`, CHANGELOG.

**Verified.** 954 green; mkdocs strict; sweep; screenshots regenerated. NOT verified live: the runtime theme
switch is a menu action with no socket verb, so it could not be driven from here.

**Reviewer must still check (Swing).** With the demo project open and the Project panel showing: Theme ▸ Dark —
the panel background, row text, muted second lines, section titles, the ⚠ rows (should be salmon, readable) and
the Event types header/border all take the dark palette immediately; Theme ▸ Light reverses it. Also check the
open runbook viewer dialog is not expected to follow (it is modeless and built once — close/reopen).

# Bugs found (not yet fixed) — for the next session

Not changes to review — defects surfaced while working, logged here so the next puller can pick them up and
fix them properly. Promote to a tracker item / spec when triaged.

## ☑ 2026-08-26 · Saved graphs destroyed — misdiagnosed, retracted, then REPRODUCED single-instance on a LOG open; FIXED on `fix/graphs-lost-on-log-open`

**Third write-up (the second session, reviewing both earlier ones).** The retraction below is right that
the first mechanism was wrong and that *open project with no log* is safe — reproduced here too, one JVM,
isolated `-Duser.home`, a project with 4 saved graphs: `graph.count` stayed 4 through the open and two
forced saves. It is wrong that there is nothing to fix. Continuing the same single-instance run:
**with the project active, opening a log dropped the profile to `graph.count=1` ("Graph 1")**. No second
instance was involved.

Mechanism, read from the whole method this time: `onLoaded` assigns `store`, then calls
`graphTabs.bind()`, which opens its placeholder tab through `addGraph()` — and `addGraph()` fires the
change listener. Since B-M20-3 (`b40f207`, 2026-08-17) that listener is `onGraphsEdited` →
`saveConfigQuietly` → `syncOpenGraphsIntoConfig()`, whose `store == null` guard no longer applies
because the store was assigned two lines earlier. So `config.savedGraphs` became `["Graph 1"]` one
line before `graphTabs.restore(config.savedGraphs)` read it back. Every release since 1.1 has done this
on the first log opened under a project with saved graphs. The two-instance race described below may
also be real, but it was not needed to lose the graphs.

Fixed: `GraphTabs.bind()` opens its placeholder under the `restoring` guard (structure is not an edit),
and `onLoaded` snapshots the saved graphs before binding. `GraphTabsBindIsNotAnEditTest` plays
MainFrame's exact sequence with MainFrame's exact listener shape, headless; a second test pins that a
real edit still persists. Live on the fixed jar: 4 stays 4 through the log open and a forced save.
CHANGELOG ▸ Fixed. Lesson for this ledger, added to the one below: a retraction needs the same
reproduction discipline as a report — the retraction tested the path the report named, not the
path the user had actually taken (they opened a log).

### Second write-up (retraction, as committed in 61e8952)


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
