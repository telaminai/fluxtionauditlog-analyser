# Proposal: say what a project reload gives you back, and which window control is saved

**Status:** proposal, nothing built. Raised 2026-09-24 by the owner while verifying M68.2/M68.3 at a real
display: *"reloading a project does not bring the log and graphs back after closing"*, *"a restore last
session only brings some of the state back"*, and later *"was the zoom ignored"*.

> **Rewritten 2026-09-24 after independent review. The first version of this document was wrong at its
> centre** and its central recommendation has been withdrawn. It claimed the recovery model captured only
> two things, a log and a topology, and proposed either renaming "Restore last session" to match that or
> widening the capture. Both rested on a false reading. The error and its cause are recorded below,
> because how it happened is more useful than the conclusion it reached.

## What the recovery model actually captures

Read from the code, not from a session:

- **Four input roles**, enumerated and validated in `session/resume/SessionResumeStore.java`:
  `log`, `topology`, `design`, `diagnostics`. A role outside that set is rejected at construction.
- **A `view` map**, assembled in `MainFrame.captureSession()`, holding the **filter** (from, to,
  dimensions, text, group mode), the **findings/flags**, the **selected records**, the **topology view**
  (including its cursor), the **selected graph**, the log and graph **hashes**, the **provenance** and the
  **format**.
- It is **applied**, not merely stored: `applyRecovery` calls `restoreRecoveryView(plan.snapshot().view(), …)`,
  gated on the captured `loadedLogHashes` still matching. When the bytes have changed the view is
  deliberately **withheld** rather than applied to a different log — a designed refusal, not a gap.

So "Restore last session" restores considerably more than its name suggests, and the earlier complaint
that it "only brings some of the state back" has a narrower cause than a thin capture.

### How the first version got this wrong

It read `context.restoration.inputs` in one live session, saw two entries — `log` and `topology` — and
concluded the model supported only those two. The session had no design and no diagnostics open, so two
is what that fixture could ever show. A sample was mistaken for a schema, and the conclusion was then
written up as fact and committed.

`SessionResumeStore.Input` is fifteen lines long and states the four roles in a `Set.of(...)`. Reading it
would have cost less than writing the paragraph that got it wrong.

## What is genuinely not captured

Three things, and they are the whole of the remaining gap:

- **Zoom** — `ChartPanel.zoomIn/zoomOut/resetView`, called by the `+`, `−` and `Fit` buttons. Nothing
  writes it and nothing restores it.
- **Spotlight** — session-scoped by design; a callout is explicitly never saved.
- **Posture** — session-scoped by design, cleared by a project switch, as the `open` verb documents.

The last two are deliberate and documented. Only zoom is an accident of omission.

## The one gap worth acting on: zoom versus pin

Found by the owner reloading and expecting the zoom back:

- **Zoom** (`+`, `−`, `Fit`) is a lens. Nothing calls `mutated()`, nothing is written, nothing returns.
- **Pin** (📌, "Pin to current window") is a fact. It is `GraphSpec.from`/`to`, written by `ConfigStore`
  as `graph.N.from`/`graph.N.to` and re-applied by `doRestore` through `panel.pin(...)`.

Both set the visible window. They sit on the same toolbar. One is forgotten and one is saved, and nothing
on screen distinguishes them — 📌 marks the tab only *after* the fact. A person who zooms and reloads has
no way to know they should have pinned.

**Recommended, and the only recommendation in this document:** say which control keeps its window. Word
the tooltips so zoom reads as a lens and pin as something kept; optionally, when a chart reloads unpinned,
let its caption line say the window was not kept. No new persisted state, no policy decision, no
dependency on anything else here. Widening capture to include zoom is a separate question and should not
hold this up.

## The second complaint: a project reload shows an empty canvas

Reopening a project applies settings and does not reopen a log; `GraphTabs.restore` then returns early
because `store == null`, so no chart is rebuilt. Both behaviours are deliberate and pre-date this work —
the guard dates to `e965afa2` (initial public release) and the settings-not-session split to `1fab905e`
(M35.8), whose echo says so in as many words.

The complaint is therefore about **disclosure**, not behaviour: the verb echo explains the split to an
agent, while a person clicking in the UI simply finds their charts gone, one step from recoverable.

- **B1 — say it on screen.** When a project is applied with no log open, the Project panel's log row says
  so and points at the reopen. **Withdrawn as written.** The first version called this "reveal-only and
  therefore inside D-L3 as amended". That is not established: bringing a chart view forward is not the
  same as loading a log, which changes the session. Before anything is built, the spec must say which
  surface may *offer* navigation to the existing restore decision and which may *execute* it — and
  `spec-project-starter-journey.md` already has text in this area that has to be reconciled first.
- **B2 — reopen the previous log automatically.** Not recommended, and not the owner's to be talked into:
  a project is a session boundary (M35.5), and silently carrying a log across it is how someone reads one
  system's log under another system's settings.
- **B3 — offer it, never take it**, the way `projectOffer` already offers settings. Consistent with
  M35.4/D-AI5. The plausible route if disclosure alone proves too quiet.

## Non-goals

Not proposed: changing the settings/session split, widening the capture, renaming "Restore last session",
or approving any automatic restoration. The first version proposed the rename; on the corrected facts the
current label is **more** accurate than the replacement it suggested, not less.

## Evidence pointers

- `session/resume/SessionResumeStore.java` — the four roles, `Snapshot.view`, the identity check.
- `MainFrame.captureSession()` — what the view map actually holds.
- `MainFrame.applyRecovery` / `restoreRecoveryView` — that it is applied, and the hash gate that withholds it.
- `GraphTabs.restore` — the `store == null` guard (initial release) and the M68.3 closed-chart skip.
- `ChartPanel.zoomIn/zoomOut/resetView` versus `ConfigStore` `graph.N.from`/`to` — the lens and the fact.
