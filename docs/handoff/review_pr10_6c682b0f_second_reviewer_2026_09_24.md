# PR #10 at `6c682b0f` — second review

## Declared conflict of interest — read before weighing anything below

**I am one of the two implementing sessions.** My commits are in this PR: `ProjectRevealer` and its
`Surface`, the `confirmDelete` seam, `deleteSelected`, the `TrailingWhitespaceTest` evidence exemption,
and the hand-resolved `deleteCurrent` conflict that merged the stale-index guard with the injectable
predicate. `ProjectPanelChartLifecycleFrameTest` — the subject of finding R3 — was written by me.

So this is not the independent review the brief asks for. It is an author checking the other author's
corrections, including corrections to the author's own defects. Where I approve something I wrote, that
carries no weight; where I confirm a finding against my own work, it should be read as a concession
rather than as verification.

What I can honestly offer: everything below was **run**, not read, and the one new finding concerns code
I did not write.

## Verdict

**Approve the chart-lifecycle work.** Every claimed number reproduces exactly at the pinned head, the R1
fix is complete across all three call sites, and the verification machinery is the most rigorous in this
repository — I tried to break it and could not.

**The Linux `DesignSpotlightFrameTest` intermittent should not block this PR**, for reasons given in F2,
but it must stay open and it weakens the display gate as evidence generally.

**Two owner decisions remain**, one of them new (F1).

## What I ran, at `6c682b0f`, in an isolated detached worktree

| Check | Result |
|---|---|
| `mvn -q clean test` | **1938 / 0 failures / 0 errors / 78 skipped** → 1860 executed (counted from Surefire XML, not console) |
| `python3 tools/test_project_chart_review.py` | 5 tests, OK |
| `verify… --mode preflight` | 15 frame suites, 22 anchors |
| `verify… --mode display` | **79 tests, zero failures/errors/skips** |
| `verify… --mode mutations` | **22 controls**, each *green / named assertion red / restored green, bytes identical* |
| `mkdocs build --strict` | pass |
| `git diff --check` | clean; worktree clean after mutations — every source byte restored |

Every figure in the author's report reproduces. I found no discrepancy between claim and artefact — which
is worth stating plainly, because the previous round had one and I raised it.

## Confirmations (not new findings)

**R1's fix is complete.** `restoreGraphDefinitions` (`ui/MainFrame.java:5283`) is now the sole route to
`graphTabs.restore`, and all three call sites go through it: log open (`:3998`), import (`:4730`) and
project apply (`:5273`). It catches `IllegalArgumentException`, withholds every definition rather than
choosing a winner, and names the offending file. No automatic rename or winner-selection policy was
adopted, as the brief required.

**The refusal is mutation-covered**, by `global-refusal` and `global-preservation`, both targeting
`DuplicateGlobalChartsFrameTest#duplicateGlobalChartsAreWithheldAndLogLoadingCompletes`.

**The source-discovery check works. I tested it rather than reading it.** Removing
`DuplicateGlobalChartsFrameTest` from *both* CI lists and running preflight fails with:

```
AssertionError: ('CI frame suites differ from source', ['DuplicateGlobalChartsFrameTest'], [])
```

It names the omitted suite. `display_classes` (`tools/verify_project_chart_review.py:125`) also asserts
the execution list and the zero-skip guard list are identical, which is the specific gap R3 found. CI
file restored byte-identically afterwards.

**R3 was correct, and it was against my test.** `ProjectPanelChartLifecycleFrameTest` as I wrote it was
both dormant (in neither CI list) and hollow: it asserted only the outer tab title rather than the item;
its Cancel case explicitly tolerated the question never being asked
(`assertTrue(asked.isEmpty() || asked.size() == 1)`), which is not a Cancel test at all; and it seeded
`.fluxtion-analyser` as a *file* where `ConfigStore` uses `.fluxtion-analyser/config`
(`config/ConfigStore.java:21`), so the configuration was never loaded. The reviewer broke three behaviours
simultaneously and the suite stayed 3/0/0/0. Those were surviving mutations. The consolidation into
`ChartLifecycleReviewFrameTest` is the right correction, and its replacement Cancel case proves the real
modal was found and answered.

I have carried that lesson into PR #11: the same `.fluxtion-analyser` seed line appears in
`SilentActionDisclosureFrameTest`, where it is inert because the test sets `savedGraphs` directly, but it
is dead and misleading and I am removing it.

## Findings

### F1 — Owner decision, new: an ambiguous global config blocks *all* chart work, and recovery is manual

`GraphTabs.refuseDefinitions` disables every editing control — `New graph`, `Rename…`, `Close graph` and
`Delete chart` (`ui/GraphTabs.java:59,88`) — and `addGraph` returns null while a refusal is in force. So a
single duplicate pair anywhere in the **global** config prevents a person creating even a brand-new,
unrelated chart. The message directs them to close the analyser, hand-edit the config file, and restart.

This is coherent: the state is ambiguous, `SavedGraphMerge.merge` would throw on save, and allowing new
work risks entrenching the ambiguity. Log inspection remains available and the message says so. I am not
calling it a defect.

But the cost is real and falls on exactly the users the compatibility concern identified — people whose
profiles were made ambiguous by *shipped* releases, who did nothing wrong. Their first experience is a
chart pane they cannot use and an instruction to edit a file by hand.

