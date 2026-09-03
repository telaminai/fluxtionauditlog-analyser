# Assessment — the playground's `build-with-ai` prompts and assets

**Read 2026-09-03** from `https://fluxtion-playground.dev/build-with-ai` and its linked assets.
Assessed against rounds 07, 08, 48, 55, 57.

**First, a correction to my own draft.** I began writing that the prompts omit the silent-failure
guardrails. **They do not.** `CLAUDE.md` covers all five — trigger-by-reference and
`@NoTriggerReference`, the config-vs-derived-state distinction, one `@OnTrigger` per node,
`@AssignToField` for same-typed parameters, and an explicit *"Failures that compile, run, and are
still wrong… Nothing will ever warn you"*. That is the material round 08 was designed around, and it
is well written. The criticism below is narrower and, I think, more useful.

## What is there

| asset | mode, in this repo's taxonomy |
|---|---|
| Prompt 1 — direct node authoring | **mode 3** (author writes nodes; compiler resolves topology) |
| Prompt 2 + `spring-authoring/*` | **mode 2** — *"scaffolding structure, not writing logic"*; the bean file generates stubs the author then fills |
| `CLAUDE.md`, golden path, starter templates | shared assets |

**Mode 2 already exists and I said it did not.** The starter path is exactly the owner's item 2. What
is genuinely absent is **modes 0 and 1** — nothing on the page mentions a component catalogue, a jar
manifest, or selecting a pre-built component. An integrator arriving with bought-in jars has no path.

## Finding 1 — the two modes have OPPOSITE wiring rules, and only one is documented

`spring-authoring/contract.md` is explicit: topology is `constructor-arg ref` between beans, and it
**warns against `#{bean.field}`**.

**That is correct for mode 2 and wrong for mode 1.** In mode 2 every node is a top-level bean the
author is scaffolding, so `ref=` reaches everything. In mode 1 the beans are *vendor entry points* —
holders that carry no Fluxtion annotations — and `ref=` reaches only the holder. Round 48 measured the
consequence: wiring through the entry point compiles, runs, and silently yields **8 dispatch stages
instead of 17**. `value="#{bean.field}"` is the only construct that reaches a node inside a component.

> **The same document that is right for one mode is actively harmful in the other**, and nothing tells
> the reader which one they are in. This is the strongest argument for mode-scoped skills, and it was
> found by reading, not predicted.

## Finding 2 — prompt 1 states the reassuring half of a two-part claim

`CLAUDE.md` says both of these, correctly:

> *"Fluxtion's compile errors are written to be directive"* … and … *"These three do not fail — they
> produce a green build and a graph that is not the one you described. **Nothing will ever warn you**"*

**Prompt 1 lifts only the first into the operating instruction:** *"Fluxtion's errors are directive, so
when one fires it's telling you the fix: apply it and re-run."* Combined with *"You have a compile/run
loop"* as the stated verification strategy, the prompt tells the author to trust a loop that
`CLAUDE.md` itself says cannot catch the failures that matter.

Round 07 measured this precisely: the idiom errors have **no searchable symptom, because their symptom
is absence**. An author who reads the orientation before starting is protected; one who reads it when
something breaks is not, because nothing breaks.

**Proposed fix, one clause:** *"…apply it and re-run. The errors that matter most produce no error —
check the generated dispatch, not just the build."*

## Finding 3 — the verification step every round used is missing

Every round from 07 onward scored the **generated dispatch**, mechanically: `guardCheck_x()`
membership, `@OnTrigger` invocations per cycle, node instance counts. It is a `grep` over emitted
source and it catches exactly the class of failure `CLAUDE.md` warns about and the compile/run loop
cannot.

Neither prompt mentions it. **This is the cheapest high-value addition to the page** — one paragraph
telling the author to read `guardCheck_*` and confirm the graph is the one they described.

## Finding 4 — a criticism that does NOT transfer, recorded so it is not repeated

Round 48 measured a worked example going from best-in-study (2 builds) to **harmful (+28 turns)** once
the catalogue was indexed, and P1 generalises it as *discovery aids expire*. Prompt 1 says *"match the
closest example in the playground before improvising."*

**That is not the same situation.** P1's mechanism is that an example teaches you to derive what an
index now answers. **The playground has no catalogue**, so nothing has replaced the example and it
remains the best available aid. The criticism applies only if a catalogue is introduced — at which
point prompt 1 must be re-ablated.

## What I would change, ordered by evidence strength

| # | change | evidence |
|---|---|---|
| 1 | State the wiring rule **per mode**: `ref=` for scaffolded nodes, `#{bean.field}` for reaching inside a bought-in component | round 48, 17→8 stages, measured |
| 2 | Add the missing half-sentence to prompt 1 about errors that produce no error | rounds 07, 08, and `CLAUDE.md`'s own text |
| 3 | Add "check the generated dispatch" as an explicit verification step | every round since 07 |
| 4 | Add a mode-0/1 path: read the catalogue first, resolve what can be resolved | rounds 48, 55, 57 — resolver at 0 tokens, 38 ms |
| 5 | Leave example-matching alone until a catalogue exists | P1, and it does not yet apply |

## Honest limits of this assessment

**My fixture is not theirs.** Everything measured in this repo used a component-assembly task with a
manifest catalogue; the playground is scaffolding and node authoring with no catalogue. Findings 1–3
concern Fluxtion semantics and transfer. Finding 5 explicitly does not. **None of these prompts has
been ablated** — this is a reading against prior measurements, not a measurement, and it should be
labelled as such until the mode-2/3 ablation runs.
