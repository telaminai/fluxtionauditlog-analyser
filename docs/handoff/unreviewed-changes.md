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

## ☐ 2026-08-28 · `c4d1db3` · fix(mcp): the survivor window reclaims a dead endpoint; ATTENTION is amber

**What.** Owner's eyeball run of `verify-m43.py` (check C, step "close the second analyser"): the first window
went *ready → elsewhere* correctly, then read **"MCP starting" for ever** once the second window closed — its
server was listening throughout; only the endpoint FILE had died with the other process. `McpSetupState.
shouldReclaim(readiness, serverListening)` (pure) is true only for `STARTING` with a listening server;
`refreshMcpIndicator` then calls new `ActionServer.republish()` and re-classifies. A LIVE other owner is never
displaced (`OTHER_INSTANCE` → false), so two windows cannot fight. Also: the light rendered ATTENTION in
`warnForeground()` (brick) — the owner's word for it was "red", and D-AI9 says amber and no red — so new
`UiTheme.attentionForeground()` (amber on both themes) is used for the light only; the Project panel's ⚠ rows
keep brick/salmon. The indicator timer is now a field stopped in `onExit` (my N1 on `ac6a559`).

**Files.** `mcp/McpSetupState` (+`shouldReclaim`), `net/ActionServer` (+`republish`, `start()` uses it),
`ui/MainFrame` (`refreshMcpIndicator`, `mcpIndicatorTimer`, `onExit`), `ui/UiTheme` (+`attentionForeground`),
`McpSetupStateTest` (+truth table), `ActionServerTest` (+republish round-trip), `tools/verify-m43.py` (automated
check [4]: the first analyser owns the endpoint again within ~10 s; eyeball CHECK C step 4 added, red named as
FAIL), CHANGELOG ▸ Fixed.

**Verified.** 1069 green; jar rebuilt. NOT verified live from here: the owner's eyeball window was open under
the shared isolated home, so I did not start analysers against it — the owner re-runs `--eyeball` (steps 3–4)
and the automated run (`verify-m43.py`, now 9 checks) is the reclaim's regression test.

**Reviewer must still check.** (1) Whether auto-reclaim is the right POLICY, not just the right mechanism: when
the newcomer goes away, an AI client silently starts reaching the survivor's log again. I judged that correct —
the endpoint should always name *some* live window, and the light says which — but it is a decision, not a
fix, and D-AI9 does not state it; a one-line addendum to `completed/spec-ai-menu.md` if the reviewer agrees.
(2) The amber values (`0xA86E00` light / `0xE6B43C` dark) against both themes' status-bar backgrounds — I chose
them for contrast by arithmetic, not by eye. (3) Whether `STARTING` with NO listening server (start failed) should
say something other than "starting" — unchanged here; `applyRestServer` already puts the failure in the status
bar.

## ☑ reviewed 2026-08-28 (the second session) · `ac6a559` · fix(ui): the D-AI9 light polls, because the fact it reports changes without us

**Verdict.** Correct, and the right shape. The three questions the entry asks, answered from the code:
(1) **A repeating timer is already this frame's idiom for a fact nothing notifies us of** — `followTimer`
polls the log file at `FOLLOW_POLL_MS`, `projectSaveDebounce` and `searchDebounce` are timers too; 5 s is
proportionate for a light a human reads. The per-tick cost is `RestEndpointFile.read()` (one small file,
or null when the transport is off) plus `ProcessHandle.of(pid).isAlive()` — well under a millisecond, fine
on the EDT. (2) **No fight with the theme switch**: both paths call the same `refreshMcpIndicator()`, which
recomputes label, tooltip and foreground from the *current* theme every time, so the timer can only ever
re-apply what `applyTheme` just applied; `JLabel.setText` with an identical string does not revalidate, so
there is no 5-second flicker either. (3) **`verify-m43.py` under-claims by half a check**: eyeball 3 says
the light's *colour* cannot be reached from a script, but `capture-docs.py` already launches under
`Theme ▸ Dark` and the `screenshot` verb paints the window — so "green in light, legible in dark" IS
reachable (two launches, read the pixel); only "recomputed on a LIVE switch" is not. Not blocking; worth
extending if the script is touched again. The F1 one-liner (symlinked file skipped like a symlinked
directory) is right and tested. 1067 green.

**N1 (not blocking).** The timer is anonymous and never stopped; `onExit` stops `followTimer` explicitly
before `System.exit`, so for symmetry hold it in a field and add it to that step list. Harmless today
because exit follows.

**Addendum (same day).** The owner's eyeball run found what this review did not think to ask: after the second window CLOSES, the first reads "MCP starting" for ever, because nothing re-publishes its endpoint. Fixed in `c4d1db3` (entry above). The poll itself was correct; the state machine behind it lacked a transition.

