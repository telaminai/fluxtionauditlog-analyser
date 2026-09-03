# Walkthrough — what the user says, what the LLM does, what runs underneath

**Companion to** [`spec-authoring-mode-selector.md`](spec-authoring-mode-selector.md).
**Every "behind the scenes" block below is real output** from `tools/fluxtion-harness.py` against the
round-55 fixture, captured 2026-09-03. Nothing here is illustrative.

> **Writing this found three defects that the prose had hidden** — see *What simulating it exposed* at
> the end. That is the argument for walkthroughs over descriptions.

---

## Session A — the integrator. Nothing is authored.

**User:**
> *"I've got the vendor jars in `lib/`. I need a risk engine that publishes the standard figures and
> raises a breach alert when the exposure limit is breached, with exact breach and alert counts. This
> desk hedges every position."*

**LLM, turn 1 — the only judgement in the whole session.** Two acts, both semantic, neither mechanical:

1. **requirement → figure list.** *"the standard figures … a breach alert … exact counts"* becomes
   `mid, depth, vol, ewma, adjusted, spread, book, score, notional, exposure, var, charge, buffer,
   fee, alert, breachCount, streak, alertCount`.
2. **requirement → site convention.** *"this desk hedges every position"* becomes `spread=hedged`.

**Behind the scenes** — one tool call, no model involved:

```
$ fluxtion-harness --jars lib --figures mid,depth,…,alertCount --conventions spread=hedged --json
{
  "branch": "catalogue",
  "modes": ["0+"],
  "skills": [null],
  "authoring_required": []
}
```

**`skills: [null]` is the important line.** Nothing is fetched. No authoring asset loads, because there
is nothing to author.

**LLM, turn 2:**
> *"Everything you need is already in the jars. I've resolved the wiring — `MarketDataPlus`,
> `PricingHedged`, `LiquidityStd`, `RiskSupervised`, `CapitalRegulated` — and written the bean file.
> `PricingHedged` was chosen because your desk hedges; five sibling components implement other spread
> conventions. Build with `mvn compile`. Shall I add `spread=hedged` to the project profile so this
> decision is recorded and never asked again?"*

**Cost:** two turns. The selector ran in 38 ms for zero tokens; the bean file it emitted was verified
in round 57 to produce **byte-identical alerts** to the expected output.

---

## Session B — the same, plus something the catalogue cannot supply

**User adds:**
> *"…and I need the net position tracked per symbol."*

**Behind the scenes** — the same call, one extra figure:

```
$ fluxtion-harness --jars lib --figures …,netPosition --conventions spread=hedged --json
{
  "branch": "catalogue",
  "modes": ["0+", "2/3"],
  "skills": [null, "fluxtion-node-authoring"],
  "authoring_required": ["netPosition"]
}
```

**Two modes, because mode is per figure.** Eighteen figures resolve and load nothing; one figure needs
authoring and loads exactly one asset.

**LLM, turn 2:**
> *"Eighteen of your nineteen figures come from the jars — that half is wired and buildable already.
> Only `netPosition` needs writing: one node, accumulating per symbol across trades. I'll fetch the
> node-authoring guide and write it against the resolved graph."*

**What did NOT happen:** the model did not design a graph, choose a topology, or write nineteen nodes.
**Authoring was scoped to the gap** — which is the entire point of running the catalogue first.

---

## Session C — the catalogue cannot decide, so one question is asked

Same jars, **no site profile**:

```
  MODE 1 — 6 equally minimal selections. Assembly is mechanical;
  selection is not. One question needs judgement:

      jar 'pricing' — 6 candidates:
        PricingCapped      adds the spread capped at the venue ceiling  [spread=capped]
        PricingFull        adds the spread                              [spread=raw]
        PricingGross       adds the spread gross of execution fees      [spread=gross]
        PricingHedged      adds the spread including the desk hedging overlay  [spread=hedged]
        PricingNetted      adds the spread net of the standing inventory offset [spread=netted]
        PricingSmoothed    adds the spread with the smoothing factor applied    [spread=smoothed]

  Answer it once with --conventions and this becomes mode 0+.
```

**LLM, turn 2:**
> *"Everything wires itself except one choice: six pricing components are identical in type and differ
> only in how they compute the spread. Which does your desk use — hedged, netted, gross of fees,
> capped, smoothed, or raw?"*

**User:** *"hedged."*

Session becomes 0+ **permanently**: written to the project profile, never asked again, and re-resolved
free on every rebuild and every vendor upgrade.

**Note what the model is NOT asked to do.** It is not asked to read six prose descriptions and infer.
It is handed six candidates with declared conventions and asked one question in the user's own
vocabulary. Round 55 measured the alternative: a model reading the descriptions got it right, and
spent **11 `javap` calls** doing so.

---

## Session D — the profile names something nothing implements

```
$ fluxtion-harness --jars lib --figures … --conventions spread=exotic --json
{ "modes": ["unsatisfiable"], "skills": [null] }
```

> *"No component implements an `exotic` spread. Your options are: pick one of the six conventions
> available, buy a component that implements it, or author one — which puts `spread` into mode 2/3."*

**A clean refusal, not a guess.** The resolver never invents a selection.

---

## The whole flow

```
user, natural language
        │
        ▼
LLM ── requirement → figures            ← judgement, and UNMEASURED
    └─ requirement → conventions        ← judgement, once per installation
        │
        ▼
SELECTOR (tool, 0 tokens, 38 ms) ── reads jar manifests ── constraint solve
        │
        ▼
handoff record: branch · modes[] · skills[] · resolved · gap · candidates
        │
   ┌────┴──────────────┬──────────────────┬─────────────────┐
   ▼                   ▼                  ▼                 ▼
mode 0/0+          mode 1             mode 2/3         unsatisfiable
nothing loads      one question       fetch ONE asset  ask the user
bean file written  → profile line     author the GAP   revise or buy
```

**Where the tokens go.** Only two of these cost anything: the requirement→figures step, and the mode-1
question. Assembly, wiring, ordering and re-resolution are free forever.

---

## What simulating it exposed

Writing this walkthrough by *running* it rather than describing it found three defects in work that had
already been committed and specced:

1. **`--json` did not cover the unsatisfiable path** — it printed prose to a caller expecting JSON, so
   a programmatic skill loader would have crashed on the one outcome that most needs handling.
2. **The mixed branch mislabelled an unsatisfiable profile as mode 1** — it would have asked the user
   to choose between candidates when in fact none matched.
3. **A manifest without a trailing blank line silently loses its last attribute.** `jar` dropped
   `Fluxtion-Convention: spread=smoothed`, so `PricingSmoothed` became unselectable at any site with a
   `spread` profile — **exactly the failure mode R5 predicts**, produced by accident, with no warning
   from any tool.

Defect 3 is the one worth carrying forward: it is a second independent argument for
[`spec-component-catalogue.md`](spec-component-catalogue.md)'s core claim that **manifests must be
generated, not hand-written**, and it should become one of that spec's build-failing validations —
*every entry point sharing a type surface must declare a convention, and the generator must verify the
attribute survives into the jar, not merely into the source file.*
