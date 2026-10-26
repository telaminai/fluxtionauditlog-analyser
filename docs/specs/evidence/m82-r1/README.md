# M82 r1 digest (2026-10-25): bootstrapped arm

Sanitised summary for [spec-authoring-scorecard.md](../../spec-authoring-scorecard.md) §7. Counts and findings only; no
transcripts, paths or key material.

## Method
- Starter: the analyser-bundle starter (template scaffold digest `6d331b04...`) with its five skills, in the directory.
- Analyser 1.38.0 run as a GUI with REST on; its MCP registered at project scope and reported connected.
- Task: the baseline task plus one sentence saying the starter is in the directory and the analyser is connected.
- Runs: Sonnet 5.5 x5 (b1-b5), Haiku 5.5 x1 (h1). Per-run rows were not kept beyond the aggregates below.

## Counts (Sonnet, n=5, baseline for comparison)

| Endpoint | Baseline | Bootstrapped |
|---|---|---|
| E1 buildable project | 5/5 | 5/5 |
| E2 compiles | 5/5 | 5/5 |
| E3 runs correctly | 1/5 | 5/5 |
| E4 explains a record | 0/5 | 5/5 (read from the YAML) |
| SC-B2 analyser pointer | n/a | 0/5 |
| F1 strict (own generated processor) | not checked | 0/5 |
| E5 dispatch claims | correct where made | 4/5 clean |
| Median minutes / acts / $ | 3.0 / 29 / 0.31 | 2.4 / 15 / 0.26 |
| Stopped for consent | 4/5 | 0/5 |

The comparison is confounded: the template already had the task's shape, so the runs only filled in method bodies.

## Analyser use
Sonnet: 0 MCP calls in 5 runs; all five told the person to open the YAML and GraphML themselves. Haiku (h1) was the only
run that authored a graph (XML, validate, generate) and made 2 MCP calls (open, then spotlight on a node).

## Top stopping points
1. The analyser is never reached: nothing in the project instructions routes "explain / evidence" to the load skill or
   the MCP tools, and the YAML is greppable.
2. The jar goes stale: the run script builds only on first use, and exports accumulate across runs (2/5).
3. The alert is an audit field, not an output; adding a sink needs a binding plus regeneration.
4. `eventHandlers` is misread as routing (2/6).

## Observed vs inferred
Observed: all counts and the zero MCP calls. Inferred: that the missing route (not unwillingness) explains the zero.
Limits: n=5 (5/5 is consistent with up to 45% failure); one grader for E4 and E5; the operator pre-opened the project.