**N2 (live verification is the owner's eyeball run).** The two things I could not observe from here — the
colour on a live `Theme ▸ Dark`, and the light moving to *MCP elsewhere* within ~5 s when a second analyser
takes the endpoint — are exactly checks C of `verify-m43.py --eyeball`, which the owner is running next.


**What.** `MainFrame.startMcpIndicatorWatch()` — a 5-second repeating Swing timer plus a
`windowActivated` refresh for the MCP status light. Also review F1's one-liner in `SkillDiscovery`
(symlinked FILE, not just directory) with a regression test, and a new `tools/verify-m43.py`.

**Why.** Found by writing the verification script, not by reading the code. Starting a second analyser
under the same home makes the NEWCOMER take the endpoint file, so the first window silently stops being
the one an AI client reaches — and its light kept reading "MCP ready", because it refreshed only at
startup, on a theme switch and on the transport toggle. None of those happen when another process takes
over. A light that asserts the wrong thing in exactly the state it was built to reveal is worse than no
light, so this is a defect in D-AI9 rather than a polish item.

**The design choice worth reviewing.** A poll is the shape I chose because nothing notifies us of another
process claiming the file. Cost is one small file read and a pid compare every 5s on the EDT, and window
activation covers the common case before the timer fires. The alternatives I did not take: a
`WatchService` on the endpoint directory (more machinery, and the file is rewritten rather than
edited-in-place, so the event story is not simpler), or refreshing only on activation (correct when you
look at the window, wrong while you are watching it).

**Files.** `MainFrame.java` (+watch), `SkillDiscovery.java` (+1 guard),
`SkillDiscoveryTest.java` (+1 test), `tools/verify-m43.py` (new).

**Verified.** 1067 green. `verify-m43.py` re-run against the rebuilt jar: 8 pass, 0 fail — including the
two-analysers-one-owner case that exposed this, and an assertion that the watch is wired.

**What the reviewer must still check.**
1. **Is 5s the right interval, and is a timer acceptable at all here?** It is the first repeating timer I
   have added to this frame. If there is a reason this app avoids them, this is the wrong shape and
   activation-only would be the fallback.
2. **That the timer does not fight the theme switch** — both call `refreshMcpIndicator()`, which
   recomputes explicit colours; I could not observe painting from a script.
3. **`verify-m43.py`'s honesty.** It claims three checks are unreachable from a script. If any of those
   IS reachable, the script is under-claiming and should be extended rather than trusted as-is.

## ☑ reviewed 2026-08-28 (the second session) · `7e8e859` · docs(specs): reviewer addendum §10a written INTO the Mongoose validation spec

**Verdict.** The substance checks out against the sources it cites, and the form is acceptable. A1: `tools/bench/
loop-bench.py` does glob the registry, check mode 600 and the UP-MNG-01 fields, check the pid is alive and
export the log — the steps the addendum says it plays; naming it as the minimum evidence for VAL-04/05 is
right. A2: `spec-agent-brokered-dev-loop.md` §C1 (*the registry is a directory of endpoint files, owned by
nobody*, `~/.mongoose/servers/<name>` mode 600) says what the addendum says it says. A4: `SkillFrontmatter`
reads exactly `name`/`description`, so the suggested `SKILL.md` shape is the one M43 surfaces. A3 is a
recommendation, correctly framed as one. On the form — editing another session's spec on main — the
addendum is additive, dated and signed in place, and the ledger entry exists, which is the protocol.

**N1 (the one follow-up).** A1–A4 live only as prose in §10a; the artefacts' own tracker
(`mongoose-bootstrap-artefacts/specs/tracker.md`) does not mention them, so the author can accept or
reject each explicitly — four `☐` items there, one per recommendation, and the decision on UP-MNG-02's
fate recorded in `upstream-asks.md` as A3 asks. Otherwise the addendum is advice the next author has to
find, which is the failure mode it was written in to avoid.


**What.** My review of `docs/specs/mongoose-bootstrap-artefacts` (d91e236) did not stop at a review file:
it added **§10a (A1–A4)** to `specs/spec-mongoose-analyser-validation.md` and annotated the *Discovery
contract* paragraph of `skill/mongoose-local-skill-contract.md`. Full reasoning in
`docs/handoff/review_mongoose_bootstrap_artefacts.txt`.

**Why written in, not only reported.** The findings are about which spec the work anchors to, and a
recommendation that lives only in a review file is one the next author has to go and find. But this means
**I have edited another session's spec directly on main**, which is exactly what this ledger exists for.
The addendum is additive — nothing of theirs was changed or deleted, and it is marked as a reviewer
addendum with its date and author, so its status is legible in place.

**The substance, in one line each.** The artefacts cite M19 only, while the loop they describe is owned by
`spec-agent-brokered-dev-loop.md` (ACCEPTED v2), which they do not cite. A1: §H's conformance harness
already exists (`tools/bench/loop-bench.py`) and should back Gates V2–V3 instead of a hand-run checklist.
A2: server discovery re-derives §C1's registry in a weaker, config-derived form. A3: UP-MNG-01 and
UP-MNG-02 should not share a fate. A4: ship the shared skill in the `SKILL.md` frontmatter shape M43 now
reads, and the analyser surfaces it for free.

**Files.** `docs/specs/mongoose-bootstrap-artefacts/specs/spec-mongoose-analyser-validation.md` (+82,
new §10a), `.../skill/mongoose-local-skill-contract.md` (+7, one annotation),
`docs/handoff/review_mongoose_bootstrap_artefacts.txt` (new).

**Verified.** 1066 green; `mkdocs build --strict` passes; four-term sweep clean. I also checked the
snapshot directory against the README's own promise rather than taking it — no real home paths, no
credential-shaped strings. loop-bench.py's scope was read (`tools/bench/README.md` + the script's step
list) rather than inferred, and `mongoose-stub.py`'s "statement of what one must do" framing is quoted
from it.

