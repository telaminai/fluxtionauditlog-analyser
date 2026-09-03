# SPEC (PROPOSED) — the minimum instruction set for LLM authoring against Fluxtion

**Status** proposed · **Target** the public Fluxtion AI instructions (`docs/claude.txt` and the
golden path), plus the artefact-side conventions in
[`spec-component-catalogue.md`](spec-component-catalogue.md)
**Evidence** [`docs/experience/runs/round-48/`](../experience/runs/round-48/)

> **Superseded in part.** The canonical target architecture is
> [`spec-authoring-modes.md`](spec-authoring-modes.md) ▸ *THE TARGET ARCHITECTURE*. Where this
> document's architectural claims disagree with it, that one wins. What remains authoritative here
> is the **measured evidence and the corrections**, which are preserved as historical record —
> including the ones that were wrong, because how they were wrong is the transferable part.
 — fifteen measured
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

### P3 — cost tracks TURNS, not words

Introspection put reading and component selection at **32–50% of effort**, and writing the
integration at **5–10%**.

**An earlier draft of this spec said "manual size dominated every measurement". That is measurably
wrong** and is corrected here, because the correction makes the argument stronger rather than weaker.
Across the fifteen cells:

| driver | correlation with weighted cost |
|---|---|
| **turns** | **r = +0.955** |
| manual words | r = +0.687 |
| `javap` calls | r = +0.528 |

The decisive pair: **cell B (879 words, 85 turns, 4.17M) against cell C (2,342 words, 85 turns,
4.13M)** — 2.7× the manual, same turns, fractionally *cheaper*. Words correlate at all only because
large manuals *caused* more turns; they cost little directly.

**The mechanism is prompt caching.** A stable prefix is re-read at 0.1× base input price, so a manual
that does not change between turns is already amortised. Cutting words from it wins almost nothing;
cutting *turns* wins everything. Cell J (848 words, 72 turns, 2.91M) beats cell B (879 words, 85
turns, 4.17M) on **13 turns**, at a near-identical word count.

> **Move the answer into the artefact before writing prose about it** — not to shorten the prompt, but
> because a precomputed answer removes a turn and prose does not. Every instruction competes with a
> manifest entry that would make a round-trip unnecessary.

**Corollary — the tax argument is weaker than it looks, and the spec should not lean on it.** "Charged
against every turn" is true at 0.1×, not 1×. An instruction that costs words but saves even one turn
is worth keeping. The case for minimality rests on *harm* — the worked example at +28 turns, the
procedure at 21 `javap` calls — not on prompt length.

### P3a — do not optimise below the cache floor

`Claude Haiku 4.5` has a **4,096-token minimum cacheable prefix**; below it, requests are *silently*
processed uncached with no error. The measured optimum's manual is 659 words — roughly 880 tokens.

A prefix trimmed under the floor pays **1×** on every token every turn instead of **0.1×**, so
shrinking a manual can make each turn more expensive per token. In an agent loop the accumulating
tool output usually crosses the floor within a few turns, which bounds the exposure to the opening
turns — but the effect is real and non-monotonic, and it is invisible unless
`usage.cache_read_input_tokens` is checked.

**Any future round MUST record `cache_creation_input_tokens` and `cache_read_input_tokens`**, because
a cost comparison between two prefix sizes is meaningless if one of them was silently uncached.

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

### P3b — token counts are NOT comparable across model generations

**Read from platform.claude.com this session, not recalled:**

> **Context window:** 1M tokens is roughly 555k words … on the current tokenizer *(introduced with
> Claude Opus 4.7)*; models before it fit about **750k words** in 1M tokens.

Haiku 4.5 predates Opus 4.7, so the two arms of this study are tokenised differently:

| | tokens/word | note |
|---|---|---|
| Opus 5 (comparison arm) | 1.802 | current tokenizer |
| Haiku 4.5 (Fluxtion arm) | 1.333 | previous tokenizer |

**Identical text yields 1.35× more tokens on the comparison arm.** Combined with Opus 5 costing 5×
Haiku 4.5 per token ($5/$25 vs $1/$5 per MTok), **the same text costs 6.76× more on the comparison
arm before any difference in behaviour.**

**The weighted metric used throughout this project — `output×50 + input×10 + cache_read×1` — measures
token VOLUME, not money.** Its *relative* weights are right (5 : 1 : 0.1 matches output : input :
cache-read pricing exactly), but it applies the same absolute scale to both arms, so it silently
prices an Opus token and a Haiku token identically.

Three ratios follow, all defensible, answering different questions:

| comparison | raw weighted units | **dollars** | tokenizer-adjusted work |
|---|---|---|---|
| round 48, plain Java | 1.88× | **9.39×** | 1.39× |
| round 49, idiomatic Java | 2.71× | **13.54×** | 2.00× |

**Every ratio this project has published to date is the raw-units column**, which understates the
economic difference by 5× and overstates the like-for-like work difference by 1.35×.

**Normative:**

1. Any cross-model comparison MUST state which of the three it is quoting.
2. **Dollars is the correct economic claim** — the entire thesis is that better instructions let a
   *cheaper* model succeed, so the model difference is the result, not a confound. It MUST be
   accompanied by the plain statement that different models were used.
3. **Tokenizer-adjusted work is the correct efficiency claim** — and it is the modest one, 1.39×–2.00×.
   Quoting the dollar figure as though it measured inherent efficiency would be dishonest.
4. Raw weighted units SHOULD be retired from external use. They are neither measure and read as both.

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

### P4a — the assembly is free; instructions should only cover judgement

**Measured after this spec was first written, and it narrows the spec's own scope.**
[`tools/bean-resolver.py`](../../tools/bean-resolver.py) resolves `Fluxtion-Requires` against
`Fluxtion-Provides` as a constraint solve and emits the bean file. Against the round-48 fixture it
produces a **unique** selection **identical to the measured optimum in both components and wiring**;
the build is green, and the alerts are **byte-identical to the expected output**.

| | cell O — a model authored it | the resolver |
|---|---|---|
| turns | 51 | — |
| `mvn` runs | 5 | — |
| weighted cost | **1.98M** | **0** |

Against round 55's fixture — six entry points sharing one type surface — it reports the ambiguity,
isolates the undecided jar, prints the candidate descriptions, and **refuses to guess**.

That is the boundary, and it is now measured rather than argued:

| half | input → output | who |
|---|---|---|
| **assembly** | type surface → XML | a resolver — free, deterministic |
| **selection** | business requirement → `Fluxtion-Description` | **a model, and only here** |

**Normative consequence for this spec.** N1–N4 were earned on a task that included assembly. Assembly
should not be in the task. **Future instruction sets SHOULD assume the bean file is generated and
cover only selection**, and any future round SHOULD measure selection alone. N3 (the catalogue read)
and N4 (the supplied runner) are candidates for retirement on that basis — both exist to help an
author do work a resolver now does — and MUST be re-ablated against a selection-only task before
being kept.

**And the series headline is restated accordingly:** not "7.5× cheaper to author", but **the assembly
is free and you pay only for judgement.**

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
