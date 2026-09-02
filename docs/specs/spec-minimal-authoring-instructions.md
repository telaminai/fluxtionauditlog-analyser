# SPEC (PROPOSED) — the minimum instruction set for LLM authoring against Fluxtion

**Status** proposed · **Target** the public Fluxtion AI instructions (`docs/claude.txt` and the
golden path), plus the artefact-side conventions in
[`spec-component-catalogue.md`](spec-component-catalogue.md)
**Evidence** [`docs/experience/runs/round-48/`](../experience/runs/round-48/) — fifteen measured
cells, one model, one problem; replication n=4 on the optimum; ablations in `ABLATION.md`

## What this specifies

**The smallest set of instructions that lets a language model author a correct Fluxtion integration,
and the rule for deciding whether any future instruction belongs in it.**

Instructions are charged against every turn of every session. Text that does not change an outcome is
a permanent tax. This spec exists because that tax was measured rather than estimated, and because
the measurement retired material that felt indispensable.

**Non-goal.** This does not specify how to *use* Fluxtion. It specifies what must be *told* to an
author who already has the jars, and — critically — how much of the answer belongs in the artefact
rather than in prose.

## The measurement this rests on

Fifteen cells, one model (Haiku 4.5), one problem: assemble five bought-in components into a risk
engine, scored against a held-out scenario. **The only variable was what the integrator was given.**

| cell | consumer writes | manual | turns | javap | weighted cost | result |
|---|---|---|---|---|---|---|
| open brief | beans + runner + tests | 2,608 w | 206 | 12 | **14.80M** | **wrong** |
| −SCOPE section | beans + runner + tests | 2,442 | 175 | 26 | 10.78M | **wrong** |
| −assembly guide | beans + runner + tests | 2,431 | 152 | 24 | 8.35M | pass, 27 internals declared |
| indexed jar manifest | beans + runner | 848 | 72 | 6 | 2.91M | pass |
| **optimum: bean file only** | **beans** | **659** | **51** | **0** | **1.98M** | pass |

**14.80M and wrong → 1.98M and correct. 7.5×, same model, same jars, same problem.**

Replicated four times at the optimum: **mean 2.15M, sd 0.12, 14% spread, 4/4 correct, 3/4 with zero
`javap`.**

## Normative: what MUST be present

Each item below is retained because **removing it was measured to cost something**. The evidence
column is the cost of deletion, not an argument for inclusion.

| § | Instruction | Words | Cost of removing it |
|---|---|---|---|
| **N1** | **A scope section: what the author must NOT write** | ~166 | wrong answer, 3 failed builds, +5.8M |
| **N2** | **The entry-point-is-not-a-node rule** | ~170 | author declared **27 vendor internals** instead of 5 components |
| **N3** | **The one-command catalogue read** | ~40 | the author falls back to `javap`; every discovery aid below becomes necessary |
| **N4** | **The runner shape, supplied not described** | — | 72 → 51 turns; `javap` 6 → 0 |

### N1 — the scope section

The instruction set MUST state, in the imperative and by exclusion, what the author is *not*
authoring. In the measured fixture this was *"you are constructing the bean file, nothing else"*.

**This is the single highest-value item and it is invisible to every successful run.** All three
introspected cells named it unused — one wrote *"I never considered writing one"* — while the cell
that removed it produced a wrong answer. A successful run is definitionally the one that did not need
the guardrail.

### N2 — the entry-point rule

The instruction set MUST state that a composite/entry-point class **is not a node**: it carries no
annotations, never becomes dirty, and cannot be a trigger parent. Wiring through it silently yields a
smaller graph.

This is retained on the same logic as N1 — its failure mode is silent, so no author reports needing
it.

### N3 — the catalogue read

The instruction set MUST give **one command** that prints the component catalogue out of the jar
manifest, including any unfolding needed for the manifest's line-continuation format. It MUST NOT
describe a *procedure* for discovery. See P1 below.

### N4 — the runner

The harness MUST be supplied as a file, not described in prose. Description costs turns and provokes
`javap`; a supplied file costs nothing and names zero components.

## Normative: what MUST NOT be present

