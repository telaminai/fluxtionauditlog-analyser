# Review — `docs/proposals/readable-surfaces.md` (at `5548ce9b`, PR #58), 2026-09-28

**Reviewer:** Claude (analyser session). Findings only: the document is not rewritten, and nothing was pushed to
`proto/view-model-nodes-2`. The code the document cites was read at `5548ce9b`.

## Verdict

**Adopt with a narrower scope.** The three-layer framing is right and worth keeping. The decoupling proposal, as
written, erases the layer it is built on. The per-element table moves most of its rows into the Fluxtion session
processor without saying that this means moving **ownership** of that state there. Adopt:

1. the three layers, with one correction: layer 2 exists only where there is a render answer (below);
2. **step 1, reshaped:** publish each view node's *last emitted* view, and its render answer, in the snapshot and
   `context`. That *is* layer 2, costs no new node and no new audit, and is what an agent cannot see today;
3. step 4, with the gate made concrete (§5).

Do not adopt step 2 (it retires nothing; §2), readable-only views as *nodes* (§3), or the table as a work list (§4).

## Is the balance right about what goes into the generated orchestration?

The owner asked for an eye on this, and it is where the document is weakest. It never states a rule, and the table
implies that everything statable becomes a node. The rule this codebase already has is rule 9: the processor owns
**session state, and the decision of WHEN it is stale**. A view earns a node when it has one of these:

