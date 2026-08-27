# Spec — the Project panel: what is in force, stated in one place

**Status:** IMPLEMENTED 2026-08-27 on `feat/m37-loaded-panel`, awaiting review (ACCEPTED 2026-08-27; review `docs/handoff/review_spec_loaded_panel.txt`, C1–C4 folded in; report `docs/handoff/report_feat_m37_loaded_panel.txt`). **Milestone:** M37. **Tracker:** [tracker.md](tracker.md) ▸ M37.

## The proposition

The owner's words: *"currently it is not clear what is loaded in the current project."*

The analyser holds five kinds of state, drawn from three tiers (own settings, project, transient), and
tells the user about them in five different places:

| State | Where it is told today | What is wrong with that |
|---|---|---|
| **Project** — name, file location, which tiers are in force | window title (name only) | the path is nowhere; "am I on my own settings or the project's" is a guess |
| **Audit log** — file, rolled set, or a reader's SYSTEM (§E provenance) | status bar, once, at load | overwritten by the next status message; a rolled set's members are never listed |
| **Graph** — opened / supplied by the reader / none, and the pairing verdict | Topology tab header | invisible from the Records tab, where most of the session happens |
| **Event processors** — the configured Java classes, the selected one, whether source resolves | Settings ▸ Source, two dialogs deep; the Source tab dropdown | a dropdown that shows one value at a time is not a list of what is configured |
| **Source roots** — own + project + the transient demo root | Settings ▸ Source | the tier of each root is not shown; the demo root is invisible by design and so is a surprise |

Meanwhile the action socket's `context` verb reports **all of it, at once, with provenance** — to
agents. The human who sits at the shared research canvas (docs/site/the-loop.md) is the one party who
cannot see what is on it. The graph-loss defect fixed on 2026-08-26 ran for every release since 1.1
because nobody could see the saved-graph count change under them; three of the M35 review findings
were of the same species. This is the gap.

**So this is not a new model.** It is `context`, rendered for the human, in the west column next to
*Event types*, one click to show or hide.

## D-L1 — it is a rendering of `context`, not a second model

The panel is built from the same assembly the action socket serves (`context` → the map the MCP tool
returns; `SessionFacts` for the pasted-prompt subset). Nothing in the panel may be computed from
MainFrame fields directly. Two consequences, both wanted:

- **The human and the agent see the same facts.** When the panel and `context` disagree, one of them is
  lying, and the test catches it: the panel is constructed FROM the context map in a headless test, and a
  second test walks every row and finds its key.
- **A fact the panel needs that `context` lacks is added to `context` first** — which improves the agent
  in the same slice. (Candidate gaps at spec time: the project **file location**; the tier of each source
  root; the rolled set's member list; whether each configured processor **resolves** to a source file.)

## D-L2 — five sections, and every "none" is a sentence, never a blank

```
PROJECT        DemoQuote                      ~/projects/demo/.analyser/project.fluxtion-settings
               settings from: project (own settings for: columns, window)
AUDIT LOG      demo-quote-audit.yaml          ~/projects/demo/logs/…   opened by you · 10 records · 12:00:01–12:00:09 UTC
               (a rolled set lists its members here, oldest first, with the current one marked)
GRAPH          demo-quote-processor.graphml   ~/projects/demo/build/…  opened by you
               pairing: applies — declared by the graph (opened beats supplied)
PROCESSORS     com.acme.demo.generated.DemoQuoteProcessor   selected · source found
               com.acme.demo.generated.HedgeProcessor       source NOT found under any root
SOURCE ROOTS   ~/projects/demo/src/main/java                project
               ~/work/shared-lib/src/main/java              own settings
               (demo root, transient — gone at restart)     demo
```

**The AUDIT LOG row shows the origin the user or agent named** (review C2): an S3 log reads
`s3://bucket/key`, never the temp file it was fetched to — `context.log.openedFrom`, with `log.path`
(the local copy) mentioned as "fetched to a local copy" and nothing more. A rolled set's members are
listed by display name in load order under the set's row; their directory is the row above.

Empty states are sentences that say what would fill them: *"No project — using your own settings
(~/.fluxtion-analyser)."* · *"No log loaded."* · *"No graph — File ▸ Open topology, or a reader may
supply one with its log."* · *"No event processors configured — Settings ▸ Source."* A blank row is a
question the user has to go and answer somewhere else, which is the complaint this spec exists for.

**A sixth section, REPORTS** (M37.6, owner-requested after the first live render): where files leave — the
assistant's exchange directory (machine-tier: a path on this disk, never shared) or the sentence *"File
exchange off — Settings ▸ Assistant"* — and each saved report by title with its section count (project
tier). M38.5's *published destination* (spec-portable-context D-C6) is a further row here, not a new section.

## D-L3 — every row carries where it came from, and offers to REVEAL, never to CHANGE

Provenance is a column, not a tooltip: *opened by you* / *from the project* / *supplied by the reader
(DECLARED | INFERRED)* / *from the action socket* / *demo, transient*. The `OpenRequest` that M35.9
attached to every load already carries this; the panel is its first human-facing consumer.

Actions on a row are **reveal and navigate only**: *Copy path*, *Show in Finder / folder*, *Open in
Source tab* (a processor), *Go to Topology* (the graph), *Settings ▸ Source…* (roots). Closing,
switching, resetting stay where they are (File menu, Topology, Settings) — the panel may link to them,
it may not do them. Offer, never act; and a display that can mutate state is a display people learn not
to trust.

