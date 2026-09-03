# SPEC (PROPOSED) — the authoring mode selector, and the two branches beneath it

**Status** proposed · **Target** a Fluxtion-wide authoring entry point; the selector is new, the
branches beneath it mostly exist
**Evidence** rounds 07, 08, 48, 55, 57; `tools/fluxtion-harness.py`;
[`assessment-playground-ai-prompts.md`](../proposals/assessment-playground-ai-prompts.md)
**Companion** [`spec-authoring-modes.md`](spec-authoring-modes.md) — the mode taxonomy and delivery order

## The end state

```
                          AUTHORING MODE SELECTOR                      ← NEW
                          a tool, not a prompt
                          0 tokens · 38 ms · always runs first
                                     │
              ┌──────────────────────┴──────────────────────┐
              │                                             │
      CATALOGUE BRANCH                              GREENFIELD BRANCH
      components already exist                      nothing exists yet
      (this repo: rounds 48/55/57)                  (playground: build-with-ai)
              │                                             │
    ┌─────────┼─────────┐                          ┌────────┴────────┐
    │         │         │                          │                 │
  mode 0    mode 0+   mode 1                     mode 2            mode 3
  resolved  profile   select                    scaffold          direct
  0 tokens  0 tokens  1 question                XML → stubs       Java nodes
  NO SKILL  NO SKILL  selection skill           spring-authoring  CLAUDE.md
```

**The selector is the only new component.** Both branches beneath it already exist and are largely
proven — the catalogue branch in this repo, the greenfield branch on the playground. What is missing
is the thing that decides which one you are in, and today nothing does: the playground's two prompts
assume greenfield, and this repo's resolver assumes a catalogue.

## Why a selector, rather than letting the user pick

Three measured reasons.

**1. Picking wrong is expensive and silent.** An integrator who takes the greenfield path authors a
graph that already exists in a jar. Cell O cost **1.98M weighted / 51 turns**; the resolver costs
**0 / 38 ms**. Nothing warns you that you chose the expensive path.

**2. The branches have OPPOSITE wiring rules.** `spring-authoring/contract.md` mandates
`constructor-arg ref` and warns against `#{bean.field}` — correct in the greenfield branch, where
every node is a top-level bean. In the catalogue branch the beans are vendor *entry points* that carry
no Fluxtion annotations, so `ref=` reaches only the holder: round 48 measured **8 dispatch stages
instead of 17**, compiling and running. **The same instruction is right in one branch and harmful in
the other**, and only a selector can know which document to hand over.

**3. Real sessions are MIXED.** Most figures resolve; a few need authoring. Treating a session as one
branch is wrong — see the handoff below.

## The decision procedure

Everything except one question is mechanical.

| # | question | answered by | outcome |
|---|---|---|---|
| 1 | is there a catalogue on the path? | scan jars for `Fluxtion-Component` | no → greenfield |
| 2 | does it cover the required figures? | constraint solve | no → mixed or greenfield |
| 3 | is the covering selection unique? | count minimal solutions | yes → **mode 0** |
| 4 | does a site profile decide it? | `Fluxtion-Convention` × profile | yes → **mode 0+** |
| 5 | otherwise | — | **mode 1**: one question, candidates listed |
| 6 | for the uncovered figures: XML-scaffold or direct Java? | **the user** — a preference, not derivable | **mode 2** or **mode 3** |

**Only step 6 asks a human anything**, and only when the catalogue cannot cover the requirement.
Steps 1–5 are `tools/fluxtion-harness.py` and cost nothing.

### The one input that is not mechanical, and has never been measured

The selector needs **the figures the business requires**. Turning *"a risk engine that alerts on
breach"* into a figure list is judgement, and it is upstream of every measurement this project has
taken — **every round was handed the figure list.** It is the first place a model earns its keep in a
session, and it is unmeasured.

## The handoff contract

The selector emits a machine-readable record so a skill loader can act on it without a model reading
prose. Implemented today as `--json`:

```json
{
  "branch": "catalogue",
  "modes": ["0+", "2/3"],
  "skills": [null, "fluxtion-node-authoring"],
  "resolved_figures": ["adjusted", "alert", "..."],
  "authoring_required": ["netPosition"],
  "selection_candidates": {}
}
```

Four properties this contract must keep:

- **`modes` is a list, because mode is per FIGURE.** A mixed session resolves most of the graph and
  scopes authoring to the gap. Instruction cost then scales with the gap, not with the graph.
- **`skills` may contain `null`.** Modes 0 and 0+ load **nothing** — the cheapest session is one where
  the skill system is silent because there is no authoring to guide. This is the strongest form of the
  result and the contract must be able to express it.
- **`selection_candidates` carries the declared `Fluxtion-Convention` values**, so answering mode 1
  once converts the session to mode 0+ permanently, as a profile line.