**The question for the owner:** should `New graph` remain available while ambiguous definitions are
withheld, and is an in-app repair (rename one duplicate, with both definitions shown) wanted? The second
is a rename policy, which the brief explicitly reserves to the owner, so this review does not choose it.

### F2 — The Linux intermittent: disclosed, unexplained, and in my judgement not a blocker

Run 36022854847 failed `DesignSpotlightFrameTest.sourceViewportRefusesHiddenLinesAndAddReportsDepartures`;
run 36023872517 at `02604589` passed all 79 after a change the author states is diagnostic-only. I read
`02604589`: it adds `designGeometry(f, 3)` to an assertion message and touches no production code. The
author's own conclusion — that the later green run is *not* evidence of repair — is correct and properly
recorded.

**A hypothesis the report does not offer.** That test asserts which source lines fit the viewport: a
spotlight set spanning beyond it must refuse, and a set within it must succeed. Lines-per-viewport is a
function of font metrics, and Xvfb's fonts and layout timing differ from a developer machine's. So the
failure is more likely **environment-metric dependent than randomly intermittent** — which matters,
because a metric-dependent failure will recur deterministically on some runners and never on others, and
"it passed the second time" tells you nothing. The new `designGeometry` diagnostic should settle it on the
next occurrence; that is the right next step and it is already in place.

**Why it should not block:** it is in the design-spotlight source viewport, touches no chart-lifecycle
code, and both Linux runs passed every new chart suite. **Why it still matters:** the display gate is the
primary evidence for this PR, and a gate with an unexplained failure mode is weaker evidence than its
79/0/0/0 suggests. Do not close it on a green run.

### F3 — Minor: the refusal message's recovery path is untested

The message instructs the person to close the project (or the analyser), edit names in a named file, and
reopen. Nothing exercises that round trip — that after hand-editing, the definitions load and both
survive. The regression suite proves the refusal and the preservation, not the recovery. Given the
audience is users with legacy profiles, a test that edits the seeded file and reopens would close the loop.
Optional; the preservation assertions already prevent the dangerous outcome.

## What I could not verify

- **Anything on Linux or under Xvfb.** All my runs were macOS with a real display. F2 rests on reading CI
  logs and the test, not on reproducing the failure.
- **The visual correctness of anything.** `doClick()` and programmatic activation drive real components
  and their listeners, but not hit-testing, focus, z-order or whether a control is visible. A button
  rendered off-screen passes every test here, mine included.
- **My own contributions**, as declared at the top.
- The committed mutation evidence belongs to `32c33074`; I re-ran the controls at `6c682b0f` rather than
  auditing the older captures.

## Disposition

| Item | Disposition |
|---|---|
| R1 duplicate global definitions | **Fixed and verified** — single funnel, all three call sites, mutation-covered |
| R2 stale mutation anchors | **Fixed** — preflight validates all 22 anchors before Maven |
| R3 ungated/overstated frame tests | **Fixed** — consolidated; discovery check now prevents recurrence; finding was against my own work and was correct |
| Linux design-spotlight intermittent | **Open, non-blocking** (F2) — keep visible, do not close on a green run |
| Last-tab/placeholder policy | **Owner decision**, untouched |
| Ambiguous-config blocks all chart work | **Owner decision** (F1), new |

No required corrections. I did not merge, release, deploy, use a compilation key, or modify the
implementation. The CI file and all mutated sources were restored byte-identically and verified clean.

## Owner decisions — answered 2026-09-24, after this review

Recorded here so they are not lost in a chat log. None is implemented yet; each is work to schedule.

**F1 — ambiguous global config. Decision: `New graph` stays available, and the analyser offers an in-app
repair.** A duplicate pair must no longer block unrelated chart work. The repair is a dialog naming the
ambiguous charts and offering, per duplicate, **delete** or **rename**. This is the rename policy the
earlier brief reserved to the owner, and it is now granted — with the constraint that follows from the
whole round of work: the dialog must show what each definition contains before it is destroyed, and
neither option may be applied silently or as a default. Withholding the definitions remains correct until
the person chooses.

**Linux `DesignSpotlightFrameTest` intermittent. Decision: agreed, keep it open.** It does not block
shipping. A later green run is not a repair, and the new `designGeometry` diagnostic settles it on the
next occurrence.

**Last-tab/placeholder policy. Delegated to this review; decided here on precedent, not taste.**

The placeholder should stay — an empty tab strip reads as breakage — but it **must not be persisted**.
The codebase already draws this line and simply does not draw it consistently:

- `GraphTabs.bind` wraps its placeholder in `restoring = true`, with the comment *"The placeholder tab a
  fresh binding opens is STRUCTURAL, not a user edit"*. Not persisted.
- `doRestore`'s placeholder (`ui/GraphTabs.java:434`) runs inside `restore`'s guard. Not persisted.
- `deleteConfirmed`'s placeholder (`ui/GraphTabs.java:600`) is **unguarded**, so it fires the change
  listener and is written to the profile.

So deleting your last chart leaves a saved `Graph N` the person never created — a phantom definition
produced by an act of deletion, which is the opposite of what they asked for. The decision is to make the
delete path match the other two: create the placeholder structurally, suppressed, and let it persist only
once it acquires content like any other chart.

This is deliberately the narrower of the two available answers. Removing the placeholder entirely would
also solve the phantom, but it changes what a person sees after a delete, and nothing in the evidence
calls for that.