- a WHEN: change detection, gating, ordering, a ticket;
- consistency across facts (the status line's two gates);
- a render that must be answered and audited.

A view that is a **pure function of the snapshot** has none of them, and it belongs in `SessionSnapshot.of`, as a
projection. `IdentityBannerView.of` is already such a pure static. Put it in a node, and every trigger pays an
invocation line (§5 of the PR #58 review: 70 B per append per node on `OpenLog`). That spends exactly the audit
selectivity the document says to protect. So:

| kind | where it lives | audit |
|---|---|---|
| gated, changed-only, rendered | node + effect + answer (as #55/#58) | the diff and the answer |
| derived, readable, never rendered | projection in `SessionSnapshot.of` | none, and it must not claim to be layer 2 |
| state the session does not own yet (filter, chart scope, topology focus, report list) | **not a view problem**: first decide whether the session should own it | — |

The last row is the balance question in its real form. It is taken up under "The strongest argument not made" below.

## 1. The three layers

**Real, with one mislabelled example and one missing layer.**

- **Knows vs states, `scanPending`: the example is not a shipped defect.** #55's RESULTS Finding 1 says so itself.
  In the frame the old `evidencePending` gate held, and a half-landed view "would be drawn … then replaced … before
  any repaint. No frame test sees it." The harm it names is "a false statement in the evidence", and that exists
  **only because views are audited**. The gap is real, but the defect is one the view-model mechanism would
  introduce, not one the app shipped. The claim "each gap has already produced a shipped defect" is true for one of
  the two.
- **States vs draws, #48: accurate.** The fix (`b3e03d6d`) records it. Labels were drawn at `h - 6`, inside the
  explanation footer, and the footer filled its background over them. It shipped in 1.24.0.
- **Two layers collapse in the decoupling proposal.** See §3.
- **A missing layer: what the person or the config *defined*.** A saved chart's definition, the filter a person set,
  a report's sections and a saved focus are not what the session knows, what a surface was told, or what was drawn.
  They are authored state, owned by `AppConfig` and the panels. Half the table's rows are this layer. It matters
  because the session processor does not own it, so a view of it is not a projection of anything the processor has.

## 2. The second-composition-site argument

**Mostly inaccurate. The document's own mid-way revision is right, and it undercuts more than the document admits.**

`context`'s `log.identity` block (`MainFrame`, `put("identity", …)`) has three branches, and they answer three
different questions:

1. **`observeReadIdentity()`: what is true of the file at THIS request.** A fresh observation, with `readsSuspended`.
   It also **posts `LogIdentityObserved` to the session** on the next EDT turn, and that post is what later moves the
   banner. So branch 1 is not a rival composition. It is the fact's *source*, and `context` is one EDT turn ahead of
   the banner by design.
2. **`identitySnap.logIdentity()`: already a projection of the session.** It reads the same `OpenLog.identity` /
   `identityReason` that `IdentityBanner` reads. There are not "different inputs".
3. **"not assessed": a reader-capability statement** the banner never makes. `IdentityBannerView.of(null)` is
   `shown=false`, the same as "verified".

So the only branch a published view could retire is the one that is already a projection. Step 2 retires nothing
substantive. The concession "the identity verdict was already in context" is therefore **understated**: `context` is
the *richer* statement (a fresh observation, suspension, capability), and the banner is a lossy projection of it.
What `context` genuinely lacks is layer 2: *whether the banner is up right now*, meaning `shown` plus the last
render's answer. That argues for step 1 as reshaped above, not for step 2.

**The rule-9-shaped sites are in the adapter, not in `context`.** `observeReadIdentity()` composes a status line by
hand (`status.setText("⚠ " + displayName(...) + ": " + identity.reason())`) at the moment it observes. That is
precisely the "status line composed where an event happens" rule 9 names, and it can overwrite the status-line view.
This is the better example for the document to use, and a defect to file.

## 3. Decoupling: publish without render or audit

- **A published-but-never-rendered view is not "what a surface states".** Layer 2 is defined by the render: the
  effect, its answer, and the backends that drew it. A view that no surface was told is derived knowledge, which is
  layer 1 reshaped. Calling it layer 2 is the category error the document warns against ("getting it wrong is how the
  idea gets oversold"). It is still useful, but label it `derived`, not `states`.
- **What breaks with unaudited views: citation.** The product's claim is "trust the evidence, not the author". An
  agent that quotes a readable-only view in a report or evidence bundle cites something with no record of *when* it
  was true, and the audit cannot corroborate it. That does not weaken the audit's faithfulness, since nothing false is
  recorded. It weakens the *citability* of what agents quote. Rule: **only audited views may be cited as evidence**,
  and `context` should mark readable-only ones as such.
- **The document's cost line is wrong for its own proposal.** "Readable — cost: the node's invocation line" assumes
  readable views are nodes. As projections they cost nothing and add no noise. As nodes they add exactly the noise §5
  worries about.

## 4. The per-element table, by its own test

The rows I would move:

| row | move to | why |
|---|---|---|
| Project panel rows (readable ✅) | ❌, or "already done" | `ProjectModel.from(context)` is already a projection of `context`, enforced by `ProjectPanelIsRevealOnlyTest` (D-L1). There is no second composition to fold. It is the pattern to copy, not a row to build |
| chart scope incl. `emptyReason` (✅ ✅) | **with #53, later** | `graphScopes` comes from `GraphPanel.scopeFacts()`: panel state, not session state. `emptyReason` is a *paint outcome* (`ChartPanel.PaintOutcome` in M69, PR #57), so it is the drawn layer. The row straddles two layers and a change of owner |
| `showing N of M` + filter (✅ ✅) | readable only, committed changes only | The filter lives in `FilterState` (UI). The visible count comes from Swing's sorter over the store, so it needs a fact per change (Finding 2). Auditing it per edit contradicts the document's own "a keystroke round-tripping a graph is the wrong shape" |
| topology context (✅ ✅) | readable only | Owned by `TopologyPanel`'s focus stack. M69 walks cite a focus by its **definition digest**, not by the label, and a walk's showing state is already audited by the `walkPlayback` node. The stated reason does not hold |
| pairing verdict (✅ ✅) | **keep, first** | `publishPairing()` is a hand call in `onSessionSnapshot`, the same shape the identity banner retired. The best next element |
| record table rows (❌) | keep ❌ | The Finding 2 reasoning holds. By reference, the backend must read the store to know what it states, so the view no longer determines the statement and cannot be reproduced from the audit |

## 5. Cost evidence and the real risk

- **n=2 does not support "the cheap half can cover far more of the app".** The PR #58 review also shows that part of
  the measured +3.3% was a (view nodes × renders) dispatch fan-out. It is fixed on `review/pr58-fixes` (+2.0%). Its
  existence means per-element costs were not yet understood when the generalisation was written. The floor that
  remains is per append, per node on `OpenLog`.
- **Selectivity is the right risk.** "Measure signal-to-noise before widening" is currently a placeholder. A concrete
  gate the existing instrument can compute:
  - **invocation lines per `LogAppended` record from nodes that changed nothing**, at N view nodes;
  - with a bound, for example no more than 2, before any widening.

  The same instrument, with a synthetic graph of N no-op view nodes, answers it in minutes.

## The two concessions

- **(a) Five of six were the drawn layer.** Honest, but the sample is biased. That day's work was #46–#50 (chart
  defects) and spotlight overlap, which are drawn-layer tasks by construction. It supports "#53 is higher value for
  chart and spotlight work", not in general. **Slightly overstated as a general claim.**
- **(b) Already in context. Understated** (§2). `context` says more than the banner, and more freshly.

## Claims I could not verify

The brief cites material **not present at `5548ce9b` or on any pushed branch** (`git grep` across all remotes):

- the section *"What step 1 landed, and one thing it proved wrong"*;
- `PublishedSurfacesTest#whatIsKnownAndWhatIsShownDiverge` and its 30 known / 25 shown;
- a `surfaces` block in `SessionSnapshot.of` or `MainFrame`, and its `ContextSections` entry.

I cannot say whether `surfaces` is a projection or a second assembly, or whether the divergence fixture is
contrived. If they exist locally, push them and I will review that delta.

**`fields()` as the audit's vocabulary: half true.** For `StatusLineView`, the audit entry is `changedFrom`, which
is built on `fields()`, so the vocabulary is shared. `IdentityBannerView` has no `fields()`; its `changedFrom` lists
the names by hand, a second list that can drift from the record.

## The strongest argument against the proposal that it does not make

**Most of the table is not a publication change. It is an ownership change, and the wrong direction for Fluxtion.**
A view node can state only what reached the session as a fact (Finding 2). The filter, chart scope, topology focus,
report list and coverage summary are owned today by Swing panels, `AppConfig` and verbs computed on demand. Making
them views means routing each one's state changes through the processor:

- keystrokes and drags debounced into facts;
- chart windows mirrored into the session;
- focus stacks duplicated.

That is a large migration that drags high-frequency interactive state into the orchestrator. The orchestrator is
valuable *because* it is selective: session state and WHEN it is stale. The document names selectivity as the thing
to protect, then proposes a table whose realisation erodes it by construction. Nothing it measured (two elements,
both already session-owned) speaks to that cost.

Keep the processor for what has a WHEN. Let projections, and #53's display list, cover the rest.