| item | words | why it must go |
|---|---|---|
| fully-qualified-name reference sheets | 251 | free to delete — zero measured effect |
| a worked example, **once the catalogue is indexed** | ~200 | **+28 turns.** Harmful, not merely useless — see P1 |
| a step-by-step discovery procedure | ~150 | its "one `javap` per entry point" step produced **21 `javap` calls**, the most of any cell |
| template READMEs | 1,725 | removable at six extra builds; a poor trade |

## Principles for deciding what belongs

### P1 — discovery aids expire

**The worked example produced the fewest builds in the study (2), then became harmful once the
catalogue was indexed.** It teaches the author to `javap` for facts the manifest now answers.

> **A document describing a procedure for discovery has a shelf life, and it ends when the discovery
> is precomputed.**

Any instruction whose content is *how to find out X* MUST be deleted the moment X is carried by the
artefact. This is the only rule here that requires re-running an ablation after an unrelated change.

### P2 — guardrails cannot be retired by asking

Preventive text is invisible to the runs that need it least. **Only a measurement can retire it.** An
author's report that a section was unused is not evidence for deletion; it is the expected report
from a run the section protected.

### P3 — cost tracks comprehension, not authoring

Introspection put reading and component selection at **32–50% of effort**, and writing the
integration at **5–10%**. This is why manual size dominated every measurement and why **indexing the
artefact beat every documentation change**.

> **Move the answer into the artefact before writing prose about it.** Every instruction competes
> with a manifest entry that would make it unnecessary.

### P4 — tool knowledge is fair; task knowledge is not

An author using Fluxtion libraries gets Fluxtion instructions. An author using plain Java already has
the training. **Neither arm may be given the design of the solution** — only the knowledge of its
tools. Instruction sets that encode the answer measure the author of the instructions, not the model.

### P5 — the artefact's API decides whether the author can be correct at all

Round 53 measured this directly. Given a component library **without** a counter-free recompute, both
available behaviours were wrong — a figure 88% understated, or a false alert and three inflated
counters. Given the same library **with** one added `refresh()` method, an idiomatic author scored
**17/17**.

> **No instruction set can rescue an API that makes correctness unreachable.** Before adding prose,
> check whether the artefact offers an operation that lets the author be right.

For Fluxtion specifically this is structural rather than conventional: the recompute/handle split that
had to be added by hand in that experiment is what `@OnTrigger` already is.

## Artefact-side requirements

These are not instructions but they dominate instruction cost, and the instruction set is only minimal
if they hold. Full treatment in [`spec-component-catalogue.md`](spec-component-catalogue.md).

- **A1** — the jar manifest MUST carry a per-entry catalogue: what the entry point provides, requires,
  consumes, and its constructor signature.
- **A2** — provided fields MUST be indexed as `field=Interface`. Adding this notation alone took the
  fixture from **111 turns and 12 `javap` calls to 72 and 6**.
- **A3** — the catalogue MUST be generated from the code. Hand-writing it drifted twice in one
  fixture: a value silently truncated at 72 bytes, and a notation one author read as "this field does
  not exist", costing four lookups.

## Protocol for changing this spec

An instruction may be **added** only with a measurement showing that its absence costs correctness,
turns, or builds. An instruction may be **removed** on a measurement showing its absence costs
nothing. **Author self-report may falsify a metric but may never establish a result** — that rule was
itself derived from a round where the mechanical scorer was wrong and the author's report caught it.

State the falsifier before running. This project has had four scoring defects, and the most
consequential pointed toward the conclusion already being drafted.

## Known limits, stated

- **n = 1 per ablation cell.** Only the optimum was replicated (n=4).
- **One model.** Haiku 4.5 on the Fluxtion arm; a stronger model was used on the comparison arm
  because tool knowledge differs (P4).
- **Correctness was not the differentiator in rounds 46–48.** Plain Java on a stronger model matched
  or beat Fluxtion on correctness three times, and the stated falsifier fired each time. The measured
  advantage in those rounds was cost, not accuracy.
- **Most of the 7.5× is not the framework.** It is instructions, a manifest convention **any Java
  library could adopt**, and the removal of scaffolding the fixture itself invented. This spec is
  therefore largely portable, and saying so is more useful than claiming otherwise.
- **The catalogue's selection had zero degrees of freedom** in the measured fixture — the requires
  chain forced every choice. This measured wiring, not judgement.