## D-L4 — the pairing verdict is a row, not a footnote

Graph ↔ log pairing is the fact users most often get wrong in silence (M34 §E, M35.4). The GRAPH section
states it in the house words: *applies / does not apply*, *declared by the graph / inferred*, and when
two graphs are in play, which won and why (*opened beats supplied*). The Topology header keeps its
statement; this one is visible from the Records tab.

## D-L5 — a NavRail toggle beside *Event types*, persisted

`NavRail.addToggle("Project", …)` on the west rail, sharing the west column with the event-type filter
(stacked; the split is persisted like every other layout choice). Collapsed state persists in
`AppConfig.projectPanelCollapsed`, the divider in `westDivider`. Default **shown** — a panel that answers "what
is in force" must be seen once to be looked for later; after that the user's choice stands. Mechanism
(review C3): a new boolean whose default is *shown*. An existing config has no such key, so the first
launch of the shipping version shows it, and the first collapse persists; nothing is hung off
`lastRunVersion`, which a dev build never writes.

## D-L6 — it follows the lifecycle, it does not poll

The panel re-renders on exactly the events M35 defined: log load / close / reset / switch processor,
project open / close / switch, graph open / clear (including a reader's graph clearing with its log),
source-root edits, and any load that arrived over the action socket. It subscribes where `context`'s
own truthfulness is maintained, so the two cannot drift. Test: the sequence *open log → open graph →
open project → close log* is played headless and the panel's rows follow at every step.

## D-L7 — plurals are honest

The request says "graphml(s)". Today exactly one graph is in force at a time (one `TopologyPanel`
topology; GraphSource precedence picks it) — so the GRAPH section shows one, and if a reader supplied a
second that lost the precedence, says so on a second line rather than inventing a list. What IS plural
is listed: a rolled set's members; every configured processor; every root with its tier; and, for an
M34.2 reader whose log embeds several processors (the split-view dropdown of `881b047`), the processors
the log itself declares. When a reader TRIED to supply a graph and could not, `sourceGraphNote`
(M34 review F2) is the GRAPH section's second line — named here so it is not reimplemented (review N1).

## D-L8 — what is DRAWN is abbreviated; what is COPIED is complete

Support screenshot this application (review C4; `docs/site/support.md`), and this panel is by
construction the most path- and name-dense surface in it — default shown. So the drawn form is
abbreviated: `$HOME` becomes `~`, a long path keeps its head and last two segments with the middle
elided (`ProjectModel.abbreviate`), and the full value lives behind *Copy path* and the tooltip. Rule 1
cannot see inside an image; this narrows what an incidental screenshot carries, and a column of full
paths was unreadable at the west rail's width anyway.

## Relationship to M20.5 (project artifact pointers)

M20.5 makes the profile **point at** a graphml and a log directory, and has the analyser *offer* to open
them. That is a statement about what the project *would* load; this panel states what *is* loaded. They
compose: with M20.5 shipped, the panel gains a line *"the project points at build/…/x.graphml — not
loaded · Open?"* — the offered state made visible, which M20.5 cannot do on its own. **Order: this
first.** M20.5 without it is an offer that fires once at open and is then invisible.

## Non-goals

- Not a file browser and not a recent-files list — the start page and File menu own those.
- Not a settings editor — no field on it is writable.
- Not a replacement for the status bar's transient messages, only for its role as the one place the
  pairing was stated.

## Decisions taken 2026-08-26 (owner)

1. **Name: "Project."** Decided 2026-08-27 after seeing it live, overriding the earlier "Loaded": the
   panel is the project — what it points at and what is in force under it — and "Project" is the word
   the window title, the File menu and the profile already use. (*Loaded* was the interim choice;
   *Session* was generic; *In force* is how the docs talk, not how a rail button reads.) The spec's
   filename keeps its history; the surface is the Project panel.
2. **Stacked** with *Event types* in the west column — the two answer different questions and both are
   glanced at, not worked in.
3. The start page does **not** show the PROJECT section inline — the west panel coexists with it.

## Acceptance

- [ ] Every row is built from the `context` map; a headless test constructs the panel from a captured
      map and a second test proves every rendered key exists in `context` (D-L1).
- [ ] Gaps found in `context` (project file location, root tiers, set members, processor resolution)
      are added to `context` and documented on the MCP/context reference page in the same slice.
- [ ] All five empty states are sentences (D-L2); no section ever renders empty.
- [ ] Provenance shown per row; actions are reveal/navigate only — a test asserts no action on the
      panel calls a mutating MainFrame method (D-L3).
- [ ] Pairing verdict stated in the GRAPH section in the house words (D-L4).
- [ ] NavRail toggle, persisted, default shown once (D-L5).
- [ ] Lifecycle sequence test passes (D-L6); no timer.
- [ ] Docs: a page under *The analyser*, with a generated screenshot (`tools/capture-docs.py`), read
      before commit (rule 1).
- [ ] CHANGELOG line; tracker M37 slices ☑; this spec's status → SHIPPED.

## Slices

- **M37.1** `context` parity — add the missing facts to `context`; the parity test scaffold.
- **M37.2** The panel + NavRail toggle + persistence; five sections with empty states (D-L2, D-L5).
- **M37.3** Provenance column + reveal/navigate actions (D-L3); pairing row (D-L4).
- **M37.4** Lifecycle wiring + the sequence test (D-L6).
- **M37.5** Docs page + generated screenshot; CHANGELOG; tracker.
