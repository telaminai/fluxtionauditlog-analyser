# Topology feedback intake — issues 36–37

The [feedback snapshot](ANALYSER-FEEDBACK.md) preserves the two owner-supplied observations appended
on 2026-09-20. The master index now includes 37 issues. Earlier snapshots are unchanged.

- 36: combined `showAll:true, scaffolding:false` leaves focus depth 1; `pop:"all"` worked. Source
  inspection identifies differing MCP and toolbar paths: `clearView` clears highlights without popping
  focus, whereas the toolbar's Show all pops first. This was not independently exercised at runtime.
- 37: six spotlight captions overlap neighbouring topology content. The participant identifies the
  covered nodes; the inspected screenshot confirms six targets and overlapping content. Placement scores
  lit cut-outs and other captions, not all occupied topology nodes. A fix needs viewport-aware geometry
  and an explicit fallback when no clear placement fits.

The screenshot's original path/hash is recorded in the manifest; it is not copied into the public repo.
The packet contains observations and source checks, not a product fix or UI acceptance result.

Related discovery: focus saving already exists through `analyser_topology` with `saveFocusAs` and optional
`rationale`; recall uses `focus` with the saved name. A focus must first be applied; saving the full graph
is refused. This is project-scoped configuration, not a snapshot of the entire spotlight/session state.
