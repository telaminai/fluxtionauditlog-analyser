# Topology Focus as a Filter Context — drill-down, not toggle (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-17 · Milestone **M27**

Companion to **[tracker.md](tracker.md)** (M27). Corrects the focus model shipped in M22
(`topology/TopologyFocus`) after owner use on the 309-node estate and the production maker graph.
Supersedes the M22 wording "focus shows only that scope; Show all / click empty canvas returns to the
plain full graph". Touches the same seam as the in-flight polish item H4 (routes bound at terminal
nodes) — coordinate, don't collide.

## The correction (owner-stated, 2026-08-17)

Focus today is a **toggle**: a view mode that dims/hides against the full graph, where clicking empty
canvas exits everything at once. Focus is redefined as a **filter operation** with drill-down
semantics:

1. **Applying focus filters the graph.** The scope-subgraph of the selected nodes *becomes the whole
   graph* as far as every subsequent UI operation is concerned — layout, scope-cycling, selection,
   the index overlay, node counts, Fit.
2. **Inside a context, interactions are context-relative.** Clicking a node cycles its scope
   (*node → neighbours → routes → all*) **within the context** — "all" means "all of this context",
   not the original graph. Applying focus again drills deeper: contexts **nest**.
3. **Clicking empty canvas clears dimming/selection within the context — it does NOT exit the
   filter.** Leaving a context is a separate, explicit act. (The analogy is the records table: a
   click in the table doesn't discard the shared filter.)
4. **Exiting is explicit and stack-shaped**: one action pops a level (back to the parent context),
   one returns to the full graph. Esc = pop; **Show all** = pop-to-full (and says so).

Mental model to implement against: the records-table filter, transplanted. Filter narrows the world;
selection and dimming are ephemeral *within* it; the two must never share an exit gesture.

## The model

- `FocusContext` = an induced subgraph (a node-id set + the edges among them) with a human-readable
  derivation label ("routes of storePnl", "neighbours of bidMakerOrder").
- A **context stack**: full graph at the bottom, each focus-apply pushes. All existing view machinery
  (layout, scope computation, index overlay, counts, scaffolding filter) reads the **top of stack**
  instead of the full topology. Practical depth is 1–3; no artificial limit, but the breadcrumb (below)
  keeps the user oriented.
- **Execution shading is computed on the full graph, displayed within the context** — evidence wins
  over view state (the M22.53 rule). A cycle that runs through nodes outside the context is indicated
  at the context boundary (an edge stub / "n nodes outside this view ran" note in the status line)
  rather than silently cropped — the context must never quietly misrepresent a propagation as
  contained when it isn't.
- Scaffolding hide/show composes orthogonally, as today.

## UI

- **Breadcrumb** in the status line: `All (62) ▸ hedge path (12) ▸ neighbours of hedgeToOrdersNode (5)`
  — each crumb clickable to pop to that level.
- Canvas click → clear selection + dimming (top context untouched). Esc → pop one level. **Show all**
  → pop to full graph, wording changed to make it the *filter* exit, not the dimming exit.
- Node click → scope-cycling within the top context (unchanged gesture, re-rooted meaning).
- The step-through cursor, callouts and coverage all render against the top context with the
  boundary-honesty rule above.

## Named focus — save the context (owner-requested; the large-graph payoff)

- **Save focus as…** names the top context's node set; a picker (toolbar dropdown + the `topology`
  verb) recalls it — recalling REPLACES the stack top? No: recalling **pushes onto the full graph**
  (a named focus is an absolute view, not a relative one) — predictable regardless of what was open.
- **Persistence & sharing**: named focuses are project-tier artifacts — they describe *the processor*,
  not the machine or the session — persisted like saved graphs and rideable through `SettingsShare`
  (new list under the GRAPHS-adjacent project scope; decide whether a new M15 category or a sub-list
  of the existing project tier — favour whichever keeps `ProjectProfile.PROJECT_SCOPED` at five
  pinned categories, i.e. fold into an existing one deliberately, and say so in the report).
- A named focus stores **instanceIds**. Against a different build, missing ids are surfaced with the
  existing mismatch machinery (warn + show what resolved), never silently dropped — same rule as
  topology/log pairing.
- **Verb surface**: `topology { focus: "<name>" }` recalls by name, and `topology
  { saveFocusAs: "<name>", rationale: "…" }` saves the top context as a named focus (owner decision
  2026-08-17, reversing the draft's restraint). The precedent is agent-created graphs (AV.2): an
  agent that has isolated the subsystem behind a finding should be able to leave that view behind as
  a durable, named artifact — same class of write as a saved graph, and governed the same way:
  **`rationale` captions the focus** (shown in the picker) so a week later it is a finding, not an
  unexplained view; **replace-by-name** semantics on save (like graphs on import); plain delete from
  the picker. Not destructive-hinted — it writes project config exactly as `graph` does, no file, no
  FAQ change (`FaqSecurityContractTest` untouched). The echo returns the saved name + node count +
  breadcrumb so the agent can tell the user exactly what it kept.

## Verb semantics alignment

The `topology` verb's current `focus: bool` ("hides vs dims") becomes context-aware: `scope` +
`focus:true` pushes a context (verb echo names the resulting breadcrumb), `focus:false` dims only, and
a new `pop: true | "all"` exits levels. Echoes always carry the current breadcrumb string so an agent
knows what world its next call operates in. Schemas via `VerbSchemas` as ever — MCP picks it up free.

## Out of scope

- No change to what scope *computes* (H4's terminal-node bound lands independently in the same class).
- No per-context saved layout/zoom (view prefs stay global per M22.32).
- No agent-initiated **project switching** — unchanged; saving a named focus writes into the
  *current* project only.

## Delivery slices

1. **M27.1** `FocusContext` + context stack in `topology/TopologyFocus` — pure, headless-tested:
   nesting, context-relative scope cycling, boundary detection for out-of-context execution, pop
   semantics. (This is most of the work; the class already exists and has a test to extend.)
2. **M27.2** UI rewiring: canvas-click = clear-dim-only, Esc = pop, Show all = pop-to-full,
   breadcrumb, boundary indication. Human-eyeball list in the report (Swing).
3. **M27.3** Named focus: save/picker/persist (project tier per above) + share + mismatch surfacing +
   verb alignment — `topology {focus: name, pop}` to recall/exit and `{saveFocusAs, rationale}` to
   save (agent-creatable, rationale-captioned, replace-by-name). Changelog: behaviour change of
   canvas-click/Show-all MUST be called out — it changes a shipped gesture.
4. **M27.4** Docs: topology guide's exploration section rewritten around the filter-context model
   (the drill-down mental model, breadcrumb, named focuses); screenshots via the harness.