- **The record names skills, not content.** Skills load on demand; the selector never carries them.

## Where the assets live: the website hosts one asset per MODE

**Owner, 2026-09-03:** *"their interactive free form builder is just a start point, we will rework
this completely so we have authoring mode assets on the website."*

That settles the shape and it is better than deferring to an external branch. The website is not the
greenfield half — it is **the asset host for every mode**, and today's `build-with-ai` page is a
start point covering two of them.

```
SELECTOR (local tool, 0 tokens)  ──names a mode──▶  ASSET URL on the website  ──fetched on demand──▶  the model
```

**Assets are FETCHED, not injected, and that is a cost decision rather than a packaging one.** Injected
text is charged against every turn of every session; a fetched asset is charged once, in the session
that actually needs it. Round 07 reached the same conclusion from the other direction — its deletion
candidate was *"move the whole section to the FETCHED resources where it costs nothing per turn"*. The
mode split makes that mechanical: a mode-0 session fetches nothing, because there is nothing to author.

### The asset contract

One asset per mode, at a stable URL the selector can name:

| mode | asset | must contain |
|---|---|---|
| **0 / 0+** | *none* | nothing is authored; the skill system stays silent |
| **1** | `/authoring/mode-1-selection.md` | how to read a `Fluxtion-Description`; that **absence of a promise rules a candidate out** (round 55); how a decision becomes a profile line |
| **2** | `/authoring/mode-2-spring-scaffold.md` | today's `spring-authoring/*`, plus its wiring rule stated as **its own** |
| **3** | `/authoring/mode-3-node-authoring.md` | today's `CLAUDE.md` + golden path, plus the dispatch check |
| shared | `/authoring/silent-failures.md` | the five guardrails `CLAUDE.md` already carries well — referenced by 2 and 3, not duplicated |

**Every asset MUST open by naming its mode and its wiring rule.** That single line is what prevents the
17→8 failure, and it is the difference between a document that is right and one that is right *here*.

**The selector emits the URL, not the content.** The handoff record's `skills` field becomes asset
URLs; nothing in the always-on context grows as modes are added.

## Requirements on the branches

**R1 — each mode skill MUST state its own wiring rule.** This is the finding that motivates the whole
architecture; leaving it implicit reproduces the 17→8 failure.

**R2 — every skill MUST include the dispatch check.** *"Read `guardCheck_*` in the generated source and
confirm the graph is the one you described."* Every round since 07 scored exactly this, and it is the
only step that catches failures which compile, run and are wrong.

**R3 — prompt 1 MUST NOT state that errors are directive without its other half.** `CLAUDE.md` says
both *"errors are written to be directive"* and *"nothing will ever warn you"*. The prompt currently
carries only the first, alongside a compile/run loop that cannot catch what the second describes.

**R4 — no asset may assume its own mode is the only one.** Each MUST open by naming the mode it serves,
so an author holding jars is routed to mode 0/1 rather than led into authoring what already exists.
Today both prompts assume greenfield; after the rework, the selector decides and the asset states
which world it is written for.

**R4a — assets are fetched, never injected.** A mode-0 session must fetch nothing. Adding a mode must
not grow the always-on context.

**R5 — a catalogue-generating build MUST fail** if two entry points share a type surface and either
declares no `Fluxtion-Convention`, since silence is not a match and an undeclared variant becomes
unselectable. (Belongs in [`spec-component-catalogue.md`](spec-component-catalogue.md).)

## What is built, and what is not

| piece | status |
|---|---|
| resolver, wiring + selection | **built, verified** — unique selection, byte-identical alerts, 0 tokens |
| `Fluxtion-Convention` + site profile | **built, verified** — one word decides the build |
| mode derivation, incl. mixed sessions | **built** |
| `--json` handoff record | **built** |
| catalogue generation from bytecode | spec'd only — `spec-component-catalogue.md` |
| the mode-1 selection asset | **not written** |
| asset fetching from the handoff record | **not built** |
| the website's mode-asset rework | **owner's, planned** — today's two prompts are the start point |
| ablation of any authoring asset | **never run** — this is where the ceiling is |

## Honest limits

- **The catalogue modes are measured; the authoring modes are not.** Rounds 48/55/57 used a
  component-assembly fixture. No authoring asset has been ablated, so R1–R4 are reasoned from prior
  measurements rather than measured in their own setting. **The rework is the moment to build the
  ablation in from the start**, rather than measuring assets after they have hardened — which is how
  round 48 ended up ablating a document nobody had designed to be ablated.
- **Step 6 is a preference and may not be stable.** Whether authors prefer XML scaffolding or direct
  Java is unmeasured; if one dominates, the branch collapses to a single mode.
- **`n` is small throughout.** Mode 0 is deterministic and needs no replication; every mode-1 result
  rests on single runs.
