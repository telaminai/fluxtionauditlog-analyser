# Un-reviewed changes on `main` — ARCHIVE of reviewed entries (tidied through 2026-08-30)

Every entry below was reviewed and ticked in the live ledger `docs/handoff/unreviewed-changes.md` before
being moved here. The initial archive held nine entries plus one incident record (2026-08-26 → 2026-08-28);
later accepted entries carry their review verdict with them. The live file holds only entries still awaiting
review or accepted entries awaiting the next tidy.

---

## ☑ reviewed 2026-08-30 (the analyser reviewer) · `b07a74d` · make the released-bundle analyser/MCP acceptance reproducible

**Verdict.** Accepted. The early-return and exception paths all converge through `finish`, which interrupts
and then force-kills surviving analyser/bridge children and removes the disposable home. The bridge request
has a bounded 15-second response wait; a timeout becomes a failed assertion rather than hanging the bench,
and a subsequent broken pipe is caught by the outer failure path. Exact coverage 1.0 is correctly scoped to
M19's typed acceptance bundle in both the module docstring and bench README; it is not presented as a generic
bundle invariant. The previously recorded public-wire run remains the behavioural proof: 19/19 against the
published artefact, including a fresh packaged analyser and a separately launched packaged MCP bridge.

I did not rerun the desktop process under Linux/xvfb. I did re-read the public-wire implementation and its
shared `McpBridge`, compile the script, verify the documented invocation, and confirm the current generated
Download ZIP still passes the separate static contract 49/49. A refreshed producer artefact is tracked in the
P3 review, not as a defect in this analyser-owned bench.

**What.** `tools/bench/bundle-client-bench.py` launches the packaged analyser under a disposable,
never-configured Java home; opens a generated bundle project before its exported YAML + declared GraphML;
asserts the profile's two runbooks, skill provenance, record count, pairing and complete coverage; then
launches the packaged stdio bridge and checks current discovery, tools/list and analyser_context against
that same state. The bench README documents its place beside the static ZIP checker.

**Why.** M19 P3's producer run had one remaining analyser-owned gate. An in-process test or a hand-written
REST request would not prove the command an MCP client actually launches, and a one-off script would not
guard the three-repository seam after the milestone. This preserves the exact public-wire check that
accepted the released bundle.

**Files.** `tools/bench/bundle-client-bench.py`, `tools/bench/README.md`; tracker and P3 review in the
following metadata commit.

**Verified.** Python compile and help pass. Against fluxtion-web artefact branch `m19/p3-artifacts` at
`893fbdf`, the bench passes 19/19 using the packaged analyser jar: fresh REST launch, two-call project/log
load, two described runbooks with existing files, canonical@f5efe17 provenance, 23 records, pairing 2/2,
coverage 1.0, modern MCP discovery, 14 advertised tools and analyser_context state parity. Diff check
passes. The desktop launch required running outside the filesystem sandbox; the isolated home and bridge
were removed afterwards.

**What the reviewer must still check.** Read the process cleanup and failure paths, especially early
returns and a bridge timeout. Run the command from `tools/bench/README.md` under Linux/xvfb against the
handed-off ZIP if practical. Challenge the strict coverage-1.0 requirement: it is deliberate for M19's
typed business-event example, but this bench must not be presented as a generic arbitrary-bundle checker.

---

## ☑ reviewed 2026-08-28 (the first session) · `c4d1db3` · fix(mcp): the survivor window reclaims a dead endpoint; ATTENTION is amber

**Verdict.** Correct, no findings. Full reasoning in commit `d2007ea`; ticked here because a review that
only exists in a commit message is one the next reader of this ledger will not find.

The reclaim predicate is right and the load-bearing part is what it REFUSES — `shouldReclaim` is false for
`OTHER_INSTANCE`, so a live owner is never displaced and two windows cannot fight over the file. I checked
the thing a truth table cannot: `actionServer != null` is a faithful proxy for "listening" (assigned only
after `start()` returns, nulled in the catch, nulled on stop), and the interleaving I looked for —
shutdown's `stop()` at 4383 not nulling, so a poll could republish a dead endpoint — cannot happen,
because the timer and the shutdown steps are both on the EDT and serialise.

