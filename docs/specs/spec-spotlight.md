# Spec — spotlight: the tutor points at the thing on screen

**Status:** IMPLEMENTED 2026-09-17 on branch `fix/m46-agent-api-closure`, awaiting independent review
(`docs/handoff/report_m64_spotlight.txt`); **extended the same day by D-SP6 and D-SP7** (owner direction: several
spotlights and callouts at once; the guidance in the GENERAL assistant guidance, not one skill —
`docs/handoff/report_m64_6_multi_spotlight.txt`) — see *As built* at the end, which records **one assumption of this
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

## D-SP6 — several at once, because a finding is a relation (added 2026-09-17, owner direction)

D-SP2 closed with *"One spotlight at a time; a new one replaces the last."* That was right for a tour — each beat
points at one thing — and wrong for a diagnosis, where the thing to be shown is usually a RELATION: this node feeds
that one, which never logged, in the cycle on this row. The owner, on seeing the verb work: *"in normal operation an
LLM can explain a result by spotlighting specific areas with callouts … this is useful"*, then *"support multiple
spotlights and callouts"*. **That sentence of D-SP2 is superseded by this section; the rest of D-SP2 stands.**

- `spotlight {target, caption}` is unchanged and still REPLACES what is lit.
- `spotlight {targets: [{target, caption?} | "<target>", …]}` lights a SET — at most **six**
  (`SpotlightVocabulary.MAX_LIT`; bounded, because past six nothing is being pointed at). Each entry carries its own
  one-line callout; a top-level `caption` beside a list is refused (it would belong to none of them), as are an
  unknown field in an entry and a target named twice.
- **A set is all-or-nothing** (the canvas's validate-before-mutate rule). Every name is parsed before the surface is
  touched; then each is revealed and measured, and after each reveal every EARLIER member is measured again, because
  revealing one target can hide another (a topology node and a chart note live on different tabs). Two things that
  cannot be on screen together are refused **naming the pair** and saying what to do instead (*light them one after
  the other*). It is the same pure function shape as `resolve` — `SpotlightTarget.resolveAll(names, Surface)` — and
  tested headless with a surface that has tabs.
- `{add: true}` keeps what is lit. A target already lit is re-lit in place (same number, new callout), never twice.
  The bound applies to the union. A standing spotlight that the new target's reveal takes off screen **goes out**, and
  the echo's `wentOut` names it.
- `{clear: true}` puts all out; `{clear: true, target}` exactly one.
- **Numbered.** With more than one lit, each cut-out carries a number badge and its callout is tagged
  *assistant · n*; the echo and `context` carry `n`. A number is kept for the life of its spotlight — putting one out
  does NOT renumber the rest, and a new one never reuses a spoken number — because the chat that named them
  (*"2 never logged"*) has already been read. Numbers restart when the set is replaced or emptied.
- **Callout placement** is `SpotlightGeometry.layout`: each callout is tried on the same four sides in the same
  order as the single rule, and the first side covering NO cut-out and NO callout already placed wins; when every
  side covers something, the least-covering side wins (overlapped can be read; off screen cannot). With one spotlight
  it is exactly the single rule — asserted.
- **One shape, learnt once.** The verb's echo and `context` both say `spotlight: {lit: [{n, target, caption?}]}` (the
  echo adds `bounds` per entry). D-SP4's `spotlight: {target, caption}` is superseded — the verb was unreleased, so
  there was no client to keep compatible.
- A refused call lights nothing new. What was lit STAYS — unless the attempt's own reveal took it off screen, in
  which case it goes out and the refusal says so. (As first built a refusal never re-measured; by reading, a failed
  `topology:node:<typo>` sent from the Graph tab would have left a chart note's spotlight painted over the Topology
  tab. Not reproduced before it was fixed — the new behaviour is what is tested.)

**Amended after review (2026-09-17) — two places where the code did not do what the bullets above say.**

- *"A number is kept for the life of its spotlight … a new one never reuses a spoken number."* The first
  implementation derived the next number from the CURRENT maximum, so it held only while the highest member stayed
  lit: light 1 and 2, put out 2, add another, and the newcomer was "2". The overlay now keeps a **high-water mark**
  that restarts only when the set is replaced or emptied (by `clear`, by putting the last one out, or by a
  re-measure retiring the last one). My own test removed the MIDDLE member, which cannot expose this; the
  regression tests remove the highest, and retire it by re-measure.
