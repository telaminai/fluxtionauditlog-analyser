# Proposal — readable surfaces: view models as the machine-readable statement of the UI

**Status:** proposed 2026-09-28, from the view-model spike (#55) and its second element (#58).

## The proposition

The spike justifies view nodes by the defects they catch. That is real — #55's Finding 1 shows two gates that no
frame test can witness — but it is the smaller half.

> A view model is **the only machine-readable record of what a surface states.** Everything else the analyser
> publishes describes what the *session knows*, which is a different thing and can differ.

## Three layers, and they answer different questions

This distinction is the substance of the proposal, and getting it wrong is how the idea gets oversold.

| layer | answers | where it lives today |
|---|---|---|
| **what the session knows** | *has the log been re-read since it changed?* | `context`, hand-composed per field |
| **what a surface was told to state** | *does the record pane currently say so?* | **nowhere** — view models (#55/#58) |
| **what was actually drawn** | *is that sentence visible, or covered by a footer?* | **nowhere** — the display list (#53) |

They come apart in practice, and each gap has already produced a shipped defect:

- **Knows vs states.** #55's `scanPending` gate: the session knows the new count and the old findings; a surface
  told both states something the session never believed. Invisible to a frame, recorded in the audit as fact.
- **States vs draws.** #48: the chart was told to draw axis labels and did — then the explanation footer filled
  its background over them. Every layer above the pixels was correct.

## What is actually missing, stated precisely

`context` already publishes a great deal, including the identity verdict (`log.identity.state`). So the gap is
**not** "an agent cannot see the state". It is narrower and, I think, more interesting:

1. **`context` is a second composition site.** The identity verdict is assembled there in three branches, from a
   different source than the banner's. Two places compose the same fact from different inputs — which is the
   shape rule 9 exists to forbid, surviving because `context` is not thought of as a surface. **It is one:** it is
   the surface an agent reads. With a view published in the snapshot, `context` becomes a projection rather than a
   composition, and the second site goes.
2. **Nothing says what a surface currently states**, only what the session knows. An agent cannot distinguish
   *"the analyser knows the file changed"* from *"the record pane is telling the user so right now"*.
3. **Nothing says what was drawn at all.** Which is what I actually needed most — see the honest assessment below.

## Decouple the two values, because they have different costs

The spike ties publication to rendering: a view is emitted, an effect is requested, backends draw, the audit
records it. For readability none of that is needed.

> **Proposal: a view may be published in the snapshot without requesting a render effect or auditing the change.**

- **Readable** — the view is in the snapshot and therefore in `context`. Cost: the node's invocation line.
  #58 measured a non-changing element at **0 records, +3.3% bytes**.
- **Audited** — the render answer and the field diff are in the log, so *what a surface was told, and when* is
  citable in a report or an evidence bundle. Cost: per change.

One decision becomes two, and the cheap half can cover far more of the app than the expensive half is worth.

## Where it is useful — my assessment

The test I would apply per element:

> **Could a person be wrong about what this element says, in a way that matters?** And **would you cite it?**

| surface | readable | audited | why |
|---|---|---|---|
| status line | ✅ | ✅ | built; multi-input, ordering history |
| identity banner | ✅ | ✅ | built; three surfaces, one verdict |
| pairing verdict | ✅ | ✅ | the sentence a reader most often quotes about whether a graph fits a log |
| `showing N of M` + active filter | ✅ | ✅ | the commonest way to be wrong about what you are looking at |
| chart *scope* (window, pinned, `emptyReason`) | ✅ | ✅ | already in `graphScopes` — would become a projection instead of a composition |
| Reports tab list | ✅ | — | quoted; changes rarely; no ordering history |
| Project panel rows | ✅ | — | already `context`-derived; folding it removes the second composition |
| topology context (`All (77) ▸ focus (21)`) | ✅ | ✅ | the walk work (M69) will cite it |
| coverage summary | ✅ | — | stated once, quoted often |
| **record table rows** | ❌ | ❌ | bulk. By value absurd; by reference breaks Finding 2, since the backend then reads the store |
| text fields, scroll, tree expansion, drag | ❌ | ❌ | direct manipulation; a keystroke round-tripping a graph is the wrong shape |
| divider positions, selected tab, focus | ❌ | ❌ | nobody would replay it; pure audit cost |
| chart interaction at frame rate | ❌ | ❌ | the chart's *definition* is a view; dragging its window is not |

**The pattern:** a surface usually has a small statable core inside a large uninteresting body. The record table's
rows are out; its `showing 21 of 21` is in. That, rather than "which panels", is the unit.

## The honest part: what I actually needed today, and it was not this

Across a full day driving this app I reached for a screenshot repeatedly. Going back over those moments:

| what I needed to know | which layer answers it |
|---|---|
| were the chart's x-axis labels visible, or covered? | **drawn** |
| did two spotlight callouts overlap? | **drawn** |
| was callout 4 clipped by the crop? | **drawn** |
| did the chart render identically after a refactor? | **drawn** |
| did the closing step reach the window edge? | **drawn** |
| what does the status bar currently say? | *states* |

**Five of six were the drawn layer, which view models do not address.** That is #53's display list, and on this
evidence it is the higher-value readability work of the two. This proposal should not be read as arguing
otherwise: it argues the *states* layer is missing and cheap, not that it is what I was short of.

The pairing is what makes either worth much. "The chart was told to show a threshold rule at 95" plus "a dashed
line was drawn at y=157 with the label *a threshold rule (95)*" is a complete, checkable account with no pixels in
it. Either alone leaves the obvious question unanswered.

## Risk: audit selectivity is the thing to protect

The analyser's strongest property is that **absence from the log means something**. If every surface writes on
every change, the session log becomes a trace, and a trace is what most application logs already are and why
nobody reads them.

#58 measured a non-changing element at zero records, which is reassuring — but it measured **two** elements, one
of them deliberately the easy case. Nobody has measured what a graph with twenty view nodes does to the
signal-to-noise of the session audit. **That, not bytes, is what would quietly spoil the product's best property**,
and it should be measured before the readable set is widened much beyond the audited one.

## What step 1 landed, and one thing it proved wrong

Step 1 is done (`surfaces` in the snapshot and in `context`, its own section). Two findings from doing it:

**The divergence is real and is now legible.** `PublishedSurfacesTest#whatIsKnownAndWhatIsShownDiverge`: after an
append with no re-scan, `log.records` is 30 and `surfaces.statusLine.records` is 25. The session knows 30; the
person is being shown 25; the gate is working correctly. Before this, no reader of the snapshot or of `context`
could tell those apart — which is the claim above, pinned as a test rather than asserted in prose.

**Step 2 as written was wrong.** `context`'s identity has three branches, and the first is not a duplicate of the
banner's: when Follow is off it *observes the file at this request* (D-E6), which is a fresher fact than the
verdict the banner holds, and it carries `readsSuspended`, which the view does not. Retiring it would lose a real
answer. The honest version of step 2 is narrower: **branches 2 and 3 are candidates; branch 1 is a separate
question the session does not currently answer.** `surfaces.identityBanner` now sits beside `log.identity`, and
the two disagreeing is itself informative — an agent can see that what the session freshly observed is not what
the screen is showing.

That comparison is deliberately **not** computed in `context`. A divergence note assembled there would be exactly
the second composition site this argues against; if it is worth stating, a node should state it.

## Suggested order

1. ~~**Publish the two existing views in the snapshot and `context`**~~ — done; see above.
2. ~~**Fold `context`'s branches 2–3 into the published view**~~ — done: both project `surfaces.identityBanner`, which
   now states `NOT_ASSESSED` because the store's read-through assessment reaches the session on `LogOpened`. "Observed
   at this request" (branch 1) is written up separately, in [`identity-observation-as-a-fact.md`](identity-observation-as-a-fact.md).
3. **The `showing N of M` + filter core**, as the first readable-only element.
4. **Measure the audit's signal-to-noise**, not just its volume, before going further.
5. **Charts last, and with #53**: a `ChartView` alone would not have caught any of the five drawn-layer questions
   above.