The amber correction is a finding against my own decision, which is the useful kind: D-AI9 said no red and
my implementation used the Project panel's brick. Their N1 (timer held in a field, stopped in `onExit`)
was already fixed before I reached it, and their point (3) corrected an over-modest "cannot" in
`verify-m43.py` — the colour IS reachable by launching twice under each theme; only the live switch is
not. Corrected there.

**Open, and NOT a blocker:** auto-reclaim is a policy — an agent mid-session silently reaches the
survivor's log, where a person sees the light change. My view is that it is the right policy (a dead
endpoint hides the same change behind a hard failure) and that D-AI9 should gain a one-line addendum
naming the residual rather than only the choice. That is the owner's call, and it is a wording change,
not code.

**Owner's eyeball re-run, same day: PASSED.** CHECK C on the rebuilt jar — *MCP ready* (green) → second window
starts → *MCP elsewhere* in amber → second window closes → back to *MCP ready* in green within the poll. The amber
values are therefore judged by eye as well as by arithmetic; nothing on this entry remains open except the D-AI9
wording addendum above, which is the owner's call.


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

---

_Tidied again 2026-08-29 (the Mongoose/playground session): the nine M19-round entries below were
all reviewed and ticked in the live ledger, then moved here. The live file is empty of pending
entries. Entries are verbatim._

## ☑ reviewed 2026-08-29 (the Mongoose/playground session) · `3720ef9` + `a3769bd` + `9750fad` + `8236aa5` + `34c1081` · correct the M19 profile ABI and add the P3 static bundle preflight