- *"A set is all-or-nothing … every name is parsed before the surface is touched."* True of `resolveAll`, and NOT
  of the verb: the executor revealed a `records:row` through `goto`'s path — which relaxes the filter and changes
  the selection — BEFORE the frame parsed the rest of the set or checked `add`'s bound. So
  `{targets: ["records:row:15", "not-a-target"]}` was refused and had still erased the person's filter; so had a
  seventh target added to six. The rule is now one pure function, `SpotlightTarget.precheck(requests, litNow)` —
  the grammar, EVERY name, and the bound over the union — applied by the executor before its first reveal (and
  again by the frame). **The line, stated:** a call that is WRONG touches nothing; a call that is well-formed but
  cannot be shown (a node the graph does not have; two things on different tabs) may have brought another view
  forward finding that out, and says so. The first is a guarantee; the second is what "revealed first" costs.
- **Re-review R5 moved one case across that line, correctly.** I had called an out-of-range `records:row:<n>`
  "well-formed — finding out is the reveal". Whether record 99999 EXISTS is a fact about the store, knowable
  without the screen; and the reveal did not merely fail to find it — `goto` CLAMPS an index, so the call relaxed
  the person's filter, selected the LAST record, and then refused mentioning neither. `precheck` now takes the
  open log's record count and refuses such a row first, saying the range and that nothing was changed. `goto`'s
  clamping is `goto`'s contract and is untouched. What remains on the "may move the view" side is only what truly
  needs the screen: a node the graph does not have, a note a chart does not have, two things on different tabs.

## D-SP7 — *when* to point is general guidance, stated once and present at every entrance

As first built, the only text telling an assistant to point was the verb's own description and the guided-start
skill. So an assistant diagnosing a real log had the verb and no reason to reach for it. `SpotlightVocabulary.GUIDANCE`
is one paragraph — point before you explain; one thing or a numbered set; light AFTER the view-changing verbs; a
callout is your words, shows WHERE, is not evidence and is never saved; the durable forms are flags, chart notes and
reports; point when the person would otherwise have to hunt, not for every sentence — and it is printed at **every
way an assistant arrives**: the in-app assistant's action manifest (which also gains the verb and its targets: until
now the built-in assistant could run `spotlight` but was never told it existed), the copy-prompt REST manifest, the
MCP bridge's server `instructions`, and (conditionally — the copy path may have no actions) the system prompt.
`SpotlightGuidanceIsAtEveryEntranceTest` holds all four; a fifth entrance belongs in that test.

For PEOPLE, the user guide gains **Ask it to show you** (what to say; the four things worth knowing; one spotlight
and a four-callout finding, each in the light AND the dark theme, generated by `capture-docs.py --spotlight` under
the isolated home), and the in-app help gains the same paragraph.

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

## After review (2026-09-17) — what the two reviews changed, and what they did not

- **`graph:series:<label>` is EXACT** (first round, M64 F1). It fell back to `startsWith` "because an external
  series' entry carries a suffix the caller has no reason to know about" — so `graph:series:quote` lit
  `quotePublisher.spread` and echoed the invented name as lit, and of two series sharing a prefix it took the
  first drawn. A pointer at a guessed target is what D-SP2's closed vocabulary exists to refuse. Now: the exact
  label, or the exact label plus the ONE suffix the legend itself writes (`GraphPanel.EXTERNAL_SUFFIX`), and
  nothing looser; the bare label means the audit series when both exist. `GraphPanel.legendIndexOf` is pure and
  tested headless; seen red with `startsWith` put back (3 of 5).
- **…and it must name exactly ONE** (re-review R4). My answer to F1 claimed that with exact matching "no label is
  ambiguous". False: the same external spec can be given twice, and a formula's label is free text that may itself
  end in the legend's suffix, so two legend rows can read identically — and "the first" was still a guess. More than
  one candidate at the winning tier (exact, else exact + suffix) is now a refusal that SAYS there are two and to
  redraw with distinct labels. `GraphPanel.legendMatches`; three headless regressions; both reviewer reproductions on
  the built jar; seen red with "first match" put back.