**What the reviewer must still check.**
1. **Whether A1 is actually achievable** — I claim the real starter could satisfy `mongoose-stub.py`'s
   contract and let `loop-bench.py` run unstubbed. I have not run it against a real Mongoose and have no
   way to; if the starter cannot publish a registry file, A1 depends on A2 and both need owner sign-off.
2. **A2's cost.** I recommend the skill read the registry with a YAML fallback. That is easy to say from
   outside the starter; the author of those scripts should say whether it is cheap there.
3. **A3 is a decision, not a finding** — whether UP-MNG-02 is superseded by skills is the owner's call and
   I only argue the two asks should be separated, not which way it goes.
4. **Whether §10a belongs in that document at all.** It is a reviewer's voice inside someone else's spec.
   If the author would rather it lived only in the review file, moving it is a clean revert of two hunks.

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

## ☑ reviewed 2026-08-27 (the first session) · `44b44c9` · fix(report): a table's numeric call parameters survive JSON

**Verdict.** Correct, correctly scoped, and a real bug — any agent sending a numeric anchor in a table
call hit it, and it presented as the tool contradicting the author ("read needs a recordIndex" when one
was given), which is the kind that wastes an afternoon. Both flagged challenges checked; both hold.
962 green after the three tests added here.

**The 1e15 ceiling — sound, and for a better reason than "a big round number".** The real boundary is
2^53 (9007199254740992), above which a double no longer holds consecutive integers, so converting would
INVENT a value. 1e15 sits comfortably under it. I checked it against every numeric parameter the schemas
actually expose rather than in the abstract: `at`/`from`/`to` are **epoch millis** (~1.75e12),
`byteOffset` would need a petabyte-scale log, and `recordIndex`/`count`/`limit`/`step` are small. Nothing
reachable comes near the ceiling. Above it the value is left as-is and the verb refuses it — announce
rather than be silently wrong, which is the house rule. Pinned by a test.

**The series seam — the fix already covers it; only the test was missing.** SERIES and TABLE both build
their call through `callMap`, so series was fixed the moment table was. But an untested half of a shared
seam is one refactor from being a bug again, and this one cost a live capture to find the first time, so
it is tested now with epoch-millis values (`1.0E12` → `1000000000000`, not `1.0E12`).

**Also checked, not flagged:** a genuinely fractional parameter keeps its fraction (`above: 17.25` stays
`"17.25"` — the fix must not turn every number into an integer); null values were already guarded in
`callMap` and still are; `Short`/`Byte`/`BigInteger` route through the same integral path correctly.

**No follow-up.** Scenario 6 of the sample-conversations page issues `"recordIndex": 0` in a table call,
so the page now exercises this regression on every capture — the bug cannot come back quietly.

**What.** `ReportVerb.callMap` stringified every value of a table section's `call`; a JSON number arrives as a
`Double`, so `recordIndex: 0` was re-issued to `read` as `"0.0"`, `asInt` returned null and assembly failed with
*read needs a recordIndex* although the author had given one. `callValue` now renders an integral number whole
(`0.0` → `0`); non-integral and non-numeric values are unchanged.

**Files.** `report/ReportVerb.java` (`callMap`, new `callValue`), `ReportVerbTest`
(`numericCallParametersSurviveAsIntegers_aJsonDoubleAnchorStillAnchors`), CHANGELOG ▸ Fixed.

**Verified.** 956 green; the sample-conversations harness (which aborts on any failed call) records two reports
with `read`-derived tables and both render rows (`conv-chart-and-report.png`, `conv-deploy-uat.png`).

**Reviewer must still check.** Whether `1e15` is the right ceiling for "render whole" (above it a long would
still be exact but the double no longer is), and whether the same stringify-then-reparse seam exists for
SERIES sections' `call` — `callMap` is shared, so the fix covers it, but no series test exercises a numeric.

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
