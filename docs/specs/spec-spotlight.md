# Spec — spotlight: the tutor points at the thing on screen

**Status:** IMPLEMENTED 2026-09-17 on branch `fix/m46-agent-api-closure`, awaiting independent review
(`docs/handoff/report_m64_spotlight.txt`) — see *As built* at the end, which records **one assumption of this
spec that was wrong** (D-SP1/D-SP5: the screenshot does NOT paint the glass pane) and how it was corrected.
PROPOSED 2026-09-16 (owner question: *"how difficult would it be to create a callout in a separate
window that points to the item the guided-start runbook wants to highlight?"*). **Tracker:** [tracker.md](tracker.md) ▸ M64.
**Related:** [spec-guided-start.md](spec-guided-start.md) (D-G2 — *the tutor POINTS; the screen PROVES* — this is the
pointing), [spec-trust-structure.md](spec-trust-structure.md) (D-T3: a spotlight is evidence about WHERE, never
testimony about WHAT), M35.9 (no dialog ever fires at an empty screen — a spotlight is not a modal and never blocks).

## The idea, in one sentence

An agent driving the UI for a person can already open, filter, select, draw and screenshot; what it cannot do is
**point**. `spotlight` lets it say *"this, here"* — a dimmed screen with a cut-out around one named thing, an arrow,
and one short caption — and then check, with the screenshot verb it already has, that it pointed at the right thing.

## Is it a good idea? — the honest read

**Yes, narrowly.** The guided-start skill's one rule is *"show, do not tell"*, and its measured failure mode is the
tutor summarising instead of showing. Today "showing" means "the view is on screen somewhere in a 2000-pixel window";
a person new to the tool does not know where to look, and the tutor cannot tell them except in prose — the very
thing the rule forbids. A spotlight closes that gap with the smallest possible surface: one verb, one overlay, no
new state that survives it.

**The risk is over-building the addressing.** Every panel could expose every widget; that is a registry nobody
maintains and a vocabulary no skill remembers. So the vocabulary is **fixed and small** (D-SP2), grown only when a
beat of a runbook needs a target it does not have — and each addition names the beat that needed it.

**Not a good idea as a general "annotate the UI" feature.** A spotlight is transient by construction (D-SP4): it is
never saved with a graph, never exported, never in a report. Pointing is a *tutoring* act; the artefacts that carry
findings (flags, notes, reports) already exist and stay the only durable form.

## D-SP1 — a glass-pane overlay first; a separate window only if a beat needs one

Two ways to draw over the app:

| form | pros | cons |
|---|---|---|
| **the frame's glass pane** | inside the frame: no z-order, HiDPI or multi-monitor surprises; **paints into the `screenshot` the tutor takes to verify itself**; ~1 day | cannot point outside the frame (a menu popup, a dialog, another window) |
| **a separate translucent always-on-top window** | can point anywhere on the desktop | window management: above the frame, below dialogs, following moves and resizes, hidden with the frame; per-pixel translucency is compositor-dependent on Linux; ~2 days and the part most likely to misbehave on a machine we do not own |

**Decision: glass pane.** Every beat in the shipped runbook points at something INSIDE the frame (a tab, the
topology, a record's node list, the coverage shading, a chart). The window form is recorded here as the extension
and is taken only when a runbook beat is written that must point outside the frame; that beat is the evidence.

## D-SP2 — a fixed target vocabulary, named as a person would say it

`spotlight {target, caption?, clear?}`. `target` is one of:

| target | resolves to | why it exists (the beat) |
|---|---|---|
| `tab:<summary\|source\|graph\|topology\|reports\|assistant>` | the side tab header | every beat: "look here first" |
| `records` · `records:row:<recordIndex>` | the records table; one row (revealed first, as `goto` does) | beat 1 — "the order in the list IS the order it ran" |
| `detail` · `detail:node:<instanceId>` | the record detail; one node's block in the logical view | beat 1 — "one record's node list" |
| `topology` · `topology:node:<instanceId>` | the canvas; one node's box (the canvas already knows its bounds) | beats 1 and 2 — the graph, then a node that never ran |
| `coverage` | the coverage panel/verdict | beat 2 |
| `graph` · `graph:note:<n>` · `graph:series:<label>` | the chart; a numbered note's rule; a series' legend entry | beat 3 |
| `project` · `project:<row>` | the Project panel; one of its rows (log, graph, processors, roots) | "what is in force" |
| `toolbar:<open\|flag\|explain\|follow>` · `status` | a toolbar button; the status line | the pairing verdict lives in the status line |

Rules: a target that is **not on screen** (its tab is not selected, the row is filtered out) is first **revealed**
with the same moves the existing verbs use (`showTab`, `reveal`), then lit; a target that cannot be resolved is a
plain error naming the vocabulary, never a spotlight on nothing. `caption` is at most one line; it is the tutor's
words, so it is **testimony**, and the overlay renders it in a style that says so (the same muted "assistant" style
the topology callout uses), never in the app's own colours. One spotlight at a time; a new one replaces the last.

## D-SP3 — the overlay is dumb; the resolution is pure

`SpotlightTarget.resolve(name) → Rectangle in frame coordinates` is a pure function over the panels' exposed bounds
and is unit-tested headless for every vocabulary entry, including the "not visible" and "unknown" cases. The
overlay itself does four things and nothing else: dim the frame, cut the target out, draw an arrow from the
caption to the cut-out (the caption sits in whichever quadrant has room), and go away. It repaints on frame resize
and vanishes on: any click, Escape, `spotlight {clear: true}`, and **any verb that changes the view** (open, filter,
goto, graph, topology — a spotlight that outlives its context points at the wrong thing, which is worse than none).

## D-SP4 — transient by construction

No field in `config`, no key in the profile, nothing in a saved graph, nothing in a report, nothing in `context`
except `spotlight: {target, caption}` while one is showing — so the tutor's own verification loop
(`context` → `screenshot`) can confirm what is lit. A restart shows no spotlight. This is the same rule the topology
canvas applies to its record marker ("held as its own transient field so it can never leak into a saved graph").

## D-SP5 — the skill changes, the verbs do not multiply

`guided-start` gains one line per beat: *spotlight the thing you are about to talk about, then say one sentence,
then take the screenshot that proves the spotlight is on it.* No other verb changes. `screenshot` already paints
the glass pane because it captures the window the app painted (M35 series, "painted by the app").

## Acceptance

- [ ] Every vocabulary entry resolves to non-empty bounds when its target is visible, and to a typed "not visible /
      unknown" answer otherwise — headless tests, one per entry, plus a negative control for a misspelt target.
- [ ] A spotlight on a filtered-out row reveals the row first (the `goto`/`reveal` path), then lights it.
- [ ] `open`, `filter`, `goto`, `graph`, `topology` each clear a live spotlight — one test per verb, and a mutation
      check that removing the clear from one of them turns exactly its test red.
- [ ] `context` reports the live spotlight and nothing after it is cleared; the profile, saved graphs and reports
      never contain one (grep the persisted forms in a test).
- [ ] The display suite (`ui-frame` job) lights `topology:node:<id>` on a real frame and the screenshot verb's
      image has the cut-out over that node's bounds — checked by pixel sampling inside and outside the cut-out, not
      by eye.
- [ ] The guided-start skill's three beats each spotlight before they speak; a re-run of the docs-site prompt by a
      context-free client (the held-out run the spec-guided-start tracker item still owes) uses it without being told
      how.

## Effort

**One day** for D-SP1–D-SP5 with the glass pane and the eight target families above; half a day more for the
display test and the skill edit. The separate-window form, if a beat ever needs it: one to two further days, and
the first thing to test on Linux.

## Open for the owner

1. Should a spotlight also **scroll** the records table / source pane to its target, or only reveal the tab? (Proposed:
   scroll, because "here" that is off-screen is not here.) — **BUILT AS PROPOSED**: it scrolls, and centres a topology
   node only when it is not already fully in view (a jump nobody needed loses context).
2. Is a one-line caption enough, or should a spotlight carry the beat's whole sentence? (Proposed: one line; the
   sentence belongs to the tutor's chat, where it is clearly testimony.) — **BUILT AS PROPOSED**: one line, at most 160
   characters; a longer or multi-line caption is refused with that reason.

Both were taken as proposed because the owner asked for the implementation without answering them; either is a small
change if the answer is different.

## As built (2026-09-17)

**One assumption above was wrong, and the spec is corrected here rather than quietly diverged from.** D-SP1's table
says the glass pane *"paints into the `screenshot` the tutor takes to verify itself"* and D-SP5 says *"`screenshot`
already paints the glass pane because it captures the window the app painted"*. **It does not.** The verb paints the
frame's CONTENT PANE (or one panel, for a scoped shot); the glass pane is a sibling of the layered pane under the root
pane and is not part of either. The first real-frame run of the display test showed it exactly: brightness outside
the cut-out was `696 → 696` — an undimmed image of a window that was visibly dimmed on screen. The verb now
**composites a live spotlight onto whatever it painted** (`SpotlightOverlay.paintOnto`), translated so the cut-out
lands on the pixels it covers on screen, and `SpotlightFrameTest` holds it: with that one call removed, the test goes
red with those numbers. The consequence is a good one — the tutor's verification shot is now a statement about the
real overlay rather than an accident of what `paint` reaches — but it was not free, as the spec claimed.

**It is a sixteenth verb.** The action surface is held down by four tests whose rule is that it does not grow *for a
concept an existing verb already names*. Nothing points: `goto` and `topology` SELECT, `screenshot` RECORDS. So this
clears the same bar `handoff` did (tracker ▸ Decisions), and each guard test now says so.

**The shape D-SP3 asked for.** `SpotlightTarget` owns the vocabulary and `resolve(name, Surface)` — parse, reveal,
measure — with the frame behind a three-method interface, so all of it is tested headless with a map for a surface.
`SpotlightGeometry` is the caption placement as arithmetic. `SpotlightOverlay` is dumb: dim, cut out, arrow, caption,
go away. What ends a spotlight is ONE list, `SpotlightTarget.VIEW_CHANGING_VERBS`, applied in one place
(`ActionExecutor.render`) before the verb runs — so the spec's mutation check holds literally: remove `goto` from it
and exactly `gotoPutsItOut` goes red.

**What each target resolves to, where the spec left room.** `coverage` lights the Topology tab's own status line —
that is where the pairing/coverage verdict is stated for a person; there is no separate coverage panel.
`project:<row>` lights a SECTION of the Project panel (*Audit log*, *Graph*, *Event processors*, *Source roots*).
`graph:note:<n>` uses the number printed on the note's pin, by walking the same column map the chart paints from.
`records:row:<n>` reveals a filtered-out record through `goto`'s own reveal path, called directly — the public verb
would put out the spotlight about to be lit.

**A refused spotlight changes nothing.** An unknown or not-visible target is an error, and a spotlight already lit
stays lit: a failed request is not a reason to stop pointing at the last thing.

**First-show layout.** A tab selected a moment ago has no size until layout runs. The reveal validates the tab pane
synchronously before measuring, and the adapter re-measures once more after the queued events drain (and on every
frame resize); a target that can no longer be measured puts the spotlight out.

### Acceptance, as met

- [x] Every vocabulary entry resolves, or gives a typed not-visible / unknown — `SpotlightTargetTest` (59 cases, a map
      for a surface), plus the misspelt-target negative controls; and on the BUILT JAR, `tools/verify-m64-spotlight.py`
      lights every family on a real window.
- [x] A filtered-out row is revealed first, then lit — the same script, after `filter {text: …}`.
- [x] `open`, `filter`, `goto`, `graph`, `topology` each clear a live spotlight — `SpotlightEndsWhenTheViewChangesTest`,
      one test per verb, **mutation-checked**; `screenshot`, `context` and the rest are pinned as NOT clearing it.
- [x] `context` reports the live spotlight and nothing after; nothing persists one —
      `SpotlightIsNeverPersistedTest` checks the CODE (no package that persists anything may know the word), which is
      stronger than grepping one saved file.
- [x] The display suite lights `topology:node:<id>` on a real frame and pixel-samples the screenshot verb's image —
      `SpotlightFrameTest`, added to the CI `ui-frame` job and to its fail-if-skipped guard.
- [~] The guided-start skill spotlights before each beat speaks — **done** (and written so a client on an analyser
      older than the verb simply carries on). The **held-out re-run by a context-free client is NOT done**: it is the
      run spec-guided-start's tracker item already owes, and it needs a person and a fresh client.