- **Numbers are a high-water mark** (M64.6 F1) and **a call is validated WHOLE before any reveal** (M64.6 F2) — see
  D-SP6, amended in place below its bullets.
- **NOT changed here: the `coverage` target's name.** Both reviews say it misleads (it lights the PAIRING line), and
  they are right. It is tracker M64.9, after the merge, with M64.8 — the guided-start skill names the target, and
  no skill is touched on this branch.

## M64.8 — a runbook that finds a fault class ends in a spotlight (owner direction 2026-09-17; not built)

The strongest use of the verb is not the tutor but the **runbook**: a runbook written to find one class of fault
walks the log, and when it finds the fault it spotlights the evidence — the row, node or series it matched — with a
caption that quotes what it matched on. "Here is what I found" becomes "here it is", and a new user learns where that
class of fault shows up in the tool at the same moment they learn they have one.

Two rules make the pattern safe in front of someone who cannot tell a demo from a diagnosis:

1. **The pointer follows the evidence, not the script.** A runbook step lights the target it actually matched
   (`records:row:N`, `topology:node:<name>`, `graph:series:<label>`), and its caption is built from the match. A step
   that lights the same place whatever it found is a demo. D-SP6's all-or-nothing rule already helps: a target that
   does not exist lights nothing.
2. **Not finding the fault is a first-class outcome.** The step says what it checked and that it found nothing, and
   lights nothing. The temptation in a runbook is to point at something anyway; resist it in the skill text.

**Where it lives.** One canonical skill in the corpus that shows the shape — filter to the class of fault, confirm with
`read`, then `spotlight` the matched target with a caption built from the match — under `docs/skills/common`, beside
`guided-start`, because it is not Spring- or Mongoose-specific. Pinned by the usual two commits (bytes, then the
`m19-skills/2/index.json` revision + sha256), then vendored into the playground's starter by `scripts/vendor-skills.mjs`
so every generated template project carries it. A project's own runbooks (which the profile only points at, M38.1) are
where authors apply the shape to their own fault class; the skill is what they copy.

**Not this branch.** It is a third skill edit and re-pin; it follows the merge, not precedes it.

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

**It is the fifteenth verb — the ONE verb this work adds.** (This paragraph said "a sixteenth" on the day, when
M48.7's `handoff` was briefly a verb of its own; that was folded into `open` before it shipped.) The action surface
is held down by four tests whose rule is that it does not grow *for a concept an existing verb already names*.
Nothing points: `goto` and `topology` SELECT, `screenshot` RECORDS — so `spotlight` clears that bar, and each guard
test says so. `handoff` did not clear it, which is why it is on `open` (tracker ▸ Decisions).

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

### Acceptance, D-SP6 / D-SP7 (2026-09-17)

- [x] The input grammar, the bound, duplicates, per-entry captions — `SpotlightSetTest` (11, headless).
- [x] All-or-nothing; a misspelling anywhere touches nothing; the cannot-be-together pair is named —
      `SpotlightSetTest`, **mutation-checked** (drop the re-measure of earlier members → exactly
      `twoThingsThatCannotBeOnScreenTogether…` red, with the misleading *"is not in the graph"* it would have said).
- [x] Numbering, re-light in place, put one out without renumbering, re-measure says what went out —
      `SpotlightOverlayTest` (5, headless).
- [x] Callout layout: equals the single rule for one; neighbours and a stack cover neither a cut-out nor each other;
      crowded stays on screen — `SpotlightGeometryTest` (+6).
- [x] On a real frame: two nodes lit together, BOTH cut-outs byte-identical to the unlit screenshot, the ground dimmed
      once not twice, numbered in `context`, a bad set refused with the two still lit, `add` and `clear + target` —
      `SpotlightFrameTest` (+1), **mutation-checked** (cut only the first hole → red, second hole tinted).
- [x] On the built jar — `tools/verify-m64-spotlight.py`, now 64 checks (+17).
- [x] The guidance at every entrance — `SpotlightGuidanceIsAtEveryEntranceTest` (5).
- [x] Dark theme **seen**: `spotlight-dark.png` and `spotlight-findings-dark.png`, generated and read. (Open note of
      the first report, closed.)