**Verdict.** Accepted — and the checker is the strongest instrument either side has built. Verified
empirically against artifacts from the REAL Download path, not hand-made trees: canonical bundle 49/49 in
BOTH zip and directory mode; the old P1 profile REJECTED with eight precise failures naming each ABI
defect (the entry's explicit ask); all four provenance shapes correct — canonical 49/49, clean mirror
49/49, `none` 35/35 with its own explicit none-consistency check, and a credential-bearing mirror
provenance REJECTED. v3 keys cross-checked against my emitter and the importer-backed test. One NOTE
(N1): `none` and canonical both exit green but mean different things — a `none` bundle is structurally
valid yet cannot meet M19 acceptance, so green on `none` in the coming matrix must not read as
acceptance-ready. Detail: [`review_m19_analyser_slices_round2.txt`](review_m19_analyser_slices_round2.txt).

**What.** `m19-bundle/3` replaces v2's unusable profile table with the real zero-based ConfigStore list
families, selected processor and explicit share version; a `ProjectProfile.load` test pins the exact
generator-facing file. `tools/bench/bundle-bench.py` then checks a generated directory or zip for that
profile ABI, contract/guide mirror, safe inventory, committed source, declared/discoverable GraphML,
runbook/frontmatter/provenance/version parity, executable commands, placeholders, developer paths and
literal key material. Nine deterministic fixtures run in CI, including the real single-root zip shape
and refusal of an unsafe archive member outside that root.

**Why.** P3 scaffolding caught that P1 emitted `sourceRoot.1`, one-based runbooks and singular
`eventProcessorFqn`. The analyser iterates list members from zero and recognises processors only through
`eventProcessorFqn.count`/members plus `selectedEventProcessor`; the apparently complete v2 profile
therefore loaded none of those facts. No v2 bundle was published or accepted.

**Files.** M19 spec/tracker and cross-repo handoff/review; `ProjectProfileTest`; `bundle-bench.py` and its
Python tests; bench/tool documentation; CI workflow. The exact v3 handoff commit is `a3769bd`.

**Verified.** Profile/spec-link focused tests pass; full Maven suite passes 1,110/1,110; bundle fixtures
pass 9/9; packaged stub/analyser/MCP loop passes 23/23; pinned strict-site build, Python compile, diff
check and tracked-file four-term sweep pass. This is intentionally static P3 scaffolding, not a live
generated-bundle verdict.

**What the reviewer must still check.** Compare every v3 key/index directly with `ConfigStore` and
`SettingsShare`, including project-name derivation. Challenge the checker for false passes/failures,
especially zip path/mode handling, Java-properties/frontmatter parsing, `none` versus mirror provenance,
GraphML discovery depth and secret/developer-path heuristics. Run it against the playground's actual
canonical, `none` and non-canonical generated fixtures when available; confirm it rejects the old P1
profile. Inspect the post-push CI run. The real keyless run/export/stop plus fresh analyser/MCP path
remains a separate P3 gate.

## ☑ reviewed 2026-08-29 (the Mongoose/playground session) · `297c4c1` · atomically publish the REST endpoint found by the Linux loop

**Verdict.** Accepted — the race is genuinely closed in the details that decide it: the pending file is
created in the SAME parent (what makes ATOMIC_MOVE possible at all), owner-only permissions are a
CREATION attribute rather than a later chmod (no world-readable window on a token file), both fallbacks
are present, and `finally deleteIfExists` cleans the sibling on every path including the throw. The
bench's tolerance is inside the pre-existing deadline, so a server that never becomes valid still fails
by step rather than hanging — the risk the entry itself raised is not present. Detail:
[`review_m19_analyser_slices_round2.txt`](review_m19_analyser_slices_round2.txt).

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

## ☑ reviewed 2026-08-29 (the Mongoose/playground session) · `389d331` · discover the generated bundle's Maven-resource GraphML

**Verdict.** Accepted — closes F1 of my own discovery review, in the narrowest correct way: the resources
directory joins `graphRoots` ONLY, never `sourceRoots`, guarded by `isDirectory`, and since `apply()`
admits source roots exclusively from `offer.sourceRoots()` the extra root cannot be selected or persisted
even if a user confirms every box. Verified against the REAL generated bundle rather than the fixture —
bundle-bench's day-two check offers the exact emitted path
`src/main/resources/com/example/myapp/generated/MarketProcessor.graphml`, so both sides now agree on the
concrete shape the original review could only state conditionally. Detail:
[`review_m19_analyser_slices_round2.txt`](review_m19_analyser_slices_round2.txt).

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

## ☑ reviewed 2026-08-29 (the Mongoose/playground session) · `6243a89` · resolve the accepted M19 slice-review follow-ups

**Verdict.** Accepted — all three follow-ups closed at the identified points. The key wipe: validation moved
inside the try and the fill null-guarded, so the rejected-name path wipes while a null key still raises
the refusal with no NPE masking it (the interaction the entry asks about). The clean-stop marker is
present and my generator substitutes it — all three project-owned operations (start, export, clean stop)
carry markers and all three are substituted, with the no-marker gate refusing leftovers. Confirmed the
generator consumes 6243a89 and not an earlier snapshot: manifest, profile and both guides carry that
exact revision and bundle-bench's provenance parity passes on the real bundle. Detail:
[`review_m19_analyser_slices_round2.txt`](review_m19_analyser_slices_round2.txt).

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

---

## ☑ reviewed 2026-08-29 (the Mongoose/playground session — the consumer side, and the session that reported the behaviour) · `f5efe17` · make the canonical load-log procedure respect project session boundaries

**Verdict.** Accepted. This came from my P3 run and the correction matches what I actually observed:
`open {project, log, graphml}` returns ok with `ignored: [log, graphml]` and the exact reason "a project
switch is a session boundary, so open the log and graph in a second call, inside the new project". The
new step 2 says precisely that, in the right order, and the wording matches the verb's real behaviour
rather than paraphrasing it. All three checks the entry asks for, run against the SHIPPED bundle:
(1) the "do not use `analyser_context` to find an unopened log" correction is still present — the false
claim is not reintroduced; (2) the shipped skill carries the concrete `./export-audit.sh`,
`logs/audit-fluxtion-spring-mongoose.yaml` and `src/main/resources/.../MarketProcessor.graphml`
substitutions with ZERO `TODO(bundle)` or `/path/to/` markers; (3) the vendored bytes are SHA-256
identical to the published canonical file (`936950b…`). Re-vendored from the live default root:
`canonical@f5efe17e1b234bdb6c55cd8fada27d2bdc8d2bc8`, matrix green on all three legs, and the whole
clean-machine chain re-run on a bundle regenerated at this revision. Worth recording that the drift
guard worked exactly as designed on this re-vendor: the exact-match substitutions still hit, and the
only failure was the VENDORED.md/manifest revision pin — the check whose entire job is to notice that a
re-vendor happened.

**What.** `common/load-audit-log/SKILL.md` now checks whether this project is active and, when needed,
opens the project alone before opening the YAML + GraphML in a second call. The versioned index advances
to the skill-source commit; CanonicalSkillsTest pins the new bytes and the two-call instruction.

**Why.** The live P3 session tried the tempting combined `open {project, log, graphml}` shape. The
analyser correctly treats a project switch as a session boundary and ignores the other parameters, but
the canonical skill did not warn the agent. A generated procedure must not recommend a call that only
partially applies while looking successful.

**Files.** Canonical load-log skill and changelog in `f5efe17`; index, parity test, tracker and handoff in
the following metadata commit.

**Verified.** Focused `CanonicalSkillsTest,ProjectVerbTest,SpecLinksResolveTest`, full Maven suite
1,112/1,112, strict docs and diff check pass on the final index state. The application behavior is
already pinned by ProjectVerbTest; this slice changes instructions, not the verb.

**What the reviewer must still check.** Confirm the two-call wording matches `ProjectVerbTest` and does
not reintroduce the false claim that `analyser_context` can discover an unopened log. Re-vendor from the
live default root in the playground, verify the new skill bytes and revision, and ensure the generated
bundle contains the concrete export/GraphML substitution with no marker.

---

## ☑ reviewed 2026-08-29 (the Mongoose/playground session — the consumer side) · `99c79bf` · publish the analyser-owned canonical m19-skills/1 index

**Verdict.** Accepted, with the strongest evidence available: the consumer now runs against it. The
default CLI (no `--source`) fetches this exact root over the network and records
`canonical@6243a899774d591119559305a137ecf144819efd`, and the fetched bytes were IDENTICAL to the
previously committed snapshot — so the live mechanism retroactively validated the `--declare-canonical`
declaration it replaced, which is now DELETED (F4's playground half). Independently checked: the index
returns 200; both `skills[].path` entries exist below the root; and both files' SHA-256 match analyser
commit `6243a89` byte-for-byte (`4c95500…` and `f2737e2…`). Raw `main` as the build/release root is the
right call — canonical content is analyser-owned, it already matches the required
`<root>/m19-skills/1/index.json` layout, and nothing at runtime ever fetches it (the generator vendors
at build time into a committed snapshot). The load-audit-log + run-mongoose-server subset matches the
accepted no-replay deviation and the not-publishable embedded gate. The refresh rule cannot silently
retain a stale revision because `manifest.json` records the fetched revision and a test pins it against
`VENDORED.md`. Detail in the handoff report §7k/§7m.

**What.** `docs/skills/m19-skills/1/index.json` publishes the accepted Mongoose subset from the public
raw repository root. The skills README names the machine root and refresh rule; CanonicalSkillsTest pins
the contract, two selected tiers/paths, source revision and exact SHA-256 bytes. The changelog records the
new build/release endpoint. Generated projects remain offline snapshots and never fetch it.

**Why.** P2 review F4 proved the playground's proposed default root returned HTTP 404. Canonical content
is analyser-owned, so the stable raw analyser repository is the smallest root that preserves one source
of truth and already matches m19-skills/1's required `<root>/m19-skills/1/index.json` plus below-root
skill paths. replay-a-run is omitted by the accepted no-replay claim; embedded remains not publishable.

**Files.** Canonical index and README; CanonicalSkillsTest; changelog. Tracker/review/handoff disposition
is in the following metadata commit.

**Verified.** Focused canonical tests and full Maven suite 1,111/1,111 pass; strict docs and diff check
pass. After push the exact raw index returned 200, and the playground P2 retriever selected both files
from the live root with revision `6243a899774d591119559305a137ecf144819efd`.

**What the reviewer must still check.** Challenge raw `main` as the stable build/release root and the
decision to publish only load-audit-log + run-mongoose-server. Compare both hashes with analyser commit
`6243a89`; confirm the index paths stay below root and the refresh rule cannot silently retain a stale
revision. Independently run the playground retriever against the root. F4 still requires the playground
to adopt it as CANONICAL_ROOT and delete its manual relabelling flag; F5 then tests the actual zips.
